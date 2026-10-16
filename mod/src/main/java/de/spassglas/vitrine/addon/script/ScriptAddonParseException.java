package de.spassglas.vitrine.addon.script;

/**
 * Ein {@code scripts/*.vs} liess sich nicht in ein {@link ScriptAddon}
 * uebersetzen -- kaputtes JSON, ein unbekanntes Ereignis/eine unbekannte
 * Aktion, oder eine in sich fehlerhafte eingebettete Bedingung/Formel
 * ({@link de.spassglas.vitrine.script.ScriptParseException}). Immer eine
 * gepruefte Ausnahme mit Klartext-Meldung -- {@link ScriptAddonLoader} faengt
 * sie und laesst das betroffene Paket ohne Skripte, statt den Client
 * mitzureissen (Charter-Regel 2).
 */
public class ScriptAddonParseException extends Exception {

	public ScriptAddonParseException(String message) {
		super(message);
	}

	public ScriptAddonParseException(String message, Throwable cause) {
		super(message, cause);
	}
}
