package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.MalletteDao;
import fr.ecole.bcd.model.Mallette;
import fr.ecole.bcd.model.MalletteItem;
import fr.ecole.bcd.model.RessourcePedagogique;
import fr.ecole.bcd.model.RessourcePedagogique.TypeRessource;
import fr.ecole.bcd.service.RessourcePedagogiqueService;
import fr.ecole.bcd.service.SoundService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * Boîte de dialogue modale et ergonomique pour la consultation, l'ouverture en 1 clic
 * et l'ajout de ressources pédagogiques (liens web Éduscol, fiches PDF, questionnaires).
 *
 * @author JC ART
 * @since 2026-11-16
 */
public final class RessourcesPedagogiquesDialog {

    private RessourcesPedagogiquesDialog() {}

    /**
     * Ouvre le gestionnaire de ressources pédagogiques pour un livre précis (par ISBN).
     */
    public static void openForLivre(String isbn, String titre, String auteur, Runnable onDataChanged) {
        showDialog(isbn, titre, auteur, null, onDataChanged);
    }

    /**
     * Ouvre le gestionnaire de ressources pédagogiques pour une mallette entière et ses ouvrages.
     */
    public static void openForMallette(Mallette mallette, Runnable onDataChanged) {
        showDialog(null, null, null, mallette, onDataChanged);
    }

    private static void showDialog(String isbn, String titreLivre, String auteurLivre, Mallette mallette, Runnable onDataChanged) {
        final boolean isMalletteMode = (mallette != null);
        final RessourcePedagogiqueService service = new RessourcePedagogiqueService();
        final MalletteDao malletteDao = new MalletteDao();

        Dialog<Void> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);

        String mainTitle = isMalletteMode
                ? "🧰 Ressources Pédagogiques – " + mallette.getTitre() + " (" + mallette.getCode() + ")"
                : "📚 Ressources Pédagogiques – " + (titreLivre != null ? titreLivre : "Ouvrage");

        dialog.setTitle(mainTitle);
        dialog.setHeaderText(null);

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Button closeBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeBtn.setText("Fermer");

        VBox rootBox = new VBox(14);
        rootBox.setPrefWidth(720);
        rootBox.setPrefHeight(620);
        rootBox.setPadding(new Insets(10));

        // ── 1. En-tête contextuel ─────────────────────────────────────────────
        VBox headerCard = new VBox(6);
        headerCard.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12 16;");

        HBox topTitleBox = new HBox(10);
        topTitleBox.setAlignment(Pos.CENTER_LEFT);

        Label lblBadge = new Label(isMalletteMode ? "🧰 MALLETTE" : "📖 LIVRE");
        lblBadge.setStyle("-fx-font-weight: 800; -fx-font-size: 11px; -fx-background-color: " + (isMalletteMode ? "#E0F2FE" : "#FEF3C7")
                + "; -fx-text-fill: " + (isMalletteMode ? "#0369A1" : "#B45309") + "; -fx-padding: 3 8; -fx-background-radius: 6;");

        Label lblTitreHeader = new Label(isMalletteMode ? mallette.getTitre() : titreLivre);
        lblTitreHeader.setStyle("-fx-font-weight: 800; -fx-font-size: 16px; -fx-text-fill: #0F172A;");
        lblTitreHeader.setWrapText(true);

        topTitleBox.getChildren().addAll(lblBadge, lblTitreHeader);

        Label lblSubHeader = new Label();
        lblSubHeader.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
        if (isMalletteMode) {
            lblSubHeader.setText("Thème : " + mallette.getTheme() + (mallette.getNiveau().isEmpty() ? "" : " • Niveau : " + mallette.getNiveau())
                    + " • Consultez et lancez en 1 clic les fiches d'exploitation et liens web de la mallette et de ses livres.");
        } else {
            lblSubHeader.setText("Auteur : " + (auteurLivre != null && !auteurLivre.isBlank() ? auteurLivre : "Non renseigné")
                    + " • ISBN : " + (isbn != null ? isbn : "–")
                    + " • Associez des questionnaires, liens Éduscol ou fiches d'activités à ce livre.");
        }
        lblSubHeader.setWrapText(true);

        headerCard.getChildren().addAll(topTitleBox, lblSubHeader);

        // ── 2. Liste des ressources existantes ────────────────────────────────
        HBox listTitleBar = new HBox(8);
        listTitleBar.setAlignment(Pos.CENTER_LEFT);
        Label lblSectionTitle = new Label("Ressources disponibles");
        lblSectionTitle.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #1E293B;");
        Label lblCount = new Label("(0)");
        lblCount.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B; -fx-font-weight: 600;");
        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        Label lblTipClick = new Label("💡 Cliquez sur '🚀 Ouvrir' pour lancer directement la page web ou le PDF");
        lblTipClick.setStyle("-fx-font-size: 11px; -fx-text-fill: #2563EB; -fx-font-style: italic;");
        listTitleBar.getChildren().addAll(lblSectionTitle, lblCount, spacer1, lblTipClick);

        VBox resourcesContainer = new VBox(8);
        resourcesContainer.setPadding(new Insets(4));

        ScrollPane scrollPane = new ScrollPane(resourcesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(250);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: #E2E8F0; -fx-border-radius: 6;");

        final ObservableList<RessourcePedagogique> currentRessources = FXCollections.observableArrayList();

        // Méthode de rechargement de la liste
        Runnable reloadList = () -> {
            try {
                List<RessourcePedagogique> fetched;
                if (isMalletteMode) {
                    fetched = service.getToutesRessourcesPourMallette(mallette.getId());
                } else {
                    fetched = service.getRessourcesParIsbn(isbn);
                }
                currentRessources.setAll(fetched);
                lblCount.setText("(" + fetched.size() + ")");

                resourcesContainer.getChildren().clear();

                if (fetched.isEmpty()) {
                    VBox emptyBox = new VBox(8);
                    emptyBox.setAlignment(Pos.CENTER);
                    emptyBox.setPadding(new Insets(30));
                    Label emptyIcon = new Label("📂");
                    emptyIcon.setStyle("-fx-font-size: 32px;");
                    Label emptyTitle = new Label("Aucune ressource pédagogique enregistrée");
                    emptyTitle.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #64748B;");
                    Label emptySub = new Label("Ajoutez un lien web (Éduscol, éditeur, vidéo) ou un document PDF ci-dessous.");
                    emptySub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
                    emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub);
                    resourcesContainer.getChildren().add(emptyBox);
                } else {
                    for (RessourcePedagogique r : fetched) {
                        HBox card = new HBox(12);
                        card.setAlignment(Pos.CENTER_LEFT);
                        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10 12;");

                        // Icône type
                        Label iconBox = new Label(r.getIcone());
                        iconBox.setStyle("-fx-font-size: 18px; -fx-background-color: "
                                + (r.isLienWeb() ? "#DBEAFE" : "#FCE7F3")
                                + "; -fx-padding: 8 10; -fx-background-radius: 8;");

                        // Infos
                        VBox textCol = new VBox(3);
                        HBox.setHgrow(textCol, Priority.ALWAYS);

                        HBox titleLine = new HBox(8);
                        titleLine.setAlignment(Pos.CENTER_LEFT);

                        Label lblResTitre = new Label(r.getTitre());
                        lblResTitre.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #0F172A;");

                        Label lblTypeBadge = new Label(r.getType().getLibelle());
                        lblTypeBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-padding: 2 6; -fx-background-radius: 4; -fx-background-color: "
                                + (r.isLienWeb() ? "#EFF6FF; -fx-text-fill: #1D4ED8;" : "#FDF2F8; -fx-text-fill: #BE185D;"));

                        titleLine.getChildren().addAll(lblResTitre, lblTypeBadge);

                        // Si en mode mallette et lié à un livre précis
                        if (isMalletteMode && r.getLivreTitre() != null && !r.getLivreTitre().isBlank()) {
                            Label lblLivreTag = new Label("📖 Livre : " + r.getLivreTitre());
                            lblLivreTag.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-padding: 1 6; -fx-background-radius: 4;");
                            titleLine.getChildren().add(lblLivreTag);
                        } else if (isMalletteMode && (r.getIsbn() == null || r.getIsbn().isBlank())) {
                            Label lblMalletteTag = new Label("🧰 Toute la mallette");
                            lblMalletteTag.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-background-color: #E0F2FE; -fx-text-fill: #0369A1; -fx-padding: 1 6; -fx-background-radius: 4;");
                            titleLine.getChildren().add(lblMalletteTag);
                        }

                        // Cible (URL ou nom de fichier)
                        String cibleAffichee = r.getUrlOuChemin();
                        if (r.isFichierLocal()) {
                            File f = new File(r.getUrlOuChemin());
                            cibleAffichee = "Fichier : " + f.getName();
                        }
                        Label lblCible = new Label(cibleAffichee);
                        lblCible.setStyle("-fx-font-size: 11px; -fx-font-family: monospace; -fx-text-fill: #64748B;");
                        lblCible.setMaxWidth(380);

                        textCol.getChildren().addAll(titleLine, lblCible);

                        if (r.getDescription() != null && !r.getDescription().isBlank()) {
                            Label lblDesc = new Label(r.getDescription());
                            lblDesc.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569; -fx-font-style: italic;");
                            lblDesc.setWrapText(true);
                            textCol.getChildren().add(lblDesc);
                        }

                        // Bouton Ouvrir en 1 clic
                        Button btnOuvrir = new Button("🚀 Ouvrir");
                        btnOuvrir.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand;");
                        btnOuvrir.setOnAction(ev -> {
                            try {
                                service.ouvrirRessource(r);
                                SoundService.playSuccess();
                            } catch (Exception ex) {
                                SoundService.playError();
                                DialogHelper.showError("Ouverture impossible", ex.getMessage());
                            }
                        });

                        // Bouton Supprimer
                        Button btnDelete = new Button("🗑️");
                        btnDelete.setStyle("-fx-background-color: transparent; -fx-text-fill: #DC2626; -fx-font-size: 12px; -fx-cursor: hand; -fx-padding: 6;");
                        btnDelete.setTooltip(new Tooltip("Supprimer cette ressource"));
                        btnDelete.setOnAction(ev -> {
                            boolean confirm = DialogHelper.showConfirmation("Suppression de la ressource",
                                    "Supprimer la ressource",
                                    "Voulez-vous vraiment supprimer la ressource '" + r.getTitre() + "' ?");
                            if (confirm) {
                                try {
                                    service.supprimerRessource(r.getId());
                                    SoundService.playSuccess();
                                    if (onDataChanged != null) onDataChanged.run();
                                } catch (SQLException ex) {
                                    DialogHelper.showError("Erreur de suppression", ex.getMessage());
                                }
                            }
                        });

                        card.getChildren().addAll(iconBox, textCol, btnOuvrir, btnDelete);
                        resourcesContainer.getChildren().add(card);
                    }
                }
            } catch (SQLException e) {
                DialogHelper.showError("Erreur", "Impossible de charger les ressources : " + e.getMessage());
            }
        };

        // Chargement initial
        reloadList.run();

        // ── 3. Formulaire d'ajout rapide ──────────────────────────────────────
        VBox addFormCard = new VBox(10);
        addFormCard.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #CBD5E1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12 14;");

        HBox formTitleBox = new HBox(8);
        formTitleBox.setAlignment(Pos.CENTER_LEFT);
        Label lblFormTitle = new Label("➕ Ajouter une ressource pédagogique");
        lblFormTitle.setStyle("-fx-font-weight: 800; -fx-font-size: 13px; -fx-text-fill: #0F172A;");
        formTitleBox.getChildren().add(lblFormTitle);

        // Type : Toggle Liens Web vs Fichier PDF
        ToggleGroup typeGroup = new ToggleGroup();
        RadioButton rbWeb = new RadioButton("🌐 Lien Web (Éduscol, site éditeur, vidéo)");
        rbWeb.setToggleGroup(typeGroup);
        rbWeb.setSelected(true);
        rbWeb.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

        RadioButton rbFile = new RadioButton("📄 Fichier PDF ou Document local");
        rbFile.setToggleGroup(typeGroup);
        rbFile.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");

        HBox typeSelectorBox = new HBox(16, rbWeb, rbFile);
        typeSelectorBox.setAlignment(Pos.CENTER_LEFT);

        // Champ Titre
        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de la ressource (ex: Fiche d'exploitation Gallimard, Questionnaire Rallye-lecture...)");

        // Champ Cible Web / Fichier
        TextField tfUrl = new TextField();
        tfUrl.setPromptText("Adresse du site web (ex: https://eduscol.education.fr/... ou babelio.com)");

        HBox filePickBox = new HBox(8);
        filePickBox.setAlignment(Pos.CENTER_LEFT);
        TextField tfFilePath = new TextField();
        tfFilePath.setPromptText("Sélectionnez un document sur votre ordinateur…");
        tfFilePath.setEditable(false);
        HBox.setHgrow(tfFilePath, Priority.ALWAYS);

        Button btnBrowse = new Button("📂 Parcourir…");
        btnBrowse.setStyle("-fx-font-size: 12px; -fx-cursor: hand;");
        final File[] selectedLocalFile = new File[1];

        btnBrowse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Sélectionner un document pédagogique");
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Tous les documents supportés", "*.pdf", "*.doc", "*.docx", "*.odt", "*.txt", "*.png", "*.jpg"),
                    new FileChooser.ExtensionFilter("Fichiers PDF (*.pdf)", "*.pdf"),
                    new FileChooser.ExtensionFilter("Documents Word (*.doc, *.docx)", "*.doc", "*.docx"),
                    new FileChooser.ExtensionFilter("Tous les fichiers", "*.*")
            );
            Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
            File picked = chooser.showOpenDialog(stage);
            if (picked != null) {
                selectedLocalFile[0] = picked;
                tfFilePath.setText(picked.getAbsolutePath());
                if (tfTitre.getText().isBlank()) {
                    String cleanName = picked.getName().replaceAll("\\.[^.]+$", "");
                    tfTitre.setText(cleanName);
                }
            }
        });

        filePickBox.getChildren().addAll(tfFilePath, btnBrowse);
        filePickBox.setVisible(false);
        filePickBox.setManaged(false);

        // Switch d'affichage Web vs Fichier
        typeGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            boolean isWeb = rbWeb.isSelected();
            tfUrl.setVisible(isWeb);
            tfUrl.setManaged(isWeb);
            filePickBox.setVisible(!isWeb);
            filePickBox.setManaged(!isWeb);
        });

        // Sélecteur d'ouvrage si en mode mallette
        HBox bookTargetBox = new HBox(8);
        bookTargetBox.setAlignment(Pos.CENTER_LEFT);
        Label lblLivreTarget = new Label("Associer à :");
        lblLivreTarget.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #475569;");
        ComboBox<ItemCible> cbBookTarget = new ComboBox<>();
        cbBookTarget.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cbBookTarget, Priority.ALWAYS);

        if (isMalletteMode) {
            cbBookTarget.getItems().add(new ItemCible(null, "🧰 Toute la mallette (général)"));
            try {
                List<MalletteItem> items = malletteDao.getItems(mallette.getId());
                for (MalletteItem it : items) {
                    cbBookTarget.getItems().add(new ItemCible(it.getIsbn(), "📖 " + it.getTitre() + " (#" + it.getExemplaireId() + ")"));
                }
            } catch (SQLException ignored) {}
            cbBookTarget.getSelectionModel().select(0);
            bookTargetBox.getChildren().addAll(lblLivreTarget, cbBookTarget);
        } else {
            bookTargetBox.setVisible(false);
            bookTargetBox.setManaged(false);
        }

        // Champ Description / Pistes
        TextField tfDescription = new TextField();
        tfDescription.setPromptText("Description courte ou niveau conseillé (ex: Cycle 3, questionnaire en 4 pages avec corrigé)");

        // Bouton Enregistrer
        HBox submitBox = new HBox(10);
        submitBox.setAlignment(Pos.CENTER_RIGHT);

        Label lblFeedback = new Label();
        lblFeedback.setStyle("-fx-font-size: 11px;");

        Button btnSave = new Button("💾 Enregistrer la ressource");
        btnSave.setStyle("-fx-background-color: #10B981; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 12px; -fx-padding: 7 14; -fx-background-radius: 6; -fx-cursor: hand;");

        btnSave.setOnAction(e -> {
            String titreInput = tfTitre.getText() != null ? tfTitre.getText().trim() : "";
            if (titreInput.isEmpty()) {
                lblFeedback.setText("⚠️ Veuillez indiquer un titre pour la ressource.");
                lblFeedback.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 600;");
                return;
            }

            boolean isWeb = rbWeb.isSelected();
            String pathOrUrl;
            TypeRessource typeR;

            if (isWeb) {
                pathOrUrl = tfUrl.getText() != null ? tfUrl.getText().trim() : "";
                if (pathOrUrl.isEmpty()) {
                    lblFeedback.setText("⚠️ Veuillez saisir une adresse URL (ex: https://...).");
                    lblFeedback.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 600;");
                    return;
                }
                typeR = TypeRessource.LIEN_WEB;
            } else {
                pathOrUrl = (selectedLocalFile[0] != null) ? selectedLocalFile[0].getAbsolutePath() : tfFilePath.getText().trim();
                if (pathOrUrl.isEmpty() || !new File(pathOrUrl).exists()) {
                    lblFeedback.setText("⚠️ Veuillez sélectionner un document existant.");
                    lblFeedback.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 600;");
                    return;
                }
                typeR = TypeRessource.detecterType(pathOrUrl);
            }

            String targetIsbn = isbn;
            Integer targetMalletteId = isMalletteMode ? mallette.getId() : null;

            if (isMalletteMode) {
                ItemCible selectedTarget = cbBookTarget.getValue();
                if (selectedTarget != null && selectedTarget.isbn != null && !selectedTarget.isbn.isBlank()) {
                    targetIsbn = selectedTarget.isbn;
                } else {
                    targetIsbn = null;
                }
            }

            try {
                service.ajouterRessource(targetIsbn, targetMalletteId, titreInput, typeR, pathOrUrl, tfDescription.getText());
                SoundService.playSuccess();

                // Réinitialiser le formulaire
                tfTitre.clear();
                tfUrl.clear();
                tfFilePath.clear();
                selectedLocalFile[0] = null;
                tfDescription.clear();
                lblFeedback.setText("✅ Ressource ajoutée avec succès !");
                lblFeedback.setStyle("-fx-text-fill: #15803D; -fx-font-weight: 600;");

                reloadList.run();
                if (onDataChanged != null) onDataChanged.run();

            } catch (Exception ex) {
                SoundService.playError();
                lblFeedback.setText("❌ Erreur : " + ex.getMessage());
                lblFeedback.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 600;");
            }
        });

        submitBox.getChildren().addAll(lblFeedback, btnSave);

        addFormCard.getChildren().addAll(formTitleBox, typeSelectorBox, tfTitre, tfUrl, filePickBox, bookTargetBox, tfDescription, submitBox);

        rootBox.getChildren().addAll(headerCard, listTitleBar, scrollPane, addFormCard);

        dialog.getDialogPane().setContent(rootBox);
        dialog.showAndWait();
    }

    private static class ItemCible {
        final String isbn;
        final String label;

        ItemCible(String isbn, String label) {
            this.isbn = isbn;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
