[![CI](https://github.com/Romanow/aggregator-by-tags/actions/workflows/build.yml/badge.svg)](https://github.com/Romanow/aggregator-by-tags/actions/workflows/build.yml)
[![pre-commit](https://img.shields.io/badge/pre--commit-enabled-brightgreen?logo=pre-commit)](https://github.com/pre-commit/pre-commit)
[![Release](https://img.shields.io/github/v/release/Romanow/aggregator-by-tags?logo=github&sort=semver)](https://github.com/Romanow/aggregator-by-tags/releases/latest)
[![License](https://img.shields.io/github/license/Romanow/aggregator-by-tags)](https://github.com/Romanow/aggregator-by-tags/blob/master/LICENSE)

# OpenAPI aggregator by Tags

## Подключение

### Maven

```xml
<dependency>
  <groupId>ru.romanow.openapi</groupId>
  <artifactId>aggregator-by-tags</artifactId>
  <version>${aggregator-by-tags.version}</version>
</dependency>
```

### Gradle

```groovy
testImplementation "ru.romanow.openapi:aggregator-by-tags:$aggregatorByTagsVersion"
```

## Реализация

Генерация агрегированного OpenAPI по нескольким файлам:

1. Если ничего не задано, то результатом будет конкатенированный OpenAPI.
2. Если задан параметр `include`, то в результирующий OpenAPI добавляются только те path, для которых заданы _все_
   переданные `tags`. Параметр `exclude` не учитывается.
3. Если задан параметр `exclude`, то из всего множества `tags` удаляются те, что переданы в `exclude`.

При фильтрации результата используются следующий допущения:

* Целевые `tags` строятся на базе блоке `tags` в верхнем уровне описания OpenAPI и к ним применяется
  фильтрация `include`, `exclude`.
* Метод добавляется в результирующий OpenAPI, если всего `tags` содержатся в целевых `tags`.
* Если метод добавляется в результирующий OpenAPI, то его имена объектов request и response сохраняются и при
  копировании схемы копируются только те объекты, на которые ссылались целевые методы.
* Фильтрация выполняется только в блоке `schemas`, т.к. [springdoc](https://springdoc.org/) при генерации добавляет все
  объекты туда, но не заполняет `headers`, `parameters`, `requestBodies`, `responses`.
