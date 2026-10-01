package dev.afunk.surfcraft.client;

import dev.afunk.surfcraft.SurfCraft;
import dev.afunk.surfcraft.movement.SurfPlayer;
import dev.afunk.surfcraft.movement.Surfing;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class SurfCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		RampModels.register();
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, SurfCraft.id("speedometer"), SpeedHud::extract);
		KarambitPreview.register();
		// Whether other players are surfing, for their animation.
		ClientPlayNetworking.registerGlobalReceiver(Surfing.TYPE, (payload, context) -> {
			if (context.player().level().getEntity(payload.entity()) instanceof SurfPlayer other) other.surfcraft$setWindow(payload.on() ? 1 : 0);
		});
	}
}
