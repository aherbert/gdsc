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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.function.Predicate;
import java.util.logging.Logger;
import uk.ac.sussex.gdsc.core.utils.FileUtils;

/**
 * Manages I/O of the TimeValuePoint class.
 */
public class TimeValuePointManager {
  private final String filename;
  private FileType type;

  // Used to parse the input file
  private int[] fields;
  private int maxField;
  private String delimiter;
  private String line;
  private int lineCount;

  /**
   * The file type.
   */
  public enum FileType {
    /** The find foci. */
    FIND_FOCI,
    /** Comma-separated values using format [id,t,x,y,z,v]. */
    CSV_IDTXYZV,
    /** Comma-separated values using format [id,t,x,y]. */
    CSV_IDTXY,
    /** Tab delimited using format [id,t,x,y,z,v]. */
    TAB_IDTXYZV,
    /** Tab delimited using format [id,t,x,y]. */
    TAB_IDTXY,
    /** The Quick PALM format. */
    QUICK_PALM,
    /** The STORMJ format. */
    STORMJ,
    /** Tab delimited using format [x,y,z]. */
    TAB_XYZ,
    /** Comma-separated values using format [x,y,z]. */
    CSV_XYZ,
    /** Unknown file-format. */
    UNKNOWN
  }

  /**
   * Instantiates a new time value point manager.
   *
   * @param filename the filename
   */
  public TimeValuePointManager(String filename) {
    this.filename = filename;
  }

  /**
   * Attempts to detect the file type by reading the initial lines.
   *
   * @return The type of file containing the points
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public FileType getFileType() throws IOException {
    if (type != null) {
      return type;
    }

    try (final BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
      // Load results from file
      String currentLine;
      int lines = 1;
      int nonHeaderLines = 0;
      while ((currentLine = input.readLine()) != null) {
        type = guessFiletype(currentLine, lines++);

        if (!currentLine.startsWith("#")) {
          nonHeaderLines++;
        }

        // Stop after several non-header lines
        if (type != FileType.UNKNOWN || nonHeaderLines > 1) {
          break;
        }
      }
    }

    return type;
  }

  private static FileType guessFiletype(String line, int lineCount) {
    int delimiterCount = countDelimeters(line, "\t");

    // FindFoci file
    if (line.startsWith("Peak #\tMask Value") && delimiterCount >= 5) {
      return FileType.FIND_FOCI;
    }

    // Look for a unique text for the QuickPALM file on the first line
    if (lineCount == 1 && line.contains("Up-Height") && delimiterCount >= 14) {
      return FileType.QUICK_PALM;
    }

    // STORMJ file can have extra header lines so ignore line count
    if (line.startsWith("#") && line.contains("origX") && delimiterCount >= 12) {
      return FileType.STORMJ;
    }

    // Tab separated fields: ID,T,X,Y,Z,Value
    if (delimiterCount >= 5) {
      return FileType.TAB_IDTXYZV;
    }
    // File is allowed to have Z and Value missing
    if (delimiterCount >= 3) {
      return FileType.TAB_IDTXY;
    }
    if (delimiterCount == 2) {
      return FileType.TAB_XYZ;
    }

    // Comma separated fields: ID,T,X,Y,Z,Value
    delimiterCount = countDelimeters(line, ",");
    if (delimiterCount >= 5) {
      return FileType.CSV_IDTXYZV;
    }
    // File is allowed to have Z and Value missing
    if (delimiterCount >= 3) {
      return FileType.CSV_IDTXY;
    }
    if (delimiterCount == 2) {
      return FileType.CSV_XYZ;
    }

    return FileType.UNKNOWN;
  }

  private static int countDelimeters(String text, String delimiter) {
    return text.split(delimiter).length - 1;
  }

  /**
   * Save the points to file.
   *
   * @param points the points
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public void savePoints(TimeValuedPoint[] points) throws IOException {
    if (points == null) {
      return;
    }

    final Path path = Paths.get(filename);
    FileUtils.createParent(path);

    try (final BufferedWriter out = Files.newBufferedWriter(path)) {
      // Save results to file
      final StringBuilder sb = new StringBuilder();

      out.write("ID,T,X,Y,Z,Value");
      out.newLine();

      // Output all results in ascending rank order
      int id = 0;
      for (final TimeValuedPoint point : points) {
        sb.append(++id).append(',');
        sb.append(point.getTime()).append(',');
        sb.append(point.getX()).append(',');
        sb.append(point.getY()).append(',');
        sb.append(point.getZ()).append(',');
        sb.append(point.getValue());
        out.append(sb);
        out.newLine();
        sb.setLength(0);
      }
    }
  }

  /**
   * Save the points to the given file.
   *
   * @param points the points
   * @param filename the filename
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static void savePoints(TimeValuedPoint[] points, String filename) throws IOException {
    new TimeValuePointManager(filename).savePoints(points);
  }

  /**
   * Loads the points from the file.
   *
   * @return the time valued point[]
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public TimeValuedPoint[] loadPoints() throws IOException {
    getFileType();

    if (type == FileType.UNKNOWN || !setupParser()) {
      return new TimeValuedPoint[0];
    }

    final LinkedList<TimeValuedPoint> points = new LinkedList<>();
    try (final BufferedReader input = Files.newBufferedReader(Paths.get(filename))) {
      // Load results from file

      skipHeader(input);

      int errors = 0;
      while (line != null) {
        final String[] tokens = line.split(delimiter);
        if (tokens.length > maxField) {
          try {
            // int id = Integer.parseInt(tokens[fields[0]]); // Not currently needed
            float time = 1;
            if (fields[1] >= 0) {
              time = Float.parseFloat(tokens[fields[1]]); // QuickPALM uses a float for the time
            }
            final float xpos = Float.parseFloat(tokens[fields[2]]);
            final float ypos = Float.parseFloat(tokens[fields[3]]);
            float zpos = 0;
            if (fields[4] >= 0) {
              zpos = Float.parseFloat(tokens[fields[4]]);
              if (type == FileType.QUICK_PALM) {
                // z is in nm and so must be converted to approximate pixels
                final float xNm = Float.parseFloat(tokens[4]);
                zpos *= xpos / xNm;
              }
            }
            float value = 0;
            if (fields[5] >= 0) {
              value = Float.parseFloat(tokens[fields[5]]);
            }
            points.add(new TimeValuedPoint(xpos, ypos, zpos, (int) time, value));
          } catch (final NumberFormatException ex) {
            Logger.getLogger(getClass().getName())
                .warning(() -> "Invalid numbers on line: " + lineCount);
            if (++errors > 10) {
              break;
            }
          }
        }
        readLine(input);
      }

      return points.toArray(new TimeValuedPoint[0]);
    }
  }


  /**
   * Loads the points from the file.
   *
   * @param filename the filename
   * @return The points
   * @throws IOException Signals that an I/O exception has occurred.
   */
  public static TimeValuedPoint[] loadPoints(String filename) throws IOException {
    return new TimeValuePointManager(filename).loadPoints();
  }

  private boolean setupParser() {
    maxField = 0;
    lineCount = 0;
    line = null;

    switch (type) {
      case FIND_FOCI:
        delimiter = "\t";
        // Store the object field in T
        fields = new int[] {0, 20, 2, 3, 4, 5};
        break;

      case QUICK_PALM:
        delimiter = "\t";
        fields = new int[] {0, 14, 2, 3, 6, 1};
        break;

      case STORMJ:
        delimiter = "\t";
        // No ID or z-dimension
        fields = new int[] {-1, 0, 10, 11, -1, 6};
        break;

      case TAB_IDTXYZV:
        delimiter = "\t";
        // ID,T,X,Y,Z,Value
        fields = new int[] {0, 1, 2, 3, 4, 5};
        break;

      case TAB_IDTXY:
        delimiter = "\t";
        // ID,T,X,Y
        fields = new int[] {0, 1, 2, 3, -1, -1};
        break;

      case TAB_XYZ:
        delimiter = "\t";
        // X,Y,Z
        fields = new int[] {-1, -1, 0, 1, 2, -1};
        break;

      case CSV_IDTXYZV:
        delimiter = ",";
        // ID,T,X,Y,Z,Value
        fields = new int[] {0, 1, 2, 3, 4, 5};
        break;

      case CSV_IDTXY:
        delimiter = ",";
        // ID,T,X,Y
        fields = new int[] {0, 1, 2, 3, -1, -1};
        break;

      case CSV_XYZ:
        delimiter = ",";
        // X,Y,Z
        fields = new int[] {-1, -1, 0, 1, 2, -1};
        break;

      default:
        return false;
    }

    for (final int i : fields) {
      if (maxField < i) {
        maxField = i;
      }
    }

    return true;
  }

  /**
   * Reads lines from the input until the first record is reached. Leaves the line variable at the
   * first record.
   *
   * @param input the input
   * @throws IOException Signals that an I/O exception has occurred.
   */
  private void skipHeader(BufferedReader input) throws IOException {

    if (type == FileType.QUICK_PALM) {
      // First line is the header so read this
      readLine(input);
    }

    Predicate<String> headerTest;

    if (type == FileType.FIND_FOCI) {
      headerTest = text -> text.startsWith("#") || text.startsWith("Peak #");
    } else {
      headerTest = text -> text.startsWith("#");
    }

    // Read until no comment character
    do {
      readLine(input);
    } while (line != null && headerTest.test(line));
  }

  private void readLine(BufferedReader input) throws IOException {
    line = input.readLine();
    lineCount++;
  }
}
