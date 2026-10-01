package dev.afunk.surfcraft.mixin;

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

/** The controller's trust payload and the server's surf window (see {@link SurfPlayer}), and e2: no sneak edge back-off for surfers. */
@Mixin(Player.class)
abstract class PlayerMixin implements SurfPlayer {
	@Unique
	private @Nullable Vec3 surfcraft$trust;
	@Unique
	private int surfcraft$window;

	@Override
	public @Nullable Vec3 surfcraft$trust() {
		return surfcraft$trust;
	}

	@Override
	public void surfcraft$setTrust(@Nullable Vec3 movement) {
		surfcraft$trust = movement;
	}

	@Override
	public int surfcraft$window() {
		return surfcraft$window;
	}

	@Override
	public void surfcraft$setWindow(int moves) {
		surfcraft$window = moves;
	}

	/**
	 * Sneaking shrinks moves that could drop off a ledge, judged against vanilla shapes (the ramps' staircases). The
	 * controller's own moves are never backed off (it hands sneaking on flat ground to vanilla), so the server's
	 * re-simulation must not back off a surfer's moves either, or it rubber-bands them: in the surf window, which opens
	 * by the controller's own reach. Vanilla moves keep their back-off, near ramps too.
	 */
	@Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
	private void surfcraft$noBackOffForSurfers(Vec3 delta, MoverType type, CallbackInfoReturnable<Vec3> cir) {
		if (((Player) (Object) this).isShiftKeyDown() && (surfcraft$trust != null || surfcraft$window > 0)) cir.setReturnValue(delta);
	}
}
