package com.legominecraft.item;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.block.LegoColor;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
	private static final Map<LegoColor, Item> MINIFIG_SPAWN_ITEMS = new EnumMap<>(LegoColor.class);

	public static final Item LEGO_MODEL = Registry.register(
		BuiltInRegistries.ITEM,
		LegoMinecraftMod.id("lego_model"),
		new LegoModelItem(new Item.Properties().stacksTo(1))
	);

	public static final Item LEGO_MOVER = Registry.register(
		BuiltInRegistries.ITEM,
		LegoMinecraftMod.id("lego_mover"),
		new LegoMoverItem(new Item.Properties().stacksTo(1))
	);

	private ModItems() {
	}

	public static void register() {
		for (LegoColor color : LegoColor.opaque()) {
			String id = "lego_minifig_spawn_" + color.getId();
			Item item = Registry.register(
				BuiltInRegistries.ITEM,
				LegoMinecraftMod.id(id),
				new LegoMinifigSpawnItem(color, new Item.Properties())
			);
			MINIFIG_SPAWN_ITEMS.put(color, item);
		}
	}

	public static Item getMinifigSpawn(LegoColor color) {
		return MINIFIG_SPAWN_ITEMS.get(color);
	}

	public static Map<LegoColor, Item> minifigSpawnItems() {
		return Collections.unmodifiableMap(MINIFIG_SPAWN_ITEMS);
	}
}
