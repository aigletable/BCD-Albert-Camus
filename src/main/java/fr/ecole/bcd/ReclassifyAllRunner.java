package fr.ecole.bcd;

import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreClassificationService.ClassificationResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Script d'exécution de reclassification complète de la base BCD Express.
 */
public class ReclassifyAllRunner {

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" 🪄 BCD Express — Reclassification Complète du Catalogue ");
        System.out.println("═══════════════════════════════════════════════════════════\n");

        try {
            DatabaseManager.setDbUrl("jdbc:sqlite:bcd.db");
            DatabaseManager.initializeDatabase();
            Connection conn = DatabaseManager.getConnection();

            // 1. Nettoyage des résidus de tests parasites (Point 5)
            System.out.println("🧹 Nettoyage des anciennes données de test (TEST-*)...");
            try (Statement stmt = conn.createStatement()) {
                int emp = stmt.executeUpdate("DELETE FROM emprunts WHERE utilisateur_numero LIKE 'TEST%' OR exemplaire_id IN (SELECT id FROM exemplaires WHERE isbn LIKE 'TEST%');");
                int mal = stmt.executeUpdate("DELETE FROM mallette_items WHERE exemplaire_id IN (SELECT id FROM exemplaires WHERE isbn LIKE 'TEST%');");
                int res = stmt.executeUpdate("DELETE FROM ressources_pedagogiques WHERE isbn LIKE 'TEST%';");
                int boi = stmt.executeUpdate("DELETE FROM boite_verification WHERE isbn LIKE 'TEST%';");
                int ex = stmt.executeUpdate("DELETE FROM exemplaires WHERE isbn LIKE 'TEST%';");
                int liv = stmt.executeUpdate("DELETE FROM livres WHERE isbn LIKE 'TEST%';");
                int usr = stmt.executeUpdate("DELETE FROM utilisateurs WHERE numero LIKE 'TEST%';");
                stmt.executeUpdate("UPDATE livres SET genre = 'Documentaire', theme = 'Harcèlement & Vivre ensemble' WHERE isbn = 'CAMUS-0817' AND genre LIKE '%Harcèlement%';");
                System.out.printf("   Purge effectuée : %d livre(s), %d exemplaire(s), %d emprunt(s), %d élève(s) de test supprimés.\n\n",
                        liv, ex, emp, usr);
            }

            // 2. Statistiques AVANT
            Map<String, Integer> genreBefore = countDistribution(conn, "genre");
            Map<String, Integer> themeBefore = countDistribution(conn, "theme");
            Map<String, Integer> niveauBefore = countDistribution(conn, "niveau");

            LivreDao dao = new LivreDao();
            LivreClassificationService classifier = new LivreClassificationService();
            List<Livre> livres = dao.findAll();
            System.out.printf("📚 Analyse de %d livres réels dans la BCD...\n", livres.size());

            long start = System.currentTimeMillis();

            // 3. Reclassification en transaction ACID rapide
            conn.setAutoCommit(false);
            String updateSql = "UPDATE livres SET genre = ?, theme = ?, niveau = ? WHERE isbn = ?";
            int updatedCount = 0;
            int changedCount = 0;

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (Livre l : livres) {
                    ClassificationResult res = classifier.classifySimple(
                            l.getIsbn(),
                            l.getTitre(),
                            l.getAuteur(),
                            l.getEditeur(),
                            l.getDescription()
                    );

                    String newGenre = LivreClassificationService.normalizeGenre(res.getGenre(), res.getNiveau());
                    String newTheme = LivreClassificationService.normalizeTheme(res.getTheme());
                    String newNiveau = LivreClassificationService.normalizeNiveau(res.getNiveau());

                    boolean changed = !newGenre.equals(l.getGenre())
                            || !newTheme.equals(l.getTheme())
                            || !newNiveau.equals(l.getNiveau());

                    if (changed) {
                        changedCount++;
                    }

                    ps.setString(1, newGenre);
                    ps.setString(2, newTheme);
                    ps.setString(3, newNiveau);
                    ps.setString(4, l.getIsbn());
                    ps.addBatch();
                    updatedCount++;

                    if (updatedCount % 100 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
                conn.commit();
            } finally {
                conn.setAutoCommit(true);
            }

            long elapsed = System.currentTimeMillis() - start;

            // 4. Statistiques APRÈS
            Map<String, Integer> genreAfter = countDistribution(conn, "genre");
            Map<String, Integer> themeAfter = countDistribution(conn, "theme");
            Map<String, Integer> niveauAfter = countDistribution(conn, "niveau");

            System.out.printf("⚡ Reclassification terminée en %d ms (%.2f ms par livre) !\n",
                    elapsed, (double) elapsed / livres.size());
            System.out.printf("🎯 Fiches rectifiées : %d / %d (%.1f %% du fonds)\n\n",
                    changedCount, updatedCount, (double) changedCount * 100 / updatedCount);

            // 5. Affichage comparatif détaillé
            System.out.println("📊 COMPARAISON AVANT / APRÈS :");
            System.out.println("-----------------------------------------------------------");
            printComparison("GENRE", genreBefore, genreAfter);
            System.out.println("-----------------------------------------------------------");
            printComparison("THÈME", themeBefore, themeAfter);
            System.out.println("-----------------------------------------------------------");
            printComparison("NIVEAU", niveauBefore, niveauAfter);
            System.out.println("-----------------------------------------------------------");

            // Exemples notables corrigés
            System.out.println("\n🔍 EXEMPLES DE LIVRES SIGNIFICATIVEMENT AMÉLIORÉS :");
            checkSample(conn, "9782218041112", "Ratus et les lapins");
            checkSample(conn, "9782747030472", "Surtout, n'ouvre pas la porte ! (J'aime lire)");
            checkSample(conn, "9782211084772", "Le roi crocodile");
            checkSample(conn, "9782070595167", "La reine JouJou");
            checkSample(conn, "9782211020466", "Guillaume l'apprenti sorcier");
            checkSample(conn, "CAMUS-0817", "Fiche interne CAMUS-0817");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, Integer> countDistribution(Connection conn, String column) throws Exception {
        Map<String, Integer> map = new HashMap<>();
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT " + column + ", count(*) FROM livres GROUP BY " + column)) {
            while (rs.next()) {
                String val = rs.getString(1);
                if (val == null || val.isBlank()) val = "(Vide)";
                map.put(val, rs.getInt(2));
            }
        }
        return map;
    }

    private static void printComparison(String label, Map<String, Integer> before, Map<String, Integer> after) {
        System.out.println("📌 " + label + " :");
        java.util.Set<String> allKeys = new java.util.TreeSet<>();
        allKeys.addAll(before.keySet());
        allKeys.addAll(after.keySet());
        for (String key : allKeys) {
            int b = before.getOrDefault(key, 0);
            int a = after.getOrDefault(key, 0);
            int diff = a - b;
            String diffStr = diff > 0 ? "(+" + diff + ")" : (diff < 0 ? "(" + diff + ")" : "(=)");
            System.out.printf("   • %-30s : %3d  ➔  %3d  %-6s\n", key, b, a, diffStr);
        }
    }

    private static void checkSample(Connection conn, String isbn, String label) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT isbn, titre, genre, theme, niveau FROM livres WHERE isbn = ?")) {
            ps.setString(1, isbn);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.printf("   ✅ [%s] \"%s\" ➔ Genre: %s | Thème: %s | Niveau: %s\n",
                            isbn, rs.getString("titre"), rs.getString("genre"), rs.getString("theme"), rs.getString("niveau"));
                }
            }
        } catch (Exception ignored) {}
    }
}
