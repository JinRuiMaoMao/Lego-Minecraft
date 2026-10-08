package com.legominecraft.client.renderer;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.client.model.LegoMinifigModel;
import com.legominecraft.entity.LegoMinifigEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class LegoMinifigRenderer extends MobRenderer<LegoMinifigEntity, LegoMinifigModel> {
	private static final ResourceLocation TEXTURE = LegoMinecraftMod.id("textures/entity/lego_minifig.png");

	public LegoMinifigRenderer(EntityRendererProvider.Context context) {
		super(context, new LegoMinifigModel(context.bakeLayer(LegoMinifigModel.LAYER)), 0.3F);
	}

	@Override
	public ResourceLocation getTextureLocation(LegoMinifigEntity entity) {
		return TEXTURE;
	}
}
