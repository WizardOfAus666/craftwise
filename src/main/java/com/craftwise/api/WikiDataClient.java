package com.craftwise.api;

import com.craftwise.api.ApiResponseHandler.WikiPageInfo;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * Client for the official Old School RuneScape Wiki MediaWiki API
 * ({@code https://oldschool.runescape.wiki/api.php}).
 * Used for verifying canonical Wiki references and fetching page summaries on demand.
 * Caches responses aggressively to avoid redundant requests.
 */
@Singleton
public class WikiDataClient
{
	private static final Logger log = LoggerFactory.getLogger(WikiDataClient.class);
	public static final String WIKI_API_URL = "https://oldschool.runescape.wiki/api.php";

	private final OkHttpClient httpClient;
	private final ApiResponseHandler responseHandler;
	private final Map<String, WikiPageInfo> pageCache = new ConcurrentHashMap<>();

	@Inject
	public WikiDataClient(OkHttpClient httpClient)
	{
		OkHttpClient base = httpClient != null ? httpClient : new OkHttpClient();
		this.httpClient = base.newBuilder()
			.connectTimeout(8, TimeUnit.SECONDS)
			.readTimeout(12, TimeUnit.SECONDS)
			.build();
		this.responseHandler = new ApiResponseHandler();
	}

	public WikiPageInfo getCachedPageInfo(String pageTitle)
	{
		if (pageTitle == null)
		{
			return null;
		}
		return pageCache.get(pageTitle.trim());
	}

	/**
	 * Queries the OSRS Wiki API for canonical page info and summary extract.
	 * Returns cached data immediately if already fetched during this session.
	 */
	public WikiPageInfo fetchPageInfo(String pageTitle) throws IOException
	{
		if (pageTitle == null || pageTitle.trim().isEmpty())
		{
			throw new IllegalArgumentException("Page title must not be empty.");
		}
		String normalized = pageTitle.trim();
		WikiPageInfo cached = pageCache.get(normalized);
		if (cached != null)
		{
			return cached;
		}

		String encodedTitle = URLEncoder.encode(normalized, StandardCharsets.UTF_8);
		String url = WIKI_API_URL
			+ "?action=query&prop=info|extracts&inprop=url&exintro=1&explaintext=1&redirects=1&format=json&titles="
			+ encodedTitle;

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", WikiPriceClient.USER_AGENT)
			.header("Accept", "application/json")
			.get()
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				throw new IOException("OSRS Wiki API returned HTTP " + response.code() + " for " + normalized);
			}
			ResponseBody body = response.body();
			if (body == null)
			{
				throw new IOException("Empty response from OSRS Wiki API for " + normalized);
			}
			WikiPageInfo info = responseHandler.parseWikiQueryResponse(body.string(), normalized);
			pageCache.put(normalized, info);
			return info;
		}
		catch (IOException ex)
		{
			log.debug("[CraftWise] WikiDataClient query failed for '{}': {}", normalized, ex.getMessage());
			throw ex;
		}
	}
}
