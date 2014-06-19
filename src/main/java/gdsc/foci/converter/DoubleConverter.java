package gdsc.foci.converter;

import java.text.DecimalFormat;

import org.jdesktop.beansbinding.Converter;

public class DoubleConverter extends Converter<Double,String>
{
	private static DecimalFormat dc = new DecimalFormat("#.##");
	
	@Override
	public String convertForward(Double paramS)
	{
		return dc.format(paramS);
	}

	@Override
	public Double convertReverse(String paramT)
	{
		return Double.valueOf(paramT);
	}
}
