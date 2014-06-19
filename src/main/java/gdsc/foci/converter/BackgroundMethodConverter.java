package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class BackgroundMethodConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return FindFoci.backgroundMethods[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<FindFoci.backgroundMethods.length; i++)
		{
			if (FindFoci.backgroundMethods[i].equals(paramS))
			{
				return Integer.valueOf(i);
			}
		}
		return null;
	}
}
