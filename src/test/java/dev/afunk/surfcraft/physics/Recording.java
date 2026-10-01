package dev.afunk.surfcraft.physics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A native CS:S recording (the surf repo's {@code tests/fixtures/css-reference}): before/after rows per tick and
 * the server settings in its header, replayed the way the surf repo's {@code css-reference.test.ts} does.
 */
record Recording(String name, List<Map<String, String>> rows, boolean autoBunnyHop, boolean speed260) {
	static final double DT = 0.015f;
	/** The CS:S standing hull, 32x32x62. */
	static final V3 HULL = new V3(16, 16, 31);
	static final double POSITION_TOLERANCE = 0.05, VELOCITY_TOLERANCE = 0.002, STAMINA_TOLERANCE = 0.001;

	static Recording load(String name, boolean crlf) throws IOException {
		String text;
		try (InputStream in = Recording.class.getResourceAsStream("/css-reference/" + name + ".csv")) {
			if (in == null) throw new IOException("missing recording " + name);
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		if (crlf) text = text.replaceAll("\r?\n", "\r\n");
		List<String> lines = text.lines().filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
		String[] columns = lines.getFirst().split(",");
		List<Map<String, String>> rows = new ArrayList<>();
		for (String line : lines.subList(1, lines.size())) {
			String[] values = line.split(",");
			Map<String, String> row = new HashMap<>();
			for (int i = 0; i < columns.length; i++) row.put(columns[i], values[i]);
			rows.add(row);
		}
		List<String> header = text.lines().filter(line -> line.startsWith("#")).toList();
		return new Recording(name, rows, header.contains("# sv_autobunnyhopping=1"),
				header.contains("# weapon_profile=scout") || header.contains("# weapon_profile=unarmed"));
	}

	double num(int row, String column) {
		return Double.parseDouble(rows.get(row).get(column));
	}

	/** Worst per-axis errors over the replay, the ticks with surf contact, and the first tick out of tolerance. */
	record Result(String name, int ticks, double position, double velocity, double stamina, int surfTicks, String firstFailure) {
		@Override
		public String toString() {
			return String.format("%-22s %4d ticks  worst position %.6f  velocity %.6f  stamina %.6f  surf ticks %3d%s", name, ticks, position,
					velocity, stamina, surfTicks, firstFailure == null ? "" : "\n    FIRST MISMATCH " + firstFailure);
		}
	}

	/** Replays every command from the first row's state, with no corrections, against {@code world}. */
	Result replay(List<Brush> world) {
		Config c = (name.startsWith("surf") ? Config.CSS_SURF : Config.CSS).withAutoBunnyHop(autoBunnyHop);
		if (speed260) c = c.withSpeed(260);
		SourceMovement m = new SourceMovement();
		m.setOrigin(new V3(num(0, "x"), num(0, "y"), num(0, "z")));
		m.velocity = new V3(num(0, "vx"), num(0, "vy"), num(0, "vz"));
		m.grounded = ((int) num(0, "flags") & 1) != 0;
		m.stamina = (float) num(0, "stamina");
		double position = 0, velocity = 0, stamina = 0;
		int surfTicks = 0;
		String failure = null;
		for (int i = 0; i < rows.size(); i += 2) {
			int after = i + 1;
			if (!"before".equals(rows.get(i).get("phase")) || !"after".equals(rows.get(after).get("phase")) || num(i, "frame") != num(after, "frame"))
				throw new IllegalStateException(name + ": unpaired row " + i);
			int buttons = (int) num(i, "buttons");
			if ((buttons & 4) != 0 || num(i, "ducked") != 0 || num(after, "ducked") != 0) throw new IllegalStateException(name + " ducks; duck is not ported");
			m.tick(c, num(i, "forward"), num(i, "side"), num(i, "yaw"), (buttons & 2) != 0, DT, HULL, world);
			if (m.surfNormal != null) surfTicks++;
			double dp = Math.max(Math.abs(m.origin.x() - num(after, "x")), Math.max(Math.abs(m.origin.y() - num(after, "y")), Math.abs(m.origin.z() - num(after, "z"))));
			double dv = Math.max(Math.abs(m.velocity.x() - num(after, "vx")), Math.max(Math.abs(m.velocity.y() - num(after, "vy")), Math.abs(m.velocity.z() - num(after, "vz"))));
			double ds = Math.abs(m.stamina - num(after, "stamina"));
			boolean ground = ((int) num(after, "flags") & 1) != 0;
			position = Math.max(position, dp);
			velocity = Math.max(velocity, dv);
			stamina = Math.max(stamina, ds);
			if (failure == null && (!(dp < POSITION_TOLERANCE) || !(dv < VELOCITY_TOLERANCE) || !(ds < STAMINA_TOLERANCE) || m.grounded != ground))
				failure = String.format("tick %d: origin %s expected (%s, %s, %s); velocity %s expected (%s, %s, %s); grounded %s expected %s; stamina %s expected %s; contacts %s",
						(int) num(i, "frame"), m.origin, rows.get(after).get("x"), rows.get(after).get("y"), rows.get(after).get("z"), m.velocity,
						rows.get(after).get("vx"), rows.get(after).get("vy"), rows.get(after).get("vz"), m.grounded, ground, m.stamina,
						rows.get(after).get("stamina"), m.contacts);
		}
		return new Result(name, rows.size() / 2, position, velocity, stamina, surfTicks, failure);
	}
}
