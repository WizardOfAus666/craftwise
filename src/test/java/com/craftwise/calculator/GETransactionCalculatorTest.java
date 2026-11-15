package com.craftwise.calculator;

import net.runelite.api.ItemID;
import org.junit.Assert;
import org.junit.Test;

public class GETransactionCalculatorTest
{
	@Test
	public void testTaxBelowFiftyGpIsZero()
	{
		Assert.assertEquals(0L, GETransactionCalculator.calculateTaxPerItem(0L, ItemID.VIAL, false, true));
		Assert.assertEquals(0L, GETransactionCalculator.calculateTaxPerItem(1L, ItemID.VIAL, false, true));
		Assert.assertEquals(0L, GETransactionCalculator.calculateTaxPerItem(49L, ItemID.VIAL, false, true));
		Assert.assertEquals(1L, GETransactionCalculator.calculateTaxPerItem(50L, ItemID.VIAL, false, true));
	}

	@Test
	public void testTwoPercentFloorAndCap()
	{
		Assert.assertEquals(1L, GETransactionCalculator.calculateTaxPerItem(99L, ItemID.SAPPHIRE, false, true));
		Assert.assertEquals(2L, GETransactionCalculator.calculateTaxPerItem(100L, ItemID.SAPPHIRE, false, true));
		Assert.assertEquals(2_400L, GETransactionCalculator.calculateTaxPerItem(120_000L, ItemID.DRAGONSTONE_RING, false, true));
		Assert.assertEquals(5_000_000L, GETransactionCalculator.calculateTaxPerItem(250_000_000L, ItemID.ZENYTE_AMULET, false, true));
		Assert.assertEquals(5_000_000L, GETransactionCalculator.calculateTaxPerItem(1_000_000_000L, ItemID.ZENYTE_AMULET, false, true));
	}

	@Test
	public void testExemptItemsAndPerItemRoundingForMultipleOutputs()
	{
		Assert.assertEquals(0L, GETransactionCalculator.calculateTaxPerItem(10_000_000L, ItemID.OLD_SCHOOL_BOND, false, true));
		Assert.assertEquals(0L, GETransactionCalculator.calculateTaxPerItem(5_000L, ItemID.CHISEL, false, true));
		Assert.assertEquals(0L, GETransactionCalculator.calculateTaxPerItem(5_000L, ItemID.EMPTY_LIGHT_ORB, true, true));

		// 15 amethyst bolt tips sold at 99 GP each: floor(99 * 0.02) = 1 GP per tip => 15 GP total (NOT 29 GP)
		Assert.assertEquals(15L, GETransactionCalculator.calculateTotalTax(99L, 15L, ItemID.AMETHYST_BOLT_TIPS, false, true));
	}
}
