package com.craftwise.util;

/**
 * Official Old School RuneScape XP progression table covering levels 1 to 99.
 * Uses the exact Jagex integer floor formula:
 * {@code XP(L) = floor( (1/4) * sum_{i=1}^{L-1} floor(i + 300 * 2^(i/7)) )}.
 */
public final class XPTable
{
	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 99;
	public static final long MAX_XP = 200_000_000L;

	private static final long[] XP_FOR_LEVEL = new long[MAX_LEVEL + 1];

	static
	{
		XP_FOR_LEVEL[1] = 0L;
		long points = 0L;
		for (int level = 1; level < MAX_LEVEL; level++)
		{
			points += (long) Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0));
			XP_FOR_LEVEL[level + 1] = points / 4L;
		}
	}

	private XPTable()
	{
	}

	/**
	 * Returns the minimum total XP required to reach the specified level (1..99).
	 */
	public static long getXpForLevel(int level)
	{
		int clamped = clampLevel(level);
		return XP_FOR_LEVEL[clamped];
	}

	/**
	 * Returns the Crafting level (1..99) corresponding to the given total XP.
	 */
	public static int getLevelForXp(long xp)
	{
		if (xp <= 0L)
		{
			return MIN_LEVEL;
		}
		for (int level = MAX_LEVEL; level >= MIN_LEVEL; level--)
		{
			if (xp >= XP_FOR_LEVEL[level])
			{
				return level;
			}
		}
		return MIN_LEVEL;
	}

	/**
	 * Returns XP remaining from {@code currentXp} to reach {@code targetLevel}.
	 * Returns 0 if {@code currentXp} already meets or exceeds {@code targetLevel}.
	 */
	public static long getXpRemainingToLevel(long currentXp, int targetLevel)
	{
		long targetXp = getXpForLevel(targetLevel);
		return Math.max(0L, targetXp - Math.max(0L, currentXp));
	}

	/**
	 * Returns XP remaining until the next level (returns 0 if already level 99+).
	 */
	public static long getXpToNextLevel(long currentXp)
	{
		int currentLevel = getLevelForXp(currentXp);
		if (currentLevel >= MAX_LEVEL)
		{
			return 0L;
		}
		return getXpRemainingToLevel(currentXp, currentLevel + 1);
	}

	/**
	 * Returns progress percentage (0.0 to 100.0) within the current level toward the next level.
	 */
	public static double getProgressToNextLevelPercent(long currentXp)
	{
		long safeXp = Math.max(0L, currentXp);
		int currentLevel = getLevelForXp(safeXp);
		if (currentLevel >= MAX_LEVEL)
		{
			return 100.0;
		}
		long levelStartXp = getXpForLevel(currentLevel);
		long nextLevelXp = getXpForLevel(currentLevel + 1);
		long span = nextLevelXp - levelStartXp;
		if (span <= 0L)
		{
			return 100.0;
		}
		return Math.min(100.0, Math.max(0.0, ((safeXp - levelStartXp) * 100.0) / span));
	}

	/**
	 * Returns overall progress percentage (0.0 to 100.0) from {@code currentXp} toward {@code targetLevel}.
	 */
	public static double getProgressToTargetPercent(long currentXp, int targetLevel)
	{
		long safeXp = Math.max(0L, currentXp);
		long targetXp = getXpForLevel(targetLevel);
		if (targetXp <= 0L || safeXp >= targetXp)
		{
			return 100.0;
		}
		return Math.min(100.0, Math.max(0.0, (safeXp * 100.0) / targetXp));
	}

	public static int clampLevel(int level)
	{
		return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
	}
}
