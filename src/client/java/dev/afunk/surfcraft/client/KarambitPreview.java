package dev.afunk.surfcraft.client;

import dev.afunk.surfcraft.karambit.KarambitItem;
import dev.afunk.surfcraft.karambit.ModulePlacer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * While the player holds the Karambit (main hand, not sneaking) and looks at a block, outlines the box its click would
 * fill: green when it fits, red when it doesn't (blocked cells outlined too). Uses the server's own {@link ModulePlacer}
 * plan, as per-tick gizmos (emitted inside the client tick, drawn every frame until the next one).
 */
public final class KarambitPreview {
	private static final int FITS = 0xFF35E06A, BLOCKED = 0xFFFF3B3B;

	private KarambitPreview() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(KarambitPreview::tick);
	}

	private static void tick(Minecraft client) {
		ItemStack knife = client.player == null ? ItemStack.EMPTY : client.player.getMainHandItem();
		if (!knife.is(KarambitItem.KARAMBIT) || client.player.isShiftKeyDown() || client.level == null
				|| !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
		ModulePlacer.Plan plan = ModulePlacer.plan(client.level, client.player, knife, hit.getBlockPos(), hit.getDirection());
		int color = plan.ok() ? FITS : BLOCKED;
		Gizmos.cuboid(AABB.of(plan.box()).inflate(0.02), GizmoStyle.strokeAndFill(color, 3.0F, color & 0x2CFFFFFF));
		for (BlockPos pos : plan.blocked().subList(0, Math.min(64, plan.blocked().size()))) Gizmos.cuboid(pos, 0.04F, GizmoStyle.stroke(BLOCKED, 2.0F));
	}
}
