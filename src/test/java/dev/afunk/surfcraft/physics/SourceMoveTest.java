package dev.afunk.surfcraft.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SourceMoveTest {
	/** Measured in build 11003710 (surf repo wall-glide): a 600 units/s wall hit keeps -1/32 units/s, not zero. */
	@Test
	void clipLeavesOneThirtySecondOutward() {
		V3 wall = SourceMove.clipVelocity(new V3(600, 200, -84), new V3(-1, 0, 0));
		assertEquals(-1.0 / 32, wall.x(), 0);
		assertEquals(200, wall.y(), 0);
		assertEquals(-84, wall.z(), 0);
		V3 n = new V3(0.6, 0, 0.8), ramp = SourceMove.clipVelocity(new V3(-300, 600, -500), n);
		assertEquals(1.0 / 32, ramp.dot(n), 1e-4);
		assertEquals(600, ramp.y(), 0);
	}

	/** DIST_EPSILON: a box trace stops 1/32 unit short of the plane it hits; the flat recordings stand at z 96.03125. */
	@Test
	void traceStopsOneThirtySecondShort() {
		HullTrace t = SourceHull.trace(CssReferenceReplayTest.FLAT, new V3(0, 0, 200), new V3(0, 0, 100), Recording.HULL);
		assertEquals(96 + 31 + 1.0 / 32, t.end().z(), 1e-9);
		assertEquals(new V3(0, 0, 1), t.normal());
	}
}
