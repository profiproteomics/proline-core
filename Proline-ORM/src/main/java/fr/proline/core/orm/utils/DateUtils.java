package fr.proline.core.orm.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DateUtils {

    private static final Logger LOG = LoggerFactory.getLogger(DateUtils.class);

    /* Private constructor (Utility class) */
    private DateUtils() {
    }

    /**
     * Parses a raw (String) release date to a <code>Date</code> object.
     * 
     * @param rawDate
     *            Expected format of Pdi <code>SequenceDbRelease.date</code> : yyyymmdd
     * @return <code>Date</code> object or <code>null</code> if <code>rawString</code> is empty or invalid
     *         format.
     */
    public static Date parseReleaseDate(final String rawDate) {
	Date result = null;

	if (!StringUtils.isEmpty(rawDate)) {
	    final DateFormat df = new SimpleDateFormat("yyyyMMdd");

	    try {
		final Date d = df.parse(rawDate);
		result = flushTime(d);
	    } catch (ParseException pEx) {
		LOG.warn("Unable to parse [" + rawDate + "] as a date value", pEx);
	    }

	}

	return result;

    }

    /**
     * Resets all time parts of a <code>Date</code> object, keeping date parts.
     * 
     * @param src
     *            Source date, must not be <code>null</code>
     * @return Date containing only date parts (year, month, day...)
     */
    public static Date flushTime(final Date src) {

	if (src == null) {
	    throw new IllegalArgumentException("Src is null");
	}

	final Calendar cal = Calendar.getInstance();
	cal.setTime(src);

	/* Reset time parts of the calendar */
	cal.set(Calendar.HOUR_OF_DAY, 0);
	cal.clear(Calendar.MINUTE);
	cal.clear(Calendar.SECOND);
	cal.clear(Calendar.MILLISECOND);

	return cal.getTime();
    }

}
