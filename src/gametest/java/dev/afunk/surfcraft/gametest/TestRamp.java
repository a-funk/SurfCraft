package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfRampBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A ramp to build in tests: the cells of a box ({@code nU} columns along the facing, {@code nY} rows, {@code nW} long)
 * under the plane through a seed cell with the standalone cut {@code p}. The expected cut of cell (u, y) is the plane
 * formula {@code C - p*u - q*y} clamped to {@code p+q}, with {@code C = p + p*seedU + q*seedY}.
 */
record TestRamp(SurfRampBlock block, Direction facing, BlockPos origin, int nU, int nY, int nW, int seedU, int seedY) {
	record Cell(int u, int y, int w) {
		boolean touches(Cell o) {
			return Math.abs(u - o.u) <= 1 && Math.abs(y - o.y) <= 1 && Math.abs(w - o.w) <= 1;
		}
	}

	int planeCut(Cell c) {
		return block.p + block.p * seedU + block.q * seedY - block.p * c.u - block.q * c.y;
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

	/**
	 * Places the cells one by one with the block's own placement rule, then checks that every partial cell reports the
	 * same world slope plane through {@link SurfRampBlock#worldPlanes}; describes the first problem, or null.
	 */
	@Nullable String place(Level level, List<Cell> order) {
		for (Cell c : order) {
			BlockState state = block.placementState(level, pos(c), facing);
			level.setBlock(pos(c), state, Block.UPDATE_ALL);
			int want = Math.min(planeCut(c), block.p + block.q);
			if (state.getValue(block.cut) != want || state.getValue(SurfRampBlock.FACING) != facing) {
				return "%s facing %s, cell %s placed after %d cells: got %s, want cut %d".formatted(
						block, facing, c, order.indexOf(c), state, want);
			}
		}
		double[] first = null;
		for (Cell c : order) {
			if (planeCut(c) >= block.p + block.q) continue;
			// The slope is the only plane that is neither vertical nor horizontal.
			double[] slope = Arrays.stream(SurfRampBlock.worldPlanes(level.getBlockState(pos(c)), pos(c)))
					.filter(pl -> pl[1] != 0 && Math.abs(pl[1]) != 1).findFirst().orElseThrow();
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
