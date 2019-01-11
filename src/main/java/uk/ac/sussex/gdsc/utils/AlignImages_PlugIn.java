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

package uk.ac.sussex.gdsc.utils;

import uk.ac.sussex.gdsc.UsageTracker;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Measurements;
import ij.plugin.PlugIn;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Rectangle;
import java.util.ArrayList;

//@formatter:off
/**
 * Aligns an image stack to a reference image using XY translation to maximise the normalised
 * correlation. Takes in:
 *
 * <ul>
 * <li>The reference image</li>
 * <li>The reference image mask (states which pixels are important)</li>
 * <li>The image/stack to align</li>
 * <li>Max/Min values for the X and Y translation</li>
 * </ul>
 *
 * <p>Reference image and mask must be the same width/height.
 *
 * <p>For each translation:
 *
 * <ul>
 * <li>Move the image to align</li>
 * <li>Calculate the correlation between images
 * (ignore pixels not in the reference mask)</li>
 * <li>Report alignment stats</li>
 * </ul>
 *
 * <p>Output new stack with the best alignment with optional sub-pixel accuracy.
 */
//@formatter:on
public class AlignImages_PlugIn implements PlugIn {
  private static final String TITLE = "Align Images";
  private static int myminXShift = -10;
  private static int mymaxXShift = 10;
  private static int myminYShift = -10;
  private static int mymaxYShift = 10;
  private static final String[] subPixelMethods = new String[] {"None", "Cubic", "Gaussian"};
  private static int subPixelMethod = 2;
  private static final String[] methods = ImageProcessor.getInterpolationMethods();
  private static int interpolationMethod = ImageProcessor.NONE;

  private static final String NONE = "[None]";
  private static String reference = "";
  private static String referenceMask = NONE;
  private static String target = "";
  private static boolean showCorrelationImage;
  private static boolean clipOutput;

  /** Ask for parameters and then execute. */
  @Override
  public void run(String arg) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    final String[] imageList = getImagesList();

    if (imageList.length < 1) {
      IJ.showMessage("Error", "Only 8-bit and 16-bit images are supported");
      return;
    }

    if (!showDialog(imageList)) {
      return;
    }

    final ImagePlus refImp = WindowManager.getImage(reference);
    final ImageProcessor maskIp = getProcessor(referenceMask);
    final ImagePlus targetImp = WindowManager.getImage(target);

    final ImagePlus alignedImp =
        exec(refImp, maskIp, targetImp, myminXShift, mymaxXShift, myminYShift, mymaxYShift,
            subPixelMethod, interpolationMethod, showCorrelationImage, clipOutput);

    if (alignedImp != null) {
      alignedImp.show();
    }
  }

  private static ImageProcessor getProcessor(String title) {
    final ImagePlus imp = WindowManager.getImage(title);
    if (imp != null) {
      return imp.getProcessor();
    }
    return null;
  }

  private static String[] getImagesList() {
    // Find the currently open images
    final ArrayList<String> newImageList = new ArrayList<>();

    for (final int id : uk.ac.sussex.gdsc.core.ij.ImageJUtils.getIdList()) {
      final ImagePlus imp = WindowManager.getImage(id);

      // Image must be 8-bit/16-bit
      if (imp != null && (imp.getType() == ImagePlus.GRAY8 || imp.getType() == ImagePlus.GRAY16
          || imp.getType() == ImagePlus.GRAY32)) {
        // Check it is not one the result images
        final String imageTitle = imp.getTitle();
        newImageList.add(imageTitle);
      }
    }

    return newImageList.toArray(new String[0]);
  }

  private static boolean showDialog(String[] imageList) {
    final GenericDialog gd = new GenericDialog(TITLE);

    if (!contains(imageList, reference)) {
      reference = imageList[0];
      referenceMask = NONE;
    }

    // Add option to have no mask
    final String[] maskList = new String[imageList.length + 1];
    maskList[0] = NONE;
    for (int i = 0; i < imageList.length; i++) {
      maskList[i + 1] = imageList[i];
    }
    if (!contains(maskList, target)) {
      target = maskList[0];
    }

    gd.addMessage("Align target image stack to a reference using\n"
        + "correlation within a translation range. Ignore pixels\n"
        + "in the reference using a mask.");

    gd.addChoice("Reference_image", imageList, reference);
    gd.addChoice("Reference_mask", maskList, referenceMask);
    gd.addChoice("Target_image", maskList, target);
    gd.addNumericField("Min_X_translation", myminXShift, 0);
    gd.addNumericField("Max_X_translation", mymaxXShift, 0);
    gd.addNumericField("Min_Y_translation", myminYShift, 0);
    gd.addNumericField("Max_Y_translation", mymaxYShift, 0);
    gd.addChoice("Sub-pixel_method", subPixelMethods, subPixelMethods[subPixelMethod]);
    gd.addChoice("Interpolation", methods, methods[interpolationMethod]);
    gd.addCheckbox("Show_correlation_image", showCorrelationImage);
    gd.addCheckbox("Clip_output", clipOutput);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.UTILITY);

    gd.showDialog();

    if (gd.wasCanceled()) {
      return false;
    }

    reference = gd.getNextChoice();
    referenceMask = gd.getNextChoice();
    target = gd.getNextChoice();
    myminXShift = (int) gd.getNextNumber();
    mymaxXShift = (int) gd.getNextNumber();
    myminYShift = (int) gd.getNextNumber();
    mymaxYShift = (int) gd.getNextNumber();
    subPixelMethod = gd.getNextChoiceIndex();
    interpolationMethod = gd.getNextChoiceIndex();
    showCorrelationImage = gd.getNextBoolean();
    clipOutput = gd.getNextBoolean();

    return true;
  }

  private static boolean contains(String[] imageList, String title) {
    for (final String t : imageList) {
      if (t.equals(title)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Execute the plugin.
   *
   * @param refImp the reference image
   * @param maskIp the mask image
   * @param targetImp the target image
   * @param minXShift the min X shift
   * @param maxXShift the max X shift
   * @param minYShift the min Y shift
   * @param maxYShift the max Y shift
   * @param subPixelMethod the sub pixel method
   * @param interpolationMethod the interpolation method
   * @param showCorrelationImage the show correlation image flag
   * @param clipOutput the clip output flag
   * @return the aligned image plus
   */
  public ImagePlus exec(ImagePlus refImp, ImageProcessor maskIp, ImagePlus targetImp, int minXShift,
      int maxXShift, int minYShift, int maxYShift, int subPixelMethod, int interpolationMethod,
      boolean showCorrelationImage, boolean clipOutput) {
    final ImageProcessor refIp = refImp.getProcessor();
    if (targetImp == null) {
      targetImp = refImp;
    }

    // Check same size
    if (!isValid(refIp, maskIp, targetImp)) {
      return null;
    }
    if (maxXShift < minXShift || maxYShift < minYShift) {
      return null;
    }

    // Note:
    // If the reference image is centred and the target image is normalised then the result
    // of the top half of the Pearson correlation equation will be the correlation produced by
    // the Align_Images_FFT without normalisation.

    final ImageStack outStack = new ImageStack(targetImp.getWidth(), targetImp.getHeight());
    ImageStack correlationStack = null;
    final FloatProcessor fp =
        new FloatProcessor(maxXShift - minXShift + 1, maxYShift - minYShift + 1);
    if (showCorrelationImage) {
      correlationStack = new ImageStack(maxXShift - minXShift + 1, maxYShift - minYShift + 1);
    }

    final ImageStack stack = targetImp.getStack();
    for (int slice = 1; slice <= stack.getSize(); slice++) {
      final ImageProcessor targetIp = stack.getProcessor(slice);
      outStack.addSlice(null, alignImages(refIp, maskIp, targetIp, slice, minXShift, maxXShift,
          minYShift, maxYShift, fp, subPixelMethod, interpolationMethod, clipOutput));
      if (correlationStack != null) {
        correlationStack.addSlice(null, fp.duplicate());
      }
    }

    if (showCorrelationImage) {
      new ImagePlus(targetImp.getTitle() + " Correlation", correlationStack).show();
    }

    return new ImagePlus(targetImp.getTitle() + " Aligned", outStack);
  }

  /**
   * Subtract mean from the image and return a float processor.
   *
   * @param ip the image
   * @return the float processor
   */
  public static FloatProcessor centre(ImageProcessor ip) {
    final float[] pixels = new float[ip.getPixelCount()];

    // Subtract mean and normalise to unit length
    double sum = 0;
    for (int i = 0; i < ip.getPixelCount(); i++) {
      sum += ip.getf(i);
    }
    final float av = (float) (sum / pixels.length);
    for (int i = 0; i < pixels.length; i++) {
      pixels[i] = ip.getf(i) - av;
    }

    return new FloatProcessor(ip.getWidth(), ip.getHeight(), pixels, null);
  }

  /**
   * Convert to unit length, return a float processor.
   *
   * @param ip the image
   * @return the float processor
   */
  public static FloatProcessor normalise(ImageProcessor ip) {
    final float[] pixels = new float[ip.getPixelCount()];

    // Normalise to unit length and subtract mean
    double sum = 0;
    for (int i = 0; i < pixels.length; i++) {
      sum += ip.getf(i) * ip.getf(i);
    }
    if (sum > 0) {
      final double factor = 1.0 / Math.sqrt(sum);
      for (int i = 0; i < pixels.length; i++) {
        pixels[i] = (float) (ip.getf(i) * factor);
      }
    }

    return new FloatProcessor(ip.getWidth(), ip.getHeight(), pixels, null);
  }

  private static boolean isValid(ImageProcessor refIp, ImageProcessor maskIp, ImagePlus targetImp) {
    if (refIp == null || targetImp == null) {
      return false;
    }
    if (maskIp != null) {
      return refIp.getWidth() == maskIp.getWidth() && refIp.getHeight() == maskIp.getHeight();
    }
    return true;
  }

  private static ImageProcessor alignImages(ImageProcessor refIp, ImageProcessor maskIp,
      ImageProcessor targetIp, int slice, int minXShift, int maxXShift, int minYShift,
      int maxYShift, FloatProcessor fp, int subPixelMethod, int interpolationMethod,
      boolean clipOutput) {
    double scoreMax = 0;
    int xshiftMax = 0;
    int yshiftMax = 0;
    for (int xshift = minXShift; xshift <= maxXShift; xshift++) {
      for (int yshift = minYShift; yshift <= maxYShift; yshift++) {
        final double score = calculateScore(refIp, maskIp, targetIp, xshift, yshift);
        fp.setf(xshift - minXShift, yshift - minYShift, (float) score);

        if (scoreMax < score) {
          scoreMax = score;
          xshiftMax = xshift;
          yshiftMax = yshift;
        }
      }
    }

    double xoffset = xshiftMax;
    double yoffset = yshiftMax;

    String estimatedScore = "";
    if (subPixelMethod > 0 && scoreMax != 1.00) {
      double[] centre;
      final int i = xshiftMax - minXShift;
      final int j = yshiftMax - minYShift;
      if (subPixelMethod == 1) {
        centre = performCubicFit(fp, i, j);
      } else {
        centre = performGaussianFit(fp);
      }

      if (centre != null) {
        xoffset = centre[0] + minXShift;
        yoffset = centre[1] + minYShift;

        double score = fp.getBicubicInterpolatedPixel(centre[0], centre[1], fp);
        if (score < -1) {
          score = -1;
        }
        if (score > 1) {
          score = 1;
        }
        estimatedScore = String.format(" (interpolated score %g)", score);
      }
    }

    String warning = "";
    if (xoffset == minXShift || xoffset == maxXShift || yoffset == minYShift
        || yoffset == maxYShift) {
      warning = "***";
    }
    IJ.log(String.format("Best Slice%s %d  x %g  y %g = %g%s", warning, slice, xoffset, yoffset,
        scoreMax, estimatedScore));

    return translate(interpolationMethod, targetIp, xoffset, yoffset, clipOutput);
  }

  /**
   * Duplicate and translate the image processor.
   *
   * @param interpolationMethod the interpolation method
   * @param ip the image
   * @param xoffset the x offset
   * @param yoffset the y offset
   * @param clipOutput Set to true to ensure the output image has the same max as the input. Applies
   *        to bicubic interpolation
   * @return New translated processor
   */
  public static ImageProcessor translate(int interpolationMethod, ImageProcessor ip, double xoffset,
      double yoffset, boolean clipOutput) {
    final ImageProcessor newIp = ip.duplicate();
    translateProcessor(interpolationMethod, newIp, xoffset, yoffset, clipOutput);
    return newIp;
  }

  /**
   * Translate the image processor in place.
   *
   * @param interpolationMethod the interpolation method
   * @param ip the image
   * @param xoffset the x offset
   * @param yoffset the y offset
   * @param clipOutput Set to true to ensure the output image has the same max as the input. Applies
   *        to bicubic interpolation
   */
  public static void translateProcessor(int interpolationMethod, ImageProcessor ip, double xoffset,
      double yoffset, boolean clipOutput) {
    // Check if interpolation is needed
    if (xoffset == (int) xoffset && yoffset == (int) yoffset) {
      interpolationMethod = ImageProcessor.NONE;
    }

    // Bicubic interpolation can generate values outside the input range.
    // Optionally clip these. This is not applicable for ColorProcessors.
    ImageStatistics stats = null;
    if (interpolationMethod == ImageProcessor.BICUBIC && clipOutput
        && !(ip instanceof ColorProcessor)) {
      stats = ImageStatistics.getStatistics(ip, Measurements.MIN_MAX, null);
    }

    ip.setInterpolationMethod(interpolationMethod);
    ip.translate(xoffset, yoffset);

    if (stats != null) {
      final float max = (float) stats.max;
      for (int i = ip.getPixelCount(); i-- > 0;) {
        if (ip.getf(i) > max) {
          ip.setf(i, max);
        }
      }
    }
  }

  /**
   * Iteratively search the cubic spline surface around the given pixel to maximise the value.
   *
   * @param fp Float processor containing a peak surface
   * @param xpos The peak x position
   * @param ypos The peak y position
   * @return The peak location with sub-pixel accuracy
   */
  public static double[] performCubicFit(FloatProcessor fp, int xpos, int ypos) {
    double[] centre = new double[] {xpos, ypos};
    // This value will be progressively halved.
    // Start with a value that allows the number of iterations to fully cover the region +/- 1 pixel
    double range = 0.5;
    for (int c = 10; c-- > 0;) {
      centre = performCubicFit(fp, centre[0], centre[1], range);
      range /= 2;
    }
    return centre;
  }

  private static double[] performCubicFit(FloatProcessor fp, double x, double y, double range) {
    final double[] centre = new double[2];
    double peakValue = Double.NEGATIVE_INFINITY;
    for (final double x0 : new double[] {x - range, x, x + range}) {
      for (final double y0 : new double[] {y - range, y, y + range}) {
        final double v = fp.getBicubicInterpolatedPixel(x0, y0, fp);
        if (peakValue < v) {
          peakValue = v;
          centre[0] = x0;
          centre[1] = y0;
        }
      }
    }
    return centre;
  }

  /**
   * Perform an interpolated Gaussian fit.
   *
   * <p>The following functions for peak finding using Gaussian fitting have been extracted from
   * Jpiv: http://www.jpiv.vennemann-online.de/
   *
   * @param fp Float processor containing a peak surface
   * @return The peak location with sub-pixel accuracy
   */
  public static double[] performGaussianFit(FloatProcessor fp) {
    // Extract Pixel block
    final float[][] pixelBlock = new float[fp.getWidth()][fp.getHeight()];
    for (int x = pixelBlock.length; x-- > 0;) {
      for (int y = pixelBlock[0].length; y-- > 0;) {
        if (Float.isNaN(fp.getf(x, y))) {
          pixelBlock[x][y] = -1;
        } else {
          pixelBlock[x][y] = fp.getf(x, y);
        }
      }
    }

    // Extracted as per the code in Jpiv2.PivUtils:
    final int x = 0;
    final int y = 0;
    final int w = fp.getWidth();
    final int h = fp.getHeight();
    final int[] maxCoord = getPeak(pixelBlock);
    final double[] subpixelCoord = gaussianPeakFit(pixelBlock, maxCoord[0], maxCoord[1]);
    double[] ret = null;
    // more or less acceptable peak fit
    if (Math.abs(subpixelCoord[0] - maxCoord[0]) < w / 2
        && Math.abs(subpixelCoord[1] - maxCoord[1]) < h / 2) {
      subpixelCoord[0] += x;
      subpixelCoord[1] += y;
      // Jpiv block is in [Y,X] format (not [X,Y])
      ret = new double[] {subpixelCoord[1], subpixelCoord[0]};
    }
    return ret;
  }

  /**
   * Finds the highest value in a correlation matrix.
   *
   * @param subCorrMat A single correlation matrix.
   * @return The indices of the highest value {i,j} or {y,x}.
   */
  private static int[] getPeak(float[][] subCorrMat) {
    final int[] coord = new int[2];
    float peakValue = 0;
    for (int i = 0; i < subCorrMat.length; ++i) {
      for (int j = 0; j < subCorrMat[0].length; ++j) {
        if (subCorrMat[i][j] > peakValue) {
          peakValue = subCorrMat[i][j];
          coord[0] = j;
          coord[1] = i;
        }
      }
    }
    return coord;
  }

  /**
   * Gaussian peak fit. See Raffel, Willert, Kompenhans; Particle Image Velocimetry; 3rd printing;
   * S. 131 for details
   *
   * @param subCorrMat some two dimensional data containing a correlation peak
   * @param x the horizontal peak position
   * @param y the vertical peak position
   * @return a double array containing the peak position with sub pixel accuracy
   */
  private static double[] gaussianPeakFit(float[][] subCorrMat, int x, int y) {
    final double[] coord = new double[2];
    // border values
    if (x == 0 || x == subCorrMat[0].length - 1 || y == 0 || y == subCorrMat.length - 1) {
      coord[0] = x;
      coord[1] = y;
    } else {
      coord[0] = x + (Math.log(subCorrMat[y][x - 1]) - Math.log(subCorrMat[y][x + 1]))
          / (2 * Math.log(subCorrMat[y][x - 1]) - 4 * Math.log(subCorrMat[y][x])
              + 2 * Math.log(subCorrMat[y][x + 1]));
      coord[1] = y + (Math.log(subCorrMat[y - 1][x]) - Math.log(subCorrMat[y + 1][x]))
          / (2 * Math.log(subCorrMat[y - 1][x]) - 4 * Math.log(subCorrMat[y][x])
              + 2 * Math.log(subCorrMat[y + 1][x]));
    }
    return (coord);
  }

  private static double calculateScore(ImageProcessor refIp, ImageProcessor maskIp,
      ImageProcessor targetIp, int xshift, int yshift) {
    // Same dimensions at current
    double sumX = 0;
    double sumXy = 0;
    double sumXx = 0;
    double sumYy = 0;
    double sumY = 0;
    int count = 0;
    float ch1;
    float ch2;

    final int refMax = getBitClippedMax(refIp);
    final int targetMax = getBitClippedMax(targetIp);

    // Set the bounds for the search in the reference image:
    // - x,y needs to be within reference
    // - x,y shifted needs to be within target

    // Bounds of the reference image
    final Rectangle bRef = new Rectangle(0, 0, refIp.getWidth(), refIp.getHeight());

    // This is the smallest of the two images.
    final Rectangle region = new Rectangle(0, 0, Math.min(refIp.getWidth(), targetIp.getWidth()),
        Math.min(refIp.getHeight(), targetIp.getHeight()));

    // Shift the region
    region.x += xshift;
    region.y += yshift;

    // Constrain search to the reference coordinates that overlap the region
    final Rectangle intersect = bRef.intersection(region);

    final int xmin = intersect.x;
    final int xmax = intersect.x + intersect.width;
    final int ymin = intersect.y;
    final int ymax = intersect.y + intersect.height;

    for (int y = ymax; y-- > ymin;) {
      final int y2 = y - yshift;

      for (int x = xmax; x-- > xmin;) {
        // Check if this is within the mask
        if (maskIp == null || maskIp.get(x, y) > 0) {
          final int x2 = x - xshift;

          ch1 = refIp.getf(x, y);
          ch2 = targetIp.getf(x2, y2);

          // Ignore clipped values
          if (ch1 == 0 || ch1 == refMax || ch2 == 0 || ch2 == targetMax) {
            continue;
          }

          sumX += ch1;
          sumXy += (ch1 * ch2);
          sumXx += (ch1 * ch1);
          sumYy += (ch2 * ch2);
          sumY += ch2;

          count++;
        }
      }
    }

    double correlation = Double.NaN;

    if (count > 0) {
      // Note:
      // If the reference image is centred and the target image is normalised then the result
      // of the top half of the Pearson correlation equation will be the correlation produced by
      // the Align_Images_FFT without normalisation.

      final double pearsons1 = sumXy - (1.0 * sumX * sumY / count);
      final double pearsons2 = sumXx - (1.0 * sumX * sumX / count);
      final double pearsons3 = sumYy - (1.0 * sumY * sumY / count);

      if (pearsons2 == 0 || pearsons3 == 0) {
        // If there is data and all the variances are the same then correlation is perfect
        if (sumXx == sumYy && sumXx == sumXy && sumXx > 0) {
          correlation = 1;
        } else {
          correlation = 0;
        }
      } else {
        correlation = pearsons1 / (Math.sqrt(pearsons2 * pearsons3));
      }
    }

    return correlation;
  }

  private static int getBitClippedMax(ImageProcessor ip) {
    final int max = (int) ip.getMax();
    // Check for bit clipped maximum values
    for (final int bit : new int[] {8, 16, 10, 12}) {
      final int bitClippedMax = (int) (Math.pow(2.0, bit) - 1);
      if (max == bitClippedMax) {
        return max;
      }
    }
    return Integer.MAX_VALUE;
  }
}
