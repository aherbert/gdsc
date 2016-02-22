package gdsc.analytics;

/**
 * URL builder for the tracking requests.
 */
public interface IGoogleAnalyticsURLBuilder
{
	/**
	 * Gets the version for this builder
	 * 
	 * @return The version
	 */
	public String getVersion();

	/**
	 * Build the URL request from the data
	 * 
	 * @param requestData The request data
	 * @return The URL
	 */
	public String buildURL(RequestData requestData);
}
