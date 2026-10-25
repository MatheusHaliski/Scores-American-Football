package br.com.velion.scoresaf.domain.engine.model;

/**
 * Como o drive terminou (RF42). {@link #MISSED_FG} entra além da lista do plano: tratar FG errado como turnover ou punt distorce
 * o gráfico de drives (D-32).
 */
public enum DriveResult { TD, FG, MISSED_FG, PUNT, TURNOVER, DOWNS, SAFETY, END_HALF }
