package dev.afunk.surfcraft.movement;

import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.physics.Brush;
import dev.afunk.surfcraft.physics.Plane;
import dev.afunk.surfcraft.physics.RampBrushes;
import dev.afunk.surfcraft.physics.RampCell;
import dev.afunk.surfcraft.physics.SourceUnits;
import dev.afunk.surfcraft.physics.V3;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * What a player collides with near ramps, as Source brushes in a local frame: Source units relative to an anchor block
 * (Source = (x, -z, y) * 39.37 from the anchor's corner), so float origins stay precise millions of blocks out.
 *
 * <p>Order matters to Source's trace (the first brush wins ties): block boxes, entity colliders and the world border
 * first, then every ramp cell through {@link RampBrushes#of}, which merges cells into brushes that surf like one brush
 * per ramp. Ramp blocks' own (staircase) shapes are never used. A box that lies under a ramp's slope and touches it
 * (stone under a ramp) gets the slope plane as a bevel: its shape is unchanged, but the hull riding over its corner sees
 * the slope instead of the box's faces (otherwise it bumps there, as at a seam between ramp brushes).
 */
public final class BrushWorld {
	static final double K = SourceUnits.PER_BLOCK;

	private BrushWorld() {
	}

	/** Whether any surf ramp block lies in the blocks {@code box} touches. */
	public static boolean rampNear(CollisionGetter level, AABB box) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int x0 = floor(box.minX), y0 = floor(box.minY), z0 = floor(box.minZ), x1 = floor(box.maxX), y1 = floor(box.maxY), z1 = floor(box.maxZ);
		for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
			if (SurfRampBlock.isRamp(state(level, pos.set(x, y, z)))) return true;
		}
		return false;
	}

	/** The block's state without loading its chunk (as vanilla's collision reads blocks): air where none is loaded. */
	private static BlockState state(CollisionGetter level, BlockPos pos) {
		BlockGetter chunk = level.getChunkForCollisions(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
		return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos);
	}

	/**
	 * Brushes for everything {@code entity} collides with inside {@code region} (Minecraft coordinates), relative to
	 * {@code anchor}. With {@code rampsOnly}, just the ramp brushes.
	 */
	public static List<Brush> collect(Level level, @Nullable Entity entity, AABB region, BlockPos anchor, boolean rampsOnly) {
		int ax = anchor.getX(), ay = anchor.getY(), az = anchor.getZ();
		CollisionContext context = entity == null ? CollisionContext.empty() : CollisionContext.of(entity);
		List<double[]> boxes = new ArrayList<>();
		List<RampBrushes.Placed> cells = new ArrayList<>();
		Map<List<Long>, double[]> slopes = new LinkedHashMap<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		// One block more below and around: shapes taller than their cell (fences, walls) reach into the region.
		int x0 = floor(region.minX) - 1, y0 = floor(region.minY) - 1, z0 = floor(region.minZ) - 1, x1 = floor(region.maxX) + 1, y1 = floor(region.maxY),
				z1 = floor(region.maxZ) + 1;
		for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) {
			BlockState state = state(level, pos.set(x, y, z));
			if (state.isAir()) continue;
			if (SurfRampBlock.isRamp(state)) {
				if (x < x0 + 1 || x > x1 - 1 || z < z0 + 1 || z > z1 - 1 || y < y0 + 1) continue;
				Direction f = state.getValue(SurfRampBlock.FACING);
				RampCell cell = SurfRampBlock.cell(state);
				RampBrushes.Placed placed = new RampBrushes.Placed(x - ax, y - ay, z - az, f.getStepX(), f.getStepZ(), cell);
				cells.add(placed);
				if (!cell.full()) slopes.computeIfAbsent(slopeKey(placed), k -> slopePlane(placed));
				continue;
			}
			if (rampsOnly) continue;
			VoxelShape shape = state.getCollisionShape(level, pos, context);
			if (shape.isEmpty()) continue;
			if (shape == Shapes.block()) addBox(boxes, region, x, y, z, x + 1, y + 1, z + 1, ax, ay, az);
			else for (AABB b : shape.toAabbs()) addBox(boxes, region, x + b.minX, y + b.minY, z + b.minZ, x + b.maxX, y + b.maxY, z + b.maxZ, ax, ay, az);
		}
		List<Brush> out = new ArrayList<>(boxes.size() + cells.size());
		if (!rampsOnly) {
			if (entity != null) {
				for (VoxelShape shape : level.getEntityCollisions(entity, region)) {
					for (AABB b : shape.toAabbs()) addBox(boxes, region, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, ax, ay, az);
				}
				WorldBorder border = level.getWorldBorder();
				if (border.isInsideCloseToBorder(entity, region)) borderBoxes(boxes, region, border, ax, ay, az);
			}
			for (double[] b : boxes) out.add(box(b, slopes.values()));
		}
		out.addAll(RampBrushes.of(cells));
		return out;
	}

	/** A box (anchor-relative, clipped to nothing: kept whole) if it reaches into the region. */
	private static void addBox(List<double[]> boxes, AABB region, double x0, double y0, double z0, double x1, double y1, double z1, int ax, int ay, int az) {
		if (x1 <= region.minX || x0 >= region.maxX || y1 <= region.minY || y0 >= region.maxY || z1 <= region.minZ || z0 >= region.maxZ) return;
		boxes.add(new double[] {x0 - ax, y0 - ay, z0 - az, x1 - ax, y1 - ay, z1 - az});
	}

	/** The world border's solid (everything outside it), as boxes around the region. */
	private static void borderBoxes(List<double[]> boxes, AABB region, WorldBorder border, int ax, int ay, int az) {
		double bx0 = Math.floor(border.getMinX()), bz0 = Math.floor(border.getMinZ()), bx1 = Math.ceil(border.getMaxX()), bz1 = Math.ceil(border.getMaxZ());
		AABB r = region.inflate(1);
		if (r.minX < bx0) addBox(boxes, region, r.minX, r.minY, r.minZ, bx0, r.maxY, r.maxZ, ax, ay, az);
		if (r.maxX > bx1) addBox(boxes, region, bx1, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, ax, ay, az);
		if (r.minZ < bz0) addBox(boxes, region, r.minX, r.minY, r.minZ, r.maxX, r.maxY, bz0, ax, ay, az);
		if (r.maxZ > bz1) addBox(boxes, region, r.minX, r.minY, bz1, r.maxX, r.maxY, r.maxZ, ax, ay, az);
	}

	/**
	 * A box brush; slope planes {@code {nx, ny, nz, d}} (anchor-relative Minecraft coordinates) that the box lies under and
	 * touches are added as bevels.
	 */
	private static Brush box(double[] b, Iterable<double[]> slopes) {
		List<Plane> planes = new ArrayList<>(7);
		planes.add(new Plane(1, 0, 0, b[3] * K));
		planes.add(new Plane(-1, 0, 0, -b[0] * K));
		planes.add(new Plane(0, 0, 1, b[4] * K));
		planes.add(new Plane(0, 0, -1, -b[1] * K));
		planes.add(new Plane(0, -1, 0, b[5] * K));
		planes.add(new Plane(0, 1, 0, -b[2] * K));
		double cx = (b[0] + b[3]) / 2, cy = (b[1] + b[4]) / 2, cz = (b[2] + b[5]) / 2, hx = (b[3] - b[0]) / 2, hy = (b[4] - b[1]) / 2, hz = (b[5] - b[2]) / 2;
		for (double[] s : slopes) {
			double top = s[0] * cx + s[1] * cy + s[2] * cz + Math.abs(s[0]) * hx + Math.abs(s[1]) * hy + Math.abs(s[2]) * hz;
			if (Math.abs(top - s[3]) < 1e-9) planes.add(new Plane(s[0], -s[2], s[1], s[3] * K));
		}
		return new Brush(planes, new V3(b[0] * K, -b[5] * K, b[1] * K), new V3(b[3] * K, -b[2] * K, b[4] * K));
	}

	/** Identifies a slope: facing, p:q and the plane constant every cell on it shares. */
	private static List<Long> slopeKey(RampBrushes.Placed c) {
		RampCell r = c.cell();
		long u = c.fx() > 0 ? c.x() : c.fx() < 0 ? -c.x() - 1 : c.fz() > 0 ? c.z() : -c.z() - 1;
		return List.of((long) c.fx(), (long) c.fz(), (long) r.p(), (long) r.q(), r.cut() + r.p() * u + (long) r.q() * c.y());
	}

	/** The cell's slope plane in anchor-relative Minecraft coordinates. */
	private static double[] slopePlane(RampBrushes.Placed c) {
		double[] local = c.cell().localPlanes(c.fx(), c.fz())[6];
		return new double[] {local[0], local[1], local[2], local[3] + local[0] * c.x() + local[1] * c.y() + local[2] * c.z()};
	}

	/** Minecraft coordinates (anchor-relative blocks) to Source units. */
	public static V3 toSource(Vec3 v, BlockPos anchor) {
		return new V3((v.x - anchor.getX()) * K, -(v.z - anchor.getZ()) * K, (v.y - anchor.getY()) * K);
	}

	/** Source units back to Minecraft coordinates. */
	public static Vec3 toMinecraft(V3 v, BlockPos anchor) {
		return new Vec3(anchor.getX() + v.x() / K, anchor.getY() + v.z() / K, anchor.getZ() - v.y() / K);
	}

	/** A Minecraft movement (blocks) in Source units. */
	public static V3 toSourceDelta(Vec3 d) {
		return new V3(d.x * K, -d.z * K, d.y * K);
	}

	public static Vec3 toMinecraftDelta(V3 d) {
		return new Vec3(d.x() / K, d.z() / K, -d.y() / K);
	}

	/** An entity's box as Source half extents (float dimensions, as its bounding box). */
	public static V3 hull(Entity entity) {
		double w = entity.getBbWidth() / 2.0f, h = entity.getBbHeight() / 2.0;
		return new V3(w * K, w * K, h * K);
	}

	/** Source-local min/max corners to a Minecraft box. */
	public static AABB toMinecraft(V3 min, V3 max, BlockPos anchor) {
		Vec3 a = toMinecraft(min, anchor), b = toMinecraft(max, anchor);
		return new AABB(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z), Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
	}

	private static int floor(double v) {
		return (int) Math.floor(v);
	}
}
