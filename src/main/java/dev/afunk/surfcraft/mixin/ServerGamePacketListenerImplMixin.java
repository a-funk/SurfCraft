package dev.afunk.surfcraft.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.afunk.surfcraft.movement.RampCollision;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * e1: the server guesses a jump when its simulation put the player on the ground and a packet then moves up without
 * ground. Near ramps that is just surfing (its own simulation lands on the slope every tick), so no jump stats or hunger.
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	@WrapWithCondition(method = "handlePlayerPositionChange", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;jumpFromGround()V"))
	private boolean surfcraft$noJumpsOnRamps(ServerPlayer player) {
		return !RampCollision.near(player, Vec3.ZERO);
	}
}
