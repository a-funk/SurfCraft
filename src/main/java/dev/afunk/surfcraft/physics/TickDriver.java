package dev.afunk.surfcraft.physics;

import java.util.List;

/**
 * Runs {@link SourceMovement} from 50 ms Minecraft ticks: 0.015 s substeps through a time accumulator, the view yaw
 * interpolated across them from the previous tick's yaw, and the state swept forward to the tick boundary for
 * publishing (so 3- and 4-substep ticks don't judder). The in-game controller and the offline replay both use this
 * class, so a replay of recorded inputs reproduces the published positions exactly.
 */
public final class TickDriver {
	public static final double TICK = 0.05;
	public static final double DT = (double) 0.015f;

	public final SourceMovement core = new SourceMovement();
	/** Simulated time the core state lags behind the tick boundary, in [0, DT). */
	public double lag;
	/** Source yaw at the last tick boundary, in degrees; NaN until the first tick. */
	public double yaw = Double.NaN;
	/**
	 * The feet swept forward to the tick boundary: the position Minecraft shows. On a tick that {@link #landed} and ended
	 * airborne again, the touchdown instead, carried along the ground to the tick boundary's spot.
	 */
	public V3 published = V3.ZERO;
	/** Some substep this tick touched a surf ramp (0.01 < normal z < 0.7). */
	public boolean surfed;
	/** Some substep this tick hit a wall or overhang (normal z <= 0.01, above -0.7). */
	public boolean wall;
	/** Some substep this tick hit a ceiling. */
	public boolean ceiling;
	/**
	 * Some substep this tick landed: it ended on walkable ground after the one before it ended airborne. With auto hop the
	 * next substep can already jump, so the tick may end airborne; the landing is still this tick's (CS:S takes fall damage
	 * in the landing command, vanilla on the tick that lands).
	 */
	public boolean landed;
	/** The hull started this tick inside something no nudge could get it out of. */
	public boolean stuck;

	/** Nudges tried, in units, when the hull starts inside or exactly against a brush: up first, then sideways. */
	private static final V3[] NUDGES;

	static {
		double[] up = {1.0 / 32, 1.0 / 16, 1.0 / 8, 1.0 / 4, 1.0 / 2, 1, 2, 4, 8, 16}, side = {1.0 / 8, 1.0 / 2, 2, 8};
		NUDGES = new V3[up.length + side.length * 4];
		int i = 0;
		for (double z : up) NUDGES[i++] = new V3(0, 0, z);
		for (double s : side) for (V3 v : new V3[] {new V3(s, 0, 0), new V3(-s, 0, 0), new V3(0, s, 0), new V3(0, -s, 0)}) NUDGES[i++] = v;
	}

	/**
	 * Source's CheckStuck, simplified: Source counts a hull touching a plane as solid, but Minecraft stands players exactly
	 * on block tops, so a core placed from a Minecraft position starts all-solid and would never move. Moves the core to
	 * the first clear nudge; false if it is stuck where no nudge helps.
	 */
	boolean unstick(List<Brush> world, V3 hull) {
		V3 centre = core.origin.add(new V3(0, 0, hull.z()));
		if (!SourceHull.trace(world, centre, centre, hull).startSolid()) return true;
		for (V3 nudge : NUDGES) {
			V3 c = centre.add(nudge);
			if (!SourceHull.trace(world, c, c, hull).startSolid()) {
				core.setOrigin(core.origin.add(nudge));
				return true;
			}
		}
		return false;
	}

	/**
	 * One Minecraft tick. {@code forward}/{@code side} are command units (side positive to the right), {@code yawNow}
	 * the Source yaw at this tick (degrees, unwrapped; the turn since the last tick is taken the short way round).
	 */
	public void tick(Config c, double forward, double side, boolean jump, double yawNow, V3 hull, List<Brush> world) {
		double from = Double.isNaN(yaw) ? yawNow : yaw, turn = Math.IEEEremainder(yawNow - from, 360);
		surfed = wall = ceiling = landed = false;
		stuck = !unstick(world, hull);
		if (stuck) {
			published = core.origin;
			return;
		}
		lag += TICK;
		V3 touchdown = null;
		while (lag >= DT) {
			lag -= DT;
			boolean airborne = !core.grounded;
			core.tick(c, forward, side, from + turn * (1 - lag / TICK), jump, DT, hull, world);
			if (airborne && core.grounded && touchdown == null) touchdown = core.origin;
			surfed |= core.surfNormal != null;
			for (HullTrace h : core.contacts) {
				double nz = h.normal().z();
				if (nz < -0.7) ceiling = true;
				else if (nz <= 0.01) wall = true;
			}
		}
		yaw = yawNow;
		landed = touchdown != null;
		published = sweep(world, core.origin, core.velocity.scale(lag), hull);
		// Landed and hopped again within the tick: publish the touchdown, carried along the ground to the tick boundary's
		// spot, so the tick's packet is the landing (on the ground, after the whole fall), as vanilla's would be.
		if (landed && !core.grounded) published = sweep(world, touchdown, new V3(published.x() - touchdown.x(), published.y() - touchdown.y(), 0), hull);
	}

	/** Penetration the publishing sweep ignores, in units (it stops where the hull touches): below the server's TOL. */
	static final double PUBLISH_TOL = 1e-5;
	/**
	 * A start this far inside a brush (units) counts as touching it: float origins put the core up to half a float ulp
	 * (2.4e-4 units below 8192) inside a face it reached without crossing, and the sweep must not go deeper from there.
	 */
	static final double PUBLISH_SLACK = 1e-3;

	/**
	 * The feet moved by {@code move}, sliding along whatever the hull touches on the way and never deeper into a brush than
	 * it started. This uses the exact sweep, not Source's trace: a hull resting within DIST_EPSILON of a ramp (as surfing
	 * hulls do) gets a hit at fraction 0 from Source's trace for any move into it, which would freeze the extrapolation;
	 * the exact sweep slides along instead.
	 */
	public static V3 sweep(List<Brush> world, V3 feet, V3 move, V3 hull) {
		V3 lift = new V3(0, 0, hull.z()), centre = feet.add(lift), rest = move;
		for (int bump = 0; bump < 4 && !rest.isZero(); bump++) {
			ExactCollide.Hit hit = ExactCollide.first(world, centre, centre.add(rest), hull, PUBLISH_TOL, PUBLISH_SLACK);
			centre = centre.add(rest.scale(hit.fraction()));
			if (hit.fraction() == 1) break;
			rest = rest.scale(1 - hit.fraction());
			double into = rest.dot(hit.normal());
			// Keep what runs along the plane, leaning out by a hair so the next sweep starts clear of it.
			rest = rest.sub(hit.normal().scale(Math.min(into, 0) - 1e-7));
		}
		return centre.sub(lift);
	}

	/** Where the hull may be this tick: around the core's feet, out to everything it can reach in one tick. */
	public V3[] reach(V3 hull, double margin) {
		double r = core.velocity.length() * TICK + margin;
		V3 centre = core.origin.add(new V3(0, 0, hull.z())), half = hull.add(new V3(r, r, r));
		return new V3[] {centre.sub(half), centre.add(half)};
	}

	/** An independent copy (for replays). */
	public TickDriver copy() {
		TickDriver d = new TickDriver();
		SourceMovement a = core, b = d.core;
		b.origin = a.origin;
		b.velocity = a.velocity;
		b.grounded = a.grounded;
		b.stamina = a.stamina;
		b.surfaceFriction = a.surfaceFriction;
		b.jumpHeld = a.jumpHeld;
		b.surfNormal = a.surfNormal;
		b.contacts = a.contacts;
		b.ground = a.ground;
		d.lag = lag;
		d.yaw = yaw;
		d.published = published;
		d.surfed = surfed;
		d.wall = wall;
		d.ceiling = ceiling;
		d.landed = landed;
		d.stuck = stuck;
		return d;
	}
}
