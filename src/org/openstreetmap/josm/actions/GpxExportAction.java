// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.actions.SaveActionBase.createAndOpenSaveFileChooser;
import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.text.MessageFormat;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.io.importexport.FileExporter;
import org.openstreetmap.josm.gui.io.importexport.GpxExporter;
import org.openstreetmap.josm.gui.io.importexport.GpxImporter;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Exports data to gpx.
 * @since 78
 */
public class GpxExportAction extends DiskAccessAction {

    /**
     * Constructs a new {@code GpxExportAction}.
     */
    public GpxExportAction() {
        super(tr("Export to GPX..."), "exportgpx", tr("Export the data to GPX file."),
                Shortcut.registerShortcut("file:exportgpx", tr("File: {0}", tr("Export to GPX...")), KeyEvent.VK_E, Shortcut.CTRL));
        setHelpId(ht("/Action/GpxExport"));
    }

    /**
     * Deferring constructor for child classes.
     *
     * @param name see {@code DiskAccessAction}
     * @param iconName see {@code DiskAccessAction}
     * @param tooltip see {@code DiskAccessAction}
     * @param shortcut see {@code DiskAccessAction}
     * @param register see {@code DiskAccessAction}
     * @param toolbarId see {@code DiskAccessAction}
     * @param installAdapters see {@code DiskAccessAction}
     *
     * @since 13210
     */
    protected GpxExportAction(String name, String iconName, String tooltip, Shortcut shortcut,
            boolean register, String toolbarId, boolean installAdapters) {
        super(name, iconName, tooltip, shortcut, register, toolbarId, installAdapters);
    }

    /**
     * Get the layer to export.
     * @return The layer to export, if supported, otherwise {@code null}.
     */
    protected Layer getLayer() {
        Layer layer = getLayerManager().getActiveLayer();
        return GpxExporter.isSupportedLayer(layer) ? layer : null;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled())
            return;
        Layer layer = getLayer();
        if (layer == null) {
            JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(),
                    tr("Nothing to export. Get some data first."),
                    tr("Information"),
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        export(layer);
    }

    /**
     * Exports a layer to a file. Launches a file chooser to request the user to enter a file name.
     *
     * <code>layer</code> must not be null. <code>layer</code> must be of a supported type.
     *
     * @param layer the layer
     * @throws IllegalArgumentException if layer is null
     * @throws IllegalArgumentException if layer is not of a supported type.
     * @see GpxExporter#isSupportedLayer
     */
    public void export(Layer layer) {
        CheckParameterUtil.ensureParameterNotNull(layer, "layer");
        if (!GpxExporter.isSupportedLayer(layer))
            throw new IllegalArgumentException(MessageFormat.format("Expected instance of {0}. Got ''{1}''.",
                    GpxExporter.getSupportedLayers(), layer.getClass().getName()));

        File file = createAndOpenSaveFileChooser(tr("Export GPX file"), GpxImporter.getFileFilter());
        if (file == null)
            return;

        for (FileExporter exporter : ExtensionFileFilter.getExporters()) {
            if (exporter.acceptFile(file, layer)) {
                try {
                    exporter.exportData(file, layer);
                } catch (IOException | InvalidPathException e) {
                    SaveActionBase.showAndLogException(e);
                }
            }
        }
    }

    @Override
    protected boolean listenToSelectionChange() {
        return false;
    }

    /**
     * Refreshes the enabled state
     */
    @Override
    protected void updateEnabledState() {
        setEnabled(getLayer() != null);
    }
}
