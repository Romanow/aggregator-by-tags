# Запуск локального кластера k8s

```shell
$ kind create cluster --config kind.yml
$ helm repo add romanow https://romanow.github.io/helm-charts/
$ helm repo update

$ kind load docker-image romanowalex/openapi-aggregator-by-tags:v1.0
$ helm install store romanow/java-service --values=service/values.yaml

```

Проверяем запрос `http://localhost:32080/api/v1/openapi/warranty`.
