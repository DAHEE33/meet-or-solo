# 관리자 만남 장소(festival_meeting_points) 관리 설계

## 1. 배경과 범위

`festival_meeting_points`는 `[10-매칭 24차]`에서 Backend 관리 API(등록/수정/활성-비활성/목록 조회)까지
구현됐지만, 당시 "Admin UI는 현재 mock dashboard 범위를 과도하게 확장하므로 제외"하기로 결정해
Frontend 화면이 없다. 그 결과 지금은 운영자가 SQL `INSERT`로 직접 좌표를 넣는 방식으로만
장소를 관리하고 있다.

이번 설계는 두 가지를 다룬다.

1. **관리자 화면 상단 네비게이션 정리** — `/admin`, `/admin/reports`, `/admin/members`가 각자
   페이지 안에서 임시로 만든 되돌아가기 링크만 가지고 있고 공통 메뉴바가 없다. 신규 화면을
   추가하기 전에 공통 네비게이션을 만들어 메뉴를 일관되게 정리한다.
2. **만남 장소 관리자 화면 신규 추가** — 기존 Backend API(`/api/admin/festivals/{festivalId}/meeting-points`)를
   그대로 사용하는 CRUD 화면을 추가한다.
3. **만남 장소가 하나도 없는 축제 자동 백필** — 관광공사 축제 API 스케줄러(`FestivalSyncScheduler` →
   `FestivalSyncService`)가 **매 실행마다** "현재 `ACTIVE`인 축제인데 `festival_meeting_points`
   행이 단 하나도 없는" 축제를 전부 스캔해, 축제 좌표(`festivals.map_x/map_y`)를 그대로 써서 `ACTIVE`
   상태의 기본 장소 1건을 자동 생성한다. **신규로 막 들어온 축제뿐 아니라, 이번 기능이 배포되기 전부터
   이미 존재하던(만남 장소가 한 번도 등록되지 않은) 축제도 다음 스케줄 실행에서 동일하게 채워진다** —
   즉 "새 축제 최초 시딩"은 이 백필 규칙의 특수 케이스일 뿐, 별도 로직이 아니다. 이후 관리자가 신규
   화면에서 좌표/이름/주소를 조정하거나 장소를 추가한다.

이번 설계는 문서 정리까지만이며, 실제 코드 변경은 사용자가 "진행해줘"로 승인한 뒤 별도로 진행한다
(`AGENTS.md`, `docs/08_AI_WORKING_RULES.md` 승인 규칙).

## 2. 현재 상태 재확인

### 2.1 Backend — 이미 구현된 부분 (변경 없음)

- `V15__add_festival_meeting_points.sql`: `festival_meeting_points` 테이블. `festival_id`,
  `kakao_place_id`(축제별 unique), `name`, `address`, `map_x`/`map_y`(경도/위도, `NOT NULL`),
  `status`(`ACTIVE`/`INACTIVE`), `assignment_order`, 생성/수정 시각. `status='ACTIVE'` 부분
  index로 활성 후보를 `assignment_order, id` 순으로 조회한다.
- `AdminFestivalMeetingPointController` (`/api/admin/festivals/{festivalId}/meeting-points`):
  `GET`(목록), `POST`(등록, 항상 `INACTIVE`로 생성), `PUT /{pointId}`(수정), `PATCH /{pointId}/status`
  (활성/비활성 전환). 삭제(hard delete) API는 없다.
- `FestivalMeetingPointAdminService.requireAdmin()`이 매 호출마다 `members.role == 'ADMIN'`을 확인한다.
- 매칭 확정 시 `assignedGroupCount % candidateCount`로 `ACTIVE` 후보를 순환 배정하고(`[10-매칭 24차]`),
  `ACTIVE` 장소가 하나도 없는 축제는 `MatchPoolEntryService`가 `MATCHING_MEETING_POINT_NOT_READY`로
  풀 진입 자체를 막는다. 즉 **신규 축제는 관리자가 수동으로 첫 장소를 등록·활성화하기 전까지 매칭이
  열리지 않는다** — 이번 설계의 3번(자동 시딩)이 필요한 근본 이유다.

### 2.2 Frontend — 없는 부분

- `AdminDashboardPage`, `AdminReportsPage`, `AdminMembersPage` 세 화면이 각자 다른 방식으로
  되돌아가기 링크를 하드코딩하고 있고(`AdminReportsPage`는 대시보드 링크만, `AdminMembersPage`는
  대시보드+신고 관리 링크만), 세 화면을 아우르는 공통 네비게이션이 없다.
- 만남 장소를 관리하는 화면, API 클라이언트(`adminMeetingPoints.ts`), hook이 전혀 없다.

### 2.3 축제 동기화 흐름 (자동 시딩 훅 지점)

`FestivalSyncScheduler.synchronizeFestivals()`(`app.festival.sync.enabled=true`일 때 주기 실행)
→ `FestivalSyncService.synchronizeFestivals()`(관광공사 API 페이지 조회·매핑) →
`FestivalSyncWriter.upsert(syncData, syncScope)`.

`upsert()` 내부에서 `contentId` 기준으로 기존 축제를 조회해, 없으면 `Festival.create(...)`로
**신규 생성**하고 있으면 `festival.synchronize(...)`로 **갱신**한다. 신규/갱신 여부를
`insertedCount`/`updatedCount`로만 집계하고 있어, "이번에 새로 생성된 `Festival` 엔티티 목록"을
별도로 얻으려면 현재 반복문에 약간의 수정이 필요하다(3장에서 구체화).

`Festival.mapX`/`Festival.mapY`는 관광공사 API의 `mapx`/`mapy`를 그대로 저장한 값으로, 관례상
`mapX`=경도, `mapY`=위도다. `FestivalMeetingPointUpsertRequest.longitude()`/`latitude()`가
`FestivalMeetingPoint.mapX`/`mapY`에 매핑되는 것과 같은 축이므로 좌표 변환 없이 그대로 재사용할 수 있다.

## 3. 설계 — 만남 장소 0건 축제 자동 백필 (Backend)

### 3.1 트리거 조건 — "신규"가 아니라 "0건"이 기준

이전 초안은 "이번 sync에서 새로 INSERT된 축제"만 대상으로 삼았지만, 그렇게 하면 **이 기능을
배포하기 전부터 이미 존재하던, 만남 장소가 하나도 없는 축제는 영원히 채워지지 않는다.** 사용자
요구사항대로 기준을 다음으로 바꾼다.

> 지금 `ACTIVE` 상태인 축제인데 `festival_meeting_points`에 해당 `festival_id` 행이 **단 하나도
> 없으면**, 스케줄러가 실행될 때마다 이를 확인해서 최소 1건을 자동으로 채운다.

- 판단 기준은 "행이 0건"이다 — **"`ACTIVE` 상태인 행이 0건"이 아니다.** 관리자가 이미 장소를
  등록해뒀다면(현재 전부 `INACTIVE`로 꺼둔 상태라도) 그 축제는 "관리자가 이미 손댄 축제"로 보고
  두 번 다시 자동 시딩 대상에 넣지 않는다. 그래야 관리자가 점검 등의 이유로 일시적으로 모든 장소를
  비활성화해도, 다음날 스케줄러가 마음대로 새 `ACTIVE` 장소를 또 만들어버리는 일이 없다. 즉 이
  백필은 "한 번도 설정된 적 없는 축제"를 위한 안전망이고, "관리자가 다 꺼둔 축제"를 대신 복구해주는
  기능이 아니다(이 차이는 3.5절에서 다시 정리한다).
- 대상은 `festival.status = 'ACTIVE'`인 축제만이다. `ENDED`/`HIDDEN`/`INACTIVE`(비활성 시즌)
  축제는 체크인 자체가 막혀 있어(`FestivalCheckinService`가 `FestivalStatus.ACTIVE`만 허용) 만남
  장소가 필요 없다. 나중에 다시 `ACTIVE`로 바뀌는 축제는 그 시점 이후 스케줄러 실행에서 여전히
  0건이면 자연스럽게 채워진다.
- `mapX` 또는 `mapY`가 `null`인 축제는 이번 실행에서 건너뛰고 `WARN` 로그만 남긴다. 좌표가 계속
  없으면 스케줄러가 돌 때마다 같은 로그가 반복된다 — 의도된 동작이다(관광공사 데이터 결손을 계속
  드러내야 관리자가 인지하고 수동으로 채울 수 있다).
- 같은 축제에 동시에 두 번 INSERT를 시도하는 경쟁 상태는, 현재 `@Scheduled(fixedDelay=...)`가
  이전 실행이 끝난 뒤에만 다음 실행을 시작하는 단일 인스턴스 전제에서는 발생하지 않는다. 앱을
  다중 인스턴스로 띄우는 시점이 오면 별도로 분산 스케줄링(예: shedlock)을 검토해야 한다는 점만
  가정으로 남겨둔다.

### 3.2 구현 지점 — 별도 서비스로 분리, `FestivalSyncWriter`는 건드리지 않음

"신규 축제만" 기준이었을 때는 `FestivalSyncWriter.upsert()`의 반복문 안에서 방금 만든 `Festival`
목록을 넘겨받아야 했지만, 기준이 "0건 스캔"으로 바뀌면서 굳이 `upsert()` 내부 로직을 바꿀 필요가
없어졌다 — 신규 컴포넌트가 스스로 DB를 조회해서 대상을 찾기 때문이다. `FestivalSyncWriter`는
축제/이미지 동기화라는 기존 책임만 유지한다.

신규 컴포넌트 `FestivalMeetingPointBackfillService`(가칭, `domain/festival/service` 패키지)를
`FestivalSyncService.synchronizeFestivals()`에서 `writer.upsert(...)` 다음 순서로 호출한다.

```java
// FestivalSyncService.synchronizeFestivals() 안, writer.upsert(...) 다음
FestivalSyncWriteResult writeResult = writer.upsert(uniqueSyncData.values(), syncScope);
int seededMeetingPointCount = meetingPointBackfillService.seedMissingDefaultPoints();
return new FestivalSyncResult(..., seededMeetingPointCount, ...);
```

```java
@Service
public class FestivalMeetingPointBackfillService {
    private final FestivalRepository festivals;
    private final FestivalMeetingPointRepository points;

    @Transactional
    public int seedMissingDefaultPoints() {
        List<Festival> targets = festivals.findActiveFestivalsWithoutMeetingPoint(FestivalStatus.ACTIVE);
        int seeded = 0;
        for (Festival festival : targets) {
            if (festival.getMapX() == null || festival.getMapY() == null) {
                log.warn("만남 장소 자동 백필 skip: 좌표 없음. festivalId={}, contentId={}",
                        festival.getId(), festival.getContentId());
                continue;
            }
            FestivalMeetingPoint point = FestivalMeetingPoint.inactive(
                    festival.getId(),
                    "AUTO-" + festival.getContentId(),
                    defaultName(festival),
                    defaultAddress(festival),
                    festival.getMapX(),
                    festival.getMapY(),
                    0 // 자동 시딩 장소는 assignment_order=0으로 고정
            );
            point.changeStatus(FestivalMeetingPointStatus.ACTIVE); // 즉시 매칭에 쓸 수 있어야 함
            points.save(point);
            seeded++;
        }
        return seeded;
    }
}
```

- `FestivalRepository`에 신규 조회 메서드를 추가한다(기존 `findAllVisibleWithCoordinates` 등과
  같은 JPQL 스타일).

  ```java
  @Query("""
          select festival
          from Festival festival
          where festival.status = :status
            and not exists (
                select 1 from FestivalMeetingPoint point
                where point.festivalId = festival.id
            )
          """)
  List<Festival> findActiveFestivalsWithoutMeetingPoint(@Param("status") FestivalStatus status);
  ```

  좌표 유무는 쿼리 조건에 넣지 않고 위 서비스의 반복문에서 확인한다 — 그래야 "좌표 없어서 못 채운
  축제"를 매 실행마다 `WARN`으로 계속 드러낼 수 있다(쿼리 조건에 넣으면 그 축제는 조용히 계속
  빠지기만 하고 아무 로그도 남지 않는다).
- `FestivalMeetingPoint.inactive(...)`를 재사용해 만들고 곧바로 `changeStatus(ACTIVE)`를 호출하는
  방식을 제안한다 — 엔티티에 새 팩토리 메서드(`seeded(...)`)를 추가하는 대신 기존 메서드 조합으로
  충분하다. 다만 "왜 `inactive()`로 만들고 바로 `ACTIVE`로 바꾸는가"가 코드만 보면 어색할 수 있어,
  실제 구현 시 `FestivalMeetingPoint.inactive(...)` 자리에 `FestivalMeetingPoint.seededActive(...)`
  같은 의도가 드러나는 별도 팩토리를 만드는 대안도 있다. **구현 단계에서 최종 선택**(둘 다 동작은 동일).
- 별도 멱등성 가드(`existsByFestivalId`)는 필요하지 않다 — 조회 쿼리 자체가 "이 축제에 행이
  0건"인 축제만 반환하므로, 한 실행 안에서 같은 축제를 두 번 처리할 여지가 없다.

### 3.3 placeholder 값 규칙

| 필드 | 값 | 이유 |
|---|---|---|
| `kakaoPlaceId` | `"AUTO-" + festival.contentId` | `festival_id + kakao_place_id` unique 제약을 만족하고, 실제 카카오 장소 검색으로 등록된 값이 아님을 접두어로 구분해 관리자 화면에서 "자동 생성됨" 표시에 활용할 수 있다. `contentId`는 관광공사 축제 고유 ID라 축제당 항상 유일하다. |
| `name` | `festival.title + " (자동 등록 기본 위치)"` | 관리자가 목록에서 자동 생성 항목을 바로 인지하도록 표시를 남긴다. 이름은 이후 관리자가 언제든 `PUT`으로 바꿀 수 있다. |
| `address` | `festival.address`가 비어있지 않으면 그대로 사용, 없으면 `"주소 미확인 (관리자 확인 필요)"` | `chk_festival_meeting_points_address`가 빈 문자열을 거부하므로 fallback 문자열이 필요하다. |
| `mapX`/`mapY` | `festival.mapX`/`festival.mapY` 그대로 | 이번 요구사항의 핵심 — "최초에는 축제 위치로 잡는다." |
| `status` | `ACTIVE` | `ACTIVE` 후보가 없으면 매칭 자체가 열리지 않으므로(2.1절), 시딩의 존재 의미가 없어진다. |
| `assignmentOrder` | `0` | 관리자가 이후 추가하는 장소는 기존 샘플 데이터 관례(`10`, `20`, ...)를 따르도록 화면 기본값을 `10` 단위로 제안한다(4.2절). |

### 3.4 트랜잭션·실패 처리

- 백필은 `writer.upsert(...)`가 끝난 뒤 **별도 트랜잭션**(`FestivalMeetingPointBackfillService.seedMissingDefaultPoints()`
  자체의 `@Transactional`)으로 처리한다. 축제/이미지 upsert와 한 트랜잭션으로 묶지 않는 이유는,
  백필 대상이 "이번 sync로 새로 들어온 축제"에 한정되지 않고 DB에 이미 있던 축제 전체를 스캔하는
  독립적인 스텝이기 때문이다 — 백필이 실패해도 방금 끝난 축제/이미지 upsert 커밋은 그대로 유지돼야
  한다.
- `FestivalSyncService.synchronizeFestivals()`가 백필 호출을 `try/catch`로 감싸 실패해도 축제
  동기화 자체(반환값, 로그)는 정상 성공으로 처리하고 백필 실패만 별도로 `WARN` 로그를 남긴다. 다음
  스케줄 실행에서 같은 축제가 여전히 0건이면 다시 시도되므로, 한 번의 백필 실패가 영구적인 문제로
  남지 않는다.
- `festival.getMapX()`/`getMapY()`가 이미 `-180~180`/`-90~90` 범위인 관광공사 응답만 통과하도록
  매핑 단계(`FestivalSyncMapper`)에서 걸러진다면 실질적으로 `festival_meeting_points`의 좌표 CHECK
  위반은 발생하지 않는다 — 구현 시 `FestivalSyncMapper`의 기존 좌표 검증 로직을 확인해 이 가정이
  맞는지 재확인한다.
- `FestivalSyncResult`에 `seededMeetingPointCount` 필드를 추가해 `FestivalSyncScheduler`의 성공
  로그(`log.info(...)`)에 노출한다. 운영 중 "이번 실행에서 만남 장소가 없던 축제 중 몇 건에 기본
  장소가 자동 생성됐는지"를 로그로 바로 확인할 수 있어야 한다. `FestivalSyncWriteResult`는 이
  필드를 몰라도 되므로 변경하지 않는다(백필 카운트는 `FestivalSyncService`가 직접 결과에 얹는다).

### 3.5 기존 정책과의 관계 재확인

- `[10-매칭 24차]` 정책 문서(`docs/11_DATABASE_DESIGN.md` 4장 근처)의 "관리 API는 `ADMIN` role만
  등록·수정·활성/비활성 허용"은 그대로 유지한다. 자동 백필은 스케줄러 내부 시스템 호출이라 관리자
  인증 경로를 타지 않으며, `FestivalMeetingPointAdminService`를 거치지 않고 리포지토리를 직접
  사용한다(관리자 권한 검사는 사람이 API를 호출할 때만 의미가 있다).
- 관리자가 이후 자동 생성된 장소를 비활성화하거나 좌표를 옮기는 것은 완전히 허용된다 — 이 설계는
  "행이 0건인 축제에 1건을 넣어준다"까지이며, 그 이후 생애주기는 기존 관리자 CRUD API가 그대로
  담당한다.
- **자동 복구(auto-heal)와는 다르다.** 관리자가 자동 생성된 장소를 포함해 어떤 장소라도 하나 이상
  등록해둔 축제는 —그 상태가 전부 `INACTIVE`라도— 더 이상 "0건"이 아니므로 백필 대상에서 영구히
  빠진다. 즉 관리자가 의도적으로 모든 장소를 비활성화해 매칭을 잠깐 막아둔 경우, 다음 스케줄러
  실행이 이를 대신 복구해 다시 `ACTIVE` 장소를 만들어주지 않는다. 이는 "관리자가 조정한 값은
  스케줄러가 절대 덮어쓰지 않는다"는 원칙을 지키기 위한 의도된 동작이며, 3.1절의 판단 기준(행 개수
  0건, `ACTIVE` 개수 0건이 아님)이 바로 이 구분을 만든다.
- 따라서 "관리자가 마지막 `ACTIVE` 장소를 비활성화하면 매칭이 막힌다"는 운영 리스크는 여전히
  남아있고, 이번 백필 기능으로 해결되는 문제가 아니다. 4.2절 화면에서 "마지막 활성 장소 비활성화"
  시 경고 문구를 넣는 것을 권장한다(강제 차단은 Backend API 계약을 벗어나므로 이번 범위에서는 UI
  경고로만 완화).

## 4. 설계 — 관리자 화면

### 4.1 공통 상단 네비게이션

신규 공유 컴포넌트 `frontend/src/components/admin/AdminNav.tsx`를 만들어 관리자 화면 4곳
(`AdminDashboardPage`, `AdminReportsPage`, `AdminMembersPage`, 신규 만남 장소 관리 화면)이 모두
같은 헤더를 쓰도록 정리한다.

- 메뉴 항목(고정 4개, 데이터 기반 아님): 대시보드(`/admin`), 신고 관리(`/admin/reports`),
  회원 관리(`/admin/members`), 만남 장소 관리(`/admin/meeting-points`, 신규).
- `useLocation()`으로 현재 경로와 일치하는 메뉴에 active 스타일(굵게/coral 밑줄 등, 기존
  `AdminMembersPage`가 이미 쓰는 `text-coral` 강조 톤 재사용)을 준다.
- 각 페이지의 `<header>` 안 좌측 타이틀은 페이지별로 유지하고, `AdminNav`는 헤더 우측 또는 헤더
  하단에 얇은 탭 바 형태로 넣는다(기존 `border-b border-line bg-white` 헤더 톤 유지).
- `AdminDashboardPage`의 기존 카드형 바로가기(신고 검토, 회원 조회·제재)는 유지해도 되고 제거해도
  되는데, 네비게이션이 생기면 카드와 메뉴가 중복 안내가 되므로 **카드는 제거하고 통계 섹션만
  남기는 쪽을 권장**한다(결정은 구현 승인 시 확정).
- 이 변경은 세 기존 페이지의 헤더 JSX를 수정하는 작업이라 "Frontend 화면 수정"에 해당하며,
  파일 안전 규칙상 각 페이지의 기존 내용을 먼저 확인한 뒤 헤더 부분만 교체한다.

### 4.2 만남 장소 관리 화면 (`/admin/meeting-points`)

신규 라우트를 `App.tsx`에 `AdminRoute`로 감싸 추가한다(기존 3개 라우트와 동일한 패턴).

**화면 구성**

1. **축제 선택** — `GET /api/admin/festivals?keyword=`(`AdminFestivalController` →
   `FestivalAdminQueryService`, `AdminAuthorizationService.requireAdmin`으로 ADMIN 권한 확인)를
   admin 전용으로 신설해 키워드 검색 + 선택 UI를 만든다. 공개 `GET /api/festivals`
   (`FestivalController.getFestivals`)는 일반 사용자 화면을 위해 `festival.eventEndDate >= 오늘`
   조건을 항상 걸어 종료된 축제를 숨기므로(`FestivalRepository.findVisibleFestivals`), 처음에는
   이를 그대로 재사용했으나 관리자가 방금 끝난 축제의 만남 장소를 조회·수정할 수 없는 문제가 있어
   별도 엔드포인트로 분리했다(`FestivalRepository.findForAdmin`). 대상 상태는 `ACTIVE`/`ENDED`만
   포함하고, 운영자가 숨긴 `HIDDEN`과 동기화상 비활성 시즌인 `INACTIVE`는 제외한다. 결과는
   `eventStartDate`/`eventEndDate`/`status`를 함께 내려주고, frontend가
   `resolveDisplayStatus`/`groupFestivalsByDisplayStatus`(`utils/festival.ts`)로 **진행 중/진행
   예정/마감** 3개 그룹으로 나눠 보여준다 — 검색어 없이 진입해도 항상 이 3그룹이 채워진다. 마감된
   축제도 장소 등록/수정/활성화를 계속 허용한다(체크인 자체가 `FestivalCheckinService`에서
   `ACTIVE`만 허용되므로 매칭에는 영향이 없다).
2. **선택한 축제의 장소 목록** — `GET /api/admin/festivals/{festivalId}/meeting-points` 결과를
   `assignmentOrder` 순 테이블/카드로 표시한다. 각 행에 이름, 주소, 좌표, 상태 배지(`ACTIVE`=teal,
   `INACTIVE`=회색), 배정 순서, `kakaoPlaceId`가 `AUTO-`로 시작하면 "자동 생성" 태그를 보여준다.
3. **등록/수정 폼** — `FestivalMeetingPointUpsertRequest` 그대로 매핑한 폼(이름, 주소, 경도,
   위도, 배정 순서, 카카오 장소 ID). 신규 등록은 기존 API 계약대로 항상 `INACTIVE`로 생성되므로,
   등록 직후 "활성화" 버튼을 바로 눌러야 함을 안내 문구로 표시한다.
4. **상태 토글** — `PATCH /{pointId}/status`를 호출하는 활성/비활성 버튼. 마지막 `ACTIVE` 장소를
   비활성화하려는 경우 3.5절에서 언급한 경고 confirm을 띄운다(클라이언트 판단만으로, 서버 차단은 없음).
5. **좌표 입력 보조** — 위도/경도 숫자 입력은 fallback으로 남기고, 그 위에 카카오맵 기반 보조
   UI 두 가지를 둔다. Kakao **Local REST API**(서버 전용 키, 매칭 엔진의 후보 검색용으로 이미
   계획된 것)가 아니라, 이미 로드하는 Kakao Maps **JS SDK의 `services` 라이브러리**를 쓴다 —
   추가 키·백엔드 변경이 필요 없다(`&libraries=services`만 SDK 로드 URL에 추가).
   - `KakaoPlaceSearch`(`components/admin/KakaoPlaceSearch.tsx`) — `Places.keywordSearch`로
     이름/주소로 검색해 이름·주소·좌표·`kakaoPlaceId`를 폼에 채운다.
   - `KakaoCoordinatePicker`(`components/admin/KakaoCoordinatePicker.tsx`) — 검색으로 채운
     좌표를 지도로 보여주고, 클릭하면 그 지점으로 좌표를 옮긴다. 신규 등록의 초기 중심점은
     선택된 축제의 좌표(`AdminFestivalSummaryResponse.mapX/mapY`)를 쓴다 — 없으면 춘천을
     기본값으로 쓴다.
   - 검색/지도가 실패하거나 원하는 결과가 없어도 숫자 입력 필드가 그대로 있어 등록이
     막히지 않는다.

**Frontend 파일 계획**

- `frontend/src/api/adminMeetingPoints.ts` — `adminReports.ts`와 동일한 `apiClient<T>()` 패턴으로
  `list(festivalId)`, `create(festivalId, body)`, `update(festivalId, pointId, body)`,
  `changeStatus(festivalId, pointId, status)`를 감싼다. 응답 타입은
  `FestivalMeetingPointResponse`를 그대로 옮긴 TypeScript 타입으로 정의한다.
- `frontend/src/api/adminFestivals.ts` — `GET /api/admin/festivals?keyword=`를 감싼
  `adminFestivalsApi.search(keyword?)`. 공개 `festivalsApi.getList`와 별도인 이유는 위 4.2절 참고.
- `frontend/src/hooks/useAdminMeetingPoints.ts` — `useAdminMembers.ts`/`useAdminReports.ts`와
  같은 결로 상태(`LOADING`/`READY`/`ERROR`), 선택된 축제 변경, 등록/수정/상태변경 요청과 in-flight
  가드·`AbortController`를 관리한다.
- `frontend/src/pages/AdminMeetingPointsPage.tsx` — 화면 본체.
- `frontend/src/components/admin/KakaoPlaceSearch.tsx`, `KakaoCoordinatePicker.tsx` — 5번
  항목의 카카오맵 검색/좌표 선택 보조 UI. `components/matching/KakaoMeetingPointMap.tsx`의
  SDK 로더(`loadKakaoMaps`)를 그대로 재사용한다.
- 기존 `useAdminMembers.test.ts`/`useAdminReports.test.ts`와 대응하는 신규 테스트 파일들.

## 5. Backend 구현 계획 (요약)

1. `FestivalRepository`에 `findActiveFestivalsWithoutMeetingPoint(FestivalStatus status)` 추가(3.2절).
2. 신규 `FestivalMeetingPointBackfillService` 추가(3.2절) — `FestivalRepository`,
   `FestivalMeetingPointRepository`에만 의존.
3. `FestivalSyncService.synchronizeFestivals()`가 `writer.upsert(...)` 다음 단계로 백필 서비스를
   호출하고, 실패해도 축제 동기화 자체는 계속 성공 처리하도록 `try/catch`로 감싼다(3.4절).
   `FestivalSyncWriter`는 변경하지 않는다.
4. `FestivalSyncResult`에 `seededMeetingPointCount` 필드 추가, `FestivalSyncScheduler` 로그 문구 갱신.
   `FestivalSyncWriteResult`는 변경하지 않는다.
5. Migration 추가 없음 — 기존 `V15` 테이블/제약을 그대로 사용한다.
6. `AdminFestivalMeetingPointController`/`FestivalMeetingPointAdminService`는 변경하지 않는다
   (Frontend가 기존 계약을 그대로 소비).

## 6. Frontend 구현 계획 (요약)

1. `components/admin/AdminNav.tsx` 신규 + 기존 세 페이지 헤더에 적용.
2. `api/adminMeetingPoints.ts`, `hooks/useAdminMeetingPoints.ts`, `pages/AdminMeetingPointsPage.tsx` 신규.
3. `App.tsx`에 `/admin/meeting-points` 라우트 추가(`AdminRoute`로 감싸기).
4. `AdminDashboardPage`의 기존 바로가기 카드 정리(4.1절 권장안 확정 후 반영).

## 7. 이번 범위에서 제외 (후속 과제)

- 만남 장소 hard delete API — 현재는 `ACTIVE`/`INACTIVE` 전환만 가능하고 행 삭제가 없다. 잘못
  등록된 자동 시딩 값을 지우고 싶은 경우 `PUT`으로 값을 고치는 방식만 가능하다.
- 축제별 여러 활성 장소에 대한 혼잡도 기반 분산 배정(`docs/10_PROGRESS_LOG.md` `[10-매칭 23차 준비]`에
  이미 확장 방향으로 명시).
- 마지막 `ACTIVE` 장소 비활성화를 서버에서 차단하는 정책(현재는 클라이언트 경고로만 완화).

## 8. 테스트 우선순위

- **Backend**: `FestivalMeetingPointBackfillService` 단위 테스트 — 핵심 비즈니스 로직이므로
  `docs/09_TEST_AND_QUALITY_STRATEGY.md` 원칙상 우선 테스트 대상이다.
  - 좌표 없는 축제는 skip하고 `WARN` 로그만 남긴다(생성 안 함).
  - 장소가 0건인 `ACTIVE` 축제는 `ACTIVE`/`assignmentOrder=0`/`kakaoPlaceId="AUTO-{contentId}"`로
    정확히 1건 생성된다.
  - 장소가 이미 1건 이상 있는 축제는 그 장소가 전부 `INACTIVE`라도 건드리지 않는다(3.5절의
    "자동 복구가 아니다" 계약을 직접 검증하는 케이스).
  - `ENDED`/`HIDDEN`/`INACTIVE` 축제는 장소가 0건이어도 대상에서 빠진다.
- **Backend**: `FestivalRepository.findActiveFestivalsWithoutMeetingPoint(...)`에 대한 PostgreSQL
  통합 테스트 — 0건/1건 이상/비활성 상태 조합을 fixture로 검증.
- **Backend**: `FestivalSyncService` 통합 테스트에 "sync 이전부터 존재하던 만남 장소 0건 축제가
  이번 실행에서 채워진다", "백필 예외가 발생해도 축제 upsert 결과는 그대로 성공 반환한다" 케이스 추가.
- **Frontend**: `useAdminMeetingPoints` hook의 등록/수정/상태변경 성공·실패·in-flight 가드,
  `AdminMeetingPointsPage`의 목록·폼 렌더링, `AdminNav`의 active 경로 표시.

## 9. 결정 필요 사항 (구현 승인 전 확인)

- 4.1절 대시보드 카드 유지 여부(권장: 제거).
- 3.2절 엔티티 팩토리 방식(`inactive()` + `changeStatus()` 재사용 vs. 신규 `seededActive()` 팩토리).
- 작업 브랜치: 현재 브랜치 `feature/wbs-10-a-festival-course`에서 이어갈지, `docs/12_GIT_BRANCH_RULES.md`
  기준으로 `feature/wbs-10-a-admin-meeting-points`처럼 별도 브랜치로 분리할지.
