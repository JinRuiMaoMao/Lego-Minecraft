package com.legominecraft.multipart;

import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import com.legominecraft.mesh.BrickMesh;
import com.legominecraft.mesh.Mesh;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Geometry of one placed studded LEGO piece.
 * <p>
 * Scale: 1 stud = 1 block = 16 px wide, 1 brick = 3 plates = 1 block tall. Local piece space has
 * x ∈ [0, studsX·16], z ∈ [0, studsZ·16] (px), y above the piece bottom, with the slope's low edge at
 * local z = 0.
 * <p>
 * Local → world: {@code facing} turns the piece around its own up axis (it is the direction local −z
 * points to while the piece stands upright, the footprint keeping its minimum corner at 0), then the
 * piece is tipped so its up axis points to {@code up} (sideways pieces sit on side studs), then it is
 * offset by (tx, ty, tz) units inside its anchor cell.
 * <p>
 * The world keeps real LEGO proportions: a block is one stud (8 mm) wide but one brick (9.6 mm) tall,
 * so world px are 2 per mm across and 5/3 per mm up. Tipping works in millimetres, which is why a
 * sideways piece is shorter (along world y) and deeper (across) than the same piece upright: a sideways
 * 2×2 brick covers exactly the 2×2 side studs of a bracket. A unit is 4/15 mm along every axis, so
 * 30 units across a block and 36 up it: half plates, sixths of a stud and their sideways counterparts
 * all lie on it, and quarter turns map it onto itself.
 * <p>
 * Shape coordinates are kept in units and only divided by the cell size at the very end, so
 * touching pieces produce bit-identical boundaries and LibMultiPart never sees a false overlap.
 */
public record LegoPiece(LegoPartDefinition def, Direction facing, Direction up, int tx, int ty, int tz) {
	public static final int PX = 16;
	public static final double PLATE_PX = PX / 3.0;
	/** Units per block along x, y, z. */
	public static final int UNITS_X = 30;
	public static final int UNITS_Y = 36;
	public static final int PLATE_UNITS = UNITS_Y / 3;
	private static final int[] CELL_UNITS = {UNITS_X, UNITS_Y, UNITS_X};
	/** Fittings (bars, clips) are given in piece-local sixths of a stud (half a plate up). */
	public static final double FIT_PX = PX / 6.0;
	/** World px per mm across over px per mm up (1.2): how much longer a span is lying than standing. */
	public static final double ACROSS_PER_UP = 1.2;
	/** Collision steps under a curved slope's top, per stud of depth. */
	private static final int CURVE_BOXES_PER_ROW = 2;
	private static final double CURVE_LIP_PLATES = 0.5;
	/** Technic brick hole centre above the brick bottom: 5.8 mm of 9.6 mm → 29/3 px. */
	public static final double SIDE_HOLE_Y = 29.0 / 3.0;
	/** Half-size of the square cut-out left for pins/axles in collision shapes. */
	public static final double HOLE_HALF = 4.8;
	public static final double STUD_R = 4.8;
	/** A shaped part grips a stud below when the stud centre lies this far inside its outline. */
	private static final double RECEPTOR_MARGIN = 3.0;
	private static final double CONNECT_EPS = 0.05;
	/** Collision half-size of a bar relative to its drawn radius. */
	private static final double BAR_BOX_SCALE = 8.0 / 9.0;
	/** Clip ring collision: hole and outer half-sizes, in units. */
	public static final double CLIP_HOLE = 1.25;
	public static final double CLIP_OUTER = 2.0;
	/**
	 * Mesh poking less than this (px, on any axis) into a cell outside the collision boxes is clipped off;
	 * more takes the cell as a fragment shaped like that mesh (an empty-shaped part can't be placed).
	 */
	private static final double MESH_CELL_MIN_PX = 1.0;
	private static final Map<LegoPiece, Placed> PLACED = new ConcurrentHashMap<>();

	/** y0/y1 are in plates above the piece bottom. */
	public record LocalBox(double x0, double x1, double z0, double z1, double y0, double y1) {
	}

	/** Hole centre in piece-local px (y above the piece bottom); vertical holes go through plates. */
	public record LocalHole(double x, double y, double z, boolean vertical) {
	}

	/**
	 * A stud, or a receptor (the anti-stud under a piece), at its base centre in absolute world px.
	 * A stud pointing in {@code dir} plugs into a receptor with the same position and direction.
	 */
	public record Stud(double x, double y, double z, Direction dir) {
		public boolean matches(Stud o) {
			return dir == o.dir && Math.abs(x - o.x) < CONNECT_EPS && Math.abs(y - o.y) < CONNECT_EPS
				&& Math.abs(z - o.z) < CONNECT_EPS;
		}
	}

	/**
	 * @param boxes     collision boxes {x0, y0, z0, x1, y1, z1} in units relative to the anchor cell
	 * @param raws      mesh in px relative to the anchor cell
	 * @param fragments occupied cell offsets, lowest first
	 */
	private record Placed(List<double[]> boxes, List<Mesh.Raw> raws, List<int[]> fragments, double minY, double maxY) {
	}

	/** Studs up, {@code bottomOffset} plates above the anchor cell's floor. */
	public static LegoPiece upright(LegoPartDefinition def, Direction facing, int bottomOffset) {
		return new LegoPiece(def, facing, Direction.UP, 0, bottomOffset * PLATE_UNITS, 0);
	}

	/** Units per block along world axis {@code axis} (0 = x, 1 = y, 2 = z). */
	public static int cellUnits(int axis) {
		return CELL_UNITS[axis];
	}

	/** Size of one unit in px along world axis {@code axis}. */
	public static double unitPx(int axis) {
		return (double) PX / CELL_UNITS[axis];
	}

	/** World px → millimetres. */
	private static double[] mm(double[] px) {
		return new double[] {px[0] / 2, px[1] * 0.6, px[2] / 2};
	}

	/** Millimetres → world px. */
	private static double[] pxOf(double[] mm) {
		return new double[] {mm[0] * 2, mm[1] / 0.6, mm[2] * 2};
	}

	public int widthPx() {
		return def.studsX() * PX;
	}

	public int depthPx() {
		return def.studsZ() * PX;
	}

	public int heightPlates() {
		return def.heightPlates();
	}

	/** Local z where the sloped face ends; studded slopes keep a flat stud row at the back. */
	public double slopeEndPx() {
		return def.kind() == LegoPartType.CHEESE_SLOPE ? depthPx() : depthPx() - PX;
	}

	/**
	 * Profile of a curved slope's top as LDraw 15068 draws it (a sheared 1/8 cylinder): {depth, height}
	 * fractions from the lip (depth 0, height 1/4) up to the flat back edge (depth 1, height 1), in
	 * {@code segments} steps.
	 */
	public static double[][] curveProfile(int segments) {
		double[][] out = new double[segments + 1][];
		for (int i = 0; i <= segments; i++) {
			double a = Math.PI / 4 * i / segments;
			double depth = 1 + Math.sin(a) - Math.cos(a);
			double height = (28.9719 * (Math.cos(a) + Math.sin(a)) - 24.972) / 16;
			out[i] = new double[] {Math.max(0, Math.min(1, depth)), Math.max(0, Math.min(1, height))};
		}
		return out;
	}

	/**
	 * Height in plates of a curved slope's top at depth fraction {@code depth}: the real parts all keep a
	 * half-plate lip, the curve stretching over the rest of the height.
	 */
	public static double curvePlates(double depth, double heightPlates) {
		return CURVE_LIP_PLATES + (heightPlates - CURVE_LIP_PLATES) * (curveHeightAt(depth) - 0.25) / 0.75;
	}

	/**
	 * Height in plates of {@code def}'s curved top at depth fraction {@code depth} (0 = front lip). The
	 * profile picks the curve: "" the 15068 curve rising to the back edge, "round" a quarter circle (37352),
	 * "double" and "double_round" the same mirrored about the middle into an arch (93273, 3563).
	 */
	public static double curvePlates(LegoPartDefinition def, double depth) {
		String profile = def.profile();
		boolean mirrored = profile.startsWith("double");
		double t = mirrored ? 1 - Math.abs(2 * depth - 1) : depth;
		int hp = def.heightPlates();
		if (!profile.endsWith("round")) {
			return curvePlates(t, hp);
		}
		double radius = def.studsZ() * (mirrored ? 0.5 : 1) * 2.5;
		double lip = Math.max(0, hp - radius);
		return lip + (hp - lip) * Math.sqrt(Math.max(0, 1 - (1 - t) * (1 - t)));
	}

	/** Ascending depth fractions where {@code def}'s curved top bends, {@code segments} per curve. */
	public static double[] curveDepths(LegoPartDefinition def, int segments) {
		String profile = def.profile();
		double[] half = new double[segments + 1];
		double[][] curve = curveProfile(segments);
		for (int i = 0; i <= segments; i++) {
			half[i] = profile.endsWith("round") ? 1 - Math.cos(Math.PI / 2 * i / segments) : curve[i][0];
		}
		if (!profile.startsWith("double")) {
			return half;
		}
		double[] out = new double[2 * segments + 1];
		for (int i = 0; i <= segments; i++) {
			out[i] = half[i] / 2;
			out[2 * segments - i] = 1 - half[i] / 2;
		}
		return out;
	}

	/** Height fraction of the curved top at depth fraction {@code depth} (0 = lip, 1 = back edge). */
	private static double curveHeightAt(double depth) {
		double[][] curve = curveProfile(64);
		for (int i = 0; i + 1 < curve.length; i++) {
			double[] a = curve[i];
			double[] b = curve[i + 1];
			if (depth <= b[0]) {
				return b[0] - a[0] < 1.0E-9 ? b[1] : a[1] + (b[1] - a[1]) * (depth - a[0]) / (b[0] - a[0]);
			}
		}
		return 1;
	}

	public boolean rotated() {
		return facing.getAxis() == Direction.Axis.X;
	}

	/** Upright footprint in studs along X (before tipping to {@code up}). */
	public int sizeX() {
		return rotated() ? def.studsZ() : def.studsX();
	}

	public int sizeZ() {
		return rotated() ? def.studsX() : def.studsZ();
	}

	public double worldX(double lx, double lz) {
		return switch (facing) {
			case SOUTH -> widthPx() - lx;
			case EAST -> depthPx() - lz;
			case WEST -> lz;
			default -> lx;
		};
	}

	public double worldZ(double lx, double lz) {
		return switch (facing) {
			case SOUTH -> depthPx() - lz;
			case EAST -> lx;
			case WEST -> widthPx() - lx;
			default -> lz;
		};
	}

	// ---- local → world ----

	/**
	 * Rotation (in millimetres) taking upright +y to {@code up}, on upright px: a span turned from
	 * standing to lying grows by {@link #ACROSS_PER_UP}, one turned from lying to standing shrinks by it.
	 */
	private double[] tip(double x, double y, double z) {
		double yLying = y * ACROSS_PER_UP;
		double xStanding = x / ACROSS_PER_UP;
		double zStanding = z / ACROSS_PER_UP;
		return switch (up) {
			case DOWN -> new double[] {x, -y, -z};
			case NORTH -> new double[] {x, zStanding, -yLying};
			case SOUTH -> new double[] {x, -zStanding, yLying};
			case EAST -> new double[] {yLying, -xStanding, z};
			case WEST -> new double[] {-yLying, xStanding, z};
			default -> new double[] {x, y, z};
		};
	}

	/** Local px → px relative to the anchor cell's minimum corner. */
	public double[] toWorld(double lx, double ly, double lz) {
		double[] r = tip(worldX(lx, lz), ly, worldZ(lx, lz));
		return new double[] {r[0] + tx * unitPx(0), r[1] + ty * unitPx(1), r[2] + tz * unitPx(2)};
	}

	/** Local (px, plates, px) → units relative to the anchor cell's minimum corner, snapped onto whole units when within rounding error. */
	private double[] toWorldUnits(double lx, double lyPlates, double lz) {
		double[] p = toWorld(lx, lyPlates * PLATE_PX, lz);
		double[] out = new double[3];
		for (int k = 0; k < 3; k++) {
			double v = p[k] / unitPx(k);
			double r = Math.rint(v);
			out[k] = Math.abs(v - r) < 1.0E-6 ? r : v;
		}
		return out;
	}

	/** A piece together with its anchor cell. */
	public record Anchored(LegoPiece piece, int ax, int ay, int az) {
	}

	/**
	 * This piece (anchored at a) turned 90° clockwise around world Y, i.e. every point (x, y, z)
	 * moves to (−z, y, x).
	 */
	public Anchored turnedClockwise(int ax, int ay, int az) {
		Anchored out = transformed(ax, ay, az, d -> new double[] {-d[2], d[1], d[0]}, new double[3]);
		if (out == null) {
			throw new IllegalStateException("no rotation for " + this);
		}
		return out;
	}

	/**
	 * This piece (anchored at a) after the quarter-turn {@code rot} (a linear map of vectors, applied in
	 * millimetres) around {@code pivot} (absolute world px). Null when the result falls off the unit lattice.
	 */
	public Anchored transformed(int ax, int ay, int az, UnaryOperator<double[]> rot, double[] pivot) {
		double[] o = toWorld(0, 0, 0);
		double[] oMm = mm(o);
		double[][] want = new double[3][];
		for (int i = 0; i < 3; i++) {
			double[] p = mm(toWorld(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0));
			want[i] = rot.apply(new double[] {p[0] - oMm[0], p[1] - oMm[1], p[2] - oMm[2]});
		}
		double[] at = mm(new double[] {ax * PX + o[0], ay * PX + o[1], az * PX + o[2]});
		double[] pv = mm(pivot);
		double[] rel = rot.apply(new double[] {at[0] - pv[0], at[1] - pv[1], at[2] - pv[2]});
		double[] target = pxOf(new double[] {pv[0] + rel[0], pv[1] + rel[1], pv[2] + rel[2]});
		for (Direction f : Direction.Plane.HORIZONTAL) {
			for (Direction u : Direction.values()) {
				LegoPiece c = new LegoPiece(def, f, u, 0, 0, 0);
				double[] c0 = c.toWorld(0, 0, 0);
				double[] c0Mm = mm(c0);
				boolean same = true;
				for (int i = 0; i < 3 && same; i++) {
					double[] p = mm(c.toWorld(i == 0 ? 1 : 0, i == 1 ? 1 : 0, i == 2 ? 1 : 0));
					for (int k = 0; k < 3; k++) {
						same &= Math.abs(p[k] - c0Mm[k] - want[i][k]) < 1.0E-6;
					}
				}
				if (!same) {
					continue;
				}
				int[] units = new int[3];
				for (int k = 0; k < 3; k++) {
					double v = (target[k] - c0[k]) / unitPx(k);
					units[k] = (int) Math.round(v);
					if (Math.abs(v - units[k]) > 0.02) {
						return null;
					}
				}
				return new Anchored(new LegoPiece(def, f, u, Math.floorMod(units[0], UNITS_X), Math.floorMod(units[1], UNITS_Y),
					Math.floorMod(units[2], UNITS_X)), Math.floorDiv(units[0], UNITS_X), Math.floorDiv(units[1], UNITS_Y),
					Math.floorDiv(units[2], UNITS_X));
			}
		}
		return null;
	}

	/** World direction of a local axis direction. */
	public Direction toWorld(Direction local) {
		double[] a = toWorld(0, 0, 0);
		double[] b = toWorld(local.getStepX(), local.getStepY(), local.getStepZ());
		return Direction.getNearest((float) (b[0] - a[0]), (float) (b[1] - a[1]), (float) (b[2] - a[2]));
	}

	// ---- local geometry ----

	public boolean hasStudLocal(int ix, int iz) {
		LegoPartType kind = def.kind();
		if (!kind.hasStuds()) {
			return false;
		}
		if (def.panel() != null) {
			return def.panel().rim();
		}
		if (kind == LegoPartType.SLOPE) {
			return iz == def.studsZ() - 1;
		}
		return !def.isShaped() || fitsInside(ix * PX + PX / 2.0, iz * PX + PX / 2.0, STUD_R);
	}

	/** Whether a stud from below can plug into the bottom at local column (ix, iz). */
	public boolean hasReceptorLocal(int ix, int iz) {
		if (def.kind() == LegoPartType.BAR || def.smoothBottom()) {
			return false;
		}
		if (def.kind() == LegoPartType.INVERTED_SLOPE) {
			return iz == def.studsZ() - 1;
		}
		return !def.isShaped() || fitsInside(ix * PX + PX / 2.0, iz * PX + PX / 2.0, RECEPTOR_MARGIN);
	}

	/** Footprint outline in piece-local px: convex polygons of (x, z) points. */
	public List<double[][]> localPolys() {
		if (!def.isShaped()) {
			double w = widthPx();
			double d = depthPx();
			return List.<double[][]>of(new double[][] {{0, 0}, {w, 0}, {w, d}, {0, d}});
		}
		List<double[][]> out = new ArrayList<>(def.shape().size());
		for (double[][] poly : def.shape()) {
			double[][] p = new double[poly.length][];
			for (int i = 0; i < poly.length; i++) {
				p[i] = new double[] {poly[i][0] * PX, poly[i][1] * PX};
			}
			out.add(p);
		}
		return out;
	}

	/** Whether a disc of radius {@code r} around the local point lies wholly inside one outline polygon. */
	private boolean fitsInside(double x, double z, double r) {
		for (double[][] poly : localPolys()) {
			double area = 0;
			for (int i = 0; i < poly.length; i++) {
				double[] a = poly[i];
				double[] b = poly[(i + 1) % poly.length];
				area += a[0] * b[1] - b[0] * a[1];
			}
			double sign = Math.signum(area);
			boolean inside = true;
			for (int i = 0; i < poly.length && inside; i++) {
				double[] a = poly[i];
				double[] b = poly[(i + 1) % poly.length];
				double ex = b[0] - a[0];
				double ez = b[1] - a[1];
				double len = Math.sqrt(ex * ex + ez * ez);
				if (len < 1.0E-9) {
					continue;
				}
				double dist = sign * (ex * (z - a[1]) - ez * (x - a[0])) / len;
				inside = dist >= r - 1.0E-3;
			}
			if (inside) {
				return true;
			}
		}
		return false;
	}

	public List<LocalBox> localBoxes() {
		int w = widthPx();
		int d = depthPx();
		int h = heightPlates();
		List<LocalBox> boxes = new ArrayList<>();
		switch (def.kind()) {
			case SLOPE, CHEESE_SLOPE -> {
				double slopeEnd = slopeEndPx();
				boxes.add(new LocalBox(0, w, 0, slopeEnd / 2, 0, h / 3.0));
				boxes.add(new LocalBox(0, w, slopeEnd / 2, slopeEnd, 0, h * 2 / 3.0));
				if (slopeEnd < d) {
					boxes.add(new LocalBox(0, w, slopeEnd, d, 0, h));
				}
			}
			case INVERTED_SLOPE -> {
				double slopeEnd = slopeEndPx();
				boxes.add(new LocalBox(0, w, 0, slopeEnd / 2, h * 2 / 3.0, h));
				boxes.add(new LocalBox(0, w, slopeEnd / 2, slopeEnd, h / 3.0, h));
				boxes.add(new LocalBox(0, w, slopeEnd, d, 0, h));
			}
			case CURVED_SLOPE -> {
				for (int iz = 0; iz < def.studsZ(); iz++) {
					for (int i = 0; i < CURVE_BOXES_PER_ROW; i++) {
						double za = (iz + (double) i / CURVE_BOXES_PER_ROW) * PX;
						double zb = (iz + (double) (i + 1) / CURVE_BOXES_PER_ROW) * PX;
						boxes.add(new LocalBox(0, w, za, zb, def.bottomPlates(iz), curvePlates(def, (za + zb) / 2 / d)));
					}
				}
			}
			case ROUND_BRICK, ROUND_PLATE, ROUND_TILE -> boxes.add(new LocalBox(1, w - 1, 1, d - 1, 0, h));
			case BAR -> {
			}
			case WALL_PANEL -> boxes.addAll(panelBoxes());
			case TECHNIC_BRICK -> {
				double holeY0 = (SIDE_HOLE_Y - HOLE_HALF) * 3 / PX;
				double holeY1 = (SIDE_HOLE_Y + HOLE_HALF) * 3 / PX;
				double z = 0;
				for (LocalHole hole : localHoles()) {
					boxes.add(new LocalBox(0, w, z, hole.z() - HOLE_HALF, 0, h));
					boxes.add(new LocalBox(0, w, hole.z() - HOLE_HALF, hole.z() + HOLE_HALF, 0, holeY0));
					boxes.add(new LocalBox(0, w, hole.z() - HOLE_HALF, hole.z() + HOLE_HALF, holeY1, h));
					z = hole.z() + HOLE_HALF;
				}
				boxes.add(new LocalBox(0, w, z, d, 0, h));
			}
			case TECHNIC_PLATE -> {
				double cx = w / 2.0;
				boxes.add(new LocalBox(0, cx - HOLE_HALF, 0, d, 0, h));
				boxes.add(new LocalBox(cx + HOLE_HALF, w, 0, d, 0, h));
				double z = 0;
				for (LocalHole hole : localHoles()) {
					boxes.add(new LocalBox(cx - HOLE_HALF, cx + HOLE_HALF, z, hole.z() - HOLE_HALF, 0, h));
					z = hole.z() + HOLE_HALF;
				}
				boxes.add(new LocalBox(cx - HOLE_HALF, cx + HOLE_HALF, z, d, 0, h));
			}
			default -> {
				if (def.isShaped()) {
					addOutlineBoxes(boxes, def.bodyPlates());
				} else {
					boxes.add(new LocalBox(0, w, 0, d, 0, h));
				}
			}
		}
		LegoPartDefinition.Bracket bracket = def.bracket();
		if (bracket != null) {
			double y0 = bracket.bottomPlates(h);
			boxes.add(new LocalBox(bracket.x0() * PX, (bracket.x0() + bracket.width()) * PX,
				-bracket.thickness() * PLATE_PX, 0, y0, y0 + bracket.plates()));
		}
		addFittingBoxes(boxes);
		return boxes;
	}

	/** Base, walls and top rim of a wall panel; walls under the rim stop at its underside so no faces coincide. */
	public List<LocalBox> panelBoxes() {
		LegoPartDefinition.Panel panel = def.panel() != null ? def.panel() : LegoPartDefinition.Panel.PLAIN;
		double w = widthPx();
		double d = depthPx();
		int h = heightPlates();
		double t = panel.wallPx();
		double inner = panel.rim() ? h - 0.5 : h;
		List<LocalBox> boxes = new ArrayList<>();
		if (def.isShaped()) {
			addOutlineBoxes(boxes, 1);
		} else {
			boxes.add(new LocalBox(0, w, 0, d, 0, 1));
		}
		boxes.add(new LocalBox(0, w, d - t, d, 1, h));
		if (panel.left()) {
			boxes.add(new LocalBox(0, t, 0, d - t, 1, inner));
		}
		if (panel.right()) {
			boxes.add(new LocalBox(w - t, w, 0, d - t, 1, inner));
		}
		if (panel.divider()) {
			double half = panel.dividerPx() / 2;
			boxes.add(new LocalBox(w / 2 - half, w / 2 + half, 0, d - t, 1, inner));
		}
		if (panel.rim()) {
			boxes.add(new LocalBox(0, w, 0, d - t, inner, h));
		}
		return boxes;
	}

	/** Bars are thinner than they look and clip rings leave room for them, so a held bar never collides. */
	private void addFittingBoxes(List<LocalBox> boxes) {
		LegoPartDefinition.Fittings fittings = def.fittings();
		for (double[] b : fittings.boxes()) {
			boxes.add(unitBox(b[0], b[1], b[2], b[3], b[4], b[5]));
		}
		for (LegoPartDefinition.Bar bar : fittings.bars()) {
			double half = bar.radius() * BAR_BOX_SCALE;
			double[] lo = new double[3];
			double[] hi = new double[3];
			for (int k = 0; k < 3; k++) {
				double a = Math.min(bar.from()[k], bar.to()[k]);
				double b = Math.max(bar.from()[k], bar.to()[k]);
				boolean along = b - a > 1.0E-6;
				lo[k] = along ? a : a - half;
				hi[k] = along ? b : a + half;
			}
			boxes.add(unitBox(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
		}
		for (LegoPartDefinition.Clip clip : fittings.clips()) {
			int a = clip.axis().ordinal();
			int p = (a + 1) % 3;
			int q = (a + 2) % 3;
			double[][] ring = {
				{-CLIP_OUTER, -CLIP_HOLE, -CLIP_OUTER, CLIP_OUTER},
				{CLIP_HOLE, CLIP_OUTER, -CLIP_OUTER, CLIP_OUTER},
				{-CLIP_HOLE, CLIP_HOLE, -CLIP_OUTER, -CLIP_HOLE},
				{-CLIP_HOLE, CLIP_HOLE, CLIP_HOLE, CLIP_OUTER},
			};
			for (double[] r : ring) {
				double[] lo = new double[3];
				double[] hi = new double[3];
				lo[a] = clip.at()[a] - clip.halfWidth();
				hi[a] = clip.at()[a] + clip.halfWidth();
				lo[p] = clip.at()[p] + r[0];
				hi[p] = clip.at()[p] + r[1];
				lo[q] = clip.at()[q] + r[2];
				hi[q] = clip.at()[q] + r[3];
				boxes.add(unitBox(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]));
			}
		}
	}

	/** A box given in piece-local units. */
	private static LocalBox unitBox(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new LocalBox(x0 * FIT_PX, x1 * FIT_PX, z0 * FIT_PX, z1 * FIT_PX, y0 / 2, y1 / 2);
	}

	/**
	 * Covers each outline polygon with boxes lying inside it (2 px strips along z, merged when equal),
	 * so neighbouring angled parts never collide where they only touch along a diagonal.
	 */
	private void addOutlineBoxes(List<LocalBox> boxes, double h) {
		double step = 2;
		for (double[][] poly : localPolys()) {
			double zMin = Double.MAX_VALUE;
			double zMax = -Double.MAX_VALUE;
			for (double[] p : poly) {
				zMin = Math.min(zMin, p[1]);
				zMax = Math.max(zMax, p[1]);
			}
			double runX0 = 0;
			double runX1 = 0;
			double runZ0 = 0;
			double runZ1 = 0;
			boolean run = false;
			for (double z0 = zMin; z0 < zMax - 1.0E-6; z0 += step) {
				double z1 = Math.min(z0 + step, zMax);
				double[] a = xRange(poly, z0);
				double[] b = xRange(poly, z1);
				double x0 = Math.max(a[0], b[0]);
				double x1 = Math.min(a[1], b[1]);
				if (x1 - x0 < 1.0E-6) {
					if (run) {
						boxes.add(new LocalBox(runX0, runX1, runZ0, runZ1, 0, h));
						run = false;
					}
					continue;
				}
				if (run && Math.abs(x0 - runX0) < 1.0E-6 && Math.abs(x1 - runX1) < 1.0E-6) {
					runZ1 = z1;
					continue;
				}
				if (run) {
					boxes.add(new LocalBox(runX0, runX1, runZ0, runZ1, 0, h));
				}
				runX0 = x0;
				runX1 = x1;
				runZ0 = z0;
				runZ1 = z1;
				run = true;
			}
			if (run) {
				boxes.add(new LocalBox(runX0, runX1, runZ0, runZ1, 0, h));
			}
		}
	}

	/** {min x, max x} of a convex polygon along the line at local z (empty range if it misses). */
	private static double[] xRange(double[][] poly, double z) {
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (int i = 0; i < poly.length; i++) {
			double[] a = poly[i];
			double[] b = poly[(i + 1) % poly.length];
			if (z < Math.min(a[1], b[1]) - 1.0E-9 || z > Math.max(a[1], b[1]) + 1.0E-9) {
				continue;
			}
			if (Math.abs(b[1] - a[1]) < 1.0E-9) {
				min = Math.min(min, Math.min(a[0], b[0]));
				max = Math.max(max, Math.max(a[0], b[0]));
			} else {
				double x = a[0] + (b[0] - a[0]) * (z - a[1]) / (b[1] - a[1]);
				min = Math.min(min, x);
				max = Math.max(max, x);
			}
		}
		return min > max ? new double[] {0, 0} : new double[] {min, max};
	}

	/** Technic holes sit between studs: N−1 on a 1×N brick (one centred hole on 1×1). */
	public List<LocalHole> localHoles() {
		List<LocalHole> holes = new ArrayList<>();
		int n = def.studsZ();
		switch (def.kind()) {
			case TECHNIC_BRICK -> {
				if (n == 1) {
					holes.add(new LocalHole(widthPx() / 2.0, SIDE_HOLE_Y, PX / 2.0, false));
				}
				for (int i = 1; i < n; i++) {
					holes.add(new LocalHole(widthPx() / 2.0, SIDE_HOLE_Y, i * PX, false));
				}
			}
			case TECHNIC_PLATE -> {
				double midY = heightPlates() * PX / 6.0;
				for (int i = 1; i < n; i++) {
					holes.add(new LocalHole(widthPx() / 2.0, midY, i * PX, true));
				}
			}
			default -> {
			}
		}
		return holes;
	}

	/**
	 * Top stud centres (x, z) in piece-local px, with the height in px of the face each stands on and
	 * its vertical direction (1 up, −1 down).
	 */
	public List<double[]> topStudCentres() {
		List<double[]> out = new ArrayList<>();
		if (!def.studs().isEmpty()) {
			for (double[] s : def.studs()) {
				out.add(new double[] {s[0] * PX, s[1] * PX, s[2] * PLATE_PX, s[3]});
			}
			return out;
		}
		double top = heightPlates() * PLATE_PX;
		for (int ix = 0; ix < def.studsX(); ix++) {
			for (int iz = 0; iz < def.studsZ(); iz++) {
				if (hasStudLocal(ix, iz)) {
					out.add(new double[] {ix * PX + 8, iz * PX + 8, top, 1});
				}
			}
		}
		return out;
	}

	/** Studs in piece-local px with their local direction (top studs point ±y, side studs −z). */
	private List<double[]> localStuds() {
		List<double[]> out = new ArrayList<>();
		for (double[] c : topStudCentres()) {
			out.add(new double[] {c[0], c[2], c[1], 0, c[3], 0});
		}
		LegoPartDefinition.Bracket bracket = def.bracket();
		if (bracket != null) {
			for (int i = 0; i < bracket.width(); i++) {
				for (int j = 0; j < bracket.height(); j++) {
					out.add(new double[] {(bracket.x0() + i) * PX + 8, bracket.studPlates(j, heightPlates()) * PLATE_PX,
						-bracket.thickness() * PLATE_PX, 0, 0, -1});
				}
			}
		}
		for (double[] s : def.sideStudCentres()) {
			out.add(sideStudLocal(s, widthPx(), depthPx(), 0));
		}
		return out;
	}

	/**
	 * A side stud centre from {@link LegoPartDefinition#sideStudCentres()} as {x, y, z, dx, dy, dz} in
	 * piece-local px, standing {@code lift} px out from its face of the {@code width × depth} body.
	 */
	public static double[] sideStudLocal(double[] s, double width, double depth, double lift) {
		Direction face = Direction.from3DDataValue((int) s[2]);
		double along = s[0] * PX;
		double y = s[1] * PLATE_PX;
		return switch (face) {
			case SOUTH -> new double[] {along, y, depth + lift, 0, 0, 1};
			case WEST -> new double[] {-lift, y, along, -1, 0, 0};
			case EAST -> new double[] {width + lift, y, along, 1, 0, 0};
			default -> new double[] {along, y, -lift, 0, 0, -1};
		};
	}

	// ---- placed geometry (cached) ----

	private Placed placed() {
		return PLACED.computeIfAbsent(this, LegoPiece::computePlaced);
	}

	private Placed computePlaced() {
		List<double[]> boxes = new ArrayList<>();
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		TreeSet<List<Integer>> cells = new TreeSet<>(LegoPiece::compareCells);
		for (LocalBox box : localBoxes()) {
			double[] a = toWorldUnits(box.x0(), box.y0(), box.z0());
			double[] c = toWorldUnits(box.x1(), box.y1(), box.z1());
			double[] out = new double[6];
			for (int i = 0; i < 3; i++) {
				out[i] = Math.min(a[i], c[i]);
				out[i + 3] = Math.max(a[i], c[i]);
			}
			if (out[3] - out[0] < 1.0E-9 || out[4] - out[1] < 1.0E-9 || out[5] - out[2] < 1.0E-9) {
				continue;
			}
			boxes.add(out);
			minY = Math.min(minY, out[1]);
			maxY = Math.max(maxY, out[4]);
			int[] c0 = new int[3];
			int[] c1 = new int[3];
			for (int i = 0; i < 3; i++) {
				c0[i] = (int) Math.floor((out[i] + 1.0E-6) / CELL_UNITS[i]);
				c1[i] = (int) Math.floor((out[i + 3] - 1.0E-6) / CELL_UNITS[i]);
			}
			for (int x = c0[0]; x <= c1[0]; x++) {
				for (int y = c0[1]; y <= c1[1]; y++) {
					for (int z = c0[2]; z <= c1[2]; z++) {
						cells.add(List.of(x, y, z));
					}
				}
			}
		}

		List<Mesh.Raw> raws = new ArrayList<>();
		double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (Mesh.Raw raw : BrickMesh.local(this).raws()) {
			double[][] v = new double[raw.v().length][];
			for (int i = 0; i < v.length; i++) {
				double[] q = raw.v()[i];
				v[i] = toWorld(q[0], q[1], q[2]);
				if (raw.anchor() == null) {
					for (int k = 0; k < 3; k++) {
						min[k] = Math.min(min[k], v[i][k]);
						max[k] = Math.max(max[k], v[i][k]);
					}
				}
			}
			double[] anchor = raw.anchor() == null ? null : toWorld(raw.anchor()[0], raw.anchor()[1], raw.anchor()[2]);
			raws.add(new Mesh.Raw(v, raw.material(), raw.clip(), anchor));
		}
		if (min[0] <= max[0]) {
			for (int x = (int) Math.floor((min[0] + Mesh.CLIP_EPS) / PX); x <= (int) Math.floor((max[0] - Mesh.CLIP_EPS) / PX); x++) {
				for (int y = (int) Math.floor((min[1] + Mesh.CLIP_EPS) / PX); y <= (int) Math.floor((max[1] - Mesh.CLIP_EPS) / PX); y++) {
					for (int z = (int) Math.floor((min[2] + Mesh.CLIP_EPS) / PX); z <= (int) Math.floor((max[2] - Mesh.CLIP_EPS) / PX); z++) {
						List<Integer> cell = List.of(x, y, z);
						if (cells.contains(cell)) {
							continue;
						}
						double[] mb = meshBoundsInCell(raws, x, y, z);
						if (mb != null && mb[3] - mb[0] >= MESH_CELL_MIN_PX && mb[4] - mb[1] >= MESH_CELL_MIN_PX
							&& mb[5] - mb[2] >= MESH_CELL_MIN_PX) {
							cells.add(cell);
							int[] at = {x, y, z};
							double[] box = new double[6];
							for (int i = 0; i < 3; i++) {
								box[i] = (at[i] * PX + mb[i]) / unitPx(i);
								box[i + 3] = (at[i] * PX + mb[i + 3]) / unitPx(i);
							}
							boxes.add(box);
						}
					}
				}
			}
		}

		List<int[]> fragments = new ArrayList<>(cells.size());
		for (List<Integer> c : cells) {
			fragments.add(new int[] {c.get(0), c.get(1), c.get(2)});
		}
		return new Placed(boxes, raws, fragments, minY == Double.MAX_VALUE ? 0 : minY, maxY == -Double.MAX_VALUE ? 0 : maxY);
	}

	private static int compareCells(List<Integer> a, List<Integer> b) {
		int c = Integer.compare(a.get(1), b.get(1));
		if (c == 0) {
			c = Integer.compare(a.get(0), b.get(0));
		}
		return c != 0 ? c : Integer.compare(a.get(2), b.get(2));
	}

	/** Bounds {x0, y0, z0, x1, y1, z1} in px of the mesh clipped to cell (dx, dy, dz), or null if none reaches it. */
	private static double[] meshBoundsInCell(List<Mesh.Raw> raws, int dx, int dy, int dz) {
		double[] b = null;
		for (Mesh.Raw raw : raws) {
			if (raw.anchor() != null) {
				continue;
			}
			Mesh.Poly poly = clipRaw(raw, dx, dy, dz);
			if (poly == null) {
				continue;
			}
			if (b == null) {
				b = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
			}
			for (double[] v : poly.v()) {
				for (int k = 0; k < 3; k++) {
					b[k] = Math.min(b[k], v[k]);
					b[k + 3] = Math.max(b[k + 3], v[k]);
				}
			}
		}
		return b;
	}

	private static Mesh.Poly clipRaw(Mesh.Raw raw, int dx, int dy, int dz) {
		return Mesh.clipToCell(translate(raw.v(), dx, dy, dz), raw.material(), true);
	}

	private static double[][] translate(double[][] src, int dx, int dy, int dz) {
		double[][] v = new double[src.length][];
		for (int i = 0; i < v.length; i++) {
			double[] q = src[i];
			v[i] = new double[] {q[0] - dx * PX, q[1] - dy * PX, q[2] - dz * PX};
		}
		return v;
	}

	/** Cell offsets (relative to the anchor cell) this piece occupies, lowest first. */
	public List<int[]> fragments() {
		return placed().fragments();
	}

	/** Lowest point of the body, in plates above the anchor cell's floor. */
	public double minYPlates() {
		return placed().minY() / PLATE_UNITS;
	}

	/** Highest point of the body (studs excluded), in plates above the anchor cell's floor. */
	public double maxYPlates() {
		return placed().maxY() / PLATE_UNITS;
	}

	/** Collision/outline shape of the fragment at cell offset (dx, dy, dz), in block-local units. */
	public VoxelShape fragmentShape(int dx, int dy, int dz) {
		VoxelShape shape = Shapes.empty();
		int[] c = {dx * UNITS_X, dy * UNITS_Y, dz * UNITS_X};
		for (double[] b : placed().boxes()) {
			double[] lo = new double[3];
			double[] hi = new double[3];
			boolean empty = false;
			for (int i = 0; i < 3; i++) {
				lo[i] = Math.max(b[i], c[i]);
				hi[i] = Math.min(b[i + 3], c[i] + CELL_UNITS[i]);
				empty |= hi[i] <= lo[i];
			}
			if (empty) {
				continue;
			}
			shape = Shapes.or(shape, Shapes.box(
				(lo[0] - c[0]) / UNITS_X, (lo[1] - c[1]) / UNITS_Y, (lo[2] - c[2]) / UNITS_X,
				(hi[0] - c[0]) / UNITS_X, (hi[1] - c[1]) / UNITS_Y, (hi[2] - c[2]) / UNITS_X
			));
		}
		return shape;
	}

	/** Render polygons of one fragment, in px relative to that fragment's cell. */
	public List<Mesh.Poly> fragmentPolys(int dx, int dy, int dz) {
		List<Mesh.Poly> out = new ArrayList<>();
		for (Mesh.Raw raw : placed().raws()) {
			if (raw.anchor() == null) {
				Mesh.Poly poly = clipRaw(raw, dx, dy, dz);
				if (poly != null) {
					out.add(poly);
				}
			} else if ((int) Math.floor(raw.anchor()[0] / PX) == dx && (int) Math.floor(raw.anchor()[1] / PX) == dy
				&& (int) Math.floor(raw.anchor()[2] / PX) == dz) {
				out.add(new Mesh.Poly(translate(raw.v(), dx, dy, dz), raw.material(), null));
			}
		}
		return out;
	}

	// ---- connections, in absolute world px given the anchor cell ----

	public List<Stud> studs(int ax, int ay, int az) {
		List<Stud> out = new ArrayList<>();
		for (double[] s : localStuds()) {
			double[] p = toWorld(s[0], s[1], s[2]);
			Direction dir = toWorld(Direction.getNearest((float) s[3], (float) s[4], (float) s[5]));
			out.add(new Stud(ax * PX + p[0], ay * PX + p[1], az * PX + p[2], dir));
		}
		return out;
	}

	public List<Stud> receptors(int ax, int ay, int az) {
		List<Stud> out = new ArrayList<>();
		for (int ix = 0; ix < def.studsX(); ix++) {
			for (int iz = 0; iz < def.studsZ(); iz++) {
				if (hasReceptorLocal(ix, iz)) {
					double[] p = toWorld(ix * PX + 8, def.bottomPlates(iz) * PLATE_PX, iz * PX + 8);
					out.add(new Stud(ax * PX + p[0], ay * PX + p[1], az * PX + p[2], up));
				}
			}
		}
		for (double[] r : def.receptors()) {
			double[] p = toWorld(r[0] * PX, r[2] * PLATE_PX, r[1] * PX);
			out.add(new Stud(ax * PX + p[0], ay * PX + p[1], az * PX + p[2], up));
		}
		return out;
	}

	/** Centres of the footprint columns the piece covers, on its bottom plane, in absolute world px. */
	public List<double[]> bottomSpots(int ax, int ay, int az) {
		List<double[]> out = new ArrayList<>();
		for (int ix = 0; ix < def.studsX(); ix++) {
			for (int iz = 0; iz < def.studsZ(); iz++) {
				if (coversColumn(ix, iz)) {
					double[] p = toWorld(ix * PX + 8, 0, iz * PX + 8);
					out.add(new double[] {ax * PX + p[0], ay * PX + p[1], az * PX + p[2]});
				}
			}
		}
		return out;
	}

	private boolean coversColumn(int ix, int iz) {
		if (!def.isShaped()) {
			return true;
		}
		for (int i = 1; i < 6; i++) {
			for (int k = 1; k < 6; k++) {
				if (fitsInside(ix * PX + i * PX / 6.0, iz * PX + k * PX / 6.0, 0)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Bounding box {x0, y0, z0, x1, y1, z1} of the body in px relative to the anchor cell. */
	public double[] boundsPx() {
		double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (double[] box : placed().boxes()) {
			for (int i = 0; i < 3; i++) {
				b[i] = Math.min(b[i], box[i] * unitPx(i));
				b[i + 3] = Math.max(b[i + 3], box[i + 3] * unitPx(i));
			}
		}
		return b;
	}

	/** A bar in absolute world px, running from {@code a} to {@code b} along {@code axis}. */
	public record WorldBar(Direction.Axis axis, double[] a, double[] b, boolean grip) {
		public double lo() {
			return Math.min(a[axis.ordinal()], b[axis.ordinal()]);
		}

		public double hi() {
			return Math.max(a[axis.ordinal()], b[axis.ordinal()]);
		}

		/** The end at {@code lo()} ({@code false}) or {@code hi()} ({@code true}). */
		public double[] end(boolean high) {
			return (a[axis.ordinal()] > b[axis.ordinal()]) == high ? a : b;
		}
	}

	/**
	 * A clip or ring in absolute world px. A socket (hollow stud) only takes the end of a bar standing
	 * on it along {@code socket}; for a blind bar hole {@code halfWidth} is its depth and the end may
	 * stop anywhere in it. A bar shorter than the clip (a candlestick's peg) is held when it lies wholly
	 * inside it.
	 */
	public record WorldClip(Direction.Axis axis, double[] at, double halfWidth, Direction open, Direction socket) {
		/** Whether this clip holds {@code bar} as it lies. */
		public boolean holds(WorldBar bar) {
			if (bar.axis() != axis || !bar.grip()) {
				return false;
			}
			int k = axis.ordinal();
			for (int i = 0; i < 3; i++) {
				if (i != k && Math.abs(bar.a()[i] - at[i]) > CONNECT_EPS) {
					return false;
				}
			}
			if (socket != null) {
				boolean up = socket.getAxisDirection() == Direction.AxisDirection.POSITIVE;
				double into = up ? bar.lo() - at[k] : at[k] - bar.hi();
				return into > -CONNECT_EPS && into < halfWidth + CONNECT_EPS;
			}
			if (bar.hi() - bar.lo() < 2 * halfWidth - CONNECT_EPS) {
				return at[k] - halfWidth - CONNECT_EPS <= bar.lo() && bar.hi() <= at[k] + halfWidth + CONNECT_EPS;
			}
			return bar.lo() - CONNECT_EPS <= at[k] - halfWidth && at[k] + halfWidth <= bar.hi() + CONNECT_EPS;
		}
	}

	public List<WorldBar> bars(int ax, int ay, int az) {
		List<WorldBar> out = new ArrayList<>();
		for (LegoPartDefinition.Bar bar : def.fittings().bars()) {
			double[] a = worldUnitsPoint(bar.from(), ax, ay, az);
			double[] b = worldUnitsPoint(bar.to(), ax, ay, az);
			int k = 0;
			for (int i = 1; i < 3; i++) {
				if (Math.abs(b[i] - a[i]) > Math.abs(b[k] - a[k])) {
					k = i;
				}
			}
			out.add(new WorldBar(Direction.Axis.values()[k], a, b, bar.grip()));
		}
		return out;
	}

	public List<WorldClip> clips(int ax, int ay, int az) {
		List<WorldClip> out = new ArrayList<>();
		for (LegoPartDefinition.Clip clip : def.fittings().clips()) {
			Direction mouthDir = clip.blind() && clip.open() != null
				? clip.open() : Direction.fromAxisAndDirection(clip.axis(), Direction.AxisDirection.POSITIVE);
			Direction opening = toWorld(mouthDir);
			Direction.Axis axis = opening.getAxis();
			if (clip.blind()) {
				int sign = mouthDir.getAxisDirection().getStep();
				double[] bottom = clip.at().clone();
				bottom[clip.axis().ordinal()] -= sign * clip.halfWidth();
				double[] mouth = clip.at().clone();
				mouth[clip.axis().ordinal()] += sign * clip.halfWidth();
				double[] b = worldUnitsPoint(bottom, ax, ay, az);
				double depth = Math.abs(worldUnitsPoint(mouth, ax, ay, az)[axis.ordinal()] - b[axis.ordinal()]);
				out.add(new WorldClip(axis, b, depth, null, opening));
				continue;
			}
			double[] at = worldUnitsPoint(clip.at(), ax, ay, az);
			double[] edge = clip.at().clone();
			edge[clip.axis().ordinal()] += clip.halfWidth();
			double halfWidth = Math.abs(worldUnitsPoint(edge, ax, ay, az)[axis.ordinal()] - at[axis.ordinal()]);
			out.add(new WorldClip(axis, at, halfWidth, clip.open() == null ? null : toWorld(clip.open()), null));
		}
		if (def.openStuds()) {
			List<double[]> local = localStuds();
			List<Stud> world = studs(ax, ay, az);
			for (int i = 0; i < world.size(); i++) {
				Stud s = world.get(i);
				if (def.isOpenStud(local.get(i)[0] / PX, local.get(i)[2] / PX)) {
					out.add(new WorldClip(s.dir().getAxis(), new double[] {s.x(), s.y(), s.z()}, 0, null, s.dir()));
				}
			}
		}
		return out;
	}

	private double[] worldUnitsPoint(double[] u, int ax, int ay, int az) {
		double[] p = toWorld(u[0] * FIT_PX, u[1] * FIT_PX, u[2] * FIT_PX);
		return new double[] {ax * PX + p[0], ay * PX + p[1], az * PX + p[2]};
	}

	/** Holes in absolute world px, given the anchor cell's block position. */
	public List<TechnicHole> worldHoles(int ax, int ay, int az) {
		List<TechnicHole> out = new ArrayList<>();
		for (LocalHole hole : localHoles()) {
			double[] p = toWorld(hole.x(), hole.y(), hole.z());
			Direction.Axis axis = toWorld(hole.vertical() ? Direction.UP : Direction.EAST).getAxis();
			out.add(new TechnicHole(ax * PX + p[0], ay * PX + p[1], az * PX + p[2], axis, false));
		}
		return out;
	}
}
