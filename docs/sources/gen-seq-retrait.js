const { buildSequence, renderTo } = require('./seq-lib');

const participants = [
  { id: 'user', label: 'Utilisateur', color:'#1f3a8a' },
  { id: 'api', label: 'API Withdrawals', color:'#0f766e' },
  { id: 'db', label: 'Base de données', color:'#92400e' },
  { id: 'wallet', label: 'Wallet Service', color:'#9d174d' },
  { id: 'provider', label: 'Provider de Paiement', color:'#374151' },
  { id: 'notif', label: 'Module Notifications', color:'#334155' },
];

const messages = [
  { from:'user', to:'api', type:'sync', label:'POST /withdrawals { amount, paymentMethodId } — Header Idempotency-Key' },
  { from:'api', to:'db', type:'sync', label:"vérifier l'idempotency_key (déjà traitée ?)" },
  { from:'db', to:'api', type:'return', label:'aucune occurrence — nouvelle demande' },
  { from:'api', to:'wallet', type:'sync', label:'réserver(walletId, montant)' },
  { from:'wallet', to:'db', type:'sync', label:'UPDATE wallets SET available-=montant, reserved+=montant WHERE available>=montant' },
  { from:'db', to:'wallet', type:'return', label:'1 ligne affectée (fonds réservés, BR-041 respectée)' },
  { from:'wallet', to:'db', type:'sync', label:'INSERT wallet_transactions (type=RESERVE, idempotency_key)' },
  { from:'api', to:'db', type:'sync', label:'INSERT withdrawals (status=REQUESTED)' },
  { from:'api', to:'user', type:'return', label:'201 Created { withdrawalId, status: REQUESTED, netAmount }' },
  { type:'note', over:['db','provider'], lines:['Traitement asynchrone : un PLATFORM_OPERATOR approuve la demande, puis le transfert est initié (file RabbitMQ).'] },
  { from:'api', to:'provider', type:'async', label:'initier le transfert Mobile Money' },
  { from:'provider', to:'api', type:'async', label:'webhook : transfert confirmé (idempotent)' },
  { from:'api', to:'db', type:'sync', label:'UPDATE withdrawals SET status=COMPLETED' },
  { from:'api', to:'wallet', type:'sync', label:'finaliser(walletId, montant)' },
  { from:'wallet', to:'db', type:'sync', label:'INSERT wallet_transactions (type=WITHDRAWAL) ; UPDATE wallets SET reserved-=montant' },
  { from:'api', to:'notif', type:'async', label:'publier événement WithdrawalCompleted (SMS + IN_APP)' },
];

const svg = buildSequence({
  title: 'Séquence — Demande de retrait utilisateur',
  participants, messages, width: 1800,
});

renderTo(svg, '../diagrams/sequence-03-retrait.png').then(i=>console.log('OK',i)).catch(e=>{console.error(e);process.exit(1);});
