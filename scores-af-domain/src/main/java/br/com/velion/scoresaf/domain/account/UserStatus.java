package br.com.velion.scoresaf.domain.account;

/**
 * Situação da conta. PENDING_DELETION = exclusão solicitada (RF46 CA07), com prazo de arrependimento de 7 dias — o login
 * continua possível apenas para cancelar; DELETED = exclusão lógica LGPD (dados pessoais anonimizados, irreversível).
 */
public enum UserStatus { ACTIVE, BLOCKED, PENDING_DELETION, DELETED }
