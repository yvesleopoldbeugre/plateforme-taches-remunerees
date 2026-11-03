/**
 * Lecture agrégée pure (UC-19, UC-20, CDCF §15) : aucune table propre, aucun état — ce module
 * compose les services existants (campaigns, tasks, participations, wallet, users, organizations,
 * withdrawals, fraud) pour calculer des KPI à la demande. Dépendance à sens unique vers eux tous ;
 * aucun module ne dépend de celui-ci (DAT §8.1).
 */
package com.platform.statistics;
