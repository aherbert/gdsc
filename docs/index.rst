.. index:: ! GDSC ImageJ Plugins

GDSC ImageJ Plugins
===================

.. image:: https://github.com/aherbert/gdsc/actions/workflows/build.yml/badge.svg
    :target: https://github.com/aherbert/gdsc/actions/workflows/build.yml
    :alt: Build Status

.. image:: https://readthedocs.org/projects/gdsc-imagej/badge/?version=latest
    :target: https://gdsc-imagej.readthedocs.io/en/latest/?badge=latest
    :alt: Documentation Status

.. image:: https://img.shields.io/badge/License-GPL%20v3-blue.svg
    :target: https://www.gnu.org/licenses/gpl-3.0
    :alt: License: GPL v3

The GDSC ImageJ plugins are a collection of analysis programs for microscopy images including colocalisation analysis and peak finding. These run as plugins within `ImageJ <https://imagej.net/ij/index.html>`_, an image processing and analysis program using Java.

The plugins are easily :ref:`installed <installation:Installation>` into ``ImageJ``.
The library works for Java 11+. This is the java specification used by `SciJava <https://scijava.org/>`_ for participating projects as of 2026 (see `org.scijava:pom-scijava <https://mvnrepository.com/artifact/org.scijava/pom-scijava>`_). It is recommended that all ``ImageJ`` plugins use this specification to maximise compatibility.

The plugins were developed at the `Genome Damage and Stability Centre <https://www.sussex.ac.uk/research/centres/genome-damage-and-stability-centre/>`_ at the University of Sussex, UK. The source code is hosted on GitHub_.

The plugins can be incorporated into ``ImageJ`` macros to allow automation of image analysis workflows.

The following plugins are available using the order presented on the ``ImageJ > Plugins > GDSC`` menu:

Cells Plugins
-------------

The :ref:`cells_plugins:Cells Plugins`
provide tools for analysis of cells from microscopy images.

Colocalisation Analysis
-----------------------

The :ref:`colocalisation_plugins:Colocalisation Plugins`
provide various tools to perform colocalisation
analysis. The tools include:

- Thresholding for N-dimensional images for signal identification
- Correlation and overlap coefficient analysis
- Colocalisation significance testing

Find Foci
---------

The :ref:`findfoci_plugins:Find Foci Plugins`
allow the identification of peak intensity regions within
2D and 3D images. The tools include:

- Automated and semi-automated labelling of peaks
- Comparison of marked points between images
- Optimisation of algorithm parameters
- Batch analysis of images
- Alignment of manually marked points to peak maxima

Thresholding Plugins
--------------------

The :ref:`thresholding_plugins:Thresholding Plugins`
provide various tools for thresholding and mask generation.

Utility Plugins
---------------

The :ref:`utility_plugins:Utility Plugins`
provide various utility tools for image analysis. The tools
include:

- Difference of Gaussians and Laplacian of Gaussian filters for contrast enhancement
- Image alignment
- Mask conversion and measurement tools

..
  No index

Contents
========

.. toctree::
    :numbered:
    :maxdepth: 4

    installation
    imagej_plugins
    cells_plugins
    colocalisation_plugins
    findfoci_plugins
    thresholding_plugins
    utility_plugins
    references

.. toctree::
    :hidden:

    change_log


..
  No index

Index
=====

* :ref:`genindex`
* :ref:`search`


.. index:: ! Issues

Issues
======

Please file a bug report on GitHub_.

.. _GitHub: https://github.com/aherbert/gdsc/
