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

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ObjDoubleConsumer;
import uk.ac.sussex.gdsc.core.trees.DoubleDistanceFunction;
import uk.ac.sussex.gdsc.core.trees.DoubleDistanceFunctions;
import uk.ac.sussex.gdsc.core.trees.KdTrees;
import uk.ac.sussex.gdsc.core.trees.ObjDoubleKdTree;
import uk.ac.sussex.gdsc.core.utils.ValidationUtils;

/**
 * Stores a set of {@link FindFociResult} within an indexed structure suitable for efficient search
 * of assigned and unassigned results. All find operations return a result that is either assigned
 * or unassigned. The assigned status is mutable and may be set by the caller on the returned
 * object.
 */
public final class AssignedFindFociResultSearchIndex {
  private final ObjDoubleKdTree<AssignedFindFociResult> tree;
  private final DoubleDistanceFunction distanceFunction;
  private double searchDistance;
  private SearchMode searchMode;

  /**
   * Define the search modes.
   */
  public enum SearchMode {
    /** Search for the highest result. */
    HIGHEST("Highest"),
    /** Search for the closest result. */
    CLOSEST("Closest");

    private final String name;

    /**
     * Create an instance.
     *
     * @param name the name
     */
    SearchMode(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return name;
    }

    /**
     * Get the search mode for the given ordinal.
     *
     * @param value the value
     * @return the search mode (or null)
     */
    public static SearchMode forOrdinal(int value) {
      if (value == 0) {
        return SearchMode.HIGHEST;
      } else if (value == 1) {
        return SearchMode.CLOSEST;
      }
      return null;
    }
  }

  /**
   * Store a single result.
   */
  private static class TreeResult implements ObjDoubleConsumer<AssignedFindFociResult> {
    double distance;
    AssignedFindFociResult result;

    @Override
    public void accept(AssignedFindFociResult result, double distance) {
      this.result = result;
      this.distance = distance;
    }
  }

  /**
   * Create an instance. The scale is used to define the distance represented by the XYZ voxel
   * dimensions used in the results. The initial search distance is set at 10 pixels in the X
   * direction.
   *
   * @param results the results
   * @param scaleX the scale X
   * @param scaleY the scale Y
   * @param scaleZ the scale Z
   */
  public AssignedFindFociResultSearchIndex(List<FindFociResult> results, double scaleX,
      double scaleY, double scaleZ) {
    ValidationUtils.checkStrictlyPositive(scaleX, "scaleX");
    ValidationUtils.checkStrictlyPositive(scaleY, "scaleY");

    // Check for 2D / 3D
    if (is3d(results)) {
      ValidationUtils.checkStrictlyPositive(scaleZ, "scaleZ");

      // Create the dimension weighting for the tree
      final double[] weight = {1.0 / scaleX, 1.0 / scaleY, 1.0 / scaleZ};
      tree = KdTrees.newObjDoubleKdTree(3, i -> weight[i]);

      // Populate the tree
      results.stream().forEach(r -> {
        final double[] location = {r.getX(), r.getY(), r.getZ()};
        tree.add(location, new AssignedFindFociResult(r));
      });

      // Scale relative to X
      final double sy = scaleY / scaleX;
      final double sz = scaleZ / scaleX;
      distanceFunction = new DoubleDistanceFunction() {
        @Override
        public double distance(double[] p1, double[] p2) {
          final double dx = p1[0] - p2[0];
          final double dy = (p1[1] - p2[1]) * sy;
          final double dz = (p1[2] - p2[2]) * sz;
          return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public double distanceToRectangle(double[] point, double[] min, double[] max) {
          final double dx = getDistanceOutsideRange(point[0], min[0], max[0]);
          final double dy = getDistanceOutsideRange(point[1], min[1], max[1]) * sy;
          final double dz = getDistanceOutsideRange(point[2], min[2], max[2]) * sz;
          return dx * dx + dy * dy + dz * dz;
        }
      };
    } else {
      // Create the dimension weighting for the tree
      final double[] weight = {1.0 / scaleX, 1.0 / scaleY};
      tree = KdTrees.newObjDoubleKdTree(2, i -> weight[i]);

      // Populate the tree
      results.stream().forEach(r -> {
        final double[] location = {r.getX(), r.getY()};
        tree.add(location, new AssignedFindFociResult(r));
      });

      // Typically the scale x and y are the same so we can ignore scaling.
      if (scaleX == scaleY) {
        distanceFunction = DoubleDistanceFunctions.SQUARED_EUCLIDEAN_2D;
      } else {
        // Scale relative to X
        final double sy = scaleY / scaleX;
        distanceFunction = new DoubleDistanceFunction() {
          @Override
          public double distance(double[] p1, double[] p2) {
            final double dx = p1[0] - p2[0];
            final double dy = (p1[1] - p2[1]) * sy;
            return dx * dx + dy * dy;
          }

          @Override
          public double distanceToRectangle(double[] point, double[] min, double[] max) {
            final double dx = getDistanceOutsideRange(point[0], min[0], max[0]);
            final double dy = getDistanceOutsideRange(point[1], min[1], max[1]) * sy;
            return dx * dx + dy * dy;
          }
        };
      }
    }

    setSearchDistance(10);
  }

  /**
   * Checks if the results are 3D.
   *
   * @param results the results
   * @return true, if 3D
   */
  private static boolean is3d(List<FindFociResult> results) {
    if (!results.isEmpty()) {
      final int z = results.get(0).getZ();
      final Iterator<FindFociResult> it = results.iterator();
      while (it.hasNext()) {
        if (it.next().getZ() != z) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Gets the distance that the value is outside the min - max range. Return 0 if inside the range.
   *
   * <p>This does not work for NaN values.
   *
   * @param value the value
   * @param min the min
   * @param max the max
   * @return the distance
   */
  private static double getDistanceOutsideRange(double value, double min, double max) {
    if (value > max) {
      return value - max;
    }
    return (value < min) ? min - value : 0;
  }

  /**
   * Get the number of results stored in the index.
   *
   * @return the size
   */
  public int size() {
    return tree.size();
  }

  /**
   * Gets the distance function. This will compute a squared Euclidean distance between points. The
   * distances in each axis are relative to the x dimension. The dimension scales are set in the
   * constructor.
   *
   * @return the distance function
   */
  public DoubleDistanceFunction getDistanceFunction() {
    return distanceFunction;
  }

  /**
   * Sets the maximum search distance along the x-axis in scaled units.
   *
   * @param pixels the new search distance
   * @return a reference to this object
   * @throws IllegalArgumentException if the distance is not positive
   */
  public AssignedFindFociResultSearchIndex setSearchDistance(int pixels) {
    ValidationUtils.checkPositive(pixels);
    // This will not matter if the dimensions are 2 or 3
    searchDistance = distanceFunction.distance(new double[] {pixels, 0, 0}, new double[3]);
    return this;
  }

  /**
   * Gets the maximum search distance along the x-axis in scaled units.
   *
   * @return the search distance
   */
  public double getSearchDistance() {
    return Math.sqrt(searchDistance);
  }

  /**
   * Sets the search mode.
   *
   * @param searchMode the searchMode to set
   * @return a reference to this object
   */
  public AssignedFindFociResultSearchIndex setSearchMode(SearchMode searchMode) {
    this.searchMode = searchMode;
    return this;
  }

  /**
   * Gets the search mode.
   *
   * @return the searchMode
   */
  public SearchMode getSearchMode() {
    return searchMode;
  }

  /**
   * Sets the assigned flag on all the points.
   *
   * @param assigned the assigned status
   */
  public void setAssigned(boolean assigned) {
    tree.forEach((xyz, r) -> r.setAssigned(assigned));
  }

  /**
   * Performs the given action for each item in the tree until all elements have been processed or
   * the action throws an exception. The iteration order is unspecified. Exceptions thrown by the
   * action are relayed to the caller.
   *
   * @param action the action to be performed for each element
   */
  public void forEach(Consumer<? super AssignedFindFociResult> action) {
    tree.forEach((xyz, r) -> action.accept(r));
  }

  /**
   * Find the result using the current search mode within the sampling resolution from the given
   * coordinates with the specified assigned status.
   *
   * @param x the x coord
   * @param y the y coord
   * @param z the z coord
   * @param assigned the assigned status
   * @return The result (or null)
   */
  public AssignedFindFociResult find(int x, int y, int z, boolean assigned) {
    if (searchMode == SearchMode.CLOSEST) {
      return findClosest(x, y, z, assigned);
    }
    return findHighest(x, y, z, assigned);
  }

  /**
   * Find the point that matches the given coordinates with the specified assigned status.
   *
   * <p>Note: Duplicate xyz values in the results that match the specified search coordinates will
   * return an arbitrary result.
   *
   * @param x the x coord
   * @param y the y coord
   * @param z the z coord
   * @param assigned the assigned status
   * @return The result (or null)
   */
  public AssignedFindFociResult findExact(int x, int y, int z, boolean assigned) {
    return findClosestWithinDistance(x, y, z, assigned, 0);
  }

  /**
   * Find the closest result within the sampling resolution from the given coordinates with the
   * specified assigned status.
   *
   * @param x the x coord
   * @param y the y coord
   * @param z the z coord
   * @param assigned the assigned status
   * @return The result (or null)
   */
  public AssignedFindFociResult findClosest(int x, int y, int z, boolean assigned) {
    return findClosestWithinDistance(x, y, z, assigned, searchDistance);
  }

  /**
   * Find the closest result within the search distance from the given coordinates with the
   * specified assigned status.
   *
   * @param x the x coord
   * @param y the y coord
   * @param z the z coord
   * @param assigned the assigned status
   * @param searchDistance the search distance
   * @return The result (or null)
   */
  private AssignedFindFociResult findClosestWithinDistance(int x, int y, int z, boolean assigned,
      double searchDistance) {
    final double[] location = {x, y, z};
    final TreeResult result = new TreeResult();
    tree.nearestNeighbour(location, distanceFunction, r -> r.isAssigned() == assigned, result);
    // Note: If nothing was found then distance and result will be null
    return result.distance <= searchDistance ? result.result : null;
  }

  /**
   * Find the highest result within the sampling resolution from the given coordinates with the
   * specified assigned status.
   *
   * @param x the x coord
   * @param y the y coord
   * @param z the z coord
   * @param assigned the assigned status
   * @return The result (or null)
   * @see FindFociResult#getMaxValue()
   */
  public AssignedFindFociResult findHighest(int x, int y, int z, boolean assigned) {
    final double[] location = {x, y, z};
    final TreeResult result = new TreeResult();
    tree.findNeighbours(location, searchDistance, distanceFunction, (t, value) -> {
      // Must match the assigned status
      if (t.isAssigned() == assigned
          // Must be higher
          && (result.result == null
              || result.result.getResult().getMaxValue() < t.getResult().getMaxValue())) {
        result.accept(t, value);
      }
    });
    return result.result;
  }
}
