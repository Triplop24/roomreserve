package dev.portfolio.roomreserve.room;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByActiveTrueOrderByIdAsc();

    Optional<Room> findByIdAndActiveTrue(Long id);
}
