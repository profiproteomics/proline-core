package fr.proline.core.orm.pdi;

import fr.proline.util.StringUtils;

public enum Alphabet {

    AA, DNA;

    public static Alphabet findAlphabet(final String name) {

	if (StringUtils.isEmpty(name)) {
	    throw new IllegalArgumentException("Invalid name");
	}

	final String normalizedName = name.trim();

	Alphabet result = null;

	for (final Alphabet alphabet : Alphabet.values()) {

	    if (alphabet.name().equalsIgnoreCase(normalizedName)) {
		result = alphabet;

		break;
	    }

	}

	return result;
    }

}
