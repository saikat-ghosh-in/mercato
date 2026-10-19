package com.mercato.Repository;
import com.mercato.Entity.CommissionChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CommissionChangeRepository extends JpaRepository<CommissionChange, Long> {
    List<CommissionChange> findAllByOrderByCreatedAtDesc();
    List<CommissionChange> findByStatusIn(List<String> statuses);
}
