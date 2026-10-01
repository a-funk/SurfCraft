package dev.afunk.surfcraft.gametest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** What the server corrected (filled by a test-only mixin on the server's move handling; any server thread). */
public final class ServerCorrections {
	public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

	private ServerCorrections() {
	}

	public static void record(String event) {
		EVENTS.add(event);
		System.out.println("SURFCRAFT server: " + event);
	}
}
