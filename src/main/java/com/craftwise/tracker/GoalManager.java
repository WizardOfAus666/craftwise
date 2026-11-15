package com.craftwise.tracker;

import com.craftwise.util.XPTable;
import com.google.gson.Gson;
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
 * Phase 13: Saved Crafting Goals with local JSON persistence.
 * Tracks target Crafting level/XP, preferred recipe, capital budget,
 * and remaining progress against current XP.
 */
@Slf4j
public class GoalManager
{
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CraftingGoal
	{
		private String id;
		private String title;
		private int startLevel;
		private long startXp;
		private int targetLevel;
		private long targetXp;
		private String preferredRecipeId;
		private String preferredRecipeName;
		private long budgetGp;
		private long createdAtMillis;
		private boolean completed;

		public long getRemainingXp(long currentXp)
		{
			return Math.max(0L, targetXp - Math.max(0L, currentXp));
		}

		public double getProgressPercent(long currentXp)
		{
			long span = Math.max(1L, targetXp - startXp);
			long gained = Math.max(0L, currentXp - startXp);
			return Math.min(100.0, (gained * 100.0) / span);
		}
	}

	private static final Type LIST_TYPE = new TypeToken<List<CraftingGoal>>()
	{
	}.getType();

	private final Path storageFile;
	private final Gson gson;
	private final List<CraftingGoal> goals = Collections.synchronizedList(new ArrayList<>());

	public GoalManager(Gson gson)
	{
		this(Paths.get(System.getProperty("user.home"), ".runelite", "craftwise", "goals.json"), gson);
	}

	public GoalManager(Path storageFile, Gson gson)
	{
		this.storageFile = storageFile;
		this.gson = gson.newBuilder().setPrettyPrinting().create();
		load();
	}

	public synchronized void load()
	{
		goals.clear();
		if (storageFile == null || !Files.exists(storageFile))
		{
			return;
		}
		try
		{
			String json = new String(Files.readAllBytes(storageFile), StandardCharsets.UTF_8);
			List<CraftingGoal> loaded = gson.fromJson(json, LIST_TYPE);
			if (loaded != null)
			{
				goals.addAll(loaded);
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to load CraftWise goals from {}", storageFile, ex);
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
			List<CraftingGoal> snapshot;
			synchronized (goals)
			{
				snapshot = new ArrayList<>(goals);
			}
			Files.write(storageFile, gson.toJson(snapshot).getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException ex)
		{
			log.warn("Failed to save CraftWise goals to {}", storageFile, ex);
		}
	}

	public synchronized CraftingGoal addGoal(CraftingGoal goal)
	{
		if (goal == null)
		{
			return null;
		}
		if (goal.getId() == null || goal.getId().trim().isEmpty())
		{
			goal.setId(UUID.randomUUID().toString());
		}
		if (goal.getCreatedAtMillis() <= 0L)
		{
			goal.setCreatedAtMillis(System.currentTimeMillis());
		}
		int clampedStart = Math.max(1, Math.min(99, goal.getStartLevel() > 0 ? goal.getStartLevel() : 1));
		int clampedTarget = Math.max(clampedStart, Math.min(99, goal.getTargetLevel() > 0 ? goal.getTargetLevel() : 99));
		goal.setStartLevel(clampedStart);
		goal.setTargetLevel(clampedTarget);
		if (goal.getStartXp() <= 0L)
		{
			goal.setStartXp(XPTable.getXpForLevel(clampedStart));
		}
		if (goal.getTargetXp() <= 0L)
		{
			goal.setTargetXp(XPTable.getXpForLevel(clampedTarget));
		}
		goals.add(0, goal);
		save();
		return goal;
	}

	public synchronized boolean toggleCompleted(String goalId)
	{
		synchronized (goals)
		{
			for (CraftingGoal g : goals)
			{
				if (goalId != null && goalId.equals(g.getId()))
				{
					g.setCompleted(!g.isCompleted());
					save();
					return true;
				}
			}
		}
		return false;
	}

	public synchronized boolean removeGoal(String goalId)
	{
		if (goalId == null)
		{
			return false;
		}
		boolean removed;
		synchronized (goals)
		{
			removed = goals.removeIf(g -> goalId.equals(g.getId()));
		}
		if (removed)
		{
			save();
		}
		return removed;
	}

	public List<CraftingGoal> getGoals()
	{
		synchronized (goals)
		{
			return Collections.unmodifiableList(new ArrayList<>(goals));
		}
	}
}
