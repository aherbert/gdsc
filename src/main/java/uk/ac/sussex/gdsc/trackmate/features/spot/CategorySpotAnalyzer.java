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

package uk.ac.sussex.gdsc.trackmate.features.spot;

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.features.spot.SpotAnalyzer;
import fiji.plugin.trackmate.features.spot.SpotAnalyzerFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import net.imagej.ImgPlus;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import org.scijava.plugin.Plugin;
import uk.ac.sussex.gdsc.trackmate.detector.PrecomputedDetector;

/**
 * This is a simple class that declares the features that are computed by the
 * {@link PrecomputedDetector}. If the spot does not contain the feature then it is set to zero.
 *
 * @param <T> the pixels type
 */
public class CategorySpotAnalyzer<T> implements SpotAnalyzer<T> {
  @Override
  public void process(Iterable<Spot> spots) {
    spots.forEach(
        spot -> SpotAnalyzers.setFeatureIfMissing(spot, PrecomputedDetector.SPOT_CATEGORY));
  }

  /**
   * A factory for creating NucleusSpotAnalyzer objects.
   *
   * @param <T> the pixels type
   */
  @Plugin(type = SpotAnalyzerFactory.class, priority = 1d)
  public static class Factory<T extends RealType<T> & NativeType<T>>
      implements SpotAnalyzerFactory<T> {
    private static final String KEY = "CATEGORY_ANALYZER";
    private static final String NAME = "Category analyzer";
    private static final String INFO_TEXT =
        "Exposes the category loaded by the pre-computed detector during spot detection. "
            + "If not present then the feature will be set to zero.";

    private static final List<String> FEATURES;
    private static final Map<String, Boolean> IS_INT;
    private static final Map<String, String> FEATURE_NAMES;
    private static final Map<String, String> FEATURE_SHORT_NAMES;
    private static final Map<String, Dimension> FEATURE_DIMENSIONS;

    static {
      FEATURES = Collections.singletonList(PrecomputedDetector.SPOT_CATEGORY);
      IS_INT = Collections.singletonMap(PrecomputedDetector.SPOT_CATEGORY, Boolean.TRUE);
      FEATURE_NAMES = Collections.singletonMap(PrecomputedDetector.SPOT_CATEGORY, "Category");
      FEATURE_SHORT_NAMES = Collections.singletonMap(PrecomputedDetector.SPOT_CATEGORY, "Category");
      FEATURE_DIMENSIONS =
          Collections.singletonMap(PrecomputedDetector.SPOT_CATEGORY, Dimension.NONE);
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
    public SpotAnalyzer<T> getAnalyzer(final ImgPlus<T> img, final int frame, final int channel) {
      return new CategorySpotAnalyzer<>();
    }
  }
}
