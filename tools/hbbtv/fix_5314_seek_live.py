#!/usr/bin/env python3
"""LOVEStv 5.3.14: reliable native-key seek and one-tap return to already tuned TDT.

Apply AFTER all 5.3.13 scripts. Do not alter the 5.3.12 TCL Activity
mount, Tbrowser lifecycle, or the working 5.3.13 green ad skipper.
"""
from pathlib import Path

base=Path('owntv_tdt/app')
jsfile=base/'src/hbbtv32/assets/lovestv_playback_145.js'
s=jsfile.read_text(encoding='utf8')
def replace(old,new, label):
    global s
    n=s.count(old)
    if n!=1: raise RuntimeError(f'JS {label}: expected one match, got {n}')
    s=s.replace(old,new)

replace('''  if(!playbackContext(player)||!player.visible||player.onAdBreak||player.bumperUrl||player.trackSelectOpen||document.querySelectorAll('.dialog').length)return false;''',
'''  // The 1.45 adapter checked player.visible as well as the real DOM video.
  // TCL reports this Vue flag false on some valid start-over/VOD screens.
  // The visible #videoPlayer and video element are the authoritative checks.
  if(!playbackContext(player)||player.onAdBreak||player.bumperUrl||player.trackSelectOpen||document.querySelectorAll('.dialog').length)return false;''','less strict scan eligibility')
replace('''  if(player.live===false){
    var vodRanges=ranges(video),vod=[];''',
'''  if(player.live===false || !player.startOverDetails){
    // Always prefer the HTML video's real seekable ranges. Some TCL LOVEStv
    // pages omit startOverDetails even while a valid time-shift video plays.
    var vodRanges=ranges(video),vod=[];''','use seekable range fallback')
replace('''  if(e.repeat||(previous&&now-previous.lastPress<150))return true;
  var index=previous&&previous.player===player''',
'''  if(e.repeat||(previous&&now-previous.lastPress<150))return true;
  diag.lastNativeSeekKey={code:code,at:now};
  var index=previous&&previous.player===player''','deduplicate native keys')
replace('''  if(Math.abs(target-before)>.5)player.seek(target-before);
  if(typeof player.displayPlayer==='function')player.displayPlayer(4);''',
'''  if(Math.abs(target-before)>.5) {
    try { player.seek(target-before); }
    catch(seekError) { diag.seek.push({source:'progressive',error:String(seekError)}); }
    // The TCL renderer sometimes accepts player.seek() but fails to seek.
    // Check the real video position and fall back to an allowed HTML5 seek.
    (function(v,goal){window.setTimeout(function(){
      try {
        if(v!==player.video || v.readyState<1 || Math.abs(v.currentTime-goal)<5)return;
        var available=ranges(v),valid=false;
        for(var k=0;k<available.length;k++)if(goal>=available[k][0]&&goal<=available[k][1])valid=true;
        if(valid) {v.currentTime=goal; diag.seek.push({source:'html-video-fallback',target:goal});}
      }catch(error){diag.seek.push({source:'html-video-fallback',error:String(error)});}
    },450);})(player.video,target);
  }
  if(typeof player.displayPlayer==='function')player.displayPlayer(4);''','HTML video seek fallback')
# Explicit trusted native command: engine.handleKey may navigate but not generate DOM
# keydown in TCL browser; player key already passed app gate and is user-originated.
replace('''function drawScan(){''',
'''// Invoked from Channel+'s hardware-remote service only, after giving the
// browser's trusted keydown first opportunity. Do not call the ad skipper here.
window.__channelplusPlaybackNativeKey=function(androidCode){
  try {
    if(androidCode===90)androidCode=39; // Android media fast-forward
    if(androidCode===89)androidCode=37; // Android media rewind
    if(androidCode===22)androidCode=39; // Android dpad right
    if(androidCode===21)androidCode=37; // Android dpad left
    if(androidCode===183)androidCode=403; // Android PROG_RED
    if(androidCode===403){
      if(!playbackContext(window.player) && !rtveContext())return 'red-not-in-playback';
      diag.nativeLiveRequest=Date.now();
      return 'exit-to-tdt'; // Java releases HbbTV gate and shows native tuner
    }
    if(androidCode!==37&&androidCode!==39&&androidCode!==412&&androidCode!==417)return 'ignored';
    if(diag.lastNativeSeekKey && diag.lastNativeSeekKey.code===androidCode &&
       Date.now()-diag.lastNativeSeekKey.at<650)return 'already-handled';
    var e={keyCode:androidCode,isTrusted:true,repeat:false,
      preventDefault:function(){},stopImmediatePropagation:function(){}};
    var result=rtveScanKey(e)||scanKey(e);
    diag.nativeSeekResult={code:androidCode,ok:!!result,at:Date.now(),
      hasPlayer:!!window.player,hasVideo:!!document.getElementById('_vue-hbbtv-player-video')};
    return result?'seek-requested':'seek-not-allowed';
  } catch(err){diag.nativeSeekError=String(err);return 'seek-error';}
};
function drawScan(){''','trusted Android remote fallback')
# Replace stale seek-based returnToLive with a direct native TV command indicator.
start=s.index('function returnToLive(event){')
end=s.index('\nfunction drawLiveButton(){', start)
s=s[:start]+'''function returnToLive(event){
  if(!event||event.keyCode!==403||event.isTrusted!==true)return;
  if(!playbackContext(window.player)&&!rtveContext())return;
  event.preventDefault();event.stopImmediatePropagation();
  // Channel+ deliberately keeps its DVB/TDT TvView tuned underneath LOVEStv.
  // The hardware RED command asks Java to hide only the native HbbTV browser
  // and release remote capture; seeking the edge of a catch-up clip is NOT live.
  diag.lastLiveAction=Date.now();
  hint('Volviendo a la TDT en directo…');
}'''+s[end:]
replace("var show=playbackContext(player)&&player.live&&!player.onAdBreak&&!player.bumperUrl&&controls&&/(^|\\s)_vue-hbbtv-player-show(\\s|$)/.test(controls.className);",
"var show=playbackContext(player)&&!player.onAdBreak&&!player.bumperUrl&&controls&&/(^|\\s)_vue-hbbtv-player-show(\\s|$)/.test(controls.className);",
'live label in all video playback')
replace("  var info=liveWindow(player,player.video);if(!info||info.last-player.video.currentTime<=3){if(button)button.style.display='none';return;}",
"  // Return-to-live is native TDT, also meaningful for catch-up and VOD; do not gate on seekable live edge.", 'live button no timeshift requirement')
replace("    button.onclick=function(e){e.preventDefault();e.stopPropagation();returnToLive({keyCode:403,isTrusted:e.isTrusted,preventDefault:function(){},stopImmediatePropagation:function(){}});};document.body.appendChild(button);",
"    button.style.pointerEvents='none';document.body.appendChild(button);", 'display a remote hint not a dead click target')
replace("  var label='Volver al directo';if(button.textContent!==label)button.textContent=label;",
"  var label='ROJO · Volver al directo';if(button.textContent!==label)button.textContent=label;",'red remote hint')
assert 'function tryAdvance(e)' in s and "e.keyCode!==404" in s  # green unchanged
assert 'var speeds=[2,4,8,16,32],steps=[30,120,240,480,960];' in s
jsfile.write_text(s, encoding='utf8')

service=base/'src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java'
s=service.read_text(encoding='utf8')
def jreplace(old,new,label):
    global s
    n=s.count(old)
    if n!=1: raise RuntimeError(f'Java {label}: expected one match, got {n}')
    s=s.replace(old,new)
# Keep an easily recognized request, with all JS evaluated on main thread.
anchor='    private void prepareKeys() throws ReflectiveOperationException {'
new='''    // The real TCL Tbrowser does not reliably emit a DOM keydown for all
    // Android remote scan keys. The old JS listener remains the first path;
    // this hardware-key fallback invokes the same guarded player seeking logic.
    // All seek positions remain inside provider-reported HTML5 seekable ranges.
    private void forwardRemotePlaybackKey(int code) {
        if(closed || !running || browser==null || !ready || hidden) return;
        try {
            Object common=browser.getClass().getMethod("getCommonWebView",String.class)
                    .invoke(browser,"hbbtv_tbrowser");
            if(common==null) return;
            java.lang.reflect.Field f=common.getClass().getDeclaredField("mWebView");
            f.setAccessible(true);
            Object webview=f.get(common);
            if(webview==null) return;
            String page=String.valueOf(webview.getClass().getMethod("getUrl").invoke(webview));
            // Never inject hardware commands into arbitrary website/app pages.
            boolean love=page.startsWith("https://hbbtv.lovestv.es/lovestv3-4.0.0/");
            boolean rtve=page.startsWith("http://www.rtve.es/hbbtv/redbutton/play/hbbtv/app/") ||
                    page.startsWith("https://www.rtve.es/hbbtv/redbutton/play/hbbtv/app/");
            if(!love && !rtve) return;
            Method evaluate=null;
            for(Method m:webview.getClass().getMethods()) {
                Class<?>[] args=m.getParameterTypes();
                if(m.getName().equals("evaluateJavascript") && args.length==2 &&
                    args[0]==String.class && args[1].isInterface()) { evaluate=m; break; }
            }
            if(evaluate==null)return;
            Class<?> callbackType=evaluate.getParameterTypes()[1];
            ClassLoader loader=callbackType.getClassLoader();
            if(loader==null)loader=getClass().getClassLoader();
            final long tune=generation, id=channelId;
            Object callback=Proxy.newProxyInstance(loader,new Class<?>[]{callbackType},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return objectMethod(proxy,method,args);
                if(args!=null && args.length>0){
                    String outcome=String.valueOf(args[0]);
                    if(outcome.contains("exit-to-tdt"))main.post(()->{
                        if(closed || !running || generation!=tune || channelId!=id || hidden)return;
                        hidden=true; visibility();
                        // Shared protocol already has HIDE; echo it to the player
                        // so it drops HbbTvInputGate and restores native TDT keys.
                        reply(HIDE,new Bundle());
                        Log.i(TAG,"LOVEStv: native return to tuned TDT requested by RED");
                    });
                    else if(!outcome.contains("already-handled") && !outcome.contains("ignored"))
                        Log.i(TAG,"LOVEStv remote playback key="+code+" result="+outcome);
                }
                return null;
            });
            String script="(function(){return typeof window.__channelplusPlaybackNativeKey==='function'"+
                "?window.__channelplusPlaybackNativeKey("+code+"): 'controls-not-loaded';})()";
            evaluate.invoke(webview,script,callback);
        } catch(Exception | LinkageError error) {
            Log.w(TAG,"LOVEStv remote playback fallback unavailable",error);
        }
    }
    private void prepareKeys() throws ReflectiveOperationException {'''
jreplace(anchor,new,'forwardRemotePlaybackKey')
# Existing key() relays Android key to vendor HBBTV, then sends ACK.
anchor='''        result.putLong(KEY_EPOCH,data.getLong(KEY_EPOCH)); result.putLong(GENERATION,data.getLong(GENERATION)); reply(KEY_RESULT,result);'''
new='''        result.putLong(KEY_EPOCH,data.getLong(KEY_EPOCH)); result.putLong(GENERATION,data.getLong(GENERATION)); reply(KEY_RESULT,result);
        // Delay briefly so trusted DOM keydown can process first; the script
        // de-duplicates it if the Java fallback subsequently reaches the page.
        if(code==KeyEvent.KEYCODE_DPAD_LEFT || code==KeyEvent.KEYCODE_DPAD_RIGHT ||
           code==KeyEvent.KEYCODE_MEDIA_REWIND || code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
           code==KeyEvent.KEYCODE_PROG_RED)
            main.postDelayed(()->forwardRemotePlaybackKey(code),120);'''
jreplace(anchor,new,'hardware key forward')
anchor='''        }
    }
    private void key(Bundle data) {'''
new='''        }
        // Some TCL remotes send media-transport keycodes instead of dpad keys.
        if(keys.containsKey(KeyEvent.KEYCODE_DPAD_LEFT))
            keys.put(KeyEvent.KEYCODE_MEDIA_REWIND,keys.get(KeyEvent.KEYCODE_DPAD_LEFT));
        if(keys.containsKey(KeyEvent.KEYCODE_DPAD_RIGHT))
            keys.put(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,keys.get(KeyEvent.KEYCODE_DPAD_RIGHT));
    }
    private void key(Bundle data) {'''
jreplace(anchor,new,'map transport keys')
assert 'activityParent.addView(root,new ViewGroup.LayoutParams(-1,-1))' in s
assert 'main.post(installPlaybackControls)' in s
assert 'Native HbbTV autostart completed' in s
service.write_text(s,encoding='utf8')

view=base/'src/main/java/tv/own/owntv/features/hbbtv/HbbTvPlayerView.java'
s=view.read_text(encoding='utf8')
old='''        if(!colour && !(gate.interactive() && navigation)) return false;'''
assert s.count(old)==1, 'view key gate changed'
s=s.replace(old,'''        boolean transport=code==KeyEvent.KEYCODE_MEDIA_REWIND ||
                code==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
        if(!colour && !(gate.interactive() && (navigation || transport))) return false;''')
old='''            case FAILED: fail("Runtime initialization failed; see preceding ChannelplusHbb log",null); break;'''
assert s.count(old)==1,'view message switch changed'
s=s.replace(old,'''            case HIDE:
                // Service asked to close LOVEStv playback and return to tuner;
                // it is already visually hidden. Do not detune or kill HbbTV.
                gate.leave();
                Log.i(TAG,"Returned to live TDT; remote keys restored");
                break;
            case FAILED: fail("Runtime initialization failed; see preceding ChannelplusHbb log",null); break;''')
assert 'Delivering queued colour key=' in s
view.write_text(s,encoding='utf8')
print('PASS 5.3.14: checked UI seek + native hardware fallback; red returns to existing TDT; green ad skip and 5.3.12 native browser untouched')