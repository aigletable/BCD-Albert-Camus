package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.BoiteVerificationDao;
import fr.ecole.bcd.database.EmpruntDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.EtiquetteItem;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Exemplaire.Statut;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.LivreAVerifier;
import fr.ecole.bcd.service.BarcodeService;
import fr.ecole.bcd.service.BarcodeService.FormatPlanche;
import fr.ecole.bcd.service.GoogleBooksService;
import fr.ecole.bcd.service.LivreBatchImportService;
import fr.ecole.bcd.service.LivreBatchImportService.BatchReport;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreClassificationService.ClassificationResult;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.LivreMetadataService.MetadataResult;
import fr.ecole.bcd.service.InventaireSessionService;
import fr.ecole.bcd.service.RessourcePedagogiqueService;
import fr.ecole.bcd.service.SoundService;
import fr.ecole.bcd.view.AnimationHelper;
import fr.ecole.bcd.view.BoiteVerificationDialogHelper;
import fr.ecole.bcd.view.DialogHelper;
import fr.ecole.bcd.view.DouchetteDialogHelper;
import fr.ecole.bcd.view.EtiquettesDialogHelper;
import fr.ecole.bcd.view.RessourcesPedagogiquesDialog;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Contrôleur de l'onglet Inventaire & Livres.
 * <p>
 * Gère le catalogue des livres, la recherche temps réel, la fiche détaillée
 * avec couverture vérifiée, l'intégration multi-sources (BNF, Google Books, OpenLibrary, Epagine),
 * l'import par lot de fichiers CSV d'inventaire, et la gestion des exemplaires physiques.
 * </p>
 */
public class InventaireController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Cache en mémoire des images de couverture pour affichage instantané sans re-téléchargement. */
    private static final Map<String, Image> COVER_IMAGE_CACHE = new ConcurrentHashMap<>();

    // ── Composants FXML — Actions & Recherche ───────────────────────────
    @FXML private TextField searchField;
    @FXML private Button btnBoiteVerif;
    @FXML private Button btnAddByTitle;
    @FXML private Button btnModeDouchette;
    @FXML private Button btnOpenRecolement;
    @FXML private Button btnEtiquettes;
    @FXML private Button btnImportCsv;
    @FXML private Button btnRefreshCovers;
    @FXML private Button btnExportCatalogue;
    @FXML private Button btnAddLivre;
    @FXML private Button btnEditLivre;
    @FXML private Button btnDeleteLivre;
    @FXML private Button btnRessourcesLivre;
    @FXML private Button btnModeDouchetteTop;

    // ── Barre d'actions groupées (Sélection multiple) ────────────────────
    @FXML private HBox boxBatchActions;
    @FXML private Label lblBatchCount;
    @FXML private Button btnBatchLabels;
    @FXML private Button btnBatchDelete;

    // ── Composants FXML — Filtres Thématiques & Niveaux ────────────────
    @FXML private ComboBox<String> genreFilter;
    @FXML private ComboBox<String> themeFilter;
    @FXML private ComboBox<String> niveauFilter;
    @FXML private ToggleButton btnFilterZeroCopies;
    @FXML private Button btnResetFilters;
    @FXML private Button btnClassifyCatalogue;

    // ── Mode d'affichage Grille / Liste ────────────────────────────────
    @FXML private Button btnViewGrid;
    @FXML private Button btnViewList;
    @FXML private TableColumn<Livre, Livre> colCard;

    // ── Calque Modal In-App (Overlay anti-écran noir) ──────────────────
    @FXML private StackPane modalOverlay;
    @FXML private VBox modalCard;

    // ── Composants FXML — SplitPane & Catalogue (Gauche) ────────────────
    @FXML private SplitPane splitPane;
    @FXML private TableView<Livre> livreTableView;
    @FXML private TableColumn<Livre, String> colTitre;
    @FXML private TableColumn<Livre, String> colAuteur;
    @FXML private TableColumn<Livre, String> colGenre;
    @FXML private TableColumn<Livre, String> colNiveau;
    @FXML private TableColumn<Livre, String> colTheme;
    @FXML private TableColumn<Livre, String> colIsbn;
    @FXML private TableColumn<Livre, String> colEditeur;
    @FXML private TableColumn<Livre, String> colExemplaires;

    // ── Composants FXML — Fiche détaillée & Exemplaires (Droite) ────────
    @FXML private StackPane detailContainer;
    @FXML private VBox emptyDetailBox;
    @FXML private VBox detailContentBox;
    @FXML private StackPane coverBox;
    @FXML private ImageView imgCouverture;
    @FXML private Label lblNoCover;
    @FXML private ProgressIndicator coverProgress;
    @FXML private Button btnSearchCover;
    @FXML private Label lblTitre;
    @FXML private Label lblAuteur;
    @FXML private HBox boxBadges;
    @FXML private Label lblBadgeGenre;
    @FXML private Label lblBadgeTheme;
    @FXML private Label lblBadgeNiveau;
    @FXML private Label lblDescription;
    @FXML private Label lblIsbn;
    @FXML private Label lblEditeur;
    @FXML private Label lblExemplairesRatio;
    @FXML private Button btnAddExemplaire;
    @FXML private Button btnReassignExemplaire;
    @FXML private Button btnEditExemplaire;
    @FXML private Button btnDeleteExemplaire;
    @FXML private TableView<Exemplaire> exemplaireTableView;
    @FXML private TableColumn<Exemplaire, String> colExId;
    @FXML private TableColumn<Exemplaire, Exemplaire> colExStatut;
    @FXML private TableColumn<Exemplaire, Exemplaire> colExRemarque;
    @FXML private TableColumn<Exemplaire, String> colExDate;

    // ── Barre de statut ─────────────────────────────────────────────────
    @FXML private Label statusLabel;

    // ── DAOs & Services ─────────────────────────────────────────────────
    private final LivreDao livreDao = new LivreDao();
    private final ExemplaireDao exemplaireDao = new ExemplaireDao();
    private final EmpruntDao empruntDao = new EmpruntDao();
    private final GoogleBooksService googleBooksService = new GoogleBooksService();
    private final LivreMetadataService metadataService = new LivreMetadataService();
    private final LivreBatchImportService batchImportService = new LivreBatchImportService();
    private final LivreClassificationService classificationService = new LivreClassificationService();
    private final BoiteVerificationDao boiteVerificationDao = new BoiteVerificationDao();
    private final RessourcePedagogiqueService ressourcePedagogiqueService = new RessourcePedagogiqueService();

    // ── Données observables ─────────────────────────────────────────────
    private final ObservableList<Livre> masterLivres = FXCollections.observableArrayList();
    private FilteredList<Livre> filteredLivres;
    private final ObservableList<Exemplaire> masterExemplaires = FXCollections.observableArrayList();

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @FXML
    public void initialize() {
        configureLivreTable();
        configureFilterComboBoxes();
        configureExemplaireTable();
        configureCoverBox();

        // Filtrage en temps réel
        filteredLivres = new FilteredList<>(masterLivres, p -> true);
        livreTableView.setItems(filteredLivres);
        livreTableView.setPlaceholder(fr.ecole.bcd.view.EmptyStateHelper.create(
                "📚", "Aucun livre trouvé", "Essaie un mot-clé plus court ou réinitialise les filtres."));
        exemplaireTableView.setPlaceholder(fr.ecole.bcd.view.EmptyStateHelper.create(
                "📦", "Aucun exemplaire physique", "Ce titre ne possède aucun exemplaire enregistré en stock."));
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilter(newVal));

        // Sélection d'un livre
        livreTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            showLivreDetail(newVal);
        });

        // Barre d'actions groupées (sélection multiple)
        livreTableView.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<Livre>) c -> {
            updateBatchActionsBar();
        });

        // Sélection d'un exemplaire
        exemplaireTableView.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            btnDeleteExemplaire.setDisable(n == null);
            btnEditExemplaire.setDisable(n == null);
            if (btnReassignExemplaire != null) btnReassignExemplaire.setDisable(n == null);
        });

        // Boutons désactivés par défaut tant qu'aucun livre n'est sélectionné
        btnEditLivre.setDisable(true);
        btnDeleteLivre.setDisable(true);
        btnDeleteExemplaire.setDisable(true);
        btnEditExemplaire.setDisable(true);
        if (btnReassignExemplaire != null) btnReassignExemplaire.setDisable(true);
        if (btnSearchCover != null) btnSearchCover.setDisable(true);
        if (btnRessourcesLivre != null) btnRessourcesLivre.setDisable(true);

        // Chargement initial des données
        loadData();
        updateBoiteVerifBadge();

        // Vue par défaut : Grille moderne de cartes (comme dans la maquette)
        setViewGrid();
    }

    private void configureCoverBox() {
        // Arrondir les angles de l'image de couverture
        Rectangle clip = new Rectangle(85, 115);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        imgCouverture.setClip(clip);
    }

    private void configureLivreTable() {
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colTitre.setCellFactory(col -> new TableCell<>() {
            private final Label iconLbl = new Label("⚠️");
            private final Label titleLbl = new Label();
            private final Label badge = new Label("À renseigner");
            private final HBox warnBox = new HBox(6, iconLbl, titleLbl, badge);

            {
                warnBox.setAlignment(Pos.CENTER_LEFT);
                badge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-weight: 800; -fx-font-size: 10px; -fx-padding: 1 6; -fx-background-radius: 999; -fx-border-color: #fca5a5; -fx-border-radius: 999;");
                titleLbl.setStyle("-fx-font-weight: 800; -fx-text-fill: #dc2626;");
            }

            @Override
            protected void updateItem(String titre, boolean empty) {
                super.updateItem(titre, empty);
                if (empty || titre == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else if (fr.ecole.bcd.view.CoverHelper.isTitreNonRenseigne(titre)) {
                    setText(null);
                    titleLbl.setText(titre);
                    setGraphic(warnBox);
                    setStyle("-fx-background-color: rgba(254, 226, 226, 0.45);");
                } else {
                    setText(titre);
                    setGraphic(null);
                    setStyle("-fx-font-weight: 700;");
                }
            }
        });

        colAuteur.setCellValueFactory(cell -> {
            String aut = cell.getValue().getAuteur();
            return new SimpleStringProperty(aut != null && !aut.isBlank() ? aut : "–");
        });

        colIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        colIsbn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String isbn, boolean empty) {
                super.updateItem(isbn, empty);
                if (empty || isbn == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(isbn);
                    setStyle("-fx-font-family: monospace; -fx-alignment: CENTER; -fx-font-size: 11px;");
                }
            }
        });

        if (colEditeur != null) {
            colEditeur.setCellValueFactory(cell -> {
                String ed = cell.getValue().getEditeur();
                return new SimpleStringProperty(ed != null && !ed.isBlank() ? ed : "–");
            });
        }

        // Colonnes Genre, Thème et Niveau avec badges stylisés
        if (colGenre != null) {
            colGenre.setCellValueFactory(cell -> {
                String g = cell.getValue().getGenre();
                return new SimpleStringProperty(g != null && !g.isBlank() ? g : "–");
            });
            colGenre.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String genre, boolean empty) {
                    super.updateItem(genre, empty);
                    if (empty || genre == null || genre.equals("–")) {
                        setText(empty ? null : "–");
                        setGraphic(null);
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #94A3B8;");
                    } else {
                        Label badge = new Label(genre);
                        badge.getStyleClass().addAll("badge-genre", fr.ecole.bcd.view.CoverHelper.getStyleForGenre(genre).badgeClass());
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }

        if (colTheme != null) {
            colTheme.setCellValueFactory(cell -> {
                String t = cell.getValue().getTheme();
                return new SimpleStringProperty(t != null && !t.isBlank() ? t : "–");
            });
            colTheme.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String theme, boolean empty) {
                    super.updateItem(theme, empty);
                    if (empty || theme == null || theme.equals("–")) {
                        setText(empty ? null : "–");
                        setGraphic(null);
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #94A3B8;");
                    } else {
                        Label badge = new Label(theme);
                        badge.getStyleClass().add("badge-theme");
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }

        if (colNiveau != null) {
            colNiveau.setCellValueFactory(cell -> {
                String n = cell.getValue().getNiveau();
                return new SimpleStringProperty(n != null && !n.isBlank() ? n : "–");
            });
            colNiveau.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String niveau, boolean empty) {
                    super.updateItem(niveau, empty);
                    if (empty || niveau == null || niveau.equals("–")) {
                        setText(empty ? null : "–");
                        setGraphic(null);
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #94A3B8;");
                    } else {
                        Label badge = new Label(niveau);
                        badge.getStyleClass().add("badge-niveau");
                        setGraphic(badge);
                        setText(null);
                        setAlignment(Pos.CENTER);
                    }
                }
            });
        }

        // Colonne exemplaires avec rendu dynamique
        colExemplaires.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getExemplairesRatio()));
        colExemplaires.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Livre livre = getTableRow() != null ? getTableRow().getItem() : null;
                    setText(item);
                    if (livre != null) {
                        if (livre.getNbExemplaires() == 0) {
                            setStyle("-fx-text-fill: #999; -fx-alignment: CENTER;");
                        } else if (livre.getNbDisponibles() == 0) {
                            setStyle("-fx-text-fill: #d73a49; -fx-font-weight: bold; -fx-alignment: CENTER;");
                        } else {
                            setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-alignment: CENTER;");
                        }
                    } else {
                        setStyle("-fx-alignment: CENTER;");
                    }
                }
            }
        });

        // Configuration de la carte visuelle Grille
        if (colCard != null) {
            colCard.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
            colCard.setCellFactory(col -> new TableCell<>() {
                private final ImageView coverImg = new ImageView();
                private final Label coverPlaceholder = new Label("📖");
                private final StackPane thumbBox = new StackPane(coverPlaceholder, coverImg);

                private final Label titleLabel = new Label();
                private final Label authorLabel = new Label();
                private final Label statusPill = new Label();
                private final Label copiesLabel = new Label();
                private final Label niveauBadge = new Label();

                private final Region spacer = new Region();
                private final HBox bottomLine = new HBox(8, copiesLabel, spacer, niveauBadge);
                private final VBox rightInfo = new VBox(4, titleLabel, authorLabel, statusPill, bottomLine);
                private final HBox card = new HBox(14, thumbBox, rightInfo);

                {
                    thumbBox.setMinSize(56, 76);
                    thumbBox.setPrefSize(56, 76);
                    thumbBox.setMaxSize(56, 76);
                    thumbBox.getStyleClass().add("book-thumb-box");

                    Rectangle clip = new Rectangle(56, 76);
                    clip.setArcWidth(8);
                    clip.setArcHeight(8);
                    coverImg.setClip(clip);
                    coverImg.setFitWidth(56);
                    coverImg.setFitHeight(76);
                    coverImg.setPreserveRatio(false);

                    coverPlaceholder.getStyleClass().add("book-cover-placeholder");

                    titleLabel.getStyleClass().add("book-card-title");
                    authorLabel.getStyleClass().add("book-card-author");

                    HBox.setHgrow(spacer, Priority.ALWAYS);
                    copiesLabel.getStyleClass().add("book-card-copies");
                    niveauBadge.getStyleClass().add("book-card-niveau");

                    rightInfo.setAlignment(Pos.CENTER_LEFT);
                    HBox.setHgrow(rightInfo, Priority.ALWAYS);

                    card.setAlignment(Pos.CENTER_LEFT);
                    card.getStyleClass().add("book-catalog-card");
                }

                @Override
                protected void updateItem(Livre livre, boolean empty) {
                    super.updateItem(livre, empty);
                    if (empty || livre == null) {
                        setGraphic(null);
                        setText(null);
                    } else {
                        boolean nonRenseigne = fr.ecole.bcd.view.CoverHelper.isTitreNonRenseigne(livre.getTitre());
                        if (nonRenseigne) {
                            titleLabel.setText("⚠️ " + livre.getTitre());
                            titleLabel.setStyle("-fx-font-weight: 800; -fx-text-fill: #DC2626;");
                            statusPill.setText("⚠️ Titre à renseigner !");
                            statusPill.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B; -fx-font-weight: 800; -fx-border-color: #FCA5A5; -fx-border-radius: 999; -fx-background-radius: 999;");
                            card.setStyle("-fx-background-color: #FEF2F2; -fx-border-color: #F87171; -fx-border-width: 1.5; -fx-border-radius: 8; -fx-background-radius: 8;");
                        } else {
                            titleLabel.setText(livre.getTitre());
                            titleLabel.setStyle("");
                            card.setStyle("");
                            // Statut pill
                            if (livre.getNbDisponibles() > 0 && livre.getNbDisponibles() == livre.getNbExemplaires()) {
                                statusPill.setText("Disponible");
                                statusPill.getStyleClass().setAll("pill-green");
                                statusPill.setStyle("");
                            } else if (livre.getNbDisponibles() > 0) {
                                statusPill.setText("En partie disponible (" + livre.getNbDisponibles() + "/" + livre.getNbExemplaires() + ")");
                                statusPill.getStyleClass().setAll("pill-orange");
                                statusPill.setStyle("");
                            } else if (livre.getNbExemplaires() > 0) {
                                statusPill.setText("En prêt (0 dispo)");
                                statusPill.getStyleClass().setAll("pill-red");
                                statusPill.setStyle("");
                            } else {
                                statusPill.setText("0 exemplaire");
                                statusPill.getStyleClass().setAll("pill-blue");
                                statusPill.setStyle("");
                            }
                        }
                        authorLabel.setText(livre.getAuteur() != null && !livre.getAuteur().isBlank() ? livre.getAuteur() : "Auteur non renseigné");

                        copiesLabel.setText("📦 " + livre.getNbExemplaires() + " ex.");
                        niveauBadge.setText(livre.getNiveau() != null && !livre.getNiveau().isBlank() ? livre.getNiveau() : "Cycle 2");

                        // Couverture vérifiée
                        Image cached = null;
                        if (livre.getCouvertureUrl() != null && !livre.getCouvertureUrl().isBlank()) {
                            cached = COVER_IMAGE_CACHE.get(livre.getCouvertureUrl());
                            if (cached == null) {
                                try {
                                    cached = new Image(livre.getCouvertureUrl(), 56, 76, true, true, true);
                                    COVER_IMAGE_CACHE.put(livre.getCouvertureUrl(), cached);
                                } catch (Exception ignored) {}
                            }
                        }

                        thumbBox.getChildren().removeIf(n -> n != coverPlaceholder && n != coverImg);
                        if (cached != null && !cached.isError()) {
                            coverImg.setImage(cached);
                            coverImg.setVisible(true);
                            coverPlaceholder.setVisible(false);
                        } else {
                            coverImg.setImage(null);
                            coverImg.setVisible(false);
                            coverPlaceholder.setVisible(false);
                            Node styledCover = fr.ecole.bcd.view.CoverHelper.createStyledCover(livre, 56, 76);
                            thumbBox.getChildren().add(styledCover);
                        }

                        setGraphic(card);
                        setText(null);
                    }
                }
            });
        }

        livreTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        livreTableView.setRowFactory(tv -> {
            TableRow<Livre> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem printLabelsItem = new MenuItem("🏷️ Imprimer les étiquettes pour la sélection…");
            printLabelsItem.setOnAction(e -> handleEtiquettes());

            MenuItem copyIsbnItem = new MenuItem("📋 Copier l'ISBN");
            copyIsbnItem.setOnAction(e -> {
                Livre l = row.getItem();
                if (l != null && l.getIsbn() != null) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(l.getIsbn());
                    clipboard.setContent(content);
                }
            });

            MenuItem copyAllSelectedIsbnsItem = new MenuItem("📋 Copier les ISBNs sélectionnés");
            copyAllSelectedIsbnsItem.setOnAction(e -> {
                List<Livre> selected = livreTableView.getSelectionModel().getSelectedItems();
                if (selected != null && !selected.isEmpty()) {
                    String isbns = selected.stream()
                            .filter(Objects::nonNull)
                            .map(Livre::getIsbn)
                            .filter(Objects::nonNull)
                            .collect(Collectors.joining("\n"));
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(isbns);
                    clipboard.setContent(content);
                }
            });

            MenuItem copyTitleAuthorItem = new MenuItem("📋 Copier Titre & Auteur");
            copyTitleAuthorItem.setOnAction(e -> {
                Livre l = row.getItem();
                if (l != null) {
                    Clipboard clipboard = Clipboard.getSystemClipboard();
                    ClipboardContent content = new ClipboardContent();
                    content.putString(l.getTitre() + (l.getAuteur() != null ? " — " + l.getAuteur() : ""));
                    clipboard.setContent(content);
                }
            });

            MenuItem deleteBatchItem = new MenuItem("🗑️ Supprimer les livres sélectionnés…");
            deleteBatchItem.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: bold;");
            deleteBatchItem.setOnAction(e -> handleDeleteLivre());

            MenuItem selectAllItem = new MenuItem("☑️ Tout sélectionner");
            selectAllItem.setOnAction(e -> livreTableView.getSelectionModel().selectAll());

            contextMenu.getItems().addAll(printLabelsItem, deleteBatchItem, new SeparatorMenuItem(),
                    copyIsbnItem, copyAllSelectedIsbnsItem, copyTitleAuthorItem,
                    new SeparatorMenuItem(), selectAllItem);

            contextMenu.setOnShowing(ev -> {
                int selCount = livreTableView.getSelectionModel().getSelectedItems().size();
                if (selCount > 1) {
                    printLabelsItem.setText("🏷️ Imprimer les étiquettes (" + selCount + " livres sélectionnés)…");
                    deleteBatchItem.setText("🗑️ Supprimer les " + selCount + " livres sélectionnés…");
                    deleteBatchItem.setVisible(true);
                    copyAllSelectedIsbnsItem.setVisible(true);
                } else {
                    printLabelsItem.setText("🏷️ Imprimer les étiquettes de ce livre…");
                    deleteBatchItem.setText("🗑️ Supprimer ce livre…");
                    deleteBatchItem.setVisible(true);
                    copyAllSelectedIsbnsItem.setVisible(false);
                }
            });

            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditLivre();
                }
            });

            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );
            return row;
        });

        livreTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    @FXML
    public void setViewGrid() {
        if (livreTableView != null && colCard != null) {
            Livre selected = livreTableView.getSelectionModel().getSelectedItem();
            colCard.setVisible(true);
            livreTableView.getColumns().setAll(colCard);
            livreTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            if (selected != null) {
                livreTableView.getSelectionModel().select(selected);
                livreTableView.scrollTo(selected);
            }
            if (btnViewGrid != null) {
                btnViewGrid.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
                btnViewGrid.getStyleClass().add("btn-view-active");
                btnViewGrid.setStyle(null);
            }
            if (btnViewList != null) {
                btnViewList.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
                btnViewList.getStyleClass().add("btn-view-inactive");
                btnViewList.setStyle(null);
            }
        }
    }

    @FXML
    public void setViewList() {
        if (livreTableView != null) {
            Livre selected = livreTableView.getSelectionModel().getSelectedItem();
            if (colTitre != null) colTitre.setVisible(true);
            if (colAuteur != null) colAuteur.setVisible(true);
            if (colGenre != null) colGenre.setVisible(true);
            if (colNiveau != null) colNiveau.setVisible(true);
            if (colTheme != null) colTheme.setVisible(true);
            if (colEditeur != null) colEditeur.setVisible(true);
            if (colIsbn != null) colIsbn.setVisible(true);
            if (colExemplaires != null) colExemplaires.setVisible(true);

            livreTableView.getColumns().setAll(colTitre, colAuteur, colGenre, colNiveau, colTheme, colEditeur, colIsbn, colExemplaires);
            livreTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
            if (selected != null) {
                livreTableView.getSelectionModel().select(selected);
                livreTableView.scrollTo(selected);
            }
            if (btnViewGrid != null) {
                btnViewGrid.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
                btnViewGrid.getStyleClass().add("btn-view-inactive");
                btnViewGrid.setStyle(null);
            }
            if (btnViewList != null) {
                btnViewList.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
                btnViewList.getStyleClass().add("btn-view-active");
                btnViewList.setStyle(null);
            }
        }
    }

    private void configureFilterComboBoxes() {
        if (genreFilter != null) {
            ObservableList<String> list = FXCollections.observableArrayList();
            list.add("Tous les genres");
            list.addAll(LivreClassificationService.GENRES_STANDARDS);
            genreFilter.setItems(list);
            genreFilter.getSelectionModel().selectFirst();
            genreFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter(searchField.getText()));
        }

        if (themeFilter != null) {
            ObservableList<String> list = FXCollections.observableArrayList();
            list.add("Tous les thèmes");
            list.addAll(LivreClassificationService.THEMES_STANDARDS);
            themeFilter.setItems(list);
            themeFilter.getSelectionModel().selectFirst();
            themeFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter(searchField.getText()));
        }

        if (niveauFilter != null) {
            ObservableList<String> list = FXCollections.observableArrayList();
            list.add("Tous les niveaux");
            list.addAll(LivreClassificationService.NIVEAUX_STANDARDS);
            try {
                for (String dbNiv : livreDao.findAllNiveaux()) {
                    if (dbNiv != null && !dbNiv.isBlank() && !list.contains(dbNiv)) {
                        list.add(dbNiv);
                    }
                }
            } catch (Exception ignored) {}
            niveauFilter.setItems(list);
            niveauFilter.getSelectionModel().selectFirst();
            niveauFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyFilter(searchField.getText()));
        }
    }

    private void configureExemplaireTable() {
        colExId.setCellValueFactory(cell ->
                new SimpleStringProperty("#" + cell.getValue().getId()));
        colExId.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        // Colonne Statut avec badge visuel
        colExStatut.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        colExStatut.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Exemplaire ex, boolean empty) {
                super.updateItem(ex, empty);
                if (empty || ex == null) {
                    setGraphic(null);
                } else {
                    if (ex.getStatut() == Statut.DISPONIBLE) {
                        badge.setText("🟢 DISPONIBLE");
                        badge.getStyleClass().setAll("badge-disponible");
                    } else {
                        badge.setText("🟠 EMPRUNTÉ");
                        badge.getStyleClass().setAll("badge-emprunte");
                    }
                    setAlignment(Pos.CENTER);
                    setGraphic(badge);
                }
            }
        });

        // Colonne Remarque / État avec badge
        colExRemarque.setCellValueFactory(cell -> new javafx.beans.property.SimpleObjectProperty<>(cell.getValue()));
        colExRemarque.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(Exemplaire ex, boolean empty) {
                super.updateItem(ex, empty);
                if (empty || ex == null || ex.getRemarque().isBlank()) {
                    setGraphic(null);
                    setText("–");
                    setStyle("-fx-alignment: CENTER; -fx-text-fill: #8c959f;");
                } else {
                    setText(null);
                    String rem = ex.getRemarque();
                    badge.setText(rem);
                    if (rem.contains("Mauvais") || rem.contains("Abîmé") || rem.contains("Détérioré")) {
                        badge.setStyle("-fx-background-color: #ffebe9; -fx-text-fill: #cf222e; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else if (rem.contains("À réparer") || rem.contains("Réparation")) {
                        badge.setStyle("-fx-background-color: #fff8c5; -fx-text-fill: #9a6700; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else if (rem.contains("vérifier") || rem.contains("Attention")) {
                        badge.setStyle("-fx-background-color: #ddf4ff; -fx-text-fill: #0969da; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else if (rem.contains("Mallette")) {
                        badge.setStyle("-fx-background-color: #f3e8ff; -fx-text-fill: #7c3aed; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else {
                        badge.setStyle("-fx-background-color: #f6f8fa; -fx-text-fill: #24292f; -fx-padding: 2 6; -fx-background-radius: 10; -fx-font-size: 11px;");
                    }
                    setAlignment(Pos.CENTER);
                    setGraphic(badge);
                }
            }
        });

        colExDate.setCellValueFactory(cell -> {
            LocalDate d = cell.getValue().getDateAjout();
            return new SimpleStringProperty(d != null ? d.format(DATE_FMT) : "–");
        });
        colExDate.setStyle("-fx-alignment: CENTER;");

        exemplaireTableView.setItems(masterExemplaires);
        exemplaireTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        exemplaireTableView.setRowFactory(tv -> {
            TableRow<Exemplaire> row = new TableRow<>();
            ContextMenu cm = new ContextMenu();
            MenuItem itemReassign = new MenuItem("🔀 Séparer en nouveau livre / corriger l'affectation…");
            itemReassign.setOnAction(e -> handleReassignExemplaire());
            MenuItem itemRemarque = new MenuItem("⚠️ Modifier l'état / remarque…");
            itemRemarque.setOnAction(e -> handleEditExemplaireRemarque());
            MenuItem itemDelete = new MenuItem("🗑️ Supprimer cet exemplaire");
            itemDelete.setOnAction(e -> handleDeleteExemplaire());

            cm.getItems().addAll(itemReassign, itemRemarque, new SeparatorMenuItem(), itemDelete);
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(cm)
            );
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    handleEditExemplaireRemarque();
                }
            });
            return row;
        });
    }

    // =====================================================================
    //  Chargement & Filtrage
    // =====================================================================

    public void loadData() {
        try {
            List<Livre> livres = livreDao.findAllWithCounts();
            Livre currentSelected = livreTableView.getSelectionModel().getSelectedItem();
            String selectedIsbn = currentSelected != null ? currentSelected.getIsbn() : null;

            masterLivres.setAll(livres);

            // Restaurer la sélection si possible
            if (selectedIsbn != null) {
                Livre found = null;
                for (Livre l : masterLivres) {
                    if (selectedIsbn.equals(l.getIsbn())) {
                        found = l;
                        livreTableView.getSelectionModel().select(l);
                        break;
                    }
                }
                showLivreDetail(found);
            }

            updateStatusCounters();

        } catch (SQLException e) {
            showError("Erreur de chargement", "Impossible de charger les livres : " + e.getMessage());
        }
    }

    private void applyFilter(String query) {
        String lowerQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        String[] queryTokens = null;
        if (lowerQuery != null) {
            String normQ = LivreDao.stripAccents(lowerQuery);
            String[] raw = normQ.split("\\s+");
            List<String> list = new ArrayList<>();
            for (String r : raw) {
                if (!r.isBlank()) list.add(r.trim());
            }
            if (!list.isEmpty()) {
                queryTokens = list.toArray(new String[0]);
            }
        }
        final String[] activeTokens = queryTokens;

        String selGenre = (genreFilter != null && genreFilter.getValue() != null && !genreFilter.getValue().equals("Tous les genres"))
                ? genreFilter.getValue() : null;
        String selTheme = (themeFilter != null && themeFilter.getValue() != null && !themeFilter.getValue().equals("Tous les thèmes"))
                ? themeFilter.getValue() : null;
        String selNiveau = (niveauFilter != null && niveauFilter.getValue() != null && !niveauFilter.getValue().equals("Tous les niveaux"))
                ? niveauFilter.getValue() : null;
        boolean onlyZeroCopies = (btnFilterZeroCopies != null && btnFilterZeroCopies.isSelected());

        filteredLivres.setPredicate(livre -> {
            // Filtre rapide livres à 0 exemplaire
            if (onlyZeroCopies && livre.getNbExemplaires() > 0) {
                return false;
            }

            // 1. Recherche multi-mots clés plein texte (titre, auteur, éditeur, ISBN, genre, thème, niveau, synopsis)
            if (activeTokens != null && activeTokens.length > 0) {
                String fullBookText = LivreDao.stripAccents(
                        (livre.getTitre() != null ? livre.getTitre() : "") + " "
                        + (livre.getAuteur() != null ? livre.getAuteur() : "") + " "
                        + (livre.getEditeur() != null ? livre.getEditeur() : "") + " "
                        + (livre.getIsbn() != null ? livre.getIsbn() : "") + " "
                        + (livre.getGenre() != null ? livre.getGenre() : "") + " "
                        + (livre.getTheme() != null ? livre.getTheme() : "") + " "
                        + (livre.getNiveau() != null ? livre.getNiveau() : "") + " "
                        + (livre.getDescription() != null ? livre.getDescription() : "")
                );

                for (String token : activeTokens) {
                    boolean tokenMatched = fullBookText.contains(token)
                            || (livre.getNiveau() != null && LivreClassificationService.matchesNiveau(livre.getNiveau(), token));
                    if (!tokenMatched) {
                        return false;
                    }
                }
            }

            // 2. Filtre par Genre
            if (selGenre != null) {
                if (livre.getGenre() == null || !livre.getGenre().equalsIgnoreCase(selGenre)) {
                    return false;
                }
            }

            // 3. Filtre par Thème
            if (selTheme != null) {
                if (livre.getTheme() == null || !livre.getTheme().equalsIgnoreCase(selTheme)) {
                    return false;
                }
            }

            // 4. Filtre par Niveau hiérarchique & inclusif (Cycles et Classes)
            if (selNiveau != null) {
                if (!LivreClassificationService.matchesNiveau(livre.getNiveau(), selNiveau)) {
                    return false;
                }
            }

            return true;
        });

        updateStatusCounters();
    }

    @FXML
    private void handleToggleFilterZeroCopies() {
        applyFilter(searchField != null ? searchField.getText() : "");
    }

    @FXML
    private void handleResetFilters() {
        if (searchField != null) searchField.clear();
        if (genreFilter != null) genreFilter.getSelectionModel().selectFirst();
        if (themeFilter != null) themeFilter.getSelectionModel().selectFirst();
        if (niveauFilter != null) niveauFilter.getSelectionModel().selectFirst();
        if (btnFilterZeroCopies != null) btnFilterZeroCopies.setSelected(false);
        applyFilter("");
    }

    @FXML
    private void handleClassifyCatalogue() {
        if (masterLivres.isEmpty()) {
            showInfo("Catalogue vide", "Aucun livre à classifier.");
            return;
        }

        long unclassified = masterLivres.stream()
                .filter(l -> l.getGenre() == null || l.getTheme() == null || l.getNiveau() == null
                        || l.getGenre().isBlank() || l.getTheme().isBlank() || l.getNiveau().isBlank())
                .count();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(confirm);
        confirm.setTitle("Classification automatique du catalogue");
        confirm.setHeaderText("Classifier automatiquement les livres de la BCD");
        confirm.setContentText(String.format(
                "Le catalogue compte %d livre(s), dont %d non classé(s) ou partiellement classé(s).\n\n"
                        + "L'analyse sémantique automatique va inspecter les titres, auteurs, éditeurs et descriptions "
                        + "pour leur attribuer automatiquement le Genre (BD, Roman, Album...), le Thème "
                        + "(Animaux, Guerre & Histoire, Nature...) et le Niveau scolaire (CP, CE1, CM2...).",
                masterLivres.size(), unclassified));

        ButtonType btnRunUnclass = new ButtonType("Classifier les " + unclassified + " incomplet(s)");
        ButtonType btnRunAll = new ButtonType("Reclassifier TOUT (" + masterLivres.size() + ")");
        confirm.getButtonTypes().setAll(btnRunUnclass, btnRunAll, ButtonType.CANCEL);

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;

        boolean reclassifyAll = (choice.get() == btnRunAll);

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                int count = 0;
                for (Livre l : masterLivres) {
                    boolean need = reclassifyAll || l.getGenre() == null || l.getTheme() == null || l.getNiveau() == null
                            || l.getGenre().isBlank() || l.getTheme().isBlank() || l.getNiveau().isBlank();
                    if (!need) continue;

                    ClassificationResult res = classificationService.classifySimple(
                            l.getTitre(),
                            l.getAuteur(),
                            l.getEditeur(),
                            l.getDescription()
                    );

                    if (l.getGenre() == null || l.getGenre().isBlank() || reclassifyAll) {
                        l.setGenre(res.getGenre());
                    }
                    if (l.getTheme() == null || l.getTheme().isBlank() || reclassifyAll) {
                        l.setTheme(res.getTheme());
                    }
                    if (l.getNiveau() == null || l.getNiveau().isBlank() || reclassifyAll) {
                        l.setNiveau(res.getNiveau());
                    }

                    livreDao.update(l);
                    count++;
                }
                return count;
            }
        };

        task.setOnSucceeded(e -> {
            int updated = task.getValue();
            loadData();
            showInfo("Classification terminée", String.format(
                    "🪄 %d livre(s) ont été classés avec succès selon leurs genres, thèmes et niveaux scolaires !",
                    updated));
        });

        task.setOnFailed(e -> {
            showError("Erreur de classification", task.getException().getMessage());
        });

        new Thread(task).start();
    }

    private void updateStatusCounters() {
        int totalLivres = masterLivres.size();
        int totalEx = 0;
        int totalDispo = 0;
        int zeroCount = 0;
        for (Livre l : masterLivres) {
            totalEx += l.getNbExemplaires();
            totalDispo += l.getNbDisponibles();
            if (l.getNbExemplaires() == 0) {
                zeroCount++;
            }
        }

        if (btnFilterZeroCopies != null) {
            btnFilterZeroCopies.setText("⚠️ 0 exemplaire (" + zeroCount + ")");
            if (zeroCount > 0) {
                btnFilterZeroCopies.setStyle("-fx-font-size: 11.5px; -fx-padding: 5px 12px; -fx-cursor: hand; -fx-text-fill: #b45309; -fx-border-color: #f59e0b; -fx-font-weight: bold;");
            } else {
                btnFilterZeroCopies.setStyle("-fx-font-size: 11.5px; -fx-padding: 5px 12px; -fx-cursor: hand;");
            }
        }

        updateBoiteVerifBadge();

        int visibleLivres = filteredLivres.size();
        if (visibleLivres == totalLivres) {
            updateStatus(String.format("📚 %d titre(s) • %d exemplaire(s) physique(s) (%d disponible(s))%s",
                    totalLivres, totalEx, totalDispo,
                    zeroCount > 0 ? " • ⚠️ " + zeroCount + " à 0 exemplaire" : ""));
        } else {
            updateStatus(String.format("🔍 %d / %d titre(s) affiché(s) • %d exemplaire(s) au total",
                    visibleLivres, totalLivres, totalEx));
        }
    }

    public void updateBoiteVerifBadge() {
        int count = boiteVerificationDao.countPending();
        if (btnBoiteVerif != null) {
            if (count > 0) {
                btnBoiteVerif.setText("📦 À vérifier (" + count + ")");
                btnBoiteVerif.setStyle("-fx-font-weight: bold; -fx-border-color: #0969da; -fx-text-fill: #0969da;");
            } else {
                btnBoiteVerif.setText("📦 Boîte à vérifier");
                btnBoiteVerif.setStyle("");
            }
        }
    }

    // =====================================================================
    //  Affichage du détail d'un livre
    // =====================================================================

    private void showLivreDetail(Livre livre) {
        if (livre == null) {
            emptyDetailBox.setVisible(true);
            detailContentBox.setVisible(false);
            btnEditLivre.setDisable(true);
            btnDeleteLivre.setDisable(true);
            if (btnSearchCover != null) btnSearchCover.setDisable(true);
            if (btnRessourcesLivre != null) btnRessourcesLivre.setDisable(true);
            if (boxBadges != null) {
                boxBadges.setVisible(false);
                boxBadges.setManaged(false);
            }
            if (lblDescription != null) {
                lblDescription.setText("");
                lblDescription.setVisible(false);
                lblDescription.setManaged(false);
            }
            masterExemplaires.clear();
            return;
        }

        emptyDetailBox.setVisible(false);
        detailContentBox.setVisible(true);
        btnEditLivre.setDisable(false);
        btnDeleteLivre.setDisable(false);
        if (btnSearchCover != null) btnSearchCover.setDisable(false);
        if (btnRessourcesLivre != null) {
            btnRessourcesLivre.setDisable(false);
            updateRessourcesButton(livre);
        }

        // Métadonnées
        boolean nonRenseigne = fr.ecole.bcd.view.CoverHelper.isTitreNonRenseigne(livre.getTitre());
        if (nonRenseigne) {
            lblTitre.setText("⚠️ " + livre.getTitre() + "  [Titre à renseigner !]");
            lblTitre.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 800;");
        } else {
            lblTitre.setText(livre.getTitre());
            lblTitre.setStyle("");
        }
        lblAuteur.setText(livre.getAuteur() != null && !livre.getAuteur().isBlank()
                ? livre.getAuteur() : "Auteur non renseigné");
        lblIsbn.setText(livre.getIsbn() != null ? livre.getIsbn() : "–");
        lblEditeur.setText(livre.getEditeur() != null && !livre.getEditeur().isBlank()
                ? livre.getEditeur() : "Non précisé");
        lblExemplairesRatio.setText(livre.getNbDisponibles() + "/" + livre.getNbExemplaires() + " dispo");
        lblExemplairesRatio.getStyleClass().setAll(livre.getNbDisponibles() > 0 ? "pill-green" : "pill-orange");

        // Badges Genre, Thème, Niveau
        boolean hasBadge = false;
        if (lblBadgeGenre != null) {
            String g = livre.getGenre();
            if (g != null && !g.isBlank()) {
                lblBadgeGenre.setText(g);
                lblBadgeGenre.getStyleClass().setAll("badge-genre", fr.ecole.bcd.view.CoverHelper.getStyleForGenre(g).badgeClass());
                lblBadgeGenre.setVisible(true);
                lblBadgeGenre.setManaged(true);
                hasBadge = true;
            } else {
                lblBadgeGenre.setVisible(false);
                lblBadgeGenre.setManaged(false);
            }
        }
        if (lblBadgeTheme != null) {
            String t = livre.getTheme();
            if (t != null && !t.isBlank()) {
                lblBadgeTheme.setText(t);
                lblBadgeTheme.setVisible(true);
                lblBadgeTheme.setManaged(true);
                hasBadge = true;
            } else {
                lblBadgeTheme.setVisible(false);
                lblBadgeTheme.setManaged(false);
            }
        }
        if (lblBadgeNiveau != null) {
            String n = livre.getNiveau();
            if (n != null && !n.isBlank()) {
                lblBadgeNiveau.setText(n);
                lblBadgeNiveau.setVisible(true);
                lblBadgeNiveau.setManaged(true);
                hasBadge = true;
            } else {
                lblBadgeNiveau.setVisible(false);
                lblBadgeNiveau.setManaged(false);
            }
        }
        if (boxBadges != null) {
            boxBadges.setVisible(hasBadge);
            boxBadges.setManaged(hasBadge);
        }

        // Synopsis / Description
        if (lblDescription != null) {
            String desc = livre.getDescription();
            if (desc != null && !desc.isBlank()) {
                lblDescription.setText(desc);
                lblDescription.setVisible(true);
                lblDescription.setManaged(true);
            } else {
                lblDescription.setText("");
                lblDescription.setVisible(false);
                lblDescription.setManaged(false);
            }
        }

        // Couverture avec cache mémoire, indicateur de chargement et fausse couverture stylisée Apple Books
        coverBox.getChildren().removeIf(n -> n != imgCouverture && n != lblNoCover && n != coverProgress);
        lblNoCover.setVisible(false);

        String url = livre.getCouvertureUrl();
        if (url != null && !url.isBlank()) {
            Image cached = COVER_IMAGE_CACHE.get(url);
            if (cached != null) {
                if (cached.isError()) {
                    imgCouverture.setImage(null);
                    imgCouverture.setVisible(false);
                    coverBox.getChildren().add(0, fr.ecole.bcd.view.CoverHelper.createStyledCover(livre, 88, 120));
                    if (coverProgress != null) coverProgress.setVisible(false);
                } else {
                    imgCouverture.setImage(cached);
                    imgCouverture.setVisible(true);
                    if (coverProgress != null) coverProgress.setVisible(false);
                }
            } else {
                imgCouverture.setImage(null);
                imgCouverture.setVisible(false);
                if (coverProgress != null) coverProgress.setVisible(true);

                try {
                    Image img = new Image(url, true); // chargement asynchrone
                    img.errorProperty().addListener((obs, oldVal, hasError) -> {
                        if (hasError) {
                            Platform.runLater(() -> {
                                COVER_IMAGE_CACHE.put(url, img);
                                imgCouverture.setImage(null);
                                imgCouverture.setVisible(false);
                                coverBox.getChildren().removeIf(n -> n != imgCouverture && n != lblNoCover && n != coverProgress);
                                coverBox.getChildren().add(0, fr.ecole.bcd.view.CoverHelper.createStyledCover(livre, 88, 120));
                                if (coverProgress != null) coverProgress.setVisible(false);
                            });
                        }
                    });
                    img.progressProperty().addListener((obs, oldVal, progress) -> {
                        if (progress.doubleValue() >= 1.0 && !img.isError()) {
                            COVER_IMAGE_CACHE.put(url, img);
                            Platform.runLater(() -> {
                                coverBox.getChildren().removeIf(n -> n != imgCouverture && n != lblNoCover && n != coverProgress);
                                imgCouverture.setImage(img);
                                imgCouverture.setVisible(true);
                                if (coverProgress != null) coverProgress.setVisible(false);
                            });
                        }
                    });
                    imgCouverture.setImage(img);
                } catch (Exception e) {
                    imgCouverture.setImage(null);
                    imgCouverture.setVisible(false);
                    coverBox.getChildren().add(0, fr.ecole.bcd.view.CoverHelper.createStyledCover(livre, 88, 120));
                    if (coverProgress != null) coverProgress.setVisible(false);
                }
            }
        } else {
            imgCouverture.setImage(null);
            imgCouverture.setVisible(false);
            coverBox.getChildren().add(0, fr.ecole.bcd.view.CoverHelper.createStyledCover(livre, 88, 120));
            if (coverProgress != null) coverProgress.setVisible(false);
        }

        // Charger les exemplaires physiques
        loadExemplairesForLivre(livre.getIsbn());
    }

    private void loadExemplairesForLivre(String isbn) {
        try {
            List<Exemplaire> list = exemplaireDao.findByIsbn(isbn);
            masterExemplaires.setAll(list);
        } catch (SQLException e) {
            showError("Erreur", "Impossible de charger les exemplaires : " + e.getMessage());
        }
    }

    // =====================================================================
    //  Actions — Nouveau Livre (par Titre ou par ISBN)
    // =====================================================================

    @FXML
    private void handleAddByTitle() {
        Window owner = livreTableView != null && livreTableView.getScene() != null
                ? livreTableView.getScene().getWindow() : null;
        fr.ecole.bcd.view.LivreVerificationDialogHelper.openAddByTitleDialog(
                owner,
                livreDao,
                exemplaireDao,
                boiteVerificationDao,
                metadataService,
                null,
                null,
                newLivre -> {
                    loadData();
                    if (masterLivres != null) {
                        for (Livre l : masterLivres) {
                            if (newLivre.getIsbn().equals(l.getIsbn())) {
                                livreTableView.getSelectionModel().select(l);
                                livreTableView.scrollTo(l);
                                break;
                            }
                        }
                    }
                    updateStatus("✅ Livre '" + newLivre.getTitre() + "' ajouté avec succès au catalogue.");
                }
        );
    }

    @FXML
    private void handleAddLivre() {
        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Ajouter un nouveau livre");
        dialog.setHeaderText("Saisissez l'ISBN pour remplir automatiquement les informations via la BNF, Google Books ou OpenLibrary, ou saisissez-les manuellement.");

        ButtonType saveButtonType = new ButtonType("Enregistrer le livre", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        // Formulaire
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 16, 20));

        TextField tfIsbn = new TextField();
        tfIsbn.setPromptText("Ex: 9782070612758");
        Button btnSearchCascade = new Button("🔍 Rechercher");
        Button btnGenCode = new Button("🏷️ Code BCD sans ISBN");
        btnGenCode.getStyleClass().add("btn-outline");
        btnGenCode.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        btnGenCode.setTooltip(new Tooltip("Générer un code interne pour un livre ancien sans ISBN ou une revue (ex: CAMUS-0042)"));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);

        Label lblSearchStatus = new Label("💡 Entrez l'ISBN pour recherche automatique, ou laissez vide pour un livre ancien sans code-barres.");
        lblSearchStatus.setStyle("-fx-text-fill: #586069; -fx-font-size: 11px;");

        btnGenCode.setOnAction(e -> {
            try {
                String candidate = livreDao.generateUniqueBcdCode();
                tfIsbn.setText(candidate);
                lblSearchStatus.setText("💡 Code BCD généré (" + candidate + ") pour ouvrage sans ISBN. Vous pourrez lui imprimer une étiquette code-barres.");
                lblSearchStatus.setStyle("-fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px;");
            } catch (Exception ignored) {
                tfIsbn.setText("CAMUS-" + (System.currentTimeMillis() % 100000));
            }
        });

        HBox isbnBox = new HBox(8, tfIsbn, btnSearchCascade, btnGenCode, spinner);
        HBox.setHgrow(tfIsbn, Priority.ALWAYS);
        isbnBox.setAlignment(Pos.CENTER_LEFT);

        TextField tfTitre = new TextField();
        tfTitre.setPromptText("Titre de l'ouvrage (obligatoire)");
        TextField tfAuteur = new TextField();
        tfAuteur.setPromptText("Nom de l'auteur");
        TextField tfEditeur = new TextField();
        tfEditeur.setPromptText("Maison d'édition");
        TextField tfCouverture = new TextField();
        tfCouverture.setPromptText("https://... (URL de l'image de couverture)");

        ComboBox<String> cbGenre = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.GENRES_STANDARDS));
        cbGenre.setEditable(true);
        cbGenre.setPromptText("Ex: BD, Roman, Album...");

        ComboBox<String> cbTheme = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.THEMES_STANDARDS));
        cbTheme.setEditable(true);
        cbTheme.setPromptText("Ex: Guerre & Histoire, Animaux...");

        ComboBox<String> cbNiveau = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.NIVEAUX_STANDARDS));
        cbNiveau.setEditable(true);
        cbNiveau.setPromptText("Ex: CP, CE1, CM2...");

        TextArea taDescription = new TextArea();
        taDescription.setPromptText("Résumé ou synopsis du livre…");
        taDescription.setPrefRowCount(3);
        taDescription.setWrapText(true);

        Spinner<Integer> spNbCopies = new Spinner<>(1, 50, 1);
        spNbCopies.setEditable(true);

        grid.add(new Label("ISBN (facultatif si ancien) :"), 0, 0);
        grid.add(isbnBox, 1, 0);
        grid.add(lblSearchStatus, 1, 1);
        grid.add(new Label("Titre : *"), 0, 2);
        grid.add(tfTitre, 1, 2);
        grid.add(new Label("Auteur :"), 0, 3);
        grid.add(tfAuteur, 1, 3);
        grid.add(new Label("Éditeur :"), 0, 4);
        grid.add(tfEditeur, 1, 4);
        grid.add(new Label("Genre :"), 0, 5);
        grid.add(cbGenre, 1, 5);
        grid.add(new Label("Thème :"), 0, 6);
        grid.add(cbTheme, 1, 6);
        grid.add(new Label("Niveau scolaire :"), 0, 7);
        grid.add(cbNiveau, 1, 7);

        Button btnFindCoverSimple = new Button("🔍 Trouver image");
        btnFindCoverSimple.getStyleClass().add("btn-outline");
        btnFindCoverSimple.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");
        btnFindCoverSimple.setOnAction(e -> {
            String t = tfTitre.getText().trim();
            String a = tfAuteur.getText().trim();
            String is = tfIsbn.getText().trim();
            if (!t.isEmpty() || !is.isEmpty()) {
                btnFindCoverSimple.setDisable(true);
                lblSearchStatus.setText("🔍 Recherche d'image de couverture en haute résolution…");
                lblSearchStatus.setStyle("-fx-text-fill: #0366d6; -fx-font-size: 11px;");
                Task<String> tCover = new Task<>() {
                    @Override
                    protected String call() {
                        return metadataService.findVerifiedCover(is, "", t, a);
                    }
                };
                tCover.setOnSucceeded(ev -> {
                    btnFindCoverSimple.setDisable(false);
                    String found = tCover.getValue();
                    if (found != null && !found.isBlank()) {
                        tfCouverture.setText(found);
                        lblSearchStatus.setText("✅ Couverture trouvée et attachée !");
                        lblSearchStatus.setStyle("-fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px;");
                    } else {
                        lblSearchStatus.setText("ℹ️ Aucune image trouvée en ligne pour ce livre.");
                        lblSearchStatus.setStyle("-fx-text-fill: #d97706; -fx-font-size: 11px;");
                    }
                });
                tCover.setOnFailed(ev -> btnFindCoverSimple.setDisable(false));
                new Thread(tCover).start();
            }
        });
        HBox coverBox = new HBox(8, tfCouverture, btnFindCoverSimple);
        HBox.setHgrow(tfCouverture, Priority.ALWAYS);
        coverBox.setAlignment(Pos.CENTER_LEFT);

        grid.add(new Label("URL Couverture :"), 0, 8);
        grid.add(coverBox, 1, 8);
        grid.add(new Label("Description / Résumé :"), 0, 9);
        grid.add(taDescription, 1, 9);
        grid.add(new Label("Exemplaire(s) initiaux :"), 0, 10);
        grid.add(spNbCopies, 1, 10);

        dialog.getDialogPane().setContent(grid);

        // Validation du bouton Enregistrer : seul le titre est obligatoire
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        Runnable validate = () -> {
            boolean valid = !tfTitre.getText().isBlank();
            saveButton.setDisable(!valid);
        };
        saveButton.setDisable(true);
        tfIsbn.textProperty().addListener((o, oldV, newV) -> validate.run());
        tfTitre.textProperty().addListener((o, oldV, newV) -> validate.run());

        // Recherche multi-sources en cascade (BNF -> Google Books -> OpenLibrary -> Couverture Epagine)
        Runnable runMultiSearch = () -> {
            String rawIsbn = tfIsbn.getText().trim();
            String clean = LivreMetadataService.cleanIsbn(rawIsbn);
            if (clean.isEmpty()) {
                String queryTitre = tfTitre.getText().trim();
                String queryAuteur = tfAuteur.getText().trim();
                if (!queryTitre.isEmpty()) {
                    btnSearchCascade.setDisable(true);
                    spinner.setVisible(true);
                    lblSearchStatus.setText("🔍 Recherche par Titre en ligne (OpenLibrary, Google Books, BNF)…");
                    lblSearchStatus.setStyle("-fx-text-fill: #0366d6; -fx-font-size: 11px;");

                    Task<List<MetadataResult>> taskTitre = new Task<>() {
                        @Override
                        protected List<MetadataResult> call() {
                            return metadataService.rechercherParTitreAuteur(queryTitre, queryAuteur, 3);
                        }
                    };
                    taskTitre.setOnSucceeded(e -> {
                        btnSearchCascade.setDisable(false);
                        spinner.setVisible(false);
                        List<MetadataResult> foundList = taskTitre.getValue();
                        if (foundList != null && !foundList.isEmpty()) {
                            MetadataResult found = foundList.get(0);
                            tfTitre.setText(found.getTitre());
                            if (found.getAuteur() != null) tfAuteur.setText(found.getAuteur());
                            if (found.getEditeur() != null) tfEditeur.setText(found.getEditeur());
                            if (found.getCouvertureUrl() != null && !found.getCouvertureUrl().isBlank()) {
                                tfCouverture.setText(found.getCouvertureUrl());
                            }
                            if (found.getGenre() != null && !found.getGenre().isBlank()) cbGenre.setValue(found.getGenre());
                            if (found.getTheme() != null && !found.getTheme().isBlank()) cbTheme.setValue(found.getTheme());
                            if (found.getNiveau() != null && !found.getNiveau().isBlank()) cbNiveau.setValue(found.getNiveau());
                            if (found.getDescription() != null && !found.getDescription().isBlank()) taDescription.setText(found.getDescription());

                            if (found.getIsbn() != null && !found.getIsbn().isBlank()) {
                                tfIsbn.setText(found.getIsbn());
                                lblSearchStatus.setText("✅ Livre trouvé via " + found.getSource() + " (ISBN officiel : " + found.getIsbn() + ") !");
                            } else {
                                btnGenCode.fire();
                                lblSearchStatus.setText("✅ Informations trouvées via " + found.getSource() + " ! Code BCD généré.");
                            }
                            lblSearchStatus.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 11px;");
                        } else {
                            lblSearchStatus.setText("ℹ️ Aucun résultat en ligne pour ce titre. Saisie manuelle conservée.");
                            lblSearchStatus.setStyle("-fx-text-fill: #d97706; -fx-font-size: 11px;");
                        }
                        validate.run();
                    });
                    taskTitre.setOnFailed(e -> {
                        btnSearchCascade.setDisable(false);
                        spinner.setVisible(false);
                        lblSearchStatus.setText("⚠️ Échec réseau lors de la recherche. Saisie manuelle possible.");
                        lblSearchStatus.setStyle("-fx-text-fill: #d73a49; -fx-font-size: 11px;");
                    });
                    new Thread(taskTitre).start();
                    return;
                }

                lblSearchStatus.setText("⚠️ Veuillez renseigner un ISBN valide, ou au moins un Titre d'ouvrage.");
                lblSearchStatus.setStyle("-fx-text-fill: #d73a49; -fx-font-size: 11px;");
                return;
            }

            tfIsbn.setText(clean);
            btnSearchCascade.setDisable(true);
            spinner.setVisible(true);
            lblSearchStatus.setText("Interrogation BNF, Google Books & OpenLibrary…");
            lblSearchStatus.setStyle("-fx-text-fill: #0366d6; -fx-font-size: 11px;");

            Task<MetadataResult> task = new Task<>() {
                @Override
                protected MetadataResult call() {
                    return metadataService.rechercher(clean);
                }
            };

            task.setOnSucceeded(e -> {
                btnSearchCascade.setDisable(false);
                spinner.setVisible(false);
                MetadataResult found = task.getValue();
                if (found != null && found.isFound()) {
                    tfTitre.setText(found.getTitre());
                    if (found.getAuteur() != null) tfAuteur.setText(found.getAuteur());
                    if (found.getEditeur() != null) tfEditeur.setText(found.getEditeur());
                    if (found.getCouvertureUrl() != null && !found.getCouvertureUrl().isBlank()) {
                        tfCouverture.setText(found.getCouvertureUrl());
                    }
                    if (found.getGenre() != null && !found.getGenre().isBlank()) {
                        cbGenre.setValue(found.getGenre());
                    }
                    if (found.getTheme() != null && !found.getTheme().isBlank()) {
                        cbTheme.setValue(found.getTheme());
                    }
                    if (found.getNiveau() != null && !found.getNiveau().isBlank()) {
                        cbNiveau.setValue(found.getNiveau());
                    }
                    if (found.getDescription() != null && !found.getDescription().isBlank()) {
                        taDescription.setText(found.getDescription());
                    }
                    String coverNotice = (found.getCouvertureUrl() != null && !found.getCouvertureUrl().isBlank())
                            ? " + Couverture vérifiée" : "";
                    String classNotice = (found.getGenre() != null ? " [" + found.getGenre() + " • " + found.getNiveau() + "]" : "");
                    lblSearchStatus.setText("✅ Informations trouvées via " + found.getSource() + coverNotice + classNotice + " !");
                    lblSearchStatus.setStyle("-fx-text-fill: #28a745; -fx-font-weight: bold; -fx-font-size: 11px;");
                } else if (found != null && found.getCouvertureUrl() != null && !found.getCouvertureUrl().isBlank()) {
                    tfCouverture.setText(found.getCouvertureUrl());
                    lblSearchStatus.setText("📷 Couverture vérifiée trouvée ! Saisie manuelle du titre requise.");
                    lblSearchStatus.setStyle("-fx-text-fill: #d97706; -fx-font-size: 11px;");
                } else {
                    lblSearchStatus.setText("ℹ️ Aucun résultat trouvé sur BNF, Google Books ou OpenLibrary. Saisie manuelle.");
                    lblSearchStatus.setStyle("-fx-text-fill: #d97706; -fx-font-size: 11px;");
                }
                validate.run();
            });

            task.setOnFailed(e -> {
                btnSearchCascade.setDisable(false);
                spinner.setVisible(false);
                lblSearchStatus.setText("⚠️ Échec réseau lors de la recherche. Saisie manuelle possible.");
                lblSearchStatus.setStyle("-fx-text-fill: #d73a49; -fx-font-size: 11px;");
            });

            new Thread(task).start();
        };

        btnSearchCascade.setOnAction(e -> runMultiSearch.run());
        tfIsbn.setOnAction(e -> runMultiSearch.run());

        Platform.runLater(tfIsbn::requestFocus);

        dialog.setResultConverter(btnType -> {
            if (btnType == saveButtonType) {
                String cleanIsbn = LivreMetadataService.cleanIsbn(tfIsbn.getText().trim());
                if (cleanIsbn.isEmpty()) {
                    // Livre ancien sans code-barres / ISBN : génération automatique immédiate en arrière-plan
                    try {
                        int count = masterLivres != null ? masterLivres.size() : livreDao.count();
                        cleanIsbn = "CAMUS-" + String.format("%04d", count + 1);
                        int offset = 1;
                        while (livreDao.findByIsbn(cleanIsbn) != null) {
                            cleanIsbn = "CAMUS-" + String.format("%04d", count + 1 + offset++);
                        }
                    } catch (Exception ignored) {
                        cleanIsbn = "CAMUS-" + (System.currentTimeMillis() % 100000);
                    }
                }
                String cover = tfCouverture.getText().trim();
                if (cover.isEmpty()) {
                    try {
                        String autoC = metadataService.findVerifiedCover(cleanIsbn, "", tfTitre.getText().trim(), tfAuteur.getText().trim());
                        if (autoC != null && !autoC.isBlank()) {
                            cover = autoC;
                        }
                    } catch (Exception ignored) {}
                }
                Livre newLivre = new Livre(
                        cleanIsbn,
                        tfTitre.getText().trim(),
                        tfAuteur.getText().trim(),
                        tfEditeur.getText().trim(),
                        cover,
                        cbGenre.getValue() != null && !cbGenre.getValue().isBlank() ? cbGenre.getValue().trim() : null,
                        cbTheme.getValue() != null && !cbTheme.getValue().isBlank() ? cbTheme.getValue().trim() : null,
                        cbNiveau.getValue() != null && !cbNiveau.getValue().isBlank() ? cbNiveau.getValue().trim() : null,
                        taDescription.getText() != null && !taDescription.getText().isBlank() ? taDescription.getText().trim() : null
                );

                try {
                    // Vérifier si le livre existe déjà
                    Livre existing = livreDao.findByIsbn(cleanIsbn);
                    if (existing != null) {
                        int exCount = existing.getNbExemplaires();
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        MainApp.prepareDialog(alert);
                        alert.setTitle("Livre déjà présent dans le catalogue");
                        alert.setHeaderText("Le livre '" + existing.getTitre() + "' existe déjà (ISBN : " + cleanIsbn + ")");
                        alert.setContentText("Cet ouvrage possède déjà " + exCount + " exemplaire(s) physique(s) en base.\n\n"
                                + "• Souhaitez-vous ajouter " + spNbCopies.getValue() + " exemplaire(s) supplémentaire(s) (+ " + spNbCopies.getValue() + ") ?\n"
                                + "• Ou le consigner dans la Boîte à vérifier pour contrôler vos étagères physiques ?");

                        ButtonType btnOui = new ButtonType("➕ Oui, ajouter (+ " + spNbCopies.getValue() + ")", ButtonBar.ButtonData.YES);
                        ButtonType btnNon = new ButtonType("📦 Non, mettre en boîte à vérifier", ButtonBar.ButtonData.NO);
                        ButtonType btnAnnuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
                        alert.getButtonTypes().setAll(btnOui, btnNon, btnAnnuler);

                        Optional<ButtonType> rep = alert.showAndWait();
                        if (rep.isPresent() && rep.get() == btnOui) {
                            int count = spNbCopies.getValue();
                            for (int i = 0; i < count; i++) {
                                exemplaireDao.insert(new Exemplaire(cleanIsbn, Statut.DISPONIBLE, LocalDate.now()));
                            }
                            loadData();
                            for (Livre l : masterLivres) {
                                if (cleanIsbn.equals(l.getIsbn())) {
                                    livreTableView.getSelectionModel().select(l);
                                    break;
                                }
                            }
                            updateStatus("✅ " + count + " exemplaire(s) supplémentaire(s) ajouté(s) à '" + existing.getTitre() + "'.");
                            return true;
                        } else if (rep.isPresent() && rep.get() == btnNon) {
                            boiteVerificationDao.insert(new LivreAVerifier(
                                    cleanIsbn,
                                    existing.getTitre(),
                                    existing.getAuteur(),
                                    "Signalé lors de l'ajout manuel (" + exCount + " ex. existants)",
                                    exCount
                            ));
                            updateBoiteVerifBadge();
                            showInfo("Boîte à vérifier", "Le livre '" + existing.getTitre() + "' a été placé dans la Boîte à vérifier.");
                            return false;
                        } else {
                            return false;
                        }
                    }

                    // Insertion du livre
                    livreDao.insert(newLivre);

                    // Insertion des exemplaires demandés
                    int count = spNbCopies.getValue();
                    for (int i = 0; i < count; i++) {
                        exemplaireDao.insert(new Exemplaire(cleanIsbn, Statut.DISPONIBLE, LocalDate.now()));
                    }

                    loadData();

                    // Sélectionner le livre créé
                    for (Livre l : masterLivres) {
                        if (cleanIsbn.equals(l.getIsbn())) {
                            livreTableView.getSelectionModel().select(l);
                            break;
                        }
                    }

                    updateStatus("✅ Livre '" + newLivre.getTitre() + "' ajouté avec " + count + " exemplaire(s).");
                    return true;

                } catch (SQLException ex) {
                    showError("Erreur d'enregistrement", ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =====================================================================
    //  Actions — Ressources Pédagogiques du Livre
    // =====================================================================

    private void updateRessourcesButton(Livre livre) {
        if (btnRessourcesLivre == null || livre == null) return;
        try {
            int count = ressourcePedagogiqueService.compterRessourcesParIsbn(livre.getIsbn());
            btnRessourcesLivre.setText("📚 Ressources pédagogiques" + (count > 0 ? " (" + count + ")" : ""));
        } catch (Exception ignored) {
            btnRessourcesLivre.setText("📚 Ressources pédagogiques");
        }
    }

    @FXML
    private void handleRessourcesLivre() {
        Livre selected = livreTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        RessourcesPedagogiquesDialog.openForLivre(selected.getIsbn(), selected.getTitre(), selected.getAuteur(), () -> {
            updateRessourcesButton(selected);
        });
    }

    // =====================================================================
    //  Actions — Modifier le Livre
    // =====================================================================

    @FXML
    private void handleEditLivre() {
        Livre selected = livreTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Modifier un livre");
        dialog.setHeaderText("Modification de la fiche du livre : " + selected.getTitre());

        ButtonType saveButtonType = new ButtonType("Enregistrer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 16, 20));

        TextField tfIsbn = new TextField(selected.getIsbn());
        TextField tfTitre = new TextField(selected.getTitre());
        TextField tfAuteur = new TextField(selected.getAuteur() != null ? selected.getAuteur() : "");
        TextField tfEditeur = new TextField(selected.getEditeur() != null ? selected.getEditeur() : "");
        TextField tfCouverture = new TextField(selected.getCouvertureUrl() != null ? selected.getCouvertureUrl() : "");

        ComboBox<String> cbGenre = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.GENRES_STANDARDS));
        cbGenre.setEditable(true);
        cbGenre.setValue(selected.getGenre());

        ComboBox<String> cbTheme = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.THEMES_STANDARDS));
        cbTheme.setEditable(true);
        cbTheme.setValue(selected.getTheme());

        ComboBox<String> cbNiveau = new ComboBox<>(FXCollections.observableArrayList(LivreClassificationService.NIVEAUX_STANDARDS));
        cbNiveau.setEditable(true);
        cbNiveau.setValue(selected.getNiveau());

        TextArea taDescription = new TextArea(selected.getDescription() != null ? selected.getDescription() : "");
        taDescription.setPrefRowCount(3);
        taDescription.setWrapText(true);

        Button btnAutoClassify = new Button("🪄 Auto-classifier");
        btnAutoClassify.setStyle("-fx-cursor: hand;");
        btnAutoClassify.setOnAction(ev -> {
            ClassificationResult cr = classificationService.classifySimple(
                    tfTitre.getText().trim(),
                    tfAuteur.getText().trim(),
                    tfEditeur.getText().trim(),
                    taDescription.getText().trim()
            );
            cbGenre.setValue(cr.getGenre());
            cbTheme.setValue(cr.getTheme());
            cbNiveau.setValue(cr.getNiveau());
        });

        HBox classBtnBox = new HBox(8, cbGenre, btnAutoClassify);
        HBox.setHgrow(cbGenre, Priority.ALWAYS);

        grid.add(new Label("ISBN : *"), 0, 0);
        grid.add(tfIsbn, 1, 0);
        grid.add(new Label("Titre : *"), 0, 1);
        grid.add(tfTitre, 1, 1);
        grid.add(new Label("Auteur :"), 0, 2);
        grid.add(tfAuteur, 1, 2);
        grid.add(new Label("Éditeur :"), 0, 3);
        grid.add(tfEditeur, 1, 3);
        grid.add(new Label("Genre :"), 0, 4);
        grid.add(classBtnBox, 1, 4);
        grid.add(new Label("Thème :"), 0, 5);
        grid.add(cbTheme, 1, 5);
        grid.add(new Label("Niveau :"), 0, 6);
        grid.add(cbNiveau, 1, 6);
        grid.add(new Label("URL Couverture :"), 0, 7);
        grid.add(tfCouverture, 1, 7);
        grid.add(new Label("Description / Résumé :"), 0, 8);
        grid.add(taDescription, 1, 8);

        dialog.getDialogPane().setContent(grid);

        Node saveBtn = dialog.getDialogPane().lookupButton(saveButtonType);
        tfTitre.textProperty().addListener((o, oldV, newV) -> saveBtn.setDisable(newV.isBlank() || tfIsbn.getText().isBlank()));
        tfIsbn.textProperty().addListener((o, oldV, newV) -> saveBtn.setDisable(newV.isBlank() || tfTitre.getText().isBlank()));

        dialog.setResultConverter(btnType -> {
            if (btnType == saveButtonType) {
                String oldIsbn = selected.getIsbn();
                String rawNewIsbn = tfIsbn.getText().trim();
                String cleanNewIsbn = LivreMetadataService.cleanIsbn(rawNewIsbn);

                if (cleanNewIsbn.isEmpty()) {
                    showError("ISBN invalide", "Veuillez renseigner un ISBN valide.");
                    return false;
                }

                selected.setTitre(tfTitre.getText().trim());
                selected.setAuteur(tfAuteur.getText().trim());
                selected.setEditeur(tfEditeur.getText().trim());
                selected.setCouvertureUrl(tfCouverture.getText().trim());
                selected.setGenre(cbGenre.getValue() != null && !cbGenre.getValue().isBlank() ? cbGenre.getValue().trim() : null);
                selected.setTheme(cbTheme.getValue() != null && !cbTheme.getValue().isBlank() ? cbTheme.getValue().trim() : null);
                selected.setNiveau(cbNiveau.getValue() != null && !cbNiveau.getValue().isBlank() ? cbNiveau.getValue().trim() : null);
                selected.setDescription(taDescription.getText() != null && !taDescription.getText().isBlank() ? taDescription.getText().trim() : null);

                try {
                    if (!cleanNewIsbn.equals(oldIsbn)) {
                        selected.setIsbn(cleanNewIsbn);
                        livreDao.updateBookIsbn(oldIsbn, selected);
                        loadData();
                        showLivreDetail(selected);
                        updateStatus("✅ ISBN modifié (" + oldIsbn + " ➔ " + cleanNewIsbn + ") et fiche mise à jour.");
                    } else {
                        livreDao.update(selected);
                        loadData();
                        showLivreDetail(selected);
                        updateStatus("✅ Fiche du livre '" + selected.getTitre() + "' mise à jour.");
                    }
                    return true;
                } catch (SQLException e) {
                    showError("Erreur de modification", e.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait();
    }

    // =====================================================================
    //  Actions — Supprimer le Livre
    // =====================================================================

    @FXML
    private void handleDeleteLivre() {
        List<Livre> selectedList = (livreTableView != null && livreTableView.getSelectionModel().getSelectedItems() != null)
                ? livreTableView.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).distinct().toList()
                : List.of();

        if (selectedList.isEmpty()) {
            Livre single = livreTableView != null ? livreTableView.getSelectionModel().getSelectedItem() : null;
            if (single != null) selectedList = List.of(single);
        }

        if (selectedList.isEmpty()) return;

        try {
            InventaireSessionService invService = new InventaireSessionService();

            // CAS 1 : Suppression multiple (plusieurs livres sélectionnés)
            if (selectedList.size() > 1) {
                List<Livre> deletable = new ArrayList<>();
                List<String> blockedBorrowed = new ArrayList<>();
                List<String> blockedFrozen = new ArrayList<>();
                int totalCopiesCount = 0;

                for (Livre l : selectedList) {
                    if (invService.isLivreGele(l.getIsbn())) {
                        blockedFrozen.add(l.getTitre());
                        continue;
                    }
                    List<Exemplaire> copies = exemplaireDao.findByIsbn(l.getIsbn());
                    boolean hasBorrowed = copies.stream().anyMatch(c -> c.getStatut() == Statut.EMPRUNTE);
                    if (hasBorrowed) {
                        blockedBorrowed.add(l.getTitre());
                    } else {
                        deletable.add(l);
                        totalCopiesCount += copies.size();
                    }
                }

                if (!blockedBorrowed.isEmpty() || !blockedFrozen.isEmpty()) {
                    StringBuilder warn = new StringBuilder();
                    if (!blockedBorrowed.isEmpty()) {
                        warn.append("• ").append(blockedBorrowed.size())
                            .append(" livre(s) ont des exemplaires actuellement prêtés (retour requis).\n");
                    }
                    if (!blockedFrozen.isEmpty()) {
                        warn.append("• ").append(blockedFrozen.size())
                            .append(" livre(s) font partie d'une session d'inventaire active (stock gelé).\n");
                    }
                    if (deletable.isEmpty()) {
                        showWarning("Suppression impossible", "Aucun des livres sélectionnés ne peut être supprimé :\n\n" + warn);
                        return;
                    } else {
                        warn.append("\nSeuls les ").append(deletable.size()).append(" autres livres pourront être supprimés.");
                        showWarning("Livres protégés ignorés", warn.toString());
                    }
                }

                // Confirmation de suppression en masse
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(confirm);
                confirm.setTitle("Confirmer la suppression par lot");
                confirm.setHeaderText("Supprimer " + deletable.size() + " livre(s) sélectionné(s) ?");
                confirm.setContentText("Cette action supprimera définitivement ces " + deletable.size()
                        + " ouvrages et leurs " + totalCopiesCount + " exemplaire(s) physique(s) de la BCD.\n\n"
                        + "⚠️ Cette opération est irréversible.");

                // Si plus de 5 livres, confirmation renforcée par mot-clé pour éviter toute fausse manip
                if (deletable.size() >= 5) {
                    TextInputDialog securityDialog = new TextInputDialog();
                    MainApp.prepareDialog(securityDialog);
                    securityDialog.setTitle("Sécurité désherbage / Suppression massive");
                    securityDialog.setHeaderText("⚠️ Suppression massive de " + deletable.size() + " livres (" + totalCopiesCount + " exemplaires)");
                    securityDialog.setContentText("Pour confirmer la suppression définitive de ces " + deletable.size()
                            + " livres, tapez le mot 'SUPPRIMER' ci-dessous :");

                    Optional<String> res = securityDialog.showAndWait();
                    if (res.isEmpty() || !"SUPPRIMER".equalsIgnoreCase(res.get().trim())) {
                        showInfo("Suppression annulée", "Le mot de confirmation n'a pas été saisi. Aucun livre n'a été supprimé.");
                        return;
                    }
                } else {
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        return;
                    }
                }

                List<String> isbns = deletable.stream().map(Livre::getIsbn).toList();
                int deletedCount = livreDao.deleteBatch(isbns);
                loadData();
                showLivreDetail(null);
                updateStatus("🗑️ " + deletedCount + " livre(s) supprimé(s) du catalogue (" + totalCopiesCount + " exemplaires retirés).");
                return;
            }

            // CAS 2 : Suppression d'un livre unique
            Livre selected = selectedList.get(0);
            if (invService.isLivreGele(selected.getIsbn())) {
                showWarning("Suppression impossible",
                        "Ce livre fait partie d'une session d'inventaire active (stock gelé sur ce rayon).\n"
                                + "Veuillez clôturer l'inventaire avant de pouvoir modifier le catalogue.");
                return;
            }

            List<Exemplaire> copies = exemplaireDao.findByIsbn(selected.getIsbn());
            boolean hasBorrowed = copies.stream().anyMatch(c -> c.getStatut() == Statut.EMPRUNTE);
            if (hasBorrowed) {
                showWarning("Suppression impossible",
                        "Ce livre ne peut pas être supprimé car un ou plusieurs de ses exemplaires sont actuellement prêtés.\n"
                                + "Veuillez d'abord enregistrer le retour des emprunts concernés.");
                return;
            }

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            MainApp.prepareDialog(confirm);
            confirm.setTitle("Confirmer la suppression");
            confirm.setHeaderText("Supprimer '" + selected.getTitre() + "' ?");
            confirm.setContentText("Cette action supprimera définitivement le livre et ses "
                    + copies.size() + " exemplaire(s) physique(s).\n\n"
                    + "Cette opération est irréversible.");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                livreDao.delete(selected.getIsbn());
                loadData();
                showLivreDetail(null);
                updateStatus("🗑️ Livre '" + selected.getTitre() + "' supprimé.");
            }

        } catch (SQLException e) {
            showError("Erreur de suppression", e.getMessage());
        }
    }

    // =====================================================================
    //  Actions — Exemplaires physiques
    // =====================================================================

    @FXML
    private void handleAddExemplaire() {
        Livre selected = livreTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        try {
            Exemplaire ex = new Exemplaire(selected.getIsbn(), Statut.DISPONIBLE, LocalDate.now());
            int newId = exemplaireDao.insert(ex);

            // Recharger les exemplaires de ce livre
            loadExemplairesForLivre(selected.getIsbn());

            // Mettre à jour les compteurs du livre dans le master table
            selected.setNbExemplaires(selected.getNbExemplaires() + 1);
            selected.setNbDisponibles(selected.getNbDisponibles() + 1);
            lblExemplairesRatio.setText("📦 " + selected.getExemplairesRatio());
            livreTableView.refresh();
            updateStatusCounters();

            updateStatus("✅ Exemplaire #" + newId + " ajouté pour '" + selected.getTitre() + "'.");

        } catch (SQLException e) {
            showError("Erreur d'ajout", "Impossible d'ajouter un exemplaire : " + e.getMessage());
        }
    }

    @FXML
    private void handleDeleteExemplaire() {
        Exemplaire selectedEx = exemplaireTableView.getSelectionModel().getSelectedItem();
        Livre selectedLivre = livreTableView.getSelectionModel().getSelectedItem();
        if (selectedEx == null || selectedLivre == null) return;

        if (selectedEx.getStatut() == Statut.EMPRUNTE) {
            showWarning("Suppression refusée",
                    "L'exemplaire #" + selectedEx.getId() + " est actuellement emprunté.\n"
                            + "Veuillez d'abord enregistrer son retour avant de pouvoir le supprimer.");
            return;
        }

        InventaireSessionService invService = new InventaireSessionService();
        if (invService.isExemplaireGele(selectedEx.getId())) {
            showWarning("Suppression refusée",
                    "L'exemplaire #" + selectedEx.getId() + " fait partie d'une session d'inventaire active (stock gelé).");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Supprimer l'exemplaire");
        confirm.setHeaderText("Supprimer l'exemplaire physique #" + selectedEx.getId() + " ?");
        confirm.setContentText("Cette action retirera cet exemplaire de l'inventaire.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                boolean ok = exemplaireDao.delete(selectedEx.getId());
                if (ok) {
                    loadExemplairesForLivre(selectedLivre.getIsbn());
                    selectedLivre.setNbExemplaires(Math.max(0, selectedLivre.getNbExemplaires() - 1));
                    selectedLivre.setNbDisponibles(Math.max(0, selectedLivre.getNbDisponibles() - 1));
                    lblExemplairesRatio.setText("📦 " + selectedLivre.getExemplairesRatio());
                    livreTableView.refresh();
                    updateStatusCounters();
                    updateStatus("🗑️ Exemplaire #" + selectedEx.getId() + " supprimé.");
                }
            } catch (SQLException e) {
                showError("Erreur de suppression",
                        "Impossible de supprimer cet exemplaire (il est peut-être lié à un ancien emprunt dans l'historique) : "
                                + e.getMessage());
            }
        }
    }

    @FXML
    private void handleReassignExemplaire() {
        Exemplaire selectedEx = exemplaireTableView.getSelectionModel().getSelectedItem();
        Livre selectedLivre = livreTableView.getSelectionModel().getSelectedItem();
        if (selectedEx == null || selectedLivre == null) return;

        Dialog<Boolean> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Séparer cet exemplaire en nouvelle fiche");
        dialog.setHeaderText("Transférer l'exemplaire physique #" + selectedEx.getId() + " vers une fiche distincte.\n"
                + "Idéal pour une revue dont le numéro est différent, ou un livre mal affecté.");

        ButtonType btnValider = new ButtonType("Transférer vers cette fiche", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(btnValider, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16, 20, 16, 20));

        // Proposer un code adapté selon le type d'ouvrage
        String defaultCode = selectedLivre.getIsbn();
        String currentTitle = selectedLivre.getTitre() != null ? selectedLivre.getTitre() : "";
        if (currentTitle.toLowerCase().contains("j'aime lire") || defaultCode.contains("01973")) {
            defaultCode = "JAL-" + String.format("%03d", (System.currentTimeMillis() % 900) + 100);
        } else if (defaultCode.startsWith("378") || defaultCode.startsWith("379") || defaultCode.startsWith("977")) {
            defaultCode = "REV-" + String.format("%03d", (System.currentTimeMillis() % 900) + 100);
        } else if (!defaultCode.matches("^[0-9Xx]{10,13}$")) {
            defaultCode = defaultCode + "-BIS";
        } else {
            defaultCode = "CAMUS-" + String.format("%04d", (System.currentTimeMillis() % 9000) + 1000);
        }

        TextField tfCode = new TextField(defaultCode);
        TextField tfTitre = new TextField(currentTitle);
        TextField tfAuteur = new TextField(selectedLivre.getAuteur() != null ? selectedLivre.getAuteur() : "");
        TextField tfEditeur = new TextField(selectedLivre.getEditeur() != null ? selectedLivre.getEditeur() : "");

        grid.add(new Label("Nouvel Identifiant / ISBN :"), 0, 0);
        grid.add(tfCode, 1, 0);
        grid.add(new Label("Nouveau Titre : *"), 0, 1);
        grid.add(tfTitre, 1, 1);
        grid.add(new Label("Auteur :"), 0, 2);
        grid.add(tfAuteur, 1, 2);
        grid.add(new Label("Éditeur :"), 0, 3);
        grid.add(tfEditeur, 1, 3);

        Label lblHelp = new Label("💡 Cet exemplaire recevra sa propre fiche indépendante dans l'inventaire.");
        lblHelp.setStyle("-fx-font-size: 11px; -fx-text-fill: #0366d6;");
        grid.add(lblHelp, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == btnValider) {
                String cleanCode = LivreMetadataService.cleanIsbn(tfCode.getText().trim());
                String tit = tfTitre.getText().trim();
                if (cleanCode.isEmpty() || tit.isEmpty()) {
                    showWarning("Champs incomplets", "L'identifiant et le titre sont obligatoires.");
                    return false;
                }

                Livre targetLivre = new Livre(cleanCode, tit, tfAuteur.getText().trim(), tfEditeur.getText().trim(),
                        selectedLivre.getCouvertureUrl(), selectedLivre.getGenre(), selectedLivre.getTheme(),
                        selectedLivre.getNiveau(), selectedLivre.getDescription());

                try {
                    livreDao.reassignExemplaireIsbn(selectedEx.getId(), selectedLivre.getIsbn(), targetLivre);
                    loadData();
                    // Sélectionner la nouvelle fiche
                    for (Livre l : masterLivres) {
                        if (cleanCode.equals(l.getIsbn())) {
                            livreTableView.getSelectionModel().select(l);
                            break;
                        }
                    }
                    updateStatus("🔀 Exemplaire #" + selectedEx.getId() + " transféré vers '" + tit + "'.");
                    return true;
                } catch (SQLException ex) {
                    showError("Erreur de transfert", ex.getMessage());
                    return false;
                }
            }
            return false;
        });

        dialog.showAndWait();
    }

    @FXML
    private void handleEditExemplaireRemarque() {
        Exemplaire selectedEx = exemplaireTableView.getSelectionModel().getSelectedItem();
        Livre selectedLivre = livreTableView.getSelectionModel().getSelectedItem();
        if (selectedEx == null || selectedLivre == null) return;

        ChoiceDialog<String> dialog = new ChoiceDialog<>(
                selectedEx.getRemarque().isBlank() ? "🟢 Bon état" : selectedEx.getRemarque(),
                List.of("🟢 Bon état", "🩹 À réparer", "⚠️ Mauvais état", "❓ Titre à vérifier", "📝 Autre remarque…")
        );
        dialog.setTitle("État / Remarque de l'exemplaire");
        dialog.setHeaderText("Exemplaire #" + selectedEx.getId() + " — " + selectedLivre.getTitre());
        dialog.setContentText("Sélectionnez l'état de l'exemplaire :");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String chosen = result.get();
            if ("📝 Autre remarque…".equals(chosen)) {
                TextInputDialog textDialog = new TextInputDialog(selectedEx.getRemarque());
                textDialog.setTitle("Remarque personnalisée");
                textDialog.setHeaderText("Exemplaire #" + selectedEx.getId());
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
                exemplaireDao.updateRemarque(selectedEx.getId(), chosen);
                selectedEx.setRemarque(chosen);
                exemplaireTableView.refresh();
                updateStatus("✅ État mis à jour pour l'exemplaire #" + selectedEx.getId() + (chosen.isBlank() ? " (Bon état)" : " (" + chosen + ")"));
            } catch (SQLException e) {
                showError("Erreur d'enregistrement", "Impossible de mettre à jour l'exemplaire : " + e.getMessage());
            }
        }
    }

    // =====================================================================
    //  Actions — Recherche de couverture & Import CSV
    // =====================================================================

    @FXML
    private void handleSearchCover() {
        Livre selected = livreTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        btnSearchCover.setDisable(true);
        updateStatus("🔍 Recherche d'une couverture vérifiée pour '" + selected.getTitre() + "' (OpenLibrary / Amazon / Google Books)...");

        Task<String> task = new Task<>() {
            @Override
            protected String call() {
                return metadataService.refreshCover(selected.getIsbn(), selected.getCouvertureUrl(), selected.getTitre(), selected.getAuteur());
            }
        };

        task.setOnSucceeded(e -> {
            btnSearchCover.setDisable(false);
            String verifiedCover = task.getValue();
            if (verifiedCover != null && !verifiedCover.isBlank()) {
                boolean isNew = !verifiedCover.equals(selected.getCouvertureUrl());
                if (isNew) {
                    selected.setCouvertureUrl(verifiedCover);
                    try {
                        livreDao.update(selected);
                    } catch (SQLException ex) {
                        showError("Erreur d'enregistrement", "Impossible de mettre à jour la couverture : " + ex.getMessage());
                    }
                    COVER_IMAGE_CACHE.remove(verifiedCover);
                }
                showLivreDetail(selected);
                updateStatus("✅ Couverture vérifiée active pour '" + selected.getTitre() + "'.");
                if (isNew) {
                    showInfo("Couverture trouvée", "Une nouvelle couverture officielle a été trouvée et enregistrée pour :\n\n« "
                            + selected.getTitre() + " »");
                } else {
                    showInfo("Couverture validée", "La couverture officielle haute résolution est bien validée et active pour :\n\n« "
                            + selected.getTitre() + " »");
                }
            } else {
                updateStatus("ℹ️ Aucune couverture trouvée pour l'ISBN " + selected.getIsbn() + ".");
                showInfo("Couverture non trouvée",
                        "Aucune couverture de meilleure qualité n'a été trouvée sur Amazon, OpenLibrary ou Google Books pour l'ISBN "
                                + selected.getIsbn() + ".\n\nVous pouvez toujours renseigner une URL manuellement via le bouton '✏️ Modifier'.");
            }
        });

        task.setOnFailed(e -> {
            btnSearchCover.setDisable(false);
            updateStatus("⚠️ Échec de la recherche de couverture.");
            showError("Erreur réseau", "Impossible de joindre les serveurs de couverture : "
                    + (task.getException() != null ? task.getException().getMessage() : "Inconnu"));
        });

        new Thread(task).start();
    }

    /**
     * Rafraîchit les couvertures de TOUS les livres du catalogue.
     * Détecte les placeholders (Epagine 2687 octets) et cherche de vraies couvertures
     * via OpenLibrary, Amazon, Google Books et recherche par titre.
     */
    @FXML
    private void handleRefreshAllCovers() {
        if (masterLivres.isEmpty()) {
            showInfo("Catalogue vide", "Aucun livre dans le catalogue.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Rafraîchir toutes les couvertures");
        confirm.setHeaderText("🖼️ Rechercher de vraies couvertures pour les " + masterLivres.size() + " livres");
        confirm.setContentText(
                "Cette opération va vérifier chaque couverture du catalogue et remplacer les images manquantes "
                        + "ou les placeholders génériques par de vraies couvertures depuis :\n\n"
                        + "  • OpenLibrary (taille Large)\n"
                        + "  • Amazon CDN (haute résolution)\n"
                        + "  • Recherche par Titre/Auteur pour les classiques jeunesse\n"
                        + "  • Google Books (haute résolution)\n\n"
                        + "⏱️ Cela peut prendre quelques instants selon la connexion internet.\n"
                        + "L'application reste utilisable pendant le traitement.");

        ButtonType btnStart = new ButtonType("🚀 Lancer le rafraîchissement");
        confirm.getButtonTypes().setAll(btnStart, ButtonType.CANCEL);

        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) return;

        // Dialogue de progression
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Rafraîchissement des couvertures en cours");
        progressDialog.setHeaderText("🖼️ Recherche de couvertures sur Google Books, OpenLibrary et Epagine…");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(500);
        Label lblProgress = new Label("Démarrage…");
        lblProgress.setStyle("-fx-font-size: 11px; -fx-text-fill: #586069;");
        lblProgress.setWrapText(true);

        VBox content = new VBox(12, progressBar, lblProgress);
        content.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(content);
        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        progressDialog.show();

        // Copier la liste pour le thread
        List<Livre> allLivres = new java.util.ArrayList<>(masterLivres);

        Task<int[]> task = new Task<>() {
            @Override
            protected int[] call() throws Exception {
                int updated = 0;
                int checked = 0;
                int total = allLivres.size();

                for (int i = 0; i < total; i++) {
                    Livre livre = allLivres.get(i);
                    checked++;
                    final int idx = i + 1;
                    final String titre = livre.getTitre();

                    Platform.runLater(() -> {
                        progressBar.setProgress((double) idx / total);
                        lblProgress.setText(String.format("(%d/%d) %s…", idx, total, titre));
                    });

                    try {
                        String currentCover = livre.getCouvertureUrl();
                        String betterCover = metadataService.refreshCover(livre.getIsbn(), currentCover, livre.getTitre(), livre.getAuteur());

                        if (betterCover != null && !betterCover.isBlank()
                                && !betterCover.equals(currentCover)) {
                            livre.setCouvertureUrl(betterCover);
                            livreDao.update(livre);
                            updated++;
                            System.out.println("🖼️ Couverture mise à jour : " + titre + " → " + betterCover);
                        }
                    } catch (Exception e) {
                        System.err.println("⚠️ Erreur couverture pour " + titre + " : " + e.getMessage());
                    }

                    // Petite pause pour ne pas surcharger les APIs (politesse)
                    if (i < total - 1) {
                        Thread.sleep(300);
                    }
                }

                return new int[]{checked, updated};
            }
        };

        task.setOnSucceeded(e -> {
            progressDialog.setResult(null);
            progressDialog.close();
            int[] results = task.getValue();
            loadData();
            // Re-sélectionner le livre en cours si besoin
            Livre selected = livreTableView.getSelectionModel().getSelectedItem();
            if (selected != null) showLivreDetail(selected);

            showInfo("Couvertures rafraîchies",
                    String.format("🖼️ Traitement terminé !\n\n"
                                    + "• %d livre(s) vérifié(s)\n"
                                    + "• %d couverture(s) améliorée(s)\n"
                                    + "• %d inchangée(s)\n\n"
                                    + "Les couvertures proviennent d'OpenLibrary, d'Amazon CDN et de Google Books.",
                            results[0], results[1], results[0] - results[1]));
            updateStatus(String.format("🖼️ Couvertures : %d améliorée(s) sur %d livre(s) vérifié(s).",
                    results[1], results[0]));
        });

        task.setOnFailed(e -> {
            progressDialog.setResult(null);
            progressDialog.close();
            loadData();
            showError("Erreur", "Le rafraîchissement a rencontré une erreur : "
                    + (task.getException() != null ? task.getException().getMessage() : "Inconnue"));
        });

        // Permettre l'annulation
        progressDialog.setOnCloseRequest(evt -> {
            task.cancel();
        });

        new Thread(task).start();
    }


    @FXML
    private void handleImportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer une liste de livres par lot (CSV)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Fichiers CSV (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("Tous les fichiers (*.*)", "*.*")
        );

        File file = chooser.showOpenDialog(livreTableView.getScene().getWindow());
        if (file == null) return;

        // Dialogue modal de progression
        Dialog<Void> progressDialog = new Dialog<>();
        progressDialog.setTitle("Importation des livres en cours");
        progressDialog.setHeaderText("Recherche multi-sources (BNF, Google Books, OpenLibrary) & couvertures");

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(440);
        Label lblCurrent = new Label("Initialisation du fichier...");
        lblCurrent.setStyle("-fx-font-size: 11px; -fx-text-fill: #586069;");
        lblCurrent.setWrapText(true);

        VBox content = new VBox(12, progressBar, lblCurrent);
        content.setPadding(new Insets(20));
        progressDialog.getDialogPane().setContent(content);

        progressDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Node closeButton = progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE);
        closeButton.setVisible(false);

        Task<BatchReport> task = new Task<>() {
            @Override
            protected BatchReport call() throws Exception {
                return batchImportService.importFromCsv(file, info -> {
                    Platform.runLater(() -> {
                        double p = info.total() > 0 ? (double) info.current() / info.total() : -1;
                        progressBar.setProgress(p);
                        String titleDisplay = (info.titre() != null && !info.titre().isBlank())
                                ? info.titre() : "Recherche en cascade...";
                        lblCurrent.setText(String.format("[%d/%d] ISBN %s : %s (%s)",
                                info.current(), info.total(), info.isbn(), titleDisplay, info.source()));
                    });
                });
            }
        };

        task.setOnSucceeded(e -> {
            progressDialog.close();
            loadData();
            BatchReport report = task.getValue();
            updateStatus(String.format("✅ Import terminé : %d nouveau(x), %d réapprovisionné(s), %d exemplaire(s).",
                    report.getNewBooks(), report.getExistingBooks(), report.getCopiesCreated()));

            Alert reportAlert = new Alert(Alert.AlertType.INFORMATION);
            reportAlert.setTitle("Importation terminée");
            reportAlert.setHeaderText("Rapport d'importation des livres");

            TextArea area = new TextArea(report.getSummary());
            area.setEditable(false);
            area.setWrapText(true);
            area.setPrefRowCount(14);
            area.setPrefColumnCount(52);
            area.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");

            reportAlert.getDialogPane().setContent(area);
            reportAlert.showAndWait();
        });

        task.setOnFailed(e -> {
            progressDialog.close();
            Throwable ex = task.getException();
            showError("Échec de l'import CSV",
                    "Une erreur est survenue lors du traitement du fichier :\n"
                            + (ex != null ? ex.getMessage() : "Erreur inconnue"));
        });

        new Thread(task).start();
        progressDialog.showAndWait();
    }

    // =====================================================================
    //  Actions — Export Catalogue & Mode Douchette (Scan Continu)
    // =====================================================================

    @FXML
    private void handleExportCatalogue() {
        if (masterLivres.isEmpty()) {
            showInfo("Catalogue vide", "Aucun livre à exporter.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter le catalogue des livres (CSV)");
        chooser.setInitialFileName("catalogue_bcd_" + LocalDate.now() + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier CSV (*.csv)", "*.csv"));

        File file = chooser.showSaveDialog(livreTableView.getScene().getWindow());
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("\uFEFF"); // BOM UTF-8 pour Excel
            writer.write("ISBN;Titre;Auteur;Éditeur;Genre;Thème;Niveau;Exemplaires_Total;Exemplaires_Disponibles;Exemplaires_Empruntés;Couverture_URL;Description\n");

            for (Livre l : masterLivres) {
                int empruntes = Math.max(0, l.getNbExemplaires() - l.getNbDisponibles());
                writer.write(String.format("%s;%s;%s;%s;%s;%s;%s;%d;%d;%d;%s;%s\n",
                        cleanCsv(l.getIsbn()),
                        cleanCsv(l.getTitre()),
                        cleanCsv(l.getAuteur()),
                        cleanCsv(l.getEditeur()),
                        cleanCsv(l.getGenre()),
                        cleanCsv(l.getTheme()),
                        cleanCsv(l.getNiveau()),
                        l.getNbExemplaires(),
                        l.getNbDisponibles(),
                        empruntes,
                        cleanCsv(l.getCouvertureUrl()),
                        cleanCsv(l.getDescription())
                ));
            }

            showInfo("Export réussi", String.format("Le catalogue a été exporté avec succès !\n\n"
                    + "📁 Emplacement : %s\n"
                    + "📚 %d titre(s) répertorié(s).",
                    file.getAbsolutePath(), masterLivres.size()));

        } catch (IOException ex) {
            showError("Erreur d'exportation", "Impossible d'écrire le fichier : " + ex.getMessage());
        }
    }

    @FXML
    private void handleOpenRecolement() {
        if (livreTableView != null && livreTableView.getScene() != null) {
            TabPane tabPane = (TabPane) livreTableView.getScene().lookup(".tab-pane");
            if (tabPane != null) {
                for (Tab tab : tabPane.getTabs()) {
                    if (tab.getText().contains("Récolement")) {
                        tabPane.getSelectionModel().select(tab);
                        break;
                    }
                }
            }
        }
    }

    // =====================================================================
    //  Actions — Boîte de Vérification & Mode Douchette
    // =====================================================================

    @FXML
    private void handleOpenBoiteVerification() {
        BoiteVerificationDialogHelper.openBoiteVerificationDialog(
                boiteVerificationDao, livreDao, exemplaireDao,
                this::loadData, this::updateBoiteVerifBadge
        );
    }

    @FXML
    private void handleModeDouchette() {
        DouchetteDialogHelper.openDouchetteDialog(
                livreDao, exemplaireDao, boiteVerificationDao, metadataService,
                this::loadData, this::updateBoiteVerifBadge
        );
    }

    public static class DouchetteSessionItem extends DouchetteDialogHelper.DouchetteSessionItem {
        public DouchetteSessionItem(String isbn, String titre, String auteur, String editeur,
                                    int exemplaireId, String source, String couvertureUrl, String heure, String remarque) {
            super(isbn, titre, auteur, editeur, exemplaireId, source, couvertureUrl, heure, remarque);
        }
        public DouchetteSessionItem(String isbn, String titre, String auteur, String editeur,
                                    int exemplaireId, String source, String couvertureUrl, String heure, String remarque,
                                    String genre, String theme, String niveau) {
            super(isbn, titre, auteur, editeur, exemplaireId, source, couvertureUrl, heure, remarque, genre, theme, niveau);
        }
    }

    private String cleanCsv(String val) {
        if (val == null) return "";
        return val.replace(";", ",").replace("\n", " ").trim();
    }

    // =====================================================================
    //  Utilitaires UI
    // =====================================================================

    private void updateStatus(String message) {
        statusLabel.setText(message);
    }

    @FXML
    public void closeModal() {
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
            modalOverlay.setManaged(false);
            if (modalCard != null) {
                modalCard.getChildren().clear();
            }
        }
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
    //  FEATURE 4 : Générateur d'Étiquettes & Codes-barres (In-App Modal)
    // =====================================================================

    @FXML
    private void handleEtiquettes() {
        List<Livre> selected = (livreTableView != null && livreTableView.getSelectionModel().getSelectedItems() != null)
                ? livreTableView.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList()
                : List.of();

        MainApp.selectTab("Étiquettes");
        Object ctrl = MainApp.getTabController("Étiquettes");
        if (ctrl instanceof fr.ecole.bcd.controller.EtiquettesController ec) {
            if (!selected.isEmpty()) {
                ec.selectLivres(selected);
            }
        }
    }

    // =====================================================================
    //  FEATURE : Actions groupées (Suppression et étiquetage par lot)
    // =====================================================================

    private void updateBatchActionsBar() {
        if (boxBatchActions == null || livreTableView == null) return;
        var selected = livreTableView.getSelectionModel().getSelectedItems();
        int count = (selected != null) ? (int) selected.stream().filter(Objects::nonNull).count() : 0;
        if (count > 1) {
            boxBatchActions.setVisible(true);
            boxBatchActions.setManaged(true);
            if (lblBatchCount != null) {
                lblBatchCount.setText("☑️ " + count + " livres sélectionnés");
            }
            if (btnBatchLabels != null) {
                btnBatchLabels.setText("🏷️ Étiquettes (" + count + ")");
            }
            if (btnBatchDelete != null) {
                btnBatchDelete.setText("🗑️ Supprimer les " + count + " livres");
            }
        } else {
            boxBatchActions.setVisible(false);
            boxBatchActions.setManaged(false);
        }
    }

    @FXML
    private void handleBatchLabels() {
        handleEtiquettes();
    }

    @FXML
    private void handleClearSelection() {
        if (livreTableView != null) {
            livreTableView.getSelectionModel().clearSelection();
        }
    }
}
