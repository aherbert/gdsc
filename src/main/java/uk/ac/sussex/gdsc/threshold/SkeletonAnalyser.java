/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.ImageJUtils;

/**
 * Skeletonise a mask image. Then produce a set of lines connecting node points on the skeleton. <p>
 * The skeleton is modified from the ImageJ default by removing pixels that are 4-connected to
 * adjacent 8-connected pixels (e.g. North &amp; East, East &amp; South, South &amp; West, West
 * &amp; North) unless 8-connected on the opposite side or 4-connected on the other two sides. This
 * eliminates redundant pixels.
 */
public class SkeletonAnalyser implements PlugInFilter {
  private static String TITLE = "Skeleton Analyser";
  private static TextWindow resultsWindow = null;
  private static boolean writeHeader = true;

  private static boolean s_pruneJunctions = false;
  private static int s_minLength = 0;
  private static boolean s_showNodeMap = true;
  private static boolean s_showOverlay = false;
  private static boolean s_showTable = true;

  /** The prune junctions. */
  // Public to allow control of the algorithm in the run(ImageProcessor) method
  boolean pruneJunctions = false;

  /** The min length. */
  int minLength = 0;

  /** The show node map. */
  boolean showNodeMap = true;

  /** The show overlay. */
  boolean showOverlay = false;

  /** The show table. */
  boolean showTable = true;

  private int foreground;
  private ImagePlus imp;

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
  public static final byte[] PROCESSED_DIRECTIONS = new byte[] {(byte) 1, (byte) 2, (byte) 4,
      (byte) 8, (byte) 16, (byte) 32, (byte) 64, (byte) 128};

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
    final GenericDialog gd = new GenericDialog(TITLE);

    gd.addCheckbox("Prune_junctions", s_pruneJunctions);
    gd.addNumericField("Min_length", s_minLength, 0);
    gd.addCheckbox("Show_node_map", s_showNodeMap);
    gd.addCheckbox("Show_overlay", s_showOverlay);
    gd.addCheckbox("Show_table", s_showTable);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return false;
    }

    pruneJunctions = s_pruneJunctions = gd.getNextBoolean();
    minLength = s_minLength = (int) gd.getNextNumber();
    showNodeMap = s_showNodeMap = gd.getNextBoolean();
    showOverlay = s_showOverlay = gd.getNextBoolean();
    showTable = s_showTable = gd.getNextBoolean();

    return true;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor ip) {
    final ByteProcessor bp = (ByteProcessor) ip.convertToByte(false);

    final int width = bp.getWidth();
    final int height = bp.getHeight();

    skeletonise(bp, true);
    // skeltonise(bp, false);

    final byte[] map = findNodes(bp);
    if (pruneJunctions) {
      boolean pruned = prune(map, width, height);
      while (pruned) {
        pruned = prune(map, width, height);
      }
    }

    final List<float[]> lines = extractLines(map);

    if (showNodeMap) {
      final ColorProcessor cp = createMapImage(map, width, height);
      final ImagePlus mapImp = ImageJUtils.display(this.imp.getTitle() + " SkeletonNodeMap", cp);
      if (showOverlay) {
        showOverlay(mapImp, lines);
      }
    }

    if (showTable) {
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

    int myForeground = Prefs.blackBackground ? 255 : 0;
    if (ip.isInvertedLut()) {
      myForeground = 255 - myForeground;
    }
    this.foreground = myForeground;

    ip.resetRoi();
    skeletonize(ip, trim);
    ip.setBinaryThreshold();

    return true;
  }

  /**
   * Search the skeleton and create a node map of the skeleton points. Points can be either:
   * TERMINUS, EDGE or JUNCTION. Points not on the skeleton are set to zero.
   *
   * @param ip Skeletonized image
   * @return The skeleton node map (or null if not a binary processor)
   */
  public byte[] findNodes(ByteProcessor ip) {
    if (!ip.isBinary()) {
      return null;
    }

    byte foreground = (byte) (Prefs.blackBackground ? 255 : 0);
    if (ip.isInvertedLut()) {
      foreground = (byte) (255 - foreground);
    }

    if (maxx == 0) {
      initialise(ip.getWidth(), ip.getHeight());
    }

    final byte[] skeleton = (byte[]) ip.getPixels();
    final byte[] map = new byte[ip.getPixelCount()];

    for (int index = map.length; index-- > 0;) {
      if (skeleton[index] == foreground) {
        // Process the neighbours
        final int count = nRadii(skeleton, index);

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
    final byte foreground = (byte) this.foreground;
    final byte[] map = new byte[skeleton.length];

    for (int index = map.length; index-- > 0;) {
      if (skeleton[index] == foreground) {
        // Process the neighbours
        final int count = nRadii(skeleton, index);

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

  private class Line implements Comparable<Line> {
    int start;
    @SuppressWarnings("unused")
    int end;
    float length;
    boolean internal;
    ChainCode code;

    Line(int start, int end, float length, boolean internal, ChainCode code) {
      this.start = start;
      this.end = end;
      this.length = length;
      this.internal = internal;
      this.code = code;
    }

    @Override
    public int compareTo(Line that) {
      return this.start - that.start;
    }
  }

  private class LineComparator implements Comparator<Line> {
    @Override
    public int compare(Line o1, Line o2) {
      if (o1.internal ^ o2.internal) {
        // If one is internal and the other is not
        return (o1.internal) ? -1 : 1;
      }
      // Rank by length
      if (o1.length > o2.length) {
        return -1;
      }
      if (o1.length < o2.length) {
        return 1;
      }
      return 0;
    }
  }

  /**
   * Prune the shortest line from junctions that end in a terminus, until all junctions are
   * eliminated.
   *
   * @param map the map
   * @param width the width
   * @param height the height
   * @return true, if successful
   */
  private boolean prune(byte[] map, int width, int height) {
    // Each entry is: startX, startY, endX, endY, length
    final List<ChainCode> chainCodes = new LinkedList<>();
    final List<float[]> lines = extractLines(map, chainCodes);

    // Convert line to start and end index
    final int[] startIndex = new int[lines.size()];
    final int[] endIndex = new int[startIndex.length];
    final float[] lengths = new float[startIndex.length];
    int i = 0;
    for (final float[] line : lines) {
      startIndex[i] = getIndex((int) line[0], (int) line[1]);
      endIndex[i] = getIndex((int) line[2], (int) line[3]);
      lengths[i] = line[4];
      i++;
    }

    final Iterator<ChainCode> it = chainCodes.iterator();

    final ArrayList<Line> junctionLines = new ArrayList<>(lines.size());
    final LineComparator lineComparator = new LineComparator();

    // Find all lines that start at a terminus and end at a junction
    // Find all the lines that start and end at a junction
    for (i = 0; i < startIndex.length; i++) {
      final ChainCode code = it.next();

      // Ends at a junction
      if ((map[endIndex[i]] & JUNCTION) != 0) {
        // Check if it starts at a junction (i.e. is internal)
        final boolean internal = (map[startIndex[i]] & JUNCTION) != 0;

        // Store the line under the index of the junction, reverse the chain code
        junctionLines
            .add(new Line(endIndex[i], startIndex[i], lengths[i], internal, code.reverse()));

        if (internal) {
          // Starts at a junction so store under this junction as well
          junctionLines.add(new Line(startIndex[i], endIndex[i], lengths[i], true, code));
        }
      }
    }

    // Sort by the junction start
    Collections.sort(junctionLines);

    // Each junction should have 3/4 lines if clean up or corner pixels was OK, worst case is 8.
    int n = 0;
    final Line[] currentLines = new Line[8];
    int current = 0;

    final ArrayList<Line> toDelete = new ArrayList<>(100);

    // Process the set of lines for each junction
    for (final Line line : junctionLines) {
      if (current != line.start) {
        if (n != 0) {
          markForDeletion(lineComparator, n, currentLines, toDelete);
        }
        n = 0;
        current = line.start;
      }
      currentLines[n++] = line;
    }

    if (n != 0) {
      markForDeletion(lineComparator, n, currentLines, toDelete);
    }

    if (toDelete.isEmpty()) {
      for (i = 0; i < map.length; i++) {
        map[i] |= PROCESSED;
      }
      return false;
    }

    // If any marked for deletion, remove the shortest line
    Collections.sort(toDelete, new Comparator<Line>() {
      @Override
      public int compare(Line o1, Line o2) {
        // Rank by length
        if (o1.length < o2.length) {
          return -1;
        }
        if (o1.length > o2.length) {
          return 1;
        }
        return 0;
      }
    });

    // ColorProcessor cp = createMapImage(map, width, height);
    // Utils.display(this.imp.getTitle() + " SkeletonNodeMap", cp);

    // Remove the line for deletion
    final ChainCode code = toDelete.get(0).code;
    int x = code.getX();
    int y = code.getY();
    // System.out.printf("Pruning %d,%d: %f\n", x, y, code.getDistance());
    final int[] run = code.getRun();
    int index = getIndex(x, y);
    // System.out.printf("Pruning %d,%d, T=%b, E=%b, J=%b\n", x, y, (map[index] & TERMINUS) != 0,
    // (map[index] & EDGE) != 0, (map[index] & JUNCTION) != 0);

    if ((map[index] & LINE) != 0) {
      // System.out.printf("Pruning %d,%d\n", x, y);
      map[index] = 0;
    }
    for (final int d : run) {
      x += ChainCode.DIR_X_OFFSET[d];
      y += ChainCode.DIR_Y_OFFSET[d];
      index = getIndex(x, y);
      // System.out.printf("Pruning %d,%d, T=%b, E=%b, J=%b\n", x, y, (map[index] & TERMINUS) != 0,
      // (map[index] & EDGE) != 0, (map[index] & JUNCTION) != 0);
      if ((map[index] & LINE) != 0) {
        // System.out.printf("Pruning %d,%d\n", x, y);
        map[index] = 0;
      }
    }

    // Create a new map
    final byte foreground = (byte) this.foreground;
    final byte background = (byte) (this.foreground + 1);
    for (i = 0; i < map.length; i++) {
      map[i] = (map[i] != 0) ? foreground : background;
    }
    final byte[] map2 = findNodes(map);
    System.arraycopy(map2, 0, map, 0, map.length);
    return true;
  }

  private void markForDeletion(final LineComparator lineComparator, int n, Line[] currentLines,
      ArrayList<Line> toDelete) {
    if (n < 3) {
      System.err.printf("Junction %d,%d has less than 3 lines", currentLines[0].start % maxx,
          currentLines[0].start / maxx);
    }

    // Sort
    Arrays.sort(currentLines, 0, n, lineComparator);

    // Keep all edges that go to another junction
    int keep = 0;
    while (keep < n && currentLines[keep].internal) {
      keep++;
    }

    // Keep remaining edges in length order (descending) until there are only 2 edges.
    while (keep < n && keep < 2) {
      keep++;
    }

    // Mark others for deletion
    while (keep < n) {
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
        getXY(index, xy);
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
  int nRadii(byte[] types, int index) {
    int countTransitions = 0;
    final byte foreground = (byte) this.foreground;
    boolean prevPixelSet = true;
    boolean firstPixelSet = true; // initialise to make the compiler happy
    final int[] xyz = new int[3];
    getXY(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1); // not
                                                                                    // necessary,
                                                                                    // but faster
                                                                                    // than
    // isWithin
    for (int d = 0; d < 8; d++) { // walk around the point and note every no-line->line transition
      boolean pixelSet = prevPixelSet;
      if (isInner || isWithinXY(x, y, d)) {
        pixelSet = types[index + offset[d]] == foreground;
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
   * closed loops of continuous edges. <p> This should be called with a map created in the
   * {@link #findNodes(ByteProcessor) } method.
   *
   * @param map The skeleton node map
   * @return List of line data. Each entry is: startX, startY, endX, endY, length
   */
  public List<float[]> extractLines(byte[] map) {
    return extractLines(map, null);
  }

  /**
   * Extract the lines between nodes (TERMINUS or JUNCTION) by following EDGE pixels. Also extracts
   * closed loops of continuous edges. <p> This should be called with a map created in the
   * {@link #findNodes(ByteProcessor) } method.
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
        getXY(index, xy);
        final int x = xy[0];
        final int y = xy[1];
        // System.out.printf("Process %d,%d\n", x, y);

        if (myChainCodes != null) {
          code = new ChainCode(x, y);
          myChainCodes.add(code);
        }
        lines.add(extend(map, index, code));

        // Mark as processed
        map[index] |= PROCESSED;
      }
    }

    // showMap(map, maxx, maxy, "LineMapTerminals");

    // Process JUNCTIONS
    for (int index = 0; index < map.length; index++) {
      if ((map[index] & JUNCTION) != 0 && (map[index] & PROCESSED) != PROCESSED) {
        getXY(index, xy);
        final int x = xy[0];
        final int y = xy[1];
        // System.out.printf("Process %d,%d\n", x, y);

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

    // showMap(map, maxx, maxy, "LineMapJunctions");

    // Process EDGEs - These should be the closed loops with no junctions/terminals
    for (int index = 0; index < map.length; index++) {
      if ((map[index] & EDGE) == EDGE && (map[index] & PROCESSED) != PROCESSED) {
        getXY(index, xy);
        final int x = xy[0];
        final int y = xy[1];
        // System.out.printf("Process %d,%d\n", x, y);

        if (myChainCodes != null) {
          code = new ChainCode(x, y);
          myChainCodes.add(code);
        }
        lines.add(extend(map, index, code));
      }
    }

    // showMap(map, maxx, maxy, "LineMapEdges");

    // Sort by length
    final ArrayList<Result> results = new ArrayList<>(lines.size());
    for (final float[] line : lines) {
      results.add(new Result(line, null));
    }
    if (myChainCodes != null) {
      int i = 0;
      for (final ChainCode c : myChainCodes) {
        results.get(i++).code = c;
      }
    }

    Collections.sort(results);

    final ArrayList<float[]> sortedLines = new ArrayList<>(lines.size());
    for (final Result result : results) {
      sortedLines.add(result.line);
    }
    if (myChainCodes != null) {
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
   * are marked as processed. <p> Will not start in any direction that has previously been used.
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
      length += ChainCode.DIR_LENGTH[nextDirection];

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
    getXY(startIndex, xyStart);
    getXY(currentIndex, xyEnd);

    return new float[] {xyStart[0], xyStart[1], xyEnd[0], xyEnd[1], length};
  }

  private int findStartDirection(byte[] map, int index, byte[] processedDirections) {
    final int[] xyz = new int[3];
    getXY(index, xyz);
    final int x = xyz[0];
    final int y = xyz[1];

    final boolean isInner = (y != 0 && y != maxy - 1) && (x != 0 && x != maxx - 1);

    // Sweep one way until a background pixel is found
    int dir = 8;
    while (dir > 0) {
      dir--;
      if (isInner || isWithinXY(x, y, dir)) {
        if (map[index + offset[dir]] == 0) {
          break;
        }
      }
    }

    // Sweep the other way until a skeleton pixel is found that has not been used.
    // This sweep direction must match that used in findNext(...)
    for (int i = 1; i <= 8; i++) {
      final int d = (dir + i) % 8;
      if (isInner || isWithinXY(x, y, d)) {
        if ((map[index + offset[d]] & SKELETON) != 0
            && (map[index + offset[d]] & PROCESSED) != PROCESSED
            && (processedDirections[0] & PROCESSED_DIRECTIONS[d]) == 0) {
          return addDirection(d, processedDirections);
        }
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
    getXY(index, xyz);
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
      // int d = (searchDirection + SEARCH[i]) % 8;
      final int d = (searchDirection + i) % 8;
      if (isInner || isWithinXY(x, y, d)) {
        if ((map[index + offset[d]] & NODE) != 0) {
          return d;
        }
      }
    }

    // Now do a sweep for EDGEs
    searchDirection = (nextDirection + 6) % 8;
    for (int i = 0; i < 6; i++) {
      // int d = (searchDirection + SEARCH[i]) % 8;
      final int d = (searchDirection + i) % 8;
      if (isInner || isWithinXY(x, y, d)) {
        if ((map[index + offset[d]] & EDGE) == EDGE
            && (map[index + offset[d]] & PROCESSED) != PROCESSED) {
          return d;
        }
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
      if (line[4] < minLength) {
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
      if (line[4] < minLength) {
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

  private String unit = "px";
  private double unitConversion = 1;

  private void createResultsWindow() {
    if (this.imp.getCalibration() != null) {
      unit = imp.getCalibration().getUnit();
      unitConversion = imp.getCalibration().pixelWidth;
    }
    if (java.awt.GraphicsEnvironment.isHeadless()) {
      if (writeHeader) {
        writeHeader = false;
        IJ.log(createResultsHeader());
      }
    } else if (resultsWindow == null || !resultsWindow.isShowing()) {
      resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 400, 500);
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

    if (java.awt.GraphicsEnvironment.isHeadless()) {
      IJ.log(sb.toString());
    } else {
      resultsWindow.append(sb.toString());
    }
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
      // new ImagePlus("ip2", ip2).show();
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

    byte foreground = (byte) (Prefs.blackBackground ? 255 : 0);
    if (ip.isInvertedLut()) {
      foreground = (byte) (255 - foreground);
    }
    final byte background = (byte) ((byte) 255 - foreground);

    if (maxx == 0) {
      initialise(ip.getWidth(), ip.getHeight());
    }

    final byte[] skeleton = (byte[]) ip.getPixels();

    for (int index = skeleton.length; index-- > 0;) {
      if (skeleton[index] == foreground) {
        getXY(index, xyz);
        final int x = xyz[0];
        final int y = xyz[1];

        final boolean isInner = (y != 0 && y != ylimit) && (x != 0 && x != xlimit);

        final boolean[] edgesSet = new boolean[8];
        for (int d = 8; d-- > 0;) {
          // analyze 4 flat-edge neighbours
          if (isInner || isWithinXY(x, y, d)) {
            edgesSet[d] = (skeleton[index + offset[d]] == foreground);
          }
        }

        for (int d = 0; d < 8; d += 2) {
          if ((edgesSet[d] && edgesSet[(d + 2) % 8])
              && !(edgesSet[(d + 5) % 8] || (edgesSet[(d + 4) % 8] && edgesSet[(d + 6) % 8]))) {
            removed++;
            skeleton[index] = background;
          }
        }
      }
    }

    return removed;
  }

  private int maxx = 0, maxy = 0; // image dimensions
  private int xlimit, ylimit;
  private int[] offset;

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
    offset = new int[ChainCode.DIR_X_OFFSET.length];
    for (int d = offset.length; d-- > 0;) {
      offset[d] = getIndex(ChainCode.DIR_X_OFFSET[d], ChainCode.DIR_Y_OFFSET[d]);
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
  private int[] getXY(int index, int[] xy) {
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
  private boolean isWithinXY(int x, int y, int direction) {
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
      case 8:
        return true;
    }
    return false;
  }

  private class Result implements Comparable<Result> {
    float[] line;
    ChainCode code;

    Result(float[] line, ChainCode code) {
      this.line = line;
      this.code = code;
    }

    @Override
    public int compareTo(Result that) {
      final int[] result = new int[1];

      // Distance first
      if (compare(this.line[4], that.line[4], result) != 0) {
        return -result[0];
      }

      // Then coordinates
      for (int i = 0; i < 4; i++) {
        if (compare(this.line[i], that.line[i], result) != 0) {
          return result[0];
        }
      }

      return 0;
    }

    private int compare(float value1, float value2, int[] result) {
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
}
