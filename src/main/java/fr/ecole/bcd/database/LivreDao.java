package fr.ecole.bcd.database;

import fr.ecole.bcd.model.Livre;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet d'accès aux données (DAO) pour la table relationnelle {@code livres}.
 *
 * <p>Cette classe gère la persistance, la recherche textuelle plein texte pondérée,
 * les réassignations d'ISBN, ainsi que les opérations d'agrégation liées aux fiches
 * bibliographiques du catalogue BCD Express. Elle applique des politiques de gestion
 * avancées :</p>
 * <ul>
 *   <li>Insertion idempotente via {@code INSERT OR IGNORE} pour prévenir les doublons lors des scans successifs.</li>
 *   <li>Recherche floue tolérante aux accents, aux ligatures (œ, æ) et à la casse.</li>
 *   <li>Moteur de recherche multicritères pondéré (mots-clés avec scoring de pertinence).</li>
 *   <li>Réassignation d'ISBN avec gestion transactionnelle ACID (transfert d'exemplaires et nettoyage automatique des fiches orphelines).</li>
 *   <li>Calcul des compteurs d'exemplaires totaux et disponibles en rayon via jointures optimisées.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class LivreDao {

    /**
     * Constructeur par défaut de {@code LivreDao}.
     */
    public LivreDao() {
    }

    // =====================================================================
    //  CREATE
    // =====================================================================

    /**
     * Insère une nouvelle fiche bibliographique dans la base de données.
     * Si un livre possédant le même numéro ISBN existe déjà, l'instruction SQL
     * {@code INSERT OR IGNORE} ignore silencieusement l'opération sans lever d'erreur.
     *
     * @param livre l'entité {@link Livre} contenant les métadonnées de l'ouvrage
     * @return {@code true} si une nouvelle ligne a été effectivement créée en base,
     *         {@code false} si le livre existait déjà
     * @throws SQLException en cas de panne de communication avec SQLite
     */
    public boolean insert(Livre livre) throws SQLException {
        String rawGenre = livre.getGenre();
        String rawTheme = livre.getTheme();
        if ((rawTheme == null || rawTheme.isBlank()) && rawGenre != null
                && fr.ecole.bcd.service.LivreClassificationService.THEMES_STANDARDS.contains(rawGenre.trim())) {
            rawTheme = rawGenre.trim();
            rawGenre = "Documentaire";
        }
        String genre = fr.ecole.bcd.service.LivreClassificationService.normalizeGenre(rawGenre, livre.getNiveau());
        String theme = fr.ecole.bcd.service.LivreClassificationService.normalizeTheme(rawTheme);
        String niveau = fr.ecole.bcd.service.LivreClassificationService.normalizeNiveau(livre.getNiveau());

        String sql = "INSERT OR IGNORE INTO livres (isbn, titre, auteur, editeur, couverture_url, genre, theme, niveau, description) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, livre.getIsbn());
            ps.setString(2, livre.getTitre());
            ps.setString(3, livre.getAuteur());
            ps.setString(4, livre.getEditeur());
            ps.setString(5, livre.getCouvertureUrl());
            ps.setString(6, genre);
            ps.setString(7, theme);
            ps.setString(8, niveau);
            ps.setString(9, livre.getDescription());
            return ps.executeUpdate() > 0;
        }
    }

    // =====================================================================
    //  READ
    // =====================================================================

    /**
     * Recherche un livre par son ISBN.
     *
     * @param isbn l'ISBN-10 ou ISBN-13
     * @return le livre trouvé, ou {@code null} s'il n'existe pas
     */
    public Livre findByIsbn(String isbn) throws SQLException {
        String sql = "SELECT * FROM livres WHERE isbn = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /**
     * Recherche un livre par son titre exact (insensible à la casse/accents).
     *
     * @param titre titre du livre
     * @return le livre trouvé, ou null
     */
    public Livre findByTitreExact(String titre) throws SQLException {
        if (titre == null || titre.isBlank()) return null;
        String clean = titre.trim();
        String sql = "SELECT * FROM livres WHERE LOWER(titre) = LOWER(?) LIMIT 1";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, clean);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }

        // Fallback avec normalisation des accents
        String normTarget = stripAccents(clean);
        for (Livre l : findAll()) {
            if (stripAccents(l.getTitre()).equalsIgnoreCase(normTarget)) {
                return l;
            }
        }
        return null;
    }

    /**
     * Recherche les livres correspondant au terme dans leur titre (recherche par nom du livre).
     *
     * @param terme extrait de titre recherché
     * @return liste des livres correspondants
     */
    public List<Livre> findByTitre(String terme) throws SQLException {
        if (terme == null || terme.isBlank()) return new ArrayList<>();
        String clean = terme.trim();
        String sql = "SELECT * FROM livres WHERE LOWER(titre) LIKE LOWER(?) "
                   + "ORDER BY CASE WHEN LOWER(titre) = LOWER(?) THEN 0 "
                   + "              WHEN LOWER(titre) LIKE LOWER(?) THEN 1 "
                   + "              ELSE 2 END, titre ASC";
        List<Livre> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, "%" + clean + "%");
            ps.setString(2, clean);
            ps.setString(3, clean + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }

        // Fallback avec normalisation si rien trouvé
        if (list.isEmpty()) {
            String normTerm = stripAccents(clean);
            for (Livre l : findAll()) {
                if (stripAccents(l.getTitre()).contains(normTerm)) {
                    list.add(l);
                }
            }
        }
        return list;
    }

    /**
     * Supprime les accents, neutralise les ligatures typographiques (œ, æ, etc.)
     * et convertit une chaîne en minuscules pour faciliter les comparaisons textuelles insensibles.
     *
     * @param s la chaîne d'origine à normaliser
     * @return la chaîne normalisée en minuscules sans diacritiques, ou une chaîne vide si {@code s} est null
     */
    public static String stripAccents(String s) {
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
     * Recherche avancée multi-mots clés plein texte.
     * <p>
     * Recherche tous les mots-clés spécifiés à travers le titre, l'auteur, l'éditeur,
     * le genre, le thème et le résumé/synopsis (description).
     * Les accents et la casse sont neutralisés, et les résultats sont ordonnés par pertinence.
     * </p>
     *
     * @param motsCles un ou plusieurs mots recherchés (séparés par des espaces)
     * @return liste des livres correspondants, triés par pertinence
     */
    public List<Livre> findByMotsCles(String motsCles) throws SQLException {
        if (motsCles == null || motsCles.isBlank()) return new ArrayList<>();

        String[] rawTokens = stripAccents(motsCles).split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String t : rawTokens) {
            String clean = t.trim();
            if (!clean.isEmpty()) {
                tokens.add(clean);
            }
        }
        if (tokens.isEmpty()) return new ArrayList<>();

        List<Livre> all = findAll();
        class ScoredLivre {
            final Livre livre;
            final int score;
            ScoredLivre(Livre livre, int score) {
                this.livre = livre;
                this.score = score;
            }
        }

        List<ScoredLivre> scored = new ArrayList<>();

        for (Livre l : all) {
            String titreNorm = stripAccents(l.getTitre());
            String auteurNorm = stripAccents(l.getAuteur());
            String editeurNorm = stripAccents(l.getEditeur());
            String genreNorm = stripAccents(l.getGenre());
            String themeNorm = stripAccents(l.getTheme());
            String niveauNorm = stripAccents(l.getNiveau());
            String descNorm = stripAccents(l.getDescription());
            String isbnNorm = stripAccents(l.getIsbn());

            String fullText = titreNorm + " " + auteurNorm + " " + editeurNorm + " "
                    + genreNorm + " " + themeNorm + " " + niveauNorm + " " + isbnNorm + " " + descNorm;

            boolean allTokensMatch = true;
            int score = 0;

            for (String token : tokens) {
                if (!fullText.contains(token)) {
                    allTokensMatch = false;
                    break;
                }
                // Scoring par importance du champ
                if (titreNorm.contains(token)) {
                    score += 20;
                    if (titreNorm.equals(token)) score += 30;
                }
                if (themeNorm.contains(token) || genreNorm.contains(token)) {
                    score += 10;
                }
                if (auteurNorm.contains(token)) {
                    score += 10;
                }
                if (descNorm.contains(token)) {
                    score += 5;
                }
                if (editeurNorm.contains(token) || isbnNorm.contains(token)) {
                    score += 3;
                }
            }

            if (allTokensMatch) {
                scored.add(new ScoredLivre(l, score));
            }
        }

        scored.sort((a, b) -> {
            int cmp = Integer.compare(b.score, a.score);
            if (cmp != 0) return cmp;
            return a.livre.getTitre().compareToIgnoreCase(b.livre.getTitre());
        });

        List<Livre> result = new ArrayList<>(scored.size());
        for (ScoredLivre sl : scored) {
            result.add(sl.livre);
        }
        return result;
    }

    /**
     * Récupère l'intégralité des fiches de livres cataloguées, triées par ordre alphabétique de titre.
     *
     * @return la liste exhaustive des fiches {@link Livre}
     * @throws SQLException si une anomalie SQL se produit
     */
    public List<Livre> findAll() throws SQLException {
        String sql = "SELECT * FROM livres ORDER BY titre";
        List<Livre> list = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Recherche textuelle simple filtrant les livres dont le titre, l'auteur ou l'ISBN
     * contient la chaîne spécifiée.
     *
     * @param terme le fragment textuel recherché
     * @return la liste des livres correspondants triés par titre
     * @throws SQLException en cas d'erreur de communication avec la base
     */
    public List<Livre> search(String terme) throws SQLException {
        String sql = "SELECT * FROM livres "
                   + "WHERE titre LIKE ? OR auteur LIKE ? OR isbn LIKE ? "
                   + "ORDER BY titre";
        List<Livre> list = new ArrayList<>();
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
     * Récupère l'ensemble des fiches bibliographiques accompagnées de leurs statistiques d'exemplaires :
     * nombre total d'exemplaires rattachés et nombre d'exemplaires immédiatement disponibles pour le prêt.
     *
     * @return la liste des livres enrichis avec {@link Livre#getNbExemplaires()} et {@link Livre#getNbDisponibles()}
     * @throws SQLException en cas de dysfonctionnement lors de l'exécution de la jointure
     */
    public List<Livre> findAllWithCounts() throws SQLException {
        String sql = "SELECT l.isbn, l.titre, l.auteur, l.editeur, l.couverture_url, "
                   + "       l.genre, l.theme, l.niveau, l.description, "
                   + "       COUNT(e.id) AS total_ex, "
                   + "       SUM(CASE WHEN e.statut = 'DISPONIBLE' THEN 1 ELSE 0 END) AS dispo_ex "
                   + "FROM livres l "
                   + "LEFT JOIN exemplaires e ON l.isbn = e.isbn "
                   + "GROUP BY l.isbn "
                   + "ORDER BY l.titre COLLATE NOCASE";
        List<Livre> list = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Livre l = mapRow(rs);
                l.setNbExemplaires(rs.getInt("total_ex"));
                l.setNbDisponibles(rs.getInt("dispo_ex"));
                list.add(l);
            }
        }
        return list;
    }

    /**
     * Recherche textuelle parmi les livres avec calcul dynamique en temps réel des compteurs
     * d'exemplaires physiques (totaux et disponibles).
     *
     * @param terme le fragment recherché (titre, auteur ou code ISBN)
     * @return la liste des livres correspondants avec leurs compteurs à jour
     * @throws SQLException si la requête SQL échoue
     */
    public List<Livre> searchWithCounts(String terme) throws SQLException {
        String sql = "SELECT l.isbn, l.titre, l.auteur, l.editeur, l.couverture_url, "
                   + "       l.genre, l.theme, l.niveau, l.description, "
                   + "       COUNT(e.id) AS total_ex, "
                   + "       SUM(CASE WHEN e.statut = 'DISPONIBLE' THEN 1 ELSE 0 END) AS dispo_ex "
                   + "FROM livres l "
                   + "LEFT JOIN exemplaires e ON l.isbn = e.isbn "
                   + "WHERE l.titre LIKE ? OR l.auteur LIKE ? OR l.isbn LIKE ? "
                   + "GROUP BY l.isbn "
                   + "ORDER BY l.titre COLLATE NOCASE";
        List<Livre> list = new ArrayList<>();
        String pattern = "%" + terme + "%";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Livre l = mapRow(rs);
                    l.setNbExemplaires(rs.getInt("total_ex"));
                    l.setNbDisponibles(rs.getInt("dispo_ex"));
                    list.add(l);
                }
            }
        }
        return list;
    }

    /**
     * Dénombre le nombre total de fiches bibliographiques distinctes enregistrées dans la base.
     *
     * @return le nombre total de titres catalogués
     * @throws SQLException en cas d'erreur SQL
     */
    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM livres";

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.getInt(1);
        }
    }

    /**
     * Génère un code interne séquentiel unique sans collision pour les ouvrages
     * anciens ou sans ISBN/code-barres (ex: CAMUS-0042).
     *
     * @return un identifiant unique garanti non existant dans la base
     * @throws SQLException en cas d'erreur SQL
     */
    public String generateUniqueBcdCode() throws SQLException {
        int total = count();
        String candidate = "CAMUS-" + String.format("%04d", total + 1);
        int offset = 1;
        while (findByIsbn(candidate) != null) {
            candidate = "CAMUS-" + String.format("%04d", total + 1 + offset++);
        }
        return candidate;
    }

    // =====================================================================
    //  UPDATE
    // =====================================================================

    /**
     * Met à jour l'intégralité des attributs descriptifs d'un livre existant identifié par son ISBN.
     *
     * @param livre l'objet {@link Livre} renfermant les nouvelles informations (titre, auteur, éditeur, etc.)
     * @throws SQLException si la mise à jour échoue au niveau de la base de données
     */
    public void update(Livre livre) throws SQLException {
        String rawGenre = livre.getGenre();
        String rawTheme = livre.getTheme();
        if ((rawTheme == null || rawTheme.isBlank()) && rawGenre != null
                && fr.ecole.bcd.service.LivreClassificationService.THEMES_STANDARDS.contains(rawGenre.trim())) {
            rawTheme = rawGenre.trim();
            rawGenre = "Documentaire";
        }
        String genre = fr.ecole.bcd.service.LivreClassificationService.normalizeGenre(rawGenre, livre.getNiveau());
        String theme = fr.ecole.bcd.service.LivreClassificationService.normalizeTheme(rawTheme);
        String niveau = fr.ecole.bcd.service.LivreClassificationService.normalizeNiveau(livre.getNiveau());

        String sql = "UPDATE livres SET titre = ?, auteur = ?, editeur = ?, couverture_url = ?, "
                   + "genre = ?, theme = ?, niveau = ?, description = ? "
                   + "WHERE isbn = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, livre.getTitre());
            ps.setString(2, livre.getAuteur());
            ps.setString(3, livre.getEditeur());
            ps.setString(4, livre.getCouvertureUrl());
            ps.setString(5, genre);
            ps.setString(6, theme);
            ps.setString(7, niveau);
            ps.setString(8, livre.getDescription());
            ps.setString(9, livre.getIsbn());
            ps.executeUpdate();
        }
    }

    /**
     * Modifie l'identifiant ISBN d'un ouvrage et réassigne automatiquement l'ensemble de ses exemplaires
     * physiques au nouvel ISBN au sein d'une transaction SQLite ACID.
     * <p>
     * Si une fiche existait déjà pour le nouvel ISBN, ses métadonnées sont actualisées et les exemplaires
     * y sont rattachés. L'ancienne fiche devenue orpheline est ensuite purgée.
     * </p>
     *
     * @param oldIsbn      l'ancien code ISBN avant modification
     * @param updatedLivre l'objet {@link Livre} contenant le nouvel ISBN ainsi que les données corrigées
     * @throws SQLException si une erreur survient au cours de la transaction (un rollback automatique est effectué)
     */
    public void updateBookIsbn(String oldIsbn, Livre updatedLivre) throws SQLException {
        if (oldIsbn == null || updatedLivre == null || updatedLivre.getIsbn() == null) return;
        String newIsbn = updatedLivre.getIsbn().trim();
        if (newIsbn.isEmpty()) return;

        Connection conn = DatabaseManager.getConnection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            if (oldIsbn.equals(newIsbn)) {
                update(updatedLivre);
                conn.commit();
                return;
            }

            String normGenre = fr.ecole.bcd.service.LivreClassificationService.normalizeGenre(updatedLivre.getGenre(), updatedLivre.getNiveau());
            String normTheme = fr.ecole.bcd.service.LivreClassificationService.normalizeTheme(updatedLivre.getTheme());
            String normNiveau = fr.ecole.bcd.service.LivreClassificationService.normalizeNiveau(updatedLivre.getNiveau());

            Livre existingTarget = findByIsbn(newIsbn);
            if (existingTarget == null) {
                String insertSql = "INSERT INTO livres (isbn, titre, auteur, editeur, couverture_url, genre, theme, niveau, description) "
                                 + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, newIsbn);
                    ps.setString(2, updatedLivre.getTitre());
                    ps.setString(3, updatedLivre.getAuteur());
                    ps.setString(4, updatedLivre.getEditeur());
                    ps.setString(5, updatedLivre.getCouvertureUrl());
                    ps.setString(6, normGenre);
                    ps.setString(7, normTheme);
                    ps.setString(8, normNiveau);
                    ps.setString(9, updatedLivre.getDescription());
                    ps.executeUpdate();
                }
            } else {
                String updateSql = "UPDATE livres SET titre = ?, auteur = ?, editeur = ?, couverture_url = ?, "
                                 + "genre = ?, theme = ?, niveau = ?, description = ? WHERE isbn = ?";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, updatedLivre.getTitre());
                    ps.setString(2, updatedLivre.getAuteur());
                    ps.setString(3, updatedLivre.getEditeur());
                    ps.setString(4, updatedLivre.getCouvertureUrl());
                    ps.setString(5, normGenre);
                    ps.setString(6, normTheme);
                    ps.setString(7, normNiveau);
                    ps.setString(8, updatedLivre.getDescription());
                    ps.setString(9, newIsbn);
                    ps.executeUpdate();
                }
            }

            // Réassigner tous les exemplaires de oldIsbn vers newIsbn
            String updateExSql = "UPDATE exemplaires SET isbn = ? WHERE isbn = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateExSql)) {
                ps.setString(1, newIsbn);
                ps.setString(2, oldIsbn);
                ps.executeUpdate();
            }

            // Supprimer l'ancienne fiche devenue vide
            String deleteOldSql = "DELETE FROM livres WHERE isbn = ?";
            try (PreparedStatement ps = conn.prepareStatement(deleteOldSql)) {
                ps.setString(1, oldIsbn);
                ps.executeUpdate();
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    /**
     * Transfère un exemplaire physique spécifique vers un autre ISBN (correction d'un mauvais scan).
     * Si l'ancienne fiche livre ne contient plus aucun exemplaire après le transfert, elle est supprimée.
     *
     * @param exemplaireId id de l'exemplaire physique à transférer
     * @param oldIsbn      ancien ISBN
     * @param newLivre     fiche livre cible contenant le nouvel ISBN
     */
    public void reassignExemplaireIsbn(int exemplaireId, String oldIsbn, Livre newLivre) throws SQLException {
        if (oldIsbn == null || newLivre == null || newLivre.getIsbn() == null) return;
        String newIsbn = newLivre.getIsbn().trim();
        if (newIsbn.isEmpty()) return;

        Connection conn = DatabaseManager.getConnection();
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            Livre existingTarget = findByIsbn(newIsbn);
            if (existingTarget == null) {
                String insertSql = "INSERT INTO livres (isbn, titre, auteur, editeur, couverture_url, genre, theme, niveau, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, newIsbn);
                    ps.setString(2, newLivre.getTitre());
                    ps.setString(3, newLivre.getAuteur());
                    ps.setString(4, newLivre.getEditeur());
                    ps.setString(5, newLivre.getCouvertureUrl());
                    ps.setString(6, newLivre.getGenre() != null ? newLivre.getGenre() : "");
                    ps.setString(7, newLivre.getTheme() != null ? newLivre.getTheme() : "");
                    ps.setString(8, newLivre.getNiveau() != null ? newLivre.getNiveau() : "");
                    ps.setString(9, newLivre.getDescription() != null ? newLivre.getDescription() : "");
                    ps.executeUpdate();
                }
            }

            // Transférer cet exemplaire vers le nouvel ISBN
            String updateExSql = "UPDATE exemplaires SET isbn = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateExSql)) {
                ps.setString(1, newIsbn);
                ps.setInt(2, exemplaireId);
                ps.executeUpdate();
            }

            // Vérifier s'il reste des exemplaires sous l'ancien ISBN
            String countSql = "SELECT COUNT(*) FROM exemplaires WHERE isbn = ?";
            int remaining = 0;
            try (PreparedStatement ps = conn.prepareStatement(countSql)) {
                ps.setString(1, oldIsbn);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) remaining = rs.getInt(1);
                }
            }

            // Si plus aucun exemplaire, nettoyer l'ancien livre
            if (remaining == 0) {
                String deleteSql = "DELETE FROM livres WHERE isbn = ?";
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setString(1, oldIsbn);
                    ps.executeUpdate();
                }
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    // =====================================================================
    //  DELETE
    // =====================================================================

    /**
     * Supprime définitivement un livre à partir de son numéro ISBN.
     * <p>
     * <b>Attention (Cascade) :</b> La contrainte de clé étrangère sur la table {@code exemplaires}
     * étant configurée en {@code ON DELETE CASCADE}, la suppression de la fiche livre entraîne
     * automatiquement et irrémédiablement la suppression de tous les exemplaires physiques associés.
     * </p>
     *
     * @param isbn l'identifiant ISBN de l'ouvrage à supprimer
     * @return {@code true} si la ligne correspondante a été supprimée, {@code false} sinon
     * @throws SQLException si la suppression échoue au niveau SQL
     */
    public boolean delete(String isbn) throws SQLException {
        String sql = "DELETE FROM livres WHERE isbn = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Supprime par lot une collection de livres et l'ensemble de leurs exemplaires physiques
     * en une seule transaction SQLite atomique ultra-rapide.
     *
     * @param isbns liste des ISBNs ou codes internes des livres à supprimer
     * @return le nombre total de livres supprimés
     * @throws SQLException si une erreur SQL survient
     */
    public int deleteBatch(List<String> isbns) throws SQLException {
        if (isbns == null || isbns.isEmpty()) return 0;
        String sql = "DELETE FROM livres WHERE isbn = ?";
        Connection conn = DatabaseManager.getConnection();
        boolean oldAuto = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            int count = 0;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (String isbn : isbns) {
                    if (isbn == null || isbn.isBlank()) continue;
                    ps.setString(1, isbn.trim());
                    ps.addBatch();
                }
                int[] res = ps.executeBatch();
                for (int r : res) {
                    if (r > 0 || r == Statement.SUCCESS_NO_INFO) count++;
                }
            }
            conn.commit();
            return count;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(oldAuto);
        }
    }

    /**
     * Supprime un livre et l'ensemble de ses exemplaires physiques en effectuant une recherche par son titre.
     * Recherche d'abord une correspondance exacte, puis floue.
     *
     * @param titre l'intitulé ou l'extrait du titre du livre à supprimer
     * @return {@code true} si au moins un livre a été trouvé et supprimé, {@code false} sinon
     * @throws SQLException en cas d'erreur SQL
     */
    public boolean deleteByTitre(String titre) throws SQLException {
        Livre l = findByTitreExact(titre);
        if (l == null) {
            List<Livre> matches = findByTitre(titre);
            if (!matches.isEmpty()) {
                l = matches.get(0);
            }
        }
        if (l != null) {
            return delete(l.getIsbn());
        }
        return false;
    }

    /**
     * Dénombre le nombre total d'exemplaires physiques attachés à un livre identifié par son titre.
     *
     * @param titre le titre exact ou un fragment du titre recherché
     * @return le nombre total d'exemplaires physiques enregistrés pour cet ouvrage
     * @throws SQLException en cas d'anomalie SQL
     */
    public int countExemplairesByTitre(String titre) throws SQLException {
        String sql = "SELECT COUNT(e.id) FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE LOWER(l.titre) = LOWER(?) OR LOWER(l.titre) LIKE LOWER(?)";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, titre.trim());
            ps.setString(2, "%" + titre.trim() + "%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return 0;
    }

    // =====================================================================
    //  Mapping
    // =====================================================================

    /**
     * Convertit une ligne de résultat JDBC {@link ResultSet} en objet métier {@link Livre}.
     * Gère avec résilience l'éventuelle absence de colonnes optionnelles (genre, thème, niveau, description).
     *
     * @param rs le curseur SQL positionné
     * @return un objet {@link Livre} hydraté
     * @throws SQLException en cas de problème d'accès aux colonnes obligatoires
     */
    private Livre mapRow(ResultSet rs) throws SQLException {
        Livre l = new Livre();
        l.setIsbn(rs.getString("isbn"));
        l.setTitre(rs.getString("titre"));
        l.setAuteur(rs.getString("auteur"));
        l.setEditeur(rs.getString("editeur"));
        l.setCouvertureUrl(rs.getString("couverture_url"));
        try {
            String g = rs.getString("genre");
            l.setGenre(g != null ? g : "");
        } catch (SQLException ignored) {
            l.setGenre("");
        }
        try {
            String t = rs.getString("theme");
            l.setTheme(t != null ? t : "");
        } catch (SQLException ignored) {
            l.setTheme("");
        }
        try {
            String n = rs.getString("niveau");
            l.setNiveau(n != null ? n : "");
        } catch (SQLException ignored) {
            l.setNiveau("");
        }
        try {
            String d = rs.getString("description");
            l.setDescription(d != null ? d : "");
        } catch (SQLException ignored) {
            l.setDescription("");
        }
        return l;
    }

    /**
     * Récupère la liste de tous les genres littéraires distincts et non vides présents dans le catalogue.
     * Les valeurs sont triées par ordre alphabétique insensible à la casse.
     *
     * @return la liste des libellés de genres uniques
     * @throws SQLException si la requête échoue
     */
    public List<String> findAllGenres() throws SQLException {
        String sql = "SELECT DISTINCT genre FROM livres WHERE genre IS NOT NULL AND TRIM(genre) != '' ORDER BY genre COLLATE NOCASE";
        List<String> list = new ArrayList<>();
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    /**
     * Récupère la liste de tous les thèmes distincts et non vides présents dans le catalogue.
     * Les valeurs sont triées par ordre alphabétique insensible à la casse.
     *
     * @return la liste des thèmes uniques
     * @throws SQLException si la requête échoue
     */
    public List<String> findAllThemes() throws SQLException {
        String sql = "SELECT DISTINCT theme FROM livres WHERE theme IS NOT NULL AND TRIM(theme) != '' ORDER BY theme COLLATE NOCASE";
        List<String> list = new ArrayList<>();
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }

    /**
     * Récupère la liste de tous les niveaux scolaires distincts et non vides présents dans le catalogue.
     * Les valeurs sont triées par ordre alphabétique insensible à la casse.
     *
     * @return la liste des niveaux scolaires uniques
     * @throws SQLException si la requête échoue
     */
    public List<String> findAllNiveaux() throws SQLException {
        String sql = "SELECT DISTINCT niveau FROM livres WHERE niveau IS NOT NULL AND TRIM(niveau) != '' ORDER BY niveau COLLATE NOCASE";
        List<String> list = new ArrayList<>();
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) list.add(rs.getString(1));
        }
        return list;
    }
}
