package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Clip connections when placing: a piece with a clip clicked onto a bar or handle snaps around it, and a
 * bar clicked onto a clip, ring or hollow stud slides into it. Every orientation and lattice position
 * that makes the joint is tried, best first; the first one that fits is placed.
 */
public final class LegoClipPlacer {
	/** How close (px) the hit must be to a bar's axis to count as clicking the bar. */
	private static final double BAR_REACH = 4.5;
	/** How close (px) the hit must be to a clip's centre to count as clicking the clip. */
	private static final double CLIP_REACH = 8;
	private static final double EPS = 0.02;
	private static final int MAX_TRIES = 96;
	/** Clips slide along bars in steps of 1/6 block, which is a whole number of units on every axis. */
	private static final double SLIDE_PX = LegoPiece.PX / 6.0;

	private LegoClipPlacer() {
	}

	record Candidate(LegoPiece.Anchored anchored, double score) {
	}

	/** Null when the click is not a clip connection, so normal placement goes on. */
	public static InteractionResult tryPlace(UseOnContext ctx, LegoPartDefinition def, LegoColor color) {
		LegoPartDefinition.Fittings fittings = def.fittings();
		boolean hasClips = !fittings.clips().isEmpty();
		boolean hasBars = fittings.bars().stream().anyMatch(LegoPartDefinition.Bar::grip);
		if (!hasClips && !hasBars) {
			return null;
		}
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		Vec3 hitVec = ctx.getClickLocation();
		double[] h = {hitVec.x * LegoPiece.PX, hitVec.y * LegoPiece.PX, hitVec.z * LegoPiece.PX};
		LegoStructurePart part = LegoPiecePlacer.findHitPart(level, ctx.getClickedPos(), hitVec, ctx.getClickedFace());
		if (part == null) {
			return null;
		}
		Vec3 eyeVec = player != null ? player.getEyePosition() : hitVec.add(0, 1, 0);
		double[] eye = {eyeVec.x * LegoPiece.PX, eyeVec.y * LegoPiece.PX, eyeVec.z * LegoPiece.PX};

		List<Candidate> candidates = null;
		boolean socket = false;
		if (hasClips) {
			LegoPiece.WorldBar bar = nearestBar(part.bars(), h);
			if (bar != null) {
				candidates = onBar(def, bar, h, eye);
			}
		}
		if (candidates == null && hasBars) {
			LegoPiece.WorldClip clip = nearestClip(part.clips(), h, def.kind() == LegoPartType.BAR);
			if (clip != null) {
				candidates = intoClip(def, clip, eye);
				socket = clip.socket() != null;
			}
		}
		if (candidates == null || candidates.isEmpty()) {
			return null;
		}
		candidates.sort(Comparator.comparingDouble(Candidate::score));
		for (Candidate c : candidates.subList(0, Math.min(MAX_TRIES, candidates.size()))) {
			LegoPiece.Anchored a = c.anchored();
			List<MultipartContainer.PartOffer> offers = LegoPiecePlacer.offers(level, a.piece(), color, a.ax(), a.ay(), a.az());
			if (offers != null) {
				LegoPiecePlacer.apply(ctx, a.piece(), offers, a.ax(), a.ay(), a.az());
				return InteractionResult.sidedSuccess(level.isClientSide);
			}
		}
		return socket ? null : LegoPiecePlacer.fail(level, player, "blocked");
	}

	private static LegoPiece.WorldBar nearestBar(List<LegoPiece.WorldBar> bars, double[] h) {
		LegoPiece.WorldBar best = null;
		double bestDist = BAR_REACH;
		for (LegoPiece.WorldBar bar : bars) {
			if (!bar.grip()) {
				continue;
			}
			int k = bar.axis().ordinal();
			if (h[k] < bar.lo() - 1 || h[k] > bar.hi() + 1) {
				continue;
			}
			double d = perpendicular(bar.a(), h, k);
			if (d < bestDist) {
				best = bar;
				bestDist = d;
			}
		}
		return best;
	}

	private static LegoPiece.WorldClip nearestClip(List<LegoPiece.WorldClip> clips, double[] h, boolean sockets) {
		LegoPiece.WorldClip best = null;
		double bestDist = CLIP_REACH;
		for (LegoPiece.WorldClip clip : clips) {
			if (clip.socket() != null && !sockets) {
				continue;
			}
			double d = 0;
			for (int i = 0; i < 3; i++) {
				d += (h[i] - clip.at()[i]) * (h[i] - clip.at()[i]);
			}
			d = Math.sqrt(d);
			if (d < bestDist) {
				best = clip;
				bestDist = d;
			}
		}
		return best;
	}

	private static double perpendicular(double[] onLine, double[] p, int axis) {
		double d = 0;
		for (int i = 0; i < 3; i++) {
			if (i != axis) {
				d += (p[i] - onLine[i]) * (p[i] - onLine[i]);
			}
		}
		return Math.sqrt(d);
	}

	/** Upright first, then lying on a side, then upside down. */
	private static double upRank(Direction up) {
		return up == Direction.UP ? 0 : up == Direction.DOWN ? 2000 : 1000;
	}

	/** Positions of a clip piece holding {@code bar}: the body on the player's side, nearest the hit. */
	static List<Candidate> onBar(LegoPartDefinition def, LegoPiece.WorldBar bar, double[] h, double[] eye) {
		int k = bar.axis().ordinal();
		List<Candidate> out = new ArrayList<>();
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (Direction up : Direction.values()) {
				LegoPiece oriented = new LegoPiece(def, facing, up, 0, 0, 0);
				double[] centre = centre(oriented);
				for (LegoPiece.WorldClip clip : oriented.clips(0, 0, 0)) {
					if (clip.axis() != bar.axis() || clip.socket() != null) {
						continue;
					}
					double[] t = new double[3];
					boolean onGrid = true;
					for (int i = 0; i < 3; i++) {
						if (i != k) {
							t[i] = bar.a()[i] - clip.at()[i];
							onGrid &= isUnit(t[i], i);
						}
					}
					if (!onGrid) {
						continue;
					}
					double side = 0;
					for (int i = 0; i < 3; i++) {
						if (i != k) {
							side += (centre[i] - clip.at()[i]) * (eye[i] - bar.a()[i]);
						}
					}
					double lo = bar.lo() + clip.halfWidth();
					double hi = bar.hi() - clip.halfWidth();
					if (lo > hi + EPS) {
						for (double shift : new double[] {bar.hi() - clip.halfWidth() - clip.at()[k], bar.lo() + clip.halfWidth() - clip.at()[k]}) {
							t[k] = shift;
							if (isUnit(shift, k)) {
								out.add(new Candidate(anchor(oriented, t), upRank(up) + (side < 0 ? 500 : 0)));
							}
						}
						continue;
					}
					int n0 = (int) Math.ceil((lo - clip.at()[k]) / SLIDE_PX - EPS);
					int n1 = (int) Math.floor((hi - clip.at()[k]) / SLIDE_PX + EPS);
					for (int n = n0; n <= n1; n++) {
						t[k] = n * SLIDE_PX;
						double s = clip.at()[k] + t[k];
						double score = upRank(up) + (side < 0 ? 500 : 0) + Math.abs(s - h[k]);
						out.add(new Candidate(anchor(oriented, t), score));
					}
				}
			}
		}
		return out;
	}

	/**
	 * Positions of a bar piece held by {@code clip}: into a hollow stud it stands on the stud; through a
	 * clip or ring it sticks out toward the player, its end at the clip first.
	 */
	static List<Candidate> intoClip(LegoPartDefinition def, LegoPiece.WorldClip clip, double[] eye) {
		int k = clip.axis().ordinal();
		double toward = Math.signum(eye[k] - clip.at()[k]);
		List<Candidate> out = new ArrayList<>();
		for (Direction facing : Direction.Plane.HORIZONTAL) {
			for (Direction up : Direction.values()) {
				LegoPiece oriented = new LegoPiece(def, facing, up, 0, 0, 0);
				double rank = def.kind() == LegoPartType.BAR && def.heightPlates() <= 1 ? 0 : upRank(up);
				for (LegoPiece.WorldBar bar : oriented.bars(0, 0, 0)) {
					if (bar.axis() != clip.axis() || !bar.grip()) {
						continue;
					}
					double[] t = new double[3];
					boolean onGrid = true;
					for (int i = 0; i < 3; i++) {
						if (i != k) {
							t[i] = clip.at()[i] - bar.a()[i];
							onGrid &= isUnit(t[i], i);
						}
					}
					if (!onGrid) {
						continue;
					}
					if (clip.socket() != null) {
						// Deepest first; a blind hole (halfWidth = depth) also lets the end stop short of its bottom.
						double sign = clip.socket().getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
						double end = sign > 0 ? bar.lo() : bar.hi();
						for (double d = 0; d <= clip.halfWidth() + EPS; d += SLIDE_PX) {
							t[k] = clip.at()[k] + sign * d - end;
							if (isUnit(t[k], k)) {
								out.add(new Candidate(anchor(oriented, t), rank + d));
							}
						}
						t[k] = clip.at()[k] + sign * clip.halfWidth() - end;
						if (clip.halfWidth() > 0 && isUnit(t[k], k)) {
							out.add(new Candidate(anchor(oriented, t), rank + clip.halfWidth()));
						}
						continue;
					}
					double lo = bar.lo() + clip.halfWidth();
					double hi = bar.hi() - clip.halfWidth();
					if (lo > hi + EPS) {
						// A peg shorter than the clip goes in flush with either end; the body sits outside.
						for (double shift : new double[] {clip.at()[k] + clip.halfWidth() - bar.hi(), clip.at()[k] - clip.halfWidth() - bar.lo()}) {
							t[k] = shift;
							if (isUnit(shift, k)) {
								out.add(new Candidate(anchor(oriented, t), rank));
							}
						}
						continue;
					}
					double preferred = toward >= 0 ? lo : hi;
					int n0 = (int) Math.ceil((clip.at()[k] - hi) / SLIDE_PX - EPS);
					int n1 = (int) Math.floor((clip.at()[k] - lo) / SLIDE_PX + EPS);
					for (int n = n0; n <= n1; n++) {
						t[k] = n * SLIDE_PX;
						double s = clip.at()[k] - t[k];
						out.add(new Candidate(anchor(oriented, t), rank + Math.abs(s - preferred)));
					}
				}
			}
		}
		return out;
	}

	private static boolean isUnit(double px, int axis) {
		double u = px / LegoPiece.unitPx(axis);
		return Math.abs(u - Math.rint(u)) < EPS;
	}

	/** Body centre of a piece anchored at the origin, in px. */
	private static double[] centre(LegoPiece piece) {
		double[] b = piece.boundsPx();
		return new double[] {(b[0] + b[3]) / 2, (b[1] + b[4]) / 2, (b[2] + b[5]) / 2};
	}

	/** {@code oriented} (anchored at the origin) moved by {@code t} px, which is a whole number of units. */
	private static LegoPiece.Anchored anchor(LegoPiece oriented, double[] t) {
		int nx = LegoPiece.UNITS_X;
		int ny = LegoPiece.UNITS_Y;
		int[] u = new int[3];
		u[0] = (int) Math.rint(t[0] / LegoPiece.unitPx(0)) + oriented.tx();
		u[1] = (int) Math.rint(t[1] / LegoPiece.unitPx(1)) + oriented.ty();
		u[2] = (int) Math.rint(t[2] / LegoPiece.unitPx(2)) + oriented.tz();
		return new LegoPiece.Anchored(
			new LegoPiece(oriented.def(), oriented.facing(), oriented.up(),
				Math.floorMod(u[0], nx), Math.floorMod(u[1], ny), Math.floorMod(u[2], nx)),
			Math.floorDiv(u[0], nx), Math.floorDiv(u[1], ny), Math.floorDiv(u[2], nx));
	}
}
