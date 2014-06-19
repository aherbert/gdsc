package gdsc.foci.converter;

import gdsc.foci.GridPointManager;

import org.jdesktop.beansbinding.Converter;

public class SearchModeConverter extends Converter<Integer,Object>
{
	@Override
	public String convertForward(Integer paramT)
	{
		return GridPointManager.SEARCH_MODES[paramT.intValue()];
	}
	
	@Override
	public Integer convertReverse(Object paramS)
	{
		for (int i=0; i<GridPointManager.SEARCH_MODES.length; i++)
		{
			if (GridPointManager.SEARCH_MODES[i].equals(paramS))
			{
				return i;
			}
		}
		return null;
	}
}
