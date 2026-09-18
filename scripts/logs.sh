#!/usr/bin/env bash
#
# 운영 컨테이너 로그 조회.
#
# 운영 VM에서 실행합니다.
#
#   cd /home/ubuntu/meet-or-solo-prod
#   ./scripts/logs.sh backend
#   ./scripts/logs.sh nginx
#   ./scripts/logs.sh postgres
#   ./scripts/logs.sh backend --no-follow
#
# ─────────────────────────────────────────────────────────────────────────────
# 설계 의도
#
#   - **조회 전용입니다.** 이 스크립트가 실행하는 docker 명령은 `info`, `ps`, `logs`
#     뿐입니다. 컨테이너를 만들거나 시작·재시작·중지하거나 설정을 바꾸는 명령은
#     들어 있지 않습니다.
#
#   - 로그 **저장** 방식은 건드리지 않습니다. 운영 로그는 compose의 json-file 드라이버가
#     `max-size 10m`, `max-file 3`으로 보관합니다(docs/07 운영 수동 배포 절차 10절).
#     별도 logs/ 폴더나 파일 로깅 설정을 쓰지 않습니다. 이 스크립트는 그 저장된 로그를
#     읽기만 합니다.
#
#   - Ctrl+C는 **조회만** 끝냅니다. `docker logs -f`는 컨테이너 밖에서 로그 스트림을
#     읽는 클라이언트라서, 중단해도 컨테이너 안의 프로세스에는 아무 신호가 가지 않습니다.
#     서비스는 그대로 돌아갑니다.
#
#   - 실패 원인을 구분해서 알려줍니다. "로그가 안 보인다"의 원인은 보통 셋 중 하나인데
#     (docker에 접근이 안 됨 / 컨테이너를 아직 안 만듦 / 만들었지만 멈춤) 증상이 비슷해
#     구분이 안 되면 엉뚱한 곳을 고치게 됩니다.
#
# 종료 코드
#   0    정상
#   1    사용법 오류
#   2    docker 접근 실패 (미설치 또는 데몬에 접근 불가)
#   3    컨테이너 미생성
#   130  Ctrl+C로 조회 종료
# ─────────────────────────────────────────────────────────────────────────────

set -uo pipefail

TAIL_LINES="${PROD_LOGS_TAIL:-100}"

usage() {
    cat <<'USAGE'
사용법: ./scripts/logs.sh <서비스> [--no-follow]

  서비스 (셋 중 하나)
    backend     meet-or-solo-backend-prod
    nginx       meet-or-solo-nginx-prod
    postgres    meet-or-solo-postgres-prod

  옵션
    --no-follow   최근 로그만 출력하고 바로 끝냅니다.
                  (생략하면 최근 로그를 보여준 뒤 실시간으로 계속 따라갑니다)

  예시
    ./scripts/logs.sh backend               # 실시간 추적
    ./scripts/logs.sh backend --no-follow   # 최근 로그만
    PROD_LOGS_TAIL=300 ./scripts/logs.sh nginx

  기본으로 최근 100줄을 타임스탬프와 함께 보여줍니다.
  줄 수는 PROD_LOGS_TAIL 환경변수로 바꿉니다.
  실시간 추적 중에는 Ctrl+C로 빠져나옵니다. 서비스는 영향을 받지 않습니다.
USAGE
}

log() {
    printf '[logs] %s\n' "$1" >&2
}

# Ctrl+C는 조회만 끝낸다. 컨테이너에는 신호가 가지 않는다.
trap 'printf "\n"; log "조회를 종료합니다. 컨테이너와 서비스는 그대로입니다."; exit 130' INT

# ─────────────────────────────────────────────────────────────────────────────
# 인자 확인
# ─────────────────────────────────────────────────────────────────────────────
if [ "$#" -eq 0 ]; then
    log "서비스 이름이 필요합니다."
    echo >&2
    usage >&2
    exit 1
fi

SERVICE="$1"
shift

FOLLOW=1
while [ "$#" -gt 0 ]; do
    case "$1" in
        --no-follow)
            FOLLOW=0
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            log "알 수 없는 옵션입니다: $1"
            echo >&2
            usage >&2
            exit 1
            ;;
    esac
    shift
done

case "${SERVICE}" in
    backend|nginx|postgres)
        CONTAINER="meet-or-solo-${SERVICE}-prod"
        ;;
    -h|--help)
        usage
        exit 0
        ;;
    *)
        log "알 수 없는 서비스입니다: ${SERVICE}"
        log "backend, nginx, postgres 중 하나여야 합니다."
        echo >&2
        usage >&2
        exit 1
        ;;
esac

# ─────────────────────────────────────────────────────────────────────────────
# docker 접근 확인 — 컨테이너 미생성과 구분해서 알려준다
# ─────────────────────────────────────────────────────────────────────────────
if ! command -v docker >/dev/null 2>&1; then
    log "ERROR: docker 명령을 찾을 수 없습니다."
    log "  이 스크립트는 운영 VM에서 실행해야 합니다."
    exit 2
fi

if ! docker info >/dev/null 2>&1; then
    log "ERROR: Docker 데몬에 접근할 수 없습니다."
    log "  원인은 보통 둘 중 하나입니다."
    log "    1) 현재 사용자가 docker 그룹에 없다  ->  id 로 확인"
    log "    2) Docker 데몬이 멈췄다              ->  systemctl status docker 로 확인"
    log "  (이 스크립트는 데몬을 시작하지 않습니다)"
    exit 2
fi

# ─────────────────────────────────────────────────────────────────────────────
# 컨테이너 상태 확인 — 미생성 / 중지 / 실행 중을 나눠서 안내한다
# ─────────────────────────────────────────────────────────────────────────────
if ! docker ps -a --format '{{.Names}}' | tr -d '\r' | grep -qx "${CONTAINER}"; then
    log "ERROR: 컨테이너 ${CONTAINER} 가 아직 만들어지지 않았습니다."
    log "  운영 배포를 아직 하지 않았다면 정상입니다. 배포 절차는"
    log "  docs/07_DEPLOYMENT.md의 '운영 수동 배포 절차'를 참고하세요."
    log "  (이 스크립트는 컨테이너를 만들지 않습니다)"
    exit 3
fi

RUNNING=0
if docker ps --format '{{.Names}}' | tr -d '\r' | grep -qx "${CONTAINER}"; then
    RUNNING=1
fi

if [ "${RUNNING}" -eq 0 ]; then
    log "알림: ${CONTAINER} 가 중지 상태입니다. 남아 있는 로그만 보여줍니다."
    if [ "${FOLLOW}" -eq 1 ]; then
        log "  중지된 컨테이너는 새 로그가 생기지 않으므로 실시간 추적은 바로 끝납니다."
    fi
    log "  (이 스크립트는 컨테이너를 시작하지 않습니다)"
fi

# ─────────────────────────────────────────────────────────────────────────────
# 조회
# ─────────────────────────────────────────────────────────────────────────────
if [ "${FOLLOW}" -eq 1 ] && [ "${RUNNING}" -eq 1 ]; then
    log "${CONTAINER} 최근 ${TAIL_LINES}줄 + 실시간 추적 (Ctrl+C로 종료)"
else
    log "${CONTAINER} 최근 ${TAIL_LINES}줄"
fi

# exec를 쓰지 않는다. exec로 셸을 대체하면 위에서 건 INT trap이 사라져서
# Ctrl+C를 눌렀을 때 "서비스는 그대로입니다" 안내를 띄울 수 없다.
if [ "${FOLLOW}" -eq 1 ]; then
    docker logs --timestamps --tail "${TAIL_LINES}" --follow "${CONTAINER}"
else
    docker logs --timestamps --tail "${TAIL_LINES}" "${CONTAINER}"
fi
exit $?
