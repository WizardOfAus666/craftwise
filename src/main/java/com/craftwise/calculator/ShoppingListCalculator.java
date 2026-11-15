package com.craftwise.calculator;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.ProfitCalculator.CustomPriceOverride;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.model.ItemPrice;
import com.craftwise.model.TrainingPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

/**
 * Phase 11: Material Shopping List generator & exporter.
 * Aggregates and deduplicates consumed materials across recipes, multi-step chains,
 * or multi-stage training plans, subtracts player-owned quantities, and computes
 * remaining buy quantities and exact GP costs.
 */
public final class ShoppingListCalculator
{
	@Getter
	@RequiredArgsConstructor
	public enum ShoppingSortOrder
	{
		TOTAL_COST_DESC("Highest Cost"),
		QUANTITY_DESC("Largest Quantity"),
		STAGE_ORDER("Crafting Order"),
		NAME_ASC("Name (A-Z)");

		private final String label;
	}

	@Data
	@Builder
	public static class ShoppingListItem
	{
		private final int itemId;
		private final String itemName;
		private final String stageLabel;
		private final int firstStageOrder;
		private final long totalRequiredQty;
		private final long ownedQty;
		private final long remainingToBuyQty;
		private final long unitBuyPrice;
		private final boolean priceAvailable;
		private final long totalRemainingCostGp;
		private final boolean tradeable;
	}

	@Data
	@Builder
	public static class ShoppingListSummary
	{
		@Builder.Default
		private final List<ShoppingListItem> items = Collections.emptyList();
		@Builder.Default
		private final List<String> requiredToolsAndMoulds = Collections.emptyList();
		private final long totalRemainingCostGp;
		private final long totalUniqueMaterials;
		private final boolean allPricesAvailable;
	}

	private static final class Accumulator
	{
		final int itemId;
		final String itemName;
		final String stageLabel;
		final int stageOrder;
		final boolean tradeable;
		double totalQty;

		Accumulator(int itemId, String itemName, String stageLabel, int stageOrder, boolean tradeable)
		{
			this.itemId = itemId;
			this.itemName = itemName;
			this.stageLabel = stageLabel;
			this.stageOrder = stageOrder;
			this.tradeable = tradeable;
		}
	}

	private ShoppingListCalculator()
	{
	}

	public static ShoppingListSummary fromSingleRecipe(
		CraftingRecipe recipe,
		long actions,
		Map<Integer, Long> ownedByItemId,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices,
		ShoppingSortOrder sortOrder)
	{
		if (recipe == null || actions <= 0L)
		{
			return ShoppingListSummary.builder().allPricesAvailable(true).build();
		}
		Map<Integer, Accumulator> acc = new LinkedHashMap<>();
		List<String> tools = new ArrayList<>();
		collectTools(recipe, tools);

		for (RecipeInput in : recipe.getInputs())
		{
			if (!in.isConsumed())
			{
				continue;
			}
			Accumulator existing = acc.get(in.getItemId());
			if (existing == null)
			{
				existing = new Accumulator(in.getItemId(), in.getItemName(), recipe.getName(), 1, true);
				acc.put(in.getItemId(), existing);
			}
			existing.totalQty += in.getQuantity() * actions;
		}
		return finalizeSummary(acc, tools, ownedByItemId, prices, nameToIdIndex, mode, customPrices, sortOrder);
	}

	public static ShoppingListSummary fromTrainingPlan(
		TrainingPlan plan,
		Map<Integer, Long> ownedByItemId,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices,
		ShoppingSortOrder sortOrder)
	{
		if (plan == null || plan.getStages() == null || plan.getStages().isEmpty())
		{
			return ShoppingListSummary.builder().allPricesAvailable(true).build();
		}
		Map<Integer, Accumulator> acc = new LinkedHashMap<>();
		List<String> tools = new ArrayList<>();
		int stageIdx = 1;
		for (TrainingPlan.TrainingStage step : plan.getStages())
		{
			CraftingRecipe r = step.getRecipe();
			if (r == null || step.getActionsNeeded() <= 0L)
			{
				continue;
			}
			collectTools(r, tools);
			String stageLbl = "Lvl " + step.getFromLevel() + "-" + step.getToLevel() + " (" + r.getName() + ")";
			for (RecipeInput in : r.getInputs())
			{
				if (!in.isConsumed())
				{
					continue;
				}
				Accumulator existing = acc.get(in.getItemId());
				if (existing == null)
				{
					existing = new Accumulator(in.getItemId(), in.getItemName(), stageLbl, stageIdx, true);
					acc.put(in.getItemId(), existing);
				}
				existing.totalQty += in.getQuantity() * step.getActionsNeeded();
			}
			stageIdx++;
		}
		return finalizeSummary(acc, tools, ownedByItemId, prices, nameToIdIndex, mode, customPrices, sortOrder);
	}

	private static void collectTools(CraftingRecipe recipe, List<String> tools)
	{
		for (String t : recipe.getAllToolsAndMoulds())
		{
			if (t != null && !t.trim().isEmpty() && !tools.contains(t.trim()))
			{
				tools.add(t.trim());
			}
		}
	}

	private static ShoppingListSummary finalizeSummary(
		Map<Integer, Accumulator> accMap,
		List<String> tools,
		Map<Integer, Long> ownedByItemId,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices,
		ShoppingSortOrder sortOrder)
	{
		List<ShoppingListItem> items = new ArrayList<>(accMap.size());
		long totalCost = 0L;
		boolean allAvail = true;

		for (Accumulator a : accMap.values())
		{
			long required = Math.max(0L, (long) Math.ceil(a.totalQty - 1e-9));
			long owned = ownedByItemId != null ? Math.max(0L, ownedByItemId.getOrDefault(a.itemId, 0L)) : 0L;
			long remaining = Math.max(0L, required - owned);

			long unitPrice = 0L;
			boolean priceAvail = true;
			if (a.itemId == ItemID.COINS_995)
			{
				unitPrice = 1L;
			}
			else if (!a.tradeable)
			{
				unitPrice = 0L;
			}
			else
			{
				ItemPrice ip = ProfitCalculator.resolveItemPrice(a.itemId, a.itemName, prices, nameToIdIndex);
				Long buy = null;
				if (customPrices != null && customPrices.containsKey(a.itemId))
				{
					CustomPriceOverride ov = customPrices.get(a.itemId);
					if (ov != null && ov.getBuyPrice() != null && ov.getBuyPrice() > 0L)
					{
						buy = ov.getBuyPrice();
					}
				}
				if (buy == null && ip != null)
				{
					buy = mode == PriceCalculationMode.CUSTOM
						? ip.getAverageMarketPrice()
						: ip.resolveBuyPrice(mode, null);
				}
				if (buy == null || buy <= 0L)
				{
					priceAvail = false;
					allAvail = false;
				}
				else
				{
					unitPrice = buy;
				}
			}

			long rowCost = priceAvail ? remaining * unitPrice : 0L;
			totalCost += rowCost;

			items.add(ShoppingListItem.builder()
				.itemId(a.itemId)
				.itemName(a.itemName)
				.stageLabel(a.stageLabel)
				.firstStageOrder(a.stageOrder)
				.totalRequiredQty(required)
				.ownedQty(owned)
				.remainingToBuyQty(remaining)
				.unitBuyPrice(unitPrice)
				.priceAvailable(priceAvail)
				.totalRemainingCostGp(rowCost)
				.tradeable(a.tradeable)
				.build());
		}

		ShoppingSortOrder effectiveSort = sortOrder != null ? sortOrder : ShoppingSortOrder.TOTAL_COST_DESC;
		switch (effectiveSort)
		{
			case QUANTITY_DESC:
				items.sort(Comparator.comparingLong(ShoppingListItem::getRemainingToBuyQty).reversed());
				break;
			case STAGE_ORDER:
				items.sort(Comparator.comparingInt(ShoppingListItem::getFirstStageOrder));
				break;
			case NAME_ASC:
				items.sort(Comparator.comparing(ShoppingListItem::getItemName, String.CASE_INSENSITIVE_ORDER));
				break;
			case TOTAL_COST_DESC:
			default:
				items.sort(Comparator.comparingLong(ShoppingListItem::getTotalRemainingCostGp).reversed());
				break;
		}

		return ShoppingListSummary.builder()
			.items(Collections.unmodifiableList(items))
			.requiredToolsAndMoulds(Collections.unmodifiableList(tools))
			.totalRemainingCostGp(totalCost)
			.totalUniqueMaterials(items.size())
			.allPricesAvailable(allAvail)
			.build();
	}

	public static String formatPlainText(ShoppingListSummary summary, String title)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("=== CraftWise Shopping List");
		if (title != null && !title.trim().isEmpty())
		{
			sb.append(": ").append(title.trim());
		}
		sb.append(" ===\n");
		if (!summary.getRequiredToolsAndMoulds().isEmpty())
		{
			sb.append("Required Tools/Moulds (not consumed): ")
				.append(String.join(", ", summary.getRequiredToolsAndMoulds()))
				.append("\n");
		}
		for (ShoppingListItem item : summary.getItems())
		{
			sb.append(String.format(Locale.US,
				"- %-26s Need: %,8d | Owned: %,7d | Buy: %,8d | Unit: %s GP | Total: %,d GP%n",
				item.getItemName(),
				item.getTotalRequiredQty(),
				item.getOwnedQty(),
				item.getRemainingToBuyQty(),
				item.isPriceAvailable() ? String.format(Locale.US, "%,d", item.getUnitBuyPrice()) : "N/A",
				item.getTotalRemainingCostGp()));
		}
		sb.append(String.format(Locale.US, "Total Estimated Remaining Cost: %,d GP%s%n",
			summary.getTotalRemainingCostGp(),
			summary.isAllPricesAvailable() ? "" : " (incomplete market prices)"));
		return sb.toString();
	}
}
