package com.asystent.kinowy.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.asystent.kinowy.network.GmailApiClient;
import com.asystent.kinowy.network.GmailApiService;
import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Repozytorium obsługujące komunikację z Gmail API.
 * <p>
 * Odpowiedzialności:
 * <ul>
 *   <li>Zarządzanie tokenem OAuth 2.0 (docelowo z Google Sign-In)</li>
 *   <li>Pobieranie listy wiadomości z załącznikami .xlsx</li>
 *   <li>Pobieranie danych załącznika (base64url)</li>
 * </ul>
 */
public class GmailRepository {

    private static final String XLSX_QUERY = "has:attachment filename:xlsx";
    private static final int DEFAULT_MAX_RESULTS = 10;

    private final GmailApiService apiService;

    public GmailRepository() {
        this.apiService = GmailApiClient.getInstance().getApiService();
    }

    // ─── OAuth ───────────────────────────────────────────────────────────

    /**
     * Ustawia token dostępu OAuth 2.0 pozyskany z Google Sign-In.
     * Token jest przekazywany do {@link GmailApiClient}, który automatycznie
     * dołącza go do każdego żądania HTTP.
     *
     * @param accessToken token z Google Sign-In / OAuth flow
     */
    public void setAccessToken(@Nullable String accessToken) {
        GmailApiClient.getInstance().setAccessToken(accessToken);
    }

    /**
     * Sprawdza, czy token dostępu jest ustawiony (niepusty).
     */
    public boolean isAuthenticated() {
        String token = GmailApiClient.getInstance().getAccessToken();
        return token != null && !token.isEmpty();
    }

    // ─── Pobieranie wiadomości ───────────────────────────────────────────

    /**
     * Asynchronicznie pobiera listę wiadomości Gmail zawierających załącznik .xlsx.
     *
     * @param callback Retrofit callback z wynikiem (JSON z listą wiadomości)
     */
    public void fetchMessageList(@NonNull Callback<JsonObject> callback) {
        fetchMessageList(DEFAULT_MAX_RESULTS, callback);
    }

    /**
     * Asynchronicznie pobiera listę wiadomości Gmail zawierających załącznik .xlsx.
     *
     * @param maxResults maksymalna liczba wyników
     * @param callback   Retrofit callback z wynikiem
     */
    public void fetchMessageList(int maxResults, @NonNull Callback<JsonObject> callback) {
        apiService.listMessages(XLSX_QUERY, maxResults).enqueue(callback);
    }

    /**
     * Pobiera szczegóły pojedynczej wiadomości (nagłówki, parts, snippet).
     *
     * @param messageId identyfikator wiadomości Gmail
     * @param callback  Retrofit callback z wynikiem
     */
    public void fetchMessageDetail(@NonNull String messageId,
                                   @NonNull Callback<JsonObject> callback) {
        apiService.getMessage(messageId).enqueue(callback);
    }

    // ─── Załączniki ──────────────────────────────────────────────────────

    /**
     * Asynchronicznie pobiera dane załącznika (body zakodowane w base64url).
     *
     * @param messageId    identyfikator wiadomości Gmail
     * @param attachmentId identyfikator załącznika
     * @param callback     Retrofit callback z wynikiem
     */
    public void fetchAttachment(@NonNull String messageId,
                                @NonNull String attachmentId,
                                @NonNull Callback<JsonObject> callback) {
        apiService.getAttachment(messageId, attachmentId).enqueue(callback);
    }
}
