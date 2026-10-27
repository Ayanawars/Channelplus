# TDT native timeshift — local implementation

Uses the TV Input Framework session already tuned by TifPlayback. Android TvView commands timeShiftPause, timeShiftResume and timeShiftSeekTo preserve that session and the TV-managed rolling buffer. No IPTV HTTP buffer is created for antenna channels.

The tuner must report TIME_SHIFT_STATUS_AVAILABLE. Its TimeShiftPositionCallback supplies epoch start/current positions; unsupported/unavailable or invalid positions do not enable seeking. Pause can be available before the seek bounds arrive. Buffer storage, capacity and any USB configuration remain under the TV input’s control. Native TV-app support does not prove that a vendor exposes the same feature through TIF to other clients; this needs the TCL device test.

Controls: Play/Pause pauses/resumes; rewind/forward use the configured live rewind step and clamp to the real buffer; Go Live seeks to the live epoch and resumes without retuning or discarding the buffer. The HUD delay, watched-time clock, EPG-at-position and timeline use native positions. The timeline window reflects the tuner’s actual available buffer. Source failover does not treat a paused/rewound tuner as failed. Hardware tuner playback does not offer an mpv engine switch.

State/callbacks reset on tune/retry, source changes, detach and disconnect. Unsupported commands disable timeshift controls while keeping the TV channel. Leaving a channel ends its native buffer according to the vendor’s session lifecycle.

Validation: resource XML and git diff whitespace checks passed. Added unit cases cover clamped seeks, invalid/unavailable bounds, pause delay, callback latency, rolling eviction and variable timeline geometry. These tests and APK build remain unexecuted: the required Gradle wrapper distribution is absent and its download is blocked in this environment. TV verification is required for native callback support, Play/Pause, repeated seek, return to live and source switching. No GitHub writes or release were performed.

API reference: https://developer.android.com/reference/android/media/tv/TvView
