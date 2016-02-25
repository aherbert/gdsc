package gdsc.analytics;

import java.util.Arrays;

/*
 * <ul>
 * <li>Copyright (c) 2010 Daniel Murphy
 * <li>Copyright (c) 2016 Alex Herbert
 * </ul>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * @see https://code.google.com/archive/p/jgoogleanalyticstracker/
 */

/**
 * Google Analytics request data
 * 
 * @author Alex Herbert
 */
public class RequestData
{
	private String[] labels = null;
	private String[] values = null;
	private int size = 0;
	private String customVariablesURL = null;
	private String pageTitle = null;
	private String pageURL = null;

	/**
	 * Add a custom variable
	 * 
	 * @param label
	 * @param value
	 */
	public void addCustomVariable(String label, String value)
	{
		if (label == null || value == null)
			return;
		if (labels == null)
		{
			labels = new String[1];
			values = new String[1];
		}
		else if (labels.length == size)
		{
			final int newSize = (int) (size * 1.5) + 1;
			labels = Arrays.copyOf(labels, newSize);
			values = Arrays.copyOf(values, newSize);
		}
		labels[size] = label;
		values[size] = value;
		size++;

		setCustomVariablesURL(null);
	}

	/**
	 * @return the number of custom variables
	 */
	public int getVariableCount()
	{
		return size;
	}

	/**
	 * Note that the size of the returned array may be larger than {@link #getVariableCount()}
	 * 
	 * @return the labels (used for custom variables)
	 */
	public String[] getLabels()
	{
		return labels;
	}

	/**
	 * Note that the size of the returned array may be larger than {@link #getVariableCount()}
	 * 
	 * @return the values (used for custom variables)
	 */
	public String[] getValues()
	{
		return values;
	}

	/**
	 * Sets the page title, which will be the Content Title in Google Analytics
	 * 
	 * @param argContentTitle
	 *            the contentTitle to set
	 */
	public void setPageTitle(String argContentTitle)
	{
		pageTitle = argContentTitle;
	}

	/**
	 * @return the page title
	 */
	public String getPageTitle()
	{
		return pageTitle;
	}

	/**
	 * The page url, which is required. Traditionally this is of the form "/content/page.html", but you can
	 * put anything here (like "/com/dmurph/test.java").
	 * 
	 * @param argPageURL
	 *            the pageURL to set
	 */
	public void setPageURL(String argPageURL)
	{
		pageURL = argPageURL;
	}

	/**
	 * @return the page URL
	 */
	public String getPageURL()
	{
		return pageURL;
	}

	/**
	 * Get the cached custom variables URL
	 * 
	 * @return the customVariablesURL
	 */
	public String getCustomVariablesURL()
	{
		return customVariablesURL;
	}

	/**
	 * Cache the custom variables URL. Allows re-use of this object for a different page request.
	 * 
	 * @param customVariablesURL
	 *            the customVariablesURL to set
	 */
	public void setCustomVariablesURL(String customVariablesURL)
	{
		this.customVariablesURL = customVariablesURL;
	}
}
