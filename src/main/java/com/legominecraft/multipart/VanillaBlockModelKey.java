package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.render.PartModelKey;
import net.minecraft.world.level.block.state.BlockState;

/** A vanilla block's model, its min corner {@code ux, uy, uz} {@link LegoPiece} units from the rendering cell's corner. */
public final class VanillaBlockModelKey extends PartModelKey {
	private final BlockState state;
	private final int ux;
	private final int uy;
	private final int uz;

	public VanillaBlockModelKey(BlockState state, int ux, int uy, int uz) {
		this.state = state;
		this.ux = ux;
		this.uy = uy;
		this.uz = uz;
	}

	public BlockState state() {
		return state;
	}

	public int ux() {
		return ux;
	}

	public int uy() {
		return uy;
	}

	public int uz() {
		return uz;
	}

	@Override
	public boolean equals(Object obj) {
		return obj instanceof VanillaBlockModelKey o && state == o.state && ux == o.ux && uy == o.uy && uz == o.uz;
	}

	@Override
	public int hashCode() {
		return ((state.hashCode() * 31 + ux) * 31 + uy) * 31 + uz;
	}
}
