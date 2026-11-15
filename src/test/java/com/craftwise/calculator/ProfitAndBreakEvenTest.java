package com.craftwise.calculator;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.BreakEvenCalculator.BreakEvenAnalysis;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.CraftingResult.BreakEvenStatus;
import com.craftwise.model.ItemPrice;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Test;

public class ProfitAndBreakEvenTest
{
	@Test
	public void testMissingMarketPriceDoesNotDefaultToZero()
	{
		CraftingRecipe airStaff = CraftingDataRepository.getById("air_battlestaff");
		Map<Integer, ItemPrice> prices = new HashMap<>();
		// Provide Battlestaff and Air orb, omit Air battlestaff!
		prices.put(ItemID.BATTLESTAFF, ItemPrice.builder().itemId(ItemID.BATTLESTAFF).highPrice(8_000L).lowPrice(7_900L).build());
		prices.put(ItemID.AIR_ORB, ItemPrice.builder().itemId(ItemID.AIR_ORB).highPrice(1_100L).lowPrice(1_050L).build());

		CraftingResult res = ProfitCalculator.evaluate(
			airStaff, prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null);

		Assert.assertFalse(res.isPriceDataComplete());
		Assert.assertTrue(res.getMissingPriceItemIds().contains(ItemID.AIR_BATTLESTAFF));
		Assert.assertEquals(BreakEvenStatus.UNAVAILABLE, res.getBreakEvenStatus());
		Assert.assertNull(res.getBreakEvenSellPrice());
	}

	@Test
	public void testMultiInputProfitAndTaxCalculation()
	{
		CraftingRecipe airStaff = CraftingDataRepository.getById("air_battlestaff");
		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.BATTLESTAFF, ItemPrice.builder().itemId(ItemID.BATTLESTAFF).highPrice(8_000L).lowPrice(7_900L).build());
		prices.put(ItemID.AIR_ORB, ItemPrice.builder().itemId(ItemID.AIR_ORB).highPrice(1_100L).lowPrice(1_050L).build());
		prices.put(ItemID.AIR_BATTLESTAFF, ItemPrice.builder().itemId(ItemID.AIR_BATTLESTAFF).highPrice(9_300L).lowPrice(9_200L).build());

		CraftingResult res = ProfitCalculator.evaluate(
			airStaff, prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null);

		// Instant buy inputs: 8000 + 1100 = 9100
		// Instant sell output: 9200; GE tax = floor(9200 / 50) = 184
		// Net rev = 9016; Profit = 9016 - 9100 = -84
		Assert.assertTrue(res.isPriceDataComplete());
		Assert.assertEquals(9_100.0, res.getMaterialCostPerCraft(), 0.001);
		Assert.assertEquals(9_200.0, res.getGrossRevenuePerCraft(), 0.001);
		Assert.assertEquals(184.0, res.getGeTaxPerCraft(), 0.001);
		Assert.assertEquals(9_016.0, res.getNetRevenuePerCraft(), 0.001);
		Assert.assertEquals(-84.0, res.getProfitPerCraft(), 0.001);
		Assert.assertEquals(-840.0, res.getProfit10(), 0.001);
		Assert.assertEquals(-8_400.0, res.getProfit100(), 0.001);
		Assert.assertEquals(-84_000.0, res.getProfit1000(), 0.001);
	}

	@Test
	public void testMultiOutputAmethystBoltTips()
	{
		CraftingRecipe tips = CraftingDataRepository.getById("amethyst_bolt_tips");
		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.AMETHYST, ItemPrice.builder().itemId(ItemID.AMETHYST).highPrice(2_400L).lowPrice(2_300L).build());
		prices.put(ItemID.AMETHYST_BOLT_TIPS, ItemPrice.builder().itemId(ItemID.AMETHYST_BOLT_TIPS).highPrice(200L).lowPrice(190L).build());

		CraftingResult res = ProfitCalculator.evaluate(
			tips, prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null);

		// Cost = 2400; 15 tips @ 190 = 2850 gross; tax per tip = floor(190/50)=3 -> 15*3 = 45 tax; net = 2805; profit = +405
		Assert.assertTrue(res.isPriceDataComplete());
		Assert.assertEquals(2_400.0, res.getMaterialCostPerCraft(), 0.001);
		Assert.assertEquals(2_850.0, res.getGrossRevenuePerCraft(), 0.001);
		Assert.assertEquals(45.0, res.getGeTaxPerCraft(), 0.001);
		Assert.assertEquals(405.0, res.getProfitPerCraft(), 0.001);
	}

	@Test
	public void testBreakEvenPromptExampleAndTaxValidation()
	{
		CraftingRecipe recipe = CraftingDataRepository.getById("zenyte_ring");
		BreakEvenAnalysis analysis = BreakEvenCalculator.analyze(
			recipe,
			100_000.0,
			120_000L,
			0.0,
			Collections.emptyList(),
			true
		);

		Assert.assertTrue(analysis.isAvailable());
		Assert.assertEquals(2_400L, analysis.getCurrentTaxPerPrimaryUnit());
		Assert.assertEquals(17_600L, analysis.getCurrentNetProfit());
		Assert.assertEquals(BreakEvenStatus.PROFITABLE, analysis.getStatus());

		long bePrice = analysis.getBreakEvenPrimarySellPrice();
		long netAtBe = GETransactionCalculator.calculateNetRevenuePerItem(
			bePrice, recipe.getPrimaryItemId(), false, true);
		long netBelowBe = GETransactionCalculator.calculateNetRevenuePerItem(
			bePrice - 1L, recipe.getPrimaryItemId(), false, true);

		Assert.assertTrue("Net at break-even must cover 100,000 gp", netAtBe >= 100_000L);
		Assert.assertTrue("Net at break-even - 1 must fall short", netBelowBe < 100_000L);
	}

	@Test
	public void testUntradeableLightOrbHasZeroRevenueNotMissingError()
	{
		CraftingRecipe orb = CraftingDataRepository.getById("empty_light_orb");
		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.MOLTEN_GLASS, ItemPrice.builder().itemId(ItemID.MOLTEN_GLASS).highPrice(110L).lowPrice(105L).build());

		CraftingResult res = ProfitCalculator.evaluate(
			orb, prices, PriceCalculationMode.INSTANT_BUY_SELL, true, null, null, null);

		Assert.assertTrue(res.isPriceDataComplete());
		Assert.assertEquals(110.0, res.getMaterialCostPerCraft(), 0.001);
		Assert.assertEquals(0.0, res.getGrossRevenuePerCraft(), 0.001);
		Assert.assertEquals(-110.0, res.getProfitPerCraft(), 0.001);
	}
}
