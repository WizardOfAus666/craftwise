package com.craftwise.calculator;

import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.TrainingPlan;
import com.craftwise.model.TrainingPlan.OptimizationObjective;
import com.craftwise.model.TrainingPlan.TrainingStage;
import com.craftwise.util.XPTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Phase 9: Training Optimiser 2.0.
 * Evaluates Plans A–E side-by-side over verified recipes, enforcing F2P/members,
 * explicit temporary Crafting boosts (only on boostable recipes), quest/unlock gating
 * (never assumes completed quests/unlocks), and total capital budget constraints.
 */
public final class TrainingPlanComparisonCalculator
{
	@Getter
	@RequiredArgsConstructor
	public enum PlanStrategy
	{
		PLAN_A_FASTEST("Plan A: Fastest XP", "Maximizes XP/hour at current prices."),
		PLAN_B_CHEAPEST("Plan B: Cheapest Loss", "Minimizes total GP cost to reach target level."),
		PLAN_C_MOST_PROFITABLE("Plan C: Most Profitable", "Maximizes total net GP earned while reaching target."),
		PLAN_D_BEST_BALANCE("Plan D: Best Balance", "Balances high XP/hour with favorable GP/XP cost."),
		PLAN_E_UNDER_BUDGET("Plan E: Best Under Budget", "Fastest feasible method within available GP budget.");

		private final String label;
		private final String description;
	}

	@Getter
	@RequiredArgsConstructor
	public enum LevelRangePreset
	{
		CURRENT_TO_99("Current -> 99"),
		RANGE_70_99("70 -> 99"),
		RANGE_80_99("80 -> 99"),
		RANGE_90_99("90 -> 99"),
		CUSTOM("Custom Range");

		private final String label;
	}

	@Data
	@Builder
	public static class OptimizerOptions
	{
		private final int currentLevel;
		private final long currentXp;
		private final int targetLevel;
		private final int temporaryBoost; // 0 to +5 explicit boost
		private final boolean membersAllowed;
		private final boolean includeQuestLocked;
		private final boolean includeUnlockRequired;
		private final long availableBudgetGp; // <= 0 means unlimited for non-budget plans, or explicit check for Plan E
		@Builder.Default
		private final boolean enforceProgressionSteps = true;
	}

	@Data
	@Builder
	public static class ComparedPlan
	{
		private final PlanStrategy strategy;
		private final TrainingPlan plan;
		private final boolean feasible;
		private final String statusReason;
	}

	private TrainingPlanComparisonCalculator()
	{
	}

	public static List<ComparedPlan> buildSideBySidePlans(
		List<CraftingResult> allResults,
		OptimizerOptions options)
	{
		if (options == null || allResults == null || allResults.isEmpty())
		{
			return Collections.emptyList();
		}

		int startLvl = Math.max(1, Math.min(99, options.getCurrentLevel()));
		int targetLvl = Math.max(startLvl + 1, Math.min(99, options.getTargetLevel()));
		long startXp = Math.max(options.getCurrentXp(), XPTable.getXpForLevel(startLvl));
		long targetXp = XPTable.getXpForLevel(targetLvl);
		long neededXp = Math.max(0L, targetXp - startXp);
		if (neededXp <= 0L)
		{
			return Collections.emptyList();
		}

		List<CraftingResult> eligible = filterEligible(allResults, options);
		List<ComparedPlan> plans = new ArrayList<>(5);

		plans.add(buildPlanForStrategy(PlanStrategy.PLAN_A_FASTEST, eligible, startLvl, startXp, targetLvl, targetXp, options));
		plans.add(buildPlanForStrategy(PlanStrategy.PLAN_B_CHEAPEST, eligible, startLvl, startXp, targetLvl, targetXp, options));
		plans.add(buildPlanForStrategy(PlanStrategy.PLAN_C_MOST_PROFITABLE, eligible, startLvl, startXp, targetLvl, targetXp, options));
		plans.add(buildPlanForStrategy(PlanStrategy.PLAN_D_BEST_BALANCE, eligible, startLvl, startXp, targetLvl, targetXp, options));
		plans.add(buildPlanForStrategy(PlanStrategy.PLAN_E_UNDER_BUDGET, eligible, startLvl, startXp, targetLvl, targetXp, options));

		return Collections.unmodifiableList(plans);
	}

	private static List<CraftingResult> filterEligible(List<CraftingResult> results, OptimizerOptions options)
	{
		List<CraftingResult> filtered = new ArrayList<>();
		int boost = Math.max(0, Math.min(5, options.getTemporaryBoost()));
		for (CraftingResult r : results)
		{
			if (r == null || !r.isPriceDataComplete() || r.getRecipe() == null)
			{
				continue;
			}
			CraftingRecipe recipe = r.getRecipe();
			if (recipe.getVerificationStatus() == VerificationStatus.UNSUPPORTED)
			{
				continue;
			}
			if (recipe.getXpPerAction() <= 0.0)
			{
				continue;
			}
			if (!options.isMembersAllowed() && recipe.isMembers())
			{
				continue;
			}
			// Do not assume quest/unlock completions unless user explicitly enabled them
			if (!options.isIncludeQuestLocked() && recipe.hasQuestRequirement())
			{
				continue;
			}
			if (!options.isIncludeUnlockRequired() && recipe.hasUnlockRequirement())
			{
				continue;
			}
			int effectiveStartCap = recipe.isBoostable()
				? Math.min(99, options.getTargetLevel() - 1 + boost)
				: (options.getTargetLevel() - 1);
			if (recipe.getRequiredLevel() > effectiveStartCap)
			{
				continue;
			}
			filtered.add(r);
		}
		return filtered;
	}

	private static ComparedPlan buildPlanForStrategy(
		PlanStrategy strategy,
		List<CraftingResult> eligible,
		int startLevel,
		long startXp,
		int targetLevel,
		long targetXp,
		OptimizerOptions options)
	{
		if (eligible.isEmpty())
		{
			return ComparedPlan.builder()
				.strategy(strategy)
				.feasible(false)
				.statusReason("No verified recipes match level, members, and unlock filters.")
				.build();
		}

		// Collect transition milestones where higher-level methods unlock inside [startLevel, targetLevel)
		List<Integer> breakpoints = new ArrayList<>();
		breakpoints.add(startLevel);
		if (options.isEnforceProgressionSteps())
		{
			int boost = Math.max(0, Math.min(5, options.getTemporaryBoost()));
			for (CraftingResult r : eligible)
			{
				int effectiveUnlockLvl = r.getRecipe().isBoostable()
					? Math.max(1, r.getRecipe().getRequiredLevel() - boost)
					: r.getRecipe().getRequiredLevel();
				if (effectiveUnlockLvl > startLevel && effectiveUnlockLvl < targetLevel && !breakpoints.contains(effectiveUnlockLvl))
				{
					breakpoints.add(effectiveUnlockLvl);
				}
			}
			Collections.sort(breakpoints);
		}

		long cursorXp = startXp;
		int cursorLvl = startLevel;
		List<TrainingStage> stages = new ArrayList<>();
		double totalHours = 0.0;
		double totalProfit = 0.0;
		double totalMaterialCost = 0.0;
		double totalGrossRev = 0.0;
		double totalGeTax = 0.0;
		long totalActions = 0L;

		for (int b = 0; b < breakpoints.size(); b++)
		{
			int segStartLvl = breakpoints.get(b);
			int segEndLvl = (b + 1 < breakpoints.size()) ? breakpoints.get(b + 1) : targetLevel;
			long segTargetXp = Math.min(targetXp, XPTable.getXpForLevel(segEndLvl));
			long segXpNeeded = Math.max(0L, segTargetXp - cursorXp);
			if (segXpNeeded <= 0L)
			{
				continue;
			}

			int effLvl = Math.max(cursorLvl, segStartLvl);
			CraftingResult best = pickBestForSegment(strategy, eligible, effLvl, segXpNeeded, options);
			if (best == null)
			{
				return ComparedPlan.builder()
					.strategy(strategy)
					.feasible(false)
					.statusReason(strategy == PlanStrategy.PLAN_E_UNDER_BUDGET
						? "No eligible method fits within your " + options.getAvailableBudgetGp() + " GP budget."
						: "No unlocked recipe available at Crafting level " + effLvl + ".")
					.build();
			}

			// Merge consecutive stages if the same recipe stays optimal across adjacent level thresholds
			long actions = (long) Math.ceil(segXpNeeded / best.getXpPerAction());
			double xpGained = actions * best.getXpPerAction();
			double matCost = actions * best.getMaterialCostPerCraft();
			double grossRev = actions * best.getGrossRevenuePerCraft();
			double geTax = actions * best.getGeTaxPerCraft();
			double netProfit = actions * best.getProfitPerCraft();
			double hrs = best.getActionsPerHour() > 0.0 ? actions / best.getActionsPerHour() : 0.0;

			if (!stages.isEmpty() && Objects.equals(stages.get(stages.size() - 1).getRecipe().getId(), best.getRecipe().getId()))
			{
				TrainingStage prev = stages.remove(stages.size() - 1);
				long combinedActions = prev.getActionsNeeded() + actions;
				double combinedXp = prev.getXpGained() + xpGained;
				double combinedCost = prev.getTotalMaterialCost() + matCost;
				double combinedProfit = prev.getTotalNetProfit() + netProfit;
				double combinedHours = prev.getEstimatedHours() + hrs;
				stages.add(TrainingStage.builder()
					.fromLevel(prev.getFromLevel())
					.toLevel(segEndLvl)
					.recipe(best.getRecipe())
					.result(best)
					.actionsNeeded(combinedActions)
					.xpGained(combinedXp)
					.totalMaterialCost(combinedCost)
					.totalNetProfit(combinedProfit)
					.estimatedHours(combinedHours)
					.build());
			}
			else
			{
				stages.add(TrainingStage.builder()
					.fromLevel(segStartLvl)
					.toLevel(segEndLvl)
					.recipe(best.getRecipe())
					.result(best)
					.actionsNeeded(actions)
					.xpGained(xpGained)
					.totalMaterialCost(matCost)
					.totalNetProfit(netProfit)
					.estimatedHours(hrs)
					.build());
			}

			totalActions += actions;
			totalHours += hrs;
			totalProfit += netProfit;
			totalMaterialCost += matCost;
			totalGrossRev += grossRev;
			totalGeTax += geTax;
			cursorXp += Math.round(xpGained);
			cursorLvl = segEndLvl;
			if (cursorXp >= targetXp)
			{
				break;
			}
		}

		if (stages.isEmpty())
		{
			return ComparedPlan.builder()
				.strategy(strategy)
				.feasible(false)
				.statusReason("Could not construct training stages.")
				.build();
		}

		// For Plan E, check net capital loss feasibility cleanly
		if (strategy == PlanStrategy.PLAN_E_UNDER_BUDGET && options.getAvailableBudgetGp() > 0L)
		{
			double netRequiredGp = totalProfit < 0.0 ? -totalProfit : 0.0;
			if (netRequiredGp > options.getAvailableBudgetGp())
			{
				return ComparedPlan.builder()
					.strategy(strategy)
					.feasible(false)
					.statusReason(String.format(java.util.Locale.US,
						"Cheapest plan net loss (%,.0f GP) exceeds %,d GP budget.",
						netRequiredGp, options.getAvailableBudgetGp()))
					.build();
			}
		}

		long totalXpGained = Math.max(0L, cursorXp - startXp);
		double avgXpPerHr = totalHours > 0.0 ? totalXpGained / totalHours : 0.0;

		TrainingPlan builtPlan = TrainingPlan.builder()
			.objective(mapStrategyToObjective(strategy))
			.startLevel(startLevel)
			.targetLevel(targetLevel)
			.xpRequired(targetXp - startXp)
			.totalEstimatedHours(totalHours)
			.totalMaterialCost(totalMaterialCost)
			.totalGrossRevenue(totalGrossRev)
			.totalGeTax(totalGeTax)
			.totalNetProfit(totalProfit)
			.totalActions(totalActions)
			.averageXpPerHour(avgXpPerHr)
			.feasible(true)
			.statusMessage(strategy.getDescription())
			.stages(Collections.unmodifiableList(stages))
			.build();

		return ComparedPlan.builder()
			.strategy(strategy)
			.plan(builtPlan)
			.feasible(true)
			.statusReason("Ready")
			.build();
	}

	private static CraftingResult pickBestForSegment(
		PlanStrategy strategy,
		List<CraftingResult> eligible,
		int effectiveLevel,
		long xpNeeded,
		OptimizerOptions options)
	{
		int boost = Math.max(0, Math.min(5, options.getTemporaryBoost()));
		List<CraftingResult> unlocked = new ArrayList<>();
		for (CraftingResult r : eligible)
		{
			int usableLvl = r.getRecipe().isBoostable() ? effectiveLevel + boost : effectiveLevel;
			if (r.getRecipe().getRequiredLevel() <= usableLvl && r.getXpPerAction() > 0.0)
			{
				unlocked.add(r);
			}
		}
		if (unlocked.isEmpty())
		{
			return null;
		}

		switch (strategy)
		{
			case PLAN_A_FASTEST:
				return Collections.max(unlocked, Comparator.comparingDouble(CraftingResult::getXpPerHour));

			case PLAN_B_CHEAPEST:
				return Collections.max(unlocked, Comparator.comparingDouble(r ->
				{
					double gpPerXp = r.getGpPerXp();
					return Double.isNaN(gpPerXp) ? -Double.MAX_VALUE : gpPerXp;
				}));

			case PLAN_C_MOST_PROFITABLE:
				return Collections.max(unlocked, Comparator.comparingDouble(r ->
				{
					long acts = (long) Math.ceil(xpNeeded / r.getXpPerAction());
					return acts * r.getProfitPerCraft();
				}));

			case PLAN_D_BEST_BALANCE:
			{
				double maxXpHr = unlocked.stream().mapToDouble(CraftingResult::getXpPerHour).max().orElse(1.0);
				if (maxXpHr <= 0.0)
				{
					maxXpHr = 1.0;
				}
				final double normMaxXp = maxXpHr;
				return Collections.max(unlocked, Comparator.comparingDouble(r ->
				{
					double xpScore = r.getXpPerHour() / normMaxXp; // 0..1
					double gpPerXp = Double.isNaN(r.getGpPerXp()) ? -20.0 : Math.max(-20.0, Math.min(10.0, r.getGpPerXp()));
					double gpScore = (gpPerXp + 20.0) / 30.0; // normalized ~0..1
					return (0.55 * xpScore) + (0.45 * gpScore);
				}));
			}

			case PLAN_E_UNDER_BUDGET:
			{
				long budget = options.getAvailableBudgetGp();
				List<CraftingResult> affordable = new ArrayList<>();
				for (CraftingResult r : unlocked)
				{
					long acts = (long) Math.ceil(xpNeeded / r.getXpPerAction());
					double netLoss = -(acts * r.getProfitPerCraft());
					if (budget <= 0L || netLoss <= budget)
					{
						affordable.add(r);
					}
				}
				if (affordable.isEmpty())
				{
					// Fall back to cheapest GP/XP method if none strictly fits full budget upfront
					return Collections.max(unlocked, Comparator.comparingDouble(r ->
						Double.isNaN(r.getGpPerXp()) ? -Double.MAX_VALUE : r.getGpPerXp()));
				}
				return Collections.max(affordable, Comparator.comparingDouble(CraftingResult::getXpPerHour));
			}

			default:
				return unlocked.get(0);
		}
	}

	private static OptimizationObjective mapStrategyToObjective(PlanStrategy strategy)
	{
		switch (strategy)
		{
			case PLAN_A_FASTEST:
				return OptimizationObjective.MAX_XP_WITHIN_BUDGET;
			case PLAN_B_CHEAPEST:
				return OptimizationObjective.LOWEST_LOSS;
			case PLAN_C_MOST_PROFITABLE:
				return OptimizationObjective.MAX_PROFIT_WHILE_TRAINING;
			case PLAN_E_UNDER_BUDGET:
				return OptimizationObjective.FASTEST_WITHIN_BUDGET;
			case PLAN_D_BEST_BALANCE:
			default:
				return OptimizationObjective.MIN_COST_TO_TARGET;
		}
	}
}
