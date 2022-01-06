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

package uk.ac.sussex.gdsc.foci;

/**
 * Contains the foci result of the FindFoci algorithm.
 */
public class FindFociResult {
  /**
   * The X coordinate.
   */
  int x;
  /**
   * The Y coordinate.
   */
  int y;
  /**
   * The Z coordinate (this is zero-indexed, not one-indexed as per ImageJ stack slices).
   */
  int z;
  /**
   * The internal ID used during the FindFoci routine. This can be ignored.
   */
  int id;
  /**
   * The number of pixels in the peak.
   */
  int count;
  /**
   * The sum of the peak intensity.
   */
  double totalIntensity;
  /**
   * The peak maximum value.
   */
  float maxValue;
  /**
   * The peak highest saddle point.
   */
  float highestSaddleValue;
  /**
   * The peak ID of the touching peak with the highest saddle point.
   */
  int saddleNeighbourId;
  /**
   * The average of the peak intensity.
   */
  double averageIntensity;
  /**
   * The sum of the peak intensity above the background.
   */
  double totalIntensityAboveBackground;
  /**
   * The average of the peak intensity above the background.
   */
  double averageIntensityAboveBackground;
  /**
   * The number of pixels in the peak above the highest saddle.
   */
  int countAboveSaddle;
  /**
   * The sum of the peak intensity above the highest saddle.
   */
  double intensityAboveSaddle;
  /**
   * The sum of the peak intensity above the minimum value of the analysed image.
   */
  double totalIntensityAboveImageMinimum;
  /**
   * The average of the peak intensity above the minimum value of the analysed image.
   */
  double averageIntensityAboveImageMinimum;
  /**
   * The custom sort value. This is used internally to sort the results using values not stored in
   * the result array.
   */
  double sortValue;
  /**
   * The state (i.e. pixel value) from the mask image
   */
  int state;
  /**
   * The allocated object from the mask image.
   */
  int object;
  /**
   * The minimum x range covered by the peak. This is used when merging peaks above the minimum
   * saddle value.
   */
  int minx;
  /**
   * The minimum y range covered by the peak. This is used when merging peaks above the minimum
   * saddle value.
   */
  int miny;
  /**
   * The minimum z range covered by the peak. This is used when merging peaks above the minimum
   * saddle value.
   */
  int minz;
  /**
   * The maximum x range covered by the peak. This is used when merging peaks above the minimum
   * saddle value.
   */
  int maxx;
  /**
   * The maximum y range covered by the peak. This is used when merging peaks above the minimum
   * saddle value.
   */
  int maxy;
  /**
   * The maximum z range covered by the peak. This is used when merging peaks above the minimum
   * saddle value.
   */
  int maxz;

  /**
   * Instantiates a new find foci result.
   */
  public FindFociResult() {}

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  protected FindFociResult(FindFociResult source) {
    x = source.x;
    y = source.y;
    z = source.z;
    id = source.id;
    count = source.count;
    totalIntensity = source.totalIntensity;
    maxValue = source.maxValue;
    highestSaddleValue = source.highestSaddleValue;
    saddleNeighbourId = source.saddleNeighbourId;
    averageIntensity = source.averageIntensity;
    totalIntensityAboveBackground = source.totalIntensityAboveBackground;
    averageIntensityAboveBackground = source.averageIntensityAboveBackground;
    countAboveSaddle = source.countAboveSaddle;
    intensityAboveSaddle = source.intensityAboveSaddle;
    totalIntensityAboveImageMinimum = source.totalIntensityAboveImageMinimum;
    averageIntensityAboveImageMinimum = source.averageIntensityAboveImageMinimum;
    sortValue = source.sortValue;
    state = source.state;
    object = source.object;
    minx = source.minx;
    miny = source.miny;
    minz = source.minz;
    maxx = source.maxx;
    maxy = source.maxy;
    maxz = source.maxz;
  }

  /**
   * Returns a copy of this result.
   *
   * @return the copy
   */
  public FindFociResult copy() {
    return new FindFociResult(this);
  }

  /**
   * Gets the coordinates.
   *
   * @return the coordinates [XYZ]
   */
  public int[] getCoordinates() {
    return new int[] {x, y, z};
  }

  /**
   * Update the bounds using the union with the given result.
   *
   * @param result the result
   */
  void updateBounds(FindFociResult result) {
    minx = Math.min(minx, result.minx);
    miny = Math.min(miny, result.miny);
    minz = Math.min(minz, result.minz);
    maxx = Math.max(maxx, result.maxx);
    maxy = Math.max(maxy, result.maxy);
    maxz = Math.max(maxz, result.maxz);
  }

  /**
   * The X coordinate.
   *
   * @return the x
   */
  public int getX() {
    return x;
  }

  /**
   * The Y coordinate.
   *
   * @return the y
   */
  public int getY() {
    return y;
  }

  /**
   * The Z coordinate (this is zero-indexed, not one-indexed as per ImageJ stack slices).
   *
   * @return the z
   */
  public int getZ() {
    return z;
  }

  /**
   * The number of pixels in the peak.
   *
   * @return the count
   */
  public int getCount() {
    return count;
  }

  /**
   * The sum of the peak intensity.
   *
   * @return the total intensity
   */
  public double getTotalIntensity() {
    return totalIntensity;
  }

  /**
   * The peak maximum value.
   *
   * @return the max value
   */
  public float getMaxValue() {
    return maxValue;
  }

  /**
   * The peak highest saddle point.
   *
   * @return the highest saddle value
   */
  public float getHighestSaddleValue() {
    return highestSaddleValue;
  }

  /**
   * The peak ID of the touching peak with the highest saddle point.
   *
   * @return the saddle neighbour id
   */
  public int getSaddleNeighbourId() {
    return saddleNeighbourId;
  }

  /**
   * The average of the peak intensity.
   *
   * @return the average intensity
   */
  public double getAverageIntensity() {
    return averageIntensity;
  }

  /**
   * The sum of the peak intensity above the background.
   *
   * @return the total intensity above background
   */
  public double getTotalIntensityAboveBackground() {
    return totalIntensityAboveBackground;
  }

  /**
   * The average of the peak intensity above the background.
   *
   * @return the average intensity above background
   */
  public double getAverageIntensityAboveBackground() {
    return averageIntensityAboveBackground;
  }

  /**
   * The number of pixels in the peak above the highest saddle.
   *
   * @return the count above saddle
   */
  public int getCountAboveSaddle() {
    return countAboveSaddle;
  }

  /**
   * The sum of the peak intensity above the highest saddle.
   *
   * @return the intensity above saddle
   */
  public double getIntensityAboveSaddle() {
    return intensityAboveSaddle;
  }

  /**
   * The sum of the peak intensity above the minimum value of the analysed image.
   *
   * @return the total intensity above image minimum
   */
  public double getTotalIntensityAboveImageMinimum() {
    return totalIntensityAboveImageMinimum;
  }

  /**
   * The average of the peak intensity above the minimum value of the analysed image.
   *
   * @return the average intensity above image minimum
   */
  public double getAverageIntensityAboveImageMinimum() {
    return averageIntensityAboveImageMinimum;
  }

  /**
   * The state (i.e. pixel value) from the mask image
   *
   * @return the state
   */
  public int getState() {
    return state;
  }

  /**
   * The allocated object from the mask image.
   *
   * @return the object
   */
  public int getObject() {
    return object;
  }

  /**
   * The minimum x range covered by the peak.
   *
   * @return the minx
   */
  public int getMinx() {
    return minx;
  }

  /**
   * The minimum y range covered by the peak.
   *
   * @return the miny
   */
  public int getMiny() {
    return miny;
  }

  /**
   * The minimum z range covered by the peak.
   *
   * @return the minz
   */

  public int getMinz() {
    return minz;
  }

  /**
   * The maximum x range covered by the peak.
   *
   * @return the maxx
   */
  public int getMaxx() {
    return maxx;
  }

  /**
   * The maximum y range covered by the peak.
   *
   * @return the maxy
   */
  public int getMaxy() {
    return maxy;
  }

  /**
   * The maximum z range covered by the peak.
   *
   * @return the maxz
   */
  public int getMaxz() {
    return maxz;
  }
}
