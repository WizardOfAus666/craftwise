package com.craftwise.ui;

import com.craftwise.calculator.BudgetCalculator;
import com.craftwise.calculator.BudgetCalculator.BudgetMethodEvaluation;
import com.craftwise.calculator.ShoppingListCalculator;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListItem;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListSummary;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingSortOrder;
import com.craftwise.calculator.TrainingOptimizer;
import com.craftwise.calculator.TrainingPlanComparisonCalculator;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.ComparedPlan;
import com.craftwise.calculator.TrainingPlanComparisonCalculator.OptimizerOptions;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.PlayerCraftingStats;
import com.craftwise.model.TrainingPlan;
import com.craftwise.model.TrainingPlan.OptimizationObjective;
import com.craftwise.model.TrainingPlan.TrainingStage;
import com.craftwise.tracker.GoalManager.CraftingGoal;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import com.craftwise.util.XPTable;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Interactive Training Planner and Budget Optimiser with responsive zero-clipping layout.
 */
public class TrainingPlannerPanel extends CraftWiseTheme.ScrollableWidthPanel
{
	private final CraftingDataManager dataManager;
	private final Consumer<CraftingResult> onRecipeSelected;

	private final JSpinner startLvlSpinner = new JSpinner(new SpinnerNumberModel(70, 1, 98, 1));
	private final JSpinner targetLvlSpinner = new JSpinner(new SpinnerNumberModel(99, 2, 99, 1));
	private final JSpinner boostSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 5, 1));
	private final JCheckBox membersCheck = new JCheckBox("Members (P2P)", true);
	private final JCheckBox questUnlockCheck = new JCheckBox("Include Quest Reqs", true);
	private final JCheckBox miscUnlockCheck = new JCheckBox("Include Unlocks", true);
	private final JTextField budgetField = new JTextField("5M");
	private final JTextField maxLossField = new JTextField("");
	private final JTextField minXpHrField = new JTextField("");
	private final JComboBox<OptimizationObjective> objectiveCombo = new JComboBox<>(OptimizationObjective.values());
	private final JComboBox<ShoppingSortOrder> shoppingSortCombo = new JComboBox<>(ShoppingSortOrder.values());
	private final JPanel targetBtns = new JPanel(new GridLayout(2, 3, 2, 2));

	private final JPanel summaryPanel = new JPanel();
	private final JPanel comparedPlansPanel = new JPanel();
	private final JPanel stagesPanel = new JPanel();
	private final JPanel shoppingListPanel = new JPanel();
	private final JPanel budgetMethodsPanel = new JPanel();

	private boolean wideMode = false;
	private boolean updating = false;
	private TrainingPlan currentDisplayedPlan;
	private ShoppingListSummary currentShoppingSummary;

	public TrainingPlannerPanel(CraftingDataManager dataManager, Consumer<CraftingResult> onRecipeSelected)
	{
		this.dataManager = dataManager;
		this.onRecipeSelected = onRecipeSelected;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));

		add(buildControlsBand());
		add(Box.createVerticalStrut(4));

		summaryPanel.setLayout(new BoxLayout(summaryPanel, BoxLayout.Y_AXIS));
		summaryPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		summaryPanel.setBorder(CraftWiseTheme.CARD_BORDER_GOLD);
		summaryPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(summaryPanel);
		add(Box.createVerticalStrut(4));

		JLabel cmpHdr = CraftWiseTheme.createSectionHeader("Plans A-E Comparison");
		cmpHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(cmpHdr);
		comparedPlansPanel.setLayout(new BoxLayout(comparedPlansPanel, BoxLayout.Y_AXIS));
		comparedPlansPanel.setOpaque(false);
		comparedPlansPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(comparedPlansPanel);
		add(Box.createVerticalStrut(4));

		JLabel stagesHdr = CraftWiseTheme.createSectionHeader("Progression Stages");
		stagesHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(stagesHdr);

		stagesPanel.setLayout(new BoxLayout(stagesPanel, BoxLayout.Y_AXIS));
		stagesPanel.setOpaque(false);
		stagesPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(stagesPanel);
		add(Box.createVerticalStrut(4));

		JLabel shopHdr = CraftWiseTheme.createSectionHeader("Material Shopping List");
		shopHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(shopHdr);
		shoppingListPanel.setLayout(new BoxLayout(shoppingListPanel, BoxLayout.Y_AXIS));
		shoppingListPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		shoppingListPanel.setBorder(CraftWiseTheme.CARD_BORDER);
		shoppingListPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(shoppingListPanel);
		add(Box.createVerticalStrut(4));

		JLabel budgetHdr = CraftWiseTheme.createSectionHeader("Top Single Methods in Budget");
		budgetHdr.setAlignmentX(LEFT_ALIGNMENT);
		add(budgetHdr);

		budgetMethodsPanel.setLayout(new BoxLayout(budgetMethodsPanel, BoxLayout.Y_AXIS));
		budgetMethodsPanel.setOpaque(false);
		budgetMethodsPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(budgetMethodsPanel);

		syncInitialFromManager();
		refresh();
	}

	public void setWideMode(boolean wide)
	{
		if (this.wideMode == wide)
		{
			return;
		}
		this.wideMode = wide;
		targetBtns.setLayout(wide ? new GridLayout(1, 6, 3, 0) : new GridLayout(2, 3, 2, 2));
		targetBtns.revalidate();
	}

	private JPanel buildControlsBand()
	{
		JPanel band = CraftWiseTheme.createCard();
		band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
		band.setAlignmentX(LEFT_ALIGNMENT);

		JPanel lvlRow = new JPanel(new GridLayout(1, 2, 6, 0));
		lvlRow.setOpaque(false);
		lvlRow.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.styleSpinner(startLvlSpinner);
		CraftWiseTheme.styleSpinner(targetLvlSpinner);
		lvlRow.add(CraftWiseTheme.stackedField("Start Level", startLvlSpinner));
		lvlRow.add(CraftWiseTheme.stackedField("Target Level", targetLvlSpinner));
		band.add(lvlRow);
		band.add(Box.createVerticalStrut(4));

		targetBtns.setOpaque(false);
		targetBtns.setAlignmentX(LEFT_ALIGNMENT);
		for (int lvl : new int[]{70, 80, 85, 90, 95, 99})
		{
			CraftWiseTheme.CraftButton b = new CraftWiseTheme.CraftButton("Lvl " + lvl);
			b.setPreferredSize(new Dimension(48, 20));
			b.addActionListener(e -> {
				targetLvlSpinner.setValue(lvl);
				recalculatePlan();
			});
			targetBtns.add(b);
		}
		band.add(targetBtns);
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleTextField(budgetField);
		band.add(inlineRow("Budget (gp):", budgetField));
		band.add(Box.createVerticalStrut(3));

		JPanel budgetBtns = new JPanel(new GridLayout(2, 4, 2, 2));
		budgetBtns.setOpaque(false);
		budgetBtns.setAlignmentX(LEFT_ALIGNMENT);
		String[] presets = {"100K", "500K", "1M", "5M", "10M", "50M", "100M"};
		for (String p : presets)
		{
			CraftWiseTheme.CraftButton b = new CraftWiseTheme.CraftButton(p);
			b.setPreferredSize(new Dimension(44, 20));
			b.addActionListener(e -> {
				budgetField.setText(p);
				recalculatePlan();
			});
			budgetBtns.add(b);
		}
		CraftWiseTheme.CraftButton syncBtn = new CraftWiseTheme.CraftButton("My Lvl", true);
		syncBtn.setPreferredSize(new Dimension(44, 20));
		syncBtn.addActionListener(e -> {
			syncInitialFromManager();
			recalculatePlan();
		});
		budgetBtns.add(syncBtn);
		band.add(budgetBtns);
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleComboBox(objectiveCombo);
		band.add(CraftWiseTheme.stackedField("Objective", objectiveCombo));
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleTextField(maxLossField);
		CraftWiseTheme.styleTextField(minXpHrField);
		JPanel extraRow = new JPanel(new GridLayout(1, 2, 6, 0));
		extraRow.setOpaque(false);
		extraRow.setAlignmentX(LEFT_ALIGNMENT);
		extraRow.add(CraftWiseTheme.stackedField("Max Loss (gp)", maxLossField));
		extraRow.add(CraftWiseTheme.stackedField("Min XP/hr", minXpHrField));
		band.add(extraRow);
		band.add(Box.createVerticalStrut(4));

		JPanel rangePresetsRow = new JPanel(new GridLayout(1, 4, 2, 0));
		rangePresetsRow.setOpaque(false);
		rangePresetsRow.setAlignmentX(LEFT_ALIGNMENT);
		rangePresetsRow.add(rangePresetBtn("Cur->99", -1, 99));
		rangePresetsRow.add(rangePresetBtn("70->99", 70, 99));
		rangePresetsRow.add(rangePresetBtn("80->99", 80, 99));
		rangePresetsRow.add(rangePresetBtn("90->99", 90, 99));
		band.add(rangePresetsRow);
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleSpinner(boostSpinner);
		band.add(inlineRow("Temp Boost (+):", boostSpinner));
		band.add(Box.createVerticalStrut(2));

		stylePlannerCheck(membersCheck);
		stylePlannerCheck(questUnlockCheck);
		stylePlannerCheck(miscUnlockCheck);
		JPanel accessRow = new JPanel(new GridLayout(3, 1, 0, 1));
		accessRow.setOpaque(false);
		accessRow.setAlignmentX(LEFT_ALIGNMENT);
		accessRow.add(membersCheck);
		accessRow.add(questUnlockCheck);
		accessRow.add(miscUnlockCheck);
		band.add(accessRow);

		startLvlSpinner.addChangeListener(e -> { if (!updating) recalculatePlan(); });
		targetLvlSpinner.addChangeListener(e -> { if (!updating) recalculatePlan(); });
		boostSpinner.addChangeListener(e -> {
			if (!updating)
			{
				dataManager.setTemporaryBoost(((Number) boostSpinner.getValue()).intValue());
				recalculatePlan();
			}
		});
		membersCheck.addActionListener(e -> {
			dataManager.setMembersAllowed(membersCheck.isSelected());
			recalculatePlan();
		});
		questUnlockCheck.addActionListener(e -> {
			dataManager.setIncludeQuestLocked(questUnlockCheck.isSelected());
			recalculatePlan();
		});
		miscUnlockCheck.addActionListener(e -> {
			dataManager.setIncludeUnlockRequired(miscUnlockCheck.isSelected());
			recalculatePlan();
		});
		CraftWiseTheme.styleComboBox(shoppingSortCombo);
		shoppingSortCombo.addActionListener(e -> {
			if (!updating && currentDisplayedPlan != null)
			{
				renderShoppingList(currentDisplayedPlan, dataManager.getConfig() == null || dataManager.getConfig().compactNumbers());
			}
		});
		objectiveCombo.addActionListener(e -> { if (!updating) recalculatePlan(); });

		DocumentListener dl = new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { if (!updating) recalculatePlan(); }
			@Override public void removeUpdate(DocumentEvent e) { if (!updating) recalculatePlan(); }
			@Override public void changedUpdate(DocumentEvent e) { if (!updating) recalculatePlan(); }
		};
		budgetField.getDocument().addDocumentListener(dl);
		maxLossField.getDocument().addDocumentListener(dl);
		minXpHrField.getDocument().addDocumentListener(dl);

		return band;
	}

	private void syncInitialFromManager()
	{
		updating = true;
		try
		{
			PlayerCraftingStats stats = dataManager.getPlayerStats();
			if (stats != null)
			{
				startLvlSpinner.setValue(Math.min(98, Math.max(1, stats.getRealLevel())));
				targetLvlSpinner.setValue(Math.max(2, stats.getTargetLevel()));
			}
			long b = dataManager.getBudgetGp();
			if (b > 0L)
			{
				budgetField.setText(NumberFormatter.formatCompact(b, true));
			}
		}
		finally
		{
			updating = false;
		}
	}

	public void refresh()
	{
		recalculatePlan();
	}

	private void recalculatePlan()
	{
		if (updating)
		{
			return;
		}
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();

		int startLvl = ((Number) startLvlSpinner.getValue()).intValue();
		int targetLvl = ((Number) targetLvlSpinner.getValue()).intValue();
		if (targetLvl <= startLvl)
		{
			targetLvl = Math.min(99, startLvl + 1);
		}

		PlayerCraftingStats stats = dataManager.getPlayerStats();
		long startXp = (stats != null && stats.getRealLevel() == startLvl)
			? stats.getCurrentXp()
			: XPTable.getXpForLevel(startLvl);

		Long parsedBudget = CurrencyFormatter.parseGpAmount(budgetField.getText());
		long budget = parsedBudget != null ? parsedBudget : 0L;

		Long parsedMaxLoss = CurrencyFormatter.parseGpAmount(maxLossField.getText());
		long maxLoss = parsedMaxLoss != null ? parsedMaxLoss : -1L;

		Long parsedMinXp = CurrencyFormatter.parseGpAmount(minXpHrField.getText());
		double minXpHr = parsedMinXp != null ? parsedMinXp : 0.0;

		OptimizationObjective obj = (OptimizationObjective) objectiveCombo.getSelectedItem();

		TrainingPlan plan = TrainingOptimizer.optimize(
			dataManager.getAllResults(),
			startXp,
			targetLvl,
			budget,
			obj,
			maxLoss,
			minXpHr
		);
		this.currentDisplayedPlan = plan;

		renderSummary(plan, compact);
		renderStages(plan, compact);
		renderShoppingList(plan, compact);

		int boost = ((Number) boostSpinner.getValue()).intValue();
		List<ComparedPlan> compared = TrainingPlanComparisonCalculator.buildSideBySidePlans(
			dataManager.getAllResults(),
			OptimizerOptions.builder()
				.currentLevel(startLvl)
				.currentXp(startXp)
				.targetLevel(targetLvl)
				.temporaryBoost(boost)
				.membersAllowed(membersCheck.isSelected())
				.includeQuestLocked(questUnlockCheck.isSelected())
				.includeUnlockRequired(miscUnlockCheck.isSelected())
				.availableBudgetGp(budget)
				.build()
		);
		renderComparedPlans(compared, compact);

		List<BudgetMethodEvaluation> topSingle = BudgetCalculator.evaluateWithinBudget(
			dataManager.getAllResults(),
			startLvl,
			plan.getXpRequired(),
			budget,
			obj,
			maxLoss,
			minXpHr
		);
		renderBudgetMethods(topSingle, compact);
	}

	private void renderSummary(TrainingPlan plan, boolean compact)
	{
		summaryPanel.removeAll();
		JLabel hdr = new JLabel("Plan Summary (Lvl " + plan.getStartLevel() + " -> " + plan.getTargetLevel() + ")");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		summaryPanel.add(hdr);

		JLabel statusLbl = new JLabel("<html>" + plan.getStatusMessage() + "</html>");
		statusLbl.setFont(CraftWiseTheme.getSmallFont());
		statusLbl.setForeground(plan.isFeasible()
			? CraftWiseTheme.COLOR_PROFIT_GREEN
			: CraftWiseTheme.COLOR_WARNING_YELLOW);
		statusLbl.setBorder(BorderFactory.createEmptyBorder(2, 0, 3, 0));
		summaryPanel.add(statusLbl);

		Color plColor = plan.getTotalNetProfit() >= 0
			? CraftWiseTheme.COLOR_PROFIT_GREEN
			: CraftWiseTheme.COLOR_LOSS_RED;

		summaryPanel.add(CraftWiseTheme.kvRow("XP Needed", NumberFormatter.formatXp(plan.getXpRequired(), compact), CraftWiseTheme.TEXT_PRIMARY));
		summaryPanel.add(CraftWiseTheme.kvRow("Total Crafts", NumberFormatter.formatInt(plan.getTotalActions()), CraftWiseTheme.TEXT_PRIMARY));
		summaryPanel.add(CraftWiseTheme.kvRow("Mat Spend", CurrencyFormatter.formatGp(Math.round(plan.getTotalMaterialCost()), compact), CraftWiseTheme.TEXT_PRIMARY));
		summaryPanel.add(CraftWiseTheme.kvRow("Expected Rev", CurrencyFormatter.formatGp(Math.round(plan.getTotalGrossRevenue()), compact), CraftWiseTheme.TEXT_PRIMARY));
		summaryPanel.add(CraftWiseTheme.kvRow("GE Tax", CurrencyFormatter.formatGp(Math.round(plan.getTotalGeTax()), compact), CraftWiseTheme.COLOR_WARNING_YELLOW));
		summaryPanel.add(CraftWiseTheme.kvRow("Net P/L", CurrencyFormatter.formatSignedGp(plan.getTotalNetProfit(), compact), plColor));
		summaryPanel.add(CraftWiseTheme.kvRow("Avg XP/hr", NumberFormatter.formatXpPerHour(plan.getAverageXpPerHour(), compact), CraftWiseTheme.TEXT_SECONDARY));
		summaryPanel.add(CraftWiseTheme.kvRow("Est. Time", NumberFormatter.formatHours(plan.getTotalEstimatedHours()), CraftWiseTheme.TEXT_SECONDARY));

		CraftWiseTheme.CraftButton saveGoalBtn = new CraftWiseTheme.CraftButton("Save as Goal", true);
		saveGoalBtn.setAlignmentX(LEFT_ALIGNMENT);
		saveGoalBtn.addActionListener(ev -> {
			String firstRecipeId = plan.getStages().isEmpty() ? "" : plan.getStages().get(0).getRecipe().getId();
			String firstRecipeName = plan.getStages().isEmpty() ? "Mixed Plan" : plan.getStages().get(0).getRecipe().getName();
			Long parsedB = CurrencyFormatter.parseGpAmount(budgetField.getText());
			dataManager.getGoalManager().addGoal(CraftingGoal.builder()
				.title("Crafting " + plan.getStartLevel() + " -> " + plan.getTargetLevel() + " (" + firstRecipeName + ")")
				.startLevel(plan.getStartLevel())
				.startXp(XPTable.getXpForLevel(plan.getStartLevel()))
				.targetLevel(plan.getTargetLevel())
				.targetXp(XPTable.getXpForLevel(plan.getTargetLevel()))
				.preferredRecipeId(firstRecipeId)
				.preferredRecipeName(firstRecipeName)
				.budgetGp(parsedB != null ? parsedB : 0L)
				.build());
			saveGoalBtn.setText("Saved!");
			dataManager.notifyListenersPublic();
		});
		summaryPanel.add(Box.createVerticalStrut(4));
		summaryPanel.add(saveGoalBtn);

		summaryPanel.revalidate();
		summaryPanel.repaint();
	}

	private void renderComparedPlans(List<ComparedPlan> plans, boolean compact)
	{
		comparedPlansPanel.removeAll();
		if (plans == null || plans.isEmpty())
		{
			JLabel empty = new JLabel("No Plans A-E available.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			comparedPlansPanel.add(empty);
		}
		else
		{
			for (ComparedPlan cp : plans)
			{
				JPanel card = CraftWiseTheme.createCard();
				card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
				card.setAlignmentX(LEFT_ALIGNMENT);

				JLabel hdr = new JLabel(cp.getStrategy().getLabel());
				hdr.setFont(CraftWiseTheme.getSmallBoldFont());
				hdr.setForeground(cp.isFeasible() ? CraftWiseTheme.ACCENT_GOLD : CraftWiseTheme.COLOR_WARNING_YELLOW);
				card.add(hdr);

				if (!cp.isFeasible() || cp.getPlan() == null)
				{
					JLabel reason = new JLabel("<html>" + cp.getStatusReason() + "</html>");
					reason.setFont(CraftWiseTheme.getSmallFont());
					reason.setForeground(CraftWiseTheme.TEXT_MUTED);
					card.add(reason);
				}
				else
				{
					TrainingPlan p = cp.getPlan();
					Color plCol = p.getTotalNetProfit() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
					String lead = p.getStages().isEmpty() ? "-" : p.getStages().get(0).getRecipe().getName();
					card.add(CraftWiseTheme.kvRow("Method", lead, CraftWiseTheme.TEXT_PRIMARY));
					card.add(CraftWiseTheme.kvRow("Net P/L", CurrencyFormatter.formatSignedGp(p.getTotalNetProfit(), compact), plCol));
					card.add(CraftWiseTheme.kvRow("Speed / Time", NumberFormatter.formatXpPerHour(p.getAverageXpPerHour(), compact)
						+ " · " + NumberFormatter.formatHours(p.getTotalEstimatedHours()), CraftWiseTheme.TEXT_SECONDARY));
					card.setToolTipText("Click to inspect Stages & Shopping List for " + cp.getStrategy().getLabel());
					card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
					card.addMouseListener(new MouseAdapter()
					{
						@Override
						public void mouseClicked(MouseEvent e)
						{
							currentDisplayedPlan = p;
							renderSummary(p, compact);
							renderStages(p, compact);
							renderShoppingList(p, compact);
						}
					});
				}
				comparedPlansPanel.add(card);
				comparedPlansPanel.add(Box.createVerticalStrut(3));
			}
		}
		comparedPlansPanel.revalidate();
		comparedPlansPanel.repaint();
	}

	private void renderShoppingList(TrainingPlan plan, boolean compact)
	{
		shoppingListPanel.removeAll();
		ShoppingSortOrder sort = (ShoppingSortOrder) shoppingSortCombo.getSelectedItem();
		ShoppingListSummary summary = ShoppingListCalculator.fromTrainingPlan(
			plan,
			Collections.emptyMap(),
			dataManager.getCurrentPrices(),
			dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
			dataManager.getConfig() != null ? dataManager.getConfig().priceCalculationMode() : com.craftwise.CraftWiseConfig.PriceCalculationMode.INSTANT_BUY_SELL,
			dataManager.getCustomPrices(),
			sort
		);
		this.currentShoppingSummary = summary;

		JPanel bar = new JPanel(new BorderLayout(4, 0));
		bar.setOpaque(false);
		bar.setAlignmentX(LEFT_ALIGNMENT);
		bar.add(shoppingSortCombo, BorderLayout.CENTER);
		CraftWiseTheme.CraftButton copyBtn = new CraftWiseTheme.CraftButton("Copy List");
		copyBtn.setPreferredSize(new Dimension(74, 21));
		copyBtn.addActionListener(ev -> {
			String txt = ShoppingListCalculator.formatPlainText(
				currentShoppingSummary,
				"Lvl " + plan.getStartLevel() + "-" + plan.getTargetLevel());
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(txt), null);
			copyBtn.setText("Copied!");
		});
		bar.add(copyBtn, BorderLayout.EAST);
		shoppingListPanel.add(bar);
		shoppingListPanel.add(Box.createVerticalStrut(4));

		if (summary.getItems().isEmpty())
		{
			JLabel empty = new JLabel("No materials required.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			shoppingListPanel.add(empty);
		}
		else
		{
			if (!summary.getRequiredToolsAndMoulds().isEmpty())
			{
				JLabel tools = new JLabel("<html>Tools: " + String.join(", ", summary.getRequiredToolsAndMoulds()) + "</html>");
				tools.setFont(CraftWiseTheme.getSmallFont());
				tools.setForeground(CraftWiseTheme.TEXT_MUTED);
				shoppingListPanel.add(tools);
				shoppingListPanel.add(Box.createVerticalStrut(2));
			}
			for (ShoppingListItem item : summary.getItems())
			{
				String qtyAndCost = NumberFormatter.formatInt(item.getRemainingToBuyQty()) + "x · "
					+ (item.isPriceAvailable()
						? CurrencyFormatter.formatGp(item.getTotalRemainingCostGp(), compact)
						: "Missing GP");
				shoppingListPanel.add(CraftWiseTheme.kvRow(item.getItemName(), qtyAndCost, CraftWiseTheme.TEXT_PRIMARY));
			}
			shoppingListPanel.add(Box.createVerticalStrut(3));
			shoppingListPanel.add(CraftWiseTheme.kvRow("Total Est. Spend",
				CurrencyFormatter.formatGp(summary.getTotalRemainingCostGp(), compact), CraftWiseTheme.ACCENT_GOLD));
		}
		shoppingListPanel.revalidate();
		shoppingListPanel.repaint();
	}

	private void renderStages(TrainingPlan plan, boolean compact)
	{
		stagesPanel.removeAll();
		if (plan.getStages().isEmpty())
		{
			JLabel empty = new JLabel("No stages match this budget & constraints.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			stagesPanel.add(empty);
		}
		else
		{
			for (TrainingStage st : plan.getStages())
			{
				JPanel row = CraftWiseTheme.createCard();
				row.setLayout(new BorderLayout(6, 0));
				row.setAlignmentX(LEFT_ALIGNMENT);

				JLabel icon = new JLabel();
				icon.setPreferredSize(new Dimension(24, 24));
				dataManager.loadItemIcon(st.getRecipe().getPrimaryItemId(), img ->
					icon.setIcon(new ImageIcon(img.getScaledInstance(22, 22, java.awt.Image.SCALE_SMOOTH))));
				row.add(icon, BorderLayout.WEST);

				JPanel center = new JPanel(new GridLayout(3, 1, 0, 2));
				center.setOpaque(false);
				center.setMinimumSize(new Dimension(0, 46));
				JLabel title = new JLabel("Lvl " + st.getFromLevel() + "-" + st.getToLevel() + " " + st.getRecipe().getName());
				title.setFont(CraftWiseTheme.getSmallBoldFont());
				title.setForeground(CraftWiseTheme.TEXT_PRIMARY);
				title.setMinimumSize(new Dimension(0, 14));
				JLabel detail = new JLabel(NumberFormatter.formatInt(st.getActionsNeeded()) + " crafts · "
					+ NumberFormatter.formatHours(st.getEstimatedHours()));
				detail.setFont(CraftWiseTheme.getSmallFont());
				detail.setForeground(CraftWiseTheme.TEXT_MUTED);
				detail.setMinimumSize(new Dimension(0, 14));
				JLabel costLine = new JLabel("Cost " + CurrencyFormatter.formatGp(Math.round(st.getTotalMaterialCost()), compact)
					+ " · Net " + CurrencyFormatter.formatSignedGp(st.getTotalNetProfit(), compact));
				costLine.setFont(CraftWiseTheme.getSmallBoldFont());
				costLine.setForeground(st.getTotalNetProfit() >= 0
					? CraftWiseTheme.COLOR_PROFIT_GREEN
					: CraftWiseTheme.COLOR_LOSS_RED);
				costLine.setMinimumSize(new Dimension(0, 14));
				center.add(title);
				center.add(detail);
				center.add(costLine);
				row.add(center, BorderLayout.CENTER);

				row.setToolTipText(st.getRecipe().getName() + " (" + NumberFormatter.formatXp(st.getXpGained(), compact) + ")");
				row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				row.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						if (onRecipeSelected != null && st.getResult() != null)
						{
							onRecipeSelected.accept(st.getResult());
						}
					}
				});

				stagesPanel.add(row);
				stagesPanel.add(Box.createVerticalStrut(4));
			}
		}
		stagesPanel.revalidate();
		stagesPanel.repaint();
	}

	private void renderBudgetMethods(List<BudgetMethodEvaluation> evals, boolean compact)
	{
		budgetMethodsPanel.removeAll();
		if (evals == null || evals.isEmpty())
		{
			JLabel none = new JLabel("No single method qualifies within budget.");
			none.setFont(CraftWiseTheme.getSmallFont());
			none.setForeground(CraftWiseTheme.TEXT_MUTED);
			budgetMethodsPanel.add(none);
		}
		else
		{
			int limit = Math.min(6, evals.size());
			for (int i = 0; i < limit; i++)
			{
				BudgetMethodEvaluation ev = evals.get(i);
				CraftingResult r = ev.getResult();
				JPanel row = CraftWiseTheme.createCard();
				row.setLayout(new BorderLayout(6, 0));
				row.setAlignmentX(LEFT_ALIGNMENT);

				JPanel left = new JPanel(new GridLayout(2, 1, 0, 2));
				left.setOpaque(false);
				left.setMinimumSize(new Dimension(0, 30));
				JLabel name = new JLabel(r.getRecipe().getName() + (ev.isReachesTargetWithinBudget() ? " *" : ""));
				name.setFont(CraftWiseTheme.getSmallBoldFont());
				name.setForeground(ev.isReachesTargetWithinBudget() ? CraftWiseTheme.ACCENT_GOLD : CraftWiseTheme.TEXT_PRIMARY);
				name.setMinimumSize(new Dimension(0, 14));
				JLabel sub = new JLabel(NumberFormatter.formatInt(ev.getMaxAffordableActions()) + " crafts · "
					+ NumberFormatter.formatXp(ev.getTotalXpAchievable(), compact));
				sub.setFont(CraftWiseTheme.getSmallFont());
				sub.setForeground(CraftWiseTheme.TEXT_MUTED);
				sub.setMinimumSize(new Dimension(0, 14));
				left.add(name);
				left.add(sub);

				JLabel right = new JLabel(CurrencyFormatter.formatSignedGp(ev.getTotalNetProfit(), compact));
				right.setFont(CraftWiseTheme.getSmallBoldFont());
				right.setForeground(ev.getTotalNetProfit() >= 0
					? CraftWiseTheme.COLOR_PROFIT_GREEN
					: CraftWiseTheme.COLOR_LOSS_RED);

				row.setToolTipText(r.getRecipe().getName()
					+ (ev.isReachesTargetWithinBudget() ? " (Reaches target level)" : " (Partial budget run)"));
				row.add(left, BorderLayout.CENTER);
				row.add(right, BorderLayout.EAST);
				row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				row.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						if (onRecipeSelected != null)
						{
							onRecipeSelected.accept(r);
						}
					}
				});

				budgetMethodsPanel.add(row);
				budgetMethodsPanel.add(Box.createVerticalStrut(4));
			}
		}
		budgetMethodsPanel.revalidate();
		budgetMethodsPanel.repaint();
	}

	private CraftWiseTheme.CraftButton rangePresetBtn(String label, int startLvlOverride, int targetLvl)
	{
		CraftWiseTheme.CraftButton b = new CraftWiseTheme.CraftButton(label);
		b.setPreferredSize(new Dimension(48, 20));
		b.addActionListener(e -> {
			updating = true;
			try
			{
				int startVal = startLvlOverride > 0
					? startLvlOverride
					: (dataManager.getPlayerStats() != null ? Math.min(98, Math.max(1, dataManager.getPlayerStats().getRealLevel())) : 1);
				startLvlSpinner.setValue(startVal);
				targetLvlSpinner.setValue(Math.max(startVal + 1, targetLvl));
			}
			finally
			{
				updating = false;
			}
			recalculatePlan();
		});
		return b;
	}

	private static void stylePlannerCheck(JCheckBox cb)
	{
		cb.setOpaque(false);
		cb.setForeground(CraftWiseTheme.TEXT_SECONDARY);
		cb.setFont(CraftWiseTheme.getSmallFont());
		cb.setAlignmentX(LEFT_ALIGNMENT);
	}

	private static JPanel inlineRow(String text, java.awt.Component comp)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 23));
		JLabel l = new JLabel(text);
		l.setFont(CraftWiseTheme.getSmallFont());
		l.setForeground(CraftWiseTheme.TEXT_MUTED);
		p.add(l, BorderLayout.WEST);
		p.add(comp, BorderLayout.CENTER);
		return p;
	}
}
