package com.mercato.Service;

import com.mercato.Entity.Address;
import com.mercato.Entity.EcommUser;
import com.mercato.ExceptionHandler.ForbiddenOperationException;
import com.mercato.ExceptionHandler.ResourceNotFoundException;
import com.mercato.Mapper.AddressMapper;
import com.mercato.Payloads.Request.AddressRequestDTO;
import com.mercato.Payloads.Response.AddressResponseDTO;
import com.mercato.Repository.AddressRepository;
import com.mercato.Utils.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressRepository addressRepository;
    private final AuthUtil authUtil;
    private final com.mercato.Repository.UserRepository userRepository;

    private EcommUser lockCurrentUser() {
        return userRepository.findByUserIdForUpdate(authUtil.getLoggedInUser().getUserId()).orElseThrow();
    }

    private List<Address> normalizeDefaults(EcommUser user) {
        List<Address> addresses = addressRepository.findByUser_UserIdOrderByIdAsc(user.getUserId());
        Address chosen = addresses.stream().filter(Address::isDefaultAddress).findFirst()
                .orElse(addresses.isEmpty() ? null : addresses.get(0));
        addresses.forEach(address -> address.setDefaultAddress(address == chosen));
        return addresses;
    }

    @Override
    @Transactional
    public AddressResponseDTO createAddress(AddressRequestDTO addressRequestDTO) {
        EcommUser currentUser = lockCurrentUser();

        Address newAddress = new Address();
        newAddress.setRecipientName(addressRequestDTO.getRecipientName());
        newAddress.setRecipientPhone(addressRequestDTO.getRecipientPhone());
        newAddress.setAddressLine1(addressRequestDTO.getAddressLine1());
        newAddress.setAddressLine2(addressRequestDTO.getAddressLine2());
        newAddress.setCity(addressRequestDTO.getCity());
        newAddress.setState(addressRequestDTO.getState());
        newAddress.setPincode(addressRequestDTO.getPincode());
        newAddress.setCountry(addressRequestDTO.getCountry());
        newAddress.setDefaultAddress(normalizeDefaults(currentUser).isEmpty());

        currentUser.addAddress(newAddress);

        Address savedAddress = addressRepository.save(newAddress);
        return AddressMapper.toDTO(savedAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponseDTO> getAllAddresses() {
        List<Address> addresses = addressRepository.findAll();
        return addresses.stream()
                .map(AddressMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AddressResponseDTO getAddress(String addressId) {
        Address address = getAddressById(addressId); // throws
        return AddressMapper.toDTO(address);
    }

    @Override
    @Transactional
    public List<AddressResponseDTO> getUserAddresses() {
        EcommUser currentUser = lockCurrentUser();
        List<Address> addresses = normalizeDefaults(currentUser);
        return addresses.stream()
                .map(AddressMapper::toDTO)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponseDTO updateAddress(String addressId, AddressRequestDTO addressRequestDTO) {
        Address address = getAddressById(addressId);
        EcommUser currentUser = authUtil.getLoggedInUser();

        if (address.getUser() == null) {
            throw new IllegalStateException("Address is not associated with any user");
        }
        if (!address.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("You are not authorized to perform this action.");
        }

        address.setRecipientName(addressRequestDTO.getRecipientName());
        address.setRecipientPhone(addressRequestDTO.getRecipientPhone());
        address.setAddressLine1(addressRequestDTO.getAddressLine1());
        address.setAddressLine2(addressRequestDTO.getAddressLine2());
        address.setCity(addressRequestDTO.getCity());
        address.setState(addressRequestDTO.getState());
        address.setPincode(addressRequestDTO.getPincode());
        address.setCountry(addressRequestDTO.getCountry());

        return AddressMapper.toDTO(address);
    }

    @Override
    @Transactional
    public void deleteAddress(String addressId) {
        EcommUser currentUser = lockCurrentUser();
        Address address = getAddressById(addressId);

        if (address.getUser() == null) {
            throw new IllegalStateException("Address is not associated with any user");
        }
        if (!address.getUser().getId().equals(currentUser.getId())) {
            throw new ForbiddenOperationException("You are not authorized to perform this action.");
        }

        currentUser.removeAddress(address);
        addressRepository.delete(address);
        addressRepository.flush();
        normalizeDefaults(currentUser);
    }

    @Override
    @Transactional
    public AddressResponseDTO setDefaultAddress(String addressId) {
        EcommUser user = lockCurrentUser();
        List<Address> addresses = addressRepository.findByUser_UserIdOrderByIdAsc(user.getUserId());
        Address selected = addresses.stream().filter(address -> addressId.equals(address.getAddressId())).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));
        addresses.forEach(address -> address.setDefaultAddress(false));
        addressRepository.flush();
        selected.setDefaultAddress(true);
        return AddressMapper.toDTO(selected);
    }


    private Address getAddressById(String addressId) {
        return addressRepository.findByAddressId(addressId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "addressId", addressId));
    }
}
