package fr.ecole.bcd.service;

import fr.ecole.bcd.model.Livre;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Service d'interrogation de l'API Google Books.
 * <p>
 * Permet de rechercher un livre par ISBN et d'en extraire les informations
 * bibliographiques (titre, auteur, éditeur, URL de couverture).
 * </p>
 *
 * <p>L'API est publique et ne nécessite pas de clé pour les requêtes simples
 * (avec un quota limité par IP).</p>
 *
 * @see <a href="https://developers.google.com/books/docs/v1/using">Google Books API</a>
 */
public class GoogleBooksService {

    private static final String API_URL = "https://www.googleapis.com/books/v1/volumes?q=isbn:%s&maxResults=1";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;

    public GoogleBooksService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Recherche un livre par ISBN via l'API Google Books.
     *
     * @param isbn l'ISBN-10 ou ISBN-13 (avec ou sans tirets)
     * @return un objet {@link Livre} pré-rempli, ou {@code null} si aucun résultat
     * @throws IOException en cas d'erreur réseau
     * @throws InterruptedException si la requête est interrompue
     */
    public Livre rechercherParIsbn(String isbn) throws IOException, InterruptedException {
        // Nettoyer l'ISBN (retirer les tirets et espaces)
        String cleanIsbn = isbn.replaceAll("[\\s-]", "");

        String url = String.format(API_URL,
                URLEncoder.encode(cleanIsbn, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.err.println("⚠️ Google Books API HTTP " + response.statusCode());
            return null;
        }

        return parseResponse(cleanIsbn, response.body());
    }

    /**
     * Parse la réponse JSON de Google Books et extrait les infos du premier résultat.
     */
    private Livre parseResponse(String isbn, String jsonBody) {
        JSONObject root = new JSONObject(jsonBody);

        int totalItems = root.optInt("totalItems", 0);
        if (totalItems == 0) {
            System.out.println("📭 Aucun résultat Google Books pour ISBN : " + isbn);
            return null;
        }

        JSONArray items = root.getJSONArray("items");
        JSONObject volumeInfo = items.getJSONObject(0).getJSONObject("volumeInfo");

        Livre livre = new Livre();
        livre.setIsbn(isbn);

        // Titre
        livre.setTitre(volumeInfo.optString("title", "Titre inconnu"));

        // Auteur(s) — concaténation si plusieurs
        if (volumeInfo.has("authors")) {
            JSONArray authors = volumeInfo.getJSONArray("authors");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < authors.length(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(authors.getString(i));
            }
            livre.setAuteur(sb.toString());
        }

        // Éditeur
        if (volumeInfo.has("publisher")) {
            livre.setEditeur(volumeInfo.getString("publisher"));
        }

        // Image de couverture (préférer le thumbnail pour la légèreté)
        if (volumeInfo.has("imageLinks")) {
            JSONObject images = volumeInfo.getJSONObject("imageLinks");
            String coverUrl = images.optString("thumbnail",
                    images.optString("smallThumbnail", null));
            // Google Books renvoie parfois des URLs en HTTP, on force HTTPS
            if (coverUrl != null && coverUrl.startsWith("http://")) {
                coverUrl = coverUrl.replace("http://", "https://");
            }
            livre.setCouvertureUrl(coverUrl);
        }

        // Essayer de récupérer le "bon" ISBN depuis la réponse
        if (volumeInfo.has("industryIdentifiers")) {
            JSONArray ids = volumeInfo.getJSONArray("industryIdentifiers");
            for (int i = 0; i < ids.length(); i++) {
                JSONObject idObj = ids.getJSONObject(i);
                String type = idObj.optString("type", "");
                // Préférer ISBN-13
                if ("ISBN_13".equals(type)) {
                    livre.setIsbn(idObj.getString("identifier"));
                    break;
                } else if ("ISBN_10".equals(type) && livre.getIsbn().equals(isbn)) {
                    livre.setIsbn(idObj.getString("identifier"));
                }
            }
        }

        System.out.println("📚 Google Books → " + livre);
        return livre;
    }
}
