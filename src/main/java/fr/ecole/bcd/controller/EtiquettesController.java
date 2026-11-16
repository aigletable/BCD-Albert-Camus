package fr.ecole.bcd.controller;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.EtiquetteItem;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.service.BarcodeService;
import fr.ecole.bcd.service.BarcodeService.FormatPlanche;
import fr.ecole.bcd.service.SoundService;
import fr.ecole.bcd.view.AnimationHelper;
import fr.ecole.bcd.view.CoverHelper;
import fr.ecole.bcd.view.DialogHelper;

import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;

/**
 * Contrôleur de la page dédiée d'impression d'étiquettes et codes-barres.
 * <p>
 * Offre une expérience fluide et sur-mesure pour la BCD :
 * </p>
 * <ul>
 *   <li>Liste à cocher claire avec indicateur visuel immédiat.</li>
 *   <li>Détection des exemplaires multiples : demande interactive du nombre d'étiquettes souhaité.</li>
 *   <li>Ajout ultra-rapide au clavier ou à la douchette : saisie du titre + Entrée ➔ livre coché instantanément.</li>
 *   <li>Génération directe des planches A4 (Avery / Agipa) avec décalage anti-gaspillage.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-11-16
 */
public class EtiquettesController implements Initializable {

    // ── Champs FXML ──────────────────────────────────────────────────────────
    @FXML private TextField txtRechercheRapide;
    @FXML private Button btnValiderRapide;
    @FXML private Label lblActionFeedback;

    @FXML private TextField txtFiltre;
    @FXML private CheckBox chkFiltreSelectionnes;
    @FXML private Label lblTableTotal;

    @FXML private TableView<EtiquetteSelectionRow> tableLivres;
    @FXML private TableColumn<EtiquetteSelectionRow, Boolean> colSelect;
    @FXML private TableColumn<EtiquetteSelectionRow, String> colTitre;
    @FXML private TableColumn<EtiquetteSelectionRow, String> colAuteur;
    @FXML private TableColumn<EtiquetteSelectionRow, String> colIsbn;
    @FXML private TableColumn<EtiquetteSelectionRow, Integer> colExemplaires;
    @FXML private TableColumn<EtiquetteSelectionRow, Integer> colQuantite;
    @FXML private TableColumn<EtiquetteSelectionRow, String> colGenre;

    // Volet Réglages & Résumé
    @FXML private Label lblNbLivresChoisis;
    @FXML private Label lblNbEtiquettesTotal;
    @FXML private Label lblNbFeuillesA4;
    @FXML private Label lblAntiGaspillageSummary;

    @FXML private ComboBox<FormatPlanche> cbFormat;
    @FXML private Label lblFormatDesc;
    @FXML private Spinner<Integer> spStartOffset;
    @FXML private TextField txtEnTete;
    @FXML private CheckBox chkTitle;
    @FXML private CheckBox chkBarcode;
    @FXML private CheckBox chkBorders;

    // Aperçu
    @FXML private Label lblPrevHeader;
    @FXML private Label lblPrevTitre;
    @FXML private Label lblPrevBars;
    @FXML private Label lblPrevId;

    @FXML private Button btnImprimer;

    // ── Données et DAOs ──────────────────────────────────────────────────────
    private final LivreDao livreDao = new LivreDao();
    private final ExemplaireDao exemplaireDao = new ExemplaireDao();

    private final ObservableList<EtiquetteSelectionRow> masterRows = FXCollections.observableArrayList();
    private FilteredList<EtiquetteSelectionRow> filteredRows;

    // ── Modèle de ligne de sélection ─────────────────────────────────────────
    public static class EtiquetteSelectionRow {
        private final Livre livre;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final IntegerProperty quantite = new SimpleIntegerProperty(1);

        public EtiquetteSelectionRow(Livre livre) {
            this.livre = livre;
            int total = livre.getNbExemplaires();
            this.quantite.set(total > 0 ? total : 1);
        }

        public Livre getLivre() { return livre; }
        public String getIsbn() { return livre.getIsbn() != null ? livre.getIsbn() : ""; }
        public String getTitre() { return livre.getTitre() != null ? livre.getTitre() : ""; }
        public String getAuteur() { return livre.getAuteur() != null ? livre.getAuteur() : ""; }
        public String getEditeur() { return livre.getEditeur() != null ? livre.getEditeur() : ""; }
        public String getGenre() { return livre.getGenre() != null ? livre.getGenre() : ""; }
        public int getNbExemplaires() { return livre.getNbExemplaires(); }

        public boolean isSelected() { return selected.get(); }
        public void setSelected(boolean val) { selected.set(val); }
        public BooleanProperty selectedProperty() { return selected; }

        public int getQuantite() { return quantite.get(); }
        public void setQuantite(int val) { quantite.set(Math.max(1, val)); }
        public IntegerProperty quantiteProperty() { return quantite; }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        configureTable();
        configureSettings();
        configureFastSearch();
        configureFilter();

        // Chargement initial des données
        loadData();
    }

    /**
     * Charge ou rafraîchit tous les livres depuis SQLite en préservant les sélections actives.
     */
    public void loadData() {
        try {
            Map<String, Integer> selectedQuantities = new HashMap<>();
            for (EtiquetteSelectionRow r : masterRows) {
                if (r.isSelected()) {
                    selectedQuantities.put(r.getIsbn(), r.getQuantite());
                }
            }

            List<Livre> livres = livreDao.findAllWithCounts();
            List<EtiquetteSelectionRow> newRows = new ArrayList<>(livres.size());

            for (Livre l : livres) {
                EtiquetteSelectionRow row = new EtiquetteSelectionRow(l);
                if (selectedQuantities.containsKey(l.getIsbn())) {
                    row.setSelected(true);
                    row.setQuantite(selectedQuantities.get(l.getIsbn()));
                }
                // Écouteur pour recalculer les totaux dès qu'une case ou une quantité change
                row.selectedProperty().addListener((obs, oldV, newV) -> updateKPIs());
                row.quantiteProperty().addListener((obs, oldV, newV) -> updateKPIs());
                newRows.add(row);
            }

            masterRows.setAll(newRows);
            updateTableCountLabel();
            updateKPIs();
        } catch (SQLException e) {
            DialogHelper.showError("Erreur de chargement", "Impossible de charger les livres : " + e.getMessage());
        }
    }

    /**
     * Permet à un autre contrôleur (ex: Inventaire) de pré-cocher des livres spécifiques.
     */
    public void selectLivres(List<Livre> livres) {
        if (livres == null || livres.isEmpty()) return;
        Set<String> targetIsbns = new HashSet<>();
        for (Livre l : livres) {
            if (l != null && l.getIsbn() != null) targetIsbns.add(l.getIsbn());
        }

        for (EtiquetteSelectionRow r : masterRows) {
            if (targetIsbns.contains(r.getIsbn())) {
                r.setSelected(true);
            }
        }
        updateKPIs();
    }

    // ── Configuration du tableau ─────────────────────────────────────────────
    private void configureTable() {
        colSelect.setCellValueFactory(cell -> cell.getValue().selectedProperty());
        colSelect.setCellFactory(col -> new TableCell<>() {
            private final CheckBox cb = new CheckBox();
            {
                cb.setAlignment(Pos.CENTER);
                cb.setOnAction(e -> {
                    EtiquetteSelectionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        boolean willBeSelected = cb.isSelected();
                        if (willBeSelected) {
                            handleBookChecked(row);
                        } else {
                            row.setSelected(false);
                        }
                    }
                });
            }

            @Override
            protected void updateItem(Boolean selected, boolean empty) {
                super.updateItem(selected, empty);
                if (empty || selected == null) {
                    setGraphic(null);
                } else {
                    cb.setSelected(selected);
                    setGraphic(cb);
                }
            }
        });

        // Titre de l'ouvrage (mis en rouge vif si "Livre ISBN...")
        colTitre.setCellValueFactory(new PropertyValueFactory<>("titre"));
        colTitre.setCellFactory(col -> new TableCell<>() {
            private final Label iconLbl = new Label("⚠️");
            private final Label titleLbl = new Label();
            private final Label badge = new Label("À renseigner");
            private final HBox warnBox = new HBox(6, iconLbl, titleLbl, badge);

            {
                warnBox.setAlignment(Pos.CENTER_LEFT);
                badge.setStyle("-fx-background-color: #FEE2E2; -fx-text-fill: #991B1B; -fx-font-weight: 800; -fx-font-size: 10px; -fx-padding: 1 6; -fx-background-radius: 999; -fx-border-color: #FCA5A5; -fx-border-radius: 999;");
                titleLbl.setStyle("-fx-font-weight: 800; -fx-text-fill: #DC2626;");
            }

            @Override
            protected void updateItem(String titre, boolean empty) {
                super.updateItem(titre, empty);
                if (empty || titre == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else if (CoverHelper.isTitreNonRenseigne(titre)) {
                    setText(null);
                    titleLbl.setText(titre);
                    setGraphic(warnBox);
                    setStyle("-fx-background-color: rgba(254, 226, 226, 0.45);");
                } else {
                    setText(titre);
                    setGraphic(null);
                    setStyle("-fx-font-weight: 700; -fx-text-fill: #1E293B;");
                }
            }
        });

        // Auteur & Éditeur
        colAuteur.setCellValueFactory(cell -> {
            String aut = cell.getValue().getAuteur();
            String ed = cell.getValue().getEditeur();
            String res = (aut != null && !aut.isBlank()) ? aut : "–";
            if (ed != null && !ed.isBlank()) res += " (" + ed + ")";
            return new SimpleStringProperty(res);
        });

        // ISBN
        colIsbn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        colIsbn.setStyle("-fx-font-family: monospace; -fx-alignment: CENTER;");

        // Exemplaires physiques en BCD
        colExemplaires.setCellValueFactory(new PropertyValueFactory<>("nbExemplaires"));
        colExemplaires.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                setAlignment(Pos.CENTER);
            }
            @Override
            protected void updateItem(Integer count, boolean empty) {
                super.updateItem(count, empty);
                if (empty || count == null) {
                    setGraphic(null);
                } else {
                    badge.setText("📦 " + count + " ex.");
                    if (count > 1) {
                        badge.setStyle("-fx-background-color: #DBEAFE; -fx-text-fill: #1D4ED8; -fx-font-weight: 800; -fx-font-size: 11px; -fx-padding: 2 7; -fx-background-radius: 6;");
                    } else if (count == 1) {
                        badge.setStyle("-fx-background-color: #E2E8F0; -fx-text-fill: #475569; -fx-font-weight: 700; -fx-font-size: 11px; -fx-padding: 2 7; -fx-background-radius: 6;");
                    } else {
                        badge.setText("0 ex.");
                        badge.setStyle("-fx-background-color: #F1F5F9; -fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-padding: 2 7; -fx-background-radius: 6;");
                    }
                    setGraphic(badge);
                }
            }
        });

        // Quantité d'étiquettes à imprimer (Interactive avec Spinner & bouton choix)
        colQuantite.setCellValueFactory(cell -> cell.getValue().quantiteProperty().asObject());
        colQuantite.setCellFactory(col -> new TableCell<>() {
            private final Spinner<Integer> spinner = new Spinner<>(1, 999, 1);
            private final Button btnChanger = new Button("⚙️");
            private final HBox box = new HBox(6, spinner, btnChanger);

            {
                box.setAlignment(Pos.CENTER);
                spinner.setPrefWidth(65);
                spinner.setEditable(true);
                btnChanger.setStyle("-fx-cursor: hand; -fx-padding: 2 6; -fx-font-size: 11px;");
                btnChanger.setTooltip(new Tooltip("Choisir le nombre d'exemplaires à imprimer"));

                spinner.valueProperty().addListener((obs, oldV, newV) -> {
                    EtiquetteSelectionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null && newV != null) {
                        row.setQuantite(newV);
                    }
                });

                btnChanger.setOnAction(e -> {
                    EtiquetteSelectionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                    if (row != null) {
                        promptMultipleCopies(row);
                    }
                });
            }

            @Override
            protected void updateItem(Integer q, boolean empty) {
                super.updateItem(q, empty);
                EtiquetteSelectionRow row = getTableRow() != null ? getTableRow().getItem() : null;
                if (empty || row == null) {
                    setGraphic(null);
                } else if (!row.isSelected()) {
                    Label disabledLbl = new Label("–");
                    disabledLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-style: italic;");
                    setGraphic(disabledLbl);
                } else {
                    int max = Math.max(1, row.getNbExemplaires());
                    SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 999, row.getQuantite());
                    spinner.setValueFactory(factory);
                    btnChanger.setVisible(row.getNbExemplaires() > 1);
                    btnChanger.setManaged(row.getNbExemplaires() > 1);
                    setGraphic(box);
                }
            }
        });

        // Genre
        colGenre.setCellValueFactory(new PropertyValueFactory<>("genre"));
        colGenre.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            { setAlignment(Pos.CENTER); }
            @Override
            protected void updateItem(String genre, boolean empty) {
                super.updateItem(genre, empty);
                if (empty || genre == null || genre.isBlank()) {
                    setGraphic(null);
                } else {
                    badge.setText(genre);
                    CoverHelper.GenreStyle style = CoverHelper.getStyleForGenre(genre);
                    badge.getStyleClass().setAll("badge-genre", style.badgeClass());
                    badge.setStyle("-fx-padding: 2 7; -fx-background-radius: 999; -fx-font-size: 10.5px; -fx-font-weight: 700;");
                    setGraphic(badge);
                }
            }
        });

        // Coloration douce des lignes sélectionnées
        tableLivres.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(EtiquetteSelectionRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                } else if (row.isSelected()) {
                    setStyle("-fx-background-color: #F0FDF4;");
                } else {
                    setStyle("");
                }
            }
        });

        // Filtrage et tri
        filteredRows = new FilteredList<>(masterRows, p -> true);
        SortedList<EtiquetteSelectionRow> sortedRows = new SortedList<>(filteredRows);
        sortedRows.comparatorProperty().bind(tableLivres.comparatorProperty());
        tableLivres.setItems(sortedRows);
    }

    // ── Gestion du cochage avec demande pour exemplaires multiples ────────────
    private void handleBookChecked(EtiquetteSelectionRow row) {
        if (row == null) return;

        int totalExemplaires = row.getNbExemplaires();
        if (totalExemplaires > 1) {
            // "si le livre a plusieurs exemplaires on demande si on veut en imprimer plusieurs, si oui combien"
            promptMultipleCopies(row);
        } else {
            row.setQuantite(1);
            row.setSelected(true);
        }
        tableLivres.refresh();
        updateKPIs();
    }

    /**
     * Boîte de dialogue conviviale demandant combien d'étiquettes imprimer pour un livre à exemplaires multiples.
     */
    private void promptMultipleCopies(EtiquetteSelectionRow row) {
        int total = row.getNbExemplaires();

        Dialog<Integer> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("📚 Choix des exemplaires à imprimer");
        dialog.setHeaderText("L'ouvrage « " + row.getTitre() + " » compte " + total + " exemplaires en BCD.");

        Label lblPrompt = new Label("Combien d'étiquettes souhaitez-vous générer pour ce livre ?");
        lblPrompt.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        RadioButton rbTous = new RadioButton("Imprimer TOUS les exemplaires (" + total + " étiquettes)");
        rbTous.setSelected(true);
        rbTous.setStyle("-fx-font-size: 12.5px;");

        RadioButton rbUn = new RadioButton("Imprimer 1 SEUL exemplaire (1 étiquette)");
        rbUn.setStyle("-fx-font-size: 12.5px;");

        RadioButton rbCustom = new RadioButton("Choisir une quantité précise :");
        rbCustom.setStyle("-fx-font-size: 12.5px;");

        ToggleGroup group = new ToggleGroup();
        rbTous.setToggleGroup(group);
        rbUn.setToggleGroup(group);
        rbCustom.setToggleGroup(group);

        Spinner<Integer> spCustom = new Spinner<>(1, Math.max(total, 99), Math.min(2, total));
        spCustom.setPrefWidth(80);
        spCustom.disableProperty().bind(rbCustom.selectedProperty().not());

        HBox customBox = new HBox(8, rbCustom, spCustom);
        customBox.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, lblPrompt, rbTous, rbUn, customBox);
        content.setPadding(new javafx.geometry.Insets(14));
        dialog.getDialogPane().setContent(content);

        ButtonType btnValider = new ButtonType("Valider", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnAnnuler = new ButtonType("Annuler", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(btnValider, btnAnnuler);

        dialog.setResultConverter(btn -> {
            if (btn == btnValider) {
                if (rbTous.isSelected()) return total;
                if (rbUn.isSelected()) return 1;
                return spCustom.getValue();
            }
            return null;
        });

        Optional<Integer> result = dialog.showAndWait();
        if (result.isPresent()) {
            row.setQuantite(result.get());
            row.setSelected(true);
        } else {
            // Si l'utilisateur a annulé alors qu'il n'était pas sélectionné, on le laisse décoché
            if (row.getQuantite() <= 0) {
                row.setSelected(false);
            }
        }
        tableLivres.refresh();
        updateKPIs();
    }

    // ── Recherche & Ajout Ultra-rapide ("tape le nom, valide et ça part") ─────
    private void configureFastSearch() {
        txtRechercheRapide.setOnAction(e -> handleValiderRechercheRapide());
    }

    @FXML
    private void handleValiderRechercheRapide() {
        String input = txtRechercheRapide.getText() != null ? txtRechercheRapide.getText().trim() : "";
        if (input.isEmpty()) return;

        String cleanInput = input.toLowerCase();
        String cleanIsbnQuery = input.replaceAll("[^0-9X]", "").toLowerCase();

        // 1. Recherche par ISBN exact
        EtiquetteSelectionRow found = null;
        for (EtiquetteSelectionRow r : masterRows) {
            String rIsbn = r.getIsbn().replaceAll("[^0-9X]", "").toLowerCase();
            if (!cleanIsbnQuery.isEmpty() && rIsbn.equals(cleanIsbnQuery)) {
                found = r;
                break;
            }
        }

        // 2. Recherche par Titre exact
        if (found == null) {
            for (EtiquetteSelectionRow r : masterRows) {
                if (r.getTitre().equalsIgnoreCase(input)) {
                    found = r;
                    break;
                }
            }
        }

        // 3. Recherche par Titre ou Auteur contenant le mot
        if (found == null) {
            List<EtiquetteSelectionRow> partialMatches = new ArrayList<>();
            for (EtiquetteSelectionRow r : masterRows) {
                if (r.getTitre().toLowerCase().contains(cleanInput) || r.getAuteur().toLowerCase().contains(cleanInput)) {
                    partialMatches.add(r);
                }
            }
            if (partialMatches.size() == 1) {
                found = partialMatches.get(0);
            } else if (partialMatches.size() > 1) {
                // Plusieurs résultats : on filtre le tableau pour que l'utilisateur clique
                txtFiltre.setText(input);
                showActionFeedback("💡 Plusieurs livres correspondent à « " + input + " » : cochez celui de votre choix ci-dessous.", "#0284C7");
                return;
            }
        }

        if (found != null) {
            // Trouvé ! "Ça part"
            txtRechercheRapide.clear();
            final EtiquetteSelectionRow target = found;

            if (target.isSelected()) {
                showActionFeedback("ℹ️ « " + target.getTitre() + " » est déjà coché (" + target.getQuantite() + " étiquette(s)).", "#0284C7");
            } else {
                handleBookChecked(target);
                showActionFeedback("✅ « " + target.getTitre() + " » validé et coché (" + target.getQuantite() + " étiquette(s)) !", "#16A34A");
                SoundService.playSuccess();
            }

            // Défilement automatique vers la ligne
            tableLivres.scrollTo(target);
            tableLivres.getSelectionModel().select(target);
            AnimationHelper.flashSuccess(tableLivres);
            Platform.runLater(txtRechercheRapide::requestFocus);
        } else {
            showActionFeedback("⚠️ Aucun livre trouvé pour : « " + input + " »", "#DC2626");
            SoundService.playWarning();
            AnimationHelper.flashError(txtRechercheRapide);
        }
    }

    private void showActionFeedback(String message, String colorHex) {
        lblActionFeedback.setText(message);
        lblActionFeedback.setStyle("-fx-font-weight: 700; -fx-font-size: 12px; -fx-text-fill: " + colorHex + ";");
        lblActionFeedback.setVisible(true);
        lblActionFeedback.setManaged(true);
    }

    // ── Filtrage dynamique de la table ────────────────────────────────────────
    private void configureFilter() {
        txtFiltre.textProperty().addListener((obs, oldV, q) -> applyFilter());
        chkFiltreSelectionnes.selectedProperty().addListener((obs, oldV, b) -> applyFilter());
    }

    private void applyFilter() {
        String query = txtFiltre.getText() != null ? txtFiltre.getText().trim().toLowerCase() : "";
        boolean onlyChecked = chkFiltreSelectionnes.isSelected();

        filteredRows.setPredicate(row -> {
            if (row == null) return false;
            if (onlyChecked && !row.isSelected()) return false;
            if (query.isEmpty()) return true;

            boolean matchTitre = row.getTitre().toLowerCase().contains(query);
            boolean matchAuteur = row.getAuteur().toLowerCase().contains(query);
            boolean matchEditeur = row.getEditeur().toLowerCase().contains(query);
            boolean matchIsbn = row.getIsbn().toLowerCase().contains(query);
            boolean matchGenre = row.getGenre().toLowerCase().contains(query);

            return matchTitre || matchAuteur || matchEditeur || matchIsbn || matchGenre;
        });

        updateTableCountLabel();
    }

    private void updateTableCountLabel() {
        int visible = filteredRows != null ? filteredRows.size() : 0;
        int total = masterRows.size();
        lblTableTotal.setText(String.format("%d / %d livre(s) affiché(s)", visible, total));
    }

    // ── Actions de masse ─────────────────────────────────────────────────────
    @FXML
    private void handleActualiser() {
        loadData();
    }

    @FXML
    private void handleToutCocher() {
        for (EtiquetteSelectionRow r : filteredRows) {
            r.setSelected(true);
            if (r.getQuantite() <= 0) r.setQuantite(Math.max(1, r.getNbExemplaires()));
        }
        tableLivres.refresh();
        updateKPIs();
        showActionFeedback("☑️ Tous les livres visibles ont été cochés.", "#16A34A");
    }

    @FXML
    private void handleToutDecocher() {
        for (EtiquetteSelectionRow r : masterRows) {
            r.setSelected(false);
        }
        tableLivres.refresh();
        updateKPIs();
        showActionFeedback("⬜ Sélection réinitialisée.", "#64748B");
    }

    @FXML
    private void handleCocherDerniers() {
        int count = Math.min(24, masterRows.size());
        for (int i = 0; i < count; i++) {
            EtiquetteSelectionRow r = masterRows.get(i);
            r.setSelected(true);
        }
        tableLivres.refresh();
        updateKPIs();
        showActionFeedback("✨ Les 24 premiers livres du catalogue ont été cochés.", "#1D4ED8");
    }

    // ── Configuration & Aperçu de la Planche A4 ──────────────────────────────
    private void configureSettings() {
        cbFormat.setItems(FXCollections.observableArrayList(
                FormatPlanche.STANDARD_24,
                FormatPlanche.AVERY_21,
                FormatPlanche.COMPACT_30,
                FormatPlanche.GRAND_14,
                FormatPlanche.TRANCHE_65
        ));
        cbFormat.setValue(FormatPlanche.STANDARD_24);

        spStartOffset.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 24, 1));

        cbFormat.valueProperty().addListener((obs, oldV, f) -> {
            if (f != null) {
                int max = f.getTotalParPage();
                int cur = spStartOffset.getValue();
                spStartOffset.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, Math.min(cur, max)));
                updateFormatDescription(f);
                updateKPIs();
            }
        });

        spStartOffset.valueProperty().addListener((obs, oldV, newV) -> updateKPIs());

        txtEnTete.textProperty().addListener((obs, oldV, txt) -> updatePreview());
        chkTitle.selectedProperty().addListener((obs, oldV, b) -> updatePreview());
        chkBarcode.selectedProperty().addListener((obs, oldV, b) -> updatePreview());

        updateFormatDescription(FormatPlanche.STANDARD_24);
        updatePreview();
    }

    private void updateFormatDescription(FormatPlanche f) {
        if (f == FormatPlanche.STANDARD_24) {
            lblFormatDesc.setText("💡 3 colonnes x 8 lignes (70 x 37 mm) — Standard universel dos de livre.");
        } else if (f == FormatPlanche.AVERY_21) {
            lblFormatDesc.setText("💡 3 colonnes x 7 lignes (63.5 x 38.1 mm) — Format Avery L7160 le plus répandu.");
        } else if (f == FormatPlanche.COMPACT_30) {
            lblFormatDesc.setText("💡 3 colonnes x 10 lignes (70 x 29.6 mm) — Cotes fines et jeunesse.");
        } else if (f == FormatPlanche.GRAND_14) {
            lblFormatDesc.setText("💡 2 colonnes x 7 lignes (105 x 42.3 mm) — Grands albums et BD.");
        } else {
            lblFormatDesc.setText("💡 5 colonnes x 13 lignes (38.1 x 21.2 mm) — Tranches fines de livres.");
        }
    }

    private void updatePreview() {
        String header = txtEnTete.getText() != null && !txtEnTete.getText().isBlank()
                ? txtEnTete.getText().toUpperCase() : "ÉCOLE ALBERT CAMUS PASTEUR";
        lblPrevHeader.setText(header);

        // Premier livre sélectionné comme échantillon
        EtiquetteSelectionRow sample = masterRows.stream().filter(EtiquetteSelectionRow::isSelected).findFirst().orElse(null);
        String sampleTitre = sample != null ? sample.getTitre() : "Exemple de livre";
        lblPrevTitre.setText(sampleTitre);

        lblPrevTitre.setVisible(chkTitle.isSelected());
        lblPrevTitre.setManaged(chkTitle.isSelected());

        lblPrevBars.setVisible(chkBarcode.isSelected());
        lblPrevBars.setManaged(chkBarcode.isSelected());
    }

    private void updateKPIs() {
        long livresCount = masterRows.stream().filter(EtiquetteSelectionRow::isSelected).count();
        int totalEtiquettes = masterRows.stream()
                .filter(EtiquetteSelectionRow::isSelected)
                .mapToInt(EtiquetteSelectionRow::getQuantite)
                .sum();

        lblNbLivresChoisis.setText(livresCount + " livre" + (livresCount > 1 ? "s" : ""));
        lblNbEtiquettesTotal.setText(totalEtiquettes + " étiquette" + (totalEtiquettes > 1 ? "s" : ""));

        FormatPlanche f = cbFormat.getValue() != null ? cbFormat.getValue() : FormatPlanche.STANDARD_24;
        int perPage = f.getTotalParPage();
        int offset = spStartOffset.getValue() != null ? spStartOffset.getValue() - 1 : 0;
        int totalSlots = Math.max(0, offset) + totalEtiquettes;
        int pages = (int) Math.ceil((double) totalSlots / perPage);
        if (pages == 0 && totalEtiquettes > 0) pages = 1;

        lblNbFeuillesA4.setText(pages + " feuille" + (pages > 1 ? "s" : ""));

        int usedOnLast = totalSlots > 0 ? (totalSlots % perPage == 0 ? perPage : totalSlots % perPage) : 0;
        int remainingUnused = perPage - usedOnLast;

        if (totalEtiquettes == 0) {
            lblAntiGaspillageSummary.setText("⚠️ Aucun livre coché actuellement. Cochez les livres dans la liste à gauche.");
            lblAntiGaspillageSummary.setStyle("-fx-font-size: 11px; -fx-text-fill: #B45309; -fx-background-color: #FEF3C7; -fx-padding: 6px 10px; -fx-background-radius: 6px;");
        } else {
            String offsetNotice = offset > 0 ? " (départ décalé de " + offset + " case(s))" : "";
            lblAntiGaspillageSummary.setText(String.format("📄 %d étiquette(s) sur %d feuille(s) A4%s\n✨ Dernière feuille : %d étiquette(s) utilisée(s), reste %d étiquette(s) vierge(s).",
                    totalEtiquettes, pages, offsetNotice, usedOnLast, remainingUnused));
            lblAntiGaspillageSummary.setStyle("-fx-font-size: 11px; -fx-text-fill: #0284C7; -fx-background-color: #E0F2FE; -fx-padding: 6px 10px; -fx-background-radius: 6px;");
        }

        updatePreview();
    }

    // ── Génération & Impression ("après quand j'ai fini ça génère") ──────────
    private List<EtiquetteItem> collectSelectedEtiquetteItems() throws SQLException {
        List<EtiquetteSelectionRow> selected = masterRows.stream()
                .filter(EtiquetteSelectionRow::isSelected)
                .toList();

        List<EtiquetteItem> items = new ArrayList<>();

        for (EtiquetteSelectionRow row : selected) {
            Livre livre = row.getLivre();
            int needed = row.getQuantite();

            // Récupère les exemplaires physiques de la base
            List<EtiquetteItem> existing = exemplaireDao.findEtiquettesByIsbn(livre.getIsbn());

            int taken = 0;
            for (EtiquetteItem ex : existing) {
                if (taken < needed) {
                    items.add(ex);
                    taken++;
                } else {
                    break;
                }
            }

            // S'il n'y a pas assez d'exemplaires physiques enregistrés (ou livre sans exemplaire encore créé),
            // on complète avec des étiquettes d'exemplaires virtuels basés sur l'ISBN
            while (taken < needed) {
                items.add(new EtiquetteItem(
                        0,
                        livre.getIsbn(),
                        livre.getTitre(),
                        livre.getAuteur(),
                        livre.getEditeur(),
                        ""
                ));
                taken++;
            }
        }

        return items;
    }

    @FXML
    private void handleGenererEtImprimer() {
        try {
            List<EtiquetteItem> items = collectSelectedEtiquetteItems();
            if (items.isEmpty()) {
                DialogHelper.showWarning("Sélection vide", "Veuillez cocher au moins un livre dans la liste avant de lancer l'impression.");
                return;
            }

            File tmpFile = File.createTempFile("etiquettes_bcd_", ".html");
            int offset = spStartOffset.getValue() != null ? spStartOffset.getValue() - 1 : 0;

            BarcodeService.genererPlancheHtml(
                    items,
                    cbFormat.getValue(),
                    txtEnTete.getText().trim(),
                    chkTitle.isSelected(),
                    chkBarcode.isSelected(),
                    offset,
                    chkBorders.isSelected(),
                    tmpFile
            );

            DialogHelper.openInBrowser(tmpFile);
            SoundService.playSuccess();
            showActionFeedback(String.format("✅ Planche de %d étiquette(s) générée et ouverte dans votre navigateur !", items.size()), "#16A34A");

        } catch (Exception ex) {
            DialogHelper.showError("Erreur d'impression", "Impossible de générer la planche : " + ex.getMessage());
            SoundService.playError();
        }
    }

    @FXML
    private void handleEnregistrerHtml() {
        try {
            List<EtiquetteItem> items = collectSelectedEtiquetteItems();
            if (items.isEmpty()) {
                DialogHelper.showWarning("Sélection vide", "Veuillez cocher au moins un livre dans la liste.");
                return;
            }

            FileChooser fc = new FileChooser();
            fc.setTitle("Enregistrer la planche d'étiquettes A4");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document HTML (*.html)", "*.html"));
            fc.setInitialFileName("etiquettes_bcd.html");

            File dest = fc.showSaveDialog(MainApp.getPrimaryStage());
            if (dest != null) {
                int offset = spStartOffset.getValue() != null ? spStartOffset.getValue() - 1 : 0;
                BarcodeService.genererPlancheHtml(
                        items,
                        cbFormat.getValue(),
                        txtEnTete.getText().trim(),
                        chkTitle.isSelected(),
                        chkBarcode.isSelected(),
                        offset,
                        chkBorders.isSelected(),
                        dest
                );
                SoundService.playSuccess();
                showActionFeedback("💾 Planche enregistrée avec succès sous " + dest.getName(), "#16A34A");
            }
        } catch (Exception ex) {
            DialogHelper.showError("Erreur d'enregistrement", "Erreur : " + ex.getMessage());
        }
    }
}
