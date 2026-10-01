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
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The surf runs against a real dedicated server over a network connection, in survival: there the player is not the
 * single-player owner, so the server also checks "moved too quickly". A 320-block steep ramp at 2200 units/s, a 5:4 ramp
 * at 1500, and a fast crossing of an A-frame's apex (the chord between the two slopes passes under the apex, which the
 * server's exact collision lifts over). No move may be corrected or logged as moved wrongly or too quickly.
 */
public class SurfServerClientTest implements FabricClientGameTest {
	static final double UPS = SurfClientTest.UPS;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ClientTests.enabled("server")) return;
		context.getInput().resizeWindow(1280, 720);
		ServerCorrections.EVENTS.clear();
		try (TestDedicatedServerContext server = context.worldBuilder().createServer(new Properties());
				TestDedicatedServerConnection connection = server.connect();
				LogWatch log = new LogWatch()) {
			server.runCommand("time set noon");
			server.runCommand("gamemode survival @a");
			int g = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			AFrame steep = new AFrame(SurfBlocks.STEEP_SURF_RAMP, 0, g, 0, 10, 320, false);
			AFrame gentle = new AFrame(SurfBlocks.SURF_RAMP, 40, g, 0, 6, 200, false);
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
}
