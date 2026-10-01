package dev.afunk.surfcraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * A survival player on an in-memory connection, fed the packets a client sends, so the server's own movement handling
 * runs: moved too quickly, moved wrongly, fall damage, the floating check. Its connection is not in the server's list, so
 * {@link #tick} stands in for the server's tick of this player. Server thread only.
 */
final class PacketPlayer {
	private static final Field AWAITING;

	static {
		try {
			AWAITING = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
			AWAITING.setAccessible(true);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	final ServerPlayer player;

	PacketPlayer(ServerLevel level, String name) {
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
	}

	/** A server teleport, acknowledged a server tick later as a client does (an acknowledgement in the same tick counts as a move). */
	void teleport(Vec3 pos) {
		player.connection.teleport(pos.x, pos.y, pos.z, 0, 0);
		tick();
		try {
			player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(AWAITING.getInt(player.connection), pos.x, pos.y, pos.z, 0, 0));
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		player.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
		tick();
	}

	/** One client tick's packets: the position, then the tick end. */
	void move(Vec3 pos, boolean onGround) {
		player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(pos, onGround, false));
		player.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
	}

	void sneak(boolean shift) {
		player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, shift, false)));
	}

	/** The server's tick of this player: its zero-input simulation, the per-tick packet budget and the floating check. */
	void tick() {
		player.connection.tick();
	}

	/** Full health, no damage cooldown, no fall distance. */
	void heal() {
		player.setHealth(player.getMaxHealth());
		player.damageCooldownTime = 0;
		player.resetFallDistance();
	}

	void leave() {
		player.level().getServer().getPlayerList().remove(player);
	}
}
