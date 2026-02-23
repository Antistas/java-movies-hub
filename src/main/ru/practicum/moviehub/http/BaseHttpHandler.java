package ru.practicum.moviehub.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//import ru.practicum.moviehub.model.ErrorResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class BaseHttpHandler implements HttpHandler {
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final String CT_JSON_WO_CHARSET = "application/json";
    protected static final String CT_HEADER = "Content-Type";
    protected static final String METHOD_GET = "GET";
    protected static final String METHOD_POST = "POST";
    protected static final String METHOD_DELETE = "DELETE";

    protected final Gson gson;

    protected BaseHttpHandler() {
        this.gson = new GsonBuilder().create();
    }

    protected void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
        ex.close();
    }

    protected void sendNoContent(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", CT_JSON);
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    // ????
    protected void sendJson(HttpExchange ex, int status, Object body) throws IOException {
        sendJson(ex, status, gson.toJson(body));
    }

    /*
    protected void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, new ErrorResponse(message));
    }*/

    protected boolean isJsonContentType(HttpExchange ex) {
        List<String> headers = ex.getRequestHeaders().get(CT_HEADER);
        if (headers == null)
            return false;

        return headers.contains(CT_JSON_WO_CHARSET);
    }
}