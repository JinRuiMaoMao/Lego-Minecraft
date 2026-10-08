package com.legominecraft;

import com.legominecraft.block.ModBlocks;
import com.legominecraft.multipart.LegoGrabber;
import com.legominecraft.multipart.LegoMover;
import com.legominecraft.multipart.LegoParts;
import com.legominecraft.multipart.VanillaBlockPlacer;
import com.legominecraft.entity.LegoMinifigEntity;
import com.legominecraft.entity.ModEntities;
import com.legominecraft.item.ModItemGroups;
import com.legominecraft.item.ModItems;
import com.legominecraft.network.LegoNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LegoMinecraftMod implements ModInitializer {
	public static final String MOD_ID = "legominecraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModEntities.register();
		LegoParts.register();
		ModBlocks.register();
		ModItems.register();
		ModItemGroups.register();
		FabricDefaultAttributeRegistry.register(ModEntities.LEGO_MINIFIG, LegoMinifigEntity.createAttributes());
		LegoNetwork.register();
		UseBlockCallback.EVENT.register(LegoGrabber::onUseBlock);
		UseBlockCallback.EVENT.register(VanillaBlockPlacer::onUseBlock);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LegoMover.forget(handler.getPlayer()));
		if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment()) {
			com.legominecraft.multipart.LegoSelfCheck.run();
		}
		LOGGER.info("Lego Minecraft loaded — LibMultiPart structure building enabled!");
	}

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
}
