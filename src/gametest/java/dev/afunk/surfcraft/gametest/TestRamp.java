package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.physics.RampCell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A ramp to build in tests: the cells of a box ({@code nU} columns along the facing, {@code nY} rows, {@code nW} long)
 * under the plane through a seed cell with the standalone cut {@code p}. The expected cut of cell (u, y) is the plane
 * formula {@code C - p*u - q*y} clamped to {@code p+q}, with {@code C = p + p*seedU + q*seedY}.
 */
record TestRamp(SurfRampBlock block, Direction facing, BlockPos origin, int nU, int nY, int nW, int seedU, int seedY) {
	/** Bottom up, front to back: an order the joining rule builds exactly (as a player builds a ramp). */
	static final Comparator<Cell> BOTTOM_UP = Comparator.comparingInt(Cell::y).thenComparingInt(c -> -c.u()).thenComparingInt(Cell::w);

	record Cell(int u, int y, int w) {
		boolean touches(Cell o) {
			return Math.abs(u - o.u) <= 1 && Math.abs(y - o.y) <= 1 && Math.abs(w - o.w) <= 1;
		}
	}

	int planeCut(Cell c) {
		return RampCell.continueCut(block.p, block.q, block.p, seedU, seedY, c.u, c.y);
	}

	BlockPos pos(Cell c) {
		return switch (facing) {
			case EAST -> origin.offset(c.u, c.y, c.w);
			case WEST -> origin.offset(-c.u, c.y, c.w);
			case SOUTH -> origin.offset(c.w, c.y, c.u);
			default -> origin.offset(c.w, c.y, -c.u);
		};
	}

	List<Cell> box() {
		List<Cell> cells = new ArrayList<>();
		for (int u = 0; u < nU; u++) for (int y = 0; y < nY; y++) for (int w = 0; w < nW; w++) cells.add(new Cell(u, y, w));
		return cells;
	}

	/** The seed first, then the other cells under the plane in the given order. */
	List<Cell> sorted(Comparator<Cell> order) {
		Cell seed = new Cell(seedU, seedY, 0);
		List<Cell> cells = new ArrayList<>(box().stream().filter(c -> planeCut(c) >= 1 && !c.equals(seed)).sorted(order).toList());
		cells.addFirst(seed);
		return cells;
	}

	/** The seed first, then a random cell touching an already placed one, until all cells are placed. */
	List<Cell> randomConnected(long seed) {
		Random rng = new Random(seed);
		List<Cell> left = new ArrayList<>(sorted(Comparator.comparingInt(Cell::u))), order = new ArrayList<>();
		order.add(left.removeFirst());
		while (!left.isEmpty()) {
			List<Cell> frontier = left.stream().filter(c -> order.stream().anyMatch(c::touches)).toList();
			Cell next = frontier.get(rng.nextInt(frontier.size()));
			left.remove(next);
			order.add(next);
		}
		return order;
	}

	void clear(Level level) {
		for (Cell c : box()) level.setBlock(pos(c), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
	}

	/** Places the cells one by one with the block's own placement rule (no click), then {@link #check}s them. */
	@Nullable String place(Level level, List<Cell> order) {
		for (Cell c : order) level.setBlock(pos(c), block.placementState(level, pos(c), facing), Block.UPDATE_ALL);
		return check(level, order);
	}

	/**
	 * Builds the cells by hand through the item, as a player does: each cell is a right-click on the face of the cell of
	 * this ramp it grows from (in front, else below, else the previous or next slice, else behind), the first on the
	 * cell itself (like a click on grass, there is no block to continue). Then {@link #check}s them.
	 */
	@Nullable String placeByHand(Player player, List<Cell> order) {
		Set<Cell> placed = new HashSet<>();
		for (Cell c : order) {
			BlockPos pos = pos(c), from = pos;
			for (Cell n : List.of(new Cell(c.u + 1, c.y, c.w), new Cell(c.u, c.y - 1, c.w), new Cell(c.u, c.y, c.w - 1), new Cell(c.u, c.y, c.w + 1), new Cell(c.u - 1, c.y, c.w))) {
				if (placed.contains(n)) {
					from = pos(n);
					break;
				}
			}
			Direction face = from.equals(pos) ? Direction.UP : Direction.getNearest(pos.subtract(from), Direction.UP);
			player.setYRot(facing.getOpposite().toYRot());
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
			player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(from), face, from, false)));
			placed.add(c);
		}
		return check(player.level(), order);
	}

	/**
	 * Every cell has exactly the plane formula's cut and this facing, and every partial cell reports the same world slope
	 * plane (its {@link RampCell#localPlanes} slope moved to the block); describes the first problem, or null.
	 */
	@Nullable String check(Level level, List<Cell> order) {
		for (Cell c : order) {
			BlockState state = level.getBlockState(pos(c));
			int want = Math.min(planeCut(c), block.p + block.q);
			if (!state.is(block) || state.getValue(block.cut) != want || state.getValue(SurfRampBlock.FACING) != facing) {
				return "%s facing %s, cell %s (placed %d of %d): got %s, want cut %d".formatted(block, facing, c, order.indexOf(c) + 1, order.size(), state, want);
			}
		}
		double[] first = null;
		for (Cell c : order) {
			if (planeCut(c) >= block.p + block.q) continue;
			BlockPos at = pos(c);
			double[] slope = SurfRampBlock.cell(level.getBlockState(at)).localPlanes(facing.getStepX(), facing.getStepZ())[6];
			slope[3] += slope[0] * at.getX() + slope[1] * at.getY() + slope[2] * at.getZ();
			if (first == null) first = slope;
			for (int k = 0; k < 4; k++) {
				// Game tests run millions of blocks out, where d carries ~1e-9 of double rounding: compare relatively.
				if (Math.abs(slope[k] - first[k]) > 1e-12 * Math.max(1, Math.abs(first[k]))) return "%s facing %s: cell %s slope plane %s differs from %s".formatted(
						block, facing, c, Arrays.toString(slope), Arrays.toString(first));
			}
		}
		return null;
	}
}
