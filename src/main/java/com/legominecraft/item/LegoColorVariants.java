package com.legominecraft.item;

import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import com.legominecraft.block.ModBlocks;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Groups the per-colour items of one LEGO part. Creative tabs show a single sample colour per
 * group; the client opens a colour grid with all variants when the sample is clicked.
 */
public final class LegoColorVariants {
	private LegoColorVariants() {
	}

	public static LegoColor sampleColor(LegoPartDefinition def) {
		return def.kind().isTechnicStudless() ? LegoColor.LIGHT_GRAY : LegoColor.RED;
	}

	public static LegoColor sampleColor(LegoPartType decorKind) {
		return LegoColor.RED;
	}

	public static final LegoColor MINIFIG_SAMPLE = LegoColor.YELLOW;

	/** All colour variants of the item's part in {@link LegoColor} order, or an empty list. */
	public static List<Item> variants(Item item) {
		List<Item> out = new ArrayList<>();
		if (item instanceof LegoPartItem part) {
			for (LegoColor color : part.getDefinition().colors()) {
				out.add(LegoPartItem.byDefinitionAndColor(part.getDefinition(), color));
			}
		} else if (item instanceof LegoBlockItem block) {
			for (LegoColor color : LegoColor.opaque()) {
				out.add(ModBlocks.get(block.getPartType(), color).asItem());
			}
		} else if (item instanceof LegoMinifigSpawnItem) {
			for (LegoColor color : LegoColor.opaque()) {
				out.add(ModItems.getMinifigSpawn(color));
			}
		}
		return out;
	}
}
