package fr.ecole.bcd.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Modèle métier représentant une Mallette Pédagogique ou Thématique.
 * <p>
 * Une mallette regroupe un ensemble cohérent d'exemplaires physiques de livres
 * autour d'un même thème (ex: Harcèlement scolaire &amp; Vivre ensemble, Contes du monde, Mythologie)
 * ou d'un projet de classe. Elle peut être empruntée en un seul scan par un enseignant.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class Mallette {

    /**
     * Statuts de circulation d'une mallette pédagogique.
     */
    public enum Statut {
        /** Mallette en rayon dans la BCD, prête à être empruntée. */
        DISPONIBLE("Disponible", "status-disponible"),

        /** Mallette actuellement empruntée par un enseignant pour sa classe. */
        EMPRUNTE("En prêt", "status-emprunte"),

        /** Mallette désactivée ou archivée. */
        ARCHIVEE("Archivée", "status-retard");

        /** Libellé d'affichage en français. */
        private final String libelle;

        /** Classe de style CSS AtlantaFX associée. */
        private final String cssClass;

        /**
         * Constructeur de l'état de la mallette.
         *
         * @param libelle nom convivial
         * @param cssClass classe CSS
         */
        Statut(String libelle, String cssClass) {
            this.libelle = libelle;
            this.cssClass = cssClass;
        }

        /**
         * Retourne le libellé d'affichage du statut.
         *
         * @return libellé en clair
         */
        public String getLibelle() { return libelle; }

        /**
         * Retourne la classe CSS de style badge.
         *
         * @return sélecteur CSS
         */
        public String getCssClass() { return cssClass; }
    }

    /** Formateur de date standard (dd/MM/yyyy). */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Identifiant unique de la mallette en base de données. */
    private int id;

    /** Code technique de la mallette (ex. {@code MAL-001}). */
    private String code;

    /** Titre explicite de la mallette (ex. {@code Harcèlement & Vivre ensemble}). */
    private String titre;

    /** Thématique pédagogique centrale. */
    private String theme;

    /** Niveau scolaire conseillé (ex. {@code Cycle 3 (CM1-CM2)}). */
    private String niveau;

    /** Description des objectifs pédagogiques et contenu. */
    private String description;

    /** Statut courant de la mallette. */
    private Statut statut = Statut.DISPONIBLE;

    /** Date de création ou d'assemblage de la mallette. */
    private LocalDate dateCreation = LocalDate.now();

    /** Numéro d'identifiant de l'enseignant emprunteur (si empruntée). */
    private String emprunteurNumero;

    /** Nom complet de l'enseignant emprunteur. */
    private String emprunteurNom;

    /** Classe de l'enseignant emprunteur. */
    private String emprunteurClasse;

    /** Date de sortie en prêt. */
    private LocalDate dateEmprunt;

    /** Date limite de restitution prévue. */
    private LocalDate dateRetourPrevue;

    /** Nombre total de livres contenus dans la mallette. */
    private int nombreLivres = 0;

    /**
     * Constructeur par défaut (requis pour le mapping DAO).
     */
    public Mallette() {}

    /**
     * Constructeur initialisant une nouvelle mallette thématique.
     *
     * @param code code unique (ex. {@code MAL-001})
     * @param titre titre de la mallette
     * @param theme thématique pédagogique
     * @param niveau niveau scolaire
     * @param description objectifs et contenu
     */
    public Mallette(String code, String titre, String theme, String niveau, String description) {
        this.code = code;
        this.titre = titre;
        this.theme = theme;
        this.niveau = niveau != null ? niveau : "";
        this.description = description != null ? description : "";
        this.statut = Statut.DISPONIBLE;
        this.dateCreation = LocalDate.now();
    }

    /**
     * Retourne l'identifiant de la mallette.
     *
     * @return identifiant numérique
     */
    public int getId() { return id; }

    /**
     * Définit l'identifiant de la mallette.
     *
     * @param id identifiant numérique
     */
    public void setId(int id) { this.id = id; }

    /**
     * Retourne le code unique de la mallette.
     *
     * @return code (ex. {@code MAL-001})
     */
    public String getCode() { return code; }

    /**
     * Définit le code unique de la mallette.
     *
     * @param code code
     */
    public void setCode(String code) { this.code = code; }

    /**
     * Retourne le titre de la mallette.
     *
     * @return le titre
     */
    public String getTitre() { return titre; }

    /**
     * Définit le titre de la mallette.
     *
     * @param titre nouveau titre
     */
    public void setTitre(String titre) { this.titre = titre; }

    /**
     * Retourne la thématique de la mallette.
     *
     * @return le thème
     */
    public String getTheme() { return theme; }

    /**
     * Définit la thématique de la mallette.
     *
     * @param theme nouveau thème
     */
    public void setTheme(String theme) { this.theme = theme; }

    /**
     * Retourne le niveau scolaire cible.
     *
     * @return le niveau
     */
    public String getNiveau() { return niveau; }

    /**
     * Définit le niveau scolaire cible.
     *
     * @param niveau niveau
     */
    public void setNiveau(String niveau) { this.niveau = niveau; }

    /**
     * Retourne la description de la mallette.
     *
     * @return résumé / notes
     */
    public String getDescription() { return description; }

    /**
     * Définit la description de la mallette.
     *
     * @param description description
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * Retourne le statut de circulation.
     *
     * @return statut disponible ou en prêt
     */
    public Statut getStatut() { return statut; }

    /**
     * Définit le statut de circulation.
     *
     * @param statut nouveau statut
     */
    public void setStatut(Statut statut) { this.statut = statut; }

    /**
     * Retourne la date de création de la mallette.
     *
     * @return date de création
     */
    public LocalDate getDateCreation() { return dateCreation; }

    /**
     * Définit la date de création de la mallette.
     *
     * @param dateCreation date de création
     */
    public void setDateCreation(LocalDate dateCreation) { this.dateCreation = dateCreation; }

    /**
     * Retourne le numéro d'usager de l'emprunteur actuel.
     *
     * @return numéro d'usager
     */
    public String getEmprunteurNumero() { return emprunteurNumero; }

    /**
     * Définit le numéro d'usager de l'emprunteur.
     *
     * @param emprunteurNumero numéro d'usager
     */
    public void setEmprunteurNumero(String emprunteurNumero) { this.emprunteurNumero = emprunteurNumero; }

    /**
     * Retourne le nom de l'emprunteur actuel.
     *
     * @return nom d'usager
     */
    public String getEmprunteurNom() { return emprunteurNom; }

    /**
     * Définit le nom de l'emprunteur.
     *
     * @param emprunteurNom nom
     */
    public void setEmprunteurNom(String emprunteurNom) { this.emprunteurNom = emprunteurNom; }

    /**
     * Retourne la classe de l'emprunteur actuel.
     *
     * @return classe
     */
    public String getEmprunteurClasse() { return emprunteurClasse; }

    /**
     * Définit la classe de l'emprunteur.
     *
     * @param emprunteurClasse classe
     */
    public void setEmprunteurClasse(String emprunteurClasse) { this.emprunteurClasse = emprunteurClasse; }

    /**
     * Retourne la date de début de l'emprunt.
     *
     * @return date d'emprunt
     */
    public LocalDate getDateEmprunt() { return dateEmprunt; }

    /**
     * Définit la date de début de l'emprunt.
     *
     * @param dateEmprunt date d'emprunt
     */
    public void setDateEmprunt(LocalDate dateEmprunt) { this.dateEmprunt = dateEmprunt; }

    /**
     * Retourne la date limite de restitution prévue.
     *
     * @return date limite de retour
     */
    public LocalDate getDateRetourPrevue() { return dateRetourPrevue; }

    /**
     * Définit la date limite de restitution prévue.
     *
     * @param dateRetourPrevue date limite
     */
    public void setDateRetourPrevue(LocalDate dateRetourPrevue) { this.dateRetourPrevue = dateRetourPrevue; }

    /**
     * Retourne le nombre d'exemplaires inclus dans la mallette.
     *
     * @return total de livres
     */
    public int getNombreLivres() { return nombreLivres; }

    /**
     * Met à jour le nombre d'exemplaires contenus.
     *
     * @param nombreLivres total de livres
     */
    public void setNombreLivres(int nombreLivres) { this.nombreLivres = nombreLivres; }

    /**
     * Indique si la mallette est actuellement disponible en rayon.
     *
     * @return {@code true} si disponible
     */
    public boolean isDisponible() {
        return statut == Statut.DISPONIBLE;
    }

    /**
     * Indique si la mallette est actuellement sortie en prêt.
     *
     * @return {@code true} si en prêt
     */
    public boolean isEmprunte() {
        return statut == Statut.EMPRUNTE;
    }

    /**
     * Retourne le nom et la classe de l'emprunteur formatés pour l'affichage (ex. {@code DUPONT (CM2-A)}).
     *
     * @return chaîne d'affichage
     */
    public String getEmprunteurComplet() {
        if (!isEmprunte() || emprunteurNom == null || emprunteurNom.isBlank()) {
            return "–";
        }
        if (emprunteurClasse != null && !emprunteurClasse.isBlank()) {
            return emprunteurNom + " (" + emprunteurClasse + ")";
        }
        return emprunteurNom;
    }

    /**
     * Retourne la date de création formatée pour l'UI.
     *
     * @return date formatée
     */
    public String getDateCreationFormatted() {
        return dateCreation != null ? dateCreation.format(DATE_FMT) : "–";
    }

    /**
     * Retourne la date d'emprunt formatée pour l'UI.
     *
     * @return date formatée
     */
    public String getDateEmpruntFormatted() {
        return dateEmprunt != null ? dateEmprunt.format(DATE_FMT) : "–";
    }

    /**
     * Retourne la date de retour prévue formatée pour l'UI.
     *
     * @return date formatée
     */
    public String getDateRetourPrevueFormatted() {
        return dateRetourPrevue != null ? dateRetourPrevue.format(DATE_FMT) : "–";
    }

    /**
     * Représentation textuelle de la mallette (ex. {@code MAL-001 - Harcèlement (Vivre ensemble)}).
     *
     * @return libellé textuel
     */
    @Override
    public String toString() {
        return code + " - " + titre + " (" + theme + ")";
    }
}
