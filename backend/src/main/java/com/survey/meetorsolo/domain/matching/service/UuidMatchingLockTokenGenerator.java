package com.survey.meetorsolo.domain.matching.service;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UuidMatchingLockTokenGenerator implements MatchingLockTokenGenerator {
    @Override public String generate() { return UUID.randomUUID().toString(); }
}
