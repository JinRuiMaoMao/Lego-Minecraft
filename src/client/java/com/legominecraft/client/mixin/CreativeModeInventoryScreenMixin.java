package com.legominecraft.client.mixin;

import com.legominecraft.client.gui.ColorPickerOverlay;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeModeInventoryScreenMixin {
	/** While the colour grid is open, the creative screen must not hover or highlight slots underneath it. */
	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int legominecraft$hideMouseX(int mouseX) {
		return ColorPickerOverlay.isOpenFor((Screen) (Object) this) ? -10000 : mouseX;
	}

	@ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
	private int legominecraft$hideMouseY(int mouseY) {
		return ColorPickerOverlay.isOpenFor((Screen) (Object) this) ? -10000 : mouseY;
	}

	/**
	 * A leading '#' normally switches to item-tag search; "#3001" (as shown in LEGO tooltips) is a
	 * design ID, so search it as plain text. Sizes typed as "1*2" or "1x2" match names written "1×2".
	 */
	@Redirect(
		method = "refreshSearchResults",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;")
	)
	private String legominecraft$designIdQuery(EditBox box) {
		String value = box.getValue();
		if (value.matches("#\\d+")) {
			return value.substring(1);
		}
		return value.replaceAll("(\\d)\\s*[*xX×]\\s*(?=\\d)", "$1×");
	}
}
