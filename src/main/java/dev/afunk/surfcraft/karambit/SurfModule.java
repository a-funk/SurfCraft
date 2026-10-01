package dev.afunk.surfcraft.karambit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.physics.RampCell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece of surf wall the Karambit carries: a {@code width x height x length} box of block states in the module frame,
 * x across the ramp, y up and z along it, with +z (south) the module's forward. Stored as a palette plus run-length
 * encoded palette indices, z fastest, so a ramp (a prism along z) takes one run per column.
 */
public record SurfModule(int width, int height, int length, List<BlockState> palette, List<Integer> runs) {
	public static final int MAX_SIZE = 32, MAX_PALETTE = 256, MAX_RUNS = 4096;
	public static final Codec<SurfModule> CODEC = RecordCodecBuilder.<SurfModule>create(i -> i.group(
			Codec.intRange(1, MAX_SIZE).fieldOf("width").forGetter(SurfModule::width),
			Codec.intRange(1, MAX_SIZE).fieldOf("height").forGetter(SurfModule::height),
			Codec.intRange(1, MAX_SIZE).fieldOf("length").forGetter(SurfModule::length),
			BlockState.CODEC.listOf(1, MAX_PALETTE).fieldOf("palette").forGetter(SurfModule::palette),
			Codec.INT.listOf(2, 2 * MAX_RUNS).fieldOf("runs").forGetter(SurfModule::runs)
	).apply(i, SurfModule::new)).validate(SurfModule::validate);
	/** The classic two-sided 51-degree surf ramp: Surf Ramp 5:4 on both sides of a ridge, 8 wide, 5 tall, 8 long. */
	public static final SurfModule DEFAULT = twoSided(SurfBlocks.SURF_RAMP, 4, 8);

	/** Encodes cells indexed by {@link #index}. */
	public static SurfModule of(int width, int height, int length, BlockState[] cells) {
		List<BlockState> palette = new ArrayList<>();
		Map<BlockState, Integer> ids = new HashMap<>();
		List<Integer> runs = new ArrayList<>();
		for (BlockState state : cells) {
			int id = ids.computeIfAbsent(state, s -> {
				palette.add(s);
				return palette.size() - 1;
			});
			if (!runs.isEmpty() && runs.get(runs.size() - 2) == id) runs.set(runs.size() - 1, runs.getLast() + 1);
			else runs.addAll(List.of(id, 1));
		}
		return new SurfModule(width, height, length, List.copyOf(palette), List.copyOf(runs));
	}

	public int index(int x, int y, int z) {
		return (y * width + x) * length + z;
	}

	public BlockState[] cells() {
		BlockState[] cells = new BlockState[width * height * length];
		for (int i = 0, at = 0; i < runs.size(); i += 2) Arrays.fill(cells, at, at += runs.get(i + 1), palette.get(runs.get(i)));
		return cells;
	}

	/** The number of non-air cells. */
	public int blocks() {
		int n = 0;
		for (int i = 0; i < runs.size(); i += 2) if (!palette.get(runs.get(i)).isAir()) n += runs.get(i + 1);
		return n;
	}

	/** Checks what the codec's field ranges can't: the runs cover the box exactly with valid palette indices. */
	public static DataResult<SurfModule> validate(SurfModule m) {
		if (m.palette.size() > MAX_PALETTE || m.runs.size() > 2 * MAX_RUNS || m.runs.size() % 2 != 0) return DataResult.error(() -> "module too complex");
		long cells = 0;
		for (int i = 0; i < m.runs.size(); i += 2) {
			int id = m.runs.get(i), n = m.runs.get(i + 1);
			if (id < 0 || id >= m.palette.size() || n < 1) return DataResult.error(() -> "bad module run " + id + " x" + n);
			cells += n;
		}
		long want = (long) m.width * m.height * m.length;
		return cells == want ? DataResult.success(m) : DataResult.error(() -> "module runs cover " + want + " cells wrongly");
	}

	/**
	 * Both sides of a ridge running along z: {@code half} columns facing west, then {@code half} facing east, each side's
	 * slope meeting the ground at its outer edge (the outer bottom cell has the standalone cut p) and every cell under it
	 * cut by the plane formula, so cells under the slope are full.
	 */
	static SurfModule twoSided(SurfRampBlock ramp, int half, int length) {
		int p = ramp.p, q = ramp.q, width = 2 * half, height = Math.ceilDiv(p * half, q);
		BlockState[] cells = new BlockState[width * height * length];
		for (int x = 0; x < width; x++) {
			Direction facing = x < half ? Direction.WEST : Direction.EAST;
			BlockPos outer = new BlockPos(x < half ? 0 : width - 1, 0, 0);
			for (int y = 0; y < height; y++) {
				int cut = RampCell.continueCut(p, q, p, SurfRampBlock.cellU(facing, outer), 0, SurfRampBlock.cellU(facing, new BlockPos(x, y, 0)), y);
				BlockState state = cut < 1 ? Blocks.AIR.defaultBlockState()
						: ramp.defaultBlockState().setValue(SurfRampBlock.FACING, facing).setValue(ramp.cut, Math.min(cut, p + q));
				for (int z = 0; z < length; z++) cells[(y * width + x) * length + z] = state;
			}
		}
		return of(width, height, length, cells);
	}
}
