package com.craftwise.tracker;

import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.DataIntegrityChecker;
import com.craftwise.data.DataIntegrityChecker.IntegrityReport;
import com.craftwise.tracker.GoalManager.CraftingGoal;
import com.craftwise.tracker.SessionTrackerManager.CraftingSessionRecord;
import com.craftwise.util.XPTable;
import com.google.gson.Gson;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TrackerPersistenceTest
{
	@Rule
	public final TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testSessionTrackerPersistenceAndMath() throws Exception
	{
		Path file = tempFolder.newFile("sessions.json").toPath();
		SessionTrackerManager mgr = new SessionTrackerManager(file, new Gson());

		CraftingSessionRecord rec = mgr.addSession(CraftingSessionRecord.builder()
			.recipeId("cut_ruby")
			.recipeName("Cutting Ruby")
			.startLevel(50)
			.endLevel(52)
			.startXp(XPTable.getXpForLevel(50))
			.endXp(XPTable.getXpForLevel(50) + 10_000L)
			.itemsCrafted(200L)
			.actualMaterialCostGp(200_000L)
			.actualGrossSalesGp(250_000L)
			.actualGeTaxGp(5_000L)
			.theoreticalProfitGp(40_000L)
			.build());

		Assert.assertEquals(10_000L, rec.getXpGained());
		Assert.assertEquals(45_000L, rec.getRealizedNetProfitGp());
		Assert.assertEquals(5_000L, rec.getVarianceVsTheoreticalGp());

		SessionTrackerManager reloaded = new SessionTrackerManager(file, new Gson());
		Assert.assertEquals(1, reloaded.getSessions().size());
		Assert.assertEquals(45_000L, reloaded.getTotalRealizedProfitGp());
		Assert.assertEquals(10_000L, reloaded.getTotalTrackedXp());

		Assert.assertTrue(reloaded.removeSession(rec.getId()));
		Assert.assertEquals(0, reloaded.getSessions().size());

		reloaded.addSession(CraftingSessionRecord.builder().recipeId("cut_ruby").recipeName("Cut Ruby").itemsCrafted(10L).build());
		reloaded.addSession(CraftingSessionRecord.builder().recipeId("cut_diamond").recipeName("Cut Diamond").itemsCrafted(5L).build());
		Assert.assertEquals(2, reloaded.clearSessions());
		Assert.assertEquals(0, reloaded.getSessions().size());
		Assert.assertEquals(0, new SessionTrackerManager(file, new Gson()).getSessions().size());
	}

	@Test
	public void testGoalManagerPersistenceAndProgress() throws Exception
	{
		Path file = tempFolder.newFile("goals.json").toPath();
		GoalManager gm = new GoalManager(file, new Gson());

		CraftingGoal goal = gm.addGoal(CraftingGoal.builder()
			.title("99 Crafting via Air Bstaff")
			.startLevel(70)
			.targetLevel(99)
			.preferredRecipeId("air_battlestaff")
			.preferredRecipeName("Air battlestaff")
			.budgetGp(25_000_000L)
			.build());

		Assert.assertEquals(XPTable.getXpForLevel(70), goal.getStartXp());
		Assert.assertEquals(XPTable.getXpForLevel(99), goal.getTargetXp());
		Assert.assertTrue(goal.getRemainingXp(XPTable.getXpForLevel(70)) > 12_000_000L);
		Assert.assertEquals(100.0, goal.getProgressPercent(XPTable.getXpForLevel(99)), 0.01);

		GoalManager reloaded = new GoalManager(file, new Gson());
		Assert.assertEquals(1, reloaded.getGoals().size());
		Assert.assertTrue(reloaded.toggleCompleted(goal.getId()));
		Assert.assertTrue(reloaded.getGoals().get(0).isCompleted());
	}

	@Test
	public void testDataIntegrityCheckerPassesAllRepositoryRecipes()
	{
		IntegrityReport report = DataIntegrityChecker.check(CraftingDataRepository.getAllRecipes());
		Assert.assertTrue("Repository integrity errors: " + report.getErrors(), report.isPassed());
		Assert.assertTrue(report.getTotalRecipes() >= 90);
		Assert.assertTrue(report.getVerifiedCount() >= 85);
	}
}
