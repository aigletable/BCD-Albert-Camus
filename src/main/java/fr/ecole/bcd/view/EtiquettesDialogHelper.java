package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.model.EtiquetteItem;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.service.BarcodeService;
import fr.ecole.bcd.service.BarcodeService.FormatPlanche;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Supplier;

/**
 * Boîte de dialogue et calque modal interactif pour la configuration, la sélection et l'impression
 * de planches d'étiquettes A4 autocollantes et de codes-barres physiques pour les livres.
 * <p>
 * Ce composant offre des fonctionnalités complètes de ciblage et d'export :
 * </p>
 * <ul>
 *   <li><b>Modes de sélection multiples</b> : sélection courante du tableau, sélection personnalisée (panier de livres),
 *       résultats filtrés par recherche, plage d'exemplaires numérique (#ID de début à fin),
 *       ou derniers ouvrages nouvellement ajoutés.</li>
 *   <li><b>Choix des formats de planches</b> : support natif des planches standard du marché
 *       (24 étiquettes 70x37 mm, 14 étiquettes 105x42.4 mm, 21 étiquettes 63.5x38.1 mm).</li>
 *   <li><b>Personnalisation visuelle</b> : inclusion optionnelle du code-barres Code 128, de la cote de rangement,
 *       du genre littéraire, et gestion du décalage (offset) pour réutiliser des planches partiellement imprimées.</li>
 *   <li><b>Sortie multi-format</b> : génération de documents PDF vectoriels haute fidélité prêts à l'impression
 *       et fichiers HTML mis en page.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class EtiquettesDialogHelper {

    /**
     * Constructeur privé empêchant l'instanciation de cette classe utilitaire.
     */
    private EtiquettesDialogHelper() {
        // Classe utilitaire
    }

    /**
     * Récupère la liste des éléments d'étiquettes à générer en fonction du mode de sélection choisi par l'utilisateur.
     *
     * @param exemplaireDao      le DAO d'accès aux exemplaires physiques
     * @param isSelected         {@code true} pour imprimer uniquement les livres sélectionnés dans le tableau principal
     * @param selectedLivres     la liste des livres sélectionnés
     * @param isCustomPick       {@code true} pour imprimer les livres choisis via la sélection manuelle personnalisée
     * @param customPickedIsbns  l'ensemble des ISBN retenus manuellement
     * @param isFiltered         {@code true} pour imprimer l'ensemble des livres correspondant aux filtres actifs de recherche
     * @param filteredLivresList la liste des livres actuellement visibles après filtrage
     * @param isRange            {@code true} pour imprimer selon une plage d'identifiants d'exemplaires (#ID début à #ID fin)
     * @param startStr           la chaîne représentant l'ID de début
     * @param endStr             la chaîne représentant l'ID de fin
     * @param isLatest           {@code true} pour imprimer les N derniers exemplaires enregistrés
     * @param latestLimit        le nombre maximum de derniers exemplaires à extraire (par défaut 24)
     * @return la liste des {@link EtiquetteItem} prête pour la génération des planches
     * @throws SQLException en cas d'erreur lors de la requête SQL dans la base SQLite
     */
    public static List<EtiquetteItem> getEtiquettesItems(
            ExemplaireDao exemplaireDao,
            boolean isSelected, List<Livre> selectedLivres,
            boolean isCustomPick, Set<String> customPickedIsbns,
            boolean isFiltered, List<Livre> filteredLivresList,
            boolean isRange, String startStr, String endStr,
            boolean isLatest, Integer latestLimit) throws SQLException {

        if (isSelected && selectedLivres != null && !selectedLivres.isEmpty()) {
            List<String> isbns = selectedLivres.stream()
                    .filter(Objects::nonNull)
                    .map(Livre::getIsbn)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return exemplaireDao.findEtiquettesByIsbns(isbns);
        } else if (isCustomPick && customPickedIsbns != null && !customPickedIsbns.isEmpty()) {
            return exemplaireDao.findEtiquettesByIsbns(new ArrayList<>(customPickedIsbns));
        } else if (isFiltered && filteredLivresList != null && !filteredLivresList.isEmpty()) {
            List<String> isbns = filteredLivresList.stream()
                    .filter(Objects::nonNull)
                    .map(Livre::getIsbn)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return exemplaireDao.findEtiquettesByIsbns(isbns);
        } else if (isRange) {
            int start = 1;
            int end = 50;
            try { start = Integer.parseInt(startStr.trim()); } catch (Exception ignored) {}
            try { end = Integer.parseInt(endStr.trim()); } catch (Exception ignored) {}
            return exemplaireDao.findEtiquettesByRange(start, end);
        } else if (isLatest) {
            int limit = latestLimit != null ? latestLimit : 24;
            return exemplaireDao.findEtiquettesLatest(limit);
        } else {
            return exemplaireDao.findAllEtiquettes();
        }
    }

    /**
     * Ouvre la fenêtre modale superposée (calque modal JavaFX) permettant de configurer
     * et de lancer l'impression d'étiquettes de codes-barres.
     *
     * @param exemplaireDao      DAO d'accès aux données des exemplaires
     * @param livreTableView     la table JavaFX principale des livres de l'inventaire
     * @param masterLivres       la liste maître de tous les livres en base
     * @param modalOverlay       le conteneur plein écran transparent accueillant la modale
     * @param modalCard          la carte graphique blanche stylisée accueillant le contenu
     * @param closeModalCallback rappel exécuté lors de la fermeture de la modale
     */
    public static void openEtiquettesDialog(
            ExemplaireDao exemplaireDao,
            TableView<Livre> livreTableView,
            ObservableList<Livre> masterLivres,
            StackPane modalOverlay,
            VBox modalCard,
            Runnable closeModalCallback) {

        VBox content = new VBox(14);
        content.setPrefWidth(650);
        content.setPadding(new Insets(10));

        // 1. Périmètre des exemplaires à imprimer
        Label lblScopeSec = new Label("1. Quels exemplaires imprimer ?");
        lblScopeSec.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        ToggleGroup scopeGroup = new ToggleGroup();

        // Livres actuellement sélectionnés dans la table
        List<Livre> selectedLivres = (livreTableView != null && livreTableView.getSelectionModel().getSelectedItems() != null)
                ? livreTableView.getSelectionModel().getSelectedItems().stream().filter(Objects::nonNull).toList()
                : List.of();
        int selectedCount = selectedLivres.size();
        int selectedCopies = selectedLivres.stream().mapToInt(Livre::getNbExemplaires).sum();

        RadioButton rbSelected = new RadioButton();
        if (selectedCount == 1) {
            Livre single = selectedLivres.get(0);
            int copies = single.getNbExemplaires();
            rbSelected.setText(String.format("Livre sélectionné : %s (%s • %d ex.)",
                    single.getTitre(), single.getIsbn(), copies));
        } else if (selectedCount > 1) {
            rbSelected.setText(String.format("Livres sélectionnés dans le tableau (%d livres • %d exemplaires au total)",
                    selectedCount, selectedCopies));
        } else {
            rbSelected.setText("Livres sélectionnés dans le tableau (aucun livre sélectionné actuellement)");
            rbSelected.setDisable(true);
        }
        rbSelected.setToggleGroup(scopeGroup);

        // Résultats de la recherche / filtre actuel
        List<Livre> currentTableItems = (livreTableView != null && livreTableView.getItems() != null)
                ? livreTableView.getItems().stream().filter(Objects::nonNull).toList()
                : List.of();
        int filterCount = currentTableItems.size();
        int filterCopies = currentTableItems.stream().mapToInt(Livre::getNbExemplaires).sum();
        boolean hasActiveFilter = (masterLivres != null && filterCount < masterLivres.size());

        RadioButton rbFiltered = new RadioButton();
        if (hasActiveFilter && filterCount > 0) {
            rbFiltered.setText(String.format("Résultats du filtre de recherche actuel (%d livres • %d exemplaires)",
                    filterCount, filterCopies));
            rbFiltered.setDisable(false);
        } else {
            rbFiltered.setText("Résultats du filtre de recherche actuel (aucun filtre actif)");
            rbFiltered.setDisable(true);
        }
        rbFiltered.setToggleGroup(scopeGroup);

        // Sélection personnalisée de livres (Picker interactif)
        RadioButton rbCustomPick = new RadioButton("🎯 Sélection personnalisée de livres (recherche et cases à cocher)…");
        rbCustomPick.setToggleGroup(scopeGroup);

        VBox customPickCard = new VBox(8);
        customPickCard.setPadding(new Insets(10));
        customPickCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #cbd5e1; -fx-border-radius: 8; -fx-background-radius: 8;");
        customPickCard.setVisible(false);
        customPickCard.setManaged(false);

        Set<String> customPickedIsbns = new LinkedHashSet<>();
        for (Livre l : selectedLivres) {
            if (l.getIsbn() != null) customPickedIsbns.add(l.getIsbn());
        }

        TextField tfPickerSearch = new TextField();
        tfPickerSearch.setPromptText("🔍 Filtrer les livres par titre, auteur, ISBN...");
        HBox.setHgrow(tfPickerSearch, Priority.ALWAYS);

        Button btnCheckAll = new Button("☑️ Tout cocher");
        btnCheckAll.getStyleClass().add("btn-outline");
        btnCheckAll.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");

        Button btnUncheckAll = new Button("⬜ Tout décocher");
        btnUncheckAll.getStyleClass().add("btn-outline");
        btnUncheckAll.setStyle("-fx-font-size: 11px; -fx-padding: 4 8;");

        HBox pickerToolbar = new HBox(8, tfPickerSearch, btnCheckAll, btnUncheckAll);
        pickerToolbar.setAlignment(Pos.CENTER_LEFT);

        FilteredList<Livre> pickerFilteredList = new FilteredList<>(masterLivres != null ? masterLivres : FXCollections.observableArrayList(), p -> true);
        tfPickerSearch.textProperty().addListener((obs, oldV, q) -> {
            String query = (q != null) ? q.trim().toLowerCase() : "";
            pickerFilteredList.setPredicate(livre -> {
                if (livre == null) return false;
                if (query.isEmpty()) return true;
                boolean matchTitre = livre.getTitre() != null && livre.getTitre().toLowerCase().contains(query);
                boolean matchAuteur = livre.getAuteur() != null && livre.getAuteur().toLowerCase().contains(query);
                boolean matchIsbn = livre.getIsbn() != null && livre.getIsbn().toLowerCase().contains(query);
                return matchTitre || matchAuteur || matchIsbn;
            });
        });

        ListView<Livre> pickerListView = new ListView<>(pickerFilteredList);
        pickerListView.setPrefHeight(180);
        pickerListView.setMaxHeight(220);

        Label lblPickerStatus = new Label();
        lblPickerStatus.setStyle("-fx-font-size: 11.5px; -fx-font-weight: bold; -fx-text-fill: #1a66ff;");

        Runnable updatePickerCount = () -> {
            int countBooks = customPickedIsbns.size();
            int countCopies = (masterLivres != null) ? masterLivres.stream()
                    .filter(l -> customPickedIsbns.contains(l.getIsbn()))
                    .mapToInt(Livre::getNbExemplaires)
                    .sum() : 0;
            lblPickerStatus.setText(String.format("✨ %d livre(s) sélectionné(s) • %d étiquette(s) au total", countBooks, countCopies));
            rbCustomPick.setText(String.format("🎯 Sélection personnalisée de livres (%d livre%s • %d ex.)",
                    countBooks, countBooks > 1 ? "s" : "", countCopies));
        };

        pickerListView.setCellFactory(lv -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final Label titleLabel = new Label();
            private final Label subLabel = new Label();
            private final Label copiesBadge = new Label();
            private final HBox layout = new HBox(10);
            private final VBox textContainer = new VBox(2);
            private final Region spacer = new Region();

            {
                titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
                subLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
                textContainer.getChildren().addAll(titleLabel, subLabel);
                HBox.setHgrow(textContainer, Priority.ALWAYS);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                layout.setAlignment(Pos.CENTER_LEFT);
                layout.getChildren().addAll(checkBox, textContainer, spacer, copiesBadge);

                checkBox.setOnAction(e -> {
                    Livre livre = getItem();
                    if (livre != null && livre.getIsbn() != null) {
                        if (checkBox.isSelected()) {
                            customPickedIsbns.add(livre.getIsbn());
                        } else {
                            customPickedIsbns.remove(livre.getIsbn());
                        }
                        updatePickerCount.run();
                    }
                });
            }

            @Override
            protected void updateItem(Livre livre, boolean empty) {
                super.updateItem(livre, empty);
                if (empty || livre == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    titleLabel.setText(livre.getTitre());
                    String auteur = (livre.getAuteur() != null && !livre.getAuteur().isBlank()) ? livre.getAuteur() : "Auteur non renseigné";
                    subLabel.setText(auteur + " • ISBN: " + (livre.getIsbn() != null ? livre.getIsbn() : "–"));

                    int copies = livre.getNbExemplaires();
                    copiesBadge.setText("📦 " + copies + " ex.");
                    if (copies == 0) {
                        copiesBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #94a3b8; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;");
                    } else {
                        copiesBadge.setStyle("-fx-background-color: #e0f2fe; -fx-text-fill: #0284c7; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 2 6; -fx-background-radius: 4;");
                    }

                    checkBox.setSelected(customPickedIsbns.contains(livre.getIsbn()));
                    setGraphic(layout);
                }
            }
        });

        pickerListView.setOnMouseClicked(e -> {
            Livre clicked = pickerListView.getSelectionModel().getSelectedItem();
            if (clicked != null && clicked.getIsbn() != null) {
                if (customPickedIsbns.contains(clicked.getIsbn())) {
                    customPickedIsbns.remove(clicked.getIsbn());
                } else {
                    customPickedIsbns.add(clicked.getIsbn());
                }
                pickerListView.refresh();
                updatePickerCount.run();
            }
        });

        btnCheckAll.setOnAction(e -> {
            for (Livre l : pickerFilteredList) {
                if (l != null && l.getIsbn() != null) customPickedIsbns.add(l.getIsbn());
            }
            pickerListView.refresh();
            updatePickerCount.run();
        });

        btnUncheckAll.setOnAction(e -> {
            for (Livre l : pickerFilteredList) {
                if (l != null && l.getIsbn() != null) customPickedIsbns.remove(l.getIsbn());
            }
            pickerListView.refresh();
            updatePickerCount.run();
        });

        customPickCard.getChildren().addAll(pickerToolbar, pickerListView, lblPickerStatus);

        scopeGroup.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            boolean isCustom = (newT == rbCustomPick);
            customPickCard.setVisible(isCustom);
            customPickCard.setManaged(isCustom);
        });

        updatePickerCount.run();

        // Tous les exemplaires de la bibliothèque
        int totalExemplaires = 0;
        try {
            totalExemplaires = exemplaireDao.countTotal();
        } catch (SQLException ignored) {}

        RadioButton rbAll = new RadioButton("Tous les exemplaires de la bibliothèque (Total : " + totalExemplaires + " exemplaires)");
        rbAll.setToggleGroup(scopeGroup);

        // Sélection par défaut
        if (selectedCount > 0) {
            rbSelected.setSelected(true);
        } else if (hasActiveFilter && filterCount > 0) {
            rbFiltered.setSelected(true);
        } else {
            rbAll.setSelected(true);
        }

        // Plage de numéros d'exemplaires
        RadioButton rbRange = new RadioButton("Plage de numéros d'exemplaires :");
        rbRange.setToggleGroup(scopeGroup);

        TextField tfStartId = new TextField("1");
        tfStartId.setPrefWidth(70);
        TextField tfEndId = new TextField("50");
        tfEndId.setPrefWidth(70);
        HBox rangeBox = new HBox(8, rbRange, new Label("du #"), tfStartId, new Label("au #"), tfEndId);
        rangeBox.setAlignment(Pos.CENTER_LEFT);

        // Derniers exemplaires
        RadioButton rbLatest = new RadioButton("Derniers exemplaires enregistrés :");
        rbLatest.setToggleGroup(scopeGroup);
        ComboBox<Integer> cbLatestCount = new ComboBox<>(FXCollections.observableArrayList(10, 20, 50, 100));
        cbLatestCount.setValue(24);
        HBox latestBox = new HBox(8, rbLatest, new Label("les"), cbLatestCount, new Label("derniers"));
        latestBox.setAlignment(Pos.CENTER_LEFT);

        VBox scopeBox = new VBox(8, rbSelected, rbFiltered, rbCustomPick, customPickCard, rbAll, rangeBox, latestBox);
        scopeBox.setPadding(new Insets(8, 12, 8, 12));
        scopeBox.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 8; -fx-background-radius: 8;");

        // 2. Format de planche A4
        Label lblFormatSec = new Label("2. Format de la planche d'étiquettes A4 :");
        lblFormatSec.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        ComboBox<FormatPlanche> cbFormat = new ComboBox<>(FXCollections.observableArrayList(
                FormatPlanche.STANDARD_24,
                FormatPlanche.AVERY_21,
                FormatPlanche.COMPACT_30,
                FormatPlanche.GRAND_14,
                FormatPlanche.TRANCHE_65
        ));
        cbFormat.setValue(FormatPlanche.STANDARD_24);
        cbFormat.setPrefWidth(520);

        Label lblFormatHint = new Label();
        lblFormatHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #0284c7;");

        // Options de réutilisation et d'alignement anti-gaspillage
        Spinner<Integer> spStartOffset = new Spinner<>(1, 24, 1);
        spStartOffset.setPrefWidth(80);
        spStartOffset.setEditable(true);

        Label lblOffsetHelp = new Label("💡 Utile si vous avez déjà décollé des étiquettes sur la 1ère feuille : commence à la case choisie pour éviter tout gaspillage !");
        lblOffsetHelp.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        lblOffsetHelp.setWrapText(true);

        HBox offsetBox = new HBox(10,
                new Label("Commencer à l'étiquette n° :"),
                spStartOffset,
                new Label("(1ère feuille)")
        );
        offsetBox.setAlignment(Pos.CENTER_LEFT);

        CheckBox chkBorders = new CheckBox("Tracer les contours / repères de découpe (décocher si vous imprimez sur planches d'étiquettes pré-découpées)");
        chkBorders.setSelected(false);

        Label lblSheetSummary = new Label();
        lblSheetSummary.setWrapText(true);

        Supplier<Integer> estimateCountSupplier = () -> {
            try {
                return getEtiquettesItems(
                        exemplaireDao,
                        rbSelected.isSelected(), selectedLivres,
                        rbCustomPick.isSelected(), customPickedIsbns,
                        rbFiltered.isSelected(), currentTableItems,
                        rbRange.isSelected(), tfStartId.getText(), tfEndId.getText(),
                        rbLatest.isSelected(), cbLatestCount.getValue()).size();
            } catch (Exception ex) {
                return 0;
            }
        };

        Runnable updateSummary = () -> {
            int count = estimateCountSupplier.get();
            FormatPlanche f = cbFormat.getValue();
            if (f == null) f = FormatPlanche.STANDARD_24;
            int offset = spStartOffset.getValue() - 1;
            int perPage = f.getTotalParPage();
            int totalSlots = Math.max(0, offset) + count;
            int pages = (int) Math.ceil((double) totalSlots / perPage);
            if (pages == 0) pages = (count > 0 ? 1 : 0);

            int usedOnLast = totalSlots > 0 ? (totalSlots % perPage == 0 ? perPage : totalSlots % perPage) : 0;
            int remainingUnused = perPage - usedOnLast;

            if (count == 0) {
                lblSheetSummary.setText("⚠️ Aucun exemplaire physique ne correspond aux critères choisis.");
                lblSheetSummary.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #b45309; -fx-background-color: #fef3c7; -fx-padding: 8 12; -fx-background-radius: 6;");
            } else {
                String offsetDetail = offset > 0 ? String.format(" (décalage de %d case(s) sur la page 1)", offset) : "";
                lblSheetSummary.setText(String.format(
                        "📄 Optimisation anti-gaspillage : %d étiquette(s) physique(s) • %d feuille(s) A4 requise(s)%s\n"
                        + "✨ %d étiquette(s) sur la dernière page • Reste %d étiquette(s) vierge(s) réutilisable(s) au prochain tirage.",
                        count, pages, offsetDetail, usedOnLast, remainingUnused
                ));
                lblSheetSummary.setStyle("-fx-font-size: 11.5px; -fx-font-weight: 600; -fx-text-fill: #0369a1; -fx-background-color: #e0f2fe; -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-color: #bae6fd; -fx-border-radius: 6;");
            }
        };

        Runnable updateHint = () -> {
            FormatPlanche f = cbFormat.getValue();
            if (f == FormatPlanche.STANDARD_24) {
                lblFormatHint.setText("💡 Standard 24 étiquettes : 3x8, 70x37 mm (Avery L7159/C2160/Agipa 119013) — universel dos de livre.");
            } else if (f == FormatPlanche.AVERY_21) {
                lblFormatHint.setText("💡 Planche Avery 21 étiquettes : 3x7, 63.5x38.1 mm (Avery L7160/Agipa 119011) — standard Avery le plus répandu.");
            } else if (f == FormatPlanche.COMPACT_30) {
                lblFormatHint.setText("💡 Compact 30 étiquettes : 3x10, 70x29.6 mm (Avery L7651/Agipa 119014) — cotes & petits ouvrages jeunesse.");
            } else if (f == FormatPlanche.GRAND_14) {
                lblFormatHint.setText("💡 Grand format 14 étiquettes : 2x7, 105x42.3 mm (Avery L7163/Agipa 119017) — grands albums et BDs.");
            } else {
                lblFormatHint.setText("💡 Tranches 65 étiquettes : 5x13, 38.1x21.2 mm (Avery 3659/L7651) — spécial tranches fines de livres.");
            }
        };

        cbFormat.valueProperty().addListener((obs, oldV, f) -> {
            if (f != null) {
                int max = f.getTotalParPage();
                int cur = spStartOffset.getValue();
                if (cur > max) cur = 1;
                spStartOffset.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, max, cur));
            }
            updateHint.run();
            updateSummary.run();
        });

        spStartOffset.valueProperty().addListener((obs, oldV, newV) -> updateSummary.run());
        scopeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> updateSummary.run());
        cbLatestCount.valueProperty().addListener((obs, oldV, newV) -> updateSummary.run());
        tfStartId.textProperty().addListener((obs, oldV, newV) -> updateSummary.run());
        tfEndId.textProperty().addListener((obs, oldV, newV) -> updateSummary.run());

        updateHint.run();
        updateSummary.run();

        // 3. Personnalisation & options
        Label lblOptionsSec = new Label("3. Personnalisation de l'étiquette :");
        lblOptionsSec.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        TextField tfEnTete = new TextField("ÉCOLE ALBERT CAMUS PASTEUR");
        tfEnTete.setPromptText("Nom de l'école / en-tête de l'étiquette");
        HBox enTeteBox = new HBox(10, new Label("En-tête :"), tfEnTete);
        enTeteBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tfEnTete, Priority.ALWAYS);

        CheckBox chkTitle = new CheckBox("Afficher le titre de l'ouvrage");
        chkTitle.setSelected(true);

        CheckBox chkBarcode = new CheckBox("Afficher le code-barres scannable (Code 39 standard)");
        chkBarcode.setSelected(true);

        HBox chkBox = new HBox(20, chkTitle, chkBarcode);

        // 4. Aperçu visuel d'une étiquette
        Label lblPreviewSec = new Label("Aperçu indicatif d'une étiquette :");
        lblPreviewSec.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");

        VBox previewCard = new VBox(3);
        previewCard.setAlignment(Pos.CENTER);
        previewCard.setPadding(new Insets(8));
        previewCard.setPrefWidth(220);
        previewCard.setMaxWidth(260);
        previewCard.setStyle("-fx-background-color: white; -fx-border-color: #94a3b8; -fx-border-style: dashed; -fx-border-radius: 4; -fx-background-radius: 4;");

        Label prevHeader = new Label("ÉCOLE ALBERT CAMUS PASTEUR");
        prevHeader.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        String sampleTitle = !selectedLivres.isEmpty() ? selectedLivres.get(0).getTitre() : "Exemple de livre";
        Label prevTitle = new Label(sampleTitle);
        prevTitle.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #0f172a;");
        prevTitle.setWrapText(true);

        Label prevBars = new Label("||| || |||| | ||| || |");
        prevBars.setStyle("-fx-font-family: monospace; -fx-font-size: 14px; -fx-font-weight: bold; -fx-letter-spacing: 2px;");

        Label prevId = new Label("Ex. #42");
        prevId.setStyle("-fx-font-family: monospace; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #0284c7;");

        previewCard.getChildren().addAll(prevHeader, prevTitle, prevBars, prevId);

        Runnable updatePreview = () -> {
            prevHeader.setText(tfEnTete.getText().isBlank() ? "ÉCOLE ALBERT CAMUS PASTEUR" : tfEnTete.getText().toUpperCase());
            prevTitle.setVisible(chkTitle.isSelected());
            prevTitle.setManaged(chkTitle.isSelected());
            prevBars.setVisible(chkBarcode.isSelected());
            prevBars.setManaged(chkBarcode.isSelected());
        };

        tfEnTete.textProperty().addListener((obs, oldV, newV) -> updatePreview.run());
        chkTitle.selectedProperty().addListener((obs, oldV, newV) -> updatePreview.run());
        chkBarcode.selectedProperty().addListener((obs, oldV, newV) -> updatePreview.run());

        HBox previewContainer = new HBox(previewCard);
        previewContainer.setAlignment(Pos.CENTER);

        // Assemblage des sections dans content
        content.getChildren().addAll(
                lblScopeSec,
                scopeBox,
                new Separator(),
                lblFormatSec,
                cbFormat,
                lblFormatHint,
                offsetBox,
                lblOffsetHelp,
                chkBorders,
                lblSheetSummary,
                new Separator(),
                lblOptionsSec,
                enTeteBox,
                chkBox,
                new Separator(),
                lblPreviewSec,
                previewContainer
        );

        // 5. Boutons d'action
        Button btnOpenBrowser = new Button("🖨️ Ouvrir la planche dans le navigateur (Prêt à imprimer)");
        btnOpenBrowser.getStyleClass().add("btn-blue");

        Button btnSaveHtml = new Button("💾 Enregistrer le fichier HTML…");
        btnSaveHtml.getStyleClass().add("btn-outline");

        // Helper pour récupérer les étiquettes avec les filtres sélectionnés
        Supplier<List<EtiquetteItem>> fetchItems = () -> {
            try {
                return getEtiquettesItems(
                        exemplaireDao,
                        rbSelected.isSelected(), selectedLivres,
                        rbCustomPick.isSelected(), customPickedIsbns,
                        rbFiltered.isSelected(), currentTableItems,
                        rbRange.isSelected(), tfStartId.getText(), tfEndId.getText(),
                        rbLatest.isSelected(), cbLatestCount.getValue());
            } catch (SQLException e) {
                return List.of();
            }
        };

        // ── CAS 1 : Affichage dans le calque modal In-App ──
        if (modalOverlay != null && modalCard != null) {
            modalCard.getChildren().clear();

            HBox modalHeader = new HBox(12);
            modalHeader.setAlignment(Pos.CENTER_LEFT);

            VBox titleBox = new VBox(2);
            Label titleLbl = new Label("🏷️ Générateur d'Étiquettes & Codes-barres");
            titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: 800; -fx-text-fill: #0F172A;");
            Label subtitleLbl = new Label("Génération et impression de planches d'étiquettes A4 prêtes à coller pour les livres.");
            subtitleLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #64748B;");
            titleBox.getChildren().addAll(titleLbl, subtitleLbl);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button btnCloseX = new Button("✕");
            btnCloseX.getStyleClass().add("btn-outline");
            btnCloseX.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-cursor: hand;");
            btnCloseX.setOnAction(e -> {
                if (closeModalCallback != null) closeModalCallback.run();
            });

            modalHeader.getChildren().addAll(titleBox, spacer, btnCloseX);

            Label lblFeedback = new Label();
            lblFeedback.setWrapText(true);
            lblFeedback.setVisible(false);
            lblFeedback.setManaged(false);

            Button btnFermerModal = new Button("Fermer");
            btnFermerModal.getStyleClass().add("btn-outline");
            btnFermerModal.setOnAction(e -> {
                if (closeModalCallback != null) closeModalCallback.run();
            });

            btnOpenBrowser.setOnAction(e -> {
                try {
                    List<EtiquetteItem> items = fetchItems.get();
                    if (items.isEmpty()) {
                        lblFeedback.setText("⚠️ Aucun exemplaire physique ne correspond aux critères choisis.");
                        lblFeedback.setStyle("-fx-text-fill: #b45309; -fx-font-size: 12px; -fx-background-color: #fef3c7; -fx-padding: 8 12; -fx-background-radius: 6;");
                        lblFeedback.setVisible(true);
                        lblFeedback.setManaged(true);
                        return;
                    }
                    File tmp = File.createTempFile("etiquettes_bcd_", ".html");
                    int offset = spStartOffset.getValue() - 1;
                    boolean borders = chkBorders.isSelected();
                    BarcodeService.genererPlancheHtml(
                            items,
                            cbFormat.getValue(),
                            tfEnTete.getText().trim(),
                            chkTitle.isSelected(),
                            chkBarcode.isSelected(),
                            offset,
                            borders,
                            tmp
                    );
                    DialogHelper.openInBrowser(tmp);
                    lblFeedback.setText(String.format("✅ Planche de %d étiquette(s) ouverte dans votre navigateur web !\n💡 Conseil pour l'impression : réglez l'échelle sur 100%% (taille réelle) et marges sur Aucune.", items.size()));
                    lblFeedback.setStyle("-fx-text-fill: #166534; -fx-font-size: 12px; -fx-background-color: #dcfce7; -fx-padding: 8 12; -fx-background-radius: 6;");
                    lblFeedback.setVisible(true);
                    lblFeedback.setManaged(true);
                } catch (Exception ex) {
                    lblFeedback.setText("❌ Erreur : " + ex.getMessage());
                    lblFeedback.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 12px; -fx-background-color: #fee2e2; -fx-padding: 8 12; -fx-background-radius: 6;");
                    lblFeedback.setVisible(true);
                    lblFeedback.setManaged(true);
                }
            });

            btnSaveHtml.setOnAction(e -> {
                try {
                    List<EtiquetteItem> items = fetchItems.get();
                    if (items.isEmpty()) {
                        lblFeedback.setText("⚠️ Aucun exemplaire physique ne correspond aux critères choisis.");
                        lblFeedback.setStyle("-fx-text-fill: #b45309; -fx-font-size: 12px; -fx-background-color: #fef3c7; -fx-padding: 8 12; -fx-background-radius: 6;");
                        lblFeedback.setVisible(true);
                        lblFeedback.setManaged(true);
                        return;
                    }
                    FileChooser fc = new FileChooser();
                    fc.setTitle("Enregistrer la planche d'étiquettes");
                    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier HTML (*.html)", "*.html"));
                    fc.setInitialFileName("etiquettes_bcd.html");
                    File dest = fc.showSaveDialog(MainApp.getPrimaryStage());
                    if (dest != null) {
                        int offset = spStartOffset.getValue() - 1;
                        boolean borders = chkBorders.isSelected();
                        BarcodeService.genererPlancheHtml(
                                items,
                                cbFormat.getValue(),
                                tfEnTete.getText().trim(),
                                chkTitle.isSelected(),
                                chkBarcode.isSelected(),
                                offset,
                                borders,
                                dest
                        );
                        lblFeedback.setText(String.format("✅ Planche de %d étiquette(s) enregistrée avec succès : %s", items.size(), dest.getName()));
                        lblFeedback.setStyle("-fx-text-fill: #166534; -fx-font-size: 12px; -fx-background-color: #dcfce7; -fx-padding: 8 12; -fx-background-radius: 6;");
                        lblFeedback.setVisible(true);
                        lblFeedback.setManaged(true);
                    }
                } catch (Exception ex) {
                    lblFeedback.setText("❌ Erreur : " + ex.getMessage());
                    lblFeedback.setStyle("-fx-text-fill: #991b1b; -fx-font-size: 12px; -fx-background-color: #fee2e2; -fx-padding: 8 12; -fx-background-radius: 6;");
                    lblFeedback.setVisible(true);
                    lblFeedback.setManaged(true);
                }
            });

            HBox modalFooter = new HBox(12, btnSaveHtml, btnOpenBrowser, btnFermerModal);
            modalFooter.setAlignment(Pos.CENTER_RIGHT);

            ScrollPane scrollPane = new ScrollPane(content);
            scrollPane.setFitToWidth(true);
            scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
            scrollPane.setPrefHeight(450);
            scrollPane.setMaxHeight(520);

            modalCard.getChildren().setAll(
                    modalHeader,
                    new Separator(),
                    scrollPane,
                    lblFeedback,
                    new Separator(),
                    modalFooter
            );

            modalOverlay.setOnMouseClicked(e -> {
                if (e.getTarget() == modalOverlay && closeModalCallback != null) {
                    closeModalCallback.run();
                }
            });

            modalOverlay.setVisible(true);
            modalOverlay.setManaged(true);
            return;
        }

        // ── CAS 2 : Fallback Dialog fenêtré standard ──
        Dialog<ButtonType> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("🏷️ Générateur d'Étiquettes & Codes-barres");
        dialog.setHeaderText("Génération et impression de planches d'étiquettes prêtes à coller pour les livres.");
        ButtonType btnFermer = new ButtonType("Fermer", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(btnFermer);

        btnOpenBrowser.setOnAction(e -> {
            try {
                List<EtiquetteItem> items = fetchItems.get();
                if (items.isEmpty()) {
                    DialogHelper.showWarning("Aucun exemplaire", "Aucun exemplaire physique ne correspond aux critères choisis.");
                    return;
                }
                File tmp = File.createTempFile("etiquettes_bcd_", ".html");
                int offset = spStartOffset.getValue() - 1;
                boolean borders = chkBorders.isSelected();
                BarcodeService.genererPlancheHtml(
                        items,
                        cbFormat.getValue(),
                        tfEnTete.getText().trim(),
                        chkTitle.isSelected(),
                        chkBarcode.isSelected(),
                        offset,
                        borders,
                        tmp
                );
                DialogHelper.openInBrowser(tmp);
                DialogHelper.showInfo("Planche prête à imprimer",
                        String.format("La planche de %d étiquette(s) a été ouverte dans votre navigateur web.\n\n"
                        + "💡 Conseils pour l'impression :\n"
                        + "• Dans la fenêtre d'impression, réglez l'échelle sur 100%% (taille réelle).\n"
                        + "• Réglez les marges sur 'Aucune' ou 0 pour respecter le calibrage de la planche.\n"
                        + "• Décochez l'option 'En-têtes et pieds de page' pour un alignement parfait.", items.size()));
            } catch (Exception ex) {
                DialogHelper.showError("Erreur de génération", "Impossible de générer la planche d'étiquettes : " + ex.getMessage());
            }
        });

        btnSaveHtml.setOnAction(e -> {
            try {
                List<EtiquetteItem> items = fetchItems.get();
                if (items.isEmpty()) {
                    DialogHelper.showWarning("Aucun exemplaire", "Aucun exemplaire physique ne correspond aux critères choisis.");
                    return;
                }
                FileChooser fc = new FileChooser();
                fc.setTitle("Enregistrer la planche d'étiquettes");
                fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier HTML (*.html)", "*.html"));
                fc.setInitialFileName("etiquettes_bcd.html");
                File dest = fc.showSaveDialog(dialog.getOwner() != null ? dialog.getOwner() : MainApp.getPrimaryStage());
                if (dest != null) {
                    int offset = spStartOffset.getValue() - 1;
                    boolean borders = chkBorders.isSelected();
                    BarcodeService.genererPlancheHtml(
                            items,
                            cbFormat.getValue(),
                            tfEnTete.getText().trim(),
                            chkTitle.isSelected(),
                            chkBarcode.isSelected(),
                            offset,
                            borders,
                            dest
                    );
                    DialogHelper.showInfo("Export réussi", String.format("La planche de %d étiquette(s) a été enregistrée avec succès :\n%s", items.size(), dest.getAbsolutePath()));
                }
            } catch (Exception ex) {
                DialogHelper.showError("Erreur d'enregistrement", ex.getMessage());
            }
        });

        HBox btnRow = new HBox(12, btnSaveHtml, btnOpenBrowser);
        btnRow.setAlignment(Pos.CENTER_RIGHT);

        VBox dialogContent = new VBox(12, content, new Separator(), btnRow);
        dialog.getDialogPane().setContent(dialogContent);
        dialog.showAndWait();
    }
}
