package com.blindtest.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.blindtest.model.entity.ImportJobStatus;
import com.blindtest.model.entity.TrackImportJobEntity;
import com.blindtest.model.entity.UserEntity;
import com.blindtest.model.entity.UserRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class TrackImportJobRepositoryTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TrackImportJobRepository jobRepository;

    private UserEntity admin;
    private UserEntity otherAdmin;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(buildAdmin("admin", "admin@example.com"));
        otherAdmin = userRepository.save(buildAdmin("admin2", "admin2@example.com"));

        jobRepository.save(buildJob(admin, "https://youtube.com/a", ImportJobStatus.QUEUED));
        jobRepository.save(buildJob(admin, "https://youtube.com/b", ImportJobStatus.DONE));
        jobRepository.save(buildJob(otherAdmin, "https://youtube.com/c", ImportJobStatus.QUEUED));
    }

    @Test
    void findByStatusReturnsMatchingJobs() {
        List<TrackImportJobEntity> results = jobRepository.findByStatus(ImportJobStatus.QUEUED);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(TrackImportJobEntity::getStatus).containsOnly(ImportJobStatus.QUEUED);
    }

    @Test
    void findByStatusReturnsEmptyWhenNoneMatch() {
        List<TrackImportJobEntity> results = jobRepository.findByStatus(ImportJobStatus.FAILED);
        assertThat(results).isEmpty();
    }

    @Test
    void findByAdminIdReturnsOnlyThatAdminsJobs() {
        List<TrackImportJobEntity> results = jobRepository.findByAdminId(admin.getId());
        assertThat(results).hasSize(2);
    }

    @Test
    void findByAdminIdReturnsOneJobForOtherAdmin() {
        List<TrackImportJobEntity> results = jobRepository.findByAdminId(otherAdmin.getId());
        assertThat(results).hasSize(1);
    }

    @Test
    void saveAndFindByIdRoundTripsAllFields() {
        TrackImportJobEntity job = buildJob(admin, "https://youtube.com/test", ImportJobStatus.DOWNLOADING);
        job = jobRepository.save(job);

        TrackImportJobEntity found = jobRepository.findById(job.getId()).orElseThrow();
        assertThat(found.getYoutubeUrl()).isEqualTo("https://youtube.com/test");
        assertThat(found.getStatus()).isEqualTo(ImportJobStatus.DOWNLOADING);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    private UserEntity buildAdmin(String username, String email) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(UserRole.ADMIN);
        return user;
    }

    private TrackImportJobEntity buildJob(UserEntity adminUser, String url, ImportJobStatus status) {
        TrackImportJobEntity job = new TrackImportJobEntity();
        job.setYoutubeUrl(url);
        job.setStatus(status);
        job.setAdmin(adminUser);
        return job;
    }
}
