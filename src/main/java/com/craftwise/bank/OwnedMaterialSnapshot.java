package com.craftwise.bank;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Thread-safe snapshot of player-owned Crafting materials across Bank and Inventory,
 * keeping bank, unnoted inventory, and noted inventory quantities cleanly separated.
 */
@Slf4j
public class OwnedMaterialSnapshot
{
	@Getter
	@RequiredArgsConstructor
	public enum BankDataState
	{
		UNAVAILABLE("BANK DATA UNAVAILABLE"),
		LIVE_OPEN("Bank Open (Live Sync)"),
		SNAPSHOT_CLOSED("Bank Closed (Last Scan)");

		private final String label;

		@Override
		public String toString()
		{
			return label;
		}
	}

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class OwnedItemCount
	{
		private int itemId;
		private String itemName;
		private long bankQty;
		private long inventoryUnnotedQty;
		private long inventoryNotedQty;
		private long manualAdjustmentQty;

		/**
		 * Total directly craftable/usable quantity across bank + unnoted inventory + manual count
		 * (excludes noted inventory items until unnoted at a bank).
		 */
		public long getCombinedUsableQty()
		{
			return Math.max(0L, bankQty + inventoryUnnotedQty + manualAdjustmentQty);
		}

		/**
		 * Total owned quantity including noted items currently held in inventory.
		 */
		public long getCombinedIncludingNotedQty()
		{
			return Math.max(0L, bankQty + inventoryUnnotedQty + inventoryNotedQty + manualAdjustmentQty);
		}
	}

	@Data
	@Builder
	public static class SnapshotView
	{
		private final BankDataState bankDataState;
		private final long lastBankScanEpochSec;
		private final long lastInventoryScanEpochSec;
		@Builder.Default
		private final Map<Integer, OwnedItemCount> items = Collections.emptyMap();

		public boolean isBankScanned()
		{
			return bankDataState != BankDataState.UNAVAILABLE;
		}

		public OwnedItemCount getCount(int itemId)
		{
			return items != null ? items.get(itemId) : null;
		}

		public Map<Integer, Long> toCombinedUsableMap(boolean includeNotedFromInventory)
		{
			if (items == null || items.isEmpty())
			{
				return Collections.emptyMap();
			}
			Map<Integer, Long> out = new LinkedHashMap<>(items.size());
			for (Map.Entry<Integer, OwnedItemCount> e : items.entrySet())
			{
				OwnedItemCount c = e.getValue();
				long q = includeNotedFromInventory ? c.getCombinedIncludingNotedQty() : c.getCombinedUsableQty();
				if (q > 0L)
				{
					out.put(e.getKey(), q);
				}
			}
			return Collections.unmodifiableMap(out);
		}
	}

	private static final Type OVERRIDE_TYPE = new TypeToken<Map<Integer, Long>>()
	{
	}.getType();

	private volatile Filepath manualFile;
	private final Gson gson;
	private volatile BankDataState bankDataState = BankDataState.UNAVAILABLE;
	private volatile long lastBankScanEpochSec = 0L;
	private volatile long lastInventoryScanEpochSec = 0L;

	private final Map<Integer, Long> bankQuantities = new LinkedHashMap<>();
	private final Map<Integer, Long> invUnnotedQuantities = new LinkedHashMap<>();
	private final Map<Integer, Long> invNotedQuantities = new LinkedHashMap<>();
	private final Map<Integer, String> itemNames = new LinkedHashMap<>();
	private final Map<Integer, Long> manualOwnedOverrides = new LinkedHashMap<>();

	public OwnedMaterialSnapshot(Gson gson)
	{
		this(null, gson);
	}

	public OwnedMaterialSnapshot(Filepath manualFile, Gson gson)
	{
		this.manualFile = manualFile;
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		loadManualOverrides();
	}

	public synchronized void initializeStorage(Filepath manualFile)
	{
		this.manualFile = manualFile;
		loadManualOverrides();
	}

	public synchronized void loadManualOverrides()
	{
		manualOwnedOverrides.clear();
		Filepath file = this.manualFile;
		if (file == null || !file.exists())
		{
			return;
		}
		try (Reader reader = file.openReader())
		{
			Map<Integer, Long> loaded = gson.fromJson(reader, OVERRIDE_TYPE);
			if (loaded != null)
			{
				manualOwnedOverrides.putAll(loaded);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to load CraftWise manual owned quantities from {}", file, ex);
		}
	}

	public synchronized void saveManualOverrides()
	{
		Filepath file = this.manualFile;
		if (file == null)
		{
			return;
		}
		try
		{
			Filepath parent = file.getParent();
			if (parent != null && !parent.exists())
			{
				parent.createDirectories();
			}
			file.write(gson.toJson(new LinkedHashMap<>(manualOwnedOverrides)));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise manual owned quantities to {}", file, ex);
		}
	}

	public synchronized void setManualOwnedQuantity(int itemId, String itemName, long quantity)
	{
		if (itemId <= 0)
		{
			return;
		}
		if (itemName != null && !itemName.trim().isEmpty())
		{
			itemNames.put(itemId, itemName.trim());
		}
		if (quantity <= 0L)
		{
			manualOwnedOverrides.remove(itemId);
		}
		else
		{
			manualOwnedOverrides.put(itemId, quantity);
		}
		saveManualOverrides();
	}

	public synchronized void clearManualOverrides()
	{
		manualOwnedOverrides.clear();
		saveManualOverrides();
	}

	public synchronized Map<Integer, Long> getManualOwnedOverrides()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<>(manualOwnedOverrides));
	}

	public synchronized void updateBankContents(
		Map<Integer, Long> newBankQtyById,
		Map<Integer, String> namesById,
		boolean bankOpen,
		long epochSec)
	{
		bankQuantities.clear();
		if (newBankQtyById != null)
		{
			bankQuantities.putAll(newBankQtyById);
		}
		if (namesById != null)
		{
			itemNames.putAll(namesById);
		}
		this.lastBankScanEpochSec = epochSec;
		this.bankDataState = bankOpen ? BankDataState.LIVE_OPEN : BankDataState.SNAPSHOT_CLOSED;
	}

	public synchronized void updateInventoryContents(
		Map<Integer, Long> newUnnotedQtyById,
		Map<Integer, Long> newNotedQtyById,
		Map<Integer, String> namesById,
		long epochSec)
	{
		invUnnotedQuantities.clear();
		invNotedQuantities.clear();
		if (newUnnotedQtyById != null)
		{
			invUnnotedQuantities.putAll(newUnnotedQtyById);
		}
		if (newNotedQtyById != null)
		{
			invNotedQuantities.putAll(newNotedQtyById);
		}
		if (namesById != null)
		{
			itemNames.putAll(namesById);
		}
		this.lastInventoryScanEpochSec = epochSec;
	}

	public synchronized void onBankClosed()
	{
		if (this.bankDataState == BankDataState.LIVE_OPEN)
		{
			this.bankDataState = BankDataState.SNAPSHOT_CLOSED;
		}
	}

	public synchronized void resetSessionBankState()
	{
		bankQuantities.clear();
		invUnnotedQuantities.clear();
		invNotedQuantities.clear();
		this.bankDataState = BankDataState.UNAVAILABLE;
		this.lastBankScanEpochSec = 0L;
		this.lastInventoryScanEpochSec = 0L;
	}

	public synchronized SnapshotView getSnapshotView()
	{
		Map<Integer, OwnedItemCount> combined = new LinkedHashMap<>();
		for (Integer id : bankQuantities.keySet())
		{
			mergeEntry(combined, id);
		}
		for (Integer id : invUnnotedQuantities.keySet())
		{
			mergeEntry(combined, id);
		}
		for (Integer id : invNotedQuantities.keySet())
		{
			mergeEntry(combined, id);
		}
		for (Integer id : manualOwnedOverrides.keySet())
		{
			mergeEntry(combined, id);
		}

		return SnapshotView.builder()
			.bankDataState(bankDataState)
			.lastBankScanEpochSec(lastBankScanEpochSec)
			.lastInventoryScanEpochSec(lastInventoryScanEpochSec)
			.items(Collections.unmodifiableMap(combined))
			.build();
	}

	private void mergeEntry(Map<Integer, OwnedItemCount> combined, int id)
	{
		if (combined.containsKey(id))
		{
			return;
		}
		long b = bankQuantities.getOrDefault(id, 0L);
		long iu = invUnnotedQuantities.getOrDefault(id, 0L);
		long in = invNotedQuantities.getOrDefault(id, 0L);
		long man = manualOwnedOverrides.getOrDefault(id, 0L);
		if (b + iu + in + man <= 0L)
		{
			return;
		}
		combined.put(id, OwnedItemCount.builder()
			.itemId(id)
			.itemName(itemNames.getOrDefault(id, "Item #" + id))
			.bankQty(b)
			.inventoryUnnotedQty(iu)
			.inventoryNotedQty(in)
			.manualAdjustmentQty(man)
			.build());
	}
}
