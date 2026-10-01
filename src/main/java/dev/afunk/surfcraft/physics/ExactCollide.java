package dev.afunk.surfcraft.physics;

import java.util.List;
import java.util.TreeSet;

/**
 * {@code Entity.collide} for a player box among exact brushes (Source units, the box given by its centre and half
 * extents). Unlike Source's trace there is no DIST_EPSILON: the box stops where it touches a plane, a start up to
 * {@link #TOL} inside one counts as touching, and it may always leave a brush it already overlaps (as vanilla).
 *
 * <p>The straight move is kept whenever the swept box stays clear: a client surfing a ramp ends every tick on or above
 * one plane, so the chord between two of its published positions is clear and the server's re-simulation lands exactly
 * on the client's target. Otherwise the move resolves like vanilla, axis by axis (up/down first, then the longer
 * horizontal axis), with vanilla's step-up. The server's re-simulation of a client's move is at least as lenient as the
 * client's own trace (Source's trace lets a box graze an edge within DIST_EPSILON), and may also lift the box over a
 * convex edge it crossed within the tick (a chord between two slopes of an A-frame passes under the apex).
 */
public final class ExactCollide {
	/** Penetration that does not count as a hit, in units (2.5e-6 blocks): rounding between client floats and server doubles. */
	public static final double TOL = 1e-4;

	private ExactCollide() {
	}

	/** First contact along a sweep: the fraction of the way, and the normal of the plane crossed (zero if none). */
	public record Hit(double fraction, V3 normal) {
	}

	/**
	 * Where the box (centre, half extents) moving from {@code from} to {@code to} first touches a brush it would otherwise
	 * cross more than {@code tol} into (and did not start inside). The tolerance only decides whether there is a hit: the
	 * box stops where it touches, never inside, or a later move could start "inside" the brush and pass through it. A start
	 * at most {@code slack} inside a brush counts as touching it: the box may slide along or back out, not go deeper.
	 */
	public static Hit first(List<Brush> world, V3 from, V3 to, V3 half, double tol, double slack) {
		double sx = from.x(), sy = from.y(), sz = from.z(), ex = to.x(), ey = to.y(), ez = to.z();
		double hx = half.x(), hy = half.y(), hz = half.z();
		double minX = Math.min(sx, ex) - hx, maxX = Math.max(sx, ex) + hx, minY = Math.min(sy, ey) - hy, maxY = Math.max(sy, ey) + hy;
		double minZ = Math.min(sz, ez) - hz, maxZ = Math.max(sz, ez) + hz;
		double fraction = 1;
		Plane hit = null;
		brushes:
		for (Brush brush : world) {
			V3 lo = brush.min(), hi = brush.max();
			if (minX >= hi.x() || maxX <= lo.x() || minY >= hi.y() || maxY <= lo.y() || minZ >= hi.z() || maxZ <= lo.z()) continue;
			double t = tol;
			if (slack > 0) {
				// How far the start is outside the brush (negative: inside, by the shallowest face).
				double out = Double.NEGATIVE_INFINITY;
				for (Plane p : brush.planes())
					out = Math.max(out, sx * p.nx() + sy * p.ny() + sz * p.nz() - p.d() - Math.abs(p.nx()) * hx - Math.abs(p.ny()) * hy - Math.abs(p.nz()) * hz);
				if (out < -tol && out >= -slack) t = tol - out;
			}
			double enter = 0, leave = 1, touch = 0;
			Plane entered = null;
			boolean startsOutside = false;
			for (Plane p : brush.planes()) {
				double dist = p.d() + Math.abs(p.nx()) * hx + Math.abs(p.ny()) * hy + Math.abs(p.nz()) * hz;
				double a = sx * p.nx() + sy * p.ny() + sz * p.nz() - dist, b = ex * p.nx() + ey * p.ny() + ez * p.nz() - dist;
				if (a >= -t) {
					startsOutside = true;
					if (b >= -t) continue brushes;
					double f = (a + t) / (a - b);
					if (entered == null || f > enter) {
						enter = f;
						entered = p;
					}
					touch = Math.max(touch, a / (a - b));
				} else if (b >= -t) leave = Math.min(leave, (a + t) / (a - b));
			}
			if (startsOutside && enter < leave && touch < fraction) {
				fraction = touch;
				hit = entered;
			}
		}
		return new Hit(fraction, hit == null ? V3.ZERO : hit.normal());
	}

	/**
	 * The fraction of the way from {@code from} to {@code to} at which the box first crosses into a brush it did not
	 * start inside; 1 if it never does.
	 */
	public static double sweep(List<Brush> world, V3 from, V3 to, V3 half) {
		return first(world, from, to, half, TOL, 0).fraction();
	}

	/** True when the box moves from {@code from} to {@code to} without crossing into anything. */
	public static boolean clear(List<Brush> world, V3 from, V3 to, V3 half) {
		return sweep(world, from, to, half) == 1;
	}

	/**
	 * The movement of a box with its feet at {@code feet}: {@code delta} if the straight sweep is clear, else resolved axis
	 * by axis. {@code step} is the step height; {@code onGround} the entity's ground flag (for vanilla's step-up rule).
	 * {@code lift} (only for checking a client's move): the straight move also counts as clear when Source's trace passes
	 * it or the box shrunk by DIST_EPSILON does; and when the resolved move falls short horizontally, also try lifting the
	 * box by up to {@code step + delta's horizontal length / 2} before moving across.
	 */
	public static V3 resolve(List<Brush> world, V3 feet, V3 delta, V3 half, double step, boolean onGround, boolean lift) {
		V3 centre = feet.add(new V3(0, 0, half.z())), end = centre.add(delta);
		if (delta.isZero() || clear(world, centre, end, half)) return delta;
		if (lift) {
			HullTrace source = SourceHull.trace(world, centre, end, half);
			V3 e = new V3(SourceHull.EPSILON, SourceHull.EPSILON, SourceHull.EPSILON);
			if (source.fraction() == 1 && !source.startSolid() || clear(world, centre, end, half.sub(e))) return delta;
		}
		V3 moved = axes(world, centre, delta, half);
		boolean blockedX = moved.x() != delta.x(), blockedY = moved.y() != delta.y(), landed = moved.z() != delta.z() && delta.z() < 0;
		if (!blockedX && !blockedY) return moved;
		if (lift) return lift(world, centre, delta, half, moved, step + Math.hypot(delta.x(), delta.y()) / 2);
		if (step > 0 && (landed || onGround)) {
			// Vanilla's step-up: the lowest collider height within reach that gets further across wins.
			V3 grounded = landed ? centre.add(new V3(0, 0, moved.z())) : centre;
			for (double h : heights(world, grounded.z() - half.z(), step, centre, delta, half)) {
				if (h == moved.z()) continue;
				V3 up = axes(world, grounded, new V3(delta.x(), delta.y(), h), half);
				if (flatSq(up) > flatSq(moved)) return up.add(new V3(0, 0, landed ? moved.z() : 0));
			}
		}
		return moved;
	}

	/** Vanilla's order: up/down first, then the longer horizontal axis, then the other. */
	static V3 axes(List<Brush> world, V3 centre, V3 delta, V3 half) {
		double z = delta.z() == 0 ? 0 : delta.z() * sweep(world, centre, centre.add(new V3(0, 0, delta.z())), half);
		V3 c = centre.add(new V3(0, 0, z));
		double x, y;
		if (Math.abs(delta.x()) >= Math.abs(delta.y())) {
			x = delta.x() == 0 ? 0 : delta.x() * sweep(world, c, c.add(new V3(delta.x(), 0, 0)), half);
			c = c.add(new V3(x, 0, 0));
			y = delta.y() == 0 ? 0 : delta.y() * sweep(world, c, c.add(new V3(0, delta.y(), 0)), half);
		} else {
			y = delta.y() == 0 ? 0 : delta.y() * sweep(world, c, c.add(new V3(0, delta.y(), 0)), half);
			c = c.add(new V3(0, y, 0));
			x = delta.x() == 0 ? 0 : delta.x() * sweep(world, c, c.add(new V3(delta.x(), 0, 0)), half);
		}
		return new V3(x, y, z);
	}

	/** Lifted candidates (collider heights within reach, then the full lift), keeping the one that gets furthest across. */
	private static V3 lift(List<Brush> world, V3 centre, V3 delta, V3 half, V3 best, double maxLift) {
		double want = flatSq(delta);
		TreeSet<Double> lifts = heights(world, centre.z() - half.z(), maxLift, centre, delta, half);
		lifts.add(maxLift);
		for (double h : lifts) {
			if (h <= 0) continue;
			double up = h * sweep(world, centre, centre.add(new V3(0, 0, h)), half);
			V3 across = axes(world, centre.add(new V3(0, 0, up)), new V3(delta.x(), delta.y(), 0), half);
			if (flatSq(across) > flatSq(best)) best = new V3(across.x(), across.y(), up);
			if (flatSq(best) >= want * (1 - 1e-12)) break;
		}
		return best;
	}

	/** Heights of collider tops and bottoms (relative to {@code bottom}) in [0, max], among brushes near the move. */
	private static TreeSet<Double> heights(List<Brush> world, double bottom, double max, V3 centre, V3 delta, V3 half) {
		TreeSet<Double> out = new TreeSet<>();
		double r = Math.hypot(delta.x(), delta.y());
		for (Brush b : world) {
			if (b.max().x() < centre.x() - half.x() - r || b.min().x() > centre.x() + half.x() + r || b.max().y() < centre.y() - half.y() - r
					|| b.min().y() > centre.y() + half.y() + r) continue;
			for (double z : new double[] {b.min().z(), b.max().z()}) {
				double h = z - bottom;
				if (h >= 0 && h <= max) out.add(h);
			}
		}
		return out;
	}

	private static double flatSq(V3 v) {
		return v.x() * v.x() + v.y() * v.y();
	}
}
