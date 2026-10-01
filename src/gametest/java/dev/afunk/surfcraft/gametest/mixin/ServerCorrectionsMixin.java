package dev.afunk.surfcraft.gametest.mixin;

import dev.afunk.surfcraft.gametest.ServerCorrections;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Test oracle: every time the server teleports a player back while handling a move packet (moved too quickly, moved
 * wrongly, or the silent "new collision" rejection), with the shapes the target box ran into.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerCorrectionsMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handlePlayerPositionChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;teleport(DDDFF)V"))
	private void surfcraftTest$correction(double x, double y, double z, float yRot, float xRot, boolean onGround, boolean horizontalCollision, CallbackInfo ci) {
		ServerCorrections.record("correction of " + player.getPlainTextName() + " from " + player.position() + " (target " + x + ", " + y + ", " + z + ")");
	}

	@Inject(method = "isEntityCollidingWithAnythingNew", at = @At("RETURN"))
	private void surfcraftTest$newCollision(LevelReader level, Entity entity, AABB oldAABB, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) return;
		AABB box = entity.getBoundingBox().move(x - entity.getX(), y - entity.getY(), z - entity.getZ());
		StringBuilder hit = new StringBuilder();
		for (var shape : level.getPreMoveCollisions(entity, box.deflate(1.0E-5F), oldAABB.getBottomCenter())) hit.append(' ').append(shape.bounds());
		ServerCorrections.record("new collision for box " + box + ":" + hit);
	}
}
