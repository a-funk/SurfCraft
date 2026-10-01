package dev.afunk.surfcraft.client.mixin;

import dev.afunk.surfcraft.client.SurfController;
import dev.afunk.surfcraft.client.SurfDriven;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * d1: vanilla swings the legs by distance moved, saturating at a quarter block per tick, so a surfer's legs would run
 * flat out. Sliding or airborne under the controller, they settle instead.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityAnimationMixin {
	@Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
	private void surfcraft$glide(boolean useY, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) return;
		SurfController controller = ((SurfDriven) player).surfcraft$controller();
		if (!controller.driving() || controller.driver() == null || controller.driver().core.grounded) return;
		player.walkAnimation.update(0, 0.4f, 1);
		ci.cancel();
	}
}
