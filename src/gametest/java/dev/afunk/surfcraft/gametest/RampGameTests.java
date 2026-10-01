package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.SurfCraft;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.gametest.TestRamp.Cell;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
				Comparator<Cell> bottomUp = Comparator.comparingInt(Cell::y).thenComparingInt(c -> -c.u()).thenComparingInt(Cell::w);
				build(helper, top, top.sorted(topDown));
				build(helper, front, front.sorted(bottomUp));
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
