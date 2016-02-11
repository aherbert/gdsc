package gdsc.foci;

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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.LinkedList;

/**
 * Manages I/O of the TimeValuePoint class
 */
public class TimeValuePointManager
{
	public enum FileType
	{
		FIND_FOCI, CSV_IdTXYZV, CSV_IdTXY, TAB_IdTXYZV, TAB_IdTXY, QuickPALM, STORMJ, TAB_XYZ, CSV_XYZ, Unknown
	}

	private static final String newline = System.getProperty("line.separator");

	private String filename;
	private FileType type = null;

	// Used to parse the input file
	private int[] fields;
	private int maxField;
	private String delimiter;
	private String line;
	private int lineCount;

	public TimeValuePointManager(String filename)
	{
		this.filename = filename;
	}

	/**
	 * Attempts to detect the file type by reading the initial lines
	 * 
	 * @return The type of file containing the points
	 * @throws IOException
	 */
	public FileType getFileType() throws IOException
	{
		if (type != null)
			return type;

		BufferedReader input = null;
		try
		{
			// Load results from file
			input = new BufferedReader(new FileReader(filename));

			String line;
			int lineCount = 1;
			int nonHeaderLines = 0;
			while ((line = input.readLine()) != null)
			{
				type = guessFiletype(line, lineCount++);

				if (!line.startsWith("#"))
					nonHeaderLines++;

				// Stop after several non-header lines
				if (type != FileType.Unknown || nonHeaderLines > 1)
					break;
			}
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (IOException e)
			{
			}
		}

		return type;
	}

	private FileType guessFiletype(String line, int lineCount)
	{
		int delimiterCount = countDelimeters(line, "\t");

		// FindFoci file
		if (line.startsWith("Peak #\tMask Value") && delimiterCount >= 5)
			return FileType.FIND_FOCI;

		// Look for a unique text for the QuickPALM file on the first line
		if (lineCount == 1 && line.contains("Up-Height") && delimiterCount >= 14)
			return FileType.QuickPALM;

		// STORMJ file can have extra header lines so ignore line count
		if (line.startsWith("#") && line.contains("origX") && delimiterCount >= 12)
			return FileType.STORMJ;

		// Tab separated fields: ID,T,X,Y,Z,Value
		if (delimiterCount >= 5)
			return FileType.TAB_IdTXYZV;
		// File is allowed to have Z and Value missing
		if (delimiterCount >= 3)
			return FileType.TAB_IdTXY;
		if (delimiterCount == 2)
			return FileType.TAB_XYZ;

		// Comma separated fields: ID,T,X,Y,Z,Value
		delimiterCount = countDelimeters(line, ",");
		if (delimiterCount >= 5)
			return FileType.CSV_IdTXYZV;
		// File is allowed to have Z and Value missing
		if (delimiterCount >= 3)
			return FileType.CSV_IdTXY;
		if (delimiterCount == 2)
			return FileType.CSV_XYZ;

		return FileType.Unknown;
	}

	private int countDelimeters(String text, String delimiter)
	{
		return text.split(delimiter).length - 1;
	}

	/**
	 * Save the points to file
	 * 
	 * @param points
	 * @throws IOException
	 */
	public void savePoints(TimeValuedPoint[] points) throws IOException
	{
		if (points == null)
			return;

		OutputStreamWriter out = null;
		try
		{
			File file = new File(filename);
			if (!file.exists())
			{
				if (file.getParent() != null)
					new File(file.getParent()).mkdirs();
			}

			// Save results to file
			FileOutputStream fos = new FileOutputStream(filename);
			out = new OutputStreamWriter(fos);

			StringBuilder sb = new StringBuilder();

			out.write("ID,T,X,Y,Z,Value" + newline);

			// Output all results in ascending rank order
			int id = 0;
			for (TimeValuedPoint point : points)
			{
				sb.append(++id).append(',');
				sb.append(point.getTime()).append(',');
				sb.append(point.getX()).append(',');
				sb.append(point.getY()).append(',');
				sb.append(point.getZ()).append(',');
				sb.append(point.getValue()).append(newline);
				out.write(sb.toString());
				sb.setLength(0);
			}
		}
		finally
		{
			try
			{
				if (out != null)
					out.close();
			}
			catch (IOException e)
			{
			}
		}
	}

	/**
	 * Loads the points from the file
	 * 
	 * @return
	 * @throws IOException
	 */
	public TimeValuedPoint[] loadPoints() throws IOException
	{
		getFileType();

		if (type == FileType.Unknown || !setupParser())
			return new TimeValuedPoint[0];

		LinkedList<TimeValuedPoint> points = new LinkedList<TimeValuedPoint>();
		BufferedReader input = null;
		try
		{
			// Load results from file
			input = new BufferedReader(new FileReader(filename));

			// TODO - Read in binary files from STORMJ

			skipHeader(input);

			int errors = 0;
			while (line != null)
			{
				String[] tokens = line.split(delimiter);
				if (tokens.length > maxField)
				{
					try
					{
						//int id = Integer.parseInt(tokens[fields[0]]); // Not currently needed
						float t = 1;
						if (fields[1] >= 0)
							t = Float.parseFloat(tokens[fields[1]]); // QuickPALM uses a float for the time
						float x = Float.parseFloat(tokens[fields[2]]);
						float y = Float.parseFloat(tokens[fields[3]]);
						float z = 0;
						if (fields[4] >= 0)
							z = Float.parseFloat(tokens[fields[4]]);
						if (type == FileType.QuickPALM)
						{
							// z is in nm and so must be converted to approximate pixels
							float xNm = Float.parseFloat(tokens[4]);
							z *= x / xNm;
						}
						float value = 0;
						if (fields[5] >= 0)
							value = Float.parseFloat(tokens[fields[5]]);
						points.add(new TimeValuedPoint(x, y, z, (int) t, value));
					}
					catch (NumberFormatException e)
					{
						System.err.println("Invalid numbers on line: " + lineCount);
						if (++errors > 10)
							break;
					}
				}
				readLine(input);
			}

			return points.toArray(new TimeValuedPoint[0]);
		}
		finally
		{
			try
			{
				if (input != null)
					input.close();
			}
			catch (IOException e)
			{
			}
		}
	}

	private boolean setupParser()
	{
		maxField = 0;
		lineCount = 0;
		line = null;

		switch (type)
		{
			case FIND_FOCI:
				delimiter = "\t";
				// Store the object field in T
				fields = new int[] { 0, 20, 2, 3, 4, 5 };
				break;
				
			case QuickPALM:
				delimiter = "\t";
				fields = new int[] { 0, 14, 2, 3, 6, 1 };
				break;

			case STORMJ:
				delimiter = "\t";
				// No ID or z-dimension
				fields = new int[] { -1, 0, 10, 11, -1, 6 };
				break;

			case TAB_IdTXYZV:
				delimiter = "\t";
				// ID,T,X,Y,Z,Value
				fields = new int[] { 0, 1, 2, 3, 4, 5 };
				break;

			case TAB_IdTXY:
				delimiter = "\t";
				// ID,T,X,Y
				fields = new int[] { 0, 1, 2, 3, -1, -1 };
				break;

			case TAB_XYZ:
				delimiter = "\t";
				// X,Y,Z
				fields = new int[] { -1, -1, 0, 1, 2, -1 };
				break;

			case CSV_IdTXYZV:
				delimiter = ",";
				// ID,T,X,Y,Z,Value
				fields = new int[] { 0, 1, 2, 3, 4, 5 };
				break;

			case CSV_IdTXY:
				delimiter = ",";
				// ID,T,X,Y
				fields = new int[] { 0, 1, 2, 3, -1, -1 };
				break;

			case CSV_XYZ:
				delimiter = ",";
				// X,Y,Z
				fields = new int[] { -1, -1, 0, 1, 2, -1 };
				break;

			default:
				return false;
		}

		for (int i : fields)
			if (maxField < i)
				maxField = i;

		return true;
	}

	/**
	 * Reads lines from the input until the first record is reached. Leaves the line variable at the first record.
	 * 
	 * @param input
	 * @throws IOException
	 */
	private void skipHeader(BufferedReader input) throws IOException
	{
		switch (type)
		{
			case FIND_FOCI:
				do
				{
					readLine(input);
				} while (line != null && (line.startsWith("#") || line.startsWith("Peak #")));

				break;
				
			case QuickPALM:
				readLine(input); // First line is the header so read this

				// Allow fall-through to read the next line

			// Read until no comment character
			default:
				do
				{
					readLine(input);
				} while (line != null && line.startsWith("#"));

				break;
		}
	}

	private void readLine(BufferedReader input) throws IOException
	{
		line = input.readLine();
		//if (line != null) // No need as the lineCount is only referenced when line is not null
		lineCount++;
	}

	/**
	 * Save the points to the given file
	 * 
	 * @param points
	 * @param filename
	 * @throws IOException
	 */
	public static void savePoints(TimeValuedPoint[] points, String filename) throws IOException
	{
		TimeValuePointManager manager = new TimeValuePointManager(filename);
		manager.savePoints(points);
	}

	/**
	 * Loads the points from the file
	 * 
	 * @param filename
	 * @return The points
	 * @throws IOException
	 */
	public static TimeValuedPoint[] loadPoints(String filename) throws IOException
	{
		TimeValuePointManager manager = new TimeValuePointManager(filename);
		return manager.loadPoints();
	}
}