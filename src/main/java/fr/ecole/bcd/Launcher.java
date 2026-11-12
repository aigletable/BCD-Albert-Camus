package fr.ecole.bcd;

/**
 * Point d'entrée d'amorçage standard (« Bootstrap Launcher ») pour l'application BCD Express.
 *
 * <p>Cette classe joue un rôle fondamental dans l'architecture d'exécution de l'application :</p>
 * <ul>
 *   <li><b>Résolution du module-path JavaFX :</b> Lorsque JavaFX est exécuté dans un environnement
 *       non modularisé (sans descripteur {@code module-info.java}), la JVM refuse de démarrer directement
 *       une classe principale héritant de {@link javafx.application.Application} en levant une exception.</li>
 *   <li><b>Délégation d'amorçage :</b> En ne dérivant d'aucune classe JavaFX dans sa hiérarchie de types,
 *       {@code Launcher} permet à la JVM de charger la machine virtuelle et les dépendances du classpath
 *       avant de déléguer l'initialisation du runtime graphique à {@link MainApp#main(String[])}.</li>
 *   <li><b>Compatibilité multiplateforme :</b> Assure une exécution transparente sur macOS, Windows et Linux,
 *       aussi bien via Maven ({@code javafx:run}) qu'à partir d'un JAR exécutable unifié (Fat JAR).</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class Launcher {

    /**
     * Constructeur privé pour empêcher l'instanciation de cette classe utilitaire d'amorce.
     */
    private Launcher() {
    }

    /**
     * Point d'entrée principal de la JVM lors de l'exécution de l'application BCD Express.
     *
     * @param args arguments passés en ligne de commande (transmis intégralement à {@link MainApp#main(String[])})
     */
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
