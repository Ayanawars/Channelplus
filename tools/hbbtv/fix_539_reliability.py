#!/usr/bin/env python3
"""HbbTV integration reliability + observable startup. Re-run against exact 5.3.7+hotfix+key-routing source."""
from pathlib import Path
root=Path('owntv_tdt/app/src/main/java/tv/own/owntv')
def change(path, old, new):
    content=path.read_text(encoding='utf-8')
    if content.count(old)!=1:
        raise RuntimeError(f'{path}: expected once ({content.count(old)}): {old[:105]!r}')
    path.write_text(content.replace(old,new),encoding='utf-8')
p=root/'features/hbbtv/HbbTvPlayerView.java'
change(p, '    private int session;\n', '    private int session;\n    private int queuedColour=-1;\n    private long queuedGeneration=-1;\n')
change(p,
    '        HbbTvActivityHost.attach(activity);\n        tvView=new TvView(context);',
    '        HbbTvActivityHost.attach(activity);\n        Log.i(TAG,"Player view created: sdk="+Build.VERSION.SDK_INT+" 64bit="+Process.is64Bit()+\n                " vendorJar="+new File("/product/framework/com.tcl.hbbtv.jar").isFile()+" activity="+(activity!=null));\n        tvView=new TvView(context);')
change(p,
    '                @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady=true; connect(); }',
    '                @Override public void surfaceCreated(SurfaceHolder holder) { surfaceReady=true; Log.i(TAG,"Browser overlay surface created"); connect(); }')
change(p,
    '                @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady=false; disconnect(); }',
    '                @Override public void surfaceDestroyed(SurfaceHolder holder) { surfaceReady=false; Log.i(TAG,"Browser surface destroyed; release service binding"); disconnect(); }')
change(p,
    '''        }
    }
    public TvView getTvView() { return tvView; }''',
    '''        } else Log.w(TAG,"Embedded HbbTV disabled: requires SDK30, 32-bit process and vendor framework jar");
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        Log.i(TAG,"HbbTV view attached size="+getWidth()+"x"+getHeight());
        post(this::connect);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        super.onSizeChanged(w,h,oldw,oldh);
        if(w>0 && h>0 && (oldw==0 || oldh==0)) { Log.i(TAG,"HbbTV first layout size="+w+"x"+h); post(this::connect); }
    }
    public TvView getTvView() { return tvView; }''')
change(p,
    '        if(released) return;\n        // A failed vendor runtime must never poison a later channel or retune.',
    '        if(released) return;\n        final boolean wasEnabled=enabled, wasReady=ready;\n        final long wasChannel=channelId, wasGeneration=generation;\n        // A failed vendor runtime must never poison a later channel or retune.')
change(p,
    '            gate.leave();\n            if(failed)',
    '            gate.leave(); queuedColour=-1; queuedGeneration=-1;\n            if(failed)')
change(p,
    '        enabled=activePlayer && input!=null && input.startsWith("com.tcl.tvinput/.TDTVInputService/") && id>=0;\n        if(!enabled)',
    '''        enabled=activePlayer && input!=null && input.startsWith("com.tcl.tvinput/.TDTVInputService/") && id>=0;
        if(enabled!=wasEnabled || ready!=wasReady || channelId!=wasChannel || generation!=wasGeneration)
            Log.i(TAG,"TDT HbbTV eligibility="+enabled+" videoReady="+ready+" playerActive="+activePlayer+
                " channel="+id+" input="+input+" gen="+generation+" surfaceReady="+surfaceReady+
                " size="+getWidth()+"x"+getHeight()+" overlay="+(overlay!=null));
        if(!enabled)''')
change(p,
    '        if(connection!=null) return;\n        session=serial.incrementAndGet();',
    '        if(connection!=null) return;\n        if(activity!=null) owners.put(activity,this);\n        session=serial.incrementAndGet();')
change(p,
    '                remote=new Messenger(binder); attachRemote();',
    '                Log.i(TAG,"TCL runtime service connected"); remote=new Messenger(binder); attachRemote();')
change(p,
    '            Log.d(TAG,"Autostart bound on TCL TDT channel="+channelId+" videoReady="+ready);',
    '''            Log.i(TAG,"TCL runtime bind requested: channel="+channelId+" ready="+ready+
                    " size="+getWidth()+"x"+getHeight()+" service="+intent.getComponent());
            main.postDelayed(()->{ if(connection==candidate && remote==null) fail("TCL runtime bind callback timed out",null); },10000);''')
change(p,
    '        attached=true; send(ATTACH,data);',
    '        attached=true; Log.i(TAG,"TCL runtime ATTACH: channel="+channelId+" videoReady="+ready); send(ATTACH,data);')
change(p,
    '                Log.d(TAG,"HbbTV engine started before key; broadcaster rendering still requires device confirmation"); break;',
    '''                Log.i(TAG,"HbbTV runtime STARTED, broadcaster rendering still requires confirmation");
                if(queuedColour>=0 && queuedGeneration==generation && enabled && ready) {
                    int code=queuedColour; queuedColour=-1; queuedGeneration=-1;
                    gate.beginColour();
                    Bundle pending=channel(); pending.putInt(CODE,code); pending.putLong(KEY_EPOCH,gate.epoch());
                    send(KEY,pending); Log.i(TAG,"Delivering queued colour key="+code+" to TCL runtime");
                }
                break;''')
change(p,
    '        if(!enabled || !ready || !started || remote==null) {\n            if(colour(code)) Log.w(TAG,"Colour key blocked: code="+code+',
    '        if(!enabled || !ready || !started || remote==null) {\n            if(colour(code)) Log.w(TAG,"Colour key blocked: code="+code+')
# Replace the entire block tail of the not-ready branch (diagnostic was already injected by 539 key routing).
change(p,
    '''                " input="+input+" channel="+channelId+" generation="+generation);
            return false;
        }
        boolean colour=colour(code);''',
    '''                " input="+input+" channel="+channelId+" generation="+generation);
            // Retain the remote key while a valid TDT session's HbbTV engine starts.
            // The old fallback opened the ordinary Channel+ player bar instead.
            if(colour(code) && enabled) {
                if(event.getRepeatCount()>0) return consumed.contains(code);
                consumed.add(code); queuedColour=code; queuedGeneration=generation;
                if(failed) { failed=false; Log.i(TAG,"Retrying TCL runtime on colour key"); }
                connect();
                return true;
            }
            return false;
        }
        boolean colour=colour(code);''')
change(p,
    '    private void fail(String reason,Throwable error) { Log.w(TAG,reason,error); failed=true; disconnect(); }',
    '    private void fail(String reason,Throwable error) { Log.e(TAG,reason,error); failed=true; disconnect(); }')

service=Path('owntv_tdt/app/src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java')
change(service,
    '    @Override public void onCreate() { super.onCreate(); liveServices++; }\n    @Override public IBinder onBind(Intent intent) { return Build.VERSION.SDK_INT>=30 ? inbox.getBinder() : null; }',
    '''    @Override public void onCreate() {
        super.onCreate(); liveServices++;
        Log.i(TAG,"HbbTV service onCreate pid="+Process.myPid()+" 64bit="+Process.is64Bit()+
                " appProcess="+getApplicationInfo().processName);
    }
    @Override public IBinder onBind(Intent intent) {
        Log.i(TAG,"HbbTV service onBind sdk="+Build.VERSION.SDK_INT);
        return Build.VERSION.SDK_INT>=30 ? inbox.getBinder() : null;
    }''')
change(service,
    '        if(closed || message.replyTo==null || message.sendingUid!=Process.myUid()) return true;',
    '''        if(closed || message.replyTo==null || message.sendingUid!=Process.myUid()) {
            Log.w(TAG,"HbbTV service dropped msg="+message.what+" closed="+closed+
                    " uid="+message.sendingUid+" ours="+Process.myUid()+" reply="+(message.replyTo!=null));
            return true;
        }''')
print('PASS 5.3.9: startup logs, eligible TDT gating, delayed bind watchdog, retry on blue, queued key, activity overlay recreation')
activity=root/'MainActivity.kt'
change(activity,
    '''        val consumed = hbbKeyHandler?.invoke(event) == true
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            hbbKeyHandler != null &&
            (event.keyCode in android.view.KeyEvent.KEYCODE_PROG_RED..android.view.KeyEvent.KEYCODE_PROG_BLUE ||
             event.keyCode in android.view.KeyEvent.KEYCODE_BUTTON_A..android.view.KeyEvent.KEYCODE_BUTTON_MODE)) {
            Log.i("ChannelplusHbb", "Remote=" + android.view.KeyEvent.keyCodeToString(event.keyCode) +
                " consumedByHbb=" + consumed)
        }''',
    '''        val consumed = hbbKeyHandler?.invoke(event) == true
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            (event.keyCode in android.view.KeyEvent.KEYCODE_PROG_RED..android.view.KeyEvent.KEYCODE_PROG_BLUE ||
             event.keyCode in android.view.KeyEvent.KEYCODE_BUTTON_A..android.view.KeyEvent.KEYCODE_BUTTON_MODE)) {
            Log.i("ChannelplusHbb", "Remote=" + android.view.KeyEvent.keyCodeToString(event.keyCode) +
                " shellHandler=" + (hbbKeyHandler != null) + " consumedByHbb=" + consumed)
        }''')
print('PASS 5.3.9: key trace is emitted even if Compose has not registered its HbbTV handler')