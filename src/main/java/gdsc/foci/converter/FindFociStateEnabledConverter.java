package gdsc.foci.converter;

import gdsc.foci.model.FindFociState;

import org.jdesktop.beansbinding.Converter;

public class FindFociStateEnabledConverter extends Converter<FindFociState,Boolean>
{
	@Override
	public Boolean convertForward(FindFociState paramT)
	{
		return paramT != FindFociState.COMPLETE;
	}
	
	@Override
	public FindFociState convertReverse(Boolean paramS)
	{
		return null;
	}
}
