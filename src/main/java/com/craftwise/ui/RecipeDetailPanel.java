package com.craftwise.ui;

import com.craftwise.calculator.MarketConfidenceEvaluator.PriceConfidence;
import com.craftwise.calculator.MultiStepCalculator;
import com.craftwise.calculator.MultiStepCalculator.MultiStepAnalysis;
import com.craftwise.calculator.MultiStepCalculator.StageSummary;
import com.craftwise.calculator.ProfitCalculator;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.data.CraftingRecipe.VerificationStatus;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.CraftingResult.BreakEvenStatus;
import com.craftwise.model.ItemPrice;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.api.ItemID;
import net.runelite.client.util.LinkBrowser;

/**
 * Detailed breakdown view for a selected {@link CraftingResult}, showing requirements,
 * OSRS Wiki reference link, input/output prices, batch P/L, break-even, and quick jump actions.
 */
public class RecipeDetailPanel extends CraftWiseTheme.ScrollableWidthPanel
{
	private final CraftingDataManager dataManager;
	private final Runnable onBack;
	private final Consumer<CraftingResult> onOpenProfitCalc;
	private final Consumer<CraftingResult> onOpenBreakEven;
	private final Consumer<CraftingResult> onOpenHistory;

	private CraftingResult currentResult;

	public RecipeDetailPanel(
		CraftingDataManager dataManager,
		Runnable onBack,
		Consumer<CraftingResult> onOpenProfitCalc,
		Consumer<CraftingResult> onOpenBreakEven,
		Consumer<CraftingResult> onOpenHistory)
	{
		this.dataManager = dataManager;
		this.onBack = onBack;
		this.onOpenProfitCalc = onOpenProfitCalc;
		this.onOpenBreakEven = onOpenBreakEven;
		this.onOpenHistory = onOpenHistory;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));
	}

	public void showRecipe(CraftingResult result)
	{
		this.currentResult = result;
		rebuild();
	}

	public void refreshIfVisible()
	{
		if (currentResult == null)
		{
			return;
		}
		String id = currentResult.getRecipe().getId();
		for (CraftingResult r : dataManager.getAllResults())
		{
			if (r.getRecipe().getId().equals(id))
			{
				this.currentResult = r;
				break;
			}
		}
		rebuild();
	}

	private void rebuild()
	{
		removeAll();
		if (currentResult == null)
		{
			revalidate();
			repaint();
			return;
		}

		CraftingRecipe rec = currentResult.getRecipe();
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();

		JPanel topBar = new JPanel(new BorderLayout(4, 0));
		topBar.setOpaque(false);
		topBar.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.CraftButton backBtn = new CraftWiseTheme.CraftButton("< Back");
		backBtn.setPreferredSize(new Dimension(64, 21));
		backBtn.addActionListener(e -> {
			if (onBack != null)
			{
				onBack.run();
			}
		});
		CraftWiseTheme.CraftButton wikiBtn = new CraftWiseTheme.CraftButton("OSRS Wiki");
		wikiBtn.setPreferredSize(new Dimension(76, 21));
		wikiBtn.addActionListener(e -> {
			if (rec.getWikiReference() != null)
			{
				LinkBrowser.browse(rec.getWikiReference());
			}
		});
		topBar.add(backBtn, BorderLayout.WEST);
		topBar.add(wikiBtn, BorderLayout.EAST);
		add(topBar);
		add(Box.createVerticalStrut(4));

		// Compact wrapped header band (never clips on right)
		JPanel headerBand = CraftWiseTheme.createGoldCard();
		headerBand.setLayout(new BorderLayout(6, 0));
		headerBand.setAlignmentX(LEFT_ALIGNMENT);

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(32, 32));
		dataManager.loadItemIcon(rec.getPrimaryItemId(), img -> iconLabel.setIcon(new ImageIcon(img)));
		headerBand.add(iconLabel, BorderLayout.WEST);

		JPanel titleCol = new JPanel();
		titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
		titleCol.setOpaque(false);

		JLabel nameLbl = new JLabel("<html><b>" + escapeHtml(rec.getName()) + "</b></html>");
		nameLbl.setFont(CraftWiseTheme.getBoldFont());
		nameLbl.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		nameLbl.setAlignmentX(LEFT_ALIGNMENT);

		JLabel metaLbl = new JLabel("<html>Lvl " + rec.getRequiredLevel()
			+ " · " + escapeHtml(rec.getCategory().getDisplayName())
			+ " · " + escapeHtml(rec.getMethodVariant()) + "</html>");
		metaLbl.setFont(CraftWiseTheme.getSmallFont());
		metaLbl.setForeground(CraftWiseTheme.TEXT_SECONDARY);
		metaLbl.setAlignmentX(LEFT_ALIGNMENT);

		JLabel verifyLbl = new JLabel("<html>" + escapeHtml(rec.getVerificationStatus().getLabel())
			+ " · " + NumberFormatter.formatXp(rec.getXpPerAction(), false)
			+ " (" + escapeHtml(currentResult.getXpRateSource().getLabel()) + ")</html>");
		verifyLbl.setFont(CraftWiseTheme.getSmallBoldFont());
		verifyLbl.setForeground(rec.getVerificationStatus() == VerificationStatus.VERIFIED
			? CraftWiseTheme.COLOR_PROFIT_GREEN
			: CraftWiseTheme.COLOR_WARNING_YELLOW);
		verifyLbl.setAlignmentX(LEFT_ALIGNMENT);

		titleCol.add(nameLbl);
		titleCol.add(metaLbl);
		titleCol.add(verifyLbl);
		headerBand.add(titleCol, BorderLayout.CENTER);
		add(headerBand);
		add(Box.createVerticalStrut(4));

		JPanel actionsRow = new JPanel(new GridLayout(1, 3, 3, 0));
		actionsRow.setOpaque(false);
		actionsRow.setAlignmentX(LEFT_ALIGNMENT);
		CraftWiseTheme.CraftButton calcBtn = new CraftWiseTheme.CraftButton("Profit", true);
		CraftWiseTheme.CraftButton beBtn = new CraftWiseTheme.CraftButton("Break-Ev");
		CraftWiseTheme.CraftButton histBtn = new CraftWiseTheme.CraftButton("History");
		calcBtn.setPreferredSize(new Dimension(60, 21));
		beBtn.setPreferredSize(new Dimension(60, 21));
		histBtn.setPreferredSize(new Dimension(60, 21));
		calcBtn.addActionListener(e -> { if (onOpenProfitCalc != null) onOpenProfitCalc.accept(currentResult); });
		beBtn.addActionListener(e -> { if (onOpenBreakEven != null) onOpenBreakEven.accept(currentResult); });
		histBtn.addActionListener(e -> { if (onOpenHistory != null) onOpenHistory.accept(currentResult); });
		actionsRow.add(calcBtn);
		actionsRow.add(beBtn);
		actionsRow.add(histBtn);
		add(actionsRow);
		add(Box.createVerticalStrut(4));

		JPanel finBand = CraftWiseTheme.createCard();
		finBand.setLayout(new BoxLayout(finBand, BoxLayout.Y_AXIS));
		finBand.setAlignmentX(LEFT_ALIGNMENT);

		JLabel finHdr = new JLabel("Profit & Loss (" + currentResult.getPriceMode().getLabel() + ")");
		finHdr.setFont(CraftWiseTheme.getSmallBoldFont());
		finHdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		finBand.add(finHdr);
		finBand.add(Box.createVerticalStrut(3));

		if (!currentResult.isPriceDataComplete())
		{
			finBand.add(wrappedLabel("Missing prices: " + String.join(", ", currentResult.getMissingPriceItemNames()),
				CraftWiseTheme.COLOR_WARNING_YELLOW));
		}
		else
		{
			finBand.add(CraftWiseTheme.kvRow("Mat Cost", CurrencyFormatter.formatGp(Math.round(currentResult.getMaterialCostPerCraft()), compact), CraftWiseTheme.TEXT_PRIMARY));
			finBand.add(CraftWiseTheme.kvRow("Gross Rev", CurrencyFormatter.formatGp(Math.round(currentResult.getGrossRevenuePerCraft()), compact), CraftWiseTheme.TEXT_PRIMARY));
			finBand.add(CraftWiseTheme.kvRow("GE Tax", CurrencyFormatter.formatGp(Math.round(currentResult.getGeTaxPerCraft()), compact), CraftWiseTheme.COLOR_WARNING_YELLOW));
			finBand.add(CraftWiseTheme.kvRow("Net Rev", CurrencyFormatter.formatGp(Math.round(currentResult.getNetRevenuePerCraft()), compact), CraftWiseTheme.TEXT_PRIMARY));
			Color plColor = currentResult.getProfitPerCraft() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
			finBand.add(CraftWiseTheme.kvRow("Profit / Craft", CurrencyFormatter.formatSignedGp(currentResult.getProfitPerCraft(), compact), plColor));
			finBand.add(CraftWiseTheme.kvRow("Profit / Inv (" + currentResult.getItemsPerInventory() + ")", CurrencyFormatter.formatSignedGp(currentResult.getProfitPerInventory(), compact), plColor));
			finBand.add(CraftWiseTheme.kvRow("10 / 100 Crafts", CurrencyFormatter.formatSignedGp(currentResult.getProfit10(), compact)
				+ " / " + CurrencyFormatter.formatSignedGp(currentResult.getProfit100(), compact), plColor));
			finBand.add(CraftWiseTheme.kvRow("1,000 Crafts", CurrencyFormatter.formatSignedGp(currentResult.getProfit1000(), compact), plColor));
			finBand.add(CraftWiseTheme.kvRow("Hourly P/L", CurrencyFormatter.formatSignedGp(currentResult.getProfitPerHour(), compact) + "/h", plColor));
			finBand.add(CraftWiseTheme.kvRow("GP / XP", CurrencyFormatter.formatGpPerXp(currentResult.getGpPerXp()), plColor));
			finBand.add(CraftWiseTheme.kvRow("XP / Hour", NumberFormatter.formatXpPerHour(currentResult.getXpPerHour(), compact), CraftWiseTheme.TEXT_PRIMARY));
			finBand.add(CraftWiseTheme.kvRow("GE Confidence",
				currentResult.getPriceConfidence().getLabel()
					+ " (" + NumberFormatter.formatInt(currentResult.getMin1hVolume()) + "/h vol)",
				colorForConfidence(currentResult.getPriceConfidence())));

			if (currentResult.getBreakEvenSellPrice() != null)
			{
				Color beColor = colorForStatus(currentResult.getBreakEvenStatus());
				finBand.add(CraftWiseTheme.kvRow("Break-Even",
					CurrencyFormatter.formatGp(currentResult.getBreakEvenSellPrice(), false)
						+ " (" + currentResult.getBreakEvenStatus().getLabel() + ")", beColor));
			}
			if (currentResult.getActionsToTarget() > 0L)
			{
				finBand.add(CraftWiseTheme.kvRow("To Target", NumberFormatter.formatInt(currentResult.getActionsToTarget())
					+ " (" + NumberFormatter.formatHours(currentResult.getHoursToTarget()) + ")", CraftWiseTheme.TEXT_SECONDARY));
			}
			if (!currentResult.getMarketWarnings().isEmpty())
			{
				for (String w : currentResult.getMarketWarnings())
				{
					finBand.add(wrappedLabel("! " + w, CraftWiseTheme.COLOR_WARNING_YELLOW));
				}
			}
		}
		add(finBand);
		add(Box.createVerticalStrut(4));

		JPanel ioBand = CraftWiseTheme.createCard();
		ioBand.setLayout(new BoxLayout(ioBand, BoxLayout.Y_AXIS));
		ioBand.setAlignmentX(LEFT_ALIGNMENT);
		JLabel ioHdr = new JLabel("Inputs & Outputs");
		ioHdr.setFont(CraftWiseTheme.getSmallBoldFont());
		ioHdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		ioBand.add(ioHdr);
		ioBand.add(Box.createVerticalStrut(3));

		for (RecipeInput in : rec.getInputs())
		{
			String priceTxt;
			if (in.getItemId() == ItemID.COINS_995)
			{
				priceTxt = "1 gp ea";
			}
			else
			{
				ItemPrice ip = ProfitCalculator.resolveItemPrice(in.getItemId(), in.getItemName(),
					dataManager.getCurrentPrices(),
					dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null);
				Long p = ip != null ? ip.resolveBuyPrice(currentResult.getPriceMode(), null) : null;
				priceTxt = p != null ? CurrencyFormatter.formatGp(p, false) : "Missing";
			}
			ioBand.add(CraftWiseTheme.kvRow("In: " + formatQty(in.getQuantity()) + "x " + in.getItemName(), priceTxt, CraftWiseTheme.TEXT_SECONDARY));
		}

		for (RecipeOutput out : rec.getOutputs())
		{
			String priceTxt;
			if (!out.isTradeable())
			{
				priceTxt = "Untradeable";
			}
			else
			{
				ItemPrice ip = ProfitCalculator.resolveItemPrice(out.getItemId(), out.getItemName(),
					dataManager.getCurrentPrices(),
					dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null);
				Long p = ip != null ? ip.resolveSellPrice(currentResult.getPriceMode(), null) : null;
				priceTxt = p != null ? CurrencyFormatter.formatGp(p, false) : "Missing";
			}
			String probNote = out.getProbability() < 0.9999 ? " (" + NumberFormatter.formatPercent(out.getProbability() * 100.0) + ")" : "";
			ioBand.add(CraftWiseTheme.kvRow("Out: " + formatQty(out.getQuantity()) + "x " + out.getItemName() + probNote, priceTxt, CraftWiseTheme.TEXT_PRIMARY));
		}
		add(ioBand);
		add(Box.createVerticalStrut(4));

		JPanel reqBand = CraftWiseTheme.createCard();
		reqBand.setLayout(new BoxLayout(reqBand, BoxLayout.Y_AXIS));
		reqBand.setAlignmentX(LEFT_ALIGNMENT);
		JLabel reqHdr = new JLabel("Requirements & Notes");
		reqHdr.setFont(CraftWiseTheme.getSmallBoldFont());
		reqHdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		reqBand.add(reqHdr);
		reqBand.add(Box.createVerticalStrut(3));

		if (!rec.getAllToolsAndMoulds().isEmpty())
		{
			reqBand.add(wrappedLabel("Tools / Moulds: " + String.join(", ", rec.getAllToolsAndMoulds()), CraftWiseTheme.TEXT_SECONDARY));
		}
		else if (!rec.getRequiredEquipment().isEmpty())
		{
			reqBand.add(wrappedLabel("Tools: " + String.join(", ", rec.getRequiredEquipment()), CraftWiseTheme.TEXT_SECONDARY));
		}
		if (!rec.getQuestRequirements().isEmpty())
		{
			reqBand.add(wrappedLabel("Quests: " + String.join(", ", rec.getQuestRequirements()), CraftWiseTheme.COLOR_WARNING_YELLOW));
		}
		if (!rec.getUnlockRequirements().isEmpty())
		{
			reqBand.add(wrappedLabel("Unlocks: " + String.join(", ", rec.getUnlockRequirements()), CraftWiseTheme.COLOR_WARNING_YELLOW));
		}
		if (!rec.getAdditionalRequirements().isEmpty())
		{
			reqBand.add(wrappedLabel("Reqs: " + String.join(", ", rec.getAdditionalRequirements()), CraftWiseTheme.TEXT_SECONDARY));
		}
		reqBand.add(wrappedLabel("Access: " + (rec.isMembers() ? "Members (P2P)" : "Free-to-Play (F2P)")
			+ (rec.isBoostable() ? " · Boostable" : " · Not Boostable"), CraftWiseTheme.TEXT_MUTED));
		if (rec.getSpecialMechanics() != null && !rec.getSpecialMechanics().isEmpty())
		{
			reqBand.add(wrappedLabel("Notes: " + rec.getSpecialMechanics(), CraftWiseTheme.TEXT_MUTED));
		}
		if (rec.getXpRateBasis() != null && !rec.getXpRateBasis().isEmpty())
		{
			reqBand.add(wrappedLabel("Rate Basis: " + rec.getXpRateBasis(), CraftWiseTheme.TEXT_MUTED));
		}
		add(reqBand);

		// Multi-Step BUY Intermediate vs MAKE Intermediate card (Phase 4)
		MultiStepAnalysis chain = MultiStepCalculator.analyze(
			rec,
			CraftingDataRepository.getAllRecipes(),
			dataManager.getCurrentPrices(),
			dataManager.getWikiPriceClient() != null ? dataManager.getWikiPriceClient().getNameToIdIndex() : null,
			currentResult.getPriceMode(),
			currentResult.isGeTaxApplied(),
			dataManager.getCustomPrices()
		);
		if (chain.isHasMultiStepChain())
		{
			add(Box.createVerticalStrut(4));
			JPanel chainBand = CraftWiseTheme.createCard();
			chainBand.setLayout(new BoxLayout(chainBand, BoxLayout.Y_AXIS));
			chainBand.setAlignmentX(LEFT_ALIGNMENT);

			JLabel chHdr = new JLabel("Multi-Step Chain (Buy vs Make)");
			chHdr.setFont(CraftWiseTheme.getSmallBoldFont());
			chHdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
			chainBand.add(chHdr);
			chainBand.add(Box.createVerticalStrut(2));

			for (StageSummary st : chain.getStages())
			{
				chainBand.add(wrappedLabel(
					"Stage " + st.getStageIndex() + ": " + st.getRecipe().getName()
						+ " (+" + NumberFormatter.formatXp(st.getStageXpTotal(), compact) + ")",
					CraftWiseTheme.TEXT_SECONDARY));
			}
			if (chain.isBuyIntermediateAvailable())
			{
				chainBand.add(CraftWiseTheme.kvRow("Buy Interm. P/L",
					CurrencyFormatter.formatSignedGp(chain.getBuyIntermediateProfit(), compact)
						+ " (" + NumberFormatter.formatXp(chain.getBuyIntermediateXp(), compact) + ")",
					chain.getBuyIntermediateProfit() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED));
			}
			if (chain.isMakeAllStepsAvailable())
			{
				chainBand.add(CraftWiseTheme.kvRow("Make All P/L",
					CurrencyFormatter.formatSignedGp(chain.getMakeAllProfit(), compact)
						+ " (" + NumberFormatter.formatXp(chain.getMakeAllXp(), compact) + ")",
					chain.getMakeAllProfit() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED));
			}
			chainBand.add(wrappedLabel("Verdict: " + chain.getRecommendation() + " — " + chain.getSummaryText(),
				"MAKE INTERMEDIATE".equals(chain.getRecommendation()) ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.ACCENT_GOLD_SOFT));
			add(chainBand);
		}

		// Expandable calculation transparency breakdown ("Why is this calculated this way?")
		if (!currentResult.getCalculationBreakdown().isEmpty())
		{
			add(Box.createVerticalStrut(4));
			JPanel explainBand = CraftWiseTheme.createCard();
			explainBand.setLayout(new BoxLayout(explainBand, BoxLayout.Y_AXIS));
			explainBand.setAlignmentX(LEFT_ALIGNMENT);

			CraftWiseTheme.CraftButton toggleExplain = new CraftWiseTheme.CraftButton("Why is this calculated this way? (Show)");
			toggleExplain.setAlignmentX(LEFT_ALIGNMENT);
			JPanel linesPanel = new JPanel();
			linesPanel.setLayout(new BoxLayout(linesPanel, BoxLayout.Y_AXIS));
			linesPanel.setOpaque(false);
			linesPanel.setAlignmentX(LEFT_ALIGNMENT);
			linesPanel.setVisible(false);
			for (String line : currentResult.getCalculationBreakdown())
			{
				linesPanel.add(wrappedLabel(line, CraftWiseTheme.TEXT_MUTED));
			}
			toggleExplain.addActionListener(ev -> {
				boolean next = !linesPanel.isVisible();
				linesPanel.setVisible(next);
				toggleExplain.setText(next ? "Why is this calculated this way? (Hide)" : "Why is this calculated this way? (Show)");
				revalidate();
				repaint();
			});
			explainBand.add(toggleExplain);
			explainBand.add(Box.createVerticalStrut(2));
			explainBand.add(linesPanel);
			add(explainBand);
		}

		revalidate();
		repaint();
	}

	private static JLabel wrappedLabel(String text, Color color)
	{
		JLabel lbl = new JLabel("<html>" + escapeHtml(text) + "</html>");
		lbl.setFont(CraftWiseTheme.getSmallFont());
		lbl.setForeground(color);
		lbl.setBorder(BorderFactory.createEmptyBorder(1, 0, 2, 0));
		lbl.setAlignmentX(LEFT_ALIGNMENT);
		return lbl;
	}

	private static String escapeHtml(String s)
	{
		if (s == null) return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String formatQty(double q)
	{
		if (Math.abs(q - Math.rint(q)) < 1e-6)
		{
			return String.valueOf(Math.round(q));
		}
		return NumberFormatter.formatDecimal(q, 2);
	}

	public static Color colorForStatus(BreakEvenStatus status)
	{
		if (status == null)
		{
			return CraftWiseTheme.COLOR_MISSING_GREY;
		}
		switch (status)
		{
			case PROFITABLE:
				return CraftWiseTheme.COLOR_PROFIT_GREEN;
			case NEAR_BREAK_EVEN:
				return CraftWiseTheme.COLOR_WARNING_YELLOW;
			case LOSS:
				return CraftWiseTheme.COLOR_LOSS_RED;
			case UNAVAILABLE:
			default:
				return CraftWiseTheme.COLOR_MISSING_GREY;
		}
	}

	public static Color colorForConfidence(PriceConfidence confidence)
	{
		if (confidence == null)
		{
			return CraftWiseTheme.COLOR_MISSING_GREY;
		}
		switch (confidence)
		{
			case HIGH:
				return CraftWiseTheme.COLOR_PROFIT_GREEN;
			case MEDIUM:
				return CraftWiseTheme.COLOR_WARNING_YELLOW;
			case LOW:
				return CraftWiseTheme.COLOR_LOSS_RED;
			case UNAVAILABLE:
			default:
				return CraftWiseTheme.COLOR_MISSING_GREY;
		}
	}
}
