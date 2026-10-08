package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import com.legominecraft.block.LegoColor;
import com.legominecraft.mesh.Mesh;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A whole LEGO build captured as one item: every piece with its position relative to the build's
 * minimum corner. Can be rotated in 90° steps around Y and placed back as a unit.
 */
public final class LegoModel {
	/** Receives one convex polygon of the model, in px relative to the model's (0, 0, 0) block. */
	@FunctionalInterface
	public interface PolySink {
		void accept(LegoColor color, double[][] vertices);
	}

	private sealed interface Entry permits Brick, Technic {
		List<BlockPos> cells();

		Entry moved(int dx, int dy, int dz);

		/** Moves up or down by whole plates (1/3 block); the piece may change cell. */
		Entry movedPlates(int plates);

		/** Moves by whole {@link LegoPiece} units along each axis (Technic parts round to their own grid). */
		Entry translated(int ux, int uy, int uz);

		/** Equals the {@link LegoFragmentPart#pieceKey} of this piece once placed. */
		Object key();

		Entry rotatedClockwise();

		int bottomPlates();

		int topPlates();

		void polys(PolySink sink);
	}

	private static void emitInCell(PolySink sink, LegoColor color, List<Mesh.Poly> polys, BlockPos cell) {
		for (Mesh.Poly poly : polys) {
			double[][] v = new double[poly.v().length][];
			for (int i = 0; i < v.length; i++) {
				double[] q = poly.v()[i];
				v[i] = new double[] {q[0] + cell.getX() * 16, q[1] + cell.getY() * 16, q[2] + cell.getZ() * 16};
			}
			sink.accept(color, v);
		}
	}

	/** @param origin anchor cell of a studded piece (cell offsets in {@code data} are 0) */
	private record Brick(BlockPos origin, LegoPartData data) implements Entry {
		@Override
		public List<BlockPos> cells() {
			List<BlockPos> out = new ArrayList<>();
			for (int[] f : data.piece().fragments()) {
				out.add(origin.offset(f[0], f[1], f[2]));
			}
			return out;
		}

		@Override
		public Entry moved(int dx, int dy, int dz) {
			return new Brick(origin.offset(dx, dy, dz), data);
		}

		@Override
		public Entry movedPlates(int plates) {
			return translated(0, plates * LegoPiece.PLATE_UNITS, 0);
		}

		@Override
		public Entry translated(int ux, int uy, int uz) {
			int ax = LegoPiece.UNITS_X;
			int ay = LegoPiece.UNITS_Y;
			int x = origin.getX() * ax + data.tx() + ux;
			int y = origin.getY() * ay + data.ty() + uy;
			int z = origin.getZ() * ax + data.tz() + uz;
			LegoPartData d = new LegoPartData(data.designId(), data.color(), data.facing(), data.up(),
				Math.floorMod(x, ax), Math.floorMod(y, ay), Math.floorMod(z, ax), 0, 0, 0);
			return new Brick(new BlockPos(Math.floorDiv(x, ax), Math.floorDiv(y, ay), Math.floorDiv(z, ax)), d);
		}

		@Override
		public Object key() {
			return List.of(origin, data);
		}

		@Override
		public int bottomPlates() {
			return origin.getY() * 3 + (int) Math.floor(data.piece().minYPlates() + 1.0E-6);
		}

		@Override
		public int topPlates() {
			return origin.getY() * 3 + (int) Math.ceil(data.piece().maxYPlates() - 1.0E-6);
		}

		@Override
		public void polys(PolySink sink) {
			LegoPiece piece = data.piece();
			for (int[] f : piece.fragments()) {
				emitInCell(sink, data.color(), piece.fragmentPolys(f[0], f[1], f[2]), origin.offset(f[0], f[1], f[2]));
			}
		}

		@Override
		public Entry rotatedClockwise() {
			LegoPiece.Anchored t = data.piece().turnedClockwise(origin.getX(), origin.getY(), origin.getZ());
			return new Brick(new BlockPos(t.ax(), t.ay(), t.az()), LegoPartData.of(t.piece(), data.color()));
		}
	}

	/** @param anchor anchor cell of a Technic part (offsets in {@code data} are 0) */
	private record Technic(BlockPos anchor, TechnicPartData data) implements Entry {
		@Override
		public List<BlockPos> cells() {
			List<BlockPos> out = new ArrayList<>();
			for (int[] f : data.piece().fragments()) {
				out.add(anchor.offset(f[0], f[1], f[2]));
			}
			return out;
		}

		@Override
		public Entry moved(int dx, int dy, int dz) {
			return new Technic(anchor.offset(dx, dy, dz), data);
		}

		@Override
		public Entry movedPlates(int plates) {
			int per = TechnicPartData.THIRDS_PER_CELL;
			int ty = anchor.getY() * per + data.oy() + plates * (per / 3);
			TechnicPartData d = new TechnicPartData(data.designId(), data.color(), data.dir(), data.roll(),
				data.ox(), Math.floorMod(ty, per), data.oz(), 0, 0, 0);
			return new Technic(new BlockPos(anchor.getX(), Math.floorDiv(ty, per), anchor.getZ()), d);
		}

		@Override
		public Entry translated(int ux, int uy, int uz) {
			int per = TechnicPartData.THIRDS_PER_CELL;
			int x = anchor.getX() * per + data.ox() + Math.round((float) ux * per / LegoPiece.UNITS_X);
			int y = anchor.getY() * per + data.oy() + Math.round((float) uy * per / LegoPiece.UNITS_Y);
			int z = anchor.getZ() * per + data.oz() + Math.round((float) uz * per / LegoPiece.UNITS_X);
			TechnicPartData d = new TechnicPartData(data.designId(), data.color(), data.dir(), data.roll(),
				Math.floorMod(x, per), Math.floorMod(y, per), Math.floorMod(z, per), 0, 0, 0);
			return new Technic(new BlockPos(Math.floorDiv(x, per), Math.floorDiv(y, per), Math.floorDiv(z, per)), d);
		}

		@Override
		public Object key() {
			return List.of(anchor, data);
		}

		@Override
		public int bottomPlates() {
			return (int) Math.floor((anchor.getY() * 16 + data.piece().minY()) * 3 / 16 + 1.0E-3);
		}

		@Override
		public int topPlates() {
			return (int) Math.ceil((anchor.getY() * 16 + data.piece().maxY()) * 3 / 16 - 1.0E-3);
		}

		@Override
		public void polys(PolySink sink) {
			TechnicPiece piece = data.piece();
			for (int[] f : piece.fragments()) {
				emitInCell(sink, data.color(), piece.fragmentPolys(f[0], f[1], f[2]), anchor.offset(f[0], f[1], f[2]));
			}
		}

		@Override
		public Entry rotatedClockwise() {
			int per = TechnicPartData.THIRDS_PER_CELL;
			long tx = (long) anchor.getX() * per + data.ox();
			long tz = (long) anchor.getZ() * per + data.oz();
			long nx = -tz;
			long nz = tx;
			Direction dir = data.dir().getAxis().isHorizontal() ? data.dir().getClockWise() : data.dir();
			Direction.Axis roll = switch (data.roll()) {
				case X -> Direction.Axis.Z;
				case Z -> Direction.Axis.X;
				case Y -> Direction.Axis.Y;
			};
			if (roll == dir.getAxis()) {
				roll = TechnicPiece.defaultRoll(dir);
			}
			BlockPos a = new BlockPos((int) Math.floorDiv(nx, per), anchor.getY(), (int) Math.floorDiv(nz, per));
			TechnicPartData d = new TechnicPartData(data.designId(), data.color(), dir, roll,
				(int) Math.floorMod(nx, per), data.oy(), (int) Math.floorMod(nz, per), 0, 0, 0);
			return new Technic(a, d);
		}
	}

	private final List<Entry> entries;
	/** Player facing (2D data value) when the model was captured. */
	private final int facing;
	private final LegoModel[] rotations = new LegoModel[4];
	private int[] dimensions;
	private Map<Long, int[]> columns;
	private List<Connector> connectors;
	private int bottomPlates = Integer.MIN_VALUE;
	private int heightPlates = Integer.MIN_VALUE;

	private LegoModel(List<Entry> entries, int facing) {
		this.entries = entries;
		this.facing = facing;
	}

	public int size() {
		return entries.size();
	}

	public int facing() {
		return facing;
	}

	public static LegoModel capture(Collection<LegoFragmentPart> pieces, Direction playerFacing) {
		return new LegoModel(entriesOf(pieces), playerFacing.get2DDataValue()).normalized();
	}

	/** The pieces where they stand in the world (world cell coordinates, not normalised). */
	public static LegoModel captureInPlace(Collection<? extends LegoFragmentPart> pieces) {
		return new LegoModel(entriesOf(pieces), 0);
	}

	private static List<Entry> entriesOf(Collection<? extends LegoFragmentPart> pieces) {
		List<Entry> entries = new ArrayList<>();
		for (LegoFragmentPart part : pieces) {
			if (part instanceof LegoStructurePart s) {
				entries.add(new Brick(s.originPos(), s.getData().base()));
			} else if (part instanceof TechnicPart t) {
				TechnicPartData d = t.getData();
				entries.add(new Technic(t.anchorCell(), new TechnicPartData(
					d.designId(), d.color(), d.dir(), d.roll(), d.ox(), d.oy(), d.oz(), 0, 0, 0)));
			}
		}
		return entries;
	}

	/** Moves by whole studs (1 block) horizontally and whole plates vertically. */
	public LegoModel shifted(int studsX, int plates, int studsZ) {
		return translated(studsX * LegoPiece.UNITS_X, plates * LegoPiece.PLATE_UNITS, studsZ * LegoPiece.UNITS_X);
	}

	/** Moves by whole {@link LegoPiece} units along each axis. */
	public LegoModel translated(int ux, int uy, int uz) {
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry e : entries) {
			out.add(e.translated(ux, uy, uz));
		}
		return new LegoModel(out, facing);
	}

	/** Whether {@code part} is a fragment of one of this (in-world) model's pieces. */
	public boolean contains(LegoFragmentPart part) {
		Object key = part.pieceKey();
		for (Entry e : entries) {
			if (e.key().equals(key)) {
				return true;
			}
		}
		return false;
	}

	/** This in-world model without the pieces that are no longer in the world. */
	public LegoModel present(Level level) {
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry e : entries) {
			if (!partsOf(level, e).isEmpty()) {
				out.add(e);
			}
		}
		return out.size() == entries.size() ? this : new LegoModel(out, facing);
	}

	/** Takes this in-world model's pieces out of the world (no drops). */
	public void remove(Level level) {
		for (Entry e : entries) {
			partsOf(level, e).forEach(LegoFragmentPart::removeSelf);
		}
	}

	private static List<LegoFragmentPart> partsOf(Level level, Entry entry) {
		Object key = entry.key();
		List<LegoFragmentPart> out = new ArrayList<>();
		for (BlockPos pos : entry.cells()) {
			MultipartContainer container = MultipartUtil.get(level, pos);
			if (container == null) {
				continue;
			}
			for (LegoFragmentPart part : container.getParts(LegoFragmentPart.class)) {
				if (part.pieceKey().equals(key)) {
					out.add(part);
				}
			}
		}
		return out;
	}

	private int[] min() {
		int[] m = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
		for (Entry e : entries) {
			for (BlockPos c : e.cells()) {
				m[0] = Math.min(m[0], c.getX());
				m[1] = Math.min(m[1], c.getY());
				m[2] = Math.min(m[2], c.getZ());
			}
		}
		return m;
	}

	/** Size in blocks (x, y, z); the model's minimum corner is always (0, 0, 0). */
	public int[] dimensions() {
		if (dimensions == null) {
			int[] m = {0, 0, 0};
			for (Entry e : entries) {
				for (BlockPos c : e.cells()) {
					m[0] = Math.max(m[0], c.getX() + 1);
					m[1] = Math.max(m[1], c.getY() + 1);
					m[2] = Math.max(m[2], c.getZ() + 1);
				}
			}
			dimensions = m;
		}
		return dimensions.clone();
	}

	private LegoModel normalized() {
		if (entries.isEmpty()) {
			return this;
		}
		int[] m = min();
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry e : entries) {
			out.add(e.moved(-m[0], -m[1], -m[2]));
		}
		return new LegoModel(out, facing);
	}

	/** Lowest piece bottom, in plates above the model's (0, 0, 0) block (0..2 after normalisation). */
	public int bottomPlates() {
		if (bottomPlates == Integer.MIN_VALUE) {
			int m = Integer.MAX_VALUE;
			for (Entry e : entries) {
				m = Math.min(m, e.bottomPlates());
			}
			bottomPlates = entries.isEmpty() ? 0 : m;
		}
		return bottomPlates;
	}

	/** Height from the lowest bottom to the highest top, in plates. */
	public int heightPlates() {
		if (heightPlates == Integer.MIN_VALUE) {
			int m = Integer.MIN_VALUE;
			for (Entry e : entries) {
				m = Math.max(m, e.topPlates());
			}
			heightPlates = entries.isEmpty() ? 0 : m - bottomPlates();
		}
		return heightPlates;
	}

	/**
	 * {lowest bottom, highest top} in plates of the pieces standing in block column (x, z), or null if
	 * the column is empty: what plugs onto (or under) the build when that column is placed on it.
	 */
	public int[] column(int x, int z) {
		if (columns == null) {
			Map<Long, int[]> map = new HashMap<>();
			for (Entry e : entries) {
				int bottom = e.bottomPlates();
				int top = e.topPlates();
				for (BlockPos c : e.cells()) {
					int[] range = map.computeIfAbsent(columnKey(c.getX(), c.getZ()), k -> new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE});
					range[0] = Math.min(range[0], bottom);
					range[1] = Math.max(range[1], top);
				}
			}
			columns = map;
		}
		return columns.get(columnKey(x, z));
	}

	private static long columnKey(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	/** A stud ({@code stud}) or receptor of one of the model's studded pieces, in px relative to block (0, 0, 0). */
	public record Connector(LegoPiece.Stud at, boolean stud) {
	}

	public List<Connector> connectors() {
		if (connectors == null) {
			List<Connector> out = new ArrayList<>();
			for (Entry e : entries) {
				if (e instanceof Brick b) {
					LegoPiece piece = b.data().piece();
					BlockPos o = b.origin();
					for (LegoPiece.Stud s : piece.studs(o.getX(), o.getY(), o.getZ())) {
						out.add(new Connector(s, true));
					}
					for (LegoPiece.Stud s : piece.receptors(o.getX(), o.getY(), o.getZ())) {
						out.add(new Connector(s, false));
					}
				}
			}
			connectors = List.copyOf(out);
		}
		return connectors;
	}

	/** Moves by whole blocks horizontally and whole plates vertically. */
	public LegoModel moved(int dx, int plates, int dz) {
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry e : entries) {
			out.add(e.moved(dx, 0, dz).movedPlates(plates));
		}
		return new LegoModel(out, facing);
	}

	public void polys(PolySink sink) {
		for (Entry e : entries) {
			e.polys(sink);
		}
	}

	public LegoModel rotatedClockwise(int steps) {
		steps = Math.floorMod(steps, 4);
		if (steps == 0) {
			return this;
		}
		LegoModel cached = rotations[steps];
		if (cached != null) {
			return cached;
		}
		LegoModel model = this;
		for (int i = 0; i < steps; i++) {
			List<Entry> out = new ArrayList<>(model.entries.size());
			for (Entry e : model.entries) {
				out.add(e.rotatedClockwise());
			}
			model = new LegoModel(out, facing).normalized();
		}
		rotations[steps] = model;
		return model;
	}

	/**
	 * This in-world model turned a quarter around the line through {@code pivot} (absolute px) along
	 * {@code axis}. Null when it holds a Technic part or a piece would leave the unit lattice.
	 */
	public LegoModel rotatedAbout(Direction.Axis axis, boolean positive, double[] pivot) {
		return transformed(quarterTurn(axis, positive), pivot);
	}

	private static UnaryOperator<double[]> quarterTurn(Direction.Axis axis, boolean positive) {
		return switch (axis) {
			case X -> positive ? d -> new double[] {d[0], -d[2], d[1]} : d -> new double[] {d[0], d[2], -d[1]};
			case Y -> positive ? d -> new double[] {-d[2], d[1], d[0]} : d -> new double[] {d[2], d[1], -d[0]};
			case Z -> positive ? d -> new double[] {-d[1], d[0], d[2]} : d -> new double[] {d[1], -d[0], d[2]};
		};
	}

	/**
	 * This model stood on its side (or turned over) so that its up points to {@code up}. A sideways
	 * model turns about the horizontal axis across {@code up}, its side away from {@code up} rising, as
	 * a picture lying on the floor is stood up to face the viewer; for {@link Direction#DOWN} it turns
	 * over about the axis across {@code forward}. Null when it holds a Technic part.
	 */
	public LegoModel tipped(Direction up, Direction forward) {
		if (up == Direction.UP) {
			return this;
		}
		UnaryOperator<double[]> rot;
		if (up == Direction.DOWN) {
			UnaryOperator<double[]> quarter = quarterTurn(forward.getClockWise().getAxis(), true);
			rot = d -> quarter.apply(quarter.apply(d));
		} else {
			Direction.Axis axis = up.getClockWise().getAxis();
			UnaryOperator<double[]> pos = quarterTurn(axis, true);
			double[] turnedUp = pos.apply(new double[] {0, 1, 0});
			boolean positive = Math.round(turnedUp[0]) == up.getStepX() && Math.round(turnedUp[2]) == up.getStepZ();
			rot = positive ? pos : quarterTurn(axis, false);
		}
		LegoModel turned = transformed(rot, new double[3]);
		return turned == null ? null : turned.normalized();
	}

	private LegoModel transformed(UnaryOperator<double[]> rot, double[] pivot) {
		List<Entry> out = new ArrayList<>(entries.size());
		for (Entry e : entries) {
			if (!(e instanceof Brick b)) {
				return null;
			}
			LegoPiece.Anchored t = b.data().piece().transformed(b.origin().getX(), b.origin().getY(), b.origin().getZ(), rot, pivot);
			if (t == null) {
				return null;
			}
			out.add(new Brick(new BlockPos(t.ax(), t.ay(), t.az()), LegoPartData.of(t.piece(), b.data().color())));
		}
		return new LegoModel(out, facing);
	}

	/** Whether every piece fits where it is, judged against the world as it is now (nothing is placed). */
	public boolean canPlace(Level level) {
		for (Entry e : entries) {
			if (offers(level, e) == null) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Places every piece at its current position. All-or-nothing: if any piece is blocked, the pieces
	 * already placed are removed again.
	 */
	public boolean place(Level level) {
		List<List<LegoFragmentPart>> placed = new ArrayList<>();
		for (Entry e : entries) {
			List<MultipartContainer.PartOffer> offers = offers(level, e);
			if (offers == null) {
				for (List<LegoFragmentPart> parts : placed) {
					parts.forEach(LegoFragmentPart::removeSelf);
				}
				return false;
			}
			List<LegoFragmentPart> parts = new ArrayList<>(offers.size());
			for (MultipartContainer.PartOffer offer : offers) {
				offer.apply();
				parts.add((LegoFragmentPart) offer.getHolder().getPart());
			}
			LegoFragmentPart main = parts.getFirst();
			for (int i = 1; i < parts.size(); i++) {
				main.linkTo(parts.get(i));
				parts.get(i).linkTo(main);
			}
			placed.add(parts);
		}
		return true;
	}

	private static List<MultipartContainer.PartOffer> offers(Level level, Entry entry) {
		List<MultipartContainer.PartOffer> offers = new ArrayList<>();
		if (entry instanceof Brick b) {
			for (int[] f : b.data().piece().fragments()) {
				LegoPartData d = b.data().withCell(f[0], f[1], f[2]);
				if (!offer(level, b.origin().offset(f[0], f[1], f[2]),
					holder -> new LegoStructurePart(LegoParts.STRUCTURE, holder, d), offers)) {
					return null;
				}
			}
		} else if (entry instanceof Technic t) {
			TechnicPartData base = t.data();
			for (int[] f : base.piece().fragments()) {
				TechnicPartData d = new TechnicPartData(base.designId(), base.color(), base.dir(), base.roll(),
					base.ox(), base.oy(), base.oz(), f[0], f[1], f[2]);
				if (!offer(level, t.anchor().offset(f[0], f[1], f[2]),
					holder -> new TechnicPart(LegoParts.TECHNIC, holder, d), offers)) {
					return null;
				}
			}
		}
		return offers.isEmpty() ? null : offers;
	}

	private static boolean offer(
		Level level,
		BlockPos pos,
		MultipartContainer.MultipartCreator creator,
		List<MultipartContainer.PartOffer> out
	) {
		if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return false;
		}
		MultipartContainer.PartOffer offer = LegoParts.offer(level, pos, creator);
		if (offer == null) {
			return false;
		}
		out.add(offer);
		return true;
	}

	// ---- NBT ----

	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();
		tag.putByte("facing", (byte) facing);
		ListTag list = new ListTag();
		for (Entry e : entries) {
			CompoundTag t = new CompoundTag();
			BlockPos p;
			if (e instanceof Brick b) {
				t.putString("type", "brick");
				b.data().write(t);
				p = b.origin();
			} else {
				Technic tech = (Technic) e;
				t.putString("type", "technic");
				tech.data().write(t);
				p = tech.anchor();
			}
			t.putInt("px", p.getX());
			t.putInt("py", p.getY());
			t.putInt("pz", p.getZ());
			list.add(t);
		}
		tag.put("pieces", list);
		return tag;
	}

	public static LegoModel fromTag(CompoundTag tag) {
		List<Entry> entries = new ArrayList<>();
		for (Tag element : tag.getList("pieces", Tag.TAG_COMPOUND)) {
			CompoundTag t = (CompoundTag) element;
			BlockPos p = new BlockPos(t.getInt("px"), t.getInt("py"), t.getInt("pz"));
			if ("technic".equals(t.getString("type"))) {
				entries.add(new Technic(p, TechnicPartData.fromTag(t)));
			} else {
				entries.add(new Brick(p, LegoPartData.fromTag(t)));
			}
		}
		return new LegoModel(entries, tag.getByte("facing"));
	}
}
