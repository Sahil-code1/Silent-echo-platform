package com.silentecho.silentecho_backend.security;

import com.silentecho.silentecho_backend.entity.AccountType;

import java.io.Serializable;

/** The session principal. It holds only the internal account ID and the workspace type. */
public record AuthenticatedAccount(String accountId, AccountType accountType) implements Serializable {
}
