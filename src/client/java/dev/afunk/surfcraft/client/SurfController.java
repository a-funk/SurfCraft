package dev.afunk.surfcraft.client;

import dev.afunk.surfcraft.mixin.EntityAccessor;
import dev.afunk.surfcraft.movement.BrushWorld;
import dev.afunk.surfcraft.movement.RampCollision;
import dev.afunk.surfcraft.movement.SurfPlayer;
import dev.afunk.surfcraft.physics.Brush;
import dev.afunk.surfcraft.physics.Config;
import dev.afunk.surfcraft.physics.SourceUnits;
import dev.afunk.surfcraft.physics.TickDriver;
import dev.afunk.surfcraft.physics.V3;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * CS:S surf movement for the local player (one per {@link LocalPlayer}, so a new world, death or respawn starts fresh).
 *
 * <p>It drives while the player is on foot (not flying, gliding, riding, in a fluid, swimming, climbing, sleeping,
 * spectating, spin attacking, stuck in a cobweb, levitating or slow falling) and either near a ramp
 * ({@link RampCollision#surfNear}) or still in the post-surf window: airborne since leaving one, or on other ground for at
 * most {@link RampCollision#GRACE} ticks, so hops carry from ramp to ramp. Sneaking on flat ground is vanilla's sneak,
 * near a ramp or not. Each driven tick runs {@link dev.afunk.surfcraft.physics.SourceMovement} substeps through
 * {@link TickDriver} and publishes the result through {@code player.move} with a trust payload, so footsteps, view bob,
 * fall handling and block effects keep working; the velocity it leaves in {@code deltaMovement} is the true one, which
 * vanilla continues from when it hands back.
 *
 * <p>While it drives, CS:S's fixed values apply (knife speed 250, jump, gravity 800): Speed, Jump Boost and honey's jump
 * factor do not change them. Block speed factors (soul sand, honey) and bounces (slime, beds) do, as vanilla applies them
 * in {@code move}.
 */
public final class SurfController {
	static final double K = SourceUnits.PER_BLOCK;
	/** Units/s to blocks/tick: 0.0254 m per unit, 0.05 s per tick. */
	public static final double UNITS_TO_BLOCKS_PER_TICK = 0.0254 * TickDriver.TICK;
	/** Units around the hull's one-tick reach collected as brushes: covers a jump, a step and a tick's acceleration. */
	static final double REACH = 60;
	/** Units from the anchor after which the local frame follows the player (float origins stay within 0.001 unit). */
	static final double REANCHOR = 8192;

	/** One driven tick, for tests: the driver before it, its brushes and inputs, and what was published. */
	public record Tick(TickDriver before, BlockPos anchor, List<Brush> world, double forward, double side, boolean jump, double yaw, Vec3 published,
			Vec3 deltaMovement, long nanos) {
	}

	private final LocalPlayer player;
	private @Nullable TickDriver driver;
	private BlockPos anchor = BlockPos.ZERO;
	private @Nullable Vec3 lastPos, lastVel;
	private int groundTicks;
	private boolean driving;
	private double forward, side;
	private boolean jump;

	/** External changes the controller picked up: position resyncs (teleports, corrections) and adopted velocities. */
	public int resyncs, adopts;
	/** Nanoseconds the last driven tick took (collecting brushes, substeps, publishing). */
	public long nanos;
	public @Nullable Consumer<Tick> recorder;
	/** Resyncs and hand-backs, described (for tests). */
	public @Nullable Consumer<String> log;

	public SurfController(LocalPlayer player) {
		this.player = player;
	}

	public boolean driving() {
		return driving;
	}

	public @Nullable TickDriver driver() {
		return driver;
	}

	/** Horizontal speed in units/s while driving. */
	public double speed() {
		return driver == null ? 0 : Math.hypot(driver.core.velocity.x(), driver.core.velocity.y());
	}

	/** a1, after {@code applyInput}: decide whether to drive this tick, take the keys, and keep vanilla from jumping. */
	public void decide() {
		boolean was = driving;
		driving = false;
		if (!eligible()) {
			if (was && log != null) log.accept("not eligible at " + player.position());
			handBack();
			return;
		}
		if (driver != null && !player.position().equals(lastPos)) {
			// A teleport, a server correction or anything else moved the player: start over from there (and only near a
			// ramp: the post-surf window is for flying on from a ramp, not for wherever a teleport lands).
			resyncs++;
			if (log != null) log.accept("resync " + lastPos + " -> " + player.position());
			stop();
		}
		// Sneaking on flat ground is vanilla's sneak (slow, held back at edges), near a ramp too: CS:S's duck is not ported.
		if (player.isShiftKeyDown() && (driver != null ? driver.core.grounded : player.onGround())) {
			if (was && log != null) log.accept("hand back to sneak at " + player.position());
			handBack();
			return;
		}
		double speed = driver != null ? driver.core.velocity.length() * UNITS_TO_BLOCKS_PER_TICK : player.getDeltaMovement().length();
		if (RampCollision.surfNear(player, speed)) groundTicks = 0;
		else if (driver == null) {
			if (was && log != null) log.accept("no ramp near " + player.position());
			return;
		} else {
			groundTicks = driver.core.grounded ? groundTicks + 1 : 0;
			if (groundTicks > RampCollision.GRACE) {
				if (log != null) log.accept("hand back after " + groundTicks + " ticks on the ground at " + player.position());
				handBack();
				return;
			}
		}
		driving = true;
		Input keys = player.input.keyPresses;
		forward = 400 * ((keys.forward() ? 1 : 0) - (keys.backward() ? 1 : 0));
		side = 400 * ((keys.right() ? 1 : 0) - (keys.left() ? 1 : 0));
		jump = keys.jump();
		player.setJumping(false);
	}

	private boolean eligible() {
		return player.isAlive() && Minecraft.getInstance().getCameraEntity() == player && !player.isPassenger() && !player.getAbilities().flying
				&& !player.isFallFlying() && !player.isInWater() && !player.isInLava() && !player.isSwimming() && !player.onClimbable() && !player.isSleeping()
				&& !player.isSpectator() && !player.isAutoSpinAttack() && !player.noPhysics
				&& ((EntityAccessor) player).surfcraft$stuckSpeedMultiplier().lengthSqr() <= 1e-7
				&& !player.hasEffect(MobEffects.LEVITATION) && !player.hasEffect(MobEffects.SLOW_FALLING);
	}

	private void stop() {
		driver = null;
		lastPos = lastVel = null;
		groundTicks = 0;
	}

	/**
	 * Leaves the player to vanilla movement. From the ground, with the downward velocity vanilla's players at rest carry
	 * (Source keeps none): vanilla's first move then finds the ground too, instead of counting the player airborne.
	 */
	private void handBack() {
		if (driver != null && driver.core.grounded) player.setDeltaMovement(player.getDeltaMovement().with(Direction.Axis.Y, -player.getGravity()));
		stop();
	}

	/** a2, instead of vanilla travel; false hands this tick to vanilla (the hull is stuck inside something). */
	public boolean travel() {
		long start = System.nanoTime();
		Vec3 pos = player.position();
		if (driver == null) {
			driver = new TickDriver();
			place(pos);
			driver.core.grounded = player.onGround();
			if (log != null) log.accept("start at " + pos + " moving " + player.getDeltaMovement());
		} else if (!player.getDeltaMovement().equals(cleaned(lastVel))) {
			// Knockback, explosions, pushes, motion packets, block effects.
			adopts++;
			driver.core.velocity = sourceVelocity(player.getDeltaMovement());
		}
		TickDriver d = driver;
		anchor = rebase(d, anchor);
		TickDriver before = recorder == null ? null : d.copy();
		double yaw = -player.getYRot() - 90.0;
		List<Brush> world = brushes(player.level(), player, d, anchor);
		d.tick(config(player.maxUpStep()), forward, side, jump, yaw, BrushWorld.hull(player), world);
		if (d.stuck) {
			if (log != null) log.accept("stuck at " + pos + ", handing this tick to vanilla");
			driving = false;
			stop();
			return false;
		}
		publish(d);
		nanos = System.nanoTime() - start;
		if (recorder != null) recorder.accept(new Tick(before, anchor, world, forward, side, jump, yaw, player.position(), player.getDeltaMovement(), nanos));
		return true;
	}

	/** Puts the core at a Minecraft position and velocity (deltaMovement), with a fresh anchor there. */
	private void place(Vec3 pos) {
		anchor = BlockPos.containing(pos);
		driver.core.setOrigin(BrushWorld.toSource(pos, anchor));
		driver.core.velocity = sourceVelocity(player.getDeltaMovement());
		driver.published = driver.core.origin;
	}

	/** The surf profile: CS:S surf settings at knife speed 250, stepping like the player (stairs and slabs). */
	public static Config config(float maxUpStep) {
		return Config.CSS_SURF.withStepHeight(maxUpStep * K);
	}

	/** Everything the hull can reach this tick, as brushes in the anchor's frame. */
	public static List<Brush> brushes(Level level, LocalPlayer player, TickDriver d, BlockPos anchor) {
		V3[] reach = d.reach(BrushWorld.hull(player), REACH);
		return BrushWorld.collect(level, player, BrushWorld.toMinecraft(reach[0], reach[1], anchor), anchor, false);
	}

	/** Moves the local frame along when the core strays far from its anchor; returns the anchor to use. */
	public static BlockPos rebase(TickDriver d, BlockPos anchor) {
		V3 o = d.core.origin;
		if (Math.max(Math.abs(o.x()), Math.max(Math.abs(o.y()), Math.abs(o.z()))) < REANCHOR) return anchor;
		BlockPos next = BlockPos.containing(BrushWorld.toMinecraft(o, anchor));
		V3 shift = BrushWorld.toSourceDelta(Vec3.atLowerCornerOf(anchor.subtract(next)));
		d.core.setOrigin(o.add(shift));
		d.published = d.published.add(shift);
		return next;
	}

	/**
	 * Publishes the core state at the tick boundary through {@code move} (research doc section 9): a Source-grounded
	 * player's move gets a small downward probe so {@code move} sees ground (footsteps, landing, supporting block), then
	 * deltaMovement and the flags are set from the core. What {@code move} does to the velocity, the core takes on: the
	 * block speed factor (soul sand, honey) and a bounce (slime, beds), which reverses this tick's fall.
	 */
	private void publish(TickDriver d) {
		Vec3 exact = BrushWorld.toMinecraft(d.published, anchor).subtract(player.position());
		// A tick that landed reports ground even if auto hop left it again (it then publishes the touchdown): fall damage.
		boolean grounded = d.core.grounded || d.landed;
		Vec3 request = grounded ? new Vec3(exact.x, Math.min(exact.y, 0) - 1e-3, exact.z) : exact;
		Vec3 velocity = minecraftVelocity(d.core.velocity);
		double fall = player.getDeltaMovement().y;
		player.setDeltaMovement(velocity.x, fall, velocity.z);
		SurfPlayer trust = (SurfPlayer) player;
		trust.surfcraft$setTrust(exact);
		try {
			player.move(MoverType.SELF, request);
		} finally {
			trust.surfcraft$setTrust(null);
		}
		Vec3 moved = player.getDeltaMovement();
		boolean bounced = fall < 0 && moved.y > 0;
		if (bounced || moved.x != velocity.x || moved.z != velocity.z) {
			// A bounce leaves the ground as vanilla's jump on slime does: the higher of the jump and the bounce.
			d.core.velocity = sourceVelocity(new Vec3(moved.x, bounced ? Math.max(velocity.y, moved.y) : velocity.y, moved.z));
			d.core.grounded &= !bounced;
			velocity = minecraftVelocity(d.core.velocity);
		}
		player.setDeltaMovement(velocity);
		player.verticalCollision = grounded || d.ceiling;
		player.verticalCollisionBelow = grounded;
		player.minorHorizontalCollision = false;
		// Ramps are not ground (CS:S): only walls count as horizontal collisions, so sprinting along a ramp continues.
		player.setOnGroundWithMovement(grounded, d.wall, exact);
		lastPos = player.position();
		lastVel = velocity;
	}

	/** Vanilla's tiny-velocity cleanup at the start of {@code aiStep}, for players. */
	static Vec3 cleaned(Vec3 m) {
		double x = m.x, y = m.y, z = m.z;
		if (m.horizontalDistanceSqr() < 9.0E-6) {
			x = 0;
			z = 0;
		}
		if (Math.abs(m.y) < 0.003) y = 0;
		return new Vec3(x, y, z);
	}

	/** deltaMovement (blocks/tick) to a Source velocity (units/s), stored as float like Source. */
	public static V3 sourceVelocity(Vec3 v) {
		return new V3(v.x, -v.z, v.y).scale(1 / UNITS_TO_BLOCKS_PER_TICK).toFloat();
	}

	/** A Source velocity (units/s) as deltaMovement (blocks/tick). */
	public static Vec3 minecraftVelocity(V3 v) {
		return new Vec3(v.x(), v.z(), -v.y()).scale(UNITS_TO_BLOCKS_PER_TICK);
	}
}
