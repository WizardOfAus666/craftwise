package com.craftwise.calculator;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.CraftingResult.BreakEvenStatus;
import com.craftwise.model.CraftingResult.XpRateSource;
import com.craftwise.model.ItemPrice;
import com.craftwise.model.PlayerCraftingStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import net.runelite.api.ItemID;

/**
 * Calculates per-craft, batch, hourly, and target-level financial and XP results
 * for every {@link CraftingRecipe}.
 * Never substitutes 0 GP for missing market prices; incomplete recipes are explicitly flagged.
 */
public final class ProfitCalculator
{
	@Data
	@AllArgsConstructor
	public static class CustomPriceOverride
	{
		private final Long buyPrice;
		private final Long sellPrice;
	}

	private ProfitCalculator()
	{
	}

	public static CraftingResult evaluate(
		CraftingRecipe recipe,
		Map<Integer, ItemPrice> prices,
		PriceCalculationMode mode,
		boolean includeGeTax,
		Map<Integer, CustomPriceOverride> customPrices,
		Map<String, Double> customActionsPerHour,
		PlayerCraftingStats playerStats)
	{
		return evaluate(recipe, prices, Collections.emptyMap(), mode, includeGeTax,
			customPrices, customActionsPerHour, playerStats);
	}

	public static CraftingResult evaluate(
		CraftingRecipe recipe,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		boolean includeGeTax,
		Map<Integer, CustomPriceOverride> customPrices,
		Map<String, Double> customActionsPerHour,
		PlayerCraftingStats playerStats)
	{
		PriceCalculationMode safeMode = mode != null ? mode : PriceCalculationMode.INSTANT_BUY_SELL;
		Map<Integer, ItemPrice> safePrices = prices != null ? prices : Collections.emptyMap();

		List<Integer> missingIds = new ArrayList<>();
		List<String> missingNames = new ArrayList<>();
		boolean usingStale = false;
		boolean usingCustom = false;

		double materialCost = 0.0;
		long minInput1hVol = Long.MAX_VALUE;
		boolean hasTrackedInputVol = false;

		for (RecipeInput input : recipe.getInputs())
		{
			if (!input.isConsumed())
			{
				continue;
			}
			if (input.getItemId() == ItemID.COINS_995)
			{
				materialCost += input.getQuantity();
				continue;
			}

			ItemPrice ip = resolveItemPrice(input.getItemId(), input.getItemName(), safePrices, nameToIdIndex);
			if (ip != null && ip.isStale())
			{
				usingStale = true;
			}
			if (ip != null && ip.getTotal1hVolume() > 0L)
			{
				minInput1hVol = Math.min(minInput1hVol, ip.getTotal1hVolume());
				hasTrackedInputVol = true;
			}

			Long customBuy = getCustomBuy(customPrices, input.getItemId());
			Long resolvedBuy = resolveUnitBuyPrice(ip, safeMode, customBuy);
			if (customBuy != null && customBuy > 0L && (safeMode == PriceCalculationMode.CUSTOM || resolvedBuy.equals(customBuy)))
			{
				usingCustom = true;
			}

			if (resolvedBuy == null || resolvedBuy <= 0L)
			{
				missingIds.add(input.getItemId());
				missingNames.add(input.getItemName());
			}
			else
			{
				materialCost += input.getQuantity() * resolvedBuy;
			}
		}

		double grossRevenue = 0.0;
		double geTax = 0.0;
		double secondaryNetRevenue = 0.0;
		Long primarySellPrice = null;
		long primary1hVol = 0L;

		List<RecipeOutput> outputs = recipe.getOutputs();
		for (int i = 0; i < outputs.size(); i++)
		{
			RecipeOutput output = outputs.get(i);
			if (!output.isTradeable())
			{
				if (i == 0)
				{
					primarySellPrice = 0L;
				}
				continue;
			}

			ItemPrice ip = resolveItemPrice(output.getItemId(), output.getItemName(), safePrices, nameToIdIndex);
			if (ip != null && ip.isStale())
			{
				usingStale = true;
			}
			if (i == 0 && ip != null)
			{
				primary1hVol = ip.getTotal1hVolume();
			}

			Long customSell = getCustomSell(customPrices, output.getItemId());
			Long resolvedSell = resolveUnitSellPrice(ip, safeMode, customSell);
			if (customSell != null && customSell > 0L && (safeMode == PriceCalculationMode.CUSTOM || resolvedSell != null && resolvedSell.equals(customSell)))
			{
				usingCustom = true;
			}

			// Crushed gem is effectively ~1 gp vendor/worthless if missing on GE; don't invalidate semi-precious gem if crushed gem has no recent GE trade
			if ((resolvedSell == null || resolvedSell <= 0L) && output.getItemId() == ItemID.CRUSHED_GEM)
			{
				resolvedSell = 1L;
			}

			if (resolvedSell == null || resolvedSell <= 0L)
			{
				missingIds.add(output.getItemId());
				missingNames.add(output.getItemName());
			}
			else
			{
				double expectedQty = output.getExpectedQuantity();
				double grossForOut = expectedQty * resolvedSell;
				double taxForOut = GETransactionCalculator.calculateExpectedTax(
					resolvedSell, expectedQty, output.getItemId(), output.isTaxExempt(), includeGeTax);
				grossRevenue += grossForOut;
				geTax += taxForOut;
				if (i == 0)
				{
					primarySellPrice = resolvedSell;
				}
				else
				{
					secondaryNetRevenue += (grossForOut - taxForOut);
				}
			}
		}

		// Realistic XP rate & inventory breakdown (Phase 10)
		Double customActionOverride = (customActionsPerHour != null) ? customActionsPerHour.get(recipe.getId()) : null;
		RealisticXpRateCalculator.XpRateBreakdown xpBreakdown = RealisticXpRateCalculator.calculate(
			recipe, customActionOverride != null ? customActionOverride : 0.0);
		double actionsPerHour = xpBreakdown.getActionsPerHour();
		XpRateSource xpSource = xpBreakdown.getXpRateSource();
		double xpPerAction = xpBreakdown.getXpPerAction();
		double xpPerHour = xpBreakdown.getXpPerHour();

		// Market liquidity, spread & recency confidence (Phase 7)
		List<ItemPrice> tradedPrices = new ArrayList<>();
		ItemPrice primaryOutputPrice = null;
		for (RecipeInput input : recipe.getInputs())
		{
			if (input.isConsumed() && input.getItemId() != ItemID.COINS_995)
			{
				ItemPrice ip = resolveItemPrice(input.getItemId(), input.getItemName(), safePrices, nameToIdIndex);
				if (ip != null)
				{
					tradedPrices.add(ip);
				}
			}
		}
		for (int i = 0; i < outputs.size(); i++)
		{
			RecipeOutput output = outputs.get(i);
			if (output.isTradeable())
			{
				ItemPrice ip = resolveItemPrice(output.getItemId(), output.getItemName(), safePrices, nameToIdIndex);
				if (i == 0)
				{
					primaryOutputPrice = ip;
				}
				if (ip != null)
				{
					tradedPrices.add(ip);
				}
			}
		}
		long nowEpochSeconds = System.currentTimeMillis() / 1000L;
		MarketConfidenceEvaluator.MarketEvaluation marketEval = MarketConfidenceEvaluator.evaluate(
			missingIds.isEmpty(), tradedPrices, primaryOutputPrice, nowEpochSeconds);

		boolean complete = missingIds.isEmpty();
		if (!complete)
		{
			return CraftingResult.builder()
				.recipe(recipe)
				.priceMode(safeMode)
				.geTaxApplied(includeGeTax)
				.priceDataComplete(false)
				.usingStalePrices(usingStale)
				.usingCustomOverrides(usingCustom)
				.missingPriceItemIds(Collections.unmodifiableList(missingIds))
				.missingPriceItemNames(Collections.unmodifiableList(missingNames))
				.xpPerAction(xpPerAction)
				.actionsPerHour(actionsPerHour)
				.xpPerHour(xpPerHour)
				.xpRateSource(xpSource)
				.itemsPerInventory(xpBreakdown.getItemsPerInventory())
				.actionsPerMinute(xpBreakdown.getActionsPerMinute())
				.gpPerXp(Double.NaN)
				.breakEvenStatus(BreakEvenStatus.UNAVAILABLE)
				.primaryOutput1hVolume(primary1hVol)
				.minInput1hVolume(hasTrackedInputVol ? minInput1hVol : 0L)
				.min1hVolume(marketEval.getMin1hVolume())
				.worstSpreadPercent(marketEval.getWorstSpreadPercent())
				.oldestPriceAgeSeconds(marketEval.getOldestPriceAgeSeconds())
				.priceConfidence(marketEval.getConfidence())
				.marketWarnings(marketEval.getWarnings())
				.variableOutcome(recipe.hasVariableOutcome())
				.calculationNotice(MarketConfidenceEvaluator.GE_EXECUTION_DISCLAIMER)
				.build();
		}

		// Snap deterministic integer-quantity crafts to exact integer GP totals to eliminate float noise
		if (!recipe.hasVariableOutcome())
		{
			materialCost = Math.round(materialCost);
			grossRevenue = Math.round(grossRevenue);
			geTax = Math.round(geTax);
		}

		double netRevenue = grossRevenue - geTax;
		double profitPerCraft = netRevenue - materialCost;
		double profitPerInventory = profitPerCraft * xpBreakdown.getItemsPerInventory();
		double profitPerHour = profitPerCraft * actionsPerHour;
		double gpPerXp = xpPerAction > 0.0 ? profitPerCraft / xpPerAction : Double.NaN;

		Long breakEvenSell = null;
		BreakEvenStatus beStatus = BreakEvenStatus.UNAVAILABLE;
		if (recipe.isTradeableOutput() && recipe.getPrimaryOutput() != null)
		{
			RecipeOutput primary = recipe.getPrimaryOutput();
			breakEvenSell = BreakEvenCalculator.calculateMinSellingPrice(
				materialCost,
				primary.getExpectedQuantity(),
				secondaryNetRevenue,
				primary.getItemId(),
				primary.isTaxExempt(),
				includeGeTax
			);
			beStatus = BreakEvenCalculator.classifyStatus(profitPerCraft, materialCost);
		}
		else if (!recipe.isTradeableOutput())
		{
			beStatus = materialCost > 0.0 ? BreakEvenStatus.LOSS : BreakEvenStatus.PROFITABLE;
		}

		long actionsToTarget = 0L;
		double totalCostToTarget = 0.0;
		double totalProfitToTarget = 0.0;
		double hoursToTarget = 0.0;
		if (playerStats != null && playerStats.getXpToTarget() > 0L && xpPerAction > 0.0)
		{
			actionsToTarget = (long) Math.ceil(playerStats.getXpToTarget() / xpPerAction);
			totalCostToTarget = actionsToTarget * materialCost;
			totalProfitToTarget = actionsToTarget * profitPerCraft;
			hoursToTarget = actionsPerHour > 0.0 ? actionsToTarget / actionsPerHour : 0.0;
		}

		List<String> breakdown = buildBreakdownLines(
			recipe, safeMode, includeGeTax, materialCost, grossRevenue, geTax, netRevenue,
			profitPerCraft, profitPerInventory, breakEvenSell, primarySellPrice, xpBreakdown, marketEval);

		return CraftingResult.builder()
			.recipe(recipe)
			.priceMode(safeMode)
			.geTaxApplied(includeGeTax)
			.priceDataComplete(true)
			.usingStalePrices(usingStale)
			.usingCustomOverrides(usingCustom)
			.materialCostPerCraft(materialCost)
			.grossRevenuePerCraft(grossRevenue)
			.geTaxPerCraft(geTax)
			.netRevenuePerCraft(netRevenue)
			.profitPerCraft(profitPerCraft)
			.profit10(profitPerCraft * 10.0)
			.profit100(profitPerCraft * 100.0)
			.profit1000(profitPerCraft * 1000.0)
			.xpPerAction(xpPerAction)
			.actionsPerHour(actionsPerHour)
			.xpPerHour(xpPerHour)
			.xpRateSource(xpSource)
			.profitPerHour(profitPerHour)
			.gpPerXp(gpPerXp)
			.breakEvenSellPrice(breakEvenSell)
			.primaryOutputMarketPrice(primarySellPrice)
			.breakEvenStatus(beStatus)
			.actionsToTarget(actionsToTarget)
			.totalCostToTarget(totalCostToTarget)
			.totalProfitToTarget(totalProfitToTarget)
			.hoursToTarget(hoursToTarget)
			.primaryOutput1hVolume(primary1hVol)
			.minInput1hVolume(hasTrackedInputVol ? minInput1hVol : 0L)
			.min1hVolume(marketEval.getMin1hVolume())
			.worstSpreadPercent(marketEval.getWorstSpreadPercent())
			.oldestPriceAgeSeconds(marketEval.getOldestPriceAgeSeconds())
			.priceConfidence(marketEval.getConfidence())
			.marketWarnings(marketEval.getWarnings())
			.itemsPerInventory(xpBreakdown.getItemsPerInventory())
			.actionsPerMinute(xpBreakdown.getActionsPerMinute())
			.profitPerInventory(profitPerInventory)
			.variableOutcome(recipe.hasVariableOutcome())
			.calculationNotice(MarketConfidenceEvaluator.GE_EXECUTION_DISCLAIMER)
			.calculationBreakdown(breakdown)
			.build();
	}

	private static List<String> buildBreakdownLines(
		CraftingRecipe recipe,
		PriceCalculationMode mode,
		boolean includeGeTax,
		double materialCost,
		double grossRevenue,
		double geTax,
		double netRevenue,
		double profitPerCraft,
		double profitPerInventory,
		Long breakEvenSell,
		Long primarySellPrice,
		RealisticXpRateCalculator.XpRateBreakdown xpBreakdown,
		MarketConfidenceEvaluator.MarketEvaluation marketEval)
	{
		List<String> lines = new ArrayList<>();
		lines.add("Pricing Scenario: " + (mode != null ? mode.getLabel() : PriceCalculationMode.INSTANT_BUY_SELL.getLabel()));
		lines.add(String.format(java.util.Locale.US, "1. Consumed Materials Cost: %,.0f GP / action", materialCost));
		lines.add(String.format(java.util.Locale.US, "2. Gross GE Output Value: %,.0f GP%s",
			grossRevenue, recipe.hasVariableOutcome() ? " (expected average value)" : ""));
		lines.add(String.format(java.util.Locale.US, "3. Grand Exchange Tax: %,.0f GP (%s)",
			geTax, includeGeTax ? "2% per unit >= 50 GP, 5M cap, tools/low items exempt" : "disabled"));
		lines.add(String.format(java.util.Locale.US, "4. Net Revenue After Tax: %,.0f GP", netRevenue));
		lines.add(String.format(java.util.Locale.US, "5. Net Profit / Action: %+,.0f GP (%+,.0f GP / inventory of %d)",
			profitPerCraft, profitPerInventory, xpBreakdown.getItemsPerInventory()));
		if (breakEvenSell != null && primarySellPrice != null)
		{
			lines.add(String.format(java.util.Locale.US, "6. Primary Sell Price: %,d GP vs Exact Break-Even: %,d GP",
				primarySellPrice, breakEvenSell));
		}
		lines.add("7. Throughput Model (" + xpBreakdown.getXpRateSource().getLabel() + "): " + xpBreakdown.getModelExplanation());
		lines.add(String.format(java.util.Locale.US, "8. Market Confidence: %s (Min 1h vol: %,d, Worst spread: %.1f%%)",
			marketEval.getConfidence().getLabel(), marketEval.getMin1hVolume(), marketEval.getWorstSpreadPercent()));
		lines.add(MarketConfidenceEvaluator.GE_EXECUTION_DISCLAIMER);
		return Collections.unmodifiableList(lines);
	}

	public static ItemPrice resolveItemPrice(
		int itemId,
		String itemName,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex)
	{
		if (prices == null || prices.isEmpty())
		{
			return null;
		}
		ItemPrice direct = prices.get(itemId);
		if (direct != null && direct.hasAnyMarketPrice())
		{
			return direct;
		}
		if (itemName != null && nameToIdIndex != null && !nameToIdIndex.isEmpty())
		{
			Integer mappedId = nameToIdIndex.get(itemName.trim().toLowerCase(java.util.Locale.ROOT));
			if (mappedId != null)
			{
				ItemPrice byName = prices.get(mappedId);
				if (byName != null)
				{
					return byName;
				}
			}
		}
		return direct;
	}

	private static Long getCustomBuy(Map<Integer, CustomPriceOverride> customPrices, int itemId)
	{
		if (customPrices == null)
		{
			return null;
		}
		CustomPriceOverride ov = customPrices.get(itemId);
		return ov != null ? ov.getBuyPrice() : null;
	}

	private static Long getCustomSell(Map<Integer, CustomPriceOverride> customPrices, int itemId)
	{
		if (customPrices == null)
		{
			return null;
		}
		CustomPriceOverride ov = customPrices.get(itemId);
		return ov != null ? ov.getSellPrice() : null;
	}

	private static Long resolveUnitBuyPrice(ItemPrice ip, PriceCalculationMode mode, Long customBuy)
	{
		if (customBuy != null && customBuy > 0L)
		{
			return customBuy;
		}
		if (ip == null)
		{
			return null;
		}
		if (mode == PriceCalculationMode.CUSTOM)
		{
			// Fallback to average market price for un-overridden ingredients in Custom mode
			return ip.getAverageMarketPrice();
		}
		return ip.resolveBuyPrice(mode, null);
	}

	private static Long resolveUnitSellPrice(ItemPrice ip, PriceCalculationMode mode, Long customSell)
	{
		if (customSell != null && customSell > 0L)
		{
			return customSell;
		}
		if (ip == null)
		{
			return null;
		}
		if (mode == PriceCalculationMode.CUSTOM)
		{
			return ip.getAverageMarketPrice();
		}
		return ip.resolveSellPrice(mode, null);
	}
}
