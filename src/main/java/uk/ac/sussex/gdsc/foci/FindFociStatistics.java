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
 * Contains the statistics results of the FindFoci algorithm.
 */
public class FindFociStatistics {
  /**
   * The minimum in the analysed region.
   */
  float regionMinimum;
  /**
   * The maximum in the analysed region.
   */
  float regionMaximum;
  /**
   * The mean in the analysed region.
   */
  double regionAverage;
  /**
   * The standard deviation in the analysed region.
   */
  double regionStdDev;
  /**
   * The total image intensity in the analysed region.
   */
  double regionTotal;
  /**
   * The image background level.
   */
  float background;
  /**
   * The total image intensity above the background.
   */
  double totalAboveBackground;
  /**
   * The minimum of the background region.
   */
  float backgroundRegionMinimum;
  /**
   * The maximum of the background region.
   */
  float backgroundRegionMaximum;
  /**
   * The mean of the background region.
   */
  double backgroundRegionAverage;
  /**
   * The standard deviation of the background region.
   */
  double backgroundRegionStdDev;
  /**
   * The minimum image value.
   */
  float imageMinimum;
  /**
   * The total image intensity above the minimum image value.
   */
  double totalAboveImageMinimum;

  /**
   * Instantiates a new find foci statistics.
   */
  public FindFociStatistics() {}

  /**
   * Copy constructor.
   *
   * @param source the source
   */
  protected FindFociStatistics(FindFociStatistics source) {
    regionMinimum = source.regionMinimum;
    regionMaximum = source.regionMaximum;
    regionAverage = source.regionAverage;
    regionStdDev = source.regionStdDev;
    regionTotal = source.regionTotal;
    background = source.background;
    totalAboveBackground = source.totalAboveBackground;
    backgroundRegionMinimum = source.backgroundRegionMinimum;
    backgroundRegionMaximum = source.backgroundRegionMaximum;
    backgroundRegionAverage = source.backgroundRegionAverage;
    backgroundRegionStdDev = source.backgroundRegionStdDev;
    imageMinimum = source.imageMinimum;
    totalAboveImageMinimum = source.totalAboveImageMinimum;
  }

  /**
   * Returns a copy of this statistics.
   *
   * @return the copy
   */
  public FindFociStatistics copy() {
    return new FindFociStatistics(this);
  }

  /**
   * Get the minimum in the analysed region.
   *
   * @return the region minimum
   */
  public float getRegionMinimum() {
    return regionMinimum;
  }

  /**
   * Get the maximum in the analysed region.
   *
   * @return the region maximum
   */
  public float getRegionMaximum() {
    return regionMaximum;
  }

  /**
   * Get the mean in the analysed region.
   *
   * @return the region average
   */
  public double getRegionAverage() {
    return regionAverage;
  }

  /**
   * Get the standard deviation in the analysed region.
   *
   * @return the region std dev
   */
  public double getRegionStdDev() {
    return regionStdDev;
  }

  /**
   * Get the total image intensity in the analysed region.
   *
   * @return the region total
   */
  public double getRegionTotal() {
    return regionTotal;
  }

  /**
   * Get the image background level.
   *
   * @return the background
   */
  public float getBackground() {
    return background;
  }

  /**
   * Get the total image intensity above the background.
   *
   * @return the total above background
   */
  public double getTotalAboveBackground() {
    return totalAboveBackground;
  }

  /**
   * Get the minimum of the background region.
   *
   * @return the background region minimum
   */
  public float getBackgroundRegionMinimum() {
    return backgroundRegionMinimum;
  }

  /**
   * Get the maximum of the background region.
   *
   * @return the background region maximum
   */
  public float getBackgroundRegionMaximum() {
    return backgroundRegionMaximum;
  }

  /**
   * Get the mean of the background region.
   *
   * @return the background region average
   */
  public double getBackgroundRegionAverage() {
    return backgroundRegionAverage;
  }

  /**
   * Get the standard deviation of the background region.
   *
   * @return the background region std dev
   */
  public double getBackgroundRegionStdDev() {
    return backgroundRegionStdDev;
  }

  /**
   * Get the minimum image value.
   *
   * @return the image minimum
   */
  public float getImageMinimum() {
    return imageMinimum;
  }

  /**
   * Get the total image intensity above the minimum image value.
   *
   * @return the total above image minimum
   */
  public double getTotalAboveImageMinimum() {
    return totalAboveImageMinimum;
  }
}
