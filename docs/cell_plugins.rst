.. index:: ! Cell Plugins

Cell Plugins
============

The GDSC plugins provide various tools for analysis of cells from microscopy images.

The plugins are described in the following sections using the order presented on the
``Plugins > GDSC > Cell`` menu.

.. index:: ! Nuclei Outline

Nuclei Outline
--------------

Outline nuclei across multiple frames. Performs the following:

- Filter the image (Gaussian blur).
- Mask all the nuclei using thresholding.
- Fill holes and filter outliers.
- Remove small nuclei based on size.
- Divide large nuclei based on size using watershed.
- Provides save options: ``Overlay ROI``; ``Save to memory``; and ``Results Table``
