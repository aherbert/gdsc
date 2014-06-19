package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class SearchMethodConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return FindFoci.searchMethods[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<FindFoci.searchMethods.length; i++)
		{
			if (FindFoci.searchMethods[i].equals(paramS))
			{
				return Integer.valueOf(i);
			}
		}
		return null;
	}
}
