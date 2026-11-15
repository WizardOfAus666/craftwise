package com.craftwise.util;

import java.util.Locale;

/**
 * Formatting and parsing utilities for OSRS GP amounts and GP/XP ratios.
 */
public final class CurrencyFormatter
{
	private CurrencyFormatter()
	{
	}

	public static String formatGp(long gp, boolean compact)
	{
		if (compact && Math.abs(gp) >= 10_000L)
		{
			return NumberFormatter.formatCompact(gp, true) + " gp";
		}
		return NumberFormatter.formatInt(gp) + " gp";
	}

	public static String formatSignedGp(long gp, boolean compact)
	{
		if (gp > 0L)
		{
			return "+" + formatGp(gp, compact);
		}
		return formatGp(gp, compact);
	}

	public static String formatSignedGp(double gp, boolean compact)
	{
		if (Double.isNaN(gp) || Double.isInfinite(gp))
		{
			return "N/A";
		}
		long rounded = Math.round(gp);
		return formatSignedGp(rounded, compact);
	}

	public static String formatGpPerXp(double gpPerXp)
	{
		if (Double.isNaN(gpPerXp) || Double.isInfinite(gpPerXp))
		{
			return "N/A";
		}
		String sign = gpPerXp > 0.005 ? "+" : "";
		return sign + NumberFormatter.formatDecimal(gpPerXp, 2) + " gp/xp";
	}

	/**
	 * Parses user-entered GP strings supporting suffixes like "k", "m", "b" (e.g., "500k", "1.5m", "10,000").
	 * Returns null if the input cannot be parsed as a valid non-negative GP amount.
	 */
	public static Long parseGpAmount(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String cleaned = raw.trim().toLowerCase(Locale.ROOT)
			.replace(",", "")
			.replace("_", "")
			.replace("gp", "")
			.trim();
		if (cleaned.isEmpty())
		{
			return null;
		}
		double multiplier = 1.0;
		if (cleaned.endsWith("k"))
		{
			multiplier = 1_000.0;
			cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
		}
		else if (cleaned.endsWith("m"))
		{
			multiplier = 1_000_000.0;
			cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
		}
		else if (cleaned.endsWith("b"))
		{
			multiplier = 1_000_000_000.0;
			cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
		}
		try
		{
			double val = Double.parseDouble(cleaned);
			if (Double.isNaN(val) || Double.isInfinite(val) || val < 0.0)
			{
				return null;
			}
			double scaled = val * multiplier;
			if (scaled > Long.MAX_VALUE)
			{
				return Long.MAX_VALUE;
			}
			return Math.round(scaled);
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}
}
