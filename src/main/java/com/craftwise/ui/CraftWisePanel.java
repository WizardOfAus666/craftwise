package com.craftwise.ui;

import com.craftwise.data.CraftingDataManager;
import com.craftwise.market.MarketWatcherService.AlertEvent;
import com.craftwise.model.CraftingResult;
import com.craftwise.model.ProfitReport;
import com.craftwise.ui.RecipeTablePanel.ViewMode;
import com.craftwise.util.NumberFormatter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import net.runelite.client.ui.PluginPanel;

/**
 * Main RuneLite panel for CraftWise with a clean single-row tab bar,
 * unobtrusive live status header, and optional Pop Out desktop window.
 */
public class CraftWisePanel extends PluginPanel
{
	public static final String NAV_DASHBOARD = "DASHBOARD";
	public static final String NAV_RECOMMENDATIONS = "RECOMMENDATIONS";
	public static final String NAV_METHODS = "METHODS";
	public static final String NAV_PLANNER = "PLANNER";
	public static final String NAV_PROFIT_CALC = "PROFIT_CALC";
	public static final String NAV_BREAK_EVEN = "BREAK_EVEN";
	public static final String NAV_PRICE_HISTORY = "PRICE_HISTORY";
	public static final String NAV_MARKET_WATCHER = "MARKET_WATCHER";
	public static final String NAV_BANK_CRAFTING = "BANK_CRAFTING";
	public static final String NAV_SETTINGS = "SETTINGS";
	public static final String NAV_DETAIL = "DETAIL";

	private static final int WIDE_THRESHOLD_PX = 430;
	private static final int SIDEBAR_PREFERRED_WIDTH = 340;

	private final CraftingDataManager dataManager;

	private final JPanel workspaceRoot = new JPanel(new BorderLayout(0, 6));
	private final JLabel titleLabel = new JLabel("CraftWise");
	private final JLabel statusDotLabel = new JLabel();
	private final CraftWiseTheme.CraftButton refreshButton = new CraftWiseTheme.CraftButton("Sync");
	private final CraftWiseTheme.CraftButton settingsButton = new CraftWiseTheme.CraftButton("Cfg");
	private final CraftWiseTheme.CraftButton popOutButton = new CraftWiseTheme.CraftButton("Expand");

	private final JPanel navBar = new JPanel(new GridLayout(2, 4, 2, 2));
	private final Map<String, CraftWiseTheme.CraftButton> navButtons = new LinkedHashMap<>();
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cardContainer = new JPanel(cardLayout);

	private final DashboardPanel dashboardPanel;
	private final RecipeTablePanel recipesPanel;
	private final TrainingPlannerPanel plannerPanel;
	private final CalculatorPanel calculatorPanel;
	private final PriceHistoryPanel priceHistoryPanel;
	private final MarketWatcherPanel marketWatcherPanel;
	private final BankCraftingPanel bankCraftingPanel;
	private final SettingsPanel settingsPanel;
	private final RecipeDetailPanel detailPanel;

	private String activeNav = NAV_DASHBOARD;
	private String previousNavBeforeDetail = NAV_DASHBOARD;
	private JFrame detachedFrame = null;
	private boolean lastWideState = false;

	@Inject
	public CraftWisePanel(CraftingDataManager dataManager)
	{
		super(false);
		this.dataManager = dataManager;

		setLayout(new BorderLayout(0, 0));
		setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		workspaceRoot.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		workspaceRoot.add(buildTopHeaderAndNav(), BorderLayout.NORTH);

		dashboardPanel = new DashboardPanel(dataManager, this::openRecipeDetail);
		recipesPanel = new RecipeTablePanel(dataManager, ViewMode.RECOMMENDATIONS, this::openRecipeDetail);
		plannerPanel = new TrainingPlannerPanel(dataManager, this::openRecipeDetail);
		calculatorPanel = new CalculatorPanel(dataManager);
		priceHistoryPanel = new PriceHistoryPanel(dataManager);
		marketWatcherPanel = new MarketWatcherPanel(dataManager, this::openRecipeDetail);
		bankCraftingPanel = new BankCraftingPanel(dataManager, this::openRecipeDetail);
		settingsPanel = new SettingsPanel(dataManager);
		detailPanel = new RecipeDetailPanel(
			dataManager,
			() -> selectNav(previousNavBeforeDetail),
			r -> {
				calculatorPanel.openRecipe(r.getRecipe(), CalculatorPanel.SUB_PROFIT);
				selectNav(NAV_PROFIT_CALC);
			},
			r -> {
				calculatorPanel.openRecipe(r.getRecipe(), CalculatorPanel.SUB_BREAKEVEN);
				selectNav(NAV_BREAK_EVEN);
			},
			r -> {
				priceHistoryPanel.openRecipe(r.getRecipe());
				selectNav(NAV_PRICE_HISTORY);
			}
		);

		cardContainer.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		cardContainer.add(wrapScrollable(dashboardPanel), NAV_DASHBOARD);
		cardContainer.add(recipesPanel, NAV_RECOMMENDATIONS);
		cardContainer.add(wrapScrollable(plannerPanel), NAV_PLANNER);
		cardContainer.add(wrapScrollable(calculatorPanel), NAV_PROFIT_CALC);
		cardContainer.add(wrapScrollable(priceHistoryPanel), NAV_PRICE_HISTORY);
		cardContainer.add(marketWatcherPanel, NAV_MARKET_WATCHER);
		cardContainer.add(bankCraftingPanel, NAV_BANK_CRAFTING);
		cardContainer.add(wrapScrollable(settingsPanel), NAV_SETTINGS);
		cardContainer.add(wrapScrollable(detailPanel), NAV_DETAIL);

		workspaceRoot.add(cardContainer, BorderLayout.CENTER);
		workspaceRoot.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				applyResponsiveLayout(workspaceRoot.getWidth());
			}
		});

		add(workspaceRoot, BorderLayout.CENTER);

		selectNav(NAV_DASHBOARD);
		refreshAll();

		dataManager.addChangeListener(() -> SwingUtilities.invokeLater(this::refreshAll));
	}

	@Override
	public Dimension getPreferredSize()
	{
		Dimension superPref = super.getPreferredSize();
		int width = detachedFrame == null ? SIDEBAR_PREFERRED_WIDTH : 242;
		return new Dimension(width, superPref != null ? superPref.height : 600);
	}

	@Override
	public Dimension getMinimumSize()
	{
		Dimension superMin = super.getMinimumSize();
		return new Dimension(242, superMin != null ? superMin.height : 300);
	}

	private JPanel buildTopHeaderAndNav()
	{
		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setBackground(CraftWiseTheme.BG_MATTE_BLACK);

		// Clean 28px header bar
		JPanel bar = new JPanel(new BorderLayout(4, 0));
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
		bar.setAlignmentX(LEFT_ALIGNMENT);

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		left.setOpaque(false);
		titleLabel.setFont(CraftWiseTheme.getTitleFont());
		titleLabel.setForeground(CraftWiseTheme.TEXT_PRIMARY);
		statusDotLabel.setFont(CraftWiseTheme.getSmallFont());
		left.add(titleLabel);
		left.add(statusDotLabel);
		bar.add(left, BorderLayout.CENTER);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		right.setOpaque(false);

		refreshButton.setPreferredSize(new Dimension(38, 22));
		refreshButton.setToolTipText("Refresh OSRS Wiki GE prices");
		refreshButton.addActionListener(e -> {
			statusDotLabel.setText("Sync...");
			statusDotLabel.setForeground(CraftWiseTheme.COLOR_WARNING_YELLOW);
			dataManager.refreshPricesNowAsync();
		});

		settingsButton.setPreferredSize(new Dimension(32, 22));
		settingsButton.setToolTipText("Settings & Overrides");
		settingsButton.addActionListener(e -> selectNav(NAV_SETTINGS));

		popOutButton.setPreferredSize(new Dimension(48, 22));
		popOutButton.setToolTipText("Open in a wider window");
		popOutButton.addActionListener(e -> togglePopOut());

		right.add(refreshButton);
		right.add(settingsButton);
		right.add(popOutButton);
		bar.add(right, BorderLayout.EAST);

		top.add(bar);

		// Compact 2x4 tab grid (crisp and never clipped even in 225px sidebar)
		navBar.setOpaque(false);
		navBar.setAlignmentX(LEFT_ALIGNMENT);

		addNavButton(navBar, NAV_DASHBOARD, "Home", "Crafting overview & top methods");
		addNavButton(navBar, NAV_RECOMMENDATIONS, "Recipes", "Search, filter, and rank all crafting recipes");
		addNavButton(navBar, NAV_PLANNER, "Plan", "Training planner & budget optimiser");
		addNavButton(navBar, NAV_PROFIT_CALC, "Calc", "Profit, Break-Even & Inventory calculators");
		addNavButton(navBar, NAV_PRICE_HISTORY, "Charts", "Historical prices & profit margins");
		addNavButton(navBar, NAV_MARKET_WATCHER, "Market", "Live Crafting Market Watcher & opportunity alerts");
		addNavButton(navBar, NAV_BANK_CRAFTING, "Bank", "Bank-Aware Crafting scanner & material optimizer");
		addNavButton(navBar, NAV_SETTINGS, "Cfg", "Settings & Overrides");

		top.add(navBar);
		return top;
	}

	private void addNavButton(JPanel container, String navKey, String text, String tooltip)
	{
		CraftWiseTheme.CraftButton btn = new CraftWiseTheme.CraftButton(text);
		btn.setTabStyle(true);
		btn.setPreferredSize(new Dimension(42, 23));
		btn.setToolTipText(tooltip);
		btn.addActionListener(e -> selectNav(navKey));
		navButtons.put(navKey, btn);
		container.add(btn);
	}

	private void applyResponsiveLayout(int width)
	{
		boolean wide = width >= WIDE_THRESHOLD_PX;
		if (wide != lastWideState)
		{
			lastWideState = wide;
		}
		recipesPanel.setWideMode(wide);
		plannerPanel.setWideMode(wide);
		priceHistoryPanel.setWideMode(wide);
		marketWatcherPanel.setWideMode(wide);
		bankCraftingPanel.setWideMode(wide);
	}

	public void togglePopOut()
	{
		if (detachedFrame != null)
		{
			dockBack();
			return;
		}
		remove(workspaceRoot);
		revalidate();
		repaint();

		JPanel placeholder = new JPanel(new BorderLayout(0, 8));
		placeholder.setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		placeholder.setBorder(BorderFactory.createEmptyBorder(24, 12, 24, 12));
		JLabel info = new JLabel("<html><center>CraftWise is open in a<br>separate window.</center></html>", JLabel.CENTER);
		info.setFont(CraftWiseTheme.getRegularFont());
		info.setForeground(CraftWiseTheme.TEXT_MUTED);
		CraftWiseTheme.CraftButton dockBtn = new CraftWiseTheme.CraftButton("Dock to Sidebar", true);
		dockBtn.setPreferredSize(new Dimension(124, 26));
		dockBtn.addActionListener(e -> dockBack());
		JPanel wrapBtn = new JPanel(new FlowLayout(FlowLayout.CENTER));
		wrapBtn.setOpaque(false);
		wrapBtn.add(dockBtn);
		placeholder.add(info, BorderLayout.NORTH);
		placeholder.add(wrapBtn, BorderLayout.CENTER);
		add(placeholder, BorderLayout.CENTER);
		revalidate();
		repaint();

		popOutButton.setText("Dock");
		popOutButton.setToolTipText("Return CraftWise to RuneLite sidebar");

		detachedFrame = new JFrame("CraftWise");
		detachedFrame.setIconImage(CraftWiseTheme.createToolbarIcon());
		detachedFrame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		detachedFrame.getContentPane().setLayout(new BorderLayout());
		detachedFrame.getContentPane().setBackground(CraftWiseTheme.BG_MATTE_BLACK);
		detachedFrame.getContentPane().add(workspaceRoot, BorderLayout.CENTER);
		detachedFrame.setMinimumSize(new Dimension(540, 520));
		detachedFrame.setSize(900, 720);
		detachedFrame.setLocationRelativeTo(this);
		detachedFrame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e)
			{
				dockBack();
			}

			@Override
			public void windowClosed(WindowEvent e)
			{
				if (detachedFrame != null)
				{
					dockBack();
				}
			}
		});
		detachedFrame.setVisible(true);
		SwingUtilities.invokeLater(() -> applyResponsiveLayout(workspaceRoot.getWidth()));
	}

	private void dockBack()
	{
		JFrame f = detachedFrame;
		detachedFrame = null;
		if (f != null)
		{
			f.getContentPane().remove(workspaceRoot);
			f.setVisible(false);
			f.dispose();
		}
		popOutButton.setText("Expand");
		popOutButton.setToolTipText("Open in a wider window");

		removeAll();
		add(workspaceRoot, BorderLayout.CENTER);
		revalidate();
		repaint();
		SwingUtilities.invokeLater(() -> applyResponsiveLayout(workspaceRoot.getWidth()));
	}

	public void selectNav(String navKey)
	{
		if (!NAV_DETAIL.equals(navKey))
		{
			previousNavBeforeDetail = navKey;
		}
		this.activeNav = navKey;

		String effectiveNavTab = navKey;
		if (NAV_METHODS.equals(navKey))
		{
			effectiveNavTab = NAV_RECOMMENDATIONS;
		}
		else if (NAV_BREAK_EVEN.equals(navKey))
		{
			effectiveNavTab = NAV_PROFIT_CALC;
		}

		for (Map.Entry<String, CraftWiseTheme.CraftButton> entry : navButtons.entrySet())
		{
			entry.getValue().setActive(entry.getKey().equals(effectiveNavTab));
		}
		settingsButton.setActive(NAV_SETTINGS.equals(navKey));

		if (NAV_BREAK_EVEN.equals(navKey))
		{
			calculatorPanel.selectSubTab(CalculatorPanel.SUB_BREAKEVEN);
			cardLayout.show(cardContainer, NAV_PROFIT_CALC);
		}
		else if (NAV_PROFIT_CALC.equals(navKey))
		{
			if (CalculatorPanel.SUB_BREAKEVEN.equals(calculatorPanel.getActiveSubTab()))
			{
				calculatorPanel.selectSubTab(CalculatorPanel.SUB_PROFIT);
			}
			cardLayout.show(cardContainer, NAV_PROFIT_CALC);
		}
		else if (NAV_METHODS.equals(navKey))
		{
			cardLayout.show(cardContainer, NAV_RECOMMENDATIONS);
		}
		else
		{
			cardLayout.show(cardContainer, navKey);
		}
	}

	public void openRecipeDetail(CraftingResult result)
	{
		if (result == null)
		{
			return;
		}
		if (!NAV_DETAIL.equals(activeNav))
		{
			previousNavBeforeDetail = activeNav;
		}
		detailPanel.showRecipe(result);
		selectNav(NAV_DETAIL);
	}

	public void refreshAll()
	{
		updateHeaderStatus();
		dashboardPanel.refresh();
		recipesPanel.refresh();
		plannerPanel.refresh();
		calculatorPanel.refresh();
		priceHistoryPanel.refresh();
		marketWatcherPanel.refresh();
		bankCraftingPanel.refresh();
		settingsPanel.refresh();
		if (NAV_DETAIL.equals(activeNav))
		{
			detailPanel.refreshIfVisible();
		}
	}

	public void onMarketAlert(AlertEvent event)
	{
		if (event != null)
		{
			marketWatcherPanel.recordAlert(event);
		}
	}

	private void updateHeaderStatus()
	{
		boolean live = dataManager.getWikiPriceClient() != null && dataManager.getWikiPriceClient().isLiveConnected();
		ProfitReport report = dataManager.getLatestReport();
		long lastEpoch = (dataManager.getWikiPriceClient() != null && dataManager.getWikiPriceClient().getPriceCache() != null)
			? dataManager.getWikiPriceClient().getPriceCache().getLastPriceUpdateEpochSec()
			: 0L;
		String age = NumberFormatter.formatRelativeAge(lastEpoch, Instant.now().getEpochSecond());

		if (live && report != null && !report.isUsingStaleCache())
		{
			statusDotLabel.setText("· Live");
			statusDotLabel.setForeground(CraftWiseTheme.COLOR_PROFIT_GREEN);
			statusDotLabel.setToolTipText("Live OSRS Wiki prices (" + dataManager.getCurrentPrices().size() + " items · " + age + ")");
		}
		else if (!dataManager.getCurrentPrices().isEmpty())
		{
			statusDotLabel.setText("· Cache");
			statusDotLabel.setForeground(CraftWiseTheme.COLOR_WARNING_YELLOW);
			statusDotLabel.setToolTipText("Cached prices (" + age + ")");
		}
		else
		{
			statusDotLabel.setText("· Offline");
			statusDotLabel.setForeground(CraftWiseTheme.COLOR_LOSS_RED);
			statusDotLabel.setToolTipText("Offline — no prices loaded");
		}
	}

	private JScrollPane wrapScrollable(JPanel panel)
	{
		JScrollPane sp = new JScrollPane(panel);
		sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		CraftWiseTheme.styleScrollPane(sp);
		return sp;
	}
}
