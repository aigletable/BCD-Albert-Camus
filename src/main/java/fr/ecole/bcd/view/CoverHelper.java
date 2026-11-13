package fr.ecole.bcd.view;

import fr.ecole.bcd.model.Livre;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/**
 * Générateur de couvertures de livres stylisées façon « Apple Books / Liseuse »
 * pour les ouvrages qui ne disposent pas d'image de couverture sur internet.
 *
 * <p>Caractéristiques graphiques :</p>
 * <ul>
 *   <li>Dégradé de couleur harmonieux adapté au Genre littéraire (BD, Roman, Album, Conte...).</li>
 *   <li>Reliure et tranche de livre texturée avec rainure d'ouverture (effet 3D).</li>
 *   <li>Typographie contrastée blanche avec picto thématique et nom d'auteur.</li>
 *   <li>Ombre portée tridimensionnelle douce.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-11-12
 */
public final class CoverHelper {

    private CoverHelper() {
        // Classe utilitaire
    }

    /**
     * Palette de couleurs par genre littéraire.
     */
    public record GenreStyle(String icon, Color topColor, Color botColor, String badgeClass) {}

    public static GenreStyle getStyleForGenre(String genre) {
        if (genre == null || genre.isBlank()) {
            return new GenreStyle("📖", Color.web("#475569"), Color.web("#1E293B"), "badge-genre-autre");
        }
        String g = genre.toLowerCase().trim();
        if (g.contains("bd") || g.contains("bande dessinée") || g.contains("comics")) {
            return new GenreStyle("🎨", Color.web("#F59E0B"), Color.web("#D97706"), "badge-genre-bd");
        } else if (g.contains("roman")) {
            return new GenreStyle("📘", Color.web("#2563EB"), Color.web("#1D4ED8"), "badge-genre-roman");
        } else if (g.contains("album")) {
            return new GenreStyle("🧸", Color.web("#10B981"), Color.web("#059669"), "badge-genre-album");
        } else if (g.contains("conte") || g.contains("mythe") || g.contains("magie")) {
            return new GenreStyle("🏰", Color.web("#8B5CF6"), Color.web("#6D28D9"), "badge-genre-conte");
        } else if (g.contains("documentaire") || g.contains("science") || g.contains("histoire")) {
            return new GenreStyle("🔬", Color.web("#0284C7"), Color.web("#0369A1"), "badge-genre-documentaire");
        } else if (g.contains("revue") || g.contains("magazine") || g.contains("presse")) {
            return new GenreStyle("📰", Color.web("#F43F5E"), Color.web("#E11D48"), "badge-genre-revue");
        } else if (g.contains("manga")) {
            return new GenreStyle("⚡", Color.web("#EA580C"), Color.web("#C2410C"), "badge-genre-manga");
        } else if (g.contains("poésie") || g.contains("théâtre")) {
            return new GenreStyle("🎭", Color.web("#D946EF"), Color.web("#A21CAF"), "badge-genre-poesie");
        }
        return new GenreStyle("📖", Color.web("#475569"), Color.web("#1E293B"), "badge-genre-autre");
    }

    /**
     * Construit une couverture stylisée vectorielle avec effet de livre réaliste.
     *
     * @param livre  le livre source
     * @param width  largeur souhaitée
     * @param height hauteur souhaitée
     * @return un composant {@link Node} prêt à être affiché
     */
    public static StackPane createStyledCover(Livre livre, double width, double height) {
        String genre = livre != null ? livre.getGenre() : "";
        String titre = livre != null && livre.getTitre() != null ? livre.getTitre() : "Livre BCD";
        String auteur = livre != null && livre.getAuteur() != null ? livre.getAuteur() : "";

        GenreStyle style = getStyleForGenre(genre);

        // Fond avec dégradé subtil
        Rectangle bg = new Rectangle(width, height);
        bg.setArcWidth(10);
        bg.setArcHeight(10);
        LinearGradient grad = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, style.topColor()),
                new Stop(1, style.botColor())
        );
        bg.setFill(grad);

        // Tranche gauche (reliure du livre)
        Rectangle spine = new Rectangle(Math.max(4, width * 0.08), height);
        spine.setArcWidth(4);
        spine.setArcHeight(4);
        LinearGradient spineGrad = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, 0.45)),
                new Stop(0.7, Color.rgb(0, 0, 0, 0.15)),
                new Stop(1, Color.rgb(255, 255, 255, 0.15))
        );
        spine.setFill(spineGrad);
        StackPane.setAlignment(spine, Pos.CENTER_LEFT);

        // Bordure dorée / claire très fine
        Rectangle innerBorder = new Rectangle(width - 6, height - 6);
        innerBorder.setArcWidth(8);
        innerBorder.setArcHeight(8);
        innerBorder.setFill(Color.TRANSPARENT);
        innerBorder.setStroke(Color.rgb(255, 255, 255, 0.25));
        innerBorder.setStrokeWidth(1);

        // Contenu texte & picto
        VBox textBox = new VBox(height > 100 ? 6 : 2);
        textBox.setAlignment(Pos.CENTER);
        textBox.setPadding(new Insets(height > 100 ? 12 : 6, 8, height > 100 ? 12 : 6, Math.max(8, width * 0.12)));

        Label iconLbl = new Label(style.icon());
        iconLbl.setStyle("-fx-font-size: " + (height > 100 ? "26px" : "15px") + "; -fx-text-fill: white;");

        Label titleLbl = new Label(titre);
        titleLbl.setWrapText(true);
        titleLbl.setAlignment(Pos.CENTER);
        titleLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        titleLbl.setStyle("-fx-font-weight: 800; -fx-text-fill: white; " +
                "-fx-font-size: " + (height > 100 ? "11.5px" : "9px") + "; -fx-line-spacing: -1px;");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Label authorLbl = new Label(auteur.isBlank() ? "Albert Camus" : auteur);
        authorLbl.setWrapText(true);
        authorLbl.setAlignment(Pos.CENTER);
        authorLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        authorLbl.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-size: " + (height > 100 ? "9.5px" : "7.5px") + "; -fx-font-weight: 600;");

        if (height > 100) {
            textBox.getChildren().addAll(iconLbl, titleLbl, spacer, authorLbl);
        } else {
            textBox.getChildren().addAll(iconLbl, titleLbl);
        }

        StackPane root = new StackPane(bg, spine, innerBorder, textBox);
        root.setMaxSize(width, height);
        root.setMinSize(width, height);
        root.setPrefSize(width, height);

        // Ombre 3D douce
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(15, 23, 42, 0.22));
        shadow.setRadius(height > 100 ? 12 : 6);
        shadow.setOffsetX(height > 100 ? 2 : 1);
        shadow.setOffsetY(height > 100 ? 4 : 2);
        root.setEffect(shadow);

        return root;
    }
}

