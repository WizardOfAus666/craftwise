package com.craftwise.ui;

import com.craftwise.CraftWiseConfig.RankingMode;
import com.craftwise.data.CraftingCategory;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingDataManager.MethodFilter;
import com.craftwise.model.CraftingResult;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * Searchable, filterable, sortable Crafting table panel.
 * Fits cleanly in the 225px sidebar (3 auto-sized columns) and expands to 8 columns when popped out wide.
 */
public class RecipeTablePanel extends JPanel
{
	public enum ViewMode
	{
		RECOMMENDATIONS,
		CRAFTING_METHODS
	}

	private static final String[] WIDE_COLUMNS = {
		"Method", "Lvl", "XP", "Cost", "Net Rev", "Profit", "GP/XP", "XP/hr"
	};

	private static final String[] COMPACT_COLUMNS = {
		"Method", "Profit", "XP/hr"
	};

	private final CraftingDataManager dataManager;
	private final ViewMode viewMode;
	private final Consumer<CraftingResult> onRecipeSelected;

	private final JTextField searchField = new JTextField();
	private final CraftWiseTheme.CraftButton filtersToggleBtn = new CraftWiseTheme.CraftButton("Filters");
	private final JPanel advancedFiltersPanel = new JPanel();
	private final JComboBox<Object> categoryCombo = new JComboBox<>();
	private final JComboBox<MethodFilter> filterCombo = new JComboBox<>(MethodFilter.values());
	private final JComboBox<RankingMode> rankingCombo = new JComboBox<>(RankingMode.values());
	private final JTextField minLvlField = new JTextField("1");
	private final JTextField minXpHrField = new JTextField("");
	private final JTextField maxBudgetField = new JTextField("");

	private final JLabel countSummaryLabel = new JLabel();
	private final RecipeTableModel tableModel = new RecipeTableModel();
	private final JTable table = new JTable(tableModel);

	private boolean wideMode = false;
	private boolean advancedOpen = false;
	private int sortLogicalCol = -1;
	private boolean sortAscending = false;
	private boolean updating = false;

	public RecipeTablePanel(
		CraftingDataManager dataManager,
		ViewMode viewMode,
		Consumer<CraftingResult> onRecipeSelected)
	{
		this.dataManager = dataManager;
		this.viewMode = viewMode;
		this.onRecipeSelected = onRecipeSelected;

		setLayout(new BorderLayout(0, 6));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		add(buildControlsPanel(), BorderLayout.NORTH);
		add(buildTableScrollPane(), BorderLayout.CENTER);
		add(buildFooterBar(), BorderLayout.SOUTH);

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

	private JPanel buildControlsPanel()
	{
		JPanel card = CraftWiseTheme.createCard();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.styleTextField(searchField);
		searchField.setToolTipText("Search crafting recipes...");
		filtersToggleBtn.setPreferredSize(new Dimension(54, 24));
		filtersToggleBtn.addActionListener(e -> {
			advancedOpen = !advancedOpen;
			advancedFiltersPanel.setVisible(advancedOpen);
			filtersToggleBtn.setActive(advancedOpen);
			card.revalidate();
		});
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(filtersToggleBtn, BorderLayout.EAST);
		card.add(searchRow);
		card.add(Box.createVerticalStrut(5));

		categoryCombo.addItem("All Categories");
		for (CraftingCategory cat : CraftingCategory.values())
		{
			categoryCombo.addItem(cat);
		}
		CraftWiseTheme.styleComboBox(categoryCombo);
		CraftWiseTheme.styleComboBox(filterCombo);
		CraftWiseTheme.styleComboBox(rankingCombo);

		if (viewMode == ViewMode.RECOMMENDATIONS && dataManager.getConfig() != null)
		{
			rankingCombo.setSelectedItem(dataManager.getConfig().preferredRankingMode());
			filterCombo.setSelectedItem(dataManager.getConfig().showFutureUnlocks()
				? MethodFilter.ALL_METHODS
				: MethodFilter.UNLOCKED_METHODS);
		}
		else
		{
			filterCombo.setSelectedItem(MethodFilter.ALL_METHODS);
		}

		JPanel catFilterRow = new JPanel(new GridLayout(1, 2, 4, 0));
		catFilterRow.setOpaque(false);
		catFilterRow.setAlignmentX(LEFT_ALIGNMENT);
		catFilterRow.add(categoryCombo);
		catFilterRow.add(filterCombo);
		card.add(catFilterRow);
		card.add(Box.createVerticalStrut(4));

		rankingCombo.setAlignmentX(LEFT_ALIGNMENT);
		card.add(rankingCombo);

		advancedFiltersPanel.setLayout(new BoxLayout(advancedFiltersPanel, BoxLayout.Y_AXIS));
		advancedFiltersPanel.setOpaque(false);
		advancedFiltersPanel.setAlignmentX(LEFT_ALIGNMENT);
		advancedFiltersPanel.setVisible(false);

		advancedFiltersPanel.add(Box.createVerticalStrut(5));
		JPanel numRow = new JPanel(new GridLayout(1, 3, 4, 0));
		numRow.setOpaque(false);
		numRow.add(CraftWiseTheme.stackedField("Min Lvl", styleNumericField(minLvlField)));
		numRow.add(CraftWiseTheme.stackedField("Min XP/h", styleNumericField(minXpHrField)));
		numRow.add(CraftWiseTheme.stackedField("Budget", styleNumericField(maxBudgetField)));
		advancedFiltersPanel.add(numRow);
		card.add(advancedFiltersPanel);

		DocumentListener docListener = new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { if (!updating) applyFilters(); }
			@Override public void removeUpdate(DocumentEvent e) { if (!updating) applyFilters(); }
			@Override public void changedUpdate(DocumentEvent e) { if (!updating) applyFilters(); }
		};
		searchField.getDocument().addDocumentListener(docListener);
		minLvlField.getDocument().addDocumentListener(docListener);
		minXpHrField.getDocument().addDocumentListener(docListener);
		maxBudgetField.getDocument().addDocumentListener(docListener);

		categoryCombo.addActionListener(e -> { if (!updating) applyFilters(); });
		filterCombo.addActionListener(e -> { if (!updating) applyFilters(); });
		rankingCombo.addActionListener(e -> {
			if (!updating)
			{
				sortLogicalCol = -1;
				applyFilters();
			}
		});

		return card;
	}

	private JScrollPane buildTableScrollPane()
	{
		table.setBackground(CraftWiseTheme.BG_CARD);
		table.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		table.setGridColor(CraftWiseTheme.BORDER_SUBTLE);
		table.setSelectionBackground(CraftWiseTheme.ACCENT_GOLD_DARK);
		table.setSelectionForeground(CraftWiseTheme.TEXT_PRIMARY);
		table.setRowHeight(27);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setFillsViewportHeight(true);
		table.setShowVerticalLines(false);
		table.setIntercellSpacing(new Dimension(2, 0));

		JTableHeader header = table.getTableHeader();
		CraftWiseTheme.styleTableHeader(header);
		header.setPreferredSize(new Dimension(0, 24));

		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int viewCol = table.columnAtPoint(e.getPoint());
				if (viewCol >= 0)
				{
					int logicalCol = toLogicalCol(viewCol);
					if (sortLogicalCol == logicalCol)
					{
						sortAscending = !sortAscending;
					}
					else
					{
						sortLogicalCol = logicalCol;
						sortAscending = (logicalCol == 0 || logicalCol == 1 || logicalCol == 3);
					}
					applySortAndUpdate();
				}
			}
		});

		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				int row = table.rowAtPoint(e.getPoint());
				if (row >= 0 && row < tableModel.getRowCount())
				{
					CraftingResult clicked = tableModel.getRow(row);
					if (clicked != null && onRecipeSelected != null)
					{
						onRecipeSelected.accept(clicked);
					}
				}
			}
		});

		JScrollPane sp = new JScrollPane(table);
		sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		CraftWiseTheme.styleScrollPane(sp);
		sp.setBorder(BorderFactory.createLineBorder(CraftWiseTheme.BORDER_SUBTLE, 1));
		return sp;
	}

	private void configureTableColumns()
	{
		RecipeCellRenderer renderer = new RecipeCellRenderer();
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		if (wideMode)
		{
			int[] widths = {190, 36, 52, 68, 68, 68, 60, 68};
			for (int i = 0; i < widths.length; i++)
			{
				table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
				table.getColumnModel().getColumn(i).setCellRenderer(renderer);
			}
		}
		else
		{
			int[] widths = {120, 52, 48};
			for (int i = 0; i < widths.length; i++)
			{
				table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
				table.getColumnModel().getColumn(i).setCellRenderer(renderer);
			}
		}
	}

	private int toLogicalCol(int viewCol)
	{
		if (wideMode)
		{
			return viewCol;
		}
		if (viewCol == 1) return 5;
		if (viewCol == 2) return 7;
		return 0;
	}

	private JPanel buildFooterBar()
	{
		JPanel footer = new JPanel(new BorderLayout(4, 0));
		footer.setOpaque(false);
		countSummaryLabel.setFont(CraftWiseTheme.getSmallFont());
		countSummaryLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		JLabel hint = new JLabel(wideMode ? "Click row for details" : "Click row · Expand for table");
		hint.setFont(CraftWiseTheme.getSmallFont());
		hint.setForeground(CraftWiseTheme.TEXT_LOCKED);
		footer.add(countSummaryLabel, BorderLayout.CENTER);
		footer.add(hint, BorderLayout.EAST);
		return footer;
	}

	public void refresh()
	{
		applyFilters();
	}

	private void applyFilters()
	{
		Object selCat = categoryCombo.getSelectedItem();
		CraftingCategory cat = selCat instanceof CraftingCategory ? (CraftingCategory) selCat : null;
		MethodFilter mf = (MethodFilter) filterCombo.getSelectedItem();
		RankingMode rm = (RankingMode) rankingCombo.getSelectedItem();

		int minLvl = parsePositiveInt(minLvlField.getText(), 1);
		double minXpHr = parseDoubleOrZero(minXpHrField.getText());
		Long budgetParsed = CurrencyFormatter.parseGpAmount(maxBudgetField.getText());
		long maxBudget = budgetParsed != null ? budgetParsed : -1L;

		List<CraftingResult> queried = dataManager.queryResults(
			searchField.getText(),
			cat,
			mf,
			rm,
			minLvl,
			99,
			0.0,
			minXpHr,
			maxBudget,
			-1.0
		);

		tableModel.setRows(queried);
		if (sortLogicalCol >= 0)
		{
			applySortAndUpdate();
		}
		else
		{
			updateSummaryCount();
		}
	}

	private void applySortAndUpdate()
	{
		List<CraftingResult> current = new ArrayList<>(tableModel.getRows());
		if (sortLogicalCol >= 0 && !current.isEmpty())
		{
			Comparator<CraftingResult> baseCmp = comparatorForColumn(sortLogicalCol);
			Comparator<CraftingResult> cmp = sortAscending ? baseCmp : baseCmp.reversed();
			Comparator<CraftingResult> finalCmp = (a, b) -> {
				if (a.isPriceDataComplete() != b.isPriceDataComplete() && sortLogicalCol >= 3 && sortLogicalCol <= 6)
				{
					return a.isPriceDataComplete() ? -1 : 1;
				}
				return cmp.compare(a, b);
			};
			current.sort(finalCmp);
			tableModel.setRows(current);
		}
		updateSummaryCount();
	}

	private void updateSummaryCount()
	{
		int total = tableModel.getRowCount();
		int profitable = 0;
		for (CraftingResult r : tableModel.getRows())
		{
			if (r.isProfitable())
			{
				profitable++;
			}
		}
		countSummaryLabel.setText(total + " recipes · " + profitable + " profit");
	}

	private Comparator<CraftingResult> comparatorForColumn(int col)
	{
		switch (col)
		{
			case 0:
				return Comparator.comparing(r -> r.getRecipe().getName(), String.CASE_INSENSITIVE_ORDER);
			case 1:
				return Comparator.comparingInt(r -> r.getRecipe().getRequiredLevel());
			case 2:
				return Comparator.comparingDouble(CraftingResult::getXpPerAction);
			case 3:
				return Comparator.comparingDouble(CraftingResult::getMaterialCostPerCraft);
			case 4:
				return Comparator.comparingDouble(CraftingResult::getNetRevenuePerCraft);
			case 5:
				return Comparator.comparingDouble(CraftingResult::getProfitPerCraft);
			case 6:
				return Comparator.comparingDouble(r -> Double.isNaN(r.getGpPerXp()) ? -1e9 : r.getGpPerXp());
			case 7:
				return Comparator.comparingDouble(CraftingResult::getXpPerHour);
			default:
				return Comparator.comparingInt(r -> r.getRecipe().getRequiredLevel());
		}
	}

	private static JTextField styleNumericField(JTextField f)
	{
		CraftWiseTheme.styleTextField(f);
		return f;
	}

	private static int parsePositiveInt(String s, int fallback)
	{
		try
		{
			int v = Integer.parseInt(s.trim());
			return Math.max(1, Math.min(99, v));
		}
		catch (Exception ex)
		{
			return fallback;
		}
	}

	private static double parseDoubleOrZero(String s)
	{
		if (s == null || s.trim().isEmpty())
		{
			return 0.0;
		}
		Long gpLike = CurrencyFormatter.parseGpAmount(s);
		if (gpLike != null)
		{
			return gpLike;
		}
		try
		{
			return Math.max(0.0, Double.parseDouble(s.trim()));
		}
		catch (Exception ex)
		{
			return 0.0;
		}
	}

	private static class RecipeTableModel extends AbstractTableModel
	{
		private List<CraftingResult> rows = Collections.emptyList();
		private boolean wide = false;

		public void setWideMode(boolean wide)
		{
			if (this.wide != wide)
			{
				this.wide = wide;
				fireTableStructureChanged();
			}
		}

		public void setRows(List<CraftingResult> rows)
		{
			this.rows = rows != null ? new ArrayList<>(rows) : Collections.emptyList();
			fireTableDataChanged();
		}

		public List<CraftingResult> getRows()
		{
			return rows;
		}

		public CraftingResult getRow(int idx)
		{
			return idx >= 0 && idx < rows.size() ? rows.get(idx) : null;
		}

		@Override public int getRowCount() { return rows.size(); }
		@Override public int getColumnCount() { return wide ? WIDE_COLUMNS.length : COMPACT_COLUMNS.length; }
		@Override public String getColumnName(int col) { return wide ? WIDE_COLUMNS[col] : COMPACT_COLUMNS[col]; }

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			return rows.get(rowIndex);
		}
	}

	private class RecipeCellRenderer extends DefaultTableCellRenderer
	{
		@Override
		public Component getTableCellRendererComponent(
			JTable tbl, Object value, boolean isSelected, boolean hasFocus, int row, int viewCol)
		{
			JLabel cell = (JLabel) super.getTableCellRendererComponent(tbl, "", isSelected, hasFocus, row, viewCol);
			cell.setFont(CraftWiseTheme.getSmallFont());
			cell.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
			cell.setIcon(null);

			if (!(value instanceof CraftingResult))
			{
				return cell;
			}
			CraftingResult r = (CraftingResult) value;
			boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();
			int playerLvl = dataManager.getPlayerStats() != null ? dataManager.getPlayerStats().getRealLevel() : 1;
			boolean unlocked = r.getRecipe().getRequiredLevel() <= playerLvl;

			if (!isSelected)
			{
				cell.setBackground(row % 2 == 0 ? CraftWiseTheme.BG_CARD : CraftWiseTheme.BG_DEEP_CHARCOAL);
			}

			int logicalCol = toLogicalCol(viewCol);
			Color fg = unlocked ? CraftWiseTheme.TEXT_PRIMARY : CraftWiseTheme.TEXT_LOCKED;
			cell.setHorizontalAlignment(logicalCol == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
			cell.setToolTipText(buildTooltip(r, compact));

			switch (logicalCol)
			{
				case 0:
					cell.setText(r.getRecipe().getName());
					dataManager.loadItemIcon(r.getRecipe().getPrimaryItemId(), img -> {
						cell.setIcon(new ImageIcon(img.getScaledInstance(17, 17, java.awt.Image.SCALE_SMOOTH)));
						table.repaint();
					});
					break;
				case 1:
					cell.setText(String.valueOf(r.getRecipe().getRequiredLevel()));
					fg = unlocked ? CraftWiseTheme.TEXT_SECONDARY : CraftWiseTheme.COLOR_LOSS_RED;
					break;
				case 2:
					cell.setText(NumberFormatter.formatDecimal(r.getXpPerAction(), 1));
					break;
				case 3:
					cell.setText(r.isPriceDataComplete()
						? NumberFormatter.formatCompact(r.getMaterialCostPerCraft(), compact)
						: "N/A");
					break;
				case 4:
					cell.setText(r.isPriceDataComplete()
						? NumberFormatter.formatCompact(r.getNetRevenuePerCraft(), compact)
						: "N/A");
					break;
				case 5:
					if (!r.isPriceDataComplete())
					{
						cell.setText("N/A");
						fg = CraftWiseTheme.COLOR_MISSING_GREY;
					}
					else
					{
						cell.setText((r.getProfitPerCraft() > 0 ? "+" : "")
							+ NumberFormatter.formatCompact(r.getProfitPerCraft(), compact));
						fg = r.getProfitPerCraft() >= 0
							? CraftWiseTheme.COLOR_PROFIT_GREEN
							: CraftWiseTheme.COLOR_LOSS_RED;
					}
					break;
				case 6:
					if (!r.isPriceDataComplete() || Double.isNaN(r.getGpPerXp()))
					{
						cell.setText("N/A");
						fg = CraftWiseTheme.COLOR_MISSING_GREY;
					}
					else
					{
						cell.setText((r.getGpPerXp() > 0.005 ? "+" : "") + NumberFormatter.formatDecimal(r.getGpPerXp(), 2));
						fg = r.getGpPerXp() >= 0
							? CraftWiseTheme.COLOR_PROFIT_GREEN
							: CraftWiseTheme.COLOR_LOSS_RED;
					}
					break;
				case 7:
					cell.setText(NumberFormatter.formatCompact(r.getXpPerHour(), compact));
					if (!r.getRecipe().isXpRateVerified() && r.getXpPerAction() > 0)
					{
						fg = CraftWiseTheme.TEXT_SECONDARY;
					}
					break;
				default:
					break;
			}

			cell.setForeground(fg);
			return cell;
		}

		private String buildTooltip(CraftingResult r, boolean compact)
		{
			String pl = r.isPriceDataComplete()
				? CurrencyFormatter.formatSignedGp(r.getProfitPerCraft(), compact) + " (" + CurrencyFormatter.formatGpPerXp(r.getGpPerXp()) + ")"
				: "Missing price data";
			return r.getRecipe().getName() + " · Lvl " + r.getRecipe().getRequiredLevel()
				+ " · " + NumberFormatter.formatXp(r.getXpPerAction(), false)
				+ " · P/L: " + pl;
		}
	}
}
