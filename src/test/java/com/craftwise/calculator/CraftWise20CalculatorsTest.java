package com.craftwise.calculator;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.BreakEvenCalculator.BreakEvenAnalysis;
import com.craftwise.calculator.BreakEvenCalculator.MarginPresetTarget;
import com.craftwise.calculator.MarketConfidenceEvaluator.MarketEvaluation;
import com.craftwise.calculator.MarketConfidenceEvaluator.PriceConfidence;
import com.craftwise.calculator.MultiStepCalculator.MultiStepAnalysis;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListSummary;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingSortOrder;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.ComparedPlan;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.OptimizerOptions;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.PlanStrategy;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.craftwise.util.XPTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Test;

public class CraftWise20CalculatorsTest
{
	private static ItemPrice mkPrice(int id, String name, long high, long low, long vol1h)
	{
		long now = System.currentTimeMillis() / 1000L;
		return ItemPrice.builder()
			.itemId(id)
			.name(name)
			.highPrice(high)
			.lowPrice(low)
			.highTime(now - 60L)
			.lowTime(now - 60L)
			.avg5mHigh(high)
			.avg5mLow(low)
			.vol5mHigh(Math.max(0L, vol1h / 12L))
			.vol5mLow(Math.max(0L, vol1h / 12L))
			.avg1hHigh(high)
			.avg1hLow(low)
			.vol1hHigh(vol1h)
			.vol1hLow(vol1h)
			.fetchedAtEpochSec(now)
			.stale(false)
			.build();
	}

	@Test
	public void testPriceScenariosOrdering()
	{
		ItemPrice p = ItemPrice.builder()
			.itemId(ItemID.UNCUT_RUBY)
			.name("Uncut ruby")
			.highPrice(1100L)
			.lowPrice(950L)
			.avg5mHigh(1080L)
			.avg5mLow(970L)
			.avg1hHigh(1070L)
			.avg1hLow(980L)
			.build();

		long consBuy = p.resolveBuyPrice(PriceCalculationMode.INSTANT_BUY_SELL, null);
		long consSell = p.resolveSellPrice(PriceCalculationMode.INSTANT_BUY_SELL, null);
		long patBuy = p.resolveBuyPrice(PriceCalculationMode.PATIENT_BUY_SELL, null);
		long patSell = p.resolveSellPrice(PriceCalculationMode.PATIENT_BUY_SELL, null);
		long optBuy = p.resolveBuyPrice(PriceCalculationMode.OPTIMISTIC_MARKET, null);
		long optSell = p.resolveSellPrice(PriceCalculationMode.OPTIMISTIC_MARKET, null);

		Assert.assertTrue(optBuy <= patBuy && patBuy <= consBuy);
		Assert.assertTrue(consSell <= patSell && patSell <= optSell);
	}

	@Test
	public void testMarketConfidenceNeverFakesZeroVolume()
	{
		ItemPrice illiquid = mkPrice(ItemID.UNCUT_ZENYTE, "Uncut zenyte", 15_000_000L, 12_000_000L, 0L);
		MarketEvaluation lowEval = MarketConfidenceEvaluator.evaluate(
			true, Collections.singletonList(illiquid), illiquid, System.currentTimeMillis() / 1000L);
		Assert.assertEquals(PriceConfidence.LOW, lowEval.getConfidence());
		Assert.assertTrue(lowEval.isLowLiquidityWarning());
		Assert.assertTrue(lowEval.isWideSpreadWarning());
		Assert.assertEquals(0L, lowEval.getMin1hVolume());

		ItemPrice liquid = mkPrice(ItemID.RUBY, "Ruby", 1020L, 1000L, 5000L);
		MarketEvaluation highEval = MarketConfidenceEvaluator.evaluate(
			true, Collections.singletonList(liquid), liquid, System.currentTimeMillis() / 1000L);
		Assert.assertEquals(PriceConfidence.HIGH, highEval.getConfidence());
		Assert.assertTrue(highEval.getWarnings().isEmpty());
	}

	@Test
	public void testBreakEvenMarginPresetsAndNonConsumedExclusion()
	{
		CraftingRecipe cutRuby = CraftingDataRepository.getById("cut_ruby");
		Assert.assertNotNull(cutRuby);
		BreakEvenAnalysis rubyAnalysis = BreakEvenCalculator.analyze(
			cutRuby,
			1000.0,
			1200L,
			0.0,
			Collections.singletonList(1000L),
			true
		);
		Assert.assertTrue(rubyAnalysis.isAvailable());
		Assert.assertEquals(1, rubyAnalysis.getInputLimits().size());
		Assert.assertEquals(5, rubyAnalysis.getMarginTargets().size());
		MarginPresetTarget m0 = rubyAnalysis.getMarginTargets().get(0);
		MarginPresetTarget m20 = rubyAnalysis.getMarginTargets().get(4);
		Assert.assertTrue(m0.getNetProfitAtTarget() >= 0L);
		Assert.assertTrue(m20.getTargetSellPrice() > m0.getTargetSellPrice());

		// Explicit test for excluding consumed(false) tools & COINS_995 from inputLimits
		CraftingRecipe syntheticToolRecipe = CraftingRecipe.builder()
			.id("test_tool_exclusion")
			.name("Synthetic Tool Exclusion")
			.requiredLevel(1)
			.xpPerAction(10.0)
			.xpPerOutputItem(10.0)
			.inputs(java.util.Arrays.asList(
				CraftingRecipe.RecipeInput.builder().itemId(ItemID.UNCUT_RUBY).itemName("Uncut ruby").quantity(1.0).consumed(true).build(),
				CraftingRecipe.RecipeInput.builder().itemId(ItemID.CHISEL).itemName("Chisel").quantity(1.0).consumed(false).build(),
				CraftingRecipe.RecipeInput.builder().itemId(ItemID.COINS_995).itemName("Coins").quantity(10.0).consumed(true).build()
			))
			.outputs(Collections.singletonList(
				CraftingRecipe.RecipeOutput.builder().itemId(ItemID.RUBY).itemName("Ruby").quantity(1.0).probability(1.0).tradeable(true).build()
			))
			.build();
		BreakEvenAnalysis toolAnalysis = BreakEvenCalculator.analyze(
			syntheticToolRecipe, 1010.0, 1200L, 0.0, java.util.Arrays.asList(1000L, 15L, 1L), true);
		Assert.assertEquals(1, toolAnalysis.getInputLimits().size());
		Assert.assertEquals(ItemID.UNCUT_RUBY, toolAnalysis.getInputLimits().get(0).getItemId());
	}

	@Test
	public void testMultiStepBuyVsMakeZeroDoubleCounting()
	{
		// Sapphire amulet stringing uses Sapphire amulet (u) + Ball of wool -> Sapphire amulet
		// Upstream 1: Sapphire amulet (u) uses Cut sapphire + Gold bar
		// Upstream 2: Cut sapphire uses Uncut sapphire
		CraftingRecipe finalRecipe = CraftingDataRepository.getById("string_sapphire_amulet");
		Assert.assertNotNull(finalRecipe);

		Map<Integer, ItemPrice> prices = new HashMap<>();
		prices.put(ItemID.UNCUT_SAPPHIRE, mkPrice(ItemID.UNCUT_SAPPHIRE, "Uncut sapphire", 400L, 380L, 5000L));
		prices.put(ItemID.SAPPHIRE, mkPrice(ItemID.SAPPHIRE, "Sapphire", 450L, 430L, 5000L));
		prices.put(ItemID.GOLD_BAR, mkPrice(ItemID.GOLD_BAR, "Gold bar", 150L, 140L, 10000L));
		prices.put(ItemID.SAPPHIRE_AMULET_U, mkPrice(ItemID.SAPPHIRE_AMULET_U, "Sapphire amulet (u)", 700L, 650L, 2000L));
		prices.put(ItemID.BALL_OF_WOOL, mkPrice(ItemID.BALL_OF_WOOL, "Ball of wool", 100L, 90L, 5000L));
		prices.put(ItemID.SAPPHIRE_AMULET, mkPrice(ItemID.SAPPHIRE_AMULET, "Sapphire amulet", 1050L, 1000L, 2000L));

		MultiStepAnalysis analysis = MultiStepCalculator.analyze(
			finalRecipe,
			CraftingDataRepository.getAllRecipes(),
			prices,
			Collections.emptyMap(),
			PriceCalculationMode.INSTANT_BUY_SELL,
			true,
			Collections.emptyMap()
		);

		Assert.assertTrue(analysis.isHasMultiStepChain());
		Assert.assertTrue(analysis.isBuyIntermediateAvailable());
		Assert.assertTrue(analysis.isMakeAllStepsAvailable());

		// Direct buy: Sapphire amulet (u) @ 700 + Ball of wool @ 100 = 800 GP cost
		Assert.assertEquals(800.0, analysis.getBuyIntermediateCost(), 0.01);
		// Make all from raw: Uncut sapphire (400) + Gold bar (150) + Ball of wool (100) = 650 GP cost (zero double-counting!)
		Assert.assertEquals(650.0, analysis.getMakeAllRawCost(), 0.01);
		Assert.assertEquals("MAKE INTERMEDIATE", analysis.getRecommendation());
		Assert.assertEquals(150.0, analysis.getProfitAdvantageMakeVsBuy(), 0.01);
		// Upstream cutting (50) + stringing? Wait: Cut sapphire (50) + Amulet (u) (65) + Stringing (4) = 119 XP -> +115 XP gain
		Assert.assertTrue(analysis.getXpGainMakeVsBuy() > 0.0);
	}

	@Test
	public void testPlansAThroughEAndShoppingList()
	{
		Map<Integer, ItemPrice> prices = new HashMap<>();
		for (CraftingRecipe r : CraftingDataRepository.getAllRecipes())
		{
			for (CraftingRecipe.RecipeInput in : r.getInputs())
			{
				prices.putIfAbsent(in.getItemId(), mkPrice(in.getItemId(), in.getItemName(), 500L, 480L, 2000L));
			}
			for (CraftingRecipe.RecipeOutput out : r.getOutputs())
			{
				prices.putIfAbsent(out.getItemId(), mkPrice(out.getItemId(), out.getItemName(), 620L, 600L, 2000L));
			}
		}

		List<CraftingResult> evals = new ArrayList<>();
		for (CraftingRecipe r : CraftingDataRepository.getAllRecipes())
		{
			evals.add(ProfitCalculator.evaluate(
				r, prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, true,
				Collections.emptyMap(), Collections.emptyMap(), null));
		}

		// F2P only, no quests assumed
		List<ComparedPlan> plans = TrainingPlanComparisonCalculator.buildSideBySidePlans(
			evals,
			OptimizerOptions.builder()
				.currentLevel(40)
				.currentXp(XPTable.getXpForLevel(40))
				.targetLevel(60)
				.temporaryBoost(0)
				.membersAllowed(false)
				.includeQuestLocked(false)
				.includeUnlockRequired(false)
				.availableBudgetGp(2_000_000L)
				.build()
		);

		Assert.assertEquals(5, plans.size());
		Assert.assertEquals(PlanStrategy.PLAN_A_FASTEST, plans.get(0).getStrategy());
		for (ComparedPlan cp : plans)
		{
			Assert.assertTrue("Plan " + cp.getStrategy() + " should be feasible in F2P test", cp.isFeasible());
			for (com.craftwise.model.TrainingPlan.TrainingStage st : cp.getPlan().getStages())
			{
				Assert.assertFalse("F2P filter violated", st.getRecipe().isMembers());
				Assert.assertFalse("Quest assumption violated", st.getRecipe().hasQuestRequirement());
			}
		}

		ShoppingListSummary shopping = ShoppingListCalculator.fromTrainingPlan(
			plans.get(0).getPlan(),
			Collections.singletonMap(ItemID.GOLD_BAR, 10L),
			prices,
			Collections.emptyMap(),
			PriceCalculationMode.INSTANT_BUY_SELL,
			Collections.emptyMap(),
			ShoppingSortOrder.TOTAL_COST_DESC
		);
		Assert.assertTrue(shopping.isAllPricesAvailable());
		Assert.assertFalse(shopping.getItems().isEmpty());
		String text = ShoppingListCalculator.formatPlainText(shopping, "40-60 F2P");
		Assert.assertTrue(text.contains("CraftWise Shopping List"));
	}
}
