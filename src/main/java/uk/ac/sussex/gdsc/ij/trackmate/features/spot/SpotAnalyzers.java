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

import fiji.plugin.trackmate.Spot;

/**
 * Helper class for instances of SpotAnalyzer.
 */
class SpotAnalyzers {
  /** Zero as a Double. */
  private static final Double ZERO = Double.valueOf(0);

  /**
   * Sets the feature if missing.
   *
   * @param spot the spot
   * @param key the key
   */
  static void setFeatureIfMissing(Spot spot, String key) {
    if (spot.getFeature(key) == null) {
      spot.putFeature(key, ZERO);
    }
  }
}
