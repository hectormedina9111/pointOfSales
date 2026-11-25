package com.medinasystems.pos.cash.application;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.cash.OpenSessionInfo;
import com.medinasystems.pos.cash.OpenSessions;
import com.medinasystems.pos.cash.domain.CashSessionStatus;
import com.medinasystems.pos.cash.infrastructure.CashRegisterRepository;
import com.medinasystems.pos.cash.infrastructure.CashSessionRepository;

/** Answers {@link OpenSessions} from the open session of the cashier and its register. */
@Component
public class DefaultOpenSessions implements OpenSessions {

    private final CashSessionRepository sessions;
    private final CashRegisterRepository registers;

    public DefaultOpenSessions(CashSessionRepository sessions, CashRegisterRepository registers) {
        this.sessions = sessions;
        this.registers = registers;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OpenSessionInfo> currentFor(long userId) {
        return sessions.findByUserIdAndStatus(userId, CashSessionStatus.OPEN)
                .flatMap(session -> registers.findById(session.getCashRegisterId())
                        .map(register -> new OpenSessionInfo(session.getId(), register.getId(),
                                register.getBranchId(), register.getReceiptPrefix())));
    }
}
