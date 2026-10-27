package com.vortex.engine.desktop;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/**
 * DesktopUiKit — Unified Dark Obsidian + Electric Cyan Custom Swing UI Engine.
 *
 * Eliminates Swing's default Metal blue/gray 3D look-and-feel completely, providing
 * custom-painted dropdowns, animated tab strips, smooth 60 FPS hover/focus transitions,
 * custom toggle switches, sleek thin scrollbars, hairline splitters, and dark modals.
 */
public final class DesktopUiKit {

    // Unified Vortex Obsidian + Electric Cyan Theme (matches Android Mobile 1:1!)
    public static final Color C_VOID         = new Color(9, 13, 22);     // #090D16
    public static final Color C_HEADER       = new Color(13, 19, 32);    // #0D1320
    public static final Color C_PANEL        = new Color(15, 23, 38);    // #0F1726
    public static final Color C_SURFACE      = new Color(20, 30, 48);    // #141E30
    public static final Color C_SURFACE_HOV  = new Color(28, 42, 66);    // #1C2A42
    public static final Color C_INPUT_BG     = new Color(11, 16, 28);    // #0B101C
    public static final Color C_BORDER       = new Color(36, 51, 77);    // #24334D
    public static final Color C_CYAN         = new Color(0, 229, 255);   // #00E5FF
    public static final Color C_CYAN_HOV     = new Color(72, 238, 255);  // #48EEFF
    public static final Color C_CYAN_TINT    = new Color(13, 40, 56);    // #0D2838
    public static final Color C_CYAN_DARK_TX = new Color(4, 19, 26);     // #04131A
    public static final Color C_TEXT         = new Color(241, 245, 249); // #F1F5F9
    public static final Color C_TEXT_MUTED   = new Color(148, 163, 184); // #94A3B8
    public static final Color C_TEXT_DIM     = new Color(100, 116, 139); // #64748B
    public static final Color C_DANGER_BG    = new Color(31, 19, 28);    // #1F131C
    public static final Color C_DANGER_HOV   = new Color(52, 24, 38);    // #341826
    public static final Color C_DANGER_BDR   = new Color(244, 63, 94);   // #F43F5E
    public static final Color C_DANGER_TX    = new Color(253, 164, 175); // #FDA4AF
    public static final Color C_OK_TX        = new Color(52, 211, 153);  // #34D399

    public static final Font FONT_UI         = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_UI_BOLD    = new Font("SansSerif", Font.BOLD, 12);
    public static final Font FONT_TITLE      = new Font("SansSerif", Font.BOLD, 14);
    public static final Font FONT_MONO       = new Font("Monospaced", Font.PLAIN, 13);

    public enum Variant {
        PRIMARY,   // Solid Electric Cyan (#00E5FF) with dark bold label
        ACCENT,    // Tinted Cyan (#0D2838) + #00E5FF border & text
        SECONDARY, // Unified Obsidian (#141E30) + #24334D -> #00E5FF hover border
        DANGER     // Subtle dark rose surface for Stop / Delete
    }

    private DesktopUiKit() {}

    public static void installDarkDefaults() {
        UIManager.put("Panel.background", C_PANEL);
        UIManager.put("Label.foreground", C_TEXT);
        UIManager.put("Label.font", FONT_UI);
        UIManager.put("Viewport.background", C_VOID);
        UIManager.put("ScrollPane.background", C_VOID);
        UIManager.put("SplitPane.background", C_VOID);
        UIManager.put("PopupMenu.background", C_HEADER);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(C_CYAN, 1));
        UIManager.put("ToolTip.background", C_HEADER);
        UIManager.put("ToolTip.foreground", C_TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(C_CYAN, 1));
    }

    public static Color lerpColor(Color a, Color b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * k);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * k);
        return new Color(r, g, bl, al);
    }

    private static void enableAA(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // =========================================================================
    // 1. VortexButton — Custom 60 FPS Hover/Press Interpolated Button
    // =========================================================================
    public static final class VortexButton extends JButton {
        private Variant variant;
        private float hoverT = 0f;
        private boolean hovered = false;
        private boolean pressed = false;
        private final Timer animTimer;

        public VortexButton(String text, Variant variant) {
            super(text);
            this.variant = variant;
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(FONT_UI_BOLD);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(6, 12, 6, 12));

            animTimer = new Timer(16, e -> {
                float target = hovered ? 1f : 0f;
                if (Math.abs(hoverT - target) < 0.04f) {
                    hoverT = target;
                    ((Timer) e.getSource()).stop();
                } else {
                    hoverT += (target - hoverT) * 0.28f;
                }
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    if (!animTimer.isRunning()) animTimer.start();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    pressed = false;
                    if (!animTimer.isRunning()) animTimer.start();
                }
                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        public void setVariant(Variant v) {
            this.variant = v;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            int w = fm.stringWidth(getText()) + 24;
            int h = Math.max(30, fm.getHeight() + 12);
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth();
            int h = getHeight();
            int yShift = pressed ? 1 : 0;

            Color fill, border, textCol;
            switch (variant) {
                case PRIMARY:
                    fill = lerpColor(C_CYAN, C_CYAN_HOV, hoverT);
                    border = fill;
                    textCol = C_CYAN_DARK_TX;
                    if (hoverT > 0.05f) {
                        g2.setColor(new Color(0, 229, 255, Math.round(55 * hoverT)));
                        g2.fill(new RoundRectangle2D.Float(0, 0, w, h, 10, 10));
                    }
                    break;
                case ACCENT:
                    fill = lerpColor(C_CYAN_TINT, new Color(19, 58, 80), hoverT);
                    border = C_CYAN;
                    textCol = C_CYAN;
                    break;
                case DANGER:
                    fill = lerpColor(C_DANGER_BG, C_DANGER_HOV, hoverT);
                    border = lerpColor(new Color(92, 36, 52), C_DANGER_BDR, hoverT);
                    textCol = C_DANGER_TX;
                    break;
                case SECONDARY:
                default:
                    fill = lerpColor(C_SURFACE, C_SURFACE_HOV, hoverT);
                    border = lerpColor(C_BORDER, C_CYAN, hoverT * 0.85f);
                    textCol = lerpColor(C_TEXT, Color.WHITE, hoverT);
                    break;
            }

            RoundRectangle2D rr = new RoundRectangle2D.Float(1f, 1f + yShift, w - 2f, h - 2f - yShift, 8f, 8f);
            g2.setColor(fill);
            g2.fill(rr);
            g2.setStroke(new BasicStroke(1.15f));
            g2.setColor(border);
            g2.draw(rr);

            g2.setFont(getFont());
            g2.setColor(isEnabled() ? textCol : C_TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() + yShift;
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }
    }

    // =========================================================================
    // 2. VortexDropdown — Custom Dark Dropdown with Animated Rotating Chevron
    // =========================================================================
    public static final class VortexDropdown extends JComponent {
        private final String[] items;
        private int selectedIndex = 0;
        private float hoverT = 0f;
        private float chevronAngle = 0f; // 0 -> 180 deg
        private boolean hovered = false;
        private boolean menuOpen = false;
        private BiConsumer<Integer, String> listener;
        private final Timer animTimer;

        public VortexDropdown(String[] options, String initial) {
            this.items = (options != null && options.length > 0) ? options.clone() : new String[]{"—"};
            for (int i = 0; i < this.items.length; i++) {
                if (this.items[i].equalsIgnoreCase(initial)) {
                    this.selectedIndex = i;
                    break;
                }
            }
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(170, 32));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            animTimer = new Timer(16, e -> {
                float targetHov = (hovered || menuOpen) ? 1f : 0f;
                float targetAng = menuOpen ? 180f : 0f;
                boolean done = true;
                if (Math.abs(hoverT - targetHov) > 0.03f) {
                    hoverT += (targetHov - hoverT) * 0.28f;
                    done = false;
                } else hoverT = targetHov;

                if (Math.abs(chevronAngle - targetAng) > 1.5f) {
                    chevronAngle += (targetAng - chevronAngle) * 0.30f;
                    done = false;
                } else chevronAngle = targetAng;

                repaint();
                if (done) ((Timer) e.getSource()).stop();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    if (!animTimer.isRunning()) animTimer.start();
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    if (!animTimer.isRunning()) animTimer.start();
                }
                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) showCustomPopup();
                }
            });
        }

        public int getSelectedIndex() { return selectedIndex; }
        public String getSelectedItem() { return items[selectedIndex]; }

        public void setSelectedItem(String val) {
            for (int i = 0; i < items.length; i++) {
                if (items[i].equalsIgnoreCase(val)) {
                    selectedIndex = i;
                    repaint();
                    return;
                }
            }
        }

        public void setSelectedIndex(int idx) {
            if (idx >= 0 && idx < items.length) {
                selectedIndex = idx;
                repaint();
            }
        }

        public void setOnSelect(BiConsumer<Integer, String> l) {
            this.listener = l;
        }

        private void showCustomPopup() {
            menuOpen = true;
            if (!animTimer.isRunning()) animTimer.start();

            JPopupMenu popup = new JPopupMenu();
            popup.setBackground(C_HEADER);
            popup.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(C_CYAN, 1),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));

            JPanel listPanel = new JPanel();
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            listPanel.setBackground(C_HEADER);

            int popW = Math.max(getWidth(), 210);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                final boolean isSel = (i == selectedIndex);
                JPanel row = new JPanel(new BorderLayout(8, 0)) {
                    boolean rowHov = false;
                    {
                        setOpaque(false);
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
                        addMouseListener(new MouseAdapter() {
                            @Override public void mouseEntered(MouseEvent e) { rowHov = true; repaint(); }
                            @Override public void mouseExited(MouseEvent e) { rowHov = false; repaint(); }
                            @Override public void mousePressed(MouseEvent e) {
                                boolean changed = (selectedIndex != idx);
                                selectedIndex = idx;
                                popup.setVisible(false);
                                repaint();
                                if (changed && listener != null) {
                                    listener.accept(idx, items[idx]);
                                }
                            }
                        });
                    }
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        enableAA(g2);
                        if (isSel || rowHov) {
                            g2.setColor(isSel ? C_CYAN_TINT : C_SURFACE_HOV);
                            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                        }
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
                JLabel lbl = new JLabel(items[i]);
                lbl.setFont(isSel ? FONT_UI_BOLD : FONT_UI);
                lbl.setForeground(isSel ? C_CYAN : C_TEXT);
                row.add(lbl, BorderLayout.CENTER);
                if (isSel) {
                    JLabel chk = new JLabel("✓");
                    chk.setFont(FONT_UI_BOLD);
                    chk.setForeground(C_CYAN);
                    row.add(chk, BorderLayout.EAST);
                }
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
                row.setPreferredSize(new Dimension(popW - 12, 30));
                listPanel.add(row);
            }

            if (items.length > 8) {
                JScrollPane sp = createScrollPane(listPanel);
                sp.setPreferredSize(new Dimension(popW, 256));
                popup.add(sp);
            } else {
                popup.add(listPanel);
            }

            popup.addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) {}
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                    menuOpen = false;
                    if (!animTimer.isRunning()) animTimer.start();
                }
                @Override public void popupMenuCanceled(PopupMenuEvent e) {
                    menuOpen = false;
                    if (!animTimer.isRunning()) animTimer.start();
                }
            });
            popup.show(this, 0, getHeight() + 3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth();
            int h = getHeight();

            RoundRectangle2D rr = new RoundRectangle2D.Float(1f, 1f, w - 2f, h - 2f, 8f, 8f);
            g2.setColor(lerpColor(C_SURFACE, C_SURFACE_HOV, hoverT));
            g2.fill(rr);
            g2.setStroke(new BasicStroke(menuOpen ? 1.4f : 1.1f));
            g2.setColor(lerpColor(C_BORDER, C_CYAN, hoverT));
            g2.draw(rr);

            // Selected value text
            g2.setFont(FONT_UI);
            g2.setColor(C_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            String txt = items[selectedIndex];
            int maxTextW = Math.max(20, w - 34);
            if (fm.stringWidth(txt) > maxTextW) {
                while (txt.length() > 3 && fm.stringWidth(txt + "…") > maxTextW) {
                    txt = txt.substring(0, txt.length() - 1);
                }
                txt += "…";
            }
            g2.drawString(txt, 11, (h - fm.getHeight()) / 2 + fm.getAscent());

            // Vector rotating chevron
            float cx = w - 15f;
            float cy = h * 0.5f;
            AffineTransform old = g2.getTransform();
            g2.translate(cx, cy);
            g2.rotate(Math.toRadians(chevronAngle));
            Path2D chev = new Path2D.Float();
            chev.moveTo(-4.2f, -2.0f);
            chev.lineTo(0f, 2.4f);
            chev.lineTo(4.2f, -2.0f);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(C_CYAN);
            g2.draw(chev);
            g2.setTransform(old);
            g2.dispose();
        }
    }

    // =========================================================================
    // 3. VortexToggle — Custom 60 FPS Animated Pill Switch (Replaces JCheckBox)
    // =========================================================================
    public static final class VortexToggle extends JComponent {
        private boolean selected;
        private float animT;
        private final String label;
        private Runnable onChange;
        private final Timer timer;

        public VortexToggle(String label, boolean initial) {
            this.label = label;
            this.selected = initial;
            this.animT = initial ? 1f : 0f;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

            timer = new Timer(16, e -> {
                float target = selected ? 1f : 0f;
                if (Math.abs(animT - target) < 0.04f) {
                    animT = target;
                    ((Timer) e.getSource()).stop();
                } else {
                    animT += (target - animT) * 0.30f;
                }
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    setSelected(!selected);
                    if (onChange != null) onChange.run();
                }
            });
        }

        public boolean isSelected() { return selected; }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            int tw = getFontMetrics(FONT_UI).stringWidth(label != null ? label : "");
            return new Dimension(tw + 56, 26);
        }

        public void setSelected(boolean s) {
            if (this.selected == s) return;
            this.selected = s;
            if (!timer.isRunning()) timer.start();
        }

        public void setOnChange(Runnable r) { this.onChange = r; }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth();
            int h = getHeight();

            g2.setFont(FONT_UI);
            g2.setColor(C_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(label, 2, (h - fm.getHeight()) / 2 + fm.getAscent());

            float swW = 36f, swH = 18f;
            float swX = w - swW - 2f;
            float swY = (h - swH) * 0.5f;

            RoundRectangle2D track = new RoundRectangle2D.Float(swX, swY, swW, swH, swH, swH);
            g2.setColor(lerpColor(C_SURFACE_HOV, C_CYAN, animT));
            g2.fill(track);
            g2.setColor(lerpColor(C_BORDER, C_CYAN, animT));
            g2.draw(track);

            float pad = 2.5f;
            float knobD = swH - pad * 2f;
            float kx = swX + pad + animT * (swW - pad * 2f - knobD);
            g2.setColor(animT > 0.5f ? C_CYAN_DARK_TX : C_TEXT);
            g2.fillOval(Math.round(kx), Math.round(swY + pad), Math.round(knobD), Math.round(knobD));
            g2.dispose();
        }
    }

    // =========================================================================
    // 4. VortexField — Custom Rounded Dark Input with Animated Cyan Focus Glow
    // =========================================================================
    public static JTextField createField(String initial) {
        JTextField tf = new JTextField(initial == null ? "" : initial) {
            float focusT = 0f;
            boolean focused = false;
            final Timer t = new Timer(16, e -> {
                float target = focused ? 1f : 0f;
                if (Math.abs(focusT - target) < 0.04f) {
                    focusT = target;
                    ((Timer) e.getSource()).stop();
                } else focusT += (target - focusT) * 0.30f;
                repaint();
            });
            {
                setOpaque(false);
                setForeground(C_TEXT);
                setCaretColor(C_CYAN);
                setSelectedTextColor(C_CYAN_DARK_TX);
                setSelectionColor(C_CYAN);
                setFont(FONT_UI);
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { focused = true; t.start(); }
                    @Override public void focusLost(FocusEvent e) { focused = false; t.start(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                enableAA(g2);
                RoundRectangle2D rr = new RoundRectangle2D.Float(1f, 1f, getWidth() - 2f, getHeight() - 2f, 7f, 7f);
                g2.setColor(C_INPUT_BG);
                g2.fill(rr);
                g2.setStroke(new BasicStroke(focused ? 1.4f : 1.0f));
                g2.setColor(lerpColor(C_BORDER, C_CYAN, focusT));
                g2.draw(rr);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        return tf;
    }

    // =========================================================================
    // 5. VortexTabStrip — 60 FPS Sliding-Pill Animated Workspace Tabs
    // =========================================================================
    public static final class VortexTabStrip extends JComponent {
        private final String[] keys;
        private final String[] labels;
        private int activeIdx = 0;
        private int hoverIdx = -1;
        private float indicatorX = 0f;
        private float indicatorW = 70f;
        private boolean initializedPill = false;
        private BiConsumer<Integer, String> onTabSelected;
        private final Timer slideTimer;

        public VortexTabStrip(String[] keys, String[] labels) {
            this.keys = keys;
            this.labels = labels;
            setOpaque(true);
            setBackground(C_HEADER);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(480, 38));

            slideTimer = new Timer(16, e -> {
                Rectangle target = getTabBounds(activeIdx);
                if (target.width <= 0) return;
                boolean done = true;
                if (Math.abs(indicatorX - target.x) > 0.8f) {
                    indicatorX += (target.x - indicatorX) * 0.32f;
                    done = false;
                } else indicatorX = target.x;

                if (Math.abs(indicatorW - target.width) > 0.8f) {
                    indicatorW += (target.width - indicatorW) * 0.32f;
                    done = false;
                } else indicatorW = target.width;

                repaint();
                if (done) ((Timer) e.getSource()).stop();
            });

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = tabAt(e.getPoint());
                    if (idx != hoverIdx) {
                        hoverIdx = idx;
                        repaint();
                    }
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    hoverIdx = -1;
                    repaint();
                }
                @Override
                public void mousePressed(MouseEvent e) {
                    int idx = tabAt(e.getPoint());
                    if (idx >= 0) selectTab(idx, true);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        public void setOnTabSelected(BiConsumer<Integer, String> cb) {
            this.onTabSelected = cb;
        }

        public void selectTabByKey(String key, boolean fire) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equalsIgnoreCase(key)) {
                    selectTab(i, fire);
                    return;
                }
            }
        }

        public void selectTab(int idx, boolean fire) {
            if (idx < 0 || idx >= keys.length) return;
            this.activeIdx = idx;
            if (!slideTimer.isRunning()) slideTimer.start();
            repaint();
            if (fire && onTabSelected != null) {
                onTabSelected.accept(idx, keys[idx]);
            }
        }

        private Rectangle getTabBounds(int idx) {
            int n = labels.length;
            if (n == 0 || getWidth() <= 0) return new Rectangle(0, 0, 0, 0);
            int pad = 6;
            int totalW = getWidth() - pad * 2;
            int tabW = totalW / n;
            return new Rectangle(pad + idx * tabW, 5, tabW - 3, getHeight() - 10);
        }

        private int tabAt(Point p) {
            for (int i = 0; i < labels.length; i++) {
                if (getTabBounds(i).contains(p)) return i;
            }
            return -1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(C_HEADER);
            g2.fillRect(0, 0, w, h);
            g2.setColor(C_BORDER);
            g2.drawLine(0, h - 1, w, h - 1);

            Rectangle cur = getTabBounds(activeIdx);
            if (!initializedPill && cur.width > 0) {
                indicatorX = cur.x;
                indicatorW = cur.width;
                initializedPill = true;
            }

            // Hover subtle preview
            if (hoverIdx >= 0 && hoverIdx != activeIdx) {
                Rectangle hr = getTabBounds(hoverIdx);
                g2.setColor(C_SURFACE);
                g2.fill(new RoundRectangle2D.Float(hr.x, hr.y, hr.width, hr.height, 7, 7));
            }

            // Sliding Cyan Active Pill + Bottom Neon Indicator
            if (indicatorW > 0) {
                RoundRectangle2D pill = new RoundRectangle2D.Float(indicatorX, 5f, indicatorW, h - 10f, 7f, 7f);
                g2.setColor(C_CYAN_TINT);
                g2.fill(pill);
                g2.setStroke(new BasicStroke(1.15f));
                g2.setColor(C_CYAN);
                g2.draw(pill);
                g2.fill(new RoundRectangle2D.Float(indicatorX + 8f, h - 3f, Math.max(8f, indicatorW - 16f), 2.5f, 2f, 2f));
            }

            g2.setFont(FONT_UI_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < labels.length; i++) {
                Rectangle r = getTabBounds(i);
                g2.setColor(i == activeIdx ? C_CYAN : (i == hoverIdx ? C_TEXT : C_TEXT_MUTED));
                String lbl = labels[i];
                int tx = r.x + (r.width - fm.stringWidth(lbl)) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(lbl, tx, ty);
            }
            g2.dispose();
        }
    }

    // =========================================================================
    // 6. Sleek Thin Dark ScrollPane & Hairline SplitPane (Zero Metal Blue!)
    // =========================================================================
    public static JScrollPane createScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(C_VOID);
        sp.setBackground(C_VOID);
        styleScrollBar(sp.getVerticalScrollBar());
        styleScrollBar(sp.getHorizontalScrollBar());
        return sp;
    }

    public static void styleScrollBar(JScrollBar sb) {
        if (sb == null) return;
        sb.setOpaque(true);
        sb.setBackground(C_VOID);
        sb.setPreferredSize(new Dimension(7, 7));
        sb.setUnitIncrement(16);
        sb.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = C_BORDER;
                this.trackColor = C_VOID;
            }
            @Override
            protected JButton createDecreaseButton(int orientation) { return zeroBtn(); }
            @Override
            protected JButton createIncreaseButton(int orientation) { return zeroBtn(); }
            private JButton zeroBtn() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
                g.setColor(C_VOID);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                if (r.isEmpty() || !scrollbar.isEnabled()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                enableAA(g2);
                g2.setColor(isDragging || isThumbRollover() ? C_CYAN : C_BORDER);
                g2.fill(new RoundRectangle2D.Float(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6));
                g2.dispose();
            }
        });
    }

    public static JSplitPane createSplitPane(int orientation, Component a, Component b, int dividerLoc) {
        JSplitPane sp = new JSplitPane(orientation, a, b);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setDividerSize(4);
        sp.setDividerLocation(dividerLoc);
        sp.setContinuousLayout(true);
        sp.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(Graphics g) {
                        g.setColor(C_BORDER);
                        g.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        return sp;
    }

    // =========================================================================
    // 7. Custom Dark Popup Menu & Custom Dark Modal Dialog (Zero JOptionPane!)
    // =========================================================================
    public static final class MenuAction {
        public final String label;
        public final Runnable action;
        public MenuAction(String label, Runnable action) {
            this.label = label;
            this.action = action;
        }
    }

    public static void showPopupMenu(JComponent invoker, List<MenuAction> actions) {
        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(C_HEADER);
        popup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_CYAN, 1),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(C_HEADER);

        for (MenuAction ma : actions) {
            if ("---".equals(ma.label)) {
                JPanel sep = new JPanel();
                sep.setBackground(C_BORDER);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                sep.setPreferredSize(new Dimension(220, 1));
                box.add(Box.createVerticalStrut(3));
                box.add(sep);
                box.add(Box.createVerticalStrut(3));
                continue;
            }
            JPanel row = new JPanel(new BorderLayout()) {
                boolean hov = false;
                {
                    setOpaque(false);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 14));
                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { hov = true; repaint(); }
                        @Override public void mouseExited(MouseEvent e) { hov = false; repaint(); }
                        @Override public void mousePressed(MouseEvent e) {
                            popup.setVisible(false);
                            if (ma.action != null) ma.action.run();
                        }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    if (hov) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        enableAA(g2);
                        g2.setColor(C_CYAN_TINT);
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                        g2.setColor(C_CYAN);
                        g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 6, 6));
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            JLabel lbl = new JLabel(ma.label);
            lbl.setFont(FONT_UI);
            lbl.setForeground(C_TEXT);
            row.add(lbl, BorderLayout.CENTER);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
            box.add(row);
        }
        popup.add(box);
        popup.show(invoker, 0, invoker.getHeight() + 4);
    }

    public static void showInfoModal(JFrame owner, String title, String message) {
        JDialog dlg = new JDialog(owner, title, true);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_CYAN, 1),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));

        JLabel head = new JLabel(title);
        head.setFont(FONT_TITLE);
        head.setForeground(C_CYAN);
        root.add(head, BorderLayout.NORTH);

        JLabel msg = new JLabel("<html><body style='width:340px;color:#E2E8F0;font-family:sans-serif;'>"
                + message.replace("\n", "<br>") + "</body></html>");
        root.add(msg, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        VortexButton ok = new VortexButton("OK", Variant.PRIMARY);
        ok.addActionListener(e -> dlg.dispose());
        foot.add(ok);
        root.add(foot, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
    }

    public static DefaultListCellRenderer createDarkListRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                l.setFont(isSelected ? FONT_UI_BOLD : FONT_UI);
                l.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                if (isSelected) {
                    l.setBackground(C_CYAN_TINT);
                    l.setForeground(C_CYAN);
                } else {
                    l.setBackground(index % 2 == 0 ? C_VOID : new Color(12, 18, 30));
                    l.setForeground(C_TEXT);
                }
                return l;
            }
        };
    }
}
