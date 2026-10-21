package com.desi.youtube.upload;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<UploadSession, UUID> {}
