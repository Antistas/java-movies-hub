package ru.practicum.moviehub.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.practicum.moviehub.store.MoviesStore;

public class MoviesApiTest {

    private static final String BASE = "http://localhost"; // базовая часть URL
    private static final int PORT = 8080;
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final String CT_JSON_WO_CHARSET = "application/json";
    private static MoviesServer server;
    private static HttpClient client;


    @BeforeAll
    static void beforeAll() {
        server = new MoviesServer(new MoviesStore(), PORT);
        server.start();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @BeforeEach
    void beforeEach() {
        server.clearStore();
    }

    @AfterAll
    static void afterAll() {
        server.stop();
    }

    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
        String body = resp.body().trim();
        assertTrue(body.startsWith("[") && body.endsWith("]"), "Ожидается JSON-массив");
    }

    @Test
    void shouldReturnOneMovie() throws Exception {
        String movieToPublish = "{\"title\": \"Name\", \"year\": 1991}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies"))
                .header("Content-Type", CT_JSON_WO_CHARSET)
                .POST(HttpRequest.BodyPublishers.ofString(movieToPublish))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
        String body = resp.body().trim();

        JsonElement el = JsonParser.parseString(body);
        assertTrue(el.isJsonArray(), "Ожидается JSON-массив");
        var arr = el.getAsJsonArray();
        assertEquals(1, arr.size(), "Должен быть ровно 1 фильм");

        JsonObject movie = arr.get(0).getAsJsonObject();
        assertEquals(1, movie.get("id").getAsInt());
        assertEquals("Name", movie.get("title").getAsString());
        assertEquals(1991, movie.get("year").getAsInt());
    }

    @Test
    void shouldReturnOneMovieByIdWithGetQuery() throws Exception {
        for (int i = 0; i < 5; i++) {
            int year = 1991 + i;
            String movieToPublish = "{\"title\": \"Movie Name\", \"year\": " + year + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE +":" + PORT + "/movies"))
                    .header("Content-Type", CT_JSON_WO_CHARSET)
                    .POST(HttpRequest.BodyPublishers.ofString(movieToPublish))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies/3"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
        String body = resp.body().trim();
        JsonElement el = JsonParser.parseString(body);
        JsonObject obj = el.getAsJsonObject();
        assertEquals("Movie Name", obj.get("title").getAsString());
        assertEquals(3, obj.get("id").getAsInt());
        assertEquals(1993, obj.get("year").getAsInt());
    }

    @Test
    void shouldReturnErrorWhenMovieIdNotExist() throws Exception {
        for (int i = 0; i < 5; i++) {
            int year = 1991 + i;
            String movieToPublish = "{\"title\": \"Movie Name\", \"year\": " + year + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE +":" + PORT + "/movies"))
                    .header("Content-Type", CT_JSON_WO_CHARSET)
                    .POST(HttpRequest.BodyPublishers.ofString(movieToPublish))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies/13"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, resp.statusCode(), "GET /movies должен вернуть 404");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
        String body = resp.body().trim();
        JsonElement el = JsonParser.parseString(body);
        JsonObject obj = el.getAsJsonObject();
        assertEquals("Фильм не найден", obj.get("error").getAsString());
    }

    @Test
    void shouldReturnSeveralMovies() throws Exception {

        for (int i = 0; i < 5; i++) {
            int year = 1991 + i;
            String movieToPublish = "{\"title\": \"Movie Name\", \"year\": " + year + "}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE +":" + PORT + "/movies"))
                    .header("Content-Type", CT_JSON_WO_CHARSET)
                    .POST(HttpRequest.BodyPublishers.ofString(movieToPublish))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");

        String body = resp.body();
        JsonElement el = JsonParser.parseString(body);
        assertTrue(el.isJsonArray(), "Ожидается JSON-массив");

        var arr = el.getAsJsonArray();
        assertEquals(5, arr.size(), "Должно быть 5 фильмов");

        Map<Integer, Integer> idToYear = new HashMap<>();
        for (JsonElement e : arr) {
            JsonObject m = e.getAsJsonObject();
            idToYear.put(m.get("id").getAsInt(), m.get("year").getAsInt());
            assertEquals("Movie Name", m.get("title").getAsString());
        }
        assertEquals(Map.of(1,1991, 2,1992, 3,1993, 4,1994, 5,1995), idToYear);
    }

    @Test
    void postMovies_whenEmptyBody_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies с пустым телом должен вернуть 415");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
        JsonElement jsonElement = JsonParser.parseString(resp.body());
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        assertEquals("Unsupported Media Type", jsonObject.get("error").getAsString(), "Ожидается ошибка неподдерживаемого типа");
    }

    @Test
    void postMovies_whenBodyWithNoData_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE +":" + PORT + "/movies"))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies с пустым телом должен вернуть 415");
        String contentTypeHeaderValue = resp.headers().firstValue("Content-Type").orElse("");
        assertEquals(CT_JSON, contentTypeHeaderValue, "Content-Type должен содержать формат данных и кодировку");
        JsonElement jsonElement = JsonParser.parseString(resp.body());
        JsonObject jsonObject = jsonElement.getAsJsonObject();
        assertEquals("Unsupported Media Type", jsonObject.get("error").getAsString(), "Ожидается ошибка неподдерживаемого типа");
    }
}