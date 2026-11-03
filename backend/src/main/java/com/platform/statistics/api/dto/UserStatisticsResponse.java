package com.platform.statistics.api.dto;

import java.math.BigDecimal;

/**
 * CDCF §15 — "Utilisateur" row: "tâches réalisées/validées/rejetées, revenu total et moyen,
 * taux de réussite, montant et fréquence des retraits."
 *
 * <p>{@code tasksRealized} = participations dont {@code submitted_at} n'est pas nul (une tâche
 * tentée, qu'elle soit encore en revue, validée ou rejetée) ; {@code successRate} =
 * {@code tasksValidated / tasksRealized}. {@code revenueTotal}/{@code revenueAverage} viennent
 * de {@code wallets.lifetime_earned} (Phase 8). {@code withdrawalFrequency} compte les retraits
 * {@code COMPLETED} au total depuis la création du compte — une vraie fréquence (par mois, par
 * semaine) demanderait un découpage temporel hors scope de cette phase.
 */
public record UserStatisticsResponse(
        long tasksRealized,
        long tasksValidated,
        long tasksRejected,
        BigDecimal successRate,
        BigDecimal revenueTotal,
        BigDecimal revenueAverage,
        BigDecimal withdrawalAmount,
        long withdrawalFrequency
) {
}
