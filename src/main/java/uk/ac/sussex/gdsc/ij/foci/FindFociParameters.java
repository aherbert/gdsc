package uk.ac.sussex.gdsc.ij.foci;

import ij.IJ;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.AlgorithmOption;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.BackgroundMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.CentreMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.PeakMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SearchMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.SortMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.StatisticsMethod;
import uk.ac.sussex.gdsc.ij.foci.FindFociProcessorOptions.ThresholdMethod;

/**
 * A class to allow conversion of {@link FindFociParameters} to and from a {@link String}.
 */
class FindFociParameters {
  private static final String SPACER = " : ";

  /** The processor options. */
  final FindFociProcessorOptions processorOptions;
  /** The cached parameter string. */
  private String parameterString;

  /**
   * Creates an instance.
   *
   * @param processorOptions the processor options
   */
  FindFociParameters(FindFociProcessorOptions processorOptions) {
    this.processorOptions = processorOptions.copy();
  }

  /**
   * Creates the string representation of the parameters (the value is computed once and cached).
   *
   * @return the string representation of the parameters.
   */
  @Override
  public String toString() {
    String result = parameterString;
    if (result == null) {
      parameterString = result = createParametersString();
    }
    return result;
  }

  /**
   * Convert the parameters into a string representation.
   *
   * @return the string
   */
  private String createParametersString() {
    // Output results
    final StringBuilder sb = new StringBuilder();
    // Field 1
    sb.append(processorOptions.getGaussianBlur()).append('\t');
    // Field 2
    sb.append(processorOptions.getBackgroundMethod().getDescription());
    if (FindFociOptimiser_PlugIn.backgroundMethodHasStatisticsMode(processorOptions.getBackgroundMethod())) {
      sb.append(" (").append(processorOptions.getStatisticsMethod().getDescription())
          .append(") ");
    }
    sb.append(SPACER);
    if (FindFociOptimiser_PlugIn.backgroundMethodHasParameter(processorOptions.getBackgroundMethod())) {
      sb.append(IJ.d2s(processorOptions.getBackgroundParameter(), 2));
    } else {
      sb.append(processorOptions.getThresholdMethod().getDescription());
    }
    sb.append('\t');
    // Field 3
    sb.append(processorOptions.getMaxPeaks()).append('\t');
    // Field 4
    sb.append(processorOptions.getMinSize());
    if (processorOptions.isOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE)) {
      sb.append(" >saddle");
      if (processorOptions.isOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE)) {
        sb.append(" conn");
      }
    }
    sb.append('\t');
    // Field 5
    sb.append(processorOptions.getSearchMethod().getDescription());
    if (FindFociOptimiser_PlugIn.searchMethodHasParameter(processorOptions.getSearchMethod())) {
      sb.append(SPACER).append(IJ.d2s(processorOptions.getSearchParameter(), 2));
    }
    sb.append('\t');
    // Field 6
    sb.append(processorOptions.getPeakMethod().getDescription()).append(SPACER);
    sb.append(IJ.d2s(processorOptions.getPeakParameter(), 2)).append('\t');
    // Field 7
    sb.append(processorOptions.getSortMethod().getDescription()).append('\t');
    // Field 8
    sb.append(processorOptions.getCentreMethod().getDescription());
    if (centreMethodHasParameter(processorOptions.getCentreMethod())) {
      sb.append(SPACER).append(IJ.d2s(processorOptions.getCentreParameter(), 2));
    }
    sb.append('\t');
    return sb.toString();
  }

  /**
   * Convert the FindFoci text representation into {@link FindFociParameters}.
   *
   * @param text the parameters text
   * @return the parameters
   * @throws IllegalArgumentException if the argument could not be parsed
   */
  static FindFociParameters fromString(String text) {
    final String[] fields = FindFociOptimiser_PlugIn.TAB_PATTERN.split(text);
    try {
      final FindFociProcessorOptions processorOptions = new FindFociProcessorOptions(true);
      // Field 1
      processorOptions.setGaussianBlur(Double.parseDouble(fields[0]));
      // Field 2 - Divided by a spacer
      int index = fields[1].indexOf(SPACER);
      final String backgroundMethod = fields[1].substring(0, index);
      final String backgroundOption = fields[1].substring(index + SPACER.length());
      index = backgroundMethod.indexOf('(');
      if (index != -1) {
        final int first = index + 1;
        final int last = backgroundMethod.indexOf(')', first);
        processorOptions.setBackgroundMethod(
            BackgroundMethod.fromDescription(backgroundMethod.substring(0, index - 1)));
        processorOptions.setStatisticsMethod(
            StatisticsMethod.fromDescription(backgroundMethod.substring(first, last)));
      } else {
        processorOptions.setBackgroundMethod(BackgroundMethod.fromDescription(backgroundMethod));
      }
      if (FindFociOptimiser_PlugIn.backgroundMethodHasParameter(processorOptions.getBackgroundMethod())) {
        processorOptions.setBackgroundParameter(Double.parseDouble(backgroundOption));
      } else {
        processorOptions.setThresholdMethod(ThresholdMethod.fromDescription(backgroundOption));
      }
      // Field 3
      processorOptions.setMaxPeaks(Integer.parseInt(fields[2]));
      // Field 4
      index = fields[3].indexOf(' ');
      if (index > 0) {
        processorOptions.setOption(AlgorithmOption.MINIMUM_ABOVE_SADDLE, true);
        if (fields[3].contains("conn")) {
          processorOptions.setOption(AlgorithmOption.CONTIGUOUS_ABOVE_SADDLE, true);
        }
        fields[3] = fields[3].substring(0, index);
      }
      processorOptions.setMinSize(Integer.parseInt(fields[3]));
      // Field 5
      index = fields[4].indexOf(SPACER);
      if (index != -1) {
        processorOptions
            .setSearchParameter(Double.parseDouble(fields[4].substring(index + SPACER.length())));
        fields[4] = fields[4].substring(0, index);
      }
      processorOptions.setSearchMethod(SearchMethod.fromDescription(fields[4]));
      // Field 6
      index = fields[5].indexOf(SPACER);
      processorOptions.setPeakMethod(PeakMethod.fromDescription(fields[5].substring(0, index)));
      processorOptions
          .setPeakParameter(Double.parseDouble(fields[5].substring(index + SPACER.length())));
      // Field 7
      processorOptions.setSortMethod(SortMethod.fromDescription(fields[6]));
      // Field 8
      index = fields[7].indexOf(SPACER);
      if (index != -1) {
        processorOptions
            .setCentreParameter(Double.parseDouble(fields[7].substring(index + SPACER.length())));
        fields[7] = fields[7].substring(0, index);
      }
      processorOptions.setCentreMethod(CentreMethod.fromDescription(fields[7]));

      return new FindFociParameters(processorOptions);
    } catch (final NullPointerException | NumberFormatException | IndexOutOfBoundsException ex) {
      // NPE will be thrown if the enum cannot parse the description because null
      // will be passed to the setter.
      throw new IllegalArgumentException(
          "Error converting parameters to FindFoci options: " + text, ex);
    }
  }

  private static boolean centreMethodHasParameter(CentreMethod centreMethod) {
    return (centreMethod == CentreMethod.CENTRE_OF_MASS_SEARCH
        || centreMethod == CentreMethod.GAUSSIAN_SEARCH);
  }
}