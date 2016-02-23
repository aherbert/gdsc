package gdsc.analytics;

/*
 * <ul>
 * <li>Copyright (c) 2010 Daniel Murphy, Stefan Brozinski
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

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.net.SocketAddress;
import java.net.URL;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;
import java.util.regex.MatchResult;

/**
 * Common tracking calls are implemented as methods, but if you want to control
 * what data to send, then use {@link #makeCustomRequest(AnalyticsRequestData)}.
 * If you are making custom calls, the only requirements are:
 * <ul>
 * <li>If you are tracking an event, {@link AnalyticsRequestData#setEventCategory(String)} and
 * {@link AnalyticsRequestData#setEventAction(String)} must both be populated.</li>
 * <li>If you are not tracking an event, {@link AnalyticsRequestData#setPageURL(String)} must be populated</li>
 * </ul>
 * See the <a
 * href=http://code.google.com/intl/en-US/apis/analytics/docs/tracking/gaTrackingTroubleshooting.html#gifParameters>
 * Google Troubleshooting Guide</a> for more info on the tracking parameters (although it doesn't seem to be fully
 * updated).
 * <p>
 * The tracker can operate in three modes:
 * <ul>
 * <li>synchronous mode: The HTTP request is sent to GA immediately, before the track method returns. This may slow your
 * application down if GA doesn't respond fast.
 * <li>multi-thread mode: Each track method call creates a new short-lived thread that sends the HTTP request to GA in
 * the background and terminates.
 * <li>single-thread mode (the default): The track method stores the request in a FIFO and returns immediately. A single
 * long-lived background thread consumes the FIFO content and sends the HTTP requests to GA.
 * </ul>
 * </p>
 * <p>
 * To halt the background thread safely, use the call {@link #stopBackgroundThread(long)}, where the parameter is the
 * timeout to wait for any remaining queued tracking calls to be made. Keep in mind that if new tracking requests are
 * made after the thread is stopped, they will just be stored in the queue, and will not be sent to GA until the thread
 * is started again with {@link #startBackgroundThread()} (This is assuming you are in single-threaded mode to begin
 * with).
 * </p>
 * <p>
 * Note: This class has been forked from the JGoogleAnalyticsTracker project and modified by Alex Herbert to alter the
 * data sent to Google Analytics and remove the slfj dependency. The architecture for dispatching messages is unchanged.
 * </p>
 * 
 * @author Daniel Murphy, Stefan Brozinski, Alex Herbert
 */
public class GoogleAnalyticsTracker
{
	public static enum DispatchMode
	{
		/**
		 * Each tracking call will wait until the http request
		 * completes before returning
		 */
		SYNCHRONOUS,
		/**
		 * Each tracking call spawns a new thread to make the http request
		 */
		MULTI_THREAD,
		/**
		 * Each tracking request is added to a queue, and a single dispatch thread makes the requests.
		 */
		SINGLE_THREAD
	}

	private static Logger logger = new Logger();
	private static final ThreadGroup asyncThreadGroup = new ThreadGroup("Async Google Analytics Threads");
	private static long asyncThreadsRunning = 0;
	private static Proxy proxy = Proxy.NO_PROXY;
	private static Queue<String> fifo = new LinkedList<String>();
	private static Thread backgroundThread = null; // the thread used in 'queued' mode.
	private static boolean backgroundThreadMayRun = false;

	static
	{
		asyncThreadGroup.setMaxPriority(Thread.MIN_PRIORITY);
		asyncThreadGroup.setDaemon(true);
	}

	public static enum GoogleAnalyticsVersion
	{
		V_4_7_2
	}

	private GoogleAnalyticsVersion version;
	private ClientData clientData;
	private IGoogleAnalyticsURLBuilder builder;
	private DispatchMode mode;
	private boolean enabled;

	public GoogleAnalyticsTracker(ClientData clientData, GoogleAnalyticsVersion version)
	{
		this(clientData, version, DispatchMode.SINGLE_THREAD);
	}

	public GoogleAnalyticsTracker(ClientData clientData, GoogleAnalyticsVersion version, DispatchMode dispatchMode)
	{
		this.version = version;
		this.clientData = clientData;
		createBuilder();
		enabled = true;
		setDispatchMode(dispatchMode);
	}

	/**
	 * Sets the dispatch mode
	 * 
	 * @see DispatchMode
	 * @param argMode
	 *            the mode to to put the tracker in. If this is null, the tracker
	 *            defaults to {@link DispatchMode#SINGLE_THREAD}
	 */
	public void setDispatchMode(DispatchMode argMode)
	{
		if (argMode == null)
		{
			argMode = DispatchMode.SINGLE_THREAD;
		}
		if (argMode == DispatchMode.SINGLE_THREAD)
		{
			startBackgroundThread();
		}
		mode = argMode;
	}

	/**
	 * Gets the current dispatch mode. Default is {@link DispatchMode#SINGLE_THREAD}.
	 * 
	 * @see DispatchMode
	 * @return
	 */
	public DispatchMode getDispatchMode()
	{
		return mode;
	}

	/**
	 * Convenience method to check if the tracker is in synchronous mode.
	 * 
	 * @return
	 */
	public boolean isSynchronous()
	{
		return mode == DispatchMode.SYNCHRONOUS;
	}

	/**
	 * Convenience method to check if the tracker is in single-thread mode
	 * 
	 * @return
	 */
	public boolean isSingleThreaded()
	{
		return mode == DispatchMode.SINGLE_THREAD;
	}

	/**
	 * Convenience method to check if the tracker is in multi-thread mode
	 * 
	 * @return
	 */
	public boolean isMultiThreaded()
	{
		return mode == DispatchMode.MULTI_THREAD;
	}

	/**
	 * Resets the session cookie.
	 */
	public void resetSession()
	{
		clientData.getSessionData().newSession();
	}

	/**
	 * Sets if the api dispatches tracking requests.
	 * 
	 * @param argEnabled
	 */
	public void setEnabled(boolean argEnabled)
	{
		enabled = argEnabled;
	}

	/**
	 * If the api is dispatching tracking requests (default of true).
	 * 
	 * @return
	 */
	public boolean isEnabled()
	{
		return enabled;
	}

	/**
	 * Define the proxy to use for all GA tracking requests.
	 * <p>
	 * Call this static method early (before creating any tracking requests).
	 * 
	 * @param argProxy
	 *            The proxy to use
	 */
	public static void setProxy(Proxy argProxy)
	{
		proxy = (argProxy != null) ? argProxy : Proxy.NO_PROXY;
	}

	/**
	 * Define the proxy to use for all GA tracking requests.
	 * <p>
	 * Call this static method early (before creating any tracking requests).
	 * 
	 * @param proxyAddr
	 *            "addr:port" of the proxy to use; may also be given as URL ("http://addr:port/").
	 */
	public static void setProxy(String proxyAddr)
	{
		if (proxyAddr != null)
		{
			Scanner s = new Scanner(proxyAddr);

			// Split into "proxyAddr:proxyPort".
			proxyAddr = null;
			int proxyPort = 8080;
			try
			{
				s.findInLine("(http://|)([^:/]+)(:|)([0-9]*)(/|)");
				MatchResult m = s.match();

				if (m.groupCount() >= 2)
				{
					proxyAddr = m.group(2);
				}

				if ((m.groupCount() >= 4) && (!m.group(4).isEmpty()))
				{
					proxyPort = Integer.parseInt(m.group(4));
				}
			}
			finally
			{
				s.close();
			}

			if (proxyAddr != null)
			{
				SocketAddress sa = new InetSocketAddress(proxyAddr, proxyPort);
				setProxy(new Proxy(Type.HTTP, sa));
			}
		}
	}

	/**
	 * Wait for background tasks to complete.
	 * <p>
	 * This works in queued and asynchronous mode.
	 * 
	 * @param timeoutMillis
	 *            The maximum number of milliseconds to wait.
	 */
	public static void completeBackgroundTasks(long timeoutMillis)
	{
		boolean fifoEmpty = false;
		boolean asyncThreadsCompleted = false;

		long absTimeout = System.currentTimeMillis() + timeoutMillis;
		while (System.currentTimeMillis() < absTimeout)
		{
			synchronized (fifo)
			{
				fifoEmpty = (fifo.size() == 0);
			}

			synchronized (GoogleAnalyticsTracker.class)
			{
				asyncThreadsCompleted = (asyncThreadsRunning == 0);
			}

			if (fifoEmpty && asyncThreadsCompleted)
			{
				break;
			}

			try
			{
				Thread.sleep(100);
			}
			catch (InterruptedException e)
			{
				break;
			}
		}
	}

	/**
	 * Makes a custom tracking request based from the given data.
	 * 
	 * @param argData
	 * @throws NullPointerException
	 *             if argData is null or if the URL builder is null
	 */
	public synchronized void makeCustomRequest(RequestData argData)
	{
		if (!enabled)
		{
			logger.debug("Ignoring tracking request, enabled is false");
			return;
		}
		if (argData == null)
		{
			throw new NullPointerException("Data cannot be null");
		}
		if (builder == null)
		{
			throw new NullPointerException("Class was not initialized");
		}
		final String url = builder.buildURL(argData);

		switch (mode)
		{
			case MULTI_THREAD:
				Thread t = new Thread(asyncThreadGroup, "AnalyticsThread-" + asyncThreadGroup.activeCount())
				{
					public void run()
					{
						synchronized (GoogleAnalyticsTracker.class)
						{
							asyncThreadsRunning++;
						}
						try
						{
							dispatchRequest(url);
						}
						finally
						{
							synchronized (GoogleAnalyticsTracker.class)
							{
								asyncThreadsRunning--;
							}
						}
					}
				};
				t.setDaemon(true);
				t.start();
				break;

			case SYNCHRONOUS:
				dispatchRequest(url);
				break;

			case SINGLE_THREAD:
			default: // in case it's null, we default to the single-thread
				synchronized (fifo)
				{
					fifo.add(url);
					fifo.notify();
				}
				if (!backgroundThreadMayRun)
				{
					logger.error(
							"A tracker request has been added to the queue but the background thread isn't running.");
				}
				break;
		}
	}

	private static void dispatchRequest(String requestURL)
	{
		try
		{
			final URL url = new URL(requestURL);
			final HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);
			connection.setRequestMethod("GET");
			connection.setInstanceFollowRedirects(true);
			connection.connect();
			final int responseCode = connection.getResponseCode();
			if (responseCode != HttpURLConnection.HTTP_OK)
			{
				logger.error("JGoogleAnalyticsTracker: Error requesting url '%s', received response code %d", requestURL,
						responseCode);
			}
			else
			{
				logger.debug("JGoogleAnalyticsTracker: Tracking success for url '%s'", requestURL);
			}
		}
		catch (Exception e)
		{
			logger.error("Error making tracking request: %s", e.getMessage());
		}
	}

	private void createBuilder()
	{
		switch (version)
		{
			case V_4_7_2:
			default:
				builder = new GoogleAnalyticsURLBuilder(clientData);
				break;
		}
	}

	/**
	 * If the background thread for 'queued' mode is not running, start it now.
	 */
	private synchronized static void startBackgroundThread()
	{
		if (backgroundThread == null)
		{
			backgroundThreadMayRun = true;
			backgroundThread = new Thread(asyncThreadGroup, "AnalyticsBackgroundThread")
			{
				public void run()
				{
					logger.debug("AnalyticsBackgroundThread started");
					while (backgroundThreadMayRun)
					{
						try
						{
							String url = null;

							synchronized (fifo)
							{
								if (fifo.isEmpty())
								{
									fifo.wait();
								}

								if (!fifo.isEmpty())
								{
									// Get a reference to the oldest element in the FIFO, but leave it in the FIFO until it is processed.
									url = fifo.peek();
								}
							}

							if (url != null)
							{
								try
								{
									dispatchRequest(url);
								}
								finally
								{
									// Now that we have completed the HTTP request to GA, remove the element from the FIFO.
									synchronized (fifo)
									{
										fifo.poll();
									}
								}
							}
						}
						catch (Exception e)
						{
							logger.error("Got exception from dispatch thread: %s", e.getMessage());
						}
					}
				}
			};

			// Don't prevent the application from terminating.
			// Use completeBackgroundTasks() before exit if you want to ensure 
			// that all pending GA requests are sent. 
			backgroundThread.setDaemon(true);
			backgroundThread.start();
		}
	}

	/**
	 * Stop the long-lived background thread.
	 * <p>
	 * This method is needed for debugging purposes only. Calling it in an application is not really required: The
	 * background thread will terminate automatically when the application exits.
	 * 
	 * @param timeoutMillis
	 *            If nonzero, wait for thread completion before returning.
	 */
	public static void stopBackgroundThread(long timeoutMillis)
	{
		backgroundThreadMayRun = false;
		synchronized (fifo)
		{
			fifo.notify();
		}
		if ((backgroundThread != null) && (timeoutMillis > 0))
		{
			try
			{
				backgroundThread.join(timeoutMillis);
			}
			catch (InterruptedException e)
			{
			}
			backgroundThread = null;
		}
	}

	/**
	 * Track a page
	 * 
	 * @param pageUrl
	 *            The page URL (must not be null)
	 * @param pageTitle
	 *            The page title
	 */
	public void page(String pageUrl, String pageTitle)
	{
		RequestData data = new RequestData();
		data.setPageURL(pageUrl);
		data.setPageTitle(pageTitle);
		data.setType(RequestData.Type.PAGE);
		makeCustomRequest(data);
	}

	/**
	 * Track a сustom variable. I do not know if this can be called with null page information.
	 * 
	 * @param pageUrl
	 *            The page URL
	 * @param pageTitle
	 *            The page title
	 * @param value
	 *            the variable value
	 */
	public void customVariable(String pageUrl, String pageTitle, String value)
	{
		RequestData data = new RequestData();
		data.setPageURL(pageUrl);
		data.setPageTitle(pageTitle);
		data.setType(RequestData.Type.CUSTOM_VARIABLE);
		data.setValue(value);
		makeCustomRequest(data);
	}

	/**
	 * Get the logger
	 * 
	 * @return the logger
	 */
	public static Logger getLogger()
	{
		return logger;
	}

	/**
	 * Set the logger
	 * 
	 * @param logger
	 *            the logger to set
	 */
	public static void setLogger(Logger logger)
	{
		// If null set to the default (null) logger
		if (logger == null)
			logger = new Logger();
		GoogleAnalyticsTracker.logger = logger;
	}
}