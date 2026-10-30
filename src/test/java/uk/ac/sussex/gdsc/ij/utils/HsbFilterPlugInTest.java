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

package uk.ac.sussex.gdsc.ij.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"javadoc"})
class HsbFilterPlugInTest {
  private static final float DELTA = 1e-6f;

  @Test
  void testHueDistance() {
    Assertions.assertEquals(0, HsbFilter_PlugIn.hueDistance(0.3f, 0.3f), DELTA);
    Assertions.assertEquals(0.2f, HsbFilter_PlugIn.hueDistance(0.3f, 0.5f), DELTA);
    Assertions.assertEquals(0.2f, HsbFilter_PlugIn.hueDistance(0.5f, 0.3f), DELTA);
    // Maximum distance
    Assertions.assertEquals(0.5f, HsbFilter_PlugIn.hueDistance(0.25f, 0.75f), DELTA);
    // Wrap below 0
    Assertions.assertEquals(0.07f, HsbFilter_PlugIn.hueDistance(0.98f, 0.05f), DELTA);
    // Wrap above 1
    Assertions.assertEquals(0.07f, HsbFilter_PlugIn.hueDistance(0.02f, 0.95f), DELTA);
    // A hue of 1 is the same as 0
    Assertions.assertEquals(0, HsbFilter_PlugIn.hueDistance(0f, 1f), DELTA);
  }
}
