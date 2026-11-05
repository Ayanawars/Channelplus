#!/usr/bin/env python3
"""Channel+ 5.3.18 candidate: one-BACK LOVEStv exit and Compose remote ownership.

Apply AFTER fix_5317_rearm_after_exit.py. The TCL native browser stays attached
and able to render broadcaster prompts, but an idle/rearmed browser is not
allowed to steal DPAD/OK/BACK focus from Channel+'s fullscreen HUD.

Fail on unexpected source: the 5.3.17 APK is the only supported baseline.
No tuner retune, change to the blue-key readiness queue, or signing/release.
"""
from pathlib import Path

base = Path("owntv_tdt/app/src/main/java/tv/own/owntv")
view = base / "features/hbbtv/HbbTvPlayerView.java"
activity = base / "MainActivity.kt"
service = Path("owntv_tdt/app/src/main/java/tv/own/channelplus/lovestvprobe/HbbTvRuntimeService.java")

def once(path, old, new, label):
    source = path.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor in {path}, got {count}")
    path.write_text(source.replace(old, new), encoding="utf-8")

# MainActivity gets hardware events BEFORE the Activity decor/WebView. Consume
# the matching KeyUp even if a restart has temporarily removed the player from
# the owner registry, otherwise Compose interprets it as a second Back.
once(activity,
     "    var hbbReturnHandler: ((String, Long) -> Unit)? = null",
     """    var hbbReturnHandler: ((String, Long) -> Unit)? = null
    private var hbbBackOwnedUntilKeyUp = false""",
     "remember HbbTV BACK key ownership")
once(activity,
     "        val consumed = hbbKeyHandler?.invoke(event) == true",
     """        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && hbbBackOwnedUntilKeyUp) {
            if (event.action == android.view.KeyEvent.ACTION_UP) hbbBackOwnedUntilKeyUp = false
            // Includes auto-repeat DOWN events while TCL is rebinding.
            return true
        }
        val consumed = hbbKeyHandler?.invoke(event) == true
        if (consumed && event.keyCode == android.view.KeyEvent.KEYCODE_BACK &&
            event.action == android.view.KeyEvent.ACTION_DOWN) {
            hbbBackOwnedUntilKeyUp = true
        }""",
     "pair HbbTV BACK down/up across native service rearm")

# A single Back in the interactive broadcaster app should return to the SAME
# TDT stream, not walk through multiple fullscreen shells or leave the TV
# player. Preserve version 5.3.17's service recycle and the Compose focus tick.
once(view,
     "    public static boolean dispatch(Activity owner,KeyEvent event) {",
     """    private boolean exitInteractiveOnBack(KeyEvent event) {
        if(event.getKeyCode()!=KeyEvent.KEYCODE_BACK || !enabled || !gate.interactive()) return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
            Log.i(TAG,"LOVEStv BACK: exit to existing full-screen TDT; preserve Channel+ navigation");
            gate.leave();
            send(HIDE,channel());
            rearmAfterExit("LOVEStv BACK: restore full-screen Channel+");
            if(activity instanceof tv.own.owntv.MainActivity)
                ((tv.own.owntv.MainActivity)activity).onHbbBrowserHidden();
        }
        return true;
    }
    public static boolean dispatch(Activity owner,KeyEvent event) {""",
     "one-press LOVEStv exit")
once(view,
     "return host!=null && host.key(event);",
     "return host!=null && (host.exitInteractiveOnBack(event) || host.key(event));",
     "route back before generic native keys")

# In 5.3.17 a FRESH HbbTV service is started ~1.8 s after closing LOVEStv.
# It reattaches a full-screen TCL WebView above Compose. Even if alpha=0 or
# root is visually transparent, the child browser can request remote focus.
# Keep broadcaster artwork/prompts visible, but block descendants' focus
# until a user BLUE key actively enters the interactive application.
once(service,
     "    private ViewGroup activityParent;",
     """    private ViewGroup activityParent;
    private boolean hbbOwnsRemote;""",
     "native input owner state")
once(service,
     "root.setDescendantFocusability(hidden ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);",
     "root.setDescendantFocusability(hidden || !hbbOwnsRemote ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);",
     "do not focus idle native browser")
once(service,
     """        if(hidden) {
            root.clearFocus();""",
     """        if(hidden) {
            hbbOwnsRemote=false;
            root.clearFocus();""",
     "release native remote ownership on HIDE")
once(service,
     "    private void key(Bundle data) {",
     """    private void key(Bundle data) {
        if(!hidden && data.getInt(CODE)==KeyEvent.KEYCODE_PROG_BLUE) {
            hbbOwnsRemote=true;
            if(root!=null)
                root.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            Log.i(TAG,"LOVEStv blue key: native browser may own interactive remote focus");
        }""",
     "acquire native remote only on BLUE")

a = activity.read_text(encoding="utf-8")
v = view.read_text(encoding="utf-8")
s = service.read_text(encoding="utf-8")
assert "hbbBackOwnedUntilKeyUp" in a and "hbbFocusRestorationTick" in a
assert "exitInteractiveOnBack(event) || host.key(event)" in v
assert "rearmAfterExit(\"LOVEStv BACK: restore full-screen Channel+\")" in v
assert "hbbOwnsRemote" in s and "ViewGroup.FOCUS_BLOCK_DESCENDANTS" in s
assert "activityParent.addView(root,new ViewGroup.LayoutParams(-1,-1))" in s
assert "root.setVisibility(hidden ? View.GONE : View.VISIBLE)" in s
print("PASS 5.3.18: Back exits LOVEStv once; native browser cannot steal idle focus; LEFT/OK remain in Channel+; TDT tuning preserved")
