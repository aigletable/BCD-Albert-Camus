package fr.ecole.bcd.service;

import fr.ecole.bcd.model.EtiquetteItem;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Service de génération de codes-barres vectoriels (Code 39 standard) et
 * de planches d'étiquettes A4 prêtes pour l'impression haute précision en HTML / CSS.
 * <p>
 * Ce service implémente l'ensemble de la chaîne de production des étiquettes de rayonnage :
 * </p>
 * <ul>
 *   <li><b>Symbologie Code 39</b> : encodage autonome de chiffres, lettres majuscules et symboles sans police externe.</li>
 *   <li><b>Rendu vectoriel pur SVG</b> : aucun pixel flou lors de l'impression physique à 300 ou 600 DPI.</li>
 *   <li><b>Compatibilité planches standard</b> : gabarits physiques rigoureusement calibrés
 *       (Avery 24, 21, 30, 14 étiquettes, et tranches fines 65 étiquettes).</li>
 *   <li><b>Offset de démarrage</b> : évite le gaspillage en permettant de reprendre l'impression sur des feuilles déjà entamées.</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class BarcodeService {

    /**
     * Table de motifs de barres pour la symbologie Code 39 (N = Narrow / Étroit, W = Wide / Large).
     */
    private static final Map<Character, String> CODE39_PATTERNS = new HashMap<>();

    static {
        CODE39_PATTERNS.put('0', "NNNWWNWNN");
        CODE39_PATTERNS.put('1', "WNNWNNNNW");
        CODE39_PATTERNS.put('2', "NNWWNNNNW");
        CODE39_PATTERNS.put('3', "WNWWNNNNN");
        CODE39_PATTERNS.put('4', "NNNWWNNNW");
        CODE39_PATTERNS.put('5', "WNNWWNNNN");
        CODE39_PATTERNS.put('6', "NNWWWNNNN");
        CODE39_PATTERNS.put('7', "NNNWNNWNW");
        CODE39_PATTERNS.put('8', "WNNWNNWNN");
        CODE39_PATTERNS.put('9', "NNWWNNWNN");
        CODE39_PATTERNS.put('A', "WNNNNWNNW");
        CODE39_PATTERNS.put('B', "NNWNNWNNW");
        CODE39_PATTERNS.put('C', "WNWNNWNNN");
        CODE39_PATTERNS.put('D', "NNNNWWNNW");
        CODE39_PATTERNS.put('E', "WNNNWWNNN");
        CODE39_PATTERNS.put('F', "NNWNWWNNN");
        CODE39_PATTERNS.put('G', "NNNNNWWNW");
        CODE39_PATTERNS.put('H', "WNNNNWWNN");
        CODE39_PATTERNS.put('I', "NNWNNWWNN");
        CODE39_PATTERNS.put('J', "NNNNWWWNN");
        CODE39_PATTERNS.put('K', "WNNNNNNWW");
        CODE39_PATTERNS.put('L', "NNWNNNNWW");
        CODE39_PATTERNS.put('M', "WNWNNNNWN");
        CODE39_PATTERNS.put('N', "NNNNWNNWW");
        CODE39_PATTERNS.put('O', "WNNNWNNWN");
        CODE39_PATTERNS.put('P', "NNWNWNNWN");
        CODE39_PATTERNS.put('Q', "NNNNNNWWW");
        CODE39_PATTERNS.put('R', "WNNNNNWWN");
        CODE39_PATTERNS.put('S', "NNWNNNWWN");
        CODE39_PATTERNS.put('T', "NNNNWNWWN");
        CODE39_PATTERNS.put('U', "WWNNNNNNW");
        CODE39_PATTERNS.put('V', "NWWNNNNNW");
        CODE39_PATTERNS.put('W', "WWWNNNNNN");
        CODE39_PATTERNS.put('X', "NWNNWNNNW");
        CODE39_PATTERNS.put('Y', "WWNNWNNNN");
        CODE39_PATTERNS.put('Z', "NWWNWNNNN");
        CODE39_PATTERNS.put('-', "NWNNNNWNW");
        CODE39_PATTERNS.put('.', "WWNNNNWNN");
        CODE39_PATTERNS.put(' ', "NWWNNNWNN");
        CODE39_PATTERNS.put('$', "NWNWNWNNN");
        CODE39_PATTERNS.put('/', "NWNWNNNWN");
        CODE39_PATTERNS.put('+', "NWNNNWNWN");
        CODE39_PATTERNS.put('%', "NNNWNWNWN");
        CODE39_PATTERNS.put('*', "NWNNWNWNN");
    }

    /**
     * Constructeur public par défaut du service de codes-barres.
     */
    public BarcodeService() {
        // Constructeur par défaut
    }

    /**
     * Formats physiques standard de planches d'étiquettes A4 autocollantes pris en charge.
     */
    public enum FormatPlanche {
        /** Planche standard de 24 étiquettes (3 colonnes x 8 lignes, 70 x 37 mm). */
        STANDARD_24(
            "Planche standard : 24 étiquettes (3x8, 70x37 mm — Avery L7159/C2160/Agipa 119013)",
            3, 8, 70.0, 37.0, 24, 0.1, 0.0, 0.0, 0.0
        ),
        /** Planche de 21 étiquettes (3 colonnes x 7 lignes, 63.5 x 38.1 mm). */
        AVERY_21(
            "Planche Avery : 21 étiquettes (3x7, 63.5x38.1 mm — Avery L7160/Agipa 119011)",
            3, 7, 63.5, 38.1, 21, 14.75, 7.25, 2.5, 0.0
        ),
        /** Planche compacte de 30 étiquettes (3 colonnes x 10 lignes, 70 x 29.6 mm). */
        COMPACT_30(
            "Planche compacte : 30 étiquettes (3x10, 70x29.6 mm — Avery L7651/Agipa 119014)",
            3, 10, 70.0, 29.6, 30, 0.1, 0.0, 0.0, 0.0
        ),
        /** Planche grand format de 14 étiquettes (2 colonnes x 7 lignes, 105 x 42.3 mm). */
        GRAND_14(
            "Planche grand format : 14 étiquettes (2x7, 105x42.3 mm — Avery L7163/Agipa 119017)",
            2, 7, 105.0, 42.3, 14, 0.05, 0.0, 0.0, 0.0
        ),
        /** Planche tranches fines de 65 étiquettes (5 colonnes x 13 lignes, 38.1 x 21.2 mm). */
        TRANCHE_65(
            "Planche tranches : 65 étiquettes (5x13, 38.1x21.2 mm — Avery 3659/L7651)",
            5, 13, 38.1, 21.2, 65, 10.3, 4.75, 2.5, 0.0
        );

        /** Libellé descriptif pour l'interface utilisateur */
        private final String libelle;
        /** Nombre de colonnes par feuille A4 */
        private final int colonnes;
        /** Nombre de lignes par feuille A4 */
        private final int lignes;
        /** Largeur d'une étiquette individuelle en millimètres */
        private final double largeurMm;
        /** Hauteur d'une étiquette individuelle en millimètres */
        private final double hauteurMm;
        /** Nombre total d'étiquettes sur une feuille A4 */
        private final int totalParPage;
        /** Marge supérieure physique en millimètres */
        private final double margeHautMm;
        /** Marge gauche physique en millimètres */
        private final double margeGaucheMm;
        /** Espacement horizontal entre colonnes en millimètres */
        private final double gapColMm;
        /** Espacement vertical entre lignes en millimètres */
        private final double gapRowMm;

        FormatPlanche(String libelle, int colonnes, int lignes, double largeurMm, double hauteurMm, int totalParPage,
                      double margeHautMm, double margeGaucheMm, double gapColMm, double gapRowMm) {
            this.libelle = libelle;
            this.colonnes = colonnes;
            this.lignes = lignes;
            this.largeurMm = largeurMm;
            this.hauteurMm = hauteurMm;
            this.totalParPage = totalParPage;
            this.margeHautMm = margeHautMm;
            this.margeGaucheMm = margeGaucheMm;
            this.gapColMm = gapColMm;
            this.gapRowMm = gapRowMm;
        }

        /** @return le libellé descriptif */
        public String getLibelle() { return libelle; }
        /** @return le nombre de colonnes */
        public int getColonnes() { return colonnes; }
        /** @return le nombre de lignes */
        public int getLignes() { return lignes; }
        /** @return la largeur en mm */
        public double getLargeurMm() { return largeurMm; }
        /** @return la hauteur en mm */
        public double getHauteurMm() { return hauteurMm; }
        /** @return le total d'étiquettes par page */
        public int getTotalParPage() { return totalParPage; }
        /** @return la marge supérieure en mm */
        public double getMargeHautMm() { return margeHautMm; }
        /** @return la marge gauche en mm */
        public double getMargeGaucheMm() { return margeGaucheMm; }
        /** @return l'espacement entre colonnes en mm */
        public double getGapColMm() { return gapColMm; }
        /** @return l'espacement entre lignes en mm */
        public double getGapRowMm() { return gapRowMm; }

        @Override
        public String toString() { return libelle; }
    }

    // =====================================================================
    //  Génération Code-barres SVG
    // =====================================================================

    /**
     * Génère un code-barres vectoriel SVG en Code 39 pour un texte donné.
     *
     * @param rawText texte brut à encoder (chiffres, lettres majuscules, tirets)
     * @param heightPx hauteur du code-barres en pixels
     * @return code XML/SVG prêt à être injecté en HTML
     */
    public static String generateCode39Svg(String rawText, int heightPx) {
        if (rawText == null || rawText.isBlank()) {
            rawText = "0";
        }
        String clean = rawText.trim().toUpperCase();
        // Vérifier les caractères
        StringBuilder valid = new StringBuilder();
        for (char c : clean.toCharArray()) {
            if (CODE39_PATTERNS.containsKey(c)) {
                valid.append(c);
            }
        }
        if (valid.length() == 0) valid.append("0");

        // Code 39 est délimité par des astérisques '*'
        String toEncode = "*" + valid.toString() + "*";

        double narrow = 1.6;
        double wide = narrow * 2.5;
        double interCharGap = narrow * 1.5;

        // Calcul de la largeur totale
        double totalWidth = 0;
        for (int i = 0; i < toEncode.length(); i++) {
            char c = toEncode.charAt(i);
            String pattern = CODE39_PATTERNS.get(c);
            if (pattern == null) pattern = CODE39_PATTERNS.get('*');
            for (int p = 0; p < 9; p++) {
                boolean isWide = pattern.charAt(p) == 'W';
                totalWidth += isWide ? wide : narrow;
            }
            if (i < toEncode.length() - 1) {
                totalWidth += interCharGap;
            }
        }

        // Construction du SVG
        StringBuilder svg = new StringBuilder();
        svg.append(String.format(Locale.ROOT,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %.1f %d\" "
                + "width=\"100%%\" height=\"%dpx\" preserveAspectRatio=\"none\">",
                totalWidth, heightPx, heightPx
        ));

        double currentX = 0;
        for (int i = 0; i < toEncode.length(); i++) {
            char c = toEncode.charAt(i);
            String pattern = CODE39_PATTERNS.get(c);
            if (pattern == null) pattern = CODE39_PATTERNS.get('*');

            for (int p = 0; p < 9; p++) {
                boolean isBar = (p % 2 == 0);
                boolean isWide = pattern.charAt(p) == 'W';
                double w = isWide ? wide : narrow;

                if (isBar) {
                    svg.append(String.format(Locale.ROOT,
                            "<rect x=\"%.2f\" y=\"0\" width=\"%.2f\" height=\"%d\" fill=\"#000000\"/>",
                            currentX, w, heightPx
                    ));
                }
                currentX += w;
            }
            if (i < toEncode.length() - 1) {
                currentX += interCharGap;
            }
        }
        svg.append("</svg>");
        return svg.toString();
    }

    // =====================================================================
    //  Génération de Planche A4 HTML
    // =====================================================================

    /**
     * Surcharge de compatibilité (6 paramètres) : impression standard sans décalage ni contours forcés.
     */
    public static void genererPlancheHtml(List<EtiquetteItem> items,
                                         FormatPlanche format,
                                         String enTeteLibelle,
                                         boolean inclureTitre,
                                         boolean inclureBarcode,
                                         File outputFile) throws IOException {
        genererPlancheHtml(items, format, enTeteLibelle, inclureTitre, inclureBarcode, 0, false, outputFile);
    }

    /**
     * Génère une planche d'étiquettes A4 complète en HTML prêt pour l'impression haute précision.
     * Calibré rigoureusement aux dimensions physiques des feuilles d'étiquettes adhésives A4 (210 x 297 mm)
     * pour garantir AUCUNE coupure entre pages et AUCUNE perte de feuille.
     *
     * @param items liste des exemplaires à imprimer
     * @param format format d'étiquettes choisi (Avery / Agipa standard)
     * @param enTeteLibelle libellé d'en-tête (ex: "BCD École" ou nom de l'école)
     * @param inclureTitre afficher le titre du livre sur l'étiquette
     * @param inclureBarcode afficher le code-barres vectoriel
     * @param startOffset nombre de cases déjà utilisées à sauter sur la 1ère feuille (0 pour commencer en haut à gauche)
     * @param afficherBordures true pour tracer les repères de découpe (pointillés), false pour planches pré-découpées
     * @param outputFile fichier de destination HTML
     */
    public static void genererPlancheHtml(List<EtiquetteItem> items,
                                         FormatPlanche format,
                                         String enTeteLibelle,
                                         boolean inclureTitre,
                                         boolean inclureBarcode,
                                         int startOffset,
                                         boolean afficherBordures,
                                         File outputFile) throws IOException {

        if (format == null) {
            format = FormatPlanche.STANDARD_24;
        }

        if (enTeteLibelle == null || enTeteLibelle.isBlank()) {
            enTeteLibelle = "ÉCOLE ALBERT CAMUS PASTEUR";
        }

        int safeOffset = Math.max(0, startOffset);
        int totalItems = items != null ? items.size() : 0;
        int perPage = format.getTotalParPage();
        int totalSlots = safeOffset + totalItems;
        int pageCount = (int) Math.ceil((double) totalSlots / perPage);
        if (pageCount == 0) pageCount = 1;

        // Dimensions et styles adaptés selon le format
        int barcodeHeight;
        String headerFontSize;
        String titleFontSize;
        String titleMaxHeight;
        String idFontSize;
        String isbnFontSize;

        switch (format) {
            case TRANCHE_65:
                barcodeHeight = 15;
                headerFontSize = "6.5px";
                titleFontSize = "7px";
                titleMaxHeight = "1.2em";
                idFontSize = "8px";
                isbnFontSize = "6.5px";
                break;
            case COMPACT_30:
                barcodeHeight = 20;
                headerFontSize = "7.5px";
                titleFontSize = "8px";
                titleMaxHeight = "1.3em";
                idFontSize = "9px";
                isbnFontSize = "7px";
                break;
            case GRAND_14:
                barcodeHeight = 30;
                headerFontSize = "9px";
                titleFontSize = "11px";
                titleMaxHeight = "2.6em";
                idFontSize = "11px";
                isbnFontSize = "9px";
                break;
            case AVERY_21:
            case STANDARD_24:
            default:
                barcodeHeight = 25;
                headerFontSize = "8px";
                titleFontSize = "9.5px";
                titleMaxHeight = "2.4em";
                idFontSize = "10px";
                isbnFontSize = "7.5px";
                break;
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append(String.format(Locale.ROOT, "<title>Planche d'étiquettes A4 BCD Express — %s (%d exemplaires)</title>\n",
                escapeHtml(format.getLibelle()), totalItems));
        html.append("<style>\n");
        html.append("  @page {\n");
        html.append("    size: A4 portrait;\n");
        html.append("    margin: 0 !important;\n");
        html.append("  }\n");
        html.append("  *, *::before, *::after {\n");
        html.append("    box-sizing: border-box;\n");
        html.append("  }\n");
        html.append("  html, body {\n");
        html.append("    margin: 0;\n");
        html.append("    padding: 0;\n");
        html.append("    background-color: #f1f5f9;\n");
        html.append("    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n");
        html.append("    color: #0f172a;\n");
        html.append("    -webkit-print-color-adjust: exact;\n");
        html.append("    print-color-adjust: exact;\n");
        html.append("  }\n");
        html.append("  .banner-actions {\n");
        html.append("    background: #0f172a;\n");
        html.append("    color: #f8fafc;\n");
        html.append("    padding: 12px 24px;\n");
        html.append("    display: flex;\n");
        html.append("    align-items: center;\n");
        html.append("    justify-content: space-between;\n");
        html.append("    gap: 16px;\n");
        html.append("    box-shadow: 0 4px 15px rgba(0,0,0,0.2);\n");
        html.append("    position: sticky;\n");
        html.append("    top: 0;\n");
        html.append("    z-index: 9999;\n");
        html.append("    font-size: 13px;\n");
        html.append("  }\n");
        html.append("  .banner-info {\n");
        html.append("    display: flex;\n");
        html.append("    flex-direction: column;\n");
        html.append("    gap: 3px;\n");
        html.append("  }\n");
        html.append("  .banner-title {\n");
        html.append("    font-weight: 800;\n");
        html.append("    font-size: 14px;\n");
        html.append("    color: #ffffff;\n");
        html.append("  }\n");
        html.append("  .banner-meta {\n");
        html.append("    font-size: 12px;\n");
        html.append("    color: #94a3b8;\n");
        html.append("  }\n");
        html.append("  .banner-tips {\n");
        html.append("    display: flex;\n");
        html.append("    align-items: center;\n");
        html.append("    gap: 10px;\n");
        html.append("    background: #1e293b;\n");
        html.append("    padding: 6px 12px;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    border: 1px solid #334155;\n");
        html.append("    font-size: 11.5px;\n");
        html.append("    color: #cbd5e1;\n");
        html.append("  }\n");
        html.append("  .banner-buttons {\n");
        html.append("    display: flex;\n");
        html.append("    align-items: center;\n");
        html.append("    gap: 10px;\n");
        html.append("  }\n");
        html.append("  .btn-toggle-borders {\n");
        html.append("    background: #334155;\n");
        html.append("    color: #f1f5f9;\n");
        html.append("    border: 1px solid #475569;\n");
        html.append("    padding: 8px 14px;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    font-weight: 600;\n");
        html.append("    font-size: 12px;\n");
        html.append("    cursor: pointer;\n");
        html.append("    transition: all 0.15s ease;\n");
        html.append("  }\n");
        html.append("  .btn-toggle-borders:hover { background: #475569; }\n");
        html.append("  .btn-print {\n");
        html.append("    background: #0284c7;\n");
        html.append("    color: white;\n");
        html.append("    border: none;\n");
        html.append("    padding: 9px 20px;\n");
        html.append("    border-radius: 6px;\n");
        html.append("    font-weight: 700;\n");
        html.append("    font-size: 13px;\n");
        html.append("    cursor: pointer;\n");
        html.append("    box-shadow: 0 2px 6px rgba(2, 132, 199, 0.4);\n");
        html.append("    transition: all 0.15s ease;\n");
        html.append("  }\n");
        html.append("  .btn-print:hover { background: #0369a1; }\n");

        // Page A4 rigide (210 x 296.2 mm pour éviter tout saut de page sous-pixel navigateur)
        html.append("  .sheet-page {\n");
        html.append("    width: 210mm;\n");
        html.append("    height: 296.2mm;\n");
        html.append("    max-height: 296.2mm;\n");
        html.append("    margin: 15mm auto;\n");
        html.append("    background: #ffffff;\n");
        html.append("    box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);\n");
        html.append("    box-sizing: border-box;\n");
        html.append("    page-break-inside: avoid;\n");
        html.append("    break-inside: avoid;\n");
        html.append("    page-break-after: always;\n");
        html.append("    break-after: page;\n");
        html.append("    overflow: hidden;\n");
        html.append(String.format(Locale.ROOT, "    padding-top: %.2fmm;\n", format.getMargeHautMm()));
        html.append(String.format(Locale.ROOT, "    padding-bottom: %.2fmm;\n", format.getMargeHautMm()));
        html.append(String.format(Locale.ROOT, "    padding-left: %.2fmm;\n", format.getMargeGaucheMm()));
        html.append(String.format(Locale.ROOT, "    padding-right: %.2fmm;\n", format.getMargeGaucheMm()));
        html.append("    position: relative;\n");
        html.append("  }\n");
        html.append("  .sheet-page:last-of-type {\n");
        html.append("    page-break-after: auto !important;\n");
        html.append("    break-after: auto !important;\n");
        html.append("  }\n");

        // Grille d'étiquettes
        html.append("  .grid-labels {\n");
        html.append("    display: grid;\n");
        html.append("    width: 100%;\n");
        html.append("    height: 100%;\n");
        html.append(String.format(Locale.ROOT, "    grid-template-columns: repeat(%d, %.2fmm);\n", format.getColonnes(), format.getLargeurMm()));
        html.append(String.format(Locale.ROOT, "    grid-template-rows: repeat(%d, %.2fmm);\n", format.getLignes(), format.getHauteurMm()));
        html.append(String.format(Locale.ROOT, "    column-gap: %.2fmm;\n", format.getGapColMm()));
        html.append(String.format(Locale.ROOT, "    row-gap: %.2fmm;\n", format.getGapRowMm()));
        html.append("    justify-content: center;\n");
        html.append("    align-content: center;\n");
        html.append("    box-sizing: border-box;\n");
        html.append("  }\n");

        // Carte d'étiquette
        html.append("  .label-card {\n");
        html.append(String.format(Locale.ROOT, "    width: %.2fmm;\n", format.getLargeurMm()));
        html.append(String.format(Locale.ROOT, "    height: %.2fmm;\n", format.getHauteurMm()));
        html.append(String.format(Locale.ROOT, "    max-height: %.2fmm;\n", format.getHauteurMm()));
        html.append("    box-sizing: border-box;\n");
        html.append("    padding: 1.5mm 2mm;\n");
        html.append("    display: flex;\n");
        html.append("    flex-direction: column;\n");
        html.append("    justify-content: space-between;\n");
        html.append("    align-items: center;\n");
        html.append("    text-align: center;\n");
        html.append("    overflow: hidden;\n");
        html.append("    background: #ffffff;\n");
        html.append("    page-break-inside: avoid;\n");
        html.append("    break-inside: avoid;\n");
        html.append("    position: relative;\n");
        html.append("  }\n");

        // Case vide (offset ou fin de page)
        html.append("  .empty-slot {\n");
        html.append(String.format(Locale.ROOT, "    width: %.2fmm;\n", format.getLargeurMm()));
        html.append(String.format(Locale.ROOT, "    height: %.2fmm;\n", format.getHauteurMm()));
        html.append(String.format(Locale.ROOT, "    max-height: %.2fmm;\n", format.getHauteurMm()));
        html.append("    box-sizing: border-box;\n");
        html.append("    background: transparent;\n");
        html.append("    page-break-inside: avoid;\n");
        html.append("    break-inside: avoid;\n");
        html.append("    display: flex;\n");
        html.append("    align-items: center;\n");
        html.append("    justify-content: center;\n");
        html.append("    color: #94a3b8;\n");
        html.append("    font-size: 8px;\n");
        html.append("    font-family: monospace;\n");
        html.append("  }\n");

        html.append("  .border-visible {\n");
        html.append("    border: 1px dotted #94a3b8 !important;\n");
        html.append("  }\n");
        html.append("  .border-hidden {\n");
        html.append("    border: 1px solid transparent !important;\n");
        html.append("  }\n");

        // Typographie interne
        html.append("  .label-header {\n");
        html.append(String.format(Locale.ROOT, "    font-size: %s;\n", headerFontSize));
        html.append("    font-weight: 700;\n");
        html.append("    letter-spacing: 0.4px;\n");
        html.append("    color: #475569;\n");
        html.append("    text-transform: uppercase;\n");
        html.append("    white-space: nowrap;\n");
        html.append("    overflow: hidden;\n");
        html.append("    text-overflow: ellipsis;\n");
        html.append("    width: 100%;\n");
        html.append("    line-height: 1.1;\n");
        html.append("  }\n");

        html.append("  .label-title {\n");
        html.append(String.format(Locale.ROOT, "    font-size: %s;\n", titleFontSize));
        html.append("    font-weight: 700;\n");
        html.append("    color: #0f172a;\n");
        html.append("    line-height: 1.15;\n");
        html.append(String.format(Locale.ROOT, "    max-height: %s;\n", titleMaxHeight));
        html.append("    overflow: hidden;\n");
        html.append("    text-overflow: ellipsis;\n");
        html.append("    width: 100%;\n");
        html.append("    word-break: break-word;\n");
        html.append("  }\n");

        html.append("  .barcode-wrapper {\n");
        html.append("    width: 94%;\n");
        html.append("    margin: 0.5mm auto;\n");
        html.append("    display: flex;\n");
        html.append("    align-items: center;\n");
        html.append("    justify-content: center;\n");
        html.append("    overflow: hidden;\n");
        html.append("  }\n");

        html.append("  .label-footer {\n");
        html.append("    width: 100%;\n");
        html.append("    display: flex;\n");
        html.append("    align-items: baseline;\n");
        html.append("    justify-content: center;\n");
        html.append("    gap: 6px;\n");
        html.append("    line-height: 1;\n");
        html.append("  }\n");

        html.append("  .label-id {\n");
        html.append("    font-family: 'Courier New', Courier, monospace;\n");
        html.append(String.format(Locale.ROOT, "    font-size: %s;\n", idFontSize));
        html.append("    font-weight: 800;\n");
        html.append("    color: #0284c7;\n");
        html.append("  }\n");

        html.append("  .label-isbn {\n");
        html.append(String.format(Locale.ROOT, "    font-size: %s;\n", isbnFontSize));
        html.append("    color: #64748b;\n");
        html.append("    font-family: 'Courier New', Courier, monospace;\n");
        html.append("  }\n");

        // CSS Spécifique à l'impression
        html.append("  @media print {\n");
        html.append("    html, body {\n");
        html.append("      background: transparent !important;\n");
        html.append("      margin: 0 !important;\n");
        html.append("      padding: 0 !important;\n");
        html.append("      width: 210mm !important;\n");
        html.append("      height: 297mm !important;\n");
        html.append("    }\n");
        html.append("    .banner-actions {\n");
        html.append("      display: none !important;\n");
        html.append("    }\n");
        html.append("    .sheet-page {\n");
        html.append("      margin: 0 !important;\n");
        html.append("      box-shadow: none !important;\n");
        html.append("      width: 210mm !important;\n");
        html.append("      height: 296.2mm !important;\n");
        html.append("      max-height: 296.2mm !important;\n");
        html.append("      page-break-inside: avoid !important;\n");
        html.append("      break-inside: avoid !important;\n");
        html.append("      page-break-after: always !important;\n");
        html.append("      break-after: page !important;\n");
        html.append("    }\n");
        html.append("    .sheet-page:last-of-type {\n");
        html.append("      page-break-after: auto !important;\n");
        html.append("      break-after: auto !important;\n");
        html.append("    }\n");
        html.append("    .empty-slot {\n");
        html.append("      color: transparent !important;\n");
        html.append("    }\n");
        html.append("  }\n");
        html.append("</style>\n");

        // Script JS interactif pour basculer les repères de découpe et imprimer
        html.append("<script>\n");
        html.append("  function toggleBorders() {\n");
        html.append("    var elements = document.querySelectorAll('.label-card, .empty-slot');\n");
        html.append("    elements.forEach(function(el) {\n");
        html.append("      if (el.classList.contains('border-visible')) {\n");
        html.append("        el.classList.remove('border-visible');\n");
        html.append("        el.classList.add('border-hidden');\n");
        html.append("      } else {\n");
        html.append("        el.classList.remove('border-hidden');\n");
        html.append("        el.classList.add('border-visible');\n");
        html.append("      }\n");
        html.append("    });\n");
        html.append("    var btn = document.getElementById('btnToggleBorders');\n");
        html.append("    if (btn) {\n");
        html.append("      var isVisible = document.querySelector('.label-card.border-visible') !== null;\n");
        html.append("      btn.innerText = isVisible ? '✂️ Masquer les repères de découpe' : '✂️ Afficher les repères de découpe';\n");
        html.append("    }\n");
        html.append("  }\n");
        html.append("</script>\n");
        html.append("</head>\n<body>\n");

        // Bandeau d'aide et de contrôle écran
        String initialBtnBorderText = afficherBordures ? "✂️ Masquer les repères de découpe" : "✂️ Afficher les repères de découpe";
        html.append("<div class=\"banner-actions\">\n");
        html.append("  <div class=\"banner-info\">\n");
        html.append(String.format(Locale.ROOT, "    <div class=\"banner-title\">🏷️ Planche d'étiquettes A4 — %s</div>\n", escapeHtml(format.getLibelle())));
        html.append(String.format(Locale.ROOT, "    <div class=\"banner-meta\">%d exemplaire(s) physique(s) • %d feuille(s) A4%s</div>\n",
                totalItems, pageCount, safeOffset > 0 ? String.format(" (décalage initial de %d case(s))", safeOffset) : ""));
        html.append("  </div>\n");

        html.append("  <div class=\"banner-tips\">\n");
        html.append("    <span>💡 Paramètres d'impression : <strong>Échelle 100%</strong> (taille réelle) • <strong>Marges : Aucune</strong> • <strong>En-têtes/pieds décochés</strong></span>\n");
        html.append("  </div>\n");

        html.append("  <div class=\"banner-buttons\">\n");
        html.append(String.format(Locale.ROOT, "    <button id=\"btnToggleBorders\" class=\"btn-toggle-borders\" onclick=\"toggleBorders()\">%s</button>\n", initialBtnBorderText));
        html.append("    <button class=\"btn-print\" onclick=\"window.print()\">🖨️ Imprimer la planche</button>\n");
        html.append("  </div>\n");
        html.append("</div>\n");

        // Rendu des pages physiques A4
        String borderClass = afficherBordures ? "border-visible" : "border-hidden";

        for (int p = 0; p < pageCount; p++) {
            html.append("<div class=\"sheet-page\">\n");
            html.append("  <div class=\"grid-labels\">\n");

            for (int s = 0; s < perPage; s++) {
                int globalSlot = p * perPage + s;

                if (globalSlot < safeOffset) {
                    // Case sautée pour réutiliser une planche déjà entamée
                    html.append(String.format(Locale.ROOT,
                            "    <div class=\"empty-slot %s\">[Déjà utilisée #%d]</div>\n",
                            borderClass, globalSlot + 1));
                } else if (globalSlot < safeOffset + totalItems) {
                    // Exemplaire réel à imprimer
                    EtiquetteItem item = items.get(globalSlot - safeOffset);
                    html.append(String.format(Locale.ROOT, "    <div class=\"label-card %s\">\n", borderClass));
                    html.append(String.format(Locale.ROOT, "      <div class=\"label-header\">%s</div>\n", escapeHtml(enTeteLibelle)));

                    if (inclureTitre) {
                        html.append(String.format(Locale.ROOT, "      <div class=\"label-title\" title=\"%s\">%s</div>\n",
                                escapeHtml(item.getTitre()), escapeHtml(item.getTitre())));
                    }

                    if (inclureBarcode) {
                        html.append("      <div class=\"barcode-wrapper\">\n");
                        html.append(generateCode39Svg(item.getCodeBarreValue(), barcodeHeight));
                        html.append("      </div>\n");
                    }

                    html.append("      <div class=\"label-footer\">\n");
                    html.append(String.format(Locale.ROOT, "        <span class=\"label-id\">Ex. #%d</span>\n", item.getExemplaireId()));
                    if (!item.getIsbn().isBlank() && format != FormatPlanche.TRANCHE_65) {
                        html.append(String.format(Locale.ROOT, "        <span class=\"label-isbn\">%s</span>\n", escapeHtml(item.getIsbn())));
                    }
                    html.append("      </div>\n");
                    html.append("    </div>\n");
                } else {
                    // Case vide en fin de dernière feuille
                    html.append(String.format(Locale.ROOT,
                            "    <div class=\"empty-slot %s\"></div>\n",
                            borderClass));
                }
            }

            html.append("  </div>\n"); // .grid-labels
            html.append("</div>\n");   // .sheet-page
        }

        html.append("</body>\n</html>");

        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }

    /**
     * Échappe les caractères réservés HTML (&amp;, &lt;, &gt;, &quot;, &#39;) afin de prévenir
     * les casses de balisage dans la planche générée.
     *
     * @param text le texte brut
     * @return le texte avec entités HTML échappées, ou chaîne vide si {@code null}
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}
