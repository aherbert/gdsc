/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2020 Alex Herbert
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

package uk.ac.sussex.gdsc.foci;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;

@SuppressWarnings({"javadoc"})
class ObjectAnalyzer3DTest {
  @Test
  void checkDimensions() {
    final int maxx = 5;
    final int maxy = 7;
    final int maxz = 3;
    final int[] image = new int[maxx * maxy * maxz];
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    Assertions.assertEquals(maxx, oa.getMaxX());
    Assertions.assertEquals(maxy, oa.getMaxY());
    Assertions.assertEquals(maxz, oa.getMaxZ());
  }

  @Test
  void check8ConnectedProperty() {
    final int maxx = 2;
    final int maxy = 3;
    final int maxz = 4;
    final int[] image = new int[maxx * maxy * maxz];
    ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    Assertions.assertFalse(oa.isEightConnected());
    oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz, true);
    Assertions.assertTrue(oa.isEightConnected());
    oa.setEightConnected(false);
    Assertions.assertFalse(oa.isEightConnected());
    oa.setEightConnected(false);
    Assertions.assertFalse(oa.isEightConnected());
  }

  @Test
  void checkGetObjects2d() {
    final int maxx = 4;
    final int maxy = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, 1);
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
  void checkMinObjectSizeProperty2d() {
    final int maxx = 4;
    final int maxy = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, 1);
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
  void checkGetObjectCentres2d() {
    final int maxx = 4;
    final int maxy = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, 1);
    oa.getMaxObject();
    final double[][] centres = {
    //@formatter:off
        {0, 0, 0, 0},
        {2, 0, 0, 1},
        {3, 0, 0, 1},
        {1, 1.5, 0, 2},
        {2.5, 2, 0, 2},
    };
    //@formatter:on
    Assertions.assertArrayEquals(centres, oa.getObjectCentres());
  }

  @Test
  void checkGetSurfaceCount2d() {
    final int maxx = 4;
    final int maxy = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, 1);
    oa.getMaxObject();
    final int[][] counts = {
    //@formatter:off
        {0, 0, 0},
        {2, 2, 2},
        {2, 2, 2},
        {4, 2, 4},
        {4, 4, 2},
    };
    //@formatter:on
    Assertions.assertArrayEquals(counts, oa.getSurfaceCount());
  }

  @Test
  void checkGetObjects3d() {
    final int maxx = 4;
    final int maxy = 3;
    final int maxz = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 0, 0, 0,
        0, 0, 0, 7,

        0, 0, 0, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,

        0, 0, 3, 0,
        0, 3, 0, 0,
        0, 3, 7, 0,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    // 4n connected
    Assertions.assertFalse(oa.isEightConnected());
    Assertions.assertEquals(5, oa.getMaxObject());
    final int[] m1 = {
    //@formatter:off
        0, 0, 1, 2,
        0, 0, 0, 0,
        0, 0, 0, 3,

        0, 0, 0, 2,
        0, 4, 0, 0,
        0, 4, 3, 3,

        0, 0, 5, 0,
        0, 4, 0, 0,
        0, 4, 3, 0,
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
        0, 0, 0, 0,
        0, 0, 0, 3,

        0, 0, 0, 2,
        0, 1, 0, 0,
        0, 1, 3, 3,

        0, 0, 1, 0,
        0, 1, 0, 0,
        0, 1, 3, 0,
    };
    //@formatter:on
    Assertions.assertArrayEquals(m2, oa.getObjectMask());
  }

  @Test
  void checkGetObjectCentres3d() {
    final int maxx = 4;
    final int maxy = 3;
    final int maxz = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 0, 0, 0,
        0, 0, 0, 7,

        0, 0, 0, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,

        0, 0, 3, 0,
        0, 3, 0, 0,
        0, 3, 7, 0,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    oa.getMaxObject();
    final double[][] centres = {
    //@formatter:off
        {0, 0, 0, 0},
        {2, 0, 0, 1},
        {3, 0, 0.5, 2},
        {2.5, 2, 1, 4},
        {1, 1.5, 1.5, 4},
        {2, 0, 2, 1},
    };
    //@formatter:on
    Assertions.assertArrayEquals(centres, oa.getObjectCentres());
  }

  @Test
  void checkGetSurfaceCount3d() {
    final int maxx = 4;
    final int maxy = 3;
    final int maxz = 3;
    final int[] image = {
    //@formatter:off
        0, 0, 3, 4,
        0, 0, 0, 0,
        0, 0, 0, 7,

        0, 0, 0, 4,
        0, 3, 0, 0,
        0, 3, 7, 7,

        0, 0, 3, 0,
        0, 3, 0, 0,
        0, 3, 7, 0,
    };
    //@formatter:on
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    oa.getMaxObject();
    final int[][] counts = {
    //@formatter:off
        {0, 0, 0},
        {2, 2, 2},
        {2, 4, 4},
        {4, 8, 6},
        {4, 4, 8},
        {2, 2, 2},
    };
    //@formatter:on
    Assertions.assertArrayEquals(counts, oa.getSurfaceCount());
  }

  @Test
  void testManySmallObjects2d() {
    final int maxx = 35;
    final int maxy = 34;
    final int maxz = 1;
    final int[] image = new int[maxx * maxy * maxz];
    // 1010101 ...
    // 0101010 ...
    // ...
    int object = 0;
    final int[] m1 = new int[image.length];
    for (int i = 0; i < image.length; i += 2) {
      image[i] = 1;
      m1[i] = ++object;
    }
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    Assertions.assertEquals(object, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, oa.getObjectMask());
  }

  @Test
  void testManySmallObjects3d() {
    final int maxx = 35;
    final int maxy = 34;
    final int maxz = 3;
    final int[] image = new int[maxx * maxy * maxz];
    // 1010101 ...
    // 0101010 ...
    // ...
    int object = 0;
    final int[] m1 = new int[image.length];
    int xy = maxx * maxy;
    for (int z = 0; z < maxz; z++) {
      int i = z * xy + z % 2;
      for (int j = z % 2; j < xy; i += 2, j += 2) {
        image[i] = 1;
        m1[i] = ++object;
      }
    }
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    Assertions.assertEquals(object, oa.getMaxObject());
    Assertions.assertArrayEquals(m1, oa.getObjectMask());
  }

  @Test
  void testSingleLargeObjects2d() {
    // Big enough to require expansion of the search list
    final int maxx = 35;
    final int maxy = 34;
    final int maxz = 1;
    final int[] image = SimpleArrayUtils.newIntArray(maxx * maxy * maxz, 3);
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    Assertions.assertEquals(1, oa.getMaxObject());
    Assertions.assertArrayEquals(SimpleArrayUtils.newIntArray(image.length, 1), oa.getObjectMask());
  }

  @Test
  void testSingleLargeObjects3d() {
    // Big enough to require expansion of the search list
    final int maxx = 35;
    final int maxy = 34;
    final int maxz = 3;
    final int[] image = SimpleArrayUtils.newIntArray(maxx * maxy * maxz, 3);
    final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(image, maxx, maxy, maxz);
    Assertions.assertEquals(1, oa.getMaxObject());
    Assertions.assertArrayEquals(SimpleArrayUtils.newIntArray(image.length, 1), oa.getObjectMask());
  }
}
