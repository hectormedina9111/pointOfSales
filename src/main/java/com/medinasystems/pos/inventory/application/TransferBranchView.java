package com.medinasystems.pos.inventory.application;

/**
 * A branch that a transfer can go to, with whether this node operates it. A transfer is sent by the node that operates its
 * origin and received by the node that operates its destination.
 */
public record TransferBranchView(long id, String code, String name, boolean operatedHere) {
}
