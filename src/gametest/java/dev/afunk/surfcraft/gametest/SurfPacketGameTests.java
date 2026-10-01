package dev.afunk.surfcraft.gametest;

import com.mojang.authlib.GameProfile;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.movement.BrushWorld;
import dev.afunk.surfcraft.movement.RampCollision;
import dev.afunk.surfcraft.physics.Brush;
import dev.afunk.surfcraft.physics.Config;
import dev.afunk.surfcraft.physics.SourceUnits;
import dev.afunk.surfcraft.physics.TickDriver;
import dev.afunk.surfcraft.physics.V3;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The server's side of a surfer's move packets: a real survival player on a connection, fed what the controller sends
 * (one position per tick and a tick end, the server ticking the connection in between).
 */
public class SurfPacketGameTests {
	static final double K = SourceUnits.PER_BLOCK;

	/** A survival player joined through an embedded connection (not ticked by the server: the test ticks it). */
	private static ServerPlayer join(GameTestHelper helper) {
		GameProfile profile = new GameProfile(UUID.randomUUID(), "surfer");
		ServerPlayer p = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, p, CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(GameType.SURVIVAL);
		p.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		return p;
	}

	/** A server teleport, acknowledged a tick later as a client does (an acknowledgement in the same tick is too quick). */
	private static void teleport(ServerPlayer p, Vec3 pos) {
		p.connection.teleport(pos.x, pos.y, pos.z, 0, 0);
		p.connection.tick();
		int id;
		try {
			var field = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
			field.setAccessible(true);
			id = field.getInt(p.connection);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id, pos.x, pos.y, pos.z, 0, 0));
		p.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
		p.connection.tick();
	}

	/**
	 * Auto hop lands and jumps within one tick about 7 times in 10, and that tick's packet must still be the landing, or
	 * the server never sees one (no fall damage, from any height). Drops of 4 to 15 blocks onto stone, the controller's
	 * core and onGround rule feeding the real packet handler, must hurt exactly as much with jump held as without.
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void hopLandingsTakeFallDamage(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		ServerPlayer p = join(helper);
		Config config = Config.CSS_SURF.withStepHeight(p.maxUpStep() * K);
		V3 hull = BrushWorld.hull(p);
		int corrections = ServerCorrections.EVENTS.size();
		StringBuilder damages = new StringBuilder();
		for (int i = 0; i < 24; i++) {
			// 4 to 15 blocks, some just past a whole block (4.03, 14.07, 15.03), where a fall counted short loses a point.
			double h = 4.03 + i * 0.4783;
			int[] damage = new int[2];
			for (int k = 0; k < 2; k++) {
				Vec3 start = helper.absoluteVec(new Vec3(3.5, 1 + h, 3.5));
				teleport(p, start);
				p.setHealth(20);
				p.setInvulnerableTime(0);
				p.damageCooldownTime = 0;
				p.resetFallDistance();
				BlockPos anchor = BlockPos.containing(start);
				TickDriver d = new TickDriver();
				d.core.setOrigin(BrushWorld.toSource(start, anchor));
				d.published = d.core.origin;
				float least = 20;
				for (int t = 0; t < 40; t++) {
					V3[] reach = d.reach(hull, 60);
					List<Brush> world = BrushWorld.collect(helper.getLevel(), p, BrushWorld.toMinecraft(reach[0], reach[1], anchor), anchor, false);
					d.tick(config, 0, 0, k == 1, -90, hull, world);
					// SurfController.publish's onGround: a tick that landed reports ground.
					p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(BrushWorld.toMinecraft(d.published, anchor), d.core.grounded || d.landed, d.wall));
					p.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
					p.connection.tick();
					least = Math.min(least, p.getHealth());
				}
				damage[k] = Math.round(20 - least);
			}
			damages.append(" %.2f:%d/%d".formatted(h, damage[0], damage[1]));
			helper.assertValueEqual(damage[1], damage[0], "fall damage from " + h + " blocks with jump held (without: " + damage[0] + ")");
			// Vanilla's floor(fall - 3), the fall ending where Source grounds the hull: up to 2 units above the floor.
			helper.assertTrue(damage[0] <= (int) Math.floor(h - 3) && damage[0] >= (int) Math.floor(h - 3 - 2 / K), "fall damage from " + h + " blocks: " + damage[0]);
		}
		System.out.println("SURFCRAFT drop damage (blocks:no jump/jump held)" + damages);
		helper.assertValueEqual(ServerCorrections.EVENTS.size(), corrections, "server corrections");
		helper.getLevel().getServer().getPlayerList().remove(p);
		helper.succeed();
	}

	/**
	 * The server re-simulates moves next to ramps with exact collision and a lift region that grows with the move. Moves
	 * longer than the controller can make (a modified client, with player_movement_check off) are vanilla's: no cost.
	 */
	@GameTest(skyAccess = true)
	public void longMovesAreLeftToVanilla(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		SurfRampBlock ramp = SurfBlocks.SURF_RAMP;
		helper.setBlock(new BlockPos(4, 1, 4), ramp.defaultBlockState().setValue(SurfRampBlock.FACING, Direction.EAST).setValue(ramp.cut, 9));
		Player p = helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 feet = helper.absoluteVec(new Vec3(3.2, 1, 4.5));
		p.snapTo(feet.x, feet.y, feet.z, 0, 0);
		helper.assertTrue(RampCollision.collide(p, new Vec3(-15, 0, 0), MoverType.PLAYER) != null, "a 15-block move beside a ramp should be exact");
		for (double length : new double[] {16, 1000, 10000}) {
			long start = System.nanoTime();
			Vec3 got = RampCollision.collide(p, new Vec3(-length, 0, 0), MoverType.PLAYER);
			double ms = (System.nanoTime() - start) / 1e6;
			// Before the cap: 20-45 ms at 1000 blocks, seconds at 10000.
			helper.assertTrue(got == null && ms < 20, "a " + length + "-block move took " + ms + " ms and returned " + got);
		}
		p.discard();
		helper.succeed();
	}
}
