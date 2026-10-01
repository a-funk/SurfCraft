package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.karambit.SurfModule;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds a showcase in a superflat world at noon and takes 1280x720 screenshots into
 * build/gametest/screenshots: a 5:4 ramp (4 columns, 5 rows, 8 long) facing west back to back with a 2:1
 * ramp (3 columns, 6 rows, 8 long) facing east, both placed cell by cell with the joining rule, and two rows of single
 * blocks (5:4 at z = 13, 2:1 at z = 16) facing north, east, south and west from low x to high x. Both items sit in the
 * hotbar; one shot holds the 5:4 ramp. Then two 5:4 ramps side by side built by hand (the south one's foot a block
 * further east), the selection outline on a single cell and on a slope, and floating roofs (the default module's slope
 * cells only, and the whole module) over the ground in sun and in rain.
 * {@code -PclientTests=fps} measures the frame rate over six 16-tall A-frames 128 long, with ramp cells and with stone inside.
 */
public class RampShowcaseClientTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		boolean showcase = ClientTests.enabled("showcase"), fps = ClientTests.picked("fps");
		if (!showcase && !fps) return;
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			world.getServer().runCommand("time set noon");
			int g = world.getServer().computeOnServer(server -> build(server.overworld(), server.getPlayerList().getPlayers().getFirst()));
			world.getServer().runOnServer(server -> {
				Inventory inventory = server.getPlayerList().getPlayers().getFirst().getInventory();
				inventory.setItem(0, new ItemStack(SurfBlocks.SURF_RAMP));
				inventory.setItem(1, new ItemStack(SurfBlocks.STEEP_SURF_RAMP));
			});
			context.runOnClient(client -> {
				client.player.getAbilities().flying = true;
				client.options.chatVisibility().set(ChatVisiblity.HIDDEN);
			});
			context.getInput().pressKey(options -> options.keyHotbarSlots[8]); // an empty hand for the scene shots
			if (showcase) {
				shot(context, world, "overview_south_west", 1, g + 5, 17, 9, g + 2, 5);
				shot(context, world, "overview_north_east", 19, g + 5, -5, 10, g + 2, 5);
				shot(context, world, "slope_5_4_facing_west", 2.5, g + 3, 4, 8.5, g + 2.2, 4);
				shot(context, world, "slope_2_1_facing_east", 19.5, g + 3.5, 4, 12.5, g + 3, 4);
				shot(context, world, "singles_from_south", 9.5, g + 4, 22, 9.5, g, 14.5);
				shot(context, world, "singles_from_north", 9.5, g + 4.5, 9.5, 9.5, g, 15);
				look(context, world, "side_by_side", 51, g + 4, 7, 45, g + 1.5, 2);
				look(context, world, "side_by_side_profile", 45.5, g + 1.2, 9, 45.5, g + 2, 3);
				look(context, world, "outline_cell", 70.5, g + 1.3, 3.2, 70.5, g + 0.4, 5.5);
				look(context, world, "outline_slope", 5.5, g + 1.2, 3.5, 8.3, g + 1.6, 3.5);
				look(context, world, "roofs_shadow", 100.5, g + 4, -12, 100.5, g + 2, 6);
				context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
				context.waitTicks(40); // let the equip animation and the item name fade finish
				shot(context, world, "hand_and_hotbar", 4, g + 4, 13, 9, g + 2, 5);
				context.getInput().pressKey(options -> options.keyHotbarSlots[8]);
				world.getServer().runCommand("weather rain");
				context.waitTicks(120); // the rain fades in
				look(context, world, "roofs_rain_under_slope_cells", 93.5, g, 1.5, 93.5, g + 1.62, 12);
				look(context, world, "roofs_rain", 100.5, g + 3, -7, 100.5, g + 2, 4);
				world.getServer().runCommand("weather clear");
			}
			if (fps) fps(context, world, g);
		}
	}

	/** Builds the scenes on the ground and returns the ground's y (the first free block). */
	private static int build(ServerLevel level, ServerPlayer player) {
		int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		List<TestRamp> ramps = List.of(
				new TestRamp(SurfBlocks.SURF_RAMP, Direction.WEST, new BlockPos(10, g, 0), 4, 5, 8, 3, 0),
				new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.EAST, new BlockPos(11, g, 0), 3, 6, 8, 2, 0));
		for (TestRamp ramp : ramps) {
			String error = ramp.place(level, ramp.sorted(TestRamp.BOTTOM_UP));
			if (error != null) throw new AssertionError(error);
		}
		for (int b = 0; b < SurfBlocks.RAMPS.size(); b++) {
			SurfRampBlock block = SurfBlocks.RAMPS.get(b);
			int x = 6;
			for (Direction facing : Direction.Plane.HORIZONTAL) {
				BlockPos pos = new BlockPos(x, g, 13 + 3 * b);
				level.setBlock(pos, block.placementState(level, pos, facing), Block.UPDATE_ALL);
				x += 2;
			}
		}
		// Two 5:4 ramps side by side, built by hand: A (z 0..1), then B (z 2..3) with its foot one block further east.
		for (TestRamp ramp : List.of(new TestRamp(SurfBlocks.SURF_RAMP, Direction.EAST, new BlockPos(42, g, 0), 4, 5, 2, 3, 0),
				new TestRamp(SurfBlocks.SURF_RAMP, Direction.EAST, new BlockPos(43, g, 2), 4, 5, 2, 3, 0))) {
			ramp.placeByHand(player, ramp.sorted(TestRamp.BOTTOM_UP));
		}
		BlockPos single = new BlockPos(70, g, 5);
		level.setBlock(single, SurfBlocks.SURF_RAMP.placementState(level, single, Direction.NORTH), Block.UPDATE_ALL);
		// Roofs 6 up: the default module's slope cells only (a hollow ramp) at x = 90, the whole module at x = 104.
		SurfModule m = SurfModule.DEFAULT;
		BlockState[] cells = m.cells();
		for (int x = 0; x < m.width(); x++) for (int y = 0; y < m.height(); y++) for (int z = 0; z < m.length(); z++) {
			BlockState s = cells[SurfModule.index(m.width(), m.length(), x, y, z)];
			if (s.isAir()) continue;
			if (!SurfRampBlock.cell(s).full()) level.setBlock(new BlockPos(90 + x, g + 6 + y, z), s, Block.UPDATE_ALL);
			level.setBlock(new BlockPos(104 + x, g + 6 + y, z), s, Block.UPDATE_ALL);
		}
		return g;
	}

	/** Frames per second (Minecraft's 1 s counter, 5 samples) at one camera: empty, six 16-tall A-frames 128 long, then the same with stone inside. */
	private static void fps(ClientGameTestContext context, TestSingleplayerContext world, int g) {
		context.runOnClient(client -> {
			client.options.enableVsync().set(false);
			client.options.framerateLimit().set(260);
			client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		StringBuilder out = new StringBuilder("SURFCRAFT fps:");
		for (String fill : List.of("empty", "ramp cells inside", "stone inside")) {
			if (!fill.equals("empty")) world.getServer().runOnServer(server -> {
				for (int i = 0; i < 6; i++) new AFrame(SurfBlocks.SURF_RAMP, 2000 + 28 * i, g, 0, 16, 128, fill.equals("stone inside")).build(server.overworld());
			});
			look(context, world, "fps_" + fill.replace(' ', '_'), 2070, g + 20, -30, 2070, g + 6, 40);
			context.waitTicks(40);
			int[] samples = new int[5];
			for (int i = 0; i < samples.length; i++) {
				context.waitTicks(20);
				samples[i] = context.computeOnClient(Minecraft::getFps);
			}
			out.append(' ').append(fill).append(' ').append(Arrays.toString(samples)).append(" mean ").append(Arrays.stream(samples).average().orElse(0)).append(';');
		}
		System.out.println(out);
	}

	/** A scene shot: the camera at feet position (x, y, z), turned toward (tx, ty, tz) from the feet. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, double tx, double ty, double tz) {
		world.getServer().runCommand("tp @a %s %s %s facing %s %s %s".formatted(x, y, z, tx, ty, tz));
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(3);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(ClientTests.OUT));
	}

	/**
	 * An aimed shot: feet at (x, y, z), flying (set on the server, so it holds), the eye looking at (tx, ty, tz) so the
	 * crosshair is on it ({@code tp ... facing} aims from the feet, so the target is lowered by the eye height).
	 */
	private static void look(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, double tx, double ty, double tz) {
		world.getServer().runCommand("tp @a %s %s %s facing %s %s %s".formatted(x, y, z, tx, ty - 1.62, tz));
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		});
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(3);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(ClientTests.OUT));
	}
}
