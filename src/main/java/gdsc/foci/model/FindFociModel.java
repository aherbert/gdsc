package gdsc.foci.model;

/*----------------------------------------------------------------------------- 
 * GDSC Plugins for ImageJ
 * 
 * Copyright (C) 2011 Alex Herbert
 * Genome Damage and Stability Centre
 * University of Sussex, UK
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *---------------------------------------------------------------------------*/

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import gdsc.foci.FindFoci;

/**
 * Provides a bean property model for the FindFoci algorithm
 */
public class FindFociModel extends AbstractModelObject
{
	private int backgroundMethod = FindFoci.BACKGROUND_AUTO_THRESHOLD;
	private double backgroundParameter = 3;
	private String thresholdMethod = "Otsu";
	private String statisticsMode = "Both";
	private int searchMethod = FindFoci.SEARCH_ABOVE_BACKGROUND;
	private double searchParameter = 0.3;
	private int minSize = 5;
	private boolean minimumAboveSaddle = true;
	private int peakMethod = FindFoci.PEAK_RELATIVE_ABOVE_BACKGROUND;
	private double peakParameter = 0.5;
	private int sortMethod = FindFoci.SORT_INTENSITY;
	private int maxPeaks = 50;
	private int showMask = 3;
	private boolean showTable = true;
	private boolean markMaxima = true;
	private boolean markROIMaxima = false;
	private boolean showMaskMaximaAsDots = true;
	private boolean showLogMessages = true;
	private boolean removeEdgeMaxima = false;
	private boolean saveResults = false;
	private String resultsDirectory = null;
	private double gaussianBlur = 0;
	private int centreMethod = FindFoci.CENTRE_MAX_VALUE_SEARCH;
	private double centreParameter = 2;
	private double fractionParameter = 0.5;

	private List<String> imageList = new ArrayList<String>();
	private String selectedImage = "";

	private List<String> maskImageList = new ArrayList<String>();
	private String maskImage = "";

	private boolean changed = false;

	/**
	 * Used to swap between the background parameter for absolute values and others
	 */
	private double backgroundParameterMemory = 0;
	/**
	 * Used to swap between the peak parameter for absolute values and others
	 */
	private double peakParameterMemory = 0;

	/**
	 * Default constructor
	 */
	public FindFociModel()
	{
		// Notify if any properties change
		this.addPropertyChangeListener(new PropertyChangeListener()
		{
			public void propertyChange(PropertyChangeEvent evt)
			{
				if (!changed && !("changed".equals(evt.getPropertyName())))
				{
					setChanged(true);
				}
			}
		});
	}

	/**
	 * @param backgroundMethod
	 *            the backgroundMethod to set
	 */
	public void setBackgroundMethod(int backgroundMethod)
	{
		int oldValue = this.backgroundMethod;
		this.backgroundMethod = backgroundMethod;
		firePropertyChange("backgroundmethod", oldValue, this.backgroundMethod);

		// Check if this is a switch to/from absolute background
		if (oldValue != backgroundMethod &&
				(oldValue == FindFoci.BACKGROUND_ABSOLUTE || backgroundMethod == FindFoci.BACKGROUND_ABSOLUTE))
		{
			double current = backgroundParameter;
			setBackgroundParameter(backgroundParameterMemory);
			backgroundParameterMemory = current;
		}
	}

	/**
	 * @return the backgroundMethod
	 */
	public int getBackgroundMethod()
	{
		return backgroundMethod;
	}

	/**
	 * @param backgroundParameter
	 *            the backgroundParameter to set
	 */
	public void setBackgroundParameter(double backgroundParameter)
	{
		double oldValue = this.backgroundParameter;
		this.backgroundParameter = backgroundParameter;
		firePropertyChange("backgroundParameter", oldValue, this.backgroundParameter);
	}

	/**
	 * @return the backgroundParameter
	 */
	public double getBackgroundParameter()
	{
		return backgroundParameter;
	}

	/**
	 * @param thresholdMethod
	 *            the thresholdMethod to set
	 */
	public void setThresholdMethod(String thresholdMethod)
	{
		String oldValue = this.thresholdMethod;
		this.thresholdMethod = thresholdMethod;
		firePropertyChange("thresholdMethod", oldValue, this.thresholdMethod);
	}

	/**
	 * @return the thresholdMethod
	 */
	public String getThresholdMethod()
	{
		return thresholdMethod;
	}

	/**
	 * @param statisticsMode
	 *            the statisticsMode to set
	 */
	public void setStatisticsMode(String statisticsMode)
	{
		String oldValue = this.statisticsMode;
		this.statisticsMode = statisticsMode;
		firePropertyChange("statisticsMode", oldValue, this.statisticsMode);
	}

	/**
	 * @return the statisticsMode
	 */
	public String getStatisticsMode()
	{
		return statisticsMode;
	}

	/**
	 * @param searchMethod
	 *            the searchMethod to set
	 */
	public void setSearchMethod(int searchMethod)
	{
		int oldValue = this.searchMethod;
		this.searchMethod = searchMethod;
		firePropertyChange("searchMethod", oldValue, this.searchMethod);
	}

	/**
	 * @return the searchMethod
	 */
	public int getSearchMethod()
	{
		return searchMethod;
	}

	/**
	 * @param searchParameter
	 *            the searchParameter to set
	 */
	public void setSearchParameter(double searchParameter)
	{
		double oldValue = this.searchParameter;
		this.searchParameter = searchParameter;
		firePropertyChange("searchParameter", oldValue, this.searchParameter);
	}

	/**
	 * @return the searchParameter
	 */
	public double getSearchParameter()
	{
		return searchParameter;
	}

	/**
	 * @param minSize
	 *            the minSize to set
	 */
	public void setMinSize(int minSize)
	{
		int oldValue = this.minSize;
		this.minSize = minSize;
		firePropertyChange("minSize", oldValue, this.minSize);
	}

	/**
	 * @return the minSize
	 */
	public int getMinSize()
	{
		return minSize;
	}

	/**
	 * @param minimumAboveSaddle
	 *            the minimumAboveSaddle to set
	 */
	public void setMinimumAboveSaddle(boolean minimumAboveSaddle)
	{
		boolean oldValue = this.minimumAboveSaddle;
		this.minimumAboveSaddle = minimumAboveSaddle;
		firePropertyChange("minimumAboveSaddle", oldValue, this.minimumAboveSaddle);
	}

	/**
	 * @return the minimumAboveSaddle
	 */
	public boolean isMinimumAboveSaddle()
	{
		return minimumAboveSaddle;
	}

	/**
	 * @param peakMethod
	 *            the peakMethod to set
	 */
	public void setPeakMethod(int peakMethod)
	{
		int oldValue = this.peakMethod;
		this.peakMethod = peakMethod;
		firePropertyChange("peakMethod", oldValue, this.peakMethod);

		// Check if this is a switch to/from absolute background
		if (oldValue != peakMethod && (oldValue == FindFoci.PEAK_ABSOLUTE || peakMethod == FindFoci.PEAK_ABSOLUTE))
		{
			double current = peakParameter;
			setPeakParameter(peakParameterMemory);
			peakParameterMemory = current;
		}
	}

	/**
	 * @return the peakMethod
	 */
	public int getPeakMethod()
	{
		return peakMethod;
	}

	/**
	 * @param peakParameter
	 *            the peakParameter to set
	 */
	public void setPeakParameter(double peakParameter)
	{
		double oldValue = this.peakParameter;
		this.peakParameter = peakParameter;
		firePropertyChange("peakParameter", oldValue, this.peakParameter);
	}

	/**
	 * @return the peakParameter
	 */
	public double getPeakParameter()
	{
		return peakParameter;
	}

	/**
	 * @param sortMethod
	 *            the sortMethod to set
	 */
	public void setSortMethod(int sortMethod)
	{
		double oldValue = this.sortMethod;
		this.sortMethod = sortMethod;
		firePropertyChange("sortMethod", oldValue, this.sortMethod);
	}

	/**
	 * @return the sortMethod
	 */
	public int getSortMethod()
	{
		return sortMethod;
	}

	/**
	 * @param maxPeaks
	 *            the maxPeaks to set
	 */
	public void setMaxPeaks(int maxPeaks)
	{
		double oldValue = this.maxPeaks;
		this.maxPeaks = maxPeaks;
		firePropertyChange("maxPeaks", oldValue, this.maxPeaks);
	}

	/**
	 * @return the maxPeaks
	 */
	public int getMaxPeaks()
	{
		return maxPeaks;
	}

	/**
	 * @param showMask
	 *            the showMask to set
	 */
	public void setShowMask(int showMask)
	{
		int oldValue = this.showMask;
		this.showMask = showMask;
		firePropertyChange("showMask", oldValue, this.showMask);
	}

	/**
	 * @return the showMask
	 */
	public int getShowMask()
	{
		return showMask;
	}

	/**
	 * @param showTable
	 *            the showTable to set
	 */
	public void setShowTable(boolean showTable)
	{
		boolean oldValue = this.showTable;
		this.showTable = showTable;
		firePropertyChange("showTable", oldValue, this.showTable);
	}

	/**
	 * @return the showTable
	 */
	public boolean isShowTable()
	{
		return showTable;
	}

	/**
	 * @param markMaxima
	 *            the markMaxima to set
	 */
	public void setMarkMaxima(boolean markMaxima)
	{
		boolean oldValue = this.markMaxima;
		this.markMaxima = markMaxima;
		firePropertyChange("markMaxima", oldValue, this.markMaxima);
	}

	/**
	 * @return the markMaxima
	 */
	public boolean isMarkMaxima()
	{
		return markMaxima;
	}

	/**
	 * @param markROIMaxima
	 *            the markROIMaxima to set
	 */
	public void setMarkROIMaxima(boolean markROIMaxima)
	{
		boolean oldValue = this.markROIMaxima;
		this.markROIMaxima = markROIMaxima;
		firePropertyChange("markROIMaxima", oldValue, this.markROIMaxima);
	}

	/**
	 * @return the markROIMaxima
	 */
	public boolean isMarkROIMaxima()
	{
		return markROIMaxima;
	}

	/**
	 * @return the showROIMaximaAsDots
	 */
	public boolean isShowROIMaximaAsDots()
	{
		return showMaskMaximaAsDots;
	}

	/**
	 * @param showMaskMaximaAsDots
	 *            the showMaskMaximaAsDots to set
	 */
	public void setShowMaskMaximaAsDots(boolean showMaskMaximaAsDots)
	{
		boolean oldValue = this.showMaskMaximaAsDots;
		this.showMaskMaximaAsDots = showMaskMaximaAsDots;
		firePropertyChange("showROIMaximaAsDots", oldValue, this.showMaskMaximaAsDots);
	}

	/**
	 * @param showLogMessages
	 *            the showLogMessages to set
	 */
	public void setShowLogMessages(boolean showLogMessages)
	{
		boolean oldValue = this.showLogMessages;
		this.showLogMessages = showLogMessages;
		firePropertyChange("showLogMessages", oldValue, this.showLogMessages);
	}

	/**
	 * @return the showLogMessages
	 */
	public boolean isShowLogMessages()
	{
		return showLogMessages;
	}

	/**
	 * @param removeEdgeMaxima
	 *            the removeEdgeMaxima to set
	 */
	public void setRemoveEdgeMaxima(boolean removeEdgeMaxima)
	{
		boolean oldValue = this.removeEdgeMaxima;
		this.removeEdgeMaxima = removeEdgeMaxima;
		firePropertyChange("removeEdgeMaxima", oldValue, this.removeEdgeMaxima);
	}

	/**
	 * @return the removeEdgeMaxima
	 */
	public boolean isRemoveEdgeMaxima()
	{
		return removeEdgeMaxima;
	}

	/**
	 * @param saveResults
	 *            the saveResults to set
	 */
	public void setSaveResults(boolean saveResults)
	{
		boolean oldValue = this.saveResults;
		this.saveResults = saveResults;
		firePropertyChange("saveResults", oldValue, this.saveResults);
	}

	/**
	 * @return the saveResults
	 */
	public boolean isSaveResults()
	{
		return saveResults;
	}

	/**
	 * @param resultsDirectory
	 *            the resultsDirectory to set
	 */
	public void setResultsDirectory(String resultsDirectory)
	{
		String oldValue = this.resultsDirectory;
		this.resultsDirectory = resultsDirectory;
		firePropertyChange("resultsDirectory", oldValue, this.resultsDirectory);
	}

	/**
	 * @return the resultsDirectory
	 */
	public String getResultsDirectory()
	{
		return resultsDirectory;
	}

	/**
	 * @param gaussianBlur
	 *            the gaussianBlur to set
	 */
	public void setGaussianBlur(double gaussianBlur)
	{
		double oldValue = this.gaussianBlur;
		this.gaussianBlur = gaussianBlur;
		firePropertyChange("gaussianBlur", oldValue, this.gaussianBlur);
	}

	/**
	 * @return the gaussianBlur
	 */
	public double getGaussianBlur()
	{
		return gaussianBlur;
	}

	/**
	 * @param centreMethod
	 *            the centreMethod to set
	 */
	public void setCentreMethod(int centreMethod)
	{
		double oldValue = this.centreMethod;
		this.centreMethod = centreMethod;
		firePropertyChange("centreMethod", oldValue, this.centreMethod);
	}

	/**
	 * @return the centreMethod
	 */
	public int getCentreMethod()
	{
		return centreMethod;
	}

	/**
	 * @param centreParameter
	 *            the centreParameter to set
	 */
	public void setCentreParameter(double centreParameter)
	{
		double oldValue = this.centreParameter;
		this.centreParameter = centreParameter;
		firePropertyChange("centreParameter", oldValue, this.centreParameter);
	}

	/**
	 * @return the centreParameter
	 */
	public double getCentreParameter()
	{
		return centreParameter;
	}

	/**
	 * @param fractionParameter
	 *            the fractionParameter to set
	 */
	public void setFractionParameter(double fractionParameter)
	{
		double oldValue = this.fractionParameter;
		this.fractionParameter = fractionParameter;
		firePropertyChange("fractionParameter", oldValue, this.fractionParameter);
	}

	/**
	 * @return the fractionParameter
	 */
	public double getFractionParameter()
	{
		return fractionParameter;
	}

	/**
	 * @param imageList
	 *            The imageList to set
	 */
	public void setImageList(List<String> imageList)
	{
		List<String> oldValue = this.imageList;
		this.imageList = imageList;

		if (!imageList.equals(oldValue))
		{
			String oldSelectedImage = this.selectedImage;

			// The image list has changed - Notify bound properties
			firePropertyChange("imageList", oldValue, imageList);

			// Check if the selected image still exists
			if (imageList.contains(oldSelectedImage))
			{
				setSelectedImage(oldSelectedImage);
			}
			else
			{
				setSelectedImage(imageList.isEmpty() ? "" : imageList.get(0));
			}
		}
	}

	/**
	 * @return the imageList
	 */
	public List<String> getImageList()
	{
		return imageList;
	}

	/**
	 * @param selectedImage
	 *            the selectedImage to set
	 */
	public void setSelectedImage(String selectedImage)
	{
		String oldValue = this.selectedImage;
		this.selectedImage = selectedImage;
		firePropertyChange("selectedImage", oldValue, this.selectedImage);
	}

	/**
	 * @return the selectedImage
	 */
	public String getSelectedImage()
	{
		return selectedImage;
	}

	/**
	 * @param maskImageList
	 *            The maskImageList to set
	 */
	public void setMaskImageList(List<String> maskImageList)
	{
		List<String> oldValue = this.maskImageList;
		this.maskImageList = maskImageList;

		if (!maskImageList.equals(oldValue))
		{
			String oldMaskImage = this.maskImage;

			// The image list has changed - Notify bound properties
			firePropertyChange("maskImageList", oldValue, maskImageList);

			// Check if the selected image still exists
			if (maskImageList.contains(oldMaskImage))
			{
				setMaskImage(oldMaskImage);
			}
			else
			{
				setMaskImage(maskImageList.isEmpty() ? "" : maskImageList.get(0));
			}
		}
	}

	/**
	 * @return the imageList
	 */
	public List<String> getMaskImageList()
	{
		return maskImageList;
	}

	/**
	 * @param maskImage
	 *            the maskImage to set
	 */
	public void setMaskImage(String maskImage)
	{
		String oldValue = this.maskImage;
		this.maskImage = maskImage;
		firePropertyChange("maskImage", oldValue, this.maskImage);
	}

	/**
	 * @return the maskImage
	 */
	public String getMaskImage()
	{
		return maskImage;
	}

	/**
	 * Sets the current state of the FindFoci model to unchanged
	 */
	public void setUnchanged()
	{
		setChanged(false);
	}

	/**
	 * @param newValue
	 *            The new changed value
	 */
	private void setChanged(boolean newValue)
	{
		boolean oldValue = this.changed;
		this.changed = newValue;
		firePropertyChange("changed", oldValue, newValue);
	}

	/**
	 * @return the changed
	 */
	public boolean isChanged()
	{
		return changed;
	}

	/**
	 * Cause a property changed event to be created with the property name set to "valid".
	 * This does not alter any values in the model but serves as a mechanism to raise an event that signals that any
	 * calculations based on the model should be refreshed. For example it can be used to signal that a new
	 * image has been set without changing the selectedImage property.
	 */
	public void invalidate()
	{
		firePropertyChange("valid", true, false);
	}

	/**
	 * Performs a deep copy
	 * 
	 * @return A copy of this object
	 */
	public FindFociModel deepCopy()
	{
		//		FindFociModel newModel;
		//		try
		//		{
		//			// Copy primitives with the clone() method
		//			newModel = (FindFociModel) super.clone();
		//
		//			// Create duplicates of the objects
		//			newModel.imageList = new ArrayList<String>(this.imageList.size());
		//			for (String item : this.imageList)
		//			{
		//				newModel.imageList.add(item);
		//			}
		//			newModel.maskImageList = new ArrayList<String>(this.maskImageList.size());
		//			for (String item : this.maskImageList)
		//			{
		//				newModel.maskImageList.add(item);
		//			}
		//			
		//			return newModel;
		//		}
		//		catch (CloneNotSupportedException e)
		//		{
		//			return null;
		//		}

		FindFociModel newModel = new FindFociModel();
		newModel.backgroundMethod = this.backgroundMethod;
		newModel.backgroundParameter = this.backgroundParameter;
		newModel.thresholdMethod = this.thresholdMethod;
		newModel.statisticsMode = this.statisticsMode;
		newModel.searchMethod = this.searchMethod;
		newModel.searchParameter = this.searchParameter;
		newModel.minSize = this.minSize;
		newModel.minimumAboveSaddle = this.minimumAboveSaddle;
		newModel.peakMethod = this.peakMethod;
		newModel.peakParameter = this.peakParameter;
		newModel.sortMethod = this.sortMethod;
		newModel.maxPeaks = this.maxPeaks;
		newModel.showMask = this.showMask;
		newModel.showMaskMaximaAsDots = this.showMaskMaximaAsDots;
		newModel.showTable = this.showTable;
		newModel.markMaxima = this.markMaxima;
		newModel.markROIMaxima = this.markROIMaxima;
		newModel.showLogMessages = this.showLogMessages;
		newModel.removeEdgeMaxima = this.removeEdgeMaxima;
		newModel.saveResults = this.saveResults;
		newModel.resultsDirectory = this.resultsDirectory;
		newModel.gaussianBlur = this.gaussianBlur;
		newModel.centreMethod = this.centreMethod;
		newModel.centreParameter = this.centreParameter;
		newModel.fractionParameter = this.fractionParameter;
		newModel.selectedImage = this.selectedImage;
		newModel.maskImage = this.maskImage;
		newModel.changed = this.changed;
		newModel.backgroundParameterMemory = this.backgroundParameterMemory;
		newModel.peakParameterMemory = this.peakParameterMemory;

		newModel.imageList = new ArrayList<String>(this.imageList.size());
		for (String item : this.imageList)
		{
			newModel.imageList.add(item);
		}
		newModel.maskImageList = new ArrayList<String>(this.maskImageList.size());
		for (String item : this.maskImageList)
		{
			newModel.maskImageList.add(item);
		}

		return newModel;
	}
}
