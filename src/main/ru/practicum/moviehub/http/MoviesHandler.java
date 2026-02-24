package ru.practicum.moviehub.http;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.sun.net.httpserver.HttpExchange;
import ru.practicum.moviehub.AppLogger;
import ru.practicum.moviehub.api.ErrorResponse;
import ru.practicum.moviehub.model.Movie;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;

public class MoviesHandler extends BaseHttpHandler {
    private final MoviesStore store;
    private final AppLogger sysLog;
    private static final int MIN_YEAR = 1888;

    public MoviesHandler(MoviesStore store, AppLogger log) {
        super();
        this.store = store;
        this.sysLog = log;
    }

    public MoviesHandler(AppLogger log) {
        super();
        this.store = new MoviesStore();
        this.sysLog = log;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            sysLog.info("Запрос: " + ex.getRequestMethod() + " " + ex.getRequestURI());

            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            List<String> parts = Arrays.stream(path.split("/"))
                    .filter(s -> !s.isBlank())
                    .toList();

            if (parts.isEmpty()) {
                sysLog.warn("Не найдено: " + path);
                sendError(ex, 404, "Не найдено");
                return;
            }

            if (!"movies".equals(parts.get(0))) {
                sysLog.warn("Не найдено: " + path);
                sendError(ex, 404, "Не найдено");
                return;
            }

            if (parts.size() == 1) {
                handleCollection(ex, method);
                return;
            }

            if (parts.size() == 2) {
                handleItem(ex, method, parts.get(1));
                return;
            }

            sysLog.warn("Не найдено: " + path);
            sendError(ex, 404, "Не найдено");

        } catch (Exception e) {
            sysLog.error("Внутренняя ошибка сервера", e);
            sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleCollection(HttpExchange ex, String method) throws IOException {
        switch (method) {
            case METHOD_GET -> handleGetMovies(ex);
            case METHOD_POST -> handlePostMovie(ex);
            default -> sendError(ex, 405, "Method Not Allowed");
        }
    }

    private void handleItem(HttpExchange ex, String method, String idStr) throws IOException {
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException nfe) {
            sysLog.warn("Некорректный ID = " + idStr);
            sendError(ex, 400, "Некорректный ID = " + idStr);
            return;
        }

        switch (method) {
            case METHOD_GET -> handleGetMovieById(ex, id);
            case METHOD_DELETE -> handleDeleteMovie(ex, id);
            default -> sendError(ex, 405, "Method Not Allowed");
        }
    }

    private void handleGetMovies(HttpExchange ex) throws IOException {
        Map<String, String> params = parseQuery(ex.getRequestURI());

        if (params.isEmpty()) {
            sendJson(ex, 200, store.findAll());
        } else {
            try {
                int year = Integer.parseInt(params.get("year"));
                sendJson(ex, 200, store.findByYear(year));
            } catch (NumberFormatException e) {
                sysLog.error("Некорректный параметр - " + params.get("year"), e);
                sendError(ex, 400, "Некорректный параметр - " + params.get("year"));
            }
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        String query = uri.getQuery(); // работаем с параметрами

        if (query == null || query.isBlank())
            return new HashMap<>();

        return Arrays.stream(query.split("&"))
                .map(p -> p.split("=", 2))
                .filter(arr -> arr.length == 2)
                .collect(Collectors.toMap(
                        arr -> URLDecoder.decode(arr[0], StandardCharsets.UTF_8),
                        arr -> URLDecoder.decode(arr[1], StandardCharsets.UTF_8)
                ));
    }

    private void handlePostMovie(HttpExchange ex) throws IOException {
        if (!isJsonContentType(ex)) {
            sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String compact = body.replaceAll("\\s+", " ").trim();
        String preview = compact.length() > 1000 ? compact.substring(0, 1000) + "..." : compact;
        sysLog.info("Body: " + preview);

        try {
            JsonElement root = JsonParser.parseString(body);

            if (!root.isJsonObject()) {
                sysLog.warn("Ошибка валидации: ожидался JSON-объект");
                sendJson(ex, 422, new ErrorResponse("Ошибка валидации", List.of("ожидался JSON-объект")));
                return;
            }

            JsonObject obj = root.getAsJsonObject();
            List<String> errors = validate(obj);

            if (!errors.isEmpty()) {
                sysLog.warn("Ошибка валидации:" + errors.toString());
                sendJson(ex, 422, new ErrorResponse("Ошибка валидации", errors));
                return;
            }

            int year = obj.get("year").getAsInt();
            String title = obj.get("title").getAsString();
            Movie newMovie = store.create(title, year);
            sysLog.info("Добавлен фильм:" + newMovie.toString());
            sendJson(ex, 201, newMovie);
        } catch (JsonSyntaxException e) {
            sendError(ex, 400, "Некорректный JSON");
        }
    }

    private List<String> validate(JsonObject object) {

        List<String> errors = new ArrayList<>();

        String title = null;
        if (!object.has("title") || object.get("title").isJsonNull()) {
            errors.add("Название не должно быть пустым");
        } else if (!object.get("title").isJsonPrimitive() || !object.get("title").getAsJsonPrimitive().isString()) {
            errors.add("title должен быть строкой");
        } else {
            title = object.get("title").getAsString();
            if (title.isBlank())
                errors.add("Название не должно быть пустым");
            if (title.length() > 100)
                errors.add("Название должно быть не длиннее 100 символов");
        }

        Integer year = null;
        if (!object.has("year") || object.get("year").isJsonNull()) {
            errors.add("Год должен быть указан");
        } else if (!object.get("year").isJsonPrimitive() || !object.get("year").getAsJsonPrimitive().isNumber()) {
            errors.add("Год должен быть числом");
        } else {
            try {
                year = object.get("year").getAsInt(); // упадёт, если 1991.5
            } catch (NumberFormatException | UnsupportedOperationException e) {
                errors.add("Год должен быть целым числом");
            }
            if (year != null) {
                int maxYear = Year.now().getValue() + 1;
                if (year < MIN_YEAR || year > maxYear)
                    errors.add("Год должен быть между " + MIN_YEAR + " и " + maxYear);
            }
        }

        return errors;
    }

    private void handleGetMovieById(HttpExchange ex, long id) throws IOException {
        Optional<Movie> found = store.findById(id);
        if (found.isEmpty()) {
            sysLog.warn("Фильм не найден");
            sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendJson(ex, 200, found.get());
    }

    private void handleDeleteMovie(HttpExchange ex, long id) throws IOException {
        if (!store.delete(id)) {
            sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendNoContent(ex);
    }
}