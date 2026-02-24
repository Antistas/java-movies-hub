package ru.practicum.moviehub.http;

import com.sun.net.httpserver.HttpServer;
import ru.practicum.moviehub.AppLogger;
import ru.practicum.moviehub.exceptions.LogFileException;
import ru.practicum.moviehub.store.MoviesStore;

import java.io.IOException;
import java.net.InetSocketAddress;

public class MoviesServer {
    private final HttpServer server;
    static final String SYSTEM_LOG = "src/logs/movieHubApp.log";
    AppLogger sysLog = null;
    private final MoviesStore  ms;

    public MoviesServer(MoviesStore ms, int port) {
        this.ms = ms;
        try {
            sysLog = AppLogger.system(SYSTEM_LOG);
            final AppLogger log = sysLog;
            log.info("MovieHub started.");
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/movies", new MoviesHandler(ms, sysLog));
        } catch (LogFileException e) {
            System.err.println("Ошибка работы с лог-файлом: " + e.getMessage());
            throw new RuntimeException("Ошибка работы с лог-файлом: " + e.getMessage());
        } catch (IOException e) {
            sysLog.error("Не удалось создать HTTP-сервер", e);
            throw new RuntimeException("Не удалось создать HTTP-сервер", e);
        }
    }

    public void start() {
        server.start();
        sysLog.info("Сервер запущен");
        System.out.println("Сервер запущен");
    }

    public void stop() {
        server.stop(0);
        sysLog.info("Сервер остановлен");
        try {
            sysLog.close();
        } catch (Exception closeError) {
            System.err.println("Не удалось корректно закрыть лог-файл: " + closeError.getMessage());
        }

        System.out.println("Сервер остановлен");
    }

    public void clearStore() {
        this.ms.clear();
    }
}