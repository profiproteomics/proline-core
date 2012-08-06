package fr.proline.core.orm.utils;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;

import org.junit.Test;

public class DateUtilsTest {

    @Test
    public void testParseReleaseDate() {
	final String rawDate = "20121221";
	final Date parsedDate = DateUtils.parseReleaseDate(rawDate);

	final Calendar cal = Calendar.getInstance();
	cal.set(Calendar.YEAR, 2012);
	cal.set(Calendar.MONTH, Calendar.DECEMBER);
	cal.set(Calendar.DAY_OF_MONTH, 21);

	assertEquals("Expected date equals parsed date", DateUtils.flushTime(cal.getTime()), parsedDate);
    }

}
