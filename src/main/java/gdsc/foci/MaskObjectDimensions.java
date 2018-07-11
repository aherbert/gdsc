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
package gdsc.foci;

import java.util.Arrays;
import java.util.Comparator;

import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.RealVector;

import gdsc.UsageTracker;
import gdsc.core.ij.Utils;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

/**
 * For each unique pixel value in the mask (defining an object), analyse the pixels
 * values and calculate the inertia tensor. Then produce the dimensions of the object
 * on the axes of the moments of inertia.
 */
public class MaskObjectDimensions implements PlugInFilter
{
	/** The plugin title. */
	private static final String TITLE = "Mask Object Dimensions";

	private final int flags = DOES_16 + DOES_8G;
	private ImagePlus imp;

	private static String[] sortMethods = new String[] { "Value", "Area", "CoM" };
	private static int SORT_VALUE = 0;
	private static int SORT_AREA = 1;
	private static int SORT_COM = 2;

	private static double mergeDistance = 0;
	private static boolean showOverlay = true;
	private static boolean clearTable = true;
	private static boolean showVectors = false;
	private static int sortMethod = SORT_VALUE;

	private static TextWindow resultsWindow = null;

	private class MaskObject
	{
		double cx, cy, cz;
		int n;
		int[] values;
		int[] lower, upper;

		public MaskObject(double cx, double cy, double cz, int n, int value)
		{
			this.cx = cx;
			this.cy = cy;
			this.cz = cz;
			this.n = n;
			values = new int[] { value };
		}

		public int getValue()
		{
			return values[0];
		}

		public double distance2(MaskObject that)
		{
			final double dx = this.cx - that.cx;
			final double dy = this.cy - that.cy;
			final double dz = this.cz - that.cz;
			return dx * dx + dy * dy + dz * dz;
		}

		public void merge(MaskObject that)
		{
			final int n2 = this.n + that.n;
			this.cx = (this.cx * this.n + that.cx * that.n) / n2;
			this.cy = (this.cy * this.n + that.cy * that.n) / n2;
			this.cz = (this.cz * this.n + that.cz * that.n) / n2;
			this.n = n2;

			// Merge the values
			final int[] newValues = new int[this.values.length + that.values.length];
			System.arraycopy(this.values, 0, newValues, 0, this.values.length);
			System.arraycopy(that.values, 0, newValues, this.values.length, that.values.length);
			this.values = newValues;

			that.n = 0;
		}

		public void initialiseBounds()
		{
			lower = new int[] { (int) cx, (int) cy, (int) cz };
			upper = lower.clone();
		}

		public void updateBounds(int x, int y, int z)
		{
			if (lower[0] > x)
				lower[0] = x;
			if (lower[1] > y)
				lower[1] = y;
			if (lower[2] > z)
				lower[2] = z;
			if (upper[0] < x)
				upper[0] = x;
			if (upper[1] < y)
				upper[1] = y;
			if (upper[2] < z)
				upper[2] = z;
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
	 */
	@Override
	public int setup(String arg, ImagePlus imp)
	{
		UsageTracker.recordPlugin(this.getClass(), arg);

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

	private static boolean showDialog()
	{
		final GenericDialog gd = new GenericDialog(TITLE);

		gd.addMessage(
				"For each object defined with a unique pixel value,\ncompute the dimensions along the axes of the inertia tensor");

		gd.addSlider("Merge_distance", 0, 15, mergeDistance);
		gd.addCheckbox("Show_overlay", showOverlay);
		gd.addCheckbox("Clear_table", clearTable);
		gd.addCheckbox("Show_vectors", showVectors);
		gd.addChoice("Sort_method", sortMethods,
				sortMethods[Math.max(0, Math.min(sortMethod, sortMethods.length - 1))]);

		gd.addHelp(gdsc.help.URL.FIND_FOCI);
		gd.showDialog();

		if (gd.wasCanceled())
			return false;

		mergeDistance = Math.abs(gd.getNextNumber());
		showOverlay = gd.getNextBoolean();
		clearTable = gd.getNextBoolean();
		showVectors = gd.getNextBoolean();
		sortMethod = gd.getNextChoiceIndex();

		return true;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
	 */
	@Override
	public void run(ImageProcessor inputProcessor)
	{
		// Extract the current z-stack
		final int channel = imp.getChannel();
		final int frame = imp.getFrame();
		final int[] dimensions = imp.getDimensions();
		final int maxx = dimensions[0];
		final int maxy = dimensions[1];
		final int maxz = dimensions[3];

		final int[] histogram = new int[(imp.getBitDepth() == 8) ? 256 : 65536];
		final int size = maxx * maxy;
		final int[] image = new int[size * maxz];
		final ImageStack stack = imp.getImageStack();
		for (int slice = 1, j = 0; slice <= maxz; slice++)
		{
			final int index = imp.getStackIndex(channel, slice, frame);
			final ImageProcessor ip = stack.getProcessor(index);
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
		MaskObject[] objects = new MaskObject[max - min + 1];
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
			objects[object - min] = new MaskObject(cx, cy, cz, n, object);
		}

		// Iteratively join closest objects
		if (mergeDistance > 0)
			while (true)
			{
				// Find closest pairs
				int ii = -1, jj = -1;
				double d = Double.POSITIVE_INFINITY;
				for (int i = 1; i < objects.length; i++)
				{
					// Skip empty objects
					if (objects[i].n == 0)
						continue;
					for (int j = 0; j < i; j++)
					{
						// Skip empty objects
						if (objects[j].n == 0)
							continue;
						final double d2 = objects[i].distance2(objects[j]);
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
				MaskObject big, small;
				if (objects[jj].n < objects[ii].n)
				{
					big = objects[ii];
					small = objects[jj];
				}
				else
				{
					big = objects[jj];
					small = objects[ii];
				}

				//System.out.printf("Merge %d + %d\n", big+min, small+min);
				big.merge(small);

				// If we merge an object then its image pixels must be updated with the new object value
				final int oldValue = small.getValue();
				final int newValue = big.getValue();
				for (int i = 0; i < image.length; i++)
					if (image[i] == oldValue)
						image[i] = newValue;
			}

		// Remove merged objects and map the value to the new index
		final int[] objectMap = new int[max + 1];
		int newLength = 0;
		for (int i = 0; i < objects.length; i++)
		{
			if (objects[i].n == 0)
				continue;
			objects[newLength] = objects[i];
			objectMap[objects[i].getValue()] = newLength;
			newLength++;
		}
		objects = Arrays.copyOf(objects, newLength);

		// Output lines
		final Overlay overlay = (showOverlay) ? new Overlay() : null;

		// Compute the bounding box for each object. This increases the speed of processing later
		for (final MaskObject o : objects)
			o.initialiseBounds();
		for (int z = 0, i = 0; z < maxz; z++)
			for (int y = 0; y < maxy; y++)
				for (int x = 0; x < maxx; x++, i++)
					if (image[i] != 0)
						objects[objectMap[image[i]]].updateBounds(x, y, z);

		// Sort the objects
		Comparator<MaskObject> c = null;
		if (sortMethod == SORT_COM)
			c = new Comparator<MaskObjectDimensions.MaskObject>()
			{
				@Override
				public int compare(MaskObject o1, MaskObject o2)
				{
					if (o1.cx < o2.cx)
						return -1;
					if (o1.cx > o2.cx)
						return 1;
					if (o1.cy < o2.cy)
						return -1;
					if (o1.cy > o2.cy)
						return 1;
					if (o1.cz < o2.cz)
						return -1;
					if (o1.cz > o2.cz)
						return 1;
					return 0;
				}
			};
		else if (sortMethod == SORT_AREA)
			c = new Comparator<MaskObjectDimensions.MaskObject>()
			{
				@Override
				public int compare(MaskObject o1, MaskObject o2)
				{
					if (o1.n < o2.n)
						return -1;
					if (o1.n > o2.n)
						return 1;
					return 0;
				}
			};
		else
		// if (sortMethod == SORT_VALUE)
		{
			// Do nothing since this is the default
		}
		if (c != null)
			Arrays.sort(objects, c);

		// Get the calibrated units
		final Calibration cal = imp.getCalibration();
		String units = cal.getUnits();
		final double calx, caly, calz;
		if (cal.getXUnit().equals(cal.getYUnit()) && cal.getXUnit().equals(cal.getZUnit()))
		{
			calx = cal.pixelWidth;
			caly = cal.pixelHeight;
			calz = cal.pixelDepth;
		}
		else
		{
			calx = caly = calz = 1;
			units = "px";
		}

		// For each object
		for (final MaskObject object : objects)
		{
			final int objectValue = object.getValue();

			// Set bounds
			final int[] mind = object.lower;
			final int[] maxd = object.upper.clone();
			// Increase the upper bounds by 1 to allow < and >= range checks
			for (int i = 0; i < 3; i++)
				maxd[i] += 1;

			// Calculate the inertia tensor
			final double[][] tensor = new double[3][3];

			// Remove 0.5 pixel offset for convenience
			final double cx = object.cx - 0.5;
			final double cy = object.cy - 0.5;
			final double cz = object.cz - 0.5;
			for (int z = mind[2]; z < maxd[2]; z++)
				for (int y = mind[1]; y < maxd[1]; y++)
					for (int x = mind[0], i = z * size + y * maxx + mind[0]; x < maxd[0]; x++, i++)
						if (image[i] == objectValue)
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
			final double[] eigenValues = new double[3];
			final double[][] eigenVectors = new double[3][3];

			final BlockRealMatrix matrix = new BlockRealMatrix(3, 3);
			for (int i = 0; i < 3; i++)
				matrix.setColumn(i, tensor[i]);

			final EigenDecomposition eigen = new EigenDecomposition(matrix);
			for (int i = 0; i < 3; i++)
			{
				eigenValues[i] = eigen.getRealEigenvalue(i);
				final RealVector v = eigen.getEigenvector(i);
				for (int j = 0; j < 3; j++)
					eigenVectors[i][j] = v.getEntry(j);
			}

			// Sort
			eigen_sort3(eigenValues, eigenVectors);

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

			final StringBuilder sb = new StringBuilder();
			sb.append(imp.getTitle());
			sb.append('\t').append(units);
			Arrays.sort(object.values);
			sb.append('\t').append(Arrays.toString(object.values));
			sb.append('\t').append(object.n);
			for (int i = 0; i < 3; i++)
				sb.append('\t').append(Utils.rounded(com[i]));

			// The minor moment of inertia will be around the longest axis of the object, so start downwards
			for (int axis = 3; axis-- > 0;)
			{
				final double epsilon = 1e-6;
				final double[] direction = eigenVectors[axis];
				double s = 0;
				double longest = 0; // Used to normalise the longest dimension to 1
				for (int i = 0; i < 3; i++)
				{
					final double v = Math.abs(direction[i]);
					if (v < epsilon)
						direction[i] = 0;
					if (longest < v)
						longest = v;
					s += direction[i];
				}
				final double[] direction1 = com.clone();
				final double[] direction2 = com.clone();
				if (s != 0)
				{
					// Assuming unit vector then moving in increments of 1 should never skip pixels
					// in any dimension. Normalise to 1 in the longest dimension should still be OK.
					for (int i = 0; i < 3; i++)
					{
						direction[i] /= longest;
						if (direction[i] > 1)
							direction[i] = 1;
					}

					final double[] pos = new double[3];

					// Move one way, then the other
					for (final int dir : new int[] { -1, 1 })
					{
						final double[] tmp = (dir == 1) ? direction1 : direction2;
						moveDirection: for (int n = dir;; n += dir)
						{
							for (int i = 0; i < 3; i++)
							{
								pos[i] = com[i] + n * direction[i];
								// Check bounds
								if (pos[i] < mind[i] || pos[i] >= maxd[i])
									break moveDirection;
							}
							final int index = ((int) pos[2]) * size + ((int) pos[1]) * maxx + ((int) pos[0]);
							// Check if we are inside the object
							if (image[index] != objectValue)
								continue;
							for (int i = 0; i < 3; i++)
								tmp[i] = pos[i];
						}
					}
				}

				// Round to half pixels (since that is our accuracy during the pixel search)
				for (int i = 0; i < 3; i++)
				{
					direction2[i] = (int) direction2[i] + 0.5;
					direction1[i] = (int) direction1[i] + 0.5;
				}

				if (showVectors)
				{
					for (int i = 0; i < 3; i++)
						sb.append('\t').append(Utils.rounded(direction1[i]));
					for (int i = 0; i < 3; i++)
						sb.append('\t').append(Utils.rounded(direction2[i]));
				}

				// Distance in pixels
				double dx = direction2[0] - direction1[0];
				double dy = direction2[1] - direction1[1];
				double dz = direction2[2] - direction1[2];
				double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				//System.out.printf("Object %2d Axis %d   : %8.3f %8.3f %8.3f - %8.3f %8.3f %8.3f == %12g\n", object,
				//		axis + 1, lower[0], lower[1], lower[2], upper[0], upper[1], upper[2], d);
				sb.append('\t').append(Utils.rounded(d));

				// Calibrated length
				dx *= calx;
				dy *= caly;
				dz *= calz;
				d = Math.sqrt(dx * dx + dy * dy + dz * dz);
				sb.append('\t').append(Utils.rounded(d));

				// Draw lines on the image
				if (showOverlay)
				{
					final Line roi = new Line(direction1[0], direction1[1], direction2[0], direction2[1]);
					overlay.add(roi);
				}
			}

			resultsWindow.append(sb.toString());
		}

		if (showOverlay)
			imp.setOverlay(overlay);
	}

	private static void createResultsWindow()
	{
		if (resultsWindow == null || !resultsWindow.isShowing())
			resultsWindow = new TextWindow(TITLE + " Results", createResultsHeader(), "", 900, 300);
	}

	private static void clearResultsWindow()
	{
		if (resultsWindow != null && resultsWindow.isShowing())
			resultsWindow.getTextPanel().clear();
	}

	private static String createResultsHeader()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append("Image");
		sb.append("\tUnits");
		sb.append("\tObject");
		sb.append("\tArea");
		sb.append("\tcx\tcy\tcz");
		for (int i = 1; i <= 3; i++)
		{
			if (showVectors)
			{
				sb.append("\tv").append(i).append(" lx");
				sb.append("\tv").append(i).append(" ly");
				sb.append("\tv").append(i).append(" lz");
				sb.append("\tv").append(i).append(" ux");
				sb.append("\tv").append(i).append(" uy");
				sb.append("\tv").append(i).append(" uz");
			}
			sb.append("\tv").append(i).append(" len (px)");
			sb.append("\tv").append(i).append(" len (units)");
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
