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

import ij.Prefs;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.ij.foci.NucleiOutline_PlugIn.Nucleus;
import uk.ac.sussex.gdsc.ij.foci.NucleiOutline_PlugIn.Settings;

@SuppressWarnings({"javadoc"})
class NucleiOutlinePlugInTest {
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testOutliersAreRemoved(boolean blackBackground) {
    // Image with a nucleus and a single pixel outlier
    final ByteProcessor bp = new ByteProcessor(64, 64);
    final int cx = 20;
    final int cy = 20;
    final int r = 10;
    for (int y = cy - r; y <= cy + r; y++) {
      for (int x = cx - r; x <= cx + r; x++) {
        if ((x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r) {
          bp.set(x, y, 200);
        }
      }
    }
    bp.set(50, 50, 200);

    final Settings settings = new Settings();
    settings.setBlur1(0);
    settings.setMethod(AutoThreshold.Method.OTSU);
    settings.setOutlierRadius(2);
    settings.setOutlierThreshold(50);
    settings.setMinNucleusSize(0);
    settings.setMaxNucleusSize(1e10);

    final boolean previous = Prefs.blackBackground;
    try {
      Prefs.blackBackground = blackBackground;
      final Nucleus[] nuclei = NucleiOutline_PlugIn.analyseImage(settings, 1, bp);
      Assertions.assertEquals(1, nuclei.length, "Outlier pixel should be removed");
      Assertions.assertEquals(cx, nuclei[0].getObjectCentre().getCentreX(), 0.5);
      Assertions.assertEquals(cy, nuclei[0].getObjectCentre().getCentreY(), 0.5);
    } finally {
      Prefs.blackBackground = previous;
    }
  }
}
