# SurfCraft

Counter-Strike: Source surf in Minecraft 26.3 (Fabric). Craft surf ramps, build them to any size, and surf them
with CS:S movement: air strafing, ramp clipping, auto bunny hop, reimplemented and checked against recordings
from a real CS:S server.

Live dev log (screenshots of the build, step by step): https://devlog-production-6292.up.railway.app

## What's in it
- **Surf Ramp** (51°, the most common angle on KSF surf maps) and **Steep Surf Ramp** (63°). Blocks join into
  one smooth slope of any size: place them next to each other and each block continues its neighbour's plane.
- **Karambit**, the surf builder. Copy a piece of ramp you built and stamp it again and again: extend a ramp
  past its end, or place the piece anywhere, turned to face you. A fresh knife places a classic two-sided
  51° surf ramp.
- **CS:S surf physics** near ramps and in the air after them: sv_airaccelerate 150, gravity 800, knife speed
  250, 66.67 Hz movement ticks, Source's ramp clipping and trace. Walking far from ramps stays vanilla.
- **Speedometer** in units/s while you surf (hidden with F1).
- Works in single player and on servers (install it on both).

## Play
**Quick start (no launcher needed).** On a Mac with Java 25 (`brew install openjdk@25`), double-click
`Start SurfCraft.command`, or run `./gradlew runClient`. It starts Minecraft with the mod in an offline dev
profile (its files stay in `run/`).

**In the official launcher.**
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0+26.3 and `surfcraft-<version>.jar`
   (from `./gradlew build`, in `build/libs/`) in your `mods` folder.
3. A server needs the same two jars in its `mods` folder; players need the mod to join.

## Recipes
| Item | Recipe |
|---|---|
| 6 Surf Ramp | 6 smooth stone in a stairs shape (`S..` / `SS.` / `SSS`) |
| 4 Steep Surf Ramp | 4 smooth stone in a steep L (`S..` / `S..` / `SS.`) |
| 1 Surf Ramp or 1 Steep Surf Ramp | 1 smooth stone in a stonecutter |
| Karambit | 2 iron ingots and a stick, diagonal (`..I` / `.I.` / `S..`) |

## Building ramps
- A ramp block faces you when you place it, like stairs: the slope runs down toward you.
- Blocks next to a ramp join it. Build a ramp in columns of two blocks, each column a row higher than the one in
  front (the 51° ramp needs an extra step every four columns; fill any gap you see and the block shapes itself
  to fit). Anything solid works under the slope.
- **Karambit:** sneak + right-click a ramp to copy it; right-click a ramp to extend it past the end you're
  looking toward; right-click the ground to place the piece; sneak + right-click the air to undo. A placement
  only happens if all of it fits; the outline shows green when it fits and red when it doesn't. In survival it
  uses the blocks from your inventory.

## Surfing
- Jump onto a ramp's slope and hold the strafe key toward the ramp (A or D). You slide along it instead of
  falling: that's surf. Look along the ramp and steer with the mouse.
- In the air, strafe and turn the mouse the same way (A + left, D + right) to gain speed, as in CS:S.
- Hold jump to bunny hop on landing.
- Ramps aren't ground: you can't stand on them, only on the flat top or the floor.

## How it's built and tested
- `dev.afunk.surfcraft.physics`: a Java port of the CS:S movement code from the browser surf port
  (`/Users/funk/code/sandbox/surf`), in Source units. It replays 14 flat-ground recordings and 4 surf-ramp
  recordings from a real CS:S server with a worst error of 0.006 units.
- Ramp blocks become merged collision brushes so seams between blocks don't rampbug.
- The client runs the physics; the server re-checks every move against the same exact collision, so there's no
  rubber-banding. Tested in survival in single player and on a dedicated server at up to 2200 units/s.
- `./gradlew build` runs the JUnit and server game tests; `./gradlew runClientGameTest` runs the in-game tests
  (they open a window). Details, decisions and gotchas: `MODLOG.md`, `docs/dev/`, `AGENTS.md`.

## Licensing
All rights reserved for now. The movement code follows the Source SDK's movement structure (via the author's
browser surf project) and was checked against a CS:S server; like that project, public release of the code is gated
on a provenance review. See `THIRD_PARTY_NOTICES.md` and `LICENSES/`.

## Credits
Built with Claude Code (Claude Opus 5.5) using the [universal-modder](https://github.com/rehan-remade/universal-modder)
skills, on [Fabric](https://fabricmc.net). The movement port follows the author's CS:S browser surf project.
Textures and the Karambit sprite are drawn in code. See `THIRD_PARTY_NOTICES.md`.
