package com.legominecraft.mesh;

import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Convex-polygon mesh builder shared by server (fragment coverage) and client (rendering).
 * All coordinates are in pixels (16 per block).
 */
public final class Mesh {
	public static final int BODY = 0;
	public static final int HOLE = 1;
	public static final double CLIP_EPS = 1.0E-4;

	/** CELL polys are clipped to their cell; TOP polys (studs) only render in the piece's top cell. */
	public enum Clip { CELL, TOP }

	/**
	 * @param anchor for TOP polys (studs): a point just inside the body under the stud's base; the stud is
	 *               rendered unclipped with whichever cell contains this point. Null for CELL polys.
	 */
	public record Raw(double[][] v, int material, Clip clip, double[] anchor) {
		public Raw(double[][] v, int material, Clip clip) {
			this(v, material, clip, null);
		}
	}

	/** @param cull face to cull against a neighbour, or null */
	public record Poly(double[][] v, int material, Direction cull) {
	}

	private final List<Raw> raws = new ArrayList<>();
	private double[] anchor;

	public List<Raw> raws() {
		return raws;
	}

	/** Anchor point given to the TOP polys added after this call (null to stop). */
	public void anchor(double[] point) {
		anchor = point;
	}

	public static double[] p(double x, double y, double z) {
		return new double[] {x, y, z};
	}

	/** Adds a convex polygon, flipping it if needed so it winds counter-clockwise seen from the normal. */
	public void face(int material, Clip clip, double nx, double ny, double nz, double[]... v) {
		double[] n = normal(v);
		if (n[0] * nx + n[1] * ny + n[2] * nz < 0) {
			double[][] r = new double[v.length][];
			for (int i = 0; i < v.length; i++) {
				r[i] = v[v.length - 1 - i];
			}
			v = r;
		}
		raws.add(new Raw(v, material, clip, clip == Clip.TOP ? anchor : null));
	}

	public void box(double x0, double x1, double y0, double y1, double z0, double z1, int material, Clip clip) {
		face(material, clip, 0, -1, 0, p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1));
		face(material, clip, 0, 1, 0, p(x0, y1, z0), p(x1, y1, z0), p(x1, y1, z1), p(x0, y1, z1));
		face(material, clip, 0, 0, -1, p(x0, y0, z0), p(x1, y0, z0), p(x1, y1, z0), p(x0, y1, z0));
		face(material, clip, 0, 0, 1, p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1));
		face(material, clip, -1, 0, 0, p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0));
		face(material, clip, 1, 0, 0, p(x1, y0, z0), p(x1, y0, z1), p(x1, y1, z1), p(x1, y1, z0));
	}

	private static double[] point(int axis, double a, double u, double v) {
		double[] out = new double[3];
		out[axis] = a;
		out[(axis + 1) % 3] = u;
		out[(axis + 2) % 3] = v;
		return out;
	}

	/**
	 * Extrudes a convex 2D polygon along {@code axis} from {@code a0} to {@code a1}. The polygon's
	 * (u, v) coordinates map to axes {@code axis+1} and {@code axis+2} (cyclic).
	 */
	public void extrude(int axis, double[][] uv, double a0, double a1, int material, Clip clip, boolean cap0, boolean cap1) {
		double cu = 0;
		double cv = 0;
		for (double[] q : uv) {
			cu += q[0];
			cv += q[1];
		}
		cu /= uv.length;
		cv /= uv.length;
		for (int i = 0; i < uv.length; i++) {
			double[] a = uv[i];
			double[] b = uv[(i + 1) % uv.length];
			double[] n = point(axis, 0, (a[0] + b[0]) / 2 - cu, (a[1] + b[1]) / 2 - cv);
			face(material, clip, n[0], n[1], n[2],
				point(axis, a0, a[0], a[1]), point(axis, a0, b[0], b[1]),
				point(axis, a1, b[0], b[1]), point(axis, a1, a[0], a[1]));
		}
		if (cap0) {
			cap(axis, uv, a0, -1, material, clip);
		}
		if (cap1) {
			cap(axis, uv, a1, 1, material, clip);
		}
	}

	/** Flat convex polygon perpendicular to {@code axis} at {@code a}, facing {@code sign}. */
	public void cap(int axis, double[][] uv, double a, int sign, int material, Clip clip) {
		double[][] v = new double[uv.length][];
		for (int i = 0; i < uv.length; i++) {
			v[i] = point(axis, a, uv[i][0], uv[i][1]);
		}
		double[] n = point(axis, sign, 0, 0);
		face(material, clip, n[0], n[1], n[2], v);
	}

	/** Regular polygon whose flats sit at {@code r} (circumradius widened accordingly). */
	public static double[][] regular(double cu, double cv, double r, int segments) {
		double rc = r / Math.cos(Math.PI / segments);
		double[][] out = new double[segments][];
		for (int i = 0; i < segments; i++) {
			double a = Math.PI * 2 * (i + 0.5) / segments;
			out[i] = new double[] {cu + Math.cos(a) * rc, cv + Math.sin(a) * rc};
		}
		return out;
	}

	public static double[][] rect(double u0, double u1, double v0, double v1) {
		return new double[][] {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
	}

	/** Newell normal; robust for any planar polygon. */
	public static double[] normal(double[][] v) {
		double nx = 0;
		double ny = 0;
		double nz = 0;
		for (int i = 0; i < v.length; i++) {
			double[] a = v[i];
			double[] b = v[(i + 1) % v.length];
			nx += (a[1] - b[1]) * (a[2] + b[2]);
			ny += (a[2] - b[2]) * (a[0] + b[0]);
			nz += (a[0] - b[0]) * (a[1] + b[1]);
		}
		return new double[] {nx, ny, nz};
	}

	/** Sutherland–Hodgman clip of a convex polygon to {@code min ≤ v[axis] ≤ max}. */
	public static double[][] clip(double[][] v, int axis, double min, double max) {
		v = clipPlane(v, axis, min, true);
		return clipPlane(v, axis, max, false);
	}

	private static double[][] clipPlane(double[][] v, int axis, double bound, boolean keepAbove) {
		if (v.length < 3) {
			return v;
		}
		List<double[]> out = new ArrayList<>(v.length + 2);
		for (int i = 0; i < v.length; i++) {
			double[] a = v[i];
			double[] b = v[(i + 1) % v.length];
			boolean aIn = inside(a[axis], bound, keepAbove);
			boolean bIn = inside(b[axis], bound, keepAbove);
			if (aIn) {
				out.add(a);
			}
			if (aIn != bIn) {
				double t = (bound - a[axis]) / (b[axis] - a[axis]);
				out.add(new double[] {
					a[0] + (b[0] - a[0]) * t,
					a[1] + (b[1] - a[1]) * t,
					a[2] + (b[2] - a[2]) * t
				});
			}
		}
		return out.toArray(new double[0][]);
	}

	private static boolean inside(double value, double bound, boolean keepAbove) {
		return keepAbove ? value >= bound - CLIP_EPS : value <= bound + CLIP_EPS;
	}

	/** Clips to the 16³ cell; returns null if nothing is left. */
	public static Poly clipToCell(double[][] v, int material, boolean clipY) {
		v = clip(v, 0, 0, 16);
		v = clip(v, 2, 0, 16);
		if (clipY) {
			v = clip(v, 1, 0, 16);
		}
		if (v.length < 3) {
			return null;
		}
		double[] n = normal(v);
		if (n[0] * n[0] + n[1] * n[1] + n[2] * n[2] < 1.0E-8) {
			return null;
		}
		return new Poly(v, material, cullFace(v));
	}

	private static Direction cullFace(double[][] v) {
		for (int axis = 0; axis < 3; axis++) {
			for (double bound : new double[] {0, 16}) {
				boolean all = true;
				for (double[] p : v) {
					if (Math.abs(p[axis] - bound) > CLIP_EPS) {
						all = false;
						break;
					}
				}
				if (all) {
					return Direction.fromAxisAndDirection(
						Direction.Axis.values()[axis],
						bound > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE
					);
				}
			}
		}
		return null;
	}
}
