package com.craftwise.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Number, XP, percentage, and duration formatting utilities for CraftWise.
 */
public final class NumberFormatter
{
	private static final NumberFormat INT_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
	private static final DecimalFormat ONE_DEC_FORMAT = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));
	private static final DecimalFormat TWO_DEC_FORMAT = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

	private NumberFormatter()
	{
	}

	public static synchronized String formatInt(long value)
	{
		return INT_FORMAT.format(value);
	}

	public static synchronized String formatDecimal(double value, int decimals)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return "N/A";
		}
		if (decimals <= 0)
		{
			return INT_FORMAT.format(Math.round(value));
		}
		if (decimals == 1)
		{
			return ONE_DEC_FORMAT.format(value);
		}
		return TWO_DEC_FORMAT.format(value);
	}

	public static String formatCompact(double value, boolean useCompact)
	{
		if (Double.isNaN(value) || Double.isInfinite(value))
		{
			return "N/A";
		}
		if (!useCompact)
		{
			return formatInt(Math.round(value));
		}
		double abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs >= 1_000_000_000.0)
		{
			return sign + formatDecimal(abs / 1_000_000_000.0, 2) + "B";
		}
		if (abs >= 1_000_000.0)
		{
			return sign + formatDecimal(abs / 1_000_000.0, 2) + "M";
		}
		if (abs >= 10_000.0)
		{
			return sign + formatDecimal(abs / 1_000.0, 1) + "K";
		}
		return formatInt(Math.round(value));
	}

	public static String formatXp(double xp, boolean compact)
	{
		if (Double.isNaN(xp) || Double.isInfinite(xp))
		{
			return "N/A";
		}
		if (compact && Math.abs(xp) >= 10_000.0)
		{
			return formatCompact(xp, true) + " XP";
		}
		if (Math.abs(xp - Math.rint(xp)) < 1e-6)
		{
			return formatInt(Math.round(xp)) + " XP";
		}
		return formatDecimal(xp, 1) + " XP";
	}

	public static String formatXpPerHour(double xpPerHour, boolean compact)
	{
		if (Double.isNaN(xpPerHour) || Double.isInfinite(xpPerHour) || xpPerHour <= 0.0)
		{
			return "N/A";
		}
		return formatCompact(xpPerHour, compact) + " XP/hr";
	}

	public static String formatPercent(double percent)
	{
		if (Double.isNaN(percent) || Double.isInfinite(percent))
		{
			return "N/A";
		}
		return formatDecimal(percent, 1) + "%";
	}

	/**
	 * Formats a duration in hours into a concise readable string (e.g. "12m", "2h 15m", "3d 4h").
	 */
	public static String formatHours(double hours)
	{
		if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0.0)
		{
			return "N/A";
		}
		long totalMinutes = Math.round(hours * 60.0);
		if (totalMinutes < 1L)
		{
			return "<1m";
		}
		long days = totalMinutes / (24L * 60L);
		long remHours = (totalMinutes % (24L * 60L)) / 60L;
		long mins = totalMinutes % 60L;
		if (days > 0L)
		{
			return days + "d " + remHours + "h";
		}
		if (remHours > 0L)
		{
			return remHours + "h " + mins + "m";
		}
		return mins + "m";
	}

	/**
	 * Formats an epoch timestamp (seconds) into a relative age label like "12s ago", "4m ago", "2h ago".
	 */
	public static String formatRelativeAge(long epochSeconds, long nowEpochSeconds)
	{
		if (epochSeconds <= 0L)
		{
			return "Never";
		}
		long diff = Math.max(0L, nowEpochSeconds - epochSeconds);
		if (diff < 60L)
		{
			return diff + "s ago";
		}
		if (diff < 3600L)
		{
			return (diff / 60L) + "m ago";
		}
		if (diff < 86400L)
		{
			return (diff / 3600L) + "h ago";
		}
		return (diff / 86400L) + "d ago";
	}
}
