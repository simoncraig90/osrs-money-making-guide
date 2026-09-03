package com.moneymakingguide;

import com.google.inject.Provides;
import com.moneymakingguide.data.MmgDataset;
import com.moneymakingguide.data.MmgItem;
import com.moneymakingguide.data.MmgMethod;
import com.moneymakingguide.service.DatasetService;
import com.moneymakingguide.service.PlayerState;
import com.moneymakingguide.service.PriceService;
import com.moneymakingguide.service.ProfitCalculator;
import com.moneymakingguide.service.RequirementService;
import com.moneymakingguide.ui.MoneyMakingGuidePanel;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Money Making Guide",
	description = "Browse the wiki's money makers, filtered to what your account can actually do",
	tags = {"money", "ge", "grand exchange", "profit", "gp", "wiki"}
)
public class MoneyMakingGuidePlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private MoneyMakingGuideConfig config;

	@Inject
	private DatasetService datasetService;

	@Inject
	private PriceService priceService;

	@Inject
	private ProfitCalculator profitCalculator;

	@Inject
	private RequirementService requirementService;

	private MoneyMakingGuidePanel panel;
	private NavigationButton navButton;

	// Container and item ids by number rather than by enum. The api.InventoryID and
	// api.ItemID enums are deprecated, and the gameval classes that replace them do not
	// exist before client 1.12.38 -- referencing either pins the plugin to a version
	// range for no benefit. These ids are game data and do not change.
	private static final int INV_INVENTORY = 93;
	private static final int INV_BANK = 95;
	private static final int ITEM_COINS = 995;

	/** Snapshot of the account, rebuilt on the client thread and read by Swing. */
	@Getter
	private volatile PlayerState playerState = PlayerState.LOGGED_OUT;

	/**
	 * Quest states are comparatively expensive to enumerate, so they are cached and only
	 * rescanned on login rather than every time a stat ticks over.
	 */
	private volatile Map<String, QuestState> questCache = new HashMap<>();

	/** Set by game events, drained on the next tick so a burst of them costs one rebuild. */
	private volatile boolean stateDirty;

	@Provides
	MoneyMakingGuideConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MoneyMakingGuideConfig.class);
	}

	@Override
	protected void startUp()
	{
		panel = injector.getInstance(MoneyMakingGuidePanel.class);
		panel.init(this, config, datasetService, priceService, profitCalculator, requirementService);

		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/moneymakingguide/icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Money Making Guide")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		// Show something immediately, then bring both feeds up to date in the background.
		datasetService.loadLocal();
		warmGuidePrices();
		SwingUtilities.invokeLater(panel::rebuild);

		if (datasetService.isStale())
		{
			datasetService.refresh(config.datasetUrl(), () ->
			{
				warmGuidePrices();
				SwingUtilities.invokeLater(panel::rebuild);
			});
		}

		priceService.refresh(false, () -> SwingUtilities.invokeLater(panel::rebuild));

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invokeLater(() -> rebuildPlayerState(true));
		}
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
		playerState = PlayerState.LOGGED_OUT;
		questCache = new HashMap<>();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				clientThread.invokeLater(() ->
				{
					rebuildPlayerState(true);
					return true;
				});
				warmGuidePrices();
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				playerState = PlayerState.LOGGED_OUT;
				questCache = new HashMap<>();
				repaint();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		stateDirty = true;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == INV_INVENTORY || event.getContainerId() == INV_BANK)
		{
			stateDirty = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (stateDirty)
		{
			stateDirty = false;
			rebuildPlayerState(false);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!MoneyMakingGuideConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		if ("datasetUrl".equals(event.getKey()))
		{
			datasetService.refresh(config.datasetUrl(), this::repaint);
			return;
		}

		repaint();
	}

	/** Refetches prices on the schedule the wiki API asks callers to respect. */
	@net.runelite.client.task.Schedule(period = 5, unit = java.time.temporal.ChronoUnit.MINUTES)
	public void refreshPrices()
	{
		priceService.refresh(false, this::repaint);
	}

	/** Forces both feeds to update; wired to the panel's refresh button. */
	public void forceRefresh()
	{
		datasetService.refresh(config.datasetUrl(), this::repaint);
		priceService.refresh(true, this::repaint);
		clientThread.invokeLater(() -> rebuildPlayerState(true));
	}

	/**
	 * Snapshots ItemManager's prices on the client thread.
	 *
	 * <p>Only used as a fallback and by the "wiki guide price" basis, but it has to
	 * happen here: ItemManager asserts it is on the client thread, and the panel runs
	 * on the EDT.
	 */
	private void warmGuidePrices()
	{
		MmgDataset dataset = datasetService.getDataset();
		if (dataset == null)
		{
			return;
		}

		Set<Integer> ids = new HashSet<>();
		for (MmgMethod method : dataset.methods())
		{
			for (MmgItem item : method.inputs())
			{
				if (item.id != null)
				{
					ids.add(item.id);
				}
			}
			for (MmgItem item : method.outputs())
			{
				if (item.id != null)
				{
					ids.add(item.id);
				}
			}
		}

		// The item cache is not readable until the client has finished starting, so this
		// returns false to be retried on a later tick rather than throwing.
		clientThread.invokeLater(() ->
		{
			if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
			{
				return false;
			}

			priceService.warmGuidePrices(ids);
			repaint();
			return true;
		});
	}

	/** Must run on the client thread. */
	private void rebuildPlayerState(boolean rescanQuests)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			playerState = PlayerState.LOGGED_OUT;
			repaint();
			return;
		}

		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		Skill[] skills = Skill.values();
		int[] levels = new int[skills.length];
		for (int i = 0; i < skills.length; i++)
		{
			levels[i] = client.getRealSkillLevel(skills[i]);
		}

		if (rescanQuests)
		{
			Map<String, QuestState> quests = new HashMap<>();
			for (Quest quest : Quest.values())
			{
				try
				{
					quests.put(RequirementService.normalise(quest.getName()), quest.getState(client));
				}
				catch (Exception e)
				{
					// A quest whose varbits this client build cannot resolve; skip it
					// rather than losing the whole scan.
					log.debug("could not read state for {}", quest.getName(), e);
				}
			}
			questCache = quests;
		}

		ItemContainer bank = client.getItemContainer(INV_BANK);
		ItemContainer inventory = client.getItemContainer(INV_INVENTORY);

		long coins = 0;
		if (inventory != null)
		{
			coins += inventory.count(ITEM_COINS);
		}
		if (bank != null)
		{
			coins += bank.count(ITEM_COINS);
		}

		playerState = PlayerState.builder()
			.loggedIn(true)
			.levels(levels)
			.combatLevel(local.getCombatLevel())
			.membersWorld(client.getWorldType().contains(WorldType.MEMBERS))
			.coins(coins)
			.bankSeen(bank != null)
			.quests(questCache)
			.build();

		repaint();
	}

	private void repaint()
	{
		MoneyMakingGuidePanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuild);
		}
	}
}
