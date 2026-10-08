package com.legominecraft.client.mixin;

import com.legominecraft.client.ClipDragController;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freezes the camera during a clip drag and hands the mouse motion to {@link ClipDragController}. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Shadow
	private double accumulatedDX;
	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void legominecraft$dragClip(double movementTime, CallbackInfo ci) {
		if (ClipDragController.isActive()) {
			ClipDragController.onMouseMoved(accumulatedDX);
			accumulatedDX = 0;
			accumulatedDY = 0;
			ci.cancel();
		}
	}
}
