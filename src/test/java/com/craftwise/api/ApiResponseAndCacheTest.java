package com.craftwise.api;

import com.craftwise.api.ApiResponseHandler.TimeseriesData;
import com.craftwise.api.ApiResponseHandler.WikiPageInfo;
import com.craftwise.model.ItemPrice;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import net.runelite.api.ItemID;
import net.runelite.client.util.Filepath;
import org.junit.Assert;
import org.junit.Test;

public class ApiResponseAndCacheTest
{
	@Test
	public void testLatestParsingPreservesNullPrices() throws IOException
	{
		String latestJson = "{\"data\":{"
			+ "\"1617\":{\"high\":2100,\"highTime\":1700000000,\"low\":2050,\"lowTime\":1700000005},"
			+ "\"1619\":{\"high\":null,\"highTime\":0,\"low\":1100,\"lowTime\":1700000010}"
			+ "}}";
		ApiResponseHandler handler = new ApiResponseHandler();
		Map<Integer, ItemPrice> parsed = handler.parseLatestResponse(latestJson, 1700000020L);

		Assert.assertEquals(2, parsed.size());
		Assert.assertEquals(Long.valueOf(2100L), parsed.get(ItemID.UNCUT_DIAMOND).getHighPrice());
		Assert.assertNull("Null price in JSON must stay null", parsed.get(ItemID.UNCUT_RUBY).getHighPrice());
		Assert.assertEquals(Long.valueOf(1100L), parsed.get(ItemID.UNCUT_RUBY).getLowPrice());
	}

	@Test
	public void testRollingAverageTimeseriesAndWikiQueryParsing() throws IOException
	{
		ApiResponseHandler handler = new ApiResponseHandler();
		Map<Integer, ItemPrice> prices = handler.parseLatestResponse(
			"{\"data\":{\"1601\":{\"high\":1900,\"highTime\":100,\"low\":1850,\"lowTime\":101}}}", 200L);

		handler.mergeRollingAverageResponse(
			prices,
			"{\"data\":{\"1601\":{\"avgHighPrice\":1910,\"highPriceVolume\":500,\"avgLowPrice\":1860,\"lowPriceVolume\":450}}}",
			true,
			200L);

		ItemPrice diamond = prices.get(ItemID.DIAMOND);
		Assert.assertEquals(Long.valueOf(1910L), diamond.getAvg5mHigh());
		Assert.assertEquals(950L, diamond.getTotal5mVolume());

		TimeseriesData ts = handler.parseTimeseriesResponse(
			"{\"data\":["
				+ "{\"timestamp\":1000,\"avgHighPrice\":1800,\"avgLowPrice\":1780,\"highPriceVolume\":10,\"lowPriceVolume\":10},"
				+ "{\"timestamp\":2000,\"avgHighPrice\":1900,\"avgLowPrice\":1880,\"highPriceVolume\":15,\"lowPriceVolume\":15},"
				+ "{\"timestamp\":3000,\"avgHighPrice\":2000,\"avgLowPrice\":1980,\"highPriceVolume\":20,\"lowPriceVolume\":20}"
				+ "]}",
			ItemID.DIAMOND,
			"1h");
		Assert.assertEquals(3, ts.getPoints().size());
		Assert.assertEquals(Long.valueOf(1790L), ts.getEarliestPrice());
		Assert.assertEquals(Long.valueOf(1990L), ts.getLatestPrice());

		WikiPageInfo page = handler.parseWikiQueryResponse(
			"{\"query\":{\"pages\":{\"1234\":{\"pageid\":1234,\"title\":\"Diamond\",\"fullurl\":\"https://oldschool.runescape.wiki/w/Diamond\",\"extract\":\"A cut diamond.\",\"lastrevid\":999}}}}",
			"Diamond");
		Assert.assertFalse(page.isMissing());
		Assert.assertEquals("Diamond", page.getTitle());
		Assert.assertEquals("https://oldschool.runescape.wiki/w/Diamond", page.getCanonicalUrl());
	}

	@Test
	public void testPriceCacheDiskPersistenceAndStaleDetection() throws IOException
	{
		Path tempFile = Files.createTempFile("craftwise-cache-test", ".json");
		Filepath cachePath = Filepath.Unchecked.getRooted(tempFile.getParent())
			.joinSegment(tempFile.getFileName().toString());
		try
		{
			PriceCache writer = new PriceCache(cachePath, new Gson());
			writer.setStaleThresholdSec(60L);
			Map<Integer, ItemPrice> map = Map.of(
				ItemID.DIAMOND, ItemPrice.builder().itemId(ItemID.DIAMOND).highPrice(1900L).lowPrice(1850L).build()
			);
			writer.savePrices(map, 1000L);

			Assert.assertFalse(writer.isStale(1030L));
			Assert.assertTrue(writer.isStale(1200L));

			PriceCache reader = new PriceCache(cachePath, new Gson());
			Assert.assertFalse(reader.isEmpty());
			Assert.assertEquals(Long.valueOf(1900L), reader.getPrice(ItemID.DIAMOND).getHighPrice());
		}
		finally
		{
			Files.deleteIfExists(tempFile);
		}
	}
}
