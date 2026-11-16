-- =============================================================================
-- BCD Express – Schéma de la base de données SQLite
-- =============================================================================
-- Ce script est exécuté au démarrage de l'application par DatabaseManager.
-- Toutes les instructions utilisent IF NOT EXISTS pour être idempotentes
-- (relancer le script n'écrase pas les données existantes).
-- =============================================================================

-- Active le support des clés étrangères (indispensable pour SQLite)
PRAGMA foreign_keys = ON;

-- =============================================================================
-- Table : utilisateurs
-- Élèves (E-101, E-102…) et enseignants (P-01, P-02…)
-- =============================================================================
CREATE TABLE IF NOT EXISTS utilisateurs (
    numero       TEXT PRIMARY KEY,                              -- ex. E-101, P-01
    nom          TEXT NOT NULL,
    prenom       TEXT NOT NULL,
    type_compte  TEXT NOT NULL CHECK (type_compte IN ('ELEVE', 'ENSEIGNANT')),
    classe       TEXT NOT NULL                                  -- ex. CM1-A, Direction
);

-- =============================================================================
-- Table : livres
-- Fiche bibliographique identifiée par ISBN (données Google Books)
-- =============================================================================
CREATE TABLE IF NOT EXISTS livres (
    isbn           TEXT PRIMARY KEY,                            -- ISBN-10 ou ISBN-13
    titre          TEXT NOT NULL,
    auteur         TEXT,                                        -- peut être inconnu
    editeur        TEXT,
    couverture_url TEXT,                                        -- URL image Google Books / OpenLibrary
    genre          TEXT DEFAULT '',                             -- BD, Album, Roman, Documentaire, Conte...
    theme          TEXT DEFAULT '',                             -- Guerre & Histoire, Animaux, Aventure...
    niveau         TEXT DEFAULT '',                             -- Maternelle, CP, CE1, CE2, CM1, CM2...
    description    TEXT DEFAULT ''                              -- Résumé / synopsis
);

-- =============================================================================
-- Table : exemplaires
-- Chaque copie physique d'un livre, identifiée par un numéro incrémental
-- unique (noté manuellement sur la première page du livre).
-- =============================================================================
CREATE TABLE IF NOT EXISTS exemplaires (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,               -- ID physique noté dans le livre
    isbn       TEXT    NOT NULL,
    statut     TEXT    NOT NULL DEFAULT 'DISPONIBLE'
                       CHECK (statut IN ('DISPONIBLE', 'EMPRUNTE')),
    date_ajout TEXT    NOT NULL DEFAULT (DATE('now')),           -- ISO 8601 : YYYY-MM-DD
    remarque   TEXT    DEFAULT '',

    FOREIGN KEY (isbn) REFERENCES livres(isbn) ON DELETE CASCADE
);

-- =============================================================================
-- Table : emprunts
-- Historique complet des prêts. Un emprunt en cours a date_rendu = NULL.
-- =============================================================================
CREATE TABLE IF NOT EXISTS emprunts (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    utilisateur_numero  TEXT    NOT NULL,
    exemplaire_id       INTEGER NOT NULL,
    date_emprunt        TEXT    NOT NULL DEFAULT (DATE('now')),  -- ISO 8601
    date_limite         TEXT    NOT NULL,                        -- date_emprunt + 7 jours
    date_rendu          TEXT    DEFAULT NULL,                    -- NULL = en cours
    est_renouvele       INTEGER NOT NULL DEFAULT 0
                                CHECK (est_renouvele IN (0, 1)),

    FOREIGN KEY (utilisateur_numero) REFERENCES utilisateurs(numero) ON DELETE RESTRICT,
    FOREIGN KEY (exemplaire_id)      REFERENCES exemplaires(id)      ON DELETE RESTRICT
);

-- =============================================================================
-- Index pour optimiser les requêtes fréquentes
-- =============================================================================

-- Recherche d'exemplaires par ISBN (ex. « combien de copies de ce livre ? »)
CREATE INDEX IF NOT EXISTS idx_exemplaires_isbn
    ON exemplaires(isbn);

-- Emprunts en cours (index partiel : ne stocke que les lignes non rendues)
-- Utilisé par : retards, vérification de disponibilité, tableau de bord
CREATE INDEX IF NOT EXISTS idx_emprunts_en_cours
    ON emprunts(exemplaire_id, date_limite)
    WHERE date_rendu IS NULL;

-- Emprunts par utilisateur (« cet élève a-t-il un retard ? »)
-- Index composite couvrant la requête de blocage avant nouvel emprunt
CREATE INDEX IF NOT EXISTS idx_emprunts_utilisateur_actifs
    ON emprunts(utilisateur_numero, date_rendu);

-- Recherche d'emprunts par exemplaire (« qui a emprunté l'exemplaire n°42 ? »)
CREATE INDEX IF NOT EXISTS idx_emprunts_exemplaire
    ON emprunts(exemplaire_id);

-- =============================================================================
-- Tables : Campagnes d'inventaire & Qualification des écarts (Gel de stock)
-- =============================================================================
CREATE TABLE IF NOT EXISTS sessions_inventaire (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    date_debut       TEXT NOT NULL,
    date_fin         TEXT DEFAULT NULL,
    perimetre_type   TEXT NOT NULL,
    perimetre_valeur TEXT DEFAULT '',
    statut           TEXT NOT NULL DEFAULT 'ACTIVE',
    total_attendus   INTEGER DEFAULT 0,
    total_presents   INTEGER DEFAULT 0,
    total_prets      INTEGER DEFAULT 0,
    total_manquants  INTEGER DEFAULT 0,
    total_surplus    INTEGER DEFAULT 0,
    commentaire      TEXT DEFAULT ''
);

CREATE TABLE IF NOT EXISTS inventaire_lignes (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id       INTEGER NOT NULL,
    exemplaire_id    INTEGER NOT NULL,
    statut_pointage  TEXT NOT NULL DEFAULT 'MANQUANT',
    motif_ecart      TEXT DEFAULT 'AUCUN',
    commentaire      TEXT DEFAULT '',
    date_scan        TEXT DEFAULT NULL,
    FOREIGN KEY (session_id) REFERENCES sessions_inventaire(id) ON DELETE CASCADE
);

-- =============================================================================
-- Tables : Mallettes Pédagogiques & Thématiques
-- =============================================================================
CREATE TABLE IF NOT EXISTS mallettes (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    code                TEXT UNIQUE NOT NULL,                        -- ex: MAL-001
    titre               TEXT NOT NULL,                               -- ex: Mallette Harcèlement & Vivre ensemble
    theme               TEXT NOT NULL,                               -- ex: Harcèlement & Vivre ensemble
    niveau              TEXT DEFAULT '',                             -- ex: Cycle 3 (CM1-CM2)
    description         TEXT DEFAULT '',                             -- Objectifs / notes
    statut              TEXT NOT NULL DEFAULT 'DISPONIBLE'
                        CHECK (statut IN ('DISPONIBLE', 'EMPRUNTE', 'ARCHIVEE')),
    date_creation       TEXT NOT NULL DEFAULT (DATE('now')),
    emprunteur_numero   TEXT DEFAULT NULL,
    emprunteur_nom      TEXT DEFAULT '',
    emprunteur_classe   TEXT DEFAULT '',
    date_emprunt        TEXT DEFAULT NULL,
    date_retour_prevue  TEXT DEFAULT NULL,
    FOREIGN KEY (emprunteur_numero) REFERENCES utilisateurs(numero) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS mallette_items (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    mallette_id    INTEGER NOT NULL,
    exemplaire_id  INTEGER NOT NULL,
    date_ajout     TEXT NOT NULL DEFAULT (DATE('now')),
    FOREIGN KEY (mallette_id) REFERENCES mallettes(id) ON DELETE CASCADE,
    FOREIGN KEY (exemplaire_id) REFERENCES exemplaires(id) ON DELETE CASCADE,
    UNIQUE (mallette_id, exemplaire_id)
);

CREATE INDEX IF NOT EXISTS idx_mallette_items_mallette
    ON mallette_items(mallette_id);
CREATE INDEX IF NOT EXISTS idx_mallette_items_exemplaire
    ON mallette_items(exemplaire_id);

-- =============================================================================
-- Table : Boîte à vérifier (Contrôle des doublons au scan & anomalies de stock)
-- =============================================================================
CREATE TABLE IF NOT EXISTS boite_verification (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    isbn             TEXT NOT NULL,
    titre            TEXT DEFAULT '',
    auteur           TEXT DEFAULT '',
    date_scan        TEXT NOT NULL,
    motif            TEXT DEFAULT '',
    nb_exemplaires   INTEGER DEFAULT 0,
    statut           TEXT NOT NULL DEFAULT 'A_VERIFIER'
);

CREATE INDEX IF NOT EXISTS idx_boite_verif_statut
    ON boite_verification(statut);

-- =============================================================================
-- Table : ressources_pedagogiques
-- Ressources pédagogiques associées à un livre (ISBN) et/ou à une mallette
-- (fiches d'exploitation, liens Éduscol, rallye-lecture, questionnaires PDF...)
-- =============================================================================
CREATE TABLE IF NOT EXISTS ressources_pedagogiques (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    isbn             TEXT DEFAULT NULL,
    mallette_id      INTEGER DEFAULT NULL,
    titre            TEXT NOT NULL,
    type             TEXT NOT NULL CHECK (type IN ('LIEN_WEB', 'PDF', 'DOCUMENT', 'AUTRE')),
    url_ou_chemin    TEXT NOT NULL,
    description      TEXT DEFAULT '',
    date_ajout       TEXT NOT NULL DEFAULT (DATE('now')),
    FOREIGN KEY (isbn) REFERENCES livres(isbn) ON DELETE CASCADE,
    FOREIGN KEY (mallette_id) REFERENCES mallettes(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ressources_isbn
    ON ressources_pedagogiques(isbn);
CREATE INDEX IF NOT EXISTS idx_ressources_mallette
    ON ressources_pedagogiques(mallette_id);


