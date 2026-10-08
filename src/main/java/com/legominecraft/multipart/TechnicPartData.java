package com.legominecraft.multipart;

import com.legominecraft.block.LegoCatalog;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One block-cell fragment of a studless Technic part.
 *
 * @param dir        direction the part's length runs in (beam/axle/pin), or its axis (gear/bush)
 * @param roll       hole axis of a beam (perpendicular to {@code dir}); ignored by round parts
 * @param ox         anchor position inside the anchor cell, in thirds of a pixel (0..47)
 * @param dx         this fragment's cell offset from the anchor cell (may be negative)
 */
public record TechnicPartData(
	int designId,
	LegoColor color,
	Direction dir,
	Direction.Axis roll,
	int ox,
	int oy,
	int oz,
	int dx,
	int dy,
	int dz
) {
	public static final int THIRDS_PER_CELL = 48;

	public LegoPartDefinition definition() {
		return LegoCatalog.getOrDefault(designId);
	}

	public TechnicPiece piece() {
		return TechnicPiece.of(definition(), dir, roll, ox, oy, oz);
	}

	public boolean isAnchor() {
		return dx == 0 && dy == 0 && dz == 0;
	}

	public static TechnicPartData fromTag(CompoundTag tag) {
		Direction dir = Direction.from3DDataValue(tag.getByte("dir"));
		Direction.Axis roll = Direction.Axis.values()[Math.floorMod(tag.getByte("roll"), 3)];
		if (roll == dir.getAxis()) {
			roll = TechnicPiece.defaultRoll(dir);
		}
		return new TechnicPartData(
			tag.getInt("design_id"),
			LegoColor.fromId(tag.getString("color")),
			dir,
			roll,
			Math.floorMod(tag.getInt("ox"), THIRDS_PER_CELL),
			Math.floorMod(tag.getInt("oy"), THIRDS_PER_CELL),
			Math.floorMod(tag.getInt("oz"), THIRDS_PER_CELL),
			tag.getInt("dx"),
			tag.getInt("dy"),
			tag.getInt("dz")
		);
	}

	public void write(CompoundTag tag) {
		tag.putInt("design_id", designId);
		tag.putString("color", color.getId());
		tag.putByte("dir", (byte) dir.get3DDataValue());
		tag.putByte("roll", (byte) roll.ordinal());
		tag.putInt("ox", ox);
		tag.putInt("oy", oy);
		tag.putInt("oz", oz);
		tag.putInt("dx", dx);
		tag.putInt("dy", dy);
		tag.putInt("dz", dz);
	}

	public void toNetwork(FriendlyByteBuf buf) {
		buf.writeVarInt(designId);
		buf.writeUtf(color.getId());
		buf.writeByte(dir.get3DDataValue());
		buf.writeByte(roll.ordinal());
		buf.writeByte(ox);
		buf.writeByte(oy);
		buf.writeByte(oz);
		buf.writeVarInt(dx);
		buf.writeVarInt(dy);
		buf.writeVarInt(dz);
	}

	public static TechnicPartData fromNetwork(FriendlyByteBuf buf) {
		int designId = buf.readVarInt();
		LegoColor color = LegoColor.fromId(buf.readUtf());
		Direction dir = Direction.from3DDataValue(buf.readByte());
		Direction.Axis roll = Direction.Axis.values()[Math.floorMod(buf.readByte(), 3)];
		int ox = buf.readByte();
		int oy = buf.readByte();
		int oz = buf.readByte();
		int dx = buf.readVarInt();
		int dy = buf.readVarInt();
		int dz = buf.readVarInt();
		return new TechnicPartData(designId, color, dir, roll, ox, oy, oz, dx, dy, dz);
	}
}
