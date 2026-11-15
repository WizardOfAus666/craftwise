package com.craftwise.model;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import lombok.Builder;
import lombok.Data;

/**
 * Represents Grand Exchange market pricing and metadata for an OSRS item
 * retrieved from the OSRS Wiki Real-time Prices API.
 * Missing prices are preserved as {@code null} rather than defaulting to 0 GP.
 */
@Data
@Builder(toBuilder = true)
public class ItemPrice
{
	private final int itemId;
	private final String name;
	private final String examine;
	private final boolean members;
	private final int buyLimit;
	private final int highAlch;
	private final int lowAlch;
	private final int storeValue;

	// /latest instant prices (nullable when unavailable)
	private final Long highPrice;
	private final long highTime;
	private final Long lowPrice;
	private final long lowTime;

	// /5m rolling average
	private final Long avg5mHigh;
	private final Long avg5mLow;
	private final long vol5mHigh;
	private final long vol5mLow;

	// /1h rolling average
	private final Long avg1hHigh;
	private final Long avg1hLow;
	private final long vol1hHigh;
	private final long vol1hLow;

	// Metadata
	private final long fetchedAtEpochSec;
	private final boolean stale;

	/**
	 * Returns the best available recent market average price, preferring /5m midpoint,
	 * then /1h midpoint, then /latest midpoint. Returns null if no market price exists.
	 */
	public Long getAverageMarketPrice()
	{
		Long avg5m = midpoint(avg5mHigh, avg5mLow);
		if (avg5m != null)
		{
			return avg5m;
		}
		Long avg1h = midpoint(avg1hHigh, avg1hLow);
		if (avg1h != null)
		{
			return avg1h;
		}
		return midpoint(highPrice, lowPrice);
	}

	/**
	 * Resolves the unit buying price (cost of an input material) under the chosen mode.
	 * Returns null if no valid price exists for the selected mode.
	 */
	public Long resolveBuyPrice(PriceCalculationMode mode, Long customBuyOverride)
	{
		if (mode == PriceCalculationMode.CUSTOM)
		{
			if (customBuyOverride != null && customBuyOverride > 0L)
			{
				return customBuyOverride;
			}
			// Fallback to average or latest only if user didn't override this specific item?
			// Returning null or falling back? Wait: custom mode should still fall back cleanly
			// if an item has no custom override only if explicitly handled, OR let ProfitCalculator
			// supply custom overrides when present and fall back to market when null.
			return customBuyOverride;
		}
		switch (mode)
		{
			case INSTANT_BUY_SELL:
			case INSTANT_BUY:
				return firstPositive(highPrice, avg5mHigh, avg1hHigh, lowPrice);
			case PATIENT_BUY_SELL:
			case INSTANT_SELL:
				return firstPositive(lowPrice, avg5mLow, avg1hLow, highPrice);
			case OPTIMISTIC_MARKET:
				return minPositive(lowPrice, avg5mLow, avg1hLow, highPrice);
			case AVERAGE_MARKET:
				return getAverageMarketPrice();
			default:
				return firstPositive(highPrice, lowPrice);
		}
	}

	/**
	 * Resolves the unit selling price (revenue for an output item) under the chosen mode.
	 * Returns null if no valid price exists for the selected mode.
	 */
	public Long resolveSellPrice(PriceCalculationMode mode, Long customSellOverride)
	{
		if (mode == PriceCalculationMode.CUSTOM)
		{
			return customSellOverride;
		}
		switch (mode)
		{
			case INSTANT_BUY_SELL:
			case INSTANT_SELL:
				return firstPositive(lowPrice, avg5mLow, avg1hLow, highPrice);
			case PATIENT_BUY_SELL:
			case INSTANT_BUY:
				return firstPositive(highPrice, avg5mHigh, avg1hHigh, lowPrice);
			case OPTIMISTIC_MARKET:
				return maxPositive(highPrice, avg5mHigh, avg1hHigh, lowPrice);
			case AVERAGE_MARKET:
				return getAverageMarketPrice();
			default:
				return firstPositive(lowPrice, highPrice);
		}
	}

	private static Long minPositive(Long... candidates)
	{
		Long best = null;
		for (Long c : candidates)
		{
			if (c != null && c > 0L && (best == null || c < best))
			{
				best = c;
			}
		}
		return best;
	}

	private static Long maxPositive(Long... candidates)
	{
		Long best = null;
		for (Long c : candidates)
		{
			if (c != null && c > 0L && (best == null || c > best))
			{
				best = c;
			}
		}
		return best;
	}

	public boolean hasAnyMarketPrice()
	{
		return (highPrice != null && highPrice > 0L)
			|| (lowPrice != null && lowPrice > 0L)
			|| (avg5mHigh != null && avg5mHigh > 0L)
			|| (avg5mLow != null && avg5mLow > 0L)
			|| (avg1hHigh != null && avg1hHigh > 0L)
			|| (avg1hLow != null && avg1hLow > 0L);
	}

	public long getTotal5mVolume()
	{
		return Math.max(0L, vol5mHigh) + Math.max(0L, vol5mLow);
	}

	public long getTotal1hVolume()
	{
		return Math.max(0L, vol1hHigh) + Math.max(0L, vol1hLow);
	}

	private static Long midpoint(Long a, Long b)
	{
		boolean validA = a != null && a > 0L;
		boolean validB = b != null && b > 0L;
		if (validA && validB)
		{
			return Math.round((a + b) / 2.0);
		}
		if (validA)
		{
			return a;
		}
		if (validB)
		{
			return b;
		}
		return null;
	}

	private static Long firstPositive(Long... candidates)
	{
		if (candidates == null)
		{
			return null;
		}
		for (Long c : candidates)
		{
			if (c != null && c > 0L)
			{
				return c;
			}
		}
		return null;
	}
}
