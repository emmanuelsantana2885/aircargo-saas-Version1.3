package com.aircargo.uldservice.repository;

import com.aircargo.uldservice.entity.UldAwb;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UldAwbRepository extends JpaRepository<UldAwb, UUID> {
    List<UldAwb> findByUldId(UUID uldId);
    List<UldAwb> findByUldIdIn(List<UUID> uldIds);
    List<UldAwb> findByMawbId(UUID mawbId);
    List<UldAwb> findByMawbIdIn(List<UUID> mawbIds);
    List<UldAwb> findByMawbLabel(String mawbLabel);
    List<UldAwb> findByMawbLabelIn(List<String> mawbLabels);
    Optional<UldAwb> findByUldIdAndMawbId(UUID uldId, UUID mawbId);
    void deleteByUldId(UUID uldId);
}
