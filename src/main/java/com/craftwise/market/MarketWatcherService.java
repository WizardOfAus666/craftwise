package com.craftwise.market;

import com.craftwise.CraftWiseConfig;
import com.craftwise.data.CraftingCategory;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import com.craftwise.market.MarketOpportunity.OpportunityStatus;
import com.craftwise.market.MarketOpportunity.PriceFreshness;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.CraftingResult.BreakEvenStatus;
import com.craftwise.model.ItemPrice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Stateful Market Watcher scanner (Feature 1).
 * Continuously compares live market evaluations for verified Crafting recipes against previous
 * observations, classifies profit opportunities, filters noise/rounding jitter, records genuine
 * historical profitability snapshots for watched/scanned methods, and generates deduplicated alerts.
 */
public class MarketWatcherService
{
	@Getter
	@RequiredArgsConstructor
	public enum ScannerFilterMode
	{
		ALL("All Verified"),
		PROFITABLE_ONLY("Profitable Only"),
		NEWLY_PROFITABLE("Newly Profitable"),
		HIGH_PROFIT("High Profit"),
		IMPROVING("Improving"),
		NEAR_BREAK_EVEN("Near Break-Even"),
		DECLINING("Declining"),
		UNPROFITABLE("Unprofitable"),
		WATCHED_ONLY("Watchlist Only"),
		PROFITABLE_WITH_MY_BANK("Profitable With My Bank");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	@RequiredArgsConstructor
	public enum ScannerSortMode
	{
		PROFIT_PER_ACTION("Profit / Action"),
		PROFIT_PER_HOUR("Profit / Hour"),
		PROFIT_MARGIN("Profit Margin %"),
		GP_PER_XP("GP / XP"),
		PRICE_IMPROVEMENT("Recent Improvement"),
		REQUIRED_LEVEL("Crafting Level");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Data
	@Builder
	public static class ScannerFilterCriteria
	{
		@Builder.Default
		private final ScannerFilterMode mode = ScannerFilterMode.ALL;
		private final CraftingCategory category;
		private final String searchQuery;
		@Builder.Default
		private final double minProfitGp = -Double.MAX_VALUE;
		@Builder.Default
		private final double minMarginPercent = -Double.MAX_VALUE;
		@Builder.Default
		private final double maxMaterialCostGp = Double.MAX_VALUE;
		@Builder.Default
		private final int minLevel = 1;
		@Builder.Default
		private final int maxLevel = 99;
		@Builder.Default
		private final double minXpPerAction = 0.0;
		@Builder.Default
		private final double minGpPerXp = -Double.MAX_VALUE;
	}

	@Data
	@Builder
	public static class AlertRuleConfig
	{
		@Builder.Default
		private final boolean enabled = true;
		@Builder.Default
		private final boolean alertOnNewlyProfitable = true;
		@Builder.Default
		private final boolean alertOnBreakEven = true;
		@Builder.Default
		private final boolean alertOnWatchedLoss = true;
		@Builder.Default
		private final long minProfitGp = 500L;
		@Builder.Default
		private final long minInventoryProfitGp = 5000L;
		@Builder.Default
		private final double minImprovementPercent = 10.0;
		@Builder.Default
		private final long cooldownSeconds = 900L;
		@Builder.Default
		private final long highProfitThresholdGp = 250L;

		public static AlertRuleConfig fromConfig(CraftWiseConfig cfg)
		{
			if (cfg == null)
			{
				return AlertRuleConfig.builder().build();
			}
			return AlertRuleConfig.builder()
				.enabled(cfg.enableMarketAlerts())
				.alertOnNewlyProfitable(cfg.alertOnNewlyProfitable())
				.alertOnBreakEven(cfg.alertOnBreakEven())
				.alertOnWatchedLoss(cfg.alertOnWatchedLoss())
				.minProfitGp(cfg.alertMinProfitGp())
				.minInventoryProfitGp(cfg.alertMinInventoryProfitGp())
				.minImprovementPercent(cfg.alertMinImprovementPercent())
				.cooldownSeconds(cfg.alertCooldownMinutes() * 60L)
				.highProfitThresholdGp(cfg.highProfitThresholdGp())
				.build();
		}
	}

	@Data
	@Builder
	public static class AlertEvent
	{
		private final String recipeId;
		private final String recipeName;
		private final String alertType;
		private final String message;
		private final long profitPerCraft;
		private final long profitPerInventory;
		private final long triggeredAtEpochSec;
	}

	@Data
	@Builder
	public static class ScanResult
	{
		private final long scannedAtEpochSec;
		@Builder.Default
		private final List<MarketOpportunity> opportunities = Collections.emptyList();
		@Builder.Default
		private final List<AlertEvent> triggeredAlerts = Collections.emptyList();
	}

	@Data
	private static final class PriorSnapshot
	{
		final double profitPerCraft;
		final double marginPercent;
		final BreakEvenStatus breakEvenStatus;
		final long timestampSec;
	}

	private final WatchlistManager watchlistManager;
	private final Map<String, PriorSnapshot> previousByRecipeId = new HashMap<>();
	private final Map<String, Boolean> activeAlertState = new HashMap<>();
	private final Map<String, Long> lastAlertSentEpoch = new HashMap<>();
	private volatile ScanResult latestScan = ScanResult.builder().build();

	public MarketWatcherService(WatchlistManager watchlistManager)
	{
		this.watchlistManager = watchlistManager;
	}

	public synchronized ScanResult scan(
		List<CraftingResult> results,
		Map<Integer, ItemPrice> currentPrices,
		AlertRuleConfig alertRules,
		long nowEpochSec)
	{
		if (results == null || results.isEmpty())
		{
			latestScan = ScanResult.builder().scannedAtEpochSec(nowEpochSec).build();
			return latestScan;
		}

		AlertRuleConfig rules = alertRules != null ? alertRules : AlertRuleConfig.builder().build();
		List<MarketOpportunity> opportunities = new ArrayList<>();
		List<AlertEvent> alerts = new ArrayList<>();

		// Update watched individual items
		if (currentPrices != null && watchlistManager != null)
		{
			for (WatchlistManager.WatchedItemRecord wi : watchlistManager.getWatchedItems())
			{
				ItemPrice ip = currentPrices.get(wi.getItemId());
				if (ip != null)
				{
					watchlistManager.updateWatchedItemPrice(wi.getItemId(), ip.getHighPrice(), ip.getLowPrice(), nowEpochSec);
				}
			}
		}

		boolean anyWatchedHistoryAdded = false;
		for (CraftingResult r : results)
		{
			if (r == null || r.getRecipe() == null)
			{
				continue;
			}
			CraftingRecipe recipe = r.getRecipe();
			// Never present unverified / unsupported mechanics as reliable scanner opportunities
			if (recipe.getVerificationStatus() != VerificationStatus.VERIFIED)
			{
				continue;
			}

			boolean watched = watchlistManager != null && watchlistManager.isRecipeWatched(recipe.getId());
			PriceFreshness freshness = classifyFreshness(r, nowEpochSec);
			double marginPct = computeMarginPercent(r);
			PriorSnapshot prior = previousByRecipeId.get(recipe.getId());

			Double prevProfit = prior != null ? prior.profitPerCraft : null;
			Double prevMargin = prior != null ? prior.marginPercent : null;
			double profitDelta = prevProfit != null ? (r.getProfitPerCraft() - prevProfit) : 0.0;
			double marginDelta = prevMargin != null ? (marginPct - prevMargin) : 0.0;

			OpportunityStatus status = classifyOpportunity(r, freshness, prior, profitDelta, marginDelta, rules);

			MarketOpportunity opp = MarketOpportunity.builder()
				.result(r)
				.status(status)
				.freshness(freshness)
				.priceAgeSeconds(r.getOldestPriceAgeSeconds())
				.checkedAtEpochSec(nowEpochSec)
				.previousProfitPerCraft(prevProfit)
				.profitDeltaGp(profitDelta)
				.profitMarginPercent(marginPct)
				.previousMarginPercent(prevMargin)
				.marginDeltaPercent(marginDelta)
				.watched(watched)
				.build();
			opportunities.add(opp);

			if (r.isPriceDataComplete())
			{
				if (watchlistManager != null && watched)
				{
					long matGp = Math.round(r.getMaterialCostPerCraft());
					long outGp = Math.round(r.getGrossRevenuePerCraft());
					long profGp = Math.round(r.getProfitPerCraft());
					long beGp = r.getBreakEvenSellPrice() != null ? r.getBreakEvenSellPrice() : 0L;
					watchlistManager.recordProfitSnapshot(recipe.getId(), nowEpochSec, matGp, outGp, profGp, beGp, false);
					anyWatchedHistoryAdded = true;
				}

				if (rules.isEnabled())
				{
					evaluateAlerts(r, opp, prior, rules, nowEpochSec, alerts);
				}

				previousByRecipeId.put(recipe.getId(), new PriorSnapshot(
					r.getProfitPerCraft(), marginPct, r.getBreakEvenStatus(), nowEpochSec));
			}
		}

		if (anyWatchedHistoryAdded && watchlistManager != null)
		{
			watchlistManager.saveHistory();
		}

		latestScan = ScanResult.builder()
			.scannedAtEpochSec(nowEpochSec)
			.opportunities(Collections.unmodifiableList(opportunities))
			.triggeredAlerts(Collections.unmodifiableList(alerts))
			.build();
		return latestScan;
	}

	private void evaluateAlerts(
		CraftingResult r,
		MarketOpportunity opp,
		PriorSnapshot prior,
		AlertRuleConfig rules,
		long nowEpochSec,
		List<AlertEvent> outAlerts)
	{
		String recipeId = r.getRecipe().getId();
		String name = r.getRecipe().getName();
		long profitCraft = Math.round(r.getProfitPerCraft());
		long profitInv = Math.round(r.getProfitPerInventory());

		// 1. Newly profitable transition (requires prior observation <= 0)
		boolean isNewlyProfit = prior != null && prior.profitPerCraft <= 0.0 && r.getProfitPerCraft() > 0.0
			&& !r.isUsingStalePrices();
		if (rules.isAlertOnNewlyProfitable())
		{
			tryTriggerAlert(recipeId, "NEWLY_PROFITABLE", isNewlyProfit, nowEpochSec, rules.getCooldownSeconds(),
				AlertEvent.builder()
					.recipeId(recipeId)
					.recipeName(name)
					.alertType("NEWLY_PROFITABLE")
					.message(String.format(Locale.US, "%s turned profitable (%+,d GP/craft). GE fill not guaranteed.", name, profitCraft))
					.profitPerCraft(profitCraft)
					.profitPerInventory(profitInv)
					.triggeredAtEpochSec(nowEpochSec)
					.build(),
				outAlerts);
		}

		// 2. Profit per action exceeded threshold
		boolean exceedsCraftThreshold = r.getProfitPerCraft() >= rules.getMinProfitGp() && rules.getMinProfitGp() > 0L
			&& opp.getFreshness() != PriceFreshness.UNAVAILABLE;
		tryTriggerAlert(recipeId, "PROFIT_THRESHOLD", exceedsCraftThreshold, nowEpochSec, rules.getCooldownSeconds(),
			AlertEvent.builder()
				.recipeId(recipeId)
				.recipeName(name)
				.alertType("PROFIT_THRESHOLD")
				.message(String.format(Locale.US, "%s profit %,d GP/craft (threshold: %,d GP). Verify GE volume.",
					name, profitCraft, rules.getMinProfitGp()))
				.profitPerCraft(profitCraft)
				.profitPerInventory(profitInv)
				.triggeredAtEpochSec(nowEpochSec)
				.build(),
			outAlerts);

		// 3. Profit per inventory exceeded threshold
		boolean exceedsInvThreshold = r.getProfitPerInventory() >= rules.getMinInventoryProfitGp()
			&& rules.getMinInventoryProfitGp() > 0L
			&& opp.getFreshness() != PriceFreshness.UNAVAILABLE;
		tryTriggerAlert(recipeId, "INVENTORY_THRESHOLD", exceedsInvThreshold, nowEpochSec, rules.getCooldownSeconds(),
			AlertEvent.builder()
				.recipeId(recipeId)
				.recipeName(name)
				.alertType("INVENTORY_THRESHOLD")
				.message(String.format(Locale.US, "%s hits %,d GP/inventory (threshold: %,d GP).",
					name, profitInv, rules.getMinInventoryProfitGp()))
				.profitPerCraft(profitCraft)
				.profitPerInventory(profitInv)
				.triggeredAtEpochSec(nowEpochSec)
				.build(),
			outAlerts);

		// 4. Reached near break-even from loss
		boolean reachedBreakEven = prior != null
			&& prior.breakEvenStatus == BreakEvenStatus.LOSS
			&& r.getBreakEvenStatus() == BreakEvenStatus.NEAR_BREAK_EVEN;
		if (rules.isAlertOnBreakEven())
		{
			tryTriggerAlert(recipeId, "BREAK_EVEN_REACHED", reachedBreakEven, nowEpochSec, rules.getCooldownSeconds(),
				AlertEvent.builder()
					.recipeId(recipeId)
					.recipeName(name)
					.alertType("BREAK_EVEN_REACHED")
					.message(String.format(Locale.US, "%s reached near break-even (%+,d GP/craft).", name, profitCraft))
					.profitPerCraft(profitCraft)
					.profitPerInventory(profitInv)
					.triggeredAtEpochSec(nowEpochSec)
					.build(),
				outAlerts);
		}

		// 5. Margin improvement >= X%
		boolean improvedMargin = prior != null && opp.getMarginDeltaPercent() >= rules.getMinImprovementPercent()
			&& Math.abs(opp.getProfitDeltaGp()) >= noiseFloorGp(r.getMaterialCostPerCraft());
		tryTriggerAlert(recipeId, "MARGIN_IMPROVED", improvedMargin, nowEpochSec, rules.getCooldownSeconds(),
			AlertEvent.builder()
				.recipeId(recipeId)
				.recipeName(name)
				.alertType("MARGIN_IMPROVED")
				.message(String.format(Locale.US, "%s margin improved by +%.1f%% (%+,d GP/craft).",
					name, opp.getMarginDeltaPercent(), profitCraft))
				.profitPerCraft(profitCraft)
				.profitPerInventory(profitInv)
				.triggeredAtEpochSec(nowEpochSec)
				.build(),
			outAlerts);

		// 6. Watched method dropped to unprofitable
		if (rules.isAlertOnWatchedLoss() && opp.isWatched())
		{
			boolean turnedLoss = prior != null && prior.profitPerCraft >= 0.0 && r.getProfitPerCraft() < 0.0;
			tryTriggerAlert(recipeId, "WATCHED_LOSS", turnedLoss, nowEpochSec, rules.getCooldownSeconds(),
				AlertEvent.builder()
					.recipeId(recipeId)
					.recipeName(name)
					.alertType("WATCHED_LOSS")
					.message(String.format(Locale.US, "Watched %s became unprofitable (%+,d GP/craft).", name, profitCraft))
					.profitPerCraft(profitCraft)
					.profitPerInventory(profitInv)
					.triggeredAtEpochSec(nowEpochSec)
					.build(),
				outAlerts);
		}
	}

	private void tryTriggerAlert(
		String recipeId,
		String alertType,
		boolean conditionActive,
		long nowEpochSec,
		long cooldownSec,
		AlertEvent event,
		List<AlertEvent> outAlerts)
	{
		String key = recipeId + "::" + alertType;
		boolean wasActive = Boolean.TRUE.equals(activeAlertState.get(key));
		if (!conditionActive)
		{
			activeAlertState.put(key, false);
			return;
		}
		if (wasActive)
		{
			// Already alerted while this condition stayed true -> suppress duplicate!
			return;
		}
		Long lastSent = lastAlertSentEpoch.get(key);
		if (lastSent != null && (nowEpochSec - lastSent) < cooldownSec)
		{
			return;
		}
		activeAlertState.put(key, true);
		lastAlertSentEpoch.put(key, nowEpochSec);
		outAlerts.add(event);
	}

	private static OpportunityStatus classifyOpportunity(
		CraftingResult r,
		PriceFreshness freshness,
		PriorSnapshot prior,
		double profitDelta,
		double marginDelta,
		AlertRuleConfig rules)
	{
		if (freshness == PriceFreshness.UNAVAILABLE || !r.isPriceDataComplete())
		{
			return OpportunityStatus.UNAVAILABLE;
		}
		double profit = r.getProfitPerCraft();
		double noiseGp = noiseFloorGp(r.getMaterialCostPerCraft());

		if (prior != null && prior.profitPerCraft <= 0.0 && profit > 0.0
			&& r.getBreakEvenStatus() != BreakEvenStatus.NEAR_BREAK_EVEN)
		{
			return OpportunityStatus.NEWLY_PROFITABLE;
		}
		if (r.getBreakEvenStatus() == BreakEvenStatus.NEAR_BREAK_EVEN)
		{
			return OpportunityStatus.NEAR_BREAK_EVEN;
		}
		if (prior != null && profitDelta > noiseGp && marginDelta >= Math.max(2.0, rules.getMinImprovementPercent() * 0.5))
		{
			return OpportunityStatus.IMPROVING;
		}
		if (prior != null && profitDelta < -noiseGp && marginDelta <= -5.0)
		{
			return OpportunityStatus.DECLINING;
		}
		if (profit >= rules.getHighProfitThresholdGp())
		{
			return OpportunityStatus.HIGH_PROFIT;
		}
		if (profit > 0.0)
		{
			return OpportunityStatus.STABLE_PROFIT;
		}
		return OpportunityStatus.UNPROFITABLE;
	}

	private static double noiseFloorGp(double materialCost)
	{
		return Math.max(5.0, Math.abs(materialCost) * 0.005);
	}

	public static PriceFreshness classifyFreshness(CraftingResult r, long nowEpochSec)
	{
		if (r == null || !r.isPriceDataComplete())
		{
			return PriceFreshness.UNAVAILABLE;
		}
		long ageSec = r.getOldestPriceAgeSeconds();
		if (r.isUsingStalePrices() || ageSec > 1800L)
		{
			return PriceFreshness.STALE;
		}
		if (ageSec <= 300L)
		{
			return PriceFreshness.FRESH;
		}
		return PriceFreshness.RECENT;
	}

	public static double computeMarginPercent(CraftingResult r)
	{
		if (r == null || !r.isPriceDataComplete())
		{
			return 0.0;
		}
		double cost = r.getMaterialCostPerCraft();
		if (cost <= 0.0)
		{
			return r.getProfitPerCraft() > 0.0 ? 100.0 : 0.0;
		}
		return (r.getProfitPerCraft() * 100.0) / cost;
	}

	public static List<MarketOpportunity> filterAndSort(
		List<MarketOpportunity> source,
		ScannerFilterCriteria criteria,
		ScannerSortMode sortMode)
	{
		if (source == null || source.isEmpty())
		{
			return Collections.emptyList();
		}
		ScannerFilterCriteria c = criteria != null ? criteria : ScannerFilterCriteria.builder().build();
		String q = c.getSearchQuery() != null ? c.getSearchQuery().trim().toLowerCase(Locale.ROOT) : "";

		List<MarketOpportunity> filtered = new ArrayList<>();
		for (MarketOpportunity opp : source)
		{
			CraftingResult r = opp.getResult();
			if (r == null || r.getRecipe() == null)
			{
				continue;
			}
			CraftingRecipe rec = r.getRecipe();
			if (c.getCategory() != null && rec.getCategory() != c.getCategory())
			{
				continue;
			}
			if (rec.getRequiredLevel() < c.getMinLevel() || rec.getRequiredLevel() > c.getMaxLevel())
			{
				continue;
			}
			if (rec.getXpPerAction() < c.getMinXpPerAction())
			{
				continue;
			}
			if (!q.isEmpty() && !rec.getName().toLowerCase(Locale.ROOT).contains(q))
			{
				continue;
			}
			if (!matchesMode(opp, c.getMode()))
			{
				continue;
			}
			if (r.isPriceDataComplete())
			{
				if (r.getProfitPerCraft() < c.getMinProfitGp())
				{
					continue;
				}
				if (opp.getProfitMarginPercent() < c.getMinMarginPercent())
				{
					continue;
				}
				if (r.getMaterialCostPerCraft() > c.getMaxMaterialCostGp())
				{
					continue;
				}
				double gpXp = Double.isNaN(r.getGpPerXp()) ? -Double.MAX_VALUE : r.getGpPerXp();
				if (gpXp < c.getMinGpPerXp())
				{
					continue;
				}
			}
			else if (c.getMode() != ScannerFilterMode.ALL && c.getMode() != ScannerFilterMode.WATCHED_ONLY)
			{
				continue;
			}
			filtered.add(opp);
		}

		ScannerSortMode effectiveSort = sortMode != null ? sortMode : ScannerSortMode.PROFIT_PER_HOUR;
		Comparator<MarketOpportunity> comp;
		switch (effectiveSort)
		{
			case PROFIT_PER_ACTION:
				comp = Comparator.comparingDouble(o -> o.getResult().isPriceDataComplete() ? o.getResult().getProfitPerCraft() : -1e12);
				filtered.sort(comp.reversed());
				break;
			case PROFIT_MARGIN:
				comp = Comparator.comparingDouble(o -> o.getResult().isPriceDataComplete() ? o.getProfitMarginPercent() : -1e12);
				filtered.sort(comp.reversed());
				break;
			case GP_PER_XP:
				comp = Comparator.comparingDouble(o -> o.getResult().isPriceDataComplete() && !Double.isNaN(o.getResult().getGpPerXp())
					? o.getResult().getGpPerXp() : -1e12);
				filtered.sort(comp.reversed());
				break;
			case PRICE_IMPROVEMENT:
				comp = Comparator.comparingDouble(MarketOpportunity::getProfitDeltaGp);
				filtered.sort(comp.reversed());
				break;
			case REQUIRED_LEVEL:
				filtered.sort(Comparator.comparingInt(o -> o.getResult().getRecipe().getRequiredLevel()));
				break;
			case PROFIT_PER_HOUR:
			default:
				comp = Comparator.comparingDouble(o -> o.getResult().isPriceDataComplete() ? o.getResult().getProfitPerHour() : -1e12);
				filtered.sort(comp.reversed());
				break;
		}
		return Collections.unmodifiableList(filtered);
	}

	private static boolean matchesMode(MarketOpportunity opp, ScannerFilterMode mode)
	{
		if (mode == null || mode == ScannerFilterMode.ALL)
		{
			return true;
		}
		switch (mode)
		{
			case PROFITABLE_ONLY:
				return opp.isProfitable();
			case NEWLY_PROFITABLE:
				return opp.getStatus() == OpportunityStatus.NEWLY_PROFITABLE;
			case HIGH_PROFIT:
				return opp.getStatus() == OpportunityStatus.HIGH_PROFIT;
			case IMPROVING:
				return opp.isImproving();
			case NEAR_BREAK_EVEN:
				return opp.getStatus() == OpportunityStatus.NEAR_BREAK_EVEN;
			case DECLINING:
				return opp.getStatus() == OpportunityStatus.DECLINING;
			case UNPROFITABLE:
				return opp.getStatus() == OpportunityStatus.UNPROFITABLE || opp.getStatus() == OpportunityStatus.DECLINING;
			case WATCHED_ONLY:
				return opp.isWatched();
			default:
				return true;
		}
	}

	public WatchlistManager getWatchlistManager()
	{
		return watchlistManager;
	}

	public ScanResult getLatestScan()
	{
		return latestScan;
	}
}
