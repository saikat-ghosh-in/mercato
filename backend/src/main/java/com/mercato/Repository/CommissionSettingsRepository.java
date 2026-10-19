package com.mercato.Repository;
import com.mercato.Entity.CommissionSettings;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface CommissionSettingsRepository extends JpaRepository<CommissionSettings, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from CommissionSettings s where s.id = 1")
    Optional<CommissionSettings> lockSettings();
}
