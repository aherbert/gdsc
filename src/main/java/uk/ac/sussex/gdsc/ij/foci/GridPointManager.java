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

import java.util.LinkedList;
import java.util.List;

/**
 * Stores a set of GridPoints within a grid arrangement at a given resolution. Allows comparison of
 * a coordinate with any point within the sampling resolution to locate the highest unassigned grid
 * point.
 *
 * <p>Currently only supports a 2D grid.
 */
public class GridPointManager {
  private final List<GridPoint> allPoints;
  @SuppressWarnings("rawtypes")
  private List[][] grid;
  private final int resolution;
  private int minX = Integer.MAX_VALUE;
  private int minY = Integer.MAX_VALUE;
  private int searchMode;

  /**
   * Define the search modes for the {@link #findUnassignedPoint(int, int)} method.
   */
  private static final String[] searchModes = new String[] {"Highest", "Closest"};

  /** Constant for finding the highest point. */
  public static final int HIGHEST = 0;

  /** Constant for finding the closest point. */
  public static final int CLOSEST = 1;

  /**
   * Gets the search modes.
   *
   * @return the search modes
   */
  public static String[] getSearchModes() {
    return searchModes.clone();
  }

  /**
   * Instantiates a new grid point manager.
   *
   * @param points the points
   * @param resolution the resolution
   * @throws GridException the grid exception
   */
  public GridPointManager(List<GridPoint> points, int resolution) throws GridException {
    this.resolution = resolution;
    this.allPoints = points;
    initialiseGrid();
  }

  private void initialiseGrid() throws GridException {
    // Find the minimum and maximum x,y
    int maxX = 0;
    int maxY = maxX;
    for (final GridPoint p : allPoints) {
      if (p.getX() < minX) {
        minX = p.getXint();
      }
      if (p.getX() > maxX) {
        maxX = p.getXint();
      }
      if (p.getY() < minY) {
        minY = p.getYint();
      }
      if (p.getY() > maxY) {
        maxY = p.getYint();
      }
    }

    if (minX < 0 || minY < 0) {
      throw new GridException(
          "Minimum grid coordinates must not be negative (x" + minX + ",y" + minY + ")");
    }

    final int xBlocks = getXBlock(maxX) + 1;
    final int yBlocks = getYBlock(maxY) + 1;

    if (xBlocks > 500 || yBlocks > 500) {
      throw new GridException(
          "Maximum number of grid blocks exceeded, please increase the resolution parameter");
    }
    if (xBlocks <= 0 || yBlocks <= 0) {
      throw new GridException("No coordinates to add to the grid");
    }

    grid = new List[xBlocks][yBlocks];

    // Assign points
    for (final GridPoint p : allPoints) {
      addToGrid(p);
    }
  }

  private int getXBlock(int x) {
    return (x - minX) / resolution;
  }

  private int getYBlock(int y) {
    return (y - minY) / resolution;
  }

  private void addToGrid(GridPoint point) {
    final int xBlock = getXBlock(point.getXint());
    final int yBlock = getYBlock(point.getYint());
    @SuppressWarnings("unchecked")
    List<GridPoint> points = grid[xBlock][yBlock];
    if (points == null) {
      points = new LinkedList<>();
      grid[xBlock][yBlock] = points;
    }

    point.setAssigned(false);
    points.add(point);
  }

  /**
   * Resets the assigned flag on all the points.
   */
  public void resetAssigned() {
    for (final GridPoint p : allPoints) {
      p.setAssigned(false);
    }
  }

  /**
   * Find the unassigned point using the current search mode If a point is found it will have its
   * assigned flag set to true.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findUnassignedPoint(int xCoord, int yCoord) {
    if (searchMode == CLOSEST) {
      return findClosestUnassignedPoint(xCoord, yCoord);
    }
    return findHighestUnassignedPoint(xCoord, yCoord);
  }

  /**
   * Find the highest assigned point within the sampling resolution from the given coordinates.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findHighestAssignedPoint(int xCoord, int yCoord) {
    return findHighest(xCoord, yCoord, true);
  }

  /**
   * Find the highest unassigned point within the sampling resolution from the given coordinates. If
   * a point is found it will have its assigned flag set to true.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findHighestUnassignedPoint(int xCoord, int yCoord) {
    final GridPoint point = findHighest(xCoord, yCoord, false);

    if (point != null) {
      point.setAssigned(true);
    }

    return point;
  }

  /**
   * Find the highest point within the sampling resolution from the given coordinates with the
   * specified assigned status.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @param assigned the assigned
   * @return The GridPoint (or null)
   */
  public GridPoint findHighest(int xCoord, int yCoord, boolean assigned) {
    GridPoint point = null;

    final int xBlock = getXBlock(xCoord);
    final int yBlock = getYBlock(yCoord);

    double resolution2 = (double) resolution * resolution;
    if (!assigned) {
      // Use closest assigned peak to set the resolution for the unassigned search
      final GridPoint closestPoint = findClosestAssignedPoint(xCoord, yCoord);
      if (closestPoint != null) {
        resolution2 = closestPoint.distanceSquared(xCoord, yCoord);
      }
    }

    // Check all surrounding blocks for highest unassigned point
    float maxValue = Float.NEGATIVE_INFINITY;
    for (int x = Math.max(0, xBlock - 1); x <= Math.min(grid.length - 1, xBlock + 1); x++) {
      for (int y = Math.max(0, yBlock - 1); y <= Math.min(grid[0].length - 1, yBlock + 1); y++) {
        if (grid[x][y] != null) {
          @SuppressWarnings("unchecked")
          final List<GridPoint> points = grid[x][y];

          for (final GridPoint p : points) {
            if ((p.isAssigned() == assigned) && (p.distanceSquared(xCoord, yCoord) < resolution2)
                && (maxValue < p.getValue())) {
              maxValue = p.getValue();
              point = p;
            }
          }
        }
      }
    }

    return point;
  }

  /**
   * Find the assigned point that matches the given coordinates.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findExactAssignedPoint(int xCoord, int yCoord) {
    return findExact(xCoord, yCoord, true);
  }

  /**
   * Find the unassigned point that matches the given coordinates. If a point is found it will have
   * its assigned flag set to true.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findExactUnassignedPoint(int xCoord, int yCoord) {
    final GridPoint point = findExact(xCoord, yCoord, false);

    if (point != null) {
      point.setAssigned(true);
    }

    return point;
  }

  /**
   * Find the point that matches the given coordinates with the specified assigned status.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @param assigned the assigned
   * @return The GridPoint (or null)
   */
  public GridPoint findExact(int xCoord, int yCoord, boolean assigned) {
    final int xBlock = getXBlock(xCoord);
    final int yBlock = getYBlock(yCoord);

    final int x = Math.min(grid.length - 1, Math.max(0, xBlock));
    final int y = Math.min(grid[0].length - 1, Math.max(0, yBlock));

    if (grid[x][y] != null) {
      @SuppressWarnings("unchecked")
      final List<GridPoint> points = grid[x][y];

      for (final GridPoint p : points) {
        if (p.isAssigned() == assigned && p.getX() == xCoord && p.getY() == yCoord) {
          return p;
        }
      }
    }

    return null;
  }

  /**
   * Find the closest assigned point within the sampling resolution from the given coordinates.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findClosestAssignedPoint(int xCoord, int yCoord) {
    return findClosest(xCoord, yCoord, true);
  }

  /**
   * Find the closest unassigned point within the sampling resolution from the given coordinates. If
   * a point is found it will have its assigned flag set to true.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @return The GridPoint (or null)
   */
  public GridPoint findClosestUnassignedPoint(int xCoord, int yCoord) {
    final GridPoint point = findClosest(xCoord, yCoord, false);

    if (point != null) {
      point.setAssigned(true);
    }

    return point;
  }

  /**
   * Find the closest point within the sampling resolution from the given coordinates with the
   * specified assigned status.
   *
   * @param xCoord the x coord
   * @param yCoord the y coord
   * @param assigned the assigned
   * @return The GridPoint (or null)
   */
  public GridPoint findClosest(int xCoord, int yCoord, boolean assigned) {
    GridPoint point = null;

    final int xBlock = getXBlock(xCoord);
    final int yBlock = getYBlock(yCoord);

    double resolution2 = (double) resolution * resolution;
    for (int x = Math.max(0, xBlock - 1); x <= Math.min(grid.length - 1, xBlock + 1); x++) {
      for (int y = Math.max(0, yBlock - 1); y <= Math.min(grid[0].length - 1, yBlock + 1); y++) {
        if (grid[x][y] != null) {
          @SuppressWarnings("unchecked")
          final List<GridPoint> points = grid[x][y];

          for (final GridPoint p : points) {
            if (p.isAssigned() == assigned) {
              final double d2 = p.distanceSquared(xCoord, yCoord);
              if (d2 < resolution2) {
                resolution2 = d2;
                point = p;
              }
            }
          }
        }
      }
    }

    return point;
  }

  /**
   * Gets the resolution.
   *
   * @return the resolution
   */
  public int getResolution() {
    return resolution;
  }

  /**
   * Sets the search mode.
   *
   * @param searchMode the searchMode to set (see {@link #getSearchModes()}).
   */
  public void setSearchMode(int searchMode) {
    this.searchMode = searchMode;
  }

  /**
   * Gets the search mode.
   *
   * @return the searchMode
   */
  public int getSearchMode() {
    return searchMode;
  }
}
