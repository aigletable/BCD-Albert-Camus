package fr.ecole.bcd.database;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * Gestionnaire centralisé de la connexion et de la persistance SQLite pour l'application BCD Express.
 *
 * <p>Cette classe applique le patron de conception Singleton pour maintenir une connexion
 * JDBC SQLite unique et thread-safe tout au long du cycle de vie de l'application. Elle est
 * responsable de l'amorçage de la base de données, de l'activation des contraintes relationnelles,
 * des optimisations de performances (mode WAL, synchronous normal), ainsi que des migrations
 * de schéma incrémentales.</p>
 *
 * <h3>Responsabilités principales :</h3>
 * <ul>
 *   <li>Fournir une instance de connexion JDBC unique via {@link #getConnection()}.</li>
 *   <li>Exécuter systématiquement {@code PRAGMA foreign_keys = ON} pour garantir l'intégrité référentielle.</li>
 *   <li>Configurer le moteur SQLite en mode WAL (Write-Ahead Logging) pour des lectures/écritures concurrentes fluides.</li>
 *   <li>Charger et exécuter le schéma initial {@code /sql/schema.sql} de manière idempotente.</li>
 *   <li>Gérer les montées de version du schéma (colonnes additionnelles) sans perte de données.</li>
 *   <li>Permettre le basculement dynamique de l'URL JDBC pour les tests d'intégration et simulations.</li>
 *   <li>Clôturer proprement les ressources lors de l'arrêt de JavaFX.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public final class DatabaseManager {

    // ── Configuration ────────────────────────────────────────────────────
    /**
     * URL JDBC par défaut pointant vers le fichier de base de données {@code bcd.db}
     * situé à la racine du répertoire d'exécution de l'application.
     */
    public static final String DEFAULT_DB_URL = "jdbc:sqlite:bcd.db";

    /**
     * URL JDBC active actuellement utilisée pour ouvrir de nouvelles connexions.
     * Modifiable dynamiquement par {@link #setDbUrl(String)}.
     */
    private static String dbUrl = DEFAULT_DB_URL;

    /**
     * Emplacement du fichier de schéma SQL au sein du classpath des ressources de l'application.
     */
    private static final String SCHEMA_RESOURCE = "/sql/schema.sql";

    // ── État interne ─────────────────────────────────────────────────────
    /**
     * Instance unique de la connexion JDBC SQLite active (Singleton).
     */
    private static Connection connection;

    /**
     * Constructeur privé pour empêcher l'instanciation directe de cette classe utilitaire statique.
     */
    private DatabaseManager() { }

    /**
     * Définit l'URL JDBC de la base de données SQLite.
     * <p>
     * Cette méthode est particulièrement utile pour les tests d'intégration,
     * les scénarios d'import/export ou les environnements de simulation isolés.
     * Si une connexion existait sous l'ancienne URL, elle est automatiquement fermée.
     * </p>
     *
     * @param newUrl la nouvelle chaîne de connexion JDBC (ex. {@code "jdbc:sqlite::memory:"}),
     *               ou {@code null}/vide pour restaurer l'URL par défaut.
     */
    public static synchronized void setDbUrl(String newUrl) {
        closeConnection();
        dbUrl = (newUrl != null && !newUrl.isBlank()) ? newUrl : DEFAULT_DB_URL;
    }

    /**
     * Récupère l'URL JDBC SQLite actuellement configurée.
     *
     * @return la chaîne d'URL JDBC active.
     */
    public static synchronized String getDbUrl() {
        return dbUrl;
    }

    /**
     * Réinitialise l'URL JDBC de la base de données à sa valeur nominale par défaut ({@value #DEFAULT_DB_URL}).
     * Ferme la connexion active le cas échéant.
     */
    public static synchronized void resetDbUrl() {
        setDbUrl(DEFAULT_DB_URL);
    }

    // =====================================================================
    //  Connexion
    // =====================================================================

    /**
     * Retourne la connexion SQLite active, ou en ouvre une nouvelle.
     * <p>
     * Le {@code PRAGMA foreign_keys = ON} est exécuté à chaque ouverture
     * car il doit être réactivé <em>par connexion</em> dans SQLite.
     * </p>
     *
     * @return connexion JDBC prête à l'emploi
     * @throws SQLException en cas d'erreur de connexion
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(dbUrl);

            // Active les clés étrangères et le mode WAL pour la réactivité
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON;");
                stmt.execute("PRAGMA journal_mode = WAL;");
                stmt.execute("PRAGMA synchronous = NORMAL;");
            }

            System.out.println("🔗 Connexion SQLite ouverte : " + dbUrl);
        }
        return connection;
    }

    // =====================================================================
    //  Initialisation du schéma
    // =====================================================================

    /**
     * Lit {@code schema.sql} depuis les ressources et exécute chaque
     * instruction SQL pour créer les tables et les index.
     * <p>
     * Le script est idempotent ({@code CREATE … IF NOT EXISTS}) : il peut
     * être exécuté à chaque démarrage sans risque de perte de données.
     * </p>
     *
     * @throws RuntimeException si le fichier SQL est introuvable
     *                          ou si une erreur SQL survient
     */
    public static void initializeDatabase() {
        System.out.println("📦 Initialisation de la base de données…");

        // 1. Lire le fichier SQL embarqué
        String schemaSql = loadResourceAsString(SCHEMA_RESOURCE);

        // 2. Retirer les commentaires SQL (lignes commençant par --)
        //    puis exécuter chaque instruction séparée par ";"
        try (Statement stmt = getConnection().createStatement()) {

            // Supprimer les lignes de commentaires purs
            StringBuilder cleaned = new StringBuilder();
            for (String line : schemaSql.split("\n")) {
                String trimmedLine = line.trim();
                if (!trimmedLine.startsWith("--")) {
                    // Retirer aussi les commentaires en fin de ligne
                    int commentIdx = line.indexOf("--");
                    if (commentIdx >= 0) {
                        cleaned.append(line, 0, commentIdx);
                    } else {
                        cleaned.append(line);
                    }
                    cleaned.append("\n");
                }
            }

            // Séparer et exécuter les instructions
            String[] statements = cleaned.toString().split(";");
            int count = 0;

            for (String sql : statements) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed + ";");
                    count++;
                }
            }

            // Migration schéma incrémentale rétrocompatible
            try {
                stmt.execute("ALTER TABLE exemplaires ADD COLUMN remarque TEXT DEFAULT '';");
            } catch (SQLException ignored) {
                // La colonne existe déjà
            }

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN genre TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN theme TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN niveau TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN description TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN dewey TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN page_count INTEGER DEFAULT 0;");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN sujets_api TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE livres ADD COLUMN age_api TEXT DEFAULT '';");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("CREATE TABLE IF NOT EXISTS ressources_pedagogiques ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                        + "isbn TEXT DEFAULT NULL, "
                        + "mallette_id INTEGER DEFAULT NULL, "
                        + "titre TEXT NOT NULL, "
                        + "type TEXT NOT NULL CHECK (type IN ('LIEN_WEB', 'PDF', 'DOCUMENT', 'AUTRE')), "
                        + "url_ou_chemin TEXT NOT NULL, "
                        + "description TEXT DEFAULT '', "
                        + "date_ajout TEXT NOT NULL DEFAULT (DATE('now')), "
                        + "FOREIGN KEY (isbn) REFERENCES livres(isbn) ON DELETE CASCADE, "
                        + "FOREIGN KEY (mallette_id) REFERENCES mallettes(id) ON DELETE CASCADE);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ressources_isbn ON ressources_pedagogiques(isbn);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ressources_mallette ON ressources_pedagogiques(mallette_id);");
            } catch (SQLException ignored) {}

            // Nettoyage automatique de cohérence : réconcilier les remarques orphelines de mallettes
            try {
                stmt.execute("UPDATE exemplaires SET remarque = '' "
                        + "WHERE (remarque LIKE 'Mallette%' OR remarque LIKE '%Mallette%') "
                        + "  AND id NOT IN (SELECT exemplaire_id FROM mallette_items);");
            } catch (SQLException ignored) {}

            System.out.println("✅ Base de données initialisée (" + count + " instructions exécutées).");

        } catch (SQLException e) {
            throw new RuntimeException("❌ Erreur lors de l'initialisation du schéma SQL.", e);
        }
    }

    // =====================================================================
    //  Fermeture
    // =====================================================================

    /**
     * Ferme proprement la connexion SQLite si elle est ouverte.
     * Appelée depuis {@code MainApp.stop()} à l'arrêt de l'application.
     */
    public static synchronized void closeConnection() {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                    System.out.println("🔒 Connexion SQLite fermée.");
                }
            } catch (SQLException e) {
                System.err.println("⚠️ Erreur lors de la fermeture de la connexion : " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    // =====================================================================
    //  Utilitaire privé
    // =====================================================================

    /**
     * Charge un fichier texte depuis les ressources du classpath.
     *
     * @param resourcePath chemin absolu dans les ressources (ex. "/sql/schema.sql")
     * @return le contenu du fichier sous forme de String
     * @throws RuntimeException si le fichier est introuvable
     */
    private static String loadResourceAsString(String resourcePath) {
        try (InputStream is = DatabaseManager.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new RuntimeException(
                        "❌ Ressource introuvable : " + resourcePath
                        + " — vérifiez que le fichier est bien dans src/main/resources.");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("❌ Impossible de lire la ressource : " + resourcePath, e);
        }
    }
}
