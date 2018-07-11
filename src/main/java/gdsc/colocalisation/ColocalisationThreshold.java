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
package gdsc.colocalisation;

import java.awt.Rectangle;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.process.ImageProcessor;

/**
 * Class that allow the threshold for colocalisation analysis to be calculated for two images. A regression is performed
 * between the two images. If the correlation is positive a search is performed to iteratively reduce two image
 * thresholds until all pixels below these thresholds produce a negative correlation.
 *
 * Based on the Colocalisation_Threshold plugin at
 * http://www.uhnres.utoronto.ca/facilities/wcif/imagej/colour_analysis.htm#6.3 Colocalisation Threshold
 */
public class ColocalisationThreshold
{
	private ImagePlus imp1;
	private ImagePlus imp2;
	private int roiIndex = 0;
	private int maxIterations = 30;
	private boolean includeNullPixels = false;
	private boolean includeSaturatedPixels = false;
	private boolean exhaustiveSearch = false;
	private ArrayList<ThresholdResult> results;
	private boolean correlated = false;
	private int threshold1;
	private int threshold2;
	private double m;
	private double b;
	private double rTotal;
	private double rBelowThreshold;
	private double rAboveThreshold;
	private double rThreshold = 0;
	private double convergenceTolerance = 0.001;
	private double searchTolerance = 0.05;
	private int ch1Max = 0;
	private int ch2Max = 0;
	private int ch1Min = Integer.MAX_VALUE;
	private int ch2Min = Integer.MAX_VALUE;

	/**
	 * Constructor.
	 *
	 * @param imp1
	 *            Image 1
	 * @param imp2
	 *            Image 2
	 */
	public ColocalisationThreshold(ImagePlus imp1, ImagePlus imp2)
	{
		init(imp1, imp2, 0);
	}

	/**
	 * Constructor.
	 *
	 * @param imp1
	 *            Image 1
	 * @param imp2
	 *            Image 2
	 * @param roiIndex
	 *            Contains the index of the image ROI that should be analysed (0,1,2)
	 */
	public ColocalisationThreshold(ImagePlus imp1, ImagePlus imp2, int roiIndex)
	{
		init(imp1, imp2, roiIndex);
	}

	private void init(ImagePlus imp1, ImagePlus imp2, int roiIndex)
	{
		if (imp1 == null || imp2 == null)
			throw new NullPointerException("Input images must not be null");
		if (imp1.getWidth() != imp2.getWidth() || imp1.getHeight() != imp2.getHeight())
			throw new RuntimeException("Input images must have the same dimensions");
		if (imp1.getStackSize() != imp2.getImageStackSize())
			throw new RuntimeException("Input images must have the same stack size");

		this.imp1 = imp1;
		this.imp2 = imp2;
		this.roiIndex = roiIndex;
	}

	/**
	 * Performs iterative correlation thresholding. Thresholds are lowered until all pixels below these thresholds
	 * produce a negative correlation.
	 *
	 * @return true if correlated
	 */
	public boolean correlate()
	{
		IJ.log("=-=-=-=-=-=-=");
		IJ.log("Performing correlation ...");

		// Set results to default
		threshold1 = 0;
		threshold2 = 0;
		rBelowThreshold = Double.NaN;
		m = 0;
		b = 0;
		correlated = false;

		int ch1;
		int ch2;
		int ch3;
		ch1Max = 0;
		ch2Max = 0;
		ch1Min = Integer.MAX_VALUE;
		ch2Min = Integer.MAX_VALUE;

		// Set up the ROI
		ImageProcessor ipMask = null;
		Rectangle roiRect = null;

		if (roiIndex != 0)
		{
			final ImagePlus roiImage = (roiIndex == 1) ? imp1 : imp2;
			final Roi roi = roiImage.getRoi();

			if (roi != null)
			{
				roiRect = roi.getBounds();

				// Use a mask for an irregular ROI
				if (roi.getType() != Roi.RECTANGLE)
					ipMask = roiImage.getMask();
			}
			else
				// Reset the choice for next time
				roiIndex = 0;
		}

		// Speed up the processing by extracting the pixels arrays for the ROI into a new stack.
		// This will allow the array to be iterated over directly.
		final int size = countPixels(roiRect, ipMask);
		final int[] i1 = new int[size];
		final int[] i2 = new int[size];
		extractPixels(roiRect, ipMask, i1, i2);

		// start regression
		final long startTime = System.currentTimeMillis();

		// A regression is required using all the non-zero pixels
		long ch1Sum = 0;
		long ch2Sum = 0;
		long ch3Sum = 0;
		int nZero = 0;

		// get means
		for (int i = i1.length; i-- > 0;)
		{
			ch1 = i1[i];
			ch2 = i2[i];

			if (ch1Max < ch1)
				ch1Max = ch1;
			if (ch1Min > ch1)
				ch1Min = ch1;
			if (ch2Max < ch2)
				ch2Max = ch2;
			if (ch2Min > ch2)
				ch2Min = ch2;

			ch3 = ch1 + ch2;
			ch1Sum += ch1;
			ch2Sum += ch2;
			ch3Sum += ch3;
			if (ch1 == 0 && ch2 == 0)
				nZero++;
		}

		final int n = (includeNullPixels) ? size : size - nZero;

		final double ch1Mean = ch1Sum / (double) n;
		final double ch2Mean = ch2Sum / (double) n;
		final double ch3Mean = ch3Sum / (double) n;

		// TODO - Add a check to ensure that the sum will not lose precision as the total aggregates.
		// This could be done by keeping a BigDecimal to store the overall sum. When the rolling total
		// reaches the a specified precision limit for a double then it should be added to the
		// BigDecimal and reset. The precision limit could be set using the value of the mean,
		// e.g. 1e10 times bigger than the mean.

		// Calculate variances
		double ch1mch1MeanSqSum = 0;
		double ch2mch2MeanSqSum = 0;
		double ch1mch2MeanSqSum = 0;
		double ch3mch3MeanSqSum = 0;
		for (int i = i1.length; i-- > 0;)
		{
			ch1 = i1[i];
			ch2 = i2[i];
			ch3 = ch1 + ch2;
			ch1mch1MeanSqSum += (ch1 - ch1Mean) * (ch1 - ch1Mean);
			ch2mch2MeanSqSum += (ch2 - ch2Mean) * (ch2 - ch2Mean);
			ch1mch2MeanSqSum += (ch1 - ch1Mean) * (ch2 - ch2Mean);
			ch3mch3MeanSqSum += (ch3 - ch3Mean) * (ch3 - ch3Mean);
		}

		rTotal = ch1mch2MeanSqSum / Math.sqrt(ch1mch1MeanSqSum * ch2mch2MeanSqSum);

		IJ.log("R = " + IJ.d2s(rTotal, 4));

		if (rTotal < searchTolerance || rTotal < rThreshold)
			// No correlation at all
			return correlationResult(false, "No correlation found.");

		// http://mathworld.wolfram.com/Covariance.html
		// ?2 = X2?(X)2
		// = E[X2]?(E[X])2
		// var (x+y) = var(x)+var(y)+2(covar(x,y));
		// 2(covar(x,y)) = var(x+y) - var(x)-var(y);

		final double ch1Var = ch1mch1MeanSqSum / (n - 1);
		final double ch2Var = ch2mch2MeanSqSum / (n - 1);
		final double ch3Var = ch3mch3MeanSqSum / (n - 1);
		final double ch1ch2covar = 0.5 * (ch3Var - (ch1Var + ch2Var));

		// do regression
		// See:Dissanaike and Wang
		// http://papers.ssrn.com/sol3/papers.cfm?abstract_id=407560

		final double denom = 2 * ch1ch2covar;
		final double num = ch2Var - ch1Var +
				Math.sqrt((ch2Var - ch1Var) * (ch2Var - ch1Var) + (4 * ch1ch2covar * ch1ch2covar));

		m = num / denom;
		b = ch2Mean - m * ch1Mean;

		IJ.log("Channel 2 = Channel 1 * " + IJ.d2s(m, 4) + ((b < 0) ? " " : " +") + IJ.d2s(b, 4));
		IJ.log("Channel 1 range " + ch1Min + " - " + ch1Max);
		IJ.log("Channel 2 range " + ch2Min + " - " + ch2Max);

		final double ch2MinCalc = ch1Min * m + b;
		final double ch2MaxCalc = ch1Max * m + b;
		IJ.log("Channel 2 calculated range " + IJ.d2s(ch2MinCalc, 0) + " - " + IJ.d2s(ch2MaxCalc, 0));

		// Set-up for convergence

		// The magnitude of b represents the difference of mean random overlap between channels
		// after correction for their correlation, i.e. difference in noise.
		// If the noise in channel 2 is higher than channel 1 (i.e. b is positive) then the channel 1
		// threshold can approach zero.

		int lowerThreshold = (int) Math.ceil((ch2Min - b) / m);
		int upperThreshold = (int) Math.floor((ch2Max - b) / m);

		IJ.log("Channel 1 interpolated range " + lowerThreshold + " - " + upperThreshold);
		if (lowerThreshold < ch1Min)
			lowerThreshold = 0;
		if (upperThreshold > ch1Max)
			upperThreshold = ch1Max;
		IJ.log("Channel 1 search range " + lowerThreshold + " - " + upperThreshold);

		final int currentThreshold = upperThreshold;

		if (rTotal == 1.0)
			// Perfect correlation. The default results are OK.
			IJ.log("Perfect correlation found (did you select the same input image for both channels?).");
		else if (upperThreshold - lowerThreshold < 1)
			IJ.log("No range to search for positive correlations.");
		else
		{
			boolean result;

			if (exhaustiveSearch)
				result = findThresholdExhaustive(i1, i2, lowerThreshold, upperThreshold, currentThreshold);
			else
				result = findThreshold(i1, i2, lowerThreshold, upperThreshold, currentThreshold);

			if (!result)
				return false;
		}

		final double seconds = (System.currentTimeMillis() - startTime) / 1000.0;

		return correlationResult(true, "Threshold calculation time = " + seconds);
	}

	/**
	 * Calculate correlation.
	 *
	 * @param sumX
	 *            the sum X
	 * @param sumXY
	 *            the sum XY
	 * @param sumXX
	 *            the sum XX
	 * @param sumYY
	 *            the sum YY
	 * @param sumY
	 *            the sum Y
	 * @param n
	 *            the n
	 * @return the correlation
	 */
	public static double calculateCorrelation(long sumX, long sumXY, long sumXX, long sumYY, long sumY, long n)
	{
		BigInteger nSumXY = BigInteger.valueOf(sumXY).multiply(BigInteger.valueOf(n));
		BigInteger nSumXX = BigInteger.valueOf(sumXX).multiply(BigInteger.valueOf(n));
		BigInteger nSumYY = BigInteger.valueOf(sumYY).multiply(BigInteger.valueOf(n));

		nSumXY = nSumXY.subtract(BigInteger.valueOf(sumX).multiply(BigInteger.valueOf(sumY)));
		nSumXX = nSumXX.subtract(BigInteger.valueOf(sumX).multiply(BigInteger.valueOf(sumX)));
		nSumYY = nSumYY.subtract(BigInteger.valueOf(sumY).multiply(BigInteger.valueOf(sumY)));

		final BigInteger product = nSumXX.multiply(nSumYY);

		return nSumXY.doubleValue() / Math.sqrt(product.doubleValue());

		//		double pearsons1 = n * sumXY - (sumX * sumY);
		//		double pearsons2 = n * sumXX - (sumX * sumX);
		//		double pearsons3 = n * sumYY - (sumY * sumY);
		//		return pearsons1 / (Math.sqrt(pearsons2 * pearsons3));
	}

	private int countPixels(Rectangle roiRect, ImageProcessor ipMask)
	{
		int count = 0;

		final int nslices = imp1.getStackSize();
		final int width = imp1.getWidth();
		final int height = imp1.getHeight();

		int rwidth, rheight;

		if (roiRect == null)
		{
			rwidth = width;
			rheight = height;
		}
		else
		{
			rwidth = roiRect.width;
			rheight = roiRect.height;
		}

		for (int y = rheight; y-- > 0;)
			for (int x = rwidth; x-- > 0;)
				if (ipMask == null || ipMask.get(x, y) != 0)
					count++;

		return count * nslices;
	}

	private void extractPixels(Rectangle roiRect, ImageProcessor ipMask, int[] i1, int[] i2)
	{
		int count = 0;

		final int nslices = imp1.getStackSize();
		final int width = imp1.getWidth();
		final int height = imp1.getHeight();

		int xOffset, yOffset, rwidth, rheight;

		if (roiRect == null)
		{
			xOffset = 0;
			yOffset = 0;
			rwidth = width;
			rheight = height;
		}
		else
		{
			xOffset = roiRect.x;
			yOffset = roiRect.y;
			rwidth = roiRect.width;
			rheight = roiRect.height;
		}

		final ImageStack img1 = imp1.getStack();
		final ImageStack img2 = imp2.getStack();

		for (int s = 1; s <= nslices; s++)
		{
			final ImageProcessor ip1 = img1.getProcessor(s);
			final ImageProcessor ip2 = img2.getProcessor(s);

			for (int y = rheight; y-- > 0;)
				for (int x = rwidth; x-- > 0;)
					if (ipMask == null || ipMask.get(x, y) != 0)
					{
						i1[count] = ip1.getPixel(x + xOffset, y + yOffset);
						i2[count] = ip2.getPixel(x + xOffset, y + yOffset);
						count++;
					}
		}

	}

	private boolean findThreshold(int[] i1, int[] i2, int lowerThreshold, int upperThreshold, int currentThreshold)
	{
		int ch1, ch2;

		// Create the results and add the global correlation - this will be the fallback result
		results = new ArrayList<>(maxIterations);
		results.add(new ThresholdResult(0, 0, rTotal, Double.NaN));
		results.add(new ThresholdResult(ch1Max, ch2Max, Double.NaN, rTotal));

		// We already have the complete correlation so move the threshold down for the first step.
		int newThreshold = decreaseThreshold(currentThreshold, lowerThreshold);

		int iteration = 1;
		while (iteration <= maxIterations)
		{
			if (IJ.escapePressed())
			{
				IJ.beep();
				return correlationResult(false, "IJ plugin cancelled");
			}

			// Check if the threshold has been updated.
			if (currentThreshold == newThreshold || isRepeatThreshold(results, newThreshold))
			{
				IJ.log("Convergence at threshold " + newThreshold);
				break;
			}

			currentThreshold = newThreshold;

			// Set new thresholds
			threshold1 = currentThreshold;
			threshold2 = (int) Math.round((threshold1 * m) + b);

			// A = both channels above the threshold
			// B = either channel below the threshold

			long ch1SumA = 0, ch2SumA = 0;
			int nZeroA = 0, nA = 0;
			long ch1SumB = 0, ch2SumB = 0;
			int nZeroB = 0, nB = 0;

			// Get means
			for (int i = i1.length; i-- > 0;)
			{
				ch1 = i1[i];
				ch2 = i2[i];

				if ((ch1 < (threshold1)) || (ch2 < (threshold2)))
				{
					if (ch1 == 0 && ch2 == 0)
						nZeroB++;
					else
					{
						ch1SumB += ch1;
						ch2SumB += ch2;
					}
					nB++;
				}
				else
				{
					if (ch1 == 0 && ch2 == 0)
						nZeroA++;
					else
					{
						ch1SumA += ch1;
						ch2SumA += ch2;
					}
					nA++;
				}
			}

			if (!includeNullPixels)
			{
				nA -= nZeroA;
				nB -= nZeroB;
			}

			final double ch1MeanA = ch1SumA / (double) nA;
			final double ch2MeanA = ch2SumA / (double) nA;
			final double ch1MeanB = ch1SumB / (double) nB;
			final double ch2MeanB = ch2SumB / (double) nB;

			// Calculate correlation
			double ch1mch1MeanSqSumA = 0;
			double ch2mch2MeanSqSumA = 0;
			double ch1mch2MeanSqSumA = 0;
			double ch1mch1MeanSqSumB = 0;
			double ch2mch2MeanSqSumB = 0;
			double ch1mch2MeanSqSumB = 0;

			for (int i = i1.length; i-- > 0;)
			{
				ch1 = i1[i];
				ch2 = i2[i];
				if ((ch1 < (threshold1)) || (ch2 < (threshold2)))
				{
					ch1mch1MeanSqSumB += (ch1 - ch1MeanB) * (ch1 - ch1MeanB);
					ch2mch2MeanSqSumB += (ch2 - ch2MeanB) * (ch2 - ch2MeanB);
					ch1mch2MeanSqSumB += (ch1 - ch1MeanB) * (ch2 - ch2MeanB);
				}
				else
				{
					ch1mch1MeanSqSumA += (ch1 - ch1MeanA) * (ch1 - ch1MeanA);
					ch2mch2MeanSqSumA += (ch2 - ch2MeanA) * (ch2 - ch2MeanA);
					ch1mch2MeanSqSumA += (ch1 - ch1MeanA) * (ch2 - ch2MeanA);
				}
			}

			final double rA = ch1mch2MeanSqSumA / Math.sqrt(ch1mch1MeanSqSumA * ch2mch2MeanSqSumA);
			final double rB = ch1mch2MeanSqSumB / Math.sqrt(ch1mch1MeanSqSumB * ch2mch2MeanSqSumB);

			// if r is not a number then set divide by zero to be true
			boolean divByZero;
			if (Double.isNaN(rB) || nB == 0)
				divByZero = true;
			else
			{
				divByZero = false;
				results.add(new ThresholdResult(threshold1, threshold2, rA, rB));
			}

			IJ.log(iteration + ", c1(threshold)=" + threshold1 + ", c2(threshold)=" + threshold2 + " : r=" +
					IJ.d2s(rA, 4) + ", r2=" + ((divByZero) ? "NaN" : IJ.d2s(rB, 4)));

			// Change threshold max
			// Ensure that the correlation above the thresholds is positive and below is negative.
			// If there are no pixels below the threshold then a divide by zero error occurs.
			if (divByZero || rB < rThreshold) // || r < r2)
			{
				lowerThreshold = currentThreshold;
				newThreshold = increaseThreshold(currentThreshold, upperThreshold);
			}
			else
			{
				upperThreshold = currentThreshold;
				newThreshold = decreaseThreshold(currentThreshold, lowerThreshold);
			}

			// If our r is close to our level of tolerance then set threshold has been found.
			// Only true if the correlation above the threshold is positive.
			if (Math.abs(rB - rThreshold) < Math.abs(convergenceTolerance) && rA > 0)
			{
				IJ.log("Correlation below tolerance ... quitting");
				break;
			}

			iteration++;
		}

		if (iteration > maxIterations)
			IJ.log("Maximum iterations reached");

		findResultThreshold();

		return true;
	}

	private boolean findThresholdExhaustive(int[] i1, int[] i2, int lowerThreshold, int upperThreshold,
			int currentThreshold)
	{
		int ch1, ch2;

		// Create the results and add the global correlation - this will be the fallback result
		results = new ArrayList<>(maxIterations);
		results.add(new ThresholdResult(0, 0, rTotal, Double.NaN));
		results.add(new ThresholdResult(ch1Max, ch2Max, Double.NaN, rTotal));

		// Enumerate over the threshold range
		final int interval = (int) Math.ceil((1.0 * (upperThreshold - lowerThreshold)) / maxIterations);
		int iteration = 1;

		for (currentThreshold = lowerThreshold; currentThreshold <= upperThreshold; currentThreshold += interval)
		{
			if (IJ.escapePressed())
			{
				IJ.beep();
				return correlationResult(false, "IJ plugin cancelled");
			}

			// Set new thresholds
			threshold1 = currentThreshold;
			threshold2 = (int) Math.round((threshold1 * m) + b);

			// A = both channels above the threshold
			// B = either channel below the threshold

			long ch1SumA = 0, ch2SumA = 0;
			int nZeroA = 0, nA = 0;
			long ch1SumB = 0, ch2SumB = 0;
			int nZeroB = 0, nB = 0;

			// Get means
			for (int i = i1.length; i-- > 0;)
			{
				ch1 = i1[i];
				ch2 = i2[i];

				if ((ch1 < (threshold1)) || (ch2 < (threshold2)))
				{
					if (ch1 == 0 && ch2 == 0)
						nZeroB++;
					else
					{
						ch1SumB += ch1;
						ch2SumB += ch2;
					}
					nB++;
				}
				else
				{
					if (ch1 == 0 && ch2 == 0)
						nZeroA++;
					else
					{
						ch1SumA += ch1;
						ch2SumA += ch2;
					}
					nA++;
				}
			}

			if (!includeNullPixels)
			{
				nA -= nZeroA;
				nB -= nZeroB;
			}

			final double ch1MeanA = ch1SumA / (double) nA;
			final double ch2MeanA = ch2SumA / (double) nA;
			final double ch1MeanB = ch1SumB / (double) nB;
			final double ch2MeanB = ch2SumB / (double) nB;

			// Calculate correlation
			double ch1mch1MeanSqSumA = 0;
			double ch2mch2MeanSqSumA = 0;
			double ch1mch2MeanSqSumA = 0;
			double ch1mch1MeanSqSumB = 0;
			double ch2mch2MeanSqSumB = 0;
			double ch1mch2MeanSqSumB = 0;

			for (int i = i1.length; i-- > 0;)
			{
				ch1 = i1[i];
				ch2 = i2[i];
				if ((ch1 < (threshold1)) || (ch2 < (threshold2)))
				{
					ch1mch1MeanSqSumB += (ch1 - ch1MeanB) * (ch1 - ch1MeanB);
					ch2mch2MeanSqSumB += (ch2 - ch2MeanB) * (ch2 - ch2MeanB);
					ch1mch2MeanSqSumB += (ch1 - ch1MeanB) * (ch2 - ch2MeanB);
				}
				else
				{
					ch1mch1MeanSqSumA += (ch1 - ch1MeanA) * (ch1 - ch1MeanA);
					ch2mch2MeanSqSumA += (ch2 - ch2MeanA) * (ch2 - ch2MeanA);
					ch1mch2MeanSqSumA += (ch1 - ch1MeanA) * (ch2 - ch2MeanA);
				}
			}

			final double rA = ch1mch2MeanSqSumA / Math.sqrt(ch1mch1MeanSqSumA * ch2mch2MeanSqSumA);
			final double rB = ch1mch2MeanSqSumB / Math.sqrt(ch1mch1MeanSqSumB * ch2mch2MeanSqSumB);

			// Only add a results if there is a correlation below the threshold
			if (!(Double.isNaN(rB) || nB == 0))
				results.add(new ThresholdResult(threshold1, threshold2, rA, rB));

			IJ.log(iteration + ", c1(threshold)=" + threshold1 + ", c2(threshold)=" + threshold2 + " : r=" +
					IJ.d2s(rA, 4) + ", r2=" + IJ.d2s(rB, 4));

			if (Double.isNaN(rA))
				break;

			iteration++;
		}

		findResultThreshold();

		return true;
	}

	/**
	 * Find the first threshold with a correlation within the limit of the target correlation.
	 */
	private void findResultThreshold()
	{
		// Results are sorted from high to low threshold.
		Collections.sort(results);

		rBelowThreshold = 2;

		for (final ThresholdResult result : results)
			// Look for results where the correlation above the threshold is positive
			// and the correlation below the threshold (RltT) is closer to the target R-threshold.
			if (result.r1 > rThreshold &&
					//result.r2 < rThreshold &&
					//Math.abs(result.r2 - rThreshold) < Math.abs(convergenceTolerance) &&
					Math.abs(result.r2 - rThreshold) < Math.abs(rBelowThreshold - rThreshold))
			{
				rAboveThreshold = result.r1;
				rBelowThreshold = result.r2;
				threshold1 = result.threshold1;
				threshold2 = result.threshold2;

				// If the RltT is within the convergence limit then this is the final result.
				//if (Math.abs(result.r2 - rThreshold) < Math.abs(convergenceTolerance))
				//{
				//	break;
				//}
			}

		// Set the values in the case that no correlations satisfy the criteria.
		if (rBelowThreshold == 2)
		{
			final ThresholdResult result = results.get(results.size() - 1);
			rAboveThreshold = result.r1;
			rBelowThreshold = result.r2;
			threshold1 = result.threshold1;
			threshold2 = result.threshold2;
		}
	}

	private static boolean isRepeatThreshold(ArrayList<ThresholdResult> results, int newThreshold)
	{
		for (final ThresholdResult result : results)
			if (result.threshold1 == newThreshold)
				return true;
		return false;
	}

	private boolean correlationResult(boolean result, String message)
	{
		IJ.log(message);
		IJ.log("-=-=-=-=-=-=-");
		correlated = result;
		return result;
	}

	private static int decreaseThreshold(int current, int min)
	{
		return current - (int) Math.ceil((current - min) / 2.0);
	}

	private static int increaseThreshold(int current, int max)
	{
		return current + (int) Math.ceil((max - current) / 2.0);
	}

	/**
	 * Sets the max iterations.
	 *
	 * @param maxIterations
	 *            the new max iterations
	 */
	public void setMaxIterations(int maxIterations)
	{
		this.maxIterations = maxIterations;
	}

	/**
	 * Gets the max iterations.
	 *
	 * @return the max iterations
	 */
	public int getMaxIterations()
	{
		return maxIterations;
	}

	/**
	 * Sets the include null pixels.
	 *
	 * @param includeNullPixels
	 *            the new include null pixels
	 */
	public void setIncludeNullPixels(boolean includeNullPixels)
	{
		this.includeNullPixels = includeNullPixels;
	}

	/**
	 * Checks if is include null pixels.
	 *
	 * @return true, if is include null pixels
	 */
	public boolean isIncludeNullPixels()
	{
		return includeNullPixels;
	}

	/**
	 * Sets the include saturated pixels.
	 *
	 * @param includeSaturatedPixels
	 *            the new include saturated pixels
	 */
	public void setIncludeSaturatedPixels(boolean includeSaturatedPixels)
	{
		this.includeSaturatedPixels = includeSaturatedPixels;
	}

	/**
	 * Checks if is include saturated pixels.
	 *
	 * @return true, if is include saturated pixels
	 */
	public boolean isIncludeSaturatedPixels()
	{
		return includeSaturatedPixels;
	}

	/**
	 * Sets the exhaustive search.
	 *
	 * @param exhaustiveSearch
	 *            the new exhaustive search
	 */
	public void setExhaustiveSearch(boolean exhaustiveSearch)
	{
		this.exhaustiveSearch = exhaustiveSearch;
	}

	/**
	 * Checks if is exhaustive search.
	 *
	 * @return true, if is exhaustive search
	 */
	public boolean isExhaustiveSearch()
	{
		return exhaustiveSearch;
	}

	/**
	 * Sets the results.
	 *
	 * @param results
	 *            the new results
	 */
	public void setResults(ArrayList<ThresholdResult> results)
	{
		this.results = results;
	}

	/**
	 * Gets the results.
	 *
	 * @return the results
	 */
	public ArrayList<ThresholdResult> getResults()
	{
		return results;
	}

	/**
	 * Checks if is correlated.
	 *
	 * @return true, if is correlated
	 */
	public boolean isCorrelated()
	{
		return correlated;
	}

	/**
	 * Gets the threshold 1.
	 *
	 * @return the threshold 1
	 */
	public int getThreshold1()
	{
		return (correlated) ? threshold1 : 0;
	}

	/**
	 * Gets the threshold 2.
	 *
	 * @return the threshold 2
	 */
	public int getThreshold2()
	{
		return (correlated) ? threshold2 : 0;
	}

	/**
	 * Gets the m from y = mx+b.
	 *
	 * @return the m
	 */
	public double getM()
	{
		return m;
	}

	/**
	 * Gets the b from y = mx+b.
	 *
	 * @return the b
	 */
	public double getB()
	{
		return b;
	}

	/**
	 * Gets the correlation total.
	 *
	 * @return the correlation total
	 */
	public double getRTotal()
	{
		return rTotal;
	}

	/**
	 * Gets the correlation below threshold.
	 *
	 * @return the correlation below threshold
	 */
	public double getRBelowThreshold()
	{
		return (correlated) ? rBelowThreshold : Double.NaN;
	}

	/**
	 * Gets the correlation above threshold.
	 *
	 * @return the correlation above threshold
	 */
	public double getRAboveThreshold()
	{
		return (correlated) ? rAboveThreshold : Double.NaN;
	}

	/**
	 * Set the limit for the correlation below the threshold. The search will stop when the correlation for the pixels
	 * below threshold is with the convergence tolerance distance of this R, i.e. is R = R-threshold +/- tolerance.
	 *
	 * @param r
	 *            the new correlation threshold
	 */
	public void setRThreshold(double r)
	{
		this.rThreshold = r;
	}

	/**
	 * Gets the correlation threshold.
	 *
	 * @return the correlation threshold
	 */
	public double getRThreshold()
	{
		return rThreshold;
	}

	/**
	 * Set the tolerance for convergence. The search will stop when the correlation for the pixels below threshold is
	 * with the convergence tolerance distance of this R, i.e. is R = R-threshold +/- tolerance.
	 *
	 * @param tolerance
	 *            the new convergence tolerance
	 */
	public void setConvergenceTolerance(double tolerance)
	{
		this.convergenceTolerance = tolerance;
	}

	/**
	 * Gets the convergence tolerance.
	 *
	 * @return the convergence tolerance
	 */
	public double getConvergenceTolerance()
	{
		return convergenceTolerance;
	}

	/**
	 * Gets the ch 1 max.
	 *
	 * @return the ch 1 max
	 */
	public int getCh1Max()
	{
		return ch1Max;
	}

	/**
	 * Gets the ch 2 max.
	 *
	 * @return the ch 2 max
	 */
	public int getCh2Max()
	{
		return ch2Max;
	}

	/**
	 * Gets the ch 1 min.
	 *
	 * @return the ch 1 min
	 */
	public int getCh1Min()
	{
		return ch1Min;
	}

	/**
	 * Gets the ch 2 min.
	 *
	 * @return the ch 2 min
	 */
	public int getCh2Min()
	{
		return ch2Min;
	}

	/**
	 * Set the tolerance for performing a search. The search will not start if the total correlation for the pixels
	 * below this threshold.
	 *
	 * @param searchTolerance
	 *            the new search tolerance
	 */
	public void setSearchTolerance(double searchTolerance)
	{
		this.searchTolerance = searchTolerance;
	}

	/**
	 * Gets the search tolerance.
	 *
	 * @return the search tolerance
	 */
	public double getSearchTolerance()
	{
		return searchTolerance;
	}

	/**
	 * Store the results of the correlation for a specified threshold.
	 */
	public class ThresholdResult implements Comparable<ThresholdResult>
	{
		/** The threshold 1. */
		public int threshold1;

		/** The threshold 2. */
		public int threshold2;

		/** The correlation 1. */
		public double r1;

		/** The correlation 2. */
		public double r2;

		/**
		 * Instantiates a new threshold result.
		 *
		 * @param threshold1
		 *            the threshold 1
		 * @param threshold2
		 *            the threshold 2
		 * @param r
		 *            the correlation 1
		 * @param r2
		 *            the correlation 2
		 */
		public ThresholdResult(int threshold1, int threshold2, double r, double r2)
		{
			this.threshold1 = threshold1;
			this.threshold2 = threshold2;
			this.r1 = r;
			this.r2 = r2;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		@Override
		public int compareTo(ThresholdResult otherResult) throws ClassCastException
		{
			return otherResult.threshold1 - this.threshold1;
		}
	}
}
