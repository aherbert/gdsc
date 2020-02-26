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

package uk.ac.sussex.gdsc.utils;

import ij.IJ;
import imagescience.ImageScience;
import imagescience.utility.VersionChecker;

/**
 * Utility for checking the ImageScience library.
 *
 * @see <a href= "https://imagescience.org/meijering/software/imagescience/">ImageScience: A Java
 *      Library for Scientific Image Computing</a>
 */
public final class ImageScienceUtils {
  /** The minimum ImageScience version. */
  public static final String MIN_IS_VERSION = "3.0.0";

  /**
   * No public construction.
   */
  private ImageScienceUtils() {}

  /**
   * Checks for the ImageScience library.
   *
   * @return true, if successful
   */
  public static boolean hasImageScience() {
    return hasImageScience(MIN_IS_VERSION);
  }

  /**
   * Checks for the ImageScience library.
   *
   * @param version the version
   * @return true, if successful
   */
  public static boolean hasImageScience(String version) {
    try {
      // Entire block in try-catch as the library may not be present
      if (VersionChecker.compare(ImageScience.version(), version) < 0) {
        throw new IllegalStateException();
      }
      return true;
    } catch (final Throwable ex) {
      return false;
    }
  }

  /**
   * Show an error stating that the minimum ImageScience version is required.
   *
   * @see IJ#error(String)
   */
  public static void showError() {
    showError(MIN_IS_VERSION);
  }

  /**
   * Show an error stating that the minimum ImageScience version is required.
   *
   * @param version the version
   * @see IJ#error(String)
   */
  public static void showError(String version) {
    IJ.error("Requires ImageScience version " + version + " or higher");
  }
}
