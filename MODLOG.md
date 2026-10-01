# SurfCraft MODLOG

The journal for this mod (universal-modder `mod-any-game` loop). Confirmed facts, decisions, failures and
their causes, and the next step. Newest entries at the bottom of each section.

## Intake
- **Game:** Minecraft Java Edition **26.3** (latest stable, 2026-10-01), Fabric. macOS 26.2, Apple M4.
  Minecraft is not installed through the launcher on this Mac; development and testing use Loom's dev client
  (`./gradlew runClient`, offline dev account, its own `run/` folder). No user saves are touched.
- **Idea:** CS:S/CS:GO surf in Minecraft. Craftable surf ramp blocks that join into ramps of any size, and
  movement that is CS:S's own surf physics (air strafing, ramp clipping), ported from the browser port in
  `/Users/funk/code/sandbox/surf` (which replays 54 real CS:S server recordings).
- **Online/offline:** single player and servers the user runs. The mod must be on the server and client
  (registry sync refuses mismatched clients), so it cannot be used as a client-side movement cheat.
- **Done means:** the jar builds; the physics core replays real CS:S recordings within the surf repo's
  tolerances; ramps craft, place, join and drop in game; a scripted run in the real client surfs a built
  ramp without falling through, rampbugs or rubber-banding; screenshots of it.

## Recon
- `um kb search minecraft`: `games/gta-v/minecraft-passthrough.md` (Minecraft 26.3 + Fabric, JDK 25) and
  `techniques/oracles-how-agents-know-a-mod-works.md` (trace replay is the oracle for ports).
- Versions (from `meta.fabricmc.net`, maven, and the 26.3 example mod):
  Minecraft 26.3, Fabric Loader 0.19.5, Loom 1.18.2 (`net.fabricmc.fabric-loom`, no mappings: 26.x is
  unobfuscated, Mojang names), Fabric API 0.161.0+26.3, Gradle 9.7.1, JDK 25 (Homebrew `openjdk@25`
  25.0.4.1: `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`), JUnit 6.1.3.
- Source of truth, kept **outside the repo** in `~/minecraft-26.3-decomp/`:
  `common/`, `client/` (decompiled Minecraft, from `./gradlew genSources`), `jar-common/`, `jar-client/`
  (vanilla data and asset JSON), `fabric-api/<module>/` (Fabric API sources for 0.161.0+26.3),
  `fabric-repo/` (Fabric API test mods at tag `0.161.0+26.3`).
- Vanilla 26.3 has no smooth stone stairs (only `smooth_stone_slab`), so a stairs-shaped smooth stone recipe
  is free.

## Route
**Loader API + Mixin (Fabric).** Blocks, items, recipes, models and tests go through Fabric API and data
packs. Player movement has no API, so it is patched with Mixin. The client is authoritative for its own
movement in Minecraft, so the surf physics runs on the client; the server only needs collision that agrees
with it and fall damage that ignores ramp contact.

## Physics evidence (from the surf repo, `docs/MOVEMENT_REFERENCE.md`)
- Reference engine: CS:S dedicated server build 11003710. Tick interval float 0.015 s (66.67 Hz).
- Surf cvars: gravity 800, accelerate 5, airaccelerate 150, friction 4, stopspeed 75, maxvelocity 3500,
  knife speed 250, air wish speed cap 30, jump `fround(sqrt(2*800*57))`, step 18, ground normal z >= 0.7,
  hull 32x32x62, auto bunny hop on, no bunny hop speed cap.
- Ramp clipping leaves a 1/32 unit/s outward component and is computed in float in Source axis order;
  velocity and feet origin are stored as float in Source units at tick boundaries.
- Checked-in recordings: `surf/tests/fixtures/css-reference/*.csv` (before/after rows per tick).
- Real-map ramp angles (local KSF maps, histogram of surf faces): the mode is **49-51 degrees**, then 55-56,
  60-65. A 5:4 rise:run slope is 51.3 degrees; 2:1 is 63.4 degrees. A 1x1 45-degree wedge is walkable ground
  in Source (normal z 0.707 >= 0.7), not a surf ramp.
- `local-content/css-reference/kitsune-ramp.json` (gitignored) holds the surf_kitsune brushes around the
  ramp captures, from `node tools/extract-kitsune-brushes.mjs` (needs the user's own
  `surf/local-content/maps/surf_kitsune.bsp`, SHA-256 checked). The region also intersects `func_brush *56`
  (solidbsp=0, collides through its compiled VPhysics hull, not raw planes); if the ramp replays need it,
  they will show it.

## Design decisions
- **Units:** 1 block = 1 m, 1 Source unit = 0.0254 m (the surf repo's scale): 39.37 units per block.
  Source (x, y, z-up) = (MC x, -MC z, MC y) x 39.37. Source yaw = -MC yaw - 90 degrees.
- **Physics core** (`dev.afunk.surfcraft.physics`, no Minecraft imports): a Java port of the surf repo's CS:S
  movement (`src/physics/player.ts` cssMovement path without duck/ladder/water/push, `source-move.ts`,
  BSP branch of `source-hull.ts`), in Source units and axes. Validated by replaying the CS:S recordings.
- **Ramp blocks:** `surf_ramp` (5:4, 51.3 degrees) and `steep_surf_ramp` (2:1, 63.4 degrees). Each cell
  stores `facing` (the direction the slope faces, i.e. descends toward) and `cut` c in 1..p+q: the cell's solid
  is `p*u + q*y <= c` in cell coordinates (u along facing from the back edge, y up), c = p+q is a full cell.
  Placement continues the plane of a neighbouring ramp of the same type and facing
  (`c = c_n + p*(U_n - U) + q*(y_n - y)`), so blocks join into one smooth slope of any size; with no
  neighbour, c = p (the slope starts at the cell's bottom front edge).
- **Collision:** vanilla collision (mobs, items, `isPlayerCollidingWithAnythingNew`) sees an inscribed
  staircase (8 slices), which lies entirely under the true plane. Players never use it near ramps: a
  `Entity.collide` hook slides players against the exact brushes (both sides), and the surf controller
  traces the exact brushes. Exact cell brushes carry axial planes at the cell's **tight** AABB (Source's
  axial bevels); without them a box sweep hits phantom geometry above the slope's top edge.
- **Controller:** client only. Runs CS:S movement in 0.015 s substeps per 50 ms tick (time accumulator),
  view yaw interpolated across substeps, when the player is eligible (on foot, not flying, gliding, riding,
  swimming, climbing, or spectating) and near a ramp, or airborne/briefly grounded after surfing. The
  published Minecraft position is the core state swept forward to the tick boundary, so 3/4-substep ticks
  do not judder.
- **Server:** no movement code; the collide hook makes its re-simulated move agree with the client, and
  fall distance resets on ramp contact.
