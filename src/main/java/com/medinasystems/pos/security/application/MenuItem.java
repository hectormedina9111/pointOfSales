package com.medinasystems.pos.security.application;

import java.util.List;

/** One node of the menu a user may see. Children are listed only when their parent is granted too. */
public record MenuItem(String code, String name, String route, String icon, List<MenuItem> children) {
}
