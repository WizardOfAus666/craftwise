package com.craftwise.calculator;

import com.craftwise.model.ItemPrice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Evaluates real Grand Exchange liquidity, spread, and recency risk
 * strictly from observed OSRS Wiki price endpoints (/latest, /5m, /1h).
 * Never fabricates volume metrics when the API reports none.
 */
public final class MarketConfidenceEvaluator
{
	@Getter
	@RequiredArgsConstructor
	public enum PriceConfidence
	{
		HIGH("High", "Active 1h volume, tight spread, and recent trades."),
		MEDIUM("Medium", "Moderate trade volume or spread; watch GE offer fill speed."),
		LOW("Low", "Thin volume, wide bid-ask spread, or aged market data."),
		UNAVAILABLE("Unavailable", "Missing market price data.");

		private final String label;
		private final String description;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Data
	@Builder
	public static class MarketEvaluation
	{
		private final PriceConfidence confidence;
		private final long min1hVolume;
		private final long primaryOutput1hVolume;
		private final double worstSpreadPercent;
		private final long oldestPriceAgeSeconds;
		private final boolean lowLiquidityWarning;
		private final boolean wideSpreadWarning;
		private final boolean stalePriceWarning;
		@Builder.Default
		private final List<String> warnings = Collections.emptyList();
	}

	public static final String GE_EXECUTION_DISCLAIMER =
		"Note: Execution at displayed GE prices is not guaranteed; verify live offer margins and trade volume before bulk trades.";

	private MarketConfidenceEvaluator()
	{
	}

	public static MarketEvaluation evaluate(
		boolean priceDataComplete,
		List<ItemPrice> tradedPrices,
		ItemPrice primaryOutputPrice,
		long nowEpochSeconds)
	{
		if (!priceDataComplete)
		{
			return MarketEvaluation.builder()
				.confidence(PriceConfidence.UNAVAILABLE)
				.min1hVolume(0L)
				.primaryOutput1hVolume(primaryOutputPrice != null ? primaryOutputPrice.getTotal1hVolume() : 0L)
				.worstSpreadPercent(0.0)
				.oldestPriceAgeSeconds(0L)
				.lowLiquidityWarning(true)
				.warnings(Collections.singletonList("Market price missing for one or more required items."))
				.build();
		}

		long primaryVol = primaryOutputPrice != null ? primaryOutputPrice.getTotal1hVolume() : 0L;
		if (tradedPrices == null || tradedPrices.isEmpty())
		{
			return MarketEvaluation.builder()
				.confidence(PriceConfidence.MEDIUM)
				.min1hVolume(primaryVol)
				.primaryOutput1hVolume(primaryVol)
				.worstSpreadPercent(0.0)
				.oldestPriceAgeSeconds(0L)
				.warnings(Collections.emptyList())
				.build();
		}

		PriceConfidence worst = PriceConfidence.HIGH;
		long minVol = Long.MAX_VALUE;
		double maxSpreadPct = 0.0;
		long maxAgeSec = 0L;
		boolean lowLiquidity = false;
		boolean wideSpread = false;
		boolean stale = false;
		List<String> warnings = new ArrayList<>();

		for (ItemPrice price : tradedPrices)
		{
			if (price == null)
			{
				continue;
			}
			long vol1h = price.getTotal1hVolume();
			if (vol1h < minVol)
			{
				minVol = vol1h;
			}

			String itemLabel = price.getName() != null && !price.getName().trim().isEmpty()
				? price.getName()
				: "Item #" + price.getItemId();

			if (vol1h == 0L)
			{
				lowLiquidity = true;
				worst = lowerOf(worst, PriceConfidence.LOW);
				warnings.add("Low/no 1h GE volume reported for " + itemLabel + ".");
			}
			else if (vol1h < 10L)
			{
				lowLiquidity = true;
				worst = lowerOf(worst, PriceConfidence.LOW);
				warnings.add("Very low liquidity (" + vol1h + "/hr) for " + itemLabel + ".");
			}
			else if (vol1h < 100L)
			{
				worst = lowerOf(worst, PriceConfidence.MEDIUM);
			}

			Long high = price.getHighPrice();
			Long low = price.getLowPrice();
			if (high != null && low != null && high > 0L && low > 0L && high >= low)
			{
				double mid = (high + low) / 2.0;
				double spreadPct = mid > 0.0 ? ((high - low) * 100.0) / mid : 0.0;
				if (spreadPct > maxSpreadPct)
				{
					maxSpreadPct = spreadPct;
				}
				if (spreadPct > 15.0)
				{
					wideSpread = true;
					worst = lowerOf(worst, PriceConfidence.LOW);
					warnings.add(String.format("Wide bid-ask spread (%.1f%%) on %s.", spreadPct, itemLabel));
				}
				else if (spreadPct > 6.0)
				{
					wideSpread = true;
					worst = lowerOf(worst, PriceConfidence.MEDIUM);
					warnings.add(String.format("Moderate spread (%.1f%%) on %s.", spreadPct, itemLabel));
				}
			}

			long latestTradeEpoch = Math.max(price.getHighTime(), price.getLowTime());
			if (nowEpochSeconds > 0L && latestTradeEpoch > 0L && nowEpochSeconds >= latestTradeEpoch)
			{
				long ageSec = nowEpochSeconds - latestTradeEpoch;
				if (ageSec > maxAgeSec)
				{
					maxAgeSec = ageSec;
				}
				if (ageSec > 21_600L) // > 6 hours old
				{
					stale = true;
					worst = lowerOf(worst, PriceConfidence.LOW);
					warnings.add("Latest trade for " + itemLabel + " is over 6 hours old.");
				}
				else if (ageSec > 3_600L) // > 1 hour old
				{
					stale = true;
					worst = lowerOf(worst, PriceConfidence.MEDIUM);
				}
			}
		}

		if (minVol == Long.MAX_VALUE)
		{
			minVol = 0L;
		}

		return MarketEvaluation.builder()
			.confidence(worst)
			.min1hVolume(minVol)
			.primaryOutput1hVolume(primaryVol)
			.worstSpreadPercent(maxSpreadPct)
			.oldestPriceAgeSeconds(maxAgeSec)
			.lowLiquidityWarning(lowLiquidity)
			.wideSpreadWarning(wideSpread)
			.stalePriceWarning(stale)
			.warnings(Collections.unmodifiableList(warnings))
			.build();
	}

	private static PriceConfidence lowerOf(PriceConfidence a, PriceConfidence b)
	{
		return a.ordinal() >= b.ordinal() ? a : b;
	}
}
