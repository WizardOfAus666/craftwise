# CraftWise

**Author:** Gaige

**CraftWise** is an Old School RuneScape (OSRS) RuneLite plugin for Crafting training optimisation, Grand Exchange (GE) profit/loss calculation, break-even analysis, and live OSRS Wiki market inspection.

## Key Features

- **Live OSRS Wiki Market Integration**
  - Pulls live pricing data from the OSRS Wiki Real-time Prices API (`/latest`, `/mapping`, `/5m`, `/1h`, `/timeseries`, attempting `/api/v2/osrs` with automatic fallback to `/api/v1/osrs`).
  - Queries official OSRS Wiki `api.php` metadata for wiki page validation.
  - Caches item prices locally, identifies missing or stale prices explicitly (never defaulting unknown items to 0 GP), and refreshes asynchronously off the client thread.
- **Verified OSRS Crafting Recipe Database**
  - Covers all major Crafting categories: Gem Cutting, Jewellery, Leather, Dragonhide, Glassblowing, Battlestaves, Pottery, and Other (Silver items, Tiaras, Xerician clothing, Snelms, etc.).
  - Models multi-input recipes, variable/probability-based outputs (such as semi-precious gem crushing for Opal, Jade, and Red Topaz), equipment/spell/quest prerequisites, and explicit `VerificationStatus`.
- **Accurate 2% OSRS Grand Exchange Tax Engine**
  - Implements the current 2% GE seller tax (`floor(price / 50)` per item), `< 50 GP` tax exemption, `5,000,000 GP` per-item cap at `250,000,000+ GP`, and explicit exempt-item handling.
- **Profit/Loss & Break-Even Calculators**
  - Supports `INSTANT_BUY_SELL`, `INSTANT_BUY`, `INSTANT_SELL`, `AVERAGE_MARKET`, and `CUSTOM` price assumptions.
  - Displays 1 / 10 / 100 / 1,000 craft totals, hourly projections, GP/XP, exact integer break-even sell prices validated through `GETransactionCalculator`, maximum input prices, and `GREEN` / `YELLOW` / `RED` viability indicators.
- **XP, Budget & Multi-Stage Training Planner**
  - Reads the player's live Crafting level and XP from the RuneLite `Client` (with manual level 1–99 fallback) using the official OSRS XP table.
  - Plans single-method or budget-constrained training paths and multi-stage level-range routes (e.g., cheapest path to 99, fastest XP, or best XP/profit balance).
  - Includes inventory/session capacity and GE buy-limit (4-hour) estimates.

## Project Structure

```text
src/main/java/com/craftwise/
  CraftWisePlugin.java        # RuneLite plugin lifecycle & event subscriptions
  CraftWiseConfig.java        # Plugin configuration options
  api/                        # WikiPriceClient, WikiDataClient, PriceCache, ApiResponseHandler
  calculator/                 # Profit, BreakEven, XP, Budget, TrainingOptimizer, Inventory, GE Tax
  data/                       # CraftingCategory, CraftingRecipe, CraftingDataRepository, RecipeValidator, CraftingDataManager
  model/                      # ItemPrice, CraftingResult, ProfitReport, TrainingPlan, PlayerCraftingStats
  ui/                         # CraftWisePanel + 8 views + RecipeDetailPanel + CraftWiseTheme
  util/                       # XPTable, CurrencyFormatter, NumberFormatter
```

## Build & Test

### Quick Verify (Standalone / No Gradle Daemon)

```powershell
powershell -ExecutionPolicy Bypass -File build.ps1 verify
```

### Standard Gradle Build & RuneLite Launch

```powershell
.\gradlew.bat test
.\gradlew.bat shadowJar
.\gradlew.bat run
```

## Disclaimer

Market prices are sourced dynamically from third-party community trades reported by the OSRS Wiki Prices API and should be treated as live estimates rather than guaranteed execution prices.
