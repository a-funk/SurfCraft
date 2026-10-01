package dev.afunk.surfcraft.client;

import net.fabricmc.api.ClientModInitializer;

public final class SurfCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RampModels.register();
	}
}
