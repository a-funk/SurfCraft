package dev.afunk.surfcraft.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The surf-server speedometer: horizontal speed in units/s, centred above the hotbar while the controller drives.
 * Registered next to the chat, so it hides with the rest of the HUD (F1).
 */
final class SpeedHud {
	private SpeedHud() {
	}

	static void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		SurfController controller = ((SurfDriven) mc.player).surfcraft$controller();
		if (!controller.driving()) return;
		graphics.centeredText(mc.font, Math.round(controller.speed()) + " u/s", graphics.guiWidth() / 2, graphics.guiHeight() - 72, 0xFFFFFFFF);
	}
}
