# Litematica Array

A small client-side Fabric addon for [Litematica](https://modrinth.com/mod/litematica): repeat a schematic placement
along X, Y and Z, right in Litematica's placement configuration, with a live preview. Arrays make no new schematic file; the **Shape Generator** builds shapes from blocks and saves them as schematics.

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

### Shape Generator

`M` → **Shape Generator** (at the end of the right-hand column) makes a solid or hollow shape out of blocks and saves it
as a schematic. Each side is 1 to 256 blocks.

- **Box**: solid.
- **Plane**: flat, 1 thick, normal to **Axis**.
- **Frame**: the 12 edges of the box; Thickness is the edge width.
- **Ellipsoid**: fills the box (equal sides = sphere).
- **Dome**: upper half of an ellipsoid along **Axis**; the size along it is the height.
- **Cylinder**: along **Axis**, filling the box.
- **Cone**: along **Axis**, base at the low end; **Top diameter** (0 = sharp tip).
- **Pyramid**: base at the low end; **Top size** (0 = point).
- **Torus**: ring lying normal to **Axis**; **Tube diameter**.
- **Arch**: half ring; **Arch width** is the ring width.
- **Tube**: hollow cylinder along **Axis**, open ends; **Wall width**.
- **Grid**: lines every **Spacing** blocks, **Line width** wide. Size 1 along **Axis** = flat grid, else a 3D lattice.

**Outline** is the shell, **Fill** the inside. Thickness is the shell width (as thick as the shape = all outline).
Leave **Fill** empty for a hollow shape.

Each of **Outline** and **Fill** takes blocks:

- Type `stone` or `minecraft:stone`; add a state in brackets: `oak_log[axis=x]`.
- Mix with weights: `60%stone_bricks,40%andesite`. A part without `%` weighs 1.
- **Hand** adds the block in your main hand (in Single mode it replaces the field).
- The multi-block modes (everything but Single) take two or more blocks separated by commas, e.g. `stone,andesite`; the field shows the syntax.
- Ids are suggested while you type; **Tab** takes the first one, or click one. An unknown block turns red and disables **Generate**.

Pattern modes:

- **Single**: always the first block.
- **Mix**: random by weight; **Seed** fixes it, **Dice** rolls a new one.
- **Checker**: alternates in cells of **Cell X/Y/Z** blocks.
- **Stripes**: alternates bands of **Width** along **Axis**.
- **Gradient**: blocks in order along **Axis**, in equal bands; **Dither** blends the edges.
- **Diagonal**: bands of **Width** along x + z (meaningful with **Axis** Y).
- **Brick**: bricks of **Cell X/Y/Z**, every other row shifted by half; a second block is the mortar.

- The shape shows as a ghost in the world and updates with every change. Its min corner sits at your position, or its
  center with **Anchor: Center**.
- **Ghost: Off** removes the ghost from the world (the 3D view keeps updating); the choice is remembered.
- Move the ghost with Litematica's tools; the screen keeps the spot until you toggle the anchor.
- Drag the 3D view to orbit, scroll to zoom, **Reset view** to start over.
- **Peek** (hold it, or hold `G` with no text field focused) hides the panel to show the ghost.

- **Folder** / **Browse...** picks the save folder: any subfolder of Litematica's schematics folder (**New folder** creates one).
  The choice is remembered.
- **Generate** writes `<name>.litematic` into that folder, turns the ghost into a normal placement
  and closes. An existing file blocks it; hold **Shift** to overwrite, which also removes placements that used the old file.
- **Cancel** or Esc closes the screen and removes the ghost.
- **Load** a preset, or type a name and **Save** the current settings; **Delete** needs Shift.
- Presets and your last settings are kept in `config/schemarray/shapes.json`; the last settings return when you reopen the screen.

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
