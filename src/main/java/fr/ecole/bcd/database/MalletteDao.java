package fr.ecole.bcd.database;

import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Mallette;
import fr.ecole.bcd.model.MalletteItem;
import fr.ecole.bcd.service.LivreClassificationService;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Objet d'accès aux données (DAO) pour la gestion des mallettes pédagogiques thématiques et de leur inventaire.
 *
 * <p>Cette classe orchestre la persistance des paniers et séries de lecture groupée
 * ("mallettes pédagogiques") mises à disposition des enseignants de l'école. Elle assure :</p>
 * <ul>
 *   <li>La génération automatique de codes d'identification séquentiels (ex. "MAL-001", "MAL-002").</li>
 *   <li>La recherche multi-critères filtrable par mot-clé, par thème et par statut de disponibilité.</li>
 *   <li>La composition dynamique du panier d'exemplaires (ajout/retrait d'ouvrages).</li>
 *   <li>Le suivi du prêt global de la mallette à un enseignant ou à une classe avec date d'échéance.</li>
 *   <li>L'agrégation des thèmes normalisés en synergie avec {@link LivreClassificationService}.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class MalletteDao {

    /**
     * Constructeur par défaut de {@code MalletteDao}.
     */
    public MalletteDao() {
    }

    // =====================================================================
    //  CREATE & CODE GENERATION
    // =====================================================================

    /**
     * Insère une nouvelle mallette pédagogique en base de données SQLite.
     * Si le code de la mallette n'est pas fourni, un identifiant incrémental unique est automatiquement généré.
     *
     * @param mallette l'entité {@link Mallette} à persister
     * @return la clé primaire auto-générée assignée à la mallette
     * @throws IllegalArgumentException si {@code mallette} est null
     * @throws SQLException si une erreur SQL survient lors de l'insertion
     */
    public int insert(Mallette mallette) throws SQLException {
        if (mallette == null) {
            throw new IllegalArgumentException("La mallette ne peut pas être null.");
        }

        if (mallette.getCode() == null || mallette.getCode().isBlank()) {
            mallette.setCode(genererCodeSuivant());
        }

        String sql = "INSERT INTO mallettes (code, titre, theme, niveau, description, statut, date_creation) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, mallette.getCode().trim().toUpperCase());
            ps.setString(2, mallette.getTitre().trim());
            ps.setString(3, mallette.getTheme() != null ? mallette.getTheme().trim() : "Général");
            ps.setString(4, mallette.getNiveau() != null ? mallette.getNiveau().trim() : "");
            ps.setString(5, mallette.getDescription() != null ? mallette.getDescription().trim() : "");
            ps.setString(6, mallette.getStatut() != null ? mallette.getStatut().name() : Mallette.Statut.DISPONIBLE.name());
            ps.setString(7, mallette.getDateCreation() != null ? mallette.getDateCreation().toString() : LocalDate.now().toString());

            ps.executeUpdate();
            try (ResultSet gk = ps.getGeneratedKeys()) {
                if (gk.next()) {
                    int id = gk.getInt(1);
                    mallette.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Échec de la création de la mallette : aucun ID généré.");
    }

    /**
     * Calcule et génère le prochain code d'identification séquentiel disponible (au format {@code MAL-XXX}).
     * Vérifie systématiquement l'absence de collision en base.
     *
     * @return un code unique non utilisé (ex. "MAL-004")
     * @throws SQLException en cas de problème lors de la vérification des codes existants
     */
    public String genererCodeSuivant() throws SQLException {
        String sql = "SELECT COUNT(*) FROM mallettes";
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            int count = rs.next() ? rs.getInt(1) : 0;
            int nextNum = count + 1;
            String code = String.format("MAL-%03d", nextNum);
            while (findByCode(code) != null) {
                nextNum++;
                code = String.format("MAL-%03d", nextNum);
            }
            return code;
        }
    }

    // =====================================================================
    //  READ
    // =====================================================================

    /**
     * Recherche une mallette par son identifiant numérique unique en calculant le nombre d'ouvrages associés.
     *
     * @param id la clé primaire de la mallette
     * @return l'objet {@link Mallette} hydraté, ou {@code null} si introuvable
     * @throws SQLException en cas d'erreur SQL
     */
    public Mallette findById(int id) throws SQLException {
        String sql = "SELECT m.*, (SELECT COUNT(*) FROM mallette_items WHERE mallette_id = m.id) as nb_livres "
                   + "FROM mallettes m WHERE m.id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Recherche une mallette à partir de son code textuel unique (ex. "MAL-001"), insensible à la casse.
     *
     * @param code le code de la mallette
     * @return l'entité {@link Mallette} trouvée, ou {@code null} sinon
     * @throws SQLException si la requête échoue
     */
    public Mallette findByCode(String code) throws SQLException {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT m.*, (SELECT COUNT(*) FROM mallette_items WHERE mallette_id = m.id) as nb_livres "
                   + "FROM mallettes m WHERE UPPER(m.code) = UPPER(?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Récupère la liste intégrale des mallettes enregistrées, ordonnée de la plus récente à la plus ancienne.
     *
     * @return la liste ordonnée des objets {@link Mallette}
     * @throws SQLException en cas de dysfonctionnement de la requête
     */
    public List<Mallette> findAll() throws SQLException {
        String sql = "SELECT m.*, (SELECT COUNT(*) FROM mallette_items WHERE mallette_id = m.id) as nb_livres "
                   + "FROM mallettes m ORDER BY m.id DESC";
        List<Mallette> list = new ArrayList<>();
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Recherche avancée multi-critères filtrant les mallettes selon un mot-clé textuel,
     * un thème spécifique et un état de disponibilité.
     *
     * @param query le terme textuel recherché dans le code, le titre, le thème ou la description
     * @param themeFilter le thème sélectionné (ou "Tous" pour désactiver ce filtre)
     * @param statutFilter le statut ciblé (ou "Tous" pour désactiver ce filtre)
     * @return la liste des mallettes correspondant à l'ensemble des critères
     * @throws SQLException si l'exécution de la requête dynamique échoue
     */
    public List<Mallette> search(String query, String themeFilter, String statutFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT m.*, (SELECT COUNT(*) FROM mallette_items WHERE mallette_id = m.id) as nb_livres "
                + "FROM mallettes m WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (query != null && !query.isBlank()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            sql.append(" AND (LOWER(m.code) LIKE ? OR LOWER(m.titre) LIKE ? OR LOWER(m.theme) LIKE ? OR LOWER(m.description) LIKE ?) ");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (themeFilter != null && !themeFilter.isBlank() && !themeFilter.equalsIgnoreCase("Tous")) {
            sql.append(" AND LOWER(m.theme) = LOWER(?) ");
            params.add(themeFilter.trim());
        }

        if (statutFilter != null && !statutFilter.isBlank() && !statutFilter.equalsIgnoreCase("Tous")) {
            sql.append(" AND m.statut = ? ");
            params.add(statutFilter.trim().toUpperCase());
        }

        sql.append(" ORDER BY m.id DESC");

        List<Mallette> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // =====================================================================
    //  UPDATE & DELETE
    // =====================================================================

    /**
     * Met à jour les informations descriptives d'une mallette existante.
     *
     * @param mallette l'objet {@link Mallette} contenant les données modifiées
     * @throws SQLException si la mise à jour échoue en base
     */
    public void update(Mallette mallette) throws SQLException {
        String sql = "UPDATE mallettes SET code = ?, titre = ?, theme = ?, niveau = ?, description = ?, statut = ? "
                   + "WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, mallette.getCode().trim().toUpperCase());
            ps.setString(2, mallette.getTitre().trim());
            ps.setString(3, mallette.getTheme().trim());
            ps.setString(4, mallette.getNiveau() != null ? mallette.getNiveau().trim() : "");
            ps.setString(5, mallette.getDescription() != null ? mallette.getDescription().trim() : "");
            ps.setString(6, mallette.getStatut().name());
            ps.setInt(7, mallette.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Supprime définitivement une mallette à partir de son identifiant technique.
     *
     * @param id la clé primaire de la mallette à supprimer
     * @throws SQLException si la suppression échoue
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM mallettes WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // =====================================================================
    //  GESTION DU PRÊT DE MALLETTE
    // =====================================================================

    /**
     * Enregistre le prêt global d'une mallette pédagogique auprès d'un usager (enseignant ou classe).
     *
     * @param malletteId l'identifiant technique de la mallette
     * @param userNumero le numéro unique de l'emprunteur
     * @param userNom le nom de l'emprunteur
     * @param userClasse la classe associée
     * @param dateEmprunt la date de début du prêt (par défaut aujourd'hui si null)
     * @param dateRetourPrevue la date de restitution convenue
     * @throws SQLException si la mise à jour échoue
     */
    public void setEmprunt(int malletteId, String userNumero, String userNom, String userClasse,
                           LocalDate dateEmprunt, LocalDate dateRetourPrevue) throws SQLException {
        String sql = "UPDATE mallettes SET statut = 'EMPRUNTE', emprunteur_numero = ?, emprunteur_nom = ?, "
                   + "emprunteur_classe = ?, date_emprunt = ?, date_retour_prevue = ? WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, userNumero);
            ps.setString(2, userNom);
            ps.setString(3, userClasse != null ? userClasse : "");
            ps.setString(4, dateEmprunt != null ? dateEmprunt.toString() : LocalDate.now().toString());
            ps.setString(5, dateRetourPrevue != null ? dateRetourPrevue.toString() : null);
            ps.setInt(6, malletteId);
            ps.executeUpdate();
        }
    }

    /**
     * Clôture le prêt d'une mallette et réinitialise son statut à {@link Mallette.Statut#DISPONIBLE}.
     *
     * @param malletteId l'identifiant technique de la mallette restituée
     * @throws SQLException si la réinitialisation échoue
     */
    public void clearEmprunt(int malletteId) throws SQLException {
        String sql = "UPDATE mallettes SET statut = 'DISPONIBLE', emprunteur_numero = NULL, emprunteur_nom = '', "
                   + "emprunteur_classe = '', date_emprunt = NULL, date_retour_prevue = NULL WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            ps.executeUpdate();
        }
    }

    // =====================================================================
    //  ITEMS / LIVRES CONTENUS DANS LA MALLETTE
    // =====================================================================

    /**
     * Associe un exemplaire physique à une mallette pédagogique.
     *
     * @param malletteId l'identifiant de la mallette
     * @param exemplaireId l'identifiant de l'exemplaire à ajouter
     * @return {@code true} si l'exemplaire a été ajouté, {@code false} s'il était déjà présent
     * @throws SQLException si l'insertion échoue
     */
    public boolean addItem(int malletteId, int exemplaireId) throws SQLException {
        String sql = "INSERT OR IGNORE INTO mallette_items (mallette_id, exemplaire_id, date_ajout) VALUES (?, ?, ?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            ps.setInt(2, exemplaireId);
            ps.setString(3, LocalDate.now().toString());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Retire un exemplaire physique d'une mallette.
     *
     * @param malletteId l'identifiant de la mallette
     * @param exemplaireId l'identifiant de l'exemplaire à dissocier
     * @return {@code true} si l'exemplaire a été retiré, {@code false} s'il n'était pas associé
     * @throws SQLException si la suppression échoue
     */
    public boolean removeItem(int malletteId, int exemplaireId) throws SQLException {
        String sql = "DELETE FROM mallette_items WHERE mallette_id = ? AND exemplaire_id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            ps.setInt(2, exemplaireId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Dissocie et vide l'intégralité des exemplaires physiques rattachés à une mallette.
     *
     * @param malletteId l'identifiant de la mallette à vider
     * @throws SQLException si la suppression échoue
     */
    public void removeAllItems(int malletteId) throws SQLException {
        String sql = "DELETE FROM mallette_items WHERE mallette_id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            ps.executeUpdate();
        }
    }

    /**
     * Récupère la liste de tous les exemplaires physiques contenus dans une mallette avec leurs métadonnées d'ouvrage.
     *
     * @param malletteId l'identifiant de la mallette
     * @return la liste des objets {@link MalletteItem} ordonnée par titre de livre
     * @throws SQLException si la jointure échoue
     */
    public List<MalletteItem> getItems(int malletteId) throws SQLException {
        String sql = "SELECT mi.id, mi.mallette_id, mi.exemplaire_id, mi.date_ajout, "
                   + "e.isbn, e.statut as ex_statut, l.titre, l.auteur, l.genre, l.theme "
                   + "FROM mallette_items mi "
                   + "JOIN exemplaires e ON mi.exemplaire_id = e.id "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE mi.mallette_id = ? "
                   + "ORDER BY l.titre ASC, mi.exemplaire_id ASC";

        List<MalletteItem> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MalletteItem item = new MalletteItem();
                    item.setId(rs.getInt("id"));
                    item.setMalletteId(rs.getInt("mallette_id"));
                    item.setExemplaireId(rs.getInt("exemplaire_id"));
                    String da = rs.getString("date_ajout");
                    if (da != null && !da.isBlank()) {
                        item.setDateAjout(LocalDate.parse(da));
                    }
                    item.setIsbn(rs.getString("isbn"));
                    item.setTitre(rs.getString("titre"));
                    item.setAuteur(rs.getString("auteur"));
                    item.setGenre(rs.getString("genre"));
                    item.setTheme(rs.getString("theme"));
                    String st = rs.getString("ex_statut");
                    item.setStatutExemplaire(st != null ? Exemplaire.Statut.valueOf(st) : Exemplaire.Statut.DISPONIBLE);
                    list.add(item);
                }
            }
        }
        return list;
    }

    /**
     * Dénombre le nombre total d'exemplaires physiques contenus dans une mallette.
     *
     * @param malletteId l'identifiant de la mallette
     * @return le nombre de livres associés
     * @throws SQLException si la requête échoue
     */
    public int countItems(int malletteId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM mallette_items WHERE mallette_id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, malletteId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Détermine si un exemplaire physique donné est actuellement réservé ou contenu dans une mallette.
     *
     * @param exemplaireId l'identifiant technique de l'exemplaire
     * @return {@code true} si l'exemplaire est dans une mallette, {@code false} sinon
     * @throws SQLException si la vérification échoue
     */
    public boolean isExemplaireInMallette(int exemplaireId) throws SQLException {
        String sql = "SELECT 1 FROM mallette_items WHERE exemplaire_id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, exemplaireId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Retrouve la mallette pédagogique contenant un exemplaire physique spécifique, le cas échéant.
     *
     * @param exemplaireId l'identifiant de l'exemplaire
     * @return l'objet {@link Mallette} contenant l'exemplaire, ou {@code null} s'il est libre
     * @throws SQLException en cas de problème de requête
     */
    public Mallette getMalletteForExemplaire(int exemplaireId) throws SQLException {
        String sql = "SELECT m.*, (SELECT COUNT(*) FROM mallette_items WHERE mallette_id = m.id) as nb_livres "
                   + "FROM mallette_items mi "
                   + "JOIN mallettes m ON mi.mallette_id = m.id "
                   + "WHERE mi.exemplaire_id = ? LIMIT 1";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, exemplaireId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Retourne la liste dédoublonnée et ordonnée de tous les thèmes existants dans les mallettes et les thèmes standards.
     *
     * @return la liste des libellés de thèmes pour les filtres et listes déroulantes
     * @throws SQLException si la lecture des thèmes en base échoue
     */
    public List<String> getAllThemes() throws SQLException {
        Set<String> themes = new LinkedHashSet<>();
        // Toujours mettre Harcèlement & Vivre ensemble en tête pour faciliter l'accès
        themes.add("Harcèlement & Vivre ensemble");

        String sql = "SELECT DISTINCT theme FROM mallettes WHERE theme IS NOT NULL AND theme != '' ORDER BY theme ASC";
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                themes.add(rs.getString(1).trim());
            }
        }

        // Ajouter les thèmes standards du moteur sémantique
        themes.addAll(LivreClassificationService.THEMES_STANDARDS);
        return new ArrayList<>(themes);
    }

    // =====================================================================
    //  MAPPING
    // =====================================================================

    /**
     * Mappe une ligne de curseur JDBC {@link ResultSet} vers une instance de {@link Mallette}.
     *
     * @param rs le curseur SQL positionné
     * @return un objet {@link Mallette} hydraté
     * @throws SQLException si la lecture échoue
     */
    private Mallette mapRow(ResultSet rs) throws SQLException {
        Mallette m = new Mallette();
        m.setId(rs.getInt("id"));
        m.setCode(rs.getString("code"));
        m.setTitre(rs.getString("titre"));
        m.setTheme(rs.getString("theme"));
        m.setNiveau(rs.getString("niveau"));
        m.setDescription(rs.getString("description"));

        String st = rs.getString("statut");
        m.setStatut(st != null ? Mallette.Statut.valueOf(st) : Mallette.Statut.DISPONIBLE);

        String dc = rs.getString("date_creation");
        if (dc != null && !dc.isBlank()) m.setDateCreation(LocalDate.parse(dc));

        m.setEmprunteurNumero(rs.getString("emprunteur_numero"));
        m.setEmprunteurNom(rs.getString("emprunteur_nom"));
        m.setEmprunteurClasse(rs.getString("emprunteur_classe"));

        String de = rs.getString("date_emprunt");
        if (de != null && !de.isBlank()) m.setDateEmprunt(LocalDate.parse(de));

        String dr = rs.getString("date_retour_prevue");
        if (dr != null && !dr.isBlank()) m.setDateRetourPrevue(LocalDate.parse(dr));

        try {
            m.setNombreLivres(rs.getInt("nb_livres"));
        } catch (SQLException ignored) {}

        return m;
    }
}
