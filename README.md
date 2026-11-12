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
