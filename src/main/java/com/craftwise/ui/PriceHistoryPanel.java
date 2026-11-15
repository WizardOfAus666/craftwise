package com.craftwise.ui;

import com.craftwise.api.ApiResponseHandler.TimeseriesData;
import com.craftwise.api.ApiResponseHandler.TimeseriesPoint;
import com.craftwise.calculator.GETransactionCalculator;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.data.CraftingDataRepository;
import com.craftwise.data.CraftingRecipe;
import com.craftwise.data.CraftingRecipe.RecipeInput;
import com.craftwise.data.CraftingRecipe.RecipeOutput;
import com.craftwise.model.CraftingResult;
import com.craftwise.util.CurrencyFormatter;
import com.craftwise.util.NumberFormatter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.ItemID;

/**
 * Historical GE price & profit margin chart using OSRS Wiki /timeseries,
 * with unclipped timeframe selector across docked and wide pop-out modes.
 */
public class PriceHistoryPanel extends CraftWiseTheme.ScrollableWidthPanel
{
	public enum TimePeriod
	{
		HOURS_6("6h", "5m", 6L * 3600L),
		HOURS_24("24h", "5m", 24L * 3600L),
		DAYS_7("7d", "1h", 7L * 86400L),
		DAYS_30("30d", "6h", 30L * 86400L),
		MONTHS_6("6m", "24h", 180L * 86400L),
		YEAR_1("1y", "24h", 365L * 86400L);

		final String label;
		final String wikiTimestep;
		final long windowSeconds;

		TimePeriod(String label, String wikiTimestep, long windowSeconds)
		{
			this.label = label;
			this.wikiTimestep = wikiTimestep;
			this.windowSeconds = windowSeconds;
		}
	}

	private final CraftingDataManager dataManager;

	private final JComboBox<RecipeChoice> recipeCombo = new JComboBox<>();
	private final JPanel periodRow = new JPanel(new GridLayout(2, 3, 2, 2));
	private final List<CraftWiseTheme.CraftButton> periodButtons = new ArrayList<>();
	private final JLabel statusLabel = new JLabel("Select a recipe to load history.");
	private final HistoryChartCanvas chartCanvas = new HistoryChartCanvas();
	private final JPanel statsPanel = new JPanel();
	private final JPanel alertsPanel = new JPanel();

	private TimePeriod selectedPeriod = TimePeriod.DAYS_7;
	private boolean wideMode = false;
	private boolean syncing = false;

	public PriceHistoryPanel(CraftingDataManager dataManager)
	{
		this.dataManager = dataManager;

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(2, 2, 8, 2));

		add(buildControlsBand());
		add(Box.createVerticalStrut(4));

		chartCanvas.setPreferredSize(new Dimension(210, 165));
		chartCanvas.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
		chartCanvas.setAlignmentX(LEFT_ALIGNMENT);
		add(chartCanvas);
		add(Box.createVerticalStrut(4));

		statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
		statsPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		statsPanel.setBorder(CraftWiseTheme.CARD_BORDER);
		statsPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(statsPanel);
		add(Box.createVerticalStrut(4));

		alertsPanel.setLayout(new BoxLayout(alertsPanel, BoxLayout.Y_AXIS));
		alertsPanel.setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
		alertsPanel.setBorder(CraftWiseTheme.CARD_BORDER_GOLD);
		alertsPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(alertsPanel);

		populateRecipes();
		updateMarketAlerts();
	}

	public void setWideMode(boolean wide)
	{
		if (this.wideMode == wide)
		{
			return;
		}
		this.wideMode = wide;
		periodRow.setLayout(wide ? new GridLayout(1, TimePeriod.values().length, 3, 0) : new GridLayout(2, 3, 2, 2));
		periodRow.revalidate();
		chartCanvas.setPreferredSize(new Dimension(wide ? 580 : 210, wide ? 210 : 165));
		chartCanvas.revalidate();
	}

	private JPanel buildControlsBand()
	{
		JPanel band = CraftWiseTheme.createCard();
		band.setLayout(new BoxLayout(band, BoxLayout.Y_AXIS));
		band.setAlignmentX(LEFT_ALIGNMENT);

		CraftWiseTheme.styleComboBox(recipeCombo);
		recipeCombo.addActionListener(e -> {
			if (!syncing)
			{
				loadSelectedHistory();
			}
		});
		band.add(CraftWiseTheme.stackedField("Recipe", recipeCombo));
		band.add(Box.createVerticalStrut(4));

		periodRow.setOpaque(false);
		periodRow.setAlignmentX(LEFT_ALIGNMENT);
		for (TimePeriod p : TimePeriod.values())
		{
			CraftWiseTheme.CraftButton btn = new CraftWiseTheme.CraftButton(p.label);
			btn.setPreferredSize(new Dimension(52, 20));
			btn.setActive(p == selectedPeriod);
			btn.addActionListener(e -> {
				selectedPeriod = p;
				for (int i = 0; i < periodButtons.size(); i++)
				{
					periodButtons.get(i).setActive(TimePeriod.values()[i] == selectedPeriod);
				}
				loadSelectedHistory();
			});
			periodButtons.add(btn);
			periodRow.add(btn);
		}
		band.add(periodRow);
		band.add(Box.createVerticalStrut(3));

		statusLabel.setFont(CraftWiseTheme.getSmallFont());
		statusLabel.setForeground(CraftWiseTheme.TEXT_MUTED);
		statusLabel.setAlignmentX(LEFT_ALIGNMENT);
		band.add(statusLabel);
		return band;
	}

	private void populateRecipes()
	{
		syncing = true;
		try
		{
			recipeCombo.removeAllItems();
			for (CraftingRecipe r : CraftingDataRepository.getAllRecipes())
			{
				if (r.isTradeableOutput())
				{
					recipeCombo.addItem(new RecipeChoice(r));
				}
			}
		}
		finally
		{
			syncing = false;
		}
	}

	public void openRecipe(CraftingRecipe recipe)
	{
		if (recipe == null)
		{
			return;
		}
		syncing = true;
		try
		{
			for (int i = 0; i < recipeCombo.getItemCount(); i++)
			{
				if (recipeCombo.getItemAt(i).recipe.getId().equals(recipe.getId()))
				{
					recipeCombo.setSelectedIndex(i);
					break;
				}
			}
		}
		finally
		{
			syncing = false;
		}
		loadSelectedHistory();
	}

	public void refresh()
	{
		updateMarketAlerts();
	}

	public void loadSelectedHistory()
	{
		Object sel = recipeCombo.getSelectedItem();
		if (!(sel instanceof RecipeChoice))
		{
			return;
		}
		CraftingRecipe recipe = ((RecipeChoice) sel).recipe;
		RecipeOutput primaryOut = recipe.getPrimaryOutput();
		if (primaryOut == null || !primaryOut.isTradeable())
		{
			statusLabel.setText("No tradeable output for price history.");
			return;
		}

		List<Integer> neededIds = new ArrayList<>();
		neededIds.add(primaryOut.getItemId());
		for (RecipeInput in : recipe.getInputs())
		{
			if (in.getItemId() != ItemID.COINS_995 && !neededIds.contains(in.getItemId()))
			{
				neededIds.add(in.getItemId());
			}
		}

		statusLabel.setText("Loading " + selectedPeriod.label + " history...");
		Map<Integer, TimeseriesData> fetched = new ConcurrentHashMap<>();

		for (int itemId : neededIds)
		{
			dataManager.fetchTimeseriesAsync(
				itemId,
				selectedPeriod.wikiTimestep,
				data -> {
					fetched.put(itemId, data);
					if (fetched.size() == neededIds.size())
					{
						SwingUtilities.invokeLater(() -> renderHistory(recipe, fetched));
					}
				},
				err -> SwingUtilities.invokeLater(() -> statusLabel.setText("Unavailable: " + err))
			);
		}
	}

	private void renderHistory(CraftingRecipe recipe, Map<Integer, TimeseriesData> rawSeries)
	{
		long nowSec = Instant.now().getEpochSecond();
		long cutoffSec = nowSec - selectedPeriod.windowSeconds;

		int outId = recipe.getPrimaryOutput().getItemId();
		TimeseriesData outData = rawSeries.get(outId);
		if (outData == null || outData.getPoints().isEmpty())
		{
			statusLabel.setText("No history for " + recipe.getName());
			chartCanvas.setSeries(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
			return;
		}

		TreeMap<Long, Double> outByTs = new TreeMap<>();
		for (TimeseriesPoint p : outData.getPoints())
		{
			if (p.getTimestamp() >= cutoffSec && p.getMidpointPrice() != null)
			{
				outByTs.put(p.getTimestamp(), (double) p.getMidpointPrice());
			}
		}
		if (outByTs.isEmpty())
		{
			for (TimeseriesPoint p : outData.getPoints())
			{
				if (p.getMidpointPrice() != null)
				{
					outByTs.put(p.getTimestamp(), (double) p.getMidpointPrice());
				}
			}
		}

		Map<Integer, TreeMap<Long, Long>> inputMaps = new ConcurrentHashMap<>();
		for (RecipeInput in : recipe.getInputs())
		{
			if (in.getItemId() == ItemID.COINS_995) continue;
			TimeseriesData inData = rawSeries.get(in.getItemId());
			TreeMap<Long, Long> tm = new TreeMap<>();
			if (inData != null)
			{
				for (TimeseriesPoint p : inData.getPoints())
				{
					if (p.getMidpointPrice() != null)
					{
						tm.put(p.getTimestamp(), p.getMidpointPrice());
					}
				}
			}
			inputMaps.put(in.getItemId(), tm);
		}

		boolean applyTax = dataManager.getConfig() == null || dataManager.getConfig().includeGeTax();
		List<Double> outputSeries = new ArrayList<>();
		List<Double> matSeries = new ArrayList<>();
		List<Double> marginSeries = new ArrayList<>();

		for (Map.Entry<Long, Double> e : outByTs.entrySet())
		{
			long ts = e.getKey();
			double unitOutPrice = e.getValue();
			double grossRev = unitOutPrice * recipe.getPrimaryOutput().getExpectedQuantity();
			double tax = GETransactionCalculator.calculateExpectedTax(
				Math.round(unitOutPrice),
				recipe.getPrimaryOutput().getExpectedQuantity(),
				outId,
				recipe.getPrimaryOutput().isTaxExempt(),
				applyTax
			);

			double matCost = 0.0;
			boolean completeMat = true;
			for (RecipeInput in : recipe.getInputs())
			{
				if (in.getItemId() == ItemID.COINS_995)
				{
					matCost += in.getQuantity();
					continue;
				}
				TreeMap<Long, Long> tm = inputMaps.get(in.getItemId());
				Long nearest = findNearestPrice(tm, ts);
				if (nearest == null)
				{
					completeMat = false;
					break;
				}
				matCost += in.getQuantity() * nearest;
			}

			outputSeries.add(grossRev);
			if (completeMat)
			{
				matSeries.add(matCost);
				marginSeries.add((grossRev - tax) - matCost);
			}
		}

		chartCanvas.setSeries(outputSeries, matSeries, marginSeries);

		statsPanel.removeAll();
		boolean compact = dataManager.getConfig() == null || dataManager.getConfig().compactNumbers();
		statusLabel.setText(recipe.getName() + " (" + selectedPeriod.label + ", " + outputSeries.size() + " pts)");

		JLabel hdr = new JLabel("History Stats (" + selectedPeriod.label + ")");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		statsPanel.add(hdr);

		if (outData.getLatestPrice() != null)
		{
			statsPanel.add(CraftWiseTheme.kvRow("Latest Output", CurrencyFormatter.formatGp(outData.getLatestPrice(), compact), CraftWiseTheme.TEXT_PRIMARY));
			statsPanel.add(CraftWiseTheme.kvRow("Range", CurrencyFormatter.formatGp(outData.getMinPrice(), compact)
				+ "-" + CurrencyFormatter.formatGp(outData.getMaxPrice(), compact), CraftWiseTheme.TEXT_SECONDARY));
			statsPanel.add(CraftWiseTheme.kvRow("Change", NumberFormatter.formatPercent(outData.getPercentChange())
				+ " (" + outData.getTrend().getLabel() + ")",
				outData.getPercentChange() >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED));
			statsPanel.add(CraftWiseTheme.kvRow("Volatility", NumberFormatter.formatPercent(outData.getVolatilityPercent())
				+ (outData.isUnusualSpike() ? " [Spike]" : ""),
				outData.isUnusualSpike() ? CraftWiseTheme.COLOR_WARNING_YELLOW : CraftWiseTheme.TEXT_MUTED));
		}

		if (marginSeries.size() >= 2)
		{
			double firstM = marginSeries.get(0);
			double lastM = marginSeries.get(marginSeries.size() - 1);
			double deltaM = lastM - firstM;
			String marginDir = deltaM > 10 ? "Rising" : (deltaM < -10 ? "Falling" : "Flat");
			Color mColor = deltaM >= 0 ? CraftWiseTheme.COLOR_PROFIT_GREEN : CraftWiseTheme.COLOR_LOSS_RED;
			statsPanel.add(CraftWiseTheme.kvRow("Margin Trend", marginDir + " (" + CurrencyFormatter.formatSignedGp(deltaM, compact) + ")", mColor));
		}

		JLabel disclaimer = new JLabel("<html>Historical OSRS Wiki trade averages only.</html>");
		disclaimer.setFont(CraftWiseTheme.getSmallFont());
		disclaimer.setForeground(CraftWiseTheme.TEXT_LOCKED);
		disclaimer.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		statsPanel.add(disclaimer);

		statsPanel.revalidate();
		statsPanel.repaint();
	}

	private void updateMarketAlerts()
	{
		alertsPanel.removeAll();
		JLabel hdr = new JLabel("Current Market Snapshot");
		hdr.setFont(CraftWiseTheme.getSmallBoldFont());
		hdr.setForeground(CraftWiseTheme.ACCENT_GOLD);
		alertsPanel.add(hdr);

		int unprofitableCount = 0;
		int totalComplete = 0;
		for (CraftingResult r : dataManager.getAllResults())
		{
			if (r.isPriceDataComplete())
			{
				totalComplete++;
				if (r.isLossMaking())
				{
					unprofitableCount++;
				}
			}
		}
		alertsPanel.add(CraftWiseTheme.kvRow("Loss-Making", unprofitableCount + " / " + totalComplete, CraftWiseTheme.COLOR_LOSS_RED));
		alertsPanel.add(CraftWiseTheme.kvRow("Profitable", (totalComplete - unprofitableCount) + " / " + totalComplete, CraftWiseTheme.COLOR_PROFIT_GREEN));
		alertsPanel.revalidate();
		alertsPanel.repaint();
	}

	private static Long findNearestPrice(TreeMap<Long, Long> tm, long ts)
	{
		if (tm == null || tm.isEmpty())
		{
			return null;
		}
		Long exact = tm.get(ts);
		if (exact != null)
		{
			return exact;
		}
		Map.Entry<Long, Long> floor = tm.floorEntry(ts);
		Map.Entry<Long, Long> ceil = tm.ceilingEntry(ts);
		if (floor == null) return ceil.getValue();
		if (ceil == null) return floor.getValue();
		return Math.abs(ts - floor.getKey()) <= Math.abs(ceil.getKey() - ts)
			? floor.getValue()
			: ceil.getValue();
	}

	private static class RecipeChoice
	{
		final CraftingRecipe recipe;
		RecipeChoice(CraftingRecipe recipe) { this.recipe = recipe; }
		@Override public String toString() { return recipe.getName(); }
	}

	private static class HistoryChartCanvas extends JPanel
	{
		private List<Double> outLine = Collections.emptyList();
		private List<Double> matLine = Collections.emptyList();
		private List<Double> marginLine = Collections.emptyList();

		HistoryChartCanvas()
		{
			setBackground(CraftWiseTheme.BG_DEEP_CHARCOAL);
			setBorder(BorderFactory.createLineBorder(CraftWiseTheme.BORDER_SUBTLE, 1));
		}

		void setSeries(List<Double> out, List<Double> mat, List<Double> margin)
		{
			this.outLine = out != null ? out : Collections.emptyList();
			this.matLine = mat != null ? mat : Collections.emptyList();
			this.marginLine = margin != null ? margin : Collections.emptyList();
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
			int padL = 8;
			int padR = 8;
			int padT = 22;
			int padB = 12;

			g2.setFont(CraftWiseTheme.getSmallFont());
			g2.setColor(CraftWiseTheme.ACCENT_GOLD);
			g2.drawString("- Out", padL, 13);
			g2.setColor(CraftWiseTheme.COLOR_INFO_BLUE);
			g2.drawString("- Mat", padL + 46, 13);
			g2.setColor(CraftWiseTheme.COLOR_PROFIT_GREEN);
			g2.drawString("- Margin", padL + 92, 13);

			if (outLine.size() < 2)
			{
				g2.setColor(CraftWiseTheme.TEXT_MUTED);
				g2.drawString("Select a recipe above", padL + 4, h / 2);
				g2.dispose();
				return;
			}

			double minVal = Double.MAX_VALUE;
			double maxVal = -Double.MAX_VALUE;
			for (double v : outLine) { minVal = Math.min(minVal, v); maxVal = Math.max(maxVal, v); }
			for (double v : matLine) { minVal = Math.min(minVal, v); maxVal = Math.max(maxVal, v); }
			for (double v : marginLine) { minVal = Math.min(minVal, v); maxVal = Math.max(maxVal, v); }
			if (Math.abs(maxVal - minVal) < 1.0)
			{
				maxVal += 10.0;
				minVal -= 10.0;
			}

			int plotW = Math.max(10, w - padL - padR);
			int plotH = Math.max(10, h - padT - padB);

			if (minVal < 0 && maxVal > 0)
			{
				int yZero = padT + (int) Math.round(((maxVal - 0.0) / (maxVal - minVal)) * plotH);
				g2.setColor(CraftWiseTheme.BORDER_DEFAULT);
				g2.drawLine(padL, yZero, padL + plotW, yZero);
			}

			drawSeries(g2, matLine, CraftWiseTheme.COLOR_INFO_BLUE, minVal, maxVal, padL, padT, plotW, plotH);
			drawSeries(g2, outLine, CraftWiseTheme.ACCENT_GOLD, minVal, maxVal, padL, padT, plotW, plotH);
			Color mColor = (!marginLine.isEmpty() && marginLine.get(marginLine.size() - 1) >= 0)
				? CraftWiseTheme.COLOR_PROFIT_GREEN
				: CraftWiseTheme.COLOR_LOSS_RED;
			drawSeries(g2, marginLine, mColor, minVal, maxVal, padL, padT, plotW, plotH);

			g2.dispose();
		}

		private void drawSeries(
			Graphics2D g2, List<Double> data, Color color,
			double minV, double maxV, int padL, int padT, int plotW, int plotH)
		{
			if (data == null || data.size() < 2)
			{
				return;
			}
			g2.setColor(color);
			g2.setStroke(new BasicStroke(1.5f));
			int n = data.size();
			for (int i = 0; i < n - 1; i++)
			{
				int x1 = padL + (int) Math.round((i * (double) plotW) / (n - 1));
				int x2 = padL + (int) Math.round(((i + 1) * (double) plotW) / (n - 1));
				int y1 = padT + (int) Math.round(((maxV - data.get(i)) / (maxV - minV)) * plotH);
				int y2 = padT + (int) Math.round(((maxV - data.get(i + 1)) / (maxV - minV)) * plotH);
				g2.drawLine(x1, y1, x2, y2);
			}
		}
	}
}
