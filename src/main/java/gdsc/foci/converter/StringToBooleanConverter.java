package gdsc.foci.converter;

import org.jdesktop.beansbinding.Converter;

/**
 * Converts to true if a string is not null or empty, else false.
 */
public class StringToBooleanConverter extends Converter<String,Boolean>
{
	@Override
	public Boolean convertForward(String paramS)
	{
		return (paramS != null && !paramS.equalsIgnoreCase("")) ? true : false;
	}

	@Override
	public String convertReverse(Boolean paramT)
	{
		return (paramT) ? paramT.toString() : "";
	}
}
