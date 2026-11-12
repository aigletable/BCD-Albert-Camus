package fr.ecole.bcd.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Entité métier représentant une campagne officielle ou session d'inventaire et récolement.
 * <p>
 * Permet de geler temporairement les mouvements de prêt sur un périmètre
 * documentaire ciblé (Tout le catalogue, Genre, Thème, Niveau scolaire ou Livre précis)
 * et de consigner les résultats statistiques du rapprochement physique ainsi que les motifs d'écarts.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class SessionInventaire {

    /**
     * Type de périmètre d'application du gel et du récolement d'inventaire.
     */
    public enum PerimetreType {
        /** La totalité des ouvrages et exemplaires de la BCD. */
        TOUT("📚 Tout le catalogue"),

        /** Périmètre filtré sur un genre littéraire (ex. Roman, BD, Documentaire). */
        GENRE("🏷️ Par Genre"),

        /** Périmètre filtré sur une thématique documentaire (ex. Animaux, Histoire). */
        THEME("🎨 Par Thème"),

        /** Périmètre filtré sur un cycle ou niveau de classe (ex. Cycle 2, CP). */
        NIVEAU("🎓 Par Niveau scolaire"),

        /** Périmètre ciblé sur tous les exemplaires d'un ouvrage ISBN précis. */
        LIVRE("📖 Un livre spécifique");

        /** Libellé convivial d'affichage. */
        private final String libelle;

        /**
         * Constructeur d'un type de périmètre.
         *
         * @param libelle nom d'affichage
         */
        PerimetreType(String libelle) {
            this.libelle = libelle;
        }

        /**
         * Retourne le libellé du type de périmètre.
         *
         * @return libellé UI
         */
        public String getLibelle() {
            return libelle;
        }
    }

    /**
     * Statut du cycle de vie d'une campagne d'inventaire.
     */
    public enum StatutSession {
        /** Campagne active en cours avec gel des mouvements sur le périmètre. */
        ACTIVE("🔴 En cours (Stock gelé)"),

        /** Campagne finalisée, procès-verbal généré et gel de stock levé. */
        CLOTUREE("✅ Clôturée et validée"),

        /** Campagne annulée sans modification des stocks. */
        ANNULEE("✕ Annulée");

        /** Libellé convivial d'affichage. */
        private final String libelle;

        /**
         * Constructeur d'un statut de session.
         *
         * @param libelle nom d'affichage
         */
        StatutSession(String libelle) {
            this.libelle = libelle;
        }

        /**
         * Retourne le libellé du statut de session.
         *
         * @return libellé UI
         */
        public String getLibelle() {
            return libelle;
        }
    }

    /** Formateur d'horodatage pour l'affichage (dd/MM/yyyy HH:mm). */
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Identifiant unique de la session (clé primaire). */
    private int id;

    /** Date et heure de démarrage de la session. */
    private String dateDebut;

    /** Date et heure de clôture de la session (null si active). */
    private String dateFin;

    /** Type de filtre documentaire appliqué. */
    private PerimetreType perimetreType;

    /** Valeur du filtre documentaire (ex. {@code BD}, {@code Cycle 3}). */
    private String perimetreValeur;

    /** Statut courant de la session. */
    private StatutSession statut;

    /** Nombre total d'exemplaires attendus dans le périmètre. */
    private int totalAttendus;

    /** Nombre d'exemplaires pointés présents sur les étagères. */
    private int totalPresents;

    /** Nombre d'exemplaires légitimement sortis en prêt. */
    private int totalPrets;

    /** Nombre d'exemplaires non pointés (écarts négatifs). */
    private int totalManquants;

    /** Nombre d'exemplaires retrouvés en plus (écarts positifs). */
    private int totalSurplus;

    /** Commentaire ou conclusion générale de la session. */
    private String commentaire = "";

    /**
     * Constructeur par défaut initialisant une session active horodatée à l'instant présent.
     */
    public SessionInventaire() {
        this.statut = StatutSession.ACTIVE;
        this.dateDebut = LocalDateTime.now().format(DISPLAY_FMT);
    }

    /**
     * Constructeur complet pour reconstruction depuis SQLite.
     *
     * @param id identifiant unique
     * @param dateDebut horodatage de début
     * @param dateFin horodatage de fin
     * @param perimetreType type de périmètre
     * @param perimetreValeur valeur cible
     * @param statut statut de session
     * @param totalAttendus exemplaires attendus
     * @param totalPresents exemplaires pointés
     * @param totalPrets exemplaires en prêt
     * @param totalManquants exemplaires manquants
     * @param totalSurplus exemplaires en surplus
     * @param commentaire observations
     */
    public SessionInventaire(int id, String dateDebut, String dateFin,
                             PerimetreType perimetreType, String perimetreValeur,
                             StatutSession statut,
                             int totalAttendus, int totalPresents, int totalPrets,
                             int totalManquants, int totalSurplus, String commentaire) {
        this.id = id;
        this.dateDebut = dateDebut;
        this.dateFin = dateFin;
        this.perimetreType = perimetreType;
        this.perimetreValeur = perimetreValeur != null ? perimetreValeur : "";
        this.statut = statut != null ? statut : StatutSession.ACTIVE;
        this.totalAttendus = totalAttendus;
        this.totalPresents = totalPresents;
        this.totalPrets = totalPrets;
        this.totalManquants = totalManquants;
        this.totalSurplus = totalSurplus;
        this.commentaire = commentaire != null ? commentaire : "";
    }

    /**
     * Indique si la session est actuellement en cours et bloque les mouvements de stock.
     *
     * @return {@code true} si {@link StatutSession#ACTIVE}
     */
    public boolean isActive() {
        return statut == StatutSession.ACTIVE;
    }

    /**
     * Retourne une description lisible et humaine du périmètre ciblé pour l'UI.
     *
     * @return libellé explicite
     */
    public String getPerimetreLibelle() {
        if (perimetreType == null || perimetreType == PerimetreType.TOUT) {
            return "Tout le catalogue BCD";
        }
        if (perimetreValeur == null || perimetreValeur.isBlank()) {
            return perimetreType.getLibelle();
        }
        switch (perimetreType) {
            case GENRE:
                return "Genre : " + perimetreValeur;
            case THEME:
                return "Thème : " + perimetreValeur;
            case NIVEAU:
                return "Niveau : " + perimetreValeur;
            case LIVRE:
                return "Livre spécifique : " + perimetreValeur;
            default:
                return perimetreType.getLibelle() + " (" + perimetreValeur + ")";
        }
    }

    /**
     * Calcule le taux de conformité physique (présents + en prêt légitime) sur le stock attendu.
     *
     * @return pourcentage de conformité entre 0.0 et 100.0
     */
    public double getTauxPresence() {
        int verifiables = totalAttendus;
        if (verifiables <= 0) return 100.0;
        int conformes = totalPresents + totalPrets;
        return (conformes * 100.0) / verifiables;
    }

    /**
     * Retourne l'identifiant de la session.
     *
     * @return identifiant numérique
     */
    public int getId() { return id; }

    /**
     * Définit l'identifiant de la session.
     *
     * @param id identifiant numérique
     */
    public void setId(int id) { this.id = id; }

    /**
     * Retourne la date de début de la session.
     *
     * @return date de début formatée
     */
    public String getDateDebut() { return dateDebut; }

    /**
     * Définit la date de début de la session.
     *
     * @param dateDebut date de début
     */
    public void setDateDebut(String dateDebut) { this.dateDebut = dateDebut; }

    /**
     * Retourne la date de fin de la session.
     *
     * @return date de clôture
     */
    public String getDateFin() { return dateFin; }

    /**
     * Définit la date de fin de la session.
     *
     * @param dateFin date de clôture
     */
    public void setDateFin(String dateFin) { this.dateFin = dateFin; }

    /**
     * Retourne le type de périmètre configuré.
     *
     * @return {@link PerimetreType}
     */
    public PerimetreType getPerimetreType() { return perimetreType; }

    /**
     * Définit le type de périmètre configuré.
     *
     * @param perimetreType type de périmètre
     */
    public void setPerimetreType(PerimetreType perimetreType) { this.perimetreType = perimetreType; }

    /**
     * Retourne la valeur du filtre de périmètre.
     *
     * @return valeur textuelle
     */
    public String getPerimetreValeur() { return perimetreValeur; }

    /**
     * Définit la valeur du filtre de périmètre.
     *
     * @param perimetreValeur valeur
     */
    public void setPerimetreValeur(String perimetreValeur) { this.perimetreValeur = perimetreValeur; }

    /**
     * Retourne le statut de la session.
     *
     * @return {@link StatutSession}
     */
    public StatutSession getStatut() { return statut; }

    /**
     * Définit le statut de la session.
     *
     * @param statut nouveau statut
     */
    public void setStatut(StatutSession statut) { this.statut = statut; }

    /**
     * Retourne le nombre total d'exemplaires attendus.
     *
     * @return total attendu
     */
    public int getTotalAttendus() { return totalAttendus; }

    /**
     * Définit le nombre total d'exemplaires attendus.
     *
     * @param totalAttendus total attendu
     */
    public void setTotalAttendus(int totalAttendus) { this.totalAttendus = totalAttendus; }

    /**
     * Retourne le nombre d'exemplaires pointés présents.
     *
     * @return total présents
     */
    public int getTotalPresents() { return totalPresents; }

    /**
     * Définit le nombre d'exemplaires pointés présents.
     *
     * @param totalPresents total présents
     */
    public void setTotalPresents(int totalPresents) { this.totalPresents = totalPresents; }

    /**
     * Retourne le nombre d'exemplaires en prêt.
     *
     * @return total en prêt
     */
    public int getTotalPrets() { return totalPrets; }

    /**
     * Définit le nombre d'exemplaires en prêt.
     *
     * @param totalPrets total en prêt
     */
    public void setTotalPrets(int totalPrets) { this.totalPrets = totalPrets; }

    /**
     * Retourne le nombre d'exemplaires manquants.
     *
     * @return total manquants
     */
    public int getTotalManquants() { return totalManquants; }

    /**
     * Définit le nombre d'exemplaires manquants.
     *
     * @param totalManquants total manquants
     */
    public void setTotalManquants(int totalManquants) { this.totalManquants = totalManquants; }

    /**
     * Retourne le nombre d'exemplaires en surplus.
     *
     * @return total surplus
     */
    public int getTotalSurplus() { return totalSurplus; }

    /**
     * Définit le nombre d'exemplaires en surplus.
     *
     * @param totalSurplus total surplus
     */
    public void setTotalSurplus(int totalSurplus) { this.totalSurplus = totalSurplus; }

    /**
     * Retourne le commentaire de synthèse.
     *
     * @return commentaire
     */
    public String getCommentaire() { return commentaire; }

    /**
     * Définit le commentaire de synthèse.
     *
     * @param commentaire commentaire
     */
    public void setCommentaire(String commentaire) { this.commentaire = commentaire; }
}
