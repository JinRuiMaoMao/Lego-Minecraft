package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.AbstractPart;
import alexiil.mc.lib.multipart.api.MultipartContainer;
import alexiil.mc.lib.multipart.api.MultipartHolder;
import alexiil.mc.lib.multipart.api.MultipartUtil;
import alexiil.mc.lib.multipart.api.PartDefinition;
import alexiil.mc.lib.multipart.api.render.PartModelKey;
import alexiil.mc.lib.net.IMsgReadCtx;
import alexiil.mc.lib.net.IMsgWriteCtx;
import alexiil.mc.lib.net.NetByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * A vanilla block standing on LEGO studs off the block grid. At LEGO scale a block is exactly a 1×1 brick,
 * so it sits on the plate grid like one; it spans up to eight cells, one fragment per cell, and keeps only
 * its looks, shape, sounds and drop (no block entity, redstone or other behaviour).
 */
public class VanillaBlockPart extends AbstractPart {
	private static final double EPS = 1.0E-3;

	/**
	 * @param ux offset of the block's min corner inside its anchor cell, in {@link LegoPiece} units
	 *           (0..29 across, uy 0..35 up)
	 * @param dx this fragment's cell offset from the anchor cell (0 or 1)
	 */
	public record Data(BlockState state, int ux, int uy, int uz, int dx, int dy, int dz) {
		public Data withCell(int[] cell) {
			return new Data(state, ux, uy, uz, cell[0], cell[1], cell[2]);
		}
	}

	private final Data data;
	private VoxelShape shape;

	public VanillaBlockPart(PartDefinition definition, MultipartHolder holder, Data data) {
		super(definition, holder);
		this.data = data;
	}

	public Data getData() {
		return data;
	}

	public static VoxelShape blockShape(BlockState state) {
		return state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** {@code state}'s shape moved to its offset, clipped to the cell at offset {@code cell} from the anchor. */
	public static VoxelShape cellShape(BlockState state, int ux, int uy, int uz, int[] cell) {
		VoxelShape moved = blockShape(state).move(
			(double) ux / LegoPiece.UNITS_X - cell[0], (double) uy / LegoPiece.UNITS_Y - cell[1], (double) uz / LegoPiece.UNITS_X - cell[2]);
		VoxelShape clipped = Shapes.join(moved, Shapes.block(), BooleanOp.AND);
		if (clipped.isEmpty()) {
			return clipped;
		}
		AABB b = clipped.bounds();
		return b.getXsize() < EPS || b.getYsize() < EPS || b.getZsize() < EPS ? Shapes.empty() : clipped;
	}

	/** Cell offsets (0 or 1 per axis) from the anchor cell that {@code state} reaches at that offset, lowest first. */
	public static List<int[]> fragments(BlockState state, int ux, int uy, int uz) {
		List<int[]> out = new ArrayList<>();
		for (int y = 0; y <= (uy > 0 ? 1 : 0); y++) {
			for (int x = 0; x <= (ux > 0 ? 1 : 0); x++) {
				for (int z = 0; z <= (uz > 0 ? 1 : 0); z++) {
					int[] cell = {x, y, z};
					if (!cellShape(state, ux, uy, uz, cell).isEmpty()) {
						out.add(cell);
					}
				}
			}
		}
		return out;
	}

	/** The fragment that renders the whole block and drops it: the first cell the block reaches. */
	public boolean isPrimary() {
		List<int[]> cells = fragments(data.state(), data.ux(), data.uy(), data.uz());
		return !cells.isEmpty() && cells.getFirst()[0] == data.dx() && cells.getFirst()[1] == data.dy() && cells.getFirst()[2] == data.dz();
	}

	public BlockPos originPos() {
		return holder.getContainer().getMultipartPos().offset(-data.dx(), -data.dy(), -data.dz());
	}

	/** The block's min corner in absolute world px. */
	public double[] minPx() {
		BlockPos o = originPos();
		return new double[] {
			o.getX() * LegoPiece.PX + data.ux() * LegoPiece.unitPx(0),
			o.getY() * LegoPiece.PX + data.uy() * LegoPiece.unitPx(1),
			o.getZ() * LegoPiece.PX + data.uz() * LegoPiece.unitPx(2),
		};
	}

	public void linkTo(VanillaBlockPart other) {
		holder.addRequiredPart(other);
	}

	/** The block part whose box face was clicked, falling back to any box containing the hit. */
	public static VanillaBlockPart findHit(Level level, BlockPos pos, Vec3 hit, Direction face) {
		MultipartContainer container = MultipartUtil.get(level, pos);
		if (container == null) {
			return null;
		}
		Vec3 local = hit.subtract(pos.getX(), pos.getY(), pos.getZ());
		VanillaBlockPart fallback = null;
		for (VanillaBlockPart part : container.getParts(VanillaBlockPart.class)) {
			for (AABB box : part.getShape().toAabbs()) {
				if (!box.inflate(EPS).contains(local)) {
					continue;
				}
				double faceCoord = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
					? box.max(face.getAxis()) : box.min(face.getAxis());
				if (Math.abs(faceCoord - local.get(face.getAxis())) < EPS) {
					return part;
				}
				fallback = part;
			}
		}
		return fallback;
	}

	private ItemStack itemStack() {
		return new ItemStack(data.state().getBlock().asItem());
	}

	@Override
	public VoxelShape getShape() {
		if (shape == null) {
			shape = cellShape(data.state(), data.ux(), data.uy(), data.uz(), new int[] {data.dx(), data.dy(), data.dz()});
		}
		return shape;
	}

	@Override
	public PartModelKey getModelKey() {
		return isPrimary() ? new VanillaBlockModelKey(data.state(), data.ux() - data.dx() * LegoPiece.UNITS_X,
			data.uy() - data.dy() * LegoPiece.UNITS_Y, data.uz() - data.dz() * LegoPiece.UNITS_X) : null;
	}

	@Override
	protected BlockState getClosestBlockState() {
		return data.state();
	}

	@Override
	public ItemStack getPickStack(BlockHitResult hitResult) {
		return itemStack();
	}

	@Override
	public Component getName(BlockHitResult hitResult) {
		return data.state().getBlock().getName();
	}

	@Override
	public void addDrops(NonNullList<ItemStack> drops) {
		if (isPrimary() && !itemStack().isEmpty()) {
			drops.add(itemStack());
		}
	}

	@Override
	public void afterBreak(Player player) {
		Level level = holder.getContainer().getMultipartWorld();
		if (!isPrimary() && !level.isClientSide && !player.isCreative()) {
			Block.popResource(level, holder.getContainer().getMultipartPos(), itemStack());
		}
	}

	@Override
	public CompoundTag toTag(HolderLookup.Provider registries) {
		CompoundTag tag = super.toTag(registries);
		tag.put("state", NbtUtils.writeBlockState(data.state()));
		tag.putInt("ux", data.ux());
		tag.putInt("uy", data.uy());
		tag.putInt("uz", data.uz());
		tag.putInt("dx", data.dx());
		tag.putInt("dy", data.dy());
		tag.putInt("dz", data.dz());
		return tag;
	}

	public static VanillaBlockPart fromNbt(PartDefinition def, MultipartHolder holder, CompoundTag tag, HolderLookup.Provider registries) {
		BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("state"));
		return new VanillaBlockPart(def, holder, new Data(state, tag.getInt("ux"), tag.getInt("uy"), tag.getInt("uz"),
			tag.getInt("dx"), tag.getInt("dy"), tag.getInt("dz")));
	}

	public static VanillaBlockPart fromNet(PartDefinition def, MultipartHolder holder, NetByteBuf buf, IMsgReadCtx ctx) {
		BlockState state = Block.stateById(buf.readVarInt());
		return new VanillaBlockPart(def, holder, new Data(state, buf.readByte(), buf.readByte(), buf.readByte(),
			buf.readByte(), buf.readByte(), buf.readByte()));
	}

	@Override
	public void writeCreationData(NetByteBuf buf, IMsgWriteCtx ctx) {
		buf.writeVarInt(Block.getId(data.state()));
		buf.writeByte(data.ux());
		buf.writeByte(data.uy());
		buf.writeByte(data.uz());
		buf.writeByte(data.dx());
		buf.writeByte(data.dy());
		buf.writeByte(data.dz());
	}
}
