package com.legominecraft.multipart;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side memory of what each player placed last (one piece, or every piece of a placed model),
 * and the move tool's nudging of it: one stud sideways or one plate up / down per use.
 */
public final class LegoMover {
	private static final Map<UUID, Selection> SELECTED = new ConcurrentHashMap<>();

	private record Selection(Level level, LegoModel model) {
	}

	private LegoMover() {
	}

	public static void remember(Player player, Level level, LegoModel inWorld) {
		if (player != null && !level.isClientSide && inWorld.size() > 0) {
			SELECTED.put(player.getUUID(), new Selection(level, inWorld));
		}
	}

	public static void remember(Player player, Level level, LegoFragmentPart piece) {
		remember(player, level, LegoModel.captureInPlace(List.of(piece)));
	}

	public static void forget(Player player) {
		SELECTED.remove(player.getUUID());
	}

	/** Whether {@code part} belongs to what {@code player} currently moves. */
	public static boolean isSelected(Player player, Level level, LegoFragmentPart part) {
		Selection s = SELECTED.get(player.getUUID());
		return s != null && s.level() == level && s.model().contains(part);
	}

	/** Moves the player's selection one step toward {@code dir}; reports the outcome on the action bar. */
	public static boolean move(Player player, Level level, Direction dir) {
		Selection s = SELECTED.get(player.getUUID());
		LegoModel model = s == null || s.level() != level ? null : s.model().present(level);
		if (model == null || model.size() == 0) {
			forget(player);
			player.displayClientMessage(Component.translatable("message.legominecraft.move_nothing"), true);
			return false;
		}
		LegoModel moved = model.shifted(dir.getStepX(), dir.getStepY(), dir.getStepZ());
		model.remove(level);
		if (!moved.place(level)) {
			model.place(level);
			SELECTED.put(player.getUUID(), new Selection(level, model));
			player.displayClientMessage(Component.translatable("message.legominecraft.move_blocked"), true);
			return false;
		}
		SELECTED.put(player.getUUID(), new Selection(level, moved));
		level.playSound(null, BlockPos.containing(player.getEyePosition()), SoundEvents.ITEM_FRAME_ROTATE_ITEM,
			SoundSource.PLAYERS, 0.6F, 1.2F);
		return true;
	}
}
