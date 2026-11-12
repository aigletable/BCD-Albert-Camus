package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.EmpruntDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.MotifEcart;
import fr.ecole.bcd.model.RecolementItem;
import fr.ecole.bcd.model.RecolementItem.StatutInventaire;
import fr.ecole.bcd.model.SessionInventaire;
import fr.ecole.bcd.model.SessionInventaire.PerimetreType;
import fr.ecole.bcd.service.EmpruntService;
import fr.ecole.bcd.service.InventaireSessionService;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.ThemeService;
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
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Contrôleur de l'onglet Récolement &amp; Inventaire physique.
 * <p>
 * Gestion de stock professionnelle inspirée du retail (Max Mara) :
 * <ul>
 *   <li>Lancement de sessions d'inventaire ciblées (1 Livre, 1 Genre, 1 Thème, 1 Niveau, ou Tout)</li>
 *   <li><b>Gel automatique des mouvements (prêts/sorties)</b> pendant toute la durée de la session</li>
 *   <li>Pointage physique douchette et qualification des écarts (+/-)</li>
 *   <li>Clôture et génération du Procès-Verbal officiel d'inventaire</li>
 * </ul>
 * </p>
 */
public class RecolementController {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    // ── Composants FXML — En-tête & Session ──────────────────────────────
    @FXML private Button btnStartCampaign;
    @FXML private Button btnCloturerSession;
    @FXML private Button btnAnnulerSession;
    @FXML private Button btnExportCsv;

    // ── Composants FXML — Bandeau d'état & Gel ──────────────────────────
    @FXML private HBox sessionBanner;
    @FXML private Label lblSessionBadge;
    @FXML private Label lblSessionDetails;
    @FXML private Label lblFreezeWarning;

    // ── Composants FXML — KPIs & Progression ────────────────────────────
    @FXML private Label lblTotalCount;
    @FXML private Label lblPresentCount;
    @FXML private Label lblPretCount;
    @FXML private Label lblManquantCount;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblProgressPercent;

    // ── Composants FXML — Scan Douchette ────────────────────────────────
    @FXML private TextField scanField;
    @FXML private Button btnScanValidate;
    @FXML private Label lblFeedback;

    // ── Composants FXML — Filtres ───────────────────────────────────────
    @FXML private ToggleButton tbManquants;
    @FXML private ToggleButton tbPresents;
    @FXML private ToggleButton tbPrets;
    @FXML private ToggleButton tbSurplus;
    @FXML private ToggleButton tbTous;
    @FXML private TextField tableSearchField;
    @FXML private Button btnCopierIsbn;
    @FXML private Button btnPointerDirect;
    @FXML private Button btnQualifierEcart;

    // ── Composants FXML — Tableau ───────────────────────────────────────
    @FXML private TableView<RecolementItem> recolementTable;
    @FXML private TableColumn<RecolementItem, String> colId;
    @FXML private TableColumn<RecolementItem, String> colTitre;
    @FXML private TableColumn<RecolementItem, String> colIsbn;
    @FXML private TableColumn<RecolementItem, RecolementItem> colStatut;
    @FXML private TableColumn<RecolementItem, RecolementItem> colMotifEcart;
    @FXML private TableColumn<RecolementItem, String> colInfo;
    @FXML private TableColumn<RecolementItem, String> colRemarque;

    // ── Modale In-App ───────────────────────────────────────────────────
    @FXML private StackPane modalOverlay;
    @FXML private VBox modalCard;

    // ── DAOs & Services ─────────────────────────────────────────────────
    private final ExemplaireDao exemplaireDao = new ExemplaireDao();
    private final EmpruntDao empruntDao = new EmpruntDao();
    private final EmpruntService empruntService = new EmpruntService();
    private final LivreDao livreDao = new LivreDao();
    private final InventaireSessionService sessionService = new InventaireSessionService();

    private final ObservableList<RecolementItem> masterItems = FXCollections.observableArrayList();
    private FilteredList<RecolementItem> filteredItems;
    private final ToggleGroup filterToggleGroup = new ToggleGroup();

    private SessionInventaire currentSession = null;

    // =====================================================================
    //  Initialisation
    // =====================================================================

    @FXML
    public void initialize() {
        configureToggleGroup();
        configureTable();

        filteredItems = new FilteredList<>(masterItems, p -> true);
        recolementTable.setItems(filteredItems);

        // Recherche textuelle dans le tableau
        tableSearchField.textProperty().addListener((obs, oldV, newV) -> applyFilter());

        // Touche Entrée dans le scan douchette
        scanField.setOnAction(e -> handleScanSubmit());
        scanField.setPromptText("🎯 Scannez le code-barres / N° d'exemplaire ou tapez le nom du livre...");

        // Sélection de ligne pour justifier l'écart, copier l'ISBN et pointer direct
        recolementTable.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            btnQualifierEcart.setDisable(n == null);
            if (btnCopierIsbn != null) btnCopierIsbn.setDisable(n == null);
            if (btnPointerDirect != null) {
                btnPointerDirect.setDisable(n == null || n.getStatutInventaire() != StatutInventaire.MANQUANT);
            }
        });
        btnQualifierEcart.setDisable(true);
        if (btnCopierIsbn != null) btnCopierIsbn.setDisable(true);
        if (btnPointerDirect != null) btnPointerDirect.setDisable(true);

        // Clic sur l'arrière-plan de la modale pour fermer
        if (modalOverlay != null) {
            modalOverlay.setOnMouseClicked(e -> {
                if (e.getTarget() == modalOverlay) closeModal();
            });
        }

        // Vérifier s'il y a déjà une session active en base
        verifierSessionActive();

        // Par défaut, afficher les manquants
        tbManquants.setSelected(true);
        applyFilter();
    }

    private void verifierSessionActive() {
        currentSession = sessionService.getSessionActive();
        actualiserBandeauSession();
        loadData();
    }

    private void actualiserBandeauSession() {
        if (currentSession != null && currentSession.isActive()) {
            // Session active
            btnStartCampaign.setVisible(false);
            btnStartCampaign.setManaged(false);

            btnCloturerSession.setVisible(true);
            btnCloturerSession.setManaged(true);

            btnAnnulerSession.setVisible(true);
            btnAnnulerSession.setManaged(true);

            if (ThemeService.isDarkMode()) {
                sessionBanner.setStyle("-fx-background-color: #351114; -fx-border-color: #DA3633; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 10px 16px;");
                lblSessionDetails.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #F85149;");
                lblFreezeWarning.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #FFA657; -fx-font-weight: 700;");
            } else {
                sessionBanner.setStyle("-fx-background-color: #FEF2F2; -fx-border-color: #F87171; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 10px 16px;");
                lblSessionDetails.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #991B1B;");
                lblFreezeWarning.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #DC2626; -fx-font-weight: 700;");
            }
            lblSessionBadge.setText("🔴 INVENTAIRE ACTIF");
            lblSessionBadge.getStyleClass().setAll("pill-red");

            lblSessionDetails.setText(String.format("Campagne #%d • %s (Lancée le %s)",
                    currentSession.getId(), currentSession.getPerimetreLibelle(), currentSession.getDateDebut()));

            lblFreezeWarning.setText("🧊 Stock gelé : prêts bloqués sur ce périmètre");

        } else {
            // Mode veille
            btnStartCampaign.setVisible(true);
            btnStartCampaign.setManaged(true);

            btnCloturerSession.setVisible(false);
            btnCloturerSession.setManaged(false);

            btnAnnulerSession.setVisible(false);
            btnAnnulerSession.setManaged(false);

            if (ThemeService.isDarkMode()) {
                sessionBanner.setStyle("-fx-background-color: #161B22; -fx-border-color: #30363D; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 10px 16px;");
                lblSessionDetails.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #C9D1D9;");
                lblFreezeWarning.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #8B949E; -fx-font-weight: 600;");
            } else {
                sessionBanner.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 10px 16px;");
                lblSessionDetails.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 600; -fx-text-fill: #334155;");
                lblFreezeWarning.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B; -fx-font-weight: 600;");
            }
            lblSessionBadge.setText("🟢 Mode Veille");
            lblSessionBadge.getStyleClass().setAll("pill-green");

            lblSessionDetails.setText("Aucune session active — Les mouvements de prêt/retour fonctionnent normalement sur toute la BCD.");
            lblFreezeWarning.setText("Prêts ouverts");
        }
    }

    private void configureToggleGroup() {
        tbManquants.setToggleGroup(filterToggleGroup);
        tbPresents.setToggleGroup(filterToggleGroup);
        tbPrets.setToggleGroup(filterToggleGroup);
        if (tbSurplus != null) tbSurplus.setToggleGroup(filterToggleGroup);
        tbTous.setToggleGroup(filterToggleGroup);
    }

    private void configureTable() {
        // N° Exemplaire
        colId.setCellValueFactory(cell ->
                new SimpleStringProperty("#" + cell.getValue().getExemplaireId()));
        colId.setStyle("-fx-alignment: CENTER; -fx-font-weight: bold;");

        // Titre & Auteur
        colTitre.setCellValueFactory(cell -> {
            RecolementItem item = cell.getValue();
            String auteur = item.getAuteur().isBlank() ? "" : " – " + item.getAuteur();
            return new SimpleStringProperty(item.getTitre() + auteur);
        });

        // ISBN
        colIsbn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getIsbn()));
        colIsbn.setStyle("-fx-alignment: CENTER;");

        // Statut de pointage avec badge stylisé
        colStatut.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colStatut.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            @Override
            protected void updateItem(RecolementItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    switch (item.getStatutInventaire()) {
                        case PRESENT -> {
                            badge.setText("✅ Présent");
                            badge.getStyleClass().setAll("pill-green");
                        }
                        case EN_PRET -> {
                            badge.setText("👤 En prêt");
                            badge.getStyleClass().setAll("pill-blue");
                        }
                        case MANQUANT -> {
                            badge.setText("⚠️ Manquant");
                            badge.getStyleClass().setAll("pill-orange");
                        }
                        case SURPLUS -> {
                            badge.setText("✨ Surplus");
                            badge.getStyleClass().setAll("pill-purple");
                        }
                    }
                    badge.setAlignment(Pos.CENTER);
                    setGraphic(badge);
                    setAlignment(Pos.CENTER);
                }
            }
        });

        // Motif d'écart (+/-)
        colMotifEcart.setCellValueFactory(cell -> new SimpleObjectProperty<>(cell.getValue()));
        colMotifEcart.setCellFactory(col -> new TableCell<>() {
            private final Label lblMotif = new Label();
            @Override
            protected void updateItem(RecolementItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    MotifEcart motif = item.getMotifEcart();
                    if (motif != null && motif != MotifEcart.AUCUN) {
                        lblMotif.setText(motif.getLibelle());
                        if (motif == MotifEcart.PERDU) {
                            lblMotif.setStyle("-fx-text-fill: #DC2626; -fx-font-weight: 700; -fx-font-size: 11.5px;");
                        } else if (motif == MotifEcart.PILON) {
                            lblMotif.setStyle("-fx-text-fill: #EA580C; -fx-font-weight: 700; -fx-font-size: 11.5px;");
                        } else if (motif == MotifEcart.RETROUVE) {
                            lblMotif.setStyle("-fx-text-fill: #7E22CE; -fx-font-weight: 700; -fx-font-size: 11.5px;");
                        } else {
                            lblMotif.setStyle("-fx-text-fill: #2563EB; -fx-font-weight: 600; -fx-font-size: 11.5px;");
                        }
                        setGraphic(lblMotif);
                    } else {
                        setGraphic(null);
                        setText("–");
                        setStyle("-fx-alignment: CENTER; -fx-text-fill: #94A3B8;");
                    }
                }
            }
        });

        // Informations d'emprunt ou de scan
        colInfo.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getEmprunteurAffichage()));

        // Remarque et état physique
        colRemarque.setCellValueFactory(cell -> {
            RecolementItem item = cell.getValue();
            String rem = item.getRemarque();
            if (!item.getMotifCommentaire().isBlank()) {
                rem = (rem.isBlank() ? "" : rem + " | ") + item.getMotifCommentaire();
            }
            return new SimpleStringProperty(rem.isBlank() ? "–" : rem);
        });

        recolementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Menu contextuel & interactions rapides sur chaque ligne
        recolementTable.setRowFactory(tv -> {
            TableRow<RecolementItem> row = new TableRow<>();
            ContextMenu contextMenu = new ContextMenu();

            MenuItem copyIsbnItem = new MenuItem("📋 Copier l'ISBN");
            copyIsbnItem.setOnAction(e -> {
                RecolementItem item = row.getItem();
                if (item != null && item.getIsbn() != null && !item.getIsbn().isBlank()) {
                    copyToClipboard(item.getIsbn(), "ISBN copié : " + item.getIsbn());
                }
            });

            MenuItem copyIdItem = new MenuItem("📋 Copier le N° d'exemplaire");
            copyIdItem.setOnAction(e -> {
                RecolementItem item = row.getItem();
                if (item != null) {
                    copyToClipboard("#" + item.getExemplaireId(), "N° exemplaire copié : #" + item.getExemplaireId());
                }
            });

            MenuItem copyAllItem = new MenuItem("📋 Copier Titre & ISBN");
            copyAllItem.setOnAction(e -> {
                RecolementItem item = row.getItem();
                if (item != null) {
                    copyToClipboard(item.getTitre() + " - " + item.getIsbn(), "Titre & ISBN copiés");
                }
            });

            MenuItem fillScanItem = new MenuItem("⚡ Insérer dans le champ de scan");
            fillScanItem.setOnAction(e -> {
                RecolementItem item = row.getItem();
                if (item != null && item.getIsbn() != null) {
                    scanField.setText(item.getIsbn());
                    scanField.requestFocus();
                    scanField.selectAll();
                    lblFeedback.setText("📋 ISBN " + item.getIsbn() + " inséré dans le scan (appuyez sur Entrée)");
                    lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
                }
            });

            MenuItem pointerDirectItem = new MenuItem("✅ Pointer directement comme PRÉSENT");
            pointerDirectItem.setOnAction(e -> {
                RecolementItem item = row.getItem();
                if (item != null) {
                    pointerExemplaireDirect(item);
                }
            });

            MenuItem qualifierItem = new MenuItem("✏️ Justifier l'écart (Motif manquant...)");
            qualifierItem.setOnAction(e -> {
                RecolementItem item = row.getItem();
                if (item != null) {
                    afficherModaleEcart(item);
                }
            });

            contextMenu.getItems().addAll(copyIsbnItem, copyIdItem, copyAllItem, new SeparatorMenuItem(),
                    fillScanItem, pointerDirectItem, new SeparatorMenuItem(), qualifierItem);

            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty())
                            .then((ContextMenu) null)
                            .otherwise(contextMenu)
            );

            // Double clic : met immédiatement l'ISBN dans le champ de scan et sélectionne
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    RecolementItem item = row.getItem();
                    if (item != null && item.getIsbn() != null) {
                        scanField.setText(item.getIsbn());
                        scanField.requestFocus();
                        scanField.selectAll();
                        lblFeedback.setText("📋 ISBN " + item.getIsbn() + " prêt pour validation (appuyez sur Entrée)");
                        lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
                    }
                }
            });

            return row;
        });

        // Raccourci clavier Cmd+C / Ctrl+C pour copier l'ISBN
        recolementTable.setOnKeyPressed(event -> {
            if (event.isShortcutDown() && event.getCode() == KeyCode.C) {
                RecolementItem sel = recolementTable.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getIsbn() != null && !sel.getIsbn().isBlank()) {
                    copyToClipboard(sel.getIsbn(), "ISBN copié dans le presse-papier : " + sel.getIsbn());
                    event.consume();
                }
            }
        });
    }

    // =====================================================================
    //  Actions Rapides — Copie & Pointage Direct
    // =====================================================================

    @FXML
    private void handleCopierIsbn() {
        RecolementItem sel = recolementTable.getSelectionModel().getSelectedItem();
        if (sel != null && sel.getIsbn() != null && !sel.getIsbn().isBlank()) {
            copyToClipboard(sel.getIsbn(), "ISBN copié dans le presse-papier : " + sel.getIsbn());
        } else {
            lblFeedback.setText("⚠️ Veuillez sélectionner une ligne pour copier son ISBN.");
            lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #EA580C; -fx-font-weight: bold;");
        }
    }

    @FXML
    private void handlePointerDirect() {
        RecolementItem sel = recolementTable.getSelectionModel().getSelectedItem();
        if (sel != null) {
            pointerExemplaireDirect(sel);
        }
    }

    private void pointerExemplaireDirect(RecolementItem item) {
        if (item.getStatutInventaire() == StatutInventaire.PRESENT) {
            playWarningSound();
            lblFeedback.setText("ℹ️ Exemplaire #" + item.getExemplaireId() + " déjà validé présent à " + item.getHeureScan());
            lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
            return;
        }

        String timeStr = LocalTime.now().format(TIME_FMT);
        item.marquerPresent(timeStr);
        if (currentSession != null) {
            sessionService.pointerExemplaire(currentSession.getId(), item.getExemplaireId(), timeStr, false);
        }
        playBeep();
        updateKpis();
        recolementTable.refresh();
        lblFeedback.setText("✅ " + item.getTitre() + " (Ex. #" + item.getExemplaireId() + ") validé présent à " + timeStr);
        lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #15803D; -fx-font-weight: bold;");
        if (btnPointerDirect != null) btnPointerDirect.setDisable(true);
    }

    private void copyToClipboard(String text, String message) {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
        lblFeedback.setText("📋 " + message);
        lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #15803D; -fx-font-weight: bold;");
    }

    // =====================================================================
    //  Chargement des Données
    // =====================================================================

    public void loadData() {
        try {
            masterItems.clear();
            List<RecolementItem> items;
            if (currentSession != null && currentSession.isActive()) {
                items = sessionService.getItemsPourSession(currentSession);
            } else {
                items = exemplaireDao.findAllForRecolement();
            }
            masterItems.addAll(items);
            updateKpis();
            recolementTable.refresh();
        } catch (SQLException e) {
            showError("Erreur de chargement", "Impossible de charger les données : " + e.getMessage());
        }
    }

    private void updateKpis() {
        int total = masterItems.size();
        long presents = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.PRESENT).count();
        long prets = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.EN_PRET).count();
        long manquants = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.MANQUANT).count();

        lblTotalCount.setText(String.valueOf(total));

        double pctPresents = total > 0 ? (presents * 100.0) / total : 0;
        lblPresentCount.setText(String.format("%d (%.0f%%)", presents, pctPresents));

        double pctPrets = total > 0 ? (prets * 100.0) / total : 0;
        lblPretCount.setText(String.format("%d (%.0f%%)", prets, pctPrets));

        double pctManquants = total > 0 ? (manquants * 100.0) / total : 0;
        lblManquantCount.setText(String.format("%d (%.0f%%)", manquants, pctManquants));

        long verifiables = presents + prets;
        double progress = total > 0 ? (double) verifiables / total : 0;
        progressBar.setProgress(progress);
        lblProgressPercent.setText(String.format("%.0f%% (%d / %d vérifiés)", progress * 100, verifiables, total));
    }

    // =====================================================================
    //  Filtrage de la Table
    // =====================================================================

    @FXML
    private void handleFilterChange() {
        applyFilter();
    }

    private void applyFilter() {
        String query = tableSearchField.getText() != null ? tableSearchField.getText().trim().toLowerCase() : "";

        filteredItems.setPredicate(item -> {
            // 1. Filtre par onglet/statut
            if (tbManquants.isSelected() && item.getStatutInventaire() != StatutInventaire.MANQUANT) {
                return false;
            }
            if (tbPresents.isSelected() && item.getStatutInventaire() != StatutInventaire.PRESENT) {
                return false;
            }
            if (tbPrets.isSelected() && item.getStatutInventaire() != StatutInventaire.EN_PRET) {
                return false;
            }
            if (tbSurplus != null && tbSurplus.isSelected() && item.getStatutInventaire() != StatutInventaire.SURPLUS) {
                return false;
            }

            // 2. Filtre textuel
            if (!query.isEmpty()) {
                boolean matchTitre = item.getTitre().toLowerCase().contains(query);
                boolean matchAuteur = item.getAuteur().toLowerCase().contains(query);
                boolean matchIsbn = item.getIsbn().toLowerCase().contains(query);
                boolean matchId = String.valueOf(item.getExemplaireId()).contains(query);
                boolean matchEmp = item.getEmprunteurAffichage().toLowerCase().contains(query);
                boolean matchMotif = item.getMotifEcart().getLibelle().toLowerCase().contains(query);
                return matchTitre || matchAuteur || matchIsbn || matchId || matchEmp || matchMotif;
            }

            return true;
        });

        recolementTable.refresh();
    }

    // =====================================================================
    //  Scan Douchette & Rapprochement physique
    // =====================================================================

    @FXML
    private void handleScanSubmit() {
        String raw = scanField.getText();
        if (raw == null || raw.isBlank()) return;
        raw = raw.trim();

        // 1. Extraction d'un numéro d'exemplaire (#42 ou 42)
        int parsedId = -1;
        if (raw.startsWith("#")) {
            try {
                parsedId = Integer.parseInt(raw.substring(1).trim());
            } catch (NumberFormatException ignored) {}
        } else {
            try {
                parsedId = Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {}
        }

        // 2. Recherche dans le périmètre actuel
        RecolementItem matched = null;
        if (parsedId > 0) {
            final int id = parsedId;
            matched = masterItems.stream()
                    .filter(i -> i.getExemplaireId() == id)
                    .findFirst().orElse(null);
        }

        // Si non trouvé par ID, tester par code ISBN
        if (matched == null) {
            String cleanIsbn = LivreMetadataService.cleanIsbn(raw);
            if (!cleanIsbn.isEmpty()) {
                // Trouver le premier exemplaire non encore scanné de cet ISBN
                matched = masterItems.stream()
                        .filter(i -> i.getIsbn().equalsIgnoreCase(cleanIsbn) && i.getStatutInventaire() == StatutInventaire.MANQUANT)
                        .findFirst()
                        .orElse(null);

                // Si tous déjà scannés, prendre n'importe lequel pour info
                if (matched == null) {
                    matched = masterItems.stream()
                            .filter(i -> i.getIsbn().equalsIgnoreCase(cleanIsbn))
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        // Si non trouvé par ID ni ISBN, tester par le NOM / TITRE du livre !
        if (matched == null) {
            String normSearch = stripAccents(raw);
            if (!normSearch.isEmpty()) {
                // Trouver le premier exemplaire manquant dont le titre contient le texte recherché
                matched = masterItems.stream()
                        .filter(i -> stripAccents(i.getTitre()).contains(normSearch) && i.getStatutInventaire() == StatutInventaire.MANQUANT)
                        .findFirst()
                        .orElse(null);

                // Si aucun manquant, prendre le premier correspondant pour info
                if (matched == null) {
                    matched = masterItems.stream()
                            .filter(i -> stripAccents(i.getTitre()).contains(normSearch))
                            .findFirst()
                            .orElse(null);
                }
            }
        }

        String timeStr = LocalTime.now().format(TIME_FMT);

        // 3. Traitement selon le résultat
        if (matched != null) {
            if (matched.getStatutInventaire() == StatutInventaire.MANQUANT) {
                matched.marquerPresent(timeStr);
                if (currentSession != null) {
                    sessionService.pointerExemplaire(currentSession.getId(), matched.getExemplaireId(), timeStr, false);
                }
                playBeep();
                updateKpis();
                recolementTable.refresh();
                lblFeedback.setText("✅ " + matched.getTitre() + " (Ex. #" + matched.getExemplaireId() + ") validé présent à " + timeStr);
                lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #15803D; -fx-font-weight: bold;");

            } else if (matched.getStatutInventaire() == StatutInventaire.PRESENT) {
                playWarningSound();
                lblFeedback.setText("ℹ️ Exemplaire #" + matched.getExemplaireId() + " déjà scanné à " + matched.getHeureScan());
                lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #1D4ED8; -fx-font-weight: bold;");

            } else if (matched.getStatutInventaire() == StatutInventaire.EN_PRET) {
                playAlertSound();
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(alert);
                alert.setTitle("Livre noté en prêt");
                alert.setHeaderText("L'exemplaire physique #" + matched.getExemplaireId() + " est noté comme emprunté !");
                alert.setContentText(String.format("Ce livre était emprunté par : %s (%s)%n"
                                + "Date d'emprunt : %s%n%n"
                                + "Souhaitez-vous enregistrer son RETOUR immédiatement et le marquer présent en rayon ?",
                        matched.getEmprunteurNom(), matched.getEmprunteurClasse(),
                        matched.getDateEmprunt() != null ? matched.getDateEmprunt() : "–"));

                Optional<ButtonType> res = alert.showAndWait();
                if (res.isPresent() && res.get() == ButtonType.OK) {
                    try {
                        empruntService.retournerParExemplaire(matched.getExemplaireId());
                        matched.marquerPresent(timeStr);
                        if (currentSession != null) {
                            sessionService.pointerExemplaire(currentSession.getId(), matched.getExemplaireId(), timeStr, false);
                        }
                        updateKpis();
                        recolementTable.refresh();
                        lblFeedback.setText("✅ Retour enregistré : exemplaire #" + matched.getExemplaireId() + " retourné et validé présent !");
                        lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #15803D; -fx-font-weight: bold;");
                    } catch (Exception ex) {
                        showError("Erreur de retour", ex.getMessage());
                    }
                }
            }
        } else {
            // Le livre scanné n'est PAS dans le périmètre actif !
            // Vérifier s'il existe dans la BCD pour le qualifier en SURPLUS
            Exemplaire horsPerimetre = null;
            if (parsedId > 0) {
                try {
                    horsPerimetre = exemplaireDao.findById(parsedId);
                } catch (Exception ignored) {}
            }

            if (horsPerimetre != null) {
                playAlertSound();
                try {
                    Livre livre = livreDao.findByIsbn(horsPerimetre.getIsbn());
                    String titreLivre = livre != null ? livre.getTitre() : "Livre inconnu";
                    String auteurLivre = livre != null ? livre.getAuteur() : "";

                    Alert surplusAlert = new Alert(Alert.AlertType.CONFIRMATION);
                    MainApp.prepareDialog(surplusAlert);
                    surplusAlert.setTitle("Exemplaire hors périmètre (Surplus)");
                    surplusAlert.setHeaderText("✨ Exemplaire retrouvé hors périmètre !");
                    surplusAlert.setContentText(String.format("L'exemplaire #%d ('%s') ne fait pas partie du périmètre de l'inventaire en cours.%n%n"
                            + "Souhaitez-vous l'ajouter à la session en tant qu'EXEMPLAIRE EN SURPLUS / RETROUVÉ ?",
                            horsPerimetre.getId(), titreLivre));

                    if (surplusAlert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        RecolementItem surplusItem = new RecolementItem(
                                horsPerimetre.getId(), horsPerimetre.getIsbn(), titreLivre, auteurLivre, "",
                                "✨ Retrouvé en surplus", horsPerimetre.getStatut(), "", "", null, null
                        );
                        surplusItem.marquerSurplus(timeStr);
                        masterItems.add(surplusItem);
                        if (currentSession != null) {
                            sessionService.pointerExemplaire(currentSession.getId(), horsPerimetre.getId(), timeStr, true);
                        }
                        updateKpis();
                        recolementTable.refresh();
                        lblFeedback.setText("✨ Exemplaire #" + horsPerimetre.getId() + " ajouté en SURPLUS !");
                        lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #7E22CE; -fx-font-weight: bold;");
                    }
                } catch (SQLException e) {
                    showError("Erreur", e.getMessage());
                }
            } else {
                playAlertSound();
                lblFeedback.setText("❓ Aucun livre ni exemplaire trouvé pour le code : " + raw);
                lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #DC2626; -fx-font-weight: bold;");
            }
        }

        scanField.clear();
        Platform.runLater(scanField::requestFocus);
    }

    // =====================================================================
    //  Qualification des Écarts (+/-)
    // =====================================================================

    @FXML
    private void handleQualifierEcart() {
        RecolementItem selected = recolementTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        // Ouvrir une modale pour qualifier l'écart
        afficherModaleEcart(selected);
    }

    private void afficherModaleEcart(RecolementItem item) {
        if (modalOverlay == null || modalCard == null) return;

        modalCard.getChildren().clear();

        // En-tête
        Label title = new Label("✏️ Justifier l'écart de stock");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        Label subtitle = new Label("Exemplaire #" + item.getExemplaireId() + " : " + item.getTitre());
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        // Menu déroulant Motif
        Label lblMotif = new Label("Motif de l'écart :");
        lblMotif.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #334155;");

        ComboBox<MotifEcart> cbMotifs = new ComboBox<>();
        cbMotifs.getItems().addAll(MotifEcart.values());
        cbMotifs.setValue(item.getMotifEcart() != null ? item.getMotifEcart() : MotifEcart.AUCUN);
        cbMotifs.setMaxWidth(Double.MAX_VALUE);

        // Champ commentaire
        Label lblComm = new Label("Précision / Commentaire :");
        lblComm.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #334155;");

        TextField txtComm = new TextField(item.getMotifCommentaire());
        txtComm.setPromptText("ex: Signalé perdu par l'élève, page déchirée, en réparation...");

        // Boutons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnSave = new Button("Enregistrer la justification");
        btnSave.getStyleClass().add("btn-blue");

        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("btn-outline");
        btnCancel.setOnAction(e -> closeModal());

        btnSave.setOnAction(e -> {
            MotifEcart sel = cbMotifs.getValue();
            String comm = txtComm.getText() != null ? txtComm.getText().trim() : "";
            item.setMotifEcart(sel);
            item.setMotifCommentaire(comm);

            if (currentSession != null) {
                sessionService.updateMotifEcart(currentSession.getId(), item.getExemplaireId(), sel, comm);
            }

            recolementTable.refresh();
            closeModal();
            lblFeedback.setText("✅ Justification enregistrée pour l'exemplaire #" + item.getExemplaireId());
            lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #15803D; -fx-font-weight: bold;");
        });

        btnBox.getChildren().addAll(btnCancel, btnSave);

        modalCard.getChildren().addAll(title, subtitle, lblMotif, cbMotifs, lblComm, txtComm, new Separator(), btnBox);

        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    // =====================================================================
    //  Lancement de Campagne d'Inventaire
    // =====================================================================

    @FXML
    private void handleStartCampaign() {
        afficherModaleLancementCampagne();
    }

    private void afficherModaleLancementCampagne() {
        if (modalOverlay == null || modalCard == null) return;

        modalCard.getChildren().clear();

        Label title = new Label("🚀 Démarrer une Campagne d'Inventaire");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        Label desc = new Label("Choisissez le périmètre de l'inventaire. Pendant toute la durée de la session, "
                + "les mouvements (prêts) sur ce périmètre seront automatiquement gelés pour garantir l'intégrité du stock.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-line-spacing: 2px;");

        // Options de Périmètre
        ToggleGroup perimGroup = new ToggleGroup();

        RadioButton rbTout = new RadioButton("📚 Tout le catalogue (Inventaire général de la BCD)");
        rbTout.setToggleGroup(perimGroup);
        rbTout.setSelected(true);
        rbTout.setStyle("-fx-font-weight: 600; -fx-font-size: 12.5px;");

        RadioButton rbGenre = new RadioButton("🏷️ Par Genre :");
        rbGenre.setToggleGroup(perimGroup);
        rbGenre.setStyle("-fx-font-weight: 600; -fx-font-size: 12.5px;");
        ComboBox<String> cbGenre = new ComboBox<>();
        cbGenre.getItems().addAll(LivreClassificationService.GENRES_STANDARDS);
        cbGenre.getSelectionModel().selectFirst();
        cbGenre.setPrefWidth(220);
        cbGenre.disableProperty().bind(rbGenre.selectedProperty().not());
        HBox boxGenre = new HBox(8, rbGenre, cbGenre);
        boxGenre.setAlignment(Pos.CENTER_LEFT);

        RadioButton rbTheme = new RadioButton("🎨 Par Thème :");
        rbTheme.setToggleGroup(perimGroup);
        rbTheme.setStyle("-fx-font-weight: 600; -fx-font-size: 12.5px;");
        ComboBox<String> cbTheme = new ComboBox<>();
        cbTheme.getItems().addAll(LivreClassificationService.THEMES_STANDARDS);
        cbTheme.getSelectionModel().selectFirst();
        cbTheme.setPrefWidth(220);
        cbTheme.disableProperty().bind(rbTheme.selectedProperty().not());
        HBox boxTheme = new HBox(8, rbTheme, cbTheme);
        boxTheme.setAlignment(Pos.CENTER_LEFT);

        RadioButton rbNiveau = new RadioButton("🎓 Par Niveau scolaire :");
        rbNiveau.setToggleGroup(perimGroup);
        rbNiveau.setStyle("-fx-font-weight: 600; -fx-font-size: 12.5px;");
        ComboBox<String> cbNiveau = new ComboBox<>();
        cbNiveau.getItems().addAll(LivreClassificationService.NIVEAUX_STANDARDS);
        cbNiveau.getSelectionModel().selectFirst();
        cbNiveau.setPrefWidth(220);
        cbNiveau.disableProperty().bind(rbNiveau.selectedProperty().not());
        HBox boxNiveau = new HBox(8, rbNiveau, cbNiveau);
        boxNiveau.setAlignment(Pos.CENTER_LEFT);

        RadioButton rbLivre = new RadioButton("📖 Un livre spécifique (Titre ou ISBN) :");
        rbLivre.setToggleGroup(perimGroup);
        rbLivre.setStyle("-fx-font-weight: 600; -fx-font-size: 12.5px;");
        ComboBox<String> cbLivre = new ComboBox<>();
        try {
            for (Livre l : livreDao.findAll()) {
                cbLivre.getItems().add(l.getIsbn() + " - " + l.getTitre());
            }
            if (!cbLivre.getItems().isEmpty()) cbLivre.getSelectionModel().selectFirst();
        } catch (Exception ignored) {}
        cbLivre.setPrefWidth(260);
        cbLivre.disableProperty().bind(rbLivre.selectedProperty().not());
        HBox boxLivre = new HBox(8, rbLivre, cbLivre);
        boxLivre.setAlignment(Pos.CENTER_LEFT);

        VBox perimBox = new VBox(10, rbTout, boxGenre, boxTheme, boxNiveau, boxLivre);
        perimBox.getStyleClass().add("dialog-subcard");

        // Alerte Gel
        Label alertFreeze = new Label("🧊 Attention : dès le lancement, aucun prêt ne pourra être effectué sur les livres du périmètre choisi jusqu'à la clôture.");
        alertFreeze.setWrapText(true);
        alertFreeze.getStyleClass().add("dialog-alert-freeze");

        // Boutons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnLaunch = new Button("Lancer l'inventaire et geler le stock");
        btnLaunch.getStyleClass().add("btn-blue");

        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("btn-outline");
        btnCancel.setOnAction(e -> closeModal());

        btnLaunch.setOnAction(e -> {
            PerimetreType pType = PerimetreType.TOUT;
            String pVal = "";

            if (rbGenre.isSelected()) {
                pType = PerimetreType.GENRE;
                pVal = cbGenre.getValue();
            } else if (rbTheme.isSelected()) {
                pType = PerimetreType.THEME;
                pVal = cbTheme.getValue();
            } else if (rbNiveau.isSelected()) {
                pType = PerimetreType.NIVEAU;
                pVal = cbNiveau.getValue();
            } else if (rbLivre.isSelected()) {
                pType = PerimetreType.LIVRE;
                String rawLivre = cbLivre.getValue();
                if (rawLivre != null && rawLivre.contains(" - ")) {
                    pVal = rawLivre.substring(0, rawLivre.indexOf(" - ")).trim();
                } else {
                    pVal = rawLivre;
                }
            }

            try {
                currentSession = sessionService.demarrerSession(pType, pVal);
                actualiserBandeauSession();
                loadData();
                closeModal();
                showInfo("Session d'inventaire lancée",
                        String.format("La session d'inventaire a démarré avec succès !%n%n"
                                + "🎯 Périmètre : %s%n"
                                + "📚 %d exemplaire(s) sous inventaire%n"
                                + "🧊 Les prêts sont temporairement gelés sur ce rayon.",
                                currentSession.getPerimetreLibelle(), masterItems.size()));
            } catch (Exception ex) {
                showError("Erreur de lancement", ex.getMessage());
            }
        });

        btnBox.getChildren().addAll(btnCancel, btnLaunch);

        modalCard.getChildren().addAll(title, desc, perimBox, alertFreeze, new Separator(), btnBox);

        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    // =====================================================================
    //  Clôture & Validation de la Session
    // =====================================================================

    @FXML
    private void handleCloturerSession() {
        if (currentSession == null) return;

        List<RecolementItem> manquants = masterItems.stream()
                .filter(i -> i.getStatutInventaire() == StatutInventaire.MANQUANT)
                .toList();

        List<RecolementItem> nonJustifies = manquants.stream()
                .filter(i -> i.getMotifEcart() == null || i.getMotifEcart() == MotifEcart.AUCUN)
                .toList();

        // ⚠️ Si des manquants n'ont pas de motif précisé, afficher la modale de qualification obligatoire !
        if (!nonJustifies.isEmpty()) {
            afficherModaleClotureAvecQualification(manquants, nonJustifies);
            return;
        }

        // Si tous les manquants sont qualifiés (ou aucun manquant), afficher le bilan avant sortie de stock
        afficherModaleConfirmationCloture(manquants);
    }

    private void afficherModaleClotureAvecQualification(List<RecolementItem> manquants, List<RecolementItem> nonJustifies) {
        if (modalOverlay == null || modalCard == null) return;
        modalCard.getChildren().clear();

        // En-tête d'alerte
        Label title = new Label("⚠️ Qualification obligatoire des manquants");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #DC2626;");

        Label desc = new Label(String.format("Il reste %d livre(s) manquant(s) sans motif sur un total de %d manquant(s).%n%n"
                + "Pour valider l'inventaire et mettre à jour le stock de la BCD, vous devez impérativement indiquer la raison de leur absence (Perdu, Mis au pilon, En réparation, etc.).",
                nonJustifies.size(), manquants.size()));
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #334155; -fx-line-spacing: 2px;");

        // Carte 1 : Option rapide en lot
        VBox batchBox = new VBox(8);
        batchBox.getStyleClass().add("dialog-alert-freeze");

        Label batchTitle = new Label("⚡ Option rapide : attribuer un motif commun aux " + nonJustifies.size() + " manquants non qualifiés :");
        batchTitle.getStyleClass().add("dialog-alert-title");

        ComboBox<MotifEcart> cbBatchMotif = new ComboBox<>();
        cbBatchMotif.getItems().addAll(
                MotifEcart.PERDU,
                MotifEcart.PILON,
                MotifEcart.INEXPLIQUE,
                MotifEcart.REPARATION
        );
        cbBatchMotif.setValue(MotifEcart.PERDU);
        cbBatchMotif.setMaxWidth(Double.MAX_VALUE);

        TextField txtBatchComm = new TextField();
        txtBatchComm.setPromptText("Précision facultative (ex: Inventaire officiel " + LocalDate.now().getYear() + ")…");

        Button btnBatchApply = new Button("Appliquer ce motif à tous les " + nonJustifies.size() + " manquants");
        btnBatchApply.getStyleClass().add("btn-blue");

        batchBox.getChildren().addAll(batchTitle, cbBatchMotif, txtBatchComm, btnBatchApply);

        // Avertissement impact stock
        Label alertStock = new Label("📦 Impact sur le stock : À la validation, les exemplaires manquants déclarés Perdus, Mis au pilon ou Inexpliqués seront automatiquement retirés du stock de la BCD (et décomptés du catalogue).");
        alertStock.setWrapText(true);
        alertStock.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #1E40AF; -fx-font-weight: 600; -fx-background-color: #EFF6FF; -fx-padding: 10px 12px; -fx-border-color: #BFDBFE; -fx-border-radius: 8px; -fx-background-radius: 8px;");

        // Boutons
        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnCancel = new Button("Annuler / Continuer l'inventaire");
        btnCancel.getStyleClass().add("btn-outline");
        btnCancel.setOnAction(e -> closeModal());

        Button btnDetailTable = new Button("✏️ Qualifier un par un dans le tableau");
        btnDetailTable.getStyleClass().add("btn-outline");
        btnDetailTable.setOnAction(e -> {
            closeModal();
            tbManquants.setSelected(true);
            applyFilter();
            if (!nonJustifies.isEmpty()) {
                recolementTable.getSelectionModel().select(nonJustifies.get(0));
                recolementTable.scrollTo(nonJustifies.get(0));
            }
            lblFeedback.setText("💡 Cliquez sur 'Justifier l'écart' pour préciser le motif de chaque livre manquant.");
            lblFeedback.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #1D4ED8; -fx-font-weight: bold;");
        });

        btnBatchApply.setOnAction(e -> {
            MotifEcart m = cbBatchMotif.getValue();
            String comm = txtBatchComm.getText() != null ? txtBatchComm.getText().trim() : "";
            for (RecolementItem it : nonJustifies) {
                it.setMotifEcart(m);
                it.setMotifCommentaire(comm);
                if (currentSession != null) {
                    sessionService.updateMotifEcart(currentSession.getId(), it.getExemplaireId(), m, comm);
                }
            }
            recolementTable.refresh();
            // Tous qualifiés -> passer à la confirmation de clôture
            afficherModaleConfirmationCloture(manquants);
        });

        btnBox.getChildren().addAll(btnCancel, btnDetailTable);

        modalCard.getChildren().addAll(title, desc, batchBox, alertStock, new Separator(), btnBox);
        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    private void afficherModaleConfirmationCloture(List<RecolementItem> manquants) {
        if (modalOverlay == null || modalCard == null) return;
        modalCard.getChildren().clear();

        long presents = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.PRESENT).count();
        long prets = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.EN_PRET).count();
        long surplus = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.SURPLUS).count();

        // Compter les sorties de stock (manquants sauf réparation)
        long sortiesStock = manquants.stream().filter(i -> i.getMotifEcart() != MotifEcart.REPARATION).count();
        long enReparation = manquants.stream().filter(i -> i.getMotifEcart() == MotifEcart.REPARATION).count();

        Label title = new Label("✅ Clôture & Rapprochement de Stock");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        VBox summaryBox = new VBox(6);
        summaryBox.getStyleClass().add("dialog-subcard");
        summaryBox.getChildren().addAll(
                new Label("📊 Bilan définitif du récolement :"),
                new Label("• Périmètre suivi : " + masterItems.size() + " exemplaire(s)"),
                new Label("• Présents vérifiés : " + presents),
                new Label("• En prêt justifiés : " + prets),
                new Label("• Écarts manquants : " + manquants.size() + " (tous qualifiés)"),
                new Label("• Surplus retrouvés : " + surplus)
        );

        VBox stockBox = new VBox(6);
        stockBox.getStyleClass().add("dialog-alert-freeze");
        Label lblStockAction = new Label(String.format("📦 Sorties de stock appliquées :%n"
                + "• %d livre(s) manquant(s) seront DÉFINITIVEMENT RETIRÉS du stock de la BCD.%n"
                + (enReparation > 0 ? "• " + enReparation + " exemplaire(s) maintenu(s) avec la mention 'En réparation'.%n" : "")
                + "• Le gel de stock sera levé et le Procès-Verbal officiel généré.", sortiesStock));
        lblStockAction.getStyleClass().add("dialog-alert-text");
        stockBox.getChildren().add(lblStockAction);

        HBox btnBox = new HBox(10);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        Button btnCancel = new Button("Annuler");
        btnCancel.getStyleClass().add("btn-outline");
        btnCancel.setOnAction(e -> closeModal());

        Button btnValider = new Button("Valider, mettre à jour le stock et clôturer");
        btnValider.setStyle("-fx-background-color: #16A34A; -fx-text-fill: white; -fx-font-weight: 700; -fx-background-radius: 8px; -fx-padding: 8px 16px; -fx-cursor: hand;");
        btnValider.setOnAction(e -> {
            closeModal();
            procederClotureDefinitive(manquants, sortiesStock);
        });

        btnBox.getChildren().addAll(btnCancel, btnValider);
        modalCard.getChildren().addAll(title, summaryBox, stockBox, new Separator(), btnBox);

        modalOverlay.setVisible(true);
        modalOverlay.setManaged(true);
    }

    private void procederClotureDefinitive(List<RecolementItem> manquants, long sortiesStock) {
        try {
            File pv = sessionService.cloturerSession(currentSession, masterItems, "Inventaire validé par le responsable");
            int sessionId = currentSession.getId();
            currentSession = null;
            actualiserBandeauSession();
            loadData();

            showInfo("Inventaire clôturé avec succès",
                    String.format("L'inventaire #%d est clôturé avec succès !%n%n"
                            + "📦 Stock BCD mis à jour : %d exemplaire(s) manquant(s) retiré(s) du catalogue.%n"
                            + "🧊 Le gel de stock est levé sur toute la BCD.%n%n"
                            + "📄 Le Procès-Verbal officiel a été généré :%n%s",
                            sessionId, sortiesStock, pv.getAbsolutePath()));

            MainApp.openDocument(pv);

        } catch (SQLException ex) {
            showError("Erreur de clôture", ex.getMessage());
        }
    }

    @FXML
    private void handleAnnulerSession() {
        if (currentSession == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(confirm);
        confirm.setTitle("Abandon de la session");
        confirm.setHeaderText("Abandonner la session d'inventaire en cours ?");
        confirm.setContentText("Le gel de stock sera immédiatement levé sans modifier les statuts des livres.");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            try {
                sessionService.annulerSession(currentSession.getId());
                currentSession = null;
                actualiserBandeauSession();
                loadData();
                lblFeedback.setText("ℹ️ Session d'inventaire annulée. Gel de stock levé.");
                lblFeedback.setStyle("-fx-font-size: 13px; -fx-text-fill: #475569;");
            } catch (SQLException ex) {
                showError("Erreur d'annulation", ex.getMessage());
            }
        }
    }

    @FXML
    public void closeModal() {
        if (modalOverlay != null) {
            modalOverlay.setVisible(false);
            modalOverlay.setManaged(false);
            if (modalCard != null) modalCard.getChildren().clear();
        }
    }

    // =====================================================================
    //  Export CSV
    // =====================================================================

    @FXML
    private void handleExportCsv() {
        if (masterItems.isEmpty()) {
            showInfo("Aucune donnée", "Le tableau d'inventaire est vide.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Exporter le bilan du récolement");
        String cleanDate = LocalDate.now().toString().replace("-", "");
        fileChooser.setInitialFileName("bilan_recolement_bcd_" + cleanDate + ".csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Fichiers CSV (*.csv)", "*.csv")
        );

        File file = fileChooser.showSaveDialog(recolementTable.getScene().getWindow());
        if (file == null) return;

        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write("\uFEFF"); // BOM UTF-8 pour Excel

            int total = masterItems.size();
            long presents = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.PRESENT).count();
            long prets = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.EN_PRET).count();
            long manquants = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.MANQUANT).count();
            long surplus = masterItems.stream().filter(i -> i.getStatutInventaire() == StatutInventaire.SURPLUS).count();

            writer.write(String.format("# BILAN INVENTAIRE & RÉCOLEMENT BCD - ÉCOLE ALBERT CAMUS PASTEUR - %s\n", LocalDate.now()));
            if (currentSession != null) {
                writer.write(String.format("# SESSION ACTIVE : #%d - %s\n", currentSession.getId(), currentSession.getPerimetreLibelle()));
            }
            writer.write(String.format("# TOTAL SUIVI : %d\n", total));
            writer.write(String.format("# PRÉSENTS SCANNÉS : %d (%.1f%%)\n", presents, total > 0 ? (presents * 100.0) / total : 0));
            writer.write(String.format("# EN PRÊT : %d (%.1f%%)\n", prets, total > 0 ? (prets * 100.0) / total : 0));
            writer.write(String.format("# ÉCARTS MANQUANTS : %d (%.1f%%)\n", manquants, total > 0 ? (manquants * 100.0) / total : 0));
            writer.write(String.format("# SURPLUS RETROUVÉS : %d\n\n", surplus));

            writer.write("Statut_Pointage;N_Exemplaire;Titre;Auteur;ISBN;Motif_Ecart;Commentaire_Ecart;Details_Emprunteur;Remarque_Etat\n");

            for (RecolementItem i : masterItems) {
                writer.write(String.format("%s;%d;%s;%s;%s;%s;%s;%s;%s\n",
                        i.getStatutInventaire().name(),
                        i.getExemplaireId(),
                        cleanCsv(i.getTitre()),
                        cleanCsv(i.getAuteur()),
                        cleanCsv(i.getIsbn()),
                        cleanCsv(i.getMotifEcart().getLibelle()),
                        cleanCsv(i.getMotifCommentaire()),
                        cleanCsv(i.getEmprunteurAffichage()),
                        cleanCsv(i.getRemarque())
                ));
            }

            showInfo("Export réussi", String.format("Le bilan du récolement a été exporté avec succès !%n%n"
                            + "📁 Emplacement : %s%n"
                            + "📚 %d exemplaires répertoriés (%d manquants).",
                    file.getAbsolutePath(), total, (int) manquants));

        } catch (IOException ex) {
            showError("Erreur d'exportation", "Impossible d'écrire le fichier : " + ex.getMessage());
        }
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    private void playBeep() {
        SoundService.playSuccess();
        if (scanField != null) {
            AnimationHelper.flashSuccess(scanField);
        }
    }

    private void playAlertSound() {
        SoundService.playError();
        if (scanField != null) {
            AnimationHelper.flashError(scanField);
        }
    }

    private void playWarningSound() {
        SoundService.playWarning();
        if (scanField != null) {
            AnimationHelper.flashWarning(scanField);
        }
    }

    private String cleanCsv(String val) {
        if (val == null) return "";
        return val.replace(";", ",").replace("\n", " ").trim();
    }

    private void showError(String title, String content) {
        DialogHelper.showError(title, content);
    }

    private void showInfo(String title, String content) {
        DialogHelper.showInfo(title, content);
    }

    private static String stripAccents(String s) {
        if (s == null) return "";
        String normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
