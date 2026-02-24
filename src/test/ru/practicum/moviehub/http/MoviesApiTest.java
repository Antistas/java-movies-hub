package ru.practicum.moviehub.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import ru.practicum.moviehub.store.MoviesStore;

public class MoviesApiTest {

    private static final String BASE = "http://localhost"; // базовая часть URL
    private static final int PORT = 8080;
    protected static final String CT_JSON = "application/json; charset=UTF-8";
    protected static final String CT_JSON_WO_CHARSET = "application/json";
    private static MoviesServer server;
    private static HttpClient client;

    private static String url(String path) {
        return BASE + ":" + PORT + path;
    }

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

    private static void assertJsonContentType(HttpResponse<?> resp) {
        String ct = resp.headers().firstValue("Content-Type").orElse("").toLowerCase();
        assertTrue(ct.startsWith(CT_JSON_WO_CHARSET), "Content-Type должен начинаться с application/json");
        assertTrue(ct.contains("charset=utf-8"), "Content-Type должен содержать charset=UTF-8");
    }

    private static HttpResponse<String> postMovie(String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies")))
                .header("Content-Type", CT_JSON_WO_CHARSET)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void seedMoviesSequentialYears(int count, int startYear) throws Exception {
        for (int i = 0; i < count; i++) {
            int year = startYear + i;
            String body = "{\"title\": \"Movie Name\", \"year\": " + year + "}";
            HttpResponse<String> resp = postMovie(body);
            assertEquals(201, resp.statusCode(), "Seed POST должен вернуть 201");
        }
    }

    private static void seedMoviesAlternatingYears(int count, int year1, int year2) throws Exception {
        for (int i = 0; i < count; i++) {
            int year = (i % 2 == 0) ? year1 : year2;
            String body = "{\"title\": \"Movie Name\", \"year\": " + year + "}";
            HttpResponse<String> resp = postMovie(body);
            assertEquals(201, resp.statusCode(), "Seed POST должен вернуть 201");
        }
    }

    private static JsonArray getMoviesArray(String pathAndQuery) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url(pathAndQuery)))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET " + pathAndQuery + " должен вернуть 200");
        assertJsonContentType(resp);

        JsonElement el = JsonParser.parseString(resp.body());
        assertTrue(el.isJsonArray(), "Ожидается JSON-массив");
        return el.getAsJsonArray();
    }

    // запрашиваем все фильмы (в базе нет фильмов)
    @Test
    void getMovies_whenEmpty_returnsEmptyArray() throws Exception {
        JsonArray arr = getMoviesArray("/movies");
        assertEquals(0, arr.size(), "Ожидается пустой список фильмов");
    }

    // запрашиваем все фильмы (в базе 1 фильм)
    @Test
    void shouldReturnOneMovie() throws Exception {
        String movieToPublish = "{\"title\": \"Name\", \"year\": 1991}";
        HttpResponse<String> postResp = postMovie(movieToPublish);
        assertEquals(201, postResp.statusCode(), "POST /movies должен вернуть 201");

        JsonArray arr = getMoviesArray("/movies");
        assertEquals(1, arr.size(), "Должен быть ровно 1 фильм");

        JsonObject movie = arr.get(0).getAsJsonObject();
        assertEquals(1, movie.get("id").getAsInt());
        assertEquals("Name", movie.get("title").getAsString());
        assertEquals(1991, movie.get("year").getAsInt());
    }

    // получаем фильм по id
    @Test
    void shouldReturnOneMovieByIdWithGetQuery() throws Exception {
        seedMoviesSequentialYears(5, 1991);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies/3")))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, resp.statusCode(), "GET /movies/{id} должен вернуть 200");
        assertJsonContentType(resp);
        String body = resp.body().trim();
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Movie Name", jsonObject.get("title").getAsString());
        assertEquals(3, jsonObject.get("id").getAsInt());
        assertEquals(1993, jsonObject.get("year").getAsInt());
    }

    // получаем фильм по id, где id - не число
    @Test
    void shouldGetErrorWhenGetQueryWithError() throws Exception {
        seedMoviesSequentialYears(5, 1991);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies/abs")))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(jsonObject.get("error").getAsString().contains("Некорректный ID"), "Некорректный ID");
    }

    // 404 при запросе несуществующего фильма
    @Test
    void shouldReturnErrorWhenMovieIdNotExist() throws Exception {
        seedMoviesSequentialYears(5, 1991);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies/13")))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, resp.statusCode(), "GET /movies должен вернуть 404");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Фильм не найден", jsonObject.get("error").getAsString());
    }

    // проверяем вывод нескольких фильмов
    @Test
    void shouldReturnSeveralMovies() throws Exception {

        seedMoviesSequentialYears(5, 1991);
        var arr = getMoviesArray("/movies");
        assertEquals(5, arr.size(), "Должно быть 5 фильмов");

        Map<Integer, Integer> idToYear = new HashMap<>();
        for (JsonElement e : arr) {
            JsonObject m = e.getAsJsonObject();
            idToYear.put(m.get("id").getAsInt(), m.get("year").getAsInt());
            assertEquals("Movie Name", m.get("title").getAsString());
        }
        assertEquals(Map.of(1,1991, 2,1992, 3,1993, 4,1994, 5,1995), idToYear);
    }

    // отправляем пустоту
    @Test
    void postMovies_whenEmptyBody_returnsEmptyArray() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies")))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies с пустым телом должен вернуть 415");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Unsupported Media Type", jsonObject.get("error").getAsString(), "Ожидается ошибка неподдерживаемого типа");
    }

    // отправляем пустой json
    @Test
    void postMoviesWOContentType() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies")))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(415, resp.statusCode(), "POST /movies с пустым телом должен вернуть 415");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Unsupported Media Type", jsonObject.get("error").getAsString(), "Ожидается ошибка неподдерживаемого типа");
    }

    // отправляем нормальный фильм
    @Test
    void postMoviesWithCorrectData() throws Exception {
        String movieToPublish = "{\"title\": \"Movie Name\", \"year\": 2000}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(201, resp.statusCode(), "POST /movies с корректными данными должен вернуть 201");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals(1, jsonObject.get("id").getAsInt(), "Ожидается id фильма");
    }

    // отправляем фильм с некорректным годом > текущего
    @Test
    void postMoviesWithIncorrectYearMoreThanCurrent() throws Exception {
        String movieToPublish = "{\"title\": \"Movie Name\", \"year\": 2100}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с некорректными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertTrue(details.get(0).getAsString().contains("Год должен быть между 1888"), "Некорректный год");
    }

    // отправляем фильм с некорректным годом < минимального
    @Test
    void postMoviesWithIncorrectYearLessThanMinimal() throws Exception {
        String movieToPublish = "{\"title\": \"Movie Name\", \"year\": 1300}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с некорректными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertTrue(details.get(0).getAsString().contains("Год должен быть между 1888"), "Некорректный год");
    }

    // отправляем фильм с пустым названием
    @Test
    void postMoviesWithEmptyTitle() throws Exception {
        String movieToPublish = "{\"title\": \"\", \"year\": 2000}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с некорректными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertEquals("Название не должно быть пустым", details.get(0).getAsString(), "Некорректный название");
    }

    // отправляем фильм со слишком длинным названием
    @Test
    void postMoviesWithTooLongTitle() throws Exception {
        String title = "a".repeat(101);
        String movieToPublish = "{\"title\": \"" + title + "\", \"year\": 2000}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с некорректными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertEquals("Название должно быть не длиннее 100 символов", details.get(0).getAsString(), "Некорректное название");
    }

    // отправляем фильм без года
    @Test
    void postMoviesNoYear() throws Exception {
        String movieToPublish = "{\"title\": \"Название100символов\"}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с некорректными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertEquals("Год должен быть указан", details.get(0).getAsString(), "Год должен быть указан");
    }

    // отправляем некорректный JSON
    @Test
    void postMoviesWithEmptyTitleAndYear() throws Exception {
        String movieToPublish = "{\"title\": \"\", \"year\":}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(400, resp.statusCode(), "POST /movies с некорректным JSON должен вернуть 400");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Некорректный JSON", jsonObject.get("error").getAsString(), "Некорректный JSON");
    }

    // отправляем пустое название и некорректный год
    @Test
    void postMoviesWithIncorretTitleAndYear() throws Exception {
        String movieToPublish = "{\"title\": \"\", \"year\": 2100}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с невалидными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertEquals("Название не должно быть пустым", details.get(0).getAsString(), "Название не должно быть пустым");
        assertTrue(details.get(1).getAsString().contains("Год должен быть между 1888"), "Год должен быть между 1888 и текущим");
    }

    // отправляем пустое название и некорректный год
    @Test
    void postMoviesWithNoTitleAndIncorrectYear() throws Exception {
        String movieToPublish = "{\"year\": \"asda\"}";
        HttpResponse<String> resp = postMovie(movieToPublish);
        assertEquals(422, resp.statusCode(), "POST /movies с невалидными данными должен вернуть 422");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Ошибка валидации", jsonObject.get("error").getAsString(), "Ошибка валидации");
        JsonArray details = jsonObject.get("details").getAsJsonArray();
        assertEquals("Название не должно быть пустым", details.get(0).getAsString(), "Название не должно быть пустым");
        assertEquals("Год должен быть числом", details.get(1).getAsString(), "Год должен быть числом");
    }

    // отправляем фильм с некорректным типом данных
    @Test
    void postMoviesWithIncorrectContentType() throws Exception {
        String movieToPublish = "{\"title\": \"Movie Name\", \"year\": 1991}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies")))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(movieToPublish, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(415, resp.statusCode(), "POST /movies с неправильным типом должен вернуть 415");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Unsupported Media Type", jsonObject.get("error").getAsString(), "Unsupported Media Type");
    }

    // отправляем фильм с некорректным методом
    @Test
    void postMoviesWithUnsupportedMethod() throws Exception {
        String movieToPublish = "{\"title\": \"Movie Name\", \"year\": 1991}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies")))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofString(movieToPublish))
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(405, resp.statusCode(), "PUT /movies с неправильным методом должен вернуть 405");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Method Not Allowed", jsonObject.get("error").getAsString(), "Некорректный метод");
    }

    // удаляем фильм с существующим ID
    @Test
    void deleteMovieWithCorrectID() throws Exception {
        seedMoviesSequentialYears(5, 1991);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies/1")))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(204, resp.statusCode(), "DELETE /movies/{id} должен вернуть 204");
        assertJsonContentType(resp);
        assertTrue(resp.body() == null || resp.body().isBlank(), "Для 204 тело должно быть пустым");

        req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies/1")))
                .GET()
                .build();
        resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(404, resp.statusCode(), "GET /movies должен вернуть 404");
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Фильм не найден", jsonObject.get("error").getAsString(), "Фильм не найден");
    }

    // удаляем фильм с несуществующим ID
    @Test
    void deleteMovieWithIncorrectID() throws Exception {
        seedMoviesSequentialYears(5, 1991);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies/6")))
                .DELETE()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertJsonContentType(resp);
        assertEquals(404, resp.statusCode(), "DELETE /movies/{id} должен вернуть 404");
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertEquals("Фильм не найден", jsonObject.get("error").getAsString(), "Фильм не найден");
    }

    // ищем фильм по году
    @Test
    void getMovieWithGetQuery() throws Exception {
        seedMoviesSequentialYears(5, 1991);
        JsonArray jsonArray = getMoviesArray("/movies?year=1992");
        assertEquals(1, jsonArray.size(), "Должен быть 1 фильм");
        Map<Integer, Integer> idToYear = new HashMap<>();
        for (JsonElement e : jsonArray) {
            JsonObject m = e.getAsJsonObject();
            idToYear.put(m.get("id").getAsInt(), m.get("year").getAsInt());
            assertEquals("Movie Name", m.get("title").getAsString());
        }
        assertEquals(Map.of(2,1992), idToYear);
    }

    // ищем фильмы по году
    @Test
    void getMoviesWithGetQuery() throws Exception {
        seedMoviesAlternatingYears(5, 1991, 1992);
        JsonArray jsonArray = getMoviesArray("/movies?year=1992");

        assertEquals(2, jsonArray.size(), "Должен быть 2 фильма");

        Map<Integer, Integer> idToYear = new HashMap<>();
        for (JsonElement e : jsonArray) {
            JsonObject m = e.getAsJsonObject();
            idToYear.put(m.get("id").getAsInt(), m.get("year").getAsInt());
            assertEquals("Movie Name", m.get("title").getAsString());
        }
        assertEquals(Map.of(2,1992, 4, 1992), idToYear);
    }

    // ищем фильмы по году, параметр год некорректный
    @Test
    void getMoviesWithIncorrectGetQuery() throws Exception {
        String param = "1asasd";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies?year=" + param)))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(400, resp.statusCode(), "GET /movies должен вернуть 400");
        assertJsonContentType(resp);
        JsonObject jsonObject = JsonParser.parseString(resp.body()).getAsJsonObject();
        assertTrue(jsonObject.get("error").getAsString().contains("Некорректный параметр"), "GET должен вернуть ошибку");
    }

    // ищем фильмы по году, таких фильмов нет
    @Test
    void getMoviesWithGetQueryWhenNoMoviesByYear() throws Exception {
        seedMoviesSequentialYears(5, 1991);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url("/movies?year=2000")))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertJsonContentType(resp);
        assertEquals(200, resp.statusCode(), "GET /movies должен вернуть 200");
        JsonElement el = JsonParser.parseString(resp.body());
        assertTrue(el.isJsonArray(), "Ожидается JSON-массив");
        assertEquals(0, el.getAsJsonArray().size(), "GET должен вернуть пустой массив");
    }
}