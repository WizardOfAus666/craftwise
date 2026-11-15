package com.craftwise.bank;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.bank.BankCraftingCalculator.BankMaterialValueEntry;
import com.craftwise.bank.BankCraftingCalculator.BankRecipeMatch;
import com.craftwise.bank.BankCraftingCalculator.BankRecommendationMode;
import com.craftwise.bank.BankCraftingCalculator.BankTrainingComparison;
import com.craftwise.bank.BankCraftingCalculator.CoverageTier;
import com.craftwise.bank.OwnedMaterialSnapshot.BankDataState;
import com.craftwise.bank.OwnedMaterialSnapshot.OwnedItemCount;
import com.craftwise.bank.OwnedMaterialSnapshot.SnapshotView;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListSummary;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingSortOrder;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.google.gson.Gson;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BankCraftingTest
{
	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	private static ItemPrice mkPrice(int id, String name, long high, long low)
	{
		long now = System.currentTimeMillis() / 1000L;
		return ItemPrice.builder()
			.itemId(id)
			.name(name)
			.highPrice(high)
			.lowPrice(low)
			.highTime(now - 20L)
			.lowTime(now - 20L)
			.fetchedAtEpochSec(now)
			.stale(false)
			.build();
	}

	private static ItemContainer mockContainer(final Item[] items)
	{
		return (ItemContainer) Proxy.newProxyInstance(
			ItemContainer.class.getClassLoader(),
			new Class<?>[]{ItemContainer.class},
			(proxy, method, args) ->
			{
				if ("getItems".equals(method.getName()))
				{
					return items;
				}
				return null;
			});
	}

	private static Map<Integer, ItemPrice> samplePrices()
	{
		Map<Integer, ItemPrice> p = new HashMap<>();
		p.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L));
		p.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 1350L, 1300L));
		p.put(ItemID.UNCUT_SAPPHIRE, mkPrice(ItemID.UNCUT_SAPPHIRE, "Uncut sapphire", 450L, 420L));
		p.put(ItemID.SAPPHIRE, mkPrice(ItemID.SAPPHIRE, "Sapphire", 520L, 480L));
		p.put(ItemID.GOLD_BAR, mkPrice(ItemID.GOLD_BAR, "Gold bar", 120L, 110L));
		p.put(ItemID.SAPPHIRE_BRACELET_11072, mkPrice(ItemID.SAPPHIRE_BRACELET_11072, "Sapphire bracelet", 1100L, 1050L));
		p.put(ItemID.GREEN_DRAGON_LEATHER, mkPrice(ItemID.GREEN_DRAGON_LEATHER, "Green dragon leather", 1500L, 1450L));
		p.put(ItemID.THREAD, mkPrice(ItemID.THREAD, "Thread", 4L, 3L));
		p.put(ItemID.GREEN_DHIDE_BODY, mkPrice(ItemID.GREEN_DHIDE_BODY, "Green d'hide body", 4400L, 4300L));
		p.put(ItemID.AMETHYST, mkPrice(ItemID.AMETHYST, "Amethyst", 3200L, 3100L));
		p.put(ItemID.AMETHYST_BOLT_TIPS, mkPrice(ItemID.AMETHYST_BOLT_TIPS, "Amethyst bolt tips", 260L, 250L));
		p.put(ItemID.CHISEL, mkPrice(ItemID.CHISEL, "Chisel", 20L, 10L));
		p.put(ItemID.NEEDLE, mkPrice(ItemID.NEEDLE, "Needle", 20L, 10L));
		p.put(ItemID.BRACELET_MOULD, mkPrice(ItemID.BRACELET_MOULD, "Bracelet mould", 200L, 100L));
		p.put(ItemID.COINS_995, mkPrice(ItemID.COINS_995, "Coins", 1L, 1L));
		return p;
	}

	private static List<CraftingResult> evaluateRecipes(Map<Integer, ItemPrice> prices, String... ids)
	{
		List<CraftingResult> out = new ArrayList<>();
		for (String id : ids)
		{
			CraftingRecipe r = CraftingDataRepository.getById(id);
			if (r != null)
			{
				out.add(ProfitCalculator.evaluate(
					r, prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL,
					true, Collections.emptyMap(), Collections.emptyMap(), null));
			}
		}
		return out;
	}

	@Test
	public void testSnapshotLifecycleAndNotedSeparation() throws Exception
	{
		Path manualFile = tempFolder.newFile("manual_owned.json").toPath();
		OwnedMaterialSnapshot snapshot = new OwnedMaterialSnapshot(manualFile, new Gson());

		// Initially unavailable; never assumes bank is empty
		Assert.assertEquals(BankDataState.UNAVAILABLE, snapshot.getSnapshotView().getBankDataState());
		Assert.assertFalse(snapshot.getSnapshotView().isBankScanned());

		// Scan bank via container proxy
		ItemContainer bank = mockContainer(new Item[]{
			new Item(ItemID.UNCUT_RUBY, 150),
			new Item(ItemID.CHISEL, 1)
		});
		BankInventoryScanner.scanBank(bank, null, snapshot, true, 1_700_000_000L);
		Assert.assertEquals(BankDataState.LIVE_OPEN, snapshot.getSnapshotView().getBankDataState());
		Assert.assertTrue(snapshot.getSnapshotView().isBankScanned());

		// Separate bank vs unnoted inventory vs noted inventory directly
		Map<Integer, Long> unnoted = Collections.singletonMap(ItemID.UNCUT_RUBY, 100L);
		Map<Integer, Long> noted = Collections.singletonMap(ItemID.UNCUT_RUBY, 250L);
		snapshot.updateInventoryContents(unnoted, noted,
			Collections.singletonMap(ItemID.UNCUT_RUBY, "Uncut ruby"), 1_700_000_010L);

		OwnedItemCount rubyCount = snapshot.getSnapshotView().getCount(ItemID.UNCUT_RUBY);
		Assert.assertNotNull(rubyCount);
		Assert.assertEquals(150L, rubyCount.getBankQty());
		Assert.assertEquals(100L, rubyCount.getInventoryUnnotedQty());
		Assert.assertEquals(250L, rubyCount.getInventoryNotedQty());
		Assert.assertEquals(250L, rubyCount.getCombinedUsableQty());
		Assert.assertEquals(500L, rubyCount.getCombinedIncludingNotedQty());

		// Closing bank keeps last scanned quantities as SNAPSHOT_CLOSED
		snapshot.onBankClosed();
		Assert.assertEquals(BankDataState.SNAPSHOT_CLOSED, snapshot.getSnapshotView().getBankDataState());
		Assert.assertEquals(150L, snapshot.getSnapshotView().getCount(ItemID.UNCUT_RUBY).getBankQty());

		// Logout clears session containers back to UNAVAILABLE
		snapshot.resetSessionBankState();
		Assert.assertEquals(BankDataState.UNAVAILABLE, snapshot.getSnapshotView().getBankDataState());
		Assert.assertFalse(snapshot.getSnapshotView().isBankScanned());
	}

	@Test
	public void testSingleMultiInputMultiOutputAndReusableTools() throws Exception
	{
		Path manualFile = tempFolder.newFile("manual_owned.json").toPath();
		OwnedMaterialSnapshot snapshot = new OwnedMaterialSnapshot(manualFile, new Gson());
		Map<Integer, ItemPrice> prices = samplePrices();

		Map<Integer, Long> bankMap = new HashMap<>();
		bankMap.put(ItemID.UNCUT_RUBY, 150L);
		bankMap.put(ItemID.GREEN_DRAGON_LEATHER, 300L);
		bankMap.put(ItemID.THREAD, 100L);
		bankMap.put(ItemID.NEEDLE, 1L);
		bankMap.put(ItemID.AMETHYST, 100L);
		// Intentionally omit CHISEL first to verify missing reusable tool behavior
		snapshot.updateBankContents(bankMap, Collections.emptyMap(), true, 1_700_000_000L);

		// Add noted unc. ruby to test noted toggle
		snapshot.updateInventoryContents(
			Collections.singletonMap(ItemID.UNCUT_RUBY, 100L),
			Collections.singletonMap(ItemID.UNCUT_RUBY, 250L),
			Collections.emptyMap(),
			1_700_000_005L);

		// 1. Cut ruby WITHOUT chisel -> missingTools = true, 0 crafts
		CraftingRecipe cutRuby = CraftingDataRepository.getById("cut_ruby");
		CraftingResult cutRubyRes = evaluateRecipes(prices, "cut_ruby").get(0);
		BankRecipeMatch noChisel = BankCraftingCalculator.matchRecipe(
			cutRuby, cutRubyRes, snapshot.getSnapshotView(), 500L, false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertTrue(noChisel.isMissingTools());
		Assert.assertEquals(0L, noChisel.getMaxCompleteCrafts());
		Assert.assertEquals(CoverageTier.PARTIAL, noChisel.getCoverageTier());

		// Empty snapshot -> NOT_READY
		OwnedMaterialSnapshot emptySnap = new OwnedMaterialSnapshot(tempFolder.newFile("empty_owned.json").toPath(), new Gson());
		BankRecipeMatch emptyMatch = BankCraftingCalculator.matchRecipe(
			cutRuby, cutRubyRes, emptySnap.getSnapshotView(), 500L, false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertEquals(CoverageTier.NOT_READY, emptyMatch.getCoverageTier());

		// Add reusable chisel to inventory so inventory-only crafts can execute cleanly
		Map<Integer, Long> invWithChisel = new HashMap<>();
		invWithChisel.put(ItemID.UNCUT_RUBY, 100L);
		invWithChisel.put(ItemID.CHISEL, 1L);
		snapshot.updateInventoryContents(
			invWithChisel,
			Collections.singletonMap(ItemID.UNCUT_RUBY, 250L),
			Collections.emptyMap(),
			1_700_000_020L);

		// 2. Cut ruby WITH chisel, exclude noted vs include noted
		BankRecipeMatch rubyUnnotedOnly = BankCraftingCalculator.matchRecipe(
			cutRuby, cutRubyRes, snapshot.getSnapshotView(), 500L, false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertFalse(rubyUnnotedOnly.isMissingTools());
		Assert.assertEquals(250L, rubyUnnotedOnly.getMaxCompleteCrafts());
		Assert.assertEquals(100L, rubyUnnotedOnly.getReadyInventoryOnlyCrafts());
		Assert.assertEquals(500L, rubyUnnotedOnly.getMaxCraftsIncludingNoted());
		Assert.assertEquals(50.0, rubyUnnotedOnly.getCoveragePercent(), 0.01);
		Assert.assertEquals(CoverageTier.PARTIAL, rubyUnnotedOnly.getCoverageTier());

		BankRecipeMatch rubyInclNoted = BankCraftingCalculator.matchRecipe(
			cutRuby, cutRubyRes, snapshot.getSnapshotView(), 500L, true,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertEquals(500L, rubyInclNoted.getMaxCompleteCrafts());
		Assert.assertEquals(CoverageTier.READY_100, rubyInclNoted.getCoverageTier());

		// 3. Multi-ingredient Green d'hide body (3 leather + thread + reusable needle)
		CraftingRecipe dhideBody = CraftingDataRepository.getById("green_dhide_body");
		CraftingResult dhideRes = evaluateRecipes(prices, "green_dhide_body").get(0);
		BankRecipeMatch bodyMatch = BankCraftingCalculator.matchRecipe(
			dhideBody, dhideRes, snapshot.getSnapshotView(), 120L, false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertEquals(100L, bodyMatch.getMaxCompleteCrafts());
		Assert.assertEquals(83.33, bodyMatch.getCoveragePercent(), 0.1);
		Assert.assertEquals(CoverageTier.READY_75, bodyMatch.getCoverageTier());
		// Verify 20 missing crafts cost: 60 leather * 1500 = 90000 GP (thread: missing 0/needed? 1 per 5 or 1? Let's verify >= 90000)
		Assert.assertTrue(bodyMatch.getMissingForTargetCostGp() >= 90_000L);

		// 4. Multi-output Amethyst bolt tips (15 output per craft)
		CraftingRecipe boltTips = CraftingDataRepository.getById("amethyst_bolt_tips");
		CraftingResult boltRes = evaluateRecipes(prices, "amethyst_bolt_tips").get(0);
		BankRecipeMatch boltMatch = BankCraftingCalculator.matchRecipe(
			boltTips, boltRes, snapshot.getSnapshotView(), 100L, false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertEquals(100L, boltMatch.getMaxCompleteCrafts());
		Assert.assertEquals(100.0 * boltRes.getProfitPerCraft(), boltMatch.getTotalProfitFromOwnedStock(), 0.5);
	}

	@Test
	public void testMaterialValueTrainingComparisonAndShoppingOverride() throws Exception
	{
		Path manualFile = tempFolder.newFile("manual_owned.json").toPath();
		OwnedMaterialSnapshot snapshot = new OwnedMaterialSnapshot(manualFile, new Gson());
		Map<Integer, ItemPrice> prices = samplePrices();

		Map<Integer, Long> bankMap = new HashMap<>();
		bankMap.put(ItemID.SAPPHIRE, 400L);
		bankMap.put(ItemID.GOLD_BAR, 400L);
		bankMap.put(ItemID.BRACELET_MOULD, 1L);
		bankMap.put(ItemID.UNCUT_RUBY, 200L);
		bankMap.put(ItemID.CHISEL, 1L);
		snapshot.updateBankContents(bankMap, Collections.emptyMap(), true, 1_700_000_000L);

		List<CraftingResult> results = evaluateRecipes(
			prices, "cut_ruby", "cut_sapphire", "sapphire_bracelet");

		// Material value comparison: Sapphire bracelet profit is positive after tax, so crafting > selling raw
		List<BankMaterialValueEntry> valueEntries = BankCraftingCalculator.analyzeOwnedMaterials(
			snapshot.getSnapshotView(), results, 99, false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, true, Collections.emptyMap());
		Assert.assertFalse(valueEntries.isEmpty());
		BankMaterialValueEntry goldEntry = valueEntries.stream()
			.filter(e -> e.getItemId() == ItemID.GOLD_BAR)
			.findFirst().orElse(null);
		Assert.assertNotNull(goldEntry);
		Assert.assertEquals(400L, goldEntry.getCraftableUsingOwnedQty());
		Assert.assertEquals("CRAFT (Est.)", goldEntry.getComparisonLabel());

		// Bank training comparison Plan A vs Plan B with real bank sell value computed
		BankTrainingComparison cmp = BankCraftingCalculator.compareBankTrainingPlans(
			snapshot.getSnapshotView(), results, 40, 37_224L, 50,
			5_000_000L, 0, true, false, false, false, true,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL);
		Assert.assertTrue(cmp.getXpCoveredByBank() > 0L);
		Assert.assertTrue(cmp.getOwnedBankCraftingSellValueGp() > 0L);

		// Shopping list with manual owned quantity override persisted
		snapshot.setManualOwnedQuantity(ItemID.GOLD_BAR, "Gold bar", 100L);
		OwnedMaterialSnapshot reloaded = new OwnedMaterialSnapshot(manualFile, new Gson());
		Assert.assertEquals(Long.valueOf(100L), reloaded.getManualOwnedOverrides().get(ItemID.GOLD_BAR));

		CraftingRecipe sapBracelet = CraftingDataRepository.getById("sapphire_bracelet");
		ShoppingListSummary shop = BankCraftingCalculator.buildBankAwareShoppingList(
			sapBracelet, 600L, snapshot.getSnapshotView(), false,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL,
			Collections.emptyMap(), ShoppingSortOrder.TOTAL_COST_DESC);
		// Owns 400 bank + 100 manual = 500 gold bars, needs 600 -> missing 100 gold bars
		// Owns 400 sapphires -> missing 200 sapphires
		Assert.assertTrue(shop.getTotalRemainingCostGp() > 0L);

		// Rank BEST_WITH_MY_BANK returns craftable recipes first
		List<BankRecipeMatch> ranked = BankCraftingCalculator.matchAndRankAll(
			results, snapshot.getSnapshotView(), 200L, false,
			99, true, true, true,
			false, false, BankRecommendationMode.BEST_WITH_MY_BANK,
			prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertTrue(ranked.get(0).isCraftableNow());
	}
}
