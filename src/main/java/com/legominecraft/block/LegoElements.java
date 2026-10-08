package com.legominecraft.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.legominecraft.LegoMinecraftMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Official LEGO element IDs (design + color, as printed in building instructions), newest first.
 * Generated from Rebrickable's element list by {@code scripts/make_elements.py}.
 */
public final class LegoElements {
	private static final Map<String, List<Long>> ELEMENTS = load();

	private LegoElements() {
	}

	public static List<Long> of(int designId, LegoColor color) {
		return ELEMENTS.getOrDefault(designId + "/" + color.getId(), List.of());
	}

	private static Map<String, List<Long>> load() {
		Map<String, List<Long>> out = new HashMap<>();
		try (InputStream stream = LegoElements.class.getClassLoader().getResourceAsStream("lego_elements.json")) {
			if (stream == null) {
				LegoMinecraftMod.LOGGER.warn("lego_elements.json not found — no element IDs");
				return out;
			}
			JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Map.Entry<String, JsonElement> design : root.entrySet()) {
				for (Map.Entry<String, JsonElement> color : design.getValue().getAsJsonObject().entrySet()) {
					List<Long> ids = new ArrayList<>();
					color.getValue().getAsJsonArray().forEach(e -> ids.add(e.getAsLong()));
					out.put(design.getKey() + "/" + color.getKey(), List.copyOf(ids));
				}
			}
		} catch (Exception e) {
			LegoMinecraftMod.LOGGER.error("Failed to load lego_elements.json", e);
		}
		return out;
	}
}
