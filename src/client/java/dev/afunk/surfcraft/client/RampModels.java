package dev.afunk.surfcraft.client;

import dev.afunk.surfcraft.SurfCraft;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.physics.RampCell;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.model.MeshQuadCollection;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Exact ramp geometry through Fabric's model loading and renderer APIs: every block state bakes a mesh of its cell
 * (a block state resolver, so there are no blockstate files), and item models of type {@code surfcraft:ramp} render
 * the standalone cell. Textures are world-aligned (UV lock), so they run on across neighbouring cells. The selection
 * outline is the cell's exact edges too.
 */
public final class RampModels {
	/** The ramp state an extracted block outline is for (a block state is immutable, so safe to pass to the draw). */
	private static final RenderStateDataKey<BlockState> OUTLINED = RenderStateDataKey.create(() -> "surfcraft ramp outline");

	private RampModels() {
	}

	public static void register() {
		LevelExtractionEvents.AFTER_BLOCK_OUTLINE_EXTRACTION.register((context, hit) -> {
			BlockOutlineRenderState outline = context.levelState().blockOutlineRenderState;
			BlockState state = outline == null ? null : context.level().getBlockState(outline.pos());
			if (state != null && SurfRampBlock.isRamp(state)) outline.setData(OUTLINED, state);
		});
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register(RampModels::outline);
		ModelLoadingPlugin.register(plugin -> {
			for (SurfRampBlock block : SurfBlocks.RAMPS) {
				plugin.registerBlockStateResolver(block, resolver -> {
					for (BlockState state : block.getStateDefinition().getPossibleStates()) {
						resolver.setModel(state, new BlockModel(texture(block), SurfRampBlock.cell(state), state.getValue(SurfRampBlock.FACING)));
					}
				});
			}
		});
		// {"fabric:type": "surfcraft:ramp", "parent": ..., "block": ...}: the block's standalone cell (cut = p).
		UnbakedModelDeserializer.register(SurfCraft.id("ramp"), (json, context) -> {
			SurfRampBlock block = (SurfRampBlock) BuiltInRegistries.BLOCK.getValue(Identifier.parse(GsonHelper.getAsString(json, "block")));
			return new ItemModel(texture(block), new RampCell(block.p, block.q, block.p), Identifier.parse(GsonHelper.getAsString(json, "parent")));
		});
	}

	private static Material texture(SurfRampBlock block) {
		return new Material(BuiltInRegistries.BLOCK.getKey(block).withPrefix("block/"));
	}

	private record BlockModel(Material texture, RampCell cell, Direction facing) implements BlockStateModel.UnbakedRoot, ModelDebugName {
		@Override
		public void resolveDependencies(Resolver resolver) {
		}

		@Override
		public BlockStateModel bake(BlockState state, ModelBaker baker) {
			Material.Baked material = baker.materials().get(texture, this);
			return new SingleVariant(new SimpleModelWrapper(new MeshQuadCollection(mesh(cell, facing, material)), true, material));
		}

		@Override
		public Object visualEqualityGroup(BlockState state) {
			return this;
		}

		@Override
		public String debugName() {
			return "surfcraft ramp " + cell + " facing " + facing;
		}
	}

	/** Faces west, so the stairs display transforms (parent {@code block/stairs}) show it the way they show stairs. */
	private record ItemModel(Material texture, RampCell cell, Identifier parent) implements UnbakedModel {
		@Override
		public UnbakedGeometry geometry() {
			return (slots, baker, state, name) -> new MeshQuadCollection(mesh(cell, Direction.WEST, baker.materials().get(texture, name)));
		}

		@Override
		public TextureSlots.Data textureSlots() {
			return new TextureSlots.Data.Builder().addTexture(PARTICLE_TEXTURE_REFERENCE, texture).build();
		}
	}

	/**
	 * The cell's exact solid in block-local coordinates: per edge of its counter-clockwise (u, y) cross-section an
	 * axial face on the cell boundary (with that cull face) or the slope (no cull face), plus the cross-section itself
	 * at w = 0 and w = 1.
	 */
	static Mesh mesh(RampCell cell, Direction facing, Material.Baked material) {
		MutableMesh mesh = Renderer.get().mutableMesh();
		QuadEmitter emitter = mesh.emitter();
		List<double[]> poly = polygon(cell);
		int n = poly.size();
		for (int i = 0; i < n; i++) {
			double[] a = poly.get(i), b = poly.get((i + 1) % n);
			Direction side = a[0] == 0 && b[0] == 0 ? facing.getOpposite()
					: a[0] == 1 && b[0] == 1 ? facing
					: a[1] == 0 && b[1] == 0 ? Direction.DOWN
					: a[1] == 1 && b[1] == 1 ? Direction.UP
					: null;
			// The outward normal of a counter-clockwise polygon's edge points to its right.
			double du = b[0] - a[0], dy = b[1] - a[1], len = Math.hypot(du, dy);
			double[][] quad = {{a[0], a[1], 0}, {b[0], b[1], 0}, {b[0], b[1], 1}, {a[0], a[1], 1}};
			// The slope takes its texture projected onto the vertical plane across the facing (UV lock on the facing).
			emit(emitter, material, facing, quad, dy / len, -du / len, 0, side == null ? facing : side, side);
		}
		// The cross-section faces +w at w = 1 and, reversed, -w at w = 0; fanned into quads (a triangle repeats a vertex).
		// A slope cell's has no cull face: its occlusion shape can't match a slanted outline (SurfRampBlock.faces).
		for (int w = 0; w <= 1; w++) {
			Direction side = w == 1 ? facing.getClockWise() : facing.getCounterClockWise();
			for (int k = 1; k < n - 1; k += 2) {
				int[] fan = {0, k, k + 1, Math.min(k + 2, n - 1)};
				double[][] quad = new double[4][];
				for (int j = 0; j < 4; j++) {
					double[] v = poly.get(w == 1 ? fan[j] : (n - fan[j]) % n);
					quad[j] = new double[] {v[0], v[1], w};
				}
				emit(emitter, material, facing, quad, 0, 0, 2 * w - 1, side, cell.full() ? side : null);
			}
		}
		return mesh.immutableCopy();
	}

	/** The cell's cross-section, vertices snapped to the 1/(p*q) grid they lie on, so neighbouring cells share edges bit for bit. */
	private static List<double[]> polygon(RampCell cell) {
		double grid = cell.p() * cell.q();
		List<double[]> poly = new ArrayList<>();
		for (double[] v : cell.polygon()) poly.add(new double[] {Math.round(v[0] * grid) / grid, Math.round(v[1] * grid) / grid});
		return poly;
	}

	/** In place of vanilla's outline (the block's shape, the staircase collision uses): the cell's edges, drawn as vanilla draws outlines. */
	private static boolean outline(LevelRenderContext context, BlockOutlineRenderState outline) {
		BlockState state = outline.getData(OUTLINED);
		if (state == null) return true;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		GameRenderer renderer = context.gameRenderer();
		context.poseStack().pushPose();
		context.poseStack().translate(outline.pos().getX() - camera.x, outline.pos().getY() - camera.y, outline.pos().getZ() - camera.z);
		if (outline.highContrast()) edges(context, RenderTypes.secondaryBlockOutline(), state, 0xFF000000, 7.0F);
		edges(context, outline.highContrast() ? RenderTypes.linesDepthBias() : renderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent(),
				state, outline.highContrast() ? 0xFF57FFE1 : ARGB.black(102), renderer.gameRenderState().windowRenderState.appropriateLineWidth);
		context.poseStack().popPose();
		return false;
	}

	/** The prism's edges: the cross-section's at both ends of the cell along the ramp, and one along it from each vertex. */
	private static void edges(LevelRenderContext context, RenderType type, BlockState state, int color, float width) {
		Direction facing = state.getValue(SurfRampBlock.FACING);
		List<double[]> poly = polygon(SurfRampBlock.cell(state));
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), type, (pose, buffer) -> {
			Vector3f normal = new Vector3f();
			for (int i = 0; i < poly.size(); i++) {
				double[] a = poly.get(i), b = poly.get((i + 1) % poly.size());
				for (double[] e : new double[][] {{a[0], a[1], 0, b[0], b[1], 0}, {a[0], a[1], 1, b[0], b[1], 1}, {a[0], a[1], 0, a[0], a[1], 1}}) {
					Vector3f from = local(facing, e[0], e[1], e[2]), to = local(facing, e[3], e[4], e[5]);
					to.sub(from, normal).normalize();
					buffer.addVertex(pose, from).setColor(color).setNormal(pose, normal).setLineWidth(width);
					buffer.addVertex(pose, to).setColor(color).setNormal(pose, normal).setLineWidth(width);
				}
			}
		});
	}

	private static void emit(QuadEmitter emitter, Material.Baked material, Direction facing, double[][] uyw, double nu, double ny, double nw, Direction nominal, @Nullable Direction cull) {
		Vector3f normal = local(facing, nu, ny, nw).sub(local(facing, 0, 0, 0));
		for (int i = 0; i < 4; i++) emitter.pos(i, local(facing, uyw[i][0], uyw[i][1], uyw[i][2])).normal(i, normal);
		emitter.cullFace(cull);
		emitter.nominalFace(nominal);
		emitter.materialBake(material, MutableQuadView.BAKE_LOCK_UV);
		emitter.emit();
	}

	/** (u along the facing, y, w) to block-local (x, y, z): the east-facing frame rotated about y, so windings keep. */
	private static Vector3f local(Direction facing, double u, double y, double w) {
		return switch (facing) {
			case EAST -> new Vector3f((float) u, (float) y, (float) w);
			case WEST -> new Vector3f((float) (1 - u), (float) y, (float) (1 - w));
			case SOUTH -> new Vector3f((float) (1 - w), (float) y, (float) u);
			default -> new Vector3f((float) w, (float) y, (float) (1 - u));
		};
	}
}
