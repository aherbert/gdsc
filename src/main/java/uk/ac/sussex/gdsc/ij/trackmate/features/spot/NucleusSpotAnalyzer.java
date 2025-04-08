/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2025 Alex Herbert
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

package uk.ac.sussex.gdsc.ij.trackmate.features.spot;

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.features.spot.SpotAnalyzer;
import fiji.plugin.trackmate.features.spot.SpotAnalyzerFactory;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.ij.trackmate.detector.NucleusDetector;

/**
 * This is a simple class that declares the features that are computed by the
 * {@link NucleusDetector}. If the spot does not contain the feature then it is set to zero.
 *
 * @param <T> the pixels type
 */
public class NucleusSpotAnalyzer<T> implements SpotAnalyzer<T> {
  @Override
  public void process(Iterable<Spot> spots) {
    spots.forEach(spot -> {
      SpotAnalyzers.setFeatureIfMissing(spot, NucleusDetector.NUCLEUS_MEAN_INSIDE);
      SpotAnalyzers.setFeatureIfMissing(spot, NucleusDetector.NUCLEUS_MEAN_OUTSIDE);
    });
  }

  /**
   * A factory for creating NucleusSpotAnalyzer objects.
   *
   * @param <T> the pixels type
   */
  @Plugin(type = SpotAnalyzerFactory.class, priority = 1d)
  public static class Factory<T extends RealType<T> & NativeType<T>>
      implements SpotAnalyzerFactory<T> {
    private static final String KEY = "NUCLEUS_ANALYZER";
    private static final String NAME = "Nucleus analyzer";
    private static final String INFO_TEXT =
        "Exposes the mean inside and outside the nucleus computed "
            + "by the nucleus detector during spot detection. This analysis cannot be performed "
            + "separately to the nucleus detector; in that case features will be set to zero.";

    private static final List<String> FEATURES;
    private static final Map<String, Boolean> IS_INT;
    private static final Map<String, String> FEATURE_NAMES;
    private static final Map<String, String> FEATURE_SHORT_NAMES;
    private static final Map<String, Dimension> FEATURE_DIMENSIONS;

    static {
      final Map<String, Boolean> isInt = new HashMap<>(2);
      final Map<String, String> featureNames = new HashMap<>(2);
      final Map<String, String> featureShortNames = new HashMap<>(2);
      final Map<String, Dimension> featureDimensions = new HashMap<>(2);

      isInt.put(NucleusDetector.NUCLEUS_MEAN_INSIDE, false);
      featureNames.put(NucleusDetector.NUCLEUS_MEAN_INSIDE, "Mean inside nucleus");
      featureShortNames.put(NucleusDetector.NUCLEUS_MEAN_INSIDE, "Mean inside");
      featureDimensions.put(NucleusDetector.NUCLEUS_MEAN_INSIDE, Dimension.INTENSITY);

      isInt.put(NucleusDetector.NUCLEUS_MEAN_OUTSIDE, false);
      featureNames.put(NucleusDetector.NUCLEUS_MEAN_OUTSIDE, "Mean outside nucleus");
      featureShortNames.put(NucleusDetector.NUCLEUS_MEAN_OUTSIDE, "Mean outside");
      featureDimensions.put(NucleusDetector.NUCLEUS_MEAN_OUTSIDE, Dimension.INTENSITY);

      FEATURES = Collections.unmodifiableList(
          Arrays.asList(NucleusDetector.NUCLEUS_MEAN_INSIDE, NucleusDetector.NUCLEUS_MEAN_OUTSIDE));
      IS_INT = Collections.unmodifiableMap(isInt);
      FEATURE_NAMES = Collections.unmodifiableMap(featureNames);
      FEATURE_SHORT_NAMES = Collections.unmodifiableMap(featureShortNames);
      FEATURE_DIMENSIONS = Collections.unmodifiableMap(featureDimensions);
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
    public Map<String, Boolean> getIsIntFeature() {
      return IS_INT;
    }

    @Override
    public boolean isManualFeature() {
      return false;
    }

    @Override
    public String getInfoText() {
      return INFO_TEXT;
    }

    @Override
    public ImageIcon getIcon() {
      return null;
    }

    @Override
    public String getName() {
      return NAME;
    }

    @Override
    public SpotAnalyzer<T> getAnalyzer(ImgPlus<T> img, int frame, int channel) {
      return new NucleusSpotAnalyzer<>();
    }
  }
}
