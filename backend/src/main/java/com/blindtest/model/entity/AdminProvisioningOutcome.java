package com.blindtest.model.entity;

/** Safe outcome persisted for a first-administrator provisioning attempt. */
public enum AdminProvisioningOutcome {
    PROMOTED,
    ALREADY_ADMIN,
    USER_NOT_FOUND,
    EMAIL_NOT_VERIFIED,
    ADMIN_ALREADY_EXISTS
}
