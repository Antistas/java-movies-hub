package ru.practicum.moviehub.exceptions;

public class LogFileException extends Exception  {
    public LogFileException(String file, Throwable cause) {
        super("Не удалось создать лог-файл: " + file);
        initCause(cause);
    }
}
