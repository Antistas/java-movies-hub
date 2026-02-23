package ru.practicum.moviehub.store;

import ru.practicum.moviehub.model.Movie;
import java.util.*;

public class MoviesStore {

    private final Map<Long, Movie> movies;

    public List<Movie> findAll() {
        return new ArrayList<>(movies.values());
    }

    public MoviesStore() {
        this.movies = new HashMap<>();
    }

    public List<Movie> findByYear(int year) {
        return movies.values().stream()
                .filter(movie -> movie.getYear() == year).toList();
    }

    public Optional<Movie> findById(long id) {
        return Optional.ofNullable(movies.get(id));
    }

    public Movie create(String title, int year) {
        long id = movies.size() + 1;
        Movie movie = new Movie(id, title, year);
        movies.put(id, movie);
        return movie;
    }

    // Переделать?
    public boolean delete(long id) {
        return movies.remove(id) != null;
    }

    public void clear() {
        movies.clear();
    }
}