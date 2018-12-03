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
package uk.ac.sussex.gdsc.format;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;

/**
 * Provide an extension to the {@link DecimalFormat} with a minimum and maximum limit. If the parsed
 * source is outside the bounds it will be set the corresponding limit.
 */
public class LimitedNumberFormat extends DecimalFormat {
  /**
   * Auto-generated.
   */
  private static final long serialVersionUID = -2564688480913124241L;

  private double min = Double.MIN_VALUE;
  private double max = Double.MAX_VALUE;

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
    super();
    this.min = min;
  }

  @Override
  public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
    final StringBuffer sb = super.format(number, toAppendTo, pos);
    return sb;
  }

  @Override
  public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
    final StringBuffer sb = super.format(number, toAppendTo, pos);
    return sb;
  }

  @Override
  public Number parse(String source, ParsePosition parsePosition) {
    // int currentIndex = parsePosition.getIndex();
    Number n = super.parse(source, parsePosition);
    if (n != null) {
      // if (n.doubleValue() < min || n.doubleValue() > max)
      // {
      // parsePosition.setErrorIndex(currentIndex);
      // parsePosition.setIndex(currentIndex);
      // n = null;
      // }
      if (n.doubleValue() < min) {
        n = Double.valueOf(min);
      } else if (n.doubleValue() > max) {
        n = Double.valueOf(max);
      }
    }
    return n;
  }

}
