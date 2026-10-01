package dev.afunk.surfcraft.physics;

import java.util.List;

/**
 * Source's box trace against BSP brushes (CM_ClipBoxToBrush), ported from the surf repo's
 * {@code traceSourceHull} plane branch: each plane is pushed out by the box's support distance (a Minkowski sum
 * that relies on the brush's bevels) and the box stops {@link #EPSILON} short of the plane it enters.
 */
public final class SourceHull {
	/** DIST_EPSILON. */
	public static final double EPSILON = 1.0 / 32;

	private SourceHull() {
	}

	/**
	 * Sweeps the axis-aligned box with half extents {@code half} whose centre moves from {@code start} to
	 * {@code end}. On equal fractions the earlier brush in {@code world} wins, except that, as in Source, a later
	 * brush whose raw (epsilon backed-off) entry fraction is negative replaces an earlier hit clamped to 0.
	 */
	public static HullTrace trace(List<Brush> world, V3 start, V3 end, V3 half) {
		double sx = start.x(), sy = start.y(), sz = start.z(), ex = end.x(), ey = end.y(), ez = end.z();
		double hx = half.x(), hy = half.y(), hz = half.z();
		// Broad phase: the swept box's bounds grown by EPSILON, as the reference's AABB query.
		double cx = (sx + ex) * 0.5, cy = (sy + ey) * 0.5, cz = (sz + ez) * 0.5;
		double bx = hx + (Math.abs(ex - sx) / 2 + EPSILON), by = hy + (Math.abs(ey - sy) / 2 + EPSILON), bz = hz + (Math.abs(ez - sz) / 2 + EPSILON);
		double fraction = 1;
		V3 normal = V3.ZERO;
		boolean startSolid = false, allSolid = false;
		brushes:
		for (Brush brush : world) {
			V3 min = brush.min(), max = brush.max();
			if (cx - bx > max.x() || cx + bx < min.x() || cy - by > max.y() || cy + by < min.y() || cz - bz > max.z() || cz + bz < min.z()) continue;
			double enter = -1, leave = 1;
			boolean startsOutside = false, endsOutside = false;
			Plane clip = null;
			for (Plane p : brush.planes()) {
				double dist = p.d() + Math.abs(p.nx()) * hx + Math.abs(p.ny()) * hy + Math.abs(p.nz()) * hz;
				double a = sx * p.nx() + sy * p.ny() + sz * p.nz() - dist, b = ex * p.nx() + ey * p.ny() + ez * p.nz() - dist;
				if (a > 0) startsOutside = true;
				if (b > 0) endsOutside = true;
				// A segment that stays outside one face misses, even inside its epsilon margin.
				if (a > 0 && b > 0) continue brushes;
				if (a <= 0 && b <= 0) continue;
				if (a > b) {
					double f = (a - EPSILON) / (a - b);
					if (f > enter) {
						enter = f;
						clip = p;
					}
				} else leave = Math.min(leave, (a + EPSILON) / (a - b));
			}
			if (!startsOutside) {
				startSolid = true;
				// Starting inside but leaving keeps BSP's exit-from-solid behaviour: no stop.
				if (!endsOutside) {
					allSolid = true;
					fraction = 0;
				}
			} else if (enter < leave && enter > -1 && enter < fraction) {
				fraction = Math.max(0, enter);
				normal = clip.normal();
			}
		}
		return new HullTrace(fraction, start.add(end.sub(start).scale(fraction)), normal, startSolid, allSolid);
	}
}
