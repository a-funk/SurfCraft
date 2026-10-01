package dev.afunk.surfcraft.movement;

import dev.afunk.surfcraft.SurfCraft;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server's surf window: whom the surf controller drives, as far as the server can tell. It opens with a move that
 * comes near a ramp by the controller's own reach ({@link RampCollision#surfNear}), stays open while the moves are
 * airborne, and closes after more than {@link RampCollision#GRACE} moves on the ground in a row (the controller hands back
 * then too) or at a teleport (the controller starts over). Surfers get its allowances: a move budget that fits CS:S speeds,
 * no sneak edge back-off on the server, knockback from their own movement, their own ground state.
 *
 * <p>Also the payload that tells the players tracking a player whether it is surfing, for their animation of it.
 */
public record Surfing(int entity, boolean on) implements CustomPacketPayload {
	public static final Type<Surfing> TYPE = new Type<>(SurfCraft.id("surfing"));
	private static final StreamCodec<ByteBuf, Surfing> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Surfing::entity, ByteBufCodecs.BOOL, Surfing::on,
			Surfing::new);

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC);
		EntityTrackingEvents.START_TRACKING.register((entity, viewer) -> {
			if (entity instanceof SurfPlayer p && p.surfcraft$surfing()) ServerPlayNetworking.send(viewer, new Surfing(entity.getId(), true));
		});
	}

	/** One move packet: opens or keeps the window when the move comes near a ramp or is airborne, counts it down on the ground. */
	public static void move(ServerPlayer player, boolean near, boolean onGround) {
		int window = ((SurfPlayer) player).surfcraft$window();
		set(player, near || window > 0 && !onGround ? RampCollision.GRACE + 1 : Math.max(0, window - 1));
	}

	/** Sets the window, telling the players tracking this one when it opens or closes. */
	public static void set(ServerPlayer player, int window) {
		SurfPlayer p = (SurfPlayer) player;
		if (p.surfcraft$surfing() != window > 0) for (ServerPlayer viewer : PlayerLookup.tracking(player)) ServerPlayNetworking.send(viewer, new Surfing(player.getId(), window > 0));
		p.surfcraft$setWindow(window);
	}

	@Override
	public Type<Surfing> type() {
		return TYPE;
	}
}
