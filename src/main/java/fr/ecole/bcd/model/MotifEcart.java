package fr.ecole.bcd.model;

/**
 * Énumération des motifs de justification pour les écarts d'inventaire (+ ou -).
 * <p>
 * Inspiré des pratiques professionnelles de gestion de stock en bibliothèque :
 * permet de catégoriser chaque exemplaire manquant, dégradé ou retrouvé en surplus
 * lors des campagnes de récolement physique.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public enum MotifEcart {

    /** Aucun motif spécifique d'écart n'est renseigné. */
    AUCUN("– Aucun motif –", "aucun"),

    /** Exemplaire perdu par un élève ou un usager de la BCD. */
    PERDU("⚠️ Perdu par un élève / usager", "perdu"),

    /** Ouvrage détérioré, mis au pilon ou désherbé. */
    PILON("🗑️ Dégradé / Mis au pilon", "pilon"),

    /** Ouvrage retiré temporairement pour reliure ou réparation. */
    REPARATION("🔧 En reliure / En cours de réparation", "reparation"),

    /** Disparition inexpliquée nécessitant des recherches complémentaires. */
    INEXPLIQUE("❓ Manquant inexpliqué (Recherche requise)", "inexplique"),

    /** Exemplaire physique retrouvé en rayon lors du pointage. */
    RETROUVE("✨ Exemplaire retrouvé en surplus", "retrouve");

    /** Libellé convivial affiché dans l'interface graphique. */
    private final String libelle;

    /** Code technique stocké en base de données SQLite. */
    private final String code;

    /**
     * Constructeur interne de l'énumération.
     *
     * @param libelle libellé complet pour l'affichage UI
     * @param code identifiant technique court
     */
    MotifEcart(String libelle, String code) {
        this.libelle = libelle;
        this.code = code;
    }

    /**
     * Retourne le libellé lisible du motif d'écart.
     *
     * @return le libellé d'affichage
     */
    public String getLibelle() {
        return libelle;
    }

    /**
     * Retourne le code technique du motif d'écart.
     *
     * @return le code textuel
     */
    public String getCode() {
        return code;
    }

    /**
     * Résout un motif d'écart à partir de son code ou nom technique.
     *
     * @param code le code technique ou nom d'énumération
     * @return l'instance correspondante de {@link MotifEcart}, ou {@link #AUCUN} par défaut
     */
    public static MotifEcart fromCode(String code) {
        if (code == null || code.isBlank()) return AUCUN;
        for (MotifEcart m : values()) {
            if (m.code.equalsIgnoreCase(code) || m.name().equalsIgnoreCase(code)) {
                return m;
            }
        }
        return AUCUN;
    }

    /**
     * Représentation textuelle du motif pour les composants JavaFX.
     *
     * @return le libellé d'affichage
     */
    @Override
    public String toString() {
        return libelle;
    }
}
