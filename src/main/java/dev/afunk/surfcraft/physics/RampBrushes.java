package dev.afunk.surfcraft.physics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Source brushes for placed ramp cells that surf like one brush per ramp: no rampbugs at the cells' seams.
 *
 * <p>Source's box trace (CM_ClipBoxToBrush, ported exactly) has a dead zone about DIST_EPSILON wide at seams, and
 * CS:S rampbugs on multi-brush ramps the same way. With one brush per cell, about one 300-tick surf in six on a
 * 5-block ramp is clipped or stopped mid-ramp. The brushes here keep the true solid but leave no seams where the
 * hull slides:
 * <ul>
 * <li>Identical cells in a row along the ramp's length are one prism, so there are no seams along the length.
 * <li>Across the slope the cells must stay separate (their union need not be convex), and where the hull's
 * contact edge crosses such a seam the cell being left already counts the hull as gone (its leave fraction is
 * pulled back by the epsilon) while the cell being entered reports its internal axial face. So every run of cut
 * cells along one slope also gets a thin slab under the slope with no seams of its own. Slabs come last: as in
 * Source, a later brush wins when its raw entry fraction is below an earlier one's clamped zero. The slab is the
 * only solid added: where the slope crosses the block grid at a corner whose full cell below is missing (a
 * ramp of cut cells only), the cut cells meet in a single point and the slab bridges it with a sliver at most
 * {@link #SLAB} deep under that corner.
 * <li>A full cell whose top-front corner lies on the slope reports its axial faces when the hull rides over that
 * corner within the epsilon. It gets the slope plane as a bevel; the cube lies under that plane, so its expanded
 * shape is unchanged.
 * </ul>
 */
public final class RampBrushes {
	/** Slab thickness under the slope, in blocks (1/8 Source unit): it stays inside the cut cells. */
	static final double SLAB = 0.125 / SourceUnits.PER_BLOCK;

	/** A placed cell: block position, the facing step (fx, fz) the slope descends toward, and its geometry. */
	public record Placed(int x, int y, int z, int fx, int fz, RampCell cell) {
		/** Cell coordinate along the facing, as the block code's {@code continueCut} uses it. */
		int u() {
			return fx > 0 ? x : fx < 0 ? -x - 1 : fz > 0 ? z : -z - 1;
		}

		/** Coordinate across the facing: along the ramp's length. */
		int w() {
			return fx != 0 ? z : x;
		}

		/** The slope's constant: every cell on one plane shares {@code cut + p*u + q*y}. */
		long plane() {
			return cell.cut() + (long) cell.p() * u() + (long) cell.q() * y;
		}
	}

	/** One slope: the facing, p:q and its plane {@code p*u + q*y <= plane} in cell units. */
	private record Slope(int fx, int fz, int p, int q, long plane) {
	}

	/** A prism along w: a cell at (u, y), or (cut 0) a slab over the slope's u-span [from, to]. */
	private record Piece(Slope slope, int cut, int u, int y, boolean bevel, double from, double to) {
	}

	private RampBrushes() {
	}

	public static List<Brush> of(List<Placed> cells) {
		// Where the slope crosses each cut cell (a span in u), per slope and slice across it.
		Map<Slope, Map<Integer, List<double[]>>> spans = new LinkedHashMap<>();
		for (Placed c : cells) {
			RampCell r = c.cell();
			if (r.full()) continue;
			long plane = c.plane();
			double from = Math.max(c.u(), (double) (plane - (long) r.q() * (c.y() + 1)) / r.p()), to = Math.min(c.u() + 1, (double) (plane - (long) r.q() * c.y()) / r.p());
			spans.computeIfAbsent(slope(c, plane), k -> new TreeMap<>()).computeIfAbsent(c.w(), k -> new ArrayList<>()).add(new double[] {from, to});
		}
		Map<Piece, List<Integer>> pieces = new LinkedHashMap<>();
		for (Placed c : cells) {
			Slope touched = slope(c, c.plane());
			boolean bevel = c.cell().full() && spans.containsKey(touched) && spans.get(touched).containsKey(c.w());
			pieces.computeIfAbsent(new Piece(touched, c.cell().cut(), c.u(), c.y(), bevel, 0, 0), k -> new ArrayList<>()).add(c.w());
		}
		// Slabs after the cells: each maximal run of touching spans in a slice.
		for (Map.Entry<Slope, Map<Integer, List<double[]>>> e : spans.entrySet()) {
			for (Map.Entry<Integer, List<double[]>> slice : e.getValue().entrySet()) {
				List<double[]> s = slice.getValue();
				s.sort((a, b) -> Double.compare(a[0], b[0]));
				double from = s.getFirst()[0], to = s.getFirst()[1];
				for (double[] span : s.subList(1, s.size())) {
					if (span[0] > to + 1e-9) {
						pieces.computeIfAbsent(new Piece(e.getKey(), 0, 0, 0, false, from, to), k -> new ArrayList<>()).add(slice.getKey());
						from = span[0];
					}
					to = Math.max(to, span[1]);
				}
				pieces.computeIfAbsent(new Piece(e.getKey(), 0, 0, 0, false, from, to), k -> new ArrayList<>()).add(slice.getKey());
			}
		}
		// Each piece's consecutive slices become one prism.
		List<Brush> out = new ArrayList<>();
		for (Map.Entry<Piece, List<Integer>> e : pieces.entrySet()) {
			List<Integer> w = e.getValue();
			w.sort(null);
			int first = w.getFirst();
			for (int i = 1; i <= w.size(); i++) {
				if (i < w.size() && w.get(i) == w.get(i - 1) + 1) continue;
				out.add(prism(e.getKey(), first, w.get(i - 1) + 1));
				if (i < w.size()) first = w.get(i);
			}
		}
		return out;
	}

	private static Slope slope(Placed c, long plane) {
		return new Slope(c.fx(), c.fz(), c.cell().p(), c.cell().q(), plane);
	}

	/** A piece from w0 to w1, built in ramp axes (u along the facing, y up, w across), as a Source brush. */
	private static Brush prism(Piece k, double w0, double w1) {
		Slope s = k.slope();
		double p = s.p(), q = s.q(), l = Math.hypot(p, q);
		double[] slope = {p / l, q / l, 0, s.plane() / l};
		List<double[]> ramp = new ArrayList<>();
		boolean slab = k.cut() == 0;
		if (slab) {
			// The slope's strip over [from, to], SLAB thick, bounded tightly.
			ramp.addAll(List.of(new double[] {1, 0, 0, k.to()}, new double[] {-1, 0, 0, -k.from()}, new double[] {0, 1, 0, (s.plane() - p * k.from()) / q},
					new double[] {0, -1, 0, -(s.plane() - p * k.to()) / q}));
		} else {
			// The cell's tight bounds, as RampCell.localPlanes.
			RampCell cell = new RampCell(s.p(), s.q(), k.cut());
			ramp.addAll(List.of(new double[] {1, 0, 0, k.u() + cell.maxU()}, new double[] {-1, 0, 0, -k.u()}, new double[] {0, 1, 0, k.y() + cell.maxY()},
					new double[] {0, -1, 0, -k.y()}));
		}
		ramp.add(new double[] {0, 0, 1, w1});
		ramp.add(new double[] {0, 0, -1, -w0});
		if (slab || k.bevel() || k.cut() < s.p() + s.q()) ramp.add(slope);
		if (slab) ramp.add(new double[] {-slope[0], -slope[1], 0, SLAB - slope[3]});
		// To Minecraft axes: u is x, -x, z or -z for the facing; w is the other horizontal axis.
		double[][] mc = new double[ramp.size()][];
		for (int i = 0; i < mc.length; i++) {
			double[] r = ramp.get(i);
			mc[i] = s.fx() != 0 ? new double[] {s.fx() * r[0], r[1], r[2], r[3]} : new double[] {r[2], r[1], s.fz() * r[0], r[3]};
		}
		return SourceUnits.blockBrush(mc, 0, 0, 0);
	}
}
