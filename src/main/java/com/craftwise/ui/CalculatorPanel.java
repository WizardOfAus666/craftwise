package com.craftwise.ui;

import com.craftwise.CraftWiseConfig.PriceCalculationMode;
import com.craftwise.calculator.BreakEvenCalculator;
import com.craftwise.calculator.BreakEvenCalculator.BreakEvenAnalysis;
import com.craftwise.calculator.BreakEvenCalculator.InputBreakEvenLimit;
import com.craftwise.calculator.BreakEvenCalculator.MarginPresetTarget;
import com.craftwise.calculator.GETransactionCalculator;
import com.craftwise.calculator.InventoryCalculator;
import com.craftwise.calculator.InventoryCalculator.InventorySessionResult;
import com.craftwise.calculator.InventoryCalculator.SessionMaterial;
import com.craftwise.calculator.InventoryCalculator.SessionOutput;
import com.craftwise.calculator.MultiStepCalculator;
import com.craftwise.calculator.MultiStepCalculator.MultiStepAnalysis;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.CraftingResult.BreakEvenStatus;
import com.craftwise.model.ItemPrice;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.api.ItemID;

/**
 * Interactive calculation workspace containing Profit Calc, Break-Even,
 * and Inventory Session calculator with compact zero-clipping layouts.
 */
public class CalculatorPanel extends CraftWiseTheme.ScrollableWidthPanel
{
	public static final String SUB_PROFIT = "PROFIT";
	public static final String SUB_BREAKEVEN = "BREAKEVEN";
	public static final String SUB_INVENTORY = "INVENTORY";
	public static final String SUB_COMPARE = "COMPARE";

	private final CraftingDataManager dataManager;

	private final JComboBox<RecipeChoice> recipeCombo = new JComboBox<>();
	private final CraftWiseTheme.CraftButton btnProfit = new CraftWiseTheme.CraftButton("Profit Calc");
	private final CraftWiseTheme.CraftButton btnBreakEven = new CraftWiseTheme.CraftButton("Break-Even");
	private final CraftWiseTheme.CraftButton btnInventory = new CraftWiseTheme.CraftButton("Inventory");
	private final CraftWiseTheme.CraftButton btnCompare = new CraftWiseTheme.CraftButton("Compare (5)");

	private final CardLayout subLayout = new CardLayout();
	private final JPanel subCards = new JPanel(subLayout);
	private String activeSubTab = SUB_PROFIT;

	// Profit Calc controls
	private final JComboBox<PriceCalculationMode> modeCombo = new JComboBox<>(PriceCalculationMode.values());
	private final JCheckBox taxCheckbox = new JCheckBox("Apply 2% GE Tax", true);
	private final JTextField profitQtyField = new JTextField("1");
	private final JTextField customMatCostField = new JTextField();
	private final JTextField customSellPriceField = new JTextField();
	private final JPanel profitOutputPanel = new JPanel();

	// Break-Even controls
	private final JTextField beMatCostField = new JTextField();
	private final JTextField beSellPriceField = new JTextField();
	private final JLabel beIndicatorLabel = new JLabel("", SwingConstants.CENTER);
	private final JPanel beOutputPanel = new JPanel();

	// Inventory Calc controls
	private final JTextField invCustomQtyField = new JTextField("27");
	private final JPanel invOutputPanel = new JPanel();

	// Side-by-side Method Comparison controls (up to 5 methods)
	private final List<JComboBox<RecipeChoice>> compareCombos = new ArrayList<>();
	private final JPanel compareOutputPanel = new JPanel();

	private boolean syncingRecipe = false;
	private long lastMarketMatCost = 0L;
	private long lastMarketSellPrice = 0L;

	public CalculatorPanel(CraftingDataManager dataManager)
	{
		this.dataManager = dataManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));

		add(buildSelectorHeader());
		add(Box.createVerticalStrut(4));

		subCards.setOpaque(false);
		subCards.setAlignmentX(LEFT_ALIGNMENT);
		subCards.add(buildProfitCard(), SUB_PROFIT);
		subCards.add(buildBreakEvenCard(), SUB_BREAKEVEN);
		subCards.add(buildInventoryCard(), SUB_INVENTORY);
		subCards.add(buildCompareCard(), SUB_COMPARE);
		add(subCards);

		populateRecipes();
		selectSubTab(SUB_PROFIT);
	}

	public String getActiveSubTab()
	{
		return activeSubTab;
	}

	private JPanel buildSelectorHeader()
	{
		JPanel band = CraftWiseTheme.createCard();
		band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
		band.setAlignmentX(LEFT_ALIGNMENT);

		CraftWiseTheme.styleComboBox(recipeCombo);
		recipeCombo.setAlignmentX(LEFT_ALIGNMENT);
		recipeCombo.addActionListener(e -> {
			if (!syncingRecipe)
			{
				onRecipeChanged(true);
			}
		});
		band.add(CraftWiseTheme.stackedField("Recipe", recipeCombo));
		band.add(Box.createVerticalStrut(4));

		JPanel tabsRow = new JPanel(new GridLayout(2, 2, 2, 2));
		tabsRow.setOpaque(false);
		tabsRow.setAlignmentX(LEFT_ALIGNMENT);
		btnProfit.setPreferredSize(new Dimension(56, 21));
		btnBreakEven.setPreferredSize(new Dimension(56, 21));
		btnInventory.setPreferredSize(new Dimension(56, 21));
		btnCompare.setPreferredSize(new Dimension(56, 21));
		btnProfit.addActionListener(e -> selectSubTab(SUB_PROFIT));
		btnBreakEven.addActionListener(e -> selectSubTab(SUB_BREAKEVEN));
		btnInventory.addActionListener(e -> selectSubTab(SUB_INVENTORY));
		btnCompare.addActionListener(e -> selectSubTab(SUB_COMPARE));
		tabsRow.add(btnProfit);
		tabsRow.add(btnBreakEven);
		tabsRow.add(btnInventory);
		tabsRow.add(btnCompare);
		band.add(tabsRow);
		return band;
	}

	private JPanel buildProfitCard()
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setOpaque(false);

		JPanel inputBand = CraftWiseTheme.createCard();
		inputBand.setLayout(new BoxLayout(inputBand, BoxLayout.Y_AXIS));
		inputBand.setAlignmentX(LEFT_ALIGNMENT);

		CraftWiseTheme.styleComboBox(modeCombo);
		if (dataManager.getConfig() != null)
		{
			modeCombo.setSelectedItem(dataManager.getConfig().priceCalculationMode());
			taxCheckbox.setSelected(dataManager.getConfig().includeGeTax());
		}
		modeCombo.addActionListener(e -> {
			if (!syncingRecipe)
			{
				onRecipeChanged(true);
			}
		});

		taxCheckbox.setOpaque(false);
		taxCheckbox.setForeground(CraftWiseTheme.TEXT_SECONDARY);
		taxCheckbox.setFont(CraftWiseTheme.getSmallFont());
		taxCheckbox.setAlignmentX(LEFT_ALIGNMENT);
		taxCheckbox.addActionListener(e -> recalculateProfitView());

		inputBand.add(CraftWiseTheme.stackedField("Price Assumption", modeCombo));
		inputBand.add(Box.createVerticalStrut(2));
		inputBand.add(taxCheckbox);
		inputBand.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleTextField(profitQtyField);
		CraftWiseTheme.styleTextField(customMatCostField);
		CraftWiseTheme.styleTextField(customSellPriceField);

		JPanel qtyPresets = new JPanel(new GridLayout(1, 4, 2, 0));
		qtyPresets.setOpaque(false);
		qtyPresets.setAlignmentX(LEFT_ALIGNMENT);
		for (int q : new int[]{1, 10, 100, 1000})
		{
			CraftWiseTheme.CraftButton b = new CraftWiseTheme.CraftButton(NumberFormatter.formatInt(q));
			b.setPreferredSize(new Dimension(42, 20));
			b.addActionListener(e -> profitQtyField.setText(String.valueOf(q)));
			qtyPresets.add(b);
		}

		inputBand.add(compactRow("Quantity:", profitQtyField));
		inputBand.add(Box.createVerticalStrut(3));
		inputBand.add(qtyPresets);
		inputBand.add(Box.createVerticalStrut(4));

		JPanel overridePair = new JPanel(new GridLayout(1, 2, 6, 0));
		overridePair.setOpaque(false);
		overridePair.setAlignmentX(LEFT_ALIGNMENT);
		overridePair.add(CraftWiseTheme.stackedField("Mat Cost/Craft", customMatCostField));
		overridePair.add(CraftWiseTheme.stackedField("Sell Price/Out", customSellPriceField));
		inputBand.add(overridePair);

		DocumentListener dl = simpleDocListener(this::recalculateProfitView);
		profitQtyField.getDocument().addDocumentListener(dl);
		customMatCostField.getDocument().addDocumentListener(dl);
		customSellPriceField.getDocument().addDocumentListener(dl);

		container.add(inputBand);
		container.add(Box.createVerticalStrut(4));

		profitOutputPanel.setLayout(new BoxLayout(profitOutputPanel, BoxLayout.Y_AXIS));
		profitOutputPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		profitOutputPanel.setBorder(CraftWiseTheme.CARD_BORDER);
		profitOutputPanel.setAlignmentX(LEFT_ALIGNMENT);
		container.add(profitOutputPanel);
		return container;
	}

	private JPanel buildBreakEvenCard()
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setOpaque(false);

		JPanel inputBand = CraftWiseTheme.createCard();
		inputBand.setLayout(new BoxLayout(inputBand, BoxLayout.Y_AXIS));
		inputBand.setAlignmentX(LEFT_ALIGNMENT);

		CraftWiseTheme.styleTextField(beMatCostField);
		CraftWiseTheme.styleTextField(beSellPriceField);
		JPanel costPair = new JPanel(new GridLayout(1, 2, 6, 0));
		costPair.setOpaque(false);
		costPair.setAlignmentX(LEFT_ALIGNMENT);
		costPair.add(CraftWiseTheme.stackedField("Material Cost (gp)", beMatCostField));
		costPair.add(CraftWiseTheme.stackedField("Sell Price (gp)", beSellPriceField));
		inputBand.add(costPair);
		inputBand.add(Box.createVerticalStrut(4));

		JPanel adjRow = new JPanel(new GridLayout(1, 5, 2, 0));
		adjRow.setOpaque(false);
		adjRow.setAlignmentX(LEFT_ALIGNMENT);
		adjRow.add(adjustSellBtn("-10%", 0.90));
		adjRow.add(adjustSellBtn("-5%", 0.95));
		CraftWiseTheme.CraftButton mktBtn = new CraftWiseTheme.CraftButton("Mkt");
		mktBtn.setPreferredSize(new Dimension(36, 21));
		mktBtn.addActionListener(e -> {
			beMatCostField.setText(String.valueOf(lastMarketMatCost));
			beSellPriceField.setText(String.valueOf(lastMarketSellPrice));
			recalculateBreakEvenView();
		});
		adjRow.add(mktBtn);
		adjRow.add(adjustSellBtn("+5%", 1.05));
		adjRow.add(adjustSellBtn("+10%", 1.10));
		inputBand.add(adjRow);

		DocumentListener dl = simpleDocListener(this::recalculateBreakEvenView);
		beMatCostField.getDocument().addDocumentListener(dl);
		beSellPriceField.getDocument().addDocumentListener(dl);

		container.add(inputBand);
		container.add(Box.createVerticalStrut(4));

		beIndicatorLabel.setOpaque(true);
		beIndicatorLabel.setFont(CraftWiseTheme.getSmallBoldFont());
		beIndicatorLabel.setPreferredSize(new Dimension(100, 24));
		beIndicatorLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		beIndicatorLabel.setBorder(BorderFactory.createLineBorder(CraftWiseTheme.BORDER_DEFAULT, 1));
		beIndicatorLabel.setAlignmentX(LEFT_ALIGNMENT);
		container.add(beIndicatorLabel);
		container.add(Box.createVerticalStrut(4));

		beOutputPanel.setLayout(new BoxLayout(beOutputPanel, BoxLayout.Y_AXIS));
		beOutputPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		beOutputPanel.setBorder(CraftWiseTheme.CARD_BORDER);
		beOutputPanel.setAlignmentX(LEFT_ALIGNMENT);
		container.add(beOutputPanel);
		return container;
	}

	private JPanel buildInventoryCard()
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setOpaque(false);

		JPanel inputBand = CraftWiseTheme.createCard();
		inputBand.setLayout(new BoxLayout(inputBand, BoxLayout.Y_AXIS));
		inputBand.setAlignmentX(LEFT_ALIGNMENT);

		JPanel presetGrid = new JPanel(new GridLayout(2, 3, 2, 2));
		presetGrid.setOpaque(false);
		presetGrid.setAlignmentX(LEFT_ALIGNMENT);
		presetGrid.add(invPresetBtn("1 Inv", 1, true));
		presetGrid.add(invPresetBtn("5 Inv", 5, true));
		presetGrid.add(invPresetBtn("10 Inv", 10, true));
		presetGrid.add(invPresetBtn("100", 100, false));
		presetGrid.add(invPresetBtn("500", 500, false));
		presetGrid.add(invPresetBtn("1,000", 1000, false));
		inputBand.add(presetGrid);
		inputBand.add(Box.createVerticalStrut(4));

		CraftWiseTheme.styleTextField(invCustomQtyField);
		invCustomQtyField.getDocument().addDocumentListener(simpleDocListener(this::recalculateInventoryView));
		inputBand.add(compactRow("Crafts:", invCustomQtyField));

		container.add(inputBand);
		container.add(Box.createVerticalStrut(4));

		invOutputPanel.setLayout(new BoxLayout(invOutputPanel, BoxLayout.Y_AXIS));
		invOutputPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		invOutputPanel.setBorder(CraftWiseTheme.CARD_BORDER);
		invOutputPanel.setAlignmentX(LEFT_ALIGNMENT);
		container.add(invOutputPanel);
		return container;
	}

	private CraftWiseTheme.CraftButton adjustSellBtn(String label, double factor)
	{
		CraftWiseTheme.CraftButton b = new CraftWiseTheme.CraftButton(label);
		b.setPreferredSize(new Dimension(36, 21));
		b.addActionListener(e -> {
			Long cur = CurrencyFormatter.parseGpAmount(beSellPriceField.getText());
			long base = cur != null && cur > 0L ? cur : lastMarketSellPrice;
			long next = Math.max(1L, Math.round(base * factor));
			beSellPriceField.setText(String.valueOf(next));
			recalculateBreakEvenView();
		});
		return b;
	}

	private CraftWiseTheme.CraftButton invPresetBtn(String label, int value, boolean isInventories)
	{
		CraftWiseTheme.CraftButton b = new CraftWiseTheme.CraftButton(label);
		b.setPreferredSize(new Dimension(56, 21));
		b.addActionListener(e -> {
			CraftingRecipe rec = getSelectedRecipe();
			if (rec != null)
			{
				int qty = isInventories
					? InventoryCalculator.calculateCraftsPerInventory(rec) * value
					: value;
				invCustomQtyField.setText(String.valueOf(Math.max(1, qty)));
				recalculateInventoryView();
			}
		});
		return b;
	}

	public void selectSubTab(String subTab)
	{
		this.activeSubTab = subTab;
		subLayout.show(subCards, subTab);
		btnProfit.setActive(SUB_PROFIT.equals(subTab));
		btnBreakEven.setActive(SUB_BREAKEVEN.equals(subTab));
		btnInventory.setActive(SUB_INVENTORY.equals(subTab));
		btnCompare.setActive(SUB_COMPARE.equals(subTab));
		if (SUB_COMPARE.equals(subTab))
		{
			recalculateCompareView();
		}
	}

	public void openRecipe(CraftingRecipe recipe, String subTab)
	{
		selectSubTab(subTab);
		if (recipe != null)
		{
			syncingRecipe = true;
			try
			{
				for (int i = 0; i < recipeCombo.getItemCount(); i++)
				{
					RecipeChoice item = recipeCombo.getItemAt(i);
					if (item.recipe.getId().equals(recipe.getId()))
					{
						recipeCombo.setSelectedIndex(i);
						break;
					}
				}
			}
			finally
			{
				syncingRecipe = false;
			}
			onRecipeChanged(true);
		}
	}

	public void refresh()
	{
		onRecipeChanged(false);
		recalculateCompareView();
	}

	private void populateRecipes()
	{
		syncingRecipe = true;
		try
		{
			recipeCombo.removeAllItems();
			for (CraftingRecipe rec : CraftingDataRepository.getAllRecipes())
			{
				recipeCombo.addItem(new RecipeChoice(rec));
			}
		}
		finally
		{
			syncingRecipe = false;
		}
		onRecipeChanged(true);
	}

	private CraftingRecipe getSelectedRecipe()
	{
		Object sel = recipeCombo.getSelectedItem();
		return sel instanceof RecipeChoice ? ((RecipeChoice) sel).recipe : null;
	}

	private void onRecipeChanged(boolean resetFieldsToMarket)
	{
		CraftingRecipe rec = getSelectedRecipe();
		if (rec == null)
		{
			return;
		}
		PriceCalculationMode mode = (PriceCalculationMode) modeCombo.getSelectedItem();
		boolean includeTax = taxCheckbox.isSelected();
		CraftingResult eval = ProfitCalculator.evaluate(
			rec,
			dataManager.getCurrentPrices(),
			dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
			mode,
			includeTax,
			dataManager.getCustomPrices(),
			dataManager.getCustomActionsPerHour(),
			dataManager.getPlayerStats()
		);

		lastMarketMatCost = eval.isPriceDataComplete() ? Math.round(eval.getMaterialCostPerCraft()) : 0L;
		lastMarketSellPrice = eval.isPriceDataComplete() && eval.getPrimaryOutputMarketPrice() != null
			? eval.getPrimaryOutputMarketPrice()
			: 0L;

		if (resetFieldsToMarket)
		{
			syncingRecipe = true;
			try
			{
				customMatCostField.setText(eval.isPriceDataComplete() ? String.valueOf(lastMarketMatCost) : "");
				customSellPriceField.setText(eval.isPriceDataComplete() ? String.valueOf(lastMarketSellPrice) : "");
				beMatCostField.setText(eval.isPriceDataComplete() ? String.valueOf(lastMarketMatCost) : "");
				beSellPriceField.setText(eval.isPriceDataComplete() ? String.valueOf(lastMarketSellPrice) : "");
				invCustomQtyField.setText(String.valueOf(InventoryCalculator.calculateCraftsPerInventory(rec)));
			}
			finally
			{
				syncingRecipe = false;
			}
		}

		recalculateProfitView();
		recalculateBreakEvenView();
		recalculateInventoryView();
	}

	private void recalculateProfitView()
	{
		if (syncingRecipe)
		{
			return;
		}
		profitOutputPanel.removeAll();
		CraftingRecipe rec = getSelectedRecipe();
		if (rec == null)
		{
			return;
		}
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();
		boolean includeTax = taxCheckbox.isSelected();
		PriceCalculationMode mode = (PriceCalculationMode) modeCombo.getSelectedItem();

		Long qtyParsed = CurrencyFormatter.parseGpAmount(profitQtyField.getText());
		long qty = qtyParsed != null && qtyParsed > 0L ? qtyParsed : 1L;

		Long matOverride = CurrencyFormatter.parseGpAmount(customMatCostField.getText());
		Long sellOverride = CurrencyFormatter.parseGpAmount(customSellPriceField.getText());

		CraftingResult marketEval = ProfitCalculator.evaluate(
			rec,
			dataManager.getCurrentPrices(),
			dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
			mode,
			includeTax,
			dataManager.getCustomPrices(),
			dataManager.getCustomActionsPerHour(),
			dataManager.getPlayerStats()
		);

		double matCost = matOverride != null ? matOverride : (marketEval.isPriceDataComplete() ? marketEval.getMaterialCostPerCraft() : -1);
		double grossRev;
		double taxPerCraft;

		if (sellOverride != null && rec.getPrimaryOutput() != null)
		{
			RecipeOutput primary = rec.getPrimaryOutput();
			double primQty = primary.getExpectedQuantity();
			grossRev = primQty * sellOverride;
			taxPerCraft = GETransactionCalculator.calculateExpectedTax(
				sellOverride, primQty, primary.getItemId(), primary.isTaxExempt(), includeTax);
		}
		else if (marketEval.isPriceDataComplete())
		{
			grossRev = marketEval.getGrossRevenuePerCraft();
			taxPerCraft = marketEval.getGeTaxPerCraft();
		}
		else
		{
			grossRev = -1;
			taxPerCraft = 0;
		}

		if (matCost < 0 || grossRev < 0)
		{
			JLabel miss = new JLabel("<html>Price unavailable. Enter overrides above or sync market prices.</html>");
			miss.setFont(CraftWiseTheme.getSmallFont());
			miss.setForeground(CraftWiseTheme.COLOR_WARNING_YELLOW);
			profitOutputPanel.add(miss);
		}
		else
		{
			double netRev = grossRev - taxPerCraft;
			double profitPer = netRev - matCost;
			double totalProfit = profitPer * qty;
			double totalXp = rec.getXpPerAction() * qty;
			double gpXp = rec.getXpPerAction() > 0 ? profitPer / rec.getXpPerAction() : Double.NaN;
			Color plColor = profitPer >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;

			String labelTag = (matOverride != null && matOverride != lastMarketMatCost) || (sellOverride != null && sellOverride != lastMarketSellPrice)
				? "Custom Override"
				: (mode != null ? mode.getLabel() : "Market");

			JLabel hdr = new JLabel("Results (" + labelTag + ")");
			hdr.setFont(CraftWiseTheme.getSmallBoldFont());
			hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
			profitOutputPanel.add(hdr);
			profitOutputPanel.add(Box.createVerticalStrut(3));

			profitOutputPanel.add(CraftWiseTheme.kvRow("Cost (" + qty + "x)", CurrencyFormatter.formatGp(Math.round(matCost * qty), compact), CraftWiseTheme.TEXT_PRIMARY));
			profitOutputPanel.add(CraftWiseTheme.kvRow("Gross Rev", CurrencyFormatter.formatGp(Math.round(grossRev * qty), compact), CraftWiseTheme.TEXT_PRIMARY));
			profitOutputPanel.add(CraftWiseTheme.kvRow("GE Tax", CurrencyFormatter.formatGp(Math.round(taxPerCraft * qty), compact), CraftWiseTheme.COLOR_WARNING_YELLOW));
			profitOutputPanel.add(CraftWiseTheme.kvRow("Net Rev", CurrencyFormatter.formatGp(Math.round(netRev * qty), compact), CraftWiseTheme.TEXT_PRIMARY));
			profitOutputPanel.add(CraftWiseTheme.kvRow("Net P/L", CurrencyFormatter.formatSignedGp(totalProfit, compact), plColor));
			profitOutputPanel.add(CraftWiseTheme.kvRow("Total XP", NumberFormatter.formatXp(totalXp, compact), CraftWiseTheme.TEXT_SECONDARY));
			profitOutputPanel.add(CraftWiseTheme.kvRow("GP / XP", CurrencyFormatter.formatGpPerXp(gpXp), plColor));
			profitOutputPanel.add(Box.createVerticalStrut(2));
			profitOutputPanel.add(CraftWiseTheme.kvRow("10x P/L", CurrencyFormatter.formatSignedGp(profitPer * 10.0, compact), plColor));
			profitOutputPanel.add(CraftWiseTheme.kvRow("100x P/L", CurrencyFormatter.formatSignedGp(profitPer * 100.0, compact), plColor));
			profitOutputPanel.add(CraftWiseTheme.kvRow("1,000x P/L", CurrencyFormatter.formatSignedGp(profitPer * 1000.0, compact), plColor));
			profitOutputPanel.add(CraftWiseTheme.kvRow("Hourly P/L", CurrencyFormatter.formatSignedGp(profitPer * marketEval.getActionsPerHour(), compact) + "/h", plColor));
			profitOutputPanel.add(CraftWiseTheme.kvRow("Confidence",
				marketEval.getPriceConfidence().getLabel()
					+ " (" + NumberFormatter.formatInt(marketEval.getMin1hVolume()) + "/h)",
				RecipeDetailPanel.colorForConfidence(marketEval.getPriceConfidence())));

			MultiStepAnalysis chain = MultiStepCalculator.analyze(
				rec,
				CraftingDataRepository.getAllRecipes(),
				dataManager.getCurrentPrices(),
				dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
				mode,
				includeTax,
				dataManager.getCustomPrices()
			);
			if (chain.isHasMultiStepChain() && chain.isMakeAllStepsAvailable())
			{
				profitOutputPanel.add(Box.createVerticalStrut(3));
				JLabel chHdr = new JLabel("Multi-Step: " + chain.getRecommendation());
				chHdr.setFont(CraftWiseTheme.getSmallBoldFont());
				chHdr.setForeground(CraftWiseTheme.ACCENT_GOLD_SOFT);
				profitOutputPanel.add(chHdr);
				profitOutputPanel.add(CraftWiseTheme.kvRow("Buy Interm.", CurrencyFormatter.formatSignedGp(chain.getBuyIntermediateProfit(), compact), CraftWiseTheme.TEXT_SECONDARY));
				profitOutputPanel.add(CraftWiseTheme.kvRow("Make All", CurrencyFormatter.formatSignedGp(chain.getMakeAllProfit(), compact)
					+ " (+" + NumberFormatter.formatXp(chain.getXpGainMakeVsBuy(), compact) + ")", CraftWiseTheme.TEXT_PRIMARY));
			}

			JLabel notice = new JLabel("<html>Execution at GE prices not guaranteed.</html>");
			notice.setFont(CraftWiseTheme.getSmallFont());
			notice.setForeground(CraftWiseTheme.TEXT_MUTED);
			profitOutputPanel.add(Box.createVerticalStrut(2));
			profitOutputPanel.add(notice);
		}

		profitOutputPanel.revalidate();
		profitOutputPanel.repaint();
	}

	private void recalculateBreakEvenView()
	{
		if (syncingRecipe)
		{
			return;
		}
		beOutputPanel.removeAll();
		CraftingRecipe rec = getSelectedRecipe();
		if (rec == null)
		{
			return;
		}

		boolean includeTax = taxCheckbox.isSelected();
		Long matCostVal = CurrencyFormatter.parseGpAmount(beMatCostField.getText());
		Long sellVal = CurrencyFormatter.parseGpAmount(beSellPriceField.getText());

		if (matCostVal == null || sellVal == null || !rec.isTradeableOutput())
		{
			beIndicatorLabel.setText(rec.isTradeableOutput() ? "Enter prices above" : "Untradeable output");
			beIndicatorLabel.setBackground(CraftWiseTheme.BG_CARD_ELEVATED);
			beIndicatorLabel.setForeground(CraftWiseTheme.COLOR_MISSING_GREY);
			beOutputPanel.revalidate();
			beOutputPanel.repaint();
			return;
		}

		List<Long> inputUnitPrices = new ArrayList<>();
		PriceCalculationMode mode = (PriceCalculationMode) modeCombo.getSelectedItem();
		for (RecipeInput in : rec.getInputs())
		{
			if (in.getItemId() == ItemID.COINS_995)
			{
				inputUnitPrices.add(1L);
				continue;
			}
			ItemPrice ip = ProfitCalculator.resolveItemPrice(in.getItemId(), in.getItemName(),
				dataManager.getCurrentPrices(),
				dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null);
			Long p = ip != null ? ip.resolveBuyPrice(mode, null) : null;
			inputUnitPrices.add(p != null ? p : 0L);
		}

		BreakEvenAnalysis analysis = BreakEvenCalculator.analyze(
			rec,
			matCostVal,
			sellVal,
			0.0,
			inputUnitPrices,
			includeTax
		);

		if (!analysis.isAvailable())
		{
			beIndicatorLabel.setText("Unavailable");
			beIndicatorLabel.setBackground(CraftWiseTheme.BG_CARD_ELEVATED);
			beIndicatorLabel.setForeground(CraftWiseTheme.COLOR_MISSING_GREY);
		}
		else
		{
			BreakEvenStatus status = analysis.getStatus();
			Color statusColor = RecipeDetailPanel.colorForStatus(status);
			beIndicatorLabel.setText(status.getLabel()
				+ " (" + CurrencyFormatter.formatSignedGp(analysis.getCurrentNetProfit(), false) + ")");
			Color bgBase = CraftWiseTheme.BG_CARD;
			int r = (int) Math.round(bgBase.getRed() * 0.84 + statusColor.getRed() * 0.16);
			int g = (int) Math.round(bgBase.getGreen() * 0.84 + statusColor.getGreen() * 0.16);
			int b = (int) Math.round(bgBase.getBlue() * 0.84 + statusColor.getBlue() * 0.16);
			beIndicatorLabel.setBackground(new Color(r, g, b));
			beIndicatorLabel.setForeground(statusColor);

			beOutputPanel.add(CraftWiseTheme.kvRow("Break-Even Sell", CurrencyFormatter.formatGp(analysis.getBreakEvenPrimarySellPrice(), false), CraftWiseTheme.ACCENT_GOLD));
			beOutputPanel.add(CraftWiseTheme.kvRow("Tax @ Break-Even", CurrencyFormatter.formatGp(analysis.getBreakEvenTaxPerPrimaryUnit(), false), CraftWiseTheme.TEXT_MUTED));
			beOutputPanel.add(CraftWiseTheme.kvRow("Tax @ Your Price", CurrencyFormatter.formatGp(analysis.getCurrentTaxPerPrimaryUnit(), false), CraftWiseTheme.COLOR_WARNING_YELLOW));
			beOutputPanel.add(CraftWiseTheme.kvRow("Max Total Cost", CurrencyFormatter.formatGp(analysis.getMaxTotalMaterialCost(), false), CraftWiseTheme.TEXT_PRIMARY));
			beOutputPanel.add(CraftWiseTheme.kvRow("Max Sell Drop", NumberFormatter.formatPercent(analysis.getMaxAcceptableSellDropPercent()), statusColor));
			beOutputPanel.add(CraftWiseTheme.kvRow("Max Cost Rise", NumberFormatter.formatPercent(analysis.getMaxAcceptableCostRisePercent()), statusColor));
			beOutputPanel.add(CraftWiseTheme.kvRow("Net Profit/Loss", CurrencyFormatter.formatSignedGp(analysis.getCurrentNetProfit(), false), statusColor));

			if (!analysis.getMarginTargets().isEmpty())
			{
				beOutputPanel.add(Box.createVerticalStrut(4));
				JLabel mrgHdr = new JLabel("Target Margin Sell Prices");
				mrgHdr.setFont(CraftWiseTheme.getSmallBoldFont());
				mrgHdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
				beOutputPanel.add(mrgHdr);
				for (MarginPresetTarget mt : analysis.getMarginTargets())
				{
					beOutputPanel.add(CraftWiseTheme.kvRow(mt.getLabel(),
						CurrencyFormatter.formatGp(mt.getTargetSellPrice(), false)
							+ " (" + CurrencyFormatter.formatSignedGp(mt.getNetProfitAtTarget(), false) + ")",
						CraftWiseTheme.TEXT_PRIMARY));
				}
			}

			if (!analysis.getInputLimits().isEmpty())
			{
				beOutputPanel.add(Box.createVerticalStrut(4));
				JLabel limHdr = new JLabel("Max Input Buy Prices");
				limHdr.setFont(CraftWiseTheme.getSmallBoldFont());
				limHdr.setForeground(CraftWiseTheme.ACCENT_GOLD_SOFT);
				beOutputPanel.add(limHdr);
				for (InputBreakEvenLimit lim : analysis.getInputLimits())
				{
					beOutputPanel.add(CraftWiseTheme.kvRow(lim.getItemName(),
						CurrencyFormatter.formatGp(lim.getMaxAffordableUnitPrice(), false),
						CraftWiseTheme.TEXT_SECONDARY));
				}
			}
		}

		beOutputPanel.revalidate();
		beOutputPanel.repaint();
	}

	private void recalculateInventoryView()
	{
		if (syncingRecipe)
		{
			return;
		}
		invOutputPanel.removeAll();
		CraftingRecipe rec = getSelectedRecipe();
		if (rec == null)
		{
			return;
		}
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();
		Long parsedQty = CurrencyFormatter.parseGpAmount(invCustomQtyField.getText());
		long actions = parsedQty != null && parsedQty > 0L ? parsedQty : 1L;

		PriceCalculationMode mode = (PriceCalculationMode) modeCombo.getSelectedItem();
		CraftingResult eval = ProfitCalculator.evaluate(
			rec,
			dataManager.getCurrentPrices(),
			dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
			mode,
			taxCheckbox.isSelected(),
			dataManager.getCustomPrices(),
			dataManager.getCustomActionsPerHour(),
			dataManager.getPlayerStats()
		);

		InventorySessionResult session = InventoryCalculator.calculateForActions(eval, actions);
		invOutputPanel.add(CraftWiseTheme.kvRow("Crafts / Inv", session.getCraftsPerInventory() + " (28 slots)", CraftWiseTheme.ACCENT_GOLD));
		invOutputPanel.add(CraftWiseTheme.kvRow("Inventories", NumberFormatter.formatInt(session.getInventoriesNeeded()), CraftWiseTheme.TEXT_PRIMARY));
		invOutputPanel.add(CraftWiseTheme.kvRow("Total XP", NumberFormatter.formatXp(session.getTotalXpGained(), compact), CraftWiseTheme.TEXT_PRIMARY));
		invOutputPanel.add(CraftWiseTheme.kvRow("Est. Time", NumberFormatter.formatDecimal(session.getEstimatedMinutes(), 1) + " mins", CraftWiseTheme.TEXT_SECONDARY));

		if (session.isAvailable())
		{
			Color plColor = session.getTotalNetProfit() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
			invOutputPanel.add(CraftWiseTheme.kvRow("Mat Cost", CurrencyFormatter.formatGp(Math.round(session.getTotalMaterialCost()), compact), CraftWiseTheme.TEXT_PRIMARY));
			invOutputPanel.add(CraftWiseTheme.kvRow("Gross Rev", CurrencyFormatter.formatGp(Math.round(session.getTotalGrossRevenue()), compact), CraftWiseTheme.TEXT_PRIMARY));
			invOutputPanel.add(CraftWiseTheme.kvRow("GE Tax", CurrencyFormatter.formatGp(Math.round(session.getTotalGeTax()), compact), CraftWiseTheme.COLOR_WARNING_YELLOW));
			invOutputPanel.add(CraftWiseTheme.kvRow("Net P/L", CurrencyFormatter.formatSignedGp(session.getTotalNetProfit(), compact), plColor));
		}
		else
		{
			JLabel warn = new JLabel("Missing prices for GP totals.");
			warn.setFont(CraftWiseTheme.getSmallFont());
			warn.setForeground(CraftWiseTheme.COLOR_WARNING_YELLOW);
			invOutputPanel.add(warn);
		}

		invOutputPanel.add(Box.createVerticalStrut(3));
		JLabel matHdr = new JLabel("Materials Needed");
		matHdr.setFont(CraftWiseTheme.getSmallBoldFont());
		matHdr.setForeground(CraftWiseTheme.ACCENT_GOLD_SOFT);
		invOutputPanel.add(matHdr);
		for (SessionMaterial m : session.getMaterials())
		{
			invOutputPanel.add(CraftWiseTheme.kvRow(m.getItemName(), NumberFormatter.formatInt(m.getPurchaseQuantity()) + "x", CraftWiseTheme.TEXT_SECONDARY));
		}

		invOutputPanel.add(Box.createVerticalStrut(3));
		JLabel outHdr = new JLabel("Expected Outputs");
		outHdr.setFont(CraftWiseTheme.getSmallBoldFont());
		outHdr.setForeground(CraftWiseTheme.ACCENT_GOLD_SOFT);
		invOutputPanel.add(outHdr);
		for (SessionOutput o : session.getOutputs())
		{
			invOutputPanel.add(CraftWiseTheme.kvRow(o.getItemName(), NumberFormatter.formatDecimal(o.getExpectedQuantity(), 1) + "x", CraftWiseTheme.TEXT_PRIMARY));
		}

		invOutputPanel.revalidate();
		invOutputPanel.repaint();
	}

	private JPanel buildCompareCard()
	{
		JPanel container = new JPanel();
		container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
		container.setOpaque(false);

		JPanel slotsBand = CraftWiseTheme.createCard();
		slotsBand.setLayout(new BoxLayout(slotsBand, BoxLayout.Y_AXIS));
		slotsBand.setAlignmentX(LEFT_ALIGNMENT);

		JLabel hdr = new JLabel("Compare Up To 5 Methods");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		slotsBand.add(hdr);
		slotsBand.add(Box.createVerticalStrut(3));

		List<CraftingRecipe> recipes = CraftingDataRepository.getAllRecipes();
		String[] defaultIds = {"cut_ruby", "air_battlestaff", "green_dhide_body", "blue_dhide_body", "gold_amulet_u"};
		compareCombos.clear();
		for (int i = 0; i < 5; i++)
		{
			JComboBox<RecipeChoice> combo = new JComboBox<>();
			combo.addItem(new RecipeChoice(null));
			int selectedIdx = 0;
			int idx = 1;
			for (CraftingRecipe r : recipes)
			{
				combo.addItem(new RecipeChoice(r));
				if (r.getId().equals(defaultIds[i]))
				{
					selectedIdx = idx;
				}
				idx++;
			}
			combo.setSelectedIndex(selectedIdx);
			CraftWiseTheme.styleComboBox(combo);
			combo.addActionListener(e -> {
				if (!syncingRecipe)
				{
					recalculateCompareView();
				}
			});
			compareCombos.add(combo);
			slotsBand.add(compactRow("#" + (i + 1), combo));
			slotsBand.add(Box.createVerticalStrut(2));
		}

		container.add(slotsBand);
		container.add(Box.createVerticalStrut(4));

		compareOutputPanel.setLayout(new BoxLayout(compareOutputPanel, BoxLayout.Y_AXIS));
		compareOutputPanel.setOpaque(false);
		compareOutputPanel.setAlignmentX(LEFT_ALIGNMENT);
		container.add(compareOutputPanel);
		return container;
	}

	private void recalculateCompareView()
	{
		if (syncingRecipe)
		{
			return;
		}
		compareOutputPanel.removeAll();
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();
		PriceCalculationMode mode = (PriceCalculationMode) modeCombo.getSelectedItem();
		boolean includeTax = taxCheckbox.isSelected();

		int shown = 0;
		for (int i = 0; i < compareCombos.size(); i++)
		{
			RecipeChoice choice = (RecipeChoice) compareCombos.get(i).getSelectedItem();
			if (choice == null || choice.recipe == null)
			{
				continue;
			}
			shown++;
			CraftingRecipe r = choice.recipe;
			CraftingResult res = ProfitCalculator.evaluate(
				r,
				dataManager.getCurrentPrices(),
				dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
				mode,
				includeTax,
				dataManager.getCustomPrices(),
				dataManager.getCustomActionsPerHour(),
				dataManager.getPlayerStats()
			);

			JPanel card = CraftWiseTheme.createCard();
			card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
			card.setAlignmentX(LEFT_ALIGNMENT);

			JLabel title = new JLabel("#" + (i + 1) + " [Lvl " + r.getRequiredLevel() + "] " + r.getName());
			title.setFont(CraftWiseTheme.getSmallBoldFont());
			title.setForeground(CraftWiseTheme.ACCENT_GOLD);
			card.add(title);

			if (!res.isPriceDataComplete())
			{
				card.add(CraftWiseTheme.kvRow("Market", "Missing prices", CraftWiseTheme.COLOR_WARNING_YELLOW));
			}
			else
			{
				Color plCol = res.getProfitPerCraft() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
				card.add(CraftWiseTheme.kvRow("XP/hr", NumberFormatter.formatXpPerHour(res.getXpPerHour(), compact), CraftWiseTheme.TEXT_PRIMARY));
				card.add(CraftWiseTheme.kvRow("Profit/Action", CurrencyFormatter.formatSignedGp(res.getProfitPerCraft(), compact), plCol));
				card.add(CraftWiseTheme.kvRow("Profit/Hour", CurrencyFormatter.formatSignedGp(res.getProfitPerHour(), compact) + "/h", plCol));
				card.add(CraftWiseTheme.kvRow("GP / XP", CurrencyFormatter.formatGpPerXp(res.getGpPerXp()), plCol));
				card.add(CraftWiseTheme.kvRow("Confidence", res.getPriceConfidence().getLabel() + " (" + res.getMin1hVolume() + "/h)",
					RecipeDetailPanel.colorForConfidence(res.getPriceConfidence())));
				card.add(CraftWiseTheme.kvRow("Access", (r.isMembers() ? "P2P" : "F2P")
					+ (r.hasQuestRequirement() ? " · Quest" : ""), CraftWiseTheme.TEXT_MUTED));
			}
			compareOutputPanel.add(card);
			compareOutputPanel.add(Box.createVerticalStrut(3));
		}

		if (shown == 0)
		{
			JLabel empty = new JLabel("Select up to 5 methods above to compare.");
			empty.setFont(CraftWiseTheme.getSmallFont());
			empty.setForeground(CraftWiseTheme.TEXT_MUTED);
			compareOutputPanel.add(empty);
		}
		compareOutputPanel.revalidate();
		compareOutputPanel.repaint();
	}

	private static JPanel compactRow(String label, java.awt.Component comp)
	{
		JPanel p = new JPanel(new BorderLayout(4, 0));
		p.setOpaque(false);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 23));
		JLabel lbl = new JLabel(label);
		lbl.setFont(CraftWiseTheme.getSmallFont());
		lbl.setForeground(CraftWiseTheme.TEXT_MUTED);
		p.add(lbl, BorderLayout.WEST);
		p.add(comp, BorderLayout.CENTER);
		return p;
	}

	private static DocumentListener simpleDocListener(Runnable r)
	{
		return new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { r.run(); }
			@Override public void removeUpdate(DocumentEvent e) { r.run(); }
			@Override public void changedUpdate(DocumentEvent e) { r.run(); }
		};
	}

	private static class RecipeChoice
	{
		final CraftingRecipe recipe;

		RecipeChoice(CraftingRecipe recipe)
		{
			this.recipe = recipe;
		}

		@Override
		public String toString()
		{
			return recipe == null ? "-- None --" : "[" + recipe.getRequiredLevel() + "] " + recipe.getName();
		}
	}
}
