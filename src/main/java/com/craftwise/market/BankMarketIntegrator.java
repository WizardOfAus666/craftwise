package com.craftwise.market;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.bank.BankCraftingCalculator;
import com.craftwise.bank.BankCraftingCalculator.BankRecipeMatch;
import com.craftwise.bank.OwnedMaterialSnapshot.SnapshotView;
import com.craftwise.calculator.ProfitCalculator.CustomPriceOverride;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.market.MarketOpportunity.OpportunityStatus;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Feature 3: Integrates live Crafting Market Watcher opportunities with Bank-Aware Crafting.
 * Surfaces recipes that are simultaneously profitable on current GE prices AND craftable
 * using the player's owned bank/inventory materials, highlighting the Best Opportunity Using Your Bank.
 */
public final class BankMarketIntegrator
{
	@Data
	@Builder
	public static class IntegratedOpportunity
	{
		private final MarketOpportunity opportunity;
		private final BankRecipeMatch bankMatch;
		private final long maxCraftableFromBank;
		private final boolean profitableWithMyBank;
		private final double immediateProfitFromBankGp;
		private final double immediateXpFromBank;
		private final long missingBuyCostGp;
		private final boolean bankDataAvailable;
	}

	@Data
	@Builder
	public static class IntegratedMarketView
	{
		private final boolean bankDataAvailable;
		@Builder.Default
		private final List<IntegratedOpportunity> items = Collections.emptyList();
		private final IntegratedOpportunity bestOpportunityUsingYourBank;
		private final IntegratedOpportunity topNewlyProfitableWithBank;
		private final int craftableCount;
		private final int profitableWithBankCount;
	}

	private BankMarketIntegrator()
	{
	}

	public static IntegratedMarketView integrate(
		List<MarketOpportunity> opportunities,
		SnapshotView snapshot,
		long targetCraftsForMissingCalc,
		boolean includeNotedFromInventory,
		int playerLevel,
		boolean membersAllowed,
		boolean includeQuestLocked,
		boolean includeUnlockRequired,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		boolean bankAvailable = snapshot != null && snapshot.isBankScanned();
		if (opportunities == null || opportunities.isEmpty())
		{
			return IntegratedMarketView.builder()
				.bankDataAvailable(bankAvailable)
				.items(Collections.emptyList())
				.craftableCount(0)
				.profitableWithBankCount(0)
				.build();
		}

		List<IntegratedOpportunity> combined = new ArrayList<>(opportunities.size());
		IntegratedOpportunity bestWithBank = null;
		IntegratedOpportunity topNewlyWithBank = null;
		int craftableCount = 0;
		int profitableWithBankCount = 0;

		for (MarketOpportunity opp : opportunities)
		{
			if (opp == null || opp.getResult() == null || opp.getResult().getRecipe() == null)
			{
				continue;
			}
			CraftingResult res = opp.getResult();
			CraftingRecipe rec = res.getRecipe();

			BankRecipeMatch match = bankAvailable
				? BankCraftingCalculator.matchRecipe(
					rec, res, snapshot, targetCraftsForMissingCalc, includeNotedFromInventory,
					prices, nameToIdIndex, mode, customPrices)
				: null;

			long maxCrafts = match != null ? match.getMaxCompleteCrafts() : 0L;
			boolean eligibleForPlayer = rec.getRequiredLevel() <= playerLevel
				&& (membersAllowed || !rec.isMembers())
				&& (includeQuestLocked || !rec.hasQuestRequirement())
				&& (includeUnlockRequired || !rec.hasUnlockRequirement());

			boolean profWithBank = bankAvailable
				&& eligibleForPlayer
				&& maxCrafts > 0L
				&& opp.isProfitable();

			double immediateProfit = match != null ? match.getTotalProfitFromOwnedStock() : 0.0;
			double immediateXp = match != null ? match.getTotalXpFromOwnedStock() : 0.0;
			long missingCost = match != null ? match.getMissingForTargetCostGp() : 0L;

			if (bankAvailable && maxCrafts > 0L && eligibleForPlayer)
			{
				craftableCount++;
			}
			if (profWithBank)
			{
				profitableWithBankCount++;
			}

			IntegratedOpportunity item = IntegratedOpportunity.builder()
				.opportunity(opp)
				.bankMatch(match)
				.maxCraftableFromBank(maxCrafts)
				.profitableWithMyBank(profWithBank)
				.immediateProfitFromBankGp(immediateProfit)
				.immediateXpFromBank(immediateXp)
				.missingBuyCostGp(missingCost)
				.bankDataAvailable(bankAvailable)
				.build();
			combined.add(item);

			if (profWithBank)
			{
				if (bestWithBank == null || compareBestBankCandidate(item, bestWithBank) < 0)
				{
					bestWithBank = item;
				}
				if (opp.getStatus() == OpportunityStatus.NEWLY_PROFITABLE)
				{
					if (topNewlyWithBank == null
						|| item.getImmediateProfitFromBankGp() > topNewlyWithBank.getImmediateProfitFromBankGp())
					{
						topNewlyWithBank = item;
					}
				}
			}
		}

		return IntegratedMarketView.builder()
			.bankDataAvailable(bankAvailable)
			.items(Collections.unmodifiableList(combined))
			.bestOpportunityUsingYourBank(bestWithBank)
			.topNewlyProfitableWithBank(topNewlyWithBank)
			.craftableCount(craftableCount)
			.profitableWithBankCount(profitableWithBankCount)
			.build();
	}

	private static int compareBestBankCandidate(IntegratedOpportunity a, IntegratedOpportunity b)
	{
		// Prioritize newly profitable / high profit when profits are strong, otherwise total immediate profit
		int cmpProfit = Double.compare(b.getImmediateProfitFromBankGp(), a.getImmediateProfitFromBankGp());
		if (cmpProfit != 0)
		{
			return cmpProfit;
		}
		int aBoost = a.getOpportunity().getStatus() == OpportunityStatus.NEWLY_PROFITABLE ? 1 : 0;
		int bBoost = b.getOpportunity().getStatus() == OpportunityStatus.NEWLY_PROFITABLE ? 1 : 0;
		if (aBoost != bBoost)
		{
			return Integer.compare(bBoost, aBoost);
		}
		return Comparator.<Double>reverseOrder().compare(a.getImmediateXpFromBank(), b.getImmediateXpFromBank());
	}

	public static List<IntegratedOpportunity> filter(
		List<IntegratedOpportunity> all,
		boolean onlyProfitableWithMyBank)
	{
		if (all == null || all.isEmpty())
		{
			return Collections.emptyList();
		}
		if (!onlyProfitableWithMyBank)
		{
			return all;
		}
		List<IntegratedOpportunity> filtered = new ArrayList<>();
		for (IntegratedOpportunity item : all)
		{
			if (item != null && item.isProfitableWithMyBank())
			{
				filtered.add(item);
			}
		}
		return Collections.unmodifiableList(filtered);
	}
}
