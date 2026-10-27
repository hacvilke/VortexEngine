package com.vortex.engine.desktop;

import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.PixelSpriteCodec;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Interactive 16x16 Desktop Pixel Art Painter with Mirror-X symmetry & 1-Click Scene Spawning.
 */
public class DesktopPixelArtDialog extends JDialog {

    public interface OnSpriteSaved {
        void onSaved(String spriteName, boolean spawnInScene);
    }

    private final int[] pixels = new int[PixelSpriteCodec.SIZE * PixelSpriteCodec.SIZE];
    private int brushArgb = 0xFF00E5FF;
    private boolean mirrorX = false;

    public DesktopPixelArtDialog(JFrame owner, GameProject project, OnSpriteSaved callback) {
        super(owner, "Vortex 16x16 Pixel Art Sprite Studio", true);
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(13, 19, 32));

        String initName = "hero_sprite";
        String initEncoded = PixelSpriteCodec.createDefaultKnightSprite();
        if (!project.customSprites.isEmpty()) {
            Map.Entry<String, String> first = project.customSprites.entrySet().iterator().next();
            initName = first.getKey();
            initEncoded = first.getValue();
        }
        System.arraycopy(PixelSpriteCodec.decodeToArgbPixels(initEncoded), 0, pixels, 0, pixels.length);

        // Top Bar
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.setBackground(new Color(19, 28, 46));

        JLabel lbl = new JLabel("Sprite ID:");
        lbl.setForeground(Color.WHITE);
        top.add(lbl);

        JTextField nameField = new JTextField(initName, 14);
        top.add(nameField);

        JCheckBox mirrorCb = new JCheckBox("Mirror-X Symmetry");
        mirrorCb.setForeground(Color.WHITE);
        mirrorCb.setOpaque(false);
        mirrorCb.addActionListener(e -> mirrorX = mirrorCb.isSelected());
        top.add(mirrorCb);

        JPanel canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                int size = Math.min(getWidth(), getHeight()) - 16;
                int ox = (getWidth() - size) / 2;
                int oy = (getHeight() - size) / 2;
                float cell = size / (float) PixelSpriteCodec.SIZE;

                for (int y = 0; y < PixelSpriteCodec.SIZE; y++) {
                    for (int x = 0; x < PixelSpriteCodec.SIZE; x++) {
                        int x0 = Math.round(ox + x * cell);
                        int y0 = Math.round(oy + y * cell);
                        int cw = Math.max(1, Math.round(cell));
                        g2.setColor(((x + y) % 2 == 0) ? new Color(21, 30, 45) : new Color(28, 40, 59));
                        g2.fillRect(x0, y0, cw, cw);

                        int argb = pixels[y * PixelSpriteCodec.SIZE + x];
                        if (((argb >>> 24) & 0xFF) > 32) {
                            g2.setColor(new Color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF));
                            g2.fillRect(x0, y0, cw, cw);
                        }
                        g2.setColor(new Color(65, 85, 110, 90));
                        g2.drawRect(x0, y0, cw, cw);
                    }
                }
            }
        };
        canvas.setPreferredSize(new Dimension(420, 420));
        canvas.setBackground(new Color(9, 13, 22));

        MouseAdapter painter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { paintAt(e); }
        };
        canvas.addMouseListener(painter);
        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) { paintAt(e); }
        });

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> {
            for (int i = 0; i < pixels.length; i++) pixels[i] = 0;
            canvas.repaint();
        });
        top.add(clearBtn);

        JButton knightBtn = new JButton("Knight Preset");
        knightBtn.addActionListener(e -> {
            System.arraycopy(PixelSpriteCodec.decodeToArgbPixels(PixelSpriteCodec.createDefaultKnightSprite()), 0, pixels, 0, pixels.length);
            canvas.repaint();
        });
        top.add(knightBtn);

        JButton crateBtn = new JButton("Crate Preset");
        crateBtn.addActionListener(e -> {
            System.arraycopy(PixelSpriteCodec.decodeToArgbPixels(PixelSpriteCodec.createCrateSprite()), 0, pixels, 0, pixels.length);
            canvas.repaint();
        });
        top.add(crateBtn);

        add(top, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        // Palette + Save South Panel
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(19, 28, 46));

        JPanel pal = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 6));
        pal.setOpaque(false);
        int[] cols = {
                0xFF00E5FF, 0xFF00E676, 0xFFFFD740, 0xFFFF5252,
                0xFFFF4081, 0xFF7C4DFF, 0xFFFF9100, 0xFF8D6E63,
                0xFFFFFFFF, 0xFF94A3B8, 0xFF334155, 0xFF090D16, 0x00000000
        };
        for (int c : cols) {
            JButton sw = new JButton(c == 0 ? "ERASE" : "  ");
            if (c != 0) {
                sw.setBackground(new Color((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF));
                sw.setOpaque(true);
                sw.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1));
            }
            sw.addActionListener(e -> brushArgb = c);
            pal.add(sw);
        }
        south.add(pal, BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        actions.setOpaque(false);

        JButton saveOnly = new JButton("Save Asset");
        saveOnly.addActionListener(e -> {
            String nm = nameField.getText().trim().isEmpty() ? "sprite_1" : nameField.getText().trim();
            project.customSprites.put(nm, PixelSpriteCodec.encodeFromArgbPixels(pixels));
            if (callback != null) callback.onSaved(nm, false);
            dispose();
        });
        actions.add(saveOnly);

        JButton saveSpawn = new JButton("✓ Save & Spawn in 2D Scene");
        saveSpawn.setBackground(new Color(0, 200, 83));
        saveSpawn.setForeground(Color.BLACK);
        saveSpawn.addActionListener(e -> {
            String nm = nameField.getText().trim().isEmpty() ? "sprite_1" : nameField.getText().trim();
            project.customSprites.put(nm, PixelSpriteCodec.encodeFromArgbPixels(pixels));
            if (callback != null) callback.onSaved(nm, true);
            dispose();
        });
        actions.add(saveSpawn);

        south.add(actions, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void paintAt(MouseEvent e) {
        JPanel canvas = (JPanel) e.getComponent();
        int size = Math.min(canvas.getWidth(), canvas.getHeight()) - 16;
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
