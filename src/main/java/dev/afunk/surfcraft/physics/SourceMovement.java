// Contains routines derived from Valve's Source SDK 2013 game movement: see THIRD_PARTY_NOTICES.md (Source 1 SDK License, free of charge).
package dev.afunk.surfcraft.physics;

import java.util.List;

/**
 * CS:S walking and air movement for one player, ported from the surf repo's {@code SourcePlayer.move} (the
 * {@code cssMovement} path without duck, ladders, water, noclip, base velocity, moving ground, materials,
 * gravity scale or lagged movement) plus CS:S's quadrant ground check, which that port lacks, in Source units and axes.
 * One {@link #tick} is one user command.
 */
public final class SourceMovement {
	/** Walkable ground: normal z at least this (and rising at most 140 units/s). */
	public static final double GROUND_NORMAL_Z = 0.7;

	/** Feet (bottom centre of the hull), stored as float like Source's origin; set it with {@link #setOrigin}. */
	public V3 origin = V3.ZERO;
	/** Stored as float at the end of each tick. */
	public V3 velocity = V3.ZERO;
	public boolean grounded;
	/** Remaining jump stamina recovery, in milliseconds (m_flStamina). */
	public float stamina;
	/** 0.25 while airborne and rising at up to 140 units/s, else 1; scales the next tick's acceleration. */
	public float surfaceFriction = 1;
	/** Jump was held last tick: without auto hop a jump needs a fresh press. */
	public boolean jumpHeld;
	/** Last surf-ramp contact this tick (0.01 < normal z < 0.7), or null. */
	public V3 surfNormal;
	/** This tick's collision contacts in order, empty when the move was unobstructed. */
	public List<HullTrace> contacts = List.of();
	/** This tick's ground probe: the whole hull swept 2 units down from where it ended (the quarter boxes' are not kept). */
	public HullTrace ground;

	public void setOrigin(V3 feet) {
		origin = feet.toFloat();
	}

	/**
	 * Runs one command. {@code forwardMove}/{@code sideMove} are command units (+-400 at full key press, side
	 * positive to the right), {@code yawDegrees} Source yaw. The hull's centre is {@code origin + (0, 0, hullHalf.z)}.
	 */
	public void tick(Config c, double forwardMove, double sideMove, double yawDegrees, boolean jump, double dt, V3 hullHalf, List<Brush> world) {
		SourceMove.Trace trace = (from, to) -> SourceHull.trace(world, from, to, hullHalf);
		double gravity = c.gravity();
		stamina = Math.max(0, stamina - (float) (dt * 1000));
		// PlayerMove (sv_optimizedmovement) leaves the ground above 250 units/s.
		if (grounded && velocity.z() > 250) grounded = false;
		surfNormal = null;

		// The reference builds forward/right from yaw - 90 degrees in its own axes; these are the same doubles.
		double yaw = (yawDegrees - 90) * Math.PI / 180, sin = StrictMath.sin(yaw), cos = StrictMath.cos(yaw);
		double f = Math.clamp(forwardMove / 400, -1.0, 1.0), r = Math.clamp(sideMove / 400, -1.0, 1.0);
		V3 rawWish = new V3(-sin * f + cos * r, cos * f + sin * r, 0);
		V3 direction = rawWish.normalize();
		// CheckParameters caps the command (up to 400 units/s) at the player speed: a partial command is not a
		// fraction of that cap.
		double speed = Math.min(400 * rawWish.length(), c.speed());
		boolean jumping = jump && (!jumpHeld || c.autoBunnyHop());
		jumpHeld = jump;

		// StartGravity runs before CheckJumpButton; FinishGravity after the move.
		double vx = velocity.x(), vy = velocity.y(), vz = velocity.z() - gravity * dt / 2;
		if (grounded) {
			if (jumping) {
				double length = Math.sqrt(vx * vx + vy * vy + vz * vz);
				if (c.bunnyHopSpeedLimit() > 0 && length > c.bunnyHopSpeedLimit()) {
					double s = c.bunnyHopSpeedLimit() / length;
					vx *= s;
					vy *= s;
					vz *= s;
				}
				// A standing jump adds its impulse to the half-step; stamina scales it.
				vz = (vz + c.jumpSpeed()) * (1 - stamina * 0.00019) - gravity * dt / 2;
				stamina = (float) (25000.0 / 19);
				grounded = false;
			} else {
				vz = 0;
				double flat = Math.sqrt(vx * vx + vy * vy);
				if (flat < 1e-8 / 0.0254) {
					vx = 0;
					vy = 0;
				} else {
					double friction = c.friction() * surfaceFriction;
					double ratio = Math.max(0, flat - Math.max(flat, c.stopSpeed()) * friction * dt) / flat;
					vx *= ratio;
					vy *= ratio;
				}
				if (stamina > 0) {
					double ratio = StrictMath.pow(1 - stamina * 0.00019, dt * 70);
					vx *= ratio;
					vy *= ratio;
				}
			}
		}
		V3 v = new V3(vx, vy, vz);
		v = grounded
				? accelerate(v, direction, speed, c.accelerate() * surfaceFriction, dt, speed, false)
				// Legacy air acceleration: the gain is scaled by the uncapped wish speed, the wish capped at 30.
				: accelerate(v, direction, speed, c.airAccelerate() * surfaceFriction, dt, Math.min(c.airWishCap(), speed), true);
		if (grounded && v.length() < 1) v = V3.ZERO;
		double max = c.maxVelocity();
		velocity = new V3(Math.clamp(v.x(), -max, max), Math.clamp(v.y(), -max, max), Math.clamp(v.z(), -max, max));

		boolean wasGrounded = grounded;
		V3 movement = velocity.scale(dt), start = origin.add(new V3(0, 0, hullHalf.z())), next;
		contacts = List.of();
		HullTrace direct = trace.apply(start, start.add(movement));
		if (direct.fraction() == 1 && !direct.startSolid()) next = direct.end();
		else {
			SourceMove.SlideMove result = wasGrounded
					? SourceMove.stepMove(start, movement.scale(1 / dt), dt, c.stepHeight(), trace)
					: SourceMove.slideMove(start, movement.scale(1 / dt), dt, false, trace);
			next = result.position();
			velocity = result.velocity();
			contacts = List.copyOf(result.contacts());
		}
		if (wasGrounded && (movement.x() != 0 || movement.y() != 0)) next = SourceMove.stayOnGround(next, c.stepHeight(), trace);

		ground = trace.apply(next, next.sub(new V3(0, 0, 2)));
		grounded = velocity.z() <= 140 && (walkable(ground) || quadrantGround(world, next, hullHalf));
		setOrigin(next.sub(new V3(0, 0, hullHalf.z())));
		// CategorizePosition lowers air control while rising slowly near the apex; fast ascent and falling reset it.
		surfaceFriction = !grounded && velocity.z() > 0 && velocity.z() <= 140 ? 0.25f : 1;
		for (HullTrace hit : contacts) if (hit.normal().z() > 0.01 && hit.normal().z() < GROUND_NORMAL_Z) surfNormal = hit.normal();
		vz = grounded ? 0 : velocity.z() - gravity * dt / 2;
		velocity = new V3(velocity.x(), velocity.y(), vz).toFloat();
	}

	private static boolean walkable(HullTrace t) {
		return t.fraction() < 1 && t.normal().z() >= GROUND_NORMAL_Z;
	}

	/**
	 * TryTouchGroundInQuadrants (CategorizePosition, when the whole hull's probe finds no walkable ground): each quarter of
	 * the hull, in CS:S's order (-x -y, +x +y, -x +y, +x -y), probes the same 2 units down; the first walkable hit grounds
	 * the player. At a ramp's toe it finds the floor under the down-slope quarter while the hull's back edge still rides
	 * the slope.
	 */
	private static boolean quadrantGround(List<Brush> world, V3 centre, V3 half) {
		V3 quarter = new V3(half.x() / 2, half.y() / 2, half.z());
		for (double[] q : new double[][] {{-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
			V3 c = centre.add(new V3(q[0] * quarter.x(), q[1] * quarter.y(), 0));
			if (walkable(SourceHull.trace(world, c, c.sub(new V3(0, 0, 2)), quarter))) return true;
		}
		return false;
	}

	/** Accelerates only the component along the wish direction, keeping perpendicular momentum. */
	static V3 accelerate(V3 velocity, V3 direction, double wishSpeed, double acceleration, double dt, double cap, boolean legacy) {
		double remaining = cap - velocity.dot(direction);
		if (remaining <= 0) return velocity;
		return velocity.add(direction.scale(Math.min(remaining, acceleration * (legacy ? wishSpeed : cap) * dt)));
	}
}
