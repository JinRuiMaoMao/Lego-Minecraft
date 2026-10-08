package com.legominecraft.client;

import com.legominecraft.item.LegoMoverItem;
import com.legominecraft.network.LegoNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.function.UnaryOperator;

/** While the move tool is held: arrows move the selection left / right / forward / back, Page Up / Down up / down. */
public final class MoverKeys {
	private static final String CATEGORY = "key.categories.legominecraft";

	private record Binding(KeyMapping key, UnaryOperator<Direction> fromAhead) {
	}

	private static Binding[] bindings;

	private MoverKeys() {
	}

	public static void register() {
		bindings = new Binding[] {
			bind("move_left", GLFW.GLFW_KEY_LEFT, Direction::getCounterClockWise),
			bind("move_right", GLFW.GLFW_KEY_RIGHT, Direction::getClockWise),
			bind("move_forward", GLFW.GLFW_KEY_UP, d -> d),
			bind("move_back", GLFW.GLFW_KEY_DOWN, Direction::getOpposite),
			bind("move_up", GLFW.GLFW_KEY_PAGE_UP, d -> Direction.UP),
			bind("move_down", GLFW.GLFW_KEY_PAGE_DOWN, d -> Direction.DOWN),
		};
		ClientTickEvents.END_CLIENT_TICK.register(MoverKeys::tick);
	}

	private static Binding bind(String name, int key, UnaryOperator<Direction> fromAhead) {
		KeyMapping mapping = KeyBindingHelper.registerKeyBinding(
			new KeyMapping("key.legominecraft." + name, InputConstants.Type.KEYSYM, key, CATEGORY));
		return new Binding(mapping, fromAhead);
	}

	private static void tick(Minecraft mc) {
		boolean active = mc.player != null && mc.screen == null && LegoMoverItem.isHolding(mc.player);
		for (Binding b : bindings) {
			while (b.key().consumeClick()) {
				if (active) {
					ClientPlayNetworking.send(new LegoNetwork.Move(b.fromAhead().apply(mc.player.getDirection())));
				}
			}
		}
	}
}
