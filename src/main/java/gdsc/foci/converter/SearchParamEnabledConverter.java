package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class SearchParamEnabledConverter extends Converter<Integer,Boolean>
{
	@Override
	public Boolean convertForward(Integer paramS)
	{
		int searchMethod = paramS.intValue();
		return Boolean.valueOf(
				searchMethod == FindFoci.SEARCH_FRACTION_OF_PEAK_MINUS_BACKGROUND);
	}

	@Override
	public Integer convertReverse(Boolean paramT)
	{
		// N/A
		return null;
	}
}
