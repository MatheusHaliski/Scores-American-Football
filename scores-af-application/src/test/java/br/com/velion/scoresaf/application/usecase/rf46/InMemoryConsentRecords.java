package br.com.velion.scoresaf.application.usecase.rf46;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.velion.scoresaf.application.port.out.persistence.ConsentRecordRepository;
import br.com.velion.scoresaf.domain.account.ConsentPurpose;
import br.com.velion.scoresaf.domain.account.ConsentRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Repositório de consentimentos em memória (append-only) sobre Mockito. */
final class InMemoryConsentRecords {
  final List<ConsentRecord> rows = new ArrayList<>();
  final ConsentRecordRepository repo = mock(ConsentRecordRepository.class);

  InMemoryConsentRecords() {
    when(repo.save(any())).thenAnswer(i -> { ConsentRecord r = i.getArgument(0); rows.add(r); return r; });
    when(repo.findByUserIdOrderByRecordedAtDesc(any())).thenAnswer(i -> sorted((UUID) i.getArgument(0)));
    when(repo.findFirstByUserIdAndPurposeOrderByRecordedAtDesc(any(), any())).thenAnswer(i ->
        sorted((UUID) i.getArgument(0)).stream().filter(r -> r.getPurpose() == (ConsentPurpose) i.getArgument(1)).findFirst());
  }

  private List<ConsentRecord> sorted(UUID userId) {
    List<ConsentRecord> l = new ArrayList<>();
    for (int i = rows.size() - 1; i >= 0; i--) if (rows.get(i).getUser().getId().equals(userId)) l.add(rows.get(i));
    l.sort(Comparator.comparing(ConsentRecord::getRecordedAt).reversed());
    return l;
  }
}
