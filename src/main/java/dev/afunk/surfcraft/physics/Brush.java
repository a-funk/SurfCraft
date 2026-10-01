package dev.afunk.surfcraft.physics;

import java.util.List;

/**
 * A convex solid, the intersection of its planes' solid sides, in Source units. Box traces expand every plane by
 * the box, so a brush needs Source's bevels (axial planes at its tight bounds, plus edge bevels where its edges
 * are not axis-aligned) or the expanded shape bulges past the real one. {@code min}/{@code max} bound the solid
 * for broad-phase rejection only and may be infinite.
 */
public record Brush(List<Plane> planes, V3 min, V3 max) {
	public Brush {
		planes = List.copyOf(planes);
	}

	/**
	 * A brush bounded by its own vertices: the intersections of three planes that lie inside every plane (within
	 * 0.01 units, as the surf repo's reconstruction). Throws if the planes enclose nothing.
	 */
	public static Brush of(List<Plane> planes) {
		double[] lo = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double[] hi = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
		int n = planes.size(), vertices = 0;
		for (int i = 0; i < n; i++) for (int j = i + 1; j < n; j++) for (int k = j + 1; k < n; k++) {
			V3 a = planes.get(i).normal(), b = planes.get(j).normal(), c = planes.get(k).normal();
			V3 bc = b.cross(c), ca = c.cross(a), ab = a.cross(b);
			double det = a.dot(bc);
			if (Math.abs(det) < 1e-8) continue;
			V3 p = bc.scale(planes.get(i).d()).add(ca.scale(planes.get(j).d())).add(ab.scale(planes.get(k).d())).scale(1 / det);
			boolean inside = true;
			for (Plane q : planes) if (q.nx() * p.x() + q.ny() * p.y() + q.nz() * p.z() - q.d() > 0.01) { inside = false; break; }
			if (!inside) continue;
			vertices++;
			lo[0] = Math.min(lo[0], p.x()); lo[1] = Math.min(lo[1], p.y()); lo[2] = Math.min(lo[2], p.z());
			hi[0] = Math.max(hi[0], p.x()); hi[1] = Math.max(hi[1], p.y()); hi[2] = Math.max(hi[2], p.z());
		}
		if (vertices < 4) throw new IllegalArgumentException("planes enclose no convex volume");
		return new Brush(planes, new V3(lo[0], lo[1], lo[2]), new V3(hi[0], hi[1], hi[2]));
	}
}
