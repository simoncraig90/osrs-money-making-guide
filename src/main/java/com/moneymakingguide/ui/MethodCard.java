package com.moneymakingguide.ui;

import com.moneymakingguide.data.MmgMethod;
import com.moneymakingguide.service.ProfitCalculator;
import com.moneymakingguide.service.RequirementService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

/** One money maker in the list. Clicking it opens the wiki guide. */
class MethodCard extends JPanel
{
	private static final Color PROFIT = new Color(76, 175, 80);
	private static final Color LOSS = new Color(220, 90, 90);
	private static final Color WARNING = new Color(220, 170, 60);
	private static final Color BLOCKED = new Color(150, 90, 90);

	MethodCard(MmgMethod method, ProfitCalculator.Result profit, RequirementService.Assessment assessment)
	{
		setLayout(new BorderLayout(0, 2));
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(6, 6, 6, 6)));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setCursor(new Cursor(Cursor.HAND_CURSOR));

		boolean blocked = !assessment.isEligible();

		JLabel name = new JLabel(method.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(blocked ? ColorScheme.LIGHT_GRAY_COLOR.darker() : Color.WHITE);
		name.setToolTipText(tooltip(method, profit, assessment));

		JLabel value = new JLabel(gp(profit.getProfitPerHour()) + (method.recurring ? "" : "/hr"));
		value.setFont(FontManager.getRunescapeSmallFont());
		value.setHorizontalAlignment(SwingConstants.RIGHT);
		value.setForeground(blocked
			? BLOCKED
			: (profit.getProfitPerHour() >= 0 ? PROFIT : LOSS));

		JPanel top = new JPanel(new BorderLayout(4, 0));
		top.setBackground(getBackground());
		top.add(name, BorderLayout.CENTER);
		top.add(value, BorderLayout.EAST);
		add(top, BorderLayout.NORTH);

		JPanel detail = new JPanel();
		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
		detail.setBackground(getBackground());

		String requirements = summarise(method);
		if (!requirements.isEmpty())
		{
			detail.add(sub(requirements, ColorScheme.LIGHT_GRAY_COLOR));
		}

		if (profit.getCostPerHour() > 0)
		{
			detail.add(sub("Needs " + gp(profit.getCostPerHour()) + " up front",
				ColorScheme.LIGHT_GRAY_COLOR));
		}

		for (String blocker : assessment.getBlockers())
		{
			detail.add(sub("✗ " + blocker, BLOCKED));
		}

		for (String warning : assessment.getWarnings())
		{
			detail.add(sub("! " + warning, WARNING));
		}

		add(detail, BorderLayout.CENTER);

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(method.wikiUrl());
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				top.setBackground(getBackground());
				detail.setBackground(getBackground());
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				setBackground(ColorScheme.DARKER_GRAY_COLOR);
				top.setBackground(getBackground());
				detail.setBackground(getBackground());
			}
		});
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	private JLabel sub(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	private static String summarise(MmgMethod method)
	{
		if (method.skillsText == null || method.skillsText.isEmpty())
		{
			return "No requirements";
		}
		return method.skillsText.replace('\n', ',' ).replaceAll(",\\s*", ", ");
	}

	private static String tooltip(MmgMethod method, ProfitCalculator.Result profit,
								  RequirementService.Assessment assessment)
	{
		StringBuilder sb = new StringBuilder("<html><body style='width:250px'>");
		sb.append("<b>").append(escape(method.activity == null ? method.name : method.activity)).append("</b><br>");

		if (profit.getKph() > 0)
		{
			sb.append("Assumes ").append(rate(profit.getKph())).append(" ")
				.append(escape(method.kphLabel == null ? "per hour" : method.kphLabel.toLowerCase()))
				.append("<br>");
		}

		sb.append("Inputs ").append(gp(profit.getCostPerHour()))
			.append(" &rarr; outputs ").append(gp(profit.getRevenuePerHour()))
			.append(" (after 2% GE tax)<br>");

		if (method.questsText != null && !method.questsText.isEmpty())
		{
			sb.append("<br>Quests: ").append(escape(method.questsText.replace("\n", ", ")));
		}

		if (!assessment.isEligible())
		{
			sb.append("<br><br>Hidden because: ")
				.append(escape(String.join("; ", assessment.getBlockers())));
		}

		return sb.append("<br><br><i>Click to open the wiki guide</i></body></html>").toString();
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String rate(double v)
	{
		return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}

	static String gp(double v)
	{
		double a = Math.abs(v);
		if (a >= 1_000_000_000D)
		{
			return String.format("%.2fb", v / 1_000_000_000D);
		}
		if (a >= 1_000_000D)
		{
			return String.format("%.2fm", v / 1_000_000D);
		}
		if (a >= 1_000D)
		{
			return String.format("%.0fk", v / 1_000D);
		}
		return String.format("%.0f", v);
	}
}
