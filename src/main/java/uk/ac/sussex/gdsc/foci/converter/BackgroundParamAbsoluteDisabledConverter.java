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
package uk.ac.sussex.gdsc.foci.converter;

import org.jdesktop.beansbinding.Converter;

import uk.ac.sussex.gdsc.foci.FindFociProcessor;

/**
 * Convert the background method to a flag indicating if absolute is disabled.
 */
public class BackgroundParamAbsoluteDisabledConverter extends Converter<Integer, Boolean> {
  @Override
  public Boolean convertForward(Integer paramS) {
    final int backgroundMethod = paramS.intValue();
    return Boolean.valueOf(backgroundMethod != FindFociProcessor.BACKGROUND_ABSOLUTE);
  }

  @Override
  public Integer convertReverse(Boolean paramT) {
    // N/A
    return null;
  }
}
