package com.vortex.engine.desktop;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
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
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * DesktopUiKit v4.2 — Pure Monochrome Dark + Crisp White Custom Swing UI Engine (ZERO BLUE!).
 *
 * Provides:
 *  - Pure neutral dark carbon surfaces (#09090B / #111113 / #161619 / #1E1E22) + Crisp White (#FFFFFF) accents
 *  - Custom anti-aliased vector icons (Icon enum) so buttons & tabs never depend on OS emoji fonts
 *  - Smooth 60 FPS animations on buttons, dropdown chevrons, sliding tab pills, pill switches, sliders & card transitions
 *  - Draggable 8px grip-handle splitters (properly honoring minimum sizes so sidebars resize smoothly!)
 *  - Custom dark File Browser Modal & Popup Menus (zero Swing Metal blue/white dialogs!)
 */
public final class DesktopUiKit {

    // High-Contrast 5-Tier Neutral Dark + Crisp White Studio Palette (ZERO BLUE!)
    public static final Color C_VOID          = new Color(7, 7, 9);       // #070709 Deep recessed canvas / code / console well
    public static final Color C_INPUT_BG      = new Color(11, 11, 15);    // #0B0B0F Recessed numeric / text input well
    public static final Color C_PANEL         = new Color(21, 21, 26);    // #15151A Primary dock surface (Hierarchy / Explorer)
    public static final Color C_PANEL_ALT     = new Color(28, 28, 35);    // #1C1C23 Alternating card / Inspector accordion surface
    public static final Color C_HEADER        = new Color(33, 33, 41);    // #212129 Elevated dock header bar
    public static final Color C_SURFACE       = new Color(42, 42, 52);    // #2A2A34 Interactive controls & active tab surface
    public static final Color C_SURFACE_HOV   = new Color(56, 56, 69);    // #383845 Hover state highlight
    public static final Color C_BORDER_SUBTLE = new Color(46, 46, 57);    // #2E2E39 Subtle inner card divider
    public static final Color C_BORDER        = new Color(64, 64, 79);    // #40404F Crisp structural panel separation border
    public static final Color C_BORDER_STRONG = new Color(102, 102, 122); // #66667A Active splitter / input focus border
    public static final Color C_WHITE         = new Color(255, 255, 255); // #FFFFFF Pure crisp accent white
    public static final Color C_WHITE_SOFT    = new Color(240, 240, 245); // #F0F0F5
    public static final Color C_ACCENT_TINT   = new Color(48, 48, 59);    // #30303B Selected item / active pill fill
    public static final Color C_DARK_INK      = new Color(9, 9, 11);      // #09090B
    public static final Color C_TEXT          = new Color(250, 250, 252); // #FAFAFC Primary readable text
    public static final Color C_TEXT_LABEL    = new Color(226, 226, 235); // #E2E2EB High-contrast Inspector property labels
    public static final Color C_TEXT_MUTED    = new Color(184, 184, 198); // #B8B8C6 Readable metadata & secondary labels (no eye strain)

    // Backwards-compatible aliases mapped strictly to Crisp White / Neutral Dark (ZERO BLUE!)
    public static final Color C_CYAN         = C_WHITE;
    public static final Color C_CYAN_HOV     = C_WHITE;
    public static final Color C_CYAN_TINT    = C_ACCENT_TINT;
    public static final Color C_CYAN_DARK_TX = C_DARK_INK;

    // Semantic Build / Script / Console Feedback Palette (Soft Red Errors + Bright Green Successes + Warm Amber)
    public static final Color C_DANGER_BG    = new Color(48, 20, 25);
    public static final Color C_DANGER_HOV   = new Color(66, 27, 34);
    public static final Color C_DANGER_BD    = new Color(156, 56, 68);
    public static final Color C_DANGER_TX    = new Color(255, 107, 107);  // #FF6B6B Soft coral red for errors
    public static final Color C_OK_BG        = new Color(12, 42, 27);     // #0C2A1B Subtle emerald confirmation tint
    public static final Color C_OK_BD        = new Color(22, 163, 74);    // #16A34A Emerald confirmation border
    public static final Color C_OK_TX        = new Color(0, 230, 118);    // #00E676 Bright emerald green for successful builds
    public static final Color C_WARN_BG      = new Color(45, 34, 12);
    public static final Color C_WARN_TX      = new Color(251, 191, 36);   // #FBBF24 Warm amber warnings

    // Crisp Native ClearType / FreeType Font Resolution & Readable Default Scale (100% — NEVER 10pt!)
    public static final String UI_FAMILY   = pickInstalledFont(
            "Inter", "Segoe UI", "Ubuntu", "Cantarell", "Liberation Sans", "DejaVu Sans", "SansSerif");
    public static final String MONO_FAMILY = pickInstalledFont(
            "JetBrains Mono", "Cascadia Mono", "Consolas", "Liberation Mono", "DejaVu Sans Mono", "Monospaced");

    private static float uiScale = 1.00f;
    private static int baseUiPt = 14;
    private static int baseMonoPt = 14;

    public static Font FONT_SMALL      = new Font(UI_FAMILY, Font.PLAIN, scaledFontPt(12));
    public static Font FONT_SMALL_BOLD = new Font(UI_FAMILY, Font.BOLD, scaledFontPt(12));
    public static Font FONT_UI         = new Font(UI_FAMILY, Font.PLAIN, scaledFontPt(baseUiPt));
    public static Font FONT_UI_BOLD    = new Font(UI_FAMILY, Font.BOLD, scaledFontPt(baseUiPt));
    public static Font FONT_TITLE      = new Font(UI_FAMILY, Font.BOLD, scaledFontPt(baseUiPt + 1));
    public static Font FONT_MONO       = new Font(MONO_FAMILY, Font.PLAIN, scaledFontPt(baseMonoPt));

    private static String pickInstalledFont(String... candidates) {
        try {
            String[] avail = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            if (avail != null) {
                java.util.Set<String> lower = new java.util.HashSet<>();
                Map<String, String> exact = new HashMap<>();
                for (String f : avail) {
                    String k = f.toLowerCase(Locale.US);
                    lower.add(k);
                    exact.put(k, f);
                }
                for (String c : candidates) {
                    String k = c.toLowerCase(Locale.US);
                    if (lower.contains(k)) return exact.get(k);
                }
            }
        } catch (Throwable ignored) {}
        return candidates[candidates.length - 1];
    }

    public static float detectOptimalLaptopScale() {
        return 1.00f;
    }

    public static float getUiScale() { return uiScale; }
    public static int getBaseFontPt() { return scaledFontPt(baseUiPt); }

    public static int scaled(int px) {
        return Math.max(1, Math.round(px * uiScale));
    }

    public static int scaledFontPt(int basePt) {
        return Math.max(12, Math.round(basePt * uiScale));
    }

    public static void setUiScale(float scale) {
        uiScale = Math.max(0.92f, Math.min(1.35f, scale));
        rebuildFonts();
    }

    public static void adjustFontPt(int delta) {
        baseUiPt = Math.max(12, Math.min(18, baseUiPt + delta));
        baseMonoPt = Math.max(12, Math.min(19, baseMonoPt + delta));
        rebuildFonts();
    }

    private static void rebuildFonts() {
        FONT_SMALL      = new Font(UI_FAMILY, Font.PLAIN, Math.max(11, scaledFontPt(baseUiPt - 1)));
        FONT_SMALL_BOLD = new Font(UI_FAMILY, Font.BOLD, Math.max(11, scaledFontPt(baseUiPt - 1)));
        FONT_UI         = new Font(UI_FAMILY, Font.PLAIN, scaledFontPt(baseUiPt));
        FONT_UI_BOLD    = new Font(UI_FAMILY, Font.BOLD, scaledFontPt(baseUiPt));
        FONT_TITLE      = new Font(UI_FAMILY, Font.BOLD, scaledFontPt(baseUiPt + 1));
        FONT_MONO       = new Font(MONO_FAMILY, Font.PLAIN, scaledFontPt(baseMonoPt));
        UIManager.put("Label.font", FONT_UI);
        UIManager.put("Button.font", FONT_UI);
        UIManager.put("TextField.font", FONT_UI);
        UIManager.put("TextArea.font", FONT_MONO);
        UIManager.put("List.font", FONT_UI);
        UIManager.put("ToolTip.font", FONT_SMALL);
    }

    public enum Variant { PRIMARY, ACCENT, SECONDARY, DANGER }

    public enum Icon {
        NONE, PLAY, STOP, PLUS, FOLDER, SAVE, UNDO, REDO, CUBE,
        CODE, FLOW, SPARKLE, SYNC, GRID, WAVE, EXPORT, TERMINAL, CHECK, CLOSE, REFRESH,
        FILE, SPLIT, NODES, SEND, GUI,
        COLLAPSE_LEFT, COLLAPSE_RIGHT, COLLAPSE_DOWN, COLLAPSE_UP, UNDOCK, MAXIMIZE,
        MORE_VERT, MOVE, SCALE_GIZMO, EYE
    }

    private DesktopUiKit() {}

    public static void installDarkDefaults() {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}
        rebuildFonts();
        UIManager.put("Panel.background", C_VOID);
        UIManager.put("SplitPane.background", new Color(25, 25, 32));
        UIManager.put("Viewport.background", C_VOID);
        UIManager.put("ScrollPane.background", C_VOID);
        UIManager.put("List.background", C_VOID);
        UIManager.put("List.foreground", C_TEXT);
        UIManager.put("List.selectionBackground", C_ACCENT_TINT);
        UIManager.put("List.selectionForeground", C_WHITE);
        UIManager.put("TextArea.background", C_VOID);
        UIManager.put("TextArea.foreground", C_TEXT);
        UIManager.put("TextPane.background", C_VOID);
        UIManager.put("TextPane.foreground", C_TEXT);
        UIManager.put("TextField.background", C_INPUT_BG);
        UIManager.put("TextField.foreground", C_WHITE);
        UIManager.put("Label.foreground", C_TEXT);
        UIManager.put("ToolTip.background", C_SURFACE);
        UIManager.put("ToolTip.foreground", C_WHITE);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(C_WHITE, 1));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(C_BORDER, 1));
        UIManager.put("PopupMenu.background", C_HEADER);
        // Godot-style tree: crisp white chevrons (never the stock blue Metal handles).
        UIManager.put("Tree.collapsedIcon", chevronIcon(false));
        UIManager.put("Tree.expandedIcon", chevronIcon(true));
        UIManager.put("Tree.background", C_VOID);
        UIManager.put("Tree.foreground", C_TEXT);
        UIManager.put("Tree.selectionBackground", C_ACCENT_TINT);
        UIManager.put("Tree.selectionForeground", C_WHITE);
        UIManager.put("Tree.hash", new Color(46, 46, 57));
    }

    /** Small monochrome disclosure chevron for tree/dock headers. */
    public static javax.swing.Icon chevronIcon(final boolean expanded) {
        return new javax.swing.Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(C_TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (expanded) {
                    g2.drawLine(x + 3, y + 5, x + 6, y + 9);
                    g2.drawLine(x + 6, y + 9, x + 9, y + 5);
                } else {
                    g2.drawLine(x + 5, y + 3, x + 9, y + 6);
                    g2.drawLine(x + 9, y + 6, x + 5, y + 9);
                }
                g2.dispose();
            }
            @Override public int getIconWidth() { return 12; }
            @Override public int getIconHeight() { return 12; }
        };
    }

    public static void enableAA(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        try {
            Object dh = java.awt.Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (dh instanceof Map<?, ?>) {
                g2.addRenderingHints((Map<?, ?>) dh);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            }
        } catch (Throwable ignored) {
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_DEFAULT);
    }

    public static Color lerpColor(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    // =========================================================================
    // Crisp Custom Anti-Aliased Vector Icon Renderer (14x14 px)
    // =========================================================================
    public static void paintIcon(Graphics2D g2, Icon icon, float cx, float cy, float size, Color color) {
        if (icon == null || icon == Icon.NONE) return;
        Graphics2D g = (Graphics2D) g2.create();
        enableAA(g);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(color);
        float r = size * 0.5f;
        g.setStroke(new BasicStroke(1.55f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (icon) {
            case PLAY: {
                Path2D p = new Path2D.Float();
                p.moveTo(cx - r * 0.55f, cy - r * 0.72f);
                p.lineTo(cx + r * 0.72f, cy);
                p.lineTo(cx - r * 0.55f, cy + r * 0.72f);
                p.closePath();
                g.fill(p);
                break;
            }
            case STOP: {
                float s = r * 1.15f;
                g.fill(new RoundRectangle2D.Float(cx - s * 0.5f, cy - s * 0.5f, s, s, 2f, 2f));
                break;
            }
            case PLUS: {
                g.draw(new Line2D.Float(cx - r * 0.65f, cy, cx + r * 0.65f, cy));
                g.draw(new Line2D.Float(cx, cy - r * 0.65f, cx, cy + r * 0.65f));
                break;
            }
            case FOLDER: {
                Path2D p = new Path2D.Float();
                p.moveTo(cx - r * 0.8f, cy - r * 0.55f);
                p.lineTo(cx - r * 0.2f, cy - r * 0.55f);
                p.lineTo(cx, cy - r * 0.3f);
                p.lineTo(cx + r * 0.8f, cy - r * 0.3f);
                p.lineTo(cx + r * 0.8f, cy + r * 0.65f);
                p.lineTo(cx - r * 0.8f, cy + r * 0.65f);
                p.closePath();
                g.draw(p);
                break;
            }
            case SAVE: {
                g.draw(new RoundRectangle2D.Float(cx - r * 0.7f, cy - r * 0.7f, r * 1.4f, r * 1.4f, 2.5f, 2.5f));
                g.draw(new Line2D.Float(cx - r * 0.35f, cy - r * 0.7f, cx - r * 0.35f, cy - r * 0.15f));
                g.draw(new Line2D.Float(cx + r * 0.35f, cy - r * 0.7f, cx + r * 0.35f, cy - r * 0.15f));
                g.draw(new Line2D.Float(cx - r * 0.35f, cy - r * 0.15f, cx + r * 0.35f, cy - r * 0.15f));
                g.fill(new RoundRectangle2D.Float(cx - r * 0.4f, cy + r * 0.15f, r * 0.8f, r * 0.45f, 1.5f, 1.5f));
                break;
            }
            case UNDO: {
                g.draw(new Arc2D.Float(cx - r * 0.65f, cy - r * 0.45f, r * 1.3f, r * 1.15f, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Float(cx - r * 0.65f, cy + r * 0.1f, cx - r * 0.9f, cy - r * 0.25f));
                g.draw(new Line2D.Float(cx - r * 0.65f, cy + r * 0.1f, cx - r * 0.28f, cy - r * 0.2f));
                break;
            }
            case REDO: {
                g.draw(new Arc2D.Float(cx - r * 0.65f, cy - r * 0.45f, r * 1.3f, r * 1.15f, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Float(cx + r * 0.65f, cy + r * 0.1f, cx + r * 0.9f, cy - r * 0.25f));
                g.draw(new Line2D.Float(cx + r * 0.65f, cy + r * 0.1f, cx + r * 0.28f, cy - r * 0.2f));
                break;
            }
            case CUBE: {
                Path2D p = new Path2D.Float();
                p.moveTo(cx, cy - r * 0.8f);
                p.lineTo(cx + r * 0.75f, cy - r * 0.38f);
                p.lineTo(cx + r * 0.75f, cy + r * 0.42f);
                p.lineTo(cx, cy + r * 0.82f);
                p.lineTo(cx - r * 0.75f, cy + r * 0.42f);
                p.lineTo(cx - r * 0.75f, cy - r * 0.38f);
                p.closePath();
                g.draw(p);
                g.draw(new Line2D.Float(cx, cy, cx, cy + r * 0.82f));
                g.draw(new Line2D.Float(cx, cy, cx + r * 0.75f, cy - r * 0.38f));
                g.draw(new Line2D.Float(cx, cy, cx - r * 0.75f, cy - r * 0.38f));
                break;
            }
            case CODE: {
                g.draw(new Line2D.Float(cx - r * 0.35f, cy - r * 0.55f, cx - r * 0.8f, cy));
                g.draw(new Line2D.Float(cx - r * 0.8f, cy, cx - r * 0.35f, cy + r * 0.55f));
                g.draw(new Line2D.Float(cx + r * 0.35f, cy - r * 0.55f, cx + r * 0.8f, cy));
                g.draw(new Line2D.Float(cx + r * 0.8f, cy, cx + r * 0.35f, cy + r * 0.55f));
                break;
            }
            case FLOW: {
                Path2D p = new Path2D.Float();
                p.moveTo(cx + r * 0.15f, cy - r * 0.82f);
                p.lineTo(cx - r * 0.55f, cy + r * 0.08f);
                p.lineTo(cx + r * 0.05f, cy + r * 0.08f);
                p.lineTo(cx - r * 0.15f, cy + r * 0.82f);
                p.lineTo(cx + r * 0.55f, cy - r * 0.08f);
                p.lineTo(cx - r * 0.05f, cy - r * 0.08f);
                p.closePath();
                g.fill(p);
                break;
            }
            case SPARKLE: {
                Path2D p = new Path2D.Float();
                p.moveTo(cx, cy - r * 0.85f);
                p.quadTo(cx, cy, cx + r * 0.85f, cy);
                p.quadTo(cx, cy, cx, cy + r * 0.85f);
                p.quadTo(cx, cy, cx - r * 0.85f, cy);
                p.quadTo(cx, cy, cx, cy - r * 0.85f);
                p.closePath();
                g.fill(p);
                break;
            }
            case SYNC: {
                g.draw(new Arc2D.Float(cx - r * 0.68f, cy - r * 0.68f, r * 1.36f, r * 1.36f, 25, 135, Arc2D.OPEN));
                g.draw(new Arc2D.Float(cx - r * 0.68f, cy - r * 0.68f, r * 1.36f, r * 1.36f, 205, 135, Arc2D.OPEN));
                break;
            }
            case GRID: {
                float s = r * 1.4f;
                float x0 = cx - s * 0.5f, y0 = cy - s * 0.5f;
                g.draw(new RoundRectangle2D.Float(x0, y0, s, s, 2f, 2f));
                g.draw(new Line2D.Float(cx, y0, cx, y0 + s));
                g.draw(new Line2D.Float(x0, cy, x0 + s, cy));
                break;
            }
            case WAVE: {
                g.draw(new Line2D.Float(cx - r * 0.65f, cy - r * 0.25f, cx - r * 0.65f, cy + r * 0.25f));
                g.draw(new Line2D.Float(cx - r * 0.22f, cy - r * 0.68f, cx - r * 0.22f, cy + r * 0.68f));
                g.draw(new Line2D.Float(cx + r * 0.22f, cy - r * 0.45f, cx + r * 0.22f, cy + r * 0.45f));
                g.draw(new Line2D.Float(cx + r * 0.65f, cy - r * 0.72f, cx + r * 0.65f, cy + r * 0.72f));
                break;
            }
            case EXPORT: {
                g.draw(new RoundRectangle2D.Float(cx - r * 0.72f, cy - r * 0.1f, r * 1.44f, r * 0.82f, 2f, 2f));
                g.draw(new Line2D.Float(cx, cy + r * 0.25f, cx, cy - r * 0.75f));
                g.draw(new Line2D.Float(cx, cy - r * 0.75f, cx - r * 0.35f, cy - r * 0.4f));
                g.draw(new Line2D.Float(cx, cy - r * 0.75f, cx + r * 0.35f, cy - r * 0.4f));
                break;
            }
            case TERMINAL: {
                g.draw(new Line2D.Float(cx - r * 0.72f, cy - r * 0.45f, cx - r * 0.2f, cy));
                g.draw(new Line2D.Float(cx - r * 0.2f, cy, cx - r * 0.72f, cy + r * 0.45f));
                g.draw(new Line2D.Float(cx + r * 0.05f, cy + r * 0.48f, cx + r * 0.72f, cy + r * 0.48f));
                break;
            }
            case CHECK: {
                g.draw(new Line2D.Float(cx - r * 0.65f, cy + r * 0.05f, cx - r * 0.15f, cy + r * 0.55f));
                g.draw(new Line2D.Float(cx - r * 0.15f, cy + r * 0.55f, cx + r * 0.7f, cy - r * 0.5f));
                break;
            }
            case CLOSE: {
                g.draw(new Line2D.Float(cx - r * 0.52f, cy - r * 0.52f, cx + r * 0.52f, cy + r * 0.52f));
                g.draw(new Line2D.Float(cx + r * 0.52f, cy - r * 0.52f, cx - r * 0.52f, cy + r * 0.52f));
                break;
            }
            case REFRESH: {
                g.draw(new Arc2D.Float(cx - r * 0.65f, cy - r * 0.65f, r * 1.3f, r * 1.3f, 35, 290, Arc2D.OPEN));
                g.draw(new Line2D.Float(cx + r * 0.55f, cy - r * 0.38f, cx + r * 0.65f, cy - r * 0.72f));
                g.draw(new Line2D.Float(cx + r * 0.55f, cy - r * 0.38f, cx + r * 0.22f, cy - r * 0.38f));
                break;
            }
            case FILE: {
                Path2D doc = new Path2D.Float();
                doc.moveTo(cx - r * 0.55f, cy - r * 0.72f);
                doc.lineTo(cx + r * 0.18f, cy - r * 0.72f);
                doc.lineTo(cx + r * 0.55f, cy - r * 0.35f);
                doc.lineTo(cx + r * 0.55f, cy + r * 0.72f);
                doc.lineTo(cx - r * 0.55f, cy + r * 0.72f);
                doc.closePath();
                g.draw(doc);
                g.draw(new Line2D.Float(cx + r * 0.18f, cy - r * 0.72f, cx + r * 0.18f, cy - r * 0.35f));
                g.draw(new Line2D.Float(cx + r * 0.18f, cy - r * 0.35f, cx + r * 0.55f, cy - r * 0.35f));
                break;
            }
            case SPLIT: {
                g.draw(new RoundRectangle2D.Float(cx - r * 0.72f, cy - r * 0.62f, r * 1.44f, r * 1.24f, 2.5f, 2.5f));
                g.draw(new Line2D.Float(cx, cy - r * 0.62f, cx, cy + r * 0.62f));
                break;
            }
            case NODES: {
                g.draw(new RoundRectangle2D.Float(cx - r * 0.78f, cy - r * 0.62f, r * 0.62f, r * 0.52f, 2f, 2f));
                g.draw(new RoundRectangle2D.Float(cx + r * 0.16f, cy + r * 0.10f, r * 0.62f, r * 0.52f, 2f, 2f));
                Path2D wire = new Path2D.Float();
                wire.moveTo(cx - r * 0.16f, cy - r * 0.36f);
                wire.curveTo(cx + r * 0.15f, cy - r * 0.36f, cx - r * 0.15f, cy + r * 0.36f, cx + r * 0.16f, cy + r * 0.36f);
                g.draw(wire);
                break;
            }
            case SEND: {
                Path2D tri = new Path2D.Float();
                tri.moveTo(cx - r * 0.68f, cy - r * 0.58f);
                tri.lineTo(cx + r * 0.72f, cy);
                tri.lineTo(cx - r * 0.68f, cy + r * 0.58f);
                tri.lineTo(cx - r * 0.35f, cy);
                tri.closePath();
                g.draw(tri);
                break;
            }
            case GUI: {
                g.draw(new RoundRectangle2D.Float(cx - r * 0.74f, cy - r * 0.62f, r * 1.48f, r * 1.24f, 3f, 3f));
                g.draw(new Line2D.Float(cx - r * 0.45f, cy - r * 0.18f, cx + r * 0.45f, cy - r * 0.18f));
                g.draw(new Line2D.Float(cx - r * 0.45f, cy + r * 0.22f, cx + r * 0.18f, cy + r * 0.22f));
                break;
            }
            case COLLAPSE_LEFT: {
                g.draw(new Line2D.Float(cx - r * 0.62f, cy - r * 0.62f, cx - r * 0.62f, cy + r * 0.62f));
                Path2D chev = new Path2D.Float();
                chev.moveTo(cx + r * 0.48f, cy - r * 0.52f);
                chev.lineTo(cx - r * 0.18f, cy);
                chev.lineTo(cx + r * 0.48f, cy + r * 0.52f);
                g.draw(chev);
                break;
            }
            case COLLAPSE_RIGHT: {
                g.draw(new Line2D.Float(cx + r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f));
                Path2D chev = new Path2D.Float();
                chev.moveTo(cx - r * 0.48f, cy - r * 0.52f);
                chev.lineTo(cx + r * 0.18f, cy);
                chev.lineTo(cx - r * 0.48f, cy + r * 0.52f);
                g.draw(chev);
                break;
            }
            case COLLAPSE_DOWN: {
                g.draw(new Line2D.Float(cx - r * 0.62f, cy + r * 0.62f, cx + r * 0.62f, cy + r * 0.62f));
                Path2D chev = new Path2D.Float();
                chev.moveTo(cx - r * 0.52f, cy - r * 0.38f);
                chev.lineTo(cx, cy + r * 0.22f);
                chev.lineTo(cx + r * 0.52f, cy - r * 0.38f);
                g.draw(chev);
                break;
            }
            case COLLAPSE_UP: {
                g.draw(new Line2D.Float(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy - r * 0.62f));
                Path2D chev = new Path2D.Float();
                chev.moveTo(cx - r * 0.52f, cy + r * 0.38f);
                chev.lineTo(cx, cy - r * 0.22f);
                chev.lineTo(cx + r * 0.52f, cy + r * 0.38f);
                g.draw(chev);
                break;
            }
            case UNDOCK: {
                g.draw(new RoundRectangle2D.Float(cx - r * 0.72f, cy - r * 0.32f, r * 1.05f, r * 1.02f, 2f, 2f));
                g.draw(new Line2D.Float(cx - r * 0.05f, cy + r * 0.05f, cx + r * 0.68f, cy - r * 0.68f));
                g.draw(new Line2D.Float(cx + r * 0.18f, cy - r * 0.68f, cx + r * 0.68f, cy - r * 0.68f));
                g.draw(new Line2D.Float(cx + r * 0.68f, cy - r * 0.68f, cx + r * 0.68f, cy - r * 0.18f));
                break;
            }
            case MAXIMIZE: {
                float s = r * 0.68f;
                g.draw(new Line2D.Float(cx - s, cy - s, cx - s * 0.3f, cy - s));
                g.draw(new Line2D.Float(cx - s, cy - s, cx - s, cy - s * 0.3f));
                g.draw(new Line2D.Float(cx + s, cy - s, cx + s * 0.3f, cy - s));
                g.draw(new Line2D.Float(cx + s, cy - s, cx + s, cy - s * 0.3f));
                g.draw(new Line2D.Float(cx - s, cy + s, cx - s * 0.3f, cy + s));
                g.draw(new Line2D.Float(cx - s, cy + s, cx - s, cy + s * 0.3f));
                g.draw(new Line2D.Float(cx + s, cy + s, cx + s * 0.3f, cy + s));
                g.draw(new Line2D.Float(cx + s, cy + s, cx + s, cy + s * 0.3f));
                break;
            }
            case MORE_VERT: {
                float d = 2.4f;
                g.fill(new Ellipse2D.Float(cx - d * 0.5f, cy - r * 0.65f - d * 0.5f, d, d));
                g.fill(new Ellipse2D.Float(cx - d * 0.5f, cy - d * 0.5f, d, d));
                g.fill(new Ellipse2D.Float(cx - d * 0.5f, cy + r * 0.65f - d * 0.5f, d, d));
                break;
            }
            case MOVE: {
                float s = r * 0.72f;
                g.draw(new Line2D.Float(cx - s, cy, cx + s, cy));
                g.draw(new Line2D.Float(cx, cy - s, cx, cy + s));
                g.draw(new Line2D.Float(cx + s, cy, cx + s * 0.5f, cy - s * 0.35f));
                g.draw(new Line2D.Float(cx + s, cy, cx + s * 0.5f, cy + s * 0.35f));
                g.draw(new Line2D.Float(cx, cy - s, cx - s * 0.35f, cy - s * 0.5f));
                g.draw(new Line2D.Float(cx, cy - s, cx + s * 0.35f, cy - s * 0.5f));
                break;
            }
            case SCALE_GIZMO: {
                float s = r * 0.68f;
                g.draw(new Line2D.Float(cx - s, cy + s, cx + s, cy - s));
                g.fill(new RoundRectangle2D.Float(cx + s - 2.5f, cy - s - 0.5f, 3.5f, 3.5f, 1f, 1f));
                g.fill(new RoundRectangle2D.Float(cx - s - 1f, cy + s - 2.5f, 3.5f, 3.5f, 1f, 1f));
                break;
            }
            case EYE: {
                Path2D p = new Path2D.Float();
                p.moveTo(cx - r * 0.8f, cy);
                p.quadTo(cx, cy - r * 0.72f, cx + r * 0.8f, cy);
                p.quadTo(cx, cy + r * 0.72f, cx - r * 0.8f, cy);
                g.draw(p);
                g.fill(new Ellipse2D.Float(cx - r * 0.25f, cy - r * 0.25f, r * 0.5f, r * 0.5f));
                break;
            }
            default:
                break;
        }
        g.dispose();
    }

    // =========================================================================
    // 1. VortexButton — Custom 60 FPS Hover + Tactile Spring + Vector Icon Button
    // =========================================================================
    public static final class VortexButton extends JButton {
        private Variant variant;
        private Icon iconType = Icon.NONE;
        private float hoverT = 0f;
        private float pressT = 0f;
        private boolean hovered = false;
        private boolean pressed = false;
        private final Timer animTimer;

        public VortexButton(String text, Variant variant) {
            this(text, variant, Icon.NONE);
        }

        public VortexButton(String text, Variant variant, Icon iconType) {
            super(text);
            this.variant = variant;
            this.iconType = iconType == null ? Icon.NONE : iconType;
            setUI(new BasicButtonUI());
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(variant == Variant.PRIMARY || variant == Variant.ACCENT ? FONT_UI_BOLD : FONT_UI);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(scaled(4), scaled(9), scaled(4), scaled(9)));

            animTimer = new Timer(16, e -> {
                float targetH = hovered ? 1f : 0f;
                float targetP = pressed ? 1f : 0f;
                boolean doneH = Math.abs(hoverT - targetH) < 0.04f;
                boolean doneP = Math.abs(pressT - targetP) < 0.06f;
                hoverT = doneH ? targetH : hoverT + (targetH - hoverT) * 0.32f;
                pressT = doneP ? targetP : pressT + (targetP - pressT) * 0.42f;
                if (doneH && doneP) ((Timer) e.getSource()).stop();
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; pressed = false; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mousePressed(MouseEvent e) { pressed = true; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; if (!animTimer.isRunning()) animTimer.start(); }
            });
        }

        public void setVariant(Variant v) {
            this.variant = v;
            setFont(v == Variant.PRIMARY || v == Variant.ACCENT ? FONT_UI_BOLD : FONT_UI);
            repaint();
        }

        public void setIconType(Icon ic) {
            this.iconType = ic == null ? Icon.NONE : ic;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            setFont(variant == Variant.PRIMARY || variant == Variant.ACCENT ? FONT_UI_BOLD : FONT_UI);
            Dimension d = super.getPreferredSize();
            int minH = scaled(26);
            if (iconType != Icon.NONE) {
                String txt = getText();
                int extra = (txt == null || txt.isEmpty()) ? scaled(6) : scaled(15);
                return new Dimension(d.width + extra, Math.max(minH, d.height));
            }
            return new Dimension(d.width, Math.max(minH, d.height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);

            int w = getWidth();
            int h = getHeight();
            float inset = 0.5f + pressT * 1.1f;

            Color bgBase, bgHover, borderBase, borderHover, fg;
            switch (variant) {
                case PRIMARY:
                    bgBase = C_WHITE_SOFT;
                    bgHover = C_WHITE;
                    borderBase = C_WHITE;
                    borderHover = C_WHITE;
                    fg = C_DARK_INK;
                    break;
                case ACCENT:
                    bgBase = C_ACCENT_TINT;
                    bgHover = new Color(52, 52, 60);
                    borderBase = new Color(210, 210, 218);
                    borderHover = C_WHITE;
                    fg = C_WHITE;
                    break;
                case DANGER:
                    bgBase = C_DANGER_BG;
                    bgHover = C_DANGER_HOV;
                    borderBase = C_DANGER_BD;
                    borderHover = C_DANGER_TX;
                    fg = C_DANGER_TX;
                    break;
                case SECONDARY:
                default:
                    bgBase = C_SURFACE;
                    bgHover = C_SURFACE_HOV;
                    borderBase = C_BORDER;
                    borderHover = new Color(150, 150, 162);
                    fg = C_TEXT;
                    break;
            }

            Color bg = lerpColor(bgBase, bgHover, hoverT);
            if (pressT > 0.05f) bg = lerpColor(bg, C_VOID, pressT * 0.16f);
            Color bd = lerpColor(borderBase, borderHover, hoverT);

            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(
                    inset, inset, w - inset * 2f - 0.5f, h - inset * 2f - 0.5f, 7f, 7f);
            g2.setColor(bg);
            g2.fill(shape);
            g2.setColor(bd);
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(shape);

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String txt = getText() == null ? "" : getText();
            int tw = fm.stringWidth(txt);
            boolean hasIcon = iconType != Icon.NONE;
            int iconW = hasIcon ? 13 : 0;
            int gap = (hasIcon && !txt.isEmpty()) ? 6 : 0;
            int totalW = iconW + gap + tw;
            int startX = (w - totalW) / 2;

            if (hasIcon) {
                paintIcon(g2, iconType, startX + iconW * 0.5f, h * 0.5f, 12.5f, fg);
            }
            if (!txt.isEmpty()) {
                g2.setColor(fg);
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(txt, startX + iconW + gap, ty);
            }
            g2.dispose();
        }
    }

    // =========================================================================
    // 2. VortexDropdown — Custom Dark Selector + 60 FPS Rotating White Chevron
    // =========================================================================
    public static final class VortexDropdown extends JComponent {
        private String[] items;
        private int selectedIdx = 0;
        private float hoverT = 0f;
        private float openT = 0f;
        private boolean hovered = false;
        private boolean popupOpen = false;
        private BiConsumer<Integer, String> onSelect;
        private final Timer animTimer;

        public VortexDropdown(String[] items, String currentItem) {
            this.items = (items == null || items.length == 0) ? new String[]{""} : items.clone();
            if (currentItem != null) {
                for (int i = 0; i < this.items.length; i++) {
                    if (this.items[i].equalsIgnoreCase(currentItem)) {
                        this.selectedIdx = i;
                        break;
                    }
                }
            }
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(scaled(190), scaled(28)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(30)));

            animTimer = new Timer(16, e -> {
                float tH = hovered ? 1f : 0f;
                float tO = popupOpen ? 1f : 0f;
                boolean d1 = Math.abs(hoverT - tH) < 0.04f;
                boolean d2 = Math.abs(openT - tO) < 0.04f;
                hoverT = d1 ? tH : hoverT + (tH - hoverT) * 0.30f;
                openT = d2 ? tO : openT + (tO - openT) * 0.32f;
                if (d1 && d2) ((Timer) e.getSource()).stop();
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mousePressed(MouseEvent e) { showDropdownPopup(); }
            });
        }

        public void setItems(String[] nextItems, String selectItem) {
            this.items = (nextItems == null || nextItems.length == 0) ? new String[]{""} : nextItems.clone();
            this.selectedIdx = 0;
            if (selectItem != null) {
                for (int i = 0; i < this.items.length; i++) {
                    if (this.items[i].equalsIgnoreCase(selectItem)) { this.selectedIdx = i; break; }
                }
            }
            repaint();
        }

        public int getSelectedIndex() { return selectedIdx; }
        public String getSelectedItem() { return items[Math.max(0, Math.min(selectedIdx, items.length - 1))]; }

        public void setSelectedIndex(int idx) {
            if (idx >= 0 && idx < items.length) {
                this.selectedIdx = idx;
                repaint();
            }
        }

        public void setOnSelect(BiConsumer<Integer, String> listener) {
            this.onSelect = listener;
        }

        private void showDropdownPopup() {
            JPopupMenu popup = new JPopupMenu();
            popup.setBackground(C_HEADER);
            popup.setBorder(BorderFactory.createLineBorder(C_WHITE, 1));

            popup.addPopupMenuListener(new PopupMenuListener() {
                @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { popupOpen = true; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { popupOpen = false; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void popupMenuCanceled(PopupMenuEvent e) { popupOpen = false; if (!animTimer.isRunning()) animTimer.start(); }
            });

            JPanel listBox = new JPanel();
            listBox.setLayout(new BoxLayout(listBox, BoxLayout.Y_AXIS));
            listBox.setBackground(C_HEADER);
            listBox.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

            int minW = Math.max(getWidth() - 10, 210);
            for (int i = 0; i < items.length; i++) {
                final int idx = i;
                final boolean active = (i == selectedIdx);
                JPanel row = new JPanel(new BorderLayout()) {
                    boolean rowHover = false;
                    {
                        setOpaque(false);
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                        addMouseListener(new MouseAdapter() {
                            @Override public void mouseEntered(MouseEvent e) { rowHover = true; repaint(); }
                            @Override public void mouseExited(MouseEvent e) { rowHover = false; repaint(); }
                            @Override public void mousePressed(MouseEvent e) {
                                selectedIdx = idx;
                                popup.setVisible(false);
                                repaint();
                                if (onSelect != null) onSelect.accept(idx, items[idx]);
                            }
                        });
                    }
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        enableAA(g2);
                        if (active || rowHover) {
                            g2.setColor(active ? C_ACCENT_TINT : C_SURFACE_HOV);
                            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 5, 5));
                            if (active) {
                                g2.setColor(C_WHITE);
                                g2.fill(new RoundRectangle2D.Float(2, 5, 3, getHeight() - 10, 2, 2));
                            }
                        }
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
                JLabel lbl = new JLabel(items[i]);
                lbl.setFont(active ? FONT_UI_BOLD : FONT_UI);
                lbl.setForeground(active ? C_WHITE : C_TEXT);
                row.add(lbl, BorderLayout.CENTER);
                Dimension pref = row.getPreferredSize();
                row.setPreferredSize(new Dimension(Math.max(minW, pref.width + 18), 28));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                listBox.add(row);
            }

            if (items.length > 10) {
                JScrollPane sp = createScrollPane(listBox);
                sp.setPreferredSize(new Dimension(Math.max(getWidth(), 280), 280));
                popup.add(sp);
            } else {
                popup.add(listBox);
            }
            popup.show(this, 0, getHeight() + 3);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);

            int w = getWidth();
            int h = getHeight();
            Color bg = lerpColor(C_INPUT_BG, C_SURFACE, Math.max(hoverT * 0.7f, openT));
            Color bd = lerpColor(C_BORDER, C_WHITE, Math.max(hoverT * 0.65f, openT));

            RoundRectangle2D.Float box = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, 7f, 7f);
            g2.setColor(bg);
            g2.fill(box);
            g2.setColor(bd);
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(box);

            g2.setFont(FONT_UI);
            g2.setColor(C_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            String text = getSelectedItem();
            int maxTextW = Math.max(20, w - 34);
            if (fm.stringWidth(text) > maxTextW) {
                while (text.length() > 3 && fm.stringWidth(text + "…") > maxTextW) {
                    text = text.substring(0, text.length() - 1);
                }
                text = text + "…";
            }
            g2.drawString(text, 10, (h - fm.getHeight()) / 2 + fm.getAscent());

            double cx = w - 15.0;
            double cy = h / 2.0;
            AffineTransform old = g2.getTransform();
            g2.translate(cx, cy);
            g2.rotate(Math.PI * openT);
            Path2D chev = new Path2D.Double();
            chev.moveTo(-4.2, -1.8);
            chev.lineTo(0.0, 2.4);
            chev.lineTo(4.2, -1.8);
            g2.setColor(openT > 0.2f || hoverT > 0.3f ? C_WHITE : C_TEXT_MUTED);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(chev);
            g2.setTransform(old);

            g2.dispose();
        }
    }

    // =========================================================================
    // 3. VortexToggle — Custom 60 FPS Monochrome White Pill Switch
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

            int sw = 34, sh = 18;
            int sx = w - sw - 2;
            int sy = (h - sh) / 2;

            Color track = lerpColor(C_SURFACE, C_WHITE, animT);
            Color border = lerpColor(C_BORDER, C_WHITE, animT);
            g2.setColor(track);
            g2.fill(new RoundRectangle2D.Float(sx, sy, sw, sh, sh, sh));
            g2.setColor(border);
            g2.draw(new RoundRectangle2D.Float(sx, sy, sw, sh, sh, sh));

            float knobSize = 12f;
            float kx = sx + 3f + animT * (sw - knobSize - 6f);
            float ky = sy + (sh - knobSize) / 2f;
            g2.setColor(lerpColor(C_TEXT_MUTED, C_DARK_INK, animT));
            g2.fill(new RoundRectangle2D.Float(kx, ky, knobSize, knobSize, knobSize, knobSize));

            g2.dispose();
        }
    }

    // =========================================================================
    // 4. Custom Focus-Animated TextField (Crisp White Focus Ring)
    // =========================================================================
    public static JTextField createField(String initialValue) {
        JTextField tf = new JTextField(initialValue == null ? "" : initialValue) {
            private float focusT = 0f;
            private boolean focused = false;
            private final Timer fTimer = new Timer(16, e -> {
                float target = focused ? 1f : 0f;
                if (Math.abs(focusT - target) < 0.05f) {
                    focusT = target;
                    ((Timer) e.getSource()).stop();
                } else {
                    focusT += (target - focusT) * 0.30f;
                }
                repaint();
            });
            {
                setOpaque(false);
                setForeground(C_TEXT);
                setCaretColor(C_WHITE);
                setSelectionColor(C_ACCENT_TINT);
                setSelectedTextColor(C_WHITE);
                setFont(FONT_UI);
                setBorder(BorderFactory.createEmptyBorder(6, 9, 6, 9));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
                addFocusListener(new FocusAdapter() {
                    @Override public void focusGained(FocusEvent e) { focused = true; if (!fTimer.isRunning()) fTimer.start(); }
                    @Override public void focusLost(FocusEvent e) { focused = false; if (!fTimer.isRunning()) fTimer.start(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                enableAA(g2);
                Color bg = lerpColor(C_INPUT_BG, new Color(20, 20, 24), focusT);
                Color bd = lerpColor(C_BORDER, C_WHITE, focusT);
                RoundRectangle2D.Float r = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 6f, 6f);
                g2.setColor(bg);
                g2.fill(r);
                g2.setColor(bd);
                g2.setStroke(new BasicStroke(1.1f));
                g2.draw(r);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        return tf;
    }

    // =========================================================================
    // 5. VortexTabStrip — 60 FPS Sliding White Pill + Custom Vector Icons
    // =========================================================================
    public static final class VortexTabStrip extends JComponent {
        private final String[] keys;
        private final String[] labels;
        private final Icon[] icons;
        private int activeIdx = 0;
        private int hoverIdx = -1;
        private float indicatorX = 0f;
        private float indicatorW = 64f;
        private boolean initialized = false;
        private BiConsumer<Integer, String> onTabSelected;
        private final Timer slideTimer;

        public VortexTabStrip(String[] keys, String[] labels) {
            this(keys, labels, null);
        }

        public VortexTabStrip(String[] keys, String[] labels, Icon[] icons) {
            this.keys = keys;
            this.labels = labels;
            this.icons = icons;
            setOpaque(true);
            setBackground(C_HEADER);
            setPreferredSize(new Dimension(scaled(380), scaled(33)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            slideTimer = new Timer(16, e -> {
                Rectangle target = getTabBounds(activeIdx);
                float dx = target.x - indicatorX;
                float dw = target.width - indicatorW;
                if (Math.abs(dx) < 0.7f && Math.abs(dw) < 0.7f) {
                    indicatorX = target.x;
                    indicatorW = target.width;
                    ((Timer) e.getSource()).stop();
                } else {
                    indicatorX += dx * 0.34f;
                    indicatorW += dw * 0.34f;
                }
                repaint();
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

        public void selectTab(int idx, boolean fireCallback) {
            if (idx < 0 || idx >= keys.length) return;
            this.activeIdx = idx;
            if (!slideTimer.isRunning()) slideTimer.start();
            repaint();
            if (fireCallback && onTabSelected != null) {
                onTabSelected.accept(idx, keys[idx]);
            }
        }

        public void selectTabByKey(String key, boolean fireCallback) {
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].equalsIgnoreCase(key)) {
                    selectTab(i, fireCallback);
                    return;
                }
            }
        }

        public void setOnTabSelected(BiConsumer<Integer, String> cb) {
            this.onTabSelected = cb;
        }

        private Rectangle getTabBounds(int idx) {
            int n = Math.max(1, keys.length);
            int pad = 6;
            int avail = Math.max(60, getWidth() - pad * 2);
            int w = avail / n;
            return new Rectangle(pad + idx * w, 4, w - 2, getHeight() - 8);
        }

        private int tabAt(Point p) {
            for (int i = 0; i < keys.length; i++) {
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

            if (!initialized && w > 20) {
                Rectangle init = getTabBounds(activeIdx);
                indicatorX = init.x;
                indicatorW = init.width;
                initialized = true;
            }

            if (hoverIdx >= 0 && hoverIdx != activeIdx) {
                Rectangle hr = getTabBounds(hoverIdx);
                g2.setColor(C_SURFACE);
                g2.fill(new RoundRectangle2D.Float(hr.x, hr.y, hr.width, hr.height, 6f, 6f));
            }

            g2.setColor(C_ACCENT_TINT);
            g2.fill(new RoundRectangle2D.Float(indicatorX, 4f, indicatorW, h - 8f, 6f, 6f));
            g2.setColor(new Color(95, 95, 108));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(indicatorX, 4f, indicatorW, h - 8f, 6f, 6f));
            g2.setColor(C_WHITE);
            g2.fill(new RoundRectangle2D.Float(indicatorX + 8f, h - 3f, Math.max(10f, indicatorW - 16f), 2.2f, 2f, 2f));

            for (int i = 0; i < labels.length; i++) {
                Rectangle r = getTabBounds(i);
                g2.setFont(i == activeIdx ? FONT_UI_BOLD : FONT_UI);
                FontMetrics fm = g2.getFontMetrics();
                Color fg = i == activeIdx ? C_WHITE : (i == hoverIdx ? C_TEXT : C_TEXT_MUTED);
                g2.setColor(fg);
                String lbl = labels[i];
                Icon ic = (icons != null && i < icons.length && icons[i] != null) ? icons[i] : Icon.NONE;
                int iconW = ic != Icon.NONE ? 12 : 0;
                int gap = ic != Icon.NONE ? 5 : 0;
                int totalW = iconW + gap + fm.stringWidth(lbl);
                int sx = r.x + (r.width - totalW) / 2;
                if (ic != Icon.NONE) {
                    paintIcon(g2, ic, sx + iconW * 0.5f, h * 0.5f, 11.5f, fg);
                }
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(lbl, sx + iconW + gap, ty);
            }
            g2.dispose();
        }
    }

    // =========================================================================
    // 6. Sleek Thin Dark ScrollPane & Draggable 8px Grip-Handle SplitPane!
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
            @Override protected JButton createDecreaseButton(int orientation) { return zeroBtn(); }
            @Override protected JButton createIncreaseButton(int orientation) { return zeroBtn(); }
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
                g2.setColor(isDragging || isThumbRollover() ? C_WHITE : new Color(68, 68, 78));
                g2.fill(new RoundRectangle2D.Float(r.x + 1, r.y + 1, r.width - 2, r.height - 2, 6, 6));
                g2.dispose();
            }
        });
    }

    /**
     * Draggable 9px Beveled SplitPane with crisp dual separation borders, glowing 3-dot grip handle,
     * and double-click-to-collapse / restore memory!
     */
    public static JSplitPane createSplitPane(int orientation, Component a, Component b, int dividerLoc) {
        if (a != null) a.setMinimumSize(new Dimension(0, 0));
        if (b != null) b.setMinimumSize(new Dimension(0, 0));

        final JSplitPane sp = new JSplitPane(orientation, true, a, b);
        final int[] savedLoc = {dividerLoc};
        sp.setOpaque(true);
        sp.setBackground(new Color(25, 25, 32));
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setUI(new BasicSplitPaneUI() {
            @Override
            public BasicSplitPaneDivider createDefaultDivider() {
                return new BasicSplitPaneDivider(this) {
                    private boolean divHover = false;
                    {
                        setBorder(BorderFactory.createEmptyBorder());
                        setCursor(Cursor.getPredefinedCursor(
                                orientation == JSplitPane.HORIZONTAL_SPLIT
                                        ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
                        addMouseListener(new MouseAdapter() {
                            @Override public void mouseEntered(MouseEvent e) { divHover = true; repaint(); }
                            @Override public void mouseExited(MouseEvent e) { divHover = false; repaint(); }
                            @Override public void mouseClicked(MouseEvent e) {
                                if (e.getClickCount() >= 2) {
                                    int cur = sp.getDividerLocation();
                                    int maxDim = orientation == JSplitPane.HORIZONTAL_SPLIT ? sp.getWidth() : sp.getHeight();
                                    if (cur <= 18 || (maxDim > 80 && cur >= maxDim - 26)) {
                                        sp.setDividerLocation(Math.max(120, savedLoc[0]));
                                    } else {
                                        savedLoc[0] = cur;
                                        if (orientation == JSplitPane.VERTICAL_SPLIT) {
                                            sp.setDividerLocation(Math.max(40, maxDim - 36));
                                        } else {
                                            sp.setDividerLocation(0);
                                        }
                                    }
                                }
                            }
                        });
                    }
                    @Override
                    public void paint(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        enableAA(g2);
                        int w = getWidth();
                        int h = getHeight();
                        g2.setColor(divHover ? C_SURFACE_HOV : new Color(25, 25, 32));
                        g2.fillRect(0, 0, w, h);

                        if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                            g2.setColor(divHover ? C_BORDER_STRONG : C_BORDER);
                            g2.drawLine(0, 0, 0, h);
                            g2.drawLine(w - 1, 0, w - 1, h);
                            g2.setColor(divHover ? C_WHITE : C_TEXT_MUTED);
                            int cy = h / 2;
                            for (int dy = -7; dy <= 7; dy += 7) {
                                g2.fill(new Ellipse2D.Float(w / 2f - 1.6f, cy + dy - 1.6f, 3.2f, 3.2f));
                            }
                        } else {
                            g2.setColor(divHover ? C_BORDER_STRONG : C_BORDER);
                            g2.drawLine(0, 0, w, 0);
                            g2.drawLine(0, h - 1, w, h - 1);
                            g2.setColor(divHover ? C_WHITE : C_TEXT_MUTED);
                            int cx = w / 2;
                            for (int dx = -7; dx <= 7; dx += 7) {
                                g2.fill(new Ellipse2D.Float(cx + dx - 1.6f, h / 2f - 1.6f, 3.2f, 3.2f));
                            }
                        }
                        g2.dispose();
                    }
                };
            }
        });
        // Set dividerSize & dividerLocation AFTER setUI so BasicSplitPaneUI never resets them!
        sp.setDividerSize(9);
        sp.setDividerLocation(dividerLoc);
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
        popup.setBorder(BorderFactory.createLineBorder(C_WHITE, 1));

        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBackground(C_HEADER);
        box.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        for (MenuAction ma : actions) {
            if ("---".equals(ma.label)) {
                box.add(Box.createVerticalStrut(4));
                JPanel sep = new JPanel();
                sep.setBackground(C_BORDER);
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                sep.setPreferredSize(new Dimension(180, 1));
                box.add(sep);
                box.add(Box.createVerticalStrut(4));
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
                        g2.setColor(C_ACCENT_TINT);
                        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                        g2.setColor(C_WHITE);
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

    public static void showInfoModal(Component owner, String title, String message) {
        java.awt.Window win = owner instanceof java.awt.Window
                ? (java.awt.Window) owner
                : (owner != null ? SwingUtilities.getWindowAncestor(owner) : null);
        JDialog dlg = new JDialog(win, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_WHITE, 1),
                BorderFactory.createEmptyBorder(16, 18, 14, 18)));

        JLabel head = new JLabel(title);
        head.setFont(FONT_TITLE);
        head.setForeground(C_WHITE);
        root.add(head, BorderLayout.NORTH);

        JLabel msg = new JLabel("<html><body style='width:380px;color:#FAFAFA;font-family:" + UI_FAMILY + ",sans-serif;font-size:11pt;'>"
                + message.replace("\n", "<br>") + "</body></html>");
        root.add(msg, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        VortexButton ok = new VortexButton("OK", Variant.PRIMARY, Icon.CHECK);
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
                    l.setBackground(C_ACCENT_TINT);
                    l.setForeground(C_WHITE);
                } else {
                    l.setBackground(index % 2 == 0 ? C_VOID : new Color(15, 15, 18));
                    l.setForeground(C_TEXT);
                }
                return l;
            }
        };
    }

    // =========================================================================
    // 8. VortexSlider — Custom Animated White Track & Glowing Thumb Slider
    // =========================================================================
    public static final class VortexSlider extends JComponent {
        private final int min;
        private final int max;
        private int value;
        private final String prefix;
        private final String suffix;
        private float hoverT = 0f;
        private boolean hover = false;
        private boolean dragging = false;
        private IntConsumer onChange;
        private final Timer animTimer;

        public VortexSlider(String prefix, int min, int max, int initial, String suffix) {
            this.prefix = prefix;
            this.min = min;
            this.max = Math.max(min + 1, max);
            this.value = Math.max(min, Math.min(max, initial));
            this.suffix = suffix;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(240, 30));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            animTimer = new Timer(16, e -> {
                float target = (hover || dragging) ? 1f : 0f;
                if (Math.abs(hoverT - target) < 0.05f) {
                    hoverT = target;
                    ((Timer) e.getSource()).stop();
                } else {
                    hoverT += (target - hoverT) * 0.30f;
                }
                repaint();
            });

            MouseAdapter ma = new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mousePressed(MouseEvent e) { dragging = true; updateFromX(e.getX()); if (!animTimer.isRunning()) animTimer.start(); }
                @Override public void mouseReleased(MouseEvent e) { dragging = false; if (!animTimer.isRunning()) animTimer.start(); }
            };
            addMouseListener(ma);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) { updateFromX(e.getX()); }
            });
        }

        public void setOnChange(IntConsumer c) { this.onChange = c; }
        public int getValue() { return value; }
        public void setValue(int v) {
            this.value = Math.max(min, Math.min(max, v));
            repaint();
        }

        private void updateFromX(int mx) {
            int trackX = 115;
            int trackW = Math.max(40, getWidth() - trackX - 12);
            float t = Math.max(0f, Math.min(1f, (mx - trackX) / (float) trackW));
            int next = Math.round(min + t * (max - min));
            if (next != value) {
                value = next;
                repaint();
                if (onChange != null) onChange.accept(value);
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth();
            int h = getHeight();

            String txt = prefix + ": " + value + suffix;
            g2.setFont(FONT_UI_BOLD);
            g2.setColor(C_WHITE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(txt, 2, (h + fm.getAscent() - fm.getDescent()) / 2);

            int trackX = 115;
            int trackW = Math.max(40, w - trackX - 12);
            int trackY = h / 2 - 3;
            g2.setColor(C_SURFACE);
            g2.fill(new RoundRectangle2D.Float(trackX, trackY, trackW, 6, 6, 6));
            g2.setColor(C_BORDER);
            g2.draw(new RoundRectangle2D.Float(trackX, trackY, trackW, 6, 6, 6));

            float frac = (value - min) / (float) (max - min);
            float fillW = Math.max(6f, frac * trackW);
            g2.setColor(C_WHITE);
            g2.fill(new RoundRectangle2D.Float(trackX, trackY, fillW, 6, 6, 6));

            float cx = trackX + frac * trackW;
            float cy = h / 2f;
            if (hoverT > 0.02f) {
                g2.setColor(new Color(255, 255, 255, Math.round(45 * hoverT)));
                float gr = 8f + 3f * hoverT;
                g2.fill(new RoundRectangle2D.Float(cx - gr, cy - gr, gr * 2, gr * 2, gr * 2, gr * 2));
            }
            float r = 6.0f + 1.2f * hoverT;
            g2.setColor(C_WHITE);
            g2.fill(new RoundRectangle2D.Float(cx - r, cy - r, r * 2, r * 2, r * 2, r * 2));
            g2.setColor(C_DARK_INK);
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(new RoundRectangle2D.Float(cx - r, cy - r, r * 2, r * 2, r * 2, r * 2));
            g2.dispose();
        }
    }

    // =========================================================================
    // 9. AnimatedDeck — 60 FPS Smooth Slide + Alpha Fade Workspace Switcher
    // =========================================================================
    public static final class AnimatedDeck extends JPanel {
        private final CardLayout cards = new CardLayout();
        private final Map<String, Integer> orderMap = new HashMap<>();
        private String currentKey = null;
        private float animProgress = 1f;
        private int slideDir = 1;
        private final Timer animTimer;

        public AnimatedDeck() {
            setLayout(cards);
            setBackground(C_VOID);
            animTimer = new Timer(16, e -> {
                animProgress += (1f - animProgress) * 0.34f;
                if (1f - animProgress < 0.02f) {
                    animProgress = 1f;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
        }

        public void addCard(String key, JComponent comp, int order) {
            orderMap.put(key, order);
            add(comp, key);
            if (currentKey == null) currentKey = key;
        }

        public void showCard(String key, boolean animate) {
            if (key == null || key.equals(currentKey)) return;
            int prevIdx = orderMap.getOrDefault(currentKey, 0);
            int nextIdx = orderMap.getOrDefault(key, 0);
            slideDir = nextIdx >= prevIdx ? 1 : -1;
            currentKey = key;
            cards.show(this, key);
            if (animate) {
                animProgress = 0f;
                if (!animTimer.isRunning()) animTimer.start();
            } else {
                animProgress = 1f;
                repaint();
            }
        }

        @Override
        public void paint(Graphics g) {
            if (animProgress >= 0.99f) {
                super.paint(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            float alpha = 0.45f + 0.55f * animProgress;
            int dx = Math.round((1f - animProgress) * 14f * slideDir);
            g2.translate(dx, 0);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
            super.paint(g2);
            g2.dispose();
        }
    }

    // =========================================================================
    // 10. Custom Dark Monochrome File Browser Modal (Zero Swing JFileChooser!)
    // =========================================================================
    public static File showFileModal(Component owner, String title, boolean saveMode, String defaultFileName) {
        final File[] chosen = {null};
        final File[] curDir = {new File(System.getProperty("user.home", ".")).getAbsoluteFile()};
        if (!curDir[0].exists()) curDir[0] = new File(".").getAbsoluteFile();

        java.awt.Window win = owner instanceof java.awt.Window
                ? (java.awt.Window) owner
                : (owner != null ? SwingUtilities.getWindowAncestor(owner) : null);
        JDialog dlg = new JDialog(win, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(C_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(C_WHITE, 1),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        root.setPreferredSize(new Dimension(540, 390));

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.setOpaque(false);
        JLabel head = new JLabel(title.toUpperCase());
        head.setFont(FONT_TITLE);
        head.setForeground(C_WHITE);
        top.add(head, BorderLayout.NORTH);

        JPanel navRow = new JPanel(new BorderLayout(6, 0));
        navRow.setOpaque(false);
        JTextField dirField = createField(curDir[0].getAbsolutePath());
        JPanel navBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navBtns.setOpaque(false);
        VortexButton upBtn = new VortexButton("Up", Variant.SECONDARY);
        VortexButton homeBtn = new VortexButton("Home", Variant.SECONDARY, Icon.FOLDER);
        navBtns.add(upBtn);
        navBtns.add(homeBtn);
        navRow.add(navBtns, BorderLayout.WEST);
        navRow.add(dirField, BorderLayout.CENTER);
        top.add(navRow, BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        DefaultListModel<String> listModel = new DefaultListModel<>();
        final List<File> currentEntries = new ArrayList<>();
        JList<String> fileList = new JList<>(listModel);
        fileList.setBackground(C_VOID);
        fileList.setCellRenderer(createDarkListRenderer());
        JScrollPane sp = createScrollPane(fileList);
        sp.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
        root.add(sp, BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout(0, 8));
        bot.setOpaque(false);
        JPanel nameRow = new JPanel(new BorderLayout(8, 0));
        nameRow.setOpaque(false);
        JLabel fnLbl = new JLabel("File Name:");
        fnLbl.setFont(FONT_UI);
        fnLbl.setForeground(C_TEXT_MUTED);
        JTextField nameField = createField(defaultFileName != null ? defaultFileName : "project.vortex.json");
        nameRow.add(fnLbl, BorderLayout.WEST);
        nameRow.add(nameField, BorderLayout.CENTER);
        bot.add(nameRow, BorderLayout.NORTH);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        VortexButton cancelBtn = new VortexButton("Cancel", Variant.SECONDARY, Icon.CLOSE);
        cancelBtn.addActionListener(e -> dlg.dispose());
        VortexButton okBtn = new VortexButton(saveMode ? "Save File" : "Open File", Variant.PRIMARY, saveMode ? Icon.SAVE : Icon.FOLDER);
        foot.add(cancelBtn);
        foot.add(okBtn);
        bot.add(foot, BorderLayout.SOUTH);
        root.add(bot, BorderLayout.SOUTH);

        Runnable reloadDir = () -> {
            dirField.setText(curDir[0].getAbsolutePath());
            listModel.clear();
            currentEntries.clear();
            File[] children = curDir[0].listFiles();
            if (children != null) {
                Arrays.sort(children, (a, b) -> {
                    if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                for (File f : children) {
                    if (f.getName().startsWith(".")) continue;
                    currentEntries.add(f);
                    listModel.addElement((f.isDirectory() ? "[DIR]  " : "       ") + f.getName());
                }
            }
        };

        upBtn.addActionListener(e -> {
            File p = curDir[0].getParentFile();
            if (p != null && p.exists()) { curDir[0] = p; reloadDir.run(); }
        });
        homeBtn.addActionListener(e -> {
            curDir[0] = new File(System.getProperty("user.home", ".")).getAbsoluteFile();
            reloadDir.run();
        });
        dirField.addActionListener(e -> {
            File typed = new File(dirField.getText().trim());
            if (typed.isDirectory()) { curDir[0] = typed; reloadDir.run(); }
        });
        fileList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = fileList.getSelectedIndex();
                if (idx < 0 || idx >= currentEntries.size()) return;
                File sel = currentEntries.get(idx);
                if (e.getClickCount() >= 2 && sel.isDirectory()) {
                    curDir[0] = sel;
                    reloadDir.run();
                } else if (sel.isFile()) {
                    nameField.setText(sel.getName());
                    if (e.getClickCount() >= 2 && !saveMode) {
                        chosen[0] = sel;
                        dlg.dispose();
                    }
                }
            }
        });
        okBtn.addActionListener(e -> {
            String nm = nameField.getText().trim();
            if (nm.isEmpty()) return;
            File f = new File(nm);
            chosen[0] = f.isAbsolute() ? f : new File(curDir[0], nm);
            dlg.dispose();
        });

        reloadDir.run();
        dlg.setContentPane(root);
        dlg.pack();
        dlg.setLocationRelativeTo(owner);
        dlg.setVisible(true);
        return chosen[0];
    }

    // =========================================================================
    // 11. Distinct Panel Dock Header Bar (Accent Bar + Title + Count + Actions)
    // =========================================================================
    public static JPanel createDockHeader(String title, String badgeText, JComponent rightActions) {
        JPanel hdr = new JPanel(new BorderLayout(6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                enableAA(g2);
                g2.setColor(C_WHITE);
                g2.fill(new RoundRectangle2D.Float(4, 6, 3f, Math.max(8, getHeight() - 12), 2f, 2f));
                g2.dispose();
            }
        };
        hdr.setBackground(C_HEADER);
        hdr.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER),
                BorderFactory.createEmptyBorder(scaled(4), scaled(12), scaled(4), scaled(6))));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 1));
        left.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(FONT_UI_BOLD);
        t.setForeground(C_WHITE);
        left.add(t);
        if (badgeText != null && !badgeText.isEmpty()) {
            JLabel b = new JLabel(" " + badgeText + " ");
            b.setFont(FONT_SMALL_BOLD);
            b.setOpaque(true);
            b.setBackground(C_SURFACE);
            b.setForeground(C_TEXT_LABEL);
            b.setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
            left.add(b);
        }
        hdr.add(left, BorderLayout.CENTER);
        if (rightActions != null) {
            rightActions.setOpaque(false);
            hdr.add(rightActions, BorderLayout.EAST);
        }
        return hdr;
    }

    /**
     * Godot 4 / Unreal 5 2-Column Property Inspector Row (clean fixed-width label left, stretch control right).
     */
    public static JPanel createPropertyRow(String label, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(30)));
        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_UI);
        lbl.setForeground(C_TEXT_LABEL);
        lbl.setPreferredSize(new Dimension(scaled(92), scaled(26)));
        row.add(lbl, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        return row;
    }

    // =========================================================================
    // 12. VortexNumberField — Visual Drag Scrubber + Stepper Arrows + Mouse Wheel
    // =========================================================================
    public static final class VortexNumberField extends JPanel {
        private double value;
        private final double step;
        private final double minVal;
        private final double maxVal;
        private final JTextField textField;
        private DoubleConsumer onChange;
        private boolean updatingText = false;

        public VortexNumberField(String axisBadge, Color badgeColor, double initial, double step, double minVal, double maxVal) {
            super(new BorderLayout(0, 0));
            this.value = initial;
            this.step = step > 0 ? step : 0.1;
            this.minVal = minVal;
            this.maxVal = maxVal;

            setBackground(C_INPUT_BG);
            setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
            setPreferredSize(new Dimension(scaled(86), scaled(26)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, scaled(28)));

            // Left: Draggable Axis / Scrub Handle Badge
            if (axisBadge != null && !axisBadge.isEmpty()) {
                JLabel scrub = new JLabel(axisBadge, JLabel.CENTER);
                scrub.setOpaque(true);
                scrub.setBackground(new Color(26, 26, 34));
                scrub.setForeground(badgeColor != null ? badgeColor : C_TEXT_LABEL);
                scrub.setFont(FONT_SMALL_BOLD);
                scrub.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 0, 1, C_BORDER_SUBTLE),
                        BorderFactory.createEmptyBorder(0, scaled(6), 0, scaled(6))));
                scrub.setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                scrub.setToolTipText("Drag to scrub (Shift=fine, Ctrl=fast)");
                installDragScrub(scrub);
                add(scrub, BorderLayout.WEST);
            }

            // Center: Direct Numeric Input
            textField = new JTextField(formatVal(initial));
            textField.setBackground(C_INPUT_BG);
            textField.setForeground(C_WHITE);
            textField.setCaretColor(C_WHITE);
            textField.setSelectionColor(C_ACCENT_TINT);
            textField.setSelectedTextColor(C_WHITE);
            textField.setFont(FONT_MONO);
            textField.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
            textField.setToolTipText("Enter value, scroll wheel, or drag");
            textField.addActionListener(e -> commitTyped());
            textField.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent e) {
                    setBorder(BorderFactory.createLineBorder(C_WHITE, 1));
                    SwingUtilities.invokeLater(textField::selectAll);
                }
                @Override public void focusLost(FocusEvent e) {
                    setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
                    commitTyped();
                }
            });
            add(textField, BorderLayout.CENTER);

            // Right: Draggable & Clickable Stepper Arrows (◀ / ▶)
            JComponent arrows = new JComponent() {
                private int hoverPart = 0; // -1 left, +1 right
                {
                    setPreferredSize(new Dimension(scaled(22), scaled(24)));
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                    setToolTipText("Click ◀ / ▶ to step, or drag horizontally to scrub");
                    addMouseListener(new MouseAdapter() {
                        private Point pressPt;
                        private boolean dragged;
                        @Override public void mousePressed(MouseEvent e) {
                            pressPt = e.getPoint();
                            dragged = false;
                        }
                        @Override public void mouseReleased(MouseEvent e) {
                            if (!dragged && pressPt != null) {
                                double mult = e.isShiftDown() ? 0.1 : (e.isControlDown() ? 10.0 : 1.0);
                                double dir = e.getX() < getWidth() / 2 ? -1.0 : 1.0;
                                setValueInternal(value + dir * VortexNumberField.this.step * mult, true);
                            }
                        }
                        @Override public void mouseExited(MouseEvent e) { hoverPart = 0; repaint(); }
                    });
                    addMouseMotionListener(new MouseMotionAdapter() {
                        private int lastX = Integer.MIN_VALUE;
                        @Override public void mouseMoved(MouseEvent e) {
                            hoverPart = e.getX() < getWidth() / 2 ? -1 : 1;
                            repaint();
                            lastX = Integer.MIN_VALUE;
                        }
                        @Override public void mouseDragged(MouseEvent e) {
                            if (lastX != Integer.MIN_VALUE) {
                                int dx = e.getX() - lastX;
                                if (dx != 0) {
                                    double mult = e.isShiftDown() ? 0.1 : (e.isControlDown() ? 5.0 : 1.0);
                                    setValueInternal(value + dx * VortexNumberField.this.step * 0.5 * mult, true);
                                }
                            }
                            lastX = e.getX();
                        }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    enableAA(g2);
                    int w = getWidth(), h = getHeight();
                    g2.setColor(new Color(22, 22, 29));
                    g2.fillRect(0, 0, w, h);
                    g2.setColor(C_BORDER_SUBTLE);
                    g2.drawLine(0, 0, 0, h);
                    int mid = w / 2;
                    int cy = h / 2;
                    // Left arrow ◀
                    Path2D l = new Path2D.Float();
                    l.moveTo(mid - 2, cy - 3.2f);
                    l.lineTo(mid - 6, cy);
                    l.lineTo(mid - 2, cy + 3.2f);
                    l.closePath();
                    g2.setColor(hoverPart == -1 ? C_WHITE : C_TEXT_MUTED);
                    g2.fill(l);
                    // Right arrow ▶
                    Path2D r = new Path2D.Float();
                    r.moveTo(mid + 2, cy - 3.2f);
                    r.lineTo(mid + 6, cy);
                    r.lineTo(mid + 2, cy + 3.2f);
                    r.closePath();
                    g2.setColor(hoverPart == 1 ? C_WHITE : C_TEXT_MUTED);
                    g2.fill(r);
                    g2.dispose();
                }
            };
            add(arrows, BorderLayout.EAST);

            // Mouse Wheel Tweaking across entire control!
            java.awt.event.MouseWheelListener wheel = (MouseWheelEvent e) -> {
                double rot = -e.getPreciseWheelRotation();
                if (rot == 0) rot = -e.getWheelRotation();
                double mult = e.isShiftDown() ? 0.1 : (e.isControlDown() ? 10.0 : 1.0);
                setValueInternal(value + (rot > 0 ? 1 : -1) * this.step * mult, true);
                e.consume();
            };
            this.addMouseWheelListener(wheel);
            textField.addMouseWheelListener(wheel);
            arrows.addMouseWheelListener(wheel);
        }

        private void installDragScrub(JComponent c) {
            final int[] prevX = {Integer.MIN_VALUE};
            c.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { prevX[0] = e.getXOnScreen(); }
                @Override public void mouseReleased(MouseEvent e) { prevX[0] = Integer.MIN_VALUE; }
            });
            c.addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseDragged(MouseEvent e) {
                    int cur = e.getXOnScreen();
                    if (prevX[0] != Integer.MIN_VALUE) {
                        int dx = cur - prevX[0];
                        if (dx != 0) {
                            double mult = e.isShiftDown() ? 0.1 : (e.isControlDown() ? 5.0 : 1.0);
                            setValueInternal(value + dx * step * 0.5 * mult, true);
                        }
                    }
                    prevX[0] = cur;
                }
            });
        }

        private void commitTyped() {
            if (updatingText) return;
            try {
                double parsed = Double.parseDouble(textField.getText().trim());
                setValueInternal(parsed, true);
            } catch (Exception ignored) {
                updatingText = true;
                textField.setText(formatVal(value));
                updatingText = false;
            }
        }

        private void setValueInternal(double next, boolean notify) {
            double clamped = Math.max(minVal, Math.min(maxVal, next));
            if (Math.abs(clamped) < 1e-6) clamped = 0.0;
            this.value = clamped;
            updatingText = true;
            textField.setText(formatVal(clamped));
            updatingText = false;
            if (notify && onChange != null) onChange.accept(clamped);
        }

        private static String formatVal(double v) {
            if (Math.abs(v - Math.round(v)) < 0.001 && Math.abs(v) >= 100) {
                return String.format(Locale.US, "%.0f", v);
            }
            return String.format(Locale.US, "%.2f", v);
        }

        public float getFloatValue() {
            commitTyped();
            return (float) value;
        }

        public void setValue(double v) {
            setValueInternal(v, false);
        }

        public void setOnChange(DoubleConsumer c) {
            this.onChange = c;
        }
    }

    // =========================================================================
    // 13. AccordionSection — Collapsible Inspector Property Folder
    // =========================================================================
    public static final class AccordionSection extends JPanel {
        private boolean expanded;
        private final JPanel bodyPanel;
        private Consumer<Boolean> onToggle;

        public AccordionSection(String title, boolean initiallyExpanded) {
            super(new BorderLayout(0, 0));
            this.expanded = initiallyExpanded;
            setOpaque(true);
            setBackground(C_PANEL_ALT);
            setBorder(BorderFactory.createLineBorder(C_BORDER, 1));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel header = new JPanel(new BorderLayout(6, 0)) {
                private boolean hov = false;
                {
                    setOpaque(true);
                    setBackground(C_HEADER);
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setBorder(BorderFactory.createEmptyBorder(scaled(6), scaled(9), scaled(6), scaled(9)));
                    addMouseListener(new MouseAdapter() {
                        @Override public void mouseEntered(MouseEvent e) { hov = true; setBackground(C_SURFACE); repaint(); }
                        @Override public void mouseExited(MouseEvent e) { hov = false; setBackground(C_HEADER); repaint(); }
                        @Override public void mousePressed(MouseEvent e) {
                            setExpanded(!expanded);
                            if (onToggle != null) onToggle.accept(expanded);
                        }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (expanded) {
                        g.setColor(C_BORDER_SUBTLE);
                        g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                    }
                }
            };

            final JLabel chev = new JLabel(expanded ? "▾" : "▸");
            chev.setFont(FONT_UI_BOLD);
            chev.setForeground(C_WHITE);
            header.add(chev, BorderLayout.WEST);

            JLabel ttl = new JLabel(title);
            ttl.setFont(FONT_UI_BOLD);
            ttl.setForeground(C_WHITE);
            header.add(ttl, BorderLayout.CENTER);

            add(header, BorderLayout.NORTH);

            bodyPanel = new JPanel();
            bodyPanel.setLayout(new BoxLayout(bodyPanel, BoxLayout.Y_AXIS));
            bodyPanel.setBackground(C_PANEL_ALT);
            bodyPanel.setBorder(BorderFactory.createEmptyBorder(scaled(7), scaled(8), scaled(8), scaled(8)));
            bodyPanel.setVisible(expanded);
            add(bodyPanel, BorderLayout.CENTER);

            header.addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    chev.setText(expanded ? "▾" : "▸");
                }
            });
        }

        public JPanel getBody() { return bodyPanel; }
        public boolean isExpanded() { return expanded; }

        public void setExpanded(boolean exp) {
            this.expanded = exp;
            bodyPanel.setVisible(exp);
            revalidate();
            if (getParent() != null) {
                getParent().revalidate();
                getParent().repaint();
            }
            repaint();
        }

        public void setOnToggle(Consumer<Boolean> listener) {
            this.onToggle = listener;
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension pref = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, pref.height);
        }
    }

    // =========================================================================
    // 14. VortexCodeTab — Seamless IDE File Tab (Active Top Bar + Clear × Close)
    // =========================================================================
    public static final class VortexCodeTab extends JComponent {
        private final String filePath;
        private final String shortName;
        private final boolean active;
        private final boolean closable;
        private boolean tabHover = false;
        private boolean closeHover = false;

        public VortexCodeTab(String filePath, boolean active, boolean closable,
                             Runnable onSelect, Runnable onClose) {
            this.filePath = filePath;
            this.shortName = filePath.contains("/") ? filePath.substring(filePath.lastIndexOf('/') + 1) : filePath;
            this.active = active;
            this.closable = closable;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(filePath + " — Click to switch • Middle-click or × to close • Drag onto Viewport to snap split");

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { tabHover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { tabHover = false; closeHover = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (SwingUtilities.isMiddleMouseButton(e) && closable && onClose != null) {
                        onClose.run();
                        return;
                    }
                    if (closable && getCloseRect().contains(e.getPoint())) {
                        if (onClose != null) onClose.run();
                        return;
                    }
                    if (onSelect != null) onSelect.run();
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    boolean overX = closable && getCloseRect().contains(e.getPoint());
                    if (overX != closeHover) {
                        closeHover = overX;
                        repaint();
                    }
                }
            });
        }

        private Rectangle getCloseRect() {
            int s = scaled(16);
            return new Rectangle(getWidth() - s - scaled(6), (getHeight() - s) / 2, s, s);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(active ? FONT_UI_BOLD : FONT_UI);
            int w = scaled(16) + fm.stringWidth(shortName) + (closable ? scaled(21) : scaled(8));
            return new Dimension(w, scaled(28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth(), h = getHeight();

            Color bg = active ? C_SURFACE : (tabHover ? new Color(29, 29, 37) : new Color(16, 16, 21));
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 1, w - 1, h + 4, 6, 6));

            g2.setColor(active ? C_BORDER_STRONG : C_BORDER_SUBTLE);
            g2.draw(new RoundRectangle2D.Float(0.5f, 1.5f, w - 1.5f, h + 3f, 6, 6));

            if (active) {
                // Obvious crisp white active-tab top indicator bar!
                g2.setColor(C_WHITE);
                g2.fill(new RoundRectangle2D.Float(1, 1, w - 2, 2.6f, 2, 2));
            }

            // File dot indicator
            g2.setColor(active ? C_OK_TX : C_TEXT_MUTED);
            g2.fill(new Ellipse2D.Float(scaled(8), h / 2f - 2.5f, 5f, 5f));

            g2.setFont(active ? FONT_UI_BOLD : FONT_UI);
            g2.setColor(active ? C_WHITE : (tabHover ? C_TEXT : C_TEXT_MUTED));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(shortName, scaled(17), (h - fm.getHeight()) / 2 + fm.getAscent() + 1);

            if (closable) {
                Rectangle xr = getCloseRect();
                if (closeHover) {
                    g2.setColor(C_DANGER_BG);
                    g2.fill(new RoundRectangle2D.Float(xr.x, xr.y, xr.width, xr.height, 5, 5));
                    g2.setColor(C_DANGER_BD);
                    g2.draw(new RoundRectangle2D.Float(xr.x, xr.y, xr.width, xr.height, 5, 5));
                }
                paintIcon(g2, Icon.CLOSE, xr.x + xr.width * 0.5f, xr.y + xr.height * 0.5f,
                        9.5f, closeHover ? C_DANGER_TX : (active ? C_TEXT_LABEL : C_TEXT_MUTED));
            }
            g2.dispose();
        }
    }

    // =========================================================================
    // 15. VortexLogPane & StatusPill — Color-Coded Build/Script Feedback
    // =========================================================================
    public static final class VortexLogPane extends JTextPane {
        private static final int MAX_DOC_CHARS = 26_000;

        public VortexLogPane() {
            setEditable(false);
            setBackground(C_VOID);
            setForeground(C_TEXT);
            setCaretColor(C_WHITE);
            setFont(FONT_MONO);
            setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        }

        public void appendColoredLine(String line) {
            if (line == null) return;
            StyledDocument doc = getStyledDocument();
            try {
                if (doc.getLength() > MAX_DOC_CHARS) {
                    doc.remove(0, doc.getLength() - 16_000);
                }
                for (String sub : line.split("\n", -1)) {
                    appendSingleLine(doc, sub);
                }
                setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        }

        private void appendSingleLine(StyledDocument doc, String s) throws BadLocationException {
            String u = s.toUpperCase(Locale.US);
            SimpleAttributeSet attr = new SimpleAttributeSet();
            StyleConstants.setFontFamily(attr, MONO_FAMILY);
            StyleConstants.setFontSize(attr, FONT_MONO.getSize());

            boolean isErr = u.contains("[ERROR]") || u.contains("[FAIL") || u.contains("[CRASH]")
                    || s.startsWith("✕") || u.contains("SYNTAXERROR") || u.contains("EXCEPTION")
                    || u.contains("FAILED:") || u.contains("ERROR:");
            boolean isOk = !isErr && (u.contains("[SUCCESS]") || u.contains("[PASS]") || s.contains("✓")
                    || u.contains("[SAVED]") || u.contains("[EXPORT]") || u.contains("STARTED LIVE PLAY")
                    || u.contains("COMPILE OK") || u.contains("EXIT 0"));
            boolean isWarn = !isErr && !isOk && (u.contains("[WARN") || u.contains("STOPPED PLAY")
                    || u.contains("UNDID") || u.contains("REDID"));

            if (isErr) {
                StyleConstants.setForeground(attr, C_DANGER_TX);
                StyleConstants.setBackground(attr, new Color(36, 13, 17));
                StyleConstants.setBold(attr, true);
            } else if (isOk) {
                StyleConstants.setForeground(attr, C_OK_TX);
                StyleConstants.setBold(attr, true);
            } else if (isWarn) {
                StyleConstants.setForeground(attr, C_WARN_TX);
            } else if (s.trim().startsWith("•")) {
                StyleConstants.setForeground(attr, C_TEXT_LABEL);
            } else {
                StyleConstants.setForeground(attr, C_TEXT);
            }
            doc.insertString(doc.getLength(), s + "\n", attr);
        }

        public void clearLogs() {
            super.setText("");
        }
    }

    public static final class StatusPill extends JComponent {
        public enum State { NEUTRAL, SUCCESS, ERROR, WARNING }
        private State state = State.SUCCESS;
        private String text = "✓ Ready";

        public StatusPill(String initialText, State initialState) {
            this.text = initialText;
            this.state = initialState;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        public void setStatus(String message, State nextState) {
            this.text = message == null ? "" : message;
            this.state = nextState == null ? State.NEUTRAL : nextState;
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(FONT_UI_BOLD);
            int w = Math.min(scaled(310), fm.stringWidth(text) + scaled(22));
            return new Dimension(Math.max(scaled(90), w), scaled(25));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            enableAA(g2);
            int w = getWidth(), h = getHeight();
            Color bg, bd, fg;
            switch (state) {
                case SUCCESS:
                    bg = C_OK_BG; bd = C_OK_BD; fg = C_OK_TX; break;
                case ERROR:
                    bg = C_ERR_BG_SAFE(); bd = C_DANGER_BD; fg = C_DANGER_TX; break;
                case WARNING:
                    bg = C_WARN_BG; bd = C_WARN_TX; fg = C_WARN_TX; break;
                default:
                    bg = C_SURFACE; bd = C_BORDER; fg = C_TEXT_LABEL; break;
            }
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h));
            g2.setColor(bd);
            g2.setStroke(new BasicStroke(1.1f));
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, h, h));

            g2.setFont(FONT_UI_BOLD);
            g2.setColor(fg);
            FontMetrics fm = g2.getFontMetrics();
            String drawTxt = text;
            int maxW = w - scaled(16);
            while (drawTxt.length() > 4 && fm.stringWidth(drawTxt) > maxW) {
                drawTxt = drawTxt.substring(0, drawTxt.length() - 2) + "…";
            }
            g2.drawString(drawTxt, (w - fm.stringWidth(drawTxt)) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }

        private static Color C_ERR_BG_SAFE() {
            return new Color(46, 16, 21);
        }
    }
}
