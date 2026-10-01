package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.client.SurfController;
import dev.afunk.surfcraft.client.SurfDriven;
import dev.afunk.surfcraft.physics.TickDriver;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Drives the client player through a test: teleports, velocities, keys, one tick at a time, sampling both sides. It
 * also checks what must hold every tick on any server: the server's copy of the player is always one of the client's
 * recent positions (never a correction).
 */
final class Surfer {
	/** Per tick: the client player, the controller, and the server's copy of the player. */
	record Sample(boolean driving, Vec3 pos, AABB box, double speed, Vec3 deltaMovement, boolean grounded, boolean surfed, float yaw, Vec3 server,
			float health, int resyncs, int adopts, long nanos, boolean inWater, boolean flying) {
	}

	/** The client player's position after every client tick (the test waits run ticks of their own too). Client thread. */
	private static final ArrayDeque<Vec3> HISTORY = new ArrayDeque<>();

	static {
		ClientTickEvents.END_CLIENT_TICK.register(c -> {
			if (c.player == null) return;
			HISTORY.addLast(c.player.position());
			while (HISTORY.size() > 8) HISTORY.removeFirst();
		});
	}

	final ClientGameTestContext context;
	final TestServerContext server;
	final TestServerConnection connection;
	final List<String> failures = new ArrayList<>();
	/** Ticks the server's copy of the player was none of the client's recent positions. */
	int untracked;

	Surfer(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		this.context = context;
		this.server = server;
		this.connection = connection;
	}

	static SurfController controller(Minecraft c) {
		return ((SurfDriven) c.player).surfcraft$controller();
	}

	void tp(double x, double y, double z, float yaw, float pitch) {
		String command = "tp @a %s %s %s %s %s".formatted(x, y, z, yaw, pitch);
		server.runCommand(command);
		connection.waitForClientboundPackets();
		// A long run leaves its start outside the client's view distance. Teleported into chunks the client does not have
		// yet, the player (vanilla, no ramp in sight) falls through terrain only the server has: wait, then teleport again.
		// (ClientLevel.hasChunk is always true; the chunk source knows.)
		int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
		java.util.function.Predicate<Minecraft> loaded = c -> {
			for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) if (!c.level.getChunkSource().hasChunk(cx + i, cz + j)) return false;
			return true;
		};
		if (!context.computeOnClient(loaded::test)) {
			context.waitFor(loaded, 400);
			server.runCommand(command);
		}
		teleported();
	}

	/** After a server teleport: the client has it, and the server holds it until the client's next move. */
	void teleported() {
		connection.waitForClientboundPackets();
		context.runOnClient(c -> {
			HISTORY.clear();
			HISTORY.add(c.player.position());
		});
	}

	void velocity(Vec3 blocksPerTick) {
		context.runOnClient(c -> c.player.setDeltaMovement(blocksPerTick));
	}

	/** Clears the controller's counters (after a teleport has been picked up). */
	void resetCounters() {
		context.runOnClient(c -> {
			SurfController sc = controller(c);
			sc.resyncs = 0;
			sc.adopts = 0;
		});
	}

	Sample tick() {
		context.waitTick();
		return observe();
	}

	/** {@link #tick}'s checks for a tick something else ran (a frame capture that ticks itself). */
	Sample observe() {
		Sample s = sample();
		List<Vec3> recent = context.computeOnClient(c -> List.copyOf(HISTORY));
		// The server's copy lags by a tick or two but must be exactly one of the client's positions.
		if (!recent.contains(s.server)) {
			untracked++;
			failures.add("server at " + s.server + ", client recently at " + recent);
		}
		if (s.health < lastHealth) System.out.printf("SURFCRAFT health %.1f -> %.1f at %s, %s, driving %b, grounded %b%n", lastHealth, s.health, s.pos,
				context.computeOnClient(c -> c.player.getLastDamageSource() == null ? "?" : c.player.getLastDamageSource().getMsgId()), s.driving, s.grounded);
		lastHealth = s.health;
		return s;
	}

	/** NaN until the first sample: no "drop" from nothing. */
	private float lastHealth = Float.NaN;

	Sample sample() {
		Sample client = context.computeOnClient(c -> {
			LocalPlayer p = c.player;
			SurfController sc = controller(c);
			TickDriver d = sc.driver();
			return new Sample(sc.driving(), p.position(), p.getBoundingBox(), sc.speed(), p.getDeltaMovement(), d != null && d.core.grounded,
					d != null && d.surfed, p.getYRot(), null, p.getHealth(), sc.resyncs, sc.adopts, sc.nanos, p.isInWater(), p.getAbilities().flying);
		});
		Object[] s = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
			return new Object[] {p.position(), p.getHealth()};
		});
		return new Sample(client.driving, client.pos, client.box, client.speed, client.deltaMovement, client.grounded, client.surfed, client.yaw,
				(Vec3) s[0], (Float) s[1], client.resyncs, client.adopts, client.nanos, client.inWater, client.flying);
	}

	void check(boolean ok, String message) {
		if (!ok) failures.add(message);
	}

	void screenshot(String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(ClientTests.OUT));
	}

	/** Speed in units/s from a deltaMovement in blocks/tick (horizontal only). */
	static double horizontal(Vec3 blocksPerTick) {
		return blocksPerTick.horizontalDistance() / SurfController.UNITS_TO_BLOCKS_PER_TICK;
	}

	void assertClean(String what) {
		if (!failures.isEmpty()) throw new AssertionError(what + ":\n  " + String.join("\n  ", failures.subList(0, Math.min(25, failures.size()))));
	}
}
