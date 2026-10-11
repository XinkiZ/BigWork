package com.wylie.registration.repo;

import com.wylie.registration.domain.ScheduleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleItemRepository extends JpaRepository<ScheduleItem, Long> {

    List<ScheduleItem> findByScheduleIdOrderByItemTypeAscSortOrderAsc(Long scheduleId);

    void deleteByScheduleId(Long scheduleId);

    List<ScheduleItem> findByOfferingId(Long offeringId);
}