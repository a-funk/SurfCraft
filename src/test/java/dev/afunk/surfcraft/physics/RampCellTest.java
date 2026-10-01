package dev.afunk.surfcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RampCellTest {
	private static final int[][] SLOPES = {{5, 4}, {2, 1}};
	private static final int[][] FACINGS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

	@Test
	void polygonIsTheClippedSquare() {
		for (int[] s : SLOPES) for (int c = 1; c <= s[0] + s[1]; c++) {
			RampCell cell = new RampCell(s[0], s[1], c);
			List<double[]> poly = cell.polygon();
			double area = 0;
			for (int i = 0; i < poly.size(); i++) {
				double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
				area += a[0] * b[1] - b[0] * a[1];
				assertTrue(s[0] * a[0] + s[1] * a[1] <= c + 1e-9, "vertex above the slope");
			}
			area /= 2;
			assertTrue(area > 0, "counter-clockwise");
			// Exact area of the unit square under p*u + q*y <= c by fine sampling.
			int n = 2000, inside = 0;
			for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) if (s[0] * (i + 0.5) / n + s[1] * (j + 0.5) / n <= c) inside++;
			assertEquals((double) inside / n / n, area, 2e-3);
		}
	}

	@Test
	void neighbouringCellsShareOneWorldPlane() {
		for (int[] s : SLOPES) for (int[] f : FACINGS) {
			int p = s[0], q = s[1], plane = 3 * p + 2 * q + 1;
			double[] first = null;
			int cells = 0;
			for (int bx = -6; bx <= 6; bx++) for (int by = -6; by <= 6; by++) for (int bz = -6; bz <= 6; bz++) {
				// Cell coordinate u of the block along the facing, as the block code computes it.
				int u = f[0] > 0 ? bx : f[0] < 0 ? -bx - 1 : f[1] > 0 ? bz : -bz - 1;
				int cut = RampCell.continueCut(p, q, plane, 0, 0, u, by);
				if (cut < 1 || cut >= p + q) continue;
				double[] slope = new RampCell(p, q, cut).localPlanes(f[0], f[1])[6];
				double[] world = {slope[0], slope[1], slope[2], slope[3] + slope[0] * bx + slope[1] * by + slope[2] * bz};
				if (first == null) first = world;
				for (int k = 0; k < 4; k++) assertEquals(first[k], world[k], 1e-12, "plane differs at " + bx + "," + by + "," + bz);
				cells++;
			}
			assertTrue(cells > 20, "too few cut cells sampled");
		}
	}

	@Test
	void staircaseStaysUnderTheSlope() {
		for (int[] s : SLOPES) for (int c = 1; c <= s[0] + s[1]; c++) {
			RampCell cell = new RampCell(s[0], s[1], c);
			for (double[] box : cell.staircase(8)) assertTrue(s[0] * box[2] + s[1] * box[3] <= c + 1e-9 || cell.full());
		}
	}

	@Test
	void planesBoundTheSolidTightly() {
		for (int[] s : SLOPES) for (int c = 1; c <= s[0] + s[1]; c++) for (int[] f : FACINGS) {
			RampCell cell = new RampCell(s[0], s[1], c);
			double[][] planes = cell.localPlanes(f[0], f[1]);
			for (double[] v : cell.polygon()) for (double w : new double[] {0, 1}) assertTrue(inside(planes, local(f, v[0], v[1], w), 1e-9));
			// Just above the top-back edge is outside: the tight axial bevel, not the cell's top face.
			assertFalse(inside(planes, local(f, 0.001, cell.maxY() + 0.01, 0.5), 0));
			if (!cell.full()) assertFalse(inside(planes, local(f, 0.999, 0.999, 0.5), 0));
		}
	}

	private static double[] local(int[] f, double u, double y, double w) {
		if (f[0] > 0) return new double[] {u, y, w};
		if (f[0] < 0) return new double[] {1 - u, y, w};
		if (f[1] > 0) return new double[] {w, y, u};
		return new double[] {w, y, 1 - u};
	}

	private static boolean inside(double[][] planes, double[] x, double eps) {
		for (double[] pl : planes) if (pl[0] * x[0] + pl[1] * x[1] + pl[2] * x[2] > pl[3] + eps) return false;
		return true;
	}
}
