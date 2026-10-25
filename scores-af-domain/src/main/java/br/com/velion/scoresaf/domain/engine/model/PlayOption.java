package br.com.velion.scoresaf.domain.engine.model;

import br.com.velion.scoresaf.domain.shared.Coverage;
import br.com.velion.scoresaf.domain.shared.DefensePackage;
import br.com.velion.scoresaf.domain.shared.Formation;
import br.com.velion.scoresaf.domain.shared.PlayConcept;

/**
 * Opção oferecida numa decisão (RF62). Ataque: conceito e formação ({@code concept} nulo nas conversões e chutes especiais, que o
 * {@code code} já diz). Defesa: pacote, cobertura e blitz. {@code labelKey} é a chave i18n do rótulo.
 */
public record PlayOption(String code, DecisionSide side, PlayConcept concept, Formation formation, DefensePackage defensePackage,
                         Coverage coverage, boolean blitz, String labelKey) {}
