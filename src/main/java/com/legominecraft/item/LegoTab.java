package com.legominecraft.item;

import com.legominecraft.LegoMinecraftMod;
import net.minecraft.resources.ResourceLocation;

public enum LegoTab {
	STRUCTURE("lego_structure", "itemGroup.legominecraft.lego_structure"),
	TECHNIC("lego_technic", "itemGroup.legominecraft.lego_technic"),
	MINIFIG("lego_minifig", "itemGroup.legominecraft.lego_minifig"),
	DECOR("lego_decor", "itemGroup.legominecraft.lego_decor"),
	MISC("lego_misc", "itemGroup.legominecraft.lego_misc");

	private final String id;
	private final String translationKey;

	LegoTab(String id, String translationKey) {
		this.id = id;
		this.translationKey = translationKey;
	}

	public ResourceLocation resourceLocation() {
		return LegoMinecraftMod.id(id);
	}

	public String getTranslationKey() {
		return translationKey;
	}
}
