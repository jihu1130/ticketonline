package io.github.jihu1130.ticketonline.domain.message.repository;

import io.github.jihu1130.ticketonline.domain.message.entity.Message;
import io.github.jihu1130.ticketonline.domain.message.entity.MessageType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /** 받은 메시지 (알림 또는 받은 쪽지), 최근 순 */
    @Query("select m from Message m"
            + " left join fetch m.sender"
            + " left join fetch m.product"
            + " where m.receiver.id = :memberId and m.type = :type"
            + " order by m.createdAt desc, m.id desc")
    List<Message> findReceived(@Param("memberId") Long memberId, @Param("type") MessageType type, Pageable pageable);

    /** 보낸 쪽지, 최근 순 */
    @Query("select m from Message m"
            + " join fetch m.receiver"
            + " left join fetch m.product"
            + " where m.sender.id = :memberId"
            + " order by m.createdAt desc, m.id desc")
    List<Message> findSent(@Param("memberId") Long memberId, Pageable pageable);

    long countByReceiverIdAndTypeAndReadAtIsNull(Long receiverId, MessageType type);

    /** 한 종류 모두 읽음 */
    @Modifying(clearAutomatically = true)
    @Query("update Message m set m.readAt = CURRENT_TIMESTAMP"
            + " where m.receiver.id = :memberId and m.type = :type and m.readAt is null")
    int markAllRead(@Param("memberId") Long memberId, @Param("type") MessageType type);
}
