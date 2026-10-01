package dev.afunk.surfcraft.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft blocks (x, y-up, z) to Source units and axes (x, y, z-up): 1 block = 1 m and 1 unit = 0.0254 m, so
 * Source = (x, -z, y) * {@link #PER_BLOCK}. A proper rotation, so normals map the same way.
 */
public final class SourceUnits {
	public static final double PER_BLOCK = 1 / 0.0254;

	private SourceUnits() {
	}

	/**
	 * A block's convex solid from block-local planes {@code {nx, ny, nz, d}} ({@code n·l <= d}, l in [0, 1]^3, as
	 * {@link RampCell#localPlanes}) at block {@code (bx, by, bz)}, as a Source brush.
	 */
	public static Brush blockBrush(double[][] localPlanes, int bx, int by, int bz) {
		List<Plane> planes = new ArrayList<>();
		for (double[] p : localPlanes) planes.add(new Plane(p[0], -p[2], p[1], (p[3] + p[0] * bx + p[1] * by + p[2] * bz) * PER_BLOCK));
		return Brush.of(planes);
	}
}
