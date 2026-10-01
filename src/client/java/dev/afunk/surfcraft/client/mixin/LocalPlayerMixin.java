package dev.afunk.surfcraft.client.mixin;

import dev.afunk.surfcraft.client.SurfController;
import dev.afunk.surfcraft.client.SurfDriven;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The local player's surf controller (a new player, after a respawn or world change, gets a new one), a1: decide each
 * tick after the input is applied (and keep vanilla from jumping), a3: no auto-jump while driving.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin implements SurfDriven {
	@Unique
	private SurfController surfcraft$controller;

	@Override
	public SurfController surfcraft$controller() {
		if (surfcraft$controller == null) surfcraft$controller = new SurfController((LocalPlayer) (Object) this);
		return surfcraft$controller;
	}

	@Inject(method = "applyInput", at = @At("TAIL"))
	private void surfcraft$decide(CallbackInfo ci) {
		surfcraft$controller().decide();
	}

	@Inject(method = "canAutoJump", at = @At("HEAD"), cancellable = true)
	private void surfcraft$noAutoJump(CallbackInfoReturnable<Boolean> cir) {
		if (surfcraft$controller().driving()) cir.setReturnValue(false);
	}
}
