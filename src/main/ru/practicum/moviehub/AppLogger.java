package ru.practicum.moviehub;

import ru.practicum.moviehub.exceptions.LogFileException;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class AppLogger implements AutoCloseable {
    private final PrintWriter out;
    private final String prefix;

    private AppLogger(String file, String prefix) throws LogFileException {
        try {
            this.out = new PrintWriter(new FileWriter(file, true), true);
            this.prefix = prefix;
        } catch (IOException e) {
            throw new LogFileException(file, e);
        }
    }

    public static AppLogger system(String file) throws LogFileException {
        return new AppLogger(file, "[SYSTEM] ");
    }

    public void info(String msg) {
        out.println(prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + " INFO  " + msg);
    }

    public void warn(String msg) {
        out.println(prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + " WARN  " + msg);
    }

    public void error(String msg, Exception e) {
        out.println(prefix + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")) + " ERROR " + e.getMessage() + " " + msg);
    }

    @Override public void close() {
        out.close();
    }
}


