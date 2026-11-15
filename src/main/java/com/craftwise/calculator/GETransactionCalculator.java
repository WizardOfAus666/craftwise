package com.craftwise.calculator;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.ItemID;

/**
 * Implements official Old School RuneScape Grand Exchange transaction tax rules:
 * - Seller pays 2% tax, rounded down to the nearest whole GP per individual item sold.
 * - Items sold below 50 GP incur 0 GP tax (floor(49 * 0.02) = 0).
 * - Tax is capped at 5,000,000 GP per single item (sales >= 250,000,000 GP).
 * - Buying on the Grand Exchange never incurs GE tax.
 * - Exempt items (Old School Bonds, starter tools, untradeables, Coins) incur 0 GP tax.
 */
public final class GETransactionCalculator
{
	public static final double TAX_RATE = 0.02;
	public static final long TAX_DIVISOR = 50L;
	public static final long MIN_TAXABLE_PRICE_GP = 50L;
	public static final long MAX_TAX_PER_ITEM_GP = 5_000_000L;

	private static final Set<Integer> EXEMPT_ITEM_IDS;

	static
	{
		// Official OSRS Wiki Grand Exchange#Exempt_from_tax list (post May 2025 2% update)
		Set<Integer> exempt = new HashSet<>();
		exempt.add(ItemID.COINS_995);
		exempt.add(ItemID.PLATINUM_TOKEN);
		exempt.add(ItemID.OLD_SCHOOL_BOND);
		// Tools
		exempt.add(ItemID.CHISEL);
		exempt.add(ItemID.GARDENING_TROWEL);
		exempt.add(ItemID.GLASSBLOWING_PIPE);
		exempt.add(ItemID.HAMMER);
		exempt.add(ItemID.NEEDLE);
		exempt.add(ItemID.PESTLE_AND_MORTAR);
		exempt.add(ItemID.RAKE);
		exempt.add(ItemID.SAW);
		exempt.add(ItemID.SECATEURS);
		exempt.add(ItemID.SEED_DIBBER);
		exempt.add(ItemID.SHEARS);
		exempt.add(ItemID.SPADE);
		exempt.add(ItemID.WATERING_CAN);
		// Teleport items (including craftable/enchantable jewellery)
		exempt.add(ItemID.GAMES_NECKLACE8);
		exempt.add(ItemID.RING_OF_DUELING8);
		exempt.add(ItemID.TELEPORT_TO_HOUSE);
		exempt.add(ItemID.VARROCK_TELEPORT);
		exempt.add(ItemID.LUMBRIDGE_TELEPORT);
		exempt.add(ItemID.FALADOR_TELEPORT);
		exempt.add(ItemID.CAMELOT_TELEPORT);
		exempt.add(ItemID.ARDOUGNE_TELEPORT);
		exempt.add(19651); // Kourend castle teleport (tablet)
		exempt.add(28824); // Civitas illa fortis teleport
		// Energy potions
		exempt.add(ItemID.ENERGY_POTION4);
		exempt.add(ItemID.ENERGY_POTION3);
		exempt.add(ItemID.ENERGY_POTION2);
		exempt.add(ItemID.ENERGY_POTION1);
		// Low level combat consumables
		exempt.add(ItemID.BRONZE_ARROW);
		exempt.add(ItemID.IRON_ARROW);
		exempt.add(ItemID.STEEL_ARROW);
		exempt.add(ItemID.BRONZE_DART);
		exempt.add(ItemID.IRON_DART);
		exempt.add(ItemID.STEEL_DART);
		exempt.add(ItemID.MIND_RUNE);
		// Low level food
		exempt.add(ItemID.BASS);
		exempt.add(ItemID.BREAD);
		exempt.add(ItemID.CAKE);
		exempt.add(ItemID.COOKED_CHICKEN);
		exempt.add(ItemID.COOKED_MEAT);
		exempt.add(ItemID.HERRING);
		exempt.add(ItemID.LOBSTER);
		exempt.add(ItemID.MACKEREL);
		exempt.add(ItemID.MEAT_PIE);
		exempt.add(ItemID.PIKE);
		exempt.add(ItemID.SALMON);
		exempt.add(ItemID.SHRIMPS);
		exempt.add(ItemID.TUNA);
		EXEMPT_ITEM_IDS = Collections.unmodifiableSet(exempt);
	}

	private GETransactionCalculator()
	{
	}

	public static boolean isItemTaxExempt(int itemId, boolean recipeTaxExempt)
	{
		return recipeTaxExempt || itemId <= 0 || EXEMPT_ITEM_IDS.contains(itemId);
	}

	/**
	 * Calculates the GE seller tax in whole GP for a single item sold at {@code unitSellPrice}.
	 */
	public static long calculateTaxPerItem(long unitSellPrice, int itemId, boolean recipeTaxExempt, boolean includeGeTax)
	{
		if (!includeGeTax || unitSellPrice < MIN_TAXABLE_PRICE_GP || isItemTaxExempt(itemId, recipeTaxExempt))
		{
			return 0L;
		}
		long tax = unitSellPrice / TAX_DIVISOR;
		return Math.min(MAX_TAX_PER_ITEM_GP, Math.max(0L, tax));
	}

	/**
	 * Calculates the net GP received by the seller for a single item after GE tax.
	 */
	public static long calculateNetRevenuePerItem(long unitSellPrice, int itemId, boolean recipeTaxExempt, boolean includeGeTax)
	{
		long safePrice = Math.max(0L, unitSellPrice);
		return safePrice - calculateTaxPerItem(safePrice, itemId, recipeTaxExempt, includeGeTax);
	}

	/**
	 * Calculates total GE tax for selling {@code quantity} items at {@code unitSellPrice} each.
	 * Rounding is applied per individual item before multiplying by quantity.
	 */
	public static long calculateTotalTax(long unitSellPrice, long quantity, int itemId, boolean recipeTaxExempt, boolean includeGeTax)
	{
		if (quantity <= 0L)
		{
			return 0L;
		}
		return calculateTaxPerItem(unitSellPrice, itemId, recipeTaxExempt, includeGeTax) * quantity;
	}

	/**
	 * Calculates expected GE tax for fractional/probability-weighted output quantities.
	 */
	public static double calculateExpectedTax(long unitSellPrice, double expectedQuantity, int itemId, boolean recipeTaxExempt, boolean includeGeTax)
	{
		if (expectedQuantity <= 0.0)
		{
			return 0.0;
		}
		return calculateTaxPerItem(unitSellPrice, itemId, recipeTaxExempt, includeGeTax) * expectedQuantity;
	}
}
