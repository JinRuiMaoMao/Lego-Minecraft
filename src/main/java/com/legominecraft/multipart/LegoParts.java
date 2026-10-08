package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import alexiil.mc.lib.multipart.api.PartDefinition;
import com.legominecraft.LegoMinecraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class LegoParts {
	public static final PartDefinition STRUCTURE = new PartDefinition(
		LegoMinecraftMod.id("structure"),
		LegoStructurePart::fromNbt,
		LegoStructurePart::fromNet
	);

	public static final PartDefinition TECHNIC = new PartDefinition(
		LegoMinecraftMod.id("technic"),
		TechnicPart::fromNbt,
		TechnicPart::fromNet
	);

	public static final PartDefinition VANILLA_BLOCK = new PartDefinition(
		LegoMinecraftMod.id("vanilla_block"),
		VanillaBlockPart::fromNbt,
		VanillaBlockPart::fromNet
	);

	private LegoParts() {
	}

	public static void register() {
		STRUCTURE.register();
		TECHNIC.register();
		VANILLA_BLOCK.register();
	}

	/**
	 * {@link MultipartUtil#offerNewPart}, treating a cell LibMultiPart can't check as blocked: its overlap
	 * check throws on a part with an empty shape (a fragment whose piece no longer reaches that cell,
	 * e.g. saved before the piece's geometry changed).
	 */
	public static MultipartContainer.PartOffer offer(Level level, BlockPos pos, MultipartContainer.MultipartCreator creator) {
		try {
			return MultipartUtil.offerNewPart(level, pos, creator);
		} catch (UnsupportedOperationException e) {
			return null;
		}
	}
}
