package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.BoiteVerificationDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Exemplaire.Statut;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.LivreAVerifier;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 * Boîte de dialogue modale pour la boîte de vérification des scans (doublons potentiels)
 * et le traitement des anomalies de stock (notamment les fiches de livres sans aucun exemplaire physique).
 * <p>
 * Ce composant d'interface fournit deux onglets d'administration essentiels pour l'intégrité de la BCD :
 * </p>
 * <ul>
 *   <li><b>Onglet 1 : Scans à vérifier (Doublons)</b> — Permet d'examiner les ouvrages scannés à la douchette
 *       alors qu'ils existaient déjà dans le catalogue. L'opérateur peut valider l'ajout d'un nouvel exemplaire
 *       physique (+1), gérer les exemplaires individuels, supprimer le livre ou marquer le signalement comme résolu.</li>
 *   <li><b>Onglet 2 : Livres à 0 exemplaire</b> — Identifie les notices orphelines du catalogue sans exemplaire
 *       physique présent sur les rayonnages. Permet de créer un exemplaire régularisé ou de purger les fiches vides.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class BoiteVerificationDialogHelper {

    /**
     * Formateur de date standard français (jour/mois/année) pour l'affichage dans les tableaux de gestion.
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Constructeur privé empêchant l'instanciation de cette classe utilitaire.
     */
    private BoiteVerificationDialogHelper() {
        // Classe utilitaire
    }

    /**
     * Ouvre la fenêtre modale principale de la boîte de vérification et d'analyse des anomalies de stock.
     *
     * @param boiteVerificationDao DAO d'accès aux livres signalés en attente de vérification
     * @param livreDao             DAO d'accès aux fiches du catalogue
     * @param exemplaireDao        DAO d'accès aux exemplaires physiques
     * @param onDataChanged        action de rappel déclenchée lors de modifications du stock pour rafraîchir l'interface parente
     * @param onBadgeUpdate        action de rappel déclenchée pour mettre à jour les compteurs et badges de notification
     */
    public static void openBoiteVerificationDialog(
            BoiteVerificationDao boiteVerificationDao,
            LivreDao livreDao,
            ExemplaireDao exemplaireDao,
            Runnable onDataChanged,
            Runnable onBadgeUpdate) {

        Dialog<Void> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("📦 Boîte de vérification & Anomalies de stock");
        dialog.setHeaderText("Contrôlez les livres signalés lors des scans (doublons potentiels) et les fiches sans exemplaire physique.");

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeBtn.setText("Fermer");

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ─────────────────────────────────────────────────────────────────
        // TAB 1 : Scans à vérifier (Doublons potentiels)
        // ─────────────────────────────────────────────────────────────────
        Tab tabScans = new Tab("📦 Scans à vérifier (Doublons)");
        ObservableList<LivreAVerifier> itemsScans = FXCollections.observableArrayList();
        try {
            itemsScans.setAll(boiteVerificationDao.findAllPending());
        } catch (SQLException e) {
            DialogHelper.showError("Erreur", "Impossible de charger la boîte à vérifier : " + e.getMessage());
        }

        TableView<LivreAVerifier> tableScans = new TableView<>(itemsScans);
        tableScans.setPrefHeight(340);
        tableScans.setPlaceholder(new Label("🎉 Aucun livre à vérifier ! Votre stock et vos scans sont conformes."));

        TableColumn<LivreAVerifier, String> colDate = new TableColumn<>("Date / Heure");
        colDate.setPrefWidth(125);
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDateScan()));
        colDate.setStyle("-fx-alignment: CENTER;");

        TableColumn<LivreAVerifier, String> colIsbn = new TableColumn<>("ISBN");
        colIsbn.setPrefWidth(120);
        colIsbn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));
        colIsbn.setStyle("-fx-alignment: CENTER;");

        TableColumn<LivreAVerifier, String> colTitre = new TableColumn<>("Titre & Auteur");
        colTitre.setPrefWidth(280);
        colTitre.setCellValueFactory(c -> {
            LivreAVerifier it = c.getValue();
            String s = it.getTitre();
            if (it.getAuteur() != null && !it.getAuteur().isBlank()) s += " — " + it.getAuteur();
            return new SimpleStringProperty(s);
        });

        TableColumn<LivreAVerifier, String> colCopies = new TableColumn<>("Ex. en base");
        colCopies.setPrefWidth(95);
        colCopies.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNbExemplaires() + " ex."));
        colCopies.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<LivreAVerifier, String> colMotif = new TableColumn<>("Motif du signalement");
        colMotif.setPrefWidth(190);
        colMotif.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMotif()));

        tableScans.getColumns().addAll(colDate, colIsbn, colTitre, colCopies, colMotif);
        tableScans.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        Label lblInfoScans = new Label("💡 Ces livres ont été scannés alors qu'ils existaient déjà. Si vous confirmez avoir un exemplaire physique en plus sur vos étagères, cliquez sur '➕ Ajouter un exemplaire'. Sinon, vous pouvez supprimer un doublon ou retirer le signalement.");
        lblInfoScans.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #57606a;");
        lblInfoScans.setWrapText(true);

        Button btnAddCopyFromBox = new Button("➕ Ajouter l'exemplaire (+1)");
        btnAddCopyFromBox.setStyle("-fx-background-color: #2da44e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnAddCopyFromBox.setDisable(true);

        Button btnManageCopiesFromBox = new Button("📋 Gérer / Supprimer un exemplaire");
        btnManageCopiesFromBox.getStyleClass().add("btn-outline");
        btnManageCopiesFromBox.setStyle("-fx-cursor: hand;");
        btnManageCopiesFromBox.setDisable(true);

        Button btnDeleteLivreFromBox = new Button("🗑️ Supprimer le livre");
        btnDeleteLivreFromBox.setStyle("-fx-text-fill: #cf222e; -fx-cursor: hand;");
        btnDeleteLivreFromBox.setDisable(true);

        Button btnDismissFromBox = new Button("✅ Marquer vérifié");
        btnDismissFromBox.getStyleClass().add("btn-outline");
        btnDismissFromBox.setStyle("-fx-cursor: hand;");
        btnDismissFromBox.setDisable(true);

        Button btnClearAllBox = new Button("🧹 Vider la boîte");
        btnClearAllBox.setStyle("-fx-cursor: hand; -fx-font-size: 11px;");

        tableScans.getSelectionModel().selectedItemProperty().addListener((obs, o, selected) -> {
            boolean disabled = (selected == null);
            btnAddCopyFromBox.setDisable(disabled);
            btnManageCopiesFromBox.setDisable(disabled);
            btnDeleteLivreFromBox.setDisable(disabled);
            btnDismissFromBox.setDisable(disabled);
        });

        btnAddCopyFromBox.setOnAction(e -> {
            LivreAVerifier sel = tableScans.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                exemplaireDao.insert(new Exemplaire(sel.getIsbn(), Statut.DISPONIBLE, LocalDate.now(), ""));
                boiteVerificationDao.resolve(sel.getId());
                itemsScans.remove(sel);
                if (onBadgeUpdate != null) onBadgeUpdate.run();
                if (onDataChanged != null) onDataChanged.run();
                DialogHelper.showInfo("Exemplaire ajouté", "✅ 1 exemplaire supplémentaire (+1) a été enregistré pour '" + sel.getTitre() + "'.");
            } catch (SQLException ex) {
                DialogHelper.showError("Erreur", "Impossible d'ajouter l'exemplaire : " + ex.getMessage());
            }
        });

        btnManageCopiesFromBox.setOnAction(e -> {
            LivreAVerifier sel = tableScans.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            openExemplairesManagerDialog(sel.getIsbn(), sel.getTitre(), exemplaireDao, onDataChanged);
            try {
                Livre l = livreDao.findByIsbn(sel.getIsbn());
                if (l != null) {
                    sel.setNbExemplaires(l.getNbExemplaires());
                    tableScans.refresh();
                }
            } catch (SQLException ignored) {}
        });

        btnDeleteLivreFromBox.setOnAction(e -> {
            LivreAVerifier sel = tableScans.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (DialogHelper.showConfirmation("Supprimer le livre",
                    "Supprimer définitivement '" + sel.getTitre() + "' ?",
                    "Cette action supprimera le livre du catalogue et tous ses exemplaires.")) {
                try {
                    livreDao.delete(sel.getIsbn());
                    boiteVerificationDao.delete(sel.getId());
                    itemsScans.remove(sel);
                    if (onBadgeUpdate != null) onBadgeUpdate.run();
                    if (onDataChanged != null) onDataChanged.run();
                    DialogHelper.showInfo("Livre supprimé", "Le livre '" + sel.getTitre() + "' a été supprimé.");
                } catch (SQLException ex) {
                    DialogHelper.showError("Erreur", "Impossible de supprimer le livre : " + ex.getMessage());
                }
            }
        });

        btnDismissFromBox.setOnAction(e -> {
            LivreAVerifier sel = tableScans.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                boiteVerificationDao.resolve(sel.getId());
                itemsScans.remove(sel);
                if (onBadgeUpdate != null) onBadgeUpdate.run();
            } catch (SQLException ex) {
                DialogHelper.showError("Erreur", "Impossible de mettre à jour le statut : " + ex.getMessage());
            }
        });

        btnClearAllBox.setOnAction(e -> {
            if (itemsScans.isEmpty()) return;
            if (DialogHelper.showConfirmation("Vider la boîte à vérifier",
                    "Retirer tous les signalements de la boîte ?",
                    "Cette action marque tous les éléments de la boîte comme vérifiés sans modifier les livres.")) {
                try {
                    boiteVerificationDao.clearAll();
                    itemsScans.clear();
                    if (onBadgeUpdate != null) onBadgeUpdate.run();
                } catch (SQLException ex) {
                    DialogHelper.showError("Erreur", "Impossible de vider la boîte : " + ex.getMessage());
                }
            }
        });

        HBox boxActionsScans = new HBox(8, btnAddCopyFromBox, btnManageCopiesFromBox, btnDismissFromBox, btnDeleteLivreFromBox, new Region(), btnClearAllBox);
        HBox.setHgrow(boxActionsScans.getChildren().get(4), Priority.ALWAYS);
        boxActionsScans.setAlignment(Pos.CENTER_LEFT);

        VBox layoutScans = new VBox(10, lblInfoScans, tableScans, boxActionsScans);
        layoutScans.setPadding(new Insets(12));
        tabScans.setContent(layoutScans);

        // ─────────────────────────────────────────────────────────────────
        // TAB 2 : Livres avec 0 exemplaire physique (Anomalies)
        // ─────────────────────────────────────────────────────────────────
        Tab tabZero = new Tab("⚠️ Livres à 0 exemplaire");
        ObservableList<Livre> itemsZero = FXCollections.observableArrayList();
        try {
            itemsZero.setAll(boiteVerificationDao.findLivresSansExemplaire());
        } catch (SQLException e) {
            DialogHelper.showError("Erreur", "Impossible de charger les livres à 0 exemplaire : " + e.getMessage());
        }
        tabZero.setText("⚠️ Livres à 0 exemplaire (" + itemsZero.size() + ")");

        TableView<Livre> tableZero = new TableView<>(itemsZero);
        tableZero.setPrefHeight(340);
        tableZero.setPlaceholder(new Label("🎉 Aucun livre à 0 exemplaire en stock. Toutes vos fiches ont au moins 1 exemplaire physique."));

        TableColumn<Livre, String> colZIsbn = new TableColumn<>("ISBN");
        colZIsbn.setPrefWidth(120);
        colZIsbn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));
        colZIsbn.setStyle("-fx-alignment: CENTER;");

        TableColumn<Livre, String> colZTitre = new TableColumn<>("Titre");
        colZTitre.setPrefWidth(250);
        colZTitre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitre()));

        TableColumn<Livre, String> colZAuteur = new TableColumn<>("Auteur");
        colZAuteur.setPrefWidth(160);
        colZAuteur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAuteur() != null ? c.getValue().getAuteur() : "–"));

        TableColumn<Livre, String> colZEditeur = new TableColumn<>("Éditeur");
        colZEditeur.setPrefWidth(140);
        colZEditeur.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEditeur() != null ? c.getValue().getEditeur() : "–"));

        TableColumn<Livre, String> colZGenre = new TableColumn<>("Genre");
        colZGenre.setPrefWidth(100);
        colZGenre.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getGenre() != null ? c.getValue().getGenre() : "–"));

        tableZero.getColumns().addAll(colZIsbn, colZTitre, colZAuteur, colZEditeur, colZGenre);
        tableZero.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        Label lblInfoZero = new Label("💡 Ces ouvrages existent dans le catalogue mais n'ont aucun exemplaire physique associé. Vous pouvez leur créer un exemplaire si le livre est bien sur l'étagère, ou supprimer la fiche vide.");
        lblInfoZero.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #57606a;");
        lblInfoZero.setWrapText(true);

        Button btnAddCopyZero = new Button("➕ Ajouter 1 exemplaire physique");
        btnAddCopyZero.setStyle("-fx-background-color: #2da44e; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        btnAddCopyZero.setDisable(true);

        Button btnDeleteZero = new Button("🗑️ Supprimer cette fiche vide");
        btnDeleteZero.setStyle("-fx-text-fill: #cf222e; -fx-cursor: hand;");
        btnDeleteZero.setDisable(true);

        Button btnPurgeAllZero = new Button("🧹 Supprimer TOUS les livres à 0 exemplaire");
        btnPurgeAllZero.setStyle("-fx-text-fill: #cf222e; -fx-cursor: hand; -fx-font-size: 11px;");

        tableZero.getSelectionModel().selectedItemProperty().addListener((obs, o, selected) -> {
            boolean disabled = (selected == null);
            btnAddCopyZero.setDisable(disabled);
            btnDeleteZero.setDisable(disabled);
        });

        btnAddCopyZero.setOnAction(e -> {
            Livre sel = tableZero.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            try {
                exemplaireDao.insert(new Exemplaire(sel.getIsbn(), Statut.DISPONIBLE, LocalDate.now(), ""));
                itemsZero.remove(sel);
                tabZero.setText("⚠️ Livres à 0 exemplaire (" + itemsZero.size() + ")");
                if (onDataChanged != null) onDataChanged.run();
                DialogHelper.showInfo("Exemplaire créé", "✅ 1 exemplaire physique a été créé pour '" + sel.getTitre() + "'.");
            } catch (SQLException ex) {
                DialogHelper.showError("Erreur", "Impossible de créer l'exemplaire : " + ex.getMessage());
            }
        });

        btnDeleteZero.setOnAction(e -> {
            Livre sel = tableZero.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            if (DialogHelper.showConfirmation("Supprimer la fiche vide",
                    "Supprimer définitivement '" + sel.getTitre() + "' ?",
                    "Cette fiche sans exemplaire physique sera retirée du catalogue.")) {
                try {
                    livreDao.delete(sel.getIsbn());
                    itemsZero.remove(sel);
                    tabZero.setText("⚠️ Livres à 0 exemplaire (" + itemsZero.size() + ")");
                    if (onDataChanged != null) onDataChanged.run();
                } catch (SQLException ex) {
                    DialogHelper.showError("Erreur", "Impossible de supprimer : " + ex.getMessage());
                }
            }
        });

        btnPurgeAllZero.setOnAction(e -> {
            if (itemsZero.isEmpty()) return;
            if (DialogHelper.showConfirmation("Purger tous les livres à 0 exemplaire",
                    "Supprimer les " + itemsZero.size() + " fiches vides à 0 exemplaire ?",
                    "Tous les livres sans aucun exemplaire physique seront définitivement retirés du catalogue.")) {
                int deleted = 0;
                for (Livre l : new ArrayList<>(itemsZero)) {
                    try {
                        livreDao.delete(l.getIsbn());
                        deleted++;
                    } catch (SQLException ignored) {}
                }
                itemsZero.clear();
                tabZero.setText("⚠️ Livres à 0 exemplaire (0)");
                if (onDataChanged != null) onDataChanged.run();
                DialogHelper.showInfo("Nettoyage effectué", deleted + " fiche(s) vide(s) supprimée(s) du catalogue.");
            }
        });

        HBox boxActionsZero = new HBox(8, btnAddCopyZero, btnDeleteZero, new Region(), btnPurgeAllZero);
        HBox.setHgrow(boxActionsZero.getChildren().get(2), Priority.ALWAYS);
        boxActionsZero.setAlignment(Pos.CENTER_LEFT);

        VBox layoutZero = new VBox(10, lblInfoZero, tableZero, boxActionsZero);
        layoutZero.setPadding(new Insets(12));
        tabZero.setContent(layoutZero);

        tabPane.getTabs().addAll(tabScans, tabZero);

        VBox mainLayout = new VBox(10, tabPane);
        mainLayout.setPrefWidth(880);
        mainLayout.setPrefHeight(480);

        dialog.getDialogPane().setContent(mainLayout);
        dialog.showAndWait();
        if (onDataChanged != null) onDataChanged.run();
    }

    /**
     * Ouvre une boîte de dialogue modale dédiée à la consultation et à la gestion des exemplaires physiques
     * d'un ouvrage donné (suppression d'exemplaire fantôme, vérification de l'état ou du statut).
     *
     * @param isbn           l'identifiant ISBN de l'ouvrage dont on gère les exemplaires
     * @param titre          le titre du livre affiché dans l'en-tête de la fenêtre
     * @param exemplaireDao  le DAO d'accès aux exemplaires physiques
     * @param onDataChanged  action de rappel invoquée suite à la suppression d'un exemplaire pour rafraîchir l'affichage
     */
    public static void openExemplairesManagerDialog(String isbn, String titre, ExemplaireDao exemplaireDao, Runnable onDataChanged) {
        Dialog<Void> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Exemplaires de '" + titre + "'");
        dialog.setHeaderText("Liste des exemplaires physiques enregistrés en BCD pour cet ouvrage (ISBN : " + isbn + ").");

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        ObservableList<Exemplaire> list = FXCollections.observableArrayList();
        try {
            list.setAll(exemplaireDao.findByIsbn(isbn));
        } catch (SQLException e) {
            DialogHelper.showError("Erreur", "Impossible de charger les exemplaires : " + e.getMessage());
        }

        TableView<Exemplaire> table = new TableView<>(list);
        table.setPrefHeight(250);
        table.setPlaceholder(new Label("Aucun exemplaire physique trouvé."));

        TableColumn<Exemplaire, String> colId = new TableColumn<>("N° Exemplaire");
        colId.setPrefWidth(110);
        colId.setCellValueFactory(c -> new SimpleStringProperty("#" + c.getValue().getId()));
        colId.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        TableColumn<Exemplaire, String> colStatut = new TableColumn<>("Statut");
        colStatut.setPrefWidth(120);
        colStatut.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatut().toString()));
        colStatut.setStyle("-fx-alignment: CENTER;");

        TableColumn<Exemplaire, String> colDate = new TableColumn<>("Date d'ajout");
        colDate.setPrefWidth(120);
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDateAjout() != null ? c.getValue().getDateAjout().format(DATE_FMT) : "–"));
        colDate.setStyle("-fx-alignment: CENTER;");

        TableColumn<Exemplaire, String> colRem = new TableColumn<>("Remarque / État");
        colRem.setPrefWidth(180);
        colRem.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRemarque() != null ? c.getValue().getRemarque() : ""));

        table.getColumns().addAll(colId, colStatut, colDate, colRem);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        Button btnDelEx = new Button("🗑️ Supprimer cet exemplaire (doublon fantôme)");
        btnDelEx.setStyle("-fx-text-fill: #cf222e; -fx-cursor: hand; -fx-font-weight: bold;");
        btnDelEx.setDisable(true);

        table.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> btnDelEx.setDisable(n == null));

        btnDelEx.setOnAction(e -> {
            Exemplaire selEx = table.getSelectionModel().getSelectedItem();
            if (selEx == null) return;
            if (selEx.getStatut() == Statut.EMPRUNTE) {
                DialogHelper.showWarning("Suppression refusée", "Cet exemplaire est actuellement emprunté.");
                return;
            }
            if (DialogHelper.showConfirmation("Supprimer l'exemplaire",
                    "Supprimer l'exemplaire #" + selEx.getId() + " ?",
                    "Cet exemplaire physique sera retiré de la base.")) {
                try {
                    exemplaireDao.delete(selEx.getId());
                    list.remove(selEx);
                    if (onDataChanged != null) onDataChanged.run();
                } catch (SQLException ex) {
                    DialogHelper.showError("Erreur", "Impossible de supprimer cet exemplaire : " + ex.getMessage());
                }
            }
        });

        VBox layout = new VBox(10, table, btnDelEx);
        layout.setPadding(new Insets(12));
        layout.setPrefWidth(600);
        dialog.getDialogPane().setContent(layout);
        dialog.showAndWait();
    }
}
