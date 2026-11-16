package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.BoiteVerificationDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Exemplaire.Statut;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.LivreAVerifier;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.LivreMetadataService.MetadataResult;
import fr.ecole.bcd.service.SoundService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Assistant interactif d'ajout et de vérification d'ouvrages par Titre & Auteur (sans ISBN / code-barres).
 * <p>
 * Conçu pour les situations concrètes en BCD :
 * <ul>
 *   <li>Livres anciens (parus avant 1970 ou sans code-barres imprimé).</li>
 *   <li>Codes-barres effacés, arrachés ou recouverts d'un scotch opaque.</li>
 *   <li>Dons sans code-barres ou périodiques / revues.</li>
 * </ul>
 * </p>
 * <p>
 * Fonctionnement :
 * <ol>
 *   <li>L'utilisateur tape le titre (+ auteur optionnel) et clique sur « Rechercher en ligne ».</li>
 *   <li>Interrogation en cascade (OpenLibrary, Google Books, BNF).</li>
 *   <li>Présentation des éditions correspondantes avec choix rapide.</li>
 *   <li>Fiche de vérification détaillée pré-remplie (couverture, classification, résumé, code interne CAMUS-XXXX).</li>
 *   <li>Validation et enregistrement immédiat du livre et de ses exemplaires physiques.</li>
 * </ol>
 * </p>
 *
 * @author JC ART
 * @since 2026-11-16
 */
public final class LivreVerificationDialogHelper {

    private LivreVerificationDialogHelper() {
        // Classe utilitaire
    }

    /**
     * Ouvre la boîte de dialogue interactive d'ajout par Titre & Auteur.
     *
     * @param owner                fenêtre parente
     * @param livreDao             DAO des livres
     * @param exemplaireDao        DAO des exemplaires physiques
     * @param boiteVerificationDao DAO de la boîte à vérifier
     * @param metadataService      service de recherche bibliographique
     * @param initialTitre         titre initial (peut être null ou vide)
     * @param initialAuteur        auteur initial (peut être null ou vide)
     * @param onLivreAdded         rappel exécuté lors de l'ajout effectif du livre
     */
    public static void openAddByTitleDialog(
            Window owner,
            LivreDao livreDao,
            ExemplaireDao exemplaireDao,
            BoiteVerificationDao boiteVerificationDao,
            LivreMetadataService metadataService,
            String initialTitre,
            String initialAuteur,
            Consumer<Livre> onLivreAdded) {

        Dialog<Livre> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        if (owner != null) dialog.initOwner(owner);

        dialog.setTitle("📖 Ajout d'un livre sans code-barres (Recherche par Titre & Auteur)");
        dialog.setHeaderText("Recherchez l'ouvrage par son titre en ligne (BNF, Google Books, OpenLibrary), vérifiez ou ajustez sa fiche, puis enregistrez-le dans la BCD.");

        ButtonType btnValider = new ButtonType("✅ Valider et Ajouter à la BCD", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnValider, ButtonType.CANCEL);

        // ─────────────────────────────────────────────────────────────────
        // 1. Zone supérieure : Saisie du Titre & Auteur + Déclencheur recherche
        // ─────────────────────────────────────────────────────────────────
        VBox topBox = new VBox(10);
        topBox.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 12 16; -fx-background-radius: 8; -fx-border-color: #cbd5e1; -fx-border-radius: 8;");

        Label lblTopIntro = new Label("🔎 1. Identifiez l'ouvrage physique sans code-barres :");
        lblTopIntro.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: #1e293b;");

        TextField tfSearchTitre = new TextField(initialTitre != null ? initialTitre : "");
        tfSearchTitre.setPromptText("Titre de l'ouvrage (ex: Le Petit Prince, Les Fables de La Fontaine…)");
        tfSearchTitre.setStyle("-fx-font-size: 13px; -fx-padding: 7 10;");
        HBox.setHgrow(tfSearchTitre, Priority.ALWAYS);

        TextField tfSearchAuteur = new TextField(initialAuteur != null ? initialAuteur : "");
        tfSearchAuteur.setPromptText("Auteur (facultatif, ex: Saint-Exupéry)");
        tfSearchAuteur.setPrefWidth(220);
        tfSearchAuteur.setStyle("-fx-font-size: 13px; -fx-padding: 7 10;");

        Button btnSearchOnline = new Button("🔍 Rechercher en ligne");
        btnSearchOnline.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12.5px; -fx-cursor: hand; -fx-padding: 7 14; -fx-background-radius: 6;");

        Button btnManualMode = new Button("⚡ Saisie directe sans recherche");
        btnManualMode.setStyle("-fx-background-color: white; -fx-text-fill: #475569; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-font-size: 11.5px; -fx-cursor: hand; -fx-padding: 7 10;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        HBox searchRow = new HBox(8, tfSearchTitre, tfSearchAuteur, btnSearchOnline, btnManualMode, spinner);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label lblStatus = new Label("💡 Tapez le titre de l'ouvrage puis appuyez sur Entrée ou cliquez sur « Rechercher en ligne ».");
        lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748b;");

        topBox.getChildren().addAll(lblTopIntro, searchRow, lblStatus);

        // ─────────────────────────────────────────────────────────────────
        // 2. Zone intermédiaire : Liste des éditions candidates (si > 1)
        // ─────────────────────────────────────────────────────────────────
        VBox candidatesBox = new VBox(8);
        candidatesBox.setStyle("-fx-background-color: #eff6ff; -fx-padding: 10 14; -fx-background-radius: 8; -fx-border-color: #bfdbfe; -fx-border-radius: 8;");
        candidatesBox.setVisible(false);
        candidatesBox.setManaged(false);

        Label lblCandidatesTitle = new Label("📚 Éditions trouvées en ligne — Cliquez pour sélectionner l'édition correspondante :");
        lblCandidatesTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1d4ed8;");
        VBox candidatesList = new VBox(6);
        candidatesBox.getChildren().addAll(lblCandidatesTitle, candidatesList);

        // ─────────────────────────────────────────────────────────────────
        // 3. Zone principale : Fiche de vérification & enregistrement
        // ─────────────────────────────────────────────────────────────────
        VBox verificationCard = new VBox(12);
        verificationCard.setStyle("-fx-background-color: white; -fx-padding: 16; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");

        Label lblVerifTitle = new Label("📋 2. Fiche de vérification et classification avant ajout à la BCD");
        lblVerifTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13.5px; -fx-text-fill: #0f172a;");

        // Panneau Couverture à gauche
        VBox coverPane = new VBox(8);
        coverPane.setAlignment(Pos.TOP_CENTER);
        coverPane.setPrefWidth(140);
        coverPane.setMinWidth(140);

        StackPane coverContainer = new StackPane();
        coverContainer.setPrefSize(120, 160);
        coverContainer.setMaxSize(120, 160);
        coverContainer.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");

        ImageView coverImage = new ImageView();
        coverImage.setFitWidth(120);
        coverImage.setFitHeight(160);
        coverImage.setPreserveRatio(true);
        Rectangle clip = new Rectangle(120, 160);
        clip.setArcWidth(12);
        clip.setArcHeight(12);
        coverImage.setClip(clip);
        coverContainer.getChildren().add(coverImage);

        TextField tfCouvertureUrl = new TextField();
        tfCouvertureUrl.setPromptText("URL de l'image…");
        tfCouvertureUrl.setVisible(false);
        tfCouvertureUrl.setManaged(false);

        Button btnAutoFindCover = new Button("🔍 Trouver couverture");
        btnAutoFindCover.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0369a1; -fx-border-color: #bae6fd; -fx-border-radius: 5; -fx-font-size: 11px; -fx-cursor: hand; -fx-font-weight: bold; -fx-padding: 4 8;");
        btnAutoFindCover.setTooltip(new Tooltip("Recherche automatique haute résolution sur Amazon CDN, OpenLibrary HD et Epagine"));

        Button btnChangeCover = new Button("✏️ URL image");
        btnChangeCover.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 8;");

        ProgressIndicator coverSpinner = new ProgressIndicator();
        coverSpinner.setPrefSize(16, 16);
        coverSpinner.setVisible(false);

        Label lblCoverStatus = new Label();
        lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        lblCoverStatus.setWrapText(true);
        lblCoverStatus.setMaxWidth(135);
        lblCoverStatus.setAlignment(Pos.CENTER);

        HBox coverBtns = new HBox(4, btnAutoFindCover, btnChangeCover);
        coverBtns.setAlignment(Pos.CENTER);

        coverPane.getChildren().addAll(coverContainer, coverBtns, coverSpinner, lblCoverStatus);

        // Formulaire champs à droite
        GridPane formGrid = new GridPane();
        formGrid.setHgap(10);
        formGrid.setVgap(10);
        HBox.setHgrow(formGrid, Priority.ALWAYS);

        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de l'ouvrage (obligatoire)");
        tfTitre.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");

        TextField tfAuteur = new TextField();
        tfAuteur.setPromptText("Nom et prénom de l'auteur");

        TextField tfEditeur = new TextField();
        tfEditeur.setPromptText("Maison d'édition (ex: Gallimard, Milan, Nathan…)");

        TextField tfCode = new TextField();
        tfCode.setStyle("-fx-font-family: monospace; -fx-font-weight: bold; -fx-font-size: 12px;");
        tfCode.setPromptText("Ex: CAMUS-0042 ou ISBN officiel");

        Button btnGenNewCode = new Button("🔄 Nouveau code BCD");
        btnGenNewCode.setStyle("-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 5; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 4 8;");
        btnGenNewCode.setTooltip(new Tooltip("Générer un nouvel identifiant interne sans collision pour ouvrage sans code-barres"));

        Label lblCodeBadge = new Label("🏷️ Code interne généré");
        lblCodeBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");

        HBox codeBox = new HBox(8, tfCode, btnGenNewCode, lblCodeBadge);
        codeBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfCode, Priority.ALWAYS);

        ComboBox<String> cbGenre = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.GENRES_STANDARDS));
        cbGenre.setEditable(true);
        cbGenre.setPromptText("Genre littéraire…");
        cbGenre.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> cbTheme = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.THEMES_STANDARDS));
        cbTheme.setEditable(true);
        cbTheme.setPromptText("Thème principal…");
        cbTheme.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> cbNiveau = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.NIVEAUX_STANDARDS));
        cbNiveau.setEditable(true);
        cbNiveau.setPromptText("Niveau scolaire conseillé…");
        cbNiveau.setMaxWidth(Double.MAX_VALUE);

        HBox classifBox = new HBox(8, cbGenre, cbTheme, cbNiveau);
        HBox.setHgrow(cbGenre, Priority.ALWAYS);
        HBox.setHgrow(cbTheme, Priority.ALWAYS);
        HBox.setHgrow(cbNiveau, Priority.ALWAYS);

        TextArea taDescription = new TextArea();
        taDescription.setPromptText("Résumé, 4e de couverture ou synopsis pédagogique de l'ouvrage…");
        taDescription.setPrefRowCount(3);
        taDescription.setWrapText(true);

        Spinner<Integer> spNbCopies = new Spinner<>(1, 50, 1);
        spNbCopies.setEditable(true);
        spNbCopies.setPrefWidth(90);

        Label lblCopiesHint = new Label("💡 Combien d'exemplaires physiques de ce titre ajoutez-vous aujourd'hui ?");
        lblCopiesHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        HBox copiesBox = new HBox(10, spNbCopies, lblCopiesHint);
        copiesBox.setAlignment(Pos.CENTER_LEFT);

        // Remplissage GridPane
        int row = 0;
        formGrid.add(new Label("Titre de l'ouvrage : *"), 0, row);
        formGrid.add(tfTitre, 1, row++);

        formGrid.add(new Label("Auteur(s) :"), 0, row);
        formGrid.add(tfAuteur, 1, row++);

        formGrid.add(new Label("Éditeur :"), 0, row);
        formGrid.add(tfEditeur, 1, row++);

        formGrid.add(new Label("Identifiant / Code :"), 0, row);
        formGrid.add(codeBox, 1, row++);

        formGrid.add(new Label("Classification BCD :"), 0, row);
        formGrid.add(classifBox, 1, row++);

        formGrid.add(new Label("Résumé / Description :"), 0, row);
        formGrid.add(taDescription, 1, row++);

        formGrid.add(new Label("Exemplaires initiaux :"), 0, row);
        formGrid.add(copiesBox, 1, row++);

        HBox verifContent = new HBox(16, coverPane, formGrid);
        verifContent.setAlignment(Pos.TOP_LEFT);

        verificationCard.getChildren().addAll(lblVerifTitle, verifContent);

        // ─────────────────────────────────────────────────────────────────
        // Assemblage global dans un ScrollPane pour confort sur petits écrans
        // ─────────────────────────────────────────────────────────────────
        VBox rootLayout = new VBox(14, topBox, candidatesBox, verificationCard);
        rootLayout.setPadding(new Insets(10));
        rootLayout.setPrefWidth(840);

        ScrollPane scrollPane = new ScrollPane(rootLayout);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(620);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");

        dialog.getDialogPane().setContent(scrollPane);

        // ─────────────────────────────────────────────────────────────────
        // Logique de rafraîchissement dynamique de la couverture
        // ─────────────────────────────────────────────────────────────────
        Runnable refreshCoverDisplay = () -> {
            String url = tfCouvertureUrl.getText() != null ? tfCouvertureUrl.getText().trim() : "";
            if (!url.isEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                try {
                    Image img = new Image(url, 120, 160, true, true, true);
                    img.progressProperty().addListener((obs, oldP, newP) -> {
                        if (newP.doubleValue() >= 1.0 && !img.isError()) {
                            coverImage.setImage(img);
                            coverContainer.getChildren().setAll(coverImage);
                        }
                    });
                    img.errorProperty().addListener((obs, oldE, isErr) -> {
                        if (Boolean.TRUE.equals(isErr)) {
                            Livre previewLivre = new Livre(
                                    tfCode.getText(),
                                    tfTitre.getText().isBlank() ? "Livre BCD" : tfTitre.getText(),
                                    tfAuteur.getText(),
                                    tfEditeur.getText(),
                                    "",
                                    cbGenre.getValue() != null ? cbGenre.getValue() : "Roman",
                                    "", "", ""
                            );
                            StackPane vectorCover = CoverHelper.createStyledCover(previewLivre, 120, 160);
                            coverContainer.getChildren().setAll(vectorCover);
                        }
                    });
                    coverImage.setImage(img);
                    coverContainer.getChildren().setAll(coverImage);
                    return;
                } catch (Exception ignored) {}
            }

            // Repli couverture stylisée vectorielle
            Livre previewLivre = new Livre(
                    tfCode.getText(),
                    tfTitre.getText().isBlank() ? "Livre BCD" : tfTitre.getText(),
                    tfAuteur.getText(),
                    tfEditeur.getText(),
                    "",
                    cbGenre.getValue() != null ? cbGenre.getValue() : "Roman",
                    "", "", ""
            );
            StackPane vectorCover = CoverHelper.createStyledCover(previewLivre, 120, 160);
            coverContainer.getChildren().setAll(vectorCover);
        };

        // Recherche automatique de couverture ultra-rapide (Amazon CDN, OpenLibrary HD, Epagine)
        Runnable runFindCover = () -> {
            String title = tfTitre.getText().trim();
            String author = tfAuteur.getText().trim();
            String code = tfCode.getText().trim();
            if (title.isEmpty() && code.isEmpty()) {
                lblCoverStatus.setText("⚠️ Titre requis");
                lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #dc2626;");
                return;
            }

            btnAutoFindCover.setDisable(true);
            coverSpinner.setVisible(true);
            lblCoverStatus.setText("Recherche image…");
            lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #2563eb;");

            Task<String> coverTask = new Task<>() {
                @Override
                protected String call() {
                    return metadataService.findVerifiedCover(code, "", title, author);
                }
            };
            coverTask.setOnSucceeded(e -> {
                btnAutoFindCover.setDisable(false);
                coverSpinner.setVisible(false);
                String c = coverTask.getValue();
                if (c != null && !c.isBlank()) {
                    tfCouvertureUrl.setText(c);
                    refreshCoverDisplay.run();
                    lblCoverStatus.setText("✅ Couverture trouvée");
                    lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #166534; -fx-font-weight: bold;");
                } else {
                    lblCoverStatus.setText("ℹ️ Aucune image trouvée");
                    lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #d97706;");
                }
            });
            coverTask.setOnFailed(e -> {
                btnAutoFindCover.setDisable(false);
                coverSpinner.setVisible(false);
                lblCoverStatus.setText("⚠️ Erreur réseau");
                lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #dc2626;");
            });
            new Thread(coverTask).start();
        };

        btnAutoFindCover.setOnAction(e -> runFindCover.run());

        // Bouton changer couverture
        btnChangeCover.setOnAction(e -> {
            TextInputDialog prompt = new TextInputDialog(tfCouvertureUrl.getText());
            MainApp.prepareDialog(prompt);
            prompt.setTitle("Image de couverture");
            prompt.setHeaderText("Saisissez l'adresse URL directe de la couverture du livre :");
            prompt.setContentText("URL (https://…) :");
            prompt.showAndWait().ifPresent(newUrl -> {
                tfCouvertureUrl.setText(newUrl.trim());
                lblCoverStatus.setText(newUrl.isBlank() ? "" : "📷 URL personnalisée");
                refreshCoverDisplay.run();
            });
        });

        // Générer un premier code CAMUS par défaut
        Runnable generateBcdCode = () -> {
            try {
                String candidate = livreDao.generateUniqueBcdCode();
                tfCode.setText(candidate);
                lblCodeBadge.setText("🏷️ Code interne généré");
                lblCodeBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #92400e; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");
            } catch (SQLException ex) {
                tfCode.setText("CAMUS-" + (System.currentTimeMillis() % 100000));
            }
        };
        generateBcdCode.run();

        btnGenNewCode.setOnAction(e -> generateBcdCode.run());

        // Bouton de validation activé uniquement si titre non vide
        Node btnOk = dialog.getDialogPane().lookupButton(btnValider);
        Runnable validateOk = () -> {
            boolean valid = tfTitre.getText() != null && !tfTitre.getText().trim().isEmpty();
            btnOk.setDisable(!valid);
        };
        btnOk.setDisable(true);
        tfTitre.textProperty().addListener((o, oldV, newV) -> {
            validateOk.run();
            refreshCoverDisplay.run();
        });
        cbGenre.valueProperty().addListener((o, oldV, newV) -> refreshCoverDisplay.run());

        // ─────────────────────────────────────────────────────────────────
        // Remplir le formulaire avec un résultat de recherche
        // ─────────────────────────────────────────────────────────────────
        Consumer<MetadataResult> populateFormWithResult = meta -> {
            if (meta == null) return;
            tfTitre.setText(meta.getTitre());
            if (meta.getAuteur() != null) tfAuteur.setText(meta.getAuteur());
            if (meta.getEditeur() != null) tfEditeur.setText(meta.getEditeur());
            if (meta.getDescription() != null) taDescription.setText(meta.getDescription());

            if (meta.getGenre() != null && !meta.getGenre().isBlank()) cbGenre.setValue(meta.getGenre());
            if (meta.getTheme() != null && !meta.getTheme().isBlank()) cbTheme.setValue(meta.getTheme());
            if (meta.getNiveau() != null && !meta.getNiveau().isBlank()) cbNiveau.setValue(meta.getNiveau());

            if (meta.getIsbn() != null && !meta.getIsbn().isBlank()) {
                tfCode.setText(meta.getIsbn());
                lblCodeBadge.setText("🌐 ISBN officiel trouvé (" + meta.getSource() + ")");
                lblCodeBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #166534; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 6;");
            } else {
                generateBcdCode.run();
            }

            if (meta.getCouvertureUrl() != null && !meta.getCouvertureUrl().isBlank()) {
                tfCouvertureUrl.setText(meta.getCouvertureUrl());
                lblCoverStatus.setText("✅ Couverture active");
                lblCoverStatus.setStyle("-fx-font-size: 10px; -fx-text-fill: #166534; -fx-font-weight: bold;");
            } else {
                tfCouvertureUrl.clear();
                lblCoverStatus.setText("");
                // Recherche automatique immédiate de couverture
                Platform.runLater(runFindCover::run);
            }
            refreshCoverDisplay.run();
            validateOk.run();
        };

        // ─────────────────────────────────────────────────────────────────
        // Déclencheur de recherche en ligne
        // ─────────────────────────────────────────────────────────────────
        Runnable triggerSearch = () -> {
            String titreQuery = tfSearchTitre.getText().trim();
            String auteurQuery = tfSearchAuteur.getText().trim();
            if (titreQuery.isEmpty() && auteurQuery.isEmpty()) {
                lblStatus.setText("⚠️ Veuillez au moins renseigner un titre ou un nom d'auteur.");
                lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                return;
            }

            btnSearchOnline.setDisable(true);
            spinner.setVisible(true);
            lblStatus.setText("🔍 Recherche multi-sources en ligne (BNF, Google Books, OpenLibrary)…");
            lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #2563eb; -fx-font-weight: bold;");
            candidatesBox.setVisible(false);
            candidatesBox.setManaged(false);
            candidatesList.getChildren().clear();

            Task<List<MetadataResult>> task = new Task<>() {
                @Override
                protected List<MetadataResult> call() {
                    return metadataService.rechercherParTitreAuteur(titreQuery, auteurQuery, 5);
                }
            };

            task.setOnSucceeded(ev -> {
                btnSearchOnline.setDisable(false);
                spinner.setVisible(false);
                List<MetadataResult> results = task.getValue();

                if (results == null || results.isEmpty()) {
                    lblStatus.setText("ℹ️ Aucun résultat en ligne. Saisie manuelle activée avec code interne BCD.");
                    lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #d97706;");
                    tfTitre.setText(titreQuery);
                    tfAuteur.setText(auteurQuery);
                    LivreClassificationService.ClassificationResult cr =
                            metadataService.getClassifier().classifySimple(titreQuery, auteurQuery, "", "");
                    cbGenre.setValue(cr.getGenre());
                    cbTheme.setValue(cr.getTheme());
                    cbNiveau.setValue(cr.getNiveau());
                    generateBcdCode.run();
                    refreshCoverDisplay.run();
                    validateOk.run();
                    return;
                }

                // Si 1 seul résultat trouvé
                if (results.size() == 1) {
                    MetadataResult r = results.get(0);
                    populateFormWithResult.accept(r);
                    lblStatus.setText("✅ Livre trouvé via " + r.getSource() + " ! Vérifiez les informations ci-dessous.");
                    lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #166534; -fx-font-weight: bold;");
                    SoundService.playSuccess();
                    return;
                }

                // Plusieurs correspondances : afficher la liste de sélection d'éditions
                lblStatus.setText("✅ " + results.size() + " correspondances trouvées. Cliquez sur celle correspondant à votre livre :");
                lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #166534; -fx-font-weight: bold;");
                SoundService.playSuccess();

                candidatesList.getChildren().clear();
                for (MetadataResult cand : results) {
                    HBox card = new HBox(12);
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.setStyle("-fx-background-color: white; -fx-border-color: #cbd5e1; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12; -fx-cursor: hand;");

                    Node visualGraphic;
                    String cUrl = cand.getCouvertureUrl();
                    if (cUrl != null && !cUrl.isBlank() && (cUrl.startsWith("http://") || cUrl.startsWith("https://"))) {
                        ImageView miniThumb = new ImageView();
                        miniThumb.setFitWidth(36);
                        miniThumb.setFitHeight(48);
                        miniThumb.setPreserveRatio(true);
                        Rectangle miniClip = new Rectangle(36, 48);
                        miniClip.setArcWidth(6);
                        miniClip.setArcHeight(6);
                        miniThumb.setClip(miniClip);
                        Image mImg = new Image(cUrl, 36, 48, true, true, true);
                        miniThumb.setImage(mImg);
                        StackPane thumbWrap = new StackPane(miniThumb);
                        thumbWrap.setPrefSize(36, 48);
                        visualGraphic = thumbWrap;
                    } else {
                        Label lblIcon = new Label(CoverHelper.getStyleForGenre(cand.getGenre()).icon());
                        lblIcon.setStyle("-fx-font-size: 20px;");
                        visualGraphic = lblIcon;
                    }

                    VBox info = new VBox(2);
                    HBox.setHgrow(info, Priority.ALWAYS);

                    Label tLbl = new Label(cand.getTitre() + (!cand.getAuteur().isBlank() ? " — " + cand.getAuteur() : ""));
                    tLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12.5px; -fx-text-fill: #1e293b;");

                    String subStr = (cand.getEditeur().isBlank() ? "" : cand.getEditeur() + " • ")
                            + (cand.getDatePublication().isBlank() ? "" : cand.getDatePublication() + " • ")
                            + (!cand.getIsbn().isBlank() ? "ISBN " + cand.getIsbn() : "Sans ISBN")
                            + " [" + cand.getSource() + "]";
                    Label subLbl = new Label(subStr);
                    subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

                    info.getChildren().addAll(tLbl, subLbl);

                    Button btnSelectThis = new Button("Choisir cette édition ➔");
                    btnSelectThis.setStyle("-fx-background-color: #2563eb; -fx-text-fill: white; -fx-font-size: 11.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 5 10; -fx-background-radius: 5;");
                    btnSelectThis.setOnAction(clickE -> {
                        populateFormWithResult.accept(cand);
                        candidatesBox.setVisible(false);
                        candidatesBox.setManaged(false);
                        lblStatus.setText("✅ Édition sélectionnée : '" + cand.getTitre() + "' (" + cand.getEditeur() + "). Fiche prête pour enregistrement.");
                        lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #166534; -fx-font-weight: bold;");
                    });

                    card.getChildren().addAll(visualGraphic, info, btnSelectThis);
                    candidatesList.getChildren().add(card);
                }

                candidatesBox.setVisible(true);
                candidatesBox.setManaged(true);

                // Pré-remplir avec le premier par défaut
                populateFormWithResult.accept(results.get(0));
            });

            task.setOnFailed(ev -> {
                btnSearchOnline.setDisable(false);
                spinner.setVisible(false);
                lblStatus.setText("⚠️ Recherche en ligne indisponible. Saisie manuelle activée.");
                lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #dc2626;");
                tfTitre.setText(titreQuery);
                tfAuteur.setText(auteurQuery);
                generateBcdCode.run();
                refreshCoverDisplay.run();
                validateOk.run();
            });

            new Thread(task).start();
        };

        btnSearchOnline.setOnAction(e -> triggerSearch.run());
        tfSearchTitre.setOnAction(e -> triggerSearch.run());
        tfSearchAuteur.setOnAction(e -> triggerSearch.run());

        btnManualMode.setOnAction(e -> {
            tfTitre.setText(tfSearchTitre.getText().trim());
            tfAuteur.setText(tfSearchAuteur.getText().trim());
            generateBcdCode.run();
            LivreClassificationService.ClassificationResult cr =
                    metadataService.getClassifier().classifySimple(tfTitre.getText(), tfAuteur.getText(), "", "");
            cbGenre.setValue(cr.getGenre());
            cbTheme.setValue(cr.getTheme());
            cbNiveau.setValue(cr.getNiveau());
            refreshCoverDisplay.run();
            validateOk.run();
            lblStatus.setText("⚡ Mode saisie manuelle activé avec code BCD interne garanti unique.");
            lblStatus.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #475569; -fx-font-weight: bold;");
            if (!tfTitre.getText().isBlank()) {
                runFindCover.run();
            }
            Platform.runLater(tfTitre::requestFocus);
        });

        tfTitre.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && (tfCouvertureUrl.getText() == null || tfCouvertureUrl.getText().isBlank()) && tfTitre.getText().trim().length() >= 3) {
                runFindCover.run();
            }
        });

        // Initialisation si titre passé en paramètre
        if (initialTitre != null && !initialTitre.isBlank()) {
            Platform.runLater(triggerSearch::run);
        } else {
            refreshCoverDisplay.run();
            Platform.runLater(tfSearchTitre::requestFocus);
        }

        // ─────────────────────────────────────────────────────────────────
        // Conversion du résultat et persistance en base
        // ─────────────────────────────────────────────────────────────────
        dialog.setResultConverter(buttonType -> {
            if (buttonType == btnValider) {
                String cleanTitre = tfTitre.getText().trim();
                String cleanAuteur = tfAuteur.getText().trim();
                String cleanEditeur = tfEditeur.getText().trim();
                String code = tfCode.getText().trim();

                if (code.isEmpty()) {
                    try {
                        code = livreDao.generateUniqueBcdCode();
                    } catch (SQLException ex) {
                        code = "CAMUS-" + (System.currentTimeMillis() % 100000);
                    }
                }

                String genre = cbGenre.getValue() != null && !cbGenre.getValue().isBlank() ? cbGenre.getValue().trim() : "Roman";
                String theme = cbTheme.getValue() != null && !cbTheme.getValue().isBlank() ? cbTheme.getValue().trim() : "Vie quotidienne & École";
                String niveau = cbNiveau.getValue() != null && !cbNiveau.getValue().isBlank() ? cbNiveau.getValue().trim() : "Cycle 2 (CP-CE2)";
                String desc = taDescription.getText() != null && !taDescription.getText().isBlank() ? taDescription.getText().trim() : null;
                String coverUrl = tfCouvertureUrl.getText() != null && !tfCouvertureUrl.getText().isBlank() ? tfCouvertureUrl.getText().trim() : null;

                // Si aucune couverture n'est encore renseignée, résolution automatique immédiate
                // comme pour le scan ISBN, pour garantir la présence de la couverture dès l'ajout en base !
                if (coverUrl == null || coverUrl.isBlank()) {
                    try {
                        String autoC = metadataService.findVerifiedCover(code, "", cleanTitre, cleanAuteur);
                        if (autoC != null && !autoC.isBlank()) {
                            coverUrl = autoC;
                        }
                    } catch (Exception ignored) {}
                }

                Livre newLivre = new Livre(code, cleanTitre, cleanAuteur, cleanEditeur, coverUrl, genre, theme, niveau, desc);

                try {
                    Livre existing = livreDao.findByIsbn(code);
                    int nbCopies = spNbCopies.getValue();

                    if (existing != null) {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        MainApp.prepareDialog(alert);
                        alert.setTitle("Livre déjà présent dans le catalogue");
                        alert.setHeaderText("Le livre '" + existing.getTitre() + "' existe déjà sous le code " + code);
                        alert.setContentText("Cet ouvrage existe déjà en base.\n\n"
                                + "• Souhaitez-vous ajouter " + nbCopies + " exemplaire(s) supplémentaire(s) ?\n"
                                + "• Ou le consigner dans la Boîte à vérifier pour contrôler vos étagères ?");

                        ButtonType btnOui = new ButtonType("➕ Ajouter (+ " + nbCopies + ")", ButtonBar.ButtonData.YES);
                        ButtonType btnNon = new ButtonType("📦 Boîte à vérifier", ButtonBar.ButtonData.NO);
                        ButtonType btnAnnuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
                        alert.getButtonTypes().setAll(btnOui, btnNon, btnAnnuler);

                        Optional<ButtonType> rep = alert.showAndWait();
                        if (rep.isPresent() && rep.get() == btnOui) {
                            for (int i = 0; i < nbCopies; i++) {
                                exemplaireDao.insert(new Exemplaire(code, Statut.DISPONIBLE, LocalDate.now(), ""));
                            }
                            if (onLivreAdded != null) onLivreAdded.accept(existing);
                            SoundService.playSuccess();
                            return existing;
                        } else if (rep.isPresent() && rep.get() == btnNon && boiteVerificationDao != null) {
                            boiteVerificationDao.insert(new LivreAVerifier(
                                    code, existing.getTitre(), existing.getAuteur(),
                                    "Signalé lors de l'ajout manuel par titre", 1
                            ));
                            DialogHelper.showInfo("Boîte à vérifier", "Le livre '" + existing.getTitre() + "' a été placé dans la Boîte à vérifier.");
                            return null;
                        } else {
                            return null;
                        }
                    }

                    // Insertion normale d'un nouveau livre
                    livreDao.insert(newLivre);

                    // Insertion des exemplaires demandés
                    for (int i = 0; i < nbCopies; i++) {
                        exemplaireDao.insert(new Exemplaire(code, Statut.DISPONIBLE, LocalDate.now(), ""));
                    }

                    if (onLivreAdded != null) {
                        onLivreAdded.accept(newLivre);
                    }
                    SoundService.playSuccess();
                    return newLivre;

                } catch (SQLException ex) {
                    SoundService.playError();
                    DialogHelper.showError("Erreur d'enregistrement", "Impossible d'enregistrer le livre : " + ex.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait();
    }
}
