package com.craftwise.bank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type OVERRIDE_TYPE = new TypeToken<Map<Integer, Long>>()
	{
	}.getType();

	private final Path manualFile;
	private volatile BankDataState bankDataState = BankDataState.UNAVAILABLE;
	private volatile long lastBankScanEpochSec = 0L;
	private volatile long lastInventoryScanEpochSec = 0L;

	private final Map<Integer, Long> bankQuantities = new LinkedHashMap<>();
	private final Map<Integer, Long> invUnnotedQuantities = new LinkedHashMap<>();
	private final Map<Integer, Long> invNotedQuantities = new LinkedHashMap<>();
	private final Map<Integer, String> itemNames = new LinkedHashMap<>();
	private final Map<Integer, Long> manualOwnedOverrides = new LinkedHashMap<>();

	public OwnedMaterialSnapshot()
	{
		this(Paths.get(System.getProperty("user.home"), ".runelite", "craftwise", "manual_owned.json"));
	}

	public OwnedMaterialSnapshot(Path manualFile)
	{
		this.manualFile = manualFile;
		loadManualOverrides();
	}

	public synchronized void loadManualOverrides()
	{
		manualOwnedOverrides.clear();
		if (manualFile == null || !Files.exists(manualFile))
		{
			return;
		}
		try
		{
			String json = new String(Files.readAllBytes(manualFile), StandardCharsets.UTF_8);
			Map<Integer, Long> loaded = GSON.fromJson(json, OVERRIDE_TYPE);
			if (loaded != null)
			{
				manualOwnedOverrides.putAll(loaded);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to load CraftWise manual owned quantities from {}", manualFile, ex);
		}
	}

	public synchronized void saveManualOverrides()
	{
		if (manualFile == null)
		{
			return;
		}
		try
		{
			Path parent = manualFile.getParent();
			if (parent != null && !Files.exists(parent))
			{
				Files.createDirectories(parent);
			}
			Files.write(manualFile, GSON.toJson(new LinkedHashMap<>(manualOwnedOverrides)).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise manual owned quantities to {}", manualFile, ex);
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
