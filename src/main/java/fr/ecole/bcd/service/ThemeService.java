package fr.ecole.bcd.service;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;

import java.util.prefs.Preferences;

/**
 * Service de gestion du thème visuel (Mode Jour / Mode Nuit) pour l'application BCD Express.
 * <p>
 * Ce service offre les fonctionnalités suivantes :
 * </p>
 * <ul>
 *   <li>Bascule dynamique et sans redémarrage entre les thèmes AtlantaFX {@link PrimerLight} et {@link PrimerDark}.</li>
 *   <li>Superposition de la feuille de style spécifique {@code dark.css} ou {@code app.css} sur la scène active.</li>
 *   <li>Mémorisation persistante de la préférence utilisateur via {@link Preferences} (nœud utilisateur local).</li>
 *   <li>Mise à jour en temps réel du bouton de bascule (icône, infobulle et micro-styles graphiques).</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class ThemeService {

    /**
     * Clé de préférence système enregistrant l'état d'activation du mode nuit (booléen).
     */
    private static final String PREF_KEY_DARK = "dark_mode_active";

    /**
     * Nœud de préférences utilisateur Java associé à la classe ThemeService.
     */
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeService.class);

    /**
     * État d'activation actuel du mode sombre en mémoire.
     */
    private static boolean darkMode = PREFS.getBoolean(PREF_KEY_DARK, false);

    /**
     * Constructeur privé pour empêcher l'instanciation de cette classe utilitaire.
     */
    private ThemeService() {
        // Classe utilitaire
    }

    /**
     * Indique si le mode sombre (nuit) est actuellement actif.
     *
     * @return {@code true} si le thème sombre est appliqué, {@code false} pour le thème clair
     */
    public static boolean isDarkMode() {
        return darkMode;
    }

    /**
     * Initialise et applique le thème par défaut ou mémorisé lors de la création de la fenêtre principale.
     *
     * @param scene     la scène principale de l'application
     * @param toggleBtn le bouton d'en-tête permettant de basculer de thème (peut être {@code null})
     */
    public static void initTheme(Scene scene, Button toggleBtn) {
        applyTheme(scene, toggleBtn);
    }

    /**
     * Inverse l'état du thème (Jour &harr; Nuit), persiste le choix dans les préférences et applique les modifications visuelles.
     *
     * @param scene     la scène principale
     * @param toggleBtn le bouton de bascule à synchroniser
     */
    public static void toggleTheme(Scene scene, Button toggleBtn) {
        darkMode = !darkMode;
        try {
            PREFS.putBoolean(PREF_KEY_DARK, darkMode);
        } catch (Exception ignored) {}
        applyTheme(scene, toggleBtn);
    }

    /**
     * Applique les styles CSS et le UserAgentStylesheet appropriés sur la scène et met à jour l'état visuel du bouton.
     *
     * @param scene     la scène JavaFX sur laquelle appliquer les feuilles de style
     * @param toggleBtn le bouton de bascule dont le texte et l'icône doivent être adaptés
     */
    public static void applyTheme(Scene scene, Button toggleBtn) {
        String appCss = ThemeService.class.getResource("/css/app.css") != null
                ? ThemeService.class.getResource("/css/app.css").toExternalForm() : null;
        String darkCss = ThemeService.class.getResource("/css/dark.css") != null
                ? ThemeService.class.getResource("/css/dark.css").toExternalForm() : null;

        if (darkMode) {
            setAppStylesheet(new PrimerDark().getUserAgentStylesheet());
            if (scene != null && darkCss != null) {
                scene.getStylesheets().setAll(darkCss);
            }
            if (toggleBtn != null) {
                toggleBtn.setText("☀️ Mode jour");
                toggleBtn.setTooltip(new Tooltip("Basculer en mode clair (jour)"));
                toggleBtn.setStyle("-fx-background-color: #21262D; -fx-text-fill: #FBBF24; -fx-border-color: #FBBF24; "
                        + "-fx-font-weight: 700; -fx-font-size: 12px; -fx-background-radius: 999px; -fx-border-radius: 999px; "
                        + "-fx-padding: 5px 14px; -fx-cursor: hand; -fx-effect: dropshadow(gaussian, rgba(251, 191, 36, 0.25), 6, 0, 0, 1);");
            }
        } else {
            setAppStylesheet(new PrimerLight().getUserAgentStylesheet());
            if (scene != null && appCss != null) {
                scene.getStylesheets().setAll(appCss);
            }
            if (toggleBtn != null) {
                toggleBtn.setText("🌙 Mode nuit");
                toggleBtn.setTooltip(new Tooltip("Basculer en mode sombre (nuit)"));
                toggleBtn.setStyle("-fx-background-color: #F8FAFC; -fx-text-fill: #334155; -fx-border-color: #CBD5E1; "
                        + "-fx-font-weight: 600; -fx-font-size: 12px; -fx-background-radius: 999px; -fx-border-radius: 999px; "
                        + "-fx-padding: 5px 14px; -fx-cursor: hand;");
            }
        }
    }

    /**
     * Définit la feuille de style globale du User-Agent JavaFX en gérant les exceptions éventuelles en mode headless.
     *
     * @param stylesheet l'URL de la feuille de style AtlantaFX
     */
    private static void setAppStylesheet(String stylesheet) {
        try {
            Application.setUserAgentStylesheet(stylesheet);
        } catch (IllegalStateException ignored) {
            // Ignoré si le toolkit JavaFX n'est pas initialisé (tests unitaires ou SmokeTest)
        }
    }
}
