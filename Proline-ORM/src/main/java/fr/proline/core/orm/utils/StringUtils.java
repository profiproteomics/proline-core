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

    /**
     * Convert a char residue to a string value.
     * 
     * @param residue
     *            Residue as char primitive.
     * @return Residue as a string or <code>null</code> if <code>residue == '\0'</code>.
     */
    public static String convertCharResidueToString(final char residue) {
	String result = null;

	if (residue != '\0') {
	    result = Character.toString(residue);
	}

	return result;
    }

    /**
     * Convert a string residue to a char primitive.
     * 
     * @param strResidue
     *            Residue as string object.
     * @return Residue as first char of <code>strResidue</code> or <code>'\0'</code> if
     *         <code>strResidue</code> is <code>null</code> or emty.
     */
    public static char convertStringResidueToChar(final String strResidue) {
	char result = '\0';

	if (!isEmpty(strResidue)) {
	    result = strResidue.charAt(0);
	}

	return result;
    }

}
