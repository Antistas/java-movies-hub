package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpExchange;
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

    public MoviesHandler(MoviesStore store) {
        super();
        this.store = store;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod();

            if ("/movies".equals(path) || "/movies/".equals(path)) {
                handleCollection(ex, method);
                return;
            }

            if (path.startsWith("/movies/")) {
                handleItem(ex, method, path);
                return;
            }

            //sendError(ex, 404, "Не найдено");
        } catch (Exception e) {
            // на этапе скелета пусть будет 500
            //sendError(ex, 500, "Внутренняя ошибка сервера");
        }
    }

    private void handleCollection(HttpExchange ex, String method) throws IOException {
        switch (method) {
            case METHOD_GET -> handleGetMovies(ex);
            case METHOD_POST -> handlePostMovie(ex);
            //default -> sendError(ex, 405, "Method Not Allowed");
        }
    }

    private void handleItem(HttpExchange ex, String method, String path) throws IOException {

        String idStr = path.substring("/movies/".length());

        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException nfe) {
            //sendError(ex, 400, "Некорректный ID");
            return;
        }

        switch (method) {
            case METHOD_GET -> handleGetMovieById(ex, id);
            case METHOD_DELETE -> handleDeleteMovie(ex, id);
            //default -> sendError(ex, 405, "Method Not Allowed");
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
                return;
            } catch (NumberFormatException e) {
                System.out.println("Некорректный год");
                //sendJson(ex, 200, store.findByYear(year));
                return;
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
            //sendError(ex, 415, "Unsupported Media Type");
            return;
        }

        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        Movie incoming;

        try {
            //incoming = gson.fromJson(body, Movie.class);
        } catch (Exception e) {
            //sendError(ex, 400, "Некорректный JSON");
            return;
        }

        List<String> errors = new ArrayList<>(); //validate(incoming);
        if (!errors.isEmpty()) {
            //sendJson(ex, 422, new ru.practicum.moviehub.model.ErrorResponse("Ошибка валидации", errors));
            return;
        }

        //Movie created = store.create(incoming.getTitle(), incoming.getYear());
        //sendJson(ex, 201, created);
    }

    private List<String> validate(Movie m) {

        List<String> errors = new ArrayList<>();

        if (m == null) {
            errors.add("тело запроса пустое");
            return errors;
        }

        String title = m.getTitle();

        if (title == null || title.isBlank())
            errors.add("название не должно быть пустым");
        if (title != null && title.length() > 100)
            errors.add("название должно быть не длиннее 100 символов");

        int year = m.getYear();
        int maxYear = Year.now().getValue() + 1;
        if (year < 1888 || year > maxYear)
            errors.add("год должен быть между 1888 и " + maxYear);

        return errors;
    }

    private void handleGetMovieById(HttpExchange ex, long id) throws IOException {
        Optional<Movie> found = store.findById(id);
        if (found.isEmpty()) {
            //sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendJson(ex, 200, found.get());
    }

    private void handleDeleteMovie(HttpExchange ex, long id) throws IOException {
        boolean deleted = store.delete(id);
        if (!deleted) {
            //sendError(ex, 404, "Фильм не найден");
            return;
        }
        sendNoContent(ex);
    }
}