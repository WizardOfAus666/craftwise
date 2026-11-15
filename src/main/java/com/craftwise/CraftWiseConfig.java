package com.craftwise;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

/**
 * RuneLite configuration interface for CraftWise.
 * All settings persist across RuneLite sessions via RuneLite's ConfigManager.
 */
@ConfigGroup(CraftWiseConfig.CONFIG_GROUP)
public interface CraftWiseConfig extends Config
{
	String CONFIG_GROUP = "craftwise";

	enum PriceCalculationMode
	{
		INSTANT_BUY_SELL("Instant Buy / Instant Sell (Conservative)"),
		PATIENT_BUY_SELL("Patient Offers (Low Buy / High Sell)"),
		OPTIMISTIC_MARKET("Optimistic Market (Min Buy / Max Sell)"),
		AVERAGE_MARKET("Average Market (5m / 1h Avg)"),
		INSTANT_BUY("Instant Buy (High Buy / High Sell)"),
		INSTANT_SELL("Instant Sell (Low Buy / Low Sell)"),
		CUSTOM("Custom User Prices");

		private final String label;

		PriceCalculationMode(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum RankingMode
	{
		HIGHEST_XP_PER_HOUR("Highest XP / Hour"),
		HIGHEST_PROFIT_PER_HOUR("Highest Profit / Hour"),
		HIGHEST_PROFIT_PER_ITEM("Highest Profit / Action"),
		LOWEST_COST_PER_XP("Lowest GP / XP"),
		LOWEST_TOTAL_COST_TO_TARGET("Lowest Total Cost to Target"),
		BEST_XP_PROFIT_BALANCE("Best Balance of XP & Profit"),
		BEST_WITHIN_BUDGET("Best Method Within Budget");

		private final String label;

		RankingMode(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(
		name = "Market & Pricing",
		description = "Configure OSRS Wiki Real-time Prices API polling, price assumptions, and GE tax.",
		position = 0
	)
	String SECTION_PRICES = "prices";

	@ConfigSection(
		name = "Recommendations & Display",
		description = "Configure default ranking mode, number formatting, and method visibility.",
		position = 1
	)
	String SECTION_DISPLAY = "display";

	@ConfigSection(
		name = "Training & Budget Defaults",
		description = "Configure default GP budget, target Crafting level, and offline level fallback.",
		position = 2
	)
	String SECTION_PLANNER = "planner";

	@ConfigSection(
		name = "Market Watcher & Alerts",
		description = "Configure profit opportunity thresholds, noise filters, and alert notifications.",
		position = 3
	)
	String SECTION_WATCHER = "watcher";

	@ConfigSection(
		name = "Custom Overrides",
		description = "Persisted custom item prices and custom XP/hour rates.",
		position = 4,
		closedByDefault = true
	)
	String SECTION_OVERRIDES = "overrides";

	@Range(min = 30, max = 3600)
	@ConfigItem(
		keyName = "priceRefreshIntervalSeconds",
		name = "Price refresh interval (s)",
		description = "Background refresh interval in seconds for fetching OSRS Wiki prices (30 to 3600).",
		section = SECTION_PRICES,
		position = 1
	)
	default int priceRefreshIntervalSeconds()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "priceCalculationMode",
		name = "Price calculation mode",
		description = "Select how input material costs and output selling prices are determined.",
		section = SECTION_PRICES,
		position = 2
	)
	default PriceCalculationMode priceCalculationMode()
	{
		return PriceCalculationMode.INSTANT_BUY_SELL;
	}

	@ConfigItem(
		keyName = "includeGeTax",
		name = "Apply Grand Exchange tax",
		description = "Deduct the 2% OSRS GE seller tax (0 GP under 50 GP, capped at 5M GP per item) from output revenue.",
		section = SECTION_PRICES,
		position = 3
	)
	default boolean includeGeTax()
	{
		return true;
	}

	@ConfigItem(
		keyName = "preferredRankingMode",
		name = "Default ranking mode",
		description = "Default sort and recommendation mode for CraftWise.",
		section = SECTION_DISPLAY,
		position = 1
	)
	default RankingMode preferredRankingMode()
	{
		return RankingMode.BEST_XP_PROFIT_BALANCE;
	}

	@ConfigItem(
		keyName = "compactNumbers",
		name = "Compact GP & XP numbers",
		description = "Format large numbers using K, M, and B suffixes (e.g. 1.25M gp).",
		section = SECTION_DISPLAY,
		position = 2
	)
	default boolean compactNumbers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFutureUnlocks",
		name = "Include future level unlocks",
		description = "Include methods above your current Crafting level in general tables.",
		section = SECTION_DISPLAY,
		position = 3
	)
	default boolean showFutureUnlocks()
	{
		return false;
	}

	@Range(min = 0, max = 2147483647)
	@ConfigItem(
		keyName = "defaultBudgetGp",
		name = "Default GP budget",
		description = "Default available budget in GP for the budget optimiser and planner.",
		section = SECTION_PLANNER,
		position = 1
	)
	default int defaultBudgetGp()
	{
		return 5_000_000;
	}

	@Range(min = 2, max = 99)
	@ConfigItem(
		keyName = "defaultTargetLevel",
		name = "Default target level",
		description = "Default target Crafting level (2 to 99).",
		section = SECTION_PLANNER,
		position = 2
	)
	default int defaultTargetLevel()
	{
		return 99;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(
		keyName = "manualFallbackLevel",
		name = "Offline fallback level",
		description = "Manual Crafting level used when not logged into RuneLite or when manual override is enabled.",
		section = SECTION_PLANNER,
		position = 3
	)
	default int manualFallbackLevel()
	{
		return 70;
	}

	@ConfigItem(
		keyName = "customPricesJson",
		name = "Custom prices (JSON)",
		description = "Serialized custom item price overrides (managed from the in-plugin Settings & Calculator).",
		section = SECTION_OVERRIDES,
		position = 1
	)
	default String customPricesJson()
	{
		return "{}";
	}

	@ConfigItem(
		keyName = "customXpRatesJson",
		name = "Custom XP/hr rates (JSON)",
		description = "Serialized custom actions/hour or XP/hour overrides (managed from in-plugin Settings).",
		section = SECTION_OVERRIDES,
		position = 2
	)
	default String customXpRatesJson()
	{
		return "{}";
	}

	@ConfigItem(
		keyName = "enableMarketAlerts",
		name = "Enable Market Alerts",
		description = "Notify when watched or scanned Crafting recipes cross configured profit thresholds.",
		section = SECTION_WATCHER,
		position = 1
	)
	default boolean enableMarketAlerts()
	{
		return true;
	}

	@ConfigItem(
		keyName = "playAlertSound",
		name = "Play Alert Sound",
		description = "Play a system audio chime alongside desktop notification when an alert triggers.",
		section = SECTION_WATCHER,
		position = 2
	)
	default boolean playAlertSound()
	{
		return false;
	}

	@ConfigItem(
		keyName = "alertOnNewlyProfitable",
		name = "Alert: Newly Profitable",
		description = "Alert when a previously unprofitable recipe turns profitable.",
		section = SECTION_WATCHER,
		position = 3
	)
	default boolean alertOnNewlyProfitable()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alertOnBreakEven",
		name = "Alert: Reaches Break-Even",
		description = "Alert when a recipe reaches near break-even from a loss state.",
		section = SECTION_WATCHER,
		position = 4
	)
	default boolean alertOnBreakEven()
	{
		return true;
	}

	@ConfigItem(
		keyName = "alertOnWatchedLoss",
		name = "Alert: Watched Turns Unprofitable",
		description = "Alert when a watched recipe falls below break-even into a loss.",
		section = SECTION_WATCHER,
		position = 5
	)
	default boolean alertOnWatchedLoss()
	{
		return true;
	}

	@Range(min = 1, max = 10000000)
	@ConfigItem(
		keyName = "alertMinProfitGp",
		name = "Alert Profit / Craft (GP)",
		description = "Minimum GP profit per action to trigger a profit threshold alert.",
		section = SECTION_WATCHER,
		position = 6
	)
	default int alertMinProfitGp()
	{
		return 500;
	}

	@Range(min = 1, max = 100000000)
	@ConfigItem(
		keyName = "alertMinInventoryProfitGp",
		name = "Alert Profit / Inv (GP)",
		description = "Minimum GP profit per full inventory to trigger an inventory alert.",
		section = SECTION_WATCHER,
		position = 7
	)
	default int alertMinInventoryProfitGp()
	{
		return 5000;
	}

	@Range(min = 1, max = 500)
	@ConfigItem(
		keyName = "alertMinImprovementPercent",
		name = "Alert Margin Improvement (%)",
		description = "Minimum percentage-point margin improvement to classify/alert as Improving.",
		section = SECTION_WATCHER,
		position = 8
	)
	default int alertMinImprovementPercent()
	{
		return 10;
	}

	@Range(min = 1, max = 1440)
	@ConfigItem(
		keyName = "alertCooldownMinutes",
		name = "Alert Cooldown (mins)",
		description = "Minimum cooldown per recipe/condition before another notification can fire.",
		section = SECTION_WATCHER,
		position = 9
	)
	default int alertCooldownMinutes()
	{
		return 15;
	}

	@Range(min = 1, max = 1000000)
	@ConfigItem(
		keyName = "highProfitThresholdGp",
		name = "High Profit Threshold (GP)",
		description = "Minimum net profit per action required to label an opportunity as HIGH PROFIT.",
		section = SECTION_WATCHER,
		position = 10
	)
	default int highProfitThresholdGp()
	{
		return 250;
	}
}
