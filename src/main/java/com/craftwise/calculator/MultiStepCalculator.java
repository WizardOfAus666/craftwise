package com.craftwise.calculator;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.ProfitCalculator.CustomPriceOverride;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import net.runelite.api.ItemID;

/**
 * Phase 4: Multi-Step Crafting Calculator.
 * Compares buying finished/intermediate materials directly on the GE vs
 * crafting upstream Crafting stages from raw materials, with zero double-counting
 * of material costs, intermediate outputs, GE tax, or XP.
 */
public final class MultiStepCalculator
{
	@Data
	@Builder
	public static class StageSummary
	{
		private final int stageIndex;
		private final CraftingRecipe recipe;
		private final double multiplierForOneFinal;
		private final double stageXpTotal;
		private final double standaloneProfitPerAction;
	}

	@Data
	@Builder
	public static class MultiStepAnalysis
	{
		private final boolean hasMultiStepChain;
		private final CraftingRecipe finalRecipe;
		@Builder.Default
		private final List<StageSummary> stages = Collections.emptyList();

		// Strategy A: Buy immediate inputs for the final recipe directly
		private final boolean buyIntermediateAvailable;
		private final double buyIntermediateCost;
		private final double buyIntermediateNetRevenue;
		private final double buyIntermediateProfit;
		private final double buyIntermediateXp;
		private final double buyIntermediateGpPerXp;

		// Strategy B: Craft upstream Crafting stage(s) + final recipe from raw inputs
		private final boolean makeAllStepsAvailable;
		private final double makeAllRawCost;
		private final double makeAllSecondaryNetRevenue;
		private final double makeAllFinalNetRevenue;
		private final double makeAllProfit;
		private final double makeAllXp;
		private final double makeAllGpPerXp;

		private final String recommendation; // "MAKE INTERMEDIATE", "BUY INTERMEDIATE", "SINGLE STEP", "UNAVAILABLE"
		private final double profitAdvantageMakeVsBuy;
		private final double xpGainMakeVsBuy;
		private final String summaryText;
		@Builder.Default
		private final Map<Integer, Double> makeAllRawMaterialQuantities = Collections.emptyMap();
		@Builder.Default
		private final Map<Integer, Double> buyDirectMaterialQuantities = Collections.emptyMap();
	}

	private MultiStepCalculator()
	{
	}

	public static MultiStepAnalysis analyze(
		CraftingRecipe finalRecipe,
		List<CraftingRecipe> allRecipes,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		boolean includeGeTax,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		if (finalRecipe == null)
		{
			return MultiStepAnalysis.builder()
				.hasMultiStepChain(false)
				.recommendation("UNAVAILABLE")
				.summaryText("No recipe selected.")
				.build();
		}

		// Collect direct recipe buy-intermediate economics using single-step ProfitCalculator
		CraftingResult directResult = ProfitCalculator.evaluate(
			finalRecipe, prices, nameToIdIndex, mode, includeGeTax, customPrices, null, null);

		Map<Integer, Double> buyDirectQty = new LinkedHashMap<>();
		for (RecipeInput in : finalRecipe.getInputs())
		{
			if (in.isConsumed())
			{
				buyDirectQty.merge(in.getItemId(), in.getQuantity(), Double::sum);
			}
		}

		// Build upstream stage map: outputItemId -> producer CraftingRecipe
		// Prioritize explicit intermediateRecipeIds, then safe direct single-step Crafting producers
		Map<Integer, CraftingRecipe> producerByItemId = buildProducerIndex(allRecipes);
		List<StageSummary> chain = buildChainStages(finalRecipe, producerByItemId, prices, nameToIdIndex, mode, includeGeTax, customPrices);

		if (chain.size() <= 1)
		{
			return MultiStepAnalysis.builder()
				.hasMultiStepChain(false)
				.finalRecipe(finalRecipe)
				.stages(chain)
				.buyIntermediateAvailable(directResult.isPriceDataComplete())
				.buyIntermediateCost(directResult.getMaterialCostPerCraft())
				.buyIntermediateNetRevenue(directResult.getNetRevenuePerCraft())
				.buyIntermediateProfit(directResult.getProfitPerCraft())
				.buyIntermediateXp(finalRecipe.getXpPerAction())
				.buyIntermediateGpPerXp(directResult.getGpPerXp())
				.makeAllStepsAvailable(false)
				.recommendation("SINGLE STEP")
				.summaryText("Single-step method (no upstream Crafting stage applies).")
				.buyDirectMaterialQuantities(Collections.unmodifiableMap(buyDirectQty))
				.build();
		}

		// Compute full raw-to-final net cost & revenue WITHOUT double-counting intermediate items!
		// Because stages[0 .. N-2] are crafted solely to supply stages downstream, their internal
		// outputs are NOT sold on GE (only unconsumed secondary byproducts are sold), and downstream
		// recipes do NOT pay GP for self-crafted intermediate inputs.
		double makeRawCost = 0.0;
		double makeSecondaryNet = 0.0;
		double makeTotalXp = 0.0;
		boolean makeComplete = true;
		Map<Integer, Double> rawQtyMap = new LinkedHashMap<>();
		Set<Integer> internallyCraftedOutputs = new LinkedHashSet<>();

		for (int idx = 0; idx < chain.size() - 1; idx++)
		{
			RecipeOutput primaryOut = chain.get(idx).getRecipe().getPrimaryOutput();
			if (primaryOut != null)
			{
				internallyCraftedOutputs.add(primaryOut.getItemId());
			}
		}

		for (int idx = 0; idx < chain.size(); idx++)
		{
			StageSummary stage = chain.get(idx);
			CraftingRecipe r = stage.getRecipe();
			double scale = stage.getMultiplierForOneFinal();
			makeTotalXp += stage.getStageXpTotal();

			for (RecipeInput in : r.getInputs())
			{
				if (!in.isConsumed())
				{
					continue;
				}
				// Do NOT pay GE buy cost if this input is supplied by an earlier Crafting stage in the chain
				if (idx > 0 && internallyCraftedOutputs.contains(in.getItemId()))
				{
					continue;
				}
				double qty = in.getQuantity() * scale;
				rawQtyMap.merge(in.getItemId(), qty, Double::sum);
				if (in.getItemId() == ItemID.COINS_995)
				{
					makeRawCost += qty;
					continue;
				}
				ItemPrice ip = ProfitCalculator.resolveItemPrice(in.getItemId(), in.getItemName(), prices, nameToIdIndex);
				Long buy = resolveBuy(ip, mode, customPrices, in.getItemId());
				if (buy == null || buy <= 0L)
				{
					makeComplete = false;
				}
				else
				{
					makeRawCost += qty * buy;
				}
			}

			// Secondary outputs of any stage (or all non-final stage secondary byproducts) contribute net value
			List<RecipeOutput> outs = r.getOutputs();
			for (int oi = 0; oi < outs.size(); oi++)
			{
				RecipeOutput out = outs.get(oi);
				boolean isIntermediateForNext = (idx < chain.size() - 1) && oi == 0;
				if (isIntermediateForNext || !out.isTradeable())
				{
					continue;
				}
				if (idx == chain.size() - 1)
				{
					// Handled in final revenue below
					continue;
				}
				ItemPrice ip = ProfitCalculator.resolveItemPrice(out.getItemId(), out.getItemName(), prices, nameToIdIndex);
				Long sell = resolveSell(ip, mode, customPrices, out.getItemId());
				if (sell != null && sell > 0L)
				{
					double outQty = out.getExpectedQuantity() * scale;
					double tax = GETransactionCalculator.calculateExpectedTax(
						sell, outQty, out.getItemId(), out.isTaxExempt(), includeGeTax);
					makeSecondaryNet += (outQty * sell) - tax;
				}
			}
		}

		double makeFinalNetRev = directResult.isPriceDataComplete()
			? directResult.getNetRevenuePerCraft() + makeSecondaryNet
			: Double.NaN;
		if (!directResult.isPriceDataComplete())
		{
			makeComplete = false;
		}

		double makeProfit = makeComplete ? (makeFinalNetRev - makeRawCost) : Double.NaN;
		double makeGpPerXp = (makeComplete && makeTotalXp > 0.0) ? (makeProfit / makeTotalXp) : Double.NaN;
		double deltaProfit = (makeComplete && directResult.isPriceDataComplete())
			? (makeProfit - directResult.getProfitPerCraft())
			: Double.NaN;
		double xpGain = makeTotalXp - finalRecipe.getXpPerAction();

		String rec = "UNAVAILABLE";
		String summary = "Incomplete market prices for multi-step comparison.";
		if (makeComplete && directResult.isPriceDataComplete())
		{
			if (deltaProfit >= 0.0)
			{
				rec = "MAKE INTERMEDIATE";
				summary = String.format(java.util.Locale.US,
					"Making upstream stage(s) yields %+,.0f GP and +%.1f XP per final craft vs buying intermediates.",
					deltaProfit, xpGain);
			}
			else
			{
				rec = "BUY INTERMEDIATE";
				summary = String.format(java.util.Locale.US,
					"Buying intermediate(s) saves %,.0f GP per craft (skips +%.1f upstream XP).",
					Math.abs(deltaProfit), xpGain);
			}
		}

		return MultiStepAnalysis.builder()
			.hasMultiStepChain(true)
			.finalRecipe(finalRecipe)
			.stages(Collections.unmodifiableList(chain))
			.buyIntermediateAvailable(directResult.isPriceDataComplete())
			.buyIntermediateCost(directResult.getMaterialCostPerCraft())
			.buyIntermediateNetRevenue(directResult.getNetRevenuePerCraft())
			.buyIntermediateProfit(directResult.getProfitPerCraft())
			.buyIntermediateXp(finalRecipe.getXpPerAction())
			.buyIntermediateGpPerXp(directResult.getGpPerXp())
			.makeAllStepsAvailable(makeComplete)
			.makeAllRawCost(makeRawCost)
			.makeAllSecondaryNetRevenue(makeSecondaryNet)
			.makeAllFinalNetRevenue(makeFinalNetRev)
			.makeAllProfit(makeProfit)
			.makeAllXp(makeTotalXp)
			.makeAllGpPerXp(makeGpPerXp)
			.recommendation(rec)
			.profitAdvantageMakeVsBuy(deltaProfit)
			.xpGainMakeVsBuy(xpGain)
			.summaryText(summary)
			.makeAllRawMaterialQuantities(Collections.unmodifiableMap(rawQtyMap))
			.buyDirectMaterialQuantities(Collections.unmodifiableMap(buyDirectQty))
			.build();
	}

	private static Map<Integer, CraftingRecipe> buildProducerIndex(List<CraftingRecipe> allRecipes)
	{
		Map<Integer, CraftingRecipe> index = new HashMap<>();
		if (allRecipes == null)
		{
			return index;
		}
		for (CraftingRecipe r : allRecipes)
		{
			if (r == null || r.getPrimaryOutput() == null || r.hasVariableOutcome())
			{
				continue;
			}
			// Avoid choosing multi-step enchantment chains or massive batches as upstream single-item producers
			if (r.getId().endsWith("_chain"))
			{
				continue;
			}
			int outId = r.getPrimaryOutput().getItemId();
			// Prefer simpler single-output deterministic recipes (e.g. cut gems, molten glass, leather body, ball of wool)
			CraftingRecipe existing = index.get(outId);
			if (existing == null || r.getInputs().size() < existing.getInputs().size())
			{
				index.put(outId, r);
			}
		}
		return index;
	}

	private static List<StageSummary> buildChainStages(
		CraftingRecipe finalRecipe,
		Map<Integer, CraftingRecipe> producerByItemId,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		boolean includeGeTax,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		List<StageSummary> reversed = new ArrayList<>();
		Set<String> visitedIds = new LinkedHashSet<>();

		// Start with final stage
		reversed.add(StageSummary.builder()
			.stageIndex(0)
			.recipe(finalRecipe)
			.multiplierForOneFinal(1.0)
			.stageXpTotal(finalRecipe.getXpPerAction())
			.build());
		visitedIds.add(finalRecipe.getId());

		// Check explicit intermediateRecipeIds first
		if (finalRecipe.getIntermediateRecipeIds() != null && !finalRecipe.getIntermediateRecipeIds().isEmpty())
		{
			List<String> interIds = finalRecipe.getIntermediateRecipeIds();
			for (int i = interIds.size() - 1; i >= 0; i--)
			{
				CraftingRecipe inter = CraftingDataRepository.getById(interIds.get(i));
				if (inter != null && visitedIds.add(inter.getId()))
				{
					double mult = inferScaleMultiplier(inter, finalRecipe);
					CraftingResult sr = ProfitCalculator.evaluate(
						inter, prices, nameToIdIndex, mode, includeGeTax, customPrices, null, null);
					reversed.add(StageSummary.builder()
						.stageIndex(reversed.size())
						.recipe(inter)
						.multiplierForOneFinal(mult)
						.stageXpTotal(inter.getXpPerAction() * mult)
						.standaloneProfitPerAction(sr.isPriceDataComplete() ? sr.getProfitPerCraft() : Double.NaN)
						.build());
				}
			}
		}
		else
		{
			// Auto-discover up to 2 direct Crafting upstream producers (e.g. Amulet -> Amulet (u) -> Cut Gem)
			CraftingRecipe current = finalRecipe;
			double cumulativeScale = 1.0;
			for (int depth = 0; depth < 2; depth++)
			{
				CraftingRecipe upstream = null;
				double neededFromUpstream = 1.0;
				for (RecipeInput in : current.getInputs())
				{
					if (!in.isConsumed())
					{
						continue;
					}
					CraftingRecipe candidate = producerByItemId.get(in.getItemId());
					if (candidate != null && visitedIds.add(candidate.getId()))
					{
						upstream = candidate;
						double outQty = candidate.getPrimaryOutput() != null
							? Math.max(1e-6, candidate.getPrimaryOutput().getExpectedQuantity())
							: 1.0;
						neededFromUpstream = in.getQuantity() / outQty;
						break;
					}
				}
				if (upstream == null)
				{
					break;
				}
				cumulativeScale *= neededFromUpstream;
				CraftingResult sr = ProfitCalculator.evaluate(
					upstream, prices, nameToIdIndex, mode, includeGeTax, customPrices, null, null);
				reversed.add(StageSummary.builder()
					.stageIndex(reversed.size())
					.recipe(upstream)
					.multiplierForOneFinal(cumulativeScale)
					.stageXpTotal(upstream.getXpPerAction() * cumulativeScale)
					.standaloneProfitPerAction(sr.isPriceDataComplete() ? sr.getProfitPerCraft() : Double.NaN)
					.build());
				current = upstream;
			}
		}

		// Reverse so upstream raw stage comes first (0 -> N-1)
		Collections.reverse(reversed);
		List<StageSummary> ordered = new ArrayList<>(reversed.size());
		for (int i = 0; i < reversed.size(); i++)
		{
			StageSummary s = reversed.get(i);
			ordered.add(StageSummary.builder()
				.stageIndex(i + 1)
				.recipe(s.getRecipe())
				.multiplierForOneFinal(s.getMultiplierForOneFinal())
				.stageXpTotal(s.getStageXpTotal())
				.standaloneProfitPerAction(s.getStandaloneProfitPerAction())
				.build());
		}
		return ordered;
	}

	private static double inferScaleMultiplier(CraftingRecipe upstream, CraftingRecipe downstream)
	{
		if (upstream.getPrimaryOutput() == null)
		{
			return 1.0;
		}
		int producedId = upstream.getPrimaryOutput().getItemId();
		double outQty = Math.max(1e-6, upstream.getPrimaryOutput().getExpectedQuantity());
		for (RecipeInput in : downstream.getInputs())
		{
			if (in.isConsumed() && in.getItemId() == producedId)
			{
				return in.getQuantity() / outQty;
			}
		}
		return 1.0;
	}

	private static Long resolveBuy(
		ItemPrice ip,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices,
		int itemId)
	{
		if (customPrices != null)
		{
			CustomPriceOverride ov = customPrices.get(itemId);
			if (ov != null && ov.getBuyPrice() != null && ov.getBuyPrice() > 0L)
			{
				return ov.getBuyPrice();
			}
		}
		if (ip == null)
		{
			return null;
		}
		if (mode == PriceCalculationMode.CUSTOM)
		{
			return ip.getAverageMarketPrice();
		}
		return ip.resolveBuyPrice(mode, null);
	}

	private static Long resolveSell(
		ItemPrice ip,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices,
		int itemId)
	{
		if (customPrices != null)
		{
			CustomPriceOverride ov = customPrices.get(itemId);
			if (ov != null && ov.getSellPrice() != null && ov.getSellPrice() > 0L)
			{
				return ov.getSellPrice();
			}
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
