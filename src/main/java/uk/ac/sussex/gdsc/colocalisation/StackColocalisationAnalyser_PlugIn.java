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

package uk.ac.sussex.gdsc.colocalisation;

import uk.ac.sussex.gdsc.UsageTracker;
import uk.ac.sussex.gdsc.colocalisation.cda.Cda_PlugIn;
import uk.ac.sussex.gdsc.colocalisation.cda.TwinStackShifter;
import uk.ac.sussex.gdsc.core.ij.ThresholdUtils;
import uk.ac.sussex.gdsc.core.ij.gui.ExtendedGenericDialog;
import uk.ac.sussex.gdsc.core.threshold.AutoThreshold;
import uk.ac.sussex.gdsc.core.utils.Correlator;
import uk.ac.sussex.gdsc.utils.SliceCollection;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.filter.PlugInFilter;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

/**
 * Processes a stack image with multiple channels. Requires three channels. Each frame is processed
 * separately. Extracts all the channels (collating z-stacks) and performs: (1). Thresholding to
 * create a mask for each channel (2). CDA analysis of channel 1 vs channel 2 within the region
 * defined by channel 3.
 */
public class StackColocalisationAnalyser_PlugIn implements PlugInFilter {
  private static final String TITLE = "Stack Colocalisation Analyser";
  private static final String COMBINE = "Combine 1+2";
  private static final String NONE = "None";

  // ImageJ indexes for the dimensions array
  private static final int C = 2;
  private static final int Z = 3;
  private static final int T = 4;

  private static TextWindow tw;

  private static String methodOption = AutoThreshold.Method.OTSU.toString();
  private static int channel1 = 1;
  private static int channel2 = 2;
  private static int channel3;

  // Options flags
  private static boolean logThresholds;
  private static boolean logResults;
  private static boolean showMask;
  private static boolean subtractThreshold;

  private static int permutations = 100;
  private static int minimumRadius = 9;
  private static int maximumRadius = 16;
  private static double pCut = 0.05;

  // Store a reference to the current working image
  private ImagePlus imp;

  private boolean firstResult;
  private Correlator correlator;
  private int[] ii1;
  private int[] ii2;

  /** {@inheritDoc} */
  @Override
  public int setup(String arg, ImagePlus imp) {
    UsageTracker.recordPlugin(this.getClass(), arg);

    if (imp == null) {
      return DONE;
    }

    final int[] dimensions = imp.getDimensions();

    if (dimensions[2] < 2 || imp.getStackSize() < 2
        || (imp.getBitDepth() != 8 && imp.getBitDepth() != 16)) {
      if (IJ.isMacro()) {
        IJ.log("Multi-channel stack required (8-bit or 16-bit): " + imp.getTitle());
      } else {
        IJ.showMessage("Multi-channel stack required (8-bit or 16-bit)");
      }
      return DONE;
    }

    this.imp = imp;

    return DOES_16 + DOES_8G + NO_CHANGES;
  }

  /** {@inheritDoc} */
  @Override
  public void run(ImageProcessor inputProcessor) {
    final int[] dimensions = imp.getDimensions();
    final int currentSlice = imp.getCurrentSlice();

    final String[] methods = getMethods();
    // channel3 is set within getMethods()
    final int nChannels = (channel3 != 1) ? 3 : 2;

    final int size = dimensions[0] * dimensions[1];
    correlator = new Correlator(size);
    ii1 = new int[size];
    ii2 = new int[size];

    for (final String method : methods) {
      if (logThresholds || logResults) {
        IJ.log("Stack colocalisation (" + method + ") : " + imp.getTitle());
      }

      ImageStack maskStack = null;
      ImagePlus maskImage = null;
      if (showMask) {
        // The stack will only have 3 channels
        maskStack = new ImageStack(imp.getWidth(), imp.getHeight(),
            nChannels * dimensions[Z] * dimensions[T]);

        // Ensure empty layers are filled to avoid ImageJ error creating ImagePlus
        final byte[] empty = new byte[maskStack.getWidth() * maskStack.getHeight()];
        Arrays.fill(empty, (byte) 255);
        maskStack.setPixels(empty, 1);

        maskImage = new ImagePlus(imp.getTitle() + ":" + method, maskStack);
        maskImage.setDimensions(nChannels, dimensions[Z], dimensions[T]);
      }

      for (int t = 1; t <= dimensions[T]; t++) {
        final ArrayList<AnalysisSliceCollection> sliceCollections = new ArrayList<>();

        // Extract the channels
        for (int c = 1; c <= dimensions[C]; c++) {
          // Process all slices together
          final AnalysisSliceCollection sliceCollection = new AnalysisSliceCollection(c);
          for (int z = 1; z <= dimensions[Z]; z++) {
            sliceCollection.add(imp.getStackIndex(c, z, t));
          }
          sliceCollections.add(sliceCollection);
        }

        // Get the channels:
        final AnalysisSliceCollection s1 = sliceCollections.get(channel1 - 1);
        final AnalysisSliceCollection s2 = sliceCollections.get(channel2 - 1);

        // Create masks
        extractImageAndCreateOutputMask(method, maskImage, 1, t, s1);
        extractImageAndCreateOutputMask(method, maskImage, 2, t, s2);

        // Note that channel 3 is offset by 1 because it contains the [none] option
        AnalysisSliceCollection s3;
        if (channel3 > 1) {
          s3 = sliceCollections.get(channel3 - 2);
          extractImageAndCreateOutputMask(method, maskImage, 3, t, s3);
        } else {
          s3 = new AnalysisSliceCollection(0);
          if (channel3 == 0) {
            // Combine the two masks
            combineMasksAndCreateOutputMask(method, maskImage, 3, t, s1, s2, s3);
          }
        }

        final double[] results = correlate(s1, s2, s3);

        reportResult(method, t, s1.getSliceName(), s2.getSliceName(), s3.getSliceName(), results);
      }

      if (showMask) {
        maskImage.show();
        IJ.run("Stack to Hyperstack...", "order=xyczt(default) channels=" + nChannels + " slices="
            + dimensions[Z] + " frames=" + dimensions[T] + " display=Color");
      }
    }
    imp.setSlice(currentSlice);
  }

  private void extractImageAndCreateOutputMask(String method, ImagePlus maskImage, int channel,
      int frame, AnalysisSliceCollection sliceCollection) {
    sliceCollection.createStack(imp);
    sliceCollection.createMask(method);
    if (logThresholds) {
      IJ.log("t" + frame + sliceCollection.getSliceName() + " threshold = "
          + sliceCollection.threshold);
    }

    if (showMask) {
      final ImageStack maskStack = maskImage.getImageStack();
      for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++) {
        final int originalSliceNumber = sliceCollection.get(s - 1);
        final int newSliceNumber = maskImage.getStackIndex(channel, s, frame);
        maskStack.setSliceLabel(method + ":" + imp.getStack().getSliceLabel(originalSliceNumber),
            newSliceNumber);
        maskStack.setPixels(sliceCollection.maskStack.getPixels(s), newSliceNumber);
      }
    }
  }

  private static void combineMasksAndCreateOutputMask(String method, ImagePlus maskImage,
      int channel, int frame, AnalysisSliceCollection s1, AnalysisSliceCollection s2,
      AnalysisSliceCollection sliceCollection) {
    sliceCollection.createMask(s1.maskStack, s2.maskStack);
    if (showMask) {
      final ImageStack maskStack = maskImage.getImageStack();
      for (int s = 1; s <= sliceCollection.maskStack.getSize(); s++) {
        final int newSliceNumber = maskImage.getStackIndex(channel, s, frame);
        maskStack.setSliceLabel(method + ":" + COMBINE, newSliceNumber);
        maskStack.setPixels(sliceCollection.maskStack.getPixels(s), newSliceNumber);
      }
    }
  }

  private String[] getMethods() {
    final ExtendedGenericDialog gd = new ExtendedGenericDialog(TITLE);
    gd.addMessage(TITLE);
    firstResult = true;

    String[] indices = new String[imp.getNChannels()];
    for (int i = 1; i <= indices.length; i++) {
      indices[i - 1] = "" + i;
    }

    gd.addChoice("Channel_1", indices, channel1 - 1);
    gd.addChoice("Channel_2", indices, channel2 - 1);
    indices = addNoneOption(indices);
    gd.addChoice("Channel_3", indices, channel3);

    // Commented out the methods that take a long time on 16-bit images.
    String[] methods = {"Try all", AutoThreshold.Method.DEFAULT.toString(),
        // "Huang",
        // "Intermodes",
        // "IsoData",
        AutoThreshold.Method.LI.toString(), AutoThreshold.Method.MAX_ENTROPY.toString(),
        AutoThreshold.Method.MEAN.toString(), AutoThreshold.Method.MIN_ERROR_I.toString(),
        // "Minimum",
        AutoThreshold.Method.MOMENTS.toString(), AutoThreshold.Method.OTSU.toString(),
        AutoThreshold.Method.PERCENTILE.toString(), AutoThreshold.Method.RENYI_ENTROPY.toString(),
        // "Shanbhag",
        AutoThreshold.Method.TRIANGLE.toString(), AutoThreshold.Method.YEN.toString(),
        AutoThreshold.Method.NONE.toString()};

    gd.addChoice("Method", methods, methodOption);
    gd.addCheckbox("Log_thresholds", logThresholds);
    gd.addCheckbox("Log_results", logResults);
    gd.addCheckbox("Show_mask", showMask);
    gd.addCheckbox("Subtract threshold", subtractThreshold);
    gd.addNumericField("Permutations", permutations, 0);
    gd.addNumericField("Minimum_shift", minimumRadius, 0);
    gd.addNumericField("Maximum_shift", maximumRadius, 0);
    gd.addNumericField("Significance", pCut, 3);
    gd.addHelp(uk.ac.sussex.gdsc.help.UrlUtils.COLOCALISATION);

    gd.showDialog();
    if (gd.wasCanceled()) {
      return new String[0];
    }

    channel1 = gd.getNextChoiceIndex() + 1;
    channel2 = gd.getNextChoiceIndex() + 1;
    channel3 = gd.getNextChoiceIndex();
    methodOption = gd.getNextChoice();
    logThresholds = gd.getNextBoolean();
    logResults = gd.getNextBoolean();
    showMask = gd.getNextBoolean();
    subtractThreshold = gd.getNextBoolean();
    permutations = (int) gd.getNextNumber();
    minimumRadius = (int) gd.getNextNumber();
    maximumRadius = (int) gd.getNextNumber();
    pCut = gd.getNextNumber();

    // Check parameters
    if (minimumRadius > maximumRadius) {
      return failed("Minimum radius cannot be above maximum radius");
    }
    if (maximumRadius > 255) {
      return failed("Maximum radius cannot be above 255");
    }
    if (pCut < 0 || pCut > 1) {
      return failed("Significance must be between 0-1");
    }

    if (!methodOption.equals("Try all")) {
      // Ensure that the string contains known methods (to avoid passing bad macro arguments)
      methods = extractMethods(methodOption.split(" "), methods);
    } else {
      // Shift the array to remove the try all option
      final String[] newMethods = new String[methods.length - 1];
      for (int i = 0; i < newMethods.length; i++) {
        newMethods[i] = methods[i + 1];
      }
      methods = newMethods;
    }

    if (methods.length == 0) {
      return failed("No valid thresholding method(s) specified");
    }

    return methods;
  }

  private static String[] addNoneOption(String[] indices) {
    final String[] newIndices = new String[indices.length + 2];
    newIndices[0] = COMBINE;
    newIndices[1] = NONE;
    for (int i = 0; i < indices.length; i++) {
      newIndices[i + 2] = indices[i];
    }
    return newIndices;
  }

  private static String[] failed(String message) {
    IJ.error(TITLE, message);
    return new String[0];
  }

  /**
   * Filtered the set of options using the allowed methods array.
   *
   * @param options the options
   * @param allowedMethods the allowed methods
   * @return filtered options
   */
  private static String[] extractMethods(String[] options, String[] allowedMethods) {
    final ArrayList<String> methods = new ArrayList<>();
    for (final String option : options) {
      for (final String allowedMethod : allowedMethods) {
        if (option.equals(allowedMethod)) {
          methods.add(option);
          break;
        }
      }
    }
    return methods.toArray(new String[0]);
  }

  /**
   * Create the combination of the two stacks.
   *
   * @param stack1 the stack 1
   * @param stack2 the stack 2
   * @param operation the operation
   * @return the new stack
   */
  private static ImageStack combineBits(ImageStack stack1, ImageStack stack2, int operation) {
    final ImageStack newStack = new ImageStack(stack1.getWidth(), stack1.getHeight());
    for (int s = 1; s <= stack1.getSize(); s++) {
      newStack.addSlice(null,
          combineBits(stack1.getProcessor(s), stack2.getProcessor(s), operation));
    }
    return newStack;
  }

  /**
   * Create the combination of the two processors.
   *
   * @param ip1 the image 1
   * @param ip2 the image 2
   * @param operation the blitter operation
   * @return the new processor
   */
  private static ImageProcessor combineBits(ImageProcessor ip1, ImageProcessor ip2, int operation) {
    final ImageProcessor bp = ip1.duplicate();
    bp.copyBits(ip2, 0, 0, operation);
    return bp;
  }

  /**
   * Calculate the Mander's coefficients and Pearson correlation coefficient (R) between the two
   * input channels within the intersect of their masks.
   *
   * @param s1 the s 1
   * @param s2 the s 2
   * @param s3 the s 3
   * @return an array containing: M1, M2, R, the number of overlapping pixels; the % total area for
   *         the overlap;
   */
  private double[] correlate(AnalysisSliceCollection s1, AnalysisSliceCollection s2,
      AnalysisSliceCollection s3) {
    double m1Significant = 0;
    double m2Significant = 0;
    double correlationSignificant = 0;

    // Debug - show all the input
    // Utils.display("Stack Analyser Stack1", s1.imageStack);
    // Utils.display("Stack Analyser Stack2", s2.imageStack);
    // Utils.display("Stack Analyser ROI1", s1.maskStack);
    // Utils.display("Stack Analyser ROI2", s2.maskStack);
    // Utils.display("Stack Analyser Confined", s3.maskStack);

    // Calculate the total intensity within the channels, only counting regions in the channel mask
    // and the ROI
    final double totalIntensity1 = getTotalIntensity(s1.imageStack, s1.maskStack, s3.maskStack);
    final double totalIntensity2 = getTotalIntensity(s2.imageStack, s2.maskStack, s3.maskStack);

    // System.out.printf("Stack Analyser total = %s (%s) %s (%s) : %s)\n",
    // totalIntensity1, getTotalIntensity(s1.maskStack, s1.maskStack, null),
    // totalIntensity2, getTotalIntensity(s2.maskStack, s2.maskStack, null),
    // getTotalIntensity(s3.maskStack, s3.maskStack, null));

    // Get the standard result
    final CalculationResult result = calculateCorrelation(s1.imageStack, s1.maskStack,
        s2.imageStack, s2.maskStack, s3.maskStack, 0, totalIntensity1, totalIntensity2);

    if (permutations > 0) {
      // Circularly permute the s2 stack and compute the M1,M2,R stats.
      // Perform permutations within given distance limits, random n trials from all combinations.

      final int[] indices =
          Cda_PlugIn.getRandomShiftIndices(minimumRadius, maximumRadius, permutations);

      final TwinStackShifter stackShifter =
          new TwinStackShifter(s2.imageStack, s2.maskStack, s3.maskStack);

      // Process only the specified number of permutations
      final ArrayList<CalculationResult> results = new ArrayList<>(indices.length);

      for (int n = indices.length; n-- > 0;) {
        final int index = indices[n];
        final int x = Cda_PlugIn.getXShift(index);
        final int y = Cda_PlugIn.getYShift(index);

        final double distance = Math.sqrt(x * x + y * y);

        stackShifter.setShift(x, y);
        stackShifter.run();

        results.add(calculateCorrelation(s1.imageStack, s1.maskStack,
            stackShifter.getResultImage().getStack(), stackShifter.getResultImage2().getStack(),
            s3.maskStack, distance, totalIntensity1, totalIntensity2));
      }

      // Output if significant at given confidence level. Avoid bounds errors.
      final int upperIndex =
          (int) Math.min(results.size() - 1, Math.ceil(results.size() * (1 - pCut)));
      final int lowerIndex = (int) Math.floor(results.size() * pCut);

      Collections.sort(results, StackColocalisationAnalyser_PlugIn::compareM1);
      m1Significant =
          getSignificance(results.get(lowerIndex).m1, result.m1, results.get(upperIndex).m1);
      Collections.sort(results, StackColocalisationAnalyser_PlugIn::compareM2);
      m2Significant =
          getSignificance(results.get(lowerIndex).m2, result.m2, results.get(upperIndex).m2);
      Collections.sort(results, StackColocalisationAnalyser_PlugIn::compareR);
      correlationSignificant = getSignificance(results.get(lowerIndex).correlation,
          result.correlation, results.get(upperIndex).correlation);
    }

    return new double[] {result.m1, result.m2, result.correlation, result.overlapCount, result.area,
        m1Significant, m2Significant, correlationSignificant};
  }

  private static double getSignificance(double lower, double value, double upper) {
    if (value < lower) {
      return -1;
    }
    if (value > upper) {
      return 1;
    }
    return 0;
  }

  private static double getTotalIntensity(ImageStack imageStack, ImageStack maskStack,
      ImageStack maskStack2) {
    double total = 0;
    for (int s = 1; s <= imageStack.getSize(); s++) {
      final ImageProcessor ip1 = imageStack.getProcessor(s);
      final ImageProcessor ip2 = maskStack.getProcessor(s);
      final byte[] mask = (byte[]) ip2.getPixels();

      if (maskStack2 != null) {
        final ImageProcessor ip3 = maskStack2.getProcessor(s);
        final byte[] mask2 = (byte[]) ip3.getPixels();

        for (int i = mask.length; i-- > 0;) {
          if (mask[i] != 0 && mask2[i] != 0) {
            total += ip1.get(i);
          }
        }
      } else {
        for (int i = mask.length; i-- > 0;) {
          if (mask[i] != 0) {
            total += ip1.get(i);
          }
        }
      }
    }
    return total;
  }

  /**
   * Calculate the Mander's coefficients and Pearson correlation coefficient (R) between the two
   * input channels within the intersect of their masks. Only use the pixels within the roi mask.
   */
  private CalculationResult calculateCorrelation(ImageStack image1, ImageStack mask1,
      ImageStack image2, ImageStack mask2, ImageStack roi, double distance, double totalIntensity1,
      double totalIntensity2) {
    final ImageStack overlapStack = combineBits(mask1, mask2, Blitter.AND);

    int total = 0;

    correlator.clear();

    for (int s = 1; s <= overlapStack.getSize(); s++) {
      final ImageProcessor ip1 = image1.getProcessor(s);
      final ImageProcessor ip2 = image2.getProcessor(s);

      final ByteProcessor overlap = (ByteProcessor) overlapStack.getProcessor(s);
      final byte[] b = (byte[]) overlap.getPixels();

      int count = 0;
      if (roi != null) {
        // Calculate correlation within a specified region
        final ImageProcessor ip3 = roi.getProcessor(s);
        final byte[] mask = (byte[]) ip3.getPixels();

        for (int i = mask.length; i-- > 0;) {
          if (mask[i] != 0) {
            total++;
            if (b[i] != 0) {
              ii1[count] = ip1.get(i);
              ii2[count] = ip2.get(i);
              count++;
            }
          }
        }
      } else {
        // Calculate correlation for entire image
        for (int i = ip1.getPixelCount(); i-- > 0;) {
          if (b[i] != 0) {
            ii1[count] = ip1.get(i);
            ii2[count] = ip2.get(i);
            count++;
          }
        }
        total += ip1.getPixelCount();
      }
      correlator.add(ii1, ii2, count);
    }

    final double m1 = correlator.getSumX() / totalIntensity1;
    final double m2 = correlator.getSumY() / totalIntensity2;
    final double r = correlator.getCorrelation();

    return new CalculationResult(distance, m1, m2, r, correlator.getN(),
        (100.0 * correlator.getN() / total));
  }

  /**
   * Reports the results for the correlation to the IJ log window.
   *
   * @param frame The timeframe
   * @param c1 Channel 1 title
   * @param c2 Channel 2 title
   * @param c3 Channel 3 title
   * @param results The correlation results
   */
  private void reportResult(String method, int frame, String c1, String c2, String c3,
      double[] results) {
    createResultsWindow();

    final double m1 = results[0];
    final double m2 = results[1];
    final double r = results[2];
    final int n = (int) results[3];
    final double area = results[4];
    final String m1Significant = getResult(results[5]);
    final String m2Significant = getResult(results[6]);
    final String rSignificant = getResult(results[7]);

    final char spacer = (logResults) ? ',' : '\t';

    final StringBuilder sb = new StringBuilder();
    sb.append(imp.getTitle()).append(spacer);
    sb.append(IJ.d2s(pCut, 4)).append(spacer);
    sb.append(method).append(spacer);
    sb.append(frame).append(spacer);
    sb.append(c1).append(spacer);
    sb.append(c2).append(spacer);
    sb.append(c3).append(spacer);
    sb.append(n).append(spacer);
    sb.append(IJ.d2s(area, 2)).append("%").append(spacer);
    sb.append(IJ.d2s(m1, 4)).append(spacer);
    sb.append(m1Significant).append(spacer);
    sb.append(IJ.d2s(m2, 4)).append(spacer);
    sb.append(m2Significant).append(spacer);
    sb.append(IJ.d2s(r, 4)).append(spacer);
    sb.append(rSignificant);

    if (logResults) {
      IJ.log(sb.toString());
    } else {
      tw.append(sb.toString());
    }
  }

  private static String getResult(double significance) {
    if (significance < 0) {
      return "Non-colocated";
    }
    if (significance > 0) {
      return "Colocated";
    }
    return "-";
  }

  private void createResultsWindow() {
    if (logResults) {
      if (firstResult) {
        firstResult = false;
        IJ.log("Image,p,Method,Frame,Ch1,Ch2,Ch3,n,Area,M1,Sig,M2,Sig,R,Sig");
      }
    } else if (tw == null || !tw.isShowing()) {
      tw = new TextWindow(TITLE + " Results",
          "Image\tp\tMethod\tFrame\tCh1\tCh2\tCh3\tn\tArea\tM1\tSig\tM2\tSig\tR\tSig", "", 700,
          300);
    }
  }

  /**
   * Provides functionality to process a collection of slices from an Image.
   */
  private static class AnalysisSliceCollection extends SliceCollection {

    ImageStack imageStack;
    ImageStack maskStack;
    int threshold;
    String overrideName;

    /**
     * Instantiates a new analysis slice collection.
     *
     * @param indexC The channel index
     */
    AnalysisSliceCollection(int indexC) {
      super(indexC, 0, 0);
    }

    /**
     * Extracts the configured slices from the image into a stack.
     *
     * @param imp the image
     */
    void createStack(ImagePlus imp) {
      imageStack = createStack(imp.getImageStack());
    }

    /**
     * Creates a mask using the specified thresholding method.
     *
     * @param method the method
     */
    private void createMask(String method) {
      // Create an aggregate histogram
      final int[] data = ThresholdUtils.getHistogram(imageStack);

      threshold = AutoThreshold.getThreshold(method, data);

      final boolean mySubtractThreshold = subtractThreshold && (threshold > 0);

      // Create a mask for each image in the stack
      maskStack = new ImageStack(imageStack.getWidth(), imageStack.getHeight());
      final int size = imageStack.getWidth() * imageStack.getHeight();
      for (int s = 1; s <= imageStack.getSize(); s++) {
        final byte[] bp = new byte[size];
        final ImageProcessor ip = imageStack.getProcessor(s);
        for (int i = size; i-- > 0;) {
          final int value = ip.get(i);
          if (value > threshold) {
            bp[i] = (byte) 255;
          }
          if (mySubtractThreshold) {
            ip.set(i, value - threshold);
          }
        }
        maskStack.addSlice(null, bp);
      }
    }

    /**
     * Creates a mask by combining the two masks.
     *
     * <p>This method is used when there is no channel 3 and the region is defined by the
     * combination of the channel 1 and channel 2 masks.
     *
     * @param stack1 the stack 1
     * @param stack2 the stack 2
     */
    private void createMask(ImageStack stack1, ImageStack stack2) {
      overrideName = COMBINE;
      maskStack = combineBits(stack1, stack2, Blitter.OR);
    }

    @Override
    public String getSliceName() {
      if (overrideName != null) {
        return overrideName;
      }
      return super.getSliceName();
    }
  }

  /**
   * Used to store the calculation results of the intersection of two images.
   */
  public static class CalculationResult {
    /** Shift distance. */
    public final double distance;
    /** Mander's 1. */
    public final double m1;
    /** Mander's 2. */
    public final double m2;
    /** Correlation. */
    public final double correlation;
    /** The number of overlapping pixels. */
    public final int overlapCount;
    /** The % total area for the overlap. */
    public final double area;

    /**
     * Instantiates a new calculation result.
     *
     * @param distance the shift distance
     * @param m1 the Mander's 1
     * @param m2 the Mander's 2
     * @param correlation the correlation
     * @param overlapCount the number of overlapping pixels
     * @param area the % total area for the overlap
     */
    public CalculationResult(double distance, double m1, double m2, double correlation,
        int overlapCount, double area) {
      this.distance = distance;
      this.m1 = m1;
      this.m2 = m2;
      this.correlation = correlation;
      this.overlapCount = overlapCount;
      this.area = area;
    }
  }

  /**
   * Compare the results using the Mander's 1 coefficient.
   */
  private static int compareM1(CalculationResult o1, CalculationResult o2) {
    return fastDoubleCompare(o1.m1, o2.m1);
  }

  /**
   * Compare the results using the Mander's 2 coefficient.
   */
  private static int compareM2(CalculationResult o1, CalculationResult o2) {
    return fastDoubleCompare(o1.m2, o2.m2);
  }

  /**
   * Compare the results using the correlation coefficient.
   */
  private static int compareR(CalculationResult o1, CalculationResult o2) {
    return fastDoubleCompare(o1.correlation, o2.correlation);
  }

  /**
   * Compare doubles ignoring NaN.
   *
   * @param d1 the first value
   * @param d2 the second value
   * @return the comparison result
   */
  private static int fastDoubleCompare(double d1, double d2) {
    if (d1 < d2) {
      return -1;
    }
    if (d1 > d2) {
      return 1;
    }
    return 0;
  }
}
