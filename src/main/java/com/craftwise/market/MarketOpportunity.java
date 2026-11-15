package com.craftwise.market;

import com.craftwise.model.CraftingResult;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Snapshot evaluation of a verified Crafting recipe for the Live Market Watcher.
 */
@Data
@Builder
public class MarketOpportunity
{
	@Getter
	@RequiredArgsConstructor
	public enum OpportunityStatus
	{
		NEWLY_PROFITABLE("Newly Profitable"),
		HIGH_PROFIT("High Profit"),
		IMPROVING("Improving"),
		NEAR_BREAK_EVEN("Near Break-Even"),
		DECLINING("Declining"),
		UNPROFITABLE("Unprofitable"),
		STABLE_PROFIT("Profitable"),
		UNAVAILABLE("Unavailable");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	@RequiredArgsConstructor
	public enum PriceFreshness
	{
		FRESH("Fresh", "Updated within 5 minutes"),
		RECENT("Recent", "Updated within 30 minutes"),
		STALE("Stale", "Price data over 30 minutes old"),
		UNAVAILABLE("Unavailable", "Missing or incomplete market prices");

		private final String label;
		private final String description;

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final CraftingResult result;
	private final OpportunityStatus status;
	private final PriceFreshness freshness;
	private final long priceAgeSeconds;
	private final long checkedAtEpochSec;

	private final Double previousProfitPerCraft;
	private final double profitDeltaGp;
	private final double profitMarginPercent;
	private final Double previousMarginPercent;
	private final double marginDeltaPercent;
	private final boolean watched;

	public boolean isProfitable()
	{
		return result != null && result.isProfitable();
	}

	public boolean isImproving()
	{
		return status == OpportunityStatus.NEWLY_PROFITABLE || status == OpportunityStatus.IMPROVING;
	}
}
