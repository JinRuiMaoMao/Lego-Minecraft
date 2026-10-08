package com.legominecraft.client.model;

import alexiil.mc.lib.multipart.api.render.PartModelBaker;
import alexiil.mc.lib.multipart.api.render.PartRenderContext;
import com.legominecraft.mesh.Mesh;
import com.legominecraft.multipart.TechnicPartData;
import com.legominecraft.multipart.TechnicPartModelKey;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public enum TechnicPartModelBaker implements PartModelBaker<TechnicPartModelKey> {
	INSTANCE;

	private static final Map<TechnicPartData, List<Mesh.Poly>> MESHES = new ConcurrentHashMap<>();

	@Override
	public void emitQuads(TechnicPartModelKey key, PartRenderContext ctx) {
		TechnicPartData data = key.data();
		List<Mesh.Poly> polys = MESHES.computeIfAbsent(data, d -> d.piece().fragmentPolys(d.dx(), d.dy(), d.dz()));
		LegoPartModelBaker.emit(ctx.getEmitter(), polys, data.color());
	}
}
