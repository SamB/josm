// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.io.importexport;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.testutils.JOSMTestRules;

/**
 * Unit tests of {@link NoteImporter} class.
 */
class NoteImporterTest {

    /**
     * Use the test rules to remove any layers and reset state.
     */
    @RegisterExtension
    public final JOSMTestRules rules = new JOSMTestRules();

    /**
     * Non-regression test for <a href="https://josm.openstreetmap.de/ticket/12531">Bug #12531</a>.
     */
    @Test
    void testTicket12531() {
        MainApplication.getLayerManager().resetState();
        assertNull(MainApplication.getMap());
        assertTrue(new NoteImporter().importDataHandleExceptions(
                new File(TestUtils.getRegressionDataFile(12531, "notes.osn")), null));
    }
}
