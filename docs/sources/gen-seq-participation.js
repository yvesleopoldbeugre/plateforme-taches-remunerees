const { buildSequence, renderTo } = require('./seq-lib');

const participants = [
  { id: 'user', label: 'Utilisateur' , color:'#1f3a8a'},
  { id: 'api', label: 'API Participations', color:'#0f766e' },
  { id: 'db', label: 'Base de données', color:'#92400e' },
  { id: 'fraud', label: 'Module Fraude', color:'#6b21a8' },
  { id: 'notif', label: 'Module Notifications', color:'#334155' },
];

const messages = [
  { from:'user', to:'api', type:'sync', label:'POST /tasks/{id}/participate' },
  { from:'api', to:'db', type:'sync', label:"UPDATE tasks SET current_participants+1 WHERE current_participants<max (CAS)" },
  { from:'db', to:'api', type:'return', label:'1 ligne affectée (place obtenue)' },
  { from:'api', to:'db', type:'sync', label:'INSERT task_participations (status=RESERVED, expires_at)' },
  { from:'api', to:'user', type:'return', label:'201 Created { participationId, status: RESERVED, expiresAt }' },
  { type:'note', over:['user','notif'], lines:["L'utilisateur réalise la tâche puis revient soumettre sa preuve avant expiration (BR-021)"] },
  { from:'user', to:'api', type:'sync', label:'POST /participations/{id}/submission { content, attachments }' },
  { from:'api', to:'fraud', type:'sync', label:'vérifier content_hash (doublon ?)' },
  { from:'fraud', to:'api', type:'return', label:'OK — aucun doublon détecté' },
  { from:'api', to:'db', type:'sync', label:'INSERT task_submissions ; UPDATE participation SET status=UNDER_REVIEW' },
  { from:'api', to:'notif', type:'async', label:'publier événement SubmissionReceived' },
  { from:'api', to:'user', type:'return', label:'201 Created { submissionId, participationStatus: UNDER_REVIEW }' },
];

const svg = buildSequence({
  title: "Séquence — Participation à une tâche et soumission d'une preuve",
  participants, messages, width: 1650,
});

renderTo(svg, '../diagrams/sequence-01-participation-soumission.png').then(i=>console.log('OK',i)).catch(e=>{console.error(e);process.exit(1);});
