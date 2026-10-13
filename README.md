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
- **No root, no PC, no pairing.** Remapped keys are typed by Konkr Remap's own keyboard.
- **Auto-update.** The app tells you when a new version is out and installs it in one tap.

## Requirements

- Konkr Pocket Fit or Pocket Fit Elite (any Android 11+ device should work)

## Install

1. On the device, open the [latest release](https://github.com/pw-tf/Konkr-Remap/releases/latest) and download
   `konkr-remap-vX.Y.Z.apk`.
2. Open the file. If Android asks, allow your browser or file manager to install apps.

Later updates are installed from inside the app. You don't need to come back here.

## First-time setup (about 1 minute)

Konkr Remap has two parts. A **remapping service** sees your button presses, and the **Konkr Remap keyboard** types
the remapped keys into the app in front. Android only lets the selected keyboard send keys to other apps, which is
why the app is also a keyboard. It has no keys of its own and never sees what you type.

Open Konkr Remap and follow the three steps on screen:

1. **Turn on remapping.** Tap **Turn on**, choose **Konkr Remap** and switch it on.
   If Android says *Restricted setting*, go back, tap **App info**, then **⋮ › Allow restricted settings**, and
   try again. Android does this for every app installed from a downloaded APK.
2. **Turn on the Konkr Remap keyboard.** Tap **Turn on** and switch on **Konkr Remap** in the keyboard list.
   Android shows its standard warning that keyboards can collect what you type; this one has no keys and can't.
3. **Select the keyboard.** Tap **Select** and choose **Konkr Remap**.

When all three are done the app shows **● Remapping is active**. Nothing else is needed after a reboot.

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

### Typing text

When you tap a text box, the Konkr Remap keyboard shows a small bar instead of keys. Tap **Switch keyboard** to
type with your normal keyboard (e.g. Gboard). The next time you press a remapped button, Konkr Remap switches back
to its keyboard by itself.

Home, Back, Recent apps, volume and media keys don't need the keyboard, so they work even while you're typing.

### Updates

When a new version is released the app shows **Version X is available** at the top. Tap **Update**, then confirm
in Android's install prompt. The first time, Android asks you to allow Konkr Remap to install apps.

## Troubleshooting

| Problem | Fix |
|---|---|
| "Restricted setting" when turning on remapping | Settings › Apps › Konkr Remap › ⋮ › **Allow restricted settings**, then turn it on again. |
| Remapped keys do nothing | Check the app's setup steps: the Konkr Remap keyboard must be turned on and selected. |
| A key only works the second time | The first press switched back from your typing keyboard; Konkr Remap sends it as soon as its keyboard is ready. If it's still lost, press again. |
| A button can't be detected | Analog triggers/sticks that only report a position (not a press) can't be remapped. |
| A game ignores remapped buttons | Some games read the controller directly and ignore keys from a keyboard. Map to a keyboard key the game supports, or leave that button unmapped for that game with a per-app setup. |

## Privacy

- The remapping (accessibility) service only looks at **button presses** and **which app is in front** (for
  per-app setups). It doesn't read screen content.
- The keyboard only sends keys. It has no keys of its own, so there's nothing for it to record.
- The only network request is the update check against this repo's GitHub Releases. Nothing else leaves the device.

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
| `RemapService.kt` | Accessibility service: receives every physical key press, tracks the foreground app, and handles Home/Back/Recents, volume and media keys itself. |
| `Mappings.kt` | Mapping model, JSON storage, and `Gestures` (press / hold / double-press logic, unit tested). |
| `Keyboard.kt` | `RemapKeyboard` input method: types remapped keys (with Ctrl/Shift/Alt/Meta) into the app in front through its input connection. The service switches back to it when another keyboard is selected. |
| `Updater.kt` | Checks GitHub Releases and installs through `PackageInstaller`. |
| `MainActivity.kt` | Jetpack Compose UI. |
