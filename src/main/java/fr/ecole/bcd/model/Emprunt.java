package fr.ecole.bcd.model;

import java.time.LocalDate;

/**
 * Entité métier représentant un emprunt (prêt de livre) consigné à la BCD.
 * <p>
 * Mappe la table {@code emprunts} de la base SQLite. Un prêt actif se caractérise
 * par {@code dateRendu == null}. La classe intègre également les données jointes
 * issues des tables {@code utilisateurs}, {@code exemplaires} et {@code livres}
 * pour optimiser les performances d'affichage de la table des prêts.
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class Emprunt {

    /** Identifiant unique du prêt (clé primaire auto-incrémentée). */
    private int id;

    /** Identifiant de l'usager emprunteur (clé étrangère vers {@code utilisateurs.numero}). */
    private String utilisateurNumero;

    /** Identifiant physique de l'exemplaire emprunté (clé étrangère vers {@code exemplaires.id}). */
    private int exemplaireId;

    /** Date de début du prêt. */
    private LocalDate dateEmprunt;

    /** Date limite de restitution fixée selon les règles métier. */
    private LocalDate dateLimite;

    /** Date effective de restitution du livre (null si le prêt est en cours). */
    private LocalDate dateRendu;

    /** Indique si le prêt a déjà bénéficié d'un renouvellement / prolongation de délai. */
    private boolean estRenouvele;

    // ── Données jointes (affichage complet dans les tableaux UI) ─────────────

    /** Nom de famille de l'emprunteur (champ joint). */
    private String utilisateurNom;

    /** Prénom de l'emprunteur (champ joint). */
    private String utilisateurPrenom;

    /** Classe scolaire de l'emprunteur (champ joint). */
    private String utilisateurClasse;

    /** Type de profil de l'emprunteur (champ joint). */
    private Utilisateur.TypeCompte utilisateurType;

    /** Titre du livre emprunté (champ joint). */
    private String livreTitre;

    /** Auteur du livre emprunté (champ joint). */
    private String livreAuteur;

    /** Code ISBN du livre emprunté (champ joint). */
    private String livreIsbn;

    /** URL de la jaquette du livre (champ joint). */
    private String livreCouvertureUrl;

    /**
     * Constructeur par défaut sans argument (requis pour le DAO).
     */
    public Emprunt() { }

    /**
     * Constructeur complet initialisant tous les attributs de persistance d'un prêt.
     *
     * @param id identifiant unique
     * @param utilisateurNumero numéro de l'usager
     * @param exemplaireId identifiant physique de l'exemplaire
     * @param dateEmprunt date d'enregistrement du prêt
     * @param dateLimite date de fin théorique
     * @param dateRendu date de retour effectif (null si en cours)
     * @param estRenouvele true si déjà prolongé
     */
    public Emprunt(int id, String utilisateurNumero, int exemplaireId,
                   LocalDate dateEmprunt, LocalDate dateLimite,
                   LocalDate dateRendu, boolean estRenouvele) {
        this.id = id;
        this.utilisateurNumero = utilisateurNumero;
        this.exemplaireId = exemplaireId;
        this.dateEmprunt = dateEmprunt;
        this.dateLimite = dateLimite;
        this.dateRendu = dateRendu;
        this.estRenouvele = estRenouvele;
    }

    /**
     * Indique si l'emprunt est actuellement actif (non encore restitué).
     *
     * @return {@code true} si {@code dateRendu == null}
     */
    public boolean isEnCours() {
        return dateRendu == null;
    }

    /**
     * Indique si le prêt en cours a dépassé sa date limite de restitution.
     *
     * @return {@code true} si le livre est en retard
     */
    public boolean isEnRetard() {
        return isEnCours() && dateLimite != null && LocalDate.now().isAfter(dateLimite);
    }

    /**
     * Calcule le nombre de jours de retard par rapport à la date limite courante.
     *
     * @return nombre de jours de retard (0 si le livre n'est pas en retard ou est déjà rendu)
     */
    public long getJoursRetard() {
        if (!isEnRetard()) return 0;
        return java.time.temporal.ChronoUnit.DAYS.between(dateLimite, LocalDate.now());
    }

    /**
     * Retourne l'identifiant du prêt.
     *
     * @return identifiant numérique
     */
    public int getId() { return id; }

    /**
     * Définit l'identifiant du prêt.
     *
     * @param id identifiant numérique
     */
    public void setId(int id) { this.id = id; }

    /**
     * Retourne le numéro de l'usager emprunteur.
     *
     * @return identifiant usager
     */
    public String getUtilisateurNumero() { return utilisateurNumero; }

    /**
     * Définit le numéro de l'usager emprunteur.
     *
     * @param utilisateurNumero numéro usager
     */
    public void setUtilisateurNumero(String utilisateurNumero) { this.utilisateurNumero = utilisateurNumero; }

    /**
     * Retourne l'identifiant physique de l'exemplaire emprunté.
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
     * Retourne la date de sortie en prêt.
     *
     * @return date d'emprunt
     */
    public LocalDate getDateEmprunt() { return dateEmprunt; }

    /**
     * Définit la date de début du prêt.
     *
     * @param dateEmprunt date d'emprunt
     */
    public void setDateEmprunt(LocalDate dateEmprunt) { this.dateEmprunt = dateEmprunt; }

    /**
     * Retourne la date limite de restitution.
     *
     * @return date limite
     */
    public LocalDate getDateLimite() { return dateLimite; }

    /**
     * Définit la date limite de restitution.
     *
     * @param dateLimite nouvelle date d'échéance
     */
    public void setDateLimite(LocalDate dateLimite) { this.dateLimite = dateLimite; }

    /**
     * Retourne la date de restitution effective du livre.
     *
     * @return date de retour, ou {@code null} si non rendu
     */
    public LocalDate getDateRendu() { return dateRendu; }

    /**
     * Définit la date de restitution effective du livre.
     *
     * @param dateRendu date de retour
     */
    public void setDateRendu(LocalDate dateRendu) { this.dateRendu = dateRendu; }

    /**
     * Indique si le prêt a fait l'objet d'un renouvellement.
     *
     * @return {@code true} si renouvelé
     */
    public boolean isEstRenouvele() { return estRenouvele; }

    /**
     * Définit l'état de renouvellement du prêt.
     *
     * @param estRenouvele booléen
     */
    public void setEstRenouvele(boolean estRenouvele) { this.estRenouvele = estRenouvele; }

    /**
     * Retourne le nom de l'emprunteur (jointure).
     *
     * @return nom de famille
     */
    public String getUtilisateurNom() { return utilisateurNom; }

    /**
     * Définit le nom de l'emprunteur.
     *
     * @param utilisateurNom nom
     */
    public void setUtilisateurNom(String utilisateurNom) { this.utilisateurNom = utilisateurNom; }

    /**
     * Retourne le prénom de l'emprunteur (jointure).
     *
     * @return prénom
     */
    public String getUtilisateurPrenom() { return utilisateurPrenom; }

    /**
     * Définit le prénom de l'emprunteur.
     *
     * @param utilisateurPrenom prénom
     */
    public void setUtilisateurPrenom(String utilisateurPrenom) { this.utilisateurPrenom = utilisateurPrenom; }

    /**
     * Retourne la classe de l'emprunteur (jointure).
     *
     * @return classe
     */
    public String getUtilisateurClasse() { return utilisateurClasse; }

    /**
     * Définit la classe de l'emprunteur.
     *
     * @param utilisateurClasse classe
     */
    public void setUtilisateurClasse(String utilisateurClasse) { this.utilisateurClasse = utilisateurClasse; }

    /**
     * Retourne le profil usager (jointure).
     *
     * @return type élève ou enseignant
     */
    public Utilisateur.TypeCompte getUtilisateurType() { return utilisateurType; }

    /**
     * Définit le profil usager.
     *
     * @param utilisateurType type de compte
     */
    public void setUtilisateurType(Utilisateur.TypeCompte utilisateurType) { this.utilisateurType = utilisateurType; }

    /**
     * Retourne le titre du livre (jointure).
     *
     * @return titre du livre
     */
    public String getLivreTitre() { return livreTitre; }

    /**
     * Définit le titre du livre.
     *
     * @param livreTitre titre
     */
    public void setLivreTitre(String livreTitre) { this.livreTitre = livreTitre; }

    /**
     * Retourne l'auteur du livre (jointure).
     *
     * @return auteur
     */
    public String getLivreAuteur() { return livreAuteur; }

    /**
     * Définit l'auteur du livre.
     *
     * @param livreAuteur auteur
     */
    public void setLivreAuteur(String livreAuteur) { this.livreAuteur = livreAuteur; }

    /**
     * Retourne l'ISBN du livre (jointure).
     *
     * @return code ISBN
     */
    public String getLivreIsbn() { return livreIsbn; }

    /**
     * Définit l'ISBN du livre.
     *
     * @param livreIsbn code ISBN
     */
    public void setLivreIsbn(String livreIsbn) { this.livreIsbn = livreIsbn; }

    /**
     * Retourne l'URL de couverture du livre (jointure).
     *
     * @return lien image
     */
    public String getLivreCouvertureUrl() { return livreCouvertureUrl; }

    /**
     * Définit l'URL de couverture du livre.
     *
     * @param livreCouvertureUrl lien image
     */
    public void setLivreCouvertureUrl(String livreCouvertureUrl) { this.livreCouvertureUrl = livreCouvertureUrl; }

    /**
     * Retourne le nom complet et la classe de l'emprunteur formatés pour l'UI (ex. {@code Paul DUPONT (CM1-A)}).
     *
     * @return chaîne formatée
     */
    public String getNomCompletEmprunteur() {
        if (utilisateurNom == null && utilisateurPrenom == null) {
            return utilisateurNumero;
        }
        String res = (utilisateurPrenom != null ? utilisateurPrenom : "") + " "
                   + (utilisateurNom != null ? utilisateurNom.toUpperCase() : "");
        if (utilisateurClasse != null && !utilisateurClasse.isBlank()) {
            res += " (" + utilisateurClasse + ")";
        }
        return res.trim();
    }

    /**
     * Retourne le titre et l'auteur du livre associés (ex. {@code Le Petit Prince — Antoine de Saint-Exupéry}).
     *
     * @return chaîne formatée
     */
    public String getTitreEtAuteurLivre() {
        if (livreTitre == null) {
            return "Exemplaire #" + exemplaireId;
        }
        if (livreAuteur != null && !livreAuteur.isBlank()) {
            return livreTitre + " — " + livreAuteur;
        }
        return livreTitre;
    }

    /**
     * Représentation textuelle synthétique pour les logs et le suivi.
     *
     * @return libellé de l'emprunt
     */
    @Override
    public String toString() {
        String status = isEnCours() ? (isEnRetard() ? "⚠️ EN RETARD" : "📖 En cours") : "✅ Rendu";
        return "Emprunt #" + id + " [" + utilisateurNumero + " → ex." + exemplaireId + "] " + status;
    }
}
