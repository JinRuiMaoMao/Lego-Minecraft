package com.legominecraft.mesh;

import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.multipart.LegoPartData;
import com.legominecraft.multipart.LegoPiece;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static com.legominecraft.mesh.Mesh.BODY;
import static com.legominecraft.mesh.Mesh.HOLE;
import static com.legominecraft.mesh.Mesh.p;

/**
 * Procedural mesh of one studded piece fragment. The whole piece is generated once in piece-local
 * space, rotated into the world, then clipped to the fragment's cell — so seams only appear between
 * different pieces, never inside one.
 */
public final class BrickMesh {
	private static final double EDGE = 0.1;
	private static final double STUD_R = 4.8;
	private static final double STUD_H = 2.8;
	private static final double SIDE_HOLE_R = 4.4;
	private static final double TOP_HOLE_R = 4.4;
	private static final double DECAL = 0.03;
	private static final double SLOPE_LIP = 2.0;
	private static final double GRILLE_SLOT = 1.6;
	private static final double OPEN_STUD_R = 3.0;
	private static final double GEM_GIRDLE = 0.4;
	private static final double GEM_TABLE = 0.55;
	private static final int ROUND_SEGMENTS = 12;
	private static final int BAR_SEGMENTS = 12;
	private static final int THICK_BAR_SEGMENTS = 16;
	private static final int CURVE_SEGMENTS = 8;
	private static final int LOG_SEGMENTS = 6;
	private static final double MASONRY_RELIEF = 0.8;
	private static final double MASONRY_GAP = 1.0;
	/** Drawn bar radius (px) per unit of catalog radius: the standard 1.125-unit bar is 3 px. */
	private static final double BAR_R_PER_UNIT = 3.0 / LegoPartDefinition.Bar.RADIUS;
	/** A Technic pin hole's radius relative to the round connector it goes through. */
	private static final double PIN_HOLE_RATIO = 0.67;
	private static final double CLIP_INNER = 3.2;
	private static final double CLIP_OUTER = 5.2;
	/** Half-angle of a clip's opening. */
	private static final double CLIP_GAP = Math.PI / 4;

	private BrickMesh() {
	}

	/** Render polygons of one fragment, in px relative to its cell. */
	public static List<Mesh.Poly> build(LegoPartData data) {
		return data.piece().fragmentPolys(data.dx(), data.dy(), data.dz());
	}

	/** The whole piece in piece-local px; {@link LegoPiece} places and clips it. */
	public static Mesh local(LegoPiece piece) {
		Mesh mesh = new Mesh();
		buildBody(piece, mesh);
		buildBracket(piece, mesh);
		buildStuds(piece, mesh);
		buildSideStuds(piece, mesh);
		buildHoles(piece, mesh);
		buildGrille(piece, mesh);
		buildFittings(piece, mesh);
		return mesh;
	}

	private static void buildFittings(LegoPiece piece, Mesh mesh) {
		LegoPartDefinition.Fittings fittings = piece.def().fittings();
		double u = LegoPiece.FIT_PX;
		for (double[] b : fittings.boxes()) {
			mesh.box(b[0] * u + EDGE, b[3] * u - EDGE, b[1] * u + EDGE, b[4] * u - EDGE, b[2] * u + EDGE, b[5] * u - EDGE,
				BODY, Mesh.Clip.CELL);
		}
		for (LegoPartDefinition.Bar bar : fittings.bars()) {
			int axis = 0;
			double len = 0;
			for (int k = 0; k < 3; k++) {
				double d = Math.abs(bar.to()[k] - bar.from()[k]);
				if (d > len) {
					len = d;
					axis = k;
				}
			}
			double[] c = bar.from();
			double a0 = Math.min(bar.from()[axis], bar.to()[axis]) * u;
			double a1 = Math.max(bar.from()[axis], bar.to()[axis]) * u;
			double r = bar.radius() * BAR_R_PER_UNIT;
			int sides = bar.sides() > 0 ? bar.sides() : r > BAR_R_PER_UNIT * 1.5 ? THICK_BAR_SEGMENTS : BAR_SEGMENTS;
			double cu = c[(axis + 1) % 3] * u;
			double cv = c[(axis + 2) % 3] * u;
			mesh.extrude(axis, Mesh.regular(cu, cv, r, sides), a0, a1, BODY, Mesh.Clip.CELL, true, true);
			if (bar.hollow()) {
				double[][] hole = Mesh.regular(cu, cv, r * PIN_HOLE_RATIO, sides);
				mesh.cap(axis, hole, a0 - DECAL, -1, HOLE, Mesh.Clip.CELL);
				mesh.cap(axis, hole, a1 + DECAL, 1, HOLE, Mesh.Clip.CELL);
			}
		}
		for (LegoPartDefinition.Clip clip : fittings.clips()) {
			buildClip(clip, mesh);
		}
	}

	/**
	 * A C-shaped ring around the clip axis (a closed ring when it has no opening), built from convex
	 * quad prisms so it renders through the normal convex-face path. Its corners line up with a
	 * {@link #THICK_BAR_SEGMENTS}-sided bar of the same radius, so a blind hole on top of a thick bar
	 * reads as one cylinder; the hole's bottom is drawn dark.
	 */
	private static void buildClip(LegoPartDefinition.Clip clip, Mesh mesh) {
		double u = LegoPiece.FIT_PX;
		int axis = clip.axis().ordinal();
		double cu = clip.at()[(axis + 1) % 3] * u;
		double cv = clip.at()[(axis + 2) % 3] * u;
		double a0 = (clip.at()[axis] - clip.halfWidth()) * u + EDGE;
		double a1 = (clip.at()[axis] + clip.halfWidth()) * u - EDGE;
		double gap = Double.NaN;
		if (clip.open() != null && !clip.blind()) {
			double[] n = {clip.open().getStepX(), clip.open().getStepY(), clip.open().getStepZ()};
			gap = Math.atan2(n[(axis + 2) % 3], n[(axis + 1) % 3]);
		}
		int segments = THICK_BAR_SEGMENTS;
		double corner = 1 / Math.cos(Math.PI / segments);
		double inner = CLIP_INNER * corner;
		double outer = (clip.outer() > 0 ? clip.outer() * BAR_R_PER_UNIT : CLIP_OUTER) * corner;
		for (int i = 0; i < segments; i++) {
			double s0 = Math.PI * 2 * (i - 0.5) / segments;
			double s1 = Math.PI * 2 * (i + 0.5) / segments;
			double mid = (s0 + s1) / 2;
			if (!Double.isNaN(gap)) {
				double diff = Math.abs(Math.IEEEremainder(mid - gap, Math.PI * 2));
				if (diff < CLIP_GAP) {
					continue;
				}
			}
			double[][] quad = {
				{cu + Math.cos(s0) * inner, cv + Math.sin(s0) * inner},
				{cu + Math.cos(s0) * outer, cv + Math.sin(s0) * outer},
				{cu + Math.cos(s1) * outer, cv + Math.sin(s1) * outer},
				{cu + Math.cos(s1) * inner, cv + Math.sin(s1) * inner},
			};
			mesh.extrude(axis, quad, a0, a1, BODY, Mesh.Clip.CELL, true, true);
		}
		if (clip.blind()) {
			int sign = clip.open() == null ? 1 : clip.open().getAxisDirection().getStep();
			double bottom = (clip.at()[axis] - sign * clip.halfWidth()) * u + sign * DECAL;
			mesh.cap(axis, Mesh.regular(cu, cv, CLIP_INNER, segments), bottom, sign, HOLE, Mesh.Clip.CELL);
		}
	}

	private static void buildBracket(LegoPiece piece, Mesh mesh) {
		LegoPartDefinition.Bracket bracket = piece.def().bracket();
		if (bracket == null) {
			return;
		}
		int base = piece.heightPlates();
		double y0 = bracket.bottomPlates(base) * LegoPiece.PLATE_PX;
		double y1 = y0 + bracket.plates() * LegoPiece.PLATE_PX;
		double x0 = bracket.x0() * LegoPiece.PX;
		double x1 = (bracket.x0() + bracket.width()) * LegoPiece.PX;
		double face = -bracket.thickness() * LegoPiece.PLATE_PX + EDGE;
		mesh.box(x0 + EDGE, x1 - EDGE, y0 + EDGE, y1 - EDGE, face, EDGE, BODY, Mesh.Clip.CELL);
		for (int i = 0; i < bracket.width(); i++) {
			for (int j = 0; j < bracket.height(); j++) {
				sideStud(mesh, x0 + i * LegoPiece.PX + 8, bracket.studPlates(j, base) * LegoPiece.PLATE_PX, face);
			}
		}
	}

	private static void buildSideStuds(LegoPiece piece, Mesh mesh) {
		for (double[] s : piece.def().sideStudCentres()) {
			sideStud(mesh, LegoPiece.sideStudLocal(s, piece.widthPx(), piece.depthPx(), -EDGE));
		}
	}

	/** Stud on a face at local z = {@code face}, pointing −z. */
	private static void sideStud(Mesh mesh, double x, double y, double face) {
		sideStud(mesh, new double[] {x, y, face, 0, 0, -1});
	}

	/** Stud standing on a face at {x, y, z}, pointing along the axis-aligned {dx, dy, dz}. */
	private static void sideStud(Mesh mesh, double[] s) {
		int axis = s[3] != 0 ? 0 : s[4] != 0 ? 1 : 2;
		double dir = s[3 + axis];
		mesh.anchor(p(s[0] - s[3] * 0.05, s[1] - s[4] * 0.05, s[2] - s[5] * 0.05));
		double[][] disc = Mesh.regular(s[(axis + 1) % 3], s[(axis + 2) % 3], STUD_R, ROUND_SEGMENTS);
		double face = s[axis];
		mesh.extrude(axis, disc, Math.min(face, face + dir * STUD_H), Math.max(face, face + dir * STUD_H), BODY,
			Mesh.Clip.TOP, dir < 0, dir > 0);
		mesh.anchor(null);
	}

	private static void buildBody(LegoPiece piece, Mesh mesh) {
		double w = piece.widthPx();
		double d = piece.depthPx();
		double h = piece.heightPlates() * LegoPiece.PX / 3.0 - EDGE;
		switch (piece.def().kind()) {
			case SLOPE, CHEESE_SLOPE -> buildSlope(w, d, h, piece.slopeEndPx(), false, mesh);
			case INVERTED_SLOPE -> buildSlope(w, d, h, piece.slopeEndPx(), true, mesh);
			case CURVED_SLOPE -> buildCurvedSlope(piece, w, d, h, mesh);
			case ROUND_BRICK, ROUND_PLATE, ROUND_TILE -> mesh.extrude(1, Mesh.regular(d / 2, w / 2, w / 2 - 0.2, ROUND_SEGMENTS),
				0, h, BODY, Mesh.Clip.CELL, true, true);
			case GEM -> buildGem(inset(piece.localPolys().getFirst(), EDGE), piece.def().bodyPlates() * LegoPiece.PLATE_PX - EDGE, mesh);
			case BAR -> {
			}
			case WALL_PANEL -> {
				for (LegoPiece.LocalBox b : piece.panelBoxes()) {
					mesh.box(b.x0() + EDGE, b.x1() - EDGE, b.y0() * LegoPiece.PLATE_PX, Math.min(b.y1() * LegoPiece.PLATE_PX, h),
						b.z0() + EDGE, b.z1() - EDGE, BODY, Mesh.Clip.CELL);
				}
			}
			default -> {
				if (piece.def().profile().equals("log")) {
					buildLogs(w, d, h, mesh);
				} else if (piece.def().profile().equals("masonry")) {
					buildMasonry(w, d, h, mesh);
				} else if (piece.def().isShaped()) {
					for (double[][] poly : piece.localPolys()) {
						double[][] inset = inset(poly, EDGE);
						double[][] uv = new double[inset.length][];
						for (int i = 0; i < inset.length; i++) {
							uv[i] = new double[] {inset[i][1], inset[i][0]};
						}
						mesh.extrude(1, uv, 0, h, BODY, Mesh.Clip.CELL, true, true);
					}
				} else {
					mesh.box(EDGE, w - EDGE, 0, h, EDGE, d - EDGE, BODY, Mesh.Clip.CELL);
				}
			}
		}
	}

	/**
	 * Palisade: one upright log per stud column, its front and back bulging to the full depth at the
	 * column's middle and leaving a groove 30 % deep where neighbouring logs meet (as LDraw 30136).
	 */
	private static void buildLogs(double w, double d, double h, Mesh mesh) {
		double zc = d / 2;
		double s = (zc - EDGE) / Math.sqrt(2);
		int n = LOG_SEGMENTS;
		for (double cx = LegoPiece.PX / 2.0; cx < w; cx += LegoPiece.PX) {
			double half = LegoPiece.PX / 2.0 - EDGE;
			double[][] uv = new double[(n + 1) * 2][];
			for (int i = 0; i <= n; i++) {
				double t = Math.PI / 2 * i / n;
				double x = cx + half * (Math.cos(t) - Math.sin(t));
				double dz = s * (Math.cos(t) + Math.sin(t));
				uv[i] = new double[] {zc - dz, x};
				uv[2 * n + 1 - i] = new double[] {zc + dz, x};
			}
			mesh.extrude(1, uv, 0, h, BODY, Mesh.Clip.CELL, true, true);
		}
	}

	/**
	 * Masonry profile: both long faces carry two courses of raised bricks, two studs long and staggered
	 * by one stud, over a core recessed by {@link #MASONRY_RELIEF} (as LDraw 98283).
	 */
	private static void buildMasonry(double w, double d, double h, Mesh mesh) {
		double r = MASONRY_RELIEF;
		double g = MASONRY_GAP;
		mesh.box(EDGE, w - EDGE, 0, h, r, d - r, BODY, Mesh.Clip.CELL);
		double px = LegoPiece.PX;
		for (int course = 0; course < 2; course++) {
			double y0 = course == 0 ? 0 : h / 2 + g / 2;
			double y1 = course == 0 ? h / 2 - g / 2 : h;
			List<Double> edges = new ArrayList<>();
			edges.add(EDGE);
			for (double x = course == 0 ? px / 2 : px * 1.5; x < w - 1.0E-6; x += px * 2) {
				edges.add(x);
			}
			edges.add(w - EDGE);
			for (int i = 0; i + 1 < edges.size(); i++) {
				double x0 = i == 0 ? edges.get(i) : edges.get(i) + g / 2;
				double x1 = i + 2 == edges.size() ? edges.get(i + 1) : edges.get(i + 1) - g / 2;
				mesh.box(x0, x1, y0, y1, EDGE, r + 0.01, BODY, Mesh.Clip.CELL);
				mesh.box(x0, x1, y0, y1, d - r - 0.01, d - EDGE, BODY, Mesh.Clip.CELL);
			}
		}
	}

	/** Straight girdle up to {@link #GEM_GIRDLE} of the height, then facets narrowing to a flat table. */
	private static void buildGem(double[][] poly, double h, Mesh mesh) {
		int n = poly.length;
		double cx = 0;
		double cz = 0;
		for (double[] q : poly) {
			cx += q[0] / n;
			cz += q[1] / n;
		}
		double yMid = h * GEM_GIRDLE;
		double[][] uv = new double[n][];
		double[][] table = new double[n][];
		for (int i = 0; i < n; i++) {
			uv[i] = new double[] {poly[i][1], poly[i][0]};
			table[i] = new double[] {cx + (poly[i][0] - cx) * GEM_TABLE, cz + (poly[i][1] - cz) * GEM_TABLE};
		}
		mesh.extrude(1, uv, 0, yMid, BODY, Mesh.Clip.CELL, true, false);
		for (int i = 0; i < n; i++) {
			double[] a = poly[i];
			double[] b = poly[(i + 1) % n];
			double[] at = table[i];
			double[] bt = table[(i + 1) % n];
			double mx = (a[0] + b[0]) / 2 - cx;
			double mz = (a[1] + b[1]) / 2 - cz;
			mesh.face(BODY, Mesh.Clip.CELL, mx, Math.hypot(mx, mz), mz,
				p(a[0], yMid, a[1]), p(b[0], yMid, b[1]), p(bt[0], h, bt[1]), p(at[0], h, at[1]));
		}
		double[][] top = new double[n][];
		for (int i = 0; i < n; i++) {
			top[i] = new double[] {table[i][1], table[i][0]};
		}
		mesh.cap(1, top, h, 1, BODY, Mesh.Clip.CELL);
	}

	/** Shrinks a convex (x, z) polygon by moving every edge inward by {@code d}. */
	private static double[][] inset(double[][] poly, double d) {
		int n = poly.length;
		double area = 0;
		for (int i = 0; i < n; i++) {
			area += poly[i][0] * poly[(i + 1) % n][1] - poly[(i + 1) % n][0] * poly[i][1];
		}
		double sign = Math.signum(area);
		double[][] lines = new double[n][];
		for (int i = 0; i < n; i++) {
			double[] a = poly[i];
			double[] b = poly[(i + 1) % n];
			double ex = b[0] - a[0];
			double ez = b[1] - a[1];
			double len = Math.sqrt(ex * ex + ez * ez);
			double nx = -sign * ez / len;
			double nz = sign * ex / len;
			lines[i] = new double[] {a[0] + nx * d, a[1] + nz * d, ex, ez};
		}
		double[][] out = new double[n][];
		for (int i = 0; i < n; i++) {
			double[] l0 = lines[(i + n - 1) % n];
			double[] l1 = lines[i];
			double den = l0[2] * l1[3] - l0[3] * l1[2];
			if (Math.abs(den) < 1.0E-9) {
				out[i] = new double[] {l1[0], l1[1]};
				continue;
			}
			double t = ((l1[0] - l0[0]) * l1[3] - (l1[1] - l0[1]) * l1[2]) / den;
			out[i] = new double[] {l0[0] + l0[2] * t, l0[1] + l0[3] * t};
		}
		return out;
	}

	/**
	 * Slope rising from the low lip at local z = 0 to full height at {@code slopeEnd}, flat behind it.
	 * Inverted slopes are the same solid mirrored vertically (flat top, sloped underside).
	 */
	private static void buildSlope(double w, double d, double h, double slopeEnd, boolean inverted, Mesh mesh) {
		Mesh.Clip c = Mesh.Clip.CELL;
		double x0 = EDGE;
		double x1 = w - EDGE;
		double z0 = EDGE;
		double z1 = d - EDGE;
		double zs = Math.min(slopeEnd, z1);
		double lip = Math.min(SLOPE_LIP, h / 3);
		double s = inverted ? -1 : 1;
		DoubleUnaryOperator y = v -> inverted ? h - v : v;

		mesh.face(BODY, c, 0, -s, 0, p(x0, y.applyAsDouble(0), z0), p(x1, y.applyAsDouble(0), z0),
			p(x1, y.applyAsDouble(0), z1), p(x0, y.applyAsDouble(0), z1));
		mesh.face(BODY, c, 0, 0, 1, p(x0, 0, z1), p(x1, 0, z1), p(x1, h, z1), p(x0, h, z1));
		mesh.face(BODY, c, 0, 0, -1, p(x0, y.applyAsDouble(0), z0), p(x1, y.applyAsDouble(0), z0),
			p(x1, y.applyAsDouble(lip), z0), p(x0, y.applyAsDouble(lip), z0));
		if (z1 - zs > 1.0E-3) {
			mesh.face(BODY, c, 0, s, 0, p(x0, y.applyAsDouble(h), zs), p(x1, y.applyAsDouble(h), zs),
				p(x1, y.applyAsDouble(h), z1), p(x0, y.applyAsDouble(h), z1));
		}
		mesh.face(BODY, c, 0, s * (zs - z0), -(h - lip),
			p(x0, y.applyAsDouble(lip), z0), p(x1, y.applyAsDouble(lip), z0),
			p(x1, y.applyAsDouble(h), zs), p(x0, y.applyAsDouble(h), zs));
		for (double x : new double[] {x0, x1}) {
			double nx = x == x0 ? -1 : 1;
			if (z1 - zs > 1.0E-3) {
				mesh.face(BODY, c, nx, 0, 0,
					p(x, y.applyAsDouble(0), z0), p(x, y.applyAsDouble(0), z1), p(x, y.applyAsDouble(h), z1),
					p(x, y.applyAsDouble(h), zs), p(x, y.applyAsDouble(lip), z0));
			} else {
				mesh.face(BODY, c, nx, 0, 0,
					p(x, y.applyAsDouble(0), z0), p(x, y.applyAsDouble(0), z1), p(x, y.applyAsDouble(h), z1),
					p(x, y.applyAsDouble(lip), z0));
			}
		}
	}

	/**
	 * Convex side profile (bottom, back, curved top down to the front lip) extruded across the width,
	 * split into runs of rows sharing an underside height so raised rows leave their recess open.
	 */
	private static void buildCurvedSlope(LegoPiece piece, double w, double d, double h, Mesh mesh) {
		LegoPartDefinition def = piece.def();
		double[] curve = LegoPiece.curveDepths(def, CURVE_SEGMENTS * def.studsZ());
		DoubleUnaryOperator top = z -> Math.min(LegoPiece.curvePlates(def, z / d) * LegoPiece.PLATE_PX, h);
		int iz = 0;
		while (iz < def.studsZ()) {
			int end = iz + 1;
			while (end < def.studsZ() && def.bottomPlates(end) == def.bottomPlates(iz)) {
				end++;
			}
			double za = iz == 0 ? EDGE : iz * LegoPiece.PX;
			double zb = end == def.studsZ() ? d - EDGE : end * LegoPiece.PX;
			double bottom = def.bottomPlates(iz) * LegoPiece.PLATE_PX + (iz == 0 ? 0 : EDGE);
			List<double[]> uv = new ArrayList<>(List.of(new double[] {bottom, za}, new double[] {bottom, zb},
				new double[] {top.applyAsDouble(zb), zb}));
			for (int i = curve.length - 1; i >= 0; i--) {
				double z = curve[i] * d;
				if (z > za + 1.0E-6 && z < zb - 1.0E-6) {
					uv.add(new double[] {top.applyAsDouble(z), z});
				}
			}
			uv.add(new double[] {top.applyAsDouble(za), za});
			mesh.extrude(0, uv.toArray(double[][]::new), EDGE, w - EDGE, BODY, Mesh.Clip.CELL, true, true);
			iz = end;
		}
	}

	private static void buildStuds(LegoPiece piece, Mesh mesh) {
		LegoPartDefinition def = piece.def();
		if (!def.kind().hasStuds() && def.studs().isEmpty()) {
			return;
		}
		for (double[] c : piece.topStudCentres()) {
			if (c[3] < 0) {
				double base = c[2] + EDGE;
				mesh.anchor(p(c[0], base + 0.05, c[1]));
				mesh.extrude(1, Mesh.regular(c[1], c[0], STUD_R, ROUND_SEGMENTS), base - STUD_H, base,
					BODY, Mesh.Clip.TOP, true, false);
				if (def.isOpenStud(c[0] / LegoPiece.PX, c[1] / LegoPiece.PX)) {
					mesh.cap(1, Mesh.regular(c[1], c[0], OPEN_STUD_R, ROUND_SEGMENTS), base - STUD_H - DECAL, -1, HOLE, Mesh.Clip.TOP);
				}
				mesh.anchor(null);
				continue;
			}
			double top = c[2] - EDGE;
			mesh.anchor(p(c[0], top - 0.05, c[1]));
			mesh.extrude(1, Mesh.regular(c[1], c[0], STUD_R, ROUND_SEGMENTS), top, top + STUD_H,
				BODY, Mesh.Clip.TOP, false, true);
			if (def.isOpenStud(c[0] / LegoPiece.PX, c[1] / LegoPiece.PX)) {
				mesh.cap(1, Mesh.regular(c[1], c[0], OPEN_STUD_R, ROUND_SEGMENTS), top + STUD_H + DECAL, 1, HOLE, Mesh.Clip.TOP);
			}
			mesh.anchor(null);
		}
	}

	/** Dark slots across the top, or upright slots on the local −z face. */
	private static void buildGrille(LegoPiece piece, Mesh mesh) {
		String grille = piece.def().grille();
		double w = piece.widthPx();
		double d = piece.depthPx();
		double h = piece.heightPlates() * LegoPiece.PLATE_PX - EDGE;
		if (grille.equals("top")) {
			int n = piece.def().studsZ() * 2 + 1;
			double top = h + DECAL;
			mesh.anchor(p(w / 2, h - 0.5, d / 2));
			for (int i = 1; i <= n; i++) {
				double z = d * i / (n + 1);
				mesh.cap(1, Mesh.rect(z - GRILLE_SLOT / 2, z + GRILLE_SLOT / 2, 2, w - 2), top, 1, HOLE, Mesh.Clip.TOP);
			}
			mesh.anchor(null);
		} else if (grille.equals("side")) {
			int n = piece.def().studsX() * 2 + 1;
			for (int i = 1; i <= n; i++) {
				double x = w * i / (n + 1);
				mesh.cap(2, Mesh.rect(x - GRILLE_SLOT / 2, x + GRILLE_SLOT / 2, 2, h - 2), EDGE - DECAL, -1, HOLE, Mesh.Clip.CELL);
			}
		}
	}

	private static void buildHoles(LegoPiece piece, Mesh mesh) {
		for (LegoPiece.LocalHole hole : piece.localHoles()) {
			if (hole.vertical()) {
				double top = piece.heightPlates() * LegoPiece.PX / 3.0 - EDGE + DECAL;
				mesh.anchor(p(hole.x(), top - 0.5, hole.z()));
				mesh.cap(1, Mesh.regular(hole.z(), hole.x(), TOP_HOLE_R, ROUND_SEGMENTS), top, 1, HOLE, Mesh.Clip.TOP);
				mesh.anchor(null);
			} else {
				double[][] disc = Mesh.regular(hole.y(), hole.z(), SIDE_HOLE_R, ROUND_SEGMENTS);
				mesh.cap(0, disc, EDGE - DECAL, -1, HOLE, Mesh.Clip.CELL);
				mesh.cap(0, disc, piece.widthPx() - EDGE + DECAL, 1, HOLE, Mesh.Clip.CELL);
			}
		}
	}
}
