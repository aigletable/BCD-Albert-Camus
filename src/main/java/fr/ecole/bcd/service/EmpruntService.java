package fr.ecole.bcd.service;

import fr.ecole.bcd.database.EmpruntDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.model.BilanStatistiques;
import fr.ecole.bcd.model.Emprunt;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Utilisateur;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Service métier pour la gestion des emprunts.
 * <p>
 * Encapsule les règles de gestion :
 * <ul>
 *   <li>Un élève peut emprunter au maximum {@value #MAX_EMPRUNTS_ELEVE} livres simultanément.</li>
 *   <li>Un enseignant peut emprunter au maximum {@value #MAX_EMPRUNTS_ENSEIGNANT} livres.</li>
 *   <li>Un utilisateur ayant un retard ne peut pas emprunter de nouveau livre.</li>
 *   <li>Un emprunt ne peut être renouvelé qu'une seule fois.</li>
 *   <li>L'exemplaire doit être disponible pour pouvoir être emprunté.</li>
 * </ul>
 * </p>
 */
public class EmpruntService {

    // ── Règles métier configurables ─────────────────────────────────────
    public static final int MAX_EMPRUNTS_ELEVE = 3;
    public static final int MAX_EMPRUNTS_ENSEIGNANT = 5;
    public static final int DUREE_PRET_JOURS = 7;

    private final EmpruntDao empruntDao;
    private final ExemplaireDao exemplaireDao;

    public EmpruntService() {
        this.empruntDao = new EmpruntDao();
        this.exemplaireDao = new ExemplaireDao();
    }

    // =====================================================================
    //  Emprunter
    // =====================================================================

    /**
     * Crée un emprunt après vérification de toutes les règles métier.
     *
     * @param utilisateur l'emprunteur
     * @param exemplaireId l'id de l'exemplaire à emprunter
     * @return l'emprunt créé
     * @throws RegleMetierException si une règle est violée
     * @throws SQLException en cas d'erreur technique
     */
    public Emprunt emprunter(Utilisateur utilisateur, int exemplaireId)
            throws RegleMetierException, SQLException {

        // 1. Vérifier que l'exemplaire existe
        Exemplaire exemplaire = exemplaireDao.findById(exemplaireId);
        if (exemplaire == null) {
            throw new RegleMetierException("L'exemplaire #" + exemplaireId + " n'existe pas.");
        }

        // 2. Vérifier que l'exemplaire est disponible
        if (exemplaire.getStatut() != Exemplaire.Statut.DISPONIBLE) {
            throw new RegleMetierException(
                    "L'exemplaire #" + exemplaireId + " n'est pas disponible (statut : "
                    + exemplaire.getStatut() + ").");
        }

        // 2.5. Vérifier si l'exemplaire est gelé par une session d'inventaire en cours (Stock Freeze)
        InventaireSessionService invSessionService = new InventaireSessionService();
        if (invSessionService.isExemplaireGele(exemplaireId)) {
            throw new RegleMetierException(
                    "Emprunt impossible : l'exemplaire #" + exemplaireId + " fait partie d'une session d'inventaire active (stock gelé sur ce rayon).");
        }

        // 3. Vérifier que l'utilisateur n'a pas de retard
        if (empruntDao.hasRetard(utilisateur.getNumero())) {
            throw new RegleMetierException(
                    utilisateur.getPrenom() + " " + utilisateur.getNom()
                    + " a des livres en retard. Retour obligatoire avant tout nouvel emprunt.");
        }

        // 4. Vérifier le quota d'emprunts
        int empruntsEnCours = empruntDao.countEnCours(utilisateur.getNumero());
        int max = getMaxEmprunts(utilisateur);

        if (empruntsEnCours >= max) {
            throw new RegleMetierException(
                    utilisateur.getPrenom() + " " + utilisateur.getNom()
                    + " a déjà " + empruntsEnCours + " emprunt(s) en cours "
                    + "(maximum autorisé : " + max + ").");
        }

        // 5. Tout est OK → créer l'emprunt et marquer l'exemplaire comme emprunté
        Emprunt emprunt = empruntDao.creerEmprunt(utilisateur.getNumero(), exemplaireId);
        exemplaireDao.updateStatut(exemplaireId, Exemplaire.Statut.EMPRUNTE);

        System.out.println("📖 Emprunt créé : " + emprunt);
        return emprunt;
    }

    /**
     * Crée un emprunt directement en spécifiant le nom / titre du livre (sans avoir besoin de l'ISBN).
     * Recherche automatiquement un exemplaire physique disponible pour cet ouvrage.
     *
     * @param utilisateur l'emprunteur
     * @param titre nom ou extrait de titre du livre
     * @return l'emprunt validé
     * @throws RegleMetierException si aucun exemplaire n'est disponible ou si quota/règles dépassés
     */
    public Emprunt emprunterParTitre(Utilisateur utilisateur, String titre)
            throws RegleMetierException, SQLException {
        if (titre == null || titre.isBlank()) {
            throw new RegleMetierException("Veuillez indiquer le nom du livre à emprunter.");
        }
        List<Exemplaire> dispos = exemplaireDao.findDisponiblesByTitre(titre.trim());
        if (dispos.isEmpty()) {
            int total = exemplaireDao.countByTitre(titre.trim());
            if (total > 0) {
                throw new RegleMetierException("Tous les exemplaires (" + total + ") du livre '" + titre + "' sont actuellement prêtés.");
            } else {
                throw new RegleMetierException("Aucun livre intitulé '" + titre + "' n'a été trouvé dans le catalogue.");
            }
        }
        Exemplaire premierDispo = dispos.get(0);
        return emprunter(utilisateur, premierDispo.getId());
    }

    /**
     * Crée un prêt groupé pour une série de classe / panier enseignant.
     * Permet d'emprunter plusieurs livres en un seul lot avec une durée personnalisée.
     *
     * @param utilisateur  l'emprunteur (enseignant)
     * @param exemplaireIds liste des identifiants d'exemplaires à prêter
     * @param dateLimite    date de retour convenue
     * @return la liste des emprunts créés
     * @throws RegleMetierException si une condition n'est pas remplie
     * @throws SQLException         en cas d'erreur SQL
     */
    public List<Emprunt> emprunterPanier(Utilisateur utilisateur, List<Integer> exemplaireIds, LocalDate dateLimite)
            throws RegleMetierException, SQLException {

        if (utilisateur == null) {
            throw new RegleMetierException("Veuillez sélectionner un enseignant / emprunteur.");
        }
        if (exemplaireIds == null || exemplaireIds.isEmpty()) {
            throw new RegleMetierException("Le panier est vide. Veuillez scanner au moins un livre.");
        }
        if (dateLimite == null || dateLimite.isBefore(LocalDate.now())) {
            throw new RegleMetierException("La date de retour doit être fixée à aujourd'hui ou dans le futur.");
        }

        InventaireSessionService invService = new InventaireSessionService();
        for (int exId : exemplaireIds) {
            if (invService.isExemplaireGele(exId)) {
                throw new RegleMetierException(
                        "L'exemplaire #" + exId + " fait partie d'une session d'inventaire en cours (stock gelé). Emprunt impossible.");
            }
        }

        // Vérification préalable de l'existence et de la disponibilité de tous les exemplaires
        for (int exId : exemplaireIds) {
            Exemplaire ex = exemplaireDao.findById(exId);
            if (ex == null) {
                throw new RegleMetierException("L'exemplaire #" + exId + " n'existe pas.");
            }
            if (ex.getStatut() != Exemplaire.Statut.DISPONIBLE) {
                throw new RegleMetierException("L'exemplaire #" + exId + " (" + ex.getIsbn() + ") n'est plus disponible.");
            }
        }

        // Création des emprunts et mise à jour du statut des exemplaires
        List<Emprunt> result = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int exId : exemplaireIds) {
            Emprunt emp = empruntDao.creerEmpruntAvecDateLimite(utilisateur.getNumero(), exId, now, dateLimite);
            exemplaireDao.updateStatut(exId, Exemplaire.Statut.EMPRUNTE);
            result.add(emp);
        }

        System.out.println("🧺 Panier de prêt validé : " + result.size() + " livres pour " + utilisateur.getNom());
        return result;
    }

    // =====================================================================
    //  Retourner
    // =====================================================================

    /**
     * Enregistre le retour d'un emprunt et remet l'exemplaire en disponible.
     *
     * @param empruntId l'id de l'emprunt à retourner
     * @throws RegleMetierException si l'emprunt n'existe pas ou est déjà rendu
     */
    public void retourner(int empruntId) throws RegleMetierException, SQLException {

        Emprunt emprunt = empruntDao.findById(empruntId);
        if (emprunt == null) {
            throw new RegleMetierException("L'emprunt #" + empruntId + " n'existe pas.");
        }
        if (!emprunt.isEnCours()) {
            throw new RegleMetierException("L'emprunt #" + empruntId + " est déjà rendu.");
        }

        // Enregistrer le retour
        empruntDao.retourner(empruntId);

        // Remettre l'exemplaire en disponible
        exemplaireDao.updateStatut(emprunt.getExemplaireId(), Exemplaire.Statut.DISPONIBLE);

        System.out.println("✅ Retour enregistré : emprunt #" + empruntId);
    }

    /**
     * Retour rapide : retrouve l'emprunt en cours de l'exemplaire et le retourne.
     *
     * @param exemplaireId l'id de l'exemplaire rendu
     * @return l'emprunt qui a été retourné
     * @throws RegleMetierException si aucun emprunt en cours pour cet exemplaire
     */
    public Emprunt retournerParExemplaire(int exemplaireId)
            throws RegleMetierException, SQLException {

        Emprunt emprunt = empruntDao.findEnCoursByExemplaire(exemplaireId);
        if (emprunt == null) {
            throw new RegleMetierException(
                    "Aucun emprunt en cours pour l'exemplaire #" + exemplaireId + ".");
        }

        retourner(emprunt.getId());
        emprunt.setDateRendu(LocalDate.now());
        return emprunt;
    }

    /**
     * Retour rapide par code-barres ISBN (douchette) :
     * retrouve l'emprunt en cours pour ce livre et le retourne.
     *
     * @param isbn l'ISBN scanné à la douchette
     * @return l'emprunt retourné avec ses détails
     * @throws RegleMetierException si aucun emprunt en cours pour cet ISBN
     */
    public Emprunt retournerParIsbn(String isbn) throws RegleMetierException, SQLException {
        List<Emprunt> enCours = empruntDao.findEnCoursByIsbnWithDetails(isbn);
        if (enCours.isEmpty()) {
            throw new RegleMetierException("Aucun exemplaire de ce livre (ISBN " + isbn + ") n'est actuellement emprunté (déjà disponible en rayon).");
        }
        Emprunt toReturn = enCours.get(0);
        retourner(toReturn.getId());
        toReturn.setDateRendu(LocalDate.now());
        return toReturn;
    }

    /**
     * Retour rapide par nom / titre du livre :
     * retrouve l'emprunt en cours pour ce titre de livre et enregistre son retour.
     *
     * @param titre le nom ou extrait du titre du livre rendu
     * @return l'emprunt retourné avec ses détails
     * @throws RegleMetierException si aucun emprunt en cours pour ce livre
     */
    public Emprunt retournerParTitre(String titre) throws RegleMetierException, SQLException {
        if (titre == null || titre.isBlank()) {
            throw new RegleMetierException("Veuillez spécifier le titre du livre à retourner.");
        }
        List<Emprunt> enCours = empruntDao.findEnCoursByTitreWithDetails(titre.trim());
        if (enCours.isEmpty()) {
            throw new RegleMetierException("Aucun emprunt en cours n'a été trouvé pour le livre '" + titre.trim() + "'.");
        }
        Emprunt toReturn = enCours.get(0);
        retourner(toReturn.getId());
        toReturn.setDateRendu(LocalDate.now());
        return toReturn;
    }

    // =====================================================================
    //  Renouveler
    // =====================================================================

    /**
     * Renouvelle un emprunt (repousse la date limite de 7 jours).
     * Un emprunt ne peut être renouvelé qu'une seule fois.
     *
     * @param empruntId l'id de l'emprunt à renouveler
     * @throws RegleMetierException si l'emprunt est déjà renouvelé ou rendu
     */
    public void renouveler(int empruntId) throws RegleMetierException, SQLException {

        Emprunt emprunt = empruntDao.findById(empruntId);
        if (emprunt == null) {
            throw new RegleMetierException("L'emprunt #" + empruntId + " n'existe pas.");
        }
        if (!emprunt.isEnCours()) {
            throw new RegleMetierException("L'emprunt #" + empruntId + " est déjà rendu.");
        }
        if (emprunt.isEstRenouvele()) {
            throw new RegleMetierException(
                    "L'emprunt #" + empruntId + " a déjà été renouvelé une fois "
                    + "(un seul renouvellement autorisé).");
        }

        empruntDao.renouveler(empruntId);
        System.out.println("🔄 Emprunt #" + empruntId + " renouvelé.");
    }

    // =====================================================================
    //  Requêtes déléguées
    // =====================================================================

    /** Retourne la liste des emprunts en cours (tous utilisateurs). */
    public List<Emprunt> getEmpruntsEnCours() throws SQLException {
        return empruntDao.findEnCours();
    }

    /** Retourne la liste des emprunts en cours avec détails (utilisateur + livre). */
    public List<Emprunt> getEmpruntsEnCoursWithDetails() throws SQLException {
        return empruntDao.findAllEnCoursWithDetails();
    }

    /** Retourne la liste des emprunts en retard. */
    public List<Emprunt> getEmpruntsEnRetard() throws SQLException {
        return empruntDao.findEnRetard();
    }

    /** Retourne les emprunts en cours d'un utilisateur. */
    public List<Emprunt> getEmpruntsEnCours(String utilisateurNumero) throws SQLException {
        return empruntDao.findEnCoursByUtilisateur(utilisateurNumero);
    }

    /** Retourne les emprunts en cours correspondant à un nom/titre de livre. */
    public List<Emprunt> getEmpruntsEnCoursParTitre(String titre) throws SQLException {
        return empruntDao.findEnCoursByTitreWithDetails(titre);
    }

    /**
     * Retourne le bilan statistique complet pour la direction et le conseil d'école.
     */
    public BilanStatistiques getBilanStatistiques() throws SQLException {
        return empruntDao.getBilanStatistiques();
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    /**
     * Retourne le quota maximum d'emprunts selon le type d'utilisateur.
     */
    public int getMaxEmprunts(Utilisateur u) {
        return u.getTypeCompte() == Utilisateur.TypeCompte.ENSEIGNANT
                ? MAX_EMPRUNTS_ENSEIGNANT
                : MAX_EMPRUNTS_ELEVE;
    }

    // =====================================================================
    //  Exception métier
    // =====================================================================

    /**
     * Exception levée lorsqu'une règle métier est violée.
     * Contient un message en français destiné à être affiché à l'utilisateur.
     */
    public static class RegleMetierException extends Exception {
        public RegleMetierException(String message) {
            super(message);
        }
    }
}
