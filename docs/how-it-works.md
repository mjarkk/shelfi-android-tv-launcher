# How Shelfi works

## The shelf

Every app you can launch is a book on one shelf: TV apps and phone apps alike, games included. The one opened last
stands first. Apps never opened from Shelfi follow, A to Z. Shelfi only counts launches it made itself. Android's
settings app stays off the shelf, because Shelfi opens it from its own settings.

A spine takes its colour from the app's art, shows the app's icon at the top and its name down the side, with the text
at 4.5:1 contrast or better. The book you're on turns out in 3D to show its cover: the app's TV banner, or its icon
when it has none. Around the art, the cover carries on the art's own background. Shelfi reads the banner's edges and
calls them solid, a gradient, or busy (a photo, noise, a logo running off the edge). A solid or gradient edge is
painted out to the cover's edges. A busy one gets a mat around it. Apps without any art get a cover of flat shapes,
like the made-up games on the desktop build.

One spring drives each turn, and it's only read while laying out and drawing, so gliding along the shelf recomposes
once per key press, never per animation frame. `RecompositionTest` holds the shelf, the room's pan and the settings
wall to that.

## A shelf longer than the screen

The plank is as long as the shelf needs: every book, one cover turned out, and the two ducks, with a margin at each
end. It is never shorter than the screen. When there are more apps than fit, the plank runs off the screen and the
view pans along the room. The wall, plank, books and ducks all move together.

Focus moves freely across the screen. The view only moves when the focused book, or the book either side of it, would
come within the TV's safe margin (96 px) of a screen edge, and then by just enough. At the first book and at the duck
at the end, the plank's end and a strip of wall come into view. Back and Home pan back to the start.

## The ducks

Two duck bookends hold the books up, one hugging the first book and one the last, a wing over the edge of each spine.
Only the duck at the end is a focus stop; the one at the start is decoration and can be dropped with `LeftDuck` in
`ui/settings/Duck.kt`. The ducks never move: a book next to one turns its cover out in front of the duck's wing.
Focus the duck at the end and its face lights up, with a cream ring and a soft glow around it. There are two looks,
yellow and white, picked in settings.

## Settings

![The settings frames hanging on the wall](settings.avif)

OK (or ↑) on the duck at the end pans the view up the wall to four frames: Wallpaper, Hidden apps, Duck and System
settings. They hang above wherever the view is, so from the far end of a long shelf the view pans straight up, and
coming back down returns to the same place.

| Action | TV remote | Gamepad | Keyboard (desktop) |
|---|---|---|---|
| Open settings (on the duck) | OK or ↑ | A or ↑ | Enter or ↑ |
| Move between frames | D-pad ← → | D-pad ← → | ← → |
| Open a frame | OK | A | Enter |
| Back to the shelf, on the duck | Back or ↓ | B or ↓ | Esc, Backspace or ↓ |

Opening a frame pans one level further up the wall, to its page; Back pans down again, to the frame it came from.

- **Wallpaper** opens a row of every wallpaper, each in a frame. ← → browses, OK hangs the focused one, and Back
  leaves the wall as it was.
- **Hidden apps** opens a grid of every installed app, hidden ones included. The D-pad moves, OK hides or shows the
  focused app, and Back closes the grid. Hidden apps leave the shelf at once and stay installed. A hidden app that is
  uninstalled and installed again stays hidden.
- **Duck** shows the ducks in their current colour. OK switches both between yellow and white on the spot, with no
  page. Shelfi remembers the choice; yellow is the default.
- **System settings** opens Android TV's own settings. The desktop build only logs it.

## Wallpapers and the room

![The ten wallpapers](wallpapers.avif)

The shelf hangs on a papered wall above painted panelling. The ten patterns are drawn in code, with no image assets:
rose trellis, daisies, teatime, strawberry gingham, toadstools and ferns, sage stripe, cherries, folk tulips, lemon
grove, and cats and yarn. Shelfi starts on a random one and moves on to another every hour. Picking one in settings
shows it at once and restarts the hour.

The room is as long as the plank. Its warm light, its dusky ends and the plank's shadows belong to the room, not the
screen: they're one screen's worth with the middle column stretched to the room's length, so the ends are darker
and a long middle is evenly lit.

Each wallpaper's room is baked in the background into opaque pieces: its two ends, half a screen each, and strips of its middle that repeat in between. Each pattern's
repeat is rendered once into a small tile. A pan only moves the layer the pieces are recorded in, so the wall costs
one screen of blits per frame and is never redrawn while the shelf moves.

## Code layout

| Path | |
|---|---|
| `shared/` | Kotlin Multiplatform library (Android + JVM) with nearly everything: `App()`, the `LauncherRepository` contract, the fake repository, and the `ui/` package (`ui/settings/` holds the ducks, the frames and their pages, `ui/wallpaper/` the wallpapers and the wall) |
| `androidApp/` | The launcher: `MainActivity`, `AndroidLauncherRepository` (installed apps, launching, artwork, saved choices), the manifest, banner and icon |
| `desktopApp/` | The desktop entry point, a window around `App()` with `FakeLauncherRepository` |
| `design/Shelf.dc.html` | The original 1920×1080 mockup. The geometry and the fake shelf's colours come from it; its header and button hints were dropped |

`LauncherRepository` is the line between the UI and the platform. Everything above it is shared, so the desktop
build and the snapshot tests run the real UI.

## Tests and snapshots

`mise run test` runs the shared tests on the JVM and as Android host tests. `mise run snapshot` renders the
home screen, the ducks, every settings page and every wallpaper with fake data, at 1920×1080, into
`build/snapshots/`. It's the quickest way to see a change without a TV. The settings and wallpaper pictures above come from it; the
README's screenshot is the Android TV emulator.
