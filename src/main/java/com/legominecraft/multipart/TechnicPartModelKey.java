package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.render.PartModelKey;

public final class TechnicPartModelKey extends PartModelKey {
	private final TechnicPartData data;

	public TechnicPartModelKey(TechnicPartData data) {
		this.data = data;
	}

	public TechnicPartData data() {
		return data;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof TechnicPartModelKey other && data.equals(other.data);
	}

	@Override
	public int hashCode() {
		return data.hashCode();
	}
}
