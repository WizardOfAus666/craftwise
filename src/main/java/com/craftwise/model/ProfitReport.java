package com.craftwise.model;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Aggregated market & profitability report across all evaluated Crafting recipes.
 */
@Data
@Builder
public class ProfitReport
{
	private final long generatedAtEpochSec;
	private final PriceCalculationMode priceMode;
	private final boolean apiLive;
	private final boolean usingStaleCache;
	private final String marketStatusMessage;

	private final int totalRecipes;
	private final int completeRecipes;
	private final int incompleteRecipes;
	private final int profitableUnlockedRecipes;

	private final CraftingResult bestProfitPerHour;
	private final CraftingResult bestProfitPerCraft;
	private final CraftingResult bestXpPerHour;
	private final CraftingResult cheapestTrainingMethod;
	private final CraftingResult bestBalancedMethod;

	@Builder.Default
	private final List<CraftingResult> allResults = Collections.emptyList();
}
