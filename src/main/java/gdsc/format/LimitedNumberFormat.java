package gdsc.format;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.ParsePosition;

/**
 * Provide an extension to the {@link DecimalFormat} with a minimum and maximum
 * limit. If the parsed source is outside the bounds it will be set the corresponding limit.
 */
public class LimitedNumberFormat extends DecimalFormat
{
	/**
	 * Auto-generated 
	 */
	private static final long serialVersionUID = -2564688480913124241L;
	
	private double min = Double.MIN_VALUE;
	private double max = Double.MAX_VALUE;
	
	public LimitedNumberFormat(double min, double max)
	{
		super();
		this.min = min;
		this.max = max;
	}

	public LimitedNumberFormat(double min)
	{
		super();
		this.min = min;
	}

	@Override
	public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos)
	{
		StringBuffer sb = super.format(number, toAppendTo, pos);
		return sb;
	}

	@Override
	public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos)
	{
		StringBuffer sb = super.format(number, toAppendTo, pos);
		return sb;
	}

	@Override
	public Number parse(String source, ParsePosition parsePosition)
	{
//		int currentIndex = parsePosition.getIndex();
		Number n = super.parse(source, parsePosition);
		if (n != null)
		{
//			if (n.doubleValue() < min || n.doubleValue() > max)
//			{
//				parsePosition.setErrorIndex(currentIndex);
//				parsePosition.setIndex(currentIndex);
//				n = null;
//			}
			if (n.doubleValue() < min)
			{
				n = Double.valueOf(min);
			}
			else if (n.doubleValue() > max)
			{
				n = Double.valueOf(max);
			}
		}
		return n;
	}

}
