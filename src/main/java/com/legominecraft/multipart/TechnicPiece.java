package com.legominecraft.multipart;

import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.mesh.Mesh;
import com.legominecraft.mesh.TechnicGeometry;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A studless Technic part placed in the world: part-space geometry rotated so x runs along
 * {@code dir} and y along {@code roll}, then offset to the anchor point inside the anchor cell.
 * Cell-space coordinates are pixels relative to the anchor cell's minimum corner.
 */
public record TechnicPiece(LegoPartDefinition def, Direction dir, Direction.Axis roll, int ox, int oy, int oz) {
	private static final Map<TechnicPiece, Placed> PLACED = new ConcurrentHashMap<>();

	private record Placed(List<double[]> boxes, List<Mesh.Raw> raws, List<int[]> fragments, double minY, double maxY) {
	}

	public static TechnicPiece of(LegoPartDefinition def, Direction dir, Direction.Axis roll, int ox, int oy, int oz) {
		if (roll == dir.getAxis()) {
			roll = defaultRoll(dir);
		}
		return new TechnicPiece(def, dir, roll, ox, oy, oz);
	}

	public static Direction.Axis defaultRoll(Direction dir) {
		return dir.getAxis() == Direction.Axis.Y ? Direction.Axis.X : Direction.Axis.Y;
	}

	public TechnicGeometry geometry() {
		return TechnicGeometry.of(def);
	}

	private double[][] basis() {
		double[] el = {dir.getStepX(), dir.getStepY(), dir.getStepZ()};
		double[] eh = {roll == Direction.Axis.X ? 1 : 0, roll == Direction.Axis.Y ? 1 : 0, roll == Direction.Axis.Z ? 1 : 0};
		double[] ep = {
			el[1] * eh[2] - el[2] * eh[1],
			el[2] * eh[0] - el[0] * eh[2],
			el[0] * eh[1] - el[1] * eh[0]
		};
		return new double[][] {el, eh, ep};
	}

	/** Part space → cell space (px from the anchor cell corner). */
	public double[] toCell(double x, double y, double z) {
		double[][] b = basis();
		return new double[] {
			ox / 3.0 + x * b[0][0] + y * b[1][0] + z * b[2][0],
			oy / 3.0 + x * b[0][1] + y * b[1][1] + z * b[2][1],
			oz / 3.0 + x * b[0][2] + y * b[1][2] + z * b[2][2]
		};
	}

	/** World axis of a part-space axis index (0 = x, 1 = y, 2 = z). */
	public Direction.Axis worldAxis(int partAxis) {
		double[] e = basis()[partAxis];
		if (e[0] != 0) {
			return Direction.Axis.X;
		}
		return e[1] != 0 ? Direction.Axis.Y : Direction.Axis.Z;
	}

	private Placed placed() {
		return PLACED.computeIfAbsent(this, TechnicPiece::computePlaced);
	}

	private Placed computePlaced() {
		TechnicGeometry g = geometry();
		List<double[]> boxes = new ArrayList<>();
		double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (TechnicGeometry.Box box : g.boxes) {
			double[] a = toCell(box.x0(), box.y0(), box.z0());
			double[] c = toCell(box.x1(), box.y1(), box.z1());
			double[] out = new double[6];
			for (int i = 0; i < 3; i++) {
				out[i] = Math.min(a[i], c[i]);
				out[i + 3] = Math.max(a[i], c[i]);
				min[i] = Math.min(min[i], out[i]);
				max[i] = Math.max(max[i], out[i + 3]);
			}
			boxes.add(out);
		}
		List<Mesh.Raw> raws = new ArrayList<>();
		for (Mesh.Raw raw : g.mesh.raws()) {
			double[][] v = new double[raw.v().length][];
			for (int i = 0; i < v.length; i++) {
				double[] q = raw.v()[i];
				v[i] = toCell(q[0], q[1], q[2]);
				for (int k = 0; k < 3; k++) {
					min[k] = Math.min(min[k], v[i][k]);
					max[k] = Math.max(max[k], v[i][k]);
				}
			}
			raws.add(new Mesh.Raw(v, raw.material(), raw.clip()));
		}

		List<int[]> fragments = new ArrayList<>();
		int[] c0 = new int[3];
		int[] c1 = new int[3];
		for (int i = 0; i < 3; i++) {
			c0[i] = (int) Math.floor((min[i] + Mesh.CLIP_EPS) / 16);
			c1[i] = (int) Math.floor((max[i] - Mesh.CLIP_EPS) / 16);
		}
		for (int dx = c0[0]; dx <= c1[0]; dx++) {
			for (int dy = c0[1]; dy <= c1[1]; dy++) {
				for (int dz = c0[2]; dz <= c1[2]; dz++) {
					if (cellHasShape(boxes, dx, dy, dz) || cellHasMesh(raws, dx, dy, dz)) {
						fragments.add(new int[] {dx, dy, dz});
					}
				}
			}
		}
		for (int i = 0; i < fragments.size(); i++) {
			int[] f = fragments.get(i);
			if (f[0] == 0 && f[1] == 0 && f[2] == 0) {
				fragments.add(0, fragments.remove(i));
				break;
			}
		}
		return new Placed(boxes, raws, fragments, min[1], max[1]);
	}

	/** Lowest point in px relative to the anchor cell's bottom. */
	public double minY() {
		return placed().minY();
	}

	/** Highest point in px relative to the anchor cell's bottom. */
	public double maxY() {
		return placed().maxY();
	}

	private static boolean cellHasShape(List<double[]> boxes, int dx, int dy, int dz) {
		for (double[] b : boxes) {
			if (b[0] < dx * 16 + 16 && b[3] > dx * 16 && b[1] < dy * 16 + 16 && b[4] > dy * 16
				&& b[2] < dz * 16 + 16 && b[5] > dz * 16) {
				return true;
			}
		}
		return false;
	}

	private static boolean cellHasMesh(List<Mesh.Raw> raws, int dx, int dy, int dz) {
		for (Mesh.Raw raw : raws) {
			if (clipRaw(raw, dx, dy, dz) != null) {
				return true;
			}
		}
		return false;
	}

	private static Mesh.Poly clipRaw(Mesh.Raw raw, int dx, int dy, int dz) {
		double[][] v = new double[raw.v().length][];
		for (int i = 0; i < v.length; i++) {
			double[] q = raw.v()[i];
			v[i] = new double[] {q[0] - dx * 16, q[1] - dy * 16, q[2] - dz * 16};
		}
		return Mesh.clipToCell(v, raw.material(), true);
	}

	/** Cell offsets (relative to the anchor cell) this part occupies; the anchor cell is first if present. */
	public List<int[]> fragments() {
		return placed().fragments();
	}

	public VoxelShape fragmentShape(int dx, int dy, int dz) {
		VoxelShape shape = Shapes.empty();
		for (double[] b : placed().boxes()) {
			double x0 = Math.max(b[0], dx * 16);
			double y0 = Math.max(b[1], dy * 16);
			double z0 = Math.max(b[2], dz * 16);
			double x1 = Math.min(b[3], dx * 16 + 16);
			double y1 = Math.min(b[4], dy * 16 + 16);
			double z1 = Math.min(b[5], dz * 16 + 16);
			if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
				continue;
			}
			shape = Shapes.or(shape, Shapes.box(
				(x0 - dx * 16) / 16, (y0 - dy * 16) / 16, (z0 - dz * 16) / 16,
				(x1 - dx * 16) / 16, (y1 - dy * 16) / 16, (z1 - dz * 16) / 16
			));
		}
		return shape;
	}

	public List<Mesh.Poly> fragmentPolys(int dx, int dy, int dz) {
		List<Mesh.Poly> out = new ArrayList<>();
		for (Mesh.Raw raw : placed().raws()) {
			Mesh.Poly poly = clipRaw(raw, dx, dy, dz);
			if (poly != null) {
				out.add(poly);
			}
		}
		return out;
	}

	private List<TechnicHole> toWorld(List<TechnicGeometry.PartHole> list, int ax, int ay, int az) {
		List<TechnicHole> out = new ArrayList<>(list.size());
		for (TechnicGeometry.PartHole h : list) {
			double[] c = toCell(h.x(), h.y(), h.z());
			out.add(new TechnicHole(ax * 16 + c[0], ay * 16 + c[1], az * 16 + c[2], worldAxis(h.axis()), h.cross()));
		}
		return out;
	}

	/** Holes in absolute world px, given the anchor cell's block position. */
	public List<TechnicHole> worldHoles(int ax, int ay, int az) {
		return toWorld(geometry().holes, ax, ay, az);
	}

	/** Pin/axle modules in absolute world px. */
	public List<TechnicHole> worldModules(int ax, int ay, int az) {
		return toWorld(geometry().modules, ax, ay, az);
	}
}
