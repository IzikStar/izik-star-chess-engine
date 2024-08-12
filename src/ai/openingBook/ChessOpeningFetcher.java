package ai.openingBook;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChessOpeningFetcher {
    public static void main(String[] args) {
        LichessApiService apiService = RetrofitClient.getClient().create(LichessApiService.class);
        Call<String> call = apiService.getOpenings();

        call.enqueue(new Callback<String>() {
            @Override
            public void onResponse(Call<String> call, Response<String> response) {
                if (response.isSuccessful()) {
                    String jsonResponse = response.body();
                    System.out.println("Openings Data: " + jsonResponse);
                } else {
                    System.out.println("Request Failed");
                }
            }

            @Override
            public void onFailure(Call<String> call, Throwable t) {
                t.printStackTrace();
            }
        });
    }
}

