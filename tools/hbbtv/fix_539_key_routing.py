#!/usr/bin/env python3
"""5.3.9: route live TIF color keys to registered HbbTV view; add actionable diagnostics.

This does not pretend the TCL native runtime is ready. It makes the key path
observable and avoids coupling to preview list/URI state.
"""
from pathlib import Path
root = Path('owntv_tdt/app/src/main/java/tv/own/owntv')
shell = root / 'features/shell/OwnTVShell.kt'
s = shell.read_text()
old='''    // The player starts HbbTV on video readiness; keys only control its existing browser.
    val hbbKeyHandler by rememberUpdatedState<(android.view.KeyEvent) -> Boolean>({ event ->
        val channel = previewChannel
        val tif = tv.own.owntv.core.model.TifChannelUri.parse(liveVm.previewEngine.currentUrl.orEmpty())
        if (hbbActivity != null && playerMode == PlayerMode.FULLSCREEN && multiview == null &&
            zapSource == MainSection.LIVE_TV && !catchupActive && channel != null && tif != null) {
            tv.own.owntv.features.hbbtv.HbbTvPlayerView.dispatch(hbbActivity, event)
        } else false
    })'''
new='''    // The HbbTV player view already knows whether a TCL TIF channel is active,
    // the video generation and the runtime state. Do not also gate remote input
    // on previewChannel/currentUrl: those values can be stale or null in full-screen.
    val hbbKeyHandler by rememberUpdatedState<(android.view.KeyEvent) -> Boolean>({ event ->
        if (hbbActivity != null && playerMode == PlayerMode.FULLSCREEN && multiview == null &&
            zapSource == MainSection.LIVE_TV && !catchupActive) {
            tv.own.owntv.features.hbbtv.HbbTvPlayerView.dispatch(hbbActivity, event)
        } else false
    })'''
assert s.count(old) == 1, 'Full-screen HbbTV gate changed; refuse blind patch'
shell.write_text(s.replace(old,new))
player = root / 'features/hbbtv/HbbTvPlayerView.java'
s = player.read_text()
old='''    public static boolean dispatch(Activity owner,KeyEvent event) {
        HbbTvPlayerView host=owners.get(owner); return host!=null && host.key(event);
    }'''
new='''    public static boolean dispatch(Activity owner,KeyEvent event) {
        HbbTvPlayerView host=owners.get(owner);
        if(host==null && event.getAction()==KeyEvent.ACTION_DOWN && colour(event.getKeyCode()))
            Log.w(TAG,"Colour key received but no active TCL HbbTV view; key="+event.getKeyCode());
        return host!=null && host.key(event);
    }'''
assert s.count(old)==1
s=s.replace(old,new)
old='''        if(!enabled || !ready || !started || remote==null) {
            if(colour(code)) Log.d(TAG,"Colour key before runtime readiness code="+code);
            return false;
        }'''
new='''        if(!enabled || !ready || !started || remote==null) {
            if(colour(code)) Log.w(TAG,"Colour key blocked: code="+code+
                " enabled="+enabled+" videoReady="+ready+" runtimeStarted="+started+
                " serviceConnected="+(remote!=null)+" failed="+failed+
                " surfaceReady="+surfaceReady+" overlay="+(overlay!=null)+
                " input="+input+" channel="+channelId+" generation="+generation);
            return false;
        }'''
assert s.count(old)==1
player.write_text(s.replace(old,new))
activity = root / 'MainActivity.kt'
s=activity.read_text()
old='''    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (hbbKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }'''
new='''    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val consumed = hbbKeyHandler?.invoke(event) == true
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            hbbKeyHandler != null &&
            (event.keyCode in android.view.KeyEvent.KEYCODE_PROG_RED..android.view.KeyEvent.KEYCODE_PROG_BLUE ||
             event.keyCode in android.view.KeyEvent.KEYCODE_BUTTON_A..android.view.KeyEvent.KEYCODE_BUTTON_MODE)) {
            Log.i("ChannelplusHbb", "Remote=" + android.view.KeyEvent.keyCodeToString(event.keyCode) +
                " consumedByHbb=" + consumed)
        }
        if (consumed) return true
        return super.dispatchKeyEvent(event)
    }'''
assert s.count(old)==1
activity.write_text(s.replace(old,new))
assert 'val channel = previewChannel' not in shell.read_text().split('val hbbKeyHandler by',1)[1].split('DisposableEffect(hbbActivity)',1)[0]
assert 'enabled="+enabled+" videoReady=' in player.read_text()
print('PASS: color key routing no longer depends on preview metadata; diagnostics for inactive runtime')