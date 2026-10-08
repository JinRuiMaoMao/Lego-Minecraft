package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.multipart.api.render.PartModelKey;
import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.IMsgWriteCtx;
import alexiil.mc.lib.net.InvalidInputDataException;
import alexiil.mc.lib.net.NetByteBuf;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** One cell of a studless Technic part (beam, pin, axle, bush, gear). */
public class TechnicPart extends LegoFragmentPart {
	private final TechnicPartData data;
	private final TechnicPiece piece;
	private VoxelShape shape;

	public TechnicPart(PartDefinition definition, MultipartHolder holder, TechnicPartData data) {
		super(definition, holder);
		this.data = data;
		this.piece = data.piece();
	}

	public TechnicPartData getData() {
		return data;
	}

	public TechnicPiece getPiece() {
		return piece;
	}

	public LegoPartDefinition getDefinition() {
		return piece.def();
	}

	@Override
	public int designId() {
		return data.designId();
	}

	@Override
	public LegoColor color() {
		return data.color();
	}

	@Override
	public boolean isPrimary() {
		List<int[]> fragments = piece.fragments();
		if (fragments.isEmpty()) {
			return true;
		}
		int[] first = fragments.getFirst();
		return first[0] == data.dx() && first[1] == data.dy() && first[2] == data.dz();
	}

	public BlockPos anchorCell() {
		return holder.getContainer().getMultipartPos().offset(-data.dx(), -data.dy(), -data.dz());
	}

	@Override
	public Object pieceKey() {
		return List.of(anchorCell(), new TechnicPartData(
			data.designId(), data.color(), data.dir(), data.roll(), data.ox(), data.oy(), data.oz(), 0, 0, 0
		));
	}

	@Override
	public List<BlockPos> pieceCells() {
		BlockPos a = anchorCell();
		List<BlockPos> cells = new ArrayList<>();
		for (int[] f : piece.fragments()) {
			cells.add(a.offset(f[0], f[1], f[2]));
		}
		return cells;
	}

	@Override
	public List<TechnicHole> technicHoles() {
		BlockPos a = anchorCell();
		return piece.worldHoles(a.getX(), a.getY(), a.getZ());
	}

	@Override
	public List<TechnicHole> technicModules() {
		BlockPos a = anchorCell();
		return piece.worldModules(a.getX(), a.getY(), a.getZ());
	}

	@Override
	public VoxelShape getShape() {
		if (shape == null) {
			shape = piece.fragmentShape(data.dx(), data.dy(), data.dz());
		}
		return shape;
	}

	@Override
	public PartModelKey getModelKey() {
		return new TechnicPartModelKey(data);
	}

	@Override
	public CompoundTag toTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.toTag(registries);
		data.write(tag);
		return tag;
	}

	public static TechnicPart fromNbt(
		PartDefinition def,
		MultipartHolder holder,
		CompoundTag tag,
		HolderLookup.Provider registries
	) {
		return new TechnicPart(def, holder, TechnicPartData.fromTag(tag));
	}

	public static TechnicPart fromNet(
		PartDefinition def,
		MultipartHolder holder,
		NetByteBuf buf,
		IMsgReadCtx ctx
	) throws InvalidInputDataException {
		return new TechnicPart(def, holder, TechnicPartData.fromNetwork(buf));
	}

	@Override
	public void writeCreationData(NetByteBuf buf, IMsgWriteCtx ctx) {
		data.toNetwork(buf);
	}
}
