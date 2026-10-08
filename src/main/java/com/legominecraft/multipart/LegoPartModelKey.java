package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.render.PartModelKey;

public final class LegoPartModelKey extends PartModelKey {
	private final LegoPartData data;

	public LegoPartModelKey(LegoPartData data) {
		this.data = data;
	}

	public LegoPartData data() {
		return data;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof LegoPartModelKey other && data.equals(other.data);
	}

	@Override
	public int hashCode() {
		return data.hashCode();
	}
}
