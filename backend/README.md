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

Pas encore implémenté (phases suivantes de la roadmap, DAT §11) : authentification réelle,
entités JPA, endpoints métier, event bus applicatif, wallet/ledger, paiements, anti-fraude.

## Prérequis

- Java 21
- Maven 3.9+
- Docker (pour PostgreSQL/Redis en local et pour les tests Testcontainers)

## Démarrer en local

```bash
docker compose up -d        # PostgreSQL (5432) + Redis (6379)
./mvnw spring-boot:run       # ou: mvn spring-boot:run
curl http://localhost:8080/actuator/health
```

Variables d'environnement (valeurs par défaut adaptées à `docker-compose.yml`) :
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `SERVER_PORT`.

## Tests

```bash
mvn test
```

Le test de contexte démarre un conteneur PostgreSQL jetable (Testcontainers) et vérifie que
l'application boote avec la migration Flyway appliquée.
