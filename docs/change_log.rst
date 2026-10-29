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


Version 2.0
-----------

Major release of GDSC ImageJ Plugins.

* Update to GDSC IJ parent 4.0 based on pom-scijava 45.1.0 for compatibility with
  ImageJ/Fiji July 2026.
* Update to GDSC Core 2.3.

Requires Java 11.
