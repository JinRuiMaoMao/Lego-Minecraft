package com.legominecraft.item;

import com.legominecraft.block.LegoColor;
import com.legominecraft.entity.LegoMinifigEntity;
import com.legominecraft.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class LegoMinifigSpawnItem extends Item {
	private final LegoColor color;

	public LegoMinifigSpawnItem(LegoColor color, Properties properties) {
		super(properties);
		this.color = color;
	}

	public LegoColor getLegoColor() {
		return color;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		Direction face = context.getClickedFace();
		if (face == Direction.DOWN) {
			pos = context.getClickedPos().below();
		}

		LegoMinifigEntity minifig = ModEntities.LEGO_MINIFIG.create((ServerLevel) level);
		if (minifig == null) {
			return InteractionResult.FAIL;
		}

		minifig.setLegoColor(this.color);
		minifig.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, context.getHorizontalDirection().toYRot(), 0.0F);
		level.addFreshEntity(minifig);
		context.getItemInHand().shrink(1);
		return InteractionResult.CONSUME;
	}
}
