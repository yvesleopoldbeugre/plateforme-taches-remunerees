const H = require('./docx-helpers');
const { h1, h2, p, note, bullet, dataTable, imageParagraph, pageBreak } = H;

const sections = [
  ...H.titlePage({
    docType: "Diagramme de Cas d'Utilisation",
    title: "Plateforme de Tâches & Campagnes Rémunérées",
    subtitle: 'MVP — Vue UML des cas d\'utilisation',
    project: 'Plateforme de Tâches Rémunérées',
    version: '1.0',
    date: '21 octobre 2026',
    author: 'Document de référence — équipe produit / technique',
  }),
  ...H.tocPage(),

  h1('1. Objet du document'),
  p("Ce document présente le diagramme de cas d'utilisation (UML) de la plateforme, qui modélise les interactions entre les acteurs du système et les fonctions qu'ils déclenchent. Il complète le Cahier des Charges Fonctionnel en offrant une vue synthétique et normalisée des périmètres d'action de chaque acteur."),

  h1('2. Notation utilisée'),
  dataTable({
    header: ['Élément', 'Signification'],
    widths: [2800, 6838],
    rows: [
      ['Bonhomme allumette', "Acteur : rôle humain ou système interagissant avec la plateforme."],
      ['Ellipse bleue', "Cas d'utilisation propre à un acteur unique."],
      ['Ellipse orange', "Cas d'utilisation transverse : plusieurs acteurs interviennent dans le même workflow métier (ex. une organisation soumet une campagne à revue, qu'un administrateur approuve ensuite)."],
      ['Ellipse grise', "Cas d'utilisation inclus : sous-fonction technique systématiquement déclenchée par un autre cas d'utilisation (ex. vérification d'éligibilité)."],
      ['Flèche pleine', "Association entre un acteur et un cas d'utilisation qu'il déclenche directement."],
      ['Flèche pointillée «include»', "Le cas d'utilisation source déclenche systématiquement le cas d'utilisation cible comme sous-étape obligatoire."],
      ['Flèche pointillée «extend»', "Le cas d'utilisation source est un prolongement conditionnel du cas d'utilisation cible (ex. un litige ne survient qu'après un rejet)."],
    ],
  }),
  note("Par souci de lisibilité, ce diagramme présente les cas d'utilisation les plus représentatifs de chaque acteur plutôt que l'intégralité du catalogue UC-01 à UC-24, qui est détaillé en section 4."),
  pageBreak(),

  h1('3. Diagramme'),
  ...imageParagraph('../diagrams/diagramme-cas-utilisation.png', { width: 620, caption: "Figure 1 — Diagramme de cas d'utilisation de la plateforme" }),
  pageBreak(),

  h1("4. Catalogue complet des cas d'utilisation"),
  dataTable({
    header: ['Réf.', 'Cas d\'utilisation', 'Acteur principal', 'Relation'],
    widths: [800, 4200, 2538, 1900],
    rows: [
      ['UC-01', "S'inscrire et vérifier son compte", 'USER / ORG_OWNER', '—'],
      ['UC-02', 'Créer une organisation et inviter des membres', 'ORGANIZATION_OWNER', '—'],
      ['UC-03', "Alimenter le portefeuille d'une organisation", 'ORGANIZATION_OWNER', '—'],
      ['UC-04', 'Créer une campagne en brouillon', 'ORG_OWNER / MANAGER', '—'],
      ['UC-05', 'Définir une ou plusieurs tâches', 'ORG_OWNER / MANAGER', '—'],
      ['UC-06', 'Soumettre une campagne à revue', 'ORG_OWNER / MANAGER', 'déclenche UC-07'],
      ['UC-07', 'Publier une campagne', 'PLATFORM_OPERATOR / ADMIN', '—'],
      ['UC-08', 'Parcourir / filtrer le catalogue', 'USER', '—'],
      ['UC-09', 'Participer à une tâche', 'USER', 'include UC-vérif. éligibilité'],
      ['UC-10', 'Soumettre une preuve', 'USER', 'include UC-détection fraude'],
      ['UC-11', 'Valider automatiquement', 'Système (règles)', '—'],
      ['UC-12', 'Valider manuellement', 'ORG_OWNER / MANAGER', 'include UC-16'],
      ['UC-13', 'Rejeter avec motif standardisé', 'ORG_OWNER / MANAGER', '—'],
      ['UC-14', 'Contester un rejet (litige)', 'USER', 'extend UC-12/13'],
      ['UC-15', 'Arbitrer un litige', 'PLATFORM_ADMIN / OPERATOR', '—'],
      ['UC-16', 'Créditer une récompense', 'Système', '—'],
      ['UC-17', 'Demander un retrait', 'USER', '—'],
      ['UC-18', 'Traiter un retrait', 'PLATFORM_OPERATOR', '—'],
      ['UC-19', 'Consulter les statistiques de campagne', 'ORG_OWNER / MANAGER', '—'],
      ['UC-20', "Consulter l'historique financier", 'USER / ORG_OWNER', '—'],
      ['UC-21', 'Suspendre un compte/organisation', 'PLATFORM_ADMIN', '—'],
      ['UC-22', 'Détecter un événement de fraude', 'Système + ADMIN', '—'],
      ['UC-23', "Mettre en pause / annuler une campagne", 'ORG_OWNER / ADMIN', '—'],
      ['UC-24', 'Recevoir une notification', 'Tous rôles', '—'],
    ],
  }),
];

H.buildAndSave(sections, '../03-Diagramme-Cas-Utilisation.docx');
