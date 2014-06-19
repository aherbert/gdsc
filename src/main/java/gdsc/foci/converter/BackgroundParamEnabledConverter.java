package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class BackgroundParamEnabledConverter extends Converter<Integer,Boolean>
{
	@Override
	public Boolean convertForward(Integer paramS)
	{
		int backgroundMethod = paramS.intValue();
		return Boolean.valueOf(
				backgroundMethod == FindFoci.BACKGROUND_STD_DEV_ABOVE_MEAN ||
				backgroundMethod == FindFoci.BACKGROUND_ABSOLUTE);
	}

	@Override
	public Integer convertReverse(Boolean paramT)
	{
		// N/A
		return null;
	}
}
