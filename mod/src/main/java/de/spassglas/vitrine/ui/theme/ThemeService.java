package de.spassglas.vitrine.ui.theme;

/**
 * Haelt den einen {@link ThemeManager} fuer den echten Client, verdrahtet mit
 * dem an {@code SystemToast} angebundenen {@link SystemToastThemePresenter}
 * (ROADMAP T2). Gleiches Muster wie
 * {@code de.spassglas.vitrine.hotapply.HotApplyService}: eine verschachtelte
 * Holder-Klasse ("initialization-on-demand holder", JLS 12.4.1), damit die
 * Client-Anbindung erst beim ersten Zugriff entsteht und der {@link ThemeManager}
 * selbst client-frei testbar bleibt.
 *
 * <p>Startzustand ist das Standard-Theme ({@link DefaultThemeTokens#INSTANCE},
 * aus {@link VitrineTheme} abgeleitet) -- ein frischer Client zeigt sofort ein
 * vollstaendiges Theme, ohne dass irgendeine Datei vorhanden sein muss
 * (Charter-Regel 12).</p>
 */
public final class ThemeService {

	private static final class InstanceHolder {
		static final ThemeManager INSTANCE = create();

		private static ThemeManager create() {
			ThemeManager manager = new ThemeManager(DefaultThemeTokens.INSTANCE, new SystemToastThemePresenter());
			// GATE21: die Masze in VitrineTheme folgen dem aktiven Theme. Als
			// erster Zuhoerer registriert, damit sie vor jedem Neuaufbau eines
			// Screens (spaeter registrierte Zuhoerer) schon umgestellt sind.
			manager.addRepaintListener(() -> VitrineTheme.applyFrom(manager::size));
			return manager;
		}
	}

	private ThemeService() {
	}

	/** Der eine {@link ThemeManager} fuer den echten Client. */
	public static ThemeManager instance() {
		return InstanceHolder.INSTANCE;
	}

	/** Kurzform fuer {@code instance().active()} -- der aktuell aktive Token-Satz. */
	public static ThemeTokens tokens() {
		return instance().active();
	}
}
