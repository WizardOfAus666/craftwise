package com.craftwise.model;

import com.craftwise.data.CraftingRecipe;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Represents an optimized multi-stage Crafting training plan from a starting level/XP
 * to a target level, respecting budget and constraint preferences.
 */
@Data
@Builder
public class TrainingPlan
{
	public enum OptimizationObjective
	{
		MAX_XP_WITHIN_BUDGET("Max XP Within Budget"),
		MIN_COST_TO_TARGET("Lowest Cost to Target"),
		MAX_PROFIT_WHILE_TRAINING("Max Profit While Training"),
		FASTEST_WITHIN_BUDGET("Fastest XP Within Budget"),
		LOWEST_LOSS("Smallest Possible Loss");

		private final String label;

		OptimizationObjective(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Data
	@Builder
	public static class TrainingStage
	{
		private final int fromLevel;
		private final int toLevel;
		private final CraftingRecipe recipe;
		private final CraftingResult result;
		private final long actionsNeeded;
		private final double xpGained;
		private final double totalMaterialCost;
		private final double totalGrossRevenue;
		private final double totalGeTax;
		private final double totalNetProfit;
		private final double estimatedHours;
	}

	private final int startLevel;
	private final long startXp;
	private final int targetLevel;
	private final long targetXp;
	private final long xpRequired;
	private final long availableBudgetGp;
	private final OptimizationObjective objective;
	private final boolean feasible;
	private final String statusMessage;

	@Builder.Default
	private final List<TrainingStage> stages = Collections.emptyList();

	private final long totalActions;
	private final double totalXpGained;
	private final double totalMaterialCost;
	private final double totalGrossRevenue;
	private final double totalGeTax;
	private final double totalNetProfit;
	private final double totalEstimatedHours;
	private final double peakUpfrontCost;
	private final double averageXpPerHour;
}
