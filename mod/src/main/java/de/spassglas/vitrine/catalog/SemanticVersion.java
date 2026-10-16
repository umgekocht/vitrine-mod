package de.spassglas.vitrine.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Ein nach <a href="https://semver.org/spec/v2.0.0.html">SemVer 2.0.0</a>
 * geparster Versionsstring (ROADMAP K5, "Versionsvergleich (SemVer)").
 *
 * <p>Bewusst <em>strukturell</em> geparst statt per aus dem Netz kopierter
 * Regex (Charter/Auftrag: "Kein Regex-Copy aus dem Internet"): {@code
 * MAJOR.MINOR.PATCH} als drei nichtnegative Ganzzahlen ohne fuehrende Null,
 * optional {@code -prerelease} (punktgetrennte Bezeichner) und optional
 * {@code +build} (punktgetrennte Bezeichner). Die dieselben Regeln
 * ausdrueckende Referenz-Regex steht in {@code
 * registry/schema/vitrine.schema.json} und {@code
 * registry-package.schema.json} -- dieser Parser haelt sich Feld fuer Feld
 * daran.</p>
 *
 * <ul>
 *   <li>{@link #tryParse(String)} -- wirft nie; bei Murks {@link
 *       Optional#empty()} (auf {@code DEBUG} geloggt). Der Aufrufer behandelt
 *       eine nicht parsebare Version als "kein Update" (ROADMAP K5), nie als
 *       Absturz.</li>
 *   <li>{@link #compareTo(SemanticVersion)} -- Praezedenz nach SemVer 2.0.0
 *       Punkt 11: numerische Felder numerisch; eine Vorabversion ({@code
 *       -alpha}) ist kleiner als die zugehoerige Release-Version;
 *       Build-Metadaten ({@code +...}) haben <em>keinen</em> Einfluss.</li>
 *   <li>{@link #isNewerThan(SemanticVersion)} -- {@code compareTo(other) &gt; 0}.</li>
 * </ul>
 *
 * <p>{@link #equals(Object)}/{@link #hashCode()} folgen der Praezedenz und
 * ignorieren daher ebenfalls die Build-Metadaten -- zwei Versionen, die sich
 * nur im {@code +build}-Anhang unterscheiden, gelten als gleich.</p>
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/SemVer");

	private final long major;
	private final long minor;
	private final long patch;
	/** Punktgetrennte Vorabversions-Bezeichner; leer = Release-Version. */
	private final List<String> prerelease;
	/** Punktgetrennte Build-Metadaten-Bezeichner; ohne Einfluss auf die Praezedenz. */
	private final List<String> build;

	private SemanticVersion(long major, long minor, long patch, List<String> prerelease, List<String> build) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.prerelease = List.copyOf(prerelease);
		this.build = List.copyOf(build);
	}

	/**
	 * Parst {@code raw} nach SemVer 2.0.0. Wirft nie -- ein {@code null},
	 * leerer, oder in irgendeinem Feld ungueltiger String ergibt {@link
	 * Optional#empty()} (auf {@code DEBUG} geloggt).
	 */
	public static Optional<SemanticVersion> tryParse(String raw) {
		if (raw == null || raw.isBlank()) {
			LOGGER.debug("Versionsstring ist null/leer -- nicht parsebar.");
			return Optional.empty();
		}
		String core = raw.trim();

		// --- +build abtrennen (alles nach dem ERSTEN '+') --------------------
		List<String> build = List.of();
		int plus = core.indexOf('+');
		if (plus >= 0) {
			String buildPart = core.substring(plus + 1);
			core = core.substring(0, plus);
			List<String> parsedBuild = parseDotSeparatedIdentifiers(buildPart, false);
			if (parsedBuild == null) {
				LOGGER.debug("Version '{}' hat ungueltige Build-Metadaten.", raw);
				return Optional.empty();
			}
			build = parsedBuild;
		}

		// --- -prerelease abtrennen (alles nach dem ERSTEN '-') --------------
		List<String> prerelease = List.of();
		int dash = core.indexOf('-');
		if (dash >= 0) {
			String prePart = core.substring(dash + 1);
			core = core.substring(0, dash);
			List<String> parsedPre = parseDotSeparatedIdentifiers(prePart, true);
			if (parsedPre == null) {
				LOGGER.debug("Version '{}' hat einen ungueltigen Vorabversions-Teil.", raw);
				return Optional.empty();
			}
			prerelease = parsedPre;
		}

		// --- MAJOR.MINOR.PATCH -------------------------------------------
		String[] numbers = core.split("\\.", -1);
		if (numbers.length != 3) {
			LOGGER.debug("Version '{}' hat nicht genau drei Zahlenfelder (MAJOR.MINOR.PATCH).", raw);
			return Optional.empty();
		}
		long[] parsed = new long[3];
		for (int i = 0; i < 3; i++) {
			Long value = parseNumericField(numbers[i]);
			if (value == null) {
				LOGGER.debug("Version '{}': Feld '{}' ist keine gueltige nichtnegative Ganzzahl ohne fuehrende Null.",
						raw, numbers[i]);
				return Optional.empty();
			}
			parsed[i] = value;
		}
		return Optional.of(new SemanticVersion(parsed[0], parsed[1], parsed[2], prerelease, build));
	}

	/**
	 * Ein nichtnegatives Zahlenfeld ({@code MAJOR}/{@code MINOR}/{@code
	 * PATCH}): nur Ziffern, keine fuehrende Null (ausser dem alleinstehenden
	 * {@code "0"}). {@code null} bei Verstoss.
	 */
	private static Long parseNumericField(String field) {
		if (!isAllDigits(field)) {
			return null;
		}
		if (field.length() > 1 && field.charAt(0) == '0') {
			return null;
		}
		try {
			return Long.parseLong(field);
		} catch (NumberFormatException e) {
			// Astronomisch grosse Feldwerte kommen bei echten Paketen nicht vor.
			LOGGER.debug("Zahlenfeld '{}' passt nicht in einen long -- als nicht parsebar behandelt.", field);
			return null;
		}
	}

	/**
	 * Zerlegt {@code part} an den Punkten und prueft jeden Bezeichner. Bei
	 * einer Vorabversion ({@code prerelease == true}) duerfen rein numerische
	 * Bezeichner keine fuehrende Null haben (SemVer 2.0.0 Punkt 9);
	 * Build-Metadaten (Punkt 10) erlauben das. {@code null} bei Verstoss,
	 * sonst die Liste der Bezeichner (nie leer).
	 */
	private static List<String> parseDotSeparatedIdentifiers(String part, boolean prerelease) {
		if (part.isEmpty()) {
			return null;
		}
		String[] identifiers = part.split("\\.", -1);
		List<String> result = new ArrayList<>(identifiers.length);
		for (String identifier : identifiers) {
			if (identifier.isEmpty() || !isValidIdentifierCharset(identifier)) {
				return null;
			}
			if (prerelease && isAllDigits(identifier)
					&& identifier.length() > 1 && identifier.charAt(0) == '0') {
				return null;
			}
			result.add(identifier);
		}
		return result;
	}

	private static boolean isValidIdentifierCharset(String s) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '-';
			if (!ok) {
				return false;
			}
		}
		return true;
	}

	private static boolean isAllDigits(String s) {
		if (s.isEmpty()) {
			return false;
		}
		for (int i = 0; i < s.length(); i++) {
			if (s.charAt(i) < '0' || s.charAt(i) > '9') {
				return false;
			}
		}
		return true;
	}

	/** {@code true}, wenn diese Version nach SemVer-Praezedenz echt nach {@code other} kommt. */
	public boolean isNewerThan(SemanticVersion other) {
		return compareTo(other) > 0;
	}

	/** {@code true}, wenn dies eine Vorabversion ist ({@code 1.0.0-alpha}). */
	public boolean isPrerelease() {
		return !prerelease.isEmpty();
	}

	public long major() {
		return major;
	}

	public long minor() {
		return minor;
	}

	public long patch() {
		return patch;
	}

	@Override
	public int compareTo(SemanticVersion other) {
		Objects.requireNonNull(other, "other");
		int byNumber = Long.compare(major, other.major);
		if (byNumber != 0) {
			return byNumber;
		}
		byNumber = Long.compare(minor, other.minor);
		if (byNumber != 0) {
			return byNumber;
		}
		byNumber = Long.compare(patch, other.patch);
		if (byNumber != 0) {
			return byNumber;
		}
		// SemVer 2.0.0 Punkt 11: eine Vorabversion ist KLEINER als die
		// zugehoerige Release-Version.
		if (prerelease.isEmpty() && other.prerelease.isEmpty()) {
			return 0;
		}
		if (prerelease.isEmpty()) {
			return 1;
		}
		if (other.prerelease.isEmpty()) {
			return -1;
		}
		return comparePrerelease(prerelease, other.prerelease);
	}

	private static int comparePrerelease(List<String> a, List<String> b) {
		int shared = Math.min(a.size(), b.size());
		for (int i = 0; i < shared; i++) {
			String left = a.get(i);
			String right = b.get(i);
			boolean leftNumeric = isAllDigits(left);
			boolean rightNumeric = isAllDigits(right);
			int cmp;
			if (leftNumeric && rightNumeric) {
				cmp = new BigInteger(left).compareTo(new BigInteger(right));
			} else if (leftNumeric) {
				// Numerische Bezeichner haben niedrigere Praezedenz als alphanumerische.
				cmp = -1;
			} else if (rightNumeric) {
				cmp = 1;
			} else {
				cmp = left.compareTo(right); // ASCII-Sortierung
			}
			if (cmp != 0) {
				return cmp < 0 ? -1 : 1;
			}
		}
		// Alle gemeinsamen Felder gleich: der laengere Satz gewinnt.
		return Integer.compare(a.size(), b.size());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SemanticVersion other)) {
			return false;
		}
		// Konsistent mit compareTo: Build-Metadaten bleiben aussen vor.
		return major == other.major && minor == other.minor && patch == other.patch
				&& prerelease.equals(other.prerelease);
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch, prerelease);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(major).append('.').append(minor).append('.').append(patch);
		if (!prerelease.isEmpty()) {
			sb.append('-').append(String.join(".", prerelease));
		}
		if (!build.isEmpty()) {
			sb.append('+').append(String.join(".", build));
		}
		return sb.toString();
	}
}
