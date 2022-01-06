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

package uk.ac.sussex.gdsc.foci;

/**
 * Stores a {@link FindFociResult} with an assigned flag for use in search.
 */
public final class AssignedFindFociResult {
  private final FindFociResult result;
  private boolean assigned;

  /**
   * Create an instance.
   *
   * @param result the result
   */
  public AssignedFindFociResult(FindFociResult result) {
    this.result = result;
  }

  /**
   * Gets the result.
   *
   * @return the result
   */
  public FindFociResult getResult() {
    return result;
  }

  /**
   * Sets the assigned flag.
   *
   * @param assigned the new assigned flag
   */
  public void setAssigned(boolean assigned) {
    this.assigned = assigned;
  }

  /**
   * Checks if is assigned.
   *
   * @return true, if is assigned
   */
  public boolean isAssigned() {
    return assigned;
  }
}
