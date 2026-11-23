package fr.ecole.bcd;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import fr.ecole.bcd.service.ThemeService;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
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
     * Référence statique vers le TabPane principal pour navigation inter-onglets.
     */
    private static TabPane mainTabPane;

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
     * Bascule automatiquement vers l'onglet dont le titre contient le texte spécifié.
     *
     * @param titleContains sous-chaîne du titre de l'onglet
     */
    public static void selectTab(String titleContains) {
        if (mainTabPane != null) {
            for (Tab tab : mainTabPane.getTabs()) {
                if (tab.getText() != null && tab.getText().contains(titleContains)) {
                    mainTabPane.getSelectionModel().select(tab);
                    break;
                }
            }
        }
    }

    /**
     * Récupère le contrôleur associé à un onglet donné.
     *
     * @param titleContains sous-chaîne du titre de l'onglet
     * @return le contrôleur instancié ou {@code null}
     */
    public static Object getTabController(String titleContains) {
        if (mainTabPane != null) {
            for (Tab tab : mainTabPane.getTabs()) {
                if (tab.getText() != null && tab.getText().contains(titleContains)) {
                    return tab.getUserData();
                }
            }
        }
        return null;
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
            String activeCssPath = ThemeService.isDarkMode()
                    ? ThemeService.getThemePreset().getDarkCss()
                    : ThemeService.getThemePreset().getLightCss();
            String css = MainApp.class.getResource(activeCssPath) != null
                    ? MainApp.class.getResource(activeCssPath).toExternalForm() : null;
            if (css == null) {
                css = MainApp.class.getResource(ThemeService.isDarkMode() ? "/css/dark.css" : "/css/app.css") != null
                        ? MainApp.class.getResource(ThemeService.isDarkMode() ? "/css/dark.css" : "/css/app.css").toExternalForm() : null;
            }
            if (css != null) {
                dialog.getDialogPane().getStylesheets().setAll(css);
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
        mainTabPane = tabPane;

        // Onglet Prêts & Retours (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("📖  Prêts & Retours", "/fxml/prets.fxml"));

        // Onglet Inventaire & Livres (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("📚  Inventaire & Livres", "/fxml/inventaire.fxml"));

        // Onglet Étiquettes & Codes-barres (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("🏷️  Étiquettes", "/fxml/etiquettes.fxml"));

        // Onglet Récolement & Inventaire physique (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("📋  Récolement", "/fxml/recolement.fxml"));

        // Onglet Utilisateurs (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("👥  Utilisateurs", "/fxml/utilisateurs.fxml"));

        // Onglet Mallettes Pédagogiques (FXML chargé)
        tabPane.getTabs().add(createFxmlTab("🎒  Mallettes Thématiques", "/fxml/mallettes.fxml"));

        // Onglet Plan 3D de la BCD & Rangement (FXML chargé directement sans ScrollPane pour garantir un plan fixe)
        tabPane.getTabs().add(createFxmlTabDirect("🗺️  Plan 3D BCD", "/fxml/plan3d.fxml"));

        // Synchronisation automatique des données lors de la navigation entre onglets
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getUserData() != null) {
                Object controller = newTab.getUserData();
                if (controller instanceof fr.ecole.bcd.controller.RecolementController rc) {
                    rc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.InventaireController ic) {
                    ic.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.EtiquettesController ec) {
                    ec.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.PretsController pc) {
                    pc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.UtilisateursController uc) {
                    uc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.MallettesController mc) {
                    mc.loadData();
                } else if (controller instanceof fr.ecole.bcd.controller.Plan3DController p3c) {
                    p3c.loadData();
                }
            }
        });

        /* ── 3. Bandeau supérieur officiel de l'école (Style Maquette & KPIs) ───────── */
        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-school-header");

        // Carré bleu avec livre
        Label logoIcon = new Label("📖");
        logoIcon.setStyle("-fx-font-size: 14px; -fx-text-fill: white; -fx-background-color: linear-gradient(to bottom right, #2563EB, #1D4ED8); " +
                "-fx-background-radius: 8px; -fx-min-width: 30px; -fx-min-height: 30px; -fx-alignment: center; " +
                "-fx-effect: dropshadow(gaussian, rgba(37, 99, 235, 0.35), 6, 0, 0, 1);");

        Label appName = new Label("BCD Express");
        appName.setStyle("-fx-font-weight: 800; -fx-font-size: 17px; -fx-text-fill: #1D4ED8;");

        HBox brandBox = new HBox(8, logoIcon, appName);
        brandBox.setAlignment(Pos.CENTER_LEFT);

        Label separator = new Label("|");
        separator.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 14px;");

        Label schoolBadge = new Label("🏫 École Albert Camus Pasteur • Meudon");
        schoolBadge.getStyleClass().add("school-name-badge");

        // Widgets KPI en direct (Nombre total de livres & emprunts en cours)
        Label kpiLivres = new Label("📚 … livres");
        kpiLivres.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #334155; -fx-background-color: #F1F5F9; -fx-padding: 3 9; -fx-background-radius: 999px; -fx-border-color: #E2E8F0; -fx-border-radius: 999px;");
        Label kpiPrets = new Label("🎒 … emprunts");
        kpiPrets.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #1D4ED8; -fx-background-color: #EFF6FF; -fx-padding: 3 9; -fx-background-radius: 999px; -fx-border-color: #BFDBFE; -fx-border-radius: 999px;");
        HBox kpiBox = new HBox(6, kpiLivres, kpiPrets);
        kpiBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

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
            refreshHeaderKpis(kpiLivres, kpiPrets);
        }));

        // Bouton Thème Jour / Nuit
        Button btnTheme = new Button();
        btnTheme.getStyleClass().add("theme-toggle-btn");

        topBar.getChildren().addAll(brandBox, separator, schoolBadge, kpiBox, spacer, btnBackup, btnTheme);

        VBox root = new VBox(topBar, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        /* ── 4. Scène et fenêtre ─────────────────────────────────────── */
        // Dimensions confortables et spacieuses si l'utilisateur dé-maximise la fenêtre
        Scene scene = new Scene(root, 1200, 780);

        btnTheme.setOnAction(e -> ThemeService.toggleTheme(scene, btnTheme));
        ThemeService.initTheme(scene, btnTheme);

        // Chargement initial des KPIs en direct
        refreshHeaderKpis(kpiLivres, kpiPrets);
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldT, newT) -> refreshHeaderKpis(kpiLivres, kpiPrets));

        stage.setTitle("BCD Express – École Albert Camus Pasteur (Meudon)");
        stage.setMinWidth(960);
        stage.setMinHeight(620);
        stage.setScene(scene);

        // Calquer immédiatement sur la grandeur maximale de l'écran (Plein écran / Maximisé pour Mac & écran BCD)
        try {
            Screen screen = Screen.getPrimary();
            if (screen != null) {
                Rectangle2D visualBounds = screen.getVisualBounds();
                stage.setX(visualBounds.getMinX());
                stage.setY(visualBounds.getMinY());
                stage.setWidth(visualBounds.getWidth());
                stage.setHeight(visualBounds.getHeight());
            }
        } catch (Exception ignored) {}
        try {
            stage.setMaximized(true);
        } catch (Exception ignored) {}

        stage.show();

        System.out.println("✅ BCD Express démarré avec succès (École Albert Camus Pasteur - Meudon).");
    }

    /**
     * Charge une vue FXML et instancie son contrôleur associé dans un nouvel onglet {@link Tab}.
     * Le contenu est enveloppé dans un {@link ScrollPane} adaptatif et invisible :
     * <ul>
     *   <li>En plein écran : le contenu s'étire et remplit 100% de la fenêtre (fitToWidth &amp; fitToHeight).</li>
     *   <li>En mode fenêtré ou écran réduit : les ascenseurs s'activent pour garantir qu'aucun bouton ni tableau ne soit écrasé.</li>
     * </ul>
     * Le contrôleur instancié est enregistré dans les {@code userData} de l'onglet.
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

            if (content instanceof Region region) {
                // Tailles minimales ergonomiques garanties (évite tout écrasement des contrôles)
                region.setMinWidth(960);
                region.setMinHeight(580);
            }

            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.getStyleClass().add("tab-scroll-pane");
            scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; -fx-border-width: 0;");

            tab.setContent(scrollPane);
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
     * Charge une vue FXML et instancie son contrôleur directement dans l'onglet sans ScrollPane externe.
     * Utilisé pour les vues à disposition fixe et interactive (comme le Plan 3D de la BCD)
     * afin d'éviter toute boucle d'agrandissement et de garantir une page stable.
     *
     * @param title l'intitulé textuel affiché dans l'onglet
     * @param fxmlPath le chemin du fichier FXML dans les ressources de l'application
     * @return l'onglet prêt à être inséré dans le {@link TabPane}
     */
    private Tab createFxmlTabDirect(String title, String fxmlPath) {
        Tab tab = new Tab(title);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Node content = loader.load();
            tab.setContent(content);
            tab.setUserData(loader.getController());
        } catch (IOException e) {
            System.err.println("❌ Erreur de chargement FXML direct : " + fxmlPath);
            e.printStackTrace();
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

    /**
     * Rafraîchit les compteurs KPIs du bandeau d'en-tête (Nombre total de livres & emprunts actifs).
     *
     * @param lblLivres label affichant le total des livres
     * @param lblPrets  label affichant les emprunts en cours
     */
    public static void refreshHeaderKpis(Label lblLivres, Label lblPrets) {
        if (lblLivres == null && lblPrets == null) return;
        javafx.concurrent.Task<int[]> task = new javafx.concurrent.Task<>() {
            @Override
            protected int[] call() {
                int nbL = 0;
                int nbP = 0;
                try {
                    nbL = new fr.ecole.bcd.database.LivreDao().count();
                    nbP = new fr.ecole.bcd.database.EmpruntDao().countTotalEnCours();
                } catch (Exception ignored) {}
                return new int[]{nbL, nbP};
            }
        };
        task.setOnSucceeded(e -> {
            int[] vals = task.getValue();
            if (lblLivres != null) lblLivres.setText("📚 " + vals[0] + " livres");
            if (lblPrets != null) lblPrets.setText("🎒 " + vals[1] + " emprunt" + (vals[1] > 1 ? "s" : ""));
        });
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    }
}
