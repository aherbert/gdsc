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
package gdsc.utils;

import java.awt.Checkbox;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Label;
import java.awt.Point;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
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

/**
 * Measures the distance between two consecutive points in XYZ.
 */
public class Measure3D extends PlugInFrame
{
	private static final long serialVersionUID = 286478476052530844L;
	private static final String TITLE = "Measure 3D";
	private static final String OPT_LOCATION = "Measure3D.location";
	private static final String OPT_LOCATION_RESULTS = "Measure3D.location2";
	private static Measure3D instance = null;
	private static TextWindow results = null;

	private int lastID = 0, lastX, lastY, lastZ, lastC, lastT;

	/**
	 * Constructor
	 */
	public Measure3D()
	{
		super(TITLE);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#run(java.lang.String)
	 */
	@Override
	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (WindowManager.getImageCount() == 0)
		{
			IJ.showMessage("No images opened.");
			return;
		}

		// Install the macro that is called when the image is clicked.
		// Do this each time since the toolbar could change.	
		installTool();

		if (instance != null)
		{
			if (!(instance.getTitle().equals(getTitle())))
			{
				Measure3D oldInstance = instance;
				Prefs.saveLocation(OPT_LOCATION, oldInstance.getLocation());
				oldInstance.close();
			}
			else
			{
				instance.toFront();
				return;
			}
		}

		instance = this;
		IJ.register(Measure3D.class);
		WindowManager.addWindow(this);

		createFrame();

		addKeyListener(IJ.getInstance());
		pack();
		Point loc = Prefs.getLocation(OPT_LOCATION);
		if (loc != null)
			setLocation(loc);
		else
		{
			GUI.center(this);
		}
		if (IJ.isMacOSX())
			setResizable(false);
		setVisible(true);
	}

	private void installTool()
	{
		String name = "Measure 3D Tool";
		if (Toolbar.getInstance().getToolId(name) == -1)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("macro 'Measure 3D Tool - L0ef7F0d22Fe722C00fT06103T5610D' {\n");
			sb.append("   call('").append(this.getClass().getName()).append(".run');\n");
			sb.append("};\n");
			new MacroInstaller().install(sb.toString());
		}
		Toolbar.getInstance().setTool(name);
	}

	public static void run()
	{
		if (instance != null)
		{
			instance.imageClicked();
		}
		else
		{
			ImagePlus imp = getCurrentImage();
			if (imp == null)
				return;
			// Create a new instance
			Measure3D p = new Measure3D();
			p.run("");
		}
	}

	private void imageClicked()
	{
		ImagePlus imp = getCurrentImage();
		if (imp == null)
			return;
		Point p = imp.getCanvas().getCursorLoc();
		if (p == null)
			return;
		int x = p.x;
		int y = p.y;
		int c = imp.getC();
		int z = imp.getZ();
		int t = imp.getT();

		if (lastID == imp.getID() && lastC == c && lastT == t)
		{
			// This is the second point clicked on the same image and [C,T]

			// Reset the clicked point
			lastID = 0;
			for (int i = 1; i < labels.length; i++)
				labels[i].setText("");

			// Compute the distance
			double dx = x - lastX;
			double dy = y - lastY;
			double dz = z - lastZ;
			double d = Math.sqrt(dx * dx + dy * dy + dz + dz);

			// Compute calibrated distance
			Calibration cal = imp.getCalibration();
			double d2 = -1;
			if (cal != null)
			{
				// Assume X and Y units are the same. Check the z-unit.
				if (cal.getXUnit().equals(cal.getZUnit()))
				{
					dx *= cal.pixelWidth;
					dy *= cal.pixelHeight;
					dz *= cal.pixelDepth;
					d2 = Math.sqrt(dx * dx + dy * dy + dz + dz);
				}
			}

			// Record to a table
			record(imp, x, y, z, c, t, d, d2, cal.getXUnit());

			// Overlay the line on the image
			if (overlayCheckbox.getState())
			{
				Line roi = new Line(lastX, lastY, x, y);
				Overlay o = new Overlay(roi);
				imp.setOverlay(o);
			}
		}
		else
		{
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
			lastID = imp.getID();
			lastX = x;
			lastY = y;
			lastZ = z;
			lastC = c;
			lastT = t;

			// Overlay the first point on the image
			if (overlayCheckbox.getState())
			{
				PointRoi roi = new PointRoi(lastX, lastY);
				Overlay o = new Overlay(roi);
				imp.setOverlay(o);
			}
		}
	}

	private void createResultsTable()
	{
		if (results == null || !results.isVisible())
		{
			results = new TextWindow(TITLE, "Image\tc\tt\tx1\ty1\tz1\tx2\ty2\tz2\tD (px)\tD\tUnits", "", 800, 400);
			Point loc = Prefs.getLocation(OPT_LOCATION_RESULTS);
			if (loc != null)
				results.setLocation(loc);

			results.addWindowListener(new WindowListener()
			{

				@Override
				public void windowActivated(WindowEvent e)
				{
				}

				@Override
				public void windowClosed(WindowEvent e)
				{
				}

				@Override
				public void windowClosing(WindowEvent e)
				{
					Prefs.saveLocation(OPT_LOCATION_RESULTS, results.getLocation());
				}

				@Override
				public void windowDeactivated(WindowEvent e)
				{
				}

				@Override
				public void windowDeiconified(WindowEvent e)
				{
				}

				@Override
				public void windowIconified(WindowEvent e)
				{
				}

				@Override
				public void windowOpened(WindowEvent e)
				{
				}
			});
		}
	}

	private void record(ImagePlus imp, int x, int y, int z, int c2, int t, double d, double d2, String units)
	{
		createResultsTable();
		StringBuilder sb = new StringBuilder(imp.getTitle());
		sb.append('\t').append(lastC);
		sb.append('\t').append(lastT);
		sb.append('\t').append(lastX);
		sb.append('\t').append(lastY);
		sb.append('\t').append(lastZ);
		sb.append('\t').append(x);
		sb.append('\t').append(y);
		sb.append('\t').append(z);
		sb.append('\t').append(Utils.rounded(d));
		if (d != -1)
		{
			sb.append('\t').append(Utils.rounded(d2));
			sb.append('\t').append(units);
		}
		else
		{
			sb.append("\t\t");
		}
		results.append(sb.toString());
	}

	/**
	 * @return The current image (must have an image canvas)
	 */
	private static ImagePlus getCurrentImage()
	{
		// NOTE: BUG
		// The ImageCanvas.mousePressed(MouseEvent e) eventually calls
		//   Toolbar.getInstance().runMacroTool(toolID);
		// This runs the HSB_Picker if the tool is selected.
		//
		// This happens before WindowManager.setCurrentImage(...) is called
		// by the containing window that has been activated or brought to the front.
		// This means it is possible to click in a different image, raising the ImageCanvas event,
		// but have the HSL values sampled from the previous current image due to the use
		// of WindowManager.getCurrentImage().
		//
		// This can be fixed by setting:
		//   WindowManager.setCurrentWindow(win);
		// in the ImageCanvas.mousePressed(...) method.

		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp == null || imp.getCanvas() == null)
			return null;
		return imp;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.frame.PlugInFrame#close()
	 */
	@Override
	public void close()
	{
		Prefs.saveLocation(OPT_LOCATION, getLocation());
		instance = null;
		super.close();
	}

	GridBagLayout mainGrid = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
	int row = 0;
	Label[] labels;
	Checkbox overlayCheckbox;

	private void createFrame()
	{
		setLayout(mainGrid);

		// Build a grid that shows the last pressed position.
		labels = new Label[6];
		ImagePlus imp = getCurrentImage();
		String title = (imp == null) ? "" : imp.getTitle();
		createLabelPanel(labels[0] = new Label(), "Image", title);
		createLabelPanel(labels[1] = new Label(), "X", "");
		createLabelPanel(labels[2] = new Label(), "Y", "");
		createLabelPanel(labels[3] = new Label(), "Z", "");
		createLabelPanel(labels[4] = new Label(), "C", "");
		createLabelPanel(labels[5] = new Label(), "T", "");

		overlayCheckbox = new Checkbox("Overlay", true);
		add(overlayCheckbox, 0, 1);
	}

	private void createLabelPanel(Label labelField, String label, String value)
	{
		Label listLabel = new Label(label, 0);
		add(listLabel, 0, 1);
		if (labelField != null)
		{
			// labelField.setSize(fontWidth * 3, fontWidth);
			labelField.setText(value);
			add(labelField, 1, 1);
		}
		row++;
	}

	private void add(Component comp, int x, int width)
	{
		c.gridx = x;
		c.gridy = row;
		c.gridwidth = width;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;
		mainGrid.setConstraints(comp, c);
		add(comp);
	}
}
