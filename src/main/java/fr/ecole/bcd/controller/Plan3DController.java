package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.ZoneBcd;
import fr.ecole.bcd.model.ZoneBcd.FaceBcd;
import fr.ecole.bcd.service.PlanBcdService;
import fr.ecole.bcd.service.SoundService;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contrôleur de l'onglet Plan 3D de la BCD &amp; Emplacement des Livres.
 *
 * <p>Offre une modélisation spatiale interactive fidèle aux 11 étagères, îlots et bacs
 * réels de l'école Albert Camus, avec vue 2D architecturale, vue 3D isométrique avec
 * extrusion et ombrage, repérage en temps réel par douchette, et inspection des livres
 * physiques stockés dans SQLite.</p>
 */
public class Plan3DController {

    @FXML private Button btnAssistantDouchette;
    @FXML private Button btnEtiquettesRayon;
    @FXML private Button btnOuvrirVueWeb;

    @FXML private TextField mapSearchField;
    @FXML private Button btnClearSearch;
    @FXML private Label lblSearchStatus;

    @FXML private HBox pillsContainer;
    @FXML private Button pillAll;
    @FXML private Button pillShelves;
    @FXML private Button pillIlots;
    @FXML private Button pillBins;
    @FXML private Button pillBD;
    @FXML private Button pillRomans;
    @FXML private Button pillAlbums;
    @FXML private Button pillDocs;
    @FXML private Button pillRevues;

    @FXML private StackPane canvasContainer;
    @FXML private Canvas mapCanvas;

    @FXML private Button btnView2D;
    @FXML private Button btnView3D;
    @FXML private Button btnZoomIn;
    @FXML private Button btnZoomOut;
    @FXML private Button btnResetView;
    @FXML private ToggleButton btnToggleLabels;

    @FXML private Label lblOrientation;

    @FXML private VBox tooltipBox;
    @FXML private Label tooltipTitle;
    @FXML private Label tooltipSub;
    @FXML private Label tooltipHint;

    @FXML private VBox sidebarCard;
    @FXML private VBox sidebarContent;

    private final PlanBcdService planService = new PlanBcdService();
    private final LivreDao livreDao = new LivreDao();

    /** Mode de vue : 2D (Plan du dessus) ou 3D (Isométrique). */
    private boolean isView3D = false;

    /** Meuble actuellement sélectionné. */
    private ZoneBcd selectedZone = null;

    /** Meuble actuellement survolé. */
    private ZoneBcd hoveredZone = null;

    /** Épingle animée sur un livre repéré par douchette ou recherche. */
    private ZoneBcd pinnedZone = null;
    private String pinnedBookTitle = null;
    private String pinnedBookCote = null;

    /** Ensemble des meubles correspondants à la recherche ou au filtre actif. */
    private final Set<String> matchedZoneIds = new HashSet<>();
    private String activeFilter = "all";

    /** Transformations de caméra (Pan & Zoom). */
    private double zoomFactor = 1.0;
    private double panOffsetX = 0.0;
    private double panOffsetY = 0.0;

    private double dragStartX;
    private double dragStartY;
    private boolean isDragging = false;

    /** Animation du pulse doré pour les résultats de recherche. */
    private double animationTick = 0.0;
    private AnimationTimer animTimer;

    @FXML
    public void initialize() {
        // Redimensionnement automatique du Canvas
        canvasContainer.widthProperty().addListener((obs, oldW, newW) -> {
            mapCanvas.setWidth(newW.doubleValue());
            render();
        });
        canvasContainer.heightProperty().addListener((obs, oldH, newH) -> {
            mapCanvas.setHeight(newH.doubleValue());
            render();
        });

        setupMouseInteractions();
        setupSearchInteractions();

        // Animation douce pour l'épingle et le surbrillance
        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                animationTick += 0.05;
                if (pinnedZone != null || !matchedZoneIds.isEmpty()) {
                    render();
                }
            }
        };
        animTimer.start();

        // Chargement initial du panneau latéral
        renderSidebarOverview();
        Platform.runLater(this::render);
    }

    /**
     * Rechargement des données lors de la navigation vers l'onglet.
     */
    public void loadData() {
        render();
        if (selectedZone != null) {
            renderSidebarDetail(selectedZone);
        } else {
            renderSidebarOverview();
        }
    }

    /* ── Interactions Souris (Pan, Zoom, Clic, Hover) ─────────────────────── */

    private void setupMouseInteractions() {
        mapCanvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown() || e.isSecondaryButtonDown()) {
                dragStartX = e.getX() - panOffsetX;
                dragStartY = e.getY() - panOffsetY;
                isDragging = false;
            }
        });

        mapCanvas.setOnMouseDragged(e -> {
            isDragging = true;
            panOffsetX = e.getX() - dragStartX;
            panOffsetY = e.getY() - dragStartY;
            hideTooltip();
            render();
        });

        mapCanvas.setOnMouseMoved(e -> {
            if (isDragging) return;
            ZoneBcd hit = pickZone(e.getX(), e.getY());
            if (hit != hoveredZone) {
                hoveredZone = hit;
                mapCanvas.setCursor(hit != null ? Cursor.HAND : Cursor.DEFAULT);
                if (hit != null) {
                    showTooltip(hit, e.getX(), e.getY());
                } else {
                    hideTooltip();
                }
                render();
            } else if (hit != null) {
                updateTooltipPosition(e.getX(), e.getY());
            }
        });

        mapCanvas.setOnMouseClicked(e -> {
            if (isDragging) {
                isDragging = false;
                return;
            }
            ZoneBcd hit = pickZone(e.getX(), e.getY());
            selectZone(hit);
        });

        mapCanvas.setOnScroll(e -> {
            double delta = e.getDeltaY();
            double factor = delta > 0 ? 1.12 : 0.89;
            double newZoom = zoomFactor * factor;
            if (newZoom >= 0.5 && newZoom <= 3.5) {
                // Centrage du zoom autour du curseur
                double mouseX = e.getX();
                double mouseY = e.getY();
                panOffsetX = mouseX - (mouseX - panOffsetX) * factor;
                panOffsetY = mouseY - (mouseY - panOffsetY) * factor;
                zoomFactor = newZoom;
                render();
            }
        });
    }

    private void showTooltip(ZoneBcd z, double x, double y) {
        tooltipTitle.setText((z.hasNumero() ? z.getNumero() + " • " : "") + z.getNom());
        tooltipSub.setText(z.getTypeLabel() + " (" + z.getCouleurReelle() + ")");
        tooltipHint.setText("Capacité : " + (z.getCapacite() > 0 ? z.getCapacite() + " livres" : "Mobilier") + " • Clic pour inspecter");
        tooltipBox.setVisible(true);
        tooltipBox.setManaged(true);
        updateTooltipPosition(x, y);
    }

    private void updateTooltipPosition(double x, double y) {
        double px = Math.min(x + 16, mapCanvas.getWidth() - 260);
        double py = Math.min(y + 16, mapCanvas.getHeight() - 100);
        tooltipBox.setTranslateX(px);
        tooltipBox.setTranslateY(py);
    }

    private void hideTooltip() {
        tooltipBox.setVisible(false);
        tooltipBox.setManaged(false);
    }

    /* ── Barre de recherche et filtres rapides ───────────────────────────── */

    private void setupSearchInteractions() {
        mapSearchField.textProperty().addListener((obs, oldV, newV) -> {
            handleSearch(newV);
        });
    }

    private void handleSearch(String query) {
        btnClearSearch.setVisible(query != null && !query.isBlank());
        btnClearSearch.setManaged(query != null && !query.isBlank());
        matchedZoneIds.clear();

        if (query == null || query.trim().length() < 2) {
            lblSearchStatus.setVisible(false);
            lblSearchStatus.setManaged(false);
            pinnedZone = null;
            render();
            return;
        }

        String q = query.trim().toLowerCase();
        for (ZoneBcd z : planService.getAllZones()) {
            if (z.matchesQuery(q)) {
                matchedZoneIds.add(z.getId());
            }
        }

        // Recherche également dans la base de données SQLite pour trouver le rayon d'un livre spécifique !
        try {
            List<Livre> livres = livreDao.searchWithCounts(q);
            if (!livres.isEmpty()) {
                Livre best = livres.getFirst();
                ZoneBcd targetZone = planService.determineZoneForLivre(best);
                if (targetZone != null) {
                    matchedZoneIds.add(targetZone.getId());
                    pinnedZone = targetZone;
                    pinnedBookTitle = best.getTitre();
                    pinnedBookCote = computeCote(best);
                }
            }
        } catch (SQLException ignored) {}

        lblSearchStatus.setVisible(true);
        lblSearchStatus.setManaged(true);
        if (matchedZoneIds.isEmpty()) {
            lblSearchStatus.setText("⚠️ Aucun rayon trouvé pour « " + query + " »");
            lblSearchStatus.setStyle("-fx-text-fill: #B91C1C; -fx-background-color: #FEF2F2;");
        } else {
            lblSearchStatus.setText("✨ " + matchedZoneIds.size() + " zone(s) surlignée(s)");
            lblSearchStatus.setStyle("-fx-text-fill: #15803D; -fx-background-color: #F0FDF4;");
        }

        render();
    }

    @FXML
    private void handleClearSearch() {
        mapSearchField.clear();
        mapSearchField.requestFocus();
    }

    /* ── Filtres Pilules (Tous, Étagères, Îlots, Bacs, BD, Romans, etc.) ─── */

    @FXML private void handleFilterAll() { applyFilter("all", pillAll); }
    @FXML private void handleFilterShelves() { applyFilter("shelves", pillShelves); }
    @FXML private void handleFilterIlots() { applyFilter("ilots", pillIlots); }
    @FXML private void handleFilterBins() { applyFilter("bins", pillBins); }
    @FXML private void handleFilterBD() { applyFilter("bd", pillBD); }
    @FXML private void handleFilterRomans() { applyFilter("romans", pillRomans); }
    @FXML private void handleFilterAlbums() { applyFilter("albums", pillAlbums); }
    @FXML private void handleFilterDocs() { applyFilter("docs", pillDocs); }
    @FXML private void handleFilterRevues() { applyFilter("revues", pillRevues); }

    private void applyFilter(String filter, Button activeBtn) {
        this.activeFilter = filter;
        pillsContainer.getChildren().forEach(n -> {
            if (n instanceof Button b) {
                b.getStyleClass().remove("map3d-pill-active");
            }
        });
        if (activeBtn != null) {
            activeBtn.getStyleClass().add("map3d-pill-active");
        }

        matchedZoneIds.clear();
        pinnedZone = null;

        if (!"all".equals(filter)) {
            for (ZoneBcd z : planService.getAllZones()) {
                boolean match = switch (filter) {
                    case "shelves" -> z.getCategorie() == ZoneBcd.CategorieMobilier.SHELF && (z.getId().equals("1") || z.getId().equals("3") || z.getId().equals("8") || z.getId().equals("11"));
                    case "ilots" -> z.getId().equals("4") || z.getId().equals("6");
                    case "bins" -> z.getCategorie() == ZoneBcd.CategorieMobilier.BIN;
                    case "bd" -> z.getId().equals("2");
                    case "romans" -> z.getId().equals("1") || z.getId().equals("3") || z.getId().equals("4");
                    case "albums" -> z.getId().equals("6") || z.getId().equals("8") || z.getId().equals("9");
                    case "docs" -> z.getId().equals("3") || z.getId().equals("4") || z.getId().equals("8") || z.getId().equals("9");
                    case "revues" -> z.getId().equals("6") || z.getId().equals("10");
                    default -> false;
                };
                if (match) matchedZoneIds.add(z.getId());
            }
        }
        render();
    }

    /* ── Changement de Vue & Contrôles Caméra ─────────────────────────────── */

    @FXML
    private void handleSetView2D() {
        isView3D = false;
        btnView2D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView2D.getStyleClass().add("btn-view-active");
        btnView3D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView3D.getStyleClass().add("btn-view-inactive");
        lblOrientation.setText("Nord : Verrière (Couloir) • Sud : Grandes Fenêtres");
        render();
    }

    @FXML
    private void handleSetView3D() {
        isView3D = true;
        btnView3D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView3D.getStyleClass().add("btn-view-active");
        btnView2D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView2D.getStyleClass().add("btn-view-inactive");
        lblOrientation.setText("Orientation 3D : Vue Isométrique depuis le Sud-Ouest");
        render();
    }

    @FXML
    private void handleZoomIn() {
        zoomFactor = Math.min(3.5, zoomFactor * 1.2);
        render();
    }

    @FXML
    private void handleZoomOut() {
        zoomFactor = Math.max(0.5, zoomFactor * 0.83);
        render();
    }

    @FXML
    private void handleResetView() {
        zoomFactor = 1.0;
        panOffsetX = 0.0;
        panOffsetY = 0.0;
        render();
    }

    @FXML
    private void handleToggleLabels() {
        btnToggleLabels.setText(btnToggleLabels.isSelected() ? "🏷️ Étiquettes : OUI" : "🏷️ Étiquettes : NON");
        render();
    }

    /* ── Actions Principales (Douchette, Étiquettes, Web) ────────────────── */

    @FXML
    private void handleOpenAssistantDouchette() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Assistant Douchette — Bip & Range");
        dialog.setHeaderText("⚡ Assistant de Rangement BCD Express\nScannez ou saisissez l'ISBN / Code-barres du livre à ranger :");
        dialog.setContentText("Code-barres / ISBN :");
        MainApp.prepareDialog(dialog);

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(input -> {
            if (input.isBlank()) return;
            try {
                Livre livre = livreDao.findByIsbn(input.trim());
                if (livre == null) {
                    List<Livre> list = livreDao.searchWithCounts(input.trim());
                    if (!list.isEmpty()) livre = list.getFirst();
                }

                if (livre != null) {
                    SoundService.playSuccess();
                    ZoneBcd target = planService.determineZoneForLivre(livre);
                    selectZone(target);
                    pinnedZone = target;
                    pinnedBookTitle = livre.getTitre();
                    pinnedBookCote = computeCote(livre);
                    render();

                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Livre identifié !");
                    info.setHeaderText("📖 " + livre.getTitre() + " (" + livre.getAuteur() + ")");
                    info.setContentText("👉 À RANGER EN : Zone " + target.getNumero() + " • " + target.getNom() +
                            "\n\n📍 Détail : " + target.getTypeLabel() +
                            "\n🏷️ Cote : " + pinnedBookCote +
                            "\n🎨 Couleur meuble : " + target.getCouleurReelle() +
                            "\n\nL'étagère a été automatiquement surlignée et sélectionnée sur le plan 3D.");
                    MainApp.prepareDialog(info);
                    info.showAndWait();
                } else {
                    SoundService.playWarning();
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Livre inconnu");
                    alert.setHeaderText("⚠️ Livre non trouvé dans la base BCD");
                    alert.setContentText("Le code « " + input + " » n'a pas été trouvé.\nVous pouvez le déposer sur la Table de Tri (Accueil) pour catalogage.");
                    MainApp.prepareDialog(alert);
                    alert.showAndWait();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });
    }

    @FXML
    private void handleOpenEtiquettesRayon() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("🏷️ Étiquettes de Rayon — BCD Albert Camus");
        dialog.setHeaderText("Signalétique officielle des 11 espaces de la BCD");
        MainApp.prepareDialog(dialog);

        VBox content = new VBox(10);
        content.setPadding(new Insets(16));
        content.setPrefWidth(540);

        Label intro = new Label("Ces étiquettes correspondent fidèlement aux meubles de la BCD pour guider les élèves :");
        intro.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569;");
        content.getChildren().add(intro);

        ScrollPane scroll = new ScrollPane();
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(420);

        VBox list = new VBox(8);
        for (ZoneBcd z : planService.getNumberedZones()) {
            HBox card = new HBox(12);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setStyle("-fx-background-color: white; -fx-padding: 10 14; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8;");

            Label num = new Label(String.valueOf(z.getNumero()));
            num.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #1D4ED8; -fx-background-color: #EFF6FF; -fx-min-width: 32px; -fx-min-height: 32px; -fx-alignment: center; -fx-background-radius: 999; -fx-border-color: #BFDBFE; -fx-border-radius: 999;");

            VBox info = new VBox(2);
            Label nom = new Label(z.getIcon() + "  " + z.getNom());
            nom.setStyle("-fx-font-weight: 700; -fx-font-size: 13px; -fx-text-fill: #0F172A;");
            Label desc = new Label(z.getTypeLabel() + " • " + z.getCotePrefix());
            desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            info.getChildren().addAll(nom, desc);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label badge = new Label(z.getCategorie().getLibelle());
            badge.setStyle("-fx-font-size: 10px; -fx-font-weight: 700; -fx-padding: 3 8; -fx-background-radius: 4; -fx-background-color: " + z.getCategorie().getColorFill() + "; -fx-text-fill: " + z.getCategorie().getColorText() + ";");

            card.getChildren().addAll(num, info, spacer, badge);
            list.getChildren().add(card);
        }
        scroll.setContent(list);
        content.getChildren().add(scroll);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    @FXML
    private void handleOpenVueWeb() {
        File webFile = new File("docs/plan_2d_maquette_3d_bcd.html");
        if (webFile.exists()) {
            MainApp.openDocument(webFile);
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Fichier maquette 3D introuvable dans docs/");
            MainApp.prepareDialog(alert);
            alert.showAndWait();
        }
    }

    /* ── Sélection & Rendu Inspecteur Latéral ─────────────────────────────── */

    public void selectZone(ZoneBcd zone) {
        this.selectedZone = zone;
        render();
        if (zone != null) {
            renderSidebarDetail(zone);
        } else {
            renderSidebarOverview();
        }
    }

    private void renderSidebarOverview() {
        sidebarContent.getChildren().clear();

        Label title = new Label("🧭  Explorer la BCD");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        Label intro = new Label("Cliquez sur une étagère sur le plan 2D ou dans la maquette 3D pour inspecter son contenu et ses livres.");
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        VBox zonesBox = new VBox(6);
        for (ZoneBcd z : planService.getNumberedZones()) {
            Button btn = new Button();
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 7 10; -fx-cursor: hand;");

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label badge = new Label(String.valueOf(z.getNumero()));
            badge.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0F172A; -fx-background-color: " + z.getCategorie().getColorFill() + "; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-radius: 999;");

            VBox textCol = new VBox(1);
            Label nom = new Label(z.getIcon() + " " + z.getNom());
            nom.setStyle("-fx-font-size: 12px; -fx-font-weight: 700; -fx-text-fill: #1E293B;");
            Label sub = new Label(z.getTypeLabel());
            sub.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748B;");
            textCol.getChildren().addAll(nom, sub);

            row.getChildren().addAll(badge, textCol);
            btn.setGraphic(row);
            btn.setOnAction(e -> selectZone(z));
            zonesBox.getChildren().add(btn);
        }

        Label miscTitle = new Label("MOBILIER COMPLÉMENTAIRE");
        miscTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #94A3B8; -fx-padding: 8 0 2 0;");

        FlowPane miscPills = new FlowPane(6, 6);
        for (ZoneBcd m : planService.getMiscFurniture()) {
            Button chip = new Button(m.getNomCourt());
            chip.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 4 8; -fx-background-radius: 6; -fx-cursor: hand;");
            chip.setOnAction(e -> selectZone(m));
            miscPills.getChildren().add(chip);
        }

        sidebarContent.getChildren().addAll(title, intro, new Separator(), zonesBox, miscTitle, miscPills);
    }

    private void renderSidebarDetail(ZoneBcd z) {
        sidebarContent.getChildren().clear();

        Button btnBack = new Button("← Retour à la vue d'ensemble");
        btnBack.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563EB; -fx-font-size: 11.5px; -fx-font-weight: 700; -fx-cursor: hand; -fx-padding: 0;");
        btnBack.setOnAction(e -> selectZone(null));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        if (z.hasNumero()) {
            Label badge = new Label(String.valueOf(z.getNumero()));
            badge.setStyle("-fx-font-size: 18px; -fx-font-weight: 800; -fx-text-fill: #0F172A; -fx-background-color: " + z.getCategorie().getColorFill() + "; -fx-min-width: 42px; -fx-min-height: 42px; -fx-alignment: center; -fx-background-radius: 10; -fx-border-color: " + z.getCategorie().getColorStroke() + "; -fx-border-radius: 10;");
            header.getChildren().add(badge);
        }

        VBox titleCol = new VBox(2);
        Label title = new Label(z.getNom());
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");
        Label sub = new Label(z.getTypeLabel());
        sub.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B;");
        titleCol.getChildren().addAll(title, sub);
        header.getChildren().add(titleCol);

        // Fiche technique
        GridPane techGrid = new GridPane();
        techGrid.setHgap(8);
        techGrid.setVgap(8);
        techGrid.setStyle("-fx-background-color: #F8FAFC; -fx-padding: 10; -fx-background-radius: 8; -fx-border-color: #E2E8F0; -fx-border-radius: 8;");

        VBox b1 = createKpiBox("COULEUR RÉELLE", z.getCouleurReelle());
        VBox b2 = createKpiBox("EMPRISE AU SOL", String.format(Locale.FRENCH, "%.1f m × %.1f m", z.getWidth(), z.getDepth()));
        GridPane.setHgrow(b1, Priority.ALWAYS);
        GridPane.setHgrow(b2, Priority.ALWAYS);
        techGrid.add(b1, 0, 0);
        techGrid.add(b2, 1, 0);

        // Faces et contenus
        VBox facesBox = new VBox(6);
        for (FaceBcd f : z.getFaces()) {
            VBox fCard = new VBox(3);
            fCard.setStyle("-fx-background-color: white; -fx-padding: 8 10; -fx-background-radius: 6; -fx-border-color: #E2E8F0; -fx-border-radius: 6;");
            Label sideLabel = new Label("📍 " + f.side());
            sideLabel.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 800; -fx-text-fill: #1E293B;");
            Label sideContent = new Label(f.content());
            sideContent.setWrapText(true);
            sideContent.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
            fCard.getChildren().addAll(sideLabel, sideContent);
            facesBox.getChildren().add(fCard);
        }

        // Section livres réels SQLite
        VBox booksSection = new VBox(6);
        Label booksHeader = new Label("📚  Livres réels dans cet espace");
        booksHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #0F172A; -fx-padding: 4 0 0 0;");
        booksSection.getChildren().add(booksHeader);

        try {
            String queryTerm = z.hasNumero() ? (z.getNumero() == 2 ? "Astérix" : (z.getNumero() == 8 ? "animaux" : (z.getNumero() == 1 ? "contes" : "roman"))) : "livre";
            List<Livre> sample = livreDao.searchWithCounts(queryTerm);
            if (!sample.isEmpty()) {
                int limit = Math.min(5, sample.size());
                for (int i = 0; i < limit; i++) {
                    Livre l = sample.get(i);
                    HBox bookRow = new HBox(8);
                    bookRow.setAlignment(Pos.CENTER_LEFT);
                    bookRow.setStyle("-fx-background-color: #F8FAFC; -fx-padding: 5 8; -fx-background-radius: 6; -fx-border-color: #E2E8F0; -fx-border-radius: 6;");

                    Label bookIcon = new Label(l.getGenre() != null && l.getGenre().equals("BD") ? "💬" : "📖");
                    bookIcon.setStyle("-fx-font-size: 14px;");

                    VBox bookInfo = new VBox(1);
                    Label bookTitle = new Label(l.getTitre());
                    bookTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #1E293B;");
                    Label bookSub = new Label((l.getAuteur() != null ? l.getAuteur() : "Inconnu") + " • " + computeCote(l));
                    bookSub.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");
                    bookInfo.getChildren().addAll(bookTitle, bookSub);

                    bookRow.getChildren().addAll(bookIcon, bookInfo);
                    booksSection.getChildren().add(bookRow);
                }
            } else {
                Label empty = new Label("Aucun exemplaire en base.");
                empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
                booksSection.getChildren().add(empty);
            }
        } catch (SQLException ex) {
            Label err = new Label("Base locale prête.");
            err.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
            booksSection.getChildren().add(err);
        }

        // Boutons d'action
        Button btnVoirInventaire = new Button("📚 Voir ces livres dans l'inventaire");
        btnVoirInventaire.setMaxWidth(Double.MAX_VALUE);
        btnVoirInventaire.getStyleClass().add("btn-outline");
        btnVoirInventaire.setOnAction(e -> {
            MainApp.selectTab("Inventaire");
            Object ctrl = MainApp.getTabController("Inventaire");
            if (ctrl instanceof InventaireController ic) {
                ic.filterByQuery(z.getNomCourt());
            }
        });

        Button btnBipIci = new Button("⚡ Bip & Ranger un livre ici");
        btnBipIci.setMaxWidth(Double.MAX_VALUE);
        btnBipIci.getStyleClass().add("btn-douchette-indigo");
        btnBipIci.setOnAction(e -> handleOpenAssistantDouchette());

        sidebarContent.getChildren().addAll(
                btnBack,
                header,
                techGrid,
                facesBox,
                new Separator(),
                booksSection,
                btnVoirInventaire,
                btnBipIci
        );
    }

    private VBox createKpiBox(String title, String value) {
        VBox box = new VBox(2);
        Label lblT = new Label(title);
        lblT.setStyle("-fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: #94A3B8;");
        Label lblV = new Label(value);
        lblV.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #1E293B;");
        box.getChildren().addAll(lblT, lblV);
        return box;
    }

    /* ── Moteur de Rendu Graphique (Canvas 2D / 3D) ───────────────────────── */

    private void render() {
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        if (isView3D) {
            render3DIsometric(gc, w, h);
        } else {
            render2DPlan(gc, w, h);
        }
    }

    /**
     * Rendu 2D Architectural (Vue du dessus fidèle au plan SVG de la BCD).
     */
    private void render2DPlan(GraphicsContext gc, double cw, double ch) {
        double roomW = PlanBcdService.ROOM_WIDTH;
        double roomD = PlanBcdService.ROOM_DEPTH;

        // Calcul de l'échelle d'affichage en conservant le ratio
        double margin = 40.0;
        double scaleX = (cw - margin * 2) / roomW;
        double scaleY = (ch - margin * 2) / roomD;
        double scale = Math.min(scaleX, scaleY) * zoomFactor;

        double originX = (cw - roomW * scale) / 2.0 + panOffsetX;
        double originY = (ch - roomD * scale) / 2.0 + panOffsetY;

        // 1. Fond de la salle
        gc.setFill(Color.web("#FFFFFF"));
        gc.fillRect(originX, originY, roomW * scale, roomD * scale);

        // Grille carrelée fine (tous les 1 mètre)
        gc.setStroke(Color.web("#F1F5F9"));
        gc.setLineWidth(1.0);
        for (double x = 1.0; x < roomW; x += 1.0) {
            gc.strokeLine(originX + x * scale, originY, originX + x * scale, originY + roomD * scale);
        }
        for (double z = 1.0; z < roomD; z += 1.0) {
            gc.strokeLine(originX, originY + z * scale, originX + roomW * scale, originY + z * scale);
        }

        // Contour de la salle
        gc.setStroke(Color.web("#334155"));
        gc.setLineWidth(3.0);
        gc.strokeRect(originX, originY, roomW * scale, roomD * scale);

        // 2. Murs et ouvertures architecturales
        // Verrière jaune (Nord : Z=0, X=2.1 à 8.8)
        gc.setStroke(Color.web("#EAB308"));
        gc.setLineWidth(5.0);
        gc.strokeLine(originX + 2.1 * scale, originY, originX + 8.8 * scale, originY);

        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.setFill(Color.web("#A16207"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("VERRIÈRE JAUNE (COULOIR)", originX + 5.45 * scale, originY - 10);

        // Porte d'entrée (Nord : X=1.0 à 2.0)
        gc.setFill(Color.web("#FFFFFF"));
        gc.fillRect(originX + 1.0 * scale, originY - 3, 1.0 * scale, 6);
        gc.setStroke(Color.web("#2563EB"));
        gc.setLineWidth(2.5);
        gc.strokeLine(originX + 1.0 * scale, originY, originX + 1.0 * scale, originY + 0.95 * scale);
        gc.fillText("PORTE", originX + 1.5 * scale, originY - 10);

        // Grandes fenêtres extérieures (Sud : Z=7.5, X=0.4 à 8.6)
        gc.setStroke(Color.web("#38BDF8"));
        gc.setLineWidth(5.0);
        gc.strokeLine(originX + 0.4 * scale, originY + roomD * scale, originX + 8.6 * scale, originY + roomD * scale);
        gc.setFill(Color.web("#0284C7"));
        gc.fillText("GRANDES FENÊTRES EXTÉRIEURES & RADIATEURS", originX + 4.5 * scale, originY + roomD * scale + 18);

        // Tapis bleu décoratif sous les poufs
        gc.setFill(Color.web("#BAE6FD", 0.45));
        gc.setStroke(Color.web("#7DD3FC"));
        gc.setLineWidth(1.0);
        gc.fillRoundRect(originX + 6.5 * scale, originY + 5.6 * scale, 1.6 * scale, 1.7 * scale, 10, 10);
        gc.strokeRoundRect(originX + 6.5 * scale, originY + 5.6 * scale, 1.6 * scale, 1.7 * scale, 10, 10);

        // 3. Dessin des meubles et îlots
        for (ZoneBcd z : planService.getAllZones()) {
            double rx = originX + z.getX1() * scale;
            double ry = originY + z.getZ1() * scale;
            double rw = z.getWidth() * scale;
            double rh = z.getDepth() * scale;

            boolean isSelected = z.equals(selectedZone);
            boolean isHovered = z.equals(hoveredZone);
            boolean isMatched = matchedZoneIds.contains(z.getId());
            boolean isDimmed = !matchedZoneIds.isEmpty() && !isMatched && !isSelected;

            gc.setGlobalAlpha(isDimmed ? 0.28 : 1.0);

            // Couleur principale
            Color fillColor = Color.web(z.getCategorie().getColorFill());
            Color strokeColor = Color.web(z.getCategorie().getColorStroke());

            if ("8".equals(z.getId())) {
                fillColor = Color.web("#BBF7D0"); // Vert clair pour biblio verte
                strokeColor = Color.web("#16A34A");
            }

            gc.setFill(fillColor);
            gc.fillRoundRect(rx, ry, rw, rh, 6, 6);

            // Contour
            if (isSelected) {
                gc.setStroke(Color.web("#1D4ED8"));
                gc.setLineWidth(3.5);
            } else if (isMatched) {
                double pulse = 2.0 + Math.sin(animationTick * 2.0) * 1.5;
                gc.setStroke(Color.web("#F59E0B"));
                gc.setLineWidth(pulse);
            } else if (isHovered) {
                gc.setStroke(Color.web("#0F172A"));
                gc.setLineWidth(2.0);
            } else {
                gc.setStroke(strokeColor);
                gc.setLineWidth(1.5);
            }
            gc.strokeRoundRect(rx, ry, rw, rh, 6, 6);

            // Numéro ou Libellé
            if (btnToggleLabels.isSelected()) {
                double cx = rx + rw / 2.0;
                double cy = ry + rh / 2.0;

                if (z.hasNumero()) {
                    // Pastille blanche avec numéro
                    double badgeR = Math.max(8, Math.min(14, 11 * zoomFactor));
                    gc.setFill(Color.WHITE);
                    gc.fillOval(cx - badgeR, cy - badgeR, badgeR * 2, badgeR * 2);
                    gc.setStroke(strokeColor);
                    gc.setLineWidth(1.2);
                    gc.strokeOval(cx - badgeR, cy - badgeR, badgeR * 2, badgeR * 2);

                    gc.setFill(Color.web("#0F172A"));
                    gc.setFont(Font.font("System", FontWeight.BOLD, Math.max(9, badgeR * 1.1)));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(String.valueOf(z.getNumero()), cx, cy + badgeR * 0.38);
                } else {
                    gc.setFill(Color.web(z.getCategorie().getColorText()));
                    gc.setFont(Font.font("System", FontWeight.BOLD, Math.max(8, 10 * zoomFactor)));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(z.getNomCourt(), cx, cy + 3);
                }
            }

            gc.setGlobalAlpha(1.0);
        }

        // 4. Épingle flottante animée si livre repéré
        if (pinnedZone != null) {
            drawPin(gc, originX + pinnedZone.getCenterX() * scale, originY + pinnedZone.getCenterZ() * scale);
        }
    }

    /**
     * Rendu 3D Isométrique complet avec extrusion, ombrage de profondeur et textures.
     */
    private void render3DIsometric(GraphicsContext gc, double cw, double ch) {
        double roomW = PlanBcdService.ROOM_WIDTH;
        double roomD = PlanBcdService.ROOM_DEPTH;

        // Facteurs de projection isométrique standard (30 degrés)
        double cosAngle = 0.866; // cos(30°)
        double sinAngle = 0.500; // sin(30°)
        double unitScale = 36.0 * zoomFactor;
        double heightScale = 28.0 * zoomFactor;

        double centerX = cw / 2.0 + panOffsetX;
        double centerY = ch / 2.0 + 80 + panOffsetY;

        // Fonction locale de projection (x, z, h) -> (sx, sy)
        java.util.function.BiFunction<Double, Double, double[]> project = (x, z) -> {
            double rx = x - roomW / 2.0;
            double rz = z - roomD / 2.0;
            double sx = centerX + (rx - rz) * cosAngle * unitScale;
            double sy = centerY + (rx + rz) * sinAngle * unitScale;
            return new double[]{sx, sy};
        };

        // 1. Sol isométrique
        double[] p0 = project.apply(0.0, 0.0);
        double[] p1 = project.apply(roomW, 0.0);
        double[] p2 = project.apply(roomW, roomD);
        double[] p3 = project.apply(0.0, roomD);

        gc.setFill(Color.web("#F8FAFC"));
        gc.fillPolygon(new double[]{p0[0], p1[0], p2[0], p3[0]},
                       new double[]{p0[1], p1[1], p2[1], p3[1]}, 4);
        gc.setStroke(Color.web("#94A3B8"));
        gc.setLineWidth(2.0);
        gc.strokePolygon(new double[]{p0[0], p1[0], p2[0], p3[0]},
                         new double[]{p0[1], p1[1], p2[1], p3[1]}, 4);

        // Murs arrière (Nord et Ouest)
        double wallH = 2.4 * heightScale;
        gc.setFill(Color.web("#E2E8F0", 0.65));
        // Mur Nord
        gc.fillPolygon(new double[]{p0[0], p1[0], p1[0], p0[0]},
                       new double[]{p0[1], p1[1], p1[1] - wallH, p0[1] - wallH}, 4);
        // Mur Ouest
        gc.fillPolygon(new double[]{p0[0], p3[0], p3[0], p0[0]},
                       new double[]{p0[1], p3[1], p3[1] - wallH, p0[1] - wallH}, 4);

        // 2. Dessin des meubles triés en profondeur (tri par Z puis X pour bon ordre de tracé)
        List<ZoneBcd> sorted = new ArrayList<>(planService.getAllZones());
        sorted.sort(Comparator.comparingDouble((ZoneBcd z) -> z.getZ1() + z.getX1()));

        for (ZoneBcd z : sorted) {
            boolean isSelected = z.equals(selectedZone);
            boolean isHovered = z.equals(hoveredZone);
            boolean isMatched = matchedZoneIds.contains(z.getId());
            boolean isDimmed = !matchedZoneIds.isEmpty() && !isMatched && !isSelected;

            gc.setGlobalAlpha(isDimmed ? 0.25 : 1.0);

            double hPix = z.getHauteur() * heightScale;
            double[] b0 = project.apply(z.getX1(), z.getZ1());
            double[] b1 = project.apply(z.getX2(), z.getZ1());
            double[] b2 = project.apply(z.getX2(), z.getZ2());
            double[] b3 = project.apply(z.getX1(), z.getZ2());

            Color baseColor = Color.web(z.getCategorie().getColorFill());
            if ("8".equals(z.getId())) baseColor = Color.web("#4ADE80"); // Vert

            // Face latérale Sud (b3 à b2)
            gc.setFill(baseColor.darker());
            gc.fillPolygon(new double[]{b3[0], b2[0], b2[0], b3[0]},
                           new double[]{b3[1], b2[1], b2[1] - hPix, b3[1] - hPix}, 4);

            // Face latérale Est (b2 à b1)
            gc.setFill(baseColor.darker().darker());
            gc.fillPolygon(new double[]{b2[0], b1[0], b1[0], b2[0]},
                           new double[]{b2[1], b1[1], b1[1] - hPix, b2[1] - hPix}, 4);

            // Face supérieure (Top)
            gc.setFill(isSelected ? Color.web("#3B82F6") : baseColor.brighter());
            gc.fillPolygon(new double[]{b0[0], b1[0], b2[0], b3[0]},
                           new double[]{b0[1] - hPix, b1[1] - hPix, b2[1] - hPix, b3[1] - hPix}, 4);

            // Arêtes
            gc.setStroke(isSelected ? Color.web("#1D4ED8") : (isMatched ? Color.web("#F59E0B") : Color.web("#334155")));
            gc.setLineWidth(isSelected || isMatched ? 2.5 : 1.0);
            gc.strokePolygon(new double[]{b0[0], b1[0], b2[0], b3[0]},
                             new double[]{b0[1] - hPix, b1[1] - hPix, b2[1] - hPix, b3[1] - hPix}, 4);

            // Étiquettes flottantes 3D
            if (btnToggleLabels.isSelected() && z.hasNumero()) {
                double topCenterX = (b0[0] + b1[0] + b2[0] + b3[0]) / 4.0;
                double topCenterY = (b0[1] + b1[1] + b2[1] + b3[1]) / 4.0 - hPix - 14;

                double badgeR = Math.max(9, 11 * zoomFactor);
                gc.setFill(isSelected ? Color.web("#1D4ED8") : Color.web("#0F172A", 0.9));
                gc.fillRoundRect(topCenterX - badgeR * 1.5, topCenterY - badgeR, badgeR * 3, badgeR * 2, 8, 8);

                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("System", FontWeight.BOLD, Math.max(9, badgeR * 1.05)));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.valueOf(z.getNumero()), topCenterX, topCenterY + badgeR * 0.38);
            }

            gc.setGlobalAlpha(1.0);
        }

        // Épingle 3D
        if (pinnedZone != null) {
            double[] topPos = project.apply(pinnedZone.getCenterX(), pinnedZone.getCenterZ());
            drawPin(gc, topPos[0], topPos[1] - pinnedZone.getHauteur() * heightScale - 20);
        }
    }

    private void drawPin(GraphicsContext gc, double x, double y) {
        double bounce = Math.sin(animationTick * 3.0) * 6;
        double py = y - 10 + bounce;

        // Ombre au sol
        gc.setFill(Color.web("#0F172A", 0.25));
        gc.fillOval(x - 8, y + 4, 16, 6);

        // Bulle de l'épingle
        gc.setFill(Color.web("#DC2626"));
        gc.setStroke(Color.web("#FEF08A"));
        gc.setLineWidth(2.0);

        String text = pinnedBookTitle != null ? "📍 " + pinnedBookTitle : "📍 Zone " + (pinnedZone.hasNumero() ? pinnedZone.getNumero() : "");
        gc.setFont(Font.font("System", FontWeight.BOLD, 11));
        double textW = 120;
        gc.fillRoundRect(x - textW / 2.0, py - 30, textW, 24, 8, 8);
        gc.strokeRoundRect(x - textW / 2.0, py - 30, textW, 24, 8, 8);

        // Flèche vers le bas
        gc.fillPolygon(new double[]{x - 5, x + 5, x},
                       new double[]{py - 6, py - 6, py}, 3);

        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(text.length() > 18 ? text.substring(0, 16) + "…" : text, x, py - 14);
    }

    /**
     * Détection du meuble cliqué ou survolé.
     */
    private ZoneBcd pickZone(double mx, double my) {
        double roomW = PlanBcdService.ROOM_WIDTH;
        double roomD = PlanBcdService.ROOM_DEPTH;

        if (!isView3D) {
            double margin = 40.0;
            double scaleX = (mapCanvas.getWidth() - margin * 2) / roomW;
            double scaleY = (mapCanvas.getHeight() - margin * 2) / roomD;
            double scale = Math.min(scaleX, scaleY) * zoomFactor;

            double originX = (mapCanvas.getWidth() - roomW * scale) / 2.0 + panOffsetX;
            double originY = (mapCanvas.getHeight() - roomD * scale) / 2.0 + panOffsetY;

            double roomX = (mx - originX) / scale;
            double roomZ = (my - originY) / scale;

            for (ZoneBcd z : planService.getAllZones()) {
                if (z.containsPoint(roomX, roomZ)) {
                    return z;
                }
            }
        } else {
            // Détection en mode isométrique
            double cosAngle = 0.866;
            double sinAngle = 0.500;
            double unitScale = 36.0 * zoomFactor;
            double heightScale = 28.0 * zoomFactor;
            double centerX = mapCanvas.getWidth() / 2.0 + panOffsetX;
            double centerY = mapCanvas.getHeight() / 2.0 + 80 + panOffsetY;

            List<ZoneBcd> sorted = new ArrayList<>(planService.getAllZones());
            sorted.sort((a, b) -> Double.compare(b.getZ1() + b.getX1(), a.getZ1() + a.getX1()));

            for (ZoneBcd z : sorted) {
                double rx = z.getCenterX() - roomW / 2.0;
                double rz = z.getCenterZ() - roomD / 2.0;
                double sx = centerX + (rx - rz) * cosAngle * unitScale;
                double sy = centerY + (rx + rz) * sinAngle * unitScale - z.getHauteur() * heightScale / 2.0;
                double dist = Math.hypot(mx - sx, my - sy);
                if (dist < 26 * zoomFactor) {
                    return z;
                }
            }
        }
        return null;
    }

    /**
     * Calcule la cote indicative d'un livre pour affichage sur le plan et la douchette.
     */
    public static String computeCote(Livre l) {
        if (l == null) return "S.C.";
        String genre = l.getGenre() != null ? l.getGenre().trim() : "";
        String dewey = l.getDewey() != null ? l.getDewey().trim() : "";
        String auteur = l.getAuteur() != null ? l.getAuteur().trim().toUpperCase(Locale.FRENCH) : "";
        String trigramme = auteur.length() >= 3 ? auteur.substring(0, 3) : auteur;

        if (!dewey.isEmpty()) {
            return dewey + (!trigramme.isEmpty() ? " " + trigramme : "");
        }
        if ("BD".equalsIgnoreCase(genre)) {
            return "BD" + (!trigramme.isEmpty() ? " " + trigramme : "");
        }
        if ("Roman".equalsIgnoreCase(genre)) {
            return "R " + (!trigramme.isEmpty() ? trigramme : "");
        }
        if ("Album".equalsIgnoreCase(genre)) {
            return "A " + (!trigramme.isEmpty() ? trigramme : "");
        }
        if ("Conte".equalsIgnoreCase(genre)) {
            return "C " + (!trigramme.isEmpty() ? trigramme : "");
        }
        if ("Revue / Magazine".equalsIgnoreCase(genre)) {
            return "REV";
        }
        return (!genre.isEmpty() ? genre.substring(0, Math.min(3, genre.length())).toUpperCase() : "LIV")
                + (!trigramme.isEmpty() ? " " + trigramme : "");
    }
}
