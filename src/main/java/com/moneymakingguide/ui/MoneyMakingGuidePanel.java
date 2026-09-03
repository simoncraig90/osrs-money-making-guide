package com.moneymakingguide.ui;

import com.moneymakingguide.MoneyMakingGuideConfig;
import com.moneymakingguide.MoneyMakingGuidePlugin;
import com.moneymakingguide.data.MmgDataset;
import com.moneymakingguide.data.MmgMethod;
import com.moneymakingguide.service.DatasetService;
import com.moneymakingguide.service.PlayerState;
import com.moneymakingguide.service.PriceService;
import com.moneymakingguide.service.ProfitCalculator;
import com.moneymakingguide.service.RequirementService;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class MoneyMakingGuidePanel extends PluginPanel
{
	private enum Sort
	{
		PROFIT("Profit"),
		CHEAPEST("Cheapest to start"),
		NAME("Name");

		private final String label;

		Sort(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private final JTextField search = new JTextField();
	private final JComboBox<Sort> sort = new JComboBox<>(Sort.values());
	private final JCheckBox hideIneligible = new JCheckBox("Hide what I can't do", true);
	private final JLabel status = new JLabel();
	private final JPanel results = new JPanel();

	private MoneyMakingGuidePlugin plugin;
	private MoneyMakingGuideConfig config;
	private DatasetService datasetService;
	private PriceService priceService;
	private ProfitCalculator profitCalculator;
	private RequirementService requirementService;

	@Inject
	MoneyMakingGuidePanel()
	{
		// Own the scroll pane so the filter controls stay pinned to the top.
		super(false);
	}

	public void init(MoneyMakingGuidePlugin plugin, MoneyMakingGuideConfig config,
					 DatasetService datasetService, PriceService priceService,
					 ProfitCalculator profitCalculator, RequirementService requirementService)
	{
		this.plugin = plugin;
		this.config = config;
		this.datasetService = datasetService;
		this.priceService = priceService;
		this.profitCalculator = profitCalculator;
		this.requirementService = requirementService;

		hideIneligible.setSelected(config.hideIneligible());
		buildLayout();
	}

	private void buildLayout()
	{
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(getBackground());

		search.setToolTipText("Filter by name");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});

		sort.addActionListener(e -> rebuild());
		sort.setFocusable(false);

		hideIneligible.setBackground(getBackground());
		hideIneligible.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		hideIneligible.setFont(FontManager.getRunescapeSmallFont());
		hideIneligible.setToolTipText("Hide methods with a skill requirement above your level");
		hideIneligible.addActionListener(e -> rebuild());

		JButton refresh = new JButton("Refresh");
		refresh.setFocusable(false);
		refresh.setFont(FontManager.getRunescapeSmallFont());
		refresh.addActionListener(e -> plugin.forceRefresh());

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 3));
		controls.setBackground(getBackground());
		controls.add(search);
		controls.add(sort);
		controls.add(hideIneligible);
		controls.add(refresh);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		statusRow.setBackground(getBackground());
		statusRow.add(status);

		header.add(controls);
		header.add(statusRow);
		add(header, BorderLayout.NORTH);

		results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
		results.setBackground(getBackground());

		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(getBackground());
		wrapper.add(results, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(wrapper,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setBackground(getBackground());
		add(scroll, BorderLayout.CENTER);
	}

	/** Recomputes and redraws the whole list. Must be called on the EDT. */
	public void rebuild()
	{
		if (plugin == null)
		{
			return;
		}

		results.removeAll();

		MmgDataset dataset = datasetService.getDataset();
		if (dataset == null || dataset.methods().isEmpty())
		{
			status.setText(html("No guide data. " + describeError()));
			revalidate();
			repaint();
			return;
		}

		PlayerState player = plugin.getPlayerState();
		Set<Integer> taxExempt = dataset.taxExemptIds();
		long bankroll = bankroll(player);
		boolean allowMembers = allowMembers(player);
		String query = search.getText().trim().toLowerCase(Locale.ROOT);

		List<Row> rows = new ArrayList<>();
		int hidden = 0;

		for (MmgMethod method : dataset.methods())
		{
			if (method.recurring && !config.includeRecurring())
			{
				continue;
			}

			if (!query.isEmpty() && !method.name.toLowerCase(Locale.ROOT).contains(query))
			{
				continue;
			}

			ProfitCalculator.Result profit = profitCalculator.compute(
				method, priceService, config.priceMode(), taxExempt);

			if (profit.getProfitPerHour() < config.minProfit())
			{
				continue;
			}

			RequirementService.Assessment assessment = requirementService.assess(
				method, player, profit.getCostPerHour(),
				config.hideUnaffordable() ? bankroll : -1,
				config.hardFilterQuests(), allowMembers);

			if (!assessment.isEligible() && hideIneligible.isSelected())
			{
				hidden++;
				continue;
			}

			rows.add(new Row(method, profit, assessment));
		}

		rows.sort(comparator());

		for (Row row : rows)
		{
			results.add(new MethodCard(row.method, row.profit, row.assessment));
		}

		status.setText(html(describeStatus(rows.size(), hidden, player)));
		revalidate();
		repaint();
	}

	private Comparator<Row> comparator()
	{
		Sort selected = (Sort) sort.getSelectedItem();
		if (selected == Sort.NAME)
		{
			return Comparator.comparing(r -> r.method.name);
		}
		if (selected == Sort.CHEAPEST)
		{
			return Comparator.comparingDouble((Row r) -> r.profit.getCostPerHour())
				.thenComparing(Comparator.comparingDouble((Row r) -> -r.profit.getProfitPerHour()));
		}
		return Comparator.comparingDouble(r -> -r.profit.getProfitPerHour());
	}

	private long bankroll(PlayerState player)
	{
		int override = config.bankrollOverride();
		if (override > 0)
		{
			return override;
		}
		return player.isLoggedIn() ? player.getCoins() : Long.MAX_VALUE;
	}

	private boolean allowMembers(PlayerState player)
	{
		switch (config.membersFilter())
		{
			case MEMBERS:
				return true;
			case FREE:
				return false;
			default:
				// On the login screen there is no world to match, so do not hide anything.
				return !player.isLoggedIn() || player.isMembersWorld();
		}
	}

	private String describeStatus(int shown, int hidden, PlayerState player)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(shown).append(" methods");

		if (hidden > 0)
		{
			sb.append(", ").append(hidden).append(" hidden");
		}

		sb.append("<br>Prices: ");
		if (priceService.hasLivePrices())
		{
			sb.append(config.priceMode().toString().toLowerCase(Locale.ROOT));
		}
		else
		{
			sb.append("cached (").append(describeError()).append(")");
		}

		sb.append("<br>Data: ").append(datasetService.getSource());

		if (!player.isLoggedIn())
		{
			sb.append("<br>Log in to filter by your levels");
		}
		else if (config.hideUnaffordable() && config.bankrollOverride() <= 0 && !player.isBankSeen())
		{
			sb.append("<br>Open your bank to count it as bankroll");
		}

		return sb.toString();
	}

	private String describeError()
	{
		String e = priceService.getLastError();
		if (e == null)
		{
			e = datasetService.getLastError();
		}
		return e == null ? "loading" : e;
	}

	private static String html(String body)
	{
		return "<html><body style='width:" + (PluginPanel.PANEL_WIDTH - 20) + "px'>" + body + "</body></html>";
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(PANEL_WIDTH + SCROLLBAR_WIDTH, super.getPreferredSize().height);
	}

	private static final class Row
	{
		private final MmgMethod method;
		private final ProfitCalculator.Result profit;
		private final RequirementService.Assessment assessment;

		private Row(MmgMethod method, ProfitCalculator.Result profit,
					RequirementService.Assessment assessment)
		{
			this.method = method;
			this.profit = profit;
			this.assessment = assessment;
		}
	}
}
