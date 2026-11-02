const H = require('./docx-helpers');
const { h1, h2, p, note, bullet, numbered, dataTable, imageParagraph, pageBreak } = H;

const sections = [
  ...H.titlePage({
    docType: 'Diagramme de Séquence',
    title: "Plateforme de Tâches & Campagnes Rémunérées",
    subtitle: 'MVP — Flux critiques détaillés (UML)',
    project: 'Plateforme de Tâches Rémunérées',
    version: '1.0',
    date: '21 octobre 2026',
    author: 'Document de référence — équipe technique',
  }),
  ...H.tocPage(),

  h1('1. Objet du document'),
  p("Ce document détaille, sous forme de diagrammes de séquence UML, les trois flux techniques les plus critiques de la plateforme : la participation à une tâche et la soumission d'une preuve, la validation d'une soumission avec crédit de la récompense, et la demande de retrait d'un utilisateur. Ces flux concentrent l'essentiel des exigences de cohérence transactionnelle et d'idempotence décrites dans le Cahier des Charges Technique et le DAT."),
  note("Les diagrammes sont volontairement simplifiés (sans barres d'activation) pour rester lisibles ; ils représentent les appels applicatifs et les opérations de base de données déterminantes, pas l'intégralité du code."),
  pageBreak(),

  h1('2. Participation à une tâche et soumission d\'une preuve'),
  p("Ce flux illustre deux moments distincts du cycle de vie d'une participation : la réservation d'une place (avec garantie qu'il n'y a jamais plus de participants que de places disponibles, via une mise à jour conditionnelle atomique), puis la soumission de la preuve après réalisation de la tâche (avec vérification anti-doublon avant mise en file de revue)."),
  ...imageParagraph('../diagrams/sequence-01-participation-soumission.png', { width: 620, caption: "Figure 1 — Séquence : participation et soumission" }),
  h2('Points clés'),
  bullet("L'obtention d'une place est garantie par une opération SQL atomique (UPDATE conditionnel), pas par une lecture puis écriture séparées — élimine la race condition (cf. BR-022)."),
  bullet("La vérification anti-doublon (hash de contenu) a lieu avant l'enregistrement de la soumission et peut bloquer la validation automatique si un doublon est détecté (BR-051)."),
  bullet("La notification de réception est publiée de façon asynchrone (ne bloque pas la réponse à l'utilisateur)."),
  pageBreak(),

  h1('3. Validation d\'une soumission et crédit de la récompense'),
  p("Ce flux illustre la décision d'une organisation sur une soumission, et son effet en cascade sur le portefeuille : consommation du budget de la campagne, puis crédit du portefeuille de l'utilisateur — les deux opérations étant tracées individuellement dans le ledger financier."),
  ...imageParagraph('../diagrams/sequence-02-validation-recompense.png', { width: 620, caption: "Figure 2 — Séquence : validation et crédit de la récompense" }),
  h2('Points clés'),
  bullet("Le débit du budget de campagne est lui aussi une mise à jour conditionnelle (budget_consumed + montant ≤ budget_reserved), garantissant qu'une organisation ne peut jamais dépenser plus que son budget réservé."),
  bullet("Chaque mouvement financier (débit organisation, crédit utilisateur) donne lieu à une écriture distincte et immuable dans wallet_transactions, avec une clé d'idempotence."),
  bullet("La récompense ne passe à l'état CREDITED qu'une fois le ledger effectivement mis à jour — jamais l'inverse."),
  pageBreak(),

  h1('4. Demande de retrait utilisateur'),
  p("Ce flux illustre la demande de retrait, qui combine une vérification d'idempotence (pour supporter un retry réseau sans double traitement), une réservation immédiate des fonds, puis un traitement asynchrone impliquant un provider de paiement externe via webhook."),
  ...imageParagraph('../diagrams/sequence-03-retrait.png', { width: 650, caption: "Figure 3 — Séquence : demande de retrait" }),
  h2('Points clés'),
  bullet("La clé d'idempotence (en-tête Idempotency-Key) est vérifiée avant tout traitement : une requête rejouée avec la même clé ne déclenche jamais une seconde réservation de fonds (prévention du double retrait, BR-042)."),
  bullet("Les fonds sont réservés (available → reserved) dès la demande, pas seulement à l'approbation — le solde affiché à l'utilisateur reste donc toujours cohérent avec son engagement réel."),
  bullet("Le traitement par le provider de paiement est asynchrone (file RabbitMQ) : la confirmation arrive via webhook, lui-même traité de façon idempotente."),
];

H.buildAndSave(sections, '../04-Diagramme-Sequence.docx');
