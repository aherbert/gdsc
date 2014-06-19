package gdsc.foci.converter;

import org.jdesktop.beansbinding.Converter;

public class SliderConverter extends Converter<Double,Integer>
{
	public static double SCALE_FACTOR = 1000;
	
	@Override
	public Integer convertForward(Double paramS)
	{
		return Integer.valueOf((int)(paramS * SCALE_FACTOR));
	}

	@Override
	public Double convertReverse(Integer paramT)
	{
		return Double.valueOf(String.format("%.3g%n", paramT / SCALE_FACTOR));
	}
}
