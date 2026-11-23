package com.medinasystems.pos.shared;

/**
 * Role of this installation: the central node owns master data, a store node
 * runs the daily operation of one branch.
 */
public enum NodeRole {
    CENTRAL,
    STORE
}
