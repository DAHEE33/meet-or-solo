#!/usr/bin/env bash
#
# 운영 PostgreSQL 복원 / 복원 검증 스크립트.
#
# 운영 VM에서 실행합니다.
#
#   # 1) 복원 검증 (기본값, 운영 DB를 건드리지 않음)
#   ./scripts/restore-prod-db.sh /home/ubuntu/backups/meet-or-solo-prod/<파일>.dump
#
#   # 2) 실제 운영 DB 복원 (장애 상황에서만)
#   ./scripts/restore-prod-db.sh <파일>.dump --target-prod
#
# ─────────────────────────────────────────────────────────────────────────────
# 설계 의도
#
#   - **기본 동작은 검증입니다.** 이번 실행만의 임시 DB를 새로 만들어 거기에 복원하고,
#     테이블 수·Flyway 이력·주요 건수를 보여준 뒤 그 임시 DB만 지웁니다.
#     운영 DB는 열지도 않습니다.
#     "백업이 있다"와 "백업이 복원된다"는 다른 말이고, 후자는 해 봐야만 압니다.
#
#   - **삭제로 시작하지 않습니다.** 이전 판은 `dropdb --if-exists`로 시작했는데,
#     검증 DB 이름이 운영 DB와 같게 설정돼 있으면 그 한 줄이 운영 DB를 지웠습니다.
#     지금은 (1) 이름이 운영 DB와 같으면 어떤 명령도 실행하기 전에 중단하고,
#     (2) 이미 존재하는 DB는 건드리지 않고 중단하며,
#     (3) 이번 실행이 직접 만든 DB만 정리합니다.
#
#   - **DB 이름을 셸 문자열에 끼워 넣지 않습니다.** 컨테이너 안의 `sh -c`에는 고정된
#     스크립트만 넘기고 이름은 위치 인자(`"$1"`)로 전달합니다. 이름 자체도 미리
#     정규식으로 검증합니다.
#
#   - **운영 복원은 4단계를 통과해야만 파괴적 명령에 닿습니다.**
#       ① 덤프 목록(TOC)을 읽을 수 있는가
#       ② 임시 DB에 실제로 복원되는가
#       ③ 현재 운영 DB의 사전 백업이 성공했는가
#       ④ 그때서야 교체
#     ①~③ 중 하나라도 실패하면 운영 DB는 손대지 않은 상태로 남습니다.
#
#   - 비밀번호를 알 필요가 없습니다. 계정과 DB 이름을 컨테이너 환경변수에서 읽습니다.
# ─────────────────────────────────────────────────────────────────────────────

set -euo pipefail

CONTAINER="${PROD_DB_CONTAINER:-meet-or-solo-postgres-prod}"
BACKEND_CONTAINER="${PROD_BACKEND_CONTAINER:-meet-or-solo-backend-prod}"
VERIFY_DB_PREFIX="${PROD_RESTORE_VERIFY_DB_PREFIX:-meet_or_solo_restore_check}"
BACKUP_DIR="${PROD_BACKUP_DIR:-/home/ubuntu/backups/meet-or-solo-prod}"

# 이번 실행이 만든 임시 DB만 정리하기 위한 상태.
VERIFY_DB=""
VERIFY_DB_CREATED=0
KEEP_VERIFY_DB=0

log() {
    printf '[restore-prod-db] %s\n' "$1"
}

die() {
    log "ERROR: $1"
    exit 1
}

# ─────────────────────────────────────────────────────────────────────────────
# docker 래퍼. 컨테이너 안에는 **고정된 스크립트**만 넘기고 가변 값은 위치 인자로 준다.
# ─────────────────────────────────────────────────────────────────────────────

# 운영 DB 이름을 컨테이너 환경변수에서 읽는다.
# 아래 모든 안전장치가 이 값을 기준으로 하므로 호스트 쪽 추측값을 쓰지 않는다.
prod_db_name() {
    docker exec "${CONTAINER}" sh -c 'printf %s "$POSTGRES_DB"' | tr -d '\r'
}

# DB 목록을 그대로 받아 host에서 비교한다. 이름을 SQL이나 셸에 끼워 넣지 않는다.
db_exists() {
    docker exec "${CONTAINER}" \
        sh -c 'psql -U "$POSTGRES_USER" -d postgres -tAc "select datname from pg_database"' \
        | tr -d '\r' | grep -qx "$1"
}

create_db() {
    docker exec "${CONTAINER}" sh -c 'createdb -U "$POSTGRES_USER" -- "$1"' _ "$1"
}

drop_db() {
    docker exec "${CONTAINER}" sh -c 'dropdb -U "$POSTGRES_USER" --if-exists -- "$1"' _ "$1"
}

restore_into() {  # $1 = DB 이름, $2 = 덤프 파일
    docker exec -i "${CONTAINER}" \
        sh -c 'pg_restore -U "$POSTGRES_USER" -d "$1" --no-owner --no-privileges' _ "$1" \
        < "$2"
}

run_sql() {  # $1 = DB 이름, $2 = SQL
    docker exec "${CONTAINER}" \
        sh -c 'psql -U "$POSTGRES_USER" -d "$1" -c "$2"' _ "$1" "$2"
}

# 덤프의 목록(TOC)을 읽을 수 있는지 확인한다.
#
# ⚠ 이것은 **무결성 검증이 아닙니다.** `pg_restore --list`는 아카이브 헤더와 목차만
#   읽습니다. 뒤쪽 데이터 블록이 잘려 있거나 깨져 있어도 목록은 정상으로 나올 수
#   있습니다. 파일이 덤프 형식이 맞는지 보는 **1차 관문**일 뿐이고, 실제 무결성은
#   임시 DB에 복원해 봐야(verify_restore) 확인됩니다.
dump_toc_readable() {
    docker exec -i "${CONTAINER}" pg_restore --list < "$1" > /dev/null 2>&1
}

cleanup() {
    if [ "${VERIFY_DB_CREATED}" -eq 1 ] && [ "${KEEP_VERIFY_DB}" -eq 0 ]; then
        log "임시 DB 정리: ${VERIFY_DB}"
        drop_db "${VERIFY_DB}" || log "WARN: 임시 DB ${VERIFY_DB} 정리에 실패했습니다. 직접 지워주세요."
    elif [ "${VERIFY_DB_CREATED}" -eq 1 ]; then
        log "임시 DB ${VERIFY_DB} 를 남겨 둡니다. 확인 후 직접 지워주세요:"
        log "  docker exec ${CONTAINER} sh -c 'dropdb -U \"\$POSTGRES_USER\" -- ${VERIFY_DB}'"
    fi
}
trap cleanup EXIT INT TERM

# ─────────────────────────────────────────────────────────────────────────────
# 인자와 사전 조건
# ─────────────────────────────────────────────────────────────────────────────

DUMP_FILE="${1:-}"
MODE="verify"
if [ "${2:-}" = "--target-prod" ]; then
    MODE="prod"
elif [ -n "${2:-}" ]; then
    die "알 수 없는 옵션: $2 (사용 가능: --target-prod)"
fi

if [ -z "${DUMP_FILE}" ] || [ ! -f "${DUMP_FILE}" ]; then
    log "사용법: $0 <덤프파일> [--target-prod]"
    exit 1
fi

if [ ! -s "${DUMP_FILE}" ]; then
    die "덤프 파일이 비어 있습니다: ${DUMP_FILE}"
fi

if ! command -v docker >/dev/null 2>&1; then
    die "docker를 찾을 수 없습니다."
fi

if ! docker ps --format '{{.Names}}' | tr -d '\r' | grep -qx "${CONTAINER}"; then
    die "컨테이너 ${CONTAINER} 가 실행 중이 아닙니다."
fi

PROD_DB="$(prod_db_name || true)"
if [ -z "${PROD_DB}" ]; then
    die "컨테이너에서 POSTGRES_DB를 읽지 못했습니다. 컨테이너 상태를 확인하세요."
fi

# ─────────────────────────────────────────────────────────────────────────────
# 임시 DB 이름을 정하고 안전한지 확인한다. 이 블록을 통과하기 전에는
# createdb/dropdb/pg_restore 어느 것도 실행되지 않는다.
# ─────────────────────────────────────────────────────────────────────────────

# 실행별 고유 이름. 같은 시각에 두 번 돌려도 서로의 DB를 건드리지 않는다.
#
# PROD_RESTORE_VERIFY_DB로 전체 이름을 직접 지정할 수도 있다(테스트·디버그용).
# 그 경우에도 아래 검사를 똑같이 통과해야 한다 — 이름 형식, 운영 DB와의 충돌,
# 이미 존재하는지. 즉 이름을 지정한다고 해서 안전장치가 느슨해지지 않는다.
VERIFY_DB="${PROD_RESTORE_VERIFY_DB:-${VERIFY_DB_PREFIX}_$(date +%Y%m%d%H%M%S)_$$}"

# PostgreSQL 식별자 규칙. 따옴표 없이 쓸 수 있는 형태로만 제한한다.
# 이 검사를 통과하면 이름에 셸 메타문자나 따옴표가 섞여 들어갈 여지가 없다.
if ! printf '%s' "${VERIFY_DB}" | grep -qE '^[a-z_][a-z0-9_]*$'; then
    die "임시 DB 이름에 허용되지 않는 문자가 있습니다: ${VERIFY_DB} (a-z, 0-9, _ 만 허용)"
fi
if [ "${#VERIFY_DB}" -gt 63 ]; then
    die "임시 DB 이름이 63자를 넘습니다(PostgreSQL 식별자 한도): ${VERIFY_DB}"
fi

# ★ 가장 중요한 안전장치. 임시 DB 이름이 운영 DB와 같으면 여기서 멈춘다.
#   PROD_RESTORE_VERIFY_DB_PREFIX를 운영 DB 이름으로 잘못 설정한 경우가 여기에 걸린다.
if [ "${VERIFY_DB}" = "${PROD_DB}" ] || [ "${VERIFY_DB_PREFIX}" = "${PROD_DB}" ]; then
    die "임시 DB 이름이 운영 DB(${PROD_DB})와 같습니다. 중단합니다. PROD_RESTORE_VERIFY_DB_PREFIX를 확인하세요."
fi

# 이미 있는 DB는 이번 실행의 것이 아니다. 지우지 않고 멈춘다.
if db_exists "${VERIFY_DB}"; then
    die "임시 DB ${VERIFY_DB} 가 이미 존재합니다. 기존 DB를 건드리지 않고 중단합니다."
fi

# ─────────────────────────────────────────────────────────────────────────────
# 공통: 임시 DB에 복원해 보고 결과를 출력한다.
# 성공하면 0, 실패하면 1을 돌려준다. 실패 시 임시 DB는 조사용으로 남긴다.
# ─────────────────────────────────────────────────────────────────────────────
verify_restore() {
    log "임시 DB 생성: ${VERIFY_DB}"
    if ! create_db "${VERIFY_DB}"; then
        log "ERROR: 임시 DB 생성 실패"
        return 1
    fi
    VERIFY_DB_CREATED=1

    # pgvector extension은 덤프 안에 CREATE EXTENSION으로 들어 있습니다(V11).
    # 이미지가 pgvector/pgvector:pg16이라 extension 파일이 존재합니다.
    log "복원 중..."
    if ! restore_into "${VERIFY_DB}" "${DUMP_FILE}"; then
        log "ERROR: 복원 실패."
        KEEP_VERIFY_DB=1
        return 1
    fi

    log "--- 복원 결과 ---"
    run_sql "${VERIFY_DB}" \
        "select count(*) as tables from information_schema.tables where table_schema = 'public';" || true
    run_sql "${VERIFY_DB}" \
        "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;" || true
    run_sql "${VERIFY_DB}" \
        "select (select count(*) from members) as members, (select count(*) from festivals) as festivals;" || true

    return 0
}

# ─────────────────────────────────────────────────────────────────────────────
# 검증 모드 (기본)
# ─────────────────────────────────────────────────────────────────────────────
if [ "${MODE}" = "verify" ]; then
    log "복원 검증 모드입니다. 운영 DB(${PROD_DB})는 건드리지 않습니다."

    if ! dump_toc_readable "${DUMP_FILE}"; then
        die "덤프 목록(TOC)을 읽을 수 없습니다. 손상되었거나 pg_dump -Fc 형식이 아닙니다: ${DUMP_FILE}"
    fi

    if ! verify_restore; then
        die "복원 검증 실패. 이 백업 파일은 신뢰할 수 없습니다."
    fi

    log "검증 성공. 이 덤프는 복원 가능합니다."
    exit 0
fi

# ─────────────────────────────────────────────────────────────────────────────
# 운영 DB 복원 모드
# ─────────────────────────────────────────────────────────────────────────────
log "⚠ 운영 DB(${PROD_DB}) 복원 모드입니다. 현재 운영 데이터를 덮어씁니다."
log "  backend가 DB에 붙어 있으면 drop이 실패합니다. 먼저 멈추세요:"
log "    docker stop ${BACKEND_CONTAINER}"
log ""
log "  진행 순서: ①덤프 TOC 확인 → ②임시 DB 복원 검증 → ③운영 DB 사전 백업 → ④교체"
log "  ①~③ 중 하나라도 실패하면 운영 DB를 손대지 않고 중단합니다."
printf '계속하려면 RESTORE 를 입력하세요: '
read -r CONFIRM
if [ "${CONFIRM}" != "RESTORE" ]; then
    log "취소했습니다."
    exit 1
fi

if docker ps --format '{{.Names}}' | tr -d '\r' | grep -qx "${BACKEND_CONTAINER}"; then
    die "${BACKEND_CONTAINER} 가 아직 실행 중입니다. 먼저 멈추세요: docker stop ${BACKEND_CONTAINER}"
fi

# --- ① 덤프 TOC ---------------------------------------------------------------
log "[1/4] 덤프 목록(TOC) 확인"
if ! dump_toc_readable "${DUMP_FILE}"; then
    die "덤프 목록을 읽을 수 없습니다. 운영 DB를 손대지 않고 중단합니다: ${DUMP_FILE}"
fi

# --- ② 임시 DB 복원 검증 -------------------------------------------------------
log "[2/4] 임시 DB에 복원해 보기 (운영 DB 미접촉)"
if ! verify_restore; then
    die "이 백업 파일은 복원되지 않습니다. 운영 DB를 손대지 않고 중단합니다."
fi
log "[2/4] 통과. 이 덤프는 복원 가능합니다."

# --- ③ 현재 운영 DB 사전 백업 ---------------------------------------------------
# 이것이 실패하면 교체하지 않는다. 사전 백업 없이 drop하면 되돌릴 방법이 사라진다.
log "[3/4] 현재 운영 DB 사전 백업"
mkdir -p "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}" 2>/dev/null || true
PRE_RESTORE_DUMP="${BACKUP_DIR}/pre-restore-$(date +%Y%m%d-%H%M%S).dump"

if ! docker exec "${CONTAINER}" sh -c \
        'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc --no-owner --no-privileges' \
        > "${PRE_RESTORE_DUMP}"; then
    rm -f "${PRE_RESTORE_DUMP}"
    die "사전 백업 실패. 되돌릴 수단이 없으므로 운영 DB를 손대지 않고 중단합니다."
fi
chmod 600 "${PRE_RESTORE_DUMP}" 2>/dev/null || true

if [ ! -s "${PRE_RESTORE_DUMP}" ]; then
    rm -f "${PRE_RESTORE_DUMP}"
    die "사전 백업 파일이 비어 있습니다. 운영 DB를 손대지 않고 중단합니다."
fi

if ! dump_toc_readable "${PRE_RESTORE_DUMP}"; then
    die "사전 백업의 목록을 읽을 수 없습니다. 파일은 남겨 둡니다(${PRE_RESTORE_DUMP}). 운영 DB를 손대지 않고 중단합니다."
fi
log "[3/4] 사전 백업 완료: ${PRE_RESTORE_DUMP}"

# --- ④ 교체 -------------------------------------------------------------------
log "[4/4] 운영 DB 교체를 시작합니다. 여기서부터는 되돌리려면 사전 백업이 필요합니다."

if ! drop_db "${PROD_DB}"; then
    die "운영 DB drop 실패. DB는 그대로입니다. 연결이 남아 있는지 확인하세요(backend, psql 세션)."
fi

if ! create_db "${PROD_DB}"; then
    log "ERROR: 운영 DB 생성 실패. **운영 DB가 없는 상태입니다.**"
    log "복구 절차:"
    log "  1) docker exec ${CONTAINER} sh -c 'createdb -U \"\$POSTGRES_USER\" -- ${PROD_DB}'"
    log "  2) $0 ${PRE_RESTORE_DUMP} --target-prod   # 사전 백업으로 되돌리기"
    exit 1
fi

if ! restore_into "${PROD_DB}" "${DUMP_FILE}"; then
    log "ERROR: 운영 DB 복원이 중간에 실패했습니다. **DB가 불완전한 상태입니다.**"
    log "복구 절차 — 사전 백업으로 되돌립니다:"
    log "  $0 ${PRE_RESTORE_DUMP} --target-prod"
    log "사전 백업 파일: ${PRE_RESTORE_DUMP}"
    log "backend를 다시 올리기 전에 반드시 되돌리세요."
    exit 1
fi

log "복원 완료."
log "  사전 백업(되돌릴 때 사용): ${PRE_RESTORE_DUMP}"
log "  backend를 다시 올리고 기동 로그와 Flyway 상태를 확인하세요(docs/07 운영 수동 배포 절차 3절)."
log "  docker compose --env-file .env -f infra/docker/docker-compose.prod.yml up -d backend"
