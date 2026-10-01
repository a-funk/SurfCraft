package dev.afunk.surfcraft.block;

import dev.afunk.surfcraft.SurfCraft;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class SurfBlocks {
	/** 5:4, 51.3 degrees: the most common angle of real KSF surf ramps. */
	public static final SurfRampBlock SURF_RAMP = ramp("surf_ramp", 5, 4);
	/** 2:1, 63.4 degrees. */
	public static final SurfRampBlock STEEP_SURF_RAMP = ramp("steep_surf_ramp", 2, 1);
	public static final List<SurfRampBlock> RAMPS = List.of(SURF_RAMP, STEEP_SURF_RAMP);

	private SurfBlocks() {
	}

	public static void register() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
				.register(output -> output.insertAfter(Items.SMOOTH_STONE_SLAB, SURF_RAMP, STEEP_SURF_RAMP));
	}

	private static SurfRampBlock ramp(String name, int p, int q) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, SurfCraft.id(name));
		SurfRampBlock block = Registry.register(BuiltInRegistries.BLOCK, key,
				new SurfRampBlock(p, q, BlockBehaviour.Properties.ofFullCopy(Blocks.SMOOTH_STONE).noOcclusion().setId(key)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, key.identifier());
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}
