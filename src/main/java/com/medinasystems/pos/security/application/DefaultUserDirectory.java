package com.medinasystems.pos.security.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.UserDirectory;
import com.medinasystems.pos.security.UserInfo;
import com.medinasystems.pos.security.infrastructure.AppUserRepository;

/** Answers {@link UserDirectory} from the user table. */
@Component
public class DefaultUserDirectory implements UserDirectory {

    private final AppUserRepository users;

    public DefaultUserDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserInfo> find(long userId) {
        return users.findById(userId).map(user -> new UserInfo(user.getId(), user.getUsername(),
                user.getFullName()));
    }
}
