package com.craftwise.calculator;

import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.CraftingResult.XpRateSource;
import com.craftwise.util.XPTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Calculates required actions, materials, GP cost/profit, and time to reach a target
 * Crafting level or XP goal using a selected {@link CraftingRecipe}.
 */
public final class XPCalculator
{
	@Data
	@Builder
	public static class MaterialRequirement
	{
		private final int itemId;
		private final String itemName;
		private final double exactQuantity;
		private final long purchaseQuantity;
	}

	@Data
	@Builder
	public static class XPGoalProjection
	{
		private final boolean available;
		private final CraftingRecipe recipe;
		private final int startLevel;
		private final long startXp;
		private final int targetLevel;
		private final long targetXp;
		private final long xpRequired;
		private final long actionsRequired;
		private final double totalXpGained;
		private final double totalMaterialCost;
		private final double totalGrossRevenue;
		private final double totalGeTax;
		private final double totalNetProfit;
		private final double xpPerHour;
		private final XpRateSource xpRateSource;
		private final double estimatedHours;
		@Builder.Default
		private final List<MaterialRequirement> materials = Collections.emptyList();
	}

	private XPCalculator()
	{
	}

	public static XPGoalProjection calculate(
		CraftingResult result,
		long currentXp,
		int targetLevel)
	{
		if (result == null || result.getRecipe() == null)
		{
			return XPGoalProjection.builder().available(false).build();
		}

		CraftingRecipe recipe = result.getRecipe();
		long safeStartXp = Math.max(0L, Math.min(XPTable.MAX_XP, currentXp));
		int startLvl = XPTable.getLevelForXp(safeStartXp);
		int safeTarget = XPTable.clampLevel(targetLevel);
		long targetXp = XPTable.getXpForLevel(safeTarget);
		long xpReq = Math.max(0L, targetXp - safeStartXp);

		double xpPerAction = recipe.getXpPerAction();
		if (xpReq == 0L || xpPerAction <= 0.0)
		{
			return XPGoalProjection.builder()
				.available(result.isPriceDataComplete())
				.recipe(recipe)
				.startLevel(startLvl)
				.startXp(safeStartXp)
				.targetLevel(safeTarget)
				.targetXp(targetXp)
				.xpRequired(xpReq)
				.actionsRequired(0L)
				.xpPerHour(result.getXpPerHour())
				.xpRateSource(result.getXpRateSource())
				.build();
		}

		long actions = (long) Math.ceil(xpReq / xpPerAction);
		double totalXp = actions * xpPerAction;
		double hours = result.getActionsPerHour() > 0.0 ? actions / result.getActionsPerHour() : 0.0;

		List<MaterialRequirement> materials = new ArrayList<>();
		for (RecipeInput input : recipe.getInputs())
		{
			double exactQty = actions * input.getQuantity();
			long purchaseQty = (long) Math.ceil(exactQty - 1e-9);
			materials.add(MaterialRequirement.builder()
				.itemId(input.getItemId())
				.itemName(input.getItemName())
				.exactQuantity(exactQty)
				.purchaseQuantity(purchaseQty)
				.build());
		}

		if (!result.isPriceDataComplete())
		{
			return XPGoalProjection.builder()
				.available(false)
				.recipe(recipe)
				.startLevel(startLvl)
				.startXp(safeStartXp)
				.targetLevel(safeTarget)
				.targetXp(targetXp)
				.xpRequired(xpReq)
				.actionsRequired(actions)
				.totalXpGained(totalXp)
				.xpPerHour(result.getXpPerHour())
				.xpRateSource(result.getXpRateSource())
				.estimatedHours(hours)
				.materials(Collections.unmodifiableList(materials))
				.build();
		}

		return XPGoalProjection.builder()
			.available(true)
			.recipe(recipe)
			.startLevel(startLvl)
			.startXp(safeStartXp)
			.targetLevel(safeTarget)
			.targetXp(targetXp)
			.xpRequired(xpReq)
			.actionsRequired(actions)
			.totalXpGained(totalXp)
			.totalMaterialCost(actions * result.getMaterialCostPerCraft())
			.totalGrossRevenue(actions * result.getGrossRevenuePerCraft())
			.totalGeTax(actions * result.getGeTaxPerCraft())
			.totalNetProfit(actions * result.getProfitPerCraft())
			.xpPerHour(result.getXpPerHour())
			.xpRateSource(result.getXpRateSource())
			.estimatedHours(hours)
			.materials(Collections.unmodifiableList(materials))
			.build();
	}
}
