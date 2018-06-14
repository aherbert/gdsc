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
package gdsc.colocalisation.cda;

import java.awt.Color;

import ij.gui.Plot;

/**
 * Provides functionality to plot a histogram of sampleData and then determine if a value is
 * significant using a specified p-value, i.e. it lies outside the upper/lower tails of the histogram sampleData.
 * <p>
 * This class is based on the original CDA_Plugin developed by Maria Osorio-Reich:
 * <a href=
 * "http://imagejdocu.tudor.lu/doku.php?id=plugin:analysis:confined_displacement_algorithm_determines_true_and_random_colocalization_:start">Confined
 * Displacement Algorithm Determines True and Random Colocalization</a>
 */
public class PlotResults
{
	private Plot plot;
	private String plotTitle = "";
	private String plotYTitle = "";
	private String plotXTitle = "";
	private double[] probabilityLimits = null;
	private String significanceTest = "";
	private double[] normalisedHistogram;
	private double[] sampleData;
	private double[] histogramX;
	private int[] histogramY;
	private Color color = Color.blue;
	private double sampleValue;
	private double pValue = 0.05;

	public PlotResults(double sampleValue, int bins, double[] sampleData, Color color)
	{
		createHistogram(sampleData, bins);

		this.sampleValue = sampleValue;
		this.color = color;
	}

	private void createHistogram(double[] sampleData, int bins)
	{
		this.sampleData = sampleData;
		histogramX = new double[bins];
		histogramY = new int[bins];

		// Get the range
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		for (int i = 0; i < sampleData.length; i++)
		{
			if (min > sampleData[i])
				min = sampleData[i];
			if (max < sampleData[i])
				max = sampleData[i];
		}

		double interval = (max - min) / bins;

		// Set the bins
		for (int i = 0; i < bins; i++)
		{
			histogramX[i] = min + i * interval;
		}

		// Count the frequency
		for (int i = 0; i < sampleData.length; i++)
		{
			int bin = (int) ((sampleData[i] - min) / interval);
			if (bin >= bins)
				bin = bins - 1;
			if (bin < 0)
				bin = 0;
			histogramY[bin]++;
		}
	}

	public void calculate(double pValue)
	{
		setPValue(pValue);
		calculate();
	}

	public void calculate()
	{
		probabilityLimits = getProbabilityLimits(sampleData);
		significanceTest = testSignificance(probabilityLimits, sampleValue);
		normalisedHistogram = normaliseHistogram(histogramY);
		plot = null;
	}

	private void createPlot()
	{
		double[] xValues = createHistogramAxis(histogramX);
		double[] yValues = createHistogramValues(normalisedHistogram);

		// Set the upper limit for the plot
		double maxHistogram = 0;
		for (double d : normalisedHistogram)
			if (maxHistogram < d)
				maxHistogram = d;
		maxHistogram *= 1.05;

		plot = new Plot(plotTitle, plotXTitle, plotYTitle, xValues, yValues,
				Plot.X_NUMBERS + Plot.Y_NUMBERS + Plot.X_TICKS + Plot.Y_TICKS);

		// Ensure the horizontal scale goes from 0 to 1 but add at least 0.05 to the limits.
		double xMin = Math.min(xValues[0] - 0.05, Math.min(sampleValue - 0.05, 0));
		double xMax = Math.max(xValues[xValues.length - 1] + 0.05, Math.max(sampleValue + 0.05, 1));
		plot.setLimits(xMin, xMax, 0, maxHistogram * 1.05);
		plot.setLineWidth(1);

		plot.setColor(color);
		plot.draw();

		// Draw the significance lines and add top points
		plot.drawLine(probabilityLimits[0], 0, probabilityLimits[0], maxHistogram);
		plot.drawLine(probabilityLimits[1], 0, probabilityLimits[1], maxHistogram);

		// Draw lines for the lower and upper probability limits 
		double[][] markers = new double[2][2];
		markers[0][0] = probabilityLimits[0];
		markers[0][1] = probabilityLimits[1];
		markers[1][0] = maxHistogram;
		markers[1][1] = maxHistogram;
		plot.addPoints(markers[0], markers[1], 4);

		// Draw line for the data value 
		double[][] valueMarker = new double[2][1];
		valueMarker[0][0] = sampleValue;
		valueMarker[1][0] = maxHistogram;
		plot.setColor(Color.magenta);
		plot.drawLine(sampleValue, 0, sampleValue, maxHistogram);
		plot.addPoints(valueMarker[0], valueMarker[1], 0);
	}

	public void setXTitle(String title)
	{
		plotXTitle = title;
	}

	public void setYTitle(String title)
	{
		plotYTitle = title;
	}

	public void setTitle(String title)
	{
		plotTitle = title;
	}

	public Plot getPlot()
	{
		if (plot == null)
		{
			createPlot();
		}
		return plot;
	}

	public double[] getProbabilityLimits()
	{
		return probabilityLimits;
	}

	public String getSignificanceTest()
	{
		return significanceTest;
	}

	private double[] createHistogramAxis(double[] histogramX)
	{
		double[] axis = new double[histogramX.length * 2 + 2];
		int index = 0;
		for (int i = 0; i < histogramX.length; ++i)
		{
			axis[index++] = histogramX[i];
			axis[index++] = histogramX[i];
		}
		double dx = (histogramX[1] - histogramX[0]);
		axis[index++] = histogramX[histogramX.length - 1] + dx;
		axis[index++] = histogramX[histogramX.length - 1] + dx;
		return axis;
	}

	private double[] createHistogramValues(double[] histogramY)
	{
		double[] axis = new double[histogramY.length * 2 + 2];

		int index = 0;
		axis[index++] = 0;
		for (int i = 0; i < histogramY.length; ++i)
		{
			axis[index++] = histogramY[i];
			axis[index++] = histogramY[i];
		}
		axis[index++] = 0;
		return axis;
	}

	private int getTotal(int[] histogram)
	{
		int total = 0;
		for (int i : histogram)
			total += i;
		return total;
	}

	private double[] normaliseHistogram(int[] histogram)
	{
		int total = getTotal(histogram);

		double[] normalised = new double[histogram.length];

		for (int j = 0; j < histogram.length; ++j)
			normalised[j] = ((double) histogram[j] / total);

		return normalised;
	}

	private double[] getProbabilityLimits(double[] p)
	{
		double[] limits = new double[2];
		int pCut = (int) Math.ceil(p.length * pValue);
		limits[0] = p[pCut];
		limits[1] = p[(p.length - pCut - 1)];
		return limits;
	}

	private String testSignificance(double[] limits, double sampleValue)
	{
		String outputString = "";
		if (sampleValue >= limits[1])
		{
			outputString = " Value is significant! (colocalised)";
		}
		else if (sampleValue <= limits[0])
		{
			outputString = " Value is significant! (not colocalised)";
		}
		else
		{
			outputString = " Value is NOT significant! ";
		}
		return outputString;
	}

	/**
	 * @param pValue
	 *            the pValue to set
	 */
	public void setPValue(double pValue)
	{
		if (pValue >= 0 && pValue <= 1)
		{
			this.pValue = pValue;
		}
		else
		{
			throw new IllegalArgumentException("p-Value must be between 0 and 1: " + pValue);
		}
	}

	/**
	 * @return the pValue
	 */
	public double getPValue()
	{
		return pValue;
	}
}
