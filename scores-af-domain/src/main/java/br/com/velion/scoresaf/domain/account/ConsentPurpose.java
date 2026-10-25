package br.com.velion.scoresaf.domain.account;

/**
 * Finalidades de tratamento que dependem de consentimento (RF46 — Privacidade). Cada finalidade é consentida em separado
 * (sem consentimento agrupado), nunca vem marcada por padrão e é revogável com o mesmo esforço (LGPD Art. 8º §5º).
 * {@link #BIOMETRIC_AVATAR} é dado pessoal sensível (Art. 11): a foto/marcos faciais só são tratados com ele ativo, e a
 * revogação apaga foto, textura e modelo 3D do "Meu jogador" (RF44), as fotos de referência e os parâmetros do Digital Double.
 * O texto do biométrico passou a 2026-10-v2 com o Digital Double (bloco E): passa a citar métricas e parâmetros do rosto
 * guardados e a retenção das fotos; consentimentos na versão anterior aparecem como desatualizados.
 *
 * <p>Futebol americano (bloco 3): o catálogo é o do basquete. {@link #BIOMETRIC_AVATAR} fica porque a mecânica de finalidade
 * sensível (reaceite quando o texto muda, cascata na revogação) é da plataforma e é testada por ele; nenhum dado biométrico é
 * tratado antes do "Meu jogador" (RF44, bloco 9), que liga o {@code ConsentRevocationHandler} dele.
 */
public enum ConsentPurpose {
  ANALYTICS("Art. 7º, I", "2026-09-v1", false),
  MARKETING_EMAIL("Art. 7º, I", "2026-09-v1", false),
  PUBLIC_RANKING("Art. 7º, I", "2026-09-v1", false),
  AI_ANALYST("Art. 7º, I", "2026-09-v1", false),
  BIOMETRIC_AVATAR("Art. 11, I", "2026-10-v2", true);

  private final String legalBasis;
  private final String currentTextVersion;
  private final boolean sensitive;

  ConsentPurpose(String legalBasis, String currentTextVersion, boolean sensitive) {
    this.legalBasis = legalBasis; this.currentTextVersion = currentTextVersion; this.sensitive = sensitive;
  }
  public String legalBasis() { return legalBasis; }
  public String currentTextVersion() { return currentTextVersion; }
  public boolean sensitive() { return sensitive; }
}
