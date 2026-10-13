# Konkr Remap

Button remapper for the Konkr Pocket Fit and Pocket Fit Elite. Map any button to any other button, key or key
combo (e.g. `L1 → Ctrl+Tab` in Chrome), with optional hold and double-press actions and per-app overrides.

## How it works

- An **accessibility service** sees each physical button press and decides what it becomes.
- A small **helper** runs with ADB (shell) rights and injects the resulting keys into whatever app is in front.
  The app starts it itself through Android's *Wireless debugging*, so there's no PC, root or Shizuku involved.

## Setup on the device (Android 11+)

1. Settings › About › tap **Build number** 7 times to enable Developer options.
2. Connect to Wi-Fi.
3. Open Konkr Remap › **Pair**. In Wireless debugging, turn it on and tap *Pair device with pairing code*,
   then type the code into the Konkr Remap notification. The helper starts and remapping turns on.
4. Tap **Add button**, press a Konkr button, pick what it should do, **Save**.

After a reboot the app turns Wireless debugging back on and restarts the helper by itself (Wi-Fi needed);
until then buttons work as normal.

## Releases and auto-update

The app checks this repo's latest GitHub Release on launch and offers a one-tap update. To publish:

1. One time, create a signing key and add it as repo secrets (Settings › Secrets and variables › Actions):
   ```sh
   keytool -genkeypair -v -keystore release.jks -alias konkr -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 release.jks   # → KEYSTORE_BASE64
   ```
   Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`konkr`), `KEY_PASSWORD`.
   Back up `release.jks`: if you lose it, users have to uninstall to get updates.
2. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`. CI builds, signs and publishes the APK.

Every other push builds a debug APK as a workflow artifact for testing. It is signed differently from releases,
so uninstall it before installing a release.
