package com.vortex.engine.desktop;

import com.vortex.engine.core.flow.VortexBlueprintGraph;
import com.vortex.engine.core.flow.VortexBlueprintGraph.BpLink;
import com.vortex.engine.core.flow.VortexBlueprintGraph.BpNode;
import com.vortex.engine.core.model.GameProject;
import static com.vortex.engine.desktop.DesktopUiKit.*;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Lightweight Unreal-Blueprint-Style Visual Event & GUI Diagram Canvas (v4.3).
 *
 * Works alongside the classic right-dock Flow Event-Sheet tab and multi-file scripts:
 * every visual node chain compiles automatically into Playtest, Desktop, Android APK, and HTML5 exports!
 * Palette is strictly zero-blue (monochrome carbon grid + crisp white Bezier wires + neutral/semantic headers).
 */
public final class DesktopBlueprintPanel extends JPanel {

    private static final int NODE_W = 216;
    private static final int NODE_H_STD = 92;
    private static final int NODE_H_BRANCH = 108;

    private GameProject project;
    private final Runnable onGraphMutated;

    private BpNode selectedNode = null;
    private BpNode draggingNode = null;
    private float dragOffsetX = 0f, dragOffsetY = 0f;

    // Wire dragging from output pin
    private BpNode wireFromNode = null;
    private String wireFromPin = "OUT";
    private Point wireMousePt = null;

    // Pan & Zoom
    private float panX = 24f, panY = 24f;
    private float zoom = 1.0f;
    private Point lastPanMouse = null;

    private final GraphCanvas canvas = new GraphCanvas();
    private final VortexDropdown paletteSelect;
    private final JLabel selTitleLbl = mkLabel("Select a Blueprint block to edit its parameters", true);
    private final JLabel p1Lbl = mkLabel("Param 1", false);
    private final JLabel p2Lbl = mkLabel("Param 2", false);
    private final JLabel p3Lbl = mkLabel("Param 3", false);
    private final JTextField p1Field = createField("");
    private final JTextField p2Field = createField("");
    private final JTextField p3Field = createField("");
    private boolean suppressFieldSync = false;

    private static JLabel mkLabel(String text, boolean bold) {
        JLabel l = new JLabel(text);
        l.setFont(bold ? FONT_UI_BOLD : FONT_UI);
        l.setForeground(bold ? C_WHITE : C_TEXT_MUTED);
        return l;
    }

    public DesktopBlueprintPanel(GameProject initialProject, Runnable onGraphMutated) {
        super(new BorderLayout(0, 0));
        this.project = initialProject;
        this.onGraphMutated = onGraphMutated;
        setBackground(C_VOID);
        setMinimumSize(new Dimension(220, 180));

        // Top Palette Toolbar (compact so all controls fit cleanly on 1366x768 laptop panes)
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, scaled(4), scaled(4)));
        topBar.setBackground(C_HEADER);
        topBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, C_BORDER));

        String[] labels = new String[VortexBlueprintGraph.NODE_PALETTE.length];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = "[" + VortexBlueprintGraph.NODE_PALETTE[i][1] + "] " + VortexBlueprintGraph.NODE_PALETTE[i][2];
        }
        paletteSelect = new VortexDropdown(labels, labels[0]);
        paletteSelect.setPreferredSize(new Dimension(scaled(168), scaled(25)));

        VortexButton addNodeBtn = new VortexButton("Add", Variant.PRIMARY, Icon.PLUS);
        addNodeBtn.addActionListener(e -> {
            int idx = Math.max(0, paletteSelect.getSelectedIndex());
            spawnNode(VortexBlueprintGraph.NODE_PALETTE[idx][0]);
        });

        VortexButton quickDialogBtn = new VortexButton("Dialog", Variant.ACCENT, Icon.GUI);
        quickDialogBtn.addActionListener(e -> spawnNode("GUI_SHOW_DIALOG"));

        VortexButton quickLabelBtn = new VortexButton("Label", Variant.SECONDARY, Icon.GUI);
        quickLabelBtn.addActionListener(e -> spawnNode("GUI_SET_LABEL"));

        VortexButton quickBarBtn = new VortexButton("Bar", Variant.SECONDARY, Icon.GUI);
        quickBarBtn.addActionListener(e -> spawnNode("GUI_SET_BAR"));

        VortexButton unlinkBtn = new VortexButton("Unwire", Variant.SECONDARY, Icon.CLOSE);
        unlinkBtn.setToolTipText("Remove wires attached to selected block");
        unlinkBtn.addActionListener(e -> {
            if (selectedNode != null && project != null) {
                String id = selectedNode.id;
                project.blueprintLinks.removeIf(l -> id.equals(l.fromNodeId) || id.equals(l.toNodeId));
                notifyChanged();
                canvas.repaint();
            }
        });

        VortexButton delNodeBtn = new VortexButton("Del", Variant.DANGER, Icon.CLOSE);
        delNodeBtn.addActionListener(e -> deleteSelectedNode());

        VortexButton resetViewBtn = new VortexButton("Center", Variant.SECONDARY, Icon.GRID);
        resetViewBtn.addActionListener(e -> {
            panX = 24f; panY = 24f; zoom = 1.0f;
            canvas.repaint();
        });

        topBar.add(paletteSelect);
        topBar.add(addNodeBtn);
        topBar.add(quickDialogBtn);
        topBar.add(quickLabelBtn);
        topBar.add(quickBarBtn);
        topBar.add(unlinkBtn);
        topBar.add(delNodeBtn);
        topBar.add(resetViewBtn);
        add(topBar, BorderLayout.NORTH);

        add(canvas, BorderLayout.CENTER);

        // Bottom Compact Selected-Block Property Bar
        JPanel propBar = new JPanel(new BorderLayout(scaled(6), scaled(4)));
        propBar.setBackground(C_HEADER);
        propBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, C_BORDER),
                BorderFactory.createEmptyBorder(scaled(5), scaled(8), scaled(6), scaled(8))));

        propBar.add(selTitleLbl, BorderLayout.NORTH);
        JPanel fieldsGrid = new JPanel(new GridLayout(1, 3, scaled(6), 0));
        fieldsGrid.setOpaque(false);
        fieldsGrid.add(wrapField(p1Lbl, p1Field));
        fieldsGrid.add(wrapField(p2Lbl, p2Field));
        fieldsGrid.add(wrapField(p3Lbl, p3Field));
        propBar.add(fieldsGrid, BorderLayout.CENTER);
        add(propBar, BorderLayout.SOUTH);

        DocumentListener dl = new DocumentListener() {
            private void sync() {
                if (suppressFieldSync || selectedNode == null) return;
                selectedNode.param1 = p1Field.getText();
                selectedNode.param2 = p2Field.getText();
                selectedNode.param3 = p3Field.getText();
                notifyChanged();
                canvas.repaint();
            }
            @Override public void insertUpdate(DocumentEvent e) { sync(); }
            @Override public void removeUpdate(DocumentEvent e) { sync(); }
            @Override public void changedUpdate(DocumentEvent e) { sync(); }
        };
        p1Field.getDocument().addDocumentListener(dl);
        p2Field.getDocument().addDocumentListener(dl);
        p3Field.getDocument().addDocumentListener(dl);

        bindProject(initialProject);
    }

    private JPanel wrapField(JLabel lbl, JTextField tf) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.add(lbl, BorderLayout.NORTH);
        p.add(tf, BorderLayout.CENTER);
        return p;
    }

    public void bindProject(GameProject p) {
        this.project = p;
        this.selectedNode = (p != null && !p.blueprintNodes.isEmpty()) ? p.blueprintNodes.get(0) : null;
        syncInspectorFromSelection();
        canvas.repaint();
    }

    private void spawnNode(String nodeType) {
        if (project == null) return;
        float gx = (Math.max(80, canvas.getWidth() / 2f) - panX) / zoom - NODE_W / 2f + (project.blueprintNodes.size() % 4) * 26f;
        float gy = (Math.max(80, canvas.getHeight() / 2f) - panY) / zoom - 44f + (project.blueprintNodes.size() % 4) * 22f;
        BpNode created = VortexBlueprintGraph.createByType(nodeType, Math.max(20f, gx), Math.max(20f, gy));
        // Auto-wire from currently selected node if compatible!
        if (selectedNode != null && created.hasExecIn()) {
            String pin = "OUT";
            boolean alreadyWired = false;
            for (BpLink l : project.blueprintLinks) {
                if (selectedNode.id.equals(l.fromNodeId) && pin.equals(l.fromPin)) { alreadyWired = true; break; }
            }
            if (!alreadyWired) {
                project.blueprintLinks.add(new BpLink(selectedNode.id, pin, created.id));
                created.x = selectedNode.x + NODE_W + 54f;
                created.y = selectedNode.y;
            }
        }
        project.blueprintNodes.add(created);
        selectedNode = created;
        syncInspectorFromSelection();
        notifyChanged();
        canvas.repaint();
    }

    private void deleteSelectedNode() {
        if (project == null || selectedNode == null) return;
        String id = selectedNode.id;
        project.blueprintNodes.remove(selectedNode);
        project.blueprintLinks.removeIf(l -> id.equals(l.fromNodeId) || id.equals(l.toNodeId));
        selectedNode = project.blueprintNodes.isEmpty() ? null : project.blueprintNodes.get(0);
        syncInspectorFromSelection();
        notifyChanged();
        canvas.repaint();
    }

    private void notifyChanged() {
        if (onGraphMutated != null) onGraphMutated.run();
    }

    private void syncInspectorFromSelection() {
        suppressFieldSync = true;
        try {
            if (selectedNode == null) {
                selTitleLbl.setText("No block selected — click a block or add GUI Dialog / Event / Action blocks above");
                p1Lbl.setText("Param 1"); p2Lbl.setText("Param 2"); p3Lbl.setText("Param 3");
                p1Field.setText(""); p2Field.setText(""); p3Field.setText("");
                p1Field.setEnabled(false); p2Field.setEnabled(false); p3Field.setEnabled(false);
                return;
            }
            p1Field.setEnabled(true); p2Field.setEnabled(true); p3Field.setEnabled(true);
            selTitleLbl.setText("[" + selectedNode.getCategory() + "] " + selectedNode.title + "  —  Drag right pin ▶ to wire execution flow");
            String[] labels = paramLabelsFor(selectedNode.type);
            p1Lbl.setText(labels[0]);
            p2Lbl.setText(labels[1]);
            p3Lbl.setText(labels[2]);
            p1Field.setText(selectedNode.param1 == null ? "" : selectedNode.param1);
            p2Field.setText(selectedNode.param2 == null ? "" : selectedNode.param2);
            p3Field.setText(selectedNode.param3 == null ? "" : selectedNode.param3);
        } finally {
            suppressFieldSync = false;
        }
    }

    private static String[] paramLabelsFor(String type) {
        if (type == null) return new String[]{"Param 1", "Param 2", "Param 3", "Param 4"};
        switch (type) {
            case "GUI_SHOW_DIALOG": return new String[]{"Speaker Title", "Dialog Message", "Duration (sec)", "Unused"};
            case "GUI_SET_LABEL":   return new String[]{"Widget ID", "Label Text", "Anchor X%,Y%", "Font Size"};
            case "GUI_SET_BAR":     return new String[]{"ID|Title", "Value", "Max Value", "Anchor X%,Y%"};
            case "GUI_SET_BANNER":  return new String[]{"Banner Text", "-", "-", "-"};
            case "EV_BUTTON":       return new String[]{"Button (A or B)", "-", "-", "-"};
            case "EV_COLLIDE":      return new String[]{"Tag A", "Tag B", "-", "-"};
            case "BR_SCORE_GTE":    return new String[]{"Min Score", "-", "-", "-"};
            case "BR_HEALTH_LTE":   return new String[]{"Max Health", "-", "-", "-"};
            case "BR_EXISTS":       return new String[]{"Entity Name", "-", "-", "-"};
            case "ACT_SPAWN_2D":    return new String[]{"Name", "Tag", "Primitive", "Color Hex"};
            case "ACT_PARTICLES":   return new String[]{"Around Entity", "Count", "Color Hex", "-"};
            case "ACT_JUMP":        return new String[]{"Entity Name", "Jump Impulse", "-", "-"};
            case "ACT_SHAKE":       return new String[]{"Intensity", "Duration (s)", "-", "-"};
            default:                return new String[]{"Value / Target", "Param 2", "Param 3", "Param 4"};
        }
    }

    // =========================================================================
    // Interactive Node + Bezier Wire Graph Canvas
    // =========================================================================
    private final class GraphCanvas extends JPanel {
        GraphCanvas() {
            setBackground(C_VOID);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (project == null) return;
                    if (SwingUtilities.isRightMouseButton(e) || SwingUtilities.isMiddleMouseButton(e)) {
                        // Right-clicking a pin disconnects its wires; otherwise starts canvas pan
                        Point2D.Float gp = toGraph(e.getPoint());
                        BpNode pinOwner = findPinDisconnectTarget(gp.x, gp.y);
                        if (pinOwner != null && SwingUtilities.isRightMouseButton(e)) {
                            String id = pinOwner.id;
                            project.blueprintLinks.removeIf(l -> id.equals(l.fromNodeId) || id.equals(l.toNodeId));
                            notifyChanged();
                            repaint();
                            return;
                        }
                        lastPanMouse = e.getPoint();
                        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                        return;
                    }

                    Point2D.Float gp = toGraph(e.getPoint());
                    // 1. Check output pins first so user can drag a wire!
                    for (int i = project.blueprintNodes.size() - 1; i >= 0; i--) {
                        BpNode n = project.blueprintNodes.get(i);
                        String hitOut = hitTestOutputPin(n, gp.x, gp.y);
                        if (hitOut != null) {
                            wireFromNode = n;
                            wireFromPin = hitOut;
                            wireMousePt = e.getPoint();
                            selectedNode = n;
                            syncInspectorFromSelection();
                            repaint();
                            return;
                        }
                    }

                    // 2. Check node body selection / drag
                    for (int i = project.blueprintNodes.size() - 1; i >= 0; i--) {
                        BpNode n = project.blueprintNodes.get(i);
                        int nh = n.isBranch() ? NODE_H_BRANCH : NODE_H_STD;
                        if (gp.x >= n.x && gp.x <= n.x + NODE_W && gp.y >= n.y && gp.y <= n.y + nh) {
                            selectedNode = n;
                            draggingNode = n;
                            dragOffsetX = gp.x - n.x;
                            dragOffsetY = gp.y - n.y;
                            syncInspectorFromSelection();
                            repaint();
                            return;
                        }
                    }

                    lastPanMouse = e.getPoint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    if (wireFromNode != null && project != null) {
                        Point2D.Float gp = toGraph(e.getPoint());
                        for (int i = project.blueprintNodes.size() - 1; i >= 0; i--) {
                            BpNode target = project.blueprintNodes.get(i);
                            if (target != wireFromNode && target.hasExecIn() && hitTestInputPin(target, gp.x, gp.y)) {
                                // Replace any existing wire from same (fromNodeId, fromPin)
                                final String fid = wireFromNode.id;
                                final String fpin = wireFromPin;
                                project.blueprintLinks.removeIf(l -> fid.equals(l.fromNodeId) && fpin.equals(l.fromPin));
                                project.blueprintLinks.add(new BpLink(fid, fpin, target.id));
                                notifyChanged();
                                break;
                            }
                        }
                    }
                    wireFromNode = null;
                    wireMousePt = null;
                    draggingNode = null;
                    lastPanMouse = null;
                    repaint();
                }
            });

            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (wireFromNode != null) {
                        wireMousePt = e.getPoint();
                        repaint();
                        return;
                    }
                    if (draggingNode != null) {
                        Point2D.Float gp = toGraph(e.getPoint());
                        draggingNode.x = Math.round((gp.x - dragOffsetX) / 8f) * 8f;
                        draggingNode.y = Math.round((gp.y - dragOffsetY) / 8f) * 8f;
                        notifyChanged();
                        repaint();
                        return;
                    }
                    if (lastPanMouse != null) {
                        panX += e.getX() - lastPanMouse.x;
                        panY += e.getY() - lastPanMouse.y;
                        lastPanMouse = e.getPoint();
                        repaint();
                    }
                }
            });

            addMouseWheelListener((MouseWheelEvent e) -> {
                float factor = e.getPreciseWheelRotation() < 0 ? 1.08f : 0.92f;
                zoom = Math.max(0.55f, Math.min(1.55f, zoom * factor));
                repaint();
            });
        }

        private Point2D.Float toGraph(Point p) {
            return new Point2D.Float((p.x - panX) / zoom, (p.y - panY) / zoom);
        }

        private Point2D.Float inputPinPos(BpNode n) {
            return new Point2D.Float(n.x + 12f, n.y + 44f);
        }

        private Point2D.Float outputPinPos(BpNode n, String pin) {
            if (n.isBranch()) {
                if ("FALSE".equalsIgnoreCase(pin)) return new Point2D.Float(n.x + NODE_W - 12f, n.y + 78f);
                return new Point2D.Float(n.x + NODE_W - 12f, n.y + 48f);
            }
            return new Point2D.Float(n.x + NODE_W - 12f, n.y + 44f);
        }

        private String hitTestOutputPin(BpNode n, float gx, float gy) {
            if (n.isBranch()) {
                Point2D.Float pT = outputPinPos(n, "TRUE");
                if (pT.distance(gx, gy) <= 13f) return "TRUE";
                Point2D.Float pF = outputPinPos(n, "FALSE");
                if (pF.distance(gx, gy) <= 13f) return "FALSE";
                return null;
            }
            Point2D.Float p = outputPinPos(n, "OUT");
            return p.distance(gx, gy) <= 13f ? "OUT" : null;
        }

        private boolean hitTestInputPin(BpNode n, float gx, float gy) {
            if (!n.hasExecIn()) return false;
            int nh = n.isBranch() ? NODE_H_BRANCH : NODE_H_STD;
            // Generous drop hit-box on left half or input pin
            return inputPinPos(n).distance(gx, gy) <= 18f
                    || (gx >= n.x && gx <= n.x + NODE_W * 0.45f && gy >= n.y && gy <= n.y + nh);
        }

        private BpNode findPinDisconnectTarget(float gx, float gy) {
            if (project == null) return null;
            for (BpNode n : project.blueprintNodes) {
                if (hitTestOutputPin(n, gx, gy) != null || (n.hasExecIn() && inputPinPos(n).distance(gx, gy) <= 14f)) {
                    return n;
                }
            }
            return null;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            paintGrid(g2, w, h);

            if (project == null) {
                g2.dispose();
                return;
            }

            Graphics2D wg = (Graphics2D) g2.create();
            wg.translate(panX, panY);
            wg.scale(zoom, zoom);

            // 1. Paint Bezier Execution Wires
            for (BpLink link : project.blueprintLinks) {
                BpNode from = findById(link.fromNodeId);
                BpNode to = findById(link.toNodeId);
                if (from == null || to == null) continue;
                Point2D.Float p0 = outputPinPos(from, link.fromPin);
                Point2D.Float p1 = inputPinPos(to);
                Color wireCol = "FALSE".equalsIgnoreCase(link.fromPin)
                        ? new Color(248, 113, 113, 230)
                        : new Color(255, 255, 255, 235);
                drawBezierWire(wg, p0.x, p0.y, p1.x, p1.y, wireCol, 2.4f);
            }

            // Live Dragging Wire Preview
            if (wireFromNode != null && wireMousePt != null) {
                Point2D.Float p0 = outputPinPos(wireFromNode, wireFromPin);
                Point2D.Float p1 = toGraph(wireMousePt);
                drawBezierWire(wg, p0.x, p0.y, p1.x, p1.y, new Color(255, 255, 255, 190), 2.0f);
            }

            // 2. Paint Blueprint Nodes
            for (BpNode n : project.blueprintNodes) {
                paintNode(wg, n, n == selectedNode);
            }
            wg.dispose();

            // Corner HUD Hint
            g2.setFont(DesktopUiKit.FONT_SMALL);
            g2.setColor(new Color(161, 161, 170, 210));
            g2.drawString("Blueprint Graph (" + project.blueprintNodes.size() + " blocks, "
                    + project.blueprintLinks.size() + " wires) • Drag ▶ pin to connect • Right-click pin to unwire • Wheel zoom", 12, h - 10);
            g2.dispose();
        }

        private void paintGrid(Graphics2D g2, int w, int h) {
            g2.setColor(new Color(10, 10, 13));
            g2.fillRect(0, 0, w, h);
            int step = Math.max(14, Math.round(24 * zoom));
            int ox = ((Math.round(panX) % step) + step) % step;
            int oy = ((Math.round(panY) % step) + step) % step;
            g2.setColor(new Color(24, 24, 29));
            for (int x = ox; x < w; x += step) g2.drawLine(x, 0, x, h);
            for (int y = oy; y < h; y += step) g2.drawLine(0, y, w, y);
            int major = step * 4;
            int mox = ((Math.round(panX) % major) + major) % major;
            int moy = ((Math.round(panY) % major) + major) % major;
            g2.setColor(new Color(36, 36, 43));
            for (int x = mox; x < w; x += major) g2.drawLine(x, 0, x, h);
            for (int y = moy; y < h; y += major) g2.drawLine(0, y, w, y);
        }

        private void drawBezierWire(Graphics2D g2, float x0, float y0, float x1, float y1, Color col, float width) {
            float tang = Math.max(48f, Math.abs(x1 - x0) * 0.48f);
            CubicCurve2D.Float curve = new CubicCurve2D.Float(x0, y0, x0 + tang, y0, x1 - tang, y1, x1, y1);
            g2.setColor(new Color(0, 0, 0, 120));
            g2.setStroke(new BasicStroke(width + 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curve);
            g2.setColor(col);
            g2.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(curve);
        }

        private void paintNode(Graphics2D g2, BpNode n, boolean sel) {
            int nh = n.isBranch() ? NODE_H_BRANCH : NODE_H_STD;
            RoundRectangle2D.Float card = new RoundRectangle2D.Float(n.x, n.y, NODE_W, nh, 10f, 10f);

            // Drop shadow
            g2.setColor(new Color(0, 0, 0, 130));
            g2.fill(new RoundRectangle2D.Float(n.x + 2f, n.y + 3f, NODE_W, nh, 10f, 10f));

            // Body
            g2.setColor(new Color(22, 22, 26, 244));
            g2.fill(card);

            // Semantic Category Header Band (strictly ZERO blue!)
            String cat = n.getCategory();
            Color hdrCol;
            if ("EVENT".equalsIgnoreCase(cat)) hdrCol = new Color(68, 28, 34);
            else if ("GUI".equalsIgnoreCase(cat)) hdrCol = new Color(46, 46, 54);
            else if ("BRANCH".equalsIgnoreCase(cat)) hdrCol = new Color(58, 46, 24);
            else hdrCol = new Color(34, 34, 40);

            g2.setClip(card);
            g2.setColor(hdrCol);
            g2.fillRect(Math.round(n.x), Math.round(n.y), NODE_W, 26);
            g2.setClip(null);

            g2.setColor(sel ? Color.WHITE : new Color(62, 62, 72));
            g2.setStroke(new BasicStroke(sel ? 1.8f : 1.0f));
            g2.draw(card);

            // Title + Category Pill
            g2.setFont(DesktopUiKit.FONT_SMALL_BOLD);
            g2.setColor(Color.WHITE);
            g2.drawString(trimToWidth(g2, n.title, NODE_W - 52), n.x + 10f, n.y + 17f);

            g2.setFont(new Font(DesktopUiKit.UI_FAMILY, Font.BOLD, Math.max(10, DesktopUiKit.FONT_SMALL.getSize() - 1)));
            int cw = g2.getFontMetrics().stringWidth(cat) + 8;
            g2.setColor(new Color(255, 255, 255, 32));
            g2.fillRoundRect(Math.round(n.x + NODE_W - cw - 7), Math.round(n.y + 6), cw, 14, 5, 5);
            g2.setColor(new Color(235, 235, 240));
            g2.drawString(cat, n.x + NODE_W - cw - 3f, n.y + 16f);

            // Input Exec Pin (▶)
            if (n.hasExecIn()) {
                Point2D.Float ip = inputPinPos(n);
                drawExecPin(g2, ip.x, ip.y, Color.WHITE);
            }

            // Output Exec Pin(s)
            if (n.isBranch()) {
                Point2D.Float pt = outputPinPos(n, "TRUE");
                Point2D.Float pf = outputPinPos(n, "FALSE");
                drawExecPin(g2, pt.x, pt.y, new Color(134, 239, 172));
                drawExecPin(g2, pf.x, pf.y, new Color(252, 165, 165));
                g2.setFont(new Font(DesktopUiKit.UI_FAMILY, Font.BOLD, Math.max(10, DesktopUiKit.FONT_SMALL.getSize() - 1)));
                g2.setColor(new Color(134, 239, 172));
                g2.drawString("TRUE", pt.x - 36f, pt.y + 3.5f);
                g2.setColor(new Color(252, 165, 165));
                g2.drawString("FALSE", pf.x - 40f, pf.y + 3.5f);
            } else {
                Point2D.Float op = outputPinPos(n, "OUT");
                drawExecPin(g2, op.x, op.y, Color.WHITE);
            }

            // Node Parameter Summary Preview
            g2.setFont(DesktopUiKit.FONT_MONO);
            g2.setColor(new Color(212, 212, 220));
            String line1 = n.param1 == null ? "" : n.param1;
            String line2 = n.param2 == null ? "" : n.param2;
            if (!line1.isEmpty()) {
                g2.drawString(trimToWidth(g2, line1, NODE_W - 56), n.x + 26f, n.y + 48f);
            }
            if (!line2.isEmpty()) {
                g2.setColor(new Color(165, 165, 176));
                g2.drawString(trimToWidth(g2, line2, NODE_W - 44), n.x + 26f, n.y + 66f);
            } else if (n.param3 != null && !n.param3.isEmpty()) {
                g2.setColor(new Color(165, 165, 176));
                g2.drawString(trimToWidth(g2, n.param3, NODE_W - 44), n.x + 26f, n.y + 66f);
            }
        }

        private void drawExecPin(Graphics2D g2, float cx, float cy, Color col) {
            Path2D.Float p = new Path2D.Float();
            p.moveTo(cx - 4.5f, cy - 5.2f);
            p.lineTo(cx + 5.2f, cy);
            p.lineTo(cx - 4.5f, cy + 5.2f);
            p.closePath();
            g2.setColor(col);
            g2.fill(p);
        }

        private String trimToWidth(Graphics2D g2, String s, int maxW) {
            if (s == null) return "";
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(s) <= maxW) return s;
            String t = s;
            while (t.length() > 3 && fm.stringWidth(t + "…") > maxW) {
                t = t.substring(0, t.length() - 1);
            }
            return t + "…";
        }

        private BpNode findById(String id) {
            if (project == null || id == null) return null;
            for (BpNode n : project.blueprintNodes) {
                if (id.equals(n.id)) return n;
            }
            return null;
        }
    }
}
