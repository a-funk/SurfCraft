package dev.afunk.surfcraft;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.karambit.KarambitItem;
import dev.afunk.surfcraft.movement.Surfing;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfCraft implements ModInitializer {
	public static final String MOD_ID = "surfcraft";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		SurfBlocks.register();
		KarambitItem.register();
		Surfing.register();
		LOG.info("SurfCraft loaded");
	}
}
