package com.craftwise.model;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.MarketConfidenceEvaluator.PriceConfidence;
import com.craftwise.data.CraftingRecipe;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Full financial and XP calculation result for a single {@link CraftingRecipe}
 * under a specific {@link PriceCalculationMode}.
 */
@Data
@Builder
public class CraftingResult
{
	public enum XpRateSource
	{
		VERIFIED("Verified"),
		ESTIMATED("Estimated"),
		USER_CONFIGURED("User Rate");

		private final String label;

		XpRateSource(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	public enum BreakEvenStatus
	{
		PROFITABLE("Profitable"),
		NEAR_BREAK_EVEN("Near Break-Even"),
		LOSS("Losing Money"),
		UNAVAILABLE("Unavailable");

		private final String label;

		BreakEvenStatus(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	private final CraftingRecipe recipe;
	private final PriceCalculationMode priceMode;
	private final boolean geTaxApplied;

	// Price completeness & market freshness
	private final boolean priceDataComplete;
	private final boolean usingStalePrices;
	private final boolean usingCustomOverrides;
	@Builder.Default
	private final List<Integer> missingPriceItemIds = Collections.emptyList();
	@Builder.Default
	private final List<String> missingPriceItemNames = Collections.emptyList();

	// Per-craft economics (GP)
	private final double materialCostPerCraft;
	private final double grossRevenuePerCraft;
	private final double geTaxPerCraft;
	private final double netRevenuePerCraft;
	private final double profitPerCraft;

	// Batch economics
	private final double profit10;
	private final double profit100;
	private final double profit1000;

	// XP & Hourly metrics
	private final double xpPerAction;
	private final double actionsPerHour;
	private final double xpPerHour;
	private final XpRateSource xpRateSource;
	private final double profitPerHour;
	private final double gpPerXp; // Positive = profit/xp, negative = cost/xp

	// Break-even snapshot for primary output
	private final Long breakEvenSellPrice;
	private final Long primaryOutputMarketPrice;
	private final BreakEvenStatus breakEvenStatus;

	// Target level projection (populated when player stats/target are evaluated)
	private final long actionsToTarget;
	private final double totalCostToTarget;
	private final double totalProfitToTarget;
	private final double hoursToTarget;

	// Volume / liquidity indicators from 1h market data
	private final long primaryOutput1hVolume;
	private final long minInput1hVolume;
	private final long min1hVolume;
	private final double worstSpreadPercent;
	private final long oldestPriceAgeSeconds;
	@Builder.Default
	private final PriceConfidence priceConfidence = PriceConfidence.UNAVAILABLE;
	@Builder.Default
	private final List<String> marketWarnings = Collections.emptyList();

	// Inventory & realistic rate transparency
	private final int itemsPerInventory;
	private final double actionsPerMinute;
	private final double profitPerInventory;
	private final boolean variableOutcome;
	private final String calculationNotice;
	@Builder.Default
	private final List<String> calculationBreakdown = Collections.emptyList();

	public boolean isProfitable()
	{
		return priceDataComplete && profitPerCraft > 0.0;
	}

	public boolean isLossMaking()
	{
		return priceDataComplete && profitPerCraft < 0.0;
	}
}
