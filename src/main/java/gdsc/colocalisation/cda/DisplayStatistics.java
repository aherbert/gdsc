package gdsc.colocalisation.cda;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 *
 * This class is based on the original CDA_Plugin developed by 
 * Maria Osorio-Reich:
 * http://imagejdocu.tudor.lu/doku.php?id=plugin:analysis:confined_displacement_algorithm_determines_true_and_random_colocalization_:start
 *---------------------------------------------------------------------------*/

import ij.gui.PlotWindow;
import ij.process.ImageStatistics;

import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Wraps the plot window of a PlotResults object. Adds a panel to the bottom containing the probability
 * statistics.
 */
public class DisplayStatistics
{
	private static final String meanTitle = "Mean: ";
	private static final String stdDevTitle = "Std Dev: ";
	private static final String d0Title = "(d=0):";
	
	private Panel statisticsPanel;
	private String title;
	private String prefix;
	private Label meanLabel;
	private Label stdDevLabel;
	private Label limitsLabel;
	private Label significanceLabel;
	private Label d0ValueLabel;
	private PlotResults plotResults;
	private double d0Value;
	private ImageStatistics statistics;
	private NumberFormat formatter;
	private PlotWindow plotWindow;

	public double getValue()
	{
		return d0Value;
	}

	public double getAverage()
	{
		return statistics.mean;
	}

	public double getStdDev()
	{
		return statistics.stdDev;
	}

	public double getLowerLimit()
	{
		return plotResults.getProbabilityLimits()[0];
	}

	public double getUpperLimit()
	{
		return plotResults.getProbabilityLimits()[1];
	}

	public void close()
	{
		if (plotWindow != null && plotWindow.isShowing())
		{
			plotWindow.close();
			plotWindow = null;
		}
	}

	public DisplayStatistics(String title, String prefix)
	{
		this.title = title;
		this.prefix = prefix;
	}
	
	public void setData(PlotResults histogram, ImageStatistics statistics, double d0Value)
	{
		this.statistics = statistics;
		this.plotResults = histogram;
		this.d0Value = d0Value;
	}
	
	public void draw()
	{
		if (statistics == null || plotResults == null || createFrame())
			return;
		
		// Update the plot
		setValues();
		
		plotWindow.drawPlot(plotResults.getPlot());
		plotWindow.pack();
		plotWindow.invalidate();
	}

	private boolean createFrame()
	{
		if (plotWindow != null && plotWindow.isShowing())
			return false;
		
		// Build a panel containing statistics about the plot
		formatter = new DecimalFormat("#0.0000");

		statisticsPanel = new Panel();
		statisticsPanel.setLayout(new GridLayout(4, 2));

		meanLabel = new Label();
		stdDevLabel = new Label();
		limitsLabel = new Label();
		d0ValueLabel = new Label();
		significanceLabel = new Label();

		setValues();
		
		statisticsPanel.add(meanLabel);
		statisticsPanel.add(stdDevLabel);
		statisticsPanel.add(limitsLabel);
		statisticsPanel.add(d0ValueLabel);
		statisticsPanel.add(significanceLabel);

		plotWindow = plotResults.getPlot().show();
		plotWindow.setTitle(title);
		
		// Add the panel to the plot
		plotWindow.add(statisticsPanel);
		plotWindow.pack();
		plotWindow.invalidate();
		
		return true;
	}

	private void setValues()
	{
		meanLabel.setText(meanTitle.concat(String.valueOf(formatter.format(statistics.mean))));
		stdDevLabel.setText(stdDevTitle.concat(String.valueOf(formatter
				.format(statistics.stdDev))));
		limitsLabel.setText(prefix.concat("(p<" + plotResults.getPValue() + "):").concat(
				String.valueOf(formatter.format(plotResults.getProbabilityLimits()[0])).concat("|")
						.concat(String.valueOf(formatter.format(plotResults.getProbabilityLimits()[1])))));
		d0ValueLabel.setText(prefix.concat(d0Title).concat(
				String.valueOf(formatter.format(d0Value))));
		significanceLabel.setText(prefix.concat(plotResults.getSignificanceTest()));
	}
	
	public PlotResults getPlotResults()
	{
		return plotResults;
	}

	/**
	 * @return the plotWindow
	 */
	public PlotWindow getPlotWindow()
	{
		return plotWindow;
	}
}
