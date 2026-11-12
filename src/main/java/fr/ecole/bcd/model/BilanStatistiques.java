package fr.ecole.bcd.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Modèle regroupant les indicateurs statistiques, KPIs et palmarès de la BCD.
 * <p>
 * Utilisé pour alimenter les tableaux de bord décisionnels, les bilans annuels
 * d'activité remis à la direction et les rapports présentés en conseils d'école.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class BilanStatistiques {

    // ── Synthèse globale ──────────────────────────────────────────────────

    /** Total historique de tous les prêts consentis depuis l'origine de la BCD. */
    private int totalPretsHistorique;

    /** Nombre de prêts actuellement en cours (non restitués). */
    private int pretsEnCours;

    /** Nombre de prêts dont la date limite de retour est dépassée. */
    private int pretsEnRetard;

    /** Nombre total d'élèves inscrits dans l'établissement. */
    private int totalElevesInscrits;

    /** Nombre d'élèves ayant emprunté au moins un livre. */
    private int elevesActifs;

    /** Nombre de titres ou livres distincts ayant circulé au moins une fois. */
    private int livresDifferentsEmpruntes;

    // ── Palmarès détaillés ────────────────────────────────────────────────

    /** Liste ordonnée des livres les plus empruntés. */
    private List<TopLivre> topLivres = new ArrayList<>();

    /** Liste ordonnée des statistiques de prêt par classe scolaire. */
    private List<ClasseStat> statsParClasse = new ArrayList<>();

    /** Liste ordonnée des lecteurs les plus assidus. */
    private List<TopLecteur> topLecteurs = new ArrayList<>();

    /**
     * Constructeur par défaut initialisant les listes internes de statistiques.
     */
    public BilanStatistiques() {}

    // ── Getters / Setters globaux ─────────────────────────────────────────

    /**
     * Retourne le nombre total de prêts enregistrés depuis la mise en service.
     *
     * @return total historique de prêts
     */
    public int getTotalPretsHistorique() {
        return totalPretsHistorique;
    }

    /**
     * Définit le nombre total de prêts enregistrés depuis la mise en service.
     *
     * @param totalPretsHistorique total historique
     */
    public void setTotalPretsHistorique(int totalPretsHistorique) {
        this.totalPretsHistorique = totalPretsHistorique;
    }

    /**
     * Retourne le nombre de prêts en cours.
     *
     * @return prêts actifs
     */
    public int getPretsEnCours() {
        return pretsEnCours;
    }

    /**
     * Définit le nombre de prêts en cours.
     *
     * @param pretsEnCours prêts actifs
     */
    public void setPretsEnCours(int pretsEnCours) {
        this.pretsEnCours = pretsEnCours;
    }

    /**
     * Retourne le nombre d'emprunts en retard de restitution.
     *
     * @return prêts en retard
     */
    public int getPretsEnRetard() {
        return pretsEnRetard;
    }

    /**
     * Définit le nombre d'emprunts en retard.
     *
     * @param pretsEnRetard prêts en retard
     */
    public void setPretsEnRetard(int pretsEnRetard) {
        this.pretsEnRetard = pretsEnRetard;
    }

    /**
     * Retourne le nombre total d'élèves enregistrés dans l'école.
     *
     * @return effectif total
     */
    public int getTotalElevesInscrits() {
        return totalElevesInscrits;
    }

    /**
     * Définit le nombre total d'élèves enregistrés.
     *
     * @param totalElevesInscrits effectif total
     */
    public void setTotalElevesInscrits(int totalElevesInscrits) {
        this.totalElevesInscrits = totalElevesInscrits;
    }

    /**
     * Retourne le nombre d'élèves ayant au moins un emprunt à leur actif.
     *
     * @return nombre d'élèves emprunteurs
     */
    public int getElevesActifs() {
        return elevesActifs;
    }

    /**
     * Définit le nombre d'élèves ayant emprunté.
     *
     * @param elevesActifs nombre d'élèves emprunteurs
     */
    public void setElevesActifs(int elevesActifs) {
        this.elevesActifs = elevesActifs;
    }

    /**
     * Retourne le nombre d'ouvrages différents ayant été empruntés.
     *
     * @return nombre de titres différents
     */
    public int getLivresDifferentsEmpruntes() {
        return livresDifferentsEmpruntes;
    }

    /**
     * Définit le nombre d'ouvrages différents empruntés.
     *
     * @param livresDifferentsEmpruntes nombre de titres
     */
    public void setLivresDifferentsEmpruntes(int livresDifferentsEmpruntes) {
        this.livresDifferentsEmpruntes = livresDifferentsEmpruntes;
    }

    /**
     * Calcule le taux de participation des élèves à la BCD en pourcentage.
     *
     * @return pourcentage d'élèves actifs entre 0.0 et 100.0
     */
    public double getTauxElevesActifs() {
        if (totalElevesInscrits <= 0) return 0.0;
        return (double) elevesActifs * 100.0 / totalElevesInscrits;
    }

    /**
     * Retourne le palmarès des livres les plus empruntés.
     *
     * @return liste ordonnée de {@link TopLivre}
     */
    public List<TopLivre> getTopLivres() {
        return topLivres;
    }

    /**
     * Définit le palmarès des livres les plus empruntés.
     *
     * @param topLivres liste de {@link TopLivre}
     */
    public void setTopLivres(List<TopLivre> topLivres) {
        this.topLivres = topLivres;
    }

    /**
     * Retourne le classement d'activité par classe.
     *
     * @return liste de {@link ClasseStat}
     */
    public List<ClasseStat> getStatsParClasse() {
        return statsParClasse;
    }

    /**
     * Définit le classement d'activité par classe.
     *
     * @param statsParClasse liste de {@link ClasseStat}
     */
    public void setStatsParClasse(List<ClasseStat> statsParClasse) {
        this.statsParClasse = statsParClasse;
    }

    /**
     * Retourne le palmarès des élèves les plus assidus.
     *
     * @return liste ordonnée de {@link TopLecteur}
     */
    public List<TopLecteur> getTopLecteurs() {
        return topLecteurs;
    }

    /**
     * Définit le palmarès des élèves les plus assidus.
     *
     * @param topLecteurs liste de {@link TopLecteur}
     */
    public void setTopLecteurs(List<TopLecteur> topLecteurs) {
        this.topLecteurs = topLecteurs;
    }

    // ── Sous-classes de statistiques ─────────────────────────────────────

    /**
     * Fiche statistique d'un livre figurant dans le palmarès des emprunts.
     */
    public static class TopLivre {
        /** Rang dans le classement (1 pour le premier). */
        private int rang;

        /** Code ISBN du livre. */
        private String isbn;

        /** Titre du livre. */
        private String titre;

        /** Auteur du livre. */
        private String auteur;

        /** Nombre de prêts enregistrés pour ce titre. */
        private int nbPrets;

        /**
         * Constructeur d'un élément du palmarès des livres.
         *
         * @param rang rang dans le classement
         * @param isbn code ISBN
         * @param titre titre du livre
         * @param auteur nom d'auteur
         * @param nbPrets cumul des prêts
         */
        public TopLivre(int rang, String isbn, String titre, String auteur, int nbPrets) {
            this.rang = rang;
            this.isbn = isbn;
            this.titre = titre;
            this.auteur = auteur != null ? auteur : "";
            this.nbPrets = nbPrets;
        }

        /**
         * Retourne le rang de classement.
         *
         * @return rang (1-indexé)
         */
        public int getRang() { return rang; }

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
         * Retourne le volume de prêts enregistrés pour ce livre.
         *
         * @return nombre de prêts
         */
        public int getNbPrets() { return nbPrets; }
    }

    /**
     * Statistiques d'emprunts agrégées pour une classe scolaire.
     */
    public static class ClasseStat {
        /** Rang de la classe dans le classement des emprunts. */
        private int rang;

        /** Nom de la classe (ex. {@code CM1-A}). */
        private String classe;

        /** Nombre total de livres empruntés par les élèves de la classe. */
        private int nbPrets;

        /** Nombre d'élèves distincts de la classe ayant emprunté au moins un livre. */
        private int nbEmprunteurs;

        /**
         * Constructeur d'une statistique de classe.
         *
         * @param rang rang
         * @param classe identifiant de classe
         * @param nbPrets nombre total de prêts
         * @param nbEmprunteurs effectif d'élèves actifs
         */
        public ClasseStat(int rang, String classe, int nbPrets, int nbEmprunteurs) {
            this.rang = rang;
            this.classe = classe;
            this.nbPrets = nbPrets;
            this.nbEmprunteurs = nbEmprunteurs;
        }

        /**
         * Retourne le rang de la classe.
         *
         * @return rang (1-indexé)
         */
        public int getRang() { return rang; }

        /**
         * Retourne le nom de la classe.
         *
         * @return nom de la classe
         */
        public String getClasse() { return classe; }

        /**
         * Retourne le volume global de prêts pour la classe.
         *
         * @return total de prêts
         */
        public int getNbPrets() { return nbPrets; }

        /**
         * Retourne le nombre d'élèves emprunteurs dans la classe.
         *
         * @return nombre d'élèves
         */
        public int getNbEmprunteurs() { return nbEmprunteurs; }

        /**
         * Calcule la moyenne de prêts par élève emprunteur dans la classe.
         *
         * @return ratio moyen de prêts
         */
        public double getMoyennePretsParEmprunteur() {
            if (nbEmprunteurs <= 0) return 0.0;
            return (double) nbPrets / nbEmprunteurs;
        }
    }

    /**
     * Fiche individuelle d'un élève figurant dans le palmarès des lecteurs.
     */
    public static class TopLecteur {
        /** Rang individuel dans le palmarès (1 pour le premier lecteur). */
        private int rang;

        /** Numéro d'identifiant usager (ex. {@code E-101}). */
        private String numero;

        /** Nom de famille de l'élève. */
        private String nom;

        /** Prénom de l'élève. */
        private String prenom;

        /** Classe scolaire de l'élève. */
        private String classe;

        /** Nombre total de livres empruntés par cet usager. */
        private int nbPrets;

        /**
         * Constructeur d'un lecteur du palmarès.
         *
         * @param rang rang de classement
         * @param numero numéro d'usager
         * @param nom nom de famille
         * @param prenom prénom
         * @param classe classe
         * @param nbPrets total d'emprunts
         */
        public TopLecteur(int rang, String numero, String nom, String prenom, String classe, int nbPrets) {
            this.rang = rang;
            this.numero = numero;
            this.nom = nom;
            this.prenom = prenom;
            this.classe = classe;
            this.nbPrets = nbPrets;
        }

        /**
         * Retourne le rang de l'élève.
         *
         * @return rang (1-indexé)
         */
        public int getRang() { return rang; }

        /**
         * Retourne le numéro unique de l'élève.
         *
         * @return numéro d'usager
         */
        public String getNumero() { return numero; }

        /**
         * Retourne le nom de famille de l'élève.
         *
         * @return nom
         */
        public String getNom() { return nom; }

        /**
         * Retourne le prénom de l'élève.
         *
         * @return prénom
         */
        public String getPrenom() { return prenom; }

        /**
         * Retourne le nom et prénom formatés pour l'UI.
         *
         * @return nom complet
         */
        public String getNomComplet() { return nom + " " + prenom; }

        /**
         * Retourne la classe de l'élève.
         *
         * @return classe
         */
        public String getClasse() { return classe; }

        /**
         * Retourne le nombre total de livres empruntés par l'élève.
         *
         * @return nombre de prêts
         */
        public int getNbPrets() { return nbPrets; }
    }
}
