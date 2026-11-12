package fr.ecole.bcd.view;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Composant de présentation chaleureux et visuel (« Empty States ») pour les listes
 * et tableaux vides (recherches infructueuses, zéro retard, panier vide, etc.).
 *
 * @author JC ART
 * @since 2026-11-12
 */
public final class EmptyStateHelper {

    private EmptyStateHelper() {
        // Classe utilitaire
    }

    /**
     * Crée un composant d'état vide avec icône, titre bienveillant et explication.
     *
     * @param icon      grand émoji ou picto (ex: "🎉", "🔍", "🧺")
     * @param titre     titre principal encourageant
     * @param sousTitre conseil ou précision pédagogique
     * @return un {@link VBox} stylisé prêt à être passé à {@code TableView.setPlaceholder()}
     */
    public static VBox create(String icon, String titre, String sousTitre) {
        VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(24, 20, 24, 20));

        Label lblIcon = new Label(icon);
        lblIcon.setStyle("-fx-font-size: 38px; -fx-padding: 0 0 4px 0;");

        Label lblTitre = new Label(titre);
        lblTitre.setStyle("-fx-font-weight: 700; -fx-font-size: 15px; -fx-text-fill: #1E293B;");

        Label lblSousTitre = new Label(sousTitre);
        lblSousTitre.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #64748B; -fx-wrap-text: true; -fx-text-alignment: center;");
        lblSousTitre.setMaxWidth(380);

        box.getChildren().addAll(lblIcon, lblTitre, lblSousTitre);
        return box;
    }
}
