package dev.afunk.surfcraft.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/** CS:S collision response (the surf repo's {@code source-move.ts}): clipping, TryPlayerMove, stairs, ground adhesion. */
public final class SourceMove {
	/** A player trace: the hull's centre from start to end. */
	public interface Trace extends BiFunction<V3, V3, HullTrace> {
	}

	public record SlideMove(V3 position, V3 velocity, List<HullTrace> contacts) {
	}

	private SourceMove() {
	}

	/**
	 * CS:S's ClipVelocity (server_srv.so build 11003710, overbounce 1, no redirect), in its float operations and order:
	 * the part of the velocity into the plane is removed and 1/32 unit/s is added along the normal, so velocity already
	 * leaving the plane keeps its outward part (plus 1/32); then a result still moving into the plane is pushed back
	 * onto it.
	 */
	public static V3 clipVelocity(V3 velocity, V3 normal) {
		float x = (float) velocity.x(), y = (float) velocity.y(), z = (float) velocity.z();
		float nx = (float) normal.x(), ny = (float) normal.y(), nz = (float) normal.z();
		float k = Math.max(-(y * ny + x * nx + nz * z), 0f) + 1f / 32;
		y += ny * k;
		x += nx * k;
		z += nz * k;
		float adjust = ny * y + nx * x + nz * z;
		if (adjust < 0) {
			x -= nx * adjust;
			y -= ny * adjust;
			z -= nz * adjust;
		}
		return new V3(x, y, z);
	}

	/**
	 * Four-bump TryPlayerMove. Each impact consumes only its share of the tick; later sweeps follow the clipped
	 * velocity, including two-plane creases.
	 */
	public static SlideMove slideMove(V3 start, V3 initial, double dt, boolean grounded, Trace trace) {
		V3 position = start, velocity = initial, original = initial;
		double remaining = dt, allFraction = 0;
		List<V3> planes = new ArrayList<>();
		List<HullTrace> contacts = new ArrayList<>();
		for (int bump = 0; bump < 4 && !velocity.isZero(); bump++) {
			HullTrace hit = trace.apply(position, position.add(velocity.scale(remaining)));
			allFraction += hit.fraction();
			if (hit.allSolid()) {
				velocity = V3.ZERO;
				break;
			}
			if (hit.fraction() > 0) {
				if (hit.fraction() == 1) {
					HullTrace stuck = trace.apply(hit.end(), hit.end());
					if (stuck.startSolid() || stuck.fraction() != 1) {
						velocity = V3.ZERO;
						break;
					}
				}
				position = hit.end();
				original = velocity;
				planes.clear();
			}
			if (hit.fraction() == 1) break;
			contacts.add(hit);
			remaining -= remaining * hit.fraction();
			if (planes.size() >= 5) {
				velocity = V3.ZERO;
				break;
			}
			planes.add(hit.normal());
			if (planes.size() == 1 && !grounded) {
				// Default sv_bounce is zero, so floors and surf planes use overbounce 1.
				velocity = clipVelocity(original, hit.normal());
				original = velocity;
			} else {
				boolean clear = false;
				for (int i = 0; i < planes.size() && !clear; i++) {
					velocity = clipVelocity(original, planes.get(i));
					clear = true;
					for (int j = 0; j < planes.size(); j++) if (j != i && !(velocity.dot(planes.get(j)) >= 0)) clear = false;
				}
				if (!clear) {
					if (planes.size() != 2) {
						velocity = V3.ZERO;
						break;
					}
					V3 crease = planes.get(0).cross(planes.get(1)).normalize();
					velocity = crease.scale(crease.dot(velocity));
				}
				if (velocity.dot(initial) <= 0) {
					velocity = V3.ZERO;
					break;
				}
			}
		}
		if (allFraction == 0) velocity = V3.ZERO;
		return new SlideMove(position, velocity, contacts);
	}

	/** Compares a plain slide with an up / slide / down stair attempt. */
	public static SlideMove stepMove(V3 start, V3 velocity, double dt, double stepHeight, Trace trace) {
		SlideMove down = slideMove(start, velocity, dt, true, trace);
		V3 rise = new V3(0, 0, stepHeight + 1.0 / 32);
		HullTrace upTrace = trace.apply(start, start.add(rise));
		SlideMove up = slideMove(!upTrace.startSolid() && !upTrace.allSolid() ? upTrace.end() : start, velocity, dt, true, trace);
		HullTrace landing = trace.apply(up.position(), up.position().sub(rise));
		// A miss leaves a zero normal, so stepping needs a walkable landing.
		if (landing.normal().z() < 0.7) return down;
		V3 position = !landing.startSolid() && !landing.allSolid() ? landing.end() : up.position();
		if (flatDistanceSq(down.position(), start) > flatDistanceSq(position, start)) return down;
		List<HullTrace> contacts = new ArrayList<>(up.contacts());
		contacts.add(landing);
		return new SlideMove(position, new V3(up.velocity().x(), up.velocity().y(), down.velocity().z()), contacts);
	}

	/** Keeps a grounded player on a floor that drops away by up to a step. */
	public static V3 stayOnGround(V3 position, double stepHeight, Trace trace) {
		V3 start = trace.apply(position, position.add(new V3(0, 0, 2))).end();
		HullTrace down = trace.apply(start, position.sub(new V3(0, 0, stepHeight)));
		return down.fraction() > 0 && down.fraction() < 1 && !down.startSolid() && down.normal().z() >= 0.7
				&& Math.abs(position.z() - down.end().z()) > 1.0 / 64 ? down.end() : position;
	}

	private static double flatDistanceSq(V3 p, V3 start) {
		double dx = p.x() - start.x(), dy = p.y() - start.y();
		return dx * dx + dy * dy;
	}
}
