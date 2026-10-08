package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.AbstractPart;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.ModBlocks;
import com.legominecraft.item.LegoPartItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * One block-cell fragment of a multi-cell LEGO piece. Fragments of a piece are mutually required,
 * so breaking any of them removes the whole piece. Exactly one item drops: the primary fragment
 * drops through normal loot, any other fragment broken by a player pops the item itself.
 */
public abstract class LegoFragmentPart extends AbstractPart {
	protected LegoFragmentPart(PartDefinition definition, MultipartHolder holder) {
		super(definition, holder);
	}

	public abstract int designId();

	public abstract LegoColor color();

	public abstract boolean isPrimary();

	/** Technic holes and pin/axle modules this fragment's piece offers, in absolute world px. */
	public abstract List<TechnicHole> technicHoles();

	public List<TechnicHole> technicModules() {
		return List.of();
	}

	/** Identifies the whole piece: equal for all fragments of the same placed piece. */
	public abstract Object pieceKey();

	/** Every block position holding a fragment of this piece. */
	public abstract List<BlockPos> pieceCells();

	public void linkTo(LegoFragmentPart other) {
		holder.addRequiredPart(other);
	}

	public void removeSelf() {
		if (holder.isPresent()) {
			holder.remove();
		}
	}

	public ItemStack itemStack() {
		return new ItemStack(LegoPartItem.byDesignAndColor(designId(), color()));
	}

	@Override
	protected BlockState getClosestBlockState() {
		return ModBlocks.get(designId(), color()).defaultBlockState();
	}

	@Override
	public ItemStack getPickStack(BlockHitResult hitResult) {
		return itemStack();
	}

	@Override
	public Component getName(BlockHitResult hitResult) {
		return itemStack().getHoverName();
	}

	@Override
	public void addDrops(NonNullList<ItemStack> drops) {
		if (isPrimary()) {
			drops.add(itemStack());
		}
	}

	@Override
	public void afterBreak(Player player) {
		Level level = holder.getContainer().getMultipartWorld();
		if (!isPrimary() && !level.isClientSide) {
			BlockPos pos = holder.getContainer().getMultipartPos();
			Block.popResource(level, pos, itemStack());
		}
	}
}
