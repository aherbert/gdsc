package gdsc.analytics;

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
	public static final void populateConfigData(ClientData data)
	{
		data.setEncoding(System.getProperty("file.encoding"));

		String region = System.getProperty("user.region");
		if (region == null)
		{
			region = System.getProperty("user.country");
		}
		data.setUserLanguage(System.getProperty("user.language") + "-" + region);

		String hostName = "localhost";
		try
		{
			hostName = InetAddress.getLocalHost().getHostName();
		}
		catch (UnknownHostException e)
		{
			//ignore this
		}
		data.setHostName(hostName);

		Dimension d = getScreenSize();
		data.setScreenResolution(d.getWidth() + "x" + d.getHeight());
	}

	/**
	 * Get the screen size
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
		populateConfigData(data);
		return data;
	}

}
