package fr.ecole.bcd.service;

import fr.ecole.bcd.model.BilanStatistiques;
import fr.ecole.bcd.model.Utilisateur;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service pour la génération des exports CSV et des fiches imprimables HTML pour l'école :
 * <ul>
 *   <li><b>Le Bilan d'Activité &amp; Palmarès BCD</b> (rapport pour le Conseil d'école, l'équipe enseignante et la Direction).</li>
 *   <li><b>La Fiche de prêt de série / Panier enseignant</b> (document A4 imprimable à glisser directement dans le bac de livres de la classe).</li>
 * </ul>
 *
 * @author JC ART
 * @since 2026-10-12
 * @version 1.0 (Dernière modification : 2026-11-10)
 */
public class BilanExportService {

    /**
     * Formateur de date français standard (jour/mois/année).
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Constructeur public par défaut du service d'exportation de bilan.
     */
    public BilanExportService() {
        // Constructeur par défaut
    }

    // =====================================================================
    //  Fiche Panier Enseignant / Série de classe (HTML)
    // =====================================================================

    /**
     * Représente une ligne d'ouvrage au sein d'un panier de prêt pour enseignant (série ou projet de classe).
     */
    public static class PanierLivreItem {
        /** Identifiant unique de l'exemplaire physique */
        private final int exemplaireId;
        /** Numéro ISBN */
        private final String isbn;
        /** Titre de l'ouvrage */
        private final String titre;
        /** Auteur de l'ouvrage */
        private final String auteur;
        /** Remarque ou état physique */
        private final String remarque;

        /**
         * Construit un élément de panier de prêt enseignant.
         *
         * @param exemplaireId identifiant de l'exemplaire
         * @param isbn         numéro ISBN
         * @param titre        titre du livre
         * @param auteur       auteur
         * @param remarque     remarque ou état
         */
        public PanierLivreItem(int exemplaireId, String isbn, String titre, String auteur, String remarque) {
            this.exemplaireId = exemplaireId;
            this.isbn = isbn != null ? isbn : "";
            this.titre = titre != null ? titre : "Sans titre";
            this.auteur = auteur != null ? auteur : "";
            this.remarque = remarque != null ? remarque : "";
        }

        /** @return l'identifiant de l'exemplaire */
        public int getExemplaireId() { return exemplaireId; }
        /** @return le numéro ISBN */
        public String getIsbn() { return isbn; }
        /** @return le titre du livre */
        public String getTitre() { return titre; }
        /** @return l'auteur */
        public String getAuteur() { return auteur; }
        /** @return la remarque ou l'état physique */
        public String getRemarque() { return remarque; }
    }

    /**
     * Génère une fiche imprimable A4 prête à l'emploi pour un prêt groupé enseignant ou une série de classe.
     *
     * @param enseignant   la fiche de l'enseignant emprunteur
     * @param dateEmprunt  la date de mise à disposition des livres
     * @param dateLimite   la date convenue de restitution
     * @param livres       la liste des exemplaires empruntés
     * @param outputFile   le fichier HTML de destination à générer
     * @throws IOException en cas d'erreur lors de l'écriture sur disque
     */
    public static void genererFichePanierHtml(Utilisateur enseignant,
                                              LocalDate dateEmprunt,
                                              LocalDate dateLimite,
                                              List<PanierLivreItem> livres,
                                              File outputFile) throws IOException {

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Fiche de Prêt — Panier Enseignant</title>\n");
        html.append("<style>\n");
        html.append("  @page { size: A4 portrait; margin: 12mm 15mm; }\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f8fafc; color: #1e293b; }\n");
        html.append("  .container { max-width: 800px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.08); }\n");
        html.append("  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #0284c7; padding-bottom: 15px; margin-bottom: 20px; }\n");
        html.append("  .title { font-size: 20px; font-weight: bold; color: #0284c7; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        html.append("  .subtitle { font-size: 13px; color: #64748b; margin-top: 4px; }\n");
        html.append("  .badge-school { background: #e0f2fe; color: #0369a1; padding: 4px 10px; border-radius: 4px; font-weight: bold; font-size: 12px; }\n");
        html.append("  .info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 15px; margin-bottom: 25px; }\n");
        html.append("  .info-item { font-size: 13px; }\n");
        html.append("  .info-label { color: #64748b; font-size: 11px; text-transform: uppercase; font-weight: bold; margin-bottom: 2px; }\n");
        html.append("  .info-val { font-size: 14px; font-weight: 600; color: #0f172a; }\n");
        html.append("  table { width: 100%; border-collapse: collapse; margin-bottom: 30px; font-size: 13px; }\n");
        html.append("  th { background: #f1f5f9; color: #475569; text-align: left; padding: 8px 10px; border-bottom: 2px solid #cbd5e1; font-size: 12px; text-transform: uppercase; }\n");
        html.append("  td { padding: 8px 10px; border-bottom: 1px solid #e2e8f0; vertical-align: top; }\n");
        html.append("  tr:nth-child(even) { background: #fafafa; }\n");
        html.append("  .ex-id { font-family: monospace; font-weight: bold; color: #0284c7; }\n");
        html.append("  .remarque { font-size: 11px; color: #b45309; font-style: italic; }\n");
        html.append("  .signatures { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; margin-top: 40px; }\n");
        html.append("  .sign-box { border: 1px dashed #94a3b8; border-radius: 6px; height: 90px; padding: 10px; position: relative; }\n");
        html.append("  .sign-title { font-size: 11px; font-weight: bold; color: #64748b; text-transform: uppercase; }\n");
        html.append("  .btn-print { background: #0284c7; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: bold; margin-bottom: 15px; }\n");
        html.append("  @media print {\n");
        html.append("    body { background: white; padding: 0; }\n");
        html.append("    .container { box-shadow: none; padding: 0; max-width: 100%; }\n");
        html.append("    .no-print { display: none !important; }\n");
        html.append("  }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");

        html.append("<div class=\"no-print\" style=\"max-width:800px; margin:0 auto 10px auto; display:flex; justify-content:space-between; align-items:center;\">\n");
        html.append("  <span style=\"font-size:13px; color:#64748b;\">💡 Astuce : Glissez cette fiche directement dans le bac de livres de la classe !</span>\n");
        html.append("  <button class=\"btn-print\" onclick=\"window.print()\">🖨️ Imprimer la fiche (Ctrl+P / Cmd+P)</button>\n");
        html.append("</div>\n");

        html.append("<div class=\"container\">\n");
        html.append("  <div class=\"header\">\n");
        html.append("    <div>\n");
        html.append("      <div class=\"title\">🧺 Prêt de série / Panier de classe</div>\n");
        html.append("      <div class=\"subtitle\">BCD Express — Guichet de prêt de bibliothèque scolaire</div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"badge-school\">ÉCOLE ALBERT CAMUS PASTEUR — MEUDON</div>\n");
        html.append("  </div>\n");

        html.append("  <div class=\"info-grid\">\n");
        html.append("    <div class=\"info-item\"><div class=\"info-label\">Enseignant(e) emprunteur</div><div class=\"info-val\">")
            .append(escapeHtml(enseignant.getPrenom() + " " + enseignant.getNom()))
            .append(" (").append(escapeHtml(enseignant.getNumero())).append(")</div></div>\n");
        html.append("    <div class=\"info-item\"><div class=\"info-label\">Classe / Destination</div><div class=\"info-val\">")
            .append(escapeHtml(enseignant.getClasse())).append("</div></div>\n");
        html.append("    <div class=\"info-item\"><div class=\"info-label\">Date d'emprunt</div><div class=\"info-val\">")
            .append(dateEmprunt.format(DATE_FMT)).append("</div></div>\n");
        html.append("    <div class=\"info-item\"><div class=\"info-label\">Date de retour convenue</div><div class=\"info-val\" style=\"color:#b91c1c;\">")
            .append(dateLimite.format(DATE_FMT)).append("</div></div>\n");
        html.append("  </div>\n");

        html.append("  <div style=\"margin-bottom:10px; font-weight:bold; font-size:14px; display:flex; justify-content:space-between;\">\n");
        html.append(String.format("    <span>Liste des ouvrages prêtés (%d livres) :</span>\n", livres.size()));
        html.append("  </div>\n");

        html.append("  <table>\n");
        html.append("    <thead>\n");
        html.append("      <tr>\n");
        html.append("        <th style=\"width:70px;\">Exemplaire</th>\n");
        html.append("        <th style=\"width:140px;\">Code-barres</th>\n");
        html.append("        <th>Titre de l'ouvrage</th>\n");
        html.append("        <th>Auteur</th>\n");
        html.append("        <th style=\"width:110px;\">ISBN</th>\n");
        html.append("        <th style=\"width:120px;\">État / Remarque</th>\n");
        html.append("      </tr>\n");
        html.append("    </thead>\n");
        html.append("    <tbody>\n");

        for (PanierLivreItem item : livres) {
            String barcodeVal = (item.getIsbn() != null && !item.getIsbn().isBlank())
                    ? item.getIsbn().replaceAll("[^0-9X]", "")
                    : String.valueOf(item.getExemplaireId());
            html.append("      <tr>\n");
            html.append(String.format("        <td class=\"ex-id\">Ex. #%d</td>\n", item.getExemplaireId()));
            html.append("        <td style=\"padding:3px 6px;\"><div style=\"width:130px;\">")
                .append(BarcodeService.generateCode39Svg(barcodeVal, 20))
                .append("</div></td>\n");
            html.append(String.format("        <td><strong>%s</strong></td>\n", escapeHtml(item.getTitre())));
            html.append(String.format("        <td>%s</td>\n", escapeHtml(item.getAuteur())));
            html.append(String.format("        <td style=\"font-family:monospace; font-size:11px;\">%s</td>\n", escapeHtml(item.getIsbn())));
            html.append(String.format("        <td><span class=\"remarque\">%s</span></td>\n", escapeHtml(item.getRemarque())));
            html.append("      </tr>\n");
        }

        html.append("    </tbody>\n");
        html.append("  </table>\n");

        html.append("  <div class=\"signatures\">\n");
        html.append("    <div class=\"sign-box\">\n");
        html.append("      <div class=\"sign-title\">Signature de l'enseignant(e) :</div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"sign-box\">\n");
        html.append("      <div class=\"sign-title\">Visa du responsable BCD :</div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        html.append("</div>\n</body>\n</html>");

        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }

    // =====================================================================
    //  Bilan d'Activité BCD (HTML)
    // =====================================================================

    /**
     * Génère un rapport annuel de synthèse élégant et imprimable en HTML, calibré pour le conseil d'école
     * et l'équipe pédagogique (indicateurs KPI, palmarès des livres, statistiques par classe et top lecteurs).
     *
     * @param bilan      l'objet {@link BilanStatistiques} consolidé contenant l'ensemble des métriques d'activité
     * @param outputFile le fichier HTML cible à générer
     * @throws IOException en cas d'erreur lors de l'écriture sur disque
     */
    public static void genererBilanHtml(BilanStatistiques bilan, File outputFile) throws IOException {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html lang=\"fr\">\n<head>\n");
          html.append("<title>Bilan d'Activité BCD — École Albert Camus Pasteur (Meudon)</title>\n");
        html.append("<style>\n");
        html.append("  @page { size: A4 portrait; margin: 10mm 15mm; }\n");
        html.append("  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 20px; background: #f8fafc; color: #1e293b; }\n");
        html.append("  .container { max-width: 850px; margin: 0 auto; background: white; padding: 30px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.08); }\n");
        html.append("  .header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #0284c7; padding-bottom: 15px; margin-bottom: 20px; }\n");
        html.append("  .title { font-size: 22px; font-weight: bold; color: #0f172a; }\n");
        html.append("  .subtitle { font-size: 13px; color: #64748b; margin-top: 4px; }\n");
        html.append("  .kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 25px; }\n");
        html.append("  .kpi-card { background: #f1f5f9; border-radius: 6px; padding: 12px; text-align: center; border-top: 3px solid #0284c7; }\n");
        html.append("  .kpi-val { font-size: 24px; font-weight: bold; color: #0284c7; margin-top: 4px; }\n");
        html.append("  .kpi-label { font-size: 11px; text-transform: uppercase; color: #64748b; font-weight: 600; }\n");
        html.append("  .section-title { font-size: 16px; font-weight: bold; color: #0f172a; margin: 25px 0 10px 0; border-left: 4px solid #0284c7; padding-left: 8px; }\n");
        html.append("  table { width: 100%; border-collapse: collapse; margin-bottom: 20px; font-size: 12.5px; }\n");
        html.append("  th { background: #f1f5f9; color: #475569; text-align: left; padding: 7px 10px; border-bottom: 2px solid #cbd5e1; font-size: 11px; text-transform: uppercase; }\n");
        html.append("  td { padding: 7px 10px; border-bottom: 1px solid #e2e8f0; }\n");
        html.append("  tr:nth-child(even) { background: #fafafa; }\n");
        html.append("  .rank { font-weight: bold; width: 45px; text-align: center; }\n");
        html.append("  .rank-1 { color: #d97706; }\n");
        html.append("  .rank-2 { color: #64748b; }\n");
        html.append("  .rank-3 { color: #b45309; }\n");
        html.append("  .btn-print { background: #0284c7; color: white; border: none; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: bold; }\n");
        html.append("  @media print {\n");
        html.append("    body { background: white; padding: 0; }\n");
        html.append("    .container { box-shadow: none; padding: 0; max-width: 100%; }\n");
        html.append("    .no-print { display: none !important; }\n");
        html.append("  }\n");
        html.append("</style>\n");
        html.append("</head>\n<body>\n");
        html.append("<div class=\"no-print\" style=\"max-width:850px; margin:0 auto 10px auto; display:flex; justify-content:space-between; align-items:center;\">\n");
        html.append("  <span style=\"font-size:13px; color:#64748b;\">📊 Rapport d'activité officiel — École Albert Camus Pasteur (Meudon)</span>\n");
        html.append("  <button class=\"btn-print\" onclick=\"window.print()\">🖨️ Imprimer le bilan (Ctrl+P / Cmd+P)</button>\n");
        html.append("</div>\n");
        html.append("<div class=\"container\">\n");
        html.append("  <div class=\"header\">\n");
        html.append("    <div>\n");
        html.append("      <div class=\"title\">📊 Bilan d'Activité & Palmarès BCD</div>\n");
        html.append("      <div class=\"subtitle\">Document de synthèse — Édité le ").append(LocalDate.now().format(DATE_FMT)).append("</div>\n");
        html.append("    </div>\n");
        html.append("    <div style=\"text-align:right;\">\n");
        html.append("      <div style=\"font-weight:bold; font-size:14px; color:#0284c7;\">ÉCOLE ALBERT CAMUS PASTEUR</div>\n");
        html.append("      <div style=\"font-size:12px; color:#64748b;\">Meudon • Bibliothèque Centre Documentaire</div>\n");
        html.append("    </div>\n");
        html.append("  </div>\n");

        // KPI
        html.append("  <div class=\"kpi-row\">\n");
        html.append(String.format("    <div class=\"kpi-card\"><div class=\"kpi-label\">Total Prêts Historiques</div><div class=\"kpi-val\">%d</div></div>\n", bilan.getTotalPretsHistorique()));
        html.append(String.format("    <div class=\"kpi-card\"><div class=\"kpi-label\">Emprunts en cours</div><div class=\"kpi-val\">%d</div></div>\n", bilan.getPretsEnCours()));
        html.append(String.format("    <div class=\"kpi-card\"><div class=\"kpi-label\">Taux d'élèves lecteurs</div><div class=\"kpi-val\">%.1f%%</div><div style=\"font-size:10px; color:#64748b;\">%d / %d élèves</div></div>\n",
                bilan.getTauxElevesActifs(), bilan.getElevesActifs(), bilan.getTotalElevesInscrits()));
        html.append(String.format("    <div class=\"kpi-card\"><div class=\"kpi-label\">Titres uniques empruntés</div><div class=\"kpi-val\">%d</div></div>\n", bilan.getLivresDifferentsEmpruntes()));
        html.append("  </div>\n");

        // Top Livres
        html.append("  <div class=\"section-title\">🏆 Palmarès des Livres les plus empruntés</div>\n");
        html.append("  <table>\n");
        html.append("    <thead><tr><th class=\"rank\">Rang</th><th>Titre de l'ouvrage</th><th>Auteur</th><th>ISBN</th><th style=\"text-align:right;\">Nombre d'emprunts</th></tr></thead>\n");
        html.append("    <tbody>\n");
        int r = 1;
        for (BilanStatistiques.TopLivre livre : bilan.getTopLivres()) {
            String rankEmoji = r == 1 ? "🥇 1" : (r == 2 ? "🥈 2" : (r == 3 ? "🥉 3" : String.valueOf(r)));
            html.append("      <tr>\n");
            html.append(String.format("        <td class=\"rank rank-%d\">%s</td>\n", r <= 3 ? r : 0, rankEmoji));
            html.append(String.format("        <td><strong>%s</strong></td>\n", escapeHtml(livre.getTitre())));
            html.append(String.format("        <td>%s</td>\n", escapeHtml(livre.getAuteur())));
            html.append(String.format("        <td style=\"font-family:monospace; font-size:11px;\">%s</td>\n", escapeHtml(livre.getIsbn())));
            html.append(String.format("        <td style=\"text-align:right; font-weight:bold; color:#0284c7;\">%d prêts</td>\n", livre.getNbPrets()));
            html.append("      </tr>\n");
            r++;
        }
        if (bilan.getTopLivres().isEmpty()) {
            html.append("      <tr><td colspan=\"5\" style=\"text-align:center; color:#94a3b8;\">Aucun emprunt enregistré.</td></tr>\n");
        }
        html.append("    </tbody>\n  </table>\n");

        // Palmarès Classes
        html.append("  <div class=\"section-title\">🏫 Classement des Classes par volume de lecture</div>\n");
        html.append("  <table>\n");
        html.append("    <thead><tr><th class=\"rank\">Rang</th><th>Classe</th><th style=\"text-align:right;\">Nombre de prêts</th><th style=\"text-align:right;\">Élèves emprunteurs</th><th style=\"text-align:right;\">Moyenne par lecteur</th></tr></thead>\n");
        html.append("    <tbody>\n");
        int cr = 1;
        for (BilanStatistiques.ClasseStat cs : bilan.getStatsParClasse()) {
            html.append("      <tr>\n");
            html.append(String.format("        <td class=\"rank\">%d</td>\n", cr++));
            html.append(String.format("        <td><strong>%s</strong></td>\n", escapeHtml(cs.getClasse())));
            html.append(String.format("        <td style=\"text-align:right; font-weight:bold; color:#0284c7;\">%d prêts</td>\n", cs.getNbPrets()));
            html.append(String.format("        <td style=\"text-align:right;\">%d élèves</td>\n", cs.getNbEmprunteurs()));
            html.append(String.format("        <td style=\"text-align:right; color:#059669; font-weight:600;\">%.1f livres/élève</td>\n", cs.getMoyennePretsParEmprunteur()));
            html.append("      </tr>\n");
        }
        if (bilan.getStatsParClasse().isEmpty()) {
            html.append("      <tr><td colspan=\"5\" style=\"text-align:center; color:#94a3b8;\">Aucune donnée de classe disponible.</td></tr>\n");
        }
        html.append("    </tbody>\n  </table>\n");

        // Top Lecteurs
        html.append("  <div class=\"section-title\">⭐ Top 10 Élèves les plus assidus</div>\n");
        html.append("  <table>\n");
        html.append("    <thead><tr><th class=\"rank\">Rang</th><th>Élève</th><th>Classe</th><th style=\"text-align:right;\">Prêts enregistrés</th></tr></thead>\n");
        html.append("    <tbody>\n");
        int lr = 1;
        for (BilanStatistiques.TopLecteur tl : bilan.getTopLecteurs()) {
            if (lr > 10) break;
            html.append("      <tr>\n");
            html.append(String.format("        <td class=\"rank\">%d</td>\n", lr++));
            html.append(String.format("        <td><strong>%s</strong></td>\n", escapeHtml(tl.getNomComplet())));
            html.append(String.format("        <td>%s</td>\n", escapeHtml(tl.getClasse())));
            html.append(String.format("        <td style=\"text-align:right; font-weight:bold; color:#0284c7;\">%d livres</td>\n", tl.getNbPrets()));
            html.append("      </tr>\n");
        }
        if (bilan.getTopLecteurs().isEmpty()) {
            html.append("      <tr><td colspan=\"4\" style=\"text-align:center; color:#94a3b8;\">Aucun lecteur enregistré.</td></tr>\n");
        }
        html.append("    </tbody>\n  </table>\n");

        html.append("</div>\n</body>\n</html>");

        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }

    // =====================================================================
    //  Bilan d'Activité BCD (CSV)
    // =====================================================================

    /**
     * Exporte l'intégralité du bilan annuel sous forme d'un fichier CSV tabulaire directement ouvrable
     * sous Microsoft Excel et LibreOffice Calc (encodé en UTF-8 avec BOM et séparateurs points-virgules).
     *
     * @param bilan      l'objet {@link BilanStatistiques} consolidé
     * @param outputFile le fichier CSV cible à écrire
     * @throws IOException en cas d'erreur d'écriture disque
     */
    public static void exporterBilanCsv(BilanStatistiques bilan, File outputFile) throws IOException {
        StringBuilder csv = new StringBuilder();
        // BOM UTF-8 pour Excel
        csv.append("\uFEFF");

        // 1. Synthèse globale
        csv.append("--- BILAN D'ACTIVITE BCD EXPRESS - ECOLE ALBERT CAMUS PASTEUR (MEUDON) ---\n");
        csv.append("Date du rapport;").append(LocalDate.now().format(DATE_FMT)).append("\n");
        csv.append("Total prêts historiques;").append(bilan.getTotalPretsHistorique()).append("\n");
        csv.append("Emprunts actuellement en cours;").append(bilan.getPretsEnCours()).append("\n");
        csv.append("Emprunts actuellement en retard;").append(bilan.getPretsEnRetard()).append("\n");
        csv.append("Nombre total d'élèves inscrits;").append(bilan.getTotalElevesInscrits()).append("\n");
        csv.append("Nombre d'élèves lecteurs actifs;").append(bilan.getElevesActifs()).append("\n");
        csv.append(String.format("Taux d'élèves lecteurs actifs;%.1f%%\n", bilan.getTauxElevesActifs()));
        csv.append("Nombre de livres différents empruntés;").append(bilan.getLivresDifferentsEmpruntes()).append("\n\n");

        // 2. Top Livres
        csv.append("--- PALMARES DES LIVRES LES PLUS EMPRUNTES ---\n");
        csv.append("Rang;Titre;Auteur;ISBN;Nombre d'emprunts\n");
        for (BilanStatistiques.TopLivre tl : bilan.getTopLivres()) {
            csv.append(tl.getRang()).append(";")
               .append(escapeCsv(tl.getTitre())).append(";")
               .append(escapeCsv(tl.getAuteur())).append(";")
               .append(escapeCsv(tl.getIsbn())).append(";")
               .append(tl.getNbPrets()).append("\n");
        }
        csv.append("\n");

        // 3. Stats par classe
        csv.append("--- STATISTIQUES PAR CLASSE ---\n");
        csv.append("Rang;Classe;Nombre de prets;Eleves emprunteurs;Moyenne prets par eleve\n");
        for (BilanStatistiques.ClasseStat cs : bilan.getStatsParClasse()) {
            csv.append(cs.getRang()).append(";")
               .append(escapeCsv(cs.getClasse())).append(";")
               .append(cs.getNbPrets()).append(";")
               .append(cs.getNbEmprunteurs()).append(";")
               .append(String.format("%.2f", cs.getMoyennePretsParEmprunteur())).append("\n");
        }
        csv.append("\n");

        // 4. Top Lecteurs
        csv.append("--- TOP LECTEURS ELEVES ---\n");
        csv.append("Rang;Identifiant;Nom;Prenom;Classe;Nombre de prets\n");
        for (BilanStatistiques.TopLecteur tl : bilan.getTopLecteurs()) {
            csv.append(tl.getRang()).append(";")
               .append(escapeCsv(tl.getNumero())).append(";")
               .append(escapeCsv(tl.getNom())).append(";")
               .append(escapeCsv(tl.getPrenom())).append(";")
               .append(escapeCsv(tl.getClasse())).append(";")
               .append(tl.getNbPrets()).append("\n");
        }

        try (FileWriter writer = new FileWriter(outputFile, StandardCharsets.UTF_8)) {
            writer.write(csv.toString());
        }
    }

    /**
     * Échappe les caractères réservés HTML (&amp;, &lt;, &gt;, &quot;, &#39;) afin de sécuriser
     * l'injection de texte dans les documents HTML générés.
     *
     * @param text le texte brut à échapper
     * @return la chaîne nettoyée avec entités HTML, ou vide si {@code null}
     */
    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * Échappe un champ pour son inclusion conforme dans un fichier CSV (guillemets doublés et encadrement).
     *
     * @param field le texte brut du champ
     * @return la valeur formatée pour CSV, ou vide si {@code null}
     */
    private static String escapeCsv(String field) {
        if (field == null) return "";
        if (field.contains(";") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
