package com.legominecraft.multipart;

import com.legominecraft.block.LegoCatalog;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * One block-cell fragment of a placed studded LEGO piece.
 *
 * @param facing rotation of the piece around its own up axis (see {@link LegoPiece})
 * @param up     world direction the piece's studs point to (UP for a normally built piece)
 * @param tx     piece offset inside its anchor cell along world x, in {@link LegoPiece} units
 *               (0..29 across, ty 0..35 up); likewise tz
 * @param dx     this fragment's cell offset from the anchor cell (may be negative)
 */
public record LegoPartData(
	int designId,
	LegoColor color,
	Direction facing,
	Direction up,
	int tx,
	int ty,
	int tz,
	int dx,
	int dy,
	int dz
) {
	/** Normally built piece: studs up, {@code bottomOffset} plates above its anchor cell's floor. */
	public static LegoPartData upright(int designId, LegoColor color, Direction facing, int bottomOffset, int dx, int dy, int dz) {
		return new LegoPartData(designId, color, facing, Direction.UP, 0, bottomOffset * LegoPiece.PLATE_UNITS, 0, dx, dy, dz);
	}

	/** The anchor fragment of {@code piece}. */
	public static LegoPartData of(LegoPiece piece, LegoColor color) {
		return new LegoPartData(piece.def().designId(), color, piece.facing(), piece.up(), piece.tx(), piece.ty(), piece.tz(), 0, 0, 0);
	}

	public LegoPartDefinition definition() {
		return LegoCatalog.getOrDefault(designId);
	}

	public LegoPartType kind() {
		return definition().kind();
	}

	public LegoPiece piece() {
		return new LegoPiece(definition(), facing, up, tx, ty, tz);
	}

	public LegoPartData withCell(int dx, int dy, int dz) {
		return new LegoPartData(designId, color, facing, up, tx, ty, tz, dx, dy, dz);
	}

	/** The piece itself, independent of which fragment this is. */
	public LegoPartData base() {
		return withCell(0, 0, 0);
	}

	public static LegoPartData fromTag(CompoundTag tag) {
		int designId = tag.contains("design_id")
			? tag.getInt("design_id")
			: LegoCatalog.legacyKindToDesignId(tag.getString("part"));
		designId = LegoCatalog.getOrDefault(designId).designId();
		Direction facing = Direction.byName(tag.getString("facing"));
		if (facing == null || facing.getAxis().isVertical()) {
			facing = Direction.NORTH;
		}
		Direction up = tag.contains("up") ? Direction.byName(tag.getString("up")) : Direction.UP;
		if (up == null) {
			up = Direction.UP;
		}
		int maxX = LegoPiece.UNITS_X - 1;
		int maxY = LegoPiece.UNITS_Y - 1;
		int x;
		int y;
		int z;
		if (tag.contains("uy")) {
			x = tag.getInt("ux");
			y = tag.getInt("uy");
			z = tag.getInt("uz");
		} else if (tag.contains("oy")) {
			// sixths of a block
			x = Math.clamp(tag.getInt("ox"), 0, 5) * (LegoPiece.UNITS_X / 6);
			y = Math.clamp(tag.getInt("oy"), 0, 5) * (LegoPiece.UNITS_Y / 6);
			z = Math.clamp(tag.getInt("oz"), 0, 5) * (LegoPiece.UNITS_X / 6);
		} else {
			// thirds of a block
			x = Math.clamp(tag.getInt("tx"), 0, 2) * (LegoPiece.UNITS_X / 3);
			y = Math.clamp(tag.getInt("bottom"), 0, 2) * (LegoPiece.UNITS_Y / 3);
			z = Math.clamp(tag.getInt("tz"), 0, 2) * (LegoPiece.UNITS_X / 3);
		}
		return new LegoPartData(
			designId,
			LegoColor.fromId(tag.getString("color")),
			facing,
			up,
			Math.clamp(x, 0, maxX),
			Math.clamp(y, 0, maxY),
			Math.clamp(z, 0, maxX),
			tag.getInt("dx"),
			tag.getInt("dy"),
			tag.getInt("dz")
		);
	}

	public void write(CompoundTag tag) {
		tag.putInt("design_id", designId);
		tag.putString("color", color.getId());
		tag.putString("facing", facing.getSerializedName());
		tag.putString("up", up.getSerializedName());
		tag.putInt("ux", tx);
		tag.putInt("uy", ty);
		tag.putInt("uz", tz);
		tag.putInt("dx", dx);
		tag.putInt("dy", dy);
		tag.putInt("dz", dz);
	}

	public void toNetwork(FriendlyByteBuf buf) {
		buf.writeVarInt(designId);
		buf.writeUtf(color.getId());
		buf.writeByte(facing.get2DDataValue());
		buf.writeByte(up.get3DDataValue());
		buf.writeByte(tx);
		buf.writeByte(ty);
		buf.writeByte(tz);
		buf.writeVarInt(dx);
		buf.writeVarInt(dy);
		buf.writeVarInt(dz);
	}

	public static LegoPartData fromNetwork(FriendlyByteBuf buf) {
		int designId = buf.readVarInt();
		LegoColor color = LegoColor.fromId(buf.readUtf());
		Direction facing = Direction.from2DDataValue(buf.readByte());
		Direction up = Direction.from3DDataValue(buf.readByte());
		int tx = buf.readByte();
		int ty = buf.readByte();
		int tz = buf.readByte();
		int dx = buf.readVarInt();
		int dy = buf.readVarInt();
		int dz = buf.readVarInt();
		return new LegoPartData(designId, color, facing, up, tx, ty, tz, dx, dy, dz);
	}
}
