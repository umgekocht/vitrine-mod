package de.spassglas.vitrine.ui.screen;

import de.spassglas.vitrine.ui.image.PackageImageTextures;
import de.spassglas.vitrine.ui.theme.ActiveThemeColors;
import de.spassglas.vitrine.ui.theme.RoundedRect;
import de.spassglas.vitrine.ui.theme.ThemeService;
import de.spassglas.vitrine.ui.theme.VitrineTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * ROADMAP GATE82: die Großansicht eines Screenshots (docs/UI-SPEC.md
 * "5. Detailseite": "Screenshots als horizontale Galerie, Klick öffnet
 * groß."). Zeigt genau ein Vorschaubild seitenrichtig eingepasst auf einer
 * abgedunkelten Fläche über dem vorigen Bildschirm -- Klick (egal wo) oder
 * ESC schließt wieder, genau wie {@code ConfirmUninstallDialog}.
 *
 * <p>Vorher gab es diese Großansicht gar nicht -- die Galerie selbst hatte
 * keinen {@code mouseClicked}/Eingabe-Pfad, ein Klick auf ein Vorschaubild
 * tat nichts.</p>
 */
public final class ScreenshotViewerScreen extends Screen {

	private final Screen parentScreen;
	private final Path sourceFile;
	private final List<String> screenshotPaths;
	private final int index;

	private final Runnable themeRepaintListener = this::onThemeChanged;

	public ScreenshotViewerScreen(Screen parentScreen, Path sourceFile, List<String> screenshotPaths, int index) {
		super(Text.translatable("vitrine.detail.screenshots.viewer.title"));
		this.parentScreen = parentScreen;
		this.sourceFile = sourceFile;
		this.screenshotPaths = List.copyOf(screenshotPaths);
		if (index < 0 || index >= this.screenshotPaths.size()) {
			throw new IllegalArgumentException(
					"Screenshot-Index " + index + " ausserhalb von 0.." + (this.screenshotPaths.size() - 1));
		}
		this.index = index;
		ThemeService.instance().addRepaintListener(themeRepaintListener);
	}

	@Override
	public void removed() {
		ThemeService.instance().removeRepaintListener(themeRepaintListener);
		super.removed();
	}

	private void onThemeChanged() {
		// Keine eigenen Widgets -- nur ein Neuzeichnen ist noetig, kein init().
	}

	/**
	 * Vanilla legt in {@code super.render(...)} die Menue-Unschaerfe ueber alles
	 * bis dahin Gezeichnete; den Hintergrund zeichnet dieser Bildschirm selbst.
	 */
	@Override
	public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		super.renderBackground(context, mouseX, mouseY, delta);
		context.fill(0, 0, this.width, this.height, ActiveThemeColors.overlayScrim());

		int boxWidth = Math.max(1, this.width - VitrineTheme.SPACE_PAGE * 2);
		int boxHeight = Math.max(1, this.height - VitrineTheme.SPACE_PAGE * 2);
		int boxX = (this.width - boxWidth) / 2;
		int boxY = (this.height - boxHeight) / 2;

		String path = screenshotPaths.get(index);
		Optional<PackageImageTextures.Texture> texture =
				sourceFile == null ? Optional.empty() : PackageImageTextures.get(sourceFile, path);
		if (texture.isPresent()) {
			PackageImageTextures.drawFitted(context, texture.get(), boxX, boxY, boxWidth, boxHeight);
		} else {
			context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, ActiveThemeColors.surfaceSolid());
		}
		RoundedRect.border(context, boxX, boxY, boxWidth, boxHeight, VitrineTheme.RADIUS_CARD,
				ActiveThemeColors.borderTranslucent());

		super.render(context, mouseX, mouseY, delta);
	}

	/** Klick schliesst die Großansicht wieder, egal an welcher Stelle (docs/UI-SPEC.md "8. Fähigkeiten-Dialog"-Muster). */
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		close();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			close();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void close() {
		if (this.client != null) {
			this.client.setScreen(parentScreen);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	/** Nur fuer Tests/Anbindung: der angezeigte Screenshot-Index. */
	public int index() {
		return index;
	}

	/** Nur fuer Tests/Anbindung: der zugrunde liegende Bildschirm, zu dem ESC/Klick zurueckkehrt. */
	public Screen parentScreen() {
		return parentScreen;
	}
}
