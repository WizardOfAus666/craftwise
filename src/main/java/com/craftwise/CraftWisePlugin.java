package com.craftwise;

import com.craftwise.bank.BankInventoryScanner;
import com.craftwise.data.CraftingDataManager;
import com.craftwise.market.MarketWatcherService.AlertEvent;
import com.craftwise.ui.CraftWisePanel;
import com.craftwise.ui.CraftWiseTheme;
import com.google.inject.Provides;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main RuneLite plugin entry point for CraftWise.
 */
@PluginDescriptor(
	name = "CraftWise",
	description = "OSRS Crafting training, profit/loss & Grand Exchange analysis tool powered by live OSRS Wiki prices",
	tags = {
		"crafting",
		"profit",
		"calculator",
		"training",
		"xp",
		"grand exchange",
		"skilling",
		"jewellery",
		"gems",
		"leather"
	}
)
public class CraftWisePlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(CraftWisePlugin.class);

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private CraftWiseConfig config;

	@Inject
	private CraftingDataManager dataManager;

	@Inject
	private Client client;

	@Inject
	private Notifier notifier;

	private CraftWisePanel panel;
	private NavigationButton navButton;

	@Provides
	CraftWiseConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CraftWiseConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		log.info("[CraftWise] Starting CraftWise plugin...");
		dataManager.setAlertDispatcher(this::handleMarketAlert);
		dataManager.startUp();
		dataManager.scanContainersOnClientThread();

		panel = new CraftWisePanel(dataManager);
		BufferedImage icon = CraftWiseTheme.createToolbarIcon();

		navButton = NavigationButton.builder()
			.tooltip("CraftWise — Crafting Training & GE Analyzer")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		if (clientToolbar != null)
		{
			clientToolbar.addNavigation(navButton);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.info("[CraftWise] Shutting down CraftWise plugin...");
		if (clientToolbar != null && navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (dataManager != null)
		{
			dataManager.setAlertDispatcher(null);
			dataManager.shutDown();
		}
		panel = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (dataManager == null || event == null)
		{
			return;
		}
		dataManager.refreshPlayerStatsFromClient();
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			dataManager.onLoggedOut();
		}
		else if (event.getGameState() == GameState.LOGGED_IN)
		{
			dataManager.scanContainersOnClientThread();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (dataManager == null || event == null || event.getItemContainer() == null)
		{
			return;
		}
		int containerId = event.getContainerId();
		if (containerId == InventoryID.BANK.getId())
		{
			boolean open = client != null && BankInventoryScanner.isBankInterfaceOpen(client);
			dataManager.scanBankContainer(event.getItemContainer(), open);
		}
		else if (containerId == InventoryID.INVENTORY.getId())
		{
			dataManager.scanInventoryContainer(event.getItemContainer());
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (dataManager == null || event == null)
		{
			return;
		}
		if (event.getGroupId() == InterfaceID.BANKMAIN && client != null)
		{
			dataManager.scanBankContainer(client.getItemContainer(InventoryID.BANK), true);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (dataManager == null || event == null)
		{
			return;
		}
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			dataManager.onBankClosed();
		}
	}

	private void handleMarketAlert(AlertEvent alert)
	{
		if (alert == null || config == null || !config.enableMarketAlerts())
		{
			return;
		}
		if (panel != null)
		{
			javax.swing.SwingUtilities.invokeLater(() -> {
				if (panel != null)
				{
					panel.onMarketAlert(alert);
				}
			});
		}
		if (notifier != null)
		{
			notifier.notify("[CraftWise " + alert.getAlertType() + "] " + alert.getMessage(), TrayIcon.MessageType.INFO);
		}
		if (config.playAlertSound())
		{
			try
			{
				Toolkit.getDefaultToolkit().beep();
			}
			catch (Throwable ignored)
			{
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (dataManager != null && event != null && event.getSkill() == Skill.CRAFTING)
		{
			dataManager.refreshPlayerStatsFromClient();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (dataManager != null)
		{
			dataManager.refreshPlayerStatsFromClient();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event == null || !CraftWiseConfig.CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (dataManager != null)
		{
			dataManager.syncFromConfig();
			dataManager.recalculateAll();
			if (panel != null)
			{
				javax.swing.SwingUtilities.invokeLater(panel::refreshAll);
			}
		}
	}

	public CraftWisePanel getPanel()
	{
		return panel;
	}

	public CraftingDataManager getDataManager()
	{
		return dataManager;
	}
}
