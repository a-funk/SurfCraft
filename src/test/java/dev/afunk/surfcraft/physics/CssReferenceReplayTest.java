package dev.afunk.surfcraft.physics;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Replays real CS:S server recordings (build 11003710) on a flat lane, from one initial state and with no
 * per-tick corrections: every flat recording of the surf repo that never ducks.
 */
class CssReferenceReplayTest {
	/** The recorded lane: a world brush with its top at z = 96, planes in the reference's box order. */
	static final List<Brush> FLAT = List.of(new Brush(List.of(new Plane(1, 0, 0, 2000), new Plane(-1, 0, 0, 2000), new Plane(0, 0, 1, 96),
			new Plane(0, 0, -1, -80), new Plane(0, -1, 0, 2000), new Plane(0, 1, 0, 2000)), new V3(-2000, -2000, 80), new V3(2000, 2000, 96)));

	@ParameterizedTest
	@ValueSource(strings = {"vanilla-run", "vanilla-release", "vanilla-jump", "vanilla-held-jump", "vanilla-bhop", "vanilla-strafe", "surf-strafe",
			"vanilla-launch", "surf-launch", "vanilla-apex-reverse", "surf-apex-reverse", "surf-scout-run", "surf-unarmed-run"})
	void replays(String name) throws IOException {
		check(Recording.load(name, false));
	}

	@Test
	void replaysCrlfCsv() throws IOException {
		check(Recording.load("vanilla-run", true));
	}

	private static void check(Recording recording) {
		Recording.Result result = recording.replay(FLAT);
		System.out.println(result);
		assertNull(result.firstFailure(), result::toString);
	}
}
