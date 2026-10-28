package com.vortex.engine.desktop;

import com.vortex.engine.core.model.Entity;
import com.vortex.engine.core.model.GameProject;
import com.vortex.engine.core.model.SceneHierarchy;

import javax.swing.BorderFactory;
import javax.swing.DropMode;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Godot-style Scene Tree (v4.5.0) — nesting, drag-to-reparent, eye visibility, rename, filter.
 * Replaces the old flat JList: everything downstream still talks in entity ids.
 */
public final class DesktopSceneTree extends JPanel {

    public interface Listener {
        void onSelect(String entityId);
        void onReparent(String childId, String newParentId);
        void onRename(String entityId, String newName);
        void onDelete(String entityId);
        void onDuplicate(String entityId);
        void onAddChild(String parentId);
        void onToggleVisible(String entityId);
    }

    private final DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("SCENE");
    private final DefaultTreeModel model = new DefaultTreeModel(rootNode);
    private final JTree tree = new JTree(model);
    private final JTextField filterField = new JTextField();
    private final Listener listener;

    private GameProject project;
    private boolean suppressSelectionCallback = false;

    public DesktopSceneTree(Listener listener) {
        super(new BorderLayout());
        this.listener = listener;
        setBackground(DesktopUiKit.C_PANEL);

        tree.setBackground(DesktopUiKit.C_VOID);
        tree.setForeground(DesktopUiKit.C_TEXT);
        tree.setFont(DesktopUiKit.FONT_UI);
        tree.setRowHeight(Math.max(24, DesktopUiKit.scaled(26)));
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(0);
        tree.putClientProperty("JTree.lineStyle", "Angled");
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new NodeRenderer());
        try { tree.setDragEnabled(true); } catch (java.awt.HeadlessException ignored) { /* styled previews / CI */ }
        tree.setDropMode(DropMode.ON);
        tree.setTransferHandler(new ReparentHandler());
        DesktopUiKit.installDarkDefaults();

        tree.addTreeSelectionListener(e -> {
            if (suppressSelectionCallback) return;
            Entity sel = selectedEntity();
            if (listener != null) listener.onSelect(sel == null ? null : sel.id);
        });

        tree.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode) {
                        Object uo = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
                        if (uo instanceof Entity) renameEntity((Entity) uo);
                    }
                }
            }
        });

        tree.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                Entity sel = selectedEntity();
                if (sel == null) return;
                if (e.getKeyCode() == KeyEvent.VK_F2) renameEntity(sel);
                else if (e.getKeyCode() == KeyEvent.VK_DELETE && listener != null) listener.onDelete(sel.id);
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "vortex-rename");
        tree.getActionMap().put("vortex-rename", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                Entity sel = selectedEntity();
                if (sel != null) renameEntity(sel);
            }
        });

        JScrollPane scroll = DesktopUiKit.createScrollPane(tree);
        scroll.getViewport().setBackground(DesktopUiKit.C_VOID);
        scroll.setPreferredSize(new Dimension(265, 195));

        JPanel filterBar = new JPanel(new BorderLayout(6, 0));
        filterBar.setBackground(DesktopUiKit.C_PANEL);
        filterBar.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));
        JLabel filterIcon = new JLabel(new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(DesktopUiKit.C_TEXT_MUTED);
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawOval(x + 1, y + 1, 10, 10);
                g2.drawLine(x + 10, y + 10, x + 14, y + 14);
                g2.dispose();
            }
            @Override public int getIconWidth() { return 16; }
            @Override public int getIconHeight() { return 16; }
        });
        filterIcon.setForeground(DesktopUiKit.C_TEXT_MUTED);
        filterIcon.setFont(DesktopUiKit.FONT_SMALL);
        filterIcon.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        filterField.setBackground(DesktopUiKit.C_INPUT_BG);
        filterField.setForeground(DesktopUiKit.C_WHITE);
        filterField.setCaretColor(DesktopUiKit.C_WHITE);
        filterField.setFont(DesktopUiKit.FONT_UI);
        filterField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));
        filterField.setToolTipText("Filter nodes");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(DocumentEvent e) { refresh(); }
        });
        filterBar.add(filterIcon, BorderLayout.WEST);
        filterBar.add(filterField, BorderLayout.CENTER);

        add(filterBar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void setProject(GameProject project) {
        this.project = project;
        refresh();
    }

    public void setListener(Listener l) { /* listener is final by design */ }

    public String getSelectedEntityId() {
        Entity e = selectedEntity();
        return e == null ? null : e.id;
    }

    public Entity selectedEntity() {
        TreePath path = tree.getSelectionPath();
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode) {
            Object uo = ((DefaultMutableTreeNode) last).getUserObject();
            if (uo instanceof Entity) return (Entity) uo;
        }
        return null;
    }

    /** Rebuild the tree, keeping expansion + selection stable. */
    public void refresh() {
        if (project == null) return;
        SceneHierarchy.heal(project.entities);

        Set<String> expanded = new HashSet<>();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath path = tree.getPathForRow(i);
            if (path != null && tree.isExpanded(path)) {
                Object uo = userObjectOf(path);
                if (uo instanceof Entity) expanded.add(((Entity) uo).id);
            }
        }
        String keepSelected = getSelectedEntityId();

        String filter = filterField.getText() == null ? "" : filterField.getText().trim().toLowerCase(Locale.US);
        rootNode.removeAllChildren();
        List<Entity> treeOrder = SceneHierarchy.flattened(project.entities);
        Set<String> added = new HashSet<>();
        for (Entity e : treeOrder) {
            if (keepByFilter(project, e, filter)) {
                DefaultMutableTreeNode node = new DefaultMutableTreeNode(e);
                Entity parent = SceneHierarchy.parentOf(project.entities, e);
                DefaultMutableTreeNode parentNode = parent == null ? null : findNode(parent.id);
                (parentNode == null ? rootNode : parentNode).add(node);
                added.add(e.id);
            }
        }
        // Any entity whose ancestor was filtered out still needs a home.
        for (Entity e : project.entities) {
            if (!added.contains(e.id) && keepByFilter(project, e, filter)) {
                rootNode.add(new DefaultMutableTreeNode(e));
                added.add(e.id);
            }
        }
        model.reload();
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath path = tree.getPathForRow(i);
            Object uo = path == null ? null : userObjectOf(path);
            if (uo instanceof Entity) {
                Entity e = (Entity) uo;
                if (expanded.contains(e.id) || !filter.isEmpty()) tree.expandPath(path);
                if (keepSelected != null && keepSelected.equals(e.id)) tree.setSelectionPath(path);
            }
        }
        if (!filter.isEmpty()) {
            for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        }
        tree.repaint();
    }

    public void selectEntity(String id) {
        suppressSelectionCallback = true;
        try {
            if (id == null) {
                tree.clearSelection();
                return;
            }
            for (int i = 0; i < tree.getRowCount(); i++) {
                TreePath path = tree.getPathForRow(i);
                Object uo = path == null ? null : userObjectOf(path);
                if (uo instanceof Entity && id.equals(((Entity) uo).id)) {
                    tree.setSelectionPath(path);
                    tree.scrollPathToVisible(path);
                    return;
                }
            }
        } finally {
            suppressSelectionCallback = false;
        }
    }

    /** Expands every node (View ▸ Expand All). */
    public void expandAll() {
        for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
    }

    private static boolean keepByFilter(GameProject project, Entity e, String filter) {
        if (filter.isEmpty()) return true;
        if (e.name != null && e.name.toLowerCase(Locale.US).contains(filter)) return true;
        if (e.primitive != null && e.primitive.toLowerCase(Locale.US).contains(filter)) return true;
        if (e.tag != null && e.tag.toLowerCase(Locale.US).contains(filter)) return true;
        return SceneHierarchy.path(project.entities, e).toLowerCase(Locale.US).contains(filter);
    }

    private DefaultMutableTreeNode findNode(String entityId) {
        return findNode(rootNode, entityId);
    }

    private DefaultMutableTreeNode findNode(DefaultMutableTreeNode node, String entityId) {
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            Object uo = child.getUserObject();
            if (uo instanceof Entity && entityId.equals(((Entity) uo).id)) return child;
            DefaultMutableTreeNode deep = findNode(child, entityId);
            if (deep != null) return deep;
        }
        return null;
    }

    private static Object userObjectOf(TreePath path) {
        if (path == null) return null;
        Object last = path.getLastPathComponent();
        if (last instanceof DefaultMutableTreeNode) return ((DefaultMutableTreeNode) last).getUserObject();
        return null;
    }

    private void renameEntity(final Entity e) {
        JTextField field = new JTextField(e.name, 18);
        field.setFont(DesktopUiKit.FONT_UI);
        field.setBackground(DesktopUiKit.C_INPUT_BG);
        field.setForeground(DesktopUiKit.C_WHITE);
        field.setCaretColor(DesktopUiKit.C_WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DesktopUiKit.C_BORDER_STRONG, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        SwingUtilities.invokeLater(field::selectAll);
        Object[] opts = {"Rename", "Cancel"};
        int r = javax.swing.JOptionPane.showOptionDialog(this, field, "Rename node",
                javax.swing.JOptionPane.OK_CANCEL_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE,
                null, opts, opts[0]);
        if (r != 0) return;
        String name = field.getText() == null ? "" : field.getText().trim();
        if (name.isEmpty()) return;
        if (listener != null) listener.onRename(e.id, name);
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        if (path != null) tree.setSelectionPath(path);
        final Entity sel = selectedEntity();
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(DesktopUiKit.C_HEADER);
        menu.setBorder(BorderFactory.createLineBorder(DesktopUiKit.C_BORDER, 1));

        menu.add(item("Rename", sel, entity -> renameEntity(entity)));
        menu.add(item("Duplicate", sel, entity -> { if (listener != null) listener.onDuplicate(entity.id); }));
        menu.add(item("Add child", sel, entity -> { if (listener != null) listener.onAddChild(entity.id); }));
        menu.add(item("Detach to root", sel, entity -> {
            if (listener != null) listener.onReparent(entity.id, "");
        }));
        menu.add(item("Toggle visible", sel, entity -> {
            if (listener != null) listener.onToggleVisible(entity.id);
        }));
        menu.addSeparator();
        menu.add(item("Delete", sel, entity -> { if (listener != null) listener.onDelete(entity.id); }));
        menu.show(tree, e.getX(), e.getY());
    }

    private interface EntityAction { void run(Entity e); }

    private JMenuItem item(String label, final Entity target, final EntityAction action) {
        JMenuItem mi = new JMenuItem(label);
        mi.setFont(DesktopUiKit.FONT_UI);
        mi.setBackground(DesktopUiKit.C_HEADER);
        mi.setForeground(target == null ? DesktopUiKit.C_TEXT_MUTED : DesktopUiKit.C_TEXT);
        mi.setEnabled(target != null);
        mi.addActionListener(e -> {
            if (target != null) action.run(target);
        });
        return mi;
    }

    // ------------------------------------------------------------------ drag → reparent

    private final class ReparentHandler extends TransferHandler {
        @Override public int getSourceActions(JComponent c) { return MOVE; }

        @Override protected Transferable createTransferable(JComponent c) {
            final Entity sel = selectedEntity();
            if (sel == null) return null;
            return new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.stringFlavor}; }
                @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.stringFlavor.equals(flavor); }
                @Override public Object getTransferData(DataFlavor flavor) { return sel.id; }
            };
        }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            if (!support.isDataFlavorSupported(DataFlavor.stringFlavor)) return false;
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            if (dl == null || dl.getPath() == null) return false;
            Object uo = userObjectOf(dl.getPath());
            if (!(uo instanceof Entity) || project == null) return false;
            Entity target = (Entity) uo;
            Entity dragged = selectedEntity();
            if (dragged == null || target.id.equals(dragged.id)) return false;
            // Godot rule: a node can never become a child of its own descendant.
            return !SceneHierarchy.isAncestor(project.entities, dragged, target);
        }

        @Override public boolean importData(TransferSupport support) {
            try {
                String childId = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                Object uo = dl == null || dl.getPath() == null ? null : userObjectOf(dl.getPath());
                if (childId == null || !(uo instanceof Entity)) return false;
                String parentId = ((Entity) uo).id;
                if (listener != null) listener.onReparent(childId, parentId);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
    }

    // ------------------------------------------------------------------ renderer

    private final class NodeRenderer extends DefaultTreeCellRenderer {
        private final Font worldFont = DesktopUiKit.FONT_UI;
        private final Font dimFont = DesktopUiKit.FONT_SMALL;

        @Override public Component getTreeCellRendererComponent(JTree t, Object value, boolean selected,
                                                               boolean expanded, boolean leaf, int row, boolean hasFocus) {
            JLabel lab = (JLabel) super.getTreeCellRendererComponent(t, value, selected, expanded, leaf, row, hasFocus);
            lab.setOpaque(true);
            lab.setBackground(selected ? DesktopUiKit.C_ACCENT_TINT : DesktopUiKit.C_VOID);
            lab.setForeground(selected ? DesktopUiKit.C_WHITE : DesktopUiKit.C_TEXT);
            lab.setFont(worldFont);
            lab.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 6));
            lab.setIconTextGap(8);
            lab.setText("");
            lab.setToolTipText(null);

            Object uo = value instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) value).getUserObject() : null;
            if (!(uo instanceof Entity)) {
                lab.setIcon(null);
                lab.setText(String.valueOf(value));
                lab.setFont(dimFont);
                lab.setForeground(DesktopUiKit.C_TEXT_MUTED);
                return lab;
            }
            Entity e = (Entity) uo;
            int depth = project == null ? 0 : SceneHierarchy.depth(project.entities, e);
            Color nameColor = e.visible ? (selected ? DesktopUiKit.C_WHITE : DesktopUiKit.C_TEXT) : DesktopUiKit.C_TEXT_MUTED;
            Color typeColor = selected ? DesktopUiKit.C_TEXT_LABEL : DesktopUiKit.C_TEXT_MUTED;
            String type = e.primitive + " \u00b7 " + e.bodyType;
            lab.setIcon(nodeIcon(e));
            lab.setText("<html><span style='color:#" + hex(nameColor) + ";font-weight:" + (e.bodyType.equals("STATIC") ? "600" : "400") + "'>"
                    + escape(e.name) + "</span><span style='color:#" + hex(typeColor) + "'>  " + escape(type) + "</span></html>");
            lab.setToolTipText(SceneHierarchy.path(project == null ? new ArrayList<Entity>() : project.entities, e)
                    + "  \u00b7  depth " + depth + (e.parentId == null || e.parentId.isEmpty() ? "" : "  \u00b7  parent " + e.parentId));
            return lab;
        }

        private String hex(Color c) {
            return String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
        }

        private String escape(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    /** Small vector node badge: 3D cube outline or 2D square, muted when hidden. */
    private Icon nodeIcon(Entity e) {
        return new Icon() {
            @Override public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new BasicStroke(1.4f));
                Color stroke = e.visible ? DesktopUiKit.C_WHITE_SOFT : DesktopUiKit.C_TEXT_MUTED;
                g2.setColor(stroke);
                int s = 12;
                if (e.is3D) {
                    int ox = x + 2, oy = y + 4;
                    g2.drawRect(ox, oy, s - 4, s - 4);
                    g2.drawLine(ox + 2, oy - 2, ox + s - 2, oy - 2);
                    g2.drawLine(ox + s - 2, oy - 2, ox + s - 2, oy + s - 6);
                    g2.drawLine(ox + 2, oy - 2, ox + 2, oy + s - 6);
                    g2.drawLine(ox + 2, oy + s - 6, ox + s - 2, oy + s - 6);
                } else {
                    g2.drawRect(x + 2, y + 3, s - 2, s - 2);
                    g2.drawLine(x + 2, y + 3, x + s, y + s + 1);
                }
                if (e.bodyType.equals("DYNAMIC")) {
                    g2.setColor(DesktopUiKit.C_OK_TX);
                    g2.fillOval(x + s + 1, y + s - 2, 3, 3);
                }
                g2.dispose();
            }
            @Override public int getIconWidth() { return 18; }
            @Override public int getIconHeight() { return 18; }
        };
    }
}
