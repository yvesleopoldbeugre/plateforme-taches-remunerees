const { buildSequence, renderTo } = require('./seq-lib');

const participants = [
  { id: 'org', label: 'Organisation (Manager)', color:'#1f3a8a' },
  { id: 'api', label: 'API Validation', color:'#0f766e' },
  { id: 'db', label: 'Base de données', color:'#92400e' },
  { id: 'wallet', label: 'Wallet Service (Ledger)', color:'#9d174d' },
  { id: 'notif', label: 'Module Notifications', color:'#334155' },
];

const messages = [
  { from:'org', to:'api', type:'sync', label:'POST /validations/{submissionId} { decision: APPROVED }' },
  { from:'api', to:'db', type:'sync', label:'INSERT validations (decision=APPROVED, reviewed_by)' },
  { from:'api', to:'db', type:'sync', label:'UPDATE task_participations SET status=APPROVED' },
  { from:'api', to:'db', type:'sync', label:'INSERT rewards (status=PENDING, amount)' },
  { from:'api', to:'wallet', type:'sync', label:'créditer(participationId, montant, idempotencyKey)' },
  { from:'wallet', to:'db', type:'sync', label:'UPDATE campaigns SET budget_consumed += montant WHERE budget_consumed+montant<=budget_reserved' },
  { from:'db', to:'wallet', type:'return', label:'1 ligne affectée (budget disponible confirmé)' },
  { from:'wallet', to:'db', type:'sync', label:'INSERT wallet_transactions (type=WITHDRAWAL, wallet=organisation)' },
  { from:'wallet', to:'db', type:'sync', label:'UPDATE wallets (USER) SET available_balance += montant' },
  { from:'wallet', to:'db', type:'sync', label:'INSERT wallet_transactions (type=CREDIT, wallet=utilisateur, idempotency_key)' },
  { from:'wallet', to:'api', type:'return', label:'Solde utilisateur mis à jour' },
  { from:'api', to:'db', type:'sync', label:'UPDATE rewards SET status=CREDITED, credited_at=now()' },
  { from:'api', to:'notif', type:'async', label:'publier événements SubmissionValidated + RewardCredited' },
  { from:'api', to:'org', type:'return', label:'200 OK { participationStatus: APPROVED }' },
];

const svg = buildSequence({
  title: "Séquence — Validation d'une soumission et crédit de la récompense",
  participants, messages, width: 1650,
});

renderTo(svg, '../diagrams/sequence-02-validation-recompense.png').then(i=>console.log('OK',i)).catch(e=>{console.error(e);process.exit(1);});
