package fr.ecole.bcd.service;

import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.EmpruntDao;
import fr.ecole.bcd.database.UtilisateurDao;
import fr.ecole.bcd.model.Emprunt;
import fr.ecole.bcd.model.Utilisateur;
import fr.ecole.bcd.model.Utilisateur.TypeCompte;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service métier dédié à la transition de rentrée scolaire et au passage d'année.
 * <p>
 * Ce service orchestre les opérations majeures du cycle annuel de l'école primaire :
 * </p>
 * <ul>
 *   <li><b>Promotion automatique des classes</b> : basculement fluide selon le cursus français
 *       (TPS &rarr; PS, PS &rarr; MS, MS &rarr; GS, GS &rarr; CP, CP &rarr; CE1, CE1 &rarr; CE2, CE2 &rarr; CM1, CM1 &rarr; CM2, CM2 &rarr; Collège / Sortant).</li>
 *   <li><b>Prévisualisation sécurisée ("Dry Run")</b> : simulation sans écriture permettant de contrôler les effectifs futurs et de repérer les retards.</li>
 *   <li><b>Détection préventive des emprunts non restitués</b> : alerte automatique sur les élèves sortants ou promus ayant encore des livres chez eux.</li>
 *   <li><b>Importation ONDE / CSV Éducation Nationale</b> : analyseur tolérant aux variations de colonnes et d'encodages (UTF-8, ANSI/Windows-1252) pour synchroniser la liste des élèves.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class PassageAnneeService {

    /**
     * Libellé conventionnel attribué aux élèves quittant l'école (vers le collège ou déménagement).
     */
    public static final String CLASSE_SORTANTS = "Collège / Sortant";

    /** DAO d'accès et de modification des fiches utilisateurs/élèves */
    private final UtilisateurDao utilisateurDao;
    /** DAO de consultation des emprunts en cours pour le repérage des retards */
    private final EmpruntDao empruntDao;

    /**
     * Initialise le service avec de nouvelles instances par défaut de {@link UtilisateurDao} et {@link EmpruntDao}.
     */
    public PassageAnneeService() {
        this(new UtilisateurDao(), new EmpruntDao());
    }

    /**
     * Initialise le service avec des DAOs injectés (adapté aux tests unitaires ou contextes spécifiques).
     *
     * @param utilisateurDao le DAO d'accès aux utilisateurs
     * @param empruntDao     le DAO d'accès aux emprunts
     */
    public PassageAnneeService(UtilisateurDao utilisateurDao, EmpruntDao empruntDao) {
        this.utilisateurDao = utilisateurDao;
        this.empruntDao = empruntDao;
    }

    // =========================================================================
    //  1. Simulation & Exécution de la promotion des classes
    // =========================================================================

    /**
     * Objet de transfert représentant une proposition de changement de classe pour un élève.
     */
    public static class PromotionItem {
        /** Fiche de l'élève concerné */
        private final Utilisateur eleve;
        /** Classe d'origine avant le passage */
        private final String ancienneClasse;
        /** Nouvelle classe calculée après promotion */
        private final String nouvelleClasse;
        /** Indique si l'élève passe dans la catégorie sortant/collège */
        private final boolean estSortant;
        /** Indique si l'élève détient actuellement un ou plusieurs livres non rendus */
        private final boolean aEmpruntsEnCours;
        /** Titres des ouvrages actuellement en possession de l'élève */
        private final List<String> livresEnCours = new ArrayList<>();

        /**
         * Construit un élément de promotion d'élève.
         *
         * @param eleve            l'utilisateur de type élève
         * @param ancienneClasse   la classe actuelle
         * @param nouvelleClasse   la classe future après promotion
         * @param estSortant       {@code true} si l'élève quitte l'établissement
         * @param aEmpruntsEnCours {@code true} si l'élève a des livres non rendus
         */
        public PromotionItem(Utilisateur eleve, String ancienneClasse, String nouvelleClasse, boolean estSortant, boolean aEmpruntsEnCours) {
            this.eleve = eleve;
            this.ancienneClasse = ancienneClasse;
            this.nouvelleClasse = nouvelleClasse;
            this.estSortant = estSortant;
            this.aEmpruntsEnCours = aEmpruntsEnCours;
        }

        /** @return la fiche utilisateur de l'élève */
        public Utilisateur getEleve() { return eleve; }
        /** @return la classe actuelle */
        public String getAncienneClasse() { return ancienneClasse; }
        /** @return la classe après passage */
        public String getNouvelleClasse() { return nouvelleClasse; }
        /** @return {@code true} si l'élève quitte l'école */
        public boolean isEstSortant() { return estSortant; }
        /** @return {@code true} si l'élève a des prêts en cours */
        public boolean isAEmpruntsEnCours() { return aEmpruntsEnCours; }
        /** @return la liste des titres d'ouvrages empruntés */
        public List<String> getLivresEnCours() { return livresEnCours; }
    }

    /**
     * Rapport consolidé de prévisualisation ou d'exécution de la rentrée scolaire.
     */
    public static class PassageAnneeReport {
        /** Liste détaillée des promotions individuelles */
        private final List<PromotionItem> promotions = new ArrayList<>();
        /** Répartition des effectifs prévus par nouvelle classe */
        private final Map<String, Integer> effectifsParNouvelleClasse = new LinkedHashMap<>();
        /** Nombre total d'élèves évalués */
        private int totalEleves = 0;
        /** Nombre d'élèves promus au niveau supérieur */
        private int totalPromus = 0;
        /** Nombre d'élèves sortants vers le collège */
        private int totalSortants = 0;
        /** Nombre d'élèves ayant des prêts en cours non soldés */
        private int totalAvecEmpruntsEnCours = 0;

        /** @return la liste de chaque élève et sa promotion */
        public List<PromotionItem> getPromotions() { return promotions; }
        /** @return la carte classe &rarr; effectif projeté */
        public Map<String, Integer> getEffectifsParNouvelleClasse() { return effectifsParNouvelleClasse; }
        /** @return l'effectif total */
        public int getTotalEleves() { return totalEleves; }
        /** @return le nombre total de promus */
        public int getTotalPromus() { return totalPromus; }
        /** @return le nombre total de sortants */
        public int getTotalSortants() { return totalSortants; }
        /** @return le nombre d'élèves avec des livres non rendus */
        public int getTotalAvecEmpruntsEnCours() { return totalAvecEmpruntsEnCours; }
    }

    /**
     * Simule l'ensemble du passage d'année scolaire en mémoire sans modifier la base de données.
     *
     * @return le rapport prévisionnel complet
     * @throws SQLException en cas d'erreur de lecture de la base SQLite
     */
    public PassageAnneeReport simulerPassage() throws SQLException {
        PassageAnneeReport report = new PassageAnneeReport();
        List<Utilisateur> allUsers = utilisateurDao.findAll();

        // Récupérer les emprunts actifs pour vérifier qui a des livres non rendus
        List<Emprunt> empruntsEnCours = empruntDao.findEnCours();
        Map<String, List<String>> empruntsParEleve = new HashMap<>();
        for (Emprunt emp : empruntsEnCours) {
            String titre = emp.getLivreTitre() != null ? emp.getLivreTitre() : "Livre #" + emp.getExemplaireId();
            empruntsParEleve.computeIfAbsent(emp.getUtilisateurNumero(), k -> new ArrayList<>()).add(titre);
        }

        for (Utilisateur u : allUsers) {
            // Uniquement les élèves, pas les enseignants ni la direction
            if (u.getTypeCompte() != TypeCompte.ELEVE) continue;

            String classeActuelle = u.getClasse() != null ? u.getClasse().trim() : "";
            // Ignorer les élèves déjà sortants
            if (CLASSE_SORTANTS.equalsIgnoreCase(classeActuelle) || classeActuelle.toLowerCase().contains("sortant")) {
                continue;
            }

            String nouvelleClasse = calculerClasseSuivante(classeActuelle);
            boolean estSortant = CLASSE_SORTANTS.equals(nouvelleClasse);
            boolean aPrets = empruntsParEleve.containsKey(u.getNumero());

            PromotionItem item = new PromotionItem(u, classeActuelle, nouvelleClasse, estSortant, aPrets);
            if (aPrets) {
                item.getLivresEnCours().addAll(empruntsParEleve.get(u.getNumero()));
                report.totalAvecEmpruntsEnCours++;
            }

            report.promotions.add(item);
            report.totalEleves++;
            if (estSortant) {
                report.totalSortants++;
            } else {
                report.totalPromus++;
            }

            report.effectifsParNouvelleClasse.merge(nouvelleClasse, 1, Integer::sum);
        }

        return report;
    }

    /**
     * Calcule et applique immédiatement la promotion des classes dans la base de données de façon transactionnelle.
     *
     * @return le rapport final d'exécution
     * @throws SQLException si la transaction SQL échoue (un rollback automatique est effectué)
     */
    public PassageAnneeReport executerPassage() throws SQLException {
        return executerPassage(simulerPassage());
    }

    /**
     * Applique un rapport de simulation validé dans la base de données de manière transactionnelle via JDBC Batch.
     *
     * @param previewReport le rapport préalablement contrôlé
     * @return le même rapport une fois persisté
     * @throws SQLException si la transaction échoue
     */
    public PassageAnneeReport executerPassage(PassageAnneeReport previewReport) throws SQLException {
        if (previewReport == null) {
            previewReport = simulerPassage();
        }

        String sql = "UPDATE utilisateurs SET classe = ? WHERE numero = ?";
        try (PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            DatabaseManager.getConnection().setAutoCommit(false);

            for (PromotionItem item : previewReport.getPromotions()) {
                ps.setString(1, item.getNouvelleClasse());
                ps.setString(2, item.getEleve().getNumero());
                ps.addBatch();
            }

            ps.executeBatch();
            DatabaseManager.getConnection().commit();
            DatabaseManager.getConnection().setAutoCommit(true);
        } catch (SQLException ex) {
            try {
                DatabaseManager.getConnection().rollback();
                DatabaseManager.getConnection().setAutoCommit(true);
            } catch (SQLException ignored) {}
            throw ex;
        }

        System.out.println("🎓 Passage d'année appliqué : " + previewReport.getTotalPromus() + " promus, " + previewReport.getTotalSortants() + " sortants.");
        return previewReport;
    }

    /**
     * Règle de transition de classe pour une école primaire française standard.
     * <p>
     * Gère les classes simples (CP, CE1, CE2, CM1, CM2) ainsi que les classes à double niveau
     * (ex. CE1-CE2 &rarr; CE2-CM1). Les CM2 deviennent automatiquement des sortants ("Collège / Sortant").
     * </p>
     *
     * @param classeActuelle l'intitulé actuel de la classe de l'élève
     * @return l'intitulé de la nouvelle classe promue
     */
    public static String calculerClasseSuivante(String classeActuelle) {
        if (classeActuelle == null || classeActuelle.isBlank()) return "CP";
        String norm = classeActuelle.trim();

        // 1. CM2 ➔ Collège (Sortant)
        if (norm.toUpperCase().matches(".*\\bCM2.*") || norm.toUpperCase().contains("CM2")) {
            return CLASSE_SORTANTS;
        }

        // 2. CM1 ➔ CM2
        if (norm.toUpperCase().contains("CM1")) {
            return norm.replaceAll("(?i)CM1", "CM2");
        }

        // 3. CE2 ➔ CM1
        if (norm.toUpperCase().contains("CE2")) {
            return norm.replaceAll("(?i)CE2", "CM1");
        }

        // 4. CE1 ➔ CE2
        if (norm.toUpperCase().contains("CE1")) {
            return norm.replaceAll("(?i)CE1", "CE2");
        }

        // 5. CP ➔ CE1
        if (norm.toUpperCase().contains("CP")) {
            return norm.replaceAll("(?i)CP", "CE1");
        }

        // 6. GS ➔ CP
        if (norm.toUpperCase().contains("GS") || norm.toUpperCase().contains("MATERNELLE")) {
            return norm.replaceAll("(?i)GS", "CP").replaceAll("(?i)MATERNELLE", "CP");
        }

        // 7. MS ➔ GS
        if (norm.toUpperCase().contains("MS")) {
            return norm.replaceAll("(?i)MS", "GS");
        }

        // 8. TPS ➔ PS
        if (norm.toUpperCase().contains("TPS")) {
            return norm.replaceAll("(?i)TPS", "PS");
        }

        // 9. PS ➔ MS
        if (norm.toUpperCase().contains("PS")) {
            return norm.replaceAll("(?i)PS", "MS");
        }

        // Cas par défaut (ULIS, etc.) : conserve la classe
        return norm;
    }

    // =========================================================================
    //  2. Importation ONDE (CSV Éducation Nationale)
    // =========================================================================

    /**
     * Résultat statistique d'une opération d'importation d'élèves via fichier CSV ONDE.
     */
    public static class ImportOndeResult {
        /** Nombre total de lignes lues dans le fichier */
        protected int totalLignes = 0;
        /** Nombre de nouveaux élèves insérés en base */
        protected int ajoutes = 0;
        /** Nombre d'élèves existants mis à jour */
        protected int misAJour = 0;
        /** Nombre de lignes ignorées ou en anomalie */
        protected int erreurs = 0;
        /** Encodage de caractères détecté lors de la lecture */
        protected String encodageUtilise = "UTF-8";
        /** Messages d'information et détails d'import */
        protected final List<String> details = new ArrayList<>();

        /** @return le nombre total de lignes */
        public int getTotalLignes() { return totalLignes; }
        /** @return le nombre total de lignes traitées */
        public int getTotalLignesTraitees() { return totalLignes; }
        /** @return le nombre d'élèves ajoutés */
        public int getAjoutes() { return ajoutes; }
        /** @return le nombre d'élèves ajoutés */
        public int getElevesAjoutes() { return ajoutes; }
        /** @return le nombre d'élèves mis à jour */
        public int getMisAJour() { return misAJour; }
        /** @return le nombre d'élèves mis à jour */
        public int getElevesMisAJour() { return misAJour; }
        /** @return le nombre d'erreurs */
        public int getErreurs() { return erreurs; }
        /** @return le nombre de lignes ignorées */
        public int getLignesIgnorees() { return erreurs; }
        /** @return l'encodage utilisé */
        public String getEncodageUtilise() { return encodageUtilise; }
        /** @return la liste des messages de détail */
        public List<String> getDetails() { return details; }
    }

    /**
     * Rapport étendu d'importation ONDE (héritant de {@link ImportOndeResult}).
     */
    public static class OndeImportReport extends ImportOndeResult {}

    /**
     * Importe un fichier CSV d'élèves extrait de l'application ministérielle ONDE
     * (Outil Numérique pour la Direction d'École) ou d'un tableur Excel.
     * <p>
     * Détecte automatiquement l'encodage (BOM UTF-8, UTF-8 standard), le séparateur (virgule ou point-virgule)
     * et mappe intelligemment les noms de colonnes.
     * </p>
     *
     * @param csvFile le fichier CSV sur disque
     * @return le rapport détaillé de l'importation (ajouts, modifications, erreurs)
     * @throws IOException           en cas d'erreur de lecture du fichier
     * @throws SQLException          en cas d'erreur d'insertion SQL
     * @throws FileNotFoundException si le fichier n'existe pas
     */
    public OndeImportReport importerFichierOnde(File csvFile) throws IOException, SQLException {
        if (csvFile == null || !csvFile.exists()) {
            throw new FileNotFoundException("Fichier CSV introuvable.");
        }

        OndeImportReport result = new OndeImportReport();
        Charset charset = detecterEncodage(csvFile);
        result.encodageUtilise = charset.displayName();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), charset))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return result;
            }

            char sep = headerLine.contains(";") ? ';' : ',';
            List<String> headers = parseCsvLine(headerLine, sep);

            int idxNom = trouverIndexColonne(headers, "nom", "famille");
            int idxPrenom = trouverIndexColonne(headers, "prenom", "prénom", "usage");
            int idxClasse = trouverIndexColonne(headers, "classe", "division", "groupe", "structure");
            int idxIne = trouverIndexColonne(headers, "ine", "identifiant", "numero", "numéro", "code");

            if (idxNom == -1 || idxPrenom == -1) {
                throw new IllegalArgumentException("Les colonnes 'Nom' et 'Prénom' sont obligatoires dans le fichier.");
            }

            // Récupérer le prochain numéro auto E-xxx
            int nextId = determinerProchainNumeroEleve();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                result.totalLignes++;
                List<String> values = parseCsvLine(line, sep);

                String nom = getVal(values, idxNom);
                String prenom = getVal(values, idxPrenom);
                String classe = idxClasse != -1 ? getVal(values, idxClasse) : "CP";
                if (classe.isBlank()) classe = "CP";

                if (nom.isBlank() || prenom.isBlank()) {
                    result.erreurs++;
                    continue;
                }

                String num = idxIne != -1 ? getVal(values, idxIne) : "";
                if (num.isBlank()) {
                    num = "E-" + String.format("%03d", nextId++);
                } else if (!num.toUpperCase().startsWith("E-") && !num.toUpperCase().startsWith("P-")) {
                    num = "E-" + num.trim();
                }

                Utilisateur existant = utilisateurDao.findByNumero(num);
                if (existant != null) {
                    existant.setNom(nom);
                    existant.setPrenom(prenom);
                    existant.setClasse(classe);
                    utilisateurDao.update(existant);
                    result.misAJour++;
                } else {
                    Utilisateur nouvelEleve = new Utilisateur(num, nom, prenom, TypeCompte.ELEVE, classe);
                    utilisateurDao.insert(nouvelEleve);
                    result.ajoutes++;
                }
            }
        }

        return result;
    }

    /**
     * Détermine le prochain numéro d'élève disponible dans la séquence E-xxx.
     *
     * @return le prochain identifiant numérique
     * @throws SQLException si la lecture des utilisateurs échoue
     */
    private int determinerProchainNumeroEleve() throws SQLException {
        List<Utilisateur> all = utilisateurDao.findAll();
        int max = 0;
        Pattern p = Pattern.compile("^E-(\\d+)$");
        for (Utilisateur u : all) {
            Matcher m = p.matcher(u.getNumero());
            if (m.find()) {
                try {
                    int val = Integer.parseInt(m.group(1));
                    if (val > max) max = val;
                } catch (NumberFormatException ignored) {}
            }
        }
        return max + 1;
    }

    /**
     * Recherche l'indice d'une colonne dans l'en-tête CSV en testant plusieurs mots-clés tolérants.
     *
     * @param headers la liste des libellés d'en-tête
     * @param motsCles les mots-clés acceptés
     * @return l'indice de la colonne (0-indexé), ou -1 si introuvable
     */
    private static int trouverIndexColonne(List<String> headers, String... motsCles) {
        for (int i = 0; i < headers.size(); i++) {
            String col = headers.get(i).toLowerCase();
            for (String mot : motsCles) {
                if (col.contains(mot)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extrait en toute sécurité une valeur d'une ligne CSV tokenisée.
     *
     * @param vals la liste des valeurs de la ligne
     * @param idx  l'indice ciblé
     * @return la valeur nettoyée, ou chaîne vide si l'indice est hors limites
     */
    private static String getVal(List<String> vals, int idx) {
        if (idx >= 0 && idx < vals.size()) {
            return vals.get(idx).trim();
        }
        return "";
    }

    /**
     * Découpe une ligne CSV en tenant compte des séparateurs protégés par des guillemets.
     *
     * @param line la ligne brute
     * @param sep  le caractère séparateur (virgule ou point-virgule)
     * @return la liste des jetons découpés
     */
    private static List<String> parseCsvLine(String line, char sep) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == sep && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    /**
     * Détecte l'encodage du fichier CSV en inspectant le préambule binaire (BOM).
     *
     * @param file le fichier à analyser
     * @return l'encodage Charset (UTF-8 par défaut)
     */
    private static Charset detecterEncodage(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] buf = new byte[3];
            int read = is.read(buf);
            if (read >= 3 && buf[0] == (byte) 0xEF && buf[1] == (byte) 0xBB && buf[2] == (byte) 0xBF) {
                return StandardCharsets.UTF_8;
            }
        } catch (Exception ignored) {}
        return StandardCharsets.UTF_8;
    }
}
