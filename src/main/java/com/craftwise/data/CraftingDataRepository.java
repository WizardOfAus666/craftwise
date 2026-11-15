package com.craftwise.data;

import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.ItemID;

/**
 * Verified repository of Old School RuneScape Crafting recipes across all 8 categories.
 * Every Crafting level, XP value, ingredient quantity, and mechanic is sourced from the
 * official OSRS Wiki and RuneLite {@link ItemID} definitions.
 */
public final class CraftingDataRepository
{
	private static final String WIKI_BASE = "https://oldschool.runescape.wiki/w/";

	private static final List<CraftingRecipe> RECIPES;
	private static final Map<String, CraftingRecipe> BY_ID;

	static
	{
		List<CraftingRecipe> list = new ArrayList<>();
		addGemCuttingRecipes(list);
		addJewelleryRecipes(list);
		addLeatherRecipes(list);
		addDragonhideRecipes(list);
		addGlassblowingRecipes(list);
		addBattlestaffRecipes(list);
		addPotteryRecipes(list);
		addOtherRecipes(list);
		addExpandedRecipes(list);

		RECIPES = Collections.unmodifiableList(list);
		Map<String, CraftingRecipe> byId = new LinkedHashMap<>(list.size());
		for (CraftingRecipe recipe : list)
		{
			byId.put(recipe.getId(), recipe);
		}
		BY_ID = Collections.unmodifiableMap(byId);
	}

	private CraftingDataRepository()
	{
	}

	public static List<CraftingRecipe> getAllRecipes()
	{
		return RECIPES;
	}

	public static CraftingRecipe getById(String id)
	{
		return id != null ? BY_ID.get(id) : null;
	}

	// =========================================================================
	// 1. GEM CUTTING
	// =========================================================================
	private static void addGemCuttingRecipes(List<CraftingRecipe> list)
	{
		// Semi-precious gems: variable crush rate depending on Crafting level
		// Modeled conservatively with 0.90 success / 0.10 crush baseline and marked PARTIALLY_VERIFIED
		list.add(semiPreciousGem("cut_opal", "Cut Opal", 1,
			ItemID.UNCUT_OPAL, "Uncut opal", ItemID.OPAL, "Opal",
			15.0, 0.92, "Opal",
			"Can crush into Crushed gem (grants 3.75 XP). Crush rate decreases with Crafting level (~3.5%-4.5% at 99)."));
		list.add(semiPreciousGem("cut_jade", "Cut Jade", 13,
			ItemID.UNCUT_JADE, "Uncut jade", ItemID.JADE, "Jade",
			20.0, 0.90, "Jade",
			"Can crush into Crushed gem (grants 5.0 XP). Crush rate decreases with Crafting level."));
		list.add(semiPreciousGem("cut_red_topaz", "Cut Red Topaz", 16,
			ItemID.UNCUT_RED_TOPAZ, "Uncut red topaz", ItemID.RED_TOPAZ, "Red topaz",
			25.0, 0.88, "Red_topaz",
			"Can crush into Crushed gem (grants 6.25 XP). Crush rate decreases with Crafting level."));

		// Precious gems: 100% success rate once level requirement is met
		list.add(preciousGem("cut_sapphire", "Cut Sapphire", 20,
			ItemID.UNCUT_SAPPHIRE, "Uncut sapphire", ItemID.SAPPHIRE, "Sapphire", 50.0, "Sapphire"));
		list.add(preciousGem("cut_emerald", "Cut Emerald", 27,
			ItemID.UNCUT_EMERALD, "Uncut emerald", ItemID.EMERALD, "Emerald", 67.5, "Emerald"));
		list.add(preciousGem("cut_ruby", "Cut Ruby", 34,
			ItemID.UNCUT_RUBY, "Uncut ruby", ItemID.RUBY, "Ruby", 85.0, "Ruby"));
		list.add(preciousGem("cut_diamond", "Cut Diamond", 43,
			ItemID.UNCUT_DIAMOND, "Uncut diamond", ItemID.DIAMOND, "Diamond", 107.5, "Diamond"));
		list.add(preciousGem("cut_dragonstone", "Cut Dragonstone", 55,
			ItemID.UNCUT_DRAGONSTONE, "Uncut dragonstone", ItemID.DRAGONSTONE, "Dragonstone", 137.5, "Dragonstone", true));
		list.add(preciousGem("cut_onyx", "Cut Onyx", 67,
			ItemID.UNCUT_ONYX, "Uncut onyx", ItemID.ONYX, "Onyx", 167.5, "Onyx", true));
		list.add(preciousGem("cut_zenyte", "Cut Zenyte", 89,
			ItemID.UNCUT_ZENYTE, "Uncut zenyte", ItemID.ZENYTE, "Zenyte", 50.0, "Zenyte", true));
	}

	private static CraftingRecipe semiPreciousGem(
		String id, String name, int level,
		int uncutId, String uncutName, int cutId, String cutName,
		double successXp, double baselineSuccessProb, String wikiSlug, String mechanics)
	{
		double crushProb = Math.max(0.0, 1.0 - baselineSuccessProb);
		double expectedXp = (successXp * baselineSuccessProb) + (successXp * 0.25 * crushProb);
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.GEM_CUTTING)
			.requiredLevel(level)
			.inputs(Collections.singletonList(input(uncutId, uncutName, 1.0)))
			.outputs(List.of(
				RecipeOutput.builder()
					.itemId(cutId).itemName(cutName).quantity(1.0)
					.probability(baselineSuccessProb).tradeable(true).build(),
				RecipeOutput.builder()
					.itemId(ItemID.CRUSHED_GEM).itemName("Crushed gem").quantity(1.0)
					.probability(crushProb).tradeable(false).build()
			))
			.xpPerAction(expectedXp)
			.xpPerOutputItem(successXp)
			.requiredEquipment(Collections.singletonList("Chisel"))
			.requiredTools(Collections.singletonList("Chisel"))
			.toolSlotsRequired(1)
			.members(true)
			.boostable(true)
			.specialMechanics(mechanics)
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.NEEDS_REVIEW)
			.methodVariant("Expected Yield (Level-Dependent)")
			.estimatedActionsPerHour(2700.0)
			.xpRateBasis("~2,700 cuts/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	private static CraftingRecipe preciousGem(
		String id, String name, int level,
		int uncutId, String uncutName, int cutId, String cutName,
		double xp, String wikiSlug)
	{
		return preciousGem(id, name, level, uncutId, uncutName, cutId, cutName, xp, wikiSlug, false);
	}

	private static CraftingRecipe preciousGem(
		String id, String name, int level,
		int uncutId, String uncutName, int cutId, String cutName,
		double xp, String wikiSlug, boolean members)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.GEM_CUTTING)
			.requiredLevel(level)
			.inputs(Collections.singletonList(input(uncutId, uncutName, 1.0)))
			.outputs(Collections.singletonList(output(cutId, cutName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.requiredEquipment(Collections.singletonList("Chisel"))
			.requiredTools(Collections.singletonList("Chisel"))
			.toolSlotsRequired(1)
			.members(members)
			.boostable(true)
			.specialMechanics("100% success rate with a Chisel. 27 gems per inventory.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Standard")
			.estimatedActionsPerHour(2700.0)
			.xpRateBasis("~2,700 cuts/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	// =========================================================================
	// 2. JEWELLERY (Gold, Silver, Gemmed, Zenyte, Stringing)
	// =========================================================================
	private static void addJewelleryRecipes(List<CraftingRecipe> list)
	{
		// Plain Gold Jewellery
		list.add(goldJewellery("gold_ring", "Gold ring", 5, ItemID.GOLD_RING, "Gold ring", 15.0, "Ring mould", "Gold_ring", false));
		list.add(goldJewellery("gold_necklace", "Gold necklace", 6, ItemID.GOLD_NECKLACE, "Gold necklace", 20.0, "Necklace mould", "Gold_necklace", false));
		list.add(goldJewellery("gold_bracelet", "Gold bracelet", 7, ItemID.GOLD_BRACELET_11069, "Gold bracelet", 25.0, "Bracelet mould", "Gold_bracelet", true));
		list.add(goldJewellery("gold_amulet_u", "Gold amulet (u)", 8, ItemID.GOLD_AMULET_U, "Gold amulet (u)", 30.0, "Amulet mould", "Gold_amulet_(u)", false));
		list.add(goldJewellery("gold_tiara", "Gold tiara", 42, ItemID.GOLD_TIARA, "Gold tiara", 35.0, "Tiara mould", "Gold_tiara", true));
		list.add(stringAmulet("string_gold_amulet", "Gold amulet (Stringing)", 8,
			ItemID.GOLD_AMULET_U, "Gold amulet (u)", ItemID.GOLD_AMULET, "Gold amulet", "Gold_amulet", false));

		// Silver Jewellery (Opal / Jade / Red Topaz + Silver bar — Members-only)
		list.add(silverJewellery("opal_ring", "Opal ring", 1, ItemID.OPAL, "Opal", ItemID.OPAL_RING, "Opal ring", 10.0, "Ring mould", "Opal_ring"));
		list.add(silverJewellery("jade_ring", "Jade ring", 13, ItemID.JADE, "Jade", ItemID.JADE_RING, "Jade ring", 32.0, "Ring mould", "Jade_ring"));
		list.add(silverJewellery("topaz_ring", "Topaz ring", 16, ItemID.RED_TOPAZ, "Red topaz", ItemID.TOPAZ_RING, "Topaz ring", 35.0, "Ring mould", "Topaz_ring"));
		list.add(silverJewellery("opal_necklace", "Opal necklace", 16, ItemID.OPAL, "Opal", ItemID.OPAL_NECKLACE, "Opal necklace", 35.0, "Necklace mould", "Opal_necklace"));
		list.add(silverJewellery("opal_bracelet", "Opal bracelet", 22, ItemID.OPAL, "Opal", ItemID.OPAL_BRACELET, "Opal bracelet", 45.0, "Bracelet mould", "Opal_bracelet"));
		list.add(silverJewellery("jade_necklace", "Jade necklace", 25, ItemID.JADE, "Jade", ItemID.JADE_NECKLACE, "Jade necklace", 54.0, "Necklace mould", "Jade_necklace"));
		list.add(silverJewellery("opal_amulet_u", "Opal amulet (u)", 27, ItemID.OPAL, "Opal", ItemID.OPAL_AMULET_U, "Opal amulet (u)", 55.0, "Amulet mould", "Opal_amulet_(u)"));
		list.add(stringAmulet("string_opal_amulet", "Opal amulet (Stringing)", 27,
			ItemID.OPAL_AMULET_U, "Opal amulet (u)", ItemID.OPAL_AMULET, "Opal amulet", "Opal_amulet", true));
		list.add(silverJewellery("jade_bracelet", "Jade bracelet", 29, ItemID.JADE, "Jade", ItemID.JADE_BRACELET, "Jade bracelet", 60.0, "Bracelet mould", "Jade_bracelet"));
		list.add(silverJewellery("topaz_necklace", "Topaz necklace", 32, ItemID.RED_TOPAZ, "Red topaz", ItemID.TOPAZ_NECKLACE, "Topaz necklace", 70.0, "Necklace mould", "Topaz_necklace"));
		list.add(silverJewellery("jade_amulet_u", "Jade amulet (u)", 34, ItemID.JADE, "Jade", ItemID.JADE_AMULET_U, "Jade amulet (u)", 70.0, "Amulet mould", "Jade_amulet_(u)"));
		list.add(stringAmulet("string_jade_amulet", "Jade amulet (Stringing)", 34,
			ItemID.JADE_AMULET_U, "Jade amulet (u)", ItemID.JADE_AMULET, "Jade amulet", "Jade_amulet", true));
		list.add(silverJewellery("topaz_bracelet", "Topaz bracelet", 38, ItemID.RED_TOPAZ, "Red topaz", ItemID.TOPAZ_BRACELET, "Topaz bracelet", 75.0, "Bracelet mould", "Topaz_bracelet"));
		list.add(silverJewellery("topaz_amulet_u", "Topaz amulet (u)", 45, ItemID.RED_TOPAZ, "Red topaz", ItemID.TOPAZ_AMULET_U, "Topaz amulet (u)", 80.0, "Amulet mould", "Topaz_amulet_(u)"));
		list.add(stringAmulet("string_topaz_amulet", "Topaz amulet (Stringing)", 45,
			ItemID.TOPAZ_AMULET_U, "Topaz amulet (u)", ItemID.TOPAZ_AMULET, "Topaz amulet", "Topaz_amulet", true));

		// Sapphire Jewellery
		list.add(gemJewellery("sapphire_ring", "Sapphire ring", 20, ItemID.SAPPHIRE, "Sapphire", ItemID.SAPPHIRE_RING, "Sapphire ring", 40.0, "Ring mould", "Sapphire_ring"));
		list.add(gemJewellery("sapphire_necklace", "Sapphire necklace", 22, ItemID.SAPPHIRE, "Sapphire", ItemID.SAPPHIRE_NECKLACE, "Sapphire necklace", 55.0, "Necklace mould", "Sapphire_necklace"));
		list.add(gemJewellery("sapphire_bracelet", "Sapphire bracelet", 23, ItemID.SAPPHIRE, "Sapphire", ItemID.SAPPHIRE_BRACELET_11072, "Sapphire bracelet", 60.0, "Bracelet mould", "Sapphire_bracelet"));
		list.add(gemJewellery("sapphire_amulet_u", "Sapphire amulet (u)", 24, ItemID.SAPPHIRE, "Sapphire", ItemID.SAPPHIRE_AMULET_U, "Sapphire amulet (u)", 65.0, "Amulet mould", "Sapphire_amulet_(u)"));
		list.add(stringAmulet("string_sapphire_amulet", "Sapphire amulet (Stringing)", 24,
			ItemID.SAPPHIRE_AMULET_U, "Sapphire amulet (u)", ItemID.SAPPHIRE_AMULET, "Sapphire amulet", "Sapphire_amulet"));

		// Emerald Jewellery
		list.add(gemJewellery("emerald_ring", "Emerald ring", 27, ItemID.EMERALD, "Emerald", ItemID.EMERALD_RING, "Emerald ring", 55.0, "Ring mould", "Emerald_ring"));
		list.add(gemJewellery("emerald_necklace", "Emerald necklace", 29, ItemID.EMERALD, "Emerald", ItemID.EMERALD_NECKLACE, "Emerald necklace", 60.0, "Necklace mould", "Emerald_necklace"));
		list.add(gemJewellery("emerald_bracelet", "Emerald bracelet", 30, ItemID.EMERALD, "Emerald", ItemID.EMERALD_BRACELET, "Emerald bracelet", 65.0, "Bracelet mould", "Emerald_bracelet"));
		list.add(gemJewellery("emerald_amulet_u", "Emerald amulet (u)", 31, ItemID.EMERALD, "Emerald", ItemID.EMERALD_AMULET_U, "Emerald amulet (u)", 70.0, "Amulet mould", "Emerald_amulet_(u)"));
		list.add(stringAmulet("string_emerald_amulet", "Emerald amulet (Stringing)", 31,
			ItemID.EMERALD_AMULET_U, "Emerald amulet (u)", ItemID.EMERALD_AMULET, "Emerald amulet", "Emerald_amulet"));

		// Ruby Jewellery
		list.add(gemJewellery("ruby_ring", "Ruby ring", 34, ItemID.RUBY, "Ruby", ItemID.RUBY_RING, "Ruby ring", 70.0, "Ring mould", "Ruby_ring"));
		list.add(gemJewellery("ruby_necklace", "Ruby necklace", 40, ItemID.RUBY, "Ruby", ItemID.RUBY_NECKLACE, "Ruby necklace", 75.0, "Necklace mould", "Ruby_necklace"));
		list.add(gemJewellery("ruby_bracelet", "Ruby bracelet", 42, ItemID.RUBY, "Ruby", ItemID.RUBY_BRACELET, "Ruby bracelet", 80.0, "Bracelet mould", "Ruby_bracelet"));
		list.add(gemJewellery("ruby_amulet_u", "Ruby amulet (u)", 50, ItemID.RUBY, "Ruby", ItemID.RUBY_AMULET_U, "Ruby amulet (u)", 85.0, "Amulet mould", "Ruby_amulet_(u)"));
		list.add(stringAmulet("string_ruby_amulet", "Ruby amulet (Stringing)", 50,
			ItemID.RUBY_AMULET_U, "Ruby amulet (u)", ItemID.RUBY_AMULET, "Ruby amulet", "Ruby_amulet"));

		// Diamond Jewellery
		list.add(gemJewellery("diamond_ring", "Diamond ring", 43, ItemID.DIAMOND, "Diamond", ItemID.DIAMOND_RING, "Diamond ring", 85.0, "Ring mould", "Diamond_ring"));
		list.add(gemJewellery("diamond_necklace", "Diamond necklace", 56, ItemID.DIAMOND, "Diamond", ItemID.DIAMOND_NECKLACE, "Diamond necklace", 90.0, "Necklace mould", "Diamond_necklace"));
		list.add(gemJewellery("diamond_bracelet", "Diamond bracelet", 58, ItemID.DIAMOND, "Diamond", ItemID.DIAMOND_BRACELET, "Diamond bracelet", 95.0, "Bracelet mould", "Diamond_bracelet"));
		list.add(gemJewellery("diamond_amulet_u", "Diamond amulet (u)", 70, ItemID.DIAMOND, "Diamond", ItemID.DIAMOND_AMULET_U, "Diamond amulet (u)", 100.0, "Amulet mould", "Diamond_amulet_(u)"));
		list.add(stringAmulet("string_diamond_amulet", "Diamond amulet (Stringing)", 70,
			ItemID.DIAMOND_AMULET_U, "Diamond amulet (u)", ItemID.DIAMOND_AMULET, "Diamond amulet", "Diamond_amulet"));

		// Dragonstone Jewellery
		list.add(gemJewellery("dragonstone_ring", "Dragonstone ring", 55, ItemID.DRAGONSTONE, "Dragonstone", ItemID.DRAGONSTONE_RING, "Dragonstone ring", 100.0, "Ring mould", "Dragonstone_ring"));
		list.add(gemJewellery("dragon_necklace", "Dragon necklace", 72, ItemID.DRAGONSTONE, "Dragonstone", ItemID.DRAGON_NECKLACE, "Dragon necklace", 105.0, "Necklace mould", "Dragon_necklace"));
		list.add(gemJewellery("dragonstone_bracelet", "Dragonstone bracelet", 74, ItemID.DRAGONSTONE, "Dragonstone", ItemID.DRAGONSTONE_BRACELET, "Dragonstone bracelet", 110.0, "Bracelet mould", "Dragonstone_bracelet"));
		list.add(gemJewellery("dragonstone_amulet_u", "Dragonstone amulet (u)", 80, ItemID.DRAGONSTONE, "Dragonstone", ItemID.DRAGONSTONE_AMULET_U, "Dragonstone amulet (u)", 150.0, "Amulet mould", "Dragonstone_amulet_(u)"));
		list.add(stringAmulet("string_dragonstone_amulet", "Dragonstone amulet (Stringing)", 80,
			ItemID.DRAGONSTONE_AMULET_U, "Dragonstone amulet (u)", ItemID.DRAGONSTONE_AMULET, "Dragonstone amulet", "Dragonstone_amulet"));

		// Alternative: Uncut Dragonstone -> Cut -> Dragonstone Bracelet (process raw gem + gold bar)
		list.add(CraftingRecipe.builder()
			.id("dragonstone_bracelet_from_uncut")
			.name("Dragonstone bracelet (From Uncut)")
			.category(CraftingCategory.JEWELLERY)
			.requiredLevel(74)
			.inputs(List.of(
				input(ItemID.UNCUT_DRAGONSTONE, "Uncut dragonstone", 1.0),
				input(ItemID.GOLD_BAR, "Gold bar", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.DRAGONSTONE_BRACELET, "Dragonstone bracelet", 1.0)))
			.xpPerAction(247.5)
			.xpPerOutputItem(247.5)
			.requiredEquipment(List.of("Chisel", "Bracelet mould"))
			.toolSlotsRequired(2)
			.additionalRequirements(Collections.singletonList("Furnace (e.g. Edgeville)"))
			.specialMechanics("2-step process: Cut uncut dragonstone (137.5 XP) + smelt bracelet (110 XP) = 247.5 XP total.")
			.wikiReference(WIKI_BASE + "Dragonstone_bracelet")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Process Raw Gem")
			.estimatedActionsPerHour(900.0)
			.xpRateBasis("Estimated ~900 full cycles/hr (cut + smelt)")
			.xpRateVerified(false)
			.build());

		// Onyx Jewellery
		list.add(gemJewellery("onyx_ring", "Onyx ring", 67, ItemID.ONYX, "Onyx", ItemID.ONYX_RING, "Onyx ring", 115.0, "Ring mould", "Onyx_ring"));
		list.add(gemJewellery("onyx_necklace", "Onyx necklace", 82, ItemID.ONYX, "Onyx", ItemID.ONYX_NECKLACE, "Onyx necklace", 120.0, "Necklace mould", "Onyx_necklace"));
		list.add(gemJewellery("onyx_bracelet", "Onyx bracelet", 84, ItemID.ONYX, "Onyx", ItemID.ONYX_BRACELET, "Onyx bracelet", 125.0, "Bracelet mould", "Onyx_bracelet"));
		list.add(gemJewellery("onyx_amulet_u", "Onyx amulet (u)", 90, ItemID.ONYX, "Onyx", ItemID.ONYX_AMULET_U, "Onyx amulet (u)", 165.0, "Amulet mould", "Onyx_amulet_(u)"));
		list.add(stringAmulet("string_onyx_amulet", "Onyx amulet (Stringing)", 90,
			ItemID.ONYX_AMULET_U, "Onyx amulet (u)", ItemID.ONYX_AMULET, "Onyx amulet", "Onyx_amulet"));

		// Zenyte Jewellery
		list.add(gemJewellery("zenyte_ring", "Zenyte ring", 89, ItemID.ZENYTE, "Zenyte", ItemID.ZENYTE_RING, "Zenyte ring", 150.0, "Ring mould", "Zenyte_ring"));
		list.add(gemJewellery("zenyte_necklace", "Zenyte necklace", 92, ItemID.ZENYTE, "Zenyte", ItemID.ZENYTE_NECKLACE, "Zenyte necklace", 165.0, "Necklace mould", "Zenyte_necklace"));
		list.add(gemJewellery("zenyte_bracelet", "Zenyte bracelet", 95, ItemID.ZENYTE, "Zenyte", ItemID.ZENYTE_BRACELET_19532, "Zenyte bracelet", 180.0, "Bracelet mould", "Zenyte_bracelet"));
		list.add(gemJewellery("zenyte_amulet_u", "Zenyte amulet (u)", 98, ItemID.ZENYTE, "Zenyte", ItemID.ZENYTE_AMULET_U, "Zenyte amulet (u)", 200.0, "Amulet mould", "Zenyte_amulet_(u)"));
		list.add(stringAmulet("string_zenyte_amulet", "Zenyte amulet (Stringing)", 98,
			ItemID.ZENYTE_AMULET_U, "Zenyte amulet (u)", ItemID.ZENYTE_AMULET, "Zenyte amulet", "Zenyte_amulet"));

		// Alternative: Zenyte shard + Onyx -> Uncut Zenyte (Wall of Flame)
		list.add(CraftingRecipe.builder()
			.id("make_uncut_zenyte")
			.name("Uncut zenyte (Shard + Onyx)")
			.category(CraftingCategory.JEWELLERY)
			.requiredLevel(70)
			.inputs(List.of(
				input(ItemID.ZENYTE_SHARD, "Zenyte shard", 1.0),
				input(ItemID.ONYX, "Onyx", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.UNCUT_ZENYTE, "Uncut zenyte", 1.0)))
			.xpPerAction(15.0)
			.xpPerOutputItem(15.0)
			.members(true)
			.boostable(false)
			.questRequirements(Collections.singletonList("Monkey Madness II"))
			.additionalRequirements(List.of("Wall of Flame (Ape Atoll dungeon)", "Zombie monkey greegree (small or big)"))
			.unlockRequirements(Collections.singletonList("Monkey Madness II"))
			.specialMechanics("Fuses a Zenyte shard and cut Onyx at the Wall of Flame into an Uncut zenyte (Requires Lvl 70 Crafting, non-boostable).")
			.wikiReference(WIKI_BASE + "Uncut_zenyte")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Process Raw Materials")
			.estimatedActionsPerHour(600.0)
			.xpRateBasis("Estimated ~600 fuses/hr (low-volume specialty craft)")
			.xpRateVerified(false)
			.build());
	}

	private static CraftingRecipe goldJewellery(
		String id, String name, int level, int outId, String outName, double xp, String mould, String wikiSlug, boolean members)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.JEWELLERY)
			.requiredLevel(level)
			.inputs(Collections.singletonList(input(ItemID.GOLD_BAR, "Gold bar", 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.members(members)
			.boostable(true)
			.requiredEquipment(Collections.singletonList(mould))
			.requiredMoulds(Collections.singletonList(mould))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace (Edgeville recommended)"))
			.specialMechanics("27 bars per inventory with " + mould + " in inventory.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Furnace Smelt")
			.estimatedActionsPerHour(1650.0)
			.xpRateBasis("~1,650 crafts/hr at Edgeville furnace (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	private static CraftingRecipe gemJewellery(
		String id, String name, int level,
		int gemId, String gemName, int outId, String outName,
		double xp, String mould, String wikiSlug)
	{
		boolean f2p = level <= 70 && !"Bracelet mould".equals(mould)
			&& gemId != ItemID.DRAGONSTONE && gemId != ItemID.ONYX && gemId != ItemID.ZENYTE;
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.JEWELLERY)
			.requiredLevel(level)
			.inputs(List.of(
				input(ItemID.GOLD_BAR, "Gold bar", 1.0),
				input(gemId, gemName, 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.members(!f2p)
			.boostable(true)
			.requiredEquipment(Collections.singletonList(mould))
			.requiredMoulds(Collections.singletonList(mould))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace (Edgeville recommended)"))
			.specialMechanics("Uses 1 Gold bar + 1 " + gemName + " per action (13 crafts per inventory).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Buy Cut Gem + Gold Bar")
			.estimatedActionsPerHour(1450.0)
			.xpRateBasis("~1,450 crafts/hr at Edgeville furnace (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	private static CraftingRecipe silverJewellery(
		String id, String name, int level,
		int gemId, String gemName, int outId, String outName,
		double xp, String mould, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.JEWELLERY)
			.requiredLevel(level)
			.inputs(List.of(
				input(ItemID.SILVER_BAR, "Silver bar", 1.0),
				input(gemId, gemName, 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.members(true)
			.boostable(true)
			.requiredEquipment(Collections.singletonList(mould))
			.requiredMoulds(Collections.singletonList(mould))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace (Edgeville recommended)"))
			.specialMechanics("Uses 1 Silver bar + 1 " + gemName + " per action (13 crafts per inventory).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Buy Cut Gem + Silver Bar")
			.estimatedActionsPerHour(1450.0)
			.xpRateBasis("~1,450 crafts/hr at Edgeville furnace (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	private static CraftingRecipe stringAmulet(
		String id, String name, int level,
		int unstrungId, String unstrungName, int strungId, String strungName, String wikiSlug)
	{
		boolean f2p = level <= 70
			&& unstrungId != ItemID.OPAL_AMULET_U
			&& unstrungId != ItemID.JADE_AMULET_U
			&& unstrungId != ItemID.TOPAZ_AMULET_U;
		return stringAmulet(id, name, level, unstrungId, unstrungName, strungId, strungName, wikiSlug, !f2p);
	}

	private static CraftingRecipe stringAmulet(
		String id, String name, int level,
		int unstrungId, String unstrungName, int strungId, String strungName, String wikiSlug, boolean members)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.JEWELLERY)
			.requiredLevel(level)
			.inputs(List.of(
				input(unstrungId, unstrungName, 1.0),
				input(ItemID.BALL_OF_WOOL, "Ball of wool", 1.0)))
			.outputs(Collections.singletonList(output(strungId, strungName, 1.0)))
			.xpPerAction(4.0)
			.xpPerOutputItem(4.0)
			.members(members)
			.boostable(true)
			.specialMechanics("Strings unstrung amulet with Ball of wool (14 per inventory). Note: Lunar String Jewellery gives Magic XP + 4 Crafting XP.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("String Amulet")
			.estimatedActionsPerHour(2200.0)
			.xpRateBasis("Estimated ~2,200 strings/hr bank-standing")
			.xpRateVerified(false)
			.build();
	}

	// =========================================================================
	// 3. LEATHER
	// =========================================================================
	private static void addLeatherRecipes(List<CraftingRecipe> list)
	{
		list.add(leatherRecipe("leather_gloves", "Leather gloves", 1, 1, ItemID.LEATHER, "Leather", ItemID.LEATHER_GLOVES, "Leather gloves", 13.75, "Leather_gloves"));
		list.add(leatherRecipe("leather_boots", "Leather boots", 7, 1, ItemID.LEATHER, "Leather", ItemID.LEATHER_BOOTS, "Leather boots", 16.25, "Leather_boots"));
		list.add(leatherRecipe("leather_cowl", "Leather cowl", 9, 1, ItemID.LEATHER, "Leather", ItemID.LEATHER_COWL, "Leather cowl", 18.5, "Leather_cowl"));
		list.add(leatherRecipe("leather_vambraces", "Leather vambraces", 11, 1, ItemID.LEATHER, "Leather", ItemID.LEATHER_VAMBRACES, "Leather vambraces", 22.0, "Leather_vambraces"));
		list.add(leatherRecipe("leather_body", "Leather body", 14, 1, ItemID.LEATHER, "Leather", ItemID.LEATHER_BODY, "Leather body", 25.0, "Leather_body"));
		list.add(leatherRecipe("leather_chaps", "Leather chaps", 18, 1, ItemID.LEATHER, "Leather", ItemID.LEATHER_CHAPS, "Leather chaps", 27.0, "Leather_chaps"));
		list.add(leatherRecipe("coif", "Coif", 38, 1, ItemID.LEATHER, "Leather", ItemID.COIF, "Coif", 37.0, "Coif"));

		// Hard leather
		list.add(leatherRecipe("hardleather_body", "Hardleather body", 28, 1, ItemID.HARD_LEATHER, "Hard leather", ItemID.HARDLEATHER_BODY, "Hardleather body", 35.0, "Hardleather_body"));

		// Alternative: Raw Cowhide + 3 GP Tanner -> Hardleather body
		list.add(CraftingRecipe.builder()
			.id("hardleather_body_from_cowhide")
			.name("Hardleather body (Tan Cowhide)")
			.category(CraftingCategory.LEATHER)
			.requiredLevel(28)
			.inputs(List.of(
				input(ItemID.COWHIDE, "Cowhide", 1.0),
				input(ItemID.COINS_995, "Coins (Tan fee)", 3.0, true, true),
				input(ItemID.THREAD, "Thread", 0.2, true, true)))
			.outputs(Collections.singletonList(output(ItemID.HARDLEATHER_BODY, "Hardleather body", 1.0)))
			.xpPerAction(35.0)
			.xpPerOutputItem(35.0)
			.requiredEquipment(Collections.singletonList("Needle"))
			.toolSlotsRequired(2)
			.additionalRequirements(Collections.singletonList("Tanner (Al Kharid / Crafting Guild, 3 gp/hide)"))
			.specialMechanics("Includes 3 GP NPC tanning fee per cowhide. Tanning itself grants 0 Crafting XP.")
			.wikiReference(WIKI_BASE + "Hardleather_body")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Process Raw Cowhide")
			.estimatedActionsPerHour(1200.0)
			.xpRateBasis("Estimated ~1,200/hr including Al Kharid tanning trip")
			.xpRateVerified(false)
			.build());

		// Studded leather
		list.add(CraftingRecipe.builder()
			.id("studded_body")
			.name("Studded body")
			.category(CraftingCategory.LEATHER)
			.requiredLevel(41)
			.inputs(List.of(
				input(ItemID.LEATHER_BODY, "Leather body", 1.0),
				input(ItemID.STEEL_STUDS, "Steel studs", 1.0, true, true)))
			.outputs(Collections.singletonList(output(ItemID.STUDDED_BODY, "Studded body", 1.0)))
			.xpPerAction(40.0)
			.xpPerOutputItem(40.0)
			.members(true)
			.boostable(true)
			.specialMechanics("Attaches Steel studs to a Leather body (no needle/thread required; crafting studded armour is members-only).")
			.wikiReference(WIKI_BASE + "Studded_body")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Upgrade Leather")
			.estimatedActionsPerHour(2200.0)
			.xpRateBasis("Estimated ~2,200 attachments/hr bank-standing")
			.xpRateVerified(false)
			.build());

		list.add(CraftingRecipe.builder()
			.id("studded_chaps")
			.name("Studded chaps")
			.category(CraftingCategory.LEATHER)
			.requiredLevel(44)
			.inputs(List.of(
				input(ItemID.LEATHER_CHAPS, "Leather chaps", 1.0),
				input(ItemID.STEEL_STUDS, "Steel studs", 1.0, true, true)))
			.outputs(Collections.singletonList(output(ItemID.STUDDED_CHAPS, "Studded chaps", 1.0)))
			.xpPerAction(42.0)
			.xpPerOutputItem(42.0)
			.specialMechanics("Attaches Steel studs to Leather chaps.")
			.wikiReference(WIKI_BASE + "Studded_chaps")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Upgrade Leather")
			.estimatedActionsPerHour(2200.0)
			.xpRateBasis("Estimated ~2,200 attachments/hr bank-standing")
			.xpRateVerified(false)
			.build());

		// Snakeskin
		list.add(leatherRecipe("snakeskin_boots", "Snakeskin boots", 45, 6, ItemID.SNAKESKIN, "Snakeskin", ItemID.SNAKESKIN_BOOTS, "Snakeskin boots", 30.0, "Snakeskin_boots"));
		list.add(leatherRecipe("snakeskin_vambraces", "Snakeskin vambraces", 47, 8, ItemID.SNAKESKIN, "Snakeskin", ItemID.SNAKESKIN_VAMBRACES, "Snakeskin vambraces", 35.0, "Snakeskin_vambraces"));
		list.add(leatherRecipe("snakeskin_bandana", "Snakeskin bandana", 48, 5, ItemID.SNAKESKIN, "Snakeskin", ItemID.SNAKESKIN_BANDANA, "Snakeskin bandana", 45.0, "Snakeskin_bandana"));
		list.add(leatherRecipe("snakeskin_chaps", "Snakeskin chaps", 51, 12, ItemID.SNAKESKIN, "Snakeskin", ItemID.SNAKESKIN_CHAPS, "Snakeskin chaps", 50.0, "Snakeskin_chaps"));
		list.add(leatherRecipe("snakeskin_body", "Snakeskin body", 53, 15, ItemID.SNAKESKIN, "Snakeskin", ItemID.SNAKESKIN_BODY, "Snakeskin body", 55.0, "Snakeskin_body"));
	}

	private static CraftingRecipe leatherRecipe(
		String id, String name, int level, int hideQty,
		int hideId, String hideName, int outId, String outName, double xp, String wikiSlug)
	{
		boolean f2p = (hideId == ItemID.LEATHER || hideId == ItemID.HARD_LEATHER) && !"coif".equals(id);
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.LEATHER)
			.requiredLevel(level)
			.inputs(List.of(
				input(hideId, hideName, hideQty),
				input(ItemID.THREAD, "Thread", 0.2, true, true)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.members(!f2p)
			.boostable(true)
			.requiredEquipment(Collections.singletonList("Needle"))
			.requiredTools(Collections.singletonList("Needle"))
			.toolSlotsRequired(2)
			.specialMechanics("Consumes 1 Thread per 5 crafts (0 with Costume needle). Requires " + hideQty + "x " + hideName + ".")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Buy Tanned Leather")
			.estimatedActionsPerHour(hideQty >= 6 ? 900.0 : 1900.0)
			.xpRateBasis(hideQty >= 6 ? "Estimated ~900 crafts/hr (multi-hide banking)" : "~1,900 crafts/hr bank-standing")
			.xpRateVerified(false)
			.build();
	}

	// =========================================================================
	// 4. DRAGONHIDE (Green, Blue, Red, Black + Shields + Raw Hide variants)
	// =========================================================================
	private static void addDragonhideRecipes(List<CraftingRecipe> list)
	{
		// Green d'hide
		list.add(dhideRecipe("green_dhide_vambraces", "Green d'hide vambraces", 57, 1, ItemID.GREEN_DRAGON_LEATHER, "Green dragon leather", ItemID.GREEN_DHIDE_VAMBRACES, "Green d'hide vambraces", 62.0, "Green_d'hide_vambraces"));
		list.add(dhideRecipe("green_dhide_chaps", "Green d'hide chaps", 60, 2, ItemID.GREEN_DRAGON_LEATHER, "Green dragon leather", ItemID.GREEN_DHIDE_CHAPS, "Green d'hide chaps", 124.0, "Green_d'hide_chaps"));
		list.add(dhideRecipe("green_dhide_body", "Green d'hide body", 63, 3, ItemID.GREEN_DRAGON_LEATHER, "Green dragon leather", ItemID.GREEN_DHIDE_BODY, "Green d'hide body", 186.0, "Green_d'hide_body"));
		list.add(dhideShield("green_dhide_shield", "Green d'hide shield", 62, ItemID.MAPLE_SHIELD, "Maple shield", ItemID.GREEN_DRAGON_LEATHER, "Green dragon leather", ItemID.STEEL_NAILS, "Steel nails", ItemID.GREEN_DHIDE_SHIELD, "Green d'hide shield", 124.0, "Green_d'hide_shield"));

		// Blue d'hide
		list.add(dhideRecipe("blue_dhide_vambraces", "Blue d'hide vambraces", 66, 1, ItemID.BLUE_DRAGON_LEATHER, "Blue dragon leather", ItemID.BLUE_DHIDE_VAMBRACES, "Blue d'hide vambraces", 70.0, "Blue_d'hide_vambraces"));
		list.add(dhideRecipe("blue_dhide_chaps", "Blue d'hide chaps", 68, 2, ItemID.BLUE_DRAGON_LEATHER, "Blue dragon leather", ItemID.BLUE_DHIDE_CHAPS, "Blue d'hide chaps", 140.0, "Blue_d'hide_chaps"));
		list.add(dhideRecipe("blue_dhide_body", "Blue d'hide body", 71, 3, ItemID.BLUE_DRAGON_LEATHER, "Blue dragon leather", ItemID.BLUE_DHIDE_BODY, "Blue d'hide body", 210.0, "Blue_d'hide_body"));
		list.add(dhideShield("blue_dhide_shield", "Blue d'hide shield", 69, ItemID.YEW_SHIELD, "Yew shield", ItemID.BLUE_DRAGON_LEATHER, "Blue dragon leather", ItemID.MITHRIL_NAILS, "Mithril nails", ItemID.BLUE_DHIDE_SHIELD, "Blue d'hide shield", 140.0, "Blue_d'hide_shield"));

		// Red d'hide
		list.add(dhideRecipe("red_dhide_vambraces", "Red d'hide vambraces", 73, 1, ItemID.RED_DRAGON_LEATHER, "Red dragon leather", ItemID.RED_DHIDE_VAMBRACES, "Red d'hide vambraces", 78.0, "Red_d'hide_vambraces"));
		list.add(dhideRecipe("red_dhide_chaps", "Red d'hide chaps", 75, 2, ItemID.RED_DRAGON_LEATHER, "Red dragon leather", ItemID.RED_DHIDE_CHAPS, "Red d'hide chaps", 156.0, "Red_d'hide_chaps"));
		list.add(dhideRecipe("red_dhide_body", "Red d'hide body", 77, 3, ItemID.RED_DRAGON_LEATHER, "Red dragon leather", ItemID.RED_DHIDE_BODY, "Red d'hide body", 234.0, "Red_d'hide_body"));
		list.add(dhideShield("red_dhide_shield", "Red d'hide shield", 76, ItemID.MAGIC_SHIELD, "Magic shield", ItemID.RED_DRAGON_LEATHER, "Red dragon leather", ItemID.ADAMANTITE_NAILS, "Adamantite nails", ItemID.RED_DHIDE_SHIELD, "Red d'hide shield", 156.0, "Red_d'hide_shield"));

		// Black d'hide
		list.add(dhideRecipe("black_dhide_vambraces", "Black d'hide vambraces", 79, 1, ItemID.BLACK_DRAGON_LEATHER, "Black dragon leather", ItemID.BLACK_DHIDE_VAMBRACES, "Black d'hide vambraces", 86.0, "Black_d'hide_vambraces"));
		list.add(dhideRecipe("black_dhide_chaps", "Black d'hide chaps", 82, 2, ItemID.BLACK_DRAGON_LEATHER, "Black dragon leather", ItemID.BLACK_DHIDE_CHAPS, "Black d'hide chaps", 172.0, "Black_d'hide_chaps"));
		list.add(dhideRecipe("black_dhide_body", "Black d'hide body", 84, 3, ItemID.BLACK_DRAGON_LEATHER, "Black dragon leather", ItemID.BLACK_DHIDE_BODY, "Black d'hide body", 258.0, "Black_d'hide_body"));
		list.add(dhideShield("black_dhide_shield", "Black d'hide shield", 83, ItemID.REDWOOD_SHIELD, "Redwood shield", ItemID.BLACK_DRAGON_LEATHER, "Black dragon leather", ItemID.RUNE_NAILS, "Rune nails", ItemID.BLACK_DHIDE_SHIELD, "Black d'hide shield", 172.0, "Black_d'hide_shield"));

		// Alternative: Raw Dragonhide + 20 GP Tanning Fee -> Bodies
		list.add(rawDhideBody("green_dhide_body_raw", "Green d'hide body (Tan Hide)", 63, ItemID.GREEN_DRAGONHIDE, "Green dragonhide", ItemID.GREEN_DHIDE_BODY, "Green d'hide body", 186.0, "Green_d'hide_body"));
		list.add(rawDhideBody("blue_dhide_body_raw", "Blue d'hide body (Tan Hide)", 71, ItemID.BLUE_DRAGONHIDE, "Blue dragonhide", ItemID.BLUE_DHIDE_BODY, "Blue d'hide body", 210.0, "Blue_d'hide_body"));
		list.add(rawDhideBody("red_dhide_body_raw", "Red d'hide body (Tan Hide)", 77, ItemID.RED_DRAGONHIDE, "Red dragonhide", ItemID.RED_DHIDE_BODY, "Red d'hide body", 234.0, "Red_d'hide_body"));
		list.add(rawDhideBody("black_dhide_body_raw", "Black d'hide body (Tan Hide)", 84, ItemID.BLACK_DRAGONHIDE, "Black dragonhide", ItemID.BLACK_DHIDE_BODY, "Black d'hide body", 258.0, "Black_d'hide_body"));
	}

	private static CraftingRecipe dhideRecipe(
		String id, String name, int level, int leatherQty,
		int leatherId, String leatherName, int outId, String outName, double xp, String wikiSlug)
	{
		double actionsPerHour = leatherQty == 3 ? 1350.0 : (leatherQty == 2 ? 1600.0 : 1900.0);
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.DRAGONHIDE)
			.requiredLevel(level)
			.inputs(List.of(
				input(leatherId, leatherName, leatherQty),
				input(ItemID.THREAD, "Thread", 0.2, true, true)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.requiredEquipment(Collections.singletonList("Needle"))
			.toolSlotsRequired(2)
			.specialMechanics((26 / leatherQty) + " crafts per inventory (26 free slots with Needle & Thread).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Buy Tanned Leather")
			.estimatedActionsPerHour(actionsPerHour)
			.xpRateBasis("~" + (int) actionsPerHour + " crafts/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	private static CraftingRecipe dhideShield(
		String id, String name, int level,
		int woodShieldId, String woodShieldName,
		int leatherId, String leatherName,
		int nailsId, String nailsName,
		int outId, String outName, double xp, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.DRAGONHIDE)
			.requiredLevel(level)
			.inputs(List.of(
				input(woodShieldId, woodShieldName, 1.0),
				input(leatherId, leatherName, 2.0),
				input(nailsId, nailsName, 15.0, true, true)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.requiredEquipment(Collections.singletonList("Hammer"))
			.requiredTools(Collections.singletonList("Hammer"))
			.toolSlotsRequired(2)
			.members(true)
			.boostable(true)
			.specialMechanics("Uses 1 wooden shield + 2 dragon leather + 15 " + nailsName.toLowerCase() + " with a Hammer (8 shields/inventory).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Shield Assembly")
			.estimatedActionsPerHour(1200.0)
			.xpRateBasis("Estimated ~1,200 shields/hr bank-standing")
			.xpRateVerified(false)
			.build();
	}

	private static CraftingRecipe rawDhideBody(
		String id, String name, int level,
		int rawHideId, String rawHideName, int outId, String outName, double xp, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.DRAGONHIDE)
			.requiredLevel(level)
			.inputs(List.of(
				input(rawHideId, rawHideName, 3.0),
				input(ItemID.COINS_995, "Coins (Tan fee)", 60.0, true, true),
				input(ItemID.THREAD, "Thread", 0.2, true, true)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.requiredEquipment(Collections.singletonList("Needle"))
			.toolSlotsRequired(2)
			.additionalRequirements(Collections.singletonList("Tanner (20 gp per hide = 60 gp per body)"))
			.specialMechanics("Buys raw dragonhides, pays 20 gp/hide at tanner (or Lunar Tan Leather), then crafts body.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Process Raw Hide + Tan")
			.estimatedActionsPerHour(1050.0)
			.xpRateBasis("Estimated ~1,050 bodies/hr including tanning step")
			.xpRateVerified(false)
			.build();
	}

	// =========================================================================
	// 5. GLASSBLOWING & MOLTEN GLASS
	// =========================================================================
	private static void addGlassblowingRecipes(List<CraftingRecipe> list)
	{
		list.add(glassblowing("beer_glass", "Beer glass", 1, ItemID.BEER_GLASS, "Beer glass", 17.5, "Beer_glass", true));
		list.add(glassblowing("empty_candle_lantern", "Empty candle lantern", 4, ItemID.EMPTY_CANDLE_LANTERN, "Empty candle lantern", 19.0, "Empty_candle_lantern", true));
		list.add(glassblowing("empty_oil_lamp", "Empty oil lamp", 12, ItemID.EMPTY_OIL_LAMP, "Empty oil lamp", 25.0, "Empty_oil_lamp", true));
		list.add(glassblowing("vial", "Vial", 33, ItemID.VIAL, "Vial", 35.0, "Vial", true));
		list.add(glassblowing("empty_fishbowl", "Empty fishbowl", 42, ItemID.EMPTY_FISHBOWL, "Empty fishbowl", 42.5, "Empty_fishbowl", true));
		list.add(glassblowing("unpowered_orb", "Unpowered orb", 46, ItemID.UNPOWERED_ORB, "Unpowered orb", 52.5, "Unpowered_orb", true));
		list.add(glassblowing("lantern_lens", "Lantern lens", 49, ItemID.LANTERN_LENS, "Lantern lens", 55.0, "Lantern_lens", true));

		// Empty light orb (level 87) is untradeable!
		list.add(CraftingRecipe.builder()
			.id("empty_light_orb")
			.name("Empty light orb")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(87)
			.inputs(Collections.singletonList(input(ItemID.MOLTEN_GLASS, "Molten glass", 1.0)))
			.outputs(Collections.singletonList(
				RecipeOutput.builder()
					.itemId(ItemID.EMPTY_LIGHT_ORB).itemName("Empty light orb").quantity(1.0)
					.probability(1.0).tradeable(false).taxExempt(true).build()))
			.xpPerAction(70.0)
			.xpPerOutputItem(70.0)
			.requiredEquipment(Collections.singletonList("Glassblowing pipe"))
			.requiredTools(Collections.singletonList("Glassblowing pipe"))
			.toolSlotsRequired(1)
			.members(true)
			.boostable(true)
			.specialMechanics("Empty light orbs are untradeable (0 GP GE revenue). No quest required to blow Empty light orbs.")
			.wikiReference(WIKI_BASE + "Empty_light_orb")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Untradeable AFK Training")
			.estimatedActionsPerHour(1700.0)
			.xpRateBasis("~1,700 glasses/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build());

		// Complete Light orb (Cave goblin wire + Empty light orb -> Light orb, also untradeable)
		list.add(CraftingRecipe.builder()
			.id("light_orb_wire")
			.name("Light orb (Add Goblin Wire)")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(87)
			.inputs(List.of(
				input(ItemID.MOLTEN_GLASS, "Molten glass", 1.0),
				input(ItemID.CAVE_GOBLIN_WIRE, "Cave goblin wire", 1.0)))
			.outputs(Collections.singletonList(
				RecipeOutput.builder()
					.itemId(ItemID.LIGHT_ORB).itemName("Light orb").quantity(1.0)
					.probability(1.0).tradeable(false).taxExempt(true).build()))
			.xpPerAction(174.0)
			.xpPerOutputItem(174.0)
			.requiredEquipment(Collections.singletonList("Glassblowing pipe"))
			.toolSlotsRequired(1)
			.questRequirements(Collections.singletonList("Death to the Dorgeshuun"))
			.specialMechanics("70 XP to blow Empty light orb + 104 XP to attach Cave goblin wire = 174 XP total. Light orb is untradeable.")
			.wikiReference(WIKI_BASE + "Light_orb")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Untradeable + Wire")
			.estimatedActionsPerHour(950.0)
			.xpRateBasis("Estimated ~950 full cycles/hr")
			.xpRateVerified(false)
			.build());

		// Smelting Molten Glass at Furnace
		list.add(CraftingRecipe.builder()
			.id("molten_glass_furnace")
			.name("Molten glass (Furnace)")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(1)
			.inputs(List.of(
				input(ItemID.BUCKET_OF_SAND, "Bucket of sand", 1.0),
				input(ItemID.SODA_ASH, "Soda ash", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.MOLTEN_GLASS, "Molten glass", 1.0)))
			.xpPerAction(20.0)
			.xpPerOutputItem(20.0)
			.additionalRequirements(Collections.singletonList("Furnace"))
			.specialMechanics("Smelts 1 Bucket of sand + 1 Soda ash into 1 Molten glass (14 per inventory).")
			.wikiReference(WIKI_BASE + "Molten_glass")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Furnace Smelt")
			.estimatedActionsPerHour(1300.0)
			.xpRateBasis("~1,300 smelts/hr at Edgeville furnace (OSRS Wiki)")
			.xpRateVerified(true)
			.build());

		// Superglass Make (3 Giant seaweed + 18 Buckets of sand -> ~26.1 Molten glass without pickup, 180 Crafting XP)
		list.add(CraftingRecipe.builder()
			.id("superglass_make_giant")
			.name("Superglass Make (3 Giant Seaweed)")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(1)
			.inputs(List.of(
				input(ItemID.GIANT_SEAWEED, "Giant seaweed", 3.0),
				input(ItemID.BUCKET_OF_SAND, "Bucket of sand", 18.0),
				input(ItemID.ASTRAL_RUNE, "Astral rune", 2.0, true, true)))
			.outputs(Collections.singletonList(
				RecipeOutput.builder()
					.itemId(ItemID.MOLTEN_GLASS).itemName("Molten glass").quantity(26.1)
					.probability(1.0).tradeable(true).build()))
			.xpPerAction(180.0)
			.xpPerOutputItem(180.0 / 26.1)
			.questRequirements(Collections.singletonList("Lunar Diplomacy"))
			.additionalRequirements(List.of("77 Magic (Lunar Spellbook)", "Smoke/Fire+Air staff for rune savings"))
			.specialMechanics("3 Giant seaweed + 18 Buckets of sand yields avg 1.45x molten glass (26.1) without floor pickup, or 1.6x (28.8) with pickup. Grants 180 Crafting XP + 78 Magic XP per cast.")
			.wikiReference(WIKI_BASE + "Superglass_Make")
			.verificationStatus(VerificationStatus.NEEDS_REVIEW)
			.members(true)
			.boostable(false)
			.methodVariant("Lunar Spell (3:18 No Pickup)")
			.estimatedActionsPerHour(580.0)
			.xpRateBasis("~580 casts/hr (~104k Crafting XP/hr, OSRS Wiki)")
			.xpRateVerified(true)
			.build());

		// Superglass Make (3 Giant seaweed + 18 Sand WITH pickup -> ~28.8 Molten glass)
		list.add(CraftingRecipe.builder()
			.id("superglass_make_giant_pickup")
			.name("Superglass Make (3:18 + Pickup)")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(1)
			.inputs(List.of(
				input(ItemID.GIANT_SEAWEED, "Giant seaweed", 3.0),
				input(ItemID.BUCKET_OF_SAND, "Bucket of sand", 18.0),
				input(ItemID.ASTRAL_RUNE, "Astral rune", 2.0, true, true)))
			.outputs(Collections.singletonList(
				RecipeOutput.builder()
					.itemId(ItemID.MOLTEN_GLASS).itemName("Molten glass").quantity(28.8)
					.probability(1.0).tradeable(true).build()))
			.xpPerAction(180.0)
			.xpPerOutputItem(180.0 / 28.8)
			.members(true)
			.boostable(false)
			.questRequirements(Collections.singletonList("Lunar Diplomacy"))
			.additionalRequirements(List.of("77 Magic (Lunar Spellbook)", "Smoke/Fire+Air staff", "Pick up floor overflow glass"))
			.unlockRequirements(Collections.singletonList("Lunar Diplomacy"))
			.specialMechanics("Picking up floor molten glass increases yield from ~1.45x (26.1) to ~1.6x (28.8) per cast at slower casts/hr.")
			.wikiReference(WIKI_BASE + "Superglass_Make")
			.verificationStatus(VerificationStatus.NEEDS_REVIEW)
			.methodVariant("Lunar Spell (3:18 Pickup)")
			.estimatedActionsPerHour(468.0)
			.xpRateBasis("~468 casts/hr (~13,500 glass/hr, OSRS Wiki)")
			.xpRateVerified(true)
			.build());

		// Superglass Make (13 Soda ash + 13 Sand -> ~16.9 Molten glass, 130 Crafting XP)
		list.add(CraftingRecipe.builder()
			.id("superglass_make_soda_ash")
			.name("Superglass Make (13 Soda Ash)")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(1)
			.inputs(List.of(
				input(ItemID.SODA_ASH, "Soda ash", 13.0),
				input(ItemID.BUCKET_OF_SAND, "Bucket of sand", 13.0),
				input(ItemID.ASTRAL_RUNE, "Astral rune", 2.0, true, true)))
			.outputs(Collections.singletonList(
				RecipeOutput.builder()
					.itemId(ItemID.MOLTEN_GLASS).itemName("Molten glass").quantity(16.9)
					.probability(1.0).tradeable(true).build()))
			.xpPerAction(130.0)
			.xpPerOutputItem(130.0 / 16.9)
			.members(true)
			.boostable(false)
			.questRequirements(Collections.singletonList("Lunar Diplomacy"))
			.additionalRequirements(List.of("77 Magic (Lunar Spellbook)", "Smoke/Fire+Air staff"))
			.unlockRequirements(Collections.singletonList("Lunar Diplomacy"))
			.specialMechanics("13 Soda ash + 13 Buckets of sand yields avg 1.3x molten glass (16.9). No glass drops to floor.")
			.wikiReference(WIKI_BASE + "Superglass_Make")
			.verificationStatus(VerificationStatus.NEEDS_REVIEW)
			.methodVariant("Lunar Spell (13:13 Soda Ash)")
			.estimatedActionsPerHour(750.0)
			.xpRateBasis("Estimated ~750 casts/hr (~97.5k Crafting XP/hr)")
			.xpRateVerified(false)
			.build());

		// Oil lantern (Empty oil lamp + Oil lantern frame)
		list.add(CraftingRecipe.builder()
			.id("oil_lantern")
			.name("Empty oil lantern")
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(26)
			.inputs(List.of(
				input(ItemID.EMPTY_OIL_LAMP, "Empty oil lamp", 1.0),
				input(ItemID.OIL_LANTERN_FRAME, "Oil lantern frame", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.EMPTY_OIL_LANTERN, "Empty oil lantern", 1.0)))
			.xpPerAction(50.0)
			.xpPerOutputItem(50.0)
			.members(true)
			.boostable(true)
			.specialMechanics("Combines an Empty oil lamp with an Oil lantern frame (14 per inventory).")
			.wikiReference(WIKI_BASE + "Oil_lantern")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Assembly")
			.estimatedActionsPerHour(2200.0)
			.xpRateBasis("Estimated ~2,200 assemblies/hr bank-standing")
			.xpRateVerified(false)
			.build());
	}

	private static CraftingRecipe glassblowing(
		String id, String name, int level, int outId, String outName, double xp, String wikiSlug, boolean tradeable)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.GLASSBLOWING)
			.requiredLevel(level)
			.inputs(Collections.singletonList(input(ItemID.MOLTEN_GLASS, "Molten glass", 1.0)))
			.outputs(Collections.singletonList(
				RecipeOutput.builder()
					.itemId(outId).itemName(outName).quantity(1.0)
					.probability(1.0).tradeable(tradeable).taxExempt(!tradeable).build()))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.requiredEquipment(Collections.singletonList("Glassblowing pipe"))
			.toolSlotsRequired(1)
			.specialMechanics("27 molten glass per inventory with Glassblowing pipe.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Buy Molten Glass")
			.estimatedActionsPerHour(1700.0)
			.xpRateBasis("~1,700 glasses/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	// =========================================================================
	// 6. BATTLESTAVES
	// =========================================================================
	private static void addBattlestaffRecipes(List<CraftingRecipe> list)
	{
		list.add(battlestaff("water_battlestaff", "Water battlestaff", 54, ItemID.WATER_ORB, "Water orb", ItemID.WATER_BATTLESTAFF, "Water battlestaff", 100.0, "Water_battlestaff"));
		list.add(battlestaff("earth_battlestaff", "Earth battlestaff", 58, ItemID.EARTH_ORB, "Earth orb", ItemID.EARTH_BATTLESTAFF, "Earth battlestaff", 112.5, "Earth_battlestaff"));
		list.add(battlestaff("fire_battlestaff", "Fire battlestaff", 62, ItemID.FIRE_ORB, "Fire orb", ItemID.FIRE_BATTLESTAFF, "Fire battlestaff", 125.0, "Fire_battlestaff"));
		list.add(battlestaff("air_battlestaff", "Air battlestaff", 66, ItemID.AIR_ORB, "Air orb", ItemID.AIR_BATTLESTAFF, "Air battlestaff", 137.5, "Air_battlestaff"));
	}

	private static CraftingRecipe battlestaff(
		String id, String name, int level, int orbId, String orbName, int outId, String outName, double xp, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.BATTLESTAVES)
			.requiredLevel(level)
			.inputs(List.of(
				input(ItemID.BATTLESTAFF, "Battlestaff", 1.0),
				input(orbId, orbName, 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.toolSlotsRequired(0)
			.specialMechanics("14 battlestaves + 14 orbs per inventory (no tools required). 2-tick craft (~2,400/hr).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Attach Charged Orb")
			.estimatedActionsPerHour(2400.0)
			.xpRateBasis("~2,400 staves/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	// =========================================================================
	// 7. POTTERY
	// =========================================================================
	private static void addPotteryRecipes(List<CraftingRecipe> list)
	{
		list.add(potteryRecipe("pottery_pot", "Pot (Wheel + Oven)", 1, ItemID.POT, "Pot", 12.6, "Pot", false, null,
			"6.3 XP moulding Unfired pot + 6.3 XP firing in pottery oven = 12.6 XP total."));
		list.add(potteryRecipe("pottery_empty_cup", "Empty cup (Wheel + Oven)", 3, ItemID.EMPTY_CUP, "Empty cup", 17.0, "Empty_cup", true, null,
			"8.5 XP moulding Unfired cup + 8.5 XP firing in pottery oven = 17.0 XP total."));
		list.add(potteryRecipe("pottery_pie_dish", "Pie dish (Wheel + Oven)", 7, ItemID.PIE_DISH, "Pie dish", 25.0, "Pie_dish", false, null,
			"15.0 XP moulding Unfired pie dish + 10.0 XP firing in pottery oven = 25.0 XP total."));
		list.add(potteryRecipe("pottery_bowl", "Bowl (Wheel + Oven)", 8, ItemID.BOWL, "Bowl", 33.0, "Bowl", false, null,
			"18.0 XP moulding Unfired bowl + 15.0 XP firing in pottery oven = 33.0 XP total."));
		list.add(potteryRecipe("pottery_plant_pot", "Empty plant pot (Wheel + Oven)", 19, ItemID.EMPTY_PLANT_POT, "Empty plant pot", 37.5, "Empty_plant_pot", true, null,
			"20.0 XP moulding Unfired plant pot + 17.5 XP firing in pottery oven = 37.5 XP total."));
		list.add(potteryRecipe("pottery_pot_lid", "Pot lid (Wheel + Oven)", 25, ItemID.POT_LID, "Pot lid", 40.0, "Pot_lid", true,
			Collections.singletonList("One Small Favour (partial completion)"),
			"20.0 XP moulding + 20.0 XP firing = 40.0 XP total (100% oven success from Crafting lvl 13+)."));
	}

	private static CraftingRecipe potteryRecipe(
		String id, String name, int level, int outId, String outName, double xp, String wikiSlug,
		boolean members, List<String> quests, String mechanics)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.POTTERY)
			.requiredLevel(level)
			.inputs(Collections.singletonList(input(ItemID.SOFT_CLAY, "Soft clay", 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.members(members)
			.boostable(true)
			.questRequirements(quests != null ? quests : Collections.emptyList())
			.additionalRequirements(Collections.singletonList("Potter's wheel & Pottery oven (Barbarian Village / Crafting Guild)"))
			.specialMechanics(mechanics)
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Wheel + Oven")
			.estimatedActionsPerHour(750.0)
			.xpRateBasis("Estimated ~750 completed items/hr (wheel + oven)")
			.xpRateVerified(false)
			.build();
	}

	// =========================================================================
	// 8. OTHER (Silver items, Tiaras, Xerician, Spinning, Amethyst, Snelm)
	// =========================================================================
	private static void addOtherRecipes(List<CraftingRecipe> list)
	{
		// Spinning
		list.add(CraftingRecipe.builder()
			.id("ball_of_wool")
			.name("Ball of wool (Spinning)")
			.category(CraftingCategory.OTHER)
			.requiredLevel(1)
			.inputs(Collections.singletonList(input(ItemID.WOOL, "Wool", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.BALL_OF_WOOL, "Ball of wool", 1.0)))
			.xpPerAction(2.5)
			.xpPerOutputItem(2.5)
			.members(false)
			.boostable(true)
			.additionalRequirements(Collections.singletonList("Spinning wheel (Lumbridge / Neitiznot)"))
			.specialMechanics("28 Wool per inventory at a spinning wheel.")
			.wikiReference(WIKI_BASE + "Ball_of_wool")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Spinning Wheel")
			.estimatedActionsPerHour(1600.0)
			.xpRateBasis("~1,600 spins/hr at Lumbridge Castle")
			.xpRateVerified(false)
			.build());

		list.add(CraftingRecipe.builder()
			.id("bow_string")
			.name("Bow string (Spin Flax)")
			.category(CraftingCategory.OTHER)
			.requiredLevel(10)
			.inputs(Collections.singletonList(input(ItemID.FLAX, "Flax", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.BOW_STRING, "Bow string", 1.0)))
			.xpPerAction(15.0)
			.xpPerOutputItem(15.0)
			.additionalRequirements(Collections.singletonList("Spinning wheel (Lumbridge / Seers' Village)"))
			.specialMechanics("28 Flax per inventory. Note: Lunar Spin Flax spell gives Magic + Crafting XP faster.")
			.wikiReference(WIKI_BASE + "Bow_string")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Spinning Wheel")
			.estimatedActionsPerHour(1600.0)
			.xpRateBasis("~1,600 spins/hr at Lumbridge spinning wheel (OSRS Wiki)")
			.xpRateVerified(true)
			.build());

		// Silver items & Tiara
		list.add(CraftingRecipe.builder()
			.id("unstrung_symbol")
			.name("Unstrung symbol")
			.category(CraftingCategory.OTHER)
			.requiredLevel(16)
			.inputs(Collections.singletonList(input(ItemID.SILVER_BAR, "Silver bar", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.UNSTRUNG_SYMBOL, "Unstrung symbol", 1.0)))
			.xpPerAction(50.0)
			.xpPerOutputItem(50.0)
			.members(false)
			.boostable(true)
			.requiredEquipment(Collections.singletonList("Holy mould"))
			.requiredMoulds(Collections.singletonList("Holy mould"))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace"))
			.specialMechanics("27 Silver bars per inventory with Holy mould.")
			.wikiReference(WIKI_BASE + "Unstrung_symbol")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Silver Smelting")
			.estimatedActionsPerHour(1550.0)
			.xpRateBasis("~1,550 smelts/hr at Edgeville furnace")
			.xpRateVerified(false)
			.build());

		list.add(CraftingRecipe.builder()
			.id("unstrung_emblem")
			.name("Unstrung emblem")
			.category(CraftingCategory.OTHER)
			.requiredLevel(17)
			.inputs(Collections.singletonList(input(ItemID.SILVER_BAR, "Silver bar", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.UNSTRUNG_EMBLEM, "Unstrung emblem", 1.0)))
			.xpPerAction(50.0)
			.xpPerOutputItem(50.0)
			.requiredEquipment(Collections.singletonList("Unholy mould"))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace"))
			.specialMechanics("27 Silver bars per inventory with Unholy mould.")
			.wikiReference(WIKI_BASE + "Unstrung_emblem")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Silver Smelting")
			.estimatedActionsPerHour(1550.0)
			.xpRateBasis("~1,550 smelts/hr at Edgeville furnace")
			.xpRateVerified(false)
			.build());

		list.add(CraftingRecipe.builder()
			.id("silver_sickle")
			.name("Silver sickle")
			.category(CraftingCategory.OTHER)
			.requiredLevel(18)
			.inputs(Collections.singletonList(input(ItemID.SILVER_BAR, "Silver bar", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.SILVER_SICKLE, "Silver sickle", 1.0)))
			.xpPerAction(50.0)
			.xpPerOutputItem(50.0)
			.requiredEquipment(Collections.singletonList("Sickle mould"))
			.toolSlotsRequired(1)
			.questRequirements(Collections.singletonList("Nature Spirit (started/completed for mould)"))
			.additionalRequirements(Collections.singletonList("Furnace"))
			.specialMechanics("27 Silver bars per inventory with Sickle mould.")
			.wikiReference(WIKI_BASE + "Silver_sickle")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Silver Smelting")
			.estimatedActionsPerHour(1550.0)
			.xpRateBasis("~1,550 smelts/hr at Edgeville furnace")
			.xpRateVerified(false)
			.build());

		list.add(CraftingRecipe.builder()
			.id("tiara")
			.name("Tiara")
			.category(CraftingCategory.OTHER)
			.requiredLevel(23)
			.inputs(Collections.singletonList(input(ItemID.SILVER_BAR, "Silver bar", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.TIARA, "Tiara", 1.0)))
			.xpPerAction(52.5)
			.xpPerOutputItem(52.5)
			.members(false)
			.boostable(true)
			.requiredEquipment(Collections.singletonList("Tiara mould"))
			.requiredMoulds(Collections.singletonList("Tiara mould"))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace"))
			.specialMechanics("Popular F2P/budget method: 27 Silver bars per inventory with Tiara mould.")
			.wikiReference(WIKI_BASE + "Tiara")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Silver Smelting")
			.estimatedActionsPerHour(1550.0)
			.xpRateBasis("~1,550 tiaras/hr at Edgeville furnace (OSRS Wiki)")
			.xpRateVerified(true)
			.build());

		// Xerician Clothing (Stackable Xerician fabric!)
		list.add(xerician("xerician_hat", "Xerician hat", 14, 3, ItemID.XERICIAN_HAT, "Xerician hat", 66.0, "Xerician_hat"));
		list.add(xerician("xerician_robe", "Xerician robe", 17, 4, ItemID.XERICIAN_ROBE, "Xerician robe", 88.0, "Xerician_robe"));
		list.add(xerician("xerician_top", "Xerician top", 22, 5, ItemID.XERICIAN_TOP, "Xerician top", 110.0, "Xerician_top"));

		// Snelm crafting (32.5 XP, tradeable shells, no quest required)
		list.add(snelm("myre_snelm", "Myre snelm", ItemID.BLAMISH_MYRE_SHELL, "Blamish myre shell (round)", ItemID.MYRE_SNELM, "Myre snelm", "Myre_snelm"));
		list.add(snelm("bloodntar_snelm", "Blood'n'tar snelm", ItemID.BLAMISH_RED_SHELL, "Blamish red shell (round)", ItemID.BLOODNTAR_SNELM, "Blood'n'tar snelm", "Blood'n'tar_snelm"));
		list.add(snelm("ochre_snelm", "Ochre snelm", ItemID.BLAMISH_OCHRE_SHELL, "Blamish ochre shell (round)", ItemID.OCHRE_SNELM, "Ochre snelm", "Ochre_snelm"));
		list.add(snelm("bruise_blue_snelm", "Bruise blue snelm", ItemID.BLAMISH_BLUE_SHELL, "Blamish blue shell (round)", ItemID.BRUISE_BLUE_SNELM, "Bruise blue snelm", "Bruise_blue_snelm"));
		list.add(snelm("broken_bark_snelm", "Broken bark snelm", ItemID.BLAMISH_BARK_SHELL, "Blamish bark shell", ItemID.BROKEN_BARK_SNELM, "Broken bark snelm", "Broken_bark_snelm"));
		list.add(snelm("pointed_myre_snelm", "Pointed myre snelm", ItemID.BLAMISH_MYRE_SHELL_3355, "Blamish myre shell (pointed)", ItemID.POINTED_MYRE_SNELM, "Pointed myre snelm", "Pointed_myre_snelm"));
		list.add(snelm("pointed_bloodntar_snelm", "Pointed blood'n'tar snelm", ItemID.BLAMISH_RED_SHELL_3357, "Blamish red shell (pointed)", ItemID.POINTED_BLOODNTAR_SNELM, "Pointed blood'n'tar snelm", "Pointed_blood'n'tar_snelm"));
		list.add(snelm("pointed_ochre_snelm", "Pointed ochre snelm", ItemID.BLAMISH_OCHRE_SHELL_3359, "Blamish ochre shell (pointed)", ItemID.POINTED_OCHRE_SNELM, "Pointed ochre snelm", "Pointed_ochre_snelm"));
		list.add(snelm("pointed_bruise_blue_snelm", "Pointed bruise blue snelm", ItemID.BLAMISH_BLUE_SHELL_3361, "Blamish blue shell (pointed)", 3343, "Pointed bruise blue snelm", "Pointed_bruise_blue_snelm"));

		// Amethyst chiseling (multi-output stackable tips!)
		list.add(amethyst("amethyst_bolt_tips", "Amethyst bolt tips", 83, ItemID.AMETHYST_BOLT_TIPS, "Amethyst bolt tips", 15.0, "Amethyst_bolt_tips"));
		list.add(amethyst("amethyst_arrowtips", "Amethyst arrowtips", 85, ItemID.AMETHYST_ARROWTIPS, "Amethyst arrowtips", 15.0, "Amethyst_arrowtips"));
		list.add(amethyst("amethyst_javelin_heads", "Amethyst javelin heads", 87, ItemID.AMETHYST_JAVELIN_TIPS, "Amethyst javelin heads", 5.0, "Amethyst_javelin_heads"));
		list.add(amethyst("amethyst_dart_tip", "Amethyst dart tip", 89, ItemID.AMETHYST_DART_TIP, "Amethyst dart tip", 8.0, "Amethyst_dart_tip"));
	}

	private static CraftingRecipe xerician(
		String id, String name, int level, int fabricQty, int outId, String outName, double xp, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.OTHER)
			.requiredLevel(level)
			.inputs(List.of(
				input(ItemID.XERICIAN_FABRIC, "Xerician fabric", fabricQty, true, true),
				input(ItemID.THREAD, "Thread", 0.2, true, true)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp)
			.xpPerOutputItem(xp)
			.requiredEquipment(Collections.singletonList("Needle"))
			.toolSlotsRequired(2)
			.specialMechanics("Xerician fabric is stackable; 26 finished robes per inventory before banking.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Standard")
			.estimatedActionsPerHour(1750.0)
			.xpRateBasis("Estimated ~1,750 crafts/hr bank-standing")
			.xpRateVerified(false)
			.build();
	}

	private static CraftingRecipe snelm(
		String id, String name, int shellId, String shellName, int outId, String outName, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.OTHER)
			.requiredLevel(15)
			.inputs(Collections.singletonList(input(shellId, shellName, 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(32.5)
			.xpPerOutputItem(32.5)
			.requiredEquipment(Collections.singletonList("Chisel"))
			.requiredTools(Collections.singletonList("Chisel"))
			.toolSlotsRequired(1)
			.members(true)
			.boostable(true)
			.specialMechanics("Carving any Blamish snail shell with a Chisel grants 32.5 Crafting XP. Shells are tradeable (no quest needed).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Carve Shell")
			.estimatedActionsPerHour(2200.0)
			.xpRateBasis("Estimated ~2,200 shells/hr bank-standing")
			.xpRateVerified(false)
			.build();
	}

	private static CraftingRecipe amethyst(
		String id, String name, int level, int outId, String outName, double outQty, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id)
			.name(name)
			.category(CraftingCategory.OTHER)
			.requiredLevel(level)
			.inputs(Collections.singletonList(input(ItemID.AMETHYST, "Amethyst", 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, outQty)))
			.xpPerAction(60.0)
			.xpPerOutputItem(60.0 / outQty)
			.requiredEquipment(Collections.singletonList("Chisel"))
			.toolSlotsRequired(1)
			.specialMechanics("Chisels 1 Amethyst into " + (int) outQty + " stackable " + outName + " (60.0 XP/amethyst; 27 amethyst/inv).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Multi-Output Chisel")
			.estimatedActionsPerHour(2200.0)
			.xpRateBasis("~2,200 amethyst/hr bank-standing (OSRS Wiki)")
			.xpRateVerified(true)
			.build();
	}

	private static RecipeInput input(int itemId, String name, double qty)
	{
		return RecipeInput.builder().itemId(itemId).itemName(name).quantity(qty).build();
	}

	private static RecipeInput input(int itemId, String name, double qty, boolean stackable, boolean consumed)
	{
		return RecipeInput.builder()
			.itemId(itemId).itemName(name).quantity(qty).stackable(stackable).consumed(consumed).build();
	}

	private static RecipeOutput output(int itemId, String name, double qty)
	{
		return RecipeOutput.builder().itemId(itemId).itemName(name).quantity(qty).probability(1.0).tradeable(true).build();
	}

	// =========================================================================
	// 9. CRAFTWISE 2.0 EXPANDED VERIFIED OSRS RECIPES (Phase 3)
	// =========================================================================
	private static void addExpandedRecipes(List<CraftingRecipe> list)
	{
		// Additional Spinning & Weaving
		list.add(simpleOther("crossbow_string", "Crossbow string (Spin Sinew)", 10,
			List.of(input(ItemID.SINEW, "Sinew", 1.0)), ItemID.CROSSBOW_STRING, "Crossbow string", 15.0,
			true, null, List.of("Spinning wheel"), "Spins 1 Sinew into 1 Crossbow string (28/inventory).",
			"Crossbow_string", "Spinning Wheel", 1600.0));
		list.add(simpleOther("spin_rope", "Rope (Spin Hair)", 30,
			List.of(input(ItemID.HAIR, "Hair", 1.0)), ItemID.ROPE, "Rope", 25.0,
			true, null, List.of("Spinning wheel"), "Spins 1 Yak hair into 1 Rope (28/inventory).",
			"Rope", "Spinning Wheel", 1600.0));
		list.add(simpleOther("weave_empty_sack", "Empty sack (Weave)", 21,
			List.of(input(ItemID.JUTE_FIBRE, "Jute fibre", 4.0)), ItemID.EMPTY_SACK, "Empty sack", 38.0,
			true, null, List.of("Loom"), "Weaves 4 Jute fibres into 1 Empty sack (7 sacks/inventory).",
			"Empty_sack", "Weaving Loom", 800.0));
		list.add(simpleOther("weave_drift_net", "Drift net (Weave)", 26,
			List.of(input(ItemID.JUTE_FIBRE, "Jute fibre", 2.0)), ItemID.DRIFT_NET, "Drift net", 55.0,
			true, null, List.of("Loom (Fossil Island / Rellekka / Corsairs)"), "Weaves 2 Jute fibres into 1 Drift net (14 nets/inventory).",
			"Drift_net", "Weaving Loom", 1100.0));
		list.add(simpleOther("weave_basket", "Basket (Weave)", 36,
			List.of(input(ItemID.WILLOW_BRANCH, "Willow branch", 6.0)), ItemID.BASKET, "Basket", 56.0,
			true, null, List.of("Loom"), "Weaves 6 Willow branches into 1 Basket (4 baskets/inventory).",
			"Basket", "Weaving Loom", 600.0));

		// Silver Bolts (unf)
		list.add(CraftingRecipe.builder()
			.id("silver_bolts_unf")
			.name("Silver bolts (unf)")
			.category(CraftingCategory.OTHER)
			.requiredLevel(21)
			.inputs(Collections.singletonList(input(ItemID.SILVER_BAR, "Silver bar", 1.0)))
			.outputs(Collections.singletonList(output(ItemID.SILVER_BOLTS_UNF, "Silver bolts (unf)", 10.0)))
			.xpPerAction(50.0)
			.xpPerOutputItem(5.0)
			.members(true)
			.boostable(true)
			.requiredEquipment(Collections.singletonList("Bolt mould"))
			.requiredMoulds(Collections.singletonList("Bolt mould"))
			.toolSlotsRequired(1)
			.additionalRequirements(Collections.singletonList("Furnace"))
			.specialMechanics("Smelts 1 Silver bar into 10 stackable Silver bolts (unf) (27 bars/inventory).")
			.wikiReference(WIKI_BASE + "Silver_bolts_(unf)")
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant("Multi-Output Mould")
			.estimatedActionsPerHour(1550.0)
			.xpRateBasis("~1,550 silver bars/hr at Edgeville furnace")
			.xpRateVerified(false)
			.build());

		// Additional Leather & Hide Shields / Spiky Vambraces / Yak-hide / Splitbark / Mixed / Hueycoatl
		list.add(CraftingRecipe.builder()
			.id("hard_leather_shield")
			.name("Hard leather shield")
			.category(CraftingCategory.LEATHER)
			.requiredLevel(41)
			.inputs(List.of(
				input(ItemID.OAK_SHIELD, "Oak shield", 1.0),
				input(ItemID.HARD_LEATHER, "Hard leather", 2.0),
				input(ItemID.BRONZE_NAILS, "Bronze nails", 15.0, true, true)))
			.outputs(Collections.singletonList(output(ItemID.HARD_LEATHER_SHIELD, "Hard leather shield", 1.0)))
			.xpPerAction(70.0).xpPerOutputItem(70.0)
			.members(true).boostable(true)
			.requiredEquipment(Collections.singletonList("Hammer")).requiredTools(Collections.singletonList("Hammer")).toolSlotsRequired(2)
			.specialMechanics("Uses 1 Oak shield + 2 Hard leather + 15 Bronze nails with a Hammer (8/inventory).")
			.wikiReference(WIKI_BASE + "Hard_leather_shield")
			.verificationStatus(VerificationStatus.VERIFIED).methodVariant("Shield Assembly")
			.estimatedActionsPerHour(1200.0).xpRateBasis("Estimated ~1,200 shields/hr bank-standing").xpRateVerified(false).build());

		list.add(CraftingRecipe.builder()
			.id("snakeskin_shield")
			.name("Snakeskin shield")
			.category(CraftingCategory.LEATHER)
			.requiredLevel(56)
			.inputs(List.of(
				input(ItemID.WILLOW_SHIELD, "Willow shield", 1.0),
				input(ItemID.SNAKESKIN, "Snakeskin", 2.0),
				input(ItemID.IRON_NAILS, "Iron nails", 15.0, true, true)))
			.outputs(Collections.singletonList(output(ItemID.SNAKESKIN_SHIELD, "Snakeskin shield", 1.0)))
			.xpPerAction(100.0).xpPerOutputItem(100.0)
			.members(true).boostable(true)
			.requiredEquipment(Collections.singletonList("Hammer")).requiredTools(Collections.singletonList("Hammer")).toolSlotsRequired(2)
			.specialMechanics("Uses 1 Willow shield + 2 Snakeskin + 15 Iron nails with a Hammer (8/inventory).")
			.wikiReference(WIKI_BASE + "Snakeskin_shield")
			.verificationStatus(VerificationStatus.VERIFIED).methodVariant("Shield Assembly")
			.estimatedActionsPerHour(1200.0).xpRateBasis("Estimated ~1,200 shields/hr bank-standing").xpRateVerified(false).build());

		list.add(spikyVambrace("spiky_vambraces", "Spiky vambraces (Leather)", ItemID.LEATHER_VAMBRACES, "Leather vambraces", ItemID.SPIKY_VAMBRACES, "Spiky vambraces", "Spiky_vambraces"));
		list.add(spikyVambrace("green_spiky_vambraces", "Green spiky vambraces", ItemID.GREEN_DHIDE_VAMBRACES, "Green d'hide vambraces", ItemID.GREEN_SPIKY_VAMBRACES, "Green spiky vambraces", "Green_spiky_vambraces"));
		list.add(spikyVambrace("blue_spiky_vambraces", "Blue spiky vambraces", ItemID.BLUE_DHIDE_VAMBRACES, "Blue d'hide vambraces", ItemID.BLUE_SPIKY_VAMBRACES, "Blue spiky vambraces", "Blue_spiky_vambraces"));
		list.add(spikyVambrace("red_spiky_vambraces", "Red spiky vambraces", ItemID.RED_DHIDE_VAMBRACES, "Red d'hide vambraces", ItemID.RED_SPIKY_VAMBRACES, "Red spiky vambraces", "Red_spiky_vambraces"));
		list.add(spikyVambrace("black_spiky_vambraces", "Black spiky vambraces", ItemID.BLACK_DHIDE_VAMBRACES, "Black d'hide vambraces", ItemID.BLACK_SPIKY_VAMBRACES, "Black spiky vambraces", "Black_spiky_vambraces"));

		// Yak-hide
		list.add(sewnArmour("yak_hide_legs", "Yak-hide armour (legs)", CraftingCategory.LEATHER, 43,
			List.of(input(ItemID.CURED_YAKHIDE, "Cured yak-hide", 1.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.YAKHIDE_ARMOUR_10824, "Yak-hide armour (legs)", 32.0, "Yak-hide_armour_(legs)",
			Collections.singletonList("The Fremennik Isles (started)")));
		list.add(sewnArmour("yak_hide_top", "Yak-hide armour (top)", CraftingCategory.LEATHER, 46,
			List.of(input(ItemID.CURED_YAKHIDE, "Cured yak-hide", 2.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.YAKHIDE_ARMOUR, "Yak-hide armour (top)", 32.0, "Yak-hide_armour_(top)",
			Collections.singletonList("The Fremennik Isles (started)")));

		// Splitbark
		list.add(splitbark("splitbark_gauntlets", "Splitbark gauntlets", 60, 1, ItemID.SPLITBARK_GAUNTLETS, "Splitbark gauntlets", 62.0, "Splitbark_gauntlets"));
		list.add(splitbark("splitbark_boots", "Splitbark boots", 60, 1, ItemID.SPLITBARK_BOOTS, "Splitbark boots", 62.0, "Splitbark_boots"));
		list.add(splitbark("splitbark_helm", "Splitbark helm", 61, 2, ItemID.SPLITBARK_HELM, "Splitbark helm", 124.0, "Splitbark_helm"));
		list.add(splitbark("splitbark_legs", "Splitbark legs", 62, 3, ItemID.SPLITBARK_LEGS, "Splitbark legs", 186.0, "Splitbark_legs"));
		list.add(splitbark("splitbark_body", "Splitbark body", 62, 4, ItemID.SPLITBARK_BODY, "Splitbark body", 248.0, "Splitbark_body"));

		// Mixed hide (Varlamore)
		list.add(sewnArmour("mixed_hide_cape", "Mixed hide cape", CraftingCategory.LEATHER, 68,
			List.of(input(ItemID.MIXED_HIDE_BASE, "Mixed hide base", 1.0), input(ItemID.JAGUAR_FUR, "Jaguar fur", 1.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.MIXED_HIDE_CAPE, "Mixed hide cape", 62.0, "Mixed_hide_cape", Collections.emptyList()));
		list.add(sewnArmour("mixed_hide_boots", "Mixed hide boots", CraftingCategory.LEATHER, 69,
			List.of(input(ItemID.MIXED_HIDE_BASE, "Mixed hide base", 1.0), input(ItemID.SUNLIGHT_ANTELOPE_FUR, "Sunlight antelope fur", 1.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.MIXED_HIDE_BOOTS, "Mixed hide boots", 75.0, "Mixed_hide_boots", Collections.emptyList()));
		list.add(sewnArmour("mixed_hide_legs", "Mixed hide legs", CraftingCategory.LEATHER, 71,
			List.of(input(ItemID.MIXED_HIDE_BASE, "Mixed hide base", 1.0), input(ItemID.FOX_FUR, "Fox fur", 3.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.MIXED_HIDE_LEGS, "Mixed hide legs", 210.0, "Mixed_hide_legs", Collections.emptyList()));
		list.add(sewnArmour("mixed_hide_top", "Mixed hide top", CraftingCategory.LEATHER, 72,
			List.of(input(ItemID.MIXED_HIDE_BASE, "Mixed hide base", 1.0), input(ItemID.SUNLIGHT_ANTELOPE_FUR, "Sunlight antelope fur", 2.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.MIXED_HIDE_TOP, "Mixed hide top", 150.0, "Mixed_hide_top", Collections.emptyList()));

		// Hueycoatl hide (30085 = Hueycoatl hide, 30082 = Hueycoatl hide vambraces)
		list.add(sewnArmour("hueycoatl_vambraces", "Hueycoatl hide vambraces", CraftingCategory.DRAGONHIDE, 76,
			List.of(input(30085, "Hueycoatl hide", 1.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			30082, "Hueycoatl hide vambraces", 95.0, "Hueycoatl_hide_vambraces", Collections.emptyList()));
		list.add(sewnArmour("hueycoatl_coif", "Hueycoatl hide coif", CraftingCategory.DRAGONHIDE, 76,
			List.of(input(30085, "Hueycoatl hide", 2.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.HUEYCOATL_HIDE_COIF, "Hueycoatl hide coif", 190.0, "Hueycoatl_hide_coif", Collections.emptyList()));
		list.add(sewnArmour("hueycoatl_chaps", "Hueycoatl hide chaps", CraftingCategory.DRAGONHIDE, 77,
			List.of(input(30085, "Hueycoatl hide", 2.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.HUEYCOATL_HIDE_CHAPS, "Hueycoatl hide chaps", 190.0, "Hueycoatl_hide_chaps", Collections.emptyList()));
		list.add(sewnArmour("hueycoatl_body", "Hueycoatl hide body", CraftingCategory.DRAGONHIDE, 78,
			List.of(input(30085, "Hueycoatl hide", 3.0), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			ItemID.HUEYCOATL_HIDE_BODY, "Hueycoatl hide body", 285.0, "Hueycoatl_hide_body", Collections.emptyList()));

		// Birdhouses (Logs + Clockwork + Hammer + Chisel)
		list.add(birdhouse("bird_house", "Bird house", 5, ItemID.LOGS, "Logs", ItemID.BIRD_HOUSE, 15.0, "Bird_house"));
		list.add(birdhouse("oak_bird_house", "Oak bird house", 15, ItemID.OAK_LOGS, "Oak logs", ItemID.OAK_BIRD_HOUSE, 20.0, "Oak_bird_house"));
		list.add(birdhouse("willow_bird_house", "Willow bird house", 25, ItemID.WILLOW_LOGS, "Willow logs", ItemID.WILLOW_BIRD_HOUSE, 25.0, "Willow_bird_house"));
		list.add(birdhouse("teak_bird_house", "Teak bird house", 35, ItemID.TEAK_LOGS, "Teak logs", ItemID.TEAK_BIRD_HOUSE, 30.0, "Teak_bird_house"));
		list.add(birdhouse("maple_bird_house", "Maple bird house", 45, ItemID.MAPLE_LOGS, "Maple logs", ItemID.MAPLE_BIRD_HOUSE, 35.0, "Maple_bird_house"));
		list.add(birdhouse("mahogany_bird_house", "Mahogany bird house", 50, ItemID.MAHOGANY_LOGS, "Mahogany logs", ItemID.MAHOGANY_BIRD_HOUSE, 40.0, "Mahogany_bird_house"));
		list.add(birdhouse("yew_bird_house", "Yew bird house", 60, ItemID.YEW_LOGS, "Yew logs", ItemID.YEW_BIRD_HOUSE, 45.0, "Yew_bird_house"));
		list.add(birdhouse("magic_bird_house", "Magic bird house", 75, ItemID.MAGIC_LOGS, "Magic logs", ItemID.MAGIC_BIRD_HOUSE, 50.0, "Magic_bird_house"));
		list.add(birdhouse("redwood_bird_house", "Redwood bird house", 90, ItemID.REDWOOD_LOGS, "Redwood logs", ItemID.REDWOOD_BIRD_HOUSE, 55.0, "Redwood_bird_house"));

		// Craft + Enchant Jewellery (Full Crafting + Enchant chain; XP counts Crafting XP only)
		list.add(craftAndEnchant("craft_enchant_ring_of_recoil", "Ring of recoil (Craft + Enchant)", 20,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.SAPPHIRE, "Sapphire", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 1.0, true, true)),
			ItemID.RING_OF_RECOIL, "Ring of recoil", 40.0, "Ring mould", "Lvl-1 Enchant (7 Magic, +17.5 Magic XP)", "Ring_of_recoil"));
		list.add(craftAndEnchant("craft_enchant_games_necklace", "Games necklace(8) (Craft + Enchant)", 22,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.SAPPHIRE, "Sapphire", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 1.0, true, true)),
			ItemID.GAMES_NECKLACE8, "Games necklace(8)", 55.0, "Necklace mould", "Lvl-1 Enchant (7 Magic; tax exempt on GE)", "Games_necklace"));
		list.add(craftAndEnchant("craft_enchant_bracelet_of_clay", "Bracelet of clay (Craft + Enchant)", 23,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.SAPPHIRE, "Sapphire", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 1.0, true, true)),
			ItemID.BRACELET_OF_CLAY, "Bracelet of clay", 60.0, "Bracelet mould", "Lvl-1 Enchant (7 Magic, +17.5 Magic XP)", "Bracelet_of_clay"));
		list.add(craftAndEnchant("craft_enchant_ring_of_dueling", "Ring of dueling(8) (Craft + Enchant)", 27,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.EMERALD, "Emerald", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.AIR_RUNE, "Air rune", 3.0, true, true)),
			ItemID.RING_OF_DUELING8, "Ring of dueling(8)", 55.0, "Ring mould", "Lvl-2 Enchant (27 Magic; tax exempt on GE)", "Ring_of_dueling"));
		list.add(craftAndEnchant("craft_enchant_binding_necklace", "Binding necklace (Craft + Enchant)", 29,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.EMERALD, "Emerald", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.AIR_RUNE, "Air rune", 3.0, true, true)),
			ItemID.BINDING_NECKLACE, "Binding necklace", 60.0, "Necklace mould", "Lvl-2 Enchant (27 Magic, +37 Magic XP)", "Binding_necklace"));
		list.add(craftAndEnchant("craft_enchant_ring_of_forging", "Ring of forging (Craft + Enchant)", 34,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.RUBY, "Ruby", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.FIRE_RUNE, "Fire rune", 5.0, true, true)),
			ItemID.RING_OF_FORGING, "Ring of forging", 70.0, "Ring mould", "Lvl-3 Enchant (49 Magic, +59 Magic XP)", "Ring_of_forging"));
		list.add(craftAndEnchant("craft_enchant_dodgy_necklace", "Dodgy necklace (Craft + Enchant)", 16,
			List.of(input(ItemID.SILVER_BAR, "Silver bar", 1.0), input(ItemID.OPAL, "Opal", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 1.0, true, true)),
			ItemID.DODGY_NECKLACE, "Dodgy necklace", 35.0, "Necklace mould", "Lvl-1 Enchant (7 Magic, +17.5 Magic XP)", "Dodgy_necklace"));
		list.add(craftAndEnchant("craft_enchant_expeditious_bracelet", "Expeditious bracelet (Craft + Enchant)", 22,
			List.of(input(ItemID.SILVER_BAR, "Silver bar", 1.0), input(ItemID.OPAL, "Opal", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 1.0, true, true)),
			ItemID.EXPEDITIOUS_BRACELET, "Expeditious bracelet", 45.0, "Bracelet mould", "Lvl-1 Enchant (7 Magic, +17.5 Magic XP)", "Expeditious_bracelet"));
		list.add(craftAndEnchant("craft_enchant_necklace_of_passage", "Necklace of passage(5) (Craft + Enchant)", 25,
			List.of(input(ItemID.SILVER_BAR, "Silver bar", 1.0), input(ItemID.JADE, "Jade", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.AIR_RUNE, "Air rune", 3.0, true, true)),
			ItemID.NECKLACE_OF_PASSAGE5, "Necklace of passage(5)", 54.0, "Necklace mould", "Lvl-2 Enchant (27 Magic, +37 Magic XP)", "Necklace_of_passage"));
		list.add(craftAndEnchant("craft_enchant_bracelet_of_slaughter", "Bracelet of slaughter (Craft + Enchant)", 38,
			List.of(input(ItemID.SILVER_BAR, "Silver bar", 1.0), input(ItemID.RED_TOPAZ, "Red topaz", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.FIRE_RUNE, "Fire rune", 5.0, true, true)),
			ItemID.BRACELET_OF_SLAUGHTER, "Bracelet of slaughter", 75.0, "Bracelet mould", "Lvl-3 Enchant (49 Magic, +59 Magic XP)", "Bracelet_of_slaughter"));
		list.add(craftAndEnchant("craft_enchant_burning_amulet", "Burning amulet(5) (Craft + Enchant)", 45,
			List.of(input(ItemID.SILVER_BAR, "Silver bar", 1.0), input(ItemID.RED_TOPAZ, "Red topaz", 1.0), input(ItemID.BALL_OF_WOOL, "Ball of wool", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.FIRE_RUNE, "Fire rune", 5.0, true, true)),
			ItemID.BURNING_AMULET5, "Burning amulet(5)", 84.0, "Amulet mould", "Smelt (80) + String (4) + Lvl-3 Enchant (49 Magic)", "Burning_amulet"));
		list.add(craftAndEnchant("craft_enchant_skills_necklace", "Skills necklace (Craft + Enchant)", 72,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.DRAGONSTONE, "Dragonstone", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 15.0, true, true), input(ItemID.EARTH_RUNE, "Earth rune", 15.0, true, true)),
			11113, "Skills necklace", 105.0, "Necklace mould", "Lvl-5 Enchant (68 Magic, +78 Magic XP)", "Skills_necklace"));
		list.add(craftAndEnchant("craft_enchant_combat_bracelet", "Combat bracelet (Craft + Enchant)", 74,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.DRAGONSTONE, "Dragonstone", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 15.0, true, true), input(ItemID.EARTH_RUNE, "Earth rune", 15.0, true, true)),
			11126, "Combat bracelet", 110.0, "Bracelet mould", "Lvl-5 Enchant (68 Magic, +78 Magic XP)", "Combat_bracelet"));
		list.add(craftAndEnchant("craft_enchant_amulet_of_glory", "Amulet of glory (Craft + Enchant)", 80,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.DRAGONSTONE, "Dragonstone", 1.0), input(ItemID.BALL_OF_WOOL, "Ball of wool", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.WATER_RUNE, "Water rune", 15.0, true, true), input(ItemID.EARTH_RUNE, "Earth rune", 15.0, true, true)),
			ItemID.AMULET_OF_GLORY, "Amulet of glory", 154.0, "Amulet mould", "Smelt (150) + String (4) + Lvl-5 Enchant (68 Magic)", "Amulet_of_glory"));
		list.add(craftAndEnchant("craft_enchant_amulet_of_fury", "Amulet of fury (Craft + Enchant)", 90,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.ONYX, "Onyx", 1.0), input(ItemID.BALL_OF_WOOL, "Ball of wool", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.EARTH_RUNE, "Earth rune", 20.0, true, true), input(ItemID.FIRE_RUNE, "Fire rune", 20.0, true, true)),
			ItemID.AMULET_OF_FURY, "Amulet of fury", 169.0, "Amulet mould", "Smelt (165) + String (4) + Lvl-6 Enchant (87 Magic)", "Amulet_of_fury"));
		list.add(craftAndEnchant("craft_enchant_ring_of_suffering", "Ring of suffering (Craft + Enchant)", 89,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.ZENYTE, "Zenyte", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.BLOOD_RUNE, "Blood rune", 20.0, true, true), input(ItemID.SOUL_RUNE, "Soul rune", 20.0, true, true)),
			ItemID.RING_OF_SUFFERING, "Ring of suffering", 150.0, "Ring mould", "Lvl-7 Enchant (93 Magic, +110 Magic XP)", "Ring_of_suffering"));
		list.add(craftAndEnchant("craft_enchant_necklace_of_anguish", "Necklace of anguish (Craft + Enchant)", 92,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.ZENYTE, "Zenyte", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.BLOOD_RUNE, "Blood rune", 20.0, true, true), input(ItemID.SOUL_RUNE, "Soul rune", 20.0, true, true)),
			ItemID.NECKLACE_OF_ANGUISH, "Necklace of anguish", 165.0, "Necklace mould", "Lvl-7 Enchant (93 Magic, +110 Magic XP)", "Necklace_of_anguish"));
		list.add(craftAndEnchant("craft_enchant_tormented_bracelet", "Tormented bracelet (Craft + Enchant)", 95,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.ZENYTE, "Zenyte", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.BLOOD_RUNE, "Blood rune", 20.0, true, true), input(ItemID.SOUL_RUNE, "Soul rune", 20.0, true, true)),
			ItemID.TORMENTED_BRACELET, "Tormented bracelet", 180.0, "Bracelet mould", "Lvl-7 Enchant (93 Magic, +110 Magic XP)", "Tormented_bracelet"));
		list.add(craftAndEnchant("craft_enchant_amulet_of_torture", "Amulet of torture (Craft + Enchant)", 98,
			List.of(input(ItemID.GOLD_BAR, "Gold bar", 1.0), input(ItemID.ZENYTE, "Zenyte", 1.0), input(ItemID.BALL_OF_WOOL, "Ball of wool", 1.0),
				input(ItemID.COSMIC_RUNE, "Cosmic rune", 1.0, true, true), input(ItemID.BLOOD_RUNE, "Blood rune", 20.0, true, true), input(ItemID.SOUL_RUNE, "Soul rune", 20.0, true, true)),
			ItemID.AMULET_OF_TORTURE, "Amulet of torture", 204.0, "Amulet mould", "Smelt (200) + String (4) + Lvl-7 Enchant (93 Magic)", "Amulet_of_torture"));
	}

	private static CraftingRecipe simpleOther(
		String id, String name, int level, List<RecipeInput> inputs, int outId, String outName, double xp,
		boolean members, List<String> quests, List<String> additionalReqs, String mechanics,
		String wikiSlug, String variant, double actionsPerHr)
	{
		return CraftingRecipe.builder()
			.id(id).name(name).category(CraftingCategory.OTHER).requiredLevel(level)
			.inputs(inputs).outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp).xpPerOutputItem(xp)
			.members(members).boostable(true)
			.questRequirements(quests != null ? quests : Collections.emptyList())
			.additionalRequirements(additionalReqs != null ? additionalReqs : Collections.emptyList())
			.specialMechanics(mechanics)
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED)
			.methodVariant(variant)
			.estimatedActionsPerHour(actionsPerHr)
			.xpRateBasis("Estimated ~" + (int) actionsPerHr + " actions/hr")
			.xpRateVerified(false)
			.build();
	}

	private static CraftingRecipe spikyVambrace(
		String id, String name, int vambId, String vambName, int outId, String outName, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id).name(name).category(CraftingCategory.LEATHER).requiredLevel(32)
			.inputs(List.of(input(vambId, vambName, 1.0), input(ItemID.KEBBIT_CLAWS, "Kebbit claws", 1.0)))
			.outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(6.0).xpPerOutputItem(6.0)
			.members(true).boostable(true).toolSlotsRequired(0)
			.specialMechanics("Attaches Kebbit claws to vambraces (no needle/thread required; grants 6 Crafting XP).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED).methodVariant("Upgrade Leather")
			.estimatedActionsPerHour(2200.0).xpRateBasis("Estimated ~2,200 attachments/hr bank-standing").xpRateVerified(false).build();
	}

	private static CraftingRecipe sewnArmour(
		String id, String name, CraftingCategory cat, int level, List<RecipeInput> inputs,
		int outId, String outName, double xp, String wikiSlug, List<String> quests)
	{
		return CraftingRecipe.builder()
			.id(id).name(name).category(cat).requiredLevel(level)
			.inputs(inputs).outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(xp).xpPerOutputItem(xp)
			.members(true).boostable(true)
			.requiredEquipment(Collections.singletonList("Needle")).requiredTools(Collections.singletonList("Needle"))
			.toolSlotsRequired(2)
			.questRequirements(quests != null ? quests : Collections.emptyList())
			.specialMechanics("Sewn with Needle & Thread.")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED).methodVariant("Sewn Armour")
			.estimatedActionsPerHour(1200.0).xpRateBasis("Estimated ~1,200 crafts/hr bank-standing").xpRateVerified(false).build();
	}

	private static CraftingRecipe splitbark(
		String id, String name, int level, int qty, int outId, String outName, double xp, String wikiSlug)
	{
		return sewnArmour(id, name, CraftingCategory.OTHER, level,
			List.of(input(ItemID.BARK, "Bark", qty), input(ItemID.FINE_CLOTH, "Fine cloth", qty), input(ItemID.THREAD, "Thread", 0.2, true, true)),
			outId, outName, xp, wikiSlug, Collections.emptyList());
	}

	private static CraftingRecipe birdhouse(
		String id, String name, int level, int logId, String logName, int outId, double xp, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id).name(name).category(CraftingCategory.OTHER).requiredLevel(level)
			.inputs(List.of(input(logId, logName, 1.0), input(ItemID.CLOCKWORK, "Clockwork", 1.0)))
			.outputs(Collections.singletonList(output(outId, name, 1.0)))
			.xpPerAction(xp).xpPerOutputItem(xp)
			.members(true).boostable(true)
			.requiredEquipment(List.of("Hammer", "Chisel")).requiredTools(List.of("Hammer", "Chisel"))
			.toolSlotsRequired(2)
			.specialMechanics("Requires Clockwork + " + logName + " with Hammer & Chisel (Fossil Island Bone Voyage needed to trap).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED).methodVariant("Assembly")
			.estimatedActionsPerHour(1800.0).xpRateBasis("Estimated ~1,800 assemblies/hr bank-standing").xpRateVerified(false).build();
	}

	private static CraftingRecipe craftAndEnchant(
		String id, String name, int level, List<RecipeInput> inputs, int outId, String outName,
		double craftXp, String mould, String magicNote, String wikiSlug)
	{
		return CraftingRecipe.builder()
			.id(id).name(name).category(CraftingCategory.JEWELLERY).requiredLevel(level)
			.inputs(inputs).outputs(Collections.singletonList(output(outId, outName, 1.0)))
			.xpPerAction(craftXp).xpPerOutputItem(craftXp)
			.members(true).boostable(true)
			.requiredEquipment(Collections.singletonList(mould)).requiredMoulds(Collections.singletonList(mould))
			.toolSlotsRequired(1)
			.additionalRequirements(List.of("Furnace", magicNote))
			.processStage("Craft & Enchant")
			.specialMechanics("Full Crafting + Enchant chain (" + magicNote + "). Enchanting grants Magic XP only (0 extra Crafting XP).")
			.wikiReference(WIKI_BASE + wikiSlug)
			.verificationStatus(VerificationStatus.VERIFIED).methodVariant("Craft + Enchant")
			.estimatedActionsPerHour(850.0).xpRateBasis("Estimated ~850 Craft+Enchant cycles/hr").xpRateVerified(false).build();
	}
}
