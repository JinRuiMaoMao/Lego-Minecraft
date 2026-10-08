package com.legominecraft.item;

import com.legominecraft.multipart.LegoModel;
import com.legominecraft.multipart.LegoModelPlacer;
import com.legominecraft.multipart.LegoMover;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.List;

/**
 * A whole LEGO build picked up with sneak + empty-hand right-click. Placing it rebuilds every piece,
 * rotated to keep its orientation relative to the player. Creative players keep the item (copying).
 */
public class LegoModelItem extends Item {
	public LegoModelItem(Properties properties) {
		super(properties);
	}

	public static ItemStack create(LegoModel model) {
		ItemStack stack = new ItemStack(ModItems.LEGO_MODEL);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(model.toTag()));
		return stack;
	}

	public static LegoModel read(ItemStack stack) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		return LegoModel.fromTag(tag);
	}

	/** Extra clockwise quarter turns the player gave the build (about its own up axis). */
	public static int turn(ItemStack stack) {
		return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getByte(TURN_KEY);
	}

	private static final String TURN_KEY = "turn";

	private static void addTurn(ItemStack stack) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putByte(TURN_KEY, (byte) ((tag.getByte(TURN_KEY) + 1) & 3)));
	}

	/** Sneak + right-click: turn the held build a quarter clockwise instead of placing it. */
	private static InteractionResult turnHeld(Level level, Player player, ItemStack stack) {
		if (!level.isClientSide) {
			addTurn(stack);
			player.displayClientMessage(Component.translatable("message.legominecraft.model_turned"), true);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isSecondaryUseActive()) {
			return InteractionResultHolder.pass(stack);
		}
		turnHeld(level, player, stack);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Level level = ctx.getLevel();
		Player player = ctx.getPlayer();
		ItemStack stack = ctx.getItemInHand();
		if (player != null && player.isSecondaryUseActive()) {
			return turnHeld(level, player, stack);
		}
		LegoModel model = read(stack);
		if (model.size() == 0) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		LegoModelPlacer.Plan placedPlan = null;
		for (LegoModelPlacer.Plan plan : LegoModelPlacer.plans(level, ctx.getHorizontalDirection(),
			ctx.getClickedPos(), ctx.getClickedFace(), ctx.getClickLocation(), model, turn(stack))) {
			if (plan.placed().place(level)) {
				placedPlan = plan;
				break;
			}
		}
		if (placedPlan == null) {
			if (player != null) {
				player.displayClientMessage(Component.translatable("message.legominecraft.blocked"), true);
			}
			return InteractionResult.FAIL;
		}
		LegoMover.remember(player, level, placedPlan.placed());
		BlockPos base = placedPlan.base();
		level.playSound(null, base, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
		level.gameEvent(player, GameEvent.BLOCK_PLACE, base);
		if (player == null || !player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return true;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		LegoModel model = read(stack);
		int[] size = model.dimensions();
		tooltip.add(Component.translatable("tooltip.legominecraft.model_pieces", model.size()).withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.legominecraft.model_size", size[0], size[1], size[2])
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.legominecraft.model_place").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable("tooltip.legominecraft.model_rotate").withStyle(ChatFormatting.DARK_AQUA));
	}
}
