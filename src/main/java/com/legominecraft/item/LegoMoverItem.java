package com.legominecraft.item;

import com.legominecraft.multipart.LegoFragmentPart;
import com.legominecraft.multipart.LegoMover;
import com.legominecraft.multipart.TechnicPlacer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Nudges what the player placed last, the whole model if that was a placed {@link LegoModelItem}:
 * one stud to the player's right per use (left while sneaking), or one plate up / down while looking
 * steeply up / down; while held, the move keys (arrows, Page Up / Down) move it in all six directions.
 * Using it on another LEGO piece selects that piece instead.
 */
public class LegoMoverItem extends Item {
	private static final float STEEP_PITCH = 45.0F;

	public LegoMoverItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Player player = ctx.getPlayer();
		Level level = ctx.getLevel();
		if (player == null) {
			return InteractionResult.PASS;
		}
		LegoFragmentPart part = TechnicPlacer.findHitPart(level, ctx.getClickedPos(), ctx.getClickLocation(), ctx.getClickedFace());
		if (part == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (LegoMover.isSelected(player, level, part)) {
			move(player, level);
		} else {
			LegoMover.remember(player, level, part);
			player.displayClientMessage(Component.translatable("message.legominecraft.move_selected"), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) {
			move(player, level);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	private static void move(Player player, Level level) {
		Direction dir;
		if (player.getXRot() < -STEEP_PITCH) {
			dir = Direction.UP;
		} else if (player.getXRot() > STEEP_PITCH) {
			dir = Direction.DOWN;
		} else {
			dir = player.getDirection().getClockWise();
		}
		move(player, level, player.isShiftKeyDown() ? dir.getOpposite() : dir);
	}

	/** Moves the player's selection one step toward {@code dir}, naming the direction as the player sees it. */
	public static void move(Player player, Level level, Direction dir) {
		Direction ahead = player.getDirection();
		String label;
		if (dir == Direction.UP) {
			label = "up";
		} else if (dir == Direction.DOWN) {
			label = "down";
		} else if (dir == ahead) {
			label = "forward";
		} else if (dir == ahead.getOpposite()) {
			label = "back";
		} else {
			label = dir == ahead.getClockWise() ? "right" : "left";
		}
		if (LegoMover.move(player, level, dir)) {
			player.displayClientMessage(Component.translatable("message.legominecraft.moved",
				Component.translatable("message.legominecraft.move_" + label)), true);
		}
	}

	public static boolean isHolding(Player player) {
		return player.getMainHandItem().getItem() instanceof LegoMoverItem
			|| player.getOffhandItem().getItem() instanceof LegoMoverItem;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.legominecraft.mover_target").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.legominecraft.mover_keys").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("tooltip.legominecraft.mover_side").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable("tooltip.legominecraft.mover_vertical").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable("tooltip.legominecraft.mover_select").withStyle(ChatFormatting.DARK_AQUA));
	}
}
