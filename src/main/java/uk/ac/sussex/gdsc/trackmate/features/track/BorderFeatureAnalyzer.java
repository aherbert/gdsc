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

package uk.ac.sussex.gdsc.trackmate.features.track;

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.features.track.TrackAnalyzer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.swing.ImageIcon;
import org.apache.commons.lang3.concurrent.ConcurrentRuntimeException;
import org.scijava.plugin.Plugin;

/**
 * Declare features related to the image border. Currently computes the mean and minimum distance to
 * the edge. The edge is defined using the bounding box over the XY positions of the SpotCollection
 * as access to the original input image is not available.
 */
@Plugin(type = TrackAnalyzer.class)
public class BorderFeatureAnalyzer implements TrackAnalyzer {

  private static final String KEY = "Track border";
  private static final String MEAN_DISTANCE = "TRACK_MEAN_DISTANCE_TO_EDGE";
  private static final String MIN_DISTANCE = "TRACK_MIN_DISTANCE_TO_EDGE";
  private static final List<String> FEATURES = new ArrayList<>(2);
  private static final Map<String, String> FEATURE_NAMES = new HashMap<>(2);
  private static final Map<String, String> FEATURE_SHORT_NAMES = new HashMap<>(2);
  private static final Map<String, Dimension> FEATURE_DIMENSIONS = new HashMap<>(2);
  private static final Map<String, Boolean> IS_INT = new HashMap<>(2);

  static {
    FEATURES.add(MEAN_DISTANCE);
    FEATURES.add(MIN_DISTANCE);

    FEATURE_NAMES.put(MEAN_DISTANCE, "Mean distance to edge");
    FEATURE_NAMES.put(MIN_DISTANCE, "Min distance to edge");

    FEATURE_SHORT_NAMES.put(MEAN_DISTANCE, "MeanD");
    FEATURE_SHORT_NAMES.put(MIN_DISTANCE, "MinD");

    // Could be POSITION
    FEATURE_DIMENSIONS.put(MEAN_DISTANCE, Dimension.LENGTH);
    FEATURE_DIMENSIONS.put(MIN_DISTANCE, Dimension.LENGTH);

    IS_INT.put(MEAN_DISTANCE, Boolean.FALSE);
    IS_INT.put(MIN_DISTANCE, Boolean.FALSE);
  }

  private int numThreads;
  private long processingTime;

  /**
   * Create an instance.
   */
  public BorderFeatureAnalyzer() {
    setNumThreads();
  }

  @Override
  public boolean isLocal() {
    return true;
  }

  @Override
  public void process(final Collection<Integer> trackIDs, final Model model) {
    if (trackIDs.isEmpty()) {
      processingTime = 0;
      return;
    }

    final long start = System.currentTimeMillis();

    // Q. How do we get the current dimensions of the image?
    // TrackMate.getSettings() would provide the input image width/height in pixels.
    // But we do not have access to the current TrackMate instance or Settings.
    // Find the bounds of the Spots as a rough approximation to the edge of the region.
    // The results will be in the calibrated units (not pixels).
    final double[] limits = {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
        Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
    model.getSpots().iterable(false).forEach(spot -> {
      final double x = spot.getDoublePosition(0);
      final double y = spot.getDoublePosition(1);
      limits[0] = x < limits[0] ? x : limits[0];
      limits[1] = x > limits[1] ? x : limits[1];
      limits[2] = y < limits[2] ? y : limits[2];
      limits[3] = y > limits[3] ? y : limits[3];
    });
    final double minx = limits[0];
    final double maxx = limits[1];
    final double miny = limits[2];
    final double maxy = limits[3];

    final ExecutorService executor = Executors.newFixedThreadPool(numThreads);
    final FeatureModel fm = model.getFeatureModel();
    final TrackModel trackModel = model.getTrackModel();
    trackIDs.forEach(trackID -> {
      executor.submit(() -> {
        final Set<Spot> track = trackModel.trackSpots(trackID);

        double sum = 0;
        double min = Double.MAX_VALUE;

        for (final Spot spot : track) {
          final double d = distanceToEdge(minx, maxx, spot.getFeature(Spot.POSITION_X), miny, maxy,
              spot.getFeature(Spot.POSITION_Y));
          sum += d;
          min = Math.min(min, d);
        }

        fm.putTrackFeature(trackID, MEAN_DISTANCE, sum / track.size());
        fm.putTrackFeature(trackID, MIN_DISTANCE, min);
      });
    });

    executor.shutdown();

    try {
      executor.awaitTermination(1, TimeUnit.DAYS);
    } catch (final InterruptedException ex) {
      // Restore interrupted state...
      Thread.currentThread().interrupt();
      throw new ConcurrentRuntimeException(ex);
    } finally {
      processingTime = System.currentTimeMillis() - start;
    }
  }

  /**
   * Compute the distance to the edge. Assumes the coordinates will be inside the limits.
   *
   * @param minx the minimum x
   * @param maxx the maximum x
   * @param x the x
   * @param miny the minimum y
   * @param maxy the maximum y
   * @param y the y
   * @return the distance
   */
  private static double distanceToEdge(double minx, double maxx, double x, double miny, double maxy,
      double y) {
    return Math.min(distanceToEdge(minx, maxx, x), distanceToEdge(miny, maxy, y));
  }

  /**
   * Compute the distance to the edge. Assumes the coordinates will be inside the limits.
   *
   * @param min the minimum
   * @param max the maximum
   * @param x the x
   * @return the distance
   */
  private static double distanceToEdge(double min, double max, double x) {
    return Math.min(x - min, max - x);
  }

  @Override
  public int getNumThreads() {
    return numThreads;
  }

  @Override
  public void setNumThreads() {
    this.numThreads = Runtime.getRuntime().availableProcessors();
  }

  @Override
  public void setNumThreads(final int numThreads) {
    this.numThreads = Math.max(1, numThreads);
  }

  @Override
  public long getProcessingTime() {
    return processingTime;
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public List<String> getFeatures() {
    return FEATURES;
  }

  @Override
  public Map<String, String> getFeatureShortNames() {
    return FEATURE_SHORT_NAMES;
  }

  @Override
  public Map<String, String> getFeatureNames() {
    return FEATURE_NAMES;
  }

  @Override
  public Map<String, Dimension> getFeatureDimensions() {
    return FEATURE_DIMENSIONS;
  }

  @Override
  public String getInfoText() {
    return null;
  }

  @Override
  public ImageIcon getIcon() {
    return null;
  }

  @Override
  public String getName() {
    return KEY;
  }

  @Override
  public Map<String, Boolean> getIsIntFeature() {
    return IS_INT;
  }

  @Override
  public boolean isManualFeature() {
    return false;
  }
}
