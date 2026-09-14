package com.survey.meetorsolo.domain.admin.auth.service;

import com.survey.meetorsolo.domain.admin.auth.entity.AdminCredential;
import com.survey.meetorsolo.domain.admin.auth.repository.AdminCredentialRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 실패 누적을 별도 transaction으로 기록한다.
 *
 * <p><b>이 클래스가 따로 있는 이유가 전부 여기에 있다.</b> 실패는 {@code BusinessException}
 * (= {@code RuntimeException})을 던져서 알리는데, 던지는 순간 호출부의 transaction이 rollback
 * 된다. 같은 transaction 안에서 실패 횟수를 올리면 그 UPDATE도 함께 사라져 <b>잠금이 영원히
 * 걸리지 않는다</b>. {@code REQUIRES_NEW}는 바깥 transaction을 잠시 멈추고 자기 transaction을
 * 따로 commit하므로, 바깥이 rollback돼도 기록은 남는다.
 *
 * <p>바깥 transaction이 이 행을 쓰기 잠금하지 않는다는 전제가 필요하다. 호출부는
 * {@code findByUsername}으로 읽기만 하므로 (PostgreSQL의 평범한 SELECT는 행을 잠그지 않는다)
 * 새 transaction이 같은 행을 UPDATE해도 서로 기다리지 않는다.
 */
@Service
public class AdminLoginFailureRecorder {

    private final AdminCredentialRepository credentials;

    public AdminLoginFailureRecorder(AdminCredentialRepository credentials) {
        this.credentials = credentials;
    }

    /**
     * 이미 사라진 자격증명이면 조용히 넘어간다. 실패를 알리는 도중에 다시 실패하면
     * 원래의 로그인 실패 응답이 500으로 뒤바뀐다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long credentialId, OffsetDateTime now) {
        credentials.findById(credentialId).ifPresent(credential -> credential.recordFailure(now));
    }

    /** 실패 기록은 자격증명 id로만 한다. 호출부의 entity를 다른 transaction으로 넘기지 않는다. */
    public void record(AdminCredential credential, OffsetDateTime now) {
        record(credential.getId(), now);
    }
}
