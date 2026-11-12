package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import fr.ecole.bcd.service.BackupService;
import fr.ecole.bcd.service.SoundService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Boîte de dialogue modale pour la gestion des sauvegardes et de la restauration de la base de données SQLite.
 * <p>
 * Accessible depuis le bandeau supérieur de l'application, ce composant permet aux gestionnaires de la BCD :
 * </p>
 * <ul>
 *   <li>De consulter l'état actuel et les statistiques de la base active (taille, nombre de livres, etc.).</li>
 *   <li>De générer des instantanés horodatés instantanés dans le dossier local de secours.</li>
 *   <li>D'exporter une sauvegarde complète sur support amovible (clé USB, disque dur externe).</li>
 *   <li>De visualiser l'historique des sauvegardes existantes avec suppression ou restauration directe.</li>
 *   <li>D'exécuter une restauration sécurisée (avec création automatique préalable d'une copie de précaution).</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class BackupDialogHelper {

    /**
     * Instance partagée du service métier de sauvegarde et de restauration.
     */
    private static final BackupService backupService = new BackupService();

    /**
     * Constructeur privé pour empêcher l'instanciation directe de cette classe utilitaire.
     */
    private BackupDialogHelper() {
        // Classe utilitaire
    }

    /**
     * Ouvre la fenêtre modale de gestion des sauvegardes et de restauration.
     *
     * @param onRestoreCallback action de rappel à exécuter après une restauration réussie
     *                          (permet de recharger les catalogues et utilisateurs en mémoire vive)
     */
    public static void openSauvegardeDialog(Runnable onRestoreCallback) {
        Dialog<Void> dialog = new Dialog<>();
        MainApp.prepareDialog(dialog);
        dialog.setTitle("Sauvegardes & Restauration");
        dialog.setHeaderText("Gestion des sauvegardes de la base de données BCD Express");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox root = new VBox(14);
        root.setPadding(new Insets(10));
        root.setPrefWidth(650);

        // Carte Stats BCD actuelle
        VBox statsBox = new VBox(6);
        statsBox.setStyle("-fx-background-color: -color-bg-subtle; -fx-padding: 12px; -fx-background-radius: 8px; -fx-border-color: -color-border-subtle; -fx-border-radius: 8px;");
        Label lblStatTitle = new Label("📊 État de la base active");
        lblStatTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label lblStatContent = new Label(backupService.calculerStatistiquesBase());
        lblStatContent.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-muted;");
        statsBox.getChildren().addAll(lblStatTitle, lblStatContent);

        // Actions rapides de sauvegarde
        HBox actionsRow = new HBox(12);
        actionsRow.setAlignment(Pos.CENTER_LEFT);

        Button btnSaveNow = new Button("💾 Sauvegarder maintenant");
        btnSaveNow.getStyleClass().addAll("btn-blue");

        Button btnSaveUsb = new Button("📁 Exporter vers Clé USB / Dossier...");
        btnSaveUsb.getStyleClass().addAll("btn-outline");

        Button btnOpenFolder = new Button("📂 Ouvrir dossier sauvegardes");
        btnOpenFolder.getStyleClass().addAll("btn-outline");

        actionsRow.getChildren().addAll(btnSaveNow, btnSaveUsb, btnOpenFolder);

        // Table des sauvegardes existantes
        Label lblHist = new Label("📦 Historique des sauvegardes disponibles (dossier backups/) :");
        lblHist.setStyle("-fx-font-weight: bold; -fx-padding: 6px 0 2px 0;");

        ObservableList<BackupService.BackupItem> backupList = FXCollections.observableArrayList();
        TableView<BackupService.BackupItem> table = new TableView<>(backupList);
        table.setPrefHeight(220);
        table.setPlaceholder(new Label("Aucune sauvegarde trouvée dans le dossier backups/."));

        TableColumn<BackupService.BackupItem, String> colNom = new TableColumn<>("Fichier de sauvegarde");
        colNom.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getNom()));
        colNom.setPrefWidth(280);

        TableColumn<BackupService.BackupItem, String> colDate = new TableColumn<>("Date / Heure");
        colDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDateFormatee()));
        colDate.setPrefWidth(140);
        colDate.setStyle("-fx-alignment: CENTER;");

        TableColumn<BackupService.BackupItem, String> colSize = new TableColumn<>("Taille");
        colSize.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTailleFormatee()));
        colSize.setPrefWidth(90);
        colSize.setStyle("-fx-alignment: CENTER_RIGHT;");

        table.getColumns().addAll(colNom, colDate, colSize);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        Runnable refreshList = () -> {
            backupList.setAll(backupService.listerSauvegardes());
            lblStatContent.setText(backupService.calculerStatistiquesBase());
        };
        refreshList.run();

        // Boutons sous le tableau
        HBox tableBtnRow = new HBox(10);
        tableBtnRow.setAlignment(Pos.CENTER_RIGHT);

        Button btnRestore = new Button("↩️ Restaurer la sélection");
        btnRestore.getStyleClass().addAll("btn-warning");
        btnRestore.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Button btnDeleteBackup = new Button("🗑️ Supprimer");
        btnDeleteBackup.getStyleClass().addAll("btn-danger-outline");
        btnDeleteBackup.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        tableBtnRow.getChildren().addAll(btnDeleteBackup, btnRestore);

        // Handlers
        btnSaveNow.setOnAction(e -> {
            try {
                File f = backupService.creerSauvegardeAutomatique();
                SoundService.playSuccessSound();
                showAlert(Alert.AlertType.INFORMATION, "Sauvegarde réussie", "✅ Sauvegarde créée avec succès :\n" + f.getName() + " (" + (f.length() / 1024) + " Ko)");
                refreshList.run();
            } catch (SQLException ex) {
                SoundService.playProblemSound();
                showAlert(Alert.AlertType.ERROR, "Erreur de sauvegarde", ex.getMessage());
            }
        });

        btnSaveUsb.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choisir l'emplacement de sauvegarde");
            fc.setInitialFileName("bcd_backup_" + LocalDate.now() + ".db");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base SQLite (*.db)", "*.db"));
            File chosen = fc.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
            if (chosen != null) {
                try {
                    backupService.creerSauvegardeVers(chosen);
                    SoundService.playSuccessSound();
                    showAlert(Alert.AlertType.INFORMATION, "Sauvegarde exportée", "✅ Sauvegarde enregistrée avec succès sur :\n" + chosen.getAbsolutePath());
                    refreshList.run();
                } catch (SQLException ex) {
                    SoundService.playProblemSound();
                    showAlert(Alert.AlertType.ERROR, "Erreur de sauvegarde", ex.getMessage());
                }
            }
        });

        btnOpenFolder.setOnAction(e -> {
            File dir = backupService.getBackupDirectory();
            if (dir.exists()) {
                MainApp.openDocument(dir);
            }
        });

        btnDeleteBackup.setOnAction(e -> {
            BackupService.BackupItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getFile() != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(confirm);
                confirm.setTitle("Supprimer la sauvegarde");
                confirm.setHeaderText("Supprimer définitivement ce fichier ?");
                confirm.setContentText(sel.getNom());
                if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) {
                    sel.getFile().delete();
                    refreshList.run();
                }
            }
        });

        btnRestore.setOnAction(e -> {
            BackupService.BackupItem sel = table.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getFile() != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                MainApp.prepareDialog(confirm);
                confirm.setTitle("⚠️ Confirmer la restauration");
                confirm.setHeaderText("Attention : la base de données actuelle va être remplacée !");
                confirm.setContentText("Voulez-vous restaurer la sauvegarde suivante ?\n\n" + sel.getNom() +
                        "\n\nUne copie de précaution de l'état actuel sera automatiquement créée avant la restauration.");
                if (confirm.showAndWait().filter(b -> b == ButtonType.OK).isPresent()) {
                    try {
                        backupService.restaurerSauvegarde(sel.getFile());
                        SoundService.playSuccessSound();
                        showAlert(Alert.AlertType.INFORMATION, "Restauration effectuée", "✅ La base de données a été restaurée avec succès !\nLe catalogue et les utilisateurs vont être rechargés.");
                        if (onRestoreCallback != null) {
                            onRestoreCallback.run();
                        }
                        dialog.close();
                    } catch (Exception ex) {
                        SoundService.playProblemSound();
                        showAlert(Alert.AlertType.ERROR, "Erreur de restauration", ex.getMessage());
                    }
                }
            }
        });

        root.getChildren().addAll(statsBox, actionsRow, new Separator(), lblHist, table, tableBtnRow);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    /**
     * Affiche une alerte stylisée préparée avec le thème global de l'application.
     *
     * @param type    le type d'alerte (INFORMATION, WARNING, ERROR, etc.)
     * @param title   le titre de la fenêtre d'alerte
     * @param content le texte descriptif du corps de l'alerte
     */
    private static void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        MainApp.prepareDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
