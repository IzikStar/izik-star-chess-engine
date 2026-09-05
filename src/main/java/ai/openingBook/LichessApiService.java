package ai.openingBook;

import retrofit2.Call;
import retrofit2.http.GET;

public interface LichessApiService {
    @GET("api/lichess_opening")
    Call<String> getOpenings();
}
