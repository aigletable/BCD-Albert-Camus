package fr.ecole.bcd.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Modèle représentant une Ressource Pédagogique associée à un livre et/ou à une mallette.
 * <p>
 * Peut être un lien web (Éduscol, Rallye-lecture, Babelio, vidéo, fiche éditeur en ligne)
 * ou un fichier local (PDF d'activités, questionnaire de lecture, document Word, corrigé).
 * </p>
 *
 * @author JC ART
 * @since 2026-11-16
 */
public class RessourcePedagogique {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Types de ressources pédagogiques pris en charge.
     */
    public enum TypeRessource {
        LIEN_WEB("Lien Web", "🌐", "badge-blue"),
        PDF("Document PDF", "📄", "badge-rose"),
        DOCUMENT("Document texte", "📝", "badge-purple"),
        AUTRE("Autre ressource", "📎", "badge-gray");

        private final String libelle;
        private final String icone;
        private final String badgeCss;

        TypeRessource(String libelle, String icone, String badgeCss) {
            this.libelle = libelle;
            this.icone = icone;
            this.badgeCss = badgeCss;
        }

        public String getLibelle() { return libelle; }
        public String getIcone() { return icone; }
        public String getBadgeCss() { return badgeCss; }

        public static TypeRessource detecterType(String urlOuChemin) {
            if (urlOuChemin == null) return AUTRE;
            String lower = urlOuChemin.trim().toLowerCase();
            if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("www.")) {
                return LIEN_WEB;
            }
            if (lower.endsWith(".pdf")) {
                return PDF;
            }
            if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".odt") || lower.endsWith(".txt")) {
                return DOCUMENT;
            }
            return AUTRE;
        }
    }

    private int id;
    private String isbn;
    private Integer malletteId;
    private String titre;
    private TypeRessource type = TypeRessource.LIEN_WEB;
    private String urlOuChemin;
    private String description = "";
    private LocalDate dateAjout = LocalDate.now();

    // Champs de commodité joints (optionnels)
    private String livreTitre = "";
    private String malletteTitre = "";

    public RessourcePedagogique() {}

    public RessourcePedagogique(String isbn, Integer malletteId, String titre, TypeRessource type, String urlOuChemin, String description) {
        this.isbn = isbn != null ? isbn.trim() : "";
        this.malletteId = malletteId;
        this.titre = titre != null ? titre.trim() : "";
        this.type = type != null ? type : TypeRessource.detecterType(urlOuChemin);
        this.urlOuChemin = urlOuChemin != null ? urlOuChemin.trim() : "";
        this.description = description != null ? description.trim() : "";
        this.dateAjout = LocalDate.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getIsbn() { return isbn; }
    public void setIsbn(String isbn) { this.isbn = isbn != null ? isbn.trim() : ""; }

    public Integer getMalletteId() { return malletteId; }
    public void setMalletteId(Integer malletteId) { this.malletteId = malletteId; }

    public String getTitre() { return titre; }
    public void setTitre(String titre) { this.titre = titre != null ? titre.trim() : ""; }

    public TypeRessource getType() { return type; }
    public void setType(TypeRessource type) { this.type = type; }

    public String getUrlOuChemin() { return urlOuChemin; }
    public void setUrlOuChemin(String urlOuChemin) { this.urlOuChemin = urlOuChemin != null ? urlOuChemin.trim() : ""; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description != null ? description.trim() : ""; }

    public LocalDate getDateAjout() { return dateAjout; }
    public void setDateAjout(LocalDate dateAjout) { this.dateAjout = dateAjout; }

    public String getDateAjoutFormatee() {
        return dateAjout != null ? dateAjout.format(DATE_FMT) : "";
    }

    public String getLivreTitre() { return livreTitre; }
    public void setLivreTitre(String livreTitre) { this.livreTitre = livreTitre; }

    public String getMalletteTitre() { return malletteTitre; }
    public void setMalletteTitre(String malletteTitre) { this.malletteTitre = malletteTitre; }

    public boolean isLienWeb() {
        return type == TypeRessource.LIEN_WEB || (urlOuChemin != null && (urlOuChemin.startsWith("http://") || urlOuChemin.startsWith("https://")));
    }

    public boolean isFichierLocal() {
        return !isLienWeb();
    }

    public String getIcone() {
        return type != null ? type.getIcone() : "📎";
    }

    @Override
    public String toString() {
        return "RessourcePedagogique{" +
                "id=" + id +
                ", titre='" + titre + '\'' +
                ", type=" + type +
                ", urlOuChemin='" + urlOuChemin + '\'' +
                ", isbn='" + isbn + '\'' +
                ", malletteId=" + malletteId +
                '}';
    }
}
