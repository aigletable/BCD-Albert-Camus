package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.database.MalletteDao;
import fr.ecole.bcd.database.UtilisateurDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.Mallette;
import fr.ecole.bcd.model.MalletteItem;
import fr.ecole.bcd.model.RessourcePedagogique;
import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.service.EmpruntService.RegleMetierException;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.MalletteService;
import fr.ecole.bcd.service.RessourcePedagogiqueService;
import fr.ecole.bcd.service.SoundService;
import fr.ecole.bcd.view.AnimationHelper;
import fr.ecole.bcd.view.CoverHelper;
import fr.ecole.bcd.view.DialogHelper;
import fr.ecole.bcd.view.RessourcesPedagogiquesDialog;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;

/**
 * Contrôleur de l'onglet Mallettes Pédagogiques & Thématiques (BCD Express).
 * Refonte graphique moderne alignée sur la maquette :
 * - Pilules de synthèse KPI compactes (disponibles, en prêt, livres)
 * - Liste latérale de cartes avec bordure d'accent colorée et bouton de prêt rapide
 * - Barre de navigation par onglets (Contenu, Ressources, Historique, Infos)
 * - Grille visuelle de couvertures de livres avec badges de quantité
 * - Tiroir inspecteur d'ouvrage avec contrôles pas-à-pas (+) / (-)
 */
public class MallettesController {

    // ── DAO & Services ──────────────────────────────────────────────────
    private final MalletteDao malletteDao = new MalletteDao();
    private final MalletteService malletteService = new MalletteService();
    private final UtilisateurDao utilisateurDao = new UtilisateurDao();
    private final LivreDao livreDao = new LivreDao();
    private final RessourcePedagogiqueService ressourceService = new RessourcePedagogiqueService();

    // ── Cache des images de couvertures réelles ─────────────────────────
    private static final Map<String, Image> COVER_IMAGE_CACHE = new ConcurrentHashMap<>();

    // ── Modèles de données observables ─────────────────────────────────
    private final ObservableList<Mallette> masterMallettes = FXCollections.observableArrayList();
    private FilteredList<Mallette> filteredMallettes;
    private final ObservableList<MalletteItem> currentItems = FXCollections.observableArrayList();

    private Mallette selectedMallette = null;
    private MalletteItem selectedBookItem = null;
    private boolean isGridView = true;

    // ── 1. En-tête & KPIs ──────────────────────────────────────────────
    @FXML private Button btnCreateMallette;
    @FXML private HBox boxKpiPills;
    @FXML private Label lblKpiTotal;
    @FXML private Label lblKpiDispo;
    @FXML private Label lblKpiPret;
    @FXML private Label lblKpiLivres;

    // ── 2. Recherche & Filtres ─────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private ComboBox<String> cbStatutFilter;
    @FXML private ComboBox<String> cbThemeFilter;
    @FXML private Button btnResetFilters;

    // ── 3. Colonne Gauche : Liste des Mallettes ────────────────────────
    @FXML private Label lblCountMallettes;
    @FXML private ListView<Mallette> mallettesListView;

    // ── 4. Colonne Droite : Fiche Détaillée & Onglets ──────────────────
    @FXML private VBox vboxEmptySelection;
    @FXML private VBox vboxDetailContent;

    // En-tête de la mallette
    @FXML private Label lblDetailTitre;
    @FXML private Label lblDetailCode;
    @FXML private Label lblDetailStatutBadge;
    @FXML private Label lblDetailThemeTag;
    @FXML private Label lblDetailNiveauTag;
    @FXML private Label lblDetailNbLivresTag;
    @FXML private Button btnPreterMallette;
    @FXML private Button btnRetournerMallette;
    @FXML private MenuButton btnMoreActions;

    // Boutons des onglets
    @FXML private Button btnTabContenu;
    @FXML private Button btnTabRessources;
    @FXML private Button btnTabHistorique;
    @FXML private Button btnTabInfos;

    // Panneaux des onglets
    @FXML private VBox paneContenu;
    @FXML private VBox paneRessources;
    @FXML private VBox paneHistorique;
    @FXML private VBox paneInfos;

    // ── Onglet 1 : Contenu (Livres & Inspecteur) ────────────────────────
    @FXML private HBox boxPretInfo;
    @FXML private Label lblDetailPretInfo;
    @FXML private TextField tfAddLivre;
    @FXML private Button btnAddLivre;
    @FXML private Button btnFillTheme;
    @FXML private Button btnToggleBooksView;
    @FXML private Label lblAddLivreFeedback;
    @FXML private HBox boxMissingCopiesBanner;
    @FXML private Label lblMissingCopiesInfo;
    @FXML private Button btnIntegrateAllCopies;

    // Vue Grille vs Tableau
    @FXML private ScrollPane scrollBooksGrid;
    @FXML private FlowPane flowBooksGrid;
    @FXML private TableView<MalletteItem> itemsTable;
    @FXML private TableColumn<MalletteItem, Integer> colItemExId;
    @FXML private TableColumn<MalletteItem, String> colItemTitre;
    @FXML private TableColumn<MalletteItem, String> colItemAuteur;
    @FXML private TableColumn<MalletteItem, String> colItemGenre;
    @FXML private TableColumn<MalletteItem, Exemplaire.Statut> colItemStatut;
    @FXML private TableColumn<MalletteItem, String> colItemCopies;
    @FXML private TableColumn<MalletteItem, Void> colItemRessources;
    @FXML private TableColumn<MalletteItem, Void> colItemAction;

    // Tiroir Inspecteur d'ouvrage sélectionné
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
    @FXML private Label lblPreviewCopiesBadge;
    @FXML private Button btnPreviewQtyMinus;
    @FXML private Label lblPreviewQtyCount;
    @FXML private Button btnPreviewQtyPlus;
    @FXML private Button btnPreviewAddOtherCopies;
    @FXML private ScrollPane scrollPreviewDesc;
    @FXML private Label lblPreviewDescription;
    @FXML private Button btnPreviewRessources;
    @FXML private Button btnPreviewRetirerTous;
    @FXML private Button btnPreviewRetirer;

    // ── Onglet 2 : Ressources Pédagogiques ──────────────────────────────
    @FXML private Button btnAddRessourceMallette;
    @FXML private TableView<RessourcePedagogique> tableRessourcesMallette;
    @FXML private TableColumn<RessourcePedagogique, String> colResType;
    @FXML private TableColumn<RessourcePedagogique, String> colResTitre;
    @FXML private TableColumn<RessourcePedagogique, String> colResCible;
    @FXML private TableColumn<RessourcePedagogique, Void> colResAction;

    // ── Onglet 3 : Historique des Prêts ────────────────────────────────
    @FXML private TableView<MalletteHistoriqueItem> tableHistoriqueMallette;
    @FXML private TableColumn<MalletteHistoriqueItem, String> colHistClasse;
    @FXML private TableColumn<MalletteHistoriqueItem, String> colHistSortie;
    @FXML private TableColumn<MalletteHistoriqueItem, String> colHistRetour;
    @FXML private TableColumn<MalletteHistoriqueItem, String> colHistStatut;

    // ── Onglet 4 : Informations & Pédagogie ─────────────────────────────
    @FXML private Button btnEditMalletteInfos;
    @FXML private Label lblInfoTheme;
    @FXML private Label lblInfoNiveau;
    @FXML private Label lblInfoDateCreation;
    @FXML private Label lblInfoStats;
    @FXML private Label lblInfoDescription;

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @FXML
    public void initialize() {
        configureListView();
        configureItemsTable();
        configureRessourcesTable();
        configureHistoriqueTable();
        configureFilters();

        if (tfAddLivre != null) {
            tfAddLivre.setOnAction(e -> handleAddLivre());
        }

        loadData();
    }

    // =====================================================================
    //  Configuration des Composants et Tableaux
    // =====================================================================

    private void configureListView() {
        filteredMallettes = new FilteredList<>(masterMallettes, p -> true);
        mallettesListView.setItems(filteredMallettes);

        mallettesListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedMallette = newV;
            updateDetailView();
        });

        // Palette de couleurs pour les bordures d'accent gauche
        final String[] ACCENT_COLORS = {"#ef4444", "#3b82f6", "#10b981", "#a855f7", "#f59e0b", "#06b6d4"};

        mallettesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Mallette m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox card = new HBox(8);
                    card.getStyleClass().add("mallette-card");
                    if (isSelected()) {
                        card.getStyleClass().add("mallette-card-selected");
                    }
                    card.setAlignment(Pos.CENTER_LEFT);

                    String color = ACCENT_COLORS[Math.abs(m.getId()) % ACCENT_COLORS.length];
                    Region accentBar = new Region();
                    accentBar.setMinWidth(5);
                    accentBar.setMaxWidth(5);
                    accentBar.setPrefWidth(5);
                    accentBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");
                    VBox.setVgrow(accentBar, Priority.ALWAYS);

                    VBox vboxText = new VBox(3);
                    HBox.setHgrow(vboxText, Priority.ALWAYS);

                    Label lblT = new Label(m.getTitre());
                    lblT.setStyle("-fx-font-weight: 800; -fx-font-size: 13.5px; -fx-text-fill: #0F172A;");

                    int distinct = 0;
                    try { distinct = malletteDao.countDistinctTitles(m.getId()); } catch (Exception ignored) {}
                    String meta = m.getCode() + " · " + (m.getNiveau().isBlank() ? "Tous niveaux" : m.getNiveau())
                            + " · " + m.getNombreLivres() + " livre" + (m.getNombreLivres() > 1 ? "s" : "")
                            + " · " + distinct + " titre" + (distinct > 1 ? "s" : "");
                    Label lblM = new Label(meta);
                    lblM.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

                    HBox rowB = new HBox(6);
                    rowB.setAlignment(Pos.CENTER_LEFT);

                    Label badge = new Label(m.isEmprunte() ? "En prêt – " + m.getEmprunteurClasse() : "Disponible");
                    badge.setStyle(m.isEmprunte()
                            ? "-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-size: 10.5px; -fx-font-weight: 700; -fx-padding: 2 7; -fx-background-radius: 999;"
                            : "-fx-background-color: #DCFCE7; -fx-text-fill: #166534; -fx-font-size: 10.5px; -fx-font-weight: 700; -fx-padding: 2 7; -fx-background-radius: 999;");

                    Region sp = new Region();
                    HBox.setHgrow(sp, Priority.ALWAYS);

                    if (m.isDisponible()) {
                        Button btnLoan = new Button("📤 Prêter");
                        btnLoan.setStyle("-fx-background-color: #2563EB; -fx-text-fill: white; -fx-font-weight: 700; -fx-font-size: 10.5px; -fx-padding: 2 8; -fx-background-radius: 6; -fx-cursor: hand;");
                        btnLoan.setOnAction(ev -> {
                            ev.consume();
                            mallettesListView.getSelectionModel().select(m);
                            handlePreterMallette();
                        });
                        rowB.getChildren().addAll(badge, sp, btnLoan);
                    } else {
                        rowB.getChildren().addAll(badge);
                    }

                    vboxText.getChildren().addAll(lblT, lblM, rowB);
                    card.getChildren().addAll(accentBar, vboxText);

                    setGraphic(card);
                    setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                }
            }
        });
    }

    private void configureItemsTable() {
        if (itemsTable == null) return;

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
                    setText("#" + id);
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
                        badge.setStyle(inM > 1
                                ? "-fx-background-color: #EDE9FE; -fx-text-fill: #6D28D9; -fx-font-weight: 700; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4;"
                                : "-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 10px; -fx-padding: 2 6; -fx-background-radius: 4;");
                        setGraphic(badge);
                        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }

        if (colItemRessources != null) {
            colItemRessources.setCellFactory(col -> new TableCell<>() {
                private final Button btnRes = new Button();
                {
                    btnRes.setOnAction(e -> {
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
                                    btnRes.setText("📎 " + count + " doc" + (count > 1 ? "s" : ""));
                                    btnRes.setStyle("-fx-background-color: #E0F2FE; -fx-text-fill: #0369A1; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 2 6; -fx-background-radius: 6; -fx-cursor: hand;");
                                } else {
                                    btnRes.setText("➕ Ajouter");
                                    btnRes.setStyle("-fx-background-color: transparent; -fx-text-fill: #64748B; -fx-font-size: 11px; -fx-padding: 2 4; -fx-cursor: hand;");
                                }
                            } catch (Exception ex) {
                                btnRes.setText("📎 0");
                            }
                            setGraphic(btnRes);
                            setAlignment(Pos.CENTER);
                        } else {
                            setGraphic(null);
                        }
                    }
                }
            });
        }

        if (colItemAction != null) {
            colItemAction.setCellFactory(col -> new TableCell<>() {
                private final Button btnRemove = new Button("❌ Retirer");
                {
                    btnRemove.setStyle("-fx-font-size: 10px; -fx-background-color: transparent; -fx-text-fill: #DC2626; -fx-cursor: hand; -fx-padding: 2 6;");
                    btnRemove.setOnAction(e -> {
                        MalletteItem item = getTableView().getItems().get(getIndex());
                        if (item != null && selectedMallette != null) {
                            demanderEtRetirerLivre(item);
                        }
                    });
                }
                @Override
                protected void updateItem(Void val, boolean empty) {
                    super.updateItem(val, empty);
                    setGraphic(empty ? null : btnRemove);
                    setAlignment(Pos.CENTER);
                }
            });
        }

        itemsTable.setItems(currentItems);
        itemsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> showBookPreview(newV));
    }

    private void configureRessourcesTable() {
        if (tableRessourcesMallette == null) return;

        colResType.setCellValueFactory(cd -> {
            RessourcePedagogique r = cd.getValue();
            String t = (r != null && r.getType() != null) ? r.getType().name() : "";
            String icon = switch (t) {
                case "WEB_URL" -> "🌐 Web";
                case "DOCUMENT_PDF" -> "📄 PDF";
                default -> "📁 Doc";
            };
            return new javafx.beans.property.SimpleStringProperty(icon);
        });

        colResTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));

        colResCible.setCellValueFactory(cd -> {
            RessourcePedagogique r = cd.getValue();
            if (r == null) return new javafx.beans.property.SimpleStringProperty("");
            if (r.getIsbn() != null && !r.getIsbn().isBlank()) {
                try {
                    Livre l = livreDao.findByIsbn(r.getIsbn());
                    if (l != null) return new javafx.beans.property.SimpleStringProperty("📖 " + l.getTitre());
                } catch (Exception ignored) {}
                return new javafx.beans.property.SimpleStringProperty("Livre (" + r.getIsbn() + ")");
            }
            return new javafx.beans.property.SimpleStringProperty("🧰 Mallette complète");
        });

        colResAction.setCellFactory(col -> new TableCell<>() {
            private final Button btnOuvrir = new Button("👁️ Ouvrir");
            private final Button btnSuppr = new Button("🗑️");
            private final HBox box = new HBox(6, btnOuvrir, btnSuppr);
            {
                box.setAlignment(Pos.CENTER);
                btnOuvrir.setStyle("-fx-background-color: #E0F2FE; -fx-text-fill: #0369A1; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 4; -fx-cursor: hand;");
                btnSuppr.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #DC2626; -fx-font-size: 11px; -fx-padding: 3 6; -fx-background-radius: 4; -fx-cursor: hand;");

                btnOuvrir.setOnAction(e -> {
                    RessourcePedagogique r = getTableView().getItems().get(getIndex());
                    if (r != null) {
                        try {
                            ressourceService.ouvrirRessource(r);
                        } catch (Exception ex) {
                            showError("Impossible d'ouvrir", ex.getMessage());
                        }
                    }
                });

                btnSuppr.setOnAction(e -> {
                    RessourcePedagogique r = getTableView().getItems().get(getIndex());
                    if (r != null) {
                        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                        MainApp.prepareDialog(confirm);
                        confirm.setTitle("Supprimer la ressource");
                        confirm.setHeaderText("Supprimer « " + r.getTitre() + " » ?");
                        confirm.setContentText("Cette ressource ne sera plus associée à cette mallette.");
                        confirm.showAndWait().ifPresent(btn -> {
                            if (btn == ButtonType.OK) {
                                try {
                                    ressourceService.supprimerRessource(r.getId());
                                    refreshRessourcesTab();
                                    refreshMalletteDetails();
                                } catch (SQLException ex) {
                                    showError("Erreur de suppression", ex.getMessage());
                                }
                            }
                        });
                    }
                });
            }

            @Override
            protected void updateItem(Void val, boolean empty) {
                super.updateItem(val, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private void configureHistoriqueTable() {
        if (tableHistoriqueMallette == null) return;

        colHistClasse.setCellValueFactory(new PropertyValueFactory<>("classeEtEnseignant"));
        colHistSortie.setCellValueFactory(new PropertyValueFactory<>("dateSortie"));
        colHistRetour.setCellValueFactory(new PropertyValueFactory<>("dateRetour"));
        colHistStatut.setCellValueFactory(new PropertyValueFactory<>("statut"));

        colHistStatut.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String statut, boolean empty) {
                super.updateItem(statut, empty);
                if (empty || statut == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(statut);
                    if ("En cours".equalsIgnoreCase(statut)) {
                        badge.setStyle("-fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-font-weight: 700; -fx-font-size: 10.5px; -fx-padding: 2 8; -fx-background-radius: 999;");
                    } else {
                        badge.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #166534; -fx-font-weight: 700; -fx-font-size: 10.5px; -fx-padding: 2 8; -fx-background-radius: 999;");
                    }
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                }
            }
        });
    }

    private void configureFilters() {
        cbStatutFilter.getItems().setAll("Tous les statuts", "Disponible", "En prêt");
        cbStatutFilter.setValue("Tous les statuts");
        cbStatutFilter.setOnAction(e -> applyFilters());

        try {
            List<String> themes = malletteDao.getAllThemes();
            cbThemeFilter.getItems().add("Tous les thèmes");
            cbThemeFilter.getItems().addAll(themes);
            cbThemeFilter.setValue("Tous les thèmes");
        } catch (SQLException ignored) {
            cbThemeFilter.getItems().setAll("Tous les thèmes");
        }
        cbThemeFilter.setOnAction(e -> applyFilters());

        searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
    }

    private void applyFilters() {
        String q = (searchField != null && searchField.getText() != null)
                ? searchField.getText().trim().toLowerCase() : "";
        String st = (cbStatutFilter != null) ? cbStatutFilter.getValue() : null;
        String th = (cbThemeFilter != null) ? cbThemeFilter.getValue() : null;

        filteredMallettes.setPredicate(m -> {
            if (!q.isEmpty()) {
                String full = (m.getCode() + " " + m.getTitre() + " " + m.getTheme() + " " + m.getNiveau() + " " + m.getEmprunteurComplet()).toLowerCase();
                if (!full.contains(q)) return false;
            }
            if (st != null && !st.equals("Tous les statuts")) {
                if (st.equals("Disponible") && !m.isDisponible()) return false;
                if (st.equals("En prêt") && !m.isEmprunte()) return false;
            }
            if (th != null && !th.equals("Tous les thèmes")) {
                if (!m.getTheme().equalsIgnoreCase(th)) return false;
            }
            return true;
        });

        if (lblCountMallettes != null) {
            int count = filteredMallettes.size();
            lblCountMallettes.setText(count + " mallette" + (count > 1 ? "s" : ""));
        }
    }

    // =====================================================================
    //  Chargement des données & Navigation
    // =====================================================================

    public void loadData() {
        try {
            List<Mallette> list = malletteDao.findAll();
            masterMallettes.setAll(list);
            updateKpis(list);
            applyFilters();

            if (selectedMallette == null && !filteredMallettes.isEmpty()) {
                mallettesListView.getSelectionModel().select(0);
            } else if (selectedMallette != null) {
                loadDataKeepSelection();
            }
        } catch (SQLException e) {
            showError("Erreur de chargement", e.getMessage());
        }
    }

    private void loadDataKeepSelection() {
        int keepId = (selectedMallette != null) ? selectedMallette.getId() : -1;
        try {
            List<Mallette> list = malletteDao.findAll();
            masterMallettes.setAll(list);
            updateKpis(list);
            applyFilters();

            if (keepId != -1) {
                for (Mallette m : filteredMallettes) {
                    if (m.getId() == keepId) {
                        mallettesListView.getSelectionModel().select(m);
                        selectedMallette = m;
                        updateDetailView();
                        return;
                    }
                }
            }
            if (!filteredMallettes.isEmpty()) {
                mallettesListView.getSelectionModel().select(0);
            } else {
                selectedMallette = null;
                updateDetailView();
            }
        } catch (SQLException e) {
            showError("Erreur de chargement", e.getMessage());
        }
    }

    private void updateKpis(List<Mallette> list) {
        int total = list.size();
        long enPret = list.stream().filter(Mallette::isEmprunte).count();
        long dispo = total - enPret;
        int totalLivres = list.stream().mapToInt(Mallette::getNombreLivres).sum();

        if (lblKpiTotal != null) lblKpiTotal.setText(total + " mallette" + (total > 1 ? "s" : ""));
        if (lblKpiDispo != null) lblKpiDispo.setText(dispo + " disponible" + (dispo > 1 ? "s" : ""));
        if (lblKpiPret != null) lblKpiPret.setText(enPret + " en prêt");
        if (lblKpiLivres != null) lblKpiLivres.setText(totalLivres + " livre" + (totalLivres > 1 ? "s" : ""));
    }

    // =====================================================================
    //  Gestion des Onglets (Contenu | Ressources | Historique | Infos)
    // =====================================================================

    @FXML private void handleSelectTabContenu() { switchTab("Contenu"); }
    @FXML private void handleSelectTabRessources() { switchTab("Ressources"); }
    @FXML private void handleSelectTabHistorique() { switchTab("Historique"); }
    @FXML private void handleSelectTabInfos() { switchTab("Infos"); }

    private void switchTab(String tabName) {
        btnTabContenu.getStyleClass().remove("tab-nav-btn-active");
        btnTabRessources.getStyleClass().remove("tab-nav-btn-active");
        btnTabHistorique.getStyleClass().remove("tab-nav-btn-active");
        btnTabInfos.getStyleClass().remove("tab-nav-btn-active");

        paneContenu.setVisible(false); paneContenu.setManaged(false);
        paneRessources.setVisible(false); paneRessources.setManaged(false);
        paneHistorique.setVisible(false); paneHistorique.setManaged(false);
        paneInfos.setVisible(false); paneInfos.setManaged(false);

        switch (tabName) {
            case "Contenu" -> {
                btnTabContenu.getStyleClass().add("tab-nav-btn-active");
                paneContenu.setVisible(true); paneContenu.setManaged(true);
            }
            case "Ressources" -> {
                btnTabRessources.getStyleClass().add("tab-nav-btn-active");
                paneRessources.setVisible(true); paneRessources.setManaged(true);
                refreshRessourcesTab();
            }
            case "Historique" -> {
                btnTabHistorique.getStyleClass().add("tab-nav-btn-active");
                paneHistorique.setVisible(true); paneHistorique.setManaged(true);
                refreshHistoriqueTab();
            }
            case "Infos" -> {
                btnTabInfos.getStyleClass().add("tab-nav-btn-active");
                paneInfos.setVisible(true); paneInfos.setManaged(true);
                refreshInfosTab();
            }
        }
    }

    @FXML
    private void handleToggleBooksView() {
        isGridView = !isGridView;
        if (isGridView) {
            scrollBooksGrid.setVisible(true);
            scrollBooksGrid.setManaged(true);
            itemsTable.setVisible(false);
            itemsTable.setManaged(false);
            btnToggleBooksView.setText("☰ Tableau");
        } else {
            scrollBooksGrid.setVisible(false);
            scrollBooksGrid.setManaged(false);
            itemsTable.setVisible(true);
            itemsTable.setManaged(true);
            btnToggleBooksView.setText("⊞ Grille");
        }
    }

    // =====================================================================
    //  Mise à jour de la Fiche Détaillée
    // =====================================================================

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

        btnFillTheme.setText("🪄 Remplir par thème");

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
            } else {
                lblDetailNbLivresTag.setText(items.size() + " livre" + (items.size() > 1 ? "s" : ""));
            }
            btnPreterMallette.setDisable(items.isEmpty());

            // Rendu de la grille visuelle de couvertures
            renderBooksGrid();

            // Détection des exemplaires supplémentaires disponibles en BCD
            List<Exemplaire> missingCopies = malletteDao.findAvailableCopiesNotInMallette(selectedMallette.getId());
            if (boxMissingCopiesBanner != null) {
                if (!missingCopies.isEmpty()) {
                    boxMissingCopiesBanner.setVisible(true);
                    boxMissingCopiesBanner.setManaged(true);
                    if (lblMissingCopiesInfo != null) {
                        lblMissingCopiesInfo.setText("💡 " + missingCopies.size() + " exemplaire(s) supplémentaire(s) sont disponibles en BCD.");
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

            // Rafraîchir l'onglet actif si différent de Contenu
            if (paneRessources != null && paneRessources.isVisible()) refreshRessourcesTab();
            if (paneHistorique != null && paneHistorique.isVisible()) refreshHistoriqueTab();
            if (paneInfos != null && paneInfos.isVisible()) refreshInfosTab();

        } catch (SQLException e) {
            showError("Erreur de chargement des livres", e.getMessage());
        }
    }

    // =====================================================================
    //  Rendu de la Grille Visuelle des Livres
    // =====================================================================

    private void renderBooksGrid() {
        if (flowBooksGrid == null) return;
        flowBooksGrid.getChildren().clear();

        if (currentItems.isEmpty()) {
            VBox emptyBox = new VBox(8);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new Insets(30));
            emptyBox.setPrefWidth(380);

            Label icon = new Label("📭");
            icon.setStyle("-fx-font-size: 32px;");
            Label title = new Label("Cette mallette est encore vide.");
            title.setStyle("-fx-font-weight: 700; -fx-font-size: 13.5px; -fx-text-fill: #64748B;");
            Label sub = new Label("Scannez un livre ou tapez son titre ci-dessus pour l'ajouter.");
            sub.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #94A3B8;");

            emptyBox.getChildren().addAll(icon, title, sub);
            flowBooksGrid.getChildren().add(emptyBox);
            return;
        }

        // Grouper par ISBN pour afficher les séries et exemplaires multiples avec badge ×Q
        Map<String, List<MalletteItem>> grouped = new LinkedHashMap<>();
        for (MalletteItem item : currentItems) {
            String key = (item.getIsbn() != null && !item.getIsbn().isBlank())
                    ? item.getIsbn() : ("TITRE-" + item.getTitre());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<String, List<MalletteItem>> entry : grouped.entrySet()) {
            List<MalletteItem> copies = entry.getValue();
            MalletteItem first = copies.get(0);
            int qty = copies.size();

            Livre livre = null;
            if (first.getIsbn() != null) {
                try {
                    livre = livreDao.findByIsbn(first.getIsbn());
                } catch (SQLException ignored) {}
            }
            if (livre == null) {
                livre = new Livre(first.getIsbn(), first.getTitre(), first.getAuteur(), "", "", first.getGenre(), first.getTheme(), "", "");
            }

            VBox card = createBookGridCard(first, livre, qty);
            flowBooksGrid.getChildren().add(card);
        }
    }

    private VBox createBookGridCard(MalletteItem item, Livre livre, int qty) {
        VBox card = new VBox(6);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(155);
        card.setMinWidth(145);
        card.setMaxWidth(165);
        card.setPadding(new Insets(10));
        card.getStyleClass().add("book-grid-card");

        // Ligne supérieure : Bouton Retirer (gauche) + Badge Quantité (droite)
        HBox topRow = new HBox();
        topRow.setAlignment(Pos.CENTER_LEFT);

        Button btnRm = new Button("Retirer");
        btnRm.getStyleClass().add("book-rm-btn");
        btnRm.setOnAction(e -> {
            e.consume();
            demanderEtRetirerLivre(item);
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Label lblQty = new Label("×" + qty);
        lblQty.getStyleClass().add("book-qty-badge");

        topRow.getChildren().addAll(btnRm, sp, lblQty);

        // Boîte Couverture (Largeur 100, Hauteur 135) avec clip arrondi
        StackPane coverBox = new StackPane();
        coverBox.setPrefSize(100, 135);
        coverBox.setMinSize(100, 135);
        coverBox.setMaxSize(100, 135);
        Rectangle clip = new Rectangle(100, 135);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        coverBox.setClip(clip);

        StackPane styledCover = CoverHelper.createStyledCover(livre, 100, 135);
        coverBox.getChildren().setAll(styledCover);

        String url = livre.getCouvertureUrl();
        if (url != null && !url.isBlank()) {
            Image cached = COVER_IMAGE_CACHE.get(url);
            if (cached != null && !cached.isError()) {
                ImageView iv = new ImageView(cached);
                iv.setFitWidth(100);
                iv.setFitHeight(135);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                coverBox.getChildren().setAll(iv);
            } else {
                try {
                    Image img = new Image(url, true);
                    img.progressProperty().addListener((obs, oldV, p) -> {
                        if (p.doubleValue() >= 1.0 && !img.isError()) {
                            COVER_IMAGE_CACHE.put(url, img);
                            Platform.runLater(() -> {
                                ImageView iv = new ImageView(img);
                                iv.setFitWidth(100);
                                iv.setFitHeight(135);
                                iv.setPreserveRatio(true);
                                iv.setSmooth(true);
                                coverBox.getChildren().setAll(iv);
                            });
                        }
                    });
                } catch (Exception ignored) {}
            }
        }

        // Titre de l'ouvrage
        Label lblT = new Label(item.getTitre());
        lblT.setWrapText(true);
        lblT.setMaxHeight(36);
        lblT.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-alignment: center;");
        lblT.setTooltip(new Tooltip(item.getTitre()));

        // Auteur & Genre
        String authorText = (item.getAuteur() != null && !item.getAuteur().isBlank()) ? item.getAuteur() : "Collectif";
        String genreText = (item.getGenre() != null && !item.getGenre().isBlank()) ? item.getGenre() : "Livre";
        Label lblA = new Label(authorText + " · " + genreText);
        lblA.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748B; -fx-text-alignment: center;");
        lblA.setWrapText(false);

        card.getChildren().addAll(topRow, coverBox, lblT, lblA);

        // Clic sur la carte = ouverture de l'inspecteur
        card.setOnMouseClicked(e -> showBookPreview(item));

        return card;
    }

    // =====================================================================
    //  Tiroir Inspecteur de l'Ouvrage Sélectionné
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

        // Stepper de quantité dans la boîte
        int inMallette = 1;
        List<Exemplaire> otherAvailable = List.of();
        try {
            inMallette = malletteDao.countCopiesInMallette(item.getMalletteId(), item.getIsbn());
            otherAvailable = malletteDao.findAvailableCopiesForIsbnNotInMallette(item.getMalletteId(), item.getIsbn());
        } catch (Exception ignored) {}

        if (lblPreviewQtyCount != null) {
            lblPreviewQtyCount.setText(String.valueOf(inMallette));
        }

        if (lblPreviewCopiesBadge != null) {
            if (inMallette > 1 && otherAvailable.isEmpty()) {
                lblPreviewCopiesBadge.setText("📦 " + inMallette + " exemplaires dans la boîte (série complète)");
            } else if (!otherAvailable.isEmpty()) {
                lblPreviewCopiesBadge.setText("📦 " + inMallette + " ex. dans la boîte (" + otherAvailable.size() + " autre(s) dispo en BCD)");
            } else {
                lblPreviewCopiesBadge.setText("📦 1 exemplaire (exemplaire unique)");
            }
        }

        if (btnPreviewAddOtherCopies != null) {
            if (!otherAvailable.isEmpty()) {
                btnPreviewAddOtherCopies.setText("➕ Ajouter tous les autres (" + otherAvailable.size() + " ex.)");
                btnPreviewAddOtherCopies.setVisible(true);
                btnPreviewAddOtherCopies.setManaged(true);
            } else {
                btnPreviewAddOtherCopies.setVisible(false);
                btnPreviewAddOtherCopies.setManaged(false);
            }
        }

        if (btnPreviewRetirerTous != null) {
            btnPreviewRetirerTous.setText("🗑️ Retirer ce titre de la boîte (" + inMallette + " ex.)");
        }
    }

    private void loadPreviewCover(Livre livre) {
        if (previewCoverBox == null) return;
        previewCoverBox.getChildren().clear();

        Rectangle clip = new Rectangle(88, 120);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        previewCoverBox.setClip(clip);

        StackPane placeholderCover = CoverHelper.createStyledCover(livre, 88, 120);
        previewCoverBox.getChildren().setAll(placeholderCover);

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
                } catch (Exception ignored) {}
            }
        }
    }

    @FXML
    private void handleCloseBookPreview() {
        showBookPreview(null);
    }

    @FXML
    private void handlePreviewQtyPlus() {
        if (selectedMallette == null || selectedBookItem == null) return;
        try {
            List<Exemplaire> available = malletteDao.findAvailableCopiesForIsbnNotInMallette(
                    selectedMallette.getId(), selectedBookItem.getIsbn());
            if (!available.isEmpty()) {
                Exemplaire toAdd = available.get(0);
                malletteService.ajouterLivre(selectedMallette.getId(), String.valueOf(toAdd.getId()));
                refreshMalletteDetails();
                showBookPreview(selectedBookItem);
                SoundService.playSuccess();
            } else {
                showInfo("Stock BCD épuisé", "Tous les exemplaires de cet ouvrage disponibles en BCD sont déjà dans la mallette.");
            }
        } catch (Exception e) {
            showError("Erreur d'ajout", e.getMessage());
        }
    }

    @FXML
    private void handlePreviewQtyMinus() {
        if (selectedMallette == null || selectedBookItem == null) return;
        try {
            List<MalletteItem> copies = malletteDao.getItemsForIsbn(
                    selectedMallette.getId(), selectedBookItem.getIsbn());
            if (copies.size() > 1) {
                MalletteItem toRemove = copies.get(copies.size() - 1);
                malletteService.retirerLivre(selectedMallette.getId(), toRemove.getExemplaireId());
                refreshMalletteDetails();
                showBookPreview(selectedBookItem);
                SoundService.playSuccess();
            } else {
                demanderEtRetirerLivre(selectedBookItem);
            }
        } catch (Exception e) {
            showError("Erreur de retrait", e.getMessage());
        }
    }

    @FXML
    private void handlePreviewAddOtherCopies() {
        if (selectedMallette == null || selectedBookItem == null) return;
        try {
            List<Exemplaire> added = malletteService.ajouterTousExemplairesLivre(selectedMallette.getId(), selectedBookItem.getIsbn());
            refreshMalletteDetails();
            showBookPreview(selectedBookItem);
            SoundService.playSuccess();
            showInfo("Exemplaires ajoutés", added.size() + " exemplaire(s) supplémentaire(s) intégré(s) à la mallette !");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
        }
    }

    @FXML
    private void handlePreviewRetirerTous() {
        if (selectedMallette == null || selectedBookItem == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Retirer tous les exemplaires");
        alert.setHeaderText("Retirer « " + selectedBookItem.getTitre() + " » ?");
        alert.setContentText("Tous les exemplaires de ce titre seront retirés de la mallette et redeviendront disponibles en BCD.");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int removed = malletteService.retirerTousExemplairesLivre(selectedMallette.getId(), selectedBookItem.getIsbn());
                    refreshMalletteDetails();
                    showBookPreview(null);
                    SoundService.playSuccess();
                    showInfo("Ouvrage retiré", removed + " exemplaire(s) retiré(s) de la mallette.");
                } catch (SQLException e) {
                    showError("Erreur de retrait", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handlePreviewRetirer() {
        if (selectedBookItem != null) {
            demanderEtRetirerLivre(selectedBookItem);
        }
    }

    @FXML
    private void handlePreviewRessources() {
        if (selectedBookItem == null) return;
        RessourcesPedagogiquesDialog.openForLivre(
                selectedBookItem.getIsbn(), selectedBookItem.getTitre(), selectedBookItem.getAuteur(),
                () -> {
                    refreshMalletteDetails();
                    showBookPreview(selectedBookItem);
                }
        );
    }

    private void demanderEtRetirerLivre(MalletteItem item) {
        if (item == null || selectedMallette == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Retirer de la mallette");
        alert.setHeaderText("Retirer « " + item.getTitre() + " » ?");
        alert.setContentText("Ce livre sera remis en rayon et redeviendra disponible pour les emprunts classiques.");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    malletteService.retirerLivre(selectedMallette.getId(), item.getExemplaireId());
                    refreshMalletteDetails();
                    SoundService.playSuccess();
                } catch (SQLException e) {
                    showError("Erreur de retrait", e.getMessage());
                }
            }
        });
    }

    // =====================================================================
    //  Onglet 2 : Ressources Pédagogiques
    // =====================================================================

    private void refreshRessourcesTab() {
        if (selectedMallette == null || tableRessourcesMallette == null) return;
        try {
            List<RessourcePedagogique> list = ressourceService.getToutesRessourcesPourMallette(selectedMallette.getId());
            tableRessourcesMallette.setItems(FXCollections.observableArrayList(list));
        } catch (SQLException e) {
            tableRessourcesMallette.setItems(FXCollections.observableArrayList());
        }
    }

    @FXML
    private void handleRessourcesMallette() {
        if (selectedMallette == null) return;
        RessourcesPedagogiquesDialog.openForMallette(selectedMallette, () -> {
            refreshMalletteDetails();
            refreshRessourcesTab();
        });
    }

    // =====================================================================
    //  Onglet 3 : Historique des Prêts
    // =====================================================================

    public static class MalletteHistoriqueItem {
        private final String classeEtEnseignant;
        private final String dateSortie;
        private final String dateRetour;
        private final String statut;
        private final boolean enCours;

        public MalletteHistoriqueItem(String classeEtEnseignant, String dateSortie, String dateRetour, String statut, boolean enCours) {
            this.classeEtEnseignant = classeEtEnseignant;
            this.dateSortie = dateSortie;
            this.dateRetour = dateRetour;
            this.statut = statut;
            this.enCours = enCours;
        }

        public String getClasseEtEnseignant() { return classeEtEnseignant; }
        public String getDateSortie() { return dateSortie; }
        public String getDateRetour() { return dateRetour; }
        public String getStatut() { return statut; }
        public boolean isEnCours() { return enCours; }
    }

    private void refreshHistoriqueTab() {
        if (selectedMallette == null || tableHistoriqueMallette == null) return;

        ObservableList<MalletteHistoriqueItem> historyList = FXCollections.observableArrayList();

        // 1. Emprunt actif en cours
        if (selectedMallette.isEmprunte()) {
            historyList.add(new MalletteHistoriqueItem(
                    selectedMallette.getEmprunteurComplet(),
                    selectedMallette.getDateEmpruntFormatted(),
                    selectedMallette.getDateRetourPrevueFormatted() + " (prévu)",
                    "En cours",
                    true
            ));
        }

        // 2. Historique passé depuis la base
        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(
                     "SELECT DISTINCT u.classe, u.nom, u.prenom, e.date_emprunt, e.date_rendu " +
                     "FROM emprunts e " +
                     "JOIN utilisateurs u ON e.utilisateur_numero = u.numero " +
                     "JOIN mallette_items mi ON e.exemplaire_id = mi.exemplaire_id " +
                     "WHERE mi.mallette_id = ? AND e.date_rendu IS NOT NULL " +
                     "ORDER BY e.date_emprunt DESC")) {

            stmt.setInt(1, selectedMallette.getId());
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String classe = rs.getString("classe");
                    String nom = rs.getString("nom");
                    String prenom = rs.getString("prenom");
                    String dateSortie = rs.getString("date_emprunt");
                    String dateRendu = rs.getString("date_rendu");

                    String enseignantStr = (classe != null && !classe.isBlank() ? classe + " – " : "") + nom + " " + prenom;
                    historyList.add(new MalletteHistoriqueItem(enseignantStr, dateSortie, dateRendu, "Rendu", false));
                }
            }
        } catch (Exception ignored) {}

        tableHistoriqueMallette.setItems(historyList);
    }

    // =====================================================================
    //  Onglet 4 : Informations & Pédagogie
    // =====================================================================

    private void refreshInfosTab() {
        if (selectedMallette == null) return;
        lblInfoTheme.setText(selectedMallette.getTheme());
        lblInfoNiveau.setText(selectedMallette.getNiveau().isBlank() ? "Tous niveaux" : selectedMallette.getNiveau());
        lblInfoDateCreation.setText(selectedMallette.getDateCreationFormatted());
        lblInfoDescription.setText(selectedMallette.getDescription().isBlank() ? "Aucune note pédagogique particulière." : selectedMallette.getDescription());

        int distinct = 0;
        try {
            distinct = malletteDao.countDistinctTitles(selectedMallette.getId());
        } catch (Exception ignored) {}
        lblInfoStats.setText(currentItems.size() + " exemplaires (" + distinct + " titres distincts)");
    }

    // =====================================================================
    //  Actions : Ajout de Livres & Prêt / Retour
    // =====================================================================

    @FXML
    public void handleResetFilters() {
        searchField.clear();
        cbStatutFilter.setValue("Tous les statuts");
        cbThemeFilter.setValue("Tous les thèmes");
        applyFilters();
    }

    @FXML
    public void handleAddLivre() {
        if (selectedMallette == null) return;
        String raw = tfAddLivre.getText().trim();
        if (raw.isEmpty()) return;

        try {
            Exemplaire ex = malletteService.ajouterLivre(selectedMallette.getId(), raw);
            tfAddLivre.clear();
            lblAddLivreFeedback.setText("✅ Livre ajouté avec succès (Exemplaire #" + ex.getId() + ")");
            lblAddLivreFeedback.setStyle("-fx-text-fill: #15803D; -fx-font-weight: 700;");
            SoundService.playSuccess();
            refreshMalletteDetails();
        } catch (RegleMetierException | SQLException e) {
            lblAddLivreFeedback.setText("❌ " + e.getMessage());
            lblAddLivreFeedback.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 700;");
            SoundService.playError();
        }
    }

    @FXML
    public void handleFillByTheme() {
        if (selectedMallette == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Remplir la mallette par thème");
        alert.setHeaderText("Remplissage automatique pour le thème « " + selectedMallette.getTheme() + " »");
        alert.setContentText("Tous les livres de la BCD correspondant à ce thème (et tous leurs exemplaires disponibles) seront intégrés à la boîte.\n\nContinuer ?");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int count = malletteService.remplirParTheme(selectedMallette.getId(), selectedMallette.getTheme());
                    refreshMalletteDetails();
                    SoundService.playSuccess();
                    showInfo("Remplissage terminé", count + " livre(s) ajouté(s) à la mallette !");
                } catch (RegleMetierException | SQLException e) {
                    showError("Erreur", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleIntegrateAllAvailableCopies() {
        if (selectedMallette == null) return;
        try {
            int added = malletteService.ajouterTousExemplairesDisponibles(selectedMallette.getId());
            refreshMalletteDetails();
            SoundService.playSuccess();
            showInfo("Exemplaires intégrés", added + " exemplaire(s) supplémentaire(s) intégré(s) à la mallette !");
        } catch (Exception e) {
            showError("Erreur", e.getMessage());
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

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnValider) {
                Utilisateur prof = cbEnseignant.getValue();
                LocalDate retour = dpRetour.getValue();
                if (prof == null) {
                    showError("Validation", "Veuillez choisir un enseignant.");
                    return false;
                }
                try {
                    malletteService.emprunterMallette(selectedMallette.getId(), prof, retour);
                    return true;
                } catch (RegleMetierException | SQLException e) {
                    showError("Erreur lors du prêt", e.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait().ifPresent(success -> {
            if (success) {
                loadDataKeepSelection();
                SoundService.playSuccess();
                showInfo("Prêt enregistré", "La mallette " + selectedMallette.getCode() + " est maintenant prêtée à " + selectedMallette.getEmprunteurComplet() + ".");
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
                    SoundService.playSuccess();
                    showInfo("Mallette restituée", "Tous les livres de la mallette sont de nouveau disponibles en BCD !");
                } catch (RegleMetierException | SQLException e) {
                    showError("Erreur lors de la restitution", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleViderMallette() {
        if (selectedMallette == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Vider la mallette");
        alert.setHeaderText("Retirer tous les livres de la mallette " + selectedMallette.getCode() + " ?");
        alert.setContentText("Tous les " + currentItems.size() + " livre(s) redeviendront disponibles en rayon BCD.");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    malletteService.viderMallette(selectedMallette.getId());
                    refreshMalletteDetails();
                    SoundService.playSuccess();
                    showInfo("Mallette vidée", "Tous les livres ont été retirés de la mallette.");
                } catch (SQLException e) {
                    showError("Erreur", e.getMessage());
                }
            }
        });
    }

    // =====================================================================
    //  Création, Édition, Suppression & Impressions
    // =====================================================================

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
                    mallettesListView.getSelectionModel().select(m);
                    break;
                }
            }
            showInfo("Mallette créée", "La mallette '" + newMallette.getTitre() + "' (" + newMallette.getCode() + ") a été créée avec " + scannedList.size() + " livre(s) !");
        });
    }

    @FXML
    public void handleEditMallette() {
        if (selectedMallette == null) return;

        Dialog<Mallette> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Modifier la mallette " + selectedMallette.getCode());
        dialog.setHeaderText("Modifiez les informations de la mallette « " + selectedMallette.getTitre() + " »");

        ButtonType btnSave = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnSave, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField tfTitre = new TextField(selectedMallette.getTitre());
        tfTitre.setPrefWidth(320);

        ComboBox<String> cbTheme = new ComboBox<>();
        cbTheme.setEditable(true);
        try {
            cbTheme.getItems().setAll(malletteDao.getAllThemes());
        } catch (SQLException ignored) {}
        cbTheme.setValue(selectedMallette.getTheme());
        cbTheme.setPrefWidth(320);

        ComboBox<String> cbNiveau = new ComboBox<>();
        cbNiveau.getItems().addAll("Tous niveaux", "Cycle 1 (Maternelle)", "Cycle 2 (CP - CE2)", "Cycle 3 (CM1 - CM2)");
        cbNiveau.setValue(selectedMallette.getNiveau().isEmpty() ? "Tous niveaux" : selectedMallette.getNiveau());
        cbNiveau.setPrefWidth(320);

        TextArea taDesc = new TextArea(selectedMallette.getDescription());
        taDesc.setPrefRowCount(3);
        taDesc.setWrapText(true);

        grid.addRow(0, new Label("Titre * :"), tfTitre);
        grid.addRow(1, new Label("Thème * :"), cbTheme);
        grid.addRow(2, new Label("Niveau :"), cbNiveau);
        grid.addRow(3, new Label("Description :"), taDesc);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == btnSave) {
                selectedMallette.setTitre(tfTitre.getText().trim());
                selectedMallette.setTheme(cbTheme.getValue());
                selectedMallette.setNiveau(cbNiveau.getValue());
                selectedMallette.setDescription(taDesc.getText().trim());
                try {
                    malletteDao.update(selectedMallette);
                    return selectedMallette;
                } catch (SQLException e) {
                    showError("Erreur de modification", e.getMessage());
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(m -> {
            loadDataKeepSelection();
            SoundService.playSuccess();
        });
    }

    @FXML
    public void handleDeleteMallette() {
        if (selectedMallette == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle("Supprimer la mallette");
        alert.setHeaderText("Supprimer la mallette " + selectedMallette.getCode() + " : « " + selectedMallette.getTitre() + " » ?");
        alert.setContentText("Attention : les livres qu'elle contient redeviendront disponibles en BCD.\nCette action est irréversible.");

        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    malletteService.supprimerMallette(selectedMallette.getId());
                    selectedMallette = null;
                    loadData();
                    SoundService.playSuccess();
                    showInfo("Mallette supprimée", "La mallette a été supprimée et ses livres ont été remis en rayon.");
                } catch (SQLException e) {
                    showError("Erreur de suppression", e.getMessage());
                }
            }
        });
    }

    @FXML
    public void handleImprimerFiche() {
        if (selectedMallette == null) return;
        try {
            File htmlFile = malletteService.genererFicheContenuHtml(selectedMallette.getId());
            MainApp.openDocument(htmlFile);
        } catch (Exception e) {
            showError("Erreur d'impression", "Impossible de générer la fiche : " + e.getMessage());
        }
    }

    @FXML
    public void handleImprimerEtiquette() {
        if (selectedMallette == null) return;
        try {
            File htmlFile = malletteService.genererEtiquetteMalletteHtml(selectedMallette.getId());
            MainApp.openDocument(htmlFile);
        } catch (Exception e) {
            showError("Erreur d'impression", "Impossible de générer l'étiquette : " + e.getMessage());
        }
    }

    // =====================================================================
    //  Helpers Notifications
    // =====================================================================

    private void showInfo(String title, String content) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        MainApp.prepareDialog(a);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    private void showError(String title, String content) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        MainApp.prepareDialog(a);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }
}
