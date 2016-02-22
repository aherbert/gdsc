package gdsc.analytics;

/**
 * Common client data. Allows caching of the client component of the Google Analytics URL.
 */
public class ClientData
{
	private final String trackingCode;
	private String encoding = "UTF-8";
	private String screenResolution = null;
	private String userLanguage = null;
	private String hostName = null;
	private SessionData sessionData;
	private String url = null;

	/**
	 * constructs with the tracking code and a new session data.
	 * 
	 * @param trackingCode
	 */
	public ClientData(String trackingCode)
	{
		this(trackingCode, SessionData.newSessionData());
	}

	/**
	 * constructs with the tracking code using the provided session data.
	 * 
	 * @param trackingCode
	 */
	public ClientData(String trackingCode, SessionData sessionData)
	{
		if (trackingCode == null)
		{
			throw new RuntimeException("Tracking code cannot be null");
		}
		this.trackingCode = trackingCode;
		this.sessionData = sessionData;
	}

	/**
	 * @return the trackingCode
	 */
	public String getTrackingCode()
	{
		return trackingCode;
	}

	/**
	 * @return the encoding
	 */
	public String getEncoding()
	{
		return encoding;
	}

	/**
	 * @return the screenResolution
	 */
	public String getScreenResolution()
	{
		return screenResolution;
	}

	/**
	 * @return the userLanguage
	 */
	public String getUserLanguage()
	{
		return userLanguage;
	}

	/**
	 * @return The hostname
	 */
	public String getHostName()
	{
		return hostName;
	}

	/**
	 * @return the session data, used to track unique sessions
	 */
	public SessionData getSessionData()
	{
		return sessionData;
	}

	/**
	 * Sets the character encoding of the client. like UTF-8
	 * 
	 * @param argEncoding
	 *            the encoding to set
	 */
	public void setEncoding(String argEncoding)
	{
		encoding = argEncoding;
	}

	/**
	 * Sets the screen resolution, like "1280x800".
	 * 
	 * @param argScreenResolution
	 *            the screenResolution to set
	 */
	public void setScreenResolution(String argScreenResolution)
	{
		screenResolution = argScreenResolution;
	}

	/**
	 * Sets the user language, like "EN-us"
	 * 
	 * @param argUserLanguage
	 *            the userLanguage to set
	 */
	public void setUserLanguage(String argUserLanguage)
	{
		userLanguage = argUserLanguage;
	}

	/**
	 * Set the hostname
	 * 
	 * @param hostName
	 *            the hostname
	 */
	public void setHostName(String hostName)
	{
		this.hostName = hostName;
	}

	/**
	 * @return The client component of the URL
	 */
	public String getUrl()
	{
		return url;
	}

	/**
	 * @param url The client component of the URL
	 */
	public void setUrl(String url)
	{
		this.url = url;
	}
}