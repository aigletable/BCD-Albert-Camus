package fr.ecole.bcd.model;

/**
 * Modèle de données immuable représentant un exemplaire physique pour la génération
 * et l'impression de planches d'étiquettes A4 et de codes-barres physiques (Code 39).
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class EtiquetteItem {

    /** Identifiant unique de l'exemplaire physique en base de données. */
    private final int exemplaireId;

    /** Code ISBN de l'ouvrage associé. */
    private final String isbn;

    /** Titre de l'ouvrage. */
    private final String titre;

    /** Nom de l'auteur principal. */
    private final String auteur;

    /** Maison d'édition de l'ouvrage. */
    private final String editeur;

    /** Remarque d'état ou avertissement associé à l'exemplaire. */
    private final String remarque;

    /**
     * Initialise une étiquette avec toutes les données bibliographiques nécessaires.
     *
     * @param exemplaireId identifiant physique unique
     * @param isbn code ISBN du livre
     * @param titre titre du livre
     * @param auteur auteur principal
     * @param editeur maison d'édition
     * @param remarque remarque d'état éventuelle
     */
    public EtiquetteItem(int exemplaireId, String isbn, String titre, String auteur, String editeur, String remarque) {
        this.exemplaireId = exemplaireId;
        this.isbn = isbn != null ? isbn : "";
        this.titre = titre != null ? titre : "Sans titre";
        this.auteur = auteur != null ? auteur : "";
        this.editeur = editeur != null ? editeur : "";
        this.remarque = remarque != null ? remarque : "";
    }

    /**
     * Retourne l'identifiant physique de l'exemplaire.
     *
     * @return le numéro d'exemplaire
     */
    public int getExemplaireId() {
        return exemplaireId;
    }

    /**
     * Retourne l'ISBN de l'ouvrage.
     *
     * @return le code ISBN
     */
    public String getIsbn() {
        return isbn;
    }

    /**
     * Retourne le titre de l'ouvrage.
     *
     * @return le titre
     */
    public String getTitre() {
        return titre;
    }

    /**
     * Retourne l'auteur de l'ouvrage.
     *
     * @return le nom d'auteur
     */
    public String getAuteur() {
        return auteur;
    }

    /**
     * Retourne l'éditeur de l'ouvrage.
     *
     * @return la maison d'édition
     */
    public String getEditeur() {
        return editeur;
    }

    /**
     * Retourne la remarque ou alerte d'état.
     *
     * @return la remarque textuelle
     */
    public String getRemarque() {
        return remarque;
    }

    /**
     * Retourne la valeur à encoder dans le code-barres scannable :
     * encode l'ISBN nettoyé de ses tirets en priorité, ou le numéro d'exemplaire en repli.
     *
     * @return chaîne alphanumérique scannable
     */
    public String getCodeBarreValue() {
        if (isbn != null && !isbn.isBlank()) {
            String clean = isbn.replaceAll("[^0-9X]", "");
            if (!clean.isEmpty()) {
                return clean;
            }
        }
        return String.valueOf(exemplaireId);
    }

    /**
     * Retourne le libellé textuel formaté imprimé sous le code-barres (ex. {@code Ex. #42}).
     *
     * @return libellé formaté
     */
    public String getLibelleExemplaire() {
        return "Ex. #" + exemplaireId;
    }
}
