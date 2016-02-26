package gdsc.analytics;

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

import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.net.InetAddress;
import java.net.UnknownHostException;

import ij.IJ;

/**
 * Populates the ClientData with information from the system
 */
public class ClientDataManager
{
	/**
	 * Populates the ClientData with information from the system
	 * 
	 * @param data
	 *            The data
	 */
	public static final void populate(ClientData data)
	{
		data.setEncoding(System.getProperty("file.encoding"));

		String region = System.getProperty("user.region");
		if (region == null)
		{
			region = System.getProperty("user.country");
		}
		data.setUserLanguage((System.getProperty("user.language") + "-" + region).toLowerCase());

		String hostName = "localhost";
		try
		{
			final InetAddress iAddress = InetAddress.getLocalHost();
			// This performs a lookup of the name service as well
			// e.g. host.domain.com
			hostName = iAddress.getCanonicalHostName();
			
			// This only retrieves the bare hostname
			// e.g. host
			// hostName = iAddress.getHostName();
			
			// This retrieves the IP address as a string
			// e.g. 192.168.0.1 
			//hostName = iAddress.getHostAddress();
		}
		catch (UnknownHostException e)
		{
			//ignore this
		}
		data.setHostName(hostName);

		final Dimension d = getScreenSize();
		data.setScreenResolution(d.width + "x" + d.height);
		
		// Add HTTP headers which will be fed into the URLConnection.setRequestProperty(String, String) method.
		// Some of this can be tracked by Google Analytics.
		// The browser and operating system are taken from the User-Agent property.
		
		//data.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36"); // To simulate Chrome
		
		// The Java URLConnection User-Agent property will default to Java/1.6.0.19 where the 
		// last part is the JRE version.
		// Build e.g. Java/1.6.0.19 (Windows 7 6.1)
		
		
		StringBuilder sb = new StringBuilder();
		sb.append("Java/").append(System.getProperty("java.version"));
		sb.append(" (").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append(")");
		data.addHeader("User-Agent", sb.toString());
	}

	/**
	 * Get the screen size
	 * <p>
	 * Taken from ImageJ.getScreenSize();
	 * 
	 * @return The dimension of the primary screen
	 */
	public static Dimension getScreenSize()
	{
		if (IJ.isWindows()) // GraphicsEnvironment.getConfigurations is *very* slow on Windows
			return Toolkit.getDefaultToolkit().getScreenSize();
		if (GraphicsEnvironment.isHeadless())
			return new Dimension(0, 0);
		// Can't use Toolkit.getScreenSize() on Linux because it returns 
		// size of all displays rather than just the primary display.
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice[] gd = ge.getScreenDevices();
		GraphicsConfiguration[] gc = gd[0].getConfigurations();
		Rectangle bounds = gc[0].getBounds();
		if ((bounds.x == 0 && bounds.y == 0) || (IJ.isLinux() && gc.length > 1))
			return new Dimension(bounds.width, bounds.height);
		else
			return Toolkit.getDefaultToolkit().getScreenSize();
	}

	/**
	 * Create a ClientData object and populate it with information from the system
	 * 
	 * @param trackingCode
	 *            The Google Analytiucs tracking code
	 * @return the client data
	 */
	public static ClientData newClientData(String trackingCode)
	{
		ClientData data = new ClientData(trackingCode);
		populate(data);
		return data;
	}

	/**
	 * Create a ClientData object and populate it with information from the system
	 * 
	 * @param trackingCode
	 *            The Google Analytiucs tracking code
	 * @param sessionData
	 *            The session data
	 * @return the client data
	 */
	public static ClientData newClientData(String trackingCode, SessionData sessionData)
	{
		ClientData data = new ClientData(trackingCode, sessionData);
		populate(data);
		return data;
	}
}
