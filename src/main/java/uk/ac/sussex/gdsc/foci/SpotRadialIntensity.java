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
package uk.ac.sussex.gdsc.foci;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.gui.PointRoi;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.text.TextWindow;
import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.core.ij.Utils;
import uk.ac.sussex.gdsc.core.ij.process.LUTHelper;
import uk.ac.sussex.gdsc.core.utils.Maths;
import uk.ac.sussex.gdsc.core.utils.SimpleArrayUtils;
import uk.ac.sussex.gdsc.core.utils.TurboList;

/**
 * Output the radial intensity around spots within a mask region. Spots are defined using FindFoci results loaded from
 * memory.
 * <p>
 * A mask must be provided as an image.
 */
public class SpotRadialIntensity implements PlugIn
{
    private static final String TITLE = "Spot Radial Intensity";
    private static TextWindow resultsWindow = null;

    private static String resultsName = "";
    private static String maskImage = "";
    private static int distance = 10;
    private static double interval = 1;
    private static boolean showFoci = false;
    private static boolean showObjects = false;
    private static boolean showTable = true;
    private static boolean showPlot = true;

    private ImagePlus imp;
    private String prefix;

    private class Foci
    {
        final int id, object, x, y;

        public Foci(int id, int object, int x, int y)
        {
            this.id = id;
            this.object = object;
            this.x = x;
            this.y = y;
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see ij.plugin.PlugIn#run(java.lang.String)
     */
    @Override
    public void run(String arg)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

        // List the foci results
        final String[] names = FindFoci.getResultsNames();
        if (names == null || names.length == 0)
        {
            IJ.error(TITLE, "Spots must be stored in memory using the " + FindFoci.TITLE + " plugin");
            return;
        }

        imp = WindowManager.getCurrentImage();
        if (imp == null)
        {
            IJ.noImage();
            return;
        }

        // Build a list of the open images for use as a mask
        final String[] maskImageList = getImageList();
        if (maskImageList.length == 0)
        {
            IJ.error(TITLE, "No mask images");
            return;
        }

        final GenericDialog gd = new GenericDialog(TITLE);

        gd.addMessage(
                "Analyses spots within a mask region\nand computes radial intensity within the mask object region.");

        gd.addChoice("Results_name", names, resultsName);
        gd.addChoice("Mask", maskImageList, maskImage);
        gd.addNumericField("Distance", distance, 0, 6, "pixels");
        gd.addNumericField("Interval", interval, 2, 6, "pixels");
        gd.addCheckbox("Show_foci", showFoci);
        gd.addCheckbox("Show_objects", showObjects);
        gd.addCheckbox("Show_table", showTable);
        gd.addCheckbox("Show_plot", showPlot);
        gd.addHelp(uk.ac.sussex.gdsc.help.URL.FIND_FOCI);

        gd.showDialog();
        if (!gd.wasOKed())
            return;

        resultsName = gd.getNextChoice();
        maskImage = gd.getNextChoice();
        final int distance = (int) gd.getNextNumber();
        final double interval = gd.getNextNumber();
        showFoci = gd.getNextBoolean();
        showObjects = gd.getNextBoolean();
        showTable = gd.getNextBoolean();
        showPlot = gd.getNextBoolean();

        // Validate options
        if (!Maths.isFinite(distance) || !Maths.isFinite(interval) || distance <= 0 || interval <= 0 ||
                (int) (distance / interval) <= 1)
        {
            IJ.error(TITLE, "No valid distances using the given interval");
            return;
        }
        if (!(showTable || showPlot))
        {
            IJ.error(TITLE, "No output option");
            return;
        }

        if (distance != SpotRadialIntensity.distance || interval != SpotRadialIntensity.interval)
            resultsWindow = null; // Create a new window
        SpotRadialIntensity.distance = distance;
        SpotRadialIntensity.interval = interval;

        // Get the objects
        final ObjectAnalyzer objects = createObjectAnalyzer(maskImage);
        if (objects == null)
            return;

        // Get the foci
        final Foci[] foci = getFoci(resultsName, objects);
        if (foci == null)
            return;

        final PointRoi roi = (showFoci || showObjects) ? createPointRoi(foci) : null;

        if (showFoci)
            imp.setRoi(roi);
        if (showObjects)
            Utils.display(TITLE + " Objects", objects.toProcessor()).setRoi(roi);

        analyse(foci, objects);
    }

    /**
     * Build a list of all the valid mask image names.
     *
     * @return The list of images
     */
    public String[] getImageList()
    {
        final ArrayList<String> newImageList = new ArrayList<>();
        final int w = imp.getWidth();
        final int h = imp.getHeight();
        for (final int id : Utils.getIDList())
        {
            final ImagePlus imp = WindowManager.getImage(id);
            if (imp == null)
                continue;
            // Same xy dimensions
            if (imp.getWidth() != w || imp.getHeight() != h)
                continue;
            // 8/16-bit
            if (imp.getBitDepth() == 24 || imp.getBitDepth() == 32)
                continue;
            newImageList.add(imp.getTitle());
        }
        return newImageList.toArray(new String[0]);
    }

    /**
     * Creates the object analyzer from the mask image.
     *
     * @param maskImage
     *            the mask image
     * @return the object analyzer
     */
    private static ObjectAnalyzer createObjectAnalyzer(String maskImage)
    {
        final ImagePlus maskImp = WindowManager.getImage(maskImage);
        if (maskImp == null)
        {
            IJ.error(TITLE, "No mask");
            return null;
        }
        return new ObjectAnalyzer(maskImp.getProcessor());
    }

    /**
     * Gets the FindFoci results that are within objects.
     *
     * @param resultsName
     *            the results name
     * @param objects
     *            the objects
     * @return the foci
     */
    private Foci[] getFoci(String resultsName, ObjectAnalyzer objects)
    {
        final FindFociMemoryResults memoryResults = FindFoci.getResults(resultsName);
        if (memoryResults == null)
        {
            IJ.error(TITLE, "No foci with the name " + resultsName);
            return null;
        }
        final ArrayList<FindFociResult> results = memoryResults.results;
        if (results.size() == 0)
        {
            IJ.error(TITLE, "Zero foci in the results with the name " + resultsName);
            return null;
        }
        final int[] mask = objects.getObjectMask();
        final int maxx = imp.getWidth();
        final TurboList<Foci> foci = new TurboList<>(results.size());
        for (int i = 0, id = 1; i < results.size(); i++)
        {
            final FindFociResult result = results.get(i);
            final int object = mask[result.y * maxx + result.x];
            if (object != 0)
                foci.add(new Foci(id++, object, result.x, result.y));
        }
        if (foci.size() == 0)
        {
            IJ.error(TITLE, "Zero foci in the results within mask objects");
            return null;
        }
        return foci.toArray(new Foci[foci.size()]);
    }

    /**
     * Creates the point roi for the foci.
     *
     * @param foci
     *            the foci
     * @return the point roi
     */
    private static PointRoi createPointRoi(Foci[] foci)
    {
        final float[] x = new float[foci.length];
        final float[] y = new float[foci.length];
        for (int ii = 0; ii < foci.length; ii++)
        {
            final Foci f = foci[ii];
            x[ii] = f.x;
            y[ii] = f.y;
        }
        final PointRoi roi = new PointRoi(x, y);
        roi.setShowLabels(true);
        return roi;
    }

    /**
     * For each foci compute the radial mean of all pixels within the same mask object.
     *
     * @param foci
     *            the foci
     * @param objects
     *            the objects
     */
    private void analyse(Foci[] foci, ObjectAnalyzer objects)
    {
        if (showTable)
            createResultsWindow();
        final StringBuilder sb = new StringBuilder();

        final int[] mask = objects.getObjectMask();
        final float[] background = getBackground(objects);

        // For radial intensity
        final float[] pixels = (float[]) imp.getProcessor().toFloat(0, null).getPixels();
        final int limit = distance * distance;
        final int maxBin = (int) (distance / interval);
        final int[] count = new int[maxBin];
        final double[] sum = new double[maxBin];
        // The lower limit of the squared distance for each bin
        final double[] distances = new double[maxBin];
        for (int i = 0; i < distances.length; i++)
            distances[i] = Maths.pow2(i * interval);

        // Table of dx^2
        final int[] dx2 = new int[2 * distance + 1];

        // Plot of each radial intensity
        final Plot plot = (showPlot) ? new Plot(TITLE, "Distance", "Average") : null;
        final double[] xAxis = SimpleArrayUtils.newArray(maxBin, 0, interval);
        final double[] yAxis = new double[xAxis.length];
        final LUT lut = LUTHelper.createLUT(LUTHelper.LutColour.FIRE_GLOW);

        final int w = imp.getWidth();
        final int upperx = imp.getWidth() - 1;
        final int uppery = imp.getHeight() - 1;
        for (int ii = 0; ii < foci.length; ii++)
        {
            final Foci f = foci[ii];

            // Find limits && clip
            final int minx = Math.max(0, f.x - distance);
            final int maxx = Math.min(upperx, f.x + distance);
            final int miny = Math.max(0, f.y - distance);
            final int maxy = Math.min(uppery, f.y + distance);

            // Table of dx^2
            for (int x = minx, j = 0; x <= maxx; x++, j++)
                dx2[j] = Maths.pow2(f.x - x);

            // Reset radial stats
            for (int i = 0, len = count.length; i < len; i++)
            {
                count[i] = 0;
                sum[i] = 0;
            }

            // For all pixels
            for (int y = miny; y <= maxy; y++)
            {
                final int dy2 = Maths.pow2(f.y - y);
                for (int x = minx, i = y * w + minx, j = 0; x <= maxx; x++, i++, j++)
                    // If correct object
                    if (mask[i] == f.object)
                    {
                        // Get distance squared
                        final int d2 = dy2 + dx2[j];
                        if (d2 < limit)
                        {
                            // Put in radial stats
                            //int bin = (int) (Math.sqrt(d2) / interval);
                            // Q. Faster than sqrt?
                            int bin = Arrays.binarySearch(distances, d2);
                            if (bin < 0)
                                bin = -bin - 2; // The bin is the (insertion point)-1 => -(bin+1) - 1
                            //if (bin != (int) (Math.sqrt(d2) / interval))
                            //	System.out.println("bin error");
                            //if (bin == maxBin)
                            //	System.out.printf("[%d] %d  %d,%d - %d,%d = %f\n", f.id, f.object, f.x, f.y, x, y,
                            //			Math.sqrt(d2));
                            count[bin]++;
                            sum[bin] += pixels[i];
                        }
                    }
            }

            if (showTable)
            {
                // Table of results
                sb.setLength(0);
                sb.append(prefix);
                sb.append('\t').append(f.id);
                sb.append('\t').append(f.object);
                final float b = background[f.object];
                sb.append('\t').append(Utils.rounded(b));
                sb.append('\t').append(f.x);
                sb.append('\t').append(f.y);
                for (int i = 0; i < maxBin; i++)
                {
                    final double v = sum[i] / count[i] - b * count[i];
                    yAxis[i] = v;
                    sb.append('\t').append(Utils.rounded(v));
                }
                for (int i = 0; i < maxBin; i++)
                    sb.append('\t').append(count[i]);

                resultsWindow.append(sb.toString());
            }

            // Add to plot
            if (showPlot)
            {
                int xLimit = 0;
                for (int i = 0; i < maxBin; i++)
                    if (count[i] != 0)
                        xLimit = i + 1;

                plot.setColor(LUTHelper.getColour(lut, ii + 1, 0, foci.length));
                if (xLimit < xAxis.length)
                    plot.addPoints(Arrays.copyOf(xAxis, xLimit), Arrays.copyOf(yAxis, xLimit), Plot.LINE);
                else
                    plot.addPoints(xAxis, yAxis, Plot.LINE);
            }
        }

        if (showPlot)
        {
            plot.setColor(Color.BLACK);
            Utils.display(TITLE, plot);
            plot.setLimitsToFit(true); // Seems to only work after drawing
        }
    }

    /**
     * Gets the background using a 3x3 block mean
     *
     * @param objects
     *            the objects
     * @return the background
     */
    private float[] getBackground(ObjectAnalyzer objects)
    {
        final ImageProcessor ip = imp.getProcessor().duplicate();
        ip.convolve3x3(SimpleArrayUtils.newIntArray(9, 1));
        final int[] mask = objects.getObjectMask();
        final int maxObject = objects.getMaxObject();
        final float[] background = new float[maxObject + 1];
        Arrays.fill(background, Float.POSITIVE_INFINITY);
        for (int i = mask.length; i-- > 0;)
        {
            final int id = mask[i];
            if (id != 0)
            {
                final float v = ip.getf(i);
                if (background[id] > v)
                    background[id] = v;
            }
        }
        return background;
    }

    private void createResultsWindow()
    {
        if (resultsWindow == null || !resultsWindow.isShowing())
            resultsWindow = new TextWindow(TITLE + " Summary", createResultsHeader(), "", 700, 300);
        prefix = String.format("%s (C=%d,Z=%d,T=%d)", imp.getTitle(), imp.getChannel(), imp.getSlice(), imp.getFrame());
    }

    private static String createResultsHeader()
    {
        final StringBuilder sb = new StringBuilder();
        sb.append("Image");
        sb.append("\tID");
        sb.append("\tObject");
        sb.append("\tBackground");
        sb.append("\tx");
        sb.append("\ty");
        final int maxBin = (int) (distance / interval);
        for (int i = 0; i < maxBin; i++)
        {
            final double low = interval * i;
            final double high = interval * (i + 1);
            sb.append("\tAv ").append(Utils.rounded(low));
            sb.append("-").append(Utils.rounded(high));
        }
        for (int i = 0; i < maxBin; i++)
        {
            final double low = interval * i;
            final double high = interval * (i + 1);
            sb.append("\tN ").append(Utils.rounded(low));
            sb.append("-").append(Utils.rounded(high));
        }
        return sb.toString();
    }
}