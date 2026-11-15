package com.craftwise.api;

import com.craftwise.api.ApiResponseHandler.TimeseriesData;
import com.craftwise.model.ItemPrice;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe local price and timeseries cache with disk persistence and expiration tracking.
 * Ensures CraftWise never blocks on repeated identical requests and degrades gracefully offline.
 */
@Singleton
public class PriceCache
{
	private static final Logger log = LoggerFactory.getLogger(PriceCache.class);
	private static final long DEFAULT_STALE_THRESHOLD_SEC = 180L;
	private static final long DEFAULT_TIMESERIES_TTL_SEC = 300L;

	private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
	private final Map<Integer, ItemPrice> livePrices = new ConcurrentHashMap<>();
	private final Map<Integer, ItemPrice> mappingMetadata = new ConcurrentHashMap<>();
	private final Map<String, CachedTimeseries> timeseriesCache = new ConcurrentHashMap<>();
	private volatile Filepath cacheFile;
	private final Gson gson;

	private volatile long lastPriceUpdateEpochSec = 0L;
	private volatile long lastMappingUpdateEpochSec = 0L;
	private volatile long staleThresholdSec = DEFAULT_STALE_THRESHOLD_SEC;

	private static class CachedTimeseries
	{
		final TimeseriesData data;
		final long cachedAtEpochSec;

		CachedTimeseries(TimeseriesData data, long cachedAtEpochSec)
		{
			this.data = data;
			this.cachedAtEpochSec = cachedAtEpochSec;
		}
	}

	private static class DiskCachePayload
	{
		long savedAtEpochSec;
		Map<Integer, ItemPrice> prices;
	}

	@Inject
	public PriceCache(Gson gson)
	{
		this(null, gson);
	}

	public PriceCache(Filepath cacheFile, Gson gson)
	{
		this.cacheFile = cacheFile;
		this.gson = Objects.requireNonNull(gson, "Gson must be provided via RuneLite @Inject.");
		loadFromDisk();
	}

	public synchronized void initializeStorage(Filepath cacheFile)
	{
		this.cacheFile = cacheFile;
		loadFromDisk();
	}

	public void setStaleThresholdSec(long staleThresholdSec)
	{
		this.staleThresholdSec = Math.max(30L, staleThresholdSec);
	}

	public long getLastPriceUpdateEpochSec()
	{
		return lastPriceUpdateEpochSec;
	}

	public long getLastMappingUpdateEpochSec()
	{
		return lastMappingUpdateEpochSec;
	}

	public boolean isEmpty()
	{
		lock.readLock().lock();
		try
		{
			return livePrices.isEmpty();
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	public boolean isStale(long nowEpochSec)
	{
		if (lastPriceUpdateEpochSec <= 0L)
		{
			return true;
		}
		return (nowEpochSec - lastPriceUpdateEpochSec) > staleThresholdSec;
	}

	public void savePrices(Map<Integer, ItemPrice> prices, long fetchedAtEpochSec)
	{
		if (prices == null || prices.isEmpty())
		{
			return;
		}
		lock.writeLock().lock();
		try
		{
			livePrices.clear();
			for (Map.Entry<Integer, ItemPrice> entry : prices.entrySet())
			{
				livePrices.put(entry.getKey(), entry.getValue().toBuilder().stale(false).build());
			}
			lastPriceUpdateEpochSec = fetchedAtEpochSec;
		}
		finally
		{
			lock.writeLock().unlock();
		}
		saveToDisk(fetchedAtEpochSec, prices);
	}

	public Map<Integer, ItemPrice> getPricesSnapshot(boolean markStale)
	{
		lock.readLock().lock();
		try
		{
			if (livePrices.isEmpty())
			{
				return Collections.emptyMap();
			}
			Map<Integer, ItemPrice> copy = new HashMap<>(livePrices.size());
			for (Map.Entry<Integer, ItemPrice> e : livePrices.entrySet())
			{
				ItemPrice p = e.getValue();
				copy.put(e.getKey(), markStale ? p.toBuilder().stale(true).build() : p);
			}
			return Collections.unmodifiableMap(copy);
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	public ItemPrice getPrice(int itemId)
	{
		lock.readLock().lock();
		try
		{
			return livePrices.get(itemId);
		}
		finally
		{
			lock.readLock().unlock();
		}
	}

	public void saveMapping(Map<Integer, ItemPrice> mapping, long fetchedAtEpochSec)
	{
		if (mapping == null || mapping.isEmpty())
		{
			return;
		}
		mappingMetadata.clear();
		mappingMetadata.putAll(mapping);
		lastMappingUpdateEpochSec = fetchedAtEpochSec;
	}

	public Map<Integer, ItemPrice> getMappingSnapshot()
	{
		return Collections.unmodifiableMap(new HashMap<>(mappingMetadata));
	}

	public boolean needsMappingRefresh(long nowEpochSec, long ttlSeconds)
	{
		return mappingMetadata.isEmpty() || (nowEpochSec - lastMappingUpdateEpochSec) > ttlSeconds;
	}

	public void putTimeseries(int itemId, String timestep, TimeseriesData data, long nowEpochSec)
	{
		if (data == null || timestep == null)
		{
			return;
		}
		timeseriesCache.put(itemId + ":" + timestep, new CachedTimeseries(data, nowEpochSec));
	}

	public TimeseriesData getFreshTimeseries(int itemId, String timestep, long nowEpochSec)
	{
		CachedTimeseries cached = timeseriesCache.get(itemId + ":" + timestep);
		if (cached == null)
		{
			return null;
		}
		if ((nowEpochSec - cached.cachedAtEpochSec) <= DEFAULT_TIMESERIES_TTL_SEC)
		{
			return cached.data;
		}
		return null;
	}

	public TimeseriesData getAnyTimeseries(int itemId, String timestep)
	{
		CachedTimeseries cached = timeseriesCache.get(itemId + ":" + timestep);
		return cached != null ? cached.data : null;
	}

	private void loadFromDisk()
	{
		Filepath file = this.cacheFile;
		if (file == null || !file.exists())
		{
			return;
		}
		try (Reader reader = file.openReader())
		{
			Type type = new TypeToken<DiskCachePayload>() {}.getType();
			DiskCachePayload payload = gson.fromJson(reader, type);
			if (payload != null && payload.prices != null && !payload.prices.isEmpty())
			{
				long nowSec = Instant.now().getEpochSecond();
				boolean stale = (nowSec - payload.savedAtEpochSec) > staleThresholdSec;
				lock.writeLock().lock();
				try
				{
					for (Map.Entry<Integer, ItemPrice> entry : payload.prices.entrySet())
					{
						if (entry.getValue() != null)
						{
							livePrices.put(entry.getKey(), entry.getValue().toBuilder().stale(stale).build());
						}
					}
					lastPriceUpdateEpochSec = payload.savedAtEpochSec;
				}
				finally
				{
					lock.writeLock().unlock();
				}
				log.debug("[CraftWise] Loaded {} cached item prices from {}", payload.prices.size(), cacheFile);
			}
		}
		catch (Exception ex)
		{
			log.debug("[CraftWise] Could not load disk price cache: {}", ex.getMessage());
		}
	}

	private void saveToDisk(long savedAtEpochSec, Map<Integer, ItemPrice> prices)
	{
		Filepath file = this.cacheFile;
		if (file == null)
		{
			return;
		}
		try
		{
			Filepath parent = file.getParent();
			if (parent != null && !parent.exists())
			{
				parent.createDirectories();
			}
			DiskCachePayload payload = new DiskCachePayload();
			payload.savedAtEpochSec = savedAtEpochSec;
			payload.prices = new HashMap<>(prices);
			file.write(gson.toJson(payload));
		}
		catch (IOException ex)
		{
			log.debug("[CraftWise] Unable to persist disk price cache: {}", ex.getMessage());
		}
	}
}
