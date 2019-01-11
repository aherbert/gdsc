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
import uk.ac.sussex.gdsc.core.utils.MathUtils;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GUI;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.MacroInstaller;
import ij.plugin.frame.PlugInFrame;
import ij.text.TextWindow;

import java.awt.Checkbox;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Measures the distance between two consecutive points in XYZ.
 */
public class Measure3D_PlugIn extends PlugInFrame {
  private static final long serialVersionUID = 286478476052530844L;
  private static final String PLUGIN_TITLE = "Measure 3D";
  private static final String OPT_LOCATION = "Measure3D.location";
  private static final String OPT_LOCATION_RESULTS = "Measure3D.location2";
  private static Measure3D_PlugIn instance;
  private static TextWindow results;

  private int lastId;
  private int lastX;
  private int lastY;
  private int lastZ;
  private int lastC;
  private int lastT;

  private GridBagLayout mainGrid;
  private GridBagConstraints constraints;
  private int row;
  private Label[] labels;
  private Checkbox overlayCheckbox;

  /**
   * Constructor.
   */
  public Measure3D_PlugIn() {
    super(PLUGIN_TITLE);
  }

  /** {@inheritDoc} */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (WindowManager.getImageCount() == 0) {
      IJ.showMessage("No images opened.");
      return;
    }

    // Install the macro that is called when the image is clicked.
    // Do this each time since the toolbar could change.
    installTool();

    if (instance != null) {
      if (!(instance.getTitle().equals(getTitle()))) {
        final Measure3D_PlugIn oldInstance = instance;
        Prefs.saveLocation(OPT_LOCATION, oldInstance.getLocation());
        oldInstance.close();
      } else {
        instance.toFront();
        return;
      }
    }

    instance = this;
    IJ.register(Measure3D_PlugIn.class);
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
  }

  /**
   * Run the Measure3D plugin using the cursor position on the current image.
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
      final Measure3D_PlugIn p = new Measure3D_PlugIn();
      p.run("");
    }
  }

  private void installTool() {
    final String name = PLUGIN_TITLE + " Tool";
    if (Toolbar.getInstance().getToolId(name) == -1) {
      final StringBuilder sb = new StringBuilder();
      sb.append("macro 'Measure 3D Tool - L0ef7F0d22Fe722C00fT06103T5610D' {\n");
      sb.append("   call('").append(this.getClass().getName()).append(".run');\n");
      sb.append("};\n");
      new MacroInstaller().install(sb.toString());
    }
    Toolbar.getInstance().setTool(name);
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
    final int x = p.x;
    final int y = p.y;
    final int c = imp.getC();
    final int z = imp.getZ();
    final int t = imp.getT();

    if (lastId == imp.getID() && lastC == c && lastT == t) {
      // This is the second point clicked on the same image and [C,T]

      // Reset the clicked point
      lastId = 0;
      for (int i = 1; i < labels.length; i++) {
        labels[i].setText("");
      }

      // Compute the distance
      double dx = x - lastX;
      double dy = y - lastY;
      double dz = z - lastZ;
      final double d = Math.sqrt(dx * dx + dy * dy + dz + dz);

      // Compute calibrated distance
      final Calibration cal = imp.getCalibration();
      double d2 = -1;
      String units = "";
      if (cal != null
          // Assume X and Y units are the same. Check the z-unit.
          && cal.getXUnit().equals(cal.getZUnit())) {
        dx *= cal.pixelWidth;
        dy *= cal.pixelHeight;
        dz *= cal.pixelDepth;
        d2 = Math.sqrt(dx * dx + dy * dy + dz + dz);
        units = cal.getXUnit();
      }

      // Record to a table
      record(imp, x, y, z, d, d2, units);

      // Overlay the line on the image
      if (overlayCheckbox.getState()) {
        final Line roi = new Line(lastX, lastY, x, y);
        final Overlay o = new Overlay(roi);
        imp.setOverlay(o);
      }
    } else {
      // This is the first point clicked on the same image and [C,T]

      // Display the clicked point
      labels[0].setText(imp.getTitle());
      labels[1].setText(Integer.toString(x));
      labels[2].setText(Integer.toString(y));
      labels[3].setText(Integer.toString(z));
      labels[4].setText(Integer.toString(c));
      labels[5].setText(Integer.toString(t));
      pack();

      // Store the point that was clicked
      lastId = imp.getID();
      lastX = x;
      lastY = y;
      lastZ = z;
      lastC = c;
      lastT = t;

      // Overlay the first point on the image
      if (overlayCheckbox.getState()) {
        final PointRoi roi = new PointRoi(lastX, lastY);
        final Overlay o = new Overlay(roi);
        imp.setOverlay(o);
      }
    }
  }

  private static void createResultsTable() {
    if (results == null || !results.isVisible()) {
      results = new TextWindow(PLUGIN_TITLE,
          "Image\tc\tt\tx1\ty1\tz1\tx2\ty2\tz2\tD (px)\tD\tUnits", "", 800, 400);
      final Point loc = Prefs.getLocation(OPT_LOCATION_RESULTS);
      if (loc != null) {
        results.setLocation(loc);
      }

      results.addWindowListener(new WindowAdapter() {
        @Override
        public void windowClosing(WindowEvent event) {
          Prefs.saveLocation(OPT_LOCATION_RESULTS, results.getLocation());
        }
      });
    }
  }

  private void record(ImagePlus imp, int x, int y, int z, double distance,
      double calibratedDistance, String units) {
    createResultsTable();
    final StringBuilder sb = new StringBuilder(imp.getTitle());
    sb.append('\t').append(lastC);
    sb.append('\t').append(lastT);
    sb.append('\t').append(lastX);
    sb.append('\t').append(lastY);
    sb.append('\t').append(lastZ);
    sb.append('\t').append(x);
    sb.append('\t').append(y);
    sb.append('\t').append(z);
    sb.append('\t').append(MathUtils.rounded(distance));
    if (calibratedDistance != -1) {
      sb.append('\t').append(MathUtils.rounded(calibratedDistance));
      sb.append('\t').append(units);
    } else {
      sb.append("\t\t");
    }
    results.append(sb.toString());
  }

  /**
   * Gets the current image.
   *
   * @return The current image (must have an image canvas).
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
    if (imp == null || imp.getCanvas() == null) {
      return null;
    }
    return imp;
  }

  /** {@inheritDoc} */
  @Override
  public void close() {
    Prefs.saveLocation(OPT_LOCATION, getLocation());
    instance = null;
    super.close();
  }

  private void createFrame() {
    mainGrid = new GridBagLayout();
    constraints = new GridBagConstraints();

    setLayout(mainGrid);

    // Build a grid that shows the last pressed position.
    labels = new Label[6];
    for (int i = 0; i < labels.length; i++) {
      labels[i] = new Label();
    }
    final ImagePlus imp = getCurrentImage();
    final String title = (imp == null) ? "" : imp.getTitle();
    createLabelPanel(labels[0], "Image", title);
    createLabelPanel(labels[1], "X", "");
    createLabelPanel(labels[2], "Y", "");
    createLabelPanel(labels[3], "Z", "");
    createLabelPanel(labels[4], "C", "");
    createLabelPanel(labels[5], "T", "");

    overlayCheckbox = new Checkbox("Overlay", true);
    add(overlayCheckbox, 0, 1);
  }

  private void createLabelPanel(Label labelField, String label, String value) {
    final Label listLabel = new Label(label, 0);
    add(listLabel, 0, 1);
    if (labelField != null) {
      labelField.setText(value);
      add(labelField, 1, 1);
    }
    row++;
  }

  private void add(Component comp, int gridx, int width) {
    constraints.gridx = gridx;
    constraints.gridy = row;
    constraints.gridwidth = width;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.WEST;
    mainGrid.setConstraints(comp, constraints);
    add(comp);
  }
}
