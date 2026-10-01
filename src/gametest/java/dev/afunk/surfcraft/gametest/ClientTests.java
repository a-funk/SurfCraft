package dev.afunk.surfcraft.gametest;

import java.nio.file.Path;
import java.util.Arrays;

/** Shared settings for the client game tests. */
final class ClientTests {
	/** Set by build.gradle (build/gametest/screenshots); defaults to the game directory's screenshots folder. */
	static final Path OUT = Path.of(System.getProperty("surfcraft.screenshots", "screenshots"));
	private static final String ONLY = System.getProperty("surfcraft.clientTests", "");

	private ClientTests() {
	}

	/** Whether to run the named test: all of them unless {@code -PclientTests=a,b} picked some. */
	static boolean enabled(String name) {
		return ONLY.isBlank() || picked(name);
	}

	/** Whether {@code -PclientTests} names it: for measurements the default run skips. */
	static boolean picked(String name) {
		return Arrays.asList(ONLY.split(",")).contains(name);
	}
}
