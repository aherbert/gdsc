package gdsc.foci;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Calculates the match between a set of predicted points and the actual points.
 */
public class MatchCalculator
{
	/**
	 * Calculate the match results for the given actual and predicted points.
	 * Points that are within the distance threshold are identified as a match.
	 * The number of true positives, false positives and false negatives are calculated.
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 *            The distance threshold
	 * @return The match results
	 */
	public static MatchResult analyseResults2D(Coordinate[] actualPoints, Coordinate[] predictedPoints,
			double dThreshold)
	{
		return analyseResults2D(actualPoints, predictedPoints, dThreshold, null, null, null, null);
	}

	/**
	 * Calculate the match results for the given actual and predicted points.
	 * Points that are within the distance threshold are identified as a match.
	 * The number of true positives, false positives and false negatives are calculated.
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 *            The distance threshold
	 * @param TP
	 *            True Positives
	 * @param FP
	 *            False Positives
	 * @param FN
	 *            False Negatives
	 * @return The match results
	 */
	public static MatchResult analyseResults2D(Coordinate[] actualPoints, Coordinate[] predictedPoints,
			double dThreshold, List<Coordinate> TP, List<Coordinate> FP, List<Coordinate> FN)
	{
		return analyseResults2D(actualPoints, predictedPoints, dThreshold, TP, FP, FN, null);
	}

	/**
	 * Calculate the match results for the given actual and predicted points.
	 * Points that are within the distance threshold are identified as a match.
	 * The number of true positives, false positives and false negatives are calculated.
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 *            The distance threshold
	 * @param TP
	 *            True Positives
	 * @param FP
	 *            False Positives
	 * @param FN
	 *            False Negatives
	 * @param matches
	 *            The matched true positives (point1 = actual, point2 = predicted)
	 * @return The match results
	 */
	public static MatchResult analyseResults2D(Coordinate[] actualPoints, Coordinate[] predictedPoints,
			double dThreshold, List<Coordinate> TP, List<Coordinate> FP, List<Coordinate> FN, List<PointPair> matches)
	{
		dThreshold *= dThreshold; // We will use the squared distance

		int predictedPointsLength = (predictedPoints != null) ? predictedPoints.length : 0;
		int actualPointsLength = (actualPoints != null) ? actualPoints.length : 0;

		int n = predictedPointsLength;
		int tp = 0; // true positives (actual with matched predicted point)
		int fp = n; // false positives (actual with no matched predicted point)
		int fn = actualPointsLength; // false negatives (predicted point with no actual point)
		double rmsd = 0;

		// loop over the two arrays assigning the closest unassigned pair
		boolean[] resultAssignment = new boolean[n];
		boolean[] roiAssignment = new boolean[fn];
		ArrayList<Assignment> assignments = new ArrayList<Assignment>(n);

		if (FP != null)
		{
			FP.addAll(asList(predictedPoints));
			FN.addAll(asList(actualPoints));
		}

		if (predictedPointsLength == 0 || actualPointsLength == 0)
		{
			return new MatchResult(tp, fp, fn, rmsd);
		}

		// Pre-calculate all-vs-all distance matrix if it can fit in memory
		int size = predictedPointsLength * actualPointsLength;
		float[][] dMatrix = null;
		if (size < 200 * 200)
		{
			dMatrix = new float[predictedPointsLength][actualPointsLength];
			for (int predictedId = predictedPointsLength; predictedId-- > 0;)
			{
				float x = predictedPoints[predictedId].getPositionX();
				float y = predictedPoints[predictedId].getPositionY();
				for (int actualId = actualPointsLength; actualId-- > 0;)
				{
					double d2 = actualPoints[actualId].distance2(x, y);
					dMatrix[predictedId][actualId] = (float) d2;
				}
			}
		}

		do
		{
			assignments.clear();

			// Process each result
			for (int predictedId = n; predictedId-- > 0;)
			{
				if (resultAssignment[predictedId])
					continue; // Already assigned

				float x = predictedPoints[predictedId].getPositionX();
				float y = predictedPoints[predictedId].getPositionY();

				// Find closest ROI point
				float d2Min = Float.MAX_VALUE;
				float dMin = Float.MAX_VALUE;
				int targetId = -1;
				for (int actualId = actualPointsLength; actualId-- > 0;)
				{
					if (roiAssignment[actualId])
						continue; // Already assigned

					if (dMatrix != null)
					{
						if (dMatrix[predictedId][actualId] < d2Min)
						{
							d2Min = dMatrix[predictedId][actualId];
							targetId = actualId;
						}
					}
					else
					{
						Coordinate actualPoint = actualPoints[actualId];

						// Calculate in steps for increased speed (allows early exit)
						float dx = actualPoint.getPositionX() - x;
						if (dx < dMin)
						{
							float dy = actualPoint.getPositionY() - y;
							if (dy < dMin)
							{
								float d2 = dx * dx + dy * dy;
								if (d2 < d2Min)
								{
									d2Min = d2;
									dMin = (float) Math.sqrt(d2Min);
									targetId = actualId;
								}
							}
						}
					}
				}

				// Store closest ROI point
				if (targetId > -1)
				{
					assignments.add(new Assignment(targetId, predictedId, d2Min));
				}
			}

			// If there are assignments
			if (!assignments.isEmpty())
			{
				// Pick the closest pair to be assigned
				Collections.sort(assignments);

				Assignment closest = assignments.get(0);
				resultAssignment[closest.getPredictedId()] = true;
				roiAssignment[closest.getTargetId()] = true;

				// If within accuracy then classify as a match
				if (closest.getDistance() < dThreshold)
				{
					tp++;
					fn--;
					fp--;
					rmsd += closest.getDistance(); // Already a squared distance

					if (TP != null)
					{
						Coordinate predictedPoint = predictedPoints[closest.getPredictedId()];
						Coordinate actualPoint = actualPoints[closest.getTargetId()];
						TP.add(predictedPoint);
						FP.remove(predictedPoint);
						FN.remove(actualPoint);
						if (matches != null)
							matches.add(new PointPair(actualPoint, predictedPoint));
					}
				}
				else
				{
					// No more assignments within the distance threshold
					break;
				}
			}

		} while (!assignments.isEmpty());

		if (tp > 0)
			rmsd = Math.sqrt(rmsd / tp);
		return new MatchResult(tp, fp, fn, rmsd);
	}

	private static Collection<Coordinate> asList(Coordinate[] points)
	{
		if (points != null)
			return Arrays.asList(points);
		return new ArrayList<Coordinate>(0);
	}

	/**
	 * Calculate the match results for the given actual and predicted points.
	 * Points that are within the distance threshold are identified as a match.
	 * The number of true positives, false positives and false negatives are calculated.
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 *            The distance threshold
	 * @return The match results
	 */
	public static MatchResult analyseResults3D(Coordinate[] actualPoints, Coordinate[] predictedPoints,
			double dThreshold)
	{
		return analyseResults3D(actualPoints, predictedPoints, dThreshold, null, null, null, null);
	}

	/**
	 * Calculate the match results for the given actual and predicted points.
	 * Points that are within the distance threshold are identified as a match.
	 * The number of true positives, false positives and false negatives are calculated.
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 *            The distance threshold
	 * @param TP
	 *            True Positives
	 * @param FP
	 *            False Positives
	 * @param FN
	 *            False Negatives
	 * @return The match results
	 */
	public static MatchResult analyseResults3D(Coordinate[] actualPoints, Coordinate[] predictedPoints,
			double dThreshold, List<Coordinate> TP, List<Coordinate> FP, List<Coordinate> FN)
	{
		return analyseResults3D(actualPoints, predictedPoints, dThreshold, TP, FP, FN, null);
	}

	/**
	 * Calculate the match results for the given actual and predicted points.
	 * Points that are within the distance threshold are identified as a match.
	 * The number of true positives, false positives and false negatives are calculated.
	 * 
	 * @param actualPoints
	 * @param predictedPoints
	 * @param dThreshold
	 *            The distance threshold
	 * @param TP
	 *            True Positives
	 * @param FP
	 *            False Positives
	 * @param FN
	 *            False Negatives
	 * @param matches
	 *            The matched true positives (point1 = actual, point2 = predicted)
	 * @return The match results
	 */
	public static MatchResult analyseResults3D(Coordinate[] actualPoints, Coordinate[] predictedPoints,
			double dThreshold, List<Coordinate> TP, List<Coordinate> FP, List<Coordinate> FN, List<PointPair> matches)
	{
		dThreshold *= dThreshold; // We will use the squared distance

		int predictedPointsLength = (predictedPoints != null) ? predictedPoints.length : 0;
		int actualPointsLength = (actualPoints != null) ? actualPoints.length : 0;

		int n = predictedPointsLength;
		int tp = 0; // true positives (actual with matched predicted point)
		int fp = n; // false positives (actual with no matched predicted point)
		int fn = actualPointsLength; // false negatives (predicted point with no actual point)
		double rmsd = 0;

		// loop over the two arrays assigning the closest unassigned pair
		boolean[] resultAssignment = new boolean[n];
		boolean[] roiAssignment = new boolean[fn];
		ArrayList<Assignment> assignments = new ArrayList<Assignment>(n);

		if (FP != null)
		{
			FP.addAll(asList(predictedPoints));
			FN.addAll(asList(actualPoints));
		}

		if (predictedPointsLength == 0 || actualPointsLength == 0)
		{
			return new MatchResult(tp, fp, fn, rmsd);
		}

		// Pre-calculate all-vs-all distance matrix if it can fit in memory
		int size = predictedPointsLength * actualPointsLength;
		float[][] dMatrix = null;
		if (size < 200 * 200)
		{
			dMatrix = new float[predictedPointsLength][actualPointsLength];
			for (int predictedId = predictedPointsLength; predictedId-- > 0;)
			{
				float x = predictedPoints[predictedId].getPositionX();
				float y = predictedPoints[predictedId].getPositionY();
				float z = predictedPoints[predictedId].getPositionZ();
				for (int actualId = actualPointsLength; actualId-- > 0;)
				{
					double d2 = actualPoints[actualId].distance2(x, y, z);
					dMatrix[predictedId][actualId] = (float) d2;
				}
			}
		}

		do
		{
			assignments.clear();

			// Process each result
			for (int predictedId = n; predictedId-- > 0;)
			{
				if (resultAssignment[predictedId])
					continue; // Already assigned

				float x = predictedPoints[predictedId].getPositionX();
				float y = predictedPoints[predictedId].getPositionY();
				float z = predictedPoints[predictedId].getPositionZ();

				// Find closest ROI point
				float d2Min = Float.MAX_VALUE;
				float dMin = Float.MAX_VALUE;
				int targetId = -1;
				for (int actualId = actualPointsLength; actualId-- > 0;)
				{
					if (roiAssignment[actualId])
						continue; // Already assigned

					if (dMatrix != null)
					{
						if (dMatrix[predictedId][actualId] < d2Min)
						{
							d2Min = dMatrix[predictedId][actualId];
							targetId = actualId;
						}
					}
					else
					{
						Coordinate actualPoint = actualPoints[actualId];

						// Calculate in steps for increased speed (allows early exit)
						float dx = actualPoint.getPositionX() - x;
						if (dx < dMin)
						{
							float dy = actualPoint.getPositionY() - y;
							if (dy < dMin)
							{
								float dz = actualPoint.getPositionZ() - z;
								if (dz < dMin)
								{
									float d2 = dx * dx + dy * dy + dz * dz;
									if (d2 < d2Min)
									{
										d2Min = d2;
										dMin = (float) Math.sqrt(d2Min);
										targetId = actualId;
									}
								}
							}
						}
					}
				}

				// Store closest ROI point
				if (targetId > -1)
				{
					assignments.add(new Assignment(targetId, predictedId, d2Min));
				}
			}

			// If there are assignments
			if (!assignments.isEmpty())
			{
				// Pick the closest pair to be assigned
				Collections.sort(assignments);

				Assignment closest = assignments.get(0);
				resultAssignment[closest.getPredictedId()] = true;
				roiAssignment[closest.getTargetId()] = true;

				// If within accuracy then classify as a match
				if (closest.getDistance() < dThreshold)
				{
					tp++;
					fn--;
					fp--;
					rmsd += closest.getDistance();

					if (TP != null)
					{
						Coordinate predictedPoint = predictedPoints[closest.getPredictedId()];
						Coordinate actualPoint = actualPoints[closest.getTargetId()];
						TP.add(predictedPoint);
						FP.remove(predictedPoint);
						FN.remove(actualPoint);
						if (matches != null)
							matches.add(new PointPair(actualPoint, predictedPoint));
					}
				}
				else
				{
					// No more assignments within the distance threshold
					break;
				}
			}

		} while (!assignments.isEmpty());

		if (tp > 0)
			rmsd = Math.sqrt(rmsd / tp);
		return new MatchResult(tp, fp, fn, rmsd);
	}
}
