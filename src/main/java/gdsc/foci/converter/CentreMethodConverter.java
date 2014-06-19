package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class CentreMethodConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return FindFoci.getCentreMethods()[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<FindFoci.getCentreMethods().length; i++)
		{
			if (FindFoci.getCentreMethods()[i].equals(paramS))
			{
				return Integer.valueOf(i);
			}
		}
		return null;
	}
}
