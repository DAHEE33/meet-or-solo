package com.survey.meetorsolo.domain.admin.safety.dto;

/** 관리자 안전 알림 상태. {@code docs/11_DATABASE_DESIGN.md}의 admin_safety_alerts.status와 같다. */
public enum AdminSafetyAlertStatus {
    /** 누적 임계 도달로 생성된 미확인 알림. */
    OPEN,
    /** 관리자가 확인했으나 아직 조치하지 않은 상태. */
    ACKNOWLEDGED,
    /** 관리자가 해당 회원을 SUSPEND 또는 BAN해 조치가 끝난 상태. */
    CLOSED
}
