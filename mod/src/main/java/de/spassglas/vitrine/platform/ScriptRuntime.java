package de.spassglas.vitrine.platform;

import java.util.Map;

/**
 * Eine Sprach-Laufzeit fuer genau ein Addon. Neue Sprachen (WebAssembly,
 * Python-Bridge, ...) kommen als weitere Implementierung dazu.
 */
public interface ScriptRuntime extends AutoCloseable {

	/** Stellt die API als globale Variable {@code vitrine} bereit und fuehrt den Hauptquelltext aus. */
	void start(String fileName, String source, Map<String, Object> api) throws Exception;

	/** Fuehrt weiteren Quelltext desselben Addons aus (fuer {@code vitrine.require}). */
	Object run(String fileName, String source) throws Exception;

	@Override
	void close();

	/** Hoechstdauer eines einzelnen Aufrufs in Millisekunden (Endlosschleifen-Schutz). */
	long CALL_LIMIT_MS = 50;
	/** Hoechstdauer fuer das erste Ausfuehren des Hauptquelltexts. */
	long START_LIMIT_MS = 1000;
}
