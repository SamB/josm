// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.download;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.plaf.basic.BasicArrowButton;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.actions.downloadtasks.PostDownloadHandler;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.preferences.AbstractProperty;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.download.DownloadSourceSizingPolicy.AdjustableDownloadSizePolicy;
import org.openstreetmap.josm.gui.download.overpass.OverpassWizardRegistration;
import org.openstreetmap.josm.gui.download.overpass.OverpassWizardRegistration.OverpassQueryWizard;
import org.openstreetmap.josm.gui.download.overpass.OverpassWizardRegistration.OverpassWizardCallbacks;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.JosmTextArea;
import org.openstreetmap.josm.io.OverpassDownloadReader;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Class defines the way data is fetched from Overpass API.
 * @since 12652
 */
public class OverpassDownloadSource implements DownloadSource<OverpassDownloadSource.OverpassDownloadData> {

    @Override
    public AbstractDownloadSourcePanel<OverpassDownloadData> createPanel(DownloadDialog dialog) {
        return new OverpassDownloadSourcePanel(this);
    }

    @Override
    public void doDownload(OverpassDownloadData data, DownloadSettings settings) {
        /*
         * In order to support queries generated by the Overpass Turbo Query Wizard tool
         * which do not require the area to be specified.
         */
        Bounds area = settings.getDownloadBounds().orElse(new Bounds(0, 0, 0, 0));
        DownloadOsmTask task = new DownloadOsmTask();
        task.setZoomAfterDownload(settings.zoomToData());
        Future<?> future = task.download(
                new OverpassDownloadReader(area, OverpassDownloadReader.OVERPASS_SERVER.get(), data.getQuery()),
                new DownloadParams().withNewLayer(settings.asNewLayer()), area, null);
        MainApplication.worker.submit(new PostDownloadHandler(task, future, data.getErrorReporter()));
    }

    @Override
    public String getLabel() {
        return tr("Download from Overpass API");
    }

    @Override
    public boolean onlyExpert() {
        return true;
    }

    /**
     * The GUI representation of the Overpass download source.
     * @since 12652
     */
    public static class OverpassDownloadSourcePanel extends AbstractDownloadSourcePanel<OverpassDownloadData>
            implements OverpassWizardCallbacks {

        private static final String SIMPLE_NAME = "overpassdownloadpanel";
        private static final AbstractProperty<Integer> PANEL_SIZE_PROPERTY =
                new IntegerProperty(TAB_SPLIT_NAMESPACE + SIMPLE_NAME, 150).cached();
        private static final BooleanProperty OVERPASS_QUERY_LIST_OPENED =
                new BooleanProperty("download.overpass.query-list.opened", false);
        private static final String ACTION_IMG_SUBDIR = "dialogs";

        private static final StringProperty DOWNLOAD_QUERY = new StringProperty("download.overpass.query",
                "/*\n" + tr("Place your Overpass query below or generate one using the Overpass Turbo Query Wizard") + "\n*/");

        private final JosmTextArea overpassQuery;
        private final UserQueryList overpassQueryList;

        /**
         * Create a new {@link OverpassDownloadSourcePanel}
         * @param ds The download source to create the panel for
         */
        public OverpassDownloadSourcePanel(OverpassDownloadSource ds) {
            super(ds);
            setLayout(new BorderLayout());

            this.overpassQuery = new JosmTextArea(DOWNLOAD_QUERY.get(), 8, 80);
            this.overpassQuery.setFont(GuiHelper.getMonospacedFont(overpassQuery));
            this.overpassQuery.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    overpassQuery.selectAll();
                }
            });

            this.overpassQueryList = new UserQueryList(this, this.overpassQuery, "download.overpass.queries");
            this.overpassQueryList.setPreferredSize(new Dimension(350, 300));

            EditSnippetAction edit = new EditSnippetAction();
            RemoveSnippetAction remove = new RemoveSnippetAction();
            this.overpassQueryList.addSelectionListener(edit);
            this.overpassQueryList.addSelectionListener(remove);

            JPanel listPanel = new JPanel(new GridBagLayout());
            listPanel.add(new JLabel(tr("Your saved queries:")), GBC.eol().insets(2).anchor(GBC.CENTER));
            listPanel.add(this.overpassQueryList, GBC.eol().fill(GBC.BOTH));
            listPanel.add(new JButton(new AddSnippetAction()), GBC.std().fill(GBC.HORIZONTAL));
            listPanel.add(new JButton(edit), GBC.std().fill(GBC.HORIZONTAL));
            listPanel.add(new JButton(remove), GBC.std().fill(GBC.HORIZONTAL));
            listPanel.setVisible(OVERPASS_QUERY_LIST_OPENED.get());

            JScrollPane scrollPane = new JScrollPane(overpassQuery);
            BasicArrowButton arrowButton = new BasicArrowButton(listPanel.isVisible()
                    ? BasicArrowButton.EAST
                    : BasicArrowButton.WEST);
            arrowButton.setToolTipText(tr("Show/hide Overpass snippet list"));
            arrowButton.addActionListener(e -> {
                if (listPanel.isVisible()) {
                    listPanel.setVisible(false);
                    arrowButton.setDirection(BasicArrowButton.WEST);
                    OVERPASS_QUERY_LIST_OPENED.put(Boolean.FALSE);
                } else {
                    listPanel.setVisible(true);
                    arrowButton.setDirection(BasicArrowButton.EAST);
                    OVERPASS_QUERY_LIST_OPENED.put(Boolean.TRUE);
                }
            });

            JPanel innerPanel = new JPanel(new BorderLayout());
            innerPanel.add(scrollPane, BorderLayout.CENTER);
            innerPanel.add(arrowButton, BorderLayout.EAST);

            JPanel leftPanel = new JPanel(new GridBagLayout());
            leftPanel.add(new JLabel(tr("Overpass query:")), GBC.eol().insets(5, 1, 5, 1).anchor(GBC.NORTHWEST));
            leftPanel.add(new JLabel(), GBC.eol().fill(GBC.VERTICAL));
            OverpassWizardRegistration.getWizards()
                .stream()
                .map(this::generateWizardButton)
                .forEach(button -> leftPanel.add(button, GBC.eol().anchor(GBC.CENTER)));
            leftPanel.add(new JLabel(), GBC.eol().fill(GBC.VERTICAL));

            add(leftPanel, BorderLayout.WEST);
            add(innerPanel, BorderLayout.CENTER);
            add(listPanel, BorderLayout.EAST);

            setMinimumSize(new Dimension(450, 240));
        }

        private JButton generateWizardButton(OverpassQueryWizard wizard) {
            JButton openQueryWizard = new JButton(wizard.getWizardName());
            openQueryWizard.setToolTipText(wizard.getWizardTooltip().orElse(null));
            openQueryWizard.addActionListener(new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wizard.startWizard(OverpassDownloadSourcePanel.this);
                }
            });
            return openQueryWizard;
        }

        @Override
        public OverpassDownloadData getData() {
            String query = overpassQuery.getText();
            /*
             * A callback that is passed to PostDownloadReporter that is called once the download task
             * has finished. According to the number of errors happened, their type we decide whether we
             * want to save the last query in OverpassQueryList.
             */
            Consumer<Collection<Object>> errorReporter = errors -> {

                boolean onlyNoDataError = errors.size() == 1 &&
                        errors.contains("No data found in this area.");

                if (errors.isEmpty() || onlyNoDataError) {
                    overpassQueryList.saveHistoricItem(query);
                }
            };

            return new OverpassDownloadData(OverpassDownloadReader.fixQuery(query), errorReporter);
        }

        @Override
        public void rememberSettings() {
            DOWNLOAD_QUERY.put(overpassQuery.getText());
        }

        @Override
        public void restoreSettings() {
            overpassQuery.setText(DOWNLOAD_QUERY.get());
        }

        @Override
        public boolean checkDownload(DownloadSettings settings) {
            String query = getData().getQuery();

            /*
             * Absence of the selected area can be justified only if the overpass query
             * is not restricted to bbox.
             */
            if (!settings.getDownloadBounds().isPresent() && query.contains("{{bbox}}")) {
                JOptionPane.showMessageDialog(
                        this.getParent(),
                        tr("Please select a download area first."),
                        tr("Error"),
                        JOptionPane.ERROR_MESSAGE
                );
                return false;
            }

            /*
             * Check for an empty query. User might want to download everything, if so validation is passed,
             * otherwise return false.
             */
            if (query.matches("(/\\*(\\*[^/]|[^\\*/])*\\*/|\\s)*")) {
                boolean doFix = ConditionalOptionPaneUtil.showConfirmationDialog(
                        "download.overpass.fix.emptytoall",
                        this,
                        tr("You entered an empty query. Do you want to download all data in this area instead?"),
                        tr("Download all data?"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        JOptionPane.YES_OPTION);
                if (doFix) {
                    String repairedQuery = "[out:xml]; \n"
                            + query + "\n"
                            + "(\n"
                            + "    node({{bbox}});\n"
                            + "<;\n"
                            + ");\n"
                            + "(._;>;);"
                            + "out meta;";
                    this.overpassQuery.setText(repairedQuery);
                } else {
                    return false;
                }
            }

            return true;
        }

        /**
         * Sets query to the query text field.
         * @param query The query to set.
         */
        public void setOverpassQuery(String query) {
            Objects.requireNonNull(query, "query");
            this.overpassQuery.setText(query);
        }

        @Override
        public Icon getIcon() {
            return ImageProvider.get("download-overpass");
        }

        @Override
        public String getSimpleName() {
            return SIMPLE_NAME;
        }

        @Override
        public DownloadSourceSizingPolicy getSizingPolicy() {
            return new AdjustableDownloadSizePolicy(PANEL_SIZE_PROPERTY);
        }

        /**
         * Action that delegates snippet creation to {@link UserQueryList#createNewItem()}.
         */
        private class AddSnippetAction extends AbstractAction {

            /**
             * Constructs a new {@code AddSnippetAction}.
             */
            AddSnippetAction() {
                new ImageProvider(ACTION_IMG_SUBDIR, "add").getResource().attachImageIcon(this, true);
                putValue(SHORT_DESCRIPTION, tr("Add new snippet"));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                overpassQueryList.createNewItem();
            }
        }

        /**
         * Action that delegates snippet removal to {@link UserQueryList#removeSelectedItem()}.
         */
        private class RemoveSnippetAction extends AbstractAction implements ListSelectionListener {

            /**
             * Constructs a new {@code RemoveSnippetAction}.
             */
            RemoveSnippetAction() {
                new ImageProvider(ACTION_IMG_SUBDIR, "delete").getResource().attachImageIcon(this, true);
                putValue(SHORT_DESCRIPTION, tr("Delete selected snippet"));
                checkEnabled();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                overpassQueryList.removeSelectedItem();
            }

            /**
             * Disables the action if no items are selected.
             */
            void checkEnabled() {
                setEnabled(overpassQueryList.getSelectedItem().isPresent());
            }

            @Override
            public void valueChanged(ListSelectionEvent e) {
                checkEnabled();
            }
        }

        /**
         * Action that delegates snippet edit to {@link UserQueryList#editSelectedItem()}.
         */
        private class EditSnippetAction extends AbstractAction implements ListSelectionListener {

            /**
             * Constructs a new {@code EditSnippetAction}.
             */
            EditSnippetAction() {
                super();
                new ImageProvider(ACTION_IMG_SUBDIR, "edit").getResource().attachImageIcon(this, true);
                putValue(SHORT_DESCRIPTION, tr("Edit selected snippet"));
                checkEnabled();
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                overpassQueryList.editSelectedItem();
            }

            /**
             * Disables the action if no items are selected.
             */
            void checkEnabled() {
                setEnabled(overpassQueryList.getSelectedItem().isPresent());
            }

            @Override
            public void valueChanged(ListSelectionEvent e) {
                checkEnabled();
            }
        }

        @Override
        public void submitWizardResult(String resultingQuery) {
            setOverpassQuery(resultingQuery);
        }
    }

    /**
     * Encapsulates data that is required to preform download from Overpass API.
     */
    static class OverpassDownloadData {
        private final String query;
        private final Consumer<Collection<Object>> errorReporter;

        OverpassDownloadData(String query, Consumer<Collection<Object>> errorReporter) {
            this.query = query;
            this.errorReporter = errorReporter;
        }

        String getQuery() {
            return this.query;
        }

        Consumer<Collection<Object>> getErrorReporter() {
            return this.errorReporter;
        }
    }

}
