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

package uk.ac.sussex.gdsc.foci.converter;

import uk.ac.sussex.gdsc.foci.FindFociProcessorOptions.CentreMethod;

import org.jdesktop.beansbinding.Converter;

/**
 * Convert the centre method to a flag indicating if the centre parameter is enabled.
 */
public class CentreParamEnabledConverter extends Converter<Integer, Boolean> {
  @Override
  public Boolean convertForward(Integer paramS) {
    final int centreMethod = paramS.intValue();
    return Boolean.valueOf(centreMethod == CentreMethod.GAUSSIAN_SEARCH.ordinal()
        || centreMethod == CentreMethod.GAUSSIAN_ORIGINAL.ordinal()
        || centreMethod == CentreMethod.CENTRE_OF_MASS_SEARCH.ordinal()
        || centreMethod == CentreMethod.CENTRE_OF_MASS_ORIGINAL.ordinal());
  }

  @Override
  public Integer convertReverse(Boolean paramT) {
    // N/A
    return null;
  }
}
