package com.aircargo.authservice.command;

/**
 * Command: attempt to authenticate a user. Immutable input for
 * {@link LoginCommandHandler}; carries no behaviour.
 */
public record LoginCommand(String email, String password, String totpCode, String ipAddress) {

    public LoginCommand {
        if (email != null) {
            email = email.trim();
        }
    }
}
