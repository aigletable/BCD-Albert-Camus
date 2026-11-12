package fr.ecole.bcd.controller;
 
import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.EmpruntDao;
import fr.ecole.bcd.database.UtilisateurDao;
import fr.ecole.bcd.model.Emprunt;
import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.model.Utilisateur.TypeCompte;

import fr.ecole.bcd.service.CsvImportService;
import fr.ecole.bcd.service.CsvImportService.ImportResult;
import fr.ecole.bcd.service.PassageAnneeService;
import fr.ecole.bcd.service.PassageAnneeService.OndeImportReport;
import fr.ecole.bcd.service.PassageAnneeService.PassageAnneeReport;
import fr.ecole.bcd.service.PassageAnneeService.PromotionItem;
import fr.ecole.bcd.service.BackupService;
import fr.ecole.bcd.service.SoundService;
import fr.ecole.bcd.view.DialogHelper;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Contrôleur de l'onglet Utilisateurs.
 * <p>
 * Gère l'affichage, la recherche, le filtrage et les opérations CRUD
 * sur les utilisateurs (élèves et enseignants) avec fiche détaillée et historique.
 * </p>
 */
public class UtilisateursController {

    // ── Composants FXML — Actions & Filtres ──────────────────────────────
    @FXML private TextField searchField;
    @FXML private ComboBox<String> classeFilter;
    @FXML private Button btnAdd;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnImportCsv;
    @FXML private Button btnPassageAnnee;

    // ── Composants FXML — Cartes KPI ────────────────────────────────────
    @FXML private Label lblKpiEleves;
    @FXML private Label lblKpiEnseignants;
    @FXML private Label lblKpiClasses;

    // ── Composants FXML — Tableau ───────────────────────────────────────
    @FXML private TableView<Utilisateur> tableView;
    @FXML private TableColumn<Utilisateur, String> colNumero;
    @FXML private TableColumn<Utilisateur, String> colNom;
    @FXML private TableColumn<Utilisateur, String> colPrenom;
    @FXML private TableColumn<Utilisateur, String> colType;
    @FXML private TableColumn<Utilisateur, String> colClasse;
    @FXML private Label statusLabel;

    // ── Composants FXML — Fiche Utilisateur (Droite) ────────────────────
    @FXML private StackPane userDetailContainer;
    @FXML private VBox userDetailCard;
    @FXML private VBox emptyUserDetailBox;
    @FXML private Label userDetailAvatar;
    @FXML private Label userDetailName;
    @FXML private Label userDetailSub;
    @FXML private Label userDetailNum;
    @FXML private Label userDetailClass;
    @FXML private Label userDetailType;
    @FXML private Label userDetailLoans;
    @FXML private VBox userDetailRecentLoans;

    // ── Données & DAOs ──────────────────────────────────────────────────
    private final UtilisateurDao dao = new UtilisateurDao();
    private final EmpruntDao empruntDao = new EmpruntDao();
    private final PassageAnneeService passageAnneeService = new PassageAnneeService();
    private final BackupService backupService = new BackupService();
    private final ObservableList<Utilisateur> masterData = FXCollections.observableArrayList();
    private FilteredList<Utilisateur> filteredData;

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @FXML
    public void initialize() {
        // Colonne Numéro
        colNumero.setCellValueFactory(new PropertyValueFactory<>("numero"));
        colNumero.setStyle("-fx-font-family: monospace; -fx-font-weight: bold; -fx-alignment: CENTER; -fx-text-fill: #64748B;");

        // Colonne Nom avec avatar initiales et prénom
        colNom.setCellValueFactory(new PropertyValueFactory<>("nom"));
        colNom.setCellFactory(col -> new TableCell<>() {
            private final Label avatar = new Label();
            private final Label nameLabel = new Label();
            private final HBox box = new HBox(10, avatar, nameLabel);
            {
                avatar.getStyleClass().add("user-avatar-badge");
                nameLabel.getStyleClass().add("user-name-label");
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String nom, boolean empty) {
                super.updateItem(nom, empty);
                if (empty || nom == null) {
                    setGraphic(null);
                } else {
                    Utilisateur u = getTableRow() != null ? getTableRow().getItem() : null;
                    String p = u != null && u.getPrenom() != null ? u.getPrenom() : "";
                    String init = (p.length() > 0 ? p.substring(0, 1) : "") + (nom.length() > 0 ? nom.substring(0, 1) : "");
                    avatar.setText(init.toUpperCase());
                    nameLabel.setText(p + " " + nom.toUpperCase());
                    setGraphic(box);
                }
            }
        });

        if (colPrenom != null) {
            colPrenom.setCellValueFactory(new PropertyValueFactory<>("prenom"));
        }

        colType.setCellValueFactory(cellData -> {
            TypeCompte type = cellData.getValue().getTypeCompte();
            String label = type == TypeCompte.ELEVE ? "👧 Élève" : "👩‍🏫 Enseignant";
            return new SimpleStringProperty(label);
        });
        colType.setStyle("-fx-alignment: CENTER; -fx-text-fill: #475569;");

        colClasse.setCellValueFactory(new PropertyValueFactory<>("classe"));
        colClasse.setStyle("-fx-alignment: CENTER; -fx-font-weight: 600; -fx-text-fill: #334155;");

        // Sélection dans le tableau
        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showUserDetail(newVal);
        });

        // Charger les données
        loadData();

        // Filtrage en temps réel
        filteredData = new FilteredList<>(masterData, p -> true);
        tableView.setItems(filteredData);

        // Listeners filtres
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        classeFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    // =====================================================================
    //  Chargement des données
    // =====================================================================

    public void loadData() {
        try {
            List<Utilisateur> all = dao.findAll();
            masterData.setAll(all);

            // Charger les classes dans le ComboBox
            List<String> classes = dao.findAllClasses();
            classeFilter.getItems().clear();
            classeFilter.getItems().add(null); // Option "Toutes les classes"
            classeFilter.getItems().addAll(classes);

            // KPIs
            long eleves = all.stream().filter(u -> u.getTypeCompte() == TypeCompte.ELEVE).count();
            long enseignants = all.stream().filter(u -> u.getTypeCompte() == TypeCompte.ENSEIGNANT).count();
            int nbClasses = classes.size();

            if (lblKpiEleves != null) lblKpiEleves.setText(String.valueOf(eleves));
            if (lblKpiEnseignants != null) lblKpiEnseignants.setText(String.valueOf(enseignants));
            if (lblKpiClasses != null) lblKpiClasses.setText(String.valueOf(nbClasses));

            updateStatus(all.size() + " utilisateur(s) chargé(s) (" + eleves + " élèves, " + enseignants + " enseignants).");

            // Sélectionner le premier utilisateur par défaut
            if (!all.isEmpty() && tableView.getSelectionModel().getSelectedItem() == null) {
                tableView.getSelectionModel().selectFirst();
            }

        } catch (SQLException e) {
            showError("Erreur de chargement", e.getMessage());
        }
    }

    private void showUserDetail(Utilisateur user) {
        if (user == null) {
            if (emptyUserDetailBox != null) emptyUserDetailBox.setVisible(true);
            if (userDetailCard != null) userDetailCard.setVisible(false);
            if (btnEdit != null) btnEdit.setDisable(true);
            if (btnDelete != null) btnDelete.setDisable(true);
            return;
        }

        if (emptyUserDetailBox != null) emptyUserDetailBox.setVisible(false);
        if (userDetailCard != null) userDetailCard.setVisible(true);
        if (btnEdit != null) btnEdit.setDisable(false);
        if (btnDelete != null) btnDelete.setDisable(false);

        String p = user.getPrenom() != null ? user.getPrenom() : "";
        String n = user.getNom() != null ? user.getNom() : "";
        String init = (p.length() > 0 ? p.substring(0, 1) : "") + (n.length() > 0 ? n.substring(0, 1) : "");

        if (userDetailAvatar != null) userDetailAvatar.setText(init.toUpperCase());
        if (userDetailName != null) userDetailName.setText(p + " " + n.toUpperCase());
        if (userDetailSub != null) userDetailSub.setText(user.getClasse() + " • Carte n° " + user.getNumero());
        if (userDetailNum != null) userDetailNum.setText(user.getNumero());
        if (userDetailClass != null) userDetailClass.setText(user.getClasse());
        if (userDetailType != null) {
            userDetailType.setText(user.getTypeCompte() == TypeCompte.ELEVE ? "👧 Élève" : "👩‍🏫 Enseignant");
        }

        // Emprunts en cours
        try {
            List<Emprunt> loans = empruntDao.findEnCoursByUtilisateur(user.getNumero());
            if (userDetailLoans != null) userDetailLoans.setText(String.valueOf(loans.size()));

            if (userDetailRecentLoans != null) {
                userDetailRecentLoans.getChildren().clear();
                if (loans.isEmpty()) {
                    Label none = new Label("Aucun emprunt en cours.");
                    none.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11.5px; -fx-font-style: italic;");
                    userDetailRecentLoans.getChildren().add(none);
                } else {
                    for (Emprunt emp : loans) {
                        HBox item = new HBox(8);
                        item.setAlignment(Pos.CENTER_LEFT);
                        item.getStyleClass().add("user-recent-loan-item");

                        Label bookIcon = new Label("📖");
                        bookIcon.getStyleClass().add("user-recent-loan-icon");

                        VBox info = new VBox(1);
                        Label title = new Label(emp.getLivreTitre() != null ? emp.getLivreTitre() : "Exemplaire #" + emp.getExemplaireId());
                        title.getStyleClass().add("user-recent-loan-title");
                        Label meta = new Label("Exemplaire #" + emp.getExemplaireId() + (emp.getDateEmprunt() != null ? " • Prêté le " + emp.getDateEmprunt() : ""));
                        meta.getStyleClass().add("user-recent-loan-meta");

                        info.getChildren().addAll(title, meta);
                        item.getChildren().addAll(bookIcon, info);
                        userDetailRecentLoans.getChildren().add(item);
                    }
                }
            }
        } catch (Exception e) {
            if (userDetailLoans != null) userDetailLoans.setText("–");
        }
    }

    /** Recharge les données depuis la base et réapplique les filtres. */
    public void refresh() {
        loadData();
        applyFilters();
    }

    // =====================================================================
    //  Filtrage
    // =====================================================================

    private void applyFilters() {
        String searchText = searchField.getText();
        String selectedClasse = classeFilter.getValue();

        filteredData.setPredicate(user -> {
            // Filtre par classe
            if (selectedClasse != null && !selectedClasse.isEmpty()) {
                if (!user.getClasse().equals(selectedClasse)) {
                    return false;
                }
            }

            // Filtre par texte de recherche
            if (searchText != null && !searchText.isBlank()) {
                String lower = searchText.toLowerCase();
                return user.getNom().toLowerCase().contains(lower)
                    || user.getPrenom().toLowerCase().contains(lower)
                    || user.getNumero().toLowerCase().contains(lower);
            }

            return true;
        });

        updateStatus(filteredData.size() + " utilisateur(s) affiché(s).");
    }

    // =====================================================================
    //  Actions CRUD
    // =====================================================================

    @FXML
    private void handleAdd() {
        Optional<Utilisateur> result = showUserDialog(null);
        result.ifPresent(user -> {
            try {
                dao.insert(user);
                refresh();
                updateStatus("✅ " + user.getPrenom() + " " + user.getNom() + " ajouté(e).");
            } catch (SQLException e) {
                showError("Erreur d'ajout",
                        "Impossible d'ajouter l'utilisateur.\n" + e.getMessage());
            }
        });
    }

    @FXML
    private void handleEdit() {
        Utilisateur selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un utilisateur à modifier.");
            return;
        }

        Optional<Utilisateur> result = showUserDialog(selected);
        result.ifPresent(user -> {
            try {
                dao.update(user);
                refresh();
                updateStatus("✅ " + user.getPrenom() + " " + user.getNom() + " modifié(e).");
            } catch (SQLException e) {
                showError("Erreur de modification",
                        "Impossible de modifier l'utilisateur.\n" + e.getMessage());
            }
        });
    }

    @FXML
    private void handleDelete() {
        Utilisateur selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Aucune sélection", "Veuillez sélectionner un utilisateur à supprimer.");
            return;
        }

        // Demander confirmation
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(confirm);
        confirm.setTitle("Confirmer la suppression");
        confirm.setHeaderText("Supprimer " + selected.getPrenom() + " " + selected.getNom() + " ?");
        confirm.setContentText("Numéro : " + selected.getNumero() + "\n"
                + "Cette action est irréversible.\n\n"
                + "⚠️ La suppression échouera si l'utilisateur a des emprunts.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                boolean deleted = dao.delete(selected.getNumero());
                if (deleted) {
                    refresh();
                    updateStatus("🗑️ " + selected.getPrenom() + " " + selected.getNom() + " supprimé(e).");
                } else {
                    showWarning("Échec", "Aucun utilisateur supprimé (introuvable).");
                }
            } catch (SQLException e) {
                showError("Erreur de suppression",
                        "Impossible de supprimer l'utilisateur.\n"
                        + "Il a probablement des emprunts en cours ou dans l'historique.\n\n"
                        + e.getMessage());
            }
        }
    }

    @FXML
    private void handleImportCsv() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Importer des élèves / utilisateurs depuis un fichier CSV");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichiers CSV (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );

        File file = fileChooser.showOpenDialog(tableView.getScene().getWindow());
        if (file == null) {
            return;
        }

        CsvImportService importService = new CsvImportService();
        try {
            ImportResult result = importService.importFromFile(file);

            // Recharger les données du tableau et la liste des classes
            loadData();

            if (result.getErrors() == 0) {
                showInfo("Import CSV réussi",
                        result.getImported() + " utilisateur(s) importé(s) avec succès !");
            } else if (result.getImported() > 0) {
                showWarning("Import CSV terminé avec avertissements",
                        result.getSummary());
            } else {
                showError("Échec de l'import CSV",
                        result.getSummary());
            }

            updateStatus(result.getImported() + " utilisateur(s) importé(s) via CSV.");

        } catch (IOException e) {
            showError("Erreur de lecture du fichier CSV", e.getMessage());
        } catch (Exception e) {
            showError("Erreur lors de l'import CSV", e.getMessage());
        }
    }

    // =====================================================================
    //  Rentrée scolaire & Import ONDE
    // =====================================================================

    @FXML
    private void handleOpenPassageAnneeDialog() {
        Dialog<Void> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Rentrée scolaire — Passage des classes & Import ONDE");
        dialog.setHeaderText("Gestion de la rentrée scolaire pour les élèves et les classes");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setPrefWidth(740);
        tabPane.setPrefHeight(520);

        // ─────────────────────────────────────────────────────────────────
        // Onglet 1 : Promotion automatique des classes
        // ─────────────────────────────────────────────────────────────────
        Tab tabPromo = new Tab("🎓 Promotion automatique des classes");
        VBox promoContent = new VBox(12);
        promoContent.setPadding(new Insets(14));

        // Info box
        VBox promoInfo = new VBox(6);
        promoInfo.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12px; -fx-background-radius: 8px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px;");
        Label lblCycle = new Label("TPS ➔ PS ➔ MS ➔ GS ➔ CP ➔ CE1 ➔ CE2 ➔ CM1 ➔ CM2 ➔ Collège (Sortant)");
        lblCycle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: -color-primary;");
        Label lblInfoDesc = new Label("Fait monter automatiquement tous les élèves inscrits au niveau supérieur.\n" +
                "🛡️ Une sauvegarde automatique de sécurité est systématiquement créée avant d'appliquer les modifications.");
        lblInfoDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -color-fg-muted;");
        promoInfo.getChildren().addAll(lblCycle, lblInfoDesc);

        // Simulation Table & KPIs
        PassageAnneeReport report;
        try {
            report = passageAnneeService.simulerPassage();
        } catch (SQLException ex) {
            showError("Erreur lors de la simulation", ex.getMessage());
            return;
        }

        HBox kpiRow = new HBox(12);
        kpiRow.setAlignment(Pos.CENTER_LEFT);
        Label kpiEleves = new Label("👥 Élèves concernés : " + report.getTotalEleves());
        kpiEleves.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label kpiPromus = new Label("✅ Promus : " + report.getTotalPromus());
        kpiPromus.setStyle("-fx-text-fill: #16A34A; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label kpiSortants = new Label("🎓 Sortants : " + report.getTotalSortants());
        kpiSortants.setStyle("-fx-text-fill: #64748B; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label kpiPrets = new Label("⚠️ Prêts en cours : " + report.getTotalAvecEmpruntsEnCours());
        if (report.getTotalAvecEmpruntsEnCours() > 0) {
            kpiPrets.setStyle("-fx-text-fill: #EA580C; -fx-font-weight: bold; -fx-font-size: 12px;");
        } else {
            kpiPrets.setStyle("-fx-text-fill: #16A34A; -fx-font-size: 12px;");
            kpiPrets.setText("✅ Aucun livre non rendu");
        }
        kpiRow.getChildren().addAll(kpiEleves, new Separator(javafx.geometry.Orientation.VERTICAL), kpiPromus, new Separator(javafx.geometry.Orientation.VERTICAL), kpiSortants, new Separator(javafx.geometry.Orientation.VERTICAL), kpiPrets);

        TableView<PromotionItem> tablePromo = new TableView<>(FXCollections.observableArrayList(report.getPromotions()));
        tablePromo.setPrefHeight(260);
        VBox.setVgrow(tablePromo, Priority.ALWAYS);

        TableColumn<PromotionItem, String> colEleve = new TableColumn<>("Élève");
        colEleve.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEleve().getPrenom() + " " + c.getValue().getEleve().getNom().toUpperCase()));
        colEleve.setPrefWidth(180);

        TableColumn<PromotionItem, String> colAnc = new TableColumn<>("Classe actuelle");
        colAnc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAncienneClasse()));
        colAnc.setPrefWidth(120);
        colAnc.setStyle("-fx-alignment: CENTER;");

        TableColumn<PromotionItem, String> colFleche = new TableColumn<>("");
        colFleche.setCellValueFactory(c -> new SimpleStringProperty("➔"));
        colFleche.setPrefWidth(30);
        colFleche.setStyle("-fx-alignment: CENTER; -fx-text-fill: -color-primary; -fx-font-weight: bold;");

        TableColumn<PromotionItem, String> colNouv = new TableColumn<>("Nouvelle classe");
        colNouv.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNouvelleClasse()));
        colNouv.setPrefWidth(130);
        colNouv.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<PromotionItem, String> colStatut = new TableColumn<>("Statut");
        colStatut.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isEstSortant() ? "🎓 Sortant" : "✅ Promu"));
        colStatut.setPrefWidth(90);
        colStatut.setStyle("-fx-alignment: CENTER;");

        TableColumn<PromotionItem, String> colPretsWarn = new TableColumn<>("Emprunts BCD");
        colPretsWarn.setCellValueFactory(c -> {
            if (c.getValue().isAEmpruntsEnCours()) {
                return new SimpleStringProperty("⚠️ " + c.getValue().getLivresEnCours().size() + " livre(s)");
            }
            return new SimpleStringProperty("✅ À jour");
        });
        colPretsWarn.setPrefWidth(120);
        colPretsWarn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.startsWith("⚠️")) {
                        setStyle("-fx-text-fill: #EA580C; -fx-font-weight: bold; -fx-alignment: CENTER;");
                        PromotionItem pi = getTableRow() != null ? getTableRow().getItem() : null;
                        if (pi != null && !pi.getLivresEnCours().isEmpty()) {
                            setTooltip(new Tooltip(String.join("\n", pi.getLivresEnCours())));
                        }
                    } else {
                        setStyle("-fx-text-fill: #16A34A; -fx-alignment: CENTER;");
                        setTooltip(null);
                    }
                }
            }
        });

        tablePromo.getColumns().addAll(colEleve, colAnc, colFleche, colNouv, colStatut, colPretsWarn);
        tablePromo.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Action row
        HBox btnPromoRow = new HBox(12);
        btnPromoRow.setAlignment(Pos.CENTER_RIGHT);
        Region promoSpacer = new Region();
        HBox.setHgrow(promoSpacer, Priority.ALWAYS);

        Button btnExecuter = new Button("🚀 Appliquer le passage d'année scolaire");
        btnExecuter.getStyleClass().add("btn-blue");
        if (report.getTotalEleves() == 0) {
            btnExecuter.setDisable(true);
        }

        final PassageAnneeReport finalReport = report;
        btnExecuter.setOnAction(e -> {
            if (finalReport.getTotalAvecEmpruntsEnCours() > 0) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(confirm);
                confirm.setTitle("Livres non rendus détectés");
                confirm.setHeaderText("Attention : " + finalReport.getTotalAvecEmpruntsEnCours() + " élève(s) ont encore des livres en prêt.");
                confirm.setContentText("Ces élèves conserveront leur historique et leurs emprunts en cours.\nSouhaitez-vous tout de même appliquer la montée de classe ?");
                Optional<ButtonType> opt = confirm.showAndWait();
                if (opt.isEmpty() || opt.get() != ButtonType.OK) {
                    return;
                }
            }

            try {
                // Sauvegarde de sécurité
                backupService.creerSauvegarde("pre_passage_annee");
                passageAnneeService.executerPassage(finalReport);
                SoundService.playSuccess();
                loadData();
                showInfo("Passage d'année effectué avec succès",
                        "Les " + finalReport.getTotalPromus() + " élèves ont été promus et " +
                        finalReport.getTotalSortants() + " sont marqués sortants.\n" +
                        "Une sauvegarde préventive a été automatiquement créée.");
                dialog.close();
            } catch (Exception ex) {
                SoundService.playError();
                showError("Erreur lors de la promotion des classes", ex.getMessage());
            }
        });

        btnPromoRow.getChildren().addAll(promoSpacer, btnExecuter);
        promoContent.getChildren().addAll(promoInfo, kpiRow, tablePromo, btnPromoRow);
        tabPromo.setContent(promoContent);

        // ─────────────────────────────────────────────────────────────────
        // Onglet 2 : Importation ONDE (CSV)
        // ─────────────────────────────────────────────────────────────────
        Tab tabOnde = new Tab("📥 Import ONDE (Base Élèves CSV)");
        VBox ondeContent = new VBox(14);
        ondeContent.setPadding(new Insets(16));

        VBox ondeInfo = new VBox(6);
        ondeInfo.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12px; -fx-background-radius: 8px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px;");
        Label lblOndeTitle = new Label("Synchronisation directe avec l'export officiel ONDE");
        lblOndeTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblOndeDesc = new Label("Exportez la liste des élèves depuis le portail académique ONDE (format CSV avec Nom, Prénom, Classe, INE).\n" +
                "BCD Express mettra à jour automatiquement les classes des élèves existants et ajoutera les nouveaux inscrits.");
        lblOndeDesc.setStyle("-fx-font-size: 11.5px; -fx-text-fill: -color-fg-muted;");
        ondeInfo.getChildren().addAll(lblOndeTitle, lblOndeDesc);

        // File selection
        Label lblFilePrompt = new Label("Fichier CSV issu d'ONDE :");
        lblFilePrompt.setStyle("-fx-font-weight: bold;");

        TextField txtOndePath = new TextField();
        txtOndePath.setEditable(false);
        txtOndePath.setPromptText("Sélectionnez le fichier CSV extrait d'ONDE…");
        HBox.setHgrow(txtOndePath, Priority.ALWAYS);

        Button btnBrowseOnde = new Button("📂 Parcourir...");
        btnBrowseOnde.getStyleClass().add("btn-outline");

        HBox ondeFileRow = new HBox(10, txtOndePath, btnBrowseOnde);
        ondeFileRow.setAlignment(Pos.CENTER_LEFT);

        Label lblOndeStatus = new Label("Aucun fichier sélectionné.");
        lblOndeStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");

        Button btnImporterOnde = new Button("📥 Importer et synchroniser la base élèves");
        btnImporterOnde.getStyleClass().add("btn-blue");
        btnImporterOnde.setDisable(true);

        final File[] selectedOndeFile = new File[1];

        btnBrowseOnde.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Sélectionner l'export CSV ONDE");
            fc.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Fichiers CSV (*.csv)", "*.csv"),
                    new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
            );
            File f = fc.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (f != null) {
                selectedOndeFile[0] = f;
                txtOndePath.setText(f.getAbsolutePath());
                lblOndeStatus.setText("Fichier prêt : " + f.getName() + " (" + (f.length() / 1024) + " Ko)");
                btnImporterOnde.setDisable(false);
            }
        });

        btnImporterOnde.setOnAction(e -> {
            if (selectedOndeFile[0] == null) return;
            try {
                // Sauvegarde de précaution
                backupService.creerSauvegarde("pre_import_onde");
                OndeImportReport res = passageAnneeService.importerFichierOnde(selectedOndeFile[0]);
                SoundService.playSuccess();
                loadData();
                showInfo("Import ONDE terminé avec succès",
                        "Rapport d'importation ONDE :\n\n" +
                        "• Total lignes traitées : " + res.getTotalLignesTraitees() + "\n" +
                        "• Nouveaux élèves ajoutés : " + res.getElevesAjoutes() + "\n" +
                        "• Élèves mis à jour : " + res.getElevesMisAJour() + "\n" +
                        "• Lignes ignorées / erreurs : " + res.getLignesIgnorees() + "\n" +
                        "• Encodage utilisé : " + res.getEncodageUtilise() + "\n\n" +
                        "Une sauvegarde de sécurité a été créée.");
                dialog.close();
            } catch (Exception ex) {
                SoundService.playError();
                showError("Erreur lors de l'import ONDE", ex.getMessage());
            }
        });

        HBox ondeActionRow = new HBox(btnImporterOnde);
        ondeActionRow.setAlignment(Pos.CENTER_RIGHT);

        ondeContent.getChildren().addAll(ondeInfo, lblFilePrompt, ondeFileRow, lblOndeStatus, new Separator(), ondeActionRow);
        tabOnde.setContent(ondeContent);

        tabPane.getTabs().addAll(tabPromo, tabOnde);
        dialog.getDialogPane().setContent(tabPane);
        dialog.showAndWait();
    }

    // =====================================================================
    //  Dialog d'ajout / modification
    // =====================================================================

    /**
     * Affiche un dialogue pour ajouter ou modifier un utilisateur.
     *
     * @param existing l'utilisateur existant à modifier, ou {@code null} pour un ajout
     * @return l'utilisateur saisi, ou vide si annulé
     */
    private Optional<Utilisateur> showUserDialog(Utilisateur existing) {
        boolean isEdit = (existing != null);

        Dialog<Utilisateur> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle(isEdit ? "Modifier un utilisateur" : "Ajouter un utilisateur");
        dialog.setHeaderText(isEdit
                ? "Modifier les informations de " + existing.getPrenom() + " " + existing.getNom()
                : "Saisir les informations du nouvel utilisateur");

        // Boutons
        ButtonType saveButtonType = new ButtonType(isEdit ? "Modifier" : "Ajouter",
                ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Formulaire
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 20, 10, 10));

        TextField numeroField = new TextField();
        numeroField.setPromptText("Ex : E-101 ou P-01");
        TextField nomField = new TextField();
        nomField.setPromptText("Nom de famille");
        TextField prenomField = new TextField();
        prenomField.setPromptText("Prénom");
        ComboBox<TypeCompte> typeField = new ComboBox<>();
        typeField.getItems().addAll(TypeCompte.values());
        typeField.setValue(TypeCompte.ELEVE);
        TextField classeField = new TextField();
        classeField.setPromptText("Ex : CM1-A");

        // Pré-remplir si modification
        if (isEdit) {
            numeroField.setText(existing.getNumero());
            numeroField.setDisable(true); // Le numéro est la PK, non modifiable
            nomField.setText(existing.getNom());
            prenomField.setText(existing.getPrenom());
            typeField.setValue(existing.getTypeCompte());
            classeField.setText(existing.getClasse());
        }

        grid.add(new Label("Numéro :"), 0, 0);    grid.add(numeroField, 1, 0);
        grid.add(new Label("Nom :"), 0, 1);        grid.add(nomField, 1, 1);
        grid.add(new Label("Prénom :"), 0, 2);      grid.add(prenomField, 1, 2);
        grid.add(new Label("Type :"), 0, 3);        grid.add(typeField, 1, 3);
        grid.add(new Label("Classe :"), 0, 4);      grid.add(classeField, 1, 4);

        dialog.getDialogPane().setContent(grid);

        // Validation : désactiver le bouton si champs vides
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(true);

        // Listener de validation
        Runnable validate = () -> {
            boolean valid = !numeroField.getText().isBlank()
                    && !nomField.getText().isBlank()
                    && !prenomField.getText().isBlank()
                    && typeField.getValue() != null
                    && !classeField.getText().isBlank();
            saveButton.setDisable(!valid);
        };

        numeroField.textProperty().addListener((obs, o, n) -> validate.run());
        nomField.textProperty().addListener((obs, o, n) -> validate.run());
        prenomField.textProperty().addListener((obs, o, n) -> validate.run());
        typeField.valueProperty().addListener((obs, o, n) -> validate.run());
        classeField.textProperty().addListener((obs, o, n) -> validate.run());

        // Si edit, valider immédiatement (les champs sont pré-remplis)
        if (isEdit) validate.run();

        // Focus sur le premier champ utile
        if (isEdit) {
            nomField.requestFocus();
        } else {
            numeroField.requestFocus();
        }

        // Convertir le résultat
        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveButtonType) {
                Utilisateur u = new Utilisateur(
                        numeroField.getText().trim(),
                        nomField.getText().trim(),
                        prenomField.getText().trim(),
                        typeField.getValue(),
                        classeField.getText().trim()
                );
                return u;
            }
            return null;
        });

        return dialog.showAndWait();
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
}
