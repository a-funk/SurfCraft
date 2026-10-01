package dev.afunk.surfcraft.physics;

/**
 * A swept-box result: the box centre stops at {@code end}, {@code fraction} of the way. {@code normal} is the
 * outward normal of the plane hit (zero when nothing was entered). {@code startSolid}: the box started inside a
 * brush; {@code allSolid}: it stayed inside that brush the whole way (fraction 0).
 */
public record HullTrace(double fraction, V3 end, V3 normal, boolean startSolid, boolean allSolid) {
}
