package com.legominecraft.multipart;

import net.minecraft.core.Direction;

/**
 * A Technic hole (or an axle/pin module) in absolute world pixels (block coordinate × 16).
 *
 * @param cross true for cross-shaped (axle) holes/modules, false for round ones
 */
public record TechnicHole(double x, double y, double z, Direction.Axis axis, boolean cross) {
	public double coord(Direction.Axis a) {
		return switch (a) {
			case X -> x;
			case Y -> y;
			case Z -> z;
		};
	}

	/** Distance from {@code (px, py, pz)} to this hole's axis line. */
	public double radialDistance(double px, double py, double pz) {
		double dx = axis == Direction.Axis.X ? 0 : px - x;
		double dy = axis == Direction.Axis.Y ? 0 : py - y;
		double dz = axis == Direction.Axis.Z ? 0 : pz - z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public boolean sameSpot(TechnicHole other) {
		return axis == other.axis
			&& Math.abs(x - other.x) < 1 && Math.abs(y - other.y) < 1 && Math.abs(z - other.z) < 1;
	}

	/** A hole accepts axles everywhere, but round pins only in round holes. */
	public boolean accepts(boolean crossModule) {
		return crossModule || !cross;
	}
}
