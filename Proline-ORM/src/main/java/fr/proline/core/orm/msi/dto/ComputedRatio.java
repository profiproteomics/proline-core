package fr.proline.core.orm.msi.dto;

public class ComputedRatio {
	
	Float numerator= null;
	Float denominator= null;
	Integer state = 0; // -1 means under-abundant, 0 means invariant and +1 means over-abundant
	Double tTestPValue = null;
	Double zTestPValue= null;
	
	
	public ComputedRatio() {
		super();
		// TODO Auto-generated constructor stub
	}
	
	public Float getNumerator() {
		return numerator;
	}
	public void setNumerator(Float numerator) {
		this.numerator = numerator;
	}
	public Float getDenominator() {
		return denominator;
	}
	public void setDenominator(Float denominator) {
		this.denominator = denominator;
	}
	public Integer getState() {
		return state;
	}
	public void setState(Integer state) {
		this.state = state;
	}
	public Double gettTestPValue() {
		return tTestPValue;
	}
	public void settTestPValue(Double tTestPValue) {
		this.tTestPValue = tTestPValue;
	}
	public Double getzTestPValue() {
		return zTestPValue;
	}
	public void setzTestPValue(Double zTestPValue) {
		this.zTestPValue = zTestPValue;
	}

}
