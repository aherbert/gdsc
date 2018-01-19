package gdsc.foci;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2018 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.utils.Maths;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.Toolbar;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.tool.PlugInTool;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

/**
 * Output the distances between the pair of spots from two channels at a user selected position.
 */
public class SpotPairDistance implements PlugIn
{
	public static final String TITLE = "Spot Pair Distance";

	private static final String PREFS_CHANNEL_1 = "gdsc.foci.spotpairdistance.channel_1";
	private static final String PREFS_CHANNEL_2 = "gdsc.foci.spotpairdistance.channel_2";
	private static final String PREFS_SEARCH_RANGE = "gdsc.foci.spotpairdistance.search_range";
	private static final String PREFS_COM_RANGE = "gdsc.foci.spotpairdistance.com_range";
	private static final String PREFS_SHOW_DISTANCES = "gdsc.foci.spotpairdistance.show_distances";
	private static final String PREFS_SHOW_SEARCH_REGION = "gdsc.foci.spotpairdistance.show_search_region";
	private static final String PREFS_SHOW_COM_REGION = "gdsc.foci.spotpairdistance.show_com_region";
	private static final String PREFS_SHOW_LINE = "gdsc.foci.spotpairdistance.show_line";
	private static final String PREFS_ADD_TO_OVERLAY = "gdsc.foci.spotpairdistance.add_to_overlay";

	/**
	 * All the work for this plugin is done with the plugin tool.
	 * It handles mouse click events from an image.
	 */
	private static class SpotPairDistancePluginTool extends PlugInTool
	{
		private static TextWindow distancesWindow = null;

		private int channel1 = (int) Prefs.get(PREFS_CHANNEL_1, 1);
		private int channel2 = (int) Prefs.get(PREFS_CHANNEL_2, 2);
		private int searchRange = (int) Prefs.get(PREFS_SEARCH_RANGE, 5);
		private int comRange = (int) Prefs.get(PREFS_COM_RANGE, 2);
		private boolean showDistances = Prefs.get(PREFS_SHOW_DISTANCES, true);
		private boolean showSearchRegion = Prefs.get(PREFS_SHOW_SEARCH_REGION, true);
		private boolean showComRegion = Prefs.get(PREFS_SHOW_COM_REGION, true);
		private boolean showLine = Prefs.get(PREFS_SHOW_LINE, true);
		private boolean addToOverlay = Prefs.get(PREFS_ADD_TO_OVERLAY, true);

		boolean active = true;

		@Override
		public String getToolName()
		{
			return TITLE + " Tool";
		}

		@Override
		public String getToolIcon()
		{
			// A magenta line between a red and blue spot
			return "Cf0fL32daCf00o1055C00foc855";
		}

		@Override
		public void showOptionsDialog()
		{
			GenericDialog gd = new GenericDialog(TITLE + " Tool Options");
			gd.addMessage(
				//@formatter:off
				"Click on a multi-channel image and the distance between the\n" +
				"center-of-mass of spots in two channels will be measured.");
				//@formatter:on
			if (!hasMultiChannelImage())
				gd.addMessage("Warning: Currently no multi-channel images are open.");
			gd.addNumericField("Channel_1", channel1, 0);
			gd.addNumericField("Channel_2", channel2, 0);
			gd.addSlider("Search_range", 1, 10, searchRange);
			gd.addSlider("Centre_of_mass_range", 1, 10, comRange);
			gd.addCheckbox("Show_distances", showDistances);
			gd.addCheckbox("Show_search_region", showSearchRegion);
			gd.addCheckbox("Show_com_region", showComRegion);
			gd.addCheckbox("Show_line", showLine);
			gd.addCheckbox("Add_to_overlay", addToOverlay);
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			synchronized (this)
			{
				channel1 = (int) gd.getNextNumber();
				channel2 = (int) gd.getNextNumber();
				searchRange = (int) gd.getNextNumber();
				comRange = (int) gd.getNextNumber();
				comRange = Maths.clip(0, searchRange, comRange);
				showDistances = gd.getNextBoolean();
				showSearchRegion = gd.getNextBoolean();
				showComRegion = gd.getNextBoolean();
				showLine = gd.getNextBoolean();
				addToOverlay = gd.getNextBoolean();
				active = (channel1 != channel2 && searchRange > 0 &&
						(showDistances || showSearchRegion || showComRegion || showLine));
				Prefs.set(PREFS_CHANNEL_1, channel1);
				Prefs.set(PREFS_CHANNEL_2, channel2);
				Prefs.set(PREFS_SEARCH_RANGE, searchRange);
				Prefs.set(PREFS_COM_RANGE, comRange);
				Prefs.set(PREFS_SHOW_DISTANCES, showDistances);
				Prefs.set(PREFS_SHOW_SEARCH_REGION, showSearchRegion);
				Prefs.set(PREFS_SHOW_COM_REGION, showComRegion);
				Prefs.set(PREFS_SHOW_LINE, showLine);
				Prefs.set(PREFS_ADD_TO_OVERLAY, addToOverlay);
			}
		}

		private boolean hasMultiChannelImage()
		{
			for (int id : Utils.getIDList())
			{
				ImagePlus imp = WindowManager.getImage(id);
				if (imp != null && imp.getNChannels() > 1)
					return true;
			}
			return false;
		}

		@Override
		public void mouseClicked(ImagePlus imp, MouseEvent e)
		{
			int c = imp.getNChannels();
			if (!active || c == 1)
				return;

			// Mark this event as handled
			e.consume();

			// Ensure rapid mouse click / new options does not break things
			synchronized (this)
			{
				if (c < channel1 || c < channel2)
				{
					IJ.log(TITLE + " - ERROR: Image has fewer channels than those selected for analysis");
					return;
				}

				ImageCanvas ic = imp.getCanvas();
				int x = ic.offScreenX(e.getX());
				int y = ic.offScreenY(e.getY());

				// Get the region bounds to search for maxima
				Rectangle bounds = new Rectangle(x - searchRange, y - searchRange, 2 * searchRange + 1,
						2 * searchRange + 1).intersection(new Rectangle(imp.getWidth(), imp.getHeight()));
				if (bounds.width == 0 || bounds.height == 0)
					return;

				if (e.isAltDown() || e.isShiftDown() || e.isControlDown())
				{
					// Option to remove the result

					// Remove all the overlay components
					Overlay o = imp.getOverlay();
					if (o != null)
					{
						Roi[] rois = o.toArray();
						o = new Overlay();
						for (int i = 0; i < rois.length; i++)
						{
							Roi roi = rois[i];
							if (roi.isArea())
							{
								Rectangle r = rois[i].getBounds();
								if (r.intersects(bounds))
									continue;
							}
							else if (roi instanceof Line)
							{
								Line line = (Line) roi;
								if (bounds.contains(line.x1d, line.y1d) || bounds.contains(line.x2d, line.y2d))
									continue;
							}
							o.add(rois[i]);
						}
						imp.setOverlay(o);
					}

					// Remove any distances from this image
					if (distancesWindow != null && distancesWindow.isShowing())
					{
						TextPanel tp = distancesWindow.getTextPanel();
						String title = imp.getTitle();
						for (int i = 0; i < tp.getLineCount(); i++)
						{
							String line = tp.getLine(i);
							// Check the image name
							int startIndex = line.indexOf('\t');
							if (startIndex == -1)
								continue;
							if (!title.equals(line.substring(0, startIndex)))
								continue;

							// Find the start of the region column
							for (int j = 4; j-- > 0;)
							{
								startIndex = line.indexOf('\t', startIndex + 1);
								if (startIndex == -1)
									break;
							}
							if (startIndex == -1)
								continue;
							int endIndex = line.indexOf('\t', startIndex + 1);
							if (endIndex == -1)
								continue;
							String text = line.substring(startIndex + 1, endIndex);

							// Region is formatted as 'x,y wxh'
							int commaIndex = text.indexOf(',');
							int spaceIndex = text.indexOf(' ');
							int xIndex = text.indexOf('x');
							if (commaIndex == -1 || spaceIndex < commaIndex || xIndex < spaceIndex)
								continue;
							try
							{
								int xx = Integer.parseInt(text.substring(0, commaIndex));
								int yy = Integer.parseInt(text.substring(commaIndex + 1, spaceIndex));
								int w = Integer.parseInt(text.substring(spaceIndex + 1, xIndex));
								int h = Integer.parseInt(text.substring(xIndex + 1));
								Rectangle r = new Rectangle(xx, yy, w, h);
								if (r.intersects(bounds))
								{
									//tp.setLine(i, "");
									tp.setSelection(i, i);
									tp.clearSelection();
								}
							}
							catch (NumberFormatException ex)
							{
							}
						}
					}

					return;
				}

				int slice = imp.getZ();
				int frame = imp.getFrame();

				int i1 = imp.getStackIndex(channel1, slice, frame);
				int i2 = imp.getStackIndex(channel2, slice, frame);

				ImageStack stack = imp.getImageStack();
				ImageProcessor ip1 = stack.getProcessor(i1);
				ImageProcessor ip2 = stack.getProcessor(i2);

				// Get the maxima
				int maxx = imp.getWidth();
				int m1 = bounds.y * maxx + bounds.x;
				int m2 = m1;
				for (int ys = 0; ys < bounds.height; ys++)
				{
					for (int xs = 0, i = (ys + bounds.y) * maxx + bounds.x; xs < bounds.width; xs++, i++)
					{
						if (ip1.getf(i) > ip1.getf(m1))
							m1 = i;
						if (ip2.getf(i) > ip2.getf(m2))
							m2 = i;
					}
				}

				// Find centre-of-mass around each maxima
				Rectangle r1 = new Rectangle();
				Rectangle r2 = new Rectangle();
				double[] com1 = com(ip1, m1, r1);
				double[] com2 = com(ip2, m2, r2);

				if (showDistances)
					addDistanceResult(imp, slice, frame, bounds, com1, com2);
				if (showSearchRegion || showComRegion || showLine)
				{
					Overlay o = null;
					if (addToOverlay)
						o = imp.getOverlay();
					if (o == null)
						o = new Overlay();

					if (showSearchRegion)
						o.add(createRoi(bounds, Color.magenta));
					if (showComRegion)
					{
						o.add(createRoi(r1, Color.red));
						o.add(createRoi(r2, Color.blue));
					}
					if (showLine)
						o.add(createLine(com1[0], com1[1], com2[0], com2[1], Color.magenta));
					imp.setOverlay(o);
				}
			}
		}

		private double[] com(ImageProcessor ip, int m, Rectangle r)
		{
			int x = m % ip.getWidth();
			int y = m / ip.getWidth();

			// Make range +/- equal
			int rx = comRange;
			if (x + comRange >= ip.getWidth())
				rx = ip.getWidth() - x - 1;
			else if (x - comRange < 0)
				rx = x;
			int ry = comRange;
			if (y + comRange >= ip.getHeight())
				ry = ip.getHeight() - y - 1;
			else if (y - comRange < 0)
				ry = y;
			int mx = x - rx;
			rx = 2 * rx + 1;
			int my = y - ry;
			ry = 2 * ry + 1;

			r.x = mx;
			r.width = rx;
			r.y = my;
			r.height = ry;

			double cx = 0;
			double cy = 0;
			double sum = 0;
			for (int ys = 0; ys < ry; ys++)
			{
				double sumX = 0;
				for (int xs = 0, i = (ys + my) * ip.getWidth() + mx; xs < rx; xs++, i++)
				{
					float f = ip.getf(i);
					sumX += f;
					cx += f * xs;
				}
				sum += sumX;
				cy += sumX * ys;
			}
			// Find centre with 0.5 as the centre of the pixel
			cx = 0.5 + cx / sum;
			cy = 0.5 + cy / sum;
			return new double[] { mx + cx, my + cy };
		}

		private Roi createRoi(Rectangle bounds, Color color)
		{
			Roi roi = new Roi(bounds);
			roi.setStrokeColor(color);
			return roi;
		}

		private Roi createLine(double x1, double y1, double x2, double y2, Color color)
		{
			Line roi = new Line(x1, y1, x2, y2);
			roi.setStrokeColor(color);
			return roi;
		}

		/**
		 * Create the result window (if it is not available)
		 */
		private void createResultsWindow()
		{
			if (showDistances && (distancesWindow == null || !distancesWindow.isShowing()))
			{
				distancesWindow = new TextWindow(TITLE + " Distances", createDistancesHeader(), "", 700, 300);
			}
		}

		private String createDistancesHeader()
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Image\t");
			sb.append("Ch 1\t");
			sb.append("Ch 2\t");
			sb.append("Z\t");
			sb.append("T\t");
			sb.append("Region\t");
			sb.append("X1 (px)\tY1 (px)\t");
			sb.append("X2 (px)\tY2 (px)\t");
			sb.append("Distance (px)\t");
			sb.append("X1\tY1\t");
			sb.append("X2\tY2\t");
			sb.append("Distance\t");
			sb.append("Units");
			return sb.toString();
		}

		private void addDistanceResult(ImagePlus imp, int slice, int frame, Rectangle bounds, double[] com1,
				double[] com2)
		{
			createResultsWindow();

			StringBuilder sb = new StringBuilder();
			sb.append(imp.getTitle()).append('\t');
			sb.append(channel1).append('\t');
			sb.append(channel2).append('\t');
			sb.append(slice).append('\t');
			sb.append(frame).append('\t');
			sb.append(bounds.x).append(',');
			sb.append(bounds.y).append(' ');
			sb.append(bounds.width).append('x');
			sb.append(bounds.height);

			sb.append('\t').append(Utils.rounded(com1[0]));
			sb.append('\t').append(Utils.rounded(com1[1]));
			sb.append('\t').append(Utils.rounded(com2[0]));
			sb.append('\t').append(Utils.rounded(com2[1]));
			double dx = com1[0] - com2[0];
			double dy = com1[1] - com2[1];
			double d = Math.sqrt(dx * dx + dy * dy);
			sb.append('\t').append(Utils.rounded(d));
			Calibration cal = imp.getCalibration();
			String unit = cal.getUnit();
			// Only if matching units and pixel scaling
			if (cal.getYUnit() == unit && (cal.pixelWidth != 1.0 || cal.pixelWidth != 1.0))
			{
				sb.append('\t').append(Utils.rounded(com1[0] * cal.pixelWidth));
				sb.append('\t').append(Utils.rounded(com1[1] * cal.pixelHeight));
				sb.append('\t').append(Utils.rounded(com2[0] * cal.pixelWidth));
				sb.append('\t').append(Utils.rounded(com2[1] * cal.pixelHeight));
				dx *= cal.pixelWidth;
				dy *= cal.pixelHeight;
				d = Math.sqrt(dx * dx + dy * dy);
				sb.append('\t').append(Utils.rounded(d));
				sb.append('\t').append(unit);
			}
			distancesWindow.append(sb.toString());
		}
	}

	private static SpotPairDistancePluginTool toolInstance = null;

	/**
	 * Initialise the manual translocation finder tool. This is to allow support for calling within macro toolsets.
	 */
	public static void addPluginTool()
	{
		if (toolInstance == null)
		{
			toolInstance = new SpotPairDistancePluginTool();
		}

		// Add the tool
		Toolbar.addPlugInTool(toolInstance);
		IJ.showStatus("Added " + TITLE + " Tool");
	}

	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		addPluginTool();

		toolInstance.showOptionsDialog();
	}
}
