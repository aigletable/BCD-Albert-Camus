package fr.ecole.bcd.model;

/**
 * Fiche bibliographique d'un ouvrage du catalogue de la BCD, identifiée de façon unique par son ISBN.
 * <p>
 * Mappe la table {@code livres} du schéma SQLite.
 * Les métadonnées (titre, auteur, éditeur, couverture, genre, niveau de lecture) proviennent
 * des connecteurs d'API (BNF, Google Books, OpenLibrary, Sudoc) ou d'une saisie manuelle.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class Livre {

    /** Identifiant ISBN unique (clé primaire, format ISBN-10 ou ISBN-13). */
    private String isbn;

    /** Titre officiel de l'ouvrage. */
    private String titre;

    /** Nom de l'auteur principal ou mention de responsabilité. */
    private String auteur;

    /** Maison d'édition ou éditeur jeunesse. */
    private String editeur;

    /** URL de l'image de couverture (haute résolution issue des APIs ou cache local). */
    private String couvertureUrl;

    /** Genre littéraire (ex. Roman, Bande dessinée, Album jeunesse, Documentaire, Conte, Théâtre, Poésie, Presse). */
    private String genre = "";

    /** Thématique pédagogique (ex. Animaux, Histoire, Science, Aventure, Harcèlement, Contes & Légendes). */
    private String theme = "";

    /** Niveau scolaire conseillé (ex. Maternelle, CP, CE1, CE2, CM1, CM2, Cycle 2, Cycle 3, Collège). */
    private String niveau = "";

    /** Résumé ou synopsis textuel de l'ouvrage. */
    private String description = "";

    /** Nombre total d'exemplaires physiques rattachés à cette fiche (calculé par requête jointe). */
    private int nbExemplaires = 0;

    /** Nombre d'exemplaires actuellement disponibles au prêt (calculé par requête jointe). */
    private int nbDisponibles = 0;

    /**
     * Constructeur vide sans argument (requis pour le mapping DAO et réflexif).
     */
    public Livre() { }

    /**
     * Constructeur de base initialisant les métadonnées bibliographiques essentielles.
     *
     * @param isbn code ISBN de l'ouvrage
     * @param titre titre du livre
     * @param auteur auteur principal
     * @param editeur éditeur
     * @param couvertureUrl URL de la jaquette
     */
    public Livre(String isbn, String titre, String auteur,
                 String editeur, String couvertureUrl) {
        this(isbn, titre, auteur, editeur, couvertureUrl, "", "", "", "");
    }

    /**
     * Constructeur complet incluant la classification pédagogique et la description.
     *
     * @param isbn code ISBN
     * @param titre titre du livre
     * @param auteur auteur principal
     * @param editeur maison d'édition
     * @param couvertureUrl lien vers l'image de couverture
     * @param genre genre littéraire
     * @param theme thème pédagogique
     * @param niveau cycle ou niveau de classe conseillé
     * @param description résumé / synopsis
     */
    public Livre(String isbn, String titre, String auteur,
                 String editeur, String couvertureUrl,
                 String genre, String theme, String niveau, String description) {
        this.isbn = isbn;
        this.titre = titre;
        this.auteur = auteur;
        this.editeur = editeur;
        this.couvertureUrl = couvertureUrl;
        this.genre = genre != null ? genre : "";
        this.theme = theme != null ? theme : "";
        this.niveau = niveau != null ? niveau : "";
        this.description = description != null ? description : "";
    }

    /**
     * Retourne l'identifiant ISBN de l'ouvrage.
     *
     * @return code ISBN
     */
    public String getIsbn() { return isbn; }

    /**
     * Définit l'identifiant ISBN de l'ouvrage.
     *
     * @param isbn nouveau code ISBN
     */
    public void setIsbn(String isbn) { this.isbn = isbn; }

    /**
     * Retourne le titre de l'ouvrage.
     *
     * @return le titre
     */
    public String getTitre() { return titre; }

    /**
     * Définit le titre de l'ouvrage.
     *
     * @param titre nouveau titre
     */
    public void setTitre(String titre) { this.titre = titre; }

    /**
     * Retourne le nom de l'auteur principal.
     *
     * @return le nom d'auteur
     */
    public String getAuteur() { return auteur; }

    /**
     * Définit le nom de l'auteur principal.
     *
     * @param auteur nouveau nom d'auteur
     */
    public void setAuteur(String auteur) { this.auteur = auteur; }

    /**
     * Retourne la maison d'édition.
     *
     * @return l'éditeur
     */
    public String getEditeur() { return editeur; }

    /**
     * Définit la maison d'édition.
     *
     * @param editeur nouvel éditeur
     */
    public void setEditeur(String editeur) { this.editeur = editeur; }

    /**
     * Retourne l'URL de l'image de couverture.
     *
     * @return lien image
     */
    public String getCouvertureUrl() { return couvertureUrl; }

    /**
     * Définit l'URL de l'image de couverture.
     *
     * @param couvertureUrl nouveau lien image
     */
    public void setCouvertureUrl(String couvertureUrl) { this.couvertureUrl = couvertureUrl; }

    /**
     * Retourne le genre littéraire de l'ouvrage.
     *
     * @return le genre (ex. {@code Roman}, {@code BD})
     */
    public String getGenre() { return genre != null ? genre : ""; }

    /**
     * Définit le genre littéraire de l'ouvrage.
     *
     * @param genre nouveau genre
     */
    public void setGenre(String genre) { this.genre = genre != null ? genre : ""; }

    /**
     * Retourne le thème pédagogique de l'ouvrage.
     *
     * @return le thème (ex. {@code Animaux}, {@code Histoire})
     */
    public String getTheme() { return theme != null ? theme : ""; }

    /**
     * Définit le thème pédagogique de l'ouvrage.
     *
     * @param theme nouveau thème
     */
    public void setTheme(String theme) { this.theme = theme != null ? theme : ""; }

    /**
     * Retourne le niveau de lecture scolaire conseillé.
     *
     * @return le niveau (ex. {@code Cycle 2}, {@code CM1})
     */
    public String getNiveau() { return niveau != null ? niveau : ""; }

    /**
     * Définit le niveau de lecture scolaire conseillé.
     *
     * @param niveau nouveau niveau
     */
    public void setNiveau(String niveau) { this.niveau = niveau != null ? niveau : ""; }

    /**
     * Retourne le résumé ou synopsis de l'ouvrage.
     *
     * @return la description textuelle
     */
    public String getDescription() { return description != null ? description : ""; }

    /**
     * Définit le résumé ou synopsis de l'ouvrage.
     *
     * @param description nouveau résumé
     */
    public void setDescription(String description) { this.description = description != null ? description : ""; }

    /**
     * Retourne le nombre total d'exemplaires physiques enregistrés en base.
     *
     * @return nombre total d'exemplaires
     */
    public int getNbExemplaires() { return nbExemplaires; }

    /**
     * Met à jour le nombre total d'exemplaires physiques associés.
     *
     * @param nbExemplaires total d'exemplaires
     */
    public void setNbExemplaires(int nbExemplaires) { this.nbExemplaires = nbExemplaires; }

    /**
     * Retourne le nombre d'exemplaires actuellement disponibles pour le prêt.
     *
     * @return nombre d'exemplaires disponibles
     */
    public int getNbDisponibles() { return nbDisponibles; }

    /**
     * Met à jour le nombre d'exemplaires actuellement disponibles.
     *
     * @param nbDisponibles nombre d'exemplaires disponibles
     */
    public void setNbDisponibles(int nbDisponibles) { this.nbDisponibles = nbDisponibles; }

    /**
     * Retourne une chaîne formatée indiquant la disponibilité sous forme de ratio (ex. {@code 2 / 3 dispo}).
     *
     * @return ratio de disponibilité pour l'affichage UI
     */
    public String getExemplairesRatio() {
        if (nbExemplaires == 0) {
            return "0 exemplaire";
        }
        return nbDisponibles + " / " + nbExemplaires + " dispo";
    }

    /**
     * Représentation textuelle de l'ouvrage pour les listes et sélecteurs (ex. {@code Titre — Auteur (ISBN)}).
     *
     * @return chaîne d'affichage
     */
    @Override
    public String toString() {
        String display = titre;
        if (auteur != null && !auteur.isBlank()) {
            display += " — " + auteur;
        }
        return display + " (" + isbn + ")";
    }
}
