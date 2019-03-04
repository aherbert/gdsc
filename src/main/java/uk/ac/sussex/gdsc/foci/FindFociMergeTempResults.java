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

package uk.ac.sussex.gdsc.foci;

/**
 * Contains the results of the FindFoci algorithm during the merge stage.
 */
public class FindFociMergeTempResults {

  /** The results array. */
  final FindFociResult[] resultsArray;

  /** The saddle points. */
  final FindFociSaddleList[] saddlePoints;

  /** The peak id map. */
  final int[] peakIdMap;

  /** The result list. */
  final FindFociResult[] resultList;

  /**
   * Instantiates a new find foci merge results.
   *
   * @param resultsArray the results array
   * @param saddlePoints the saddle points
   * @param peakIdMap the peak id map
   * @param resultList the result list
   */
  public FindFociMergeTempResults(FindFociResult[] resultsArray, FindFociSaddleList[] saddlePoints,
      int[] peakIdMap, FindFociResult[] resultList) {
    this.resultsArray = resultsArray;
    this.saddlePoints = saddlePoints;
    this.peakIdMap = peakIdMap;
    this.resultList = resultList;
  }

  /**
   * Gets the results array.
   *
   * @return the results array
   */
  public FindFociResult[] getResultsArray() {
    return resultsArray;
  }

  /**
   * Gets the saddle points.
   *
   * @return the saddle points
   */
  public FindFociSaddleList[] getSaddlePoints() {
    return saddlePoints;
  }

  /**
   * Gets the peak id map.
   *
   * @return the peak id map
   */
  public int[] getPeakIdMap() {
    return peakIdMap;
  }

  /**
   * Gets the result list.
   *
   * @return the result list
   */
  public FindFociResult[] getResultList() {
    return resultList;
  }
}
