package com.legominecraft.multipart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * Turns the pieces on one side of a clip joint a quarter around the bar. The smaller side turns; a
 * joint whose two sides are also connected some other way cannot turn.
 */
public final class LegoClipRotator {
	private static final double SAME_SPOT = 0.05;

	private LegoClipRotator() {
	}

	/** The joint whose clip centre is at {@code at} (absolute px) around a bar along {@code axis}, or null. */
	public static LegoClips.Joint findJoint(Level level, double[] at, Direction.Axis axis) {
		for (LegoFragmentPart q : LegoGrabber.partsNear(level, at[0], at[1], at[2])) {
			if (!(q instanceof LegoStructurePart s)) {
				continue;
			}
			for (LegoClips.Joint j : LegoClips.joints(level, s)) {
				double[] c = j.clip().at();
				if (j.clip().axis() == axis && Math.abs(c[0] - at[0]) < SAME_SPOT && Math.abs(c[1] - at[1]) < SAME_SPOT
					&& Math.abs(c[2] - at[2]) < SAME_SPOT) {
					return j;
				}
			}
		}
		return null;
	}

	/** The joint of {@code part} nearest the point {@code h} (absolute px), or null. */
	public static LegoClips.Joint nearestJoint(Level level, LegoStructurePart part, double[] h) {
		LegoClips.Joint best = null;
		double bestDist = Double.MAX_VALUE;
		for (LegoClips.Joint j : LegoClips.joints(level, part)) {
			double[] c = j.clip().at();
			double d = (c[0] - h[0]) * (c[0] - h[0]) + (c[1] - h[1]) * (c[1] - h[1]) + (c[2] - h[2]) * (c[2] - h[2]);
			if (d < bestDist) {
				best = j;
				bestDist = d;
			}
		}
		return best;
	}

	/**
	 * Turns one side of the joint at {@code at} by {@code steps} quarter turns (sign = direction of the
	 * mouse drag, judged from where the player looks along the bar).
	 */
	public static void rotate(Player player, Level level, double[] at, Direction.Axis axis, int steps) {
		Vec3 point = new Vec3(at[0] / LegoPiece.PX, at[1] / LegoPiece.PX, at[2] / LegoPiece.PX);
		if (steps == 0 || player.distanceToSqr(point) > 64 || !level.mayInteract(player, BlockPos.containing(point))) {
			return;
		}
		LegoClips.Joint joint = findJoint(level, at, axis);
		if (joint == null) {
			return;
		}
		Object clipKey = joint.clipPart().pieceKey();
		Object barKey = joint.barPart().pieceKey();
		Map<Object, LegoFragmentPart> clipSide = LegoGrabber.collect(level, joint.clipPart(), clipKey, barKey);
		if (clipSide.containsKey(barKey)) {
			player.displayClientMessage(Component.translatable("message.legominecraft.rotate_locked"), true);
			return;
		}
		Map<Object, LegoFragmentPart> barSide = LegoGrabber.collect(level, joint.barPart(), clipKey, barKey);
		Map<Object, LegoFragmentPart> turning = clipSide.size() <= barSide.size() ? clipSide : barSide;

		Vec3 look = player.getViewVector(1.0F);
		double along = switch (axis) {
			case X -> look.x;
			case Y -> look.y;
			case Z -> look.z;
		};
		boolean positive = (steps > 0) != (along < 0);
		LegoModel model = LegoModel.captureInPlace(turning.values());
		LegoModel turned = model;
		for (int i = 0; i < Math.abs(steps) && turned != null; i++) {
			turned = turned.rotatedAbout(axis, positive, at);
		}
		if (turned == null) {
			player.displayClientMessage(Component.translatable("message.legominecraft.rotate_technic"), true);
			return;
		}
		model.remove(level);
		if (!turned.place(level)) {
			model.place(level);
			player.displayClientMessage(Component.translatable("message.legominecraft.rotate_blocked"), true);
			return;
		}
		LegoMover.remember(player, level, turned);
		level.playSound(null, BlockPos.containing(point), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.PLAYERS, 0.6F, 1.0F);
	}
}
