package com.legominecraft.client.model;

import alexiil.mc.lib.multipart.api.render.PartModelBaker;
import alexiil.mc.lib.multipart.api.render.PartRenderContext;
import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.block.LegoColor;
import com.legominecraft.mesh.BrickMesh;
import com.legominecraft.mesh.Mesh;
import com.legominecraft.multipart.LegoPartData;
import com.legominecraft.multipart.LegoPartModelKey;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum LegoPartModelBaker implements PartModelBaker<LegoPartModelKey> {
	INSTANCE;

	private static final Map<LegoPartData, List<Mesh.Poly>> MESHES = new ConcurrentHashMap<>();
	private static RenderMaterial translucent;

	@Override
	public void emitQuads(LegoPartModelKey key, PartRenderContext ctx) {
		LegoPartData data = key.data();
		emit(ctx.getEmitter(), MESHES.computeIfAbsent(data, BrickMesh::build), data.color());
	}

	static void emit(QuadEmitter emitter, List<Mesh.Poly> polys, LegoColor color) {
		TextureAtlasSprite body = sprite("lego_flat_" + color.getId());
		TextureAtlasSprite hole = sprite(color == LegoColor.BLACK ? "lego_flat_hole_light" : "lego_flat_hole");
		RenderMaterial bodyMaterial = color.isTranslucent() ? translucent() : null;
		for (Mesh.Poly poly : polys) {
			boolean isHole = poly.material() == Mesh.HOLE;
			emitPoly(emitter, poly, isHole ? hole : body, isHole ? null : bodyMaterial);
		}
	}

	private static RenderMaterial translucent() {
		if (translucent == null) {
			translucent = RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(BlendMode.TRANSLUCENT).find();
		}
		return translucent;
	}

	private static void emitPoly(QuadEmitter emitter, Mesh.Poly poly, TextureAtlasSprite sprite, RenderMaterial material) {
		double[][] v = poly.v();
		double[] n = Mesh.normal(v);
		Direction nominal = Direction.getNearest((float) n[0], (float) n[1], (float) n[2]);
		for (int i = 1; i + 1 < v.length; i += 2) {
			int third = Math.min(i + 2, v.length - 1);
			emitter.pos(0, (float) (v[0][0] / 16), (float) (v[0][1] / 16), (float) (v[0][2] / 16));
			emitter.pos(1, (float) (v[i][0] / 16), (float) (v[i][1] / 16), (float) (v[i][2] / 16));
			emitter.pos(2, (float) (v[i + 1][0] / 16), (float) (v[i + 1][1] / 16), (float) (v[i + 1][2] / 16));
			emitter.pos(3, (float) (v[third][0] / 16), (float) (v[third][1] / 16), (float) (v[third][2] / 16));
			for (int k = 0; k < 4; k++) {
				emitter.uv(k, 0.5F, 0.5F);
			}
			emitter.spriteBake(sprite, MutableQuadView.BAKE_NORMALIZED);
			emitter.color(-1, -1, -1, -1);
			emitter.cullFace(poly.cull());
			emitter.nominalFace(nominal);
			emitter.material(material);
			emitter.emit();
		}
	}

	private static TextureAtlasSprite sprite(String name) {
		return Minecraft.getInstance()
			.getModelManager()
			.getAtlas(TextureAtlas.LOCATION_BLOCKS)
			.getSprite(LegoMinecraftMod.id("block/" + name));
	}
}
