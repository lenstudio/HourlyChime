# HourlyChime

[简体中文说明](README_CN.md)

A minimalist Android hourly chime / timed reminder app (`com.len.hourlychime`). Zero library dependencies, no layout XML — a fully programmatic UI. The code itself is under 13 KB; with 8 built-in ringtones the single APK is about 1.6 MB. Written to replace the unmaintained `com.caynax.hourlychime`.

## Features

- **Multi-profile scheduling** — a profile = name + applicable weekdays + rule set + ringtone + volume, freely combinable
- **Rule syntax** — `H:M[-H:M][/step]`, comma-separated, e.g. `9:00-18:00/60, 12:30`
- **Three ringtone sources** — app built-in / system sounds (enumerated via RingtoneManager) / user-picked file (SAF picker with persisted URI grant)
- **Independent volume** — temporarily switches STREAM_NOTIFICATION for playback, restores the original level afterwards
- **Statutory workday/holiday awareness** — bundled `assets/holidays.json`; profiles distinguish workdays / weekends / public holidays
- **Reliable firing** — exact AlarmManager alarms + `goAsync()` so playback finishes before the receiver ends; automatic rescheduling on boot, time change and timezone change; self-healing reschedule whenever the main screen resumes
- Vibration support, immersive status bar

## Source Layout

| File | Purpose |
|---|---|
| `Scheduler.java` | Data & scheduling core: profile (JSON) parsing, rule expansion, next-trigger computation |
| `ChimeReceiver.java` | Broadcast receiver: plays the ringtone and schedules the next alarm; three-branch playback (sys: / doc: / asset) |
| `MainActivity.java` | Main screen: profile list, master toggle, vibration toggle |
| `ProfileActivity.java` | Profile editor: rules, weekdays, ringtone picker (three sources), volume, audition |
| `Ui.java` | Programmatic UI helpers (immersive status bar, etc.) |

## Build

Requires an Android SDK (build-tools 34 / platform android-33) and JDK 17:

```sh
./build.sh            # produces HourlyChime.apk (provide your own debug.keystore)
```

See the comments inside `build.sh` for the manual steps. Key points:

- `aapt2 link` must pass `--min-sdk-version 30 --target-sdk-version 33` (otherwise some devices fail install with error -26)
- No layout XML; the only resource is a launcher icon; `assets/` is packed in via `-A`

## Built-in Ringtones

Eight ringtones ship in `assets/`, numbered to match the in-app names (`Ringtone N` → `ringN.*`; extensions keep their original mp3/ogg format):

| File | In-app name | Default usage |
|---|---|---|
| `ring1.mp3` | Ringtone 1 (commute reminder) | Special - time nodes |
| `ring2.mp3` | Ringtone 2 (workday full-hour chime) | Workdays - hourly |
| `ring3.ogg` | Ringtone 3 (workday half-hour chime) | Workdays - half-hourly |
| `ring4.ogg` | Ringtone 4 (rest-day hourly chime) | Special - weekends |
| `ring5.mp3` | Ringtone 5 (short ding) | — |
| `ring6.mp3` | Ringtone 6 (double ding-dong) | — |
| `ring7.mp3` | Ringtone 7 (doorbell ding-dong) | — |
| `ring8.mp3` | Ringtone 8 (appliance beep) | — |

Ringtone keys in code (`sing` / `bell` / `wind` / `chimes` / `d1`~`d4`, stored in user preferences) are decoupled from filenames; see the switch in `ChimeReceiver.java`.

## License

Source code is released under the MIT License (see LICENSE). The audio files under `assets/` are **not** covered by that license (sourced from the legacy app and Freesound) and are provided for personal use.
