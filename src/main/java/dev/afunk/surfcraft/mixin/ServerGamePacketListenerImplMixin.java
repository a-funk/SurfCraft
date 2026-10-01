package dev.afunk.surfcraft.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.afunk.surfcraft.movement.RampCollision;
import dev.afunk.surfcraft.movement.SurfPlayer;
import dev.afunk.surfcraft.movement.Surfing;
import dev.afunk.surfcraft.physics.Config;
import dev.afunk.surfcraft.physics.SourceUnits;
import dev.afunk.surfcraft.physics.TickDriver;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server side of surfing: the surf window ({@link Surfing}) and what it allows, per move packet. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	/** How far (ms) a surfer's move packets may run ahead of real time: a stall's backlog passes, a flood does not. */
	@Unique
	private static final long BACKLOG = 5000;
	/** How much CS:S gravity changes the vertical movement per tick, in blocks (no drag). */
	@Unique
	private static final double DROP = Config.CSS_SURF.gravity() * TickDriver.TICK * TickDriver.TICK / SourceUnits.PER_BLOCK;

	@Shadow
	public ServerPlayer player;
	@Shadow
	private double lastGoodX, lastGoodY, lastGoodZ;
	@Shadow
	private boolean clientIsFloating;
	/** When this surfer's move packets would be on time (real time, ms), counting 50 ms per packet. */
	@Unique
	private long surfcraft$due;
	/** The last accepted move's vertical step. */
	@Unique
	private double surfcraft$lastDy;

	/** Every move first updates the surf window: the checks below and the re-simulation (e2) judge it by that. */
	@Inject(method = "handlePlayerPositionChange", at = @At("HEAD"))
	private void surfcraft$window(double x, double y, double z, float yRot, float xRot, boolean onGround, boolean wall, CallbackInfo ci) {
		Surfing.move(player, RampCollision.surfNear(player, Math.sqrt(Mth.lengthSquared(x - lastGoodX, y - lastGoodY, z - lastGoodZ))), onGround);
	}

	/**
	 * B1: "moved too quickly" budgets the k-th move packet within a server tick 100k square blocks from the tick's first
	 * position, but only 100 once k passes 5, so a stall or a client hitch that delivers 6 packets at once stops a surfer
	 * above 1312 u/s dead. A surfer gets vanilla's single-packet budget (10 blocks; CS:S tops out at 7.7 per tick) for every
	 * packet instead, measured from its last accepted position, while its packets run at most {@link #BACKLOG} ahead of
	 * real time. Anything else is vanilla's call.
	 */
	@WrapOperation(method = "handlePlayerPositionChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;shouldCheckPlayerMovement(Z)Z"))
	private boolean surfcraft$surfBudget(ServerGamePacketListenerImpl self, boolean gliding, Operation<Boolean> original, @Local(argsOnly = true, ordinal = 0) double x,
			@Local(argsOnly = true, ordinal = 1) double y, @Local(argsOnly = true, ordinal = 2) double z) {
		if (!((SurfPlayer) player).surfcraft$surfing()) return original.call(self, gliding);
		long now = Util.getMillis();
		surfcraft$due = Math.max(surfcraft$due, now) + 50;
		return (surfcraft$due - now > BACKLOG || Mth.lengthSquared(x - lastGoodX, y - lastGoodY, z - lastGoodZ) > 100) && original.call(self, gliding);
	}

	/**
	 * B7: a move whose vertical step follows CS:S gravity from the last one is a fall, not floating, however long the
	 * airtime after a launch (vanilla kicks after 80 ticks of not descending with nothing around).
	 */
	@Inject(method = "handlePlayerPositionChange", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z", opcode = Opcodes.PUTFIELD,
			shift = At.Shift.AFTER))
	private void surfcraft$fallingIsNotFloating(CallbackInfo ci) {
		double dy = player.getY() - lastGoodY;
		if (Math.abs(dy - surfcraft$lastDy + DROP) < 0.01) clientIsFloating = false;
		surfcraft$lastDy = dy;
	}

	/**
	 * B9: the server's own zero-input simulation lands a surfer on the slope, which it then counts as ground, so it refused
	 * an elytra the client (CS:S: ramps are not ground) had deployed. A surfer's ground state is the one its client reported.
	 */
	@WrapOperation(method = "tickPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V"))
	private void surfcraft$clientGround(ServerPlayer self, Operation<Void> original) {
		boolean onGround = self.onGround();
		original.call(self);
		if (((SurfPlayer) self).surfcraft$surfing()) self.setOnGround(onGround);
	}

	/** A teleport (an ender pearl, a correction, a command) closes the surf window: the controller starts over too. */
	@Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
	private void surfcraft$teleported(PositionMoveRotation destination, Set<Relative> relatives, CallbackInfo ci) {
		Surfing.set(player, 0);
	}

	/**
	 * e1: the server guesses a jump when its simulation put the player on the ground and a packet then moves up without
	 * ground. Near ramps that is just surfing (its own simulation lands on the slope every tick), so no jump stats or hunger.
	 */
	@WrapWithCondition(method = "handlePlayerPositionChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
	private boolean surfcraft$noJumpsOnRamps(ServerPlayer player) {
		return !RampCollision.near(player, Vec3.ZERO);
	}
}
