package com.craftwise.ui;

import com.craftwise.bank.OwnedMaterialSnapshot.SnapshotView;
import com.craftwise.data.CraftingCategory;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.market.BankMarketIntegrator.IntegratedMarketView;
import com.craftwise.market.BankMarketIntegrator.IntegratedOpportunity;
import com.craftwise.market.MarketOpportunity;
import com.craftwise.market.MarketOpportunity.OpportunityStatus;
import com.craftwise.market.MarketOpportunity.PriceFreshness;
import com.craftwise.market.MarketWatcherService;
import com.craftwise.market.MarketWatcherService.AlertEvent;
import com.craftwise.market.MarketWatcherService.ScanResult;
import com.craftwise.market.MarketWatcherService.ScannerFilterCriteria;
import com.craftwise.market.MarketWatcherService.ScannerFilterMode;
import com.craftwise.market.MarketWatcherService.ScannerSortMode;
import com.craftwise.market.WatchlistManager;
import com.craftwise.market.WatchlistManager.ProfitHistoryPoint;
import com.craftwise.market.WatchlistManager.ProfitHistoryStats;
import com.craftwise.market.WatchlistManager.WatchedItemRecord;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ItemPrice;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Live Crafting Market Watcher tab (Features 1 & 3 UI).
 * Shows verified Crafting profit opportunities, bank-integrated best opportunities,
 * watchlists, alerts, and observed profit-margin history in both 225px sidebar and 720px Pop-Out modes.
 */
public class MarketWatcherPanel extends JPanel
{
	private static final String[] WIDE_COLUMNS = {
		"Item", "Lvl", "Mat Cost", "Sell", "Tax", "P/L", "GP/XP", "GP/hr", "Change", "Status"
	};

	private static final String[] COMPACT_COLUMNS = {
		"Item", "P/L", "Status"
	};

	private final CraftingDataManager dataManager;
	private final Consumer<CraftingResult> onRecipeSelected;

	private final JComboBox<ScannerFilterMode> filterCombo = new JComboBox<>(ScannerFilterMode.values());
	private final JComboBox<ScannerSortMode> sortCombo = new JComboBox<>(ScannerSortMode.values());
	private final JComboBox<Object> categoryCombo = new JComboBox<>();
	private final JTextField searchField = new JTextField();
	private final CraftWiseTheme.CraftButton advancedToggleBtn = new CraftWiseTheme.CraftButton("More");
	private final CraftWiseTheme.CraftButton scanNowBtn = new CraftWiseTheme.CraftButton("Scan Now", true);

	private final JPanel advancedFilterCard = new JPanel();
	private final JTextField minProfitField = new JTextField("");
	private final JTextField minMarginField = new JTextField("");
	private final JTextField maxCostField = new JTextField("");
	private final JTextField minGpXpField = new JTextField("");
	private final JTextField minLvlField = new JTextField("1");
	private final JTextField maxLvlField = new JTextField("99");

	private final JLabel statusSummaryLabel = new JLabel("Waiting for scan...");
	private final JPanel bestBankBannerCard = CraftWiseTheme.createGoldCard();
	private final JLabel bestBankTitleLabel = new JLabel("Best Opportunity Using Your Bank");
	private final JLabel bestBankBodyLabel = new JLabel("Open your bank in-game to match owned materials.");
	private final CraftWiseTheme.CraftButton bestBankViewBtn = new CraftWiseTheme.CraftButton("Inspect");

	private final OpportunityTableModel tableModel = new OpportunityTableModel();
	private final JTable table = new JTable(tableModel);

	// Inspector + Watchlist + History subpanel
	private final JLabel selectedTitleLabel = new JLabel("Select a recipe row above");
	private final CraftWiseTheme.CraftButton watchToggleBtn = new CraftWiseTheme.CraftButton("Watch");
	private final CraftWiseTheme.CraftButton openDetailBtn = new CraftWiseTheme.CraftButton("Breakdown", true);
	private final JPanel selectedMetricsContainer = new JPanel();
	private final JLabel historyStatsLabel = new JLabel("Observed snapshots: 0");
	private final ProfitHistoryChartCanvas historyCanvas = new ProfitHistoryChartCanvas();
	private long historyWindowSec = 24L * 3600L;

	private final JTextField watchItemField = new JTextField();
	private final CraftWiseTheme.CraftButton watchItemAddBtn = new CraftWiseTheme.CraftButton("Watch Item");
	private final JPanel watchedListContainer = new JPanel();
	private final JPanel alertsContainer = new JPanel();
	private final List<AlertEvent> recentAlerts = new ArrayList<>();

	private boolean wideMode = false;
	private boolean advancedOpen = false;
	private boolean updating = false;
	private IntegratedOpportunity selectedItem;
	private CraftingResult bestBankResult;

	public MarketWatcherPanel(
		CraftingDataManager dataManager,
		Consumer<CraftingResult> onRecipeSelected)
	{
		this.dataManager = dataManager;
		this.onRecipeSelected = onRecipeSelected;

		setLayout(new BorderLayout(0, 6));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		add(buildTopControlsAndBanner(), BorderLayout.NORTH);
		add(buildMainBody(), BorderLayout.CENTER);

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
		tableModel.setWideMode(wide);
		configureTableColumns();
	}

	public void recordAlert(AlertEvent event)
	{
		if (event == null)
		{
			return;
		}
		synchronized (recentAlerts)
		{
			recentAlerts.add(0, event);
			while (recentAlerts.size() > 15)
			{
				recentAlerts.remove(recentAlerts.size() - 1);
			}
		}
		refreshAlertsCard();
	}

	private JPanel buildTopControlsAndBanner()
	{
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		bestBankBannerCard.setLayout(new BorderLayout(6, 4));
		bestBankTitleLabel.setFont(CraftWiseTheme.getSmallBoldFont());
		bestBankTitleLabel.setForeground(CraftWiseTheme.ACCENT_GOLD);
		bestBankBodyLabel.setFont(CraftWiseTheme.getSmallFont());
		bestBankBodyLabel.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		bestBankViewBtn.setVisible(false);
		bestBankViewBtn.addActionListener(e -> {
			if (bestBankResult != null && onRecipeSelected != null)
			{
				onRecipeSelected.accept(bestBankResult);
			}
		});
		JPanel bannerText = new JPanel();
		bannerText.setLayout(new BoxLayout(bannerText, BoxLayout.Y_AXIS));
		bannerText.setOpaque(false);
		bannerText.add(bestBankTitleLabel);
		bannerText.add(Box.createVerticalStrut(2));
		bannerText.add(bestBankBodyLabel);
		bestBankBannerCard.add(bannerText, BorderLayout.CENTER);
		bestBankBannerCard.add(bestBankViewBtn, BorderLayout.EAST);
		bestBankBannerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		top.add(bestBankBannerCard);
		top.add(Box.createVerticalStrut(4));

		JPanel controls = CraftWiseTheme.createCard();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		CraftWiseTheme.styleTextField(searchField);
		searchField.setToolTipText("Search verified Crafting items or categories...");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { if (!updating) refreshTableOnly(); }
			@Override public void removeUpdate(DocumentEvent e) { if (!updating) refreshTableOnly(); }
			@Override public void changedUpdate(DocumentEvent e) { if (!updating) refreshTableOnly(); }
		});
		scanNowBtn.addActionListener(e -> dataManager.refreshPricesNowAsync());
		advancedToggleBtn.addActionListener(e -> toggleAdvancedFilters());
		JPanel btnPair = new JPanel(new GridLayout(1, 2, 4, 0));
		btnPair.setOpaque(false);
		btnPair.add(advancedToggleBtn);
		btnPair.add(scanNowBtn);
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(btnPair, BorderLayout.EAST);
		controls.add(searchRow);
		controls.add(Box.createVerticalStrut(4));

		JPanel comboRow1 = new JPanel(new GridLayout(1, 2, 4, 0));
		comboRow1.setOpaque(false);
		comboRow1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		CraftWiseTheme.styleComboBox(filterCombo);
		CraftWiseTheme.styleComboBox(sortCombo);
		CraftWiseTheme.styleComboBox(categoryCombo);
		categoryCombo.addItem("All Categories");
		for (CraftingCategory cat : CraftingCategory.values())
		{
			categoryCombo.addItem(cat);
		}
		filterCombo.addActionListener(e -> { if (!updating) refreshTableOnly(); });
		sortCombo.addActionListener(e -> { if (!updating) refreshTableOnly(); });
		categoryCombo.addActionListener(e -> { if (!updating) refreshTableOnly(); });
		comboRow1.add(filterCombo);
		comboRow1.add(categoryCombo);
		controls.add(comboRow1);
		controls.add(Box.createVerticalStrut(4));

		JPanel comboRow2 = new JPanel(new BorderLayout(6, 0));
		comboRow2.setOpaque(false);
		comboRow2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
		JLabel sortLbl = new JLabel("Sort:");
		sortLbl.setFont(CraftWiseTheme.getSmallFont());
		sortLbl.setForeground(CraftWiseTheme.TEXT_MUTED);
		comboRow2.add(sortLbl, BorderLayout.WEST);
		comboRow2.add(sortCombo, BorderLayout.CENTER);
		controls.add(comboRow2);

		advancedFilterCard.setOpaque(false);
		advancedFilterCard.setLayout(new GridLayout(2, 3, 4, 4));
		advancedFilterCard.setVisible(false);
		CraftWiseTheme.styleTextField(minProfitField);
		CraftWiseTheme.styleTextField(minMarginField);
		CraftWiseTheme.styleTextField(maxCostField);
		CraftWiseTheme.styleTextField(minGpXpField);
		CraftWiseTheme.styleTextField(minLvlField);
		CraftWiseTheme.styleTextField(maxLvlField);

		DocumentListener advDoc = new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { if (!updating) refreshTableOnly(); }
			@Override public void removeUpdate(DocumentEvent e) { if (!updating) refreshTableOnly(); }
			@Override public void changedUpdate(DocumentEvent e) { if (!updating) refreshTableOnly(); }
		};
		minProfitField.getDocument().addDocumentListener(advDoc);
		minMarginField.getDocument().addDocumentListener(advDoc);
		maxCostField.getDocument().addDocumentListener(advDoc);
		minGpXpField.getDocument().addDocumentListener(advDoc);
		minLvlField.getDocument().addDocumentListener(advDoc);
		maxLvlField.getDocument().addDocumentListener(advDoc);

		advancedFilterCard.add(CraftWiseTheme.stackedField("Min Profit GP", minProfitField));
		advancedFilterCard.add(CraftWiseTheme.stackedField("Min Margin %", minMarginField));
		advancedFilterCard.add(CraftWiseTheme.stackedField("Max Cost GP", maxCostField));
		advancedFilterCard.add(CraftWiseTheme.stackedField("Min GP/XP", minGpXpField));
		advancedFilterCard.add(CraftWiseTheme.stackedField("Min Lvl", minLvlField));
		advancedFilterCard.add(CraftWiseTheme.stackedField("Max Lvl", maxLvlField));
		controls.add(advancedFilterCard);

		controls.add(Box.createVerticalStrut(3));
		statusSummaryLabel.setFont(CraftWiseTheme.getSmallFont());
		statusSummaryLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		controls.add(statusSummaryLabel);

		top.add(controls);
		return top;
	}

	private Component buildMainBody()
	{
		table.setBackground(CraftWiseTheme.BG_CARD);
		table.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		table.setGridColor(CraftWiseTheme.BORDER_SUBTLE);
		table.setRowHeight(23);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setSelectionBackground(CraftWiseTheme.BG_CARD_HOVER);
		table.setSelectionForeground(CraftWiseTheme.TEXT_PRIMARY);
		table.setShowVerticalLines(false);
		table.setFont(CraftWiseTheme.getSmallFont());
		CraftWiseTheme.styleTableHeader(table.getTableHeader());

		table.setDefaultRenderer(Object.class, new OpportunityCellRenderer());
		table.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting())
			{
				onRowSelected();
			}
		});
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2 && selectedItem != null && onRecipeSelected != null)
				{
					onRecipeSelected.accept(selectedItem.getOpportunity().getResult());
				}
			}
		});

		JScrollPane tableScroll = new JScrollPane(table);
		CraftWiseTheme.styleScrollPane(tableScroll);
		tableScroll.setPreferredSize(new Dimension(200, 210));

		JPanel bottomContent = new CraftWiseTheme.ScrollableWidthPanel();
		bottomContent.setLayout(new BoxLayout(bottomContent, BoxLayout.Y_AXIS));
		bottomContent.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		bottomContent.add(buildSelectedInspectorCard());
		bottomContent.add(Box.createVerticalStrut(6));
		bottomContent.add(buildWatchlistCard());
		bottomContent.add(Box.createVerticalStrut(6));
		bottomContent.add(buildAlertsCard());

		JScrollPane bottomScroll = new JScrollPane(bottomContent);
		CraftWiseTheme.styleScrollPane(bottomScroll);
		bottomScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, bottomScroll);
		split.setResizeWeight(0.54);
		split.setDividerSize(4);
		split.setBorder(BorderFactory.createEmptyBorder());
		split.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		return split;
	}

	private JPanel buildSelectedInspectorCard()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		JPanel hdr = new JPanel(new BorderLayout(4, 0));
		hdr.setOpaque(false);
		selectedTitleLabel.setFont(CraftWiseTheme.getBoldFont());
		selectedTitleLabel.setForeground(CraftWiseTheme.ACCENT_GOLD);

		watchToggleBtn.addActionListener(e -> toggleWatchSelected());
		openDetailBtn.addActionListener(e -> {
			if (selectedItem != null && onRecipeSelected != null)
			{
				onRecipeSelected.accept(selectedItem.getOpportunity().getResult());
			}
		});
		JPanel btns = new JPanel(new GridLayout(1, 2, 4, 0));
		btns.setOpaque(false);
		btns.add(watchToggleBtn);
		btns.add(openDetailBtn);
		hdr.add(selectedTitleLabel, BorderLayout.CENTER);
		hdr.add(btns, BorderLayout.EAST);
		card.add(hdr);
		card.add(Box.createVerticalStrut(4));

		selectedMetricsContainer.setOpaque(false);
		selectedMetricsContainer.setLayout(new BoxLayout(selectedMetricsContainer, BoxLayout.Y_AXIS));
		card.add(selectedMetricsContainer);
		card.add(Box.createVerticalStrut(6));

		JPanel histHdr = new JPanel(new BorderLayout(4, 0));
		histHdr.setOpaque(false);
		JLabel histTitle = new JLabel("Observed Profit History");
		histTitle.setFont(CraftWiseTheme.getSmallBoldFont());
		histTitle.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		histHdr.add(histTitle, BorderLayout.WEST);

		JPanel periodBtns = new JPanel(new GridLayout(1, 4, 2, 0));
		periodBtns.setOpaque(false);
		long[] periods = {6L * 3600L, 24L * 3600L, 7L * 86400L, 30L * 86400L};
		String[] pLabels = {"6h", "24h", "7d", "30d"};
		List<CraftWiseTheme.CraftButton> pButtonList = new ArrayList<>();
		for (int i = 0; i < periods.length; i++)
		{
			final long sec = periods[i];
			CraftWiseTheme.CraftButton pb = new CraftWiseTheme.CraftButton(pLabels[i]);
			pb.setActive(sec == historyWindowSec);
			pb.addActionListener(e -> {
				historyWindowSec = sec;
				for (CraftWiseTheme.CraftButton b : pButtonList)
				{
					b.setActive(b == pb);
				}
				updateSelectedHistoryChart();
			});
			pButtonList.add(pb);
			periodBtns.add(pb);
		}
		histHdr.add(periodBtns, BorderLayout.EAST);
		card.add(histHdr);

		historyStatsLabel.setFont(CraftWiseTheme.getSmallFont());
		historyStatsLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		card.add(historyStatsLabel);
		card.add(Box.createVerticalStrut(3));
		historyCanvas.setPreferredSize(new Dimension(180, 95));
		historyCanvas.setMinimumSize(new Dimension(140, 80));
		card.add(historyCanvas);
		return card;
	}

	private JPanel buildWatchlistCard()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.add(CraftWiseTheme.createSectionHeader("Watchlist (Recipes & Items)"));
		card.add(Box.createVerticalStrut(4));

		JPanel addRow = new JPanel(new BorderLayout(4, 0));
		addRow.setOpaque(false);
		addRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		CraftWiseTheme.styleTextField(watchItemField);
		watchItemField.setToolTipText("Enter item name or ID (e.g. Uncut ruby or 1619)");
		watchItemAddBtn.addActionListener(e -> addWatchedItemFromInput());
		addRow.add(watchItemField, BorderLayout.CENTER);
		addRow.add(watchItemAddBtn, BorderLayout.EAST);
		card.add(addRow);
		card.add(Box.createVerticalStrut(4));

		watchedListContainer.setOpaque(false);
		watchedListContainer.setLayout(new BoxLayout(watchedListContainer, BoxLayout.Y_AXIS));
		card.add(watchedListContainer);
		return card;
	}

	private JPanel buildAlertsCard()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.add(CraftWiseTheme.createSectionHeader("Recent Market Alerts"));
		card.add(Box.createVerticalStrut(4));
		alertsContainer.setOpaque(false);
		alertsContainer.setLayout(new BoxLayout(alertsContainer, BoxLayout.Y_AXIS));
		card.add(alertsContainer);
		return card;
	}

	private void toggleAdvancedFilters()
	{
		advancedOpen = !advancedOpen;
		advancedFilterCard.setVisible(advancedOpen);
		advancedToggleBtn.setActive(advancedOpen);
		revalidate();
		repaint();
	}

	private void configureTableColumns()
	{
		if (table.getColumnModel().getColumnCount() == 0)
		{
			return;
		}
		if (wideMode)
		{
			int[] widths = {145, 32, 68, 68, 52, 68, 56, 72, 64, 88};
			for (int i = 0; i < Math.min(widths.length, table.getColumnModel().getColumnCount()); i++)
			{
				table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
			}
		}
		else if (table.getColumnModel().getColumnCount() >= 3)
		{
			table.getColumnModel().getColumn(0).setPreferredWidth(175);
			table.getColumnModel().getColumn(1).setPreferredWidth(62);
			table.getColumnModel().getColumn(2).setPreferredWidth(64);
		}
	}

	public void refresh()
	{
		refreshBanner();
		refreshTableOnly();
		refreshWatchlistCard();
		refreshAlertsCard();
	}

	private void refreshBanner()
	{
		IntegratedMarketView view = dataManager.getLatestIntegratedMarketView();
		SnapshotView snap = dataManager.getBankSnapshotView();
		if (snap == null || !snap.isBankScanned())
		{
			bestBankTitleLabel.setText("Best Opportunity Using Your Bank");
			bestBankBodyLabel.setText("BANK DATA UNAVAILABLE — open your bank in-game to scan materials.");
			bestBankResult = null;
			bestBankViewBtn.setVisible(false);
			return;
		}
		if (view != null && view.getBestOpportunityUsingYourBank() != null)
		{
			IntegratedOpportunity best = view.getBestOpportunityUsingYourBank();
			CraftingResult res = best.getOpportunity().getResult();
			bestBankResult = res;
			String prefix = best.getOpportunity().getStatus() == OpportunityStatus.NEWLY_PROFITABLE
				? "[NEWLY PROFITABLE] "
				: "";
			bestBankTitleLabel.setText("Best Opportunity Using Your Bank (" + view.getProfitableWithBankCount() + " ready)");
			bestBankBodyLabel.setText(String.format("<html>%s<b>%s</b>: %,d craftable<br>Est. <b>%s</b> (%s/craft)</html>",
				prefix,
				res.getRecipe().getName(),
				best.getMaxCraftableFromBank(),
				CurrencyFormatter.formatSignedGp(best.getImmediateProfitFromBankGp(), true),
				CurrencyFormatter.formatSignedGp(res.getProfitPerCraft(), true)));
			bestBankViewBtn.setVisible(true);
		}
		else
		{
			bestBankTitleLabel.setText("Best Opportunity Using Your Bank");
			bestBankBodyLabel.setText("<html>No owned bank materials currently match a profitable unlocked recipe.</html>");
			bestBankResult = null;
			bestBankViewBtn.setVisible(false);
		}
	}

	private void refreshTableOnly()
	{
		ScanResult scan = dataManager.getLatestMarketScan();
		MarketWatcherService service = dataManager.getMarketWatcherService();
		if (scan == null || service == null)
		{
			tableModel.setRows(Collections.emptyList());
			statusSummaryLabel.setText("Verified Market Scanner: awaiting initial prices...");
			return;
		}

		ScannerFilterMode fMode = (ScannerFilterMode) filterCombo.getSelectedItem();
		if (fMode == null)
		{
			fMode = ScannerFilterMode.ALL;
		}
		ScannerSortMode sMode = (ScannerSortMode) sortCombo.getSelectedItem();
		if (sMode == null)
		{
			sMode = ScannerSortMode.PROFIT_PER_ACTION;
		}
		CraftingCategory cat = categoryCombo.getSelectedItem() instanceof CraftingCategory
			? (CraftingCategory) categoryCombo.getSelectedItem()
			: null;

		boolean filterByBank = fMode == ScannerFilterMode.PROFITABLE_WITH_MY_BANK;
		ScannerFilterCriteria criteria = ScannerFilterCriteria.builder()
			.mode(filterByBank ? ScannerFilterMode.ALL : fMode)
			.category(cat)
			.searchQuery(searchField.getText())
			.minProfitGp(parseDoubleOr(minProfitField.getText(), -Double.MAX_VALUE))
			.minMarginPercent(parseDoubleOr(minMarginField.getText(), -Double.MAX_VALUE))
			.maxMaterialCostGp(parseDoubleOr(maxCostField.getText(), Double.MAX_VALUE))
			.minGpPerXp(parseDoubleOr(minGpXpField.getText(), -Double.MAX_VALUE))
			.minLevel((int) Math.max(1L, Math.round(parseDoubleOr(minLvlField.getText(), 1.0))))
			.maxLevel((int) Math.min(99L, Math.round(parseDoubleOr(maxLvlField.getText(), 99.0))))
			.build();

		List<MarketOpportunity> matched = service.filterAndSort(scan.getOpportunities(), criteria, sMode);

		IntegratedMarketView iv = dataManager.getLatestIntegratedMarketView();
		Map<String, IntegratedOpportunity> byId = new HashMap<>();
		if (iv != null)
		{
			for (IntegratedOpportunity io : iv.getItems())
			{
				byId.put(io.getOpportunity().getResult().getRecipe().getId(), io);
			}
		}

		List<IntegratedOpportunity> rows = new ArrayList<>();
		for (MarketOpportunity opp : matched)
		{
			IntegratedOpportunity io = byId.get(opp.getResult().getRecipe().getId());
			if (io == null)
			{
				io = IntegratedOpportunity.builder()
					.opportunity(opp)
					.bankDataAvailable(false)
					.build();
			}
			if (filterByBank && !io.isProfitableWithMyBank())
			{
				continue;
			}
			rows.add(io);
		}

		tableModel.setRows(rows);

		int profCount = 0;
		int newCount = 0;
		int freshCount = 0;
		for (IntegratedOpportunity r : rows)
		{
			if (r.getOpportunity().isProfitable())
			{
				profCount++;
			}
			if (r.getOpportunity().getStatus() == OpportunityStatus.NEWLY_PROFITABLE)
			{
				newCount++;
			}
			if (r.getOpportunity().getFreshness() == PriceFreshness.FRESH)
			{
				freshCount++;
			}
		}
		long ageSec = Math.max(0L, Instant.now().getEpochSecond() - scan.getScannedAtEpochSec());
		statusSummaryLabel.setText(String.format(
			"%d verified | %d profitable | %d new | %d fresh | checked %ds ago",
			rows.size(), profCount, newCount, freshCount, ageSec));

		if (!rows.isEmpty() && table.getSelectedRow() < 0)
		{
			table.setRowSelectionInterval(0, 0);
		}
		else if (selectedItem != null)
		{
			String selId = selectedItem.getOpportunity().getResult().getRecipe().getId();
			for (IntegratedOpportunity r : rows)
			{
				if (r.getOpportunity().getResult().getRecipe().getId().equals(selId))
				{
					selectedItem = r;
					updateSelectedInspector();
					break;
				}
			}
		}
	}

	private void onRowSelected()
	{
		int row = table.getSelectedRow();
		if (row >= 0)
		{
			selectedItem = tableModel.getRow(row);
			updateSelectedInspector();
		}
	}

	private void updateSelectedInspector()
	{
		selectedMetricsContainer.removeAll();
		if (selectedItem == null)
		{
			selectedTitleLabel.setText("Select a recipe row above");
			watchToggleBtn.setText("Watch");
			openDetailBtn.setEnabled(false);
			selectedMetricsContainer.revalidate();
			selectedMetricsContainer.repaint();
			return;
		}
		openDetailBtn.setEnabled(true);
		MarketOpportunity opp = selectedItem.getOpportunity();
		CraftingResult res = opp.getResult();
		CraftingRecipe rec = res.getRecipe();
		WatchlistManager wl = dataManager.getWatchlistManager();
		boolean watched = wl.isRecipeWatched(rec.getId());

		selectedTitleLabel.setText(rec.getName() + " (Lvl " + rec.getRequiredLevel() + ")");
		watchToggleBtn.setText(watched ? "Unwatch" : "Watch");
		watchToggleBtn.setActive(watched);

		Color pCol = res.isProfitable() ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Status / Freshness",
			opp.getStatus().getLabel() + " (" + opp.getFreshness().getLabel() + ")", statusColor(opp.getStatus())));
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Profit / Craft",
			CurrencyFormatter.formatSignedGp(res.getProfitPerCraft(), false), pCol));
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Profit / Inv (" + res.getItemsPerInventory() + ")",
			CurrencyFormatter.formatSignedGp(res.getProfitPerInventory(), false), pCol));
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Profit / 100 | 1,000",
			CurrencyFormatter.formatSignedGp(res.getProfit100(), true) + " | "
				+ CurrencyFormatter.formatSignedGp(res.getProfit1000(), true), pCol));
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Mat Cost / Net Rev / Tax",
			CurrencyFormatter.formatGp(Math.round(res.getMaterialCostPerCraft()), true) + " / "
				+ CurrencyFormatter.formatGp(Math.round(res.getNetRevenuePerCraft()), true) + " / "
				+ CurrencyFormatter.formatGp(Math.round(res.getGeTaxPerCraft()), true), CraftWiseTheme.TEXT_PRIMARY));
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Break-Even Sell",
			res.getBreakEvenSellPrice() != null ? CurrencyFormatter.formatGp(res.getBreakEvenSellPrice(), false) : "N/A",
			CraftWiseTheme.COLOR_WARNING_YELLOW));
		selectedMetricsContainer.add(CraftWiseTheme.kvRow("Est. GP/hr | GP/XP",
			CurrencyFormatter.formatSignedGp(res.getProfitPerHour(), true) + "/hr | "
				+ NumberFormatter.formatDecimal(res.getGpPerXp(), 1), CraftWiseTheme.TEXT_PRIMARY));
		if (selectedItem.isBankDataAvailable())
		{
			selectedMetricsContainer.add(CraftWiseTheme.kvRow("Craftable From Bank",
				String.format("%,d actions (%s)",
					selectedItem.getMaxCraftableFromBank(),
					CurrencyFormatter.formatSignedGp(selectedItem.getImmediateProfitFromBankGp(), true)),
				selectedItem.getMaxCraftableFromBank() > 0L ? CraftWiseTheme.COLOR_INFO_BLUE : CraftWiseTheme.TEXT_MUTED));
		}

		updateSelectedHistoryChart();
		selectedMetricsContainer.revalidate();
		selectedMetricsContainer.repaint();
	}

	private void updateSelectedHistoryChart()
	{
		if (selectedItem == null)
		{
			historyStatsLabel.setText("Observed snapshots: 0");
			historyCanvas.setPoints(Collections.emptyList());
			return;
		}
		String id = selectedItem.getOpportunity().getResult().getRecipe().getId();
		long now = Instant.now().getEpochSecond();
		WatchlistManager wl = dataManager.getWatchlistManager();
		List<ProfitHistoryPoint> pts = wl.getHistoryWindow(id, historyWindowSec, now);
		ProfitHistoryStats stats = WatchlistManager.computeStats(pts);

		if (stats.getSampleCount() == 0)
		{
			historyStatsLabel.setText("0 snapshots yet — history builds automatically on each scan.");
		}
		else
		{
			historyStatsLabel.setText(String.format("n=%d | Best %s | Worst %s | Avg %s | %s",
				stats.getSampleCount(),
				CurrencyFormatter.formatSignedGp(stats.getBestProfitGp(), true),
				CurrencyFormatter.formatSignedGp(stats.getWorstProfitGp(), true),
				CurrencyFormatter.formatSignedGp(stats.getAverageProfitGp(), true),
				stats.getTrendLabel()));
		}
		historyCanvas.setPoints(pts);
	}

	private void toggleWatchSelected()
	{
		if (selectedItem == null)
		{
			return;
		}
		String id = selectedItem.getOpportunity().getResult().getRecipe().getId();
		WatchlistManager wl = dataManager.getWatchlistManager();
		if (wl.isRecipeWatched(id))
		{
			wl.removeWatchedRecipe(id);
		}
		else
		{
			wl.addWatchedRecipe(id);
		}
		dataManager.recalculateAll();
		refresh();
	}

	private void addWatchedItemFromInput()
	{
		String raw = watchItemField.getText() != null ? watchItemField.getText().trim() : "";
		if (raw.isEmpty())
		{
			return;
		}
		Integer resolvedId = null;
		try
		{
			resolvedId = Integer.parseInt(raw);
		}
		catch (NumberFormatException ignored)
		{
			Map<String, Integer> idx = dataManager.getWikiPriceClient() != null
				? dataManager.getWikiPriceClient().getNameToIdIndex()
				: Collections.emptyMap();
			resolvedId = idx.get(raw.toLowerCase(java.util.Locale.ROOT));
		}
		if (resolvedId != null && resolvedId > 0)
		{
			ItemPrice ip = dataManager.getCurrentPrices().get(resolvedId);
			String iName = (ip != null && ip.getName() != null) ? ip.getName() : raw;
			dataManager.getWatchlistManager().addWatchedItem(resolvedId, iName);
			watchItemField.setText("");
			dataManager.recalculateAll();
			refresh();
		}
	}

	private void refreshWatchlistCard()
	{
		watchedListContainer.removeAll();
		WatchlistManager wl = dataManager.getWatchlistManager();
		Set<String> recipes = wl.getWatchedRecipeIds();
		List<WatchedItemRecord> itemRecs = wl.getWatchedItems();
		if (recipes.isEmpty() && itemRecs.isEmpty())
		{
			JLabel empty = new JLabel("No watched recipes or items yet. Select a row & click Watch.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			watchedListContainer.add(empty);
		}
		else
		{
			Map<Integer, ItemPrice> prices = dataManager.getCurrentPrices();
			for (String rid : recipes)
			{
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setOpaque(false);
				JLabel lbl = new JLabel("Recipe: " + rid);
				lbl.setFont(CraftWiseTheme.getSmallFont());
				lbl.setForeground(CraftWiseTheme.TEXT_PRIMARY);
				CraftWiseTheme.CraftButton rem = new CraftWiseTheme.CraftButton("X");
				rem.addActionListener(e -> {
					wl.removeWatchedRecipe(rid);
					dataManager.recalculateAll();
					refresh();
				});
				row.add(lbl, BorderLayout.CENTER);
				row.add(rem, BorderLayout.EAST);
				watchedListContainer.add(row);
			}
			for (WatchedItemRecord rec : itemRecs)
			{
				int iid = rec.getItemId();
				ItemPrice ip = prices.get(iid);
				String name = ip != null && ip.getName() != null ? ip.getName() : rec.getItemName();
				String pStr = ip != null ? CurrencyFormatter.formatGp(ip.getHighPrice() > 0 ? ip.getHighPrice() : ip.getLowPrice(), true) : "N/A";
				JPanel row = new JPanel(new BorderLayout(4, 0));
				row.setOpaque(false);
				JLabel lbl = new JLabel(name + " (" + pStr + ")");
				lbl.setFont(CraftWiseTheme.getSmallFont());
				lbl.setForeground(CraftWiseTheme.COLOR_INFO_BLUE);
				CraftWiseTheme.CraftButton rem = new CraftWiseTheme.CraftButton("X");
				rem.addActionListener(e -> {
					wl.removeWatchedItem(iid);
					dataManager.recalculateAll();
					refresh();
				});
				row.add(lbl, BorderLayout.CENTER);
				row.add(rem, BorderLayout.EAST);
				watchedListContainer.add(row);
			}
		}
		watchedListContainer.revalidate();
		watchedListContainer.repaint();
	}

	private void refreshAlertsCard()
	{
		alertsContainer.removeAll();
		List<AlertEvent> snapshotList;
		synchronized (recentAlerts)
		{
			snapshotList = new ArrayList<>(recentAlerts);
		}
		if (snapshotList.isEmpty())
		{
			JLabel empty = new JLabel("No alerts fired this session. Thresholds configured in Plugin Settings.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			alertsContainer.add(empty);
		}
		else
		{
			for (AlertEvent ev : snapshotList)
			{
				JLabel l = new JLabel("<html><b>[" + ev.getAlertType() + "]</b> " + ev.getMessage() + "</html>");
				l.setFont(CraftWiseTheme.getSmallFont());
				l.setForeground(ev.getProfitPerCraft() >= 0L ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED);
				l.setToolTipText(ev.getMessage());
				alertsContainer.add(l);
			}
		}
		alertsContainer.revalidate();
		alertsContainer.repaint();
	}

	private static String compactStatusLabel(OpportunityStatus st)
	{
		if (st == null)
		{
			return "N/A";
		}
		switch (st)
		{
			case NEWLY_PROFITABLE:
				return "New +";
			case HIGH_PROFIT:
				return "High +";
			case IMPROVING:
				return "Rising";
			case NEAR_BREAK_EVEN:
				return "Break~";
			case DECLINING:
				return "Drop";
			case UNPROFITABLE:
				return "Loss";
			case STABLE_PROFIT:
				return "Profit";
			case UNAVAILABLE:
			default:
				return "N/A";
		}
	}

	private static Color statusColor(OpportunityStatus st)
	{
		if (st == null)
		{
			return CraftWiseTheme.TEXT_MUTED;
		}
		switch (st)
		{
			case NEWLY_PROFITABLE:
			case HIGH_PROFIT:
			case IMPROVING:
			case STABLE_PROFIT:
				return CraftWiseTheme.COLOR_PROFIT_GREEN;
			case NEAR_BREAK_EVEN:
				return CraftWiseTheme.COLOR_WARNING_YELLOW;
			case DECLINING:
			case UNPROFITABLE:
				return CraftWiseTheme.COLOR_LOSS_RED;
			default:
				return CraftWiseTheme.TEXT_MUTED;
		}
	}

	private static double parseDoubleOr(String text, double fallback)
	{
		if (text == null || text.trim().isEmpty())
		{
			return fallback;
		}
		try
		{
			return Double.parseDouble(text.trim().replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static final class OpportunityTableModel extends AbstractTableModel
	{
		private boolean wideMode = false;
		private List<IntegratedOpportunity> rows = Collections.emptyList();

		public void setWideMode(boolean wide)
		{
			if (this.wideMode != wide)
			{
				this.wideMode = wide;
				fireTableStructureChanged();
			}
		}

		public void setRows(List<IntegratedOpportunity> newRows)
		{
			this.rows = newRows != null ? newRows : Collections.emptyList();
			fireTableDataChanged();
		}

		public IntegratedOpportunity getRow(int r)
		{
			return (r >= 0 && r < rows.size()) ? rows.get(r) : null;
		}

		@Override public int getRowCount() { return rows.size(); }
		@Override public int getColumnCount() { return wideMode ? WIDE_COLUMNS.length : COMPACT_COLUMNS.length; }
		@Override public String getColumnName(int col) { return wideMode ? WIDE_COLUMNS[col] : COMPACT_COLUMNS[col]; }

		@Override
		public Object getValueAt(int rowIdx, int colIdx)
		{
			IntegratedOpportunity io = rows.get(rowIdx);
			MarketOpportunity opp = io.getOpportunity();
			CraftingResult res = opp.getResult();
			CraftingRecipe rec = res.getRecipe();
			String name = (opp.isWatched() ? "* " : "") + rec.getName()
				+ (io.isProfitableWithMyBank() ? " [Bank]" : "");

			if (!wideMode)
			{
				switch (colIdx)
				{
					case 0:
						return name;
					case 1:
						return res.isPriceDataComplete()
							? CurrencyFormatter.formatSignedGp(res.getProfitPerCraft(), true)
							: "N/A";
					case 2:
					default:
						return compactStatusLabel(opp.getStatus());
				}
			}

			switch (colIdx)
			{
				case 0:
					return name;
				case 1:
					return rec.getRequiredLevel();
				case 2:
					return res.isPriceDataComplete() ? CurrencyFormatter.formatGp(Math.round(res.getMaterialCostPerCraft()), true) : "N/A";
				case 3:
					return res.isPriceDataComplete() ? CurrencyFormatter.formatGp(Math.round(res.getGrossRevenuePerCraft()), true) : "N/A";
				case 4:
					return res.isPriceDataComplete() ? CurrencyFormatter.formatGp(Math.round(res.getGeTaxPerCraft()), true) : "N/A";
				case 5:
					return res.isPriceDataComplete() ? CurrencyFormatter.formatSignedGp(res.getProfitPerCraft(), true) : "N/A";
				case 6:
					return res.isPriceDataComplete() && !Double.isNaN(res.getGpPerXp())
						? NumberFormatter.formatDecimal(res.getGpPerXp(), 1)
						: "N/A";
				case 7:
					return res.isPriceDataComplete() ? CurrencyFormatter.formatSignedGp(res.getProfitPerHour(), true) : "N/A";
				case 8:
					return opp.getPreviousProfitPerCraft() != null
						? CurrencyFormatter.formatSignedGp(opp.getProfitDeltaGp(), true)
						: "-";
				case 9:
				default:
					return opp.getStatus().getLabel();
			}
		}
	}

	private final class OpportunityCellRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(
			JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int column)
		{
			Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
			setBackground(isSelected ? CraftWiseTheme.BG_CARD_HOVER : (row % 2 == 0 ? CraftWiseTheme.BG_CARD : CraftWiseTheme.BG_DEEP_CHARCOAL));
			setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);

			IntegratedOpportunity io = tableModel.getRow(row);
			if (io == null)
			{
				setForeground(CraftWiseTheme.TEXT_PRIMARY);
				return c;
			}
			MarketOpportunity opp = io.getOpportunity();
			setToolTipText(opp.getResult().getRecipe().getName() + " — " + opp.getStatus().getLabel()
				+ " (" + opp.getFreshness().getLabel() + ")");
			int profitCol = wideMode ? 5 : 1;
			int statusCol = wideMode ? 9 : 2;
			if (column == statusCol)
			{
				setForeground(statusColor(opp.getStatus()));
			}
			else if (column == profitCol || (wideMode && (column == 7 || column == 8)))
			{
				setForeground(opp.isProfitable() ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED);
			}
			else if (column == 0 && io.isProfitableWithMyBank())
			{
				setForeground(CraftWiseTheme.COLOR_INFO_BLUE);
			}
			else
			{
				setForeground(CraftWiseTheme.TEXT_PRIMARY);
			}
			return c;
		}
	}

	private static final class ProfitHistoryChartCanvas extends JPanel
	{
		private List<ProfitHistoryPoint> points = Collections.emptyList();

		ProfitHistoryChartCanvas()
		{
			setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
			setBorder(BorderFactory.createLineBorder(CraftWiseTheme.BORDER_SUBTLE));
		}

		public void setPoints(List<ProfitHistoryPoint> pts)
		{
			this.points = pts != null ? pts : Collections.emptyList();
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			int h = getHeight();
			if (points.size() < 2)
			{
				g2.setFont(CraftWiseTheme.getSmallFont());
				g2.setColor(CraftWiseTheme.TEXT_MUTED);
				String msg = points.isEmpty() ? "No profit history recorded yet" : "1 snapshot recorded; waiting for next scan";
				FontMetrics fm = g2.getFontMetrics();
				g2.drawString(msg, Math.max(8, (w - fm.stringWidth(msg)) / 2), h / 2);
				g2.dispose();
				return;
			}

			int padL = 8;
			int padR = 8;
			int padT = 10;
			int padB = 10;
			double minV = Double.MAX_VALUE;
			double maxV = -Double.MAX_VALUE;
			for (ProfitHistoryPoint p : points)
			{
				minV = Math.min(minV, p.getProfitGp());
				maxV = Math.max(maxV, p.getProfitGp());
			}
			minV = Math.min(minV, 0.0);
			maxV = Math.max(maxV, 0.0);
			if (Math.abs(maxV - minV) < 1.0)
			{
				maxV += 1.0;
				minV -= 1.0;
			}

			int cw = Math.max(10, w - padL - padR);
			int ch = Math.max(10, h - padT - padB);
			int zeroY = padT + (int) Math.round(((maxV - 0.0) / (maxV - minV)) * ch);
			g2.setColor(CraftWiseTheme.BORDER_DEFAULT);
			g2.drawLine(padL, zeroY, padL + cw, zeroY);

			g2.setStroke(new BasicStroke(1.8f));
			int n = points.size();
			for (int i = 1; i < n; i++)
			{
				int x1 = padL + (int) Math.round(((i - 1) * 1.0 / (n - 1)) * cw);
				int x2 = padL + (int) Math.round((i * 1.0 / (n - 1)) * cw);
				int y1 = padT + (int) Math.round(((maxV - points.get(i - 1).getProfitGp()) / (maxV - minV)) * ch);
				int y2 = padT + (int) Math.round(((maxV - points.get(i).getProfitGp()) / (maxV - minV)) * ch);
				g2.setColor(points.get(i).getProfitGp() >= 0L ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED);
				g2.drawLine(x1, y1, x2, y2);
			}
			g2.dispose();
		}
	}
}
