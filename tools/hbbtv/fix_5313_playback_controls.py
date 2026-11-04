#!/usr/bin/env python3
"""Install the original 1.45 playback adapter in the live TCL HbbTV browser.

Runs after the working 5.3.12 integration; never changes the native tuner,
Activity window mounting, HbbTV init, original channel prompts or key mapping.
"""
from pathlib import Path
import hashlib

root=Path("owntv_tdt/app")
asset=root/"src/hbbtv32/assets/lovestv_playback_145.js"
data=asset.read_bytes()
assert hashlib.sha256(data).hexdigest()=="266e651e7af494803402737495acf7023e6115b2d9f282ec56427c221116504f", "Wrong LOVEStv 1.45 JS asset"
for marker in (b"function tryAdvance(e)",b"function rtveScanKey(e)",b"var steps=[30,120,240,480,960]"):
    assert marker in data

p=root/"src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java"
source=p.read_text(encoding="utf-8")
anchor="    private void prepareKeys() throws ReflectiveOperationException {"
assert source.count(anchor)==1 and "installPlaybackControls" not in source
injected="""    // Reuse the tested 1.45 green ad-skip and 2x-32x seek logic inside the
    // actual broadcaster browser. Never seek Channel+'s live tuner.
    // The JS self-checks provider URL, playback state and seekable ranges.
    private String controlsSource, lastControlsPage, lastControlsIssue;
    private final Runnable installPlaybackControls = new Runnable() {
        @Override public void run() {
            if (closed || !running || browser==null) return;
            try {
                Object common=browser.getClass().getMethod("getCommonWebView",String.class)
                        .invoke(browser,"hbbtv_tbrowser");
                if(common==null) throw new IllegalStateException("Tbrowser page not ready");
                java.lang.reflect.Field field=common.getClass().getDeclaredField("mWebView");
                field.setAccessible(true);
                Object renderer=field.get(common);
                if(renderer==null) throw new IllegalStateException("TCL WebView not ready");
                if(controlsSource==null) {
                    try (java.io.InputStream in=getAssets().open("lovestv_playback_145.js");
                         java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
                        byte[] block=new byte[4096];int len;
                        while((len=in.read(block))!=-1) out.write(block,0,len);
                        controlsSource=new String(out.toByteArray(),java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
                Method evaluate=null;
                for(Method m:renderer.getClass().getMethods()) {
                    Class<?>[] args=m.getParameterTypes();
                    if(m.getName().equals("evaluateJavascript") && args.length==2 &&
                            args[0]==String.class && args[1].isInterface()) {
                        evaluate=m;break;
                    }
                }
                if(evaluate==null)throw new NoSuchMethodException("TCL evaluateJavascript(String,callback)");
                Class<?> listenerType=evaluate.getParameterTypes()[1];
                ClassLoader loader=listenerType.getClassLoader();
                if(loader==null)loader=getClass().getClassLoader();
                Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{listenerType},
                        (proxy,method,args)->method.getDeclaringClass()==Object.class ?
                                objectMethod(proxy,method,args):null);
                evaluate.invoke(renderer,controlsSource,callback);
                lastControlsIssue=null;
                String page="unknown";
                try {
                    page=String.valueOf(renderer.getClass().getMethod("getUrl").invoke(renderer));
                } catch(Exception ignored) { }
                if(!page.equals(lastControlsPage)) {
                    lastControlsPage=page;
                    Log.i(TAG,"LOVEStv 1.45 ad-skip and seek adapter injected page="+page);
                }
            } catch(Exception | LinkageError error) {
                String issue=error.getClass().getName()+":"+String.valueOf(error.getMessage());
                if(!issue.equals(lastControlsIssue)) {
                    lastControlsIssue=issue;
                    Log.w(TAG,"1.45 playback controls not ready; native HbbTV unaffected",error);
                }
            } finally {
                if(!closed && running)main.postDelayed(this,5000);
            }
        }
    };
"""
source=source.replace(anchor,injected+anchor)
old='visibility(); Log.i(TAG,"Native HbbTV autostart completed without pressing any key; awaiting broadcaster LOVEStv side hints"); reply(STARTED,new Bundle()); inspect.run();'
assert source.count(old)==1, "5.3.12 browser startup must remain intact"
source=source.replace(old,old+" main.post(installPlaybackControls);")
assert 'activityParent.addView(root,new ViewGroup.LayoutParams(-1,-1))' in source
assert 'Native HbbTV autostart completed' in source
p.write_text(source,encoding="utf-8")
print("PASS: original 1.45 playback adapter attached to 5.3.12's unchanged TCL browser")
