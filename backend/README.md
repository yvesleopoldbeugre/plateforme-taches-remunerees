# Backend — Plateforme de Tâches & Campagnes Rémunérées

Monolithe modulaire Spring Boot (Java 21), conforme à l'architecture décrite dans le
[Dossier d'Architecture Technique](../docs/06-DAT-Dossier-Architecture-Technique.docx) (§7-8).

## État actuel : Phase 8 — Paiements et retraits

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

Phase 4 (campagnes et tâches) :

- Modules `campaigns` (`Campaign`/`CampaignEligibilityCriteria`, `CampaignService`) et `tasks`
  (`Task`, `TaskService`) — US-030 à US-033.
- `type` et `expectedProofType` sont des chaînes libres validées contre une liste blanche
  (`TaskTypes`, `ProofTypes`), pas des enums JPA — ajouter un type de tâche futur (cf. CDCF
  §2.1) reste un changement d'une ligne, jamais une migration de schéma.
- `budget_total` est recalculé automatiquement à chaque tâche ajoutée (somme de
  `reward_amount × max_participants` sur toutes les tâches), jamais saisi manuellement.
- BR-012 (justification obligatoire si critère de genre) et BR-013 (dates, places > 0)
  appliquées à la création/modification, pas seulement par les contraintes `CHECK` en base.
- Une campagne n'est modifiable (tâches, critères, titre) que tant qu'elle est `DRAFT` ;
  `submit-for-review` (DRAFT → PENDING_REVIEW) exige au moins une tâche.
- `GET/POST /api/v1/campaigns`, `GET/PUT /api/v1/campaigns/{id}/eligibility-criteria`,
  `POST /api/v1/campaigns/{id}/submit-for-review`,
  `GET/POST /api/v1/campaigns/{id}/tasks`.
- Correctifs transverses : `MissingServletRequestParameterException` et
  `MethodArgumentTypeMismatchException` renvoient désormais 400 (au lieu de 500) dans le
  `GlobalExceptionHandler`.

Phase 5 (participations et soumissions) a avancé plus vite que prévu sur la publication de
campagne, pour la raison suivante : son propre critère de validation ("BR-020/021/022
vérifiées par tests de concurrence") exige des tâches réellement participables, donc des
campagnes `PUBLISHED` — en Phase 4 ce chemin était resté bloqué derrière le module `wallet`
(Phase 7). Le déblocage choisi, documenté partout où il s'applique (code et ici) :

- `publish`/`reject`/`return-to-draft` sont implémentés dès maintenant, réservés aux rôles
  `PLATFORM_ADMIN`/`PLATFORM_OPERATOR` (`@PreAuthorize`, méthode de sécurité activée dans
  `SecurityConfig`). ⚠ `publish` ne vérifie **pas encore** BR-010 (budget_total ≤ solde
  disponible de l'organisation) : c'est un simple changement de statut tant que le module
  `wallet` n'existe pas. La vérification réelle sera ajoutée en Phase 7.
  `V4__add_campaign_rejection_reason.sql` ajoute la colonne manquante pour stocker le motif.
- Aucun endpoint ne permet encore d'attribuer `PLATFORM_ADMIN`/`PLATFORM_OPERATOR` (pas de
  bootstrap admin) — testé via une mise à jour SQL directe sur `users.platform_role`.

Modules `participations` (`TaskParticipation`, `ParticipationService`) et `submissions`
(`TaskSubmission`, `SubmissionService`) — UC-09, UC-10 :

- BR-022 (jamais plus de participants que de places) : mise à jour SQL conditionnelle
  atomique (`UPDATE ... WHERE current_participants < max_participants`), pas de verrou
  pessimiste — voir `TaskRepository.tryReserveSlot`.
- BR-020 (une seule participation active par utilisateur et par tâche) : appliqué par l'index
  unique partiel `uq_active_participation` (déjà en V1) ; la violation de contrainte est
  traduite en `ALREADY_PARTICIPATING`, et comme `participate()` est `@Transactional`, l'échec
  annule aussi la réservation de place faite juste avant — aucune libération manuelle requise.
- BR-021 (expiration de réservation) : `ReservationExpiryScheduler` (`@Scheduled`, toutes les
  60s par défaut) libère la place des réservations dépassées.
- BR-051 (doublon de soumission) : détecté par hash SHA-256 du contenu, **par tâche** —
  `V5__add_task_submissions_task_id.sql` dénormalise `task_id` sur `task_submissions` pour que
  le module `submissions` n'ait pas besoin d'interroger le module `participations`. Plus
  strict que l'intention originale du dossier ("bloque la validation automatique") : rejette
  la soumission d'emblée, faute de moteur de validation auto (Phase 6) ou de module fraude
  (Phase 10) pour faire autrement — à revisiter alors.
- BR-001/BR-002 : participer exige un compte `ACTIVE`-compatible (email + téléphone vérifiés,
  ni suspendu ni banni).
- **Testé par `ParticipationConcurrencyTest`** (le critère de validation explicite de cette
  phase) : 100 tentatives concurrentes sur une tâche à 10 places → exactement 10 réussissent,
  90 échouent en `TASK_FULL` ; 20 tentatives concurrentes du même utilisateur → exactement 1
  réussit, 19 échouent en `ALREADY_PARTICIPATING` ; une réservation expirée libère réellement
  sa place (une nouvelle participation peut la reprendre).
- `POST /api/v1/tasks/{id}/participate`, `GET /api/v1/participations/me`,
  `GET /api/v1/participations/{id}`, `POST /api/v1/participations/{id}/submission`,
  `POST /api/v1/participations/{id}/cancel`, `GET /api/v1/tasks/{id}/participations` (membres
  actifs de l'organisation uniquement).

Correctif transverse : `AuthorizationDeniedException` (levée par `@PreAuthorize` en cas de
refus) renvoyait 500 au lieu de 403 — `GlobalExceptionHandler` la gère maintenant.

Explicitement reporté à l'époque : la vérification de solde BR-010 sur `publish` — soldée en
Phase 7, voir plus bas. Toujours reporté : bootstrap d'un premier compte `PLATFORM_ADMIN` ;
`IN_PROGRESS` reste un statut inutilisé (le flux va de `RESERVED` à `SUBMITTED` directement,
comme sur le diagramme de séquence du dossier) ; passage `FULL → PUBLISHED` d'une campagne à la
libération d'une place (US-039, priorité SHOULD).

Phase 6 (validation) — module `validation` (`Validation`, `ValidationService`) — UC-11 à UC-13 :

- `POST /api/v1/validations/{submissionId}` — décision manuelle d'un `OWNER`/`MANAGER` de
  l'organisation : `APPROVED`, `REJECTED` ou `CORRECTION_REQUESTED`. `AUTO_APPROVED` est
  refusé sur cet endpoint (système uniquement).
- BR-031 (motifs de rejet standardisés) : `RejectionReasons` — comme pour `TaskTypes`, le
  dossier de conception nommait la règle sans jamais énumérer les valeurs ; tranché ici
  (`INCOMPLETE_SUBMISSION`, `PROOF_DOES_NOT_MATCH_INSTRUCTIONS`,
  `ILLEGIBLE_OR_UNUSABLE_PROOF`, `SUSPECTED_FRAUD`, `OUT_OF_SCOPE`, `OTHER`), appliquée aussi
  à `CORRECTION_REQUESTED`. Une décision sans motif valide est rejetée (400) — **c'est le
  critère de validation explicite de cette phase** ("un rejet avec motif déclenche un statut
  cohérent", DAT §11).
- UC-11 (validation automatique) : `AutoValidationRuleEvaluator` — délibérément minimal,
  opt-in par tâche via `auto_validation_rules = {"requiredFields": ["q1", "q2"]}` (tous les
  champs listés doivent être présents et non vides). Déclenché par
  `ParticipationSubmittedEvent` (premier usage réel de l'event bus posé en Phase 1) ; toute
  erreur d'évaluation est absorbée par `AutoValidationListener` et retombe en revue manuelle,
  jamais en échec de la soumission elle-même.
- `GET /api/v1/participations/{id}/submissions` — un participant voit sa propre preuve, un
  membre actif de l'organisation voit la preuve de n'importe quelle participation sur ses
  tâches (UC-12).
- `TaskParticipation` gagne `approve()`/`reject()`/`requestCorrection()`, tous les trois ne
  partant que de `UNDER_REVIEW` — une seconde décision sur une soumission déjà tranchée
  échoue proprement (409).
- `SubmissionApprovedEvent` publié sur toute approbation (manuelle ou auto) — voir Phase 7 :
  l'argent n'est en fait plus réglé par un listener sur cet événement, mais synchronement par
  `ValidationService` lui-même ; l'événement reste publié, maintenant purement informatif pour
  d'éventuels consommateurs non critiques (notifications, Phase 9+).

Phase 7 (wallet et ledger) — module `wallet` (`Wallet`, `WalletTransaction`, `Reward`,
`WalletService`, `RewardService`) — DAT §4.2/§25, UC du wallet :

- `wallet` reste le **noyau** annoncé dans son `package-info` depuis la Phase 1 : zéro
  dépendance vers un autre module métier. Concrètement, cela a dicté deux choix :
  - Un wallet est créé *à la volée* (`WalletService#getOrCreateWallet`) au premier usage, pas
    par un événement `OrganizationCreated`/`UserCreated` — `wallets.owner_id` n'a d'ailleurs pas
    de FK vers `users`/`organizations` (association polymorphe, déjà dans la V1).
  - Le règlement de l'argent à une approbation (créer la `Reward`, créditer l'utilisateur,
    libérer le budget réservé de l'organisation) n'est **pas** fait par un listener côté
    `wallet` sur `SubmissionApprovedEvent` (ce qui aurait forcé `wallet` à importer un type du
    module `validation`, même juste l'événement) : c'est `ValidationService#applyDecision` qui
    appelle directement `WalletService`/`RewardService`, dans la même transaction que la
    décision elle-même. `campaigns` et `validation` dépendent de `wallet`, jamais l'inverse.
- Chaque opération (`credit`, `reserve`, `release`, `consumeReserved`) est une seule mise à jour
  SQL conditionnelle (`WalletRepository`, même famille de pattern que `tryReserveSlot` en Phase
  5), jamais lecture-puis-écriture, et prend une clé d'idempotence : une clé déjà vue est un
  succès silencieux (pas une erreur) — c'est la sémantique attendue d'un retry après crash ou
  d'un événement redélivré. Une vraie collision concurrente sur la même clé est le cas rare,
  renvoyé en `IDEMPOTENCY_KEY_CONFLICT` par l'index unique de `wallet_transactions`.
- **Piège Hibernate rencontré et corrigé** (à documenter car non évident) : après une mise à
  jour SQL en bloc (`@Modifying`), relire l'entité par `findById` dans la *même* transaction
  renvoie l'instance encore en cache du contexte de persistance, donc les anciennes valeurs —
  pas celles qu'on vient d'écrire. Le premier correctif essayé (`clearAutomatically = true`)
  réglait ça mais vidait *tout* le contexte de persistance, détachant silencieusement les
  entités chargées plus tôt dans la même transaction par l'appelant (ex. `Campaign` dans
  `CampaignService#publish`) — leurs changements ultérieurs (`campaign.publish()`) n'étaient
  alors plus jamais flushés, même si la réponse HTTP semblait correcte. Corrigé en appliquant le
  même delta en mémoire sur l'instance déjà chargée (`Wallet#applyCredit/applyReserve/...`) puis
  en détachant *uniquement* cette entité (`WalletService#applyAndDetach`), sans toucher au
  contexte des autres entités de la transaction. Détecté par un test manuel (relire la campagne
  juste après `publish`, pas seulement regarder la réponse du POST) — pas par les tests
  automatisés, qui ne rechargeaient jamais l'entité dans la même transaction.
- BR-010, enfin vérifiée pour de vrai : `CampaignService#publish` appelle
  `WalletService#reserve` (available → reserved sur le wallet de l'organisation) *avant* la
  transition de statut ; insuffisance de solde ⇒ `INSUFFICIENT_BALANCE` (409), aucune mutation.
  Dette documentée depuis la Phase 5, soldée ici.
- BR-011 (annulation) : `Campaign#cancel()` (nouvelle transition, DRAFT/PENDING_REVIEW/
  PUBLISHED/PAUSED → CANCELLED) et `CampaignService#cancel` libèrent la part réservée jamais
  consommée (`budget_reserved - budget_consumed`) vers le wallet de l'organisation.
- BR-030 (règlement d'une récompense) : à l'approbation, `ValidationService` incrémente
  `campaigns.budget_consumed` (vue propre à la campagne) *et* appelle
  `WalletService#consumeReserved` sur le wallet de l'organisation (vue wallet, qui peut couvrir
  plusieurs campagnes) — deux comptabilités densément liées mais distinctes, délibérément :
  `wallet` ne sait rien de ce qu'est une « campagne ». `RewardService#createPending` est
  idempotent (une seule `Reward` par `participation_id`, contrainte unique de la V1) ; la
  récompense n'est marquée `CREDITED` qu'après le crédit effectif du wallet utilisateur, et un
  replay (événement redélivré) qui retombe sur une `Reward` déjà `CREDITED` ne retouche pas le
  wallet une seconde fois.
- Dépôt simulé — `POST /api/v1/organizations/{id}/wallet/deposit` crédite directement le wallet
  d'une organisation, sans passer par la table `payments` ni aucun provider : un strict
  bouche-trou pour pouvoir publier une campagne contre un vrai solde avant que Phase 8
  (paiements et retraits) construise la vraie abstraction `PaymentProvider` (H5 du CDCF) avec un
  adaptateur manuel/simulé puis de vrais providers derrière.
- **Testé par `WalletLedgerInvariantTest`** (le critère de validation explicite de cette phase,
  "Invariant solde = somme ledger vérifié par test", DAT §11) :
  `WalletService#reconstructBalances` rejoue tout le ledger d'un wallet depuis zéro et
  recalcule `available_balance`/`reserved_balance` ; les 4 tests vérifient que ce calcul
  retombe exactement sur les valeurs vivantes après dépôt/réservation/libération/consommation,
  après un dépassement de solde refusé, après un retry sur la même clé d'idempotence, et sous
  100 réservations concurrentes sur un solde ne permettant que 10 succès (même famille de
  preuve que `ParticipationConcurrencyTest` en Phase 5, appliquée à BR-010).
- `GET /api/v1/wallet/me(/transactions)`,
  `GET /api/v1/organizations/{id}/wallet(/transactions)` (membre actif requis),
  `POST /api/v1/campaigns/{id}/cancel`. Le dépôt simulé de cette phase vivait directement sur
  ce contrôleur ; il a été remplacé en Phase 8 par le vrai module `payments` — voir plus bas.

Explicitement reporté à l'époque : la vraie abstraction `PaymentProvider` et la table
`payments`/`payment_methods`, les retraits utilisateur — soldés en Phase 8, voir plus bas.
Toujours reporté : les types de transaction `DEBIT`/`FEE`/`REVERSAL`/`ADJUSTMENT` sont définis
dans l'enum mais n'ont encore aucune méthode de service (corrections admin, Phase 9) ; la
libération du budget réservé non consommé à la complétion/expiration naturelle d'une campagne
(seule l'annulation explicite le fait pour l'instant) ; bootstrap d'un premier compte
`PLATFORM_ADMIN`.

Phase 8 (paiements et retraits) — modules `payments` (`PaymentMethod`, `Payment`,
`PaymentProvider`/`PaymentProviderRegistry`/`SimulatedPaymentProvider`, `PaymentMethodService`,
`PaymentService`) et `withdrawals` (`Withdrawal`, `WithdrawalService`) — UC-17/UC-18,
BR-002/BR-040 à BR-043 :

- Abstraction `PaymentProvider` (H5 du CDCF — "aucun provider n'est choisi en dur") : une
  interface + un registre Spring (`PaymentProviderRegistry`, une `Map<code, PaymentProvider>`
  construite depuis tous les beans du type) + un seul adaptateur pour l'instant
  (`SimulatedPaymentProvider`, code `SIMULATED`). Ajouter un vrai provider (Orange Money, Wave…)
  plus tard sera une implémentation de l'interface, détectée automatiquement — zéro changement
  dans `PaymentService`/`WithdrawalService`. L'interface prend des identifiants primitifs
  (`UUID`/`BigDecimal`/`String`), jamais les entités `Payment`/`Withdrawal` elles-mêmes : sinon
  `payments` dépendrait de `withdrawals`, qui dépend déjà de `payments` (cf. DAT §8.1).
- Remplace le dépôt simulé ad-hoc de la Phase 7 (`WalletController#deposit`, supprimé) par un
  vrai flux : `POST /organizations/{id}/payments` crée un `Payment` PENDING, appelle le
  provider, puis crédite le wallet de l'organisation (`WalletService#credit`,
  `ReferenceType.PAYMENT`) seulement si le provider confirme.
- En-tête `Idempotency-Key` obligatoire sur les deux endpoints de création financière (DAT §8.3)
  — absent, c'est un 400 `MISSING_HEADER` (`MissingRequestHeaderException` ajouté au
  `GlobalExceptionHandler`, même correctif transverse que pour les paramètres manquants en
  Phase 4). `Payment`/`Withdrawal` sont eux-mêmes recherchés par cette clé avant toute mutation :
  un retry renvoie la même ressource plutôt que d'en créer une seconde.
- BR-043 (retrait) — "débit immédiat vers reserved_balance dès la demande" (DAT §8.3) : la
  demande réserve `amount` sur le wallet de l'utilisateur dans la même transaction que la
  création du `Withdrawal`, avant toute revue. Si le solde est insuffisant,
  `WalletService#reserve` lève `INSUFFICIENT_BALANCE` et annule aussi l'insertion du retrait
  (même transaction) — pas de ligne orpheline. Les frais sont déduits du montant demandé
  (`net_amount = amount - fees`), pas ajoutés par-dessus (convention fintech la plus courante).
- Machine à états `Withdrawal` : `REQUESTED` → (`UNDER_REVIEW` →) `APPROVED` → `PROCESSING` →
  `COMPLETED`/`FAILED`, ou `REQUESTED`/`UNDER_REVIEW` → `REJECTED`/`CANCELLED`. Un montant
  au-dessus du seuil de revue manuelle part en `UNDER_REVIEW` (file visible par
  `PLATFORM_OPERATOR`/`ADMIN`, `GET /withdrawals?status=`) ; en dessous, `requestWithdrawal`
  enchaîne directement `APPROVED → PROCESSING → COMPLETED` dans le même appel — **c'est le
  critère de validation explicite de cette phase** ("un retrait simulé complète le cycle
  REQUESTED→COMPLETED", DAT §11). `V6__add_withdrawal_rejection_reason.sql` ajoute la colonne
  manquante pour que l'utilisateur sache pourquoi, même gap que `V4` pour les campagnes.
- Hypothèses explicites non chiffrées dans le dossier de conception, tranchées ici comme
  `TaskTypes`/`RejectionReasons` avant (configurables, `platform.withdrawal.*`, pas des
  constantes) : montant minimum 1000 XOF, taux de frais 1,5 %, seuil de revue manuelle 50 000
  XOF. BR-044 (commission plateforme prélevée au crédit de la récompense) reste délibérément
  hors de cette phase : orthogonal aux dépôts/retraits eux-mêmes, et nécessiterait un wallet de
  revenu plateforme qui n'a pas sa place naturelle dans "paiements et retraits" — reporté à une
  passe ultérieure.
- BR-002 (compte suspendu/banni) gate aussi la demande de retrait, pas seulement la
  participation — le dossier le nomme explicitement pour ce cas.
- Petit correctif rétroactif remarqué en écrivant cette phase : `wallets.lifetime_earned`
  n'était jamais incrémenté depuis la Phase 7 (seul `available_balance` l'était), rendant ce
  compteur KPI (CDCF §15, "revenu total") inerte. Corrigé dans `WalletService#credit` /
  `#consumeReserved` (incrémenté seulement pour `REWARD`/`WITHDRAWAL` respectivement, pas pour
  les dépôts d'organisation).
- `POST /api/v1/payment-methods/me`, `GET /api/v1/payment-methods/me`,
  `POST/GET /api/v1/organizations/{id}/payment-methods`,
  `POST/GET /api/v1/organizations/{id}/payments`,
  `POST /api/v1/withdrawals`, `GET /api/v1/withdrawals/me`, `GET /api/v1/withdrawals/{id}`
  (propriétaire ou opérateur), `GET /api/v1/withdrawals?status=` (opérateur/admin),
  `POST /api/v1/withdrawals/{id}/{cancel,approve,reject}`.
- **Testé par `WithdrawalLifecycleTest`** (11 cas — petit retrait auto-complété, grand retrait
  en revue puis approuvé, rejet et annulation qui libèrent le solde, montant sous le minimum et
  solde insuffisant refusés avant toute mutation, rejeu de clé d'idempotence côté retrait et
  côté dépôt, provider non supporté refusé, premier moyen de paiement enregistré devenant
  défaut) et vérifié par curl en local et dans le conteneur Docker reconstruit (dépôt sans
  en-tête → 400, avec en-tête → crédité, campagne → tâche → participation → approbation → petit
  retrait complété en un appel → grand retrait mis en revue puis approuvé par un opérateur).

Explicitement reporté : KYC documentaire utilisateur au-delà d'un cumul de retraits (DAT §8.3
le mentionne, mais aucun champ `kyc_status` n'existe sur `users` — seules les organisations en
ont un depuis la Phase 3 ; construire ce flux documentaire appartient davantage à
l'administration, Phase 9) ; webhook provider asynchrone (le provider simulé répond toujours
synchrone et immédiatement — l'interface `PaymentProvider` est conçue pour qu'un vrai provider
async n'oblige pas à reprendre `PaymentService`/`WithdrawalService`, mais le chemin webhook
lui-même n'a rien à confirmer avant qu'un vrai provider existe) ; BR-044 (commission
plateforme, cf. ci-dessus) ; bootstrap d'un premier compte `PLATFORM_ADMIN`.

Pas encore implémenté (phases suivantes, DAT §11) : litiges, anti-fraude, administration,
statistiques.

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
