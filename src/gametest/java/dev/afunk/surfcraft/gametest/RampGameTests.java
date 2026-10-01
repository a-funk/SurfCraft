package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.SurfCraft;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.gametest.TestRamp.Cell;
import dev.afunk.surfcraft.karambit.SurfModule;
import dev.afunk.surfcraft.physics.RampCell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RampGameTests {
	private static final String RAMP_AREA = "surfcraft-gametest:ramp_area";
	private static final List<Direction> FACINGS = Direction.Plane.HORIZONTAL.stream().toList();

	/** Ramps built cell by cell in different orders get exactly the plane formula's cut in every cell. */
	@GameTest
	public void placementJoinsIntoOnePlane(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
		for (SurfRampBlock block : SurfBlocks.RAMPS) {
			for (Direction facing : FACINGS) {
				// Seeded at the top back cell (5 rows) or at the bottom front cell; 3 columns, 2 long.
				TestRamp top = new TestRamp(block, facing, origin, 3, 5, 2, 0, 4);
				TestRamp front = new TestRamp(block, facing, origin, 3, 5, 2, 2, 0);
				Comparator<Cell> topDown = Comparator.comparingInt((Cell c) -> -c.y()).thenComparingInt(Cell::u).thenComparingInt(Cell::w);
				build(helper, top, top.sorted(topDown));
				build(helper, front, front.sorted(TestRamp.BOTTOM_UP));
				build(helper, top, top.randomConnected(1));
				build(helper, front, front.randomConnected(2));
				build(helper, top, top.randomConnected(3));
				top.clear(level);
			}
		}
		helper.succeed();
	}

	private static void build(GameTestHelper helper, TestRamp ramp, List<Cell> order) {
		ramp.clear(helper.getLevel());
		String error = ramp.place(helper.getLevel(), order);
		helper.assertTrue(error == null, String.valueOf(error));
	}

	/**
	 * Two ramps of one type and facing built by hand next to or on each other (the review's sweep: B is A shifted du along
	 * the facing, dy up and dw slices along the ramp, touching but not overlapping; A first, each bottom up through the
	 * item, every click on the cell it grows from): A comes out exact, and B too wherever its first cell stands alone.
	 * (Where A's plane reaches B's first cell, that cell joins A, which is the joining rule.)
	 */
	@GameTest(structure = RAMP_AREA, maxTicks = 400)
	public void handBuiltNeighbourRampsKeepTheirPlanes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		BlockPos o = helper.absolutePos(new BlockPos(11, 7, 11));
		int layouts = 0, standalone = 0;
		for (SurfRampBlock block : SurfBlocks.RAMPS) for (Direction facing : FACINGS) for (int dw = -2; dw <= 2; dw += 2) for (int du = -3; du <= 3; du++) for (int dy = -6; dy <= 6; dy++) {
			TestRamp a = new TestRamp(block, facing, o, 4, 5, 2, 3, 0);
			TestRamp b = new TestRamp(block, facing, a.pos(new Cell(du, dy, dw)), 4, 5, 2, 3, 0);
			List<BlockPos> aCells = a.sorted(TestRamp.BOTTOM_UP).stream().map(a::pos).toList(), bCells = b.sorted(TestRamp.BOTTOM_UP).stream().map(b::pos).toList();
			if (bCells.stream().anyMatch(aCells::contains) || bCells.stream().noneMatch(q -> aCells.stream().anyMatch(p -> p.distChessboard(q) == 1))) continue;
			layouts++;
			String layout = "%s facing %s, B = A + (%d, %d, %d): ".formatted(block, facing, du, dy, dw);
			String builtA = a.placeByHand(player, a.sorted(TestRamp.BOTTOM_UP)), builtB = b.placeByHand(player, b.sorted(TestRamp.BOTTOM_UP));
			helper.assertTrue(builtA == null, layout + builtA);
			if (level.getBlockState(bCells.getFirst()).getValue(block.cut) == block.p) {
				standalone++;
				helper.assertTrue(builtB == null, layout + builtB);
			}
			a.clear(level);
			b.clear(level);
		}
		helper.assertTrue(layouts == 1328 && standalone > 1100, layouts + " layouts, " + standalone + " with B's first cell alone");
		helper.succeed();
	}

	/**
	 * A ramp placed against a ramp block of its type and facing continues that block's plane; with no ramp clicked, of two
	 * planes equally near it takes the lower one, whichever side each is on.
	 */
	@GameTest
	public void placementFollowsTheClickedRamp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		SurfRampBlock r = SurfBlocks.SURF_RAMP;
		BlockState east = r.defaultBlockState().setValue(SurfRampBlock.FACING, Direction.EAST);
		BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		player.setYRot(Direction.WEST.toYRot());
		// Below: cut 8, whose plane cuts this cell at 4. Above: cut 1, whose plane cuts it at 5.
		level.setBlockAndUpdate(pos.below(), east.setValue(r.cut, 8));
		level.setBlockAndUpdate(pos.above(), east.setValue(r.cut, 1));
		for (Direction face : List.of(Direction.UP, Direction.DOWN)) {
			BlockPos clicked = pos.relative(face.getOpposite());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(r));
			player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(clicked), face, clicked, false)));
			helper.assertValueEqual(level.getBlockState(pos), east.setValue(r.cut, face == Direction.UP ? 4 : 5), "placed against the cell " + (face == Direction.UP ? "below" : "above"));
			level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		}
		helper.assertValueEqual(r.placementState(level, pos, Direction.EAST), east.setValue(r.cut, 4), "no click: the lower plane");
		level.setBlockAndUpdate(pos.below(), Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(pos.above(), Blocks.AIR.defaultBlockState());
		for (int north : new int[] {5, 7}) {
			level.setBlockAndUpdate(pos.north(), east.setValue(r.cut, north));
			level.setBlockAndUpdate(pos.south(), east.setValue(r.cut, 12 - north));
			helper.assertValueEqual(r.placementState(level, pos, Direction.EAST), east.setValue(r.cut, 5), "between cuts " + north + " (north) and " + (12 - north));
		}
		helper.succeed();
	}

	/** Ramps block motion like smooth stone (their vanilla tag), so the MOTION_BLOCKING heightmap, and rain, snow and lightning with it, stop on them. */
	@GameTest(structure = RAMP_AREA, skyAccess = true)
	public void rampsStopRain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (SurfRampBlock r : SurfBlocks.RAMPS) {
			for (int cut : new int[] {1, r.p + r.q}) {
				BlockState roof = r.defaultBlockState().setValue(r.cut, cut);
				BlockPos at = helper.absolutePos(new BlockPos(2 + 2 * cut, 6, 2 + 4 * SurfBlocks.RAMPS.indexOf(r)));
				level.setBlockAndUpdate(at, roof);
				helper.assertTrue(roof.is(BlockTags.BLOCKS_MOTION), roof + " blocks motion");
				helper.assertValueEqual(level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()), at.getY() + 1, "rain stops on " + roof);
			}
		}
		helper.succeed();
	}

	/**
	 * Culling hides hidden faces and only those: full cells cull against full cells and stone, and for every ramp state
	 * against every ramp state and some vanilla blocks, in every direction, a face that Block.shouldRenderFace culls lies
	 * inside the neighbour's face (sampled 32 x 32 off the grid lines). Partial cells' cross-sections (the faces along the
	 * ramp) carry no cull face in RampModels: no voxel shape matches a slanted outline, so they are skipped.
	 */
	@GameTest
	public void cullingHidesOnlyCoveredFaces(GameTestHelper helper) {
		List<BlockState> ramps = SurfBlocks.RAMPS.stream().flatMap(b -> b.getStateDefinition().getPossibleStates().stream()).toList(), others = new ArrayList<>();
		for (Block b : List.of(Blocks.AIR, Blocks.STONE, Blocks.GLASS, Blocks.SMOOTH_STONE_SLAB, Blocks.STONE_STAIRS, Blocks.SNOW)) {
			b.getStateDefinition().getPossibleStates().stream().filter(s -> !s.hasProperty(BlockStateProperties.WATERLOGGED) || !s.getValue(BlockStateProperties.WATERLOGGED)).forEach(others::add);
		}
		BlockState full = ramps.stream().filter(s -> SurfRampBlock.cell(s).full()).findFirst().orElseThrow();
		for (Direction d : Direction.values()) {
			helper.assertFalse(Block.shouldRenderFace(full, full, d) || Block.shouldRenderFace(full, Blocks.STONE.defaultBlockState(), d), "full ramp cells cull " + d);
		}
		int culled = 0;
		for (BlockState s : ramps) for (BlockState n : Stream.concat(ramps.stream(), others.stream()).toList()) for (Direction d : Direction.values()) {
			for (BlockState[] pair : new BlockState[][] {{s, n}, {n, s}}) {
				BlockState self = pair[0], other = pair[1];
				Direction dir = pair[0] == s ? d : d.getOpposite();
				if (SurfRampBlock.isRamp(self) && !SurfRampBlock.cell(self).full() && dir.getAxis() == self.getValue(SurfRampBlock.FACING).getClockWise().getAxis()) continue;
				if (Block.shouldRenderFace(self, other, dir)) continue;
				culled++;
				for (int i = 0; i < 32; i++) for (int j = 0; j < 32; j++) {
					double a = (i + 0.5) / 32, b = (j + 0.5) / 32;
					if (onFace(self, dir, a, b) && !onFace(other, dir.getOpposite(), a, b)) helper.fail(self + " " + dir + " face culled by " + other + " shows at " + a + ", " + b);
				}
			}
		}
		helper.assertTrue(culled > 1000, culled + " culled faces checked");
		helper.succeed();
	}

	/** Whether point (a, b) of face {@code face} of the unit cell (the other two axes in x, y, z order) is in the state's face. */
	private static boolean onFace(BlockState state, Direction face, double a, double b) {
		double n = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
		double x = face.getAxis() == Direction.Axis.X ? n : a, y = face.getAxis() == Direction.Axis.Y ? n : face.getAxis() == Direction.Axis.X ? a : b, z = face.getAxis() == Direction.Axis.Z ? n : b;
		if (SurfRampBlock.isRamp(state)) {
			RampCell cell = SurfRampBlock.cell(state);
			double u = switch (state.getValue(SurfRampBlock.FACING)) {
				case EAST -> x;
				case WEST -> 1 - x;
				case SOUTH -> z;
				default -> 1 - z;
			};
			return cell.p() * u + cell.q() * y <= cell.cut();
		}
		Direction.Axis[] plane = Arrays.stream(Direction.Axis.values()).filter(ax -> ax != face.getAxis()).toArray(Direction.Axis[]::new);
		return state.getFaceOcclusionShape(face).toAabbs().stream().anyMatch(box -> box.min(plane[0]) < a && a < box.max(plane[0]) && box.min(plane[1]) < b && b < box.max(plane[1]));
	}

	/**
	 * Ramps shade what's under them like smooth stone: the ground under a floating hollow ramp (the default module's slope
	 * cells only) gets less sky light than the open ground, full cells are opaque, and slope cells block light by their faces.
	 */
	@GameTest(structure = RAMP_AREA, maxTicks = 100, skyAccess = true)
	public void rampsCastShadows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState full = SurfBlocks.SURF_RAMP.defaultBlockState().setValue(SurfBlocks.SURF_RAMP.cut, 9), slope = SurfBlocks.SURF_RAMP.defaultBlockState();
		helper.assertTrue(full.isSolidRender() && full.getLightDampening() == 15 && slope.useShapeForLightOcclusion(), "full cells are opaque, slope cells occlude by shape");
		for (int x = 0; x < 24; x++) for (int z = 0; z < 24; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
		SurfModule m = SurfModule.DEFAULT;
		BlockState[] cells = m.cells();
		for (int x = 0; x < m.width(); x++) for (int y = 0; y < m.height(); y++) for (int z = 0; z < m.length(); z++) {
			BlockState s = cells[SurfModule.index(m.width(), m.length(), x, y, z)];
			if (!s.isAir() && !SurfRampBlock.cell(s).full()) helper.setBlock(new BlockPos(4 + x, 10 + y, 4 + z), s);
		}
		helper.runAfterDelay(60, () -> {
			int under = level.getBrightness(LightLayer.SKY, helper.absolutePos(new BlockPos(7, 2, 8))), open = level.getBrightness(LightLayer.SKY, helper.absolutePos(new BlockPos(20, 2, 20)));
			helper.assertTrue(under < open, "sky light under the hollow ramp " + under + ", in the open " + open);
			helper.succeed();
		});
	}

	/** A player placing the item gets a ramp whose slope faces them (facing = opposite of their look direction). */
	@GameTest
	public void blockItemPlacementFacesThePlayer(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		for (int b = 0; b < SurfBlocks.RAMPS.size(); b++) {
			SurfRampBlock block = SurfBlocks.RAMPS.get(b);
			for (int i = 0; i < FACINGS.size(); i++) {
				Direction look = FACINGS.get(i);
				BlockPos pos = new BlockPos(1 + 2 * i, 1, 1 + 3 * b);
				player.setYRot(look.toYRot());
				helper.placeAt(player, new ItemStack(block), pos.below(), Direction.UP);
				BlockState placed = helper.getBlockState(pos);
				helper.assertTrue(placed.is(block), "placed " + placed + " looking " + look);
				helper.assertValueEqual(placed.getValue(SurfRampBlock.FACING), look.getOpposite(), "facing when looking " + look);
				helper.assertValueEqual(placed.getValue(block.cut), block.p, "standalone cut");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void eachRampDropsItself(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		BlockPos pos = new BlockPos(1, 1, 1);
		for (SurfRampBlock block : SurfBlocks.RAMPS) {
			helper.setBlock(pos, block);
			BlockState state = helper.getBlockState(pos);
			helper.assertTrue(pickaxe.isCorrectToolForDrops(state), "a pickaxe mines " + block);
			List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(pos), null, null, pickaxe);
			helper.assertTrue(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1, block + " drops " + drops);
		}
		helper.succeed();
	}

	@GameTest
	public void recipesMakeRamps(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getServer().getRecipeManager();
		ItemStack s = new ItemStack(Items.SMOOTH_STONE), e = ItemStack.EMPTY;
		CraftingInput stairs = CraftingInput.of(3, 3, List.of(s, e, e, s, s, e, s, s, s));
		CraftingInput steep = CraftingInput.of(3, 3, List.of(s, e, e, s, e, e, s, s, e));
		craft(helper, recipes, stairs, SurfBlocks.SURF_RAMP, 6);
		craft(helper, recipes, steep, SurfBlocks.STEEP_SURF_RAMP, 4);
		for (SurfRampBlock block : SurfBlocks.RAMPS) {
			String name = BuiltInRegistries.BLOCK.getKey(block).getPath() + "_from_smooth_stone_stonecutting";
			Recipe<?> recipe = recipes.byKey(ResourceKey.create(Registries.RECIPE, SurfCraft.id(name))).map(RecipeHolder::value).orElse(null);
			helper.assertTrue(recipe instanceof StonecutterRecipe, "stonecutting recipe " + name);
			SingleRecipeInput input = new SingleRecipeInput(s);
			StonecutterRecipe cutting = (StonecutterRecipe) recipe;
			helper.assertTrue(cutting.matches(input, level), name + " takes smooth stone");
			ItemStack out = cutting.assemble(input);
			helper.assertTrue(out.is(block.asItem()) && out.getCount() == 1, name + " makes " + out);
		}
		for (String recipe : List.of("surf_ramp", "steep_surf_ramp", "surf_ramp_from_smooth_stone_stonecutting", "steep_surf_ramp_from_smooth_stone_stonecutting")) {
			helper.assertTrue(level.getServer().getAdvancements().get(SurfCraft.id("recipes/building_blocks/" + recipe)) != null, "recipe advancement " + recipe);
		}
		helper.succeed();
	}

	@GameTest
	public void rampsFollowSmoothStoneSlabInBuildingBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), false, level.registryAccess());
		List<Item> items = BuiltInRegistries.CREATIVE_MODE_TAB.getValueOrThrow(CreativeModeTabs.BUILDING_BLOCKS).getDisplayItems().stream().map(ItemStack::getItem).toList();
		int slab = items.indexOf(Items.SMOOTH_STONE_SLAB);
		List<Item> ramps = SurfBlocks.RAMPS.stream().map(SurfRampBlock::asItem).toList();
		helper.assertTrue(slab >= 0 && items.subList(slab + 1, slab + 1 + ramps.size()).equals(ramps), "ramps right after the smooth stone slab in " + items);
		helper.succeed();
	}

	private static void craft(GameTestHelper helper, RecipeManager recipes, CraftingInput input, SurfRampBlock block, int count) {
		var match = recipes.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
		helper.assertTrue(match.isPresent(), "a crafting recipe for " + block);
		helper.assertValueEqual(match.get().id().identifier(), BuiltInRegistries.BLOCK.getKey(block), "recipe");
		ItemStack out = match.get().value().assemble(input);
		helper.assertTrue(out.is(block.asItem()) && out.getCount() == count, "crafted " + out);
	}

	/** Items collide with the inscribed staircase: one dropped on a ramp lands on it, not inside it or through it. */
	@GameTest(maxTicks = 100)
	public void droppedItemRestsOnTheRamp(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 1, 3);
		helper.setBlock(pos, SurfBlocks.SURF_RAMP.placementState(helper.getLevel(), helper.absolutePos(pos), Direction.EAST));
		ItemEntity item = helper.spawnItem(Items.DIAMOND, new Vec3(3.5, 3.5, 3.5));
		helper.succeedWhen(() -> {
			AABB box = item.getBoundingBox();
			double y = item.getY() - helper.absolutePos(pos).getY();
			helper.assertTrue(item.onGround() && item.getDeltaMovement().lengthSqr() < 1e-6, "item at rest");
			helper.assertTrue(helper.getLevel().noCollision(item, box.deflate(1e-4)), "item is not inside the ramp");
			helper.assertFalse(helper.getLevel().noCollision(item, box.move(0, -0.05, 0)), "item stands on the ramp");
			helper.assertTrue(y > 0.1 && y < 1, "item rests on the ramp cell, at height " + y);
		});
	}
}
