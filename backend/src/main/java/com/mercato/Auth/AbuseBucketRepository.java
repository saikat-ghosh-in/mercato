package com.mercato.Auth;
import org.springframework.data.jpa.repository.*;import org.springframework.data.repository.query.Param;
public interface AbuseBucketRepository extends JpaRepository<AbuseBucket,String> {
 @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @Query("select a from AbuseBucket a where a.bucketKey=:key") java.util.Optional<AbuseBucket> locked(@Param("key") String key);
}
