package dev.afunk.surfcraft.physics;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Replays the CS:S collision recordings made on surf_kitsune (an oblique surf ramp, walls and a corner) against
 * that map's brushes. The geometry is the user's own map, extracted into the gitignored {@code local-content/}
 * by {@code node tools/extract-kitsune-brushes.mjs}; without it these cases skip.
 *
 * <p>The ramp recordings touch only world brush 1039 (the ramp's player clip). The wall recordings also hit
 * {@code func_brush *57} (solidbsp 0), which collides through the hull compiled into the BSP's physics lump, not
 * through its brush planes: with the raw planes the player stops exactly 0.5 units early (wall-run tick 35, the
 * glides tick 6). Read with the surf repo's PHY reader, each of *57's four compiled convexes is its brush with
 * every face 0.5 units inside (to 0.00001 units), so that is the geometry used here. It is not a general
 * VPhysics rule: 14 of surf_kitsune's 58 func_brush brushes compile differently.
 */
class KitsuneReplayTest {
	static final Path DIR = Path.of("local-content/css-reference");
	/** func_brush *57's compiled hull: its brushes shrunk by this much on every face (checked against the lump). */
	static final double HULL_57_INSET = 0.5;

	@ParameterizedTest
	@ValueSource(strings = {"vanilla-ramp-glide", "surf-ramp-glide", "vanilla-ramp-strafe", "surf-ramp-strafe"})
	void ramp(String name) throws IOException {
		check(name, "kitsune-ramp.json", false);
	}

	@ParameterizedTest
	@ValueSource(strings = {"vanilla-wall-run", "surf-wall-run", "vanilla-wall-glide", "surf-wall-glide", "vanilla-corner-glide", "surf-corner-glide"})
	void wall(String name) throws IOException {
		check(name, "kitsune-wall.json", true);
	}

	private static void check(String name, String region, boolean withHull57) throws IOException {
		Path file = DIR.resolve(region);
		assumeTrue(Files.exists(file), "no " + file + ": run node tools/extract-kitsune-brushes.mjs");
		Recording.Result result = Recording.load(name, false).replay(brushes(file, withHull57));
		System.out.println(result);
		assertNull(result.firstFailure(), result::toString);
	}

	/** The world (model 0) brushes in BSP order, the reference's tie-break order, then optionally *57's hull. */
	static List<Brush> brushes(Path file, boolean withHull57) throws IOException {
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		List<Brush> brushes = new ArrayList<>();
		for (JsonElement brush : json.getAsJsonArray("brushes")) brushes.add(brush(brush.getAsJsonObject(), 0));
		for (JsonElement entity : json.getAsJsonArray("entityBrushes"))
			if (withHull57 && "*57".equals(entity.getAsJsonObject().get("model").getAsString()))
				for (JsonElement brush : entity.getAsJsonObject().getAsJsonArray("brushes")) brushes.add(brush(brush.getAsJsonObject(), HULL_57_INSET));
		return brushes;
	}

	static Brush brush(JsonObject brush, double inset) {
		List<Plane> planes = new ArrayList<>();
		for (JsonElement p : brush.getAsJsonArray("planes")) {
			var a = p.getAsJsonArray();
			planes.add(new Plane(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(), a.get(3).getAsDouble() - inset));
		}
		return Brush.of(planes);
	}
}
