package fr.ecole.bcd.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Entité représentant l'association d'un exemplaire physique à une mallette pédagogique.
 * <p>
 * Mappe la table {@code mallette_items} avec les propriétés jointes du livre
 * (titre, auteur, genre, statut physique) pour faciliter l'affichage et la génération des fiches A4.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class MalletteItem {

    /** Formateur de date standard pour l'affichage en tableau (dd/MM/yyyy). */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Identifiant unique de l'association (clé primaire). */
    private int id;

    /** Identifiant de la mallette conteneur. */
    private int malletteId;

    /** Identifiant physique de l'exemplaire intégré. */
    private int exemplaireId;

    /** Date d'insertion de l'exemplaire dans la mallette. */
    private LocalDate dateAjout = LocalDate.now();

    /** Code ISBN du livre associé. */
    private String isbn;

    /** Titre du livre (champ joint). */
    private String titre;

    /** Auteur du livre (champ joint). */
    private String auteur;

    /** Genre littéraire du livre (champ joint). */
    private String genre;

    /** Thématique du livre (champ joint). */
    private String theme;

    /** Statut de disponibilité de l'exemplaire (champ joint). */
    private Exemplaire.Statut statutExemplaire = Exemplaire.Statut.DISPONIBLE;

    /**
     * Constructeur par défaut sans argument (requis pour le DAO).
     */
    public MalletteItem() {}

    /**
     * Constructeur complet initialisant l'élément avec ses métadonnées jointes.
     *
     * @param malletteId identifiant de la mallette
     * @param exemplaireId identifiant physique de l'exemplaire
     * @param isbn code ISBN
     * @param titre titre du livre
     * @param auteur auteur du livre
     * @param genre genre littéraire
     * @param theme thématique pédagogique
     * @param statut statut de disponibilité
     */
    public MalletteItem(int malletteId, int exemplaireId, String isbn, String titre, String auteur, String genre, String theme, Exemplaire.Statut statut) {
        this.malletteId = malletteId;
        this.exemplaireId = exemplaireId;
        this.isbn = isbn;
        this.titre = titre;
        this.auteur = auteur != null ? auteur : "";
        this.genre = genre != null ? genre : "";
        this.theme = theme != null ? theme : "";
        this.statutExemplaire = statut != null ? statut : Exemplaire.Statut.DISPONIBLE;
        this.dateAjout = LocalDate.now();
    }

    /**
     * Retourne l'identifiant de la ligne d'association.
     *
     * @return identifiant unique
     */
    public int getId() { return id; }

    /**
     * Définit l'identifiant de l'association.
     *
     * @param id identifiant
     */
    public void setId(int id) { this.id = id; }

    /**
     * Retourne l'identifiant de la mallette parente.
     *
     * @return identifiant mallette
     */
    public int getMalletteId() { return malletteId; }

    /**
     * Définit l'identifiant de la mallette parente.
     *
     * @param malletteId identifiant mallette
     */
    public void setMalletteId(int malletteId) { this.malletteId = malletteId; }

    /**
     * Retourne l'identifiant physique de l'exemplaire.
     *
     * @return numéro d'exemplaire
     */
    public int getExemplaireId() { return exemplaireId; }

    /**
     * Définit l'identifiant physique de l'exemplaire.
     *
     * @param exemplaireId numéro d'exemplaire
     */
    public void setExemplaireId(int exemplaireId) { this.exemplaireId = exemplaireId; }

    /**
     * Retourne la date d'ajout de l'exemplaire à la mallette.
     *
     * @return date d'ajout
     */
    public LocalDate getDateAjout() { return dateAjout; }

    /**
     * Définit la date d'ajout de l'exemplaire à la mallette.
     *
     * @param dateAjout date d'ajout
     */
    public void setDateAjout(LocalDate dateAjout) { this.dateAjout = dateAjout; }

    /**
     * Retourne le code ISBN du livre.
     *
     * @return code ISBN
     */
    public String getIsbn() { return isbn; }

    /**
     * Définit le code ISBN du livre.
     *
     * @param isbn code ISBN
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
     * Retourne l'auteur de l'ouvrage.
     *
     * @return nom d'auteur
     */
    public String getAuteur() { return auteur; }

    /**
     * Définit l'auteur de l'ouvrage.
     *
     * @param auteur nouvel auteur
     */
    public void setAuteur(String auteur) { this.auteur = auteur; }

    /**
     * Retourne le genre littéraire de l'ouvrage.
     *
     * @return genre textuel
     */
    public String getGenre() { return genre; }

    /**
     * Définit le genre littéraire de l'ouvrage.
     *
     * @param genre genre textuel
     */
    public void setGenre(String genre) { this.genre = genre; }

    /**
     * Retourne le thème pédagogique de l'ouvrage.
     *
     * @return thème
     */
    public String getTheme() { return theme; }

    /**
     * Définit le thème pédagogique de l'ouvrage.
     *
     * @param theme thème
     */
    public void setTheme(String theme) { this.theme = theme; }

    /**
     * Retourne le statut de circulation de l'exemplaire.
     *
     * @return statut disponible ou emprunté
     */
    public Exemplaire.Statut getStatutExemplaire() { return statutExemplaire; }

    /**
     * Définit le statut de circulation de l'exemplaire.
     *
     * @param statutExemplaire statut
     */
    public void setStatutExemplaire(Exemplaire.Statut statutExemplaire) { this.statutExemplaire = statutExemplaire; }

    /**
     * Retourne la date d'ajout formatée pour l'affichage UI (ex. {@code 12/10/2026}).
     *
     * @return date formatée
     */
    public String getDateAjoutFormatted() {
        return dateAjout != null ? dateAjout.format(DATE_FMT) : "–";
    }

    /**
     * Représentation lisible de l'élément (ex. {@code #42 - Harry Potter (J.K. Rowling)}).
     *
     * @return libellé textuel
     */
    @Override
    public String toString() {
        return "#" + exemplaireId + " - " + titre + " (" + auteur + ")";
    }
}
