(function(){try{
var name='__channelplusPlaybackDiagnostics23';
var diag=window[name];
if(!diag){
  diag={keys:[],advance:[],started:Date.now(),lastAttempt:0,seek:[],liveActions:[],lastLiveAction:0};window[name]=diag;
  var watch={13:1,37:1,38:1,39:1,40:1,8:1,461:1,403:1,404:1,405:1,406:1,415:1,413:1,19:1,412:1,417:1,463:1};
  function record(e,phase){if(!watch[e.keyCode])return;diag.keys.push({code:e.keyCode,type:e.type,phase:phase,prevented:!!e.defaultPrevented,trusted:!!e.isTrusted,age:Date.now()-diag.started});while(diag.keys.length>24)diag.keys.shift();}
  document.addEventListener('keydown',function(e){record(e,'capture');if(rtveScanKey(e)||scanKey(e))return;returnToLive(e);tryAdvance(e);},true);
  document.addEventListener('keydown',function(e){record(e,'bubble');},false);
  document.addEventListener('keyup',function(e){record(e,'capture');},true);
}
function tryAdvance(e){
  if(e.keyCode!==404||e.isTrusted!==true)return;
  if(location.origin!=='https://hbbtv.lovestv.es'||location.pathname.indexOf('/lovestv3-4.0.0/')!==0)return;
  var c=document.getElementById('_vue-hbbtv-player-player'),v=document.getElementById('_vue-hbbtv-player-video');
  if(!c||!v||v.tagName!=='VIDEO'||!/(^|\s)_vue-hbbtv-player-adBreak(\s|$)/.test(c.className))return;
  if(Date.now()-diag.lastAttempt<2500)return;
  diag.lastAttempt=Date.now();
  var attempt={age:diag.lastAttempt-diag.started,status:'checking',before:v.currentTime,duration:v.duration};
  diag.advance.push(attempt);while(diag.advance.length>6)diag.advance.shift();
  try{
    var duration=v.duration,target=duration-0.25,ok=false;
    if(!isFinite(duration)||duration<1||duration>180||v.readyState<1){attempt.status='not-ready';hint('Espera a que cargue el anuncio');return;}
    for(var r=0;r<v.seekable.length;r++)if(v.seekable.start(r)<=target&&v.seekable.end(r)>=target)ok=true;
    if(!ok){attempt.status='not-seekable';hint('El vídeo no ofrece avance hasta el final');return;}
    if(v.currentTime>=target){attempt.status='already-at-end';return;}
    v.currentTime=target;attempt.target=target;attempt.status='requested';
    e.preventDefault();e.stopImmediatePropagation();hint('Saltando anuncio…');
    window.setTimeout(function(){attempt.after300=v.currentTime;attempt.ended300=v.ended;attempt.seeking300=v.seeking;},300);
    window.setTimeout(function(){attempt.after1800=v.currentTime;attempt.duration1800=v.duration;attempt.ready1800=v.readyState;attempt.adBreak1800=/(^|\s)_vue-hbbtv-player-adBreak(\s|$)/.test(c.className);},1800);
  }catch(error){attempt.status='error';attempt.error=String(error).substring(0,160);hint('No se pudo aplicar el avance');}
}
function hint(message){
  var el=document.getElementById('channelplus-ad-test20');
  if(!el){el=document.createElement('div');el.id='channelplus-ad-test20';el.style.cssText='position:fixed;top:28px;left:28px;z-index:2147483647;padding:12px 18px;background:rgba(0,0,0,.85);color:white;font:20px sans-serif;border-radius:6px;pointer-events:none;';document.body.appendChild(el);}
  el.textContent=message;el.style.display='block';
  if(diag.hintTimer)window.clearTimeout(diag.hintTimer);
  diag.hintTimer=window.setTimeout(function(){el.style.display='none';},3000);
}
// RTVE Play uses an Angular component and its official seekToSeconds API.
// Inspect only the context attached to the actual player host, never app globals.
function rtveLauncherReady(){
  return (location.origin==='http://hbbtv.rtve.es'||location.origin==='https://hbbtv.rtve.es')&&location.pathname.indexOf('/hbbtv/redbutton/launcher/')===0&&document.readyState==='complete'&&!!document.getElementById('contentMain')&&!!(document.getElementById('sidewayBox')||document.getElementById('leftBanner'))&&!!(window.RTVE&&window.RTVE.hbbtv&&window.RTVE.hbbtv.main);
}
function rtveApplication(){
  return (location.origin==='http://www.rtve.es'||location.origin==='https://www.rtve.es')&&location.pathname.indexOf('/hbbtv/redbutton/play/hbbtv/app/')===0;
}
function rtveContext(){
  if(!rtveApplication())return null;
  var host=document.querySelector('rtve-play-player'),video=document.getElementById('videoObj');
  if(!host||!video||String(video.tagName).toUpperCase()!=='VIDEO'||!host.contains(video))return null;
  var rect=video.getBoundingClientRect(),style=window.getComputedStyle(video);
  if(rect.width<innerWidth*.5||rect.height<innerHeight*.5||style.visibility==='hidden'||style.display==='none')return null;
  var ctx=host.__ngContext__,list=[];
  if(Array.isArray(ctx)){
    list.push(ctx[8]);
    for(var i=0;i<ctx.length&&i<100;i++)if(ctx[i]&&typeof ctx[i]==='object'&&!Array.isArray(ctx[i]))list.push(ctx[i]);
  }
  var found=null;
  for(var j=0;j<list.length;j++){
    var c=list[j],p=c&&c.player;
    if(c&&p&&p.videoPlayer&&p.videoPlayer.nativeElement===video&&typeof p.seekToSeconds==='function'&&typeof c.showControls==='function'){
      if(found&&found.component!==c)return null;
      found={component:c,player:p,video:video};
    }
  }
  if(!found)return null;
  var component=found.component,player=found.player;
  if(component.isLoading||component.isAd||player.isAd||!player.videoData||player.videoData.isLive!==false)return null;
  // Do not take directional navigation from dialogs, menus or track selection.
  var dialog=document.querySelector('[role="dialog"],#initPopUp');
  if(dialog){var ds=window.getComputedStyle(dialog),dr=dialog.getBoundingClientRect();if(ds.display!=='none'&&ds.visibility!=='hidden'&&dr.width>0&&dr.height>0)return null;}
  if(component.listenerManager&&typeof component.listenerManager.getActiveListener==='function'){
    var listener=component.listenerManager.getActiveListener();
    if(listener!==component.listenerName)return null;
  }else return null;
  if(component.speedListOpened||component.subtitleListOpened||component.audioListOpened||component.territoriesListOpened)return null;
  if(component.overlayShowing&&component.currentPlayerSection!==1&&component.currentPlayerSection!==3)return null;
  if(!isFinite(video.duration)||video.duration<=0||video.readyState<1)return null;
  var available=ranges(video),allowed=[];
  for(var k=0;k<available.length;k++){var lo=Math.max(0,available[k][0]),hi=Math.min(video.duration-.25,available[k][1]);if(isFinite(lo)&&isFinite(hi)&&hi>=lo)allowed.push([lo,hi]);}
  if(!allowed.length)return null;
  found.info={ranges:allowed};return found;
}
function rtveState(){
  if(!rtveApplication())return null;
  var host=document.querySelector('rtve-play-player'),v=document.getElementById('videoObj'),c=rtveContext();
  return {host:!!host,componentContext:!!(host&&Array.isArray(host.__ngContext__)),ready:!!c,htmlVideo:!!(v&&String(v.tagName).toUpperCase()==='VIDEO')};
}
function rtveScanKey(e){
  if(!rtveApplication()||!e||e.isTrusted!==true)return false;
  var code=e.keyCode;if(code!==37&&code!==39&&code!==412&&code!==417){stopScan();return false;}
  var context=rtveContext();if(!context){stopScan();return false;}
  var now=Date.now(),direction=code===37||code===412?-1:1,previous=diag.scan;
  if(e.repeat||(previous&&now-previous.lastPress<150)){e.preventDefault();e.stopImmediatePropagation();return true;}
  var index=previous&&previous.player===context.player&&previous.direction===direction&&now-previous.lastPress<2000?Math.min(4,previous.index+1):0;
  var steps=[30,120,240,480,960],speeds=[2,4,8,16,32],before=context.video.currentTime;
  // Preserve accumulated rapid presses even when the decoder has not sought yet.
  var base=previous&&previous.player===context.player&&now-previous.lastPress<2000?previous.target:before;
  var target=clampTarget(base+direction*steps[index],context.info),entry={provider:'rtve',before:before,target:target,status:'checking'};
  diag.seek.push(entry);while(diag.seek.length>12)diag.seek.shift();
  try{
    if(context.player.seekToSeconds(target)!==true){entry.status='official-rejected';return false;}
    entry.status='requested';
    if(typeof context.player.play==='function')context.player.play();
    context.component.showControls();
    diag.scan={kind:'rtve',player:context.player,direction:direction,index:index,speed:speeds[index],seconds:steps[index],lastPress:now,target:target};
    e.preventDefault();e.stopImmediatePropagation();drawScan();return true;
  }catch(error){entry.status='error';entry.error=String(error).substring(0,120);stopScan();return false;}
}

function inApplication(){return location.origin==='https://hbbtv.lovestv.es'&&location.pathname.indexOf('/lovestv3-4.0.0/')===0;}
function ranges(video){var result=[];try{for(var i=0;i<video.seekable.length&&i<8;i++)result.push([video.seekable.start(i),video.seekable.end(i)]);}catch(ignore){}return result;}
function playerState(){
  var player=window.player,video=document.getElementById('_vue-hbbtv-player-video');
  if(!player||!video)return null;
  return {live:!!player.live,adBreak:!!player.onAdBreak,bumper:!!player.bumperUrl,visible:!!player.visible,adapted:!!player.__channelplusSeek23,seekable:ranges(video),liveWindow:liveWindow(player,video),disabledIntervals:(player.disableSeekBetween||[]).slice(0,12)};
}
function installAdaptation(){
  if(!inApplication()&&!rtveApplication())return;
  function tick(){try{installSeek();scanTick();drawSelection();drawLiveButton();drawAdButton();}catch(ignore){}}
  tick();
  if(!diag.adaptationTimer)diag.adaptationTimer=window.setInterval(tick,250);
}
function playbackContext(player){
  var root=document.getElementById('videoPlayer'),video=document.getElementById('_vue-hbbtv-player-video');
  return !!(inApplication()&&player&&(player.startOverDetails||player.live===false)&&root&&/(^|\s)visible(\s|$)/.test(root.className)&&video&&player.video===video&&video.tagName==='VIDEO');
}
// Use the same startover clock as LOVEStv's own live-delay indicator, and intersect
// it with seekable ranges. Duration alone may include content beyond live.
function liveWindow(player,video){
  if(!player||!video||video.readyState<1||!isFinite(video.duration)||video.duration<=0)return null;
  if(player.live===false){
    var vodRanges=ranges(video),vod=[];
    for(var vr=0;vr<vodRanges.length;vr++){var a=Math.max(0,vodRanges[vr][0]),b=Math.min(video.duration-.25,vodRanges[vr][1]);if(isFinite(a)&&isFinite(b)&&b>=a)vod.push([a,b]);}
    return vod.length?{edge:video.duration,clockSource:'vod-seekable',ranges:vod,first:vod[0][0],last:vod[vod.length-1][1]}:null;
  }
  if(video.duration<=180)return null;
  var d=player.startOverDetails;if(!d||d.liveTimeshift===null||d.liveTimeshift===undefined||d.liveTimeshift==='')return null;
  var offset=Number(d.liveTimeshift),start=Number(d.startTime);
  if(!isFinite(offset)||!isFinite(start)||start<=0)return null;
  var schedule=scheduledClock(player,video);
  var edge=schedule?schedule.edge:Math.min(video.duration-5,Math.abs(offset)+Math.max(0,Date.now()/1000-start));
  var available=ranges(video),allowed=[];
  for(var i=0;i<available.length;i++){var lo=Math.ceil(Math.max(0,available[i][0])),hi=Math.floor(Math.min(edge,available[i][1]));if(isFinite(lo)&&isFinite(hi)&&hi>=lo)allowed.push([lo,hi]);}
  if(!allowed.length)return null;
  return {edge:edge,clockSource:schedule?schedule.source:'legacy-timeshift',programmeStart:schedule?schedule.start:null,programmeEnd:schedule?schedule.end:null,ranges:allowed,first:allowed[0][0],last:allowed[allowed.length-1][1]};
}
// Remote scanning uses bounded one-second seeks, not unsupported decoder playback rates.
function scanAllowed(player){
  if(!playbackContext(player)||!player.visible||player.onAdBreak||player.bumperUrl||player.trackSelectOpen||document.querySelectorAll('.dialog').length)return false;
  for(var el=document.activeElement,n=0;el&&n<8;el=el.parentElement,n++)if(/^nav-[0-9]+$/.test(el.id||'')||/^(INPUT|SELECT|TEXTAREA)$/.test(el.tagName||''))return false;
  return !(typeof player.isSeekDisabled==='function'&&player.isSeekDisabled());
}
function stopScan(){
  diag.scan=null;
  var badge=document.getElementById('channelplus-scan27');if(badge)badge.style.display='none';
}
function scanKey(e){
  if(!e||e.isTrusted!==true)return false;
  var code=e.keyCode,player=window.player;
  if(code!==37&&code!==39&&code!==412&&code!==417){stopScan();return false;}
  if(!scanAllowed(player)){stopScan();return false;}
  var info=liveWindow(player,player.video);if(!info){hint('Esperando la referencia del programa');return false;}
  e.preventDefault();e.stopImmediatePropagation();
  var now=Date.now(),direction=code===37||code===412?-1:1,previous=diag.scan;
  if(e.repeat||(previous&&now-previous.lastPress<150))return true;
  var index=previous&&previous.player===player&&previous.direction===direction&&now-previous.lastPress<2000?Math.min(4,previous.index+1):0;
  var speeds=[2,4,8,16,32],steps=[30,120,240,480,960];
  var pending=Number(player.seekValue)||0;
  var before=player.video.currentTime,target=clampTarget(before+pending+direction*steps[index],info);
  if(typeof player.resetSeekValues==='function')player.resetSeekValues();
  // Restart normal playback automatically; no continuous scan and no extra OK.
  if(typeof player.play==='function')player.play();
  var scan={player:player,direction:direction,index:index,speed:speeds[index],seconds:steps[index],lastPress:now,target:target};
  diag.scan=scan;
  if(Math.abs(target-before)>.5)player.seek(target-before);
  if(typeof player.displayPlayer==='function')player.displayPlayer(4);
  drawScan();return true;
}
function drawScan(){
  var scan=diag.scan;if(!scan)return;
  var badge=document.getElementById('channelplus-scan27');
  if(!badge){badge=document.createElement('div');badge.id='channelplus-scan27';badge.setAttribute('aria-hidden','true');badge.style.cssText='position:fixed;bottom:160px;left:40px;z-index:2147483647;background:rgba(15,15,15,.9);color:white;border:1px solid #888;border-radius:10px;padding:16px 24px;font:24px sans-serif;pointer-events:none;';document.body.appendChild(badge);}
  var amount=scan.seconds<60?scan.seconds+' s':(scan.seconds/60)+' min';
  badge.textContent='×'+scan.speed+' · '+(scan.direction>0?'+':'−')+amount;badge.style.display='block';
}
function scanTick(){
  var scan=diag.scan;if(!scan)return;
  if((scan.kind==='rtve'? !rtveContext()||rtveContext().player!==scan.player : scan.player!==window.player||!scanAllowed(scan.player))||Date.now()-scan.lastPress>=4000){stopScan();return;}
  // Timer only maintains presentation; a seek is requested exclusively by a key press.
}
function scheduledClock(player,video){
  // Only inspect component instances attached to this actual video ancestry.
  var el=video;
  for(var n=0;el&&n<8;n++,el=el.parentElement){
    var vue=el.__vue__,component=el.__vueParentComponent;
    var candidates=[vue&&vue.source,component&&component.props&&component.props.source];
    for(var k=0;k<candidates.length;k++){
      var source=candidates[k];if(!source)continue;
      var start=Number(source.scheduledStart),end=Number(source.scheduledEnd),span=end-start,now=Date.now()/1000;
      if(isFinite(start)&&isFinite(end)&&start>1000000000&&end>start&&now>=start&&Math.abs(span-video.duration)<=Math.max(120,video.duration*.05))return {edge:Math.max(0,Math.min(video.duration-5,now-start)),source:'programme-schedule',start:start,end:end};
    }
  }
  return null;
}
function clampTarget(requested,windowInfo){
  var best=null,distance=Infinity;
  for(var i=0;i<windowInfo.ranges.length;i++){var r=windowInfo.ranges[i],candidate=Math.max(r[0],Math.min(r[1],requested)),diff=Math.abs(candidate-requested);if(diff<distance){best=candidate;distance=diff;}}
  return best;
}
function installSeek(){
  var player=window.player;
  if(!player||player.__channelplusSeek23||typeof player.navigate!=='function'||typeof player.seek!=='function')return;
  var originalNavigate=player.navigate,originalSeek=player.seek;
  player.seek=function(delta){
    if(!playbackContext(this)||this.onAdBreak||this.bumperUrl)return originalSeek.apply(this,arguments);
    var video=this.video,entry={before:video.currentTime,delta:delta,status:'checking'};
    diag.seek.push(entry);while(diag.seek.length>12)diag.seek.shift();
    if(typeof this.isSeekDisabled==='function'&&this.isSeekDisabled()){entry.status='official-disabled-interval';return;}
    var info=liveWindow(this,video);if(!info){entry.status='live-limit-unavailable';hint('El límite del directo aún no está disponible');return;}
    var pending=Number(this.seekValue)||0,multiplier=Number(this.seekMultiplier)||1;
    var desired=video.currentTime+pending+Number(delta)*multiplier;
    if(!isFinite(desired)||multiplier<=0){entry.status='invalid-target';return;}
    var target=clampTarget(desired,info),adjusted=(target-video.currentTime-pending)/multiplier;
    entry.requested=desired;entry.target=target;entry.liveEdge=info.last;
    if(Math.abs(adjusted)<0.001){entry.status='at-boundary';return;}
    entry.status=target===desired?'delegated':'clamped';
    return originalSeek.call(this,adjusted);
  };
  player.navigate=function(event){
    var code=event&&event.keyCode;
    if((code!==37&&code!==39)||!this.live||!playbackContext(this)||this.onAdBreak||this.bumperUrl)return originalNavigate.apply(this,arguments);
    var live=this.live;
    try{this.live=false;return originalNavigate.apply(this,arguments);}
    finally{this.live=live;}
  };
  player.__channelplusSeek23=true;
}
function returnToLive(event){
  if(!event||event.keyCode!==403||event.isTrusted!==true)return;
  var player=window.player;
  if(!playbackContext(player)||!player.live||player.onAdBreak||player.bumperUrl)return;
  if(typeof player.isSeekDisabled==='function'&&player.isSeekDisabled())return;
  var info=liveWindow(player,player.video);
  if(!info){hint('El límite del directo aún no está disponible');return;}
  event.preventDefault();event.stopImmediatePropagation();
  if(Date.now()-diag.lastLiveAction<1000)return;
  diag.lastLiveAction=Date.now();
  var entry={before:player.video.currentTime,edge:info.last,status:'checking'};
  diag.liveActions.push(entry);while(diag.liveActions.length>6)diag.liveActions.shift();
  try{
    // Keep two seconds of headroom at the latest playable point.
    var last=info.ranges[info.ranges.length-1],target=Math.max(last[0],last[1]-2);entry.target=target;
    if(typeof player.resetSeekValues==='function')player.resetSeekValues();
    if(typeof player.play==='function')player.play();
    if(Math.abs(target-player.video.currentTime)>0.5)player.seek(target-player.video.currentTime);
    entry.status='requested';hint('Volviendo al directo…');
    window.setTimeout(function(){entry.after=player.video.currentTime;entry.paused=player.video.paused;},1800);
  }catch(error){entry.status='error';entry.error=String(error).substring(0,160);hint('No se pudo volver al directo');}
}
function drawLiveButton(){
  var button=document.getElementById('channelplus-live23'),player=window.player,controls=document.getElementById('_vue-hbbtv-player-player');
  var show=playbackContext(player)&&player.live&&!player.onAdBreak&&!player.bumperUrl&&controls&&/(^|\s)_vue-hbbtv-player-show(\s|$)/.test(controls.className);
  if(!show){if(button)button.style.display='none';return;}
  var info=liveWindow(player,player.video);if(!info||info.last-player.video.currentTime<=3){if(button)button.style.display='none';return;}
  if(!button){button=document.createElement('button');button.id='channelplus-live23';button.type='button';button.tabIndex=-1;button.setAttribute('aria-label','Volver al directo. Botón rojo del mando');button.style.cssText='position:fixed;top:28px;right:40px;z-index:2147483645;padding:12px 20px;border:1px solid rgba(255,255,255,.7);border-radius:24px;background:radial-gradient(circle at 20px center,#dc4545 0 5px,transparent 5.5px),rgba(15,15,15,.88);color:white;font:20px sans-serif;padding-left:36px;';
    button.onclick=function(e){e.preventDefault();e.stopPropagation();returnToLive({keyCode:403,isTrusted:e.isTrusted,preventDefault:function(){},stopImmediatePropagation:function(){}});};document.body.appendChild(button);}
  var label='Volver al directo';if(button.textContent!==label)button.textContent=label;
  button.style.display='block';
}
function drawAdButton(){
  var button=document.getElementById('channelplus-ad36'),root=document.getElementById('videoPlayer'),controls=document.getElementById('_vue-hbbtv-player-player'),video=document.getElementById('_vue-hbbtv-player-video');
  var show=inApplication()&&root&&/(^|\s)visible(\s|$)/.test(root.className)&&controls&&/(^|\s)_vue-hbbtv-player-adBreak(\s|$)/.test(controls.className)&&video&&video.tagName==='VIDEO';
  if(!show){if(button)button.style.display='none';return;}
  if(!button){
    button=document.createElement('button');button.id='channelplus-ad36';button.type='button';button.tabIndex=-1;
    button.setAttribute('aria-label','Saltar anuncio. Botón verde del mando');
    button.style.cssText='position:fixed;top:28px;right:40px;z-index:2147483645;padding:12px 20px 12px 36px;border:1px solid rgba(255,255,255,.7);border-radius:24px;background:radial-gradient(circle at 20px center,#63bd7a 0 5px,transparent 5.5px),rgba(15,15,15,.88);color:white;font:20px sans-serif;';
    button.textContent='Saltar anuncio';
    button.onclick=function(e){if(e.isTrusted!==true)return;e.preventDefault();e.stopPropagation();tryAdvance({keyCode:404,isTrusted:true,preventDefault:function(){},stopImmediatePropagation:function(){}});};
    document.body.appendChild(button);
  }
  button.style.display='block';
}
function drawSelection(){
  var frame=document.getElementById('channelplus-focus22'),active=document.activeElement,root=document.getElementById('videoPlayer');
  var playing=root&&/(^|\s)visible(\s|$)/.test(root.className);
  var eligible=active&&active!==document.body&&(/(^|\s)(snFocusable|focusable|settingsFocusable|dialogFocusable|programInfoFocusable)(\s|$)/.test(String(active.className||''))||/^(BUTTON|A)$/.test(active.tagName));
  var dialog=active&&/(^|\s)dialogFocusable(\s|$)/.test(String(active.className||''));
  if(!document.hasFocus()||!inApplication()||(playing&&!dialog)||!eligible){if(frame)frame.style.display='none';return;}
  // Carousel containers own DOM focus; the official CardItem owns visual selection.
  var cards=active.querySelectorAll('._container_1whzl_26._focused_1whzl_37');
  var target=cards.length===1?cards[0]:active;
  var box=target.getBoundingClientRect(),style=window.getComputedStyle(target);
  if(box.width<=0||box.height<=0||box.width>innerWidth*.6||box.height>innerHeight*.45||box.left<0||box.top<0||box.right>innerWidth||box.bottom>innerHeight||target.querySelectorAll('.snFocusable,.focusable,.settingsFocusable,.dialogFocusable,.programInfoFocusable,button,[role=button]').length>0||style.display==='none'||style.visibility==='hidden'||style.opacity==='0'){if(frame)frame.style.display='none';return;}
  if(!frame){frame=document.createElement('div');frame.id='channelplus-focus22';frame.setAttribute('aria-hidden','true');frame.style.cssText='position:fixed;z-index:2147483646;box-sizing:border-box;border:3px solid #fff;border-radius:8px;box-shadow:0 0 0 1px rgba(0,0,0,.65);pointer-events:none;background:transparent;';document.body.appendChild(frame);}
  var x=Math.max(2,box.left-3),y=Math.max(2,box.top-3),w=Math.min(box.width+6,innerWidth-x-2),h=Math.min(box.height+6,innerHeight-y-2);
  var bounds=[x,y,w,h].join(',');if(frame.__bounds!==bounds){frame.style.left=x+'px';frame.style.top=y+'px';frame.style.width=w+'px';frame.style.height=h+'px';frame.__bounds=bounds;}
  frame.style.display='block';
}
installAdaptation();
function sceneInfo(){
  function chain(el){var result=[];for(var i=0;el&&i<6;i++,el=el.parentElement){var n=nodeInfo(el),s=window.getComputedStyle(el);n.background=s.backgroundColor;n.transform=s.transform;result.push(n);}return result;}
  var headings=[],h=document.querySelectorAll('h1,[role="dialog"] h2');
  for(var i=0;i<h.length&&headings.length<4;i++){var r=h[i].getBoundingClientRect();if(r.width>0&&r.height>0)headings.push({text:(h[i].innerText||'').substring(0,220),layers:chain(h[i])});}
  var nav=document.getElementById('nav-0'),covers=[];
  for(var j=0;j<2;j++){var el=document.elementFromPoint(j?640:100,j?360:100);if(el)covers.push(chain(el));}
  return {navigation:!!nav,headings:headings,navLayers:chain(nav),bodyLayers:chain(document.body),covers:covers};
}
function nodeInfo(el){var r=el.getBoundingClientRect(),s=window.getComputedStyle(el);return {tag:el.tagName,id:el.id||'',cls:String(el.className||'').substring(0,100),x:r.left,y:r.top,w:r.width,h:r.height,display:s.display,visibility:s.visibility,opacity:s.opacity,z:s.zIndex};}
var nodes=[],all=document.querySelectorAll('[id]');
for(var i=0;i<all.length&&nodes.length<40;i++)if(/player|video|control|seek|progress|playposition|playtime|pause|trackpopup/i.test(all[i].id))nodes.push(nodeInfo(all[i]));
var media=[],els=document.querySelectorAll('video,audio,object');
for(var j=0;j<els.length&&media.length<8;j++){var el=els[j],m=nodeInfo(el);m.type=el.getAttribute('type')||'';var props=['playState','playPosition','playTime','paused','currentTime','duration','readyState'];for(var p=0;p<props.length;p++){try{var v=el[props[p]];if(typeof v==='boolean'||typeof v==='number')m[props[p]]=v;}catch(ignore){}}media.push(m);}
var frames=[],ff=document.querySelectorAll('iframe');
for(var k=0;k<ff.length&&frames.length<4;k++){var frame=nodeInfo(ff[k]);try{var doc=ff[k].contentDocument;frame.readable=!!doc;frame.title=doc?doc.title:'';frame.playerNodes=doc?doc.querySelectorAll('video,audio,object,[id*=player],[id*=control]').length:0;}catch(ignore){frame.readable=false;}frames.push(frame);}
var ke={},names=['VK_ENTER','VK_UP','VK_DOWN','VK_LEFT','VK_RIGHT','VK_PLAY','VK_PAUSE','VK_PLAY_PAUSE','VK_REWIND','VK_FAST_FWD','VK_BACK'];
for(var q=0;q<names.length;q++)try{var value=window.KeyEvent&&window.KeyEvent[names[q]];if(typeof value==='number')ke[names[q]]=value;}catch(ignore){}
var controls=[],buttons=document.querySelectorAll('button,[role=button],a,[tabindex]');
for(var b=0;b<buttons.length&&controls.length<16;b++){var box=buttons[b].getBoundingClientRect();if(box.width>0&&box.height>0){var item=nodeInfo(buttons[b]);item.text=(buttons[b].innerText||'').substring(0,80);controls.push(item);}}
var text=(document.body?document.body.innerText:'')||'',notice=text.search(/compatib|compatible|soportad|supported/i);
return JSON.stringify({url:location.origin+location.pathname,title:document.title,scene:sceneInfo(),design41:window.__cpDesign41?window.__cpDesign41.state:null,viewport:{width:innerWidth,height:innerHeight,dpr:devicePixelRatio},keyEvents:diag.keys.slice(),adAdvance:diag.advance.slice(),seekAdapter:diag.seek.slice(),liveActions:diag.liveActions.slice(),scan:diag.scan?{direction:diag.scan.direction,speed:diag.scan.speed}:null,playerState:playerState(),rtveAdapter:rtveState(),rtveLauncherReady:rtveLauncherReady(),rtveLauncherAt:Date.now(),documentFocus:document.hasFocus(),keyConstants:ke,active:document.activeElement?{tag:document.activeElement.tagName,id:document.activeElement.id||''}:null,playerNodes:nodes,media:media,frames:frames,controls:controls,ua:navigator.userAgent,notice:notice<0?'Sin aviso de compatibilidad en texto':text.substring(Math.max(0,notice-100),notice+350)});
}catch(e){return JSON.stringify({error:String(e)});}})()