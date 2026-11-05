#!/usr/bin/env python3
"""Channel+ 5.3.17: rearm the SAME TDT HbbTV runtime after an explicit LOVEStv exit.

The 5.3.16 runtime's HIDE is sticky, and the bound native HbbtvServer keeps its
running flag after the broadcaster application closes. Only ending the bound
service lets onDestroy() stop the vendor engine and destroy the exited page.
Restarting the bound service does NOT reset the existing tuner TvView.
"""
from pathlib import Path

p=Path('owntv_tdt/app/src/main/java/tv/own/owntv/features/hbbtv/HbbTvPlayerView.java')
s=p.read_text(encoding='utf8')
def once(a,b,label):
    global s
    n=s.count(a)
    if n!=1: raise RuntimeError(f'{label}: expected 1 anchor; saw {n}')
    s=s.replace(a,b)

once('private boolean enabled,ready,surfaceReady,attached,started,failed,released;',
     'private boolean enabled,ready,surfaceReady,attached,started,failed,released,rearming;', 'rearm state')

# When BACK is rejected, the provider has exited its HbbTV application.
# Retain the user's old focus restoration; the session is recycled separately.
once('''gate.leave(); send(HIDE,channel());
                    if(activity instanceof tv.own.owntv.MainActivity)''',
     '''gate.leave(); send(HIDE,channel());
                    rearmAfterExit("LOVEStv BACK exited");
                    if(activity instanceof tv.own.owntv.MainActivity)''', 'BACK session rearm')

# The native Javascript "return to direct" path also sends HIDE to the player.
once('''case HIDE:
                // Browser hidden after return-to-live; restore Compose input focus.
                gate.leave();
                if(activity instanceof tv.own.owntv.MainActivity)''',
     '''case HIDE:
                // Browser hidden after return-to-live; restore Compose input focus.
                gate.leave();
                rearmAfterExit("LOVEStv returned to TDT");
                if(activity instanceof tv.own.owntv.MainActivity)''', 'native return session rearm')

# updateHbbTv() can be invoked during the restart delay; defer binds until
# the previous service has had time to run its onDestroy/stopAsync/destoryPage.
once('''    private void connect() {
        if(''',
     '''    private void connect() {
        if(rearming) return;
        if(''', 'no early bind while rearming')

anchor='''    private void fail(String reason,Throwable error) { Log.e(TAG,reason,error); failed=true; disconnect(); }'''
method='''    /** After an explicit HbbTV exit, only recycle the native browser service.
     * Keep the TvView, input ID, current channel and Exo/Compose playback intact.
     * A fresh service performs the same 5.3.12 auto-registration as initial tune.
     */
    private void rearmAfterExit(String why) {
        if(released || !enabled || rearming) return;
        rearming=true;
        Log.i(TAG,"LOVEStv exit detected; recycle native browser service (TDT tuner preserved): "+why);
        disconnect(); // unbind lets HbbTvRuntimeService.onDestroy destroy page + stop native engine
        main.postDelayed(()->{
            rearming=false;
            if(released || !enabled) return;
            if(overlay!=null) overlay.setVisibility(View.VISIBLE);
            if(activity!=null) owners.put(activity,this);
            Log.i(TAG,"LOVEStv rearming on same tuner channel="+channelId+" generation="+generation);
            connect(); // new service, no TDT retune, so original broadcaster hint can return
        },1800L);
    }
'''
once(anchor,method+anchor,'rearm method')

once('''    public void release() {
        released=true; disconnect(); consumed.clear();''',
     '''    public void release() {
        released=true; rearming=false; disconnect(); consumed.clear();''','release lifecycle')

# Keep the 5.3.12 Activity-backed native browser and 5.3.16 hidden focus handling.
assert 'rearmAfterExit("LOVEStv BACK exited")' in s
assert 'rearmAfterExit("LOVEStv returned to TDT")' in s
assert 'gate.leave();' in s
assert 'updateHbbTv(' in s
assert 'private void connect()' in s
p.write_text(s,encoding='utf8')
print('PASS 5.3.17: fresh native HbbTV service after every explicit exit, same tuned TvView preserved')
