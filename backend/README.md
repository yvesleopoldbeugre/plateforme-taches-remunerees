# Backend — Plateforme de Tâches & Campagnes Rémunérées

Monolithe modulaire Spring Boot (Java 21), conforme à l'architecture décrite dans le
[Dossier d'Architecture Technique](../docs/06-DAT-Dossier-Architecture-Technique.docx) (§7-8).

## État actuel : Phase 1 — Architecture et base technique

Ce qui est en place :

- Structure modulaire `com.platform.<module>` (`auth`, `users`, `organizations`, `campaigns`,
  `tasks`, `participations`, `submissions`, `validation`, `wallet`, `payments`, `withdrawals`,
  `disputes`, `notifications`, `fraud`, `audit`, `admin`), chacun documenté dans son
  `package-info.java` et conservant les sous-packages `domain` / `application` /
  `infrastructure` / `api` prévus par le DAT.
- Schéma PostgreSQL complet (19 tables) en migration Flyway (`V1__init_schema.sql`), identique
  au DDL du DAT §4.
- Socle transverse (`common`) : gestion d'erreurs centralisée au format
  `{ "error": { "code", "message", "traceId" } }`, filtre de corrélation (`X-Request-Id` →
  logs structurés), configuration Spring Security de base (API stateless, `/actuator/health`
  public, tout le reste authentifié), encodage de mot de passe Argon2id.
- Health check (`/actuator/health`) et test de contexte contre un vrai PostgreSQL
  (Testcontainers) qui valide que la migration s'applique sans erreur.
- Conteneurisation : `Dockerfile` multi-stage (build Maven → image `eclipse-temurin:21-jre-alpine`
  non-root, avec `HEALTHCHECK`) et `docker-compose.yml` orchestrant `app` + `postgres` + `redis`.

Pas encore implémenté (phases suivantes de la roadmap, DAT §11) : authentification réelle,
entités JPA, endpoints métier, event bus applicatif, wallet/ledger, paiements, anti-fraude.

## Prérequis

- Docker (pour tout lancer en conteneurs, recommandé)
- Java 21 + Maven 3.9+ (uniquement pour lancer l'app hors conteneur)

## Démarrer avec Docker (recommandé)

```bash
docker compose up --build        # app (8080) + PostgreSQL (5432) + Redis (6379)
curl http://localhost:8080/actuator/health
docker compose down              # arrêt (ajouter -v pour effacer aussi le volume Postgres)
```

Si un port par défaut est déjà occupé sur la machine (ex. un autre service local sur 8080),
surchargez-le sans modifier le fichier :

```bash
APP_PORT=18080 POSTGRES_PORT=5433 REDIS_PORT=6380 docker compose up --build
```

## Démarrer sans Docker pour l'app (PostgreSQL/Redis restent en conteneurs)

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run       # ou: mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

Variables d'environnement lues par l'application (valeurs par défaut adaptées à
`docker-compose.yml`) : `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`,
`SERVER_PORT`.

## Tests

```bash
mvn test
```

Le test de contexte démarre un conteneur PostgreSQL jetable (Testcontainers) et vérifie que
l'application boote avec la migration Flyway appliquée.
