package fr.ecole.bcd.database;

import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.LivreAVerifier;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet d'accès aux données (DAO) pour la gestion de la boîte à vérifier et des anomalies d'inventaire.
 *
 * <p>Cette classe gère le stockage et le cycle de vie des enregistrements placés en attente
 * d'intervention humaine dans l'application BCD Express. Elle permet notamment :</p>
 * <ul>
 *   <li>L'enregistrement des scans problématiques (doublons refusés, codes-barres suspects, etc.).</li>
 *   <li>Le suivi du statut de traitement (en attente {@code 'A_VERIFIER'} ou résolu {@code 'RESOLU'}).</li>
 *   <li>La détection des fiches livres "orphelines" ne disposant d'aucun exemplaire physique en rayon.</li>
 *   <li>Le nettoyage et la résolution unitaire ou en masse des alertes.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class BoiteVerificationDao {

    /**
     * Constructeur par défaut.
     * Initialise la table {@code boite_verification} et ses index associés dans la base SQLite.
     */
    public BoiteVerificationDao() {
        initTable();
    }

    /**
     * Crée la table {@code boite_verification} et l'index sur la colonne {@code statut} si ceux-ci n'existent pas.
     * Cette méthode est appelée automatiquement lors de l'instanciation du DAO pour garantir la conformité du schéma.
     */
    public void initTable() {
        String sql = "CREATE TABLE IF NOT EXISTS boite_verification (" +
                "    id               INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    isbn             TEXT NOT NULL," +
                "    titre            TEXT DEFAULT ''," +
                "    auteur           TEXT DEFAULT ''," +
                "    date_scan        TEXT NOT NULL," +
                "    motif            TEXT DEFAULT ''," +
                "    nb_exemplaires   INTEGER DEFAULT 0," +
                "    statut           TEXT NOT NULL DEFAULT 'A_VERIFIER'" +
                ");" +
                "CREATE INDEX IF NOT EXISTS idx_boite_verif_statut ON boite_verification(statut);";

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            for (String query : sql.split(";")) {
                if (!query.isBlank()) {
                    stmt.execute(query.trim());
                }
            }
        } catch (SQLException e) {
            System.err.println("⚠️ Erreur lors de l'initialisation de boite_verification : " + e.getMessage());
        }
    }

    /**
     * Insère un nouvel élément suspect ou en attente d'arbitrage dans la boîte à vérifier.
     *
     * @param item l'entité {@link LivreAVerifier} contenant les métadonnées de l'alerte à insérer
     * @return la clé primaire auto-générée assignée à l'élément inséré, ou -1 en cas d'échec
     * @throws SQLException en cas d'erreur de communication avec la base SQLite
     */
    public int insert(LivreAVerifier item) throws SQLException {
        String sql = "INSERT INTO boite_verification (isbn, titre, auteur, date_scan, motif, nb_exemplaires, statut) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getIsbn() != null ? item.getIsbn() : "");
            ps.setString(2, item.getTitre() != null ? item.getTitre() : "");
            ps.setString(3, item.getAuteur() != null ? item.getAuteur() : "");
            ps.setString(4, item.getDateScan() != null ? item.getDateScan() : "");
            ps.setString(5, item.getMotif() != null ? item.getMotif() : "");
            ps.setInt(6, item.getNbExemplaires());
            ps.setString(7, item.getStatut() != null ? item.getStatut() : "A_VERIFIER");

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    item.setId(id);
                    return id;
                }
            }
        }
        return -1;
    }

    /**
     * Récupère la liste de l'ensemble des éléments actuellement en attente d'arbitrage (statut {@code 'A_VERIFIER'}).
     * Les résultats sont triés par ordre antéchronologique (du plus récent au plus ancien).
     *
     * @return la liste ordonnée des objets {@link LivreAVerifier} en attente
     * @throws SQLException si une erreur survient lors de l'exécution de la requête
     */
    public List<LivreAVerifier> findAllPending() throws SQLException {
        String sql = "SELECT * FROM boite_verification WHERE statut = 'A_VERIFIER' ORDER BY id DESC";
        List<LivreAVerifier> list = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Compte le nombre total d'éléments en attente de vérification (statut {@code 'A_VERIFIER'}).
     *
     * @return le décompte des éléments en suspens, ou 0 si aucun ou en cas d'erreur SQL
     */
    public int countPending() {
        String sql = "SELECT COUNT(*) FROM boite_verification WHERE statut = 'A_VERIFIER'";
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    /**
     * Supprime définitivement une entrée de la boîte à vérifier à partir de son identifiant technique.
     *
     * @param id l'identifiant unique de la ligne dans la table {@code boite_verification}
     * @return {@code true} si la ligne a été supprimée, {@code false} sinon
     * @throws SQLException si une erreur SQL se produit
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM boite_verification WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Marque un élément comme résolu (statut mis à jour vers {@code 'RESOLU'}).
     *
     * @param id l'identifiant de l'élément à clôturer
     * @return {@code true} si la mise à jour a affecté au moins une ligne, {@code false} sinon
     * @throws SQLException si une erreur SQL se produit
     */
    public boolean resolve(int id) throws SQLException {
        String sql = "UPDATE boite_verification SET statut = 'RESOLU' WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Vide intégralement la table de la boîte à vérifier (suppression en masse de toutes les entrées).
     *
     * @return {@code true} si la suppression a été exécutée avec succès
     * @throws SQLException si une erreur SQL survient
     */
    public boolean clearAll() throws SQLException {
        String sql = "DELETE FROM boite_verification";
        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            return stmt.executeUpdate(sql) >= 0;
        }
    }

    /**
     * Détecte et extrait la liste de toutes les fiches d'ouvrages cataloguées ne possédant
     * aucun exemplaire physique en base (jointure externe gauche avec la table {@code exemplaires}).
     *
     * @return la liste des objets {@link Livre} orphelins (avec 0 exemplaire total et disponible)
     * @throws SQLException si une erreur SQL survient lors de la jointure
     */
    public List<Livre> findLivresSansExemplaire() throws SQLException {
        String sql = "SELECT l.*, 0 AS total_ex, 0 AS dispo " +
                "FROM livres l " +
                "LEFT JOIN exemplaires e ON l.isbn = e.isbn " +
                "WHERE e.id IS NULL " +
                "ORDER BY l.titre ASC";

        List<Livre> list = new ArrayList<>();
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Livre l = new Livre(
                        rs.getString("isbn"),
                        rs.getString("titre"),
                        rs.getString("auteur"),
                        rs.getString("editeur"),
                        rs.getString("couverture_url"),
                        rs.getString("genre"),
                        rs.getString("theme"),
                        rs.getString("niveau"),
                        rs.getString("description")
                );
                l.setNbExemplaires(0);
                l.setNbDisponibles(0);
                list.add(l);
            }
        }
        return list;
    }

    /**
     * Dénombre les fiches livres du catalogue qui ne sont rattachées à aucun exemplaire physique.
     *
     * @return le nombre de livres orphelins détectés
     */
    public int countLivresSansExemplaire() {
        String sql = "SELECT COUNT(*) FROM livres l LEFT JOIN exemplaires e ON l.isbn = e.isbn WHERE e.id IS NULL";
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException ignored) {}
        return 0;
    }

    /**
     * Mappe une ligne d'un {@link ResultSet} JDBC vers une nouvelle instance de {@link LivreAVerifier}.
     *
     * @param rs le curseur positionné sur la ligne courante du résultat SQL
     * @return un objet {@link LivreAVerifier} hydraté avec les valeurs de la ligne
     * @throws SQLException si la lecture d'une colonne échoue
     */
    private LivreAVerifier mapRow(ResultSet rs) throws SQLException {
        return new LivreAVerifier(
                rs.getInt("id"),
                rs.getString("isbn"),
                rs.getString("titre"),
                rs.getString("auteur"),
                rs.getString("date_scan"),
                rs.getString("motif"),
                rs.getInt("nb_exemplaires"),
                rs.getString("statut")
        );
    }
}
