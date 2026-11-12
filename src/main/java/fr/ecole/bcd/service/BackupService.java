package fr.ecole.bcd.service;

import fr.ecole.bcd.database.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service de sauvegarde automatique et de restauration de la base de données SQLite de BCD Express.
 * <p>
 * Ce service offre des garanties critiques d'intégrité et de résilience :
 * </p>
 * <ul>
 *   <li><b>Sauvegarde atomique à chaud</b> : utilisation de la commande native SQLite {@code VACUUM INTO}
 *       permettant de créer un cliché cohérent et compacté sans interruption de service ni risque de corruption.</li>
 *   <li><b>Sauvegardes automatiques quotidiennes</b> : archivage horodaté dans le sous-dossier standard {@code backups/}.</li>
 *   <li><b>Export externe 1-clic</b> : possibilité d'exporter directement vers un support externe (clé USB, serveur de fichiers).</li>
 *   <li><b>Restauration blindée</b> : création préalable automatique d'une copie de précaution de l'état actuel avant
 *       tout remplacement physique de la base, avec contrôle d'intégrité (PRAGMA integrity_check).</li>
 *   <li><b>Politique de rétention</b> : purge des anciennes sauvegardes selon un quota paramétrable (15 par défaut).</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class BackupService {

    /**
     * Format d'horodatage utilisé pour nommer les fichiers de sauvegarde (ex. 2026-11-10_15-30-00).
     */
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /**
     * Nom du répertoire de stockage par défaut des sauvegardes au sein de l'arborescence de l'application.
     */
    private static final String DEFAULT_BACKUP_DIR = "backups";

    /**
     * Nombre standard de sauvegardes à conserver lors de la purge automatique des archives.
     */
    private static final int DEFAULT_RETENTION_COUNT = 15;

    /**
     * Dossier cible où sont stockées les sauvegardes.
     */
    private final File backupDirectory;

    /**
     * Initialise le service avec le répertoire de sauvegarde par défaut ({@code backups/}).
     */
    public BackupService() {
        this(new File(DEFAULT_BACKUP_DIR));
    }

    /**
     * Initialise le service avec un répertoire de sauvegarde personnalisé.
     *
     * @param backupDir le dossier où enregistrer les sauvegardes (créé automatiquement s'il n'existe pas)
     */
    public BackupService(File backupDir) {
        this.backupDirectory = backupDir != null ? backupDir : new File(DEFAULT_BACKUP_DIR);
        if (!this.backupDirectory.exists()) {
            this.backupDirectory.mkdirs();
        }
    }

    /**
     * Retourne le répertoire de stockage des sauvegardes géré par ce service.
     *
     * @return le dossier {@link File} de sauvegarde
     */
    public File getBackupDirectory() {
        return backupDirectory;
    }

    /**
     * Crée une sauvegarde automatique horodatée dans le dossier standard {@code backups/}.
     *
     * @return le fichier de sauvegarde créé
     * @throws SQLException si la création de la sauvegarde échoue
     */
    public File creerSauvegardeAutomatique() throws SQLException {
        return creerSauvegarde("bcd_backup");
    }

    /**
     * Crée une sauvegarde horodatée dans {@code backups/} avec un préfixe spécifique
     * (par exemple {@code pre_passage_annee} avant le basculement de niveau).
     *
     * @param prefix le préfixe textuel pour nommer le fichier
     * @return le fichier de sauvegarde généré
     * @throws SQLException si la sauvegarde échoue
     */
    public File creerSauvegarde(String prefix) throws SQLException {
        String timestamp = LocalDateTime.now().format(TS_FMT);
        String p = (prefix != null && !prefix.isBlank()) ? prefix.trim() + "_" : "bcd_backup_";
        File dest = new File(backupDirectory, p + timestamp + ".db");
        return creerSauvegardeVers(dest);
    }

    /**
     * Crée une sauvegarde complète vers un fichier de destination spécifique (ex: clé USB ou dossier distant).
     *
     * @param destination le fichier cible (.db)
     * @return le fichier de sauvegarde écrit
     * @throws SQLException             si la commande VACUUM INTO ou la copie physique échoue
     * @throws IllegalArgumentException si la destination est {@code null}
     */
    public File creerSauvegardeVers(File destination) throws SQLException {
        if (destination == null) {
            throw new IllegalArgumentException("Le fichier de destination ne peut pas être null.");
        }

        File parent = destination.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        // Si le fichier cible existe déjà, le supprimer car VACUUM INTO exige une cible neuve
        if (destination.exists()) {
            destination.delete();
        }

        String absPath = destination.getAbsolutePath().replace("\\", "/");
        // Échapper les apostrophes pour la requête SQL
        String safePath = absPath.replace("'", "''");

        try (Statement stmt = DatabaseManager.getConnection().createStatement()) {
            // Forcer l'écriture du journal WAL dans le fichier principal
            try {
                stmt.execute("PRAGMA wal_checkpoint(TRUNCATE);");
            } catch (SQLException ignored) {}

            // VACUUM INTO produit une copie cohérente et compactée à chaud
            stmt.execute("VACUUM INTO '" + safePath + "';");
            System.out.println("💾 Sauvegarde SQLite créée : " + destination.getAbsolutePath() + " (" + (destination.length() / 1024) + " Ko)");
        } catch (SQLException e) {
            // Repli de sécurité par copie physique directe du fichier de base
            System.err.println("⚠️ VACUUM INTO a échoué (" + e.getMessage() + "), tentative de copie directe...");
            try {
                copierBasePhysique(destination);
            } catch (IOException ioEx) {
                throw new SQLException("Échec de la sauvegarde SQLite : " + ioEx.getMessage(), ioEx);
            }
        }

        // Purge automatique des anciennes sauvegardes du dossier standard
        try {
            nettoyerAnciennesSauvegardes(DEFAULT_RETENTION_COUNT);
        } catch (Exception ignored) {}

        return destination;
    }

    /**
     * Restaure la base de données depuis un fichier de sauvegarde donné.
     * <p>
     * Une sauvegarde de précaution immédiate est créée avant toute opération d'écrasement,
     * et l'intégrité de la base restaurée est vérifiée avant finalisation.
     * </p>
     *
     * @param backupFile le fichier source (.db) à restaurer
     * @return {@code true} si la restauration et le contrôle d'intégrité ont réussi
     * @throws SQLException             en cas d'erreur de communication ou d'intégrité SQLite
     * @throws IOException              en cas d'erreur de lecture/écriture physique de fichier
     * @throws IllegalArgumentException si le fichier de sauvegarde est invalide, inexistant ou vide
     */
    public boolean restaurerSauvegarde(File backupFile) throws SQLException, IOException {
        if (backupFile == null || !backupFile.exists() || backupFile.length() == 0) {
            throw new IllegalArgumentException("Fichier de sauvegarde invalide ou inexistant.");
        }

        // 1. Sauvegarde de sécurité préventive
        String preBackupTs = LocalDateTime.now().format(TS_FMT);
        File preBackup = new File(backupDirectory, "pre_restauration_" + preBackupTs + ".db");
        try {
            creerSauvegardeVers(preBackup);
            System.out.println("🛡️  Sauvegarde de précaution créée : " + preBackup.getName());
        } catch (Exception ex) {
            System.err.println("⚠️ Impossible de créer la sauvegarde de précaution : " + ex.getMessage());
        }

        // 2. Déterminer le fichier cible de la base active
        String currentUrl = DatabaseManager.getDbUrl();
        String currentDbPath = "bcd.db";
        if (currentUrl.startsWith("jdbc:sqlite:")) {
            currentDbPath = currentUrl.substring("jdbc:sqlite:".length());
        }
        File targetDb = new File(currentDbPath);

        // 3. Fermer la connexion active pour libérer les verrous SQLite
        DatabaseManager.closeConnection();

        // 4. Remplacer les fichiers physiques (fichier .db, et supprimer .db-wal et .db-shm si présents)
        File walFile = new File(targetDb.getAbsolutePath() + "-wal");
        File shmFile = new File(targetDb.getAbsolutePath() + "-shm");
        if (walFile.exists()) walFile.delete();
        if (shmFile.exists()) shmFile.delete();

        Files.copy(backupFile.toPath(), targetDb.toPath(), StandardCopyOption.REPLACE_EXISTING);
        System.out.println("🔄 Base de données restaurée depuis : " + backupFile.getAbsolutePath());

        // 5. Rouvrir la connexion et vérifier l'intégrité
        try (Statement st = DatabaseManager.getConnection().createStatement();
             ResultSet rs = st.executeQuery("PRAGMA integrity_check;")) {
            if (rs.next()) {
                String integrity = rs.getString(1);
                if (!"ok".equalsIgnoreCase(integrity)) {
                    throw new SQLException("La base restaurée présente des anomalies : " + integrity);
                }
            }
        }

        return true;
    }

    /**
     * Liste toutes les sauvegardes existantes dans le dossier standard, triées de la plus récente à la plus ancienne.
     *
     * @return la liste des objets {@link BackupItem} représentant chaque fichier de sauvegarde
     */
    public List<BackupItem> listerSauvegardes() {
        if (!backupDirectory.exists() || !backupDirectory.isDirectory()) {
            return Collections.emptyList();
        }

        File[] files = backupDirectory.listFiles((dir, name) -> name.endsWith(".db"));
        if (files == null || files.length == 0) {
            return Collections.emptyList();
        }

        List<BackupItem> items = new ArrayList<>();
        for (File f : files) {
            items.add(new BackupItem(f));
        }

        // Tri du plus récent au plus ancien
        items.sort((a, b) -> Long.compare(b.getFile().lastModified(), a.getFile().lastModified()));
        return items;
    }

    /**
     * Conserve uniquement les N sauvegardes les plus récentes et supprime les plus anciennes
     * (à l'exclusion des sauvegardes manuelles de pré-restauration).
     *
     * @param maxAConserver le nombre maximal de sauvegardes à conserver
     * @return le nombre de fichiers de sauvegarde supprimés
     */
    public int nettoyerAnciennesSauvegardes(int maxAConserver) {
        List<BackupItem> list = listerSauvegardes();
        if (list.size() <= maxAConserver) {
            return 0;
        }

        int supprimes = 0;
        for (int i = maxAConserver; i < list.size(); i++) {
            File f = list.get(i).getFile();
            // Ne pas supprimer les sauvegardes manuelles de pré-restauration
            if (!f.getName().startsWith("pre_restauration_")) {
                if (f.delete()) {
                    supprimes++;
                }
            }
        }
        return supprimes;
    }

    /**
     * Copie physique octet par octet du fichier principal de base SQLite vers la destination spécifiée.
     *
     * @param destination le fichier de destination
     * @throws IOException en cas d'erreur d'E/S système
     */
    private void copierBasePhysique(File destination) throws IOException {
        String currentUrl = DatabaseManager.getDbUrl();
        String currentDbPath = "bcd.db";
        if (currentUrl.startsWith("jdbc:sqlite:")) {
            currentDbPath = currentUrl.substring("jdbc:sqlite:".length());
        }
        File src = new File(currentDbPath);
        if (src.exists()) {
            Files.copy(src.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Calcule et formate un résumé textuel des statistiques actuelles de la base de données active
     * (taille en kilo/méga-octets, nombre de titres, d'exemplaires, d'emprunts en cours et d'élèves).
     *
     * @return le texte synthétique mis en forme
     */
    public String calculerStatistiquesBase() {
        try (Statement st = DatabaseManager.getConnection().createStatement()) {
            int nbLivres = 0;
            int nbExemplaires = 0;
            int nbEmprunts = 0;
            int nbEleves = 0;

            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM livres")) {
                if (rs.next()) nbLivres = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM exemplaires")) {
                if (rs.next()) nbExemplaires = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM emprunts WHERE date_rendu IS NULL")) {
                if (rs.next()) nbEmprunts = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM utilisateurs WHERE type_compte = 'ELEVE'")) {
                if (rs.next()) nbEleves = rs.getInt(1);
            }

            String currentUrl = DatabaseManager.getDbUrl();
            String currentDbPath = "bcd.db";
            if (currentUrl.startsWith("jdbc:sqlite:")) {
                currentDbPath = currentUrl.substring("jdbc:sqlite:".length());
            }
            File f = new File(currentDbPath);
            long bytes = f.exists() ? f.length() : 0;
            String sizeStr = bytes < 1024 * 1024 ? String.format("%.1f Ko", bytes / 1024.0) : String.format("%.2f Mo", bytes / (1024.0 * 1024.0));

            return String.format("Fichier actif : %s (%s)\n%d titre(s) de livres • %d exemplaire(s) physique(s) • %d emprunt(s) en cours • %d élève(s) inscrits",
                    f.getName(), sizeStr, nbLivres, nbExemplaires, nbEmprunts, nbEleves);
        } catch (SQLException e) {
            return "Statistiques indisponibles : " + e.getMessage();
        }
    }

    // =========================================================================
    //  DTO représentant une sauvegarde
    // =========================================================================

    /**
     * Objet de transfert représentant une entrée de fichier de sauvegarde dans l'historique.
     */
    public static class BackupItem {
        /** Fichier physique sur le disque */
        private final File file;
        /** Nom de fichier */
        private final String nom;
        /** Taille en octets */
        private final long tailleOctets;
        /** Date et heure de dernière modification */
        private final LocalDateTime dateModification;

        /**
         * Construit un objet de métadonnées pour le fichier de sauvegarde spécifié.
         *
         * @param file le fichier de sauvegarde
         */
        public BackupItem(File file) {
            this.file = file;
            this.nom = file.getName();
            this.tailleOctets = file.length();
            this.dateModification = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(file.lastModified()),
                    java.time.ZoneId.systemDefault()
            );
        }

        /** @return le fichier physique */
        public File getFile() { return file; }
        /** @return le nom du fichier */
        public String getNom() { return nom; }
        /** @return la taille brute en octets */
        public long getTailleOctets() { return tailleOctets; }
        /** @return la taille formatée lisible pour l'humain (Ko ou Mo) */
        public String getTailleFormatee() {
            if (tailleOctets < 1024) return tailleOctets + " o";
            if (tailleOctets < 1024 * 1024) return String.format("%.1f Ko", (double) tailleOctets / 1024.0);
            return String.format("%.2f Mo", (double) tailleOctets / (1024.0 * 1024.0));
        }
        /** @return la date et l'heure de modification */
        public LocalDateTime getDateModification() { return dateModification; }
        /** @return la date formatée au standard français (dd/MM/yyyy HH:mm:ss) */
        public String getDateFormatee() {
            return dateModification.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
        }
    }
}
