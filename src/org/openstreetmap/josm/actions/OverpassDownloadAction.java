// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicArrowButton;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.download.DownloadDialog;
import org.openstreetmap.josm.gui.download.OverpassQueryList;
import org.openstreetmap.josm.gui.download.OverpassQueryWizardDialog;
import org.openstreetmap.josm.gui.preferences.server.OverpassServerPreference;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.JosmTextArea;
import org.openstreetmap.josm.io.OverpassDownloadReader;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Download map data from Overpass API server.
 * @since 8684
 */
public class OverpassDownloadAction extends JosmAction {

    /**
     * Constructs a new {@code OverpassDownloadAction}.
     */
    public OverpassDownloadAction() {
        super(tr("Download from Overpass API ..."), "download-overpass", tr("Download map data from Overpass API server."),
                // CHECKSTYLE.OFF: LineLength
                Shortcut.registerShortcut("file:download-overpass", tr("File: {0}", tr("Download from Overpass API ...")), KeyEvent.VK_DOWN, Shortcut.ALT_SHIFT),
                // CHECKSTYLE.ON: LineLength
                true, "overpassdownload/download", true);
        putValue("help", ht("/Action/OverpassDownload"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        OverpassDownloadDialog dialog = OverpassDownloadDialog.getInstance();
        dialog.restoreSettings();
        dialog.setVisible(true);

        if (dialog.isCanceled()) {
            return;
        }

        dialog.rememberSettings();
        Optional<Bounds> selectedArea = dialog.getSelectedDownloadArea();
        String overpassQuery = dialog.getOverpassQuery();

        /*
         * Absence of the selected area can be justified only if the overpass query
         * is not restricted to bbox.
         */
        if (!selectedArea.isPresent() && overpassQuery.contains("{{bbox}}")) {
            JOptionPane.showMessageDialog(
                    dialog,
                    tr("Please select a download area first."),
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        /*
         * A callback that is passed to PostDownloadReporter that is called once the download task
         * has finished. According to the number of errors happened, their type we decide whether we
         * want to save the last query in OverpassQueryList.
         */
        Consumer<Collection<Object>> errorReporter = errors -> {

            boolean onlyNoDataError = errors.size() == 1 &&
                    errors.contains("No data found in this area.");

            if (errors.isEmpty() || onlyNoDataError) {
                dialog.saveHistoricItemOnSuccess();
            }
        };

        /*
         * In order to support queries generated by the Overpass Turbo Query Wizard tool
         * which do not require the area to be specified.
         */
        Bounds area = selectedArea.orElseGet(() -> new Bounds(0, 0, 0, 0));
        DownloadOsmTask task = new DownloadOsmTask();
        task.setZoomAfterDownload(dialog.isZoomToDownloadedDataRequired());
        Future<?> future = task.download(
                new OverpassDownloadReader(area, OverpassServerPreference.getOverpassServer(), dialog.getOverpassQuery()),
                dialog.isNewLayerRequired(), area, null);
        Main.worker.submit(new PostDownloadHandler(task, future, errorReporter));
    }

    private static final class DisableActionsFocusListener implements FocusListener {

        private final ActionMap actionMap;

        private DisableActionsFocusListener(ActionMap actionMap) {
            this.actionMap = actionMap;
        }

        @Override
        public void focusGained(FocusEvent e) {
            enableActions(false);
        }

        @Override
        public void focusLost(FocusEvent e) {
            enableActions(true);
        }

        private void enableActions(boolean enabled) {
            Object[] allKeys = actionMap.allKeys();
            if (allKeys != null) {
                for (Object key : allKeys) {
                    Action action = actionMap.get(key);
                    if (action != null) {
                        action.setEnabled(enabled);
                    }
                }
            }
        }
    }

    /**
     * The download dialog that overpass uses.
     * @since 12576 public
     */
    public static final class OverpassDownloadDialog extends DownloadDialog {

        private JosmTextArea overpassQuery;
        private OverpassQueryList overpassQueryList;
        private static OverpassDownloadDialog instance;
        private static final BooleanProperty OVERPASS_QUERY_LIST_OPENED =
                new BooleanProperty("download.overpass.query-list.opened", false);

        private OverpassDownloadDialog(Component parent) {
            super(parent, ht("/Action/OverpassDownload"));
            cbDownloadOsmData.setEnabled(false);
            cbDownloadOsmData.setSelected(false);
            cbDownloadGpxData.setVisible(false);
            cbDownloadNotes.setVisible(false);
            cbStartup.setVisible(false);
        }

        public static OverpassDownloadDialog getInstance() {
            if (instance == null) {
                instance = new OverpassDownloadDialog(Main.parent);
            }
            return instance;
        }

        @Override
        protected void buildMainPanelAboveDownloadSelections(JPanel pnl) {
            // needed for the invisible checkboxes cbDownloadGpxData, cbDownloadNotes
            pnl.add(new JLabel(), GBC.eol());

            DisableActionsFocusListener disableActionsFocusListener =
                    new DisableActionsFocusListener(slippyMapChooser.getNavigationComponentActionMap());

            String tooltip = tr("Build an Overpass query using the Overpass Turbo Query Wizard tool");
            Action queryWizardAction = new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    new OverpassQueryWizardDialog(instance).showDialog();
                }
            };

            JButton openQueryWizard = new JButton("Query Wizard");
            openQueryWizard.setToolTipText(tooltip);
            openQueryWizard.addActionListener(queryWizardAction);

            // CHECKSTYLE.OFF: LineLength
            this.overpassQuery = new JosmTextArea(
                    "/*\n" +
                    tr("Place your Overpass query below or generate one using the Overpass Turbo Query Wizard")
                    + "\n*/",
                    8, 80);
            // CHECKSTYLE.ON: LineLength
            this.overpassQuery.setFont(GuiHelper.getMonospacedFont(overpassQuery));
            this.overpassQuery.addFocusListener(disableActionsFocusListener);
            this.overpassQuery.addFocusListener(new FocusListener() {
                @Override
                public void focusGained(FocusEvent e) {
                    overpassQuery.selectAll();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    // ignored
                }
            });

            this.overpassQueryList = new OverpassQueryList(this, this.overpassQuery);
            overpassQueryList.setToolTipText(tr("Show/hide Overpass snippet list"));
            overpassQueryList.setVisible(OVERPASS_QUERY_LIST_OPENED.get());
            overpassQueryList.setPreferredSize(new Dimension(350, 300));
            JScrollPane scrollPane = new JScrollPane(overpassQuery);
            BasicArrowButton arrowButton = new BasicArrowButton(overpassQueryList.isVisible()
                ? BasicArrowButton.EAST
                : BasicArrowButton.WEST);
            arrowButton.addActionListener(e -> {
                if (overpassQueryList.isVisible()) {
                    overpassQueryList.setVisible(false);
                    arrowButton.setDirection(BasicArrowButton.WEST);
                    OVERPASS_QUERY_LIST_OPENED.put(false);
                } else {
                    overpassQueryList.setVisible(true);
                    arrowButton.setDirection(BasicArrowButton.EAST);
                    OVERPASS_QUERY_LIST_OPENED.put(false);
                }
            });

            JPanel innerPanel = new JPanel(new BorderLayout());
            innerPanel.add(scrollPane, BorderLayout.CENTER);
            innerPanel.add(arrowButton, BorderLayout.EAST);

            JPanel pane = new JPanel(new BorderLayout());
            pane.add(innerPanel, BorderLayout.CENTER);
            pane.add(overpassQueryList, BorderLayout.EAST);

            GBC gbc = GBC.eol().fill(GBC.HORIZONTAL); gbc.ipady = 200;
            pnl.add(openQueryWizard, GBC.std().insets(5, 5, 5, 5));
            pnl.add(pane, gbc);
        }

        String getOverpassQuery() {
            return overpassQuery.getText();
        }

        /**
         * Sets the query that is displayed
         * @param text The multiline query text.
         * @since 12576 public
         */
        public void setOverpassQuery(String text) {
            overpassQuery.setText(text);
        }

        /**
         * Adds the current query to {@link OverpassQueryList}.
         */
        void saveHistoricItemOnSuccess() {
            overpassQueryList.saveHistoricItem(overpassQuery.getText());
        }

        @Override
        protected void updateSizeCheck() {
            displaySizeCheckResult(false);
        }

        /**
         * Triggers the download action to fire.
         * @since 12576 public
         */
        public void triggerDownload() {
            super.btnDownload.doClick();
        }
    }
}
