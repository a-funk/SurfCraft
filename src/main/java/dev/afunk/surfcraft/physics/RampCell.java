package dev.afunk.surfcraft.physics;

import java.util.ArrayList;
import java.util.List;

/**
 * One block cell of a surf ramp with rise:run {@code p:q}: the part of the unit cube where
 * {@code p*u + q*y <= cut}. {@code u} runs from the cell's back face toward the side the slope faces (its
 * facing), {@code y} runs up, and {@code w} runs along the ramp. {@code cut == p + q} is a full cell.
 *
 * <p>Cells on one plane share {@code C = cut + p*U + q*y} for cell coordinates {@code (U, y)}, so a
 * neighbour's cut continues the same slope into this cell: {@link #continueCut}.
 */
public record RampCell(int p, int q, int cut) {
	public RampCell {
		if (p < 1 || q < 1 || cut < 1 || cut > p + q) throw new IllegalArgumentException("bad ramp cell " + p + ":" + q + " cut " + cut);
	}

	public boolean full() {
		return cut == p + q;
	}

	/** The solid's extent along u (reached at y = 0), in (0, 1]. */
	public double maxU() {
		return Math.min(1.0, (double) cut / p);
	}

	/** The solid's extent along y (reached at u = 0), in (0, 1]. */
	public double maxY() {
		return Math.min(1.0, (double) cut / q);
	}

	/** Width of the solid along u at height y, clamped to [0, 1]. */
	public double uAt(double y) {
		return Math.clamp((cut - q * y) / p, 0.0, 1.0);
	}

	/**
	 * The cut of the cell at {@code (u, y)} on the plane through a neighbour cell {@code (nu, ny)} with cut
	 * {@code nCut}, all in cell units along the same facing. Values below 1 mean the plane passes under the
	 * cell; values above {@code p + q} mean the cell is entirely under it.
	 */
	public static int continueCut(int p, int q, int nCut, int nu, int ny, int u, int y) {
		return nCut + p * (nu - u) + q * (ny - y);
	}

	/** The cross-section {@code [0,1]^2 ∩ {p*u + q*y <= cut}} as counter-clockwise (u, y) vertices. */
	public List<double[]> polygon() {
		double[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
		List<double[]> out = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			double[] a = square[i], b = square[(i + 1) % 4];
			double fa = p * a[0] + q * a[1] - cut, fb = p * b[0] + q * b[1] - cut;
			if (fa <= 0) out.add(a);
			if ((fa < 0 && fb > 0) || (fa > 0 && fb < 0)) {
				double t = fa / (fa - fb);
				out.add(new double[] {a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1])});
			}
		}
		return out;
	}

	/**
	 * Boxes of the inscribed staircase: {@code slices} horizontal layers, each as wide as the solid at the
	 * layer's top, so every box lies under the slope. Each box is {@code {u0, y0, u1, y1}} (w spans 0..1).
	 */
	public List<double[]> staircase(int slices) {
		List<double[]> boxes = new ArrayList<>();
		if (full()) {
			boxes.add(new double[] {0, 0, 1, 1});
			return boxes;
		}
		for (int k = 0; k < slices; k++) {
			double y0 = (double) k / slices, y1 = (double) (k + 1) / slices, u1 = uAt(y1);
			if (u1 > 1e-6) boxes.add(new double[] {0, y0, u1, y1});
		}
		return boxes;
	}

	/**
	 * The cell's exact convex solid as planes {@code nx, ny, nz, d} ({@code n·x <= d}) in block-local
	 * Minecraft axes, for a cell facing horizontal step {@code (fx, fz)}: the slope plane (unless full) and
	 * the six axial planes of the solid's tight bounds. The tight axial planes are Source's axial bevels:
	 * with the cell's own bounds instead, a swept box would hit phantom geometry above the slope's top edge.
	 */
	public double[][] localPlanes(int fx, int fz) {
		double mu = maxU(), my = maxY();
		// Tight bounds in block-local x/z for the facing; u runs along +x, -x, +z or -z.
		double x0 = 0, x1 = 1, z0 = 0, z1 = 1;
		if (fx > 0) x1 = mu;
		else if (fx < 0) x0 = 1 - mu;
		else if (fz > 0) z1 = mu;
		else z0 = 1 - mu;
		List<double[]> planes = new ArrayList<>(List.of(
				new double[] {1, 0, 0, x1}, new double[] {-1, 0, 0, -x0},
				new double[] {0, 1, 0, my}, new double[] {0, -1, 0, 0},
				new double[] {0, 0, 1, z1}, new double[] {0, 0, -1, -z0}));
		if (!full()) {
			double l = Math.hypot(p, q), nu = p / l, ny = q / l, d = cut / l;
			// u = x, 1 - x, z or 1 - z: substitute into p*u + q*y <= cut.
			if (fx > 0) planes.add(new double[] {nu, ny, 0, d});
			else if (fx < 0) planes.add(new double[] {-nu, ny, 0, d - nu});
			else if (fz > 0) planes.add(new double[] {0, ny, nu, d});
			else planes.add(new double[] {0, ny, -nu, d - nu});
		}
		return planes.toArray(new double[0][]);
	}
}
