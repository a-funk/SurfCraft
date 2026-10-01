package dev.afunk.surfcraft.mixin;

import dev.afunk.surfcraft.movement.RampCollision;
import dev.afunk.surfcraft.movement.SurfPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The controller's trust payload (see {@link SurfPlayer}), and e2: no sneak edge back-off near ramps (both sides). */
@Mixin(Player.class)
abstract class PlayerMixin implements SurfPlayer {
	@Unique
	private @Nullable Vec3 surfcraft$trust;

	@Override
	public @Nullable Vec3 surfcraft$trust() {
		return surfcraft$trust;
	}

	@Override
	public void surfcraft$setTrust(@Nullable Vec3 movement) {
		surfcraft$trust = movement;
	}

	/**
	 * Sneaking shrinks moves that could drop off a ledge, judged against vanilla shapes (the ramps' staircases): on a ramp
	 * the server's re-simulation would shrink a surfer's move the client never shrank, and rubber-band. The controller's own
	 * moves are never backed off either.
	 */
	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void surfcraft$noBackOffNearRamps(Vec3 delta, MoverType type, CallbackInfoReturnable<Vec3> cir) {
		Player self = (Player) (Object) this;
		if (self.isShiftKeyDown() && (surfcraft$trust != null || RampCollision.near(self, delta))) cir.setReturnValue(delta);
	}
}
