package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.movement.BrushWorld;
import dev.afunk.surfcraft.physics.Brush;
import dev.afunk.surfcraft.physics.SourceUnits;
import dev.afunk.surfcraft.physics.TickDriver;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of surfing, with real server players: exact collision with the slope instead of the staircase (b1),
 * slope contact resetting the fall distance (c1), and the brush world's bevel on blocks under a slope; and through the
 * packets a client sends ({@link PacketPlayer}): no sneak edge back-off in the surf window (e2), move budgets for packet
 * bursts, falls onto ramp blocks' flat faces, knockback (e3) and the elytra on a ramp. (Game tests run millions of blocks
 * out: everything is measured relative to the ramp.)
 */
public class SurfMovementGameTests {
	/** Units/s to blocks/tick. */
	static final double UPS = TickDriver.TICK / SourceUnits.PER_BLOCK;

	/** A 5:4 A-frame 3 tall and 3 long, ridge at structure x = 3, base y = 1, on a stone floor. */
	private static AFrame ramp(GameTestHelper helper, boolean stone) {
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		AFrame a = new AFrame(SurfBlocks.SURF_RAMP, o.getX() + 3, o.getY() + 1, o.getZ() + 2, 3, 3, stone);
		a.build(helper.getLevel());
		return a;
	}

	private static Player player(GameTestHelper helper, Vec3 feet) {
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(feet.x, feet.y, feet.z, 0, 0);
		return player;
	}

	/** b1: a player dropping onto the slope stops on the true plane (the staircase under it is up to 1/8 block lower). */
	@GameTest
	public void serverPlayersLandOnTheExactSlope(GameTestHelper helper) {
		AFrame a = ramp(helper, false);
		for (double y : new double[] {1.0, 1.6, 2.2}) {
			Player p = player(helper, new Vec3(a.eastX(y, 0.3), a.base() + y + 0.3, a.z0() + 1.5));
			p.move(MoverType.SELF, new Vec3(0, -1.5, 0));
			double clearance = a.clearance(p.getBoundingBox());
			helper.assertTrue(clearance > -1e-5 && clearance < 0.01, "landed " + clearance + " blocks off the slope from height " + y);
			p.discard();
		}
		helper.succeed();
	}

	/** c1: sliding onto a ramp after a long fall resets the fall distance: no damage. */
	@GameTest
	public void rampContactResetsFallDistance(GameTestHelper helper) {
		AFrame a = ramp(helper, false);
		Player p = player(helper, new Vec3(a.eastX(1.5, 0.2), a.base() + 1.5 + 0.2, a.z0() + 1.5));
		p.fallDistance = 20;
		float health = p.getHealth();
		p.move(MoverType.SELF, new Vec3(0, -0.5, 0));
		helper.assertValueEqual(p.fallDistance, 0.0, "fall distance after touching the ramp");
		helper.assertValueEqual(p.getHealth(), health, "health");
		p.discard();
		helper.succeed();
	}

	/**
	 * e2: the server does not back a sneaking player's move off a ledge within the controller's reach of a ramp (C4: within
	 * 1.5 blocks plus the move, as the client): stepping off a pillar onto a steep slope, and off a pillar 1.2 blocks from a
	 * ramp. Packets as a client sends them; the server must accept both moves whole.
	 */
	@GameTest(skyAccess = true)
	public void sneakingOffALedgeNearARampIsAccepted(GameTestHelper helper) {
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		new AFrame(SurfBlocks.STEEP_SURF_RAMP, o.getX() + 3, o.getY() + 1, o.getZ() + 2, 4, 3, false).build(helper.getLevel());
		// Pillars: one behind the steep A-frame's ridge, and one in the open sky with a ramp cell 1.2 blocks from the box.
		for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
		for (int y = 40; y <= 42; y++) helper.setBlock(new BlockPos(5, y, 5), Blocks.STONE);
		helper.setBlock(new BlockPos(5, 42, 7), SurfBlocks.SURF_RAMP.defaultBlockState());
		PacketPlayer p = new PacketPlayer(helper.getLevel(), "sneaker");
		try {
			// Feet on each pillar's east edge (the last supported spot), then one tick of walking (250 u/s) off it.
			for (Vec3 edge : new Vec3[] {new Vec3(3.2999, 5, 3.5), new Vec3(6.2999, 43, 5.5)}) {
				Vec3 feet = helper.absoluteVec(edge), off = feet.add(250 * UPS, -0.016, 0);
				p.teleport(feet);
				p.sneak(true);
				p.move(feet, true);
				p.tick();
				int corrections = ServerCorrections.EVENTS.size();
				p.move(off, false);
				helper.assertTrue(ServerCorrections.EVENTS.size() == corrections && p.player.position().equals(off),
						"sneaking off the ledge at " + edge + " was corrected: server at " + p.player.position() + ", client at " + off);
				p.sneak(false);
			}
		} finally {
			p.leave();
		}
		helper.succeed();
	}

	/**
	 * B1: a server stall or client hitch lands several move packets in one server tick. Vanilla budgets the k-th packet
	 * 100k square blocks from the tick's first position, but only 100 once k passes 5: six packets above 1312 u/s (ten above
	 * 787) were teleported back. A surfer gets vanilla's single-packet budget for every packet, as long as its packets do not
	 * run more than 5 s ahead of real time; everyone else keeps vanilla's check. Bursts rise straight up, beside a ramp cell
	 * (the surf window) or with none near. Also prints the acceptance grid for the dev log chart.
	 */
	@GameTest(skyAccess = true, maxTicks = 40)
	public void surfersKeepTheirSpeedThroughPacketBursts(GameTestHelper helper) {
		// High above the test area: neighbouring tests build ramps, and a fast move reaches several blocks around.
		Vec3 start = helper.absoluteVec(new Vec3(2.5, 40, 1.5));
		// No ramp yet: vanilla's check stands.
		int[] vanilla = {burst(helper, start, 6, 1350), burst(helper, start, 10, 850)};
		String vanillaGrid = grid(helper, start);
		helper.setBlock(new BlockPos(1, 40, 1), SurfBlocks.SURF_RAMP.defaultBlockState());
		System.out.println("SURFCRAFT burst acceptance (packets in one server tick: rising u/s accepted)\n  vanilla" + vanillaGrid + "\n  surfer" + grid(helper, start));
		helper.assertTrue(vanilla[0] == 6 && vanilla[1] == 10, "bursts far from ramps (6 at 1350, 10 at 850 u/s) rejected at packets " + vanilla[0] + ", " + vanilla[1]);
		for (int[] c : new int[][] {{6, 1350}, {6, 3000}, {8, 2200}, {10, 1300}, {10, 3000}, {20, 1500}, {40, 3500}}) {
			helper.assertValueEqual(burst(helper, start, c[0], c[1]), 0, "a surfer's burst of " + c[0] + " at " + c[1] + " u/s rejected at packet");
		}
		// A flood: once the packets outrun real time by 5 s (about 100 of them), vanilla's check takes over again.
		int flood = burst(helper, start, 150, 1000);
		helper.assertTrue(flood > 90, "a 150-packet flood was not cut off after about 5 s of packets (rejected at " + flood + ")");
		helper.succeed();
	}

	/** {@code n} move packets within one server tick, rising at {@code ups} from {@code start}: the packet the server rejected (0: none). */
	private static int burst(GameTestHelper helper, Vec3 start, int n, double ups) {
		PacketPlayer p = new PacketPlayer(helper.getLevel(), "burst");
		try {
			p.teleport(start);
			int corrections = ServerCorrections.EVENTS.size();
			for (int k = 1; k <= n; k++) {
				p.move(start.add(0, k * ups * UPS, 0), false);
				if (ServerCorrections.EVENTS.size() > corrections) return k;
			}
			return 0;
		} finally {
			p.leave();
		}
	}

	/** Which bursts of 1-12 packets the server accepts, rising at 500-6000 u/s: one line per burst size. */
	private static String grid(GameTestHelper helper, Vec3 start) {
		StringBuilder out = new StringBuilder();
		for (int n = 1; n <= 12; n++) {
			out.append("\n    ").append(n).append(':');
			for (int ups = 500; ups <= 6000; ups += 500) if (burst(helper, start, n, ups) == 0) out.append(' ').append(ups);
		}
		return out.toString();
	}

	/**
	 * C3: ramp contact resets the fall only on a slope (Source's surf contact, 0.01 < normal z < 0.7). Through the packet
	 * path: 18-block falls onto a lone steep ramp's flat back half, onto a full ramp cell, and onto the floor brushing a ramp
	 * cell's side all hurt like stone; a fall onto a slope and a short slide to the floor does not.
	 */
	@GameTest(skyAccess = true)
	public void onlySlopesBreakAFall(GameTestHelper helper) {
		AFrame a = ramp(helper, false);
		SurfRampBlock steep = SurfBlocks.STEEP_SURF_RAMP, surf = SurfBlocks.SURF_RAMP;
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.STONE);
		helper.setBlock(new BlockPos(6, 1, 0), steep.defaultBlockState().setValue(SurfRampBlock.FACING, Direction.EAST).setValue(steep.cut, steep.p));
		helper.setBlock(new BlockPos(0, 1, 6), surf.defaultBlockState().setValue(surf.cut, surf.p + surf.q));
		helper.setBlock(new BlockPos(7, 1, 7), surf.defaultBlockState().setValue(surf.cut, surf.p + surf.q));
		PacketPlayer p = new PacketPlayer(helper.getLevel(), "faller");
		try {
			Object[][] landings = {{"stone", new Vec3(0.5, 2, 0.5), 15}, {"a lone steep ramp's flat back half", new Vec3(6.25, 2, 0.5), 15},
					{"a full ramp cell", new Vec3(0.5, 2, 6.5), 15}, {"the floor beside a ramp cell's side", new Vec3(6.69, 1, 7.5), 15}};
			for (Object[] l : landings) {
				Vec3 land = helper.absoluteVec((Vec3) l[1]);
				fall(p, land.add(0, 18, 0), land);
				p.move(land, true);
				p.tick();
				helper.assertValueEqual(Math.round(20 - p.player.getHealth()), l[2], "damage from an 18-block fall onto " + l[0]);
			}
			// Onto the slope (not ground: the client says so), then down it to the floor at its toe.
			Vec3 slope = new Vec3(a.eastX(2, 0.001), a.base() + 2, a.z0() + 1.5), toe = new Vec3(a.eastX(0, 0.001), a.base(), a.z0() + 1.5);
			fall(p, slope.add(0, 18, 0), slope);
			p.move(toe, true);
			p.tick();
			helper.assertValueEqual(p.player.getHealth(), 20f, "health after an 18-block fall onto a slope and a slide to its toe");
		} finally {
			p.leave();
		}
		helper.succeed();
	}

	/** From {@code top} down to {@code land} in 2-block steps (airborne), one packet per server tick, starting healed. */
	private static void fall(PacketPlayer p, Vec3 top, Vec3 land) {
		p.teleport(top);
		p.heal();
		for (double y = top.y - 2; y > land.y; y -= 2) {
			p.move(new Vec3(land.x, y, land.z), false);
			p.tick();
		}
		p.move(land, false);
		p.tick();
	}

	/**
	 * e3 (C5, B8): only a surfer's knockback starts from the movement the client reported. Away from ramps a hit during a
	 * sprint jump (0.612 blocks/tick) knocks back as in vanilla; after a teleport (an ender pearl's) the old movement is
	 * gone; a surfer hit on a ramp keeps its momentum.
	 */
	@GameTest(skyAccess = true)
	public void onlySurfersKeepTheirMomentumWhenHit(GameTestHelper helper) {
		AFrame a = ramp(helper, false);
		PacketPlayer p = new PacketPlayer(helper.getLevel(), "target"), attacker = new PacketPlayer(helper.getLevel(), "attacker");
		ServerLevel level = helper.getLevel();
		try {
			// Far from any ramp: in the open sky above the test area.
			Vec3 feet = helper.absoluteVec(new Vec3(3.5, 40, 6.5));
			p.teleport(feet);
			attacker.teleport(feet.add(1.5, 0, 0));
			p.player.setDeltaMovement(0.182, 0.333, 0);
			p.player.setKnownMovement(new Vec3(0.612, 0.42, 0));
			p.player.hurtServer(level, level.damageSources().playerAttack(attacker.player), 1);
			helper.assertTrue(Math.abs(p.player.getDeltaMovement().x - (0.182 / 2 - 0.4)) < 1e-6, "knockback far from ramps: " + p.player.getDeltaMovement());
			// Surfing along the slope at 1500 u/s, then an ender pearl's teleport and its damage.
			p.heal();
			Vec3 slope = new Vec3(a.eastX(1.5, 0.001), a.base() + 1.5, a.z0() + 0.2);
			p.teleport(slope);
			p.move(slope.add(0, 0, 1500 * UPS), false);
			helper.assertTrue(p.player.getKnownMovement().z > 1.8, "known movement " + p.player.getKnownMovement());
			p.player.teleport(new TeleportTransition(level, feet, Vec3.ZERO, 0, 0, Relative.ROTATION, TeleportTransition.DO_NOTHING));
			p.player.hurtServer(level, level.damageSources().enderPearl(), 5);
			helper.assertTrue(p.player.getDeltaMovement().lengthSqr() < 1e-12, "velocity after an ender pearl: " + p.player.getDeltaMovement());
			// A surfer hit on the ramp keeps its momentum (knockback adds to its own movement).
			p.heal();
			p.teleport(slope);
			p.move(slope.add(0, 0, 1500 * UPS), false);
			attacker.teleport(p.player.position().add(-1.5, 0, 0));
			p.player.hurtServer(level, level.damageSources().playerAttack(attacker.player), 1);
			helper.assertTrue(p.player.getDeltaMovement().z > 0.9, "a surfer's velocity after a hit: " + p.player.getDeltaMovement());
		} finally {
			p.leave();
			attacker.leave();
		}
		helper.succeed();
	}

	/**
	 * B9: an elytra deploys on a ramp. The client counts ramps as air (CS:S) and starts gliding; the server's own
	 * zero-input simulation landed on the slope, and refused it as standing on the ground.
	 */
	@GameTest(skyAccess = true)
	public void surfersCanDeployAnElytraOnARamp(GameTestHelper helper) {
		AFrame a = ramp(helper, false);
		PacketPlayer p = new PacketPlayer(helper.getLevel(), "glider");
		try {
			p.player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
			Vec3 slope = new Vec3(a.eastX(1.5, 0.001), a.base() + 1.5, a.z0() + 1.5);
			p.teleport(slope);
			for (int t = 0; t < 3; t++) {
				p.move(slope, false);
				p.tick();
			}
			p.player.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(p.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			helper.assertTrue(p.player.isFallFlying(), "the server refused the elytra on the slope (server on ground " + p.player.onGround() + ")");
		} finally {
			p.leave();
		}
		helper.succeed();
	}

	/** Entities a player collides with (a boat) are brushes too, with the boat's box, before the ramps. */
	@GameTest
	public void brushWorldIncludesEntityColliders(GameTestHelper helper) {
		ramp(helper, false);
		var boat = helper.spawn(net.minecraft.world.entity.EntityTypes.OAK_BOAT, new Vec3(6.5, 1, 6.5));
		Player p = player(helper, helper.absoluteVec(new Vec3(6.5, 1, 4.8)));
		BlockPos anchor = p.blockPosition();
		List<Brush> world = BrushWorld.collect(helper.getLevel(), p, p.getBoundingBox().inflate(2), anchor, false);
		AABB b = boat.getBoundingBox();
		double k = SourceUnits.PER_BLOCK;
		boolean found = world.stream().anyMatch(brush -> Math.abs(brush.min().x() - (b.minX - anchor.getX()) * k) < 1e-6 && Math.abs(brush.max().z() - (b.maxY - anchor.getY()) * k) < 1e-6);
		helper.assertTrue(found, "no brush for the boat at " + b + " among " + world.size());
		p.discard();
		helper.succeed();
	}

	/**
	 * The brush world lists ramps last, and gives stone under a slope that touches it the slope plane as a bevel: a 6-tall
	 * east-facing 5:4 ramp whose full cells are stone, where the slope passes through the top-front edge of stone cell
	 * (u 3, y 0).
	 */
	@GameTest
	public void brushWorldBevelsStoneUnderTheSlope(GameTestHelper helper) {
		int p = 5, q = 4, plane = 24;
		SurfRampBlock ramp = SurfBlocks.SURF_RAMP;
		for (int w = 0; w < 3; w++) for (int y = 0; y < 6; y++) for (int u = 0; p * u < plane; u++) {
			int cut = plane - p * u - q * y;
			if (cut < 1) continue;
			helper.setBlock(new BlockPos(u, 1 + y, 2 + w), cut >= p + q ? Blocks.STONE.defaultBlockState()
					: ramp.defaultBlockState().setValue(SurfRampBlock.FACING, Direction.EAST).setValue(ramp.cut, cut));
		}
		BlockPos anchor = helper.absolutePos(new BlockPos(2, 1, 3));
		AABB region = new AABB(helper.absolutePos(new BlockPos(0, 1, 2))).expandTowards(5, 6, 3);
		List<Brush> world = BrushWorld.collect(helper.getLevel(), null, region, anchor, false);
		List<Brush> ramps = BrushWorld.collect(helper.getLevel(), null, region, anchor, true);
		int boxes = world.size() - ramps.size();
		helper.assertTrue(boxes > 0 && !ramps.isEmpty(), boxes + " boxes, " + ramps.size() + " ramp brushes");
		helper.assertTrue(world.subList(boxes, world.size()).equals(ramps), "the ramp brushes are not last, in order");
		int beveled = 0;
		for (Brush box : world.subList(0, boxes)) {
			if (box.planes().size() == 7) {
				var bevel = box.planes().get(6);
				helper.assertTrue(Math.abs(bevel.nz()) > 0 && Math.abs(bevel.nz()) < 1, "a box's seventh plane is not a slope: " + bevel);
				beveled++;
			}
		}
		// Stone cell (u 3, y 0) in each of the 3 slices touches the slope at its top-front edge; no other box does.
		helper.assertValueEqual(beveled, 3, "stone boxes with the slope as a bevel");
		helper.succeed();
	}
}
