package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.physics.RampCell;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A two-sided ramp for the surf tests: its ridge runs along z at x = {@code ridge} (the east side's cells at x >= ridge
 * face east, the west side's face west), {@code height} tall from y = {@code base}, {@code length} long from z =
 * {@code z0}. Each cell gets the plane formula's cut directly. With {@code stone}, the full cells under the slopes are plain
 * stone, so the ramp blocks are only the slope's skin (the corner-bevel case).
 */
record AFrame(SurfRampBlock block, int ridge, int base, int z0, int height, int length, boolean stone) {
	void build(Level level) {
		int p = block.p, q = block.q, plane = q * height;
		for (int w = 0; w < length; w++) for (int y = 0; y < height; y++) for (int u = 0; p * u < plane; u++) {
			// The plane through the ridge top, C = q*height: the (unclamped) cut of cell (0, 0), carried to (u, y).
			int cut = RampCell.continueCut(p, q, plane, 0, 0, u, y);
			if (cut < 1) continue;
			for (Direction facing : new Direction[] {Direction.EAST, Direction.WEST}) {
				BlockState state = cut >= p + q && stone ? Blocks.STONE.defaultBlockState()
						: block.defaultBlockState().setValue(SurfRampBlock.FACING, facing).setValue(block.cut, Math.min(cut, p + q));
				int x = facing == Direction.EAST ? ridge + u : ridge - 1 - u;
				level.setBlock(new BlockPos(x, base + y, z0 + w), state, Block.UPDATE_CLIENTS);
			}
		}
	}

	/**
	 * How far the box is outside the A-frame's solid (blocks; negative: inside): the largest separation along the two
	 * slopes' normals and the vertical (the solid is under both planes and the ridge line).
	 */
	double clearance(AABB box) {
		int p = block.p, q = block.q;
		double l = Math.hypot(p, q);
		double east = p * (box.minX - ridge) + q * (box.minY - base), west = p * (ridge - box.maxX) + q * (box.minY - base);
		// Or above the ridge line: a box can rest on the apex.
		return Math.max((Math.max(east, west) - q * height) / l, box.minY - (base + height));
	}

	/** Feet on the east slope at height {@code y} above the base, at z, {@code gap} blocks clear along the normal. */
	double eastX(double y, double gap) {
		int p = block.p, q = block.q;
		return ridge + (q * height - q * y) / p + 0.3 + gap * Math.hypot(p, q) / p;
	}

	boolean alongside(double z) {
		return z > z0 && z < z0 + length;
	}
}
