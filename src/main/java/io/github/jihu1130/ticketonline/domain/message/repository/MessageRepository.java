package io.github.jihu1130.ticketonline.domain.message.repository;

import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 내 알림, 최근 순 */
    @Query("select m from Message m left join fetch m.product"
            + " where m.receiver.id = :memberId order by m.createdAt desc, m.id desc")
    List<Message> findNotices(@Param("memberId") Long memberId, Pageable pageable);

    long countByReceiverIdAndReadAtIsNull(Long receiverId);

    @Modifying(clearAutomatically = true)
    @Query("update Message m set m.readAt = CURRENT_TIMESTAMP where m.receiver.id = :memberId and m.readAt is null")
    int markAllRead(@Param("memberId") Long memberId);
}
