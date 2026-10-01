package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.gametest.TestRamp.Cell;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds a showcase in a superflat world at noon and takes 1280x720 screenshots into
 * build/gametest/screenshots: a 5:4 ramp (4 columns, 5 rows, 8 long) facing west back to back with a 2:1
 * ramp (3 columns, 6 rows, 8 long) facing east, both placed cell by cell with the joining rule, and two rows of single
 * blocks (5:4 at z = 13, 2:1 at z = 16) facing north, east, south and west from low x to high x. Both items sit in the
 * hotbar; the last shot holds the 5:4 ramp.
 */
public class RampShowcaseClientTest implements FabricClientGameTest {
	/** Set by build.gradle (build/gametest/screenshots); defaults to the game directory's screenshots folder. */
	private static final Path SCREENSHOTS = Path.of(System.getProperty("surfcraft.screenshots", "screenshots"));

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ClientTests.enabled("showcase")) return;
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder()
				.adjustSettings(settings -> settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE))
				.create()) {
			world.getServer().runCommand("time set noon");
			int g = world.getServer().computeOnServer(server -> build(server.overworld()));
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
			shot(context, world, "overview_south_west", 1, g + 5, 17, 9, g + 2, 5);
			shot(context, world, "overview_north_east", 19, g + 5, -5, 10, g + 2, 5);
			shot(context, world, "slope_5_4_facing_west", 2.5, g + 3, 4, 8.5, g + 2.2, 4);
			shot(context, world, "slope_2_1_facing_east", 19.5, g + 3.5, 4, 12.5, g + 3, 4);
			shot(context, world, "singles_from_south", 9.5, g + 4, 22, 9.5, g, 14.5);
			shot(context, world, "singles_from_north", 9.5, g + 4.5, 9.5, 9.5, g, 15);
			context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
			context.waitTicks(40); // let the equip animation and the item name fade finish
			shot(context, world, "hand_and_hotbar", 4, g + 4, 13, 9, g + 2, 5);
		}
	}

	/** Builds the showcase on the ground and returns the ground's y (the first free block). */
	private static int build(ServerLevel level) {
		int g = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		Comparator<Cell> bottomUp = Comparator.comparingInt(Cell::y).thenComparingInt(c -> -c.u()).thenComparingInt(Cell::w);
		List<TestRamp> ramps = List.of(
				new TestRamp(SurfBlocks.SURF_RAMP, Direction.WEST, new BlockPos(10, g, 0), 4, 5, 8, 3, 0),
				new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.EAST, new BlockPos(11, g, 0), 3, 6, 8, 2, 0));
		for (TestRamp ramp : ramps) {
			String error = ramp.place(level, ramp.sorted(bottomUp));
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
		return g;
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, double tx, double ty, double tz) {
		world.getServer().runCommand("tp @a %s %s %s facing %s %s %s".formatted(x, y, z, tx, ty, tz));
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(3);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(TestScreenshotOptions.of(name).withDestinationDir(SCREENSHOTS));
	}
}
