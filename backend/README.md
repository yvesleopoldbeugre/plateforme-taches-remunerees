# Backend — Plateforme de Tâches & Campagnes Rémunérées

Monolithe modulaire Spring Boot (Java 21), conforme à l'architecture décrite dans le
[Dossier d'Architecture Technique](../docs/06-DAT-Dossier-Architecture-Technique.docx) (§7-8).

## État actuel : Phase 3 — Organisations

Phase 1 (architecture et base technique) :

- Structure modulaire `com.platform.<module>` (`auth`, `users`, `organizations`, `campaigns`,
  `tasks`, `participations`, `submissions`, `validation`, `wallet`, `payments`, `withdrawals`,
  `disputes`, `notifications`, `fraud`, `audit`, `admin`), chacun documenté dans son
  `package-info.java` et conservant les sous-packages `domain` / `application` /
  `infrastructure` / `api` prévus par le DAT.
- Schéma PostgreSQL en migrations Flyway (`V1__init_schema.sql`, 19 tables identiques au DDL
  du DAT §4 ; `V2__add_platform_role.sql`, cf. ci-dessous).
- Socle transverse (`common`) : gestion d'erreurs centralisée au format
  `{ "error": { "code", "message", "traceId" } }`, filtre de corrélation (`X-Request-Id` →
  logs structurés), configuration Spring Security (API stateless, JWT), encodage de mot de
  passe Argon2id.
- Health check (`/actuator/health`) et test de contexte contre un vrai PostgreSQL
  (Testcontainers) qui valide que les migrations s'appliquent sans erreur.
- Conteneurisation : `Dockerfile` multi-stage (build Maven → image `eclipse-temurin:21-jre-alpine`
  non-root, avec `HEALTHCHECK`) et `docker-compose.yml` orchestrant `app` + `postgres` + `redis`.

Phase 2 (authentification et utilisateurs) :

- Modules `users` (entités `User`/`UserProfile`, `UserService`) et `auth` (inscription, login,
  rafraîchissement/révocation de session, vérification email + téléphone) — US-001 à US-006.
- JWT access token (15 min, HS256) + refresh token opaque rotatif stocké dans Redis (révocable
  individuellement au logout) ; aucune donnée de session en base.
- Vérification email (lien à usage unique, 24h) et téléphone (OTP 6 chiffres, 10 min, 5
  tentatives max), codes stockés uniquement dans Redis. Le module `notifications` n'existant
  pas encore, `LoggingVerificationNotifier` journalise le lien/code à la place d'un envoi réel
  (`docker compose logs app`, ou la console) — à remplacer en Phase 9.
- `platform_role` (`PLATFORM_ADMIN` / `PLATFORM_OPERATOR`, nullable) ajouté à `users` : la V1
  n'avait pas prévu de colonne pour ces rôles globaux, distincts des rôles d'organisation.
- `GET /api/v1/users/me`, `PATCH /api/v1/users/me/profile`.
- Endpoints : `POST /api/v1/auth/{register,login,refresh,logout,verify-email,
  verify-email/resend,verify-phone,verify-phone/resend}`.

Explicitement reporté (hors MVP à ce stade) : MFA/TOTP (prévu pour PLATFORM_ADMIN/OPERATOR/
ORGANIZATION_OWNER, cf. CDCT §8), réinitialisation de mot de passe, révocation de toutes les
sessions d'un coup, bootstrap d'un premier compte PLATFORM_ADMIN.

Phase 3 (organisations) :

- Module `organizations` (entités `Organization`/`OrganizationMember`, `OrganizationService`) —
  US-010 à US-014 (hors vérification admin, cf. ci-dessous).
- Créer une organisation rend son créateur `OWNER` immédiatement (pas d'invitation pour
  soi-même) ; seul un `OWNER` peut inviter (toujours en tant que `MANAGER`, cf. US-011) ou
  retirer un membre ; le `OWNER` ne peut pas être retiré.
- Invitation par email : l'invité doit déjà avoir un compte USER (pas d'invitation avec
  création de compte à la volée en MVP) ; il peut accepter ou refuser.
- `V3__add_kyc_status_check.sql` : `kyc_status` n'avait pas de contrainte `CHECK` dans la V1
  (seule colonne de statut dans ce cas) — corrigé par cohérence avec le reste du schéma.
- Décision de conception : une organisation `PENDING_VERIFICATION` peut gérer ses membres
  normalement (BR-003 ne bloque que `SUSPENDED`) — la vérification admin (US-014) ne
  conditionnera que la publication de campagnes (Phase 4+), pas la gestion d'équipe ; cette
  vérification elle-même n'est pas encore implémentée (nécessite le module admin, Phase 9).
- `GET /api/v1/organizations/{id}`, `GET /api/v1/organizations/me/memberships`,
  `GET/POST /api/v1/organizations/{id}/members`,
  `POST /api/v1/organizations/{id}/members/{accept,decline}`,
  `DELETE /api/v1/organizations/{id}/members/{userId}`.

Pas encore implémenté (phases suivantes, DAT §11) : campagnes/tâches, participations/
soumissions, validation, wallet/ledger, paiements, anti-fraude.

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
