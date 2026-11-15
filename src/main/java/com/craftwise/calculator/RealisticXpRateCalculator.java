package com.craftwise.calculator;

import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult.XpRateSource;
import lombok.Builder;
import lombok.Data;

/**
 * Computes transparent crafting throughput (actions/min, items/inventory, actions/hour, XP/hour)
 * distinguishing explicitly between user-configured overrides and realistic bank-inclusive estimates.
 */
public final class RealisticXpRateCalculator
{
	@Data
	@Builder
	public static class XpRateBreakdown
	{
		private final double actionsPerHour;
		private final double actionsPerMinute;
		private final int itemsPerInventory;
		private final double xpPerAction;
		private final double xpPerHour;
		private final XpRateSource xpRateSource;
		private final String modelExplanation;
	}

	private RealisticXpRateCalculator()
	{
	}

	public static XpRateBreakdown calculate(CraftingRecipe recipe, double customActionsPerHour)
	{
		int perInv = Math.max(1, InventoryCalculator.calculateCraftsPerInventory(recipe));
		double xpPerAction = Math.max(0.0, recipe.getXpPerAction());

		if (customActionsPerHour > 0.0)
		{
			return XpRateBreakdown.builder()
				.actionsPerHour(customActionsPerHour)
				.actionsPerMinute(customActionsPerHour / 60.0)
				.itemsPerInventory(perInv)
				.xpPerAction(xpPerAction)
				.xpPerHour(customActionsPerHour * xpPerAction)
				.xpRateSource(XpRateSource.USER_CONFIGURED)
				.modelExplanation(String.format(
					"User override: %.0f actions/hr (%.1f/min), %d actions/inv.",
					customActionsPerHour, customActionsPerHour / 60.0, perInv))
				.build();
		}

		double baselineActionsPerHour = Math.max(1.0, recipe.getEstimatedActionsPerHour());
		double actionsPerMin = baselineActionsPerHour / 60.0;
		double xpPerHour = baselineActionsPerHour * xpPerAction;

		XpRateSource source = recipe.getEstimatedActionsPerHour() > 0
			? XpRateSource.ESTIMATED
			: XpRateSource.ESTIMATED;

		String explanation = String.format(
			"Estimated practical rate: %.0f actions/hr (%.1f/min) across %d crafts/inv (includes banking overhead).",
			baselineActionsPerHour, actionsPerMin, perInv);

		return XpRateBreakdown.builder()
			.actionsPerHour(baselineActionsPerHour)
			.actionsPerMinute(actionsPerMin)
			.itemsPerInventory(perInv)
			.xpPerAction(xpPerAction)
			.xpPerHour(xpPerHour)
			.xpRateSource(source)
			.modelExplanation(explanation)
			.build();
	}
}
