package com.craftwise.market;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.data.CraftingCategory;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import com.craftwise.market.MarketOpportunity.OpportunityStatus;
import com.craftwise.market.MarketOpportunity.PriceFreshness;
import com.craftwise.market.MarketWatcherService.AlertEvent;
import com.craftwise.market.MarketWatcherService.AlertRuleConfig;
import com.craftwise.market.MarketWatcherService.ScanResult;
import com.craftwise.market.MarketWatcherService.ScannerFilterCriteria;
import com.craftwise.market.MarketWatcherService.ScannerFilterMode;
import com.craftwise.market.MarketWatcherService.ScannerSortMode;
import com.craftwise.market.WatchlistManager.ProfitHistoryPoint;
import com.craftwise.market.WatchlistManager.ProfitHistoryStats;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class MarketWatcherTest
{
	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	private static ItemPrice mkPrice(int id, String name, long high, long low, long ageSec, boolean stale)
	{
		long now = System.currentTimeMillis() / 1000L;
		return ItemPrice.builder()
			.itemId(id)
			.name(name)
			.highPrice(high)
			.lowPrice(low)
			.highTime(now - ageSec)
			.lowTime(now - ageSec)
			.avg5mHigh(high)
			.avg5mLow(low)
			.vol5mHigh(100L)
			.vol5mLow(100L)
			.avg1hHigh(high)
			.avg1hLow(low)
			.vol1hHigh(1000L)
			.vol1hLow(1000L)
			.fetchedAtEpochSec(now)
			.stale(stale)
			.build();
	}

	private static List<CraftingResult> evalRuby(Map<Integer, ItemPrice> prices)
	{
		CraftingRecipe cutRuby = CraftingDataRepository.getById("cut_ruby");
		CraftingResult r = ProfitCalculator.evaluate(
			cutRuby, prices, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL,
			true, Collections.emptyMap(), Collections.emptyMap(), null);
		return Collections.singletonList(r);
	}

	@Test
	public void testPriceIncreaseAndNewlyProfitableDetectionWithTax() throws Exception
	{
		Path wlFile = tempFolder.newFile("wl.json").toPath();
		Path histFile = tempFolder.newFile("hist.json").toPath();
		WatchlistManager wl = new WatchlistManager(wlFile, histFile, new Gson());
		wl.addWatchedRecipe("cut_ruby");
		MarketWatcherService service = new MarketWatcherService(wl);

		AlertRuleConfig rules = AlertRuleConfig.builder()
			.enabled(true)
			.alertOnNewlyProfitable(true)
			.minProfitGp(200L)
			.minInventoryProfitGp(4000L)
			.minImprovementPercent(10.0)
			.cooldownSeconds(900L)
			.highProfitThresholdGp(250L)
			.build();

		long t0 = 1_700_000_000L;

		// Scan 1: Uncut ruby costs 1,000 GP; Ruby sells for 900 GP (tax: floor(900/50)=18 GP, net=882 GP, loss=-118 GP)
		Map<Integer, ItemPrice> p1 = new HashMap<>();
		p1.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 30L, false));
		p1.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 950L, 900L, 30L, false));
		ScanResult s1 = service.scan(evalRuby(p1), p1, rules, t0);
		Assert.assertEquals(1, s1.getOpportunities().size());
		Assert.assertEquals(OpportunityStatus.UNPROFITABLE, s1.getOpportunities().get(0).getStatus());
		Assert.assertEquals(-118.0, s1.getOpportunities().get(0).getResult().getProfitPerCraft(), 0.01);
		Assert.assertTrue(s1.getTriggeredAlerts().isEmpty());

		// Scan 2 (t0 + 60): Ruby rises to 1,350 GP (tax: floor(1350/50)=27 GP, net=1323 GP, profit=+323 GP/craft)
		// 28 slots/inv * 323 = 9,044 GP/inv -> triggers NEWLY_PROFITABLE, PROFIT_THRESHOLD, INVENTORY_THRESHOLD, MARGIN_IMPROVED
		Map<Integer, ItemPrice> p2 = new HashMap<>();
		p2.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 30L, false));
		p2.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 1400L, 1350L, 30L, false));
		ScanResult s2 = service.scan(evalRuby(p2), p2, rules, t0 + 60L);
		MarketOpportunity o2 = s2.getOpportunities().get(0);
		Assert.assertEquals(OpportunityStatus.NEWLY_PROFITABLE, o2.getStatus());
		Assert.assertEquals(323.0, o2.getResult().getProfitPerCraft(), 0.01);
		Assert.assertEquals(441.0, o2.getProfitDeltaGp(), 0.01);
		Assert.assertFalse(s2.getTriggeredAlerts().isEmpty());
		Assert.assertTrue(s2.getTriggeredAlerts().stream().anyMatch(a -> "NEWLY_PROFITABLE".equals(a.getAlertType())));
		Assert.assertTrue(s2.getTriggeredAlerts().stream().anyMatch(a -> "PROFIT_THRESHOLD".equals(a.getAlertType())));
		Assert.assertTrue(s2.getTriggeredAlerts().stream().anyMatch(a -> "INVENTORY_THRESHOLD".equals(a.getAlertType())));

		// Scan 3 (t0 + 120): Identical prices -> NO duplicate alerts fired!
		ScanResult s3 = service.scan(evalRuby(p2), p2, rules, t0 + 120L);
		Assert.assertTrue("Duplicate alerts must be suppressed on unchanged prices", s3.getTriggeredAlerts().isEmpty());

		// Historical snapshots recorded & persisted for watched recipe
		List<ProfitHistoryPoint> hist = wl.getHistoryWindow("cut_ruby", 86400L, t0 + 120L);
		Assert.assertEquals(2, hist.size()); // Scan 1 & Scan 2 (Scan 3 had identical profit & spacing=60 -> updated/kept cleanly)
		ProfitHistoryStats stats = WatchlistManager.computeStats(hist);
		Assert.assertEquals(-118L, stats.getWorstProfitGp());
		Assert.assertEquals(323L, stats.getBestProfitGp());
		Assert.assertEquals("Rising", stats.getTrendLabel());
	}

	@Test
	public void testDecliningWatchedAlertAndBreakEvenTransition() throws Exception
	{
		Path wlFile = tempFolder.newFile("wl2.json").toPath();
		Path histFile = tempFolder.newFile("hist2.json").toPath();
		WatchlistManager wl = new WatchlistManager(wlFile, histFile, new Gson());
		wl.addWatchedRecipe("cut_ruby");
		MarketWatcherService service = new MarketWatcherService(wl);

		AlertRuleConfig rules = AlertRuleConfig.builder()
			.enabled(true)
			.alertOnBreakEven(true)
			.alertOnWatchedLoss(true)
			.minProfitGp(10_000L)
			.minInventoryProfitGp(100_000L)
			.cooldownSeconds(60L)
			.build();

		long t0 = 1_700_100_000L;

		// Scan 1: Losing badly (-300 GP/craft)
		Map<Integer, ItemPrice> pLoss = new HashMap<>();
		pLoss.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 20L, false));
		pLoss.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 750L, 714L, 20L, false));
		service.scan(evalRuby(pLoss), pLoss, rules, t0);

		// Scan 2: Price rises to near break-even (Ruby = 1020 GP, tax 20 GP -> net 1000 GP -> 0 GP profit!)
		Map<Integer, ItemPrice> pBe = new HashMap<>();
		pBe.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 20L, false));
		pBe.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 1050L, 1020L, 20L, false));
		ScanResult sBe = service.scan(evalRuby(pBe), pBe, rules, t0 + 120L);
		Assert.assertEquals(OpportunityStatus.NEAR_BREAK_EVEN, sBe.getOpportunities().get(0).getStatus());
		Assert.assertTrue(sBe.getTriggeredAlerts().stream().anyMatch(a -> "BREAK_EVEN_REACHED".equals(a.getAlertType())));

		// Scan 3: Drops sharply back to loss (-150 GP/craft) -> triggers WATCHED_LOSS and DECLINING status
		Map<Integer, ItemPrice> pDrop = new HashMap<>();
		pDrop.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 20L, false));
		pDrop.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 900L, 867L, 20L, false));
		ScanResult sDrop = service.scan(evalRuby(pDrop), pDrop, rules, t0 + 240L);
		Assert.assertEquals(OpportunityStatus.DECLINING, sDrop.getOpportunities().get(0).getStatus());
		Assert.assertTrue(sDrop.getTriggeredAlerts().stream().anyMatch(a -> "WATCHED_LOSS".equals(a.getAlertType())));

		// Verify persistence reload of watchlist & history
		WatchlistManager reloaded = new WatchlistManager(wlFile, histFile, new Gson());
		Assert.assertTrue(reloaded.isRecipeWatched("cut_ruby"));
		Assert.assertFalse(reloaded.getHistoryWindow("cut_ruby", 86400L, t0 + 300L).isEmpty());
	}

	@Test
	public void testStaleMissingPricesAndUnverifiedRecipeFiltering() throws Exception
	{
		Path wlFile = tempFolder.newFile("wl3.json").toPath();
		Path histFile = tempFolder.newFile("hist3.json").toPath();
		MarketWatcherService service = new MarketWatcherService(new WatchlistManager(wlFile, histFile, new Gson()));

		// Missing price must report UNAVAILABLE and not 0 GP fake profit
		Map<Integer, ItemPrice> missingMap = new HashMap<>();
		missingMap.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 20L, false));
		ScanResult sMissing = service.scan(evalRuby(missingMap), missingMap, AlertRuleConfig.builder().build(), 1_700_200_000L);
		Assert.assertEquals(PriceFreshness.UNAVAILABLE, sMissing.getOpportunities().get(0).getFreshness());
		Assert.assertEquals(OpportunityStatus.UNAVAILABLE, sMissing.getOpportunities().get(0).getStatus());
		Assert.assertFalse(sMissing.getOpportunities().get(0).getResult().isPriceDataComplete());

		// Stale price (>30m / stale=true) must classify as STALE and suppress NEWLY_PROFITABLE false alert
		Map<Integer, ItemPrice> staleMap = new HashMap<>();
		staleMap.put(ItemID.UNCUT_RUBY, mkPrice(ItemID.UNCUT_RUBY, "Uncut ruby", 1000L, 950L, 3600L, true));
		staleMap.put(ItemID.RUBY, mkPrice(ItemID.RUBY, "Ruby", 1400L, 1300L, 3600L, true));
		ScanResult sStale = service.scan(evalRuby(staleMap), staleMap, AlertRuleConfig.builder().build(), 1_700_200_060L);
		Assert.assertEquals(PriceFreshness.STALE, sStale.getOpportunities().get(0).getFreshness());

		// NEEDS_REVIEW / UNSUPPORTED recipe must NOT be emitted by Market Watcher scanner
		CraftingRecipe opal = CraftingDataRepository.getById("cut_opal"); // marked NEEDS_REVIEW for variable crush rate
		Assert.assertEquals(VerificationStatus.NEEDS_REVIEW, opal.getVerificationStatus());
		CraftingResult opalEval = ProfitCalculator.evaluate(
			opal, staleMap, Collections.emptyMap(), PriceCalculationMode.INSTANT_BUY_SELL, true,
			Collections.emptyMap(), Collections.emptyMap(), null);
		ScanResult sUnverified = service.scan(Collections.singletonList(opalEval), staleMap, AlertRuleConfig.builder().build(), 1_700_200_120L);
		Assert.assertTrue(sUnverified.getOpportunities().isEmpty());

		// Multi-filter test
		List<MarketOpportunity> filtered = MarketWatcherService.filterAndSort(
			sStale.getOpportunities(),
			ScannerFilterCriteria.builder()
				.mode(ScannerFilterMode.PROFITABLE_ONLY)
				.category(CraftingCategory.GEM_CUTTING)
				.minLevel(30)
				.minProfitGp(100.0)
				.build(),
			ScannerSortMode.PROFIT_PER_HOUR);
		Assert.assertEquals(1, filtered.size());
	}
}
