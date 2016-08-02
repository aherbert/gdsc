package gdsc.foci.converter;

import java.awt.Color;

import org.jdesktop.beansbinding.Converter;

public class SortMethodColorConverter extends Converter<Boolean,Color>
{
	@Override
	public Color convertForward(Boolean paramT)
	{
		return (paramT.booleanValue()) ? Color.red : Color.black;
	}
	
	@Override
	public Boolean convertReverse(Color paramS)
	{
		return Color.red.equals(paramS);
	}
}
