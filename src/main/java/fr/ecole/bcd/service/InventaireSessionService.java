package fr.ecole.bcd.service;

import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.MotifEcart;
import fr.ecole.bcd.model.RecolementItem;
import fr.ecole.bcd.model.SessionInventaire;
import fr.ecole.bcd.model.SessionInventaire.PerimetreType;
import fr.ecole.bcd.model.SessionInventaire.StatutSession;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service centralisant la gestion des campagnes d'inventaire, le gel de stock
 * et la justification des écarts (+/-).
 * <p>
 * Implémente le système professionnel de stock inspiré du retail (Max Mara) :
 * <ul>
 *   <li>Lancement d'une session ciblée (1 Livre, 1 Genre, 1 Thème, 1 Niveau ou Tout)</li>
 *   <li><b>Gel automatique des mouvements (prêts/sorties)</b> sur le périmètre actif</li>
 *   <li>Rapprochement physique, pointage douchette et qualification des écarts</li>
 *   <li>Clôture avec ajustement des statuts et génération du Procès-Verbal officiel</li>
 * </ul>
 * </p>
 */
public class InventaireSessionService {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ExemplaireDao exemplaireDao = new ExemplaireDao();

    public InventaireSessionService() {
        creerTablesSiNecessaire();
    }

    /**
     * Crée les tables SQLite de persistance des sessions d'inventaire si elles n'existent pas.
     */
    public synchronized void creerTablesSiNecessaire() {
        String sqlSessions = "CREATE TABLE IF NOT EXISTS sessions_inventaire ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "date_debut TEXT NOT NULL, "
                + "date_fin TEXT DEFAULT NULL, "
                + "perimetre_type TEXT NOT NULL, "
                + "perimetre_valeur TEXT DEFAULT '', "
                + "statut TEXT NOT NULL DEFAULT 'ACTIVE', "
                + "total_attendus INTEGER DEFAULT 0, "
                + "total_presents INTEGER DEFAULT 0, "
                + "total_prets INTEGER DEFAULT 0, "
                + "total_manquants INTEGER DEFAULT 0, "
                + "total_surplus INTEGER DEFAULT 0, "
                + "commentaire TEXT DEFAULT ''"
                + ");";

        String sqlLignes = "CREATE TABLE IF NOT EXISTS inventaire_lignes ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER NOT NULL, "
                + "exemplaire_id INTEGER NOT NULL, "
                + "statut_pointage TEXT NOT NULL DEFAULT 'MANQUANT', "
                + "motif_ecart TEXT DEFAULT 'AUCUN', "
                + "commentaire TEXT DEFAULT '', "
                + "date_scan TEXT DEFAULT NULL, "
                + "FOREIGN KEY (session_id) REFERENCES sessions_inventaire(id) ON DELETE CASCADE"
                + ");";

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            stmt.execute(sqlSessions);
            stmt.execute(sqlLignes);
        } catch (SQLException e) {
            System.err.println("⚠️ Erreur création tables inventaire : " + e.getMessage());
        }
    }

    // =========================================================================
    //  Gestion de la Session Active
    // =========================================================================

    /**
     * Retourne la session d'inventaire active (s'il y en a une en cours).
     */
    public SessionInventaire getSessionActive() {
        String sql = "SELECT * FROM sessions_inventaire WHERE statut = 'ACTIVE' ORDER BY id DESC LIMIT 1";
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return mapSession(rs);
            }
        } catch (SQLException e) {
            System.err.println("Erreur getSessionActive : " + e.getMessage());
        }
        return null;
    }

    /**
     * Démarre une nouvelle session d'inventaire sur le périmètre spécifié.
     */
    public SessionInventaire demarrerSession(PerimetreType type, String valeur) throws SQLException {
        SessionInventaire active = getSessionActive();
        if (active != null) {
            throw new IllegalStateException("Une session d'inventaire est déjà active (ID #" + active.getId()
                    + " - " + active.getPerimetreLibelle() + "). Veuillez la clôturer ou l'annuler avant d'en démarrer une nouvelle.");
        }

        List<RecolementItem> items = getItemsPourPerimetre(type, valeur);

        String sql = "INSERT INTO sessions_inventaire (date_debut, perimetre_type, perimetre_valeur, statut, total_attendus, total_manquants, total_prets) "
                + "VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)";

        int pretsCount = (int) items.stream().filter(i -> i.getStatutBdd() == Exemplaire.Statut.EMPRUNTE).count();
        int manquantsCount = items.size() - pretsCount;

        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, LocalDateTime.now().format(DISPLAY_FMT));
            pstmt.setString(2, type.name());
            pstmt.setString(3, valeur != null ? valeur : "");
            pstmt.setInt(4, items.size());
            pstmt.setInt(5, manquantsCount);
            pstmt.setInt(6, pretsCount);
            pstmt.executeUpdate();

            try (ResultSet gk = pstmt.getGeneratedKeys()) {
                if (gk.next()) {
                    int sessionId = gk.getInt(1);

                    // Insérer les lignes prévisionnelles
                    String sqlLine = "INSERT INTO inventaire_lignes (session_id, exemplaire_id, statut_pointage) VALUES (?, ?, ?)";
                    try (PreparedStatement lineStmt = DatabaseManager.getConnection().prepareStatement(sqlLine)) {
                        for (RecolementItem item : items) {
                            lineStmt.setInt(1, sessionId);
                            lineStmt.setInt(2, item.getExemplaireId());
                            lineStmt.setString(3, item.getStatutInventaire().name());
                            lineStmt.addBatch();
                        }
                        lineStmt.executeBatch();
                    }

                    return getSessionActive();
                }
            }
        }
        throw new SQLException("Impossible de générer l'ID de session d'inventaire.");
    }

    // =========================================================================
    //  GEL DE STOCK (Stock Freeze)
    // =========================================================================

    /**
     * Vérifie si un exemplaire physique fait partie du périmètre de la session active
     * et doit être gelé (interdiction de sortie/emprunt).
     */
    public boolean isExemplaireGele(int exemplaireId) {
        SessionInventaire active = getSessionActive();
        if (active == null) return false;

        // Si le périmètre est TOUT -> tous les exemplaires sont gelés
        if (active.getPerimetreType() == PerimetreType.TOUT) {
            return true;
        }

        // Sinon vérification par ID dans inventaire_lignes
        String sql = "SELECT 1 FROM inventaire_lignes WHERE session_id = ? AND exemplaire_id = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, active.getId());
            pstmt.setInt(2, exemplaireId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Erreur isExemplaireGele : " + e.getMessage());
        }
        return false;
    }

    /**
     * Vérifie si un livre (par son ISBN ou son Titre) est gelé par la session active.
     */
    public boolean isLivreGele(String isbnOrTitre) {
        if (isbnOrTitre == null || isbnOrTitre.isBlank()) return false;
        SessionInventaire active = getSessionActive();
        if (active == null) return false;

        if (active.getPerimetreType() == PerimetreType.TOUT) return true;

        if (active.getPerimetreType() == PerimetreType.LIVRE) {
            if (isbnOrTitre.equalsIgnoreCase(active.getPerimetreValeur())) return true;
        }

        // Vérification par jointure (par ISBN ou par Titre du livre)
        String sql = "SELECT 1 FROM inventaire_lignes il "
                + "JOIN exemplaires e ON il.exemplaire_id = e.id "
                + "JOIN livres l ON e.isbn = l.isbn "
                + "WHERE il.session_id = ? AND (e.isbn = ? OR LOWER(l.titre) = LOWER(?))";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, active.getId());
            pstmt.setString(2, isbnOrTitre.trim());
            pstmt.setString(3, isbnOrTitre.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Erreur isLivreGele : " + e.getMessage());
        }
        return false;
    }

    // =========================================================================
    //  Pointage & Rapprochement physique
    // =========================================================================

    /**
     * Enregistre le pointage d'un exemplaire lors de la session active.
     */
    public void pointerExemplaire(int sessionId, int exemplaireId, String heureScan, boolean isSurplus) {
        String sql = "UPDATE inventaire_lignes SET statut_pointage = ?, date_scan = ?, motif_ecart = ? "
                + "WHERE session_id = ? AND exemplaire_id = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, isSurplus ? "SURPLUS" : "PRESENT");
            pstmt.setString(2, heureScan);
            pstmt.setString(3, isSurplus ? MotifEcart.RETROUVE.name() : MotifEcart.AUCUN.name());
            pstmt.setInt(4, sessionId);
            pstmt.setInt(5, exemplaireId);
            int updated = pstmt.executeUpdate();

            // Si c'est un surplus non présent dans les lignes initiales, on l'insère
            if (updated == 0 && isSurplus) {
                String insertSql = "INSERT INTO inventaire_lignes (session_id, exemplaire_id, statut_pointage, motif_ecart, date_scan) "
                        + "VALUES (?, ?, 'SURPLUS', 'RETROUVE', ?)";
                try (PreparedStatement ins = DatabaseManager.getConnection().prepareStatement(insertSql)) {
                    ins.setInt(1, sessionId);
                    ins.setInt(2, exemplaireId);
                    ins.setString(3, heureScan);
                    ins.executeUpdate();
                }
            }
        } catch (SQLException e) {
            System.err.println("Erreur pointerExemplaire : " + e.getMessage());
        }
    }

    /**
     * Enregistre un motif d'écart sur un exemplaire manquant ou retrouvé.
     */
    public void updateMotifEcart(int sessionId, int exemplaireId, MotifEcart motif, String commentaire) {
        String sql = "UPDATE inventaire_lignes SET motif_ecart = ?, commentaire = ? "
                + "WHERE session_id = ? AND exemplaire_id = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, motif != null ? motif.name() : MotifEcart.AUCUN.name());
            pstmt.setString(2, commentaire != null ? commentaire : "");
            pstmt.setInt(3, sessionId);
            pstmt.setInt(4, exemplaireId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Erreur updateMotifEcart : " + e.getMessage());
        }
    }

    // =========================================================================
    //  Clôture & Annulation de Session
    // =========================================================================

    /**
     * Clôture officiellement l'inventaire :
     * 1. Ajuste les totaux de synthèse
     * 2. Met à jour les statuts/remarques en base (ex: exemplaires déclarés PERDU ou PILON)
     * 3. Lève le gel de stock (statut = CLOTUREE)
     * 4. Génère le Procès-Verbal officiel d'inventaire HTML
     */
    public File cloturerSession(SessionInventaire session, List<RecolementItem> items, String commentaireFinal) throws SQLException {
        if (session == null) throw new IllegalArgumentException("Session invalide.");

        int presents = 0;
        int prets = 0;
        int manquants = 0;
        int surplus = 0;
        int sortiesStock = 0;

        for (RecolementItem it : items) {
            switch (it.getStatutInventaire()) {
                case PRESENT -> presents++;
                case EN_PRET -> prets++;
                case MANQUANT -> {
                    manquants++;
                    MotifEcart motif = it.getMotifEcart() != null ? it.getMotifEcart() : MotifEcart.AUCUN;

                    // Mettre à jour la ligne d'inventaire
                    updateMotifEcart(session.getId(), it.getExemplaireId(), motif, it.getMotifCommentaire());

                    // Sortie de stock : tout exemplaire manquant (sauf si en cours de réparation) est définitivement retiré du stock
                    if (motif != MotifEcart.REPARATION) {
                        try {
                            exemplaireDao.delete(it.getExemplaireId());
                            sortiesStock++;
                        } catch (Exception e) {
                            System.err.println("⚠️ Impossible de retirer du stock l'exemplaire #" + it.getExemplaireId() + " : " + e.getMessage());
                        }
                    } else {
                        // En cours de réparation : conservé au stock avec remarque explicative
                        try {
                            String note = String.format("[EN RÉPARATION le %s] %s",
                                    LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                    it.getMotifCommentaire().isBlank() ? "En reliure / réparation" : it.getMotifCommentaire());
                            exemplaireDao.updateRemarque(it.getExemplaireId(), note);
                        } catch (Exception ignored) {}
                    }
                }
                case SURPLUS -> surplus++;
            }
        }

        String sql = "UPDATE sessions_inventaire SET date_fin = ?, statut = 'CLOTUREE', "
                + "total_attendus = ?, total_presents = ?, total_prets = ?, "
                + "total_manquants = ?, total_surplus = ?, commentaire = ? "
                + "WHERE id = ?";

        String dateFin = LocalDateTime.now().format(DISPLAY_FMT);
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, dateFin);
            pstmt.setInt(2, items.size());
            pstmt.setInt(3, presents);
            pstmt.setInt(4, prets);
            pstmt.setInt(5, manquants);
            pstmt.setInt(6, surplus);
            pstmt.setString(7, commentaireFinal != null ? commentaireFinal : "");
            pstmt.setInt(8, session.getId());
            pstmt.executeUpdate();
        }

        session.setDateFin(dateFin);
        session.setStatut(StatutSession.CLOTUREE);
        session.setTotalAttendus(items.size());
        session.setTotalPresents(presents);
        session.setTotalPrets(prets);
        session.setTotalManquants(manquants);
        session.setTotalSurplus(surplus);
        session.setCommentaire(commentaireFinal);

        // Générer le Procès-Verbal HTML officiel
        return genererPvInventaireHtml(session, items);
    }

    /**
     * Annule la session en cours sans modifier les exemplaires, et lève le gel.
     */
    public void annulerSession(int sessionId) throws SQLException {
        String sql = "UPDATE sessions_inventaire SET date_fin = ?, statut = 'ANNULEE' WHERE id = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, LocalDateTime.now().format(DISPLAY_FMT));
            pstmt.setInt(2, sessionId);
            pstmt.executeUpdate();
        }
    }

    // =========================================================================
    //  Chargement des Exemplaires selon le Périmètre
    // =========================================================================

    /**
     * Charge les exemplaires correspondant au périmètre choisi pour la session.
     */
    /**
     * Charge les exemplaires correspondant à la session en réconciliant les états
     * déjà enregistrés dans inventaire_lignes (scans, statuts, motifs, commentaires, surplus).
     */
    public List<RecolementItem> getItemsPourSession(SessionInventaire session) throws SQLException {
        if (session == null) return exemplaireDao.findAllForRecolement();
        List<RecolementItem> items = getItemsPourPerimetre(session.getPerimetreType(), session.getPerimetreValeur());

        String sql = "SELECT exemplaire_id, statut_pointage, motif_ecart, commentaire, date_scan "
                   + "FROM inventaire_lignes WHERE session_id = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setInt(1, session.getId());
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int exId = rs.getInt("exemplaire_id");
                    String statutPointage = rs.getString("statut_pointage");
                    String motifEcart = rs.getString("motif_ecart");
                    String comm = rs.getString("commentaire");
                    String dateScan = rs.getString("date_scan");

                    RecolementItem matched = items.stream().filter(i -> i.getExemplaireId() == exId).findFirst().orElse(null);
                    if (matched != null) {
                        if ("PRESENT".equalsIgnoreCase(statutPointage)) {
                            matched.marquerPresent(dateScan != null ? dateScan : "");
                        } else if ("SURPLUS".equalsIgnoreCase(statutPointage)) {
                            matched.marquerSurplus(dateScan != null ? dateScan : "");
                        }
                        if (motifEcart != null && !motifEcart.isBlank() && !motifEcart.equalsIgnoreCase("AUCUN")) {
                            matched.setMotifEcart(MotifEcart.fromCode(motifEcart));
                        }
                        if (comm != null && !comm.isBlank()) {
                            matched.setMotifCommentaire(comm);
                        }
                    } else if ("SURPLUS".equalsIgnoreCase(statutPointage)) {
                        Exemplaire ex = exemplaireDao.findById(exId);
                        if (ex != null) {
                            Livre l = new fr.ecole.bcd.database.LivreDao().findByIsbn(ex.getIsbn());
                            RecolementItem surplusItem = new RecolementItem(
                                    ex.getId(), ex.getIsbn(), l != null ? l.getTitre() : "Livre inconnu",
                                    l != null ? l.getAuteur() : "", "", "✨ Retrouvé en surplus", ex.getStatut(),
                                    "", "", null, null
                            );
                            surplusItem.marquerSurplus(dateScan != null ? dateScan : "");
                            if (motifEcart != null && !motifEcart.isBlank()) surplusItem.setMotifEcart(MotifEcart.fromCode(motifEcart));
                            if (comm != null) surplusItem.setMotifCommentaire(comm);
                            items.add(surplusItem);
                        }
                    }
                }
            }
        }
        return items;
    }

    public List<RecolementItem> getItemsPourPerimetre(PerimetreType type, String valeur) throws SQLException {
        List<RecolementItem> all = exemplaireDao.findAllForRecolement();
        if (type == null || type == PerimetreType.TOUT) {
            return all;
        }

        List<RecolementItem> result = new ArrayList<>();
        String valTrim = valeur != null ? valeur.trim() : "";

        for (RecolementItem item : all) {
            boolean match = false;
            switch (type) {
                case GENRE:
                    match = checkBookFieldMatch(item.getIsbn(), "genre", valTrim);
                    break;
                case THEME:
                    match = checkBookFieldMatch(item.getIsbn(), "theme", valTrim);
                    break;
                case NIVEAU:
                    match = checkBookNiveauMatch(item.getIsbn(), valTrim);
                    break;
                case LIVRE:
                    match = item.getIsbn().equalsIgnoreCase(valTrim)
                            || item.getTitre().equalsIgnoreCase(valTrim)
                            || (item.getTitre() + " - " + item.getAuteur()).equalsIgnoreCase(valTrim);
                    break;
                default:
                    match = true;
                    break;
            }
            if (match) {
                result.add(item);
            }
        }
        return result;
    }

    private boolean checkBookFieldMatch(String isbn, String field, String targetVal) {
        String sql = "SELECT " + field + " FROM livres WHERE isbn = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, isbn);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString(1);
                    return val != null && val.equalsIgnoreCase(targetVal);
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    private boolean checkBookNiveauMatch(String isbn, String targetNiveau) {
        String sql = "SELECT niveau FROM livres WHERE isbn = ?";
        try (PreparedStatement pstmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, isbn);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String niv = rs.getString(1);
                    return LivreClassificationService.matchesNiveau(niv, targetNiveau);
                }
            }
        } catch (SQLException ignored) {}
        return false;
    }

    // =========================================================================
    //  Génération du Procès-Verbal Officiel HTML
    // =========================================================================

    /**
     * Génère un Procès-Verbal officiel d'inventaire au format HTML pour l'école Albert Camus Pasteur.
     */
    public File genererPvInventaireHtml(SessionInventaire session, List<RecolementItem> items) {
        String cleanDate = LocalDate.now().toString().replace("-", "");
        File file = new File(System.getProperty("user.home"), "PV_Inventaire_BCD_" + session.getId() + "_" + cleanDate + ".html");

        long presents = items.stream().filter(i -> i.getStatutInventaire() == RecolementItem.StatutInventaire.PRESENT).count();
        long prets = items.stream().filter(i -> i.getStatutInventaire() == RecolementItem.StatutInventaire.EN_PRET).count();
        long manquants = items.stream().filter(i -> i.getStatutInventaire() == RecolementItem.StatutInventaire.MANQUANT).count();
        long surplus = items.stream().filter(i -> i.getStatutInventaire() == RecolementItem.StatutInventaire.SURPLUS).count();

        double taux = items.isEmpty() ? 100.0 : ((presents + prets) * 100.0) / items.size();

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n");
        html.append("  <meta charset=\"UTF-8\">\n");
        html.append("  <title>Procès-Verbal d'Inventaire — BCD Express</title>\n");
        html.append("  <style>\n");
        html.append("    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #F8FAFC; color: #0F172A; margin: 0; padding: 30px; }\n");
        html.append("    .container { max-width: 960px; margin: 0 auto; background: #FFFFFF; border-radius: 14px; box-shadow: 0 4px 18px rgba(15,23,42,0.06); padding: 36px; border: 1px solid #E2E8F0; }\n");
        html.append("    .header { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid #E2E8F0; padding-bottom: 20px; margin-bottom: 24px; }\n");
        html.append("    .school-title { font-size: 20px; font-weight: 800; color: #1E293B; margin: 0; }\n");
        html.append("    .school-sub { font-size: 13px; color: #64748B; margin-top: 4px; }\n");
        html.append("    .badge-session { background: #EFF6FF; color: #1A66FF; padding: 6px 14px; border-radius: 999px; font-weight: 700; font-size: 12px; border: 1px solid #BFDBFE; }\n");
        html.append("    .kpi-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 14px; margin-bottom: 28px; }\n");
        html.append("    .kpi-box { background: #F8FAFC; border: 1px solid #E2E8F0; border-radius: 10px; padding: 14px; text-align: center; }\n");
        html.append("    .kpi-title { font-size: 11px; text-transform: uppercase; font-weight: 700; color: #64748B; margin-bottom: 6px; }\n");
        html.append("    .kpi-val { font-size: 22px; font-weight: 800; color: #0F172A; }\n");
        html.append("    .kpi-green { color: #16A34A; }\n");
        html.append("    .kpi-orange { color: #EA580C; }\n");
        html.append("    .kpi-blue { color: #1A66FF; }\n");
        html.append("    table { width: 100%; border-collapse: collapse; margin-top: 14px; font-size: 12.5px; }\n");
        html.append("    th { background: #F1F5F9; color: #475569; text-align: left; padding: 10px 12px; font-weight: 700; border-bottom: 2px solid #CBD5E1; }\n");
        html.append("    td { padding: 9px 12px; border-bottom: 1px solid #E2E8F0; color: #334155; vertical-align: middle; }\n");
        html.append("    tr:nth-child(even) { background: #F8FAFC; }\n");
        html.append("    .pill { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }\n");
        html.append("    .pill-green { background: #DCFCE7; color: #15803D; }\n");
        html.append("    .pill-blue { background: #DBEAFE; color: #1D4ED8; }\n");
        html.append("    .pill-orange { background: #FFEDD5; color: #C2410C; }\n");
        html.append("    .pill-purple { background: #F3E8FF; color: #7E22CE; }\n");
        html.append("    .signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; margin-top: 40px; padding-top: 24px; border-top: 1px solid #E2E8F0; }\n");
        html.append("    .sig-box { border: 1px dashed #CBD5E1; border-radius: 8px; padding: 20px; height: 90px; }\n");
        html.append("    .sig-title { font-weight: 700; font-size: 12.5px; color: #64748B; margin-bottom: 6px; }\n");
        html.append("    @media print { body { background: white; padding: 0; } .container { border: none; box-shadow: none; padding: 0; } }\n");
        html.append("  </style>\n</head>\n<body>\n");
        html.append("<div class=\"container\">\n");

        // En-tête officiel
        html.append("  <div class=\"header\">\n");
        html.append("    <div>\n");
        html.append("      <h1 class=\"school-title\">École Albert Camus Pasteur — Meudon</h1>\n");
        html.append("      <div class=\"school-sub\">Procès-Verbal de Récolement &amp; Inventaire BCD Express • Rapprochement de Stock</div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"badge-session\">SESSION #").append(session.getId()).append(" • ").append(session.getPerimetreLibelle()).append("</div>\n");
        html.append("  </div>\n");

        // Synthèse métadonnées
        html.append("  <div style=\"margin-bottom: 20px; font-size: 13px; color: #475569;\">\n");
        html.append("    <strong>Périmètre :</strong> ").append(session.getPerimetreLibelle()).append(" &nbsp;|&nbsp; \n");
        html.append("    <strong>Démarré le :</strong> ").append(session.getDateDebut()).append(" &nbsp;|&nbsp; \n");
        html.append("    <strong>Clôturé le :</strong> ").append(session.getDateFin()).append("\n");
        html.append("  </div>\n");

        // KPIs
        html.append("  <div class=\"kpi-grid\">\n");
        html.append("    <div class=\"kpi-box\"><div class=\"kpi-title\">Total Suivi</div><div class=\"kpi-val\">").append(items.size()).append("</div></div>\n");
        html.append("    <div class=\"kpi-box\"><div class=\"kpi-title\">Présents Rayon</div><div class=\"kpi-val kpi-green\">").append(presents).append("</div></div>\n");
        html.append("    <div class=\"kpi-box\"><div class=\"kpi-title\">En Prêt Justifié</div><div class=\"kpi-val kpi-blue\">").append(prets).append("</div></div>\n");
        html.append("    <div class=\"kpi-box\"><div class=\"kpi-title\">Écarts Manquants</div><div class=\"kpi-val kpi-orange\">").append(manquants).append("</div></div>\n");
        html.append("    <div class=\"kpi-box\"><div class=\"kpi-title\">Taux de Présence</div><div class=\"kpi-val kpi-green\">").append(String.format("%.1f%%", taux)).append("</div></div>\n");
        html.append("  </div>\n");

        if (manquants > 0) {
            html.append("  <div style=\"background: #FEF2F2; border: 1px solid #FCA5A5; border-radius: 8px; padding: 12px 16px; margin-bottom: 20px; font-size: 13px; color: #991B1B;\">\n");
            html.append("    <strong>📦 Régularisation automatique du stock :</strong> ").append(manquants)
                .append(" exemplaire(s) manquant(s) ont été définitivement retirés du stock de la BCD et décomptés du catalogue.\n");
            html.append("  </div>\n");
        }

        // Tableau des exemplaires
        html.append("  <h3>Détail du Rapprochement &amp; Qualification des Écarts</h3>\n");
        html.append("  <table>\n");
        html.append("    <thead>\n");
        html.append("      <tr>\n");
        html.append("        <th style=\"width: 75px;\">N° Ex.</th>\n");
        html.append("        <th>Livre (Titre &amp; Auteur)</th>\n");
        html.append("        <th>ISBN</th>\n");
        html.append("        <th>Statut Pointage</th>\n");
        html.append("        <th>Motif d'Écart (+/-)</th>\n");
        html.append("        <th>Situation / Emprunteur</th>\n");
        html.append("      </tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");

        for (RecolementItem it : items) {
            String pillClass = "pill-orange";
            String statutLabel = "Manquant";
            if (it.getStatutInventaire() == RecolementItem.StatutInventaire.PRESENT) {
                pillClass = "pill-green";
                statutLabel = "✅ Présent (" + it.getHeureScan() + ")";
            } else if (it.getStatutInventaire() == RecolementItem.StatutInventaire.EN_PRET) {
                pillClass = "pill-blue";
                statutLabel = "👤 En prêt";
            } else if (it.getStatutInventaire() == RecolementItem.StatutInventaire.SURPLUS) {
                pillClass = "pill-purple";
                statutLabel = "✨ Surplus";
            }

            html.append("      <tr>\n");
            html.append("        <td><strong>#").append(it.getExemplaireId()).append("</strong></td>\n");
            html.append("        <td><strong>").append(escapeHtml(it.getTitre())).append("</strong><br><span style=\"color:#64748B; font-size:11.5px;\">").append(escapeHtml(it.getAuteur())).append("</span></td>\n");
            html.append("        <td><code>").append(escapeHtml(it.getIsbn())).append("</code></td>\n");
            html.append("        <td><span class=\"pill ").append(pillClass).append("\">").append(statutLabel).append("</span></td>\n");
            html.append("        <td>").append(it.getMotifEcart() != MotifEcart.AUCUN ? "<strong>" + escapeHtml(it.getMotifEcart().getLibelle()) + "</strong>" : "<span style=\"color:#94A3B8;\">–</span>");
            if (!it.getMotifCommentaire().isBlank()) {
                html.append("<br><span style=\"font-size:11px; color:#64748B;\">").append(escapeHtml(it.getMotifCommentaire())).append("</span>");
            }
            html.append("</td>\n");
            html.append("        <td>").append(escapeHtml(it.getEmprunteurAffichage())).append("</td>\n");
            html.append("      </tr>\n");
        }

        html.append("    </tbody>\n");
        html.append("  </table>\n");

        // Signatures officielles
        html.append("  <div class=\"signatures\">\n");
        html.append("    <div class=\"sig-box\"><div class=\"sig-title\">Signature du Responsable BCD / Inventaire :</div></div>\n");
        html.append("    <div class=\"sig-box\"><div class=\"sig-title\">Visa de la Direction de l'Établissement :</div></div>\n");
        html.append("  </div>\n");

        html.append("</div>\n</body>\n</html>");

        try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8)) {
            fw.write(html.toString());
        } catch (IOException e) {
            System.err.println("Erreur écriture PV HTML : " + e.getMessage());
        }
        return file;
    }

    private String escapeHtml(String val) {
        if (val == null) return "";
        return val.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private SessionInventaire mapSession(ResultSet rs) throws SQLException {
        return new SessionInventaire(
                rs.getInt("id"),
                rs.getString("date_debut"),
                rs.getString("date_fin"),
                PerimetreType.valueOf(rs.getString("perimetre_type")),
                rs.getString("perimetre_valeur"),
                StatutSession.valueOf(rs.getString("statut")),
                rs.getInt("total_attendus"),
                rs.getInt("total_presents"),
                rs.getInt("total_prets"),
                rs.getInt("total_manquants"),
                rs.getInt("total_surplus"),
                rs.getString("commentaire")
        );
    }
}
