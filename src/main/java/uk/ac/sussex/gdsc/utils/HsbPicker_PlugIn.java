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

package uk.ac.sussex.gdsc.utils;

import uk.ac.sussex.gdsc.UsageTracker;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.Toolbar;
import ij.macro.MacroRunner;
import ij.plugin.MacroInstaller;
import ij.plugin.frame.PlugInFrame;
import ij.process.ImageProcessor;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Point;
import java.awt.Scrollbar;

import javax.swing.JPanel;

/**
 * Alows an RGB image to be filtered using HSB limits.
 */
public class HsbPicker_PlugIn extends PlugInFrame {
  private static final long serialVersionUID = 5755638798388461612L;

  private static final String PLUGIN_TITLE = "HSB Picker";
  private static final String OPT_LOCATION = "HSB_Picker.location";
  private static final double SCALE = 100;

  private static HsbPicker_PlugIn instance;
  private Scrollbar sampleSlider;
  private Label pixelCountLabel;
  private final Label[] statsLabel;
  private Scrollbar scaleSlider;

  private GridBagLayout mainGrid;
  private GridBagConstraints constraints;
  private int row;

  private final SummaryStatistics[] stats;

  /**
   * Constructor.
   */
  public HsbPicker_PlugIn() {
    super(PLUGIN_TITLE);
    stats = new SummaryStatistics[3];
    statsLabel = new Label[3];
    for (int i = 0; i < stats.length; i++) {
      stats[i] = new SummaryStatistics();
    }
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (WindowManager.getImageCount() == 0) {
      IJ.showMessage("No images opened.");
      return;
    }

    if (instance != null) {
      if (!(instance.getTitle().equals(getTitle()))) {
        final HsbPicker_PlugIn oldInstance = instance;
        Prefs.saveLocation(OPT_LOCATION, oldInstance.getLocation());
        oldInstance.close();
      } else {
        instance.toFront();
        return;
      }
    }

    instance = this;
    IJ.register(HsbPicker_PlugIn.class);
    WindowManager.addWindow(this);

    createFrame();

    addKeyListener(IJ.getInstance());
    pack();
    final Point loc = Prefs.getLocation(OPT_LOCATION);
    if (loc != null) {
      setLocation(loc);
    } else {
      GUI.center(this);
    }
    if (IJ.isMacOSX()) {
      setResizable(false);
    }
    setVisible(true);

    // Install the macro that is called when the image is clicked
    installTool();
  }

  /**
   * Run the HSB Picker using the cursor position on the current image.
   */
  public static void run() {
    if (instance != null) {
      instance.imageClicked();
    } else {
      final ImagePlus imp = getCurrentImage();
      if (imp == null) {
        return;
      }
      // Create a new instance
      final HsbPicker_PlugIn p = new HsbPicker_PlugIn();
      p.run("");
    }
  }

  private void installTool() {
    final StringBuilder sb = new StringBuilder();
    sb.append("macro 'HSB Picker Tool - C00fT0610HC0f0T5910SCf00Tac10L' {\n");
    sb.append("   call('").append(this.getClass().getName()).append(".run');\n");
    sb.append("};\n");
    new MacroInstaller().install(sb.toString());
    Toolbar.getInstance().setTool(Toolbar.SPARE1);
  }

  private void imageClicked() {
    final ImagePlus imp = getCurrentImage();
    if (imp == null) {
      return;
    }
    final Point p = imp.getCanvas().getCursorLoc();
    if (p == null) {
      return;
    }
    final ImageProcessor ip = imp.getProcessor();
    addValue(ip, p);
  }

  /**
   * Gets the current image.
   *
   * @return The current image (must be 24-bit and have an image canvas).
   */
  private static ImagePlus getCurrentImage() {
    // NOTE: BUG
    // The ImageCanvas.mousePressed(MouseEvent event) eventually calls
    // Toolbar.getInstance().runMacroTool(toolID)
    // This runs the HSB_Picker if the tool is selected.
    //
    // This happens before WindowManager.setCurrentImage(...) is called
    // by the containing window that has been activated or brought to the front.
    // This means it is possible to click in a different image, raising the ImageCanvas event,
    // but have the HSL values sampled from the previous current image due to the use
    // of WindowManager.getCurrentImage().
    //
    // This can be fixed by setting:
    // WindowManager.setCurrentWindow(win)
    // in the ImageCanvas.mousePressed(...) method.

    final ImagePlus imp = WindowManager.getCurrentImage();
    if (imp == null || imp.getBitDepth() != 24 || imp.getCanvas() == null) {
      return null;
    }
    return imp;
  }

  private void addValue(ImageProcessor ip, Point point) {
    final int[] iArray = new int[3];
    final float[] hsbvals = new float[3];

    final int width = sampleSlider.getValue();
    final int limit = width * width;
    for (int y = -width; y <= width; y++) {
      for (int x = -width; x <= width; x++) {
        if (x * x + y * y > limit) {
          continue;
        }

        ip.getPixel(x + point.x, y + point.y, iArray);
        Color.RGBtoHSB(iArray[0], iArray[1], iArray[2], hsbvals);
        for (int i = 0; i < 3; i++) {
          stats[i].addValue(hsbvals[i]);
        }
      }
    }

    updateDisplayedStatistics();
  }

  private void clear() {
    for (final SummaryStatistics s : stats) {
      s.clear();
    }
    updateDisplayedStatistics();
  }

  private void runFilter() {
    if (stats[0].getN() < 2) {
      IJ.log("Not enough samples to run the filter");
      return;
    }
    // Use the SD to set a 95% interval for the width
    final double scale = scaleSlider.getValue() / SCALE;
    final float hWidth = (float) (stats[0].getStandardDeviation() * scale);
    final float sWidth = (float) (stats[1].getStandardDeviation() * scale);
    final float bWidth = (float) (stats[2].getStandardDeviation() * scale);
    HsbFilter_PlugIn.hue = clip(stats[0].getMean());
    HsbFilter_PlugIn.hueWidth = clip(hWidth);
    HsbFilter_PlugIn.saturation = clip(stats[1].getMean());
    HsbFilter_PlugIn.saturationWidth = clip(sWidth);
    HsbFilter_PlugIn.brightness = clip(stats[2].getMean());
    HsbFilter_PlugIn.brightnessWidth = clip(bWidth);
    IJ.doCommand(HsbFilter_PlugIn.TITLE);
  }

  private static float clip(double value) {
    return (float) (Math.max(0, Math.min(value, 1)));
  }

  private void updateDisplayedStatistics() {
    pixelCountLabel.setText(Long.toString(stats[0].getN()));
    for (int i = 0; i < 3; i++) {
      statsLabel[i].setText(summary(stats[i]));
    }
  }

  private static String summary(SummaryStatistics stats) {
    return String.format("%.3f +/- %.4f", stats.getMean(), stats.getStandardDeviation());
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    Prefs.saveLocation(OPT_LOCATION, getLocation());
    instance = null;
    super.close();
  }

  @SuppressWarnings("unused")
  private void createFrame() {
    mainGrid = new GridBagLayout();
    constraints = new GridBagConstraints();

    setLayout(mainGrid);

    createSliderPanel(new Scrollbar(Scrollbar.HORIZONTAL, 2, 1, 0, 15), "Sample radius",
        new Label("0"), 1);

    pixelCountLabel = new Label();
    for (int i = 0; i < 3; i++) {
      statsLabel[i] = new Label();
    }
    createLabelPanel(pixelCountLabel, "Pixels", "0");
    createLabelPanel(statsLabel[0], "Hue", "0");
    createLabelPanel(statsLabel[1], "Saturation", "0");
    createLabelPanel(statsLabel[2], "Brightness", "0");

    // Add the buttons
    final Button clearButton = new Button("Reset");
    clearButton.addActionListener(event -> clear());
    add(clearButton, 0, 3);
    row++;

    scaleSlider = new Scrollbar(Scrollbar.HORIZONTAL, (int) (2 * SCALE), 1, 1, (int) (4 * SCALE));
    createSliderPanel(scaleSlider, "Filter scale", new Label("0"), SCALE);

    // Add the buttons
    final Button filterButton = new Button("HSB Filter");
    filterButton.addActionListener(event -> runFilter());
    final Button okButton = new Button("Close");
    okButton.addActionListener(event -> close());
    final Button helpButton = new Button("Help");
    helpButton.addActionListener(event -> {
      final String macro = "run('URL...', 'url=" + uk.ac.sussex.gdsc.help.UrlUtils.UTILITY + "');";
      new MacroRunner(macro);
    });

    final JPanel buttonPanel = new JPanel();
    final FlowLayout l = new FlowLayout();
    l.setVgap(0);
    buttonPanel.setLayout(l);
    buttonPanel.add(filterButton, BorderLayout.CENTER);
    buttonPanel.add(okButton, BorderLayout.CENTER);
    buttonPanel.add(helpButton, BorderLayout.CENTER);

    add(buttonPanel, 0, 3);
    row++;

    updateDisplayedStatistics();
  }

  private void add(Component comp, int x, int width) {
    constraints.gridx = x;
    constraints.gridy = row;
    constraints.gridwidth = width;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.WEST;
    mainGrid.setConstraints(comp, constraints);
    add(comp);
  }

  private void createSliderPanel(final Scrollbar sliderField, String label, final Label sliderLabel,
      final double scale) {
    final Label listLabel = new Label(label, 0);
    add(listLabel, 0, 1);
    sliderField.setSize(100, 10);
    constraints.ipadx = 75;
    add(sliderField, 1, 1);
    constraints.ipadx = 0;
    sliderField.addAdjustmentListener(event -> setSliderLabel(sliderField, sliderLabel, scale));
    add(sliderLabel, 2, 1);
    setSliderLabel(sliderField, sliderLabel, scale);
    row++;
  }

  private static void setSliderLabel(final Scrollbar sliderField, final Label sliderLabel,
      double scale) {
    final double value = sliderField.getValue() / scale;
    sliderLabel.setText(String.format("%.2f", value));
  }

  private void createLabelPanel(Label labelField, String label, String value) {
    final Label listLabel = new Label(label, 0);
    add(listLabel, 0, 1);
    if (labelField != null) {
      labelField.setText(value);
      add(labelField, 1, 2);
    }
    row++;
  }
}
