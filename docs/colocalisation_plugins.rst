.. index:: ! Colocalisation Plugins

Colocalisation Plugins
======================

The GDSC Colocalisation plugins provide various tools to perform colocalisation
analysis. The tools provide: thresholding for N-dimensional images for signal
identification; correlation and overlap coefficient analysis; and
colocalisation significance testing.

Full details of the Colocalisation plugins can be found in the ``Colocalisation User Manual`` on GitHub_.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Colocalisation`` menu.

.. index:: ! Stack Threshold

Stack Threshold
---------------

Processes an image stack and applies thresholding to create a mask for each channel+frame combination. This allows thresholding to be applied to a 5D image on a per frame basis and can be used to define the signal region of each channel.

.. index:: ! Colocalisation Threshold

Colocalisation Threshold
------------------------

Compares two images for correlated pixel intensities. If the two images are correlated a search is performed to identify the threshold below which the two images are not correlated.

.. index:: ! CDA

CDA
---

Uses the Confined Displacement Algorithm (CDA) to compute the significance of colocalisation metrics (Manders coefficient and correlation coefficient).

This is a modified version of the original ``ImageJ`` CDA plugin by Maria Osorio-Reich (see :ref:`references:References`). The code to translate images was reimplemented to allow efficient parallel execution extending support for larger maximum shifts with the same run time.

.. index:: ! CDA (Macro)

CDA (macro)
-----------

Provides support for the CDA plugin within ``ImageJ`` macros (i.e. macro recording and execution).

.. index:: ! Stack Correlation Analyser

Stack Correlation Analyser
--------------------------

Processes a stack image with multiple channels. Extracts all the channels and frames and performs:

- Thresholding to create a mask for each channel+frame
- All-vs-all channel correlation within the union/intersect of the channel masks

.. index:: ! Stack Colocalisation Analyser

Stack Colocalisation Analyser
-----------------------------

Processes a stack image with multiple channels. Each frame is processed separately. Extracts all the channels (collating z-stacks) and performs:

- Thresholding to create a mask for each channel
- CDA analysis of channel 1-vs-channel 2, optionally within the region defined by channel 3

.. index:: ! Particle Overlap

Particle Overlap
----------------

For all particles in a mask (defined by their unique pixel value), count the overlap with a second mask image (e.g. a second set of particles). An intensity image is used to calculate the Manders coefficient of each particle.

.. index:: ! Particle Correlation

Particle Correlation
--------------------

For all particles in a mask (defined by contiguous pixels with the same value), sum the pixel intensity in two different images and then perform a correlation analysis.

.. index:: ! Colocated Mask

Colocated Mask
--------------

Create a colocated mask from two source images using the threshold or minimum display value from each image to define their image mask. The two image masks are combined using an AND or OR operator.

Supports a live preview to allow interactively setting the threshold on the source images with live mask update.

.. _GitHub: https://github.com/aherbert/gdsc/
