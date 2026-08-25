package com.survey.meetorsolo.domain.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.survey.meetorsolo.domain.member.entity.Member;
import com.survey.meetorsolo.domain.member.entity.MemberPreferenceEmbedding;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class MemberPreferenceEmbeddingRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16")
                    .asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private MemberPreferenceEmbeddingRepository embeddingRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void PENDING_상태로_저장하고_memberId로_조회한다() {
        Member member = memberRepository.save(
                Member.createKakaoMember("pgvector-test-1", "테스트유저", null));
        entityManager.flush();

        MemberPreferenceEmbedding embedding = MemberPreferenceEmbedding.create(member, "축제에서 맛집 탐방하고 싶어요");
        embeddingRepository.save(embedding);
        entityManager.flush();
        entityManager.clear();

        Optional<MemberPreferenceEmbedding> found = embeddingRepository.findByMemberId(member.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getPreferenceText()).isEqualTo("축제에서 맛집 탐방하고 싶어요");
        assertThat(found.get().getEmbeddingStatus()).isEqualTo("PENDING");
        assertThat(found.get().getEmbedding()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void COMPLETED_상태에서_vector_1536_round_trip을_확인한다() {
        Member member = memberRepository.save(
                Member.createKakaoMember("pgvector-test-2", "벡터유저", null));
        entityManager.flush();

        MemberPreferenceEmbedding embedding = MemberPreferenceEmbedding.create(member, "느긋하게 문화 답사");
        float[] vector = new float[1536];
        for (int i = 0; i < 1536; i++) {
            vector[i] = 0.001f * i;
        }
        embedding.markCompleted(vector, "text-embedding-3-small");
        embeddingRepository.save(embedding);
        entityManager.flush();
        entityManager.clear();

        MemberPreferenceEmbedding found = embeddingRepository.findByMemberId(member.getId())
                .orElseThrow();

        assertThat(found.getEmbeddingStatus()).isEqualTo("COMPLETED");
        assertThat(found.getEmbeddingModel()).isEqualTo("text-embedding-3-small");
        assertThat(found.getEmbedding()).hasSize(1536);
        assertThat(found.getEmbedding()[0]).isCloseTo(0.0f, org.assertj.core.data.Offset.offset(0.001f));
        assertThat(found.getEmbedding()[100]).isCloseTo(0.1f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    void deleteByMemberId로_임베딩을_삭제한다() {
        Member member = memberRepository.save(
                Member.createKakaoMember("pgvector-test-3", "삭제유저", null));
        entityManager.flush();

        MemberPreferenceEmbedding embedding = MemberPreferenceEmbedding.create(member, "테스트 텍스트");
        embeddingRepository.save(embedding);
        entityManager.flush();

        embeddingRepository.deleteByMemberId(member.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(embeddingRepository.findByMemberId(member.getId())).isEmpty();
    }

    @Test
    void FAILED_상태를_저장하고_조회한다() {
        Member member = memberRepository.save(
                Member.createKakaoMember("pgvector-test-4", "실패유저", null));
        entityManager.flush();

        MemberPreferenceEmbedding embedding = MemberPreferenceEmbedding.create(member, "실패 테스트 텍스트");
        embedding.markFailed();
        embeddingRepository.save(embedding);
        entityManager.flush();
        entityManager.clear();

        MemberPreferenceEmbedding found = embeddingRepository.findByMemberId(member.getId())
                .orElseThrow();

        assertThat(found.getEmbeddingStatus()).isEqualTo("FAILED");
        assertThat(found.getEmbedding()).isNull();
        assertThat(found.getEmbeddingModel()).isNull();
    }
}
