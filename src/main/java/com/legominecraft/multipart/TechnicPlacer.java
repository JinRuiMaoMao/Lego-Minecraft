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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Places studless Technic parts:
 * <ul>
 *   <li>Pins and axles aimed at a hole are inserted into it, sticking out toward the player.</li>
 *   <li>Beams aimed at a free pin/axle segment slide onto it; gears and bushes slide onto axles.</li>
 *   <li>Otherwise the part snaps onto the clicked face (on the neighbouring Technic lattice point
 *       when the face belongs to a Technic part).</li>
 * </ul>
 * All coordinates are world pixels (block × 16); anchors are quantised to thirds of a pixel.
 */
public final class TechnicPlacer {
	private static final double PAD = 1.5;
	private static final double MOUTH = 5.5;
	private static final double HALF_LEN = 8;

	private TechnicPlacer() {
	}

	private record Target(TechnicHole spot, boolean hole, double t, Direction toward) {
	}

	private record Placement(Direction dir, Direction.Axis roll, double x, double y, double z) {
	}

	/**
	 * @param blockHit the clicked block, or null when the player clicked into the air
	 *                 (a hole's opening has no collision, so the ray can pass through it)
	 */
	public static InteractionResult tryPlace(
		Level level,
		@Nullable Player player,
		@Nullable BlockHitResult blockHit,
		ItemStack stack,
		LegoPartDefinition def,
		LegoColor color
	) {
		if (player == null) {
			return InteractionResult.PASS;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = player.blockInteractionRange() * 16;
		double tBlock = blockHit != null ? blockHit.getLocation().distanceTo(eye) * 16 : reach;
		boolean sneak = player.isSecondaryUseActive();

		List<Placement> candidates = new ArrayList<>();
		Target target = findTarget(level, def, eye, look, Math.min(reach, tBlock + PAD));
		if (target != null) {
			addTargetPlacements(candidates, def, target, look, sneak);
		} else if (blockHit != null) {
			addSurfacePlacements(candidates, level, def, blockHit, look, sneak);
		} else {
			return InteractionResult.PASS;
		}

		for (Placement placement : candidates) {
			List<MultipartContainer.PartOffer> offers = offers(level, def, color, placement);
			if (offers != null) {
				if (!level.isClientSide) {
					apply(level, player, stack, def, offers);
				}
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
		}
		if (level.isClientSide) {
			player.displayClientMessage(Component.translatable(
				target != null ? "message.legominecraft.no_fit" : "message.legominecraft.blocked"
			), true);
		}
		return InteractionResult.FAIL;
	}

	// ---- targeting ----

	private static boolean isAxial(LegoPartType kind) {
		return kind == LegoPartType.PIN || kind == LegoPartType.AXLE || kind == LegoPartType.AXLE_PIN;
	}

	private static boolean acceptsTarget(LegoPartType kind, TechnicHole spot, boolean hole) {
		if (isAxial(kind)) {
			return hole && switch (kind) {
				case PIN -> spot.accepts(false);
				default -> true;
			};
		}
		if (hole) {
			return false;
		}
		return kind == LegoPartType.BEAM || spot.cross();
	}

	/** A free pin/axle segment the player is aiming at, with the axis direction pointing toward the player. */
	public record ModuleTarget(TechnicHole spot, Direction toward) {
	}

	@Nullable
	public static ModuleTarget findFreeModule(Level level, Player player, @Nullable BlockHitResult blockHit) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = player.blockInteractionRange() * 16;
		double tBlock = blockHit != null ? blockHit.getLocation().distanceTo(eye) * 16 : reach;
		Target target = findTarget(level, (spot, hole) -> !hole, eye, look, Math.min(reach, tBlock + PAD));
		return target == null ? null : new ModuleTarget(target.spot(), target.toward());
	}

	@Nullable
	private static Target findTarget(Level level, LegoPartDefinition def, Vec3 eye, Vec3 look, double maxT) {
		return findTarget(level, (spot, hole) -> acceptsTarget(def.kind(), spot, hole), eye, look, maxT);
	}

	@Nullable
	private static Target findTarget(Level level, BiPredicate<TechnicHole, Boolean> accept, Vec3 eye, Vec3 look, double maxT) {
		List<TechnicHole> holes = new ArrayList<>();
		List<TechnicHole> modules = new ArrayList<>();
		Vec3 end = eye.add(look.scale(maxT / 16));
		BlockPos min = BlockPos.containing(Math.min(eye.x, end.x) - 1, Math.min(eye.y, end.y) - 1, Math.min(eye.z, end.z) - 1);
		BlockPos max = BlockPos.containing(Math.max(eye.x, end.x) + 1, Math.max(eye.y, end.y) + 1, Math.max(eye.z, end.z) + 1);
		for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
			MultipartContainer container = MultipartUtil.get(level, pos);
			if (container == null) {
				continue;
			}
			for (LegoFragmentPart part : container.getParts(LegoFragmentPart.class)) {
				holes.addAll(part.technicHoles());
				modules.addAll(part.technicModules());
			}
		}

		double[] o = {eye.x * 16, eye.y * 16, eye.z * 16};
		double[] d = {look.x, look.y, look.z};
		Target best = null;
		for (int pass = 0; pass < 2; pass++) {
			boolean hole = pass == 0;
			for (TechnicHole spot : hole ? holes : modules) {
				if (!accept.test(spot, hole)) {
					continue;
				}
				if (!hole && holes.stream().anyMatch(spot::sameSpot)) {
					continue;
				}
				int axis = spot.axis().ordinal();
				double[] lo = new double[3];
				double[] hi = new double[3];
				for (int i = 0; i < 3; i++) {
					double c = spot.coord(Direction.Axis.values()[i]);
					double h = i == axis ? HALF_LEN : MOUTH;
					lo[i] = c - h;
					hi[i] = c + h;
				}
				double[] hit = slab(o, d, lo, hi);
				if (hit == null || hit[0] > maxT || (hole && (int) hit[1] != axis)) {
					continue;
				}
				if (best == null || hit[0] < best.t()) {
					Direction.AxisDirection sign = o[axis] >= spot.coord(spot.axis())
						? Direction.AxisDirection.POSITIVE
						: Direction.AxisDirection.NEGATIVE;
					best = new Target(spot, hole, hit[0], Direction.get(sign, spot.axis()));
				}
			}
		}
		return best;
	}

	/** Ray/box entry: returns {t, entry axis} or null. */
	@Nullable
	private static double[] slab(double[] o, double[] d, double[] lo, double[] hi) {
		double t0 = Double.NEGATIVE_INFINITY;
		double t1 = Double.POSITIVE_INFINITY;
		int axis = -1;
		for (int i = 0; i < 3; i++) {
			if (Math.abs(d[i]) < 1.0E-9) {
				if (o[i] < lo[i] || o[i] > hi[i]) {
					return null;
				}
				continue;
			}
			double a = (lo[i] - o[i]) / d[i];
			double b = (hi[i] - o[i]) / d[i];
			if (a > b) {
				double s = a;
				a = b;
				b = s;
			}
			if (a > t0) {
				t0 = a;
				axis = i;
			}
			t1 = Math.min(t1, b);
		}
		if (t0 > t1 || t1 < 0) {
			return null;
		}
		return new double[] {Math.max(t0, 0), axis};
	}

	// ---- candidate placements ----

	private static void addTargetPlacements(List<Placement> out, LegoPartDefinition def, Target target, Vec3 look, boolean sneak) {
		TechnicHole s = target.spot();
		Direction t = target.toward();
		switch (def.kind()) {
			case PIN, AXLE -> out.add(new Placement(t, TechnicPiece.defaultRoll(t), s.x(), s.y(), s.z()));
			case AXLE_PIN -> {
				if (s.cross()) {
					Direction in = t.getOpposite();
					out.add(new Placement(in, TechnicPiece.defaultRoll(in),
						s.x() + 16 * t.getStepX(), s.y() + 16 * t.getStepY(), s.z() + 16 * t.getStepZ()));
				} else {
					out.add(new Placement(t, TechnicPiece.defaultRoll(t), s.x(), s.y(), s.z()));
				}
			}
			case BEAM -> {
				for (Direction dir : beamDirections(s.axis(), look, sneak)) {
					out.add(new Placement(dir, s.axis(), s.x(), s.y(), s.z()));
				}
			}
			case GEAR, HALF_BUSH -> {
				out.add(new Placement(t, TechnicPiece.defaultRoll(t), s.x(), s.y(), s.z()));
				out.add(new Placement(t.getOpposite(), TechnicPiece.defaultRoll(t), s.x(), s.y(), s.z()));
			}
			default -> out.add(new Placement(t, TechnicPiece.defaultRoll(t), s.x(), s.y(), s.z()));
		}
	}

	/** Beam length directions perpendicular to the hole axis, best match with the view first. */
	private static List<Direction> beamDirections(Direction.Axis holeAxis, Vec3 look, boolean sneak) {
		List<Direction> dirs = new ArrayList<>();
		for (Direction dir : Direction.values()) {
			if (dir.getAxis() != holeAxis) {
				dirs.add(dir);
			}
		}
		Vec3 flat = holeAxis == Direction.Axis.Y ? new Vec3(look.x, 0, look.z) : look;
		dirs.sort(Comparator.comparingDouble(dir -> -(dir.getStepX() * flat.x + dir.getStepY() * flat.y + dir.getStepZ() * flat.z)));
		if (sneak) {
			Direction first = dirs.getFirst();
			for (int i = 1; i < dirs.size(); i++) {
				if (dirs.get(i).getAxis() != first.getAxis()) {
					dirs.addFirst(dirs.remove(i));
					break;
				}
			}
		}
		return dirs;
	}

	private static void addSurfacePlacements(
		List<Placement> out,
		Level level,
		LegoPartDefinition def,
		BlockHitResult blockHit,
		Vec3 look,
		boolean sneak
	) {
		Direction face = blockHit.getDirection();
		BlockPos clicked = blockHit.getBlockPos();
		Vec3 hit = blockHit.getLocation().scale(16);
		double[] p = surfaceAnchor(level, clicked, face, hit);

		switch (def.kind()) {
			case BEAM -> {
				for (Direction dir : beamDirections(face.getAxis(), look, sneak)) {
					out.add(new Placement(dir, face.getAxis(), p[0], p[1], p[2]));
				}
			}
			default -> out.add(new Placement(face, TechnicPiece.defaultRoll(face), p[0], p[1], p[2]));
		}
	}

	private static double[] surfaceAnchor(Level level, BlockPos clicked, Direction face, Vec3 hit) {
		LegoFragmentPart part = findHitPart(level, clicked, hit.scale(1.0 / 16), face);
		if (part instanceof TechnicPart) {
			List<TechnicHole> lattice = new ArrayList<>(part.technicHoles());
			lattice.addAll(part.technicModules());
			TechnicHole near = null;
			double bestDist = Double.MAX_VALUE;
			for (TechnicHole h : lattice) {
				double dist = Math.pow(h.x() - hit.x, 2) + Math.pow(h.y() - hit.y, 2) + Math.pow(h.z() - hit.z, 2);
				if (dist < bestDist) {
					bestDist = dist;
					near = h;
				}
			}
			if (near != null) {
				double[] p = {near.x(), near.y(), near.z()};
				double[] h = {hit.x, hit.y, hit.z};
				int a = face.getAxis().ordinal();
				for (int i = 0; i < 3; i++) {
					p[i] += i == a
						? 16 * face.getAxisDirection().getStep()
						: 16 * Math.round((h[i] - p[i]) / 16);
				}
				return p;
			}
		}
		BlockPos cell = part == null && level.getBlockState(clicked).canBeReplaced() && MultipartUtil.get(level, clicked) == null
			? clicked
			: clicked.relative(face);
		return new double[] {cell.getX() * 16 + 8, cell.getY() * 16 + 8, cell.getZ() * 16 + 8};
	}

	/** Finds the LEGO fragment whose box face was clicked, falling back to any box containing the hit. */
	@Nullable
	public static LegoFragmentPart findHitPart(Level level, BlockPos pos, Vec3 hit, Direction face) {
		MultipartContainer container = MultipartUtil.get(level, pos);
		if (container == null) {
			return null;
		}
		Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
		LegoFragmentPart fallback = null;
		for (LegoFragmentPart part : container.getParts(LegoFragmentPart.class)) {
			for (AABB box : part.getShape().toAabbs()) {
				if (!box.inflate(1.0E-3).contains(local)) {
					continue;
				}
				double faceCoord = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
					? box.max(face.getAxis())
					: box.min(face.getAxis());
				if (Math.abs(faceCoord - local.get(face.getAxis())) < 1.0E-3) {
					return part;
				}
				fallback = part;
			}
		}
		return fallback;
	}

	@Nullable
	public static LegoFragmentPart findHitPart(Level level, BlockHitResult hit) {
		return findHitPart(level, hit.getBlockPos(), hit.getLocation(), hit.getDirection());
	}

	// ---- offers ----

	@Nullable
	private static List<MultipartContainer.PartOffer> offers(Level level, LegoPartDefinition def, LegoColor color, Placement pl) {
		long tx = Math.round(pl.x() * 3);
		long ty = Math.round(pl.y() * 3);
		long tz = Math.round(pl.z() * 3);
		int per = TechnicPartData.THIRDS_PER_CELL;
		int cx = (int) Math.floorDiv(tx, per);
		int cy = (int) Math.floorDiv(ty, per);
		int cz = (int) Math.floorDiv(tz, per);
		int ox = (int) Math.floorMod(tx, per);
		int oy = (int) Math.floorMod(ty, per);
		int oz = (int) Math.floorMod(tz, per);
		TechnicPiece piece = TechnicPiece.of(def, pl.dir(), pl.roll(), ox, oy, oz);
		if (piece.fragments().isEmpty()) {
			return null;
		}
		List<MultipartContainer.PartOffer> offers = new ArrayList<>();
		for (int[] f : piece.fragments()) {
			BlockPos pos = new BlockPos(cx + f[0], cy + f[1], cz + f[2]);
			if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
				return null;
			}
			TechnicPartData data = new TechnicPartData(
				def.designId(), color, piece.dir(), piece.roll(), ox, oy, oz, f[0], f[1], f[2]
			);
			MultipartContainer.PartOffer offer = LegoParts.offer(
				level, pos, holder -> new TechnicPart(LegoParts.TECHNIC, holder, data)
			);
			if (offer == null) {
				return null;
			}
			offers.add(offer);
		}
		return offers;
	}

	private static void apply(Level level, Player player, ItemStack stack, LegoPartDefinition def, List<MultipartContainer.PartOffer> offers) {
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
		LegoMover.remember(player, level, main);
		BlockPos pos = offers.getFirst().getHolder().getContainer().getMultipartPos();
		SoundType sound = def.kind().getSoundType();
		level.playSound(
			null, pos, sound.getPlaceSound(), SoundSource.BLOCKS,
			(sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F
		);
		level.gameEvent(player, GameEvent.BLOCK_PLACE, pos);
		stack.consume(1, player);
	}
}
