#!/usr/bin/env bash
#
# 운영 PostgreSQL 백업 스크립트.
#
# 운영 VM에서 실행합니다. 로컬 개발 PC용이 아닙니다.
#
#   cd /home/ubuntu/meet-or-solo-prod
#   ./scripts/backup-prod-db.sh
#
# 설계 의도
#   - 이 스크립트는 **비밀번호를 알 필요가 없습니다.** DB 계정과 DB 이름을 컨테이너 안의
#     환경변수에서 읽으므로, .env를 파싱하지도 않고 host의 process 목록에 값이 남지도
#     않습니다.
#   - pg_dump는 custom format(-Fc)입니다. 압축돼 있고 pg_restore로 선택 복원이 됩니다.
#   - 덤프 직후 pg_restore --list로 목록(TOC)을 읽을 수 있는지 확인합니다.
#     ⚠ 이것은 **무결성 검증이 아닙니다.** --list는 아카이브 헤더와 목차만 읽으므로,
#       뒤쪽 데이터 블록이 잘려 있거나 깨져 있어도 통과할 수 있습니다.
#       "파일은 생겼지만 덤프 형식조차 아닌" 경우를 걸러내는 1차 관문일 뿐입니다.
#       실제로 복원되는지는 scripts/restore-prod-db.sh 로 임시 DB에 복원해 봐야 압니다.
#
# 자동 실행(cron) 예시는 docs/07 운영 수동 배포 절차 8절에 있습니다.

set -euo pipefail

CONTAINER="${PROD_DB_CONTAINER:-meet-or-solo-postgres-prod}"
BACKUP_DIR="${PROD_BACKUP_DIR:-/home/ubuntu/backups/meet-or-solo-prod}"
RETENTION_DAYS="${PROD_BACKUP_RETENTION_DAYS:-7}"

TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
DUMP_FILE="${BACKUP_DIR}/meet_or_solo_prod-${TIMESTAMP}.dump"

log() {
    printf '[backup-prod-db] %s\n' "$1"
}

if ! command -v docker >/dev/null 2>&1; then
    log "ERROR: docker를 찾을 수 없습니다."
    exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
    log "ERROR: 컨테이너 ${CONTAINER} 가 실행 중이 아닙니다."
    exit 1
fi

mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

log "덤프 시작 -> ${DUMP_FILE}"

# 작은따옴표를 쓰는 이유: $POSTGRES_USER / $POSTGRES_DB 를 host가 아니라 **컨테이너 안에서**
# 확장시키기 위해서입니다. compose가 주입한 값이 그대로 쓰이고 host에는 남지 않습니다.
if ! docker exec "${CONTAINER}" sh -c \
        'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges' \
        > "${DUMP_FILE}"; then
    log "ERROR: pg_dump 실패. 불완전한 파일을 지웁니다."
    rm -f "${DUMP_FILE}"
    exit 1
fi

chmod 600 "${DUMP_FILE}"

# 1차 관문: 목록(TOC)을 읽을 수 있는가.
#
# ⚠ 무결성 검증이 아닙니다. --list는 헤더와 목차만 읽습니다. 데이터 블록이 잘려 있어도
#   통과할 수 있습니다. 실제 복원 가능 여부는 restore-prod-db.sh 로 확인하세요.
#   (입력 파일명을 주지 않으면 pg_restore는 표준 입력을 읽습니다. '-'는 파일명으로
#    해석될 수 있어 쓰지 않습니다.)
if ! docker exec -i "${CONTAINER}" pg_restore --list < "${DUMP_FILE}" > /dev/null 2>&1; then
    log "ERROR: 덤프 목록을 pg_restore로 읽을 수 없습니다. 파일을 보존하니 확인하세요: ${DUMP_FILE}"
    exit 1
fi

DUMP_SIZE="$(du -h "${DUMP_FILE}" | cut -f1)"
log "덤프 완료 (${DUMP_SIZE})"

# -----------------------------------------------------------------------------
# VM 외부 보관
#
# 로컬 디스크에만 두면 VM 자체가 사라질 때 백업도 함께 사라집니다. 백업이라고 부르려면
# 최소 한 벌은 VM 밖에 있어야 합니다.
#
# ⚠ 이 구간은 **의도적으로 미구성 상태**입니다. 자동 업로드에는 저장소에 둘 수 없는 값이
#   필요합니다 — 원격지 주소, 계정, SSH key 경로(또는 Object Storage bucket과 자격증명).
#   값이 정해지면 PROD_BACKUP_REMOTE와 PROD_BACKUP_SSH_KEY를 서버 환경에 설정하세요.
#   설정 전까지는 docs/07 운영 수동 배포 절차 8절의 수동 복사 절차를 사용합니다.
# -----------------------------------------------------------------------------
if [ -n "${PROD_BACKUP_REMOTE:-}" ]; then
    SSH_OPTS=""
    if [ -n "${PROD_BACKUP_SSH_KEY:-}" ]; then
        SSH_OPTS="-i ${PROD_BACKUP_SSH_KEY}"
    fi
    log "외부 보관소로 복사: ${PROD_BACKUP_REMOTE}"
    # shellcheck disable=SC2086
    scp ${SSH_OPTS} "${DUMP_FILE}" "${PROD_BACKUP_REMOTE}/"
    log "외부 복사 완료"
else
    log "외부 보관 미구성(PROD_BACKUP_REMOTE 없음). 로컬에만 저장했습니다."
    log "docs/07 운영 수동 배포 절차 8절의 수동 복사 절차를 수행하세요."
fi

# -----------------------------------------------------------------------------
# 보관 기간 정리
# -----------------------------------------------------------------------------
DELETED="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'meet_or_solo_prod-*.dump' \
    -mtime "+${RETENTION_DAYS}" -print -delete | wc -l)"
log "보관 기간(${RETENTION_DAYS}일) 경과분 ${DELETED}건 삭제"

REMAINING="$(find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'meet_or_solo_prod-*.dump' | wc -l)"
log "현재 보관 중인 덤프: ${REMAINING}건"
log "완료"
