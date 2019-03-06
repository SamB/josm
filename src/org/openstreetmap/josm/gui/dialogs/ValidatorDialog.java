// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.openstreetmap.josm.actions.AbstractSelectAction;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.ValidateAction;
import org.openstreetmap.josm.actions.relation.EditRelationAction;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;
import org.openstreetmap.josm.data.preferences.sources.ValidatorPrefHelper;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.data.validation.ValidatorVisitor;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.PopupMenuHandler;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.validator.ValidatorTreePanel;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.layer.ValidatorLayer;
import org.openstreetmap.josm.gui.preferences.validator.ValidatorPreference;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Shortcut;
import org.xml.sax.SAXException;

/**
 * A small tool dialog for displaying the current errors. The selection manager
 * respects clicks into the selection list. Ctrl-click will remove entries from
 * the list while single click will make the clicked entry the only selection.
 *
 * @author frsantos
 */
public class ValidatorDialog extends ToggleDialog implements DataSelectionListener, ActiveLayerChangeListener {

    /** The display tree */
    public ValidatorTreePanel tree;

    /** The validate action */
    public static final ValidateAction validateAction = new ValidateAction();

    /** The fix button */
    private final SideButton fixButton;
    /** The ignore button */
    private final SideButton ignoreButton;
    /** The reset ignorelist button */
    private final SideButton ignorelistManagement;
    /** The select button */
    private final SideButton selectButton;
    /** The lookup button */
    private final SideButton lookupButton;

    private final JPopupMenu popupMenu = new JPopupMenu();
    private final transient PopupMenuHandler popupMenuHandler = new PopupMenuHandler(popupMenu);

    /** Last selected element */
    private DefaultMutableTreeNode lastSelectedNode;

    /**
     * Constructor
     */
    public ValidatorDialog() {
        super(tr("Validation Results"), "validator", tr("Open the validation window."),
                Shortcut.registerShortcut("subwindow:validator", tr("Toggle: {0}", tr("Validation Results")),
                        KeyEvent.VK_V, Shortcut.ALT_SHIFT), 150, false, ValidatorPreference.class);

        popupMenuHandler.addAction(MainApplication.getMenu().autoScaleActions.get("problem"));
        popupMenuHandler.addAction(new EditRelationAction());

        tree = new ValidatorTreePanel();
        tree.addMouseListener(new MouseEventHandler());
        addTreeSelectionListener(new SelectionWatch());
        InputMapUtils.unassignCtrlShiftUpDown(tree, JComponent.WHEN_FOCUSED);

        List<SideButton> buttons = new LinkedList<>();

        selectButton = new SideButton(new AbstractSelectAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setSelectedItems();
            }
        });
        InputMapUtils.addEnterAction(tree, selectButton.getAction());

        selectButton.setEnabled(false);
        buttons.add(selectButton);

        lookupButton = new SideButton(new LookupAction());
        buttons.add(lookupButton);

        buttons.add(new SideButton(validateAction));

        fixButton = new SideButton(new AbstractAction() {
            {
                putValue(NAME, tr("Fix"));
                putValue(SHORT_DESCRIPTION, tr("Fix the selected issue."));
                new ImageProvider("dialogs", "fix").getResource().attachImageIcon(this, true);
            }
            @Override
            public void actionPerformed(ActionEvent e) {
                fixErrors();
            }
        });
        fixButton.setEnabled(false);
        buttons.add(fixButton);

        if (ValidatorPrefHelper.PREF_USE_IGNORE.get()) {
            ignoreButton = new SideButton(new AbstractAction() {
                {
                    putValue(NAME, tr("Ignore"));
                    putValue(SHORT_DESCRIPTION, tr("Ignore the selected issue next time."));
                    new ImageProvider("dialogs", "fix").getResource().attachImageIcon(this, true);
                }
                @Override
                public void actionPerformed(ActionEvent e) {
                    ignoreErrors();
                }
            });
            ignoreButton.setEnabled(false);
            buttons.add(ignoreButton);

            ignorelistManagement = new SideButton(new AbstractAction() {
                {
                    putValue(NAME, tr("Manage Ignore"));
                    putValue(SHORT_DESCRIPTION, tr("Manage the ignore list"));
                    new ImageProvider("dialogs", "fix").getResource().attachImageIcon(this, true);
                }

                @Override
                public void actionPerformed(ActionEvent e) {
                    new ValidatorListManagementDialog("Ignore");
                }
            });
            buttons.add(ignorelistManagement);
        } else {
            ignoreButton = null;
            ignorelistManagement = null;
        }

        createLayout(tree, true, buttons);
    }

    /**
     * The action to lookup the selection in the error tree.
     */
    class LookupAction extends AbstractAction implements DataSelectionListener {

        LookupAction() {
            putValue(NAME, tr("Lookup"));
            putValue(SHORT_DESCRIPTION, tr("Looks up the selected primitives in the error list."));
            new ImageProvider("dialogs", "search").getResource().attachImageIcon(this, true);
            SelectionEventManager.getInstance().addSelectionListener(this);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
            if (ds == null) {
                return;
            }
            tree.selectRelatedErrors(ds.getSelected());
        }

        protected void updateEnabledState() {
            boolean found = false;
            for (TestError e : tree.getErrors()) {
                for (OsmPrimitive p : e.getPrimitives()) {
                    if (p.isSelected()) {
                        found = true;
                        break;
                    }
                }
            }
            setEnabled(found);
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            updateEnabledState();
        }
    }

    @Override
    public void showNotify() {
        SelectionEventManager.getInstance().addSelectionListener(this);
        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
        if (ds != null) {
            updateSelection(ds.getAllSelected());
        }
        MainApplication.getLayerManager().addAndFireActiveLayerChangeListener(this);
    }

    @Override
    public void hideNotify() {
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        SelectionEventManager.getInstance().removeSelectionListener(this);
    }

    @Override
    public void setVisible(boolean v) {
        if (tree != null) {
            tree.setVisible(v);
        }
        super.setVisible(v);
    }

    /**
     * Fix selected errors
     */
    private void fixErrors() {
        TreePath[] selectionPaths = tree.getSelectionPaths();
        if (selectionPaths == null)
            return;

        Set<DefaultMutableTreeNode> processedNodes = new HashSet<>();

        List<TestError> errorsToFix = new LinkedList<>();
        for (TreePath path : selectionPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node != null) {
                ValidatorTreePanel.visitTestErrors(node, errorsToFix::add, processedNodes);
            }
        }

        // run fix task asynchronously
        MainApplication.worker.submit(new FixTask(errorsToFix));
    }

    /**
     * Set selected errors to ignore state
     */
    private void ignoreErrors() {
        int asked = JOptionPane.DEFAULT_OPTION;
        AtomicBoolean changed = new AtomicBoolean();
        TreePath[] selectionPaths = tree.getSelectionPaths();
        if (selectionPaths == null)
            return;

        Set<DefaultMutableTreeNode> processedNodes = new HashSet<>();
        for (TreePath path : selectionPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            if (node == null) {
                continue;
            }

            Object mainNodeInfo = node.getUserObject();
            final int depth = node.getDepth();
            if (depth <= 1) {
                if (!(mainNodeInfo instanceof TestError)) {
                    Set<Pair<String, String>> state = new HashSet<>();
                    // ask if the whole set should be ignored
                    if (asked == JOptionPane.DEFAULT_OPTION) {
                        String[] a = new String[] {tr("Whole group"), tr("Single elements"), tr("Nothing")};
                        asked = JOptionPane.showOptionDialog(MainApplication.getMainFrame(), tr("Ignore whole group or individual elements?"),
                                tr("Ignoring elements"), JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
                                a, a[1]);
                    }
                    if (asked == JOptionPane.YES_NO_OPTION) {
                        ValidatorTreePanel.visitTestErrors(node, err -> {
                            err.setIgnored(true);
                            changed.set(true);
                            state.add(new Pair<>(node.getDepth() == 1 ? err.getIgnoreSubGroup() : err.getIgnoreGroup(), err.getMessage()));
                        }, processedNodes);
                        for (Pair<String, String> s : state) {
                            OsmValidator.addIgnoredError(s.a, s.b);
                        }
                        continue;
                    } else if (asked == JOptionPane.CANCEL_OPTION || asked == JOptionPane.CLOSED_OPTION) {
                        continue;
                    }
                }

                ValidatorTreePanel.visitTestErrors(node, error -> {
                    String state = error.getIgnoreState();
                    if (state != null) {
                        OsmValidator.addIgnoredError(state, error.getMessage());
                    }
                    changed.set(true);
                    error.setIgnored(true);
                }, processedNodes);
            }
        }
        if (changed.get()) {
            tree.resetErrors();
            OsmValidator.saveIgnoredErrors();
            invalidateValidatorLayers();
        }
    }

    /**
     * Sets the selection of the map to the current selected items.
     */
    private void setSelectedItems() {
        DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
        if (tree == null || ds == null)
            return;

        TreePath[] selectedPaths = tree.getSelectionPaths();
        if (selectedPaths == null)
            return;

        Collection<OsmPrimitive> sel = new HashSet<>(40);
        for (TreePath path : selectedPaths) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
            Enumeration<TreeNode> children = node.breadthFirstEnumeration();
            while (children.hasMoreElements()) {
                DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
                Object nodeInfo = childNode.getUserObject();
                if (nodeInfo instanceof TestError) {
                    TestError error = (TestError) nodeInfo;
                    error.getPrimitives().stream()
                            .filter(OsmPrimitive::isSelectable)
                            .forEach(sel::add);
                }
            }
        }
        ds.setSelected(sel);
    }

    /**
     * Checks for fixes in selected element and, if needed, adds to the sel
     * parameter all selected elements
     *
     * @param sel
     *            The collection where to add all selected elements
     * @param addSelected
     *            if true, add all selected elements to collection
     * @return whether the selected elements has any fix
     */
    private boolean setSelection(Collection<OsmPrimitive> sel, boolean addSelected) {
        AtomicBoolean hasFixes = new AtomicBoolean();

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (lastSelectedNode != null && !lastSelectedNode.equals(node)) {
            ValidatorTreePanel.visitTestErrors(lastSelectedNode, error -> error.setSelected(false));
        }

        lastSelectedNode = node;
        if (node != null) {
            ValidatorTreePanel.visitTestErrors(node, error -> {
                error.setSelected(true);

                hasFixes.set(hasFixes.get() || error.isFixable());
                if (addSelected) {
                    error.getPrimitives().stream()
                            .filter(OsmPrimitive::isSelectable)
                            .forEach(sel::add);
                }
            });
            selectButton.setEnabled(true);
            if (ignoreButton != null) {
                ignoreButton.setEnabled(node.getDepth() <= 1);
            }
        }

        return hasFixes.get();
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        OsmDataLayer editLayer = e.getSource().getEditLayer();
        if (editLayer == null) {
            tree.setErrorList(new ArrayList<TestError>());
        } else {
            tree.setErrorList(editLayer.validationErrors);
        }
    }

    /**
     * Add a tree selection listener to the validator tree.
     * @param listener the TreeSelectionListener
     * @since 5958
     */
    public void addTreeSelectionListener(TreeSelectionListener listener) {
        tree.addTreeSelectionListener(listener);
    }

    /**
     * Remove the given tree selection listener from the validator tree.
     * @param listener the TreeSelectionListener
     * @since 5958
     */
    public void removeTreeSelectionListener(TreeSelectionListener listener) {
        tree.removeTreeSelectionListener(listener);
    }

    /**
     * Replies the popup menu handler.
     * @return The popup menu handler
     * @since 5958
     */
    public PopupMenuHandler getPopupMenuHandler() {
        return popupMenuHandler;
    }

    /**
     * Replies the currently selected error, or {@code null}.
     * @return The selected error, if any.
     * @since 5958
     */
    public TestError getSelectedError() {
        Object comp = tree.getLastSelectedPathComponent();
        if (comp instanceof DefaultMutableTreeNode) {
            Object object = ((DefaultMutableTreeNode) comp).getUserObject();
            if (object instanceof TestError) {
                return (TestError) object;
            }
        }
        return null;
    }

    /**
     * Watches for double clicks and launches the popup menu.
     */
    class MouseEventHandler extends PopupMenuLauncher {

        MouseEventHandler() {
            super(popupMenu);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
            if (selPath == null) {
                tree.clearSelection();
            }

            fixButton.setEnabled(false);
            if (ignoreButton != null) {
                ignoreButton.setEnabled(false);
            }
            selectButton.setEnabled(false);

            boolean isDblClick = isDoubleClick(e);

            Collection<OsmPrimitive> sel = isDblClick ? new HashSet<>(40) : null;

            boolean hasFixes = setSelection(sel, isDblClick);
            fixButton.setEnabled(hasFixes);

            if (isDblClick) {
                DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
                if (ds != null) {
                    ds.setSelected(sel);
                }
                if (Config.getPref().getBoolean("validator.autozoom", false)) {
                    AutoScaleAction.zoomTo(sel);
                }
            }
        }

        @Override
        public void launch(MouseEvent e) {
            TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
            if (selPath == null)
                return;
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) selPath.getPathComponent(selPath.getPathCount() - 1);
            if (!(node.getUserObject() instanceof TestError))
                return;
            super.launch(e);
        }
    }

    /**
     * Watches for tree selection.
     */
    public class SelectionWatch implements TreeSelectionListener {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
            fixButton.setEnabled(false);
            if (ignoreButton != null) {
                ignoreButton.setEnabled(false);
            }
            selectButton.setEnabled(false);

            Collection<OsmPrimitive> sel = new HashSet<>();
            boolean hasFixes = setSelection(sel, true);
            fixButton.setEnabled(hasFixes);
            popupMenuHandler.setPrimitives(sel);
            invalidateValidatorLayers();
        }
    }

    /**
     * A visitor that is used to compute the bounds of an error.
     */
    public static class ValidatorBoundingXYVisitor extends BoundingXYVisitor implements ValidatorVisitor {
        @Override
        public void visit(OsmPrimitive p) {
            if (p.isUsable()) {
                p.accept((PrimitiveVisitor) this);
            }
        }

        @Override
        public void visit(WaySegment ws) {
            if (ws.lowerIndex < 0 || ws.lowerIndex + 1 >= ws.way.getNodesCount())
                return;
            visit(ws.getFirstNode());
            visit(ws.getSecondNode());
        }

        @Override
        public void visit(List<Node> nodes) {
            for (Node n: nodes) {
                visit(n);
            }
        }

        @Override
        public void visit(TestError error) {
            if (error != null) {
                error.visitHighlighted(this);
            }
        }
    }

    /**
     * Called when the selection was changed to update the list of displayed errors
     * @param newSelection The new selection
     */
    public void updateSelection(Collection<? extends OsmPrimitive> newSelection) {
        if (!Config.getPref().getBoolean(ValidatorPrefHelper.PREF_FILTER_BY_SELECTION, false))
            return;
        if (newSelection.isEmpty()) {
            tree.setFilter(null);
        }
        tree.setFilter(new HashSet<>(newSelection));
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event) {
        updateSelection(event.getSelection());
    }

    /**
     * Task for fixing a collection of {@link TestError}s. Can be run asynchronously.
     */
    class FixTask extends PleaseWaitRunnable {
        private final Collection<TestError> testErrors;
        private final List<Command> fixCommands = new ArrayList<>();
        private boolean canceled;

        FixTask(Collection<TestError> testErrors) {
            super(tr("Fixing errors ..."), false /* don't ignore exceptions */);
            this.testErrors = testErrors == null ? new ArrayList<>() : testErrors;
        }

        @Override
        protected void cancel() {
            this.canceled = true;
        }

        @Override
        protected void finish() {
            // do nothing
        }

        protected void fixError(TestError error) throws InterruptedException, InvocationTargetException {
            if (error.isFixable()) {
                if (error.getPrimitives().stream().noneMatch(OsmPrimitive::isDeleted)) {
                    final Command fixCommand = error.getFix();
                    if (fixCommand != null) {
                        SwingUtilities.invokeAndWait(fixCommand::executeCommand);
                        fixCommands.add(fixCommand);
                    }
                }
                // It is wanted to ignore an error if it said fixable, even if fixCommand was null
                // This is to fix #5764 and #5773:
                // a delete command, for example, may be null if all concerned primitives have already been deleted
                error.setIgnored(true);
            }
        }

        @Override
        protected void realRun() throws SAXException, IOException, OsmTransferException {
            ProgressMonitor monitor = getProgressMonitor();
            try {
                monitor.setTicksCount(testErrors.size());
                final DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
                int i = 0;
                SwingUtilities.invokeAndWait(ds::beginUpdate);
                try {
                    for (TestError error: testErrors) {
                        i++;
                        monitor.subTask(tr("Fixing ({0}/{1}): ''{2}''", i, testErrors.size(), error.getMessage()));
                        if (this.canceled)
                            return;
                        fixError(error);
                        monitor.worked(1);
                    }
                } finally {
                    SwingUtilities.invokeAndWait(ds::endUpdate);
                }
                monitor.subTask(tr("Updating map ..."));
                SwingUtilities.invokeAndWait(() -> {
                    if (!fixCommands.isEmpty()) {
                        UndoRedoHandler.getInstance().add(
                                fixCommands.size() > 1 ? new AutofixCommand(fixCommands) : fixCommands.get(0), false);
                    }
                    invalidateValidatorLayers();
                    tree.resetErrors();
                });
            } catch (InterruptedException e) {
                tryUndo();
                throw new JosmRuntimeException(e);
            } catch (InvocationTargetException e) {
                // FIXME: signature of realRun should have a generic checked exception we could throw here
                throw new JosmRuntimeException(e);
            } finally {
                if (monitor.isCanceled()) {
                    tryUndo();
                }
                monitor.finishTask();
            }
        }

        /**
         * Undo commands as they were not yet added to the UndoRedo Handler
         */
        private void tryUndo() {
            final DataSet ds = MainApplication.getLayerManager().getActiveDataSet();
            int i = fixCommands.size() - 1;
            ds.beginUpdate();
            for (; i >= 0; i--) {
                fixCommands.get(i).undoCommand();
            }
            ds.endUpdate();
        }

    }

    private static void invalidateValidatorLayers() {
        MainApplication.getLayerManager().getLayersOfType(ValidatorLayer.class).forEach(ValidatorLayer::invalidate);
    }

    @Override
    public void destroy() {
        if (lookupButton != null && lookupButton.getAction() instanceof DataSelectionListener) {
            Action a = lookupButton.getAction();
            SelectionEventManager.getInstance().removeSelectionListener((DataSelectionListener) a);
        }
        super.destroy();
    }

    private static class AutofixCommand extends SequenceCommand {
        AutofixCommand(Collection<Command> sequenz) {
            super(tr("auto-fixed validator issues"), sequenz, true);
            setSequenceComplete(true);
        }

        @Override
        public void undoCommand() {
            getAffectedDataSet().beginUpdate();
            super.undoCommand();
            getAffectedDataSet().endUpdate();
        }

        @Override
        public boolean executeCommand() {
            getAffectedDataSet().beginUpdate();
            boolean rc = super.executeCommand();
            getAffectedDataSet().endUpdate();
            return rc;
        }
    }
}
