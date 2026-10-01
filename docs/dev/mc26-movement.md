# Minecraft 26.3 player movement: client pipeline, server validation, SurfCraft hook points

Research notes for replacing the local player's movement with the CS:S surf controller near ramps.
Everything here was read from the decompiled 26.3 sources (Mojang names) in `local-content/decomp/`
(`~/minecraft-26.3-decomp` is a symlink to it): `common/`, `client/`, and Fabric API 0.161.0+26.3 in
`fabric-api/`. File paths are relative to `common/` or `client/`; line numbers are from that decomp.
Method descriptors and call-site owners in section 9 were checked with `javap` against the jars in
`.gradle/loom-cache/minecraftMaven/`. Behaviour is paraphrased; no decompiled bodies are copied.

Class chain: `Entity` > `LivingEntity` > `Avatar` > `Player` > `AbstractClientPlayer` > `LocalPlayer`
(and `RemotePlayer`); `Player` > `ServerPlayer`. Units: blocks, ticks (50 ms); "b/t" = blocks per tick.
Surf top speed 3500 u/s = 88.9 m/s = **4.445 b/t** (39.37 u per block).

## 0. The short version

- The local player moves once per 50 ms client tick, inside `ClientLevel.tickEntities()`:
  `commonTick()` saves old positions, then `LocalPlayer.tick()` > ... > `LivingEntity.aiStep()` > jump
  logic > `Player.travel()` > `Entity.move()` > `Entity.collide()`. Afterwards, at most one position
  packet per tick is sent (`LocalPlayer.sendChanges()`), followed by `ServerboundClientTickEndPacket`.
- Mouse look is applied per frame, outside the tick, to both `yRot` and `yRotO`. At travel time
  `yRotO == yRot`.
- The server re-simulates every position packet with `player.move(MoverType.PLAYER, target - lastGood)`.
  It rejects (teleports back) when the re-simulated end is more than 0.25 blocks from the target
  **horizontally** (vertical error is never checked: the guard on `yDist` is always true), or when the
  new box overlaps a collision shape the old box did not.
- "Moved too quickly" allows 100 squared blocks per packet, counted from the start of the server tick:
  one 4.445-block packet passes; at top speed, six or more packets in one server tick fail. The
  singleplayer owner is exempt.
- `ServerPlayer` does run vanilla `travel()` (zero input) every server tick in `doTick()`, but the
  position is snapped back afterwards. Its `deltaMovement` and collision flags survive.
- Server fall damage is driven by the client's `onGround` flag and each packet's y delta through
  `doCheckFallDamage()`. `fallDistance` is a `double`.
- Hook points: `LocalPlayer.applyInput()` TAIL (input, suppress vanilla jump), `Player.travel()` HEAD
  (run the controller), `Entity.collide()` HEAD (exact collision, both sides),
  `LivingEntity.checkFallDamage()` HEAD (ramp contact resets fall distance, both sides). Section 9.

## 1. Client tick pipeline for `LocalPlayer`

### 1.1 Frame and tick order

`Minecraft.runTick(boolean)` (`client/.../Minecraft.java:1167`), each frame:

1. `packetProcessor.processQueuedPackets()`, `runAllTasks()`: every server packet that arrived
   (teleports, motion, abilities) is applied here, before this frame's ticks.
2. Up to `min(10, ticksToDo)` calls of `Minecraft.tick()` (20 Hz from `DeltaTracker`).
3. `mouseHandler.handleAccumulatedMovement()` (line 1251): mouse look, once per frame.
4. `renderFrame(...)`: everything is interpolated with the partial tick.

`Minecraft.tick()` (line 1874), in order (only the parts that matter here):

1. `gameMode.tick()`, `pick(1.0F)`, `gui.tick()`, `handleKeybinds()` (perspective, hotbar; no movement).
2. `gameRenderer.tick()`.
3. `level.tickEntities()` (1912): `ClientLevel.tickEntities()` (`ClientLevel.java:468`) calls
   `tickNonPassenger(entity)` (484) for every ticking, non-passenger entity: `entity.commonTick()` then
   `entity.tick()`. Passengers get `commonTick()` then `rideTick()` (495).
4. `level.tickBlockEntities()`.
5. `player.sendChanges()` (1917): movement packets (1.13).
6. `level.tick(...)`, particles.
7. `connection.send(ServerboundClientTickEndPacket.INSTANCE)` (1964), unless paused.

### 1.2 Inside the local player's tick

1. `Entity.commonTick()` (`Entity.java:523`, final): `invulnerableTime--`; `setOldPosAndRot()`, which
   sets `xo = xOld = x` (same for y, z), `yRotO = yRot` and `xRotO = xRot`; on the client,
   `getInterpolation().interpolate()`; `tickCount++`.
2. `LocalPlayer.tick()` (234) runs only when `connection.hasClientLoaded()`. It calls
   `AbstractClientPlayer.tick()`: `clientAvatarState.tick(position, deltaMovement)` (sets `walkDistO`,
   cape), then `Player.tick()` (`Player.java:233`):
   - `noPhysics = isSpectator()`; spectator or passenger: `setOnGround(false)`.
   - Sleep counters; `updateIsUnderwater()`.
   - `LivingEntity.tick()` (2732):
     - `Entity.tick()` > `LivingEntity.baseTick()` > `Entity.baseTick()` (536). That covers
       `computeSpeed`, portals and sprint particles, then `wasEyeInWater` and **`updateFluidInteraction()`**:
       water/lava contact and fluid heights, fluid currents pushing `deltaMovement`, and water resetting
       `fallDistance`. Then `updateSwimming()`, lava halving `fallDistance`, and the below-world check.
       The `LivingEntity` part adds in-wall damage and drowning (server only), `hurtTime`, effects, and
       `yHeadRotO`/`yBodyRotO`.
     - `updatingUsingItem()`, `updateSwimAmount()`, then **`aiStep()`** (1.3).
     - Body yaw follows the movement since `xo`/`zo` (`tickHeadTurn`). `yRotO`, `xRotO`, `yBodyRotO`
       and `yHeadRotO` are shifted by 360 so they stay within 180 degrees of the current values.
   - Back in `Player.tick`: x and z clamped to ±2.9999999E7, attack and item tickers,
     `cooldowns.tick()`, then `updatePlayerPose()`. The pose sets the dimensions: standing 0.6 x 1.8
     (eyes 1.62), crouching 0.6 x 1.5, swimming or gliding 0.6 x 0.6 (`Avatar.java:21`).
3. Back in `LocalPlayer.tick`: first-person hands, then ambient sound handlers.

### 1.3 `LocalPlayer.aiStep()` (746)

1. `sprintTriggerTime--`; portal and nausea effects.
2. Remember last tick's input: `wasJumping`, `wasShiftKeyDown`, `hasForwardImpulse`.
3. Compute `crouching`: not flying, swimming or riding; fits crouching; and shift held, or the player
   cannot stand up.
4. **`input.tick()`**: `KeyboardInput` reads the keys (1.4).
5. Auto-jump: if `autoJumpTime > 0`, `input.makeJump()` forces `keyPresses.jump`. Auto-jump is set by
   `updateAutoJump` in `move()`; the option defaults to off (`Options.java:506`).
6. `moveTowardsClosestSpace` on the four corners: if a corner is inside a suffocating block, set
   `deltaMovement` x or z to ±0.1.
7. Sprint start and stop (1.6).
8. Creative double-tap toggles `abilities.flying` (and calls `jumpFromGround()` when enabling flight on
   the ground); spectators are forced to fly.
9. Elytra: jump newly pressed and not climbing, then `tryToStartFallFlying()` and a START_FALL_FLYING
   command.
10. In water with shift held: `goDownInWater()` (-0.04 y).
11. Flying: `deltaMovement.y += (jump - shift) * flyingSpeed * 3`.
12. Jumpable-vehicle charge.
13. `super.aiStep()`:
    - `AbstractClientPlayer.aiStep()`: `updateBob()` targets `min(0.1, horizontal deltaMovement)` when
      on the ground, otherwise 0.
    - Then `Player.aiStep()` (444): `jumpTriggerTime--`, regeneration, inventory tick, and flying resets
      `fallDistance`. **`LivingEntity.aiStep()`** (1.5) runs here. Afterwards `yHeadRot = yRot`,
      `setSpeed(MOVEMENT_SPEED)`, then the item/xp pickup (`touch`) and shoulder entities.
14. On the ground while flying (not spectator): stop flying.

### 1.4 Keyboard to `ClientInput`, `moveVector`, `xxa`/`zza`

- `ClientInput` (`client/.../player/ClientInput.java`) holds:
  - `public Input keyPresses`: a record `Input(forward, backward, left, right, jump, shift, sprint)` in
    `common/.../player/Input.java`.
  - `protected Vec2 moveVector`, read with `getMoveVector()`.
  - `hasForwardImpulse()` (`moveVector.y > 1e-5`) and `makeJump()`.
- `KeyboardInput.tick()` reads `options.keyUp/keyDown/keyLeft/keyRight/keyJump/keyShift/keySprint.isDown()`.
  It sets `moveVector = Vec2(left - right, forward - back).normalized()`, so x is left and y is forward.
  It is installed on login and respawn (`ClientPacketListener.java:548`, `1301`).
- Input is sampled once per tick and is level-triggered: a tap shorter than a tick can be missed.
  Within one tick, all substeps see the same keys.
- `LocalPlayer.applyInput()` (671) is called from `LivingEntity.aiStep`. When this player is the camera
  entity, it sets `xxa`, `zza = modifyInput(moveVector)`, `jumping = keyPresses.jump()` and the bob
  values. Otherwise it falls back to `LivingEntity.applyInput()`, which only scales by 0.98.
- `modifyInput` (686) applies, in order:
  1. x 0.98.
  2. While using an item (not riding): x the item's `UseEffects.speedMultiplier`.
  3. `isMovingSlowly()` (crouching or visually crawling): x the `SNEAKING_SPEED` attribute (0.3).
  4. Diagonals are stretched toward the unit square (length capped at 1).
- The server gets keys through `ServerboundPlayerInputPacket` (sent by `sendChanges` when `keyPresses`
  change). It calls `ServerPlayer.setLastClientInput` and `setShiftKeyDown(input.shift())`
  (`ServerGamePacketListenerImpl.java:430`).

### 1.5 `LivingEntity.aiStep()` (3009) and vanilla jumping

1. `noJumpDelay--` (if > 0).
2. Entities that cannot simulate decay velocity x 0.98. This does not apply to the local player.
3. Tiny-velocity cleanup of `deltaMovement`:
   - players: x and z set to 0 when the horizontal speed squared is < 9.0E-6;
   - every entity: y set to 0 when |y| < 0.003.
4. `applyInput()`; `isImmobile()` (dead or sleeping) clears `jumping`, `xxa` and `zza`.
5. Jump section, when `jumping && isAffectedByFluids()` (players: not flying):
   - in water with fluid height > 0 (unless on the ground in water no deeper than 0.4):
     `jumpInLiquid(WATER)` (+0.04 y);
   - in lava (unless on the ground in shallow lava): `jumpInLiquid(LAVA)`;
   - otherwise, if `onGround()` (or in shallow water) and `noJumpDelay == 0`: **`jumpFromGround()`**,
     `noJumpDelay = 10`;
   - when not jumping, `noJumpDelay = 0`. Holding jump re-jumps on the first grounded tick at least
     10 ticks after the previous jump.
6. If gliding: `updateFallFlying()`.
7. `input = Vec3(xxa, yya, zza)`; slow falling or levitation resets `fallDistance`.
8. A vehicle with a player controller runs `travelRidden(...)`. Otherwise, if
   `canSimulateMovement() && isEffectiveAi()` (both true for the local player and for `ServerPlayer`):
   **`travel(input)`**, which dispatches to `Player.travel`.
9. Server, or the authoritative client: `applyEffectsFromBlocks()`. That is `stepOn` when on the ground,
   then inside-block effects along this tick's recorded moves (portals, cobweb, berry bush, powder
   snow, bubble columns, honey), and fire.
10. Client only: `calculateEntityAnimation(omnidirectionalAirMover())` (1.12).
11. (Server freezing), auto spin attack, then `pushEntities()`: nearby pushable entities push the player
    through `push(...)`.

`jumpFromGround()` (2359) works like this:

- `jumpPower = JUMP_STRENGTH (0.42) x getBlockJumpFactor() + jump boost (0.1 per level)`.
- If `jumpPower > 1e-5`: `deltaMovement.y = max(jumpPower, y)`. Sprinting adds 0.2 in the facing
  direction `(-sin yaw, 0, cos yaw)`. Then `needsSync = true`.
- `ServerPlayer.jumpFromGround()` (1578) also awards `Stats.JUMP` and food exhaustion: 0.2 when
  sprinting, 0.05 otherwise.
- Callers: `LivingEntity.aiStep` (above), `LocalPlayer.aiStep` (enabling creative flight on the
  ground), and the server's jump detection in `handlePlayerPositionChange` (section 4).

### 1.6 Sprint and sneak

- **Sprint start** (`LocalPlayer.aiStep`, `canStartSprinting()` at 1128). All of these must hold:
  - not already sprinting, and there is forward impulse;
  - sprinting is possible: no blindness, enough food (or a sprint-capable vehicle), and not in shallow
    water unless flying;
  - not slowed by an item;
  - not gliding, unless underwater;
  - not moving slowly, unless underwater.

  Then a double-tap within `sprintWindow`, or the sprint key, calls `setSprinting(true)`. Shift, slow
  item use or the back key reset the double-tap timer.
- **Sprint stop:** `shouldStopRunSprinting()` (901) fires when sprinting becomes impossible, there is no
  forward impulse, or on `horizontalCollision && !minorHorizontalCollision`. Swimming has its own rule.
- **What sprint changes:**
  - `setSprinting` toggles a +30% `MOVEMENT_SPEED` modifier (`LivingEntity.java:2289`).
  - The server is told through START/STOP_SPRINTING in `sendIsSprintingIfNeeded()`.
  - `jumpFromGround` adds +0.2, air acceleration is 0.026 instead of 0.02
    (`Player.getFlyingSpeed`, 1934), and it affects FOV, sprint particles and server food exhaustion.
- **Sneak.** `LocalPlayer.isShiftKeyDown()` (659) reads `keyPresses.shift`; on the server it is a synced
  flag set from the input packet. Its effects:
  - the crouching pose (height 1.5) and input x 0.3;
  - `Player.maybeBackOffFromEdge` (3.1) on both sides;
  - `isDescending()` in the collision context (scaffolding);
  - `Player.getMovementEmission()` returns NONE when flying, or when sneaking on the ground: no
    footsteps or vibrations.

### 1.7 Old positions and render interpolation

- **Writers of `xo/yo/zo`, `xOld/yOld/zOld`:**
  - `Entity.commonTick()` sets them, and `yRotO`/`xRotO`, at the start of every entity tick, on both
    client and server.
  - `snapTo` and `teleportSetPosition` set position and rotation.
  - `absSnapTo(x, y, z)` sets `xo` only, not `xOld`.
  - Client position packets use `setValuesFromPositionPacket` (section 6).
  - `ServerGamePacketListenerImpl.tickPlayer` sets `xo` before `doTick`.
  - `LevelExtractor` sets `xOld` for entities with `tickCount == 0`.
- **Readers:**
  - camera: `Camera.alignWithEntity` lerps `xo` to `x`;
  - `getPosition` and `getEyePosition(partial)`;
  - entity rendering: `EntityRenderer` lerps `xOld` to `x`;
  - walk animation and body yaw use `x - xo`;
  - `oldPosition()` (`xOld`) is the fallback segment in `applyEffectsFromBlocks`.
- **Consequence:** the frame interpolates linearly from the tick-start position to whatever position the
  player has at the end of the tick. Publishing anything other than the tick-boundary position (for
  example the last substep) shows as judder. Never write these fields from the controller.

### 1.8 Mouse look; `yRot` and `yRotO` at travel time

- `MouseHandler.handleAccumulatedMovement()` (251) runs once per frame after the tick loop.
  `turnPlayer` (318) scales by `(0.6 * sensitivity + 0.2)^3 * 8` (smooth camera and spyglass are
  variants) and calls `player.turn(dx, dy)`.
- `Entity.turn(double, double)` (502) adds `dx * 0.15` to `yRot` and `dy * 0.15` to `xRot`, clamped to
  ±90. It adds the same deltas to `yRotO`/`xRotO`. So mouse look is immediate each frame, not
  interpolated. `yRot` is never wrapped (it grows while you keep turning); `LivingEntity.tick` only keeps
  `yRotO` within 180 of it.
- **At `travel()` time, `yRotO == yRot`**: `commonTick` just copied it, and nothing before travel
  changes it. `yRot` is the yaw from the last frame before this tick. To interpolate view yaw across
  substeps, the controller must remember its own previous-tick yaw.
- Vanilla direction: `getInputVector(input, speed, yRot)` (1780) gives
  `x = xxa*cos - zza*sin`, `z = zza*cos + xxa*sin`. Forward is `(-sin yaw, 0, cos yaw)`, and yaw 0 faces
  +z.

### 1.9 `deltaMovement`: use and decay

`deltaMovement` is in b/t. Every write goes through `Entity.setDeltaMovement(Vec3)` (3845), which
ignores non-finite values. Vanilla `travelInAir` (`LivingEntity.java:2438`):

1. **Friction.** On the ground, the friction of the block at `getBlockPosBelowThatAffectsMyMovement()`
   (0.500001 below the feet), adjusted by the `FRICTION_MODIFIER` attribute. In the air, 1.0. Default
   block friction is 0.6.
2. **`moveRelative(speed, input)`.** The speed comes from `getFrictionInfluencedSpeed` (2692):
   - on the ground, `getSpeed()` (`MOVEMENT_SPEED`: 0.1, 0.13 sprinting), scaled by 0.216/f^3 only when
     the friction f is above 0.6;
   - in the air, `getFlyingSpeed()`: 0.02 (0.026 sprinting), or the abilities flying speed when flying.
3. **`handleOnClimbable`.** On a climbable: horizontal clamped to ±0.15, y >= -0.15 (0 when sneaking on
   a ladder), and `fallDistance` reset.
4. **`move(MoverType.SELF, deltaMovement)`.**
5. **Climbing up.** `(horizontalCollision || jumping)` while climbing (or in powder snow with leather
   boots) sets y = 0.2.
6. **Gravity.** y gets levitation, or minus the effective gravity: 0.08 from the `GRAVITY` attribute, at
   most 0.01 when falling with slow falling. On the client, an unloaded chunk below gives -0.1 (or 0).
7. **Drag.** x and z x friction x 0.91; y x 0.98. Both are adjustable by `AIR_DRAG_MODIFIER`.

Inside `Entity.move`:

- Collision restitution zeroes the blocked components; slime and beds bounce.
- The block speed factor (soul sand, honey) scales x and z.
- `stuckSpeedMultiplier` (cobweb, berry bush, powder snow) scales the move and zeroes `deltaMovement`.

At the start of the next tick, tiny components are zeroed (1.5). For external writers, see section 6.

### 1.10 Where collision flags and `fallDistance` are updated

| Field | Type | Written by |
|---|---|---|
| `onGround` (private, read with `onGround()`) | boolean | **`Entity.move`**: `setOnGroundWithMovement(verticalCollisionBelow, horizontalCollision, movement)` when `abs(delta.y) > 0` **or** this instance is authoritative. On the local client that is every move; on the server only when the move has a y component. **`Player.tick`**: false for spectators and passengers. **Server** `handlePlayerPositionChange`: the client's packet flag. **`noPhysics` moves leave it unchanged**: it sticks (universal-modder kb, Minecraft-in-GTA gotcha 10; confirmed in 26.3). Both setters also recompute `mainSupportingBlockPos` (`checkSupportingBlock`, 711). |
| `horizontalCollision` | public boolean | **`Entity.move`, always:** x or z was clipped (`!Mth.equal`, tolerance 1e-5). `noPhysics` sets it false. **Server:** the client's packet flag. |
| `verticalCollision`, `verticalCollisionBelow` | public boolean | **`Entity.move`**, under the same condition as `onGround`: `delta.y != movement.y`, and "below" also needs `delta.y < 0`. |
| `minorHorizontalCollision` | public boolean | **`Entity.move`**: `isHorizontalCollisionMinor(movement)` when colliding. **`LocalPlayer`** (1091): true when the angle between the input direction and the actual horizontal movement is < 0.1396 rad (8 degrees). |
| `fallDistance` | public **double** | **Accumulates** in `Entity.checkFallDamage` (1587): `fallDistance -= (float) ya` when ya < 0 and not in water; resets after landing. **Resets:** water contact, climbables, slow falling or levitation, flying, riding (`LivingEntity.rideTick`), cobweb (`makeStuckInBlock`), the fall-reset clip in `move` (3.1), and on the server `movedUpwards`. **Lava** halves it. **Elytra** caps it at 1 while y > -0.5. |

In 26.3 there is no `Entity.walkDist` and no `wasOnGround`. View-bob walk distance lives in the
client-only `ClientAvatarState`. The only "was on ground" values are
`LocalPlayer.lastOnGround` (change detection for `sendPosition`) and `ServerEntity.wasOnGround` (the
tracker sends a full position sync when it flips).

### 1.11 `LocalPlayer.move()` override (975)

After `super.move` (section 3) it calls `updateAutoJump(dx, dz)`, which is active only when auto-jump
is on, on the ground, moving and not sneaking. It then calls `addWalkedDistance(length(dx, dz) * 0.6)`,
feeding `ClientAvatarState.walkDist` for view bobbing.

### 1.12 Walk animation, bobbing, footsteps

- **Walk animation.** `LivingEntity.calculateEntityAnimation(boolean useY)` (2634) is client only and
  runs in `aiStep` after travel.
  - `d = length(x - xo, useY ? y - yo : 0, z - zo)`. Passengers and dead entities stop the animation.
  - `updateWalkAnimation(d)` calls `walkAnimation.update(min(d * 4, 1), 0.4, 1)` (`WalkAnimationState`:
    `speed`, `position`, `stop()`).
  - It saturates at 0.25 b/t and does not check `onGround`: airborne horizontal motion swings the legs.
  - `RemotePlayer.tick()` calls `calculateEntityAnimation(false)`, so other clients animate the surfer
    from its interpolated motion.
- **View bobbing.** `ClientAvatarState`: `bob` comes from `updateBob()` (before travel), `walkDist` from
  `LocalPlayer.move`.
- **Footsteps.** `Entity.applyMovementEmissionAndPlaySound` (905) runs inside `move`, on the server or
  the authoritative client, when the emission is not NONE and the player is not riding:
  - `moveDist += horizontal * 0.6` (full length on climbables); `flyDist += length * 0.6`.
  - When `moveDist > nextStep` and the supporting block is not air, `vibrationAndSoundEffectsFromBlock`
    needs on ground, climbable, crouching with y == 0, or on rails, and not swimming.
  - It then plays `walkingStepSound` > `Player.playStepSound` (1448: swim sound in water, combination
    blocks like carpet and snow) and sets `nextStep = (int) moveDist + 1`.
  - Routing: `Player.playSound` calls `level.playSound(this, ...)`. The server sends to everyone except
    the player; the client plays its own steps locally.
- **Supporting block.** `getOnPos()`, `getOnPosLegacy()` (0.2 below) and
  `getBlockPosBelowThatAffectsMyMovement()` (0.5 below) use `mainSupportingBlockPos` when present. That
  comes from `level.findSupportingBlock` over a 1e-6 slab under the box; otherwise they use
  `floor(position)`.

### 1.13 Sending movement: `LocalPlayer.sendChanges()` (266) and `sendPosition()` (296)

Once per client tick, after all entities ticked. Not while paused, only after `hasClientLoaded()`.

1. When `keyPresses` changed: `ServerboundPlayerInputPacket(keyPresses)`.
2. Passenger: `Rot(yRot, xRot, onGround, horizontalCollision)`, plus a vehicle move if the client
   controls the vehicle.
3. Otherwise `sendPosition()`:
   - `sendIsSprintingIfNeeded()` sends START/STOP_SPRINTING when the flag changed.
   - It only sends while this player is the camera entity.
   - `positionReminder++`. **Move** when `|pos - lastSent|^2 > (2.0E-4)^2`, or when
     `positionReminder >= 20` (a forced resend at least once a second). **Rot** when yaw or pitch
     changed at all.
   - It sends `PosRot(position, yRot, xRot, onGround, horizontalCollision)`, `Pos(...)`, `Rot(...)`, or
     `StatusOnly(onGround, horizontalCollision)` when only the flags changed.
   - Fields: x, y, z as doubles; yRot, xRot as floats; a flags byte (1 = onGround,
     2 = horizontalCollision).
4. At the end of `Minecraft.tick`: `ServerboundClientTickEndPacket`.

Server rules that constrain this:

- A second packet carrying a position before the next tick-end packet disconnects the client
  (`receivedPositionThisTick`). So one position per client tick; substeps must stay internal.
- The tick-end packet with no movement this tick sets the server's known movement to zero.

## 2. `travel()` branches and the predicates that select them

`LivingEntity.aiStep` calls `travel(input)` (vehicles with a player controller use `travelRidden`
instead). For players, `Player.travel(Vec3)` (`Player.java:1368`) handles it:

1. `isPassenger()`: `LivingEntity.travel(input)`. The position is then overwritten by the vehicle's
   `positionRider`; players riding are ticked through `rideTick()`.
2. Otherwise:
   - if `isSwimming()`, nudge `deltaMovement.y` toward the look direction first;
   - if `getAbilities().flying`, run `LivingEntity.travel(input)` and then set
     `y = original y * 0.6`;
   - else run `LivingEntity.travel(input)`.

`LivingEntity.travel(Vec3)` (2400):

1. If `shouldTravelInFluid(level.getFluidState(blockPosition()))` (`isInLiquid() &&
   isAffectedByFluids() && !canStandOnFluid(...)`), then `travelInFluid`:
   - `isInWater()`: `travelInWater` (0.8 or 0.9 slowdown, `WATER_MOVEMENT_EFFICIENCY`, dolphin's
     grace).
   - Otherwise `travelInLava`.
2. Else if `isFallFlying()`: `travelFallFlying` (elytra physics; it falls back to `travelInAir` and
   stops gliding on a climbable).
3. Else `travelInAir` (1.9).

| Predicate | Source of truth | Computed on |
|---|---|---|
| `isInWater()` (= `wasTouchingWater`), `isInLava()`, `getFluidHeight(tag)`, `isEyeInFluid` | `EntityFluidInteraction.update`, called by `Entity.updateFluidInteraction()` (1679) in `baseTick`, and again from `LivingEntity.checkFallDamage` when not in water. Covers the box, current flow, fluid heights from the box bottom, and the eye. | Each side for its own copy: the client for `LocalPlayer`, the server for `ServerPlayer` in `doTick`. Not synced. |
| `isAffectedByFluids()` | `Player`: `!abilities.flying` | Both |
| `isSwimming()` | Shared flag 4. `updateSwimming()` in `baseTick`: start when sprinting, underwater and not riding; keep while sprinting in water. `Player`: false when flying or spectator. | Both sides each tick; the server value is also synced back through entity data. |
| `onClimbable()` | `LivingEntity.onClimbable()` (1715): block at `blockPosition()` (`getInBlockState`) is `#climbable`, or a trapdoor over a matching ladder. Gliding ignores `#can_glide_through`. `Player`: false when flying. It also records `lastClimbablePos`. | On demand, each side |
| `getAbilities().flying` | Client double-tap or spectator forcing, sent with `ServerboundPlayerAbilitiesPacket`; the server accepts it only with `mayfly`. The server pushes `ClientboundPlayerAbilitiesPacket` (game mode). | Client-driven, server-validated |
| `isFallFlying()` | Shared flag 7. The client sets it locally (`tryToStartFallFlying`) and sends START_FALL_FLYING; the server validates (`handlePlayerCommand`, 1786) and `updateFallFlying` clears it when it cannot glide. | Both; server authoritative through entity data |
| `isPassenger()` | `vehicle != null`, set by the server (`ClientboundSetPassengersPacket`) | Server authoritative |
| `isSpectator()` / `noPhysics` | `gameMode() == SPECTATOR`: client from `PlayerInfo`, server from `gameMode`. `Player.tick` sets `noPhysics = isSpectator()` on both sides; `move` then skips collision. | Both |
| `isSleeping()` / `isImmobile()` | Synced sleeping position; `isImmobile` = dead or sleeping, which clears input | Server authoritative |
| `canSimulateMovement()` / `isEffectiveAi()` | Players are `MoveSimulationType.AUTHORITATIVE_SIDE_AND_SERVER`: true on the server and for the local player, false for `RemotePlayer` | Both |

## 3. `Entity.move` and `Entity.collide`

### 3.1 `Entity.move(MoverType moverType, Vec3 delta)` (737)

`MoverType` is `SELF`, `PLAYER` (server re-simulation), `PISTON`, `SHULKER_BOX` or `SHULKER`.

1. **noPhysics** (spectator): `setPos(pos + delta)`, record the movement, and clear the horizontal,
   vertical and minor collision flags. `onGround` is left alone. Return.
2. **PISTON**: `limitPistonMovement` caps each axis at ±0.51 per game tick.
3. **`stuckSpeedMultiplier`** (cobweb, berry bush, powder snow): `delta *= multiplier` (except PISTON),
   then the multiplier and `deltaMovement` are reset to zero.
4. **`delta = maybeBackOffFromEdge(delta, moverType)`.** The `Player` override (862) applies when:
   - not flying, `delta.y <= 0`, and the mover is SELF or PLAYER;
   - `isStayingOnGroundSurface()` (shift held);
   - `isAboveGround(maxUpStep)`: on the ground, or `fallDistance < step` with no room to fall
     `step - fallDistance`.

   It then shrinks `delta.x`/`delta.z` in 0.05 steps while the box could fall at least one step height
   at the new spot. This runs on both sides; the server uses the shift flag from the input packet.
5. **`movement = collide(delta)`** (3.2).
6. **If anything moved** (`|movement|^2 > 1e-7`, or the request was fully used):
   - When `fallDistance != 0` and `|movement| >= 1`: ray-clip from the feet along the movement for
     `min(|movement|, 8)` blocks with `ClipContext.Block.FALLDAMAGE_RESETTING`. That matches
     `#minecraft:fall_damage_resetting` (`#climbable`, sweet berry bush, cobweb) as full cubes, plus
     end portal/gateway, and nether portal when its delay rule is 0, for players. Any hit resets
     `fallDistance`.
   - Record `Movement(pos, newPos, delta)` (used later by `applyEffectsFromBlocks`), then
     `setPos(newPos)`.
7. **Flags:**
   - `x/zCollision = !Mth.equal(delta, movement)` per axis; `horizontalCollision` is set from them.
   - If `abs(delta.y) > 0` or `isLocalInstanceAuthoritative()`: `verticalCollision`,
     `verticalCollisionBelow`, and `setOnGroundWithMovement(verticalCollisionBelow, ...)`.
   - `minorHorizontalCollision`.
8. `effectPos = getOnPosLegacy()`. **If authoritative: `checkFallDamage(movement.y, onGround(),
   effectState, effectPos)`.** The local client is authoritative; `ServerPlayer` never is.
9. If not removed and it can simulate, and there was a vertical collision on a y move or any horizontal
   collision: `restituteMovementAfterCollisions`. It zeroes or bounces the blocked `deltaMovement`
   components; slime and beds call `bounceOn`, emit a BOUNCE event and set `syncPosition`.
10. On the server, or the authoritative client: movement emission (footsteps and vibrations, 1.12).
11. `deltaMovement.xz *= getBlockSpeedFactor()`.

Authority helpers:

- `isLocalInstanceAuthoritative()` (3695) is `isLocalClientAuthoritative()` on the client
  (`Player.isLocalPlayer()`, true only for `LocalPlayer`) and `!isClientAuthoritative()` on the server
  (false for players).
- `canSimulateMovement()` (3713) is true for players on the server and for the local player.

### 3.2 `private Vec3 collide(Vec3 movement)` (1176)

1. `entityColliders = level.getEntityCollisions(this, aabb.expandTowards(movement).expandTowards(0,
   maxUpStep(), 0))`. These are the boxes of entities this one can collide with (`canBeCollidedWith`:
   boats, shulkers, happy ghasts), excluding entities sharing its vehicle.
2. `movementStep` is the movement itself when it is zero, otherwise `collideBoundingBox(this, movement,
   aabb, level, entityColliders)`. That gathers colliders with
   `collectCollidersIgnoringWorldBorder(entity, ...)` (1249; despite the name it **adds the world
   border** shape when `worldBorder.isInsideCloseToBorder(entity, box)`), plus
   `level.getBlockCollisions(this, aabb.expandTowards(movement))`, then calls `collideWithShapes`.
3. `collideWithShapes(movement, box, shapes)` (1279) sweeps **per axis** in
   `Direction.axisStepOrder(movement)`: **Y first**, then X then Z when `|x| >= |z|`, otherwise Z then
   X. Each axis calls `Shapes.collide(axis, box.move(resolvedSoFar), shapes, d)`, which chains
   `VoxelShape.collide(axis, box, d)` (the furthest move along one axis without overlap; touching is
   allowed) and stops once `|d| < 1e-7`.
4. **Step-up** applies when `maxUpStep() > 0`, the player was blocked moving down or is already on the
   ground, and x or z was blocked. `maxUpStep()` is the `STEP_HEIGHT` attribute: 0.6 for players, at
   least 1 for mounts a player controls.
   - The grounded box is the box after the downward clip (or the current box). The step-up box is it
     expanded by `(mx, maxUpStep, mz)`, plus -1e-5 in y when not landing.
   - Colliders are gathered for that region. Candidate heights are every Y coordinate of those shapes,
     relative to the grounded box's bottom, in `[0, maxUpStep]`, sorted ascending, skipping the original
     y result.
   - For each candidate h, run `collideWithShapes((mx, h, mz), groundedBox, colliders)`. The first one
     that goes further horizontally than the plain result wins, with y re-based to the original box.
   - The whole step happens in one tick; rendering smooths it.
5. Otherwise return `movementStep`.

**Collision context.**

- `level.getBlockCollisions(entity, box)` uses `CollisionContext.of(entity)`, an
  `EntityCollisionContext` that carries `isDescending()` (= shift), the entity's bottom y, the held item
  and the entity.
- Each block shape is `context.getCollisionShape(state, level, pos)`, which calls
  `state.getCollisionShape(level, pos, context)`. This path is uncached.
- `BlockCollisions` (`world/level/BlockCollisions.java`) returns only world-space shapes that overlap
  the query box with positive volume (touching faces are skipped). It also checks neighbour cells for
  blocks whose shape extends beyond the unit cube.

**Callers.**

- `collide` is called only by `Entity.move` (line 769).
- `collideBoundingBox(Entity, ...)` is called only by `collide`.
- `collideBoundingBox(CollisionContext, ...)` is used by `Particle`.
- `collectAllColliders(...)` is used by `getAvailableSpaceBelow` and
  `ServerPlayerGameMode.isInRangeOfGround`.

`move` callers that reach players:

- every `travel` variant: the client `LocalPlayer`, and the server `ServerPlayer` in `doTick`;
- `ServerGamePacketListenerImpl.handlePlayerPositionChange` with `MoverType.PLAYER`;
- pistons (`PISTON`) and shulkers (`SHULKER`).

### 3.3 Where to substitute exact collision for players near ramps

**Hook `Entity.collide` at HEAD (cancellable).** It is the single funnel for every player move on both
sides: vanilla travel on the client, the server's `doTick` simulation, the server's packet
re-simulation, and pistons. It also wraps both the main sweep and the step-up. Lower points don't work:

- `collideBoundingBox` misses the step-up sweeps.
- `collectCollidersIgnoringWorldBorder` can only return `VoxelShape`s, which cannot represent a plane.
- A custom `VoxelShape` subclass is technically possible: the constructor is protected, and `move` and
  `collide(Axis, AABB, double)` can be overridden. But its voxel grid would still drive the overlap
  filter in `BlockCollisions` (`Shapes.joinIsNotEmpty`), `noCollision`, the step-up candidates
  (`getCoords`) and every `Shapes.or/join`. A plane would be missed or over-reported somewhere.

Use `@WrapOperation` on the `collide` call inside `move` instead only if the `MoverType` is needed (for
example to leave `PISTON` alone).

Return early (vanilla) unless `this` is a `Player` and a ramp cell lies within
`getBoundingBox().expandTowards(movement).expandTowards(0, maxUpStep(), 0)` plus a margin. When it does
apply, the replacement must keep these vanilla behaviours:

- entity boxes (`level.getEntityCollisions(this, region)`), the world border shape when
  `isInsideCloseToBorder`, and vanilla shapes of non-ramp blocks (`level.getBlockCollisions`, minus the
  ramp staircases);
- the exact brushes of ramp cells (`RampCell.localPlanes`, with tight axial bevels);
- the same return contract: the clipped movement vector, which `move` turns into flags with
  `Mth.equal` (x/z) and `!=` (y).

Semantics, from the server's acceptance rule (section 4):

- **Trust mode (client, controller-published moves):** return the controller's exact delta. The
  controller already traced it, and a second trace could move the published position off the core
  state.
- **Exact mode (server re-sim, server `doTick` sim, vanilla-mode clients):**
  1. **Chord first.** Sweep the hull straight along the whole movement against all colliders. If it is
     free, return the movement unchanged. A client surfing a ramp ends each tick on or above one plane,
     so the chord between two such points stays above it: no hit, no spurious `onGround`, and no server
     footsteps for other players.
  2. **Otherwise resolve like vanilla:** per-axis sweeps in `axisStepOrder` (Y first) with vanilla's
     step-up, against exact brushes plus vanilla boxes. Y-first sweeps match vanilla and the server's
     leniency: the server ignores the y error and checks only 0.25 blocks horizontally.

  A Source-style simultaneous slide along the chord can lose horizontal distance at step-ups (a chord
  into a ledge face stops x/z where vanilla's Y-then-X climbs it). That costs more than 0.25 blocks at
  walking speed, so the server would rubber-band.
- **Hull:** sweep the same hull the controller traces. The MC box (0.6 x 1.8) and the CS:S hull
  (32 x 32 x 62 u = 0.813 x 1.575 blocks) differ. With the CS:S hull touching a 5:4 ramp, the MC box's
  corner sits about 0.13 blocks above the plane (0.21 on 2:1).
- **Safety:** the hook runs on the client thread and the integrated server thread at once in
  singleplayer. Keep per-call state on the entity or the stack, not in statics.

The inscribed staircase lies entirely under the plane. So vanilla checks that still use it never
disagree with a box resting on or above the plane: `isEntityCollidingWithAnythingNew`, `noCollision`,
pose fitting and `findSupportingBlock`.

## 4. Server: `ServerGamePacketListenerImpl.handleMovePlayer` end to end

Server tick order:

1. Between ticks, queued packets run on the server thread: `handleMovePlayer`, `handlePlayerInput`,
   `handleClientTickEnd`, `handleAcceptTeleportPacket`.
2. `ServerLevel.tick`: `commonTick()` sets `xo = pos`; then the light `ServerPlayer.tick()` (581:
   game mode, containers, camera, advancements, `trackStartFallingPosition`); then entity tracking
   (`ServerEntity.sendChanges`: motion sync, see section 6).
3. `tickConnection()` runs `ServerGamePacketListenerImpl.tick()` > **`tickPlayer()`** (322).

### 4.1 `handleMovePlayer(ServerboundMovePlayerPacket)` (1062)

1. NaN or infinite values: disconnect `invalid_player_movement`.
2. If the packet has a position and `receivedPositionThisTick` is already set: disconnect. Otherwise
   set it; it is cleared by `handleClientTickEnd` (2227).
3. If `!wonGame`:
   - `tickCount == 0`: `resetPosition()`.
   - If `hasClientLoaded()`:
     - **Awaiting teleport** (`updateAwaitingTeleport()`, 1269): while a teleport is unacknowledged, the
       teleport is re-sent every 20 ticks and only the rotation is applied (`absSnapRotationTo`).
     - Otherwise `handlePlayerPositionChange(x, y, z, yRot, xRot, onGround, horizontalCollision)`, with
       missing fields falling back to current values.

### 4.2 `private void handlePlayerPositionChange(DDDFFZZ)` (1102)

1. Clamp the target (x/z ±3.0E7, y ±2.0E7) and wrap the rotation.
2. **Passenger:** only the rotation is applied, plus chunk tracking. Return.
3. `start = current position`. This is normally `lastGood`.
4. **Moved too quickly:**
   - `movedDist = |target - firstGood|^2`, where `firstGood` is the position at the start of the
     current server tick (`resetPosition` in `tickPlayer`). `expectedDist = |server deltaMovement|^2`.
   - Sleeping: `movedDist > 1` teleports back, and nothing else runs (no re-simulation, no fall
     check).
   - Otherwise, when the tick rate runs normally: `receivedMovePacketCount++` and
     `deltaPackets = received - known`, where `known` is reset every server tick. **If
     `deltaPackets > 5` it is set to 1.**
   - If `shouldCheckPlayerMovement(isFallFlying)` (1228) and
     `movedDist - expectedDist > (gliding ? 300 : 100) * deltaPackets`: warn
     `"{} moved too quickly! {},{},{}"`, teleport to the current position and rotation, and return.
     The check is skipped for the **singleplayer owner** (`isSingleplayerOwner()`), while changing
     dimension, with gamerule `player_movement_check` false, or when gliding with
     `elytra_movement_check` false. **Elytra multiplier: 300 instead of 100.**
5. **Server-side jump detection and re-simulation:**
   - `delta = target - lastGood`; `movedUpwards = delta.y > 0`.
   - If the server thinks the player is on the ground, the packet says not, and it moved up:
     **`player.jumpFromGround()`** (stats, food, server `deltaMovement`).
   - `playerStandsOnSomething = verticalCollisionBelow`, the server's current value. If the client
     claims ground but stands on nothing, resend the support blocks (at most every 200 ticks).
   - **`player.move(MoverType.PLAYER, delta)`**: the full `Entity.move`, including `collide` and
     `maybeBackOffFromEdge`, but no `checkFallDamage` because the server is not authoritative.
6. **Moved wrongly:**
   - The error is `target - position after the re-sim`.
   - **The y error is always zeroed**: the guard `if (yDist > -0.5 || yDist < 0.5)` (1171) is true for
     every value.
   - `fail = errX^2 + errZ^2 > 0.0625` (0.25 blocks) and not changing dimension, not sleeping, **not
     creative**, not spectator, and not `isInPostImpulseGraceTime()`. The grace time is 40 ticks after a
     wind-charge hit or a mace smash impulse, and 10 after the `ApplyEntityImpulse` enchantment effect.
     On fail it warns `"{} moved wrongly!"`.
7. **Accept** if `noPhysics` or sleeping, or if both hold: (`!fail`, or the old box already collided)
   and `!isEntityCollidingWithAnythingNew(level, player, oldAABB, target)` (1285). The latter takes
   shapes from `level.getPreMoveCollisions(entity, newBox.deflate(1e-5), oldBox.getBottomCenter())`
   (entity boxes, plus block shapes through `CollisionContext.withPosition(entity, oldBottomY)`, an
   entity context marked as placement). A shape that does not overlap the old box counts as a "new"
   collision.

   On accept:
   - **`absSnapTo(target, rot)`**: the client's position wins, not the re-simulated one.
   - **Floating check:** `clientIsFloating` is true when the y delta >= -0.03125, nothing was standing
     under the player, not spectator, server flight not allowed, no `mayfly`, no levitation, not gliding
     or spin attacking, and `noBlocksAround` holds (box inflated 0.0625 and extended 0.55 down, all
     air). More than 80 consecutive such server ticks (x 0.08/gravity) kicks for flying.
   - `clientDelta = position - start`.
   - **`setOnGroundWithMovement(clientOnGround, clientHorizontalCollision, clientDelta)`**: the client's
     flags are adopted.
   - **`doCheckFallDamage(clientDelta.x, clientDelta.y, clientDelta.z, clientOnGround)`** (section 5).
   - `handlePlayerKnownMovement` (known movement, idle timer).
   - `movedUpwards`: `resetFallDistance()`.
   - On ground, landed in liquid, climbing, spectator, gliding or spinning: `tryResetCurrentImpulseContext()`.
   - `checkMovementStatistics` (stats; food exhaustion 0.1 per block while sprinting on the ground).
   - `lastGood = position`.
8. **Reject:**
   - `teleport(start, targetRot)`.
   - `doCheckFallDamage(position - start ..., clientOnGround)`. The deltas are 0 because the teleport
     already moved the server copy to `start`, so this only lands an accumulated fall when the client
     claims ground.
   - `removeLatestMovementRecording()`.

### 4.3 Teleports and acknowledgements

`teleport(PositionMoveRotation, Set<Relative>)` (1305):

1. Increment the teleport id.
2. **`player.teleportSetPosition(destination, relatives)`** moves the server copy immediately, sets old
   position and rotation, sets `deltaMovement` to the destination's (zero for corrections) and clears
   the movement recording.
3. `awaitingPositionFromClient = position`.
4. Send `ClientboundPlayerPositionPacket.of(id, destination, relatives)`.

The client answers with `ServerboundAcceptTeleportationPacket(id, x, y, z, yRot, xRot)`, which carries
its position in 26.3. `handleAcceptTeleportPacket` (545) snaps the player to the awaited position, sets
`lastGood` to it, and then runs the acknowledgement position through `handlePlayerPositionChange` with
`onGround = false` and `horizontalCollision = false`.

### 4.4 Does `ServerPlayer` run travel/aiStep itself? Yes, and the position is then discarded

`tickPlayer()` (322), once per server tick after the levels tick:

1. `resetPosition()` sets `firstGood = lastGood = position`, and `xo = position`.
2. **`player.doTick()`** (651) runs `super.tick()`, the full `Player.tick`: `baseTick` (fluids),
   `aiStep()`, and **`travel(input)` with a zero input**: `xxa`/`zza` are never set on the server and
   `jumping` stays false. That does `move(SELF, server deltaMovement)` with vanilla gravity, drag and
   collisions (through `collide`, so through SurfCraft's hook).
3. **`absSnapTo(firstGood)`** throws away the position change.

The side effects remain:

- `deltaMovement`: gravity, drag, restitution, and later `jumpFromGround`.
- `horizontalCollision`, always.
- `onGround`, `verticalCollision` and `verticalCollisionBelow`, when the simulation moved in y.
- `moveDist` and footsteps broadcast to others, and block effects along the simulated path.
- Fall-distance resets from fluids, climbables and the fall-reset clip. There is no `checkFallDamage`.

Consequence: when the next packet arrives, `player.onGround()` and `verticalCollisionBelow` usually
come from this simulation. Sliding on a ramp, it lands, so a following packet with upward movement and
`onGround = false` triggers the server's `jumpFromGround()` (`Stats.JUMP`, +0.05 or +0.2 food
exhaustion). Surfing up a ramp therefore costs a little hunger every few ticks. Optional fix: mixin e1
in section 9.

### 4.5 What could reject positions 4+ blocks apart

- **Moved too quickly.** One packet of 4.445 blocks is 19.8 <= 100 and passes. The per-packet ceiling
  is sqrt(100) = 10 b/t (17.3 when gliding).
  - Bursts: n packets in one server tick give `(4.445 n)^2` against `100 n`. That passes for n <= 5
    (494 < 500) and **fails for n >= 6**, because the budget resets to 100.
  - So a server stall of 6 or more client ticks at top speed teleports the player back. It does not
    apply to the singleplayer owner or with `/gamerule player_movement_check false`.
  - `expectedDist` (server `deltaMovement^2`) is subtracted, which only loosens the check.
  - **SurfCraft (Wave C, B1):** n packets at a steady v fail once v > 10/sqrt(n) b/t (n <= 5) or 10/n
    (n >= 6): six packets above 1312 u/s. The CS:S cap is per axis, so |v| reaches 3500*sqrt(3) = 6062 u/s
    (7.7 b/t). Players in the server's surf window instead get vanilla's single-packet budget (100 square
    blocks from the last accepted position) for every packet, while their packets run at most 5 s ahead of
    real time; everyone else keeps vanilla's check. Do not turn the gamerule off: without it one long packet
    near a ramp costs seconds of re-simulation (review C7).
- **Moved wrongly** has no distance limit. It fails only when the re-simulation disagrees by more than
  0.25 blocks horizontally.
- **`isEntityCollidingWithAnythingNew`** only checks the end box.
- **Nothing else** in `move`/`collide` caps distance. The collision query grows with the delta; the
  fall-reset clip is capped at 8 blocks.
- **Tracking to other clients.** The player update interval is 2 ticks. Deltas over 8 blocks per axis
  (8.9 at top speed) fall back to full-precision `ClientboundEntityPositionSyncPacket`, which is fine.
- **The floating kick** needs 80 ticks of not descending with only air around (it looks 0.55 blocks down),
  so a vertical launch above about 3175 u/s used to trigger it (review B7). SurfCraft counts a move whose
  vertical step follows CS:S gravity from the last one as falling, not floating.

## 5. Fall damage pipeline

- **Field.** `Entity.fallDistance` is a `public double`. Accumulation casts each `ya` to `float`.
- **Client path.** `Entity.move` (authoritative) calls `checkFallDamage(movement.y, onGround(), state at
  getOnPosLegacy, pos)`.
- **Server path.** `handlePlayerPositionChange` calls
  **`public final void doCheckFallDamage(double xa, double ya, double za, boolean onGround)`**
  (`Entity.java:1578`). Unless the player touches an unloaded chunk, it runs
  `checkSupportingBlock(onGround, movement)` and then `checkFallDamage(ya, onGround, state, pos)` at
  `getOnPosLegacy()`.
- **The `checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos)` chain:**
  - `ServerPlayer` (1330): the mace's extra landing particles, then super.
  - `LivingEntity` (367): if not in water, `updateFluidInteraction()`. On the server, when on the ground
    with `fallDistance > 0`: `onChangedBlock` and landing particles. After super, landing clears
    `lastClimbablePos`.
  - `Entity` (1587): when not in water and `ya < 0`, `fallDistance -= (float) ya`. When on the ground
    with `fallDistance > 0`, it calls
    **`Block.fallOn(Level, BlockState, BlockPos, Entity, double fallDistance)`** (`Block.java:479`) and
    emits HIT_GROUND. Then **`resetFallDistance()`**.
- **`Block.fallOn`** calls
  `entity.causeFallDamage(fallDistance * (1 - getFallDistanceReduction()), 1.0F, damageSources().fall())`.
  `BlockBehaviour.Properties.fallDistanceReduction(float)` is new and sets the reduction. Hay, slime,
  honey, farmland, turtle egg, powder snow and dripstone override `fallOn`.
- **`Player.causeFallDamage(double, float, DamageSource)`** (1415): `mayfly` means no damage; it also
  records `FALL_ONE_CM`. Then **`LivingEntity.causeFallDamage`** (1778):
  - The current impulse context (wind charge) may cap the distance to the impulse's impact height.
  - `damage = floor((fd + 1e-6 - SAFE_FALL_DISTANCE (3)) * modifier * FALL_DAMAGE_MULTIPLIER)`.
  - A positive value plays sounds and calls `hurt(source, dmg)`. `hurt` is server-only; on the client
    only the sounds play.
  - Immunity comes from the `#fall_damage_immune` tag, or from gamerule `fall_damage` false through
    `Player.isInvulnerableTo` (663).
- **`resetFallDistance()`** (2989) sets 0. The `ServerPlayer` override (840) also fires the
  FALL_FROM_HEIGHT advancement trigger.

**Where to reset on ramp contact (server):** at the head of `LivingEntity.checkFallDamage`, limited to
players.

- Both server branches (accept and reject) reach it through `doCheckFallDamage`, and the client reaches
  it through `move`.
- Decide contact geometrically at the current (accepted) position: the controller's hull within about
  2 Source units (0.05 blocks, the CS:S ground-probe distance) of an exact ramp brush.
- On contact, `resetFallDistance()` and clamp `ya` to >= 0, so this packet's descent is not added. Do
  not cancel: that would skip `updateFluidInteraction()` and the climbable reset.

Rejected alternatives:

- **Tagging ramps `#minecraft:fall_damage_resetting`** only works for moves of 1 b/t or more, counts the
  whole cell (including the air above the slope), and the server still adds the tick's descent after
  the reset.
- **`fallDistanceReduction(1.0F)`** only protects landing on the ramp block itself, not the accumulated
  slide down to the flat floor below.
- **Resetting inside the `collide` hook** depends on the re-simulated path. `doCheckFallDamage` then
  adds the packet's descent afterwards.

## 6. Server corrections and external motion on the client

- **`ClientboundPlayerPositionPacket`** is handled by `ClientPacketListener.handleMovePlayer`
  (`client/.../ClientPacketListener.java:786`). Unless riding, it calls
  `setValuesFromPositionPacket(change, relatives, player, false)` (800):
  - The absolute value is `PositionMoveRotation.calculateAbsolute(current, change, relatives)`.
    "Current" is the position, rotation and `getKnownMovement()` (= `deltaMovement` on the client).
  - It then calls `setPos`, **`setDeltaMovement(absolute delta)`** (zero for the server's corrections),
    `setYRot`, `setXRot`, and `setOldPosAndRot(...)` shifted by the same relative change, so absolute
    teleports do not interpolate.
  - Finally it sends `ServerboundAcceptTeleportationPacket(id, x, y, z, yRot, xRot)`, tells block
    prediction, and stops block breaking.
- **`ClientboundPlayerRotationPacket`** (`handleRotatePlayer`, 822) calls `setYRot`/`setXRot` and
  `setOldRot()`, then replies `Rot(..., false, false)`.
- **`ClientboundSetEntityMotionPacket`** (`handleSetEntityMotion`, 634) calls
  `entity.lerpMotion(movement)`. For the local player that is `Entity.lerpMotion`, an **absolute
  `setDeltaMovement`**. `RemotePlayer` lerps over a few ticks instead.
  - The player receives it when `syncVelocity` was set by `markHurt()` on damage with impact (which
    includes knockback), or directly from `Player.attack` extra knockback, the mace, and the
    `ApplyEntityImpulse` enchantment effect.
  - The value is the server's `deltaMovement`: its own simulation (4.4), plus `jumpFromGround`, plus
    the knockback.
- **Knockback (server).** `LivingEntity.knockback` (1635) sets `deltaMovement` to
  `(x/2 - kx, onGround ? min(0.4, y/2 + power) : y, z/2 - kz)`, then the motion is synced as above.
- **Explosions.** `handleExplosion` (1331) calls `player.pushFromExplosion(knockback)`, which is
  `Entity.push(Vec3)`, an **additive** `setDeltaMovement(get().add(...))`. The server also pushes its
  own copy, and damage may then also send a motion packet.
- **Entity pushing.** `LivingEntity.pushEntities` makes others call `push(double, double, double)` on
  the player, which is additive. It runs in `aiStep` after travel.
- **Same-tick block effects after travel** write `deltaMovement` and flags: bubble columns, honey
  slide, slime `stepOn`, powder snow, cobweb.
- **Funnels.** Velocity always goes through `Entity.setDeltaMovement(Vec3)` (3845). Position goes
  through the final `setPosRaw`, from `setPos`, `move`, `snapTo`, `absSnapTo` and
  `teleportSetPosition`.

**Recipe for the controller to detect external changes:**

1. Store `lastPublishedPos` and `lastPublishedVel`: exactly what it wrote at the end of its travel.
2. At the start of its next travel, if `position() != lastPublishedPos`, it was a teleport, correction
   or clamp: resync the core origin, and the velocity too.
3. Apply vanilla's tiny-velocity cleanup (1.5) to `lastPublishedVel`. If `getDeltaMovement()` still
   differs, adopt it as the new velocity: knockback, explosion, motion packet, push or a block effect.
4. Optionally, a TAIL hook on `ClientPacketListener.handleMovePlayer` marks "server teleport" so the
   substep accumulator and yaw history reset as well.

## 7. Collision APIs

**`CollisionContext`** (`world/phys/shapes/CollisionContext.java`, an interface):

- Factories: `empty()`, `emptyWithFluidCollisions()`, `of(Entity)`, `of(Entity, boolean
  alwaysCollideWithFluid)`, `positionContext(double y)`, `placementContext(@Nullable Player)`,
  `withPosition(@Nullable Entity, double y)`.
- Methods: `isDescending()`, `isAbove(VoxelShape, BlockPos, boolean)`, `isHoldingItem(Item)`,
  `alwaysCollideWithFluid()`, `canStandOnFluid(FluidState, FluidState)`,
  `getCollisionShape(BlockState, CollisionGetter, BlockPos)`, `isPlacement()`.

**`EntityCollisionContext`** has `@Nullable Entity getEntity()`; `isAbove` is
`entityBottom > pos.y + shape.maxY - 1e-5`. To identify a player:
`context instanceof EntityCollisionContext ecc && ecc.getEntity() instanceof Player`.

**Block shapes:**

- `BlockBehaviour.getCollisionShape(BlockState, BlockGetter, BlockPos, CollisionContext)` is protected.
  It returns `getShape(...)` when the block has collision, otherwise empty.
- `BlockStateBase` (= `BlockState`) offers `getCollisionShape(BlockGetter, BlockPos)` (cached,
  context-free) and `getCollisionShape(BlockGetter, BlockPos, CollisionContext)` (uncached).
- The per-state cache is skipped with `dynamicShape()`. It holds the collision shape (empty context),
  "large shape" (extends past the unit cube), "full block" and face sturdiness.
- Context path users: `collide`, `noCollision` and pose fitting, `findSupportingBlock`,
  `getPreMoveCollisions`, the suffocation scan, auto-jump.
- Cached path users: `isInWall`, `isCollisionShapeFullBlock` (the suffocation predicate), and
  `getBlockSupportShape` (empty context).

**`VoxelShape`:**

- Box access: `List<AABB> toAabbs()` and `forAllBoxes(Shapes.DoubleLineConsumer)` (consumer
  `(x1, y1, z1, x2, y2, z2)`, coordinates in the shape's space). `forAllEdges` works the same way.
- Queries: `bounds()`, `min/max(Axis)`, `getCoords(Axis)`, `isEmpty()`, `move(...)`,
  `collide(Axis, AABB, double)`, `clip(...)`, `closestPointTo(Vec3)`.
- Builders: `Shapes.box` and `create` (0..1 or `AABB`), `Shapes.or/join`, `Block.box(...)`
  (0..16 pixels), `Block.column`, `Block.boxZ`.

**`CollisionGetter`** (implemented by `Level`):

- `Iterable<VoxelShape> getBlockCollisions(@Nullable Entity, AABB)`: lazy `BlockCollisions` returning
  world-space shapes that overlap the box with positive volume.
- `getBlockCollisionsFromContext(CollisionContext, AABB)`.
- `getCollisions(Entity, AABB)`: entities plus blocks.
- `getPreMoveCollisions(Entity, AABB, Vec3 oldPos)`.
- `List<VoxelShape> getEntityCollisions(Entity, AABB)`.
- `noCollision(...)` overloads (blocks, entities and the border), `noBlockCollision`.
- `collidesWithSuffocatingBlock(Entity, AABB)`, `findSupportingBlock(Entity, AABB)`,
  `findFreePosition(...)`, `clipIncludingBorder(ClipContext)`.

**Suffocation:**

- `Entity.isInWall()` (2299) is false for `noPhysics`. Otherwise it tests an eye-level square
  0.8 x width wide and 1e-6 tall against every block that `isSuffocating(...)` and whose **cached**
  collision shape overlaps it.
- Effects: the server's `LivingEntity.baseTick` deals in-wall damage; the client's
  `LocalPlayer.moveTowardsClosestSpace` pushes out of suffocating blocks; `isViewBlocking` defaults to
  the same predicate.
- The default `isSuffocating` is `#causes_suffocation` (= `#blocks_motion`) **and** a full collision
  cube. Untagged ramp blocks never suffocate.

## 8. HUD in Fabric API 0.161 (`fabric-rendering-v1`)

Package `net.fabricmc.fabric.api.client.rendering.v1.hud`:

- `HudElement` has one method: `void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker
  deltaTracker)`. In 26.x, `GuiGraphics` became `net.minecraft.client.gui.GuiGraphicsExtractor`.
- `HudElementRegistry` (static methods):
  - `addFirst(Identifier, HudElement)` and `addLast(Identifier, HudElement)` inherit no render
    condition (per the Javadoc), so F1 does not hide them unless the element checks
    `Minecraft.getInstance().gui.hud.isHidden()` itself.
  - `attachElementBefore/After(Identifier vanillaId, Identifier id, HudElement)` inherits the vanilla
    element's condition, so the element hides with F1.
  - `removeElement`, `replaceElement`.
- Vanilla ids are in `VanillaHudElements`: `MISC_OVERLAYS`, `CROSSHAIR`, `HOTBAR`, ..., `CHAT`,
  `PLAYER_LIST`, `SUBTITLES`.
- Text: `graphics.text(Font, String, int x, int y, int argb)`, with a drop shadow; an overload takes
  `boolean dropShadow`. Also `centeredText(...)`, `guiWidth()`, `guiHeight()` and `fill(...)`.
- **The colour needs a non-zero alpha:** text is skipped when the alpha is 0, so use `0xFFFFFFFF` or
  `CommonColors.WHITE`. The font is `Minecraft.getInstance().font`.
- Register from `ClientModInitializer.onInitializeClient()`. Fabric's own test mod does this in
  `fabric-repo/fabric-rendering-v1/.../HudTests.java`. Sketch:

```java
HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("surfcraft", "speed"),
		(g, dt) -> g.text(Minecraft.getInstance().font, SurfHud.line(), g.guiWidth() / 2 - 20, g.guiHeight() - 60, 0xFFFFFFFF));
```

## 9. Recommended mixins for SurfCraft

Names are the runtime names (26.3 is unobfuscated, no refmap). Call-site owners were checked with
`javap`. MixinExtras 0.5.5 ships with Fabric Loader (`@WrapOperation`, `@WrapWithCondition`,
`@ModifyReturnValue`, `@Local`). Config "client" means `surfcraft.client.mixins.json`; client mixins on
shared classes also run for the integrated server's `ServerPlayer`, so they must check
`instanceof LocalPlayer`.

| # | Purpose | Config | Target, method + descriptor | Injection | Cancel |
|---|---|---|---|---|---|
| a1 | Decide "drive this tick", capture keys, suppress the vanilla jump | client | `LocalPlayer` `applyInput()V` | `@Inject(at = @At("TAIL"))` | no |
| a2 | Run the controller instead of vanilla travel | client | `Player` `travel(Lnet/minecraft/world/phys/Vec3;)V` | `@Inject(at = @At("HEAD"), cancellable = true)` | yes |
| a3 | No auto-jump while driving | client | `LocalPlayer` `canAutoJump()Z` (private) | `@Inject(at = @At("HEAD"), cancellable = true)`, return false | yes |
| b1 | Exact collision for players near ramps (both sides) | common | `Entity` `collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;` (private) | `@Inject(at = @At("HEAD"), cancellable = true)`, `CallbackInfoReturnable<Vec3>` | yes |
| c1 | Ramp contact resets fall distance (both sides) | common | `LivingEntity` `checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V` | `@ModifyVariable(at = @At("HEAD"), argsOnly = true)` on `double ya`: reset, return `max(ya, 0)` | no |
| d1 | Legs don't run at full speed while sliding or airborne (optional) | client | `LivingEntity` `calculateEntityAnimation(Z)V` | `@Inject(at = @At("HEAD"), cancellable = true)`: `walkAnimation.update(0f, 0.4f, 1f)` | yes |
| e1 | No server-side jump detection on ramps (optional) | common | `ServerGamePacketListenerImpl` `handlePlayerPositionChange(DDDFFZZ)V` (private) | `@WrapWithCondition(at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))` | n/a |
| e2 | No sneak edge back-off on ramps (both sides; or make the controller ineligible while sneaking) | common | `Player` `maybeBackOffFromEdge(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MoverType;)Lnet/minecraft/world/phys/Vec3;` | `@Inject(at = @At("HEAD"), cancellable = true)`, return `delta` | yes |

Notes on each:

- **a1.** `applyInput` runs inside `LivingEntity.aiStep` after the flight toggle and the elytra start,
  and right before the jump section, so it is the first point where eligibility is final for the tick.
  - Eligibility: the camera entity (`minecraft.getCameraEntity() == player`), alive, not riding, not
    flying, not gliding, not in water or lava, not swimming, not climbing, not a spectator, not
    sleeping, not spin attacking, and near a ramp (or in the post-surf window).
  - Capture `input.keyPresses` and `yRot`. While driving, call `setJumping(false)` (public on
    `LivingEntity`): the jump section then does nothing and resets `noJumpDelay`.
  - Alternative: cancel `LivingEntity.jumpFromGround()V` at HEAD for the driven `LocalPlayer`.
- **a2.** Called once per tick through `LivingEntity.aiStep` (an `invokevirtual` on `travel`), after
  the jump section and before `applyEffectsFromBlocks`, `calculateEntityAnimation` and `pushEntities`.
  Handler: if `(Object) this instanceof LocalPlayer p && SurfController.driving(p)`, tick the
  controller and `ci.cancel()`.
- **b1.** Return early unless the entity is a `Player` and a ramp cell is in reach (3.3). There are
  two modes:
  - **Trust:** the controller set a per-entity payload (a field added through a mixin interface on the
    player, never a static); return it.
  - **Exact:** chord first, then vanilla-order per-axis sweeps with step-up against exact brushes plus
    vanilla shapes, entity boxes and the border.

  If `MoverType` matters, use `@WrapOperation` on
  `INVOKE Lnet/minecraft/world/entity/Entity;collide(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;`
  inside `move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V`, with
  `@Local(argsOnly = true) MoverType`.
- **c1.** Limit it to `this instanceof Player`, and test contact with the controller's hull (5).
  - Server-only alternative point: in `handlePlayerPositionChange`, before
    `INVOKE Lnet/minecraft/server/level/ServerPlayer;doCheckFallDamage(DDDZ)V` (two call sites:
    accept and reject).
  - The client controller can also call `resetFallDistance()` directly when its trace touched a ramp.
- **d1 and footsteps.**
  - Footsteps, bobbing walk distance and client fall sounds come for free if the controller publishes
    through `move()` (below).
  - Optionally gate leg animation on the Source ground state.
  - `RemotePlayer` on other clients cannot know the surf state and animates from distance, unless the
    state is synced.
- **e1 and e2** fix side effects found while tracing (4.4; 3.1 step 4). Without e2, holding shift on a
  ramp makes the server's re-simulation shrink the move and rubber-band.

**Controller publish sequence (inside a2):**

1. Detect external changes (section 6) and resync the core.
2. Run the 0.015 s substeps, interpolating yaw from the stored previous-tick yaw to the current `yRot`.
3. `P1` is the core state swept to the tick boundary; `exact = P1 - position()`.
4. Set the trust payload to `exact` and call `player.move(MoverType.SELF, request)`, then clear the
   payload.
   - If Source-grounded: `request = (exact.x, min(exact.y, 0) - 1e-3, exact.z)`. This ground probe makes
     `move` see a blocked downward component, so `verticalCollisionBelow` and `onGround` are true inside
     `move`. Footsteps, fall-damage landing and the supporting block then follow the CS:S ground state.
   - Otherwise `request = exact`, which gives `onGround = false`.
   - `LocalPlayer.move` then adds walk distance (bobbing) and runs auto-jump (off, via a3).
5. After `move`, overwrite what `move` cannot know:
   - `setDeltaMovement(coreVelocity * 0.00127)` (u/s to b/t: x 0.0254 m/u x 0.05 s);
   - `horizontalCollision` (a wall-like plane blocked the move), `minorHorizontalCollision`,
     `verticalCollision`;
   - `setOnGroundWithMovement(sourceGrounded, wall, exact)`, a harmless repeat.
6. Store `lastPublishedPos` and `lastPublishedVel`.

## 10. Pitfalls: what the controller must keep consistent

| Field or system | Who reads it | What to do |
|---|---|---|
| Position | Everything; `setPos` rebuilds the box, block and chunk position and the in-block cache | Publish through `move()` (or `setPos`); never write the position fields directly. |
| `xo/yo/zo`, `xOld/yOld/zOld` | Camera, entity renderer, walk animation, body yaw, block-effect fallback segment | Never touch them. `commonTick` sets them at tick start; publish only the tick-boundary position. |
| `yRotO` | Rendering only (the mouse moves it along with `yRot`) | Equals `yRot` at travel time; keep your own previous yaw. `yRot` is unwrapped; use differences mod 360. |
| `deltaMovement` (b/t) | Next tick's tiny-velocity cleanup; knockback (halves it); `updateBob`; elytra; vanilla travel when the controller hands back; explosions (additive); the server never sees it | Set it to the true core velocity in b/t every tick, so vanilla continues with the right momentum when the controller deactivates. |
| `onGround` and `mainSupportingBlockPos` | Packet flag (server fall damage, server jump detection); vanilla jump and friction; footsteps; bobbing; flight cancel; sneak edge logic | Source ground semantics (normal z >= 0.7): ramps are not ground. Use `setOnGroundWithMovement(onGround, horizontalCollision, movement)` so the supporting block updates. |
| `verticalCollision`, `verticalCollisionBelow` | Restitution; the server's floating check uses the server's own value | Keep consistent with `onGround`. |
| `horizontalCollision`, `minorHorizontalCollision` | Sprint stop (`horizontalCollision && !minor`); packet flag; climb and fluid-exit rules; elytra wall damage on the server | Set for wall-like contacts only, not for sliding along a ramp, or sprinting will drop. |
| `fallDistance` (double) | Client fall sound on landing; sneak edge logic; the server keeps its own copy for damage | Let `move` accumulate it; reset on ramp contact (c1). |
| `moveDist`, `flyDist`, `nextStep` | Footsteps and vibrations | Updated by `move()`; no work if you publish through `move`. |
| `ClientAvatarState.walkDist` | View bobbing | `LocalPlayer.move` adds it (`addWalkedDistance` is protected). |
| `walkAnimation` | Limb animation | Computed after travel from `x - xo`; see d1. |
| Sprint flag | Speed attribute, server food use on the ground, FOV, sprint particles (spawned under the player even on ramps) | Optionally `setSprinting(false)` while driving; vanilla re-evaluates each tick. |
| `jumping`, `noJumpDelay` | The vanilla jump section | a1 clears `jumping`. |
| Pose and dimensions | `updatePlayerPose` after aiStep; crouching shrinks the MC box | The MC box (0.6 x 1.8) is what vanilla and server checks use; keep the hook's hull equal to the controller's. |
| Keys | Sampled once per tick; auto-jump injects a jump | Read `input.keyPresses`; disable auto-jump (a3). |

More traps:

- **Testing mode.** Creative skips "moved wrongly", and the dev client's integrated server makes you the
  singleplayer owner (no "moved too quickly"). Verify rubber-banding in **survival or adventure**. Use a
  LAN or dedicated server for the speed check.
- **Log oracle.** The server warnings `moved wrongly!` and `moved too quickly!` appear in
  `run/logs/latest.log`. `is sending move packets too frequently` is debug level, so it only shows in
  the debug log.
- **Server lag.** 6 or more client ticks queued inside one server tick trigger vanilla's "moved too
  quickly" above 1312 u/s (4.5); SurfCraft budgets surfers per packet instead. Keep the gamerule on.
- **Elytra.** With an elytra worn, pressing jump in mid-air starts gliding (`LocalPlayer.aiStep` step 9)
  and drops out of the controller.
- **Two threads in singleplayer.** The client and integrated server threads both run the common
  mixins; no shared mutable statics.
- **Collision cache.** `getCollisionShape(level, pos)` with no context is cached and context-free; only
  the context overload can vary by entity.

## 11. Remaining unknowns

- `Entity.checkInsideBlocks` (per-axis replay of recorded moves for inside-block effects) was not
  traced in detail. It matters only if ramps should trigger block effects.
- Whether the controller's hull should become the player's MC dimensions, or the hook should sweep the
  CS:S hull while vanilla keeps 0.6 x 1.8, is a design decision. The 0.13 to 0.21 block gap between the
  MC box and the plane affects how the player model looks on ramps.
- Nothing here was run: no Gradle and no game, by instruction. Section 4's thresholds come from the
  code and need one survival-mode check in the dev client.
