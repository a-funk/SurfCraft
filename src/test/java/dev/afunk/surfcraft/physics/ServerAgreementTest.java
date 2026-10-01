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
 * The controller publishes one position per Minecraft tick and the server re-simulates the move between two of them
 * with {@link ExactCollide} (lifting allowed), rejecting it when it ends more than 0.25 blocks away horizontally. Here
 * random surf runs over A-frame ramps (cut cells on stone-like full cells, a floor around) publish positions through
 * {@link TickDriver}, and every tick's move must pass that check: no rubber-banding, including over the apex.
 */
class ServerAgreementTest {
	static final double K = SourceUnits.PER_BLOCK;
	/** The Minecraft player box, 0.6 x 1.8 blocks (as getBoundingBox: float dimensions). */
	static final V3 HULL = new V3(0.3f * K, 0.3f * K, 1.8f / 2.0 * K);
	static final Config CONFIG = Config.CSS_SURF.withStepHeight(0.6f * K);
	/** The server's limit: 0.25 blocks of horizontal error. */
	static final double LIMIT = 0.25 * K;

	/**
	 * An A-frame {@code length} long (Minecraft z) and {@code height} tall, its apex along x = 0: the +x side faces +x
	 * (descends toward +x), the other side faces -x. Cells of both sides plus a floor below y = 0.
	 */
	static List<Brush> aFrame(int p, int q, int height, int length) {
		List<RampBrushes.Placed> cells = new ArrayList<>();
		for (RampBrushes.Placed c : RampSurfTest.cells(p, q, height, length)) {
			cells.add(c);
			cells.add(new RampBrushes.Placed(-c.x() - 1, c.y(), c.z(), -1, 0, c.cell()));
		}
		List<Brush> world = new ArrayList<>();
		world.add(box(-40, -1, -20, 40, 0, length + 20));
		world.addAll(RampBrushes.of(cells));
		return world;
	}

	/** A box in Minecraft block coordinates as a Source brush. */
	static Brush box(double x0, double y0, double z0, double x1, double y1, double z1) {
		double[][] planes = {{1, 0, 0, x1}, {-1, 0, 0, -x0}, {0, 1, 0, y1}, {0, -1, 0, -y0}, {0, 0, 1, z1}, {0, 0, -1, -z0}};
		return SourceUnits.blockBrush(planes, 0, 0, 0);
	}

	/** Feet on the +x side's slope (p*x + q*y = q*height) at height y, clear by {@code gap} blocks, at Minecraft z. */
	static V3 onSlope(int p, int q, int height, double y, double z, double gap) {
		double l = Math.hypot(p, q), x = (q * height - q * y) / p + HULL.x() / K + gap * l / p;
		return new V3(x * K, -z * K, y * K);
	}

	static boolean inside(List<Brush> world, V3 feet) {
		V3 c = feet.add(new V3(0, 0, HULL.z()));
		for (Brush b : world) {
			boolean in = true;
			for (Plane p : b.planes()) {
				double dist = p.d() + Math.abs(p.nx()) * HULL.x() + Math.abs(p.ny()) * HULL.y() + Math.abs(p.nz()) * HULL.z();
				if (p.nx() * c.x() + p.ny() * c.y() + p.nz() * c.z() - dist >= -ExactCollide.TOL) {
					in = false;
					break;
				}
			}
			if (in) return true;
		}
		return false;
	}

	/**
	 * Random runs: start on the +x side at a random height with a random speed along the ramp and toward the apex, then
	 * random strafes (mostly into the slope), mouse turns and jumps for 120 ticks.
	 */
	@ParameterizedTest(name = "{0}:{1}")
	@CsvSource({"5, 4", "2, 1"})
	void everyPublishedMovePassesTheServerCheck(int p, int q) {
		int height = 6, length = 140;
		List<Brush> world = aFrame(p, q, height, length);
		Random random = new Random(p * 31 + q);
		int runs = 150, ticks = 0, clear = 0, overApex = 0;
		double worst = 0;
		for (int run = 0; run < runs; run++) {
			TickDriver d = new TickDriver();
			d.core.setOrigin(onSlope(p, q, height, 0.5 + random.nextDouble() * (height - 2.5), 5, 0.02));
			double along = 300 + random.nextDouble() * 2500, toward = -random.nextDouble() * 900;
			d.core.velocity = new V3(toward, -along, random.nextDouble() * 300 - 150);
			d.published = d.core.origin;
			double yaw = -90, turn = 0;
			double side = 400;
			boolean jump = false;
			for (int t = 0; t < 120; t++) {
				if (random.nextInt(10) == 0) side = random.nextInt(4) == 0 ? -400 : random.nextInt(3) == 0 ? 0 : 400;
				if (random.nextInt(15) == 0) turn = random.nextGaussian() * 4;
				if (random.nextInt(20) == 0) jump = !jump;
				yaw += turn;
				V3 from = d.published;
				boolean grounded = d.core.grounded;
				d.tick(CONFIG, 0, side, jump, yaw, HULL, world);
				V3 to = d.published, delta = to.sub(from);
				String at = "run " + run + " tick " + t + " from " + from + " to " + to;
				assertFalse(inside(world, d.core.origin), at + ": core inside the ramp at " + d.core.origin);
				assertFalse(inside(world, to), at + ": published inside the ramp");
				V3 got = ExactCollide.resolve(world, from, delta, HULL, CONFIG.stepHeight(), grounded, true);
				double error = Math.hypot(got.x() - delta.x(), got.y() - delta.y());
				worst = Math.max(worst, error);
				assertTrue(error <= LIMIT, at + ": the server would end " + error / K + " blocks away");
				if (got.equals(delta)) clear++;
				if (Math.signum(from.x()) != Math.signum(to.x())) overApex++;
				ticks++;
				if (to.z() < -2 * K || Math.abs(to.y()) > (length + 10) * K) break;
			}
		}
		System.out.printf("%d:%d: %d runs, %d ticks, %d straight chords, %d apex crossings, worst server error %.6f blocks%n", p, q, runs, ticks, clear,
				overApex, worst / K);
		assertTrue(overApex > 0, "no run crossed the apex");
	}

	/**
	 * Crossing an A-frame's apex fast within one tick: the chord between the two slopes passes a block under the apex.
	 * Vanilla's axis order and step-up (0.6 blocks) would stop the move at the near slope; the lift resolves it.
	 */
	@Test
	void apexCrossingIsLiftedOver() {
		List<Brush> world = aFrame(5, 4, 6, 20);
		V3 from = onSlope(5, 4, 6, 5, 10, 0.001), mirror = onSlope(5, 4, 6, 4.9, 10.5, 0.001);
		// On the -x side, 0.1 blocks lower: a chord through the apex region.
		V3 to = new V3(-mirror.x(), mirror.y(), mirror.z());
		V3 delta = to.sub(from);
		assertFalse(inside(world, from) || inside(world, to));
		assertFalse(ExactCollide.clear(world, from.add(new V3(0, 0, HULL.z())), to.add(new V3(0, 0, HULL.z())), HULL), "the chord should hit the apex");
		V3 vanilla = ExactCollide.resolve(world, from, delta, HULL, 0.6 * K, false, false);
		assertTrue(Math.hypot(vanilla.x() - delta.x(), vanilla.y() - delta.y()) > LIMIT, "vanilla order alone would pass: " + vanilla);
		V3 lifted = ExactCollide.resolve(world, from, delta, HULL, 0.6 * K, false, true);
		assertEquals(delta.x(), lifted.x(), 1e-9);
		assertEquals(delta.y(), lifted.y(), 1e-9);
	}

	/**
	 * The client's Source trace lets a hull graze a block's vertical edge within DIST_EPSILON, so a published chord can
	 * overlap a corner by about 0.01 units (here a stone pillar on a 5:4 A-frame, the one such move in 313k ticks of random
	 * runs among 40 pillars). The server's re-simulation must accept it, not resolve it axis by axis into the pillar (1.59
	 * blocks off: moved wrongly, and the surfer stopped dead). Moves that are not a client's keep exact collision.
	 */
	@Test
	void cornerGrazingChordIsAccepted() {
		List<Brush> world = new ArrayList<>(aFrame(5, 4, 6, 140));
		world.add(1, box(3, 1, 22, 4, 3, 23));
		V3 from = new V3(169.0752764608131, -851.4930961511183, 39.66118103814514), to = new V3(173.7897062656463, -916.9573705393236, 33.74711988318406);
		V3 delta = to.sub(from), lift = new V3(0, 0, HULL.z());
		assertFalse(ExactCollide.clear(world, from.add(lift), to.add(lift), HULL), "the exact chord should graze the pillar");
		assertEquals(delta, ExactCollide.resolve(world, from, delta, HULL, CONFIG.stepHeight(), false, true));
		assertFalse(ExactCollide.resolve(world, from, delta, HULL, CONFIG.stepHeight(), false, false).equals(delta));
	}

	/**
	 * Float origins can leave the core a fraction of an ulp inside a wall it reached without crossing (4.4e-5 units at
	 * y = -1169). The publishing sweep must not go deeper from there: it used to skip a brush it started inside and publish
	 * 0.0012 units into the wall, past the 1e-5 blocks vanilla's new-collision check allows (a server correction).
	 */
	@Test
	void publishingNeverGoesDeeperThanTheCore() {
		List<Brush> world = new ArrayList<>(aFrame(2, 1, 6, 140));
		world.add(1, box(-8, 0, 30, 8, 8, 31));
		double face = -30 * K + HULL.y();
		for (double depth : new double[] {0, 4.4e-5, 4e-4}) {
			// DIST_EPSILON above the 2:1 slope and against the wall: up the slope, pressing 0.0012 units into the wall.
			V3 feet = new V3(44.1463508605957, face - depth, 171.62074279785156);
			V3 published = TickDriver.sweep(world, feet, new V3(-0.02, -0.0012, 0.04), HULL);
			assertTrue(face - published.y() <= depth + 1e-5, "started " + depth + " units inside the wall, published " + (face - published.y()));
			assertEquals(feet.x() - 0.02, published.x(), 1e-6, "stopped instead of sliding along the wall: " + published);
		}
	}

	/**
	 * Auto hop lands and jumps inside one 50 ms tick about 7 times in 10 (the landing substep is not the tick's last), so a
	 * tick that ends grounded misses most landings. The driver flags the landing and, when the tick ends in the air again,
	 * publishes the touchdown: a drop with jump held must report the landing on the tick its fall ends, on the ground (the
	 * controller sends it as onGround, and the server takes the whole fall's damage then).
	 */
	@Test
	void everyLandingIsReported() {
		List<Brush> floor = List.of(box(-1000, -10, -1000, 1000, 0, 1000));
		int landings = 0, endedAirborne = 0;
		for (int i = 0; i < 600; i++) {
			TickDriver d = new TickDriver();
			d.core.setOrigin(new V3(0, 0, (4 + i * 0.0191) * K));
			// A shadow of the core with the same substeps shows which substep lands, and where.
			SourceMovement shadow = new SourceMovement();
			shadow.setOrigin(d.core.origin);
			double lag = 0;
			V3 touchdown = null;
			for (int t = 0; t < 400 && touchdown == null; t++) {
				d.tick(CONFIG, 0, 0, true, 0, HULL, floor);
				for (lag += TickDriver.TICK; lag >= TickDriver.DT; ) {
					lag -= TickDriver.DT;
					boolean airborne = !shadow.grounded;
					shadow.tick(CONFIG, 0, 0, 0, true, TickDriver.DT, HULL, floor);
					if (airborne && shadow.grounded && touchdown == null) touchdown = shadow.origin;
				}
				assertEquals(shadow.origin, d.core.origin, "the shadow left the driver");
				assertEquals(touchdown != null, d.landed, "drop " + i + " tick " + t);
			}
			assertTrue(touchdown != null, "drop " + i + " never landed");
			assertEquals(touchdown.z(), d.published.z(), 0, "drop " + i + ": the landing tick is not published on the ground");
			landings++;
			if (!d.core.grounded) endedAirborne++;
		}
		System.out.printf("%d landings with jump held, %d of their ticks ended airborne (hopped)%n", landings, endedAirborne);
		assertTrue(endedAirborne > landings / 2, "the hops should mostly leave the ground within the landing tick");
	}

	/** Walls stay walls: a move through a 3-block wall is not lifted over at walking speed. */
	@Test
	void liftDoesNotPassWalls() {
		List<Brush> world = List.of(box(-10, -1, -10, 10, 0, 10), box(1, 0, -10, 2, 3, 10));
		V3 from = new V3(0.5 * K, 0, 0), delta = new V3(2 * K, 0, 0);
		V3 got = ExactCollide.resolve(world, from, delta, HULL, 0.6 * K, true, true);
		assertTrue(got.x() < 0.21 * K, "passed the wall: " + got);
	}

	/** The yaw between two ticks is interpolated the short way round, also across the +-180 wrap. */
	@Test
	void yawTurnsTheShortWay() {
		TickDriver d = new TickDriver();
		d.yaw = 179;
		List<Brush> none = List.of();
		d.core.velocity = new V3(0, 0, 0);
		d.tick(CONFIG, 400, 0, false, -179 + 360 * 5, HULL, none);
		// Forward at about 180 degrees: moving toward -x, never swinging through 0 (+x).
		assertTrue(d.core.velocity.x() < 0 && Math.abs(d.core.velocity.y()) < 0.1 * Math.abs(d.core.velocity.x()), "velocity " + d.core.velocity);
	}

	/**
	 * A vanilla-driven player (creative flight, say) pressing into a slope tick after tick: each move must end touching it,
	 * never inside. A box left even the tolerance inside counts as starting inside next time, which may leave the brush:
	 * the player would sink a little further every tick (found in game: 0.23 blocks deep, then a server correction).
	 */
	@Test
	void pressingIntoASlopeNeverSinks() {
		List<Brush> world = aFrame(5, 4, 6, 20);
		Random random = new Random(11);
		V3 feet = onSlope(5, 4, 6, 3, 10, 0.001);
		double worst = 0;
		for (int i = 0; i < 3000; i++) {
			// Down and into the slope (-x), with a little drift along it.
			V3 delta = new V3(-random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 2, -random.nextDouble() * 3);
			feet = feet.add(ExactCollide.resolve(world, feet, delta, HULL, 0.6 * K, i % 2 == 0, false));
			worst = Math.max(worst, penetration(world, feet));
		}
		assertTrue(worst < 1e-9, "sank " + worst + " units into the slope");
		assertTrue(feet.z() > 2 * K, "slid off the slope: " + feet);
	}

	/** How deep the hull at these feet is inside the deepest brush (0 when clear). */
	static double penetration(List<Brush> world, V3 feet) {
		V3 c = feet.add(new V3(0, 0, HULL.z()));
		double worst = 0;
		for (Brush b : world) {
			double out = Double.NEGATIVE_INFINITY;
			for (Plane p : b.planes()) {
				double dist = p.d() + Math.abs(p.nx()) * HULL.x() + Math.abs(p.ny()) * HULL.y() + Math.abs(p.nz()) * HULL.z();
				out = Math.max(out, p.nx() * c.x() + p.ny() * c.y() + p.nz() * c.z() - dist);
			}
			worst = Math.max(worst, -out);
		}
		return worst;
	}

	/**
	 * Minecraft stands players exactly on a block's top; Source counts touching as solid, so a core placed there starts
	 * all-solid and never moves again. The driver nudges it clear first (Source's CheckStuck).
	 */
	@Test
	void feetExactlyOnTheFloorStillMove() {
		List<Brush> world = List.of(box(-10, -1, -10, 10, 0, 10));
		TickDriver d = new TickDriver();
		d.core.velocity = new V3(500, 0, 0);
		for (int t = 0; t < 10; t++) d.tick(CONFIG, 0, 0, false, -90, HULL, world);
		assertTrue(d.core.origin.x() > 50, "stuck at " + d.core.origin + " velocity " + d.core.velocity);
		assertTrue(d.core.grounded && d.core.origin.z() > 0 && d.core.origin.z() < 1, "not resting on the floor: " + d.core.origin);
	}
}
