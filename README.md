# Shelfi Launcher

![Shelfi on an Android TV emulator: installed apps as books on a shelf beside a duck, KIJK turned to show its cover](docs/screenshot.avif)

An Android TV home screen that puts your apps and games on a bookshelf.

## Install

```sh
mise install
mise run setup              # SDK packages and licences
mise run install release    # build, install and open Shelfi
mise run home               # make it the home screen
```

To go back to the stock launcher, uninstall it: `adb uninstall com.mkopenga.tvhomescreen`.

## Develop

```sh
mise install
mise run desktop
```

This opens Shelfi in a 1280×720 window with fake apps, so most UI work needs no TV. It's the same Compose UI as the
launcher, with `FakeLauncherRepository` standing in for the installed apps.

| Task                                    |                                                                                |
| --------------------------------------- | ------------------------------------------------------------------------------ |
| `mise run desktop`                      | Run on this Mac with fake apps                                                 |
| `mise run test`                         | Unit tests, on the JVM and as Android host tests                               |
| `mise run snapshot`                     | Render 1920×1080 PNGs of every screen into `build/snapshots/`                  |
| `mise run apk` / `apk:release`          | Debug APK / R8-minified release APK, signed with the debug key for sideloading |
| `mise run install [debug\|release]`     | Install on the connected device and open it                                    |
| `mise run home`                         | Make Shelfi the default home screen                                            |
| `mise run logs`                         | Follow logcat for Shelfi                                                       |
| `mise run emulator:create` / `emulator` | Create / boot a 1080p Android TV emulator                                      |
| `mise run clean`                        | Delete build outputs                                                           |

Compose debug builds are much slower than release builds, so judge scrolling and animation on
`mise run install release`.
