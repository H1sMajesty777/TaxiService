package ru.mirea.taxi.mobile.net;

import android.content.Context;
import com.google.gson.Gson;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import ru.mirea.taxi.mobile.BuildConfig;
import ru.mirea.taxi.mobile.net.Models.*;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Gateway {
    private static final String NATIVE = "NATIVE";
    private final SecureTokenStore storage;
    private final TaxiApi raw;
    private final TaxiApi api;
    private volatile String accessToken;

    public Gateway(Context context) {
        storage = new SecureTokenStore(context.getApplicationContext());
        var base = new OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(18, TimeUnit.SECONDS).addInterceptor(chain -> {
                    var request = chain.request();
                    if (request.header("X-Correlation-ID") == null) {
                        request = request.newBuilder().header("X-Correlation-ID", UUID.randomUUID().toString()).build();
                    }
                    return chain.proceed(request);
                }).build();
        raw = retrofit(base);
        api = retrofit(base.newBuilder().addInterceptor(chain -> {
            String current = accessToken;
            Request request = chain.request();
            if (current != null && request.header("Authorization") == null) {
                request = request.newBuilder().header("Authorization", "Bearer " + current).build();
            }
            return chain.proceed(request);
        }).authenticator((route, response) -> retry(response)).build());
    }

    public TaxiApi api() {
        return api;
    }

    public synchronized Profile login(String email, String password) throws IOException {
        return remember(call(raw.login(new Login(email, password, NATIVE))));
    }

    public synchronized Profile restore() throws IOException {
        String refresh = storage.read();
        if (refresh == null) {
            return null;
        }
        try {
            return remember(call(raw.refresh(new Refresh(NATIVE, refresh))));
        } catch (ApiFailure error) {
            if (error.status() == 401) {
                clear();
            }
            throw error;
        }
    }

    public Profile changePassword(String current, String replacement) throws IOException {
        Session session = call(api.password(new PasswordChange(current, replacement, NATIVE)));
        synchronized (this) {
            return remember(session);
        }
    }

    public synchronized void logout() throws IOException {
        String refresh = storage.read();
        try {
            if (refresh != null) {
                call(raw.logout(new Refresh(NATIVE, refresh)));
            }
        } finally {
            clear();
        }
    }

    public synchronized void clear() {
        accessToken = null;
        storage.clear();
    }

    private synchronized Request retry(Response response) throws IOException {
        if (response.priorResponse() != null || response.request().header("Authorization") == null) {
            return null;
        }
        String sent = response.request().header("Authorization");
        if (accessToken == null) {
            return null;
        }
        if (Objects.equals(sent, "Bearer " + accessToken)) {
            try {
                if (restore() == null) {
                    clear();
                    return null;
                }
            } catch (ApiFailure error) {
                clear();
                return null;
            }
        }
        return response.request().newBuilder().header("Authorization", "Bearer " + accessToken).build();
    }

    private Profile remember(Session session) throws IOException {
        if (session == null || session.refreshToken() == null || session.accessToken() == null
                || session.user() == null) {
            throw new IOException("Ответ сервера не содержит сессию.");
        }
        try {
            storage.write(session.refreshToken());
        } catch (IOException error) {
            clear();
            throw error;
        }
        accessToken = session.accessToken();
        return session.user();
    }

    private static TaxiApi retrofit(OkHttpClient client) {
        return new Retrofit.Builder().baseUrl(BuildConfig.BASE_URL).client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(TaxiApi.class);
    }

    public static <T> T call(Call<T> request) throws IOException {
        var response = request.execute();
        if (response.isSuccessful()) {
            return response.body();
        }
        String message = "Запрос завершился с кодом " + response.code() + ".";
        try (ResponseBody body = response.errorBody()) {
            if (body != null) {
                ErrorResponse failure = new Gson().fromJson(body.charStream(), ErrorResponse.class);
                if (failure != null && failure.message() != null) {
                    message = failure.message();
                }
            }
        } catch (com.google.gson.JsonParseException ignored) {
            // Ответ прокси может не содержать JSON.
        }
        throw new ApiFailure(response.code(), message);
    }

    private record ErrorResponse(String message) {}

    public static final class ApiFailure extends IOException {
        private final int status;

        ApiFailure(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }
}
