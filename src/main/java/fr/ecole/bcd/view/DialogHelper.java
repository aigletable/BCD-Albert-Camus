package fr.ecole.bcd.view;

import fr.ecole.bcd.MainApp;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.io.File;
import java.util.Optional;

/**
 * Utilitaires partagés pour l'affichage de boîtes de dialogue standardisées dans l'application BCD Express.
 * <p>
 * Cette classe simplifie et harmonise l'affichage des fenêtres modales JavaFX
 * (erreurs, avertissements, informations et confirmations), tout en appliquant le thème visuel
 * et les styles CSS configurés via {@link MainApp#prepareDialog(javafx.scene.control.Dialog)}.
 * Elle prend également en charge l'ouverture de fichiers bureautiques ou de rapports dans
 * le navigateur ou visualiseur système par défaut.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class DialogHelper {

    /**
     * Constructeur privé pour empêcher l'instanciation de cette classe utilitaire.
     */
    private DialogHelper() {
        // Classe utilitaire
    }

    /**
     * Affiche une boîte de dialogue modale d'erreur bloquante.
     *
     * @param title   le titre de la fenêtre modale
     * @param content le texte descriptif de l'erreur rencontrée
     */
    public static void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        MainApp.prepareDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Affiche une boîte de dialogue modale d'avertissement.
     *
     * @param title   le titre de la fenêtre modale
     * @param content le message d'avertissement ou de précaution pour l'utilisateur
     */
    public static void showWarning(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        MainApp.prepareDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Affiche une boîte de dialogue modale informative standard.
     *
     * @param title   le titre de la fenêtre d'information
     * @param content le message d'information ou de confirmation d'action
     */
    public static void showInfo(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * Affiche une boîte de dialogue modale demandant une confirmation explicite à l'utilisateur.
     *
     * @param title   le titre de la boîte de confirmation
     * @param header  le sous-titre ou question principale (peut être {@code null})
     * @param content le texte explicatif ou les conséquences de la validation
     * @return {@code true} si l'utilisateur a cliqué sur OK, {@code false} sinon (ou si la boîte a été fermée)
     */
    public static boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        MainApp.prepareDialog(alert);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * Ouvre un fichier (rapport PDF, fichier HTML, feuille de calcul, etc.) avec l'application par défaut
     * associée dans le système d'exploitation de l'hôte.
     *
     * @param file le fichier local à afficher
     */
    public static void openInBrowser(File file) {
        MainApp.openDocument(file);
    }
}
