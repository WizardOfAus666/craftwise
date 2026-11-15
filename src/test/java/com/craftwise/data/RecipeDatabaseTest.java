package com.craftwise.data;

import com.craftwise.data.CraftingRecipe.VerificationStatus;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class RecipeDatabaseTest
{
	@Test
	public void testAllRecipesPassValidationWithZeroErrors()
	{
		List<CraftingRecipe> recipes = CraftingDataRepository.getAllRecipes();
		Assert.assertTrue("Expected comprehensive recipe database", recipes.size() >= 70);

		List<String> errors = RecipeValidator.validateAll(recipes);
		Assert.assertTrue("Validation errors found: " + errors, errors.isEmpty());
	}

	@Test
	public void testVerifiedOsrsRecipeMechanics()
	{
		CraftingRecipe cutZenyte = CraftingDataRepository.getById("cut_zenyte");
		Assert.assertNotNull(cutZenyte);
		Assert.assertEquals(89, cutZenyte.getRequiredLevel());
		Assert.assertEquals(50.0, cutZenyte.getXpPerAction(), 0.001);
		Assert.assertEquals(VerificationStatus.VERIFIED, cutZenyte.getVerificationStatus());

		CraftingRecipe opal = CraftingDataRepository.getById("cut_opal");
		Assert.assertNotNull(opal);
		Assert.assertTrue(opal.isMultiOutput());
		Assert.assertTrue(opal.hasVariableOutcome());
		Assert.assertEquals(VerificationStatus.NEEDS_REVIEW, opal.getVerificationStatus());

		CraftingRecipe blackBody = CraftingDataRepository.getById("black_dhide_body");
		Assert.assertNotNull(blackBody);
		Assert.assertEquals(84, blackBody.getRequiredLevel());
		Assert.assertEquals(258.0, blackBody.getXpPerAction(), 0.001);
		Assert.assertTrue(blackBody.isMultiInput());
		Assert.assertEquals(3.0, blackBody.getInputs().get(0).getQuantity(), 0.001);

		CraftingRecipe amethystTips = CraftingDataRepository.getById("amethyst_bolt_tips");
		Assert.assertNotNull(amethystTips);
		Assert.assertEquals(83, amethystTips.getRequiredLevel());
		Assert.assertEquals(60.0, amethystTips.getXpPerAction(), 0.001);
		Assert.assertEquals(15.0, amethystTips.getPrimaryOutput().getQuantity(), 0.001);
		Assert.assertEquals(4.0, amethystTips.getXpPerOutputItem(), 0.001);

		CraftingRecipe emptyLightOrb = CraftingDataRepository.getById("empty_light_orb");
		Assert.assertNotNull(emptyLightOrb);
		Assert.assertEquals(87, emptyLightOrb.getRequiredLevel());
		Assert.assertEquals(70.0, emptyLightOrb.getXpPerAction(), 0.001);
		Assert.assertFalse(emptyLightOrb.isTradeableOutput());

		CraftingRecipe snelm = CraftingDataRepository.getById("myre_snelm");
		Assert.assertNotNull(snelm);
		Assert.assertEquals(32.5, snelm.getXpPerAction(), 0.001);
	}
}
