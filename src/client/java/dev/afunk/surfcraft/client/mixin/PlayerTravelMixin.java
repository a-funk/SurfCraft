package dev.afunk.surfcraft.client.mixin;

import dev.afunk.surfcraft.client.SurfController;
import dev.afunk.surfcraft.client.SurfDriven;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** a2: the controller moves the local player instead of vanilla travel (Player is shared: server players pass through). */
@Mixin(Player.class)
abstract class PlayerTravelMixin {
	@Inject(method = "travel", at = @At("HEAD"), cancellable = true)
	private void surfcraft$surf(Vec3 input, CallbackInfo ci) {
		if (!((Object) this instanceof LocalPlayer player)) return;
		SurfController controller = ((SurfDriven) player).surfcraft$controller();
		if (controller.driving() && controller.travel()) ci.cancel();
	}
}
