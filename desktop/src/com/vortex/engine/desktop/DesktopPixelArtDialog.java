package com.vortex.engine.desktop;

import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Interactive 16x16 Desktop Pixel Art Painter (Embedded Tab + Standalone Modal)
 * Styled with the unified Vortex Obsidian + Electric Cyan UI system.
 */
public class DesktopPixelArtDialog extends JDialog {

    public interface OnSpriteSaved {
        void onSaved(String spriteName, boolean spawnInScene);
    }

    public DesktopPixelArtDialog(JFrame owner, GameProject project, OnSpriteSaved callback) {
        super(owner, "Vortex 16x16 Pixel Art Sprite Studio", true);
        setContentPane(createEmbeddedPanel(() -> project, (nm, spawn) -> {
            if (callback != null) callback.onSaved(nm, spawn);
            dispose();
        }));
        pack();
        setLocationRelativeTo(owner);
    }

    public static JPanel createEmbeddedPanel(Supplier<GameProject> projectSupplier, OnSpriteSaved callback) {
        final int[] pixels = new int[PixelSpriteCodec.SIZE * PixelSpriteCodec.SIZE];
        final int[] brushRef = {0xFFFFFFFF};
        final boolean[] mirrorRef = {false};

        GameProject initProj = projectSupplier.get();
        String initName = "hero_sprite";
        String initEncoded = PixelSpriteCodec.createDefaultKnightSprite();
        if (initProj != null && !initProj.customSprites.isEmpty()) {
            Map.Entry<String, String> first = initProj.customSprites.entrySet().iterator().next();
            initName = first.getKey();
            initEncoded = first.getValue();
        }
        System.arraycopy(PixelSpriteCodec.decodeToArgbPixels(initEncoded), 0, pixels, 0, pixels.length);

        JPanel root = new JPanel(new BorderLayout(0, 6));
        root.setBackground(DesktopUiKit.C_VOID);
        root.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));

        // Top Bar (2 clean rows so narrow dock widths never clip Preset dropdown!)
        JPanel top = new JPanel(new GridLayout(2, 1, 0, 4));
        top.setBackground(DesktopUiKit.C_PANEL);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));

        JPanel topRow1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        topRow1.setOpaque(false);
        JPanel topRow2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        topRow2.setOpaque(false);

        JLabel lbl = new JLabel("Sprite ID:");
        lbl.setForeground(DesktopUiKit.C_TEXT_MUTED);
        lbl.setFont(DesktopUiKit.FONT_UI_BOLD);
        topRow1.add(lbl);

        JTextField nameField = DesktopUiKit.createField(initName);
        nameField.setColumns(10);
        topRow1.add(nameField);

        DesktopUiKit.VortexToggle mirrorToggle = new DesktopUiKit.VortexToggle("Mirror-X Symmetry", false);
        mirrorToggle.setOnChange(() -> mirrorRef[0] = mirrorToggle.isSelected());
        topRow2.add(mirrorToggle);

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                int size = Math.max(64, Math.min(getWidth(), getHeight()) - 14);
                int ox = (getWidth() - size) / 2;
                int oy = (getHeight() - size) / 2;
                float cell = size / (float) PixelSpriteCodec.SIZE;

                for (int y = 0; y < PixelSpriteCodec.SIZE; y++) {
                    for (int x = 0; x < PixelSpriteCodec.SIZE; x++) {
                        int x0 = Math.round(ox + x * cell);
                        int y0 = Math.round(oy + y * cell);
                        int cw = Math.max(1, Math.round(cell));
                        g2.setColor(((x + y) % 2 == 0) ? new Color(18, 18, 22) : new Color(26, 26, 31));
                        g2.fillRect(x0, y0, cw, cw);

                        int argb = pixels[y * PixelSpriteCodec.SIZE + x];
                        if (((argb >>> 24) & 0xFF) > 32) {
                            g2.setColor(new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
                            g2.fillRect(x0, y0, cw, cw);
                        }
                        g2.setColor(new Color(52, 52, 60, 95));
                        g2.drawRect(x0, y0, cw, cw);
                    }
                }
                g2.setColor(DesktopUiKit.C_BORDER);
                g2.drawRect(ox, oy, Math.round(cell * PixelSpriteCodec.SIZE), Math.round(cell * PixelSpriteCodec.SIZE));
                g2.dispose();
            }
        };
        canvas.setPreferredSize(new Dimension(340, 320));
        canvas.setBackground(DesktopUiKit.C_VOID);

        Runnable paintHelper = () -> canvas.repaint();
        MouseAdapter painter = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { applyBrush(canvas, pixels, brushRef[0], mirrorRef[0], e); }
        };
        canvas.addMouseListener(painter);
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) { applyBrush(canvas, pixels, brushRef[0], mirrorRef[0], e); }
        });

        DesktopUiKit.VortexButton clearBtn = new DesktopUiKit.VortexButton("✕ Clear Grid", DesktopUiKit.Variant.DANGER);
        clearBtn.addActionListener(e -> {
            for (int i = 0; i < pixels.length; i++) pixels[i] = 0;
            paintHelper.run();
        });
        topRow2.add(clearBtn);

        String[] presetLabels = {"Preset: Knight", "Preset: Knight Run 2", "Preset: Cyber Drone", "Preset: Crate", "Preset: Gem"};
        DesktopUiKit.VortexDropdown presetDrop = new DesktopUiKit.VortexDropdown(presetLabels, presetLabels[0]);
        presetDrop.setPreferredSize(new Dimension(168, 28));
        presetDrop.setOnSelect((idx, val) -> {
            String enc;
            if (idx == 0) { enc = PixelSpriteCodec.createDefaultKnightSprite(); nameField.setText("knight_1"); }
            else if (idx == 1) { enc = PixelSpriteCodec.createKnightRunFrame2Sprite(); nameField.setText("knight_2"); }
            else if (idx == 2) { enc = PixelSpriteCodec.createDroneFrame1Sprite(); nameField.setText("drone_1"); }
            else if (idx == 3) { enc = PixelSpriteCodec.createCrateSprite(); nameField.setText("crate"); }
            else { enc = PixelSpriteCodec.createGemSprite(); nameField.setText("gem"); }
            System.arraycopy(PixelSpriteCodec.decodeToArgbPixels(enc), 0, pixels, 0, pixels.length);
            paintHelper.run();
        });
        topRow1.add(presetDrop);

        top.add(topRow1);
        top.add(topRow2);
        root.add(top, BorderLayout.NORTH);
        root.add(canvas, BorderLayout.CENTER);

        // Palette + Actions
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);

        JPanel pal = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 4));
        pal.setBackground(DesktopUiKit.C_PANEL);
        pal.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));
        int[] cols = {
                0xFFFFFFFF, 0xFFD4D4D8, 0xFF71717A, 0xFF18181B,
                0xFF22C55E, 0xFFFACC15, 0xFFEF4444, 0xFFF97316,
                0xFFEC4899, 0xFFA855F7, 0xFF14B8A6, 0xFF8D6E63, 0x00000000
        };
        for (int c : cols) {
            JComponent sw = new JComponent() {
                {
                    setPreferredSize(new Dimension(c == 0 ? 58 : 22, 22));
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    addMouseListener(new MouseAdapter() {
                        @Override public void mousePressed(MouseEvent e) {
                            brushRef[0] = c;
                            pal.repaint();
                        }
                    });
                }
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean active = (brushRef[0] == c);
                    RoundRectangle2D rr = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 6, 6);
                    g2.setColor(c == 0 ? DesktopUiKit.C_SURFACE : new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF));
                    g2.fill(rr);
                    g2.setStroke(new BasicStroke(active ? 2.0f : 1.0f));
                    g2.setColor(active ? DesktopUiKit.C_CYAN : DesktopUiKit.C_BORDER);
                    g2.draw(rr);
                    if (c == 0) {
                        g2.setFont(DesktopUiKit.FONT_UI_BOLD);
                        g2.setColor(DesktopUiKit.C_TEXT_MUTED);
                        g2.drawString("ERASE", 5, 15);
                    }
                    g2.dispose();
                }
            };
            pal.add(sw);
        }
        south.add(pal, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        actions.setOpaque(false);

        DesktopUiKit.VortexButton saveOnly = new DesktopUiKit.VortexButton("Save Sprite Asset", DesktopUiKit.Variant.SECONDARY);
        saveOnly.addActionListener(e -> {
            GameProject p = projectSupplier.get();
            String nm = nameField.getText().trim().isEmpty() ? "sprite_1" : nameField.getText().trim();
            p.customSprites.put(nm, PixelSpriteCodec.encodeFromArgbPixels(pixels));
            if (callback != null) callback.onSaved(nm, false);
        });
        actions.add(saveOnly);

        DesktopUiKit.VortexButton saveSpawn = new DesktopUiKit.VortexButton("✓ Save & Spawn in 2D", DesktopUiKit.Variant.PRIMARY);
        saveSpawn.addActionListener(e -> {
            GameProject p = projectSupplier.get();
            String nm = nameField.getText().trim().isEmpty() ? "sprite_1" : nameField.getText().trim();
            p.customSprites.put(nm, PixelSpriteCodec.encodeFromArgbPixels(pixels));
            if (callback != null) callback.onSaved(nm, true);
        });
        actions.add(saveSpawn);

        south.add(actions, BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private static void applyBrush(JPanel canvas, int[] pixels, int brushArgb, boolean mirrorX, MouseEvent e) {
        int size = Math.max(64, Math.min(canvas.getWidth(), canvas.getHeight()) - 14);
        int ox = (canvas.getWidth() - size) / 2;
        int oy = (canvas.getHeight() - size) / 2;
        float cell = size / (float) PixelSpriteCodec.SIZE;

        int gx = (int) Math.floor((e.getX() - ox) / cell);
        int gy = (int) Math.floor((e.getY() - oy) / cell);
        if (gx >= 0 && gx < PixelSpriteCodec.SIZE && gy >= 0 && gy < PixelSpriteCodec.SIZE) {
            int val = SwingUtilities.isRightMouseButton(e) ? 0 : brushArgb;
            pixels[gy * PixelSpriteCodec.SIZE + gx] = val;
            if (mirrorX) {
                pixels[gy * PixelSpriteCodec.SIZE + (PixelSpriteCodec.SIZE - 1 - gx)] = val;
            }
            canvas.repaint();
        }
    }
}
