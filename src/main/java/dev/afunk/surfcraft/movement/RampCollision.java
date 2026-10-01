package dev.afunk.surfcraft.movement;

import dev.afunk.surfcraft.physics.Brush;
import dev.afunk.surfcraft.physics.ExactCollide;
import dev.afunk.surfcraft.physics.SourceMovement;
import dev.afunk.surfcraft.physics.SourceUnits;
import dev.afunk.surfcraft.physics.V3;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Exact collision for players near surf ramps, on both sides: the client's vanilla-driven moves (flying, swimming), the
 * server's re-simulation of every move packet and its own per-tick simulation all see the true slopes instead of the
 * ramps' inscribed staircases. Stateless: the client and integrated server threads call it at the same time.
 */
public final class RampCollision {
	static final double K = SourceUnits.PER_BLOCK;
	/** Blocks around a move within which a ramp switches a player to exact collision. */
	static final double MARGIN = 0.5;
	/** The CS:S ground probe distance (2 units), as the reach for slope contact. */
	static final double CONTACT = 2 / K;
	/**
	 * The longest move (blocks) handled here: twice the controller's top speed (3500 units/s on each axis, 7.7 blocks a
	 * tick), as a tick's 3-4 substeps plus its publishing sweep can cover up to 0.08 s of travel.
	 */
	static final double MAX_MOVE = 2 * 3500 * Math.sqrt(3) * 0.05 / K;

	private RampCollision() {
	}

	/**
	 * {@code Entity.collide} for a player: the controller's trusted movement, or the exact resolution near ramps, or null
	 * (vanilla) away from them. The server's re-simulation ({@link MoverType#PLAYER}) may lift the box over an edge the
	 * client crossed within the tick. Moves longer than the controller can publish in a tick are left to vanilla: the
	 * lifted region (and its cost) grows with the square of the length.
	 */
	public static @Nullable Vec3 collide(Player player, Vec3 movement, MoverType type) {
		Vec3 trusted = ((SurfPlayer) player).surfcraft$trust();
		if (trusted != null) return trusted;
		if (player.noPhysics || movement.lengthSqr() > MAX_MOVE * MAX_MOVE) return null;
		boolean lift = type == MoverType.PLAYER;
		double up = player.maxUpStep() + (lift ? movement.horizontalDistance() / 2 : 0);
		AABB reach = player.getBoundingBox().expandTowards(movement).expandTowards(0, up, 0).inflate(MARGIN);
		if (!BrushWorld.rampNear(player.level(), reach)) return null;
		BlockPos anchor = player.blockPosition();
		List<Brush> world = BrushWorld.collect(player.level(), player, reach, anchor, false);
		V3 delta = BrushWorld.toSourceDelta(movement);
		V3 got = ExactCollide.resolve(world, BrushWorld.toSource(player.position(), anchor), delta, BrushWorld.hull(player), player.maxUpStep() * K,
				player.onGround(), lift);
		return got == delta ? movement : BrushWorld.toMinecraftDelta(got);
	}

	/** A surf ramp within reach of this move (for the server's jump detection). */
	public static boolean near(Player player, Vec3 movement) {
		return BrushWorld.rampNear(player.level(), player.getBoundingBox().expandTowards(movement).inflate(MARGIN + 0.5));
	}

	/** Blocks beyond a tick's travel within which a ramp brings in the surf controller (and opens the server's surf window). */
	public static final double SURF_MARGIN = 1.5;
	/** Ticks on other ground after leaving a ramp before vanilla movement resumes (and the surf window closes). */
	public static final int GRACE = 10;

	/** A surf ramp within this tick's travel ({@code speed}, blocks) plus {@link #SURF_MARGIN} of the player's box: the controller's rule, on both sides. */
	public static boolean surfNear(Player player, double speed) {
		return BrushWorld.rampNear(player.level(), player.getBoundingBox().inflate(speed + SURF_MARGIN));
	}

	/**
	 * The player rests on a ramp's slope: moved 2 units down (the CS:S ground probe), its box first meets a plane of surf
	 * steepness (0.01 < normal z < 0.7) on some ramp brush. Flat tops (full cells, a lone cell's flat back) are ground and
	 * sides are walls, as in CS:S.
	 */
	public static boolean onSlope(Player player) {
		AABB below = player.getBoundingBox().expandTowards(0, -CONTACT, 0);
		if (!BrushWorld.rampNear(player.level(), below)) return false;
		BlockPos anchor = player.blockPosition();
		V3 hull = BrushWorld.hull(player), centre = BrushWorld.toSource(player.position(), anchor).add(new V3(0, 0, hull.z())), down = centre.sub(new V3(0, 0, 2));
		for (Brush b : BrushWorld.collect(player.level(), player, below, anchor, true)) {
			double nz = ExactCollide.first(List.of(b), centre, down, hull, ExactCollide.TOL, 0).normal().z();
			if (nz > 0.01 && nz < SourceMovement.GROUND_NORMAL_Z) return true;
		}
		return false;
	}
}
