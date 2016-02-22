package gdsc.analytics;

/**
 * Google Analytics request data
 */
public class RequestData
{
	public enum Type
	{
		EVENT("event"), TRANSACTION("transaction"), ITEM("item"), CUSTOM_VARIABLE("custom_variable"), PAGE("page");

		private String name;

		private Type(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private Type type = Type.PAGE;
	private String value = null;
	private String pageTitle = null;
	private String pageURL = null;

	//	utmcsr
	//	Identifies a search engine, newsletter name, or other source specified in the
	//	utm_source query parameter See the �Marketing Campaign Tracking�
	//	section for more information about query parameters.
	//
	//	utmccn
	//	Stores the campaign name or value in the utm_campaign query parameter.
	//
	//	utmctr
	//	Identifies the keywords used in an organic search or the value in the utm_term query parameter.
	//
	//	utmcmd
	//	A campaign medium or value of utm_medium query parameter.
	//
	//	utmcct
	//	Campaign content or the content of a particular ad (used for A/B testing)
	//	The value from utm_content query parameter.
	// referal:
	//utmcsr=forums.jinx.com|utmcct=/topic.asp|utmcmd=referral
	//utmcsr=rolwheels.com|utmccn=(referral)|utmcmd=referral|utmcct=/rol_dhuez_wheels.php
	// search:
	// utmcsr=google|utmccn=(organic)|utmcmd=organic|utmctr=rol%20wheels

	// utmcsr%3D(direct)%7Cutmccn%D(direct)%7utmcmd%3D(none)

	//	private String utmcsr = "(direct)";
	//	private String utmccn = "(direct)";
	//	private String utmctr = null;
	//	private String utmcmd = "(none)";
	//	private String utmcct = null;

	/**
	 * @param type
	 *            The type of request (defaults to page)
	 */
	public void setType(Type type)
	{
		this.type = type;
	}

	/**
	 * @return The type of request
	 */
	public String getType()
	{
		return type.toString();
	}

	/**
	 * @param value
	 *            the value (used for events or custom variables)
	 */
	public void setValue(String value)
	{
		this.value = value;
	}

	/**
	 * @return the value (used for events or custom variables)
	 */
	public String getValue()
	{
		return value;
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
}
