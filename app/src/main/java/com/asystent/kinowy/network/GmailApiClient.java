package com.asystent.kinowy.network;

import androidx.annotation.NonNull;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.io.IOException;

/**
 * Singleton dostarczający skonfigurowaną instancję {@link GmailApiService}.
 * <p>
 * Klient OkHttp zawiera:
 * <ul>
 *   <li><b>Auth Interceptor</b> — dodaje nagłówek {@code Authorization: Bearer &lt;token&gt;}</li>
 *   <li><b>Logging Interceptor</b> — loguje żądania i odpowiedzi HTTP (poziom BODY)</li>
 * </ul>
 * <p>
 * Token dostępu ustawiany jest przez {@link #setAccessToken(String)}.
 * Na etapie szkieletu używa pustego tokena (mock).
 */
public class GmailApiClient {

    private static final String BASE_URL = "https://www.googleapis.com/gmail/v1/";

    private static volatile GmailApiClient INSTANCE;

    private final GmailApiService apiService;
    private volatile String accessToken = ""; // mock — docelowo z OAuth 2.0

    private GmailApiClient() {
        // --- Logging interceptor ---
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        // --- Auth interceptor ---
        Interceptor authInterceptor = new Interceptor() {
            @NonNull
            @Override
            public Response intercept(@NonNull Chain chain) throws IOException {
                Request original = chain.request();
                Request.Builder builder = original.newBuilder()
                        .header("Authorization", "Bearer " + accessToken)
                        .method(original.method(), original.body());
                return chain.proceed(builder.build());
            }
        };

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .addInterceptor(loggingInterceptor)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(GmailApiService.class);
    }

    // --- Singleton ---

    public static GmailApiClient getInstance() {
        if (INSTANCE == null) {
            synchronized (GmailApiClient.class) {
                if (INSTANCE == null) {
                    INSTANCE = new GmailApiClient();
                }
            }
        }
        return INSTANCE;
    }

    // --- Public API ---

    /**
     * Zwraca instancję {@link GmailApiService} gotową do wykonywania zapytań.
     */
    public GmailApiService getApiService() {
        return apiService;
    }

    /**
     * Ustawia token dostępu OAuth 2.0.
     * Token będzie automatycznie dodawany do każdego żądania HTTP.
     *
     * @param token access token z Google Sign-In / OAuth flow
     */
    public void setAccessToken(String token) {
        this.accessToken = token != null ? token : "";
    }

    /**
     * Zwraca aktualnie ustawiony token dostępu.
     */
    public String getAccessToken() {
        return accessToken;
    }
}
