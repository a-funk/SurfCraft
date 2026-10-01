package dev.afunk.surfcraft.mixin;

import dev.afunk.surfcraft.movement.RampCollision;
import dev.afunk.surfcraft.movement.SurfPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/**
	 * c1: resting on a ramp's slope resets the fall distance (both sides: the client's move and the server's check of each
	 * move packet), and that tick's descent is not added. Slopes are not ground (CS:S), so sliding down one never hurts; a
	 * fall onto flat ground after leaving one counts from the last contact. Flat tops and sides of ramp blocks are ground and
	 * walls like any block's.
	 */
	@ModifyVariable(method = "checkFallDamage", at = @At("HEAD"), argsOnly = true)
	private double surfcraft$slopeContactResetsFall(double ya) {
		if ((Object) this instanceof Player player && RampCollision.onSlope(player)) {
			if (player.fallDistance != 0) player.resetFallDistance();
			return Math.max(ya, 0);
		}
		return ya;
	}

	/**
	 * e3: damage (anything but drowning) sends the server's velocity to the client, and knockback starts from it. For a
	 * surfer that is the server's zero-input simulation, so every hit, even fall damage when landing from a ramp, would
	 * stop them dead. In the surf window the server takes the player's actual movement (the last move the client reported)
	 * as its velocity first; a teleport closes the window, so a pearl's damage never brings back the movement before it.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void surfcraft$surferMomentum(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ServerPlayer player && !player.isFallFlying() && ((SurfPlayer) player).surfcraft$surfing()) {
			player.setDeltaMovement(player.getKnownMovement());
		}
	}
}
