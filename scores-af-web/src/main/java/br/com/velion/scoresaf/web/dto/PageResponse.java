package br.com.velion.scoresaf.web.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Resposta paginada padrão da API. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
  public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }
  public static <T> PageResponse<T> of(List<T> all, int page, int size) {
    int from = Math.min(page * size, all.size());
    int to = Math.min(from + size, all.size());
    return new PageResponse<>(all.subList(from, to), page, size, all.size(), (int) Math.ceil(all.size() / (double) Math.max(1, size)));
  }
}
