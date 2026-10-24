package com.mercato.Auth;
import org.springframework.http.HttpStatus;import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthProofException extends RuntimeException { public AuthProofException(){super("Unable to verify. Try again or request a new challenge.");} }
