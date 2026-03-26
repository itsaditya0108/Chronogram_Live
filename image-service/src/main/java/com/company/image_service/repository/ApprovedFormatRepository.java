package com.company.image_service.repository;

import com.company.image_service.entity.ApprovedFormat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovedFormatRepository extends JpaRepository<ApprovedFormat, String> {
    List<ApprovedFormat> findAllByIsActiveTrue();
}
