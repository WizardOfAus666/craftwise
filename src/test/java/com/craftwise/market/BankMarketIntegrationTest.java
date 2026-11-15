package com.craftwise.market;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.bank.OwnedMaterialSnapshot;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.market.BankMarketIntegrator.IntegratedMarketView;
import com.craftwise.market.BankMarketIntegrator.IntegratedOpportunity;
import com.craftwise.market.MarketOpportunity.OpportunityStatus;
import com.craftwise.market.MarketWatcherService.AlertRuleConfig;
import com.craftwise.market.MarketWatcherService.ScanResult;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BankMarketIntegrationTest
{
	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	private static ItemPrice mkPrice(int id, String name, long high, long low, long now)
	{
		return ItemPrice.builder()
			.itemId(id)
			.name(name)
			.highPrice(high)
			.lowPrice(low)
			.highTime(now - 30L)
			.lowTime(now - 30L)
			.fetchedAtEpochSec(now)
			.stale(false)
			.build();
	}

	private static List<CraftingResult> evalPair(Map<Integer, ItemPrice> prices)
	{
		CraftingRecipe sapBracelet = CraftingDataRepository.getById("sapphire_bracelet");
		CraftingRecipe cutRuby = CraftingDataRepository.getById("cut_ruby");
		return Arrays.asList(
			ProfitCalculator.evaluate(sapBracelet, prices, Collections.emptyMap(),
				PriceCalculationMode.INSTANT_BUY_SELL, true, Collections.emptyMap(), Collections.emptyMap(), null),
			ProfitCalculator.evaluate(cutRuby, prices, Collections.emptyMap(),
				PriceCalculationMode.INSTANT_BUY_SELL, true, Collections.emptyMap(), Collections.emptyMap(), null)
		);
	}

	@Test
	public void testNewlyProfitableOpportunityMatchedWithOwnedBankMaterials() throws Exception
	{
		Path wlFile = tempFolder.newFile("wl.json").toPath();
		Path histFile = tempFolder.newFile("hist.json").toPath();
		Path manFile = tempFolder.newFile("man.json").toPath();

		WatchlistManager wl = new WatchlistManager(wlFile, histFile);
		MarketWatcherService watcher = new MarketWatcherService(wl);
		OwnedMaterialSnapshot snapshot = new OwnedMaterialSnapshot(manFile);

		long t0 = 1_700_000_000L;
		AlertRuleConfig rules = AlertRuleConfig.builder()
			.enabled(true)
			.alertOnNewlyProfitable(true)
			.minProfitGp(100L)
			.minInventoryProfitGp(1000L)
			.cooldownSeconds(900L)
			.highProfitThresholdGp(300L)
			.build();

		// Scan 1: Sapphire bracelet losing money (520 + 120 = 640 cost, sells for 600 - 12 tax = 588 -> -52 GP)
		Map<Integer, ItemPrice> p1 = new HashMap<>();
		p1.put(ItemID.SAPPHIRE, mkPrice(ItemID.SAPPHIRE, "Sapphire", 520L, 490L, t0));
		p1.put(ItemID.GOLD_BAR, mkPrice(ItemID.GOLD_BAR, "Gold bar", 120L, 110L, t0));
		p1.put(ItemID.SAPPHIRE_BRACELET_11072, mkPrice(ItemID.SAPPHIRE_BRACELET_11072, "Sapphire bracelet", 640L, 600L, t0));
		p1.put(ItemID.BRACELET_MOULD, mkPrice(ItemID.BRACELET_MOULD, "Bracelet mould", 200L, 100L, t0));
		p1.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, t0));
		p1.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 950L, 900L, t0));
		p1.put(ItemID.CHISEL, mkPrice(ItemID.CHISEL, "Chisel", 20L, 10L, t0));

		ScanResult s1 = watcher.scan(evalPair(p1), p1, rules, t0);

		// Before bank scan: bankDataAvailable = false, filter PROFITABLE_WITH_MY_BANK returns empty
		IntegratedMarketView unavailView = BankMarketIntegrator.integrate(
			s1.getOpportunities(), snapshot.getSnapshotView(), 100L, false,
			99, true, true, true,
			p1, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());
		Assert.assertFalse(unavailView.isBankDataAvailable());
		Assert.assertNull(unavailView.getBestOpportunityUsingYourBank());
		Assert.assertTrue(BankMarketIntegrator.filter(unavailView.getItems(), true).isEmpty());

		// Populate bank with 450 sapphires, 450 gold bars, 1 bracelet mould (and 50 uncut rubies + chisel)
		Map<Integer, Long> bankMap = new HashMap<>();
		bankMap.put(ItemID.SAPPHIRE, 450L);
		bankMap.put(ItemID.GOLD_BAR, 450L);
		bankMap.put(ItemID.BRACELET_MOULD, 1L);
		bankMap.put(ItemID.UNCUT_RUBY, 50L);
		bankMap.put(ItemID.CHISEL, 1L);
		snapshot.updateBankContents(bankMap, Collections.emptyMap(), true, t0 + 10L);

		// Scan 2: Sapphire bracelet surges to 1,100 GP (net ~1,078 GP, profit +438 GP/craft); Ruby remains losing
		long t1 = t0 + 60L;
		Map<Integer, ItemPrice> p2 = new HashMap<>(p1);
		p2.put(ItemID.SAPPHIRE_BRACELET_11072, mkPrice(ItemID.SAPPHIRE_BRACELET_11072, "Sapphire bracelet", 1150L, 1100L, t1));
		ScanResult s2 = watcher.scan(evalPair(p2), p2, rules, t1);

		IntegratedMarketView liveView = BankMarketIntegrator.integrate(
			s2.getOpportunities(), snapshot.getSnapshotView(), 100L, false,
			99, true, true, true,
			p2, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, Collections.emptyMap());

		Assert.assertTrue(liveView.isBankDataAvailable());
		Assert.assertEquals(2, liveView.getCraftableCount());
		Assert.assertEquals(1, liveView.getProfitableWithBankCount());

		Assert.assertNotNull(liveView.getBestOpportunityUsingYourBank());
		Assert.assertEquals("sapphire_bracelet",
			liveView.getBestOpportunityUsingYourBank().getOpportunity().getResult().getRecipe().getId());
		Assert.assertEquals(450L, liveView.getBestOpportunityUsingYourBank().getMaxCraftableFromBank());

		Assert.assertNotNull(liveView.getTopNewlyProfitableWithBank());
		Assert.assertEquals(OpportunityStatus.NEWLY_PROFITABLE,
			liveView.getTopNewlyProfitableWithBank().getOpportunity().getStatus());

		List<IntegratedOpportunity> filtered = BankMarketIntegrator.filter(liveView.getItems(), true);
		Assert.assertEquals(1, filtered.size());
		Assert.assertEquals("sapphire_bracelet", filtered.get(0).getOpportunity().getResult().getRecipe().getId());
	}
}
