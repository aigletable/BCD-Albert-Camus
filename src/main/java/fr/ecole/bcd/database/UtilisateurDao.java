package fr.ecole.bcd.database;

import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.model.Utilisateur.TypeCompte;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet d'accès aux données (DAO) pour la table relationnelle {@code utilisateurs}.
 *
 * <p>Cette classe centralise l'ensemble des opérations CRUD (Création, Lecture, Mise à jour,
 * Suppression) et des requêtes de filtrage relatives aux abonnés de la bibliothèque scolaire BCD Express.
 * Elle prend en charge :</p>
 * <ul>
 *   <li>L'enregistrement et la mise à jour des usagers (élèves et enseignants).</li>
 *   <li>La recherche multi-critères insensible à la casse (par numéro, nom ou prénom).</li>
 *   <li>Le filtrage ciblé par classe scolaire (ex. "CP", "CE1-A") ou par rôle ({@link TypeCompte}).</li>
 *   <li>L'extraction de la liste unique des classes enregistrées dans l'établissement.</li>
 *   <li>Le respect des contraintes d'intégrité référentielle SQLite (contrainte {@code ON DELETE RESTRICT} avec les emprunts).</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class UtilisateurDao {

    /**
     * Constructeur par défaut de {@code UtilisateurDao}.
     */
    public UtilisateurDao() {
    }

    // =====================================================================
    //  CREATE
    // =====================================================================

    /**
     * Insère un nouvel utilisateur dans la base de données SQLite.
     *
     * @param u l'entité {@link Utilisateur} à persister (le numéro doit être non nul et unique)
     * @throws SQLException si une contrainte d'unicité est violée (ex. numéro déjà existant)
     *                      ou en cas d'erreur JDBC
     */
    public void insert(Utilisateur u) throws SQLException {
        String sql = "INSERT INTO utilisateurs (numero, nom, prenom, type_compte, classe) "
                   + "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, u.getNumero());
            ps.setString(2, u.getNom());
            ps.setString(3, u.getPrenom());
            ps.setString(4, u.getTypeCompte().name());
            ps.setString(5, u.getClasse());
            ps.executeUpdate();
        }
    }

    // =====================================================================
    //  READ
    // =====================================================================

    /**
     * Recherche un utilisateur par sa clé primaire métier (numéro d'identification unique).
     *
     * @param numero le numéro unique de l'utilisateur (ex. "E-101", "ENS-02")
     * @return l'objet {@link Utilisateur} trouvé, ou {@code null} si aucun usager ne correspond
     * @throws SQLException en cas d'erreur lors de l'interrogation de la base
     */
    public Utilisateur findByNumero(String numero) throws SQLException {
        String sql = "SELECT * FROM utilisateurs WHERE numero = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Récupère l'intégralité des utilisateurs enregistrés dans la bibliothèque.
     * Les résultats sont triés par ordre hiérarchique : type de compte, puis nom et prénom.
     *
     * @return la liste exhaustive des {@link Utilisateur} enregistrés
     * @throws SQLException en cas de dysfonctionnement de la requête
     */
    public List<Utilisateur> findAll() throws SQLException {
        String sql = "SELECT * FROM utilisateurs ORDER BY type_compte, nom, prenom";
        List<Utilisateur> list = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Effectue une recherche textuelle floue parmi les usagers.
     * Le terme est comparé par correspondance partielle ({@code LIKE %terme%}) avec le nom,
     * le prénom et le numéro d'usager.
     *
     * @param terme la chaîne de recherche saisie par l'utilisateur
     * @return la liste des {@link Utilisateur} correspondants, triés par type puis patronyme
     * @throws SQLException en cas d'anomalie SQL
     */
    public List<Utilisateur> search(String terme) throws SQLException {
        String sql = "SELECT * FROM utilisateurs "
                   + "WHERE nom LIKE ? OR prenom LIKE ? OR numero LIKE ? "
                   + "ORDER BY type_compte, nom, prenom";
        List<Utilisateur> list = new ArrayList<>();
        String pattern = "%" + terme + "%";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Récupère la liste de tous les utilisateurs affectés à une classe scolaire donnée.
     *
     * @param classe l'intitulé de la classe (ex. "CM2-B", "Maternelle")
     * @return la liste des utilisateurs inscrits dans cette classe, triés par nom et prénom
     * @throws SQLException en cas d'erreur de requête
     */
    public List<Utilisateur> findByClasse(String classe) throws SQLException {
        String sql = "SELECT * FROM utilisateurs WHERE classe = ? ORDER BY nom, prenom";
        List<Utilisateur> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, classe);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Filtre et retourne les utilisateurs selon leur catégorie de compte (Élève ou Enseignant).
     *
     * @param type le type de compte ciblé ({@link TypeCompte#ELEVE} ou {@link TypeCompte#ENSEIGNANT}).
     *             Si {@code null}, l'ensemble des usagers est retourné sans restriction.
     * @return la liste des utilisateurs correspondant au profil spécifié
     * @throws SQLException en cas d'erreur SQL
     */
    public List<Utilisateur> findByType(Utilisateur.TypeCompte type) throws SQLException {
        if (type == null) return findAll();
        String sql = "SELECT * FROM utilisateurs WHERE type_compte = ? ORDER BY nom, prenom";
        List<Utilisateur> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Récupère la liste distincte de toutes les classes scolaires existantes au sein de la base.
     * Cette liste est utilisée pour alimenter les listes déroulantes de filtres et de formulaires.
     *
     * @return la liste ordonnée alphabétiquement de tous les noms de classes distincts
     * @throws SQLException en cas d'erreur de lecture
     */
    public List<String> findAllClasses() throws SQLException {
        String sql = "SELECT DISTINCT classe FROM utilisateurs ORDER BY classe";
        List<String> classes = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                classes.add(rs.getString("classe"));
            }
        }
        return classes;
    }

    // =====================================================================
    //  UPDATE
    // =====================================================================

    /**
     * Met à jour les informations d'un utilisateur existant identifié par son numéro.
     *
     * @param u l'objet {@link Utilisateur} contenant les nouvelles données
     * @throws SQLException en cas d'erreur SQL lors de la mise à jour
     */
    public void update(Utilisateur u) throws SQLException {
        String sql = "UPDATE utilisateurs SET nom = ?, prenom = ?, type_compte = ?, classe = ? "
                   + "WHERE numero = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, u.getNom());
            ps.setString(2, u.getPrenom());
            ps.setString(3, u.getTypeCompte().name());
            ps.setString(4, u.getClasse());
            ps.setString(5, u.getNumero());
            ps.executeUpdate();
        }
    }

    // =====================================================================
    //  DELETE
    // =====================================================================

    /**
     * Supprime définitivement un utilisateur à partir de son numéro unique.
     * <p>
     * <b>Attention :</b> La contrainte de clé étrangère sur la table {@code emprunts}
     * étant configurée en {@code ON DELETE RESTRICT}, cette opération échouera par une
     * exception si l'utilisateur possède un historique ou un prêt en cours.
     * </p>
     *
     * @param numero le numéro unique de l'utilisateur à effacer
     * @return {@code true} si la ligne correspondante a été supprimée, {@code false} sinon
     * @throws SQLException si la suppression viole une contrainte d'intégrité ou échoue
     */
    public boolean delete(String numero) throws SQLException {
        String sql = "DELETE FROM utilisateurs WHERE numero = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, numero);
            return ps.executeUpdate() > 0;
        }
    }

    // =====================================================================
    //  Mapping ResultSet → Objet
    // =====================================================================

    /**
     * Transforme une ligne courante de résultat JDBC {@link ResultSet} en instance de modèle {@link Utilisateur}.
     *
     * @param rs le curseur SQL positionné
     * @return un objet {@link Utilisateur} entièrement alimenté
     * @throws SQLException en cas de problème d'accès aux colonnes
     */
    private Utilisateur mapRow(ResultSet rs) throws SQLException {
        Utilisateur u = new Utilisateur();
        u.setNumero(rs.getString("numero"));
        u.setNom(rs.getString("nom"));
        u.setPrenom(rs.getString("prenom"));
        u.setTypeCompte(TypeCompte.valueOf(rs.getString("type_compte")));
        u.setClasse(rs.getString("classe"));
        return u;
    }
}
