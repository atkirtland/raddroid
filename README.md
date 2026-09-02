# RadDroid

> **Archived:** This project is no longer maintained. It has been subsumed by
> [runpy-android](https://github.com/atkirtland/runpy-android).

RadDroid runs [Radicale](https://radicale.org/) (a CalDAV/CardDAV server) directly on
an Android device, as a foreground service. It lets you sync calendars and contacts
between apps on the same device (or other devices on your LAN) without depending on a
third-party cloud provider.

Radicale itself is pure Python and runs on-device via [Chaquopy](https://chaquo.com/chaquopy/),
Android's Python SDK — there's no server to host elsewhere.

## How it works

- **`MainActivity`** — a minimal UI for choosing the storage folder/config file name,
  granting storage permission, and starting/stopping the server. It also shows live
  status and lets you copy the error/log output to the clipboard for debugging.
- **`RadicaleService`** — a foreground `Service` that starts the embedded Python
  interpreter (Chaquopy) and polls it for running/error state every 1.5s.
- **`raddroid_server.py`** — the Python side. Starts/stops `radicale.server.serve(...)`
  on a background thread and captures Radicale's log output in memory so the app can
  display it.
- **`assets/config-android`** — the default Radicale config, written once to the chosen
  storage folder on first run (RadDroid never overwrites it afterward, so you can
  hand-edit it). By default the server:
  - binds to `127.0.0.1:5232` (device-local only — edit `hosts` to `0.0.0.0:5232` for
    LAN access)
  - requires no authentication (`type = none`; switch to `htpasswd` for a password)
  - stores collections under `<folder>/collections` using the `multifilesystem_nolock`
    backend, since Android's shared storage doesn't support `flock()`

## Requirements

- Android Studio (or the Gradle wrapper) with Android SDK 35 installed
- A device or emulator running Android 8.0+ (API 26), `arm64-v8a` or `x86_64`
- [uv](https://docs.astral.sh/uv/) with Python 3.11 available, used as Chaquopy's
  `buildPython` for the on-device Python build

## Building & running

```bash
./gradlew installDebug
```

or open the project in Android Studio and run the `app` module.

## Usage

1. Launch RadDroid and tap **Grant** to allow storage access (needed to store
   calendars/contacts on the SD card / shared storage).
2. Optionally change the storage folder name and config file name.
3. Tap **Start** to launch the server. Status will update to `Running on
   127.0.0.1:5232`.
4. Tap **Open in browser** to view Radicale's web interface, or point a CalDAV/CardDAV
   client (e.g. DAVx⁵) at `http://127.0.0.1:5232/`.
5. Tap **Stop** to shut the server down.

If the server fails to start, the error and log panes show the failure details, with
buttons to copy them to the clipboard for troubleshooting.

## Project layout

```
app/src/main/java/com/raddroid/app/
  MainActivity.kt        UI: config, start/stop, status/log display
  RadicaleService.kt      Foreground service hosting the Python interpreter
app/src/main/python/
  raddroid_server.py      Starts/stops Radicale, captures logs
app/src/main/assets/
  config-android           Default Radicale config template
```
