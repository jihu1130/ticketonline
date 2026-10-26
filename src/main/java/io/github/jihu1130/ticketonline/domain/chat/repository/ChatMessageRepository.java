package io.github.jihu1130.ticketonline.domain.chat.repository;

import io.github.jihu1130.ticketonline.domain.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** 방에 처음 들어왔을 때: 최근 메시지 (번호 큰 순, 화면에서 뒤집는다) */
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId order by m.id desc")
    List<ChatMessage> findLatest(@Param("roomId") Long roomId, Pageable pageable);

    /** 2초마다: 이미 받은 마지막 번호 다음부터 (번호 작은 순) */
    @Query("select m from ChatMessage m join fetch m.sender where m.room.id = :roomId and m.id > :afterId order by m.id")
    List<ChatMessage> findAfter(@Param("roomId") Long roomId, @Param("afterId") long afterId, Pageable pageable);

    /** 방별 안 읽은 개수: [방 번호, 개수]. 내가 보낸 메시지와 이미 읽은 번호까지는 빼고 센다 */
    @Query("select m.room.id, count(m) from ChatMessage m join m.room r"
            + " where m.sender.id <> :memberId"
            + " and ((r.buyer.id = :memberId and m.id > r.buyerLastReadId)"
            + "   or (r.seller.id = :memberId and m.id > r.sellerLastReadId))"
            + " group by m.room.id")
    List<Object[]> countUnreadByRoom(@Param("memberId") Long memberId);
}
