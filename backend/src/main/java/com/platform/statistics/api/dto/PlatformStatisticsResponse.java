package com.platform.statistics.api.dto;

import java.math.BigDecimal;

/**
 * CDCF §15 — "Plateforme" row: "utilisateurs et organisations actifs, volume financier
 * transité, commissions perçues, nombre de litiges et taux de résolution, nombre d'événements
 * de fraude par niveau de risque."
 *
 * <p>Deux champs du dossier manquent délibérément ici, documentés plutôt que simulés par un
 * zéro trompeur :
 * <ul>
 *   <li>commissions perçues — BR-044 (commission plateforme sur la récompense) reste reportée
 *       depuis la Phase 7 ; {@code commissionsCollected} est donc toujours 0, explicitement, pas
 *       une vraie mesure.</li>
 *   <li>litiges / taux de résolution — le module {@code disputes} n'a jamais été implémenté
 *       (orphelin des 11 phases de la roadmap) ; aucun champ n'est exposé pour ça plutôt que de
 *       renvoyer un 0 qui se lirait comme "aucun litige" au lieu de "fonctionnalité absente".</li>
 * </ul>
 */
public record PlatformStatisticsResponse(
        long activeUsers,
        long activeOrganizations,
        BigDecimal totalFinancialVolume,
        BigDecimal commissionsCollected,
        long fraudEventsLowRisk,
        long fraudEventsMediumRisk,
        long fraudEventsHighRisk
) {
}
