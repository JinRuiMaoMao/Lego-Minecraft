package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides where a {@link LegoModel} goes, the same way a single brick snaps: on top of a LEGO piece the
 * model's bottom sits on that piece's top (plate precision), under it the model's top meets its bottom,
 * against a side it starts at the same height. A corner of the whole model's footprint lands on the
 * clicked column; see {@link LegoPiecePlacer#cornerOrigins} for the order positions are tried in. Heights
 * at which the model's studs / receptors (e.g. of a piece sticking out below it) plug into the LEGO
 * pieces around it are tried first; otherwise the height is set by the pieces standing in that column.
 * Shared by the server (placing) and the client (preview).
 */
public final class LegoModelPlacer {
	/** How far past the model's own height a connection may move it up or down. */
	private static final int SNAP_SLACK_PLATES = 3;
	/** Connecting heights tried per corner position. */
	private static final int MAX_SNAPS = 3;

	private LegoModelPlacer() {
	}

	/** Distinct {@link Plan#orientation()} values. */
	public static final int ORIENTATIONS = 24;
	/** Sideways placements tried per click. */
	private static final int MAX_SIDEWAYS = 12;

	/**
	 * @param rotated     the model turned (and possibly stood on its side), minimum corner at (0, 0, 0)
	 * @param orientation which turn gave {@code rotated}: its up direction × 4 + clockwise quarter turns
	 * @param ux          offset of {@code rotated} in {@link LegoPiece} units, likewise {@code uy}, {@code uz}
	 */
	public record Plan(LegoModel rotated, int orientation, int ux, int uy, int uz) {
		public LegoModel placed() {
			return rotated.translated(ux, uy, uz);
		}

		/** The block holding the placed model's minimum corner. */
		public BlockPos base() {
			return new BlockPos(Math.floorDiv(ux, LegoPiece.UNITS_X), Math.floorDiv(uy, LegoPiece.UNITS_Y),
				Math.floorDiv(uz, LegoPiece.UNITS_X));
		}

		/** The offset in blocks. */
		public double[] offsetBlocks() {
			return new double[] {(double) ux / LegoPiece.UNITS_X, (double) uy / LegoPiece.UNITS_Y, (double) uz / LegoPiece.UNITS_X};
		}
	}

	private static int orientation(Direction up, int steps) {
		return up.ordinal() * 4 + steps;
	}

	public static List<Plan> plans(Level level, Direction look, BlockHitResult hit, LegoModel model, int turn) {
		return plans(level, look, hit.getBlockPos(), hit.getDirection(), hit.getLocation(), model, turn);
	}

	/**
	 * Candidate placements, best first; each puts the clicked column under a different part of the model.
	 *
	 * @param turn extra clockwise quarter turns about the model's own up axis
	 */
	public static List<Plan> plans(Level level, Direction look, BlockPos clicked, Direction face, Vec3 hit, LegoModel model, int turn) {
		int steps = Math.floorMod(look.get2DDataValue() - model.facing() + turn, 4);
		LegoModel rotated = model.rotatedClockwise(steps);

		BlockPos col;
		/* Plate height the model meets: its bottom sits on it, or (clicking an underside) its top touches it. */
		int plane;
		LegoStructurePart part = LegoPiecePlacer.findHitPart(level, clicked, hit, face);
		if (part != null && face != Direction.UP && hasStudsFacing(part, face)) {
			List<Plan> sideways = sidewaysPlans(level, look, part, face, hit, model, turn);
			if (!sideways.isEmpty()) {
				return sideways;
			}
		}
		if (part != null) {
			switch (face) {
				case UP -> {
					col = clicked;
					plane = part.topAbsPlates();
				}
				case DOWN -> {
					col = clicked;
					plane = part.bottomAbsPlates();
				}
				default -> {
					col = clicked.relative(face);
					plane = part.bottomAbsPlates();
				}
			}
		} else if (level.getBlockState(clicked).canBeReplaced() && MultipartUtil.get(level, clicked) == null) {
			col = clicked;
			plane = clicked.getY() * 3;
			face = Direction.UP;
		} else {
			switch (face) {
				case UP -> {
					col = clicked;
					plane = (int) Math.ceil(hit.y * 3 - 1.0E-3);
				}
				case DOWN -> {
					col = clicked;
					plane = (int) Math.floor(hit.y * 3 + 1.0E-3);
				}
				default -> {
					col = clicked.relative(face);
					plane = clicked.getY() * 3;
				}
			}
		}

		boolean under = face == Direction.DOWN;
		int[] size = rotated.dimensions();
		List<int[]> origins = LegoPiecePlacer.cornerOrigins(col.getX(), col.getZ(), size[0], size[2], face, look);
		int[] bases = new int[origins.size()];
		for (int i = 0; i < bases.length; i++) {
			int[] o = origins.get(i);
			int[] c = rotated.column(col.getX() - o[0], col.getZ() - o[1]);
			int meet = under
				? (c != null ? c[1] : rotated.bottomPlates() + rotated.heightPlates())
				: (c != null ? c[0] : rotated.bottomPlates());
			bases[i] = plane - meet;
		}

		int reach = rotated.heightPlates() + SNAP_SLACK_PLATES;
		Map<ConnectorKey, List<Double>> world = worldConnectors(level, rotated, origins, bases, reach);
		Set<Plan> out = new LinkedHashSet<>();
		for (int i = 0; i < bases.length; i++) {
			int[] o = origins.get(i);
			for (int plates : snaps(rotated, world, o[0], o[1], bases[i], reach)) {
				out.add(upright(rotated, steps, o[0], plates, o[1]));
			}
		}
		for (int i = 0; i < bases.length; i++) {
			int[] o = origins.get(i);
			out.add(upright(rotated, steps, o[0], bases[i], o[1]));
		}
		return new ArrayList<>(out);
	}

	private static Plan upright(LegoModel rotated, int steps, int dx, int plates, int dz) {
		return new Plan(rotated, orientation(Direction.UP, steps),
			dx * LegoPiece.UNITS_X, plates * LegoPiece.PLATE_UNITS, dz * LegoPiece.UNITS_X);
	}

	private static boolean hasStudsFacing(LegoStructurePart part, Direction face) {
		for (LegoPiece.Stud s : part.studs()) {
			if (s.dir() == face) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The model stood on its side (or turned over) so its bottom faces the clicked studs, which point
	 * sideways or down: one of the receptors on its bottom face lands on the clicked stud. Placements
	 * plugging into the most studs of that face come first, then those growing up (or away from the
	 * player) and to the player's right from the clicked stud. Empty when the model cannot be turned
	 * (Technic parts) or no stud is under the hit.
	 */
	private static List<Plan> sidewaysPlans(
		Level level, Direction look, LegoStructurePart part, Direction face, Vec3 hit, LegoModel model, int turn
	) {
		double[] h = {hit.x * LegoPiece.PX, hit.y * LegoPiece.PX, hit.z * LegoPiece.PX};
		LegoPiece.Stud target = LegoPiecePlacer.nearest(part.studs(), face, h);
		if (target == null) {
			return List.of();
		}
		Direction forward = face.getAxis().isHorizontal() ? face.getOpposite() : look;
		int steps = Math.floorMod(forward.get2DDataValue() - model.facing() + turn, 4);
		LegoModel turned = model.rotatedClockwise(steps).tipped(face, forward);
		if (turned == null) {
			return List.of();
		}

		int n = face.getAxis().ordinal();
		int sign = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
		List<double[]> bottom = new ArrayList<>();
		double low = Double.MAX_VALUE;
		for (LegoModel.Connector c : turned.connectors()) {
			LegoPiece.Stud s = c.at();
			if (!c.stud() && s.dir() == face) {
				double[] p = {s.x(), s.y(), s.z()};
				if (sign * p[n] < low - 1.0E-3) {
					low = sign * p[n];
					bottom.clear();
				}
				if (Math.abs(sign * p[n] - low) < 1.0E-3) {
					bottom.add(p);
				}
			}
		}
		if (bottom.isEmpty()) {
			return List.of();
		}

		double[] t = {target.x(), target.y(), target.z()};
		Set<List<Long>> studs = studsOnPlane(level, face, t, turned.dimensions());
		Direction a = face.getAxis().isHorizontal() ? Direction.UP : forward;
		Direction b = forward.getClockWise();
		record Scored(int[] units, int votes, double behind) {
		}
		List<Scored> scored = new ArrayList<>();
		Set<List<Integer>> seen = new HashSet<>();
		for (double[] r : bottom) {
			int[] u = new int[3];
			boolean onGrid = true;
			for (int k = 0; k < 3; k++) {
				double v = (t[k] - r[k]) / LegoPiece.unitPx(k);
				u[k] = (int) Math.round(v);
				onGrid &= Math.abs(v - u[k]) < 0.02;
			}
			if (!onGrid || !seen.add(List.of(u[0], u[1], u[2]))) {
				continue;
			}
			int votes = 0;
			double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
			double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
			for (double[] q : bottom) {
				double[] p = new double[3];
				for (int k = 0; k < 3; k++) {
					p[k] = q[k] + u[k] * LegoPiece.unitPx(k);
					lo[k] = Math.min(lo[k], p[k]);
					hi[k] = Math.max(hi[k], p[k]);
				}
				if (studs.contains(key(p))) {
					votes++;
				}
			}
			scored.add(new Scored(u, votes, behind(lo, hi, t, a) + behind(lo, hi, t, b)));
		}
		scored.sort(Comparator.comparingInt((Scored s) -> -s.votes()).thenComparingDouble(Scored::behind));
		List<Plan> out = new ArrayList<>();
		int orientation = orientation(face, steps);
		for (Scored s : scored.subList(0, Math.min(MAX_SIDEWAYS, scored.size()))) {
			out.add(new Plan(turned, orientation, s.units()[0], s.units()[1], s.units()[2]));
		}
		return out;
	}

	/** How far (px) the span lo..hi reaches behind the target point against direction {@code d}. */
	private static double behind(double[] lo, double[] hi, double[] t, Direction d) {
		int k = d.getAxis().ordinal();
		return d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? t[k] - lo[k] : hi[k] - t[k];
	}

	private static List<Long> key(double[] p) {
		return List.of(Math.round(p[0] * 3), Math.round(p[1] * 3), Math.round(p[2] * 3));
	}

	/**
	 * Studs pointing {@code face} on the plane through {@code t}, of the pieces in the layer of cells just
	 * behind that plane, as far around {@code t} as a model of size {@code dims} (blocks) can reach.
	 */
	private static Set<List<Long>> studsOnPlane(Level level, Direction face, double[] t, int[] dims) {
		int n = face.getAxis().ordinal();
		int behindCell = (int) Math.floor((t[n] - face.getAxisDirection().getStep() * 0.5) / LegoPiece.PX);
		int reach = Math.max(dims[0], Math.max(dims[1], dims[2])) + 1;
		int[] c = new int[3];
		for (int k = 0; k < 3; k++) {
			c[k] = (int) Math.floor(t[k] / LegoPiece.PX);
		}
		Set<List<Long>> out = new HashSet<>();
		Set<Object> seenParts = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int p = (n + 1) % 3;
		int q = (n + 2) % 3;
		int[] cell = new int[3];
		for (int i = -reach; i <= reach; i++) {
			for (int j = -reach; j <= reach; j++) {
				cell[n] = behindCell;
				cell[p] = c[p] + i;
				cell[q] = c[q] + j;
				MultipartContainer container = MultipartUtil.get(level, pos.set(cell[0], cell[1], cell[2]));
				if (container == null) {
					continue;
				}
				for (LegoStructurePart part : container.getParts(LegoStructurePart.class)) {
					if (!seenParts.add(part.pieceKey())) {
						continue;
					}
					for (LegoPiece.Stud s : part.studs()) {
						double[] sp = {s.x(), s.y(), s.z()};
						if (s.dir() == face && Math.abs(sp[n] - t[n]) < 1.0E-3) {
							out.add(key(sp));
						}
					}
				}
			}
		}
		return out;
	}

	/** Where a connector sits horizontally and which way it points; y is solved for. */
	private record ConnectorKey(long x, long z, Direction dir, boolean stud) {
		static ConnectorKey of(double x, double z, Direction dir, boolean stud) {
			return new ConnectorKey(Math.round(x * 3), Math.round(z * 3), dir, stud);
		}
	}

	/** Studs and receptors of the placed LEGO pieces around every candidate position, by {@link ConnectorKey}. */
	private static Map<ConnectorKey, List<Double>> worldConnectors(
		Level level, LegoModel rotated, List<int[]> origins, int[] bases, int reach
	) {
		Map<ConnectorKey, List<Double>> out = new HashMap<>();
		if (rotated.connectors().isEmpty() || origins.isEmpty()) {
			return out;
		}
		int[] size = rotated.dimensions();
		int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
		int p0 = Integer.MAX_VALUE, p1 = Integer.MIN_VALUE;
		for (int i = 0; i < bases.length; i++) {
			int[] o = origins.get(i);
			x0 = Math.min(x0, o[0] - 1);
			x1 = Math.max(x1, o[0] + size[0]);
			z0 = Math.min(z0, o[1] - 1);
			z1 = Math.max(z1, o[1] + size[2]);
			p0 = Math.min(p0, bases[i] + rotated.bottomPlates() - reach);
			p1 = Math.max(p1, bases[i] + rotated.bottomPlates() + rotated.heightPlates() + reach);
		}
		Set<Object> seen = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = Math.floorDiv(p0, 3) - 1; y <= Math.floorDiv(p1, 3) + 1; y++) {
					MultipartContainer container = MultipartUtil.get(level, pos.set(x, y, z));
					if (container == null) {
						continue;
					}
					for (LegoStructurePart part : container.getParts(LegoStructurePart.class)) {
						if (!seen.add(part.pieceKey())) {
							continue;
						}
						for (LegoPiece.Stud s : part.studs()) {
							out.computeIfAbsent(ConnectorKey.of(s.x(), s.z(), s.dir(), true), k -> new ArrayList<>()).add(s.y());
						}
						for (LegoPiece.Stud s : part.receptors()) {
							out.computeIfAbsent(ConnectorKey.of(s.x(), s.z(), s.dir(), false), k -> new ArrayList<>()).add(s.y());
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * Heights (plates) within {@code reach} of {@code base} at which the model, its minimum corner at block
	 * column (ox, oz), plugs into the surrounding LEGO pieces; most connections first, then nearest.
	 */
	private static List<Integer> snaps(LegoModel rotated, Map<ConnectorKey, List<Double>> world, int ox, int oz, int base, int reach) {
		if (world.isEmpty()) {
			return List.of();
		}
		Map<Integer, Integer> votes = new HashMap<>();
		for (LegoModel.Connector c : rotated.connectors()) {
			LegoPiece.Stud s = c.at();
			List<Double> ys = world.get(ConnectorKey.of(s.x() + ox * 16, s.z() + oz * 16, s.dir(), !c.stud()));
			if (ys == null) {
				continue;
			}
			for (double y : ys) {
				double plates = (y - s.y()) * 3 / 16;
				long p = Math.round(plates);
				if (Math.abs(plates - p) < 1.0E-3 && Math.abs(p - base) <= reach) {
					votes.merge((int) p, 1, Integer::sum);
				}
			}
		}
		List<Integer> out = new ArrayList<>(votes.keySet());
		out.sort(Comparator.<Integer>comparingInt(p -> -votes.get(p)).thenComparingInt(p -> Math.abs(p - base)));
		return out.size() > MAX_SNAPS ? out.subList(0, MAX_SNAPS) : out;
	}

	/** Index of the first plan that fits right now, or -1. */
	public static int firstFitting(Level level, List<Plan> plans) {
		for (int i = 0; i < plans.size(); i++) {
			if (plans.get(i).placed().canPlace(level)) {
				return i;
			}
		}
		return -1;
	}
}
