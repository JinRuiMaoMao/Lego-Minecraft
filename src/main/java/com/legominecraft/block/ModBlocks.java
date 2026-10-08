package com.legominecraft.block;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.item.LegoBlockItem;
import com.legominecraft.item.LegoPartItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ModBlocks {
	private static final Map<Integer, Map<LegoColor, Block>> BY_DESIGN_ID = new HashMap<>();
	private static final Map<LegoPartType, Map<LegoColor, Block>> BY_TYPE_AND_COLOR = new EnumMap<>(LegoPartType.class);
	private static final List<Block> ALL = new ArrayList<>();

	private ModBlocks() {
	}

	public static void register() {
		for (LegoPartDefinition def : LegoCatalog.all()) {
			Map<LegoColor, Block> byColor = new EnumMap<>(LegoColor.class);
			for (LegoColor color : def.colors()) {
				Block block = registerBlock(def.blockId(color), def.kind(), color);
				LegoPartItem.register(def, color, block, new Item.Properties());
				byColor.put(color, block);
			}
			BY_DESIGN_ID.put(def.designId(), byColor);
		}
		for (LegoPartType partType : LegoPartType.values()) {
			if (partType.isCatalogKind()) {
				continue;
			}
			Map<LegoColor, Block> byColor = new EnumMap<>(LegoColor.class);
			for (LegoColor color : LegoColor.opaque()) {
				String blockId = partType.blockId(color);
				Block block = registerBlock(blockId, partType, color);
				Registry.register(
					BuiltInRegistries.ITEM,
					LegoMinecraftMod.id(blockId),
					new LegoBlockItem(block, partType, color, new Item.Properties())
				);
				byColor.put(color, block);
			}
			BY_TYPE_AND_COLOR.put(partType, byColor);
		}
	}

	/** Falls back to white when the part doesn't come in {@code color}. */
	public static Block get(int designId, LegoColor color) {
		Map<LegoColor, Block> byColor = BY_DESIGN_ID.get(designId);
		return byColor.getOrDefault(color, byColor.get(LegoColor.WHITE));
	}

	public static Block get(LegoPartType partType, LegoColor color) {
		return BY_TYPE_AND_COLOR.get(partType).get(color);
	}

	public static List<Block> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<Block> ofType(LegoPartType partType) {
		return List.copyOf(BY_TYPE_AND_COLOR.get(partType).values());
	}

	private static Block registerBlock(String blockId, LegoPartType partType, LegoColor color) {
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
			.mapColor(color.getMapColor())
			.strength(partType.getHardness(), partType.getBlastResistance())
			.sound(partType.getSoundType())
			.noOcclusion()
			.requiresCorrectToolForDrops();
		Block block = Registry.register(
			BuiltInRegistries.BLOCK,
			LegoMinecraftMod.id(blockId),
			new LegoBlock(partType, color, properties)
		);
		ALL.add(block);
		return block;
	}
}
