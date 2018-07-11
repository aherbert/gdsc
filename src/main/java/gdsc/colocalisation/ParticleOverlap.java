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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * For all particles in a mask (defined by their unique pixel value), count the overlap with a second mask image. An
 * intensity image is used to calculate the Manders coefficient of each particle.
 */
public class ParticleOverlap implements PlugIn
{
	private static String TITLE = "Particle Overlap";
	private static String newLine = System.getProperty("line.separator");
	private static String header = "Mask1\tImage1\tMask2\tID\tN\tNo\t%\tI\tIo\tManders";

	private static String maskTitle1 = "";
	private static String imageTitle = "";
	private static String maskTitle2 = "";
	private static boolean showTotal = false;
	private static boolean showTable = true;
	private static String filename = "";

	private static TextWindow tw = null;

	private ImagePlus mask1Imp, imageImp, mask2Imp;
	private OutputStreamWriter out = null;

	@Override
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
		final GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage("For each particle in a mask (defined by unique pixel value)\n" +
				"count the overlap and Manders coefficient with a second mask image");

		final String[] imageList = Utils.getImageList(Utils.GREY_SCALE, null);
		final String[] maskList = Utils.getImageList(Utils.GREY_8_16, null);

		gd.addChoice("Particle_mask", maskList, maskTitle1);
		gd.addChoice("Particle_image", imageList, imageTitle);
		gd.addChoice("Overlap_mask", maskList, maskTitle2);
		gd.addCheckbox("Show_total", showTotal);
		gd.addCheckbox("Show_table", showTable);
		gd.addStringField("File", filename, 30);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		maskTitle1 = gd.getNextChoice();
		imageTitle = gd.getNextChoice();
		maskTitle2 = gd.getNextChoice();
		showTotal = gd.getNextBoolean();
		showTable = gd.getNextBoolean();
		filename = gd.getNextString();

		mask1Imp = WindowManager.getImage(maskTitle1);
		imageImp = WindowManager.getImage(imageTitle);
		mask2Imp = WindowManager.getImage(maskTitle2);

		if (mask1Imp == null)
		{
			IJ.error(TITLE, "No particle mask specified");
			return false;
		}
		if (!checkDimensions(mask1Imp, imageImp))
		{
			IJ.error(TITLE, "Particle image must match the dimensions of the particle mask");
			return false;
		}
		if (!checkDimensions(mask1Imp, mask2Imp))
		{
			IJ.error(TITLE, "Overlap mask must match the dimensions of the particle mask");
			return false;
		}
		if (!showTable && emptyFilename())
		{
			IJ.error(TITLE, "No output specified");
			return false;
		}

		return true;
	}

	private static boolean emptyFilename()
	{
		return (filename == null || filename.length() == 0);
	}

	private static boolean checkDimensions(ImagePlus imp1, ImagePlus imp2)
	{
		if (imp2 == null)
			return false;
		final int[] d1 = imp1.getDimensions();
		final int[] d2 = imp2.getDimensions();
		// Just check width, height, depth
		for (final int i : new int[] { 0, 1, 3 })
			if (d1[i] != d2[i])
				return false;
		return true;
	}

	private void analyse()
	{
		// Dimensions are the same. Extract a stack for each image;
		final ImageStack m1 = extractStack(mask1Imp);
		final ImageStack m2 = extractStack(mask2Imp);
		final ImageStack i1 = extractStack(imageImp);

		// Count the pixels in the particle mask
		final int[] n1 = new int[(mask1Imp.getBitDepth() == 8) ? 256 : 65536];
		// Count the pixels in the particle mask that overlap
		final int[] no1 = new int[n1.length];
		// Sum the pixels in the particle image
		final double[] s1 = new double[n1.length];
		// Sum the pixels in the particle image that overlap
		final double[] so1 = new double[n1.length];

		final int size = m1.getWidth() * m1.getHeight();
		for (int n = 1; n <= m1.getSize(); n++)
		{
			final ImageProcessor mask1 = m1.getProcessor(n);
			final ImageProcessor mask2 = m2.getProcessor(n);
			final ImageProcessor image1 = i1.getProcessor(n);

			for (int i = 0; i < size; i++)
			{
				final int p1 = mask1.get(i);
				if (p1 != 0)
				{
					final int p2 = mask2.get(i);
					final float v1 = image1.getf(i);
					n1[p1]++;
					s1[p1] += v1;
					if (p2 != 0)
					{
						no1[p1]++;
						so1[p1] += v1;
					}
				}
			}
		}

		// Summarise results
		createResultsTable();
		createResultsFile();

		final String title = createTitle();
		long sn1 = 0, sno1 = 0;
		double ss1 = 0, sso1 = 0;
		for (int id = 1; id < n1.length; id++)
		{
			if (n1[id] == 0)
				continue;
			sn1 += n1[id];
			sno1 += no1[id];
			ss1 += s1[id];
			sso1 += so1[id];
			addResult(title, id, n1[id], no1[id], s1[id], so1[id]);
		}
		if (showTotal)
			addResult(title, 0, sn1, sno1, ss1, sso1);

		closeResultsFile();
	}

	private static ImageStack extractStack(ImagePlus imp)
	{
		final ImageStack oldStack = imp.getImageStack();
		final int channel = imp.getChannel();
		final int frame = imp.getFrame();
		final int size = imp.getNSlices();
		final ImageStack stack = new ImageStack(imp.getWidth(), imp.getHeight(), size);
		for (int slice = 1; slice <= size; slice++)
		{
			final int index = imp.getStackIndex(channel, slice, frame);
			stack.setPixels(oldStack.getPixels(index), slice);
		}
		return stack;
	}

	private static void createResultsTable()
	{
		if (!showTable)
			return;
		if (tw == null || !tw.isShowing())
			tw = new TextWindow(TITLE + " Results", header, "", 800, 500);
	}

	private void createResultsFile()
	{
		if (emptyFilename())
			return;

		try
		{
			if (filename.lastIndexOf('.') < 0)
				filename += ".xls";
			final FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos, "UTF-8");
			try
			{
				out.write(header);
				out.write(newLine);
			}
			catch (final IOException e)
			{
				closeResultsFile();
			}
		}
		catch (final FileNotFoundException e)
		{
		}
		catch (final UnsupportedEncodingException e)
		{
		}
	}

	private String createTitle()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append(getName(mask1Imp)).append('\t');
		sb.append(getName(imageImp)).append('\t');
		sb.append(getName(mask2Imp)).append('\t');
		return sb.toString();
	}

	private static String getName(ImagePlus imp)
	{
		String name = imp.getTitle();
		int suffix = 0;
		final String[] prefix = { " [", "," };
		if (imp.getNChannels() > 1)
			name += prefix[suffix++] + "C=" + imp.getChannel();
		if (imp.getNFrames() > 1)
			name += prefix[suffix++] + "T=" + imp.getFrame();
		if (suffix > 0)
			name += "]";
		return name;
	}

	private void addResult(String title, int id, long n1, long no1, double s1, double so1)
	{
		final StringBuilder sb = new StringBuilder(title);
		sb.append(id).append('\t');
		sb.append(n1).append('\t');
		sb.append(no1).append('\t');
		sb.append(Utils.rounded((100.0 * no1) / n1)).append('\t');
		sb.append(s1).append('\t');
		sb.append(so1).append('\t');
		sb.append(Utils.rounded(so1 / s1, 5)).append('\t');

		recordResult(sb.toString());
	}

	private void recordResult(String string)
	{
		if (showTable)
			tw.append(string);
		if (out != null)
			try
			{
				out.write(string);
				out.write(newLine);
			}
			catch (final IOException e)
			{
				closeResultsFile();
			}
	}

	private void closeResultsFile()
	{
		if (out != null)
			try
			{
				out.close();
			}
			catch (final IOException e)
			{
			}
			finally
			{
				out = null;
			}
	}
}
