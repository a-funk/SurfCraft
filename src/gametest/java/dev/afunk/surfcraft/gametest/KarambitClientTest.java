package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.karambit.KarambitItem;
import dev.afunk.surfcraft.karambit.SurfModule;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The Karambit in a real client (superflat, noon, 1280x720), through real input: a two-sided 2:1 ramp chunk (6 wide,
 * 6 tall, 4 long) built cell by cell with the joining rule is copied (sneak + use) and extended three times (use),
 * a fresh knife places its default module, and the green (fits) and red (blocked) previews and the knife in hand are
 * shot into build/gametest/screenshots. Then a 16-tall 5:4 A-frame is copied and extended to 208 blocks, and adventure
 * mode is checked: no preview, and a sneak + click undoes nothing.
 */
public class KarambitClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ClientTests.enabled("karambit")) return;
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			world.getServer().runCommand("time set noon");
			int g = world.getServer().computeOnServer(server -> build(server.overworld()));
			world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.getInventory().setItem(0, new ItemStack(KarambitItem.KARAMBIT));
				player.getInventory().setItem(1, new ItemStack(KarambitItem.KARAMBIT));
			});
			context.runOnClient(client -> client.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
			shot(context, world, "karambit_chunk", -6, g + 7, -7, 3, g + 2, 2);

			// Copy: from the north end, looking south along the ramp, sneak + use on it.
			aim(context, world, 1.5, g + 3, -3.5, 1.5, g + 2.5, 0.5);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(options -> options.keyShift);
			SurfModule module = world.getServer().computeOnServer(server -> player(server).getInventory().getItem(0).get(KarambitItem.MODULE));
			if (module == null || module.width() != 6 || module.height() != 6 || module.length() != 4 || module.blocks() != 96) {
				throw new AssertionError("copied module " + module);
			}
			// Extend three times: each use puts the module after the ramp's south end.
			for (int i = 1; i <= 3; i++) {
				aim(context, world, 1.5, g + 3, -3.5, 1.5, g + 2.5, 0.5);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(3);
				int length = world.getServer().computeOnServer(server -> rampLength(server.overworld(), -1, 6, g, g + 6));
				if (length != 4 + 4 * i) throw new AssertionError("after extension " + i + " the ramp is " + length + " long");
			}
			shot(context, world, "karambit_extended", -9, g + 9, -6, 3, g + 1, 9);

			// A fresh knife places its default module: the classic two-sided 51-degree ramp.
			context.getInput().pressKey(options -> options.keyHotbarSlots[1]);
			aim(context, world, 14.5, g + 1, 1.5, 14.5, g, 4.5);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(3);
			BlockPos defaultMin = new BlockPos(11, g, 4), defaultMax = new BlockPos(18, g + 4, 11);
			long placed = world.getServer().computeOnServer(server -> count(server, defaultMin, defaultMax));
			if (placed != SurfModule.DEFAULT.blocks()) throw new AssertionError("default module placed " + placed + " blocks");
			shot(context, world, "karambit_default_module", 22, g + 6, 1, 14.5, g + 1, 8);

			// Previews, seen from behind the player (third person) once the action bar has faded: the box the click would
			// fill, green where it fits, red (blocked cells outlined) where it overlaps the two ramps.
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
				client.options.fov().set(90);
			});
			context.waitTicks(60);
			shot(context, world, "karambit_preview_green", 27.5, g + 2, 3, 27.5, g, 5.5);
			shot(context, world, "karambit_preview_red", 8.5, g + 2, 3, 8.5, g, 5.5);
			// Clicking there anyway is refused: nothing is placed, and the blocked cells puff red dust.
			BlockPos redMin = new BlockPos(5, g, 5), redMax = new BlockPos(12, g + 4, 12);
			long before = world.getServer().computeOnServer(server -> count(server, redMin, redMax));
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(4);
			context.takeScreenshot(TestScreenshotOptions.of("karambit_refused").withDestinationDir(ClientTests.OUT));
			long after = world.getServer().computeOnServer(server -> count(server, redMin, redMax));
			if (after != before) throw new AssertionError("a refused placement changed " + (after - before) + " blocks");
			// Holding the copied module by the long ramp: the box shows where the next extension goes.
			context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
			shot(context, world, "karambit_preview_extend", -2.5, g + 2, 11.5, 0.5, g + 1.5, 13.5);
			// The knife in hand and in the hotbar (looking past reach: no preview).
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				client.options.fov().set(70);
			});
			context.waitTicks(40);
			shot(context, world, "karambit_in_hand", -7, g + 4, 22, 1, g + 3, 6);
			// The tooltip (module and controls): the creative inventory (195x136, hotbar row at 9, 112) open, the cursor on
			// the copied knife in hotbar slot 0.
			context.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
			double[] cursor = context.computeOnClient(client -> {
				Window window = client.getWindow();
				double scale = window.getScreenWidth() / (double) window.getGuiScaledWidth();
				int left = (window.getGuiScaledWidth() - 195) / 2, top = (window.getGuiScaledHeight() - 136) / 2;
				return new double[] {(left + 17) * scale, (top + 120) * scale};
			});
			context.getInput().setCursorPos(cursor[0], cursor[1]);
			context.waitTicks(5);
			context.takeScreenshot(TestScreenshotOptions.of("karambit_tooltip").withDestinationDir(ClientTests.OUT));
			context.setScreen(() -> null);

			// Undo: sneak + use looking at the sky takes back the last placement, the default module.
			aim(context, world, 14.5, g + 8, 0.5, 14.5, g + 30, 20);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(options -> options.keyShift);
			long left = world.getServer().computeOnServer(server -> count(server, defaultMin, defaultMax));
			if (left != 0) throw new AssertionError("undo left " + left + " blocks of the default module");

			// A surf-sized ramp: a 16-tall 5:4 A-frame 8 long (ridge at x = 60) copied with a fresh knife from its north end,
			// then extended 25 times to 208 blocks by a player walking along its east foot, clicking it 3 blocks from its south
			// end (extend never loads chunks, so the end must be in view).
			world.getServer().runOnServer(server -> {
				new AFrame(SurfBlocks.SURF_RAMP, 60, g, 0, 16, 8, false).build(server.overworld());
				player(server).getInventory().setItem(2, new ItemStack(KarambitItem.KARAMBIT));
			});
			context.getInput().pressKey(options -> options.keyHotbarSlots[2]);
			aim(context, world, 63.5, g + 2, -3.5, 63.5, g + 1.5, 0.5);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(options -> options.keyShift);
			for (int i = 1; i <= 25; i++) {
				aim(context, world, 76.5, g, 8 * i - 4.5, 72.3, g + 0.3, 8 * i - 3);
				context.getInput().pressKey(options -> options.keyUse);
				context.waitTicks(3);
			}
			int surfLength = world.getServer().computeOnServer(server -> rampLength(server.overworld(), 46, 73, g, g + 16));
			if (surfLength != 208) throw new AssertionError("the A-frame is " + surfLength + " long after 25 extensions, want 208");
			// The whole ramp needs more than the tests' 5-chunk view. The server then sends a round area, which the harness's
			// square chunk check never sees complete, so this shot waits a fixed time instead.
			context.runOnClient(client -> client.options.renderDistance().set(16));
			aim(context, world, 108, g + 34, 236, 62, g + 6, 120);
			context.waitTicks(100);
			context.takeScreenshot(TestScreenshotOptions.of("karambit_surf_ramp_208").withDestinationDir(ClientTests.OUT));
			context.runOnClient(client -> client.options.renderDistance().set(5));

			// Adventure mode: no preview, and a sneak + click on a ramp (which reaches use(), as ItemStack.useOn passes without
			// building) says why and undoes nothing.
			world.getServer().runCommand("gamemode adventure @a");
			BlockPos surfMin = new BlockPos(46, g, 0), surfMax = new BlockPos(73, g + 15, 207);
			long surfBlocks = world.getServer().computeOnServer(server -> count(server, surfMin, surfMax));
			aim(context, world, 63.5, g + 2, -3.5, 63.5, g + 1.5, 0.5);
			context.getInput().holdKey(options -> options.keyShift);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(2);
			context.getInput().releaseKey(options -> options.keyShift);
			context.waitTicks(3);
			context.takeScreenshot(TestScreenshotOptions.of("karambit_adventure").withDestinationDir(ClientTests.OUT));
			long surfLeft = world.getServer().computeOnServer(server -> count(server, surfMin, surfMax));
			if (surfLeft != surfBlocks) throw new AssertionError("an adventure sneak-click changed " + (surfBlocks - surfLeft) + " blocks");
			world.getServer().runCommand("gamemode creative @a");
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	/** Non-air blocks in the box from min to max (inclusive). */
	private static long count(MinecraftServer server, BlockPos min, BlockPos max) {
		return BlockPos.betweenClosedStream(min, max).filter(p -> !server.overworld().getBlockState(p).isAir()).count();
	}

	/** A two-sided 2:1 ramp, ridge along z at x = 3, x 0..5, 6 tall, z 0..3, built bottom up; returns the ground's y. */
	private static int build(ServerLevel level) {
		int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		for (TestRamp ramp : new TestRamp[] {
				new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.WEST, new BlockPos(2, g, 0), 3, 6, 4, 2, 0),
				new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.EAST, new BlockPos(3, g, 0), 3, 6, 4, 2, 0)}) {
			String error = ramp.place(level, ramp.sorted(TestRamp.BOTTOM_UP));
			if (error != null) throw new AssertionError(error);
		}
		return g;
	}

	/** How many z-slices from z = 0 hold exactly the z = 0 slice, over x0..x1 and y0..y1. */
	private static int rampLength(ServerLevel level, int x0, int x1, int y0, int y1) {
		int z = 0;
		while (z < 512 && sameSlice(level, x0, x1, y0, y1, z)) z++;
		return z;
	}

	private static boolean sameSlice(ServerLevel level, int x0, int x1, int y0, int y1, int z) {
		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				if (level.getBlockState(new BlockPos(x, y, z)) != level.getBlockState(new BlockPos(x, y, 0))) return false;
			}
		}
		return true;
	}

	/**
	 * Puts the player at feet position (x, y, z), flying, with the eye looking at (tx, ty, tz). {@code tp ... facing} aims
	 * from the feet, so the target is lowered by the eye height. Flying is set on the server and synced: a client-only flag
	 * is reset on the ground or by the next abilities sync, and a falling camera's click misses.
	 */
	private static void aim(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z, double tx, double ty, double tz) {
		world.getServer().runCommand("tp @a %s %s %s facing %s %s %s".formatted(x, y, z, tx, ty - 1.62, tz));
		world.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		});
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(3);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, double tx, double ty, double tz) {
		aim(context, world, x, y, z, tx, ty, tz);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(ClientTests.OUT));
	}
}
