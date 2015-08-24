package gdsc.foci;

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

import gdsc.utils.ImageJHelper;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealVector;

/**
 * For each unique pixel value in the mask (defining an object), analyse the pixels
 * values and calculate the inertia tensor. Then produce the dimensions of the object
 * on the axes of the moments of inertia.
 */
public class MaskObjectDimensions implements PlugInFilter
{
	public static String FRAME_TITLE = "Mask Object Dimensions";

	private int flags = DOES_16 + DOES_8G;
	private ImagePlus imp;

	private static double mergeDistance = 0;
	private static boolean showOverlay = true;
	private static boolean clearTable = true;

	private static TextWindow resultsWindow = null;

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	public int setup(String arg, ImagePlus imp)
	{
		if (imp == null)
		{
			IJ.noImage();
			return DONE;
		}
		this.imp = imp;
		if (!showDialog())
			return DONE;
		return flags;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.ExtendedPlugInFilter#showDialog(ij.ImagePlus,
	 * java.lang.String, ij.plugin.filter.PlugInFilterRunner)
	 */
	private boolean showDialog()
	{
		GenericDialog gd = new GenericDialog(FRAME_TITLE);

		gd.addMessage("For each object defined with a unique pixel value,\ncompute the dimensions along the axes of the inertia tensor");

		gd.addSlider("Merge_distance", 0, 15, mergeDistance);
		gd.addCheckbox("Show_overlay", showOverlay);
		gd.addCheckbox("Clear_table", clearTable);

		gd.addHelp(gdsc.help.URL.FIND_FOCI);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		mergeDistance = Math.abs(gd.getNextNumber());
		showOverlay = gd.getNextBoolean();
		clearTable = gd.getNextBoolean();

		return true;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	public void run(ImageProcessor inputProcessor)
	{
		// Extract the current z-stack
		final int channel = imp.getChannel();
		final int frame = imp.getFrame();
		final int[] dimensions = imp.getDimensions();
		final int maxx = dimensions[0];
		final int maxy = dimensions[1];
		final int maxz = dimensions[3];
		final int[] maxd = new int[] { maxx, maxy, maxz };

		int[] histogram = new int[(imp.getBitDepth() == 8) ? 256 : 65536];
		int size = maxx * maxy;
		int[] image = new int[size * maxz];
		ImageStack stack = imp.getImageStack();
		for (int slice = 1, j = 0; slice <= maxz; slice++)
		{
			final int index = imp.getStackIndex(channel, slice, frame);
			ImageProcessor ip = stack.getProcessor(index);
			for (int i = 0; i < size; i++, j++)
			{
				final int value = ip.get(i);
				histogram[value]++;
				image[j] = value;
			}
		}

		// Calculate the objects (non-zero pixels)
		int min = 1;
		int max = histogram.length - 1;
		while (histogram[min] == 0 && min <= max)
			min++;
		if (min > max)
			return;
		while (histogram[max] == 0)
			max--;

		createResultsWindow();

		if (clearTable)
			clearResultsWindow();

		// For each object
		double[][] objectData = new double[max - min + 1][4];
		for (int object = min; object <= max; object++)
		{
			// Find the Centre-of-Mass
			double cx = 0, cy = 0, cz = 0;
			int n = 0;
			for (int z = 0, i = 0; z < maxz; z++)
				for (int y = 0; y < maxy; y++)
					for (int x = 0; x < maxx; x++, i++)
						if (image[i] == object)
						{
							cx += x;
							cy += y;
							cz += z;
							n++;
						}
			// Set 0.5 as the centre of the voxel mass
			cx = cx / n + 0.5;
			cy = cy / n + 0.5;
			cz = cz / n + 0.5;
			//System.out.printf("Object %d centre = %.2f,%.2f,%.2f\n", object, cx, cy, cz);
			int objectIndex = object - min;
			objectData[objectIndex][0] = cx;
			objectData[objectIndex][1] = cy;
			objectData[objectIndex][2] = cz;
			objectData[objectIndex][3] = n;
		}

		// Iteratively join closest objects
		if (mergeDistance > 0)
		{
			while (true)
			{
				// Find closest pairs
				int ii = -1, jj = -1;
				double d = Double.POSITIVE_INFINITY;
				for (int i = 1; i < objectData.length; i++)
				{
					// Skip empty objects
					if (objectData[i][3] == 0)
						continue;
					for (int j = 0; j < i; j++)
					{
						// Skip empty objects
						if (objectData[j][3] == 0)
							continue;
						double d2 = 0;
						for (int k = 0; k < 3; k++)
							d2 += (objectData[i][k] - objectData[j][k]) * (objectData[i][k] - objectData[j][k]);
						if (d > d2)
						{
							d = d2;
							ii = i;
							jj = j;
						}
					}
				}

				if (ii < 0 || Math.sqrt(d) > mergeDistance)
					break;

				// Merge
				int small = ii, big = jj;
				if (objectData[big][3] < objectData[small][3])
				{
					big = ii;
					small = jj;
				}
				//System.out.printf("Merge %d + %d\n", big+min, small+min);
				final double n1 = objectData[big][3];
				final double n2 = objectData[small][3];
				final double n = n1 + n2;
				for (int k = 0; k < 3; k++)
					objectData[big][k] = (objectData[big][k] * n1 + objectData[small][k] * n2) / n;
				objectData[big][3] = n;

				// Mark merge by setting the number of pixels to zero
				objectData[small][3] = 0;

				// If we merge an object then its image pixels must be updated with the new object value
				final int oldValue = small + min;
				final int newValue = big + min;
				for (int i = 0; i < image.length; i++)
					if (image[i] == oldValue)
						image[i] = newValue;
			}
		}

		// Output lines
		Overlay overlay = (showOverlay) ? new Overlay() : null;

		// For each object
		for (int object = min; object <= max; object++)
		{
			int objectIndex = object - min;

			// Skip empty objects
			if (objectData[objectIndex][3] == 0)
				continue;

			// Calculate the inertia tensor
			double[][] tensor = new double[3][3];

			// Remove 0.5 pixel offset for convenience
			final double cx = objectData[objectIndex][0] - 0.5;
			final double cy = objectData[objectIndex][1] - 0.5;
			final double cz = objectData[objectIndex][2] - 0.5;
			for (int z = 0, i = 0; z < maxz; z++)
				for (int y = 0; y < maxy; y++)
					for (int x = 0; x < maxx; x++, i++)
						if (image[i] == object)
						{
							final double dx = x - cx;
							final double dy = y - cy;
							final double dz = z - cz;
							final double dx2 = dx * dx;
							final double dy2 = dy * dy;
							final double dz2 = dz * dz;

							tensor[0][0] += dy2 + dz2;
							tensor[0][1] -= dx * dy;
							tensor[0][2] -= dx * dz;
							tensor[1][1] += dx2 + dz2;
							tensor[1][2] -= dy * dz;
							tensor[2][2] += dx2 + dy2;
						}

			// Inertia tensor is symmetric    
			tensor[1][0] = tensor[0][1];
			tensor[2][0] = tensor[0][2];
			tensor[2][1] = tensor[1][2];

			// Eigen decompose
			double[] values = new double[3];
			double[][] axes = new double[3][3];

			BlockRealMatrix matrix = new BlockRealMatrix(3, 3);
			for (int i = 0; i < 3; i++)
				matrix.setColumn(i, tensor[i]);

			EigenDecomposition eigen = new EigenDecomposition(matrix);
			for (int i = 0; i < 3; i++)
			{
				values[i] = eigen.getRealEigenvalue(i);
				RealVector v = eigen.getEigenvector(i);
				for (int j = 0; j < 3; j++)
					axes[i][j] = v.getEntry(j);
			}

			// Sort
			eigen_sort3(values, axes);

			// Compute the distance along each axis that is within the object.
			// Do this by constructing a line across the entire image in pixel increments, 
			// then checking the max and min pixel that are the object

			// TODO - This currently works for blobs where the COM is in the centre.
			// It does not work for objects that are joined that do not touch. It could be 
			// made far better by finding the bounding rectangle of an object and then computing
			// the longest line that can be drawn across the bounding rectangle using the 
			// tensor axes.

			final double[] com = new double[] { cx + 0.5, cy + 0.5, cz + 0.5 };

			//System.out.printf("Object %2d CoM      : %8.3f %8.3f %8.3f\n", object, com[0], com[1], com[2]);
			//for (int i = 0; i < 3; i++)
			//	System.out.printf("Object %2d Axis %d   : %8.3f %8.3f %8.3f  == %12g\n", object, i + 1, axes[i][0],
			//			axes[i][1], axes[i][2], values[i]);

			StringBuilder sb = new StringBuilder();
			sb.append(imp.getTitle());
			sb.append('\t').append(object);
			sb.append('\t').append(objectData[objectIndex][3]);
			for (int i = 0; i < 3; i++)
				sb.append('\t').append(ImageJHelper.rounded(com[i]));

			// The minor moment of inertia will be around the longest axis of the object, so start downwards
			for (int axis = 3; axis-- > 0;)
			{
				final double epsilon = 1e-6;
				final double[] direction = axes[axis];
				double s = 0;
				for (int i = 0; i < 3; i++)
				{
					if (Math.abs(direction[i]) < epsilon)
						direction[i] = 0;
					s += direction[i];
				}
				double[] lower = com.clone();
				double[] upper = com.clone();
				if (s != 0)
				{
					// Assuming unit vector then moving in increments of 1 should never skip pixels 
					// in any dimension

					// Move one way, then the other
					for (int dir : new int[] { -1, 1 })
					{
						moveDirection: for (int n = dir;; n += dir)
						{
							double[] pos = new double[] { (com[0] + n * direction[0]), (com[1] + n * direction[1]),
									(com[2] + n * direction[2]) };
							// Check image bounds
							for (int i = 0; i < 3; i++)
								if (pos[i] < 0 || pos[i] >= maxd[i])
									break moveDirection;
							final int index = ((int) pos[2]) * size + ((int) pos[1]) * maxx + ((int) pos[0]);
							// Check if we are inside the object
							if (image[index] != object)
								continue;
							if (dir == 1)
								lower = pos.clone();
							else
								upper = pos.clone();
						}
					}
				}

				// Round to half pixels (since that is our accuracy during the pixel search)
				for (int i = 0; i < 3; i++)
				{
					upper[i] = (int) upper[i] + 0.5;
					lower[i] = (int) lower[i] + 0.5;
				}

				final double dx = upper[0] - lower[0];
				final double dy = upper[1] - lower[1];
				final double dz = upper[2] - lower[2];
				final double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				//System.out.printf("Object %2d Axis %d   : %8.3f %8.3f %8.3f - %8.3f %8.3f %8.3f == %12g\n", object,
				//		axis + 1, lower[0], lower[1], lower[2], upper[0], upper[1], upper[2], d);

				for (int i = 0; i < 3; i++)
					sb.append('\t').append(ImageJHelper.rounded(lower[i]));
				for (int i = 0; i < 3; i++)
					sb.append('\t').append(ImageJHelper.rounded(upper[i]));
				sb.append('\t').append(ImageJHelper.rounded(d));

				// Draw lines on the image
				if (showOverlay)
				{
					Line roi = new Line(lower[0], lower[1], upper[0], upper[1]);
					overlay.add(roi);
				}
			}

			resultsWindow.append(sb.toString());
		}

		if (showOverlay)
			imp.setOverlay(overlay);
	}

	private void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
		{
			resultsWindow = new TextWindow(FRAME_TITLE + " Results", createResultsHeader(), "", 900, 300);
		}
	}

	private void clearResultsWindow()
	{
		if (resultsWindow != null && resultsWindow.isShowing())
		{
			resultsWindow.getTextPanel().clear();
		}
	}

	private String createResultsHeader()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Image");
		sb.append("\tObject");
		sb.append("\tArea");
		sb.append("\tcx\tcy\tcz");
		for (int i = 1; i <= 3; i++)
		{
			sb.append("\tv").append(i).append(" lx");
			sb.append("\tv").append(i).append(" ly");
			sb.append("\tv").append(i).append(" lz");
			sb.append("\tv").append(i).append(" ux");
			sb.append("\tv").append(i).append(" uy");
			sb.append("\tv").append(i).append(" uz");
			sb.append("\tv").append(i).append(" d");
		}
		return sb.toString();
	}

	/**
	 * Vector sorting routine for 3x3 set of vectors
	 * 
	 * @param w
	 *            Vector weights
	 * @param v
	 *            Vectors
	 */
	private static void eigen_sort3(double[] w, double[][] v)
	{
		int k, j, i;
		double p;

		for (i = 3; i-- > 0;)
		{
			p = w[k = i];
			for (j = i; j-- > 0;)
				if (w[j] <= p)
					p = w[k = j];
			if (k != i)
			{
				w[k] = w[i];
				w[i] = p;
				for (j = 3; j-- > 0;)
				{
					p = v[j][i];
					v[j][i] = v[j][k];
					v[j][k] = p;
				}
			}
		}
	}
}