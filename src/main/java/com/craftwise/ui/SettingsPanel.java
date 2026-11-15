package com.craftwise.ui;

import com.craftwise.CraftWiseConfig;
import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.CraftWiseConfig.RankingMode;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import net.runelite.api.ItemID;

/**
 * Compact settings panel with unclipped dropdowns, market assumptions,
 * custom XP/hour rate overrides, and custom item price overrides.
 */
public class SettingsPanel extends CraftWiseTheme.ScrollableWidthPanel
{
	private final CraftingDataManager dataManager;

	private final JSpinner refreshSpinner = new JSpinner(new SpinnerNumberModel(60, 30, 3600, 15));
	private final JComboBox<PriceCalculationMode> priceModeCombo = new JComboBox<>(PriceCalculationMode.values());
	private final JComboBox<RankingMode> rankingModeCombo = new JComboBox<>(RankingMode.values());
	private final JTextField defaultBudgetField = new JTextField("5M");
	private final JCheckBox geTaxCheck = new JCheckBox("Apply 2% Grand Exchange tax", true);
	private final JCheckBox compactNumbersCheck = new JCheckBox("Compact GP / XP (K/M/B)", true);
	private final JCheckBox showFutureCheck = new JCheckBox("Show locked methods in lists", false);

	private final JComboBox<RecipeChoice> xpRecipeCombo = new JComboBox<>();
	private final JTextField customActionsHrField = new JTextField();

	private final JComboBox<ItemChoice> itemOverrideCombo = new JComboBox<>();
	private final JTextField customBuyPriceField = new JTextField();
	private final JTextField customSellPriceField = new JTextField();

	private final JLabel feedbackLabel = new JLabel(" ");
	private final JLabel overrideCountLabel = new JLabel();

	private boolean syncing = false;

	public SettingsPanel(CraftingDataManager dataManager)
	{
		this.dataManager = dataManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));

		add(buildGeneralSettingsBand());
		add(Box.createVerticalStrut(4));
		add(buildCustomXpBand());
		add(Box.createVerticalStrut(4));
		add(buildCustomPriceBand());
		add(Box.createVerticalStrut(4));

		feedbackLabel.setFont(CraftWiseTheme.getSmallBoldFont());
		feedbackLabel.setForeground(CraftWiseTheme.COLOR_PROFIT_GREEN);
		feedbackLabel.setAlignmentX(LEFT_ALIGNMENT);
		add(feedbackLabel);
		add(Box.createVerticalStrut(4));

		JLabel authorFooter = new JLabel("CraftWise • Author: Gaige");
		authorFooter.setFont(CraftWiseTheme.getSmallFont());
		authorFooter.setForeground(CraftWiseTheme.TEXT_MUTED);
		authorFooter.setAlignmentX(LEFT_ALIGNMENT);
		add(authorFooter);

		populateOverrideDropdowns();
		refresh();
	}

	private JPanel buildGeneralSettingsBand()
	{
		JPanel band = CraftWiseTheme.createCard();
		band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
		band.setAlignmentX(LEFT_ALIGNMENT);

		JLabel hdr = new JLabel("Market & Display Settings");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		hdr.setAlignmentX(LEFT_ALIGNMENT);
		band.add(hdr);
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleSpinner(refreshSpinner);
		CraftWiseTheme.styleComboBox(priceModeCombo);
		CraftWiseTheme.styleComboBox(rankingModeCombo);
		CraftWiseTheme.styleTextField(defaultBudgetField);

		JPanel topPair = new JPanel(new GridLayout(1, 2, 6, 0));
		topPair.setOpaque(false);
		topPair.setAlignmentX(LEFT_ALIGNMENT);
		topPair.add(CraftWiseTheme.stackedField("Refresh (s)", refreshSpinner));
		topPair.add(CraftWiseTheme.stackedField("Default Budget", defaultBudgetField));
		band.add(topPair);
		band.add(Box.createVerticalStrut(4));

		band.add(CraftWiseTheme.stackedField("Price Assumption", priceModeCombo));
		band.add(Box.createVerticalStrut(3));
		band.add(CraftWiseTheme.stackedField("Default Ranking", rankingModeCombo));
		band.add(Box.createVerticalStrut(4));

		styleCheck(geTaxCheck);
		styleCheck(compactNumbersCheck);
		styleCheck(showFutureCheck);
		band.add(geTaxCheck);
		band.add(compactNumbersCheck);
		band.add(showFutureCheck);
		band.add(Box.createVerticalStrut(5));

		JPanel btnRow = new JPanel(new GridLayout(1, 2, 4, 0));
		btnRow.setOpaque(false);
		btnRow.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.CraftButton applyBtn = new CraftWiseTheme.CraftButton("Apply", true);
		CraftWiseTheme.CraftButton refreshNowBtn = new CraftWiseTheme.CraftButton("Refresh Wiki");
		applyBtn.setPreferredSize(new Dimension(80, 22));
		refreshNowBtn.setPreferredSize(new Dimension(80, 22));
		applyBtn.addActionListener(e -> applyGeneralSettings());
		refreshNowBtn.addActionListener(e -> {
			dataManager.refreshPricesNowAsync();
			feedbackLabel.setText("Refreshing OSRS Wiki prices...");
		});
		btnRow.add(applyBtn);
		btnRow.add(refreshNowBtn);
		band.add(btnRow);
		return band;
	}

	private JPanel buildCustomXpBand()
	{
		JPanel band = CraftWiseTheme.createCard();
		band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
		band.setAlignmentX(LEFT_ALIGNMENT);

		JLabel hdr = new JLabel("Custom Actions / Hour");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		hdr.setAlignmentX(LEFT_ALIGNMENT);
		band.add(hdr);
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleComboBox(xpRecipeCombo);
		CraftWiseTheme.styleTextField(customActionsHrField);
		band.add(xpRecipeCombo);
		band.add(Box.createVerticalStrut(3));
		band.add(inlineRow("Actions/hr:", customActionsHrField));
		band.add(Box.createVerticalStrut(4));

		JPanel btns = new JPanel(new GridLayout(1, 2, 4, 0));
		btns.setOpaque(false);
		btns.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.CraftButton saveXpBtn = new CraftWiseTheme.CraftButton("Save Rate", true);
		CraftWiseTheme.CraftButton resetXpBtn = new CraftWiseTheme.CraftButton("Reset");
		saveXpBtn.setPreferredSize(new Dimension(80, 21));
		resetXpBtn.setPreferredSize(new Dimension(80, 21));
		saveXpBtn.addActionListener(e -> {
			Object sel = xpRecipeCombo.getSelectedItem();
			if (sel instanceof RecipeChoice)
			{
				try
				{
					double rate = Double.parseDouble(customActionsHrField.getText().trim());
					if (rate > 0)
					{
						dataManager.setCustomActionsPerHour(((RecipeChoice) sel).recipe.getId(), rate);
						feedbackLabel.setText("Saved rate: " + ((RecipeChoice) sel).recipe.getName());
					}
				}
				catch (Exception ex)
				{
					feedbackLabel.setText("Enter a positive actions/hr number.");
				}
			}
		});
		resetXpBtn.addActionListener(e -> {
			Object sel = xpRecipeCombo.getSelectedItem();
			if (sel instanceof RecipeChoice)
			{
				dataManager.setCustomActionsPerHour(((RecipeChoice) sel).recipe.getId(), null);
				customActionsHrField.setText(String.valueOf(Math.round(((RecipeChoice) sel).recipe.getEstimatedActionsPerHour())));
				feedbackLabel.setText("Restored rate: " + ((RecipeChoice) sel).recipe.getName());
			}
		});
		btns.add(saveXpBtn);
		btns.add(resetXpBtn);
		band.add(btns);

		xpRecipeCombo.addActionListener(e -> {
			Object sel = xpRecipeCombo.getSelectedItem();
			if (sel instanceof RecipeChoice)
			{
				CraftingRecipe rec = ((RecipeChoice) sel).recipe;
				Double ov = dataManager.getCustomActionsPerHour().get(rec.getId());
				customActionsHrField.setText(String.valueOf(Math.round(ov != null ? ov : rec.getEstimatedActionsPerHour())));
			}
		});
		return band;
	}

	private JPanel buildCustomPriceBand()
	{
		JPanel band = CraftWiseTheme.createCard();
		band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
		band.setAlignmentX(LEFT_ALIGNMENT);

		JLabel hdr = new JLabel("Custom Item Prices");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		hdr.setAlignmentX(LEFT_ALIGNMENT);
		band.add(hdr);
		band.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleComboBox(itemOverrideCombo);
		CraftWiseTheme.styleTextField(customBuyPriceField);
		CraftWiseTheme.styleTextField(customSellPriceField);
		band.add(itemOverrideCombo);
		band.add(Box.createVerticalStrut(4));

		JPanel pair = new JPanel(new GridLayout(1, 2, 6, 0));
		pair.setOpaque(false);
		pair.setAlignmentX(LEFT_ALIGNMENT);
		pair.add(CraftWiseTheme.stackedField("Buy (gp)", customBuyPriceField));
		pair.add(CraftWiseTheme.stackedField("Sell (gp)", customSellPriceField));
		band.add(pair);
		band.add(Box.createVerticalStrut(4));

		JPanel btns = new JPanel(new GridLayout(1, 2, 4, 0));
		btns.setOpaque(false);
		btns.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.CraftButton savePriceBtn = new CraftWiseTheme.CraftButton("Save Price", true);
		CraftWiseTheme.CraftButton clearAllBtn = new CraftWiseTheme.CraftButton("Clear All");
		savePriceBtn.setPreferredSize(new Dimension(80, 21));
		clearAllBtn.setPreferredSize(new Dimension(80, 21));
		savePriceBtn.addActionListener(e -> {
			Object sel = itemOverrideCombo.getSelectedItem();
			if (sel instanceof ItemChoice)
			{
				Long buy = CurrencyFormatter.parseGpAmount(customBuyPriceField.getText());
				Long sell = CurrencyFormatter.parseGpAmount(customSellPriceField.getText());
				dataManager.setCustomItemPrice(((ItemChoice) sel).itemId, buy, sell);
				feedbackLabel.setText("Saved price override: " + ((ItemChoice) sel).name);
			}
		});
		clearAllBtn.addActionListener(e -> {
			dataManager.clearAllCustomOverrides();
			customBuyPriceField.setText("");
			customSellPriceField.setText("");
			feedbackLabel.setText("Cleared all custom overrides.");
		});
		btns.add(savePriceBtn);
		btns.add(clearAllBtn);
		band.add(btns);
		band.add(Box.createVerticalStrut(3));

		overrideCountLabel.setFont(CraftWiseTheme.getSmallFont());
		overrideCountLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		overrideCountLabel.setAlignmentX(LEFT_ALIGNMENT);
		band.add(overrideCountLabel);
		return band;
	}

	private void applyGeneralSettings()
	{
		if (syncing) return;
		int interval = ((Number) refreshSpinner.getValue()).intValue();
		PriceCalculationMode pm = (PriceCalculationMode) priceModeCombo.getSelectedItem();
		RankingMode rm = (RankingMode) rankingModeCombo.getSelectedItem();
		Long budget = CurrencyFormatter.parseGpAmount(defaultBudgetField.getText());

		dataManager.updateConfigSetting("priceRefreshIntervalSeconds", interval);
		if (pm != null) dataManager.updateConfigSetting("priceCalculationMode", pm);
		if (rm != null) dataManager.updateConfigSetting("preferredRankingMode", rm);
		if (budget != null)
		{
			long clamped = Math.min(Integer.MAX_VALUE, Math.max(0L, budget));
			dataManager.updateConfigSetting("defaultBudgetGp", (int) clamped);
			dataManager.setBudgetGp(budget);
		}
		dataManager.updateConfigSetting("includeGeTax", geTaxCheck.isSelected());
		dataManager.updateConfigSetting("compactNumbers", compactNumbersCheck.isSelected());
		dataManager.updateConfigSetting("showFutureUnlocks", showFutureCheck.isSelected());

		dataManager.syncFromConfig();
		dataManager.recalculateAll();
		feedbackLabel.setText("Settings applied.");
	}

	private void populateOverrideDropdowns()
	{
		Map<Integer, String> uniqueItems = new LinkedHashMap<>();
		for (CraftingRecipe rec : CraftingDataRepository.getAllRecipes())
		{
			xpRecipeCombo.addItem(new RecipeChoice(rec));
			for (RecipeInput in : rec.getInputs())
			{
				if (in.getItemId() != ItemID.COINS_995)
				{
					uniqueItems.putIfAbsent(in.getItemId(), in.getItemName());
				}
			}
			for (RecipeOutput out : rec.getOutputs())
			{
				if (out.isTradeable())
				{
					uniqueItems.putIfAbsent(out.getItemId(), out.getItemName());
				}
			}
		}
		for (Map.Entry<Integer, String> e : uniqueItems.entrySet())
		{
			itemOverrideCombo.addItem(new ItemChoice(e.getKey(), e.getValue()));
		}
	}

	public void refresh()
	{
		CraftWiseConfig cfg = dataManager.getConfig();
		syncing = true;
		try
		{
			if (cfg != null)
			{
				refreshSpinner.setValue(cfg.priceRefreshIntervalSeconds());
				priceModeCombo.setSelectedItem(cfg.priceCalculationMode());
				rankingModeCombo.setSelectedItem(cfg.preferredRankingMode());
				defaultBudgetField.setText(NumberFormatter.formatCompact(cfg.defaultBudgetGp(), true));
				geTaxCheck.setSelected(cfg.includeGeTax());
				compactNumbersCheck.setSelected(cfg.compactNumbers());
				showFutureCheck.setSelected(cfg.showFutureUnlocks());
			}
			overrideCountLabel.setText("Overrides: " + dataManager.getCustomPrices().size()
				+ " items, " + dataManager.getCustomActionsPerHour().size() + " XP rates");
		}
		finally
		{
			syncing = false;
		}
	}

	private static JPanel inlineRow(String label, java.awt.Component comp)
	{
		JPanel p = new JPanel(new BorderLayout(6, 0));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel l = new JLabel(label);
		l.setFont(CraftWiseTheme.getSmallFont());
		l.setForeground(CraftWiseTheme.TEXT_MUTED);
		p.add(l, BorderLayout.WEST);
		p.add(comp, BorderLayout.CENTER);
		return p;
	}

	private static void styleCheck(JCheckBox cb)
	{
		cb.setOpaque(false);
		cb.setFont(CraftWiseTheme.getSmallFont());
		cb.setForeground(CraftWiseTheme.TEXT_SECONDARY);
		cb.setAlignmentX(LEFT_ALIGNMENT);
	}

	private static class RecipeChoice
	{
		final CraftingRecipe recipe;
		RecipeChoice(CraftingRecipe recipe) { this.recipe = recipe; }
		@Override public String toString() { return recipe.getName(); }
	}

	private static class ItemChoice
	{
		final int itemId;
		final String name;
		ItemChoice(int itemId, String name) { this.itemId = itemId; this.name = name; }
		@Override public String toString() { return name + " (#" + itemId + ")"; }
	}
}
