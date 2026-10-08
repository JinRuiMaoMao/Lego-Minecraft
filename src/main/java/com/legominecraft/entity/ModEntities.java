package com.legominecraft.entity;

import com.legominecraft.LegoMinecraftMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	public static final EntityType<LegoMinifigEntity> LEGO_MINIFIG = Registry.register(
		BuiltInRegistries.ENTITY_TYPE,
		LegoMinecraftMod.id("lego_minifig"),
		FabricEntityTypeBuilder.create(MobCategory.CREATURE, LegoMinifigEntity::new)
			.dimensions(EntityDimensions.scalable(0.6F, 1.95F))
			.trackRangeBlocks(8)
			.build()
	);

	private ModEntities() {
	}

	public static void register() {
		// Types are registered eagerly via static fields.
	}
}
