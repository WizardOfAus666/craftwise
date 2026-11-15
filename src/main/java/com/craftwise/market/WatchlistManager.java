package com.craftwise.market;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Persistent watchlist and historical profitability tracker saved locally under ~/.runelite/craftwise/.
 * Stores only genuine observed market evaluations (never fabricates missing historical records).
 */
@Slf4j
public class WatchlistManager
{
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class WatchedItemRecord
	{
		private int itemId;
		private String itemName;
		private Long lastHighPrice;
		private Long lastLowPrice;
		private Long previousHighPrice;
		private Long previousLowPrice;
		private long lastCheckedEpochSec;
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ProfitHistoryPoint
	{
		private long timestampEpochSec;
		private long materialCostGp;
		private long outputValueGp;
		private long profitGp;
		private long breakEvenSellGp;
	}

	@Data
	@Builder
	public static class ProfitHistoryStats
	{
		private final int sampleCount;
		private final long bestProfitGp;
		private final long worstProfitGp;
		private final long currentProfitGp;
		private final double averageProfitGp;
		private final String trendLabel; // "Rising", "Falling", "Stable", "No Data"
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class WatchlistState
	{
		private Set<String> recipeIds = new LinkedHashSet<>();
		private Map<Integer, WatchedItemRecord> watchedItems = new LinkedHashMap<>();
	}

	private static final int MAX_HISTORY_POINTS_PER_RECIPE = 720; // ~30 days @ hourly or 60d @ 2h
	private static final long MIN_SNAPSHOT_SPACING_SEC = 60L;
	private static final long MAX_HISTORY_AGE_SEC = 30L * 86400L;

	private static final Type HISTORY_TYPE = new TypeToken<Map<String, List<ProfitHistoryPoint>>>()
	{
	}.getType();

	private volatile Filepath watchlistFile;
	private volatile Filepath historyFile;
	private final Gson gson;
	private final Set<String> watchedRecipeIds = Collections.synchronizedSet(new LinkedHashSet<>());
	private final Map<Integer, WatchedItemRecord> watchedItems = Collections.synchronizedMap(new LinkedHashMap<>());
	private final Map<String, List<ProfitHistoryPoint>> historyByRecipeId = Collections.synchronizedMap(new LinkedHashMap<>());

	public WatchlistManager(Gson gson)
	{
		this(null, null, gson);
	}

	public WatchlistManager(Filepath watchlistFile, Filepath historyFile, Gson gson)
	{
		this.watchlistFile = watchlistFile;
		this.historyFile = historyFile;
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		load();
	}

	public synchronized void initializeStorage(Filepath watchlistFile, Filepath historyFile)
	{
		this.watchlistFile = watchlistFile;
		this.historyFile = historyFile;
		load();
	}

	public synchronized void load()
	{
		watchedRecipeIds.clear();
		watchedItems.clear();
		historyByRecipeId.clear();

		Filepath wlFile = this.watchlistFile;
		if (wlFile != null && wlFile.exists())
		{
			try (Reader reader = wlFile.openReader())
			{
				WatchlistState state = gson.fromJson(reader, WatchlistState.class);
				if (state != null)
				{
					if (state.getRecipeIds() != null)
					{
						watchedRecipeIds.addAll(state.getRecipeIds());
					}
					if (state.getWatchedItems() != null)
					{
						watchedItems.putAll(state.getWatchedItems());
					}
				}
			}
			catch (Exception ex)
			{
				log.warn("Failed to load CraftWise watchlist from {}", wlFile, ex);
			}
		}

		Filepath histFile = this.historyFile;
		if (histFile != null && histFile.exists())
		{
			try (Reader reader = histFile.openReader())
			{
				Map<String, List<ProfitHistoryPoint>> loaded = gson.fromJson(reader, HISTORY_TYPE);
				if (loaded != null)
				{
					historyByRecipeId.putAll(loaded);
				}
			}
			catch (Exception ex)
			{
				log.warn("Failed to load CraftWise profit history from {}", histFile, ex);
			}
		}
	}

	public synchronized void saveWatchlist()
	{
		Filepath wlFile = this.watchlistFile;
		if (wlFile == null)
		{
			return;
		}
		try
		{
			ensureParent(wlFile);
			WatchlistState state;
			synchronized (watchedRecipeIds)
			{
				synchronized (watchedItems)
				{
					state = new WatchlistState(new LinkedHashSet<>(watchedRecipeIds), new LinkedHashMap<>(watchedItems));
				}
			}
			wlFile.write(gson.toJson(state));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise watchlist to {}", wlFile, ex);
		}
	}

	public synchronized void saveHistory()
	{
		Filepath histFile = this.historyFile;
		if (histFile == null)
		{
			return;
		}
		try
		{
			ensureParent(histFile);
			Map<String, List<ProfitHistoryPoint>> copy;
			synchronized (historyByRecipeId)
			{
				copy = new LinkedHashMap<>(historyByRecipeId);
			}
			histFile.write(gson.toJson(copy));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise profit history to {}", histFile, ex);
		}
	}

	private static void ensureParent(Filepath file) throws IOException
	{
		Filepath parent = file.getParent();
		if (parent != null && !parent.exists())
		{
			parent.createDirectories();
		}
	}

	public synchronized boolean addWatchedRecipe(String recipeId)
	{
		if (recipeId == null || recipeId.trim().isEmpty())
		{
			return false;
		}
		boolean added = watchedRecipeIds.add(recipeId.trim());
		if (added)
		{
			saveWatchlist();
		}
		return added;
	}

	public synchronized boolean removeWatchedRecipe(String recipeId)
	{
		if (recipeId == null)
		{
			return false;
		}
		boolean removed = watchedRecipeIds.remove(recipeId);
		if (removed)
		{
			saveWatchlist();
		}
		return removed;
	}

	public boolean isRecipeWatched(String recipeId)
	{
		return recipeId != null && watchedRecipeIds.contains(recipeId);
	}

	public Set<String> getWatchedRecipeIds()
	{
		synchronized (watchedRecipeIds)
		{
			return Collections.unmodifiableSet(new LinkedHashSet<>(watchedRecipeIds));
		}
	}

	public synchronized void addWatchedItem(int itemId, String itemName)
	{
		if (itemId <= 0)
		{
			return;
		}
		watchedItems.putIfAbsent(itemId, WatchedItemRecord.builder()
			.itemId(itemId)
			.itemName(itemName != null ? itemName : "Item #" + itemId)
			.build());
		saveWatchlist();
	}

	public synchronized void removeWatchedItem(int itemId)
	{
		if (watchedItems.remove(itemId) != null)
		{
			saveWatchlist();
		}
	}

	public synchronized void updateWatchedItemPrice(int itemId, Long highPrice, Long lowPrice, long epochSec)
	{
		WatchedItemRecord rec = watchedItems.get(itemId);
		if (rec == null)
		{
			return;
		}
		if (rec.getLastHighPrice() != null)
		{
			rec.setPreviousHighPrice(rec.getLastHighPrice());
		}
		if (rec.getLastLowPrice() != null)
		{
			rec.setPreviousLowPrice(rec.getLastLowPrice());
		}
		if (highPrice != null && highPrice > 0L)
		{
			rec.setLastHighPrice(highPrice);
		}
		if (lowPrice != null && lowPrice > 0L)
		{
			rec.setLastLowPrice(lowPrice);
		}
		rec.setLastCheckedEpochSec(epochSec);
	}

	public List<WatchedItemRecord> getWatchedItems()
	{
		synchronized (watchedItems)
		{
			return Collections.unmodifiableList(new ArrayList<>(watchedItems.values()));
		}
	}

	/**
	 * Records an authentic observed market profitability snapshot for a recipe.
	 */
	public synchronized void recordProfitSnapshot(
		String recipeId,
		long timestampEpochSec,
		long materialCostGp,
		long outputValueGp,
		long profitGp,
		long breakEvenSellGp,
		boolean persistImmediately)
	{
		if (recipeId == null || timestampEpochSec <= 0L)
		{
			return;
		}
		List<ProfitHistoryPoint> list = historyByRecipeId.computeIfAbsent(recipeId, k -> new ArrayList<>());
		if (!list.isEmpty())
		{
			ProfitHistoryPoint last = list.get(list.size() - 1);
			if ((timestampEpochSec - last.getTimestampEpochSec()) < 300L
				&& last.getProfitGp() == profitGp
				&& last.getMaterialCostGp() == materialCostGp)
			{
				return;
			}
		}
		list.add(ProfitHistoryPoint.builder()
			.timestampEpochSec(timestampEpochSec)
			.materialCostGp(materialCostGp)
			.outputValueGp(outputValueGp)
			.profitGp(profitGp)
			.breakEvenSellGp(breakEvenSellGp)
			.build());

		long cutoff = timestampEpochSec - MAX_HISTORY_AGE_SEC;
		list.removeIf(p -> p.getTimestampEpochSec() < cutoff);
		while (list.size() > MAX_HISTORY_POINTS_PER_RECIPE)
		{
			list.remove(0);
		}
		if (persistImmediately)
		{
			saveHistory();
		}
	}

	public List<ProfitHistoryPoint> getHistoryWindow(String recipeId, long windowSeconds, long nowEpochSec)
	{
		if (recipeId == null)
		{
			return Collections.emptyList();
		}
		synchronized (historyByRecipeId)
		{
			List<ProfitHistoryPoint> all = historyByRecipeId.get(recipeId);
			if (all == null || all.isEmpty())
			{
				return Collections.emptyList();
			}
			long cutoff = windowSeconds > 0L ? nowEpochSec - windowSeconds : 0L;
			List<ProfitHistoryPoint> filtered = new ArrayList<>();
			for (ProfitHistoryPoint p : all)
			{
				if (p.getTimestampEpochSec() >= cutoff)
				{
					filtered.add(p);
				}
			}
			return Collections.unmodifiableList(filtered);
		}
	}

	public static ProfitHistoryStats computeStats(List<ProfitHistoryPoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return ProfitHistoryStats.builder()
				.sampleCount(0)
				.trendLabel("No Data")
				.build();
		}
		long best = Long.MIN_VALUE;
		long worst = Long.MAX_VALUE;
		double sum = 0.0;
		for (ProfitHistoryPoint p : points)
		{
			best = Math.max(best, p.getProfitGp());
			worst = Math.min(worst, p.getProfitGp());
			sum += p.getProfitGp();
		}
		long current = points.get(points.size() - 1).getProfitGp();
		double avg = sum / points.size();
		String trend = "Stable";
		if (points.size() >= 2)
		{
			long first = points.get(0).getProfitGp();
			long delta = current - first;
			long tol = Math.max(10L, Math.round(Math.abs(avg) * 0.03));
			if (delta > tol)
			{
				trend = "Rising";
			}
			else if (delta < -tol)
			{
				trend = "Falling";
			}
		}
		return ProfitHistoryStats.builder()
			.sampleCount(points.size())
			.bestProfitGp(best)
			.worstProfitGp(worst)
			.currentProfitGp(current)
			.averageProfitGp(avg)
			.trendLabel(trend)
			.build();
	}
}
