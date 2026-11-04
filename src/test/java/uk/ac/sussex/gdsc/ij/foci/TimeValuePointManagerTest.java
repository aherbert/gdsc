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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uk.ac.sussex.gdsc.ij.foci.TimeValuePointManager.FileType;

@SuppressWarnings({"javadoc"})
class TimeValuePointManagerTest {
  @Test
  void testLoadFindFociUsesObjectAsTime(@TempDir Path dir) throws IOException {
    final String header = FindFoci_PlugIn.createResultsHeader("\n");
    final String[] columns = header.trim().split("\t");
    final int object = java.util.Arrays.asList(columns).indexOf("Object");
    Assertions.assertTrue(object > 0);
    final StringBuilder sb = new StringBuilder("# Image\ttest\n").append(header);
    // Two foci: x, y, z, size, object
    final int[][] data = {{10, 20, 1, 5, 3}, {30, 40, 2, 7, 4}};
    for (final int[] d : data) {
      final String[] row = new String[columns.length];
      java.util.Arrays.fill(row, "99.5");
      row[0] = "1";
      row[2] = Integer.toString(d[0]);
      row[3] = Integer.toString(d[1]);
      row[4] = Integer.toString(d[2]);
      row[5] = Integer.toString(d[3]);
      row[object] = Integer.toString(d[4]);
      sb.append(String.join("\t", row)).append('\n');
    }
    final Path file = dir.resolve("FindFoci.xls");
    Files.write(file, sb.toString().getBytes());

    final TimeValuePointManager manager = new TimeValuePointManager(file.toString());
    Assertions.assertEquals(FileType.FIND_FOCI, manager.getFileType());
    final TimeValuedPoint[] points = manager.loadPoints();
    Assertions.assertEquals(data.length, points.length);
    for (int i = 0; i < data.length; i++) {
      Assertions.assertEquals(data[i][0], points[i].getX());
      Assertions.assertEquals(data[i][1], points[i].getY());
      Assertions.assertEquals(data[i][2], points[i].getZ());
      Assertions.assertEquals(data[i][3], points[i].getValue());
      Assertions.assertEquals(data[i][4], points[i].getTime());
    }
  }

  @Test
  void testLoadFindFociWithoutObjectColumn(@TempDir Path dir) throws IOException {
    final Path file = dir.resolve("FindFoci.xls");
    Files.write(file, "Peak #\tMask Value\tX\tY\tZ\tSize\n1\t2\t3\t4\t5\t6\n".getBytes());
    final TimeValuedPoint[] points = TimeValuePointManager.loadPoints(file.toString());
    Assertions.assertEquals(1, points.length);
    Assertions.assertEquals(3, points[0].getX());
    Assertions.assertEquals(1, points[0].getTime());
    Assertions.assertEquals(6, points[0].getValue());
  }
}
