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

package uk.ac.sussex.gdsc.foci.converter;

import org.jdesktop.beansbinding.Converter;
import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.PeakMethod;

/**
 * Convert the peak method.
 */
public class PeakMethodConverter extends Converter<Integer, Object> {
  private static final String[] peakMethods = PeakMethod.getDescriptions();

  @Override
  public String convertForward(Integer paramT) {
    return peakMethods[paramT.intValue()];
  }

  @Override
  public Integer convertReverse(Object paramS) {
    for (int i = 0; i < peakMethods.length; i++) {
      if (peakMethods[i].equals(paramS)) {
        return Integer.valueOf(i);
      }
    }
    return null;
  }
}
