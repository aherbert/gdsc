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

import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.ij.foci.ObjectAnalyzer.ObjectCentre;

@SuppressWarnings({"javadoc"})
class ObjectAnalyzerTest {
  @Test
  void checkDimensions() {
    final int maxx = 2;
    final int maxy = 3;
    final ByteProcessor ip = new ByteProcessor(maxx, maxy);
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    Assertions.assertEquals(maxx, oa.getWidth());
    Assertions.assertEquals(maxy, oa.getHeight());
  }

  @Test
  void check8ConnectedProperty() {
    final int maxx = 2;
    final int maxy = 3;
    final ByteProcessor ip = new ByteProcessor(maxx, maxy);
    ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    Assertions.assertFalse(oa.isEightConnected());
    oa = new ObjectAnalyzer(ip, true);
    Assertions.assertTrue(oa.isEightConnected());
    oa.setEightConnected(false);
    Assertions.assertFalse(oa.isEightConnected());
    oa.setEightConnected(false);
    Assertions.assertFalse(oa.isEightConnected());
  }

  @Test
  void checkGetObjects() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    // 4n connected
    Assertions.assertFalse(oa.isEightConnected());
    Assertions.assertEquals(4, oa.getMaxObject());
    final int[] m1 = {
    //@formatter:off
        0, 0, 1, 2,
        0, 3, 0, 0,
        0, 3, 4, 4,
    };
    //@formatter:on
    Assertions.assertArrayEquals(m1, oa.getObjectMask());

    // 8n connected
    oa.setEightConnected(true);
    Assertions.assertTrue(oa.isEightConnected());
    Assertions.assertEquals(3, oa.getMaxObject());
    final int[] m2 = {
    //@formatter:off
        0, 0, 1, 2,
        0, 1, 0, 0,
        0, 1, 3, 3,
    };
    //@formatter:on
    Assertions.assertArrayEquals(m2, oa.getObjectMask());
  }

  @Test
  void checkMinObjectSizeProperty() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    Assertions.assertEquals(0, oa.getMinObjectSize());
    oa.setMinObjectSize(2);
    Assertions.assertEquals(2, oa.getMinObjectSize());
    final int[] m1 = {
    //@formatter:off
        0, 0, 0, 0,
        0, 1, 0, 0,
        0, 1, 2, 2,
    };
    //@formatter:on
    Assertions.assertEquals(2, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, oa.getObjectMask());
    oa.setMinObjectSize(2);
    Assertions.assertEquals(2, oa.getMinObjectSize());
    Assertions.assertEquals(2, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, oa.getObjectMask());
  }

  @Test
  void checkGetObjectCentres() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    oa.getMaxObject();
    final ObjectCentre[] centres = {
    //@formatter:off
        null,
        new ObjectCentre(2, 0, 1),
        new ObjectCentre(3, 0, 1),
        new ObjectCentre(1, 1.5, 2),
        new ObjectCentre(2.5, 2, 2),
    };
    //@formatter:on
    Assertions.assertArrayEquals(centres, oa.getObjectCentres());
  }

  @Test
  void testManySmallObjects() {
    final int maxx = 35;
    final int maxy = 34;
    final ByteProcessor ip = new ByteProcessor(maxx, maxy);
    // 1010101 ...
    // 0101010 ...
    // ...
    int object = 0;
    final int[] m1 = new int[ip.getPixelCount()];
    for (int i = 0; i < m1.length; i += 2) {
      ip.set(i, 1);
      m1[i] = ++object;
    }
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    Assertions.assertEquals(object, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, oa.getObjectMask());
  }

  @Test
  void testSingleLargeObjects() {
    // Big enough to require expansion of the search list
    final int maxx = 35;
    final int maxy = 34;
    final ByteProcessor ip = new ByteProcessor(maxx, maxy);
    ip.set(3);
    final ObjectAnalyzer oa = new ObjectAnalyzer(ip);
    Assertions.assertEquals(1, oa.getMaxObject());
    Assertions.assertArrayEquals(SimpleArrayUtils.newIntArray(ip.getPixelCount(), 1),
        oa.getObjectMask());
  }

  @Test
  void checkToProcessor() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    Assertions.assertEquals(4, oa.getMaxObject());
    final byte[] m1 = {
    //@formatter:off
        0, 0, 1, 2,
        0, 3, 0, 0,
        0, 3, 4, 4,
    };
    //@formatter:on
    Assertions.assertArrayEquals(m1, (byte[]) oa.toProcessor().getPixels());
  }

  @Test
  void checkToFloatProcessor() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    Assertions.assertEquals(4, oa.getMaxObject());
    final float[] m1 = {
    //@formatter:off
        0, 0, 1, 2,
        0, 3, 0, 0,
        0, 3, 4, 4,
    };
    //@formatter:on
    Assertions.assertArrayEquals(m1, (float[]) oa.toFloatProcessor().getPixels());
  }

  @Test
  void checkToProcessorWithOver255Objects() {
    final int maxx = 16;
    final int maxy = 17;
    final byte[] image = new byte[maxx * maxy];
    for (int i = 0; i < image.length; i++) {
      image[i] = (byte) i;
    }
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    final short[] m1 = new short[image.length];
    int object = 0;
    for (int i = 1; i < image.length; i++) {
      m1[i] = ((byte) i == 0) ? 0 : (short) ++object;
    }
    Assertions.assertEquals(object, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, (short[]) oa.toProcessor().getPixels());
  }

  @Test
  void checkToProcessorWithOver65535Objects() {
    final int maxx = 16;
    final int maxy = 16 * 259;
    final byte[] image = new byte[maxx * maxy];
    for (int i = 0; i < image.length; i++) {
      image[i] = (byte) i;
    }
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image));
    final float[] m1 = new float[image.length];
    int object = 0;
    for (int i = 1; i < image.length; i++) {
      m1[i] = ((byte) i == 0) ? 0 : ++object;
    }
    Assertions.assertEquals(object, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, (float[]) oa.toProcessor().getPixels());
  }

  @Test
  void checkGetObjectOutlines() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 7, 0,
        0, 3, 7, 0,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image), true);
    final Roi[] rois = oa.getObjectOutlines();
    Assertions.assertEquals(rois.length, oa.getMaxObject() + 1);
    Assertions.assertNull(rois[0]);
    // The outline does not use diagonals
    final float[] x = {2, 3, 3, 2, 2, 1, 1, 2};
    final float[] y = {0, 0, 1, 1, 3, 3, 1, 1};
    Assertions.assertEquals(new PolygonRoi(x, y, Roi.FREEROI), rois[1]);
    Assertions.assertEquals(new Roi(3, 0, 1, 1), rois[2]);
    Assertions.assertEquals(new Roi(2, 1, 1, 2), rois[3]);
  }

  @Test
  void checkGetObjectOutlines2() {
    final int maxx = 4;
    final int maxy = 3;
    final byte[] image = {
    //@formatter:off
        1, 1, 1, 1,
        1, 1, 1, 1,
        1, 1, 1, 1,
    };
    //@formatter:on
    final ObjectAnalyzer oa = new ObjectAnalyzer(new ByteProcessor(maxx, maxy, image), true);
    final Roi[] rois = oa.getObjectOutlines();
    Assertions.assertEquals(rois.length, oa.getMaxObject() + 1);
    Assertions.assertNull(rois[0]);
    Assertions.assertEquals(new Roi(0, 0, maxx, maxy), rois[1]);
  }
}
