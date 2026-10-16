.DEFAULT_GOAL := verify

.PHONY: checkstyle test verify

checkstyle:
	./mvnw checkstyle:check

test:
	./mvnw test

verify:
	./mvnw clean verify
