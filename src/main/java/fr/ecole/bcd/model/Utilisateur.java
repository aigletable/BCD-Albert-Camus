package fr.ecole.bcd.model;

/**
 * Entité métier représentant un utilisateur de la BCD : élève ou enseignant.
 * <p>
 * Mappe la table {@code utilisateurs} de la base de données SQLite.
 * La convention de numérotation standardisée utilise le préfixe {@code E-} pour les élèves
 * (ex. {@code E-101}) et {@code P-} pour les enseignants / personnels (ex. {@code P-01}).
 * </p>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class Utilisateur {

    /**
     * Types de comptes autorisés dans la BCD (contrainte SQL {@code CHECK}).
     */
    public enum TypeCompte {
        /** Compte élève avec quota standard d'emprunts. */
        ELEVE,

        /** Compte enseignant / personnel éducatif avec accès aux paniers et séries de classe. */
        ENSEIGNANT
    }

    /** Identifiant unique et immuable de l'usager (clé primaire, ex. {@code E-101}, {@code P-01}). */
    private String numero;

    /** Nom de famille de l'usager. */
    private String nom;

    /** Prénom de l'usager. */
    private String prenom;

    /** Type de profil utilisateur (élève ou enseignant). */
    private TypeCompte typeCompte;

    /** Classe ou affectation scolaire (ex. {@code CM1-A}, {@code CP}, {@code Direction}). */
    private String classe;

    /**
     * Constructeur par défaut sans argument (requis pour les instanciations DAO et réflexives).
     */
    public Utilisateur() { }

    /**
     * Constructeur complet initialisant tous les champs de l'usager.
     *
     * @param numero identifiant unique d'usager (ex. {@code E-101})
     * @param nom nom de famille
     * @param prenom prénom
     * @param typeCompte profil élève ou enseignant
     * @param classe classe scolaire ou groupe pédagogique
     */
    public Utilisateur(String numero, String nom, String prenom,
                       TypeCompte typeCompte, String classe) {
        this.numero = numero;
        this.nom = nom;
        this.prenom = prenom;
        this.typeCompte = typeCompte;
        this.classe = classe;
    }

    /**
     * Retourne le numéro unique d'usager.
     *
     * @return le numéro d'identifiant
     */
    public String getNumero() { return numero; }

    /**
     * Définit le numéro unique d'usager.
     *
     * @param numero nouvel identifiant unique
     */
    public void setNumero(String numero) { this.numero = numero; }

    /**
     * Retourne le nom de famille de l'usager.
     *
     * @return le nom
     */
    public String getNom() { return nom; }

    /**
     * Définit le nom de famille de l'usager.
     *
     * @param nom nouveau nom
     */
    public void setNom(String nom) { this.nom = nom; }

    /**
     * Retourne le prénom de l'usager.
     *
     * @return le prénom
     */
    public String getPrenom() { return prenom; }

    /**
     * Définit le prénom de l'usager.
     *
     * @param prenom nouveau prénom
     */
    public void setPrenom(String prenom) { this.prenom = prenom; }

    /**
     * Retourne le type de compte de l'usager.
     *
     * @return {@link TypeCompte#ELEVE} ou {@link TypeCompte#ENSEIGNANT}
     */
    public TypeCompte getTypeCompte() { return typeCompte; }

    /**
     * Définit le type de compte de l'usager.
     *
     * @param typeCompte nouveau profil
     */
    public void setTypeCompte(TypeCompte typeCompte) { this.typeCompte = typeCompte; }

    /**
     * Retourne la classe scolaire ou affectation de l'usager.
     *
     * @return la classe (ex. {@code CM2-B})
     */
    public String getClasse() { return classe; }

    /**
     * Définit la classe scolaire ou affectation de l'usager.
     *
     * @param classe nouvelle classe
     */
    public void setClasse(String classe) { this.classe = classe; }

    /**
     * Retourne une représentation textuelle conviviale : "Prénom NOM (Numéro)".
     *
     * @return chaîne formatée pour l'UI
     */
    @Override
    public String toString() {
        return prenom + " " + (nom != null ? nom.toUpperCase() : "") + " (" + numero + ")";
    }
}
