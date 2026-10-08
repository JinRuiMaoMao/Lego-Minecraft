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

/** One cell of a studded piece (brick, plate, Technic brick…) on the plate grid. */
public class LegoStructurePart extends LegoFragmentPart {
	private final LegoPartData data;
	private final LegoPiece piece;
	private VoxelShape shape;

	public LegoStructurePart(PartDefinition definition, MultipartHolder holder, LegoPartData data) {
		super(definition, holder);
		this.data = data;
		this.piece = data.piece();
	}

	public LegoPartData getData() {
		return data;
	}

	public LegoPiece getPiece() {
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
		int[] primary = piece.fragments().getFirst();
		return data.dx() == primary[0] && data.dy() == primary[1] && data.dz() == primary[2];
	}

	/** The piece's anchor cell (fragment offsets are relative to it). */
	public BlockPos originPos() {
		return holder.getContainer().getMultipartPos().offset(-data.dx(), -data.dy(), -data.dz());
	}

	@Override
	public Object pieceKey() {
		return List.of(originPos(), data.base());
	}

	@Override
	public List<BlockPos> pieceCells() {
		BlockPos o = originPos();
		List<BlockPos> cells = new ArrayList<>();
		for (int[] f : piece.fragments()) {
			cells.add(o.offset(f[0], f[1], f[2]));
		}
		return cells;
	}

	/** Lowest point of the piece's body, in absolute plates (3 per block). */
	public int bottomAbsPlates() {
		return originPos().getY() * 3 + (int) Math.floor(piece.minYPlates() + 1.0E-6);
	}

	/** Highest point of the piece's body (studs excluded), in absolute plates. */
	public int topAbsPlates() {
		return originPos().getY() * 3 + (int) Math.ceil(piece.maxYPlates() - 1.0E-6);
	}

	public List<LegoPiece.Stud> studs() {
		BlockPos o = originPos();
		return piece.studs(o.getX(), o.getY(), o.getZ());
	}

	public List<LegoPiece.Stud> receptors() {
		BlockPos o = originPos();
		return piece.receptors(o.getX(), o.getY(), o.getZ());
	}

	public List<LegoPiece.WorldBar> bars() {
		BlockPos o = originPos();
		return piece.bars(o.getX(), o.getY(), o.getZ());
	}

	public List<LegoPiece.WorldClip> clips() {
		BlockPos o = originPos();
		return piece.clips(o.getX(), o.getY(), o.getZ());
	}

	@Override
	public List<TechnicHole> technicHoles() {
		BlockPos o = originPos();
		return piece.worldHoles(o.getX(), o.getY(), o.getZ());
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
		return new LegoPartModelKey(data);
	}

	@Override
	public CompoundTag toTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.toTag(registries);
		data.write(tag);
		return tag;
	}

	public static LegoStructurePart fromNbt(
		PartDefinition def,
		MultipartHolder holder,
		CompoundTag tag,
		HolderLookup.Provider registries
	) {
		return new LegoStructurePart(def, holder, LegoPartData.fromTag(tag));
	}

	public static LegoStructurePart fromNet(
		PartDefinition def,
		MultipartHolder holder,
		NetByteBuf buf,
		IMsgReadCtx ctx
	) throws InvalidInputDataException {
		return new LegoStructurePart(def, holder, LegoPartData.fromNetwork(buf));
	}

	@Override
	public void writeCreationData(NetByteBuf buf, IMsgWriteCtx ctx) {
		data.toNetwork(buf);
	}
}
