package dev.afunk.surfcraft.physics;

/**
 * CS:S movement settings in Source units (units/s, units/s²). {@code bunnyHopSpeedLimit} caps the whole velocity
 * before a jump's impulse; zero disables it.
 */
public record Config(double gravity, double speed, double accelerate, double airAccelerate, double airWishCap,
		double friction, double stopSpeed, double jumpSpeed, double maxVelocity, double stepHeight,
		boolean autoBunnyHop, double bunnyHopSpeedLimit) {
	/** Vanilla CS:S with the knife, measured on dedicated server build 11003710 (surf repo {@code cssMovement}). */
	public static final Config CSS = new Config(800, 250, 5, 10, 30, 4, 75, (float) Math.sqrt(2 * 800 * 57), 3500, 18, false, 286);
	/** Skill-surf server settings: air acceleration 150, automatic hopping, no hop speed cap ({@code cssSurfMovement}). */
	public static final Config CSS_SURF = CSS.withAirAccelerate(150).withAutoBunnyHop(true).withBunnyHopSpeedLimit(0);

	public Config withSpeed(double v) {
		return new Config(gravity, v, accelerate, airAccelerate, airWishCap, friction, stopSpeed, jumpSpeed, maxVelocity, stepHeight, autoBunnyHop, bunnyHopSpeedLimit);
	}

	public Config withAirAccelerate(double v) {
		return new Config(gravity, speed, accelerate, v, airWishCap, friction, stopSpeed, jumpSpeed, maxVelocity, stepHeight, autoBunnyHop, bunnyHopSpeedLimit);
	}

	public Config withStepHeight(double v) {
		return new Config(gravity, speed, accelerate, airAccelerate, airWishCap, friction, stopSpeed, jumpSpeed, maxVelocity, v, autoBunnyHop, bunnyHopSpeedLimit);
	}

	public Config withAutoBunnyHop(boolean v) {
		return new Config(gravity, speed, accelerate, airAccelerate, airWishCap, friction, stopSpeed, jumpSpeed, maxVelocity, stepHeight, v, bunnyHopSpeedLimit);
	}

	public Config withBunnyHopSpeedLimit(double v) {
		return new Config(gravity, speed, accelerate, airAccelerate, airWishCap, friction, stopSpeed, jumpSpeed, maxVelocity, stepHeight, autoBunnyHop, v);
	}
}
