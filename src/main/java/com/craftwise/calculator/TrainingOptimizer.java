package com.craftwise.calculator;

import com.craftwise.model.CraftingResult;
import com.craftwise.model.TrainingPlan;
import com.craftwise.model.TrainingPlan.OptimizationObjective;
import com.craftwise.model.TrainingPlan.TrainingStage;
import com.craftwise.util.XPTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Optimizes multi-stage Crafting progression from the player's current XP/level to a target level.
 * Respects budget limits, minimum XP/hour, and maximum acceptable loss, combining multiple methods
 * across unlock breakpoints (e.g., Level 1-20, 20-43, 43-66, 66-84, 84-99).
 */
public final class TrainingOptimizer
{
	private TrainingOptimizer()
	{
	}

	public static TrainingPlan optimize(
		List<CraftingResult> evaluatedRecipes,
		long startXp,
		int targetLevel,
		long availableBudgetGp,
		OptimizationObjective objective,
		long maxAcceptableLossGp,
		double minDesiredXpPerHour)
	{
		long safeStartXp = Math.max(0L, Math.min(XPTable.MAX_XP, startXp));
		int startLvl = XPTable.getLevelForXp(safeStartXp);
		int safeTargetLvl = XPTable.clampLevel(targetLevel);
		long targetXp = XPTable.getXpForLevel(safeTargetLvl);
		long xpReq = Math.max(0L, targetXp - safeStartXp);
		OptimizationObjective safeObjective = objective != null ? objective : OptimizationObjective.MAX_XP_WITHIN_BUDGET;

		if (xpReq <= 0L)
		{
			return TrainingPlan.builder()
				.startLevel(startLvl)
				.startXp(safeStartXp)
				.targetLevel(safeTargetLvl)
				.targetXp(targetXp)
				.xpRequired(0L)
				.availableBudgetGp(availableBudgetGp)
				.objective(safeObjective)
				.feasible(true)
				.statusMessage("Target level already reached.")
				.stages(Collections.emptyList())
				.build();
		}

		// Collect valid XP-granting candidates with complete price data
		List<CraftingResult> candidates = new ArrayList<>();
		TreeSet<Integer> breakpoints = new TreeSet<>();
		breakpoints.add(startLvl);
		breakpoints.add(safeTargetLvl);

		if (evaluatedRecipes != null)
		{
			for (CraftingResult r : evaluatedRecipes)
			{
				if (r == null || !r.isPriceDataComplete())
				{
					continue;
				}
				if (r.getXpPerAction() <= 0.0 || r.getXpPerHour() < minDesiredXpPerHour)
				{
					continue;
				}
				if (r.getMaterialCostPerCraft() <= 0.0)
				{
					continue;
				}
				candidates.add(r);
				int req = r.getRecipe().getRequiredLevel();
				if (req > startLvl && req < safeTargetLvl)
				{
					breakpoints.add(req);
				}
			}
		}

		if (candidates.isEmpty())
		{
			return TrainingPlan.builder()
				.startLevel(startLvl)
				.startXp(safeStartXp)
				.targetLevel(safeTargetLvl)
				.targetXp(targetXp)
				.xpRequired(xpReq)
				.availableBudgetGp(availableBudgetGp)
				.objective(safeObjective)
				.feasible(false)
				.statusMessage("No methods match the selected price data and minimum XP/hr constraints.")
				.stages(Collections.emptyList())
				.build();
		}

		List<Integer> bpList = new ArrayList<>(breakpoints);
		List<TrainingStage> rawStages = new ArrayList<>();

		double remainingBudget = Math.max(0L, availableBudgetGp);
		double cumulativeLoss = 0.0;
		long runningXp = safeStartXp;
		boolean budgetExhausted = false;

		for (int i = 0; i < bpList.size() - 1; i++)
		{
			int segFromLvl = bpList.get(i);
			int segToLvl = bpList.get(i + 1);
			long segStartXp = Math.max(runningXp, XPTable.getXpForLevel(segFromLvl));
			long segEndXp = XPTable.getXpForLevel(segToLvl);
			long segXpNeeded = Math.max(0L, segEndXp - segStartXp);
			if (segXpNeeded <= 0L)
			{
				continue;
			}

			CraftingResult best = pickBestForSegment(
				candidates, segFromLvl, segXpNeeded, remainingBudget,
				safeObjective, maxAcceptableLossGp, cumulativeLoss);

			if (best == null)
			{
				// Try partial actions with remaining budget before stopping
				CraftingResult partialBest = pickBestPartialForBudget(
					candidates, segFromLvl, remainingBudget, safeObjective, maxAcceptableLossGp, cumulativeLoss);
				if (partialBest != null)
				{
					long maxAffordable = (long) Math.floor(remainingBudget / partialBest.getMaterialCostPerCraft());
					if (maxAffordable > 0L)
					{
						double gainedXp = maxAffordable * partialBest.getXpPerAction();
						int reachedLvl = XPTable.getLevelForXp(Math.min(targetXp, segStartXp + Math.round(gainedXp)));
						double matCost = maxAffordable * partialBest.getMaterialCostPerCraft();
						double grossRev = maxAffordable * partialBest.getGrossRevenuePerCraft();
						double tax = maxAffordable * partialBest.getGeTaxPerCraft();
						double netProfit = maxAffordable * partialBest.getProfitPerCraft();
						double hours = partialBest.getActionsPerHour() > 0.0 ? maxAffordable / partialBest.getActionsPerHour() : 0.0;

						rawStages.add(TrainingStage.builder()
							.fromLevel(segFromLvl)
							.toLevel(Math.max(segFromLvl, reachedLvl))
							.recipe(partialBest.getRecipe())
							.result(partialBest)
							.actionsNeeded(maxAffordable)
							.xpGained(gainedXp)
							.totalMaterialCost(matCost)
							.totalGrossRevenue(grossRev)
							.totalGeTax(tax)
							.totalNetProfit(netProfit)
							.estimatedHours(hours)
							.build());

						remainingBudget -= matCost;
						runningXp += Math.round(gainedXp);
					}
				}
				budgetExhausted = true;
				break;
			}

			long actions = (long) Math.ceil(segXpNeeded / best.getXpPerAction());
			double gainedXp = actions * best.getXpPerAction();
			double matCost = actions * best.getMaterialCostPerCraft();
			double grossRev = actions * best.getGrossRevenuePerCraft();
			double tax = actions * best.getGeTaxPerCraft();
			double netProfit = actions * best.getProfitPerCraft();
			double hours = best.getActionsPerHour() > 0.0 ? actions / best.getActionsPerHour() : 0.0;

			rawStages.add(TrainingStage.builder()
				.fromLevel(segFromLvl)
				.toLevel(segToLvl)
				.recipe(best.getRecipe())
				.result(best)
				.actionsNeeded(actions)
				.xpGained(gainedXp)
				.totalMaterialCost(matCost)
				.totalGrossRevenue(grossRev)
				.totalGeTax(tax)
				.totalNetProfit(netProfit)
				.estimatedHours(hours)
				.build());

			remainingBudget -= matCost;
			if (netProfit < 0.0)
			{
				cumulativeLoss += (-netProfit);
			}
			runningXp += Math.round(gainedXp);
			if (runningXp >= targetXp)
			{
				break;
			}
		}

		List<TrainingStage> mergedStages = mergeAdjacentStages(rawStages);
		long totalActions = 0L;
		double totalXp = 0.0;
		double totalCost = 0.0;
		double totalGross = 0.0;
		double totalTax = 0.0;
		double totalProfit = 0.0;
		double totalHours = 0.0;
		double peakStageCost = 0.0;

		for (TrainingStage st : mergedStages)
		{
			totalActions += st.getActionsNeeded();
			totalXp += st.getXpGained();
			totalCost += st.getTotalMaterialCost();
			totalGross += st.getTotalGrossRevenue();
			totalTax += st.getTotalGeTax();
			totalProfit += st.getTotalNetProfit();
			totalHours += st.getEstimatedHours();
			if (st.getTotalMaterialCost() > peakStageCost)
			{
				peakStageCost = st.getTotalMaterialCost();
			}
		}

		boolean feasible = !budgetExhausted && (safeStartXp + Math.round(totalXp) >= targetXp) && !mergedStages.isEmpty();
		double avgXpHr = totalHours > 0.0 ? totalXp / totalHours : 0.0;

		String status;
		if (feasible)
		{
			status = "Complete " + mergedStages.size() + "-stage plan within "
				+ com.craftwise.util.CurrencyFormatter.formatGp(availableBudgetGp, true) + " budget.";
		}
		else if (mergedStages.isEmpty())
		{
			status = "Budget or constraints too restrictive to start any qualifying Crafting method.";
		}
		else
		{
			int reached = XPTable.getLevelForXp(safeStartXp + Math.round(totalXp));
			status = "Budget reached at Level " + reached + " (" + Math.round((totalXp * 100.0) / xpReq)
				+ "% of XP goal). Increase budget or choose a cheaper method.";
		}

		return TrainingPlan.builder()
			.startLevel(startLvl)
			.startXp(safeStartXp)
			.targetLevel(safeTargetLvl)
			.targetXp(targetXp)
			.xpRequired(xpReq)
			.availableBudgetGp(availableBudgetGp)
			.objective(safeObjective)
			.feasible(feasible)
			.statusMessage(status)
			.stages(Collections.unmodifiableList(mergedStages))
			.totalActions(totalActions)
			.totalXpGained(totalXp)
			.totalMaterialCost(totalCost)
			.totalGrossRevenue(totalGross)
			.totalGeTax(totalTax)
			.totalNetProfit(totalProfit)
			.totalEstimatedHours(totalHours)
			.peakUpfrontCost(peakStageCost)
			.averageXpPerHour(avgXpHr)
			.build();
	}

	private static CraftingResult pickBestForSegment(
		List<CraftingResult> candidates,
		int availableLevel,
		long segXpNeeded,
		double remainingBudget,
		OptimizationObjective objective,
		long maxAcceptableLossGp,
		double cumulativeLossSoFar)
	{
		List<CraftingResult> eligible = new ArrayList<>();
		for (CraftingResult r : candidates)
		{
			if (r.getRecipe().getRequiredLevel() > availableLevel)
			{
				continue;
			}
			long actions = (long) Math.ceil(segXpNeeded / r.getXpPerAction());
			double cost = actions * r.getMaterialCostPerCraft();
			if (cost > remainingBudget + 1e-6)
			{
				continue;
			}
			double profit = actions * r.getProfitPerCraft();
			if (maxAcceptableLossGp >= 0L && profit < 0.0 && (cumulativeLossSoFar - profit) > maxAcceptableLossGp + 1e-6)
			{
				continue;
			}
			eligible.add(r);
		}
		if (eligible.isEmpty())
		{
			return null;
		}
		eligible.sort(comparatorForSegment(objective));
		return eligible.get(0);
	}

	private static CraftingResult pickBestPartialForBudget(
		List<CraftingResult> candidates,
		int availableLevel,
		double remainingBudget,
		OptimizationObjective objective,
		long maxAcceptableLossGp,
		double cumulativeLossSoFar)
	{
		List<CraftingResult> eligible = new ArrayList<>();
		for (CraftingResult r : candidates)
		{
			if (r.getRecipe().getRequiredLevel() > availableLevel)
			{
				continue;
			}
			if (r.getMaterialCostPerCraft() > remainingBudget)
			{
				continue;
			}
			if (maxAcceptableLossGp >= 0L && r.getProfitPerCraft() < 0.0
				&& (cumulativeLossSoFar - r.getProfitPerCraft()) > maxAcceptableLossGp)
			{
				continue;
			}
			eligible.add(r);
		}
		if (eligible.isEmpty())
		{
			return null;
		}
		eligible.sort(comparatorForSegment(objective));
		return eligible.get(0);
	}

	private static Comparator<CraftingResult> comparatorForSegment(OptimizationObjective objective)
	{
		switch (objective)
		{
			case FASTEST_WITHIN_BUDGET:
			case MAX_XP_WITHIN_BUDGET:
				return Comparator.comparingDouble(CraftingResult::getXpPerHour).reversed()
					.thenComparing(Comparator.comparingDouble(CraftingResult::getGpPerXp).reversed());
			case MAX_PROFIT_WHILE_TRAINING:
				return Comparator.comparingDouble(CraftingResult::getProfitPerHour).reversed()
					.thenComparing(Comparator.comparingDouble(CraftingResult::getXpPerHour).reversed());
			case MIN_COST_TO_TARGET:
			case LOWEST_LOSS:
				// Highest gpPerXp (most profitable or smallest GP loss per XP gained)
				return Comparator.comparingDouble(CraftingResult::getGpPerXp).reversed()
					.thenComparing(Comparator.comparingDouble(CraftingResult::getXpPerHour).reversed());
			default:
				return Comparator.comparingDouble(CraftingResult::getXpPerHour).reversed();
		}
	}

	private static List<TrainingStage> mergeAdjacentStages(List<TrainingStage> raw)
	{
		if (raw.size() <= 1)
		{
			return raw;
		}
		List<TrainingStage> merged = new ArrayList<>();
		TrainingStage prev = raw.get(0);
		for (int i = 1; i < raw.size(); i++)
		{
			TrainingStage curr = raw.get(i);
			if (prev.getRecipe().getId().equals(curr.getRecipe().getId()))
			{
				prev = TrainingStage.builder()
					.fromLevel(prev.getFromLevel())
					.toLevel(curr.getToLevel())
					.recipe(prev.getRecipe())
					.result(prev.getResult())
					.actionsNeeded(prev.getActionsNeeded() + curr.getActionsNeeded())
					.xpGained(prev.getXpGained() + curr.getXpGained())
					.totalMaterialCost(prev.getTotalMaterialCost() + curr.getTotalMaterialCost())
					.totalGrossRevenue(prev.getTotalGrossRevenue() + curr.getTotalGrossRevenue())
					.totalGeTax(prev.getTotalGeTax() + curr.getTotalGeTax())
					.totalNetProfit(prev.getTotalNetProfit() + curr.getTotalNetProfit())
					.estimatedHours(prev.getEstimatedHours() + curr.getEstimatedHours())
					.build();
			}
			else
			{
				merged.add(prev);
				prev = curr;
			}
		}
		merged.add(prev);
		return merged;
	}
}
