package com.craftwise.ui;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.PlayerCraftingStats;
import com.craftwise.model.ProfitReport;
import com.craftwise.tracker.GoalManager.CraftingGoal;
import com.craftwise.tracker.SessionTrackerManager.CraftingSessionRecord;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import com.craftwise.util.XPTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Clean Crafting overview panel displaying player progress, target level controls,
 * top recommendations, and live market summary.
 */
public class DashboardPanel extends CraftWiseTheme.ScrollableWidthPanel
{
	private final CraftingDataManager dataManager;
	private final Consumer<CraftingResult> onRecipeSelected;

	private final JLabel playerModeLabel = new JLabel();
	private final JLabel levelValueLabel = new JLabel();
	private final JLabel xpValueLabel = new JLabel();
	private final JLabel nextLvlValueLabel = new JLabel();
	private final JLabel targetXpValueLabel = new JLabel();
	private final JProgressBar nextLevelProgressBar = new JProgressBar(0, 100);
	private final JSpinner manualLevelSpinner = new JSpinner(new SpinnerNumberModel(70, 1, 99, 1));
	private final JSpinner targetLevelSpinner = new JSpinner(new SpinnerNumberModel(99, 1, 99, 1));
	private final CraftWiseTheme.CraftButton toggleManualBtn = new CraftWiseTheme.CraftButton("Set Manual");

	private final JPanel highlightsContainer = new JPanel();
	private final JLabel marketSummaryLabel = new JLabel();
	private final JLabel marketUpdateLabel = new JLabel();
	private final JPanel goalsContainer = new JPanel();
	private final JPanel sessionsContainer = new JPanel();
	private final JComboBox<CraftingRecipe> sessionRecipeCombo = new JComboBox<>();
	private final JTextField sessionQtyField = new JTextField("100");
	private final JTextField sessionCostField = new JTextField("");
	private final JTextField sessionSalesField = new JTextField("");
	private final CraftWiseTheme.CraftButton clearSessionsBtn = new CraftWiseTheme.CraftButton("Clear All");

	private boolean updatingControls = false;

	public DashboardPanel(CraftingDataManager dataManager, Consumer<CraftingResult> onRecipeSelected)
	{
		this.dataManager = dataManager;
		this.onRecipeSelected = onRecipeSelected;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));

		add(buildPlayerStatsCard());
		add(Box.createVerticalStrut(6));

		JLabel recHdr = CraftWiseTheme.createSectionHeader("Top Crafting Methods");
		recHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(recHdr);

		highlightsContainer.setLayout(new BoxLayout(highlightsContainer, BoxLayout.Y_AXIS));
		highlightsContainer.setOpaque(false);
		highlightsContainer.setAlignmentX(LEFT_ALIGNMENT);
		add(highlightsContainer);

		add(Box.createVerticalStrut(4));
		add(buildMarketStatusCard());

		add(Box.createVerticalStrut(6));
		JLabel goalsHdr = CraftWiseTheme.createSectionHeader("Saved Crafting Goals");
		goalsHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(goalsHdr);
		goalsContainer.setLayout(new BoxLayout(goalsContainer, BoxLayout.Y_AXIS));
		goalsContainer.setOpaque(false);
		goalsContainer.setAlignmentX(LEFT_ALIGNMENT);
		add(goalsContainer);

		add(Box.createVerticalStrut(6));
		JLabel sessHdr = CraftWiseTheme.createSectionHeader("Session Tracker");
		sessHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(sessHdr);
		add(buildSessionLoggerCard());
		add(Box.createVerticalStrut(4));
		sessionsContainer.setLayout(new BoxLayout(sessionsContainer, BoxLayout.Y_AXIS));
		sessionsContainer.setOpaque(false);
		sessionsContainer.setAlignmentX(LEFT_ALIGNMENT);
		add(sessionsContainer);

		refresh();
	}

	private JPanel buildPlayerStatsCard()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JPanel topRow = new JPanel(new BorderLayout(4, 0));
		topRow.setOpaque(false);
		JLabel hdr = new JLabel("Crafting Level");
		hdr.setFont(CraftWiseTheme.getSectionFont());
		hdr.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		playerModeLabel.setFont(CraftWiseTheme.getSmallBoldFont());
		topRow.add(hdr, BorderLayout.WEST);
		topRow.add(playerModeLabel, BorderLayout.EAST);
		card.add(topRow);
		card.add(Box.createVerticalStrut(6));

		JPanel statsGrid = new JPanel(new GridLayout(2, 2, 10, 4));
		statsGrid.setOpaque(false);
		statsGrid.add(metricBlock("Level", levelValueLabel));
		statsGrid.add(metricBlock("XP", xpValueLabel));
		statsGrid.add(metricBlock("To Next", nextLvlValueLabel));
		statsGrid.add(metricBlock("To Goal", targetXpValueLabel));
		card.add(statsGrid);
		card.add(Box.createVerticalStrut(6));

		nextLevelProgressBar.setStringPainted(true);
		nextLevelProgressBar.setFont(CraftWiseTheme.getSmallBoldFont());
		nextLevelProgressBar.setForeground(CraftWiseTheme.ACCENT_GOLD);
		nextLevelProgressBar.setBackground(CraftWiseTheme.BG_INPUT);
		nextLevelProgressBar.setBorder(BorderFactory.createLineBorder(CraftWiseTheme.BORDER_DEFAULT, 1));
		nextLevelProgressBar.setPreferredSize(new Dimension(100, 16));
		nextLevelProgressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		card.add(nextLevelProgressBar);
		card.add(Box.createVerticalStrut(6));

		JPanel spinnersRow = new JPanel(new GridLayout(1, 2, 8, 0));
		spinnersRow.setOpaque(false);

		JPanel targetWrap = new JPanel(new BorderLayout(4, 0));
		targetWrap.setOpaque(false);
		JLabel tLbl = new JLabel("Goal:");
		tLbl.setFont(CraftWiseTheme.getSmallFont());
		tLbl.setForeground(CraftWiseTheme.TEXT_MUTED);
		CraftWiseTheme.styleSpinner(targetLevelSpinner);
		targetWrap.add(tLbl, BorderLayout.WEST);
		targetWrap.add(targetLevelSpinner, BorderLayout.CENTER);

		JPanel manualWrap = new JPanel(new BorderLayout(4, 0));
		manualWrap.setOpaque(false);
		JLabel mLbl = new JLabel("Lvl:");
		mLbl.setFont(CraftWiseTheme.getSmallFont());
		mLbl.setForeground(CraftWiseTheme.TEXT_MUTED);
		CraftWiseTheme.styleSpinner(manualLevelSpinner);
		manualWrap.add(mLbl, BorderLayout.WEST);
		manualWrap.add(manualLevelSpinner, BorderLayout.CENTER);

		spinnersRow.add(targetWrap);
		spinnersRow.add(manualWrap);
		card.add(spinnersRow);
		card.add(Box.createVerticalStrut(5));

		toggleManualBtn.setPreferredSize(new Dimension(120, 24));
		JPanel toggleWrap = new JPanel(new BorderLayout());
		toggleWrap.setOpaque(false);
		toggleWrap.add(toggleManualBtn, BorderLayout.CENTER);
		card.add(toggleWrap);

		targetLevelSpinner.addChangeListener(e -> {
			if (!updatingControls)
			{
				int tgt = ((Number) targetLevelSpinner.getValue()).intValue();
				dataManager.setTargetLevel(tgt);
			}
		});

		manualLevelSpinner.addChangeListener(e -> {
			if (!updatingControls)
			{
				int lvl = ((Number) manualLevelSpinner.getValue()).intValue();
				dataManager.setManualLevel(lvl, true);
			}
		});

		toggleManualBtn.addActionListener(e -> {
			if (dataManager.isManualLevelOverrideActive())
			{
				dataManager.useLiveClientStats();
			}
			else
			{
				int lvl = ((Number) manualLevelSpinner.getValue()).intValue();
				dataManager.setManualLevel(lvl, true);
			}
		});

		return card;
	}

	private JPanel buildMarketStatusCard()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(LEFT_ALIGNMENT);

		marketSummaryLabel.setFont(CraftWiseTheme.getSmallFont());
		marketSummaryLabel.setForeground(CraftWiseTheme.TEXT_SECONDARY);
		marketUpdateLabel.setFont(CraftWiseTheme.getSmallFont());
		marketUpdateLabel.setForeground(CraftWiseTheme.TEXT_MUTED);

		card.add(marketSummaryLabel);
		card.add(Box.createVerticalStrut(2));
		card.add(marketUpdateLabel);
		return card;
	}

	private JPanel buildSessionLoggerCard()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setAlignmentX(LEFT_ALIGNMENT);

		for (CraftingRecipe r : CraftingDataRepository.getAllRecipes())
		{
			sessionRecipeCombo.addItem(r);
		}
		sessionRecipeCombo.setRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(
				javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof CraftingRecipe)
				{
					setText(((CraftingRecipe) value).getName());
				}
				return this;
			}
		});
		CraftWiseTheme.styleComboBox(sessionRecipeCombo);
		CraftWiseTheme.styleTextField(sessionQtyField);
		CraftWiseTheme.styleTextField(sessionCostField);
		CraftWiseTheme.styleTextField(sessionSalesField);

		card.add(CraftWiseTheme.stackedField("Recipe", sessionRecipeCombo));
		card.add(Box.createVerticalStrut(3));
		JPanel numRow = new JPanel(new GridLayout(1, 3, 4, 0));
		numRow.setOpaque(false);
		numRow.setAlignmentX(LEFT_ALIGNMENT);
		numRow.add(CraftWiseTheme.stackedField("Qty", sessionQtyField));
		numRow.add(CraftWiseTheme.stackedField("Cost (gp)", sessionCostField));
		numRow.add(CraftWiseTheme.stackedField("Sales (gp)", sessionSalesField));
		card.add(numRow);
		card.add(Box.createVerticalStrut(4));

		JPanel actionRow = new JPanel(new GridLayout(1, 2, 4, 0));
		actionRow.setOpaque(false);
		actionRow.setAlignmentX(LEFT_ALIGNMENT);

		CraftWiseTheme.CraftButton logBtn = new CraftWiseTheme.CraftButton("Log Session", true);
		clearSessionsBtn.setToolTipText("Remove all logged sessions from the tracker");
		logBtn.addActionListener(e -> {
			CraftingRecipe rec = (CraftingRecipe) sessionRecipeCombo.getSelectedItem();
			Long qtyVal = CurrencyFormatter.parseGpAmount(sessionQtyField.getText());
			long qty = qtyVal != null && qtyVal > 0L ? qtyVal : 1L;
			if (rec == null)
			{
				return;
			}
			CraftingResult eval = dataManager.getAllResults().stream()
				.filter(res -> res.getRecipe().getId().equals(rec.getId()))
				.findFirst()
				.orElse(null);

			long theoreticalCost = eval != null && eval.isPriceDataComplete()
				? Math.round(eval.getMaterialCostPerCraft() * qty) : 0L;
			long theoreticalGross = eval != null && eval.isPriceDataComplete()
				? Math.round(eval.getGrossRevenuePerCraft() * qty) : 0L;
			long theoreticalProfit = eval != null && eval.isPriceDataComplete()
				? Math.round(eval.getProfitPerCraft() * qty) : 0L;

			Long parsedCost = CurrencyFormatter.parseGpAmount(sessionCostField.getText());
			Long parsedSales = CurrencyFormatter.parseGpAmount(sessionSalesField.getText());
			long actualCost = parsedCost != null ? parsedCost : theoreticalCost;
			long actualSales = parsedSales != null ? parsedSales : theoreticalGross;
			long actualTax = eval != null && eval.isPriceDataComplete()
				? Math.round(eval.getGeTaxPerCraft() * qty) : 0L;

			PlayerCraftingStats stats = dataManager.getPlayerStats();
			long curXp = stats != null ? stats.getCurrentXp() : XPTable.getXpForLevel(70);
			int curLvl = stats != null ? stats.getRealLevel() : 70;
			long gainedXp = Math.round(rec.getXpPerAction() * qty);
			long endXp = curXp + gainedXp;
			int endLvl = XPTable.getLevelForXp(endXp);

			long now = System.currentTimeMillis();
			dataManager.getSessionTrackerManager().addSession(CraftingSessionRecord.builder()
				.recipeId(rec.getId())
				.recipeName(rec.getName())
				.startedAtMillis(now - 60_000L)
				.endedAtMillis(now)
				.startLevel(curLvl)
				.endLevel(endLvl)
				.startXp(curXp)
				.endXp(endXp)
				.itemsCrafted(qty)
				.actualMaterialCostGp(actualCost)
				.actualGrossSalesGp(actualSales)
				.actualGeTaxGp(actualTax)
				.theoreticalProfitGp(theoreticalProfit)
				.build());
			refresh();
		});
		clearSessionsBtn.addActionListener(e -> {
			dataManager.getSessionTrackerManager().clearSessions();
			refresh();
		});
		actionRow.add(logBtn);
		actionRow.add(clearSessionsBtn);
		card.add(actionRow);
		return card;
	}

	public void refresh()
	{
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();
		PlayerCraftingStats stats = dataManager.getPlayerStats();

		updatingControls = true;
		try
		{
			if (stats != null)
			{
				boolean isLive = stats.isLoggedIn() && !stats.isUsingManualFallback();
				playerModeLabel.setText(isLive ? stats.getPlayerName() : "Manual");
				playerModeLabel.setForeground(isLive
					? CraftWiseTheme.COLOR_PROFIT_GREEN
					: CraftWiseTheme.COLOR_WARNING_YELLOW);

				String lvlTxt = stats.getBoostedLevel() != stats.getRealLevel()
					? stats.getRealLevel() + " (" + stats.getBoostedLevel() + ")"
					: String.valueOf(stats.getRealLevel());
				levelValueLabel.setText(lvlTxt);
				xpValueLabel.setText(NumberFormatter.formatXp(stats.getCurrentXp(), compact));
				nextLvlValueLabel.setText(stats.getRealLevel() >= 99
					? "Max"
					: NumberFormatter.formatXp(stats.getXpToNextLevel(), compact));
				targetXpValueLabel.setText(stats.getXpToTarget() <= 0L
					? "Done"
					: NumberFormatter.formatXp(stats.getXpToTarget(), compact));

				int pct = (int) Math.round(stats.getProgressToNextLevelPercent());
				nextLevelProgressBar.setValue(pct);
				nextLevelProgressBar.setString(NumberFormatter.formatPercent(stats.getProgressToNextLevelPercent()) + " to next");

				targetLevelSpinner.setValue(stats.getTargetLevel());
				if (!dataManager.isManualLevelOverrideActive() && stats.isLoggedIn())
				{
					manualLevelSpinner.setValue(stats.getRealLevel());
				}
				toggleManualBtn.setText(dataManager.isManualLevelOverrideActive()
					? "Use Live RuneLite Level"
					: "Use Manual Level");
				toggleManualBtn.setActive(dataManager.isManualLevelOverrideActive());
			}
		}
		finally
		{
			updatingControls = false;
		}

		ProfitReport report = dataManager.getLatestReport();
		highlightsContainer.removeAll();
		if (report != null)
		{
			highlightsContainer.add(createHighlightCard("Best GP/hr", report.getBestProfitPerHour(), compact, true));
			highlightsContainer.add(Box.createVerticalStrut(4));
			highlightsContainer.add(createHighlightCard("Best XP/hr", report.getBestXpPerHour(), compact, false));
			highlightsContainer.add(Box.createVerticalStrut(4));
			highlightsContainer.add(createHighlightCard("Cheapest", report.getCheapestTrainingMethod(), compact, false));
			highlightsContainer.add(Box.createVerticalStrut(4));
			highlightsContainer.add(createHighlightCard("Balanced", report.getBestBalancedMethod(), compact, false));

			PriceCalculationMode mode = report.getPriceMode();
			String taxTxt = dataManager.getConfig() != null && dataManager.getConfig().includeGeTax() ? "2% tax" : "tax off";
			marketSummaryLabel.setText((mode != null ? mode.getLabel() : "Instant Buy/Sell")
				+ " · " + taxTxt + " · " + report.getCompleteRecipes() + "/" + report.getTotalRecipes() + " priced");

			long lastUpdate = dataManager.getWikiPriceClient() != null && dataManager.getWikiPriceClient().getPriceCache() != null
				? dataManager.getWikiPriceClient().getPriceCache().getLastPriceUpdateEpochSec()
				: 0L;
			long nowSec = Instant.now().getEpochSecond();
			marketUpdateLabel.setText("Updated " + NumberFormatter.formatRelativeAge(lastUpdate, nowSec)
				+ " · " + report.getProfitableUnlockedRecipes() + " profitable unlocked"
				+ (report.isUsingStaleCache() ? " (stale)" : ""));
			marketUpdateLabel.setForeground(report.isUsingStaleCache()
				? CraftWiseTheme.COLOR_WARNING_YELLOW
				: CraftWiseTheme.TEXT_MUTED);
		}
		highlightsContainer.revalidate();
		highlightsContainer.repaint();

		renderSavedGoals(compact);
		renderRecentSessions(compact);
	}

	private void renderSavedGoals(boolean compact)
	{
		goalsContainer.removeAll();
		PlayerCraftingStats stats = dataManager.getPlayerStats();
		long currentXp = stats != null ? stats.getCurrentXp() : 0L;
		List<CraftingGoal> goals = dataManager.getGoalManager().getGoals();
		if (goals.isEmpty())
		{
			JLabel empty = new JLabel("No saved goals yet (save one in Planner tab).");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			goalsContainer.add(empty);
		}
		else
		{
			for (CraftingGoal g : goals)
			{
				JPanel card = CraftWiseTheme.createCard();
				card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
				card.setAlignmentX(LEFT_ALIGNMENT);

				JPanel top = new JPanel(new BorderLayout(4, 0));
				top.setOpaque(false);
				JLabel t = new JLabel((g.isCompleted() ? "[Done] " : "") + g.getTitle());
				t.setFont(CraftWiseTheme.getSmallBoldFont());
				t.setForeground(g.isCompleted() ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.ACCENT_GOLD);
				CraftWiseTheme.CraftButton del = new CraftWiseTheme.CraftButton("X");
				del.setPreferredSize(new Dimension(24, 18));
				del.addActionListener(ev -> {
					dataManager.getGoalManager().removeGoal(g.getId());
					renderSavedGoals(compact);
				});
				top.add(t, BorderLayout.CENTER);
				top.add(del, BorderLayout.EAST);
				card.add(top);

				card.add(CraftWiseTheme.kvRow("Remaining XP",
					NumberFormatter.formatXp(g.getRemainingXp(currentXp), compact)
						+ " (" + NumberFormatter.formatPercent(g.getProgressPercent(currentXp)) + ")",
					CraftWiseTheme.TEXT_SECONDARY));
				goalsContainer.add(card);
				goalsContainer.add(Box.createVerticalStrut(3));
			}
		}
		goalsContainer.revalidate();
		goalsContainer.repaint();
	}

	private void renderRecentSessions(boolean compact)
	{
		sessionsContainer.removeAll();
		List<CraftingSessionRecord> list = dataManager.getSessionTrackerManager().getSessions();
		clearSessionsBtn.setEnabled(!list.isEmpty());
		if (list.isEmpty())
		{
			JLabel empty = new JLabel("No crafting sessions logged yet.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			sessionsContainer.add(empty);
		}
		else
		{
			sessionsContainer.add(CraftWiseTheme.kvRow("Total Tracked XP",
				NumberFormatter.formatXp(dataManager.getSessionTrackerManager().getTotalTrackedXp(), compact),
				CraftWiseTheme.TEXT_PRIMARY));
			long totPl = dataManager.getSessionTrackerManager().getTotalRealizedProfitGp();
			sessionsContainer.add(CraftWiseTheme.kvRow("Total Realized P/L",
				CurrencyFormatter.formatSignedGp(totPl, compact),
				totPl >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED));
			sessionsContainer.add(Box.createVerticalStrut(3));

			int show = Math.min(4, list.size());
			for (int i = 0; i < show; i++)
			{
				CraftingSessionRecord s = list.get(i);
				JPanel card = CraftWiseTheme.createCard();
				card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
				card.setAlignmentX(LEFT_ALIGNMENT);

				JPanel top = new JPanel(new BorderLayout(4, 0));
				top.setOpaque(false);
				JLabel hdr = new JLabel(s.getItemsCrafted() + "x " + s.getRecipeName()
					+ " (+" + NumberFormatter.formatXp(s.getXpGained(), compact) + ")");
				hdr.setFont(CraftWiseTheme.getSmallBoldFont());
				hdr.setForeground(CraftWiseTheme.TEXT_PRIMARY);
				hdr.setMinimumSize(new Dimension(0, 16));
				CraftWiseTheme.CraftButton del = new CraftWiseTheme.CraftButton("X");
				del.setToolTipText("Remove this session");
				del.setPreferredSize(new Dimension(24, 18));
				del.addActionListener(ev -> {
					dataManager.getSessionTrackerManager().removeSession(s.getId());
					renderRecentSessions(compact);
				});
				top.add(hdr, BorderLayout.CENTER);
				top.add(del, BorderLayout.EAST);
				card.add(top);

				long real = s.getRealizedNetProfitGp();
				Color col = real >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
				card.add(CraftWiseTheme.kvRow("Realized P/L", CurrencyFormatter.formatSignedGp(real, compact), col));
				card.add(CraftWiseTheme.kvRow("vs Theory", CurrencyFormatter.formatSignedGp(s.getVarianceVsTheoreticalGp(), compact), CraftWiseTheme.TEXT_MUTED));
				sessionsContainer.add(card);
				sessionsContainer.add(Box.createVerticalStrut(3));
			}
		}
		sessionsContainer.revalidate();
		sessionsContainer.repaint();
	}

	private JPanel createHighlightCard(String roleTag, CraftingResult r, boolean compact, boolean emphasizeProfit)
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BorderLayout(8, 0));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(28, 28));
		card.add(iconLabel, BorderLayout.WEST);

		JPanel center = new JPanel(new GridLayout(2, 1, 0, 2));
		center.setOpaque(false);
		center.setMinimumSize(new Dimension(0, 28));

		if (r == null)
		{
			JLabel tagLbl = new JLabel(roleTag);
			tagLbl.setFont(CraftWiseTheme.getSmallBoldFont());
			tagLbl.setForeground(CraftWiseTheme.TEXT_MUTED);
			JLabel none = new JLabel("Waiting for prices...");
			none.setFont(CraftWiseTheme.getSmallFont());
			none.setForeground(CraftWiseTheme.TEXT_MUTED);
			center.add(tagLbl);
			center.add(none);
			card.add(center, BorderLayout.CENTER);
			return card;
		}

		dataManager.loadItemIcon(r.getRecipe().getPrimaryItemId(), img ->
			iconLabel.setIcon(new ImageIcon(img.getScaledInstance(26, 26, java.awt.Image.SCALE_SMOOTH))));

		JLabel itemName = new JLabel(r.getRecipe().getName());
		itemName.setFont(CraftWiseTheme.getBoldFont());
		itemName.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		itemName.setMinimumSize(new Dimension(0, 14));

		JLabel roleAndLvl = new JLabel(roleTag + " · Lvl " + r.getRecipe().getRequiredLevel());
		roleAndLvl.setFont(CraftWiseTheme.getSmallFont());
		roleAndLvl.setForeground(CraftWiseTheme.TEXT_MUTED);
		roleAndLvl.setMinimumSize(new Dimension(0, 13));

		center.add(itemName);
		center.add(roleAndLvl);
		card.add(center, BorderLayout.CENTER);

		JPanel right = new JPanel(new GridLayout(2, 1, 0, 2));
		right.setOpaque(false);

		JLabel topMetric = new JLabel(emphasizeProfit
			? CurrencyFormatter.formatSignedGp(r.getProfitPerHour(), compact) + "/h"
			: NumberFormatter.formatXpPerHour(r.getXpPerHour(), compact));
		topMetric.setFont(CraftWiseTheme.getBoldFont());
		topMetric.setHorizontalAlignment(JLabel.RIGHT);
		topMetric.setForeground(emphasizeProfit
			? (r.getProfitPerHour() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED)
			: CraftWiseTheme.TEXT_PRIMARY);

		JLabel subMetric = new JLabel(CurrencyFormatter.formatSignedGp(r.getProfitPerCraft(), compact));
		subMetric.setFont(CraftWiseTheme.getSmallFont());
		subMetric.setHorizontalAlignment(JLabel.RIGHT);
		subMetric.setForeground(r.getProfitPerCraft() >= 0
			? CraftWiseTheme.COLOR_PROFIT_GREEN
			: CraftWiseTheme.COLOR_LOSS_RED);

		right.add(topMetric);
		right.add(subMetric);
		card.add(right, BorderLayout.EAST);

		card.setToolTipText(r.getRecipe().getName() + " (Lvl " + r.getRecipe().getRequiredLevel() + ") · "
			+ CurrencyFormatter.formatGpPerXp(r.getGpPerXp()));
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (onRecipeSelected != null)
				{
					onRecipeSelected.accept(r);
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				card.setBackground(CraftWiseTheme.BG_CARD_HOVER);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				card.setBackground(CraftWiseTheme.BG_CARD);
			}
		});
		return card;
	}

	private static JPanel metricBlock(String label, JLabel valueLabel)
	{
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.setOpaque(false);
		JLabel title = new JLabel(label);
		title.setFont(CraftWiseTheme.getSmallFont());
		title.setForeground(CraftWiseTheme.TEXT_MUTED);
		valueLabel.setFont(CraftWiseTheme.getBoldFont());
		valueLabel.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		valueLabel.setHorizontalAlignment(JLabel.RIGHT);
		p.add(title, BorderLayout.WEST);
		p.add(valueLabel, BorderLayout.EAST);
		return p;
	}
}
