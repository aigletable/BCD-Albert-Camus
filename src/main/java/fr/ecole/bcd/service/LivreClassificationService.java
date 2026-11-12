package fr.ecole.bcd.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Moteur sémantique d'inférence et d'auto-classification des livres scolaires.
 * <p>
 * Déduit automatiquement au moment du scan ISBN :
 * <ul>
 *   <li><b>Genre</b> : BD, Album, Roman, Documentaire, Conte, Poésie / Théâtre, Manga, Revue...</li>
 *   <li><b>Thème</b> : Guerre & Histoire, Animaux, Nature & Écologie, Aventure, Imaginaire & Magie...</li>
 *   <li><b>Niveau scolaire</b> : Maternelle, CP, CE1, CE2, CM1, CM2 (ou Cycle 1, Cycle 2, Cycle 3)...</li>
 * </ul>
 * </p>
 */
public class LivreClassificationService {

    public static final List<String> GENRES_STANDARDS = List.of(
            "BD",
            "Album",
            "Roman",
            "Documentaire",
            "Conte",
            "Poésie / Théâtre",
            "Manga",
            "Revue / Magazine",
            "Autre"
    );

    public static final List<String> THEMES_STANDARDS = List.of(
            "Harcèlement & Vivre ensemble",
            "Guerre & Histoire",
            "Animaux",
            "Nature & Écologie",
            "Aventure & Exploration",
            "Imaginaire & Magie",
            "Vie quotidienne & École",
            "Humour & Bêtises",
            "Sciences & Espace",
            "Contes, Mythes & Légendes",
            "Arts, Musique & Sports",
            "Autre"
    );

    public static final List<String> NIVEAUX_STANDARDS = List.of(
            "Cycle 1 (Maternelle)",
            "Cycle 2 (CP-CE2)",
            "Cycle 3 (CM1-CM2)",
            "Maternelle",
            "CP",
            "CE1",
            "CE2",
            "CM1",
            "CM2",
            "Tous niveaux"
    );

    /**
     * Résultat de classification d'un livre.
     */
    public static class ClassificationResult {
        private final String genre;
        private final String theme;
        private final String niveau;

        public ClassificationResult(String genre, String theme, String niveau) {
            this.genre = genre != null && !genre.isBlank() ? genre : "Roman";
            this.theme = theme != null && !theme.isBlank() ? theme : "Vie quotidienne & École";
            this.niveau = niveau != null && !niveau.isBlank() ? niveau : "Cycle 2 (CP-CE2)";
        }

        public String getGenre() { return genre; }
        public String getTheme() { return theme; }
        public String getNiveau() { return niveau; }

        @Override
        public String toString() {
            return String.format("[%s | %s | %s]", genre, theme, niveau);
        }
    }

    /**
     * Analyse l'ensemble des données bibliographiques collectées pour classifier l'ouvrage.
     */
    public ClassificationResult classify(String titre, String auteur, String editeur,
                                         String collection, List<String> subjects,
                                         String dewey, String ageMention,
                                         String description, int pageCount) {

        String textCorpus = buildCorpus(titre, auteur, editeur, collection, subjects, description);

        String genre = detectGenre(titre, editeur, collection, subjects, dewey, textCorpus, pageCount);
        String theme = detectTheme(titre, collection, subjects, dewey, textCorpus);
        String niveau = detectNiveau(titre, collection, subjects, ageMention, textCorpus, pageCount, genre);

        return new ClassificationResult(genre, theme, niveau);
    }

    /**
     * Analyse simplifiée basée uniquement sur les champs de base (titre, auteur, éditeur, description).
     */
    public ClassificationResult classifySimple(String titre, String auteur, String editeur, String description) {
        return classify(titre, auteur, editeur, "", java.util.Collections.emptyList(), "", "", description, 0);
    }

    // =====================================================================
    //  1. Détection du Genre
    // =====================================================================

    private String detectGenre(String titre, String editeur, String collection,
                               List<String> subjects, String dewey,
                               String corpus, int pageCount) {

        String ed = normalize(editeur);
        String col = normalize(collection);
        String tit = normalize(titre);

        // 1. Presse / Périodique
        if (col.contains("j'aime lire") || col.contains("astrapi") || col.contains("wakou")
                || col.contains("youpi") || col.contains("images doc") || col.contains("pomme d'api")
                || col.contains("belles histoires") || tit.contains("revue") || tit.contains("magazine")) {
            return "Revue / Magazine";
        }

        // 2. Manga
        if (col.contains("manga") || ed.contains("kurokawa") || ed.contains("ki-oon")
                || ed.contains("pika") || ed.contains("kana") || corpus.contains("shonen")
                || corpus.contains("shojo") || hasSubjectMatch(subjects, "manga")) {
            return "Manga";
        }

        // 3. Bande dessinée (BD)
        if (hasSubjectMatch(subjects, "bandes dessinées", "bande dessinée", "comics", "bd")
                || dewey.startsWith("741.5")
                || ed.contains("lombard") || ed.contains("dupuis") || ed.contains("dargaud")
                || ed.contains("delcourt") || ed.contains("glenat") || ed.contains("bamboo")
                || ed.contains("casterman bd") || col.contains("tchô") || col.contains("bd")
                || tit.contains("tome ") || tit.contains("les enfants de la resistance")
                || tit.contains("asterix") || tit.contains("tintin") || tit.contains("lucky luke")
                || tit.contains("mortelle adele") || tit.contains("ducobu") || tit.contains("titeuf")
                || tit.contains("les sisters") || tit.contains("anatole latuile")
                || corpus.contains("scénario") && corpus.contains("dessin") && corpus.contains("couleurs")) {
            return "BD";
        }

        // 4. Poésie / Théâtre
        if (hasSubjectMatch(subjects, "poésie", "théâtre", "poèmes")
                || corpus.contains("recueil de poèmes") || corpus.contains("pièce de théâtre")
                || col.contains("théâtre") || tit.contains("poésies") || tit.contains("fables")) {
            return "Poésie / Théâtre";
        }

        // 5. Conte / Fables / Mythes
        if (hasSubjectMatch(subjects, "contes", "conte", "fables", "légendes")
                || tit.contains("conte ") || tit.contains("contes ") || tit.contains("roule galette")
                || tit.contains("cric crac") || col.contains("contes")
                || corpus.contains("conte traditionnel") || corpus.contains("conte de fées")) {
            return "Conte";
        }

        // 6. Documentaire
        if (hasSubjectMatch(subjects, "documentaires", "documentaire", "non fiction", "ouvrages pour la jeunesse -- guides")
                || (!dewey.isEmpty() && !dewey.startsWith("8") && !dewey.startsWith("741.5"))
                || col.contains("p'tits docs") || col.contains("ptits docs") || col.contains("grande imagerie")
                || col.contains("mes premieres decouvertes") || col.contains("petite encyclopedie")
                || col.contains("decouverte") || col.contains("dokéo") || col.contains("copain")
                || ed.contains("fleurus") || ed.contains("milan jeunesse doc")
                || tit.contains("documentaire") || tit.contains("encyclopédie") || tit.contains("encyclopedie")
                || tit.contains("tout savoir sur") || tit.contains("le corps humain") || tit.contains("copain")
                || tit.contains("mon premier atlas") || tit.contains("guide des ") || tit.contains("atlas")
                || corpus.contains("documentaire")) {
            return "Documentaire";
        }

        // 7. Album jeunesse
        if (hasSubjectMatch(subjects, "albums", "album")
                || ed.contains("ecole des loisirs") || ed.contains("kaleidoscope")
                || ed.contains("pastel") || ed.contains("mijade") || ed.contains("didier jeunesse")
                || ed.contains("pere castor") || col.contains("lutin poche")
                || tit.contains("le loup qui ") || tit.contains("les trois brigands")
                || tit.contains("cornebidouille") || tit.contains("elmer") || tit.contains("chouquette")
                || (pageCount > 0 && pageCount <= 40 && !tit.contains("roman"))) {
            return "Album";
        }

        // 8. Roman
        if (hasSubjectMatch(subjects, "romans", "roman", "fiction")
                || col.contains("folio junior") || col.contains("folio cadet") || col.contains("castor poche")
                || col.contains("pocket jeunesse") || col.contains("rageot") || col.contains("j'aime lire")
                || col.contains("premiere lecture") || col.contains("premières lectures")
                || tit.contains("harry potter") || tit.contains("cabane magique") || tit.contains("chair de poule")
                || pageCount >= 48) {
            return "Roman";
        }

        return "Roman";
    }

    // =====================================================================
    //  2. Détection du Thème
    // =====================================================================

    private String detectTheme(String titre, String collection, List<String> subjects,
                               String dewey, String corpus) {

        // Harcèlement & Vivre ensemble
        if (corpusMatches(corpus, "harcelement", "harceler", "intimidation", "moquerie", "bouc emissaire",
                "vivre ensemble", "difference", "empathie", "rejet", "rumeur", "solidarite",
                "exclusion", "discrimination", "cyberharcelement", "racket", "seul contre tous", "victime")) {
            return "Harcèlement & Vivre ensemble";
        }

        // Guerre & Histoire
        if (corpusMatches(corpus, "guerre", "resistance", "1939-1945", "1914-1918", "soldat", "nazi",
                "seconde guerre", "premiere guerre", "moyen age", "chateau fort", "chevalier",
                "pharaon", "egypte", "antiquite", "romains", "gaulois", "prehistoire", "roi", "reine",
                "revolution", "napoleon", "histoire", "empire", "tranchee", "debarquement", "conflit")
                || dewey.startsWith("93") || dewey.startsWith("94")) {
            return "Guerre & Histoire";
        }

        // Animaux
        if (corpusMatches(corpus, "animal", "animaux", "chien", "chat", "loup", "renard", "ours",
                "cheval", "poney", "dinosaure", "oiseau", "poisson", "dauphin", "requin", "baleine",
                "lion", "tigre", "girafe", "elephant", "singe", "souris", "lapin", "faune", "zoo",
                "veterinaire", "canard", "insecte", "papillon", "chauve-souris")
                || dewey.startsWith("59")) {
            return "Animaux";
        }

        // Sciences & Espace & Corps humain
        if (corpusMatches(corpus, "espace", "astronaute", "fusee", "lune", "etoiles", "etoile",
                "planete", "systeme solaire", "robot", "technologie", "invention", "machine",
                "corps humain", "sante", "cerveau", "squelette", "microbe", "volcan", "astronomie",
                "experience scientifique", "sciences")
                || dewey.startsWith("52") || dewey.startsWith("60") || dewey.startsWith("61")) {
            return "Sciences & Espace";
        }

        // Nature & Écologie
        if (corpusMatches(corpus, "nature", "ecologie", "foret", "arbre", "mer", "ocean", "plage",
                "montagne", "fleuve", "riviere", "fleur", "plante", "potager", "saisons", "printemps",
                "ete", "automne", "hiver", "meteo", "climat", "environnement", "pollution", "biodiversite")
                || dewey.startsWith("57") || dewey.startsWith("58")) {
            return "Nature & Écologie";
        }

        // Imaginaire & Magie & Fantastique
        if (corpusMatches(corpus, "magie", "magicien", "sorciere", "sorcier", "dragon", "fee",
                "monstre", "fantome", "ogre", "vampire", "loup-garou", "extraterrestre", "ovni",
                "super-heros", "elfe", "lutin", "creature", "fantastique", "harry potter", "sortilege")) {
            return "Imaginaire & Magie";
        }

        // Humour & Bêtises
        if (corpusMatches(corpus, "humour", "drole", "rigolo", "farce", "betise", "gag", "rire",
                "mortelle adele", "ducobu", "titeuf", "blague", "fessees", "clown")) {
            return "Humour & Bêtises";
        }

        // Contes, Mythes & Légendes
        if (corpusMatches(corpus, "mythologie", "mythe", "dieu", "deesse", "olympe", "troie",
                "hercule", "zeus", "ulysse", "minotaure", "legende", "fable", "prince", "princesse",
                "blanche-neige", "cendrillon", "petit chaperon")) {
            return "Contes, Mythes & Légendes";
        }

        // Aventure & Exploration
        if (corpusMatches(corpus, "aventure", "expedition", "explorateur", "ile", "tresor", "pirate",
                "marin", "naufrage", "jungle", "desert", "voyage", "tour du monde", "survie", "brigands")) {
            return "Aventure & Exploration";
        }

        // Arts, Musique & Sports
        if (corpusMatches(corpus, "musique", "chant", "guitare", "piano", "danse", "danseuse",
                "ballet", "peinture", "dessin", "tableau", "musee", "sport", "football", "foot",
                "judo", "natation", "velo", "tennis", "equitation", "cirque", "theatre")
                || dewey.startsWith("78") || dewey.startsWith("79")) {
            return "Arts, Musique & Sports";
        }

        // Vie quotidienne & École (thème par défaut très répandu en littérature jeunesse)
        return "Vie quotidienne & École";
    }

    // =====================================================================
    //  3. Détection du Niveau Scolaire
    // =====================================================================

    /**
     * Vérifie si le niveau d'un livre correspond au filtre sélectionné par l'utilisateur.
     * Prend en compte la hiérarchie scolaire française (Cycles et Classes) ainsi que les
     * différentes manières dont les données peuvent être enregistrées en base :
     * <ul>
     *   <li>Cycle 1 = Maternelle (TPS, PS, MS, GS)</li>
     *   <li>Cycle 2 = CP, CE1, CE2</li>
     *   <li>Cycle 3 = CM1, CM2 (et 6ème)</li>
     * </ul>
     * Un livre classé "Cycle 2 (CP-CE2)" sera donc visible si l'utilisateur filtre par
     * "Cycle 2", "CP", "CE1" ou "CE2".
     * Inversement, un filtre "Cycle 2" affichera les livres "CP", "CE1", "CE2" et "Cycle 2 (CP-CE2)".
     */
    public static boolean matchesNiveau(String bookNiveau, String filterNiveau) {
        if (filterNiveau == null || filterNiveau.isBlank()
                || filterNiveau.equalsIgnoreCase("Tous")
                || filterNiveau.equalsIgnoreCase("Tous les niveaux")
                || filterNiveau.equalsIgnoreCase("Tous niveaux")) {
            return true;
        }

        if (bookNiveau == null || bookNiveau.isBlank()) {
            return false;
        }

        String b = bookNiveau.trim().toLowerCase();
        String f = filterNiveau.trim().toLowerCase();

        // 1. Égalité stricte ou livre universel "Tous niveaux"
        if (b.equalsIgnoreCase(f) || b.equals("tous niveaux") || b.equals("tous") || b.equals("tout niveau")) {
            return true;
        }

        // 2. Détermination de la portée du livre
        boolean bookHasC1 = b.contains("cycle 1") || b.contains("maternelle") || b.contains("ps") || b.contains("ms") || b.contains("gs");
        boolean bookHasC2 = b.contains("cycle 2") || b.contains("cp-ce2") || b.contains("cp - ce2") || b.contains("cp, ce1, ce2");
        boolean bookHasC3 = b.contains("cycle 3") || b.contains("cm1-cm2") || b.contains("cm1 - cm2") || b.contains("cm1, cm2");

        boolean bookHasCP = bookHasC2 || b.contains("cp");
        boolean bookHasCE1 = bookHasC2 || b.contains("ce1");
        boolean bookHasCE2 = bookHasC2 || b.contains("ce2");

        boolean bookHasCM1 = bookHasC3 || b.contains("cm1");
        boolean bookHasCM2 = bookHasC3 || b.contains("cm2");

        // 3. Détermination du filtre demandé
        // --- FILTRES DE CYCLES ---
        if (f.contains("cycle 1") || f.equals("maternelle")) {
            return bookHasC1 || b.contains("maternelle");
        }
        if (f.contains("cycle 2")) {
            return bookHasC2 || b.contains("cp") || b.contains("ce1") || b.contains("ce2");
        }
        if (f.contains("cycle 3")) {
            return bookHasC3 || b.contains("cm1") || b.contains("cm2") || b.contains("6e") || b.contains("6ème");
        }

        // --- FILTRES DE CLASSES SPÉCIFIQUES ---
        if (f.equals("cp")) {
            return bookHasCP;
        }
        if (f.equals("ce1")) {
            return bookHasCE1;
        }
        if (f.equals("ce2")) {
            return bookHasCE2;
        }
        if (f.equals("cm1")) {
            return bookHasCM1;
        }
        if (f.equals("cm2")) {
            return bookHasCM2;
        }

        // Fallback comparaison textuelle
        return b.contains(f) || f.contains(b);
    }

    private String detectNiveau(String titre, String collection, List<String> subjects,
                                String ageMention, String corpus, int pageCount, String genre) {

        String age = normalize(ageMention);
        String col = normalize(collection);
        String tit = normalize(titre);

        // 1. Analyse prioritaire de la mention explicite d'âge CNLJ / éditeur
        if (!age.isEmpty()) {
            if (age.contains("3 ans") || age.contains("4 ans") || age.contains("5 ans")
                    || age.contains("de 3 a 6") || age.contains("3-6") || age.contains("maternelle")
                    || age.contains("tps") || age.contains("ps") || age.contains("ms") || age.contains("gs")) {
                return "Maternelle";
            }
            if (age.contains("6-9") || age.contains("6 a 9") || age.contains("6-8") || age.contains("6 a 8")) {
                return "CE1";
            }
            if (age.contains("6 ans") || age.contains("debut cp") || age.contains("cp")) {
                return "CP";
            }
            if (age.contains("7 ans") || age.contains("ce1") || age.contains("7-9") || age.contains("7 a 9")) {
                return "CE1";
            }
            if (age.contains("8 ans") || age.contains("ce2") || age.contains("8-10") || age.contains("8 a 10")) {
                return "CE2";
            }
            if (age.contains("9 ans") || age.contains("10 ans") || age.contains("9-11") || age.contains("9 a 11") || age.contains("cm1")) {
                return "CM1";
            }
            if (age.contains("11 ans") || age.contains("12 ans") || age.contains("cm2")
                    || age.contains("college") || age.contains("6e")) {
                return "CM2";
            }
        }

        // 2. Mention explicite du niveau dans le titre, la collection ou le corpus
        if (matchesWord(tit, "cm1") || matchesWord(col, "cm1") || tit.contains("monstre du cm1")
                || tit.contains("je suis en cm1") || tit.contains("sami et julie cm1")) {
            return "CM1";
        }
        if (matchesWord(tit, "cm2") || matchesWord(col, "cm2")
                || tit.contains("je suis en cm2") || tit.contains("sami et julie cm2")) {
            return "CM2";
        }
        if (matchesWord(tit, "ce2") || matchesWord(col, "ce2") || tit.contains("sami et julie ce2")
                || tit.contains("je suis en ce2") || col.contains("niveau 3")) {
            return "CE2";
        }
        if (matchesWord(tit, "ce1") || matchesWord(col, "ce1") || tit.contains("sami et julie ce1")
                || tit.contains("je suis en ce1") || col.contains("fin de cp")
                || col.contains("niveau 2") || col.contains("premieres lectures") || col.contains("premières lectures")) {
            return "CE1";
        }
        if (matchesWord(tit, "cp") || matchesWord(col, "cp") || tit.contains("sami et julie cp")
                || tit.contains("je suis en cp") || col.contains("niveau 1") || col.contains("debut de cp")
                || col.contains("milieu de cp") || tit.contains("j'apprends a lire") || tit.contains("j'apprends à lire")) {
            return "CP";
        }
        if (matchesWord(tit, "maternelle") || matchesWord(col, "maternelle") || tit.contains("petite section")
                || tit.contains("moyenne section") || tit.contains("grande section")) {
            return "Maternelle";
        }

        // 3. Collections, séries, auteurs et personnages emblématiques
        if (tit.contains("babar") || tit.contains("trotro") || tit.contains("t'choupi") || tit.contains("tchoupi")
                || tit.contains("petit ours brun") || tit.contains("petit remi") || tit.contains("solange la mesange")
                || tit.contains("droles de petites betes") || tit.contains("drôles de petites bêtes")
                || corpus.contains("antoon krings") || corpus.contains("beatrix potter")
                || tit.contains("tout-petits") || tit.contains("imagerie des tout-petits")
                || tit.contains("pere noel") || tit.contains("père noël") || tit.contains("boucle d'or")
                || tit.contains("tetine de nina") || tit.contains("tétine de nina") || tit.contains("bebe qui crie")
                || tit.contains("bébé qui crie") || tit.contains("calinours") || tit.contains("dora")
                || tit.contains("papa ours") || tit.contains("petit chat") || tit.contains("taupe sous la neige")) {
            return "Maternelle";
        }

        if (col.contains("folio junior") || col.contains("castor poche") || col.contains("rageot")
                || tit.contains("enfants de la resistance") || tit.contains("je lis des histoires vraies")
                || tit.contains("images doc") || tit.contains("roald dahl") || tit.contains("cabane magique")
                || tit.contains("fables de la fontaine") || tit.contains("chair de poule")
                || corpus.contains("brisou-pellen")) {
            return "CM1";
        }

        if (tit.contains("harry potter") || tit.contains("seigneur des anneaux") || tit.contains("percy jackson")
                || tit.contains("treize a la douzaine") || tit.contains("bill nye") || tit.contains("tout au fond de l'ocean")
                || tit.contains("tout au fond de l'océan") || tit.contains("jules verne") || tit.contains("le petit prince")
                || corpus.contains("frank bunker gilbreth") || corpus.contains("ernestine gilbreth")) {
            return "CM2";
        }

        if (tit.contains("tom-tom") || tit.contains("tom tom") || tit.contains("mortelle adele")
                || tit.contains("mortelle adèle") || tit.contains("anatole latuile") || tit.contains("boule et bill")
                || tit.contains("ducobu") || tit.contains("les deux terreurs") || tit.contains("chemin des ecoliers")
                || corpus.contains("jacqueline cohen") || corpus.contains("evelyne reberg")) {
            return "CE2";
        }

        if (col.contains("folio cadet") || tit.contains("jaime lire") || tit.contains("j'aime lire")) {
            return "CE1";
        }

        if (col.contains("p'tits docs") || col.contains("ptits docs") || col.contains("mes p'tits docs")
                || tit.contains("les trois brigands") || tit.contains("mimi cracra") || tit.contains("une figue de reve")
                || tit.contains("chemin de l'ecole") || tit.contains("quand je saurai lire")
                || tit.contains("pierre et le loup") || tit.contains("pierre et le loup")
                || tit.contains("zoe est jalouse") || tit.contains("surprise pour la maitresse") || tit.contains("dingo et max")) {
            return "CP";
        }

        // 4. Spécificités par genre et pagination
        if ("Manga".equals(genre)) {
            return "CM2";
        }
        if ("BD".equals(genre)) {
            if (tit.contains("enfants de la resistance") || tit.contains("tintin") || tit.contains("asterix")) {
                return "CM1";
            }
            return "CE2"; // Niveau médian idéal pour les BDs jeunesse
        }
        if ("Album".equals(genre)) {
            if (pageCount > 0 && pageCount <= 28) return "Maternelle";
            return "CP";
        }

        // 5. Par défaut selon volume
        if (pageCount > 150) return "CM2";
        if (pageCount > 90) return "CM1";
        if (pageCount > 50) return "CE2";
        if (pageCount > 0 && pageCount <= 36) return "CP";

        return "Cycle 2 (CP-CE2)";
    }

    // =====================================================================
    //  Utilitaires sémantiques
    // =====================================================================

    private String buildCorpus(String titre, String auteur, String editeur,
                               String collection, List<String> subjects, String description) {
        StringBuilder sb = new StringBuilder();
        if (titre != null) sb.append(titre).append(" ");
        if (auteur != null) sb.append(auteur).append(" ");
        if (editeur != null) sb.append(editeur).append(" ");
        if (collection != null) sb.append(collection).append(" ");
        if (description != null) sb.append(description).append(" ");
        if (subjects != null) {
            for (String s : subjects) {
                if (s != null) sb.append(s).append(" ");
            }
        }
        return normalize(sb.toString());
    }

    private boolean hasSubjectMatch(List<String> subjects, String... keywords) {
        if (subjects == null || subjects.isEmpty()) return false;
        for (String sub : subjects) {
            if (sub == null) continue;
            String norm = normalize(sub);
            for (String kw : keywords) {
                if (matchesWord(norm, normalize(kw))) return true;
            }
        }
        return false;
    }

    private boolean corpusMatches(String corpus, String... keywords) {
        for (String kw : keywords) {
            if (matchesWord(corpus, normalize(kw))) return true;
        }
        return false;
    }

    private boolean matchesWord(String text, String keyword) {
        if (text == null || keyword == null || keyword.isEmpty()) return false;
        // Si le mot clé contient des espaces (ex: "corps humain", "1939-1945")
        if (keyword.contains(" ") || keyword.contains("-")) {
            return text.contains(keyword);
        }
        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(keyword) + "\\b").matcher(text).find();
    }

    private String normalize(String str) {
        if (str == null) return "";
        String norm = Normalizer.normalize(str, Normalizer.Form.NFD);
        norm = norm.replaceAll("\\p{M}", ""); // retire les accents
        return norm.toLowerCase(Locale.ROOT).trim();
    }
}
