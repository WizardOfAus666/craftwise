package com.craftwise.data;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Immutable definition of a single OSRS Crafting recipe or method variant.
 * Supports multiple inputs, multiple outputs, probability-based/variable outputs,
 * equipment/quest requirements, and explicit data verification status.
 */
@Data
@Builder(toBuilder = true)
public class CraftingRecipe
{
	public enum VerificationStatus
	{
		VERIFIED("Verified"),
		NEEDS_REVIEW("Needs Review"),
		UNSUPPORTED("Unsupported");

		private final String label;

		VerificationStatus(String label)
		{
			this.label = label;
		}

		public String getLabel()
		{
			return label;
		}
	}

	@Data
	@Builder
	public static class RecipeInput
	{
		private final int itemId;
		private final String itemName;
		private final double quantity;
		@Builder.Default
		private final boolean stackable = false;
		@Builder.Default
		private final boolean consumed = true;
	}

	@Data
	@Builder
	public static class RecipeOutput
	{
		private final int itemId;
		private final String itemName;
		private final double quantity;
		/**
		 * Probability of receiving this output per craft action (1.0 for deterministic outputs).
		 * Expected output quantity per action = quantity * probability.
		 */
		@Builder.Default
		private final double probability = 1.0;
		@Builder.Default
		private final boolean tradeable = true;
		@Builder.Default
		private final boolean taxExempt = false;

		public double getExpectedQuantity()
		{
			return quantity * probability;
		}
	}

	private final String id;
	private final String name;
	private final CraftingCategory category;
	private final int requiredLevel;

	@Builder.Default
	private final List<RecipeInput> inputs = Collections.emptyList();
	@Builder.Default
	private final List<RecipeOutput> outputs = Collections.emptyList();

	private final double xpPerAction;
	private final double xpPerOutputItem;

	@Builder.Default
	private final List<String> requiredEquipment = Collections.emptyList();
	@Builder.Default
	private final int toolSlotsRequired = 0;
	@Builder.Default
	private final List<String> questRequirements = Collections.emptyList();
	@Builder.Default
	private final List<String> additionalRequirements = Collections.emptyList();

	private final String specialMechanics;
	private final String wikiReference;
	@Builder.Default
	private final VerificationStatus verificationStatus = VerificationStatus.VERIFIED;
	@Builder.Default
	private final boolean members = true;
	@Builder.Default
	private final boolean boostable = true;
	@Builder.Default
	private final List<String> requiredTools = Collections.emptyList();
	@Builder.Default
	private final List<String> requiredMoulds = Collections.emptyList();
	@Builder.Default
	private final List<String> unlockRequirements = Collections.emptyList();
	@Builder.Default
	private final String processStage = "Single-Step";
	@Builder.Default
	private final List<String> intermediateRecipeIds = Collections.emptyList();

	@Builder.Default
	private final String methodVariant = "Standard";
	private final double estimatedActionsPerHour;
	private final String xpRateBasis;
	@Builder.Default
	private final boolean xpRateVerified = false;

	public List<String> getAllToolsAndMoulds()
	{
		java.util.LinkedHashSet<String> all = new java.util.LinkedHashSet<>();
		if (requiredTools != null)
		{
			all.addAll(requiredTools);
		}
		if (requiredMoulds != null)
		{
			all.addAll(requiredMoulds);
		}
		if (requiredEquipment != null)
		{
			all.addAll(requiredEquipment);
		}
		return Collections.unmodifiableList(new java.util.ArrayList<>(all));
	}

	public RecipeOutput getPrimaryOutput()
	{
		if (outputs == null || outputs.isEmpty())
		{
			return null;
		}
		return outputs.get(0);
	}

	public int getPrimaryItemId()
	{
		RecipeOutput primary = getPrimaryOutput();
		return primary != null ? primary.getItemId() : -1;
	}

	public double getExpectedTotalOutputQuantity()
	{
		if (outputs == null || outputs.isEmpty())
		{
			return 0.0;
		}
		double sum = 0.0;
		for (RecipeOutput output : outputs)
		{
			sum += output.getExpectedQuantity();
		}
		return sum;
	}

	public boolean isMultiInput()
	{
		return inputs != null && inputs.size() > 1;
	}

	public boolean isMultiOutput()
	{
		return outputs != null && outputs.size() > 1;
	}

	public boolean hasVariableOutcome()
	{
		if (outputs == null)
		{
			return false;
		}
		for (RecipeOutput output : outputs)
		{
			if (output.getProbability() < 0.9999)
			{
				return true;
			}
		}
		return false;
	}

	public boolean isTradeableOutput()
	{
		if (outputs == null || outputs.isEmpty())
		{
			return false;
		}
		for (RecipeOutput output : outputs)
		{
			if (output.isTradeable())
			{
				return true;
			}
		}
		return false;
	}

	public double getEstimatedXpPerHour()
	{
		return Math.max(0.0, xpPerAction * estimatedActionsPerHour);
	}

	public boolean hasQuestRequirement()
	{
		return questRequirements != null && !questRequirements.isEmpty();
	}

	public boolean hasUnlockRequirement()
	{
		return unlockRequirements != null && !unlockRequirements.isEmpty();
	}
}
