package dev.afunk.surfcraft.block;

import dev.afunk.surfcraft.physics.RampCell;
import java.util.ArrayList;
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
import org.jspecify.annotations.Nullable;

/**
 * One cell of a surf ramp with rise:run {@code p:q}. {@code FACING} is the side the slope faces (descends toward);
 * {@code cut} selects the solid {@code RampCell(p, q, cut)}. Placing a ramp continues the plane of a neighbouring
 * ramp of the same type and facing, so cells join into one smooth slope of any size.
 */
public final class SurfRampBlock extends HorizontalDirectionalBlock {
	/** How far (chessboard distance) placement searches its ramp for a cell that knows the plane. */
	private static final int SEARCH = 8;
	/** The 26 neighbour offsets. */
	private static final List<BlockPos> NEIGHBOURS = BlockPos.betweenClosedStream(-1, -1, -1, 1, 1, 1).map(BlockPos::immutable).filter(d -> !d.equals(BlockPos.ZERO)).toList();
	/** Thickness of the occlusion boxes: under every face detail (1/20 of a block), so it never decides what culls. */
	private static final double SKIN = 1.0 / 128;

	public final int p, q;
	public final IntegerProperty cut;
	private final Function<BlockState, VoxelShape> shapes, occlusion;

	public SurfRampBlock(int p, int q, Properties properties) {
		// Set before super(): the constructor builds the state definition, which needs this block's cut range.
		this.p = p;
		this.q = q;
		cut = IntegerProperty.create("cut", 1, p + q);
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(cut, p));
		shapes = getShapeForEachState(SurfRampBlock::staircase);
		occlusion = getShapeForEachState(SurfRampBlock::faces);
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

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockPos pos = context.getClickedPos();
		return placementState(context.getLevel(), pos, context.getHorizontalDirection().getOpposite(),
				context.replacingClickedOnBlock() ? null : pos.relative(context.getClickedFace().getOpposite()));
	}

	/** {@link #placementState(BlockGetter, BlockPos, Direction, BlockPos)} with no block clicked. */
	public BlockState placementState(BlockGetter level, BlockPos pos, Direction facing) {
		return placementState(level, pos, facing, null);
	}

	/**
	 * The state for a ramp placed at {@code pos} facing {@code facing} by a click on {@code clicked}: it continues the plane
	 * of a ramp of this type and facing. That is the clicked block's plane, if it is a slope cell of such a ramp whose
	 * plane reaches pos. Otherwise the search goes breadth-first through the ramp (up to {@link #SEARCH} blocks), first
	 * within pos's own slice across the ramp, then anywhere, and takes the nearest slope cells; of equally near planes,
	 * the lowest. Full cells don't keep their plane (it may lie higher), so the search passes through them, a clicked full
	 * cell must stay under the plane, and failing a plane the full neighbours bound it from below. With no ramp next to it,
	 * the slope starts at the cell's bottom front edge ({@code cut = p}).
	 */
	public BlockState placementState(BlockGetter level, BlockPos pos, Direction facing, @Nullable BlockPos clicked) {
		pos = pos.immutable();
		int min = 1, bound = 0;
		BlockState against = clicked == null ? null : level.getBlockState(clicked);
		if (against != null && against.getBlock() == this && against.getValue(FACING) == facing) {
			int c = continueCut(against, clicked, pos, facing);
			if (against.getValue(cut) < p + q && c >= 1) return state(facing, c);
			if (against.getValue(cut) == p + q) min = Math.max(1, c);
		}
		Direction.Axis along = facing.getClockWise().getAxis();
		for (boolean ownSlice : new boolean[] {true, false}) {
			Set<BlockPos> seen = new HashSet<>(List.of(pos));
			List<BlockPos> layer = List.of(pos);
			int best = 0;
			while (!layer.isEmpty() && best == 0) {
				List<BlockPos> next = new ArrayList<>();
				for (BlockPos at : layer) for (BlockPos d : NEIGHBOURS) {
					BlockPos n = at.offset(d);
					if (n.distChessboard(pos) > SEARCH || ownSlice && n.get(along) != pos.get(along) || !seen.add(n)) continue;
					BlockState ns = level.getBlockState(n);
					if (ns.getBlock() != this || ns.getValue(FACING) != facing) continue;
					int c = continueCut(ns, n, pos, facing);
					if (at == pos) bound = Math.max(bound, c);
					if (ns.getValue(cut) < p + q && c >= min && (best == 0 || c < best)) best = c;
					next.add(n);
				}
				layer = next;
			}
			if (best >= 1) return state(facing, best);
		}
		return state(facing, bound >= 1 ? bound : p);
	}

	/** The cut at {@code pos} of the plane through the cell {@code n} (state {@code ns}). */
	private int continueCut(BlockState ns, BlockPos n, BlockPos pos, Direction facing) {
		return RampCell.continueCut(p, q, ns.getValue(cut), cellU(facing, n), n.getY(), cellU(facing, pos), pos.getY());
	}

	private BlockState state(Direction facing, int c) {
		return defaultBlockState().setValue(FACING, facing).setValue(cut, Math.min(c, p + q));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shapes.apply(state);
	}

	@Override
	protected VoxelShape getOcclusionShape(BlockState state) {
		return occlusion.apply(state);
	}

	/** Light passes or stops at each face by its occlusion shape, as for stairs and slabs. */
	@Override
	protected boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}

	/** The inscribed staircase (8 slices, under the true slope), u along the facing and w across it. */
	private static VoxelShape staircase(BlockState state) {
		return prisms(state.getValue(FACING), cell(state).staircase(8));
	}

	/**
	 * What culls neighbours' faces and stops light: a full cell is a cube; a slope cell is a {@link #SKIN}-thin box on each
	 * axial face it has (bottom and back, and top and front where the slope leaves them), exactly as wide as that face, so
	 * culling and light match the true solid there. The faces along the ramp are slanted, which a voxel face can't match:
	 * RampModels gives slope cells' cross-sections no cull face.
	 */
	private static VoxelShape faces(BlockState state) {
		RampCell c = cell(state);
		if (c.full()) return Shapes.block();
		List<double[]> boxes = new ArrayList<>(List.of(new double[] {0, 0, c.maxU(), SKIN}, new double[] {0, 0, SKIN, c.maxY()}));
		if (c.cut() > c.q()) boxes.add(new double[] {0, 1 - SKIN, c.uAt(1), 1});
		if (c.cut() > c.p()) boxes.add(new double[] {1 - SKIN, 0, 1, (double) (c.cut() - c.p()) / c.q()});
		return prisms(state.getValue(FACING), boxes);
	}

	/** Boxes {@code {u0, y0, u1, y1}} in the cell's (u, y) frame, each spanning the cell along the ramp, turned to the facing. */
	private static VoxelShape prisms(Direction f, List<double[]> boxes) {
		VoxelShape shape = Shapes.empty();
		for (double[] b : boxes) {
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
