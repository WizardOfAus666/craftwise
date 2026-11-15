package com.craftwise.api;

import com.craftwise.model.ItemPrice;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Parses and validates JSON payloads from the OSRS Wiki Real-time Prices API
 * and the official OSRS Wiki MediaWiki API.
 * Strictly preserves missing/null prices as {@code null} (never 0 GP).
 */
public class ApiResponseHandler
{
	@Data
	@Builder
	public static class TimeseriesPoint
	{
		private final long timestamp;
		private final Long avgHighPrice;
		private final Long avgLowPrice;
		private final long highPriceVolume;
		private final long lowPriceVolume;

		public Long getMidpointPrice()
		{
			boolean validH = avgHighPrice != null && avgHighPrice > 0L;
			boolean validL = avgLowPrice != null && avgLowPrice > 0L;
			if (validH && validL)
			{
				return Math.round((avgHighPrice + avgLowPrice) / 2.0);
			}
			return validH ? avgHighPrice : (validL ? avgLowPrice : null);
		}
	}

	public enum MarketTrend
	{
		RISING("Increasing"),
		FALLING("Decreasing"),
		STABLE("Stable"),
		VOLATILE("Volatile"),
		UNKNOWN("Insufficient Data");

		private final String label;

		MarketTrend(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	@Data
	@Builder
	public static class TimeseriesData
	{
		private final int itemId;
		private final String timestep;
		@Builder.Default
		private final List<TimeseriesPoint> points = Collections.emptyList();
		private final Long latestPrice;
		private final Long earliestPrice;
		private final Long minPrice;
		private final Long maxPrice;
		private final double averagePrice;
		private final double percentChange;
		private final double volatilityPercent;
		private final boolean unusualSpike;
		private final MarketTrend trend;
	}

	@Data
	@Builder
	public static class WikiPageInfo
	{
		private final String title;
		private final String canonicalUrl;
		private final String extract;
		private final long revisionId;
		private final boolean missing;
	}

	public Map<Integer, ItemPrice> parseLatestResponse(String json, long fetchedAtEpochSec) throws IOException
	{
		JsonObject root = parseJsonObject(json, "/latest");
		JsonObject data = getObjectOrThrow(root, "data", "/latest");

		Map<Integer, ItemPrice> result = new HashMap<>(data.size());
		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			Integer itemId = tryParseInt(entry.getKey());
			if (itemId == null || itemId <= 0 || !entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject obj = entry.getValue().getAsJsonObject();
			Long high = getNullablePositiveLong(obj, "high");
			Long low = getNullablePositiveLong(obj, "low");
			long highTime = getLongOrZero(obj, "highTime");
			long lowTime = getLongOrZero(obj, "lowTime");

			result.put(itemId, ItemPrice.builder()
				.itemId(itemId)
				.highPrice(high)
				.highTime(highTime)
				.lowPrice(low)
				.lowTime(lowTime)
				.fetchedAtEpochSec(fetchedAtEpochSec)
				.stale(false)
				.build());
		}
		return result;
	}

	public Map<Integer, ItemPrice> parseMappingResponse(String json) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IOException("Empty /mapping response from OSRS Wiki API.");
		}
		JsonElement parsed;
		try
		{
			parsed = new JsonParser().parse(json);
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Malformed JSON from /mapping: " + ex.getMessage(), ex);
		}
		if (!parsed.isJsonArray())
		{
			throw new IOException("Expected JSON array from /mapping.");
		}

		JsonArray array = parsed.getAsJsonArray();
		Map<Integer, ItemPrice> map = new HashMap<>(array.size());
		for (JsonElement elem : array)
		{
			if (!elem.isJsonObject())
			{
				continue;
			}
			JsonObject obj = elem.getAsJsonObject();
			int id = (int) getLongOrZero(obj, "id");
			if (id <= 0)
			{
				continue;
			}
			String name = getStringOrNull(obj, "name");
			String examine = getStringOrNull(obj, "examine");
			boolean members = obj.has("members") && !obj.get("members").isJsonNull() && obj.get("members").getAsBoolean();
			int limit = (int) getLongOrZero(obj, "limit");
			int highAlch = (int) getLongOrZero(obj, "highalch");
			int lowAlch = (int) getLongOrZero(obj, "lowalch");
			int value = (int) getLongOrZero(obj, "value");

			map.put(id, ItemPrice.builder()
				.itemId(id)
				.name(name)
				.examine(examine)
				.members(members)
				.buyLimit(limit)
				.highAlch(highAlch)
				.lowAlch(lowAlch)
				.storeValue(value)
				.build());
		}
		return map;
	}

	public Map<String, Integer> buildNameToIdIndex(Map<Integer, ItemPrice> mapping)
	{
		if (mapping == null || mapping.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<String, Integer> index = new HashMap<>(mapping.size());
		for (ItemPrice item : mapping.values())
		{
			if (item.getName() != null && !item.getName().isEmpty())
			{
				index.putIfAbsent(item.getName().trim().toLowerCase(Locale.ROOT), item.getItemId());
			}
		}
		return index;
	}

	public void mergeRollingAverageResponse(
		Map<Integer, ItemPrice> prices,
		String json,
		boolean isFiveMinute,
		long fetchedAtEpochSec) throws IOException
	{
		if (prices == null)
		{
			return;
		}
		String endpoint = isFiveMinute ? "/5m" : "/1h";
		JsonObject root = parseJsonObject(json, endpoint);
		JsonObject data = getObjectOrThrow(root, "data", endpoint);

		for (Map.Entry<String, JsonElement> entry : data.entrySet())
		{
			Integer itemId = tryParseInt(entry.getKey());
			if (itemId == null || itemId <= 0 || !entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject obj = entry.getValue().getAsJsonObject();
			Long avgHigh = getNullablePositiveLong(obj, "avgHighPrice");
			Long avgLow = getNullablePositiveLong(obj, "avgLowPrice");
			long volHigh = Math.max(0L, getLongOrZero(obj, "highPriceVolume"));
			long volLow = Math.max(0L, getLongOrZero(obj, "lowPriceVolume"));

			ItemPrice existing = prices.get(itemId);
			ItemPrice.ItemPriceBuilder builder = existing != null
				? existing.toBuilder()
				: ItemPrice.builder().itemId(itemId).fetchedAtEpochSec(fetchedAtEpochSec).stale(false);

			if (isFiveMinute)
			{
				builder.avg5mHigh(avgHigh).avg5mLow(avgLow).vol5mHigh(volHigh).vol5mLow(volLow);
			}
			else
			{
				builder.avg1hHigh(avgHigh).avg1hLow(avgLow).vol1hHigh(volHigh).vol1hLow(volLow);
			}
			prices.put(itemId, builder.build());
		}
	}

	public TimeseriesData parseTimeseriesResponse(String json, int itemId, String timestep) throws IOException
	{
		JsonObject root = parseJsonObject(json, "/timeseries");
		if (!root.has("data") || !root.get("data").isJsonArray())
		{
			throw new IOException("Expected 'data' array in /timeseries response.");
		}
		JsonArray array = root.getAsJsonArray("data");
		List<TimeseriesPoint> points = new ArrayList<>(array.size());
		for (JsonElement elem : array)
		{
			if (!elem.isJsonObject())
			{
				continue;
			}
			JsonObject obj = elem.getAsJsonObject();
			long ts = getLongOrZero(obj, "timestamp");
			if (ts <= 0L)
			{
				continue;
			}
			Long avgHigh = getNullablePositiveLong(obj, "avgHighPrice");
			Long avgLow = getNullablePositiveLong(obj, "avgLowPrice");
			long volHigh = Math.max(0L, getLongOrZero(obj, "highPriceVolume"));
			long volLow = Math.max(0L, getLongOrZero(obj, "lowPriceVolume"));
			points.add(TimeseriesPoint.builder()
				.timestamp(ts)
				.avgHighPrice(avgHigh)
				.avgLowPrice(avgLow)
				.highPriceVolume(volHigh)
				.lowPriceVolume(volLow)
				.build());
		}

		points.sort(Comparator.comparingLong(TimeseriesPoint::getTimestamp));
		return summarizeTimeseries(itemId, timestep, points);
	}

	public TimeseriesData summarizeTimeseries(int itemId, String timestep, List<TimeseriesPoint> points)
	{
		if (points == null || points.isEmpty())
		{
			return TimeseriesData.builder()
				.itemId(itemId)
				.timestep(timestep)
				.points(Collections.emptyList())
				.trend(MarketTrend.UNKNOWN)
				.build();
		}

		List<Long> mids = new ArrayList<>();
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		double sum = 0.0;
		for (TimeseriesPoint p : points)
		{
			Long mid = p.getMidpointPrice();
			if (mid != null && mid > 0L)
			{
				mids.add(mid);
				if (mid < min)
				{
					min = mid;
				}
				if (mid > max)
				{
					max = mid;
				}
				sum += mid;
			}
		}

		if (mids.isEmpty())
		{
			return TimeseriesData.builder()
				.itemId(itemId)
				.timestep(timestep)
				.points(Collections.unmodifiableList(points))
				.trend(MarketTrend.UNKNOWN)
				.build();
		}

		long earliest = mids.get(0);
		long latest = mids.get(mids.size() - 1);
		double avg = sum / mids.size();
		double pctChange = earliest > 0L ? ((latest - earliest) * 100.0) / earliest : 0.0;

		double varianceSum = 0.0;
		for (Long m : mids)
		{
			double diff = m - avg;
			varianceSum += diff * diff;
		}
		double stdDev = Math.sqrt(varianceSum / mids.size());
		double volatilityPct = avg > 0.0 ? (stdDev * 100.0) / avg : 0.0;
		boolean unusual = Math.abs(pctChange) >= 12.0 || volatilityPct >= 8.0;

		MarketTrend trend;
		if (mids.size() < 3)
		{
			trend = MarketTrend.UNKNOWN;
		}
		else if (volatilityPct >= 10.0)
		{
			trend = MarketTrend.VOLATILE;
		}
		else if (pctChange >= 2.0)
		{
			trend = MarketTrend.RISING;
		}
		else if (pctChange <= -2.0)
		{
			trend = MarketTrend.FALLING;
		}
		else
		{
			trend = MarketTrend.STABLE;
		}

		return TimeseriesData.builder()
			.itemId(itemId)
			.timestep(timestep)
			.points(Collections.unmodifiableList(points))
			.earliestPrice(earliest)
			.latestPrice(latest)
			.minPrice(min)
			.maxPrice(max)
			.averagePrice(avg)
			.percentChange(pctChange)
			.volatilityPercent(volatilityPct)
			.unusualSpike(unusual)
			.trend(trend)
			.build();
	}

	public WikiPageInfo parseWikiQueryResponse(String json, String requestedTitle) throws IOException
	{
		JsonObject root = parseJsonObject(json, "wiki api.php");
		JsonObject query = root.has("query") && root.get("query").isJsonObject()
			? root.getAsJsonObject("query")
			: null;
		if (query == null || !query.has("pages") || !query.get("pages").isJsonObject())
		{
			return WikiPageInfo.builder()
				.title(requestedTitle)
				.missing(true)
				.build();
		}
		JsonObject pages = query.getAsJsonObject("pages");
		for (Map.Entry<String, JsonElement> entry : pages.entrySet())
		{
			if (!entry.getValue().isJsonObject())
			{
				continue;
			}
			JsonObject page = entry.getValue().getAsJsonObject();
			boolean missing = page.has("missing") || "-1".equals(entry.getKey());
			String title = getStringOrNull(page, "title");
			String fullUrl = getStringOrNull(page, "fullurl");
			String extract = getStringOrNull(page, "extract");
			long revId = getLongOrZero(page, "lastrevid");
			return WikiPageInfo.builder()
				.title(title != null ? title : requestedTitle)
				.canonicalUrl(fullUrl)
				.extract(extract)
				.revisionId(revId)
				.missing(missing)
				.build();
		}
		return WikiPageInfo.builder().title(requestedTitle).missing(true).build();
	}

	private static JsonObject parseJsonObject(String json, String context) throws IOException
	{
		if (json == null || json.trim().isEmpty())
		{
			throw new IOException("Empty response body from " + context);
		}
		try
		{
			JsonElement elem = new JsonParser().parse(json);
			if (!elem.isJsonObject())
			{
				throw new IOException("Expected JSON object from " + context);
			}
			return elem.getAsJsonObject();
		}
		catch (JsonSyntaxException ex)
		{
			throw new IOException("Invalid JSON from " + context + ": " + ex.getMessage(), ex);
		}
	}

	private static JsonObject getObjectOrThrow(JsonObject parent, String member, String context) throws IOException
	{
		if (!parent.has(member) || !parent.get(member).isJsonObject())
		{
			throw new IOException("Missing '" + member + "' object in " + context + " response.");
		}
		return parent.getAsJsonObject(member);
	}

	private static Long getNullablePositiveLong(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return null;
		}
		try
		{
			long value = obj.get(key).getAsLong();
			return value > 0L ? value : null;
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static long getLongOrZero(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return 0L;
		}
		try
		{
			return obj.get(key).getAsLong();
		}
		catch (Exception ex)
		{
			return 0L;
		}
	}

	private static String getStringOrNull(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull())
		{
			return null;
		}
		try
		{
			return obj.get(key).getAsString();
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static Integer tryParseInt(String value)
	{
		try
		{
			return Integer.parseInt(value);
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}
}
