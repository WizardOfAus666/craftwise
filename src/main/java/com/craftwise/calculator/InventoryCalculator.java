package com.craftwise.calculator;

import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.model.CraftingResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import net.runelite.api.ItemID;

/**
 * Calculates inventory capacity, trips, input materials, outputs, GE tax, net profit,
 * and XP gained for individual crafting sessions (1/5/10 inventories or arbitrary item counts).
 */
public final class InventoryCalculator
{
	public static final int OSRS_INVENTORY_SLOTS = 28;

	@Data
	@Builder
	public static class SessionMaterial
	{
		private final int itemId;
		private final String itemName;
		private final double exactQuantity;
		private final long purchaseQuantity;
		private final boolean stackable;
	}

	@Data
	@Builder
	public static class SessionOutput
	{
		private final int itemId;
		private final String itemName;
		private final double expectedQuantity;
	}

	@Data
	@Builder
	public static class InventorySessionResult
	{
		private final boolean available;
		private final CraftingRecipe recipe;
		private final int craftsPerInventory;
		private final long inventoriesNeeded;
		private final long actionsRequested;
		private final double totalMaterialCost;
		private final double totalGrossRevenue;
		private final double totalGeTax;
		private final double totalNetProfit;
		private final double totalXpGained;
		private final double estimatedMinutes;
		@Builder.Default
		private final List<SessionMaterial> materials = Collections.emptyList();
		@Builder.Default
		private final List<SessionOutput> outputs = Collections.emptyList();
	}

	private InventoryCalculator()
	{
	}

	/**
	 * Computes how many craft actions fit in a single 28-slot inventory for {@code recipe},
	 * accounting for non-consumed tools and stackable vs non-stackable ingredients.
	 */
	public static int calculateCraftsPerInventory(CraftingRecipe recipe)
	{
		if (recipe == null)
		{
			return 0;
		}
		int freeSlots = Math.max(1, OSRS_INVENTORY_SLOTS - Math.max(0, recipe.getToolSlotsRequired()));
		int stackableInputSlots = 0;
		int nonStackableSlotsPerAction = 0;

		for (RecipeInput input : recipe.getInputs())
		{
			if (input.getItemId() == ItemID.COINS_995 || input.getItemId() == ItemID.THREAD)
			{
				// Coins or Thread are already counted in toolSlotsRequired (or shared pouch/slot)
				continue;
			}
			if (input.isStackable())
			{
				stackableInputSlots++;
			}
			else
			{
				nonStackableSlotsPerAction += Math.max(1, (int) Math.ceil(input.getQuantity()));
			}
		}

		// If all inputs are stackable (e.g. Xerician fabric), output items take 1 non-stackable slot each
		if (nonStackableSlotsPerAction == 0)
		{
			int usable = Math.max(1, freeSlots - stackableInputSlots);
			return usable;
		}

		int usableSlots = Math.max(1, freeSlots - stackableInputSlots);
		return Math.max(1, usableSlots / nonStackableSlotsPerAction);
	}

	public static InventorySessionResult calculateForInventories(CraftingResult result, int inventories)
	{
		if (result == null || result.getRecipe() == null || inventories <= 0)
		{
			return InventorySessionResult.builder().available(false).build();
		}
		int perInv = calculateCraftsPerInventory(result.getRecipe());
		return calculateForActions(result, (long) perInv * inventories);
	}

	public static InventorySessionResult calculateForActions(CraftingResult result, long actions)
	{
		if (result == null || result.getRecipe() == null || actions <= 0L)
		{
			return InventorySessionResult.builder().available(false).build();
		}

		CraftingRecipe recipe = result.getRecipe();
		int perInv = calculateCraftsPerInventory(recipe);
		long inventories = (long) Math.ceil((double) actions / Math.max(1, perInv));

		List<SessionMaterial> mats = new ArrayList<>();
		for (RecipeInput in : recipe.getInputs())
		{
			double exact = actions * in.getQuantity();
			long purchase = (long) Math.ceil(exact - 1e-9);
			mats.add(SessionMaterial.builder()
				.itemId(in.getItemId())
				.itemName(in.getItemName())
				.exactQuantity(exact)
				.purchaseQuantity(purchase)
				.stackable(in.isStackable())
				.build());
		}

		List<SessionOutput> outs = new ArrayList<>();
		for (RecipeOutput out : recipe.getOutputs())
		{
			outs.add(SessionOutput.builder()
				.itemId(out.getItemId())
				.itemName(out.getItemName())
				.expectedQuantity(actions * out.getExpectedQuantity())
				.build());
		}

		double totalXp = actions * recipe.getXpPerAction();
		double minutes = result.getActionsPerHour() > 0.0
			? (actions / result.getActionsPerHour()) * 60.0
			: 0.0;

		if (!result.isPriceDataComplete())
		{
			return InventorySessionResult.builder()
				.available(false)
				.recipe(recipe)
				.craftsPerInventory(perInv)
				.inventoriesNeeded(inventories)
				.actionsRequested(actions)
				.totalXpGained(totalXp)
				.estimatedMinutes(minutes)
				.materials(Collections.unmodifiableList(mats))
				.outputs(Collections.unmodifiableList(outs))
				.build();
		}

		return InventorySessionResult.builder()
			.available(true)
			.recipe(recipe)
			.craftsPerInventory(perInv)
			.inventoriesNeeded(inventories)
			.actionsRequested(actions)
			.totalMaterialCost(actions * result.getMaterialCostPerCraft())
			.totalGrossRevenue(actions * result.getGrossRevenuePerCraft())
			.totalGeTax(actions * result.getGeTaxPerCraft())
			.totalNetProfit(actions * result.getProfitPerCraft())
			.totalXpGained(totalXp)
			.estimatedMinutes(minutes)
			.materials(Collections.unmodifiableList(mats))
			.outputs(Collections.unmodifiableList(outs))
			.build();
	}
}
