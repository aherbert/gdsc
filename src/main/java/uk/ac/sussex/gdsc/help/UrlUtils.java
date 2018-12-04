/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2018 Alex Herbert
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

package uk.ac.sussex.gdsc.help;

/**
 * Provides URLs.
 */
public final class UrlUtils {
  private static final String BASE_URL =
      "http://www.sussex.ac.uk/gdsc/intranet/microscopy/UserSupport/AnalysisProtocol/imagej/";

  /** The URL for the colocalisation web page. */
  public static final String COLOCALISATION = BASE_URL + "colocalisation";
  /** The URL for the utility plugins web page. */
  public static final String UTILITY = BASE_URL + "utility";
  /** The URL for the FindFoci web page. */
  public static final String FIND_FOCI = BASE_URL + "findfoci";

  /** No public construction. */
  private UrlUtils() {}
}
