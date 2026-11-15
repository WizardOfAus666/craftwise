package com.craftwise.model;

import com.craftwise.util.XPTable;
import lombok.Builder;
import lombok.Data;

/**
 * Snapshot of the player's current Crafting level, XP, and progress toward next/target levels.
 */
@Data
@Builder
public class PlayerCraftingStats
{
	private final boolean loggedIn;
	private final boolean usingManualFallback;
	private final String playerName;

	private final int realLevel;
	private final int boostedLevel;
	private final long currentXp;
	private final int targetLevel;

	private final long xpToNextLevel;
	private final long xpToTarget;
	private final double progressToNextLevelPercent;
	private final double progressToTargetPercent;

	public static PlayerCraftingStats fromLevelAndXp(
		boolean loggedIn,
		boolean manualFallback,
		String playerName,
		int realLevel,
		int boostedLevel,
		long currentXp,
		int targetLevel)
	{
		int safeReal = XPTable.clampLevel(realLevel);
		int safeBoosted = Math.max(1, Math.min(125, boostedLevel));
		int safeTarget = XPTable.clampLevel(targetLevel);
		long safeXp = Math.max(0L, Math.min(XPTable.MAX_XP, currentXp));

		return PlayerCraftingStats.builder()
			.loggedIn(loggedIn)
			.usingManualFallback(manualFallback)
			.playerName(playerName != null && !playerName.isEmpty() ? playerName : "Player")
			.realLevel(safeReal)
			.boostedLevel(safeBoosted)
			.currentXp(safeXp)
			.targetLevel(safeTarget)
			.xpToNextLevel(XPTable.getXpToNextLevel(safeXp))
			.xpToTarget(XPTable.getXpRemainingToLevel(safeXp, safeTarget))
			.progressToNextLevelPercent(XPTable.getProgressToNextLevelPercent(safeXp))
			.progressToTargetPercent(XPTable.getProgressToTargetPercent(safeXp, safeTarget))
			.build();
	}
}
