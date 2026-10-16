package de.spassglas.vitrine.ui.catalog;

import de.spassglas.vitrine.catalog.IndexPackage;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Naht (Seam) fuer "gibt es einen gecachten Katalog-Stand, und von wann"
 * (ROADMAP K4). Produktion verdrahtet {@code CachedIndexStore} (liest den
 * ETag-Cache-Rumpf ohne Netz); Tests setzen einen Fake ein.
 *
 * <p>{@link #latest()} liefert {@link Optional#empty()}, wenn es keinen oder
 * einen unbrauchbaren Cache gibt -- dann gibt es <em>kein</em> ehrliches
 * Standdatum (Charter-Regel 2).</p>
 */
@FunctionalInterface
public interface CatalogCache {

	/** Der zuletzt gecachte Katalog samt Stand, oder leer, wenn keiner vorliegt. */
	Optional<Cached> latest();

	/**
	 * Ein gecachter Katalog-Stand: die Paketliste der ersten Index-Seite und
	 * das Datum, an dem der Rumpf zuletzt geschrieben wurde.
	 */
	record Cached(List<IndexPackage> packages, LocalDate asOf) {

		public Cached {
			packages = List.copyOf(packages);
			Objects.requireNonNull(asOf, "asOf");
		}
	}
}
