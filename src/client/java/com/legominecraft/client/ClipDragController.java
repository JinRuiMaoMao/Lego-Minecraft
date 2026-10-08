package com.legominecraft.client;

import com.legominecraft.multipart.LegoClipRotator;
import com.legominecraft.multipart.LegoClips;
import com.legominecraft.multipart.LegoGrabber;
import com.legominecraft.multipart.LegoPiece;
import com.legominecraft.multipart.LegoStructurePart;
import com.legominecraft.network.LegoNetwork;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Empty-hand sneak + right-button drag on a piece held by a clip: while the button is down the camera
 * stays still and sideways mouse motion turns the piece around its bar in quarter steps. Releasing
 * without having turned anything grabs the build as a plain sneak-click would.
 */
public final class ClipDragController {
	/** Mouse travel (raw pixels) per quarter turn. */
	private static final double STEP = 60;

	private static boolean active;
	private static BlockHitResult hit;
	private static double[] at;
	private static Direction.Axis axis;
	private static double travel;
	private static boolean turned;

	private ClipDragController() {
	}

	public static void register() {
		LegoGrabber.clientDrag = ClipDragController::start;
		ClientTickEvents.END_CLIENT_TICK.register(ClipDragController::tick);
	}

	public static boolean isActive() {
		return active;
	}

	private static boolean start(LegoStructurePart part, BlockHitResult blockHit) {
		if (active) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		double[] h = {blockHit.getLocation().x * LegoPiece.PX, blockHit.getLocation().y * LegoPiece.PX,
			blockHit.getLocation().z * LegoPiece.PX};
		LegoClips.Joint joint = LegoClipRotator.nearestJoint(mc.level, part, h);
		if (joint == null) {
			return false;
		}
		active = true;
		hit = blockHit;
		at = joint.clip().at().clone();
		axis = joint.clip().axis();
		travel = 0;
		turned = false;
		return true;
	}

	/** Called with the mouse motion of one frame while dragging, instead of turning the camera. */
	public static void onMouseMoved(double dx) {
		travel += dx;
		while (Math.abs(travel) >= STEP) {
			int step = travel > 0 ? 1 : -1;
			travel -= step * STEP;
			turned = true;
			ClientPlayNetworking.send(new LegoNetwork.RotateClip(at[0], at[1], at[2], axis, step));
		}
	}

	private static void tick(Minecraft mc) {
		if (!active) {
			return;
		}
		if (mc.player == null || mc.screen != null) {
			active = false;
			return;
		}
		if (!mc.options.keyUse.isDown()) {
			active = false;
			if (!turned) {
				ClientPlayNetworking.send(new LegoNetwork.Grab(hit.getBlockPos(), hit.getLocation(), hit.getDirection()));
				mc.player.swing(InteractionHand.MAIN_HAND);
			}
		}
	}
}
