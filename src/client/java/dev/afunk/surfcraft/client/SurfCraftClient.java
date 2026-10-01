package dev.afunk.surfcraft.client;

import dev.afunk.surfcraft.SurfCraft;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class SurfCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RampModels.register();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, SurfCraft.id("speedometer"), SpeedHud::extract);
	}
}
