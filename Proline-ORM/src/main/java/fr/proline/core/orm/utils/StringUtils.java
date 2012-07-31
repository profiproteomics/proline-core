package fr.proline.core.orm.utils;

/**
 * Utility class to handle character strings.
 * 
 * @author Laurent Martin
 * 
 *         Created 31 Jul. 2012
 * 
 * 
 * 
 */
public final class StringUtils {

    /* Private constructor (Utility class) */
    private StringUtils() {
    }

    /* Public class methods */
    /**
     * Check if given String is empty (<code>null</code>, "", or contains only white-space characters).
     * 
     * @param s
     *            Source String to check
     * @return <code>true</code> if given String is <code>null</code> or empty
     */
    public static boolean isEmpty(final String s) {
	boolean empty = true; // Optimistic initialization

	if (s != null) {

	    final int stringLength = s.length();
	    for (int index = 0; empty && (index < stringLength); ++index) {
		empty = Character.isWhitespace(s.charAt(index));
	    }

	}

	return empty;
    }

}
