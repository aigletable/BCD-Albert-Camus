package fr.ecole.bcd.model;

import java.util.List;
import java.util.Objects;

/**
 * Modèle représentant un meuble, un îlot, un bac ou un mobilier de la BCD
 * dans le plan 2D et la maquette 3D de l'école Albert Camus.
 *
 * <p>Les coordonnées métriques (x1, x2, z1, z2, hauteur) sont exprimées en mètres,
 * dans le repère de la pièce 9,0 m (largeur Est-Ouest) × 7,5 m (profondeur Nord-Sud).</p>
 */
public class ZoneBcd {

    /** Face ou compartiment d'un meuble de la BCD. */
    public record FaceBcd(String side, String content) {}

    /** Catégorie de mobilier. */
    public enum CategorieMobilier {
        SHELF("Étagère", "#FDE047", "#CA8A04", "#713F12"),
        BIN("Bac à livres", "#67E8F9", "#0891B2", "#164E63"),
        SEATING("Mobilier d'assise", "#FB7185", "#E11D48", "#881337"),
        DESK("Bureau / Table", "#BAE6FD", "#38BDF8", "#0C4A6E");

        private final String libelle;
        private final String colorFill;
        private final String colorStroke;
        private final String colorText;

        CategorieMobilier(String libelle, String colorFill, String colorStroke, String colorText) {
            this.libelle = libelle;
            this.colorFill = colorFill;
            this.colorStroke = colorStroke;
            this.colorText = colorText;
        }

        public String getLibelle() { return libelle; }
        public String getColorFill() { return colorFill; }
        public String getColorStroke() { return colorStroke; }
        public String getColorText() { return colorText; }
    }

    private final String id;
    private final Integer numero;
    private final String nom;
    private final String nomCourt;
    private final String typeLabel;
    private final String couleurReelle;
    private final CategorieMobilier categorie;
    private final double x1;
    private final double x2;
    private final double z1;
    private final double z2;
    private final double hauteur;
    private final String cotePrefix;
    private final int capacite;
    private final String style3d;
    private final List<FaceBcd> faces;
    private final String motsCles;

    public ZoneBcd(String id, Integer numero, String nom, String nomCourt, String typeLabel,
                   String couleurReelle, CategorieMobilier categorie,
                   double x1, double x2, double z1, double z2, double hauteur,
                   String cotePrefix, int capacite, String style3d,
                   List<FaceBcd> faces, String motsCles) {
        this.id = Objects.requireNonNull(id);
        this.numero = numero;
        this.nom = Objects.requireNonNull(nom);
        this.nomCourt = nomCourt != null ? nomCourt : nom;
        this.typeLabel = typeLabel != null ? typeLabel : "";
        this.couleurReelle = couleurReelle != null ? couleurReelle : "";
        this.categorie = categorie != null ? categorie : CategorieMobilier.SHELF;
        this.x1 = x1;
        this.x2 = x2;
        this.z1 = z1;
        this.z2 = z2;
        this.hauteur = hauteur;
        this.cotePrefix = cotePrefix != null ? cotePrefix : "";
        this.capacite = capacite;
        this.style3d = style3d != null ? style3d : "default";
        this.faces = faces != null ? List.copyOf(faces) : List.of();
        this.motsCles = motsCles != null ? motsCles.toLowerCase() : "";
    }

    public String getId() { return id; }
    public Integer getNumero() { return numero; }
    public boolean hasNumero() { return numero != null; }
    public String getNom() { return nom; }
    public String getNomCourt() { return nomCourt; }
    public String getTypeLabel() { return typeLabel; }
    public String getCouleurReelle() { return couleurReelle; }
    public CategorieMobilier getCategorie() { return categorie; }
    public double getX1() { return x1; }
    public double getX2() { return x2; }
    public double getZ1() { return z1; }
    public double getZ2() { return z2; }
    public double getHauteur() { return hauteur; }
    public String getCotePrefix() { return cotePrefix; }
    public int getCapacite() { return capacite; }
    public String getStyle3d() { return style3d; }
    public List<FaceBcd> getFaces() { return faces; }
    public String getMotsCles() { return motsCles; }

    public double getWidth() { return Math.abs(x2 - x1); }
    public double getDepth() { return Math.abs(z2 - z1); }
    public double getCenterX() { return (x1 + x2) / 2.0; }
    public double getCenterZ() { return (z1 + z2) / 2.0; }

    /**
     * Vérifie si un point (x, z) dans le repère de la pièce est à l'intérieur de l'emprise du meuble.
     */
    public boolean containsPoint(double x, double z) {
        double minX = Math.min(x1, x2);
        double maxX = Math.max(x1, x2);
        double minZ = Math.min(z1, z2);
        double maxZ = Math.max(z1, z2);
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Recherche textuelle dans les propriétés du meuble.
     */
    public boolean matchesQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String q = query.trim().toLowerCase();
        if (nom.toLowerCase().contains(q)) return true;
        if (nomCourt.toLowerCase().contains(q)) return true;
        if (typeLabel.toLowerCase().contains(q)) return true;
        if (cotePrefix.toLowerCase().contains(q)) return true;
        if (motsCles.contains(q)) return true;
        if (numero != null && String.valueOf(numero).equals(q)) return true;
        for (FaceBcd face : faces) {
            if (face.side().toLowerCase().contains(q) || face.content().toLowerCase().contains(q)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Icône représentative selon la catégorie et le meuble.
     */
    public String getIcon() {
        if ("2".equals(id)) return "💬";
        if ("8".equals(id)) return "🌿";
        if ("7".equals(id)) return "🎲";
        if ("9".equals(id) || "10".equals(id)) return "🎒";
        if ("11".equals(id)) return "✨";
        return switch (categorie) {
            case SHELF -> "📚";
            case BIN -> "📦";
            case SEATING -> "🛋️";
            case DESK -> "🖥️";
        };
    }
}
