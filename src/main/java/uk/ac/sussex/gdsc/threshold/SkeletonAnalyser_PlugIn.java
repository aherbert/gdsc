/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

package uk.ac.sussex.gdsc.threshold;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.annotation.Nullable;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.gui.GenericDialog;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.YesNoCancelDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Skeletonise a mask image. Then produce a set of lines connecting node points on the skeleton.
 *
 * <p>The skeleton is modified from the ImageJ default by removing pixels that are 4-connected to
 * adjacent 8-connected pixels (e.g. North &amp; East, East &amp; South, South &amp; West, West
 * &amp; North) unless 8-connected on the opposite side or 4-connected on the other two sides. This
 * eliminates redundant pixels.
 */
public class SkeletonAnalyser_PlugIn implements PlugInFilter {
  private static final String TITLE = "Skeleton Analyser";
  private static AtomicReference<TextWindow> resultsWindow = new AtomicReference<>();
  /** The write header falg. This is used in headless mode to write the header once. */
  private static AtomicBoolean writeHeader = new AtomicBoolean(true);

  /** The constant for a line terminus (end). */
  public static final byte TERMINUS = (byte) 1;

  /** The constant for a line edge (middle of the line). */
  public static final byte EDGE = (byte) 2;

  /** The constant for a line junction (more than two lines join). */
  public static final byte JUNCTION = (byte) 4;

  /** The constant for a line (end or middle). */
  public static final byte LINE = TERMINUS | EDGE;

  /** The constant for a node point in a line (terminus or junction). */
  public static final byte NODE = TERMINUS | JUNCTION;

  /** The constant for a line skeleton (edge or node). */
  public static final byte SKELETON = EDGE | NODE;

  /** The constant to show a pixel has been processed. */
  public static final byte PROCESSED = (byte) 8;

  /** The constant for each of the 8-connected directions for processing pixels. */
  private static final byte[] PROCESSED_DIRECTIONS = new byte[] {(byte) 1, (byte) 2, (byte) 4,
      (byte) 8, (byte) 16, (byte) 32, (byte) 64, (byte) 128};

  /** The foreground value. Set using the ImageJ system preferences. */
  private int foreground;
  private ImagePlus imp;

  /** The result output. This may be a TextWindow or the ImageJ log window in headless mode. */
  private Consumer<String> resultOutput;

  private int maxx;
  private int maxy;
  private int xlimit;
  private int ylimit;
  private int[] offset;

  private String unit = "px";
  private double unitConversion = 1;

  /** The current settings for the plugin instance. */
  private Settings settings;

  /**
   * Contains the settings that are the re-usable state of the plugin.
   */
  private static class Settings {
    /** The last settings used by the plugin. This should be updated after plugin execution. */
    private static final AtomicReference<Settings> lastSettings =
        new AtomicReference<>(new Settings());

    boolean pruneJunctions;
    int minLength;
    boolean showNodeMap = true;
    boolean showOverlay;
    boolean showTable = true;

    /**
     * Default constructor.
     */
    Settings() {
      // Do nothing
    }

    /**
     * Copy constructor.
     *
     * @param source the source
     */
    private Settings(Settings source) {
      pruneJunctions = source.pruneJunctions;
      minLength = source.minLength;
      showNodeMap = source.showNodeMap;
      showOverlay = source.showOverlay;
      showTable = source.showTable;
    }

    /**
     * Copy the settings.
     *
     * @return the settings
     */
    Settings copy() {
      return new Settings(this);
    }

    /**
     * Load a copy of the settings.
     *
     * @return the settings
     */
    static Settings load() {
      return lastSettings.get().copy();
    }

    /**
     * Save the settings.
     */
    void save() {
      lastSettings.set(this);
    }
  }

  private static class Line {
    final int start;
    final float length;
    final boolean internal;
    final ChainCode code;

    Line(int start, float length, boolean internal, ChainCode code) {
      this.start = start;
      this.length = length;
      this.internal = internal;
      this.code = code;
    }

    /**
     * Compare the lines by start point (ascending).
     *
     * @param o1 the first line
     * @param o2 the second ine
     * @return the result [-1, 0, 1]
     */
    static int compare(Line o1, Line o2) {
      return Integer.compare(o1.start, o2.start);
    }
  }

  private static class LineComparator implements Comparator<Line>, Serializable {
    private static final long serialVersionUID = 1L;
    static final LineComparator INSTANCE = new LineComparator();

    @Override
    public int compare(Line o1, Line o2) {
      if (o1.internal ^ o2.internal) {
        // If one is internal and the other is not
        return (o1.internal) ? -1 : 1;
      }
      // Rank by length (descending)
      return Float.compare(o2.length, o1.length);
    }
  }

  private static class Result {
    float[] line;
    ChainCode code;

    Result(float[] line, ChainCode code) {
      this.line = line;
      this.code = code;
    }

    /**
     * Compare the two objects.
     *
     * <p>Uses greatest distance then the coordinates.
     *
     * @param o1 the first object
     * @param o2 the second object
     * @return a negative integer, zero, or a positive integer if object 1 is less than, equal to,
     *         or greater than object 2.
     */
    static int compare(Result o1, Result o2) {
      final int[] result = new int[1];

      // Distance first
      if (compare(o1.line[4], o2.line[4], result) != 0) {
        return -result[0];
      }

      // Then coordinates
      for (int i = 0; i < 4; i++) {
        if (compare(o1.line[i], o2.line[i], result) != 0) {
          return result[0];
        }
      }

      return 0;
    }

    private static int compare(float value1, float value2, int[] result) {
      if (value1 < value2) {
        result[0] = -1;
      } else if (value1 > value2) {
        result[0] = 1;
      } else {
        result[0] = 0;
      }
      return result[0];
    }
  }

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      IJ.noImage();
      return DONE;
    }
    this.imp = imp;
    final ImageProcessor ip = imp.getProcessor();
    if (!(ip instanceof ByteProcessor) || !((ByteProcessor) ip).isBinary()) {
      IJ.error("Binary image required");
      return DONE;
    }

    if (!showDialog()) {
      return DONE;
    }

    initialise(imp.getWidth(), imp.getHeight());
    return IJ.setupDialog(imp, DOES_8G | SNAPSHOT);
  }

  private boolean showDialog() {
    settings = Settings.load();

    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addCheckbox("Prune_junctions", settings.pruneJunctions);
    gd.addNumericField("Min_length", settings.minLength, 0);
    gd.addCheckbox("Show_node_map", settings.showNodeMap);
    gd.addCheckbox("Show_overlay", settings.showOverlay);
    gd.addCheckbox("Show_table", settings.showTable);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    settings.pruneJunctions = gd.getNextBoolean();
    settings.minLength = (int) gd.getNextNumber();
    settings.showNodeMap = gd.getNextBoolean();
    settings.showOverlay = gd.getNextBoolean();
    settings.showTable = gd.getNextBoolean();

    settings.save();

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    final ByteProcessor bp = (ByteProcessor) ip.convertToByte(false);

    final int width = bp.getWidth();
    final int height = bp.getHeight();

    skeletonise(bp, true);

    final byte[] map = findNodes(bp);
    if (settings.pruneJunctions) {
      boolean pruned = prune(map);
      while (pruned) {
        pruned = prune(map);
      }
    }

    final List<float[]> lines = extractLines(map);

    if (settings.showNodeMap) {
      final ColorProcessor cp = createMapImage(map, width, height);
      final ImagePlus mapImp = ImageJUtils.display(this.imp.getTitle() + " SkeletonNodeMap", cp);
      if (settings.showOverlay) {
        showOverlay(mapImp, lines);
      }
    }

    if (settings.showTable) {
      showResults(lines);
    }
  }

  /**
   * Skeltonise the image processor. Must be a binary image.
   *
   * @param ip the image
   * @param trim Eliminate redundant 4-connected pixels if possible.
   * @return False if not a binary image
   */
  public boolean skeletonise(ByteProcessor ip, boolean trim) {
    if (!ip.isBinary()) {
      return false;
    }

    if (maxx == 0) {
      initialise(ip.getWidth(), ip.getHeight());
    }

    this.foreground = getForeground(ip);

    ip.resetRoi();
    skeletonize(ip, trim);
    ip.setBinaryThreshold();

    return true;
  }

  private static int getForeground(ByteProcessor ip) {
    final int foreground = Prefs.blackBackground ? 255 : 0;
    return ip.isInvertedLut() ? 255 - foreground : foreground;
  }

  /**
   * Search the skeleton and create a node map of the skeleton points. Points can be either:
   * TERMINUS, EDGE or JUNCTION. Points not on the skeleton are set to zero.
   *
   * @param ip Skeletonized image
   * @return The skeleton node map (or null if not a binary processor)
   */
  @Nullable
  public byte[] findNodes(ByteProcessor ip) {
    if (!ip.isBinary()) {
      return null;
    }

    final byte foregroundValue = (byte) getForeground(ip);

    if (maxx == 0) {
      initialise(ip.getWidth(), ip.getHeight());
    }

    final byte[] skeleton = (byte[]) ip.getPixels();
    final byte[] map = new byte[ip.getPixelCount()];

    for (int index = map.length; index-- > 0;) {
      if (skeleton[index] == foregroundValue) {
        // Process the neighbours
        final int count = countRadii(skeleton, index);

        switch (count) {
          case 0:
          case 1:
            map[index] = TERMINUS;
            break;
          case 2:
            map[index] = EDGE;
            break;
          default:
            map[index] = JUNCTION;
            break;
        }
      }
    }

    return map;
  }

  /**
   * Search the skeleton and create a node map of the skeleton points. Points can be either:
   * TERMINUS, EDGE or JUNCTION. Points not on the skeleton are set to zero.
   *
   * @param skeleton the skeleton
   * @return The skeleton node map (or null if not a binary processor)
   */
  private byte[] findNodes(byte[] skeleton) {
    final byte localForeground = (byte) this.foreground;
    final byte[] map = new byte[skeleton.length];

    for (int index = map.length; index-- > 0;) {
      if (skeleton[index] == localForeground) {
        // Process the neighbours
        final int count = countRadii(skeleton, index);

        switch (count) {
          case 0:
          case 1:
            map[index] = TERMINUS;
            break;
          case 2:
            map[index] = EDGE;
            break;
          default:
            map[index] = JUNCTION;
            break;
        }
      }
    }

    return map;
  }

  /**
   * Prune the shortest line from junctions that end in a terminus, until all junctions are
   * eliminated.
   *
   * @param map the map
   * @return true, if successful
   */
  private boolean prune(byte[] map) {
    // Each entry is: startX, startY, endX, endY, length
    final List<ChainCode> chainCodes = new LinkedList<>();
    final List<float[]> lines = extractLines(map, chainCodes);

    // Convert line to start and end index
    final int[] startIndex = new int[lines.size()];
    final int[] endIndex = new int[startIndex.length];
    final float[] lengths = new float[startIndex.length];
    int count = 0;
    for (final float[] line : lines) {
      startIndex[count] = getIndex((int) line[0], (int) line[1]);
      endIndex[count] = getIndex((int) line[2], (int) line[3]);
      lengths[count] = line[4];
      count++;
    }

    final Iterator<ChainCode> it = chainCodes.iterator();

    final ArrayList<Line> junctionLines = new ArrayList<>(lines.size());

    // Find all lines that start at a terminus and end at a junction
    // Find all the lines that start and end at a junction
    for (int i = 0; i < startIndex.length; i++) {
      final ChainCode code = it.next();

      // Ends at a junction
      if ((map[endIndex[i]] & JUNCTION) != 0) {
        // Check if it starts at a junction (i.e. is internal)
        final boolean internal = (map[startIndex[i]] & JUNCTION) != 0;

        // Store the line under the index of the junction, reverse the chain code
        junctionLines.add(new Line(endIndex[i], lengths[i], internal, code.reverse()));

        if (internal) {
          // Starts at a junction so store under this junction as well
          junctionLines.add(new Line(startIndex[i], lengths[i], true, code));
        }
      }
    }

    // Sort by the junction start
    Collections.sort(junctionLines, Line::compare);

    // Each junction should have 3/4 lines if clean up or corner pixels was OK, worst case is 8.
    int lineCount = 0;
    final Line[] currentLines = new Line[8];
    int current = 0;

    final ArrayList<Line> toDelete = new ArrayList<>(100);

    // Process the set of lines for each junction
    for (final Line line : junctionLines) {
      if (current != line.start) {
        if (lineCount != 0) {
          markForDeletion(LineComparator.INSTANCE, lineCount, currentLines, toDelete);
        }
        lineCount = 0;
        current = line.start;
      }
      currentLines[lineCount++] = line;
    }

    if (lineCount != 0) {
      markForDeletion(LineComparator.INSTANCE, lineCount, currentLines, toDelete);
    }

    if (toDelete.isEmpty()) {
      for (count = 0; count < map.length; count++) {
        map[count] |= PROCESSED;
      }
      return false;
    }

    // If any marked for deletion, remove the shortest line
    Collections.sort(toDelete, (o1, o2) -> Float.compare(o1.length, o2.length));

    // Remove the line for deletion
    final ChainCode code = toDelete.get(0).code;
    int xpos = code.getX();
    int ypos = code.getY();
    final int[] run = code.getRun();
    int index = getIndex(xpos, ypos);

    if ((map[index] & LINE) != 0) {
      map[index] = 0;
    }
    for (final int d : run) {
      xpos += ChainCode.getXDirection(d);
      ypos += ChainCode.getYDirection(d);
      index = getIndex(xpos, ypos);
      if ((map[index] & LINE) != 0) {
        map[index] = 0;
      }
    }

    // Create a new map
    final byte byteForeground = (byte) this.foreground;
    final byte byteBackground = (byte) (this.foreground + 1);
    for (index = 0; index < map.length; index++) {
      map[index] = (map[index] == 0) ? byteBackground : byteForeground;
    }
    final byte[] map2 = findNodes(map);
    System.arraycopy(map2, 0, map, 0, map.length);
    return true;
  }

  private static void markForDeletion(final LineComparator lineComparator, int lineCount,
      Line[] currentLines, ArrayList<Line> toDelete) {

    // Sort
    Arrays.sort(currentLines, 0, lineCount, lineComparator);

    // Keep all edges that go to another junction
    int keep = 0;
    while (keep < lineCount && currentLines[keep].internal) {
      keep++;
    }

    // Keep remaining edges in length order (descending) until there are only 2 edges.
    while (keep < lineCount && keep < 2) {
      keep++;
    }

    // Mark others for deletion
    while (keep < lineCount) {
      toDelete.add(currentLines[keep++]);
    }
  }

  /**
   * Creates a colour image of the skeleton node map: TERMINUS = blue; EDGE = red; JUNCTION = green;
   * PROCESSED = cyan.
   *
   * @param map The skeleton node map
   * @param width the width
   * @param height the height
   * @return The colour image processor
   */
  public ColorProcessor createMapImage(byte[] map, int width, int height) {
    final int[] xy = new int[2];
    final ColorProcessor cp = new ColorProcessor(width, height);

    for (int index = map.length; index-- > 0;) {
      if ((map[index] & SKELETON) != 0) {
        getXy(index, xy);
        final int x = xy[0];
        final int y = xy[1];

        if ((map[index] & TERMINUS) != 0) {
          cp.putPixel(x, y, new int[] {0, 0, 255});
        } else if ((map[index] & EDGE) != 0) {
          cp.putPixel(x, y, new int[] {255, 0, 0});
        } else if ((map[index] & JUNCTION) != 0) {
          cp.putPixel(x, y, new int[] {0, 255, 0});
        } else if ((map[index] & PROCESSED) != 0) {
          cp.putPixel(x, y, new int[] {0, 255, 255});
        }
      }
    }

    return cp;
  }

  /**
   * Analyse the neighbours of a pixel (x, y) in a byte image; pixels > 0 ("non-white") are
   * considered foreground. Out-of-boundary pixels are considered background.
   *
   * @param types the byte image
   * @param index coordinate of the point
   * @return Number of lines emanating from this point. Zero if the point is embedded in either
   *         foreground or background
   */
  int countRadii(byte[] types, int index) {
    int countTransitions = 0;
    final byte byteForeground = (byte) this.foreground;
    boolean prevPixelSet = true;
    boolean firstPixelSet = true; // initialise to make the compiler happy
    final int[] xyz = new int[3];
    getXy(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1);
    for (int d = 0; d < 8; d++) {
      // walk around the point and note every no-line->line transition
      boolean pixelSet;
      if (isInner || isWithinXy(x, y, d)) {
        pixelSet = types[index + offset[d]] == byteForeground;
      } else {
        // Outside boundary so there is no point
        pixelSet = false;
      }
      if (pixelSet && !prevPixelSet) {
        countTransitions++;
      }
      prevPixelSet = pixelSet;
      if (d == 0) {
        firstPixelSet = pixelSet;
      }
    }
    if (firstPixelSet && !prevPixelSet) {
      countTransitions++;
    }
    return countTransitions;
  }

  /**
   * Extract the lines between nodes (TERMINUS or JUNCTION) by following EDGE pixels. Also extracts
   * closed loops of continuous edges.
   *
   * <p>This should be called with a map created in the {@link #findNodes(ByteProcessor) } method.
   *
   * @param map The skeleton node map
   * @return List of line data. Each entry is: startX, startY, endX, endY, length
   */
  public List<float[]> extractLines(byte[] map) {
    return extractLines(map, null);
  }

  /**
   * Extract the lines between nodes (TERMINUS or JUNCTION) by following EDGE pixels. Also extracts
   * closed loops of continuous edges.
   *
   * <p>This should be called with a map created in the {@link #findNodes(ByteProcessor) } method.
   *
   * @param map The skeleton node map
   * @param chainCodes If not null this will be filled with chain codes for each line
   * @return List of line data. Each entry is: startX, startY, endX, endY, length
   */
  public List<float[]> extractLines(byte[] map, List<ChainCode> chainCodes) {
    final LinkedList<float[]> lines = new LinkedList<>();
    final LinkedList<ChainCode> myChainCodes = (chainCodes != null) ? new LinkedList<>() : null;
    final int[] xy = new int[2];
    ChainCode code = null;

    // Reset
    for (int index = 0; index < map.length; index++) {
      map[index] &= ~PROCESSED;
    }

    // Process TERMINALs
    for (int index = 0; index < map.length; index++) {
      if ((map[index] & TERMINUS) != 0 && (map[index] & PROCESSED) != PROCESSED) {
        getXy(index, xy);
        final int x = xy[0];
        final int y = xy[1];

        if (myChainCodes != null) {
          code = new ChainCode(x, y);
          myChainCodes.add(code);
        }
        lines.add(extend(map, index, code));

        // Mark as processed
        map[index] |= PROCESSED;
      }
    }

    // Process JUNCTIONS
    for (int index = 0; index < map.length; index++) {
      if ((map[index] & JUNCTION) != 0 && (map[index] & PROCESSED) != PROCESSED) {
        getXy(index, xy);
        final int x = xy[0];
        final int y = xy[1];

        if (myChainCodes != null) {
          code = new ChainCode(x, y);
        }
        final byte[] processedDirections = new byte[1];
        float[] line = extend(map, index, code, processedDirections);

        // Need to extend junctions multiple times
        while (line[4] > 0) {
          // Only add the junction as a start point if a new line was created
          lines.add(line);

          if (myChainCodes != null) {
            myChainCodes.add(code);
            code = new ChainCode(x, y);
          }
          line = extend(map, index, code, processedDirections);
        }

        // Mark as processed
        map[index] |= PROCESSED;
      }
    }

    // Process EDGEs - These should be the closed loops with no junctions/terminals
    for (int index = 0; index < map.length; index++) {
      if ((map[index] & EDGE) == EDGE && (map[index] & PROCESSED) != PROCESSED) {
        getXy(index, xy);
        final int x = xy[0];
        final int y = xy[1];

        if (myChainCodes != null) {
          code = new ChainCode(x, y);
          myChainCodes.add(code);
        }
        lines.add(extend(map, index, code));
      }
    }

    // Sort by length
    final ArrayList<Result> results = new ArrayList<>(lines.size());
    for (final float[] line : lines) {
      results.add(new Result(line, null));
    }
    if (myChainCodes != null) {
      int index = 0;
      for (final ChainCode c : myChainCodes) {
        results.get(index++).code = c;
      }
    }

    Collections.sort(results, Result::compare);

    final ArrayList<float[]> sortedLines = new ArrayList<>(lines.size());
    for (final Result result : results) {
      sortedLines.add(result.line);
    }
    if (chainCodes != null) {
      chainCodes.clear();
      for (final Result result : results) {
        chainCodes.add(result.code);
      }
    }

    return sortedLines;
  }

  /**
   * Searches from the start index, following an edge until a node is reached. Edge/terminus points
   * are marked as processed.
   *
   * @param map the map
   * @param startIndex the start index
   * @param code the code
   * @return The line data: startX, startY, endX, endY, length
   */
  private float[] extend(byte[] map, int startIndex, ChainCode code) {
    final byte[] processedDirections = new byte[1];
    return extend(map, startIndex, code, processedDirections);
  }

  /**
   * Searches from the start index, following an edge until a node is reached. Edge/terminus points
   * are marked as processed.
   *
   * <p>Will not start in any direction that has previously been used.
   *
   * @param map the map
   * @param startIndex the start index
   * @param code the code
   * @param processedDirections Single byte flag containing previously used directions
   * @return The line data: startX, startY, endX, endY, length
   */
  private float[] extend(byte[] map, int startIndex, ChainCode code, byte[] processedDirections) {
    float length = 0;
    int currentIndex = startIndex;

    int nextDirection = findStartDirection(map, currentIndex, processedDirections);

    while (nextDirection >= 0) {
      currentIndex += offset[nextDirection];
      length += ChainCode.getDirectionLength(nextDirection);

      if (code != null) {
        code.add(nextDirection);
      }

      // Mark terminals / edges as processed
      if ((map[currentIndex] & LINE) != 0) {
        map[currentIndex] |= PROCESSED;
      }

      // End if back to the start point or we have reached a node
      if (currentIndex == startIndex || (map[currentIndex] & NODE) != 0) {
        break;
      }

      nextDirection = findNext(map, currentIndex, nextDirection);
    }

    final int[] xyStart = new int[2];
    final int[] xyEnd = new int[2];
    getXy(startIndex, xyStart);
    getXy(currentIndex, xyEnd);

    return new float[] {xyStart[0], xyStart[1], xyEnd[0], xyEnd[1], length};
  }

  private int findStartDirection(byte[] map, int index, byte[] processedDirections) {
    final int[] xyz = new int[3];
    getXy(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1);

    // Sweep one way until a background pixel is found
    int dir = 8;
    while (dir > 0) {
      dir--;
      if ((isInner || isWithinXy(x, y, dir)) && (map[index + offset[dir]] == 0)) {
        break;
      }
    }

    // Sweep the other way until a skeleton pixel is found that has not been used.
    // This sweep direction must match that used in findNext(...)
    for (int i = 1; i <= 8; i++) {
      final int d = (dir + i) % 8;
      if ((isInner || isWithinXy(x, y, d)) && ((map[index + offset[d]] & SKELETON) != 0
          && (map[index + offset[d]] & PROCESSED) != PROCESSED
          && (processedDirections[0] & PROCESSED_DIRECTIONS[d]) == 0)) {
        return addDirection(d, processedDirections);
      }
    }

    return -1;
  }

  /**
   * Add the direction to the set that have been processed.
   *
   * @param direction the direction
   * @param processedDirections the processed directions
   * @return The direction
   */
  private static int addDirection(int direction, byte[] processedDirections) {
    processedDirections[0] |= PROCESSED_DIRECTIONS[direction];
    return direction;
  }

  private int findNext(byte[] map, int index, int nextDirection) {
    final int[] xyz = new int[3];
    getXy(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1);

    // Set the search direction for the next point to search all points except the direction
    // that was taken.
    // Need to ignore moving to a pixel that is connected to the previous pixel. Thus use +6 offset
    // instead of +7.
    // This ignores the first pixel in a clockwise sweep starting from the previous pixel. Note that
    // since we sweep
    // clockwise that pixel would have been identified already except if this the first pixel after
    // a start point:
    // 3 2
    // 4 1
    // 5 0 +
    // +
    // This avoids moving from 1 back to 5 and allows the algorithm to process 2 3 4.
    int searchDirection;

    // Do a sweep for NODEs first
    searchDirection = (nextDirection + 6) % 8;
    for (int i = 0; i < 6; i++) {
      final int d = (searchDirection + i) % 8;
      if ((isInner || isWithinXy(x, y, d)) && (map[index + offset[d]] & NODE) != 0) {
        return d;
      }
    }

    // Now do a sweep for EDGEs
    searchDirection = (nextDirection + 6) % 8;
    for (int i = 0; i < 6; i++) {
      final int d = (searchDirection + i) % 8;
      if ((isInner || isWithinXy(x, y, d)) && (map[index + offset[d]] & EDGE) == EDGE
          && (map[index + offset[d]] & PROCESSED) != PROCESSED) {
        return d;
      }
    }

    return -1;
  }

  private void showResults(List<float[]> lines) {
    if (lines.size() > 1000) {
      final YesNoCancelDialog d = new YesNoCancelDialog(IJ.getInstance(), TITLE,
          "Do you want to show all " + lines.size() + " results?");
      d.setVisible(true);
      if (!d.yesPressed()) {
        return;
      }
    }

    createResultsWindow();

    int id = 0;
    for (final float[] line : lines) {
      if (line[4] < settings.minLength) {
        break;
      }
      addResult(++id, line);
    }
  }

  private void showOverlay(ImagePlus mapImp, List<float[]> lines) {
    final int[] x = new int[lines.size()];
    final int[] y = new int[x.length];

    int id = 0;
    for (final float[] line : lines) {
      if (line[4] < settings.minLength) {
        break;
      }
      x[id] = (int) line[0];
      y[id++] = (int) line[1];
    }

    Overlay overlay = null;
    if (id != 0) {
      final PointRoi roi = new PointRoi(x, y, id);
      roi.setShowLabels(true);
      overlay = new Overlay(roi);
    }
    mapImp.setOverlay(overlay);
  }

  private void createResultsWindow() {
    if (this.imp.getCalibration() != null) {
      unit = imp.getCalibration().getUnit();
      unitConversion = imp.getCalibration().pixelWidth;
    }
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      resultOutput = IJ::log;
      if (writeHeader.get()) {
        writeHeader.set(false);
        IJ.log(createResultsHeader());
      }
    } else {
      // Atomically create a results window if needed.
      TextWindow tw = resultsWindow.get();
      if (tw == null || !tw.isShowing()) {
        tw = new TextWindow(TITLE + " Results", createResultsHeader(), "", 400, 500);
        // When it closes remove the reference to this window
        final TextWindow closedWindow = tw;
        tw.addWindowListener(new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            resultsWindow.compareAndSet(closedWindow, null);
            super.windowClosed(event);
          }
        });
        resultsWindow.set(tw);
      }
      resultOutput = tw::append;
    }
  }

  private static String createResultsHeader() {
    final StringBuilder sb = new StringBuilder();
    sb.append("ID\t");
    sb.append("StartX\t");
    sb.append("StartY\t");
    sb.append("EndX\t");
    sb.append("EndY\t");
    sb.append("Length (px)\t");
    sb.append("Length\t");
    sb.append("Unit");
    return sb.toString();
  }

  private void addResult(int id, float[] line) {
    final StringBuilder sb = new StringBuilder();
    sb.append(id).append('\t');
    for (int i = 0; i < 4; i++) {
      sb.append((int) line[i]).append('\t');
    }
    sb.append(IJ.d2s(line[4], 2)).append('\t');
    sb.append(IJ.d2s(line[4] * unitConversion, 2)).append('\t');
    sb.append(unit);

    resultOutput.accept(sb.toString());
  }

  // ------------------------------------
  // Adapted from ij.plugin.filter.Binary
  /**
   * Skeletonize.
   *
   * @param ip the image
   * @param trim the trim
   */
  // ------------------------------------
  void skeletonize(ImageProcessor ip, boolean trim) {
    if (Prefs.blackBackground) {
      ip.invert();
    }
    final boolean edgePixels = hasEdgePixels(ip);
    final ImageProcessor ip2 = expand(ip, edgePixels);
    ((ByteProcessor) ip2).skeletonize();
    ip = shrink(ip, ip2, edgePixels);
    if (Prefs.blackBackground) {
      ip.invert();
    }
    // Remove redundant pixels
    if (trim) {
      cleanupExtraCornerPixels(ip);
    }
  }

  /**
   * Checks for edge pixels.
   *
   * @param ip the image
   * @return true, if successful
   */
  boolean hasEdgePixels(ImageProcessor ip) {
    final int width = ip.getWidth();
    final int height = ip.getHeight();
    boolean edgePixels = false;
    for (int x = 0; x < width; x++) {
      if (ip.getPixel(x, 0) == foreground) {
        edgePixels = true;
      }
    }
    for (int x = 0; x < width; x++) {
      if (ip.getPixel(x, height - 1) == foreground) {
        edgePixels = true;
      }
    }
    for (int y = 0; y < height; y++) {
      if (ip.getPixel(0, y) == foreground) {
        edgePixels = true;
      }
    }
    for (int y = 0; y < height; y++) {
      if (ip.getPixel(width - 1, y) == foreground) {
        edgePixels = true;
      }
    }
    return edgePixels;
  }

  /**
   * Expand.
   *
   * @param ip the image
   * @param hasEdgePixels the has edge pixels
   * @return the image processor
   */
  ImageProcessor expand(ImageProcessor ip, boolean hasEdgePixels) {
    if (hasEdgePixels) {
      final ImageProcessor ip2 = ip.createProcessor(ip.getWidth() + 2, ip.getHeight() + 2);
      if (foreground == 0) {
        ip2.setColor(255);
        ip2.fill();
      }
      ip2.insert(ip, 1, 1);
      return ip2;
    }
    return ip;
  }

  /**
   * Shrink.
   *
   * @param ip the image
   * @param ip2 the image 2
   * @param hasEdgePixels the has edge pixels
   * @return the image processor
   */
  ImageProcessor shrink(ImageProcessor ip, ImageProcessor ip2, boolean hasEdgePixels) {
    if (hasEdgePixels) {
      final int width = ip.getWidth();
      final int height = ip.getHeight();
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          ip.putPixel(x, y, ip2.getPixel(x + 1, y + 1));
        }
      }
    }
    return ip;
  }

  /**
   * For each skeleton pixel, check the 2 adjacent non-diagonal neighbour pixels in clockwise
   * fashion. If they are both skeleton pixels then this pixel can be removed (since they form a
   * diagonal line) if not connected at the opposite corner.
   */
  private int cleanupExtraCornerPixels(ImageProcessor ip) {
    int removed = 0;
    final int[] xyz = new int[3];

    final byte background = (byte) (255 - foreground);

    if (maxx == 0) {
      initialise(ip.getWidth(), ip.getHeight());
    }

    final byte[] skeleton = (byte[]) ip.getPixels();

    for (int index = skeleton.length; index-- > 0;) {
      if (skeleton[index] == foreground) {
        getXy(index, xyz);
        final int x = xyz[0];
        final int y = xyz[1];

        final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        // Check which neighbours are set
        final boolean[] edgesSet = new boolean[8];
        for (int d = 8; d-- > 0;) {
          if (isInner || isWithinXy(x, y, d)) {
            edgesSet[d] = (skeleton[index + offset[d]] == foreground);
          }
        }

        // analyze 4 flat-edge neighbours
        for (int d = 0; d < 8; d += 2) {
          if ((edgesSet[d] && edgesSet[(d + 2) % 8])
              && !(edgesSet[(d + 5) % 8] || (edgesSet[(d + 4) % 8] && edgesSet[(d + 6) % 8]))) {
            removed++;
            skeleton[index] = background;
            break;
          }
        }
      }
    }

    return removed;
  }

  /**
   * Initialises the global width and height variables. Creates the direction offset tables.
   *
   * @param width the width
   * @param height the height
   */
  public void initialise(int width, int height) {
    maxx = width;
    maxy = height;

    xlimit = maxx - 1;
    ylimit = maxy - 1;

    // Create the offset table (for single array 3D neighbour comparisons)
    offset = new int[ChainCode.DIRECTION_SIZE];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = getIndex(ChainCode.getXDirection(d), ChainCode.getYDirection(d));
    }
  }

  /**
   * Return the single index associated with the x,y coordinates.
   *
   * @param x the x
   * @param y the y
   * @return The index
   */
  private int getIndex(int x, int y) {
    return maxx * y + x;
  }

  /**
   * Convert the single index into x,y coords, Input array must be length >= 2.
   *
   * @param index the index
   * @param xy the xy
   * @return The xy array
   */
  private int[] getXy(int index, int[] xy) {
    xy[1] = index / maxx;
    xy[0] = index % maxx;
    return xy;
  }

  /**
   * returns whether the neighbour in a given direction is within the image. NOTE: it is assumed
   * that the pixel x,y itself is within the image! Uses class variables xlimit, ylimit: (dimensions
   * of the image)-1
   *
   * @param x x-coordinate of the pixel that has a neighbour in the given direction
   * @param y y-coordinate of the pixel that has a neighbour in the given direction
   * @param direction the direction from the pixel towards the neighbour
   * @return true if the neighbour is within the image (provided that x, y is within)
   */
  private boolean isWithinXy(int x, int y, int direction) {
    switch (direction) {
      case 0:
        return (y > 0);
      case 1:
        return (y > 0 && x < xlimit);
      case 2:
        return (x < xlimit);
      case 3:
        return (y < ylimit && x < xlimit);
      case 4:
        return (y < ylimit);
      case 5:
        return (y < ylimit && x > 0);
      case 6:
        return (x > 0);
      case 7:
        return (y > 0 && x > 0);
      default:
        return false;
    }
  }
}
