package com.legominecraft.item;

import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** Decorative LEGO block item — places like a normal block. */
public class LegoBlockItem extends BlockItem {
	private final LegoPartType partType;
	private final LegoColor legoColor;

	public LegoBlockItem(Block block, LegoPartType partType, LegoColor legoColor, Properties properties) {
		super(block, properties);
		this.partType = partType;
		this.legoColor = legoColor;
	}

	public LegoPartType getPartType() {
		return partType;
	}

	public LegoColor getLegoColor() {
		return legoColor;
	}
}
