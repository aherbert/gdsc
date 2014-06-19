package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class SortMethodConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return FindFoci.sortIndexMethods[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<FindFoci.sortIndexMethods.length; i++)
		{
			if (FindFoci.sortIndexMethods[i].equals(paramS))
			{
				return Integer.valueOf(i);
			}
		}
		return null;
	}
}
