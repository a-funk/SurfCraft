package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.SurfCraft;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.karambit.KarambitItem;
import dev.afunk.surfcraft.karambit.ModulePlacer;
import dev.afunk.surfcraft.karambit.SurfModule;
import dev.afunk.surfcraft.physics.RampCell;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** The Karambit through its item: copy, extend, free placement, all-or-nothing, survival costs, undo, data. */
public class KarambitGameTests {
	private static final String AREA = "surfcraft-gametest:karambit_area", LONG_AREA = "surfcraft-gametest:long_area";
	private static final SurfRampBlock RAMP = SurfBlocks.SURF_RAMP;
	private static final BlockState FULL_EAST = RAMP.defaultBlockState().setValue(SurfRampBlock.FACING, Direction.EAST).setValue(RAMP.cut, RAMP.p + RAMP.q);
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();

	/** A mock player in {@code mode} looking {@code look}, holding a fresh Karambit (the default module). */
	private static Player player(GameTestHelper helper, GameType mode, Direction look) {
		Player player = helper.makeMockPlayer(mode);
		mode.updatePlayerAbilities(player.getAbilities());
		player.setYRot(look.toYRot());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(KarambitItem.KARAMBIT));
		return player;
	}

	/** Right-clicks {@code face} of the block at absolute {@code pos} with the held Karambit, sneaking or not. */
	private static void click(Player player, BlockPos pos, Direction face, boolean sneak) {
		player.setShiftKeyDown(sneak);
		player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false)));
		player.setShiftKeyDown(false);
	}

	private static String key(Component message) {
		return message.getContents() instanceof TranslatableContents t ? t.getKey() : message.getString();
	}

	private static int count(Player player, Item item) {
		return player.getInventory().clearOrCountMatchingItems(s -> s.is(item), true, 0, player.inventoryMenu.getCraftSlots());
	}

	/** Copy a hand-built ramp, extend it twice looking south and once looking north: every cell is on the plane. */
	@GameTest(structure = AREA)
	public void copyThenExtendContinuesThePlane(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 8));
		// Facing east, 3 columns, 4 rows, 3 long; a stone above the slope and a chest (not copied) in its box.
		TestRamp ramp = new TestRamp(RAMP, Direction.EAST, origin, 3, 4, 3, 2, 0);
		String built = ramp.place(level, ramp.sorted(TestRamp.BOTTOM_UP));
		helper.assertTrue(built == null, "hand-built ramp: " + built);
		BlockPos stone = origin.offset(2, 3, 1), chest = origin.offset(2, 2, 0);
		level.setBlockAndUpdate(stone, STONE);
		level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());

		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		click(player, origin, Direction.UP, true);
		SurfModule module = player.getMainHandItem().get(KarambitItem.MODULE);
		helper.assertTrue(module != null && module.width() == 3 && module.height() == 4 && module.length() == 3 && module.blocks() == 28,
				"copied module " + module);
		click(player, origin, Direction.UP, false);
		click(player, origin.offset(0, 0, 3), Direction.UP, false);
		player.setYRot(Direction.NORTH.toYRot());
		ModulePlacer.Plan north = ModulePlacer.plan(level, player, player.getMainHandItem(), origin.offset(1, 0, 1), Direction.UP);
		helper.assertTrue(north.ok(), "extending north: " + north.problem() + " " + north.box() + " blocked " + north.blocked());
		click(player, origin.offset(1, 0, 1), Direction.UP, false);

		BlockPos seed = origin.offset(2, 0, 0);
		for (BlockPos pos : BlockPos.betweenClosed(origin.offset(0, 0, -3), origin.offset(2, 3, 8))) {
			int c = RampCell.continueCut(RAMP.p, RAMP.q, RAMP.p, SurfRampBlock.cellU(Direction.EAST, seed), seed.getY(), SurfRampBlock.cellU(Direction.EAST, pos), pos.getY());
			BlockState want = c >= 1 ? FULL_EAST.setValue(RAMP.cut, Math.min(c, RAMP.p + RAMP.q))
					: pos.equals(chest) ? Blocks.CHEST.defaultBlockState()
					: pos.getX() == stone.getX() && pos.getY() == stone.getY() && Math.floorMod(pos.getZ() - origin.getZ(), 3) == 1 ? STONE
					: Blocks.AIR.defaultBlockState();
			helper.assertValueEqual(level.getBlockState(pos).toString(), want.toString(), "cell " + pos.subtract(origin) + " of the extended ramp");
		}
		helper.assertTrue(level.getBlockState(origin.offset(0, 0, -4)).isAir() && level.getBlockState(origin.offset(0, 0, 9)).isAir(), "12 long");
		helper.succeed();
	}

	/** A module turns with the player: forward is the look, and the module's +x (east, the ramp's facing) turns to the player's left. */
	@GameTest(structure = AREA)
	public void freePlacementTurnsWithTheLook(GameTestHelper helper) {
		SurfModule module = SurfModule.of(1, 1, 3, new BlockState[] {FULL_EAST, STONE, Blocks.GOLD_BLOCK.defaultBlockState()});
		BlockPos ground = helper.absolutePos(new BlockPos(12, 0, 12)), start = ground.above();
		helper.getLevel().setBlockAndUpdate(ground, STONE);
		for (Direction look : Direction.Plane.HORIZONTAL) {
			Player player = player(helper, GameType.CREATIVE, look);
			player.getMainHandItem().set(KarambitItem.MODULE, module);
			click(player, ground, Direction.UP, false);
			BlockState first = helper.getLevel().getBlockState(start);
			helper.assertTrue(first.is(RAMP) && first.getValue(SurfRampBlock.FACING) == look.getCounterClockWise(), "looking " + look + ": start " + first);
			helper.assertTrue(helper.getLevel().getBlockState(start.relative(look)).is(Blocks.STONE), "looking " + look + ": middle");
			helper.assertTrue(helper.getLevel().getBlockState(start.relative(look, 2)).is(Blocks.GOLD_BLOCK), "looking " + look + ": end");
			for (int i = 0; i < 3; i++) helper.getLevel().setBlockAndUpdate(start.relative(look, i), Blocks.AIR.defaultBlockState());
		}
		helper.succeed();
	}

	/** One occupied cell refuses the whole module, names that cell, and places nothing. */
	@GameTest(structure = AREA)
	public void blockedCellRefusesTheWholeModule(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		BlockPos ground = helper.absolutePos(new BlockPos(12, 0, 2));
		level.setBlockAndUpdate(ground, STONE);
		ModulePlacer.Plan plan = ModulePlacer.plan(level, player, player.getMainHandItem(), ground, Direction.UP);
		helper.assertTrue(plan.ok(), "the default module fits: " + plan.problem());
		BlockPos obstacle = plan.cells().entrySet().stream().filter(e -> !e.getValue().isAir()).skip(100).findFirst().orElseThrow().getKey();
		level.setBlockAndUpdate(obstacle, STONE);
		ModulePlacer.Result result = ModulePlacer.place(level, player, player.getMainHandItem(), ground, Direction.UP);
		helper.assertFalse(result.ok(), "placed over a stone block");
		helper.assertValueEqual(result.blocked(), List.of(obstacle), "blocked cells");
		helper.assertValueEqual(key(result.message()), "surfcraft.karambit.blocked", "refusal");
		click(player, ground, Direction.UP, false);
		for (BlockPos pos : plan.cells().keySet()) {
			helper.assertTrue(pos.equals(obstacle) ? level.getBlockState(pos).is(Blocks.STONE) : level.getBlockState(pos).isAir(), "nothing placed at " + pos);
		}
		helper.succeed();
	}

	/** Survival pays one item per block: a refusal lists what's missing and takes nothing; a placement takes exactly that. */
	@GameTest(structure = AREA)
	public void survivalPaysForEveryBlock(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = player(helper, GameType.SURVIVAL, Direction.SOUTH);
		player.getMainHandItem().set(KarambitItem.MODULE, SurfModule.of(1, 1, 3, new BlockState[] {FULL_EAST, FULL_EAST, STONE}));
		BlockPos ground = helper.absolutePos(new BlockPos(12, 0, 12));
		level.setBlockAndUpdate(ground, STONE);
		player.getInventory().add(new ItemStack(RAMP, 1));

		ModulePlacer.Result refused = ModulePlacer.place(level, player, player.getMainHandItem(), ground, Direction.UP);
		helper.assertValueEqual(key(refused.message()), "surfcraft.karambit.missing", "refusal");
		String missing = ((Component) ((TranslatableContents) refused.message().getContents()).getArgs()[0]).getString();
		helper.assertValueEqual(missing, "1 " + new ItemStack(RAMP).getHoverName().getString() + ", 1 " + new ItemStack(Items.STONE).getHoverName().getString(), "missing list");
		helper.assertValueEqual(count(player, RAMP.asItem()), 1, "ramps after a refusal");
		helper.assertTrue(level.getBlockState(ground.above()).isAir(), "nothing placed");

		player.getInventory().add(new ItemStack(RAMP, 4));
		player.getInventory().add(new ItemStack(Items.STONE, 2));
		click(player, ground, Direction.UP, false);
		helper.assertTrue(level.getBlockState(ground.above()).is(RAMP) && level.getBlockState(ground.above().south(2)).is(Blocks.STONE), "placed");
		helper.assertValueEqual(count(player, RAMP.asItem()), 3, "ramps left");
		helper.assertValueEqual(count(player, Items.STONE), 1, "stone left");
		helper.succeed();
	}

	/** Undo restores what was there (grass included) and refunds survival items, except for cells changed since. */
	@GameTest(structure = AREA)
	public void undoRestoresAndRefunds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = player(helper, GameType.SURVIVAL, Direction.SOUTH);
		player.getMainHandItem().set(KarambitItem.MODULE, SurfModule.of(1, 1, 3, new BlockState[] {FULL_EAST, FULL_EAST, STONE}));
		BlockPos ground = helper.absolutePos(new BlockPos(12, 0, 12)), start = ground.above();
		for (int i = 0; i < 3; i++) level.setBlockAndUpdate(ground.south(i), Blocks.GRASS_BLOCK.defaultBlockState());
		level.setBlockAndUpdate(start.south(), Blocks.SHORT_GRASS.defaultBlockState());
		player.getInventory().add(new ItemStack(RAMP, 2));
		player.getInventory().add(new ItemStack(Items.STONE, 1));
		click(player, ground, Direction.UP, false);
		helper.assertTrue(level.getBlockState(start.south()).is(RAMP) && count(player, RAMP.asItem()) == 0 && count(player, Items.STONE) == 0, "placed and paid");

		level.setBlockAndUpdate(start.south(2), Blocks.AIR.defaultBlockState()); // mined since: not restored or refunded
		player.setShiftKeyDown(true);
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		helper.assertTrue(level.getBlockState(start).isAir() && level.getBlockState(start.south()).is(Blocks.SHORT_GRASS) && level.getBlockState(start.south(2)).isAir(), "restored");
		helper.assertValueEqual(count(player, RAMP.asItem()), 2, "ramps refunded");
		helper.assertValueEqual(count(player, Items.STONE), 0, "stone not refunded (mined since)");
		helper.assertValueEqual(key(ModulePlacer.undo(level, player).message()), "surfcraft.karambit.nothing_to_undo", "second undo");
		helper.succeed();
	}

	/** A module whose end slice differs from the clicked ramp's (a 5:4 module on a 2:1 ramp) is refused; nothing is placed. */
	@GameTest(structure = AREA)
	public void extendRefusesAMismatchedRamp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos gentle = helper.absolutePos(new BlockPos(2, 1, 4)), steep = helper.absolutePos(new BlockPos(12, 1, 4));
		TestRamp a = new TestRamp(RAMP, Direction.EAST, gentle, 3, 4, 3, 2, 0), b = new TestRamp(SurfBlocks.STEEP_SURF_RAMP, Direction.EAST, steep, 3, 4, 3, 2, 0);
		String built = a.place(level, a.sorted(TestRamp.BOTTOM_UP)), builtSteep = b.place(level, b.sorted(TestRamp.BOTTOM_UP));
		helper.assertTrue(built == null && builtSteep == null, "hand-built ramps: " + built + ", " + builtSteep);
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		click(player, gentle, Direction.UP, true);
		ModulePlacer.Plan plan = ModulePlacer.plan(level, player, player.getMainHandItem(), steep, Direction.UP);
		helper.assertTrue(!plan.ok() && key(plan.problem()).equals("surfcraft.karambit.mismatch"), "plan on the steep ramp: " + plan.problem());
		click(player, steep, Direction.UP, false);
		for (BlockPos pos : BlockPos.betweenClosed(steep.offset(0, 0, 3), steep.offset(2, 3, 8))) helper.assertTrue(level.getBlockState(pos).isAir(), "nothing placed at " + pos);
		// The same module does continue its own ramp.
		helper.assertTrue(ModulePlacer.plan(level, player, player.getMainHandItem(), gentle, Direction.UP).ok(), "the module continues its own ramp");
		helper.succeed();
	}

	/**
	 * The default module, placed and then extended with a fresh knife, is a two-sided 5:4 ramp: each side's slope is one
	 * plane through its outer ground edge and the ridge (x = 4, y = 5 from the module's corner), full cells lie under it,
	 * air above it, and every slice along the ridge is the same.
	 */
	@GameTest(structure = AREA)
	public void defaultModuleIsATwoSidedRamp(GameTestHelper helper) {
		SurfModule module = SurfModule.DEFAULT;
		helper.assertTrue(module.width() == 8 && module.height() == 5 && module.length() == 8 && module.blocks() == 224, "default module " + module);
		ServerLevel level = helper.getLevel();
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		BlockPos ground = helper.absolutePos(new BlockPos(12, 0, 2)), min = helper.absolutePos(new BlockPos(9, 1, 2));
		level.setBlockAndUpdate(ground, STONE);
		click(player, ground, Direction.UP, false);
		checkTwoSided(helper, min, 8);
		click(player, min.offset(3, 0, 3), Direction.UP, false);
		checkTwoSided(helper, min, 16);
		helper.succeed();
	}

	private static void checkTwoSided(GameTestHelper helper, BlockPos min, int length) {
		double l = Math.hypot(RAMP.p, RAMP.q);
		// n·(x, y, z) <= d, x and y from the module's corner: west through (0, 0) and (4, 5), east through (8, 0) and (4, 5).
		double[] west = {-RAMP.p / l, RAMP.q / l, 0, 0}, east = {RAMP.p / l, RAMP.q / l, 0, 8 * RAMP.p / l};
		int ramps = 0;
		for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, 0, 0), min.offset(8, 5, length))) {
			BlockPos at = pos.subtract(min);
			BlockState state = helper.getLevel().getBlockState(pos);
			boolean inside = at.getX() >= 0 && at.getX() < 8 && at.getY() < 5 && at.getZ() < length;
			helper.assertTrue(inside || state.isAir(), "outside the module at " + at);
			if (!inside) continue;
			helper.assertTrue(state == helper.getLevel().getBlockState(new BlockPos(pos.getX(), pos.getY(), min.getZ())), "slice " + at);
			double[] side = at.getX() < 4 ? west : east;
			if (state.isAir()) {
				for (int c = 0; c < 8; c++) helper.assertTrue(value(side, at, c) >= -1e-9, "air cell " + at + " is above the slope");
				continue;
			}
			Direction facing = at.getX() < 4 ? Direction.WEST : Direction.EAST;
			helper.assertTrue(state.is(RAMP) && state.getValue(SurfRampBlock.FACING) == facing, "ramp facing " + facing + " at " + at + ": " + state);
			RampCell cell = SurfRampBlock.cell(state);
			if (cell.full()) {
				for (int c = 0; c < 8; c++) helper.assertTrue(value(side, at, c) <= 1e-9, "full cell " + at + " is under the slope");
			} else {
				double[] slope = cell.localPlanes(facing.getStepX(), facing.getStepZ())[6];
				double d = slope[3] + slope[0] * at.getX() + slope[1] * at.getY() + slope[2] * at.getZ();
				for (int k = 0; k < 3; k++) helper.assertTrue(Math.abs(slope[k] - side[k]) < 1e-12, "slope normal at " + at);
				helper.assertTrue(Math.abs(d - side[3]) < 1e-9, "slope plane at " + at + ": d " + d + ", want " + side[3]);
			}
			ramps++;
		}
		helper.assertValueEqual(ramps, 28 * length, "ramp cells");
	}

	/** n·corner - d for corner c (bits x, y, z) of the cell at {@code at}. */
	private static double value(double[] plane, BlockPos at, int c) {
		return plane[0] * (at.getX() + (c & 1)) + plane[1] * (at.getY() + (c >> 1 & 1)) + plane[2] * (at.getZ() + (c >> 2 & 1)) - plane[3];
	}

	/**
	 * The extend preview (plan() on a ramp, run every client tick while the knife points at one) costs about the same on a
	 * 16-tall 5:4 A-frame 8 and 200 blocks long: it reads the ramp's end slice, not the whole ramp.
	 */
	@GameTest(structure = LONG_AREA, maxTicks = 400)
	public void extendPlanCostIsFlatInRampLength(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		BlockPos o = helper.absolutePos(new BlockPos(20, 1, 0)), clicked = o.offset(3, 0, 2);
		Map<Integer, Double> ms = new TreeMap<>();
		for (int length : new int[] {8, 16, 32, 48, 96, 200}) {
			new AFrame(RAMP, o.getX(), o.getY(), o.getZ(), 16, length, false).build(level);
			for (int i = 0; i < 20; i++) ModulePlacer.plan(level, player, player.getMainHandItem(), clicked, Direction.UP);
			double best = Double.MAX_VALUE;
			for (int batch = 0; batch < 5; batch++) {
				long t0 = System.nanoTime();
				for (int i = 0; i < 20; i++) ModulePlacer.plan(level, player, player.getMainHandItem(), clicked, Direction.UP);
				best = Math.min(best, (System.nanoTime() - t0) / 1e6 / 20);
			}
			ms.put(length, best);
		}
		System.out.println("SURFCRAFT extend plan() ms by 16-tall A-frame length: " + ms);
		helper.assertTrue(ms.get(200) < 2 * ms.get(8) + 0.2, "plan() cost grows with the ramp's length: " + ms);
		helper.succeed();
	}

	/** Copy a 16-tall 5:4 A-frame 8 long and extend it 25 times: a 208-long surf ramp, every slice like the first. */
	@GameTest(structure = LONG_AREA, maxTicks = 400)
	public void extendGrowsASurfSizedRamp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(20, 1, 0)), clicked = o.offset(3, 0, 2);
		new AFrame(RAMP, o.getX(), o.getY(), o.getZ(), 16, 8, false).build(level);
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		click(player, clicked, Direction.UP, true);
		SurfModule module = player.getMainHandItem().get(KarambitItem.MODULE);
		helper.assertTrue(module != null && module.width() == 26 && module.height() == 16 && module.length() == 8, "copied " + module);
		for (int i = 1; i <= 25; i++) {
			ModulePlacer.Result result = ModulePlacer.place(level, player, player.getMainHandItem(), clicked, Direction.UP);
			helper.assertTrue(result.ok(), "extension " + i + ": " + result.message().getString());
		}
		for (BlockPos pos : BlockPos.betweenClosed(o.offset(-14, 0, 0), o.offset(13, 16, 208))) {
			BlockState want = pos.getZ() - o.getZ() < 208 ? level.getBlockState(new BlockPos(pos.getX(), pos.getY(), o.getZ())) : Blocks.AIR.defaultBlockState();
			if (level.getBlockState(pos) != want) helper.fail("cell " + pos.subtract(o) + " is " + level.getBlockState(pos) + ", want " + want);
		}
		helper.succeed();
	}

	/** A ramp wider than the knife holds is refused with its size, and the knife keeps what it had. */
	@GameTest(structure = LONG_AREA)
	public void copyRefusesARampWiderThanTheKnife(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = helper.absolutePos(new BlockPos(20, 1, 2));
		new AFrame(RAMP, o.getX(), o.getY(), o.getZ(), 21, 4, false).build(level);
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		ModulePlacer.Result result = ModulePlacer.copy(level, player, player.getMainHandItem(), o.offset(3, 0, 1));
		helper.assertTrue(!result.ok() && result.message().getContents() instanceof TranslatableContents t && t.getKey().equals("surfcraft.karambit.too_wide")
				&& Arrays.equals(t.getArgs(), new Object[] {34, SurfModule.MAX_SIZE}), "copy of a 34-wide ramp: " + result.message().getString());
		helper.assertTrue(!player.getMainHandItem().has(KarambitItem.MODULE), "the knife keeps its default module");
		helper.succeed();
	}

	/**
	 * Adventure mode builds and undoes nothing with the knife: plan() (the preview) refuses with the reason, and a sneak +
	 * right-click on a ramp, which the client turns into use() (ItemStack.useOn passes without building), undoes nothing.
	 */
	@GameTest(structure = AREA)
	public void adventureModeNeitherBuildsNorUndoes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = player(helper, GameType.CREATIVE, Direction.SOUTH);
		BlockPos ground = helper.absolutePos(new BlockPos(12, 0, 2)), min = helper.absolutePos(new BlockPos(9, 1, 2));
		level.setBlockAndUpdate(ground, STONE);
		click(player, ground, Direction.UP, false);
		long placed = BlockPos.betweenClosedStream(min, min.offset(7, 4, 7)).filter(p -> !level.getBlockState(p).isAir()).count();
		helper.assertValueEqual(placed, (long) SurfModule.DEFAULT.blocks(), "placed in creative");
		GameType.ADVENTURE.updatePlayerAbilities(player.getAbilities());
		ModulePlacer.Plan plan = ModulePlacer.plan(level, player, player.getMainHandItem(), ground, Direction.UP);
		helper.assertTrue(!plan.ok() && key(plan.problem()).equals("surfcraft.karambit.may_not_build"), "adventure plan: " + plan.problem());
		click(player, min.offset(3, 0, 3), Direction.UP, true);
		player.setShiftKeyDown(true);
		player.getMainHandItem().use(level, player, InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		helper.assertValueEqual(BlockPos.betweenClosedStream(min, min.offset(7, 4, 7)).filter(p -> !level.getBlockState(p).isAir()).count(), placed, "blocks after an adventure sneak-click");
		helper.succeed();
	}

	/** A module that would land on the player says so ("You are in the way"); one that hits a block still says "No room". */
	@GameTest(structure = AREA)
	public void refusalsNameTheirCause(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (int x = 4; x < 20; x++) for (int y = 1; y < 9; y++) helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE);
		Player player = player(helper, GameType.CREATIVE, Direction.NORTH);
		BlockPos wall = helper.absolutePos(new BlockPos(12, 2, 6));
		player.setPos(wall.getX() + 0.5, wall.getY() - 1, wall.getZ() + 3.5);
		ModulePlacer.Plan plan = ModulePlacer.plan(level, player, player.getMainHandItem(), wall, Direction.SOUTH);
		helper.assertTrue(!plan.ok() && key(plan.problem()).equals("surfcraft.karambit.in_the_way") && plan.blocked().stream().allMatch(p -> new AABB(p).intersects(player.getBoundingBox())),
				"a module through the player: " + plan.problem() + " " + plan.blocked());
		player.setPos(wall.getX() + 0.5, wall.getY() - 1, wall.getZ() + 12.5);
		helper.assertTrue(ModulePlacer.plan(level, player, player.getMainHandItem(), wall, Direction.SOUTH).ok(), "the same module with the player back");
		level.setBlockAndUpdate(wall.south(3), STONE);
		helper.assertValueEqual(key(ModulePlacer.plan(level, player, player.getMainHandItem(), wall, Direction.SOUTH).problem()), "surfcraft.karambit.blocked", "a stone in the way");
		helper.succeed();
	}

	/** Two iron ingots and a stick on the diagonal make one Karambit; the recipe has its recipe-book advancement. */
	@GameTest
	public void recipeMakesAKarambit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack i = new ItemStack(Items.IRON_INGOT), s = new ItemStack(Items.STICK), e = ItemStack.EMPTY;
		CraftingInput input = CraftingInput.of(3, 3, List.of(e, e, i, e, i, e, s, e, e));
		var match = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		helper.assertTrue(match.isPresent(), "a crafting recipe for the karambit");
		ItemStack out = match.get().value().assemble(input);
		helper.assertTrue(out.is(KarambitItem.KARAMBIT) && out.getCount() == 1 && out.getMaxStackSize() == 1, "crafted " + out);
		helper.assertTrue(level.getServer().getAdvancements().get(SurfCraft.id("recipes/tools/karambit")) != null, "recipe advancement");
		helper.succeed();
	}

	/** The module survives saving (item codec through NBT) and syncing (network codec); malformed data is rejected. */
	@GameTest
	public void moduleComponentRoundTrips(GameTestHelper helper) {
		RegistryAccess access = helper.getLevel().registryAccess();
		for (SurfModule module : List.of(SurfModule.DEFAULT, SurfModule.of(2, 1, 2, new BlockState[] {FULL_EAST, STONE, Blocks.AIR.defaultBlockState(), FULL_EAST}))) {
			ItemStack knife = new ItemStack(KarambitItem.KARAMBIT);
			knife.set(KarambitItem.MODULE, module);
			Tag saved = ItemStack.CODEC.encodeStart(access.createSerializationContext(NbtOps.INSTANCE), knife).getOrThrow();
			ItemStack loaded = ItemStack.CODEC.parse(access.createSerializationContext(NbtOps.INSTANCE), saved).getOrThrow();
			helper.assertValueEqual(loaded.get(KarambitItem.MODULE), module, "saved module");
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
			KarambitItem.MODULE.streamCodec().encode(buf, module);
			helper.assertValueEqual(KarambitItem.MODULE.streamCodec().decode(buf), module, "synced module");
		}
		CompoundTag bad = (CompoundTag) SurfModule.CODEC.encodeStart(NbtOps.INSTANCE, SurfModule.DEFAULT).getOrThrow();
		bad.putInt("length", 9);
		helper.assertTrue(SurfModule.CODEC.parse(NbtOps.INSTANCE, bad).isError(), "runs that don't cover the box are rejected");
		helper.succeed();
	}
}
