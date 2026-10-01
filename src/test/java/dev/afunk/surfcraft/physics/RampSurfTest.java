package dev.afunk.surfcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Surfing Minecraft-scale ramps built from {@link RampCell}s: a ramp facing +x, 5 blocks tall, 80 long (along z),
 * with the Minecraft player hull. Seams between cells must be invisible: the cell-built ramp must surf exactly
 * like the same ramp as one brush. Plain per-cell brushes do not (Source's trace rampbugs at their seams, see
 * {@link #perCellBrushesRampbugAsInSource}), so the cells go through {@link RampBrushes}.
 */
class RampSurfTest {
	static final double K = SourceUnits.PER_BLOCK;
	/** The Minecraft player, 0.6 x 1.8 blocks. */
	static final V3 HULL = new V3(0.3 * K, 0.3 * K, 0.9 * K);
	static final int HEIGHT = 5, LENGTH = 80, TICKS = 300;

	/** Every cut cell and the full cells under the slope of a ramp facing +x, its top edge at x = 0, y = height. */
	static List<RampBrushes.Placed> cells(int p, int q, int height, int length) {
		int plane = q * height;
		List<RampBrushes.Placed> out = new ArrayList<>();
		for (int z = 0; z < length; z++) for (int y = 0; y < height; y++) for (int u = 0; p * u < plane; u++) {
			int cut = RampCell.continueCut(p, q, plane, 0, 0, u, y);
			if (cut >= 1) out.add(new RampBrushes.Placed(u, y, z, 1, 0, new RampCell(p, q, Math.min(cut, p + q))));
		}
		return out;
	}

	/** The same ramp as one brush: its slope and tight bounds, in RampCell's plane order. */
	static List<Brush> monolith(int p, int q, int height, int length) {
		double l = Math.hypot(p, q), plane = q * height;
		double[][] planes = {{1, 0, 0, plane / p}, {-1, 0, 0, 0}, {0, 1, 0, height}, {0, -1, 0, 0}, {0, 0, 1, length}, {0, 0, -1, 0}, {p / l, q / l, 0, plane / l}};
		return List.of(SourceUnits.blockBrush(planes, 0, 0, 0));
	}

	/** A player whose hull touches nothing, 1 unit off the slope, its lowest edge {@code depth} blocks under the top. */
	static SourceMovement onSlope(int p, int q, double depth) {
		double l = Math.hypot(p, q);
		double offset = q * HEIGHT / l * K + (p * HULL.x() + q * HULL.z()) / l + 1;
		double cz = (HEIGHT - depth) * K + HULL.z(), cx = (offset * l - q * cz) / p;
		SourceMovement m = new SourceMovement();
		m.setOrigin(new V3(cx, -2 * K, cz - HULL.z()));
		return m;
	}

	/**
	 * 600 units/s along the ramp (Minecraft +z, Source -y), looking along it and strafing into the slope. A light
	 * strafe slides down; a full one climbs (CS:S: the 30-unit air wish beats gravity's pull down a 51-63 degree
	 * plane), so that run starts lower to stay below the top edge.
	 */
	@ParameterizedTest(name = "{0}:{1} side {2}")
	@CsvSource({"5, 4, 15, 1, true", "2, 1, 15, 1, true", "5, 4, 400, 3.5, false", "2, 1, 400, 3.5, false"})
	void surfsAcrossCellSeamsLikeOneBrush(int p, int q, double side, double depth, boolean slidesDown) {
		List<Brush> cells = RampBrushes.of(cells(p, q, HEIGHT, LENGTH)), mono = monolith(p, q, HEIGHT, LENGTH);
		SourceMovement a = onSlope(p, q, depth), b = onSlope(p, q, depth);
		a.velocity = b.velocity = new V3(0, -600, 0);
		double startZ = a.origin.z(), position = 0, velocity = 0;
		int surf = 0;
		for (int t = 0; t < TICKS; t++) {
			a.tick(Config.CSS_SURF, 0, side, -90, false, Recording.DT, HULL, cells);
			b.tick(Config.CSS_SURF, 0, side, -90, false, Recording.DT, HULL, mono);
			position = Math.max(position, a.origin.sub(b.origin).length());
			velocity = Math.max(velocity, a.velocity.sub(b.velocity).length());
			String at = "tick " + t + " origin " + a.origin + " velocity " + a.velocity;
			assertTrue(a.origin.sub(b.origin).length() < 0.01 && a.velocity.sub(b.velocity).length() < 0.01, at + " vs one brush " + b.origin + " " + b.velocity);
			assertFalse(a.grounded, at + ": grounded on a surf ramp");
			for (HullTrace c : a.contacts) assertFalse(c.startSolid() || c.allSolid(), at + ": stuck " + c);
			V3 centre = a.origin.add(new V3(0, 0, HULL.z()));
			assertFalse(SourceHull.trace(cells, centre, centre, HULL).startSolid(), at + ": inside the ramp");
			// A rampbug zeroes or clips the velocity along the ramp at a seam; nothing here may slow it.
			assertTrue(a.velocity.y() <= -600 + 1e-3, at + ": lost speed along the ramp");
			if (a.surfNormal != null) surf++;
		}
		System.out.printf("%d:%d side %.0f: %d/%d surf ticks, height %+.2f units, cells vs one brush worst %.6f units %.6f units/s%n", p, q, side, surf, TICKS,
				a.origin.z() - startZ, position, velocity);
		assertTrue(surf >= TICKS * 0.95, "surf contact on only " + surf + " ticks");
		assertEquals(slidesDown, a.origin.z() < startZ, "height change " + (a.origin.z() - startZ));
		assertTrue(a.origin.y() < -2 * K - 0.95 * 600 * TICKS * Recording.DT, "did not travel along the ramp");
	}

	/** A 1:1 (45 degree) slope has normal z 0.707 >= 0.7: walkable ground, which is why surf ramps are steeper. */
	@Test
	void fortyFiveDegreeSlopeIsGround() {
		List<Brush> world = RampBrushes.of(cells(1, 1, HEIGHT, 4));
		SourceMovement m = onSlope(1, 1, 2);
		for (int t = 0; t < 20; t++) m.tick(Config.CSS_SURF, 0, 0, -90, false, Recording.DT, HULL, world);
		assertTrue(m.grounded, "not grounded on 45 degrees");
		assertEquals(Math.sqrt(0.5), m.ground.normal().z(), 1e-12);
		assertEquals(0, m.velocity.length(), 0, "a player standing on walkable ground keeps sliding");
	}

	/**
	 * Why {@link RampBrushes} exists: with one brush per cell, the full-strafe 5:4 climb crosses a row seam at tick
	 * 216 within DIST_EPSILON of the cell below's top face, which counts the hull as gone, and the cell above
	 * reports its internal +x face: the hull is clipped as if by a wall (velocity x +1/32) where the one-brush ramp
	 * keeps sliding. CS:S does the same on multi-brush ramps.
	 */
	@Test
	void perCellBrushesRampbugAsInSource() {
		List<Brush> plain = new ArrayList<>(), mono = monolith(5, 4, HEIGHT, LENGTH);
		for (RampBrushes.Placed c : cells(5, 4, HEIGHT, LENGTH)) plain.add(SourceUnits.blockBrush(c.cell().localPlanes(1, 0), c.x(), c.y(), c.z()));
		SourceMovement a = onSlope(5, 4, 3.5), b = onSlope(5, 4, 3.5);
		a.velocity = b.velocity = new V3(0, -600, 0);
		int t = 0;
		for (; t < TICKS && a.velocity.sub(b.velocity).length() < 0.01; t++) {
			a.tick(Config.CSS_SURF, 0, 400, -90, false, Recording.DT, HULL, plain);
			b.tick(Config.CSS_SURF, 0, 400, -90, false, Recording.DT, HULL, mono);
		}
		assertEquals(217, t, "ticks until the plain cells leave the one-brush trajectory");
		assertEquals(1, a.contacts.getFirst().normal().x(), 0, "clipped by a cell's internal +x face");
		assertEquals(1.0 / 32, a.velocity.x(), 0);
	}

	/**
	 * RampBrushes keeps the solid, for every facing and an irregular build (random gaps; a second, lower ramp on
	 * another plane further along): every point inside a plain cell brush is inside its brushes, and every point
	 * inside its brushes is inside a plain cell brush or in the slab's sliver under a grid corner of the slope.
	 */
	@Test
	void rampBrushesKeepTheSolid() {
		Random random = new Random(7);
		for (int[] s : new int[][] {{5, 4}, {2, 1}}) for (int[] f : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			int p = s[0], q = s[1];
			List<RampBrushes.Placed> placed = new ArrayList<>();
			for (int w = 0; w < 12; w++) for (int y = 0; y < HEIGHT; y++) for (int u = 0; u < 6; u++) {
				int cut = RampCell.continueCut(p, q, w < 6 ? q * HEIGHT : q * HEIGHT - 7, 0, 0, u, y);
				if (cut < 1 || random.nextInt(5) == 0) continue;
				// u along the facing back to block x/z; w runs along the other axis.
				int x = f[0] > 0 ? u : f[0] < 0 ? -u - 1 : w, z = f[1] > 0 ? u : f[1] < 0 ? -u - 1 : w;
				placed.add(new RampBrushes.Placed(x, y, z, f[0], f[1], new RampCell(p, q, Math.min(cut, p + q))));
			}
			List<Brush> plain = new ArrayList<>(), merged = RampBrushes.of(placed);
			for (RampBrushes.Placed c : placed) plain.add(SourceUnits.blockBrush(c.cell().localPlanes(c.fx(), c.fz()), c.x(), c.y(), c.z()));
			assertTrue(merged.size() < plain.size(), "nothing merged");
			assertSubset(merged, plain, x -> sliver(p, q, f, x), random, p + ":" + q + " facing " + f[0] + "," + f[1] + " adds solid");
			assertSubset(plain, merged, x -> false, random, p + ":" + q + " facing " + f[0] + "," + f[1] + " loses solid");
		}
	}

	/** Within SLAB under the slope (either plane of the irregular build) and next to a grid corner on it. */
	private static boolean sliver(int p, int q, int[] f, V3 source) {
		double x = source.x() / K, y = source.z() / K, z = -source.y() / K, l = Math.hypot(p, q);
		double u = f[0] > 0 ? x : f[0] < 0 ? -x : f[1] > 0 ? z : -z;
		for (int plane : new int[] {q * HEIGHT, q * HEIGHT - 7}) {
			double depth = (plane - p * u - q * y) / l;
			long cu = Math.round(u), cy = Math.round(y);
			if (depth >= -1e-9 && depth <= RampBrushes.SLAB + 1e-9 && p * cu + q * cy == plane && Math.abs(u - cu) < 0.01 && Math.abs(y - cy) < 0.01) return true;
		}
		return false;
	}

	/** Points sampled inside each brush of {@code a} (thin slabs included) lie inside some brush of {@code b}. */
	private static void assertSubset(List<Brush> a, List<Brush> b, java.util.function.Predicate<V3> allowed, Random random, String message) {
		for (Brush brush : a) {
			int found = 0;
			for (int i = 0; i < 400_000 && found < 40; i++) {
				V3 min = brush.min(), max = brush.max();
				V3 x = new V3(min.x() + random.nextDouble() * (max.x() - min.x()), min.y() + random.nextDouble() * (max.y() - min.y()), min.z() + random.nextDouble() * (max.z() - min.z()));
				if (!inside(brush, x, -1e-6)) continue;
				found++;
				boolean covered = false;
				for (Brush other : b) covered |= inside(other, x, 1e-6);
				assertTrue(covered || allowed.test(x), message + " at " + x);
			}
			assertTrue(found > 0, message + ": no samples in " + brush);
		}
	}

	private static boolean inside(Brush brush, V3 x, double margin) {
		for (Plane p : brush.planes()) if (p.nx() * x.x() + p.ny() * x.y() + p.nz() * x.z() - p.d() > margin) return false;
		return true;
	}
}
