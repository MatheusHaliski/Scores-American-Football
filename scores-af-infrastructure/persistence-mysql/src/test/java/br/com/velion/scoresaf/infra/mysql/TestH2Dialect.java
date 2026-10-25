package br.com.velion.scoresaf.infra.mysql;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * H2Dialect de teste que mapeia {@code @Enumerated(STRING)} para VARCHAR (em vez do ENUM nativo do H2),
 * espelhando o comportamento do MySQL com {@code columnDefinition = "VARCHAR(32)"}, e trata os aliases de texto
 * do modo MySQL (TEXT/MEDIUMTEXT → CLOB/VARCHAR) como equivalentes — permite {@code ddl-auto=validate} sobre o
 * schema criado pelo Flyway a partir de {@code V1__baseline.sql} (escrito para MySQL 8).
 */
public class TestH2Dialect extends H2Dialect {
  @Override
  public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
    super.contributeTypes(typeContributions, serviceRegistry);
    typeContributions.getTypeConfiguration().getJdbcTypeRegistry().addDescriptor(SqlTypes.ENUM, VarcharJdbcType.INSTANCE);
  }

  @Override
  public boolean equivalentTypes(int typeCode1, int typeCode2) {
    return super.equivalentTypes(typeCode1, typeCode2)
        || (isText(typeCode1) && isText(typeCode2));
  }

  private static boolean isText(int code) {
    return code == SqlTypes.VARCHAR || code == SqlTypes.LONGVARCHAR || code == SqlTypes.CLOB || code == SqlTypes.LONG32VARCHAR
        || code == SqlTypes.ENUM || code == SqlTypes.JSON || code == SqlTypes.OTHER;
  }
}
