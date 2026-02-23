package ru.practicum.moviehub;

import ru.practicum.moviehub.http.MoviesServer;
import ru.practicum.moviehub.store.MoviesStore;

/*
Функциональные требования
Эндпоинты:
4) Удаление фильма
    Эндпоинт — DELETE /movies/{id}.
    Если удалён:
    Код статуса — 204 No Content.
    Если фильм не найден:
    Код статуса — 404 Not Found.

Нефункциональные требования
1) Чёткая структура кода (разделение моделей, хранилища, сервера).
2) Обработка ошибок с корректными кодами ответа.
3) Тесты на все эндпоинты (успешные и негативные сценарии).

Список проверок
4) DELETE /movies/{id}
    удаляет фильм по существующему id;
    возвращает ошибку, если фильм не найден;
    возвращает ошибку, если id не число.
 */

public class MovieHubApp {
    public static void main(String[] args) {

        MoviesStore ms = new MoviesStore();
        ms.create("Название 1", 1991);
        ms.create("Название 2", 1994);
        ms.create("Название 3", 1995);
        ms.create("Название 4", 1996);
        ms.create("Название 5", 1997);
        ms.create("Название 6", 1998);
        ms.create("Название 6555", 1995);

        final MoviesServer server = new MoviesServer(ms, 8080);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}