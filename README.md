# Litematica Array

A small client-side Fabric addon for [Litematica](https://modrinth.com/mod/litematica): repeat a schematic placement
along X, Y and Z, right in Litematica's placement configuration, with a live preview. No new schematic file is made.

## Use

Open `M` → **Configure placement** → **Configure**. Right under the **Rotate** button there are three new rows:

```
Array   Copies       Gap
X: [3    ][±]   [0    ][±]
Y: [1    ][±]   [0    ][±]
Z: [1    ][±]   [0    ][±]
```

- **Copies**: how many along that axis. `1` = no copies, a negative number goes the other way.
- **Gap**: blocks between the copies. `0` = touching, negative = overlap.
- The **±** buttons work like Litematica's own: left click +1, right click −1, Shift ×8, Alt ×4.

The axes are the schematic's own, so rotating or mirroring the placement turns the whole array with it.

The copies are ordinary Litematica placements of the same schematic, so they render, print and verify like any
placement. They follow the original live (move, nudge, rotate, mirror, enable/disable, rendering and sub-regions
on/off), are hidden from Litematica's placement list, and are removed together with the original. Arrays are kept
across restarts in `config/schemarray/arrays.json`.

Limits: up to 1024 copies per array; the material list of the original counts one copy; sub-regions moved inside
the original are not copied over (only switched on/off).

### Folders

Litematica's placement list (`M` → **Schematic placements**) can be sorted into folders. **New folder** (bottom left)
makes a top level folder. A folder row shows `v`/`>` (open/closed), its name and how many placements are inside it
(subfolders included); a click anywhere on the row opens or closes it. Its buttons:

- **Visible / Hidden**: Hidden switches the rendering of everything in the folder (and its subfolders) off. Only the
  rendering is switched, never Enabled, and placements you had switched off yourself stay off when you show the folder again.
- **+**: a new subfolder. **Rename**: renames the folder.
- **Delete**: needs Shift held. The folder goes, its placements and subfolders move up one level; no placement is removed.

Every placement row has a new **Move** button, left of Configure: pick `(top level)` or any folder. Folders come first
(by name), then the placements (by name). While the search bar has text the list is flat. Array copies stay hidden
from the list and are never put in a folder.

Select several placements like in a file manager: click selects one, **Ctrl+click** adds or removes a row, **Shift+click**
selects the range from the last clicked row (Ctrl+Shift adds it). Then **drag** the selection (or a single placement or
folder) with the left mouse button onto a folder row to put it in there, onto a placement row to put it next to that
placement, or onto empty space to move it to the top level. Escape or letting go outside the list cancels. A folder
can't be dropped into itself. **Move** moves the whole selection when its row is selected.

**Right-click** a folder or placement row to pick a color for it from Minecraft's 16 chat colors (or no color). A folder row is tinted and
its name colored; a placement gets a colored bar (its own color, else the nearest colored folder's). With several placements selected,
right-clicking one of them colors them all.

Folders are global (all worlds and servers) and kept in `config/schemarray/folders.json`.

## Install

Download the jar for your Minecraft version from [Releases](https://github.com/GermanCGArtist/Litematica-Array/releases)
and put it into your `mods` folder, next to Litematica, malilib and Fabric API.

## Branches

One branch per Minecraft version; `main` follows the newest one.

| Branch   | Minecraft | Litematica | malilib |
|----------|-----------|------------|---------|
| `26.1.1` | 26.1.1    | 0.27.x     | 0.28.x  |
| `26.1.2` | 26.1.2    | 0.27.x     | 0.28.x  |
| `26.2`   | 26.2      | 0.28.x     | 0.29.x  |
| `26.3`   | 26.3      | 0.29.x     | 0.30.x  |

## Build

Needs JDK 25. `build.bat` (Windows) or `./gradlew build`; the jar lands in `build/libs/`. Litematica and malilib are
fetched from Modrinth's maven (compile only). Versions per branch are in `gradle.properties`.

## License

MIT
