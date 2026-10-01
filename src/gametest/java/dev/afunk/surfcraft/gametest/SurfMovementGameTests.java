package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.movement.BrushWorld;
import dev.afunk.surfcraft.physics.Brush;
import dev.afunk.surfcraft.physics.SourceUnits;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The server side of surfing, with real server players: exact collision with the slope instead of the staircase (b1),
 * ramp contact resetting the fall distance (c1), no sneak edge back-off near ramps (e2), and the brush world's bevel on
 * blocks under a slope. (Game tests run millions of blocks out: everything is measured relative to the ramp.)
 */
public class SurfMovementGameTests {
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
	 * e2: a sneaking player stepping off the ridge onto a steep slope is not held back at the edge (vanilla judges the
	 * edge by the staircase, and the server's re-simulation would shrink a surfer's moves).
	 */
	@GameTest
	public void sneakingIsNotHeldBackNearRamps(GameTestHelper helper) {
		BlockPos o = helper.absolutePos(BlockPos.ZERO);
		for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
		AFrame steep = new AFrame(SurfBlocks.STEEP_SURF_RAMP, o.getX() + 3, o.getY() + 1, o.getZ() + 2, 4, 3, false);
		steep.build(helper.getLevel());
		// A stone pillar behind the ridge, the player standing on its east edge, sneaking.
		for (int y = 1; y <= 4; y++) helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
		Player p = player(helper, new Vec3(o.getX() + 2.7, steep.base() + 4, o.getZ() + 3.5));
		p.setShiftKeyDown(true);
		p.setOnGround(true);
		double before = p.getX();
		p.move(MoverType.PLAYER, new Vec3(1.0, 0, 0));
		helper.assertTrue(Math.abs(p.getX() - before - 1.0) < 1e-9, "the move off the edge was cut to " + (p.getX() - before));
		p.discard();
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
