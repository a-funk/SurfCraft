package dev.afunk.surfcraft;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SurfCraft implements ModInitializer {
	public static final String MOD_ID = "surfcraft";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOG.info("SurfCraft loaded");
	}
}
