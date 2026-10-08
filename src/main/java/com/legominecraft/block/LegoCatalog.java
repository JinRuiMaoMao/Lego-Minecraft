package com.legominecraft.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.legominecraft.LegoMinecraftMod;
import net.minecraft.core.Direction;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Official LEGO part catalog — add entries in {@code lego_catalog.json} one by one.
 *
 * @see <a href="https://www.lego.com/en-us/pick-and-build/pick-a-brick">Pick a Brick</a>
 */
public final class LegoCatalog {
	private static final List<LegoPartDefinition> PARTS = new ArrayList<>();
	private static final Map<Integer, LegoPartDefinition> BY_DESIGN_ID = new HashMap<>();
	private static final Map<LegoPartType, List<LegoPartDefinition>> BY_KIND = new EnumMap<>(LegoPartType.class);

	static {
		loadFromResource();
	}

	private LegoCatalog() {
	}

	/**
	 * Fails hard: running without the catalog would load every saved LEGO piece as unknown, and the
	 * world would then be saved without them.
	 */
	private static void loadFromResource() {
		try (InputStream stream = LegoCatalog.class.getClassLoader().getResourceAsStream("lego_catalog.json")) {
			if (stream == null) {
				throw new IllegalStateException("lego_catalog.json not found");
			}
			JsonArray array = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement element : array) {
				register(parseEntry(element.getAsJsonObject()));
			}
		} catch (Exception e) {
			throw new IllegalStateException("Lego Minecraft cannot start: failed to load lego_catalog.json "
				+ "(refusing to run, so saved LEGO builds are not lost)", e);
		}
		if (!BY_DESIGN_ID.containsKey(3005)) {
			throw new IllegalStateException("Lego Minecraft cannot start: lego_catalog.json has no Brick 1×1 (3005)");
		}
		LegoMinecraftMod.LOGGER.info("Loaded {} official LEGO parts from catalog", PARTS.size());
	}

	private static LegoPartDefinition parseEntry(JsonObject obj) {
		int designId = obj.get("design_id").getAsInt();
		String nameEn = obj.get("name_en").getAsString();
		String nameZh = obj.get("name_zh").getAsString();
		LegoPartType kind = LegoPartType.byKindId(obj.get("kind").getAsString());
		int studsX = obj.get("studs_x").getAsInt();
		int studsZ = obj.get("studs_z").getAsInt();
		int teeth = obj.has("teeth") ? obj.get("teeth").getAsInt() : 0;
		int height = obj.has("height") ? obj.get("height").getAsInt() : 0;
		List<double[][]> shape = new ArrayList<>();
		if (obj.has("shape")) {
			for (JsonElement polyElement : obj.getAsJsonArray("shape")) {
				JsonArray points = polyElement.getAsJsonArray();
				double[][] poly = new double[points.size()][];
				for (int i = 0; i < poly.length; i++) {
					JsonArray point = points.get(i).getAsJsonArray();
					poly[i] = new double[] {point.get(0).getAsDouble(), point.get(1).getAsDouble()};
				}
				shape.add(poly);
			}
		}
		LegoPartDefinition.Bracket bracket = null;
		if (obj.has("bracket")) {
			JsonObject b = obj.getAsJsonObject("bracket");
			int rows = b.get("height").getAsInt();
			bracket = new LegoPartDefinition.Bracket(
				b.get("width").getAsInt(),
				rows,
				b.has("x0") ? b.get("x0").getAsInt() : 0,
				b.has("inverted") && b.get("inverted").getAsBoolean(),
				b.has("plates") ? b.get("plates").getAsDouble() : rows * 3.0,
				b.has("thickness") ? b.get("thickness").getAsDouble() : 1.0,
				b.has("first_stud") ? b.get("first_stud").getAsDouble() : 1.25
			);
		}
		boolean sideStuds = false;
		List<double[]> sideStudSpots = new ArrayList<>();
		if (obj.has("side_studs")) {
			JsonElement s = obj.get("side_studs");
			if (s.isJsonArray()) {
				for (JsonElement spot : s.getAsJsonArray()) {
					sideStudSpots.add(spot.isJsonArray()
						? new double[] {spot.getAsJsonArray().get(0).getAsDouble(), spot.getAsJsonArray().get(1).getAsDouble()}
						: new double[] {Double.NaN, spot.getAsDouble()});
				}
				sideStuds = !sideStudSpots.isEmpty();
			} else {
				sideStuds = s.getAsBoolean();
			}
		}
		List<Direction> sideStudFaces = new ArrayList<>();
		if (obj.has("side_stud_faces")) {
			obj.getAsJsonArray("side_stud_faces").forEach(f -> sideStudFaces.add(Direction.byName(f.getAsString())));
		}
		List<Integer> rowBottoms = new ArrayList<>();
		if (obj.has("row_bottoms")) {
			obj.getAsJsonArray("row_bottoms").forEach(b -> rowBottoms.add(b.getAsInt()));
		}
		List<double[]> studs = new ArrayList<>();
		if (obj.has("studs")) {
			for (JsonElement s : obj.getAsJsonArray("studs")) {
				JsonArray point = s.getAsJsonArray();
				studs.add(new double[] {point.get(0).getAsDouble(), point.get(1).getAsDouble(),
					point.size() > 2 ? point.get(2).getAsDouble() : height > 0 ? height : kind.heightPlates(),
					point.size() > 3 ? Math.signum(point.get(3).getAsDouble()) : 1});
			}
		}
		List<double[]> receptors = new ArrayList<>();
		if (obj.has("receptors")) {
			for (JsonElement r : obj.getAsJsonArray("receptors")) {
				JsonArray point = r.getAsJsonArray();
				receptors.add(new double[] {point.get(0).getAsDouble(), point.get(1).getAsDouble(),
					point.size() > 2 ? point.get(2).getAsDouble() : 0});
			}
		}
		String grille = obj.has("grille") ? obj.get("grille").getAsString() : "";
		List<double[]> openStudSpots = new ArrayList<>();
		boolean openStuds = false;
		if (obj.has("open_studs")) {
			JsonElement o = obj.get("open_studs");
			if (o.isJsonArray()) {
				for (JsonElement spot : o.getAsJsonArray()) {
					openStudSpots.add(new double[] {spot.getAsJsonArray().get(0).getAsDouble(), spot.getAsJsonArray().get(1).getAsDouble()});
				}
				openStuds = !openStudSpots.isEmpty();
			} else {
				openStuds = o.getAsBoolean();
			}
		}
		List<Integer> aliases = new ArrayList<>();
		if (obj.has("aliases")) {
			obj.getAsJsonArray("aliases").forEach(a -> aliases.add(a.getAsInt()));
		}
		List<LegoColor> transColors = new ArrayList<>();
		if (obj.has("trans_colors")) {
			obj.getAsJsonArray("trans_colors").forEach(c -> transColors.add(LegoColor.fromId(c.getAsString())));
		}
		return new LegoPartDefinition(designId, nameEn, nameZh, kind, studsX, studsZ, teeth, height,
			List.copyOf(shape), bracket, sideStuds, List.copyOf(studs), grille, openStuds, List.copyOf(aliases),
			List.copyOf(transColors), parseFittings(obj, kind, studsZ),
			obj.has("profile") ? obj.get("profile").getAsString() : "", parsePanel(obj, kind),
			List.copyOf(sideStudSpots), List.copyOf(rowBottoms), List.copyOf(receptors), List.copyOf(sideStudFaces),
			List.copyOf(openStudSpots), obj.has("smooth_bottom") && obj.get("smooth_bottom").getAsBoolean());
	}

	private static LegoPartDefinition.Panel parsePanel(JsonObject obj, LegoPartType kind) {
		if (!obj.has("panel")) {
			return kind == LegoPartType.WALL_PANEL ? LegoPartDefinition.Panel.PLAIN : null;
		}
		JsonObject p = obj.getAsJsonObject("panel");
		return new LegoPartDefinition.Panel(
			p.has("wall") ? p.get("wall").getAsDouble() : LegoPartDefinition.Panel.PLAIN.wall(),
			p.has("left") && p.get("left").getAsBoolean(),
			p.has("right") && p.get("right").getAsBoolean(),
			p.has("divider") && p.get("divider").getAsBoolean(),
			p.has("rim") && p.get("rim").getAsBoolean()
		);
	}

	/** A plain bar (kind "bar" without explicit fittings) lies along local z through the middle of its column. */
	private static LegoPartDefinition.Fittings parseFittings(JsonObject obj, LegoPartType kind, int studsZ) {
		List<LegoPartDefinition.Bar> bars = new ArrayList<>();
		if (obj.has("bars")) {
			for (JsonElement e : obj.getAsJsonArray("bars")) {
				JsonObject b = e.getAsJsonObject();
				bars.add(new LegoPartDefinition.Bar(
					vector(b.getAsJsonArray("from")),
					vector(b.getAsJsonArray("to")),
					b.has("r") ? b.get("r").getAsDouble() : LegoPartDefinition.Bar.RADIUS,
					!b.has("grip") || b.get("grip").getAsBoolean(),
					b.has("sides") ? b.get("sides").getAsInt() : 0,
					b.has("hollow") && b.get("hollow").getAsBoolean()
				));
			}
		} else if (kind == LegoPartType.BAR && !obj.has("clips")) {
			bars.add(new LegoPartDefinition.Bar(new double[] {3, 1, 0}, new double[] {3, 1, studsZ * 6},
				LegoPartDefinition.Bar.RADIUS, true, 0, false));
		}
		List<LegoPartDefinition.Clip> clips = new ArrayList<>();
		if (obj.has("clips")) {
			for (JsonElement e : obj.getAsJsonArray("clips")) {
				JsonObject c = e.getAsJsonObject();
				clips.add(new LegoPartDefinition.Clip(
					vector(c.getAsJsonArray("at")),
					Direction.Axis.byName(c.get("axis").getAsString()),
					c.has("open") ? Direction.byName(c.get("open").getAsString()) : null,
					c.has("half") ? c.get("half").getAsDouble() : 1.0,
					c.has("blind") && c.get("blind").getAsBoolean(),
					c.has("outer") ? c.get("outer").getAsDouble() : 0
				));
			}
		}
		List<double[]> boxes = new ArrayList<>();
		if (obj.has("boxes")) {
			for (JsonElement e : obj.getAsJsonArray("boxes")) {
				boxes.add(vector(e.getAsJsonArray()));
			}
		}
		if (bars.isEmpty() && clips.isEmpty() && boxes.isEmpty()) {
			return LegoPartDefinition.Fittings.NONE;
		}
		return new LegoPartDefinition.Fittings(List.copyOf(bars), List.copyOf(clips), List.copyOf(boxes));
	}

	private static double[] vector(JsonArray array) {
		double[] out = new double[array.size()];
		for (int i = 0; i < out.length; i++) {
			out[i] = array.get(i).getAsDouble();
		}
		return out;
	}

	private static void register(LegoPartDefinition def) {
		if (BY_DESIGN_ID.containsKey(def.designId())) {
			LegoMinecraftMod.LOGGER.warn("Duplicate LEGO design ID {} — skipping", def.designId());
			return;
		}
		PARTS.add(def);
		BY_DESIGN_ID.put(def.designId(), def);
		BY_KIND.computeIfAbsent(def.kind(), k -> new ArrayList<>()).add(def);
	}

	public static List<LegoPartDefinition> all() {
		return Collections.unmodifiableList(PARTS);
	}

	public static List<LegoPartDefinition> ofKind(LegoPartType kind) {
		return Collections.unmodifiableList(BY_KIND.getOrDefault(kind, List.of()));
	}

	public static Optional<LegoPartDefinition> find(int designId) {
		return Optional.ofNullable(BY_DESIGN_ID.get(designId));
	}

	public static LegoPartDefinition get(int designId) {
		return find(designId).orElseThrow(() -> new IllegalArgumentException("Unknown LEGO design ID: " + designId));
	}

	/** Falls back to Brick 1×1 so saves with removed design IDs still load. */
	public static LegoPartDefinition getOrDefault(int designId) {
		return find(designId).orElseGet(() -> get(3005));
	}

	/** Legacy NBT part id → default catalog design id. */
	public static int legacyKindToDesignId(String legacyId) {
		return switch (legacyId) {
			case "plate" -> 3024;
			case "tile" -> 3070;
			case "slope" -> 3039;
			case "round_brick" -> 3062;
			case "baseplate" -> 3867;
			default -> 3005;
		};
	}
}
