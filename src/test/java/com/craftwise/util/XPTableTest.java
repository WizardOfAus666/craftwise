package com.craftwise.util;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.calculator.XPCalculator;
import com.craftwise.calculator.XPCalculator.XPGoalProjection;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Test;

public class XPTableTest
{
	@Test
	public void testOfficialOsrsXpCheckpoints()
	{
		Assert.assertEquals(0L, XPTable.getXpForLevel(1));
		Assert.assertEquals(83L, XPTable.getXpForLevel(2));
		Assert.assertEquals(1_154L, XPTable.getXpForLevel(10));
		Assert.assertEquals(101_333L, XPTable.getXpForLevel(50));
		Assert.assertEquals(737_627L, XPTable.getXpForLevel(70));
		Assert.assertEquals(1_986_068L, XPTable.getXpForLevel(80));
		Assert.assertEquals(5_346_332L, XPTable.getXpForLevel(90));
		Assert.assertEquals(13_034_431L, XPTable.getXpForLevel(99));
	}

	@Test
	public void testLevelLookupBoundaries()
	{
		Assert.assertEquals(1, XPTable.getLevelForXp(0L));
		Assert.assertEquals(1, XPTable.getLevelForXp(82L));
		Assert.assertEquals(2, XPTable.getLevelForXp(83L));
		Assert.assertEquals(98, XPTable.getLevelForXp(13_034_430L));
		Assert.assertEquals(99, XPTable.getLevelForXp(13_034_431L));
		Assert.assertEquals(99, XPTable.getLevelForXp(200_000_000L));
	}

	@Test
	public void testXpGoalProjectionForCutDiamonds()
	{
		CraftingRecipe cutDiamond = CraftingDataRepository.getById("cut_diamond");
		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.UNCUT_DIAMOND, ItemPrice.builder().itemId(ItemID.UNCUT_DIAMOND).highPrice(2_000L).lowPrice(1_950L).build());
		prices.put(ItemID.DIAMOND, ItemPrice.builder().itemId(ItemID.DIAMOND).highPrice(1_900L).lowPrice(1_850L).build());

		CraftingResult eval = ProfitCalculator.evaluate(
			cutDiamond, prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null);
		Assert.assertTrue(eval.isPriceDataComplete());

		long startXp = XPTable.getXpForLevel(70); // 737,627
		XPGoalProjection proj = XPCalculator.calculate(eval, startXp, 71);
		long expectedXpReq = XPTable.getXpForLevel(71) - startXp;
		long expectedActions = (long) Math.ceil(expectedXpReq / 107.5);

		Assert.assertTrue(proj.isAvailable());
		Assert.assertEquals(expectedXpReq, proj.getXpRequired());
		Assert.assertEquals(expectedActions, proj.getActionsRequired());
		Assert.assertEquals(expectedActions, proj.getMaterials().get(0).getPurchaseQuantity());
	}
}
