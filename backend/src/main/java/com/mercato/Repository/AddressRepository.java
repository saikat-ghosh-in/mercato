package com.mercato.Repository;

import com.mercato.Entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    java.util.List<Address> findByUser_UserIdOrderByIdAsc(String userId);

    Optional<Address> findByAddressId(String addressId);

    Optional<Address> findByAddressIdAndUser_UserId(String addressId, String userId);
}
