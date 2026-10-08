package com.legominecraft.item;

import com.legominecraft.block.LegoCatalog;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import com.legominecraft.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

public final class ModItemGroups {
	public static final Map<LegoTab, CreativeModeTab> TABS = new EnumMap<>(LegoTab.class);

	private ModItemGroups() {
	}

	public static void register() {
		for (LegoTab tab : LegoTab.values()) {
			TABS.put(tab, Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				tab.resourceLocation(),
				FabricItemGroup.builder()
					.title(Component.translatable(tab.getTranslationKey()))
					.icon(() -> iconFor(tab))
					.displayItems((parameters, output) -> populateTab(tab, output))
					.build()
			));
		}
	}

	/** Only one sample colour per part is shown in the tab; every colour stays searchable. */
	private static void populateTab(LegoTab tab, CreativeModeTab.Output output) {
		if (tab == LegoTab.MISC) {
			output.accept(new ItemStack(ModItems.LEGO_MOVER));
		}
		for (LegoPartDefinition def : LegoCatalog.all()) {
			if (def.tab() != tab) {
				continue;
			}
			LegoColor sample = LegoColorVariants.sampleColor(def);
			for (LegoColor color : def.colors()) {
				output.accept(new ItemStack(LegoPartItem.byDefinitionAndColor(def, color)), visibility(color == sample));
			}
		}
		for (LegoPartType partType : LegoPartType.values()) {
			if (partType.getTab() == tab && !partType.isCatalogKind()) {
				LegoColor sample = LegoColorVariants.sampleColor(partType);
				for (LegoColor color : LegoColor.opaque()) {
					output.accept(new ItemStack(ModBlocks.get(partType, color)), visibility(color == sample));
				}
			}
		}
		if (tab == LegoTab.MINIFIG) {
			for (LegoColor color : LegoColor.opaque()) {
				output.accept(new ItemStack(ModItems.getMinifigSpawn(color)), visibility(color == LegoColorVariants.MINIFIG_SAMPLE));
			}
		}
	}

	private static CreativeModeTab.TabVisibility visibility(boolean sample) {
		return sample ? CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS : CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY;
	}

	private static ItemStack iconFor(LegoTab tab) {
		return switch (tab) {
			case STRUCTURE -> new ItemStack(LegoPartItem.byDesignAndColor(3001, LegoColor.RED));
			case TECHNIC -> new ItemStack(LegoPartItem.byDesignAndColor(3700, LegoColor.BLACK));
			case MINIFIG -> new ItemStack(ModItems.getMinifigSpawn(LegoColor.YELLOW));
			case DECOR -> new ItemStack(ModBlocks.get(LegoPartType.CHAIR, LegoColor.BROWN));
			case MISC -> new ItemStack(ModBlocks.get(LegoPartType.SIGN, LegoColor.WHITE));
		};
	}
}
