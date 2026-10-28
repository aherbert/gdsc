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


Version 2.0
-----------

Major release of GDSC ImageJ Plugins.

* Update to GDSC IJ parent 4.0 based on pom-scijava 45.1.0 for compatibility with
  ImageJ/Fiji July 2026.
* Update to GDSC Core 2.3.

Requires Java 11.
