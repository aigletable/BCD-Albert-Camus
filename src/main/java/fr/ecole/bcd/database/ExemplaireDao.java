package fr.ecole.bcd.database;

import fr.ecole.bcd.model.EtiquetteItem;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Exemplaire.Statut;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.RecolementItem;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet d'accès aux données (DAO) pour la table relationnelle {@code exemplaires}.
 *
 * <p>Cette classe gère le cycle de vie de chaque copie physique d'ouvrage au sein de la bibliothèque
 * BCD Express. Chaque exemplaire est identifié par un numéro technique unique (identifiant auto-incrémenté)
 * et rattaché à une fiche bibliographique ({@code isbn}).</p>
 *
 * <h3>Fonctionnalités couvertes :</h3>
 * <ul>
 *   <li>Enregistrement et génération des identifiants physiques uniques.</li>
 *   <li>Création à la volée d'ouvrages non catalogués avec génération d'identifiants internes (ex. "CAMUS-0001").</li>
 *   <li>Suivi des statuts de disponibilité ({@link Statut#DISPONIBLE} vs {@link Statut#EMPRUNTE}).</li>
 *   <li>Extraction des données pour l'audit et l'inventaire annuel de récolement ({@link RecolementItem}).</li>
 *   <li>Extraction et partitionnement par lots pour la génération de planches d'étiquettes à codes-barres ({@link EtiquetteItem}).</li>
 *   <li>Contrôle d'intégrité lors des suppressions pour protéger les emprunts en cours.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class ExemplaireDao {

    /**
     * Constructeur par défaut de {@code ExemplaireDao}.
     */
    public ExemplaireDao() {
    }

    // =====================================================================
    //  CREATE
    // =====================================================================

    /**
     * Insère un nouvel exemplaire physique en base et lui assigne son identifiant unique généré.
     *
     * @param ex l'entité {@link Exemplaire} à persister (son {@code id} sera mis à jour avec la clé générée)
     * @return l'identifiant numérique unique auto-généré par SQLite
     * @throws SQLException si l'insertion échoue ou si la clé générée ne peut être récupérée
     */
    public int insert(Exemplaire ex) throws SQLException {
        String sql = "INSERT INTO exemplaires (isbn, statut, date_ajout, remarque) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, ex.getIsbn());
            ps.setString(2, ex.getStatut().name());
            ps.setString(3, ex.getDateAjout().toString()); // ISO 8601
            ps.setString(4, ex.getRemarque());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    ex.setId(id);
                    return id;
                }
            }
        }
        throw new SQLException("Échec de la récupération de l'id auto-généré.");
    }

    // =====================================================================
    //  READ
    // =====================================================================

    /**
     * Recherche un exemplaire physique à partir de son numéro d'identification unique.
     *
     * @param id le numéro unique de l'exemplaire (clé primaire)
     * @return l'objet {@link Exemplaire} correspondant, ou {@code null} si introuvable
     * @throws SQLException en cas de dysfonctionnement de la requête SQL
     */
    public Exemplaire findById(int id) throws SQLException {
        String sql = "SELECT * FROM exemplaires WHERE id = ?";

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
     * Récupère l'ensemble des exemplaires physiques rattachés à un ouvrage donné par son ISBN.
     *
     * @param isbn l'identifiant ISBN du livre parent
     * @return la liste des exemplaires physiques ordonnée par numéro d'exemplaire croissant
     * @throws SQLException en cas d'erreur SQL
     */
    public List<Exemplaire> findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT * FROM exemplaires WHERE isbn = ? ORDER BY id";
        List<Exemplaire> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retourne tous les exemplaires physiques actuellement disponibles (statut {@link Statut#DISPONIBLE})
     * pour un ouvrage donné.
     *
     * @param isbn le code ISBN ciblé
     * @return la liste des exemplaires empruntables
     * @throws SQLException en cas d'anomalie SQL
     */
    public List<Exemplaire> findDisponiblesByIsbn(String isbn) throws SQLException {
        String sql = "SELECT * FROM exemplaires WHERE isbn = ? AND statut = 'DISPONIBLE' ORDER BY id";
        List<Exemplaire> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Retourne l'intégralité des exemplaires physiques de la bibliothèque actuellement disponibles en rayon.
     *
     * @return la liste ordonnée de tous les exemplaires disponibles
     * @throws SQLException en cas de dysfonctionnement SQL
     */
    public List<Exemplaire> findDisponibles() throws SQLException {
        String sql = "SELECT * FROM exemplaires WHERE statut = 'DISPONIBLE' ORDER BY id ASC";
        List<Exemplaire> list = new ArrayList<>();
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Recherche tous les exemplaires physiques disponibles au prêt associés à un titre d'ouvrage
     * (recherche insensible à la casse et tolérante aux diacritiques).
     *
     * @param titre l'intitulé ou une portion du titre recherché
     * @return la liste ordonnée des exemplaires disponibles
     * @throws SQLException en cas de dysfonctionnement de la requête
     */
    public List<Exemplaire> findDisponiblesByTitre(String titre) throws SQLException {
        if (titre == null || titre.isBlank()) return new ArrayList<>();
        String clean = titre.trim();
        String sql = "SELECT e.* FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE (LOWER(l.titre) = LOWER(?) OR LOWER(l.titre) LIKE LOWER(?)) "
                   + "  AND e.statut = 'DISPONIBLE' "
                   + "ORDER BY CASE WHEN LOWER(l.titre) = LOWER(?) THEN 0 ELSE 1 END, e.id ASC";
        List<Exemplaire> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, "%" + clean + "%");
            ps.setString(3, clean);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }

        // Fallback avec normalisation si rien trouvé en SQL
        if (list.isEmpty()) {
            String normTarget = stripAccents(clean);
            for (Exemplaire ex : findAll()) {
                if (ex.getStatut() == Statut.DISPONIBLE) {
                    LivreDao ldao = new LivreDao();
                    Livre l = ldao.findByIsbn(ex.getIsbn());
                    if (l != null && stripAccents(l.getTitre()).contains(normTarget)) {
                        list.add(ex);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Recherche tous les exemplaires physiques (quel que soit leur statut de prêt)
     * rattachés à un ouvrage identifié par son titre.
     *
     * @param titre l'intitulé exact ou partiel du titre
     * @return la liste de tous les exemplaires physiques correspondants
     * @throws SQLException si une erreur SQL se produit
     */
    public List<Exemplaire> findByTitre(String titre) throws SQLException {
        if (titre == null || titre.isBlank()) return new ArrayList<>();
        String clean = titre.trim();
        String sql = "SELECT e.* FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE (LOWER(l.titre) = LOWER(?) OR LOWER(l.titre) LIKE LOWER(?)) "
                   + "ORDER BY CASE WHEN LOWER(l.titre) = LOWER(?) THEN 0 ELSE 1 END, e.id ASC";
        List<Exemplaire> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, "%" + clean + "%");
            ps.setString(3, clean);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Dénombre le nombre total d'exemplaires physiques répertoriés pour un titre de livre donné.
     *
     * @param titre le titre du livre à sonder
     * @return le nombre total d'exemplaires rattachés
     * @throws SQLException en cas de panne SQL
     */
    public int countByTitre(String titre) throws SQLException {
        if (titre == null || titre.isBlank()) return 0;
        String sql = "SELECT COUNT(e.id) FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE LOWER(l.titre) = LOWER(?) OR LOWER(l.titre) LIKE LOWER(?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, titre.trim());
            ps.setString(2, "%" + titre.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Crée et insère un exemplaire physique directement à partir du nom d'un livre.
     * Si le livre n'est pas encore enregistré dans le catalogue, une fiche bibliographique
     * est automatiquement créée avec un identifiant interne sécurisé (ex. {@code CAMUS-0001}).
     *
     * @param titre le titre du livre (obligatoire et non vide)
     * @param statut le statut initial attribué à l'exemplaire ({@link Statut#DISPONIBLE} par défaut)
     * @param remarque commentaire d'état ou localisation de rangement
     * @return l'identifiant numérique unique de l'exemplaire nouvellement créé
     * @throws IllegalArgumentException si le titre est null ou vide
     * @throws SQLException si une opération en base échoue
     */
    public int insertExemplairePourTitre(String titre, Statut statut, String remarque) throws SQLException {
        if (titre == null || titre.isBlank()) {
            throw new IllegalArgumentException("Le titre du livre est obligatoire.");
        }
        LivreDao ldao = new LivreDao();
        Livre l = ldao.findByTitreExact(titre);
        if (l == null) {
            List<Livre> matches = ldao.findByTitre(titre);
            if (!matches.isEmpty()) {
                l = matches.get(0);
            }
        }

        String isbn;
        if (l != null) {
            isbn = l.getIsbn();
        } else {
            // Création automatique du livre avec code interne BCD
            int count = ldao.count();
            String candidate = "CAMUS-" + String.format("%04d", count + 1);
            int offset = 1;
            while (ldao.findByIsbn(candidate) != null) {
                candidate = "CAMUS-" + String.format("%04d", count + 1 + offset++);
            }
            isbn = candidate;
            Livre newLivre = new Livre(isbn, titre.trim(), "", "", "", "Général", "", "", "");
            ldao.insert(newLivre);
        }

        Exemplaire ex = new Exemplaire(isbn, statut != null ? statut : Statut.DISPONIBLE, LocalDate.now(), remarque);
        return insert(ex);
    }

    /**
     * Supprime les accents et caractères diacritiques d'une chaîne pour la recherche.
     *
     * @param s la chaîne à traiter
     * @return la chaîne normalisée en minuscules sans accents
     */
    private static String stripAccents(String s) {
        if (s == null) return "";
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("œ", "oe")
                .replace("Œ", "oe")
                .replace("æ", "ae")
                .replace("Æ", "ae")
                .replace("’", "'")
                .toLowerCase()
                .trim();
    }

    /**
     * Récupère l'intégralité des exemplaires physiques référencés dans la base, tous livres confondus.
     * Les résultats sont triés par ISBN puis par identifiant d'exemplaire croissant.
     *
     * @return la liste exhaustive des objets {@link Exemplaire}
     * @throws SQLException en cas de panne SQL
     */
    public List<Exemplaire> findAll() throws SQLException {
        String sql = "SELECT * FROM exemplaires ORDER BY isbn, id";
        List<Exemplaire> list = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Compte le nombre d'exemplaires physiques attachés à une fiche d'ouvrage (par ISBN).
     *
     * @param isbn l'identifiant ISBN de l'ouvrage
     * @return le nombre total de copies physiques répertoriées
     * @throws SQLException si la requête échoue
     */
    public int countByIsbn(String isbn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM exemplaires WHERE isbn = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1);
            }
        }
    }

    /**
     * Dénombre le nombre total de copies physiques existantes au sein de la bibliothèque.
     *
     * @return le décompte global de tous les exemplaires enregistrés
     * @throws SQLException en cas d'erreur SQL
     */
    public int countTotal() throws SQLException {
        String sql = "SELECT COUNT(*) FROM exemplaires";

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.getInt(1);
        }
    }

    // =====================================================================
    //  UPDATE
    // =====================================================================

    /**
     * Modifie l'état de disponibilité d'un exemplaire (bascule entre {@link Statut#DISPONIBLE} et {@link Statut#EMPRUNTE}).
     *
     * @param id     l'identifiant unique de l'exemplaire concerné
     * @param statut le nouveau statut à appliquer
     * @throws SQLException si la mise à jour échoue en base
     */
    public void updateStatut(int id, Statut statut) throws SQLException {
        String sql = "UPDATE exemplaires SET statut = ? WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, statut.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Met à jour la remarque d'état ou l'indication d'emplacement d'un exemplaire.
     *
     * @param id       l'identifiant unique de l'exemplaire
     * @param remarque le commentaire textuel (ex. "Neuf", "Couverture abîmée", "Rayon Maternelle")
     * @throws SQLException si l'écriture échoue
     */
    public void updateRemarque(int id, String remarque) throws SQLException {
        String sql = "UPDATE exemplaires SET remarque = ? WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, remarque != null ? remarque : "");
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Extrait tous les exemplaires accompagnés de leurs métadonnées bibliographiques complètes
     * et, si l'exemplaire est prêté, de l'identité de l'emprunteur et des dates limites de retour.
     * Utilisé pour la vue d'audit et le module de récolement annuel.
     *
     * @return la liste ordonnée des objets de vue {@link RecolementItem}
     * @throws SQLException en cas de problème lors de l'exécution de la jointure multiple
     */
    public List<RecolementItem> findAllForRecolement() throws SQLException {
        String sql = "SELECT e.id AS ex_id, e.isbn, e.statut AS ex_statut, e.remarque, "
                   + "       l.titre, l.auteur, l.editeur, "
                   + "       u.nom AS emprunteur_nom, u.prenom AS emprunteur_prenom, u.classe AS emprunteur_classe, "
                   + "       em.date_emprunt, em.date_limite "
                   + "FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "LEFT JOIN emprunts em ON e.id = em.exemplaire_id AND em.date_rendu IS NULL "
                   + "LEFT JOIN utilisateurs u ON em.utilisateur_numero = u.numero "
                   + "ORDER BY l.titre COLLATE NOCASE, e.id";

        List<RecolementItem> list = new ArrayList<>();
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("ex_id");
                String isbn = rs.getString("isbn");
                Statut statut = Statut.valueOf(rs.getString("ex_statut"));
                String remarque = rs.getString("remarque");
                if (remarque == null) remarque = "";
                String titre = rs.getString("titre");
                String auteur = rs.getString("auteur");
                String editeur = rs.getString("editeur");

                String nom = rs.getString("emprunteur_nom");
                String prenom = rs.getString("emprunteur_prenom");
                String classe = rs.getString("emprunteur_classe");
                String empNomComplet = "";
                if (nom != null && !nom.isBlank()) {
                    empNomComplet = nom + (prenom != null && !prenom.isBlank() ? " " + prenom : "");
                }

                String dateEmpStr = rs.getString("date_emprunt");
                LocalDate dateEmp = dateEmpStr != null ? LocalDate.parse(dateEmpStr) : null;
                String dateLimStr = rs.getString("date_limite");
                LocalDate dateLim = dateLimStr != null ? LocalDate.parse(dateLimStr) : null;

                list.add(new RecolementItem(id, isbn, titre, auteur, editeur, remarque, statut,
                        empNomComplet, classe, dateEmp, dateLim));
            }
        }
        return list;
    }

    // =====================================================================
    //  DELETE
    // =====================================================================

    /**
     * Supprime définitivement un exemplaire physique à partir de son numéro unique.
     * <p>
     * <b>Règles d'intégrité :</b>
     * <ul>
     *   <li>Si l'exemplaire est en cours de prêt (non encore restitué), la suppression est rejetée avec une exception.</li>
     *   <li>L'historique des anciens prêts archivés est préalablement purgé pour respecter la contrainte FK {@code RESTRICT}.</li>
     * </ul>
     * </p>
     *
     * @param id l'identifiant technique de l'exemplaire à effacer
     * @return {@code true} si l'exemplaire a été supprimé, {@code false} sinon
     * @throws SQLException si un prêt est en cours ou si une erreur SQL survient
     */
    public boolean delete(int id) throws SQLException {
        // 1. Interdire la suppression si l'exemplaire est actuellement prêté (non rendu)
        String sqlCheck = "SELECT 1 FROM emprunts WHERE exemplaire_id = ? AND date_rendu IS NULL";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sqlCheck)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new SQLException("Impossible de supprimer l'exemplaire #" + id + " : un prêt est actuellement en cours.");
                }
            }
        }

        // 2. Nettoyer l'historique des anciens prêts déjà rendus pour respecter la contrainte FK RESTRICT
        String sqlEmprunts = "DELETE FROM emprunts WHERE exemplaire_id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sqlEmprunts)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }

        // 3. Supprimer définitivement l'exemplaire de la base
        String sql = "DELETE FROM exemplaires WHERE id = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Supprime l'ensemble des exemplaires physiques disponibles attachés à un livre désigné par son titre.
     * Les exemplaires actuellement prêtés sont systématiquement préservés pour protéger l'intégrité des prêts en cours.
     *
     * @param titre l'intitulé du livre
     * @return le nombre total d'exemplaires effectivement supprimés
     * @throws SQLException en cas de problème SQL
     */
    public int deleteByTitre(String titre) throws SQLException {
        List<Exemplaire> exList = findByTitre(titre);
        int count = 0;
        for (Exemplaire ex : exList) {
            if (ex.getStatut() == Statut.DISPONIBLE) {
                if (delete(ex.getId())) {
                    count++;
                }
            }
        }
        return count;
    }

    // =====================================================================
    //  Mapping
    // =====================================================================

    /**
     * Transforme une ligne du curseur JDBC {@link ResultSet} en objet métier {@link Exemplaire}.
     *
     * @param rs le curseur SQL positionné
     * @return une instance de {@link Exemplaire} hydratée
     * @throws SQLException si la lecture d'une colonne échoue
     */
    private Exemplaire mapRow(ResultSet rs) throws SQLException {
        Exemplaire ex = new Exemplaire();
        ex.setId(rs.getInt("id"));
        ex.setIsbn(rs.getString("isbn"));
        ex.setStatut(Statut.valueOf(rs.getString("statut")));
        ex.setDateAjout(LocalDate.parse(rs.getString("date_ajout")));
        try {
            String r = rs.getString("remarque");
            ex.setRemarque(r != null ? r : "");
        } catch (SQLException ignored) {
            ex.setRemarque("");
        }
        return ex;
    }

    // =====================================================================
    //  Étiquettes & Codes-barres
    // =====================================================================

    /**
     * Récupère la liste des informations d'étiquettes à imprimer pour tous les exemplaires
     * attachés à un livre identifié par son code ISBN.
     *
     * @param isbn le numéro ISBN du livre
     * @return la liste ordonnée des objets {@link EtiquetteItem}
     * @throws SQLException en cas de problème de requête
     */
    public List<EtiquetteItem> findEtiquettesByIsbn(String isbn) throws SQLException {
        String sql = "SELECT e.id, e.isbn, l.titre, l.auteur, l.editeur, e.remarque "
                   + "FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE e.isbn = ? "
                   + "ORDER BY e.id ASC";

        List<EtiquetteItem> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEtiquetteItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Récupère les données d'étiquettes pour une liste d'ISBNs multiples avec partitionnement par lots
     * pour contourner la limite maximale de variables dans une clause SQL {@code IN (...)}.
     *
     * @param isbns la collection de codes ISBN à inclure
     * @return la liste des éléments {@link EtiquetteItem} ordonnés par titre
     * @throws SQLException en cas d'erreur de communication SQL
     */
    public List<EtiquetteItem> findEtiquettesByIsbns(List<String> isbns) throws SQLException {
        if (isbns == null || isbns.isEmpty()) return List.of();
        List<String> cleanIsbns = isbns.stream()
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        if (cleanIsbns.isEmpty()) return List.of();

        List<EtiquetteItem> list = new ArrayList<>();
        int batchSize = 400; // Limite de sécurité pour SQLite
        for (int i = 0; i < cleanIsbns.size(); i += batchSize) {
            List<String> batch = cleanIsbns.subList(i, Math.min(i + batchSize, cleanIsbns.size()));
            String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
            String sql = "SELECT e.id, e.isbn, l.titre, l.auteur, l.editeur, e.remarque "
                       + "FROM exemplaires e "
                       + "JOIN livres l ON e.isbn = l.isbn "
                       + "WHERE e.isbn IN (" + placeholders + ") "
                       + "ORDER BY l.titre COLLATE NOCASE, e.id ASC";

            try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
                for (int j = 0; j < batch.size(); j++) {
                    ps.setString(j + 1, batch.get(j));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapEtiquetteItem(rs));
                    }
                }
            }
        }
        return list;
    }

    /**
     * Récupère l'ensemble des étiquettes de tous les exemplaires physiques du catalogue.
     *
     * @return la liste globale des {@link EtiquetteItem} ordonnée par numéro d'exemplaire
     * @throws SQLException si la requête échoue
     */
    public List<EtiquetteItem> findAllEtiquettes() throws SQLException {
        String sql = "SELECT e.id, e.isbn, l.titre, l.auteur, l.editeur, e.remarque "
                   + "FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "ORDER BY e.id ASC";

        List<EtiquetteItem> list = new ArrayList<>();
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapEtiquetteItem(rs));
            }
        }
        return list;
    }

    /**
     * Récupère les données d'étiquettes pour une plage donnée de numéros d'exemplaires (bornes incluses).
     *
     * @param startId l'identifiant minimum de la plage
     * @param endId l'identifiant maximum de la plage
     * @return la liste des {@link EtiquetteItem} compris dans l'intervalle
     * @throws SQLException en cas de dysfonctionnement de la base
     */
    public List<EtiquetteItem> findEtiquettesByRange(int startId, int endId) throws SQLException {
        String sql = "SELECT e.id, e.isbn, l.titre, l.auteur, l.editeur, e.remarque "
                   + "FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE e.id BETWEEN ? AND ? "
                   + "ORDER BY e.id ASC";

        List<EtiquetteItem> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, startId);
            ps.setInt(2, endId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEtiquetteItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Récupère les données d'étiquettes des {@code limit} derniers exemplaires physiques ajoutés dans l'inventaire.
     *
     * @param limit le nombre maximal d'exemplaires récents à extraire
     * @return la liste des derniers {@link EtiquetteItem} ajoutés
     * @throws SQLException si la requête échoue
     */
    public List<EtiquetteItem> findEtiquettesLatest(int limit) throws SQLException {
        String sql = "SELECT e.id, e.isbn, l.titre, l.auteur, l.editeur, e.remarque "
                   + "FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "ORDER BY e.id DESC LIMIT ?";

        List<EtiquetteItem> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapEtiquetteItem(rs));
                }
            }
        }
        return list;
    }

    /**
     * Convertit une ligne de résultat JDBC {@link ResultSet} en objet de modèle {@link EtiquetteItem}.
     *
     * @param rs le curseur SQL positionné
     * @return une instance hydratée de {@link EtiquetteItem}
     * @throws SQLException en cas de problème d'accès aux colonnes
     */
    private EtiquetteItem mapEtiquetteItem(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String isbn = rs.getString("isbn");
        String titre = rs.getString("titre");
        String auteur = rs.getString("auteur");
        String editeur = rs.getString("editeur");
        String remarque = "";
        try {
            remarque = rs.getString("remarque");
            if (remarque == null) remarque = "";
        } catch (SQLException ignored) {}
        return new EtiquetteItem(id, isbn, titre, auteur, editeur, remarque);
    }
}
