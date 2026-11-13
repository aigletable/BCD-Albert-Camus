package fr.ecole.bcd.service;

import atlantafx.base.theme.CupertinoDark;
import atlantafx.base.theme.CupertinoLight;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import java.util.prefs.Preferences;

/**
 * Service de gestion du thème visuel (Mode Jour / Mode Nuit) et des styles d'interface
 * (Cupertino Moderne Apple &harr; Primer Classique d'origine) pour BCD Express.
 *
 * <p>Ce service offre une **porte de sortie immédiate** permettant à tout moment
 * de basculer entre le nouveau design moderne et le design classique d'origine.</p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 2.0 (Dernière modification : 2026-11-12)
 */
public final class ThemeService {

    /**
     * Presets graphiques disponibles dans BCD Express.
     */
    public enum ThemePreset {
        CUPERTINO("🍏 Cupertino (Moderne Apple)", "/css/app.css", "/css/dark.css"),
        PRIMER_CLASSIQUE("🏛️ Primer (Design Classique)", "/css/app-classic.css", "/css/dark-classic.css");

        private final String label;
        private final String lightCss;
        private final String darkCss;

        ThemePreset(String label, String lightCss, String darkCss) {
            this.label = label;
            this.lightCss = lightCss;
            this.darkCss = darkCss;
        }

        public String getLabel() {
            return label;
        }

        public String getLightCss() {
            return lightCss;
        }

        public String getDarkCss() {
            return darkCss;
        }
    }

    private static final String PREF_KEY_DARK = "dark_mode_active";
    private static final String PREF_KEY_PRESET = "theme_preset_selected";

    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeService.class);

    private static boolean darkMode = PREFS.getBoolean(PREF_KEY_DARK, false);
    private static ThemePreset currentPreset = ThemePreset.CUPERTINO;

    private ThemeService() {
        // Classe utilitaire
    }

    public static boolean isDarkMode() {
        return darkMode;
    }

    public static ThemePreset getThemePreset() {
        return currentPreset;
    }

    public static void initTheme(Scene scene, Button toggleBtn) {
        applyTheme(scene, toggleBtn);
    }

    public static void toggleTheme(Scene scene, Button toggleBtn) {
        darkMode = !darkMode;
        try {
            PREFS.putBoolean(PREF_KEY_DARK, darkMode);
        } catch (Exception ignored) {}
        applyTheme(scene, toggleBtn);
    }

    /**
     * Change le preset graphique (ex: Cupertino &harr; Primer classique) et applique les styles instantanément.
     *
     * @param preset    le nouveau preset
     * @param scene     la scène principale
     * @param toggleBtn le bouton de bascule jour/nuit
     */
    public static void setPreset(ThemePreset preset, Scene scene, Button toggleBtn) {
        if (preset == null) return;
        currentPreset = preset;
        try {
            PREFS.put(PREF_KEY_PRESET, preset.name());
        } catch (Exception ignored) {}
        applyTheme(scene, toggleBtn);
    }

    /**
     * Applique les styles CSS et le UserAgentStylesheet appropriés sur la scène.
     */
    public static void applyTheme(Scene scene, Button toggleBtn) {
        String activeCssPath = darkMode ? currentPreset.getDarkCss() : currentPreset.getLightCss();
        String cssUrl = ThemeService.class.getResource(activeCssPath) != null
                ? ThemeService.class.getResource(activeCssPath).toExternalForm() : null;

        // Si le fichier spécifique est introuvable (ex: fallback), utiliser app.css
        if (cssUrl == null) {
            cssUrl = ThemeService.class.getResource(darkMode ? "/css/dark.css" : "/css/app.css") != null
                    ? ThemeService.class.getResource(darkMode ? "/css/dark.css" : "/css/app.css").toExternalForm() : null;
        }

        if (currentPreset == ThemePreset.CUPERTINO) {
            if (darkMode) {
                setAppStylesheet(new CupertinoDark().getUserAgentStylesheet());
            } else {
                setAppStylesheet(new CupertinoLight().getUserAgentStylesheet());
            }
        } else {
            if (darkMode) {
                setAppStylesheet(new PrimerDark().getUserAgentStylesheet());
            } else {
                setAppStylesheet(new PrimerLight().getUserAgentStylesheet());
            }
        }

        if (scene != null && cssUrl != null) {
            scene.getStylesheets().setAll(cssUrl);
        }

        if (toggleBtn != null) {
            if (darkMode) {
                toggleBtn.setText("☀️ Mode jour");
                toggleBtn.setTooltip(new Tooltip("Basculer en mode clair (jour)"));
                toggleBtn.setStyle("-fx-background-color: #21262D; -fx-text-fill: #FBBF24; -fx-border-color: #FBBF24; "
                        + "-fx-font-weight: 700; -fx-font-size: 12px; -fx-background-radius: 999px; -fx-border-radius: 999px; "
                        + "-fx-padding: 5px 14px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(251, 191, 36, 0.25), 6, 0, 0, 1);");
            } else {
                toggleBtn.setText("🌙 Mode nuit");
                toggleBtn.setTooltip(new Tooltip("Basculer en mode sombre (nuit)"));
                toggleBtn.setStyle("-fx-background-color: #F8FAFC; -fx-text-fill: #334155; -fx-border-color: #CBD5E1; "
                        + "-fx-font-weight: 600; -fx-font-size: 12px; -fx-background-radius: 999px; -fx-border-radius: 999px; "
                        + "-fx-padding: 5px 14px; -fx-cursor: hand;");
            }
        }
    }

    private static void setAppStylesheet(String stylesheet) {
        try {
            Application.setUserAgentStylesheet(stylesheet);
        } catch (IllegalStateException ignored) {
            // Ignoré si le toolkit JavaFX n'est pas initialisé (tests unitaires ou SmokeTest)
        }
    }
}
