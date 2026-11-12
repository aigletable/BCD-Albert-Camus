package fr.ecole.bcd.database;

import fr.ecole.bcd.model.BilanStatistiques;
import fr.ecole.bcd.model.Emprunt;
import fr.ecole.bcd.model.Utilisateur;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Objet d'accès aux données (DAO) pour la gestion de la table relationnelle {@code emprunts}.
 *
 * <p>Cette classe constitue le cœur fonctionnel du prêt/retour de la bibliothèque BCD Express.
 * Elle centralise :</p>
 * <ul>
 *   <li>La formalisation des emprunts réguliers (durée par défaut de 7 jours) et des prêts enseignants (durées personnalisées).</li>
 *   <li>Le suivi en temps réel des prêts actifs et la détection instantanée des retards de restitution.</li>
 *   <li>Le renouvellement des prêts (prolongation d'une semaine supplémentaire avec verrouillage {@code est_renouvele = true}).</li>
 *   <li>La restitution avec archivage historique et déverrouillage de l'exemplaire physique.</li>
 *   <li>Les requêtes jointes à hautes performances pour l'affichage avec métadonnées complètes (usagers et livres).</li>
 *   <li>Le calcul analytique des indicateurs de fréquentation et du palmarès pour le bilan annuel de la direction et du conseil d'école.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class EmpruntDao {

    /**
     * Constructeur par défaut de {@code EmpruntDao}.
     */
    public EmpruntDao() {
    }

    // =====================================================================
    //  CREATE
    // =====================================================================

    /**
     * Crée et enregistre un nouvel emprunt standard pour une durée conventionnelle de 7 jours.
     *
     * @param utilisateurNumero le numéro d'identification unique de l'emprunteur (ex. "E-101")
     * @param exemplaireId      l'identifiant technique de l'exemplaire physique emprunté
     * @return l'instance {@link Emprunt} générée et persistée avec son identifiant auto-incrémenté
     * @throws SQLException si l'enregistrement échoue ou si la clé générée est indisponible
     */
    public Emprunt creerEmprunt(String utilisateurNumero, int exemplaireId) throws SQLException {
        LocalDate now = LocalDate.now();
        LocalDate limite = now.plusDays(7);
        return creerEmpruntAvecDateLimite(utilisateurNumero, exemplaireId, now, limite);
    }

    /**
     * Crée un nouvel emprunt en spécifiant explicitement sa date de prise d'effet et sa date d'échéance.
     * Cette méthode est principalement exploitée pour les séries pédagogiques et paniers de classe enseignants.
     *
     * @param utilisateurNumero le numéro unique de l'emprunteur
     * @param exemplaireId      l'identifiant unique de la copie physique
     * @param dateEmprunt       la date de début du prêt
     * @param dateLimite        la date limite convenue pour la restitution
     * @return l'objet {@link Emprunt} persisté
     * @throws SQLException en cas de violation d'intégrité ou d'erreur SQL
     */
    public Emprunt creerEmpruntAvecDateLimite(String utilisateurNumero, int exemplaireId,
                                              LocalDate dateEmprunt, LocalDate dateLimite) throws SQLException {
        String sql = "INSERT INTO emprunts (utilisateur_numero, exemplaire_id, date_emprunt, date_limite) "
                   + "VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, utilisateurNumero);
            ps.setInt(2, exemplaireId);
            ps.setString(3, dateEmprunt.toString());
            ps.setString(4, dateLimite.toString());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    Emprunt e = new Emprunt();
                    e.setId(keys.getInt(1));
                    e.setUtilisateurNumero(utilisateurNumero);
                    e.setExemplaireId(exemplaireId);
                    e.setDateEmprunt(dateEmprunt);
                    e.setDateLimite(dateLimite);
                    e.setEstRenouvele(false);
                    return e;
                }
            }
        }
        throw new SQLException("Échec de la création de l'emprunt.");
    }

    // =====================================================================
    //  READ
    // =====================================================================

    /**
     * Recherche un emprunt par son identifiant numérique unique.
     *
     * @param id la clé primaire de l'emprunt
     * @return l'entité {@link Emprunt} trouvée, ou {@code null} si inexistante
     * @throws SQLException si la requête SQL échoue
     */
    public Emprunt findById(int id) throws SQLException {
        String sql = "SELECT * FROM emprunts WHERE id = ?";

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
     * Récupère la liste de tous les emprunts actuellement actifs (non encore restitués, {@code date_rendu IS NULL}).
     * Les résultats sont triés par date d'échéance croissante (les plus urgents en tête).
     *
     * @return la liste ordonnée des {@link Emprunt} en cours
     * @throws SQLException si la requête échoue
     */
    public List<Emprunt> findEnCours() throws SQLException {
        String sql = "SELECT * FROM emprunts WHERE date_rendu IS NULL "
                   + "ORDER BY date_limite ASC";
        return executeQuery(sql);
    }

    /**
     * Récupère l'ensemble des emprunts en cours détenus par un usager spécifique.
     *
     * @param numero le numéro unique de l'utilisateur
     * @return la liste des emprunts non rendus de cet utilisateur
     * @throws SQLException en cas d'erreur de lecture
     */
    public List<Emprunt> findEnCoursByUtilisateur(String numero) throws SQLException {
        String sql = "SELECT * FROM emprunts WHERE utilisateur_numero = ? AND date_rendu IS NULL "
                   + "ORDER BY date_limite ASC";
        List<Emprunt> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Recherche l'emprunt actif rattaché à une copie physique donnée.
     *
     * @param exemplaireId l'identifiant technique de l'exemplaire
     * @return l'objet {@link Emprunt} actif, ou {@code null} si l'exemplaire n'est pas prêté
     * @throws SQLException en cas de dysfonctionnement de la requête
     */
    public Emprunt findEnCoursByExemplaire(int exemplaireId) throws SQLException {
        String sql = "SELECT * FROM emprunts WHERE exemplaire_id = ? AND date_rendu IS NULL";

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
     * Récupère la liste de tous les emprunts en retard (non rendus et dont la date d'échéance est antérieure à ce jour).
     *
     * @return la liste des emprunts en retard triés par date d'échéance
     * @throws SQLException si la requête échoue
     */
    public List<Emprunt> findEnRetard() throws SQLException {
        String sql = "SELECT * FROM emprunts WHERE date_rendu IS NULL AND date_limite < ? "
                   + "ORDER BY date_limite ASC";
        List<Emprunt> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    /**
     * Vérifie si un emprunteur donné présente au moins un emprunt en situation de retard.
     *
     * @param utilisateurNumero le numéro de l'utilisateur
     * @return {@code true} si l'usager a au moins un livre en retard non rendu, {@code false} sinon
     * @throws SQLException si la vérification échoue
     */
    public boolean hasRetard(String utilisateurNumero) throws SQLException {
        String sql = "SELECT COUNT(*) FROM emprunts "
                   + "WHERE utilisateur_numero = ? AND date_rendu IS NULL AND date_limite < ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, utilisateurNumero);
            ps.setString(2, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Dénombre le nombre total d'emprunts actuellement actifs (non rendus) pour un utilisateur donné.
     *
     * @param utilisateurNumero le numéro unique de l'emprunteur
     * @return le nombre de livres actuellement en prêt
     * @throws SQLException en cas de problème de lecture
     */
    public int countEnCours(String utilisateurNumero) throws SQLException {
        String sql = "SELECT COUNT(*) FROM emprunts "
                   + "WHERE utilisateur_numero = ? AND date_rendu IS NULL";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, utilisateurNumero);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.getInt(1);
            }
        }
    }

    /**
     * Récupère l'historique chronologique intégral des emprunts (en cours et passés) d'un utilisateur.
     *
     * @param numero le numéro unique de l'emprunteur
     * @return la liste de tous ses emprunts triés du plus récent au plus ancien
     * @throws SQLException si l'historique ne peut être extrait
     */
    public List<Emprunt> findHistoriqueByUtilisateur(String numero) throws SQLException {
        String sql = "SELECT * FROM emprunts WHERE utilisateur_numero = ? "
                   + "ORDER BY date_emprunt DESC";
        List<Emprunt> list = new ArrayList<>();

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    // =====================================================================
    //  UPDATE — Retour & Renouvellement
    // =====================================================================

    /**
     * Enregistre la restitution d'un emprunt à la date du jour (met à jour {@code date_rendu = TODAY}).
     *
     * @param empruntId l'identifiant technique de l'emprunt à clôturer
     * @throws SQLException si la mise à jour échoue en base
     */
    public void retourner(int empruntId) throws SQLException {
        retournerAvecDate(empruntId, LocalDate.now());
    }

    /**
     * Enregistre la restitution d'un emprunt en précisant manuellement une date de retour.
     *
     * @param empruntId l'identifiant de l'emprunt
     * @param dateRendu la date effective de restitution
     * @throws SQLException si la mise à jour échoue
     */
    public void retournerAvecDate(int empruntId, LocalDate dateRendu) throws SQLException {
        String sql = "UPDATE emprunts SET date_rendu = ? WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, (dateRendu != null ? dateRendu : LocalDate.now()).toString());
            ps.setInt(2, empruntId);
            ps.executeUpdate();
        }
    }

    /**
     * Prolonge d'une semaine la date limite de retour d'un prêt actif
     * et bascule le marqueur {@code est_renouvele} à vrai.
     *
     * @param empruntId l'identifiant technique du prêt à prolonger
     * @throws SQLException si la mise à jour échoue
     */
    public void renouveler(int empruntId) throws SQLException {
        String sql = "UPDATE emprunts SET date_limite = ?, est_renouvele = 1 WHERE id = ?";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, LocalDate.now().plusDays(7).toString());
            ps.setInt(2, empruntId);
            ps.executeUpdate();
        }
    }

    // =====================================================================
    //  Utilitaires privés
    // =====================================================================

    /**
     * Exécute une requête SQL brute retournant une liste d'emprunts.
     *
     * @param sql la requête SELECT
     * @return la liste des objets {@link Emprunt} mappés
     * @throws SQLException si la lecture échoue
     */
    private List<Emprunt> executeQuery(String sql) throws SQLException {
        List<Emprunt> list = new ArrayList<>();

        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    /**
     * Convertit une ligne de curseur JDBC {@link ResultSet} en objet {@link Emprunt}.
     *
     * @param rs le curseur SQL positionné
     * @return une instance hydratée de {@link Emprunt}
     * @throws SQLException si une colonne ne peut être lue
     */
    private Emprunt mapRow(ResultSet rs) throws SQLException {
        Emprunt e = new Emprunt();
        e.setId(rs.getInt("id"));
        e.setUtilisateurNumero(rs.getString("utilisateur_numero"));
        e.setExemplaireId(rs.getInt("exemplaire_id"));
        e.setDateEmprunt(LocalDate.parse(rs.getString("date_emprunt")));
        e.setDateLimite(LocalDate.parse(rs.getString("date_limite")));

        String dateRendu = rs.getString("date_rendu");
        e.setDateRendu(dateRendu != null ? LocalDate.parse(dateRendu) : null);

        e.setEstRenouvele(rs.getInt("est_renouvele") == 1);
        return e;
    }

    /**
     * Récupère l'ensemble des emprunts en cours en effectuant une jointure relationnelle optimisée
     * avec les tables {@code utilisateurs}, {@code exemplaires} et {@code livres}.
     * Toutes les données nécessaires à l'affichage en interface JavaFX sont ainsi chargées en une seule requête.
     *
     * @return la liste des emprunts enrichis, ordonnés par date d'échéance croissante
     * @throws SQLException si la jointure échoue
     */
    public List<Emprunt> findAllEnCoursWithDetails() throws SQLException {
        String sql = "SELECT e.id, e.utilisateur_numero, e.exemplaire_id, e.date_emprunt, "
                   + "       e.date_limite, e.date_rendu, e.est_renouvele, "
                   + "       u.nom AS u_nom, u.prenom AS u_prenom, u.classe AS u_classe, u.type_compte AS u_type, "
                   + "       l.isbn AS l_isbn, l.titre AS l_titre, l.auteur AS l_auteur, l.couverture_url AS l_couverture "
                   + "FROM emprunts e "
                   + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                   + "JOIN exemplaires ex ON e.exemplaire_id = ex.id "
                   + "JOIN livres l ON ex.isbn = l.isbn "
                   + "WHERE e.date_rendu IS NULL "
                   + "ORDER BY e.date_limite ASC";

        List<Emprunt> list = new ArrayList<>();
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRowWithDetails(rs));
            }
        }
        return list;
    }

    /**
     * Recherche l'emprunt actif lié à un exemplaire physique donné, avec chargement complet des détails
     * de l'usager et de l'ouvrage.
     *
     * @param exemplaireId l'identifiant technique de l'exemplaire
     * @return l'objet {@link Emprunt} enrichi, ou {@code null} s'il n'est pas en prêt
     * @throws SQLException si la requête échoue
     */
    public Emprunt findEnCoursByExemplaireWithDetails(int exemplaireId) throws SQLException {
        String sql = "SELECT e.id, e.utilisateur_numero, e.exemplaire_id, e.date_emprunt, "
                   + "       e.date_limite, e.date_rendu, e.est_renouvele, "
                   + "       u.nom AS u_nom, u.prenom AS u_prenom, u.classe AS u_classe, u.type_compte AS u_type, "
                   + "       l.isbn AS l_isbn, l.titre AS l_titre, l.auteur AS l_auteur, l.couverture_url AS l_couverture "
                   + "FROM emprunts e "
                   + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                   + "JOIN exemplaires ex ON e.exemplaire_id = ex.id "
                   + "JOIN livres l ON ex.isbn = l.isbn "
                   + "WHERE e.exemplaire_id = ? AND e.date_rendu IS NULL";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setInt(1, exemplaireId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowWithDetails(rs);
                }
            }
        }
        return null;
    }

    /**
     * Recherche tous les emprunts actifs pour une fiche ISBN donnée avec leurs détails complets.
     * Utilisé notamment lors du scan rapide d'un code-barres ISBN au guichet de retour.
     *
     * @param isbn le code ISBN scanné
     * @return la liste des emprunts actifs pour ce titre
     * @throws SQLException en cas d'erreur de lecture
     */
    public List<Emprunt> findEnCoursByIsbnWithDetails(String isbn) throws SQLException {
        String sql = "SELECT e.id, e.utilisateur_numero, e.exemplaire_id, e.date_emprunt, "
                   + "       e.date_limite, e.date_rendu, e.est_renouvele, "
                   + "       u.nom AS u_nom, u.prenom AS u_prenom, u.classe AS u_classe, u.type_compte AS u_type, "
                   + "       l.isbn AS l_isbn, l.titre AS l_titre, l.auteur AS l_auteur, l.couverture_url AS l_couverture "
                   + "FROM emprunts e "
                   + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                   + "JOIN exemplaires ex ON e.exemplaire_id = ex.id "
                   + "JOIN livres l ON ex.isbn = l.isbn "
                   + "WHERE l.isbn = ? AND e.date_rendu IS NULL "
                   + "ORDER BY e.date_limite ASC";

        List<Emprunt> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowWithDetails(rs));
                }
            }
        }
        return list;
    }

    /**
     * Recherche les emprunts actifs dont le titre du livre correspond au terme de recherche
     * (recherche insensible à la casse avec repli sur la normalisation des accents et ligatures).
     *
     * @param titre l'intitulé ou une portion du titre recherché
     * @return la liste des emprunts actifs correspondants
     * @throws SQLException si la requête échoue
     */
    public List<Emprunt> findEnCoursByTitreWithDetails(String titre) throws SQLException {
        if (titre == null || titre.isBlank()) {
            return new ArrayList<>();
        }
        String clean = titre.trim();
        List<Emprunt> list = new ArrayList<>();

        String sql = "SELECT e.id, e.utilisateur_numero, e.exemplaire_id, e.date_emprunt, "
                   + "       e.date_limite, e.date_rendu, e.est_renouvele, "
                   + "       u.nom AS u_nom, u.prenom AS u_prenom, u.classe AS u_classe, u.type_compte AS u_type, "
                   + "       l.isbn AS l_isbn, l.titre AS l_titre, l.auteur AS l_auteur, l.couverture_url AS l_couverture "
                   + "FROM emprunts e "
                   + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                   + "JOIN exemplaires ex ON e.exemplaire_id = ex.id "
                   + "JOIN livres l ON ex.isbn = l.isbn "
                   + "WHERE (LOWER(l.titre) = LOWER(?) OR LOWER(l.titre) LIKE LOWER(?)) AND e.date_rendu IS NULL "
                   + "ORDER BY CASE WHEN LOWER(l.titre) = LOWER(?) THEN 0 ELSE 1 END, e.date_limite ASC";

        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, "%" + clean + "%");
            ps.setString(3, clean);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowWithDetails(rs));
                }
            }
        }

        // Fallback avec normalisation d'accents / ligatures françaises si la recherche SQL LIKE n'a rien retourné
        if (list.isEmpty()) {
            String normSearch = stripAccents(clean);
            List<Emprunt> all = findAllEnCoursWithDetails();
            for (Emprunt emp : all) {
                if (emp.getLivreTitre() != null) {
                    String normTitle = stripAccents(emp.getLivreTitre());
                    if (normTitle.contains(normSearch) || normSearch.contains(normTitle)) {
                        list.add(emp);
                    }
                }
            }
        }

        return list;
    }

    /**
     * Recherche l'historique complet des emprunts passés et actuels pour les livres dont le titre correspond.
     *
     * @param titre l'intitulé du livre recherché
     * @return la liste des emprunts historiques correspondants
     * @throws SQLException si la requête échoue
     */
    public List<Emprunt> findHistoriqueByTitre(String titre) throws SQLException {
        if (titre == null || titre.isBlank()) return new ArrayList<>();
        String clean = titre.trim();
        String sql = "SELECT e.id, e.utilisateur_numero, e.exemplaire_id, e.date_emprunt, "
                   + "       e.date_limite, e.date_rendu, e.est_renouvele, "
                   + "       u.nom AS u_nom, u.prenom AS u_prenom, u.classe AS u_classe, u.type_compte AS u_type, "
                   + "       l.isbn AS l_isbn, l.titre AS l_titre, l.auteur AS l_auteur, l.couverture_url AS l_couverture "
                   + "FROM emprunts e "
                   + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                   + "JOIN exemplaires ex ON e.exemplaire_id = ex.id "
                   + "JOIN livres l ON ex.isbn = l.isbn "
                   + "WHERE LOWER(l.titre) LIKE LOWER(?) "
                   + "ORDER BY e.date_emprunt DESC";

        List<Emprunt> list = new ArrayList<>();
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, "%" + clean + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowWithDetails(rs));
                }
            }
        }
        return list;
    }

    /**
     * Supprime les accents et ligatures typographiques d'une chaîne de caractères.
     *
     * @param s la chaîne à normaliser
     * @return la chaîne en minuscules sans diacritiques
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
     * Mappe une ligne de résultat SQL contenant les colonnes jointes vers une instance complète de {@link Emprunt}.
     *
     * @param rs le curseur SQL positionné
     * @return un objet {@link Emprunt} avec informations d'usager et de livre
     * @throws SQLException si la lecture échoue
     */
    private Emprunt mapRowWithDetails(ResultSet rs) throws SQLException {
        Emprunt e = mapRow(rs);
        e.setUtilisateurNom(rs.getString("u_nom"));
        e.setUtilisateurPrenom(rs.getString("u_prenom"));
        e.setUtilisateurClasse(rs.getString("u_classe"));
        String typeStr = rs.getString("u_type");
        if (typeStr != null) {
            try {
                e.setUtilisateurType(Utilisateur.TypeCompte.valueOf(typeStr));
            } catch (IllegalArgumentException ignored) {}
        }
        e.setLivreIsbn(rs.getString("l_isbn"));
        e.setLivreTitre(rs.getString("l_titre"));
        e.setLivreAuteur(rs.getString("l_auteur"));
        e.setLivreCouvertureUrl(rs.getString("l_couverture"));
        return e;
    }

    // =====================================================================
    //  Statistiques & Palmarès BCD (Direction & Conseil d'école)
    // =====================================================================

    /**
     * Génère et compile le bilan statistique exhaustif de la BCD pour la direction et le conseil d'école.
     * Calcule : total des prêts historiques, prêts en cours, retards, nombre d'élèves actifs,
     * nombre de livres distincts lus, top 15 des livres les plus empruntés, classement des classes et top lecteurs.
     *
     * @return le modèle de données statistique consolidé {@link BilanStatistiques}
     * @throws SQLException si l'une des requêtes d'agrégation statistique échoue
     */
    public BilanStatistiques getBilanStatistiques() throws SQLException {
        BilanStatistiques bilan = new BilanStatistiques();
        Connection conn = DatabaseManager.getConnection();

        // 1. Total historique des prêts
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM emprunts")) {
            if (rs.next()) bilan.setTotalPretsHistorique(rs.getInt(1));
        }

        // 2. Emprunts en cours
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM emprunts WHERE date_rendu IS NULL")) {
            if (rs.next()) bilan.setPretsEnCours(rs.getInt(1));
        }

        // 3. Emprunts en retard
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM emprunts WHERE date_rendu IS NULL AND date_limite < ?")) {
            ps.setString(1, LocalDate.now().toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) bilan.setPretsEnRetard(rs.getInt(1));
            }
        }

        // 4. Nombre total d'élèves inscrits
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM utilisateurs WHERE type_compte = 'ELEVE'")) {
            if (rs.next()) bilan.setTotalElevesInscrits(rs.getInt(1));
        }

        // 5. Nombre d'élèves ayant emprunté au moins une fois
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(DISTINCT e.utilisateur_numero) "
                     + "FROM emprunts e JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                     + "WHERE u.type_compte = 'ELEVE'")) {
            if (rs.next()) bilan.setElevesActifs(rs.getInt(1));
        }

        // 6. Nombre de livres (ISBN distincts) ayant été empruntés au moins une fois
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(DISTINCT ex.isbn) "
                     + "FROM emprunts e JOIN exemplaires ex ON e.exemplaire_id = ex.id")) {
            if (rs.next()) bilan.setLivresDifferentsEmpruntes(rs.getInt(1));
        }

        // 7. Top Livres les plus empruntés (Top 15)
        String sqlTopLivres =
                "SELECT l.isbn, l.titre, l.auteur, COUNT(e.id) AS nb_prets "
                + "FROM emprunts e "
                + "JOIN exemplaires ex ON e.exemplaire_id = ex.id "
                + "JOIN livres l ON ex.isbn = l.isbn "
                + "GROUP BY l.isbn, l.titre, l.auteur "
                + "ORDER BY nb_prets DESC, l.titre ASC LIMIT 15";
        List<BilanStatistiques.TopLivre> topLivres = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlTopLivres)) {
            int rang = 1;
            while (rs.next()) {
                topLivres.add(new BilanStatistiques.TopLivre(
                        rang++,
                        rs.getString("isbn"),
                        rs.getString("titre"),
                        rs.getString("auteur"),
                        rs.getInt("nb_prets")
                ));
            }
        }
        bilan.setTopLivres(topLivres);

        // 8. Statistiques par classe (élèves uniquement)
        String sqlClasses =
                "SELECT u.classe, COUNT(e.id) AS nb_prets, COUNT(DISTINCT u.numero) AS nb_emprunteurs "
                + "FROM emprunts e "
                + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                + "WHERE u.type_compte = 'ELEVE' "
                + "GROUP BY u.classe "
                + "ORDER BY nb_prets DESC, u.classe ASC";
        List<BilanStatistiques.ClasseStat> statsClasses = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlClasses)) {
            int rang = 1;
            while (rs.next()) {
                statsClasses.add(new BilanStatistiques.ClasseStat(
                        rang++,
                        rs.getString("classe"),
                        rs.getInt("nb_prets"),
                        rs.getInt("nb_emprunteurs")
                ));
            }
        }
        bilan.setStatsParClasse(statsClasses);

        // 9. Top Lecteurs élèves (Top 15)
        String sqlLecteurs =
                "SELECT u.numero, u.nom, u.prenom, u.classe, COUNT(e.id) AS nb_prets "
                + "FROM emprunts e "
                + "JOIN utilisateurs u ON e.utilisateur_numero = u.numero "
                + "WHERE u.type_compte = 'ELEVE' "
                + "GROUP BY u.numero, u.nom, u.prenom, u.classe "
                + "ORDER BY nb_prets DESC, u.nom ASC LIMIT 15";
        List<BilanStatistiques.TopLecteur> topLecteurs = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sqlLecteurs)) {
            int rang = 1;
            while (rs.next()) {
                topLecteurs.add(new BilanStatistiques.TopLecteur(
                        rang++,
                        rs.getString("numero"),
                        rs.getString("nom"),
                        rs.getString("prenom"),
                        rs.getString("classe"),
                        rs.getInt("nb_prets")
                ));
            }
        }
        bilan.setTopLecteurs(topLecteurs);

        return bilan;
    }
}
