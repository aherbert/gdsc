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

package uk.ac.sussex.gdsc.ij.trackmate.detector;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.SpotDetector;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.imglib2.Interval;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;

/**
 * Detector that creates spots from pre-computed spot data.
 *
 * @param <T> the pixels type
 */
public class PrecomputedDetector<T extends RealType<T> & NativeType<T>> implements SpotDetector<T> {
  /** The feature key for the spot category. */
  public static final String SPOT_CATEGORY = "CATEGORY";

  /** The spots. */
  private final List<RawSpot> rawSpots;
  /** The interval. */
  private final Interval interval;
  /** The pixel sizes in the 3 dimensions. */
  private final double[] calibration;
  /** Holder for the results of detection. */
  private List<Spot> spots;
  /** Error message holder. */
  private String errorMessage;
  /** Holder for the processing time. */
  private long processingTime;

  /**
   * Create a new instance.
   *
   * @param rawSpots the raw spots
   * @param interval the interval defining the region to process (can be null)
   * @param calibration the pixel sizes in the xyz dimensions (arrays smaller than 3 are
   *        zero-padded)
   */
  public PrecomputedDetector(List<RawSpot> rawSpots, Interval interval, double[] calibration) {
    this.rawSpots = rawSpots;
    this.interval = interval;
    this.calibration = Arrays.copyOf(calibration, 3);
  }

  @Override
  public boolean checkInput() {
    // No validation
    return true;
  }

  @Override
  public boolean process() {
    final long start = System.currentTimeMillis();

    Stream<RawSpot> stream = rawSpots.stream();

    // Crop to ROI
    if (interval != null) {
      final long[] min = new long[interval.numDimensions()];
      final long[] max = new long[min.length];
      interval.min(min);
      interval.max(max);
      // Convert to double for filtering
      final double[] dmin = pad(min, -Double.MAX_VALUE);
      final double[] dmax = pad(max, Double.MAX_VALUE);
      stream = stream.filter(r -> within(r.x, dmin[0], dmax[0]) && within(r.y, dmin[1], dmax[1])
          && within(r.z, dmin[2], dmax[2]));
    }

    // Scale using the calibration
    final double sx = calibration[0];
    final double sy = calibration[1];
    final double sz = calibration[2];
    // Q. Add a metric that can be used for spot quality?
    final double quality = 1;

    spots = stream.map(r -> {
      final Spot s = new Spot(r.x * sx, r.y * sy, r.z * sz, r.radius * sx, quality, r.id);
      s.putFeature(SPOT_CATEGORY, Double.valueOf(r.category));
      return s;
    }).collect(Collectors.toList());

    this.processingTime = System.currentTimeMillis() - start;
    return true;
  }

  /**
   * Pad the data to an array of size 3 and convert to double.
   *
   * @param data the data
   * @param padValue the pad value
   * @return the double[] array
   */
  private static double[] pad(long[] data, double padValue) {
    final double[] result = {padValue, padValue, padValue};
    for (int i = 0; i < data.length; i++) {
      result[i] = data[i];
    }
    return result;
  }

  /**
   * Check if the value x is within the low and high bounds inclusive.
   *
   * @param x the x
   * @param low the low
   * @param high the high
   * @return true if within
   */
  private static boolean within(double x, double low, double high) {
    return !(x < low || x > high);
  }

  @Override
  public List<Spot> getResult() {
    return spots;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public long getProcessingTime() {
    return processingTime;
  }
}
