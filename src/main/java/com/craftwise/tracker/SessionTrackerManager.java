package com.craftwise.tracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type LIST_TYPE = new TypeToken<List<CraftingSessionRecord>>()
	{
	}.getType();

	private final Path storageFile;
	private final List<CraftingSessionRecord> sessions = Collections.synchronizedList(new ArrayList<>());

	public SessionTrackerManager()
	{
		this(Paths.get(System.getProperty("user.home"), ".runelite", "craftwise", "sessions.json"));
	}

	public SessionTrackerManager(Path storageFile)
	{
		this.storageFile = storageFile;
		load();
	}

	public synchronized void load()
	{
		sessions.clear();
		if (storageFile == null || !Files.exists(storageFile))
		{
			return;
		}
		try
		{
			String json = new String(Files.readAllBytes(storageFile), StandardCharsets.UTF_8);
			List<CraftingSessionRecord> loaded = GSON.fromJson(json, LIST_TYPE);
			if (loaded != null)
			{
				sessions.addAll(loaded);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to load CraftWise sessions from {}", storageFile, ex);
		}
	}

	public synchronized void save()
	{
		if (storageFile == null)
		{
			return;
		}
		try
		{
			Path parent = storageFile.getParent();
			if (parent != null && !Files.exists(parent))
			{
				Files.createDirectories(parent);
			}
			List<CraftingSessionRecord> snapshot;
			synchronized (sessions)
			{
				snapshot = new ArrayList<>(sessions);
			}
			Files.write(storageFile, GSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise sessions to {}", storageFile, ex);
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
