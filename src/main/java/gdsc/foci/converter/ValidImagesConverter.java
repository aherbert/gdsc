package gdsc.foci.converter;

import java.util.List;

import org.jdesktop.beansbinding.Converter;

public class ValidImagesConverter extends Converter<List<String>,Boolean>
{
	@Override
	public Boolean convertForward(List<String> paramS)
	{
		return !paramS.isEmpty();
	}

	@Override
	public List<String> convertReverse(Boolean paramT)
	{
		// N/A
		return null;
	}
}
