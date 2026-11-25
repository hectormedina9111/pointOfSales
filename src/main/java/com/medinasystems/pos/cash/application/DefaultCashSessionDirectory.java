package com.medinasystems.pos.cash.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.CashSessionDirectory;
import com.medinasystems.pos.cash.infrastructure.CashSessionRepository;

@Service
public class DefaultCashSessionDirectory implements CashSessionDirectory {

    private final CashSessionRepository sessions;

    public DefaultCashSessionDirectory(CashSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean exists(long cashSessionId) {
        return sessions.existsById(cashSessionId);
    }
}
