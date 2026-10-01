package dev.afunk.surfcraft.movement;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Added to every {@code Player} (mixin). The surf controller sets the trust payload around its own {@code move} call:
 * the exact movement it already traced, which {@code Entity.collide} then returns unchanged. It lives on the player
 * (never in a static) because the client and the integrated server move players on different threads.
 */
public interface SurfPlayer {
	@Nullable Vec3 surfcraft$trust();

	void surfcraft$setTrust(@Nullable Vec3 movement);
}
