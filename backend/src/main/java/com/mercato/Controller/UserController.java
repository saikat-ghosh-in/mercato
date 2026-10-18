package com.mercato.Controller;

import com.mercato.Payloads.Request.AddressRequestDTO;
import com.mercato.Payloads.Request.UpdateProfileRequestDTO;
import com.mercato.Payloads.Response.*;
import com.mercato.Service.AddressService;
import com.mercato.Service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AddressService addressService;

    @GetMapping("/admin/users")
    public ResponseEntity<List<EcommUserResponseDTO>> getAllUsers() {
        List<EcommUserResponseDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @PutMapping("/admin/{userId}/roles")
    public ResponseEntity<String> updateRoles(@PathVariable String userId,
                                              @RequestBody Set<String> roles) {

        userService.updateUserRoles(userId, roles);
        return ResponseEntity.ok("Roles modified successfully");
    }

    @GetMapping("/public/sellers")
    public ResponseEntity<List<SellerResponseDTO>> getAllSellers() {
        List<SellerResponseDTO> sellers = userService.getAllSellers();
        return ResponseEntity.ok(sellers);
    }

    @PutMapping("/user/edit-profile")
    public ResponseEntity<EcommUserResponseDTO> updateProfile(
            @RequestBody @Valid UpdateProfileRequestDTO request) {
        return ResponseEntity.ok(userService.updateProfile(request));
    }

    @PostMapping("/user/addresses/add")
    public ResponseEntity<AddressResponseDTO> createAddress(@Valid @RequestBody AddressRequestDTO addressRequestDTO) {
        AddressResponseDTO savedAddressResponseDTO = addressService.createAddress(addressRequestDTO);
        return new ResponseEntity<>(savedAddressResponseDTO, HttpStatus.CREATED);
    }

    @GetMapping("/user/addresses")
    public ResponseEntity<List<AddressResponseDTO>> getUserAddresses() {
        List<AddressResponseDTO> addressResponseDTOList = addressService.getUserAddresses();
        return new ResponseEntity<>(addressResponseDTOList, HttpStatus.OK);
    }

    @PutMapping("/user/addresses/{addressId}")
    public ResponseEntity<AddressResponseDTO> updateAddress(@PathVariable String addressId,
                                                            @Valid @RequestBody AddressRequestDTO addressRequestDTO) {
        AddressResponseDTO updatedAddressResponseDTO = addressService.updateAddress(addressId, addressRequestDTO);
        return new ResponseEntity<>(updatedAddressResponseDTO, HttpStatus.OK);
    }

    @DeleteMapping("/user/addresses/{addressId}")
    public ResponseEntity<MessageResponse> deleteAddress(@PathVariable String addressId) {
        addressService.deleteAddress(addressId);
        return ResponseEntity.ok(new MessageResponse("Address deleted successfully."));
    }

    @PatchMapping("/account/deactivate")
    public ResponseEntity<MessageResponse> deactivateAccount() {
        userService.deactivateAccount();
        return ResponseEntity.ok(new MessageResponse(
                "Account deactivated. You have 7 days to reactivate before permanent deletion."
        ));
    }

    @PatchMapping("/admin/deactivate-account")
    public ResponseEntity<MessageResponse> deactivateUser(@RequestParam String userId,
                                                          @RequestParam(defaultValue = "false") boolean deleteNow) {
        userService.deactivateUser(userId, deleteNow);
        return ResponseEntity.ok(new MessageResponse(
                "Account reactivated successfully. A confirmation email has been sent."
        ));
    }

    @PatchMapping("/admin/reactivate-account")
    public ResponseEntity<MessageResponse> reactivateAccount(@RequestParam String userId) {
        userService.reactivateAccount(userId);
        return ResponseEntity.ok(new MessageResponse(
                "Account reactivated successfully. A confirmation email has been sent."
        ));
    }

    @GetMapping("/deletion-status")
    public ResponseEntity<AccountDeletionStatusResponseDTO> getDeletionStatus() {
        AccountDeletionStatusResponseDTO accountDeletionStatus = userService.getAccountDeletionStatus();
        return ResponseEntity.ok(accountDeletionStatus);
    }
}
