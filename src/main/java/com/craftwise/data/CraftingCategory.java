package com.craftwise.data;

/**
 * Categories of OSRS Crafting methods supported by CraftWise.
 */
public enum CraftingCategory
{
	GEM_CUTTING("Gem Cutting", "Cutting uncut gems with a chisel"),
	JEWELLERY("Jewellery", "Gold, silver, gem, and zenyte jewellery"),
	LEATHER("Leather", "Regular, hard, studded, and snakeskin leather"),
	DRAGONHIDE("Dragonhide", "Green, blue, red, and black dragonhide armour & shields"),
	GLASSBLOWING("Glassblowing", "Blowing molten glass and smelting/Superglass Make"),
	BATTLESTAVES("Battlestaves", "Attaching charged elemental orbs to battlestaves"),
	POTTERY("Pottery", "Shaping and firing soft clay pottery"),
	OTHER("Other", "Tiaras, Xerician, amethyst, spinning, silver, and snelm crafting");

	private final String displayName;
	private final String description;

	CraftingCategory(String displayName, String description)
	{
		this.displayName = displayName;
		this.description = description;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getDescription()
	{
		return description;
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
