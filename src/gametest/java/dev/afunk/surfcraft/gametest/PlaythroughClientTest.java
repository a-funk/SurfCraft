package dev.afunk.surfcraft.gametest;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.karambit.KarambitItem;
import dev.afunk.surfcraft.karambit.SurfModule;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * SurfCraft end to end, the way a player meets it, in a survival superflat world (noon, clear, fall damage on). The
 * Karambit is crafted by mouse clicks: a crafting table from planks in the inventory's grid, then 2 iron ingots and a
 * stick in the table's grid (only the raw materials come from /give, as do the blocks a survival player would bring).
 * The course is built by look + right-click: a pillar of smooth stone (pillar-jumped), ramp 1 = the knife's default
 * two-sided 51° module placed on the pillar's side 4 blocks up and extended by right-clicking it to 96 blocks, a start pad
 * against its north end, a 5-block gap, ramp 2 = a free placement of the module on the ground (lower, 2 blocks east), and
 * a 3x12 finish pad with a gold block at its end. Then it is surfed twice, start to finish, by keyboard and mouse only
 * ({@link Pilot}): first person, then third person, a 1280x720 frame every tick into
 * build/gametest/screenshots/playthrough-&lt;run&gt;/. Each run must finish in time at full health (never hurt) with the
 * controller driving throughout, over 600 u/s, and with no server correction, "moved wrongly" or "moved too quickly".
 * The geometry and the pilot were tuned offline on the physics core (TickDriver, RampBrushes, the same code the client
 * runs) for margin: 180 runs from start spots up to 0.8 blocks apart, with the pilot's gains up to 17% off, all finished
 * without touching a wall, at 651-705 u/s (this run, from the middle spot: about 680).
 */
public class PlaythroughClientTest implements FabricClientGameTest {
	/** The course origin (x, z); y 0 is the first air block above the grass. Course coordinates are relative to it. */
	static final int OX = 10, OZ = 10;
	/** Ramp 1 (ridge at x 4, base y 4, z 0..95), the gap, ramp 2 (ridge at x 6, base on the ground), the finish pad (x 7..9). */
	static final int RAMP1 = 96, RAMP1_Y = 4, GAP = 5, RAMP2_Z = RAMP1 + GAP, FINISH_Z = RAMP2_Z + 8, GOLD_Z = FINISH_Z + 11;
	/** Ticks a run may take from its first key press to the finish (20 s). */
	static final int LIMIT = 400;
	/** Frames before the start (standing) and after the finish. */
	static final int LEAD = 20, TAIL = 30;
	/** SDL mouse buttons. */
	static final int LEFT = 1, RIGHT = 3;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ClientTests.enabled("playthrough")) return;
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> {
			s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
			s.getGameRules().set(GameRules.FALL_DAMAGE, true, null);
		}).create(); LogWatch log = new LogWatch()) {
			TestServerContext server = world.getServer();
			server.runCommand("time set noon");
			server.runCommand("weather clear");
			int g = server.computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, OX, OZ));
			context.runOnClient(c -> c.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			Playthrough p = new Playthrough(context, server, new Surfer(context, server, world.getConnection()), new BlockPos(OX, g, OZ));
			p.craftKarambit();
			p.buildCourse();
			List<String> failures = new ArrayList<>();
			for (Run run : List.of(p.surf(1, CameraType.FIRST_PERSON, log), p.surf(2, CameraType.THIRD_PERSON_BACK, log))) run.check(failures);
			context.runOnClient(c -> c.options.setCameraType(CameraType.FIRST_PERSON));
			p.surfer.failures.forEach(f -> failures.add("server off the client's positions: " + f));
			if (!failures.isEmpty()) throw new AssertionError("playthrough:\n  " + String.join("\n  ", failures));
		}
	}

	/** The world, the player's hands (keys, mouse, clicks) and what the server sees. */
	static final class Playthrough {
		final ClientGameTestContext context;
		final TestInput input;
		final TestServerContext server;
		final Surfer surfer;
		final BlockPos o;

		Playthrough(ClientGameTestContext context, TestServerContext server, Surfer surfer, BlockPos o) {
			this.context = context;
			this.input = context.getInput();
			this.server = server;
			this.surfer = surfer;
			this.o = o;
		}

		BlockPos at(int x, int y, int z) {
			return o.offset(x, y, z);
		}

		Vec3 point(double x, double y, double z) {
			return new Vec3(o.getX() + x, o.getY() + y, o.getZ() + z);
		}

		/**
		 * /give, then until the client has the items: a give right after a container closes can reach the client under the
		 * closed container's id (the server hadn't handled the close yet), and the client drops it until the next sync.
		 */
		void give(Item item, int count) {
			int before = context.computeOnClient(c -> c.player.getInventory().countItem(item));
			server.runCommand("give @a " + BuiltInRegistries.ITEM.getKey(item) + " " + count);
			context.waitFor(c -> c.player.getInventory().countItem(item) >= before + count, 100);
		}

		/** Crafts the Karambit through the UI: a crafting table in the inventory's grid, then the knife in the table. */
		void craftKarambit() {
			give(Items.OAK_PLANKS, 4);
			give(Items.IRON_INGOT, 2);
			give(Items.STICK, 1);
			input.pressKey(k -> k.keyInventory);
			context.waitFor(c -> c.gui.screen() instanceof InventoryScreen, 40);
			int planks = slotOf(Items.OAK_PLANKS);
			clickSlot(planks, LEFT);
			for (int i = 1; i <= 4; i++) clickSlot(i, RIGHT);
			clickSlot(0, LEFT);
			clickSlot(planks, LEFT);
			input.pressKey(k -> k.keyInventory);
			context.waitFor(c -> c.gui.screen() == null, 40);

			// Put the table down beside the course and open it.
			place(Items.CRAFTING_TABLE, point(-3.5, 0, -7.5), point(-3.5, 0, -5.5), at(-4, 0, -6));
			look(point(-3.5, 1, -5.5));
			click();
			context.waitFor(c -> c.gui.screen() instanceof CraftingScreen, 40);
			// The pattern "  I / I  / S  ": iron at grid row 0 column 2 and row 1 column 1 (slots 3, 5), the stick at row 2
			// column 0 (slot 7); the result (slot 0) goes where the iron was.
			int iron = slotOf(Items.IRON_INGOT), stick = slotOf(Items.STICK);
			clickSlot(iron, LEFT);
			clickSlot(3, RIGHT);
			clickSlot(5, RIGHT);
			clickSlot(stick, LEFT);
			clickSlot(7, LEFT);
			clickSlot(0, LEFT);
			clickSlot(iron, LEFT);
			input.pressKey(k -> k.keyInventory);
			context.waitFor(c -> c.gui.screen() == null, 40);
			int knives = server.computeOnServer(s -> player(s).getInventory().countItem(KarambitItem.KARAMBIT));
			if (knives != 1) throw new AssertionError("crafted " + knives + " Karambits");
			System.out.println("SURFCRAFT playthrough: crafted the Karambit in a crafting table by mouse clicks");
		}

		/** Builds the course by look + right-click, checking each step on the server. */
		void buildCourse() {
			give(Items.SMOOTH_STONE, 64);
			give(Items.GOLD_BLOCK, 1);
			// The pillar ramp 1 hangs from: pillar-jumped, a block under the feet at the top of each jump.
			hold(Items.SMOOTH_STONE);
			walkTo(point(3.5, 0, -0.5));
			input.lookAt(0, 90);
			context.waitTicks(2);
			for (int k = 0; k <= RAMP1_Y; k++) {
				double top = o.getY() + k;
				input.holdKey(opts -> opts.keyJump);
				context.waitTick();
				input.releaseKey(opts -> opts.keyJump);
				context.waitFor(c -> c.player.getY() > top + 1.15, 20);
				click();
				context.waitFor(c -> c.player.onGround(), 20);
				expect(at(3, k, -1), Blocks.SMOOTH_STONE);
			}

			// Ramp 1: the knife on the pillar's top block, its south face (from the ground, looking north): the module hangs
			// there, 4 up, centred on the pillar. Then right-click its underside looking south, near the end, to extend it.
			hold(KarambitItem.KARAMBIT);
			module(point(3.5, 0, 1.0), point(3.5, RAMP1_Y + 0.5, 0), at(0, RAMP1_Y, 0));
			for (int end = 8; end < RAMP1; end += 8) {
				module(point(9.0, 0, end - 3.0), point(7.5, RAMP1_Y, end - 0.5), null);
				int length = server.computeOnServer(s -> rampLength(s.overworld(), at(0, RAMP1_Y, 0)));
				if (length != end + 8) throw new AssertionError("ramp 1 is " + length + " long after extending to " + (end + 8));
			}

			// The start pad (top 6 up, as high as a block can be placed from the ground), over the east slope: against ramp
			// 1's north end, then against each other, each clicked from below.
			hold(Items.SMOOTH_STONE);
			place(point(6.5, 0, -1.0), point(6.5, 5.25, 0), at(6, 5, -1));
			place(point(8.0, 0, -0.5), point(7.0, 5.25, -0.5), at(7, 5, -1));
			place(point(9.0, 0, -0.5), point(8.0, 5.25, -0.5), at(8, 5, -1));
			for (int x = 6; x <= 8; x++) place(point(x + 0.5, 0, -2.0), point(x + 0.5, 5.25, -1.0), at(x, 5, -2));

			// Ramp 2: a free placement on the grass, 5 blocks past ramp 1, looking south.
			hold(KarambitItem.KARAMBIT);
			module(point(5.5, 0, RAMP2_Z - 3.0), point(5.5, 0, RAMP2_Z + 0.5), at(2, 0, RAMP2_Z));

			// The finish pad from its far end back, each block on the grass, the gold block in the middle of the last row.
			for (int z = GOLD_Z; z >= FINISH_Z; z--) {
				for (int x = 7; x <= 9; x++) {
					Vec3 stand = z >= FINISH_Z + 2 ? point(x + 0.5, 0, z - 1.5) : point(11.0, 0, z + 0.5);
					place(z == GOLD_Z && x == 8 ? Items.GOLD_BLOCK : Items.SMOOTH_STONE, stand, point(x + 0.5, 0, z + 0.5), at(x, 0, z));
				}
			}
			long ramps = server.computeOnServer(s -> count(s.overworld(), at(-1, 0, -3), at(11, 8, GOLD_Z + 1), SurfRampBlock::isRamp));
			System.out.printf("SURFCRAFT playthrough course: ramp 1 %d long (default module, base %d up, ridge %d up), gap %d, ramp 2 (on the ground, ridge 2 east), "
					+ "finish pad 3x12 with gold at its end; %d ramp blocks, all from the inventory%n", RAMP1, RAMP1_Y, RAMP1_Y + 5, GAP, ramps);
		}

		/**
		 * One Karambit click with 224 Surf Ramps in the inventory: all of them must be used (survival), and a placement
		 * (not an extension) must put the whole default module with its min corner at {@code min}.
		 */
		void module(Vec3 stand, Vec3 target, BlockPos min) {
			give(SurfBlocks.SURF_RAMP.asItem(), SurfModule.DEFAULT.blocks());
			walkTo(stand);
			look(target);
			click();
			int left = server.computeOnServer(s -> player(s).getInventory().countItem(SurfBlocks.SURF_RAMP.asItem()));
			if (left != 0) throw new AssertionError("a module click left " + left + " Surf Ramps in the inventory (looking at " + hit() + ")");
			if (min == null) return;
			long placed = server.computeOnServer(s -> count(s.overworld(), min, min.offset(7, 4, 7), SurfRampBlock::isRamp));
			if (placed != SurfModule.DEFAULT.blocks()) throw new AssertionError("the module at " + min + " has " + placed + " ramp blocks");
		}

		/** Places the held {@code item}'s block by standing at {@code stand}, looking at {@code target} and right-clicking. */
		void place(Item item, Vec3 stand, Vec3 target, BlockPos expect) {
			hold(item);
			place(stand, target, expect);
		}

		void place(Vec3 stand, Vec3 target, BlockPos expect) {
			Block block = context.computeOnClient(c -> Block.byItem(c.player.getMainHandItem().getItem()));
			walkTo(stand);
			look(target);
			click();
			expect(expect, block);
		}

		void expect(BlockPos pos, Block block) {
			String found = server.computeOnServer(s -> s.overworld().getBlockState(pos).is(block) ? null : s.overworld().getBlockState(pos).toString());
			if (found != null) throw new AssertionError("expected " + block + " at " + pos + ", found " + found + " (looking at " + hit() + ")");
		}

		String hit() {
			return context.computeOnClient(c -> c.hitResult == null ? "nothing" : c.hitResult.getType() + " " + c.hitResult.getLocation());
		}

		/** Feet to {@code feet} (standing on something there), keeping the view. */
		void walkTo(Vec3 feet) {
			server.runCommand("tp @a %s %s %s".formatted(feet.x, feet.y, feet.z));
			surfer.connection.waitForClientboundPackets();
			context.waitTicks(2);
		}

		/** Turns the view (TestInput.lookAt) so the eye looks at {@code target}, and lets the server hear of it. */
		void look(Vec3 target) {
			float[] view = context.computeOnClient(c -> {
				Vec3 d = target.subtract(c.player.getEyePosition());
				return new float[] {(float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance()))};
			});
			input.lookAt(view[0], view[1]);
			context.waitTicks(2);
		}

		void click() {
			input.pressKey(k -> k.keyUse);
			context.waitTicks(2);
		}

		/** Selects the hotbar slot holding {@code item} with its number key ({@code AIR}: the first empty one). */
		void hold(Item item) {
			int slot = context.computeOnClient(c -> {
				for (int i = 0; i < 9; i++) if (c.player.getInventory().getItem(i).is(item)) return i;
				return -1;
			});
			if (slot < 0) throw new AssertionError("no " + item + " in the hotbar");
			input.pressKey(k -> k.keyHotbarSlots[slot]);
		}

		/** The open container screen's slot holding {@code item} in the player's inventory. */
		int slotOf(Item item) {
			return context.computeOnClient(c -> {
				for (Slot s : ((AbstractContainerScreen<?>) c.gui.screen()).getMenu().slots) if (s.container == c.player.getInventory() && s.getItem().is(item)) return s.index;
				throw new AssertionError("no " + item + " in the inventory");
			});
		}

		/** Moves the cursor onto a slot of the open container screen and clicks it. */
		void clickSlot(int index, int button) {
			double[] at = context.computeOnClient(c -> {
				AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) c.gui.screen();
				Slot slot = screen.getMenu().slots.get(index);
				double scale = (double) c.getWindow().getScreenWidth() / c.getWindow().getGuiScaledWidth();
				return new double[] {(field(screen, "leftPos") + slot.x + 8) * scale, (field(screen, "topPos") + slot.y + 8) * scale};
			});
			input.setCursorPos(at[0], at[1]);
			input.pressMouse(button);
			context.waitTicks(2);
		}

		/** Frames being written in the background, and write failures. */
		final AtomicInteger saving = new AtomicInteger();
		final List<String> frameErrors = new CopyOnWriteArrayList<>();

		/**
		 * One frame of the state after the last tick, rendered as {@code takeScreenshot} renders it (the HUD at full tick),
		 * read back and written in the background, then exactly one tick: {@code takeScreenshot} waits for the read-back,
		 * which sometimes takes two ticks and drops a tick from the clip.
		 */
		void frame(Path file) {
			context.runOnClient(c -> {
				c.gameRenderer.update(DeltaTracker.ONE);
				c.gameRenderer.extract(DeltaTracker.ONE, true);
				c.gameRenderer.render();
				RenderSystem.getDevice().createCommandEncoder().submit();
				saving.incrementAndGet();
				Screenshot.takeScreenshot(c.gameRenderer.mainRenderTarget(), image -> Util.ioPool().execute(() -> {
					try (image) {
						image.writeToFile(file);
					} catch (IOException e) {
						frameErrors.add(file + ": " + e);
					} finally {
						saving.decrementAndGet();
					}
				}));
			});
			context.waitTick();
		}

		/**
		 * Surfs the course once from the start pad: {@link #LEAD} frames standing, then the {@link Pilot} at the keys and the
		 * mouse until the finish (or {@link #LIMIT}), then {@link #TAIL} frames; a frame every tick.
		 */
		Run surf(int n, CameraType camera, LogWatch log) {
			Path dir = ClientTests.OUT.resolve("playthrough-" + n);
			clean(dir);
			// The whole course in view. The server sends each player chunks out to the distance its client asked for, which
			// the client only says when its options are broadcast (closing the options screen does it).
			context.runOnClient(c -> {
				c.options.renderDistance().set(12);
				c.options.broadcastOptions();
			});
			hold(Items.AIR); // surf empty-handed
			surfer.tp(o.getX() + 7.2, o.getY() + 6, o.getZ() - 1.3, 0, 12);
			context.waitFor(c -> {
				for (int x = (o.getX() - 16) >> 4; x <= (o.getX() + 32) >> 4; x++) for (int z = (o.getZ() - 16) >> 4; z <= (o.getZ() + GOLD_Z + 16) >> 4; z++) {
					if (!c.level.getChunkSource().hasChunk(x, z)) return false;
				}
				return true;
			}, 600);
			context.waitTicks(60);
			context.runOnClient(c -> {
				c.options.setCameraType(camera);
				c.gui.toastManager().clear();
			});
			context.waitTick();
			surfer.resetCounters();
			// The first mouse move after the mouse is grabbed is dropped: spend it on nothing.
			input.moveCursor(0, 0);
			double degreesPerPixel = context.computeOnClient(c -> {
				double s = c.options.sensitivity().get() * 0.6 + 0.2;
				return s * s * s * 8 * 0.15;
			});
			Pilot pilot = new Pilot(o, degreesPerPixel);
			Keys keys = new Keys(input);
			int corrections = ServerCorrections.EVENTS.size(), rejections = log.rejections.size(), untracked = surfer.untracked;
			Surfer.Sample s = surfer.sample();
			float maxHealth = context.computeOnClient(c -> c.player.getMaxHealth());
			Run run = new Run(n, camera);
			run.health = run.lowest = s.health();
			StringBuilder csv = new StringBuilder("frame,tick,phase,x,y,z,speed,w,a,d,jump,mouse,driving,grounded,surfed,health\n");
			long start = -1, finish = -1, first = gameTime();
			int frame = 0, finishFrame = -1;
			while (true) {
				boolean racing = frame >= LEAD && finish < 0;
				if (racing) {
					if (start < 0) start = gameTime();
					pilot.steer(s);
					keys.set(pilot.w, pilot.a, pilot.d, pilot.jump);
					if (pilot.mouse != 0) input.moveCursor(pilot.mouse, 0);
				}
				frame(dir.resolve("%04d.png".formatted(frame)));
				frame++;
				Surfer.Sample next = surfer.observe();
				long now = gameTime();
				if (finish < 0) {
					run.drove &= next.driving();
					if (racing) run.path += next.pos().distanceTo(s.pos());
					run.top = Math.max(run.top, next.speed());
					run.lowest = Math.min(run.lowest, next.health());
					run.health = next.health();
				}
				csv.append("%d,%d,%d,%.4f,%.4f,%.4f,%.1f,%b,%b,%b,%b,%.2f,%b,%b,%b,%.1f%n".formatted(frame - 1, start < 0 ? -1 : now - start, pilot.phase, next.pos().x - o.getX(),
						next.pos().y - o.getY(), next.pos().z - o.getZ(), next.speed(), keys.w, keys.a, keys.d, keys.jump, racing ? pilot.mouse : 0, next.driving(), next.grounded(),
						next.surfed(), next.health()));
				s = next;
				if (racing && pilot.finished(s.pos())) {
					finish = now;
					finishFrame = frame;
					keys.set(false, false, false, false);
				}
				if (start >= 0 && finish < 0 && now - start > LIMIT) break;
				if (finish >= 0 && frame >= finishFrame + TAIL) break;
			}
			keys.set(false, false, false, false);
			run.recorded = gameTime() - first;
			context.waitFor(c -> saving.get() == 0, 200);
			run.frames = frame;
			run.frameErrors = List.copyOf(frameErrors);
			run.ticks = finish < 0 ? -1 : finish - start;
			run.maxHealth = maxHealth;
			run.corrections = ServerCorrections.EVENTS.size() - corrections;
			run.rejections = log.rejections.size() - rejections;
			run.untracked = surfer.untracked - untracked;
			run.resyncs = s.resyncs();
			try {
				Files.writeString(dir.resolveSibling("playthrough-" + n + ".csv"), csv);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			System.out.println("SURFCRAFT playthrough " + run);
			context.waitTicks(20);
			return run;
		}

		long gameTime() {
			return context.computeOnClient(c -> c.level.getGameTime());
		}
	}

	/**
	 * The surfer at the keys: W off the start pad; on a slope D (into it) with the view along the ramp, turned toward the
	 * ramp to climb when under the line it follows and away to descend (and gain speed) when over it; across the gap an air
	 * strafe, A with the mouse turning left then D turning back right (a little speed, a little drift toward ramp 2); on the
	 * finish pad jump held (auto hop), A or D with the mouse turning the same way toward the gold block. The view moves
	 * only by the mouse, at most {@link #STEP} px (2.4 degrees) a tick.
	 */
	static final class Pilot {
		static final double STEP = 16;
		/** The gap's strafe: ticks each way, and degrees of turn a tick. */
		static final int STRAFE = 3;
		static final double STRAFE_TURN = 2;
		final BlockPos o;
		final double degreesPerPixel;
		int phase, gapTicks;
		float gapYaw;
		boolean w, a, d, jump;
		double mouse;

		Pilot(BlockPos o, double degreesPerPixel) {
			this.o = o;
			this.degreesPerPixel = degreesPerPixel;
		}

		void steer(Surfer.Sample s) {
			double x = s.pos().x - o.getX(), y = s.pos().y - o.getY(), z = s.pos().z - o.getZ();
			double heading = Math.toDegrees(Math.atan2(-s.deltaMovement().x, s.deltaMovement().z));
			if (phase == 0 && z > 0.3 && !s.grounded()) phase = 1;
			if (phase == 1 && z > RAMP1) {
				phase = 2;
				gapYaw = s.yaw();
			}
			if (phase == 2 && z > RAMP2_Z + 0.5) phase = 3;
			if (phase == 3 && z > RAMP2_Z + 8) phase = 4;
			w = phase == 0;
			d = phase == 1 || phase == 3;
			a = false;
			jump = phase == 4;
			double want = switch (phase) {
				case 1 -> slope(y - RAMP1_Y, z / RAMP1, 2.2, 0.8);
				case 2 -> strafe(heading);
				case 3 -> slope(y, (z - RAMP2_Z) / 8, 2.5, 2.0);
				case 4 -> air(heading, x, z, 8.5, GOLD_Z + 0.5);
				default -> 0;
			};
			mouse = Math.clamp(Math.IEEEremainder(want - s.yaw(), 360) / degreesPerPixel, -STEP, STEP);
		}

		/** The view's yaw on a slope (0 is along the ramp): follow a line from {@code from} to {@code to} blocks above its base. */
		static double slope(double height, double along, double from, double to) {
			double line = from + (to - from) * Math.clamp(along, 0, 1);
			return Math.clamp(6 * (line - height) - 4, -8, 8);
		}

		/** The view's yaw across the gap: A turning left for {@link #STRAFE} ticks, then D turning back; then along the flight. */
		double strafe(double heading) {
			int k = gapTicks++;
			a = k < STRAFE;
			d = k >= STRAFE && k < 2 * STRAFE;
			return a ? gapYaw - STRAFE_TURN * (k + 1) : d ? gapYaw - STRAFE_TURN * (2 * STRAFE - k - 1) : heading;
		}

		/** The view's yaw in the air: turn toward (tx, tz), the strafe key the same way; within 1.5 degrees, keys off. */
		double air(double heading, double x, double z, double tx, double tz) {
			double turn = Math.IEEEremainder(Math.toDegrees(Math.atan2(-(tx - x), tz - z)) - heading, 360);
			if (Math.abs(turn) <= 1.5) return heading;
			d = turn > 0;
			a = turn < 0;
			return heading + Math.signum(turn) * Math.min(Math.abs(turn), 3);
		}

		/** Past the gold block, on the finish pad. */
		boolean finished(Vec3 pos) {
			double x = pos.x - o.getX(), y = pos.y - o.getY(), z = pos.z - o.getZ();
			return phase == 4 && z >= GOLD_Z && y > 0.99 && x > 7 && x < 10;
		}
	}

	/** Movement keys held through TestInput, pressed and released only when they change. */
	static final class Keys {
		final TestInput input;
		boolean w, a, d, jump;

		Keys(TestInput input) {
			this.input = input;
		}

		void set(boolean w, boolean a, boolean d, boolean jump) {
			this.w = key(this.w, w, o -> o.keyUp);
			this.a = key(this.a, a, o -> o.keyLeft);
			this.d = key(this.d, d, o -> o.keyRight);
			this.jump = key(this.jump, jump, o -> o.keyJump);
		}

		private boolean key(boolean was, boolean now, java.util.function.Function<Options, KeyMapping> key) {
			if (now && !was) input.holdKey(key);
			if (!now && was) input.releaseKey(key);
			return now;
		}
	}

	/** One run's numbers. */
	static final class Run {
		final int n;
		final CameraType camera;
		long ticks = -1, recorded;
		int frames, corrections, rejections, untracked, resyncs;
		double top, path;
		float health, lowest, maxHealth;
		boolean drove = true;
		List<String> frameErrors = List.of();

		Run(int n, CameraType camera) {
			this.n = n;
			this.camera = camera;
		}

		void check(List<String> failures) {
			String what = "run " + n + " (" + camera + ")";
			if (ticks < 0 || ticks > LIMIT) failures.add(what + ": did not reach the finish within " + LIMIT + " ticks");
			if (health < maxHealth || lowest < maxHealth) failures.add(what + ": health " + health + " at the finish, lowest " + lowest + " of " + maxHealth);
			if (corrections != 0 || resyncs != 0) failures.add(what + ": " + corrections + " server corrections, " + resyncs + " controller resyncs");
			if (rejections != 0) failures.add(what + ": " + rejections + " moved wrongly / too quickly");
			if (untracked != 0) failures.add(what + ": the server was off the client's positions on " + untracked + " ticks");
			if (!drove) failures.add(what + ": the controller did not drive the whole run");
			if (top <= 600) failures.add(what + ": top speed " + Math.round(top) + " u/s");
			if (frames != recorded || !frameErrors.isEmpty()) failures.add(what + ": " + frames + " frames for " + recorded + " ticks " + frameErrors);
		}

		@Override
		public String toString() {
			return "run %d (%s): finish after %d ticks (%.2f s), %.1f blocks, top %.0f u/s, health %.1f/%.1f (lowest %.1f), corrections %d, rejections %d, untracked %d, resyncs %d, controller drove throughout %b, %d frames for %d ticks"
					.formatted(n, camera, ticks, ticks / 20.0, path, top, health, maxHealth, lowest, corrections, rejections, untracked, resyncs, drove, frames, recorded);
		}
	}

	static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	static long count(ServerLevel level, BlockPos min, BlockPos max, java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> what) {
		return BlockPos.betweenClosedStream(min, max).filter(p -> what.test(level.getBlockState(p))).count();
	}

	/** Slices along +z from the module's min corner that hold exactly its first slice (8 wide, 5 tall). */
	static int rampLength(ServerLevel level, BlockPos min) {
		int z = 0;
		while (z < 512 && sameSlice(level, min, z)) z++;
		return z;
	}

	private static boolean sameSlice(ServerLevel level, BlockPos min, int z) {
		for (int x = 0; x < 8; x++) for (int y = 0; y < 5; y++) {
			if (level.getBlockState(min.offset(x, y, z)) != level.getBlockState(min.offset(x, y, 0))) return false;
		}
		return true;
	}

	/** AbstractContainerScreen keeps its position protected. */
	static int field(AbstractContainerScreen<?> screen, String name) {
		try {
			Field f = AbstractContainerScreen.class.getDeclaredField(name);
			f.setAccessible(true);
			return f.getInt(screen);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	static void clean(Path dir) {
		try {
			Files.createDirectories(dir);
			try (Stream<Path> files = Files.list(dir)) {
				for (Path f : files.filter(f -> f.toString().endsWith(".png")).toList()) Files.delete(f);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
