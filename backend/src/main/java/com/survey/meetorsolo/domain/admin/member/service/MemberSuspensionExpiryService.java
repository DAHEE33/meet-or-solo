package com.survey.meetorsolo.domain.admin.member.service;

import com.survey.meetorsolo.domain.admin.member.repository.AdminMemberRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberSuspensionExpiryService {

    private final AdminMemberRepository members;
    private final Clock clock;

    public MemberSuspensionExpiryService(AdminMemberRepository members, Clock clock) {
        this.members = members;
        this.clock = clock;
    }

    @Transactional
    public int restoreBatch(int batchSize) {
        return members.restoreExpiredSuspensions(OffsetDateTime.now(clock), batchSize);
    }
}
