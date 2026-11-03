#!/usr/bin/env python3
"""Use the real Channel+ Activity view hierarchy for TCL HbbTV, as LOVEStv 1.45 did."""
from pathlib import Path
p=Path('owntv_tdt/app/src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java')
s=p.read_text(encoding='utf8')
def sub(old,new):
    global s
    assert s.count(old)==1, (old[:100],s.count(old))
    s=s.replace(old,new)
sub('private SurfaceControlViewHost viewHost;', 'private ViewGroup activityParent;\n    private boolean activityAttached;')
sub('if(viewHost==null) attach(message.replyTo,data);', 'if(!activityAttached) attach(message.replyTo,data);')
sub('if(viewHost!=null) viewHost.relayout(Math.max(1,data.getInt(WIDTH)),Math.max(1,data.getInt(HEIGHT)));', '// Activity decor root fills available window and handles size changes.')
sub('''            viewHost=new SurfaceControlViewHost(windowContext,display,data.getBinder(HOST_TOKEN));
            // Public on API 30. Newer LayoutParams overloads cannot be used on TCL Android 11.
            viewHost.setView(root,Math.max(1,data.getInt(WIDTH)),Math.max(1,data.getInt(HEIGHT)));
            SurfaceControlViewHost.SurfacePackage surface=viewHost.getSurfacePackage();
            if(surface==null) throw new IllegalStateException("Browser surface unavailable");
            Bundle result=new Bundle(); result.putParcelable(SURFACE_PACKAGE,surface); reply(SURFACE,result); surface.release();
            Log.d(TAG,"Browser surface attached to Channel+; existing tuner retained channel="+channelId);''',
'''            if(playerActivity==null) throw new IllegalStateException("No live Channel+ Activity for TCL browser");
            activityParent=(ViewGroup)playerActivity.getWindow().getDecorView();
            if(activityParent==null) throw new IllegalStateException("Channel+ decor unavailable");
            activityParent.addView(root,new ViewGroup.LayoutParams(-1,-1));
            root.bringToFront(); activityAttached=true;
            Log.i(TAG,"Native TCL browser mounted in real Channel+ Activity decor as in 1.45, channel="+channelId);''')
sub('''        if(viewHost!=null) { viewHost.release(); viewHost=null; }
        root=null; browser=null; engine=null; client=null; running=false;''',
'''        if(root!=null && root.getParent() instanceof ViewGroup)
            ((ViewGroup)root.getParent()).removeView(root);
        activityAttached=false; activityParent=null;
        root=null; browser=null; engine=null; client=null; running=false;''')
p.write_text(s,encoding='utf8')
p=Path('owntv_tdt/app/src/main/java/tv/own/owntv/features/hbbtv/HbbTvPlayerView.java')
s=p.read_text(encoding='utf8')
old='if(released || failed || !enabled || !surfaceReady || overlay==null || getWidth()==0 || getHeight()==0) return;'
assert s.count(old)==1
s=s.replace(old,'if(released || failed || !enabled || getWidth()==0 || getHeight()==0 || activity==null) return;')
old='if(attached || !surfaceReady || !enabled || remote==null || overlay.getDisplay()==null) return;'
assert s.count(old)==1
s=s.replace(old,'if(attached || !enabled || remote==null || activity==null || activity.getDisplay()==null) return;')
old='data.putBinder(HOST_TOKEN,overlay.getHostToken()); data.putInt(DISPLAY,overlay.getDisplay().getDisplayId());'
assert s.count(old)==1
s=s.replace(old,'data.putInt(DISPLAY,activity.getDisplay().getDisplayId());')
p.write_text(s,encoding='utf8')
print('PASS: direct Activity browser root; no second player and no SurfaceControlViewHost dependency')
