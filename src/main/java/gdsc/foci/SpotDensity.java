package gdsc.foci;

import gdsc.UsageTracker;

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

import gdsc.core.ij.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.filter.EDM;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.text.TextPanel;
import ij.text.TextWindow;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Output the density around spots within a mask region. Spots are defined using FindFoci results loaded from memory.
 * <p>
 * A mask must be defined using a freehand ROI or provided as an image. This is used to create a 2D distance map of foci
 * from the edge of the mask. Any foci within the maximum analysis distance from the edge are excluded from analysis.
 */
public class SpotDensity implements PlugIn
{
	public static String TITLE = "Spot Density";
	private static TextWindow resultsWindow = null;

	private static String resultsName1 = "";
	private static String resultsName2 = "";
	private static String maskImage = "";
	private static double distance = 15;
	private static double interval = 1.5;

	private class PC
	{
		final int n, area;
		final double[] r, pcf;

		public PC(int n, int area, double[] r, double[] pcf)
		{
			this.n = n;
			this.area = area;
			this.r = r;
			this.pcf = pcf;
		}
	}

	private static ArrayList<PC> results = new ArrayList<PC>();

	private ImagePlus imp;

	private class Foci
	{
		final int id, x, y;

		public Foci(int id, int x, int y)
		{
			this.id = id;
			this.x = x;
			this.y = y;
		}

		public double distance2(Foci that)
		{
			final double dx = this.x - that.x;
			final double dy = this.y - that.y;
			return dx * dx + dy * dy;
		}
	}

	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		// List the foci results
		String[] names = FindFoci.getResultsNames();
		if (names == null || names.length == 0)
		{
			IJ.error(TITLE, "Spots must be stored in memory using the " + FindFoci.TITLE + " plugin");
			return;
		}

		imp = WindowManager.getCurrentImage();
		Roi roi = null;
		if (imp != null)
			roi = imp.getRoi();

		// Build a list of the open images for use as a mask
		String[] maskImageList = buildMaskList(roi);

		if (maskImageList.length == 0)
		{
			IJ.error(TITLE, "No mask images and no area ROI on current image");
			return;
		}

		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("Analyses spots within a mask/ROI region\nand computes density and closest distances.");

		gd.addChoice("Results_name_1", names, resultsName1);
		gd.addChoice("Results_name_2", names, resultsName2);
		gd.addChoice("Mask", maskImageList, maskImage);
		gd.addNumericField("Distance", distance, 2, 6, "pixels");
		gd.addNumericField("Interval", interval, 2, 6, "pixels");
		gd.addHelp(gdsc.help.URL.FIND_FOCI);

		gd.showDialog();
		if (!gd.wasOKed())
			return;

		resultsName1 = gd.getNextChoice();
		resultsName2 = gd.getNextChoice();
		maskImage = gd.getNextChoice();
		distance = gd.getNextNumber();
		interval = gd.getNextNumber();

		FloatProcessor fp = createDistanceMap(imp, maskImage);
		if (fp == null)
			return;

		// Get the foci
		Foci[] foci1 = getFoci(resultsName1);
		Foci[] foci2 = getFoci(resultsName2);

		if (foci1 == null || foci2 == null)
			return;

		analyse(foci1, foci2, resultsName1.equals(resultsName2), fp);
	}

	private String[] buildMaskList(Roi roi)
	{
		ArrayList<String> newImageList = new ArrayList<String>();
		if (roi != null && roi.isArea())
			newImageList.add("[ROI]");
		newImageList.addAll(Arrays.asList(Utils.getImageList(Utils.GREY_8_16, null)));
		return newImageList.toArray(new String[newImageList.size()]);
	}

	private FloatProcessor createDistanceMap(ImagePlus imp, String maskImage)
	{
		ImagePlus maskImp = WindowManager.getImage(maskImage);

		ByteProcessor bp = null;
		if (maskImp == null)
		{
			// Build a mask image using the input image ROI
			Roi roi = (imp == null) ? null : imp.getRoi();
			if (roi == null || !roi.isArea())
			{
				IJ.showMessage("Error", "No region defined (use an area ROI or an input mask)");
				return null;
			}
			bp = new ByteProcessor(imp.getWidth(), imp.getHeight());
			bp.setValue(255);
			bp.setRoi(roi);
			bp.fill(roi);
		}
		else
		{
			ImageProcessor ip = maskImp.getProcessor();
			bp = new ByteProcessor(maskImp.getWidth(), maskImp.getHeight());
			for (int i = 0; i < bp.getPixelCount(); i++)
				if (ip.get(i) != 0)
					bp.set(i, 255);
		}

		//		Utils.display("Mask", bp);

		// Create a distance map from the mask
		EDM edm = new EDM();
		FloatProcessor map = edm.makeFloatEDM(bp, 0, true);

		//		Utils.display("Map", map);
		//		
		//		float[] fmap = (float[])map.getPixels();
		//		byte[] mask = new byte[fmap.length];
		//		for (int i = 0; i < mask.length; i++)
		//			if (fmap[i] >= distance)
		//				mask[i] = -1;
		//		Utils.display("Mask2", new ByteProcessor(bp.getWidth(), bp.getHeight(), mask));

		return map;
	}

	private Foci[] getFoci(String resultsName)
	{
		ArrayList<FindFociResult> results = FindFoci.getResults(resultsName);
		if (results == null || results.size() == 0)
		{
			IJ.showMessage("Error", "No foci with the name " + resultsName);
			return null;
		}
		Foci[] foci = new Foci[results.size()];
		int i = 0;
		for (FindFociResult result : results)
			foci[i++] = new Foci(i, result.RESULT_X, result.RESULT_Y);
		return foci;
	}

	/**
	 * For all foci in set 1, compare to set 2 and output a histogram of the average density around each foci (pair
	 * correlation) and the minimum distance to another foci.
	 * 
	 * @param foci1
	 * @param foci2
	 * @param identical
	 *            True if the two sets are the same foci (self comparisons will be ignored)
	 */
	private void analyse(Foci[] foci1, Foci[] foci2, boolean identical, FloatProcessor map)
	{
		final int nBins = (int) (distance / interval) + 1;
		final double maxDistance2 = distance * distance;
		int[] H = new int[nBins];
		int[] H2 = new int[nBins];

		double[] distances = new double[foci1.length];
		int count = 0;

		// Update the second set to foci inside the mask 
		int N2 = 0;
		for (int j = foci2.length; j-- > 0;)
		{
			final Foci m2 = foci2[j];
			if (map.getPixelValue(m2.x, m2.y) != 0)
			{
				foci2[N2++] = m2;
			}
		}

		int N = 0;
		for (int i = foci1.length; i-- > 0;)
		{
			final Foci m = foci1[i];
			// Ignore molecules that are near the edge of the boundary
			if (map.getPixelValue(m.x, m.y) < distance)
				continue;
			N++;

			double min = Double.POSITIVE_INFINITY;
			for (int j = N2; j-- > 0;)
			{
				final Foci m2 = foci2[j];

				if (identical && m.id == m2.id)
					continue;

				final double d2 = m.distance2(m2);
				if (d2 < maxDistance2)
				{
					H[(int) (Math.sqrt(d2) / interval)]++;
				}
				if (d2 < min)
				{
					min = d2;
				}
			}

			if (min != Double.POSITIVE_INFINITY)
			{
				min = Math.sqrt(min);
				if (min < distance)
					H2[(int) (min / interval)]++;
				distances[count++] = min;
			}
		}

		double[] r = new double[nBins + 1];
		for (int i = 0; i <= nBins; i++)
			r[i] = i * interval;
		double[] pcf = new double[nBins];
		double[] dMin = new double[nBins];
		if (N > 0)
		{
			final double N_pi = N * Math.PI;
			for (int i = 0; i < nBins; i++)
			{
				// Pair-correlation is the count at the given distance divided by N (the number of items analysed) 
				// and the area at distance ri:
				// H[i] / (N x (pi x (r_i+1)^2 - pi x r_i^2))
				pcf[i] = H[i] / (N_pi * (r[i + 1] * r[i + 1] - r[i] * r[i]));
				// Convert to double for plotting
				dMin[i] = H2[i];
			}
		}

		// Truncate the unused r for the plot
		r = Arrays.copyOf(r, nBins);
		Plot plot1 = new Plot(TITLE + " Min Distance", "Distance (px)", "Frequency", r, dMin);
		PlotWindow pw1 = Utils.display(TITLE + " Min Distance", plot1);

		// The final bin may be empty if the correlation interval was a factor of the correlation distance
		if (pcf[pcf.length - 1] == 0)
		{
			r = Arrays.copyOf(r, nBins - 1);
			pcf = Arrays.copyOf(pcf, nBins - 1);
		}

		// Get the pixels in the entire mask
		int area = 0;
		float[] dMap = (float[]) map.getPixels();
		for (int i = dMap.length; i-- > 0;)
			if (dMap[i] != 0)
				area++;

		double avDensity = (double) N2 / area;

		// Normalisation of the density chart to produce the pair correlation.
		// Get the maximum response for the summary.
		double max = 0;
		double maxr = 0;
		for (int i = 0; i < pcf.length; i++)
		{
			pcf[i] /= avDensity;
			if (max < pcf[i])
			{
				max = pcf[i];
				maxr = r[i];
			}
		}

		// Store the result
		PC pc = new PC(N2, area, r, pcf);
		results.add(pc);

		// Display
		PlotWindow pw2 = showPairCorrelation(pc);

		Point p = pw1.getLocation();
		p.y += pw1.getHeight();
		pw2.setLocation(p);

		// Table of results
		createResultsWindow();
		StringBuilder sb = new StringBuilder();
		sb.append(results.size());
		sb.append("\t").append(foci1.length);
		sb.append("\t").append(N);
		sb.append("\t").append(foci2.length);
		sb.append("\t").append(N2);
		sb.append("\t").append(IJ.d2s(distance));
		sb.append("\t").append(IJ.d2s(interval));
		sb.append("\t").append(area);
		sb.append("\t").append(IJ.d2s(avDensity, -3));
		sb.append("\t").append(IJ.d2s(max, 3));
		sb.append("\t").append(IJ.d2s(maxr, 3));
		sb.append("\t").append(count);
		double sum = 0;
		for (int i = 0; i < count; i++)
			sum += distances[i];
		sb.append("\t").append(IJ.d2s(sum / count, 2));

		resultsWindow.append(sb.toString());
	}

	private PlotWindow showPairCorrelation(PC pc)
	{
		double avDensity = (double) pc.n / pc.area;
		String title = "Pair Correlation";
		Plot plot2 = new Plot(TITLE + " " + title, "r (px)", "g(r)", pc.r, pc.pcf);
		plot2.setColor(Color.red);
		plot2.drawLine(pc.r[0], 1, pc.r[pc.r.length - 1], 1);
		plot2.addLabel(0, 0, "Av.Density = " + IJ.d2s(avDensity, -3) + " px^-2");
		plot2.setColor(Color.blue);
		return Utils.display(TITLE + " " + title, plot2);
	}

	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(TITLE + " Summary", createResultsHeader(), "", 700, 300);

			// Allow clicking multiple results in the window to show a combined curve
			resultsWindow.getTextPanel().addMouseListener(new MouseListener()
			{
				public void mouseClicked(MouseEvent e)
				{
					TextPanel tp = null;
					if (e.getSource() instanceof TextPanel)
					{
						tp = (TextPanel) e.getSource();
					}
					else if (e.getSource() instanceof Canvas &&
							((Canvas) e.getSource()).getParent() instanceof TextPanel)
					{
						tp = (TextPanel) ((Canvas) e.getSource()).getParent();
					}

					int[] ids = new int[results.size()];
					int count = 0;
					for (int i = tp.getSelectionStart(); i <= tp.getSelectionEnd(); i++)
					{
						String line = tp.getLine(i);
						try
						{
							String sid = line.substring(0, line.indexOf('\t'));
							int id = Integer.parseInt(sid);
							if (id > 0 && id <= results.size())
								ids[count++] = id;
						}
						catch (Exception ex)
						{
							// Ignore for now
						}
					}

					if (count > 2)
					{
						// Ask the user which curves to combine since we may want to ignore some 
						// between start and end
						GenericDialog gd = new GenericDialog(TITLE);
						gd.addMessage("Select which curves to combine");

						int count2 = 0;
						int rowLimit = 20;
						if (count <= rowLimit)
						{
							// Use checkboxes
							for (int i = 0; i < count; i++)
								gd.addCheckbox("ID_" + ids[i], true);
							gd.showDialog();
							if (gd.wasCanceled())
								return;
							for (int i = 0; i < count; i++)
								if (gd.getNextBoolean())
									ids[count2++] = ids[i];
						}
						else
						{
							// Use a text area
							StringBuilder sb = new StringBuilder(Integer.toString(ids[0]));
							for (int i = 1; i < count; i++)
								sb.append("\n").append(ids[i]);
							gd.addTextAreas(sb.toString(), null, rowLimit, 10);
							gd.showDialog();
							if (gd.wasCanceled())
								return;
							for (String token : gd.getNextText().split("[ \t\n\r]+"))
							{
								try
								{
									int id = Integer.parseInt(token);
									if (id > 0 && id <= results.size())
										ids[count2++] = id;
								}
								catch (Exception ex)
								{
									// Ignore for now
								}
							}
						}
						count = count2;
					}

					if (count == 0)
						return;

					// Check all curves are the same size and build an average
					PC pc = results.get(ids[0] - 1);
					int length = pc.r.length;
					int n = pc.n;
					int area = pc.area;
					double[] r = pc.r;
					double[] pcf = pc.pcf.clone();
					for (int i = 1; i < count; i++)
					{
						pc = results.get(ids[i] - 1);
						if (length != pc.r.length)
							return;
						n += pc.n;
						area += pc.area;
						for (int j = 0; j < length; j++)
						{
							// Distance scale must be the same!
							if (r[j] != pc.r[j])
								return;
							pcf[j] += pc.pcf[j];
						}
					}
					for (int j = 0; j < length; j++)
						pcf[j] /= count;

					showPairCorrelation(new PC(n, area, r, pcf));
				}

				public void mouseEntered(MouseEvent arg0)
				{
				}

				public void mouseExited(MouseEvent arg0)
				{
				}

				public void mousePressed(MouseEvent arg0)
				{
				}

				public void mouseReleased(MouseEvent arg0)
				{
				}
			});
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("ID");
		sb.append("\tN1");
		sb.append("\tN1_internal");
		sb.append("\tN2");
		sb.append("\tN2_selection");
		sb.append("\tDistance");
		sb.append("\tInterval");
		sb.append("\tArea");
		sb.append("\tAv. Density");
		sb.append("\tMax g(r)");
		sb.append("\tr");
		sb.append("\tCount Distances");
		sb.append("\tAv. Distance");
		return sb.toString();
	}
}
