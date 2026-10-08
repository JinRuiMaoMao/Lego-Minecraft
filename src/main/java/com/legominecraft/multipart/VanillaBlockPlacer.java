package com.legominecraft.multipart;

import alexiil.mc.lib.multipart.api.MultipartContainer;
import com.legominecraft.LegoMinecraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Right-clicking a LEGO stud with a vanilla block puts the block on the stud like a 1×1 brick: its bottom
 * face on the stud's plane, centred on the stud (side and bottom studs likewise). Clicking a face of such a
 * block puts the next one flush against it. A spot on the block grid takes a real block; anywhere else
 * the block becomes a {@link VanillaBlockPart}.
 */
public final class VanillaBlockPlacer {
	private VanillaBlockPlacer() {
	}

	public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.isSpectator() || !(stack.getItem() instanceof BlockItem item)
			|| BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(LegoMinecraftMod.MOD_ID)) {
			return InteractionResult.PASS;
		}
		double[] min = targetMin(level, hit);
		if (min == null || !level.mayInteract(player, hit.getBlockPos())) {
			return InteractionResult.PASS;
		}
		int[] cell = new int[3];
		int[] off = new int[3];
		int[] perCell = {LegoPiece.UNITS_X, LegoPiece.UNITS_Y, LegoPiece.UNITS_X};
		for (int k = 0; k < 3; k++) {
			double v = min[k] / LegoPiece.unitPx(k);
			int u = (int) Math.round(v);
			if (Math.abs(v - u) > 0.02) {
				return InteractionResult.PASS;
			}
			cell[k] = Math.floorDiv(u, perCell[k]);
			off[k] = Math.floorMod(u, perCell[k]);
		}
		BlockPos anchor = new BlockPos(cell[0], cell[1], cell[2]);
		Direction face = hit.getDirection();
		BlockPlaceContext place = new BlockPlaceContext(player, hand, stack, new BlockHitResult(hit.getLocation(), face, anchor, false));
		if (off[0] == 0 && off[1] == 0 && off[2] == 0 && place.getClickedPos().equals(anchor) && place.canPlace()) {
			return item.place(place);
		}

		BlockState state = item.getBlock().getStateForPlacement(place);
		if (state == null) {
			return LegoPiecePlacer.fail(level, player, "vanilla_blocked");
		}
		if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity() || VanillaBlockPart.blockShape(state).isEmpty()) {
			return LegoPiecePlacer.fail(level, player, "vanilla_offgrid");
		}
		VanillaBlockPart.Data base = new VanillaBlockPart.Data(state, off[0], off[1], off[2], 0, 0, 0);
		List<MultipartContainer.PartOffer> offers = new ArrayList<>();
		for (int[] f : VanillaBlockPart.fragments(state, off[0], off[1], off[2])) {
			BlockPos pos = anchor.offset(f[0], f[1], f[2]);
			if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
				return LegoPiecePlacer.fail(level, player, "vanilla_blocked");
			}
			VanillaBlockPart.Data data = base.withCell(f);
			MultipartContainer.PartOffer offer = LegoParts.offer(level, pos, holder -> new VanillaBlockPart(LegoParts.VANILLA_BLOCK, holder, data));
			if (offer == null) {
				return LegoPiecePlacer.fail(level, player, "vanilla_blocked");
			}
			offers.add(offer);
		}
		if (offers.isEmpty()) {
			return LegoPiecePlacer.fail(level, player, "vanilla_blocked");
		}
		if (!level.isClientSide) {
			List<VanillaBlockPart> parts = new ArrayList<>(offers.size());
			for (MultipartContainer.PartOffer offer : offers) {
				offer.apply();
				parts.add((VanillaBlockPart) offer.getHolder().getPart());
			}
			VanillaBlockPart main = parts.getFirst();
			for (int i = 1; i < parts.size(); i++) {
				main.linkTo(parts.get(i));
				parts.get(i).linkTo(main);
			}
			SoundType sound = state.getSoundType();
			level.playSound(null, anchor, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
			level.gameEvent(player, GameEvent.BLOCK_PLACE, anchor);
			stack.consume(1, player);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	/** Min corner (absolute world px) of a block placed at the hit, or null when the hit is no stud or such block. */
	private static double[] targetMin(Level level, BlockHitResult hit) {
		Vec3 loc = hit.getLocation();
		double[] h = {loc.x * LegoPiece.PX, loc.y * LegoPiece.PX, loc.z * LegoPiece.PX};
		Direction face = hit.getDirection();
		double[] step = {face.getStepX(), face.getStepY(), face.getStepZ()};
		LegoStructurePart part = LegoPiecePlacer.findHitPart(level, hit);
		if (part != null) {
			LegoPiece.Stud s = LegoPiecePlacer.nearest(part.studs(), face, h);
			if (s == null) {
				return null;
			}
			double[] p = {s.x(), s.y(), s.z()};
			double[] out = new double[3];
			for (int k = 0; k < 3; k++) {
				out[k] = p[k] + step[k] * LegoPiece.PX / 2.0 - LegoPiece.PX / 2.0;
			}
			return out;
		}
		VanillaBlockPart block = VanillaBlockPart.findHit(level, hit.getBlockPos(), loc, face);
		if (block == null) {
			return null;
		}
		double[] out = block.minPx();
		for (int k = 0; k < 3; k++) {
			out[k] += step[k] * LegoPiece.PX;
		}
		return out;
	}
}
