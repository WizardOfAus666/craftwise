package com.craftwise.data;

import com.craftwise.CraftWiseConfig;
import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.CraftWiseConfig.RankingMode;
import com.craftwise.api.ApiResponseHandler.TimeseriesData;
import com.craftwise.api.ApiResponseHandler.WikiPageInfo;
import com.craftwise.api.PriceCache;
import com.craftwise.api.WikiDataClient;
import com.craftwise.api.WikiPriceClient;
import com.craftwise.bank.BankCraftingCalculator;
import com.craftwise.bank.BankCraftingCalculator.BankMaterialValueEntry;
import com.craftwise.bank.BankCraftingCalculator.BankRecipeMatch;
import com.craftwise.bank.BankCraftingCalculator.BankRecommendationMode;
import com.craftwise.bank.BankCraftingCalculator.BankTrainingComparison;
import com.craftwise.bank.BankInventoryScanner;
import com.craftwise.bank.OwnedMaterialSnapshot;
import com.craftwise.bank.OwnedMaterialSnapshot.SnapshotView;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.calculator.ProfitCalculator.CustomPriceOverride;
import com.craftwise.market.BankMarketIntegrator;
import com.craftwise.market.BankMarketIntegrator.IntegratedMarketView;
import com.craftwise.market.MarketWatcherService;
import com.craftwise.market.MarketWatcherService.AlertEvent;
import com.craftwise.market.MarketWatcherService.AlertRuleConfig;
import com.craftwise.market.MarketWatcherService.ScanResult;
import com.craftwise.market.WatchlistManager;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.craftwise.model.PlayerCraftingStats;
import com.craftwise.model.ProfitReport;
import com.craftwise.tracker.GoalManager;
import com.craftwise.tracker.SessionTrackerManager;
import com.craftwise.util.XPTable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central coordinator for CraftWise data, live RuneLite player Crafting stats,
 * background OSRS Wiki price fetching, caching, custom overrides, and recommendation ranking.
 */
@Singleton
public class CraftingDataManager
{
	private static final Logger log = LoggerFactory.getLogger(CraftingDataManager.class);

	public enum MethodFilter
	{
		AVAILABLE_TO_ME("Available To Me"),
		UNLOCKED_METHODS("Unlocked (Level)"),
		PROFITABLE_ONLY("Profitable Only"),
		LOSS_MAKING_ONLY("Loss-Making Only"),
		F2P_ONLY("F2P Only"),
		FUTURE_METHODS("Future Unlocks"),
		ALL_METHODS("All Methods");

		private final String label;

		MethodFilter(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final CraftWiseConfig config;
	private final WikiPriceClient wikiPriceClient;
	private final WikiDataClient wikiDataClient;
	private final Gson gson = new Gson();

	private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
	private final Map<Integer, CustomPriceOverride> customPrices = new ConcurrentHashMap<>();
	private final Map<String, Double> customActionsPerHour = new ConcurrentHashMap<>();
	private final Map<Integer, BufferedImage> iconCache = new ConcurrentHashMap<>();

	private ScheduledExecutorService executor;
	private ScheduledFuture<?> refreshTask;

	private volatile Map<Integer, ItemPrice> currentPrices = Collections.emptyMap();
	private volatile PlayerCraftingStats playerStats;
	private volatile ProfitReport latestReport;
	private volatile List<CraftingResult> allResults = Collections.emptyList();
	private volatile boolean manualLevelOverrideActive = false;
	private volatile int manualLevel = 70;
	private volatile long manualXp = XPTable.getXpForLevel(70);
	private volatile int targetLevel = 99;
	private volatile long budgetGp = 5_000_000L;
	private volatile int temporaryBoost = 0;
	private volatile boolean membersAllowed = true;
	private volatile boolean includeQuestLocked = true;
	private volatile boolean includeUnlockRequired = true;
	private final AtomicBoolean refreshing = new AtomicBoolean(false);
	private final SessionTrackerManager sessionTrackerManager = new SessionTrackerManager();
	private final GoalManager goalManager = new GoalManager();
	private final WatchlistManager watchlistManager = new WatchlistManager();
	private final MarketWatcherService marketWatcherService = new MarketWatcherService(watchlistManager);
	private final OwnedMaterialSnapshot ownedMaterialSnapshot = new OwnedMaterialSnapshot();
	private volatile ScanResult latestMarketScan;
	private volatile IntegratedMarketView latestIntegratedMarketView;
	private volatile List<BankRecipeMatch> latestBankMatches = Collections.emptyList();
	private volatile List<BankMaterialValueEntry> latestBankMaterialValues = Collections.emptyList();
	private volatile boolean includeNotedBankCalc = false;
	private volatile long bankTargetCrafts = 100L;
	private volatile BankRecommendationMode bankRecommendationMode = BankRecommendationMode.BEST_WITH_MY_BANK;
	private volatile Consumer<AlertEvent> alertDispatcher;
	private volatile List<String> integrityWarnings = Collections.emptyList();

	@Inject
	public CraftingDataManager(
		Client client,
		ClientThread clientThread,
		ItemManager itemManager,
		ConfigManager configManager,
		CraftWiseConfig config,
		WikiPriceClient wikiPriceClient,
		WikiDataClient wikiDataClient)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.config = config;
		this.wikiPriceClient = wikiPriceClient;
		this.wikiDataClient = wikiDataClient;

		PriceCache cache = wikiPriceClient != null ? wikiPriceClient.getPriceCache() : null;
		if (cache != null && !cache.isEmpty())
		{
			this.currentPrices = cache.getPricesSnapshot(cache.isStale(Instant.now().getEpochSecond()));
		}

		this.playerStats = PlayerCraftingStats.fromLevelAndXp(
			false, true, "Offline / Manual", manualLevel, manualLevel, manualXp, targetLevel);
	}

	public synchronized void startUp()
	{
		syncFromConfig();
		DataIntegrityChecker.IntegrityReport report = DataIntegrityChecker.check(CraftingDataRepository.getAllRecipes());
		this.integrityWarnings = report.getWarnings();
		if (!report.getErrors().isEmpty())
		{
			log.warn("[CraftWise] Recipe integrity errors detected: {}", report.getErrors());
		}
		refreshPlayerStatsFromClient();
		recalculateAll();

		if (executor == null || executor.isShutdown())
		{
			executor = Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "CraftWise-Background");
				t.setDaemon(true);
				return t;
			});
		}
		reschedulePeriodicRefresh();
		refreshPricesNowAsync();
	}

	public synchronized void shutDown()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(true);
			refreshTask = null;
		}
		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}
		changeListeners.clear();
	}

	public void addChangeListener(Runnable listener)
	{
		if (listener != null && !changeListeners.contains(listener))
		{
			changeListeners.add(listener);
		}
	}

	public void removeChangeListener(Runnable listener)
	{
		changeListeners.remove(listener);
	}

	private void notifyListeners()
	{
		for (Runnable listener : changeListeners)
		{
			try
			{
				listener.run();
			}
			catch (Exception ex)
			{
				log.debug("[CraftWise] Listener error: {}", ex.getMessage());
			}
		}
	}

	public synchronized void syncFromConfig()
	{
		if (config == null)
		{
			return;
		}
		this.targetLevel = XPTable.clampLevel(config.defaultTargetLevel());
		this.budgetGp = Math.max(0L, config.defaultBudgetGp());
		this.manualLevel = XPTable.clampLevel(config.manualFallbackLevel());
		if (!manualLevelOverrideActive)
		{
			this.manualXp = XPTable.getXpForLevel(manualLevel);
		}
		loadOverridesFromConfig();
		if (wikiPriceClient != null && wikiPriceClient.getPriceCache() != null)
		{
			wikiPriceClient.getPriceCache().setStaleThresholdSec(Math.max(120L, config.priceRefreshIntervalSeconds() * 3L));
		}
		if (executor != null && !executor.isShutdown())
		{
			reschedulePeriodicRefresh();
		}
	}

	private void reschedulePeriodicRefresh()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
		}
		int intervalSec = config != null ? Math.max(30, config.priceRefreshIntervalSeconds()) : 60;
		refreshTask = executor.scheduleWithFixedDelay(
			this::fetchPricesBlockingSafe,
			intervalSec,
			intervalSec,
			TimeUnit.SECONDS
		);
	}

	public void refreshPricesNowAsync()
	{
		if (executor == null || executor.isShutdown())
		{
			return;
		}
		executor.execute(this::fetchPricesBlockingSafe);
	}

	private void fetchPricesBlockingSafe()
	{
		if (wikiPriceClient == null || !refreshing.compareAndSet(false, true))
		{
			return;
		}
		try
		{
			Map<Integer, ItemPrice> fetched = wikiPriceClient.fetchAllPrices();
			if (fetched != null && !fetched.isEmpty())
			{
				this.currentPrices = fetched;
			}
		}
		catch (IOException ex)
		{
			PriceCache cache = wikiPriceClient.getPriceCache();
			if (cache != null && !cache.isEmpty())
			{
				this.currentPrices = cache.getPricesSnapshot(true);
			}
			log.debug("[CraftWise] Wiki price refresh failed ({}), using cache.", ex.getMessage());
		}
		finally
		{
			refreshing.set(false);
			recalculateAll();
			notifyListeners();
		}
	}

	public void fetchTimeseriesAsync(int itemId, String timestep, Consumer<TimeseriesData> onSuccess, Consumer<String> onError)
	{
		if (wikiPriceClient == null || executor == null || executor.isShutdown())
		{
			if (onError != null)
			{
				onError.accept("Price client unavailable.");
			}
			return;
		}
		executor.execute(() -> {
			try
			{
				TimeseriesData data = wikiPriceClient.fetchTimeseries(itemId, timestep);
				if (onSuccess != null)
				{
					onSuccess.accept(data);
				}
			}
			catch (IOException ex)
			{
				if (onError != null)
				{
					onError.accept(ex.getMessage());
				}
			}
		});
	}

	public void fetchWikiPageInfoAsync(String pageTitle, Consumer<WikiPageInfo> onSuccess, Consumer<String> onError)
	{
		if (wikiDataClient == null || executor == null || executor.isShutdown())
		{
			if (onError != null)
			{
				onError.accept("Wiki client unavailable.");
			}
			return;
		}
		executor.execute(() -> {
			try
			{
				WikiPageInfo info = wikiDataClient.fetchPageInfo(pageTitle);
				if (onSuccess != null)
				{
					onSuccess.accept(info);
				}
			}
			catch (IOException ex)
			{
				if (onError != null)
				{
					onError.accept(ex.getMessage());
				}
			}
		});
	}

	/**
	 * Refreshes player Crafting level and XP from the RuneLite {@link Client}.
	 * Must be called on the client thread (or safely skipped if offline).
	 */
	public synchronized void refreshPlayerStatsFromClient()
	{
		boolean changed = false;
		if (!manualLevelOverrideActive && client != null && client.getGameState() == GameState.LOGGED_IN)
		{
			try
			{
				int real = Math.max(1, client.getRealSkillLevel(Skill.CRAFTING));
				int boosted = Math.max(1, client.getBoostedSkillLevel(Skill.CRAFTING));
				long xp = Math.max(0L, client.getSkillExperience(Skill.CRAFTING));
				if (xp == 0L && real > 1)
				{
					xp = XPTable.getXpForLevel(real);
				}
				String name = "Player";
				Player local = client.getLocalPlayer();
				if (local != null && local.getName() != null && !local.getName().trim().isEmpty())
				{
					name = Text.sanitize(local.getName());
				}
				PlayerCraftingStats next = PlayerCraftingStats.fromLevelAndXp(
					true, false, name, real, boosted, xp, targetLevel);
				changed = !next.equals(this.playerStats);
				this.playerStats = next;
			}
			catch (Throwable ignored)
			{
			}
		}
		else
		{
			PlayerCraftingStats next = PlayerCraftingStats.fromLevelAndXp(
				false, true, "Manual / Offline", manualLevel, manualLevel, manualXp, targetLevel);
			changed = !next.equals(this.playerStats);
			this.playerStats = next;
		}

		if (changed)
		{
			recalculateAll();
			notifyListeners();
		}
	}

	public synchronized void setManualLevel(int level, boolean forceManualOverride)
	{
		int clamped = XPTable.clampLevel(level);
		this.manualLevel = clamped;
		this.manualXp = XPTable.getXpForLevel(clamped);
		this.manualLevelOverrideActive = forceManualOverride;
		refreshPlayerStatsFromClient();
		recalculateAll();
		notifyListeners();
	}

	public synchronized void useLiveClientStats()
	{
		this.manualLevelOverrideActive = false;
		refreshPlayerStatsFromClient();
		recalculateAll();
		notifyListeners();
	}

	public synchronized void setTargetLevel(int targetLevel)
	{
		this.targetLevel = XPTable.clampLevel(targetLevel);
		refreshPlayerStatsFromClient();
		recalculateAll();
		notifyListeners();
	}

	public synchronized void setBudgetGp(long budgetGp)
	{
		this.budgetGp = Math.max(0L, budgetGp);
		notifyListeners();
	}

	public synchronized void setCustomItemPrice(int itemId, Long buyPrice, Long sellPrice)
	{
		if ((buyPrice == null || buyPrice <= 0L) && (sellPrice == null || sellPrice <= 0L))
		{
			customPrices.remove(itemId);
		}
		else
		{
			customPrices.put(itemId, new CustomPriceOverride(buyPrice, sellPrice));
		}
		persistOverridesToConfig();
		recalculateAll();
		notifyListeners();
	}

	public synchronized void setCustomActionsPerHour(String recipeId, Double actionsPerHour)
	{
		if (recipeId == null)
		{
			return;
		}
		if (actionsPerHour == null || actionsPerHour <= 0.0)
		{
			customActionsPerHour.remove(recipeId);
		}
		else
		{
			customActionsPerHour.put(recipeId, actionsPerHour);
		}
		persistOverridesToConfig();
		recalculateAll();
		notifyListeners();
	}

	public synchronized void clearAllCustomOverrides()
	{
		customPrices.clear();
		customActionsPerHour.clear();
		persistOverridesToConfig();
		recalculateAll();
		notifyListeners();
	}

	public void updateConfigSetting(String keyName, Object value)
	{
		if (configManager != null && keyName != null && value != null)
		{
			configManager.setConfiguration(CraftWiseConfig.CONFIG_GROUP, keyName, value);
		}
	}

	/**
	 * Loads a RuneLite item icon asynchronously on the client thread and delivers it on Swing EDT.
	 */
	public void loadItemIcon(int itemId, Consumer<BufferedImage> callback)
	{
		if (callback == null || itemId <= 0)
		{
			return;
		}
		BufferedImage cached = iconCache.get(itemId);
		if (cached != null)
		{
			callback.accept(cached);
			return;
		}
		if (itemManager == null || clientThread == null)
		{
			return;
		}
		clientThread.invokeLater(() -> {
			try
			{
				AsyncBufferedImage img = itemManager.getImage(itemId);
				if (img != null)
				{
					img.onLoaded(() -> {
						iconCache.put(itemId, img);
						javax.swing.SwingUtilities.invokeLater(() -> callback.accept(img));
					});
					// Also deliver immediate frame if already loaded
					if (img.getWidth() > 0 && img.getHeight() > 0)
					{
						iconCache.put(itemId, img);
						javax.swing.SwingUtilities.invokeLater(() -> callback.accept(img));
					}
				}
			}
			catch (Exception ignored)
			{
			}
		});
	}

	public synchronized void recalculateAll()
	{
		PriceCalculationMode mode = config != null ? config.priceCalculationMode() : PriceCalculationMode.INSTANT_BUY_SELL;
		boolean includeTax = config == null || config.includeGeTax();
		Map<String, Integer> nameIndex = wikiPriceClient != null
			? wikiPriceClient.getNameToIdIndex()
			: Collections.emptyMap();

		List<CraftingRecipe> recipes = CraftingDataRepository.getAllRecipes();
		List<CraftingResult> results = new ArrayList<>(recipes.size());
		for (CraftingRecipe recipe : recipes)
		{
			results.add(ProfitCalculator.evaluate(
				recipe, currentPrices, nameIndex, mode, includeTax,
				customPrices, customActionsPerHour, playerStats));
		}

		this.allResults = Collections.unmodifiableList(results);
		this.latestReport = buildReport(results, mode);

		ScanResult scan = marketWatcherService.scan(
			this.allResults, currentPrices, buildAlertRuleConfig(), Instant.now().getEpochSecond());
		this.latestMarketScan = scan;

		recalculateBankMatchesInternal();

		Consumer<AlertEvent> dispatcher = this.alertDispatcher;
		if (dispatcher != null && scan != null && !scan.getTriggeredAlerts().isEmpty())
		{
			for (AlertEvent alert : scan.getTriggeredAlerts())
			{
				try
				{
					dispatcher.accept(alert);
				}
				catch (Exception ex)
				{
					log.debug("[CraftWise] Alert dispatcher error: {}", ex.getMessage());
				}
			}
		}
	}

	private AlertRuleConfig buildAlertRuleConfig()
	{
		return AlertRuleConfig.fromConfig(config);
	}

	private synchronized void recalculateBankMatchesInternal()
	{
		PriceCalculationMode mode = config != null ? config.priceCalculationMode() : PriceCalculationMode.INSTANT_BUY_SELL;
		boolean includeTax = config == null || config.includeGeTax();
		Map<String, Integer> nameIndex = wikiPriceClient != null
			? wikiPriceClient.getNameToIdIndex()
			: Collections.emptyMap();
		int playerLvl = (playerStats != null ? playerStats.getRealLevel() : 1) + temporaryBoost;
		SnapshotView snap = ownedMaterialSnapshot.getSnapshotView();

		if (this.latestMarketScan != null)
		{
			this.latestIntegratedMarketView = BankMarketIntegrator.integrate(
				this.latestMarketScan.getOpportunities(),
				snap,
				bankTargetCrafts,
				includeNotedBankCalc,
				playerLvl,
				membersAllowed,
				includeQuestLocked,
				includeUnlockRequired,
				currentPrices,
				nameIndex,
				mode,
				customPrices);
		}
		else
		{
			this.latestIntegratedMarketView = null;
		}

		if (snap.isBankScanned() || !snap.getItems().isEmpty())
		{
			this.latestBankMatches = BankCraftingCalculator.matchAndRankAll(
				this.allResults,
				snap,
				bankTargetCrafts,
				includeNotedBankCalc,
				playerLvl,
				membersAllowed,
				includeQuestLocked,
				includeUnlockRequired,
				false,
				false,
				bankRecommendationMode,
				currentPrices,
				nameIndex,
				mode,
				customPrices);

			this.latestBankMaterialValues = BankCraftingCalculator.analyzeOwnedMaterials(
				snap,
				this.allResults,
				playerLvl,
				includeNotedBankCalc,
				currentPrices,
				nameIndex,
				mode,
				includeTax,
				customPrices);
		}
		else
		{
			this.latestBankMatches = Collections.emptyList();
			this.latestBankMaterialValues = Collections.emptyList();
		}
	}

	public void recalculateBankMatchesAsync()
	{
		if (executor != null && !executor.isShutdown())
		{
			executor.execute(() -> {
				recalculateBankMatchesInternal();
				notifyListeners();
			});
		}
		else
		{
			recalculateBankMatchesInternal();
			notifyListeners();
		}
	}

	public void scanBankContainer(ItemContainer bankContainer, boolean bankOpen)
	{
		if (bankContainer == null)
		{
			return;
		}
		BankInventoryScanner.scanBank(
			bankContainer, itemManager, ownedMaterialSnapshot, bankOpen, Instant.now().getEpochSecond());
		recalculateBankMatchesAsync();
	}

	public void scanInventoryContainer(ItemContainer invContainer)
	{
		if (invContainer == null)
		{
			return;
		}
		BankInventoryScanner.scanInventory(
			invContainer, itemManager, ownedMaterialSnapshot, Instant.now().getEpochSecond());
		recalculateBankMatchesAsync();
	}

	public void scanContainersOnClientThread()
	{
		if (client == null || clientThread == null)
		{
			return;
		}
		clientThread.invokeLater(() -> {
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			long now = Instant.now().getEpochSecond();
			ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
			if (inv != null)
			{
				BankInventoryScanner.scanInventory(inv, itemManager, ownedMaterialSnapshot, now);
			}
			boolean bankOpen = BankInventoryScanner.isBankInterfaceOpen(client);
			if (bankOpen)
			{
				ItemContainer bank = client.getItemContainer(InventoryID.BANK);
				if (bank != null)
				{
					BankInventoryScanner.scanBank(bank, itemManager, ownedMaterialSnapshot, true, now);
				}
			}
			recalculateBankMatchesAsync();
		});
	}

	public void onBankClosed()
	{
		ownedMaterialSnapshot.onBankClosed();
		recalculateBankMatchesAsync();
	}

	public void onLoggedOut()
	{
		ownedMaterialSnapshot.resetSessionBankState();
		recalculateBankMatchesAsync();
	}

	public void setManualOwnedQuantity(int itemId, String itemName, long qty)
	{
		ownedMaterialSnapshot.setManualOwnedQuantity(itemId, itemName, qty);
		recalculateBankMatchesAsync();
	}

	public void setIncludeNotedBankCalc(boolean includeNoted)
	{
		if (this.includeNotedBankCalc != includeNoted)
		{
			this.includeNotedBankCalc = includeNoted;
			recalculateBankMatchesAsync();
		}
	}

	public void setBankTargetCrafts(long targetCrafts)
	{
		long safe = Math.max(1L, Math.min(1_000_000L, targetCrafts));
		if (this.bankTargetCrafts != safe)
		{
			this.bankTargetCrafts = safe;
			recalculateBankMatchesAsync();
		}
	}

	public void setBankRecommendationMode(BankRecommendationMode mode)
	{
		if (mode != null && this.bankRecommendationMode != mode)
		{
			this.bankRecommendationMode = mode;
			recalculateBankMatchesAsync();
		}
	}

	public BankTrainingComparison computeBankTrainingComparison(int startLvl, long startXp, int targetLvl, long budget)
	{
		PriceCalculationMode mode = config != null ? config.priceCalculationMode() : PriceCalculationMode.INSTANT_BUY_SELL;
		boolean includeTax = config == null || config.includeGeTax();
		Map<String, Integer> nameIndex = wikiPriceClient != null
			? wikiPriceClient.getNameToIdIndex()
			: Collections.emptyMap();
		return BankCraftingCalculator.compareBankTrainingPlans(
			ownedMaterialSnapshot.getSnapshotView(),
			this.allResults,
			startLvl,
			startXp,
			targetLvl,
			budget,
			temporaryBoost,
			membersAllowed,
			includeQuestLocked,
			includeUnlockRequired,
			includeNotedBankCalc,
			includeTax,
			currentPrices,
			nameIndex,
			mode);
	}

	private ProfitReport buildReport(List<CraftingResult> results, PriceCalculationMode mode)
	{
		int complete = 0;
		int incomplete = 0;
		int profitableUnlocked = 0;
		int playerLevel = playerStats != null ? playerStats.getRealLevel() : 1;

		List<CraftingResult> unlockedComplete = new ArrayList<>();
		for (CraftingResult r : results)
		{
			if (r.isPriceDataComplete())
			{
				complete++;
				if (r.getRecipe().getRequiredLevel() <= playerLevel)
				{
					unlockedComplete.add(r);
					if (r.isProfitable())
					{
						profitableUnlocked++;
					}
				}
			}
			else
			{
				incomplete++;
			}
		}

		CraftingResult bestProfitHr = unlockedComplete.stream()
			.max(Comparator.comparingDouble(CraftingResult::getProfitPerHour))
			.orElse(null);

		CraftingResult bestProfitCraft = unlockedComplete.stream()
			.max(Comparator.comparingDouble(CraftingResult::getProfitPerCraft))
			.orElse(null);

		CraftingResult bestXpHr = unlockedComplete.stream()
			.filter(r -> r.getXpPerAction() > 0.0)
			.max(Comparator.comparingDouble(CraftingResult::getXpPerHour))
			.orElse(null);

		CraftingResult cheapest = unlockedComplete.stream()
			.filter(r -> r.getXpPerAction() > 0.0)
			.max(Comparator.comparingDouble(CraftingResult::getGpPerXp))
			.orElse(null);

		CraftingResult balanced = rankResults(unlockedComplete, RankingMode.BEST_XP_PROFIT_BALANCE, budgetGp)
			.stream().findFirst().orElse(null);

		boolean live = wikiPriceClient != null && wikiPriceClient.isLiveConnected();
		boolean stale = wikiPriceClient != null && wikiPriceClient.getPriceCache() != null
			&& wikiPriceClient.getPriceCache().isStale(Instant.now().getEpochSecond());
		String msg = wikiPriceClient != null ? wikiPriceClient.getStatusMessage() : "Offline";

		return ProfitReport.builder()
			.generatedAtEpochSec(Instant.now().getEpochSecond())
			.priceMode(mode)
			.apiLive(live)
			.usingStaleCache(stale)
			.marketStatusMessage(msg)
			.totalRecipes(results.size())
			.completeRecipes(complete)
			.incompleteRecipes(incomplete)
			.profitableUnlockedRecipes(profitableUnlocked)
			.bestProfitPerHour(bestProfitHr)
			.bestProfitPerCraft(bestProfitCraft)
			.bestXpPerHour(bestXpHr)
			.cheapestTrainingMethod(cheapest)
			.bestBalancedMethod(balanced)
			.allResults(results)
			.build();
	}

	/**
	 * Filters and sorts recipes according to search text, category, filter pill,
	 * numeric constraints, and ranking mode.
	 */
	public List<CraftingResult> queryResults(
		String searchQuery,
		CraftingCategory categoryFilter,
		MethodFilter methodFilter,
		RankingMode rankingMode,
		int minLevel,
		int maxLevel,
		double minXpPerAction,
		double minXpPerHour,
		long maxBudgetPerActionOrRun,
		double maxGpLossPerXp)
	{
		int playerLevel = playerStats != null ? playerStats.getRealLevel() : 1;
		String q = searchQuery != null ? searchQuery.trim().toLowerCase(Locale.ROOT) : "";

		List<CraftingResult> filtered = new ArrayList<>();
		for (CraftingResult r : allResults)
		{
			CraftingRecipe rec = r.getRecipe();
			if (categoryFilter != null && rec.getCategory() != categoryFilter)
			{
				continue;
			}
			if (rec.getRequiredLevel() < minLevel || rec.getRequiredLevel() > maxLevel)
			{
				continue;
			}
			if (rec.getXpPerAction() < minXpPerAction || r.getXpPerHour() < minXpPerHour)
			{
				continue;
			}
			if (!q.isEmpty())
			{
				boolean match = rec.getName().toLowerCase(Locale.ROOT).contains(q)
					|| rec.getCategory().getDisplayName().toLowerCase(Locale.ROOT).contains(q)
					|| rec.getMethodVariant().toLowerCase(Locale.ROOT).contains(q);
				if (!match)
				{
					continue;
				}
			}

			if (methodFilter != null)
			{
				switch (methodFilter)
				{
					case AVAILABLE_TO_ME:
					{
						int effectiveLvl = rec.isBoostable() ? playerLevel + temporaryBoost : playerLevel;
						if (rec.getRequiredLevel() > effectiveLvl)
						{
							continue;
						}
						if (!membersAllowed && rec.isMembers())
						{
							continue;
						}
						if (!includeQuestLocked && rec.hasQuestRequirement())
						{
							continue;
						}
						if (!includeUnlockRequired && rec.hasUnlockRequirement())
						{
							continue;
						}
						break;
					}
					case UNLOCKED_METHODS:
						if (rec.getRequiredLevel() > (rec.isBoostable() ? playerLevel + temporaryBoost : playerLevel))
						{
							continue;
						}
						break;
					case F2P_ONLY:
						if (rec.isMembers())
						{
							continue;
						}
						break;
					case FUTURE_METHODS:
						if (rec.getRequiredLevel() <= playerLevel)
						{
							continue;
						}
						break;
					case PROFITABLE_ONLY:
						if (!r.isProfitable())
						{
							continue;
						}
						break;
					case LOSS_MAKING_ONLY:
						if (!r.isLossMaking())
						{
							continue;
						}
						break;
					case ALL_METHODS:
					default:
						break;
				}
			}

			if (maxBudgetPerActionOrRun > 0L && r.isPriceDataComplete() && r.getMaterialCostPerCraft() > maxBudgetPerActionOrRun)
			{
				continue;
			}
			if (maxGpLossPerXp >= 0.0 && r.isPriceDataComplete() && !Double.isNaN(r.getGpPerXp()))
			{
				double lossPerXp = -r.getGpPerXp();
				if (lossPerXp > maxGpLossPerXp)
				{
					continue;
				}
			}

			filtered.add(r);
		}

		return rankResults(filtered, rankingMode, maxBudgetPerActionOrRun > 0L ? maxBudgetPerActionOrRun : budgetGp);
	}

	public static List<CraftingResult> rankResults(
		List<CraftingResult> results,
		RankingMode mode,
		long budgetGp)
	{
		if (results == null || results.isEmpty())
		{
			return Collections.emptyList();
		}
		List<CraftingResult> sorted = new ArrayList<>(results);
		RankingMode safeMode = mode != null ? mode : RankingMode.BEST_XP_PROFIT_BALANCE;

		sorted.sort((a, b) -> {
			// Always place complete-price recipes ahead of missing-price recipes
			if (a.isPriceDataComplete() != b.isPriceDataComplete())
			{
				return a.isPriceDataComplete() ? -1 : 1;
			}
			switch (safeMode)
			{
				case HIGHEST_XP_PER_HOUR:
					return Double.compare(b.getXpPerHour(), a.getXpPerHour());
				case HIGHEST_PROFIT_PER_HOUR:
					return Double.compare(b.getProfitPerHour(), a.getProfitPerHour());
				case HIGHEST_PROFIT_PER_ITEM:
					return Double.compare(b.getProfitPerCraft(), a.getProfitPerCraft());
				case LOWEST_COST_PER_XP:
					return Double.compare(safeGpPerXp(b), safeGpPerXp(a));
				case LOWEST_TOTAL_COST_TO_TARGET:
					return Double.compare(a.getTotalCostToTarget(), b.getTotalCostToTarget());
				case BEST_WITHIN_BUDGET:
					boolean aFits = a.getMaterialCostPerCraft() <= budgetGp;
					boolean bFits = b.getMaterialCostPerCraft() <= budgetGp;
					if (aFits != bFits)
					{
						return aFits ? -1 : 1;
					}
					return Double.compare(balanceScore(b), balanceScore(a));
				case BEST_XP_PROFIT_BALANCE:
				default:
					return Double.compare(balanceScore(b), balanceScore(a));
			}
		});
		return sorted;
	}

	private static double safeGpPerXp(CraftingResult r)
	{
		return Double.isNaN(r.getGpPerXp()) ? -1_000_000.0 : r.getGpPerXp();
	}

	/**
	 * Balanced heuristic combining XP/hr (in k/hr) and GP/XP sustainability,
	 * rewarding profitable or low-cost fast methods over extreme GP-sink outliers.
	 */
	private static double balanceScore(CraftingResult r)
	{
		if (!r.isPriceDataComplete() || r.getXpPerHour() <= 0.0)
		{
			return -1e9;
		}
		double xpK = r.getXpPerHour() / 1000.0;
		double gpXp = Double.isNaN(r.getGpPerXp()) ? -50.0 : Math.max(-30.0, Math.min(15.0, r.getGpPerXp()));
		// Each +1 gp/xp shifts effective score favorably while still valuing high XP/hr
		return xpK * (1.0 + gpXp * 0.04) + (gpXp * 8.0);
	}

	private void loadOverridesFromConfig()
	{
		if (config == null)
		{
			return;
		}
		try
		{
			String pricesJson = config.customPricesJson();
			if (pricesJson != null && !pricesJson.trim().isEmpty())
			{
				Type type = new TypeToken<Map<Integer, CustomPriceOverride>>() {}.getType();
				Map<Integer, CustomPriceOverride> loaded = gson.fromJson(pricesJson, type);
				if (loaded != null)
				{
					customPrices.clear();
					customPrices.putAll(loaded);
				}
			}
		}
		catch (Exception ex)
		{
			log.debug("[CraftWise] Could not parse customPricesJson: {}", ex.getMessage());
		}

		try
		{
			String xpJson = config.customXpRatesJson();
			if (xpJson != null && !xpJson.trim().isEmpty())
			{
				Type type = new TypeToken<Map<String, Double>>() {}.getType();
				Map<String, Double> loaded = gson.fromJson(xpJson, type);
				if (loaded != null)
				{
					customActionsPerHour.clear();
					customActionsPerHour.putAll(loaded);
				}
			}
		}
		catch (Exception ex)
		{
			log.debug("[CraftWise] Could not parse customXpRatesJson: {}", ex.getMessage());
		}
	}

	private void persistOverridesToConfig()
	{
		if (configManager == null)
		{
			return;
		}
		configManager.setConfiguration(CraftWiseConfig.CONFIG_GROUP, "customPricesJson", gson.toJson(customPrices));
		configManager.setConfiguration(CraftWiseConfig.CONFIG_GROUP, "customXpRatesJson", gson.toJson(customActionsPerHour));
	}

	// Getters & 2.0 Preferences
	public CraftWiseConfig getConfig() { return config; }
	public WikiPriceClient getWikiPriceClient() { return wikiPriceClient; }
	public WikiDataClient getWikiDataClient() { return wikiDataClient; }
	public Map<Integer, ItemPrice> getCurrentPrices() { return currentPrices; }
	public PlayerCraftingStats getPlayerStats() { return playerStats; }
	public ProfitReport getLatestReport() { return latestReport; }
	public List<CraftingResult> getAllResults() { return allResults; }
	public int getTargetLevel() { return targetLevel; }
	public long getBudgetGp() { return budgetGp; }
	public int getTemporaryBoost() { return temporaryBoost; }
	public void setTemporaryBoost(int boost) { this.temporaryBoost = Math.max(0, Math.min(5, boost)); notifyListeners(); }
	public boolean isMembersAllowed() { return membersAllowed; }
	public void setMembersAllowed(boolean allowed) { this.membersAllowed = allowed; notifyListeners(); }
	public boolean isIncludeQuestLocked() { return includeQuestLocked; }
	public void setIncludeQuestLocked(boolean include) { this.includeQuestLocked = include; notifyListeners(); }
	public boolean isIncludeUnlockRequired() { return includeUnlockRequired; }
	public void setIncludeUnlockRequired(boolean include) { this.includeUnlockRequired = include; notifyListeners(); }
	public SessionTrackerManager getSessionTrackerManager() { return sessionTrackerManager; }
	public GoalManager getGoalManager() { return goalManager; }
	public void notifyListenersPublic() { notifyListeners(); }
	public List<String> getIntegrityWarnings() { return integrityWarnings; }
	public boolean isManualLevelOverrideActive() { return manualLevelOverrideActive; }
	public boolean isRefreshing() { return refreshing.get(); }
	public Map<Integer, CustomPriceOverride> getCustomPrices() { return Collections.unmodifiableMap(new HashMap<>(customPrices)); }
	public Map<String, Double> getCustomActionsPerHour() { return Collections.unmodifiableMap(new HashMap<>(customActionsPerHour)); }
	public WatchlistManager getWatchlistManager() { return watchlistManager; }
	public MarketWatcherService getMarketWatcherService() { return marketWatcherService; }
	public OwnedMaterialSnapshot getOwnedMaterialSnapshot() { return ownedMaterialSnapshot; }
	public SnapshotView getBankSnapshotView() { return ownedMaterialSnapshot.getSnapshotView(); }
	public ScanResult getLatestMarketScan() { return latestMarketScan; }
	public IntegratedMarketView getLatestIntegratedMarketView() { return latestIntegratedMarketView; }
	public List<BankRecipeMatch> getLatestBankMatches() { return latestBankMatches; }
	public List<BankMaterialValueEntry> getLatestBankMaterialValues() { return latestBankMaterialValues; }
	public boolean isIncludeNotedBankCalc() { return includeNotedBankCalc; }
	public long getBankTargetCrafts() { return bankTargetCrafts; }
	public BankRecommendationMode getBankRecommendationMode() { return bankRecommendationMode; }
	public void setAlertDispatcher(Consumer<AlertEvent> dispatcher) { this.alertDispatcher = dispatcher; }
}
