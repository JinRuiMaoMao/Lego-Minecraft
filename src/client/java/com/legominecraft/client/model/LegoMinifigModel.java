package com.legominecraft.client.model;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.entity.LegoMinifigEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class LegoMinifigModel extends EntityModel<LegoMinifigEntity> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(
		LegoMinecraftMod.id("lego_minifig"), "main"
	);

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart leftLeg;
	private final ModelPart rightLeg;

	public LegoMinifigModel(ModelPart root) {
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.leftLeg = root.getChild("left_leg");
		this.rightLeg = root.getChild("right_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild(
			"head",
			CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 12.0F, 0.0F)
		);
		root.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, 0.0F, -2.5F, 8.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)),
			PartPose.offset(0.0F, 12.0F, 0.0F)
		);
		root.addOrReplaceChild(
			"left_leg",
			CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offset(-2.0F, 18.0F, 0.0F)
		);
		root.addOrReplaceChild(
			"right_leg",
			CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)),
			PartPose.offset(2.0F, 18.0F, 0.0F)
		);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(LegoMinifigEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.head.yRot = netHeadYaw * ((float) Math.PI / 180F);
		this.head.xRot = headPitch * ((float) Math.PI / 180F);
		this.leftLeg.xRot = (float) Math.cos(limbSwing * 0.6662F) * 0.8F * limbSwingAmount;
		this.rightLeg.xRot = (float) Math.cos(limbSwing * 0.6662F + Math.PI) * 0.8F * limbSwingAmount;
	}

	@Override
	public void renderToBuffer(
		com.mojang.blaze3d.vertex.PoseStack poseStack,
		com.mojang.blaze3d.vertex.VertexConsumer buffer,
		int packedLight,
		int packedOverlay,
		int color
	) {
		this.head.render(poseStack, buffer, packedLight, packedOverlay, color);
		this.body.render(poseStack, buffer, packedLight, packedOverlay, color);
		this.leftLeg.render(poseStack, buffer, packedLight, packedOverlay, color);
		this.rightLeg.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}
