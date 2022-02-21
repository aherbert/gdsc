/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2022 Alex Herbert
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

import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import org.apache.commons.rng.core.source64.SplitMix64;
import org.junit.jupiter.api.Assertions;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.test.junit5.SeededTest;
import uk.ac.sussex.gdsc.test.utils.RandomSeed;

@SuppressWarnings({"javadoc"})
class SpotSeparationPlugInTest {

  @SeededTest
  void checkTheAngleIsTheSameWhenTheImageWeightIsOne(RandomSeed seed) {
    final int size = 100;
    final ByteProcessor spotIp = new ByteProcessor(size, size);
    // The rectangle should be centred
    final int ox = 10;
    final int oy = 20;
    spotIp.setRoi(new Rectangle(ox, oy, size - 2 * ox, size - 2 * oy));
    final int peakId = 255;
    spotIp.setValue(peakId);
    spotIp.fill();
    final double degrees = new SplitMix64(seed.getAsLong()).nextDouble() * 180;
    spotIp.setInterpolationMethod(ImageProcessor.NONE);
    spotIp.rotate(degrees);

    final int cx = size / 2;
    final float[] com = new float[2];
    final double angle1 = SpotSeparation_PlugIn.calculateOrientation(spotIp, cx, cx, peakId, com);
    final FloatProcessor ip =
        new FloatProcessor(size, size, SimpleArrayUtils.newFloatArray(size * size, 1));
    final double angle2 =
        SpotSeparation_PlugIn.calculateOrientation(ip, spotIp, cx, cx, peakId, com);

    Assertions.assertEquals(angle1, angle2, "Angles should be the same when the image weight is 1");
  }
}
