package fr.ecole.bcd.database;

import fr.ecole.bcd.model.RessourcePedagogique;
import fr.ecole.bcd.model.RessourcePedagogique.TypeRessource;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO pour la persistance et l'interrogation des ressources pédagogiques
 * (fiches d'activités PDF, liens web éditeurs/Éduscol, documents d'exploitation).
 *
 * @author JC ART
 * @since 2026-11-16
 */
public class RessourcePedagogiqueDao {

    public RessourcePedagogiqueDao() {}

    /**
     * Insère une nouvelle ressource pédagogique en base SQLite.
     *
     * @param ressource l'entité à insérer
     * @return l'identifiant généré
     * @throws SQLException en cas d'erreur SQL
     */
    public int insert(RessourcePedagogique ressource) throws SQLException {
        if (ressource == null) {
            throw new IllegalArgumentException("La ressource ne peut pas être null.");
        }

        String sql = "INSERT INTO ressources_pedagogiques (isbn, mallette_id, titre, type, url_ou_chemin, description, date_ajout) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (ressource.getIsbn() != null && !ressource.getIsbn().isBlank()) {
                ps.setString(1, ressource.getIsbn().trim());
            } else {
                ps.setNull(1, Types.VARCHAR);
            }
            if (ressource.getMalletteId() != null && ressource.getMalletteId() > 0) {
                ps.setInt(2, ressource.getMalletteId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, ressource.getTitre().trim());
            ps.setString(4, ressource.getType() != null ? ressource.getType().name() : TypeRessource.LIEN_WEB.name());
            ps.setString(5, ressource.getUrlOuChemin().trim());
            ps.setString(6, ressource.getDescription() != null ? ressource.getDescription().trim() : "");
            ps.setString(7, ressource.getDateAjout() != null ? ressource.getDateAjout().toString() : LocalDate.now().toString());

            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    int id = gk.getInt(1);
                    ressource.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Échec de la création de la ressource pédagogique : aucun ID généré.");
    }

    /**
     * Met à jour les informations d'une ressource existante.
     */
    public boolean update(RessourcePedagogique ressource) throws SQLException {
        if (ressource == null || ressource.getId() <= 0) return false;

        String sql = "UPDATE ressources_pedagogiques SET titre = ?, type = ?, url_ou_chemin = ?, description = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, ressource.getTitre().trim());
            ps.setString(2, ressource.getType() != null ? ressource.getType().name() : TypeRessource.LIEN_WEB.name());
            ps.setString(3, ressource.getUrlOuChemin().trim());
            ps.setString(4, ressource.getDescription() != null ? ressource.getDescription().trim() : "");
            ps.setInt(5, ressource.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Supprime une ressource pédagogique par son ID.
     */
    public boolean delete(int id) throws SQLException {
        String sql = "DELETE FROM ressources_pedagogiques WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Récupère une ressource par son ID.
     */
    public RessourcePedagogique findById(int id) throws SQLException {
        String sql = "SELECT r.*, l.titre AS livre_titre, m.titre AS mallette_titre "
                   + "FROM ressources_pedagogiques r "
                   + "LEFT JOIN livres l ON r.isbn = l.isbn "
                   + "LEFT JOIN mallettes m ON r.mallette_id = m.id "
                   + "WHERE r.id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        }
        return null;
    }

    /**
     * Récupère toutes les ressources associées à un livre (via son ISBN).
     */
    public List<RessourcePedagogique> findByIsbn(String isbn) throws SQLException {
        List<RessourcePedagogique> list = new ArrayList<>();
        if (isbn == null || isbn.isBlank()) return list;

        String sql = "SELECT r.*, l.titre AS livre_titre, m.titre AS mallette_titre "
                   + "FROM ressources_pedagogiques r "
                   + "LEFT JOIN livres l ON r.isbn = l.isbn "
                   + "LEFT JOIN mallettes m ON r.mallette_id = m.id "
                   + "WHERE r.isbn = ? "
                   + "ORDER BY r.id DESC";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn.trim());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Récupère les ressources directement rattachées à la mallette en tant qu'ensemble.
     */
    public List<RessourcePedagogique> findByMallette(int malletteId) throws SQLException {
        List<RessourcePedagogique> list = new ArrayList<>();
        String sql = "SELECT r.*, l.titre AS livre_titre, m.titre AS mallette_titre "
                   + "FROM ressources_pedagogiques r "
                   + "LEFT JOIN livres l ON r.isbn = l.isbn "
                   + "LEFT JOIN mallettes m ON r.mallette_id = m.id "
                   + "WHERE r.mallette_id = ? "
                   + "ORDER BY r.id DESC";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Récupère TOUTES les ressources pédagogiques pertinentes pour une mallette :
     * 1. Celles attachées directement à la mallette.
     * 2. Celles associées à tous les livres présents dans cette mallette.
     */
    public List<RessourcePedagogique> findAllForMallette(int malletteId) throws SQLException {
        List<RessourcePedagogique> list = new ArrayList<>();
        String sql = "SELECT DISTINCT r.*, l.titre AS livre_titre, m.titre AS mallette_titre "
                   + "FROM ressources_pedagogiques r "
                   + "LEFT JOIN livres l ON r.isbn = l.isbn "
                   + "LEFT JOIN mallettes m ON r.mallette_id = m.id "
                   + "WHERE r.mallette_id = ? "
                   + "   OR r.isbn IN ("
                   + "       SELECT DISTINCT e.isbn "
                   + "       FROM mallette_items mi "
                   + "       JOIN exemplaires e ON mi.exemplaire_id = e.id "
                   + "       WHERE mi.mallette_id = ?"
                   + "   ) "
                   + "ORDER BY CASE WHEN r.mallette_id = ? THEN 0 ELSE 1 END, l.titre ASC, r.id DESC";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            ps.setInt(2, malletteId);
            ps.setInt(3, malletteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        }
        return list;
    }

    /**
     * Dénombre le nombre de ressources associées à un livre.
     */
    public int countByIsbn(String isbn) throws SQLException {
        if (isbn == null || isbn.isBlank()) return 0;
        String sql = "SELECT COUNT(*) FROM ressources_pedagogiques WHERE isbn = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Dénombre l'ensemble des ressources pédagogiques d'une mallette (mallette + ses livres).
     */
    public int countForMallette(int malletteId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT r.id) "
                   + "FROM ressources_pedagogiques r "
                   + "WHERE r.mallette_id = ? "
                   + "   OR r.isbn IN ("
                   + "       SELECT DISTINCT e.isbn "
                   + "       FROM mallette_items mi "
                   + "       JOIN exemplaires e ON mi.exemplaire_id = e.id "
                   + "       WHERE mi.mallette_id = ?"
                   + "   )";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            ps.setInt(2, malletteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private RessourcePedagogique mapResultSet(ResultSet rs) throws SQLException {
        RessourcePedagogique r = new RessourcePedagogique();
        r.setId(rs.getInt("id"));
        r.setIsbn(rs.getString("isbn"));
        int mId = rs.getInt("mallette_id");
        if (!rs.wasNull()) {
            r.setMalletteId(mId);
        }
        r.setTitre(rs.getString("titre"));
        String typeStr = rs.getString("type");
        try {
            r.setType(TypeRessource.valueOf(typeStr));
        } catch (Exception e) {
            r.setType(TypeRessource.LIEN_WEB);
        }
        r.setUrlOuChemin(rs.getString("url_ou_chemin"));
        r.setDescription(rs.getString("description"));
        String d = rs.getString("date_ajout");
        if (d != null && !d.isBlank()) {
            try {
                r.setDateAjout(LocalDate.parse(d));
            } catch (Exception ignored) {}
        }

        try {
            r.setLivreTitre(rs.getString("livre_titre") != null ? rs.getString("livre_titre") : "");
        } catch (SQLException ignored) {}

        try {
            r.setMalletteTitre(rs.getString("mallette_titre") != null ? rs.getString("mallette_titre") : "");
        } catch (SQLException ignored) {}

        return r;
    }
}
