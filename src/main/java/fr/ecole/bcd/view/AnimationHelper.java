 package fr.ecole.bcd.view;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * Utilitaire d'animations visuelles pour les retours de scan à la douchette et les interactions rapides.
 * <p>
 * Cette classe fournit un ensemble de micro-animations modernes, fluides et non intrusives, conçues pour
 * offrir un feedback tactile et visuel instantané à l'utilisateur lors des opérations de scan,
 * d'enregistrement ou d'anomalie :
 * </p>
 * <ul>
 *   <li><b>flashSuccess</b> : impulsion lumineuse vert émeraude accompagnée d'un micro-rebond doux (scale 1.025).</li>
 *   <li><b>flashError / shakeError</b> : halo rouge vif associé à une secousse horizontale ("shake") dynamique.</li>
 *   <li><b>flashWarning</b> : halo d'avertissement ambre / orange chaleureux.</li>
 * </ul>
 * <p>
 * Toutes les méthodes sont thread-safe : si elles sont invoquées depuis un thread d'arrière-plan
 * (par exemple lors d'un appel réseau ou asynchrone), elles basculent automatiquement sur
 * le JavaFX Application Thread via {@link Platform#runLater(Runnable)}.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class AnimationHelper {

    /**
     * Clé de propriété JavaFX utilisée pour mémoriser l'effet visuel original du composant avant animation.
     */
    private static final String PREV_EFFECT_KEY = "bcd_prev_effect";

    /**
     * Constructeur privé empêchant l'instanciation de cette classe utilitaire.
     */
    private AnimationHelper() {
        // Classe utilitaire
    }

    /**
     * Déclenche une animation de succès sur le composant spécifié (halo vert lumineux et micro-rebond d'échelle).
     *
     * @param node le nœud JavaFX à animer (ex. un champ de saisie, un bouton ou une carte) ; ignoré si {@code null}
     */
    public static void flashSuccess(Node node) {
        if (node == null) return;
        runOnFxThread(() -> {
            // 1. Halo vert lumineux
            DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(34, 197, 94, 0.85), 14, 0.4, 0, 0);
            applyTemporaryEffect(node, glow, Duration.millis(350));

            // 2. Micro-rebond (échelle)
            ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
            st.setFromX(1.0);
            st.setFromY(1.0);
            st.setToX(1.025);
            st.setToY(1.025);
            st.setAutoReverse(true);
            st.setCycleCount(2);
            st.play();
        });
    }

    /**
     * Déclenche une animation d'erreur sur le composant spécifié (secousse horizontale vive et halo d'alerte rouge).
     *
     * @param node le nœud JavaFX à animer (ex. champ de texte avec code-barres non reconnu) ; ignoré si {@code null}
     */
    public static void flashError(Node node) {
        if (node == null) return;
        runOnFxThread(() -> {
            // 1. Halo rouge alerte
            DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(239, 68, 68, 0.9), 16, 0.5, 0, 0);
            applyTemporaryEffect(node, glow, Duration.millis(420));

            // 2. Secousse horizontale (shake)
            double origX = node.getTranslateX();
            TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
            tt.setFromX(origX);
            tt.setByX(-8);
            tt.setAutoReverse(true);
            tt.setCycleCount(6);
            tt.setOnFinished(e -> node.setTranslateX(origX));
            tt.play();
        });
    }

    /**
     * Déclenche une animation d'avertissement sur le composant spécifié (halo ambre doux et progressif).
     *
     * @param node le nœud JavaFX à animer ; ignoré si {@code null}
     */
    public static void flashWarning(Node node) {
        if (node == null) return;
        runOnFxThread(() -> {
            DropShadow glow = new DropShadow(BlurType.GAUSSIAN, Color.rgb(245, 158, 11, 0.85), 14, 0.45, 0, 0);
            applyTemporaryEffect(node, glow, Duration.millis(380));
        });
    }

    /**
     * Applique un effet graphique temporaire qui s'estompe progressivement via une timeline JavaFX,
     * puis rétablit l'effet d'origine du composant.
     *
     * @param node     le nœud JavaFX ciblé
     * @param glow     l'ombre portée (DropShadow) représentant le halo lumineux
     * @param duration la durée totale de transition de l'effet
     */
    private static void applyTemporaryEffect(Node node, DropShadow glow, Duration duration) {
        Effect originalEffect = node.getEffect();
        if (originalEffect != null && !node.getProperties().containsKey(PREV_EFFECT_KEY)) {
            node.getProperties().put(PREV_EFFECT_KEY, originalEffect);
        }

        node.setEffect(glow);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(glow.radiusProperty(), glow.getRadius()),
                        new KeyValue(glow.spreadProperty(), glow.getSpread())
                ),
                new KeyFrame(duration,
                        new KeyValue(glow.radiusProperty(), 0),
                        new KeyValue(glow.spreadProperty(), 0)
                )
        );

        timeline.setOnFinished(e -> {
            if (node.getProperties().containsKey(PREV_EFFECT_KEY)) {
                node.setEffect((Effect) node.getProperties().remove(PREV_EFFECT_KEY));
            } else {
                node.setEffect(null);
            }
        });

        timeline.play();
    }

    /**
     * Exécute une tâche sur le thread d'interface JavaFX, soit immédiatement si le thread appelant
     * est déjà l'UI thread, soit en l'ordonnançant via {@link Platform#runLater(Runnable)}.
     *
     * @param r l'action à exécuter
     */
    private static void runOnFxThread(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
