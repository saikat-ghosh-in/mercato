package com.mercato.Security.services;

import com.mercato.Payloads.Response.EcommUserResponseDTO;
import com.mercato.Security.payloads.LoginRequest;
import com.mercato.Security.payloads.RegisterUserRequest;
import com.mercato.Security.payloads.UserInfoResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;

public interface AuthService {

    ResponseEntity<?> authenticateUser(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse httpServletResponse) throws AuthenticationException;

    String registerNewUser(RegisterUserRequest registerUserRequest);

    String getCurrentUsernameFromAuthentication();

    EcommUserResponseDTO getCurrentUserFromAuthentication();

    ResponseEntity<?> signOutCurrentUser();
}
