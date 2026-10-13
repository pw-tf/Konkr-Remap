# Konkr Remap

A button remapper for the **Konkr Pocket Fit** and **Konkr Pocket Fit Elite**.

Turn any button on your Konkr into any other button, keyboard key or key combo, in every app including games
and Chrome. For example, `R1 → Page Down` for reading, `L1 → Ctrl+Tab` to switch Chrome tabs, or swap A and B
in a game that gets them backwards.

- **Any key or button.** Gamepad buttons, keyboard keys, Back/Home/Recent apps, media and volume keys.
- **Key combos.** Hold Ctrl, Shift, Alt or Meta with any key (e.g. `Ctrl+Shift+Tab`).
- **Press, Hold and Double press.** Give one button up to three actions.
- **Per-app setups.** Different mappings in Chrome than in your games, switched automatically.
- **Chrome shortcuts built in.** Next/previous tab, new/close tab, reload, back/forward, address bar, find, zoom.
- **No root, no PC, no Shizuku.** The app starts its own helper through Android's Wireless debugging.
- **Auto-update.** The app tells you when a new version is out and installs it in one tap.

## Requirements

- Konkr Pocket Fit or Pocket Fit Elite (any Android 11+ device should work)
- Wi-Fi, for the one-time pairing and to restart the helper after a reboot

## Install

1. On the device, open the [latest release](https://github.com/pw-tf/Konkr-Remap/releases/latest) and download
   `konkr-remap-vX.Y.Z.apk`.
2. Open the file. If Android asks, allow your browser or file manager to install apps.

Later updates are installed from inside the app. You don't need to come back here.

## First-time setup (about 2 minutes)

Konkr Remap has two parts. The app reads your button presses, and a small **helper** sends the remapped keys to
other apps. Android only lets the helper do that when it's started through Wireless debugging, which is why
pairing is needed. You only pair once.

### 1. Turn on Developer options

Settings › About device › tap **Build number** 7 times until it says you're a developer.

### 2. Pair and start the helper

1. Connect to Wi-Fi.
2. Open Konkr Remap and tap **Pair**. Allow notifications when asked.
3. Developer options opens. Turn on **Wireless debugging**, open it, and tap **Pair device with pairing code**.
   Leave the code on screen.
4. Pull down the notification shade. On the Konkr Remap notification tap **Enter pairing code**, type the 6-digit
   code and send it.
5. The notification changes to **Ready!** The helper is running and remapping is switched on.

### 3. Turn on remapping (if it isn't already)

If step 2 of the app still shows **Turn on remapping**, tap **Turn on**. When the helper is running this works in
one tap. Otherwise Android's Accessibility settings open: choose **Konkr Remap** and switch it on.

When both steps are done the app shows **● Remapping is active**.

## Using it

### Remap a button

1. Tap **Add button**, then press the button on your Konkr you want to change.
2. Tap **Press** and choose what it should do:
   - **Chrome shortcuts**: ready-made combos for browsing.
   - **Common**: the keys most people want, plus **Do nothing** to disable a button.
   - **Search all keys**: every key Android knows.
   - To make a combo, tick **Ctrl / Shift / Alt / Meta** under *Hold with* before picking the key.
3. Optional: set **Hold** (button held for 0.4 s) and/or **Double press** (two presses within 0.25 s).
4. Tap **Save**.

Tap any mapping in the list to change or remove it.

> **Tip for games:** leave Hold and Double press empty. A button with only a Press action reacts instantly
> and works when held down. With Hold or Double press set, the app has to wait to see which one you meant.

### Different buttons per app

Tap **+ App** above the list and choose an app (e.g. Chrome). Mappings you add while that app is selected apply
only inside it, and only for those buttons; every other button keeps its **All apps** mapping. To delete an
app's setup, select it and tap the **×** on its chip.

Remapping is paused while Konkr Remap itself is open, so your buttons always work normally in this app.

### After a reboot

The helper stops when the device restarts. Konkr Remap restarts it by itself: it turns Wireless debugging back
on and reconnects. This needs Wi-Fi. Until the helper is back, your buttons work as normal (not remapped). If it
doesn't come back, open the app and tap **Start**.

### Updates

When a new version is released the app shows **Version X is available** at the top. Tap **Update**, then confirm
in Android's install prompt. The first time, Android asks you to allow Konkr Remap to install apps.

## Troubleshooting

| Problem | Fix |
|---|---|
| "Pairing screen not found" | Keep the *Pair device with pairing code* dialog open while you type the code, and make sure you're on Wi-Fi. |
| "Wireless debugging not found" | Connect to Wi-Fi and turn on Wireless debugging in Developer options, then tap **Start**. |
| "Not paired yet" | Pair again (Setup step 2). Pairings can be removed in Wireless debugging › Paired devices. |
| "Restricted setting" when turning on accessibility | Start the helper first and use the app's **Turn on** button, or go to Settings › Apps › Konkr Remap › ⋮ › **Allow restricted settings**. |
| A button can't be detected | Analog triggers/sticks that only report a position (not a press) can't be remapped. |
| A game ignores remapped buttons | Some games only accept input from a real controller. Try mapping to a keyboard key, or leave that button unmapped for that game with a per-app setup. |

## Privacy

- The accessibility service only looks at **button presses** and **which app is in front** (for per-app
  setups). It doesn't read screen content, and nothing leaves the device.
- The only network request is the update check against this repo's GitHub Releases.
- The helper runs on the device only and accepts commands from the Konkr Remap app only.

---

## For developers

### Build

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # press/hold/double logic and version comparison
```

Every push runs the tests and uploads a debug APK as a workflow artifact (Actions › Build › run › Artifacts).
Debug builds are signed with a different key than releases, so uninstall one before installing the other.

### Release

1. One-time: add the signing key as repo secrets (Settings › Secrets and variables › Actions):
   `KEYSTORE_BASE64` (the `.jks` file, base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
   To make a new key:
   ```sh
   keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias konkr -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 release.jks
   ```
   Keep `release.jks` and its password backed up. If they're lost, users have to uninstall to get updates.
2. Actions › **Build** › **Run workflow**, enter the version (e.g. `0.2.0`). You can also push a `v0.2.0` tag.
   CI tests, builds, signs, creates the tag and publishes the release with the APK attached.

Version rules, so the in-app updater sees the new version:
- Use plain numbers (`0.2.0`, `1.0.0`). `0.x` versions are the betas.
- Don't add suffixes like `-beta`, and don't tick **pre-release**. GitHub's "latest release" skips pre-releases.

### How it works

| File | Role |
|---|---|
| `RemapService.kt` | Accessibility service: receives every physical key press and tracks the foreground app. |
| `Mappings.kt` | Mapping model, JSON storage, and `Gestures` (press / hold / double-press logic, unit tested). |
| `Helper.kt` | `Helper` runs as the shell user via `app_process` and injects key events. `Injector` is the app's client (local socket, peer-uid checked, version handshake so the helper restarts after an update). |
| `Adb.kt` | Pairs with and connects to the device's own Wireless debugging ([libadb-android](https://github.com/MuntashirAkon/libadb-android)) to launch the helper, and grants the app `WRITE_SECURE_SETTINGS` so it can re-enable things after a reboot. |
| `Updater.kt` | Checks GitHub Releases and installs through `PackageInstaller`. |
| `MainActivity.kt` | Jetpack Compose UI. |
