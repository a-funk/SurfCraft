package dev.afunk.surfcraft.physics;

/** A brush side in Source units: the solid side is {@code n·p <= d}, so the unit normal points out. */
public record Plane(double nx, double ny, double nz, double d) {
	public V3 normal() {
		return new V3(nx, ny, nz);
	}
}
