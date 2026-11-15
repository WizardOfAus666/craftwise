package com.craftwise.calculator;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.BudgetCalculator.BudgetMethodEvaluation;
import com.craftwise.calculator.InventoryCalculator.InventorySessionResult;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.craftwise.model.TrainingPlan;
import com.craftwise.model.TrainingPlan.OptimizationObjective;
import com.craftwise.util.XPTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Test;

public class PlannerAndInventoryTest
{
	@Test
	public void testInventoryCapacityCalculations()
	{
		// Cut diamond: 1 chisel slot -> 27 diamonds/inventory
		Assert.assertEquals(27, InventoryCalculator.calculateCraftsPerInventory(CraftingDataRepository.getById("cut_diamond")));
		// Black d'hide body: needle + thread (2 slots), 3 leather/body -> 26 / 3 = 8 bodies/inventory
		Assert.assertEquals(8, InventoryCalculator.calculateCraftsPerInventory(CraftingDataRepository.getById("black_dhide_body")));
		// Air battlestaff: 0 tool slots, 2 items/staff -> 28 / 2 = 14 staves/inventory
		Assert.assertEquals(14, InventoryCalculator.calculateCraftsPerInventory(CraftingDataRepository.getById("air_battlestaff")));
		// Xerician top: needle + thread (2) + stackable fabric (1) -> 25 robes/inventory
		Assert.assertEquals(25, InventoryCalculator.calculateCraftsPerInventory(CraftingDataRepository.getById("xerician_top")));

		CraftingRecipe airStaff = CraftingDataRepository.getById("air_battlestaff");
		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.BATTLESTAFF, ItemPrice.builder().itemId(ItemID.BATTLESTAFF).highPrice(8_000L).build());
		prices.put(ItemID.AIR_ORB, ItemPrice.builder().itemId(ItemID.AIR_ORB).highPrice(1_000L).build());
		prices.put(ItemID.AIR_BATTLESTAFF, ItemPrice.builder().itemId(ItemID.AIR_BATTLESTAFF).lowPrice(9_300L).build());
		CraftingResult eval = ProfitCalculator.evaluate(airStaff, prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null);

		InventorySessionResult fiveInv = InventoryCalculator.calculateForInventories(eval, 5);
		Assert.assertTrue(fiveInv.isAvailable());
		Assert.assertEquals(70L, fiveInv.getActionsRequested());
		Assert.assertEquals(5L, fiveInv.getInventoriesNeeded());
		Assert.assertEquals(70.0 * 137.5, fiveInv.getTotalXpGained(), 0.001);
	}

	@Test
	public void testBudgetEnforcementAndMultiStageTrainingPlan()
	{
		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.MOLTEN_GLASS, ItemPrice.builder().itemId(ItemID.MOLTEN_GLASS).highPrice(100L).lowPrice(95L).build());
		prices.put(ItemID.UNPOWERED_ORB, ItemPrice.builder().itemId(ItemID.UNPOWERED_ORB).highPrice(110L).lowPrice(105L).build());
		prices.put(ItemID.LANTERN_LENS, ItemPrice.builder().itemId(ItemID.LANTERN_LENS).highPrice(80L).lowPrice(75L).build());
		prices.put(ItemID.BATTLESTAFF, ItemPrice.builder().itemId(ItemID.BATTLESTAFF).highPrice(8_000L).lowPrice(7_900L).build());
		prices.put(ItemID.WATER_ORB, ItemPrice.builder().itemId(ItemID.WATER_ORB).highPrice(1_000L).lowPrice(950L).build());
		prices.put(ItemID.WATER_BATTLESTAFF, ItemPrice.builder().itemId(ItemID.WATER_BATTLESTAFF).highPrice(9_100L).lowPrice(9_000L).build());
		prices.put(ItemID.AIR_ORB, ItemPrice.builder().itemId(ItemID.AIR_ORB).highPrice(1_100L).lowPrice(1_050L).build());
		prices.put(ItemID.AIR_BATTLESTAFF, ItemPrice.builder().itemId(ItemID.AIR_BATTLESTAFF).highPrice(9_400L).lowPrice(9_350L).build());
		prices.put(ItemID.BLACK_DRAGON_LEATHER, ItemPrice.builder().itemId(ItemID.BLACK_DRAGON_LEATHER).highPrice(2_900L).lowPrice(2_850L).build());
		prices.put(ItemID.THREAD, ItemPrice.builder().itemId(ItemID.THREAD).highPrice(5L).lowPrice(4L).build());
		prices.put(ItemID.BLACK_DHIDE_BODY, ItemPrice.builder().itemId(ItemID.BLACK_DHIDE_BODY).highPrice(8_100L).lowPrice(8_000L).build());

		List<CraftingResult> evaluated = new ArrayList<>();
		for (String id : new String[]{"unpowered_orb", "lantern_lens", "water_battlestaff", "air_battlestaff", "black_dhide_body"})
		{
			evaluated.add(ProfitCalculator.evaluate(
				CraftingDataRepository.getById(id), prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null));
		}

		// Tight budget: 1,000 GP from level 46 to 99 must NOT exceed 1,000 GP
		TrainingPlan tightPlan = TrainingOptimizer.optimize(
			evaluated, XPTable.getXpForLevel(46), 99, 1_000L, OptimizationObjective.MAX_XP_WITHIN_BUDGET, -1L, 0.0);
		Assert.assertFalse(tightPlan.isFeasible());
		Assert.assertTrue("Total cost must never exceed budget", tightPlan.getTotalMaterialCost() <= 1_000.0 + 1e-6);

		// Generous budget: 1B GP from level 54 to 90 -> should produce multi-stage progression
		TrainingPlan fullPlan = TrainingOptimizer.optimize(
			evaluated, XPTable.getXpForLevel(54), 90, 1_000_000_000L, OptimizationObjective.FASTEST_WITHIN_BUDGET, -1L, 0.0);
		Assert.assertTrue(fullPlan.isFeasible());
		Assert.assertTrue("Should combine multiple stages across unlocks", fullPlan.getStages().size() >= 2);

		// BudgetCalculator check
		List<BudgetMethodEvaluation> withinTinyBudget = BudgetCalculator.evaluateWithinBudget(
			evaluated, 85, 100_000L, 500L, OptimizationObjective.MAX_XP_WITHIN_BUDGET, -1L, 0.0);
		for (BudgetMethodEvaluation ev : withinTinyBudget)
		{
			Assert.assertTrue(ev.getTotalMaterialSpend() <= 500.0 + 1e-6);
		}
	}
}
