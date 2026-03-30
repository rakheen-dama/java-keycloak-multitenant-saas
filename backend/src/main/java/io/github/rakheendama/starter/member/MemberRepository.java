package io.github.rakheendama.starter.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

  Optional<Member> findByKeycloakUserId(String keycloakUserId);

  Optional<Member> findByEmail(String email);

  List<Member> findByStatus(String status);
}
