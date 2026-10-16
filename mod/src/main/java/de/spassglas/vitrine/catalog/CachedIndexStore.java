package de.spassglas.vitrine.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Liest die zuletzt erfolgreich geladene erste Index-Seite <em>ohne Netz</em>
 * aus dem {@link EtagCache} (ROADMAP K4, Offline-Modus). Anders als
 * {@link HttpCatalogClient} wird hier nie ein Request geschickt: es geht nur um
 * den bereits gespeicherten Rumpf und dessen Alter.
 *
 * <ul>
 *   <li>{@link #firstPage()} -- der gecachte Rumpf, gegen dieselbe eingebettete
 *       Schema-Kopie geprueft wie ein frischer Abruf; leer, wenn es keinen oder
 *       einen unbrauchbaren Cache gibt (nie eine Exception).</li>
 *   <li>{@link #firstPageAsOf()} -- die Datei-Aenderungszeit des Cache-Rumpfes
 *       als lokales Datum ("Stand von &lt;Datum&gt;"); kein erfundenes Datum
 *       (Charter-Regel 2).</li>
 * </ul>
 */
public final class CachedIndexStore {

	private static final Logger LOGGER = LoggerFactory.getLogger("Vitrine/Catalog");

	private final EtagCache cache;

	public CachedIndexStore(EtagCache cache) {
		this.cache = Objects.requireNonNull(cache, "cache");
	}

	/**
	 * Produktions-Verdrahtung: derselbe Cache-Ordner wie
	 * {@link CatalogClient#production(Path)} ({@code &lt;vitrineConfigDir&gt;/cache/}).
	 */
	public static CachedIndexStore production(Path vitrineConfigDir) {
		return new CachedIndexStore(new EtagCache(vitrineConfigDir.resolve(CatalogEndpoints.CACHE_DIR_NAME)));
	}

	/** Die gecachte erste Index-Seite, geparst und schema-geprueft, oder leer. */
	public Optional<IndexPage> firstPage() {
		return cache.load(CatalogClient.indexCacheKey(1)).flatMap(entry -> parse(entry.body()));
	}

	/** Aenderungszeitpunkt des gecachten Rumpfes der ersten Index-Seite als lokales Datum, oder leer. */
	public Optional<LocalDate> firstPageAsOf() {
		return cache.storedAt(CatalogClient.indexCacheKey(1))
				.map(instant -> LocalDate.ofInstant(instant, ZoneId.systemDefault()));
	}

	private static Optional<IndexPage> parse(byte[] body) {
		try {
			JsonElement parsed = JsonParser.parseString(new String(body, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject()) {
				LOGGER.warn("Katalog-Cache: Rumpf ist kein JSON-Objekt, behandle ihn als fehlend.");
				return Optional.empty();
			}
			JsonObject json = parsed.getAsJsonObject();
			List<String> violations = IndexPageSchemaValidator.validate(json);
			if (!violations.isEmpty()) {
				LOGGER.warn("Katalog-Cache verletzt das Schema, behandle ihn als fehlend: {}",
						String.join("; ", violations));
				return Optional.empty();
			}
			return Optional.of(IndexPage.fromJson(json));
		} catch (RuntimeException e) {
			// deckt JsonParseException/JsonSyntaxException und jeden Formfehler
			// beim Auslesen ab -- geloggt, nicht verschluckt.
			LOGGER.warn("Katalog-Cache liess sich nicht auswerten, behandle ihn als fehlend: {}", e.toString());
			return Optional.empty();
		}
	}
}
