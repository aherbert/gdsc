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

package uk.ac.sussex.gdsc.format;

import java.text.DecimalFormat;
import java.text.ParsePosition;

/**
 * Provide an extension to the {@link DecimalFormat} with a minimum and maximum limit. If the parsed
 * source is outside the bounds it will be set the corresponding limit.
 */
public class LimitedNumberFormat extends DecimalFormat {
  /**
   * Auto-generated.
   */
  private static final long serialVersionUID = 20181207;

  private final double min;
  private final double max;

  /**
   * Instantiates a new limited number format.
   *
   * @param min the min
   * @param max the max
   */
  public LimitedNumberFormat(double min, double max) {
    super();
    this.min = min;
    this.max = max;
  }

  /**
   * Instantiates a new limited number format.
   *
   * @param min the min
   */
  public LimitedNumberFormat(double min) {
    this(min, Double.MAX_VALUE);
  }

  @Override
  public Number parse(String source, ParsePosition parsePosition) {
    Number number = super.parse(source, parsePosition);
    if (number != null) {
      if (number.doubleValue() < min) {
        number = Double.valueOf(min);
      } else if (number.doubleValue() > max) {
        number = Double.valueOf(max);
      }
    }
    return number;
  }
}
