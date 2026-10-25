package br.com.velion.scoresaf.domain.engine.model;

/** Quem chamou a jogada: a IA pelo plano de jogo, o manager numa decisão, ou a decisão automática quando o prazo venceu (RF62). */
public enum CallSource { AI, USER, AUTO }
