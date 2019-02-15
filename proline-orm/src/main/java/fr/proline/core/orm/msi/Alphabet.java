package fr.proline.core.orm.msi;

import fr.profi.util.StringUtils;

public enum Alphabet {

	aa, AA, DNA;

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
