package me.login.misc.firesale.model;

public enum SaleStatus {
    PENDING,  // Created, but not yet started
    ACTIVE,   // Currently running
    COMPLETED, // Ended because stock ran out
    EXPIRED,   // Ended because time ran out
    CANCELLED  // Manually removed by an admin
}