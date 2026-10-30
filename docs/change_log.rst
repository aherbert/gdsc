Change Log
==========

.. contents::
   :local:

Version 3.0
-----------

Major release of GDSC ImageJ Plugins.

* Rename the ``Mask Creater`` plugin to :ref:`thresholding_plugins:Mask Creator`. Macros using
  ``run("Mask Creater", ...)`` must be updated to use the new name.
* Correct dialog labels in the :ref:`findfoci_plugins:Spot Distance` plugin (``Circularity limit``,
  ``Process frames``) and the :ref:`findfoci_plugins:Translocation Finder` plugin
  (``Results name 1/2/3``). Macros using the previous option keys (``ciruclarity_limit``,
  ``processettings.frames``, ``resultsettings.name_1/2/3``) must be updated to use the new keys
  (``circularity_limit``, ``process_frames``, ``results_name_1/2/3``).
* The :ref:`findfoci_plugins:FindFoci Batch` plugin reads the ``Overlay_mask`` option from the
  parameters file. Previously the option was ignored and the mask overlay was always saved.
* The :ref:`findfoci_plugins:FindFoci Optimiser` ``Sort method`` and ``Centre method`` fields
  accept method names as well as method numbers.
* The :ref:`findfoci_plugins:FindFoci Optimiser Multi-Image` plugin ``Rank`` score conversion
  ranks the parameters with the best (lowest) average rank first. Previously the worst
  parameters were ranked first.
* Fix the :ref:`findfoci_plugins:FindFoci Helper` ``Show Overlay`` button failing when the image
  has no ROI.
* The :ref:`findfoci_plugins:FindFoci Helper` results table reports the ``Height`` of 32-bit
  images. Previously the raw bits of the float pixel value were reported.
* Standardise ``Id`` to ``ID`` in results table headings: ``Saddle ID``
  (:ref:`findfoci_plugins:FindFoci`), ``Peak ID`` (:ref:`utility_plugins:Spot Separation`),
  ``ID`` (:ref:`findfoci_plugins:FindFoci Helper`), ``Exp. ID`` (:ref:`colocalisation_plugins:CDA`)
  and ``Track ID``/``Spot ID`` (TrackMate track selection export).
* Fix the :ref:`findfoci_plugins:FindFoci Match Calculator` failing when the images used to
  create the results are closed. Height analysis and the overlay are skipped with a log message.
* The :ref:`findfoci_plugins:Match Calculator` reports the heights of 32-bit images. Previously
  the raw bits of the float pixel value were used.
* The :ref:`findfoci_plugins:Match Calculator` match table and saved matches file report the X
  and Y coordinates as integer pixels, and the heights without rounding. Previously calibrated
  coordinates were reported for calibrated
  :ref:`findfoci_plugins:FindFoci Match Calculator` results and values were rounded to 4
  significant digits.
* Fix the :ref:`findfoci_plugins:Point Extractor` failing when there is no image and the
  ``Use manager ROIs`` option is not selected. The ROI Manager is always used when there is no
  image.
* Fix the :ref:`findfoci_plugins:Assign Foci to Clusters` ``Eliminate edge clusters`` option for
  non-square images. Previously the left and right edges were checked using the image width
  instead of the height, causing an error for wide images and missed edges for tall images.
* The :ref:`findfoci_plugins:Point Aligner` unaligned point sub-image marks the unaligned point
  using the colours of the image overlay: red for a conflict point, otherwise blue. Previously
  all points were marked in yellow.
* Fix the :ref:`colocalisation_plugins:CDA (macro)` plugin ignoring the ``(Channel image)`` option
  for the ``ROI for channel 1/2`` image. Previously all the pixels of the channel were used.
* Fix the :ref:`colocalisation_plugins:CDA` ``Save results`` option failing when the
  ``Show merged channel`` or ``Show merged ROI`` options are not selected. The merged images are
  always saved.
* The :ref:`colocalisation_plugins:CDA` plugin saves the ``Show R plot`` option to the
  preferences. Previously the option was reset when ``ImageJ`` was restarted.
* The :ref:`colocalisation_plugins:CDA` statistics window reports a value equal to a significance
  limit as not significant, matching the results table.
* Fix the :ref:`colocalisation_plugins:CDA` plugin failing to compute the significance limits for
  a large ``p-Value`` relative to the number of random samples.
* Fix the :ref:`colocalisation_plugins:Colocalisation Threshold` results options
  ``Show linear regression solution`` and ``Show thresholds``. Previously each option controlled
  the other column in the results table.
* The :ref:`colocalisation_plugins:Colocalisation Threshold` ``Close windows on exit`` option closes
  the ``R-values`` plot.
* Rename the :ref:`colocalisation_plugins:Colocalisation Threshold` ``Search tolerance`` option to
  ``Minimum total correlation``. The option is the minimum correlation of all the pixels required
  to perform the threshold search; it does not control the convergence of the search.
* Fix the :ref:`colocalisation_plugins:Stack Correlation Analyser` Manders coefficients when using
  the ``None`` method with the ``Subtract threshold`` option. Previously 1 was added to each pixel.
* The :ref:`colocalisation_plugins:Stack Colocalisation Analyser` reports ``None`` in the ``Ch3``
  column when no confined region is used. Previously the column was empty.
* Fix the :ref:`colocalisation_plugins:Stack Colocalisation Analyser` failing when the
  ``Minimum shift`` is equal to the ``Maximum shift``, or is negative. These values are now
  rejected.
* Remove the empty last column from the :ref:`colocalisation_plugins:Particle Overlap` results
  table and file.
* Fix the :ref:`colocalisation_plugins:Particle Correlation` plugin failing when there are fewer
  than 3 particles. The correlations are reported as ``NaN``.
* Fix the :ref:`thresholding_plugins:Thread Analyser` ignoring the ``Threshold method`` option.
  Previously the ``Otsu`` method was always used.
* The :ref:`findfoci_plugins:Foci Neighbour Analysis` plugin saves the ``Opacity`` option to the
  preferences. Previously the option was reset when ``ImageJ`` was restarted.
* The :ref:`cells_plugins:Nuclei Outline` dialog describes the analysis of all frames of the
  current channel and z-slice. Previously the dialog stated the nuclei were tracked across frames.
* The :ref:`cells_plugins:Cell Outliner` stack dialog ``No`` button processes only the current
  frame and z-slice. Previously the button processed the selected dimensions, the same as the
  ``Yes`` button.
* The :ref:`cells_plugins:Nuclei Outline` plugin removes outliers from the mask of the nuclei
  independent of the ``ImageJ`` ``Black background`` binary option. Previously outlier removal
  filled small gaps in the background, instead of removing small nuclei, when the option was not
  selected. This also applies to the TrackMate ``Nucleus detector``.
* The :ref:`utility_plugins:Mask Overlap` plugin updates the display of the relabelled mask and
  sets the display range to the range of the object values. Previously the changes were not
  shown until the image was redrawn.
* The :ref:`utility_plugins:Mask Overlap` plugin only matches objects that overlap. Previously
  an ``IoU threshold`` of zero could match objects with no overlap.
* The :ref:`utility_plugins:Mask to ROI` plugin traces every object with the same value, for
  example all the objects in a binary mask. Previously only the first object with each value was
  traced.
* The :ref:`utility_plugins:Scale Space` dialog describes the limit of the maximum scale: a
  Gaussian standard deviation of a quarter of the minimum image dimension. Previously the dialog
  stated scales up to the minimum image dimension were computed.
* The :ref:`utility_plugins:Scale Space` ``Difference of Gaussians`` output shows an error if
  fewer than 2 scales are computed. Previously the single Gaussian image was output.
* Fix the :ref:`utility_plugins:Laplacian of Gaussian` plugin failing when using the
  ``Scaled Units`` option. The preview worked but the final filter raised an error.
* The :ref:`utility_plugins:Scale Space` ``Sub interval steps`` use a constant ratio between
  consecutive scales within each octave. Previously the scales were evenly spaced. This changes
  the scales computed when using sub interval steps.
* The :ref:`utility_plugins:Align Images` plugin names the aligned image using the suffix
  ``Aligned``. Previously the suffix ``Aligned Correlation`` was used.
* The :ref:`utility_plugins:Align Images` plugin shows an error for invalid input: a reference
  mask with a different size to the reference image; or a maximum translation below the minimum
  translation. Previously the plugin stopped without a message.
* The :ref:`utility_plugins:Align Images`, :ref:`utility_plugins:Align Images FFT` and
  :ref:`utility_plugins:Align Stacks` plugins report that a greyscale image (8-bit, 16-bit or
  32-bit) is required if no suitable image is open. Previously the message stated only 8-bit and
  16-bit images were supported.
* The :ref:`utility_plugins:Align Images FFT` and :ref:`utility_plugins:Align Stacks`
  ``Restrict translation`` option includes the maximum X and Y translation in the search.
  Previously the maximum translation was excluded, and a range with equal minimum and maximum
  did not search any translation. A maximum translation below the minimum translation is
  reported as an error.
* Fix the :ref:`utility_plugins:HSB Picker` failing when the image is clicked with the HSB
  Picker tool. The ``Sample radius`` slider was not initialised.
* The :ref:`utility_plugins:HSB Filter` plugin measures the hue difference around the colour
  circle when the hue range extends below zero. Previously the hue was only wrapped when the
  range extended above one.
* The :ref:`utility_plugins:Image Scaler` dialog shows the ``Help`` button. The plugin shows an
  error if there is no image or list file, the list file does not exist, or the images have no
  range of values. Previously the plugin stopped without a message.
* Rename the :ref:`utility_plugins:Spot Analyser` ROI mask option to ``Mask option``. The option
  used the same ``Threshold_Channel`` macro key as the threshold channel option, preventing the
  options being set in a macro. Macros must use the key ``mask_option``.
* Fix the :ref:`utility_plugins:Spot Separation` line profile of a single spot. The check that
  the spot centre of mass is within the spot used the X coordinate for both X and Y.
* The :ref:`utility_plugins:Spot Separation` results report the ``Peak ID`` of a pair using the
  first spot. Previously the second spot was used.


Version 2.0
-----------

Major release of GDSC ImageJ Plugins.

* Update to GDSC IJ parent 4.0 based on pom-scijava 45.1.0 for compatibility with
  ImageJ/Fiji July 2026.
* Update to GDSC Core 2.3.

Requires Java 11.
