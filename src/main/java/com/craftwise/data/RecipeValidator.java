package com.craftwise.data;

import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates structural and OSRS game-mechanic integrity of {@link CraftingRecipe} definitions.
 */
public final class RecipeValidator
{
	private RecipeValidator()
	{
	}

	public static List<String> validateRecipe(CraftingRecipe recipe)
	{
		if (recipe == null)
		{
			return Collections.singletonList("Recipe is null.");
		}
		List<String> errors = new ArrayList<>();

		if (recipe.getId() == null || recipe.getId().trim().isEmpty())
		{
			errors.add("Missing recipe id.");
		}
		if (recipe.getName() == null || recipe.getName().trim().isEmpty())
		{
			errors.add("Missing recipe name for " + recipe.getId());
		}
		if (recipe.getCategory() == null)
		{
			errors.add("Missing category for " + recipe.getId());
		}
		if (recipe.getRequiredLevel() < 1 || recipe.getRequiredLevel() > 99)
		{
			errors.add("Invalid requiredLevel (" + recipe.getRequiredLevel() + ") for " + recipe.getId());
		}
		if (recipe.getXpPerAction() < 0.0)
		{
			errors.add("Negative xpPerAction for " + recipe.getId());
		}
		if (recipe.getXpPerOutputItem() < 0.0)
		{
			errors.add("Negative xpPerOutputItem for " + recipe.getId());
		}
		if (recipe.getInputs() == null || recipe.getInputs().isEmpty())
		{
			errors.add("Recipe has no inputs: " + recipe.getId());
		}
		else
		{
			for (RecipeInput input : recipe.getInputs())
			{
				if (input.getItemId() <= 0)
				{
					errors.add("Invalid input itemId in " + recipe.getId() + ": " + input.getItemName());
				}
				if (input.getQuantity() <= 0.0)
				{
					errors.add("Non-positive input quantity in " + recipe.getId() + ": " + input.getItemName());
				}
				if (input.getItemName() == null || input.getItemName().trim().isEmpty())
				{
					errors.add("Missing input itemName in " + recipe.getId());
				}
			}
		}

		if (recipe.getOutputs() == null || recipe.getOutputs().isEmpty())
		{
			errors.add("Recipe has no outputs: " + recipe.getId());
		}
		else
		{
			for (RecipeOutput output : recipe.getOutputs())
			{
				if (output.getItemId() <= 0)
				{
					errors.add("Invalid output itemId in " + recipe.getId() + ": " + output.getItemName());
				}
				if (output.getQuantity() <= 0.0)
				{
					errors.add("Non-positive output quantity in " + recipe.getId() + ": " + output.getItemName());
				}
				if (output.getProbability() <= 0.0 || output.getProbability() > 1.0)
				{
					errors.add("Invalid output probability (" + output.getProbability() + ") in " + recipe.getId());
				}
				if (output.getItemName() == null || output.getItemName().trim().isEmpty())
				{
					errors.add("Missing output itemName in " + recipe.getId());
				}
			}
		}

		if (recipe.getWikiReference() == null || !recipe.getWikiReference().startsWith("https://oldschool.runescape.wiki/"))
		{
			errors.add("Missing or invalid OSRS Wiki reference URL for " + recipe.getId());
		}
		if (recipe.getVerificationStatus() == null)
		{
			errors.add("Missing verificationStatus for " + recipe.getId());
		}
		if (recipe.getEstimatedActionsPerHour() <= 0.0)
		{
			errors.add("Non-positive estimatedActionsPerHour for " + recipe.getId());
		}

		return errors;
	}

	public static List<String> validateAll(List<CraftingRecipe> recipes)
	{
		if (recipes == null || recipes.isEmpty())
		{
			return Collections.singletonList("Recipe list is empty.");
		}
		List<String> allErrors = new ArrayList<>();
		Set<String> seenIds = new HashSet<>();
		for (CraftingRecipe recipe : recipes)
		{
			if (recipe != null && recipe.getId() != null && !seenIds.add(recipe.getId()))
			{
				allErrors.add("Duplicate recipe ID: " + recipe.getId());
			}
			allErrors.addAll(validateRecipe(recipe));
		}
		return allErrors;
	}
}
