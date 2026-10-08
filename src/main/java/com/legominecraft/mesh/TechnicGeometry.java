package com.legominecraft.mesh;

import com.legominecraft.block.LegoPartDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.legominecraft.mesh.Mesh.BODY;
import static com.legominecraft.mesh.Mesh.HOLE;

/**
 * Studless Technic geometry in part space, in pixels (1 module = 8 mm = 16 px):
 * x = length/axis direction, y = beam hole axis, z = the remaining axis.
 * The anchor (origin) is the centre of the first hole/module.
 */
public final class TechnicGeometry {
	/** Liftarm half-thickness: 7.2 mm wide on an 8 mm pitch. */
	public static final double BEAM_R = 7.2;
	public static final double HOLE_HALF = 4.8;
	private static final double HOLE_R = 4.8;
	private static final double PIN_R = 4.7;
	private static final double PIN_SHAPE = 4.2;
	private static final double COLLAR_R = 5.8;
	private static final double AXLE_ARM = 4.6;
	private static final double AXLE_W = 1.7;
	private static final double BUSH_R = 6.2;
	private static final double END = 7.6;
	private static final double DECAL = 0.03;
	private static final int SEG = 12;

	/** Box in part space. */
	public record Box(double x0, double x1, double y0, double y1, double z0, double z1) {
	}

	/** Hole or axial module in part space; {@code axis} is 0 = x, 1 = y, 2 = z. */
	public record PartHole(double x, double y, double z, int axis, boolean cross) {
	}

	private static final Map<Integer, TechnicGeometry> CACHE = new ConcurrentHashMap<>();

	public final List<Box> boxes = new ArrayList<>();
	public final Mesh mesh = new Mesh();
	public final List<PartHole> holes = new ArrayList<>();
	public final List<PartHole> modules = new ArrayList<>();

	private TechnicGeometry() {
	}

	public static TechnicGeometry of(LegoPartDefinition def) {
		return CACHE.computeIfAbsent(def.designId(), id -> build(def));
	}

	private static TechnicGeometry build(LegoPartDefinition def) {
		TechnicGeometry g = new TechnicGeometry();
		int n = Math.max(1, def.length());
		double last = 16.0 * (n - 1);
		switch (def.kind()) {
			case BEAM -> g.beam(n);
			case PIN -> {
				g.pinBody(-END, last + END, true, true);
				g.collar(8);
				g.boxes.add(new Box(-END, last + END, -PIN_SHAPE, PIN_SHAPE, -PIN_SHAPE, PIN_SHAPE));
				for (int k = 0; k < n; k++) {
					g.modules.add(new PartHole(16 * k, 0, 0, 0, false));
				}
			}
			case AXLE_PIN -> {
				g.pinBody(-END, 7.3, true, false);
				g.collar(8);
				g.axleBody(8.7, 16 + END);
				g.boxes.add(new Box(-END, 16 + END, -PIN_SHAPE, PIN_SHAPE, -PIN_SHAPE, PIN_SHAPE));
				g.modules.add(new PartHole(0, 0, 0, 0, false));
				g.modules.add(new PartHole(16, 0, 0, 0, true));
			}
			case AXLE -> {
				g.axleBody(-END, last + END);
				g.boxes.add(new Box(-END, last + END, -PIN_SHAPE, PIN_SHAPE, -PIN_SHAPE, PIN_SHAPE));
				for (int k = 0; k < n; k++) {
					g.modules.add(new PartHole(16 * k, 0, 0, 0, true));
				}
			}
			case BUSH -> g.ring(-END, END, BUSH_R, SEG);
			case HALF_BUSH -> g.ring(-END, -0.4, BUSH_R, SEG);
			case GEAR -> g.gear(def.teeth(), -END, -0.4, false);
			case DOUBLE_GEAR -> g.gear(def.teeth(), -END, END, true);
			default -> g.boxes.add(new Box(-8, 8, -8, 8, -8, 8));
		}
		return g;
	}

	// ---- beams ----

	private void beam(int n) {
		double last = 16.0 * (n - 1);
		int half = SEG / 2;
		List<double[]> lp = new ArrayList<>();
		for (int i = 0; i <= half; i++) {
			double a = -Math.PI / 2 + Math.PI * i / half;
			lp.add(new double[] {last + BEAM_R * Math.cos(a), BEAM_R * Math.sin(a)});
		}
		boolean round = n == 1;
		for (int i = round ? 1 : 0; i <= (round ? half - 1 : half); i++) {
			double a = Math.PI / 2 + Math.PI * i / half;
			lp.add(new double[] {BEAM_R * Math.cos(a), BEAM_R * Math.sin(a)});
		}
		// extrude along y: (u, v) = (z, x)
		double[][] uv = new double[lp.size()][];
		for (int i = 0; i < uv.length; i++) {
			uv[i] = new double[] {lp.get(i)[1], lp.get(i)[0]};
		}
		mesh.extrude(1, uv, -BEAM_R, BEAM_R, BODY, Mesh.Clip.CELL, true, true);

		double x = -BEAM_R;
		for (int k = 0; k < n; k++) {
			double c = 16.0 * k;
			double[][] disc = Mesh.regular(0, c, HOLE_R, SEG);
			mesh.cap(1, disc, BEAM_R + DECAL, 1, HOLE, Mesh.Clip.CELL);
			mesh.cap(1, disc, -BEAM_R - DECAL, -1, HOLE, Mesh.Clip.CELL);
			holes.add(new PartHole(c, 0, 0, 1, false));

			addBox(x, c - HOLE_HALF, -BEAM_R, BEAM_R, -BEAM_R, BEAM_R);
			addBox(c - HOLE_HALF, c + HOLE_HALF, -BEAM_R, BEAM_R, -BEAM_R, -HOLE_HALF);
			addBox(c - HOLE_HALF, c + HOLE_HALF, -BEAM_R, BEAM_R, HOLE_HALF, BEAM_R);
			x = c + HOLE_HALF;
		}
		addBox(x, last + BEAM_R, -BEAM_R, BEAM_R, -BEAM_R, BEAM_R);
	}

	private void addBox(double x0, double x1, double y0, double y1, double z0, double z1) {
		if (x1 > x0 && y1 > y0 && z1 > z0) {
			boxes.add(new Box(x0, x1, y0, y1, z0, z1));
		}
	}

	// ---- pins & axles (axis along x; (u, v) = (y, z)) ----

	private void pinBody(double x0, double x1, boolean hollow0, boolean hollow1) {
		mesh.extrude(0, Mesh.regular(0, 0, PIN_R, SEG), x0, x1, BODY, Mesh.Clip.CELL, true, true);
		double[][] end = Mesh.regular(0, 0, 2.6, SEG);
		if (hollow0) {
			mesh.cap(0, end, x0 - DECAL, -1, HOLE, Mesh.Clip.CELL);
		}
		if (hollow1) {
			mesh.cap(0, end, x1 + DECAL, 1, HOLE, Mesh.Clip.CELL);
		}
	}

	private void collar(double x) {
		mesh.extrude(0, Mesh.regular(0, 0, COLLAR_R, SEG), x - 0.7, x + 0.7, BODY, Mesh.Clip.CELL, true, true);
	}

	private void axleBody(double x0, double x1) {
		mesh.extrude(0, Mesh.rect(-AXLE_ARM, AXLE_ARM, -AXLE_W, AXLE_W), x0, x1, BODY, Mesh.Clip.CELL, true, true);
		mesh.extrude(0, Mesh.rect(-AXLE_W, AXLE_W, AXLE_W, AXLE_ARM), x0, x1, BODY, Mesh.Clip.CELL, true, true);
		mesh.extrude(0, Mesh.rect(-AXLE_W, AXLE_W, -AXLE_ARM, -AXLE_W), x0, x1, BODY, Mesh.Clip.CELL, true, true);
	}

	private void crossDecals(double x0, double x1) {
		for (double[][] arm : new double[][][] {
			Mesh.rect(-AXLE_ARM, AXLE_ARM, -AXLE_W, AXLE_W),
			Mesh.rect(-AXLE_W, AXLE_W, -AXLE_ARM, AXLE_ARM)
		}) {
			mesh.cap(0, arm, x0 - DECAL, -1, HOLE, Mesh.Clip.CELL);
			mesh.cap(0, arm, x1 + DECAL, 1, HOLE, Mesh.Clip.CELL);
		}
	}

	/** Collision ring around the central cross hole (square, half-size {@code outer}). */
	private void ringBoxes(double x0, double x1, double outer) {
		addBox(x0, x1, -outer, outer, -outer, -HOLE_HALF);
		addBox(x0, x1, -outer, outer, HOLE_HALF, outer);
		addBox(x0, x1, -outer, -HOLE_HALF, -HOLE_HALF, HOLE_HALF);
		addBox(x0, x1, HOLE_HALF, outer, -HOLE_HALF, HOLE_HALF);
	}

	private void ring(double x0, double x1, double r, int segments) {
		mesh.extrude(0, Mesh.regular(0, 0, r, segments), x0, x1, BODY, Mesh.Clip.CELL, true, true);
		crossDecals(x0, x1);
		ringBoxes(x0, x1, r);
		holes.add(new PartHole(0, 0, 0, 0, true));
	}

	// ---- gears (module 1: pitch radius = Z mm/2 = Z px) ----

	private void gear(int teeth, double x0, double x1, boolean doubleBevel) {
		double root = Math.max(teeth - 2.4, 5.0);
		double tip = teeth + 2.0;
		int segments = Math.max(16, teeth * 2);
		mesh.extrude(0, Mesh.regular(0, 0, root, segments), x0, x1, BODY, Mesh.Clip.CELL, true, true);
		double baseHalf = Math.min(2.0, Math.PI * root / teeth * 0.6);
		double tipHalf = baseHalf * 0.5;
		double mid = (x0 + x1) / 2;
		double thickness = x1 - x0;
		for (int i = 0; i < teeth; i++) {
			double a = Math.PI * 2 * i / teeth;
			if (doubleBevel) {
				double core = thickness * 0.2;
				mesh.extrude(0, tooth(a, root - 0.5, tip, baseHalf, tipHalf), mid - core, mid + core,
					BODY, Mesh.Clip.CELL, true, true);
				double[][] side = tooth(a, root - 0.5, tip - 1.6, baseHalf, tipHalf * 1.2);
				mesh.extrude(0, side, x0 + 0.8, mid - core, BODY, Mesh.Clip.CELL, true, false);
				mesh.extrude(0, side, mid + core, x1 - 0.8, BODY, Mesh.Clip.CELL, false, true);
			} else {
				mesh.extrude(0, tooth(a, root - 0.5, tip, baseHalf, tipHalf), x0, x1, BODY, Mesh.Clip.CELL, true, true);
			}
		}
		crossDecals(x0, x1);
		ringBoxes(x0, x1, Math.max(root * 0.8, BUSH_R));
		holes.add(new PartHole(0, 0, 0, 0, true));
	}

	/** Convex tooth outline in the (y, z) plane, pointing along angle {@code a}. */
	private static double[][] tooth(double a, double r0, double r1, double w0, double w1) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		double[][] local = {{r0, -w0}, {r1, -w1}, {r1, w1}, {r0, w0}};
		double[][] out = new double[4][];
		for (int i = 0; i < 4; i++) {
			out[i] = new double[] {local[i][0] * c - local[i][1] * s, local[i][0] * s + local[i][1] * c};
		}
		return out;
	}
}
