# Changelog

Notable changes to SurfCraft. Versions follow [Semantic Versioning](https://semver.org/).

## 0.1.0 (2026-10-02)

First public release, for Minecraft Java Edition 26.3 with Fabric Loader 0.19.5+ and Fabric API 0.161.0+26.3.

### Added
- **Surf Ramp** (51°, a 5:4 slope, the most common angle on KSF surf maps) and **Steep Surf Ramp** (63°, 2:1).
  Each block continues the plane of the ramp it is placed against, so blocks join into one smooth slope of any
  size. Crafted from smooth stone, or 1:1 in a stonecutter.
- **Karambit**, the surf builder (2 iron ingots and a stick): copy a ramp and everything in its box (up to 32
  blocks wide and tall), extend a ramp past its end where the ends match exactly, place the piece anywhere turned
  to face you, and undo (8 steps). An outline shows green where a piece fits and red where it doesn't; placements
  are all or nothing and say why when they can't happen; survival uses blocks from your inventory. A fresh knife
  places a classic two-sided 51° ramp (8 × 5 × 8, 224 blocks).
- **CS:S surf movement** near ramps and in the air after them: sv_airaccelerate 150, gravity 800, knife speed 250,
  66.67 Hz movement ticks, Source's ramp clipping, ground checks (with the quadrant check) and box trace, auto bunny
  hop. Away from ramps, movement stays vanilla.
- Ramps collide as merged brushes, so seams between blocks don't rampbug. They render as exact wedges, with a
  matching selection outline, shadows and occlusion, and rain and snow stop on them.
- **Speedometer** in units/s while you surf (hidden with F1).
- **Servers:** the server re-checks every move against the same exact collision, and surfers get a speed budget
  that fits CS:S speeds, so lag and client hitches don't rubber-band them. Other players see surfers glide. The mod
  is needed on the server and on every client.
- Landing on a slope breaks a fall; landing on flat ground hurts as in Minecraft, bunny hop or not. Levitation and
  Slow Falling take over from surf physics while they last.

### Verified
- The movement core replays 24 recordings from a real CS:S server (build 11003710) with a worst error of 0.007
  units in position and 0.0003 u/s in velocity. Ten of them, on surf_kitsune, need a local copy of that map's
  geometry and skip without it.
- Seams: with merged brushes, 0 of 300 random surf runs per slope differ from surfing one seamless brush (41 to 59
  did with one brush per block).
- In 300 random surf runs, the server's re-simulation of each move matches the client (worst 0.000000 blocks).
- `./gradlew build` runs 50 JUnit tests and 37 server game tests. The client game tests (`./gradlew
  runClientGameTest`) surf in survival in single player and against an in-process dedicated server with 0 server
  corrections: up to 2200 u/s on the 63° ramp and 1500 u/s on the 51° ramp, and through client hitches of 6 and 10
  packets per server tick.
- Fall damage is the same with and without jump held at all 24 drop heights tested.
- A playthrough test, using keyboard and mouse input only, crafts the Karambit, builds a course with it (a 96-block
  two-sided ramp from 12 clicks, a gap, a second ramp, a finish pad) and surfs it twice: 10.6 s and 123 blocks per
  run, top speed 680 u/s, full health, 0 server corrections.
