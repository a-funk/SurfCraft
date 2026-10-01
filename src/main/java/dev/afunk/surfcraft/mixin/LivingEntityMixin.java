package dev.afunk.surfcraft.mixin;

import dev.afunk.surfcraft.movement.RampCollision;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	/**
	 * c1: touching a ramp resets the fall distance (both sides: the client's move and the server's check of each move
	 * packet), and that tick's descent is not added. Ramps are not ground (CS:S), so sliding down one never hurts; a fall
	 * onto flat ground after leaving a ramp counts from the last contact.
	 */
	@ModifyVariable(method = "checkFallDamage", at = @At("HEAD"), argsOnly = true)
	private double surfcraft$rampContactResetsFall(double ya) {
		if ((Object) this instanceof Player player && RampCollision.touchesRamp(player)) {
			if (player.fallDistance != 0) player.resetFallDistance();
			return Math.max(ya, 0);
		}
		return ya;
	}

	/**
	 * e3: damage (anything but drowning) sends the server's velocity to the client, and knockback starts from it. For a
	 * surfer that is the server's zero-input simulation, so every hit, even fall damage when landing from a ramp, would
	 * stop them dead. Near ramps, or faster than any vanilla movement on foot, the server takes the player's actual
	 * movement (the last move the client reported) as its velocity first.
	 */
	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void surfcraft$surferMomentum(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		if (!((Object) this instanceof ServerPlayer player) || player.isFallFlying()) return;
		Vec3 known = player.getKnownMovement();
		if (known.horizontalDistance() > 0.6 || RampCollision.near(player, known)) player.setDeltaMovement(known);
	}
}
