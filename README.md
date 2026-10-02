# SurfCraft

Counter-Strike: Source surf in Minecraft 26.3 (Fabric). Craft surf ramps, build them to any size, stamp out
whole ramp modules with a Karambit, and surf them with CS:S movement: air strafing, ramp clipping, auto bunny hop,
reimplemented and checked against recordings from a real CS:S server.

Live dev log (screenshots and clips of the build, step by step): https://devlog-production-6292.up.railway.app

## What's in it
- **Surf Ramp** (51°, the most common angle on KSF surf maps) and **Steep Surf Ramp** (63°). Blocks join into
  one smooth slope of any size: each block you place continues the plane of the ramp you place it against.
- **Karambit**, the surf builder. Copy a piece of ramp you built and stamp it again and again: extend a ramp past
  its end, or place the piece anywhere, turned to face you. A fresh knife places a classic two-sided 51° ramp.
- **CS:S surf physics** near ramps and in the air after them: sv_airaccelerate 150, gravity 800, knife speed 250,
  66.67 Hz movement ticks, Source's ramp clipping, ground checks and box trace. Walking away from ramps is vanilla.
- **Speedometer** in units/s while you surf (hidden with F1).
- Single player and servers: install it on both. Servers keep their normal movement checks; surfers get a
  speed budget that fits CS:S speeds, so lag doesn't rubber-band them.

## Play
**Quick start (no launcher needed).** On a Mac with Java 25 (`brew install openjdk@25`), double-click
`Start SurfCraft.command`, or run `./gradlew runClient`. It starts Minecraft with the mod in an offline dev
profile (its files stay in `run/`).

**In the official launcher.**
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.5 or newer for Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0+26.3 or newer and `surfcraft-<version>.jar`
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
- Place the next block against the ramp you're building and it continues that ramp's slope. Build in columns of
  two blocks, each column a row higher than the one in front (the 51° ramp needs an extra step every four
  columns: fill any notch you see and the block shapes itself to fit). Anything solid works under the slope.
- **Karambit:**
  - **Copy:** sneak + right-click a ramp. The knife copies that ramp and everything in its box (supports
    included), up to 32 blocks wide and tall; along a longer ramp it copies a 32-block piece.
  - **Extend:** right-click a ramp to add the piece past the end you're looking toward. It only joins if the
    ends match exactly, so extended ramps stay one smooth slope, hundreds of blocks long if you like.
  - **Place:** right-click the ground to place the piece, turned to face where you look.
  - **Undo:** sneak + right-click the air undoes your last placement.
  - The outline shows green where a piece fits and red where it doesn't. A placement happens all at once or not
    at all, and says why when it can't ("No room", "You are in the way", "Spawn protection"). In survival it
    uses blocks from your inventory; it needs build rights (not in adventure mode).

## Surfing
- Jump onto a ramp's slope and hold the strafe key toward the ramp (A or D). You slide along it instead of
  falling: that's surf. Look along the ramp and steer with the mouse.
- In the air, strafe and turn the mouse the same way (A + left, D + right) to gain speed, as in CS:S.
- Hold jump to bunny hop on landing.
- Ramps aren't ground: you can't stand on a slope, only on a ramp's flat top or the floor.
- Sneaking on the ground near ramps is ordinary Minecraft sneaking (slow walk, no walking off edges).
- Falls: landing on a slope breaks a fall; landing on flat ground hurts as in Minecraft, bunny hop or not.
- Potions that change gravity (Levitation, Slow Falling) take over from surf physics while they last; Speed and
  Jump Boost don't change CS:S movement.

## How it's built and tested
- `dev.afunk.surfcraft.physics` reimplements CS:S movement (following the author's browser surf port) in
  Source units. It replays 24 recordings from a real CS:S server (running, jumping, strafing, walls, a corner and
  a surf_kitsune ramp) with a worst position error of 0.007 units; a few details (quadrant ground check, velocity
  clipping, box-trace ties) were matched to that server build.
- Ramp blocks become merged collision brushes, so seams between blocks don't rampbug.
- The client runs the physics; the server re-checks every move against the same exact collision. Tested in
  survival in single player and on a dedicated server at up to 2200 units/s, with client hitches of 10 packets
  per server tick: no corrections, no rubber-banding.
- `./gradlew build` runs the JUnit and server game tests; `./gradlew runClientGameTest` runs the in-game tests
  (they open a window). Details, decisions and gotchas: `MODLOG.md`, `docs/dev/`, `AGENTS.md`.

## License
Free and open source under the [MIT License](LICENSE). Valve's Source 1 SDK License is acknowledged for any part of
the movement code that is a modification of the Source SDK: its text and notices are in `LICENSES/` and in the jar,
and SurfCraft stays free of charge as that license requires. Details: `THIRD_PARTY_NOTICES.md`. Not affiliated
with Valve, Mojang or Microsoft.

## Credits
Built with Claude Code (Claude Opus 5.5) using the [universal-modder](https://github.com/rehan-remade/universal-modder)
skills, on [Fabric](https://fabricmc.net). The movement port follows the author's CS:S browser surf project.
Textures, the Karambit sprite and the icon are drawn by scripts in `tools/`. See `THIRD_PARTY_NOTICES.md`.
