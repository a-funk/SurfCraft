---
kind: game
title: CS:S surf ramps and surf physics (Fabric mod)
game: Minecraft Java Edition
games_also: ["Counter-Strike: Source"]
game_version: "Minecraft Java 26.3 + Fabric Loader 0.19.5 + Fabric API 0.161.0+26.3 (Loom 1.18.2, JDK 25)"
platform: macos
engine: java
route: loader-api
tools: ["Fabric Loom 1.18.2", "Fabric API game tests + client game tests", "Mixin + MixinExtras", "JUnit 6", "objdump (CS:S server binary)", "um sprite / um kb", "Railway (public dev log)"]
anti_cheat: "none: single player and servers the user runs; the mod must be on both sides (registry sync)"
status: working
agents:
- Claude Code (Opus 5.5)
humans: []
date: '2026-10-01'
links: ["https://devlog-production-6292.up.railway.app"]
tags: [physics-port, trace-replay, movement, mixin, custom-block-model, game-tests, client-game-tests, dedicated-server, cs-source, surf]
---
# CS:S surf ramps and surf physics (Fabric mod)

> A Fabric mod for Minecraft Java 26.3 that adds CS:S/CS:GO surf: craftable 51° and 63° ramp blocks that join
> into one smooth slope of any size, a Karambit that copies, extends and places ramp modules, and CS:S surf
> movement (air strafing, ramp clipping, auto hop) ported from a TypeScript CS:S port and replayed against real
> CS:S server recordings. Verified in the real client and on a real dedicated server by Fabric client game tests.

## Setup
- Minecraft Java **26.3** (unobfuscated: Mojang names, no mappings), Fabric Loader 0.19.5, Fabric API
  0.161.0+26.3, Loom **1.18.2** (`net.fabricmc.fabric-loom`), Gradle 9.7.1, JDK 25 (Homebrew `openjdk@25`),
  JUnit 6.1.3. macOS 26.2 on an Apple M4. No launcher install needed: `./gradlew runClient` runs an offline dev
  client; `./gradlew runClientGameTest` drives a real client window.
- `./gradlew genSources` → decompiled sources (keep them in a gitignored folder inside the project).

## Route and why
Loader API (Fabric) for blocks, items, data, models and tests; Mixin for movement, which has no API. Minecraft
lets the client simulate its own player and the server re-checks each move packet, so the CS:S movement runs on
the client and the server only needs collision that agrees with it.

## How the game works (what we had to learn)
- **Client movement:** `LocalPlayer.aiStep` → `LivingEntity.aiStep` → `applyInput` (keys) → jump section →
  `Player.travel` → `Entity.move` → `Entity.collide` (private; per-axis Y-first sweeps against `VoxelShape`
  boxes, then step-up). One position packet per tick (`sendPosition`); a second one before the tick-end packet
  disconnects the client. Mouse look changes `yRot` per frame, so `yRotO == yRot` at travel time.
- **Server checks** (`ServerGamePacketListenerImpl.handlePlayerPositionChange`): it re-simulates each packet
  with `player.move(PLAYER, delta)` and rejects (teleports back with zero velocity) when the result is over 0.25
  blocks off **horizontally** (y is ignored), or when the new box overlaps a shape the old one didn't
  (`isEntityCollidingWithAnythingNew`, silent). "Moved too quickly" measures every packet of a server tick from
  the tick's first position and resets its budget after 5 packets, so any 300 ms stall at high speed fails.
  Creative skips "moved wrongly"; the singleplayer owner skips "moved too quickly"; the floating kick only exists
  on `DedicatedServer` (`allowFlight()` is true elsewhere).
- `ServerPlayer` runs a zero-input `travel` every server tick and then snaps the position back; its
  `onGround`, `deltaMovement` and flags survive and feed jump detection, elytra and knockback.
- **Collision:** sloped surfaces can't be `VoxelShape`s. The ramp's vanilla shape is an inscribed staircase
  (mobs, items, `isEntityCollidingWithAnythingNew` stay consistent), and a mixin on the `collide` call inside
  `Entity.move` gives players exact plane collision on both sides.
- **Rendering custom geometry:** `ModelLoadingPlugin.registerBlockStateResolver` with a `BlockStateModel.UnbakedRoot`
  per state baking `SingleVariant(SimpleModelWrapper(MeshQuadCollection(mesh)))`; item models through an
  `UnbakedModelDeserializer` (`"fabric:type"`). `Block.shouldRenderFace` compares the neighbour's occluder with
  your **occlusion** face, not your quads; light uses the face slices of the occlusion shape.
- `blocks_motion` is a tag in 26.x: MOTION_BLOCKING (rain, snow, lightning) ignores blocks not in it.
- **CS:S (build 11003710 `server_srv.so`, unstripped, read with objdump):** `TryTouchGroundInQuadrants`
  (quarter-hull ground probes after a failed full-hull probe), `ClipVelocity` (k = max(-in·n, 0) + 1/32), and
  `CM_ClipBoxToBrush`'s tie rule (entry numerator clamped at 0).

## Build steps
1. Fabric example mod → `net.fabricmc.fabric-loom` 1.18.2, MC 26.3; JDK 25.
2. `fabricApi { configureTests { createSourceSet = true; enableGameTests = true; enableClientGameTests = true } }`
   for `src/gametest` (server tests run in `build`; client tests with `runClientGameTest`).
3. `./gradlew build` → `build/libs/surfcraft-<v>.jar`; for players: Fabric Loader + Fabric API + the jar in `mods`.

## Verification
- **Trace replay (physics):** 24 recordings from a real CS:S server (flat movement, walls, a corner, the
  surf_kitsune ramp) replayed through the Java core from one initial state, no corrections: worst 0.007 units,
  0.0003 u/s. Map geometry is extracted from the user's own BSP into a gitignored folder.
- **Seam oracle:** a ramp built from blocks must surf bit-identically to one brush (300 random runs per slope).
- **Server agreement:** 300 random surf runs re-simulated by the server's collision: worst 0.000000 blocks off.
- **Integration oracle:** in-game published positions replayed offline through the same driver: 0.0 blocks.
- **Client game tests** (survival, real windows): single player and an in-process `DedicatedServer` over a real
  connection at up to 2200 u/s; client hitches of 6 and 10 packets per server tick; no corrections, no
  "moved wrongly/too quickly", never below a slope. Karambit driven by real mouse/keyboard input.
- **Production boot:** the built jar + Fabric API on a standalone Fabric 26.3 server.
- **Capstone playthrough:** a client game test crafts the Karambit in the crafting UI, builds a survival course with
  it by look + right-click (a 96-block ramp from 12 clicks, a gap, a second ramp), and surfs it twice with keyboard
  and mouse: 10.6 s, 123 blocks, 680 u/s, full health, 0 server corrections. Recorded as a clip.
- **Adversarial review:** five reviewers + skeptics found 1 blocker and 13 majors after the first green build;
  all fixed with regression tests. Not verified: other GPUs/OSes, a real network with real latency.

## Gotchas
1. **Ramps rampbug at block seams.** **Cause:** Source's box trace has a ~1/32-unit dead zone where the hull's
   contact edge crosses a seam between separate brushes (the same reason multi-brush CS:S ramps rampbug).
   **Fix:** merge cells into prisms along the length, add one seamless slab per slope run, bevel corner cells.
2. **Player frozen after a teleport.** **Cause:** players stand exactly on block tops, which Source counts as
   solid. **Fix:** a CheckStuck-style nudge before the first move.
3. **Silent server rejections.** **Cause:** a trace sentinel of -1 (Quake 2) let hulls creep up to 1/32 unit into
   planes; `isEntityCollidingWithAnythingNew` rejects that without logging. **Fix:** Source's tie rule.
4. **Surfers stopped dead by lag on servers.** **Cause:** "moved too quickly" resets its budget after 5 packets
   per server tick. **Fix:** a server-side surf window with a per-packet budget (and a real-time cap).
5. **Auto hop dodged fall damage.** **Cause:** landing and hopping inside one 50 ms tick, so no packet said
   "on ground". **Fix:** report the landing tick with the touchdown position.
6. **Any damage stopped a surfer.** **Cause:** `markHurt` syncs the server's zero-input velocity. **Fix:** in the
   surf window, set the server velocity to the known movement before damage.
7. **Controller and vanilla alternated every tick after a hand-back.** **Cause:** a vanilla move of (0,0,0)
   sets onGround false. **Fix:** hand back with vanilla's resting downward velocity.
8. **Joined ramps sawtoothed.** **Cause:** the neighbour search took whichever ramp came first in iteration
   order. **Fix:** continue the plane of the block the player clicked against.
9. **Rain fell through ramps.** **Cause:** not in `blocks_motion`. **Fix:** add to `blocks_motion_no_leaves`.
10. **Client game test hangs at startup.** **Cause:** with the Mac's display asleep `SDL_GL_SwapWindow` blocks.
    **Fix:** `caffeinate -d -u` around the run.
11. **`ClientLevel.hasChunk` is always true.** Use `getChunkSource().hasChunk`. Fabric's `waitFor` runs client
    ticks of its own: track per-tick state with `ClientTickEvents.END_CLIENT_TICK`.
12. **Game tests run millions of blocks out.** Compare world-space doubles relatively and run float physics in
    a local frame (rebase under 4096 units so a half-ulp stays inside the server's 1e-5-block deflation).
13. **Payloads to `PlayerLookup.tracking(p)` never reach p's own client**, and Fabric's client game tests start
    their dedicated server on port 25565 unless told otherwise.

## Assets
Textures (16x16), the Karambit sprite and the icon are drawn by Python/Pillow scripts in `tools/`; no fal key was
used. Charts in the dev log are matplotlib.

## Cost and time
About one day of agent sessions (Claude Code, Opus 5.5) orchestrating ~20 subagents in workflows (build waves,
an adversarial review, a fix wave, a capstone playthrough).

## Open questions
- CS:S duck is not ported (sneak only shrinks the box); ladders and water stay vanilla.
- Other GPUs and a real network with latency are not tested; the dedicated-server tests run over localhost.
