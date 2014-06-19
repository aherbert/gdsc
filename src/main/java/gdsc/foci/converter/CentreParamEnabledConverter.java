package gdsc.foci.converter;

import gdsc.foci.FindFoci;

import org.jdesktop.beansbinding.Converter;

public class CentreParamEnabledConverter extends Converter<Integer, Boolean>
{
	@Override
	public Boolean convertForward(Integer paramS)
	{
		int backgroundMethod = paramS.intValue();
		return Boolean.valueOf(backgroundMethod == FindFoci.CENTRE_GAUSSIAN_SEARCH ||
				backgroundMethod == FindFoci.CENTRE_GAUSSIAN_ORIGINAL ||
				backgroundMethod == FindFoci.CENTRE_OF_MASS_SEARCH ||
				backgroundMethod == FindFoci.CENTRE_OF_MASS_ORIGINAL);
	}

	@Override
	public Integer convertReverse(Boolean paramT)
	{
		// N/A
		return null;
	}
}
