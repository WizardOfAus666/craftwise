package com.craftwise.ui;

import com.craftwise.bank.BankCraftingCalculator;
import com.craftwise.bank.BankCraftingCalculator.BankMaterialValueEntry;
import com.craftwise.bank.BankCraftingCalculator.BankPlanSegment;
import com.craftwise.bank.BankCraftingCalculator.BankRecipeMatch;
import com.craftwise.bank.BankCraftingCalculator.BankRecommendationMode;
import com.craftwise.bank.BankCraftingCalculator.BankTrainingComparison;
import com.craftwise.bank.BankCraftingCalculator.CoverageTier;
import com.craftwise.bank.BankCraftingCalculator.MaterialAllocation;
import com.craftwise.bank.OwnedMaterialSnapshot.BankDataState;
import com.craftwise.bank.OwnedMaterialSnapshot.OwnedItemCount;
import com.craftwise.bank.OwnedMaterialSnapshot.SnapshotView;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListItem;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingListSummary;
import com.craftwise.calculator.ShoppingListCalculator.ShoppingSortOrder;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.market.BankMarketIntegrator.IntegratedMarketView;
import com.craftwise.market.BankMarketIntegrator.IntegratedOpportunity;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.PlayerCraftingStats;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Bank-Aware Crafting Panel (Feature 2 & Feature 3 UI).
 * Scans RuneLite Bank & Inventory containers safely on the client thread, prevents noted/placeholder
 * double-counting, and ranks craftable recipes, missing materials, Sell-vs-Craft value,
 * Plan A vs Plan B training comparisons, and bank-aware shopping lists.
 */
public class BankCraftingPanel extends JPanel
{
	private static final String SUB_CRAFT_NOW = "CRAFT_NOW";
	private static final String SUB_MATERIAL_VALUE = "MAT_VALUE";
	private static final String SUB_TRAINING = "TRAINING";
	private static final String SUB_SHOPPING = "SHOPPING";

	private static final String[] MATCH_WIDE_COLS = {
		"Recipe", "Lvl", "Craft Now", "Inv Only", "With Noted", "Coverage", "Stock XP", "Stock P/L", "Missing GP"
	};
	private static final String[] MATCH_COMPACT_COLS = {
		"Recipe", "Craft", "Coverage"
	};

	private static final String[] MATVAL_WIDE_COLS = {
		"Material", "Bank", "Inv", "Noted", "Sell Net", "Craft Net", "Adv.", "Verdict"
	};
	private static final String[] MATVAL_COMPACT_COLS = {
		"Material", "Owned", "Verdict"
	};

	private final CraftingDataManager dataManager;
	private final Consumer<CraftingResult> onRecipeSelected;

	private final JLabel bankStatusLabel = new JLabel("BANK DATA UNAVAILABLE");
	private final JLabel bankSubStatusLabel = new JLabel("Open your bank in-game to scan Crafting materials.");
	private final CraftWiseTheme.CraftButton rescanBtn = new CraftWiseTheme.CraftButton("Scan");
	private final JCheckBox includeNotedCheck = new JCheckBox("Include Noted");
	private final JTextField targetCraftsField = new JTextField("100");
	private final JComboBox<BankRecommendationMode> rankModeCombo = new JComboBox<>(BankRecommendationMode.values());

	private final JPanel bestBankHighlightCard = CraftWiseTheme.createGoldCard();
	private final JLabel bestOppLabel = new JLabel("No bank opportunity active yet.");
	private final CraftWiseTheme.CraftButton bestOppInspectBtn = new CraftWiseTheme.CraftButton("Inspect");

	private final List<CraftWiseTheme.CraftButton> subTabButtons = new ArrayList<>();
	private final CardLayout subCardLayout = new CardLayout();
	private final JPanel subCardPanel = new JPanel(subCardLayout);
	private String activeSubView = SUB_CRAFT_NOW;

	private final JCheckBox onlyCraftableCheck = new JCheckBox("Ready Now Only");
	private final MatchTableModel matchTableModel = new MatchTableModel();
	private final JTable matchTable = new JTable(matchTableModel);
	private final JPanel matchDetailContainer = new CraftWiseTheme.ScrollableWidthPanel();
	private final CraftWiseTheme.CraftButton openRecipeBreakdownBtn = new CraftWiseTheme.CraftButton("Breakdown", true);
	private BankRecipeMatch selectedMatch;

	private final MaterialValueTableModel matValTableModel = new MaterialValueTableModel();
	private final JTable matValTable = new JTable(matValTableModel);
	private final JLabel matValDisclaimerLabel = new JLabel("Estimated values use live GE prices; execution not guaranteed.");

	private final JTextField trainTargetLvlField = new JTextField("99");
	private final JTextField trainBudgetField = new JTextField("5000000");
	private final JPanel trainComparisonContainer = new CraftWiseTheme.ScrollableWidthPanel();

	private final JTextField shopQuantityField = new JTextField("250");
	private final JComboBox<ShoppingSortOrder> shopSortCombo = new JComboBox<>(ShoppingSortOrder.values());
	private final JLabel shopSummaryLabel = new JLabel("Select a recipe in Craft Now to generate a shopping list.");
	private final JPanel shopItemsContainer = new CraftWiseTheme.ScrollableWidthPanel();
	private final CraftWiseTheme.CraftButton copyShopBtn = new CraftWiseTheme.CraftButton("Copy List");
	private final JTextField manualItemField = new JTextField();
	private final JTextField manualQtyField = new JTextField("0");
	private final CraftWiseTheme.CraftButton saveManualBtn = new CraftWiseTheme.CraftButton("Save Owned", true);
	private final CraftWiseTheme.CraftButton clearManualBtn = new CraftWiseTheme.CraftButton("Clear");
	private final JPanel manualListContainer = new JPanel();

	private boolean wideMode = false;
	private CraftingResult bestBankResult;

	public BankCraftingPanel(
		CraftingDataManager dataManager,
		Consumer<CraftingResult> onRecipeSelected)
	{
		this.dataManager = dataManager;
		this.onRecipeSelected = onRecipeSelected;

		setLayout(new BorderLayout(0, 6));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		add(buildTopHeaderCard(), BorderLayout.NORTH);
		add(buildSubViewsArea(), BorderLayout.CENTER);

		configureTableColumns();
		refresh();
	}

	public void setWideMode(boolean wide)
	{
		if (this.wideMode == wide)
		{
			return;
		}
		this.wideMode = wide;
		matchTableModel.setWideMode(wide);
		matValTableModel.setWideMode(wide);
		configureTableColumns();
	}

	private JPanel buildTopHeaderCard()
	{
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		JPanel statusCard = CraftWiseTheme.createCard();
		statusCard.setLayout(new BoxLayout(statusCard, BoxLayout.Y_AXIS));

		JPanel statusHdr = new JPanel(new BorderLayout(4, 0));
		statusHdr.setOpaque(false);
		bankStatusLabel.setFont(CraftWiseTheme.getSmallBoldFont());
		bankStatusLabel.setForeground(CraftWiseTheme.COLOR_WARNING_YELLOW);
		rescanBtn.addActionListener(e -> dataManager.scanContainersOnClientThread());
		statusHdr.add(bankStatusLabel, BorderLayout.CENTER);
		statusHdr.add(rescanBtn, BorderLayout.EAST);
		statusCard.add(statusHdr);

		bankSubStatusLabel.setFont(CraftWiseTheme.getSmallFont());
		bankSubStatusLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		statusCard.add(bankSubStatusLabel);
		statusCard.add(Box.createVerticalStrut(4));

		JPanel rankRow = new JPanel(new BorderLayout(6, 0));
		rankRow.setOpaque(false);
		rankRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel rankLbl = new JLabel("Rank:");
		rankLbl.setFont(CraftWiseTheme.getSmallFont());
		rankLbl.setForeground(CraftWiseTheme.TEXT_MUTED);
		CraftWiseTheme.styleComboBox(rankModeCombo);
		rankModeCombo.setSelectedItem(dataManager.getBankRecommendationMode());
		rankModeCombo.addActionListener(e -> {
			BankRecommendationMode m = (BankRecommendationMode) rankModeCombo.getSelectedItem();
			if (m != null)
			{
				dataManager.setBankRecommendationMode(m);
			}
		});
		rankRow.add(rankLbl, BorderLayout.WEST);
		rankRow.add(rankModeCombo, BorderLayout.CENTER);
		statusCard.add(rankRow);
		statusCard.add(Box.createVerticalStrut(4));

		JPanel optRow = new JPanel(new BorderLayout(6, 0));
		optRow.setOpaque(false);
		optRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JPanel tgtWrap = new JPanel(new BorderLayout(4, 0));
		tgtWrap.setOpaque(false);
		JLabel tgtLbl = new JLabel("Target:");
		tgtLbl.setFont(CraftWiseTheme.getSmallFont());
		tgtLbl.setForeground(CraftWiseTheme.TEXT_MUTED);
		CraftWiseTheme.styleTextField(targetCraftsField);
		targetCraftsField.setPreferredSize(new Dimension(64, 22));
		targetCraftsField.setText(String.valueOf(dataManager.getBankTargetCrafts()));
		targetCraftsField.setToolTipText("Target crafts for coverage % & missing material calculation");
		targetCraftsField.addActionListener(e -> applyTargetCrafts());
		tgtWrap.add(tgtLbl, BorderLayout.WEST);
		tgtWrap.add(targetCraftsField, BorderLayout.CENTER);

		includeNotedCheck.setOpaque(false);
		includeNotedCheck.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		includeNotedCheck.setFont(CraftWiseTheme.getSmallFont());
		includeNotedCheck.setSelected(dataManager.isIncludeNotedBankCalc());
		includeNotedCheck.addActionListener(e -> dataManager.setIncludeNotedBankCalc(includeNotedCheck.isSelected()));

		optRow.add(tgtWrap, BorderLayout.CENTER);
		optRow.add(includeNotedCheck, BorderLayout.EAST);
		statusCard.add(optRow);

		top.add(statusCard);
		top.add(Box.createVerticalStrut(4));

		bestBankHighlightCard.setLayout(new BorderLayout(6, 2));
		JPanel hlText = new JPanel();
		hlText.setLayout(new BoxLayout(hlText, BoxLayout.Y_AXIS));
		hlText.setOpaque(false);
		JLabel hlTitle = new JLabel("Best Opportunity Using Your Bank");
		hlTitle.setFont(CraftWiseTheme.getSmallBoldFont());
		hlTitle.setForeground(CraftWiseTheme.ACCENT_GOLD);
		bestOppLabel.setFont(CraftWiseTheme.getSmallFont());
		bestOppLabel.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		hlText.add(hlTitle);
		hlText.add(Box.createVerticalStrut(2));
		hlText.add(bestOppLabel);
		bestOppInspectBtn.setVisible(false);
		bestOppInspectBtn.setPreferredSize(new Dimension(64, 24));
		bestOppInspectBtn.addActionListener(e -> {
			if (bestBankResult != null && onRecipeSelected != null)
			{
				onRecipeSelected.accept(bestBankResult);
			}
		});
		bestBankHighlightCard.add(hlText, BorderLayout.CENTER);
		bestBankHighlightCard.add(bestOppInspectBtn, BorderLayout.EAST);
		bestBankHighlightCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		top.add(bestBankHighlightCard);
		top.add(Box.createVerticalStrut(4));

		JPanel subTabs = new JPanel(new GridLayout(1, 4, 2, 0));
		subTabs.setOpaque(false);
		subTabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		addSubTab(subTabs, "Craft", SUB_CRAFT_NOW);
		addSubTab(subTabs, "Value", SUB_MATERIAL_VALUE);
		addSubTab(subTabs, "Plan", SUB_TRAINING);
		addSubTab(subTabs, "Shop", SUB_SHOPPING);
		top.add(subTabs);

		return top;
	}

	private void addSubTab(JPanel bar, String label, String cardKey)
	{
		CraftWiseTheme.CraftButton btn = new CraftWiseTheme.CraftButton(label);
		btn.setTabStyle(true);
		btn.setActive(cardKey.equals(activeSubView));
		btn.putClientProperty("subKey", cardKey);
		btn.addActionListener(e -> {
			activeSubView = cardKey;
			for (CraftWiseTheme.CraftButton b : subTabButtons)
			{
				b.setActive(cardKey.equals(b.getClientProperty("subKey")));
			}
			subCardLayout.show(subCardPanel, cardKey);
			refreshActiveSubView();
		});
		subTabButtons.add(btn);
		bar.add(btn);
	}

	private Component buildSubViewsArea()
	{
		subCardPanel.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		subCardPanel.add(buildCraftNowSubView(), SUB_CRAFT_NOW);
		subCardPanel.add(buildMaterialValueSubView(), SUB_MATERIAL_VALUE);
		subCardPanel.add(buildTrainingSubView(), SUB_TRAINING);
		subCardPanel.add(buildShoppingSubView(), SUB_SHOPPING);
		return subCardPanel;
	}

	private Component buildCraftNowSubView()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		JPanel topFilter = new JPanel(new BorderLayout(4, 0));
		topFilter.setOpaque(false);
		onlyCraftableCheck.setOpaque(false);
		onlyCraftableCheck.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		onlyCraftableCheck.setFont(CraftWiseTheme.getSmallFont());
		onlyCraftableCheck.addActionListener(e -> refreshCraftNowTable());
		topFilter.add(onlyCraftableCheck, BorderLayout.WEST);

		openRecipeBreakdownBtn.setEnabled(false);
		openRecipeBreakdownBtn.addActionListener(e -> {
			if (selectedMatch != null && selectedMatch.getMarketResult() != null && onRecipeSelected != null)
			{
				onRecipeSelected.accept(selectedMatch.getMarketResult());
			}
		});
		topFilter.add(openRecipeBreakdownBtn, BorderLayout.EAST);
		panel.add(topFilter, BorderLayout.NORTH);

		styleDarkTable(matchTable);
		matchTable.setDefaultRenderer(Object.class, new MatchCellRenderer());
		matchTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				int r = matchTable.getSelectedRow();
				if (r >= 0)
				{
					selectedMatch = matchTableModel.getRow(r);
					updateSelectedMatchDetail();
				}
			}
		});
		matchTable.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2 && selectedMatch != null
					&& selectedMatch.getMarketResult() != null && onRecipeSelected != null)
				{
					onRecipeSelected.accept(selectedMatch.getMarketResult());
				}
			}
		});

		JScrollPane tScroll = new JScrollPane(matchTable);
		CraftWiseTheme.styleScrollPane(tScroll);
		tScroll.setPreferredSize(new Dimension(200, 180));

		matchDetailContainer.setOpaque(false);
		matchDetailContainer.setLayout(new BoxLayout(matchDetailContainer, BoxLayout.Y_AXIS));
		JScrollPane dScroll = new JScrollPane(matchDetailContainer);
		CraftWiseTheme.styleScrollPane(dScroll);
		dScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tScroll, dScroll);
		split.setResizeWeight(0.55);
		split.setDividerSize(4);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		panel.add(split, BorderLayout.CENTER);
		return panel;
	}

	private Component buildMaterialValueSubView()
	{
		JPanel panel = new JPanel(new BorderLayout(0, 4));
		panel.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		matValDisclaimerLabel.setFont(CraftWiseTheme.getSmallFont());
		matValDisclaimerLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		panel.add(matValDisclaimerLabel, BorderLayout.NORTH);

		styleDarkTable(matValTable);
		matValTable.setDefaultRenderer(Object.class, new MatValCellRenderer());
		JScrollPane scroll = new JScrollPane(matValTable);
		CraftWiseTheme.styleScrollPane(scroll);
		panel.add(scroll, BorderLayout.CENTER);
		return panel;
	}

	private Component buildTrainingSubView()
	{
		JPanel root = new JPanel(new BorderLayout(0, 6));
		root.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		JPanel ctrl = CraftWiseTheme.createCard();
		ctrl.setLayout(new GridLayout(1, 3, 4, 0));
		ctrl.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		CraftWiseTheme.styleTextField(trainTargetLvlField);
		CraftWiseTheme.styleTextField(trainBudgetField);
		CraftWiseTheme.CraftButton computeBtn = new CraftWiseTheme.CraftButton("Compare", true);
		computeBtn.addActionListener(e -> refreshTrainingComparison());
		ctrl.add(CraftWiseTheme.stackedField("Target Lvl", trainTargetLvlField));
		ctrl.add(CraftWiseTheme.stackedField("Budget GP", trainBudgetField));
		ctrl.add(computeBtn);
		root.add(ctrl, BorderLayout.NORTH);

		trainComparisonContainer.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		trainComparisonContainer.setLayout(new BoxLayout(trainComparisonContainer, BoxLayout.Y_AXIS));
		JScrollPane scroll = new JScrollPane(trainComparisonContainer);
		CraftWiseTheme.styleScrollPane(scroll);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		root.add(scroll, BorderLayout.CENTER);
		return root;
	}

	private Component buildShoppingSubView()
	{
		JPanel root = new JPanel(new BorderLayout(0, 6));
		root.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		JPanel top = CraftWiseTheme.createCard();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JPanel row = new JPanel(new GridLayout(1, 3, 4, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		CraftWiseTheme.styleTextField(shopQuantityField);
		shopQuantityField.addActionListener(e -> refreshShoppingView());
		CraftWiseTheme.styleComboBox(shopSortCombo);
		shopSortCombo.addActionListener(e -> refreshShoppingView());
		copyShopBtn.addActionListener(e -> copyShoppingListToClipboard());
		row.add(CraftWiseTheme.stackedField("Crafts", shopQuantityField));
		row.add(CraftWiseTheme.stackedField("Sort", shopSortCombo));
		row.add(copyShopBtn);
		top.add(row);
		top.add(Box.createVerticalStrut(4));
		shopSummaryLabel.setFont(CraftWiseTheme.getSmallFont());
		shopSummaryLabel.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		top.add(shopSummaryLabel);
		root.add(top, BorderLayout.NORTH);

		JPanel body = new CraftWiseTheme.ScrollableWidthPanel();
		body.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

		shopItemsContainer.setOpaque(false);
		shopItemsContainer.setLayout(new BoxLayout(shopItemsContainer, BoxLayout.Y_AXIS));
		body.add(shopItemsContainer);
		body.add(Box.createVerticalStrut(6));

		JPanel manCard = CraftWiseTheme.createCard();
		manCard.setLayout(new BoxLayout(manCard, BoxLayout.Y_AXIS));
		manCard.add(CraftWiseTheme.createSectionHeader("Manual Owned Adjustments"));
		manCard.add(Box.createVerticalStrut(4));

		JPanel manInputs = new JPanel(new GridLayout(2, 2, 4, 4));
		manInputs.setOpaque(false);
		manInputs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
		CraftWiseTheme.styleTextField(manualItemField);
		manualItemField.setToolTipText("Item Name or Item ID (e.g. Gold bar or 2357)");
		CraftWiseTheme.styleTextField(manualQtyField);
		saveManualBtn.addActionListener(e -> saveManualAdjustment());
		clearManualBtn.addActionListener(e -> {
			dataManager.getOwnedMaterialSnapshot().clearManualOverrides();
			dataManager.recalculateBankMatchesAsync();
			refresh();
		});
		manInputs.add(CraftWiseTheme.stackedField("Item Name / ID", manualItemField));
		manInputs.add(CraftWiseTheme.stackedField("Manual Owned Qty", manualQtyField));
		manInputs.add(saveManualBtn);
		manInputs.add(clearManualBtn);
		manCard.add(manInputs);
		manCard.add(Box.createVerticalStrut(4));

		manualListContainer.setOpaque(false);
		manualListContainer.setLayout(new BoxLayout(manualListContainer, BoxLayout.Y_AXIS));
		manCard.add(manualListContainer);
		body.add(manCard);

		JScrollPane scroll = new JScrollPane(body);
		CraftWiseTheme.styleScrollPane(scroll);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		root.add(scroll, BorderLayout.CENTER);
		return root;
	}

	private void applyTargetCrafts()
	{
		try
		{
			long v = Long.parseLong(targetCraftsField.getText().trim().replace(",", ""));
			dataManager.setBankTargetCrafts(v);
		}
		catch (NumberFormatException ignored)
		{
		}
	}

	private void configureTableColumns()
	{
		if (matchTable.getColumnModel().getColumnCount() > 0)
		{
			if (wideMode)
			{
				int[] w = {145, 32, 66, 62, 68, 72, 66, 74, 72};
				for (int i = 0; i < Math.min(w.length, matchTable.getColumnModel().getColumnCount()); i++)
				{
					matchTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
				}
			}
			else if (matchTable.getColumnModel().getColumnCount() >= 3)
			{
				matchTable.getColumnModel().getColumn(0).setPreferredWidth(185);
				matchTable.getColumnModel().getColumn(1).setPreferredWidth(54);
				matchTable.getColumnModel().getColumn(2).setPreferredWidth(62);
			}
		}
		if (matValTable.getColumnModel().getColumnCount() > 0)
		{
			if (wideMode)
			{
				int[] w = {135, 58, 52, 56, 72, 72, 68, 92};
				for (int i = 0; i < Math.min(w.length, matValTable.getColumnModel().getColumnCount()); i++)
				{
					matValTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
				}
			}
			else if (matValTable.getColumnModel().getColumnCount() >= 3)
			{
				matValTable.getColumnModel().getColumn(0).setPreferredWidth(165);
				matValTable.getColumnModel().getColumn(1).setPreferredWidth(58);
				matValTable.getColumnModel().getColumn(2).setPreferredWidth(78);
			}
		}
	}

	public void refresh()
	{
		refreshBankStatusHeader();
		refreshActiveSubView();
	}

	private void refreshActiveSubView()
	{
		switch (activeSubView)
		{
			case SUB_MATERIAL_VALUE:
				refreshMaterialValueTable();
				break;
			case SUB_TRAINING:
				refreshTrainingComparison();
				break;
			case SUB_SHOPPING:
				refreshShoppingView();
				break;
			case SUB_CRAFT_NOW:
			default:
				refreshCraftNowTable();
				break;
		}
	}

	private void refreshBankStatusHeader()
	{
		SnapshotView snap = dataManager.getBankSnapshotView();
		BankDataState state = snap != null ? snap.getBankDataState() : BankDataState.UNAVAILABLE;
		bankStatusLabel.setText(state.getLabel());
		if (state == BankDataState.LIVE_OPEN)
		{
			bankStatusLabel.setForeground(CraftWiseTheme.COLOR_PROFIT_GREEN);
			int distinct = snap != null ? snap.getItems().size() : 0;
			bankSubStatusLabel.setText(String.format("Live sync active | %,d distinct stacks tracked", distinct));
		}
		else if (state == BankDataState.SNAPSHOT_CLOSED)
		{
			bankStatusLabel.setForeground(CraftWiseTheme.ACCENT_GOLD);
			long ageSec = Math.max(0L, Instant.now().getEpochSecond() - snap.getLastBankScanEpochSec());
			bankSubStatusLabel.setText(String.format("Last scanned %ds ago (%,d stacks) + live inventory",
				ageSec, snap.getItems().size()));
		}
		else
		{
			bankStatusLabel.setForeground(CraftWiseTheme.COLOR_WARNING_YELLOW);
			bankSubStatusLabel.setText("Open your bank in-game once per session to scan owned materials.");
		}

		IntegratedMarketView iv = dataManager.getLatestIntegratedMarketView();
		if (snap != null && snap.isBankScanned() && iv != null && iv.getBestOpportunityUsingYourBank() != null)
		{
			IntegratedOpportunity best = iv.getBestOpportunityUsingYourBank();
			bestBankResult = best.getOpportunity().getResult();
			bestOppLabel.setText(String.format("<html>%s: <b>%,d</b> craftable<br>Est. <b>%s</b> profit</html>",
				bestBankResult.getRecipe().getName(),
				best.getMaxCraftableFromBank(),
				CurrencyFormatter.formatSignedGp(best.getImmediateProfitFromBankGp(), true)));
			bestOppInspectBtn.setVisible(true);
		}
		else
		{
			bestBankResult = null;
			bestOppInspectBtn.setVisible(false);
			bestOppLabel.setText(snap != null && snap.isBankScanned()
				? "<html>No profitable unlocked recipe craftable from stock.</html>"
				: "<html>BANK DATA UNAVAILABLE — awaiting bank scan.</html>");
		}
	}

	private void refreshCraftNowTable()
	{
		List<BankRecipeMatch> all = dataManager.getLatestBankMatches();
		if (all == null)
		{
			all = Collections.emptyList();
		}
		List<BankRecipeMatch> visible;
		if (onlyCraftableCheck.isSelected())
		{
			visible = new ArrayList<>();
			for (BankRecipeMatch m : all)
			{
				if (m.isCraftableNow())
				{
					visible.add(m);
				}
			}
		}
		else
		{
			visible = all;
		}
		matchTableModel.setRows(visible);

		if (!visible.isEmpty() && matchTable.getSelectedRow() < 0)
		{
			matchTable.setRowSelectionInterval(0, 0);
		}
		else if (selectedMatch != null)
		{
			String id = selectedMatch.getRecipe().getId();
			for (BankRecipeMatch m : visible)
			{
				if (m.getRecipe().getId().equals(id))
				{
					selectedMatch = m;
					break;
				}
			}
			updateSelectedMatchDetail();
		}
	}

	private void updateSelectedMatchDetail()
	{
		matchDetailContainer.removeAll();
		if (selectedMatch == null)
		{
			openRecipeBreakdownBtn.setEnabled(false);
			JLabel empty = new JLabel(dataManager.getBankSnapshotView().isBankScanned()
				? "Select a recipe row above to see bank/inventory material breakdown."
				: "BANK DATA UNAVAILABLE — open your bank in-game to inspect craftable recipes.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			matchDetailContainer.add(empty);
			matchDetailContainer.revalidate();
			matchDetailContainer.repaint();
			return;
		}

		openRecipeBreakdownBtn.setEnabled(selectedMatch.getMarketResult() != null);
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		CraftingRecipe rec = selectedMatch.getRecipe();
		card.add(CraftWiseTheme.createSectionHeader(rec.getName() + " (Lvl " + rec.getRequiredLevel() + ")"));
		card.add(Box.createVerticalStrut(4));
		card.add(CraftWiseTheme.kvRow("Coverage (" + selectedMatch.getTargetCraftsEvaluated() + " target)",
			selectedMatch.getCoverageTier().getLabel() + " (" + NumberFormatter.formatDecimal(selectedMatch.getCoveragePercent(), 0) + "%)",
			tierColor(selectedMatch.getCoverageTier())));
		card.add(CraftWiseTheme.kvRow("Max Complete Crafts",
			NumberFormatter.formatInt(selectedMatch.getMaxCompleteCrafts()),
			selectedMatch.isCraftableNow() ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.TEXT_MUTED));
		card.add(CraftWiseTheme.kvRow("Inv Only / Incl. Noted",
			NumberFormatter.formatInt(selectedMatch.getReadyInventoryOnlyCrafts())
				+ " / " + NumberFormatter.formatInt(selectedMatch.getMaxCraftsIncludingNoted()),
			CraftWiseTheme.TEXT_SECONDARY));
		card.add(CraftWiseTheme.kvRow("Total XP From Stock",
			NumberFormatter.formatXp(selectedMatch.getTotalXpFromOwnedStock(), true),
			CraftWiseTheme.COLOR_INFO_BLUE));
		card.add(CraftWiseTheme.kvRow("Est. Profit From Stock",
			CurrencyFormatter.formatSignedGp(selectedMatch.getTotalProfitFromOwnedStock(), false),
			selectedMatch.getTotalProfitFromOwnedStock() >= 0.0
				? CraftWiseTheme.COLOR_PROFIT_GREEN
				: CraftWiseTheme.COLOR_LOSS_RED));
		if (selectedMatch.getMissingForTargetCostGp() > 0L)
		{
			card.add(CraftWiseTheme.kvRow("Buy Missing For Target",
				CurrencyFormatter.formatGp(selectedMatch.getMissingForTargetCostGp(), false),
				CraftWiseTheme.COLOR_WARNING_YELLOW));
		}
		if (selectedMatch.isMissingTools())
		{
			card.add(CraftWiseTheme.kvRow("Missing Tool(s)",
				String.join(", ", selectedMatch.getMissingToolNames()),
				CraftWiseTheme.COLOR_LOSS_RED));
		}
		card.add(Box.createVerticalStrut(4));

		for (MaterialAllocation a : selectedMatch.getAllocations())
		{
			String role = a.isConsumed() ? "Material" : "Tool";
			String missLabel = a.getMissingForTargetCrafts() == 0L
				? "Ready (" + NumberFormatter.formatCompact(a.getCombinedUsableQty(), true) + ")"
				: "Miss " + NumberFormatter.formatInt(a.getMissingForTargetCrafts());
			card.add(CraftWiseTheme.kvRow(a.getItemName() + " (" + role + ")", missLabel,
				a.getMissingForTargetCrafts() == 0L ? CraftWiseTheme.TEXT_PRIMARY : CraftWiseTheme.COLOR_WARNING_YELLOW));

			JLabel subLine = new JLabel(String.format("  Bank: %,d · Inv: %,d · Noted: %,d · Rem: %,d",
				a.getOwnedBankQty(), a.getOwnedInvUnnotedQty(),
				a.getOwnedInvNotedQty(), a.getRemainingAfterMaxCrafts()));
			subLine.setFont(CraftWiseTheme.getSmallFont());
			subLine.setForeground(CraftWiseTheme.TEXT_MUTED);
			card.add(subLine);
		}

		matchDetailContainer.add(card);
		matchDetailContainer.revalidate();
		matchDetailContainer.repaint();
	}

	private void refreshMaterialValueTable()
	{
		List<BankMaterialValueEntry> list = dataManager.getLatestBankMaterialValues();
		matValTableModel.setRows(list != null ? list : Collections.emptyList());
	}

	private void refreshTrainingComparison()
	{
		trainComparisonContainer.removeAll();
		PlayerCraftingStats stats = dataManager.getPlayerStats();
		int startLvl = stats != null ? stats.getRealLevel() : 1;
		long startXp = stats != null ? stats.getCurrentXp() : 0L;
		int tgtLvl = (int) Math.max(startLvl + 1L, Math.min(99L, parseLongOr(trainTargetLvlField.getText(), dataManager.getTargetLevel())));
		long budget = parseLongOr(trainBudgetField.getText(), dataManager.getBudgetGp());

		BankTrainingComparison cmp = dataManager.computeBankTrainingComparison(startLvl, startXp, tgtLvl, budget);

		JPanel cardA = CraftWiseTheme.createGoldCard();
		cardA.setLayout(new BoxLayout(cardA, BoxLayout.Y_AXIS));
		cardA.add(CraftWiseTheme.createSectionHeader("Plan A: Use Owned Bank Stock First"));
		cardA.add(Box.createVerticalStrut(4));
		cardA.add(CraftWiseTheme.kvRow("XP Covered By Bank",
			NumberFormatter.formatInt(cmp.getXpCoveredByBank()) + " / " + NumberFormatter.formatInt(cmp.getXpRequiredTotal()),
			CraftWiseTheme.COLOR_PROFIT_GREEN));
		cardA.add(CraftWiseTheme.kvRow("Remaining External Spend",
			CurrencyFormatter.formatGp(Math.round(cmp.getPlanATotalAdditionalSpendGp()), false),
			CraftWiseTheme.TEXT_PRIMARY));
		cardA.add(CraftWiseTheme.kvRow("Plan A Est. Net Profit",
			CurrencyFormatter.formatSignedGp(cmp.getPlanANetProfitLossGp(), false),
			cmp.getPlanANetProfitLossGp() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED));
		cardA.add(CraftWiseTheme.kvRow("Plan A Est. Time",
			NumberFormatter.formatDecimal(cmp.getPlanATotalHours(), 1) + " hrs", CraftWiseTheme.TEXT_MUTED));
		for (BankPlanSegment seg : cmp.getBankUsedSegments())
		{
			cardA.add(CraftWiseTheme.kvRow("  Use " + seg.getRecipe().getName(),
				String.format("%,d crafts (+%s)", seg.getCraftsUsingBankStock(), NumberFormatter.formatXp(seg.getXpGained(), true)),
				CraftWiseTheme.COLOR_INFO_BLUE));
		}
		trainComparisonContainer.add(cardA);
		trainComparisonContainer.add(Box.createVerticalStrut(6));

		JPanel cardB = CraftWiseTheme.createCard();
		cardB.setLayout(new BoxLayout(cardB, BoxLayout.Y_AXIS));
		cardB.add(CraftWiseTheme.createSectionHeader("Plan B: Market Optimal (Ignoring Bank)"));
		cardB.add(Box.createVerticalStrut(4));
		cardB.add(CraftWiseTheme.kvRow("Raw Materials Sell Value",
			CurrencyFormatter.formatGp(cmp.getOwnedBankCraftingSellValueGp(), true),
			CraftWiseTheme.ACCENT_GOLD));
		cardB.add(CraftWiseTheme.kvRow("Plan B Est. Net Profit/Loss",
			CurrencyFormatter.formatSignedGp(cmp.getPlanBNetProfitLossGp(), false),
			cmp.getPlanBNetProfitLossGp() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED));
		cardB.add(CraftWiseTheme.kvRow("Plan B Est. Time",
			NumberFormatter.formatDecimal(cmp.getPlanBTotalHours(), 1) + " hrs", CraftWiseTheme.TEXT_MUTED));
		JLabel adv = new JLabel("<html>" + cmp.getAdviceSummary() + "</html>");
		adv.setFont(CraftWiseTheme.getSmallFont());
		adv.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		cardB.add(Box.createVerticalStrut(4));
		cardB.add(adv);

		trainComparisonContainer.add(cardB);
		trainComparisonContainer.revalidate();
		trainComparisonContainer.repaint();
	}

	private void refreshShoppingView()
	{
		shopItemsContainer.removeAll();
		manualListContainer.removeAll();

		if (selectedMatch == null)
		{
			shopSummaryLabel.setText("Select a recipe in the Craft tab first to view missing items.");
		}
		else
		{
			long crafts = Math.max(1L, parseLongOr(shopQuantityField.getText(), 250L));
			ShoppingSortOrder sort = (ShoppingSortOrder) shopSortCombo.getSelectedItem();
			if (sort == null)
			{
				sort = ShoppingSortOrder.TOTAL_COST_DESC;
			}
			ShoppingListSummary sum = BankCraftingCalculator.buildBankAwareShoppingList(
				selectedMatch.getRecipe(),
				crafts,
				dataManager.getBankSnapshotView(),
				dataManager.isIncludeNotedBankCalc(),
				dataManager.getCurrentPrices(),
				dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : Collections.emptyMap(),
				dataManager.getConfig() != null ? dataManager.getConfig().priceCalculationMode() : com.craftwise.CraftWiseConfig.PriceCalculationMode.INSTANT_BUY_SELL,
				dataManager.getCustomPrices(),
				sort);
			shopSummaryLabel.setText(String.format("<html><b>%s</b> x%,d -> Remaining Buy Cost: <b>%s</b></html>",
				selectedMatch.getRecipe().getName(), crafts, CurrencyFormatter.formatGp(sum.getTotalRemainingCostGp(), false)));

			JPanel itemsCard = CraftWiseTheme.createCard();
			itemsCard.setLayout(new BoxLayout(itemsCard, BoxLayout.Y_AXIS));
			for (ShoppingListItem it : sum.getItems())
			{
				String rightVal = it.getRemainingToBuyQty() == 0L
					? "Owned"
					: "Buy " + NumberFormatter.formatCompact(it.getRemainingToBuyQty(), true)
						+ " (" + (it.isPriceAvailable() ? CurrencyFormatter.formatGp(it.getTotalRemainingCostGp(), true) : "N/A") + ")";
				itemsCard.add(CraftWiseTheme.kvRow(it.getItemName(), rightVal,
					it.getRemainingToBuyQty() == 0L ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_WARNING_YELLOW));

				JLabel sub = new JLabel(String.format("  Need: %,d · Own: %,d",
					it.getTotalRequiredQty(), it.getOwnedQty()));
				sub.setFont(CraftWiseTheme.getSmallFont());
				sub.setForeground(CraftWiseTheme.TEXT_MUTED);
				itemsCard.add(sub);
			}
			for (String tool : sum.getRequiredToolsAndMoulds())
			{
				itemsCard.add(CraftWiseTheme.kvRow("Required Tool", tool, CraftWiseTheme.COLOR_INFO_BLUE));
			}
			shopItemsContainer.add(itemsCard);
		}

		Map<Integer, Long> overrides = dataManager.getOwnedMaterialSnapshot().getManualOwnedOverrides();
		if (overrides.isEmpty())
		{
			JLabel none = new JLabel("No manual owned quantity overrides set.");
			none.setFont(CraftWiseTheme.getSmallFont());
			none.setForeground(CraftWiseTheme.TEXT_MUTED);
			manualListContainer.add(none);
		}
		else
		{
			SnapshotView snap = dataManager.getBankSnapshotView();
			for (Map.Entry<Integer, Long> e : overrides.entrySet())
			{
				OwnedItemCount cnt = snap != null ? snap.getCount(e.getKey()) : null;
				String name = cnt != null ? cnt.getItemName() : ("Item #" + e.getKey());
				JPanel r = new JPanel(new BorderLayout(4, 0));
				r.setOpaque(false);
				JLabel l = new JLabel(name + ": +" + NumberFormatter.formatInt(e.getValue()));
				l.setFont(CraftWiseTheme.getSmallFont());
				l.setForeground(CraftWiseTheme.TEXT_PRIMARY);
				CraftWiseTheme.CraftButton del = new CraftWiseTheme.CraftButton("X");
				del.addActionListener(ev -> dataManager.setManualOwnedQuantity(e.getKey(), name, 0L));
				r.add(l, BorderLayout.CENTER);
				r.add(del, BorderLayout.EAST);
				manualListContainer.add(r);
			}
		}

		shopItemsContainer.revalidate();
		shopItemsContainer.repaint();
		manualListContainer.revalidate();
		manualListContainer.repaint();
	}

	private void copyShoppingListToClipboard()
	{
		if (selectedMatch == null)
		{
			return;
		}
		long crafts = Math.max(1L, parseLongOr(shopQuantityField.getText(), 250L));
		ShoppingListSummary sum = BankCraftingCalculator.buildBankAwareShoppingList(
			selectedMatch.getRecipe(),
			crafts,
			dataManager.getBankSnapshotView(),
			dataManager.isIncludeNotedBankCalc(),
			dataManager.getCurrentPrices(),
			dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : Collections.emptyMap(),
			dataManager.getConfig() != null ? dataManager.getConfig().priceCalculationMode() : com.craftwise.CraftWiseConfig.PriceCalculationMode.INSTANT_BUY_SELL,
			dataManager.getCustomPrices(),
			ShoppingSortOrder.TOTAL_COST_DESC);

		StringBuilder sb = new StringBuilder();
		sb.append("CraftWise Bank-Aware Shopping List: ").append(selectedMatch.getRecipe().getName())
			.append(" x").append(crafts).append('\n');
		for (ShoppingListItem it : sum.getItems())
		{
			sb.append("- ").append(it.getItemName())
				.append(": Need ").append(it.getTotalRequiredQty())
				.append(", Own ").append(it.getOwnedQty())
				.append(", Buy ").append(it.getRemainingToBuyQty())
				.append(" (").append(CurrencyFormatter.formatGp(it.getTotalRemainingCostGp(), false)).append(")\n");
		}
		sb.append("Total Remaining Cost: ").append(CurrencyFormatter.formatGp(sum.getTotalRemainingCostGp(), false));
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
		}
		catch (Throwable ignored)
		{
		}
	}

	private void saveManualAdjustment()
	{
		String rawItem = manualItemField.getText() != null ? manualItemField.getText().trim() : "";
		if (rawItem.isEmpty())
		{
			return;
		}
		long qty = Math.max(0L, parseLongOr(manualQtyField.getText(), 0L));
		Integer itemId = null;
		String itemName = rawItem;
		try
		{
			itemId = Integer.parseInt(rawItem);
		}
		catch (NumberFormatException ignored)
		{
			Map<String, Integer> idx = dataManager.getWikiPriceClient() != null
				? dataManager.getWikiPriceClient().getNameToIdIndex()
				: Collections.emptyMap();
			itemId = idx.get(rawItem.toLowerCase(java.util.Locale.ROOT));
		}
		if (itemId != null && itemId > 0)
		{
			dataManager.setManualOwnedQuantity(itemId, itemName, qty);
			manualItemField.setText("");
		}
	}

	private static void styleDarkTable(JTable tbl)
	{
		tbl.setBackground(CraftWiseTheme.BG_CARD);
		tbl.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		tbl.setGridColor(CraftWiseTheme.BORDER_SUBTLE);
		tbl.setRowHeight(23);
		tbl.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		tbl.setSelectionBackground(CraftWiseTheme.BG_CARD_HOVER);
		tbl.setSelectionForeground(CraftWiseTheme.TEXT_PRIMARY);
		tbl.setShowVerticalLines(false);
		tbl.setFont(CraftWiseTheme.getSmallFont());
		CraftWiseTheme.styleTableHeader(tbl.getTableHeader());
	}

	private static String compactTierLabel(CoverageTier tier)
	{
		if (tier == null)
		{
			return "0%";
		}
		switch (tier)
		{
			case READY_100:
				return "100%";
			case READY_75:
				return "75%+";
			case PARTIAL:
				return "Partial";
			case NOT_READY:
			default:
				return "0%";
		}
	}

	private static Color tierColor(CoverageTier tier)
	{
		if (tier == null)
		{
			return CraftWiseTheme.TEXT_MUTED;
		}
		switch (tier)
		{
			case READY_100:
				return CraftWiseTheme.COLOR_PROFIT_GREEN;
			case READY_75:
				return CraftWiseTheme.COLOR_WARNING_YELLOW;
			case PARTIAL:
				return CraftWiseTheme.COLOR_INFO_BLUE;
			case NOT_READY:
			default:
				return CraftWiseTheme.COLOR_LOSS_RED;
		}
	}

	private static long parseLongOr(String text, long fallback)
	{
		if (text == null || text.trim().isEmpty())
		{
			return fallback;
		}
		try
		{
			return Long.parseLong(text.trim().replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static final class MatchTableModel extends AbstractTableModel
	{
		private boolean wideMode = false;
		private List<BankRecipeMatch> rows = Collections.emptyList();

		public void setWideMode(boolean wide)
		{
			if (this.wideMode != wide)
			{
				this.wideMode = wide;
				fireTableStructureChanged();
			}
		}

		public void setRows(List<BankRecipeMatch> newRows)
		{
			this.rows = newRows != null ? newRows : Collections.emptyList();
			fireTableDataChanged();
		}

		public BankRecipeMatch getRow(int r)
		{
			return (r >= 0 && r < rows.size()) ? rows.get(r) : null;
		}

		@Override public int getRowCount() { return rows.size(); }
		@Override public int getColumnCount() { return wideMode ? MATCH_WIDE_COLS.length : MATCH_COMPACT_COLS.length; }
		@Override public String getColumnName(int c) { return wideMode ? MATCH_WIDE_COLS[c] : MATCH_COMPACT_COLS[c]; }

		@Override
		public Object getValueAt(int rowIdx, int colIdx)
		{
			BankRecipeMatch m = rows.get(rowIdx);
			if (!wideMode)
			{
				switch (colIdx)
				{
					case 0:
						return m.getRecipe().getName();
					case 1:
						return NumberFormatter.formatCompact(m.getMaxCompleteCrafts(), true);
					case 2:
					default:
						return compactTierLabel(m.getCoverageTier());
				}
			}
			switch (colIdx)
			{
				case 0:
					return m.getRecipe().getName();
				case 1:
					return m.getRecipe().getRequiredLevel();
				case 2:
					return NumberFormatter.formatInt(m.getMaxCompleteCrafts());
				case 3:
					return NumberFormatter.formatInt(m.getReadyInventoryOnlyCrafts());
				case 4:
					return NumberFormatter.formatInt(m.getMaxCraftsIncludingNoted());
				case 5:
					return m.getCoverageTier().getLabel() + " (" + NumberFormatter.formatDecimal(m.getCoveragePercent(), 0) + "%)";
				case 6:
					return NumberFormatter.formatCompact(Math.round(m.getTotalXpFromOwnedStock()), true);
				case 7:
					return CurrencyFormatter.formatSignedGp(m.getTotalProfitFromOwnedStock(), true);
				case 8:
				default:
					return CurrencyFormatter.formatGp(m.getMissingForTargetCostGp(), true);
			}
		}
	}

	private final class MatchCellRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(
			JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
			setBackground(isSelected ? CraftWiseTheme.BG_CARD_HOVER : (row % 2 == 0 ? CraftWiseTheme.BG_CARD : CraftWiseTheme.BG_DEEP_CHARCOAL));
			setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			BankRecipeMatch m = matchTableModel.getRow(row);
			if (m == null)
			{
				setForeground(CraftWiseTheme.TEXT_PRIMARY);
				return c;
			}
			setToolTipText(m.getRecipe().getName() + " - " + m.getCoverageTier().getLabel()
				+ " (" + NumberFormatter.formatDecimal(m.getCoveragePercent(), 0) + "%)");
			int covCol = wideMode ? 5 : 2;
			if (column == covCol)
			{
				setForeground(tierColor(m.getCoverageTier()));
			}
			else if (wideMode && column == 7)
			{
				setForeground(m.getTotalProfitFromOwnedStock() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED);
			}
			else
			{
				setForeground(CraftWiseTheme.TEXT_PRIMARY);
			}
			return c;
		}
	}

	private static final class MaterialValueTableModel extends AbstractTableModel
	{
		private boolean wideMode = false;
		private List<BankMaterialValueEntry> rows = Collections.emptyList();

		public void setWideMode(boolean wide)
		{
			if (this.wideMode != wide)
			{
				this.wideMode = wide;
				fireTableStructureChanged();
			}
		}

		public void setRows(List<BankMaterialValueEntry> newRows)
		{
			this.rows = newRows != null ? newRows : Collections.emptyList();
			fireTableDataChanged();
		}

		public BankMaterialValueEntry getRow(int r)
		{
			return (r >= 0 && r < rows.size()) ? rows.get(r) : null;
		}

		@Override public int getRowCount() { return rows.size(); }
		@Override public int getColumnCount() { return wideMode ? MATVAL_WIDE_COLS.length : MATVAL_COMPACT_COLS.length; }
		@Override public String getColumnName(int c) { return wideMode ? MATVAL_WIDE_COLS[c] : MATVAL_COMPACT_COLS[c]; }

		@Override
		public Object getValueAt(int rowIdx, int colIdx)
		{
			BankMaterialValueEntry e = rows.get(rowIdx);
			if (!wideMode)
			{
				switch (colIdx)
				{
					case 0:
						return e.getItemName();
					case 1:
						return NumberFormatter.formatCompact(e.getTotalOwnedQty(), true);
					case 2:
					default:
						return e.getComparisonLabel();
				}
			}
			switch (colIdx)
			{
				case 0:
					return e.getItemName();
				case 1:
					return NumberFormatter.formatInt(e.getBankQty());
				case 2:
					return NumberFormatter.formatInt(e.getInvUnnotedQty());
				case 3:
					return NumberFormatter.formatInt(e.getInvNotedQty());
				case 4:
					return CurrencyFormatter.formatGp(e.getNetSellValueAfterTaxGp(), true);
				case 5:
					return CurrencyFormatter.formatGp(e.getEstimatedCraftProfitGp(), true);
				case 6:
					return CurrencyFormatter.formatSignedGp(e.getCraftAdvantageVsSellingGp(), true);
				case 7:
				default:
					return e.getComparisonLabel();
			}
		}
	}

	private final class MatValCellRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(
			JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
			setBackground(isSelected ? CraftWiseTheme.BG_CARD_HOVER : (row % 2 == 0 ? CraftWiseTheme.BG_CARD : CraftWiseTheme.BG_DEEP_CHARCOAL));
			setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			BankMaterialValueEntry e = matValTableModel.getRow(row);
			if (e == null)
			{
				setForeground(CraftWiseTheme.TEXT_PRIMARY);
				return c;
			}
			int verdictCol = wideMode ? 7 : 2;
			if (column == verdictCol || (wideMode && column == 6))
			{
				setForeground(e.getCraftAdvantageVsSellingGp() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_WARNING_YELLOW);
			}
			else
			{
				setForeground(CraftWiseTheme.TEXT_PRIMARY);
			}
			return c;
		}
	}
}
