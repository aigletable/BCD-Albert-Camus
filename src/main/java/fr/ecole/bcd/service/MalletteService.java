package fr.ecole.bcd.service;

import fr.ecole.bcd.database.DatabaseManager;
import fr.ecole.bcd.database.EmpruntDao;
import fr.ecole.bcd.database.ExemplaireDao;
import fr.ecole.bcd.database.LivreDao;
import fr.ecole.bcd.database.MalletteDao;
import fr.ecole.bcd.database.RessourcePedagogiqueDao;
import fr.ecole.bcd.model.*;
import fr.ecole.bcd.service.EmpruntService.RegleMetierException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service métier pour la gestion complète des Mallettes Pédagogiques & Thématiques.
 */
public class MalletteService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MalletteDao malletteDao;
    private final ExemplaireDao exemplaireDao;
    private final LivreDao livreDao;
    private final EmpruntDao empruntDao;
    private final InventaireSessionService inventaireService;

    public MalletteService() {
        this.malletteDao = new MalletteDao();
        this.exemplaireDao = new ExemplaireDao();
        this.livreDao = new LivreDao();
        this.empruntDao = new EmpruntDao();
        this.inventaireService = new InventaireSessionService();
    }

    public MalletteService(MalletteDao malletteDao, ExemplaireDao exemplaireDao, LivreDao livreDao,
                           EmpruntDao empruntDao, InventaireSessionService inventaireService) {
        this.malletteDao = malletteDao;
        this.exemplaireDao = exemplaireDao;
        this.livreDao = livreDao;
        this.empruntDao = empruntDao;
        this.inventaireService = inventaireService;
    }

    private final LivreMetadataService metadataService = new LivreMetadataService();

    // =====================================================================
    //  CRÉATION & GESTION DES MALLETTES
    // =====================================================================

    /**
     * Aperçu d'un scan de livre pour affichage dynamique dans l'interface.
     */
    public static class LivreScanPreview {
        private final String rawInput;
        private final String titre;
        private final String auteur;
        private final String isbnOuCode;
        private final boolean dejaEnBcd;
        private final String details;

        public LivreScanPreview(String rawInput, String titre, String auteur, String isbnOuCode, boolean dejaEnBcd, String details) {
            this.rawInput = rawInput;
            this.titre = titre;
            this.auteur = auteur;
            this.isbnOuCode = isbnOuCode;
            this.dejaEnBcd = dejaEnBcd;
            this.details = details;
        }

        public String getRawInput() { return rawInput; }
        public String getTitre() { return titre; }
        public String getAuteur() { return auteur; }
        public String getIsbnOuCode() { return isbnOuCode; }
        public boolean isDejaEnBcd() { return dejaEnBcd; }
        public String getDetails() { return details; }
    }

    /**
     * Crée une nouvelle mallette thématique avec code automatique.
     */
    public Mallette creerMallette(String titre, String theme, String niveau, String description)
            throws RegleMetierException, SQLException {

        if (titre == null || titre.isBlank()) {
            throw new RegleMetierException("Le titre de la mallette est obligatoire.");
        }
        if (theme == null || theme.isBlank()) {
            theme = "Général";
        }

        String code = malletteDao.genererCodeSuivant();
        Mallette m = new Mallette(code, titre.trim(), theme.trim(),
                niveau != null ? niveau.trim() : "",
                description != null ? description.trim() : "");

        malletteDao.insert(m);
        return m;
    }

    /**
     * Crée une nouvelle mallette thématique en exigeant au moins un livre scanné.
     * Les livres scannés ne sont pas obligatoirement dans la BCD (ils sont catalogués automatiquement).
     */
    public Mallette creerMalletteAvecLivres(String titre, String theme, String niveau, String description,
                                          List<String> rawLivresInputs) throws RegleMetierException, SQLException {

        if (rawLivresInputs == null || rawLivresInputs.isEmpty()) {
            throw new RegleMetierException("La création d'une mallette requiert d'y scanner au moins un livre (minimum 1 obligatoire).");
        }

        Mallette mallette = creerMallette(titre, theme, niveau, description);

        for (String raw : rawLivresInputs) {
            if (raw != null && !raw.isBlank()) {
                ajouterLivre(mallette.getId(), raw);
            }
        }

        return mallette;
    }

    /**
     * Inspecte un scan ou une saisie sans modifier la base, pour prévisualiser dans l'UI.
     */
    public LivreScanPreview inspecterScan(String rawInput, String fallbackTheme, String fallbackNiveau)
            throws RegleMetierException, SQLException {
        if (rawInput == null || rawInput.isBlank()) {
            throw new RegleMetierException("Veuillez saisir un code-barres ISBN ou un titre de livre.");
        }
        String clean = rawInput.trim();
        if (clean.startsWith("#")) clean = clean.substring(1).trim();

        // 1. Exemplaire physique direct par ID
        if (clean.matches("^\\d{1,7}$")) {
            int exId = Integer.parseInt(clean);
            Exemplaire ex = exemplaireDao.findById(exId);
            if (ex != null) {
                Livre l = livreDao.findByIsbn(ex.getIsbn());
                String t = l != null ? l.getTitre() : "Exemplaire #" + exId;
                String a = l != null ? l.getAuteur() : "";
                return new LivreScanPreview(rawInput, t, a, ex.getIsbn(), true, "Exemplaire #" + exId + " (en BCD)");
            }
        }

        // 2. Code-barres ISBN
        String cleanIsbn = LivreMetadataService.cleanIsbn(clean);
        boolean isIsbn = cleanIsbn.matches("^\\d{9}[0-9Xx]$")
                || cleanIsbn.matches("^\\d{13}$")
                || clean.toUpperCase().startsWith("ISBN")
                || clean.toUpperCase().startsWith("TEST-ISBN")
                || LivreMetadataService.isCodePresse(cleanIsbn);
        if (isIsbn) {
            String isbnKey = (clean.toUpperCase().startsWith("TEST-ISBN") || clean.toUpperCase().startsWith("ISBN")) ? clean : cleanIsbn;
            Livre l = livreDao.findByIsbn(isbnKey);
            if (l != null) {
                return new LivreScanPreview(rawInput, l.getTitre(), l.getAuteur(), isbnKey, true, "Catalogue BCD (ISBN)");
            }
            // Recherche externe via BNF / Web
            try {
                LivreMetadataService.MetadataResult meta = metadataService.rechercher(isbnKey);
                if (meta != null && meta.isFound()) {
                    return new LivreScanPreview(rawInput, meta.getTitre(), meta.getAuteur(), isbnKey, false,
                            "✨ Référencé BNF/Web (" + meta.getSource() + ")");
                }
            } catch (Exception ignored) {}
            return new LivreScanPreview(rawInput, "Livre " + isbnKey, "Auteur non renseigné", isbnKey, false, "✨ Nouveau livre (ISBN)");
        }

        // 3. Nom / Titre du livre
        Livre lTitre = livreDao.findByTitreExact(clean);
        if (lTitre == null) {
            List<Livre> matches = livreDao.findByTitre(clean);
            if (!matches.isEmpty()) lTitre = matches.get(0);
        }
        if (lTitre != null) {
            return new LivreScanPreview(rawInput, lTitre.getTitre(), lTitre.getAuteur(), lTitre.getIsbn(), true, "Catalogue BCD (titre)");
        }

        // 4. Titre hors BCD
        return new LivreScanPreview(rawInput, clean, "Auteur non renseigné", "Code BCD auto", false, "✨ Hors BCD (création auto)");
    }

    /**
     * Résout ou crée automatiquement un livre et son exemplaire physique.
     * Si le livre n'existe pas en BCD :
     * - S'il s'agit d'un code-barres ISBN : enrichissement via BNF/Google Books/OpenLibrary
     *   puis création automatique de la fiche Livre et de l'Exemplaire.
     * - S'il s'agit d'un titre de livre : création automatique avec code interne CAMUS-XXXX.
     */
    public Exemplaire resoudreOuCreerLivre(String rawInput, String fallbackTheme, String fallbackNiveau,
                                         String malletteCode, Integer malletteId) throws RegleMetierException, SQLException {
        if (rawInput == null || rawInput.isBlank()) {
            throw new RegleMetierException("Veuillez saisir un nom de livre, code-barres ou N° d'exemplaire.");
        }
        String clean = rawInput.trim();
        if (clean.startsWith("#")) clean = clean.substring(1).trim();

        Exemplaire targetEx = null;

        // 1. Identifiant physique direct (1 à 7 chiffres)
        if (clean.matches("^\\d{1,7}$")) {
            int exId = Integer.parseInt(clean);
            targetEx = exemplaireDao.findById(exId);
        }

        // 2. Recherche par ISBN si le scan ressemble à un ISBN ou périodique
        String cleanIsbn = LivreMetadataService.cleanIsbn(clean);
        boolean isIsbn = cleanIsbn.matches("^\\d{9}[0-9Xx]$")
                || cleanIsbn.matches("^\\d{13}$")
                || clean.toUpperCase().startsWith("ISBN")
                || clean.toUpperCase().startsWith("TEST-ISBN")
                || LivreMetadataService.isCodePresse(cleanIsbn);

        if (targetEx == null && isIsbn) {
            String effectiveIsbn = (clean.toUpperCase().startsWith("TEST-ISBN") || clean.toUpperCase().startsWith("ISBN")) ? clean : cleanIsbn;
            List<Exemplaire> disposIsbn = exemplaireDao.findDisponiblesByIsbn(effectiveIsbn);
            List<MalletteItem> currentItems = (malletteId != null && malletteId > 0)
                    ? malletteDao.getItems(malletteId)
                    : List.of();

            for (Exemplaire candidate : disposIsbn) {
                boolean alreadyInThis = currentItems.stream().anyMatch(it -> it.getExemplaireId() == candidate.getId());
                if (!alreadyInThis && !malletteDao.isExemplaireInMallette(candidate.getId())) {
                    targetEx = candidate;
                    break;
                }
            }

            // Si aucun exemplaire disponible n'a été trouvé, mais que le livre existe en BCD
            if (targetEx == null) {
                Livre existant = livreDao.findByIsbn(effectiveIsbn);
                if (existant != null) {
                    // Créer un nouvel exemplaire physique pour cette mallette
                    Exemplaire newEx = new Exemplaire(effectiveIsbn, Exemplaire.Statut.DISPONIBLE, LocalDate.now(),
                            "Mallette " + (malletteCode != null ? malletteCode : ""));
                    int newId = exemplaireDao.insert(newEx);
                    newEx.setId(newId);
                    targetEx = newEx;
                } else {
                    // Le livre N'EST PAS dans la BCD -> Auto-catalogage via API externe !
                    Livre livreCree = null;
                    try {
                        LivreMetadataService.MetadataResult meta = metadataService.rechercher(effectiveIsbn);
                        if (meta != null && meta.isFound()) {
                            livreCree = meta.toLivre();
                        }
                    } catch (Exception ignored) {}

                    if (livreCree == null) {
                        livreCree = new Livre(effectiveIsbn, "Livre " + effectiveIsbn, "Auteur non renseigné", "", "",
                                (fallbackTheme != null && !fallbackTheme.isBlank()) ? fallbackTheme : "Général",
                                "",
                                (fallbackNiveau != null) ? fallbackNiveau : "",
                                "Livre scanné pour la mallette " + (malletteCode != null ? malletteCode : ""));
                    } else {
                        if (livreCree.getTheme() == null || livreCree.getTheme().isBlank()) {
                            livreCree.setTheme((fallbackTheme != null && !fallbackTheme.isBlank()) ? fallbackTheme : "Général");
                        }
                        if (livreCree.getNiveau() == null || livreCree.getNiveau().isBlank()) {
                            livreCree.setNiveau(fallbackNiveau != null ? fallbackNiveau : "");
                        }
                    }

                    livreDao.insert(livreCree);

                    Exemplaire newEx = new Exemplaire(effectiveIsbn, Exemplaire.Statut.DISPONIBLE, LocalDate.now(),
                            "Mallette " + (malletteCode != null ? malletteCode : ""));
                    int newId = exemplaireDao.insert(newEx);
                    newEx.setId(newId);
                    targetEx = newEx;
                }
            }
        }

        // 3. Recherche par Nom / Titre du livre
        if (targetEx == null) {
            List<Exemplaire> disposTitre = exemplaireDao.findDisponiblesByTitre(clean);
            List<MalletteItem> currentItems = (malletteId != null && malletteId > 0)
                    ? malletteDao.getItems(malletteId)
                    : List.of();

            for (Exemplaire candidate : disposTitre) {
                boolean alreadyInThis = currentItems.stream().anyMatch(it -> it.getExemplaireId() == candidate.getId());
                if (!alreadyInThis && !malletteDao.isExemplaireInMallette(candidate.getId())) {
                    targetEx = candidate;
                    break;
                }
            }

            if (targetEx == null) {
                Livre existantTitre = livreDao.findByTitreExact(clean);
                if (existantTitre == null) {
                    List<Livre> matches = livreDao.findByTitre(clean);
                    if (!matches.isEmpty()) existantTitre = matches.get(0);
                }

                if (existantTitre != null) {
                    // Livre existant mais pas d'exemplaire dispo -> création d'un nouvel exemplaire
                    Exemplaire newEx = new Exemplaire(existantTitre.getIsbn(), Exemplaire.Statut.DISPONIBLE, LocalDate.now(),
                            "Mallette " + (malletteCode != null ? malletteCode : ""));
                    int newId = exemplaireDao.insert(newEx);
                    newEx.setId(newId);
                    targetEx = newEx;
                } else {
                    // Livre totalement hors BCD -> Création automatique avec code interne CAMUS-XXXX
                    int count = livreDao.count();
                    String candidateCode = "CAMUS-" + String.format("%04d", count + 1);
                    int offset = 1;
                    while (livreDao.findByIsbn(candidateCode) != null) {
                        candidateCode = "CAMUS-" + String.format("%04d", count + 1 + offset++);
                    }

                    Livre nouveauLivre = new Livre(candidateCode, clean, "Auteur non renseigné", "", "",
                            (fallbackTheme != null && !fallbackTheme.isBlank()) ? fallbackTheme : "Général",
                            "",
                            (fallbackNiveau != null) ? fallbackNiveau : "",
                            "Livre externe ajouté pour la mallette " + (malletteCode != null ? malletteCode : ""));
                    livreDao.insert(nouveauLivre);

                    Exemplaire newEx = new Exemplaire(candidateCode, Exemplaire.Statut.DISPONIBLE, LocalDate.now(),
                            "Mallette " + (malletteCode != null ? malletteCode : ""));
                    int newId = exemplaireDao.insert(newEx);
                    newEx.setId(newId);
                    targetEx = newEx;
                }
            }
        }

        if (targetEx == null) {
            throw new RegleMetierException("Impossible d'identifier ou de créer un exemplaire pour : '" + rawInput + "'.");
        }

        // Vérifications de conflits
        if (malletteId != null && malletteId > 0) {
            List<MalletteItem> items = malletteDao.getItems(malletteId);
            for (MalletteItem it : items) {
                if (it.getExemplaireId() == targetEx.getId()) {
                    throw new RegleMetierException("Le livre #" + targetEx.getId() + " (" + clean + ") est déjà dans cette mallette.");
                }
            }

            Mallette otherMallette = malletteDao.getMalletteForExemplaire(targetEx.getId());
            if (otherMallette != null && otherMallette.getId() != malletteId) {
                // Si l'exemplaire précis était déjà rangé dans une autre mallette,
                // on crée une copie propre pour cette mallette
                Exemplaire newEx = new Exemplaire(targetEx.getIsbn(), Exemplaire.Statut.DISPONIBLE, LocalDate.now(),
                        "Mallette " + (malletteCode != null ? malletteCode : ""));
                int newId = exemplaireDao.insert(newEx);
                newEx.setId(newId);
                targetEx = newEx;
            }
        }

        return targetEx;
    }

    /**
     * Nettoie la remarque d'un exemplaire pour en retirer toute mention de mallette.
     * Exemple : "Mallette MAL-004" -> ""
     *           "⚠️ Déchiré • Mallette MAL-001" -> "⚠️ Déchiré"
     *
     * @param currentRemarque la remarque actuelle de l'exemplaire
     * @return la remarque nettoyée de toute mention de mallette
     */
    public static String nettoyerRemarqueMallette(String currentRemarque) {
        if (currentRemarque == null || currentRemarque.isBlank()) {
            return "";
        }
        String cleaned = currentRemarque
                .replaceAll("(?i)\\s*[-•]?\\s*Mallette(\\s+[A-Za-z0-9-]+)?\\s*[-•]?\\s*", " ")
                .replaceAll("\\s+", " ")
                .trim();
        while (cleaned.startsWith("•") || cleaned.startsWith("-")) {
            cleaned = cleaned.substring(1).trim();
        }
        while (cleaned.endsWith("•") || cleaned.endsWith("-")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    /**
     * Ajoute la mention d'une mallette dans la remarque d'un exemplaire s'il ne la possède pas déjà.
     *
     * @param currentRemarque la remarque actuelle
     * @param malletteCode le code de la mallette (ex: "MAL-001")
     * @return la remarque mise à jour avec le tag de la mallette
     */
    public static String formaterRemarqueMallette(String currentRemarque, String malletteCode) {
        String tag = "Mallette " + (malletteCode != null ? malletteCode.trim() : "").trim();
        if (currentRemarque == null || currentRemarque.isBlank()) {
            return tag;
        }
        if (currentRemarque.contains(tag)) {
            return currentRemarque;
        }
        return currentRemarque.trim() + " • " + tag;
    }

    /**
     * Ajoute un livre dans la mallette par son Titre, son N° d'exemplaire ou son code-barres ISBN.
     * Si le livre n'est pas encore dans la BCD, il est automatiquement catalogué et ajouté.
     */
    public Exemplaire ajouterLivre(int malletteId, String rawInput) throws RegleMetierException, SQLException {
        Mallette mallette = malletteDao.findById(malletteId);
        if (mallette == null) {
            throw new RegleMetierException("La mallette #" + malletteId + " n'existe pas.");
        }

        Exemplaire targetEx = resoudreOuCreerLivre(rawInput, mallette.getTheme(), mallette.getNiveau(),
                mallette.getCode(), malletteId);

        malletteDao.addItem(malletteId, targetEx.getId());

        // Synchroniser l'indication d'appartenance à la mallette dans la remarque de l'exemplaire
        String newRem = formaterRemarqueMallette(targetEx.getRemarque(), mallette.getCode());
        if (!newRem.equals(targetEx.getRemarque())) {
            exemplaireDao.updateRemarque(targetEx.getId(), newRem);
            targetEx.setRemarque(newRem);
        }

        // Si la mallette est actuellement en prêt, emprunter immédiatement ce nouveau livre
        if (mallette.isEmprunte() && mallette.getEmprunteurNumero() != null) {
            LocalDate deb = mallette.getDateEmprunt() != null ? mallette.getDateEmprunt() : LocalDate.now();
            LocalDate fin = mallette.getDateRetourPrevue() != null ? mallette.getDateRetourPrevue() : LocalDate.now().plusWeeks(4);
            empruntDao.creerEmpruntAvecDateLimite(mallette.getEmprunteurNumero(), targetEx.getId(), deb, fin);
            exemplaireDao.updateStatut(targetEx.getId(), Exemplaire.Statut.EMPRUNTE);
            targetEx.setStatut(Exemplaire.Statut.EMPRUNTE);
        }

        return targetEx;
    }

    /**
     * Ajoute tous les livres disponibles correspondant au thème spécifié dans la mallette.
     * Déduplique automatiquement par ISBN afin d'éviter les doublons d'un même titre dans la boîte.
     */
    public int remplirParTheme(int malletteId, String theme) throws RegleMetierException, SQLException {
        if (theme == null || theme.isBlank()) {
            throw new RegleMetierException("Veuillez sélectionner un thème.");
        }
        Mallette mallette = malletteDao.findById(malletteId);
        if (mallette == null) {
            throw new RegleMetierException("La mallette n'existe pas.");
        }

        List<MalletteItem> currentItems = malletteDao.getItems(malletteId);
        Set<String> presentIsbns = new HashSet<>();
        for (MalletteItem it : currentItems) {
            if (it.getIsbn() != null && !it.getIsbn().isBlank()) {
                presentIsbns.add(it.getIsbn().trim());
            }
        }

        String sql = "SELECT e.* FROM exemplaires e "
                   + "JOIN livres l ON e.isbn = l.isbn "
                   + "WHERE e.statut = 'DISPONIBLE' "
                   + "  AND (LOWER(l.theme) = LOWER(?) OR LOWER(l.theme) LIKE LOWER(?) OR LOWER(l.titre) LIKE LOWER(?) OR LOWER(l.description) LIKE LOWER(?))";

        String clean = theme.trim();
        int added = 0;

        try (java.sql.PreparedStatement ps = DatabaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, clean);
            ps.setString(2, "%" + clean + "%");
            ps.setString(3, "%" + clean + "%");
            ps.setString(4, "%" + clean + "%");

            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int exId = rs.getInt("id");
                    String isbn = rs.getString("isbn");

                    // Déduplication : 1 seul exemplaire par titre dans une mallette thématique
                    if (isbn != null && presentIsbns.contains(isbn.trim())) {
                        continue;
                    }

                    boolean alreadyInThis = currentItems.stream().anyMatch(it -> it.getExemplaireId() == exId);
                    if (!alreadyInThis && !malletteDao.isExemplaireInMallette(exId)) {
                        malletteDao.addItem(malletteId, exId);
                        String curRem = rs.getString("remarque");
                        String newRem = formaterRemarqueMallette(curRem, mallette.getCode());
                        exemplaireDao.updateRemarque(exId, newRem);

                        if (isbn != null && !isbn.isBlank()) {
                            presentIsbns.add(isbn.trim());
                        }
                        added++;
                    }
                }
            }
        }
        return added;
    }

    /**
     * Retire un livre d'une mallette, nettoie sa remarque d'inventaire
     * et clôture tout emprunt actif si la mallette était prêtée.
     */
    public void retirerLivre(int malletteId, int exemplaireId) throws SQLException {
        malletteDao.removeItem(malletteId, exemplaireId);

        // Nettoyage de la remarque d'exemplaire (retirer "Mallette MAL-XXX")
        Exemplaire ex = exemplaireDao.findById(exemplaireId);
        if (ex != null) {
            String cleanRem = nettoyerRemarqueMallette(ex.getRemarque());
            exemplaireDao.updateRemarque(exemplaireId, cleanRem);
            ex.setRemarque(cleanRem);
        }

        // Si l'exemplaire était en prêt au sein de cette mallette, clôturer l'emprunt
        Emprunt emp = empruntDao.findEnCoursByExemplaire(exemplaireId);
        if (emp != null) {
            empruntDao.retourner(emp.getId());
        }
        exemplaireDao.updateStatut(exemplaireId, Exemplaire.Statut.DISPONIBLE);
    }

    /**
     * Retire tous les exemplaires d'un livre (même ISBN) présents dans cette mallette.
     *
     * @param malletteId l'identifiant de la mallette
     * @param isbn l'ISBN ou code interne du livre à retirer
     * @return le nombre d'exemplaires retirés
     */
    public int retirerTousExemplairesLivre(int malletteId, String isbn) throws SQLException {
        if (isbn == null || isbn.isBlank()) return 0;
        List<MalletteItem> items = malletteDao.getItems(malletteId);
        int count = 0;
        for (MalletteItem it : items) {
            if (isbn.trim().equals(it.getIsbn() != null ? it.getIsbn().trim() : "")) {
                retirerLivre(malletteId, it.getExemplaireId());
                count++;
            }
        }
        return count;
    }

    /**
     * Vide intégralement une mallette en nettoyant les remarques de tous les exemplaires physiques.
     */
    public void viderMallette(int malletteId) throws SQLException {
        List<MalletteItem> items = malletteDao.getItems(malletteId);
        for (MalletteItem it : items) {
            retirerLivre(malletteId, it.getExemplaireId());
        }
    }

    /**
     * Supprime définitivement une mallette et libère tous ses exemplaires dans la BCD.
     */
    public void supprimerMallette(int malletteId) throws SQLException {
        viderMallette(malletteId);
        malletteDao.delete(malletteId);
    }

    // =====================================================================
    //  PRÊT & RETOUR DE MALLETTE EN 1 CLIC
    // =====================================================================

    /**
     * Emprunte une mallette complète à un enseignant.
     * Tous les exemplaires contenus dans la boîte passent en statut EMPRUNTE.
     */
    public void emprunterMallette(int malletteId, Utilisateur enseignant, LocalDate dateRetourPrevue)
            throws RegleMetierException, SQLException {

        if (enseignant == null) {
            throw new RegleMetierException("Veuillez sélectionner un enseignant.");
        }

        Mallette mallette = malletteDao.findById(malletteId);
        if (mallette == null) {
            throw new RegleMetierException("La mallette n'existe pas.");
        }

        if (mallette.isEmprunte()) {
            throw new RegleMetierException("Cette mallette est déjà empruntée par " + mallette.getEmprunteurComplet() + ".");
        }

        List<MalletteItem> items = malletteDao.getItems(malletteId);
        if (items.isEmpty()) {
            throw new RegleMetierException("La mallette est vide ! Ajoutez au moins un livre avant de la prêter.");
        }

        if (dateRetourPrevue == null || dateRetourPrevue.isBefore(LocalDate.now())) {
            dateRetourPrevue = LocalDate.now().plusWeeks(4); // Par défaut 4 semaines pour une mallette
        }

        // Vérifier qu'aucun livre n'est gelé par un inventaire
        for (MalletteItem item : items) {
            if (inventaireService.isExemplaireGele(item.getExemplaireId())) {
                throw new RegleMetierException("L'exemplaire #" + item.getExemplaireId()
                        + " fait partie d'une session d'inventaire en cours (stock gelé).");
            }
        }

        // Créer les emprunts pour chaque livre physique de la mallette
        LocalDate now = LocalDate.now();
        for (MalletteItem item : items) {
            empruntDao.creerEmpruntAvecDateLimite(enseignant.getNumero(), item.getExemplaireId(), now, dateRetourPrevue);
            exemplaireDao.updateStatut(item.getExemplaireId(), Exemplaire.Statut.EMPRUNTE);
        }

        // Marquer la mallette comme empruntée
        String nomComplet = enseignant.getPrenom() + " " + enseignant.getNom();
        malletteDao.setEmprunt(malletteId, enseignant.getNumero(), nomComplet, enseignant.getClasse(), now, dateRetourPrevue);
    }

    /**
     * Restitue une mallette complète.
     * Tous les livres associés sont retournés et remis en rayon (DISPONIBLE).
     */
    public void retournerMallette(int malletteId) throws RegleMetierException, SQLException {
        Mallette mallette = malletteDao.findById(malletteId);
        if (mallette == null) {
            throw new RegleMetierException("La mallette n'existe pas.");
        }

        List<MalletteItem> items = malletteDao.getItems(malletteId);
        LocalDate now = LocalDate.now();

        for (MalletteItem item : items) {
            Emprunt emp = empruntDao.findEnCoursByExemplaire(item.getExemplaireId());
            if (emp != null) {
                empruntDao.retourner(emp.getId());
            }
            exemplaireDao.updateStatut(item.getExemplaireId(), Exemplaire.Statut.DISPONIBLE);
        }

        malletteDao.clearEmprunt(malletteId);
    }

    // =====================================================================
    //  DOCUMENTS IMPRIMABLES (Fiche d'inventaire A4 & Étiquette Caisse)
    // =====================================================================

    /**
     * Génère la Fiche d'Inventaire A4 de la Mallette (prête à imprimer).
     */
    public File genererFicheContenuHtml(int malletteId) throws SQLException, IOException {
        Mallette mallette = malletteDao.findById(malletteId);
        if (mallette == null) {
            throw new IllegalArgumentException("Mallette introuvable #" + malletteId);
        }
        List<MalletteItem> items = malletteDao.getItems(malletteId);

        RessourcePedagogiqueDao ressourceDao = new RessourcePedagogiqueDao();
        List<RessourcePedagogique> ressources = ressourceDao.findAllForMallette(malletteId);

        String barcodeSvg = BarcodeService.generateCode39Svg(mallette.getCode(), 45);

        StringBuilder rows = new StringBuilder();
        int num = 1;
        for (MalletteItem it : items) {
            rows.append(String.format(
                    "<tr>"
                    + "<td class='tc-num'>%d</td>"
                    + "<td class='tc-box'><span class='check-box'>&square;</span></td>"
                    + "<td class='tc-id'>#%d</td>"
                    + "<td class='tc-titre'><strong>%s</strong></td>"
                    + "<td class='tc-auteur'>%s</td>"
                    + "<td class='tc-genre'><span class='badge-genre'>%s</span></td>"
                    + "<td class='tc-isbn'>%s</td>"
                    + "</tr>\n",
                    num++,
                    it.getExemplaireId(),
                    escapeHtml(it.getTitre()),
                    escapeHtml(it.getAuteur()),
                    escapeHtml(it.getGenre().isEmpty() ? "Livre" : it.getGenre()),
                    escapeHtml(it.getIsbn())
            ));
        }

        String html = "<!DOCTYPE html>\n<html lang='fr'>\n<head>\n"
                + "<meta charset='UTF-8'>\n"
                + "<title>Fiche Mallette - " + escapeHtml(mallette.getCode()) + "</title>\n"
                + "<style>\n"
                + "@page { size: A4 portrait; margin: 12mm 15mm 12mm 15mm; }\n"
                + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #1e293b; margin: 0; padding: 0; font-size: 13px; line-height: 1.4; }\n"
                + ".header-bar { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #2563eb; padding-bottom: 12px; margin-bottom: 16px; }\n"
                + ".school-info h1 { font-size: 18px; margin: 0 0 2px 0; color: #1e3a8a; }\n"
                + ".school-info p { margin: 0; font-size: 12px; color: #64748b; }\n"
                + ".barcode-card { text-align: right; }\n"
                + ".card-meta { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 14px 18px; margin-bottom: 20px; }\n"
                + ".card-title { font-size: 20px; font-weight: 800; color: #0f172a; margin-top: 0; margin-bottom: 8px; }\n"
                + ".meta-tags { display: flex; gap: 8px; margin-bottom: 8px; }\n"
                + ".tag { display: inline-block; padding: 3px 10px; border-radius: 6px; font-size: 11px; font-weight: 700; text-transform: uppercase; }\n"
                + ".tag-theme { background: #e0f2fe; color: #0369a1; }\n"
                + ".tag-niveau { background: #f3e8ff; color: #7e22ce; }\n"
                + ".tag-code { background: #dbeafe; color: #1d4ed8; font-family: monospace; }\n"
                + ".card-desc { font-size: 12px; color: #475569; margin: 6px 0 0 0; font-style: italic; }\n"
                + "table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }\n"
                + "th { background: #f1f5f9; color: #475569; font-size: 11px; text-transform: uppercase; padding: 8px 10px; text-align: left; border-bottom: 2px solid #cbd5e1; }\n"
                + "td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; font-size: 12px; vertical-align: middle; }\n"
                + "tr:nth-child(even) { background: #fafafa; }\n"
                + ".tc-num { width: 24px; text-align: center; color: #94a3b8; font-size: 11px; }\n"
                + ".tc-box { width: 30px; text-align: center; }\n"
                + ".check-box { font-size: 16px; color: #94a3b8; }\n"
                + ".tc-id { width: 60px; font-family: monospace; font-weight: 600; color: #2563eb; }\n"
                + ".badge-genre { background: #f1f5f9; border-radius: 4px; padding: 2px 6px; font-size: 10px; color: #475569; }\n"
                + ".tc-isbn { width: 120px; font-family: monospace; font-size: 11px; color: #64748b; }\n"
                + ".sign-block { display: flex; justify-content: space-between; margin-top: 25px; border-top: 1px dashed #cbd5e1; padding-top: 15px; page-break-inside: avoid; }\n"
                + ".sign-col { width: 45%; }\n"
                + ".sign-col p { margin: 4px 0; font-size: 12px; color: #334155; }\n"
                + ".sign-box { height: 50px; border: 1px solid #cbd5e1; border-radius: 4px; margin-top: 6px; }\n"
                + "@media print { .no-print { display: none; } }\n"
                + "</style>\n</head>\n<body>\n"
                + "<div class='header-bar'>\n"
                + "  <div class='school-info'>\n"
                + "    <h1>🏫 École Albert Camus Pasteur</h1>\n"
                + "    <p>Bibliothèque Centre Documentaire (BCD) • Fiche de Mallette Pédagogique</p>\n"
                + "  </div>\n"
                + "  <div class='barcode-card'>\n"
                + "    " + barcodeSvg + "\n"
                + "  </div>\n"
                + "</div>\n"
                + "<div class='card-meta'>\n"
                + "  <div class='meta-tags'>\n"
                + "    <span class='tag tag-code'>" + escapeHtml(mallette.getCode()) + "</span>\n"
                + "    <span class='tag tag-theme'>Thème : " + escapeHtml(mallette.getTheme()) + "</span>\n"
                + "    <span class='tag tag-niveau'>Niveau : " + escapeHtml(mallette.getNiveau().isEmpty() ? "Tous niveaux" : mallette.getNiveau()) + "</span>\n"
                + "  </div>\n"
                + "  <h2 class='card-title'>" + escapeHtml(mallette.getTitre()) + "</h2>\n"
                + "  <p class='card-desc'>" + escapeHtml(mallette.getDescription().isEmpty() ? "Contenu pédagogique sélectionné par la BCD." : mallette.getDescription()) + "</p>\n"
                + "</div>\n"
                + "<table>\n"
                + "  <thead>\n"
                + "    <tr>\n"
                + "      <th class='tc-num'>N°</th>\n"
                + "      <th class='tc-box'>Pointage</th>\n"
                + "      <th class='tc-id'>Ex.</th>\n"
                + "      <th>Titre de l'ouvrage (" + items.size() + " livre" + (items.size() > 1 ? "s" : "") + ")</th>\n"
                + "      <th>Auteur</th>\n"
                + "      <th>Genre</th>\n"
                + "      <th class='tc-isbn'>ISBN</th>\n"
                + "    </tr>\n"
                + "  </thead>\n"
                + "  <tbody>\n"
                + rows.toString()
                + "  </tbody>\n"
                + "</table>\n"
                + ressourceHtmlSection(ressources)
                + "<div class='sign-block'>\n"
                + "  <div class='sign-col'>\n"
                + "    <p><strong>Emprunté par :</strong> " + escapeHtml(mallette.getEmprunteurComplet()) + "</p>\n"
                + "    <p><strong>Date d'emprunt :</strong> " + mallette.getDateEmpruntFormatted() + "</p>\n"
                + "    <p><strong>Retour prévu le :</strong> " + mallette.getDateRetourPrevueFormatted() + "</p>\n"
                + "  </div>\n"
                + "  <div class='sign-col'>\n"
                + "    <p><strong>Visa / Signature de l'enseignant :</strong></p>\n"
                + "    <div class='sign-box'></div>\n"
                + "  </div>\n"
                + "</div>\n"
                + "</body>\n</html>";

        File tempFile = File.createTempFile("fiche_mallette_" + mallette.getCode() + "_", ".html");
        try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write(html);
        }
        return tempFile;
    }

    private String ressourceHtmlSection(List<RessourcePedagogique> ressources) {
        if (ressources == null || ressources.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<div style='margin-top: 18px; margin-bottom: 16px;'>\n")
          .append("  <h3 style='font-size: 13px; color: #1e3a8a; margin: 0 0 6px 0; border-bottom: 1px solid #93c5fd; padding-bottom: 4px;'>📚 Pistes & Ressources pédagogiques associées (")
          .append(ressources.size()).append(")</h3>\n")
          .append("  <table style='margin-bottom: 10px;'>\n")
          .append("    <thead><tr><th style='width: 30px;'>Type</th><th>Titre de la ressource</th><th style='width: 140px;'>Livre associé</th><th>Lien / Document</th></tr></thead>\n")
          .append("    <tbody>\n");
        for (RessourcePedagogique r : ressources) {
            String cible = r.isFichierLocal() ? new File(r.getUrlOuChemin()).getName() : r.getUrlOuChemin();
            String livreAssocie = (r.getLivreTitre() != null && !r.getLivreTitre().isBlank()) ? r.getLivreTitre() : "Mallette entière";
            sb.append("      <tr>")
              .append("<td style='text-align: center;'>").append(r.getIcone()).append("</td>")
              .append("<td><strong>").append(escapeHtml(r.getTitre())).append("</strong>")
              .append(r.getDescription().isBlank() ? "" : "<br><span style='font-size: 10px; color: #64748b; font-style: italic;'>" + escapeHtml(r.getDescription()) + "</span>")
              .append("</td>")
              .append("<td><span style='font-size: 11px; color: #0369a1;'>").append(escapeHtml(livreAssocie)).append("</span></td>")
              .append("<td><span style='font-family: monospace; font-size: 10px; color: #475569;'>").append(escapeHtml(cible)).append("</span></td>")
              .append("</tr>\n");
        }
        sb.append("    </tbody>\n  </table>\n</div>\n");
        return sb.toString();
    }

    /**
     * Génère l'Étiquette pour coller sur la caisse / mallette plastique.
     */
    public File genererEtiquetteMalletteHtml(int malletteId) throws SQLException, IOException {
        Mallette mallette = malletteDao.findById(malletteId);
        if (mallette == null) {
            throw new IllegalArgumentException("Mallette introuvable #" + malletteId);
        }
        int count = malletteDao.countItems(malletteId);
        String barcodeSvg = BarcodeService.generateCode39Svg(mallette.getCode(), 65);

        String html = "<!DOCTYPE html>\n<html lang='fr'>\n<head>\n"
                + "<meta charset='UTF-8'>\n"
                + "<title>Étiquette Mallette " + escapeHtml(mallette.getCode()) + "</title>\n"
                + "<style>\n"
                + "@page { size: 148mm 105mm; margin: 6mm; }\n"
                + "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background: #fff; }\n"
                + ".etiquette-box { border: 3px solid #1e3a8a; border-radius: 12px; padding: 14px; height: 86mm; box-sizing: border-box; display: flex; flex-direction: column; justify-content: space-between; }\n"
                + ".top-row { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #e2e8f0; padding-bottom: 8px; }\n"
                + ".school-name { font-size: 13px; font-weight: 800; color: #1e3a8a; text-transform: uppercase; }\n"
                + ".tag-theme { background: #2563eb; color: #fff; padding: 4px 10px; border-radius: 6px; font-size: 12px; font-weight: 700; }\n"
                + ".title-box { margin: 10px 0; text-align: center; }\n"
                + ".mallette-title { font-size: 22px; font-weight: 900; color: #0f172a; margin: 0 0 6px 0; line-height: 1.2; }\n"
                + ".mallette-sub { font-size: 13px; color: #475569; font-weight: 600; }\n"
                + ".barcode-row { text-align: center; margin-top: 6px; }\n"
                + ".footer-row { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #64748b; border-top: 1px solid #e2e8f0; padding-top: 6px; }\n"
                + "</style>\n</head>\n<body>\n"
                + "<div class='etiquette-box'>\n"
                + "  <div class='top-row'>\n"
                + "    <span class='school-name'>🏫 École Albert Camus Pasteur • BCD</span>\n"
                + "    <span class='tag-theme'>" + escapeHtml(mallette.getTheme()) + "</span>\n"
                + "  </div>\n"
                + "  <div class='title-box'>\n"
                + "    <div class='mallette-title'>" + escapeHtml(mallette.getTitre()) + "</div>\n"
                + "    <div class='mallette-sub'>Niveau : " + escapeHtml(mallette.getNiveau().isEmpty() ? "Tous niveaux" : mallette.getNiveau()) + " • " + count + " livre" + (count > 1 ? "s" : "") + " inclus</div>\n"
                + "  </div>\n"
                + "  <div class='barcode-row'>\n"
                + "    " + barcodeSvg + "\n"
                + "  </div>\n"
                + "  <div class='footer-row'>\n"
                + "    <span>Code : " + escapeHtml(mallette.getCode()) + "</span>\n"
                + "    <span>Matériel pédagogique BCD à conserver en boîte</span>\n"
                + "  </div>\n"
                + "</div>\n"
                + "</body>\n</html>";

        File tempFile = File.createTempFile("etiquette_mallette_" + mallette.getCode() + "_", ".html");
        try (FileWriter writer = new FileWriter(tempFile, StandardCharsets.UTF_8)) {
            writer.write(html);
        }
        return tempFile;
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
