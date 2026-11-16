package fr.ecole.bcd.service;

import fr.ecole.bcd.database.RessourcePedagogiqueDao;
import fr.ecole.bcd.model.RessourcePedagogique;
import fr.ecole.bcd.model.RessourcePedagogique.TypeRessource;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service métier pour la gestion et l'ouverture rapide des ressources pédagogiques
 * (liens web et documents PDF / Word locaux).
 *
 * @author JC ART
 * @since 2026-11-16
 */
public class RessourcePedagogiqueService {

    private static final String STORAGE_DIR = "documents_bcd/ressources";
    private final RessourcePedagogiqueDao dao;

    public RessourcePedagogiqueService() {
        this.dao = new RessourcePedagogiqueDao();
    }

    public RessourcePedagogiqueService(RessourcePedagogiqueDao dao) {
        this.dao = dao;
    }

    /**
     * Ouvre une ressource pédagogique dans le navigateur ou le lecteur système natif.
     * Supporte macOS, Windows et Linux avec double stratégie (Desktop API puis fallback shell).
     *
     * @param ressource la ressource à ouvrir
     * @throws IOException si l'ouverture échoue
     */
    public void ouvrirRessource(RessourcePedagogique ressource) throws IOException {
        if (ressource == null || ressource.getUrlOuChemin() == null || ressource.getUrlOuChemin().isBlank()) {
            throw new IllegalArgumentException("La ressource est vide ou n'a aucun lien.");
        }

        String target = ressource.getUrlOuChemin().trim();

        if (ressource.isLienWeb()) {
            ouvrirLienWeb(target);
        } else {
            ouvrirFichierLocal(target);
        }
    }

    /**
     * Ouvre un lien URL dans le navigateur par défaut de l'ordinateur.
     */
    public void ouvrirLienWeb(String url) throws IOException {
        String cleanUrl = normaliserUrl(url);

        // Stratégie 1 : Java Desktop API
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(URI.create(cleanUrl));
                    return;
                } catch (Exception ignored) {}
            }
        }

        // Stratégie 2 : Fallback système
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", cleanUrl).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", cleanUrl).start();
            } else {
                new ProcessBuilder("xdg-open", cleanUrl).start();
            }
        } catch (Exception e) {
            throw new IOException("Impossible d'ouvrir le lien dans le navigateur : " + e.getMessage(), e);
        }
    }

    /**
     * Ouvre un fichier local (PDF, Word, etc.) dans l'application par défaut de l'ordinateur.
     */
    public void ouvrirFichierLocal(String chemin) throws IOException {
        File file = new File(chemin);
        if (!file.exists()) {
            throw new IOException("Le fichier est introuvable sur le disque : " + chemin);
        }

        // Stratégie 1 : Java Desktop API
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.OPEN)) {
                try {
                    desktop.open(file);
                    return;
                } catch (Exception ignored) {}
            }
        }

        // Stratégie 2 : Fallback système
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                new ProcessBuilder("open", file.getAbsolutePath()).start();
            } else if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "\"\"", file.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            throw new IOException("Impossible d'ouvrir le fichier : " + e.getMessage(), e);
        }
    }

    /**
     * Copie un fichier sélectionné dans le dossier local de l'application
     * ({@code documents_bcd/ressources/}) pour garantir sa pérennité.
     *
     * @param sourceFile fichier sélectionné par l'utilisateur
     * @return le chemin absolu du fichier copié
     * @throws IOException si la copie échoue
     */
    public String stockerFichierLocal(File sourceFile) throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            throw new IOException("Fichier source introuvable.");
        }

        Path targetDir = Paths.get(STORAGE_DIR).toAbsolutePath();
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        String origName = sourceFile.getName();
        String safeName = origName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String destFileName = timestamp + "_" + safeName;

        Path targetPath = targetDir.resolve(destFileName);
        Files.copy(sourceFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        return targetPath.toAbsolutePath().toString();
    }

    /**
     * Ajoute et persiste une nouvelle ressource web ou document.
     */
    public RessourcePedagogique ajouterRessource(String isbn, Integer malletteId, String titre,
                                                TypeRessource type, String urlOuChemin, String description)
            throws SQLException, IOException {

        if (titre == null || titre.isBlank()) {
            throw new IllegalArgumentException("Le titre de la ressource est obligatoire.");
        }
        if (urlOuChemin == null || urlOuChemin.isBlank()) {
            throw new IllegalArgumentException("L'adresse URL ou le chemin de fichier est obligatoire.");
        }

        String finalChemin = urlOuChemin.trim();
        if (type == TypeRessource.LIEN_WEB) {
            finalChemin = normaliserUrl(finalChemin);
        } else {
            // Si c'est un fichier local existant hors du dossier de stockage, on l'importe
            File f = new File(finalChemin);
            if (f.exists() && !f.getAbsolutePath().contains(STORAGE_DIR)) {
                try {
                    finalChemin = stockerFichierLocal(f);
                } catch (Exception ignored) {
                    // Si la copie échoue pour une raison quelconque, on conserve le chemin d'origine
                }
            }
        }

        RessourcePedagogique r = new RessourcePedagogique(isbn, malletteId, titre.trim(), type, finalChemin, description);
        dao.insert(r);
        return r;
    }

    public List<RessourcePedagogique> getRessourcesParIsbn(String isbn) throws SQLException {
        return dao.findByIsbn(isbn);
    }

    public List<RessourcePedagogique> getRessourcesParMallette(int malletteId) throws SQLException {
        return dao.findByMallette(malletteId);
    }

    public List<RessourcePedagogique> getToutesRessourcesPourMallette(int malletteId) throws SQLException {
        return dao.findAllForMallette(malletteId);
    }

    public int compterRessourcesParIsbn(String isbn) throws SQLException {
        return dao.countByIsbn(isbn);
    }

    public int compterRessourcesPourMallette(int malletteId) throws SQLException {
        return dao.countForMallette(malletteId);
    }

    public boolean supprimerRessource(int id) throws SQLException {
        return dao.delete(id);
    }

    public boolean modifierRessource(RessourcePedagogique r) throws SQLException {
        return dao.update(r);
    }

    /**
     * S'assure qu'une URL commence par un protocole web valide.
     */
    public static String normaliserUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }
}
