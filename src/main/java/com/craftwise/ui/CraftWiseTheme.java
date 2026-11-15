package com.craftwise.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import net.runelite.client.ui.FontManager;

/**
 * Clean, crisp light-brown OSRS Crafting UI design tokens and Swing helpers for CraftWise.
 * Uses smooth sans-serif RuneLite UI typography, warm parchment/tan crafting surfaces,
 * saddle-brown/bronze highlights, high-contrast espresso text, and generous padding.
 */
public final class CraftWiseTheme
{
	// Core Light-Brown Crafting Surfaces (Parchment, Linen & Tanned Leather)
	public static final Color BG_MATTE_BLACK = new Color(0xEC, 0xE2, 0xD3);
	public static final Color BG_DEEP_CHARCOAL = new Color(0xE1, 0xD3, 0xBF);
	public static final Color BG_CARD = new Color(0xF8, 0xF3, 0xEB);
	public static final Color BG_CARD_ELEVATED = new Color(0xE6, 0xD9, 0xC6);
	public static final Color BG_CARD_HOVER = new Color(0xDB, 0xCA, 0xB2);
	public static final Color BG_INPUT = new Color(0xFC, 0xF9, 0xF4);

	// Warm Brown Hairline Borders
	public static final Color BORDER_SUBTLE = new Color(0xD3, 0xC4, 0xAF);
	public static final Color BORDER_DEFAULT = new Color(0xB5, 0xA1, 0x86);
	public static final Color BORDER_GOLD = new Color(0x9E, 0x6F, 0x35);
	public static final Color BORDER_GOLD_BRIGHT = new Color(0x82, 0x56, 0x22);

	// Saddle-Brown / Bronze Accents (crisp contrast on warm light surfaces)
	public static final Color ACCENT_GOLD = new Color(0x8B, 0x56, 0x1E);
	public static final Color ACCENT_GOLD_SOFT = new Color(0x72, 0x45, 0x16);
	public static final Color ACCENT_GOLD_DARK = new Color(0xE8, 0xD4, 0xB6);

	// Semantic Colours (tuned for crisp legibility on light-brown surfaces)
	public static final Color COLOR_PROFIT_GREEN = new Color(0x18, 0x73, 0x3E);
	public static final Color COLOR_LOSS_RED = new Color(0xB8, 0x26, 0x26);
	public static final Color COLOR_WARNING_YELLOW = new Color(0x9E, 0x56, 0x05);
	public static final Color COLOR_MISSING_GREY = new Color(0x6E, 0x61, 0x53);
	public static final Color COLOR_INFO_BLUE = new Color(0x1D, 0x5F, 0x9E);

	// Warm Espresso Typography Colours
	public static final Color TEXT_PRIMARY = new Color(0x26, 0x1C, 0x14);
	public static final Color TEXT_SECONDARY = new Color(0x45, 0x36, 0x28);
	public static final Color TEXT_MUTED = new Color(0x6B, 0x59, 0x47);
	public static final Color TEXT_LOCKED = new Color(0x9B, 0x8C, 0x7B);

	// Clean Card Borders (subtle rounded-neutral dark cards with comfortable padding)
	public static final Border CARD_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
		BorderFactory.createEmptyBorder(7, 8, 7, 8)
	);

	public static final Border CARD_BORDER_GOLD = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(BORDER_GOLD, 1),
		BorderFactory.createEmptyBorder(7, 8, 7, 8)
	);

	public static final Border INPUT_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
		BorderFactory.createEmptyBorder(3, 6, 3, 6)
	);

	private CraftWiseTheme()
	{
	}

	public static Font getTitleFont()
	{
		try
		{
			return FontManager.getDefaultBoldFont().deriveFont(13.5f);
		}
		catch (Throwable t)
		{
			return new Font("SansSerif", Font.BOLD, 13);
		}
	}

	public static Font getSectionFont()
	{
		try
		{
			return FontManager.getDefaultBoldFont().deriveFont(12.0f);
		}
		catch (Throwable t)
		{
			return new Font("SansSerif", Font.BOLD, 12);
		}
	}

	public static Font getBoldFont()
	{
		try
		{
			return FontManager.getDefaultBoldFont().deriveFont(11.5f);
		}
		catch (Throwable t)
		{
			return new Font("SansSerif", Font.BOLD, 11);
		}
	}

	public static Font getRegularFont()
	{
		try
		{
			return FontManager.getDefaultFont().deriveFont(11.5f);
		}
		catch (Throwable t)
		{
			return new Font("SansSerif", Font.PLAIN, 11);
		}
	}

	public static Font getSmallFont()
	{
		try
		{
			return FontManager.getDefaultFont().deriveFont(11.0f);
		}
		catch (Throwable t)
		{
			return new Font("SansSerif", Font.PLAIN, 11);
		}
	}

	public static Font getSmallBoldFont()
	{
		try
		{
			return FontManager.getDefaultBoldFont().deriveFont(11.0f);
		}
		catch (Throwable t)
		{
			return new Font("SansSerif", Font.BOLD, 11);
		}
	}

	public static JPanel createCard()
	{
		JPanel card = new JPanel();
		card.setBackground(BG_CARD);
		card.setBorder(CARD_BORDER);
		return card;
	}

	public static JPanel createGoldCard()
	{
		JPanel card = new JPanel();
		card.setBackground(BG_CARD);
		card.setBorder(CARD_BORDER_GOLD);
		return card;
	}

	public static JLabel createSectionHeader(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(getSmallBoldFont());
		label.setForeground(TEXT_MUTED);
		label.setBorder(BorderFactory.createEmptyBorder(2, 2, 3, 2));
		return label;
	}

	public static void styleTextField(JTextField field)
	{
		field.setOpaque(true);
		field.setBackground(BG_INPUT);
		field.setForeground(TEXT_PRIMARY);
		field.setCaretColor(ACCENT_GOLD);
		field.setSelectionColor(BG_CARD_HOVER);
		field.setSelectedTextColor(TEXT_PRIMARY);
		field.setFont(getRegularFont());
		field.setBorder(INPUT_BORDER);
		field.setMinimumSize(new Dimension(0, 24));
		field.setPreferredSize(new Dimension(80, 24));
	}

	public static <T> void styleComboBox(JComboBox<T> combo)
	{
		combo.setOpaque(true);
		combo.setBackground(BG_INPUT);
		combo.setForeground(TEXT_PRIMARY);
		combo.setFont(getSmallFont());
		combo.setBorder(BorderFactory.createLineBorder(BORDER_DEFAULT, 1));
		combo.setMinimumSize(new Dimension(0, 24));
		combo.setPreferredSize(new Dimension(110, 24));
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		ListCellRenderer<? super T> existing = combo.getRenderer();
		if (!(existing instanceof StyledComboRenderer))
		{
			combo.setRenderer(new StyledComboRenderer<>(existing));
		}
	}

	private static final class StyledComboRenderer<T> implements ListCellRenderer<T>
	{
		private final ListCellRenderer<? super T> delegate;
		private final DefaultListCellRenderer fallback = new DefaultListCellRenderer();

		private StyledComboRenderer(ListCellRenderer<? super T> delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public Component getListCellRendererComponent(
			JList<? extends T> list, T value, int index, boolean isSelected, boolean cellHasFocus)
		{
			Component comp;
			if (delegate != null)
			{
				comp = delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			}
			else
			{
				comp = fallback.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			}
			if (comp instanceof JComponent)
			{
				JComponent jc = (JComponent) comp;
				jc.setOpaque(true);
				jc.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
			}
			comp.setFont(getSmallFont());
			comp.setBackground(isSelected ? BG_CARD_HOVER : BG_INPUT);
			comp.setForeground(TEXT_PRIMARY);
			return comp;
		}
	}

	public static void styleSpinner(JSpinner spinner)
	{
		spinner.setFont(getRegularFont());
		spinner.setBorder(BorderFactory.createLineBorder(BORDER_DEFAULT, 1));
		spinner.setMinimumSize(new Dimension(48, 24));
		spinner.setPreferredSize(new Dimension(60, 24));
		if (spinner.getEditor() instanceof JSpinner.DefaultEditor)
		{
			JSpinner.DefaultEditor ed = (JSpinner.DefaultEditor) spinner.getEditor();
			ed.getTextField().setOpaque(true);
			ed.getTextField().setBackground(BG_INPUT);
			ed.getTextField().setForeground(TEXT_PRIMARY);
			ed.getTextField().setCaretColor(ACCENT_GOLD);
			ed.getTextField().setSelectionColor(BG_CARD_HOVER);
			ed.getTextField().setSelectedTextColor(TEXT_PRIMARY);
			ed.getTextField().setColumns(3);
		}
	}

	public static void styleTableHeader(JTableHeader header)
	{
		header.setOpaque(true);
		header.setBackground(BG_DEEP_CHARCOAL);
		header.setForeground(TEXT_SECONDARY);
		header.setFont(getSmallBoldFont());
		header.setReorderingAllowed(false);
		header.setDefaultRenderer(new DefaultTableCellRenderer()
		{
			@Override
			public Component getTableCellRendererComponent(
				JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
			{
				JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, false, false, row, column);
				lbl.setOpaque(true);
				lbl.setBackground(BG_DEEP_CHARCOAL);
				lbl.setForeground(TEXT_SECONDARY);
				lbl.setFont(getSmallBoldFont());
				lbl.setHorizontalAlignment(SwingConstants.LEFT);
				lbl.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_DEFAULT),
					BorderFactory.createEmptyBorder(2, 4, 2, 4)
				));
				return lbl;
			}
		});
	}

	/**
	 * Creates a clean key/value row where the key sits in CENTER (truncates smoothly if narrow)
	 * and the value sits in EAST so numbers never clip.
	 */
	public static JPanel kvRow(String key, String value, Color valColor)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 19));
		JLabel k = new JLabel(key);
		k.setFont(getSmallFont());
		k.setForeground(TEXT_MUTED);
		k.setMinimumSize(new Dimension(0, 16));
		k.setToolTipText(key + ": " + value);
		JLabel v = new JLabel(value);
		v.setFont(getSmallBoldFont());
		v.setForeground(valColor);
		v.setHorizontalAlignment(JLabel.RIGHT);
		row.add(k, BorderLayout.CENTER);
		row.add(v, BorderLayout.EAST);
		return row;
	}

	public static JPanel stackedField(String label, Component comp)
	{
		JPanel p = new JPanel(new BorderLayout(0, 2));
		p.setOpaque(false);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
		JLabel l = new JLabel(label);
		l.setFont(getSmallFont());
		l.setForeground(TEXT_MUTED);
		p.add(l, BorderLayout.NORTH);
		p.add(comp, BorderLayout.CENTER);
		return p;
	}

	public static void styleScrollPane(JScrollPane scrollPane)
	{
		scrollPane.setBackground(BG_MATTE_BLACK);
		scrollPane.getViewport().setBackground(BG_MATTE_BLACK);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(7, 0));
		scrollPane.getVerticalScrollBar().setUI(new DarkScrollBarUI());
		scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 7));
		scrollPane.getHorizontalScrollBar().setUI(new DarkScrollBarUI());
	}

	/**
	 * Creates a crisp 16x16 toolbar icon featuring a faceted diamond paired with a hammer and chisel.
	 */
	public static BufferedImage createToolbarIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// 1. Cut diamond (right-center hero gem, x=5..15, y=1..12)
		int[] dXs = {8, 12, 15, 10, 5};
		int[] dYs = {1, 1, 5, 12, 5};
		g.setColor(new Color(0xB8, 0xE6, 0xFC));
		g.fillPolygon(dXs, dYs, 5);

		// Crown highlight trapezoid
		g.setColor(new Color(0xF2, 0xFB, 0xFF));
		g.fillPolygon(new int[]{8, 12, 13, 7}, new int[]{1, 1, 5, 5}, 4);

		// Right-side facet depth shading
		g.setColor(new Color(0x63, 0xB4, 0xDF));
		g.fillPolygon(new int[]{13, 15, 10}, new int[]{5, 5, 12}, 3);

		// Internal facet cut lines & sapphire outline
		g.setStroke(new BasicStroke(1.0f));
		g.setColor(new Color(0x3E, 0x8B, 0xBE));
		g.drawLine(5, 5, 15, 5);
		g.drawLine(7, 5, 10, 12);
		g.drawLine(13, 5, 10, 12);
		g.setColor(new Color(0x1B, 0x4A, 0x6E));
		g.drawPolygon(dXs, dYs, 5);

		// 2. Hammer (top-left, angled toward center)
		g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0x5A, 0x34, 0x16));
		g.drawLine(2, 3, 6, 7);
		g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0xBC, 0x7A, 0x3E));
		g.drawLine(2, 3, 6, 7);

		int[] hXs = {0, 3, 5, 2};
		int[] hYs = {3, 0, 2, 5};
		g.setColor(new Color(0xDF, 0xE6, 0xEF));
		g.fillPolygon(hXs, hYs, 4);
		g.setStroke(new BasicStroke(0.9f));
		g.setColor(new Color(0x28, 0x30, 0x3C));
		g.drawPolygon(hXs, hYs, 4);

		// 3. Chisel (bottom-left, steel blade aiming cleanly at the diamond)
		g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0x5A, 0x34, 0x16));
		g.drawLine(1, 14, 4, 11);
		g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0xCC, 0x86, 0x3B));
		g.drawLine(1, 14, 4, 11);

		// Brass ferrule + bevelled steel blade
		g.setColor(new Color(0xD8, 0xA8, 0x38));
		g.drawLine(4, 11, 5, 10);
		g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		g.setColor(new Color(0x28, 0x30, 0x3C));
		g.drawLine(5, 10, 8, 7);
		g.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
		g.setColor(new Color(0xEC, 0xF2, 0xF8));
		g.drawLine(5, 10, 8, 7);

		g.dispose();
		return img;
	}

	/**
	 * Crisp light button or clean flat underline tab.
	 */
	public static class CraftButton extends JButton
	{
		private boolean active = false;
		private boolean hovered = false;
		private boolean tabStyle = false;
		private final boolean primary;

		public CraftButton(String text)
		{
			this(text, false);
		}

		public CraftButton(String text, boolean primary)
		{
			super(text);
			this.primary = primary;
			setFont(getSmallBoldFont());
			setFocusPainted(false);
			setBorderPainted(false);
			setContentAreaFilled(false);
			setOpaque(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setMargin(new Insets(4, 6, 4, 6));
			setMinimumSize(new Dimension(0, 24));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hovered = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hovered = false;
					repaint();
				}
			});
		}

		public void setTabStyle(boolean tabStyle)
		{
			this.tabStyle = tabStyle;
			repaint();
		}

		public void setActive(boolean active)
		{
			this.active = active;
			repaint();
		}

		public boolean isActive()
		{
			return active;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

			int w = getWidth();
			int h = getHeight();

			if (tabStyle)
			{
				g2.setColor(hovered ? BG_CARD_ELEVATED : (active ? BG_CARD : BG_DEEP_CHARCOAL));
				g2.fillRect(0, 0, w, h);
				g2.setColor(BORDER_SUBTLE);
				g2.drawLine(0, h - 1, w, h - 1);
				if (active)
				{
					g2.setColor(ACCENT_GOLD);
					g2.fillRect(0, h - 2, w, 2);
				}
			}
			else
			{
				Color bg;
				Color border;
				if (!isEnabled())
				{
					bg = BG_DEEP_CHARCOAL;
					border = BORDER_SUBTLE;
				}
				else if (active || primary)
				{
					bg = hovered ? new Color(0xDD, 0xC4, 0xA0) : ACCENT_GOLD_DARK;
					border = BORDER_GOLD;
				}
				else
				{
					bg = hovered ? BG_CARD_HOVER : BG_CARD;
					border = hovered ? BORDER_DEFAULT : BORDER_SUBTLE;
				}
				g2.setColor(bg);
				g2.fillRoundRect(0, 0, w - 1, h - 1, 4, 4);
				g2.setColor(border);
				g2.drawRoundRect(0, 0, w - 1, h - 1, 4, 4);
			}

			Color fg = !isEnabled()
				? TEXT_LOCKED
				: (active ? TEXT_PRIMARY : (primary ? ACCENT_GOLD_SOFT : (hovered ? TEXT_PRIMARY : TEXT_SECONDARY)));

			g2.setFont(getFont());
			FontMetrics fm = g2.getFontMetrics();
			String txt = getText() != null ? getText() : "";
			int textW = fm.stringWidth(txt);
			int x = Math.max(3, (w - textW) / 2);
			int y = (h + fm.getAscent() - fm.getDescent()) / 2;
			g2.setColor(fg);
			g2.drawString(txt, x, y);
			g2.dispose();
		}
	}

	public static class ScrollableWidthPanel extends JPanel implements Scrollable
	{
		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private static class DarkScrollBarUI extends BasicScrollBarUI
	{
		@Override
		protected void configureScrollBarColors()
		{
			thumbColor = BORDER_DEFAULT;
			thumbDarkShadowColor = BORDER_SUBTLE;
			thumbHighlightColor = BORDER_GOLD;
			trackColor = BG_MATTE_BLACK;
		}

		@Override
		protected JButton createDecreaseButton(int orientation)
		{
			return zeroButton();
		}

		@Override
		protected JButton createIncreaseButton(int orientation)
		{
			return zeroButton();
		}

		private JButton zeroButton()
		{
			JButton btn = new JButton();
			btn.setPreferredSize(new Dimension(0, 0));
			btn.setMinimumSize(new Dimension(0, 0));
			btn.setMaximumSize(new Dimension(0, 0));
			return btn;
		}
	}
}
