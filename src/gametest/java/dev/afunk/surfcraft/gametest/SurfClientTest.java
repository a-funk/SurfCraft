package dev.afunk.surfcraft.gametest;

import dev.afunk.surfcraft.block.SurfBlocks;
import dev.afunk.surfcraft.block.SurfRampBlock;
import dev.afunk.surfcraft.client.SurfController;
import dev.afunk.surfcraft.movement.BrushWorld;
import dev.afunk.surfcraft.physics.TickDriver;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Surfing in a real client against the integrated server, in survival with fall damage on: surf runs on a 5:4 and a
 * 2:1 A-frame and on a 5:4 ramp over plain stone, each replayed offline through the same driver as the oracle; air
 * strafing; bunny hopping and the hand back to vanilla; vanilla walking away from ramps; teleports, knockback, water,
 * creative flight and sneaking; vanilla's sneak, Levitation and Slow Falling beside ramps; slime after a surf; an elytra
 * on a ramp; the speedometer; another player surfing; and the controller's cost per tick in a dense ramp field.
 */
public class SurfClientTest implements FabricClientGameTest {
	static final double UPS = SurfController.UNITS_TO_BLOCKS_PER_TICK;

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!ClientTests.enabled("surf")) return;
		context.getInput().resizeWindow(1280, 720);
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(s -> s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create();
				LogWatch log = new LogWatch()) {
			world.getServer().runCommand("time set noon");
			int g = world.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			AFrame a = new AFrame(SurfBlocks.SURF_RAMP, 0, g, 0, 6, 100, false);
			AFrame steep = new AFrame(SurfBlocks.STEEP_SURF_RAMP, 40, g, 0, 8, 100, false);
			AFrame stone = new AFrame(SurfBlocks.SURF_RAMP, 80, g, 0, 6, 100, true);
			world.getServer().runOnServer(s -> {
				ServerLevel level = s.overworld();
				a.build(level);
				steep.build(level);
				stone.build(level);
				// A pool on the 5:4 ramp's west side, and a dense field of single ramp cells for the timing run.
				for (int x = -8; x <= -6; x++) for (int z = 60; z <= 66; z++) for (int y = g - 2; y < g; y++) level.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
				// Beside the 5:4 ramp: slime floor where a launch off its ridge lands, a start platform level with the ridge at its
				// north end, and a block 1.2 blocks from its east toe (standing on it is within the controller's reach).
				for (int x = 8; x <= 30; x++) for (int z = 40; z <= 52; z++) level.setBlock(new BlockPos(x, g - 1, z), Blocks.SLIME_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int x = 5; x <= 7; x++) for (int z = 60; z <= 75; z++) level.setBlock(new BlockPos(x, g - 1, z), Blocks.SOUL_SAND.defaultBlockState(), Block.UPDATE_CLIENTS);
				for (int x = -1; x <= 1; x++) for (int z = 100; z <= 104; z++) level.setBlock(new BlockPos(x, g + 5, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				level.setBlock(new BlockPos(6, g, 80), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				Random random = new Random(3);
				for (int x = 20; x < 44; x++) for (int z = 110; z < 134; z++) for (int y = g; y < g + 2; y++) {
					if (random.nextInt(10) < 3) continue;
					SurfRampBlock block = SurfBlocks.RAMPS.get(random.nextInt(2));
					Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(random.nextLong()));
					level.setBlock(new BlockPos(x, y, z), block.defaultBlockState().setValue(SurfRampBlock.FACING, facing).setValue(block.cut, 1 + random.nextInt(block.p + block.q)), Block.UPDATE_CLIENTS);
				}
			});
			context.runOnClient(c -> c.options.chatVisibility().set(ChatVisiblity.HIDDEN));
			context.getInput().pressKey(o -> o.keyHotbarSlots[8]);
			world.getConnection().waitForChunksRender();
			Surfer s = new Surfer(context, world.getServer(), world.getConnection());
			List<Long> nanos = new ArrayList<>();

			surfRun(s, a, "5:4", 3, 800, nanos, true);
			surfRun(s, steep, "2:1", 4, 1000, nanos, false);
			surfRun(s, stone, "5:4 on stone", 3, 800, nanos, false);
			airStrafe(s, a);
			bunnyHopAndHandBack(s, a, g);
			vanillaWalking(s, g);
			edgeCases(s, a, g);
			sneakingNearRamps(s, g);
			effects(s, a, g);
			blockEffects(s, a, g);
			elytra(s, a);
			speedometer(s, a, g);
			otherSurfers(s, a, g);
			denseField(s, g, nanos);

			System.out.printf("SURFCRAFT single player: %d server rejections %s, %d untracked ticks%n", log.rejections.size(), log.rejections, s.untracked);
			s.check(log.rejections.isEmpty(), "server rejected moves: " + log.rejections);
			s.check(ServerCorrections.EVENTS.isEmpty(), "server corrections: " + ServerCorrections.EVENTS);
			s.assertClean("single player");
		}
	}

	/**
	 * Drops the player on the east slope near the start, facing along the ramp at {@code ups} units/s, holds the strafe
	 * key into the slope (D: the slope rises toward -x, the player's right) with a little mouse movement, then lets go
	 * and slides down to the ground. Every tick: the controller drives, the player is never below the slope, no speed
	 * drop over 30% in surf contact, the server follows the client exactly; afterwards health is unchanged, and the
	 * recorded inputs replayed offline through the same driver land within 0.01 blocks of every published position.
	 */
	static void surfRun(Surfer s, AFrame ramp, String name, double startY, double ups, List<Long> nanos, boolean shots) {
		ClientGameTestContext context = s.context;
		s.tp(ramp.eastX(startY, 0.01), ramp.base() + startY, ramp.z0() + 3, 0, 8);
		s.velocity(new Vec3(-0.02, 0, ups * UPS));
		List<SurfController.Tick> ticks = new ArrayList<>();
		s.tick();
		s.resetCounters();
		context.runOnClient(c -> Surfer.controller(c).recorder = ticks::add);
		float health = s.sample().health();
		int untracked = s.untracked;
		double startZ = s.sample().pos().z, worstClearance = Double.POSITIVE_INFINITY, worstDrop = 0;
		int surfTicks = 0, driven = 0, total = 0;
		Surfer.Sample last = null;
		context.getInput().holdKey(o -> o.keyRight);
		boolean sliding = false;
		int strafe = (int) Math.min(70, (ramp.length() - 3) / (ups * UPS) * 0.6);
		for (int t = 0; t < 140; t++) {
			if (t == strafe) {
				context.getInput().releaseKey(o -> o.keyRight);
				sliding = true;
			}
			// A little mouse movement: the same swing in strafe push at any speed (yaw times speed).
			context.getInput().moveCursor(2 * Math.min(1, 800 / ups) * Math.sin(t * 0.3), 0);
			Surfer.Sample x = s.tick();
			total++;
			if (x.driving()) driven++;
			// On the ramp or in the air after it the controller drives; back on flat ground it may hand back.
			else s.check(x.pos().y < ramp.base() + 0.05, name + " tick " + t + ": not driving at " + x.pos());
			if (ramp.alongside(x.pos().z)) {
				double clearance = ramp.clearance(x.box());
				worstClearance = Math.min(worstClearance, clearance);
				s.check(clearance > -1e-4, name + " tick " + t + ": " + clearance + " blocks inside the slope at " + x.pos());
			}
			if (x.surfed()) surfTicks++;
			if (last != null && x.surfed() && last.surfed() && !x.grounded()) {
				double before = last.deltaMovement().length(), after = x.deltaMovement().length(), drop = 1 - after / before;
				worstDrop = Math.max(worstDrop, drop);
				s.check(drop <= 0.3, name + " tick " + t + ": speed fell " + Math.round(drop * 100) + "% in surf contact (rampbug)");
			}
			if (shots && t == 30) {
				s.screenshot("surf_5_4_first_person");
				context.runOnClient(c -> c.options.setCameraType(CameraType.THIRD_PERSON_BACK));
				s.tick();
				s.screenshot("surf_5_4_third_person");
				context.runOnClient(c -> c.gui.hud.toggle());
				s.screenshot("surf_5_4_f1_hides_speedometer");
				context.runOnClient(c -> {
					c.gui.hud.toggle();
					c.options.setCameraType(CameraType.FIRST_PERSON);
				});
			}
			last = x;
			if (sliding && x.grounded() && !x.surfed() && x.pos().y < ramp.base() + 0.01) break;
		}
		context.getInput().releaseKey(o -> o.keyRight);
		context.runOnClient(c -> Surfer.controller(c).recorder = null);
		for (int t = 0; t < 30; t++) s.tick();
		Surfer.Sample end = s.sample();
		double travelled = end.pos().z - startZ;
		s.check(travelled > 50, name + ": travelled only " + travelled + " blocks along the ramp");
		s.check(end.health() >= health, name + ": health " + health + " -> " + end.health() + " after sliding down");
		s.check(end.resyncs() == 0, name + ": the controller resynced " + end.resyncs() + " times (server corrections)");
		untracked = s.untracked - untracked;
		double[] replay = replay(context, ticks, name);
		for (SurfController.Tick t : ticks) nanos.add(t.nanos());
		System.out.printf("SURFCRAFT run %s: %d ticks, %d driven, %d with surf contact, %.1f blocks along, worst clearance %.6f blocks, worst speed drop in contact %.1f%%, "
				+ "health %.1f -> %.1f, resyncs %d, server off the client's positions %d, replay worst %.2e blocks %.2e u/s over %d ticks%n", name, total, driven, surfTicks,
				travelled, worstClearance, worstDrop * 100, health, end.health(), end.resyncs(), untracked, replay[0], replay[1], ticks.size());
		s.check(replay[0] < 0.01, name + ": the offline replay is " + replay[0] + " blocks off the published positions");
	}

	/**
	 * The integration oracle: the recorded inputs replayed through {@link TickDriver} from the recorded starting state,
	 * against the brushes each tick was given, must reproduce every position Minecraft ended up at (within 0.01 blocks):
	 * the input mapping, substeps, yaw, publishing through {@code move} and everything else in between add nothing. Writes a CSV
	 * (speed in game and in the replay per tick) next to the screenshots. Returns the worst position (blocks) and
	 * velocity (units/s) differences.
	 */
	static double[] replay(ClientGameTestContext context, List<SurfController.Tick> ticks, String name) {
		if (ticks.isEmpty()) return new double[] {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		return context.computeOnClient(c -> {
			LocalPlayer player = c.player;
			TickDriver d = ticks.getFirst().before().copy();
			BlockPos anchor = ticks.getFirst().anchor();
			double worst = 0, worstVelocity = 0;
			StringBuilder csv = new StringBuilder("tick,x,y,z,speed_game,speed_replay,deviation_blocks\n");
			for (int i = 0; i < ticks.size(); i++) {
				SurfController.Tick t = ticks.get(i);
				anchor = SurfController.rebase(d, anchor);
				d.tick(SurfController.config(player.maxUpStep()), t.forward(), t.side(), t.jump(), t.yaw(), BrushWorld.hull(player), t.world());
				Vec3 published = BrushWorld.toMinecraft(d.published, anchor), velocity = SurfController.minecraftVelocity(d.core.velocity);
				double deviation = published.distanceTo(t.published());
				worst = Math.max(worst, deviation);
				worstVelocity = Math.max(worstVelocity, velocity.subtract(t.deltaMovement()).length() / UPS);
				csv.append("%d,%.6f,%.6f,%.6f,%.3f,%.3f,%.3e%n".formatted(i, t.published().x, t.published().y, t.published().z, Surfer.horizontal(t.deltaMovement()),
						Surfer.horizontal(velocity), deviation));
			}
			try {
				Files.createDirectories(ClientTests.OUT);
				Files.writeString(ClientTests.OUT.resolve("replay-" + name.replaceAll("[^A-Za-z0-9]+", "_") + ".csv"), csv);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return new double[] {worst, worstVelocity};
		});
	}

	/**
	 * Leaves the top of the ramp upward and away from it (+x, facing +x) at 100 units/s, then strafes in the air while
	 * turning: D with the mouse turning right (and A turning left) must gain past 250 units/s as in CS:S; D turning left
	 * must not gain.
	 */
	static void airStrafe(Surfer s, AFrame ramp) {
		double[] gains = new double[3];
		String[] names = {"D + turn right", "A + turn left", "D + turn left"};
		for (int k = 0; k < 3; k++) {
			s.tp(ramp.eastX(5.2, 0.05), ramp.base() + 5.2, ramp.z0() + 20 + 25 * k, -90, 0);
			s.velocity(new Vec3(100 * UPS, 1.0, 0));
			s.tick();
			s.tick();
			double start = s.sample().speed();
			int key = k == 1 ? 0 : 1;
			if (key == 0) s.context.getInput().holdKey(o -> o.keyLeft);
			else s.context.getInput().holdKey(o -> o.keyRight);
			double turn = k == 0 ? 80 : -80, best = start;
			boolean drove = true;
			float yaw0 = s.sample().yaw();
			for (int t = 0; t < (k == 2 ? 10 : 36); t++) {
				s.context.getInput().moveCursor(turn, 0);
				Surfer.Sample x = s.tick();
				drove &= x.driving();
				best = Math.max(best, x.speed());
				if (x.grounded()) break;
			}
			float turned = s.sample().yaw() - yaw0;
			s.context.getInput().releaseKey(o -> o.keyLeft);
			s.context.getInput().releaseKey(o -> o.keyRight);
			gains[k] = best;
			System.out.printf("SURFCRAFT air strafe %s: %.1f -> best %.1f units/s, turned %.1f degrees, driving throughout %b, health %.1f%n", names[k], start, best, turned,
					drove, s.sample().health());
			s.check(drove, "air strafe " + names[k] + ": the controller stopped driving in the air");
			for (int t = 0; t < 40 && !s.sample().grounded(); t++) s.tick();
		}
		s.check(gains[0] > 250, "air strafe D + turn right reached only " + gains[0] + " units/s");
		s.check(gains[1] > 250, "air strafe A + turn left reached only " + gains[1] + " units/s");
		s.check(gains[2] < 115, "air strafe D + turn left gained to " + gains[2] + " units/s");
	}

	/**
	 * Off the ramp onto flat ground at 500 units/s holding jump: auto hop keeps the speed and the controller keeps
	 * driving beyond the ramp's reach, also through a hit taken mid-hop (e3: the server syncs the player's real
	 * velocity, not its own zero-input one). Let go and, after the grace window, vanilla movement resumes at a sane speed.
	 */
	static void bunnyHopAndHandBack(Surfer s, AFrame ramp, int g) {
		// Full health and no hurt cooldown, so the hit mid-hop lands.
		s.server.runCommand("effect give @a minecraft:instant_health 1 5 true");
		for (int t = 0; t < 25; t++) s.tick();
		s.context.getInput().holdKey(o -> o.keyJump);
		// West, off the 5:4 ramp's west toe toward open ground (east lies the steep ramp).
		s.tp(ramp.ridge() - 5.5, g, ramp.z0() + 50, 90, 0);
		s.velocity(new Vec3(-500 * UPS, 0, 0));
		s.tick();
		s.resetCounters();
		double first = 0, min = Double.POSITIVE_INFINITY, max = 0, beforeHit = 0, afterHit = 0, lastY = 0;
		boolean drove = true;
		int jumps = 0, adopted = 0;
		float healthBefore = 0, healthAfter = 0;
		for (int t = 0; t < 50; t++) {
			if (t == 20) {
				beforeHit = s.sample().speed();
				healthBefore = s.sample().health();
				s.server.runOnServer(srv -> {
					ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
					p.hurtServer(p.level(), p.damageSources().generic(), 1);
				});
				s.connection.waitForClientboundPackets();
			}
			Surfer.Sample x = s.tick();
			if (t == 20) {
				afterHit = x.speed();
				adopted = x.adopts();
				healthAfter = x.health();
			}
			drove &= x.driving();
			if (t == 1) first = x.speed();
			if (t >= 1) {
				min = Math.min(min, x.speed());
				max = Math.max(max, x.speed());
			}
			// An auto hop lands and jumps within one tick: count jumps by the vertical velocity turning back up.
			if (x.deltaMovement().y > lastY + 0.2) jumps++;
			lastY = x.deltaMovement().y;
		}
		double away = ramp.ridge() - s.sample().pos().x;
		s.context.getInput().releaseKey(o -> o.keyJump);
		System.out.printf("SURFCRAFT bunny hop: %.1f units/s, min %.1f max %.1f over 49 ticks, %d hops, %.1f blocks from the ridge, driving throughout %b%n", first, min, max,
				jumps, away, drove);
		System.out.printf("SURFCRAFT hit mid-hop: %.1f -> %.1f units/s, health %.1f -> %.1f, velocity packets adopted %d%n", beforeHit, afterHit, healthBefore, healthAfter, adopted);
		s.check(drove, "bunny hop: the controller stopped while hopping");
		s.check(jumps >= 3, "bunny hop: only " + jumps + " hops");
		s.check(min > first * 0.98, "bunny hop lost speed: " + first + " -> " + min);
		s.check(healthAfter < healthBefore && afterHit > beforeHit * 0.95, "a hit mid-hop stopped the player: " + beforeHit + " -> " + afterHit + " units/s");
		int handBack = -1;
		for (int t = 0; t < 60; t++) {
			Surfer.Sample x = s.tick();
			if (!x.driving()) {
				handBack = t;
				break;
			}
		}
		Surfer.Sample after = s.tick();
		double vanilla = after.deltaMovement().horizontalDistance();
		System.out.printf("SURFCRAFT hand back to vanilla %d ticks after releasing jump, at %.3f blocks/tick%n", handBack, vanilla);
		s.check(handBack > 0 && handBack < 40, "vanilla movement did not resume (" + handBack + ")");
		s.check(vanilla < 0.35, "vanilla resumed at " + vanilla + " blocks/tick");
	}

	/** Far from ramps the controller stays off: walking 4.3 blocks/s, sprinting 5.6. */
	static void vanillaWalking(Surfer s, int g) {
		s.tp(20.5, g, -20.5, -90, 0);
		for (int t = 0; t < 5; t++) s.tick();
		s.check(!s.sample().driving(), "the controller drove after a teleport far from ramps");
		for (boolean sprint : new boolean[] {false, true}) {
			s.context.getInput().holdKey(o -> o.keyUp);
			if (sprint) s.context.getInput().holdKey(o -> o.keySprint);
			boolean drove = false;
			Vec3 from = null;
			for (int t = 0; t < 40; t++) {
				Surfer.Sample x = s.tick();
				drove |= x.driving();
				if (t == 19) from = x.pos();
			}
			Vec3 to = s.sample().pos();
			double speed = to.subtract(from).horizontalDistance() / 20 * 20;
			s.context.getInput().releaseKey(o -> o.keyUp);
			s.context.getInput().releaseKey(o -> o.keySprint);
			for (int t = 0; t < 10; t++) s.tick();
			System.out.printf("SURFCRAFT vanilla %s: %.3f blocks/s, controller driving %b%n", sprint ? "sprinting" : "walking", speed, drove);
			s.check(!drove, "the controller drove far from ramps");
			s.check(Math.abs(speed - (sprint ? 5.612 : 4.317)) < 0.1, "vanilla " + (sprint ? "sprinting" : "walking") + " at " + speed + " blocks/s");
		}
	}

	/** Teleport mid-surf, server knockback, water, creative flight against the slope, sneaking on the ramp. */
	static void edgeCases(Surfer s, AFrame ramp, int g) {
		ClientGameTestContext context = s.context;
		// Teleport mid-surf: the controller resyncs once and keeps surfing from the new spot.
		s.tp(ramp.eastX(3, 0.01), g + 3, ramp.z0() + 10, 0, 8);
		s.velocity(new Vec3(0, 0, 800 * UPS));
		s.tick();
		s.resetCounters();
		context.getInput().holdKey(o -> o.keyRight);
		for (int t = 0; t < 10; t++) s.tick();
		context.runOnClient(c -> Surfer.controller(c).log = e -> System.out.println("SURFCRAFT controller: " + e));
		s.server.runCommand("tp @a %s %s %s ~ ~".formatted(ramp.eastX(2, 0.01), g + 2.0, ramp.z0() + 60.0));
		s.teleported();
		boolean drove = true;
		double worst = Double.POSITIVE_INFINITY;
		for (int t = 0; t < 30; t++) {
			Surfer.Sample x = s.tick();
			drove &= x.driving();
			if (ramp.alongside(x.pos().z)) worst = Math.min(worst, ramp.clearance(x.box()));
		}
		Surfer.Sample afterTp = s.sample();
		System.out.printf("SURFCRAFT /tp mid-surf: resyncs %d, driving throughout %b, worst clearance %.6f, now at %s%n", afterTp.resyncs(), drove, worst, afterTp.pos());
		s.check(afterTp.resyncs() == 1, "/tp mid-surf: " + afterTp.resyncs() + " resyncs instead of 1");
		s.check(drove && worst > -1e-4, "/tp mid-surf: lost the ramp (driving " + drove + ", clearance " + worst + ")");

		// Server-side knockback: the server's motion packet replaces the velocity, and the controller adopts it.
		context.getInput().releaseKey(o -> o.keyRight);
		s.tick();
		s.resetCounters();
		Vec3 before = s.sample().deltaMovement();
		s.server.runOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().getFirst();
			p.knockback(1.0, -1, 0, p.damageSources().generic(), 0);
			p.connection.send(new ClientboundSetEntityMotionPacket(p));
		});
		s.connection.waitForClientboundPackets();
		Surfer.Sample hit = s.tick();
		System.out.printf("SURFCRAFT knockback: velocity %s -> %s blocks/tick, adopted %d%n", before, hit.deltaMovement(), hit.adopts());
		s.check(hit.adopts() == 1 && hit.deltaMovement().x > before.x + 0.5, "knockback did not reach the controller: " + before + " -> " + hit.deltaMovement());
		for (int t = 0; t < 40 && !s.sample().grounded(); t++) s.tick();

		// Water: falling into a pool next to the ramp hands over to vanilla swimming.
		s.tp(-6.0, g + 1.5, ramp.z0() + 63, 0, 0);
		s.velocity(new Vec3(0, 0, 0));
		boolean droveAbove = false, droveInWater = false, wet = false;
		for (int t = 0; t < 30; t++) {
			Surfer.Sample x = s.tick();
			if (t < 2) droveAbove |= x.driving();
			// A tick that started in the water (the controller decides before moving) must be vanilla.
			if (wet) droveInWater |= x.driving();
			wet |= x.inWater();
		}
		System.out.printf("SURFCRAFT water: driving above the pool %b, in water %b, driving in water %b%n", droveAbove, wet, droveInWater);
		s.check(droveAbove && wet && !droveInWater, "water hand-over (above " + droveAbove + ", wet " + wet + ", driving in water " + droveInWater + ")");

		// Creative flight: sinking onto the slope stops at the exact plane, not on the staircase below it.
		s.server.runCommand("gamemode creative @a");
		s.connection.waitForClientboundPackets();
		s.tp(ramp.eastX(3.5, 0.5), g + 3.5, ramp.z0() + 30, 0, 30);
		s.tick();
		context.runOnClient(c -> {
			c.player.getAbilities().flying = true;
			c.player.onUpdateAbilities();
		});
		s.tick();
		context.getInput().holdKey(o -> o.keyShift);
		context.getInput().holdKey(o -> o.keyRight);
		double flyWorst = Double.POSITIVE_INFINITY, flyEnd = 0;
		boolean flyDrove = false;
		int flying = 0;
		for (int t = 0; t < 30; t++) {
			Surfer.Sample x = s.tick();
			if (x.flying()) {
				flying++;
				flyDrove |= x.driving();
			}
			flyEnd = ramp.clearance(x.box());
			flyWorst = Math.min(flyWorst, flyEnd);
		}
		context.getInput().releaseKey(o -> o.keyShift);
		context.getInput().releaseKey(o -> o.keyRight);
		System.out.printf("SURFCRAFT creative flight into the slope: %d ticks flying, worst clearance %.6f, resting %.6f blocks above it, controller drove while flying %b%n",
				flying, flyWorst, flyEnd, flyDrove);
		s.check(flying > 0 && !flyDrove && flyWorst > -1e-5 && flyEnd < 0.05, "creative flight vs the slope: worst " + flyWorst + ", resting " + flyEnd + ", driving " + flyDrove);
		context.runOnClient(c -> {
			c.player.getAbilities().flying = false;
			c.player.onUpdateAbilities();
		});
		s.server.runCommand("gamemode survival @a");
		s.connection.waitForClientboundPackets();

		// Sneaking while surfing: no edge back-off on either side, so no rubber-banding.
		s.tp(ramp.eastX(3, 0.01), g + 3, ramp.z0() + 5, 0, 8);
		s.velocity(new Vec3(0, 0, 800 * UPS));
		s.tick();
		s.resetCounters();
		context.getInput().holdKey(o -> o.keyRight);
		context.getInput().holdKey(o -> o.keyShift);
		boolean sneakDrove = true;
		double sneakWorst = Double.POSITIVE_INFINITY;
		for (int t = 0; t < 50; t++) {
			Surfer.Sample x = s.tick();
			sneakDrove &= x.driving();
			if (ramp.alongside(x.pos().z)) sneakWorst = Math.min(sneakWorst, ramp.clearance(x.box()));
		}
		context.getInput().releaseKey(o -> o.keyShift);
		context.getInput().releaseKey(o -> o.keyRight);
		Surfer.Sample sneak = s.sample();
		System.out.printf("SURFCRAFT sneaking on the ramp: resyncs %d, driving throughout %b, worst clearance %.6f, crouched box height %.2f%n", sneak.resyncs(), sneakDrove,
				sneakWorst, sneak.box().getYsize());
		s.check(sneak.resyncs() == 0 && sneakDrove && sneakWorst > -1e-4, "sneaking on the ramp: resyncs " + sneak.resyncs() + ", clearance " + sneakWorst);
		for (int t = 0; t < 60 && !s.sample().grounded(); t++) s.tick();
	}

	/**
	 * Sneaking on flat ground near a ramp is vanilla's sneak (B4): it holds the player at the edge of the start platform at
	 * the ramp's end and of a block 1.2 blocks from its toe (without a server correction, C4), at sneaking speed.
	 */
	static void sneakingNearRamps(Surfer s, int g) {
		// Feet x, y, z, and the edge ahead (east).
		for (double[] e : new double[][] {{0.5, g + 6, 100.5, 2}, {6.5, g + 1, 80.5, 7}}) {
			s.tp(e[0], e[1], e[2], -90, 0);
			s.context.getInput().holdKey(o -> o.keyShift);
			for (int t = 0; t < 5; t++) s.tick();
			s.context.getInput().holdKey(o -> o.keyUp);
			for (int t = 0; t < 30; t++) s.tick();
			Surfer.Sample end = s.sample();
			s.context.getInput().releaseKey(o -> o.keyUp);
			s.context.getInput().releaseKey(o -> o.keyShift);
			System.out.printf("SURFCRAFT sneaking toward an edge at x %.0f beside a ramp: stopped at x %.3f, feet %.3f blocks above the top%n", e[3], end.pos().x, end.pos().y - e[1]);
			s.check(end.pos().y > e[1] - 1e-3 && end.pos().x < e[3] + 0.3, "sneaking walked off the edge at x " + e[3] + ": now at " + end.pos());
			for (int t = 0; t < 10; t++) s.tick();
		}
		s.tp(5.6, g, 55.5, 0, 0);
		s.context.getInput().holdKey(o -> o.keyShift);
		for (int t = 0; t < 5; t++) s.tick();
		s.context.getInput().holdKey(o -> o.keyUp);
		Vec3 from = null;
		for (int t = 0; t < 30; t++) {
			Surfer.Sample x = s.tick();
			if (t == 14) from = x.pos();
		}
		double speed = s.sample().pos().subtract(from).horizontalDistance() / 15 * 20;
		s.context.getInput().releaseKey(o -> o.keyUp);
		s.context.getInput().releaseKey(o -> o.keyShift);
		System.out.printf("SURFCRAFT sneaking along the ramp's toe: %.3f blocks/s%n", speed);
		s.check(speed < 2, "sneaking beside the ramp at " + speed + " blocks/s");
	}

	/** Levitation and Slow Falling hand the player to vanilla even beside a ramp or after a surf (B5). */
	static void effects(Surfer s, AFrame ramp, int g) {
		s.tp(5.6, g, 90.5, 90, 0);
		for (int t = 0; t < 5; t++) s.tick();
		double y0 = s.sample().pos().y, top = y0;
		boolean drove = false;
		s.server.runCommand("effect give @a minecraft:levitation 2 1 true");
		for (int t = 0; t < 30; t++) {
			Surfer.Sample x = s.tick();
			top = Math.max(top, x.pos().y);
			// From the tick the effect reaches the client.
			drove |= t > 1 && x.driving();
		}
		s.server.runCommand("effect clear @a minecraft:levitation");
		for (int t = 0; t < 30 && !s.sample().grounded(); t++) s.tick();
		// Launched off the ridge (the post-surf window) with Slow Falling.
		s.server.runCommand("effect give @a minecraft:slow_falling 30 0 true");
		s.tp(ramp.eastX(5.9, 0.05), ramp.base() + 5.9, ramp.z0() + 20.5, -90, 0);
		s.velocity(new Vec3(300 * UPS, 1.0, 0));
		double fastest = 0;
		for (int t = 0; t < 200 && (t < 5 || s.sample().pos().y - g > 0.01); t++) fastest = Math.min(fastest, s.tick().deltaMovement().y);
		s.server.runCommand("effect clear @a minecraft:slow_falling");
		System.out.printf("SURFCRAFT effects: levitation beside the ramp rose %.2f blocks (driving %b), slow falling after a launch fell at most %.3f blocks/tick%n", top - y0, drove,
				fastest);
		s.check(top - y0 > 1 && !drove, "levitation beside the ramp rose " + (top - y0) + " blocks, driving " + drove);
		s.check(fastest > -0.5, "slow falling after a launch fell at " + fastest + " blocks/tick");
	}

	/** Block effects move() applies reach the core (B3): landing on slime after a surf bounces; soul sand slows the walk. */
	static void blockEffects(Surfer s, AFrame ramp, int g) {
		s.tp(ramp.eastX(5.9, 0.05), ramp.base() + 5.9, 46.5, -90, 0);
		s.velocity(new Vec3(300 * UPS, 1.0, 0));
		int landed = -1;
		double bounce = 0;
		for (int t = 0; t < 110 && (landed < 0 || t < landed + 40); t++) {
			Surfer.Sample x = s.tick();
			if (landed < 0 && t > 5 && x.pos().y - g < 0.05) landed = t;
			else if (landed >= 0) bounce = Math.max(bounce, x.pos().y - g);
		}
		System.out.printf("SURFCRAFT slime after a surf: landed at tick %d, bounced %.2f blocks%n", landed, bounce);
		s.check(landed > 0 && bounce > 4, "slime after a surf: landed at tick " + landed + ", bounced " + bounce + " blocks");
		// Soul sand beside the toe slows the controller's walk (CS:S 250 u/s, 6.35 blocks/s) as move() slows vanilla's.
		s.tp(6.5, g, 60.5, 0, 0);
		s.context.getInput().holdKey(o -> o.keyUp);
		Vec3 from = null;
		boolean drove = true;
		for (int t = 0; t < 30; t++) {
			Surfer.Sample x = s.tick();
			drove &= x.driving();
			if (t == 14) from = x.pos();
		}
		double speed = s.sample().pos().subtract(from).horizontalDistance() / 15 * 20;
		s.context.getInput().releaseKey(o -> o.keyUp);
		System.out.printf("SURFCRAFT walking on soul sand beside the ramp: %.3f blocks/s, driving %b%n", speed, drove);
		s.check(drove && speed > 0.5 && speed < 4, "walking on soul sand beside the ramp at " + speed + " blocks/s, driving " + drove);
	}

	/** An elytra deploys on a ramp (B9): pulling up while surfing, the client and the server both glide. */
	static void elytra(Surfer s, AFrame ramp) {
		s.server.runCommand("item replace entity @a armor.chest with minecraft:elytra");
		s.tp(ramp.eastX(3, 0.01), ramp.base() + 3, ramp.z0() + 50, 0, -30);
		s.velocity(new Vec3(-0.02, 0, 1200 * UPS));
		s.tick();
		s.context.getInput().holdKey(o -> o.keyRight);
		for (int t = 0; t < 4; t++) s.tick();
		s.context.getInput().holdKey(o -> o.keyJump);
		int both = 0;
		StringBuilder trace = new StringBuilder();
		for (int t = 0; t < 10; t++) {
			if (t == 1) s.context.getInput().releaseKey(o -> o.keyJump);
			s.tick();
			boolean client = s.context.computeOnClient(c -> c.player.isFallFlying());
			boolean server = s.server.computeOnServer(srv -> srv.getPlayerList().getPlayers().getFirst().isFallFlying());
			if (client && server) both++;
			trace.append(client ? 'c' : '-').append(server ? 's' : '-').append(' ');
		}
		s.context.getInput().releaseKey(o -> o.keyRight);
		s.server.runCommand("item replace entity @a armor.chest with minecraft:air");
		System.out.println("SURFCRAFT elytra on the ramp (client/server gliding per tick): " + trace);
		s.check(both >= 3, "the elytra did not deploy on the ramp: " + trace);
		for (int t = 0; t < 80 && !s.sample().grounded(); t++) s.tick();
	}

	/**
	 * The speedometer sits above the action bar (B6), so a Karambit message stays readable, and hides at rest. Screenshots:
	 * surfing with an action-bar message, and standing beside the ramp.
	 */
	static void speedometer(Surfer s, AFrame ramp, int g) {
		s.tp(ramp.eastX(3, 0.01), ramp.base() + 3, ramp.z0() + 30, 0, 8);
		s.velocity(new Vec3(-0.02, 0, 800 * UPS));
		s.context.getInput().holdKey(o -> o.keyRight);
		for (int t = 0; t < 6; t++) s.tick();
		s.server.runOnServer(srv -> srv.getPlayerList().getPlayers().getFirst().sendOverlayMessage(Component.literal("Placed the module: 96 blocks")));
		s.tick();
		s.context.runOnClient(c -> c.gui.toastManager().clear());
		s.screenshot("speedometer_with_action_bar");
		s.context.getInput().releaseKey(o -> o.keyRight);
		for (int t = 0; t < 60 && !s.sample().grounded(); t++) s.tick();
		s.tp(5.6, g, 95.5, 90, 10);
		for (int t = 0; t < 5; t++) s.tick();
		s.check(s.sample().driving(), "not driving beside the ramp");
		s.screenshot("speedometer_hidden_at_rest");
	}

	/**
	 * Other players see a surfer glide, not run (C6). A second player, driven by packets, slides along the 5:4 slope at 1000
	 * u/s past the client's player: the server tells the client it is surfing, so its legs settle; the same flight far
	 * from ramps swings them as vanilla does.
	 */
	static void otherSurfers(Surfer s, AFrame ramp, int g) {
		// Standing on the floor east of the slope, facing up it toward where the other player passes.
		s.tp(ramp.eastX(0, 4), g, ramp.z0() + 80, 110, -12);
		for (int t = 0; t < 5; t++) s.tick();
		s.context.runOnClient(c -> c.gui.toastManager().clear());
		PacketPlayer other = s.server.computeOnServer(srv -> new PacketPlayer(srv.overworld(), "Surfer2"));
		try {
			Vec3 slope = new Vec3(ramp.eastX(3, 0.01), ramp.base() + 3, ramp.z0() + 72), air = new Vec3(60.5, g + 3, ramp.z0() + 72);
			double[] legs = {-1, -1};
			for (int k = 0; k < 2; k++) {
				Vec3 start = k == 0 ? slope : air;
				s.server.runOnServer(srv -> other.teleport(start));
				for (int t = 0; t < 16; t++) {
					Vec3 at = start.add(0, 0, (t + 1) * 1000 * UPS);
					s.server.runOnServer(srv -> {
						other.tick();
						other.move(at, false);
					});
					s.tick();
					if (k == 0 && t == 4) s.screenshot("other_player_surfing");
					float speed = s.context.computeOnClient(c -> c.level.getEntity(other.player.getId()) instanceof LivingEntity e ? e.walkAnimation.speed() : -1f);
					if (t >= 6) legs[k] = Math.max(legs[k], speed);
				}
			}
			System.out.printf("SURFCRAFT another player's legs (walk animation speed, 0 still to 1 running): surfing the ramp %.2f, the same flight far from ramps %.2f%n",
					legs[0], legs[1]);
			s.check(legs[0] >= 0 && legs[0] < 0.1 && legs[1] > 0.5, "another surfer's legs: " + legs[0] + " on the ramp, " + legs[1] + " far from ramps");
		} finally {
			s.server.runOnServer(srv -> other.leave());
		}
	}

	/** Timing in a dense field of single ramp cells (no merging): the controller's time per driven tick. */
	static void denseField(Surfer s, int g, List<Long> nanos) {
		s.tp(21.5, g + 2.6, 111.5, -45, 0);
		s.velocity(new Vec3(400 * UPS, 0, 400 * UPS));
		s.tick();
		s.resetCounters();
		List<Long> dense = new ArrayList<>();
		s.context.getInput().holdKey(o -> o.keyJump);
		s.context.getInput().holdKey(o -> o.keyUp);
		for (int t = 0; t < 80; t++) {
			Surfer.Sample x = s.tick();
			if (x.driving()) dense.add(x.nanos());
			s.context.getInput().moveCursor(10 * Math.sin(t * 0.2), 0);
		}
		s.context.getInput().releaseKey(o -> o.keyJump);
		s.context.getInput().releaseKey(o -> o.keyUp);
		long[] d = dense.stream().mapToLong(Long::longValue).sorted().toArray(), all = nanos.stream().mapToLong(Long::longValue).sorted().toArray();
		System.out.printf("SURFCRAFT controller time per tick: dense field %d ticks median %.3f ms p95 %.3f ms max %.3f ms; surf runs %d ticks median %.3f ms p95 %.3f ms max %.3f ms%n",
				d.length, ms(d, 0.5), ms(d, 0.95), ms(d, 1), all.length, ms(all, 0.5), ms(all, 0.95), ms(all, 1));
		s.check(d.length > 40, "dense field: drove only " + d.length + " ticks");
		s.check(ms(d, 0.5) < 1 && ms(all, 0.5) < 1, "the controller takes over 1 ms per tick (median)");
	}

	static double ms(long[] sorted, double q) {
		return sorted.length == 0 ? Double.NaN : sorted[Math.min(sorted.length - 1, (int) (q * sorted.length))] / 1e6;
	}
}
