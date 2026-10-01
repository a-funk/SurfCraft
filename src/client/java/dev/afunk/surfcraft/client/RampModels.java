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
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
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
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Exact ramp geometry through Fabric's model loading and renderer APIs: every block state bakes a mesh of its cell
 * (a block state resolver, so there are no blockstate files), and item models of type {@code surfcraft:ramp} render
 * the standalone cell. Textures are world-aligned (UV lock), so they run on across neighbouring cells.
 */
public final class RampModels {
	private RampModels() {
	}

	public static void register() {
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
		// Vertices are multiples of 1/p or 1/q: snap them so neighbouring cells emit bit-identical shared edges.
		double grid = cell.p() * cell.q();
		List<double[]> poly = new ArrayList<>();
		for (double[] v : cell.polygon()) poly.add(new double[] {Math.round(v[0] * grid) / grid, Math.round(v[1] * grid) / grid});
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
		for (int w = 0; w <= 1; w++) {
			Direction side = w == 1 ? facing.getClockWise() : facing.getCounterClockWise();
			for (int k = 1; k < n - 1; k += 2) {
				int[] fan = {0, k, k + 1, Math.min(k + 2, n - 1)};
				double[][] quad = new double[4][];
				for (int j = 0; j < 4; j++) {
					double[] v = poly.get(w == 1 ? fan[j] : (n - fan[j]) % n);
					quad[j] = new double[] {v[0], v[1], w};
				}
				emit(emitter, material, facing, quad, 0, 0, 2 * w - 1, side, side);
			}
		}
		return mesh.immutableCopy();
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
