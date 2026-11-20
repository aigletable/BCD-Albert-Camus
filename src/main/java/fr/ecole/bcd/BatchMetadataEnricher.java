package fr.ecole.bcd;

import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.service.LivreClassificationService;
import fr.ecole.bcd.service.LivreClassificationService.ClassificationResult;
import fr.ecole.bcd.service.LivreMetadataService;
import fr.ecole.bcd.service.LivreMetadataService.MetadataResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service de ré-enrichissement par lot des métadonnées (BnF / Google Books)
 * pour stocker définitivement Dewey, pagination, sujets RAMEAU et tranches d'âge en SQLite.
 */
public class BatchMetadataEnricher {

    private static final int SLEEP_MS = 200; // Pause polie anti-blocage

    public static void main(String[] args) {
        int maxToProcess = Integer.MAX_VALUE;
        if (args.length > 0) {
            try {
                maxToProcess = Integer.parseInt(args[0]);
            } catch (Exception ignored) {}
        }

        System.out.println("═══════════════════════════════════════════════════════════");
        System.out.println(" 🌐 BCD Express — Ré-Enrichissement & Auto-Classification ");
        System.out.println("═══════════════════════════════════════════════════════════\n");

        try {
            DatabaseManager.setDbUrl("jdbc:sqlite:bcd.db");
            DatabaseManager.initializeDatabase();
            Connection conn = DatabaseManager.getConnection();

            LivreDao dao = new LivreDao();
            LivreMetadataService metadataService = new LivreMetadataService();
            LivreClassificationService classifier = new LivreClassificationService();

            List<Livre> allLivres = dao.findAll();
            List<Livre> targets = allLivres.stream()
                    .filter(l -> l.getIsbn() != null && (l.getIsbn().startsWith("978") || l.getIsbn().startsWith("979")))
                    .limit(maxToProcess)
                    .toList();

            System.out.printf("📚 Total livres dans la base : %d\n", allLivres.size());
            System.out.printf("🎯 Livres ciblés pour l'enrichissement API : %d\n\n", targets.size());

            Map<String, Integer> genreBefore = countDistribution(conn, "genre");
            Map<String, Integer> themeBefore = countDistribution(conn, "theme");

            long start = System.currentTimeMillis();
            int enrichedCount = 0;
            int classifiedCount = 0;

            String updateSql = "UPDATE livres SET dewey = ?, page_count = ?, sujets_api = ?, age_api = ?, "
                    + "couverture_url = ?, description = ?, genre = ?, theme = ?, niveau = ? WHERE isbn = ?";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                for (int i = 0; i < targets.size(); i++) {
                    Livre l = targets.get(i);
                    String isbn = l.getIsbn();

                    boolean alreadyHasFullMeta = !l.getDewey().isBlank() && l.getPageCount() > 0 && !l.getSujetsApi().isBlank();

                    if (!alreadyHasFullMeta) {
                        MetadataResult meta = null;
                        try {
                            meta = metadataService.rechercher(isbn);
                        } catch (Exception e) {
                            System.err.printf("   ⚠️ Erreur réseau pour %s : %s\n", isbn, e.getMessage());
                        }

                        if (meta != null && meta.isFound()) {
                            enrichedCount++;
                            if (meta.getDewey() != null && !meta.getDewey().isBlank()) l.setDewey(meta.getDewey());
                            if (meta.getPageCount() > 0) l.setPageCount(meta.getPageCount());
                            if (meta.getSubjects() != null && !meta.getSubjects().isEmpty()) {
                                l.setSujetsApi(String.join(" ; ", meta.getSubjects()));
                            }
                            if (meta.getAgeMention() != null && !meta.getAgeMention().isBlank()) {
                                l.setAgeApi(meta.getAgeMention());
                            }
                            if ((l.getDescription() == null || l.getDescription().isBlank()) && !meta.getDescription().isBlank()) {
                                l.setDescription(meta.getDescription());
                            }
                            if ((l.getCouvertureUrl() == null || l.getCouvertureUrl().isBlank()) && !meta.getCouvertureUrl().isBlank()) {
                                l.setCouvertureUrl(meta.getCouvertureUrl());
                            }
                        }

                        // Pause polie anti-blocage uniquement lors des vraies requêtes réseau
                        try {
                            Thread.sleep(SLEEP_MS);
                        } catch (InterruptedException ignored) {}
                    }

                    // Classification enrichie avec 100% des métadonnées
                    ClassificationResult cr = classifier.classifyFromLivre(l);
                    l.setGenre(cr.getGenre());
                    l.setTheme(cr.getTheme());
                    l.setNiveau(cr.getNiveau());
                    classifiedCount++;

                    ps.setString(1, l.getDewey());
                    ps.setInt(2, l.getPageCount());
                    ps.setString(3, l.getSujetsApi());
                    ps.setString(4, l.getAgeApi());
                    ps.setString(5, l.getCouvertureUrl() != null ? l.getCouvertureUrl() : "");
                    ps.setString(6, l.getDescription() != null ? l.getDescription() : "");
                    ps.setString(7, l.getGenre());
                    ps.setString(8, l.getTheme());
                    ps.setString(9, l.getNiveau());
                    ps.setString(10, l.getIsbn());
                    ps.executeUpdate();

                    if ((i + 1) % 10 == 0 || (i + 1) == targets.size()) {
                        long curElapsed = System.currentTimeMillis() - start;
                        double perItem = (double) curElapsed / (i + 1);
                        long remainingMs = (long) (perItem * (targets.size() - (i + 1)));
                        System.out.printf("   [%3d / %3d] (%.1f%%) — Enrichi(s) : %d — ETA : %ds — Dernier : %s (\"%s\") [%s | %s | %s]\n",
                                (i + 1), targets.size(), (double) (i + 1) * 100 / targets.size(),
                                enrichedCount, remainingMs / 1000,
                                l.getIsbn(), truncate(l.getTitre(), 28),
                                l.getGenre(), l.getTheme(), l.getNiveau());
                    }

                    if ((i + 1) % 50 == 0) {
                        try (Statement s = conn.createStatement()) {
                            s.execute("PRAGMA wal_checkpoint(PASSIVE);");
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Checkpoint WAL pour persister immédiatement
            try (Statement s = conn.createStatement()) {
                s.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            }

            long totalElapsed = System.currentTimeMillis() - start;
            System.out.println("\n-----------------------------------------------------------");
            System.out.printf("✅ Ré-enrichissement terminé en %d s (%.1f min) !\n",
                    totalElapsed / 1000, (double) totalElapsed / 60000);
            System.out.printf("📥 Notices enrichies BnF/Google : %d / %d\n", enrichedCount, targets.size());

            Map<String, Integer> genreAfter = countDistribution(conn, "genre");
            Map<String, Integer> themeAfter = countDistribution(conn, "theme");

            System.out.println("\n📊 COMPARAISON DES GENRES :");
            printComparison("GENRE", genreBefore, genreAfter);

            System.out.println("\n📊 COMPARAISON DES THÈMES :");
            printComparison("THÈME", themeBefore, themeAfter);

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

    private static String truncate(String str, int max) {
        if (str == null) return "";
        return str.length() <= max ? str : str.substring(0, max - 1) + "…";
    }
}
