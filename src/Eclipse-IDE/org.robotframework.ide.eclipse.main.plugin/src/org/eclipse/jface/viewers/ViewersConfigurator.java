package org.eclipse.jface.viewers;

import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * Helper methods for common configuration operations on
 * {@link org.eclipse.jface.viewers.TableViewer} and
 * {@link org.eclipse.jface.viewers.TreeViewer}
 * 
 * @{author Michal Anglart
 */
public class ViewersConfigurator {

    /**
     * Disables context menu if the header of Table is clicked. Under Windows
     * normally context menu is also shown when clicking on table header
     * 
     * @param viewer
     *            Table viewer for which header context menu should be disabled
     */
    public static void disableContextMenuOnHeader(final ColumnViewer viewer) {
        // no need to dispose
        viewer.getControl().addMenuDetectListener(new MenuDetectListener() {

            @Override
            public void menuDetected(final MenuDetectEvent e) {
                e.doit = !isClickedOnHeader(e);
            }

            private boolean isClickedOnHeader(final MenuDetectEvent e) {
                final Rectangle clientArea = getClientArea(viewer);
                final Point point = viewer.getControl().toControl(e.x, e.y);
                return clientArea.y <= point.y && point.y <= clientArea.y + getHeaderHeight(viewer);
            }

            private int getHeaderHeight(final ColumnViewer viewer) {
                if (viewer instanceof TableViewer) {
                    return ((TableViewer) viewer).getTable().getHeaderHeight();
                } else if (viewer instanceof TreeViewer) {
                    return ((TreeViewer) viewer).getTree().getHeaderHeight();
                }
                throw new IllegalStateException("Unknown viewer type: "
                        + (viewer == null ? "null" : viewer.getClass().getSimpleName()));
            }

            private Rectangle getClientArea(final ColumnViewer viewer) {
                if (viewer instanceof TableViewer) {
                    return ((TableViewer) viewer).getTable().getClientArea();
                } else if (viewer instanceof TreeViewer) {
                    return ((TreeViewer) viewer).getTree().getClientArea();
                }
                throw new IllegalStateException("Unknown viewer type: "
                        + (viewer == null ? "null" : viewer.getClass().getSimpleName()));
            }
        });
    }

    /**
     * Enables deselection possibility in Table. When user clicks inside table
     * but after the last table item the selection is set to empty.
     * 
     * @param viewer
     *            Table viewer which should have deselection enabled
     */
    public static void enableDeselectionPossibility(final ColumnViewer viewer) {
        // sets empty selection when user clicked outside the table items
        // section
        viewer.getControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(final MouseEvent e) {
                if (leftClickOutsideTable(e)) {
                    viewer.setSelection(new StructuredSelection());
                }
            }

            private boolean leftClickOutsideTable(final MouseEvent e) {
                if (viewer instanceof TableViewer) {
                    return e.button == 1 && ((TableViewer) viewer).getTable().getItem(new Point(e.x, e.y)) == null;
                } else if (viewer instanceof TreeViewer) {
                    return e.button == 1 && ((TreeViewer) viewer).getTree().getItem(new Point(e.x, e.y)) == null;
                }
                throw new IllegalStateException("Unknown viewer type: "
                        + (viewer == null ? "null" : viewer.getClass().getSimpleName()));
            }
        });
    }
}
