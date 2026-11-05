# Backend — Plateforme de Tâches & Campagnes Rémunérées

Monolithe modulaire Spring Boot (Java 21), conforme à l'architecture décrite dans le
[Dossier d'Architecture Technique](../docs/06-DAT-Dossier-Architecture-Technique.docx) (§7-8).

## État actuel : Phase 12 — Sécurité des comptes (au-delà des 11 phases de la roadmap du DAT)

Les 11 phases du DAT sont closes depuis la Phase 11. Les phases suivantes (12+) traitent, une
par une et dans un ordre proposé puis validé, les points explicitement reportés documentés
phase par phase ci-dessous.

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

Explicitement reporté (hors MVP à ce stade) à l'époque : MFA/TOTP, réinitialisation de mot de
passe, révocation de toutes les sessions d'un coup — soldés en Phase 12, voir plus bas. Soldé
en Phase 9 : bootstrap d'un premier compte PLATFORM_ADMIN.

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

Explicitement reporté à l'époque : bootstrap d'un premier compte `PLATFORM_ADMIN` — soldé en
Phase 9, voir plus bas. Toujours reporté : KYC documentaire **utilisateur** au-delà d'un cumul
de retraits (DAT §8.3 le mentionne, mais aucun champ `kyc_status` n'existe sur `users` — seules
les organisations en ont un, et leur vérification est maintenant un vrai flux admin, cf. Phase
9 ; construire l'équivalent documentaire côté utilisateur reste hors scope) ; webhook provider
asynchrone (le provider simulé répond toujours synchrone et immédiatement — l'interface
`PaymentProvider` est conçue pour qu'un vrai provider async n'oblige pas à reprendre
`PaymentService`/`WithdrawalService`, mais le chemin webhook lui-même n'a rien à confirmer avant
qu'un vrai provider existe) ; BR-044 (commission plateforme, cf. ci-dessus).

Phase 9 (administration / back-office) — module `admin` (contrôleurs transverses,
`AdminBootstrapService`) et `audit` (`AuditLog`, `AuditService`, `AuditLogListener`) —
UC-21, US-014 :

- **Bootstrap du premier `PLATFORM_ADMIN`** (reporté depuis la Phase 2 — jusqu'ici, seule une
  mise à jour SQL directe sur `users.platform_role` permettait de tester les endpoints
  réservés aux rôles plateforme) : `POST /api/v1/admin/bootstrap`, public par nécessité (aucun
  admin n'existe encore pour s'authentifier), protégé par un secret partagé
  (`platform.admin.bootstrap-secret` / env `ADMIN_BOOTSTRAP_SECRET`, vide par défaut — l'endpoint
  refuse tout tant qu'il n'est pas positionné) et utilisable une seule fois : il refuse dès
  qu'un `PLATFORM_ADMIN` existe déjà. La cible doit déjà avoir un compte (inscription normale) ;
  bootstrap ne fait que le promouvoir.
- **Vérification des organisations**, dette documentée depuis la Phase 3 ("la vérification
  admin... ne conditionnera que la publication de campagnes — cette vérification elle-même
  n'est pas encore implémentée, nécessite le module admin, Phase 9") : `Organization#verify`
  (→ `ACTIVE`/`VERIFIED`) et `#rejectVerification(reason)` (`kyc_status` → `REJECTED`, le motif
  est conservé — `V7__add_organization_kyc_rejection_reason.sql` ajoute la colonne manquante,
  même geste que `V4`/`V6` pour les campagnes et les retraits). **`CampaignService#publish`
  vérifie désormais réellement que l'organisation est `ACTIVE`** avant de réserver le budget —
  jusqu'ici, n'importe quelle organisation `PENDING_VERIFICATION` pouvait publier dès qu'elle
  avait les fonds ; ce n'est plus le cas, et les scripts de vérification manuelle des phases
  précédentes doivent désormais appeler `verify` avant `publish`.
- **Suspension/réactivation** (UC-21) côté `users` (`suspend`/`reactivate`/`ban`) et
  `organizations` (`suspend`/`reactivate`, en plus de `verify`/`rejectVerification`) —
  réservé à `PLATFORM_ADMIN` seul, pas `PLATFORM_OPERATOR` : le CDCF est explicite ("[l'opérateur]
  ne peut pas... suspendre une organisation sans escalade", UC-21 ne nomme que `PLATFORM_ADMIN`).
  Suspendre un utilisateur retire immédiatement l'accès aux actions déjà gardées par BR-002
  (participation, retrait) — aucun changement requis côté `participations`/`withdrawals`, la
  garde existait déjà, il manquait juste un moyen de déclencher le statut. La réactivation d'une
  organisation retombe sur `ACTIVE` ou `PENDING_VERIFICATION` selon son `kyc_status` — jamais
  `ACTIVE` sans trace d'une vérification réelle.
- **Journal d'audit immuable** (`audit_logs`, déjà dans la V1) : `users`/`organizations`
  publient `UserStatusChangedEvent`/`OrganizationStatusChangedEvent` (DAT §8.2 — les modules
  métier publient, les modules transverses écoutent), `AuditLogListener` écrit une ligne
  insert-only avec un instantané JSON avant/après. Comme `AutoValidationListener` en Phase 6,
  une erreur d'écriture d'audit est absorbée (loggée, pas propagée) — un échec de sérialisation
  JSON ne doit jamais empêcher de suspendre un compte frauduleux ; un mode strict ("l'audit doit
  réussir") est un renforcement raisonnable laissé pour plus tard. Lu via
  `GET /api/v1/admin/audit-logs?resourceType=&resourceId=`.
- **Files d'attente admin cross-organisation** — c'est le critère de validation explicite de
  cette phase ("un admin peut traiter chaque file de bout en bout", DAT §11) : la liste de
  campagnes existante (`GET /campaigns?organizationId=`) ne peut pas répondre à "toutes les
  campagnes en attente de revue, tous organismes confondus". `GET /api/v1/admin/organizations?
  status=`, `GET /api/v1/admin/campaigns?status=` ajoutées dans le module `admin` pour ça ; la
  file des retraits existait déjà depuis la Phase 8 (`GET /withdrawals?status=`).
- `POST /api/v1/users/{id}/{suspend,reactivate,ban}`,
  `POST /api/v1/organizations/{id}/{verify,reject-verification,suspend,reactivate}`,
  `POST /api/v1/admin/bootstrap`, `GET /api/v1/admin/organizations?status=`,
  `GET /api/v1/admin/campaigns?status=`, `GET /api/v1/admin/audit-logs?resourceType=&resourceId=`.
- **Testé par `AdministrationTest`** (7 cas : bootstrap puis refus d'un second admin, cycle de
  suspension/réactivation audité, refus de rétrograder un compte banni, refus de réactiver un
  compte qui n'est ni suspendu ni banni, `publish` bloqué puis débloqué par la vérification
  d'organisation, motif de rejet KYC conservé, suspension/réactivation d'organisation retombant
  sur le bon statut) et vérifié par curl en local et dans le conteneur Docker reconstruit
  (bootstrap avec mauvais secret puis bon secret puis refus d'un second admin, `publish` refusé
  puis accepté après vérification, suspension d'un utilisateur vérifiée en l'empêchant
  réellement de participer, journal d'audit relu après coup).

Explicitement reporté à l'époque : détection de fraude et escalade automatique — soldé en
Phase 10, voir plus bas. Toujours reporté : arbitrage des litiges (UC-15, module `disputes` —
orphelin des 11 phases de la roadmap, n'a pas de phase dédiée ; laissé pour une passe
ultérieure) ; KYC documentaire utilisateur (cf. ci-dessus) ; capture de l'adresse IP sur les
lignes d'audit (`audit_logs.ip_address` existe, mais aucun filtre de requête ne la renseigne
encore) ; mode audit strict (cf. ci-dessus) ; écran de gestion des rôles plateforme au-delà du
bootstrap initial.

Phase 10 (anti-fraude) — module `fraud` (`FraudEvent`, `FraudService`, `FraudEventListener`) —
UC-22, BR-051 (revisitée), BR-050 (explicitement reportée) :

- **BR-051 revisitée** — c'est le critère de validation explicite de cette phase ("une
  soumission dupliquée génère un événement", DAT §11). Le dossier de conception dit littéralement
  "bloque la validation automatique", pas "bloque la soumission" ; la Phase 5 avait implémenté la
  forme la plus stricte (rejet pur et simple, `DUPLICATE_SUBMISSION_HASH`) faute de module fraude
  pour faire autrement — documenté comme tel à l'époque, et explicitement revisité maintenant.
  `SubmissionService#recordSubmission` **n'est plus un 400** : une soumission dont le hash
  correspond à une soumission antérieure sur la même tâche est désormais **enregistrée** et
  marquée (`task_submissions.duplicate_of_submission_id`, `V8__add_task_submission_duplicate_
  marker.sql`) plutôt que rejetée. `ValidationService#attemptAutoValidation` refuse
  l'auto-approbation sur une soumission marquée, même si ses règles correspondent par ailleurs —
  la participation retombe simplement en revue manuelle (`UNDER_REVIEW`), jamais en erreur.
  **Changement de comportement assumé** : les intégrations qui comptaient sur un 422
  `DUPLICATE_SUBMISSION_HASH` en retour d'une soumission dupliquée doivent être adaptées — elles
  reçoivent maintenant un 201 normal, avec `duplicateOfSubmissionId` renseigné dans la réponse.
- `ParticipationService#submit` publie `DuplicateSubmissionDetectedEvent` (porteur de
  `userId`/`campaignId`, pour que `fraud` — module transverse — n'ait jamais à rappeler
  `participations`/`campaigns`, même raisonnement que `SubmissionApprovedEvent` en Phase 7) ;
  `FraudEventListener` écrit la ligne `fraud_events` (`type=DUPLICATE_SUBMISSION`,
  `risk_level=MEDIUM` — hypothèse explicite, non chiffrée dans le dossier, tranchée ici). Comme
  `AutoValidationListener`/`AuditLogListener` avant lui, une erreur d'écriture est absorbée, jamais
  propagée sur le flux de soumission.
- **UC-22** (file de fraude) : `FraudEvent` a trois décisions — `review` ("escalade", réversible,
  `PLATFORM_OPERATOR` ou `ADMIN` — le CDCF autorise explicitement l'opérateur à faire une "revue
  de fraude de premier niveau"), `dismiss` ("classement sans suite", même rôles), `confirm`
  ("suspension de compte" — **`PLATFORM_ADMIN` seul**, car confirmer un événement suspend
  réellement l'utilisateur visé via `UserService#suspend` (Phase 9) dans le même appel : ce
  n'est pas une étape manuelle séparée, confirmer *est* la décision de suspendre. Toute décision
  après `CONFIRMED`/`DISMISSED` échoue (`FRAUD_EVENT_ALREADY_DECIDED`).
- `GET /api/v1/fraud-events?status=`, `POST /api/v1/fraud-events/{id}/{review,dismiss,confirm}`.
- **Testé par `FraudDetectionTest`** (5 cas : soumission dupliquée enregistrée + événement OPEN
  levé, non-auto-approbation même quand les règles correspondraient, aucun événement sur une
  soumission non dupliquée [cas de contrôle], `confirm` suspendant réellement l'utilisateur,
  `review` puis `dismiss` valide mais une seconde décision après coup refusée) — les 31 tests du
  projet passent. Vérifié aussi par curl en local et dans le conteneur Docker reconstruit (deux
  comptes soumettent le même contenu sur la même tâche → la deuxième soumission est acceptée
  avec `duplicateOfSubmissionId` renseigné, pas rejetée → l'événement apparaît dans la file →
  `confirm` suspend réellement le compte → une nouvelle participation de ce compte échoue en
  `ACCOUNT_SUSPENDED`).

Explicitement reporté : **BR-050** (appareil associé à plus de N comptes distincts) — aucune
infrastructure de capture d'empreinte d'appareil n'existe nulle part dans l'API (ni table, ni
en-tête, ni champ sur l'inscription/connexion) ; construire ce détecteur demanderait une
migration et un point de capture sur `auth`, bien au-delà du critère de validation explicite de
cette phase, qui ne porte que sur les soumissions dupliquées. `FraudEventType.MULTI_ACCOUNT_
DEVICE` est défini dans l'énumération (prêt à être utilisé) mais n'a encore aucun détecteur,
même logique que les types de transaction `DEBIT`/`FEE`/`REVERSAL`/`ADJUSTMENT` du wallet en
Phase 7 ; arbitrage des litiges (cf. ci-dessus) ; score de risque agrégé par utilisateur (chaque
`FraudEvent` reste isolé, pas de cumul pondéré par compte).

Phase 11 (statistiques) — module `statistics` (`OrganizationStatisticsService`,
`UserStatisticsService`, `PlatformStatisticsService`) — UC-19, UC-20, CDCF §15 :

- **Module de lecture pure** : aucune table propre, aucun état — le package-info le dit
  explicitement. Chaque service compose les services existants (`campaigns`/`tasks`/
  `participations` pour l'organisation, `participations`/`wallet`/`withdrawals` pour
  l'utilisateur, `users`/`organizations`/`wallet`/`fraud` pour la plateforme) plutôt que
  d'interroger leurs dépôts directement — même discipline de frontière de module que partout
  ailleurs (DAT §8.2). Dépendance à sens unique vers tous ces modules ; aucun n'en dépend.
- **KPI Organisation** (`GET /organizations/{id}/statistics`, tout membre actif) : campagnes
  créées/actives, participations totales, taux de validation/rejet (rapportés aux seules
  participations tranchées APPROVED+REJECTED — un rejet et une validation sont complémentaires
  par construction), taux d'abandon (CANCELLED+EXPIRED rapporté au total brut, lui, car l'abandon
  est un phénomène du parcours entier), budget dépensé, coût moyen par participation
  (`budget_consumed` / nombre de participations APPROVED — le seul sous-ensemble qui a
  réellement coûté de l'argent). Hypothèses explicites, le dossier ne donnant pas de formule.
- **KPI Utilisateur** (`GET /users/me/statistics`, soi-même uniquement) : tâches
  réalisées (`submitted_at` non nul)/validées/rejetées, taux de réussite, revenu total et moyen
  (directement depuis `wallets.lifetime_earned`, corrigé en Phase 8), montant et fréquence des
  retraits (retraits `COMPLETED`, total cumulé — une vraie fréquence temporelle, par mois ou par
  semaine, demanderait un découpage hors scope de cette phase).
- **KPI Plateforme** (`GET /admin/statistics`, `PLATFORM_ADMIN`/`OPERATOR`) : utilisateurs et
  organisations actifs, volume financier transité (somme de tous les `wallet_transactions` de
  type `CREDIT` — couvre à la fois les dépôts d'organisation et les récompenses, donc tout
  l'argent qui est jamais entré dans le système de wallets), événements de fraude par niveau de
  risque (Phase 10). **Deux champs du dossier manquent délibérément**, documentés plutôt que
  simulés par un zéro trompeur : les commissions perçues (`commissionsCollected` vaut toujours
  0 — BR-044 reste reportée depuis la Phase 7) et les litiges/taux de résolution (le module
  `disputes` n'a jamais été implémenté, orphelin des 11 phases de la roadmap — aucun champ
  n'est exposé pour ça plutôt qu'un 0 qui se lirait comme "aucun litige" au lieu de
  "fonctionnalité absente").
- **Testé par `StatisticsTest`** (3 cas — c'est le critère de validation explicite de cette
  phase, "les KPI sont affichés et corrects sur données de test") : fait tourner une vraie
  campagne via les services réels (publish → approve/reject/cancel), pas du SQL brut, pour que
  chaque nombre attendu soit connu par construction et pas seulement "plausible à l'œil" —
  validationRate/rejectionRate/abandonRate/budgetSpent vérifiés sur un mélange APPROVED+
  REJECTED+CANCELLED, revenu/taux de réussite vérifiés côté utilisateur, compteurs plateforme
  vérifiés en delta (avant/après une action) pour rester indépendants de l'ordre d'exécution des
  tests dans la même classe. Les 34 tests du projet passent. Vérifié aussi par curl en local et
  dans le conteneur Docker reconstruit (campagne publiée → participation approuvée → les trois
  endpoints renvoient des chiffres qui correspondent exactement au scénario : 1 campagne active,
  validationRate 1.0, revenu utilisateur = récompense exacte, volume financier plateforme =
  dépôt + récompense).

Explicitement reporté à l'époque : MFA/TOTP et réinitialisation de mot de passe (Phase 2) —
soldés en Phase 12, voir plus bas. Toujours reporté : litiges/UC-15, BR-044, BR-050, KYC
utilisateur, le vrai `PaymentProvider` async — chacun documenté dans sa propre section
ci-dessus, et pour certains déjà planifiés pour une phase suivante (voir plus bas).

Pas encore implémenté dans le périmètre des 11 phases du DAT : rien — la roadmap elle-même est
close depuis cette phase. Ce qui suit (Phase 12+) reprend, une par une, les points reportés au
fil des 11 phases.

---

## Au-delà des 11 phases du DAT

Les phases suivantes n'ont pas de numéro dans la roadmap d'origine — elles reprennent,
phase par phase et dans un ordre proposé par l'assistant puis validé par le porteur de
projet, les points explicitement reportés ci-dessus. Ordre retenu : (12) sécurité des
comptes, (13) compléments cycle de vie des campagnes, (14) litiges, (15) KYC utilisateur,
(16) anti-fraude BR-050, (17) wallet/paiements compléments financiers, (18) administration —
durcissement.

Phase 12 (sécurité des comptes) — MFA/TOTP, réinitialisation de mot de passe, révocation
globale des sessions (CDCT §8) :

- **MFA/TOTP** (`TotpService`, `MfaService`, `Base32Codec`) : RFC 6238 (HMAC-SHA1, 6 chiffres,
  pas de 30s) implémenté à la main (~20 lignes de `javax.crypto`, même esprit que le SHA-256
  fait main de `SubmissionService` pour BR-051) — aucune nouvelle dépendance Maven. Pas de
  Base32 non plus dans `java.util.Base64` : un petit codec RFC 4648 maison
  (`Base32Codec`, package-privé).
  - `POST /auth/mfa/setup` (Bearer) génère un secret et l'enregistre (`mfa_enabled` reste faux
    tant que non confirmé) ; `POST /auth/mfa/enable` confirme avec un code TOTP ;
    `POST /auth/mfa/disable` désactive, sauf si le rôle rend le MFA obligatoire (voir plus bas).
  - Connexion à deux temps quand `mfa_enabled` est vrai : `POST /auth/login` ne renvoie plus de
    jetons mais `{mfaRequired:true, mfaChallengeToken}` (jeton Redis, 5 min, même discipline que
    les jetons de `VerificationService`) ; `POST /auth/mfa/login-verify` termine la connexion.
    **Un code faux ne consomme pas le challenge** — bug trouvé et corrigé en écrivant cette
    phase : la première version consommait (supprimait) le jeton Redis avant même de vérifier
    le code, donc une faute de frappe sur le code forçait à tout recommencer (mot de passe
    inclus). Corrigé en ne consommant le challenge qu'après un code valide
    (`MfaService#peekLoginChallenge` + `invalidateLoginChallenge`, plutôt qu'un
    `consumeLoginChallenge` unique) — détecté par le test automatisé, pas en vérification
    manuelle.
  - **"Obligatoire pour PLATFORM_ADMIN/OPERATOR/ORGANIZATION_OWNER" (CDCT §8) est appliqué
    comme une forte incitation, pas un verrou technique dur** : un compte dont le rôle l'exige
    peut toujours se connecter normalement sans MFA activé (son `refresh_token` existant doit
    continuer à fonctionner), mais la réponse porte `mfaSetupRecommended:true`, et une fois le
    MFA activé, il ne peut plus être désactivé pour ce rôle (`MFA_MANDATORY_FOR_ROLE`). Un vrai
    verrou dur (bloquer la connexion tant que l'enrôlement n'est pas terminé) demanderait un état
    de session intermédiaire restreint — un renforcement raisonnable laissé pour plus tard,
    disproportionné pour le périmètre de cette phase. `MfaService` dépend de `organizations`
    (nouvelle dépendance à sens unique `auth` → `organizations`, sans cycle) uniquement pour
    savoir si l'utilisateur est `OWNER` d'au moins une organisation active.
  - **Aucune récupération en cas de perte de l'appareil d'authentification** (pas de codes de
    secours — explicitement hors scope) : constaté en écrivant cette phase en se verrouillant
    soi-même un compte admin de test pendant la vérification manuelle, débloqué par une mise à
    jour SQL directe sur `mfa_enabled`/`mfa_secret`. En production, ce serait un vrai incident —
    documenté ici comme limitation connue, pas comme un bug.
- **Réinitialisation de mot de passe** (`PasswordResetService`) : même discipline Redis que la
  vérification email (jeton à usage unique, 1h, jamais persisté en base), aucune fuite
  d'existence de compte (`requestReset` répond pareil que l'email existe ou non). La
  confirmation **révoque aussi toutes les sessions existantes** — le moment exact où un
  éventuel jeton de rafraîchissement déjà volé doit cesser de fonctionner.
- **Révocation de toutes les sessions d'un coup** (`RefreshTokenService#revokeAllForUser`) :
  un index inverse Redis (`refresh-tokens-by-user:{userId}`, un `SET`) ajouté à la gestion déjà
  existante des jetons de rafraîchissement — `POST /auth/sessions/revoke-all` (self-service).
  **Branché aussi automatiquement sur la suspension/bannissement** : `SessionRevocationListener`
  écoute `UserStatusChangedEvent` (déjà publié depuis la Phase 9) et révoque toutes les sessions
  dès qu'un compte passe `SUSPENDED`/`BANNED` — jusqu'ici, le jeton d'accès déjà émis (≤15 min)
  restait valide jusqu'à expiration naturelle même après suspension, puisqu'un JWT n'est jamais
  revérifié en base par requête ; ça reste vrai pour la fenêtre de 15 minutes, mais les sessions
  ne peuvent plus être renouvelées immédiatement.
- MFA activé/désactivé et mot de passe réinitialisé publient `UserSecurityActionEvent`, écouté
  par `audit` (même direction que `UserStatusChangedEvent`/`OrganizationStatusChangedEvent`).
- `V9__add_user_mfa_secret.sql` ajoute la colonne manquante (`mfa_enabled` existait depuis la
  V1, jamais le secret lui-même).
- **Testé par `SecurityTest`** (6 cas) — premier test du projet qui a réellement besoin de
  Redis joignable : toutes les phases précédentes contournaient sans le savoir les chemins de
  code d'`auth` qui touchent Redis. Un conteneur Redis générique (`GenericContainer`, pas de
  nouvelle dépendance Maven) rejoint le PostgreSQL déjà présent. Les 40 tests du projet passent.
  Vérifié aussi par curl en local et dans le conteneur Docker reconstruit (avec un générateur
  TOTP Python indépendant pour calculer de vrais codes RFC 6238 — setup → code faux refusé →
  code juste accepté → connexion à deux temps avec un code faux qui ne casse pas le challenge
  → mot de passe réinitialisé avec révocation de session vérifiée → révocation globale
  vérifiée → suspension déclenchant la révocation automatiquement).

Explicitement reporté : codes de secours MFA (cf. ci-dessus) ; verrou dur de connexion pour les
rôles à MFA obligatoire (cf. ci-dessus) ; écran de gestion MFA pour un admin au nom d'un autre
utilisateur (seul le self-service existe).

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

## Documentation API (Swagger / OpenAPI)

Une fois l'application démarrée, la documentation interactive est disponible sur
`http://localhost:<SERVER_PORT>/swagger-ui/index.html` (le JSON brut OpenAPI 3 sur
`/v3/api-docs`). Générée via `springdoc-openapi-starter-webmvc-ui`
([`OpenApiConfig`](src/main/java/com/platform/common/config/OpenApiConfig.java)) à partir des
annotations `@Tag`/`@Operation` posées sur chaque contrôleur — un tag par module métier
(Authentification, MFA, Utilisateurs, Organisations, Campagnes, Tâches, Participations,
Soumissions, Validation, Portefeuille, Paiements (dépôts), Moyens de paiement, Retraits,
Anti-fraude, Administration, Statistiques). Les chemins `/swagger-ui/**` et `/v3/api-docs/**`
sont publics (`permitAll`, cf. `SecurityConfig`) : utilisez le bouton "Authorize" avec
l'`accessToken` reçu de `POST /api/v1/auth/login` pour tester les endpoints protégés directement
depuis l'interface.

## Notifications réelles (email / SMS)

`VerificationNotifier` (vérification email, OTP téléphone, réinitialisation de mot de passe) a
deux implémentations, sélectionnées par `notifications.enabled` :

- `NOTIFICATIONS_ENABLED=false` (défaut — dev/tests) : `LoggingVerificationNotifier` se contente
  de logger le token/code, aucun envoi réel. C'est pour ça que les tests et `mvn spring-boot:run`
  local n'ont besoin d'aucune config SMTP/SMS.
- `NOTIFICATIONS_ENABLED=true` : `MailSmsVerificationNotifier` envoie pour de vrai, via
  [`MailService`](src/main/java/com/platform/notifications/mail/MailService.java) (SMTP,
  `spring-boot-starter-mail`) et
  [`SmsService`](src/main/java/com/platform/notifications/sms/SmsService.java) (API Djosend).

Variables à renseigner dans ce cas (voir `.env.prod.example`) :

| Variable | Rôle |
| --- | --- |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | Compte SMTP (réutilise celui du projet note-ta-boite — `mail.notetaboite.com:465`) |
| `MAIL_FROM_NAME` | Nom affiché comme expéditeur (distinct de note-ta-boite) |
| `SMS_API_KEY`, `SMS_SENDER_ID` | Compte Djosend (réutilise celui du projet mon-point) |

Un échec d'envoi (SMTP ou SMS en panne) est loggé mais ne fait jamais échouer le flux appelant
(inscription, connexion, mot de passe oublié) — l'envoi est un effet secondaire, pas une
condition de succès de ces opérations.

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

## Déploiement continu (VPS + GitHub Actions)

Pipeline (`.github/workflows/backend-deploy.yml`), déclenché à chaque push sur `main` qui
touche `backend/**` :

1. **test** — `mvn test`.
2. **build-and-push** — construit l'image Docker (`backend/Dockerfile`) et la publie sur
   GitHub Container Registry : `ghcr.io/yvesleopoldbeugre/plateforme-taches-remunerees-backend`,
   taguée `latest` et avec le SHA court du commit (rollback possible).
3. **deploy** — se connecte en SSH au VPS et exécute `docker compose pull/up` sur
   `backend/docker-compose.prod.yml` (stack de prod : Postgres + Redis + app, l'image étant
   tirée depuis GHCR plutôt que reconstruite sur place).

### Secrets GitHub à configurer (Settings → Secrets and variables → Actions)

| Secret | Valeur |
| --- | --- |
| `VPS_HOST` | IP ou domaine du VPS |
| `VPS_USER` | utilisateur SSH de déploiement |
| `VPS_SSH_KEY` | clé privée SSH dédiée (voir ci-dessous) |
| `VPS_PORT` | port SSH, si différent de 22 (optionnel) |
| `VPS_DEPLOY_PATH` | dossier sur le VPS contenant `docker-compose.prod.yml` + `.env`, ex. `/opt/plateforme-taches` |

### Mise en place initiale du VPS (une seule fois)

```bash
# 1. Sur ta machine : générer une clé SSH dédiée au déploiement (sans passphrase, lue par CI)
ssh-keygen -t ed25519 -f deploy_key -C "github-actions-deploy" -N ""
# -> colle deploy_key (clé privée) dans le secret GitHub VPS_SSH_KEY
# -> ajoute deploy_key.pub au ~/.ssh/authorized_keys de l'utilisateur de déploiement sur le VPS

# 2. Sur le VPS : installer Docker + Docker Compose (Ubuntu/Debian)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # puis se reconnecter

# 3. Sur le VPS : préparer le dossier de déploiement
sudo mkdir -p /opt/plateforme-taches && sudo chown $USER:$USER /opt/plateforme-taches
```

Copier ensuite `backend/docker-compose.prod.yml` et `backend/.env.prod.example` vers le VPS :

```bash
scp backend/docker-compose.prod.yml backend/.env.prod.example \
    <user>@<vps>:/opt/plateforme-taches/
```

Sur le VPS, renommer `.env.prod.example` en `.env` et y mettre de vraies valeurs
(`DB_PASSWORD`, `JWT_SECRET` fort, laisser `ADMIN_BOOTSTRAP_SECRET` vide une fois le premier
PLATFORM_ADMIN créé). Rendre le paquet GHCR public une fois la première image poussée
(GitHub → ton profil → Packages → le paquet → Package settings → Change visibility → Public),
sinon le `docker compose pull` échouera sur le VPS faute d'authentification au registry.

Premier démarrage manuel sur le VPS (avant même le premier passage du workflow) :

```bash
cd /opt/plateforme-taches
docker compose -f docker-compose.prod.yml pull app   # si une image a déjà été poussée
docker compose -f docker-compose.prod.yml up -d
curl http://localhost:8080/actuator/health
```

Les pushes suivants sur `main` redéploient automatiquement. Un reverse proxy (nginx/Caddy) et
HTTPS (Let's Encrypt) ne sont pas couverts ici — à ajouter séparément si un nom de domaine
pointe vers le VPS.
