package com.medinasystems.pos.sales.application;

/** Answer to a release of the claims of one return: how many claims were given back. Zero is not an error. */
public record ReleaseClaimsResult(String claimKey, int released) {
}
