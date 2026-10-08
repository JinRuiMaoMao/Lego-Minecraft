package com.legominecraft.block;

import com.legominecraft.item.LegoTab;
import net.minecraft.world.level.block.SoundType;

/**
 * Part kinds. Studded kinds (bricks, plates, Technic bricks…) are placed on the plate grid as
 * multi-cell LibMultiPart pieces; studless Technic kinds live on the 8 mm hole lattice; the rest
 * are simple decorative blocks.
 */
public enum LegoPartType {
	BRICK("brick", "Bricks", LegoTab.STRUCTURE, 2.0F, 3.0F, 3, true),
	PLATE("plate", "Plates", LegoTab.STRUCTURE, 1.5F, 2.5F, 1, true),
	TILE("tile", "Tiles", LegoTab.STRUCTURE, 1.5F, 2.5F, 1, false),
	ROUND_TILE("round_tile", "Tiles", LegoTab.STRUCTURE, 1.5F, 2.5F, 1, false),
	SLOPE("slope", "Slopes", LegoTab.STRUCTURE, 2.0F, 3.0F, 3, true),
	INVERTED_SLOPE("inverted_slope", "Slopes", LegoTab.STRUCTURE, 2.0F, 3.0F, 3, true),
	/** Studless slope over the whole depth, 2 plates tall (the "cheese" slope). */
	CHEESE_SLOPE("cheese_slope", "Slopes", LegoTab.STRUCTURE, 1.5F, 2.5F, 2, false),
	/** Studless slope whose top curves up from a thin lip at local −z to full height, flat at the +z edge. */
	CURVED_SLOPE("curved_slope", "Slopes", LegoTab.STRUCTURE, 1.5F, 2.5F, 2, false),
	ROUND_BRICK("round_brick", "Round Bricks", LegoTab.STRUCTURE, 2.0F, 3.0F, 3, true),
	ROUND_PLATE("round_plate", "Round Plates", LegoTab.STRUCTURE, 1.5F, 2.5F, 1, true),
	BASEPLATE("baseplate", "Baseplates", LegoTab.STRUCTURE, 1.0F, 2.0F, 1, true),
	/** 1-plate base with an upright studded plate on one edge (studs not on top). */
	BRACKET("bracket", "Brackets", LegoTab.STRUCTURE, 1.5F, 2.5F, 1, true),
	/** Smooth 1-plate base with thin walls standing on it; studs only on the top rim of tall panels. */
	WALL_PANEL("wall_panel", "Panels", LegoTab.STRUCTURE, 1.5F, 2.5F, 3, true),
	/** Faceted studless gem on a 1-stud base; its outline comes from the catalog shape. */
	GEM("gem", "Rocks & Gems", LegoTab.STRUCTURE, 1.0F, 2.0F, 2, false),
	/** Bars and bar holders: no body, no studs, no receptors; only their catalog fittings (1 plate thick when lying). */
	BAR("bar", "Bars, Ladders and Fences", LegoTab.STRUCTURE, 0.8F, 1.5F, 1, false),
	TECHNIC_BRICK("technic_brick", "Technic Bricks", LegoTab.TECHNIC, 2.0F, 3.0F, 3, true),
	TECHNIC_PLATE("technic_plate", "Technic Plates", LegoTab.TECHNIC, 1.5F, 2.5F, 1, true),

	BEAM("beam", "Technic Beams", LegoTab.TECHNIC, 1.5F, 2.5F, Family.TECHNIC),
	PIN("pin", "Technic Connector Pegs", LegoTab.TECHNIC, 0.5F, 1.0F, Family.TECHNIC),
	AXLE_PIN("axle_pin", "Technic Connector Pegs", LegoTab.TECHNIC, 0.5F, 1.0F, Family.TECHNIC),
	AXLE("axle", "Technic Cross Axles", LegoTab.TECHNIC, 0.5F, 1.0F, Family.TECHNIC),
	BUSH("bush", "Technic Bushes", LegoTab.TECHNIC, 0.5F, 1.0F, Family.TECHNIC),
	HALF_BUSH("half_bush", "Technic Bushes", LegoTab.TECHNIC, 0.5F, 1.0F, Family.TECHNIC),
	GEAR("gear", "Technic Gears", LegoTab.TECHNIC, 1.0F, 2.0F, Family.TECHNIC),
	DOUBLE_GEAR("double_gear", "Technic Gears", LegoTab.TECHNIC, 1.0F, 2.0F, Family.TECHNIC),

	MINIFIG_HEAD("minifig_head", "Minifigure Heads", LegoTab.MINIFIG, 1.0F, 2.0F),
	MINIFIG_TORSO("minifig_torso", "Minifigure Torsos", LegoTab.MINIFIG, 1.5F, 2.5F),
	MINIFIG_LEGS("minifig_legs", "Minifigure Legs", LegoTab.MINIFIG, 1.5F, 2.5F),
	MINIFIG_STATUE("minifig_statue", "Minifigure Statues", LegoTab.MINIFIG, 2.0F, 3.0F),

	ANIMAL("animal", "Animals & Nature", LegoTab.DECOR, 1.0F, 2.0F),
	PLANT("plant", "Plants", LegoTab.DECOR, 1.0F, 2.0F),
	LEAF("leaf", "Leaves", LegoTab.DECOR, 0.8F, 1.5F),
	FLOWER("flower", "Flowers", LegoTab.DECOR, 0.8F, 1.5F),
	CHAIR("chair", "Chairs", LegoTab.DECOR, 1.5F, 2.5F),
	TABLE("table", "Tables", LegoTab.DECOR, 1.5F, 2.5F),
	WINDOW("window", "Windows", LegoTab.DECOR, 1.0F, 2.0F),
	DOOR("door", "Doors", LegoTab.DECOR, 2.0F, 3.0F),
	LAMP("lamp", "Lamps", LegoTab.DECOR, 1.5F, 2.5F),

	CABLE("cable", "Cables & Hoses", LegoTab.MISC, 1.0F, 2.0F),
	FABRIC("fabric", "Fabric & Textiles", LegoTab.MISC, 0.8F, 1.5F),
	PANEL("panel", "Design Panels", LegoTab.MISC, 1.5F, 2.5F),
	SIGN("sign", "Signs", LegoTab.MISC, 1.0F, 2.0F);

	public enum Family {
		/** Studs-and-tubes system parts on the plate grid. */
		STUDDED,
		/** Studless Technic parts on the 8 mm hole lattice. */
		TECHNIC,
		/** Simple decorative blocks. */
		DECOR
	}

	private final String id;
	private final String officialCategory;
	private final LegoTab tab;
	private final float hardness;
	private final float blastResistance;
	/** Height in plates (3 plates = 1 brick = 1 block); studded kinds only. */
	private final int heightPlates;
	private final boolean studs;
	private final Family family;

	LegoPartType(String id, String officialCategory, LegoTab tab, float hardness, float blastResistance) {
		this(id, officialCategory, tab, hardness, blastResistance, 0, false, Family.DECOR);
	}

	LegoPartType(String id, String officialCategory, LegoTab tab, float hardness, float blastResistance, Family family) {
		this(id, officialCategory, tab, hardness, blastResistance, 0, false, family);
	}

	LegoPartType(
		String id,
		String officialCategory,
		LegoTab tab,
		float hardness,
		float blastResistance,
		int heightPlates,
		boolean studs
	) {
		this(id, officialCategory, tab, hardness, blastResistance, heightPlates, studs, Family.STUDDED);
	}

	LegoPartType(
		String id,
		String officialCategory,
		LegoTab tab,
		float hardness,
		float blastResistance,
		int heightPlates,
		boolean studs,
		Family family
	) {
		this.id = id;
		this.officialCategory = officialCategory;
		this.tab = tab;
		this.hardness = hardness;
		this.blastResistance = blastResistance;
		this.heightPlates = heightPlates;
		this.studs = studs;
		this.family = family;
	}

	public Family family() {
		return family;
	}

	public boolean isStudded() {
		return family == Family.STUDDED;
	}

	public boolean isTechnicStudless() {
		return family == Family.TECHNIC;
	}

	public String getId() {
		return id;
	}

	public static LegoPartType byKindId(String kindId) {
		for (LegoPartType type : values()) {
			if (type.id.equals(kindId)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown part kind: " + kindId);
	}

	public boolean isCatalogKind() {
		return family != Family.DECOR;
	}

	public int heightPlates() {
		return heightPlates;
	}

	public boolean hasStuds() {
		return studs;
	}

	public boolean isRound() {
		return this == ROUND_BRICK || this == ROUND_PLATE || this == ROUND_TILE;
	}

	public String getOfficialCategory() {
		return officialCategory;
	}

	public LegoTab getTab() {
		return tab;
	}

	public float getHardness() {
		return hardness;
	}

	public float getBlastResistance() {
		return blastResistance;
	}

	public String blockId(LegoColor color) {
		return "lego_" + id + "_" + color.getId();
	}

	public SoundType getSoundType() {
		return switch (this) {
			case CHAIR, TABLE, DOOR -> SoundType.WOOD;
			case WINDOW -> SoundType.GLASS;
			case PLANT, LEAF, FLOWER, ANIMAL -> SoundType.GRASS;
			case FABRIC -> SoundType.WOOL;
			case CABLE -> SoundType.CHAIN;
			default -> SoundType.METAL;
		};
	}
}
