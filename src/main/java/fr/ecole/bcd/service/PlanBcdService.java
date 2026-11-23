package fr.ecole.bcd.service;

import fr.ecole.bcd.model.Livre;
import fr.ecole.bcd.model.ZoneBcd;
import fr.ecole.bcd.model.ZoneBcd.CategorieMobilier;
import fr.ecole.bcd.model.ZoneBcd.FaceBcd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Service gérant le plan 2D et la maquette 3D de la BCD Albert Camus (Meudon).
 *
 * <p>Fournit la liste des 11 zones de rangement numérotées, du mobilier complémentaire
 * (chaises, canapé, poufs, bureaux) et les règles intelligentes de repérage et reclassement
 * spatial automatique pour la douchette et les élèves.</p>
 */
public class PlanBcdService {

    public static final double ROOM_WIDTH = 9.0;  // 9,0 mètres Est-Ouest
    public static final double ROOM_DEPTH = 7.5;  // 7,5 mètres Nord-Sud

    private static final List<ZoneBcd> ALL_ZONES;

    static {
        List<ZoneBcd> list = new ArrayList<>();

        // 1. Étagère Contes / Poésie & Romans (Double-face verticale)
        list.add(new ZoneBcd(
                "1", 1,
                "Étagère Contes / Poésie & Romans",
                "Contes / Romans A-P",
                "Étagère double-face verticale",
                "Structure jaune, tablettes grises",
                CategorieMobilier.SHELF,
                4.1, 4.7, 0.1, 1.8, 1.45,
                "ROM A-P / CONTE / POE", 280, "shelf-mixed-yellow",
                List.of(
                        new FaceBcd("Face gauche", "Contes / Poésie (mixte : étagères droites + présentoir incliné)"),
                        new FaceBcd("Face droite", "Romans classés par ordre alphabétique d'auteur (A à P)")
                ),
                "1 contes poesie poésie romans a p alphabetique étagère jaune perrault grimm andersen"
        ));

        // 2. Grand bac à Bandes Dessinées
        list.add(new ZoneBcd(
                "2", 2,
                "Grand bac à Bandes Dessinées",
                "Bac BD & Mangas",
                "Grand bac à livres BD vertical (2 niveaux en escalier)",
                "Structure jaune, caisses blanches/grises",
                CategorieMobilier.BIN,
                5.8, 6.7, 0.1, 1.9, 1.15,
                "BD", 320, "bin-bd-tiered",
                List.of(
                        new FaceBcd("Face gauche & Face droite", "Bandes Dessinées : Astérix, Tintin, Lucky Luke, Cédric, Boule & Bill, Gaston, Garfield, Divers BD & Mangas")
                ),
                "2 bd bandes dessinees dessinées asterix astérix tintin lucky luke cedric boule bill gaston garfield bac mangas"
        ));

        // 3. Grand linéaire mural (Romans & Documentaires)
        list.add(new ZoneBcd(
                "3", 3,
                "Grand linéaire mural (Romans & Documentaires)",
                "Mur Romans S-Z & Docs Histoire",
                "Bibliothèque murale simple-face (tout le mur droit)",
                "Structure jaune, tablettes grises",
                CategorieMobilier.SHELF,
                8.3, 8.9, 0.2, 7.3, 1.65,
                "ROM S-Z / DOC 300-900", 550, "shelf-wall-yellow",
                List.of(
                        new FaceBcd("Partie haute (vers le couloir)", "Romans : Harry Potter, Le Seigneur des Anneaux, Folio Cadet & Junior, Star Wars, Le Petit Nicolas, Mangas, Milan Poche, Bibliothèque rose, Geronimo, fin d'alphabet (S à Z)"),
                        new FaceBcd("Partie basse (vers les fenêtres)", "Documentaires : Vie pratique, sociale et culturelle (300-399), Histoire de France, Géographie, Moyen Âge, Histoire du Monde (900-999), Atlas, La Hulotte")
                ),
                "3 romans harry potter seigneur des anneaux folio cadet junior star wars mangas geronimo documentaires vie pratique sociale culturelle 300 399 histoire france geographie géographie moyen age âge 900 999 atlas hulotte s z"
        ));

        // 4. Îlot central droit (Documentaires & Présentoir)
        list.add(new ZoneBcd(
                "4", 4,
                "Îlot central droit (Documentaires & Présentoir)",
                "Docs Sciences/Arts & Romans Délires",
                "Étagère double-face horizontale",
                "Structure jaune, tablettes grises",
                CategorieMobilier.SHELF,
                6.1, 7.7, 3.2, 3.8, 1.45,
                "DOC 600-700 / ROM PRES", 260, "shelf-mixed-yellow-h",
                List.of(
                        new FaceBcd("Face avant (haut)", "Documentaires : Sciences, techniques, santé et industrie (600), Sports, Arts culturels (Peinture, Musique 700), Loisirs créatifs"),
                        new FaceBcd("Face arrière (bas)", "Présentoir incliné Romans (Chair de poule, Délires, sélections)")
                ),
                "4 documentaires sciences techniques sante santé sports arts culturels loisirs creatifs créatifs presentoir présentoir romans chair de poule delires délires 600 700"
        ));

        // 5. Petit bac central
        list.add(new ZoneBcd(
                "5", 5,
                "Petit bac central",
                "Bac blanc central",
                "Bac à livres d'appoint",
                "Bac blanc sur pieds métalliques jaunes",
                CategorieMobilier.BIN,
                5.3, 5.9, 3.2, 3.8, 0.85,
                "BAC DIVERS", 90, "bin-white-yellow",
                List.of(
                        new FaceBcd("Contenu", "Livres en tout genre, albums et petits formats")
                ),
                "5 bac blanc livres en tout genre albums petits formats appoint central"
        ));

        // 6. Îlot central gauche (Albums & Revues)
        list.add(new ZoneBcd(
                "6", 6,
                "Îlot central gauche (Albums & Revues)",
                "Albums A-T & Revues Jeunesse",
                "Étagère double-face horizontale",
                "Structure jaune, tablettes grises",
                CategorieMobilier.SHELF,
                3.4, 5.0, 3.2, 3.8, 1.45,
                "ALB A-T / REV", 310, "shelf-yellow",
                List.of(
                        new FaceBcd("Face haut", "Albums classés par ordre alphabétique d'auteur (A à T)"),
                        new FaceBcd("Face bas", "Revues & Magazines : Je Bouquine, Images Doc, Astrapi, J'aime Lire, Le Petit Léonard, Histoires Vraies, I Love English, Superscope")
                ),
                "6 albums a t revues magazines je bouquine images doc astrapi j aime lire petit leonard léonard histoires vraies english superscope presse"
        ));

        // 7. Bac Livres jeux
        list.add(new ZoneBcd(
                "7", 7,
                "Bac Livres jeux",
                "Bac Livres jeux & Énigmes",
                "Bac à livres sur pieds",
                "Caisse verte sur pieds blancs",
                CategorieMobilier.BIN,
                2.3, 3.2, 3.1, 3.9, 0.78,
                "JEUX", 70, "bin-green",
                List.of(
                        new FaceBcd("Contenu", "Livres jeux, cherche-et-trouve, énigmes, Charlie, devinettes")
                ),
                "7 livres jeux bac vert cherche trouve charlie enigmes énigmes devinettes"
        ));

        // 8. Bibliothèque Verte (Sciences & Premières lectures)
        list.add(new ZoneBcd(
                "8", 8,
                "Bibliothèque Verte (Sciences & Premières lectures)",
                "Biblio Verte (Sciences 500 / Albums Thème)",
                "Étagère double-face verticale",
                "Structure VERTE, tablettes blanches",
                CategorieMobilier.SHELF,
                5.6, 6.2, 5.4, 7.4, 1.55,
                "DOC 500 / ALB THEME", 340, "shelf-green",
                List.of(
                        new FaceBcd("Face droite", "Documentaires Sciences & Nature (Dewey 500-594) : Dinosaures, Animaux, Nature, Atlas des 5-8 ans"),
                        new FaceBcd("Face gauche", "Albums thématiques & Premières lectures : Princes & princesses, Sorcières, Noël, Babar, Martine & Caroline, Walt Disney, Petits albums & romans CP/CE1")
                ),
                "8 verte vert documentaires sciences nature dewey 500 594 dinosaures dino animaux atlas albums princes princesses sorcieres sorcières noel noël babar martine disney cp ce1 premieres premières lectures"
        ));

        // 9. Bacs en bois CP/CE1 (sous radiateur)
        list.add(new ZoneBcd(
                "9", 9,
                "Bacs en bois CP/CE1 (sous radiateur)",
                "Bacs bois Fenêtres (CP/CE1)",
                "2 petits bacs en bois contre le mur des fenêtres",
                "Bois clair / naturel (4 compartiments chacun)",
                CategorieMobilier.BIN,
                4.4, 5.5, 6.8, 7.4, 0.65,
                "1ERE LECT / DOC CP", 110, "bin-wood-double",
                List.of(
                        new FaceBcd("Bac gauche (sur pieds)", "Premières lectures (petits albums débutants)"),
                        new FaceBcd("Bac droit (au sol)", "Documentaires CP/CE1 (Wapiti Monde Sauvage, Talpa, etc.)")
                ),
                "9 bacs bois premieres premières lectures documentaires cp ce1 wapiti talpa radiateur fenêtres"
        ));

        // 10. Bac roulant CP/CE1
        list.add(new ZoneBcd(
                "10", 10,
                "Bac roulant CP/CE1",
                "Bac roulant Petits Romans CP",
                "Bac à livres double-face sur roulettes (6 compartiments)",
                "Structure jaune, caisse grise",
                CategorieMobilier.BIN,
                3.5, 4.8, 5.7, 6.3, 0.85,
                "CP-CE1", 150, "bin-cp-ce1",
                List.of(
                        new FaceBcd("Contenu (6 compartiments)", "CP/CE1 : Mes 1ers J'aime Lire, Je lis déjà, Youpi, Petits romans, Les Belles Histoires, Crocoscope, Mini Rose")
                ),
                "10 cp ce1 mes 1ers j aime lire youpi belles histoires petits romans crocoscope mini rose bac roulettes rouleau"
        ));

        // 11. Présentoir Nouveautés & Sur place
        list.add(new ZoneBcd(
                "11", 11,
                "Présentoir Nouveautés & Sur place",
                "Nouveautés & Pop-up Sur Place",
                "Étagère présentoir double-face verticale (tablettes inclinées)",
                "Structure jaune, tablettes grises inclinées",
                CategorieMobilier.SHELF,
                2.5, 3.1, 5.4, 7.4, 1.50,
                "NOUV / SUR PLACE", 140, "shelf-slanted-yellow",
                List.of(
                        new FaceBcd("Face droite (vers bac 10)", "Nouveautés : albums récents, documentaires phares, Le Petit Prince, Ma vallée, Légendes"),
                        new FaceBcd("Face gauche (vers table)", "À consulter sur place : grands formats, beaux livres et pop-up géants (Dino Géant, Une histoire sombre...)")
                ),
                "11 nouveautes nouveautés a consulter sur place pop up dino geant géant beaux livres grands formats presentoir présentoir petit prince"
        ));

        // --- Mobilier d'ambiance et de travail ---

        list.add(new ZoneBcd(
                "m-chaises", null,
                "3 Chaises d'attente / lecture",
                "Chaises",
                "Mobilier d'assise",
                "1 chaise verte + 2 chaises bleues",
                CategorieMobilier.SEATING,
                2.4, 3.8, 0.2, 0.7, 0.80,
                "", 0, "chairs-3",
                List.of(new FaceBcd("Emplacement", "Alignées contre la verrière jaune, entre la porte d'entrée et l'étagère 1.")),
                "chaises assise verriere couloir entree entrée"
        ));

        list.add(new ZoneBcd(
                "m-canape", null,
                "Canapé / Fauteuil vert (Coin BD)",
                "Canapé vert",
                "Mobilier d'assise",
                "Tissu vert foncé confortable",
                CategorieMobilier.SEATING,
                4.9, 5.6, 0.2, 0.8, 0.75,
                "", 0, "sofa-green",
                List.of(new FaceBcd("Emplacement", "Coin lecture confortable coincé entre l'étagère 1 et le bac à BD 2.")),
                "canape canapé fauteuil vert coin bd lecture detente détente"
        ));

        list.add(new ZoneBcd(
                "m-poufs", null,
                "Poufs verts & Tapis de lecture",
                "Poufs & Tapis",
                "Coin lecture détente",
                "Poufs vert pomme + tapis bleu à motifs",
                CategorieMobilier.SEATING,
                6.6, 7.9, 6.6, 7.3, 0.42,
                "", 0, "poufs-green",
                List.of(new FaceBcd("Emplacement", "Angle bas-droit sous les fenêtres, devant la bibliothèque verte (8) et les documentaires (3).")),
                "poufs verts tapis bleu coin lecture detente détente fenêtres"
        ));

        list.add(new ZoneBcd(
                "m-bureau", null,
                "Bureau PC (Gestion BCD)",
                "Bureau PC",
                "Poste informatique / Accueil",
                "Structure jaune, plateau clair, écran PC",
                CategorieMobilier.DESK,
                0.4, 1.4, 2.6, 4.4, 0.76,
                "", 0, "desk-pc",
                List.of(new FaceBcd("Usage", "Poste de gestion informatisée de la BCD (prêts, retours, catalogage, douchette).")),
                "bureau pc ordinateur gestion accueil scanner douchette"
        ));

        list.add(new ZoneBcd(
                "m-table", null,
                "Table de travail & Tri",
                "Table de Tri",
                "Table scolaire / Zone de tri",
                "Pieds jaunes, plateau gris, caisses transparentes dessous",
                CategorieMobilier.DESK,
                0.6, 1.6, 5.5, 7.1, 0.75,
                "", 0, "table-work",
                List.of(new FaceBcd("Usage", "Table de tri des livres, réparation, étiquetage et stockage des caisses transparentes.")),
                "table travail tri livres caisses transparentes reparation réparation"
        ));

        ALL_ZONES = Collections.unmodifiableList(list);
    }

    /**
     * Retourne l'ensemble de tous les éléments du plan (11 zones + mobilier).
     */
    public List<ZoneBcd> getAllZones() {
        return ALL_ZONES;
    }

    /**
     * Retourne uniquement les 11 zones de rangement numérotées (1 à 11).
     */
    public List<ZoneBcd> getNumberedZones() {
        return ALL_ZONES.stream()
                .filter(ZoneBcd::hasNumero)
                .toList();
    }

    /**
     * Retourne le mobilier complémentaire (chaises, canapé, poufs, bureau, table).
     */
    public List<ZoneBcd> getMiscFurniture() {
        return ALL_ZONES.stream()
                .filter(z -> !z.hasNumero())
                .toList();
    }

    /**
     * Recherche une zone par son identifiant unique ("1", "2", ... "m-bureau").
     */
    public Optional<ZoneBcd> findById(String id) {
        if (id == null) return Optional.empty();
        return ALL_ZONES.stream()
                .filter(z -> z.getId().equalsIgnoreCase(id.trim()))
                .findFirst();
    }

    /**
     * Recherche une zone par son numéro de rayon (1 à 11).
     */
    public Optional<ZoneBcd> findByNumero(int numero) {
        return ALL_ZONES.stream()
                .filter(z -> z.getNumero() != null && z.getNumero() == numero)
                .findFirst();
    }

    /**
     * Détermine intelligemment sur quel meuble / étagère un livre doit être rangé
     * dans la BCD Albert Camus, selon son genre, son auteur, sa cote Dewey, sa collection
     * et son niveau de lecture.
     *
     * @param livre l'ouvrage à localiser
     * @return la zone de rangement la plus appropriée
     */
    public ZoneBcd determineZoneForLivre(Livre livre) {
        if (livre == null) {
            return findById("5").orElse(ALL_ZONES.getFirst());
        }

        String genre = livre.getGenre() != null ? livre.getGenre().trim() : "";
        String titre = livre.getTitre() != null ? livre.getTitre().toLowerCase(Locale.FRENCH) : "";
        String auteur = livre.getAuteur() != null ? livre.getAuteur().trim().toUpperCase(Locale.FRENCH) : "";
        String dewey = livre.getDewey() != null ? livre.getDewey().trim() : "";
        String niveau = livre.getNiveau() != null ? livre.getNiveau().trim() : "";
        String theme = livre.getTheme() != null ? livre.getTheme().trim().toLowerCase(Locale.FRENCH) : "";

        // 1. Bandes Dessinées & Mangas -> Zone 2 (Grand bac BD)
        if ("BD".equalsIgnoreCase(genre) ||
                titre.contains("astérix") || titre.contains("asterix") ||
                titre.contains("tintin") || titre.contains("lucky luke") ||
                titre.contains("cédric") || titre.contains("cedric") ||
                titre.contains("boule et bill") || titre.contains("gaston") ||
                titre.contains("garfield") || titre.contains("manga") ||
                dewey.startsWith("741.5")) {
            return findById("2").orElse(ALL_ZONES.get(1));
        }

        // 2. Livres jeux / Cherche et trouve / Énigmes -> Zone 7
        if (titre.contains("cherche et trouve") || titre.contains("où est charlie") ||
                titre.contains("livre-jeu") || titre.contains("énigme") || titre.contains("enigme")) {
            return findById("7").orElse(ALL_ZONES.get(6));
        }

        // 3. Pop-up géants ou grands formats à consulter sur place -> Zone 11
        if (titre.contains("pop-up") || titre.contains("géant") || titre.contains("sur place") ||
                titre.contains("petit prince") || titre.contains("ma vallée")) {
            return findById("11").orElse(ALL_ZONES.get(10));
        }

        // 4. Revues & Magazines -> Zone 10 ou Zone 6
        if ("Revue / Magazine".equalsIgnoreCase(genre)) {
            if (titre.contains("mes premiers j'aime lire") || titre.contains("je lis déjà") ||
                    titre.contains("youpi") || "CP".equalsIgnoreCase(niveau) || "Maternelle".equalsIgnoreCase(niveau)) {
                return findById("10").orElse(ALL_ZONES.get(9)); // Bac roulant CP/CE1
            }
            return findById("6").orElse(ALL_ZONES.get(5)); // Îlot central gauche (Face bas)
        }

        // 5. Documentaires
        if ("Documentaire".equalsIgnoreCase(genre)) {
            // Débutants CP/CE1 sous radiateur
            if ("CP".equalsIgnoreCase(niveau) || "CE1".equalsIgnoreCase(niveau)) {
                return findById("9").orElse(ALL_ZONES.get(8));
            }
            // Sciences de la nature & Animaux & Dinosaures (Dewey 500-594) -> Zone 8 (Bibliothèque verte)
            if (dewey.startsWith("5") || theme.contains("science") || theme.contains("animal") ||
                    titre.contains("dinosaure") || titre.contains("nature") || titre.contains("espace")) {
                return findById("8").orElse(ALL_ZONES.get(7));
            }
            // Histoire, Géographie, Société (Dewey 300 ou 900) -> Zone 3 (Grand linéaire mural bas)
            if (dewey.startsWith("9") || dewey.startsWith("3") || theme.contains("histoire") ||
                    titre.contains("histoire") || titre.contains("atlas") || titre.contains("géographie") || titre.contains("geographie")) {
                return findById("3").orElse(ALL_ZONES.get(2));
            }
            // Techniques (600), Arts & Sports (700) -> Zone 4 (Îlot central droit)
            return findById("4").orElse(ALL_ZONES.get(3));
        }

        // 6. Contes, Poésie & Théâtre -> Zone 1 (Face gauche)
        if ("Conte".equalsIgnoreCase(genre) || "Poésie / Théâtre".equalsIgnoreCase(genre) ||
                theme.contains("conte") || titre.contains("conte") || titre.contains("poésie")) {
            return findById("1").orElse(ALL_ZONES.get(0));
        }

        // 7. Romans
        if ("Roman".equalsIgnoreCase(genre)) {
            // Séries cultes ou grands auteurs -> Zone 3 (Grand linéaire haut)
            if (titre.contains("harry potter") || titre.contains("seigneur des anneaux") ||
                    titre.contains("star wars") || titre.contains("petit nicolas") ||
                    titre.contains("geronimo") || titre.contains("folio cadet")) {
                return findById("3").orElse(ALL_ZONES.get(2));
            }
            // Chair de poule / Délires -> Zone 4 (Présentoir incliné)
            if (titre.contains("chair de poule") || titre.contains("délires") || titre.contains("delires")) {
                return findById("4").orElse(ALL_ZONES.get(3));
            }
            // Petits romans débutants CP -> Zone 10 (Bac roulant)
            if ("CP".equalsIgnoreCase(niveau) || "Maternelle".equalsIgnoreCase(niveau)) {
                return findById("10").orElse(ALL_ZONES.get(9));
            }
            // Classement alphabétique selon le NOM de famille de l'auteur
            char firstChar = getNomFamilleLettre(auteur);
            if (firstChar >= 'S' && firstChar <= 'Z') {
                return findById("3").orElse(ALL_ZONES.get(2)); // Mur droit S à Z
            } else {
                return findById("1").orElse(ALL_ZONES.get(0)); // Étagère 1 A à P
            }
        }

        // 8. Albums
        if ("Album".equalsIgnoreCase(genre)) {
            // Petits albums CP / Premières lectures
            if ("CP".equalsIgnoreCase(niveau) || "Maternelle".equalsIgnoreCase(niveau)) {
                if (titre.contains("belles histoires") || titre.contains("crocoscope")) {
                    return findById("10").orElse(ALL_ZONES.get(9));
                }
                return findById("9").orElse(ALL_ZONES.get(8)); // Bacs en bois fenêtres
            }
            // Albums thématiques (Babar, Sorcières, Princes, Noël, Disney) -> Zone 8 (Biblio verte)
            if (titre.contains("babar") || titre.contains("martine") || titre.contains("sorcière") ||
                    titre.contains("sorciere") || titre.contains("princesse") || titre.contains("noël") ||
                    titre.contains("noel") || titre.contains("disney")) {
                return findById("8").orElse(ALL_ZONES.get(7));
            }
            // Grand linéaire alphabétique A à T -> Zone 6 (Îlot central gauche)
            return findById("6").orElse(ALL_ZONES.get(5));
        }

        // Défaut
        return findById("5").orElse(ALL_ZONES.get(4));
    }

    /**
     * Extrait la première lettre du nom de famille d'un auteur pour le rangement alphabétique.
     */
    public static char getNomFamilleLettre(String auteur) {
        if (auteur == null || auteur.isBlank()) return 'A';
        String clean = auteur.trim().toUpperCase(Locale.FRENCH);
        if (clean.contains(",")) {
            String beforeComma = clean.substring(0, clean.indexOf(',')).trim();
            if (!beforeComma.isEmpty()) return beforeComma.charAt(0);
        }
        String[] tokens = clean.split("\\s+");
        if (tokens.length > 1) {
            String last = tokens[tokens.length - 1];
            if (!last.isEmpty()) return last.charAt(0);
        }
        return clean.charAt(0);
    }
}
