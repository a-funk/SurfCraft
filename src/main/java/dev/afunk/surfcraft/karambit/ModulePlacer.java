package dev.afunk.surfcraft.karambit;

import dev.afunk.surfcraft.block.SurfRampBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;

/**
 * Where a Karambit module goes and what placing it takes, shared by the server (which places) and the client preview
 * (which outlines the plan); also copies modules from ramps and keeps each player's last placements for undo.
 */
public final class ModulePlacer {
	/** Extend gives up on connected ramps with more blocks than this. ponytail: a flood fill per click/preview tick; cache it if huge ramps lag. */
	private static final int MAX_RAMP_BLOCKS = 8192;
	private static final int UNDO_DEPTH = 8;
	/** Each player's last placements, newest first (server thread only; cleared when the server stops). */
	static final Map<UUID, Deque<Placement>> HISTORY = new HashMap<>();
	private static final BlockState AIR = Blocks.AIR.defaultBlockState();

	private ModulePlacer() {
	}

	/**
	 * What a click would build: every module cell (air included) by world position, bottom up, the box they fill, the
	 * cells in the way, and why the click can't place (null if it can).
	 */
	public record Plan(Map<BlockPos, BlockState> cells, BoundingBox box, List<BlockPos> blocked, @Nullable Component problem, boolean extend) {
		public boolean ok() {
			return problem == null;
		}
	}

	/** The outcome of a click, for the action bar; refusals mark their blocked cells. */
	public record Result(boolean ok, Component message, List<BlockPos> blocked) {
		static Result fail(String key, Object... args) {
			return new Result(false, Component.translatable(key, args), List.of());
		}
	}

	record Change(BlockPos pos, BlockState before, BlockState after) {
	}

	record Placement(ResourceKey<Level> dimension, List<Change> changes, boolean paid) {
	}

	/** The knife's module, or the default two-sided ramp. */
	public static SurfModule module(ItemStack knife) {
		return knife.getOrDefault(KarambitItem.MODULE, SurfModule.DEFAULT);
	}

	/**
	 * A click on {@code face} of {@code clicked}: on a ramp, extend it; on anything else, place the module on that face,
	 * turned so its forward is the player's horizontal look, starting at the clicked block and running away from the
	 * player (and away from a clicked side face), centred across.
	 */
	public static Plan plan(Level level, Player player, ItemStack knife, BlockPos clicked, Direction face) {
		SurfModule module = module(knife);
		BlockState target = level.getBlockState(clicked);
		if (SurfRampBlock.isRamp(target)) return extend(level, player, module, clicked.immutable());
		Direction forward = player.getDirection();
		BlockPos anchor = target.canBeReplaced() ? clicked : clicked.relative(face);
		boolean sideways = forward.getAxis() == Direction.Axis.X;
		int sizeX = sideways ? module.length() : module.width(), sizeZ = sideways ? module.width() : module.length();
		BlockPos min = new BlockPos(start(anchor.getX(), sizeX, grow(face, forward, Direction.Axis.X)),
				face == Direction.DOWN ? anchor.getY() - module.height() + 1 : anchor.getY(),
				start(anchor.getZ(), sizeZ, grow(face, forward, Direction.Axis.Z)));
		return check(level, player, layout(module, turn(Direction.SOUTH, forward), min), false, null);
	}

	/** Which way the box grows along a horizontal axis from the anchor: away from the clicked face, else forward, else 0 (centred). */
	private static int grow(Direction face, Direction forward, Direction.Axis axis) {
		Direction way = face.getAxis() == axis ? face : forward.getAxis() == axis ? forward : null;
		return way == null ? 0 : way.getAxisDirection().getStep();
	}

	private static int start(int anchor, int size, int grow) {
		return grow > 0 ? anchor : grow < 0 ? anchor - size + 1 : anchor - (size - 1) / 2;
	}

	/**
	 * The module right after the end of the clicked ramp in the direction the player looks along it, on the ramp's
	 * bottom and across its lateral bounds. It must continue the ramp's end slice exactly ({@link #joins}): turned forward,
	 * else turned back (a one-sided ramp copied looking the other way).
	 */
	private static Plan extend(Level level, Player player, SurfModule module, BlockPos clicked) {
		Direction.Axis across = level.getBlockState(clicked).getValue(SurfRampBlock.FACING).getAxis();
		Direction.Axis along = across == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		Set<BlockPos> ramp = connectedRamp(level, clicked, Integer.MAX_VALUE);
		if (ramp == null) return new Plan(Map.of(), new BoundingBox(clicked), List.of(), Component.translatable("surfcraft.karambit.too_large"), true);
		BoundingBox box = BoundingBox.encapsulatingPositions(ramp).orElseThrow();
		Direction way = looking(player, along);
		int step = way.getAxisDirection().getStep();
		int end = step > 0 ? along.choose(box.maxX(), box.maxY(), box.maxZ()) : along.choose(box.minX(), box.minY(), box.minZ());
		int from = Math.min(end + step, end + step * module.length()), side = across.choose(box.minX(), box.minY(), box.minZ());
		BlockPos min = along == Direction.Axis.X ? new BlockPos(from, box.minY(), side) : new BlockPos(side, box.minY(), from);
		Plan first = null;
		for (Direction forward : List.of(way, way.getOpposite())) {
			Map<BlockPos, BlockState> cells = layout(module, turn(Direction.SOUTH, forward), min);
			if (joins(level, ramp, cells, way, end)) return check(level, player, cells, true, null);
			if (first == null) first = check(level, player, cells, true, Component.translatable("surfcraft.karambit.mismatch"));
		}
		return first;
	}

	/**
	 * Whether the module continues the ramp's end slice exactly: across the joint every ramp cell faces a cell in the same
	 * state, so each slope plane runs on unchanged ({@code RampCell.continueCut} along a ramp keeps the cut).
	 */
	private static boolean joins(Level level, Set<BlockPos> ramp, Map<BlockPos, BlockState> cells, Direction way, int end) {
		for (BlockPos p : ramp) {
			if (p.get(way.getAxis()) == end && !cells.getOrDefault(p.relative(way), AIR).equals(level.getBlockState(p))) return false;
		}
		for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
			BlockPos p = cell.getKey();
			if (p.get(way.getAxis()) == end + way.getAxisDirection().getStep() && SurfRampBlock.isRamp(cell.getValue())
					&& !cell.getValue().equals(level.getBlockState(p.relative(way.getOpposite())))) return false;
		}
		return true;
	}

	/** The module turned by {@code turn} with its box's min corner at {@code min}: world position to state, bottom up. */
	static Map<BlockPos, BlockState> layout(SurfModule module, Rotation turn, BlockPos min) {
		BlockState[] cells = module.cells();
		BlockPos far = new BlockPos(module.width() - 1, 0, module.length() - 1).rotate(turn);
		BlockPos origin = min.offset(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (int y = 0; y < module.height(); y++) {
			for (int x = 0; x < module.width(); x++) {
				for (int z = 0; z < module.length(); z++) out.put(origin.offset(new BlockPos(x, y, z).rotate(turn)), cells[module.index(x, y, z)].rotate(turn));
			}
		}
		return out;
	}

	/** Every non-air cell must land in the world, on air or a replaceable block, clear of entities and spawn protection. */
	private static Plan check(Level level, Player player, Map<BlockPos, BlockState> cells, boolean extend, @Nullable Component problem) {
		BoundingBox box = BoundingBox.encapsulatingPositions(cells.keySet()).orElseThrow();
		boolean crowded = !level.getEntities((Entity) null, AABB.of(box)).isEmpty();
		List<BlockPos> blocked = new ArrayList<>();
		cells.forEach((pos, state) -> {
			if (!state.isAir() && (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
					|| !level.getBlockState(pos).canBeReplaced() || crowded && !level.isUnobstructed(state, pos, CollisionContext.empty()))) blocked.add(pos);
		});
		if (problem == null && !blocked.isEmpty()) problem = Component.translatable("surfcraft.karambit.blocked", blocked.size());
		return new Plan(cells, box, blocked, problem, extend);
	}

	/**
	 * The ramp blocks connected to {@code start} through any of their 26 neighbours whose facings lie on start's facing
	 * axis (so a two-sided ramp is one), keeping their bounding box within {@code span} blocks per axis; null if more
	 * than {@link #MAX_RAMP_BLOCKS}.
	 */
	static @Nullable Set<BlockPos> connectedRamp(BlockGetter level, BlockPos start, int span) {
		Direction.Axis axis = level.getBlockState(start).getValue(SurfRampBlock.FACING).getAxis();
		Set<BlockPos> found = new HashSet<>(List.of(start));
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(found);
		BoundingBox box = new BoundingBox(start);
		while (!queue.isEmpty()) {
			BlockPos at = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				BlockState state = level.getBlockState(n);
				if (!SurfRampBlock.isRamp(state) || state.getValue(SurfRampBlock.FACING).getAxis() != axis || found.contains(n)) continue;
				BoundingBox grown = BoundingBox.encapsulating(box, new BoundingBox(n));
				if (Math.max(grown.getXSpan(), Math.max(grown.getYSpan(), grown.getZSpan())) > span) continue;
				BlockPos pos = n.immutable();
				found.add(pos);
				box = grown;
				queue.add(pos);
				if (found.size() > MAX_RAMP_BLOCKS) return null;
			}
		}
		return found;
	}

	/**
	 * Copies the ramp at {@code clicked} ({@link #connectedRamp}, up to 32 blocks per axis) and everything else in its
	 * bounding box onto the knife, turned into the module frame: z along the ramp, forward the way the player looks
	 * along it.
	 */
	public static Result copy(Level level, Player player, ItemStack knife, BlockPos clicked) {
		Set<BlockPos> ramp = connectedRamp(level, clicked, SurfModule.MAX_SIZE);
		if (ramp == null) return Result.fail("surfcraft.karambit.too_large");
		Direction.Axis along = level.getBlockState(clicked).getValue(SurfRampBlock.FACING).getAxis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
		Rotation turn = turn(looking(player, along), Direction.SOUTH);
		BoundingBox box = BoundingBox.encapsulatingPositions(ramp).orElseThrow();
		BlockPos min = new BlockPos(box.minX(), box.minY(), box.minZ());
		BlockPos far = BlockPos.ZERO.offset(box.getLength()).rotate(turn);
		BlockPos shift = new BlockPos(-Math.min(0, far.getX()), 0, -Math.min(0, far.getZ()));
		int width = Math.abs(far.getX()) + 1, height = box.getYSpan(), length = Math.abs(far.getZ()) + 1;
		BlockState[] cells = new BlockState[width * height * length];
		for (BlockPos p : BlockPos.betweenClosed(min, new BlockPos(box.maxX(), box.maxY(), box.maxZ()))) {
			BlockPos c = p.subtract(min).rotate(turn).offset(shift);
			cells[(c.getY() * width + c.getX()) * length + c.getZ()] = carried(level.getBlockState(p)).rotate(turn);
		}
		SurfModule module = SurfModule.of(width, height, length, cells);
		if (SurfModule.validate(module).isError()) return Result.fail("surfcraft.karambit.too_complex");
		knife.set(KarambitItem.MODULE, module);
		return new Result(true, Component.translatable("surfcraft.karambit.copied", width, height, length, module.blocks()), List.of());
	}

	/** What a copy keeps of a block: nothing with a block entity or without an item (fluids, fire), and no water. */
	private static BlockState carried(BlockState state) {
		if (state.hasBlockEntity() || state.getBlock().asItem() == Items.AIR) return AIR;
		return state.hasProperty(BlockStateProperties.WATERLOGGED) ? state.setValue(BlockStateProperties.WATERLOGGED, false) : state;
	}

	/**
	 * Places the planned module, all or nothing: it must fit, and outside creative the player must have every block's
	 * item, which it takes. Ramp cells keep their exact cuts, so modules join into one slope.
	 */
	public static Result place(ServerLevel level, Player player, ItemStack knife, BlockPos clicked, Direction face) {
		Plan plan = plan(level, player, knife, clicked, face);
		if (!plan.ok()) return new Result(false, plan.problem(), plan.blocked());
		boolean paid = !player.hasInfiniteMaterials();
		if (paid) {
			Map<Item, Integer> needed = new LinkedHashMap<>();
			plan.cells().values().forEach(s -> {
				if (s.getBlock().asItem() != Items.AIR) needed.merge(s.getBlock().asItem(), 1, Integer::sum);
			});
			Inventory inventory = player.getInventory();
			List<Component> missing = new ArrayList<>();
			needed.forEach((item, n) -> {
				int have = inventory.clearOrCountMatchingItems(s -> s.is(item), true, n, player.inventoryMenu.getCraftSlots());
				if (have < n) missing.add(Component.literal((n - have) + " ").append(item.getDefaultInstance().getHoverName()));
			});
			if (!missing.isEmpty()) return Result.fail("surfcraft.karambit.missing", ComponentUtils.formatList(missing, Component.literal(", ")));
			needed.forEach((item, n) -> inventory.clearOrCountMatchingItems(s -> s.is(item), false, n, player.inventoryMenu.getCraftSlots()));
		}
		List<Change> changes = new ArrayList<>();
		plan.cells().forEach((pos, state) -> {
			if (state.isAir()) return;
			changes.add(new Change(pos, level.getBlockState(pos), state));
			level.setBlock(pos, state, Block.UPDATE_ALL);
		});
		Deque<Placement> mine = HISTORY.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
		mine.push(new Placement(level.dimension(), changes, paid));
		if (mine.size() > UNDO_DEPTH) mine.removeLast();
		return new Result(true, Component.translatable(plan.extend() ? "surfcraft.karambit.extended" : "surfcraft.karambit.placed", changes.size()), List.of());
	}

	/**
	 * Undoes the player's last placement: cells still as placed get their old state back (and, if they were paid for,
	 * their item); cells changed since are left alone.
	 */
	public static Result undo(ServerLevel level, Player player) {
		Deque<Placement> mine = HISTORY.get(player.getUUID());
		Placement last = mine == null ? null : mine.poll();
		if (last == null) return Result.fail("surfcraft.karambit.nothing_to_undo");
		ServerLevel at = level.getServer().getLevel(last.dimension());
		Map<Item, Integer> refund = new LinkedHashMap<>();
		int restored = 0;
		for (Change c : last.changes().reversed()) {
			if (at == null || at.getBlockState(c.pos()) != c.after()) continue;
			at.setBlock(c.pos(), c.before(), Block.UPDATE_ALL);
			restored++;
			if (last.paid() && c.after().getBlock().asItem() != Items.AIR) refund.merge(c.after().getBlock().asItem(), 1, Integer::sum);
		}
		refund.forEach((item, n) -> {
			for (int left = n, stack = item.getDefaultMaxStackSize(); left > 0; left -= stack) {
				player.getInventory().placeItemBackInInventory(new ItemStack(item, Math.min(left, stack)), Prediction.SERVER_ONLY);
			}
		});
		return new Result(true, Component.translatable("surfcraft.karambit.undone", restored), List.of());
	}

	/** The way the player looks along a horizontal axis (by yaw, as {@link Player#getDirection} does). */
	static Direction looking(Player player, Direction.Axis axis) {
		double along = Vec3.directionFromRotation(0.0F, player.getYRot()).get(axis);
		return Direction.fromAxisAndDirection(axis, along >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
	}

	/** The rotation about y that turns {@code from} into {@code to}. */
	static Rotation turn(Direction from, Direction to) {
		for (Rotation r : Rotation.values()) if (r.rotate(from) == to) return r;
		throw new IllegalArgumentException(from + " -> " + to);
	}
}
