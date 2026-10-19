package com.mercato.Service;

import com.mercato.Payloads.Request.AddressRequestDTO;
import com.mercato.Payloads.Response.AddressResponseDTO;

import java.util.List;

public interface AddressService {
    AddressResponseDTO createAddress(AddressRequestDTO addressRequestDTO);

    List<AddressResponseDTO> getAllAddresses();

    AddressResponseDTO getAddress(String addressId);

    List<AddressResponseDTO> getUserAddresses();

    AddressResponseDTO updateAddress(String addressId, AddressRequestDTO addressRequestDTO);

    void deleteAddress(String addressId);

    AddressResponseDTO setDefaultAddress(String addressId);
}
