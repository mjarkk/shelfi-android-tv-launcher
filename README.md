# Shelfi Launcher

![Shelfi on an Android TV emulator: installed apps as books on a shelf beside a duck, KIJK turned to show its cover](docs/screenshot.avif)

An Android TV home screen that puts your apps and games on a bookshelf.

## Install

Download `shelfi-vX.Y.Z.apk` from the [latest release](https://github.com/mjarkk/shelfi-android-tv-launcher/releases/latest).
The same APK runs on ARM and x86 Android TV devices with Android 8.0 or newer.

Turn on USB or network debugging in the TV's developer options, then:

```sh
adb connect <tv-ip>                 # skip over USB
adb install -r shelfi-vX.Y.Z.apk
adb shell cmd package set-home-activity com.mkopenga.tvhomescreen/.MainActivity
```

The last command makes Shelfi the home screen. To update, install a newer APK the same way.

### Disable the stock launcher

Some devices, like Chromecast with Google TV, keep sending the Home button to their own launcher. Disabling it hands
Home to Shelfi. Do this at your own risk: the TV has no built-in home screen until you re-enable it.

```sh
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

Plain Android TV uses `com.google.android.tvlauncher` instead, and other brands use their own. To list the launchers on
your TV, run the command below. Disable the stock one, but leave `com.android.tv.settings/.system.FallbackHome` alone.

```sh
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

On Chromecast with Google TV, the remote's YouTube button stops working while the stock launcher is disabled.
[Button Mapper](https://play.google.com/store/apps/details?id=flar2.homebutton) can remap it.

### Go back to the stock launcher

Re-enable the stock launcher before you uninstall Shelfi. Otherwise the TV is left without a home screen.

```sh
adb shell pm enable com.google.android.apps.tv.launcherx
adb shell pm enable com.google.android.tungsten.setupwraith
adb uninstall com.mkopenga.tvhomescreen
```

If you never disabled it, only the last line is needed.

## Develop

```sh
mise install
mise run desktop
```

This opens Shelfi in a 1280×720 window with fake apps, so most UI work needs no TV. It's the same Compose UI as the
launcher, with `FakeLauncherRepository` standing in for the installed apps.

To build from source and install on a TV or emulator connected over adb:

```sh
mise run setup              # SDK packages and licences
mise run install release    # build, install and open Shelfi
mise run home               # make it the home screen
```

| Task                                    |                                                                                |
| --------------------------------------- | ------------------------------------------------------------------------------ |
| `mise run desktop`                      | Run on this Mac with fake apps                                                 |
| `mise run test`                         | Unit tests, on the JVM and as Android host tests                               |
| `mise run snapshot`                     | Render 1920×1080 PNGs of every screen into `build/snapshots/`                  |
| `mise run apk` / `apk:release`          | Debug APK / R8-minified release APK, signed with the debug key for sideloading |
| `mise run apk:signer [apk]`             | SHA-256 of the certificate that signed an APK                                  |
| `mise run install [debug\|release]`     | Install on the connected device and open it                                    |
| `mise run home`                         | Make Shelfi the default home screen                                            |
| `mise run logs`                         | Follow logcat for Shelfi                                                       |
| `mise run emulator:create` / `emulator` | Create / boot a 1080p Android TV emulator                                      |
| `mise run clean`                        | Delete build outputs                                                           |

Compose debug builds are much slower than release builds, so judge scrolling and animation on
`mise run install release`.
