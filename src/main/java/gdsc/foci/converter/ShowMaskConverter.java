package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class ShowMaskConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return FindFoci.maskOptions[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<FindFoci.maskOptions.length; i++)
		{
			if (FindFoci.maskOptions[i].equals(paramS))
			{
				return Integer.valueOf(i);
			}
		}
		return null;
	}
}
