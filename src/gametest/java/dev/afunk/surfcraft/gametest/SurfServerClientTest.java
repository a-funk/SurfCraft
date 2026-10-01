package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The surf runs against a real dedicated server over a network connection, in survival: there the player is not the
 * single-player owner, so the server also checks "moved too quickly". A 320-block steep ramp at 2200 units/s, a 5:4 ramp
 * at 1500, a fast crossing of an A-frame's apex (the chord between the two slopes passes under the apex, which the
 * server's exact collision lifts over), client hitches that land several move packets in one server tick, and a long
 * vertical launch. No move may be corrected or logged as moved wrongly or too quickly, and no one kicked for floating.
 */
public class SurfServerClientTest implements FabricClientGameTest {
	static final double UPS = SurfClientTest.UPS;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ClientTests.enabled("server")) return;
		context.getInput().resizeWindow(1280, 720);
		ServerCorrections.EVENTS.clear();
		// A free port: other test runs on this machine may hold the default one.
		Properties properties = new Properties();
		properties.setProperty("server-port", String.valueOf(HttpUtil.getAvailablePort()));
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(properties);
				TestDedicatedServerConnection connection = server.connect();
				LogWatch log = new LogWatch()) {
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			int g = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			AFrame steep = new AFrame(SurfBlocks.STEEP_SURF_RAMP, 0, g, 0, 10, 320, false);
			AFrame gentle = new AFrame(SurfBlocks.SURF_RAMP, 40, g, 0, 6, 200, false);
			// The steep ramp covers the spawn point: step off its footprint first, or the player is buried in it.
			server.runCommand("tp @a 20.5 " + g + " -20.5");
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				steep.build(level);
				gentle.build(level);
			});
			context.waitTick();
			connection.waitForChunksRender();
			context.runOnClient(c -> c.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			context.getInput().pressKey(o -> o.keyHotbarSlots[8]);
			GameType mode = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().gameMode());
			boolean owner = server.computeOnServer(s -> s.isSingleplayerOwner(s.getPlayerList().getPlayers().getFirst().nameAndId()));
			System.out.printf("SURFCRAFT dedicated server: %s in %s, single-player owner %b%n", server.computeOnServer(s -> s.getClass().getSimpleName()), mode, owner);

			Surfer s = new Surfer(context, server, connection);
			context.runOnClient(c -> Surfer.controller(c).log = e -> System.out.println("SURFCRAFT controller: " + e));
			s.check(mode == GameType.SURVIVAL && !owner, "not a survival non-owner player: " + mode + ", owner " + owner);
			List<Long> nanos = new ArrayList<>();
			screenshotDuring(s, steep);
			SurfClientTest.surfRun(s, steep, "dedicated 2:1 at 2200 u/s", 4, 2200, nanos, false);
			SurfClientTest.surfRun(s, gentle, "dedicated 5:4 at 1500 u/s", 3, 1500, nanos, false);
			apexCrossing(s, gentle);
			hitch(s, gentle, 1500, 6, 50);
			hitch(s, steep, 2200, 10, 120);
			verticalLaunch(s, gentle);

			System.out.printf("SURFCRAFT dedicated server: %d log rejections %s, %d corrections, %d untracked ticks%n", log.rejections.size(), log.rejections,
					ServerCorrections.EVENTS.size(), s.untracked);
			s.check(log.rejections.isEmpty(), "server rejected moves: " + log.rejections);
			s.check(ServerCorrections.EVENTS.isEmpty(), "server corrections: " + ServerCorrections.EVENTS);
			s.assertClean("dedicated server");
		}
	}

	/** A first- and third-person shot of the steep ramp at speed, on the dedicated server. */
	static void screenshotDuring(Surfer s, AFrame ramp) {
		s.tp(ramp.eastX(5, 0.01), ramp.base() + 5, ramp.z0() + 3, 0, 12);
		s.velocity(new Vec3(-0.02, 0, 2200 * UPS));
		s.context.getInput().holdKey(o -> o.keyRight);
		for (int t = 0; t < 25; t++) s.tick();
		s.context.runOnClient(c -> c.gui.toastManager().clear());
		s.screenshot("dedicated_steep_2200_first_person");
		s.context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		s.tick();
		s.screenshot("dedicated_steep_2200_third_person");
		s.context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
		s.context.getInput().releaseKey(o -> o.keyRight);
		for (int t = 0; t < 80 && !s.sample().grounded(); t++) s.tick();
	}

	/**
	 * Up the east slope toward the ridge fast enough to fly over it within a tick or two, landing on the west side: the
	 * chords between those published positions pass under the apex.
	 */
	static void apexCrossing(Surfer s, AFrame ramp) {
		int crossings = 0;
		for (int k = 0; k < 4; k++) {
			s.tp(ramp.eastX(4.2, 0.01), ramp.base() + 4.2, ramp.z0() + 20 + 40 * k, 0, 0);
			s.velocity(new Vec3(-(500 + 250 * k) * UPS, (300 + 100 * k) * UPS, 1200 * UPS));
			boolean east = true;
			for (int t = 0; t < 40; t++) {
				Surfer.Sample x = s.tick();
				if (east && x.pos().x < ramp.ridge()) {
					east = false;
					crossings++;
				}
				if (!east && x.grounded()) break;
			}
			for (int t = 0; t < 40 && !s.sample().grounded(); t++) s.tick();
		}
		System.out.printf("SURFCRAFT dedicated apex crossings: %d of 4 runs went over the ridge%n", crossings);
		s.check(crossings >= 3, "only " + crossings + " runs crossed the apex");
	}

	/**
	 * A client hitch mid-surf (B1): vanilla's catch-up runs the missed ticks back to back, so {@code packets} move packets
	 * reach the server within one server tick. The surfer keeps its speed: no correction, no resync.
	 */
	static void hitch(Surfer s, AFrame ramp, double ups, int packets, int z) {
		s.tp(ramp.eastX(3, 0.01), ramp.base() + 3, ramp.z0() + z, 0, 8);
		s.velocity(new Vec3(-0.02, 0, ups * UPS));
		s.tick();
		s.resetCounters();
		s.context.getInput().holdKey(o -> o.keyRight);
		for (int t = 0; t < 8; t++) s.tick();
		double before = s.sample().speed();
		int corrections = ServerCorrections.EVENTS.size();
		s.context.runOnClient(c -> {
			for (int i = 1; i < packets; i++) {
				try (var gizmos = c.collectPerTickGizmos()) {
					c.tick();
				}
			}
		});
		double after = 0;
		for (int t = 0; t < 4; t++) after = s.tick().speed();
		s.context.getInput().releaseKey(o -> o.keyRight);
		Surfer.Sample end = s.sample();
		System.out.printf("SURFCRAFT client hitch at %.0f u/s, %d move packets in one server tick: %.1f -> %.1f u/s, resyncs %d, corrections %d%n", ups, packets, before, after,
				end.resyncs(), ServerCorrections.EVENTS.size() - corrections);
		s.check(end.resyncs() == 0 && after > before * 0.9, "a " + packets + "-packet hitch at " + ups + " u/s: " + before + " -> " + after + " u/s, resyncs " + end.resyncs());
		for (int t = 0; t < 80 && !s.sample().grounded(); t++) s.tick();
	}

	/**
	 * Launched straight up off the ridge at 3400 u/s (B7), the surfer rises for over 4 s under CS:S gravity: following
	 * gravity is falling, not floating, so the server does not kick it for flying.
	 */
	static void verticalLaunch(Surfer s, AFrame ramp) {
		s.tp(ramp.eastX(5.8, 0.01), ramp.base() + 5.8, ramp.z0() + 160, 0, 0);
		s.velocity(new Vec3(0, 3400 * UPS, 0));
		double top = 0;
		for (int t = 0; t < 100; t++) top = Math.max(top, s.tick().pos().y - ramp.base());
		System.out.printf("SURFCRAFT vertical launch at 3400 u/s: rose %.1f blocks in 100 ticks%n", top);
		s.check(top > 150, "the vertical launch rose only " + top + " blocks");
		s.tp(ramp.eastX(0, 2), ramp.base(), ramp.z0() + 160, 0, 0);
	}
}
