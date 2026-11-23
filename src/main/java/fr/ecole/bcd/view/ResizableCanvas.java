package fr.ecole.bcd.view;

import javafx.scene.canvas.Canvas;

/**
 * Canvas JavaFX redimensionnable de manière réactive par son conteneur parent.
 *
 * <p>Contrairement au {@link Canvas} standard dont la taille préférée est calquée
 * en dur sur sa largeur/hauteur interne (ce qui provoque une boucle infinie
 * d'agrandissement en chaîne dans les conteneurs flexibles comme StackPane et VBox),
 * {@code ResizableCanvas} délègue entièrement son dimensionnement à son parent.</p>
 *
 * @author JC ART
 * @since 2026-11-23
 */
public class ResizableCanvas extends Canvas {

    public ResizableCanvas() {
        super();
    }

    public ResizableCanvas(double width, double height) {
        super(width, height);
    }

    @Override
    public boolean isResizable() {
        return true;
    }

    @Override
    public double prefWidth(double height) {
        return 100;
    }

    @Override
    public double prefHeight(double width) {
        return 100;
    }

    @Override
    public double minWidth(double height) {
        return 50;
    }

    @Override
    public double minHeight(double width) {
        return 50;
    }

    @Override
    public double maxWidth(double height) {
        return 4000;
    }

    @Override
    public double maxHeight(double width) {
        return 4000;
    }

    @Override
    public void resize(double width, double height) {
        super.setWidth(width);
        super.setHeight(height);
    }
}
