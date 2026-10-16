.DEFAULT_GOAL := verify

.PHONY: checkstyle test verify build start clean

checkstyle:
	./mvnw checkstyle:check

test:
	./mvnw test

verify:
	./mvnw clean verify

build:
	docker compose build

start:
	docker compose up -d --build

clean:
	docker compose down -v
