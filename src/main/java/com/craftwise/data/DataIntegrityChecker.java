package com.craftwise.data;

import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

/**
 * Startup and test-time integrity checker for {@link CraftingDataRepository} recipes.
 * Validates structural consistency, OSRS bounds, non-negative XP/quantities, probability sums,
 * verification status coverage, and Wiki references without blocking startup on non-fatal warnings.
 */
public final class DataIntegrityChecker
{
	@Data
	@Builder
	public static class IntegrityReport
	{
		private final int totalRecipes;
		private final int verifiedCount;
		private final int needsReviewCount;
		private final int unsupportedCount;
		@Builder.Default
		private final List<String> errors = Collections.emptyList();
		@Builder.Default
		private final List<String> warnings = Collections.emptyList();

		public boolean isPassed()
		{
			return errors == null || errors.isEmpty();
		}
	}

	private DataIntegrityChecker()
	{
	}

	public static IntegrityReport check(List<CraftingRecipe> recipes)
	{
		return check(recipes, Collections.emptyMap());
	}

	/**
	 * Runs full structural and optional live Wiki item-name cross-checks on {@code recipes}.
	 */
	public static IntegrityReport check(List<CraftingRecipe> recipes, Map<Integer, String> liveMappingNamesById)
	{
		if (recipes == null || recipes.isEmpty())
		{
			return IntegrityReport.builder()
				.totalRecipes(0)
				.errors(Collections.singletonList("Recipe repository is empty."))
				.build();
		}

		List<String> errors = new ArrayList<>(RecipeValidator.validateAll(recipes));
		List<String> warnings = new ArrayList<>();
		Set<String> seenNamesByVariant = new HashSet<>();
		int verified = 0;
		int needsReview = 0;
		int unsupported = 0;

		for (CraftingRecipe recipe : recipes)
		{
			if (recipe == null)
			{
				continue;
			}
			VerificationStatus status = recipe.getVerificationStatus();
			if (status == VerificationStatus.VERIFIED)
			{
				verified++;
			}
			else if (status == VerificationStatus.NEEDS_REVIEW)
			{
				needsReview++;
			}
			else if (status == VerificationStatus.UNSUPPORTED)
			{
				unsupported++;
			}

			String dedupKey = (recipe.getName() + "|" + recipe.getMethodVariant()).toLowerCase(Locale.ROOT);
			if (!seenNamesByVariant.add(dedupKey))
			{
				warnings.add("Duplicate name+variant key: " + recipe.getName() + " (" + recipe.getMethodVariant() + ")");
			}

			// Verify exclusive probability sums on variable outputs don't exceed 1.0 + eps
			if (recipe.hasVariableOutcome() && recipe.getOutputs() != null)
			{
				double probSum = 0.0;
				for (RecipeOutput out : recipe.getOutputs())
				{
					probSum += out.getProbability();
				}
				if (probSum > 1.001)
				{
					errors.add("Variable outcome probability sum exceeds 1.0 (" + probSum + ") for " + recipe.getId());
				}
			}

			// Verify optional /mapping names match our curated names cleanly
			if (liveMappingNamesById != null && !liveMappingNamesById.isEmpty())
			{
				if (recipe.getInputs() != null)
				{
					for (RecipeInput in : recipe.getInputs())
					{
						if (in.isConsumed() && in.getItemId() != 995)
						{
							String mapped = liveMappingNamesById.get(in.getItemId());
							if (mapped == null)
							{
								warnings.add("Unmapped tradeable input ID " + in.getItemId() + " (" + in.getItemName() + ") in " + recipe.getId());
							}
						}
					}
				}
				if (recipe.getOutputs() != null)
				{
					for (RecipeOutput out : recipe.getOutputs())
					{
						if (out.isTradeable())
						{
							String mapped = liveMappingNamesById.get(out.getItemId());
							if (mapped == null)
							{
								errors.add("Missing GE mapping for tradeable output ID " + out.getItemId() + " (" + out.getItemName() + ") in " + recipe.getId());
							}
						}
					}
				}
			}
		}

		return IntegrityReport.builder()
			.totalRecipes(recipes.size())
			.verifiedCount(verified)
			.needsReviewCount(needsReview)
			.unsupportedCount(unsupported)
			.errors(Collections.unmodifiableList(errors))
			.warnings(Collections.unmodifiableList(warnings))
			.build();
	}

	public static Map<Integer, String> toNameMap(Map<Integer, com.craftwise.model.ItemPrice> mapping)
	{
		if (mapping == null || mapping.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<Integer, String> out = new HashMap<>(mapping.size());
		for (Map.Entry<Integer, com.craftwise.model.ItemPrice> e : mapping.entrySet())
		{
			if (e.getValue() != null && e.getValue().getName() != null)
			{
				out.put(e.getKey(), e.getValue().getName());
			}
		}
		return out;
	}
}
