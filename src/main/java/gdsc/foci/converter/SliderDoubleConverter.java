package gdsc.foci.converter;

import org.jdesktop.beansbinding.Converter;

public class SliderDoubleConverter extends Converter<Double,Integer>
{
	@Override
	public Integer convertForward(Double paramS)
	{
		return paramS.intValue();
	}

	@Override
	public Double convertReverse(Integer paramT)
	{
		return paramT.doubleValue();
	}
}
