package io.github.jihu1130.ticketonline.domain.chat.entity;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 채팅방: 상품 하나 x 구매자 한 명 = 방 하나 (같은 상품으로 다시 문의하면 같은 방).
 * 읽음은 사람마다 "마지막으로 읽은 메시지 번호"로 기억한다. 그보다 큰 번호의 상대 메시지가 안 읽은 메시지.
 * 목록에서 매번 메시지를 뒤지지 않게 마지막 메시지와 시각을 방에 같이 저장한다.
 */
@Entity
@DynamicUpdate
@Table(name = "chat_room", uniqueConstraints = @UniqueConstraint(
        name = "uk_chat_room_product_buyer", columnNames = {"product_id", "buyer_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {

    /** 목록에 보여 줄 마지막 메시지 길이 */
    private static final int PREVIEW_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** 문의한 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    /** 상품을 등록한 사람 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private Member seller;

    @Column(length = PREVIEW_LENGTH)
    private String lastMessage;

    private LocalDateTime lastMessageAt;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long buyerLastReadId;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long sellerLastReadId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ChatRoom(Product product, Member buyer, Member seller) {
        this.product = product;
        this.buyer = buyer;
        this.seller = seller;
    }

    public boolean hasMember(Long memberId) {
        return isBuyer(memberId) || seller.getId().equals(memberId);
    }

    public boolean isBuyer(Long memberId) {
        return buyer.getId().equals(memberId);
    }

    public Member partnerOf(Long memberId) {
        return isBuyer(memberId) ? seller : buyer;
    }

    public long lastReadIdOf(Long memberId) {
        return isBuyer(memberId) ? buyerLastReadId : sellerLastReadId;
    }

    /**
     * 새 메시지가 오면 목록용 마지막 메시지를 바꾼다. 방 행을 잠근 상태에서 부른다.
     * 바뀐 칸만 저장(@DynamicUpdate)하므로 읽음 표시(따로 UPDATE)를 덮어쓰지 않는다.
     * 보낸 사람의 읽음 번호는 바꾸지 않는다: 보내는 사이에 온 상대 메시지는 아직 못 본 것이고,
     * 내가 보낸 메시지는 안 읽은 개수에 원래 들어가지 않는다.
     */
    public void onMessage(ChatMessage message) {
        String content = message.getContent();
        this.lastMessage = content.length() > PREVIEW_LENGTH ? content.substring(0, PREVIEW_LENGTH - 1) + "…" : content;
        this.lastMessageAt = message.getCreatedAt();
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
