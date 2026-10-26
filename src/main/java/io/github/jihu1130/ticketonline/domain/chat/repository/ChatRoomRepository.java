package io.github.jihu1130.ticketonline.domain.chat.repository;

import io.github.jihu1130.ticketonline.domain.chat.entity.ChatRoom;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByProductIdAndBuyerId(Long productId, Long buyerId);

    /** 방 하나와 화면에 필요한 상품·구매자·판매자를 함께 */
    @Query("select r from ChatRoom r join fetch r.product join fetch r.buyer join fetch r.seller where r.id = :id")
    Optional<ChatRoom> findDetailById(@Param("id") Long id);

    /** 메시지 보내기: 같은 방에 동시에 보내도 마지막 메시지가 섞이지 않게 방 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRoom r where r.id = :id")
    Optional<ChatRoom> findByIdForUpdate(@Param("id") Long id);

    /**
     * 내 채팅방 목록 (최근 대화 순). 판매자에게는 메시지가 한 번이라도 오간 방만 보인다
     * (구매자가 문의 버튼만 누르고 아무 말도 안 했으면 판매자 목록에는 없음).
     */
    @Query("select r from ChatRoom r"
            + " join fetch r.product"
            + " join fetch r.buyer"
            + " join fetch r.seller"
            + " where r.buyer.id = :memberId or (r.seller.id = :memberId and r.lastMessageAt is not null)"
            + " order by coalesce(r.lastMessageAt, r.createdAt) desc, r.id desc")
    List<ChatRoom> findMyRooms(@Param("memberId") Long memberId);

    /** 읽음 표시: 더 큰 번호일 때만 바꾼다 (두 창에서 동시에 열어도 뒤로 가지 않음) */
    @Modifying
    @Query("update ChatRoom r set r.buyerLastReadId = :messageId where r.id = :roomId and r.buyerLastReadId < :messageId")
    int markBuyerRead(@Param("roomId") Long roomId, @Param("messageId") long messageId);

    @Modifying
    @Query("update ChatRoom r set r.sellerLastReadId = :messageId where r.id = :roomId and r.sellerLastReadId < :messageId")
    int markSellerRead(@Param("roomId") Long roomId, @Param("messageId") long messageId);
}
