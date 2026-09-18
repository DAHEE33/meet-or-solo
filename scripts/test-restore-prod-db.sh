#!/usr/bin/env bash
#
# restore-prod-db.sh 안전장치 검증.
#
#   ./scripts/test-restore-prod-db.sh
#
# 실제 Docker도 PostgreSQL도 필요 없습니다. `docker`를 흉내 내는 mock을 PATH 앞에 두고
# 스크립트를 실행한 뒤, **어떤 명령이 실제로 호출됐는지**를 로그로 확인합니다.
#
# 이 테스트가 보는 것은 "무엇이 실행됐나"가 아니라 **"무엇이 실행되지 않았나"**입니다.
# 파괴적 명령(dropdb)이 나가면 안 되는 상황에서 나가지 않는지가 핵심입니다.

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TARGET="${SCRIPT_DIR}/restore-prod-db.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT

PASS=0
FAIL=0

PROD_DB_NAME="meet_or_solo_prod"

# ─────────────────────────────────────────────────────────────────────────────
# docker mock
# ─────────────────────────────────────────────────────────────────────────────
mkdir -p "${WORK}/bin"
cat > "${WORK}/bin/docker" <<'MOCK'
#!/usr/bin/env bash
# 호출된 인자를 통째로 기록한다. 테스트는 이 로그만 본다.
printf '%s\n' "$*" >> "${MOCK_LOG}"

args="$*"

case "${args}" in
    "ps --format {{.Names}}")
        printf '%s\n' ${MOCK_RUNNING:-}
        exit 0
        ;;
esac

# 운영 DB 이름 조회
case "${args}" in
    *'printf %s "$POSTGRES_DB"'*)
        printf '%s' "${MOCK_PROD_DB}"
        exit 0
        ;;
esac

# DB 목록 조회
case "${args}" in
    *'select datname from pg_database'*)
        printf '%s\n' ${MOCK_EXISTING_DBS:-}
        exit 0
        ;;
esac

# 덤프 목록(TOC) 확인 — pg_restore --list
case "${args}" in
    *'pg_restore --list'*)
        cat > /dev/null 2>&1 || true
        exit "${MOCK_TOC_RC:-0}"
        ;;
esac

# 사전 백업 — pg_dump
case "${args}" in
    *'pg_dump'*)
        if [ "${MOCK_PGDUMP_RC:-0}" -ne 0 ]; then
            exit "${MOCK_PGDUMP_RC}"
        fi
        printf 'PGDMP-fake-dump-content'
        exit 0
        ;;
esac

# 임시/운영 DB로 복원 — pg_restore -U
case "${args}" in
    *'pg_restore -U'*)
        cat > /dev/null 2>&1 || true
        exit "${MOCK_RESTORE_RC:-0}"
        ;;
esac

case "${args}" in
    *createdb*) exit "${MOCK_CREATEDB_RC:-0}" ;;
    *dropdb*)   exit "${MOCK_DROPDB_RC:-0}" ;;
    *psql*)     printf 'mock psql output\n'; exit 0 ;;
esac

exit 0
MOCK
chmod +x "${WORK}/bin/docker"

export PATH="${WORK}/bin:${PATH}"

# 가짜 덤프 파일 (내용은 mock이 판단하므로 비어 있지만 않으면 된다)
DUMP="${WORK}/fake.dump"
printf 'PGDMP-fake' > "${DUMP}"

# ─────────────────────────────────────────────────────────────────────────────
# 헬퍼
# ─────────────────────────────────────────────────────────────────────────────
run_case() {          # run_case <로그파일> <인자...>
    local logfile="$1"; shift
    export MOCK_LOG="${logfile}"
    : > "${MOCK_LOG}"
    "${TARGET}" "$@" > "${logfile}.out" 2>&1
    echo $?
}

check() {             # check <설명> <조건결과(0/1)>
    if [ "$2" -eq 0 ]; then
        printf '  ✔ %s\n' "$1"
        PASS=$((PASS + 1))
    else
        printf '  ✘ %s\n' "$1"
        FAIL=$((FAIL + 1))
    fi
}

# 로그에 dropdb 호출이 있는가
log_has_dropdb() { grep -q 'dropdb' "$1"; }
# 로그에 createdb 호출이 있는가
log_has_createdb() { grep -q 'createdb' "$1"; }
# 운영 DB 이름을 인자로 받은 dropdb 호출이 있는가 (가장 중요한 검사)
log_dropped_prod() { grep 'dropdb' "$1" | grep -qw "${PROD_DB_NAME}"; }
log_has_pgdump() { grep -q 'pg_dump' "$1"; }

base_env() {
    export MOCK_PROD_DB="${PROD_DB_NAME}"
    export MOCK_RUNNING="meet-or-solo-postgres-prod"
    export MOCK_EXISTING_DBS="postgres ${PROD_DB_NAME}"
    export MOCK_TOC_RC=0
    export MOCK_PGDUMP_RC=0
    export MOCK_RESTORE_RC=0
    export MOCK_CREATEDB_RC=0
    export MOCK_DROPDB_RC=0
    unset PROD_RESTORE_VERIFY_DB PROD_RESTORE_VERIFY_DB_PREFIX
    export PROD_BACKUP_DIR="${WORK}/backups"
}

echo "=== restore-prod-db.sh 안전장치 검증 ==="
echo

# ─────────────────────────────────────────────────────────────────────────────
# T1. 검증 DB 이름이 운영 DB와 같으면 중단하고, 아무 DB 명령도 실행하지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T1] 검증 DB 이름 == 운영 DB 이름 → 중단"
base_env
export PROD_RESTORE_VERIFY_DB="${PROD_DB_NAME}"
LOG="${WORK}/t1.log"
RC="$(run_case "${LOG}" "${DUMP}")"
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "dropdb를 호출하지 않는다" "$(log_has_dropdb "${LOG}" && echo 1 || echo 0)"
check "createdb를 호출하지 않는다" "$(log_has_createdb "${LOG}" && echo 1 || echo 0)"
check "운영 DB와 이름이 같다는 메시지를 출력한다" \
    "$(grep -q '운영 DB' "${LOG}.out" && echo 0 || echo 1)"
echo

# 접두사만 운영 DB와 같아도 걸러야 한다
echo "[T1-b] 접두사 == 운영 DB 이름 → 중단"
base_env
export PROD_RESTORE_VERIFY_DB_PREFIX="${PROD_DB_NAME}"
LOG="${WORK}/t1b.log"
RC="$(run_case "${LOG}" "${DUMP}")"
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "dropdb를 호출하지 않는다" "$(log_has_dropdb "${LOG}" && echo 1 || echo 0)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T2. 임시 DB 이름이 이미 존재하면 그 DB를 건드리지 않고 중단한다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T2] 임시 DB가 이미 존재 → 기존 DB 보호하며 중단"
base_env
export PROD_RESTORE_VERIFY_DB="someone_elses_db"
export MOCK_EXISTING_DBS="postgres ${PROD_DB_NAME} someone_elses_db"
LOG="${WORK}/t2.log"
RC="$(run_case "${LOG}" "${DUMP}")"
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "기존 DB에 dropdb를 호출하지 않는다" "$(log_has_dropdb "${LOG}" && echo 1 || echo 0)"
check "createdb를 호출하지 않는다" "$(log_has_createdb "${LOG}" && echo 1 || echo 0)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T3. 잘못된 백업 입력(TOC를 읽을 수 없음)이면 DB 명령이 나가지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T3] 덤프 TOC 읽기 실패 → DB 명령 미실행"
base_env
export MOCK_TOC_RC=1
LOG="${WORK}/t3.log"
RC="$(run_case "${LOG}" "${DUMP}")"
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "createdb를 호출하지 않는다" "$(log_has_createdb "${LOG}" && echo 1 || echo 0)"
check "dropdb를 호출하지 않는다" "$(log_has_dropdb "${LOG}" && echo 1 || echo 0)"
echo

echo "[T3-b] 빈 덤프 파일 → DB 명령 미실행"
base_env
EMPTY="${WORK}/empty.dump"; : > "${EMPTY}"
LOG="${WORK}/t3b.log"
RC="$(run_case "${LOG}" "${EMPTY}")"
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "createdb를 호출하지 않는다" "$(log_has_createdb "${LOG}" && echo 1 || echo 0)"
check "dropdb를 호출하지 않는다" "$(log_has_dropdb "${LOG}" && echo 1 || echo 0)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T4. 정상 검증 경로 — 이번 실행이 만든 DB만 정리하고 운영 DB는 건드리지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T4] 정상 검증 경로"
base_env
LOG="${WORK}/t4.log"
RC="$(run_case "${LOG}" "${DUMP}")"
check "정상 종료한다 (rc=${RC})" "$([ "${RC}" -eq 0 ] && echo 0 || echo 1)"
check "createdb를 1회 호출한다" \
    "$([ "$(grep -c 'createdb' "${LOG}")" -eq 1 ] && echo 0 || echo 1)"
check "dropdb를 1회 호출한다(정리)" \
    "$([ "$(grep -c 'dropdb' "${LOG}")" -eq 1 ] && echo 0 || echo 1)"
check "운영 DB에는 dropdb를 호출하지 않는다" "$(log_dropped_prod "${LOG}" && echo 1 || echo 0)"
CREATED_NAME="$(grep 'createdb' "${LOG}" | sed 's/.* //')"
DROPPED_NAME="$(grep 'dropdb' "${LOG}" | sed 's/.* //')"
check "만든 DB와 지운 DB가 같다 (${CREATED_NAME})" \
    "$([ "${CREATED_NAME}" = "${DROPPED_NAME}" ] && echo 0 || echo 1)"
check "임시 DB 이름이 실행별로 고유하다(타임스탬프 포함)" \
    "$(printf '%s' "${CREATED_NAME}" | grep -qE '_[0-9]{14}_[0-9]+$' && echo 0 || echo 1)"
echo

# 같은 조건으로 한 번 더 돌려 이름이 겹치지 않는지 본다
echo "[T4-b] 연속 실행 시 임시 DB 이름이 겹치지 않는다"
base_env
LOG2="${WORK}/t4b.log"
RC="$(run_case "${LOG2}" "${DUMP}")"
CREATED_NAME2="$(grep 'createdb' "${LOG2}" | sed 's/.* //')"
check "두 실행의 임시 DB 이름이 다르다" \
    "$([ "${CREATED_NAME}" != "${CREATED_NAME2}" ] && echo 0 || echo 1)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T5. 검증 모드에서 복원이 실패하면 임시 DB를 남기고 운영 DB는 건드리지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T5] 검증 복원 실패 → 임시 DB 보존, 운영 DB 미접촉"
base_env
export MOCK_RESTORE_RC=1
LOG="${WORK}/t5.log"
RC="$(run_case "${LOG}" "${DUMP}")"
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "운영 DB에는 dropdb를 호출하지 않는다" "$(log_dropped_prod "${LOG}" && echo 1 || echo 0)"
check "임시 DB를 지우지 않고 남긴다" "$(log_has_dropdb "${LOG}" && echo 1 || echo 0)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T6. 운영 복원 모드 — 임시 DB 복원 검증에 실패하면 운영 DB를 건드리지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T6] 운영 모드: 사전 복원 검증 실패 → 운영 DB 미접촉"
base_env
export MOCK_RESTORE_RC=1
export MOCK_LOG="${WORK}/t6.log"
: > "${MOCK_LOG}"
echo "RESTORE" | "${TARGET}" "${DUMP}" --target-prod > "${WORK}/t6.log.out" 2>&1
RC=$?
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "운영 DB에 dropdb를 호출하지 않는다" "$(log_dropped_prod "${WORK}/t6.log" && echo 1 || echo 0)"
check "사전 백업(pg_dump)까지 가지 않는다" "$(log_has_pgdump "${WORK}/t6.log" && echo 1 || echo 0)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T7. 운영 복원 모드 — 사전 백업이 실패하면 운영 DB를 건드리지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T7] 운영 모드: 사전 백업 실패 → 운영 DB 미접촉"
base_env
export MOCK_PGDUMP_RC=1
export MOCK_LOG="${WORK}/t7.log"
: > "${MOCK_LOG}"
echo "RESTORE" | "${TARGET}" "${DUMP}" --target-prod > "${WORK}/t7.log.out" 2>&1
RC=$?
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "사전 백업을 시도한다" "$(log_has_pgdump "${WORK}/t7.log" && echo 0 || echo 1)"
check "운영 DB에 dropdb를 호출하지 않는다" "$(log_dropped_prod "${WORK}/t7.log" && echo 1 || echo 0)"
check "사전 백업 실패 메시지를 출력한다" \
    "$(grep -q '사전 백업' "${WORK}/t7.log.out" && echo 0 || echo 1)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T8. 운영 복원 모드 — 확인 입력이 틀리면 아무 DB 명령도 실행하지 않는다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T8] 운영 모드: 확인 입력 불일치 → 중단"
base_env
export MOCK_LOG="${WORK}/t8.log"
: > "${MOCK_LOG}"
echo "yes" | "${TARGET}" "${DUMP}" --target-prod > "${WORK}/t8.log.out" 2>&1
RC=$?
check "0이 아닌 종료 코드로 중단한다 (rc=${RC})" "$([ "${RC}" -ne 0 ] && echo 0 || echo 1)"
check "dropdb를 호출하지 않는다" "$(log_has_dropdb "${WORK}/t8.log" && echo 1 || echo 0)"
check "createdb를 호출하지 않는다" "$(log_has_createdb "${WORK}/t8.log" && echo 1 || echo 0)"
echo

# ─────────────────────────────────────────────────────────────────────────────
# T9. 운영 복원 모드 — 모든 관문을 통과하면 사전 백업 뒤에 교체한다
# ─────────────────────────────────────────────────────────────────────────────
echo "[T9] 운영 모드: 정상 경로 — 사전 백업 후 교체"
base_env
export MOCK_LOG="${WORK}/t9.log"
: > "${MOCK_LOG}"
echo "RESTORE" | "${TARGET}" "${DUMP}" --target-prod > "${WORK}/t9.log.out" 2>&1
RC=$?
check "정상 종료한다 (rc=${RC})" "$([ "${RC}" -eq 0 ] && echo 0 || echo 1)"
check "사전 백업을 수행한다" "$(log_has_pgdump "${WORK}/t9.log" && echo 0 || echo 1)"
check "사전 백업 파일이 생성됐다" \
    "$(ls "${WORK}/backups"/pre-restore-*.dump >/dev/null 2>&1 && echo 0 || echo 1)"
check "운영 DB를 교체한다(dropdb 호출됨)" "$(log_dropped_prod "${WORK}/t9.log" && echo 0 || echo 1)"
PGDUMP_LINE="$(grep -n 'pg_dump' "${WORK}/t9.log" | head -1 | cut -d: -f1)"
PRODDROP_LINE="$(grep -n 'dropdb' "${WORK}/t9.log" | grep -w "${PROD_DB_NAME}" | head -1 | cut -d: -f1)"
check "사전 백업이 운영 DB drop보다 먼저다 (pg_dump=${PGDUMP_LINE}, dropdb=${PRODDROP_LINE})" \
    "$([ -n "${PGDUMP_LINE}" ] && [ -n "${PRODDROP_LINE}" ] && [ "${PGDUMP_LINE}" -lt "${PRODDROP_LINE}" ] && echo 0 || echo 1)"
echo

# ─────────────────────────────────────────────────────────────────────────────
echo "=============================="
printf '통과 %d건, 실패 %d건\n' "${PASS}" "${FAIL}"
echo "=============================="
[ "${FAIL}" -eq 0 ]
