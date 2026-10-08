package com.legominecraft.item;

import com.legominecraft.LegoMinecraftMod;
import com.legominecraft.block.LegoColor;
import com.legominecraft.block.LegoElements;
import com.legominecraft.block.LegoPartDefinition;
import com.legominecraft.block.LegoPartType;
import com.legominecraft.multipart.LegoPiecePlacer;
import com.legominecraft.multipart.TechnicPlacer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Item for one catalog design ID in one color; places a multi-cell LibMultiPart piece. */
public class LegoPartItem extends BlockItem {
	private static final Map<Integer, Map<LegoColor, LegoPartItem>> BY_DESIGN_ID = new HashMap<>();
	private static final Map<LegoPartType, List<LegoPartItem>> BY_KIND = new EnumMap<>(LegoPartType.class);

	private final LegoPartDefinition definition;
	private final LegoColor legoColor;

	public LegoPartItem(Block displayBlock, LegoPartDefinition definition, LegoColor legoColor, Properties properties) {
		super(displayBlock, properties);
		this.definition = definition;
		this.legoColor = legoColor;
	}

	public static void register(LegoPartDefinition definition, LegoColor color, Block displayBlock, Properties properties) {
		LegoPartItem item = new LegoPartItem(displayBlock, definition, color, properties);
		BY_DESIGN_ID.computeIfAbsent(definition.designId(), id -> new EnumMap<>(LegoColor.class)).put(color, item);
		BY_KIND.computeIfAbsent(definition.kind(), k -> new ArrayList<>()).add(item);
		Registry.register(BuiltInRegistries.ITEM, LegoMinecraftMod.id(definition.blockId(color)), item);
	}

	/** Falls back to white when the part doesn't come in {@code color}. */
	public static Item byDesignAndColor(int designId, LegoColor color) {
		Map<LegoColor, LegoPartItem> byColor = BY_DESIGN_ID.get(designId);
		return byColor.getOrDefault(color, byColor.get(LegoColor.WHITE));
	}

	public static Item byDefinitionAndColor(LegoPartDefinition definition, LegoColor color) {
		return byDesignAndColor(definition.designId(), color);
	}

	public static List<Item> ofKind(LegoPartType kind) {
		return Collections.unmodifiableList(BY_KIND.getOrDefault(kind, List.of()));
	}

	public LegoPartDefinition getDefinition() {
		return definition;
	}

	public LegoPartType getPartType() {
		return definition.kind();
	}

	public LegoColor getLegoColor() {
		return legoColor;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (definition.kind().isTechnicStudless()) {
			BlockHitResult hit = new BlockHitResult(
				context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside()
			);
			return TechnicPlacer.tryPlace(
				context.getLevel(), context.getPlayer(), hit, context.getItemInHand(), definition, legoColor
			);
		}
		return LegoPiecePlacer.tryPlace(context, definition, legoColor);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (definition.kind().isTechnicStudless()) {
			InteractionResult result = TechnicPlacer.tryPlace(level, player, null, stack, definition, legoColor);
			if (result.consumesAction()) {
				return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
			}
			if (result == InteractionResult.FAIL) {
				return InteractionResultHolder.fail(stack);
			}
		}
		return InteractionResultHolder.pass(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		StringBuilder ids = new StringBuilder("#").append(definition.designId());
		definition.aliases().forEach(a -> ids.append(" / #").append(a));
		tooltip.add(Component.literal(ids.toString()).withStyle(ChatFormatting.DARK_GRAY));
		List<Long> elements = LegoElements.of(definition.designId(), legoColor);
		if (!elements.isEmpty()) {
			String elementIds = String.join(", ", elements.stream().map(String::valueOf).toList());
			tooltip.add(Component.translatable("tooltip.legominecraft.element_ids", elementIds).withStyle(ChatFormatting.DARK_GRAY));
		}
		boolean rotatable = definition.kind().isStudded()
			? definition.studsX() != definition.studsZ() || definition.isShaped() || definition.kind() == LegoPartType.SLOPE
				|| definition.kind() == LegoPartType.INVERTED_SLOPE || definition.kind() == LegoPartType.CHEESE_SLOPE
				|| definition.kind() == LegoPartType.CURVED_SLOPE
				|| definition.kind() == LegoPartType.WALL_PANEL
			: definition.kind() == LegoPartType.BEAM;
		if (rotatable) {
			tooltip.add(Component.translatable("tooltip.legominecraft.rotate").withStyle(ChatFormatting.GRAY));
		}
	}
}
