package com.legominecraft.client.model;

import alexiil.mc.lib.multipart.api.render.PartModelBaker;
import alexiil.mc.lib.multipart.api.render.PartRenderContext;
import com.legominecraft.multipart.LegoPiece;
import com.legominecraft.multipart.VanillaBlockModelKey;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/** Emits a vanilla block's baked model shifted to its offset; faces are never culled, as they rarely meet a cell side. */
public enum VanillaBlockModelBaker implements PartModelBaker<VanillaBlockModelKey> {
	INSTANCE;

	@Override
	public void emitQuads(VanillaBlockModelKey key, PartRenderContext ctx) {
		BlockState state = key.state();
		Minecraft mc = Minecraft.getInstance();
		BakedModel model = mc.getBlockRenderer().getBlockModel(state);
		RenderMaterial material = RendererAccess.INSTANCE.getRenderer().materialFinder()
			.blendMode(BlendMode.fromRenderLayer(ItemBlockRenderTypes.getChunkRenderType(state))).find();
		float ox = key.ux() / (float) LegoPiece.UNITS_X;
		float oy = key.uy() / (float) LegoPiece.UNITS_Y;
		float oz = key.uz() / (float) LegoPiece.UNITS_X;
		QuadEmitter emitter = ctx.getEmitter();
		RandomSource random = RandomSource.create();
		for (int i = 0; i <= 6; i++) {
			Direction side = i < 6 ? Direction.from3DDataValue(i) : null;
			random.setSeed(42L);
			for (BakedQuad quad : model.getQuads(state, side, random)) {
				emitter.fromVanilla(quad, material, null);
				for (int v = 0; v < 4; v++) {
					emitter.pos(v, emitter.x(v) + ox, emitter.y(v) + oy, emitter.z(v) + oz);
				}
				if (quad.isTinted()) {
					int c = 0xFF000000 | mc.getBlockColors().getColor(state, null, null, quad.getTintIndex());
					emitter.color(c, c, c, c);
				}
				emitter.colorIndex(-1);
				emitter.cullFace(null);
				emitter.emit();
			}
		}
	}
}
