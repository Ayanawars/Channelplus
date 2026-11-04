#!/usr/bin/env python3
"""Unreleased Channel+ 5.3.15 fixes: restore HbbTV focus, horizontal left channel bar, tuner/IPTV timeshift.

Apply after existing 5.3.14 scripts in CI. Do NOT publish a GitHub Release.
"""
from pathlib import Path
P=Path('owntv_tdt/app/src/main/java/tv/own/owntv')
CORE=Path('owntv_core_tdt')
def once(path,old,new):
    s=path.read_text(encoding='utf8')
    n=s.count(old)
    if n!=1:raise RuntimeError(f'{path}: expected one anchor, got {n}: {old[:120]!r}')
    path.write_text(s.replace(old,new),encoding='utf8')

# 1. HbbTV: alpha=0 was still an attached full-screen native View ABOVE Compose.
# Android can leave it as the focused input target, so keys never reach the PlayerHud.
service=P.parents[2]/'channelplus/lovestvprobe/HbbTvRuntimeService.java'
# P ends tv/own/owntv, P.parents[1]=tv, P.parents[0]=tv/own; correct path = tv/own/channelplus
service=Path('owntv_tdt/app/src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java')
assert service.exists(),service
once(service,
    'root.setAlpha(show ? 1f : 0f); if(show) root.bringToFront();',
    '''root.setAlpha(show ? 1f : 0f);
        // Invisible alpha alone leaves the TCL browser's full-screen view over
        // Compose, retaining hardware-remote focus even after a Back/Red exit.
        root.setVisibility(show ? View.VISIBLE : View.GONE);
        root.setDescendantFocusability(show ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if(show) root.bringToFront(); else {
            root.clearFocus();
            if(activityParent!=null) {
                View content=activityParent.findViewById(android.R.id.content);
                if(content!=null) content.requestFocus();
            }
        }''')
# `HIDE` must restore Compose focus even when browser catches BACK and native key gate leaves.
activity=P/'MainActivity.kt'
once(activity,
    '    var hbbReturnHandler: ((String, Long) -> Unit)? = null',
    '''    var hbbReturnHandler: ((String, Long) -> Unit)? = null
    // Raised only after TCL has returned to the still-tuned channel.
    // Compose PlayerHud then reacquires keyboard focus without retuning TDT.
    var hbbFocusRestorationTick by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    fun onHbbBrowserHidden() { hbbFocusRestorationTick += 1 }''')
view=P/'features/hbbtv/HbbTvPlayerView.java'
once(view,
    'gate.leave(); send(HIDE,channel());\n                    Log.d(TAG,"LOVEStv finished; Channel+ remote navigation restored");',
    '''gate.leave(); send(HIDE,channel());
                    if(activity instanceof tv.own.owntv.MainActivity)
                        ((tv.own.owntv.MainActivity)activity).onHbbBrowserHidden();
                    Log.d(TAG,"LOVEStv finished; Channel+ remote navigation restored");''')
once(view,
    '''case HIDE:
                // Service asked to close LOVEStv playback and return to tuner;
                // it is already visually hidden. Do not detune or kill HbbTV.
                gate.leave();''',
    '''case HIDE:
                // Browser hidden after return-to-live; restore Compose input focus.
                gate.leave();
                if(activity instanceof tv.own.owntv.MainActivity)
                    ((tv.own.owntv.MainActivity)activity).onHbbBrowserHidden();''')
hud=P/'player/PlayerHud.kt'
once(hud,
    '    inert: Boolean = false,',
    '    inert: Boolean = false,\n    hbbFocusRestorationTick: Int = 0,')
# runs after the catchFocus requester has been created and after controlsVisible's remembered state
assert 'val catchFocus = remember { FocusRequester() }' in hud.read_text()
once(hud,
    '    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }',
    '''    // TCL browser has just been hidden; the Compose HUD must own the next OK.
    // Leave the HUD hidden: the first OK reveals its normal transport bar.
    LaunchedEffect(hbbFocusRestorationTick) {
        if (hbbFocusRestorationTick > 0) {
            controlsVisible = false
            kotlinx.coroutines.delay(32)
            runCatching { catchFocus.requestFocus() }
        }
    }
    LaunchedEffect(forceShow) { if (forceShow) controlsVisible = true }''')
shell=P/'features/shell/OwnTVShell.kt'
once(shell,
    '''                    inert = showChannelList || showHistoryList || showCategoryBrowser || showSubtitleSearch || showLocalSubPicker || showOtherChannels,''',
    '''                    inert = showChannelList || showHistoryList || showCategoryBrowser || showSubtitleSearch || showLocalSubPicker || showOtherChannels,
                    hbbFocusRestorationTick = hbbActivity?.hbbFocusRestorationTick ?: 0,''')

# 2. Keep the existing right-side history overlay and category browser; first-left
# channel list switches to a horizontal, fullscreen video-preserving Compose strip.
s=shell.read_text(encoding='utf8')
marker='// First Left — the channels of the current category.'
assert s.count(marker)==1
front,tail=s.split(marker,1)
old='tv.own.owntv.features.shell.components.ChannelListOverlay('
assert tail.count(old)>=2, 'Expected first-left AND history overlay'
i=tail.index(old)
j=tail.index('\n                        )',i)+len('\n                        )')
replacement='''tv.own.owntv.features.shell.components.HorizontalChannelOverlay(
                            channels = zapChannels,
                            currentId = zapChannels.firstOrNull { ch -> previewChannel?.let {
                                tv.own.owntv.core.live.ChannelVariants.key(ch) == tv.own.owntv.core.live.ChannelVariants.key(it)
                            } == true }?.id ?: previewChannel?.id,
                            nowPlaying = overlayNowPlaying,
                            nowProgrammes = overlayProgrammes,
                            showNumbers = directTuneEnabled,
                            onSelect = { liveVm.ensurePlaying(it); showChannelList = false },
                            onDismiss = { showChannelList = false },
                            modifier = Modifier.fillMaxSize(),
                        )'''
tail=tail[:i]+replacement+tail[j:]
assert 'ChannelListOverlay(' in tail, 'History overlay must remain'
shell.write_text(front+marker+tail,encoding='utf8')

# 3. IPTV starts buffering by default if user never explicitly disabled it;
# preserve a saved false preference. Storage safeguard already exists in TimeshiftManager.
settings=CORE/'core/src/main/java/tv/own/owntv/core/settings/SettingsRepository.kt'
once(settings,
    '''     *  full-screen. Off by default: it writes to the device's storage continuously. */
    val timeshiftEnabled: Flow<Boolean> = prefsFlow { it[Keys.TIMESHIFT_ENABLED] ?: false }''',
    '''     *  full-screen. On for compatible IPTV channels unless the user explicitly switches it off.
     *  The bounded cache still requires spare space (TimeshiftRules.canStart). */
    val timeshiftEnabled: Flow<Boolean> = prefsFlow { it[Keys.TIMESHIFT_ENABLED] ?: true }''')
vm=P/'features/settings/SettingsViewModel.kt'
once(vm,
    '''settings.timeshiftEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)''',
    '''settings.timeshiftEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)''')
# Make the app's live setting accurate about IPTV disk usage and vendor TDT.
appStrings=Path('owntv_tdt/app/src/main/res/values/strings.xml')
esStrings=Path('owntv_tdt/app/src/main/res/values-es/strings.xml')
for path,radio,notes in [(appStrings,'Radio',
    'IPTV stores a rolling buffer while playing compatible streams; you can turn it off here. TDT pause and rewind need native TCL tuner support, which the app cannot force.'),
    (esStrings,'Radio',
    'IPTV guarda un búfer temporal en emisiones compatibles; puedes desactivarlo aquí. La pausa y el rebobinado TDT requieren que el sintonizador TCL permita el control nativo.')]:
    s=path.read_text(encoding='utf8')
    assert '</resources>' in s
    addition='''    <string name="channelplus_radio_heading">'''+radio+'''</string>
    <string name="channelplus_timeshift_source_hint">'''+notes+'''</string>
'''
    s=s.replace('</resources>',addition+'</resources>')
    path.write_text(s,encoding='utf8')
video=P/'features/settings/VideoPlayerSettingsScreen.kt'
once(video,
    '''            onClick = { vm.setTimeshiftEnabled(!timeshiftEnabled) },
        )
        // The length means nothing''',
    '''            onClick = { vm.setTimeshiftEnabled(!timeshiftEnabled) },
        )
        Text(
            stringResource(R.string.channelplus_timeshift_source_hint),
            style = tv.own.owntv.ui.theme.stageText(15, 400),
            color = tv.own.owntv.ui.theme.StageColors.Muted,
            modifier = Modifier.padding(horizontal = 22.mpx, vertical = 8.mpx),
        )
        // The length means nothing''')
# Log the actual TDT vendor capability, never fake TIME_SHIFT_STATUS_AVAILABLE.
tif=CORE/'player-core/src/main/java/tv/own/owntv/player/TifPlayback.kt'
once(tif,
    '''                if (view !== next || channel?.inputId != inputId) return
                updateTimeshift(if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE)''',
    '''                if (view !== next || channel?.inputId != inputId) return
                android.util.Log.i("ChannelplusTimeshift", "TDT native status=" + status +
                    " available=" + (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE) + " input=" + inputId)
                updateTimeshift(if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE)''')
print('PASS 5.3.15: HbbTV native view no longer captures hidden keys; Compose HUD refocus, horizontal LEFT channels, IPTV timeshift enabled for unset preference, honest TDT status')