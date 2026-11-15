package com.craftwise.calculator;

import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.model.CraftingResult.BreakEvenStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Exact Grand Exchange break-even calculator.
 * Accounts for integer per-item GE tax rounding, exemptions, and multi-output scaling,
 * validating every break-even price directly against {@link GETransactionCalculator}.
 */
public final class BreakEvenCalculator
{
	@Data
	@Builder
	public static class InputBreakEvenLimit
	{
		private final int itemId;
		private final String itemName;
		private final double quantityRequired;
		private final long currentUnitPrice;
		private final long maxAffordableUnitPrice;
	}

	@Data
	@Builder
	public static class MarginPresetTarget
	{
		private final String label;
		private final double marginPercent;
		private final long targetSellPrice;
		private final long taxAtTargetSellPrice;
		private final long netProfitAtTarget;
	}

	@Data
	@Builder
	public static class BreakEvenAnalysis
	{
		private final boolean available;
		private final long totalMaterialCost;
		private final long currentPrimarySellPrice;
		private final long breakEvenPrimarySellPrice;
		private final long maxTotalMaterialCost;
		private final long currentTaxPerPrimaryUnit;
		private final long breakEvenTaxPerPrimaryUnit;
		private final long currentNetProfit;
		private final double maxAcceptableSellDropPercent;
		private final double maxAcceptableCostRisePercent;
		private final BreakEvenStatus status;
		@Builder.Default
		private final List<InputBreakEvenLimit> inputLimits = Collections.emptyList();
		@Builder.Default
		private final List<MarginPresetTarget> marginTargets = Collections.emptyList();
	}

	private BreakEvenCalculator()
	{
	}

	/**
	 * Finds the minimum integer selling price per single primary output item such that
	 * {@code expectedPrimaryQty * netRevenuePerUnit(price) + secondaryNetRevenue >= materialCost}.
	 * Validated directly against {@link GETransactionCalculator#calculateNetRevenuePerItem}.
	 */
	public static Long calculateMinSellingPrice(
		double totalMaterialCost,
		double expectedPrimaryOutputQty,
		double secondaryNetRevenue,
		int primaryItemId,
		boolean primaryTaxExempt,
		boolean includeGeTax)
	{
		if (expectedPrimaryOutputQty <= 0.0 || totalMaterialCost < 0.0)
		{
			return null;
		}

		double neededPrimaryNet = Math.max(0.0, totalMaterialCost - secondaryNetRevenue);
		if (neededPrimaryNet <= 0.0)
		{
			return 1L;
		}

		double neededNetPerUnit = neededPrimaryNet / expectedPrimaryOutputQty;
		long targetNetPerUnit = (long) Math.ceil(neededNetPerUnit - 1e-9);
		if (targetNetPerUnit <= 0L)
		{
			return 1L;
		}

		if (!includeGeTax || GETransactionCalculator.isItemTaxExempt(primaryItemId, primaryTaxExempt))
		{
			return targetNetPerUnit;
		}

		// Below 50 GP there is 0 tax
		if (targetNetPerUnit < GETransactionCalculator.MIN_TAXABLE_PRICE_GP)
		{
			return targetNetPerUnit;
		}

		// Estimate upper bound for binary search (accounting for 2% tax and 5M cap)
		long low = targetNetPerUnit;
		long high = targetNetPerUnit > (245_000_000L)
			? targetNetPerUnit + GETransactionCalculator.MAX_TAX_PER_ITEM_GP + 10L
			: (long) Math.ceil(targetNetPerUnit / 0.98) + 100L;

		// Ensure high actually satisfies the condition
		while (netTotal(high, expectedPrimaryOutputQty, primaryItemId, primaryTaxExempt, includeGeTax) + 1e-9 < neededPrimaryNet)
		{
			low = high;
			high = high * 2L;
			if (high > 10_000_000_000L)
			{
				break;
			}
		}

		// Binary search for exact minimum integer selling price validated with actual tax function
		long answer = high;
		while (low <= high)
		{
			long mid = low + (high - low) / 2L;
			double net = netTotal(mid, expectedPrimaryOutputQty, primaryItemId, primaryTaxExempt, includeGeTax);
			if (net + 1e-9 >= neededPrimaryNet)
			{
				answer = mid;
				high = mid - 1L;
			}
			else
			{
				low = mid + 1L;
			}
		}
		return answer;
	}

	private static double netTotal(
		long sellPrice,
		double qty,
		int itemId,
		boolean taxExempt,
		boolean includeGeTax)
	{
		long netPerUnit = GETransactionCalculator.calculateNetRevenuePerItem(sellPrice, itemId, taxExempt, includeGeTax);
		return netPerUnit * qty;
	}

	/**
	 * Performs full break-even analysis for a recipe at specified material cost and output selling price.
	 */
	public static BreakEvenAnalysis analyze(
		CraftingRecipe recipe,
		double materialCostPerCraft,
		long customOrCurrentPrimarySellPrice,
		double secondaryNetRevenue,
		List<Long> inputUnitPrices,
		boolean includeGeTax)
	{
		if (recipe == null || recipe.getPrimaryOutput() == null || !recipe.isTradeableOutput())
		{
			return BreakEvenAnalysis.builder()
				.available(false)
				.status(BreakEvenStatus.UNAVAILABLE)
				.build();
		}

		RecipeOutput primary = recipe.getPrimaryOutput();
		double expectedQty = primary.getExpectedQuantity();
		Long breakEvenPrice = calculateMinSellingPrice(
			materialCostPerCraft,
			expectedQty,
			secondaryNetRevenue,
			primary.getItemId(),
			primary.isTaxExempt(),
			includeGeTax
		);
		if (breakEvenPrice == null)
		{
			return BreakEvenAnalysis.builder()
				.available(false)
				.status(BreakEvenStatus.UNAVAILABLE)
				.build();
		}

		long currentTaxPerUnit = GETransactionCalculator.calculateTaxPerItem(
			customOrCurrentPrimarySellPrice, primary.getItemId(), primary.isTaxExempt(), includeGeTax);
		long breakEvenTaxPerUnit = GETransactionCalculator.calculateTaxPerItem(
			breakEvenPrice, primary.getItemId(), primary.isTaxExempt(), includeGeTax);

		double netAtCurrent = (customOrCurrentPrimarySellPrice - currentTaxPerUnit) * expectedQty + secondaryNetRevenue;
		long roundedNetProfit = Math.round(netAtCurrent - materialCostPerCraft);
		long roundedMatCost = Math.round(materialCostPerCraft);
		long maxMatCost = Math.max(0L, (long) Math.floor(netAtCurrent));

		double maxSellDropPct = customOrCurrentPrimarySellPrice > 0L
			? ((customOrCurrentPrimarySellPrice - breakEvenPrice) * 100.0) / customOrCurrentPrimarySellPrice
			: 0.0;
		double maxCostRisePct = materialCostPerCraft > 0.0
			? ((netAtCurrent - materialCostPerCraft) * 100.0) / materialCostPerCraft
			: 0.0;

		BreakEvenStatus status = classifyStatus(netAtCurrent - materialCostPerCraft, materialCostPerCraft);

		List<InputBreakEvenLimit> limits = new ArrayList<>();
		List<RecipeInput> inputs = recipe.getInputs();
		if (inputs != null && inputUnitPrices != null && inputs.size() == inputUnitPrices.size())
		{
			for (int i = 0; i < inputs.size(); i++)
			{
				RecipeInput in = inputs.get(i);
				if (!in.isConsumed() || in.getItemId() == net.runelite.api.ItemID.COINS_995)
				{
					continue;
				}
				long unitPrice = inputUnitPrices.get(i);
				double otherCost = materialCostPerCraft - (in.getQuantity() * unitPrice);
				double remainingForThisInput = Math.max(0.0, netAtCurrent - otherCost);
				long maxUnit = in.getQuantity() > 0.0 ? (long) Math.floor(remainingForThisInput / in.getQuantity()) : 0L;
				limits.add(InputBreakEvenLimit.builder()
					.itemId(in.getItemId())
					.itemName(in.getItemName())
					.quantityRequired(in.getQuantity())
					.currentUnitPrice(unitPrice)
					.maxAffordableUnitPrice(maxUnit)
					.build());
			}
		}

		double[] marginPresets = new double[]{0.0, 1.0, 5.0, 10.0, 20.0};
		List<MarginPresetTarget> marginTargets = new ArrayList<>(marginPresets.length);
		for (double pct : marginPresets)
		{
			double targetCostPlusProfit = materialCostPerCraft * (1.0 + (pct / 100.0));
			Long targetSell = calculateMinSellingPrice(
				targetCostPlusProfit, expectedQty, secondaryNetRevenue,
				primary.getItemId(), primary.isTaxExempt(), includeGeTax);
			if (targetSell != null)
			{
				long taxAtTarget = GETransactionCalculator.calculateTaxPerItem(
					targetSell, primary.getItemId(), primary.isTaxExempt(), includeGeTax);
				double netAtTarget = (targetSell - taxAtTarget) * expectedQty + secondaryNetRevenue;
				marginTargets.add(MarginPresetTarget.builder()
					.label(pct == 0.0 ? "0 GP (Break-Even)" : String.format("+%.0f%% Margin", pct))
					.marginPercent(pct)
					.targetSellPrice(targetSell)
					.taxAtTargetSellPrice(taxAtTarget)
					.netProfitAtTarget(Math.round(netAtTarget - materialCostPerCraft))
					.build());
			}
		}

		return BreakEvenAnalysis.builder()
			.available(true)
			.totalMaterialCost(roundedMatCost)
			.currentPrimarySellPrice(customOrCurrentPrimarySellPrice)
			.breakEvenPrimarySellPrice(breakEvenPrice)
			.maxTotalMaterialCost(maxMatCost)
			.currentTaxPerPrimaryUnit(currentTaxPerUnit)
			.breakEvenTaxPerPrimaryUnit(breakEvenTaxPerUnit)
			.currentNetProfit(roundedNetProfit)
			.maxAcceptableSellDropPercent(maxSellDropPct)
			.maxAcceptableCostRisePercent(maxCostRisePct)
			.status(status)
			.inputLimits(Collections.unmodifiableList(limits))
			.marginTargets(Collections.unmodifiableList(marginTargets))
			.build();
	}

	public static BreakEvenStatus classifyStatus(double profitPerCraft, double materialCost)
	{
		double nearThreshold = Math.max(10.0, Math.abs(materialCost) * 0.02);
		if (Math.abs(profitPerCraft) <= nearThreshold)
		{
			return BreakEvenStatus.NEAR_BREAK_EVEN;
		}
		return profitPerCraft > 0.0 ? BreakEvenStatus.PROFITABLE : BreakEvenStatus.LOSS;
	}
}
