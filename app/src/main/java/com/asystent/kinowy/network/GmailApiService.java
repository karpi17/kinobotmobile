package com.asystent.kinowy.network;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Interfejs Retrofit definiujący endpointy Gmail API v1.
 * <p>
 * Base URL: {@code https://www.googleapis.com/gmail/v1/}
 * <p>
 * Wymaga nagłówka {@code Authorization: Bearer <access_token>}
 * — dodawanego automatycznie przez {@link GmailApiClient}.
 */
public interface GmailApiService {

    /**
     * Pobiera listę wiadomości pasujących do podanego zapytania.
     *
     * @param query   filtr Gmail, np. {@code "has:attachment filename:xlsx"}
     * @param maxResults maksymalna liczba wyników (domyślnie 10)
     * @return surowy JSON z listą wiadomości (id, threadId)
     */
    @GET("users/me/messages")
    Call<JsonObject> listMessages(
            @Query("q") String query,
            @Query("maxResults") int maxResults
    );

    /**
     * Pobiera szczegóły pojedynczej wiadomości (nagłówki, parts, snippet).
     *
     * @param messageId identyfikator wiadomości Gmail
     * @return surowy JSON ze szczegółami wiadomości
     */
    @GET("users/me/messages/{messageId}")
    Call<JsonObject> getMessage(
            @Path("messageId") String messageId
    );

    /**
     * Pobiera dane załącznika (base64url-encoded body).
     *
     * @param messageId    identyfikator wiadomości Gmail
     * @param attachmentId identyfikator załącznika
     * @return surowy JSON z polem {@code "data"} (base64url)
     */
    @GET("users/me/messages/{messageId}/attachments/{attachmentId}")
    Call<JsonObject> getAttachment(
            @Path("messageId") String messageId,
            @Path("attachmentId") String attachmentId
    );
}
