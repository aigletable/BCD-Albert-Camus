package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.BoiteVerificationDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.LivreAVerifier;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.LivreMetadataService.MetadataResult;
import fr.ecole.bcd.service.SoundService;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Boîte de dialogue modale pour le mode Douchette (Scan Continu & Saisie rapide à la chaîne).
 * <p>
 * Ce module hautement optimisé permet d'enregistrer et de répertorier des dizaines de livres en quelques minutes :
 * </p>
 * <ul>
 *   <li>Lecture instantanée par lecteur de code-barres optique (douchette USB ou sans fil).</li>
 *   <li>Résolution automatique des métadonnées (titre, auteur, éditeur, genre, niveau, couverture)
 *       via cache local, base BnF et Google Books API.</li>
 *   <li>Contrôle anti-doublon intelligent : détection immédiate des ouvrages déjà référencés avec boîte de dialogue
 *       de décision (ajout d'exemplaire supplémentaire ou transfert en boîte de vérification).</li>
 *   <li>Modification à chaud du titre, de l'état physique ou des annotations de chaque exemplaire scanné.</li>
 *   <li>Bouton d'annulation immédiate ("Undo") du dernier scan en cas de fausse manipulation.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class DouchetteDialogHelper {

    /**
     * Constructeur privé empêchant l'instanciation de cette classe utilitaire.
     */
    private DouchetteDialogHelper() {
        // Classe utilitaire
    }

    /**
     * Représente une ligne d'historique de scan au sein de la session active de la douchette.
     * <p>
     * Conserve les métadonnées de l'ouvrage et l'identifiant de l'exemplaire physique créé,
     * permettant les modifications ou annulations en direct.
     * </p>
     */
    public static class DouchetteSessionItem {
        /** Numéro ISBN normalisé à 10 ou 13 chiffres */
        private String isbn;
        /** Titre de l'ouvrage */
        private String titre;
        /** Auteur(s) de l'ouvrage */
        private String auteur;
        /** Maison d'édition */
        private String editeur;
        /** Identifiant unique de l'exemplaire physique créé en base */
        private int exemplaireId;
        /** Origine de la notice (ex. "Base locale", "BnF", "Google Books") */
        private String source;
        /** URL de l'image de couverture */
        private String couvertureUrl;
        /** Horodatage du scan (HH:mm:ss) */
        private String heure;
        /** Remarque ou état physique (ex. "🟢 Bon état", "🩹 À réparer") */
        private String remarque;
        /** Genre littéraire (ex. Roman, BD, Album) */
        private String genre;
        /** Thème ou sujet */
        private String theme;
        /** Niveau de lecture recommandé (ex. Cycle 2, CP, CM2) */
        private String niveau;

        /**
         * Construit un élément de session douchette sans informations de classification détaillées.
         *
         * @param isbn          numéro ISBN
         * @param titre         titre du livre
         * @param auteur        auteur
         * @param editeur       éditeur
         * @param exemplaireId  identifiant de l'exemplaire
         * @param source        source des données
         * @param couvertureUrl URL de la vignette
         * @param heure         heure du scan
         * @param remarque      remarque ou état
         */
        public DouchetteSessionItem(String isbn, String titre, String auteur, String editeur,
                                    int exemplaireId, String source, String couvertureUrl, String heure, String remarque) {
            this(isbn, titre, auteur, editeur, exemplaireId, source, couvertureUrl, heure, remarque, null, null, null);
        }

        /**
         * Construit un élément complet de session douchette avec métadonnées et classification pédagogique.
         *
         * @param isbn          numéro ISBN
         * @param titre         titre du livre
         * @param auteur        auteur
         * @param editeur       éditeur
         * @param exemplaireId  identifiant de l'exemplaire
         * @param source        source des données
         * @param couvertureUrl URL de la vignette
         * @param heure         heure du scan
         * @param remarque      remarque ou état
         * @param genre         genre littéraire
         * @param theme         thème
         * @param niveau        niveau de lecture scolaire
         */
        public DouchetteSessionItem(String isbn, String titre, String auteur, String editeur,
                                    int exemplaireId, String source, String couvertureUrl, String heure, String remarque,
                                    String genre, String theme, String niveau) {
            this.isbn = isbn;
            this.titre = titre;
            this.auteur = auteur;
            this.editeur = editeur;
            this.exemplaireId = exemplaireId;
            this.source = source;
            this.couvertureUrl = couvertureUrl;
            this.heure = heure;
            this.remarque = remarque != null ? remarque : "";
            this.genre = genre;
            this.theme = theme;
            this.niveau = niveau;
        }

        /** @return le numéro ISBN */
        public String getIsbn() { return isbn; }
        /** @param isbn le numéro ISBN */
        public void setIsbn(String isbn) { this.isbn = isbn; }
        /** @return le titre du livre */
        public String getTitre() { return titre; }
        /** @param titre le titre du livre */
        public void setTitre(String titre) { this.titre = titre; }
        /** @return l'auteur */
        public String getAuteur() { return auteur; }
        /** @param auteur l'auteur */
        public void setAuteur(String auteur) { this.auteur = auteur; }
        /** @return l'éditeur */
        public String getEditeur() { return editeur; }
        /** @param editeur l'éditeur */
        public void setEditeur(String editeur) { this.editeur = editeur; }
        /** @return l'identifiant unique de l'exemplaire */
        public int getExemplaireId() { return exemplaireId; }
        /** @return la source de provenance des métadonnées */
        public String getSource() { return source; }
        /** @return l'URL de la couverture */
        public String getCouvertureUrl() { return couvertureUrl; }
        /** @param couvertureUrl l'URL de la couverture */
        public void setCouvertureUrl(String couvertureUrl) { this.couvertureUrl = couvertureUrl; }
        /** @return l'heure du scan */
        public String getHeure() { return heure; }
        /** @return la remarque sur l'état physique */
        public String getRemarque() { return remarque; }
        /** @param remarque la remarque sur l'état physique */
        public void setRemarque(String remarque) { this.remarque = remarque; }
        /** @return le genre littéraire */
        public String getGenre() { return genre; }
        /** @return le thème */
        public String getTheme() { return theme; }
        /** @return le niveau scolaire */
        public String getNiveau() { return niveau; }
    }

    /**
     * Ouvre la fenêtre modale du mode Douchette pour démarrer une session de scan rapide à la chaîne.
     *
     * @param livreDao             DAO pour la gestion des fiches de livres
     * @param exemplaireDao        DAO pour la gestion et l'insertion des exemplaires physiques
     * @param boiteVerificationDao DAO pour la mise en attente des doublons en boîte de vérification
     * @param metadataService      service de recherche multi-source des métadonnées (BnF, Google Books)
     * @param onComplete           rappel exécuté à la fermeture pour recharger la vue principale
     * @param onBadgeUpdate        rappel exécuté pour rafraîchir les badges de notification de l'application
     */
    public static void openDouchetteDialog(
            LivreDao livreDao,
            ExemplaireDao exemplaireDao,
            BoiteVerificationDao boiteVerificationDao,
            LivreMetadataService metadataService,
            Runnable onComplete,
            Runnable onBadgeUpdate) {

        Dialog<Void> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("⚡ Mode Douchette — Saisie rapide à la chaîne");
        dialog.setHeaderText("Scannez les codes-barres avec votre douchette. Modifiez, signalez ou annulez en direct.");

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeBtn.setText("Terminer la session");

        // Composants
        TextField scanField = new TextField();
        scanField.setPromptText("🎯 Pointez votre douchette et scannez le code-barres ISBN…");
        scanField.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 10px;");

        Label lblAntiDoublon = new Label("🛡️ Contrôle anti-doublon actif : confirmation demandée si le livre existe déjà, avec mise en boîte à vérifier si refusé.");
        lblAntiDoublon.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #475569; -fx-background-color: #f1f5f9; -fx-padding: 4 8; -fx-background-radius: 6;");

        Label lblFeedback = new Label("💡 Prêt pour le premier scan. Le curseur est positionné.");
        lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #0969da; -fx-font-weight: bold;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        HBox topInputBox = new HBox(10, scanField, spinner);
        HBox.setHgrow(scanField, Priority.ALWAYS);
        topInputBox.setAlignment(Pos.CENTER_LEFT);

        // Tableau d'historique de la session
        ObservableList<DouchetteSessionItem> sessionItems = FXCollections.observableArrayList();
        TableView<DouchetteSessionItem> table = new TableView<>(sessionItems);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label("Aucun livre scanné dans cette session."));

        TableColumn<DouchetteSessionItem, String> colH = new TableColumn<>("Heure");
        colH.setPrefWidth(65);
        colH.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getHeure()));
        colH.setStyle("-fx-alignment: CENTER;");

        TableColumn<DouchetteSessionItem, String> colIsbn = new TableColumn<>("ISBN");
        colIsbn.setPrefWidth(115);
        colIsbn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getIsbn()));
        colIsbn.setStyle("-fx-alignment: CENTER;");

        TableColumn<DouchetteSessionItem, DouchetteSessionItem> colTitre = new TableColumn<>("Titre, Auteur & Classification");
        colTitre.setPrefWidth(260);
        colTitre.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colTitre.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(DouchetteSessionItem it, boolean empty) {
                super.updateItem(it, empty);
                if (empty || it == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    String s = it.getTitre();
                    if (!it.getAuteur().isBlank()) s += " — " + it.getAuteur();
                    if (it.getGenre() != null && !it.getGenre().isBlank()) {
                        s += "  [" + it.getGenre() + (it.getNiveau() != null && !it.getNiveau().isBlank() ? " • " + it.getNiveau() : "") + "]";
                    }
                    if (CoverHelper.isTitreNonRenseigne(it.getTitre())) {
                        setText(null);
                        Label warn = new Label("⚠️ ");
                        Label lbl = new Label(s);
                        lbl.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 800;");
                        HBox b = new HBox(warn, lbl);
                        b.setAlignment(Pos.CENTER_LEFT);
                        setGraphic(b);
                        setStyle("-fx-background-color: rgba(254, 226, 226, 0.45);");
                    } else {
                        setText(s);
                        setGraphic(null);
                        setStyle("");
                    }
                }
            }
        });

        TableColumn<DouchetteSessionItem, String> colEx = new TableColumn<>("N° Ex.");
        colEx.setPrefWidth(65);
        colEx.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getExemplaireId() > 0 ? "#" + c.getValue().getExemplaireId() : "–"));
        colEx.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        // Colonne État / Remarque avec badges
        TableColumn<DouchetteSessionItem, DouchetteSessionItem> colRem = new TableColumn<>("État / Alerte");
        colRem.setPrefWidth(135);
        colRem.setStyle("-fx-alignment: CENTER;");
        colRem.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colRem.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(DouchetteSessionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    String rem = item.getRemarque();
                    if (rem.isBlank() || rem.equals("🟢 Bon état")) {
                        badge.setText("🟢 Bon état");
                        badge.setStyle("-fx-background-color: #dafbe1; -fx-text-fill: #1a7f37; -fx-padding: 2 7; -fx-background-radius: 10; -fx-font-size: 11px;");
                    } else if (rem.contains("renseigner") || rem.contains("Titre")) {
                        badge.setText("⚠️ Titre à renseigner");
                        badge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-padding: 2 7; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold; -fx-border-color: #fca5a5; -fx-border-radius: 10;");
                    } else if (rem.contains("Mauvais") || rem.contains("Abîmé") || rem.contains("Détérioré")) {
                        badge.setText("⚠️ " + rem.replace("⚠️", "").trim());
                        badge.setStyle("-fx-background-color: #ffebe9; -fx-text-fill: #cf222e; -fx-padding: 2 7; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else if (rem.contains("À réparer") || rem.contains("Réparation")) {
                        badge.setText("🩹 " + rem.replace("🩹", "").trim());
                        badge.setStyle("-fx-background-color: #fff8c5; -fx-text-fill: #9a6700; -fx-padding: 2 7; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else if (rem.contains("vérifier") || rem.contains("Attention") || rem.contains("Boîte")) {
                        badge.setText("📦 " + rem.replace("❓", "").replace("📦", "").trim());
                        badge.setStyle("-fx-background-color: #ddf4ff; -fx-text-fill: #0969da; -fx-padding: 2 7; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else {
                        badge.setText(rem);
                        badge.setStyle("-fx-background-color: #f6f8fa; -fx-text-fill: #24292f; -fx-padding: 2 7; -fx-background-radius: 10; -fx-font-size: 11px;");
                    }
                    setAlignment(Pos.CENTER);
                    setGraphic(badge);
                }
            }
        });

        Label lblSessionTotal = new Label("📦 0 livre(s) scanné(s) dans cette session");
        lblSessionTotal.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #24292e;");

        // Colonne Actions (Modifier, Alerte, Supprimer)
        TableColumn<DouchetteSessionItem, DouchetteSessionItem> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(130);
        colActions.setStyle("-fx-alignment: CENTER;");
        colActions.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue()));
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button btnEdit = new Button("✏️");
            private final Button btnAlert = new Button("⚠️");
            private final Button btnDel = new Button("🗑️");
            private final HBox box = new HBox(4, btnEdit, btnAlert, btnDel);

            {
                box.setAlignment(Pos.CENTER);
                btnEdit.setStyle("-fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px;");
                btnAlert.setStyle("-fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px;");
                btnDel.setStyle("-fx-cursor: hand; -fx-padding: 3 6; -fx-font-size: 11px; -fx-text-fill: #cf222e;");

                btnEdit.setTooltip(new Tooltip("Modifier le titre, auteur ou éditeur"));
                btnAlert.setTooltip(new Tooltip("Signaler l'état (mauvais état, à réparer, etc.)"));
                btnDel.setTooltip(new Tooltip("Supprimer cet exemplaire"));

                btnEdit.setOnAction(e -> {
                    DouchetteSessionItem item = getItem();
                    if (item != null) editDouchetteBook(item, table, lblFeedback, livreDao, metadataService);
                });
                btnAlert.setOnAction(e -> {
                    DouchetteSessionItem item = getItem();
                    if (item != null) editDouchetteRemarque(item, table, lblFeedback, exemplaireDao);
                });
                btnDel.setOnAction(e -> {
                    DouchetteSessionItem item = getItem();
                    if (item != null) deleteDouchetteItem(item, sessionItems, lblSessionTotal, lblFeedback, scanField, exemplaireDao);
                });
            }

            @Override
            protected void updateItem(DouchetteSessionItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    setGraphic(box);
                }
            }
        });

        table.getColumns().addAll(colH, colIsbn, colTitre, colEx, colRem, colActions);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Bouton Annuler le dernier scan
        Button btnUndoLast = new Button("↩️ Annuler dernier scan (Ctrl+Z)");
        btnUndoLast.setStyle("-fx-background-color: #fff; -fx-border-color: #d0d7de; -fx-border-radius: 6; -fx-font-size: 12px; -fx-cursor: hand; -fx-font-weight: bold;");
        btnUndoLast.setOnAction(e -> undoLastScan(sessionItems, lblSessionTotal, lblFeedback, scanField, exemplaireDao));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottomBox = new HBox(12, lblSessionTotal, spacer, btnUndoLast);
        bottomBox.setAlignment(Pos.CENTER_LEFT);

        VBox layout = new VBox(12, topInputBox, lblAntiDoublon, lblFeedback, table, bottomBox);
        layout.setPadding(new Insets(16));
        layout.setPrefWidth(860);

        dialog.getDialogPane().setContent(layout);

        // Raccourci clavier Ctrl+Z / Cmd+Z pour annuler le dernier scan
        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
                    if (event.isShortcutDown() && event.getCode() == KeyCode.Z) {
                        undoLastScan(sessionItems, lblSessionTotal, lblFeedback, scanField, exemplaireDao);
                        event.consume();
                    }
                });
            }
        });

        // Action sur scan (touche Entrée envoyée par la douchette)
        scanField.setOnAction(e -> {
            String raw = scanField.getText().trim();
            if (raw.isEmpty()) return;

            String cleanIsbn = LivreMetadataService.cleanIsbn(raw);
            if (cleanIsbn.isEmpty()) {
                lblFeedback.setText("⚠️ Code-barres non reconnu (" + raw + ").");
                lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #d73a49; -fx-font-weight: bold;");
                SoundService.playError();
                AnimationHelper.flashError(scanField);
                scanField.clear();
                Platform.runLater(scanField::requestFocus);
                return;
            }

            // Vérification immédiate si le livre est déjà présent dans la bibliothèque
            Livre existing = null;
            try {
                existing = livreDao.findByIsbn(cleanIsbn);
            } catch (SQLException ignored) {}

            if (existing != null) {
                int currentCopies = 0;
                try {
                    currentCopies = exemplaireDao.findByIsbn(cleanIsbn).size();
                } catch (SQLException ignored) {}

                String timeStr = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                boolean isRevue = "Revue / Magazine".equalsIgnoreCase(existing.getGenre())
                        || cleanIsbn.startsWith("378") || cleanIsbn.startsWith("379") || cleanIsbn.startsWith("977")
                        || cleanIsbn.toUpperCase().startsWith("JAL") || cleanIsbn.toUpperCase().startsWith("AST")
                        || cleanIsbn.toUpperCase().startsWith("REV");

                if (isRevue) {
                    // Pour les revues de presse, chaque scan ajoute directement un exemplaire physique (+1)
                    // de manière fluide sans bloquer la chaîne de scan par un popup d'alerte de doublon.
                    try {
                        int newExId = exemplaireDao.insert(new Exemplaire(cleanIsbn, Exemplaire.Statut.DISPONIBLE, LocalDate.now(), ""));
                        DouchetteSessionItem item = new DouchetteSessionItem(
                                cleanIsbn,
                                existing.getTitre(),
                                existing.getAuteur() != null ? existing.getAuteur() : "",
                                existing.getEditeur() != null ? existing.getEditeur() : "",
                                newExId,
                                "Revue (+1)",
                                existing.getCouvertureUrl(),
                                timeStr,
                                "🟢 Bon état",
                                existing.getGenre(),
                                existing.getTheme(),
                                existing.getNiveau()
                        );
                        sessionItems.add(0, item);
                        lblSessionTotal.setText("📦 " + sessionItems.size() + " livre(s) scanné(s) dans cette session");
                        lblFeedback.setText("✅ Exemplaire #" + newExId + " ajouté pour la revue '" + existing.getTitre() + "' (" + (currentCopies + 1) + " ex. au total).");
                        lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a7f37; -fx-font-weight: bold;");
                        SoundService.playSuccess();
                        AnimationHelper.flashSuccess(scanField);
                    } catch (SQLException ex) {
                        SoundService.playError();
                        AnimationHelper.flashError(scanField);
                        DialogHelper.showError("Erreur", "Impossible d'ajouter l'exemplaire : " + ex.getMessage());
                    }
                    scanField.clear();
                    Platform.runLater(scanField::requestFocus);
                    return;
                }

                // Pour les livres ordinaires (romans, BD, documentaires) : signal d'attention sonore et visuel
                SoundService.playWarning();
                AnimationHelper.flashWarning(scanField);

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(alert);
                alert.setTitle("Livre déjà présent dans la bibliothèque");
                alert.setHeaderText("📖 " + existing.getTitre() + (existing.getAuteur() != null && !existing.getAuteur().isBlank() ? " — " + existing.getAuteur() : ""));
                alert.setContentText("Cet ouvrage possède déjà " + currentCopies + " exemplaire(s) physique(s) en base.\n\n"
                        + "• Souhaitez-vous ajouter un nouvel exemplaire physique (+1) ?\n"
                        + "• Ou le consigner dans la Boîte à vérifier (pour contrôler vos étagères et éviter les doublons fantômes) ?");

                ButtonType btnOui = new ButtonType("➕ Oui, ajouter l'exemplaire (+1)", ButtonBar.ButtonData.YES);
                ButtonType btnNon = new ButtonType("📦 Non, mettre en boîte à vérifier", ButtonBar.ButtonData.NO);
                ButtonType btnAnnuler = new ButtonType("Annuler le scan", ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(btnOui, btnNon, btnAnnuler);

                Optional<ButtonType> rep = alert.showAndWait();
                timeStr = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                if (rep.isPresent() && rep.get() == btnOui) {
                    try {
                        int newExId = exemplaireDao.insert(new Exemplaire(cleanIsbn, Exemplaire.Statut.DISPONIBLE, LocalDate.now(), ""));
                        DouchetteSessionItem item = new DouchetteSessionItem(
                                cleanIsbn,
                                existing.getTitre(),
                                existing.getAuteur() != null ? existing.getAuteur() : "",
                                existing.getEditeur() != null ? existing.getEditeur() : "",
                                newExId,
                                "Stock (+1)",
                                existing.getCouvertureUrl(),
                                timeStr,
                                "🟢 Bon état",
                                existing.getGenre(),
                                existing.getTheme(),
                                existing.getNiveau()
                        );
                        sessionItems.add(0, item);
                        lblSessionTotal.setText("📦 " + sessionItems.size() + " livre(s) scanné(s) dans cette session");
                        lblFeedback.setText("✅ Exemplaire #" + newExId + " ajouté pour '" + existing.getTitre() + "' (" + (currentCopies + 1) + " ex. au total).");
                        lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #1a7f37; -fx-font-weight: bold;");
                        SoundService.playSuccess();
                        AnimationHelper.flashSuccess(scanField);
                    } catch (SQLException ex) {
                        SoundService.playError();
                        AnimationHelper.flashError(scanField);
                        DialogHelper.showError("Erreur", "Impossible d'ajouter l'exemplaire : " + ex.getMessage());
                    }
                } else if (rep.isPresent() && rep.get() == btnNon) {
                    try {
                        LivreAVerifier toVerify = new LivreAVerifier(
                                cleanIsbn,
                                existing.getTitre(),
                                existing.getAuteur(),
                                "Scan doublon refusé (" + currentCopies + " ex. existants)",
                                currentCopies
                        );
                        boiteVerificationDao.insert(toVerify);
                        if (onBadgeUpdate != null) onBadgeUpdate.run();

                        DouchetteSessionItem item = new DouchetteSessionItem(
                                cleanIsbn,
                                existing.getTitre(),
                                existing.getAuteur() != null ? existing.getAuteur() : "",
                                existing.getEditeur() != null ? existing.getEditeur() : "",
                                -1,
                                "Boîte à vérifier",
                                existing.getCouvertureUrl(),
                                timeStr,
                                "📦 Mis en boîte à vérifier",
                                existing.getGenre(),
                                existing.getTheme(),
                                existing.getNiveau()
                        );
                        sessionItems.add(0, item);
                        lblSessionTotal.setText("📦 " + sessionItems.size() + " livre(s) scanné(s) dans cette session");
                        lblFeedback.setText("📦 Placé dans la Boîte à vérifier : '" + existing.getTitre() + "' (aucun exemplaire physique ajouté).");
                        lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #0969da; -fx-font-weight: bold;");
                        SoundService.playWarning();
                        AnimationHelper.flashWarning(scanField);
                    } catch (SQLException ex) {
                        SoundService.playError();
                        AnimationHelper.flashError(scanField);
                        DialogHelper.showError("Erreur", "Impossible d'enregistrer dans la boîte à vérifier : " + ex.getMessage());
                    }
                } else {
                    lblFeedback.setText("ℹ️ Scan annulé pour '" + existing.getTitre() + "'.");
                    lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #57606a;");
                }

                scanField.clear();
                Platform.runLater(scanField::requestFocus);
                return;
            }

            // Livre non présent dans la bibliothèque : création normale avec recherche multi-sources
            scanField.setDisable(true);
            spinner.setVisible(true);
            lblFeedback.setText("🔍 Recherche multi-sources (BNF/Google/OpenLibrary) pour " + cleanIsbn + "…");
            lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #0969da; -fx-font-weight: bold;");

            Task<DouchetteSessionItem> task = new Task<>() {
                @Override
                protected DouchetteSessionItem call() throws Exception {
                    String timeStr = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    MetadataResult meta = metadataService.rechercher(cleanIsbn);
                    boolean isNonTrouve = !meta.isFound();
                    String titre = meta.isFound() ? meta.getTitre() : "Livre ISBN " + cleanIsbn;
                    String auteur = meta.isFound() ? meta.getAuteur() : "";
                    String editeur = meta.isFound() ? meta.getEditeur() : "";
                    String cover = meta.getCouvertureUrl();
                    String remarque = isNonTrouve ? "⚠️ Titre à renseigner" : "";

                    Livre nLivre = new Livre(cleanIsbn, titre, auteur, editeur, cover,
                            meta.getGenre(), meta.getTheme(), meta.getNiveau(), meta.getDescription());
                    livreDao.insert(nLivre);

                    int newExId = exemplaireDao.insert(new Exemplaire(cleanIsbn, Exemplaire.Statut.DISPONIBLE, LocalDate.now(), remarque));
                    return new DouchetteSessionItem(
                            cleanIsbn,
                            titre,
                            auteur,
                            editeur,
                            newExId,
                            meta.getSource(),
                            cover,
                            timeStr,
                            remarque,
                            meta.getGenre(),
                            meta.getTheme(),
                            meta.getNiveau()
                    );
                }
            };

            task.setOnSucceeded(ev -> {
                scanField.setDisable(false);
                spinner.setVisible(false);
                DouchetteSessionItem item = task.getValue();
                sessionItems.add(0, item);
                lblSessionTotal.setText("📦 " + sessionItems.size() + " livre(s) scanné(s) dans cette session");

                boolean nonRenseigne = CoverHelper.isTitreNonRenseigne(item.getTitre());
                if (nonRenseigne) {
                    lblFeedback.setText("⚠️ Livre non reconnu en ligne : enregistré comme '" + item.getTitre() + "' (Ex. #" + item.getExemplaireId() + ") — À compléter via ✏️");
                    lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #DC2626; -fx-font-weight: bold;");
                    SoundService.playWarning();
                    AnimationHelper.flashWarning(scanField);
                } else {
                    String classNotice = (item.getGenre() != null && !item.getGenre().isBlank()
                            ? " [" + item.getGenre() + (item.getNiveau() != null && !item.getNiveau().isBlank() ? " • " + item.getNiveau() : "") + "]" : "");
                    String exNotice = item.getExemplaireId() > 0 ? " (Exemplaire #" + item.getExemplaireId() + ")" : "";
                    lblFeedback.setText("✅ " + item.getTitre() + exNotice + classNotice + " [" + item.getSource() + "] enregistré !");
                    lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #28a745; -fx-font-weight: bold;");
                    SoundService.playSuccess();
                    AnimationHelper.flashSuccess(scanField);
                }

                scanField.clear();
                Platform.runLater(scanField::requestFocus);
            });

            task.setOnFailed(ev -> {
                scanField.setDisable(false);
                spinner.setVisible(false);
                Throwable ex = task.getException();
                lblFeedback.setText("⚠️ Erreur lors de l'enregistrement : " + (ex != null ? ex.getMessage() : "Inconnue"));
                lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #d73a49; -fx-font-weight: bold;");

                SoundService.playError();
                AnimationHelper.flashError(scanField);

                scanField.clear();
                Platform.runLater(scanField::requestFocus);
            });

            new Thread(task).start();
        });

        // Focus forcé dès l'ouverture
        Platform.runLater(scanField::requestFocus);

        dialog.showAndWait();

        // À la fermeture, recharger les livres pour mettre à jour la table principale
        if (onComplete != null) onComplete.run();
    }

    /**
     * Ouvre une boîte de dialogue modale permettant d'éditer manuellement les métadonnées d'un livre
     * scanné lors de la session (titre, auteur, éditeur, ISBN, genre, niveau, thème) ou de relancer
     * une recherche de métadonnées en ligne.
     *
     * @param item            la ligne de session sélectionnée
     * @param table           la table d'affichage de la session pour rafraîchissement
     * @param lblFeedback     le label de message de statut pour l'utilisateur
     * @param livreDao        le DAO d'accès aux livres
     * @param metadataService le service de recherche de métadonnées en ligne
     */
    private static void editDouchetteBook(
            DouchetteSessionItem item,
            TableView<DouchetteSessionItem> table,
            Label lblFeedback,
            LivreDao livreDao,
            LivreMetadataService metadataService) {

        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Modifier les informations ou corriger l'ISBN");
        dialog.setHeaderText("Correction des informations du livre ou du scan (ISBN : " + item.getIsbn() + ")");

        ButtonType saveBtnType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 16, 20));

        TextField tfIsbn = new TextField(item.getIsbn());
        tfIsbn.setPromptText("Ex. 9782070513093");
        Button btnLookup = new Button("🔍 Rechercher infos");
        btnLookup.setStyle("-fx-cursor: hand;");
        HBox isbnBox = new HBox(8, tfIsbn, btnLookup);
        HBox.setHgrow(tfIsbn, Priority.ALWAYS);

        TextField tfTitre = new TextField(item.getTitre());
        TextField tfAuteur = new TextField(item.getAuteur());
        TextField tfEditeur = new TextField(item.getEditeur());

        Label lblSearchStatus = new Label("💡 Si vous corrigez l'ISBN, cliquez sur '🔍 Rechercher infos' pour auto-remplir le titre.");
        lblSearchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #57606a;");
        lblSearchStatus.setWrapText(true);

        grid.add(new Label("ISBN : *"), 0, 0);
        grid.add(isbnBox, 1, 0);
        grid.add(new Label("Titre : *"), 0, 1);
        grid.add(tfTitre, 1, 1);
        grid.add(new Label("Auteur :"), 0, 2);
        grid.add(tfAuteur, 1, 2);
        grid.add(new Label("Éditeur :"), 0, 3);
        grid.add(tfEditeur, 1, 3);
        grid.add(lblSearchStatus, 1, 4);

        Runnable runLookup = () -> {
            String clean = LivreMetadataService.cleanIsbn(tfIsbn.getText().trim());
            if (clean.isEmpty()) {
                lblSearchStatus.setText("⚠️ Renseignez un ISBN valide pour lancer la recherche.");
                lblSearchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #cf222e;");
                return;
            }
            btnLookup.setDisable(true);
            lblSearchStatus.setText("🔍 Interrogation BNF, Google Books & OpenLibrary pour " + clean + "…");
            lblSearchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #0969da;");

            Task<MetadataResult> task = new Task<>() {
                @Override
                protected MetadataResult call() {
                    return metadataService.rechercher(clean);
                }
            };
            task.setOnSucceeded(e -> {
                btnLookup.setDisable(false);
                MetadataResult meta = task.getValue();
                if (meta != null && meta.isFound()) {
                    tfTitre.setText(meta.getTitre());
                    if (meta.getAuteur() != null) tfAuteur.setText(meta.getAuteur());
                    if (meta.getEditeur() != null) tfEditeur.setText(meta.getEditeur());
                    if (meta.getCouvertureUrl() != null && !meta.getCouvertureUrl().isBlank()) {
                        item.setCouvertureUrl(meta.getCouvertureUrl());
                    }
                    lblSearchStatus.setText("✅ Informations trouvées via " + meta.getSource() + " !");
                    lblSearchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #1a7f37; -fx-font-weight: bold;");
                } else {
                    lblSearchStatus.setText("ℹ️ Aucun résultat automatique trouvé pour cet ISBN. Saisie manuelle.");
                    lblSearchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #9a6700;");
                }
            });
            task.setOnFailed(e -> {
                btnLookup.setDisable(false);
                lblSearchStatus.setText("⚠️ Erreur réseau lors de la recherche. Saisie manuelle.");
                lblSearchStatus.setStyle("-fx-font-size: 11px; -fx-text-fill: #cf222e;");
            });
            new Thread(task).start();
        };

        btnLookup.setOnAction(e -> runLookup.run());
        tfIsbn.setOnAction(e -> runLookup.run());

        dialog.getDialogPane().setContent(grid);
        Platform.runLater(tfIsbn::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                String oldIsbn = item.getIsbn();
                String rawNewIsbn = tfIsbn.getText().trim();
                String cleanNewIsbn = LivreMetadataService.cleanIsbn(rawNewIsbn);

                if (cleanNewIsbn.isEmpty()) {
                    DialogHelper.showError("ISBN invalide", "Veuillez renseigner un ISBN valide.");
                    return false;
                }

                String newTitre = tfTitre.getText().trim();
                if (newTitre.isBlank()) return false;
                String newAuteur = tfAuteur.getText().trim();
                String newEditeur = tfEditeur.getText().trim();

                try {
                    Livre targetLivre = new Livre(cleanNewIsbn, newTitre, newAuteur, newEditeur, item.getCouvertureUrl());

                    if (!cleanNewIsbn.equals(oldIsbn)) {
                        livreDao.reassignExemplaireIsbn(item.getExemplaireId(), oldIsbn, targetLivre);
                        item.setIsbn(cleanNewIsbn);
                        item.setTitre(newTitre);
                        item.setAuteur(newAuteur);
                        item.setEditeur(newEditeur);
                        table.refresh();
                        lblFeedback.setText("✏️ ISBN corrigé : " + oldIsbn + " ➔ " + cleanNewIsbn + " ('" + newTitre + "')");
                        lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #0969da; -fx-font-weight: bold;");
                    } else {
                        Livre l = livreDao.findByIsbn(oldIsbn);
                        if (l != null) {
                            l.setTitre(newTitre);
                            l.setAuteur(newAuteur);
                            l.setEditeur(newEditeur);
                            livreDao.update(l);
                        }
                        item.setTitre(newTitre);
                        item.setAuteur(newAuteur);
                        item.setEditeur(newEditeur);
                        table.refresh();
                        lblFeedback.setText("✏️ Livre mis à jour : '" + newTitre + "'");
                        lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #0969da; -fx-font-weight: bold;");
                    }
                    return true;
                } catch (SQLException ex) {
                    DialogHelper.showError("Erreur", "Impossible de mettre à jour le livre : " + ex.getMessage());
                }
            }
            return false;
        });

        dialog.showAndWait();
    }

    /**
     * Ouvre une boîte de sélection rapide pour qualifier l'état physique de l'exemplaire scanné
     * (bon état, à réparer, mauvais état, ou remarque textuelle libre).
     *
     * @param item          la ligne de session dont on modifie la remarque
     * @param table         la table de la session pour rafraîchissement visuel
     * @param lblFeedback   le label de message de confirmation
     * @param exemplaireDao le DAO des exemplaires pour persister la mise à jour
     */
    private static void editDouchetteRemarque(
            DouchetteSessionItem item,
            TableView<DouchetteSessionItem> table,
            Label lblFeedback,
            ExemplaireDao exemplaireDao) {

        if (item.getExemplaireId() <= 0) {
            lblFeedback.setText("⚠️ Cet élément n'a pas d'exemplaire associé.");
            return;
        }

        ChoiceDialog<String> choiceDialog = new ChoiceDialog<>(
                item.getRemarque().isBlank() ? "🟢 Bon état" : item.getRemarque(),
                List.of("🟢 Bon état", "🩹 À réparer", "⚠️ Mauvais état", "❓ Titre à vérifier", "📝 Autre remarque…")
        );
        MainApp.prepareDialog(choiceDialog);
        choiceDialog.setTitle("État / Alerte de l'exemplaire #" + item.getExemplaireId());
        choiceDialog.setHeaderText("Définir l'état de l'exemplaire scanné :\n" + item.getTitre());
        choiceDialog.setContentText("État sélectionné :");

        Optional<String> result = choiceDialog.showAndWait();
        if (result.isPresent()) {
            String chosen = result.get();
            if ("📝 Autre remarque…".equals(chosen)) {
                TextInputDialog textDialog = new TextInputDialog(item.getRemarque());
                MainApp.prepareDialog(textDialog);
                textDialog.setTitle("Remarque personnalisée");
                textDialog.setHeaderText("Exemplaire #" + item.getExemplaireId());
                textDialog.setContentText("Saisissez votre remarque :");
                Optional<String> custom = textDialog.showAndWait();
                if (custom.isPresent()) {
                    chosen = custom.get().trim();
                } else {
                    return;
                }
            } else if ("🟢 Bon état".equals(chosen)) {
                chosen = "";
            }

            try {
                exemplaireDao.updateRemarque(item.getExemplaireId(), chosen);
                item.setRemarque(chosen);
                table.refresh();
                String displayChosen = chosen.isBlank() ? "Bon état" : chosen;
                lblFeedback.setText("⚠️ État mis à jour pour l'exemplaire #" + item.getExemplaireId() + " : " + displayChosen);
                lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #9a6700; -fx-font-weight: bold;");
            } catch (SQLException ex) {
                DialogHelper.showError("Erreur", "Impossible de mettre à jour l'exemplaire : " + ex.getMessage());
            }
        }
    }

    /**
     * Supprime un élément précis scanné durant la session ainsi que son exemplaire physique associé.
     *
     * @param item            la ligne de session à supprimer
     * @param sessionItems    la liste observable des scans de la session
     * @param lblSessionTotal le label affichant le total scanné
     * @param lblFeedback     le label de statut
     * @param scanField       le champ de saisie du scan pour redonner le focus
     * @param exemplaireDao   le DAO d'accès aux exemplaires
     */
    private static void deleteDouchetteItem(
            DouchetteSessionItem item,
            ObservableList<DouchetteSessionItem> sessionItems,
            Label lblSessionTotal,
            Label lblFeedback,
            TextField scanField,
            ExemplaireDao exemplaireDao) {

        try {
            if (item.getExemplaireId() > 0) {
                exemplaireDao.delete(item.getExemplaireId());
            }
            sessionItems.remove(item);
            lblSessionTotal.setText("📦 " + sessionItems.size() + " livre(s) scanné(s) dans cette session");
            lblFeedback.setText("🗑️ Exemplaire #" + item.getExemplaireId() + " (" + item.getTitre() + ") supprimé.");
            lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #cf222e; -fx-font-weight: bold;");
        } catch (SQLException ex) {
            DialogHelper.showError("Erreur de suppression", "Impossible de supprimer l'exemplaire : " + ex.getMessage());
        }
        Platform.runLater(scanField::requestFocus);
    }

    /**
     * Annule immédiatement la dernière saisie effectuée au cours de la session (retrait du dernier exemplaire créé).
     *
     * @param sessionItems    la liste des éléments scannés de la session
     * @param lblSessionTotal le label affichant le total d'exemplaires enregistrés
     * @param lblFeedback     le label de message de retour
     * @param scanField       le champ de saisie du scan pour redonner le focus
     * @param exemplaireDao   le DAO d'accès aux exemplaires physiques
     */
    private static void undoLastScan(
            ObservableList<DouchetteSessionItem> sessionItems,
            Label lblSessionTotal,
            Label lblFeedback,
            TextField scanField,
            ExemplaireDao exemplaireDao) {

        if (sessionItems.isEmpty()) {
            lblFeedback.setText("ℹ️ Aucun scan à annuler dans cette session.");
            lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #57606a;");
            Platform.runLater(scanField::requestFocus);
            return;
        }
        DouchetteSessionItem lastItem = sessionItems.get(0);
        try {
            if (lastItem.getExemplaireId() > 0) {
                exemplaireDao.delete(lastItem.getExemplaireId());
            }
            sessionItems.remove(0);
            lblSessionTotal.setText("📦 " + sessionItems.size() + " livre(s) scanné(s) dans cette session");
            lblFeedback.setText("↩️ Dernier scan annulé : exemplaire #" + lastItem.getExemplaireId() + " (" + lastItem.getTitre() + ") retiré.");
            lblFeedback.setStyle("-fx-font-size: 12px; -fx-text-fill: #cf222e; -fx-font-weight: bold;");
        } catch (SQLException ex) {
            DialogHelper.showError("Erreur d'annulation", "Impossible d'annuler : " + ex.getMessage());
        }
        Platform.runLater(scanField::requestFocus);
    }
}
