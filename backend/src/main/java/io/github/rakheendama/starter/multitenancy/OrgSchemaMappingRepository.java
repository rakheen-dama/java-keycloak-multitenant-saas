package io.github.rakheendama.starter.multitenancy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgSchemaMappingRepository extends JpaRepository<OrgSchemaMapping, UUID> {

  Optional<OrgSchemaMapping> findByOrgId(String orgId);
}
