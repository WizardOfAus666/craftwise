package com.craftwise.api;

import com.craftwise.api.ApiResponseHandler.TimeseriesData;
import com.craftwise.model.ItemPrice;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.inject.Singleton;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Official OSRS Wiki Real-time Prices API client for CraftWise.
 * Supports bulk /latest, /mapping, /5m, /1h, and /timeseries endpoints.
 * Attempts /api/v2/osrs and seamlessly falls back to /api/v1/osrs if v2 is unavailable.
 */
@Singleton
public class WikiPriceClient
{
	private static final Logger log = LoggerFactory.getLogger(WikiPriceClient.class);

	public static final String V2_BASE_URL = "https://prices.runescape.wiki/api/v2/osrs";
	public static final String V1_BASE_URL = "https://prices.runescape.wiki/api/v1/osrs";
	public static final String USER_AGENT =
		"CraftWise-RuneLite-Plugin/1.0.0 (OSRS Crafting Profit & Training Analyzer)";

	private static final long MAPPING_TTL_SECONDS = 6L * 3600L;

	private final OkHttpClient httpClient;
	private final PriceCache priceCache;
	private final ApiResponseHandler responseHandler;

	private volatile String activeBaseUrl = V2_BASE_URL;
	private volatile boolean liveConnected = false;
	private volatile String statusMessage = "Initializing OSRS Wiki Price API...";
	private volatile Map<String, Integer> nameToIdIndex = Collections.emptyMap();

	@Inject
	public WikiPriceClient(OkHttpClient httpClient, PriceCache priceCache)
	{
		this(httpClient, priceCache, new ApiResponseHandler());
	}

	public WikiPriceClient(OkHttpClient httpClient, PriceCache priceCache, ApiResponseHandler responseHandler)
	{
		if (httpClient == null)
		{
			throw new IllegalArgumentException("OkHttpClient must be provided via RuneLite @Inject.");
		}
		if (priceCache == null)
		{
			throw new IllegalArgumentException("PriceCache must be provided.");
		}
		this.httpClient = httpClient.newBuilder()
			.connectTimeout(8, TimeUnit.SECONDS)
			.readTimeout(12, TimeUnit.SECONDS)
			.writeTimeout(8, TimeUnit.SECONDS)
			.build();
		this.priceCache = priceCache;
		this.responseHandler = responseHandler != null ? responseHandler : new ApiResponseHandler();
	}

	public String getActiveBaseUrl()
	{
		return activeBaseUrl;
	}

	public boolean isLiveConnected()
	{
		return liveConnected;
	}

	public String getStatusMessage()
	{
		return statusMessage;
	}

	public PriceCache getPriceCache()
	{
		return priceCache;
	}

	public Map<String, Integer> getNameToIdIndex()
	{
		return nameToIdIndex;
	}

	/**
	 * Fetches bulk GE pricing from /latest, /mapping (if expired/empty), /5m, and /1h.
	 * Updates {@link PriceCache} on success. On failure, throws IOException after updating status.
	 */
	public synchronized Map<Integer, ItemPrice> fetchAllPrices() throws IOException
	{
		long nowSec = Instant.now().getEpochSecond();
		try
		{
			String latestJson = executeWithFallback("/latest");
			Map<Integer, ItemPrice> prices = responseHandler.parseLatestResponse(latestJson, nowSec);
			if (prices.isEmpty())
			{
				throw new IOException("OSRS Wiki /latest returned 0 item entries.");
			}

			if (priceCache.needsMappingRefresh(nowSec, MAPPING_TTL_SECONDS))
			{
				try
				{
					String mappingJson = executeWithFallback("/mapping");
					Map<Integer, ItemPrice> mapping = responseHandler.parseMappingResponse(mappingJson);
					if (!mapping.isEmpty())
					{
						priceCache.saveMapping(mapping, nowSec);
						nameToIdIndex = responseHandler.buildNameToIdIndex(mapping);
					}
				}
				catch (Exception ex)
				{
					log.debug("[CraftWise] Non-fatal /mapping fetch error: {}", ex.getMessage());
				}
			}

			mergeMappingIntoPrices(prices, priceCache.getMappingSnapshot());

			try
			{
				String fiveMinJson = executeWithFallback("/5m");
				responseHandler.mergeRollingAverageResponse(prices, fiveMinJson, true, nowSec);
			}
			catch (Exception ex)
			{
				log.debug("[CraftWise] Non-fatal /5m fetch error: {}", ex.getMessage());
			}

			try
			{
				String oneHourJson = executeWithFallback("/1h");
				responseHandler.mergeRollingAverageResponse(prices, oneHourJson, false, nowSec);
			}
			catch (Exception ex)
			{
				log.debug("[CraftWise] Non-fatal /1h fetch error: {}", ex.getMessage());
			}

			priceCache.savePrices(prices, nowSec);
			liveConnected = true;
			statusMessage = "Live (" + prices.size() + " items via OSRS Wiki API)";
			return priceCache.getPricesSnapshot(false);
		}
		catch (IOException ex)
		{
			liveConnected = false;
			statusMessage = "Offline — Using cached prices (" + ex.getMessage() + ")";
			throw ex;
		}
	}

	/**
	 * Fetches /timeseries?timestep=...&id=... with in-memory TTL caching.
	 */
	public TimeseriesData fetchTimeseries(int itemId, String timestep) throws IOException
	{
		String safeStep = (timestep == null || timestep.trim().isEmpty()) ? "1h" : timestep.trim();
		long nowSec = Instant.now().getEpochSecond();

		TimeseriesData fresh = priceCache.getFreshTimeseries(itemId, safeStep, nowSec);
		if (fresh != null)
		{
			return fresh;
		}

		try
		{
			String endpoint = "/timeseries?timestep=" + safeStep + "&id=" + itemId;
			String json = executeWithFallback(endpoint);
			TimeseriesData data = responseHandler.parseTimeseriesResponse(json, itemId, safeStep);
			priceCache.putTimeseries(itemId, safeStep, data, nowSec);
			return data;
		}
		catch (IOException ex)
		{
			TimeseriesData stale = priceCache.getAnyTimeseries(itemId, safeStep);
			if (stale != null)
			{
				return stale;
			}
			throw ex;
		}
	}

	private void mergeMappingIntoPrices(Map<Integer, ItemPrice> prices, Map<Integer, ItemPrice> mapping)
	{
		if (mapping == null || mapping.isEmpty())
		{
			return;
		}
		for (Map.Entry<Integer, ItemPrice> entry : mapping.entrySet())
		{
			int id = entry.getKey();
			ItemPrice meta = entry.getValue();
			ItemPrice current = prices.get(id);
			if (current != null)
			{
				prices.put(id, current.toBuilder()
					.name(meta.getName())
					.examine(meta.getExamine())
					.members(meta.isMembers())
					.buyLimit(meta.getBuyLimit())
					.highAlch(meta.getHighAlch())
					.lowAlch(meta.getLowAlch())
					.storeValue(meta.getStoreValue())
					.build());
			}
			else
			{
				prices.put(id, meta);
			}
		}
	}

	private String executeWithFallback(String endpointPath) throws IOException
	{
		try
		{
			return executeGetWithRetry(activeBaseUrl + endpointPath);
		}
		catch (IOException primaryEx)
		{
			if (V2_BASE_URL.equals(activeBaseUrl))
			{
				log.debug("[CraftWise] v2 endpoint failed ({}), falling back to v1 API...", primaryEx.getMessage());
				String body = executeGetWithRetry(V1_BASE_URL + endpointPath);
				activeBaseUrl = V1_BASE_URL;
				return body;
			}
			throw primaryEx;
		}
	}

	private String executeGetWithRetry(String url) throws IOException
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.header("Accept", "application/json")
			.get()
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			int code = response.code();
			if (response.isSuccessful())
			{
				ResponseBody body = response.body();
				if (body == null)
				{
					throw new IOException("Empty HTTP body from " + url);
				}
				return body.string();
			}

			if (code == 429)
			{
				throw new IOException("HTTP 429 rate-limited on " + url);
			}
			throw new IOException("HTTP " + code + " for " + url);
		}
	}
}
