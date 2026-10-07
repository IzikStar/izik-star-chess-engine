package web;

import com.google.gson.JsonObject;
import io.javalin.http.Context;

/** Sends a JSON object as the response body. */
final class Json {

    private Json() {}

    static void send(Context ctx, JsonObject body) {
        ctx.contentType("application/json").result(body.toString());
    }
}
