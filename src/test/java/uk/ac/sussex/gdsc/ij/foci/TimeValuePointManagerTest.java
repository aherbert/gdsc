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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
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

  /**
   * Load the points and capture any warnings.
   */
  private static TimeValuedPoint[] loadPoints(Path file, List<String> warnings)
      throws IOException {
    final Logger logger = Logger.getLogger(TimeValuePointManager.class.getName());
    final Handler handler = new Handler() {
      @Override
      public void publish(LogRecord record) {
        if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
          warnings.add(record.getMessage());
        }
      }

      @Override
      public void flush() {
        // Do nothing
      }

      @Override
      public void close() {
        // Do nothing
      }
    };
    logger.addHandler(handler);
    try {
      return TimeValuePointManager.loadPoints(file.toString());
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void testSavedPointsLoadWithoutWarnings(@TempDir Path dir) throws IOException {
    final TimeValuedPoint[] points = {new TimeValuedPoint(1, 2, 3, 4, 5.5f),
        new TimeValuedPoint(6, 7, 8, 9, 10.5f)};
    final Path file = dir.resolve("points.csv");
    TimeValuePointManager.savePoints(points, file.toString());
    Assertions.assertTrue(Files.readAllLines(file).get(0).startsWith("ID,"));

    final List<String> warnings = new ArrayList<>();
    final TimeValuedPoint[] loaded = loadPoints(file, warnings);
    Assertions.assertEquals(Collections.emptyList(), warnings);
    Assertions.assertEquals(points.length, loaded.length);
    for (int i = 0; i < points.length; i++) {
      Assertions.assertEquals(points[i].getX(), loaded[i].getX());
      Assertions.assertEquals(points[i].getY(), loaded[i].getY());
      Assertions.assertEquals(points[i].getZ(), loaded[i].getZ());
      Assertions.assertEquals(points[i].getTime(), loaded[i].getTime());
      Assertions.assertEquals(points[i].getValue(), loaded[i].getValue());
    }
  }

  @Test
  void testColumnHeaderIsOnlySkippedOnce(@TempDir Path dir) throws IOException {
    // Tab delimited XYZ with a header and no header
    final Path file = dir.resolve("points.txt");
    Files.write(file, "# Comment\nX\tY\tZ\n1\t2\t3\nA\tB\tC\n4\t5\t6\n".getBytes());
    final List<String> warnings = new ArrayList<>();
    TimeValuedPoint[] loaded = loadPoints(file, warnings);
    Assertions.assertEquals(2, loaded.length);
    // The invalid line after the data is reported
    Assertions.assertEquals(1, warnings.size());

    Files.write(file, "1\t2\t3\n4\t5\t6\n".getBytes());
    warnings.clear();
    loaded = loadPoints(file, warnings);
    Assertions.assertEquals(2, loaded.length);
    Assertions.assertEquals(1, loaded[0].getX());
    Assertions.assertEquals(Collections.emptyList(), warnings);
  }
}
