package com.legominecraft.block;

import com.legominecraft.item.LegoTab;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * One official LEGO design ID from Pick a Brick (e.g. 3001 = Brick 2×4, 32523 = Technic 3M Beam).
 * <p>
 * Studded parts: {@code studsX}/{@code studsZ} are the footprint in studs before rotation (1 stud = 1 block).
 * For slopes, {@code studsZ} is the depth along the slope and the low edge faces local north.
 * <p>
 * Studless Technic parts: {@code studsZ} is the length in modules (1 module = 8 mm = 1 block);
 * {@code teeth} is the tooth count for gears.
 * <p>
 * {@code height} overrides the kind's height in plates (e.g. 9 for Brick 1×2×3); 0 keeps the default.
 * <p>
 * {@code shape} is the footprint outline of non-rectangular studded parts (corner plates, wedge plates,
 * round corners…): convex polygons of (x, z) points in studs, piece-local, inside the
 * {@code studsX × studsZ} box. Empty means the full rectangle.
 * <p>
 * {@code bracket} describes the upright plate of a bracket; {@code sideStuds} puts one stud per column on
 * the local −z face at mid-height (bricks with studs on one side).
 * <p>
 * {@code sideStudSpots} places them explicitly: {x, y} with x the stud centre in studs (NaN: every
 * column) and y the centre height in plates; empty means {@link #sideStudRows()}'s default layout.
 * {@code sideStudFaces} repeats that layout on each listed face (empty: −z only); on the x faces the
 * stud x is measured along z.
 * <p>
 * {@code studs} lists the top stud centres (x, z) in studs for parts whose studs are off the regular
 * grid (jumper plates), optionally with a third value: the height in plates of the top they stand on
 * (stepped parts) and a fourth: −1 for a stud pointing down from that height (bars with studs on both
 * ends), else 1; empty means one stud per column. {@code grille} is where grille slots are
 * drawn: "top", "side" (local −z face) or "" for none. {@code openStuds} draws hollow studs: those at
 * {@code openStudSpots} (x, z in studs), or all of them when that is empty. {@code smoothBottom} leaves
 * the underside without anti-studs (inverted tiles).
 * {@code aliases} are other numbers the same design is known by (e.g. 6141 for 4073).
 * {@code transColors} are the transparent colours the real part is made in.
 * {@code fittings} are the bars (handles) and clips the part carries.
 * {@code profile} shapes the long faces (local −z and +z): "log" (a row of upright half-round logs, one
 * per stud), "masonry" (raised staggered bricks) or "" for flat.
 * {@code panel} describes the walls of a wall panel (null for other kinds).
 * {@code rowBottoms} raises the underside of each local z row by that many plates (hollow below, its
 * receptors raised to match), so the part bridges onto higher surfaces (curved slopes like 15068);
 * rows past the list's end stay at 0.
 * {@code receptors} adds anti-studs (x, z in studs, the height in plates of the plane they open
 * downward from) to those of the regular footprint, e.g. a Technic connector's pin hole.
 */
public record LegoPartDefinition(
	int designId,
	String nameEn,
	String nameZh,
	LegoPartType kind,
	int studsX,
	int studsZ,
	int teeth,
	int height,
	List<double[][]> shape,
	Bracket bracket,
	boolean sideStuds,
	List<double[]> studs,
	String grille,
	boolean openStuds,
	List<Integer> aliases,
	List<LegoColor> transColors,
	Fittings fittings,
	String profile,
	Panel panel,
	List<double[]> sideStudSpots,
	List<Integer> rowBottoms,
	List<double[]> receptors,
	List<Direction> sideStudFaces,
	List<double[]> openStudSpots,
	boolean smoothBottom
) {
	/** Whether the top stud centred at (x, z) in studs is hollow (and takes a bar). */
	public boolean isOpenStud(double x, double z) {
		if (!openStuds) {
			return false;
		}
		for (double[] s : openStudSpots) {
			if (Math.abs(s[0] - x) < 1.0E-3 && Math.abs(s[1] - z) < 1.0E-3) {
				return true;
			}
		}
		return openStudSpots.isEmpty();
	}

	/**
	 * Walls of a panel standing on its smooth 1-plate base. The back wall runs along the local +z edge,
	 * so the open side faces the player who places it.
	 *
	 * @param wall    wall thickness in LDraw units (1 stud = 20 LDU)
	 * @param left    end wall (side support) at local x = 0
	 * @param right   end wall at the far x end
	 * @param divider thin wall across the middle, from the front edge to the back wall
	 * @param rim     half-plate rim over the whole top, carrying the top studs (tall panels)
	 */
	public record Panel(double wall, boolean left, boolean right, boolean divider, boolean rim) {
		public static final Panel PLAIN = new Panel(4, false, false, false, false);
		private static final double PX_PER_LDU = 16 / 20.0;
		private static final double DIVIDER_LDU = 3;

		public double wallPx() {
			return wall * PX_PER_LDU;
		}

		public double dividerPx() {
			return DIVIDER_LDU * PX_PER_LDU;
		}
	}

	/**
	 * Bars and clips, in piece-local units (1 unit = 1/6 stud = half a plate; x, z from the footprint's
	 * minimum corner, y from the piece bottom). A clip holds a bar when the bar's axis runs through the
	 * clip's centre, parallel to the clip's axis, over the clip's whole width.
	 *
	 * @param boxes extra solid boxes {x0, y0, z0, x1, y1, z1} (handle posts, clip arms)
	 */
	public record Fittings(List<Bar> bars, List<Clip> clips, List<double[]> boxes) {
		public static final Fittings NONE = new Fittings(List.of(), List.of(), List.of());

		public boolean isEmpty() {
			return bars.isEmpty() && clips.isEmpty() && boxes.isEmpty();
		}
	}

	/**
	 * A straight bar from {@code from} to {@code to} (along one axis).
	 *
	 * @param radius in units
	 * @param grip   whether clips can hold this stretch (false for stop rings, bends)
	 * @param sides  drawn polygon sides, 0 for a round look (6 for hex nuts); only affects rendering
	 * @param hollow draws a dark hole on both end faces (a Technic connector's pin hole); only affects rendering
	 */
	public record Bar(double[] from, double[] to, double radius, boolean grip, int sides, boolean hollow) {
		public static final double RADIUS = 1.125;
	}

	/**
	 * @param at        centre of the clip ring
	 * @param axis      axis of the bar it holds
	 * @param open      local direction the tips open toward, or null for a closed ring; for a blind hole,
	 *                  the direction its mouth faces along {@code axis} (null: +axis)
	 * @param halfWidth half the ring's length along {@code axis}
	 * @param blind     a bar hole with a bottom at the end away from its mouth (candlestick top): it takes
	 *                  the end of a bar pushed in through the mouth, like a hollow stud
	 * @param outer     drawn outer radius in units, 0 for the standard thin ring; only affects rendering
	 */
	public record Clip(double[] at, Direction.Axis axis, Direction open, double halfWidth, boolean blind, double outer) {
	}

	/** Every colour this part comes in: the 26 solid colours, then the transparent ones it really exists in. */
	public List<LegoColor> colors() {
		if (transColors.isEmpty()) {
			return LegoColor.opaque();
		}
		List<LegoColor> out = new ArrayList<>(LegoColor.opaque());
		out.addAll(transColors);
		return out;
	}

	/**
	 * Upright plate standing on the local −z edge of a bracket's 1-plate base, studs facing −z.
	 *
	 * @param width     in studs, along local x
	 * @param height    in studs
	 * @param x0        studs from local x = 0 to the plate's left edge (may be negative)
	 * @param inverted  false: the plate hangs down, flush with the base's top; true: it rises from the base's bottom
	 * @param plates    length of the upright plate in plates (the real part's, e.g. 2.5 per stud of height)
	 * @param thickness of the upright plate, in plates
	 * @param firstStud plates from the base end of the upright plate to its first row of studs' centres;
	 *                  further rows follow one stud (8 mm = 2.5 plates) apart
	 */
	public record Bracket(int width, int height, int x0, boolean inverted, double plates, double thickness, double firstStud) {
		/** Lower end of the upright plate, in plates above the piece's bottom. */
		public double bottomPlates(int baseHeightPlates) {
			return inverted ? 0 : baseHeightPlates - plates;
		}

		/** Centre height of stud row {@code row}, in plates above the piece's bottom. */
		public double studPlates(int row, int baseHeightPlates) {
			return inverted ? firstStud + row * 2.5 : baseHeightPlates - firstStud - row * 2.5;
		}
	}

	public int heightPlates() {
		return height > 0 ? height : kind.heightPlates();
	}

	/** Height in plates of the underside (where the receptors are) at local row {@code iz}. */
	public int bottomPlates(int iz) {
		return iz >= 0 && iz < rowBottoms.size() ? rowBottoms.get(iz) : 0;
	}

	/** Height of the body itself in plates: a gem is half a brick (12 LDU), lower than its 2-plate slot. */
	public double bodyPlates() {
		return kind == LegoPartType.GEM ? 1.5 : heightPlates();
	}

	/**
	 * Centre heights (plates above the bottom) of the rows of side studs: one row per 2.5 plates of
	 * height, centred, one stud (8 mm = 2.5 plates) apart so sideways plates grip every row.
	 */
	public double[] sideStudRows() {
		int h = heightPlates();
		int n = Math.max(1, (int) Math.floor(h / 2.5 + 1.0E-9));
		double[] rows = new double[n];
		for (int i = 0; i < n; i++) {
			rows[i] = h / 2.0 + (i - (n - 1) / 2.0) * 2.5;
		}
		return rows;
	}

	/**
	 * Side stud centres: {position along the face in studs (x, or z on the x faces), y in plates, the
	 * face's {@link Direction#get3DDataValue()}}.
	 */
	public List<double[]> sideStudCentres() {
		List<double[]> out = new ArrayList<>();
		if (!sideStuds) {
			return out;
		}
		for (Direction face : sideStudFaces.isEmpty() ? List.of(Direction.NORTH) : sideStudFaces) {
			int columns = face.getAxis() == Direction.Axis.X ? studsZ : studsX;
			int id = face.get3DDataValue();
			if (sideStudSpots.isEmpty()) {
				for (double row : sideStudRows()) {
					for (int i = 0; i < columns; i++) {
						out.add(new double[] {i + 0.5, row, id});
					}
				}
				continue;
			}
			for (double[] s : sideStudSpots) {
				if (Double.isNaN(s[0])) {
					for (int i = 0; i < columns; i++) {
						out.add(new double[] {i + 0.5, s[1], id});
					}
				} else {
					out.add(new double[] {s[0], s[1], id});
				}
			}
		}
		return out;
	}

	public boolean isShaped() {
		return !shape.isEmpty();
	}

	public LegoTab tab() {
		return kind.getTab();
	}

	public String blockId(LegoColor color) {
		return "lego_" + designId + "_" + color.getId();
	}

	public int length() {
		return studsZ;
	}
}
