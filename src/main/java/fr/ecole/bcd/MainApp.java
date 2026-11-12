package fr.ecole.bcd;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import fr.ecole.bcd.service.ThemeService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import fr.ecole.bcd.database.DatabaseManager;

import java.io.IOException;

/**
 * Application principale JavaFX et gestionnaire de scène pour BCD Express (École Albert Camus Pasteur - Meudon).
 *
 * <p>Cette classe orchestre l'initialisation complète de l'interface utilisateur, la composition des onglets
 * métier, l'injection des feuilles de style AtlantaFX (Primer Light et Primer Dark), ainsi que le cycle
 * de vie des connexions à la base de données et des processus d'arrière-plan.</p>
 *
 * <h3>Cycle de vie JavaFX :</h3>
 * <ol>
 *   <li>{@link #init()} : Exécuté sur un thread d'amorçage JavaFX dédié avant l'affichage. Initialise la base SQLite via {@link DatabaseManager}.</li>
 *   <li>{@link #start(Stage)} : Exécuté sur le JavaFX Application Thread (UI Thread). Construit la barre de navigation supérieure, charge les FXML et affiche la fenêtre principale.</li>
 *   <li>{@link #stop()} : Exécuté à l'arrêt de l'application. Déclenche une sauvegarde SQLite automatique, coupe les alertes sonores et ferme proprement la connexion JDBC.</li>
 * </ol>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class MainApp extends Application {

    /**
     * Point d'entrée interne délégué par {@link Launcher#main(String[])}.
     *
     * @param args arguments de lancement en ligne de commande
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Phase d'initialisation préliminaire exécutée par le runtime JavaFX hors du thread UI.
     * Déclenche la création du schéma SQLite et l'exécution des scripts de migration.
     */
    @Override
    public void init() {
        DatabaseManager.initializeDatabase();
    }

    /**
     * Phase d'arrêt et de nettoyage déclenchée lors de la fermeture de la fenêtre principale.
     * Génère un backup horodaté de sécurité, arrête le pool audio {@link fr.ecole.bcd.service.SoundService}
     * et clôture la connexion JDBC {@link DatabaseManager}.
     */
    @Override
    public void stop() {
        try {
            new fr.ecole.bcd.service.BackupService().creerSauvegardeAutomatique();
        } catch (Exception ex) {
            System.err.println("⚠️ Sauvegarde automatique à la fermeture : " + ex.getMessage());
        }
        fr.ecole.bcd.service.SoundService.shutdown();
        DatabaseManager.closeConnection();
        System.out.println("👋 BCD Express fermé.");
    }

    /**
     * Référence statique vers l'instance unique de l'application active.
     */
    private static MainApp instance;

    /**
     * Référence statique vers la fenêtre principale (Stage) de l'application.
     */
    private static Stage primaryStage;

    /**
     * Récupère l'instance singleton de {@code MainApp}.
     *
     * @return l'instance courante de l'application
     */
    public static MainApp getInstance() {
        return instance;
    }

    /**
     * Récupère la fenêtre principale (Stage racine) de l'application.
     *
     * @return le {@link Stage} primaire JavaFX
     */
    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * Configure et stylise une boîte de dialogue (Dialog ou Alert JavaFX) pour garantir
     * un rendu visuel harmonisé avec le thème en vigueur et éviter les blocages plein écran sur macOS :
     * <ul>
     *   <li>Rattache la boîte de dialogue au {@link #primaryStage} comme fenêtre propriétaire.</li>
     *   <li>Injecte dynamiquement les feuilles de styles CSS adaptées au mode clair ou sombre actif.</li>
     * </ul>
     *
     * @param dialog la boîte de dialogue JavaFX à initialiser
     */
    public static void prepareDialog(javafx.scene.control.Dialog<?> dialog) {
        if (dialog == null) return;
        if (primaryStage != null) {
            try {
                dialog.initOwner(primaryStage);
            } catch (Exception ignored) {}
        }
        try {
            String css = MainApp.class.getResource("/css/app.css") != null
                    ? MainApp.class.getResource("/css/app.css").toExternalForm() : null;
            String darkCss = MainApp.class.getResource("/css/dark.css") != null
                    ? MainApp.class.getResource("/css/dark.css").toExternalForm() : null;

            if (ThemeService.isDarkMode()) {
                if (darkCss != null) {
                    dialog.getDialogPane().getStylesheets().setAll(darkCss);
                }
            } else {
                if (css != null) {
                    dialog.getDialogPane().getStylesheets().setAll(css);
                }
            }
        } catch (Exception ignored) {}
    }

    /**
     * Ouvre un document externe (fiche récapitulative HTML, guide PDF, export) dans le navigateur web par défaut du système.
     * <p>
     * Privilégie Google Chrome ou Safari sous macOS via des invocations de processus dédiées,
     * puis bascule sur {@code HostServices} ou {@link java.awt.Desktop} en repli universel.
     * </p>
     *
     * @param file le fichier local à visualiser
     */
    public static void openDocument(java.io.File file) {
        if (file == null || !file.exists()) return;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac")) {
                boolean opened = false;
                if (new java.io.File("/Applications/Google Chrome.app").exists()) {
                    try {
                        new ProcessBuilder("open", "-a", "Google Chrome", file.getAbsolutePath()).start();
                        opened = true;
                    } catch (Exception ignored) {}
                }
                if (!opened && new java.io.File("/Applications/Safari.app").exists()) {
                    try {
                        new ProcessBuilder("open", "-a", "Safari", file.getAbsolutePath()).start();
                        opened = true;
                    } catch (Exception ignored) {}
                }
                if (!opened) {
                    new ProcessBuilder("open", file.getAbsolutePath()).start();
                }
                return;
            } else if (os.contains("win")) {
                new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", file.getAbsolutePath()).start();
                return;
            } else if (os.contains("nix") || os.contains("nux")) {
                new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
                return;
            }

            if (instance != null) {
                instance.getHostServices().showDocument(file.toURI().toString());
                return;
            }

            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(file.toURI());
            }
        } catch (Exception e) {
            System.err.println("Erreur ouverture document : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Point d'entrée graphique principal JavaFX.
     * Construit l'arbre de composants (SceneGraph), assemble les onglets FXML,
     * configure les écouteurs de synchronisation inter-onglets et affiche la fenêtre principale.
     *
     * @param stage la fenêtre primaire fournie par le runtime JavaFX
     */
    @Override
    public void start(Stage stage) {
        instance = this;
        primaryStage = stage;

        /* ── 1. Appliquer le thème AtlantaFX (Primer Light ou Primer Dark) ──────────── */
        if (ThemeService.isDarkMode()) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        }

        /* ── 2. Construire les onglets ────────────────────────────────── */
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // Onglet Prêts & Retours (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("📖  Prêts & Retours", "/fxml/prets.fxml"));

        // Onglet Inventaire & Livres (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("📚  Inventaire & Livres", "/fxml/inventaire.fxml"));

        // Onglet Récolement & Inventaire physique (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("📋  Récolement", "/fxml/recolement.fxml"));

        // Onglet Utilisateurs (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("👥  Utilisateurs", "/fxml/utilisateurs.fxml"));

        // Onglet Mallettes Pédagogiques (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("🎒  Mallettes Thématiques", "/fxml/mallettes.fxml"));

        // Synchronisation automatique des données lors de la navigation entre onglets
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() != null) {
                Object controller = newTab.getUserData();
                if (controller instanceof fr.ecole.bcd.controller.RecolementController rc) {
                    rc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.InventaireController ic) {
                    ic.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.PretsController pc) {
                    pc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.UtilisateursController uc) {
                    uc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.MallettesController mc) {
                    mc.loadData();
                }
            }
        });

        /* ── 3. Bandeau supérieur officiel de l'école (Style Maquette) ───────── */
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-school-header");

        // Carré bleu avec livre
        Label logoIcon = new Label("📖");
        logoIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: white; -fx-background-color: #1A66FF; " +
                "-fx-background-radius: 8px; -fx-min-width: 30px; -fx-min-height: 30px; -fx-alignment: center;");

        Label appName = new Label("BCD Express");
        appName.setStyle("-fx-font-weight: 800; -fx-font-size: 17px; -fx-text-fill: #1A66FF;");

        HBox brandBox = new HBox(8, logoIcon, appName);
        brandBox.setAlignment(Pos.CENTER_LEFT);

        Label separator = new Label("|");
        separator.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 14px;");

        Label schoolBadge = new Label("🏫 École Albert Camus Pasteur • Meudon");
        schoolBadge.getStyleClass().add("school-name-badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label subtitle = new Label("Bibliothèque Centre Documentaire");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #94A3B8; -fx-font-weight: 500;");

        // Bouton Sauvegardes global
        Button btnBackup = new Button("💾 Sauvegardes");
        btnBackup.getStyleClass().addAll("btn-outline", "btn-sm");
        btnBackup.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px; -fx-cursor: hand;");
        btnBackup.setOnAction(e -> fr.ecole.bcd.view.BackupDialogHelper.openSauvegardeDialog(() -> {
            if (tabPane.getSelectionModel().getSelectedItem() != null) {
                Object ctrl = tabPane.getSelectionModel().getSelectedItem().getUserData();
                if (ctrl instanceof fr.ecole.bcd.controller.InventaireController ic) ic.loadData();
                else if (ctrl instanceof fr.ecole.bcd.controller.UtilisateursController uc) uc.loadData();
                else if (ctrl instanceof fr.ecole.bcd.controller.PretsController pc) pc.loadData();
            }
        }));

        // Bouton Thème Jour / Nuit
        Button btnTheme = new Button();
        btnTheme.getStyleClass().add("theme-toggle-btn");

        topBar.getChildren().addAll(brandBox, separator, schoolBadge, spacer, subtitle, btnBackup, btnTheme);

        VBox root = new VBox(topBar, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        /* ── 4. Scène et fenêtre ─────────────────────────────────────── */
        Scene scene = new Scene(root, 1080, 720);

        // Initialiser le thème (Mode Jour ou Mode Nuit)
        btnTheme.setOnAction(e -> ThemeService.toggleTheme(scene, btnTheme));
        ThemeService.initTheme(scene, btnTheme);

        stage.setTitle("BCD Express – École Albert Camus Pasteur (Meudon)");
        stage.setMinWidth(950);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();

        System.out.println("✅ BCD Express démarré avec succès (École Albert Camus Pasteur - Meudon).");
    }

    /**
     * Charge une vue FXML et instancie son contrôleur associé dans un nouvel onglet {@link Tab}.
     * Le contrôleur instancié est enregistré dans les {@code userData} de l'onglet pour permettre
     * son rafraîchissement automatique lors de la sélection de l'onglet.
     *
     * @param title l'intitulé textuel affiché dans l'onglet
     * @param fxmlPath le chemin du fichier FXML dans les ressources de l'application
     * @return l'onglet prêt à être inséré dans le {@link TabPane}
     */
    private Tab createFxmlTab(String title, String fxmlPath) {
        Tab tab = new Tab(title);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node content = loader.load();
            tab.setContent(content);
            tab.setUserData(loader.getController());
        } catch (IOException e) {
            System.err.println("❌ Erreur de chargement FXML : " + fxmlPath);
            e.printStackTrace();
            // Fallback : placeholder en cas d'erreur
            tab.setContent(createPlaceholderContent(title, "⚠️ Erreur de chargement : " + e.getMessage()));
        }
        return tab;
    }

    /**
     * Construit un onglet de remplacement provisoire (« Placeholder ») avec titre et description.
     *
     * @param title le titre de l'onglet
     * @param description le message explicatif affiché au centre
     * @return un onglet visuel d'attente
     */
    private Tab createPlaceholderTab(String title, String description) {
        Tab tab = new Tab(title);
        tab.setContent(createPlaceholderContent(title, description));
        return tab;
    }

    /**
     * Génère un conteneur vertical {@link VBox} centré arborant un message d'attente stylisé.
     *
     * @param title le titre principal
     * @param description le détail textuel ou le motif d'erreur
     * @return le nœud graphique prêt à l'affichage
     */
    private VBox createPlaceholderContent(String title, String description) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666;");

        Label statusLabel = new Label("⏳ En construction…");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #999; -fx-font-style: italic;");

        VBox content = new VBox(16, titleLabel, descLabel, statusLabel);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));
        return content;
    }
}
