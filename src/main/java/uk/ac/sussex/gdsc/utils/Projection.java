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
package uk.ac.sussex.gdsc.utils;

import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import uk.ac.sussex.gdsc.UsageTracker;

/**
 * Provides methods to filter the image in the z-axis for better 3-D projections.
 * Performs a local maxima filter in the z-axis resulting in a new image of the same
 * dimensions with a black background for non-maximal pixels.
 */
public class Projection implements PlugInFilter
{
    private ImagePlus imp;

    /*
     * (non-Javadoc)
     *
     * @see ij.plugin.filter.PlugInFilter#setup(java.lang.String, ij.ImagePlus)
     */
    @Override
    public int setup(String arg, ImagePlus imp)
    {
        UsageTracker.recordPlugin(this.getClass(), arg);

        if (imp == null || imp.getNSlices() == 1)
            return DONE;
        this.imp = imp;
        return DOES_ALL;
    }

    /*
     * (non-Javadoc)
     *
     * @see ij.plugin.filter.PlugInFilter#run(ij.process.ImageProcessor)
     */
    @Override
    public void run(ImageProcessor inputProcessor)
    {
        final ImageStack stack = imp.getStack();

        ImageProcessor ip1 = null;
        ImageProcessor ip2 = null;
        ImageProcessor ip3 = stack.getProcessor(1);

        for (int n = 2; n <= imp.getNSlices(); n++)
        {
            // Roll over processors
            ip1 = ip2;
            ip2 = ip3;
            ip3 = stack.getProcessor(n);

            process(n, ip1, ip2, ip3);
        }

        process(imp.getNSlices() + 1, ip2, ip3, null);
    }

    private static void process(int n, ImageProcessor ip1, ImageProcessor ip2, ImageProcessor ip3)
    {
        if (ip2 == null)
            return;

        if (ip1 != null)
        {
            // Check for maxima going backward
            System.out.printf("%d < %d : ", n - 2, n - 1);
            for (int i = ip2.getPixelCount(); i-- > 0;)
                if (ip1.get(i) > ip2.get(i))
                    ip2.set(i, 0);
        }

        if (ip3 != null)
        {
            // Check for maxima going forward
            System.out.printf("%d > %d", n - 1, n);
            for (int i = ip2.getPixelCount(); i-- > 0;)
                if (ip3.get(i) > ip2.get(i))
                    ip2.set(i, 0);
        }

        System.out.printf("\n");
    }
}
