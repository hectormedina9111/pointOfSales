package com.medinasystems.pos.shared;

/**
 * Thrown when a requested resource does not exist or is not visible to the
 * current user. Rendered as HTTP 404 in RFC 9457 format.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String resource;
    private final Object id;

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
        this.resource = resource;
        this.id = id;
    }

    public String getResource() {
        return resource;
    }

    public Object getId() {
        return id;
    }
}
