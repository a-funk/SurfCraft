package dev.afunk.surfcraft.movement;

import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Added to every {@code Player} (mixin). It lives on the player (never in a static) because the client and the integrated
 * server move players on different threads.
 *
 * <p>The surf controller sets the trust payload around its own {@code move} call: the exact movement it already traced,
 * which {@code Entity.collide} then returns unchanged.
 *
 * <p>The surf window is the server's view of whom the controller drives (see {@link Surfing}); on a client, what the
 * server last said about another player.
 */
public interface SurfPlayer {
	@Nullable Vec3 surfcraft$trust();

	void surfcraft$setTrust(@Nullable Vec3 movement);

	/** Moves on the ground left before the surf window closes; 0 while it is closed. */
	int surfcraft$window();

	void surfcraft$setWindow(int moves);

	default boolean surfcraft$surfing() {
		return surfcraft$window() > 0;
	}
}
