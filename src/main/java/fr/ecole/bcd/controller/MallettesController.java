package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.database.MalletteDao;
import fr.ecole.bcd.database.UtilisateurDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.Mallette;
import fr.ecole.bcd.model.MalletteItem;
import fr.ecole.bcd.service.EmpruntService.RegleMetierException;
import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.MalletteService;
import fr.ecole.bcd.service.RessourcePedagogiqueService;
import fr.ecole.bcd.service.SoundService;
import fr.ecole.bcd.view.AnimationHelper;
import fr.ecole.bcd.view.CoverHelper;
import fr.ecole.bcd.view.DialogHelper;
import fr.ecole.bcd.view.RessourcesPedagogiquesDialog;
import java.util.Optional;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contrôleur de l'onglet Mallettes Pédagogiques & Thématiques.
 */
public class MallettesController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // ── KPIs ─────────────────────────────────────────────────────────────
    @FXML private Label lblKpiTotal;
    @FXML private Label lblKpiDispo;
    @FXML private Label lblKpiPret;
    @FXML private Label lblKpiLivres;

    // ── Filtres & Recherche ──────────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private ComboBox<String> cbThemeFilter;
    @FXML private ComboBox<String> cbStatutFilter;
    @FXML private Button btnResetFilters;

    // ── Table des Mallettes (Gauche) ─────────────────────────────────────
    @FXML private Label lblCountMallettes;
    @FXML private TableView<Mallette> mallettesTable;
    @FXML private TableColumn<Mallette, String> colCode;
    @FXML private TableColumn<Mallette, String> colTitre;
    @FXML private TableColumn<Mallette, String> colTheme;
    @FXML private TableColumn<Mallette, String> colNiveau;
    @FXML private TableColumn<Mallette, Integer> colNbLivres;
    @FXML private TableColumn<Mallette, Mallette.Statut> colStatut;
    @FXML private TableColumn<Mallette, String> colEmprunteur;

    // ── Volet Détail (Droite) ────────────────────────────────────────────
    @FXML private VBox vboxEmptySelection;
    @FXML private VBox vboxDetailContent;

    @FXML private Label lblDetailCode;
    @FXML private Label lblDetailTitre;
    @FXML private Label lblDetailStatutBadge;
    @FXML private Label lblDetailThemeTag;
    @FXML private Label lblDetailNiveauTag;
    @FXML private Label lblDetailNbLivresTag;
    @FXML private Label lblDetailDescription;
    @FXML private HBox boxPretInfo;
    @FXML private Label lblDetailPretInfo;

    @FXML private Button btnPreterMallette;
    @FXML private Button btnRetournerMallette;
    @FXML private Button btnImprimerFiche;
    @FXML private Button btnImprimerEtiquette;
    @FXML private Button btnRessourcesMallette;
    @FXML private Button btnEditMallette;
    @FXML private Button btnDeleteMallette;

    // ── Livres dans la mallette ──────────────────────────────────────────
    @FXML private Label lblSectionItemsTitle;
    @FXML private TextField tfAddLivre;
    @FXML private Button btnAddLivre;
    @FXML private Label lblAddLivreFeedback;
    @FXML private Button btnFillTheme;
    @FXML private HBox boxMissingCopiesBanner;
    @FXML private Label lblMissingCopiesInfo;
    @FXML private Button btnIntegrateAllCopies;

    @FXML private TableView<MalletteItem> itemsTable;
    @FXML private TableColumn<MalletteItem, Integer> colItemExId;
    @FXML private TableColumn<MalletteItem, String> colItemTitre;
    @FXML private TableColumn<MalletteItem, String> colItemAuteur;
    @FXML private TableColumn<MalletteItem, String> colItemGenre;
    @FXML private TableColumn<MalletteItem, Exemplaire.Statut> colItemStatut;
    @FXML private TableColumn<MalletteItem, String> colItemCopies;
    @FXML private TableColumn<MalletteItem, Void> colItemRessources;
    @FXML private TableColumn<MalletteItem, Void> colItemAction;

    // ── Fiche Aperçu rapide du livre sélectionné ─────────────────────────
    @FXML private VBox cardBookPreview;
    @FXML private Label lblPreviewExemplaireId;
    @FXML private Label lblPreviewDispoBadge;
    @FXML private Button btnClosePreview;
    @FXML private StackPane previewCoverBox;
    @FXML private Label lblPreviewTitre;
    @FXML private Label lblPreviewAuteur;
    @FXML private Label lblPreviewGenre;
    @FXML private Label lblPreviewTheme;
    @FXML private Label lblPreviewNiveau;
    @FXML private Label lblPreviewIsbn;
    @FXML private Label lblPreviewDescription;
    @FXML private Label lblPreviewCopiesBadge;
    @FXML private Button btnPreviewAddOtherCopies;
    @FXML private Button btnPreviewRessources;
    @FXML private Button btnPreviewRetirer;
    @FXML private Button btnPreviewRetirerTous;

    // ── Services & DAOs ──────────────────────────────────────────────────
    private final MalletteService malletteService = new MalletteService();
    private final MalletteDao malletteDao = new MalletteDao();
    private final LivreDao livreDao = new LivreDao();
    private final UtilisateurDao utilisateurDao = new UtilisateurDao();
    private final RessourcePedagogiqueService ressourceService = new RessourcePedagogiqueService();
    private static final Map<String, Image> COVER_IMAGE_CACHE = new ConcurrentHashMap<>();

    private final ObservableList<Mallette> masterMallettes = FXCollections.observableArrayList();
    private FilteredList<Mallette> filteredMallettes;
    private final ObservableList<MalletteItem> currentItems = FXCollections.observableArrayList();

    private Mallette selectedMallette = null;
    private MalletteItem selectedBookItem = null;

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @FXML
    public void initialize() {
        configureTables();
        configureFilters();

        tfAddLivre.setOnAction(e -> handleAddLivre());

        loadData();
    }

    private void configureTables() {
        // Table des mallettes
        colCode.setCellValueFactory(new PropertyValueFactory<>("code"));
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colTheme.setCellValueFactory(new PropertyValueFactory<>("theme"));
        colNiveau.setCellValueFactory(new PropertyValueFactory<>("niveau"));
        colNbLivres.setCellValueFactory(new PropertyValueFactory<>("nombreLivres"));
        colStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));
        colEmprunteur.setCellValueFactory(cd -> new javafx.beans.property.SimpleStringProperty(cd.getValue().getEmprunteurComplet()));

        colNbLivres.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(val + " liv.");
                    setStyle("-fx-font-weight: 700; -fx-alignment: CENTER; -fx-text-fill: #475569;");
                }
            }
        });

        colStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Mallette.Statut statut, boolean empty) {
                super.updateItem(statut, empty);
                if (empty || statut == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(statut.getLibelle());
                    badge.getStyleClass().add(statut.getCssClass());
                    badge.setStyle("-fx-padding: 3 8; -fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: 700;");
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        filteredMallettes = new FilteredList<>(masterMallettes, p -> true);
        mallettesTable.setItems(filteredMallettes);

        mallettesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedMallette = newV;
            updateDetailView();
        });

        // Table des livres dans la mallette
        colItemExId.setCellValueFactory(new PropertyValueFactory<>("exemplaireId"));
        colItemTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colItemAuteur.setCellValueFactory(new PropertyValueFactory<>("auteur"));
        colItemGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colItemStatut.setCellValueFactory(new PropertyValueFactory<>("statutExemplaire"));

        colItemExId.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                } else {
                    MalletteItem item = (getIndex() >= 0 && getIndex() < getTableView().getItems().size())
                            ? getTableView().getItems().get(getIndex()) : null;
                    if (item != null && item.getIsbn() != null) {
                        try {
                            List<MalletteItem> allCopies = malletteDao.getItemsForIsbn(item.getMalletteId(), item.getIsbn());
                            if (allCopies.size() > 1) {
                                int rank = 1;
                                for (int i = 0; i < allCopies.size(); i++) {
                                    if (allCopies.get(i).getExemplaireId() == id) {
                                        rank = i + 1;
                                        break;
                                    }
                                }
                                setText("#" + id + " (" + rank + "/" + allCopies.size() + ")");
                            } else {
                                setText("#" + id);
                            }
                        } catch (Exception ex) {
                            setText("#" + id);
                        }
                    } else {
                        setText("#" + id);
                    }
                    setStyle("-fx-font-family: monospace; -fx-font-weight: 700; -fx-text-fill: #2563EB; -fx-alignment: CENTER;");
                }
            }
        });

        colItemStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Exemplaire.Statut statut, boolean empty) {
                super.updateItem(statut, empty);
                if (empty || statut == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(statut == Exemplaire.Statut.DISPONIBLE ? "En boîte" : "Prêté");
                    badge.setStyle(statut == Exemplaire.Statut.DISPONIBLE
                            ? "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4; -fx-font-weight: 700;"
                            : "-fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4; -fx-font-weight: 700;");
                    setGraphic(badge);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Colonne Nombre d'exemplaires dans la boîte
        if (colItemCopies != null) {
            colItemCopies.setCellValueFactory(cd -> {
                MalletteItem item = cd.getValue();
                if (item == null || item.getIsbn() == null) return new javafx.beans.property.SimpleStringProperty("");
                try {
                    int inMallette = malletteDao.countCopiesInMallette(item.getMalletteId(), item.getIsbn());
                    return new javafx.beans.property.SimpleStringProperty(String.valueOf(inMallette));
                } catch (Exception e) {
                    return new javafx.beans.property.SimpleStringProperty("1");
                }
            });
            colItemCopies.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String val, boolean empty) {
                    super.updateItem(val, empty);
                    if (empty || val == null || val.isBlank()) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        int inM = 1;
                        try { inM = Integer.parseInt(val); } catch (Exception ignored) {}
                        Label badge = new Label(inM > 1 ? "📦 " + inM + " ex." : "1 ex.");
                        if (inM > 1) {
                            badge.setStyle("-fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9; -fx-font-weight: 700; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4;");
                        } else {
                            badge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4;");
                        }
                        setGraphic(badge);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }

        // Colonne Ressources Pédagogiques
        if (colItemRessources != null) {
            colItemRessources.setCellFactory(col -> new TableCell<>() {
                private final Button btnRessource = new Button();
                {
                    btnRessource.setOnAction(e -> {
                        MalletteItem item = getTableView().getItems().get(getIndex());
                        if (item != null) {
                            RessourcesPedagogiquesDialog.openForLivre(
                                    item.getIsbn(), item.getTitre(), item.getAuteur(),
                                    () -> {
                                        refreshMalletteDetails();
                                        itemsTable.refresh();
                                    }
                            );
                        }
                    });
                }

                @Override
                protected void updateItem(Void val, boolean empty) {
                    super.updateItem(val, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        MalletteItem item = getTableView().getItems().get(getIndex());
                        if (item != null) {
                            try {
                                int count = ressourceService.compterRessourcesParIsbn(item.getIsbn());
                                if (count > 0) {
                                    btnRessource.setText("📎 " + count + " doc" + (count > 1 ? "s" : ""));
                                    btnRessource.setStyle("-fx-background-color: #E0F2FE; -fx-text-fill: #0369A1; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
                                } else {
                                    btnRessource.setText("➕ Ajouter");
                                    btnRessource.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748B; -fx-font-size: 11px; -fx-padding: 2 4; -fx-cursor: hand;");
                                }
                            } catch (Exception ex) {
                                btnRessource.setText("📎 0");
                            }
                            setGraphic(btnRessource);
                            setAlignment(Pos.CENTER);
                        } else {
                            setGraphic(null);
                        }
                    }
                }
            });
        }

        // Colonne Action (Retirer)
        colItemAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("❌ Retirer");
            {
                btnRemove.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; -fx-text-fill: #DC2626; -fx-cursor: hand; -fx-padding: 2 6;");
                btnRemove.setOnAction(e -> {
                    MalletteItem item = (MalletteItem) getTableRow().getItem();
                    if (item == null) {
                        int idx = getIndex();
                        if (idx >= 0 && idx < getTableView().getItems().size()) {
                            item = getTableView().getItems().get(idx);
                        }
                    }
                    if (item != null && selectedMallette != null) {
                        demanderEtRetirerLivre(item);
                    }
                });
            }

            @Override
            protected void updateItem(Void val, boolean empty) {
                super.updateItem(val, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    setGraphic(btnRemove);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        itemsTable.setItems(currentItems);

        // Clip d'arrondi sur la boîte de couverture
        if (previewCoverBox != null) {
            Rectangle clip = new Rectangle(88, 120);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            previewCoverBox.setClip(clip);
        }

        // Clic / Sélection d'un livre dans la mallette -> affichage de l'aperçu complet
        itemsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            showBookPreview(newV);
        });

        // Clic simple = mise à jour immédiate de l'aperçu, Double-clic = ouverture des ressources pédagogiques
        itemsTable.setRowFactory(tv -> {
            TableRow<MalletteItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()) {
                    MalletteItem item = row.getItem();
                    if (event.getClickCount() == 1) {
                        showBookPreview(item);
                    } else if (event.getClickCount() == 2) {
                        if (item != null) {
                            RessourcesPedagogiquesDialog.openForLivre(item.getIsbn(), item.getTitre(), item.getAuteur(), () -> {
                                refreshMalletteDetails();
                                itemsTable.refresh();
                                showBookPreview(item);
                            });
                        }
                    }
                }
            });
            return row;
        });
    }

    private void configureFilters() {
        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
        cbThemeFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilters());
        cbStatutFilter.valueProperty().addListener((obs, oldV, newV) -> applyFilters());

        cbStatutFilter.getItems().setAll("Tous", "DISPONIBLE", "EMPRUNTE");
        cbStatutFilter.setValue("Tous");
    }

    private void applyFilters() {
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String theme = cbThemeFilter.getValue();
        String statut = cbStatutFilter.getValue();

        filteredMallettes.setPredicate(m -> {
            if (theme != null && !theme.equalsIgnoreCase("Tous") && !theme.equalsIgnoreCase(m.getTheme())) {
                return false;
            }
            if (statut != null && !statut.equalsIgnoreCase("Tous") && !m.getStatut().name().equalsIgnoreCase(statut)) {
                return false;
            }
            if (!q.isEmpty()) {
                return m.getCode().toLowerCase().contains(q)
                        || m.getTitre().toLowerCase().contains(q)
                        || m.getTheme().toLowerCase().contains(q)
                        || (m.getDescription() != null && m.getDescription().toLowerCase().contains(q))
                        || m.getEmprunteurComplet().toLowerCase().contains(q);
            }
            return true;
        });

        lblCountMallettes.setText(filteredMallettes.size() + " mallette(s)");
    }

    // =====================================================================
    //  Chargement des données
    // =====================================================================

    public void loadData() {
        try {
            List<Mallette> list = malletteDao.findAll();
            masterMallettes.setAll(list);

            // Charger les thèmes disponibles dans le filtre
            List<String> themes = malletteDao.getAllThemes();
            themes.add(0, "Tous");
            cbThemeFilter.getItems().setAll(themes);
            if (cbThemeFilter.getValue() == null) cbThemeFilter.setValue("Tous");

            updateKpis(list);
            applyFilters();

            // Si aucune sélection et liste non vide, sélectionner la première
            if (!masterMallettes.isEmpty() && selectedMallette == null) {
                mallettesTable.getSelectionModel().select(0);
            } else if (selectedMallette != null) {
                loadDataKeepSelection();
            } else {
                updateDetailView();
            }

        } catch (SQLException e) {
            showError("Erreur de chargement", e.getMessage());
        }
    }

    private void loadDataKeepSelection() {
        int currentId = selectedMallette != null ? selectedMallette.getId() : -1;
        try {
            List<Mallette> list = malletteDao.findAll();
            masterMallettes.setAll(list);
            updateKpis(list);

            if (currentId > 0) {
                for (Mallette m : masterMallettes) {
                    if (m.getId() == currentId) {
                        mallettesTable.getSelectionModel().select(m);
                        selectedMallette = m;
                        break;
                    }
                }
            }
            updateDetailView();
        } catch (SQLException e) {
            showError("Erreur", e.getMessage());
        }
    }

    private void updateKpis(List<Mallette> list) {
        int total = list.size();
        long dispo = list.stream().filter(Mallette::isDisponible).count();
        long pret = list.stream().filter(Mallette::isEmprunte).count();
        int totalLivres = list.stream().mapToInt(Mallette::getNombreLivres).sum();

        lblKpiTotal.setText(String.valueOf(total));
        lblKpiDispo.setText(String.valueOf(dispo));
        lblKpiPret.setText(String.valueOf(pret));
        lblKpiLivres.setText(String.valueOf(totalLivres));
    }

    private void updateDetailView() {
        if (selectedMallette == null) {
            vboxEmptySelection.setVisible(true);
            vboxEmptySelection.setManaged(true);
            vboxDetailContent.setVisible(false);
            vboxDetailContent.setManaged(false);
            currentItems.clear();
            showBookPreview(null);
            return;
        }

        vboxEmptySelection.setVisible(false);
        vboxEmptySelection.setManaged(false);
        vboxDetailContent.setVisible(true);
        vboxDetailContent.setManaged(true);
        showBookPreview(null);

        lblDetailCode.setText(selectedMallette.getCode());
        lblDetailTitre.setText(selectedMallette.getTitre());
        lblDetailThemeTag.setText("Thème : " + selectedMallette.getTheme());
        lblDetailNiveauTag.setText("Niveau : " + (selectedMallette.getNiveau().isEmpty() ? "Tous niveaux" : selectedMallette.getNiveau()));
        lblDetailDescription.setText(selectedMallette.getDescription().isEmpty() ? "Aucune note pédagogique particulière." : selectedMallette.getDescription());

        // Statut & Badge
        lblDetailStatutBadge.setText(selectedMallette.getStatut().getLibelle());
        lblDetailStatutBadge.getStyleClass().setAll(selectedMallette.getStatut().getCssClass());
        lblDetailStatutBadge.setStyle("-fx-padding: 3 8; -fx-background-radius: 12; -fx-font-size: 11px; -fx-font-weight: 700;");

        // Emprunt
        if (selectedMallette.isEmprunte()) {
            boxPretInfo.setVisible(true);
            boxPretInfo.setManaged(true);
            lblDetailPretInfo.setText("Empruntée par " + selectedMallette.getEmprunteurComplet()
                    + " le " + selectedMallette.getDateEmpruntFormatted()
                    + " (retour prévu : " + selectedMallette.getDateRetourPrevueFormatted() + ")");
            btnPreterMallette.setVisible(false);
            btnPreterMallette.setManaged(false);
            btnRetournerMallette.setVisible(true);
            btnRetournerMallette.setManaged(true);
        } else {
            boxPretInfo.setVisible(false);
            boxPretInfo.setManaged(false);
            btnPreterMallette.setVisible(true);
            btnPreterMallette.setManaged(true);
            btnRetournerMallette.setVisible(false);
            btnRetournerMallette.setManaged(false);
        }

        btnFillTheme.setText("🪄 Remplir avec les livres du thème \"" + selectedMallette.getTheme() + "\"");

        refreshMalletteDetails();
    }

    private void refreshMalletteDetails() {
        if (selectedMallette == null) return;
        try {
            List<MalletteItem> items = malletteDao.getItems(selectedMallette.getId());
            currentItems.setAll(items);
            int distinctTitles = malletteDao.countDistinctTitles(selectedMallette.getId());
            if (distinctTitles != items.size() && items.size() > 0) {
                lblDetailNbLivresTag.setText(items.size() + " exemplaires (" + distinctTitles + " titres)");
                if (lblSectionItemsTitle != null) {
                    lblSectionItemsTitle.setText("Livres contenus dans la mallette (" + items.size() + " exemplaires — " + distinctTitles + " titres)");
                }
            } else {
                lblDetailNbLivresTag.setText(items.size() + " livre" + (items.size() > 1 ? "s" : ""));
                if (lblSectionItemsTitle != null) {
                    lblSectionItemsTitle.setText("Livres contenus dans la mallette (" + items.size() + " livre" + (items.size() > 1 ? "s" : "") + ")");
                }
            }
            btnPreterMallette.setDisable(items.isEmpty());

            if (btnRessourcesMallette != null) {
                int totalRessources = ressourceService.compterRessourcesPourMallette(selectedMallette.getId());
                btnRessourcesMallette.setText("📚 Ressources" + (totalRessources > 0 ? " (" + totalRessources + ")" : ""));
            }

            // Détecter si des exemplaires supplémentaires de ces livres existent en BCD
            List<Exemplaire> missingCopies = malletteDao.findAvailableCopiesNotInMallette(selectedMallette.getId());
            if (boxMissingCopiesBanner != null) {
                if (!missingCopies.isEmpty()) {
                    boxMissingCopiesBanner.setVisible(true);
                    boxMissingCopiesBanner.setManaged(true);
                    if (lblMissingCopiesInfo != null) {
                        lblMissingCopiesInfo.setText("💡 " + missingCopies.size() + " exemplaire(s) supplémentaire(s) de ces ouvrages sont disponibles en BCD.");
                    }
                    if (btnIntegrateAllCopies != null) {
                        btnIntegrateAllCopies.setText("➕ Tout intégrer dans la boîte (+" + missingCopies.size() + " ex.)");
                    }
                } else {
                    boxMissingCopiesBanner.setVisible(false);
                    boxMissingCopiesBanner.setManaged(false);
                }
            }

            if (!items.isEmpty()) {
                if (selectedBookItem == null) {
                    itemsTable.getSelectionModel().select(0);
                    showBookPreview(items.get(0));
                } else {
                    Optional<MalletteItem> stillThere = items.stream()
                            .filter(it -> it.getExemplaireId() == selectedBookItem.getExemplaireId())
                            .findFirst();
                    showBookPreview(stillThere.orElse(items.get(0)));
                }
            } else {
                showBookPreview(null);
            }
        } catch (SQLException e) {
            showError("Erreur de chargement des livres", e.getMessage());
        }
    }

    @FXML
    private void handleRessourcesMallette() {
        if (selectedMallette == null) return;
        RessourcesPedagogiquesDialog.openForMallette(selectedMallette, () -> {
            refreshMalletteDetails();
            itemsTable.refresh();
        });
    }

    // =====================================================================
    //  Fiche Aperçu rapide du livre sélectionné dans la mallette
    // =====================================================================

    private void showBookPreview(MalletteItem item) {
        if (cardBookPreview == null) return;
        selectedBookItem = item;

        if (item == null) {
            cardBookPreview.setVisible(false);
            cardBookPreview.setManaged(false);
            return;
        }

        cardBookPreview.setVisible(true);
        cardBookPreview.setManaged(true);

        lblPreviewExemplaireId.setText("#" + item.getExemplaireId());

        boolean dispo = item.getStatutExemplaire() == Exemplaire.Statut.DISPONIBLE;
        lblPreviewDispoBadge.setText(dispo ? "En boîte" : "Prêté");
        lblPreviewDispoBadge.setStyle(dispo
                ? "-fx-background-color: #DCFCE7; -fx-text-fill: #15803D; -fx-font-size: 10px; -fx-padding: 1 6; -fx-background-radius: 4; -fx-font-weight: 700;"
                : "-fx-background-color: #FEF3C7; -fx-text-fill: #B45309; -fx-font-size: 10px; -fx-padding: 1 6; -fx-background-radius: 4; -fx-font-weight: 700;");

        lblPreviewTitre.setText(item.getTitre());
        lblPreviewAuteur.setText(item.getAuteur() != null && !item.getAuteur().isBlank()
                ? item.getAuteur() : "Auteur non renseigné");

        String genre = item.getGenre() != null && !item.getGenre().isBlank() ? item.getGenre() : "–";
        lblPreviewGenre.setText(genre);
        lblPreviewGenre.getStyleClass().setAll("badge-genre", CoverHelper.getStyleForGenre(genre).badgeClass());

        String theme = item.getTheme() != null && !item.getTheme().isBlank() ? item.getTheme() : "–";
        lblPreviewTheme.setText(theme);

        Livre livre = null;
        try {
            livre = livreDao.findByIsbn(item.getIsbn());
        } catch (SQLException ignored) {}

        if (livre != null) {
            lblPreviewNiveau.setText(livre.getNiveau() != null && !livre.getNiveau().isBlank() ? livre.getNiveau() : "–");
            lblPreviewIsbn.setText("ISBN : " + (livre.getIsbn() != null ? livre.getIsbn() : item.getIsbn()));
            lblPreviewDescription.setText(livre.getDescription() != null && !livre.getDescription().isBlank()
                    ? livre.getDescription() : "Aucun résumé renseigné.");
            loadPreviewCover(livre);
        } else {
            lblPreviewNiveau.setText("–");
            lblPreviewIsbn.setText("ISBN : " + item.getIsbn());
            lblPreviewDescription.setText("Aucun résumé renseigné.");
            Livre fakeLivre = new Livre(item.getIsbn(), item.getTitre(), item.getAuteur(), "", "", item.getGenre(), item.getTheme(), "", "");
            loadPreviewCover(fakeLivre);
        }

        try {
            int count = ressourceService.compterRessourcesParIsbn(item.getIsbn());
            btnPreviewRessources.setText("📚 Ressources pédagogiques (" + count + ")");
        } catch (Exception ex) {
            btnPreviewRessources.setText("📚 Ressources pédagogiques (0)");
        }

        // Statut d'exemplaires dans la mallette vs restants en BCD
        int inMallette = 1;
        List<Exemplaire> otherAvailable = List.of();
        try {
            inMallette = malletteDao.countCopiesInMallette(item.getMalletteId(), item.getIsbn());
            otherAvailable = malletteDao.findAvailableCopiesForIsbnNotInMallette(item.getMalletteId(), item.getIsbn());
        } catch (Exception ignored) {}

        if (lblPreviewCopiesBadge != null) {
            if (inMallette > 1 && otherAvailable.isEmpty()) {
                lblPreviewCopiesBadge.setText("📦 " + inMallette + " exemplaires dans la boîte (série complète)");
                lblPreviewCopiesBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9; -fx-padding: 3 8; -fx-background-radius: 6;");
            } else if (!otherAvailable.isEmpty()) {
                lblPreviewCopiesBadge.setText("📦 " + inMallette + " ex. dans la boîte (" + otherAvailable.size() + " autre(s) dispo en BCD)");
                lblPreviewCopiesBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-padding: 3 8; -fx-background-radius: 6;");
            } else {
                lblPreviewCopiesBadge.setText("📦 1 exemplaire (exemplaire unique)");
                lblPreviewCopiesBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-background-color: #F1F5F9; -fx-text-fill: #334155; -fx-padding: 3 8; -fx-background-radius: 6;");
            }
        }

        if (btnPreviewAddOtherCopies != null) {
            if (!otherAvailable.isEmpty()) {
                btnPreviewAddOtherCopies.setText("➕ Ajouter les " + otherAvailable.size() + " autre(s) exemplaire(s)");
                btnPreviewAddOtherCopies.setVisible(true);
                btnPreviewAddOtherCopies.setManaged(true);
            } else {
                btnPreviewAddOtherCopies.setVisible(false);
                btnPreviewAddOtherCopies.setManaged(false);
            }
        }

        if (btnPreviewRetirer != null) {
            btnPreviewRetirer.setText("❌ Retirer cet exemplaire (#" + item.getExemplaireId() + ")");
        }

        if (btnPreviewRetirerTous != null) {
            if (inMallette > 1) {
                btnPreviewRetirerTous.setText("🗑️ Retirer tous les " + inMallette + " exemplaires");
                btnPreviewRetirerTous.setVisible(true);
                btnPreviewRetirerTous.setManaged(true);
            } else {
                btnPreviewRetirerTous.setVisible(false);
                btnPreviewRetirerTous.setManaged(false);
            }
        }
    }

    private void loadPreviewCover(Livre livre) {
        if (previewCoverBox == null) return;
        previewCoverBox.getChildren().clear();

        // 1. Appliquer le clip arrondi sur la boîte conteneur
        Rectangle clip = new Rectangle(88, 120);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        previewCoverBox.setClip(clip);

        // 2. Afficher immédiatement la couverture vectorielle de qualité Apple Books (JAMAIS de vide !)
        StackPane placeholderCover = CoverHelper.createStyledCover(livre, 88, 120);
        previewCoverBox.getChildren().setAll(placeholderCover);

        // 3. Charger l'image réelle si une URL valide existe
        String url = (livre != null) ? livre.getCouvertureUrl() : null;
        if (url != null && !url.isBlank()) {
            Image cached = COVER_IMAGE_CACHE.get(url);
            if (cached != null) {
                if (!cached.isError()) {
                    ImageView iv = new ImageView(cached);
                    iv.setFitWidth(88);
                    iv.setFitHeight(120);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    previewCoverBox.getChildren().setAll(iv);
                }
            } else {
                try {
                    Image img = new Image(url, true);
                    img.progressProperty().addListener((obs, oldV, progress) -> {
                        if (progress.doubleValue() >= 1.0 && !img.isError()) {
                            COVER_IMAGE_CACHE.put(url, img);
                            Platform.runLater(() -> {
                                if (selectedBookItem != null && livre != null && livre.getIsbn().equals(selectedBookItem.getIsbn())) {
                                    ImageView iv = new ImageView(img);
                                    iv.setFitWidth(88);
                                    iv.setFitHeight(120);
                                    iv.setPreserveRatio(true);
                                    iv.setSmooth(true);
                                    previewCoverBox.getChildren().setAll(iv);
                                }
                            });
                        }
                    });
                    img.errorProperty().addListener((obs, oldV, hasErr) -> {
                        if (hasErr) {
                            COVER_IMAGE_CACHE.put(url, img);
                        }
                    });
                } catch (Exception ignored) {}
            }
        }
    }

    @FXML
    private void handleCloseBookPreview() {
        itemsTable.getSelectionModel().clearSelection();
        showBookPreview(null);
    }

    @FXML
    private void handlePreviewRessources() {
        if (selectedBookItem == null) return;
        RessourcesPedagogiquesDialog.openForLivre(
                selectedBookItem.getIsbn(), selectedBookItem.getTitre(), selectedBookItem.getAuteur(),
                () -> {
                    refreshMalletteDetails();
                    itemsTable.refresh();
                    showBookPreview(selectedBookItem);
                }
        );
    }

    @FXML
    private void handlePreviewRetirer() {
        if (selectedBookItem != null && selectedMallette != null) {
            demanderEtRetirerLivre(selectedBookItem);
        }
    }

    /**
     * Gère la confirmation et le retrait d'un livre de la mallette.
     * Si l'ouvrage est présent en plusieurs exemplaires dans la boîte,
     * l'utilisateur peut choisir de retirer tous les exemplaires ou seulement celui-ci.
     */
    private void demanderEtRetirerLivre(MalletteItem item) {
        if (item == null || selectedMallette == null) return;

        long countSameBook = currentItems.stream()
                .filter(it -> it.getIsbn() != null && it.getIsbn().equals(item.getIsbn()))
                .count();

        if (countSameBook > 1) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            MainApp.prepareDialog(alert);
            alert.setTitle("Retirer l'ouvrage");
            alert.setHeaderText("Exemplaires multiples détectés (" + countSameBook + " exemplaires)");
            alert.setContentText("L'ouvrage '" + item.getTitre() + "' est présent en " + countSameBook + " exemplaires dans cette mallette.\n\n"
                    + "Souhaitez-vous retirer tous les exemplaires de ce livre ou seulement l'exemplaire #" + item.getExemplaireId() + " ?");

            ButtonType btnTous = new ButtonType("Tous les exemplaires (" + countSameBook + ")");
            ButtonType btnSeul = new ButtonType("Cet exemplaire seul (#" + item.getExemplaireId() + ")");
            ButtonType btnAnnuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnTous, btnSeul, btnAnnuler);
            var result = alert.showAndWait();

            if (result.isPresent() && result.get() == btnTous) {
                try {
                    malletteService.retirerTousExemplairesLivre(selectedMallette.getId(), item.getIsbn());
                    selectedBookItem = null;
                    showBookPreview(null);
                    refreshMalletteDetails();
                    loadDataKeepSelection();
                } catch (SQLException ex) {
                    showError("Erreur", ex.getMessage());
                }
            } else if (result.isPresent() && result.get() == btnSeul) {
                try {
                    malletteService.retirerLivre(selectedMallette.getId(), item.getExemplaireId());
                    selectedBookItem = null;
                    showBookPreview(null);
                    refreshMalletteDetails();
                    loadDataKeepSelection();
                } catch (SQLException ex) {
                    showError("Erreur", ex.getMessage());
                }
            }
        } else {
            boolean confirm = DialogHelper.showConfirmation(
                    "Retirer l'ouvrage",
                    "Retirer de la mallette",
                    "Voulez-vous retirer '" + item.getTitre() + "' (ex. #" + item.getExemplaireId() + ") de cette mallette ?"
            );
            if (confirm) {
                try {
                    malletteService.retirerLivre(selectedMallette.getId(), item.getExemplaireId());
                    selectedBookItem = null;
                    showBookPreview(null);
                    refreshMalletteDetails();
                    loadDataKeepSelection();
                } catch (SQLException ex) {
                    showError("Erreur", ex.getMessage());
                }
            }
        }
    }

    // =====================================================================
    //  ACTIONS UTILISATEUR
    // =====================================================================

    @FXML
    public void handleResetFilters() {
        searchField.clear();
        cbThemeFilter.setValue("Tous");
        cbStatutFilter.setValue("Tous");
    }

    @FXML
    public void handleCreateMallette() {
        Dialog<Mallette> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Créer une nouvelle Mallette Thématique");
        dialog.setHeaderText("Rassemblez des ouvrages autour d'un thème pédagogique. Le scan des livres est obligatoire.");

        ButtonType btnSave = new ButtonType("Créer la mallette", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        VBox mainBox = new VBox(14);
        mainBox.setPadding(new Insets(16, 20, 14, 20));
        mainBox.setPrefWidth(680);

        // 1. Informations générales de la mallette
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField tfTitre = new TextField();
        tfTitre.setPromptText("ex: Harcèlement scolaire & Vivre ensemble");
        tfTitre.setPrefWidth(380);

        ComboBox<String> cbTheme = new ComboBox<>();
        cbTheme.setEditable(true);
        try {
            cbTheme.getItems().setAll(malletteDao.getAllThemes());
            cbTheme.setValue("Harcèlement & Vivre ensemble");
        } catch (SQLException ignored) {
            cbTheme.getItems().addAll(LivreClassificationService.THEMES_STANDARDS);
            cbTheme.setValue("Harcèlement & Vivre ensemble");
        }
        cbTheme.setPrefWidth(380);

        ComboBox<String> cbNiveau = new ComboBox<>();
        cbNiveau.getItems().setAll(LivreClassificationService.NIVEAUX_STANDARDS);
        cbNiveau.setValue("Cycle 3 (CM1-CM2)");
        cbNiveau.setPrefWidth(380);

        TextArea taDesc = new TextArea();
        taDesc.setPromptText("Objectifs pédagogiques, projet de classe, thématiques abordées…");
        taDesc.setPrefRowCount(2);
        taDesc.setWrapText(true);

        grid.add(new Label("Titre de la mallette : *"), 0, 0);
        grid.add(tfTitre, 1, 0);
        grid.add(new Label("Thème pédagogique : *"), 0, 1);
        grid.add(cbTheme, 1, 1);
        grid.add(new Label("Niveau scolaire :"), 0, 2);
        grid.add(cbNiveau, 1, 2);
        grid.add(new Label("Description / Projet :"), 0, 3);
        grid.add(taDesc, 1, 3);

        // 2. Section Scan Obligatoire
        VBox scanBox = new VBox(8);
        scanBox.setStyle("-fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 12;");

        Label lblScanHeader = new Label("📚 Livres obligatoires dans la mallette (minimum 1 requis) :");
        lblScanHeader.setStyle("-fx-font-weight: 800; -fx-font-size: 13px; -fx-text-fill: #1e3a8a;");

        Label lblScanSub = new Label("Scannez à la douchette ou tapez le nom/ISBN. Les livres hors BCD sont automatiquement catalogués !");
        lblScanSub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        HBox scanRow = new HBox(8);
        scanRow.setAlignment(Pos.CENTER_LEFT);

        TextField tfScan = new TextField();
        tfScan.setPromptText("Scannez un code-barres ISBN ou tapez un titre de livre...");
        HBox.setHgrow(tfScan, Priority.ALWAYS);

        Button btnAddScan = new Button("➕ Ajouter");
        btnAddScan.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");

        scanRow.getChildren().addAll(tfScan, btnAddScan);

        Label lblFeedback = new Label("Scannez le premier livre pour activer la création de la mallette...");
        lblFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b; -fx-font-style: italic;");

        ObservableList<MalletteService.LivreScanPreview> scannedList = FXCollections.observableArrayList();
        TableView<MalletteService.LivreScanPreview> tvScanned = new TableView<>(scannedList);
        tvScanned.setPrefHeight(150);
        tvScanned.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<MalletteService.LivreScanPreview, String> colTitreItem = new TableColumn<>("Titre de l'ouvrage");
        colTitreItem.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colTitreItem.setPrefWidth(240);

        TableColumn<MalletteService.LivreScanPreview, String> colAuteurItem = new TableColumn<>("Auteur");
        colAuteurItem.setCellValueFactory(new PropertyValueFactory<>("auteur"));
        colAuteurItem.setPrefWidth(140);

        TableColumn<MalletteService.LivreScanPreview, String> colIsbnItem = new TableColumn<>("ISBN / Code");
        colIsbnItem.setCellValueFactory(new PropertyValueFactory<>("isbnOuCode"));
        colIsbnItem.setPrefWidth(120);

        TableColumn<MalletteService.LivreScanPreview, String> colDetailsItem = new TableColumn<>("Origine");
        colDetailsItem.setCellValueFactory(new PropertyValueFactory<>("details"));
        colDetailsItem.setPrefWidth(140);

        TableColumn<MalletteService.LivreScanPreview, Void> colRemoveItem = new TableColumn<>("");
        colRemoveItem.setPrefWidth(45);
        colRemoveItem.setCellFactory(c -> new TableCell<>() {
            private final Button btnDel = new Button("❌");
            {
                btnDel.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-text-fill: #DC2626; -fx-font-size: 11px;");
                btnDel.setOnAction(e -> {
                    MalletteService.LivreScanPreview item = getTableView().getItems().get(getIndex());
                    scannedList.remove(item);
                    lblFeedback.setText("Livre retiré : " + item.getTitre());
                    lblFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #b45309;");
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDel);
                setAlignment(Pos.CENTER);
            }
        });

        tvScanned.getColumns().addAll(colTitreItem, colAuteurItem, colIsbnItem, colDetailsItem, colRemoveItem);

        Label lblCount = new Label("Total : 0 livre (minimum 1 requis pour créer la mallette)");
        lblCount.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #dc2626;");

        scanBox.getChildren().addAll(lblScanHeader, lblScanSub, scanRow, lblFeedback, tvScanned, lblCount);
        mainBox.getChildren().addAll(grid, scanBox);
        dialog.getDialogPane().setContent(mainBox);

        // Validation dynamique du bouton Créer
        Runnable updateState = () -> {
            int count = scannedList.size();
            boolean valid = count >= 1;
            Node btn = dialog.getDialogPane().lookupButton(btnSave);
            if (btn != null) btn.setDisable(!valid);
            if (valid) {
                lblCount.setText("Total : " + count + " livre" + (count > 1 ? "s" : "") + " scanné" + (count > 1 ? "s" : ""));
                lblCount.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #15803d;");
            } else {
                lblCount.setText("Total : 0 livre (minimum 1 requis pour créer la mallette)");
                lblCount.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: #dc2626;");
            }
        };

        scannedList.addListener((javafx.collections.ListChangeListener<MalletteService.LivreScanPreview>) c -> updateState.run());

        // Action de scan (douchette Enter ou clic Ajouter)
        Runnable doAddScan = () -> {
            String input = tfScan.getText().trim();
            if (input.isEmpty()) return;
            try {
                MalletteService.LivreScanPreview preview = malletteService.inspecterScan(input, cbTheme.getValue(), cbNiveau.getValue());
                scannedList.add(preview);
                SoundService.playSuccess();
                AnimationHelper.flashSuccess(tfScan);
                lblFeedback.setText("✅ " + preview.getTitre() + " (" + preview.getDetails() + ")");
                lblFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #15803d; -fx-font-weight: bold;");
                tfScan.clear();
                tfScan.requestFocus();
            } catch (RegleMetierException | SQLException ex) {
                SoundService.playError();
                AnimationHelper.flashError(tfScan);
                lblFeedback.setText("⚠️ " + ex.getMessage());
                lblFeedback.setStyle("-fx-font-size: 11px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                tfScan.selectAll();
                tfScan.requestFocus();
            }
        };

        btnAddScan.setOnAction(e -> doAddScan.run());
        tfScan.setOnAction(e -> doAddScan.run());

        Platform.runLater(() -> {
            updateState.run();
            tfTitre.requestFocus();
        });

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnSave) {
                if (scannedList.isEmpty()) {
                    showError("Livre obligatoire", "Vous devez scanner ou ajouter au moins un livre dans la mallette.");
                    return null;
                }
                try {
                    List<String> rawInputs = scannedList.stream().map(MalletteService.LivreScanPreview::getRawInput).toList();
                    return malletteService.creerMalletteAvecLivres(
                            tfTitre.getText(),
                            cbTheme.getValue(),
                            cbNiveau.getValue(),
                            taDesc.getText(),
                            rawInputs
                    );
                } catch (RegleMetierException | SQLException e) {
                    showError("Impossible de créer la mallette", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(newMallette -> {
            loadData();
            for (Mallette m : masterMallettes) {
                if (m.getId() == newMallette.getId()) {
                    mallettesTable.getSelectionModel().select(m);
                    break;
                }
            }
            showInfo("Mallette créée", "La mallette '" + newMallette.getTitre() + "' (" + newMallette.getCode() + ") a été créée avec " + scannedList.size() + " livre(s) !");
        });
    }

    @FXML
    public void handleEditMallette() {
        if (selectedMallette == null) return;

        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Modifier la mallette " + selectedMallette.getCode());
        dialog.setHeaderText("Modifiez les informations de la mallette pédagogique.");

        ButtonType btnSave = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 10, 10));

        TextField tfCode = new TextField(selectedMallette.getCode());
        TextField tfTitre = new TextField(selectedMallette.getTitre());
        ComboBox<String> cbTheme = new ComboBox<>();
        cbTheme.setEditable(true);
        try {
            cbTheme.getItems().setAll(malletteDao.getAllThemes());
        } catch (SQLException ignored) {}
        cbTheme.setValue(selectedMallette.getTheme());

        ComboBox<String> cbNiveau = new ComboBox<>();
        cbNiveau.getItems().setAll(LivreClassificationService.NIVEAUX_STANDARDS);
        cbNiveau.setValue(selectedMallette.getNiveau());

        TextArea taDesc = new TextArea(selectedMallette.getDescription());
        taDesc.setPrefRowCount(3);
        taDesc.setWrapText(true);

        grid.add(new Label("Code mallette :"), 0, 0);
        grid.add(tfCode, 1, 0);
        grid.add(new Label("Titre :"), 0, 1);
        grid.add(tfTitre, 1, 1);
        grid.add(new Label("Thème :"), 0, 2);
        grid.add(cbTheme, 1, 2);
        grid.add(new Label("Niveau :"), 0, 3);
        grid.add(cbNiveau, 1, 3);
        grid.add(new Label("Description :"), 0, 4);
        grid.add(taDesc, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnSave) {
                try {
                    selectedMallette.setCode(tfCode.getText().trim().toUpperCase());
                    selectedMallette.setTitre(tfTitre.getText().trim());
                    selectedMallette.setTheme(cbTheme.getValue());
                    selectedMallette.setNiveau(cbNiveau.getValue());
                    selectedMallette.setDescription(taDesc.getText().trim());
                    malletteDao.update(selectedMallette);
                    return true;
                } catch (SQLException e) {
                    showError("Erreur mise à jour", e.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait().ifPresent(saved -> {
            if (saved) {
                loadDataKeepSelection();
            }
        });
    }

    @FXML
    public void handleDeleteMallette() {
        if (selectedMallette == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Supprimer la mallette");
        alert.setHeaderText("Supprimer la mallette '" + selectedMallette.getTitre() + "' (" + selectedMallette.getCode() + ") ?");
        alert.setContentText("Les livres physiques contenus ne seront pas supprimés de la BCD, mais simplement détachés de cette mallette.");

        alert.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    malletteService.supprimerMallette(selectedMallette.getId());
                    selectedMallette = null;
                    selectedBookItem = null;
                    showBookPreview(null);
                    loadData();
                } catch (SQLException e) {
                    showError("Erreur de suppression", e.getMessage());
                }
            }
        });
    }

    @FXML
    public void handleAddLivre() {
        if (selectedMallette == null) return;
        String raw = tfAddLivre.getText().trim();
        if (raw.isEmpty()) return;

        try {
            String clean = raw.startsWith("#") ? raw.substring(1).trim() : raw;
            // Si code exemplaire direct, ajouter directement
            if (clean.matches("^\\d{1,7}$")) {
                Exemplaire ex = malletteService.ajouterLivre(selectedMallette.getId(), raw);
                lblAddLivreFeedback.setText("✅ Exemplaire #" + ex.getId() + " ajouté à la mallette.");
                lblAddLivreFeedback.setStyle("-fx-text-fill: #15803D; -fx-font-weight: bold;");
                tfAddLivre.clear();
                refreshMalletteDetails();
                loadDataKeepSelection();
                return;
            }

            // Vérifier si le titre a plusieurs exemplaires disponibles en BCD
            String isbn = null;
            String cleanIsbn = LivreMetadataService.cleanIsbn(clean);
            if (cleanIsbn.matches("^\\d{9}[0-9Xx]$") || cleanIsbn.matches("^\\d{13}$") || clean.toUpperCase().startsWith("CAMUS-")) {
                isbn = cleanIsbn;
            } else {
                Livre l = livreDao.findByTitreExact(clean);
                if (l == null) {
                    List<Livre> matches = livreDao.findByTitre(clean);
                    if (!matches.isEmpty()) l = matches.get(0);
                }
                if (l != null) isbn = l.getIsbn();
            }

            List<Exemplaire> availableCopies = (isbn != null)
                    ? malletteDao.findAvailableCopiesForIsbnNotInMallette(selectedMallette.getId(), isbn)
                    : List.of();

            if (availableCopies.size() > 1) {
                Alert choiceDialog = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(choiceDialog);
                choiceDialog.setTitle("Exemplaires multiples détectés");
                choiceDialog.setHeaderText("Cet ouvrage possède " + availableCopies.size() + " exemplaires disponibles en BCD.");
                choiceDialog.setContentText("Combien d'exemplaires souhaitez-vous ajouter à la mallette '" + selectedMallette.getTitre() + "' ?");

                ButtonType btnAll = new ButtonType("Ajouter les " + availableCopies.size() + " exemplaires (Série)", ButtonBar.ButtonData.OK_DONE);
                ButtonType btnOne = new ButtonType("Ajouter 1 seul exemplaire", ButtonBar.ButtonData.OTHER);
                choiceDialog.getButtonTypes().setAll(btnAll, btnOne, ButtonType.CANCEL);

                Optional<ButtonType> res = choiceDialog.showAndWait();
                if (res.isEmpty() || res.get() == ButtonType.CANCEL) {
                    return;
                }

                if (res.get() == btnAll) {
                    List<Exemplaire> added = malletteService.ajouterTousExemplairesLivre(selectedMallette.getId(), raw);
                    lblAddLivreFeedback.setText("✅ " + added.size() + " exemplaires ajoutés à la mallette !");
                    lblAddLivreFeedback.setStyle("-fx-text-fill: #15803D; -fx-font-weight: bold;");
                    tfAddLivre.clear();
                    refreshMalletteDetails();
                    loadDataKeepSelection();
                    return;
                }
            }

            Exemplaire ex = malletteService.ajouterLivre(selectedMallette.getId(), raw);
            lblAddLivreFeedback.setText("✅ Livre ajouté : ex. #" + ex.getId());
            lblAddLivreFeedback.setStyle("-fx-text-fill: #15803D; -fx-font-weight: bold;");
            tfAddLivre.clear();
            refreshMalletteDetails();
            loadDataKeepSelection();
        } catch (RegleMetierException | SQLException e) {
            lblAddLivreFeedback.setText("⚠️ " + e.getMessage());
            lblAddLivreFeedback.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
            tfAddLivre.selectAll();
        }
    }

    @FXML
    public void handleFillByTheme() {
        if (selectedMallette == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Remplir la mallette automatiquement");
        alert.setHeaderText("Recherche automatique des livres du thème : " + selectedMallette.getTheme());

        CheckBox chkAll = new CheckBox("⚡ Inclure tous les exemplaires physiques (recommandé pour séries et mallettes)");
        chkAll.setSelected(true);
        chkAll.setStyle("-fx-font-weight: bold; -fx-font-size: 11.5px; -fx-text-fill: #0369A1;");

        VBox box = new VBox(10,
                new Label("BCD Express va scanner le catalogue et ajouter les exemplaires disponibles associés au thème '"
                        + selectedMallette.getTheme() + "' à cette mallette."),
                chkAll
        );
        alert.getDialogPane().setContent(box);

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int added = malletteService.remplirParTheme(selectedMallette.getId(), selectedMallette.getTheme(), chkAll.isSelected());
                    if (added > 0) {
                        showInfo("Remplissage terminé", added + " exemplaire(s) correspondant au thème '" + selectedMallette.getTheme() + "' ont été ajoutés à la mallette !");
                    } else {
                        showInfo("Aucun exemplaire ajouté", "Aucun exemplaire supplémentaire disponible correspondant au thème '" + selectedMallette.getTheme() + "' n'a été trouvé.");
                    }
                    refreshMalletteDetails();
                    loadDataKeepSelection();
                } catch (RegleMetierException | SQLException e) {
                    showError("Erreur de remplissage", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleIntegrateAllAvailableCopies() {
        if (selectedMallette == null) return;
        try {
            int added = malletteService.ajouterTousExemplairesDisponibles(selectedMallette.getId());
            if (added > 0) {
                showInfo("Intégration réussie", added + " exemplaire(s) physique(s) supplémentaire(s) ont été intégrés à la boîte !");
                refreshMalletteDetails();
                loadDataKeepSelection();
            }
        } catch (Exception e) {
            showError("Erreur d'intégration", e.getMessage());
        }
    }

    @FXML
    private void handlePreviewAddOtherCopies() {
        if (selectedMallette == null || selectedBookItem == null) return;
        try {
            List<Exemplaire> added = malletteService.ajouterTousExemplairesLivre(selectedMallette.getId(), selectedBookItem.getIsbn());
            showInfo("Exemplaires ajoutés", added.size() + " exemplaire(s) supplémentaire(s) ajouté(s) à la mallette !");
            refreshMalletteDetails();
            loadDataKeepSelection();
            showBookPreview(selectedBookItem);
        } catch (Exception ex) {
            showError("Erreur d'ajout", ex.getMessage());
        }
    }

    @FXML
    private void handlePreviewRetirerTous() {
        if (selectedBookItem == null || selectedMallette == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(confirm);
        confirm.setTitle("Retirer tous les exemplaires");
        confirm.setHeaderText("Retirer tous les exemplaires de '" + selectedBookItem.getTitre() + "' ?");
        confirm.setContentText("Cette action retirera tous les exemplaires physiques de cet ouvrage de la mallette.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                int count = malletteService.retirerTousExemplairesLivre(selectedMallette.getId(), selectedBookItem.getIsbn());
                showInfo("Livre retiré", count + " exemplaire(s) retiré(s) de la mallette.");
                selectedBookItem = null;
                showBookPreview(null);
                refreshMalletteDetails();
                loadDataKeepSelection();
            } catch (SQLException ex) {
                showError("Erreur", ex.getMessage());
            }
        }
    }

    @FXML
    public void handlePreterMallette() {
        if (selectedMallette == null) return;

        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Prêter la mallette " + selectedMallette.getCode());
        dialog.setHeaderText("Prêt groupé pour : " + selectedMallette.getTitre() + " (" + currentItems.size() + " livres)");

        ButtonType btnValider = new ButtonType("Valider le prêt", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnValider, ButtonType.CANCEL);

        VBox box = new VBox(12);
        box.setPadding(new Insets(16));

        Label lblEnseignant = new Label("1. Sélectionner l'enseignant emprunteur :");
        lblEnseignant.setStyle("-fx-font-weight: 700;");

        ComboBox<Utilisateur> cbEnseignant = new ComboBox<>();
        cbEnseignant.setPrefWidth(360);
        try {
            List<Utilisateur> profs = utilisateurDao.findByType(Utilisateur.TypeCompte.ENSEIGNANT);
            cbEnseignant.getItems().setAll(profs);
            if (!profs.isEmpty()) cbEnseignant.setValue(profs.get(0));
        } catch (SQLException ignored) {}

        cbEnseignant.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Utilisateur u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                } else {
                    setText("👩‍🏫 " + u.getNom() + " " + u.getPrenom() + " (" + u.getClasse() + ")");
                }
            }
        });
        cbEnseignant.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Utilisateur u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setText(null);
                } else {
                    setText("👩‍🏫 " + u.getNom() + " " + u.getPrenom() + " (" + u.getClasse() + ")");
                }
            }
        });

        Label lblDuree = new Label("2. Date de retour convenue :");
        lblDuree.setStyle("-fx-font-weight: 700;");

        DatePicker dpRetour = new DatePicker(LocalDate.now().plusWeeks(4));
        dpRetour.setPrefWidth(360);

        Label lblInfo = new Label("ℹ️ Tous les exemplaires de la mallette seront notés en prêt au nom de l'enseignant.");
        lblInfo.setStyle("-fx-font-size: 11px; -fx-text-fill: #2563EB;");

        box.getChildren().addAll(lblEnseignant, cbEnseignant, lblDuree, dpRetour, lblInfo);
        dialog.getDialogPane().setContent(box);

        dialog.setResultConverter(b -> {
            if (b == btnValider) {
                Utilisateur prof = cbEnseignant.getValue();
                LocalDate dateR = dpRetour.getValue();
                if (prof == null) {
                    showError("Erreur", "Veuillez sélectionner un enseignant.");
                    return false;
                }
                try {
                    malletteService.emprunterMallette(selectedMallette.getId(), prof, dateR);
                    return true;
                } catch (RegleMetierException | SQLException e) {
                    showError("Prêt impossible", e.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait().ifPresent(success -> {
            if (success) {
                loadDataKeepSelection();
                showInfo("Prêt validé", "La mallette a été prêtée avec succès. Vous pouvez maintenant imprimer sa fiche de contenu A4.");
            }
        });
    }

    @FXML
    public void handleRetournerMallette() {
        if (selectedMallette == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Restituer la mallette");
        alert.setHeaderText("Restitution de : " + selectedMallette.getTitre() + " (" + selectedMallette.getCode() + ")");
        alert.setContentText("Vérifiez le contenu de la boîte :\n"
                + "• Total attendu : " + currentItems.size() + " livre(s)\n\n"
                + "Souhaitez-vous clôturer le prêt et remettre tous les livres disponibles en rayon ?");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    malletteService.retournerMallette(selectedMallette.getId());
                    loadDataKeepSelection();
                    showInfo("Mallette restituée", "Tous les livres de la mallette sont de nouveau disponibles en BCD !");
                } catch (RegleMetierException | SQLException e) {
                    showError("Erreur de retour", e.getMessage());
                }
            }
        });
    }

    @FXML
    public void handleImprimerFiche() {
        if (selectedMallette == null) return;
        try {
            File fiche = malletteService.genererFicheContenuHtml(selectedMallette.getId());
            MainApp.openDocument(fiche);
        } catch (SQLException | java.io.IOException e) {
            showError("Erreur d'impression", e.getMessage());
        }
    }

    @FXML
    public void handleImprimerEtiquette() {
        if (selectedMallette == null) return;
        try {
            File etiquette = malletteService.genererEtiquetteMalletteHtml(selectedMallette.getId());
            MainApp.openDocument(etiquette);
        } catch (SQLException | java.io.IOException e) {
            showError("Erreur d'impression", e.getMessage());
        }
    }

    // =====================================================================
    //  Alerts Helpers
    // =====================================================================

    private void showInfo(String title, String content) {
        DialogHelper.showInfo(title, content);
    }

    private void showError(String title, String content) {
        DialogHelper.showError(title, content);
    }
}
