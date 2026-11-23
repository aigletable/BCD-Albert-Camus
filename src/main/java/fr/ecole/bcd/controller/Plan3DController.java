package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.ZoneBcd;
import fr.ecole.bcd.model.ZoneBcd.CategorieMobilier;
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
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * Contrôleur de l'onglet Plan 3D de la BCD &amp; Emplacement des Livres.
 *
 * <p>Design 100% fidèle au prototype web (blueprint CAD sombre, projection 3D isométrique
 * avec rotation 360°, élévation au survol, livres colorés sur étagères, boussole dynamique,
 * et fiche d'inspection détaillée avec jauge de capacité et livres réels SQLite).</p>
 *
 * @author JC ART
 * @since 2026-11-23
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

    @FXML private Button btnView3D;
    @FXML private Button btnView2D;
    @FXML private Button btnZoomIn;
    @FXML private Button btnZoomOut;
    @FXML private ToggleButton btnToggleLabels;
    @FXML private Button btnToggleAmbiance;

    @FXML private Canvas compassCanvas;
    @FXML private Label lblOrientation;

    @FXML private VBox tooltipBox;
    @FXML private Label tooltipTitle;
    @FXML private Label tooltipSub;
    @FXML private Label tooltipHint;

    @FXML private VBox sidebarCard;
    @FXML private VBox sidebarContent;

    private final PlanBcdService planService = new PlanBcdService();
    private final LivreDao livreDao = new LivreDao();

    /** Angles de caméra 3D (360° yaw, pitch, zoom) - Calqués 1:1 sur le prototype web */
    private double cameraYaw = -Math.PI / 4.0;      // -45° (vue isométrique classique exacte)
    private double cameraPitch = Math.PI / 5.2;    // ~ 34.6° d'inclinaison exacte
    private double cameraZoom = 1.0;
    private double panOffsetX = 0.0;
    private double panOffsetY = -15.0;

    private double targetYaw = -Math.PI / 4.0;
    private double targetPitch = Math.PI / 5.2;
    private double targetZoom = 1.0;

    /** Mode de vue : Vue 3D Isométrique activée par défaut ! */
    private boolean isView3D = true;
    private boolean isWarmAmbiance = false;

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

    private double dragStartX;
    private double dragStartY;
    private boolean isDragging = false;

    /** Animation douce pour la rotation, le survol et le pulse doré */
    private double animationTick = 0.0;
    private AnimationTimer animTimer;

    @FXML
    public void initialize() {
        // Redimensionnement réactif du Canvas
        canvasContainer.widthProperty().addListener((obs, oldW, newW) -> {
            double w = newW.doubleValue();
            if (w > 0 && Math.abs(mapCanvas.getWidth() - w) > 0.5) {
                mapCanvas.setWidth(w);
                render();
            }
        });
        canvasContainer.heightProperty().addListener((obs, oldH, newH) -> {
            double h = newH.doubleValue();
            if (h > 0 && Math.abs(mapCanvas.getHeight() - h) > 0.5) {
                mapCanvas.setHeight(h);
                render();
            }
        });

        setupMouseInteractions();
        setupSearchInteractions();

        // Boucle d'animation fluide 60fps pour la décélération de caméra et les effets
        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                animationTick += 0.04;
                boolean needRender = false;

                if (Math.abs(targetYaw - cameraYaw) > 0.001) {
                    cameraYaw += (targetYaw - cameraYaw) * 0.22;
                    needRender = true;
                }
                if (Math.abs(targetPitch - cameraPitch) > 0.001) {
                    cameraPitch += (targetPitch - cameraPitch) * 0.22;
                    needRender = true;
                }
                if (Math.abs(targetZoom - cameraZoom) > 0.001) {
                    cameraZoom += (targetZoom - cameraZoom) * 0.22;
                    needRender = true;
                }

                if (pinnedZone != null || !matchedZoneIds.isEmpty()) {
                    needRender = true;
                }

                if (needRender) {
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
     * Rechargement des données et actualisation du rendu (appelé par MainApp lors du changement d'onglet).
     */
    public void loadData() {
        render();
    }

    /* ── Interactions Souris (Rotation 360°, Zoom, Survol, Clic) ─────────── */

    private void setupMouseInteractions() {
        mapCanvas.setOnMousePressed(e -> {
            if (e.isPrimaryButtonDown()) {
                isDragging = true;
                dragStartX = e.getX();
                dragStartY = e.getY();
                mapCanvas.setCursor(Cursor.CLOSED_HAND);
            }
        });

        mapCanvas.setOnMouseDragged(e -> {
            if (isDragging) {
                double dx = e.getX() - dragStartX;
                double dy = e.getY() - dragStartY;
                if (isView3D) {
                    targetYaw += dx * 0.008;
                    targetPitch = Math.max(0.18, Math.min(Math.PI / 2.05, targetPitch + dy * 0.006));
                } else {
                    panOffsetX += dx;
                    panOffsetY += dy;
                }
                dragStartX = e.getX();
                dragStartY = e.getY();
                render();
            }
        });

        mapCanvas.setOnMouseReleased(e -> {
            isDragging = false;
            mapCanvas.setCursor(Cursor.DEFAULT);
        });

        mapCanvas.setOnScroll(e -> {
            double factor = e.getDeltaY() < 0 ? 0.90 : 1.10;
            targetZoom = Math.max(0.45, Math.min(3.2, targetZoom * factor));
            render();
        });

        mapCanvas.setOnMouseMoved(e -> {
            if (!isDragging) {
                checkHover(e.getX(), e.getY());
            }
        });

        mapCanvas.setOnMouseClicked(e -> {
            if (e.isStillSincePress()) {
                ZoneBcd hit = pickZone(e.getX(), e.getY());
                selectZone(hit);
                if (hit != null) {
                    SoundService.playSuccess();
                }
            }
        });
    }

    private ZoneBcd pickZone(double mx, double my) {
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();
        if (w <= 0 || h <= 0) return null;

        ZoneBcd bestHit = null;
        double bestDist = Double.MAX_VALUE;

        for (ZoneBcd z : planService.getAllZones()) {
            Point3D center = project3d(z.getCenterX(), z.getCenterZ(), z.getHauteur() / 2.0, w, h);
            double dist = Math.hypot(mx - center.sx, my - center.sy);
            double boundRadius = Math.max(18.0, Math.max(z.getWidth(), z.getDepth()) * 14.0 * cameraZoom);
            if (dist < boundRadius && dist < bestDist) {
                bestDist = dist;
                bestHit = z;
            }
        }
        return bestHit;
    }

    private void checkHover(double mx, double my) {
        ZoneBcd hit = pickZone(mx, my);
        if (!Objects.equals(hit, hoveredZone)) {
            hoveredZone = hit;
            if (hoveredZone != null) {
                String title = (hoveredZone.hasNumero() ? "Zone " + hoveredZone.getNumero() + " • " : "") + hoveredZone.getNomCourt();
                tooltipTitle.setText(hoveredZone.getIcon() + "  " + title);
                tooltipSub.setText(hoveredZone.getTypeLabel() + " • " + hoveredZone.getCotePrefix());
                tooltipBox.setVisible(true);
                tooltipBox.setManaged(true);
                updateTooltipPosition(mx, my);
                mapCanvas.setCursor(Cursor.HAND);
            } else {
                hideTooltip();
                mapCanvas.setCursor(Cursor.DEFAULT);
            }
            render();
        } else if (hoveredZone != null) {
            updateTooltipPosition(mx, my);
        }
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
        mapSearchField.textProperty().addListener((obs, oldV, newV) -> handleSearch(newV));
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
            lblSearchStatus.setStyle("-fx-text-fill: #FCA5A5; -fx-background-color: rgba(127, 29, 29, 0.5);");
        } else {
            lblSearchStatus.setText("✨ " + matchedZoneIds.size() + " zone(s) surlignée(s)");
            lblSearchStatus.setStyle("-fx-text-fill: #86EFAC; -fx-background-color: rgba(6, 78, 59, 0.5);");
        }

        render();
    }

    @FXML
    private void handleClearSearch() {
        mapSearchField.clear();
        mapSearchField.requestFocus();
    }

    /* ── Filtres Pilules ─────────────────────────────────────────────────── */

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
                    case "shelves" -> z.getCategorie() == CategorieMobilier.SHELF && (z.getId().equals("1") || z.getId().equals("3") || z.getId().equals("8") || z.getId().equals("11"));
                    case "ilots" -> z.getId().equals("4") || z.getId().equals("6");
                    case "bins" -> z.getCategorie() == CategorieMobilier.BIN;
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

    /* ── Contrôles Caméra (Toolbar Flottante 1:1 Prototype Web) ──────────── */

    @FXML
    private void handleSetView3D() {
        isView3D = true;
        targetYaw = -Math.PI / 4.0;
        targetPitch = Math.PI / 5.2;
        targetZoom = 1.0;
        panOffsetX = 0.0;
        panOffsetY = -15.0;

        btnView3D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView3D.getStyleClass().add("btn-view-active");
        btnView2D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView2D.getStyleClass().add("btn-view-inactive");
        render();
    }

    @FXML
    private void handleSetView2D() {
        isView3D = false;
        panOffsetX = 0.0;
        panOffsetY = 0.0;
        targetZoom = 1.0;

        btnView2D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView2D.getStyleClass().add("btn-view-active");
        btnView3D.getStyleClass().removeAll("btn-view-active", "btn-view-inactive");
        btnView3D.getStyleClass().add("btn-view-inactive");
        render();
    }

    @FXML
    private void handleZoomIn() {
        targetZoom = Math.min(3.2, targetZoom * 1.25);
        render();
    }

    @FXML
    private void handleZoomOut() {
        targetZoom = Math.max(0.45, targetZoom * 0.8);
        render();
    }

    @FXML
    private void handleResetView() {
        if (isView3D) {
            targetYaw = -Math.PI / 4.0;
            targetPitch = Math.PI / 5.2;
            targetZoom = 1.0;
            panOffsetY = -15.0;
        } else {
            targetZoom = 1.0;
            panOffsetY = 0.0;
        }
        panOffsetX = 0.0;
        render();
    }

    @FXML
    private void handleToggleLabels() {
        btnToggleLabels.setText(btnToggleLabels.isSelected() ? "🏷️ Étiquettes : OUI" : "🏷️ Étiquettes : NON");
        render();
    }

    @FXML
    private void handleToggleAmbiance() {
        isWarmAmbiance = !isWarmAmbiance;
        btnToggleAmbiance.setText(isWarmAmbiance ? "💡 Ambre" : "💡 Jour");
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

    /* ── Sélection & Rendu Inspecteur Latéral 1:1 Prototype Web ──────────── */

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

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Implantation de la BCD Camus Pasteur");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");
        Label countBadge = new Label("11 ZONES");
        countBadge.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 800; -fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-padding: 1 6; -fx-background-radius: 999; -fx-border-color: #FCD34D; -fx-border-radius: 999;");
        topRow.getChildren().addAll(title, countBadge);

        Label intro = new Label("Cliquez sur un meuble dans le plan 3D ou dans la liste pour voir les livres rangés.");
        intro.setWrapText(true);
        intro.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");

        VBox zonesBox = new VBox(6);
        for (ZoneBcd z : planService.getNumberedZones()) {
            Button btn = new Button();
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 8 10; -fx-cursor: hand;");

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);

            Label badge = new Label(String.valueOf(z.getNumero()));
            badge.setStyle("-fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0F172A; -fx-background-color: " + z.getCategorie().getColorFill() + "; -fx-min-width: 24px; -fx-min-height: 24px; -fx-alignment: center; -fx-background-radius: 999;");

            VBox textCol = new VBox(1);
            Label nom = new Label(z.getIcon() + "  " + z.getNomCourt());
            nom.setStyle("-fx-font-size: 12.5px; -fx-font-weight: 700; -fx-text-fill: #1E293B;");
            Label sub = new Label(z.getTypeLabel());
            sub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
            textCol.getChildren().addAll(nom, sub);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label arrow = new Label("Voir →");
            arrow.setStyle("-fx-font-size: 11px; -fx-text-fill: #1A66FF; -fx-font-weight: 700;");

            row.getChildren().addAll(badge, textCol, spacer, arrow);
            btn.setGraphic(row);
            btn.setOnAction(e -> selectZone(z));
            zonesBox.getChildren().add(btn);
        }

        Label miscTitle = new Label("MOBILIER COMPLÉMENTAIRE");
        miscTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: 800; -fx-text-fill: #94A3B8; -fx-padding: 8 0 2 0;");

        FlowPane miscPills = new FlowPane(6, 6);
        for (ZoneBcd m : planService.getMiscFurniture()) {
            Button chip = new Button(m.getIcon() + " " + m.getNomCourt());
            chip.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #475569; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 4 8; -fx-background-radius: 6; -fx-cursor: hand;");
            chip.setOnAction(e -> selectZone(m));
            miscPills.getChildren().add(chip);
        }

        sidebarContent.getChildren().addAll(topRow, intro, new Separator(), zonesBox, miscTitle, miscPills);
    }

    private void renderSidebarDetail(ZoneBcd z) {
        sidebarContent.getChildren().clear();

        Button btnBack = new Button("← Implantation de la BCD Camus Pasteur");
        btnBack.setStyle("-fx-background-color: transparent; -fx-text-fill: #1A66FF; -fx-font-size: 11.5px; -fx-font-weight: 700; -fx-cursor: hand; -fx-padding: 0;");
        btnBack.setOnAction(e -> selectZone(null));

        // 1. En-tête Meuble (Icon 28px + Titre + Badges)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        Label iconLbl = new Label(z.getIcon());
        iconLbl.setStyle("-fx-font-size: 28px;");

        VBox titleCol = new VBox(2);
        Label title = new Label(z.hasNumero() ? "Zone " + z.getNumero() + " • " + z.getNom() : z.getNom());
        title.setWrapText(true);
        title.setStyle("-fx-font-size: 14.5px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");
        Label sub = new Label(z.getTypeLabel());
        sub.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B;");
        titleCol.getChildren().addAll(title, sub);

        header.getChildren().addAll(iconLbl, titleCol);

        // Badges pilules (Catégorie, Cote, PROTOTYPE)
        FlowPane badgesRow = new FlowPane(6, 6);
        Label catBadge = new Label(z.getCategorie() == CategorieMobilier.SHELF ? "🟩 Étagère / Îlot" : (z.getCategorie() == CategorieMobilier.BIN ? "🟥 Bac à livres" : "⬜ Mobilier"));
        catBadge.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 700; -fx-padding: 3 8; -fx-background-radius: 4; -fx-background-color: " + (z.getCategorie() == CategorieMobilier.SHELF ? "#F0FDF4; -fx-text-fill: #15803D;" : (z.getCategorie() == CategorieMobilier.BIN ? "#FEF2F2; -fx-text-fill: #B91C1C;" : "#F1F5F9; -fx-text-fill: #334155;")));

        Label coteBadge = new Label("Cote : " + z.getCotePrefix());
        coteBadge.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 600; -fx-padding: 3 8; -fx-background-radius: 4; -fx-background-color: #F8FAFC; -fx-text-fill: #475569; -fx-border-color: #E2E8F0; -fx-border-radius: 4;");

        Label protoBadge = new Label("PROTOTYPE");
        protoBadge.setStyle("-fx-font-size: 9.5px; -fx-font-weight: 800; -fx-background-color: #FEF3C7; -fx-text-fill: #92400E; -fx-padding: 2 6; -fx-background-radius: 4; -fx-border-color: #FCD34D; -fx-border-radius: 4;");

        badgesRow.getChildren().addAll(catBadge, coteBadge, protoBadge);

        // 2. Jauge de capacité (calquée 1:1 sur le prototype web)
        int estimatedCapacity = z.getCapacite() > 0 ? z.getCapacite() : 120;
        int estimatedBooks = Math.min(estimatedCapacity, (int)(estimatedCapacity * 0.72));
        int pct = Math.min(100, (int) Math.round(((double) estimatedBooks / estimatedCapacity) * 100));

        VBox gaugeCard = new VBox(6);
        gaugeCard.getStyleClass().add("shelf-capacity-gauge");

        HBox gaugeHeader = new HBox();
        Label lblGaugeTitle = new Label("Capacité estimée");
        lblGaugeTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");
        Region gSpacer = new Region();
        HBox.setHgrow(gSpacer, Priority.ALWAYS);
        Label lblGaugeVal = new Label(estimatedBooks + " / " + estimatedCapacity + " ouvrages (" + pct + "%)");
        lblGaugeVal.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #475569;");
        gaugeHeader.getChildren().addAll(lblGaugeTitle, gSpacer, lblGaugeVal);

        ProgressBar pBar = new ProgressBar((double) pct / 100.0);
        pBar.setMaxWidth(Double.MAX_VALUE);
        pBar.setPrefHeight(7);
        String barColor = pct > 85 ? "#EF4444" : (pct > 65 ? "#F59E0B" : "#10B981");
        pBar.setStyle("-fx-accent: " + barColor + ";");

        gaugeCard.getChildren().addAll(gaugeHeader, pBar);

        // 3. Système de Cote & Rangement
        VBox deweyBox = new VBox(4);
        deweyBox.setStyle("-fx-background-color: #F8FAFC; -fx-border-color: #E2E8F0; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 10;");
        Label lblDeweyT = new Label("🏷️ Système de Cote & Rangement :");
        lblDeweyT.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");

        String faceDesc = z.getFaces().stream().map(FaceBcd::content).reduce((a, b) -> a + " • " + b).orElse(z.getTypeLabel());
        Label lblDeweySub = new Label(faceDesc);
        lblDeweySub.setWrapText(true);
        lblDeweySub.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");

        Label lblCoteEx = new Label("Exemple : " + z.getCotePrefix());
        lblCoteEx.setStyle("-fx-font-size: 10.5px; -fx-font-weight: 700; -fx-text-fill: #1A66FF;");

        deweyBox.getChildren().addAll(lblDeweyT, lblDeweySub, lblCoteEx);

        // 4. Boutons d'action rapides
        HBox actionsRow = new HBox(6);
        Button btnRanger = new Button("⚡ Bip & Ranger ici");
        btnRanger.getStyleClass().add("btn-douchette-indigo");
        btnRanger.setOnAction(e -> handleOpenAssistantDouchette());

        Button btnPrintLabel = new Button("🏷️ Étiquette meuble");
        btnPrintLabel.getStyleClass().add("btn-outline");
        btnPrintLabel.setOnAction(e -> handleOpenEtiquettesRayon());

        Button btnInventaire = new Button("📚 Inventaire");
        btnInventaire.getStyleClass().add("btn-outline");
        btnInventaire.setOnAction(e -> {
            MainApp.selectTab("Inventaire");
            Object ctrl = MainApp.getTabController("Inventaire");
            if (ctrl instanceof InventaireController ic) {
                ic.filterByQuery(z.getNomCourt());
            }
        });
        actionsRow.getChildren().addAll(btnRanger, btnPrintLabel, btnInventaire);

        // 5. Exemples d'ouvrages répertoriés dans ce rayon (issus de SQLite)
        VBox booksSection = new VBox(6);
        Label booksHeader = new Label("Exemples d'ouvrages répertoriés dans ce rayon :");
        booksHeader.setStyle("-fx-font-size: 12px; -fx-font-weight: 800; -fx-text-fill: #0F172A; -fx-padding: 6 0 2 0;");
        booksSection.getChildren().add(booksHeader);

        try {
            String q = z.hasNumero() ? (z.getNumero() == 2 ? "Astérix" : (z.getNumero() == 8 ? "sciences" : (z.getNumero() == 1 ? "conte" : "livre"))) : "livre";
            List<Livre> sample = livreDao.searchWithCounts(q);
            if (!sample.isEmpty()) {
                int limit = Math.min(4, sample.size());
                for (int i = 0; i < limit; i++) {
                    Livre l = sample.get(i);
                    HBox bookCard = new HBox(8);
                    bookCard.setAlignment(Pos.CENTER_LEFT);
                    bookCard.getStyleClass().add("shelf-book-card");

                    Label bEmoji = new Label(l.getGenre() != null && l.getGenre().equals("BD") ? "💬" : (l.getGenre() != null && l.getGenre().equals("Documentaire") ? "🔬" : "📖"));
                    bEmoji.setStyle("-fx-font-size: 15px; -fx-padding: 2;");

                    VBox bInfo = new VBox(2);
                    HBox.setHgrow(bInfo, Priority.ALWAYS);
                    Label bTitle = new Label(l.getTitre());
                    bTitle.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 700; -fx-text-fill: #0F172A;");
                    Label bMeta = new Label((l.getAuteur() != null ? l.getAuteur() : "Collectif") + " • " + computeCote(l));
                    bMeta.setStyle("-fx-font-size: 10.5px; -fx-text-fill: #64748B;");
                    bInfo.getChildren().addAll(bTitle, bMeta);

                    Button btnLoc = new Button("Localiser");
                    btnLoc.setStyle("-fx-font-size: 10px; -fx-padding: 3 6; -fx-background-radius: 4; -fx-cursor: hand; -fx-background-color: #EFF6FF; -fx-text-fill: #1D4ED8; -fx-border-color: #BFDBFE; -fx-border-radius: 4;");
                    btnLoc.setOnAction(e -> {
                        pinnedZone = z;
                        pinnedBookTitle = l.getTitre();
                        pinnedBookCote = computeCote(l);
                        SoundService.playSuccess();
                        render();
                    });

                    bookCard.getChildren().addAll(bEmoji, bInfo, btnLoc);
                    booksSection.getChildren().add(bookCard);
                }
            } else {
                Label empty = new Label("Espace de consultation ou étagère en cours de remplissage.");
                empty.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-font-style: italic;");
                booksSection.getChildren().add(empty);
            }
        } catch (SQLException ex) {
            Label err = new Label("Base SQLite locale prête.");
            err.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
            booksSection.getChildren().add(err);
        }

        sidebarContent.getChildren().addAll(
                btnBack,
                header,
                badgesRow,
                gaugeCard,
                deweyBox,
                actionsRow,
                new Separator(),
                booksSection
        );
    }

    /* ── Moteur de Rendu 3D Isométrique (1:1 Prototype Web) ─────────────── */

    private static class Point3D {
        final double sx, sy, depth;
        Point3D(double sx, double sy, double depth) {
            this.sx = sx;
            this.sy = sy;
            this.depth = depth;
        }
    }

    private Point3D project3d(double x, double y, double z, double cw, double ch) {
        double dx = x - PlanBcdService.ROOM_WIDTH / 2.0;
        double dy = y - PlanBcdService.ROOM_DEPTH / 2.0;
        double dz = z;

        double cosY = Math.cos(cameraYaw);
        double sinY = Math.sin(cameraYaw);
        double x1 = dx * cosY - dy * sinY;
        double y1 = dx * sinY + dy * cosY;

        double cosP = Math.cos(cameraPitch);
        double sinP = Math.sin(cameraPitch);
        double y2 = y1 * cosP - dz * sinP;
        double z2 = y1 * sinP + dz * cosP;

        double scale = (Math.min(cw, ch) / 11.2) * cameraZoom;
        double sx = cw / 2.0 + panOffsetX + x1 * scale;
        double sy = ch / 2.0 + panOffsetY + y2 * scale;

        return new Point3D(sx, sy, z2);
    }

    private double getPolygonSignedArea(Point3D[] pts) {
        double area = 0;
        for (int i = 0; i < pts.length; i++) {
            int j = (i + 1) % pts.length;
            area += pts[i].sx * pts[j].sy - pts[j].sx * pts[i].sy;
        }
        return area / 2.0;
    }

    private Color shadeColor(Color c, double percent) {
        double factor = percent / 100.0;
        if (factor >= 0) {
            return c.interpolate(Color.WHITE, factor);
        } else {
            return c.interpolate(Color.BLACK, -factor);
        }
    }

    private void render() {
        double w = mapCanvas.getWidth();
        double h = mapCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = mapCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // 1. Fond Radial Gradient Blueprint CAD Sombre (1:1 Prototype Web)
        RadialGradient bg = new RadialGradient(
                0, 0,
                w / 2.0, h * 0.35,
                Math.max(w, h) * 0.85,
                false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, isWarmAmbiance ? Color.web("#371B10") : Color.web("#1E293B")),
                new Stop(1.0, isWarmAmbiance ? Color.web("#1A0B05") : Color.web("#0F172A"))
        );
        gc.setFill(bg);
        gc.fillRect(0, 0, w, h);

        updateCompassNeedle();

        if (isView3D) {
            render3DIsometric(gc, w, h);
        } else {
            render2DPlan(gc, w, h);
        }
    }

    private void updateCompassNeedle() {
        if (compassCanvas == null || lblOrientation == null) return;
        GraphicsContext gc = compassCanvas.getGraphicsContext2D();
        double cw = compassCanvas.getWidth();
        double ch = compassCanvas.getHeight();
        gc.clearRect(0, 0, cw, ch);

        double deg = (cameraYaw * 180.0 / Math.PI) + 45.0;
        double rad = Math.toRadians(deg);

        // Cadran circulaire
        gc.setStroke(Color.rgb(255, 255, 255, 0.35));
        gc.setLineWidth(1.2);
        gc.strokeOval(1, 1, cw - 2, ch - 2);

        double cx = cw / 2.0;
        double cy = ch / 2.0;
        double len = cw * 0.38;

        double nx = cx + Math.sin(rad) * len;
        double ny = cy - Math.cos(rad) * len;
        double sx = cx - Math.sin(rad) * len;
        double sy = cy + Math.cos(rad) * len;

        // Pointe Nord (Rouge)
        gc.setStroke(Color.web("#EF4444"));
        gc.setLineWidth(2.4);
        gc.strokeLine(cx, cy, nx, ny);

        // Pointe Sud (Gris/Blanc)
        gc.setStroke(Color.web("#94A3B8"));
        gc.setLineWidth(2.4);
        gc.strokeLine(cx, cy, sx, sy);

        double normDeg = (deg % 360.0 + 360.0) % 360.0;
        if (normDeg >= 315 || normDeg < 45) {
            lblOrientation.setText("Vue Nord (Affiches / Verrière)");
        } else if (normDeg >= 45 && normDeg < 135) {
            lblOrientation.setText("Vue Ouest (Porte d'entrée)");
        } else if (normDeg >= 135 && normDeg < 225) {
            lblOrientation.setText("Vue Sud (Grandes Fenêtres)");
        } else {
            lblOrientation.setText("Vue Est (Linéaire Mural)");
        }
    }

    private void render3DIsometric(GraphicsContext gc, double cw, double ch) {
        drawFloorParquet(gc, cw, ch);
        drawArchitectureFeatures(gc, cw, ch);

        // Tri des meubles en profondeur (depth-sorting physique)
        List<ZoneBcd> sorted = new ArrayList<>(planService.getAllZones());
        sorted.sort((a, b) -> {
            Point3D pa = project3d(a.getCenterX(), a.getCenterZ(), a.getHauteur() / 2.0, cw, ch);
            Point3D pb = project3d(b.getCenterX(), b.getCenterZ(), b.getHauteur() / 2.0, cw, ch);
            return Double.compare(pa.depth, pb.depth);
        });

        for (ZoneBcd z : sorted) {
            boolean isSelected = z.equals(selectedZone);
            boolean isHovered = z.equals(hoveredZone);
            boolean isMatched = matchedZoneIds.contains(z.getId());
            boolean isDimmed = !matchedZoneIds.isEmpty() && !isMatched && !isSelected;

            gc.setGlobalAlpha(isDimmed ? 0.25 : 1.0);
            drawFurniture3D(gc, z, cw, ch, isHovered, isSelected);
            gc.setGlobalAlpha(1.0);
        }

        // Étiquettes badges au-dessus des meubles
        if (btnToggleLabels.isSelected()) {
            for (ZoneBcd z : sorted) {
                boolean isSelected = z.equals(selectedZone);
                boolean isMatched = matchedZoneIds.contains(z.getId());
                boolean isDimmed = !matchedZoneIds.isEmpty() && !isMatched && !isSelected;
                if (!isDimmed) {
                    drawShelfLabelBadge(gc, z, cw, ch, isSelected);
                }
            }
        }

        if (pinnedZone != null) {
            drawHighlightedPin(gc, cw, ch);
        }
    }

    private void drawFloorParquet(GraphicsContext gc, double cw, double ch) {
        double rw = PlanBcdService.ROOM_WIDTH;
        double rd = PlanBcdService.ROOM_DEPTH;

        Point3D p0 = project3d(0, 0, 0, cw, ch);
        Point3D p1 = project3d(rw, 0, 0, cw, ch);
        Point3D p2 = project3d(rw, rd, 0, cw, ch);
        Point3D p3 = project3d(0, rd, 0, cw, ch);

        gc.setFill(isWarmAmbiance ? Color.web("#B45309") : Color.web("#2A374A"));
        gc.fillPolygon(new double[]{p0.sx, p1.sx, p2.sx, p3.sx},
                       new double[]{p0.sy, p1.sy, p2.sy, p3.sy}, 4);

        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(1.5);
        gc.strokePolygon(new double[]{p0.sx, p1.sx, p2.sx, p3.sx},
                         new double[]{p0.sy, p1.sy, p2.sy, p3.sy}, 4);

        // Lignes fines du sol
        gc.setStroke(isWarmAmbiance ? Color.rgb(255, 255, 255, 0.08) : Color.rgb(255, 255, 255, 0.05));
        gc.setLineWidth(1.0);
        for (double x = 1.0; x < rw; x += 1.0) {
            Point3D a = project3d(x, 0, 0, cw, ch);
            Point3D b = project3d(x, rd, 0, cw, ch);
            gc.strokeLine(a.sx, a.sy, b.sx, b.sy);
        }
        for (double z = 1.0; z < rd; z += 1.0) {
            Point3D a = project3d(0, z, 0, cw, ch);
            Point3D b = project3d(rw, z, 0, cw, ch);
            gc.strokeLine(a.sx, a.sy, b.sx, b.sy);
        }

        // Tapis d'accueil bleu porte d'entrée
        Point3D m0 = project3d(1.0, 0.05, 0.01, cw, ch);
        Point3D m1 = project3d(2.1, 0.05, 0.01, cw, ch);
        Point3D m2 = project3d(2.1, 1.0, 0.01, cw, ch);
        Point3D m3 = project3d(1.0, 1.0, 0.01, cw, ch);
        gc.setFill(Color.web("#2563EB"));
        gc.fillPolygon(new double[]{m0.sx, m1.sx, m2.sx, m3.sx},
                       new double[]{m0.sy, m1.sy, m2.sy, m3.sy}, 4);
        gc.setStroke(Color.web("#93C5FD"));
        gc.setLineWidth(1.2);
        gc.strokePolygon(new double[]{m0.sx, m1.sx, m2.sx, m3.sx},
                         new double[]{m0.sy, m1.sy, m2.sy, m3.sy}, 4);
    }

    private void drawArchitectureFeatures(GraphicsContext gc, double cw, double ch) {
        double rw = PlanBcdService.ROOM_WIDTH;
        double rd = PlanBcdService.ROOM_DEPTH;
        double wallH = 1.4;

        // Mur Nord : Verrière jaune
        drawBoxSegment(gc, 2.1, -0.15, 0, 6.7, 0.15, wallH, Color.web("#F59E0B"), Color.web("#D97706"), cw, ch);

        // Mur Ouest
        drawBoxSegment(gc, -0.15, 0, 0, 0.15, rd, wallH, Color.web("#64748B"), Color.web("#475569"), cw, ch);

        // Mur Sud : Grandes fenêtres extérieures (vitrage cyan)
        drawBoxSegment(gc, 0, rd, 0, rw, 0.15, 0.7, Color.web("#38BDF8"), Color.web("#0284C7"), cw, ch);

        // Mur Est
        drawBoxSegment(gc, rw, 0, 0, 0.15, rd, wallH, Color.web("#64748B"), Color.web("#475569"), cw, ch);
    }

    private void drawBoxSegment(GraphicsContext gc, double x, double y, double z,
                                double bw, double bd, double bh,
                                Color topColor, Color sideColor, double cw, double ch) {
        Point3D p001 = project3d(x, y, z + bh, cw, ch);
        Point3D p101 = project3d(x + bw, y, z + bh, cw, ch);
        Point3D p111 = project3d(x + bw, y + bd, z + bh, cw, ch);
        Point3D p011 = project3d(x, y + bd, z + bh, cw, ch);

        Point3D p000 = project3d(x, y, z, cw, ch);
        Point3D p100 = project3d(x + bw, y, z, cw, ch);
        Point3D p110 = project3d(x + bw, y + bd, z, cw, ch);
        Point3D p010 = project3d(x, y + bd, z, cw, ch);

        Point3D[][] faces = {
                { p001, p101, p111, p011 }, // top
                { p010, p011, p111, p110 }, // front (South)
                { p100, p101, p001, p000 }, // back (North)
                { p110, p111, p101, p100 }, // right (East)
                { p000, p001, p011, p010 }  // left (West)
        };

        Color[] colors = {
                topColor,
                sideColor,
                shadeColor(sideColor, -25),
                shadeColor(sideColor, -12),
                shadeColor(sideColor, 8)
        };

        for (int i = 0; i < faces.length; i++) {
            Point3D[] pts = faces[i];
            if (getPolygonSignedArea(pts) > 0) {
                gc.beginPath();
                gc.moveTo(pts[0].sx, pts[0].sy);
                for (int p = 1; p < pts.length; p++) {
                    gc.lineTo(pts[p].sx, pts[p].sy);
                }
                gc.closePath();
                gc.setFill(colors[i]);
                gc.fill();
                gc.setStroke(Color.rgb(0, 0, 0, 0.22));
                gc.setLineWidth(1.0);
                gc.stroke();
            }
        }
    }

    private void drawFurniture3D(GraphicsContext gc, ZoneBcd z, double cw, double ch, boolean isHovered, boolean isSelected) {
        double lift = isHovered ? 0.16 : 0.0;
        double baseZ = lift;

        if (isSelected) {
            drawSelectionHalo(gc, z, cw, ch);
        }

        if (z.getCategorie() == CategorieMobilier.SHELF) {
            drawBookShelf(gc, z, baseZ, cw, ch, isHovered, isSelected);
        } else if (z.getCategorie() == CategorieMobilier.BIN) {
            drawBookBin(gc, z, baseZ, cw, ch, isHovered, isSelected);
        } else if (z.getId().equals("m-bureau")) {
            drawReceptionDesk(gc, z, baseZ, cw, ch, isHovered, isSelected);
        } else if (z.getId().equals("m-table")) {
            drawReadingTable(gc, z, baseZ, cw, ch, isHovered, isSelected);
        } else if (z.getId().equals("m-canape") || z.getId().equals("m-chaises")) {
            drawSeating(gc, z, baseZ, cw, ch, isHovered, isSelected);
        } else if (z.getId().equals("m-poufs")) {
            drawPoufs(gc, z, baseZ, cw, ch, isHovered, isSelected);
        } else if (z.getId().equals("m-chariot")) {
            drawCart(gc, z, baseZ, cw, ch, isHovered, isSelected);
        }
    }

    private void drawSelectionHalo(GraphicsContext gc, ZoneBcd z, double cw, double ch) {
        double pad = 0.18;
        Point3D p0 = project3d(z.getX1() - pad, z.getZ1() - pad, 0.02, cw, ch);
        Point3D p1 = project3d(z.getX2() + pad, z.getZ1() - pad, 0.02, cw, ch);
        Point3D p2 = project3d(z.getX2() + pad, z.getZ2() + pad, 0.02, cw, ch);
        Point3D p3 = project3d(z.getX1() - pad, z.getZ2() + pad, 0.02, cw, ch);

        gc.setFill(Color.rgb(59, 130, 246, 0.35));
        gc.fillPolygon(new double[]{p0.sx, p1.sx, p2.sx, p3.sx},
                       new double[]{p0.sy, p1.sy, p2.sy, p3.sy}, 4);

        gc.setStroke(Color.web("#60A5FA"));
        gc.setLineWidth(2.5);
        gc.setLineDashes(6.0, 4.0);
        gc.strokePolygon(new double[]{p0.sx, p1.sx, p2.sx, p3.sx},
                         new double[]{p0.sy, p1.sy, p2.sy, p3.sy}, 4);
        gc.setLineDashes(null);
    }

    private void drawBookShelf(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        Color baseCol;
        if (isSelected) {
            baseCol = Color.web("#2563EB");
        } else if (isHovered) {
            baseCol = Color.web("#34D399");
        } else if (z.getId().equals("8")) {
            baseCol = Color.web("#10B981"); // Vert émeraude pour biblio verte
        } else {
            baseCol = Color.web("#F59E0B"); // Jaune école
        }

        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ, z.getWidth(), z.getDepth(), z.getHauteur(),
                shadeColor(baseCol, 22), baseCol, cw, ch);

        // Dessin des livres réalistes sur les étagères (1:1 Prototype Web)
        drawBooksOnShelf(gc, z, baseZ, cw, ch);
    }

    private void drawBooksOnShelf(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch) {
        Color[] bookColors = {
                Color.web("#EF4444"), Color.web("#3B82F6"), Color.web("#F59E0B"), Color.web("#10B981"),
                Color.web("#8B5CF6"), Color.web("#EC4899"), Color.web("#06B6D4"), Color.web("#F97316")
        };
        int shelfLevels = 3;
        boolean isHorizontal = z.getWidth() >= z.getDepth();

        if (isHorizontal) {
            int count = Math.min(10, Math.max(5, (int)(z.getWidth() * 4)));
            double step = z.getWidth() / (count + 1);
            for (int s = 1; s <= shelfLevels; s++) {
                double bz = baseZ + (z.getHauteur() / (shelfLevels + 1)) * s;
                for (int i = 0; i < count; i++) {
                    double bx = z.getX1() + 0.08 + i * step;
                    Color col = bookColors[(i * 3 + s) % bookColors.length];
                    Point3D pA = project3d(bx, z.getZ2() + 0.02, bz, cw, ch);
                    Point3D pB = project3d(bx + step * 0.72, z.getZ2() + 0.02, bz + (z.getHauteur() * 0.16), cw, ch);
                    double minX = Math.min(pA.sx, pB.sx);
                    double minY = Math.min(pA.sy, pB.sy);
                    double bw = Math.max(3.0, Math.abs(pB.sx - pA.sx));
                    double bh = Math.max(6.0, Math.abs(pB.sy - pA.sy));
                    gc.setFill(col);
                    gc.fillRect(minX, minY, bw, bh);
                }
            }
        } else {
            int count = Math.min(12, Math.max(6, (int)(z.getDepth() * 4)));
            double step = z.getDepth() / (count + 1);
            for (int s = 1; s <= shelfLevels; s++) {
                double bz = baseZ + (z.getHauteur() / (shelfLevels + 1)) * s;
                for (int i = 0; i < count; i++) {
                    double by = z.getZ1() + 0.08 + i * step;
                    Color col = bookColors[(i * 2 + s) % bookColors.length];
                    Point3D pA = project3d(z.getX2() + 0.02, by, bz, cw, ch);
                    Point3D pB = project3d(z.getX2() + 0.02, by + step * 0.72, bz + (z.getHauteur() * 0.16), cw, ch);
                    double minX = Math.min(pA.sx, pB.sx);
                    double minY = Math.min(pA.sy, pB.sy);
                    double bw = Math.max(3.0, Math.abs(pB.sx - pA.sx));
                    double bh = Math.max(6.0, Math.abs(pB.sy - pA.sy));
                    gc.setFill(col);
                    gc.fillRect(minX, minY, bw, bh);
                }
            }
        }
    }

    private void drawBookBin(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        Color baseCol;
        if (isSelected) {
            baseCol = Color.web("#2563EB");
        } else if (isHovered) {
            baseCol = Color.web("#F87171");
        } else if (z.getId().equals("7")) {
            baseCol = Color.web("#059669"); // Caisse verte
        } else {
            baseCol = Color.web("#EF4444"); // Rouge BD / Bacs
        }

        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ, z.getWidth(), z.getDepth(), z.getHauteur(),
                shadeColor(baseCol, 18), baseCol, cw, ch);

        Point3D pMid = project3d(z.getCenterX(), z.getCenterZ(), baseZ + z.getHauteur() + 0.1, cw, ch);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 9.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(z.getNomCourt(), pMid.sx, pMid.sy);
    }

    private void drawReceptionDesk(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        Color deskCol = isSelected ? Color.web("#2563EB") : (isHovered ? Color.web("#60A5FA") : Color.web("#E2E8F0"));
        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ, z.getWidth(), z.getDepth(), z.getHauteur(), Color.WHITE, deskCol, cw, ch);

        // Ordinateur PC BCD Express
        double pcX = z.getX1() + 0.2;
        double pcZ = z.getZ1() + 0.3;
        double pcH = baseZ + z.getHauteur();
        drawBoxSegment(gc, pcX, pcZ, pcH, 0.4, 0.5, 0.35, Color.web("#38BDF8"), Color.web("#1E293B"), cw, ch);

        Point3D pPc = project3d(pcX + 0.2, pcZ + 0.25, pcH + 0.45, cw, ch);
        gc.setFill(Color.web("#38BDF8"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("💻 BCD Express", pPc.sx, pPc.sy);
    }

    private void drawReadingTable(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        Color tableCol = isSelected ? Color.web("#2563EB") : (isHovered ? Color.web("#94A3B8") : Color.web("#CBD5E1"));
        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ, z.getWidth(), z.getDepth(), z.getHauteur(), Color.web("#F1F5F9"), tableCol, cw, ch);

        Point3D pCenter = project3d(z.getCenterX(), z.getCenterZ(), baseZ + z.getHauteur() + 0.15, cw, ch);
        gc.setFill(Color.web("#475569"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 9.5));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("📖 8 Places Travail / Pop-ups", pCenter.sx, pCenter.sy);
    }

    private void drawSeating(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        Color col = isSelected ? Color.web("#2563EB") : (isHovered ? Color.web("#34D399") : Color.web("#10B981"));
        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ, z.getWidth(), z.getDepth(), z.getHauteur() * 0.55, shadeColor(col, 15), col, cw, ch);
        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ + z.getHauteur() * 0.55, z.getWidth(), 0.2, z.getHauteur() * 0.45, shadeColor(col, 25), col, cw, ch);
    }

    private void drawPoufs(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        // Tapis vert foncé
        Point3D p0 = project3d(z.getX1(), z.getZ1(), baseZ, cw, ch);
        Point3D p1 = project3d(z.getX2(), z.getZ1(), baseZ, cw, ch);
        Point3D p2 = project3d(z.getX2(), z.getZ2(), baseZ, cw, ch);
        Point3D p3 = project3d(z.getX1(), z.getZ2(), baseZ, cw, ch);

        gc.setFill(Color.web("#065F46"));
        gc.fillPolygon(new double[]{p0.sx, p1.sx, p2.sx, p3.sx},
                       new double[]{p0.sy, p1.sy, p2.sy, p3.sy}, 4);

        // 3 Poufs verts
        Color[] pColors = { Color.web("#10B981"), Color.web("#34D399"), Color.web("#059669") };
        for (int i = 0; i < 3; i++) {
            double px = z.getX1() + 0.35;
            double pz = z.getZ1() + 0.3 + i * 0.5;
            drawBoxSegment(gc, px, pz, baseZ + 0.02, 0.4, 0.4, 0.35, pColors[i], shadeColor(pColors[i], -15), cw, ch);
        }
    }

    private void drawCart(GraphicsContext gc, ZoneBcd z, double baseZ, double cw, double ch, boolean isHovered, boolean isSelected) {
        Color col = isSelected ? Color.web("#2563EB") : (isHovered ? Color.web("#94A3B8") : Color.web("#E2E8F0"));
        drawBoxSegment(gc, z.getX1(), z.getZ1(), baseZ, z.getWidth(), z.getDepth(), z.getHauteur(), Color.WHITE, col, cw, ch);

        Point3D pMid = project3d(z.getCenterX(), z.getCenterZ(), baseZ + z.getHauteur() + 0.12, cw, ch);
        gc.setFill(Color.web("#475569"));
        gc.setFont(Font.font("System", FontWeight.BOLD, 9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("🛒 Retours", pMid.sx, pMid.sy);
    }

    private void drawShelfLabelBadge(GraphicsContext gc, ZoneBcd z, double cw, double ch, boolean isSelected) {
        Point3D pTop = project3d(z.getCenterX(), z.getCenterZ(), z.getHauteur() + 0.42, cw, ch);

        String text = (z.hasNumero() ? z.getIcon() + " " + z.getNumero() + ". " + z.getNomCourt() : z.getIcon() + " " + z.getNomCourt());
        gc.setFont(Font.font("-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif", FontWeight.BOLD, 10.5));

        Text t = new Text(text);
        t.setFont(gc.getFont());
        double tw = t.getLayoutBounds().getWidth();
        double pad = 8.0;
        double bw = tw + pad * 2.0;
        double bh = 20.0;
        double bx = pTop.sx - bw / 2.0;
        double by = pTop.sy - bh / 2.0;

        gc.setFill(isSelected ? Color.web("#2563EB") : Color.rgb(15, 23, 42, 0.90));
        gc.fillRoundRect(bx, by, bw, bh, 6, 6);
        gc.setStroke(isSelected ? Color.web("#93C5FD") : Color.rgb(255, 255, 255, 0.22));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(bx, by, bw, bh, 6, 6);

        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(text, pTop.sx, pTop.sy + 4.0);
    }

    private void drawHighlightedPin(GraphicsContext gc, double cw, double ch) {
        if (pinnedZone == null) return;
        Point3D top = project3d(pinnedZone.getCenterX(), pinnedZone.getCenterZ(), pinnedZone.getHauteur() + 0.75, cw, ch);
        double bounce = Math.sin(animationTick * 3.0) * 8.0;
        double py = top.sy + bounce;

        gc.setFill(Color.web("#EF4444"));
        gc.fillPolygon(new double[]{top.sx - 8, top.sx + 8, top.sx},
                       new double[]{py - 24, py - 24, py}, 3);
        gc.fillOval(top.sx - 12, py - 36, 24, 24);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("📍", top.sx, py - 20);

        if (pinnedBookTitle != null) {
            String label = pinnedBookTitle + (pinnedBookCote != null ? " (" + pinnedBookCote + ")" : "");
            Text t = new Text(label);
            t.setFont(Font.font("System", FontWeight.BOLD, 11));
            double tw = t.getLayoutBounds().getWidth();
            double bw = tw + 16;
            double bh = 22;
            double bx = top.sx - bw / 2.0;
            double by = py - 64;

            gc.setFill(Color.rgb(15, 23, 42, 0.95));
            gc.fillRoundRect(bx, by, bw, bh, 6, 6);
            gc.setStroke(Color.web("#F59E0B"));
            gc.setLineWidth(1.5);
            gc.strokeRoundRect(bx, by, bw, bh, 6, 6);

            gc.setFill(Color.web("#FDE68A"));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(label, top.sx, by + 15);
        }
    }

    /* ── Moteur de Rendu 2D Architectural (Plan du Dessus CAD) ──────────── */

    private void render2DPlan(GraphicsContext gc, double cw, double ch) {
        double roomW = PlanBcdService.ROOM_WIDTH;
        double roomD = PlanBcdService.ROOM_DEPTH;

        double margin = 48.0;
        double scaleX = (cw - margin * 2) / roomW;
        double scaleY = (ch - margin * 2) / roomD;
        double scale = Math.min(scaleX, scaleY) * cameraZoom;

        double originX = (cw - roomW * scale) / 2.0 + panOffsetX;
        double originY = (ch - roomD * scale) / 2.0 + panOffsetY;

        // Sol 2D sombre blueprint
        gc.setFill(isWarmAmbiance ? Color.web("#B45309", 0.85) : Color.web("#1E293B"));
        gc.fillRect(originX, originY, roomW * scale, roomD * scale);

        gc.setStroke(Color.rgb(255, 255, 255, 0.08));
        gc.setLineWidth(1.0);
        for (double x = 1.0; x < roomW; x += 1.0) {
            gc.strokeLine(originX + x * scale, originY, originX + x * scale, originY + roomD * scale);
        }
        for (double z = 1.0; z < roomD; z += 1.0) {
            gc.strokeLine(originX, originY + z * scale, originX + roomW * scale, originY + z * scale);
        }

        // Contour de la salle
        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(2.5);
        gc.strokeRect(originX, originY, roomW * scale, roomD * scale);

        // Verrière jaune (Nord)
        gc.setStroke(Color.web("#EAB308"));
        gc.setLineWidth(5.0);
        gc.strokeLine(originX + 2.1 * scale, originY, originX + 8.8 * scale, originY);

        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.setFill(Color.web("#FCD34D"));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("VERRIÈRE JAUNE (COULOIR)", originX + 5.45 * scale, originY - 10);

        // Porte d'entrée
        gc.setFill(Color.web("#2563EB"));
        gc.fillRect(originX + 1.0 * scale, originY - 2, 1.0 * scale, 5);
        gc.fillText("PORTE D'ENTRÉE", originX + 1.5 * scale, originY - 10);

        // Fenêtres (Sud)
        gc.setStroke(Color.web("#38BDF8"));
        gc.setLineWidth(5.0);
        gc.strokeLine(originX + 0.4 * scale, originY + roomD * scale, originX + 8.6 * scale, originY + roomD * scale);
        gc.setFill(Color.web("#38BDF8"));
        gc.fillText("GRANDES FENÊTRES EXTÉRIEURES", originX + 4.5 * scale, originY + roomD * scale + 18);

        // Dessin des meubles 2D
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

            Color fillColor = Color.web(z.getCategorie().getColorFill());
            Color strokeColor = Color.web(z.getCategorie().getColorStroke());
            if ("8".equals(z.getId())) {
                fillColor = Color.web("#BBF7D0");
                strokeColor = Color.web("#16A34A");
            }

            gc.setFill(fillColor);
            gc.fillRoundRect(rx, ry, rw, rh, 6, 6);

            if (isSelected) {
                gc.setStroke(Color.web("#60A5FA"));
                gc.setLineWidth(3.0);
            } else if (isMatched) {
                double pulse = 2.0 + Math.sin(animationTick * 2.0) * 1.5;
                gc.setStroke(Color.web("#F59E0B"));
                gc.setLineWidth(pulse);
            } else if (isHovered) {
                gc.setStroke(Color.WHITE);
                gc.setLineWidth(2.0);
            } else {
                gc.setStroke(strokeColor);
                gc.setLineWidth(1.5);
            }
            gc.strokeRoundRect(rx, ry, rw, rh, 6, 6);

            if (btnToggleLabels.isSelected()) {
                double cx = rx + rw / 2.0;
                double cy = ry + rh / 2.0;
                if (z.hasNumero()) {
                    double badgeR = Math.max(9, Math.min(13, 11 * cameraZoom));
                    gc.setFill(Color.rgb(15, 23, 42, 0.90));
                    gc.fillOval(cx - badgeR, cy - badgeR, badgeR * 2, badgeR * 2);
                    gc.setStroke(strokeColor);
                    gc.setLineWidth(1.2);
                    gc.strokeOval(cx - badgeR, cy - badgeR, badgeR * 2, badgeR * 2);

                    gc.setFill(Color.WHITE);
                    gc.setFont(Font.font("System", FontWeight.BOLD, Math.max(9, badgeR * 1.1)));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(String.valueOf(z.getNumero()), cx, cy + badgeR * 0.38);
                } else {
                    gc.setFill(Color.web(z.getCategorie().getColorText()));
                    gc.setFont(Font.font("System", FontWeight.BOLD, Math.max(8, 10 * cameraZoom)));
                    gc.setTextAlign(TextAlignment.CENTER);
                    gc.fillText(z.getNomCourt(), cx, cy + 3);
                }
            }
            gc.setGlobalAlpha(1.0);
        }

        if (pinnedZone != null) {
            drawPin2D(gc, originX + pinnedZone.getCenterX() * scale, originY + pinnedZone.getCenterZ() * scale);
        }
    }

    private void drawPin2D(GraphicsContext gc, double x, double y) {
        double bounce = Math.sin(animationTick * 3.0) * 5.0;
        double py = y + bounce;

        gc.setFill(Color.web("#EF4444"));
        gc.fillPolygon(new double[]{x - 7, x + 7, x}, new double[]{py - 20, py - 20, py}, 3);
        gc.fillOval(x - 10, py - 30, 20, 20);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("System", FontWeight.BOLD, 10));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("📍", x, py - 16);
    }

    private static String computeCote(Livre l) {
        if (l == null) return "BCD";
        String genre = l.getGenre() != null ? l.getGenre().toUpperCase() : "ROM";
        String auteur = l.getAuteur() != null ? l.getAuteur().trim().toUpperCase() : "INC";
        String prefix = switch (genre) {
            case "BD", "BANDE DESSINÉE" -> "BD";
            case "DOCUMENTAIRE" -> "DOC";
            case "ALBUM" -> "ALB";
            case "CONTE" -> "CON";
            case "POÉSIE" -> "POE";
            case "THÉÂTRE" -> "THE";
            case "REVUE", "MAGAZINE" -> "REV";
            default -> "ROM";
        };
        char tri = PlanBcdService.getNomFamilleLettre(auteur);
        return prefix + " " + tri;
    }
}
