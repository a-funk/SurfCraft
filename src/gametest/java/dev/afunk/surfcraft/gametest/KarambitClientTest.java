package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.karambit.KarambitItem;
import dev.afunk.surfcraft.karambit.SurfModule;
import java.nio.file.Path;
import java.util.Comparator;
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
 * shot into build/gametest/screenshots.
 */
public class KarambitClientTest implements FabricClientGameTest {
	private static final Path SCREENSHOTS = Path.of(System.getProperty("surfcraft.screenshots", "screenshots"));

	@Override
	public void runTest(ClientGameTestContext context) {
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
				int length = world.getServer().computeOnServer(server -> rampLength(server.overworld(), g));
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
			context.takeScreenshot(TestScreenshotOptions.of("karambit_refused").withDestinationDir(SCREENSHOTS));
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
			context.takeScreenshot(TestScreenshotOptions.of("karambit_tooltip").withDestinationDir(SCREENSHOTS));
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
		Comparator<TestRamp.Cell> bottomUp = Comparator.comparingInt(TestRamp.Cell::y).thenComparingInt(c -> -c.u()).thenComparingInt(TestRamp.Cell::w);
		for (TestRamp ramp : new TestRamp[] {
				new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.WEST, new BlockPos(2, g, 0), 3, 6, 4, 2, 0),
				new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.EAST, new BlockPos(3, g, 0), 3, 6, 4, 2, 0)}) {
			String error = ramp.place(level, ramp.sorted(bottomUp));
			if (error != null) throw new AssertionError(error);
		}
		return g;
	}

	/** How many z-slices from z = 0 hold exactly the hand-built chunk's z = 0 slice. */
	private static int rampLength(ServerLevel level, int g) {
		int z = 0;
		while (z < 64 && sameSlice(level, g, z)) z++;
		return z;
	}

	private static boolean sameSlice(ServerLevel level, int g, int z) {
		for (int x = -1; x <= 6; x++) {
			for (int y = g; y < g + 7; y++) {
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
		context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(SCREENSHOTS));
	}
}
