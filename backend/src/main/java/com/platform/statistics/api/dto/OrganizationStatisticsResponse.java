package com.platform.statistics.api.dto;

import java.math.BigDecimal;

/**
 * CDCF §15 — "Organisation" row: "campagnes créées/actives, participations, taux de validation,
 * taux de rejet, taux d'abandon, budget dépensé, coût moyen par participation."
 *
 * <p>Hypothèses explicites (le dossier ne donne pas de formule) : {@code validationRate}/
 * {@code rejectionRate} sont calculés sur les seules participations tranchées (APPROVED +
 * REJECTED), pas sur le total — un rejet et une validation sont par construction complémentaires
 * l'un de l'autre, donc rapporter les deux au même dénominateur (le total brut, qui inclut aussi
 * les réservations encore en cours) les aurait rendus difficiles à lire ensemble.
 * {@code abandonRate} (CANCELLED + EXPIRED) est rapporté au total brut, lui, car l'abandon est un
 * phénomène du parcours entier, pas seulement des soumissions tranchées.
 * {@code averageCostPerParticipation} = budget dépensé / participations APPROVED (le seul
 * sous-ensemble qui a réellement coûté de l'argent).
 */
public record OrganizationStatisticsResponse(
        long campaignsCreated,
        long campaignsActive,
        long totalParticipations,
        BigDecimal validationRate,
        BigDecimal rejectionRate,
        BigDecimal abandonRate,
        BigDecimal budgetSpent,
        BigDecimal averageCostPerParticipation
) {
}
