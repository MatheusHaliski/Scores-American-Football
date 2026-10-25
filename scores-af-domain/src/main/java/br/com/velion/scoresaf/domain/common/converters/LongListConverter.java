package br.com.velion.scoresaf.domain.common.converters;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/**
 * Lista de inteiros longos ↔ texto JSON ({@code [100,200]}), sem Jackson no domínio (DomainPurityTest). Usado nos salários por ano
 * do contrato. Lista nula ou vazia grava {@code []}; texto malformado falha na leitura em vez de virar lista vazia em silêncio.
 */
@Converter
public class LongListConverter implements AttributeConverter<List<Long>, String> {

  @Override
  public String convertToDatabaseColumn(List<Long> values) {
    StringBuilder sb = new StringBuilder("[");
    if (values != null) {
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) sb.append(',');
        sb.append(values.get(i) == null ? 0L : values.get(i));
      }
    }
    return sb.append(']').toString();
  }

  @Override
  public List<Long> convertToEntityAttribute(String text) {
    List<Long> out = new ArrayList<>();
    if (text == null || text.isBlank()) return out;
    String s = text.trim();
    if (!s.startsWith("[") || !s.endsWith("]")) throw new IllegalArgumentException("lista JSON de inteiros esperada");
    String body = s.substring(1, s.length() - 1).trim();
    if (body.isEmpty()) return out;
    for (String part : body.split(",")) out.add(Long.parseLong(part.trim()));
    return out;
  }
}
