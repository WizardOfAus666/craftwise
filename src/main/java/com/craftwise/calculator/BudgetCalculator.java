package com.craftwise.calculator;

import com.craftwise.model.CraftingResult;
import com.craftwise.model.TrainingPlan.OptimizationObjective;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Evaluates and ranks Crafting methods strictly within a player's available GP budget.
 * Accounts for total material requirements (not just per-item margin) and never recommends
 * a method whose upfront material requirement exceeds the budget.
 */
public final class BudgetCalculator
{
	@Data
	@Builder
	public static class BudgetMethodEvaluation
	{
		private final CraftingResult result;
		private final long maxAffordableActions;
		private final long actionsNeededForTarget;
		private final boolean reachesTargetWithinBudget;
		private final double totalMaterialSpend;
		private final double totalXpAchievable;
		private final double totalNetProfit;
		private final double estimatedHours;
	}

	private BudgetCalculator()
	{
	}

	/**
	 * Evaluates all unlocked, complete-price methods against {@code budgetGp} and {@code xpToTarget}.
	 * Only returns methods where at least 1 craft can be funded within {@code budgetGp} (or for
	 * target-bound objectives, optionally filters strictly to methods that can reach the target or
	 * caps achievable crafts by budget).
	 */
	public static List<BudgetMethodEvaluation> evaluateWithinBudget(
		List<CraftingResult> allResults,
		int playerLevel,
		long xpToTarget,
		long budgetGp,
		OptimizationObjective objective,
		long maxAcceptableLossGp,
		double minDesiredXpPerHour)
	{
		if (allResults == null || allResults.isEmpty() || budgetGp <= 0L)
		{
			return List.of();
		}

		List<BudgetMethodEvaluation> evaluations = new ArrayList<>();
		for (CraftingResult r : allResults)
		{
			if (r == null || !r.isPriceDataComplete())
			{
				continue;
			}
			if (r.getRecipe().getRequiredLevel() > playerLevel)
			{
				continue;
			}
			if (r.getXpPerAction() <= 0.0 || r.getXpPerHour() < minDesiredXpPerHour)
			{
				continue;
			}
			double matCost = r.getMaterialCostPerCraft();
			if (matCost <= 0.0)
			{
				continue;
			}

			long maxAffordable = (long) Math.floor(budgetGp / matCost);
			if (maxAffordable <= 0L)
			{
				continue;
			}

			long actionsForTarget = xpToTarget > 0L
				? (long) Math.ceil(xpToTarget / r.getXpPerAction())
				: maxAffordable;
			boolean reachesTarget = xpToTarget <= 0L || maxAffordable >= actionsForTarget;
			long plannedActions = Math.min(maxAffordable, actionsForTarget);

			double totalSpend = plannedActions * matCost;
			double totalProfit = plannedActions * r.getProfitPerCraft();
			if (maxAcceptableLossGp >= 0L && (-totalProfit) > maxAcceptableLossGp)
			{
				continue;
			}

			double totalXp = plannedActions * r.getXpPerAction();
			double hours = r.getActionsPerHour() > 0.0 ? plannedActions / r.getActionsPerHour() : 0.0;

			evaluations.add(BudgetMethodEvaluation.builder()
				.result(r)
				.maxAffordableActions(maxAffordable)
				.actionsNeededForTarget(actionsForTarget)
				.reachesTargetWithinBudget(reachesTarget)
				.totalMaterialSpend(totalSpend)
				.totalXpAchievable(totalXp)
				.totalNetProfit(totalProfit)
				.estimatedHours(hours)
				.build());
		}

		Comparator<BudgetMethodEvaluation> comparator = comparatorForObjective(objective);
		evaluations.sort(comparator);
		return evaluations;
	}

	private static Comparator<BudgetMethodEvaluation> comparatorForObjective(OptimizationObjective objective)
	{
		OptimizationObjective safe = objective != null ? objective : OptimizationObjective.MAX_XP_WITHIN_BUDGET;
		switch (safe)
		{
			case MIN_COST_TO_TARGET:
				// Prefer reaching target within budget, then lowest net cost (highest net profit)
				return Comparator.comparing(BudgetMethodEvaluation::isReachesTargetWithinBudget).reversed()
					.thenComparing(Comparator.comparingDouble(BudgetMethodEvaluation::getTotalNetProfit).reversed())
					.thenComparing(Comparator.comparingDouble(BudgetMethodEvaluation::getTotalXpAchievable).reversed());
			case MAX_PROFIT_WHILE_TRAINING:
				return Comparator.comparingDouble(BudgetMethodEvaluation::getTotalNetProfit).reversed()
					.thenComparing(Comparator.comparingDouble(BudgetMethodEvaluation::getTotalXpAchievable).reversed());
			case FASTEST_WITHIN_BUDGET:
				return Comparator.comparing(BudgetMethodEvaluation::isReachesTargetWithinBudget).reversed()
					.thenComparing(Comparator.comparingDouble((BudgetMethodEvaluation e) -> e.getResult().getXpPerHour()).reversed());
			case LOWEST_LOSS:
				return Comparator.comparingDouble((BudgetMethodEvaluation e) -> -e.getResult().getGpPerXp())
					.thenComparing(Comparator.comparingDouble(BudgetMethodEvaluation::getTotalNetProfit).reversed());
			case MAX_XP_WITHIN_BUDGET:
			default:
				return Comparator.comparingDouble(BudgetMethodEvaluation::getTotalXpAchievable).reversed()
					.thenComparing(Comparator.comparingDouble((BudgetMethodEvaluation e) -> e.getResult().getXpPerHour()).reversed());
		}
	}
}
