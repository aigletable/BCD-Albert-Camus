package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.database.UtilisateurDao;
import fr.ecole.bcd.model.Emprunt;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.model.BilanStatistiques;
import fr.ecole.bcd.service.BilanExportService;
import fr.ecole.bcd.service.BilanExportService.PanierLivreItem;
import fr.ecole.bcd.service.EmpruntService;
import fr.ecole.bcd.service.EmpruntService.RegleMetierException;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.SoundService;
import fr.ecole.bcd.view.AnimationHelper;
import fr.ecole.bcd.view.DialogHelper;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import javafx.scene.input.KeyCode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Contrôleur de l'onglet Prêts & Retours.
 * <p>
 * Gère le guichet de prêt de la bibliothèque :
 * <ul>
 *   <li>Retour rapide par scan ou saisie du numéro d'exemplaire</li>
 *   <li>Nouvel emprunt avec sélection assistée et contrôle des règles métier</li>
 *   <li>Tableau des emprunts en cours avec alertes de retard</li>
 *   <li>Renouvellement en un clic</li>
 * </ul>
 * </p>
 */
public class PretsController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── Composants FXML — Action rapide ─────────────────────────────────
    @FXML private TextField quickReturnField;
    @FXML private Button btnQuickReturn;
    @FXML private Label lblQuickReturnMsg;
    @FXML private Button btnBilanActivite;
    @FXML private Button btnPretPanier;
    @FXML private Button btnExportRetards;
    @FXML private Button btnNewEmprunt;

    // ── Composants FXML — Cartes KPI ────────────────────────────────────
    @FXML private Label lblKpiTotal;
    @FXML private Label lblKpiRetards;
    @FXML private Label lblKpiRetoursJour;

    // ── Composants FXML — Filtres ───────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private ComboBox<String> classeFilter;
    @FXML private CheckBox chkRetardOnly;
    @FXML private Button btnRefresh;

    // ── Composants FXML — Tableau des emprunts ──────────────────────────
    @FXML private TableView<Emprunt> pretsTableView;
    @FXML private TableColumn<Emprunt, String> colExId;
    @FXML private TableColumn<Emprunt, Emprunt> colLivre;
    @FXML private TableColumn<Emprunt, Emprunt> colEmprunteur;
    @FXML private TableColumn<Emprunt, String> colDateEmprunt;
    @FXML private TableColumn<Emprunt, String> colDateLimite;
    @FXML private TableColumn<Emprunt, Emprunt> colStatut;
    @FXML private TableColumn<Emprunt, Emprunt> colActions;

    // ── Barre de statut ─────────────────────────────────────────────────
    @FXML private Label statusLabel;
    @FXML private Label statChips;

    // ── Services & DAOs ─────────────────────────────────────────────────
    private final EmpruntService empruntService = new EmpruntService();
    private final UtilisateurDao utilisateurDao = new UtilisateurDao();
    private final ExemplaireDao exemplaireDao = new ExemplaireDao();
    private final LivreDao livreDao = new LivreDao();

    // ── Données ─────────────────────────────────────────────────────────
    private final ObservableList<Emprunt> masterEmprunts = FXCollections.observableArrayList();
    private FilteredList<Emprunt> filteredEmprunts;

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @FXML
    public void initialize() {
        configureTableColumns();

        // Filtrage temps réel
        filteredEmprunts = new FilteredList<>(masterEmprunts, p -> true);
        pretsTableView.setItems(filteredEmprunts);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        classeFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        chkRetardOnly.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        // Chargement des données
        loadData();

        // Focus par défaut sur le champ de retour rapide pour être prêt au scan
        Platform.runLater(() -> quickReturnField.requestFocus());
    }

    private void configureTableColumns() {
        // N° Exemplaire
        colExId.setCellValueFactory(cell ->
                new SimpleStringProperty("#" + cell.getValue().getExemplaireId()));
        colExId.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        // Livre (Miniature + Titre en gras + Auteur en dessous)
        colLivre.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colLivre.setCellFactory(col -> new TableCell<>() {
            private final Label iconThumb = new Label("📖");
            private final Label titleLabel = new Label();
            private final Label authorLabel = new Label();
            private final VBox textBox = new VBox(2, titleLabel, authorLabel);
            private final HBox rowBox = new HBox(10, iconThumb, textBox);
            {
                iconThumb.getStyleClass().add("pret-thumb-icon");
                titleLabel.getStyleClass().add("pret-title-label");
                authorLabel.getStyleClass().add("pret-author-label");
                textBox.setAlignment(Pos.CENTER_LEFT);
                rowBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Emprunt e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                } else {
                    titleLabel.setText(e.getLivreTitre() != null ? e.getLivreTitre() : "Exemplaire #" + e.getExemplaireId());
                    authorLabel.setText(e.getLivreAuteur() != null && !e.getLivreAuteur().isBlank()
                            ? e.getLivreAuteur() : "Auteur non renseigné");
                    setGraphic(rowBox);
                }
            }
        });

        // Emprunteur (Nom Prénom + Classe)
        colEmprunteur.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colEmprunteur.setCellFactory(col -> new TableCell<>() {
            private final Label nameLabel = new Label();
            private final Label classBadge = new Label();
            private final HBox box = new HBox(8, nameLabel, classBadge);
            {
                nameLabel.getStyleClass().add("pret-name-label");
                classBadge.getStyleClass().add("pret-class-badge");
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Emprunt e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                } else {
                    String prenom = e.getUtilisateurPrenom() != null ? e.getUtilisateurPrenom() : "";
                    String nom = e.getUtilisateurNom() != null ? e.getUtilisateurNom().toUpperCase() : e.getUtilisateurNumero();
                    nameLabel.setText(prenom + " " + nom);

                    if (e.getUtilisateurClasse() != null && !e.getUtilisateurClasse().isBlank()) {
                        classBadge.setText(e.getUtilisateurClasse());
                        classBadge.setVisible(true);
                    } else {
                        classBadge.setVisible(false);
                    }
                    setGraphic(box);
                }
            }
        });

        // Date prêt
        colDateEmprunt.setCellValueFactory(cell -> {
            LocalDate d = cell.getValue().getDateEmprunt();
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "–");
        });
        colDateEmprunt.setStyle("-fx-alignment: CENTER; -fx-text-fill: #64748B;");

        // Date limite
        colDateLimite.setCellValueFactory(cell -> {
            LocalDate d = cell.getValue().getDateLimite();
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "–");
        });
        colDateLimite.setStyle("-fx-alignment: CENTER; -fx-font-weight: 600; -fx-text-fill: #334155;");

        // Statut & Retard (Pill badges comme dans la maquette)
        colStatut.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colStatut.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Emprunt e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                } else {
                    if (e.isEnRetard()) {
                        long jours = e.getJoursRetard();
                        badge.setText("⚠️ Retard (" + jours + " j)");
                        badge.getStyleClass().setAll("pill-orange");
                    } else if (e.isEstRenouvele()) {
                        long restants = ChronoUnit.DAYS.between(LocalDate.now(), e.getDateLimite());
                        badge.setText("🔄 Renouvelé (" + restants + " j)");
                        badge.getStyleClass().setAll("pill-blue");
                    } else {
                        long restants = ChronoUnit.DAYS.between(LocalDate.now(), e.getDateLimite());
                        String label = restants > 0 ? "En cours (" + restants + " j)" : "Retour du jour";
                        badge.setText(label);
                        badge.getStyleClass().setAll(restants > 0 ? "pill-green" : "pill-blue");
                    }
                    setAlignment(Pos.CENTER);
                    setGraphic(badge);
                }
            }
        });

        // Actions rapides (Rendre / Renouveler)
        colActions.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnRendre = new Button("↩️ Rendre");
            private final Button btnRenouveler = new Button("🔄 +7j");
            private final HBox box = new HBox(6, btnRendre, btnRenouveler);
            {
                btnRendre.getStyleClass().setAll("btn-blue", "btn-sm");
                btnRenouveler.getStyleClass().setAll("btn-outline", "btn-sm");
                box.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Emprunt e, boolean empty) {
                super.updateItem(e, empty);
                if (empty || e == null) {
                    setGraphic(null);
                } else {
                    btnRendre.setOnAction(evt -> handleRowRendre(e));
                    btnRenouveler.setOnAction(evt -> handleRowRenouveler(e));
                    btnRenouveler.setDisable(e.isEstRenouvele()); // un seul renouvellement autorisé
                    btnRenouveler.setTooltip(new Tooltip(e.isEstRenouvele()
                            ? "Cet emprunt a déjà été renouvelé."
                            : "Prolonger le prêt de 7 jours supplémentaires."));
                    setGraphic(box);
                }
            }
        });

        pretsTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    // =====================================================================
    //  Chargement des données
    // =====================================================================

    @FXML
    public void loadData() {
        try {
            List<Emprunt> enCours = empruntService.getEmpruntsEnCoursWithDetails();
            masterEmprunts.setAll(enCours);

            // Charger les classes pour le filtre
            List<String> classes = utilisateurDao.findAllClasses();
            classeFilter.getItems().clear();
            classeFilter.getItems().add(null); // Option "Toutes les classes"
            classeFilter.getItems().addAll(classes);

            updateCounters();

        } catch (SQLException e) {
            showError("Erreur de chargement", "Impossible de charger les emprunts : " + e.getMessage());
        }
    }

    private void applyFilters() {
        String searchText = searchField.getText();
        String selectedClasse = classeFilter.getValue();
        boolean retardOnly = chkRetardOnly.isSelected();

        filteredEmprunts.setPredicate(e -> {
            // Filtre retards uniquement
            if (retardOnly && !e.isEnRetard()) {
                return false;
            }

            // Filtre par classe
            if (selectedClasse != null && !selectedClasse.isBlank()) {
                if (e.getUtilisateurClasse() == null || !e.getUtilisateurClasse().equals(selectedClasse)) {
                    return false;
                }
            }

            // Filtre par texte de recherche
            if (searchText != null && !searchText.isBlank()) {
                String lower = searchText.trim().toLowerCase();
                String nomComplet = (e.getUtilisateurPrenom() + " " + e.getUtilisateurNom()).toLowerCase();
                String livre = (e.getLivreTitre() + " " + e.getLivreAuteur()).toLowerCase();
                String numEx = String.valueOf(e.getExemplaireId());
                String numUser = e.getUtilisateurNumero() != null ? e.getUtilisateurNumero().toLowerCase() : "";

                return nomComplet.contains(lower)
                        || livre.contains(lower)
                        || numEx.equals(lower)
                        || numUser.contains(lower);
            }

            return true;
        });

        updateCounters();
    }

    private void updateCounters() {
        int total = masterEmprunts.size();
        long retards = masterEmprunts.stream().filter(Emprunt::isEnRetard).count();
        LocalDate today = LocalDate.now();
        long retoursJour = masterEmprunts.stream()
                .filter(e -> e.getDateLimite() != null && today.equals(e.getDateLimite()))
                .count();

        if (lblKpiTotal != null) lblKpiTotal.setText(String.valueOf(total));
        if (lblKpiRetards != null) lblKpiRetards.setText(String.valueOf(retards));
        if (lblKpiRetoursJour != null) lblKpiRetoursJour.setText(String.valueOf(retoursJour));

        if (statusLabel != null) {
            statusLabel.setText(filteredEmprunts.size() + " prêt(s) affichés sur " + total + " en cours au total.");
        }
        if (statChips != null) {
            if (retards > 0) {
                statChips.setText("Base synchronisée • " + retards + " retard(s) à relancer");
                statChips.setStyle("-fx-text-fill: #EA580C; -fx-font-weight: bold;");
            } else {
                statChips.setText("Base synchronisée • 0 retard en cours");
                statChips.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold;");
            }
        }
    }

    @FXML
    public void handleFilterAllLoans() {
        if (chkRetardOnly != null) chkRetardOnly.setSelected(false);
        applyFilters();
    }

    @FXML
    public void handleFilterLateLoans() {
        if (chkRetardOnly != null) chkRetardOnly.setSelected(true);
        applyFilters();
    }

    @FXML
    public void handleFilterTodayLoans() {
        if (chkRetardOnly != null) chkRetardOnly.setSelected(false);
        applyFilters();
    }

    // =====================================================================
    //  Action — Retour rapide par scan
    // =====================================================================

    @FXML
    private void handleQuickReturn() {
        String input = quickReturnField.getText().trim();
        if (input.isEmpty()) return;

        // Tolérer un préfixe éventuel '#'
        if (input.startsWith("#")) {
            input = input.substring(1).trim();
        }

        try {
            Emprunt returned = null;

            // 1. Si la saisie est un petit entier (numéro d'exemplaire <= 7 chiffres)
            if (input.matches("^\\d{1,7}$")) {
                int exId = Integer.parseInt(input);
                returned = empruntService.retournerParExemplaire(exId);
            } else {
                // 2. Vérifier si c'est un format de code-barres / ISBN (uniquement chiffres et tirets, >= 9 car)
                String cleanIsbn = LivreMetadataService.cleanIsbn(input);
                boolean looksLikeIsbn = cleanIsbn.matches("^[0-9Xx]{9,13}$");

                if (looksLikeIsbn) {
                    try {
                        returned = empruntService.retournerParIsbn(cleanIsbn);
                    } catch (RegleMetierException ex) {
                        // Si échec par ISBN exact, tenter par nom / titre du livre au cas où
                        returned = empruntService.retournerParTitre(input);
                    }
                } else {
                    // 3. Saisie directe du NOM / TITRE du livre (ex: "J'aime lire", "Le Petit Prince", etc.)
                    returned = empruntService.retournerParTitre(input);
                }
            }

            // Signal sonore et visuel de validation immédiat pour confort à la douchette
            SoundService.playSuccess();
            AnimationHelper.flashSuccess(quickReturnField);

            loadData();

            String livre = returned.getLivreTitre() != null ? returned.getLivreTitre() : "Livre";
            String emprunteur = returned.getNomCompletEmprunteur();
            String classe = returned.getUtilisateurClasse() != null ? " (" + returned.getUtilisateurClasse() + ")" : "";

            lblQuickReturnMsg.setText("✅ " + livre + " (ex. #" + returned.getExemplaireId() + ") rendu par " + emprunteur + classe + " !");
            lblQuickReturnMsg.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold;");

            quickReturnField.clear();
            Platform.runLater(quickReturnField::requestFocus);

        } catch (RegleMetierException e) {
            SoundService.playError();
            AnimationHelper.flashError(quickReturnField);
            lblQuickReturnMsg.setText("⚠️ " + e.getMessage());
            lblQuickReturnMsg.setStyle("-fx-text-fill: #d73a49;");
            quickReturnField.selectAll();
            Platform.runLater(quickReturnField::requestFocus);
        } catch (SQLException e) {
            SoundService.playError();
            AnimationHelper.flashError(quickReturnField);
            showError("Erreur technique", e.getMessage());
        }
    }

    @FXML
    private void handleExportRetards() {
        List<Emprunt> retards = masterEmprunts.stream().filter(Emprunt::isEnRetard).toList();
        if (retards.isEmpty()) {
            showInfo("Aucun retard", "Excellente nouvelle ! Aucun emprunt n'est actuellement en retard.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter la liste des retards (CSV)");
        chooser.setInitialFileName("retards_bcd_" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier CSV (*.csv)", "*.csv"));

        File file = chooser.showSaveDialog(pretsTableView.getScene().getWindow());
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            // Écrire le BOM UTF-8 pour ouverture directe dans Excel
            writer.write("\uFEFF");
            writer.write("Numéro Élève;Nom;Prénom;Classe;Type;Titre du Livre;N° Exemplaire;Date Emprunt;Date Limite;Jours de retard\n");

            for (Emprunt e : retards) {
                writer.write(String.format("%s;%s;%s;%s;%s;%s;%d;%s;%s;%d\n",
                        cleanCsv(e.getUtilisateurNumero()),
                        cleanCsv(e.getUtilisateurNom()),
                        cleanCsv(e.getUtilisateurPrenom()),
                        cleanCsv(e.getUtilisateurClasse()),
                        e.getUtilisateurType() != null ? e.getUtilisateurType().name() : "",
                        cleanCsv(e.getLivreTitre()),
                        e.getExemplaireId(),
                        e.getDateEmprunt() != null ? e.getDateEmprunt().format(DATE_FMT) : "",
                        e.getDateLimite() != null ? e.getDateLimite().format(DATE_FMT) : "",
                        e.getJoursRetard()
                ));
            }

            showInfo("Export réussi", String.format("Le fichier de retards a été exporté avec succès !\n\n"
                    + "📁 Emplacement : %s\n"
                    + "⚠️ %d élève(s) / enseignant(s) en retard répertorié(s).",
                    file.getAbsolutePath(), retards.size()));

        } catch (IOException ex) {
            showError("Erreur d'exportation", "Impossible d'écrire le fichier : " + ex.getMessage());
        }
    }

    private String cleanCsv(String val) {
        if (val == null) return "";
        return val.replace(";", ",").replace("\n", " ").trim();
    }

    // =====================================================================
    //  Actions — Actions sur une ligne du tableau
    // =====================================================================

    private void handleRowRendre(Emprunt e) {
        try {
            empruntService.retourner(e.getId());
            loadData();
            updateStatus("✅ Exemplaire #" + e.getExemplaireId() + " (" + e.getTitreEtAuteurLivre() + ") rendu.");
        } catch (RegleMetierException ex) {
            showWarning("Retour impossible", ex.getMessage());
        } catch (SQLException ex) {
            showError("Erreur technique", ex.getMessage());
        }
    }

    private void handleRowRenouveler(Emprunt e) {
        try {
            empruntService.renouveler(e.getId());
            loadData();
            updateStatus("🔄 Prêt #" + e.getId() + " renouvelé jusqu'au "
                    + LocalDate.now().plusDays(7).format(DATE_FMT) + ".");
        } catch (RegleMetierException ex) {
            showWarning("Renouvellement impossible", ex.getMessage());
        } catch (SQLException ex) {
            showError("Erreur technique", ex.getMessage());
        }
    }

    // =====================================================================
    //  Action — Nouvel emprunt
    // =====================================================================

    @FXML
    private void handleNewEmprunt() {
        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Enregistrer un nouveau prêt");
        dialog.setHeaderText("Recherchez l'emprunteur par nom puis indiquez le livre physique à prêter.");

        ButtonType saveButtonType = new ButtonType("Valider le prêt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(560);

        VBox contentBox = new VBox(12);
        contentBox.setPadding(new Insets(16, 20, 16, 20));

        // ── 1. Recherche Emprunteur ─────────────────────────────────────────
        Label lblUserSection = new Label("1. Emprunteur (recherche par nom, prénom ou classe) :");
        lblUserSection.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField tfSearchUser = new TextField();
        tfSearchUser.setPromptText("🔍 Tapez le nom ou prénom de l'élève (ex: Dupont, Lucas)...");
        HBox.setHgrow(tfSearchUser, Priority.ALWAYS);

        ComboBox<String> cbClasseFilter = new ComboBox<>();
        cbClasseFilter.setPromptText("Toutes classes");
        cbClasseFilter.getItems().add(null);
        try {
            cbClasseFilter.getItems().addAll(utilisateurDao.findAllClasses());
        } catch (SQLException ignored) {}
        cbClasseFilter.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Toutes classes" : item);
            }
        });
        cbClasseFilter.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Toutes classes" : item);
            }
        });

        HBox searchRow = new HBox(8, tfSearchUser, cbClasseFilter);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        // Liste des utilisateurs
        ObservableList<Utilisateur> allUsers = FXCollections.observableArrayList();
        try {
            allUsers.addAll(utilisateurDao.findAll());
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les utilisateurs : " + e.getMessage());
        }

        FilteredList<Utilisateur> filteredUsers = new FilteredList<>(allUsers, p -> true);
        ListView<Utilisateur> lvUsers = new ListView<>(filteredUsers);
        lvUsers.setPrefHeight(120);
        lvUsers.setMaxHeight(150);
        lvUsers.setPlaceholder(new Label("Aucun élève trouvé avec cette recherche."));

        lvUsers.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Utilisateur u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                } else {
                    String role = u.getTypeCompte() == Utilisateur.TypeCompte.ELEVE ? "👧" : "👨‍🏫";
                    setText(role + "  " + u.getNom().toUpperCase() + " " + u.getPrenom()
                            + " (" + u.getClasse() + ") — " + u.getNumero());
                }
            }
        });

        // Cartouche de confirmation de l'emprunteur sélectionné
        Label lblSelectedUser = new Label("Aucun emprunteur sélectionné (cliquez sur un nom dans la liste).");
        lblSelectedUser.setStyle("-fx-font-style: italic; -fx-text-fill: #586069; -fx-font-size: 12px;");

        Label lblUserStatus = new Label();
        lblUserStatus.setStyle("-fx-font-size: 11px;");

        VBox userSummaryCard = new VBox(4, lblSelectedUser, lblUserStatus);
        userSummaryCard.setPadding(new Insets(8, 12, 8, 12));
        userSummaryCard.getStyleClass().add("dialog-subcard");

        SimpleObjectProperty<Utilisateur> selectedUserProp = new SimpleObjectProperty<>(null);

        // ── 2. Choix Exemplaire ─────────────────────────────────────────────
        Label lblExemplaireSection = new Label("2. Livre à prêter (par Nom du livre, N° d'exemplaire ou scan ISBN) :");
        lblExemplaireSection.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField tfExemplaireId = new TextField();
        tfExemplaireId.setPromptText("🔍 Tapez le nom du livre (ex: Le Petit Prince, J'aime lire) ou scannez...");
        Button btnCheckExemplaire = new Button("Vérifier");
        HBox exBox = new HBox(8, tfExemplaireId, btnCheckExemplaire);
        HBox.setHgrow(tfExemplaireId, Priority.ALWAYS);
        exBox.setAlignment(Pos.CENTER_LEFT);

        // Liste de suggestions de livres lorsqu'on tape du texte
        ListView<Livre> lvBookSuggestions = new ListView<>();
        lvBookSuggestions.setPrefHeight(90);
        lvBookSuggestions.setVisible(false);
        lvBookSuggestions.setManaged(false);
        lvBookSuggestions.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Livre item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String desc = item.getAuteur() != null && !item.getAuteur().isBlank()
                            ? " — " + item.getAuteur() : "";
                    setText("📖 " + item.getTitre() + desc);
                }
            }
        });

        Label lblExemplaireInfo = new Label("💡 Tapez le titre du livre, entrez le N° d'exemplaire ou scannez le code-barres.");
        lblExemplaireInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #586069;");

        // ── 3. Date limite ──────────────────────────────────────────────────
        Label lblDureeInfo = new Label("ℹ️ Durée du prêt : 7 jours (date limite calculée à J+7).");
        lblDureeInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #0366d6;");

        contentBox.getChildren().addAll(
                lblUserSection,
                searchRow,
                lvUsers,
                userSummaryCard,
                new Separator(),
                lblExemplaireSection,
                exBox,
                lvBookSuggestions,
                lblExemplaireInfo,
                lblDureeInfo
        );

        dialog.getDialogPane().setContent(contentBox);

        Node saveBtn = dialog.getDialogPane().lookupButton(saveButtonType);
        saveBtn.setDisable(true);

        // Stocke l'id d'exemplaire physique résolu (soit direct, soit via ISBN)
        final int[] resolvedExemplaireId = new int[]{-1};

        // Mise à jour de l'état du bouton Enregistrer
        Runnable updateSaveBtnState = () -> {
            saveBtn.setDisable(selectedUserProp.get() == null || tfExemplaireId.getText().isBlank());
        };

        // Écouteur de sélection utilisateur
        selectedUserProp.addListener((obs, oldVal, user) -> {
            if (user == null) {
                lblSelectedUser.setText("Aucun emprunteur sélectionné (cliquez sur un nom dans la liste).");
                lblSelectedUser.setStyle("-fx-font-style: italic; -fx-text-fill: #586069; -fx-font-size: 12px;");
                lblUserStatus.setText("");
            } else {
                String role = user.getTypeCompte() == Utilisateur.TypeCompte.ELEVE ? "👧" : "👨‍🏫";
                lblSelectedUser.setText("👤 Sélectionné : " + role + " " + user.getNom().toUpperCase()
                        + " " + user.getPrenom() + " (" + user.getClasse() + ") — " + user.getNumero());
                lblSelectedUser.setStyle("-fx-font-weight: bold; -fx-text-fill: #24292e; -fx-font-size: 12px;");

                try {
                    int count = empruntService.getEmpruntsEnCours(user.getNumero()).size();
                    int max = empruntService.getMaxEmprunts(user);
                    boolean aRetard = false;
                    for (Emprunt emp : empruntService.getEmpruntsEnCours(user.getNumero())) {
                        if (emp.isEnRetard()) {
                            aRetard = true;
                            break;
                        }
                    }

                    if (aRetard) {
                        lblUserStatus.setText("⚠️ Cet emprunteur a un ou plusieurs livres en retard (prêt bloqué) !");
                        lblUserStatus.setStyle("-fx-text-fill: #d73a49; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else if (count >= max) {
                        lblUserStatus.setText("⚠️ Quota atteint : " + count + " / " + max + " emprunt(s) en cours (prêt bloqué).");
                        lblUserStatus.setStyle("-fx-text-fill: #d73a49; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        lblUserStatus.setText("✅ Quota : " + count + " / " + max + " emprunt(s) en cours (prêt autorisé).");
                        lblUserStatus.setStyle("-fx-text-fill: #28a745; -fx-font-size: 11px;");
                    }
                } catch (SQLException ignored) {}
            }
            updateSaveBtnState.run();
        });

        // Filtrage dynamique des élèves (insensible à la casse et aux accents)
        Runnable applyUserFilter = () -> {
            String query = stripAccents(tfSearchUser.getText());
            String selectedClass = cbClasseFilter.getValue();

            filteredUsers.setPredicate(u -> {
                if (selectedClass != null && !selectedClass.isEmpty()) {
                    if (!selectedClass.equalsIgnoreCase(u.getClasse())) {
                        return false;
                    }
                }
                if (!query.isEmpty()) {
                    String nom = stripAccents(u.getNom());
                    String prenom = stripAccents(u.getPrenom());
                    String num = stripAccents(u.getNumero());
                    String c = stripAccents(u.getClasse());
                    return nom.contains(query)
                            || prenom.contains(query)
                            || (prenom + " " + nom).contains(query)
                            || (nom + " " + prenom).contains(query)
                            || num.contains(query)
                            || c.contains(query);
                }
                return true;
            });

            // Si un seul résultat filtré, le sélectionner automatiquement
            if (filteredUsers.size() == 1) {
                lvUsers.getSelectionModel().select(0);
                selectedUserProp.set(filteredUsers.get(0));
            }
        };

        tfSearchUser.textProperty().addListener((o, oldV, newV) -> applyUserFilter.run());
        cbClasseFilter.valueProperty().addListener((o, oldV, newV) -> applyUserFilter.run());

        // Interaction avec la ListView
        lvUsers.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                selectedUserProp.set(newV);
            }
        });

        // Touche Entrée ou clic dans la recherche ou la liste
        tfSearchUser.setOnAction(e -> {
            if (!filteredUsers.isEmpty()) {
                if (lvUsers.getSelectionModel().getSelectedItem() == null) {
                    lvUsers.getSelectionModel().select(0);
                    selectedUserProp.set(filteredUsers.get(0));
                }
                tfExemplaireId.requestFocus();
            }
        });

        tfSearchUser.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                lvUsers.requestFocus();
                if (lvUsers.getSelectionModel().getSelectedIndex() < 0 && !filteredUsers.isEmpty()) {
                    lvUsers.getSelectionModel().select(0);
                }
            }
        });

        lvUsers.setOnMouseClicked(e -> {
            if (lvUsers.getSelectionModel().getSelectedItem() != null) {
                selectedUserProp.set(lvUsers.getSelectionModel().getSelectedItem());
                tfExemplaireId.requestFocus();
            }
        });

        lvUsers.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (lvUsers.getSelectionModel().getSelectedItem() != null) {
                    selectedUserProp.set(lvUsers.getSelectionModel().getSelectedItem());
                    tfExemplaireId.requestFocus();
                }
            }
        });

        // ── Vérification exemplaire (Nom du livre, Numéro physique ou scan ISBN) ─────────
        Runnable checkExemplaire = () -> {
            String text = tfExemplaireId.getText().trim();
            if (text.startsWith("#")) text = text.substring(1).trim();

            resolvedExemplaireId[0] = -1;

            if (text.isEmpty()) {
                lblExemplaireInfo.setText("⚠️ Veuillez entrer un nom de livre, numéro d'exemplaire ou scanner un code-barres.");
                lblExemplaireInfo.setStyle("-fx-text-fill: #d73a49; -fx-font-size: 11px;");
                updateSaveBtnState.run();
                return;
            }

            try {
                // Cas 1 : Petit numéro d'exemplaire physique (1 à 7 chiffres)
                if (text.matches("^\\d{1,7}$")) {
                    int exId = Integer.parseInt(text);
                    Exemplaire ex = exemplaireDao.findById(exId);
                    if (ex == null) {
                        lblExemplaireInfo.setText("❌ Aucun exemplaire physique trouvé avec le N° #" + exId);
                        lblExemplaireInfo.setStyle("-fx-text-fill: #d73a49; -fx-font-size: 11px;");
                    } else {
                        Livre livre = livreDao.findByIsbn(ex.getIsbn());
                        String titre = livre != null ? livre.getTitre() : ex.getIsbn();

                        if (ex.getStatut() == Exemplaire.Statut.DISPONIBLE) {
                            resolvedExemplaireId[0] = exId;
                            lblExemplaireInfo.setText("✅ Livre : '" + titre + "' (ex. #" + exId + ") — Disponible au prêt");
                            lblExemplaireInfo.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 11px;");
                            lvBookSuggestions.setVisible(false);
                            lvBookSuggestions.setManaged(false);
                        } else {
                            lblExemplaireInfo.setText("⚠️ Livre : '" + titre + "' (ex. #" + exId + ") — Actuellement EMPRUNTÉ");
                            lblExemplaireInfo.setStyle("-fx-text-fill: #cf222e; -fx-font-weight: bold; -fx-font-size: 11px;");
                        }
                    }
                } else {
                    // Cas 2 : Recherche par NOM / TITRE du livre
                    List<Exemplaire> disposParTitre = exemplaireDao.findDisponiblesByTitre(text);
                    if (!disposParTitre.isEmpty()) {
                        Exemplaire firstDispo = disposParTitre.get(0);
                        resolvedExemplaireId[0] = firstDispo.getId();
                        Livre livre = livreDao.findByIsbn(firstDispo.getIsbn());
                        String titre = livre != null ? livre.getTitre() : text;
                        lblExemplaireInfo.setText("✅ Livre : '" + titre + "' — Exemplaire dispo #"
                                + firstDispo.getId() + " sélectionné (sur " + disposParTitre.size() + " dispo(s))");
                        lblExemplaireInfo.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 11px;");
                        lvBookSuggestions.setVisible(false);
                        lvBookSuggestions.setManaged(false);
                    } else {
                        // Vérifier si le livre existe dans le catalogue par titre
                        List<Livre> livresParTitre = livreDao.findByTitre(text);
                        if (!livresParTitre.isEmpty()) {
                            Livre l = livresParTitre.get(0);
                            lblExemplaireInfo.setText("⚠️ Livre : '" + l.getTitre() + "' — Tous les exemplaires sont actuellement empruntés");
                            lblExemplaireInfo.setStyle("-fx-text-fill: #cf222e; -fx-font-weight: bold; -fx-font-size: 11px;");
                        } else {
                            // Cas 3 : Scan du code-barres ISBN au dos du livre
                            String cleanIsbn = LivreMetadataService.cleanIsbn(text);
                            List<Exemplaire> dispos = cleanIsbn.isEmpty() ? List.of() : exemplaireDao.findDisponiblesByIsbn(cleanIsbn);

                            if (!dispos.isEmpty()) {
                                Exemplaire firstDispo = dispos.get(0);
                                resolvedExemplaireId[0] = firstDispo.getId();
                                Livre livre = livreDao.findByIsbn(cleanIsbn);
                                String titre = livre != null ? livre.getTitre() : cleanIsbn;
                                lblExemplaireInfo.setText("✅ Livre : '" + titre + "' — Exemplaire dispo #"
                                        + firstDispo.getId() + " sélectionné (sur " + dispos.size() + " dispo(s))");
                                lblExemplaireInfo.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 11px;");
                                lvBookSuggestions.setVisible(false);
                                lvBookSuggestions.setManaged(false);
                            } else {
                                Livre livre = !cleanIsbn.isEmpty() ? livreDao.findByIsbn(cleanIsbn) : null;
                                if (livre != null) {
                                    lblExemplaireInfo.setText("⚠️ Livre : '" + livre.getTitre() + "' — Aucun exemplaire disponible actuellement");
                                    lblExemplaireInfo.setStyle("-fx-text-fill: #cf222e; -fx-font-weight: bold; -fx-font-size: 11px;");
                                } else {
                                    lblExemplaireInfo.setText("❌ Aucun livre ou exemplaire trouvé pour : '" + text + "' (ni par nom, ni par ISBN, ni par N°)");
                                    lblExemplaireInfo.setStyle("-fx-text-fill: #d73a49; -fx-font-size: 11px;");
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                lblExemplaireInfo.setText("Erreur : " + e.getMessage());
            }

            updateSaveBtnState.run();
        };

        lvBookSuggestions.setOnMouseClicked(e -> {
            Livre sel = lvBookSuggestions.getSelectionModel().getSelectedItem();
            if (sel != null) {
                tfExemplaireId.setText(sel.getTitre());
                lvBookSuggestions.setVisible(false);
                lvBookSuggestions.setManaged(false);
                checkExemplaire.run();
            }
        });

        btnCheckExemplaire.setOnAction(e -> checkExemplaire.run());
        tfExemplaireId.setOnAction(e -> checkExemplaire.run());
        tfExemplaireId.textProperty().addListener((o, oldV, newV) -> {
            updateSaveBtnState.run();
            if (newV != null && newV.trim().length() >= 2 && !newV.trim().matches("^\\d+$")) {
                try {
                    List<Livre> suggestions = livreDao.findByTitre(newV.trim());
                    if (!suggestions.isEmpty()) {
                        lvBookSuggestions.getItems().setAll(suggestions);
                        lvBookSuggestions.setVisible(true);
                        lvBookSuggestions.setManaged(true);
                    } else {
                        lvBookSuggestions.setVisible(false);
                        lvBookSuggestions.setManaged(false);
                    }
                } catch (SQLException ignored) {}
            } else {
                lvBookSuggestions.setVisible(false);
                lvBookSuggestions.setManaged(false);
            }
        });

        dialog.setResultConverter(btnType -> {
            if (btnType == saveButtonType) {
                Utilisateur user = selectedUserProp.get();
                if (user == null) {
                    showWarning("Emprunteur manquant", "Veuillez sélectionner un élève ou enseignant.");
                    return false;
                }

                // Si pas encore vérifié, exécuter la vérification
                if (resolvedExemplaireId[0] <= 0) {
                    checkExemplaire.run();
                }

                int exId = resolvedExemplaireId[0];
                if (exId <= 0) {
                    showWarning("Exemplaire invalide", "Veuillez indiquer un exemplaire valide et disponible.");
                    return false;
                }

                try {
                    Emprunt emprunt = empruntService.emprunter(user, exId);
                    loadData();
                    updateStatus("✅ Emprunt #" + emprunt.getId() + " validé pour "
                            + user.getPrenom() + " " + user.getNom() + " (ex. #" + exId + ").");
                    return true;
                } catch (RegleMetierException e) {
                    showWarning("Prêt impossible", e.getMessage());
                    return false;
                } catch (SQLException e) {
                    showError("Erreur technique", e.getMessage());
                    return false;
                }
            }
            return false;
        });

        Platform.runLater(tfSearchUser::requestFocus);
        dialog.showAndWait();
    }

    /**
     * Supprime les accents et met en minuscules pour faciliter la recherche textuelle.
     */
    private static String stripAccents(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    // =====================================================================
    //  Utilitaires UI
    // =====================================================================

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    private void showError(String title, String content) {
        DialogHelper.showError(title, content);
    }

    private void showWarning(String title, String content) {
        DialogHelper.showWarning(title, content);
    }

    private void showInfo(String title, String content) {
        DialogHelper.showInfo(title, content);
    }

    // =====================================================================
    //  FEATURE 2 : Prêt Enseignant / Séries de classe (Panier)
    // =====================================================================

    @FXML
    private void handlePretPanier() {
        Dialog<ButtonType> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("🧺 Prêt de série / Panier de classe");
        dialog.setHeaderText("Prêt groupé d'ouvrages pour séries de classe ou projets pédagogiques.");
        ButtonType btnValiderType = new ButtonType("✅ Valider le prêt du panier", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnValiderType, ButtonType.CANCEL);

        VBox content = new VBox(14);
        content.setPrefWidth(720);
        content.setPrefHeight(560);
        content.setPadding(new Insets(10));

        // 1. Choix de l'enseignant
        Label lblTeacherSec = new Label("1. Enseignant(e) emprunteur :");
        lblTeacherSec.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField tfSearchTeacher = new TextField();
        tfSearchTeacher.setPromptText("🔍 Rechercher par nom, prénom ou classe…");
        HBox.setHgrow(tfSearchTeacher, Priority.ALWAYS);

        CheckBox chkAllUsers = new CheckBox("Tous les comptes");
        chkAllUsers.setTooltip(new Tooltip("Afficher aussi les élèves ou stagiaires si besoin"));

        HBox teacherFilterRow = new HBox(10, tfSearchTeacher, chkAllUsers);
        teacherFilterRow.setAlignment(Pos.CENTER_LEFT);

        ListView<Utilisateur> lvTeachers = new ListView<>();
        lvTeachers.setPrefHeight(100);

        List<Utilisateur> allUsers;
        try {
            allUsers = utilisateurDao.findAll();
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les utilisateurs : " + e.getMessage());
            return;
        }

        ObservableList<Utilisateur> displayedTeachers = FXCollections.observableArrayList();
        lvTeachers.setItems(displayedTeachers);

        Runnable refreshTeacherList = () -> {
            String q = stripAccents(tfSearchTeacher.getText());
            boolean showAll = chkAllUsers.isSelected();
            displayedTeachers.clear();
            for (Utilisateur u : allUsers) {
                if (!showAll && u.getTypeCompte() != Utilisateur.TypeCompte.ENSEIGNANT) {
                    continue;
                }
                String nomComplet = stripAccents(u.getNom() + " " + u.getPrenom() + " " + u.getClasse() + " " + u.getNumero());
                if (q.isEmpty() || nomComplet.contains(q)) {
                    displayedTeachers.add(u);
                }
            }
        };

        tfSearchTeacher.textProperty().addListener((obs, oldV, newV) -> refreshTeacherList.run());
        chkAllUsers.selectedProperty().addListener((obs, oldV, newV) -> refreshTeacherList.run());
        refreshTeacherList.run();

        // Affichage lisible des enseignants
        lvTeachers.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Utilisateur u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                } else {
                    String icon = u.getTypeCompte() == Utilisateur.TypeCompte.ENSEIGNANT ? "👩‍🏫" : "👤";
                    setText(icon + " " + u.getNom().toUpperCase() + " " + u.getPrenom()
                            + " — " + u.getClasse() + " (" + u.getNumero() + ")");
                }
            }
        });

        Label lblSelectedTeacher = new Label("Aucun enseignant sélectionné.");
        lblSelectedTeacher.setStyle("-fx-font-style: italic; -fx-text-fill: #586069;");
        SimpleObjectProperty<Utilisateur> selectedTeacherProp = new SimpleObjectProperty<>(null);

        lvTeachers.getSelectionModel().selectedItemProperty().addListener((obs, oldV, u) -> {
            selectedTeacherProp.set(u);
            if (u != null) {
                lblSelectedTeacher.setText("👩‍🏫 " + u.getNom().toUpperCase() + " " + u.getPrenom()
                        + " (" + u.getClasse() + ") sélectionné(e).");
                lblSelectedTeacher.setStyle("-fx-font-weight: bold; -fx-text-fill: #0969da;");
            } else {
                lblSelectedTeacher.setText("Aucun enseignant sélectionné.");
                lblSelectedTeacher.setStyle("-fx-font-style: italic; -fx-text-fill: #586069;");
            }
        });

        // 2. Durée du prêt
        Label lblDureeSec = new Label("2. Durée du prêt :");
        lblDureeSec.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        ComboBox<String> cbDuree = new ComboBox<>();
        cbDuree.getItems().addAll(
                "4 semaines (1 mois - recommandé)",
                "6 semaines (Période scolaire)",
                "8 semaines (Trimestre)",
                "2 semaines",
                "Date personnalisée…"
        );
        cbDuree.setValue("4 semaines (1 mois - recommandé)");

        DatePicker dpDatePerso = new DatePicker(LocalDate.now().plusWeeks(4));
        dpDatePerso.setPrefWidth(150);
        dpDatePerso.setVisible(false);
        dpDatePerso.setManaged(false);

        Label lblDateFinPrevue = new Label("📅 Retour prévu le : " + LocalDate.now().plusWeeks(4).format(DATE_FMT));
        lblDateFinPrevue.setStyle("-fx-font-weight: bold; -fx-text-fill: #b45309;");

        cbDuree.valueProperty().addListener((obs, oldV, val) -> {
            if ("Date personnalisée…".equals(val)) {
                dpDatePerso.setVisible(true);
                dpDatePerso.setManaged(true);
                if (dpDatePerso.getValue() != null) {
                    lblDateFinPrevue.setText("📅 Retour prévu le : " + dpDatePerso.getValue().format(DATE_FMT));
                }
            } else {
                dpDatePerso.setVisible(false);
                dpDatePerso.setManaged(false);
                LocalDate d = LocalDate.now();
                if (val != null) {
                    if (val.startsWith("4")) d = d.plusWeeks(4);
                    else if (val.startsWith("6")) d = d.plusWeeks(6);
                    else if (val.startsWith("8")) d = d.plusWeeks(8);
                    else if (val.startsWith("2")) d = d.plusWeeks(2);
                }
                lblDateFinPrevue.setText("📅 Retour prévu le : " + d.format(DATE_FMT));
            }
        });

        dpDatePerso.valueProperty().addListener((obs, oldV, val) -> {
            if (val != null) {
                lblDateFinPrevue.setText("📅 Retour prévu le : " + val.format(DATE_FMT));
            }
        });

        HBox dureeRow = new HBox(12, cbDuree, dpDatePerso, lblDateFinPrevue);
        dureeRow.setAlignment(Pos.CENTER_LEFT);

        // 3. Scan continu à la douchette
        Label lblLivresSec = new Label("3. Scan des livres du panier (Douchette / Code-barres) :");
        lblLivresSec.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField tfScan = new TextField();
        tfScan.setPromptText("🎯 Scannez le code-barres / N° d'exemplaire OU tapez le nom du livre…");
        HBox.setHgrow(tfScan, Priority.ALWAYS);

        Button btnAddScan = new Button("➕ Ajouter");
        HBox scanRow = new HBox(8, tfScan, btnAddScan);
        scanRow.setAlignment(Pos.CENTER_LEFT);

        Label lblScanFeedback = new Label("Scannez successivement les livres à prêter.");
        lblScanFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #586069;");

        ObservableList<PanierLivreItem> cartItems = FXCollections.observableArrayList();
        TableView<PanierLivreItem> cartTable = new TableView<>(cartItems);
        cartTable.setPrefHeight(160);
        VBox.setVgrow(cartTable, Priority.ALWAYS);

        TableColumn<PanierLivreItem, String> colExId = new TableColumn<>("N° Ex.");
        colExId.setPrefWidth(65);
        colExId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getExemplaireId()));

        TableColumn<PanierLivreItem, String> colTitre = new TableColumn<>("Titre");
        colTitre.setPrefWidth(240);
        colTitre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitre()));

        TableColumn<PanierLivreItem, String> colAuteur = new TableColumn<>("Auteur");
        colAuteur.setPrefWidth(140);
        colAuteur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAuteur()));

        TableColumn<PanierLivreItem, String> colRemarque = new TableColumn<>("Remarque");
        colRemarque.setPrefWidth(120);
        colRemarque.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRemarque()));

        TableColumn<PanierLivreItem, Void> colAction = new TableColumn<>("Action");
        colAction.setPrefWidth(75);
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("🗑️");
            {
                btnRemove.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
                btnRemove.setTooltip(new Tooltip("Retirer du panier"));
                btnRemove.setOnAction(e -> {
                    PanierLivreItem item = getTableView().getItems().get(getIndex());
                    cartItems.remove(item);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnRemove);
                setAlignment(Pos.CENTER);
            }
        });

        cartTable.getColumns().addAll(colExId, colTitre, colAuteur, colRemarque, colAction);
        cartTable.setPlaceholder(new Label("Aucun livre dans le panier. Scannez un livre ci-dessus."));

        Label lblCartCount = new Label("📚 0 livre(s) dans le panier");
        lblCartCount.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        Button btnPrintPreview = new Button("📄 Aperçu / Imprimer Fiche Panier");
        btnPrintPreview.setOnAction(e -> {
            if (cartItems.isEmpty()) {
                showWarning("Panier vide", "Ajoutez au moins un livre dans le panier avant d'imprimer la fiche.");
                return;
            }
            Utilisateur teacher = selectedTeacherProp.get();
            if (teacher == null) {
                teacher = new Utilisateur();
                teacher.setNom("ENSEIGNANT");
                teacher.setPrenom("");
                teacher.setNumero("P-01");
                teacher.setClasse("Classe");
            }
            LocalDate dateLimite = resolveDateLimite(cbDuree.getValue(), dpDatePerso.getValue());
            try {
                File tmpFile = File.createTempFile("fiche_panier_", ".html");
                BilanExportService.genererFichePanierHtml(teacher, LocalDate.now(), dateLimite, cartItems, tmpFile);
                openInBrowser(tmpFile);
                showInfo("Fiche ouverte", "La fiche d'emprunt a été ouverte dans votre navigateur web.");
            } catch (IOException ex) {
                showError("Erreur d'impression", "Impossible de générer la fiche : " + ex.getMessage());
            }
        });

        HBox cartBottomRow = new HBox(12, lblCartCount, new Region(), btnPrintPreview);
        HBox.setHgrow(cartBottomRow.getChildren().get(1), Priority.ALWAYS);
        cartBottomRow.setAlignment(Pos.CENTER_LEFT);

        cartItems.addListener((javafx.collections.ListChangeListener<PanierLivreItem>) c -> {
            lblCartCount.setText(String.format("📚 %d livre(s) dans le panier", cartItems.size()));
        });

        // Mécanisme d'ajout par scan
        Runnable doAddScan = () -> {
            String raw = tfScan.getText().trim();
            if (raw.isEmpty()) return;

            try {
                // Tentative 1 : est-ce un ID d'exemplaire numérique ?
                int exId = -1;
                try {
                    if (raw.matches("^\\d{1,6}$")) {
                        exId = Integer.parseInt(raw);
                    }
                } catch (NumberFormatException ignored) {}

                Exemplaire foundEx = null;
                if (exId > 0) {
                    foundEx = exemplaireDao.findById(exId);
                }

                // Tentative 2 : si pas trouvé par ID direct ou si le scan ressemble à un ISBN
                if (foundEx == null) {
                    String cleanIsbn = LivreMetadataService.cleanIsbn(raw);
                    List<Exemplaire> dispos = cleanIsbn.isEmpty() ? List.of() : exemplaireDao.findDisponiblesByIsbn(cleanIsbn);
                    // Prendre le premier non encore dans le panier
                    for (Exemplaire candidate : dispos) {
                        boolean alreadyInCart = false;
                        for (PanierLivreItem item : cartItems) {
                            if (item.getExemplaireId() == candidate.getId()) {
                                alreadyInCart = true;
                                break;
                            }
                        }
                        if (!alreadyInCart) {
                            foundEx = candidate;
                            break;
                        }
                    }
                }

                // Tentative 3 : si pas trouvé par ID ni ISBN, chercher par le NOM / TITRE du livre !
                if (foundEx == null) {
                    List<Exemplaire> dispos = exemplaireDao.findDisponiblesByTitre(raw);
                    for (Exemplaire candidate : dispos) {
                        boolean alreadyInCart = false;
                        for (PanierLivreItem item : cartItems) {
                            if (item.getExemplaireId() == candidate.getId()) {
                                alreadyInCart = true;
                                break;
                            }
                        }
                        if (!alreadyInCart) {
                            foundEx = candidate;
                            break;
                        }
                    }
                }

                if (foundEx == null) {
                    SoundService.playError();
                    AnimationHelper.flashError(tfScan);
                    lblScanFeedback.setText("⚠️ Aucun exemplaire disponible trouvé pour : " + raw);
                    lblScanFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    tfScan.selectAll();
                    return;
                }

                // Vérifier si déjà dans le panier
                for (PanierLivreItem item : cartItems) {
                    if (item.getExemplaireId() == foundEx.getId()) {
                        SoundService.playError();
                        AnimationHelper.flashError(tfScan);
                        lblScanFeedback.setText("⚠️ L'exemplaire #" + foundEx.getId() + " est déjà dans le panier.");
                        lblScanFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                        tfScan.selectAll();
                        return;
                    }
                }

                // Vérifier statut disponible
                if (foundEx.getStatut() != Exemplaire.Statut.DISPONIBLE) {
                    SoundService.playError();
                    AnimationHelper.flashError(tfScan);
                    lblScanFeedback.setText("⚠️ L'exemplaire #" + foundEx.getId() + " n'est pas disponible (actuellement " + foundEx.getStatut() + ").");
                    lblScanFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                    tfScan.selectAll();
                    return;
                }

                Livre livre = livreDao.findByIsbn(foundEx.getIsbn());
                String titre = livre != null ? livre.getTitre() : "Livre inconnu";
                String auteur = livre != null ? livre.getAuteur() : "";

                cartItems.add(new PanierLivreItem(foundEx.getId(), foundEx.getIsbn(), titre, auteur, foundEx.getRemarque()));
                SoundService.playSuccess();
                AnimationHelper.flashSuccess(tfScan);
                lblScanFeedback.setText("✅ Ajouté : #" + foundEx.getId() + " — " + titre);
                lblScanFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #16a34a; -fx-font-weight: bold;");
                tfScan.clear();
                tfScan.requestFocus();

            } catch (SQLException ex) {
                SoundService.playError();
                AnimationHelper.flashError(tfScan);
                lblScanFeedback.setText("❌ Erreur SQL : " + ex.getMessage());
                lblScanFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626;");
            }
        };

        tfScan.setOnAction(e -> doAddScan.run());
        btnAddScan.setOnAction(e -> doAddScan.run());

        content.getChildren().addAll(
                lblTeacherSec,
                teacherFilterRow,
                lvTeachers,
                lblSelectedTeacher,
                new Separator(),
                lblDureeSec,
                dureeRow,
                new Separator(),
                lblLivresSec,
                scanRow,
                lblScanFeedback,
                cartTable,
                cartBottomRow
        );

        dialog.getDialogPane().setContent(content);

        Node validerBtn = dialog.getDialogPane().lookupButton(btnValiderType);
        validerBtn.disableProperty().bind(
                selectedTeacherProp.isNull().or(javafx.beans.binding.Bindings.isEmpty(cartItems))
        );

        Platform.runLater(() -> tfScan.requestFocus());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == btnValiderType) {
            Utilisateur teacher = selectedTeacherProp.get();
            LocalDate dateLimite = resolveDateLimite(cbDuree.getValue(), dpDatePerso.getValue());

            List<Integer> ids = cartItems.stream().map(PanierLivreItem::getExemplaireId).toList();
            try {
                List<Emprunt> created = empruntService.emprunterPanier(teacher, ids, dateLimite);
                loadData();

                Alert conf = new Alert(Alert.AlertType.CONFIRMATION);
                conf.setTitle("Prêt validé");
                conf.setHeaderText("✅ Le panier de " + created.size() + " livre(s) a été validé pour "
                        + teacher.getPrenom() + " " + teacher.getNom() + " !");
                conf.setContentText("Date limite fixée au " + dateLimite.format(DATE_FMT) + ".\n\n"
                        + "Souhaitez-vous ouvrir la fiche récapitulative imprimable (à glisser dans le bac) ?");

                ButtonType btnImprimer = new ButtonType("🖨️ Imprimer la fiche", ButtonBar.ButtonData.YES);
                ButtonType btnFermer = new ButtonType("Fermer", ButtonBar.ButtonData.NO);
                conf.getButtonTypes().setAll(btnImprimer, btnFermer);

                Optional<ButtonType> rep = conf.showAndWait();
                if (rep.isPresent() && rep.get() == btnImprimer) {
                    File tmp = File.createTempFile("fiche_panier_" + teacher.getNom() + "_", ".html");
                    BilanExportService.genererFichePanierHtml(teacher, LocalDate.now(), dateLimite, cartItems, tmp);
                    openInBrowser(tmp);
                    showInfo("Fiche ouverte", "La fiche d'emprunt a été ouverte dans votre navigateur web.");
                }

            } catch (RegleMetierException | SQLException | IOException e) {
                showError("Erreur lors de la validation du panier", e.getMessage());
            }
        }
    }

    private LocalDate resolveDateLimite(String dureeChoice, LocalDate customDate) {
        if ("Date personnalisée…".equals(dureeChoice) && customDate != null) {
            return customDate;
        }
        LocalDate now = LocalDate.now();
        if (dureeChoice != null) {
            if (dureeChoice.startsWith("4")) return now.plusWeeks(4);
            if (dureeChoice.startsWith("6")) return now.plusWeeks(6);
            if (dureeChoice.startsWith("8")) return now.plusWeeks(8);
            if (dureeChoice.startsWith("2")) return now.plusWeeks(2);
        }
        return now.plusWeeks(4);
    }

    // =====================================================================
    //  FEATURE 3 : Bilan d'Activité & Palmarès BCD (Direction & Conseil)
    // =====================================================================

    @FXML
    private void handleBilanActivite() {
        BilanStatistiques bilan;
        try {
            bilan = empruntService.getBilanStatistiques();
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les statistiques : " + e.getMessage());
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("📊 Bilan d'Activité & Palmarès BCD");
        dialog.setHeaderText("Synthèse des prêts, palmarès des livres, classes et élèves pour le Conseil d'école.");
        ButtonType btnFermer = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(btnFermer);

        VBox content = new VBox(14);
        content.setPrefWidth(820);
        content.setPrefHeight(600);
        content.setPadding(new Insets(10));

        // ── Cartes KPI ──────────────────────────────────────────────────────
        HBox kpiBox = new HBox(12);
        kpiBox.setAlignment(Pos.CENTER);

        VBox cardTotal = createKpiCard("📚 Total Prêts", String.valueOf(bilan.getTotalPretsHistorique()), "#0284c7");
        VBox cardEnCours = createKpiCard("📖 En cours", String.valueOf(bilan.getPretsEnCours()), "#059669");
        VBox cardRetard = createKpiCard("⚠️ Retards", String.valueOf(bilan.getPretsEnRetard()), bilan.getPretsEnRetard() > 0 ? "#dc2626" : "#64748b");
        VBox cardTaux = createKpiCard("🎒 Élèves lecteurs", String.format("%.1f%%", bilan.getTauxElevesActifs()), "#7c3aed");
        VBox cardLivres = createKpiCard("🏷️ Titres lus", String.valueOf(bilan.getLivresDifferentsEmpruntes()), "#d97706");

        HBox.setHgrow(cardTotal, Priority.ALWAYS);
        HBox.setHgrow(cardEnCours, Priority.ALWAYS);
        HBox.setHgrow(cardRetard, Priority.ALWAYS);
        HBox.setHgrow(cardTaux, Priority.ALWAYS);
        HBox.setHgrow(cardLivres, Priority.ALWAYS);
        kpiBox.getChildren().addAll(cardTotal, cardEnCours, cardRetard, cardTaux, cardLivres);

        // ── TabPane des palmarès ─────────────────────────────────────────────
        TabPane tabPane = new TabPane();
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        // Tab 1 : Top Livres
        Tab tabLivres = new Tab("🏆 Top Livres");
        tabLivres.setClosable(false);
        TableView<BilanStatistiques.TopLivre> tvLivres = new TableView<>(FXCollections.observableArrayList(bilan.getTopLivres()));

        TableColumn<BilanStatistiques.TopLivre, String> colRangL = new TableColumn<>("Rang");
        colRangL.setPrefWidth(65);
        colRangL.setCellValueFactory(c -> {
            int r = c.getValue().getRang();
            String prefix = r == 1 ? "🥇 1" : (r == 2 ? "🥈 2" : (r == 3 ? "🥉 3" : String.valueOf(r)));
            return new SimpleStringProperty(prefix);
        });

        TableColumn<BilanStatistiques.TopLivre, String> colTitreL = new TableColumn<>("Titre");
        colTitreL.setPrefWidth(280);
        colTitreL.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitre()));

        TableColumn<BilanStatistiques.TopLivre, String> colAuteurL = new TableColumn<>("Auteur");
        colAuteurL.setPrefWidth(160);
        colAuteurL.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAuteur()));

        TableColumn<BilanStatistiques.TopLivre, String> colIsbnL = new TableColumn<>("ISBN");
        colIsbnL.setPrefWidth(130);
        colIsbnL.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));

        TableColumn<BilanStatistiques.TopLivre, String> colPretsL = new TableColumn<>("Prêts");
        colPretsL.setPrefWidth(90);
        colPretsL.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNbPrets() + " prêts"));

        tvLivres.getColumns().addAll(colRangL, colTitreL, colAuteurL, colIsbnL, colPretsL);
        tvLivres.setPlaceholder(new Label("Aucun emprunt enregistré pour le moment."));
        tabLivres.setContent(tvLivres);

        // Tab 2 : Palmarès Classes
        Tab tabClasses = new Tab("🏫 Palmarès des Classes");
        tabClasses.setClosable(false);
        TableView<BilanStatistiques.ClasseStat> tvClasses = new TableView<>(FXCollections.observableArrayList(bilan.getStatsParClasse()));

        TableColumn<BilanStatistiques.ClasseStat, String> colRangC = new TableColumn<>("Rang");
        colRangC.setPrefWidth(65);
        colRangC.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getRang())));

        TableColumn<BilanStatistiques.ClasseStat, String> colClasseC = new TableColumn<>("Classe");
        colClasseC.setPrefWidth(160);
        colClasseC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClasse()));

        TableColumn<BilanStatistiques.ClasseStat, String> colPretsC = new TableColumn<>("Total Prêts");
        colPretsC.setPrefWidth(130);
        colPretsC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNbPrets() + " prêts"));

        TableColumn<BilanStatistiques.ClasseStat, String> colElevesC = new TableColumn<>("Élèves emprunteurs");
        colElevesC.setPrefWidth(160);
        colElevesC.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNbEmprunteurs() + " élèves"));

        TableColumn<BilanStatistiques.ClasseStat, String> colMoyC = new TableColumn<>("Moyenne");
        colMoyC.setPrefWidth(140);
        colMoyC.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f livres/élève", c.getValue().getMoyennePretsParEmprunteur())));

        tvClasses.getColumns().addAll(colRangC, colClasseC, colPretsC, colElevesC, colMoyC);
        tvClasses.setPlaceholder(new Label("Aucune donnée de classe enregistrée."));
        tabClasses.setContent(tvClasses);

        // Tab 3 : Top Lecteurs
        Tab tabLecteurs = new Tab("⭐ Top Lecteurs Élèves");
        tabLecteurs.setClosable(false);
        TableView<BilanStatistiques.TopLecteur> tvLecteurs = new TableView<>(FXCollections.observableArrayList(bilan.getTopLecteurs()));

        TableColumn<BilanStatistiques.TopLecteur, String> colRangU = new TableColumn<>("Rang");
        colRangU.setPrefWidth(65);
        colRangU.setCellValueFactory(c -> {
            int r = c.getValue().getRang();
            String prefix = r == 1 ? "🥇 1" : (r == 2 ? "🥈 2" : (r == 3 ? "🥉 3" : String.valueOf(r)));
            return new SimpleStringProperty(prefix);
        });

        TableColumn<BilanStatistiques.TopLecteur, String> colNomU = new TableColumn<>("Élève");
        colNomU.setPrefWidth(240);
        colNomU.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNomComplet()));

        TableColumn<BilanStatistiques.TopLecteur, String> colClasseU = new TableColumn<>("Classe");
        colClasseU.setPrefWidth(150);
        colClasseU.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClasse()));

        TableColumn<BilanStatistiques.TopLecteur, String> colPretsU = new TableColumn<>("Livres empruntés");
        colPretsU.setPrefWidth(140);
        colPretsU.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNbPrets() + " livres"));

        tvLecteurs.getColumns().addAll(colRangU, colNomU, colClasseU, colPretsU);
        tvLecteurs.setPlaceholder(new Label("Aucun emprunteur élève enregistré."));
        tabLecteurs.setContent(tvLecteurs);

        tabPane.getTabs().addAll(tabLivres, tabClasses, tabLecteurs);

        // ── Barre de boutons d'export ───────────────────────────────────────
        Button btnExportCsv = new Button("📤 Exporter Bilan complet (CSV Excel)");
        btnExportCsv.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer le bilan statistique");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier CSV (*.csv)", "*.csv"));
            fc.setInitialFileName("bilan_bcd_" + LocalDate.now() + ".csv");
            File file = fc.showSaveDialog(dialog.getOwner());
            if (file != null) {
                try {
                    BilanExportService.exporterBilanCsv(bilan, file);
                    showInfo("Export réussi", "Le bilan a été exporté vers :\n" + file.getAbsolutePath());
                } catch (IOException ex) {
                    showError("Erreur d'export", "Impossible d'exporter le fichier : " + ex.getMessage());
                }
            }
        });

        Button btnPrintHtml = new Button("🖨️ Fiche Bilan imprimable (HTML / Navigateur)");
        btnPrintHtml.setStyle("-fx-font-weight: bold;");
        btnPrintHtml.setOnAction(e -> {
            try {
                File tmpFile = File.createTempFile("rapport_bilan_bcd_", ".html");
                BilanExportService.genererBilanHtml(bilan, tmpFile);
                openInBrowser(tmpFile);
                showInfo("Bilan annuel", "Le bilan imprimable a été ouvert dans votre navigateur web.");
            } catch (IOException ex) {
                showError("Erreur", "Impossible de générer la fiche : " + ex.getMessage());
            }
        });

        HBox actionsRow = new HBox(12, btnExportCsv, btnPrintHtml);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        content.getChildren().addAll(kpiBox, tabPane, actionsRow);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait();
    }

    private VBox createKpiCard(String label, String value, String colorHex) {
        Label lblT = new Label(label);
        lblT.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-weight: bold;");

        Label lblV = new Label(value);
        lblV.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + colorHex + ";");

        VBox box = new VBox(2, lblT, lblV);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(8, 12, 8, 12));
        box.getStyleClass().add("kpi-mini-card");
        return box;
    }

    private void openInBrowser(File file) {
        DialogHelper.openInBrowser(file);
    }
}
