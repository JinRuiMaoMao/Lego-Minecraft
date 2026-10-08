package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import com.legominecraft.item.LegoModelItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Sneak + right-click with an empty hand picks up a whole LEGO build as one {@link LegoModelItem}:
 * every piece reachable through real LEGO connections (studs into the piece above, pins/axles seated
 * in holes). Pieces that merely touch, or only meet through vanilla blocks, are not connected.
 */
public final class LegoGrabber {
	private static final int MAX_PIECES = 4096;

	/**
	 * Client side: offered every sneak-use on a piece held by a clip joint; returns true when it takes
	 * the click over (a drag that turns the piece around its bar, grabbing on release without a turn).
	 */
	public static BiPredicate<LegoStructurePart, BlockHitResult> clientDrag = (part, hit) -> false;

	private LegoGrabber() {
	}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator()
			|| !player.getMainHandItem().isEmpty()) {
			return InteractionResult.PASS;
		}
		LegoFragmentPart start = TechnicPlacer.findHitPart(level, hit);
		if (start == null || !level.mayInteract(player, hit.getBlockPos())) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) {
			if (start instanceof LegoStructurePart s && clientDrag.test(s, hit)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.SUCCESS;
		}
		grab(player, level, start, hit);
		return InteractionResult.SUCCESS;
	}

	/** Server side of a grab sent after a drag that never turned anything. */
	public static void grab(Player player, Level level, BlockHitResult hit) {
		if (player.isSpectator() || !player.getMainHandItem().isEmpty()
			|| player.distanceToSqr(hit.getLocation()) > 64 || !level.mayInteract(player, hit.getBlockPos())) {
			return;
		}
		LegoFragmentPart start = TechnicPlacer.findHitPart(level, hit);
		if (start != null) {
			grab(player, level, start, hit);
		}
	}

	private static void grab(Player player, Level level, LegoFragmentPart start, BlockHitResult hit) {
		Map<Object, LegoFragmentPart> pieces = collect(level, start);
		ItemStack result = pieces.size() == 1
			? start.itemStack()
			: LegoModelItem.create(LegoModel.capture(pieces.values(), player.getDirection()));
		for (LegoFragmentPart piece : pieces.values()) {
			removePiece(level, piece);
		}
		player.getInventory().placeItemBackInInventory(result);
		level.playSound(null, hit.getBlockPos(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS,
			0.4F, 0.9F + level.random.nextFloat() * 0.3F);
		player.displayClientMessage(Component.translatable("message.legominecraft.picked_up", pieces.size()), true);
	}

	private static Map<Object, LegoFragmentPart> collect(Level level, LegoFragmentPart start) {
		return collect(level, start, null, null);
	}

	/** Every piece connected to {@code start}, not crossing the link between pieces {@code cutA} and {@code cutB}. */
	static Map<Object, LegoFragmentPart> collect(Level level, LegoFragmentPart start, Object cutA, Object cutB) {
		Map<Object, LegoFragmentPart> seen = new LinkedHashMap<>();
		Deque<LegoFragmentPart> queue = new ArrayDeque<>();
		seen.put(start.pieceKey(), start);
		queue.add(start);
		while (!queue.isEmpty() && seen.size() < MAX_PIECES) {
			LegoFragmentPart current = queue.poll();
			Object from = current.pieceKey();
			for (LegoFragmentPart next : neighbours(level, current)) {
				Object to = next.pieceKey();
				if (cutA != null && (from.equals(cutA) && to.equals(cutB) || from.equals(cutB) && to.equals(cutA))) {
					continue;
				}
				if (seen.putIfAbsent(to, next) == null) {
					queue.add(next);
				}
			}
		}
		return seen;
	}

	private static void removePiece(Level level, LegoFragmentPart piece) {
		Object key = piece.pieceKey();
		for (BlockPos pos : piece.pieceCells()) {
			MultipartContainer container = MultipartUtil.get(level, pos);
			if (container == null) {
				continue;
			}
			for (LegoFragmentPart part : container.getParts(LegoFragmentPart.class)) {
				if (part.pieceKey().equals(key)) {
					part.removeSelf();
				}
			}
		}
	}

	private static <T extends LegoFragmentPart> List<T> partsAt(Level level, BlockPos pos, Class<T> type) {
		MultipartContainer container = MultipartUtil.get(level, pos);
		return container == null ? List.of() : container.getParts(type);
	}

	/** Parts in the cells around a world-pixel point (points on cell borders belong to both sides). */
	private static Set<LegoFragmentPart> partsNear(Level level, TechnicHole p) {
		return partsNear(level, p.x(), p.y(), p.z());
	}

	static Set<LegoFragmentPart> partsNear(Level level, double x, double y, double z) {
		Set<BlockPos> cells = new LinkedHashSet<>();
		for (int i = 0; i < 8; i++) {
			cells.add(BlockPos.containing(
				(x + ((i & 1) == 0 ? -0.5 : 0.5)) / 16,
				(y + ((i & 2) == 0 ? -0.5 : 0.5)) / 16,
				(z + ((i & 4) == 0 ? -0.5 : 0.5)) / 16
			));
		}
		Set<LegoFragmentPart> out = new LinkedHashSet<>();
		for (BlockPos cell : cells) {
			out.addAll(partsAt(level, cell, LegoFragmentPart.class));
		}
		return out;
	}

	private static List<LegoFragmentPart> neighbours(Level level, LegoFragmentPart part) {
		List<LegoFragmentPart> out = new ArrayList<>();
		if (part instanceof LegoStructurePart s) {
			for (LegoPiece.Stud stud : s.studs()) {
				for (LegoFragmentPart q : partsNear(level, stud.x(), stud.y(), stud.z())) {
					if (q instanceof LegoStructurePart qs && qs.receptors().stream().anyMatch(stud::matches)) {
						out.add(q);
					}
				}
			}
			for (LegoPiece.Stud receptor : s.receptors()) {
				for (LegoFragmentPart q : partsNear(level, receptor.x(), receptor.y(), receptor.z())) {
					if (q instanceof LegoStructurePart qs && qs.studs().stream().anyMatch(receptor::matches)) {
						out.add(q);
					}
				}
			}
			for (LegoClips.Joint joint : LegoClips.joints(level, s)) {
				out.add(joint.other(s));
			}
		}
		for (TechnicHole hole : part.technicHoles()) {
			for (LegoFragmentPart q : partsNear(level, hole)) {
				if (q.technicModules().stream().anyMatch(hole::sameSpot)) {
					out.add(q);
				}
			}
		}
		for (TechnicHole module : part.technicModules()) {
			for (LegoFragmentPart q : partsNear(level, module)) {
				if (q.technicHoles().stream().anyMatch(module::sameSpot)) {
					out.add(q);
				}
			}
		}
		return out;
	}
}
