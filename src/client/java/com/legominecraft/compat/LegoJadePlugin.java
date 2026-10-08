package com.legominecraft.compat;

import com.legominecraft.block.LegoBlock;
import com.legominecraft.block.LegoCatalog;
import com.legominecraft.block.LegoPartType;
import com.legominecraft.multipart.LegoFragmentPart;
import com.legominecraft.multipart.TechnicPlacer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum LegoJadePlugin implements IBlockComponentProvider {
	INSTANCE;

	public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("legominecraft", "lego_info");

	@Override
	public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
		BlockHitResult hit = accessor.getHitResult();
		if (hit != null) {
			LegoFragmentPart part = TechnicPlacer.findHitPart(accessor.getLevel(), hit);
			if (part != null) {
				var def = LegoCatalog.getOrDefault(part.designId());
				String size = def.kind().isStudded()
					? def.studsX() + "×" + def.studsZ()
					: def.kind() == LegoPartType.GEAR || def.kind() == LegoPartType.DOUBLE_GEAR
						? def.teeth() + "T"
						: def.length() + "L";
				tooltip.add(Component.literal("#" + def.designId() + "  " + size));
				tooltip.add(Component.translatable("jade.legominecraft.part", def.kind().getOfficialCategory()));
				return;
			}
		}
		if (accessor.getBlock() instanceof LegoBlock block) {
			tooltip.add(Component.translatable("jade.legominecraft.part", block.getPartType().getOfficialCategory()));
		}
	}

	@Override
	public ResourceLocation getUid() {
		return ID;
	}
}
