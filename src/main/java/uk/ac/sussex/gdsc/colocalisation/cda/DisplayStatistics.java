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

package uk.ac.sussex.gdsc.colocalisation.cda;

import ij.gui.PlotWindow;
import ij.process.ImageStatistics;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.Panel;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Wraps the plot window of a PlotResults object. Adds a panel to the bottom containing the
 * probability statistics.
 *
 * <p>This class is based on the original CDA_Plugin developed by Maria Osorio-Reich: <a href=
 * "http://imagejdocu.tudor.lu/doku.php?id=plugin:analysis:confined_displacement_algorithm_determines_true_and_random_colocalization_:start">Confined
 * Displacement Algorithm Determines True and Random Colocalization</a>
 */
public class DisplayStatistics {
  private static final String MEAN_TITLE = "Mean: ";
  private static final String STD_DEV_TITLE = "Std Dev: ";
  private static final String D0_TITLE = "(d=0):";

  private final String title;
  private final String prefix;
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

  /**
   * Gets the value.
   *
   * @return the value
   */
  public double getValue() {
    return d0Value;
  }

  /**
   * Gets the average.
   *
   * @return the average
   */
  public double getAverage() {
    return statistics.mean;
  }

  /**
   * Gets the std dev.
   *
   * @return the std dev
   */
  public double getStdDev() {
    return statistics.stdDev;
  }

  /**
   * Gets the lower limit.
   *
   * @return the lower limit
   */
  public double getLowerLimit() {
    return plotResults.getProbabilityLimits()[0];
  }

  /**
   * Gets the upper limit.
   *
   * @return the upper limit
   */
  public double getUpperLimit() {
    return plotResults.getProbabilityLimits()[1];
  }

  /**
   * Close the plot.
   */
  public void close() {
    if (plotWindow != null && plotWindow.isShowing()) {
      plotWindow.close();
      plotWindow = null;
    }
  }

  /**
   * Instantiates a new display statistics.
   *
   * @param title the title
   * @param prefix the prefix
   */
  public DisplayStatistics(String title, String prefix) {
    this.title = title;
    this.prefix = prefix;
  }

  /**
   * Sets the data.
   *
   * @param histogram the histogram
   * @param statistics the statistics
   * @param d0Value the d 0 value
   */
  public void setData(PlotResults histogram, ImageStatistics statistics, double d0Value) {
    this.statistics = statistics;
    this.plotResults = histogram;
    this.d0Value = d0Value;
  }

  /**
   * Draw the plot.
   */
  public void draw() {
    if (statistics == null || plotResults == null || createFrame()) {
      return;
    }

    // Update the plot
    setValues();

    plotWindow.drawPlot(plotResults.getPlot());
    plotWindow.pack();
    plotWindow.invalidate();
  }

  private boolean createFrame() {
    if (plotWindow != null && plotWindow.isShowing()) {
      return false;
    }

    // Build a panel containing statistics about the plot
    formatter = new DecimalFormat("#0.0000");

    final Panel statisticsPanel = new Panel();
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

  private void setValues() {
    meanLabel.setText(MEAN_TITLE.concat(String.valueOf(formatter.format(statistics.mean))));
    stdDevLabel.setText(STD_DEV_TITLE.concat(String.valueOf(formatter.format(statistics.stdDev))));
    limitsLabel.setText(prefix.concat("(p<" + plotResults.getPValue() + "):")
        .concat(String.valueOf(formatter.format(plotResults.getProbabilityLimits()[0])).concat("|")
            .concat(String.valueOf(formatter.format(plotResults.getProbabilityLimits()[1])))));
    d0ValueLabel.setText(prefix.concat(D0_TITLE).concat(String.valueOf(formatter.format(d0Value))));
    significanceLabel.setText(prefix.concat(plotResults.getSignificanceTest()));
  }

  /**
   * Gets the plot results.
   *
   * @return the plot results
   */
  public PlotResults getPlotResults() {
    return plotResults;
  }

  /**
   * Gets the plot window.
   *
   * @return the plotWindow
   */
  public PlotWindow getPlotWindow() {
    return plotWindow;
  }
}
