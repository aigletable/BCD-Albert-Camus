package fr.ecole.bcd.model;

import java.time.LocalDate;

/**
 * Entité représentant une copie physique de livre croisée avec ses informations d'emprunt
 * pour le tableau de récolement et de pointage d'inventaire.
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class RecolementItem {

    /**
     * Statut de pointage de l'exemplaire au cours de la session d'inventaire.
     */
    public enum StatutInventaire {
        /** Exemplaire physiquement scanné et constaté présent sur les étagères. */
        PRESENT,

        /** Exemplaire légitimement sorti en prêt au moment de la campagne. */
        EN_PRET,

        /** Exemplaire théoriquement en stock (DISPONIBLE) mais non encore pointé. */
        MANQUANT,

        /** Exemplaire retrouvé en surplus ou inattendu dans la zone pointée. */
        SURPLUS
    }

    /** Identifiant physique de l'exemplaire. */
    private final int exemplaireId;

    /** Code ISBN de l'ouvrage. */
    private final String isbn;

    /** Titre de l'ouvrage. */
    private final String titre;

    /** Auteur de l'ouvrage. */
    private final String auteur;

    /** Maison d'édition de l'ouvrage. */
    private final String editeur;

    /** Remarque d'état physique ou observation. */
    private String remarque;

    /** Statut persistant de l'exemplaire en base SQLite avant inventaire. */
    private final Exemplaire.Statut statutBdd;

    /** Nom de l'emprunteur actuel (si le livre est en prêt). */
    private final String emprunteurNom;

    /** Classe de l'emprunteur actuel. */
    private final String emprunteurClasse;

    /** Date de sortie en prêt. */
    private final LocalDate dateEmprunt;

    /** Date limite de restitution. */
    private final LocalDate dateLimite;

    /** Statut de récolement dynamique pour la session. */
    private StatutInventaire statutInventaire;

    /** Heure exacte du scan lors de la session (ex. {@code 14:32:05}). */
    private String heureScan;

    /** Motif de justification de l'écart qualifié. */
    private MotifEcart motifEcart = MotifEcart.AUCUN;

    /** Commentaire libre justificatif de l'écart. */
    private String motifCommentaire = "";

    /**
     * Constructeur initialisant l'élément de récolement à partir des données de base et d'emprunt.
     *
     * @param exemplaireId identifiant physique
     * @param isbn code ISBN
     * @param titre titre du livre
     * @param auteur auteur
     * @param editeur éditeur
     * @param remarque observation d'état
     * @param statutBdd statut initial en base
     * @param emprunteurNom nom de l'emprunteur si prêt en cours
     * @param emprunteurClasse classe de l'emprunteur
     * @param dateEmprunt date d'emprunt
     * @param dateLimite date limite
     */
    public RecolementItem(int exemplaireId, String isbn, String titre, String auteur, String editeur,
                          String remarque, Exemplaire.Statut statutBdd,
                          String emprunteurNom, String emprunteurClasse,
                          LocalDate dateEmprunt, LocalDate dateLimite) {
        this.exemplaireId = exemplaireId;
        this.isbn = isbn != null ? isbn : "";
        this.titre = titre != null ? titre : "";
        this.auteur = auteur != null ? auteur : "";
        this.editeur = editeur != null ? editeur : "";
        this.remarque = remarque != null ? remarque : "";
        this.statutBdd = statutBdd;
        this.emprunteurNom = emprunteurNom != null ? emprunteurNom : "";
        this.emprunteurClasse = emprunteurClasse != null ? emprunteurClasse : "";
        this.dateEmprunt = dateEmprunt;
        this.dateLimite = dateLimite;

        if (statutBdd == Exemplaire.Statut.EMPRUNTE) {
            this.statutInventaire = StatutInventaire.EN_PRET;
        } else {
            this.statutInventaire = StatutInventaire.MANQUANT;
        }
        this.heureScan = "";
        this.motifEcart = MotifEcart.AUCUN;
        this.motifCommentaire = "";
    }

    /**
     * Marque l'exemplaire comme physiquement présent suite à un scan douchette.
     *
     * @param heure heure horodatée du scan
     */
    public void marquerPresent(String heure) {
        this.statutInventaire = StatutInventaire.PRESENT;
        this.heureScan = heure != null ? heure : "";
        this.motifEcart = MotifEcart.AUCUN;
    }

    /**
     * Marque l'exemplaire comme surplus inattendu pointé sur l'étagère.
     *
     * @param heure heure du scan
     */
    public void marquerSurplus(String heure) {
        this.statutInventaire = StatutInventaire.SURPLUS;
        this.heureScan = heure != null ? heure : "";
        this.motifEcart = MotifEcart.RETROUVE;
    }

    /**
     * Réinitialise l'état de pointage de la ligne à son statut théorique de départ.
     */
    public void reinitialiser() {
        if (this.statutBdd == Exemplaire.Statut.EMPRUNTE) {
            this.statutInventaire = StatutInventaire.EN_PRET;
        } else {
            this.statutInventaire = StatutInventaire.MANQUANT;
        }
        this.heureScan = "";
        this.motifEcart = MotifEcart.AUCUN;
        this.motifCommentaire = "";
    }

    /**
     * Retourne le motif d'écart affecté.
     *
     * @return {@link MotifEcart}
     */
    public MotifEcart getMotifEcart() {
        return motifEcart != null ? motifEcart : MotifEcart.AUCUN;
    }

    /**
     * Définit le motif d'écart affecté.
     *
     * @param motifEcart motif d'écart
     */
    public void setMotifEcart(MotifEcart motifEcart) {
        this.motifEcart = motifEcart != null ? motifEcart : MotifEcart.AUCUN;
    }

    /**
     * Retourne le commentaire explicatif de l'écart.
     *
     * @return commentaire texte
     */
    public String getMotifCommentaire() {
        return motifCommentaire != null ? motifCommentaire : "";
    }

    /**
     * Définit le commentaire explicatif de l'écart.
     *
     * @param motifCommentaire commentaire texte
     */
    public void setMotifCommentaire(String motifCommentaire) {
        this.motifCommentaire = motifCommentaire != null ? motifCommentaire : "";
    }

    /**
     * Retourne l'identifiant physique de l'exemplaire.
     *
     * @return numéro d'exemplaire
     */
    public int getExemplaireId() { return exemplaireId; }

    /**
     * Retourne l'ISBN de l'ouvrage.
     *
     * @return code ISBN
     */
    public String getIsbn() { return isbn; }

    /**
     * Retourne le titre de l'ouvrage.
     *
     * @return titre
     */
    public String getTitre() { return titre; }

    /**
     * Retourne l'auteur de l'ouvrage.
     *
     * @return auteur
     */
    public String getAuteur() { return auteur; }

    /**
     * Retourne l'éditeur de l'ouvrage.
     *
     * @return éditeur
     */
    public String getEditeur() { return editeur; }

    /**
     * Retourne la remarque d'état de l'exemplaire.
     *
     * @return observation
     */
    public String getRemarque() { return remarque; }

    /**
     * Définit la remarque d'état de l'exemplaire.
     *
     * @param remarque observation
     */
    public void setRemarque(String remarque) { this.remarque = remarque != null ? remarque : ""; }

    /**
     * Retourne le statut initial enregistré en base de données.
     *
     * @return statut SQLite
     */
    public Exemplaire.Statut getStatutBdd() { return statutBdd; }

    /**
     * Retourne le nom de l'emprunteur actuel.
     *
     * @return nom d'usager
     */
    public String getEmprunteurNom() { return emprunteurNom; }

    /**
     * Retourne la classe de l'emprunteur actuel.
     *
     * @return classe
     */
    public String getEmprunteurClasse() { return emprunteurClasse; }

    /**
     * Retourne la date de sortie en prêt.
     *
     * @return date d'emprunt
     */
    public LocalDate getDateEmprunt() { return dateEmprunt; }

    /**
     * Retourne la date limite de prêt.
     *
     * @return date limite
     */
    public LocalDate getDateLimite() { return dateLimite; }

    /**
     * Retourne le statut d'inventaire dynamique.
     *
     * @return statut inventaire
     */
    public StatutInventaire getStatutInventaire() { return statutInventaire; }

    /**
     * Définit le statut d'inventaire dynamique.
     *
     * @param statutInventaire statut inventaire
     */
    public void setStatutInventaire(StatutInventaire statutInventaire) { this.statutInventaire = statutInventaire; }

    /**
     * Retourne l'heure de scan constatée.
     *
     * @return heure horodatée
     */
    public String getHeureScan() { return heureScan; }

    /**
     * Retourne une synthèse lisible pour l'affichage en colonne emprunteur/état.
     *
     * @return chaîne d'affichage
     */
    public String getEmprunteurAffichage() {
        if (statutInventaire == StatutInventaire.EN_PRET && !emprunteurNom.isBlank()) {
            return "Emprunté : " + emprunteurNom + (!emprunteurClasse.isBlank() ? " (" + emprunteurClasse + ")" : "");
        }
        if (statutInventaire == StatutInventaire.PRESENT && !heureScan.isBlank()) {
            return "Vu à " + heureScan;
        }
        return !remarque.isBlank() ? remarque : "–";
    }
}
