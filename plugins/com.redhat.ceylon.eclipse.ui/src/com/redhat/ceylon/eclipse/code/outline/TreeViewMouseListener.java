package com.redhat.ceylon.eclipse.code.outline;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

public final class TreeViewMouseListener implements
        MouseMoveListener {
    
    private final Tree tree;
    private final TreeViewer treeViewer;
    private TreeItem fLastItem = null;

    public TreeViewMouseListener(TreeViewer treeViewer) {
        this.treeViewer = treeViewer;
        this.tree = treeViewer.getTree();
    }

    public void mouseMove(MouseEvent e) {
        if (tree.equals(e.getSource())) {
            Object o = tree.getItem(new Point(e.x, e.y));
            if (fLastItem == null ^ o == null) {
                tree.setCursor(o == null ? null : 
                    tree.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            }
            if (o instanceof TreeItem) {
                Rectangle clientArea = tree.getClientArea();
                if (!o.equals(fLastItem)) {
                    fLastItem = (TreeItem)o;
                    tree.setSelection(new TreeItem[] { fLastItem });
                }
                else if (e.y - clientArea.y < tree.getItemHeight() / 4) {
                    // Scroll up
                    Point p = tree.toDisplay(e.x, e.y);
                    Item item = treeViewer.scrollUp(p.x, p.y);
                    if (item instanceof TreeItem) {
                        fLastItem = (TreeItem)item;
                        tree.setSelection(new TreeItem[] { fLastItem });
                    }
                }
                else if (clientArea.y + clientArea.height - e.y < tree.getItemHeight() / 4) {
                    // Scroll down
                    Point p = tree.toDisplay(e.x, e.y);
                    Item item = treeViewer.scrollDown(p.x, p.y);
                    if (item instanceof TreeItem) {
                        fLastItem = (TreeItem)item;
                        tree.setSelection(new TreeItem[] { fLastItem });
                    }
                }
            }
            else if (o == null) {
                fLastItem = null;
            }
        }
    }
}