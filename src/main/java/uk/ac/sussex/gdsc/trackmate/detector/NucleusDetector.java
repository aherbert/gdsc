/*-
 * #%L
 * Genome Damage and Stability Centre ImageJ Plugins
 *
 * Software for microscopy image analysis
 * %%
 * Copyright (C) 2011 - 2019 Alex Herbert
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

package uk.ac.sussex.gdsc.trackmate.detector;

import uk.ac.sussex.gdsc.core.utils.MathUtils;
import uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn;
import uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn.Nucleus;
import uk.ac.sussex.gdsc.foci.NucleiOutline_PlugIn.Settings;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer;
import uk.ac.sussex.gdsc.foci.ObjectAnalyzer.ObjectCentre;
import uk.ac.sussex.gdsc.foci.ObjectEroder;
import uk.ac.sussex.gdsc.foci.ObjectExpander;

import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.detection.SpotDetector;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Detector that delegates work to the {@link NucleiOutline_PlugIn}.
 *
 * <p>Detection is performed using: a Gaussian smoothing filter, threshold mask, watershed division
 * of large objects and filtering of small objects.
 *
 * @param <T> the pixels type
 */
public class NucleusDetector<T extends RealType<T> & NativeType<T>> implements SpotDetector<T> {
  /** The feature key for the mean inside the nucleus. */
  public static final String NUCLEUS_MEAN_INSIDE = "MEAN_INSIDE";
  /** The feature key for the mean outside the nucleus. */
  public static final String NUCLEUS_MEAN_OUTSIDE = "MEAN_OUTSIDE";

  /** The detector settings. */
  private final Settings detectorSettings;
  /** The image. */
  private final RandomAccessible<T> img;
  /** The analysis image. */
  private final RandomAccessible<T> analysisImg;
  /** The interval. */
  private final Interval interval;
  /** The pixel sizes in the 3 dimensions. */
  private final double[] calibration;
  /** Holder for the results of detection. */
  private List<Spot> spots;
  /** Error message holder. */
  private String errorMessage;
  /** Holder for the processing time. */
  private long processingTime;

  /**
   * Specialisation of the Spot class to store the Nucleus ROI.
   */
  public static class NucleusSpot extends Spot {
    /** The roi. */
    private final Roi roi;

    /**
     * Create a new instance.
     *
     * @param x the x
     * @param y the y
     * @param z the z
     * @param radius the radius
     * @param quality the quality
     * @param roi the roi
     */
    public NucleusSpot(double x, double y, double z, double radius, double quality, Roi roi) {
      super(x, y, z, radius, quality);
      this.roi = roi;
    }

    /**
     * Gets the roi.
     *
     * @return the roi
     */
    public Roi getRoi() {
      return roi;
    }
  }

  /**
   * Create a new instance.
   *
   * @param detectorSettings the detector settings
   * @param img the image
   * @param analysisImg the analysis image (can be null)
   * @param interval the interval defining the region to process (currently ignored)
   * @param calibration the pixel sizes in the 3 dimensions
   */
  public NucleusDetector(Settings detectorSettings, RandomAccessible<T> img,
      RandomAccessible<T> analysisImg, Interval interval, double[] calibration) {
    this.detectorSettings = detectorSettings;
    this.img = img;
    this.analysisImg = analysisImg;
    this.interval = interval;
    this.calibration = calibration;
  }

  @Override
  public boolean checkInput() {
    if (img == null) {
      errorMessage = "Image is null.";
      return false;
    }
    if (img.numDimensions() < 2 || img.numDimensions() > 3) {
      errorMessage = "Image must be 2D or 3D, got " + img.numDimensions() + "D.";
      return false;
    }
    return true;
  }

  @Override
  public boolean process() {
    final long start = System.currentTimeMillis();

    // Crop to ROI
    final long[] min = new long[interval.numDimensions()];
    final long[] max = new long[min.length];
    interval.min(min);
    interval.max(max);
    // Only process central slice of z stack
    if (min.length == 3) {
      min[2] = max[2] = (max[2] - min[2]) / 2;
    }

    // Delegate detection work to the ImageJ plugin
    final ImageProcessor ip1 = getProcessor(img, min, max);
    final ImageProcessor ip2 = getProcessor(analysisImg, min, max);
    final ObjectAnalyzer objectAnalyser = NucleiOutline_PlugIn.identifyObjects(detectorSettings,
        calibration[0] * calibration[1], ip1);
    final Nucleus[] nuclei = NucleiOutline_PlugIn.toNuclei(objectAnalyser);

    double[] meanInside;
    double[] meanOutside;
    if (ip2 != null) {
      final int[] mask = objectAnalyser.getObjectMask();
      meanInside = computeMeanInside(ip2, mask.clone());
      meanOutside = computeMeanOutside(ip2, mask);
    } else {
      meanInside = meanOutside = null;
    }

    // Pre-compute z
    final double z = (min.length == 3) ? min[2] * calibration[2] : 0;

    spots = new ArrayList<>(nuclei.length);
    for (int i = 0; i < nuclei.length; i++) {
      final Nucleus nucleus = nuclei[i];
      final ObjectCentre centre = nucleus.getObjectCentre();
      // Shift to the crop position and convert to image coordinates
      final double x = (centre.getCentreX() + min[0]) * calibration[0];
      final double y = (centre.getCentreY() + min[1]) * calibration[1];
      final double radius = nucleus.getEstimatedRadius() * calibration[0];
      // Q. Add a metric that can be used for filtering?
      final double quality = 1;
      // We cannot store anything in the Spot other than Double so specialise to extra information.
      final Roi roi = nucleus.getRoi();
      final Rectangle bounds = roi.getBounds();
      roi.setLocation(bounds.x + (int) min[0], bounds.y + (int) min[1]);
      final NucleusSpot spot = new NucleusSpot(x, y, z, radius, quality, roi);
      if (meanInside != null) {
        spot.putFeature(NUCLEUS_MEAN_INSIDE, meanInside[i + 1]);
      }
      if (meanOutside != null) {
        spot.putFeature(NUCLEUS_MEAN_OUTSIDE, meanOutside[i + 1]);
      }
      spots.add(spot);
    }

    this.processingTime = System.currentTimeMillis() - start;
    return true;
  }

  /**
   * Gets a single plane ImageProcessor from the image.
   *
   * @param <T> the pixels type
   * @param img the image (or null)
   * @param min the min
   * @param max the max
   * @return the processor (or null)
   */
  private static <T extends RealType<T> & NativeType<T>> ImageProcessor
      getProcessor(RandomAccessible<T> img, long[] min, long[] max) {
    if (img == null) {
      return null;
    }
    final IntervalView<T> crop = Views.interval(img, min, max);
    final ImagePlus imp = ImageJFunctions.wrap(crop, "CropRegion");
    return imp.getProcessor();
  }

  /**
   * Compute the mean inside each object following erosion.
   *
   * <p>The mean for each object is accessed using the object ID (which is 1-based).
   *
   * @param ip the image to analyse
   * @param mask the mask (modified in place)
   * @return the mean
   */
  private double[] computeMeanInside(ImageProcessor ip, int[] mask) {
    final int maxObject = MathUtils.max(mask);
    final ColorProcessor cp = new ColorProcessor(ip.getWidth(), ip.getHeight(), mask);
    new ObjectEroder(cp, true).erode(detectorSettings.getErosion());
    return computeMean(ip, maxObject, mask);
  }

  /**
   * Compute the mean outside each object following expansion.
   *
   * <p>The mean for each object is accessed using the object ID (which is 1-based).
   *
   * @param ip the image to analyse
   * @param mask the mask (modified in place)
   * @return the mean
   */
  private double[] computeMeanOutside(ImageProcessor ip, int[] mask) {
    final int maxObject = MathUtils.max(mask);
    final ColorProcessor cp = new ColorProcessor(ip.getWidth(), ip.getHeight(), mask);
    final ObjectExpander expander = new ObjectExpander(cp);
    expander.expand(detectorSettings.getExpansionInner());
    final int[] innerMask = mask.clone();
    // Ensure some analysis is done
    expander.expand(Math.max(detectorSettings.getExpansion(), 1));
    for (int i = 0; i < mask.length; i++) {
      mask[i] -= innerMask[i];
    }
    return computeMean(ip, maxObject, mask);
  }

  /**
   * Compute the mean of each object in the mask.
   *
   * @param ip the image
   * @param maxObject the max object
   * @param mask the mask
   * @return the mean
   */
  private static double[] computeMean(ImageProcessor ip, int maxObject, int[] mask) {
    final double[] mean = new double[maxObject + 1];
    final int[] count = new int[maxObject + 1];
    for (int i = 1; i < mask.length; i++) {
      final int object = mask[i];
      count[object]++;
      mean[object] += ip.getf(i);
    }
    for (int i = 1; i < mean.length; i++) {
      mean[i] = MathUtils.div0(mean[i], count[i]);
    }
    return mean;
  }

  @Override
  public List<Spot> getResult() {
    return spots;
  }

  @Override
  public String getErrorMessage() {
    return errorMessage;
  }

  @Override
  public long getProcessingTime() {
    return processingTime;
  }
}
