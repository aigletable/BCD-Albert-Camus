package fr.ecole.bcd.model;

import java.time.LocalDate;

/**
 * Entité métier représentant une copie physique d'un livre en bibliothèque.
 * <p>
 * Mappe la table {@code exemplaires} de la base de données SQLite.
 * Chaque exemplaire physique porte un numéro incrémental unique (noté manuellement
 * sur la première page du livre ou étiqueté avec un code-barres).
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class Exemplaire {

    /**
     * Statuts de circulation d'un exemplaire physique (contrainte SQL {@code CHECK}).
     */
    public enum Statut {
        /** Exemplaire disponible sur les étagères de la BCD pour être emprunté. */
        DISPONIBLE,

        /** Exemplaire actuellement sorti en prêt par un élève ou un enseignant. */
        EMPRUNTE
    }

    /** Identifiant physique unique auto-incrémenté (clé primaire SQLite). */
    private int id;

    /** Code ISBN du livre de référence (clé étrangère vers {@code livres.isbn}). */
    private String isbn;

    /** Statut courant de disponibilité physique de l'exemplaire. */
    private Statut statut;

    /** Date d'intégration de l'exemplaire au fonds documentaire. */
    private LocalDate dateAjout;

    /** Remarque textuelle sur l'état physique ou alerte spécifique. */
    private String remarque = "";

    /**
     * Constructeur vide par défaut (utile pour la désérialisation et le DAO).
     */
    public Exemplaire() { }

    /**
     * Constructeur complet incluant l'identifiant physique et la remarque d'état.
     *
     * @param id identifiant physique unique
     * @param isbn code ISBN du livre associé
     * @param statut disponibilité de l'exemplaire
     * @param dateAjout date d'enregistrement en base
     * @param remarque observation sur l'état physique
     */
    public Exemplaire(int id, String isbn, Statut statut, LocalDate dateAjout, String remarque) {
        this.id = id;
        this.isbn = isbn;
        this.statut = statut;
        this.dateAjout = dateAjout;
        this.remarque = remarque != null ? remarque : "";
    }

    /**
     * Constructeur complet standard sans remarque initiale.
     *
     * @param id identifiant physique unique
     * @param isbn code ISBN du livre associé
     * @param statut disponibilité de l'exemplaire
     * @param dateAjout date d'enregistrement en base
     */
    public Exemplaire(int id, String isbn, Statut statut, LocalDate dateAjout) {
        this(id, isbn, statut, dateAjout, "");
    }

    /**
     * Constructeur pour nouvelle création avec remarque (avant assignation de l'ID auto-généré).
     *
     * @param isbn code ISBN du livre associé
     * @param statut disponibilité initiale
     * @param dateAjout date d'ajout
     * @param remarque observation sur l'état
     */
    public Exemplaire(String isbn, Statut statut, LocalDate dateAjout, String remarque) {
        this.isbn = isbn;
        this.statut = statut;
        this.dateAjout = dateAjout;
        this.remarque = remarque != null ? remarque : "";
    }

    /**
     * Constructeur pour nouvelle création sans remarque (avant assignation de l'ID auto-généré).
     *
     * @param isbn code ISBN du livre associé
     * @param statut disponibilité initiale
     * @param dateAjout date d'ajout
     */
    public Exemplaire(String isbn, Statut statut, LocalDate dateAjout) {
        this(isbn, statut, dateAjout, "");
    }

    /**
     * Retourne la remarque ou alerte d'état sur l'exemplaire.
     *
     * @return la remarque textuelle (jamais null)
     */
    public String getRemarque() { return remarque != null ? remarque : ""; }

    /**
     * Définit la remarque ou alerte d'état sur l'exemplaire.
     *
     * @param remarque nouvelle observation d'état
     */
    public void setRemarque(String remarque) { this.remarque = remarque != null ? remarque : ""; }

    /**
     * Retourne le numéro unique de l'exemplaire.
     *
     * @return l'identifiant physique
     */
    public int getId() { return id; }

    /**
     * Définit le numéro unique de l'exemplaire.
     *
     * @param id identifiant physique
     */
    public void setId(int id) { this.id = id; }

    /**
     * Retourne le code ISBN du livre auquel se rattache cet exemplaire.
     *
     * @return le code ISBN
     */
    public String getIsbn() { return isbn; }

    /**
     * Définit le code ISBN du livre de référence.
     *
     * @param isbn code ISBN
     */
    public void setIsbn(String isbn) { this.isbn = isbn; }

    /**
     * Retourne le statut de circulation courant.
     *
     * @return {@link Statut#DISPONIBLE} ou {@link Statut#EMPRUNTE}
     */
    public Statut getStatut() { return statut; }

    /**
     * Définit le statut de circulation courant.
     *
     * @param statut nouveau statut
     */
    public void setStatut(Statut statut) { this.statut = statut; }

    /**
     * Retourne la date d'enregistrement de l'exemplaire.
     *
     * @return la date d'ajout
     */
    public LocalDate getDateAjout() { return dateAjout; }

    /**
     * Définit la date d'enregistrement de l'exemplaire.
     *
     * @param dateAjout date d'ajout
     */
    public void setDateAjout(LocalDate dateAjout) { this.dateAjout = dateAjout; }

    /**
     * Représentation textuelle de l'exemplaire pour le débogage et l'UI.
     *
     * @return chaîne descriptive (ex. {@code Exemplaire #42 (9782070513093) — DISPONIBLE})
     */
    @Override
    public String toString() {
        return "Exemplaire #" + id + " (" + isbn + ") — " + statut;
    }
}
