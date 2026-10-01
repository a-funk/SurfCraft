package dev.afunk.surfcraft.block;

import dev.afunk.surfcraft.physics.RampCell;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One cell of a surf ramp with rise:run {@code p:q}. {@code FACING} is the side the slope faces (descends toward);
 * {@code cut} selects the solid {@code RampCell(p, q, cut)}. Placing a ramp continues the plane of a neighbouring
 * ramp of the same type and facing, so cells join into one smooth slope of any size.
 */
public final class SurfRampBlock extends HorizontalDirectionalBlock {
	/** How far (chessboard distance) placement searches its ramp for a cell that knows the plane. */
	private static final int SEARCH = 8;
	/** The 26 neighbour offsets: faces, then edges, then corners. */
	private static final List<BlockPos> NEIGHBOURS = BlockPos.betweenClosedStream(-1, -1, -1, 1, 1, 1)
			.map(BlockPos::immutable)
			.filter(d -> !d.equals(BlockPos.ZERO))
			.sorted(Comparator.comparingInt(d -> d.distManhattan(BlockPos.ZERO)))
			.toList();

	public final int p, q;
	public final IntegerProperty cut;
	private final Function<BlockState, VoxelShape> shapes;

	public SurfRampBlock(int p, int q, Properties properties) {
		// Set before super(): the constructor builds the state definition, which needs this block's cut range.
		this.p = p;
		this.q = q;
		cut = IntegerProperty.create("cut", 1, p + q);
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(cut, p));
		shapes = getShapeForEachState(SurfRampBlock::staircase);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, cut);
	}

	public static boolean isRamp(BlockState state) {
		return state.getBlock() instanceof SurfRampBlock;
	}

	public static RampCell cell(BlockState state) {
		SurfRampBlock block = (SurfRampBlock) state.getBlock();
		return new RampCell(block.p, block.q, state.getValue(block.cut));
	}

	/** The cell coordinate along {@code facing}: cells on one plane share {@code cut + p*U + q*y}. */
	public static int cellU(Direction facing, BlockPos pos) {
		return switch (facing) {
			case EAST -> pos.getX();
			case WEST -> -pos.getX() - 1;
			case SOUTH -> pos.getZ();
			default -> -pos.getZ() - 1;
		};
	}

	/** The cell's exact solid as planes {@code nx, ny, nz, d} ({@code n·x <= d}) in world block coordinates. */
	public static double[][] worldPlanes(BlockState state, BlockPos pos) {
		Direction f = state.getValue(FACING);
		double[][] planes = cell(state).localPlanes(f.getStepX(), f.getStepZ());
		for (double[] pl : planes) pl[3] += pl[0] * pos.getX() + pl[1] * pos.getY() + pl[2] * pos.getZ();
		return planes;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return placementState(context.getLevel(), context.getClickedPos(), context.getHorizontalDirection().getOpposite());
	}

	/**
	 * The state for a ramp placed at {@code pos} facing {@code facing}: it continues the plane of the nearest partial
	 * cell of this type and facing that is connected to it (face neighbours first, then edge, then corner, then
	 * breadth-first through the ramp up to {@link #SEARCH} blocks away), if that plane covers {@code pos}. Full cells
	 * don't keep their plane (it may lie higher), so the search passes through them; failing that, the full
	 * neighbours bound the plane from below. With no ramp next to it, the slope starts at the cell's bottom front edge
	 * ({@code cut = p}).
	 */
	public BlockState placementState(BlockGetter level, BlockPos pos, Direction facing) {
		pos = pos.immutable();
		int u = cellU(facing, pos), bound = 0;
		Set<BlockPos> seen = new HashSet<>(List.of(pos));
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(pos));
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (BlockPos d : NEIGHBOURS) {
				BlockPos n = at.offset(d);
				if (n.distChessboard(pos) > SEARCH || !seen.add(n)) continue;
				BlockState ns = level.getBlockState(n);
				if (ns.getBlock() != this || ns.getValue(FACING) != facing) continue;
				int nCut = ns.getValue(cut), c = RampCell.continueCut(p, q, nCut, cellU(facing, n), n.getY(), u, pos.getY());
				if (nCut < p + q && c >= 1) return state(facing, c);
				if (at == pos) bound = Math.max(bound, c);
				queue.add(n);
			}
		}
		return state(facing, bound >= 1 ? bound : p);
	}

	private BlockState state(Direction facing, int c) {
		return defaultBlockState().setValue(FACING, facing).setValue(cut, Math.min(c, p + q));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.apply(state);
	}

	/** The inscribed staircase (8 slices, under the true slope), u along the facing and w across it. */
	private static VoxelShape staircase(BlockState state) {
		Direction f = state.getValue(FACING);
		VoxelShape shape = Shapes.empty();
		for (double[] b : cell(state).staircase(8)) {
			double u0 = b[0], u1 = b[2], y0 = b[1], y1 = b[3];
			shape = Shapes.or(shape, switch (f) {
				case EAST -> Shapes.box(u0, y0, 0, u1, y1, 1);
				case WEST -> Shapes.box(1 - u1, y0, 0, 1 - u0, y1, 1);
				case SOUTH -> Shapes.box(0, y0, u0, 1, y1, u1);
				default -> Shapes.box(0, y0, 1 - u1, 1, y1, 1 - u0);
			});
		}
		return shape.optimize();
	}
}
