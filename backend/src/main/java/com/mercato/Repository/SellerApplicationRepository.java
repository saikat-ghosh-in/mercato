package com.mercato.Repository;
import com.mercato.Entity.SellerApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SellerApplicationRepository extends JpaRepository<SellerApplication, Long> {
    Optional<SellerApplication> findByUser_UserId(String userId);
}
