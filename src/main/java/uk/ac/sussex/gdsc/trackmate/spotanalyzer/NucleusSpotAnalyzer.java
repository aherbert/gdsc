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

package uk.ac.sussex.gdsc.trackmate.spotanalyzer;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.features.spot.SpotAnalyzer;
import java.util.Iterator;
import uk.ac.sussex.gdsc.trackmate.detector.NucleusDetector;

/**
 * This is a simple class that declares the features that are computed by the
 * {@link NucleusDetector}. If the spot does not contain the feature then it is set to zero.
 *
 * @param <T> the pixels type
 */
public class NucleusSpotAnalyzer<T> implements SpotAnalyzer<T> {
  /** Zero as a Double. */
  private static final Double ZERO = Double.valueOf(0.0);

  /** The model. */
  private final Model model;
  /** The frame. */
  private final int frame;
  /** The processing time. */
  private long processingTime;

  /**
   * Create a new instance.
   *
   * @param model the model
   * @param frame the frame
   */
  public NucleusSpotAnalyzer(final Model model, final int frame) {
    this.model = model;
    this.frame = frame;
  }

  @Override
  public boolean checkInput() {
    return true;
  }

  @Override
  public boolean process() {
    final long start = System.currentTimeMillis();
    final SpotCollection sc = model.getSpots();
    final Iterator<Spot> spotIt = sc.iterator(frame, false);

    while (spotIt.hasNext()) {
      final Spot spot = spotIt.next();
      setFeatureIfMissing(spot, NucleusDetector.NUCLEUS_MEAN_INSIDE);
      setFeatureIfMissing(spot, NucleusDetector.NUCLEUS_MEAN_OUTSIDE);
    }

    processingTime = System.currentTimeMillis() - start;
    return true;
  }

  private static void setFeatureIfMissing(Spot spot, String key) {
    if (spot.getFeature(key) == null) {
      spot.putFeature(key, ZERO);
    }
  }

  @Override
  public String getErrorMessage() {
    return "";
  }

  @Override
  public long getProcessingTime() {
    return processingTime;
  }
}
