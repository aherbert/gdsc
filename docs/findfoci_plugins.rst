.. index:: ! Find Foci Plugins

Find Foci Plugins
=================

The ``FindFoci`` plugins allow the identification of peak intensity regions within 2D and 3D images. The algorithm is highly configurable and parameters can be optimised using reference images and then applied to multiple images using the batch mode.

Details of the benefits of training an algorithm on multiple images can be found in the ``FindFoci`` paper:

| **FindFoci: A Focus Detection Algorithm with Automated Parameter Training That Closely Matches Human Assignments, Reduces Human Inconsistencies and Increases Speed of Analysis**
| Herbert AD, Carr AM, Hoffmann E.
| *PLoS ONE* 9(12) 2014, **e114749**

.. image:: https://img.shields.io/badge/doi-10.1371%2Fjournal.pone.0114749-blue
    :target: https://doi.org/10.1371/journal.pone.0114749
    :alt: DOI 10.1371/journal.pone.0114749

Full details of the ``FindFoci`` plugins can be found in the ``FindFoci User Manual`` on GitHub_.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > FindFoci`` menu.

.. index:: ! FindFoci

FindFoci
--------

A standard ``ImageJ`` plugin that operates on the currently selected image. Results are presented as a mask image, shown in tabular format and/or saved to file.

.. index:: ! FindFoci GUI

FindFoci GUI
------------

Opens a permanent window frame within ``ImageJ`` and allows selection of different images. It runs the ``FindFoci`` plugin to perform the processing work. A preview option allows the parameters to be interactively updated.

.. index:: ! FindFoci Batch

FindFoci Batch
--------------

Allows a set of parameters for the ``FindFoci`` algorithm to be applied to all the images in a directory. The algorithm is run on each image in turn and the results are saved to an output directory.

.. index:: ! FindFoci Optimiser

FindFoci Optimiser
------------------

Finds the best parameters for the ``FindFoci`` algorithm using the maxima marked on a representative image. It iterates through thousands of combinations and identifies the best parameters to find the desired peak regions. These parameters can then be easily applied to other images of the same type using the ``ImageJ`` scripting tools or the ``FindFoci Batch`` plugin.

.. index:: ! FindFoci Optimiser GUI

FindFoci Optimiser GUI
----------------------

A convenience plugin that provides an ``ImageJ`` window with a selection list of all images available for the ``FindFoci Optimiser``.

.. index:: ! FindFoci Optimiser Multi-Image

FindFoci Optimiser Multi-Image
------------------------------

Allows the ``FindFoci Optimiser`` to be run on a directory of reference images to find the best parameters across the dataset. The results for each image are saved to an output directory and can be reloaded allowing the parameters to be ranked using different scoring methods.

.. index:: ! FindFoci Helper

FindFoci Helper
---------------

An interactive plugin that aligns the manually added point Regions of Interest (ROIs) with the peaks found by the ``FindFoci`` algorithm. The plugin intercepts user clicks with the multi-point ROI tool and assigns the marked points to the closest/highest peak within a search radius.

.. index:: ! FindFoci Settings

FindFoci Settings
-----------------

Configure settings for the ``FindFoci`` algorithm, e.g. the limit on the number of potential maxima for the ``FindFoci`` algorithm.

.. index:: ! FindFoci Match Calculator

FindFoci Match Calculator
-------------------------

Allows two sets of ``FindFoci`` results to be compared. The ``FindFoci`` analysis must have been saved to memory using the ``Save to memory`` option.

.. index:: ! FindFoci Macro Extensions

FindFoci Macro Extensions
-------------------------

Registers functions that can be used in the ``ImageJ`` macro language.

.. index:: ! Match Calculator

Match Calculator
----------------

Computes the match statistics between two images marked with point ROIs. This uses the same match scores as the ``FindFoci Optimiser``: Number of matches; True positives; False positives; False negatives;
`Jaccard <https://en.wikipedia.org/wiki/Jaccard_index>`_;
`Precision; Recall <https://en.wikipedia.org/wiki/Precision_and_recall#Definition>`_;
`F-score <https://en.wikipedia.org/wiki/F-score>`_. It can be used to compare the peak identification performed by two methods, including manual assignment.

.. index:: ! File Match Calculator

File Match Calculator
---------------------

Compares the coordinates in two files and computes the match statistics.

Can read ``FindFoci`` results files; QuickPALM xls files; STORMJ xls files; and a generic CSV file.

The generic CSV file has records using one of the following formats::

    ID,T,X,Y,Z,Value
    ID,T,X,Y
    X,Y,Z

The generic file can also be tab delimited.

.. index:: ! Point Aligner

Point Aligner
-------------

``ImageJ`` plugin that aligns the marked multi-point Regions of Interest (ROIs) of an image with the peaks found by the ``FindFoci`` algorithm. Foci are aligned using an uphill gradient to the parent maxima.

.. index:: ! Point Extractor

Point Extractor
---------------

``ImageJ`` plugin that saves the marked multi-point Regions of Interest (ROIs) of an image to file.

.. index:: ! Spot Distance

Spot Distance
-------------

Detects spots within a mask/ROI region. The mask can define multiple regions using a different pixel value for each. Outputs a table of spot positions and a summary table of the number of spots with min/max/average distances.

Spots are detected using the maxima within a difference of Gaussians image. The ``Smoothing`` parameter controls the removal of image noise. The ``Feature Size`` parameter controls the expected size of the spots; contrast changes (edges) much larger than the ``Feature Size`` will be removed from the image. (Set the ``Feature Size`` to zero to disable the difference of Gaussians; spots will be detected using peaks on the smoothed image.) Optionally spots can be removed if they fail a circularity threshold.

Provides options to process the current frame or all frames in the stack. The last identified spots can be shown on the image as an overlay. When the region is defined using an area ROI, each separate region is labelled using the region counter. The counter is incremented for each region and retained between runs of the plugin allowing regions to be separated in the results.

For multi-channel images the ``Dual channel`` option detects spots in two selected channels and the summary reports the distances between spots in the first channel and spots in the second channel. When processing multiple frames the ``Track objects`` option assigns spot IDs using closest-distance matching to the spots in the previous frame, allowing the same spot to be followed through time.

.. index:: ! Spot Pairs

Spot Pairs
----------

``ImageJ`` plugin that analyses marked ROI points in an image. Find the closest pairs within a set distance of each other. Supports a preview of the pairs and saves results to a table.

.. index:: ! Mask Divider

Mask Divider
------------

Overlay a mask on an image and divide into two classes. For each object in the mask (contiguous pixels with the same value; optionally 8-connected), obtain the mean pixel value of the object and then divide the objects into two classes, either by auto-thresholding or interactively.

.. index:: ! Double Mask Divider

Double Mask Divider
-------------------

Compares two masks created using the ``Mask Divider`` with pixels of AB and A'B' and creates a new mask with pixels of AA' AB' BA' BB'.

.. index:: ! Mask Analyze Particles

Mask Analyze Particles...
-------------------------

Extend the ``ImageJ`` ``Particle Analyser`` to allow the particles to be obtained using contiguous pixels with a unique value. If blank pixels exist between two pixels with the same value then they will be treated as separate objects.

This plugin is useful on masks where each object region has a unique value, for example the output mask created by ``FindFoci``.

.. index:: ! Mask Analyze Channels

Mask Analyze Channels...
-------------------------

For each object in the mask (contiguous pixels with the same value), analyse the pixels values for each channel of a target image. Estimate the background of each channel using the lowest intensity non-object pixels within the convex hull of the objects.

.. index:: ! Spot Density

Spot Density
------------

Calculate the density of spots from a second ``FindFoci`` results set around the spots from a first results set (the sets can be the same); both are loaded from memory. Only spots within a mask region are processed. Any foci within the maximum analysis distance from the edge are excluded from analysis. Analysis is performed in 2D only.

Outputs histograms of the closest distances between spots and the density of particles around the spots (i.e. the `Pair Correlation <https://en.wikipedia.org/wiki/Radial_distribution_function>`_ function).

.. index:: ! Mask Object Dimensions

Mask Object Dimensions
----------------------

Identify the inertia tensor (principle rotation axes around the centre-of-mass) for each object and computes the minimum and maximum pixel along each axis that is inside the object. Draws the axes on the image and produces a results table. Can be used to estimate dimensions of objects in 2/3D. The plugin works best on objects that are spherical.

Objects are identified using unique pixel values (can be non-contiguous), for example the output mask created by FindFoci.

Optionally objects with centres within a merge distance (in pixels) can be iteratively joined before the dimensions are computed.

.. index:: ! Assign Foci to Objects

Assign Foci to Objects
----------------------

Finds objects in an image using contiguous pixels of the same value. Locates the closest object within a search radius to each focus and summarises the counts. Foci are taken from the last ``FindFoci`` result held in memory or from point ROIs. Analysis is performed in 2D only.

.. index:: ! Assign Foci to Clusters

Assign Foci to Clusters
-----------------------

Performs 2D clustering on the latest ``FindFoci`` results held in memory. Optionally can draw the clusters on the Find Foci output mask if this is selected when the plugin is run.

.. index:: ! Translocation Finder

Translocation Finder
--------------------

Find translocations using markers for colocalisation using 3 ``FindFoci`` results sets stored in memory. Find triplets where the two markers from channel 2 & 3 matching a foci in channel 1 are also a matching pair. Output a guess for a translocation where channel 13 distance ``<< 12|23``, no translocation where ``12 << 13|23``.

.. index:: ! Translocation Finder (Tool)

Translocation Finder (Tool)
---------------------------

Provide a tool on the ``ImageJ`` toolbar that responds to a user clicking repeatedly on the same image to identify foci for potential translocations. 3 consecutive clicks are required to mark 3 channels for analysis.

Analysis options can be set for the tool.

.. index:: ! Spot Radial Intensity

Spot Radial Intensity
---------------------

Output the radial intensity around spots within a mask region. Spots are defined using ``FindFoci`` results held in memory or a point ROI.

.. index:: ! Spot Pair Distance

Spot Pair Distance
------------------

Output the distances between the pair of spots from two channels at a user selected position. Functions using a plugin action tool added to the ``ImageJ`` toolbar.

.. index:: ! Object Foci Depth

Object Foci Depth
-----------------

Finds objects in an image using contiguous pixels of the same value. Computes the distance of each foci inside an object to the edge/surface (the foci depth).

.. index:: ! Foci Neighbour Analysis

Foci Neighbour Analysis
-----------------------

Identifies foci in a primary channel and computes summary statistics of the neighbouring foci in secondary channels.


.. _GitHub: https://github.com/aherbert/gdsc/
