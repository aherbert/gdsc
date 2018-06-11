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

import java.awt.Color;
import java.awt.Point;

import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import gdsc.core.utils.Maths;
import gdsc.foci.ObjectAnalyzer3D;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.plugin.PlugIn;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import ij.text.TextWindow;

/**
 * For all particles in a mask (defined by their unique pixel value), sum the pixel intensity in two different images
 * and then perform a correlation analysis.
 */
public class ParticleCorrelation implements PlugIn
{
	private static String TITLE = "Particle Correlation";

	private static String maskTitle1 = "";
	private static String imageTitle1 = "";
	private static String imageTitle2 = "";
	private static int cImage1 = 1;
	private static int cImage2 = 2;
	private static boolean eightConnected = false;
	private static int minSize = 0;
	private static boolean showDataTable = true;
	private static boolean showPlot = false;
	private static boolean showObjects = false;

	private static TextWindow twSummary = null;
	private static TextWindow twDataTable = null;

	private ImagePlus maskImp, imageImp1, imageImp2;
	private int c1, c2;

	public void run(String arg)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

		if (WindowManager.getImageCount() == 0)
		{
			IJ.showMessage(TITLE, "No images opened.");
			return;
		}

		if (!showDialog())
			return;

		analyse();
	}

	private boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("For each particle in a mask (defined by unique pixel value)\n" +
				"sum the pixel intensity in two different images and correlate");

		String[] imageList = Utils.getImageList(Utils.GREY_SCALE, null);
		String[] maskList = Utils.getImageList(Utils.GREY_8_16, null);

		gd.addChoice("Particle_mask", maskList, maskTitle1);
		gd.addChoice("Particle_image1", imageList, imageTitle1);
		gd.addChoice("Particle_image2", imageList, imageTitle2);
		gd.addCheckbox("Eight_connected", eightConnected);
		gd.addNumericField("Min_object_size", minSize, 0);
		gd.addCheckbox("Show_data_table", showDataTable);
		gd.addCheckbox("Show_plot", showPlot);
		gd.addCheckbox("Show_objects", showObjects);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		maskTitle1 = gd.getNextChoice();
		imageTitle1 = gd.getNextChoice();
		imageTitle2 = gd.getNextChoice();
		eightConnected = gd.getNextBoolean();
		minSize = (int) gd.getNextNumber();
		showDataTable = gd.getNextBoolean();
		showPlot = gd.getNextBoolean();
		showObjects = gd.getNextBoolean();

		maskImp = WindowManager.getImage(maskTitle1);
		imageImp1 = WindowManager.getImage(imageTitle1);
		imageImp2 = WindowManager.getImage(imageTitle2);

		if (maskImp == null)
		{
			IJ.error(TITLE, "No particle mask specified");
			return false;
		}
		if (!checkDimensions(maskImp, imageImp1))
		{
			IJ.error(TITLE, "Particle image 1 must match the dimensions of the particle mask");
			return false;
		}
		if (!checkDimensions(maskImp, imageImp2))
		{
			IJ.error(TITLE, "Particle image 2 must match the dimensions of the particle mask");
			return false;
		}

		// We will use the current channel unless the two input images are the same image.
		if (imageTitle1.equals(imageTitle2))
		{
			gd = new GenericDialog(TITLE);

			gd.addMessage("Same image detected for the two input\nSelect the channels for analysis");
			int n = imageImp1.getNChannels();
			String[] list = new String[n];
			for (int i = 0; i < n; i++)
				list[i] = Integer.toString(i + 1);

			c1 = Math.min(n, cImage1);
			c2 = Math.min(n, cImage2);
			gd.addChoice("Channel_1", list, list[c1 - 1]);
			gd.addChoice("Channel_2", list, list[c2 - 1]);
			gd.showDialog();

			if (gd.wasCanceled())
				return false;

			c1 = cImage1 = gd.getNextChoiceIndex() + 1;
			c2 = cImage2 = gd.getNextChoiceIndex() + 1;
		}
		else
		{
			c1 = imageImp1.getChannel();
			c2 = imageImp2.getChannel();
		}

		return true;
	}

	private boolean checkDimensions(ImagePlus imp1, ImagePlus imp2)
	{
		if (imp2 == null)
			return false;
		int[] d1 = imp1.getDimensions();
		int[] d2 = imp2.getDimensions();
		// Just check width, height, depth 
		for (int i : new int[] { 0, 1, 3 })
			if (d1[i] != d2[i])
				return false;
		return true;
	}

	private void analyse()
	{
		// Dimensions are the same. Extract a stack for each image;
		final int[] mask = extractStack(maskImp, maskImp.getChannel());
		final float[] i1 = extractFloatStack(imageImp1, c1);
		final float[] i2 = extractFloatStack(imageImp2, c2);

		final int maxx = maskImp.getWidth();
		final int maxy = maskImp.getHeight();
		final int maxz = maskImp.getNSlices();
		final ObjectAnalyzer3D oa = new ObjectAnalyzer3D(mask, maxx, maxy, maxz, eightConnected);
		oa.setMinObjectSize(minSize);
		final int[] objectMask = oa.getObjectMask();

		final int[] count = new int[oa.getMaxObject()];
		final double[] sumx = new double[count.length];
		final double[] sumy = new double[count.length];
		final double[] sumz = new double[count.length];
		final double[] sum1 = new double[count.length];
		final double[] sum2 = new double[count.length];
		for (int z = 0, i = 0; z < maxz; z++)
			for (int y = 0; y < maxy; y++)
				for (int x = 0; x < maxx; x++, i++)
				{
					int value = objectMask[i];
					if (value != 0)
					{
						value--;
						sumx[value] += x;
						sumy[value] += y;
						sumz[value] += z;
						sum1[value] += i1[i];
						sum2[value] += i2[i];
						count[value]++;
					}
				}

		// Summarise results
		createResultsTable();

		final String title = createTitle();

		addSummary(title, sum1, sum2);

		if (showDataTable)
		{
			for (int i = 0; i < count.length; i++)
			{
				final double x = sumx[i] / count[i];
				final double y = sumy[i] / count[i];
				// Centre on the slice
				final double z = 1 + sumz[i] / count[i];
				addResult(title, i + 1, getValue(mask, objectMask, i + 1), x, y, z, count[i], sum1[i], sum2[i]);
			}
		}

		if (showPlot)
		{
			String plotTitle = TITLE + " Plot";
			Plot plot = new Plot(plotTitle, getName(imageImp1, c1), getName(imageImp2, c2));
			double[] limitsx = Maths.limits(sum1);
			double[] limitsy = Maths.limits(sum2);
			double dx = (limitsx[1] - limitsx[0]) * 0.05;
			double dy = (limitsy[1] - limitsy[0]) * 0.05;
			plot.setLimits(limitsx[0] - dx, limitsx[1] + dx, limitsy[0] - dy, limitsy[1] + dy);
			plot.setColor(Color.red);
			plot.addPoints(sum1, sum2, Plot.CROSS);
			Utils.display(plotTitle, plot);
		}

		if (showObjects)
		{
			ImageStack stack = new ImageStack(maxx, maxy, maxz);
			for (int z = 0, i = 0; z < maxz; z++)
			{
				ImageProcessor ip = (oa.getMaxObject() < 256) ? new ByteProcessor(maxx, maxy)
						: new ShortProcessor(maxx, maxy);
				for (int y = 0, index = 0; y < maxy; y++)
					for (int x = 0; x < maxx; x++, i++, index++)
					{
						final int value = objectMask[i];
						if (value != 0)
						{
							ip.set(index, value);
						}
					}
				stack.setProcessor(ip, z + 1);
			}
			ImagePlus imp = Utils.display(TITLE + " Objects", stack);
			if (oa.getMaxObject() < 256)
			{
				imp.setDisplayRange(0, oa.getMaxObject());
				imp.updateAndDraw();
			}
		}
	}

	private int[] extractStack(ImagePlus imp, int channel)
	{
		ImageStack stack = imp.getImageStack();
		final int frame = imp.getFrame();
		final int size = imp.getNSlices();
		final int[] image = new int[imp.getWidth() * imp.getHeight() * size];
		for (int slice = 1, i = 0; slice <= size; slice++)
		{
			int stackIndex = imp.getStackIndex(channel, slice, frame);
			ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.get(index);
			}
		}
		return image;
	}

	private float[] extractFloatStack(ImagePlus imp, int channel)
	{
		ImageStack stack = imp.getImageStack();
		final int frame = imp.getFrame();
		final int size = imp.getNSlices();
		final float[] image = new float[imp.getWidth() * imp.getHeight() * size];
		for (int slice = 1, i = 0; slice <= size; slice++)
		{
			int stackIndex = imp.getStackIndex(channel, slice, frame);
			ImageProcessor ip = stack.getProcessor(stackIndex);
			for (int index = 0; index < ip.getPixelCount(); index++)
			{
				image[i++] = ip.getf(index);
			}
		}
		return image;
	}

	private void createResultsTable()
	{
		if (twSummary == null || !twSummary.isShowing())
		{
			twSummary = new TextWindow(TITLE + " Summary",
					"Mask\tImage1\tImage2\tnParticles\tSpearman\tpSpearman\tR\tpR", "", 700, 250);
		}
		if (!showDataTable)
			return;
		if (twDataTable == null || !twDataTable.isShowing())
		{
			twDataTable = new TextWindow(TITLE + " Results", "Mask\tImage1\tImage2\tID\tValue\tx\ty\tz\tN\tSum1\tSum2",
					"", 700, 500);
			Point p = twSummary.getLocation();
			twDataTable.setLocation(p.x, p.y + twSummary.getHeight());
		}
	}

	private String createTitle()
	{
		StringBuilder sb = new StringBuilder();
		sb.append(getName(maskImp, maskImp.getChannel())).append('\t');
		sb.append(getName(imageImp1, c1)).append('\t');
		sb.append(getName(imageImp2, c2)).append('\t');
		return sb.toString();
	}

	private String getName(ImagePlus imp, int channel)
	{
		String name = imp.getTitle();
		int suffix = 0;
		String[] prefix = { " [", "," };
		if (imp.getNChannels() > 1)
		{
			name += prefix[suffix++] + "C=" + channel;
		}
		if (imp.getNFrames() > 1)
			name += prefix[suffix++] + "T=" + imp.getFrame();
		if (suffix > 0)
			name += "]";
		return name;
	}

	private int getValue(int[] mask, int[] objectMask, int value)
	{
		for (int i = 0; i < objectMask.length; i++)
			if (objectMask[i] == value)
				return mask[i];
		return 0;
	}

	private void addSummary(String title, double[] sum1, double[] sum2)
	{
		BlockRealMatrix rm = new BlockRealMatrix(sum1.length, 2);
		rm.setColumn(0, sum1);
		rm.setColumn(1, sum2);

		SpearmansCorrelation sr = new SpearmansCorrelation(rm);
		PearsonsCorrelation p1 = sr.getRankCorrelation();
		PearsonsCorrelation p2 = new PearsonsCorrelation(rm);

		StringBuilder sb = new StringBuilder(title);
		sb.append(sum1.length).append('\t');
		sb.append(Utils.rounded(p1.getCorrelationMatrix().getEntry(0, 1))).append('\t');
		sb.append(Utils.rounded(p1.getCorrelationPValues().getEntry(0, 1))).append('\t');
		sb.append(Utils.rounded(p2.getCorrelationMatrix().getEntry(0, 1))).append('\t');
		sb.append(Utils.rounded(p2.getCorrelationPValues().getEntry(0, 1)));
		twSummary.append(sb.toString());
	}

	private void addResult(String title, int id, int value, double x, double y, double z, int n, double s1, double s2)
	{
		StringBuilder sb = new StringBuilder(title);
		sb.append(id).append('\t');
		sb.append(value).append('\t');
		sb.append(Utils.rounded(x)).append('\t');
		sb.append(Utils.rounded(y)).append('\t');
		sb.append(Utils.rounded(z)).append('\t');
		sb.append(n).append('\t');
		sb.append(s1).append('\t');
		sb.append(s2);
		twDataTable.append(sb.toString());
	}
}
