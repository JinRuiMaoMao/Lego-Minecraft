package com.legominecraft.client;

import com.legominecraft.client.gui.ColorPickerOverlay;
import com.legominecraft.client.model.LegoMinifigModel;
import com.legominecraft.client.renderer.LegoMinifigRenderer;
import com.legominecraft.client.renderer.LegoModelPreview;
import com.legominecraft.block.LegoBlock;
import com.legominecraft.block.ModBlocks;
import com.legominecraft.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class LegoMinecraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityModelLayerRegistry.registerModelLayer(LegoMinifigModel.LAYER, LegoMinifigModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.LEGO_MINIFIG, LegoMinifigRenderer::new);
		ColorPickerOverlay.register();
		LegoModelPreview.register();
		ClipDragController.register();
		MoverKeys.register();
		for (Block block : ModBlocks.all()) {
			if (block instanceof LegoBlock lego && lego.getLegoColor().isTranslucent()) {
				BlockRenderLayerMap.INSTANCE.putBlock(block, RenderType.translucent());
			}
		}
	}
}
