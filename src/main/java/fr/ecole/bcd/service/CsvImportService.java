package fr.ecole.bcd.service;

import fr.ecole.bcd.database.UtilisateurDao;
import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.model.Utilisateur.TypeCompte;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service d'import CSV pour les utilisateurs (élèves et enseignants).
 *
 * <h3>Formats CSV supportés :</h3>
 *
 * <p><b>Avec en-tête</b> (l'ordre des colonnes est libre) :</p>
 * <pre>
 * numero;nom;prenom;type;classe
 * E-101;Dupont;Marie;ELEVE;CM1-A
 * P-01;Martin;Jean;ENSEIGNANT;Direction
 * </pre>
 *
 * <p><b>Sans en-tête</b> (ordre fixe) :</p>
 * <pre>
 * E-101;Dupont;Marie;ELEVE;CM1-A
 * </pre>
 *
 * <p><b>Format simplifié</b> (3 colonnes, pour les élèves) :</p>
 * <pre>
 * nom;prenom;classe
 * Dupont;Marie;CM1-A
 * Martin;Lucas;CE2-B
 * </pre>
 * → Le numéro est auto-généré (E-001, E-002…) et le type est ELEVE par défaut.
 *
 * <h3>Notes :</h3>
 * <ul>
 *   <li>Séparateurs supportés : {@code ;} (point-virgule) et {@code ,} (virgule)</li>
 *   <li>Encodage : UTF-8</li>
 *   <li>Les lignes vides et les lignes commençant par {@code #} sont ignorées</li>
 *   <li>Les doublons de numéro sont signalés dans le rapport (non importés)</li>
 * </ul>
 */
public class CsvImportService {

    private final UtilisateurDao dao = new UtilisateurDao();

    /**
     * Résultat d'un import CSV.
     */
    public static class ImportResult {
        private final int totalLines;
        private final int imported;
        private final List<String> errors;

        public ImportResult(int totalLines, int imported, List<String> errors) {
            this.totalLines = totalLines;
            this.imported = imported;
            this.errors = errors;
        }

        public int getTotalLines() { return totalLines; }
        public int getImported() { return imported; }
        public int getErrors() { return errors.size(); }
        public List<String> getErrorDetails() { return errors; }

        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("📊 Import terminé :\n");
            sb.append("   • ").append(imported).append(" utilisateur(s) importé(s)\n");
            sb.append("   • ").append(errors.size()).append(" erreur(s)\n");
            sb.append("   • ").append(totalLines).append(" ligne(s) traitée(s)\n");
            if (!errors.isEmpty()) {
                sb.append("\n⚠️ Détails des erreurs :\n");
                for (String err : errors) {
                    sb.append("   • ").append(err).append("\n");
                }
            }
            return sb.toString();
        }
    }

    /**
     * Importe des utilisateurs depuis un fichier CSV.
     *
     * @param file le fichier CSV à importer
     * @return le résultat de l'import avec le détail des erreurs
     */
    public ImportResult importFromFile(File file) throws IOException {
        List<String> errors = new ArrayList<>();
        int imported = 0;
        int totalLines = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String firstLine = reader.readLine();
            if (firstLine == null || firstLine.isBlank()) {
                errors.add("Le fichier est vide.");
                return new ImportResult(0, 0, errors);
            }

            // Détecter le séparateur
            char separator = detectSeparator(firstLine);

            // Détecter si la première ligne est un en-tête
            String[] firstFields = splitLine(firstLine, separator);
            boolean hasHeader = isHeader(firstFields);

            // Colonnes par défaut ou détectées depuis l'en-tête
            int colNumero = -1, colNom = -1, colPrenom = -1, colType = -1, colClasse = -1;

            if (hasHeader) {
                for (int i = 0; i < firstFields.length; i++) {
                    String col = firstFields[i].trim().toLowerCase();
                    switch (col) {
                        case "numero", "numéro", "num", "id" -> colNumero = i;
                        case "nom", "name", "last_name", "lastname" -> colNom = i;
                        case "prenom", "prénom", "firstname", "first_name" -> colPrenom = i;
                        case "type", "type_compte", "role" -> colType = i;
                        case "classe", "class", "group", "groupe" -> colClasse = i;
                    }
                }
            } else {
                // Pas d'en-tête → déterminer le format selon le nombre de colonnes
                if (firstFields.length == 5) {
                    // Format complet : numero;nom;prenom;type;classe
                    colNumero = 0; colNom = 1; colPrenom = 2; colType = 3; colClasse = 4;
                } else if (firstFields.length == 3) {
                    // Format simplifié : nom;prenom;classe
                    colNom = 0; colPrenom = 1; colClasse = 2;
                } else if (firstFields.length == 4) {
                    // Format 4 colonnes : numero;nom;prenom;classe (type = ELEVE par défaut)
                    colNumero = 0; colNom = 1; colPrenom = 2; colClasse = 3;
                } else {
                    errors.add("Format non reconnu : " + firstFields.length + " colonnes détectées. "
                             + "Formats attendus : 3 colonnes (nom;prenom;classe), "
                             + "4 colonnes (numero;nom;prenom;classe) ou "
                             + "5 colonnes (numero;nom;prenom;type;classe).");
                    return new ImportResult(0, 0, errors);
                }
            }

            // Vérifier que les colonnes essentielles sont présentes
            if (colNom == -1 || colPrenom == -1 || colClasse == -1) {
                errors.add("Colonnes manquantes. Les colonnes 'nom', 'prenom' et 'classe' sont obligatoires.");
                return new ImportResult(0, 0, errors);
            }

            // Auto-numérotation si pas de colonne numéro
            int autoNumCounter = 0;
            if (colNumero == -1) {
                autoNumCounter = findNextAutoNumber();
            }

            // Traiter les lignes
            String line = hasHeader ? reader.readLine() : firstLine;
            int lineNumber = hasHeader ? 2 : 1;

            while (line != null) {
                totalLines++;

                // Ignorer les lignes vides et les commentaires
                if (!line.isBlank() && !line.trim().startsWith("#")) {
                    String[] fields = splitLine(line, separator);

                    try {
                        Utilisateur u = parseLine(fields, lineNumber,
                                colNumero, colNom, colPrenom, colType, colClasse,
                                autoNumCounter);

                        if (colNumero == -1) {
                            autoNumCounter++;
                        }

                        dao.insert(u);
                        imported++;

                    } catch (Exception e) {
                        errors.add("Ligne " + lineNumber + " : " + e.getMessage());
                    }
                }

                line = reader.readLine();
                lineNumber++;
            }
        }

        return new ImportResult(totalLines, imported, errors);
    }

    // =====================================================================
    //  Parsing interne
    // =====================================================================

    private Utilisateur parseLine(String[] fields, int lineNumber,
                                  int colNumero, int colNom, int colPrenom,
                                  int colType, int colClasse,
                                  int autoNumCounter) {

        int maxCol = Math.max(Math.max(colNom, colPrenom), colClasse);
        if (colNumero >= 0) maxCol = Math.max(maxCol, colNumero);
        if (colType >= 0) maxCol = Math.max(maxCol, colType);

        if (fields.length <= maxCol) {
            throw new IllegalArgumentException(
                    "Pas assez de colonnes (" + fields.length + " au lieu de " + (maxCol + 1) + ").");
        }

        String nom = fields[colNom].trim();
        String prenom = fields[colPrenom].trim();
        String classe = fields[colClasse].trim();

        if (nom.isEmpty() || prenom.isEmpty() || classe.isEmpty()) {
            throw new IllegalArgumentException(
                    "Champs vides (nom='" + nom + "', prenom='" + prenom + "', classe='" + classe + "').");
        }

        // Numéro
        String numero;
        if (colNumero >= 0) {
            numero = fields[colNumero].trim();
            if (numero.isEmpty()) {
                throw new IllegalArgumentException("Numéro vide.");
            }
        } else {
            numero = String.format("E-%03d", autoNumCounter);
        }

        // Type de compte
        TypeCompte type = TypeCompte.ELEVE; // par défaut
        if (colType >= 0 && colType < fields.length) {
            String typeStr = fields[colType].trim().toUpperCase();
            if (!typeStr.isEmpty()) {
                try {
                    type = TypeCompte.valueOf(typeStr);
                } catch (IllegalArgumentException e) {
                    // Essayer des alias courants
                    if (typeStr.startsWith("E") || typeStr.equals("ÉLÈVE")) {
                        type = TypeCompte.ELEVE;
                    } else if (typeStr.startsWith("P") || typeStr.startsWith("ENS")
                            || typeStr.equals("PROF")) {
                        type = TypeCompte.ENSEIGNANT;
                    } else {
                        throw new IllegalArgumentException(
                                "Type de compte non reconnu : '" + typeStr + "'. "
                                + "Valeurs acceptées : ELEVE, ENSEIGNANT.");
                    }
                }
            }
        }

        return new Utilisateur(numero, nom, prenom, type, classe);
    }

    // =====================================================================
    //  Utilitaires
    // =====================================================================

    /** Détecte le séparateur (';' ou ',') en comptant les occurrences. */
    private char detectSeparator(String line) {
        long semicolons = line.chars().filter(c -> c == ';').count();
        long commas = line.chars().filter(c -> c == ',').count();
        return semicolons >= commas ? ';' : ',';
    }

    /** Découpe une ligne CSV (gestion basique, sans guillemets). */
    private String[] splitLine(String line, char separator) {
        return line.split(String.valueOf(separator), -1);
    }

    /** Vérifie si une ligne ressemble à un en-tête. */
    private boolean isHeader(String[] fields) {
        int matchCount = 0;
        String[] headerKeywords = {"numero", "numéro", "num", "id", "nom", "name",
                "prenom", "prénom", "firstname", "type", "role",
                "classe", "class", "group", "groupe", "last_name", "lastname", "first_name"};

        for (String field : fields) {
            String lower = field.trim().toLowerCase();
            for (String keyword : headerKeywords) {
                if (lower.equals(keyword)) {
                    matchCount++;
                    break;
                }
            }
        }
        // Si au moins 2 champs correspondent à des noms de colonnes connus
        return matchCount >= 2;
    }

    /**
     * Trouve le prochain numéro auto pour les élèves (E-XXX).
     * Regarde le max existant dans la base.
     */
    private int findNextAutoNumber() {
        try {
            List<Utilisateur> all = dao.findAll();
            int max = 0;
            for (Utilisateur u : all) {
                String num = u.getNumero();
                if (num.startsWith("E-")) {
                    try {
                        int n = Integer.parseInt(num.substring(2));
                        if (n > max) max = n;
                    } catch (NumberFormatException ignored) { }
                }
            }
            return max + 1;
        } catch (SQLException e) {
            return 1;
        }
    }
}
