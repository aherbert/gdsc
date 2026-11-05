/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2026 Alex Herbert
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package uk.ac.sussex.gdsc.ij.foci;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.ij.foci.TranslocationFinder_PlugIn.Settings;

@SuppressWarnings({"javadoc"})
class TranslocationFinderPlugInTest {
  @Test
  void testDefaultSettingsHaveEmptyTriplets() {
    // The results table double-click handler checks both lists before the plugin
    // or the tool has been run
    final Settings settings = Settings.load();
    Assertions.assertNotNull(settings.triplets);
    Assertions.assertTrue(settings.triplets.isEmpty());
    Assertions.assertNotNull(settings.manualTriplets);
    Assertions.assertTrue(settings.manualTriplets.isEmpty());
  }

  @Test
  void testIsSeparated() {
    final double factor = 2;
    Assertions.assertTrue(TranslocationFinder_PlugIn.isSeparated(1, 3, 3, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(1, 2, 3, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(1, 3, 2, factor));
    // Zero distance
    Assertions.assertTrue(TranslocationFinder_PlugIn.isSeparated(0, 1, 1, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(0, 0, 1, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(0, 0, 0, factor));
    // Min distance
    Assertions.assertTrue(TranslocationFinder_PlugIn.isSeparated(1, 9, 9, 8, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(1, 8, 9, 8, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(1, 9, 8, 8, factor));
    Assertions.assertTrue(TranslocationFinder_PlugIn.isSeparated(0, 9, 9, 8, factor));
    Assertions.assertFalse(TranslocationFinder_PlugIn.isSeparated(5, 9, 9, 8, factor));
  }
}
