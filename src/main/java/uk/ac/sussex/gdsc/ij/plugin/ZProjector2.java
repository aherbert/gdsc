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
package uk.ac.sussex.gdsc.ij.plugin;

import java.util.Arrays;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Measurements;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.process.ShortProcessor;

/**
 * Extend the ZProjector to support mode intensity projection.
 * <p>
 * Note: This class extends a copy of the default ImageJ ZProjector so that certain methods and properties can be
 * changed to protected from the private/default scope. Extending a copy allows easier update when the super class
 * changes.
 */
public class ZProjector2 extends ZProjectorCopy
{
    /** Use Mode projection. */
    public static final int MODE_METHOD = 6;
    /** Use Mode projection (ignoring zero from the image). */
    public static final int MODE_IGNORE_ZERO_METHOD = 7;

    /** The available projection methods. */
    public static final String[] METHODS = { "Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices",
            "Standard Deviation", "Median", "Mode", "Mode (ignore zero)" };

    @Override
    public void run(String arg)
    {
        super.run(arg);
        if (projImage != null)
        {
            // Set the display range
            ImageProcessor ip = projImage.getProcessor();
            if (ip instanceof ByteProcessor)
            {
                // We do not want the standard 0-255 range for ByteProcessor but the min/max range
                for (int c = 1; c <= projImage.getNChannels(); c++)
                {
                    final int index = projImage.getStackIndex(c, 1, 1);
                    projImage.setSliceWithoutUpdate(index);
                    ip = projImage.getProcessor();
                    final ImageStatistics stats = ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null);
                    ip.setMinAndMax(stats.min, stats.max);
                }
                projImage.setSliceWithoutUpdate(projImage.getStackIndex(1, 1, 1));
            }
            else
                projImage.resetDisplayRange();
            projImage.updateAndDraw();
        }
    }

    @Override
    protected GenericDialog buildControlDialog(int start, int stop)
    {
        final GenericDialog gd = new GenericDialog("ZProjection", IJ.getInstance());
        gd.addNumericField("Start slice:", startSlice, 0/* digits */);
        gd.addNumericField("Stop slice:", stopSlice, 0/* digits */);
        gd.addChoice("Projection type", METHODS, METHODS[method]);
        if (isHyperstack && imp.getNFrames() > 1 && imp.getNSlices() > 1)
            gd.addCheckbox("All time frames", allTimeFrames);
        gd.addHelp(uk.ac.sussex.gdsc.help.URL.UTILITY);
        return gd;
    }

    @Override
    protected String makeTitle()
    {
        String prefix = "AVG_";
        switch (method)
        {
            case SUM_METHOD:
                prefix = "SUM_";
                break;
            case MAX_METHOD:
                prefix = "MAX_";
                break;
            case MIN_METHOD:
                prefix = "MIN_";
                break;
            case SD_METHOD:
                prefix = "STD_";
                break;
            case MEDIAN_METHOD:
                prefix = "MED_";
                break;
            case MODE_METHOD:
            case MODE_IGNORE_ZERO_METHOD:
                prefix = "MOD_";
                break;
        }
        return WindowManager.makeUniqueName(prefix + imp.getTitle());
    }

    @Override
    public void doProjection()
    {
        if (imp == null)
            return;
        sliceCount = 0;
        for (int slice = startSlice; slice <= stopSlice; slice += increment)
            sliceCount++;
        if (method >= MODE_METHOD)
        {
            projImage = doModeProjection(method == MODE_IGNORE_ZERO_METHOD);
            return;
        }
        if (method == MEDIAN_METHOD)
        {
            projImage = doMedianProjection();
            return;
        }

        super.doProjection();
    }

    private interface Projector
    {
        float value(float[] values);
    }

    private ImagePlus doProjection(String name, Projector p)
    {
        IJ.showStatus("Calculating " + name + "...");
        final ImageStack stack = imp.getStack();
        // Check not an RGB stack
        int ptype;
        if (stack.getProcessor(1) instanceof ByteProcessor)
            ptype = BYTE_TYPE;
        else if (stack.getProcessor(1) instanceof ShortProcessor)
            ptype = SHORT_TYPE;
        else if (stack.getProcessor(1) instanceof FloatProcessor)
            ptype = FLOAT_TYPE;
        else
        {
            IJ.error("Z Project", "Non-RGB stack required");
            return null;
        }
        final ImageProcessor[] slices = new ImageProcessor[sliceCount];
        int index = 0;
        for (int slice = startSlice; slice <= stopSlice; slice += increment)
            slices[index++] = stack.getProcessor(slice);
        ImageProcessor ip2 = slices[0].duplicate();
        ip2 = ip2.convertToFloat();
        final float[] values = new float[sliceCount];
        final int width = ip2.getWidth();
        final int height = ip2.getHeight();
        final int inc = Math.max(height / 30, 1);
        for (int y = 0, k = 0; y < height; y++)
        {
            if (y % inc == 0)
                IJ.showProgress(y, height - 1);
            for (int x = 0; x < width; x++, k++)
            {
                for (int i = 0; i < sliceCount; i++)
                    //values[i] = slices[i].getPixelValue(x, y);
                    values[i] = slices[i].getf(k);
                //ip2.putPixelValue(x, y, p.value(values));
                ip2.setf(k, p.value(values));
            }
        }
        final ImagePlus projImage = makeOutputImage(imp, (FloatProcessor) ip2, ptype);
        IJ.showProgress(1, 1);
        return projImage;
    }

    /*
     * (non-Javadoc)
     *
     * @see ij.plugin.ZProjectorCopy#doMedianProjection()
     */
    @Override
    protected ImagePlus doMedianProjection()
    {
        // Override to change the method for accessing pixel values to getf()
        return doProjection("median", new Projector()
        {
            @Override
            public float value(float[] values)
            {
                return median(values);
            }
        });
    }

    /**
     * Do mode projection.
     *
     * @param ignoreZero
     *            the ignore zero flag
     * @return the image plus
     */
    protected ImagePlus doModeProjection(final boolean ignoreZero)
    {
        return doProjection("mode", new Projector()
        {
            @Override
            public float value(float[] values)
            {
                return getMode(values, ignoreZero);
            }
        });
    }

    /**
     * Return the mode of the array. Return the mode with the highest value in the event of a tie.
     *
     * NaN values are ignored. The mode may be NaN only if the array is zero length or contains only NaN.
     *
     * @param a
     *            Array
     * @param ignoreBelowZero
     *            Ignore all values less than or equal to zero. If no values are above zero the return is zero (not
     *            NaN).
     * @return The mode
     */
    public static float mode(float[] a, boolean ignoreBelowZero)
    {
        if (a == null || a.length == 0)
            return Float.NaN;
        return getMode(a, ignoreBelowZero);
    }

    /**
     * Return the mode of the array. Return the mode with the highest value in the event of a tie.
     *
     * NaN values are ignored. The mode may be NaN only if the array is zero length or contains only NaN.
     *
     * @param a
     *            Array
     * @param ignoreBelowZero
     *            Ignore all values less than or equal to zero. If no values are above zero the return is zero (not
     *            NaN).
     * @return The mode
     */
    private static float getMode(float[] a, boolean ignoreBelowZero)
    {
        // Assume array is not null or empty

        Arrays.sort(a);

        // NaN will be placed at the end
        final int length;
        if (Float.isNaN(a[a.length - 1]))
        {
            // Ignore NaN values
            int i = a.length;
            while (i-- > 0)
                if (!Float.isNaN(a[i]))
                    break;
            length = i + 1;
            if (length == 0)
                return Float.NaN;
        }
        else
            length = a.length;

        int modeCount = 0;
        float mode = 0;

        int i = 0;
        if (ignoreBelowZero)
        {
            while (i < length && a[i] <= 0)
                i++;
            if (length == i)
                return 0; //Float.NaN;
        }

        int currentCount = 1;
        float currentValue = a[i];
        while (++i < length)
        {
            if (a[i] != currentValue)
            {
                if (modeCount <= currentCount)
                {
                    modeCount = currentCount;
                    mode = currentValue;
                }
                currentCount = 1;
            }
            else
                currentCount++;
            currentValue = a[i];
        }
        // Do the final check
        if (modeCount <= currentCount)
        {
            modeCount = currentCount;
            mode = currentValue;
        }

        return mode;
    }
}