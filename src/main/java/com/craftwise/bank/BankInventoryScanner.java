package com.craftwise.bank;

import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Safe RuneLite container reader for Bank and Inventory materials (Feature 2.1 & 2.10).
 * Only accesses supported RuneLite ItemContainers and ItemManager APIs on the client thread.
 * Excludes bank placeholders, distinguishes noted vs unnoted inventory items, and never
 * assumes an unvisited bank is empty.
 */
public final class BankInventoryScanner
{
	public static final int NOTED_TEMPLATE_ID = 799;

	private BankInventoryScanner()
	{
	}

	/**
	 * Checks whether the OSRS bank main interface is legitimately open.
	 * Must be called on the client thread.
	 */
	public static boolean isBankInterfaceOpen(Client client)
	{
		if (client == null)
		{
			return false;
		}
		try
		{
			Widget bankWidget = client.getWidget(InterfaceID.BANKMAIN, 0);
			return bankWidget != null && !bankWidget.isHidden();
		}
		catch (Throwable ignored)
		{
			return false;
		}
	}

	/**
	 * Scans an accessible {@link ItemContainer} belonging to {@link InventoryID#BANK}.
	 * Must be called on the client thread when the bank container is accessible.
	 */
	public static void scanBank(
		ItemContainer bankContainer,
		ItemManager itemManager,
		OwnedMaterialSnapshot targetSnapshot,
		boolean bankOpen,
		long epochSec)
	{
		if (bankContainer == null || targetSnapshot == null)
		{
			return;
		}
		Map<Integer, Long> qtyMap = new LinkedHashMap<>();
		Map<Integer, String> nameMap = new LinkedHashMap<>();
		Item[] items = bankContainer.getItems();
		if (items != null)
		{
			for (Item item : items)
			{
				if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				int rawId = item.getId();
				int canonicalId = itemManager != null ? itemManager.canonicalize(rawId) : rawId;
				ItemComposition comp = itemManager != null ? itemManager.getItemComposition(rawId) : null;
				if (comp != null && comp.getPlaceholderTemplateId() != -1)
				{
					// Bank placeholder entry (quantity is 0 or placeholder template) -> never count as owned
					continue;
				}
				qtyMap.merge(canonicalId, (long) item.getQuantity(), Long::sum);
				ItemComposition canonComp = itemManager != null ? itemManager.getItemComposition(canonicalId) : comp;
				if (canonComp != null && canonComp.getName() != null)
				{
					nameMap.put(canonicalId, canonComp.getName());
				}
			}
		}
		targetSnapshot.updateBankContents(qtyMap, nameMap, bankOpen, epochSec);
	}

	/**
	 * Scans the player's {@link InventoryID#INVENTORY} container, separating unnoted vs noted items.
	 * Must be called on the client thread.
	 */
	public static void scanInventory(
		ItemContainer invContainer,
		ItemManager itemManager,
		OwnedMaterialSnapshot targetSnapshot,
		long epochSec)
	{
		if (invContainer == null || targetSnapshot == null)
		{
			return;
		}
		Map<Integer, Long> unnotedMap = new LinkedHashMap<>();
		Map<Integer, Long> notedMap = new LinkedHashMap<>();
		Map<Integer, String> nameMap = new LinkedHashMap<>();
		Item[] items = invContainer.getItems();
		if (items != null)
		{
			for (Item item : items)
			{
				if (item == null || item.getId() <= 0 || item.getQuantity() <= 0)
				{
					continue;
				}
				int rawId = item.getId();
				ItemComposition comp = itemManager != null ? itemManager.getItemComposition(rawId) : null;
				boolean isNoted = comp != null && comp.getNote() == NOTED_TEMPLATE_ID;
				int canonicalId = itemManager != null ? itemManager.canonicalize(rawId) : rawId;
				if (isNoted)
				{
					notedMap.merge(canonicalId, (long) item.getQuantity(), Long::sum);
				}
				else
				{
					unnotedMap.merge(canonicalId, (long) item.getQuantity(), Long::sum);
				}
				ItemComposition canonComp = itemManager != null ? itemManager.getItemComposition(canonicalId) : comp;
				if (canonComp != null && canonComp.getName() != null)
				{
					nameMap.put(canonicalId, canonComp.getName());
				}
			}
		}
		targetSnapshot.updateInventoryContents(unnotedMap, notedMap, nameMap, epochSec);
	}
}
