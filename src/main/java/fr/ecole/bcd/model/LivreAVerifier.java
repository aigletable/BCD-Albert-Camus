package fr.ecole.bcd.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Entité représentant un signalement ou un ouvrage consigné dans la "Boîte à vérifier".
 * <p>
 * Permet d'isoler temporairement les livres pour un contrôle physique ultérieur :
 * doublons potentiels refusés lors d'une session de douchette, fiches sans exemplaire physique,
 * ou anomalies détectées en stock.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class LivreAVerifier {

    /** Formateur de date et heure pour l'horodatage des signalements. */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Identifiant unique du signalement en base SQLite (clé primaire). */
    private int id;

    /** Code ISBN de l'ouvrage consigné. */
    private String isbn;

    /** Titre de l'ouvrage. */
    private String titre;

    /** Auteur de l'ouvrage. */
    private String auteur;

    /** Date et heure du scan ou de l'enregistrement de l'anomalie. */
    private String dateScan;

    /** Motif explicatif du signalement (ex. doublon refusé, 0 exemplaire). */
    private String motif;

    /** Nombre d'exemplaires physiques existants au moment du signalement. */
    private int nbExemplaires;

    /** Statut du traitement : {@code A_VERIFIER} ou {@code RESOLU}. */
    private String statut;

    /**
     * Constructeur par défaut horodatant automatiquement le signalement à l'instant présent.
     */
    public LivreAVerifier() {
        this.dateScan = LocalDateTime.now().format(FORMATTER);
        this.statut = "A_VERIFIER";
    }

    /**
     * Constructeur rapide pour consigner un nouveau signalement avec motif.
     *
     * @param isbn code ISBN de l'ouvrage
     * @param titre titre du livre
     * @param auteur auteur du livre
     * @param motif explication du signalement
     * @param nbExemplaires nombre d'exemplaires alors en base
     */
    public LivreAVerifier(String isbn, String titre, String auteur, String motif, int nbExemplaires) {
        this.isbn = isbn != null ? isbn : "";
        this.titre = titre != null ? titre : "";
        this.auteur = auteur != null ? auteur : "";
        this.dateScan = LocalDateTime.now().format(FORMATTER);
        this.motif = motif != null ? motif : "";
        this.nbExemplaires = nbExemplaires;
        this.statut = "A_VERIFIER";
    }

    /**
     * Constructeur complet pour reconstruction depuis la base SQLite.
     *
     * @param id identifiant unique
     * @param isbn code ISBN
     * @param titre titre du livre
     * @param auteur auteur du livre
     * @param dateScan horodatage du signalement
     * @param motif explication du signalement
     * @param nbExemplaires nombre d'exemplaires
     * @param statut statut de résolution
     */
    public LivreAVerifier(int id, String isbn, String titre, String auteur, String dateScan, String motif, int nbExemplaires, String statut) {
        this.id = id;
        this.isbn = isbn != null ? isbn : "";
        this.titre = titre != null ? titre : "";
        this.auteur = auteur != null ? auteur : "";
        this.dateScan = dateScan != null ? dateScan : LocalDateTime.now().format(FORMATTER);
        this.motif = motif != null ? motif : "";
        this.nbExemplaires = nbExemplaires;
        this.statut = statut != null ? statut : "A_VERIFIER";
    }

    /**
     * Retourne l'identifiant du signalement.
     *
     * @return identifiant numérique
     */
    public int getId() { return id; }

    /**
     * Définit l'identifiant du signalement.
     *
     * @param id identifiant numérique
     */
    public void setId(int id) { this.id = id; }

    /**
     * Retourne l'ISBN de l'ouvrage.
     *
     * @return code ISBN
     */
    public String getIsbn() { return isbn; }

    /**
     * Définit l'ISBN de l'ouvrage.
     *
     * @param isbn code ISBN
     */
    public void setIsbn(String isbn) { this.isbn = isbn; }

    /**
     * Retourne le titre du livre.
     *
     * @return le titre
     */
    public String getTitre() { return titre; }

    /**
     * Définit le titre du livre.
     *
     * @param titre nouveau titre
     */
    public void setTitre(String titre) { this.titre = titre; }

    /**
     * Retourne le nom de l'auteur.
     *
     * @return l'auteur
     */
    public String getAuteur() { return auteur; }

    /**
     * Définit le nom de l'auteur.
     *
     * @param auteur nouvel auteur
     */
    public void setAuteur(String auteur) { this.auteur = auteur; }

    /**
     * Retourne la date et l'heure du scan ou du signalement.
     *
     * @return date et heure formatées
     */
    public String getDateScan() { return dateScan; }

    /**
     * Définit la date et heure du signalement.
     *
     * @param dateScan date formatée
     */
    public void setDateScan(String dateScan) { this.dateScan = dateScan; }

    /**
     * Retourne le motif du signalement.
     *
     * @return le motif d'alerte
     */
    public String getMotif() { return motif; }

    /**
     * Définit le motif du signalement.
     *
     * @param motif nouveau motif
     */
    public void setMotif(String motif) { this.motif = motif; }

    /**
     * Retourne le nombre d'exemplaires existants au moment de la détection.
     *
     * @return nombre d'exemplaires
     */
    public int getNbExemplaires() { return nbExemplaires; }

    /**
     * Définit le nombre d'exemplaires physiques.
     *
     * @param nbExemplaires nombre d'exemplaires
     */
    public void setNbExemplaires(int nbExemplaires) { this.nbExemplaires = nbExemplaires; }

    /**
     * Retourne le statut de traitement (ex. {@code A_VERIFIER}, {@code RESOLU}).
     *
     * @return le statut textuel
     */
    public String getStatut() { return statut; }

    /**
     * Définit le statut de traitement.
     *
     * @param statut nouveau statut
     */
    public void setStatut(String statut) { this.statut = statut; }

    /**
     * Représentation textuelle de l'anomalie pour les logs.
     *
     * @return chaîne de débogage
     */
    @Override
    public String toString() {
        return "LivreAVerifier{" +
                "id=" + id +
                ", isbn='" + isbn + '\'' +
                ", titre='" + titre + '\'' +
                ", dateScan='" + dateScan + '\'' +
                ", motif='" + motif + '\'' +
                ", nbExemplaires=" + nbExemplaires +
                '}';
    }
}
