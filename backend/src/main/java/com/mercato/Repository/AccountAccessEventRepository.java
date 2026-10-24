package com.mercato.Repository;
import com.mercato.Entity.AccountAccessEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
public interface AccountAccessEventRepository extends JpaRepository<AccountAccessEvent,Long> {
    Page<AccountAccessEvent> findBySubjectIdOrderByCreatedAtDescIdDesc(String subjectId, Pageable page);
}
