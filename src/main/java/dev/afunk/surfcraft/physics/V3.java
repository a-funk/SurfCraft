package dev.afunk.surfcraft.physics;

/** A vector in Source units and axes (x, y, z-up). */
public record V3(double x, double y, double z) {
	public static final V3 ZERO = new V3(0, 0, 0);

	public V3 add(V3 o) {
		return new V3(x + o.x, y + o.y, z + o.z);
	}

	public V3 sub(V3 o) {
		return new V3(x - o.x, y - o.y, z - o.z);
	}

	public V3 scale(double s) {
		return new V3(x * s, y * s, z * s);
	}

	public double dot(V3 o) {
		return x * o.x + y * o.y + z * o.z;
	}

	public V3 cross(V3 o) {
		return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
	}

	public double length() {
		return Math.sqrt(x * x + y * y + z * z);
	}

	/** Scales by the reciprocal length, as the reference does; a zero vector stays zero. */
	public V3 normalize() {
		double l = length();
		return scale(1 / (l == 0 ? 1 : l));
	}

	public boolean isZero() {
		return x == 0 && y == 0 && z == 0;
	}

	/** Each component rounded to float, as Source stores origins and velocities. */
	public V3 toFloat() {
		return new V3((float) x, (float) y, (float) z);
	}
}
