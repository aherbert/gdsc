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

package uk.ac.sussex.gdsc.ij.threshold;

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.ac.sussex.gdsc.ij.utils.ImageScienceUtils;

@SuppressWarnings({"javadoc"})
class EdgeMaskPlugInTest {
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void testAboveBackground(boolean floatImage) throws ReflectiveOperationException {
    Assumptions.assumeTrue(ImageScienceUtils.hasImageScience());
    // Values 0 to 99
    final ImageProcessor ip = floatImage ? new FloatProcessor(10, 10) : new ShortProcessor(10, 10);
    for (int i = 0; i < 100; i++) {
      ip.setf(i, i + 0.25f);
    }
    final EdgeMask_PlugIn plugin = new EdgeMask_PlugIn();
    plugin.setup("", new ImagePlus("test", ip));
    // Configure: Above background method; no smoothing; background level 50
    final Field field = EdgeMask_PlugIn.class.getDeclaredField("settings");
    field.setAccessible(true);
    final Object settings = field.get(plugin);
    setField(settings, "method", 0);
    setField(settings, "smooth", 0.0);
    setField(settings, "background", 50.0);

    plugin.createMask(ip);
    for (int i = 0; i < 100; i++) {
      Assertions.assertEquals(i >= 50 ? 255 : 0, ip.getf(i), "Pixel " + i);
    }
  }

  private static void setField(Object object, String name, Object value)
      throws ReflectiveOperationException {
    final Field field = object.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(object, value);
  }
}
