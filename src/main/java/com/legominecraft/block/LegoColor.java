package com.legominecraft.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;
import java.util.List;

/** Official LEGO colours (BrickLink names in comments). */
public enum LegoColor {
	RED("red", "Red", "红色", 0xFFC91A09, MapColor.COLOR_RED, DyeColor.RED),
	BLUE("blue", "Blue", "蓝色", 0xFF0055BF, MapColor.COLOR_BLUE, DyeColor.BLUE),
	YELLOW("yellow", "Yellow", "黄色", 0xFFF2CD37, MapColor.COLOR_YELLOW, DyeColor.YELLOW),
	GREEN("green", "Green", "绿色", 0xFF00852B, MapColor.COLOR_GREEN, DyeColor.GREEN),
	WHITE("white", "White", "白色", 0xFFF4F4F4, MapColor.SNOW, DyeColor.WHITE),
	BLACK("black", "Black", "黑色", 0xFF05131D, MapColor.COLOR_BLACK, DyeColor.BLACK),
	ORANGE("orange", "Orange", "橙色", 0xFFFE8A18, MapColor.COLOR_ORANGE, DyeColor.ORANGE),
	LIME("lime", "Lime", "黄绿色", 0xFFA6CE39, MapColor.COLOR_LIGHT_GREEN, DyeColor.LIME),
	CYAN("cyan", "Cyan", "青色", 0xFF00B7B7, MapColor.COLOR_CYAN, DyeColor.CYAN),
	MAGENTA("magenta", "Magenta", "品红色", 0xFF901F76, MapColor.COLOR_MAGENTA, DyeColor.MAGENTA),
	PINK("pink", "Pink", "粉红色", 0xFFFC97AC, MapColor.COLOR_PINK, DyeColor.PINK),
	LIGHT_GRAY("light_gray", "Light Gray", "淡灰色", 0xFFA0A0A0, MapColor.COLOR_LIGHT_GRAY, DyeColor.LIGHT_GRAY),
	GRAY("gray", "Gray", "灰色", 0xFF636363, MapColor.COLOR_GRAY, DyeColor.GRAY),
	BROWN("brown", "Brown", "棕色", 0xFF6A3909, MapColor.COLOR_BROWN, DyeColor.BROWN),
	/** Medium Lavender */
	PURPLE("purple", "Purple", "紫色", 0xFFAC78BA, MapColor.COLOR_PURPLE, DyeColor.PURPLE),
	/** Medium Azure */
	LIGHT_BLUE("light_blue", "Medium Azure", "中天蓝色", 0xFF36AEBF, MapColor.COLOR_LIGHT_BLUE, DyeColor.LIGHT_BLUE),
	/** Tan (Brick Yellow) */
	TAN("tan", "Tan", "沙黄色", 0xFFE4CD9E, MapColor.SAND, DyeColor.YELLOW),
	DARK_TAN("dark_tan", "Dark Tan", "深沙黄色", 0xFF958A73, MapColor.WOOD, DyeColor.BROWN),
	DARK_RED("dark_red", "Dark Red", "深红色", 0xFF720E0F, MapColor.NETHER, DyeColor.RED),
	DARK_BLUE("dark_blue", "Dark Blue", "深蓝色", 0xFF0A3463, MapColor.LAPIS, DyeColor.BLUE),
	DARK_GREEN("dark_green", "Dark Green", "深绿色", 0xFF184632, MapColor.COLOR_GREEN, DyeColor.GREEN),
	SAND_GREEN("sand_green", "Sand Green", "沙绿色", 0xFFA0BCAC, MapColor.COLOR_LIGHT_GREEN, DyeColor.GREEN),
	/** Bright Light Orange (Flame Yellowish Orange) */
	LIGHT_ORANGE("light_orange", "Bright Light Orange", "亮橙黄色", 0xFFF8BB3D, MapColor.GOLD, DyeColor.ORANGE),
	DARK_ORANGE("dark_orange", "Dark Orange", "深橙色", 0xFFA95500, MapColor.COLOR_ORANGE, DyeColor.ORANGE),
	LAVENDER("lavender", "Lavender", "薰衣草紫", 0xFFE1D5ED, MapColor.COLOR_PURPLE, DyeColor.PURPLE),
	DARK_BROWN("dark_brown", "Dark Brown", "深棕色", 0xFF352100, MapColor.PODZOL, DyeColor.BROWN),

	// Transparent colours: only parts that really exist in them get these variants.
	TRANS_CLEAR("trans_clear", "Trans-Clear", "透明", 0x80FCFCFC, MapColor.NONE, DyeColor.WHITE),
	TRANS_BLACK("trans_black", "Trans-Black", "透明黑", 0xA0635F52, MapColor.COLOR_BLACK, DyeColor.BLACK),
	TRANS_BROWN("trans_brown", "Trans-Brown", "透明棕", 0xA0635F52, MapColor.COLOR_BROWN, DyeColor.BROWN),
	TRANS_RED("trans_red", "Trans-Red", "透明红", 0xA0C91A09, MapColor.COLOR_RED, DyeColor.RED),
	TRANS_DARK_PINK("trans_dark_pink", "Trans-Dark Pink", "透明深粉", 0xA0DF6695, MapColor.COLOR_MAGENTA, DyeColor.MAGENTA),
	TRANS_PINK("trans_pink", "Trans-Pink", "透明粉", 0xA0E4ADC8, MapColor.COLOR_PINK, DyeColor.PINK),
	TRANS_PURPLE("trans_purple", "Trans-Purple", "透明紫", 0xA0A5A5CB, MapColor.COLOR_PURPLE, DyeColor.PURPLE),
	TRANS_DARK_BLUE("trans_dark_blue", "Trans-Dark Blue", "透明深蓝", 0xA00020A0, MapColor.COLOR_BLUE, DyeColor.BLUE),
	TRANS_MEDIUM_BLUE("trans_medium_blue", "Trans-Medium Blue", "透明中蓝", 0xA0CFE2F7, MapColor.COLOR_LIGHT_BLUE, DyeColor.LIGHT_BLUE),
	TRANS_LIGHT_BLUE("trans_light_blue", "Trans-Light Blue", "透明浅蓝", 0xA0AEEFEC, MapColor.COLOR_LIGHT_BLUE, DyeColor.LIGHT_BLUE),
	TRANS_GREEN("trans_green", "Trans-Green", "透明绿", 0xA084B68D, MapColor.COLOR_GREEN, DyeColor.GREEN),
	TRANS_BRIGHT_GREEN("trans_bright_green", "Trans-Bright Green", "透明亮绿", 0xA0D9E4A7, MapColor.COLOR_LIGHT_GREEN, DyeColor.LIME),
	TRANS_NEON_GREEN("trans_neon_green", "Trans-Neon Green", "透明荧光绿", 0xA0F8F184, MapColor.COLOR_YELLOW, DyeColor.LIME),
	TRANS_YELLOW("trans_yellow", "Trans-Yellow", "透明黄", 0xA0F5CD2F, MapColor.COLOR_YELLOW, DyeColor.YELLOW),
	TRANS_ORANGE("trans_orange", "Trans-Orange", "透明橙", 0xA0F08F1C, MapColor.COLOR_ORANGE, DyeColor.ORANGE),
	TRANS_NEON_ORANGE("trans_neon_orange", "Trans-Neon Orange", "透明荧光橙", 0xA0FF800D, MapColor.COLOR_ORANGE, DyeColor.ORANGE),
	OPAL_TRANS_CLEAR("opal_trans_clear", "Opal Trans-Clear", "蛋白石透明", 0xB0FCFCFC, MapColor.SNOW, DyeColor.WHITE),
	OPAL_TRANS_LIGHT_BLUE("opal_trans_light_blue", "Opal Trans-Light Blue", "蛋白石透明浅蓝", 0xB068BCC5, MapColor.COLOR_LIGHT_BLUE, DyeColor.LIGHT_BLUE),
	OPAL_TRANS_PURPLE("opal_trans_purple", "Opal Trans-Purple", "蛋白石透明紫", 0xB08320B7, MapColor.COLOR_PURPLE, DyeColor.PURPLE);

	private static final List<LegoColor> OPAQUE = Arrays.stream(values()).filter(c -> !c.isTranslucent()).toList();

	private final String id;
	private final String nameEn;
	private final String nameZh;
	private final int rgb;
	private final MapColor mapColor;
	private final DyeColor dyeColor;

	LegoColor(String id, String nameEn, String nameZh, int argb, MapColor mapColor, DyeColor dyeColor) {
		this.id = id;
		this.nameEn = nameEn;
		this.nameZh = nameZh;
		this.rgb = argb;
		this.mapColor = mapColor;
		this.dyeColor = dyeColor;
	}

	/** The 26 solid colours every part and decorative block comes in. */
	public static List<LegoColor> opaque() {
		return OPAQUE;
	}

	public boolean isTranslucent() {
		return (rgb >>> 24) != 0xFF;
	}

	public String getId() {
		return id;
	}

	public String getDisplayNameEn() {
		return nameEn;
	}

	public String getDisplayNameZh() {
		return nameZh;
	}

	public MapColor getMapColor() {
		return mapColor;
	}

	public DyeColor getDyeColor() {
		return dyeColor;
	}

	/** BrickLink / LEGO palette ARGB (matches texture generator); alpha below 0xFF for transparent colours. */
	public int getRgb() {
		return rgb;
	}

	public static LegoColor fromId(String id) {
		for (LegoColor color : values()) {
			if (color.id.equals(id)) {
				return color;
			}
		}
		return RED;
	}
}
