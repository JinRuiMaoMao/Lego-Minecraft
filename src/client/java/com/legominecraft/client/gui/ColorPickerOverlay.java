package com.legominecraft.client.gui;

import com.legominecraft.item.LegoColorVariants;
import com.legominecraft.item.LegoPartItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Colour grid shown over the creative inventory: clicking a part's sample item in a creative tab
 * lists every colour of that part. Left click takes a full stack, right click a single item.
 */
public final class ColorPickerOverlay {
	private static final int COLS = 9;
	private static final int CELL = 18;
	private static final int PAD = 7;
	private static final int TITLE_H = 13;
	private static final int CREATIVE_W = 195;
	private static final int CREATIVE_H = 136;

	@Nullable
	private static Screen owner;
	private static List<Item> variants = List.of();
	private static Component title = Component.empty();

	private ColorPickerOverlay() {
	}

	public static boolean isOpenFor(Screen screen) {
		return owner == screen;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof CreativeModeInventoryScreen creative)) {
				return;
			}
			close();
			ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> render(s, g, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, mx, my, button) -> onClick(creative, mx, my, button));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !isOpenFor(s));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, key, scancode, modifiers) -> {
				if (!isOpenFor(s)) {
					return true;
				}
				if (key == GLFW.GLFW_KEY_ESCAPE || Minecraft.getInstance().options.keyInventory.matches(key, scancode)) {
					close();
				}
				return false;
			});
			ScreenEvents.remove(screen).register(s -> {
				if (isOpenFor(s)) {
					close();
				}
			});
		});

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (Minecraft.getInstance().screen instanceof CreativeModeInventoryScreen
				&& LegoColorVariants.variants(stack.getItem()).size() > 1) {
				lines.add(Component.translatable("tooltip.legominecraft.pick_color", LegoColorVariants.variants(stack.getItem()).size())
					.withStyle(ChatFormatting.DARK_AQUA));
			}
		});
	}

	private static void close() {
		owner = null;
		variants = List.of();
	}

	private static int rows() {
		return (variants.size() + COLS - 1) / COLS;
	}

	private static int panelW() {
		return PAD * 2 + COLS * CELL;
	}

	private static int panelH() {
		return PAD * 2 + TITLE_H + rows() * CELL;
	}

	private static int panelX(Screen s) {
		return (s.width - panelW()) / 2;
	}

	private static int panelY(Screen s) {
		return (s.height - panelH()) / 2;
	}

	private static int cellAt(Screen s, double mx, double my) {
		int gx = (int) Math.floor((mx - panelX(s) - PAD) / CELL);
		int gy = (int) Math.floor((my - panelY(s) - PAD - TITLE_H) / CELL);
		if (gx < 0 || gx >= COLS || gy < 0 || mx < panelX(s) + PAD || my < panelY(s) + PAD + TITLE_H) {
			return -1;
		}
		int index = gy * COLS + gx;
		return index < variants.size() ? index : -1;
	}

	private static boolean insidePanel(Screen s, double mx, double my) {
		return mx >= panelX(s) && mx < panelX(s) + panelW() && my >= panelY(s) && my < panelY(s) + panelH();
	}

	@Nullable
	private static Slot slotAt(CreativeModeInventoryScreen screen, double mx, double my) {
		int left = (screen.width - CREATIVE_W) / 2;
		int top = (screen.height - CREATIVE_H) / 2;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.isActive()
				&& mx >= left + slot.x - 1 && mx < left + slot.x + 17
				&& my >= top + slot.y - 1 && my < top + slot.y + 17) {
				return slot;
			}
		}
		return null;
	}

	private static boolean onClick(CreativeModeInventoryScreen screen, double mx, double my, int button) {
		Minecraft mc = Minecraft.getInstance();
		if (isOpenFor(screen)) {
			int index = cellAt(screen, mx, my);
			if (index >= 0 && (button == 0 || button == 1)) {
				ItemStack stack = new ItemStack(variants.get(index));
				stack.setCount(button == 1 ? 1 : stack.getMaxStackSize());
				screen.getMenu().setCarried(stack);
				close();
			} else if (!insidePanel(screen, mx, my)) {
				close();
			}
			return false;
		}

		if ((button != 0 && button != 1) || Screen.hasShiftDown() || mc.player == null
			|| !screen.getMenu().getCarried().isEmpty()) {
			return true;
		}
		Slot slot = slotAt(screen, mx, my);
		if (slot == null || !slot.hasItem() || slot.container == mc.player.getInventory()) {
			return true;
		}
		List<Item> found = LegoColorVariants.variants(slot.getItem().getItem());
		if (found.size() < 2) {
			return true;
		}
		owner = screen;
		variants = found;
		Component name = slot.getItem().getItem() instanceof LegoPartItem part
			? Component.translatable("gui.legominecraft.pick_color_id", part.getDefinition().designId())
			: Component.translatable("gui.legominecraft.pick_color");
		title = name;
		return false;
	}

	private static void render(Screen screen, GuiGraphics g, int mx, int my) {
		if (!isOpenFor(screen)) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int x = panelX(screen);
		int y = panelY(screen);
		int w = panelW();
		int h = panelH();

		g.pose().pushPose();
		g.pose().translate(0, 0, 500);
		g.fill(0, 0, screen.width, screen.height, 0x90000000);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
		g.fill(x, y, x + w, y + h, 0xFFC6C6C6);
		g.fill(x, y, x + w - 1, y + 1, 0xFFFFFFFF);
		g.fill(x, y, x + 1, y + h - 1, 0xFFFFFFFF);
		g.fill(x + 1, y + h - 1, x + w, y + h, 0xFF555555);
		g.fill(x + w - 1, y + 1, x + w, y + h, 0xFF555555);
		g.drawString(font, title, x + PAD, y + 6, 0xFF404040, false);

		int hovered = cellAt(screen, mx, my);
		for (int i = 0; i < variants.size(); i++) {
			int cx = x + PAD + (i % COLS) * CELL;
			int cy = y + PAD + TITLE_H + (i / COLS) * CELL;
			g.fill(cx, cy, cx + CELL, cy + CELL, 0xFF373737);
			g.fill(cx + 1, cy + 1, cx + CELL, cy + CELL, 0xFFFFFFFF);
			g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, i == hovered ? 0xFFC5C5C5 : 0xFF8B8B8B);
			g.renderItem(new ItemStack(variants.get(i)), cx + 1, cy + 1);
		}
		if (hovered >= 0) {
			g.renderTooltip(font, new ItemStack(variants.get(hovered)), mx, my);
		}
		g.pose().popPose();
	}
}
