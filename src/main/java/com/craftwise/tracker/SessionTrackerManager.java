package com.craftwise.tracker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Phase 12: Crafting Session Tracker with local JSON persistence.
 * Tracks realized Crafting XP, items crafted, actual material cost, actual sale proceeds,
 * and compares realized profit vs theoretical market estimate.
 */
@Slf4j
public class SessionTrackerManager
{
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CraftingSessionRecord
	{
		private String id;
		private String recipeId;
		private String recipeName;
		private long startedAtMillis;
		private long endedAtMillis;
		private int startLevel;
		private int endLevel;
		private long startXp;
		private long endXp;
		private long itemsCrafted;
		private long actualMaterialCostGp;
		private long actualGrossSalesGp;
		private long actualGeTaxGp;
		private long theoreticalProfitGp;
		private String notes;

		public long getXpGained()
		{
			return Math.max(0L, endXp - startXp);
		}

		public long getRealizedNetProfitGp()
		{
			return (actualGrossSalesGp - actualGeTaxGp) - actualMaterialCostGp;
		}

		public long getVarianceVsTheoreticalGp()
		{
			return getRealizedNetProfitGp() - theoreticalProfitGp;
		}

		public double getDurationHours()
		{
			long dt = Math.max(0L, endedAtMillis - startedAtMillis);
			return dt / 3_600_000.0;
		}

		public double getRealizedXpPerHour()
		{
			double hrs = getDurationHours();
			return hrs > 1e-6 ? getXpGained() / hrs : 0.0;
		}
	}

	private static final Type LIST_TYPE = new TypeToken<List<CraftingSessionRecord>>()
	{
	}.getType();

	private volatile Filepath storageFile;
	private final Gson gson;
	private final List<CraftingSessionRecord> sessions = Collections.synchronizedList(new ArrayList<>());

	public SessionTrackerManager(Gson gson)
	{
		this(null, gson);
	}

	public SessionTrackerManager(Filepath storageFile, Gson gson)
	{
		this.storageFile = storageFile;
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		load();
	}

	public synchronized void initializeStorage(Filepath storageFile)
	{
		this.storageFile = storageFile;
		load();
	}

	public synchronized void load()
	{
		sessions.clear();
		Filepath file = this.storageFile;
		if (file == null || !file.exists())
		{
			return;
		}
		try (Reader reader = file.openReader())
		{
			List<CraftingSessionRecord> loaded = gson.fromJson(reader, LIST_TYPE);
			if (loaded != null)
			{
				sessions.addAll(loaded);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to load CraftWise sessions from {}", file, ex);
		}
	}

	public synchronized void save()
	{
		Filepath file = this.storageFile;
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
			List<CraftingSessionRecord> snapshot;
			synchronized (sessions)
			{
				snapshot = new ArrayList<>(sessions);
			}
			file.write(gson.toJson(snapshot));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise sessions to {}", file, ex);
		}
	}

	public synchronized CraftingSessionRecord addSession(CraftingSessionRecord record)
	{
		if (record == null)
		{
			return null;
		}
		if (record.getId() == null || record.getId().trim().isEmpty())
		{
			record.setId(UUID.randomUUID().toString());
		}
		if (record.getStartedAtMillis() <= 0L)
		{
			record.setStartedAtMillis(System.currentTimeMillis());
		}
		if (record.getEndedAtMillis() < record.getStartedAtMillis())
		{
			record.setEndedAtMillis(System.currentTimeMillis());
		}
		sessions.add(0, record);
		save();
		return record;
	}

	public synchronized boolean removeSession(String sessionId)
	{
		if (sessionId == null)
		{
			return false;
		}
		boolean removed;
		synchronized (sessions)
		{
			removed = sessions.removeIf(s -> sessionId.equals(s.getId()));
		}
		if (removed)
		{
			save();
		}
		return removed;
	}

	public synchronized int clearSessions()
	{
		int count;
		synchronized (sessions)
		{
			count = sessions.size();
			if (count == 0)
			{
				return 0;
			}
			sessions.clear();
		}
		save();
		return count;
	}

	public List<CraftingSessionRecord> getSessions()
	{
		synchronized (sessions)
		{
			return Collections.unmodifiableList(new ArrayList<>(sessions));
		}
	}

	public long getTotalTrackedXp()
	{
		long sum = 0L;
		synchronized (sessions)
		{
			for (CraftingSessionRecord r : sessions)
			{
				sum += r.getXpGained();
			}
		}
		return sum;
	}

	public long getTotalRealizedProfitGp()
	{
		long sum = 0L;
		synchronized (sessions)
		{
			for (CraftingSessionRecord r : sessions)
			{
				sum += r.getRealizedNetProfitGp();
			}
		}
		return sum;
	}
}
