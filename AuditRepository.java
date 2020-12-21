package com.clearstream.hydrogen.dataaccess;

import com.clearstream.hydrogen.database.AudRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public interface AuditRepository extends JpaRepository<AudRecord, Long>, JpaSpecificationExecutor<AudRecord> {
    @Query("select a from AudRecord a, AudTableMetadata m, AudUser u where a.user = u and a.tableMetadata = m and m.tableName = :table order by a.timestamp desc, a.localTransactionId desc, m.columnName")
    public Page<AudRecord> getByTableName(@Param("table") String table, Pageable pageable);

  @Query("select a from AudRecord a, AudTableMetadata m, AudUser u where a.user = u and a.tableMetadata = m and m.tableName = :table and m.columnName = :column order by a.timestamp desc, a.localTransactionId desc")
  public Page<AudRecord> getByTableAndColumn(@Param("table") String table, @Param("column") String column, Pageable pageable );

  @Query("select a from AudRecord a, AudTableMetadata m, AudUser u where a.user = u and a.tableMetadata = m and m.tableName = :table and m.columnName = :column and a.relatedPk = :id order by a.timestamp desc")
    public Page<AudRecord> getByTableAndColumnAndId(@Param("table") String table, @Param("column") String column, @Param("id") Long id, Pageable pageable);

    @Query("select a from AudRecord a, AudTableMetadata m, AudUser u where a.user = u and a.tableMetadata = m and m.tableName in :tables and m.columnName in :columns order by a.timestamp desc, a.localTransactionId desc, m.tableName, m.columnName")
    public Page<AudRecord> getByTablesAndColumns(@Param("tables") Set<String> tables, @Param("columns") Set<String> columns, Pageable pageable);

    @Query("select a from AudRecord a, AudTableMetadata m, AudUser u where a.user = u and a.tableMetadata = m and m.tableName in :tables and m.columnName in :columns and a.relatedPk in :ids order by a.timestamp desc, a.localTransactionId desc, m.tableName, m.columnName")
    public Page<AudRecord> getByTablesAndColumnsAndIds(@Param("tables") Set<String> tables, @Param("columns") Set<String> columns, @Param("ids") Set<Long> ids, Pageable pageable);

    static Specification<AudRecord> pairwiseMatchNamesAndIds(Map<String, Long> namesAndIds) {
        return (root, criteriaQuery, criteriaBuilder) -> {
          List<Predicate> predicates =
                  namesAndIds
                          .entrySet()
                          .stream()
                          .map(entry -> criteriaBuilder.and(
                                  criteriaBuilder.equal(root.get("tableMetadata")
                                          .get("tableName"), entry.getKey()),
                                  criteriaBuilder.equal(root.get("relatedPk"), entry.getValue())
                                  ))
                          .collect(Collectors.toList());
          return criteriaBuilder.or(predicates.toArray(new Predicate[predicates.size()]));
        };
    }

    static Specification<AudRecord> inDateRange(Instant from, Instant to) {
      return (root, criteriaQuery, criteriaBuilder) ->
              criteriaBuilder.between(root.get("timestamp"), from, to);
    }

}
