package ru.mirea.taxi.mobile.net;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;
import ru.mirea.taxi.mobile.net.Models.*;

import java.util.List;
import java.util.Map;

public interface TaxiApi {
    @POST("api/auth/login") Call<Session> login(@Body Login input);
    @POST("api/auth/refresh") Call<Session> refresh(@Body Refresh input);
    @POST("api/auth/logout") Call<Void> logout(@Body Refresh input);
    @GET("api/auth/me") Call<Profile> me();
    @POST("api/auth/password") Call<Session> password(@Body PasswordChange input);

    @GET("api/rides") Call<Page<Ride>> rides(@QueryMap Map<String, String> filters);
    @GET("api/rides/{id}") Call<Ride> ride(@Path("id") String id);
    @POST("api/rides") Call<Ride> createRide(@Body RideInput input);
    @PUT("api/rides/{id}") Call<Ride> updateRide(@Path("id") String id, @Body RideInput input);
    @DELETE("api/rides/{id}") Call<Ride> archive(@Path("id") String id, @Query("version") long version);
    @POST("api/rides/{id}/restore") Call<Ride> restore(@Path("id") String id, @Query("version") long version);
    @DELETE("api/rides/{id}/permanent") Call<Void> deleteRide(@Path("id") String id, @Query("version") long version);
    @POST("api/rides/bulk") Call<BulkResult> bulk(@Body BulkInput input);

    @GET("api/rides/{id}/notes") Call<List<Note>> notes(@Path("id") String id);
    @POST("api/rides/{id}/notes") Call<Note> createNote(@Path("id") String id, @Body NoteInput input);
    @PUT("api/rides/{id}/notes/{noteId}") Call<Note> updateNote(@Path("id") String id,
            @Path("noteId") String noteId, @Body NoteInput input);
    @DELETE("api/rides/{id}/notes/{noteId}") Call<Void> deleteNote(@Path("id") String id, @Path("noteId") String noteId);

    @GET("api/tariffs") Call<List<Tariff>> tariffs();
    @POST("api/tariffs") Call<Tariff> createTariff(@Body TariffInput input);
    @PUT("api/tariffs/{id}") Call<Tariff> updateTariff(@Path("id") String id, @Body TariffInput input);
    @GET("api/passengers") Call<List<Passenger>> passengers();
    @GET("api/analytics/summary") Call<Summary> summary();

    @Multipart @POST("api/rides/import/preview")
    Call<CsvPreview> preview(@Query("mode") String mode, @Part MultipartBody.Part file);
    @Multipart @POST("api/rides/import/apply")
    Call<CsvApplied> apply(@Query("mode") String mode, @Part MultipartBody.Part file);
    @Streaming @GET("api/rides/export") Call<ResponseBody> export(@QueryMap Map<String, String> filters);

    @GET("api/admin/users") Call<Page<Profile>> users(@Query("page") int page, @Query("size") int size);
    @POST("api/admin/users") Call<Profile> createUser(@Body AccountInput input);
    @PUT("api/admin/users/{id}") Call<Profile> updateUser(@Path("id") String id, @Body ProfileInput input);
    @POST("api/admin/users/{id}/reset-password")
    Call<Profile> resetPassword(@Path("id") String id, @Body TemporaryPassword input);

    @GET("api/admin/audit") Call<Page<Audit>> audit(@Query("q") String query, @Query("sort") String sort,
            @Query("dir") String direction, @Query("page") int page, @Query("size") int size);
}
