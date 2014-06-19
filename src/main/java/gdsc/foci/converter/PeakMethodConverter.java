package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class PeakMethodConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return FindFoci.peakMethods[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<FindFoci.peakMethods.length; i++)
		{
			if (FindFoci.peakMethods[i].equals(paramS))
			{
				return Integer.valueOf(i);
			}
		}
		return null;
	}
}
