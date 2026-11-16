# 📚 BCD Express — Suivi du projet

> **Dernière mise à jour :** 10 novembre 2026  
> **Ce fichier est mis à jour à chaque session de travail.**

---

## 🎯 But du projet

Créer une **application de bureau** pour gérer la **BCD (Bibliothèque Centre Documentaire)** d'une école primaire.

### Objectifs principaux

- **Gérer les livres** : ajouter des livres dans la base en scannant leur ISBN (unitaire ou par lots), récupérer automatiquement les infos (titre, auteur, couverture) via l'API Google Books
- **Gérer les exemplaires** : chaque copie physique d'un livre a un numéro unique noté dans le livre
- **Gérer les emprunts** : prêt, retour, renouvellement, alertes de retard
- **Gérer les utilisateurs** : élèves et enseignants, avec leur classe
- **Zéro serveur** : tout tourne en local avec une base SQLite (`bcd.db`)

### Contexte

- Établissement : **École Albert Camus Pasteur (Meudon)**
- Projet réalisé dans le cadre d'un **Service Civique**
- Base SQLite locale embarquée (`bcd.db`) avec zéro dépendance serveur
- L'appli est conçue pour être **ultra-simple, rapide et ergonomique** pour le personnel de l'école et les bénévoles BCD

---

## 🏗️ Stack technique

| Composant | Techno | Version |
|---|---|---|
| Langage | Java | 21 LTS |
| Interface | JavaFX | 21.0.5 |
| Thème UI | AtlantaFX (Primer Light) | 2.0.1 |
| Base de données | SQLite (JDBC Xerial) | 3.47.1.0 |
| API externe | Google Books (via org.json) | – |
| Build | Maven + shade plugin (fat JAR) | – |

---

## 📂 Architecture

```
fr.ecole.bcd/
├── Launcher.java                      ← Point d'entrée (workaround module-path)
├── MainApp.java                       ← Application JavaFX (init/start/stop)
├── SmokeTest.java                     ← Tests fonctionnels (234 tests ✅)
├── model/
│   ├── Utilisateur.java               ← Élève ou enseignant (enum TypeCompte)
│   ├── Livre.java                     ← Fiche biblio identifiée par ISBN + compteurs
│   ├── Exemplaire.java                ← Copie physique (enum Statut)
│   └── Emprunt.java                   ← Prêt avec dates, retard, renouvellement + données jointes
├── database/
│   ├── DatabaseManager.java           ← Singleton connexion SQLite
│   ├── UtilisateurDao.java            ← CRUD + recherche + filtre par classe
│   ├── LivreDao.java                  ← CRUD + comptages d'exemplaires + recherche
│   ├── ExemplaireDao.java             ← CRUD + gestion statut + comptage
│   └── EmpruntDao.java                ← Créer/retourner/renouveler + requêtes jointes
├── service/
│   ├── EmpruntService.java            ← Règles métier (quotas, retards, renouvellement)
│   ├── GoogleBooksService.java        ← Appel API Google Books par ISBN
│   └── CsvImportService.java          ← Import CSV flexible (élèves / enseignants)
├── controller/
│   ├── PretsController.java           ← Contrôleur onglet Prêts & Retours (Retour rapide, Prêt, Retards, Panier)
│   ├── InventaireController.java      ← Contrôleur catalogue & livres
│   ├── EtiquettesController.java      ← Contrôleur page dédiée Étiquettes (tableau à cocher, qté exemplaires, recherche rapide)
│   ├── UtilisateursController.java    ← Contrôleur utilisateurs & passage d'année scolaire
│   ├── RecolementController.java      ← Contrôleur récolement physique & inventaire
│   └── MallettesController.java       ← Contrôleur mallettes thématiques & séries
└── view/                              ← Composants UI et boîtes de dialogue modulaires
    ├── DialogHelper.java              ← Alertes, avertissements, confirmations et liens web partagés
    ├── BoiteVerificationDialogHelper.java ← Dialogue détection doublons et fiches à 0 exemplaire
    ├── DouchetteDialogHelper.java     ← Dialogue session continue de scan douchette
    ├── EtiquettesDialogHelper.java    ← Générateur modal de planches d'étiquettes A4 & codes-barres
    ├── BackupDialogHelper.java        ← Dialogue sauvegardes SQLite & export clé USB
    └── AnimationHelper.java           ← Retours visuels et micro-animations

resources/
├── css/app.css                        ← Styles personnalisés (thème, badges, fiches, statut)
├── fxml/prets.fxml                    ← Écran Prêts & Retours (Bandeau retour rapide + tableau)
├── fxml/inventaire.fxml               ← Écran Inventaire & Livres (SplitPane catalogue + fiche)
├── fxml/etiquettes.fxml               ← Écran dédié Impression Étiquettes (tableau à cocher + réglages planche A4)
├── fxml/utilisateurs.fxml             ← Écran Utilisateurs (avec bouton import CSV)
└── sql/schema.sql                     ← Schéma de la base de données
```

---

## 📊 Avancement global

```
████████████████████  100%
```

---

## ✅ Ce qui est fait

### Infrastructure & Build
- [x] Structure Maven complète avec `pom.xml`
- [x] Fat JAR autonome opérationnel (22 Mo dans `target/bcd-express-1.0-SNAPSHOT.jar`)
- [x] Lanceur Bootstrap (`Launcher.java`) pour contourner le bug module-path macOS/Win
- [x] Commande `mvn javafx:run` opérationnelle

### Base de données
- [x] `DatabaseManager.java` — connexion singleton SQLite, `PRAGMA foreign_keys = ON`
- [x] `schema.sql` — schéma complet et idempotent (IF NOT EXISTS)
  - Table `utilisateurs` (numéro, nom, prénom, type élève/enseignant, classe)
  - Table `livres` (ISBN, titre, auteur, éditeur, URL couverture)
  - Table `exemplaires` (id physique, ISBN, statut disponible/emprunté, date ajout)
  - Table `emprunts` (utilisateur, exemplaire, dates, renouvellement)
  - 4 index optimisés
- [x] Fichier `bcd.db` généré et fonctionnel

### Modèles (Étape 1 ✅)
- [x] `Utilisateur.java` — numéro, nom, prénom, type (ELEVE/ENSEIGNANT), classe
- [x] `Livre.java` — ISBN, titre, auteur, éditeur, URL couverture
- [x] `Exemplaire.java` — id, ISBN, statut (DISPONIBLE/EMPRUNTE), date ajout
- [x] `Emprunt.java` — id, utilisateur, exemplaire, dates + `isEnCours()`, `isEnRetard()`, `getJoursRetard()`

### DAOs (Étape 2 ✅)
- [x] `UtilisateurDao.java` — CRUD, recherche par nom/prénom/numéro, filtre par classe, liste des classes
- [x] `LivreDao.java` — CRUD avec INSERT OR IGNORE, recherche titre/auteur, comptage
- [x] `ExemplaireDao.java` — CRUD, gestion statut DISPONIBLE↔EMPRUNTE, comptage par ISBN
- [x] `EmpruntDao.java` — créer/retourner/renouveler, emprunts en cours, retards, historique, recherche par ISBN pour douchette

### Services (Étape 3 & Enrichissements ✅)
- [x] `EmpruntService.java` — règles métier complètes :
  - Max 3 emprunts simultanés pour les élèves, 5 pour les enseignants
  - Blocage si l'utilisateur a un retard
  - Vérification que l'exemplaire est disponible
  - Renouvellement unique (une seule fois par emprunt)
  - Gestion automatique du statut exemplaire (DISPONIBLE ↔ EMPRUNTE)
  - **Retour rapide par numéro d'exemplaire ET par scan d'ISBN (douchette)**
  - Exception métier `RegleMetierException` avec messages en français
- [x] `GoogleBooksService.java` — appel API Google Books :
  - Recherche par ISBN (10 ou 13, avec ou sans tirets)
  - Extraction titre, auteur(s), éditeur, couverture
  - Force HTTPS, préfère ISBN-13
  - Timeout 10 secondes
- [x] `CsvImportService.java` — import CSV en masse pour les élèves/utilisateurs :
  - Détection automatique du séparateur (`;` ou `,`) et des en-têtes
  - Support de 3 formats : 3 colonnes (`nom;prenom;classe`), 4 colonnes (`numero;nom;prenom;classe`), 5 colonnes (`numero;nom;prenom;type;classe`)
  - Auto-génération des identifiants élèves (`E-001`, `E-002`…) si non fournis
  - Ignorance des lignes vides et des commentaires (`#`)
  - Rapport détaillé (`ImportResult`) avec liste d'erreurs ligne par ligne
- [x] `LivreMetadataService.java` — **Moteur multi-sources & recherche de vraies couvertures** :
  - Nettoyage intelligent d'ISBN (notation exponentielle Excel, guillemets, normalisation du `X`).
  - Cascade de recherche en 3 niveaux : **BNF** (Dublincore XML) $\rightarrow$ **Google Books** $\rightarrow$ **OpenLibrary**.
  - Filtre anti-faux livres (> 2 Ko) sur CDN Epagine & OpenLibrary.
- [x] `LivreBatchImportService.java` — **Import CSV par lot de livres scannés** :
  - Réapprovisionnement intelligent des exemplaires ou création automatique des nouveaux titres.
  - Notification de progression en temps réel pour l'UI.

### Interface Utilisateurs (Étape 4 ✅)
- [x] `utilisateurs.fxml` — écran complet avec tableau, recherche, boutons CRUD et bouton import CSV
- [x] `UtilisateursController.java` — CRUD complet, recherche temps réel, filtre classe, import CSV.

### Interface Inventaire & Livres (Étape 5 & Mode Douchette ✅)
- [x] `inventaire.fxml` — écran complet en SplitPane moderne :
  - Tableau des livres et fiche détaillée avec couverture dynamique.
  - **Bouton `⚡ Mode Douchette`** : session de scan continu à la douchette avec enrichissement automatique et historique en direct.
  - **Bouton `📥 Importer CSV`** : import par lot d'inventaires scannés avec barre de progression.
  - **Bouton `📤 Exporter CSV`** : export complet du catalogue de l'école (format UTF-8 BOM pour Excel).
  - **Bouton `🔍 Couverture`** : recherche et mise à jour de la couverture vérifiée en 1 clic.
- [x] `InventaireController.java` — contrôleur enrichi avec le mode douchette interactif et l'exportation du catalogue.

### Interface Prêts & Retours (Étape 6 & Export Retards ✅)
- [x] `prets.fxml` — écran de guichet de prêt :
  - **Retour instantané par scan douchette (N° d'exemplaire OU code-barres ISBN au dos du livre)** avec confirmation audio (bip).
  - **Bouton `📤 Exporter retards (CSV)`** : export de la liste des retards pour distribution aux enseignants.
  - Nouvel emprunt assisté avec contrôle des quotas et alertes retards.
  - Prolongation de prêt (+7j) avec renouvellement unique.
- [x] `PretsController.java` — contrôleur avec gestion douchette multi-formats et export CSV des retards.

### Interface Étiquettes & Codes-barres (Page dédiée ✅)
- [x] `etiquettes.fxml` & `EtiquettesController.java` — **Page dédiée complète d'impression d'étiquettes** :
  - **Tableau à cocher interactif** : sélection simple et visuelle des livres à imprimer.
  - **Gestion intelligente des exemplaires multiples** : dialogue automatique demandant si l'on souhaite imprimer tous les exemplaires ou une quantité précise (`[Tous]`, `[1 seul]`, `[Personnaliser...]`), avec Spinner ajustable directement sur la ligne.
  - **Recherche & Ajout ultra-rapide** : saisie du titre ou scan ISBN + touche <kbd>Entrée</kbd> ➔ livre trouvé, coché et validé instantanément (« tape le nom, valide et ça part »).
  - **Génération directe A4** : choix du format de planche (Standard 24, Avery 21, Compact 30, Grand 14, Tranches 65), gestion de l'offset anti-gaspillage, en-tête d'école personnalisable et aperçu temps réel.
  - **Bouton d'impression en 1 clic** : ouvre la planche haute précision prête à imprimer dans le navigateur par défaut.


### Tests
- [x] `SmokeTest.java` — **266 tests fonctionnels, 100% passés** ✅
- [x] Fat JAR de 22 Mo compilé et validé fonctionnellement.

---

## 📝 Notes & Décisions

| Date | Note |
|---|---|
| 15/10/2026 | **Création du suivi.** Fondations solides (Maven, BDD, schéma, squelette UI). |
| 15/10/2026 | **Étapes 1 à 6 terminées.** Tous les modèles, DAO, services métier et les 3 écrans principaux (Utilisateurs, Inventaire, Prêts & Retours) sont opérationnels. |
| 15/10/2026 | **Intégration du script Python** : Implémentation du moteur multi-sources en cascade (BNF / Google / OpenLibrary / Epagine) avec filtre anti-faux livres > 2 Ko et import CSV par lot avec quantités. |
| 15/10/2026 | **Mode Douchette & Exports ajoutés** : <br>1. **Mode Douchette d'inventaire** : session interactive de scan continu à la chaîne pour intégrer les livres en stock sans toucher la souris.<br>2. **Scan douchette pour les retours** : accepte indifféremment le N° d'exemplaire OU le code-barres ISBN au dos du livre avec bip sonore.<br>3. **Export CSV des retards** : fichier prêt pour Excel avec nom, classe, titre, jours de retard pour donner aux maîtresses.<br>4. **Export CSV du catalogue** : inventaire complet en tableur.<br>5. **71 tests passés ✅**, Fat JAR prêt à l'emploi. **Application 100% terminée.** |
| 16/10/2026 | **Recherche d'emprunteur rapide & fluide** : Remplacement de la liste déroulante par une recherche instantanée par nom/prénom/classe insensible aux accents avec sélection au clavier et support du scan direct de code-barres ISBN au prêt. |
| 16/10/2026 | **Mode Douchette enrichi (Suppression, Modification à la volée, Signalement d'état)** :<br>1. **Annulation instantanée** du dernier scan via le bouton ou le raccourci `Ctrl+Z` / `Cmd+Z`.<br>2. **Actions par ligne scannée** : ✏️ Modifier titre/auteur/éditeur, ⚠️ Signaler l'état (Bon état, À réparer, Mauvais état, Titre à vérifier, Note libre), 🗑️ Supprimer l'exemplaire.<br>3. **Gestion d'état pérenne** : Colonne `remarque` stockée en BDD et visible avec badges dans la fiche détaillée de l'inventaire. |
| 16/10/2026 | **Correction & Modification de l'ISBN** :<br>1. **En Mode Douchette (bouton ✏️)** : modification du code-barres ISBN avec bouton `🔍 Rechercher infos` pour interroger automatiquement la BNF/Google/OpenLibrary sur le bon ISBN et auto-compléter le titre/auteur.<br>2. **Dans l'Inventaire principal (bouton ✏️ Modifier)** : champ ISBN désormais déverrouillé avec réattribution automatique et transactionnelle de tous les exemplaires physiques rattachés et suppression de l'ancienne fiche vide. |
| 16/10/2026 | **Module Récolement & Inventaire physique des étagères (Onglet Dédié 📋)** :<br>1. **Session de scan en rayon** : scan direct à la douchette (ISBN ou N° exemplaire) avec bips audio et mise à jour en direct.<br>2. **Tableau de bord KPIs & Jauge live** : décompte instantané des Scannés présents (🟢), En prêt chez les élèves (🟠), et Manquants non vus (🔴).<br>3. **Détection intelligente des prêts en rayon** : si un livre noté emprunté est scanné sur l'étagère, l'app propose d'enregistrer son retour en 1 clic.<br>4. **Filtres instantanés & Déclaration de perte** : affichage ciblé des manquants avec possibilité de marquer "Porté disparu / Perdu".<br>5. **Export du bilan d'inventaire (CSV)** : rapport complet avec statistiques globales et liste détaillée pour les enseignants. |
| 16/10/2026 | **Trilogie de fonctionnalités avancées (Paniers Enseignant, Bilan & Palmarès, Générateur d'Étiquettes)** :<br>1. **🧺 Prêt Enseignant / Séries de classe (Panier)** : scan continu multi-exemplaires à la douchette pour un enseignant avec échéances scolaires (4 semaines, 6 semaines / période, ou date libre) et génération automatique d'une **fiche de prêt A4 imprimable** à glisser dans le bac de livres de la classe.<br>2. **📊 Bilan d'Activité & Palmarès BCD** : tableau de bord officiel avec KPIs globaux, **🏆 Top Livres** (médailles 🥇🥈🥉), **🏫 Palmarès des Classes** (volumes et moyennes par lecteur), **⭐ Top Lecteurs Élèves**, export **tableur Excel CSV (BOM UTF-8)** et **Fiche Bilan imprimable HTML** prête pour le Conseil d'école et la Direction.<br>3. **🏷️ Générateur d'Étiquettes & Codes-barres** : moteur autonome vectoriel **Code 39**, formats A4 (Planche standard 24 étiquettes 70x37mm, Planche tranches 65 étiquettes 38x21mm, Grand format 14 étiquettes), personnalisation de l'en-tête de l'école, filtre par livre sélectionné / ensemble du catalogue / plage d'exemplaires / derniers ajouts, avec prévisualisation en direct et ouverture en 1 clic dans le navigateur prêt à imprimer. |
| 16/10/2026 | **Moteur d'Auto-Classification Sémantique (Genre, Thème & Niveau Scolaire au Scan)** :<br>1. **100% Automatisé au scan ISBN** : dès qu'un livre est scanné à la douchette ou saisi par ISBN, le moteur extrait les notices riches de la BNF (Unimarc CNLJ / RAMEAU / Dewey 676 / Âge 333 / Résumé Electre 330), Google Books et OpenLibrary.<br>2. **Attribution automatique** :<br>&nbsp;&nbsp;• **Genre** : *BD, Album, Roman, Documentaire, Conte, Poésie / Théâtre, Manga, Revue / Magazine, Autre*.<br>&nbsp;&nbsp;• **Thème** : *Guerre & Histoire, Animaux, Nature & Écologie, Aventure & Exploration, Imaginaire & Magie, Vie quotidienne & École, Humour & Bêtises, Sciences & Espace, Contes & Mythes, Arts & Sports, Autre*.<br>&nbsp;&nbsp;• **Niveau Scolaire** : *Maternelle, CP, CE1, CE2, CM1, CM2, Cycles 1, 2, 3, Tous niveaux*.<br>3. **Affichage enrichi dans l'UI** : Badges colorés de Genre, Thème et Niveau dans la fiche détaillée avec encadré pour le synopsis/résumé du livre.<br>4. **Filtrage multicritères instantané** : 3 menus déroulants dédiés (Tous les genres, Tous les thèmes, Tous les niveaux) + bouton de réinitialisation rapide.<br>5. **Outil 1-clic `🪄 Classifier le catalogue`** : batch intelligent capable d'analyser et de classifier automatiquement les livres déjà présents en base.<br>6. **Export CSV complet** : intégration des colonnes `Genre`, `Thème`, `Niveau` et `Description` dans les exports du catalogue pour Excel.<br>7. **119 tests validés avec succès (100%) ✅**. |
| 12/11/2026 | **Mode Douchette fluide pour Revues de Presse (J'aime lire, Astrapi...)** :<br>• Ajout automatique direct d'exemplaires (+1) au scan pour les revues et périodiques jeunesse (code presse 378/379/977 ou genre Revue/Magazine), sans popup d'anti-doublon bloquant.<br>• Permet d'intégrer des dizaines de revues à la chaîne en continu (*Bip... Bip... Bip...*) à pleine vitesse.<br>• 234 tests fonctionnels validés avec 100% de succès ✅. |
| 12/11/2026 | **Refonte Esthétique « Cupertino Apple & Papeterie Douce » (avec porte de sortie garantie)** :<br>1. **Thème Cupertino Moderne (Apple)** : Intégration du thème AtlantaFX Cupertino (macOS / iPadOS) avec coins doux, ombres feutrées et fond épuré.<br>2. **Sélecteur de Design & Porte de sortie immédiate** : Menu `🎨 Design ▾` en haut à droite permettant de basculer en 1 clic entre `🍏 Cupertino Moderne (Apple)` et `🏛️ Primer Classique (Design d'origine)` en temps réel.<br>3. **Couvertures stylisées vectorielles « Apple Books »** : Génération automatique de couvertures 3D avec dégradé thématique, reliure texturée et typographie blanche pour les livres sans couverture web.<br>4. **Empty States Joyeux** : Remplacement des textes gris tristes par des bannières illustrées encourageantes (ex: *« 🎈 Zéro retard ! »*, *« 📚 Aucun livre trouvé »*).<br>5. **Badges Genres & Niveaux Pastel** : Palette douce papeterie (Miel pour BD, Azur pour Romans, Émeraude pour Albums, Lavande pour Contes, Rose pour Revues).<br>6. **Bandeau d'accueil enrichi avec KPIs en direct** : Pastilles `📚 X livres` et `🎒 X emprunts` synchronisées en temps réel avec la base SQLite.<br>7. **Sécurité maximale** : Tag git `v1.0-design-classique` et fichiers `app-classic.css` / `dark-classic.css` préservés intacts. |
| 16/11/2026 | **Signalement Rouge des Livres Inconnus (`Livre ISBN ...`) & Onglet Dédié d'Impression d'Étiquettes** :<br>1. **Alerte visuelle rouge vive** : Tous les livres enregistrés sous un titre générique (`Livre ISBN ...` ou non reconnu) s'affichent immédiatement en rouge gras `#DC2626` avec picto d'alerte `⚠️` et badge d'urgence dans la table, les cartes de la grille, le panneau de détails et le Mode Douchette pour être traités sans délai.<br>2. **Nouvel onglet plein écran `🏷️ Étiquettes`** : Remplacement du dialogue par un espace dédié avec tableau à cases à cocher (`☑️`), recherche instantanée au clavier, saisie rapide avec validation automatique par <kbd>Entrée</kbd>, gestion intelligente des exemplaires multiples (dialogue demandant si on souhaite imprimer 1, tous ou X exemplaires d'un titre avec spinner live), choix des formats (Standard 24, Avery 21, Tranches 65) et gestion de l'offset anti-gaspillage de planches entamées. |
| 16/11/2026 | **Ajout d'Ouvrages sans Code-barres / par Titre & Auteur avec Fiche de Vérification** :<br>1. **Recherche bibliographique multi-sources sans ISBN** : Interrogation en cascade d'OpenLibrary (sans quota bloquant), Google Books et BNF SRU par Titre et Auteur avec extraction automatique de l'ISBN officiel s'il existe et couverture.<br>2. **Choix interactif d'édition** : Si plusieurs éditions sont trouvées en ligne, affichage de cartes sélectionnables avec éditeur, date et source pour choisir l'édition exacte du livre physique.<br>3. **Fiche de vérification complète avant ajout (`LivreVerificationDialogHelper`)** :<br>&nbsp;&nbsp;• Prévisualisation de couverture en direct (image web ou couverture stylisée Apple Books vectorielle).<br>&nbsp;&nbsp;• Champs éditables (Titre, Auteur, Éditeur, Description).<br>&nbsp;&nbsp;• Identifiant / Code BCD : ISBN officiel pré-rempli si trouvé, ou génération automatique d'un code interne garanti sans collision (`CAMUS-XXXX`) pour impression ultérieure d'étiquette.<br>&nbsp;&nbsp;• Classification BCD automatique ou modifiable (Genre, Thème, Niveau scolaire).<br>&nbsp;&nbsp;• Nombre d'exemplaires physiques initiaux via `Spinner` (défaut 1).<br>4. **Intégration transparente dans toute l'application** :<br>&nbsp;&nbsp;• Bouton `📖 Par Titre (sans ISBN)` dans la barre d'outils du catalogue et dans le menu `Plus ▾`.<br>&nbsp;&nbsp;• Bouton `📖 Sans code-barres (Titre / Auteur)` directement dans le **Mode Douchette** permettant d'enregistrer un livre ancien sans quitter la session de scan continu.<br>&nbsp;&nbsp;• Recherche intelligente dans `Ajouter un livre` : si le champ ISBN est laissé vide mais qu'un titre est renseigné, le bouton `🔍 Rechercher` bascule automatiquement sur la recherche par titre !<br>5. **Banc de tests enrichi** : 245 tests unitaires et fonctionnels validés avec 100% de succès (245 ✅ 0 ❌). |
| 16/11/2026 | **Recherche Immédiate & Enrichissement Automatique des Couvertures (Amazon CDN & OpenLibrary HD)** :<br>1. **Couverture instantanée dès l'ajout par Titre/Auteur** : Plus besoin de demander ou chercher la couverture après l'ajout en base. Le moteur `rechercherParTitreAuteur` enrichit désormais immédiatement chaque correspondance avec `findVerifiedCover` (Amazon CDN CloudFront en ~150ms, OpenLibrary HD `-L.jpg` et Epagine).<br>2. **Miniatures visuelles dans les cartes candidates** : Les éditions proposées affichent leur vraie jaquette miniature pour un repérage visuel immédiat.<br>3. **Bouton `🔍 Trouver couverture` et auto-résolution** : Dans la fiche de vérification et dans `Ajouter un livre`, bouton dédié pour récupérer la couverture à la volée, déclenchement en arrière-plan à la saisie, et auto-résolution finale avant insertion SQLite.<br>4. **247 tests validés avec 100% de succès (247 ✅ 0 ❌)**. |
| 16/11/2026 | **Ressources Pédagogiques Clé en Main pour Livres & Mallettes Thématiques** :<br>1. **Architecture & Persistance SQLite** : Création de la table `ressources_pedagogiques` (`id`, `isbn`, `mallette_id`, `titre`, `type`, `url_ou_chemin`, `description`, `date_ajout`) avec clés étrangères et index dédiés, gérant aussi bien les liens web que les fichiers locaux.<br>2. **Types & Formats supportés** :<br>&nbsp;&nbsp;• `🌐 Liens Web` : Éduscol, fiches éditeurs (Gallimard, Milan, etc.), Rallye-lecture, vidéos, blogs pédagogiques.<br>&nbsp;&nbsp;• `📄 Fichiers & Documents locaux` : PDFs d'activités, questionnaires de lecture Word/ODT, corrigés, stockés de manière sécurisée et pérenne dans le sous-dossier `documents_bcd/ressources/`.<br>3. **Ouverture instantanée en 1 clic (`🚀 Ouvrir`)** : Stratégie hybride Java Desktop API + fallbacks natifs (macOS `open`, Windows `start`/`rundll32`, Linux `xdg-open`) ouvrant directement dans le navigateur ou lecteur PDF par défaut.<br>4. **Intégration dans l'onglet Mallettes (`MallettesController` / `mallettes.fxml`)** :<br>&nbsp;&nbsp;• Colonne `Ressources` dans le tableau des livres inclus dans la mallette avec pastille cliquable `📎 N doc(s)` ou `➕ Ajouter`.<br>&nbsp;&nbsp;• Bouton `📚 Ressources (N)` dans la barre d'action de la mallette ouvrant le gestionnaire agrégé (ressources globales de la mallette + de chaque livre).<br>5. **Intégration dans l'Inventaire Général (`InventaireController` / `inventaire.fxml`)** : Bouton d'accès direct `📚 Ressources pédagogiques (N)` sur chaque fiche de livre.<br>6. **Impression sur la Fiche A4 de la Mallette (`MalletteService`)** : Ajout d'une section élégante *« 📚 Pistes & Ressources pédagogiques associées »* sur la fiche d'inventaire A4 imprimable avec le nom des fiches, livre concerné et liens/fichiers pour l'enseignant.<br>7. **Modal interactif moderne (`RessourcesPedagogiquesDialog`)** : Visualisation, ouverture, suppression et ajout en temps réel avec sélection de fichier via `FileChooser` ou saisie d'URL avec auto-complétion du protocole `https://`.<br>8. **Banc de tests étendu** : **266 tests validés avec 100% de succès (266 ✅ 0 ❌)**. |

---

## 🚀 Lancement & Utilisation

- **Mode développement :** `mvn javafx:run`
- **Exécution directe du JAR :** `java -jar target/bcd-express-1.0-SNAPSHOT.jar`
- **Tests fonctionnels :** `mvn compile exec:java -Dexec.mainClass=fr.ecole.bcd.SmokeTest -q`
