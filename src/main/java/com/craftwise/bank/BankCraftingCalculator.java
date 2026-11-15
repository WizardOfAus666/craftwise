package com.craftwise.bank;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.bank.OwnedMaterialSnapshot.OwnedItemCount;
import com.craftwise.bank.OwnedMaterialSnapshot.SnapshotView;
import com.craftwise.calculator.GETransactionCalculator;
import com.craftwise.calculator.InventoryCalculator;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.calculator.ProfitCalculator.CustomPriceOverride;
import com.craftwise.calculator.ShoppingListCalculator;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListSummary;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingSortOrder;
import com.craftwise.calculator.TrainingPlanComparisonCalculator;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.ComparedPlan;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.OptimizerOptions;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.PlanStrategy;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.craftwise.model.TrainingPlan;
import com.craftwise.util.XPTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

/**
 * Core Bank-Aware Crafting calculation engine (Features 2.2 - 2.9).
 * Matches owned bank/inventory quantities against verified Crafting recipes, computes craftable
 * counts, shortages, reusable tool checks, bank material value comparisons (SELL vs CRAFT),
 * bank-aware training route Plan A vs Plan B, and recommendations.
 */
public final class BankCraftingCalculator
{
	@Getter
	@RequiredArgsConstructor
	public enum CoverageTier
	{
		READY_100("100% READY"),
		READY_75("75%+ READY"),
		PARTIAL("PARTIALLY READY"),
		NOT_READY("NOT READY");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Getter
	@RequiredArgsConstructor
	public enum BankRecommendationMode
	{
		BEST_WITH_MY_BANK("Best With My Bank"),
		MOST_XP_FROM_BANK("Most XP From Bank"),
		MOST_PROFIT_FROM_BANK("Most Profit From Bank"),
		LEAST_ADDITIONAL_GP("Least Additional GP"),
		BEST_MATERIAL_CONVERSION("Best Material Conversion");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Data
	@Builder
	public static class MaterialAllocation
	{
		private final int itemId;
		private final String itemName;
		private final boolean consumed;
		private final double requiredPerCraft;
		private final long ownedBankQty;
		private final long ownedInvUnnotedQty;
		private final long ownedInvNotedQty;
		private final long combinedUsableQty;
		private final long remainingAfterMaxCrafts;
		private final long missingForTargetCrafts;
		private final Long estimatedUnitBuyPrice;
		private final long additionalCostForTargetGp;
	}

	@Data
	@Builder
	public static class BankRecipeMatch
	{
		private final CraftingRecipe recipe;
		private final CraftingResult marketResult;
		private final long maxCompleteCrafts;
		private final long readyInventoryOnlyCrafts;
		private final long maxCraftsIncludingNoted;
		private final long targetCraftsEvaluated;
		private final double coveragePercent;
		private final CoverageTier coverageTier;
		private final double totalXpFromOwnedStock;
		private final double totalProfitFromOwnedStock;
		private final double expectedRevenueFromOwnedStock;
		private final double geTaxFromOwnedStock;
		private final long missingForTargetCostGp;
		private final boolean additionalPricesComplete;
		private final boolean missingTools;
		@Builder.Default
		private final List<String> missingToolNames = Collections.emptyList();
		@Builder.Default
		private final List<MaterialAllocation> allocations = Collections.emptyList();

		public boolean isCraftableNow()
		{
			return maxCompleteCrafts > 0L;
		}

		public boolean isProfitableWithOwnedStock()
		{
			return marketResult != null && marketResult.isProfitable() && maxCompleteCrafts > 0L;
		}
	}

	@Data
	@Builder
	public static class BankMaterialValueEntry
	{
		private final int itemId;
		private final String itemName;
		private final long bankQty;
		private final long invUnnotedQty;
		private final long invNotedQty;
		private final long totalOwnedQty;
		private final Long unitSellPrice;
		private final long netSellValueAfterTaxGp;
		private final String bestRecipeId;
		private final String bestRecipeName;
		private final long craftableUsingOwnedQty;
		private final long estimatedCraftProfitGp;
		private final long additionalSecondaryCostGp;
		private final long craftAdvantageVsSellingGp;
		private final String comparisonLabel; // "CRAFT (Est.)", "SELL RAW (Est.)", or "MISSING MARKET"
		private final String estimateNote;
	}

	@Data
	@Builder
	public static class BankPlanSegment
	{
		private final CraftingRecipe recipe;
		private final long craftsUsingBankStock;
		private final double xpGained;
		private final double grossValueGp;
		private final double taxGp;
		private final double externalMissingCostGp;
		private final double netGainGp;
	}

	@Data
	@Builder
	public static class BankTrainingComparison
	{
		private final int startLevel;
		private final int targetLevel;
		private final long xpRequiredTotal;
		private final long xpCoveredByBank;
		private final long xpRemainingAfterBank;
		@Builder.Default
		private final List<BankPlanSegment> bankUsedSegments = Collections.emptyList();
		private final TrainingPlan planARemainingRoute;
		private final double planATotalAdditionalSpendGp;
		private final double planANetProfitLossGp;
		private final double planATotalHours;

		private final TrainingPlan planBCheapestFullRoute;
		private final double planBNetProfitLossGp;
		private final double planBTotalHours;
		private final long ownedBankCraftingSellValueGp;

		private final String adviceSummary;
	}

	private static final Map<String, Integer> TOOL_NAME_TO_ID;

	static
	{
		Map<String, Integer> m = new LinkedHashMap<>();
		m.put("chisel", ItemID.CHISEL);
		m.put("needle", ItemID.NEEDLE);
		m.put("glassblowing pipe", ItemID.GLASSBLOWING_PIPE);
		m.put("hammer", ItemID.HAMMER);
		m.put("ring mould", ItemID.RING_MOULD);
		m.put("necklace mould", ItemID.NECKLACE_MOULD);
		m.put("bracelet mould", ItemID.BRACELET_MOULD);
		m.put("amulet mould", ItemID.AMULET_MOULD);
		m.put("tiara mould", ItemID.TIARA_MOULD);
		m.put("holy mould", ItemID.HOLY_MOULD);
		m.put("unholy mould", ItemID.UNHOLY_MOULD);
		m.put("sickle mould", ItemID.SICKLE_MOULD);
		m.put("bolt mould", ItemID.BOLT_MOULD);
		m.put("ammo mould", ItemID.AMMO_MOULD);
		TOOL_NAME_TO_ID = Collections.unmodifiableMap(m);
	}

	private BankCraftingCalculator()
	{
	}

	/**
	 * Matches a single recipe against player-owned materials.
	 * Distinguishes reusable tools/moulds from consumed ingredients and computes shortages cleanly.
	 */
	public static BankRecipeMatch matchRecipe(
		CraftingRecipe recipe,
		CraftingResult marketResult,
		SnapshotView snapshot,
		long requestedTargetCrafts,
		boolean includeNotedFromInventory,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		if (recipe == null)
		{
			return null;
		}
		long targetCrafts = requestedTargetCrafts > 0L
			? requestedTargetCrafts
			: Math.max(1L, InventoryCalculator.calculateCraftsPerInventory(recipe));

		List<RecipeInput> inputs = recipe.getInputs();
		List<MaterialAllocation> allocations = new ArrayList<>();
		java.util.Set<Integer> checkedToolIds = new java.util.HashSet<>();
		List<String> missingToolNames = new ArrayList<>();
		long maxUsableCrafts = Long.MAX_VALUE;
		long invOnlyCrafts = Long.MAX_VALUE;
		long notedIncludedCrafts = Long.MAX_VALUE;
		long additionalCostForTarget = 0L;
		boolean additionalPricesComplete = true;

		for (RecipeInput in : inputs)
		{
			OwnedItemCount count = snapshot != null ? snapshot.getCount(in.getItemId()) : null;
			long bankQ = count != null ? count.getBankQty() : 0L;
			long invU = count != null ? count.getInventoryUnnotedQty() : 0L;
			long invN = count != null ? count.getInventoryNotedQty() : 0L;
			long man = count != null ? count.getManualAdjustmentQty() : 0L;

			long usable = bankQ + invU + man + (includeNotedFromInventory ? invN : 0L);
			long allIncludingNoted = bankQ + invU + invN + man;

			if (!in.isConsumed())
			{
				checkedToolIds.add(in.getItemId());
				// Reusable tool/mould placed directly in inputs -> never scales with craft count
				long reqTool = (long) Math.ceil(Math.max(1.0, in.getQuantity()));
				long missingTool = Math.max(0L, reqTool - usable);
				Long buyP = resolveUnitBuyPrice(in.getItemId(), in.getItemName(), prices, nameToIdIndex, mode, customPrices);
				long toolCost = 0L;
				if (missingTool > 0L)
				{
					missingToolNames.add(in.getItemName() != null ? in.getItemName() : ("Item #" + in.getItemId()));
					if (buyP != null && buyP > 0L)
					{
						toolCost = missingTool * buyP;
						additionalCostForTarget += toolCost;
					}
					else if (in.getItemId() != ItemID.COINS_995)
					{
						additionalPricesComplete = false;
					}
				}
				if (usable < reqTool)
				{
					maxUsableCrafts = 0L;
				}
				if (invU < reqTool)
				{
					invOnlyCrafts = 0L;
				}
				if (allIncludingNoted < reqTool)
				{
					notedIncludedCrafts = 0L;
				}
				allocations.add(MaterialAllocation.builder()
					.itemId(in.getItemId())
					.itemName(in.getItemName())
					.consumed(false)
					.requiredPerCraft(in.getQuantity())
					.ownedBankQty(bankQ)
					.ownedInvUnnotedQty(invU)
					.ownedInvNotedQty(invN)
					.combinedUsableQty(usable)
					.remainingAfterMaxCrafts(usable)
					.missingForTargetCrafts(missingTool)
					.estimatedUnitBuyPrice(buyP)
					.additionalCostForTargetGp(toolCost)
					.build());
				continue;
			}

			double perCraft = Math.max(1e-9, in.getQuantity());
			long cUsable = (long) Math.floor((usable + 1e-9) / perCraft);
			long cInv = (long) Math.floor((invU + 1e-9) / perCraft);
			long cAll = (long) Math.floor((allIncludingNoted + 1e-9) / perCraft);

			maxUsableCrafts = Math.min(maxUsableCrafts, cUsable);
			invOnlyCrafts = Math.min(invOnlyCrafts, cInv);
			notedIncludedCrafts = Math.min(notedIncludedCrafts, cAll);

			long neededForTarget = (long) Math.ceil((perCraft * targetCrafts) - 1e-9);
			long missingForTarget = Math.max(0L, neededForTarget - usable);
			Long buyP = in.getItemId() == ItemID.COINS_995
				? Long.valueOf(1L)
				: resolveUnitBuyPrice(in.getItemId(), in.getItemName(), prices, nameToIdIndex, mode, customPrices);

			long addCost = 0L;
			if (missingForTarget > 0L)
			{
				if (buyP != null && buyP > 0L)
				{
					addCost = missingForTarget * buyP;
					additionalCostForTarget += addCost;
				}
				else
				{
					additionalPricesComplete = false;
				}
			}

			allocations.add(MaterialAllocation.builder()
				.itemId(in.getItemId())
				.itemName(in.getItemName())
				.consumed(true)
				.requiredPerCraft(perCraft)
				.ownedBankQty(bankQ)
				.ownedInvUnnotedQty(invU)
				.ownedInvNotedQty(invN)
				.combinedUsableQty(usable)
				.remainingAfterMaxCrafts(0L) // populated below once overall bottleneck is known
				.missingForTargetCrafts(missingForTarget)
				.estimatedUnitBuyPrice(buyP)
				.additionalCostForTargetGp(addCost)
				.build());
		}

		// Also verify reusable tools & moulds declared on CraftingRecipe.requiredTools / requiredMoulds
		for (String toolName : recipe.getAllToolsAndMoulds())
		{
			if (toolName == null || toolName.trim().isEmpty())
			{
				continue;
			}
			Integer toolId = resolveToolItemId(toolName, nameToIdIndex);
			if (toolId == null || !checkedToolIds.add(toolId))
			{
				continue;
			}
			OwnedItemCount count = snapshot != null ? snapshot.getCount(toolId) : null;
			long bankQ = count != null ? count.getBankQty() : 0L;
			long invU = count != null ? count.getInventoryUnnotedQty() : 0L;
			long invN = count != null ? count.getInventoryNotedQty() : 0L;
			long man = count != null ? count.getManualAdjustmentQty() : 0L;

			long usable = bankQ + invU + man + (includeNotedFromInventory ? invN : 0L);
			long allIncludingNoted = bankQ + invU + invN + man;
			long missingTool = usable < 1L ? 1L : 0L;
			Long buyP = resolveUnitBuyPrice(toolId, toolName, prices, nameToIdIndex, mode, customPrices);
			long toolCost = 0L;
			if (missingTool > 0L)
			{
				missingToolNames.add(toolName);
				if (buyP != null && buyP > 0L)
				{
					toolCost = buyP;
					additionalCostForTarget += toolCost;
				}
				maxUsableCrafts = 0L;
			}
			if (invU < 1L)
			{
				invOnlyCrafts = 0L;
			}
			if (allIncludingNoted < 1L)
			{
				notedIncludedCrafts = 0L;
			}
			allocations.add(MaterialAllocation.builder()
				.itemId(toolId)
				.itemName(toolName)
				.consumed(false)
				.requiredPerCraft(1.0)
				.ownedBankQty(bankQ)
				.ownedInvUnnotedQty(invU)
				.ownedInvNotedQty(invN)
				.combinedUsableQty(usable)
				.remainingAfterMaxCrafts(usable)
				.missingForTargetCrafts(missingTool)
				.estimatedUnitBuyPrice(buyP)
				.additionalCostForTargetGp(toolCost)
				.build());
		}

		if (maxUsableCrafts == Long.MAX_VALUE)
		{
			maxUsableCrafts = 0L;
		}
		if (invOnlyCrafts == Long.MAX_VALUE)
		{
			invOnlyCrafts = 0L;
		}
		if (notedIncludedCrafts == Long.MAX_VALUE)
		{
			notedIncludedCrafts = 0L;
		}

		// Populate true remaining quantities after performing maxUsableCrafts
		List<MaterialAllocation> finalAllocs = new ArrayList<>(allocations.size());
		for (MaterialAllocation a : allocations)
		{
			long used = a.isConsumed() ? Math.round(maxUsableCrafts * a.getRequiredPerCraft()) : 0L;
			long rem = Math.max(0L, a.getCombinedUsableQty() - used);
			finalAllocs.add(MaterialAllocation.builder()
				.itemId(a.getItemId())
				.itemName(a.getItemName())
				.consumed(a.isConsumed())
				.requiredPerCraft(a.getRequiredPerCraft())
				.ownedBankQty(a.getOwnedBankQty())
				.ownedInvUnnotedQty(a.getOwnedInvUnnotedQty())
				.ownedInvNotedQty(a.getOwnedInvNotedQty())
				.combinedUsableQty(a.getCombinedUsableQty())
				.remainingAfterMaxCrafts(rem)
				.missingForTargetCrafts(a.getMissingForTargetCrafts())
				.estimatedUnitBuyPrice(a.getEstimatedUnitBuyPrice())
				.additionalCostForTargetGp(a.getAdditionalCostForTargetGp())
				.build());
		}

		// Craftable capacity coverage against targetCrafts (never just ingredient-type count)
		double coveragePct = targetCrafts > 0L
			? Math.min(100.0, (maxUsableCrafts * 100.0) / targetCrafts)
			: 0.0;
		// Also give partial credit when >0% of target inputs exist so multi-input near-ready recipes surface cleanly
		if (coveragePct == 0.0 && maxUsableCrafts == 0L && !inputs.isEmpty())
		{
			double minRatio = 1.0;
			double avgRatio = 0.0;
			int consumedCount = 0;
			for (MaterialAllocation a : finalAllocs)
			{
				if (a.isConsumed() && a.getRequiredPerCraft() > 0.0)
				{
					double req = a.getRequiredPerCraft() * targetCrafts;
					double r = Math.min(1.0, a.getCombinedUsableQty() / req);
					minRatio = Math.min(minRatio, r);
					avgRatio += r;
					consumedCount++;
				}
			}
			if (consumedCount > 0 && (avgRatio / consumedCount) > 0.0)
			{
				// Partial materials exist for at least 1 ingredient, bottleneck currently 0 complete crafts
				coveragePct = Math.min(49.0, (avgRatio / consumedCount) * 49.0);
			}
		}
		CoverageTier tier = classifyCoverage(maxUsableCrafts, targetCrafts, coveragePct);

		double xpFromStock = maxUsableCrafts * recipe.getXpPerAction();
		double profFromStock = (marketResult != null && marketResult.isPriceDataComplete())
			? maxUsableCrafts * marketResult.getProfitPerCraft()
			: 0.0;
		double revFromStock = (marketResult != null && marketResult.isPriceDataComplete())
			? maxUsableCrafts * marketResult.getGrossRevenuePerCraft()
			: 0.0;
		double taxFromStock = (marketResult != null && marketResult.isPriceDataComplete())
			? maxUsableCrafts * marketResult.getGeTaxPerCraft()
			: 0.0;

		return BankRecipeMatch.builder()
			.recipe(recipe)
			.marketResult(marketResult)
			.maxCompleteCrafts(maxUsableCrafts)
			.readyInventoryOnlyCrafts(invOnlyCrafts)
			.maxCraftsIncludingNoted(notedIncludedCrafts)
			.targetCraftsEvaluated(targetCrafts)
			.coveragePercent(coveragePct)
			.coverageTier(tier)
			.totalXpFromOwnedStock(xpFromStock)
			.totalProfitFromOwnedStock(profFromStock)
			.expectedRevenueFromOwnedStock(revFromStock)
			.geTaxFromOwnedStock(taxFromStock)
			.missingForTargetCostGp(additionalCostForTarget)
			.additionalPricesComplete(additionalPricesComplete)
			.missingTools(!missingToolNames.isEmpty())
			.missingToolNames(Collections.unmodifiableList(missingToolNames))
			.allocations(Collections.unmodifiableList(finalAllocs))
			.build();
	}

	private static Integer resolveToolItemId(String toolName, Map<String, Integer> nameToIdIndex)
	{
		String norm = toolName.trim().toLowerCase(java.util.Locale.US);
		Integer known = TOOL_NAME_TO_ID.get(norm);
		if (known != null)
		{
			return known;
		}
		if (nameToIdIndex != null)
		{
			return nameToIdIndex.get(norm);
		}
		return null;
	}

	private static CoverageTier classifyCoverage(long maxCompleteCrafts, long targetCrafts, double pct)
	{
		if (targetCrafts > 0L && maxCompleteCrafts >= targetCrafts)
		{
			return CoverageTier.READY_100;
		}
		if (pct >= 75.0 && maxCompleteCrafts > 0L)
		{
			return CoverageTier.READY_75;
		}
		if (pct > 0.0 || maxCompleteCrafts > 0L)
		{
			return CoverageTier.PARTIAL;
		}
		return CoverageTier.NOT_READY;
	}

	public static List<BankRecipeMatch> matchAndRankAll(
		List<CraftingResult> marketResults,
		SnapshotView snapshot,
		long targetCraftsPerRecipe,
		boolean includeNotedFromInventory,
		int playerLevel,
		boolean membersAllowed,
		boolean includeQuestLocked,
		boolean includeUnlockRequired,
		boolean craftableNowOnly,
		boolean profitableWithBankOnly,
		BankRecommendationMode rankMode,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		if (marketResults == null || marketResults.isEmpty())
		{
			return Collections.emptyList();
		}

		List<BankRecipeMatch> matches = new ArrayList<>();
		for (CraftingResult res : marketResults)
		{
			if (res == null || res.getRecipe() == null)
			{
				continue;
			}
			CraftingRecipe rec = res.getRecipe();
			if (rec.getVerificationStatus() == VerificationStatus.UNSUPPORTED)
			{
				continue;
			}
			if (rec.getRequiredLevel() > playerLevel)
			{
				continue;
			}
			if (!membersAllowed && rec.isMembers())
			{
				continue;
			}
			if (!includeQuestLocked && rec.hasQuestRequirement())
			{
				continue;
			}
			if (!includeUnlockRequired && rec.hasUnlockRequirement())
			{
				continue;
			}

			BankRecipeMatch m = matchRecipe(
				rec, res, snapshot, targetCraftsPerRecipe, includeNotedFromInventory,
				prices, nameToIdIndex, mode, customPrices);
			if (m == null)
			{
				continue;
			}
			if (craftableNowOnly && !m.isCraftableNow())
			{
				continue;
			}
			if (profitableWithBankOnly && !m.isProfitableWithOwnedStock())
			{
				continue;
			}
			matches.add(m);
		}

		BankRecommendationMode effectiveRank = rankMode != null ? rankMode : BankRecommendationMode.BEST_WITH_MY_BANK;
		switch (effectiveRank)
		{
			case MOST_XP_FROM_BANK:
				matches.sort(Comparator.comparingDouble(BankRecipeMatch::getTotalXpFromOwnedStock).reversed());
				break;
			case MOST_PROFIT_FROM_BANK:
				matches.sort(Comparator.comparingDouble(BankRecipeMatch::getTotalProfitFromOwnedStock).reversed());
				break;
			case LEAST_ADDITIONAL_GP:
				matches.sort(Comparator
					.comparingDouble((BankRecipeMatch m) -> m.isCraftableNow() ? 0 : 1)
					.thenComparingLong(BankRecipeMatch::getMissingForTargetCostGp)
					.thenComparing(Comparator.comparingDouble(BankRecipeMatch::getTotalXpFromOwnedStock).reversed()));
				break;
			case BEST_MATERIAL_CONVERSION:
				matches.sort(Comparator.comparingDouble((BankRecipeMatch m) ->
					m.getMarketResult() != null && m.getMarketResult().isPriceDataComplete()
						? m.getMarketResult().getProfitPerCraft()
						: -1e9).reversed());
				break;
			case BEST_WITH_MY_BANK:
			default:
				matches.sort(Comparator
					.comparingInt((BankRecipeMatch m) -> m.isCraftableNow() ? 0 : 1)
					.thenComparing(Comparator.comparingDouble((BankRecipeMatch m) ->
						(m.getTotalXpFromOwnedStock() * 0.6) + Math.max(-50_000.0, m.getTotalProfitFromOwnedStock())).reversed()));
				break;
		}
		return Collections.unmodifiableList(matches);
	}

	/**
	 * Feature 2.6: Analyzes owned Crafting materials directly: SELL MATERIALS vs CRAFT MATERIALS.
	 */
	public static List<BankMaterialValueEntry> analyzeOwnedMaterials(
		SnapshotView snapshot,
		List<CraftingResult> marketResults,
		int playerLevel,
		boolean includeNotedFromInventory,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		boolean includeGeTax,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		if (snapshot == null || snapshot.getItems().isEmpty())
		{
			return Collections.emptyList();
		}

		// Index verified recipes consuming each itemId
		Map<Integer, List<CraftingResult>> consumers = new LinkedHashMap<>();
		for (CraftingResult r : marketResults)
		{
			if (r == null || r.getRecipe() == null)
			{
				continue;
			}
			CraftingRecipe rec = r.getRecipe();
			if (rec.getVerificationStatus() == VerificationStatus.UNSUPPORTED || rec.getRequiredLevel() > playerLevel)
			{
				continue;
			}
			for (RecipeInput in : rec.getInputs())
			{
				if (in.isConsumed() && in.getItemId() != ItemID.COINS_995)
				{
					consumers.computeIfAbsent(in.getItemId(), k -> new ArrayList<>()).add(r);
				}
			}
		}

		List<BankMaterialValueEntry> entries = new ArrayList<>();
		for (OwnedItemCount count : snapshot.getItems().values())
		{
			List<CraftingResult> uses = consumers.get(count.getItemId());
			if (uses == null || uses.isEmpty())
			{
				continue;
			}
			long ownedUsable = includeNotedFromInventory ? count.getCombinedIncludingNotedQty() : count.getCombinedUsableQty();
			if (ownedUsable <= 0L)
			{
				continue;
			}

			ItemPrice ip = ProfitCalculator.resolveItemPrice(count.getItemId(), count.getItemName(), prices, nameToIdIndex);
			Long sellP = resolveUnitSellPrice(ip, mode, customPrices, count.getItemId());
			long sellNetValue = 0L;
			if (sellP != null && sellP > 0L)
			{
				long taxUnit = GETransactionCalculator.calculateTaxPerItem(sellP, count.getItemId(), false, includeGeTax);
				sellNetValue = (sellP - taxUnit) * ownedUsable;
			}

			// Find highest net-value conversion among eligible recipes using this item
			CraftingResult bestUse = null;
			long bestCraftNetTotal = Long.MIN_VALUE;
			long bestCraftsPossible = 0L;
			long bestSecondaryAddCost = 0L;

			for (CraftingResult cr : uses)
			{
				if (!cr.isPriceDataComplete())
				{
					continue;
				}
				double qtyPerCraft = 1.0;
				for (RecipeInput in : cr.getRecipe().getInputs())
				{
					if (in.isConsumed() && in.getItemId() == count.getItemId())
					{
						qtyPerCraft = Math.max(1e-6, in.getQuantity());
						break;
					}
				}
				long craftsFromThisItem = (long) Math.floor((ownedUsable + 1e-9) / qtyPerCraft);
				if (craftsFromThisItem <= 0L)
				{
					continue;
				}
				long grossCraftNet = Math.round(craftsFromThisItem * cr.getNetRevenuePerCraft());
				long addSecCost = 0L;
				for (RecipeInput in : cr.getRecipe().getInputs())
				{
					if (!in.isConsumed() || in.getItemId() == count.getItemId())
					{
						continue;
					}
					long neededSec = Math.round(craftsFromThisItem * in.getQuantity());
					OwnedItemCount secOwned = snapshot.getCount(in.getItemId());
					long haveSec = secOwned != null
						? (includeNotedFromInventory ? secOwned.getCombinedIncludingNotedQty() : secOwned.getCombinedUsableQty())
						: 0L;
					long buySec = Math.max(0L, neededSec - haveSec);
					if (buySec > 0L)
					{
						Long secP = in.getItemId() == ItemID.COINS_995
							? Long.valueOf(1L)
							: resolveUnitBuyPrice(in.getItemId(), in.getItemName(), prices, nameToIdIndex, mode, customPrices);
						if (secP != null && secP > 0L)
						{
							addSecCost += buySec * secP;
						}
					}
				}
				long netAfterSec = grossCraftNet - addSecCost;
				if (netAfterSec > bestCraftNetTotal)
				{
					bestCraftNetTotal = netAfterSec;
					bestUse = cr;
					bestCraftsPossible = craftsFromThisItem;
					bestSecondaryAddCost = addSecCost;
				}
			}

			String bestName = bestUse != null ? bestUse.getRecipe().getName() : uses.get(0).getRecipe().getName();
			String bestId = bestUse != null ? bestUse.getRecipe().getId() : uses.get(0).getRecipe().getId();
			long advantage = (bestUse != null && sellP != null) ? (bestCraftNetTotal - sellNetValue) : 0L;
			String label = (bestUse == null || sellP == null)
				? "MISSING MARKET"
				: (advantage >= 0L ? "CRAFT (Est.)" : "SELL RAW (Est.)");

			entries.add(BankMaterialValueEntry.builder()
				.itemId(count.getItemId())
				.itemName(count.getItemName())
				.bankQty(count.getBankQty())
				.invUnnotedQty(count.getInventoryUnnotedQty())
				.invNotedQty(count.getInventoryNotedQty())
				.totalOwnedQty(ownedUsable)
				.unitSellPrice(sellP)
				.netSellValueAfterTaxGp(sellNetValue)
				.bestRecipeId(bestId)
				.bestRecipeName(bestName)
				.craftableUsingOwnedQty(bestCraftsPossible)
				.estimatedCraftProfitGp(bestUse != null ? bestCraftNetTotal : 0L)
				.additionalSecondaryCostGp(bestSecondaryAddCost)
				.craftAdvantageVsSellingGp(advantage)
				.comparisonLabel(label)
				.estimateNote("Assumes current GE prices; execution not guaranteed.")
				.build());
		}

		entries.sort(Comparator.comparingLong(BankMaterialValueEntry::getNetSellValueAfterTaxGp).reversed());
		return Collections.unmodifiableList(entries);
	}

	/**
	 * Feature 2.5: Compares Plan A (consume eligible bank stock first without double-counting)
	 * vs Plan B (ignore existing bank stock & follow optimal cheapest market route).
	 */
	public static BankTrainingComparison compareBankTrainingPlans(
		SnapshotView snapshot,
		List<CraftingResult> allResults,
		int startLevel,
		long startXp,
		int targetLevel,
		long budgetGp,
		int temporaryBoost,
		boolean membersAllowed,
		boolean includeQuestLocked,
		boolean includeUnlockRequired,
		boolean includeNotedFromInventory,
		boolean includeGeTax)
	{
		return compareBankTrainingPlans(
			snapshot, allResults, startLevel, startXp, targetLevel, budgetGp, temporaryBoost,
			membersAllowed, includeQuestLocked, includeUnlockRequired, includeNotedFromInventory, includeGeTax,
			Collections.emptyMap(), Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL);
	}

	/**
	 * Feature 2.5: Compares Plan A (consume eligible bank stock first without double-counting)
	 * vs Plan B (ignore existing bank stock & follow optimal cheapest market route),
	 * resolving live GE sell value of owned Crafting ingredients from {@code prices}.
	 */
	public static BankTrainingComparison compareBankTrainingPlans(
		SnapshotView snapshot,
		List<CraftingResult> allResults,
		int startLevel,
		long startXp,
		int targetLevel,
		long budgetGp,
		int temporaryBoost,
		boolean membersAllowed,
		boolean includeQuestLocked,
		boolean includeUnlockRequired,
		boolean includeNotedFromInventory,
		boolean includeGeTax,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode)
	{
		int sLvl = Math.max(1, Math.min(98, startLevel));
		int tLvl = Math.max(sLvl + 1, Math.min(99, targetLevel));
		long curXp = Math.max(startXp, XPTable.getXpForLevel(sLvl));
		long tgtXp = XPTable.getXpForLevel(tLvl);
		long totalXpNeeded = Math.max(0L, tgtXp - curXp);

		// Compute pure Plan B (market-optimal route ignoring bank)
		List<ComparedPlan> marketPlans = TrainingPlanComparisonCalculator.buildSideBySidePlans(
			allResults,
			OptimizerOptions.builder()
				.currentLevel(sLvl)
				.currentXp(curXp)
				.targetLevel(tLvl)
				.temporaryBoost(temporaryBoost)
				.membersAllowed(membersAllowed)
				.includeQuestLocked(includeQuestLocked)
				.includeUnlockRequired(includeUnlockRequired)
				.availableBudgetGp(budgetGp)
				.build()
		);
		TrainingPlan planB = null;
		for (ComparedPlan cp : marketPlans)
		{
			if (cp.getStrategy() == PlanStrategy.PLAN_B_CHEAPEST && cp.isFeasible())
			{
				planB = cp.getPlan();
				break;
			}
		}

		// Mutable pool of owned item counts so we NEVER double-count materials across bank segments
		Map<Integer, Double> mutableOwned = new LinkedHashMap<>();
		if (snapshot != null)
		{
			for (Map.Entry<Integer, Long> e : snapshot.toCombinedUsableMap(includeNotedFromInventory).entrySet())
			{
				mutableOwned.put(e.getKey(), (double) e.getValue());
			}
		}

		// Collect eligible recipes sorted by highest XP/hr then profit
		List<CraftingResult> candidates = new ArrayList<>();
		for (CraftingResult r : allResults)
		{
			if (r == null || r.getRecipe() == null || r.getRecipe().getVerificationStatus() == VerificationStatus.UNSUPPORTED)
			{
				continue;
			}
			CraftingRecipe rec = r.getRecipe();
			if (rec.getXpPerAction() <= 0.0)
			{
				continue;
			}
			if (!membersAllowed && rec.isMembers())
			{
				continue;
			}
			if (!includeQuestLocked && rec.hasQuestRequirement())
			{
				continue;
			}
			if (!includeUnlockRequired && rec.hasUnlockRequirement())
			{
				continue;
			}
			candidates.add(r);
		}
		candidates.sort(Comparator.comparingDouble(CraftingResult::getXpPerAction).reversed());

		List<BankPlanSegment> bankSegments = new ArrayList<>();
		long xpCursor = curXp;
		double bankCraftedNetGain = 0.0;
		double bankHrs = 0.0;

		boolean madeProgress = true;
		while (madeProgress && xpCursor < tgtXp)
		{
			madeProgress = false;
			int effLvl = XPTable.getLevelForXp(xpCursor);
			for (CraftingResult cand : candidates)
			{
				CraftingRecipe rec = cand.getRecipe();
				int usableLvl = rec.isBoostable() ? effLvl + temporaryBoost : effLvl;
				if (rec.getRequiredLevel() > usableLvl)
				{
					continue;
				}
				long maxCrafts = computeCraftsFromMutablePool(rec, mutableOwned);
				if (maxCrafts <= 0L)
				{
					continue;
				}
				long actionsToGoal = (long) Math.ceil((tgtXp - xpCursor) / rec.getXpPerAction());
				long doCrafts = Math.min(maxCrafts, Math.max(1L, actionsToGoal));
				consumeFromMutablePool(rec, mutableOwned, doCrafts);

				double xpGained = doCrafts * rec.getXpPerAction();
				double grossVal = cand.isPriceDataComplete() ? doCrafts * cand.getGrossRevenuePerCraft() : 0.0;
				double taxVal = cand.isPriceDataComplete() ? doCrafts * cand.getGeTaxPerCraft() : 0.0;
				double netGain = grossVal - taxVal; // materials came from bank (0 new GP spent)
				bankSegments.add(BankPlanSegment.builder()
					.recipe(rec)
					.craftsUsingBankStock(doCrafts)
					.xpGained(xpGained)
					.grossValueGp(grossVal)
					.taxGp(taxVal)
					.externalMissingCostGp(0.0)
					.netGainGp(netGain)
					.build());
				xpCursor += Math.round(xpGained);
				bankCraftedNetGain += netGain;
				bankHrs += cand.getActionsPerHour() > 0.0 ? doCrafts / cand.getActionsPerHour() : 0.0;
				madeProgress = true;
				break;
			}
		}

		long xpCovered = Math.min(totalXpNeeded, Math.max(0L, xpCursor - curXp));
		long xpRemaining = Math.max(0L, totalXpNeeded - xpCovered);

		TrainingPlan planARemaining = null;
		double planAAddSpend = 0.0;
		double planARemProfit = 0.0;
		double planARemHrs = 0.0;
		if (xpRemaining > 0L)
		{
			int remStartLvl = XPTable.getLevelForXp(curXp + xpCovered);
			List<ComparedPlan> remPlans = TrainingPlanComparisonCalculator.buildSideBySidePlans(
				allResults,
				OptimizerOptions.builder()
					.currentLevel(remStartLvl)
					.currentXp(curXp + xpCovered)
					.targetLevel(tLvl)
					.temporaryBoost(temporaryBoost)
					.membersAllowed(membersAllowed)
					.includeQuestLocked(includeQuestLocked)
					.includeUnlockRequired(includeUnlockRequired)
					.availableBudgetGp(budgetGp)
					.build()
			);
			for (ComparedPlan cp : remPlans)
			{
				if (cp.getStrategy() == PlanStrategy.PLAN_B_CHEAPEST && cp.isFeasible() && cp.getPlan() != null)
				{
					planARemaining = cp.getPlan();
					planAAddSpend = cp.getPlan().getTotalMaterialCost();
					planARemProfit = cp.getPlan().getTotalNetProfit();
					planARemHrs = cp.getPlan().getTotalEstimatedHours();
					break;
				}
			}
		}

		// Estimate net liquidation value of owned Crafting materials in bank/inventory if sold on GE instead
		long bankSellValue = 0L;
		if (snapshot != null && prices != null && !prices.isEmpty())
		{
			java.util.Set<Integer> craftingMaterialIds = new java.util.HashSet<>();
			for (CraftingResult cr : allResults)
			{
				if (cr == null || cr.getRecipe() == null)
				{
					continue;
				}
				for (RecipeInput in : cr.getRecipe().getInputs())
				{
					if (in.isConsumed())
					{
						craftingMaterialIds.add(in.getItemId());
					}
				}
			}
			for (OwnedItemCount c : snapshot.getItems().values())
			{
				if (!craftingMaterialIds.contains(c.getItemId()))
				{
					continue;
				}
				long q = includeNotedFromInventory ? c.getCombinedIncludingNotedQty() : c.getCombinedUsableQty();
				if (q <= 0L)
				{
					continue;
				}
				ItemPrice ip = ProfitCalculator.resolveItemPrice(c.getItemId(), c.getItemName(), prices, nameToIdIndex);
				Long unitSell = resolveUnitSellPrice(ip, mode != null ? mode : PriceCalculationMode.INSTANT_BUY_SELL, null, c.getItemId());
				if (unitSell != null && unitSell > 0L)
				{
					long netPerUnit = GETransactionCalculator.calculateNetRevenuePerItem(
						unitSell, c.getItemId(), false, includeGeTax);
					bankSellValue += Math.max(0L, q * netPerUnit);
				}
			}
		}

		double planATotalNet = bankCraftedNetGain + planARemProfit;
		double planBTotalNet = planB != null ? planB.getTotalNetProfit() : 0.0;
		String advice;
		if (xpCovered == 0L)
		{
			advice = "No eligible bank materials found; Plan A matches the market route (Plan B).";
		}
		else if (planB == null)
		{
			advice = String.format(java.util.Locale.US,
				"Bank materials provide %,d XP (%+, .0f GP est. output value after tax).",
				xpCovered, bankCraftedNetGain).replace(", ", ",");
		}
		else if (planATotalNet >= planBTotalNet)
		{
			advice = String.format(java.util.Locale.US,
				"Plan A covers %,d XP from bank and leaves %,.0f GP remaining purchase cost.",
				xpCovered, planAAddSpend);
		}
		else
		{
			advice = String.format(java.util.Locale.US,
				"Plan B may yield higher net value than crafting raw bank items; check Material Value comparison.",
				xpCovered);
		}

		return BankTrainingComparison.builder()
			.startLevel(sLvl)
			.targetLevel(tLvl)
			.xpRequiredTotal(totalXpNeeded)
			.xpCoveredByBank(xpCovered)
			.xpRemainingAfterBank(xpRemaining)
			.bankUsedSegments(Collections.unmodifiableList(bankSegments))
			.planARemainingRoute(planARemaining)
			.planATotalAdditionalSpendGp(planAAddSpend)
			.planANetProfitLossGp(planATotalNet)
			.planATotalHours(bankHrs + planARemHrs)
			.planBCheapestFullRoute(planB)
			.planBNetProfitLossGp(planBTotalNet)
			.planBTotalHours(planB != null ? planB.getTotalEstimatedHours() : 0.0)
			.ownedBankCraftingSellValueGp(bankSellValue)
			.adviceSummary(advice)
			.build();
	}

	public static ShoppingListSummary buildBankAwareShoppingList(
		CraftingRecipe recipe,
		long actions,
		SnapshotView snapshot,
		boolean includeNotedFromInventory,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices,
		ShoppingSortOrder sortOrder)
	{
		Map<Integer, Long> owned = snapshot != null
			? snapshot.toCombinedUsableMap(includeNotedFromInventory)
			: Collections.emptyMap();
		return ShoppingListCalculator.fromSingleRecipe(
			recipe, actions, owned, prices, nameToIdIndex, mode, customPrices, sortOrder);
	}

	private static long computeCraftsFromMutablePool(CraftingRecipe recipe, Map<Integer, Double> pool)
	{
		long cap = Long.MAX_VALUE;
		for (RecipeInput in : recipe.getInputs())
		{
			double have = pool.getOrDefault(in.getItemId(), 0.0);
			if (!in.isConsumed())
			{
				if (have < Math.max(1.0, in.getQuantity()))
				{
					return 0L;
				}
				continue;
			}
			long possible = (long) Math.floor((have + 1e-9) / Math.max(1e-9, in.getQuantity()));
			cap = Math.min(cap, possible);
		}
		return cap == Long.MAX_VALUE ? 0L : cap;
	}

	private static void consumeFromMutablePool(CraftingRecipe recipe, Map<Integer, Double> pool, long crafts)
	{
		for (RecipeInput in : recipe.getInputs())
		{
			if (!in.isConsumed())
			{
				continue;
			}
			double cur = pool.getOrDefault(in.getItemId(), 0.0);
			double next = Math.max(0.0, cur - (in.getQuantity() * crafts));
			pool.put(in.getItemId(), next);
		}
	}

	private static Long resolveUnitBuyPrice(
		int itemId,
		String itemName,
		Map<Integer, ItemPrice> prices,
		Map<String, Integer> nameToIdIndex,
		PriceCalculationMode mode,
		Map<Integer, CustomPriceOverride> customPrices)
	{
		if (itemId == ItemID.COINS_995)
		{
			return 1L;
		}
		if (customPrices != null)
		{
			CustomPriceOverride ov = customPrices.get(itemId);
			if (ov != null && ov.getBuyPrice() != null && ov.getBuyPrice() > 0L)
			{
				return ov.getBuyPrice();
			}
		}
		ItemPrice ip = ProfitCalculator.resolveItemPrice(itemId, itemName, prices, nameToIdIndex);
		if (ip == null)
		{
			return null;
		}
		return mode == PriceCalculationMode.CUSTOM ? ip.getAverageMarketPrice() : ip.resolveBuyPrice(mode, null);
	}

	private static Long resolveUnitSellPrice(
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
		return mode == PriceCalculationMode.CUSTOM ? ip.getAverageMarketPrice() : ip.resolveSellPrice(mode, null);
	}
}
