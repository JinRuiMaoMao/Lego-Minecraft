package com.legominecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Decorative LEGO block. Catalog parts also register one of these per color, but only as the
 * item model / particle source — the item itself places LibMultiPart pieces.
 */
public class LegoBlock extends Block {
	private static final VoxelShape THIN = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);
	private static final VoxelShape BODY = Block.box(2.0, 0.0, 2.0, 14.0, 14.0, 14.0);

	private final LegoPartType partType;
	private final LegoColor legoColor;

	public LegoBlock(LegoPartType partType, LegoColor legoColor, Properties properties) {
		super(properties);
		this.partType = partType;
		this.legoColor = legoColor;
	}

	public LegoPartType getPartType() {
		return partType;
	}

	public LegoColor getLegoColor() {
		return legoColor;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (partType) {
			case PLANT, LEAF, FLOWER, FABRIC, CABLE -> THIN;
			default -> BODY;
		};
	}

	@Override
	public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
		return Shapes.empty();
	}

	@Override
	public boolean useShapeForLightOcclusion(BlockState state) {
		return true;
	}
}
