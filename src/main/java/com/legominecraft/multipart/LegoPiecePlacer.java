package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places catalog pieces on the absolute plate grid (3 plates per block).
 * <ul>
 *   <li>On top of a LEGO piece: the clicked column must carry a stud; the new piece sits on the top.</li>
 *   <li>Under a LEGO piece: the new piece's studs plug into the bottom.</li>
 *   <li>Against a LEGO side: the new piece starts at the same bottom height, next to it.</li>
 *   <li>On any other block: snaps to that block's faces.</li>
 * </ul>
 * Multi-stud pieces become one fragment per covered block; all fragments must fit or nothing is placed.
 */
public final class LegoPiecePlacer {
	private static final double EPS = 1.0E-3;

	private LegoPiecePlacer() {
	}

	/** Which point of the new piece must land on the target: a receptor, a stud, or a footprint column centre. */
	private enum Mode { RECEPTOR, STUD, SPOT }

	/**
	 * @param x    target point in absolute world px
	 * @param up   direction the new piece's studs point to
	 * @param face clicked face; when it is not along {@code up} the piece grows away from it
	 */
	private record Target(double x, double y, double z, Direction up, Mode mode, Direction face) {
	}

	public static InteractionResult tryPlace(UseOnContext ctx, LegoPartDefinition def, LegoColor color) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();

		InteractionResult clipped = LegoClipPlacer.tryPlace(ctx, def, color);
		if (clipped != null) {
			return clipped;
		}
		if (player != null && hasTechnicHoles(def)) {
			InteractionResult onPin = tryPlaceOnPin(ctx, player, def, color);
			if (onPin != null) {
				return onPin;
			}
		}

		Target target = resolveTarget(level, ctx, def);
		if (target == null) {
			return fail(level, player, "no_stud");
		}
		Direction look = ctx.getHorizontalDirection();
		Direction facing = facingFor(def, target.up(), target.up().getAxis().isVertical() ? look.getOpposite() : Direction.DOWN);
		if (player != null && player.isSecondaryUseActive()) {
			facing = facing.getClockWise();
		}
		for (LegoPiece.Anchored c : candidates(new LegoPiece(def, facing, target.up(), 0, 0, 0), target, look)) {
			List<MultipartContainer.PartOffer> offers = offers(level, c.piece(), color, c.ax(), c.ay(), c.az());
			if (offers != null) {
				apply(ctx, c.piece(), offers, c.ax(), c.ay(), c.az());
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
		}
		return fail(level, player, "blocked");
	}

	/** How far (px, along its up axis) the piece reaches below its bottom plane: pegs, hanging brackets. */
	private static double reachBelow(LegoPiece oriented) {
		List<double[]> spots = oriented.bottomSpots(0, 0, 0);
		if (spots.isEmpty()) {
			return 0;
		}
		int k = oriented.up().getAxis().ordinal();
		double[] b = oriented.boundsPx();
		double bottom = spots.getFirst()[k];
		double below = oriented.up().getAxisDirection() == Direction.AxisDirection.POSITIVE ? bottom - b[k] : b[k + 3] - bottom;
		return Math.max(0, below);
	}

	/** {@code px} along axis {@code k}, rounded up to whole placement units. */
	private static double ceilUnits(double px, int k) {
		return Math.ceil(px / LegoPiece.unitPx(k) - 1.0E-3) * LegoPiece.unitPx(k);
	}

	/** Bottom column centres of the piece raised by {@code lift} px along its up direction. */
	private static List<double[]> liftedSpots(LegoPiece oriented, double lift) {
		Direction up = oriented.up();
		List<double[]> out = new ArrayList<>();
		for (double[] p : oriented.bottomSpots(0, 0, 0)) {
			out.add(new double[] {p[0] - up.getStepX() * lift, p[1] - up.getStepY() * lift, p[2] - up.getStepZ() * lift});
		}
		return out;
	}

	/** The facing whose local −z points to {@code want} once the piece stands on {@code up}. */
	private static Direction facingFor(LegoPartDefinition def, Direction up, Direction want) {
		for (Direction f : Direction.Plane.HORIZONTAL) {
			if (new LegoPiece(def, f, up, 0, 0, 0).toWorld(Direction.NORTH) == want) {
				return f;
			}
		}
		return Direction.NORTH;
	}

	/**
	 * Every position of {@code oriented} that puts one of its connectors (per the target's mode) on the
	 * target point, best first: the target at a corner of the piece, the piece growing away from the
	 * player and to the right, then the other corners, then everything else by distance from the best
	 * corner. Along a clicked side face's normal the piece always grows away from the face.
	 */
	private static List<LegoPiece.Anchored> candidates(LegoPiece oriented, Target target, Direction look) {
		List<double[]> points = new ArrayList<>();
		List<double[]> fallback = List.of();
		int upK = target.up().getAxis().ordinal();
		switch (target.mode()) {
			case RECEPTOR -> {
				oriented.receptors(0, 0, 0).forEach(s -> points.add(new double[] {s.x(), s.y(), s.z()}));
				if (points.isEmpty()) {
					// No anti-studs (bars, candlesticks): rest on the stud tops, a peg going down into the stud.
					double half = Math.abs(oriented.toWorld(0, LegoPiece.PLATE_PX / 2, 0)[upK] - oriented.toWorld(0, 0, 0)[upK]);
					points.addAll(liftedSpots(oriented, ceilUnits(Math.max(half, reachBelow(oriented)), upK)));
				}
			}
			case STUD -> oriented.studs(0, 0, 0).stream().filter(s -> s.dir() == target.up())
				.forEach(s -> points.add(new double[] {s.x(), s.y(), s.z()}));
			case SPOT -> {
				points.addAll(oriented.bottomSpots(0, 0, 0));
				double below = reachBelow(oriented);
				if (below > 1.0E-6) {
					fallback = liftedSpots(oriented, ceilUnits(below, upK));
				}
			}
		}
		int primaryCount = points.size();
		points.addAll(fallback);

		Direction.Axis upAxis = target.up().getAxis();
		Direction primary;
		Direction secondary;
		if (upAxis.isVertical()) {
			primary = look;
			secondary = look.getClockWise();
		} else if (look.getAxis() != upAxis) {
			primary = look;
			secondary = Direction.UP;
		} else {
			primary = Direction.UP;
			secondary = look.getClockWise();
		}
		Direction face = target.face();
		boolean pinned = face.getAxis() != upAxis;
		Direction[] forward = growDirections(face, primary);
		Direction[] side = growDirections(face, secondary);

		record Scored(LegoPiece.Anchored anchored, double score) {
		}
		List<Scored> scored = new ArrayList<>();
		double[] t = {target.x(), target.y(), target.z()};
		for (int pi = 0; pi < points.size(); pi++) {
			double[] p = points.get(pi);
			int[] units = new int[3];
			boolean onGrid = true;
			for (int k = 0; k < 3; k++) {
				double v = (t[k] - p[k]) / LegoPiece.unitPx(k);
				units[k] = (int) Math.round(v);
				onGrid &= Math.abs(v - units[k]) < 0.02;
			}
			if (!onGrid) {
				continue;
			}
			int nx = LegoPiece.UNITS_X;
			int ny = LegoPiece.UNITS_Y;
			LegoPiece piece = new LegoPiece(oriented.def(), oriented.facing(), oriented.up(),
				Math.floorMod(units[0], nx), Math.floorMod(units[1], ny), Math.floorMod(units[2], nx));
			int ax = Math.floorDiv(units[0], nx);
			int ay = Math.floorDiv(units[1], ny);
			int az = Math.floorDiv(units[2], nx);
			double[] b = piece.boundsPx();
			double[] origin = {ax * LegoPiece.PX, ay * LegoPiece.PX, az * LegoPiece.PX};
			if (pinned && !grows(b, origin, t, face)) {
				continue;
			}
			double score = -1;
			int i = 0;
			for (Direction f : forward) {
				for (Direction s : side) {
					if (score < 0 && grows(b, origin, t, f) && grows(b, origin, t, s)) {
						score = i;
					}
					i++;
				}
			}
			if (score < 0) {
				score = 10 + back(b, origin, t, forward[0]) + back(b, origin, t, side[0]);
			}
			if (pi >= primaryCount) {
				score += 1000;
			}
			scored.add(new Scored(new LegoPiece.Anchored(piece, ax, ay, az), score));
		}
		scored.sort(Comparator.comparingDouble(Scored::score));
		List<LegoPiece.Anchored> out = new ArrayList<>(scored.size());
		for (Scored s : scored) {
			out.add(s.anchored());
		}
		return out;
	}

	/** Whole columns the piece reaches behind the target, against direction {@code d}. */
	private static int back(double[] bounds, double[] origin, double[] t, Direction d) {
		int k = d.getAxis().ordinal();
		double behind = d.getAxisDirection() == Direction.AxisDirection.POSITIVE
			? t[k] - (origin[k] + bounds[k])
			: origin[k] + bounds[k + 3] - t[k];
		return Math.max(0, (int) Math.round((behind - LegoPiece.PX / 2.0) / LegoPiece.PX));
	}

	/** Whether the piece extends from the target's column only in direction {@code d} along its axis. */
	private static boolean grows(double[] bounds, double[] origin, double[] t, Direction d) {
		return back(bounds, origin, t, d) == 0;
	}

	/**
	 * Footprint origins (x, z) that put one corner of a {@code sizeX × sizeZ} footprint on the given
	 * column, best first: extending away from the player and to the player's right, then mirrored to the
	 * left, then back toward the player; after the corners, every other position still covering the
	 * column, nearest to the preferred corner first. Along a clicked side face's normal the footprint
	 * always grows away from the face.
	 */
	public static List<int[]> cornerOrigins(int colX, int colZ, int sizeX, int sizeZ, Direction face, Direction look) {
		Direction right = look.getClockWise();
		Direction[] forward = growDirections(face, look);
		Direction[] side = growDirections(face, right);
		List<int[]> out = new ArrayList<>(4);
		for (Direction f : forward) {
			for (Direction s : side) {
				int x = 0;
				int z = 0;
				for (Direction d : new Direction[] {f, s}) {
					if (d.getAxis() == Direction.Axis.X) {
						x = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? colX : colX - sizeX + 1;
					} else {
						z = d.getAxisDirection() == Direction.AxisDirection.POSITIVE ? colZ : colZ - sizeZ + 1;
					}
				}
				addOrigin(out, x, z);
			}
		}
		int[] best = out.getFirst();
		List<int[]> rest = new ArrayList<>();
		for (int x = colX - sizeX + 1; x <= colX; x++) {
			for (int z = colZ - sizeZ + 1; z <= colZ; z++) {
				boolean pinnedX = face.getAxis() == Direction.Axis.X && x != best[0];
				boolean pinnedZ = face.getAxis() == Direction.Axis.Z && z != best[1];
				if (!pinnedX && !pinnedZ) {
					rest.add(new int[] {x, z});
				}
			}
		}
		rest.sort(Comparator.comparingInt(o -> Math.abs(o[0] - best[0]) + Math.abs(o[1] - best[1])));
		for (int[] o : rest) {
			addOrigin(out, o[0], o[1]);
		}
		return out;
	}

	private static void addOrigin(List<int[]> out, int x, int z) {
		for (int[] e : out) {
			if (e[0] == x && e[1] == z) {
				return;
			}
		}
		out.add(new int[] {x, z});
	}

	private static Direction[] growDirections(Direction face, Direction preferred) {
		if (face.getAxis() == preferred.getAxis()) {
			return new Direction[] {face};
		}
		return new Direction[] {preferred, preferred.getOpposite()};
	}

	private static boolean hasTechnicHoles(LegoPartDefinition def) {
		return def.kind() == LegoPartType.TECHNIC_BRICK || def.kind() == LegoPartType.TECHNIC_PLATE;
	}

	/**
	 * Slides a Technic brick/plate onto the free pin or axle segment the player aims at: tries every
	 * facing and every hole so that a hole lands exactly on the segment (bottom kept on the plate grid),
	 * preferring placements that extend along the view direction.
	 * Returns null when no segment is targeted.
	 */
	private static InteractionResult tryPlaceOnPin(UseOnContext ctx, Player player, LegoPartDefinition def, LegoColor color) {
		Level level = ctx.getLevel();
		BlockHitResult hit = new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside());
		TechnicPlacer.ModuleTarget target = TechnicPlacer.findFreeModule(level, player, hit);
		if (target == null) {
			return null;
		}
		TechnicHole spot = target.spot();
		Vec3 look = player.getViewVector(1.0F);

		record Candidate(LegoPiece piece, int x, int y, int z, double score) {
		}
		List<Candidate> candidates = new ArrayList<>();
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			LegoPiece flat = LegoPiece.upright(def, facing, 0);
			List<TechnicHole> holes = flat.worldHoles(0, 0, 0);
			for (TechnicHole h : holes) {
				if (h.axis() != spot.axis()) {
					continue;
				}
				double ox = (spot.x() - h.x()) / 16;
				double oz = (spot.z() - h.z()) / 16;
				double plates = (spot.y() - h.y()) * 3 / 16;
				if (Math.abs(ox - Math.rint(ox)) > 0.02 || Math.abs(oz - Math.rint(oz)) > 0.02
					|| Math.abs(plates - Math.rint(plates)) > 0.02) {
					continue;
				}
				int bottomAbs = (int) Math.rint(plates);
				LegoPiece piece = LegoPiece.upright(def, facing, Math.floorMod(bottomAbs, 3));
				double cx = flat.sizeX() * 8.0 - h.x();
				double cz = flat.sizeZ() * 8.0 - h.z();
				double score = cx * look.x + cz * look.z;
				candidates.add(new Candidate(
					piece, (int) Math.rint(ox), Math.floorDiv(bottomAbs, 3), (int) Math.rint(oz), score
				));
			}
		}
		if (candidates.isEmpty()) {
			return fail(level, player, "no_fit");
		}
		candidates.sort((a, b) -> Double.compare(b.score(), a.score()));
		for (Candidate c : candidates) {
			List<MultipartContainer.PartOffer> offers = offers(level, c.piece(), color, c.x(), c.y(), c.z());
			if (offers != null) {
				apply(ctx, c.piece(), offers, c.x(), c.y(), c.z());
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
		}
		return fail(level, player, "no_fit");
	}

	static List<MultipartContainer.PartOffer> offers(
		Level level, LegoPiece piece, LegoColor color, int originX, int originY, int originZ
	) {
		LegoPartData base = LegoPartData.of(piece, color);
		List<MultipartContainer.PartOffer> offers = new ArrayList<>();
		for (int[] f : piece.fragments()) {
			BlockPos pos = new BlockPos(originX + f[0], originY + f[1], originZ + f[2]);
			if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
				return null;
			}
			LegoPartData data = base.withCell(f[0], f[1], f[2]);
			MultipartContainer.PartOffer offer = LegoParts.offer(
				level, pos, holder -> new LegoStructurePart(LegoParts.STRUCTURE, holder, data)
			);
			if (offer == null) {
				return null;
			}
			offers.add(offer);
		}
		return offers.isEmpty() ? null : offers;
	}

	static void apply(
		UseOnContext ctx, LegoPiece piece, List<MultipartContainer.PartOffer> offers, int originX, int originY, int originZ
	) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		LegoPartDefinition def = piece.def();
		if (!level.isClientSide) {
			List<LegoStructurePart> parts = new ArrayList<>(offers.size());
			for (MultipartContainer.PartOffer offer : offers) {
				offer.apply();
				parts.add((LegoStructurePart) offer.getHolder().getPart());
			}
			LegoStructurePart main = parts.getFirst();
			for (int i = 1; i < parts.size(); i++) {
				main.linkTo(parts.get(i));
				parts.get(i).linkTo(main);
			}
			LegoMover.remember(player, level, main);
			BlockPos originPos = new BlockPos(originX, originY, originZ);
			SoundType sound = def.kind().getSoundType();
			level.playSound(
				null, originPos, sound.getPlaceSound(), SoundSource.BLOCKS,
				(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F
			);
			level.gameEvent(player, GameEvent.BLOCK_PLACE, originPos);
			ctx.getItemInHand().consume(1, player);
		}
	}

	/**
	 * On a LEGO piece: a face carrying studs takes the new piece onto the stud nearest the hit (studs
	 * pointing the same way); the bottom takes the new piece's studs into the receptor nearest the hit;
	 * any other face puts the new piece next to it, on the same bottom plane and connector lattice.
	 */
	private static Target resolveTarget(Level level, UseOnContext ctx, LegoPartDefinition def) {
		BlockPos clicked = ctx.getClickedPos();
		Direction face = ctx.getClickedFace();
		Vec3 hit = ctx.getClickLocation();
		double[] h = {hit.x * LegoPiece.PX, hit.y * LegoPiece.PX, hit.z * LegoPiece.PX};

		LegoStructurePart part = findHitPart(level, clicked, hit, face);
		if (part != null) {
			Direction up = part.getData().up();
			List<LegoPiece.Stud> studs = part.studs();
			if (face == up || studs.stream().anyMatch(s -> s.dir() == face)) {
				LegoPiece.Stud s = nearest(studs, face, h);
				return s == null ? null : new Target(s.x(), s.y(), s.z(), face, Mode.RECEPTOR, face);
			}
			if (face == up.getOpposite()) {
				if (!def.kind().hasStuds() && def.studs().isEmpty()) {
					return null;
				}
				LegoPiece.Stud r = nearest(part.receptors(), up, h);
				return r == null ? null : new Target(r.x(), r.y(), r.z(), up, Mode.STUD, face);
			}
			BlockPos o = part.originPos();
			double[] ref = part.getPiece().toWorld(8, 0, 8);
			double[] lattice = {o.getX() * LegoPiece.PX + ref[0], o.getY() * LegoPiece.PX + ref[1], o.getZ() * LegoPiece.PX + ref[2]};
			double[] q = {h[0] + face.getStepX() * 8, h[1] + face.getStepY() * 8, h[2] + face.getStepZ() * 8};
			for (int k = 0; k < 3; k++) {
				double pitch = k == 1 ? LegoPiece.PX / LegoPiece.ACROSS_PER_UP : LegoPiece.PX;
				q[k] = k == up.getAxis().ordinal()
					? lattice[k]
					: lattice[k] + pitch * Math.round((q[k] - lattice[k]) / pitch);
			}
			return new Target(q[0], q[1], q[2], up, Mode.SPOT, face);
		}

		VanillaBlockPart block = VanillaBlockPart.findHit(level, clicked, hit, face);
		if (block != null) {
			double[] m = block.minPx();
			double y = switch (face) {
				case UP -> m[1] + LegoPiece.PX;
				case DOWN -> m[1] - def.heightPlates() * LegoPiece.PLATE_PX;
				default -> m[1];
			};
			return new Target(m[0] + 8 + face.getStepX() * LegoPiece.PX, y, m[2] + 8 + face.getStepZ() * LegoPiece.PX,
				Direction.UP, Mode.SPOT, face);
		}

		int bottomAbs;
		BlockPos col = clicked;
		Direction grow = face;
		if (level.getBlockState(clicked).canBeReplaced() && MultipartUtil.get(level, clicked) == null) {
			bottomAbs = clicked.getY() * 3;
			grow = Direction.UP;
		} else {
			switch (face) {
				case UP -> bottomAbs = (clicked.getY() + 1) * 3;
				case DOWN -> bottomAbs = clicked.getY() * 3 - def.heightPlates();
				default -> {
					col = clicked.relative(face);
					bottomAbs = clicked.getY() * 3;
				}
			}
		}
		return new Target(col.getX() * LegoPiece.PX + 8, bottomAbs * LegoPiece.PLATE_PX, col.getZ() * LegoPiece.PX + 8,
			Direction.UP, Mode.SPOT, grow);
	}

	/** The connector pointing {@code dir} on the hit's plane whose column contains the hit. */
	static LegoPiece.Stud nearest(List<LegoPiece.Stud> connectors, Direction dir, double[] h) {
		int n = dir.getAxis().ordinal();
		LegoPiece.Stud best = null;
		double bestDist = Double.MAX_VALUE;
		for (LegoPiece.Stud s : connectors) {
			double[] p = {s.x(), s.y(), s.z()};
			if (s.dir() != dir || Math.abs(p[n] - h[n]) > 0.6) {
				continue;
			}
			double d = 0;
			for (int k = 0; k < 3; k++) {
				if (k != n) {
					d = Math.max(d, Math.abs(p[k] - h[k]));
				}
			}
			if (d <= LegoPiece.PX / 2.0 + 0.5 && d < bestDist) {
				best = s;
				bestDist = d;
			}
		}
		return best;
	}

	/** Finds the fragment whose box face was clicked, falling back to any box containing the hit. */
	public static LegoStructurePart findHitPart(Level level, BlockPos pos, Vec3 hit, Direction face) {
		MultipartContainer container = MultipartUtil.get(level, pos);
		if (container == null) {
			return null;
		}
		Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
		LegoStructurePart fallback = null;
		for (LegoStructurePart part : container.getParts(LegoStructurePart.class)) {
			for (AABB box : part.getShape().toAabbs()) {
				if (!box.inflate(EPS).contains(local)) {
					continue;
				}
				if (Math.abs(faceCoord(box, face) - local.get(face.getAxis())) < EPS) {
					return part;
				}
				fallback = part;
			}
		}
		return fallback;
	}

	public static LegoStructurePart findHitPart(Level level, BlockHitResult hit) {
		return findHitPart(level, hit.getBlockPos(), hit.getLocation(), hit.getDirection());
	}

	private static double faceCoord(AABB box, Direction face) {
		return face.getAxisDirection() == Direction.AxisDirection.POSITIVE
			? box.max(face.getAxis())
			: box.min(face.getAxis());
	}

	static InteractionResult fail(Level level, Player player, String reason) {
		if (level.isClientSide && player != null) {
			player.displayClientMessage(Component.translatable("message.legominecraft." + reason), true);
		}
		return InteractionResult.FAIL;
	}
}
