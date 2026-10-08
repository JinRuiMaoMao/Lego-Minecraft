package com.legominecraft.network;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.item.LegoMoverItem;
import com.legominecraft.multipart.LegoClipRotator;
import com.legominecraft.multipart.LegoGrabber;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Client → server packets: the clip drag's quarter turn around a bar or the grab a plain click meant,
 * and the move tool's keys.
 */
public final class LegoNetwork {
	private LegoNetwork() {
	}

	/** Turn the joint whose clip centre is at (x, y, z) absolute px, around a bar along {@code axis}. */
	public record RotateClip(double x, double y, double z, Direction.Axis axis, int steps) implements CustomPacketPayload {
		public static final Type<RotateClip> TYPE = new Type<>(LegoMinecraftMod.id("rotate_clip"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RotateClip> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeDouble(p.x);
				buf.writeDouble(p.y);
				buf.writeDouble(p.z);
				buf.writeEnum(p.axis);
				buf.writeVarInt(p.steps);
			},
			buf -> new RotateClip(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readEnum(Direction.Axis.class),
				buf.readVarInt())
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record Grab(BlockPos pos, Vec3 hit, Direction face) implements CustomPacketPayload {
		public static final Type<Grab> TYPE = new Type<>(LegoMinecraftMod.id("grab"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Grab> CODEC = StreamCodec.of(
			(buf, p) -> {
				buf.writeBlockPos(p.pos);
				buf.writeDouble(p.hit.x);
				buf.writeDouble(p.hit.y);
				buf.writeDouble(p.hit.z);
				buf.writeEnum(p.face);
			},
			buf -> new Grab(buf.readBlockPos(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
				buf.readEnum(Direction.class))
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Move-tool key: nudge the player's selection one step toward {@code dir} (absolute world direction). */
	public record Move(Direction dir) implements CustomPacketPayload {
		public static final Type<Move> TYPE = new Type<>(LegoMinecraftMod.id("move"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Move> CODEC = StreamCodec.of(
			(buf, p) -> buf.writeEnum(p.dir),
			buf -> new Move(buf.readEnum(Direction.class))
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(RotateClip.TYPE, RotateClip.CODEC);
		PayloadTypeRegistry.playC2S().register(Grab.TYPE, Grab.CODEC);
		PayloadTypeRegistry.playC2S().register(Move.TYPE, Move.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Move.TYPE, (p, ctx) -> {
			if (LegoMoverItem.isHolding(ctx.player())) {
				LegoMoverItem.move(ctx.player(), ctx.player().level(), p.dir());
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(RotateClip.TYPE, (p, ctx) -> LegoClipRotator.rotate(
			ctx.player(), ctx.player().level(), new double[] {p.x(), p.y(), p.z()}, p.axis(), Math.max(-4, Math.min(4, p.steps()))));
		ServerPlayNetworking.registerGlobalReceiver(Grab.TYPE, (p, ctx) -> LegoGrabber.grab(
			ctx.player(), ctx.player().level(), new BlockHitResult(p.hit(), p.face(), p.pos(), false)));
	}
}
