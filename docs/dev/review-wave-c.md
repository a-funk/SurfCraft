# Wave C review (2026-10-01)

Five adversarial reviewers, each finding reproduced, then challenged by a skeptic. Severity after the skeptic.
Probe tests referenced below lived in the review worktrees `.claude/worktrees/wf_926b46be-3ca-<n>`; they are archived in `review-probes/wt-<n>/`.

| ID | Severity | Verdict | Finding |
|---|---|---|---|
| A1 | major | confirmed | Port omits CS:S's quadrant ground check (TryTouchGroundInQuadrants), so players stay airborne at ramp toes and lips where CS:S grounds them |
| A2 | minor | confirmed | Server re-simulation rejects corner-grazing moves that the client's Source trace allowed (moved wrongly, velocity zeroed) |
| A3 | minor | confirmed | Publishing sweep extrapolates into a block the core overlaps by float rounding, and the vanilla server's new-collision check rejects the move |
| A4 | minor | confirmed | SourceHull's tie rules are not CS:S's CM_ClipBoxToBrush, and the NEVER_UPDATED/'as in Source' claims are wrong |
| A5 | minor | confirmed | clipVelocity is a fitted formula, not CS:S's ClipVelocity: velocity already leaving a plane is flattened instead of kept |
| A6 | minor | confirmed | Per-axis 3500 cap allows up to 7.7 blocks/tick, so 'moved too quickly' fires with fewer packets per server tick than the research doc says |
| B1 | blocker | confirmed | A lag spike of 300 ms or more on a dedicated server, while surfing faster than about 1300 u/s, triggers vanilla's 'moved too quickly'. The surfer is teleported back and loses all their speed. |
| B2 | major | confirmed | Holding jump (auto hop) skips fall damage on about 72% of flat-ground landings: the landing and the hop happen inside one tick, so the server never sees onGround |
| B3 | minor | partial | Slime and bed bounces are erased under the controller, and so is the soul sand/honey slowdown. Landing on slime after a surf gives no bounce and a quick stop. |
| B4 | major | confirmed | Sneaking next to a ramp neither slows you down nor stops you at ledges: players walk off start platforms beside ramps while sneaking |
| B5 | minor | confirmed | Levitation and Slow Falling do nothing while the controller drives; Speed and Jump Boost are ignored too |
| B6 | minor | confirmed | The speedometer is drawn on the action-bar line, garbling the Karambit's feedback; it also shows '0 u/s' whenever you stand within about 1.5 blocks of a ramp |
| B7 | minor | confirmed | A long vertical launch gets the surfer kicked for flying on a dedicated server |
| B8 | minor | confirmed | An ender pearl thrown while surfing keeps part of the old momentum after the teleport, because e3 restores the pre-teleport movement on the pearl's damage |
| B9 | minor | confirmed | An elytra can't be deployed while touching a ramp: the client starts gliding, the server refuses, and the surfer loses 2 ticks of CS:S movement |
| C1 | major | confirmed | Surfers above ~1310 u/s are teleported back and stopped dead when 6 move packets reach the server in one server tick (a ~300 ms server stall or client hitch) |
| C2 | major | confirmed | Landing with jump held (auto hop) skips fall damage on about 2 of 3 landings, from any height |
| C3 | minor | confirmed | c1 resets the fall on any face of a ramp block: flat tops, full cells and sides cancel any fall |
| C4 | minor | confirmed | Sneaking off a ledge 1.0-1.8 blocks from a ramp is corrected ('moved wrongly'): e2's radius is smaller than the controller's |
| C5 | minor | confirmed | e3 changes vanilla knockback anywhere for ordinary sprint-jumping (0.612 blocks/tick on the jump tick) |
| C6 | minor | confirmed | Other players see a surfer's legs running at full speed in mid-air (d1 applies to the local player only) |
| C7 | minor | confirmed | The re-simulation's lift region grows without limit with move length: one long packet costs ~34x vanilla (a DoS once player_movement_check is off) |
| D1 | major | confirmed | Hand-placed ramps next to or stacked on another ramp of the same type and facing come out as a sawtooth, and the result depends on which side the other ramp is on |
| D2 | major | confirmed | Ramps are missing from minecraft:blocks_motion, so the MOTION_BLOCKING heightmap ignores them: rain falls through every ramp, it rains under hollow ramp roofs, and snow, ice and lightning reach the ground beneath |
| D3 | major | confirmed | The Karambit preview flood-fills the whole connected ramp every client tick: 7.9 ms per tick on the render thread while the knife points at a big ramp |
| D4 | major | confirmed | Extend refuses surf-sized ramps: a 16-tall 5:4 A-frame cannot be extended past 40 blocks |
| D5 | minor | confirmed | noOcclusion lets light through ramps (hollow ramps cast no shadow) and makes every hidden face of full ramp cells render (about 26% lower FPS on a large field) |
| D6 | minor | confirmed | Copy silently truncates ramps larger than 32 on any axis, and extend then tells the player to copy the ramp they just copied |
| D7 | minor | confirmed | Adventure mode: the preview says 'fits' but clicks silently do nothing, and sneak + right-click on a ramp (copy) undoes the player's last placement |
| D8 | minor | partial | 'No room' refusals for causes the preview cannot show: the player's own body when placing on a wall at eye level, and spawn protection on dedicated servers |
| D9 | minor | confirmed | The block selection outline on ramps is the 8-step inscribed staircase, drawn as stripes over the smooth slope |
| E1 | none | refuted | No README or install path for players; the only documented way to play is the Gradle dev client |
| E2 | minor | partial | THIRD_PARTY_NOTICES and fabric.mod.json leave out the physics core's Source SDK provenance that the upstream surf repo documents |
| E3 | minor | partial | local-content is not a declared test input, so the Kitsune ramp/wall replays stay silently skipped after the geometry is extracted |
| E4 | minor | partial | fabric.mod.json: placeholder 'Mod ID' icon, no contact links, and an unbounded `fabric-api: "*"` that accepts Fabric API builds missing the APIs SurfCraft uses |
| E5 | minor | confirmed | Production code has a dead method, a method only the game tests use, and duplicated cell-u and module-index logic |
| E6 | minor | partial | Test code duplication, an unused test field, and a stale doc line |
| E7 | none | refuted | Gradle wrapper does not verify the distribution it downloads |

## Lens A: Physics fidelity

**Checked and found sound:** Method: primary evidence came from the CS:S build 11003710 Linux server binaries in the surf repo (local-reference/css-server/bin/engine_srv.so and cstrike/bin/server_srv.so, 32-bit, symbols present), disassembled with /usr/bin/objdump. That was compared line by line with the Java core and the TS reference (/Users/funk/code/sandbox/surf/src/physics/player.ts cssMovement path, source-move.ts, source-hull.ts). Probes, measurement only, with no production changes: /Users/funk/code/sandbox/minecraft/.claude/worktrees/wf_926b46be-3ca-1/src/test/java/dev/afunk/surfcraft/physics/Probe*.java. ProbeAFailingTest holds three deterministic failing repros (A1, A2, A3). Run: JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./gradlew test --tests 'dev.afunk.surfcraft.physics.ProbeA*'. The Kitsune cases need the main tree's local-content linked in; I removed my symlink afterwards.

Sound, or no measurable difference:
- Replays: all 14 flat and 10 Kitsune CS:S recordings pass in this worktree (Kitsune via the main tree's local-content), with the worst errors the MODLOG reports.
- SourceMovement order against TS and CS:S FullWalkMove: StartGravity half-step, CheckJumpButton with stamina scaling and auto-hop, the vz>250 ground break, friction (stopspeed/friction, 1e-8 cutoff), the CS:S WalkMove stamina pow (CCSGameMovement::WalkMove [address omitted]), Accelerate and legacy AirAccelerate (wish cap 30, gain scaled by the uncapped 250), the <1 u/s grounded zeroing, the 0.25 surfaceFriction rule (0 < vz <= 140), FinishGravity, and float storage of origin and velocity.
- Clamp placement: CS:S calls CheckVelocity in StartGravity and FinishGravity (tail calls) and after Friction and CategorizePosition; the port clamps once after accelerate. A Source-order probe at 3300-3500 u/s showed 0/400 runs differing by more than 0.05 u. The only difference is a stored terminal vz of -3506 instead of -3500, which has no effect on movement.
- TryPlayerMove structure (four bumps, stuck re-trace, 5 planes, first-plane overbounce 1 when airborne, crease, primal-velocity reversal stop), StepMove (down slide against up/slide/down, landing test 0.7, z from the down move) and StayOnGround all match. CCSGameMovement's AirMove, WalkMove and TryTouchGround overrides add only ladders, stamina and a plain box ray.
- TickDriver: DT = (double)0.015f with a lag accumulator that only drifts physically (one extra substep per about 186 h). Each substep uses the yaw linearly interpolated at its own end time between the yaw sampled at the previous and current tick, the short way. That is the right time point; keys are constant per 50 ms tick, which is inherent to Minecraft's input sampling. The published point is the core swept by its velocity times lag with exact sliding, never fed back into the core.
- Conversions: Source = (x, -z, y) * 39.37 relative to the anchor; normals map the same way; yaw = -mcYaw - 90 (MC yaw 0 → -90, MC yaw 90 → 180); 0.00127 b/t per u/s in both directions; hull taken from getBbWidth()/2f and getBbHeight()/2 like the AABB; sideMove = 400*(D-A), forward = 400*(W-S); the wish speed is capped at 250 as in CheckParameters.
- Server agreement on mixed geometry (ProbeAServerTest, 5 layouts x 2 slopes, about 300k ticks: platform behind the ramp top, walls across A-frames, a ceiling, pillars, twin A-frames): no move beyond 0.25 blocks except the A2 case. The trust payload returns the exact delta and the ground-probe trick sets onGround. A server correction zeroes the client's velocity, which affects A2, A3 and A6.
- The NEVER_UPDATED sentinel: CS:S uses -99999, and the value never matters there because of the numerator clamp (A4). Every replay passes with -9999.

### A1: Port omits CS:S's quadrant ground check (TryTouchGroundInQuadrants), so players stay airborne at ramp toes and lips where CS:S grounds them

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/physics/SourceMovement.java:123-124 (grounded = full-hull 2-unit probe only). The surf repo inherits the same gap (player.ts probe(); its docs/ROADMAP.md M11 item 4 lists TryTouchGroundInQuadrants as not done).

**Evidence.** Primary source: the CS:S build 11003710 Linux server binary in the surf repo (local-reference/css-server/cstrike/bin/server_srv.so, 32-bit and not stripped). In CGameMovement::CategorizePosition, when TryTouchGround's hit has no entity or normal.z < 0.7 (0.7 at [address omitted]), it calls TryTouchGroundInQuadrants at [address omitted]. That function probes four quarter boxes 2 units down, feet-relative: (-x,-y), (+x,+y), (-x,+y), (+x,-y), with min/max(0, ...) applied to the player mins/maxs. The first hit with an entity and normal.z >= 0.7 grounds the player (SetGroundEntity). Failing repro: ProbeAFailingTest.quadrantGroundAtTheToe, in /Users/funk/code/sandbox/minecraft/.claude/worktrees/wf_926b46be-3ca-1/src/test/java/dev/afunk/surfcraft/physics/. Setup: world = floor box + RampBrushes.of(RampSurfTest.cells(5,4,6,140)); m.setOrigin(ServerAgreementTest.onSlope(5,4,6,1.0,10,0.02)); m.velocity = (0,-900,0); 26 ticks of m.tick(CSS_SURF step 0.6 blocks, 0, 0, -90, false, 0.015f, MC hull, world). At tick 25 the feet are 1.8004 u above the floor and the port's grounded is false. With the quadrant rule, CS:S grounds at tick 25 and the port at tick 26. Speed after 40 ticks: 383.6 u/s (CS:S rule) against 408.6 u/s (port). Random runs (ProbeAQuadrantTest: 300 runs of 300 ticks, random strafes, turns and jumps, same seeds for both, divergence = 0.01 u): runs that diverge, all at the toe (feet 0.2-1.7 u above the floor): 41 (5:4 ramp+floor), 43 (5:4 ramp+platform), 50 (5:4 A-frame), 55 (2:1 ramp), 70 (2:1 ramp+platform), 69 (2:1 A-frame). Ten ticks later the horizontal speed differs by a mean of 8-20 u/s, worst 108-1270 u/s. Probe rule, added after the existing grounded line (my implementation in ProbeMovement.quadrants): if (!grounded && vz <= 140) for each box b in {{-hx,0,-hy,0},{0,hx,0,hy},{-hx,0,0,hy},{0,hx,-hy,0}}: centre c = next + ((b0+b1)/2, (b2+b3)/2, 0), half h = ((b1-b0)/2, (b3-b2)/2, hz); t = SourceHull.trace(world, c, c - (0,0,2), h); if (t.fraction() < 1 && t.normal().z() >= 0.7) { grounded = true; break; }

**Impact.** Whenever a surfer comes down a ramp onto the floor or a ledge at its foot, or walks along a ramp's foot, CS:S grounds them earlier through the quarter hull over the flat surface. That means friction, vertical speed zeroed, and an auto-hop jump from that tick. SurfCraft keeps them sliding for an extra tick or more: about 6% more speed at the bottom, different jump timing, and changed trajectories in 14-23% of runs that reach a toe. Ramps built on the ground (the normal Minecraft build) have a toe at every bottom edge.

**Fix direction.** Port TryTouchGroundInQuadrants into SourceMovement.tick's categorize step, in the binary's box order and with its walkable test, applied only when the full-hull probe fails and vz <= 140, as above. Add ProbeAFailingTest.quadrantGroundAtTheToe as a regression test. The 24 replays are unaffected (none of them reaches a lip).

**Skeptic.** Binary checked (surf/local-reference/css-server/cstrike/bin/server_srv.so, 32-bit, not stripped). CGameMovement::CategorizePosition calls the virtual TryTouchGround (slot 0x28) with the full hull. If m_pEnt is null or normal.z < 0.7, it calls TryTouchGroundInQuadrants at [address omitted]. The constants are 0.7 (double at [address omitted]), 140.0f and 2.0f. [address omitted] traces four quarter boxes 2 units down, in the order (-x,-y), (+x,+y), (-x,+y), (+x,-y). It grounds on the first hit with m_pEnt and nz >= 0.7, and restores fraction/endpos. The linux64 server has the same function. The port only has the full-hull probe (SourceMovement.java:123-124).

Re-run in my worktree: ProbeAFailingTest.quadrantGroundAtTheToe fails as stated. At t25 the feet are 1.8004 u up, and the full probe hits the slope at f=0, nz=0.625. My own quarter-box traces (SkepticA1Test) give q2 and q4 f=0.885 nz=1.0, so CS:S grounds at t25 and the port at t26. Speed after 40 ticks is 408.6 against 383.6 u/s. ProbeAQuadrantTest reproduces 41/43/50/55/70/69 diverging runs out of 300, all at the toe.

Extra evidence that supports major (SkepticA1ToeTest). A surfer 0.5-1.5 u above the floor at the toe, 1500 u/s along the ramp, strafing into the slope (side +400, the normal surf input), no jump. The port is grounded 0/40 ticks and keeps 1500 u/s. The CS:S rule grounds 2-11 ticks and ends at 760-1325 u/s (2:1 at 0.5 u: 760; 5:4 at 0.5 u: 973). SkepticA1DescentTest: 2:1, from 6 u up at -250 u/s down-slope, strafing in. The port is grounded 5/60 and ends at 1101 u/s; CS:S is grounded 19/60 and ends at 463 u/s. With jump held, both hop out with no loss.

Correction: 'and lips' in the title is not supported. Every divergence is at a concave toe. At a convex lip with a flat top, the full-hull probe already hits the walkable top, and the reviewer's ramp+platform layout diverged only at the toe.

### A2: Server re-simulation rejects corner-grazing moves that the client's Source trace allowed (moved wrongly, velocity zeroed)

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/physics/ExactCollide.java:19 (TOL = 1e-4) and resolve()/lift() lines 88-137, used by movement/RampCollision.java:36-50 for MoverType.PLAYER. The client side is SourceHull.trace, whose leave fractions are pulled back by DIST_EPSILON.

**Evidence.** ProbeAServerTest, layout '5:4 A-frame+pillars' (A-frame 6 tall, 140 long, plus 40 random 1x1x2 stone pillars on the slopes, Random(5)). 2000 runs and 313,373 published ticks through TickDriver gave one move whose server error exceeds 0.25 blocks. Minimal failing repro, ProbeAFailingTest.serverAcceptsCornerGrazingMove: world = ServerAgreementTest.aFrame(5,4,6,140) with box(3,1,22, 4,3,23) inserted at index 1; from = (169.0752764608131, -851.4930961511183, 39.66118103814514), to = (173.7897062656463, -916.9573705393236, 33.74711988318406) (Source units, feet). ExactCollide.resolve(world, from, to-from, HULL, 0.6 blocks, false, lift=true) returns (4.714, -2.838, -0.021): error 1.5907 blocks. The same chord gives SourceHull.trace fraction 1.0, and ExactCollide.clear passes with the hull shrunk by 1/32 u. Mechanism, from a substep log: in substep 1 the hull passes the pillar's vertical edge diagonally. For Source the +x leave fraction is (a+eps)/(a-b) = 0.457 and the +y enter is 0.474, so enter > leave and it misses. For the exact sweep the corner overlaps by about 0.007 u. Resolving y before x then stops at the pillar after 2.84 u. The lift (at most 0.6 + 0.83 blocks) cannot clear a 2-block pillar. The server then calls teleport(start, ...) with PositionMoveRotation(..., Vec3.ZERO, ...) (decomp ServerGamePacketListenerImpl ~1301). The client's setValuesFromPositionPacket calls setDeltaMovement(newValues.deltaMovement()) = 0 (ClientPacketListener:800-812). SurfController.decide() resyncs and travel() restarts the core from sourceVelocity(0).

**Impact.** On a dedicated or LAN server, a surfer who grazes a block's vertical edge (pillar, wall end) within about 1/32 unit gets 'moved wrongly', is snapped back one tick and stops dead, losing all speed. Rare: once in about 313k ticks in the pillar stress layout, and 0 in about 125k ticks of the other mixed layouts. Each occurrence is drastic.

**Fix direction.** Make the server's chord test at least as lenient as the client's trace. Test chord clearance with the hull shrunk by DIST_EPSILON (1/32 u), or accept the chord when SourceHull.trace finds it clear (fraction 1, not startSolid), before falling back to the axis-by-axis resolve.

**Skeptic.** Reproduced exactly (SkepticA2Test.reviewerPair). ExactCollide.resolve returns (4.714, -2.838, -0.021), an error of 1.5907 blocks. SourceHull.trace on the same chord gives fraction 1.0 and is not startSolid. ExactCollide.first hits at 0.0433 with normal (0,1,0), the pillar's Minecraft -z face. The sampled deepest overlap of the chord with the pillar is 0.0109 u, under DIST_EPSILON.

A game-faithful layout gives the same result (SkepticA2Test.pillarsReplaceCells: the pillar blocks replace the ramp cells at their positions instead of overlapping them). TickDriver publishes the same pair at run 472 tick 10: 1 over-limit move in 313,373 ticks.

Server path, checked in the decomp:
- handlePlayerPositionChange zeroes the y error and fails above 0.0625 squared blocks.
- The old box does not touch any vanilla shape (the ramp's staircase lies under the slope), so it calls teleport(start), which passes Vec3.ZERO (lines 1301-1302).
- Client: setValuesFromPositionPacket calls setDeltaMovement(zero) (ClientPacketListener 800-812).
- SurfController.decide() sees the position change and resyncs; travel() rebuilds the driver from deltaMovement 0.

The published move is about 66 u per tick (~1300 u/s), so when it happens the stop is visible. It is rare, so minor.

### A3: Publishing sweep extrapolates into a block the core overlaps by float rounding, and the vanilla server's new-collision check rejects the move

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/physics/TickDriver.java:88 (PUBLISH_TOL = 1e-5) and sweep() 95-107, through ExactCollide.first (a brush the start already penetrates by more than tol is ignored). Origins are stored as float (SourceMovement.setOrigin), up to 8192 u from the anchor (SurfController.REANCHOR).

**Evidence.** Failing repro, ProbeAFailingTest.publishedMoveNotRejectedByVanillaServer. World = ServerAgreementTest.aFrame(2,1,6,140) with walls box(-8,0,z, 8,8,z+1) for z = 30, 60, 90, 120 inserted after the floor. State: core.origin = (44.1463508605957, -1169.2889404296875, 171.62074279785156), velocity = (-5.313642978668213, 0.03125, 4.697162628173828), grounded false, stamina 0, surfaceFriction 0.25f, jumpHeld true, lag 4.358589660025114E-8, yaw -89.4930213989111, published = (44.14635062899581, -1169.2889404283255, 171.6207430025816). Then d.tick(CONFIG, 0, 400, true, -89.39162567869332, HULL, world). The core's hull face ends 0.000044 u inside the wall face (float ulp at y≈-1169 is 1.2e-4). The sweep starts deeper than PUBLISH_TOL, skips the wall and publishes 0.001163 u (3.0e-5 blocks) inside it. Vanilla isEntityCollidingWithAnythingNew (decomp ServerGamePacketListenerImpl:1285-1298) deflates the new box by only 1.0E-5F blocks and finds the wall, which the old box did not overlap, so it rejects: teleport back with velocity 0, as in A2. How the core gets there: Source applies DIST_EPSILON only to a crossing move, so a hull can approach a face without crossing it (wall gap per tick 0.0269 → 0.0139 → 0.0024 → -0.000044 u). Rate (ProbeAWallTest, surfer pressed into the slope/wall corner, 33 anchor offsets 0-8000 u, 20k ticks each): 0 rejections in 240,000 ticks within 76 blocks of the anchor, and 12 in 420,000 ticks at 76-178 blocks.

**Impact.** Multiplayer: a surfer pressing into a wall next to a ramp occasionally, about once per 35k ticks (29 minutes) of wall-hugging more than 76 blocks from the controller's anchor, gets a server correction and stops dead.

**Fix direction.** Never publish deeper into a brush than the core already is. In sweep(), treat a start penetration up to about 1e-3 u as touching: stop or slide along that brush instead of ignoring it. Alternatively, push the published point out of any overlapped brush. That keeps the published box within the server's 1e-5-block deflation.

**Skeptic.** Reproduced the state (SkepticA3Test.reviewerState). The wall gap goes 0.00289, then 0.00337, then -0.0000437 u: the third substep ends just outside in double, and float rounding of the origin at y≈-1169 (ulp 1.2e-4) puts it inside. The core velocity is then vy = -0.224 u/s. The publishing sweep (PUBLISH_TOL 1e-5 u) treats the wall as start-penetrated, ignores it, and publishes 0.0011634 u inside. Vanilla deflates by 1.0E-5F blocks (0.000394 u). In the decomp, isEntityCollidingWithAnythingNew (1285-1298) collects getPreMoveCollisions for newAABB.deflate(1e-5F), finds the wall as a new collision, and calls teleport(start) with zero velocity.

ProbeAWallTest re-run matches: 0 rejections at offsets 0-2750 u, 12 in 420k ticks at 3500-7000 u.

Caveat on impact (SkepticA3WallSpeedTest): all 12 rejections happen at core speeds of 2.9-38 u/s, with published moves of 0.2-1.7 u. The player is already nearly stopped and the snap is at most 0.044 blocks, so 'stops dead' is barely perceptible. A fast surfer along a wall that rises from a ramp's top edge (SkepticA3Test.fastAlongAParallelWall, about 200k ticks at anchor offsets 0-7000 u) gave 0 rejections and never published inside. Real but minor.

### A4: SourceHull's tie rules are not CS:S's CM_ClipBoxToBrush, and the NEVER_UPDATED/'as in Source' claims are wrong

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/physics/SourceHull.java:13-19 and 26-28 (javadoc), 52-59 (unclamped enter fraction), 68 (comparison). physics/RampBrushes.java:20-24 ('Slabs come last: as in Source, a later brush wins...'). MODLOG Wave A 'Seam rampbug' paragraph.

**Evidence.** Disassembly of the CS:S server engine (surf repo local-reference/css-server/bin/engine_srv.so, not stripped). CM_ClipBoxToBrush<false> at [address omitted]: enterfrac starts at -99999.0 (constant at [address omitted]), not -9999. At [address omitted] the entry numerator is clamped: if DIST_EPSILON > d1 the numerator is 0, else d1-eps, so an entry fraction is never negative. The tie at 0 therefore goes to the first plane (cmova on a strict >). A brush replaces the hit only if its clamped fraction is below trace.fraction. CM_TraceToLeaf<false> stops testing brushes once the fraction is 0 ([address omitted]). Axial boxes go through IntersectRayWithBoxBrush: face chosen by raw t with ties z > y > x, and the fraction clamped at 0 before the cross-brush comparison. So in Source the sentinel's value never matters. The real deviation is the port's unclamped comparison: within a brush the plane with the largest raw negative fraction wins, and a later brush with a negative raw fraction overrides an earlier fraction-0 hit, which Source never does. Replays (ProbeATraceTest.replaysWithBothTraces, main-tree local-content linked in): all 14 flat and 10 Kitsune recordings give identical worst errors under the port's trace and a binary-faithful trace (ProbeSourceTrace), so they do not test tie-breaking. All 24 pass with the current -9999. Surf runs, game (cells + port trace) against one-brush ramps with the binary trace: 0/300, 0/300, 2/300 and 1/300 runs diverge (5:4 ramp, 5:4 A-frame, 2:1 ramp, 2:1 A-frame). Example at a toe: the port reports the slope and rides up 0.6 u with vz +31.7; CS:S reports the toe's axial bevel, giving a wall clip with vx = 1/32 on the floor. Under the binary rule, RampBrushes with slabs last differs from one brush in 0/300 (5:4) and 1/300 (2:1, a mid-ramp ground probe hits an internal cell top) runs; with slabs first 0/300 for both; plain per-cell brushes 36/300 and 13/300.

**Impact.** Small direct gameplay effect (0-2 of 300 runs, at ramp toes). However, the design rationale for RampBrushes' brush order is documented as Source behaviour when it is not. A fixer who makes the trace binary-faithful without reordering would bring back rare seam grounding. The sentinel question itself is moot.

**Fix direction.** Either keep the current semantics and correct the javadoc/MODLOG (state that the tie rule deviates from CS:S on purpose and that the slab order depends on it), or adopt the binary rule (clamp the numerator at 0 before 'f > enter', stop at fraction 0, sentinel immaterial) and list RampBrushes' slabs first (probe: 0/300 divergence from one brush).

**Skeptic.** Checked in engine_srv.so, CM_ClipBoxToBrush<false> at [address omitted]:
- NEVER_UPDATED is -99999.0f (constant at [address omitted]).
- The entry numerator is clamped: 0 if 0.03125f > d1, else d1-eps ([address omitted]). The comparison is a strict > with cmova, so a tie at 0 keeps the first plane.
- A brush replaces the hit only if its clamped enter < trace.fraction; the stored fraction is max(enter, 0).
- Brushes with numsides 0xFFFF go to IntersectRayWithBoxBrush (call at [address omitted]).
- CM_TraceToLeaf<false> leaves the brush loop as soon as the fraction is 0 ([address omitted]).

So SourceHull's unclamped entry, its -9999 'Source NEVER_UPDATED', and the SourceHull/RampBrushes javadoc claim that 'as in Source' a later brush with a negative raw fraction replaces an earlier fraction-0 hit are all wrong. In Source the sentinel never matters.

Re-run: all 23 replays give identical worst errors under both traces. Under the binary rule, slabs-last RampBrushes against the one-brush monolith diverges in 0/300 (5:4) and 1/300 (2:1); slabs first 0/300; plain cells 36/300 and 13/300. All match the finding. GAME against 'CSS monolith+binary' on single ramps: 1/300 (5:4) and 2/300 (2:1).

Caveat: that gameplay number depends on the reference monolith listing its six axial planes before the slope. The first-plane tie rule then picks the toe's axial face. vbsp emits a brush's original sides before the bevels it adds, so the number is only indicative. The documentation errors and the tie-rule deviation are solid; the measured direct effect is tiny.

### A5: clipVelocity is a fitted formula, not CS:S's ClipVelocity: velocity already leaving a plane is flattened instead of kept

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/physics/SourceMove.java:24-38

**Evidence.** server_srv.so CGameMovement::ClipVelocity(Vector&, Vector&, Vector&, float overbounce, float redirect) at [address omitted]. Every TryPlayerMove call site passes redirect = 0 (all 'push 0x0' before 'call [eax+0x84]'), so the redirect branch never runs. Float order: dot = (in.y*n.y + in.x*n.x) + n.z*in.z; k = max(-dot*overbounce, 0) + 0.03125f (constant at [address omitted]); out = in + n*k; adjust = (n.y*out.y + n.x*out.x) + n.z*out.z; if (adjust < 0) out -= n*adjust. For velocity moving into the plane this equals the port's result (out·n = 1/32). For velocity already leaving the plane (in·n > 0, which happens in TryPlayerMove's multi-plane loop), CS:S keeps the outward part plus 1/32, while the port sets the normal component to exactly 1/32. With this formula (ProbeMove.binaryClip, float normals), the Kitsune ramp replays' worst velocity error drops from 0.000671 to 0.000305 u/s (ramp-glide) and from 0.000366/0.000671 to 0.000076/0.000057 u/s (vanilla/surf ramp-strafe). Flat and wall replays are unchanged. On SurfCraft ramps, 0/300 runs part by more than 0.01 u (ProbeAClipTest).

**Impact.** Fidelity polish. The reference recordings are matched 2-10x more closely in velocity, and multi-plane clips (creases, corners) follow CS:S. No measurable change on the tested SurfCraft ramp runs.

**Fix direction.** Replace clipVelocity with the binary's formula and float operation order (code above). Keep SourceMoveTest's 1/32 expectations, which still hold.

**Skeptic.** Checked in server_srv.so, ClipVelocity(in, n, out, overbounce, redirect) at [address omitted]:
- dot = (in.y*n.y + in.x*n.x) + n.z*in.z, multiplied by overbounce and negated.
- maxss with 0, plus 0.03125f; out = in + n*k.
- adjust = (n.y*out.y + n.x*out.x) + n.z*out.z; if 0 > adjust, out -= n*adjust.
- The redirect rescale runs only if redirect > 0.

TryPlayerMove passes its own float argument as redirect. Vtable slot 0x84 resolves to [address omitted] in both the CGameMovement and CCSGameMovement vtables. All 8 slot-0x84 calls in the movement code push 0.0. There are none in the CCSGameMovement range and no direct calls. So for in·n > 0, CS:S keeps in·n + 1/32, while SourceMove.clipVelocity forces exactly 1/32.

Re-ran ProbeAClipTest. Worst velocity errors drop:
- ramp-glide: 0.000671 to 0.000305 u/s
- vanilla-ramp-strafe: 0.000366 to 0.000076 u/s
- surf-ramp-strafe: 0.000671 to 0.000057 u/s
Flat and wall replays are unchanged, and 0/300 SurfCraft runs part.

Caveat the finding omits: the worst position errors of the two ramp-strafe replays rise slightly (0.003906 to 0.004395 u, and 0.006348 to 0.006836 u). Everything stays far inside 0.05 u / 0.002 u/s, so 'matched more closely' holds for velocity only. Polish, so minor.

### A6: Per-axis 3500 cap allows up to 7.7 blocks/tick, so 'moved too quickly' fires with fewer packets per server tick than the research doc says

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** Config.CSS_SURF maxVelocity clamps each axis (SourceMovement.java:105-106; CS:S CheckVelocity at server_srv.so [address omitted] also clamps per axis). Check in decomp ServerGamePacketListenerImpl.java:1121-1150. Claim in docs/dev/mc26-movement.md §4.5 (lines 662-667).

**Evidence.** Code path: movedDist = |target - firstGood|^2 from the start of the server tick (firstGood set in resetPosition, line 385). The k-th packet in one server tick fails when movedDist - |server deltaMovement|^2 > 100*k (k capped to 1 above 5). At a steady v blocks/tick that is v > 10/sqrt(k). The doc assumes top speed 4.445 b/t (3500 u/s) and concludes 'fails for n >= 6'. Because the clamp is per axis, the total speed reaches 3500*sqrt(3) = 6062 u/s (7.70 b/t), and 4950 u/s (6.29 b/t) with both horizontal axes capped. Thresholds: k=2 above 5568 u/s, k=3 above 4545, k=4 above 3937, k=5 above 3521. Example: along-ramp at the cap while falling at 2000 u/s is 4031 u/s (5.12 b/t), which fails whenever 4 move packets are handled in one server tick. A failure teleports back with zero velocity (see A2).

**Impact.** On non-singleplayer servers under lag (3-5 client packets queued into one server tick), fast but CS:S-legitimate surfers get 'moved too quickly' and stop dead at lower burst sizes than documented.

**Fix direction.** Correct §4.5's numbers (use |v| up to 3500*sqrt(3)). For surf servers, either recommend /gamerule player_movement_check false more strongly, or relax the check for driven players near ramps with a mixin (e.g. budget by the client's actual published speed).

**Skeptic.** server_srv.so CheckVelocity loops over the three axes and clamps each component to sv_maxvelocity on its own. SourceMovement.java:105-106 also clamps per axis, so total speed can exceed 3500 u/s, up to 3500√3.

Decomp, ServerGamePacketListenerImpl:
- 1121-1150: movedDist is measured from firstGood, which is reset each tick (385). deltaPackets = received - known, with known reset in tick() (330); above 5 it becomes 1. The move fails when movedDist - |deltaMovement|² > 100*deltaPackets.
- shouldCheckPlayerMovement exempts only the singleplayer owner, a dimension change, and the gamerules.

The threshold v > 10/√k b/t gives k=2: 5568, k=3: 4545, k=4: 3937, k=5: 3521 u/s, matching the finding. docs/dev/mc26-movement.md (lines 12 and 26-27, and §4.5) assumes a 4.445 b/t top speed and says up to 5 packets always pass, which is wrong once total speed passes 3521 u/s.

The mod's behaviour is unchanged; this matters only on lagging multiplayer servers with surfers above about 3500 u/s total. Doc fix and edge case, so minor.

## Lens B: Movement robustness across game states

**Checked and found sound:** Read AGENTS.md, MODLOG.md, docs/dev/mc26-movement.md and the movement code: SurfController, the client mixins (a1, a2, a3, d1), the common mixins (b1, c1, e1, e2, e3, EntityAccessor), BrushWorld, RampCollision, TickDriver, SourceMovement, SourceMove, SourceHull, ExactCollide and RampBrushes. I checked each against the 26.3 decomp. All probes are in worktree wf_926b46be-3ca-2, not to be merged:
- src/gametest/java/dev/afunk/surfcraft/gametest/ProbeClientTest.java, registered in src/gametest/resources/fabric.mod.json. Run with -PclientTests=probe-sp, probe-sp2, probe-sp3 (add probe-effects for soul sand and Speed/Jump Boost) or probe-ded. The dedicated server uses port 25611.
- src/test/java/dev/afunk/surfcraft/physics/ProbeLandingTest.java.

Log line 1, "stuck at (0.5, -60.0, 0.5), handing this tick to vanilla":
- Cause: SurfServerClientTest builds the steep A-frame (ridge x 0, base y -60, z 0..319, 10 tall) on top of the dedicated server's spawn at (0.5, -60, 0.5). The player's box sits 10 blocks deep in full ramp cells.
- On the next tick a ramp is near, so the controller starts. TickDriver.unstick's nudges (up to 16 u up, 8 u sideways) all start solid, so it hands that one tick to vanilla. The next tick the test's /tp moves the player out ("start at (2.81,-55,3)").
- Harmless here: no correction, no damage (the first health sample was 20.0; spawn invulnerability covered the buried tick), and no effect on later runs.
- Only side effect: decide() had already cleared the vanilla jump for that tick. In real play this needs a player buried by a world edit or a gamemode switch. Overlaps of a staircase-placed box (≤1/8 block) are freed by the 1/32-8 u up-nudges.

Log line 2, "340282346638528860000000000000000000000.0 -> 20.0":
- Comes from the test harness: Surfer.lastHealth starts at Float.MAX_VALUE (src/gametest/.../Surfer.java:115), so the first tick() of every Surfer prints a "drop" from 3.4e38.
- Cosmetic. It hides no assertion, because surfRun compares end health with start health.
- Health is asserted only in surfRun. airStrafe, bunnyHopAndHandBack (apart from the deliberate hit), edgeCases and denseField only print drops. The bunny hop also holds jump, which is exactly the case where fall damage is skipped (B2).
- The comment "air strafe landings hurt" is stale: the latest log shows no damage, because the strafe circles land on the ramp.

Verified sound by probe:
- Death mid-surf (/kill) then respawn: a new LocalPlayer gets a fresh controller. A 30-tick surf at 1000 u/s drove throughout with clearance 0.000000, no resyncs and speed kept.
- Dimension change to a nether ramp (execute in the_nether tp): same result.
- Large coordinates at x 1e6 and x 29,999,800: same result.
- The corrections logged at those coordinates came from the harness's first tp into chunks the client did not have yet, under vanilla movement. ClientLevel.hasChunk always returns true in 26.3, so vanilla's unloaded-chunk hover never triggers. The controller is not involved.
- Client-side catch-up ticks: speed is unchanged until the server's correction (B1).

Verified sound by code path:
- Cobweb, sweet berry and powder snow: stuckSpeedMultiplier makes the next tick ineligible, so vanilla handles it.
- Ladders, vines and scaffolding: onClimbable hands over to vanilla. Scaffolding's shape uses the same CollisionContext.of(entity) as vanilla.
- Water and lava: ineligible.
- World border: BrushWorld.borderBoxes uses vanilla's floor/ceil and the same isInsideCloseToBorder gate.
- Build height: out-of-range y reads as air.
- Riding: passengers are ineligible, and rideTick reaches decide() through tick().
- Spectator and camera entity: ineligible.
- Single-player pause: the level is not ticked and no burst follows.
- Sprint and FOV: vanilla sprint logic still runs. horizontalCollision is set only for walls, so sprint persists on ramps. FOV change is cosmetic.
- F5: no effect on movement.
- HUD: hides with F1.
- Elytra launched once airborne after leaving a ramp: accepted.
- The "standing on air - force-sending blocks below" INFO lines are vanilla's rate-limited support-block resend on landings. Benign.

Checked and not reported:
- Surfing into a cobweb above about 1050 u/s could double-apply the stuck multiplier in the server's re-simulation. Edge case, not reproduced.
- e3's 0.6 b/t threshold matches vanilla knockback feel below it.

### B1: A lag spike of 300 ms or more on a dedicated server, while surfing faster than about 1300 u/s, triggers vanilla's 'moved too quickly'. The surfer is teleported back and loses all their speed.

- **Severity:** reviewer blocker, after skeptic **blocker** (confirmed)
- **Where:** Nothing in SurfCraft relaxes the server check. src/main/java/dev/afunk/surfcraft/mixin/ServerGamePacketListenerImplMixin.java:17-20 only wraps jumpFromGround. The vanilla check is in decomp common/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1136-1147: deltaPackets > 5 resets to 1, movedDist is measured from firstGood, and the budget is 100*deltaPackets. On the client, SurfController.java:93-99 and 169-174 resync and restart from the correction's zero deltaMovement.

**Evidence.** Client game test against the in-process dedicated server (survival, not the owner). Code: ProbeClientTest.dedicated/lagBurst in worktree wf_926b46be-3ca-2. Run with: caffeinate -d -u -t 900 ./gradlew runClientGameTest -PclientTests=probe-ded.
Setup:
- Properties server-port=25611.
- AFrame(SURF_RAMP, 40, g, 0, 6, 200, false).
- s.tp(eastX(3,0.01), base+3, z, 0, 8); s.velocity(new Vec3(-0.02, 0, 1500*UPS)); hold keyRight; 8 x s.tick().
- Emulate vanilla's catch-up after a hitch (Minecraft.runTick runs up to 10 ticks per frame): context.runOnClient(c -> { for (int i = 0; i < 5; i++) try (var g = c.collectPerTickGizmos()) { c.tick(); } }), then 4 x s.tick().
Results:
- Server WARN 'Player0 moved too quickly! -0.114,0.143,11.430' and 1 correction teleport (ServerCorrectionsMixin).
- The controller resynced once. Speed went 1500.1 -> 12.7 -> 14.5 -> 14.9 u/s, and the player was pulled back 3.8 blocks (z 78.77 -> 74.96).
- An 8-tick burst gave the same result.
- Control: an 8-tick burst at 400 u/s had no rejection and kept 400.3 u/s.
Why: the k-th packet in one server tick fails when (k*d)^2 > 100 once k > 5, so 6 packets fail above 1.667 b/t (1312 u/s), 8 above 984 u/s and 10 above 787 u/s.

**Impact.** This hits any dedicated or LAN guest; the single-player host is exempt. A client frame hitch, a server GC or autosave stall, or a TCP retransmit that delivers 6 or more move packets in one server tick rubber-bands the surfer several blocks and zeroes their speed mid-ramp. That ends the run. Vanilla never trips it: sprinting needs 35 packets, and elytra get a 3x budget.

**Fix direction.** On the server, give surfing players a budget that fits surf speeds. For example, a mixin on the moved-too-quickly comparison that, for players with recent ramp contact or fast known movement, uses the elytra 300 budget or skips the check and relies on the moved-wrongly re-simulation. This avoids telling owners to turn off player_movement_check. Optionally, also keep the core velocity across small corrections on the client.

**Skeptic.** I reproduced this on the server side myself. In 26.3, PacketProcessor.processQueuedPackets handles every queued packet at the start of a tick (MinecraftServer.processPacketsAndTick, line 995). That is before tickPlayer runs resetPosition and sets knownMovePacketCount = received (ServerGamePacketListenerImpl 323-330), so all packets in a burst share firstGood. Lines 1136-1147 then reset deltaPackets from >5 to 1 and allow only 100 squared blocks. On the client, Minecraft.runTick catches up with up to 10 ticks per frame (line 1214), and each tick sends a move packet (1917) and a tick-end packet (1964).

Probe: src/gametest/java/dev/afunk/surfcraft/gametest/SkepticServerProbeGameTests.java (moveBursts), registered in src/gametest/resources/fabric.mod.json. Run ./gradlew runGameTest and grep SKEPTIC. It uses a real listener from makeMockServerPlayerInLevel; GameTestServer.isSingleplayerOwner is false, so the check applies. Setup: handleAcceptPlayerLoad, then teleport, resetPosition, and an acknowledgement with the id read by reflection from awaitingTeleport, then tick(). Each packet is handleMovePlayer(new Pos(x, y, z+k*d)) plus handleClientTickEnd, with no tick() between packets of a burst.

Results:
- 4 single packets, one per tick, at 1500 u/s: 0 rejections.
- Burst 5 @1500: 0 rejections.
- Burst 6 @1500: rejected at packet 6, log 'moved too quickly! 0,0,11.43' (the same 11.43 as the finder's log).
- Burst 8 @1500: rejected at packet 6.
- Burst 6 @1260: ok. Burst 6 @1340: rejected.
- Burst 10 @748: ok. Burst 10 @827: rejected.
- Burst 6 @3500: rejected.
- Burst 20 (a 1 s server stall) @300: ok. Burst 20 @420: rejected.

The correction teleports with Vec3.ZERO (lines 1301-1302), so the client's velocity becomes 0 and the controller resyncs (93-99) from zero. The README promises 'no rubber-banding' on servers.

Note for the fixer: the elytra budget of 300 alone still fails 6-packet bursts above 2273 u/s and 10-packet bursts above 1364 u/s. The deltaPackets reset is the crux. How often it triggers depends on hitches (client frame stalls, server stalls, TCP retransmit bursts), but the speeds involved are ordinary surf speeds.

### B2: Holding jump (auto hop) skips fall damage on about 72% of flat-ground landings: the landing and the hop happen inside one tick, so the server never sees onGround

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/SurfController.java:203-223: publish takes onGround from d.core.grounded at the tick's end. TickDriver.java:72-83 runs 3-4 substeps per tick. Server side: decomp ServerGamePacketListenerImpl.java:1205-1209 calls doCheckFallDamage(.., isOnGround), then resetFallDistance on the next upward packet. Entity.java:1587-1603 calls fallOn only when onGround.

**Evidence.** Client game test, single player survival (ProbeClientTest.fallDamageWithJump, -PclientTests=probe-sp).
Setup:
- 5:4 AFrame(ridge 0, base g, height 6, length 100); heal.
- s.tp(eastX(h,0.05), base+h, 8+3k, -90, 0) with h = 5.9-0.07k.
- s.velocity(new Vec3(300*UPS, 1.0, 0)): apex 15.2-15.7 blocks above flat grass, landing near x 17 in the post-surf window, controller driving.
Results:
- Jump held from tick 25 (after the apex): 6 of 6 landings, health 20.0 -> 20.0. The trace shows each landing tick ending 0.15-0.26 blocks above the floor with grounded=false and onGround=false.
- Jump not held: 3 of 3 landings, 20.0 -> 10.3, 8.8 and 8.2 (12 damage, then regeneration).
- JUnit probe src/test/java/dev/afunk/surfcraft/physics/ProbeLandingTest.java (TickDriver on a flat floor brush, Config.CSS_SURF.withStepHeight(0.6*K), MC hull, jump held, 400 drop heights): 290 of 400 landing ticks end airborne, so no onGround=true packet is sent. Run with: ./gradlew test --tests dev.afunk.surfcraft.physics.ProbeLandingTest -i

**Impact.** Surfers hold jump, so any drop onto flat ground after leaving a ramp is free about 3 times in 4. The landing sound and the HIT_GROUND event are skipped too. CS:S applies fall damage in CheckFalling at the end of the landing command, before the next command's jump, and vanilla applies it on every landing, so this matches neither. The existing bunny-hop test holds jump on flat ground and cannot see it.

**Fix direction.** Track a 'landed on walkable ground after falling' flag per tick in TickDriver, like `surfed`. Publish onGround=true for that tick, using the downward probe. Alternatively, compute CS:S fall damage per substep and report the landing.

**Skeptic.** I reproduced this independently with src/test/java/dev/afunk/surfcraft/physics/SkepticLandingProbeTest.java (./gradlew test --tests dev.afunk.surfcraft.physics.SkepticLandingProbeTest -i). A shadow SourceMovement runs the same substep loop as TickDriver, and the test asserts it stays bit-identical to the driver at every tick end, so each substep's ground state is visible.

Results (600 drops of 4 to 15.5 blocks onto a flat brush, MC hull, step 0.6 blocks):
- Jump held: 423 of 600 landing ticks (70.5%) end airborne after the in-tick hop. Only landings in a tick's last substep end grounded (by landing substep: 176, 185, 177, 62; hidden: 176, 185, 62, 0). Published feet are 0.11-0.37 blocks above the floor in the hidden ticks.
- Jump not held: 0 of 600 hidden.

Server path:
- Damage comes only through doCheckFallDamage with the packet's onGround (1204-1205).
- movedUpwards resets the fall distance (1207-1209).
- Entity.checkFallDamage calls fallOn only when onGround (1587-1603).
- ServerPlayer is not authoritative, so its own move never calls checkFallDamage (Entity.move 806-809).

CS:S CheckFalling would have applied the damage at the landing substep. The README tells players to 'Hold jump to bunny hop on landing', and c1 intends vanilla fall damage on flat ground after a ramp. So this happens in normal play, and whether a landing hurts is effectively random.

### B3: Slime and bed bounces are erased under the controller, and so is the soul sand/honey slowdown. Landing on slime after a surf gives no bounce and a quick stop.

- **Severity:** reviewer major, after skeptic **minor** (partial)
- **Where:** src/client/java/dev/afunk/surfcraft/client/SurfController.java:210-215: player.move(...) runs, then setDeltaMovement(core velocity) overwrites whatever move() wrote. Lines 145-148 adopt only changes made after travel. Vanilla Entity.move writes the restitution bounce (Entity.java:816-817, 840-882) and the block speed factor (827-828) into deltaMovement inside move(). SlimeBlock.stepOn (x/z *0.4) runs later in applyEffectsFromBlocks and is adopted.

**Evidence.** Client game test, single player survival (ProbeClientTest.slime, probe-sp). Slime replaces the floor at x 8..30, z 40..52 (east of the 5:4 A-frame) and at x 55..66.
Controller case:
- s.tp(eastX(5.9,0.05), base+5.9, 46.5, -90, 0); s.velocity(new Vec3(300*UPS, 1.0, 0)).
- Lands on slime at tick 44 while driving and bounces 0.00 blocks.
- Horizontal speed 0.143 b/t at the landing tick (stepOn's x0.4 already applied: about 282 -> 113 u/s), 0.000 five ticks later.
Vanilla control:
- Dropped from the same 15.7 blocks onto the far slime field, it bounces 9.34 blocks.
Soul sand (ProbeClientTest.soulSand, probe-sp3 + probe-effects):
- Walking forward on soul sand beside the ramp toe: 6.350 b/s (driving).
- Far from ramps: 2.508 b/s.

**Impact.** Slime launch pads and landing pads, a staple of Minecraft surf and parkour maps, do not bounce after a surf (the post-surf window) or within about 1.5 blocks of a ramp. Beds and shelf mushrooms (restitution 0.75) do not bounce either. Soul sand and honey do not slow anyone near ramps.

**Fix direction.** Fold the block-driven velocity changes from move() into the core instead of discarding them. For example, when move() bounced on a bouncy block (deltaMovement.y flipped), take the core's vertical velocity from it. Apply getBlockSpeedFactor to the core's horizontal velocity, or exclude it on purpose, so speed factors and stepOn are treated consistently.

**Skeptic.** The mechanism is real; the severity is overstated. In decomp Entity.move, restituteMovementAfterCollisions (812-813, 840-882) writes the slime or bed bounce into deltaMovement, using getBlockBounciness on a downward vertical collision. The block speed factor is also applied inside move (826-827). SurfController.publish calls move (210) and then overwrites deltaMovement with the core velocity (214-215). The publish probe (request y - 1e-3 against the trusted exact y) makes verticalCollisionBelow true, so the restitution does run and is then discarded. SlimeBlock.stepOn runs after travel and is adopted on the next tick (145-148), which explains the finder's x0.4 'quick stop'; vanilla slime walking is slow too. The finder's measurements (bounce 0.00 vs 9.34 in vanilla) agree with this path.

It only happens when a builder puts slime, beds, soul sand or honey in a post-surf landing zone or within about 1.5 blocks of a ramp. That is a specific map choice, not normal surf play. Walking near ramps is uniformly CS:S 250 u/s on any block by design. So it is an edge case: minor, not major.

### B4: Sneaking next to a ramp neither slows you down nor stops you at ledges: players walk off start platforms beside ramps while sneaking

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/SurfController.java:100-121: near a ramp it always drives, and forward/side are taken raw from keyPresses (±400), bypassing LocalPlayer.modifyInput (sneak/crawl x0.3, item-use slowdown). The sneak hand-back on line 109 exists only in the not-near branch. src/main/java/dev/afunk/surfcraft/mixin/PlayerMixin.java:36-40 (e2) removes maybeBackOffFromEdge near ramps on both sides.

**Evidence.** Client game test, single player survival (ProbeClientTest.sneakPlatform, -PclientTests=probe-sp2).
Platform next to the ramp:
- A stone start platform at y=g+5 (top g+6), x -1..1, z 100..104, adjoining the north end of the 5:4 A-frame (ridge 0, height 6, z 0..99).
- tp (0.5, g+6, 100.5, yaw -90); hold sneak 5 ticks (driving=true), then sneak+W for 30 ticks.
- The player walks off the east edge (x 2) and falls 6 blocks: feet 6.00 below the platform, x 7.82, health 20.0 -> 18.8.
Control, same platform far from ramps (x 62..64):
- Held at the edge: x 65.26 with the edge at 65, feet 0.00.
Sneak speed on flat ground beside the ramp toe (ProbeClientTest.sneak, probe-sp):
- 6.350 b/s under the controller, versus 1.295 b/s far away.
- Vanilla sprinting is 5.612.

**Impact.** Builders and players on spawn or start platforms or ledges within about 1.5 blocks of a ramp lose sneak's edge protection and its slow walk. They fall off while sneaking and take fall damage. Crawling and item use (eating, bows) also do not slow them. The CS:S duck speed (x0.34) is not ported either.

**Fix direction.** When Source-grounded on non-ramp ground and sneaking, hand the tick to vanilla, as the post-surf branch already does. Alternatively, port the duck speed and apply vanilla's edge back-off in the controller's grounded publish. The client may back off itself, and e2 keeps the server from shrinking the move again.

**Skeptic.** The code path holds.
- decide() (100-115) drives whenever a ramp is within speed + 1.5 blocks, whether or not sneak is held. The sneak hand-back on line 109 exists only in the not-near branch.
- e2 (PlayerMixin 36-40) returns the unshrunk delta whenever shift is down and RampCollision.near holds (box expanded by the move and inflated 1.0 block). It does this on both sides, so even vanilla movement within 1 block of a ramp loses the edge back-off.
- The finder's platform sits at z 100..104, against the ramp's last row at z 99, inside both radii.

Caveats:
- The slow-walk half is already a documented known limit: MODLOG Wave B says 'duck is not ported (sneak only shrinks the box)'.
- The ledge half is not documented. It contradicts the controller's own comment ('Sneaking on plain ground: vanilla's edge back-off ... needs vanilla movement').
- The proposed fix is incomplete. Handing the tick to vanilla near a ramp will not restore the back-off while e2 also cancels it on the client; e2 would have to be limited to actual ramp contact.

Survival builders sneaking along ledges beside ramps they are building will fall, so major stands.

### B5: Levitation and Slow Falling do nothing while the controller drives; Speed and Jump Boost are ignored too

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/SurfController.java:123-128: eligible() does not exclude them. Gravity, speed and jump are fixed in Config.CSS_SURF (src/main/java/dev/afunk/surfcraft/physics/Config.java:11-13).

**Evidence.** Client game tests, single player survival:
- Levitation II for 2 s ('effect give @a minecraft:levitation 2 1 true'), standing at (5.6, g, 90.5) beside the ramp toe: rose 0.00 blocks in 30 ticks while driving. Far away at (60.5, g, 90.5): rose 2.21 blocks.
- Slow Falling, launched off the ridge (300 u/s east, 1 b/t up): reached the floor in 44 ticks with a fastest descent of -1.240 b/t. Dropped from the same height far away: 69 ticks, -0.368 b/t.
- Speed II walking (probe-sp3 + probe-effects): 6.350 b/s beside the ramp versus 6.044 far away.
- Jump Boost II jump height: 1.390 blocks beside the ramp versus 2.517 far away.

**Impact.** A shulker bullet or a potion next to a ramp, or after a surf, has no movement effect. Slow Falling still prevents most damage only because vanilla keeps resetting fall distance each tick.

**Fix direction.** Add !player.hasEffect(MobEffects.LEVITATION) && !player.hasEffect(MobEffects.SLOW_FALLING) to eligible() so vanilla handles those ticks. For Speed and Jump Boost, decide whether CS:S fixed values are intended, and document it.

**Skeptic.** eligible() (123-128) has no effect checks, and Config.CSS_SURF fixes gravity at 800. PlayerTravelMixin cancels travel while driving, so vanilla's levitation and slow-falling gravity in travelInAir never runs. That matches the finder's 0.00-block rise and -1.24 b/t descent. Fall damage still does not regress, because LivingEntity.aiStep resets the fall distance under slow falling or levitation.

Ignoring Speed and Jump Boost follows from the fixed CS:S values. That is a design decision rather than a defect. Levitation and Slow Falling near ramps or right after a surf are rare, so minor is right.

### B6: The speedometer is drawn on the action-bar line, garbling the Karambit's feedback; it also shows '0 u/s' whenever you stand within about 1.5 blocks of a ramp

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/SpeedHud.java:19-20 draws centeredText at guiHeight-72. Vanilla Hud.extractOverlayMessage centres the action bar at guiHeight-68 with text at y-4 (decomp client/net/minecraft/client/gui/Hud.java:342-351), so both use the same 9-pixel band. KarambitItem.feedback uses sendOverlayMessage (src/main/java/dev/afunk/surfcraft/karambit/KarambitItem.java:81).

**Evidence.** Client game test, single player survival (ProbeClientTest.hudOverlap, probe-sp):
- tp (5.6, g, 95.5, yaw 90, pitch 10) beside the 5:4 A-frame. While idle, the controller reports driving=true and the speedometer shows '0 u/s'.
- runOnServer(srv -> player.sendOverlayMessage(Component.literal("Placed the module: 96 blocks"))), the Karambit's own path.
- The screenshot reads 'Placed the @odule: 96 blocks', with '0 u/s' printed over 'module'. File: build/gametest/screenshots/0000_probe_hud_actionbar_overlap.png in the probe worktree.

**Impact.** Every Karambit message shown while standing near a ramp, which is where the tool is used, is unreadable. The same goes for any server action-bar text such as timers or plugin HUDs.

**Fix direction.** Move the speedometer off the action-bar band, or skip drawing it while an overlay message is visible. Consider hiding it at about 0 u/s.

**Skeptic.** SpeedHud line 20 draws at guiHeight-72. Hud.extractOverlayMessage (client decomp 341-351) translates to guiHeight-68 and draws the text at y-4, which is also guiHeight-72: the same 9-pixel band. I viewed the finder's screenshot (wf_926b46be-3ca-2/build/gametest/screenshots/0000_probe_hud_actionbar_overlap.png): 'Placed the [garbled]: 96 blocks', with '0 u/s' over 'module'. The speedometer shows whenever the controller drives, which includes standing idle within about 1.5 blocks of a ramp, where the Karambit's copy and extend are used.

'Unreadable' overstates it: one word is garbled at '0 u/s' (more at 4-digit speeds). It is a cosmetic overlap, so minor.

### B7: A long vertical launch gets the surfer kicked for flying on a dedicated server

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** No SurfCraft exemption exists. Vanilla sets clientIsFloating in decomp ServerGamePacketListenerImpl.java:1193-1201 and kicks after 80 ticks (getMaximumFlyingTicks) at 331-339. CS:S gravity is 800 u/s^2 (0.0508 b/t^2, no drag). Research doc 4.5 says the kick 'never triggers next to ramps', but noBlocksAround only looks 0.55 blocks down.

**Evidence.** Client game test against the dedicated server (ProbeClientTest.floating, probe-ded):
- s.tp(eastX(5.8,0.01), base+5.8, 160, 0, 0) on the 5:4 A-frame.
- s.velocity(new Vec3(0, 3400*UPS, 0)): the controller adopts it, and it is below Config's 3500 per-axis cap.
- After 82 ticks of rising (top y 129.1), the server logged 'Player0 was kicked for floating too long!' and the client was disconnected.
Threshold: floating lasts (v_z + 24.6)/800 s, so more than 80 ticks needs v_z above about 3175 u/s.

**Impact.** A surfer launched upward near the velocity cap is disconnected mid-air. The same applies to an adopted explosion or launcher velocity of about 4 b/t up, since CS:S gravity makes airtime 2-3x vanilla's. Rare to reach by surfing alone, severe when it happens.

**Fix direction.** Exempt players in the post-surf window from the floating kick. For example, call resetFlyingTicks() while the reported vertical movement keeps decreasing at CS:S gravity or faster, or while the last ramp contact is recent.

**Skeptic.** The kick logic is as described.
- tickPlayer (331-335) kicks after more than getMaximumFlyingTicks consecutive floating ticks: 80 at gravity 0.08 (369-376).
- clientIsFloating (1193-1201) needs only oyDist >= -1/32, no support, allow-flight off (the dedicated default) and noBlocksAround, which looks 0.55 blocks down (539-541).

My probe SkepticServerProbeGameTests.floatingKick feeds rising packets at CS:S gravity and counts 78, 79, 81 and 86 floating packets for v0 = 3100, 3150, 3200 and 3400 u/s, so the threshold is about 3175 u/s, matching the finder. GameTestServer keeps MinecraftServer.allowFlight() = true, so the kick itself only shows on a dedicated server, where the finder observed it.

It is hard to reach. Clipping onto a 51 or 63 degree plane yields at most v*sin(t)*cos(t), about 0.5 of horizontal speed. Even chaining both slopes and a wall with the 3500-per-axis clamp tops out near 2700 u/s, before gravity. Reaching the threshold needs stacked explosions or contrived geometry, so it is an edge case: minor.

### B8: An ender pearl thrown while surfing keeps part of the old momentum after the teleport, because e3 restores the pre-teleport movement on the pearl's damage

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/mixin/LivingEntityMixin.java:38-43 (e3) sets deltaMovement = getKnownMovement() on any hurtServer above 0.6 b/t or near ramps. Vanilla teleports with Vec3.ZERO and then hurts the owner for 5 (decomp common/net/minecraft/world/entity/projectile/throwableitemprojectile/ThrownEnderpearl.java:113-119). The hurt motion packet therefore carries the stale pre-teleport movement.

**Evidence.** Client game test, single player survival (ProbeClientTest.pearl, probe-sp2):
- 16 ender pearls in slot 0.
- Surfing the 5:4 slope at 1500 u/s: tp eastX(3,0.01), base+3, z 10, yaw 0, pitch 70; velocity (-0.02, 0, 1500*UPS); hold D.
- Press use. Speed trace: t0-t1 1500 u/s; t2 30 u/s (the teleport zeroed it and the controller resynced); t3-t15 819 u/s (adopts=1, the server's motion packet), sustained.
Vanilla leaves the velocity at zero after a pearl.

**Impact.** Pearling to stop or reposition mid-surf flings the player onward from the pearl's landing point, in the old direction.

**Fix direction.** Skip e3 when the known movement predates a teleport (e.g. while a teleport acknowledgement is pending), or apply it only to damage that carries knockback.

**Skeptic.** ThrownEnderpearl (113-119) teleports the owner with Vec3.ZERO, then calls hurtServer(5). Entity.teleportSetPosition (3259-3268) never touches ServerPlayer.lastKnownClientMovement, which getKnownMovement returns (2204-2216). So e3 (LivingEntityMixin 38-43) restores the pre-teleport movement whenever it exceeds 0.6 b/t.

Ender pearl damage is not tagged #no_impact (jar data: only drown), so hurtServer calls markHurt (LivingEntity around 1238-1239). The resulting motion packet carries the old velocity, and the controller adopts it. That matches the finder's sustained 819 u/s after t3. Vanilla leaves the velocity at zero. Pearling mid-surf is uncommon, so minor.

### B9: An elytra can't be deployed while touching a ramp: the client starts gliding, the server refuses, and the surfer loses 2 ticks of CS:S movement

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** LocalPlayer.aiStep starts gliding because the controller reports onGround=false on ramps. The server's START_FALL_FLYING handler calls Player.tryToStartFallFlying, which needs LivingEntity.canGlide and therefore !onGround() (decomp ServerGamePacketListenerImpl.java:1786-1789, LivingEntity.java:3179-3189). The server's onGround is true from its own zero-input doTick simulation landing on the slope (research doc 4.4). SurfController.eligible() drops out while gliding.

**Evidence.** Client game test, single player survival (ProbeClientTest.elytra, probe-sp2):
- 'item replace entity @a armor.chest with minecraft:elytra'; surfing the 5:4 slope at 1200 u/s holding D; jump held for one tick.
- t1: client gliding=true, server gliding=false, controller not driving.
- t2: client gliding=false (the server's stopFallFlying was synced), still not driving.
- t3: driving again.
- Speed went 1200 -> 1178 u/s, and the glide never started.

**Impact.** Each fresh jump press on a ramp with an elytra worn costs speed and 2 ticks of vanilla physics, and gliding off a ramp while in contact is impossible.

**Fix direction.** Don't try to start the elytra while the controller has ramp contact. Or reconcile the server's ground flag near ramps: its doTick simulation should not report onGround on surf slopes.

**Skeptic.** The sequence holds on both sides.
- Client: LocalPlayer.aiStep (834-835) calls tryToStartFallFlying. LivingEntity.canGlide (3179-3191) only needs !onGround, and the controller publishes onGround=false on ramps, so the client starts gliding.
- The START_FALL_FLYING command is sent during aiStep, before that tick's move packet, so the server evaluates it with the onGround from its own zero-input doTick. The hull is the MC box (BrushWorld.hull) touching the plane, so that simulation lands on the slope and onGround is true.
- The server's tryToStartFallFlying therefore fails and it calls stopFallFlying (1786-1789).
- eligible() drops out while gliding, and stop() restarts the core from deltaMovement.

The result is the finder's 2 lost ticks and about 2% speed (1200 to 1178 u/s). The research doc section 10 already noted that the elytra drops a player out of the controller; the server refusal is new. Wearing an elytra while surfing and pressing jump on a ramp is uncommon, so minor.

## Lens C: Multiplayer and server

**Checked and found sound:** All probe code is in the worktree /Users/funk/code/sandbox/minecraft/.claude/worktrees/wf_926b46be-3ca-3 and is not committed:
- src/gametest/java/dev/afunk/surfcraft/gametest/ProbeClient.java
- src/gametest/java/dev/afunk/surfcraft/gametest/ProbeServerTests.java (registered in src/gametest/resources/fabric.mod.json)
- src/test/java/dev/afunk/surfcraft/physics/ProbeGeometryAgreementTest.java
- SurfClientTest.probeHopLanding
- probe/vanilla_speed.py, with logs in probe/
./gradlew build passes with all of them (30 game tests).

What I checked and found sound:

1. Server re-simulation vs client-published moves on geometries other than an endless A-frame. JUnit probe, same method as ServerAgreementTest: random surfs through TickDriver, and every tick ExactCollide.resolve(lift=true) must land within 0.25 blocks horizontally. Geometries:
   - ramp ends in both directions, on 5:4 and 2:1;
   - a wall across a ramp's end;
   - a one-sided ramp against a wall;
   - a slope change from 5:4 to 2:1 and back;
   - asymmetric 5:4|2:1 and stepped ridges;
   - a 2-block flat top;
   - stone supports with BrushWorld's bevels, 5:4 and 2:1;
   - a cross slope rising ahead, and one falling away.
   Over ~430k ticks there were ~1.8k blocked chords and 0 failures (worst 0.0001 blocks); vanilla axis order alone resolved every blocked chord. Karambit's default module is the same two-sided 5:4 A-frame (SurfModule.twoSided).

2. The existing client tests 'surf' and 'server' pass on this machine: 0 rejections, 0 corrections, replay difference 0.00e+00.

3. Players are not colliders: only boats, shulkers and happy ghasts override canBeCollidedWith. Players only push each other, which the controller adopts as velocity. Boats are brushes on both sides; their client-side interpolation lag is the same as in vanilla.

4. Server CPU, from server game tests, per surfer per tick:
   - On a 2:1 A-frame of height 8: re-sim hook 15-45 us, own-simulation (doTick) hook 10-40 us, c1 contact 4-20 us, e1 under 1 us, about 50-90 us in total. Vanilla's collision of the same move takes 8-24 us.
   - In a dense field of single random cells that cannot merge: 80-125 us, vs 6-14 us for vanilla.
   That is a few ms per tick for dozens of surfers. The client controller's median is 0.16 ms (surf test).

5. Fall damage after a surf without jump held matches vanilla: it counts from the apex or the last ramp contact, and 40/40 (server probe) and 12/12 (client probe) landings dealt floor(fall - 3). c1 runs at the accepted position on both the accept and reject paths.

6. Hunger and stats: surfing is airborne, so vanilla's airborne rules apply (FLY_ONE_CM stat, no exhaustion). e1 only suppresses the server's guessed jumps near ramps.

7. How other players see a surfer: a SteppedInterpolationHandler over the player update interval (2 ticks) plus latency, and a hitbox equal to the MC box at the accepted position. This is the same as any fast vanilla player, such as an elytra flyer.

8. Joining across mod sets, checked from the code (no second client was run):
   - A vanilla client gets Fabric's 'This server requires Fabric Loader and Fabric API installed on your client! ... surfcraft ... Contact the server's administrator' (RegistrySyncManager.configureClient).
   - A Fabric client without SurfCraft gets 'Received N registry entries that are unknown to this client... surfcraft'.
   - A SurfCraft client on a server without the mod never finds a ramp block, so the controller never drives.
   These messages are sane.

9. The 'standing on air - force-sending blocks below' INFO lines (4 in the surf run) are vanilla's rate-limited message around teleports and landings. They are benign.

Notes for fixers writing tests:
- Game tests are encased in barrier blocks, so falls taller than the default 8-block box land on its ceiling unless the barriers are removed.
- A teleport must be acknowledged one server tick later, or the acknowledgement itself trips 'moved too quickly'.
- makeMockServerPlayer is authoritative, so its move() calls checkFallDamage. Real players get fall damage through doCheckFallDamage in the packet handler.
- Fabric's client game tests run client and server in lockstep, so lag bursts (C1) can only be reproduced in server game tests.

### C1: Surfers above ~1310 u/s are teleported back and stopped dead when 6 move packets reach the server in one server tick (a ~300 ms server stall or client hitch)

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** No mod code covers this. Vanilla check (decomp common/net/minecraft/server/network/ServerGamePacketListenerImpl.java:1122-1151): movedDist is measured from firstGood, which is reset once per server tick in tickPlayer (:322-330). From the 6th packet in a tick, deltaPackets is reset to 1. Mod side: src/client/java/dev/afunk/surfcraft/client/SurfController.java:93-99 and :172. A correction resyncs the controller, which restarts from deltaMovement, and the correction sets that to 0 (client ClientPacketListener.java:811-812; ServerGamePacketListenerImpl.java:1300-1301 passes Vec3.ZERO). docs/dev/mc26-movement.md:662-667 says the check only fails at top speed.

**Evidence.** Server game test using a real survival ServerPlayer on an embedded connection, sent exactly the packets a client sends. Probe files in the worktree: src/gametest/java/dev/afunk/surfcraft/gametest/ProbeClient.java and ProbeServerTests.probeBurstMovedTooQuickly.

The essential setup:
  ServerPlayer p = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
  Connection c = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(c);
  server.getPlayerList().placeNewPlayer(c, p, CommonListenerCookie.createInitial(profile, false));
  p.setGameMode(GameType.SURVIVAL);
  p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

Teleport (the ack must come one server tick later; an ack in the same tick trips 'moved too quickly' by itself):
  p.connection.teleport(x,y,z,0,0); p.connection.tick();
  p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(<private int awaitingTeleport via reflection>, x,y,z,0,0));
  handleClientTickEnd(INSTANCE); p.connection.tick();
This connection is not in the server's list, so connection.tick() stands in for the server tick.

Each case: teleport into open air, call p.connection.tick() once, then send n times
  handleMovePlayer(new ServerboundMovePlayerPacket.Pos(start.add(k*v,0,0), false, false));
  handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
with no server tick in between. The existing ServerCorrectionsMixin records teleports.

Output of ./gradlew runGameTest:
- 6 packets at 1250 u/s: accepted.
- 6 at 1350 u/s: TELEPORTED BACK at packet 6. Log: 'probe0 moved too quickly! 10.287,0.0,0.0'. Server stays at 8.572 of 10.287 blocks.
- 6 at 2200 u/s: back at packet 6.
- 5 at 3500 u/s: accepted. 6 at 3500 u/s: back at packet 6.
- 10 at 750 u/s: accepted. 10 at 850 u/s: back at packet 10.

So the k-th packet (k >= 6) fails when (k*v)^2 > 100. For 6 packets that is above 1.667 blocks/tick (1312 u/s); for 10 packets, above 787 u/s.

How bursts form:
- Packets queued while the server thread is busy all run in one runAllTasks between ticks (MinecraftServer.java:844-849, shouldRun :870-871).
- A client that froze runs up to 10 catch-up ticks in one frame, each sending its position (client Minecraft.java:1214).

The client game tests cannot show this: Fabric locks client and server ticks to one phaser (fabric-client-gametest MinecraftServerMixin.postRunTasks). The existing 'server' client test passed in this run with 0 corrections.

**Impact.** On a dedicated server, and for LAN guests (the single-player owner is exempt), this triggers whenever a player is surfing faster than about 1310 u/s during:
- a ~300 ms server stall (autosave, GC, chunk generation);
- a ~300 ms client hitch (catch-up ticks, e.g. chunk loading at surf speed);
- a network stall that bunches packets.
A 500 ms stall lowers the threshold to about 790 u/s. The surfer is teleported back and their velocity set to 0: the controller resyncs from deltaMovement 0, so the run is over. It will keep recurring on any real server.

**Fix direction.** Give players the surf controller drives a budget their own speed fits. For example, add a mixin on the moved-too-quickly comparison in handlePlayerPositionChange that applies when a ramp is near or the player surfed recently. It could either keep counting the burst instead of resetting deltaPackets to 1 and allow about (k*4.45 blocks)^2 plus a margin, or check each packet's own step (target - lastGood) against the surf top speed. Do not tell owners to set player_movement_check false (see C7). Correct docs/dev/mc26-movement.md sections 4.5 and 10.

**Skeptic.** I re-ran it with my own probe: SkepticProbes.c1Burst in /Users/funk/code/sandbox/minecraft/.claude/worktrees/wf_926b46be-3ca-10/src/gametest/java/dev/afunk/surfcraft/gametest/SkepticProbes.java (log in probe/run2.log). Setup: a plain survival ServerPlayer on an EmbeddedChannel Connection via placeNewPlayer, plus handleAcceptPlayerLoad. Teleport, then ack one connection.tick() later (awaitingTeleport read by reflection). Then n times handleMovePlayer(Pos(start + k*v*x, false, false)) + handleClientTickEnd, with no tick in between.

Results:
- 6 packets at 1250 and at 1310 u/s: accepted.
- 6 at 1315 u/s: 'burst moved too quickly! 10.0203', teleported back at packet 6; the server stays at 8.350 blocks.
- 6 at 1350, 2200 and 3500 u/s: back at packet 6.
- 5 at 3500 u/s: accepted.
- 10 at 750 u/s: accepted. 10 at 850 u/s: back at packet 10.

This is exactly (k*v)^2 > 100 once deltaPackets > 5 resets the budget to 100 (decomp :1137-1150); the 6-packet threshold is 1312 u/s.

The client loses all speed:
- setValuesFromPositionPacket (ClientPacketListener:799-815) sets deltaMovement to the correction's Vec3.ZERO.
- SurfController.decide sees position != lastPos and calls stop().
- place() then takes velocity from deltaMovement, which is 0.

One citation in the evidence is wrong for 26.3, but the conclusion holds. Game packets do not go through runAllTasks/shouldRun (MinecraftServer:844-871). PacketUtils.ensureRunningOnSameThread queues them on server.packetProcessor(), and processQueuedPackets drains them in one batch at the start of every tick (MinecraftServer.processPacketsAndTick:990-995). So all packets that arrive during a slow tick of 300 ms or more are handled together before the next tickPlayer. A client hitch produces the same burst: Minecraft.java:1214 runs min(10, ticksToDo) ticks per frame, and each tick sends a position.

The docs (sections 0, 4.5 and 10: 'at top speed') understate the threshold, and the README says there is no rubber-banding on dedicated servers. I did not measure how often servers stall.

### C2: Landing with jump held (auto hop) skips fall damage on about 2 of 3 landings, from any height

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** - src/main/java/dev/afunk/surfcraft/physics/SourceMovement.java:62 and 67-79: a grounded core with jump held jumps on the next substep and clears grounded.
- src/main/java/dev/afunk/surfcraft/physics/TickDriver.java:73-84: each tick runs 3-4 substeps and publishes only the last substep's state.
- src/client/java/dev/afunk/surfcraft/client/SurfController.java:205-220: the packet's onGround is d.core.grounded at the tick boundary.
- Server (decomp ServerGamePacketListenerImpl.java:1205-1209): fall damage only applies from a packet with onGround set, and any upward packet resets the fall distance.

**Evidence.** (1) Real client. I added a probe to SurfClientTest (worktree, method probeHopLanding) and ran: caffeinate -d -u -t 1500 ./gradlew runClientGameTest -PclientTests=surf,server.
- Setup: a pole of standalone Steep Surf Ramp cells (east, cut 2) at (60, g..g+15, -40).
- Each drop: on the server, setHealth(20), damageCooldownTime=0, resetFallDistance; then /tp to (61.7, g+h, -39.5) with h = 10 + 0.137*i, velocity 0, jump held or not. Tick until 10 ticks after landing and record the lowest server health.
- The player's box is 0.4 blocks from the pole, so the controller drives every tick and c1 never touches.
- No jump: 'PROBE client drops next to a ramp pole, no jump: 12 of 12 hurt, controller drove every tick in 12 (height:damage) 10.00:6 10.14:7 ... 11.51:8'.
- Jump held: 'holding jump: 5 of 12 hurt ... 10.00:0 10.14:7 10.27:7 10.41:0 10.55:0 10.69:0 10.82:0 10.96:7 11.10:8 11.23:8 11.37:0 11.51:0'.

(2) Server game test ProbeServerTests.probeHopLandingFallDamage, using the real packet handler (ProbeClient from C1).
- The shared physics core falls from rest, 10 to 15.3 blocks, onto stone: TickDriver with Config.CSS_SURF.withStepHeight(0.6*K), hull BrushWorld.hull(player), brushes BrushWorld.collect(level, player, toMinecraft(d.reach(hull,60)), anchor, false).
- Each tick: d.tick(config,0,0,jump,-90,hull,world); then Pos(toMinecraft(d.published), d.core.grounded, d.wall) plus a tick end; then connection.tick().
- No jump: 40 of 40 landings hurt, 6-12 HP each, which is floor(fall - 3).
- Jump held: 12 of 40 hurt and 28 took 0 (for example, 15.34 blocks gave 0 instead of 12). 12/40 matches 1/3.33, the chance that the landing substep is the last one of its tick.

**Impact.** Bunny-hopping onto flat ground after a ramp is normal surfing, and it makes fall damage a coin toss: the same drop deals full damage or none (none about 2 times in 3). It is also an exploit: hold jump to avoid fall damage from any height wherever the controller drives (near ramps, or airborne after leaving one).

Vanilla always reports ground on the landing tick, so every landing hurts. CS:S applies fall damage in CheckFalling on the landing command, before the next command's jump. That CS:S behaviour is from the Source SDK and not measured here; the surf repo models no fall damage.

**Fix direction.** Report the landing. Have TickDriver flag a tick in which any substep ended grounded, and send onGround=true for that tick, so doCheckFallDamage lands the fall before the next upward packet resets it.

**Skeptic.** I reproduced it with my own probe, SkepticProbes.c2HopLanding (probe/run2.log). It drives the shared core the way the client does:
- TickDriver with Config.CSS_SURF.withStepHeight(0.6*K), hull BrushWorld.hull(p), brushes BrushWorld.collect(level, p, toMinecraft(d.reach(hull,60)), anchor, false).
- It drops from rest, 10 to 15.34 blocks, onto stone.
- Each tick: d.tick(cfg,0,0,jump,-90,...), then Pos(toMinecraft(d.published), d.core.grounded, d.wall), a tick end, and connection.tick() on a real survival ServerPlayer.
- Health and damage cooldown are reset per drop.

Results:
- Without jump: 40 of 40 drops hurt (6 to 12 HP).
- Holding jump: 12 of 40 hurt; 28 took 0 (for example 15.34 blocks gave 0 instead of 12). The 12 that hurt are exactly the drops whose landing substep was the last of its tick, so that tick boundary was grounded and sent onGround=true. This is the same 12/40 the finding reports.

Code path:
- SourceMovement:62 and 67-79 jumps on the next substep.
- TickDriver publishes only the tick-boundary state.
- On the server, doCheckFallDamage lands a fall only on a packet with onGround set, and movedUpwards resets the fall distance (decomp :1205-1209).

CS:S runs CheckFalling at the end of the landing command, before the next command's jump, so dropping the landing is a real integration error. Holding jump (auto hop is on) while dropping more than 3 blocks to flat ground after a ramp is ordinary, so 'major' holds.

### C3: c1 resets the fall on any face of a ramp block: flat tops, full cells and sides cancel any fall

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** - src/main/java/dev/afunk/surfcraft/mixin/LivingEntityMixin.java:23-30.
- src/main/java/dev/afunk/surfcraft/movement/RampCollision.java:57-66: touchesRamp inflates the box by 2 units on every axis and tests it against every plane of every ramp brush (rampsOnly), so axial faces, full cells and the flat top strips of cut cells all count as ramp contact.

**Evidence.** Server game test ProbeServerTests.probeFlatTopLanding, with ProbeClient. I first removed the framework's barrier ceiling: every game test is encased in barriers, and the default 8-block box has a ceiling.
- Blocks on a stone floor: a standalone Steep Surf Ramp (east, cut 2, solid 2u+y<=2, so the back half of its top is flat), a full Surf Ramp cell (cut 9), and a full cell standing on the floor.
- Each fall: teleport 18 blocks above the landing spot, send Pos packets every 2 blocks down with onGround=false, then the landing Pos with onGround=true.
- Onto the standalone steep ramp's flat back half: damage 0 (fall distance before landing 17.00).
- Onto the full surf ramp cell: damage 0 (17.00).
- Onto the stone floor while touching the side of a ramp cell: damage 0 (fall distance before landing already 0.00).
- Onto a stone block: damage 15.

**Impact.** Any single ramp block is a fall-damage-proof landing pad: land on the flat back half of a standalone ramp, or on a full cell. Falling along a ramp face within 0.05 blocks, or landing while touching one, also cancels the fall. On survival servers that is a cheap smooth-stone substitute for the water-bucket clutch.

It is also a fidelity error: a flat face is ground in CS:S (normal z >= 0.7) and in Minecraft. c1's own rationale ('ramps are not ground') only holds for the slope faces.

**Fix direction.** Count only contact with a slope plane: test the hull, inflated by 2 units, against the slope plane of cut cells and slabs (normal z between 0.01 and 0.7 in Source axes), not against axial faces or full cells. Test it through the packet path (doCheckFallDamage); makeMockServerPlayer is authoritative, so its move() path differs from a real player's.

**Skeptic.** I reproduced it with SkepticProbes.c3FlatFaces (skyAccess=true, so no barrier ceiling; log probe/run2.log) on a real survival ServerPlayer. Each fall: teleport 18 blocks up, send Pos packets every 2 blocks down with onGround=false, then the landing Pos with onGround=true.

Damage taken:
- Flat west half of a standalone steep ramp (EAST, cut 2): 0.
- Top of a full surf ramp cell (cut 9): 0.
- Stone floor with the box 0.01 blocks from a full ramp cell's side: 0.
- The same spot 0.3 blocks away: 15.
- Stone block, as a control: 15.

Cause, as stated: touchesRamp (RampCollision:57-66) inflates the box by 2 units and tests it against every plane of every ramp brush (rampsOnly keeps full cells and axial faces). c1 therefore treats flat tops and vertical sides as ramp contact. In CS:S and in Minecraft a flat top is ground.

It is an edge case and an exploit (a fall-proof landing pad), so 'minor' is right.

### C4: Sneaking off a ledge 1.0-1.8 blocks from a ramp is corrected ('moved wrongly'): e2's radius is smaller than the controller's

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** - src/main/java/dev/afunk/surfcraft/mixin/PlayerMixin.java:36-40 uses RampCollision.near (src/main/java/dev/afunk/surfcraft/movement/RampCollision.java:53-55: box.expandTowards(delta).inflate(1.0)).
- src/client/java/dev/afunk/surfcraft/client/SurfController.java:100-114: the controller drives whenever a ramp is within 1.5 blocks plus one tick of travel, and hands back for sneaking only when no ramp is near (:109). Its own moves have no edge back-off (trust payload, and the Source movement has none).

**Evidence.** Server game test ProbeServerTests.probeSneakEdgeNearRamp, with ProbeClient.
- Stone pillar with its top at y=4. Feet at (3.2999, 4, 2.5): box minX 2.9999, the last supported spot.
- Sneak via ServerboundPlayerInputPacket(new Input(false,false,false,false,false,true,false)), then an onGround=true packet and a server tick.
- Then Pos(feet + (0.3175, -0.016, 0)), one tick at 250 u/s, with onGround=false.
- Ramp block 1.2 blocks to the side of the box: BrushWorld.rampNear(box.inflate(0.3175+1.5)) is true (the client drives) but RampCollision.near(player, delta) is false. Log 'probe3 moved wrongly!'; result 'CORRECTED (moved wrongly) (server x moved 0.0000 of 0.3175)'.
- Control with the ramp 0.2 blocks away: both are true and the move is accepted (0.3175 of 0.3175).
- By the 0.05-block back-off steps in Player.maybeBackOffFromEdge (decomp Player.java:862-911), the server's error exceeds 0.25 when the edge is within 0.0675 blocks of the start, which is about 1 in 5 edge crossings at walking speed.

**Impact.** A sneaking player, for example someone building next to ramps, who steps off a ledge 1 to 1.8 blocks from a ramp is pulled back and loses their speed.

**Fix direction.** Use one radius for both decisions: base e2 on the controller's test (rampNear(box.inflate(speed + 1.5))), or make e2's reach at least 1.5 blocks plus the move.

**Skeptic.** I reproduced it with SkepticProbes.c4SneakEdge (probe/run2.log) on a real survival ServerPlayer.

Setup:
- Stone pillar with its top at y=4; feet at x=3.2999, so box minX is 2.9999.
- Sneak via ServerboundPlayerInputPacket(Input(...shift=true...)), then an onGround=true packet and a tick.
- Then Pos(feet + (0.3175, -0.016, 0)) with onGround=false.

Results:
- Full ramp cell 1.20 blocks from the box: the controller's test rampNear(box.inflate(0.3175+1.5)) is true, but RampCollision.near (inflate 1.0) is false. Log 'sneaker moved wrongly!'; corrected, server x moved 0.0000 of 0.3175.
- Ramp 0.20 blocks away: both tests are true; accepted, 0.3175 of 0.3175.

With vanilla's 0.05-block back-off steps (Player.java:862-911), the error exceeds 0.25 only when the start is within 0.0675 blocks of the edge. That is about 21% of edge crossings at the controller's 250 u/s. The client never backs off: its moves use the trust payload, and SurfController:109 hands back for sneaking only when no ramp is near.

The effect is a one-tick rubber-band, with velocity zeroed, for a sneaking player 1.0 to 1.8 blocks from a ramp. It is real but low-stakes: minor.

### C5: e3 changes vanilla knockback anywhere for ordinary sprint-jumping (0.612 blocks/tick on the jump tick)

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/mixin/LivingEntityMixin.java:38-43 (known.horizontalDistance() > 0.6 || near ramp, then setDeltaMovement(known)).

**Evidence.** (a) Vanilla speed measured with the game's own player tick. Server game test probeVanillaSprintJumpSpeed: a real ServerPlayer on stone, each tick setSprinting(true); zza=1; setJumping(true); doTick(). Result: 'peak move 0.612 blocks/tick, mean 0.355'. The peak is the jump tick: ground speed plus the 0.2 sprint boost. A Python model of travelInAir/jumpFromGround (worktree probe/vanilla_speed.py) agrees: stone 0.612, Speed II 0.675.

(b) e3 itself, in probeE3Knockback: a real ServerPlayer far from any ramp, with the server deltaMovement set to (0.182, 0.333, 0) (vanilla's value after its own jump detection) and setKnownMovement((k, 0.42, 0)), then hurtServer(mobAttack(zombie in front)). X velocity sent to the client:
- k 0.55: -0.309
- k 0.59: -0.309 (the vanilla value)
- k 0.61: -0.095
- k 0.612: -0.094
- k 0.675: -0.063

**Impact.** In ordinary PvP or PvE far from any ramp, a player hit head-on during their sprint-jump tick gets about 30% of vanilla's knockback. By the same formula, a hit from behind gives about 1.4 times vanilla. Knockback jumps discontinuously at 0.6 blocks/tick. The comment's premise, 'faster than any vanilla movement on foot', is false.

**Fix direction.** Key e3 on the player having surfed recently (a ramp near within the post-surf window, e.g. a per-player timestamp), not on a speed cut-off that vanilla movement reaches. Note that SurfClientTest's 'hit mid-hop' case (500 u/s = 0.635 blocks/tick, away from ramps) currently relies on the speed clause, so the replacement must still cover post-surf hops.

**Skeptic.** Vanilla speed. I wrote an independent model of 26.3 travelInAir and jumpFromGround (probe/sprint_jump.py). It uses the client's real W input of 0.98 from LocalPlayer.modifyInput, stone friction 0.6*0.91, a ground acceleration of 0.13 when sprinting, 0.026 in the air, +0.2 on a sprint jump, and a 10-tick jump delay. Steady sprint-jumping peaks at 0.6122 blocks/tick on the jump tick (Speed I 0.6438, Speed II 0.6754). The server stores that per-packet displacement as known movement (handlePlayerKnownMovement), so e3's 'horizontalDistance > 0.6' fires for plain vanilla sprint-jumping.

e3 itself, in SkepticProbes.c5E3Knockback (probe/run3.log):
- A real ServerPlayer with no ramp near: RampCollision.near is false.
- Server deltaMovement (0.182, 0.333, 0), setKnownMovement((k, 0.42, 0)), then a zombie mobAttack from +x on Normal difficulty.
- Resulting x velocity: k 0.55, 0.59 and 0.60 give -0.309 (vanilla); 0.6122 gives -0.094; 0.6754 gives -0.062.

This only happens on about 1 tick in 12 while sprint-jumping, away from ramps. It is a real side effect on vanilla PvP and PvE knockback, and an edge case: minor.

### C6: Other players see a surfer's legs running at full speed in mid-air (d1 applies to the local player only)

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/mixin/LivingEntityAnimationMixin.java:18-25 returns unless the entity is a LocalPlayer. Vanilla: RemotePlayer.tick (client decomp RemotePlayer.java:35-38) calls calculateEntityAnimation(false), then updateWalkAnimation (common LivingEntity.java:2634-2646), with target speed min(4*distance, 1).

**Evidence.** Code path from the 26.3 decomp: a RemotePlayer is not a LocalPlayer, so d1 returns at once and vanilla animates from the interpolated distance moved per tick. The walk target saturates at 0.25 blocks/tick, and surf speeds are 0.38-4.4 blocks/tick (300-3500 u/s), so the legs swing at the maximum every tick. In the surfer's own third-person view d1 settles the legs, so the two views disagree. No surf state is synced to other clients.

**Impact.** In multiplayer, every surfer appears to everyone else to be sprinting in mid-air.

**Fix direction.** Base the remote animation on something other clients know: a synced surfing flag (entity data set by the server near ramps), or the same rule applied to RemotePlayers near ramps or airborne while moving fast.

**Skeptic.** Code path checked in the 26.3 decomp:
- RemotePlayer.tick (client RemotePlayer.java:34-37) calls calculateEntityAnimation(false).
- d1 (LivingEntityAnimationMixin:20) returns at once for anything that is not a LocalPlayer.
- So vanilla's updateWalkAnimation applies: target min(4*distance, 1) (LivingEntity.java:2634-2646). It saturates at 0.25 blocks/tick, and surf speeds are 0.38 to 4.4 blocks/tick.

The surfer's own third-person view (d1) settles the legs, while other players see them swing at the maximum. The claim is accurate.

Two caveats:
- MODLOG already lists this under Wave B 'Known limits' ('other clients see a surfer's legs swing').
- Vanilla airborne sprint-jumpers also swing at the maximum.

So it is a known, purely cosmetic inconsistency with no effect on play: minor.

### C7: The re-simulation's lift region grows without limit with move length: one long packet costs ~34x vanilla (a DoS once player_movement_check is off)

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/movement/RampCollision.java:40-48: up = maxUpStep + horizontalDistance/2, and the whole region is scanned twice (rampNear, then BrushWorld.collect) before ExactCollide.resolve/lift.

**Evidence.** Server game test ProbeServerTests.probeLongMoveCost: a mock server player beside a ramp block, timing RampCollision.collide(p, (-L,0,0), MoverType.PLAYER) against vanilla's getEntityCollisions plus Entity.collideBoundingBox for the same move.
- L=10: 0.28 ms vs 0.085 ms
- L=22: 0.30 ms vs 0.059 ms
- L=100: 2.09 ms vs 0.27 ms
- L=300: 9.9 ms vs 0.36 ms
- L=1000: 29.1 ms vs 0.85 ms

**Impact.** With the default gamerule a packet moves at most about 10-22 blocks, which is harmless. With player_movement_check false (docs/dev/mc26-movement.md section 10 suggests it for surf servers, and it is the obvious workaround for C1), a modified client next to any ramp can send 1000-block moves 20 times a second and burn about 0.6 s of server time per second.

**Fix direction.** Cap the lift (e.g. step height plus what a hull can rise in one tick at max velocity, about 4.5 blocks) and fall back to vanilla collide for moves longer than the controller can produce (3500 u/s is 4.45 blocks/tick per axis).

**Skeptic.** The cause is real, and the impact is larger than the finding says. Measured with SkepticProbes.c7LongMoveCost (probe/run3.log, probe/run4.log).

RampCollision.collide beside a ramp, against vanilla's getEntityCollisions plus collideBoundingBox:
- 22 blocks: 0.24 ms vs 0.016 ms.
- 1000 blocks: 20-45 ms vs 0.28 ms.
- 2000 blocks: 72-126 ms vs 0.26-0.47 ms.

With player_movement_check false, one real Pos packet through handleMovePlayer took:
- 1000 blocks: 212-254 ms.
- 3000 blocks: 373-412 ms.
- 10000 blocks: 2.3-2.5 s.

No ramp is needed:
- With no ramp anywhere (collide returned null), rampNear's own scan of the lift region costs 12 ms, 114 ms and 1.26 s at 1000, 3000 and 10000 blocks.
- Full packets in that case cost 20 ms, 184 ms and 2.07 s.
- The region is about L * L/2 * 3 blocks, with no clamp to world height, so the cost is quadratic. The 3e7 position clamp allows stalls long enough to reach the watchdog.

Under the default gamerule, a packet's step is bounded to roughly 10-40 blocks (moved too quickly, budget 100 per packet with deltaPackets <= 5, expectedDist small), which costs 1 ms or less. So default servers are safe and 'minor' stands for the default config. If the advice in docs/dev/mc26-movement.md section 10 to set player_movement_check false (also the obvious C1 workaround) is followed, any client running the mod can stall the server for seconds with a single packet. That makes removing the advice part of the fix.

## Lens D: Blocks, Karambit and UX

**Checked and found sound:** All checks ran in worktree /Users/funk/code/sandbox/minecraft/.claude/worktrees/wf_926b46be-3ca-4 (production code untouched).

Probe tests are uncommitted under src/gametest:
- LensDProbeTests.java, LensDBlockProbes.java, LensDKarambitProbes.java (server game tests)
- LensDClientTest.java (-PclientTests=lensd), LensDClientTest2.java (-PclientTests=lensd2)
- lensd_area.snbt (an empty 48x24x48 structure); the classes are registered in src/gametest/resources/fabric.mod.json

Screenshots are in build/gametest/screenshots/ (0000-0017 lensd_*, 0000-0006 lensd2_*). I looked at every one, plus the main tree's last showcase and karambit shots. The baseline ./gradlew build passed (21 server game tests).

Checked and found sound:

Joining:
- Every one of the 588 cells of hand-built 5x8x3 ramps (both types, 4 facings) was broken and placed again through the item: each came back with the identical state.
- Valleys (ramps facing each other) and A-frames (back to back), each side built bottom-up from its foot, both types: 0 wrong cells.
- In the shift sweep, layouts in the same slices with |dy| <= 3 never made a sawtooth. Only side-by-side layouts and stacks 4-6 rows up or down did (D1).

Structure blocks: a hand-built 5x7x3 ramp saved with StructureTemplate.fillFromWorld and placed with all 4 rotations x 3 mirrors, both types. Every placed cell lies on one plane and all cells come back: HorizontalDirectionalBlock rotates and mirrors FACING, and the cut is cell-local.

Pistons: a piston pushes a ramp with its state kept (cut=5, facing=north), and the ramp renders mid-push through Fabric's moving-block mixin (0010).

Explosions: a power-4 explosion among ramps and smooth stone left 8 of 12 of each. The resistance is the same, and drops follow survives_explosion.

Rendering:
- Culling against glass and stone is correct (0005).
- AO at the foot of a wall looks right, with no seams or cracks (0006).
- The crack overlay and break particles use the ramp texture (0012, 0013).
- Dropped items, the item frame, third person, first person and hotbar icons look right (0007-0009).

Karambit:
- Extend works in every direction: 4 facings x both look directions put every cell on the plane, and the ramp length doubles.
- Ceiling placement hangs the module from y-5 to y-1.
- Two players' undo histories are independent: 448 blocks became 224 after one undo, with the other module intact.
- Module item data is 1.5 KB for the default, 1.2 KB for a copied 16x16x32 2:1 ramp, and 58 KB for the codec's worst case (256 states, 4096 runs), far below the 2 MB NBT network quota.
- The tooltip text is correct and every translation key exists. I saw no raw keys in the action bar, tooltips or toasts.
- Refusal particles appear, and undo leaves changed cells alone (existing tests and shots).
- The sprite in hand looks right.

Recipes: in survival, picking up smooth stone and iron unlocked all 5 recipes (the server recipe book contains surf_ramp, steep_surf_ramp, both stonecutting recipes and karambit). The crafting table's recipe book lists both ramps (0006_lensd2). Creative tab order is covered by the existing test.

FPS caveat: the numbers are from an M4 with other reviewers possibly running clients at the same time. Each comparison was taken back to back within one run.

### D1: Hand-placed ramps next to or stacked on another ramp of the same type and facing come out as a sawtooth, and the result depends on which side the other ramp is on

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/block/SurfRampBlock.java:32-36 (NEIGHBOURS order), :97-116 (placementState)

**Evidence.** Reproduced with a server game test that uses real item placement (probe in src/gametest/java/dev/afunk/surfcraft/gametest/LensDProbeTests.java, sideBySideOffsetRamps and shiftedRampSweep, run on an empty 48x24x48 structure).

Setup:
- Mock creative player (helper.makeMockPlayer, then GameType.CREATIVE.updatePlayerAbilities).
- For each cell: player.setYRot(facing.getOpposite().toYRot()); main hand = new ItemStack(ramp, 64); then player.getMainHandItem().useOn(new UseOnContext(player, MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))).
- Cells go in the repo's bottom-up order: seed first, then TestRamp.sorted by y, then -u, then w.
- A = new TestRamp(SURF_RAMP, EAST, abs(2,1,2), 4,5,2, 3,0), slices z=2,3.
- B = new TestRamp(SURF_RAMP, EAST, abs(3,1,4), 4,5,2, 3,0), slices z=4,5, with its foot one block further east.
- Build A, then B.

Result:
- B's partial cells sit on two slope planes (C = cut + p*U + q*y): 12 cells on A's plane and 10 on B's own.
- 12 of B's cells are off B's plane. The first is Cell[u=2,y=0,w=0]: cut 5, want 9 (full).
- The mirrored layout (A at z=14,15, south of B at z=12,13) gives B one plane and 0 wrong cells.

Sweep: both ramp types, all 4 facings, B = A shifted by du in [-3,3], dy in [-6,6], dw in {-2,0,+2} slices, touching but not overlapping, A built first, both bottom-up:
- 400 of 1328 layouts give B two or more planes.
- 376 of those are side by side (either side).
- 24 are stacked in the same slices. For example, a 2:1 ramp stacked exactly on an identical one (shift 0,5,0) has 6 wrong cells. A 5:4 ramp stacked and shifted back 1 or 2 columns has 4 and 8 wrong cells.

Screenshots, from a client test that builds the same layouts with placementState:
- build/gametest/screenshots/0000_lensd2_sawtooth_profile.png: B's end face has teeth sticking out of the slope.
- 0001_lensd2_clean_profile.png: the mirrored layout is a clean triangle.

Cause: placementState returns the first partial cell of the same type and facing, in BFS order, whose plane covers pos (c >= 1), no matter which ramp that cell belongs to. NEIGHBOURS is a stable sort of betweenClosed order (x fastest, then y, then z), so face neighbours are tried N, D, W, E, U, S. For B's cell (u=2,y=0,w=0), the north neighbour is A's foot (cut 5, continueCut gives 5). It wins over the east neighbour, B's own foot, which would give 10 (full). For the exact 2:1 stack, the D (down) neighbour (A's top cell) wins over E.

Breaking and re-placing the bad cell meets the same neighbour first, so it comes back wrong again.

**Impact.** Builders who put two ramps side by side (staggered lanes, or a wide ramp built in two halves) or stack one ramp on another get notched, sawtooth slopes that stop surfers. Re-placing the bad cells repeats the error. The mirrored layout works, so the behaviour looks random.

**Fix direction.** Choose the plane deterministically, and from the ramp being built:
- First, prefer the plane of the ramp block the player clicked against (BlockPlaceContext: clickedPos.relative(clickedFace.getOpposite())), when it has the same type and facing.
- Otherwise, prefer candidates in the new cell's own cross-section (same w) over candidates across w.
- Never let the N/S/E/W iteration order decide between two different planes. For example, collect every candidate plane at the nearest BFS depth and pick by a fixed rule.
- Keep the sweep as a regression test.

**Skeptic.** I re-ran this with my own probe that uses real item placement: SkepticDProbes d1SideBySide, d1SweepClassified and d1NamedCases, in /Users/funk/code/sandbox/minecraft/.claude/worktrees/wf_926b46be-3ca-9/src/gametest/java/dev/afunk/surfcraft/gametest/SkepticDProbes.java. Run them with ./gradlew runGameTest -PgameTestFilter='surfcraft-gametest:skeptic_dprobes_d1*'; my worktree's build.gradle adds the filter hook.

Exact repro:
- A = TestRamp(SURF_RAMP, EAST, abs(2,1,2), 4,5,2, 3,0); B = TestRamp(SURF_RAMP, EAST, abs(3,1,4), 4,5,2, 3,0).
- Build A, then B, each bottom-up through ItemStack.useOn.
- B: 12 cells on A's plane, 10 on its own. First wrong cell: Cell[u=2,y=0,w=0], cut 5, want 9.
- Mirrored (A south of B): 0 wrong.
- The sweep gives exactly 400 of 1328 layouts with B on 2 or more planes.
- Named cases match: 2:1 stacked (0,5,0) has 6 wrong cells; 5:4 (-1,5,0) has 4; 5:4 (-2,5,0) has 8.

Code path verified:
- BlockPos.betweenClosed (26.3) iterates x fastest, then y, then z, so the stable sort gives face neighbours in the order N, D, W, E, U, S.
- placementState returns the first partial cell of the same type and facing with continueCut >= 1, whichever ramp it belongs to.

Two corrections to scope:
1. Narrower than the title. All 48 touching layouts where B lies on the same plane as A came out exact, so joining, widening or stacking along one plane works. 'A wide ramp built in two halves' fails only if the halves are misaligned. The defect needs touching ramps of the same type and facing on different planes: 531 of 1280 such layouts have wrong B cells.
2. Worse than reported. The wrong cut spreads along B through its own N neighbours. A 10-slice B next to a 2-slice A has 6 wrong cells in every one of its 10 slices.

A one-block stepped descent (B = A shifted (0,-1,+2), A north) puts all 16 of B's cells on A's plane; the mirror (0,-1,-2) is exact. Re-placing a cell cannot fix it, because the same neighbour wins first. Major stands: a plausible hand build gives a whole-ramp sawtooth that depends on build direction.

### D2: Ramps are missing from minecraft:blocks_motion, so the MOTION_BLOCKING heightmap ignores them: rain falls through every ramp, it rains under hollow ramp roofs, and snow, ice and lightning reach the ground beneath

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/main/resources/data/minecraft/tags/block/ (the mod only adds mineable/pickaxe.json). Vanilla 26.3: levelgen/Heightmap.java:150; jar-common data/minecraft/tags/block/blocks_motion_no_leaves.json (lists minecraft:smooth_stone, #stairs, #slabs); client WeatherEffectRenderer.java:80; Level.java:953-963 (precipitationAt); ServerLevel.java:585-607 (tickPrecipitation); BlockBehaviour.java:1024 (isSuffocating)

**Evidence.** Server game test (LensDBlockProbes.rampsDoNotBlockMotion, skyAccess=true): stone floor at y=1, 5x5 roofs at y=7, measured 40 ticks later.
- MOTION_BLOCKING height minus the roof's y: ramp roof (cut p) -5, full-ramp-cell roof -5 (both the floor), smooth stone +1, smooth stone slab +1.
- roof.is(BlockTags.BLOCKS_MOTION): false for both ramps, true for smooth stone and the slab.
- isSuffocating: false for a full ramp cell, true for smooth stone.

Client test (superflat plains, /weather rain, then 120 ticks; LensDClientTest2):
- Under a floating default module and under a floating hollow ramp, rain streaks and splashes fall right at the camera (0002_lensd2_rain_under_module.png, 0004_lensd2_rain_under_hollow.png).
- Under a floating smooth stone slab layer the ground is dry (0003_lensd2_rain_under_slab.png).

Server state at the camera spots:
- Hollow ramp: isRainingAt=true, player isInWaterOrRain=true, sky light 15, MOTION_BLOCKING Y = ground.
- Default module: MOTION_BLOCKING Y = ground (isRainingAt false only because sky light is 12).
- Slab: MOTION_BLOCKING Y = slab + 1.

Cause: in 26.3, 'blocks motion' is a tag, no longer derived from the collision shape. The chain is MOTION_BLOCKING = is(#blocks_motion_in_heightmap), which includes #blocks_motion, which includes #blocks_motion_no_leaves, which lists smooth_stone, #stairs and #slabs. Smooth stone is in exactly two vanilla block tags: that one and mineable/pickaxe. The ramps copy smooth stone's properties but are only added to mineable/pickaxe.

Other consumers, by code path:
- tickPrecipitation puts snow layers on the ground under floating ramps and freezes water under them.
- findLightningTargetAround targets the ground under ramps.
- CAUSES_SUFFOCATION: full ramp cells neither suffocate nor block the view.
- ENTITIES_CAN_TELEPORT_TO, BLOCKS_FLUID_FLOW and BLOCKS_LAVA_FIRE_SPREAD also leave ramps out.

**Impact.** Whenever it rains, rain is drawn through every surf ramp, including onto and under the ramp a player is surfing. Players under a ramp roof get rained on. Snow piles up under floating ramps and never on them. Lightning strikes the ground beneath.

**Fix direction.** Add surfcraft:surf_ramp and surfcraft:steep_surf_ramp to data/minecraft/tags/block/blocks_motion_no_leaves.json, smooth stone's other vanilla tag. Add a game test that checks the MOTION_BLOCKING height over a ramp.

**Skeptic.** Code path verified in 26.3:
- Heightmap.java:150: MOTION_BLOCKING = is(#blocks_motion_in_heightmap) or a fluid. That tag includes #blocks_motion, which includes #blocks_motion_no_leaves (jar-common), which lists minecraft:smooth_stone. The ramps are tagged only mineable/pickaxe.
- Consumers: client WeatherEffectRenderer.java:80 takes the rain column's bottom from MOTION_BLOCKING. Level.precipitationAt (Level.java:962), ServerLevel tickPrecipitation (:586) and lightning targeting (:630) use it too.
- isSuffocating defaults to #causes_suffocation (= #blocks_motion) AND a full collision shape (BlockBehaviour.java:1024).

My probe (SkepticDProbes.d2MotionBlocking): plains via fillbiome (the region had to be under 32768 blocks), floor at y=1, 5x5 roofs at y=9, measured after 60 ticks.
- MOTION_BLOCKING top minus roof y: -7 (the floor) for both a full-cell and a cut-p ramp roof; +1 for smooth stone.
- precipitationAt under the cut-p ramp roof: RAIN (sky light 15). Under smooth stone: NONE.
- Under the full-cell ramp roof: NONE, but only because sky light is 12. The client renderer reads only the heightmap, so it still draws rain there.
- Ramps are not in #blocks_motion, and isSuffocating is false for a full ramp cell.

The reviewer's screenshots (0002/0004 vs 0003) show rain at the camera under the module and the hollow ramp, and dry ground under the slab.

Side notes: water does not destroy ramps (canHoldAnyFluid uses #washed_away_by_fluids). 'No snow on top' is harmless for surfing. The real visible defect is rain falling through and under ramps.

### D3: The Karambit preview flood-fills the whole connected ramp every client tick: 7.9 ms per tick on the render thread while the knife points at a big ramp

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/KarambitPreview.java:30-38; src/main/java/dev/afunk/surfcraft/karambit/ModulePlacer.java:117-121 (extend calls connectedRamp(..., Integer.MAX_VALUE)), :185-205 (26-neighbour BFS), :46 (MAX_RAMP_BLOCKS = 8192)

**Evidence.** Client game test (LensDClientTest.fps). Setup: Apple M4, 128[address omitted], vsync off, frame limit unlimited, inactivity limit MINIMIZED. The world has six 5:4 A-frames, 16 tall and 128 long. The player stands 3 blocks from the end of one (29,440 connected cells) with the crosshair on it.

Measurements:
- ModulePlacer.plan(client.level, client.player, knife, hit pos, face) timed on the client thread: 7.88 ms per call (mean of 40 calls after 20 warm-up calls).
- The plan's result is 'too_large', drawn as a red box on the clicked cell (0017_lensd_fps_knife.png).
- FPS, Minecraft's 1-second counter, 5 samples: empty hand 254, 184, 209, 218, 210; knife 78, 175, 204, 185, 189.
- Server-side game test (LensDKarambitProbes.previewCostOnAHugeRamp): the same call takes 4.6 to 5.7 ms on ramps of 7,820 and 10,580 cells, against 0.02 ms for free placement on the ground.

The flood fill touches 27 positions per found cell (getBlockState plus HashSet work) for up to 8,193 cells, every tick, even when the outcome is just 'too large'.

**Impact.** Holding the Karambit while looking at any large ramp adds an ~8 ms render-thread stall 20 times a second on an M4, and more on slower CPUs. That happens while building, and while surfing with the knife out as in CS:S. The result is a hitch every tick on high-refresh displays and about 15% fewer frames.

**Fix direction.** Extend only needs the ramp's end slice. Walk from the clicked cell along the length axis to the last slice, then read that slice's cross-section, which is bounded by the module size. Alternatively, cache the plan per clicked position, face, look direction along the axis and module until a block changes. The same change fixes D4.

**Skeptic.** Code path:
- KarambitPreview.tick runs on END_CLIENT_TICK (the client/render thread, 20 Hz) whenever the knife is held without sneaking and a block is targeted. It calls ModulePlacer.plan with no caching.
- On a ramp, extend() calls connectedRamp(..., Integer.MAX_VALUE): a 26-neighbour BFS with 27 getBlockState calls plus HashSet and BoundingBox work per found cell, up to 8,193 cells, even when the result is just 'too_large'.

Measured with SkepticDProbes.d3PlanCost: a 16-tall 5:4 A-frame, 40 warm-up and 60 timed plan() calls, two runs (ranges are run 2 to run 1).

| Ramp | ms per plan() |
|---|---|
| 920 cells | 0.55 to 0.73 |
| 3,680 cells | 2.0 to 2.9 |
| 7,820 cells | 4.4 to 6.5 |
| 10,580 cells (too_large) | 4.2 to 10.4 |
| Free placement on the ground | 0.011 to 0.014 |

This is consistent with the reviewer's 4.6 to 5.7 ms on the server and 7.9 ms on the client. I did not re-run the client FPS test, because this lens may not, and the reviewer's FPS samples are noisy. The per-tick stall alone is 8 to 20% of the client thread's time and drops frames on displays of 120 Hz or more and on slower CPUs.

### D4: Extend refuses surf-sized ramps: a 16-tall 5:4 A-frame cannot be extended past 40 blocks

- **Severity:** reviewer major, after skeptic **major** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/karambit/ModulePlacer.java:46 (MAX_RAMP_BLOCKS = 8192), :120-121 (extend: connectedRamp returns null and the click gets 'too_large')

**Evidence.** Server game test (LensDKarambitProbes.copyAndExtendSurfSizedRamps): a 5:4 A-frame 16 tall and 8 long (the repo's AFrame helper, 230 cells per slice). A creative mock player looking south copies it with ModulePlacer.copy, then repeatedly calls ModulePlacer.place on the same ramp. The extension is refused at length 40 (9,200 cells) with 'This ramp is too large'. The cap counts every connected ramp whose facing lies on the same axis, of either type, so a ramp touching other ramps reaches it sooner.

**Impact.** The Karambit's main workflow (copy, then extend) stops working on realistic surf ramps. A 16-block-tall ramp (630 Source units) can only reach 40 blocks (about 1,575 units) in length, while real surf ramps are thousands of units long. The builder has to switch to lining up free placements by hand.

**Fix direction.** As in D3: find and check only the end slice and drop the whole-ramp cell cap for extend. If a cap remains, base it on the slice size, not the whole connected ramp.

**Skeptic.** Reproduced with SkepticDProbes.d4ExtendCap:
- AFrame(SURF_RAMP, ridge, base, z0, 16, 8, false), then ModulePlacer.copy at o.offset(3,0,2) looking south: 'Copied module 26x16x8, 1840 blocks'.
- Repeated ModulePlacer.place on the same cell extends at lengths 8, 16, 24 and 32 (7,360 cells).
- At length 40 (9,200 cells) it refuses: 'This ramp is too large'.

Cause verified:
- extend() floods with an unlimited span and returns too_large once connectedRamp passes MAX_RAMP_BLOCKS = 8192.
- The count includes every connected ramp of either type whose facing lies on the same axis.

The maximum length is about 8192 divided by the cells per slice (230 for a 16-tall A-frame), so taller ramps hit the cap much sooner. The only workaround is free placement lined up by hand, which skips the join check.

### D5: noOcclusion lets light through ramps (hollow ramps cast no shadow) and makes every hidden face of full ramp cells render (about 26% lower FPS on a large field)

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/block/SurfBlocks.java:36 (ofFullCopy(SMOOTH_STONE).noOcclusion()); vanilla BlockBehaviour.java:514-529 (initCache), Block.java:296-307 (shouldRenderFace), lighting/LightEngine.java:66-67 (isEmptyShape)

**Evidence.** Light, server game test (LensDBlockProbes.skyLightUnderRamps, skyAccess=true): 8x8 roofs floating 6 blocks above a stone floor. Sky light on the floor, under the centre / under x=1:
- smooth stone: 11 / 13
- smooth stone slab layer: 11 / 13
- default module: 12 / 14
- hollow ramp (its partial cells only): 15 / 15, the same as the open floor (15)

Screenshot 0002_lensd_shadow.png: the slab layer and the module cast shadows on the grass; the hollow ramp casts none.

FPS, client test: M4, 128[address omitted], vsync off, 5 one-second samples per case:
- empty scene: 216, 180, 174, 160, 185 (mean 183)
- six 5:4 A-frames 16x128 (176,640 cells): 117, 118, 106, 129, 121 (mean 118)
- the same A-frames with stone in the full cells (AFrame stone=true): 144, 168, 187, 154, 141 (mean 159)

The frame-time overhead over the empty scene is 3.0 ms with ramp interiors against 0.8 ms with stone interiors.

Cause: noOcclusion sets canOcclude=false, which makes the occlusion shape empty.
- Rendering: Block.shouldRenderFace returns true whenever the neighbour's face occlusion shape is empty, so all six faces of every full ramp cell are meshed, as are stone faces against full ramp cells.
- Light: LightEngine.isEmptyShape treats the ramp as empty. Partial cells get lightDampening 0, because propagatesSkylightDown holds when the staircase is not a full cube. Full cells get 1 instead of 15.

**Impact.** Floating or hollow ramps are lit as if they were not there: no shadow, and together with D2 it rains on players under them. Solid ramp bodies are one light level too bright. Large ramp builds draw much more hidden geometry than the same shapes in stone, which costs FPS, most on weaker GPUs.

**Fix direction.** Drop noOcclusion. Give full cells a full-cube occlusion shape and partial cells the staircase (or an empty) occlusion shape, and override useShapeForLightOcclusion to return true, as StairBlock and SlabBlock do.

**Skeptic.** SkepticDProbes.d5LightAndCulling.

State values:
- Full ramp cell: canOcclude=false, isSolidRender=false, lightDampening=1, propagatesSkylightDown=false.
- Cut-p cell: lightDampening=0, propagatesSkylightDown=true.
- Smooth stone: lightDampening=15.

Sky light on the floor under the centre:
- Floating hollow ramp (the default module's partial cells only): 15, the same as open floor (15).
- Default module: 13.
- Smooth stone in the same cells: 12.

Culling: of the 576 faces of the default module's full cells that pass Block.shouldRenderFace, 408 face another full ramp cell. They can never be seen but are meshed.

Code path:
- BlockBehaviour.initCache:514 makes the occlusion shape empty when !canOcclude.
- Block.shouldRenderFace returns true for an empty neighbour occluder.
- Indigo culls only through Block.shouldRenderFace.
- The ramp mesh does set cull faces (RampModels.emit), so culling is lost only because of noOcclusion.

I did not re-run the FPS test. Nuance: partial cells pass sky light even without noOcclusion, because propagatesSkylightDown is true for any non-full shape and LightEngine.isEmptyShape also requires useShapeForLightOcclusion. The fix therefore needs both parts, as the finding states.

### D6: Copy silently truncates ramps larger than 32 on any axis, and extend then tells the player to copy the ramp they just copied

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/karambit/ModulePlacer.java:212-231 (copy calls connectedRamp(level, clicked, SurfModule.MAX_SIZE)), :195-196 (cells that would grow the box past the span are skipped), :132 (the mismatch message)

**Evidence.** Server game test (LensDKarambitProbes.copyAndExtendSurfSizedRamps): a 5:4 A-frame 24 tall and 6 long, which is 40 wide and holds 3,000 cells. ModulePlacer.copy from a cell 3 east of the ridge returns 'Copied module 32x24x6, 2808 blocks': 8 columns are dropped with no warning. ModulePlacer.plan on the same ramp then returns 'The module doesn't continue this ramp's end. Sneak + right-click to copy this ramp.'

**Impact.** Copying a two-sided 5:4 ramp taller than 20 blocks, a 2:1 ramp taller than 32, or any ramp longer than 32 across its cross-section gives a lopsided partial module with no warning. Placing it builds a malformed ramp, and extend sends the player back to a copy that cannot succeed.

**Fix direction.** When the connected ramp exceeds MAX_SIZE on any axis, refuse with a specific message (for example 'This ramp is 40 wide; the knife holds 32') instead of copying a truncated box. Alternatively, raise the limit.

**Skeptic.** SkepticDProbes.d6CopyTruncates:
- AFrame(SURF_RAMP, ..., 24, 6, false) is 40 wide with 3,000 ramp cells.
- ModulePlacer.copy at o.offset(3,0,2) succeeds with 'Copied module 32x24x6, 2808 blocks'.
- ModulePlacer.plan on the same ramp with that module returns surfcraft.karambit.mismatch: 'The module doesn't continue this ramp's end. Sneak + right-click to copy this ramp.'

Cause verified: connectedRamp(level, clicked, SurfModule.MAX_SIZE) silently skips any cell that would grow the box past 32, and copy() never reports the skip. The only hint is the '32' in the size message.

### D7: Adventure mode: the preview says 'fits' but clicks silently do nothing, and sneak + right-click on a ramp (copy) undoes the player's last placement

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/client/java/dev/afunk/surfcraft/client/KarambitPreview.java:30-37; src/main/java/dev/afunk/surfcraft/karambit/KarambitItem.java:72-77 (use, no mode check); vanilla ItemStack.java:362 (useOn returns PASS when !mayBuild), Minecraft.java:1839-1857 (the client then sends useItem)

**Evidence.** Server game test (LensDKarambitProbes.adventureSneakClickOnARampUndoes):
1. A creative mock player places the default module (224 blocks) with ItemStack.useOn.
2. GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities()).
3. ModulePlacer.plan on a stone block returns ok() = true.
4. A right-click is replayed in the client's call order: ItemStack.useOn, then ItemStack.use when useOn passed.

Results:
- Plain click: useOn PASS, use PASS. Nothing is placed and no message appears.
- Sneak + right-click on one of the module's ramp cells (meant as copy): useOn PASS, then use SUCCESS, which runs undo. The module goes from 224 blocks to 0.

Client test in adventure mode: the green preview box is drawn (0015_lensd_adventure_preview.png), and the click places 0 blocks.

**Impact.** On a surf server that keeps players in adventure mode and hands out the knife, players see a green 'fits' box wherever they look, but nothing happens when they click. A builder who playtests in adventure loses their last placements (up to 8, one per sneak-click) by sneak-clicking a ramp.

**Fix direction.** Gate the knife on player.mayBuild(). Skip the preview or show it red with the reason, and make use() refuse to undo when !mayBuild.

**Skeptic.** SkepticDProbes.d7Adventure uses an in-level ServerPlayer and the real ServerPlayerGameMode.useItemOn and useItem handlers.
- A creative click places the default module: 224 blocks.
- After GameType.ADVENTURE.updatePlayerAbilities, plan().ok() is true, so the preview is green.
- A plain click gives useItemOn=Pass, then useItem=Pass: nothing placed and no message.
- Sneak + click on a module ramp cell gives useItemOn=Pass, then useItem=Success, and the module goes from 224 blocks to 0 (undone).

Client side verified:
- ItemStack.useOn returns PASS when !mayBuild before Item.useOn runs (ItemStack.java:362), so KarambitItem's 'always SUCCESS on the client' guard never runs.
- MultiPlayerGameMode.performUseItemOn takes the same path.
- Minecraft.startUseItem then falls through to gameMode.useItem, and ServerPlayerGameMode.useItem has no mayBuild check.

### D8: 'No room' refusals for causes the preview cannot show: the player's own body when placing on a wall at eye level, and spawn protection on dedicated servers

- **Severity:** reviewer minor, after skeptic **minor** (partial)
- **Where:** src/main/java/dev/afunk/surfcraft/karambit/ModulePlacer.java:88-110 (plan, grow), :168-178 (check: isUnobstructed and mayInteract both count as 'blocked'); vanilla Level.mayInteract (always true; ClientLevel does not override it), ServerLevel.java:874, DedicatedServer.java:510-526

**Evidence.** Server game test (LensDKarambitProbes.wallAndCeilingPlacement): a real in-level ServerPlayer (helper.makeMockServerPlayerInLevel()), feet 2.5 blocks in front of a stone wall, looking north at it, holding a knife with the default module. ModulePlacer.plan on the wall's south face:
- block at feet+0: 'blocked', 2 cells
- feet+1: 1 cell
- feet+2 and feet+3: fits
- looking along the wall instead: fits

The box grows away from the clicked face, toward the player, by the module's length (8), centred across the anchor. With a reach of 5 blocks or less, the player's body is inside it.

Spawn protection, by code path: the client preview runs on ClientLevel, whose mayInteract is the inherited Level.mayInteract and always returns true, so the preview is green. On the server, ServerLevel.mayInteract checks DedicatedServer.isUnderSpawnProtection, which is true for a non-op within the radius when any ops exist. check() then adds every cell to 'blocked', and the player gets 'No room: N cells are not free' with red dust.

**Impact.** Clicking a wall at eye level never places a module. The red outlines are inside the player's own body, invisible in first person, and the message says 'No room'. Non-op players near spawn on a server see a green preview, then a 'No room' refusal for every cell.

**Fix direction.** Report distinct reasons ('You are in the way', 'Spawn protection') instead of 'No room', and have the preview evaluate them too. For walls, grow the box to the side away from the player when the player is inside it.

**Skeptic.** The body case reproduces, but it is a correct refusal with a vague message, and the spawn-protection claim is partly wrong.

Body case (SkepticDProbes.d8Wall: in-level ServerPlayer looking north at a wall from 1.5, 2.5 and 4 blocks):
- Wall blocks at feet+0 and feet+1 are blocked (1 to 4 cells, which are the player).
- feet+2 and feet+3 fit; looking along the wall fits.
- The preview does show this. ClientLevel's entity list contains the local player, so check() marks the same cells red on the client.
- The refusal itself is correct: an 8-long module sticking out of a wall toward a player within reach must overlap the player. Only the reason text is unclear.

Spawn protection:
- ServerGamePacketListenerImpl.handleUseItemOn (about line 1413) checks isUnderSpawnProtection on the clicked block first. It sends vanilla's build.spawn_protection message and never calls the knife.
- So 'No room' for every cell cannot happen from inside the radius.
- Only a click just outside the radius whose box reaches into it gets 'No room: k cells', for the k protected cells.
- The green preview is real in both cases: ClientLevel inherits Level.mayInteract, which returns true.

### D9: The block selection outline on ramps is the 8-step inscribed staircase, drawn as stripes over the smooth slope

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** src/main/java/dev/afunk/surfcraft/block/SurfRampBlock.java:122-141 (getShape returns the staircase, which is also the outline and raycast shape)

**Evidence.** Client screenshots: 0005_lensd2_outline.png (a single 5:4 cell targeted from 2.5 blocks), 0006_lensd_ao.png and 0012_lensd_mining.png. The targeted ramp shows 8 stacked horizontal box edges across the smooth wedge.

**Impact.** Every ramp the player looks at shows a striped staircase outline that does not match the smooth wedge being rendered, unlike vanilla stairs, whose outline traces their shape.

**Fix direction.** Draw a custom outline for ramps from RampCell.polygon() through a client block-outline render hook, and keep the staircase only for vanilla collision and raycasting.

**Skeptic.** Code path:
- The client's LevelExtractor.extractBlockOutline builds the outline from state.getShape(level, pos, CollisionContext.of(camera entity)).
- SurfRampBlock overrides only getShape, which returns the 8-slice staircase. Collision, interaction and visual shapes all fall back to it.
- Each slice's outer top edge is at (uAt(y1), y1), which lies exactly on the slope plane (p*u1 + q*y1 = cut).
- So 8 edge lines running along the ramp's length are drawn on the visible slope.

The reviewer's 0005_lensd2_outline.png shows 8 horizontal lines across a single targeted cell. This is cosmetic only.

## Lens E: Simplicity, quality and packaging

**Checked and found sound:** Everything below was examined and found sound.

**Code read in full**
- All production code: src/main (blocks, karambit, mixins, movement, physics) and src/client (controller, models, preview, HUD, mixins).
- All test code: 7 JUnit classes and 14 game-test classes plus their mixin.
- build.gradle, gradle.properties, both fabric.mod.json files, the mixin configs and every resource JSON.

**Build**
- `./gradlew build` passes: 44 JUnit (10 Kitsune skipped in the worktree) and 21 server game tests.
- With the main tree's Kitsune geometry linked and `--rerun`, all 44 JUnit pass.
- The server game tests ran 8 more times, each at a random vanilla start position (GameTestServer uses level.getRandom()); all 21 passed every time.
- `-Xlint:all` over all four source sets gives no warnings in project code.

**Jar** (build/libs/surfcraft-0.1.0.jar, 112 entries)
- Contains only dev/afunk/surfcraft main and client classes, assets, data, fabric.mod.json and the two mixin configs.
- No test classes, no gametest mod or structure, no decompiled code, no local-content.
- Timestamps are reproducible.
- The common classes reference nothing from net/minecraft/client or surfcraft/client (javap); the client mixin config is client-only.

**um publish check** (with --game = the extracted Minecraft 26.3 client jar)
- git-archive export: 0 failures, 4 warnings (3 absolute paths, no README).
- Unzipped jar: 0 failures, 1 warning (no README).
- No file is byte-identical to a game file, and there are no decompiler fingerprints.

**Third-party material**
- The 23 test recordings are byte-identical to the surf repo's fixtures.
- The 13 flat replays are exactly that repo's non-map, non-duck set (30 non-map recordings minus 17 that duck), so CssReferenceReplayTest's 'every flat recording that never ducks' is accurate.
- The ramp and Karambit textures are drawn by the repo's own scripts (tools/draw-*.py).
- The Gradle wrapper jar is the official 9.7.1.
- The universal-modder MIT license text is present.

**Comments and docs checked against code or decomp**
- 'anything but drowning' matches the no_impact tag, which is minecraft:drown.
- LogWatch is not redundant with ServerCorrectionsMixin: 'moved wrongly!' can be logged without a teleport when the old box already collided (ServerGamePacketListenerImpl:1185-1190).
- The copies of the cell-u, staircase, localPlanes, mesh and prism facing mappings all agree.
- Every translation key used in code exists in en_us.json.

**Player install facts**
- The 26.3 launcher bundles Java 25.
- Fabric loader 0.19.5 is the stable loader for 26.3; installer 1.1.2.
- The lowest Fabric API with every API SurfCraft references is 0.155.3+26.3.

**Test-only hooks in production** (SurfController recorder/log/Tick/counters, TickDriver.copy, Config.withSpeed, BrushWorld.collect with a null entity) are the replay oracle's seams. Deleting them would lose coverage, so they are not findings.

**Probes**
- The temporary build.gradle edit and the local-content link are reverted; git status is clean.
- Probe scripts are in build/probe (gitignored): fapi_check.py, xlint.gradle and the game-test logs.

### E1: No README or install path for players; the only documented way to play is the Gradle dev client

- **Severity:** reviewer major, after skeptic **none** (refuted)
- **Where:** Repo root (no README.md, LICENSE or CHANGELOG); 'Start SurfCraft.command'; src/main/resources/fabric.mod.json

**Evidence.** - `ls` of the repo root shows AGENTS.md, CLAUDE.md, MODLOG.md, THIRD_PARTY_NOTICES.md and 'Start SurfCraft.command'. There is no README, LICENSE or CHANGELOG.
- `um publish check` on a `git archive HEAD` export gives 179 files, 0 failures, 4 warnings, one of them 'WARN no README (install steps, requirements, credits)'. On the unzipped surfcraft-0.1.0.jar it gives 79 files, 0 failures and the same warning.
- The only play path is 'Start SurfCraft.command', which runs `exec ./gradlew runClient` (dev client, offline account, needs JDK 25 and Gradle).
- Facts the README must state, all measured:
  - The 26.3 launcher manifest has javaVersion {component: java-runtime-epsilon, majorVersion: 25}, so the launcher bundles Java 25.
  - meta.fabricmc.net lists loader 0.19.5 as the stable loader for 26.3, and installer 1.1.2 as the current stable installer.
  - The lowest Fabric API for 26.3 that has every API SurfCraft references is 0.155.3+26.3 (see E4). The mod is built and tested with 0.161.0+26.3.
  - The mod must be on the server and on every client (MODLOG Intake: 'registry sync refuses mismatched clients').
- The project's own .claude/skills/publish-mod/SKILL.md §3 requires a README with requirements, install/uninstall/troubleshooting, multiplayer compatibility, credits, license and AI disclosure.

**Impact.** A player with the official launcher gets no instructions. They have to work out on their own that they need the Fabric Installer for 26.3 and Fabric API, and that the server and every client need the jar. A server owner isn't told that every player needs the mod. There is no release artifact: the jar only exists after `./gradlew build`.

**Fix direction.** Add a README.md with:
- What the mod adds, with a screenshot or video.
- Requirements: Minecraft Java 26.3, Fabric Loader >=0.19.5, Fabric API >=0.161.0+26.3. Java 25 comes with the launcher.
- Client install: Fabric Installer 1.1.2 → Client → 26.3 → loader 0.19.5 → Install. Put fabric-api and surfcraft-0.1.0.jar in the mods folder (Windows %APPDATA%\.minecraft\mods, macOS ~/Library/Application Support/minecraft/mods, Linux ~/.minecraft/mods). Launch the fabric-loader-26.3 profile.
- Server install: Fabric server launcher for 26.3 with the same two jars. Every player needs the mod.
- Recipes and Karambit controls.
- Surfing: hold A/D into the slope; the speedometer shows speed.
- Limits: no duck; vanilla movement away from ramps.
- Build from source: JDK 25, `./gradlew build`, jar in build/libs.
- Credits and notices, license, AI disclosure.
Attach the jar to a release.

**Skeptic.** The reviewer worked at 5312fcc (the other lens worktrees are at 5312fcc). Commit 19438f2 on main ('README draft and a real mod icon') then added README.md. Its 'In the official launcher' section says: install Fabric Loader 0.19.5+ for 26.3; put Fabric API 0.161.0+26.3 and surfcraft-<version>.jar (from ./gradlew build, build/libs) in mods; a server needs both jars and players need the mod to join. It also covers recipes, Karambit controls, surfing, and credits with an AI disclosure. I re-ran the reviewer's measurement at HEAD 16d501c (`git archive HEAD | tar -x -C build/probe/export`, then `.tools/venv/bin/um publish check build/probe/export`): 182 files, 0 failures, 4 warnings. All four are 'absolute user path' (THIRD_PARTY_NOTICES.md, README.md, MODLOG.md, AGENTS.md); the 'no README' warning is gone. What is still missing is publish-time polish: no LICENSE, uninstall or troubleshooting section; no prebuilt jar (the README says to build it); Java 25 for building is mentioned only in the macOS quick start. Per the publish-mod skill, publishing is the user's call. E1's claim that 0.155.3 is the lowest usable Fabric API is also inaccurate (see E4).

### E2: THIRD_PARTY_NOTICES and fabric.mod.json leave out the physics core's Source SDK provenance that the upstream surf repo documents

- **Severity:** reviewer major, after skeptic **minor** (partial)
- **Where:** THIRD_PARTY_NOTICES.md:9-11; src/main/resources/fabric.mod.json (description, license); src/main/java/dev/afunk/surfcraft/physics/{SourceMovement,SourceMove,SourceHull,RampBrushes,TickDriver}.java comments

**Evidence.** What SurfCraft says:
- THIRD_PARTY_NOTICES.md:9: 'The CS:S movement port follows `/Users/funk/code/sandbox/surf` (same owner) ... No game files, map geometry or decompiled code are included.'
- fabric.mod.json: description '...CS:S air-strafe and ramp-clip movement ported from the engine.' and "license": "All-Rights-Reserved".
- There is no LICENSE file in the repo or the jar.

The code says it is a port of that repo:
- SourceMovement: 'ported from the surf repo's SourcePlayer.move'.
- SourceMove: 'the surf repo's source-move.ts ... TryPlayerMove'.
- SourceHull: 'CM_ClipBoxToBrush, ported from the surf repo's traceSourceHull'.
- RampBrushes: 'CM_ClipBoxToBrush, ported exactly'.
- The comments also use SDK names: CheckParameters, StartGravity/FinishGravity, CheckJumpButton, CategorizePosition, CheckStuck, m_flStamina.

What the upstream documents:
- /Users/funk/code/sandbox/surf/THIRD_PARTY_NOTICES.md:33: 'The independent TypeScript player sliding/step implementation follows the publicly documented movement structure in Valve's Source SDK game movement (gamemovement.cpp)'.
- Same file, :47: 'The SDK was consulted; this project is not represented as clean-room work. Public release remains gated on provenance, permissions and technical completion.'
- surf/docs/LICENSING.md: 'must not be described as a clean-room implementation ... Before public release, review movement, collision ... for provenance ... Do not relicense third-party code by adding MIT or another project license over it.'
- surf/LICENSES/README.md keeps Valve-Source-SDK-2013.txt and thirdpartylegalnotices 'because public SDK game code ... informed this implementation'.
- That SDK license allows distributing modifications only if they are free of charge and include its LICENSE, thirdpartylegalnotices.txt and copyright notice.

`um publish check` also flags the absolute user path in THIRD_PARTY_NOTICES.md, AGENTS.md:47 and MODLOG.md:12.

**Impact.** Published as is, the jar ships SDK-informed movement and trace code under an 'All-Rights-Reserved' claim and 'ported from the engine' wording. It carries none of the provenance notes or license texts that its own upstream says it needs, and the upstream gates release on exactly this review. The notice also points readers to a private local path.

**Fix direction.** This needs a decision from the user. At a minimum:
- Carry the upstream's provenance statement into THIRD_PARTY_NOTICES, naming the surf repo by name or URL instead of a local path.
- Wherever SDK-derived portions stay, include the Source 1 SDK LICENSE and thirdpartylegalnotices.txt, also in the jar (for example `jar { from('LICENSES') }`).
- Reword the description to 'reimplemented and checked against recordings of a CS:S server'.
- Choose the license only after the provenance review.

**Skeptic.** The facts check out. The upstream quotes match verbatim: surf THIRD_PARTY_NOTICES.md:33 and :47, docs/LICENSING.md:9 and :11, LICENSES/README.md:7. The summary of the Source 1 SDK LICENSE conditions is accurate (free of charge; include LICENSE and thirdpartylegalnotices; include the copyright notice). On the SurfCraft side, THIRD_PARTY_NOTICES.md:8 cites only a local path and never mentions the SDK. fabric.mod.json:6, and now README.md:4, say 'ported from the engine', which claims more derivation than upstream's own wording ('independent ... follows the publicly documented movement structure', 'not the original game binary'). SDK names appear in comments (SourceMovement:19,59,65,126; TickDriver:43; SourceMove:7,41; SourceHull:14-19). README.md:58 adds another absolute path. The severity is wrong, though: nothing is published (no release; the README is a 'draft') and nothing changes in play or on servers. Two parts of the impact are overstated. Upstream declares its own code UNLICENSED, the same stance as SurfCraft's All-Rights-Reserved. Upstream also calls the SDK texts context ('not a blanket license'), not something every port must ship. This is a pre-release provenance gate that needs the user's decision, so it is minor under the rubric, not major.

### E3: local-content is not a declared test input, so the Kitsune ramp/wall replays stay silently skipped after the geometry is extracted

- **Severity:** reviewer major, after skeptic **minor** (partial)
- **Where:** build.gradle `test {}` block (lines 70-72); src/test/java/dev/afunk/surfcraft/physics/KitsuneReplayTest.java:30,48 (relative Path + assumeTrue)

**Evidence.** Measured in this worktree:
1. `./gradlew build` with no local-content: KitsuneReplayTest has tests="10" skipped="10".
2. `mkdir local-content && ln -s /Users/funk/code/sandbox/minecraft/local-content/css-reference local-content/css-reference`, then `./gradlew test` prints '> Task :test UP-TO-DATE', and build/test-results still show tests="10" skipped="10".
3. `./gradlew test --rerun` runs all 10, 0 skipped, all pass.

The fix was verified by temporarily adding one line to `test {}` (since reverted, git status clean):
`inputs.files(fileTree("local-content/css-reference")).withPropertyName("cssReference").withPathSensitivity(PathSensitivity.RELATIVE)`
- Run without geometry: 10 skipped.
- After linking the geometry: :test re-ran, 0 skipped.
- Unchanged: UP-TO-DATE.

**Impact.** The only replays of ramp and wall collision against real CS:S data are the ones that silently don't run. Gradle reuses the stale skipped result after the geometry is extracted, and after any re-extraction. Re-extraction is the next step in MODLOG ('export compiled hulls in tools/extract-kitsune-brushes.mjs'), so `./gradlew build` would report success without checking the new geometry. Skipped tests don't fail the build, so nothing signals it. Worktrees always skip them too.

**Fix direction.** Add that `inputs.files(fileTree("local-content/css-reference"))...` line to the `test {}` block in build.gradle. Optionally note in AGENTS.md that worktrees skip these replays, and that the main tree runs them after a merge.

**Skeptic.** Reproduced at 16d501c (JDK 25):
1. Without local-content, `./gradlew test` gives TEST-...KitsuneReplayTest.xml tests=10 skipped=10.
2. After `mkdir local-content && ln -s /Users/funk/code/sandbox/minecraft/local-content/css-reference local-content/css-reference`, `./gradlew test` prints ':test UP-TO-DATE' and the XML is unchanged (skipped=10, old timestamp).
3. `./gradlew test --rerun` runs all 10: skipped=0, all pass.
4. It goes further than reported. In a private copy of the JSON I moved the ramp slope plane (brushes[0].planes[6][3] += 5). A plain `./gradlew test` prints ':test UP-TO-DATE, BUILD SUCCESSFUL'; `--rerun` fails 4 ramp replays.
5. I temporarily added the proposed line to `test {}`: `inputs.files(fileTree("local-content/css-reference")).withPropertyName("cssReference").withPathSensitivity(PathSensitivity.RELATIVE)`. With the moved plane, :test fails 4; after restoring, it re-runs and passes; unchanged, it is UP-TO-DATE. Reverted; git is clean.

Severity is minor, not major. This only affects development and tests, never players or servers. Any change to main or test code changes the test classpath, so in the main tree (which has the geometry) the replays re-run; MODLOG's merged-main build reports 0 skipped. A physics regression therefore cannot ship this way. Only geometry-only changes (re-extraction) stay stale until the next code change. Worktrees skip these replays by design (AGENTS.md says so), and the fix doesn't change that.

### E4: fabric.mod.json: placeholder 'Mod ID' icon, no contact links, and an unbounded `fabric-api: "*"` that accepts Fabric API builds missing the APIs SurfCraft uses

- **Severity:** reviewer minor, after skeptic **minor** (partial)
- **Where:** src/main/resources/fabric.mod.json (depends, no contact); src/main/resources/assets/surfcraft/icon.png

**Evidence.** - icon.png is a 128x128 1-bit image, sha256 e7681741df50ccd1fa3c35e7e4d1db63d7122f3356f2d79f86c09180b3369ec0. Viewed, it is the Fabric example mod's handwritten 'Mod ID'. THIRD_PARTY_NOTICES itself calls it a placeholder.
- There is no "contact" block (homepage, sources, issues).
- Probe for `fabric-api: "*"`:
  - Method: for each version, download https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/<v>/fabric-api-<v>.jar and read depends.minecraft from its fabric.mod.json. Then, across all nested META-INF/jars/*.jar, check that every Fabric API class SurfCraft's jar references (`javap -v` of build/libs/surfcraft-0.1.0.jar, grep net/fabricmc/fabric/api) exists and contains the member name and descriptor.
  - Result: 0.155.1+26.3 and 0.155.2+26.3 (built for 26.3-snapshot-4) declare "minecraft": "~26.3-", so Loader accepts them on 26.3. They lack ModelLoadingPlugin, BlockStateResolver, UnbakedModelDeserializer, Renderer, MutableMesh, QuadEmitter, MutableQuadView and MeshQuadCollection. Every build from 0.155.3+26.3 to 0.161.0+26.3 has everything.
- Code path with such a build: Loader resolves `*`. Then SurfCraftClient.onInitializeClient → RampModels.register → ModelLoadingPlugin fails to resolve with NoClassDefFoundError. Loader reports it as an entrypoint failure and the game crashes ('Could not execute entrypoint stage client').

**Impact.** Mod-list UIs such as Mod Menu show 'Mod ID' as SurfCraft's icon, and there are no links to a homepage or issue tracker. A player whose Fabric API is old enough gets a startup crash instead of Loader's clear 'requires fabric-api' screen.

**Fix direction.** Set `"fabric-api": ">=0.161.0"` (the version it is built and tested with; 0.155.3 is the minimum that has every API it uses). Add `"contact": {"homepage", "sources", "issues"}`. Draw a real 128x128 icon, for example with a script like tools/draw-ramp-textures.py, and drop the placeholder line from THIRD_PARTY_NOTICES.

**Skeptic.** The headline part is already fixed on main. 19438f2 replaced icon.png: it is now 128x128 RGBA, sha256 007a5a06..., drawn by tools/draw-icon.py (I viewed it: a ramp plus the Karambit), and the placeholder line in THIRD_PARTY_NOTICES is gone. The missing `contact` block is true but trivial. On `fabric-api: "*"`, I downloaded the 26.3 builds into build/probe. Every build from 0.153.1 to 0.161.0 declares minecraft "~26.3-". `javap -v` of build/libs/surfcraft-0.1.0.jar gives 17 Fabric API classes and 22 member refs. Matching them by name and descriptor against the nested jars: only 0.155.1 and 0.155.2 lack them (9 classes: ModelLoadingPlugin, BlockStateResolver, UnbakedModelDeserializer, Renderer, Mesh, MutableMesh, MutableQuadView, QuadEmitter, MeshQuadCollection). 0.153.1, 0.154.3, 0.155.0, 0.155.3 and 0.161.0 have every member. So the reviewer's 'old enough' and '0.155.3 is the lowest' are wrong: only those two snapshot-era builds fail. The crash path is real for them, since RampModels.register is the first call in SurfCraftClient.onInitializeClient. The official FabricMC/fabric-example-mod (default branch 26.3) also uses "fabric-api": "*". This is an edge case: minor.

### E5: Production code has a dead method, a method only the game tests use, and duplicated cell-u and module-index logic

- **Severity:** reviewer minor, after skeptic **minor** (confirmed)
- **Where:** physics/SourceMovement.java:36-40; block/SurfRampBlock.java:76-82; movement/BrushWorld.java:86,146-151; physics/RampBrushes.java:36-55,111-113; karambit/SurfModule.java:52-54,97; karambit/ModulePlacer.java:225; physics/SourceMove.java:10-11

**Evidence.** From grep over src/main, src/client, src/test, src/gametest, tools and docs:
- `hitWall`: only its declaration (SourceMovement:37). TickDriver.tick computes wall/ceiling from core.contacts itself.
- `worldPlanes`: the declaration plus TestRamp.java:76,92 (game tests) only. The same 'local plane plus block offset' is written inline in BrushWorld.slopePlane:154-157, KarambitGameTests:265-266 and RampCellTest:45-46.
- BrushWorld.slopeKey:147-151 builds `List.of(fx, fz, p, q, cut + p*u + q*y)` with its own copy of the facing→u convention. That is exactly RampBrushes.Placed.u() + plane() and the private RampBrushes.Slope record, rewritten because those are package-private. The copies currently agree.
- The module index `(y * width + x) * length + z` appears 3 times: SurfModule.index:53, SurfModule.twoSided:97 and ModulePlacer.copy:225 (inline `cells[(c.getY() * width + c.getX()) * length + c.getZ()]`).
- SourceMove.Trace is a member-less interface extending BiFunction<V3,V3,HullTrace>.
- `-Xlint:all` over all four source sets (via an init script) reports no warnings in project code.

**Impact.** No player-visible effect. The cost is dead code and copies that must change together: a change to the u convention or the module layout in one place would silently break the stone-bevel key or copied modules.

**Fix direction.** - Delete SourceMovement.hitWall().
- Delete SurfRampBlock.worldPlanes. TestRamp takes `SurfRampBlock.cell(state).localPlanes(f.getStepX(), f.getStepZ())[6]` plus the block offset, as KarambitGameTests does, and drops its 'neither vertical nor horizontal' filter.
- Make RampBrushes.Slope public, add `Placed.slope()`, and key BrushWorld's `slopes` map by it. This deletes slopeKey and RampBrushes.slope(...).
- Make `SurfModule.index(width, length, x, y, z)` static and use it in copy() and twoSided().
- Optional: use BiFunction directly instead of SourceMove.Trace.

**Skeptic.** Every claim checked by grep over src/main, src/client, src/test, src/gametest, tools and docs:
- hitWall (SourceMovement:36-40) has no callers; TickDriver:77-80 classifies contacts itself.
- worldPlanes (SurfRampBlock:76-82) is used only by TestRamp:76,92. The suggested replacement is safe: RampCell.localPlanes adds the slope as plane [6] exactly when !full(), and TestRamp skips full cells (TestRamp:90).
- BrushWorld.slopeKey:147-151 repeats RampBrushes.Placed.u() and plane() (package-private in physics) plus the private Slope record. SurfRampBlock.cellU:66-73 and RampCellTest:42 are further copies of the same facing-to-u convention. All copies agree today.
- The module index `(y*width+x)*length+z` appears at SurfModule:53, SurfModule:97 and ModulePlacer:225.
- SourceMove.Trace (:10-11) is a member-less BiFunction alias.
None of this is visible to players. It is a valid simplicity finding.

### E6: Test code duplication, an unused test field, and a stale doc line

- **Severity:** reviewer minor, after skeptic **minor** (partial)
- **Where:** src/gametest: ClientTests.java:8-9, RampShowcaseClientTest.java:31-32, KarambitClientTest.java:33; RampGameTests.java:50, RampShowcaseClientTest.java:68, KarambitClientTest.java:149, KarambitGameTests.java:46; Surfer.java:27,123; SurfMovementGameTests.java:114-121; AFrame.java:20-22; TestRamp.java:29-31; docs/dev/mc26-movement.md:5

**Evidence.** - RampShowcaseClientTest and KarambitClientTest each declare `SCREENSHOTS = Path.of(System.getProperty("surfcraft.screenshots", "screenshots"))` with the same javadoc as ClientTests.OUT.
- `Comparator.comparingInt(Cell::y).thenComparingInt(c -> -c.u()).thenComparingInt(Cell::w)` is written 4 times.
- Surfer.Sample.onGround is filled (Surfer:123) but never read.
- SurfMovementGameTests.brushWorldBevelsStoneUnderTheSlope:114-121 repeats AFrame.build's east half verbatim (plane 24 = q*height 6, stone).
- AFrame (`plane - p*u - q*y`) and TestRamp.planeCut re-derive RampCell.continueCut, which SurfModule.twoSided, RampSurfTest.cells and KarambitGameTests already use.
- docs/dev/mc26-movement.md:5 says '~/minecraft-26.3-decomp is a symlink to it', but `ls /Users/funk/minecraft-26.3-decomp` gives 'No such file or directory'.
- Not mergeable: TestRamp, AFrame and the Karambit default module (SurfModule.twoSided) anchor the plane differently.
  - TestRamp: a seed cell with cut p.
  - AFrame: the ridge top, C = q*height.
  - twoSided: the outer toe with cut p.
  - Example: the 5:4 AFrame of height 3 has C = 12. TestRamp would need 5 + 5a + 4b = 12, which has no solution with a, b >= 0. Its outer toe cell has cut 2, not p = 5.
  - They also do different jobs: TestRamp goes through placementState, AFrame sets cuts directly for 320-block ramps, and twoSided is the shipped default.

**Impact.** No effect on players. Edits must be repeated in 2-4 places, and the doc points to a path that doesn't exist (and the project rules forbid files in ~/).

**Fix direction.** - Delete both SCREENSHOTS constants and use ClientTests.OUT.
- Replace the 4 comparators with one `TestRamp.BOTTOM_UP`.
- Drop Sample.onGround.
- Have AFrame and TestRamp call RampCell.continueCut.
- Delete the symlink clause from mc26-movement.md.
- Keep TestRamp, AFrame and twoSided separate.
- Leave the bevel test's inline loop: AFrame's west half would spill out of the default 8-wide structure.

**Skeptic.** Most facts check out:
- SCREENSHOTS is copied at RampShowcaseClientTest:32 and KarambitClientTest:33 from ClientTests.OUT:9.
- The bottom-up comparator appears 4 times: RampGameTests:50, RampShowcaseClientTest:68, KarambitClientTest:149, KarambitGameTests:46.
- Surfer.Sample.onGround is written (Surfer:123,129) but never read, and no Sample is ever printed whole, so toString doesn't use it either.
- SurfMovementGameTests:114-121 equals AFrame.build's east half.
- docs/dev/mc26-movement.md:5 names a ~/minecraft-26.3-decomp symlink; `ls` says no such file.
One sub-claim is wrong, and its fix would weaken a test. TestRamp.planeCut re-derives the plane formula on purpose: it is the independent oracle that TestRamp.place checks block.placementState() against, and placementState is built on RampCell.continueCut (SurfRampBlock:109). If planeCut called continueCut, the joining tests would compare continueCut with itself, and a formula bug could no longer fail them. Keep TestRamp's own formula (AFrame's makes no difference either way). The rest is trivial test cleanup.

### E7: Gradle wrapper does not verify the distribution it downloads

- **Severity:** reviewer minor, after skeptic **none** (refuted)
- **Where:** gradle/wrapper/gradle-wrapper.properties

**Evidence.** - gradle-wrapper.properties has no distributionSha256Sum.
- The checked-in gradle-wrapper.jar has sha256 7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d, which equals the official gradle-9.7.1-wrapper.jar.sha256, so the jar itself is fine.
- The official https://services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256 is acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a.

**Impact.** On a fresh machine, a corrupted or tampered Gradle distribution would not be detected.

**Fix direction.** Add `distributionSha256Sum=acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a` to gradle-wrapper.properties.

**Skeptic.** The facts are correct. There is no distributionSha256Sum. The checked-in wrapper jar's sha256 7a9ce74c... matches services.gradle.org gradle-9.7.1-wrapper.jar.sha256, and the distribution's official sha256 is acd53f1e... (both fetched with curl -L). It is still not a defect. SurfCraft's gradle-wrapper.properties is byte-identical (empty diff) to the official FabricMC/fabric-example-mod 26.3 branch, which is Gradle's default `gradle wrapper` output. The download uses HTTPS with validateDistributionUrl=true. TLS catches corruption in transit, and a truncated zip has no central directory, so it fails to open. 'A corrupted distribution would not be detected' therefore does not hold. Only a compromised services.gradle.org, its CDN, or a TLS man-in-the-middle would get through. Players and server owners never run Gradle; they install the jar. Adding the checksum is optional hardening, not a defect anyone would hit.
