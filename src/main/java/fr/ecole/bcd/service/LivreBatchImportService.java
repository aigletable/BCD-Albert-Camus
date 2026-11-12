package fr.ecole.bcd.service;

import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.model.Exemplaire;
import fr.ecole.bcd.model.Exemplaire.Statut;
import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.service.LivreMetadataService.MetadataResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service d'import par lot de livres à partir d'un fichier CSV.
 * <p>
 * Permet d'importer directement les listes d'ISBN scannés (ex: {@code difference.csv})
 * avec détection automatique du séparateur et de la colonne quantité,
 * interrogation en cascade des APIs (BNF, Google, OpenLibrary),
 * et création automatique des fiches et des exemplaires physiques.
 * </p>
 */
public class LivreBatchImportService {

    private final LivreDao livreDao = new LivreDao();
    private final ExemplaireDao exemplaireDao = new ExemplaireDao();
    private final LivreMetadataService metadataService = new LivreMetadataService();

    /**
     * Information de progression envoyée à l'UI pendant l'import.
     */
    public record ProgressInfo(int current, int total, String isbn, String titre, String source) {}

    /**
     * Rapport d'exécution de l'import par lot.
     */
    public static class BatchReport {
        private final int totalRows;
        private final int newBooks;
        private final int existingBooks;
        private final int copiesCreated;
        private final int coversFound;
        private final List<String> errors;

        public BatchReport(int totalRows, int newBooks, int existingBooks,
                           int copiesCreated, int coversFound, List<String> errors) {
            this.totalRows = totalRows;
            this.newBooks = newBooks;
            this.existingBooks = existingBooks;
            this.copiesCreated = copiesCreated;
            this.coversFound = coversFound;
            this.errors = errors;
        }

        public int getTotalRows() { return totalRows; }
        public int getNewBooks() { return newBooks; }
        public int getExistingBooks() { return existingBooks; }
        public int getCopiesCreated() { return copiesCreated; }
        public int getCoversFound() { return coversFound; }
        public List<String> getErrors() { return errors; }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 Rapport d'import de livres :\n");
            sb.append("   • ").append(newBooks).append(" nouveau(x) titre(s) créé(s)\n");
            sb.append("   • ").append(existingBooks).append(" livre(s) déjà existant(s) réapprovisionné(s)\n");
            sb.append("   • ").append(copiesCreated).append(" exemplaire(s) physique(s) ajouté(s)\n");
            sb.append("   • ").append(coversFound).append(" couverture(s) vérifiée(s) associée(s)\n");
            sb.append("   • ").append(totalRows).append(" ligne(s) traitée(s)\n");
            if (!errors.isEmpty()) {
                sb.append("\n⚠️ Erreurs rencontrées (").append(errors.size()).append(") :\n");
                for (int i = 0; i < Math.min(10, errors.size()); i++) {
                    sb.append("   • ").append(errors.get(i)).append("\n");
                }
                if (errors.size() > 10) {
                    sb.append("   ... et ").append(errors.size() - 10).append(" autre(s) erreur(s).\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Importe un fichier CSV d'ISBNs et crée les livres et exemplaires correspondants.
     *
     * @param file fichier CSV sélectionné
     * @param progressCallback callback de notification de progression (peut être null)
     * @return rapport complet de l'import
     */
    public BatchReport importFromCsv(File file, Consumer<ProgressInfo> progressCallback) throws IOException {
        List<String> errors = new ArrayList<>();
        int newBooks = 0;
        int existingBooks = 0;
        int copiesCreated = 0;
        int coversFound = 0;

        List<String[]> rows = readAllRows(file);
        if (rows.isEmpty()) {
            errors.add("Le fichier CSV est vide.");
            return new BatchReport(0, 0, 0, 0, 0, errors);
        }

        // Détection de l'en-tête et des colonnes
        String[] firstRow = rows.get(0);
        int isbnCol = 0;
        int qtyCol = -1;
        int startIdx = 0;

        String headerLine = String.join(" ", firstRow).toLowerCase();
        if (headerLine.contains("isbn")) {
            startIdx = 1; // Ignorer la première ligne car c'est l'en-tête
            for (int i = 0; i < firstRow.length; i++) {
                String col = firstRow[i].trim().toLowerCase();
                if (col.contains("isbn")) {
                    isbnCol = i;
                } else if (col.contains("qte") || col.contains("quantite") || col.contains("quantité") || col.contains("qty")) {
                    qtyCol = i;
                }
            }
        } else if (firstRow.length > 1) {
            // Pas d'en-tête : si 2 colonnes ou plus, col 0 = isbn, col 1 = qty
            qtyCol = 1;
        }

        int totalToProcess = rows.size() - startIdx;
        int processed = 0;

        for (int i = startIdx; i < rows.size(); i++) {
            processed++;
            String[] row = rows.get(i);
            if (row.length <= isbnCol) continue;

            String cleanIsbn = LivreMetadataService.cleanIsbn(row[isbnCol]);
            if (cleanIsbn.isEmpty()) continue;

            // Quantité
            int qty = 1;
            if (qtyCol >= 0 && row.length > qtyCol) {
                try {
                    String rawQty = row[qtyCol].trim().replaceAll("[^0-9]", "");
                    if (!rawQty.isEmpty()) {
                        qty = Math.max(1, Integer.parseInt(rawQty));
                    }
                } catch (NumberFormatException ignored) {}
            }

            try {
                Livre existing = livreDao.findByIsbn(cleanIsbn);
                if (existing != null) {
                    existingBooks++;
                    // Ajouter les exemplaires supplémentaires
                    for (int q = 0; q < qty; q++) {
                        exemplaireDao.insert(new Exemplaire(cleanIsbn, Statut.DISPONIBLE, LocalDate.now()));
                        copiesCreated++;
                    }
                    if (progressCallback != null) {
                        progressCallback.accept(new ProgressInfo(processed, totalToProcess, cleanIsbn, existing.getTitre(), "Base locale"));
                    }
                } else {
                    // Recherche multi-sources (BNF -> Google -> OpenLibrary -> Cover Epagine/OL)
                    MetadataResult meta = metadataService.rechercher(cleanIsbn);
                    String titre = meta.isFound() ? meta.getTitre() : "Livre ISBN " + cleanIsbn;
                    String auteur = meta.isFound() ? meta.getAuteur() : "";
                    String editeur = meta.isFound() ? meta.getEditeur() : "";
                    String cover = meta.getCouvertureUrl();

                    if (!cover.isBlank()) {
                        coversFound++;
                    }

                    Livre nLivre = new Livre(cleanIsbn, titre, auteur, editeur, cover,
                            meta.getGenre(), meta.getTheme(), meta.getNiveau(), meta.getDescription());
                    livreDao.insert(nLivre);
                    newBooks++;

                    for (int q = 0; q < qty; q++) {
                        exemplaireDao.insert(new Exemplaire(cleanIsbn, Statut.DISPONIBLE, LocalDate.now()));
                        copiesCreated++;
                    }

                    if (progressCallback != null) {
                        progressCallback.accept(new ProgressInfo(processed, totalToProcess, cleanIsbn, titre, meta.getSource()));
                    }
                }

            } catch (SQLException e) {
                errors.add("Ligne " + (i + 1) + " (ISBN " + cleanIsbn + ") : " + e.getMessage());
            } catch (Exception e) {
                errors.add("Ligne " + (i + 1) + " (ISBN " + cleanIsbn + ") : " + e.getMessage());
            }
        }

        return new BatchReport(totalToProcess, newBooks, existingBooks, copiesCreated, coversFound, errors);
    }

    private List<String[]> readAllRows(File file) throws IOException {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null) return list;

            char sep = (first.chars().filter(c -> c == ';').count() >= first.chars().filter(c -> c == ',').count()) ? ';' : ',';

            list.add(first.split(String.valueOf(sep), -1));

            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && !line.trim().startsWith("#")) {
                    list.add(line.split(String.valueOf(sep), -1));
                }
            }
        }
        return list;
    }
}
