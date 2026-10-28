package io.github.jihu1130.ticketonline;

import io.github.jihu1130.ticketonline.domain.member.entity.Member;
import io.github.jihu1130.ticketonline.domain.member.entity.MemberRole;
import io.github.jihu1130.ticketonline.domain.member.repository.MemberRepository;
import io.github.jihu1130.ticketonline.domain.product.entity.DeliveryType;
import io.github.jihu1130.ticketonline.domain.product.entity.GoodsDetail;
import io.github.jihu1130.ticketonline.domain.product.entity.Performance;
import io.github.jihu1130.ticketonline.domain.product.entity.Product;
import io.github.jihu1130.ticketonline.domain.product.entity.ProductType;
import io.github.jihu1130.ticketonline.domain.product.entity.SaleItem;
import io.github.jihu1130.ticketonline.domain.product.entity.SeatGrade;
import io.github.jihu1130.ticketonline.domain.product.entity.TicketDetail;
import io.github.jihu1130.ticketonline.domain.product.repository.GoodsDetailRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.PerformanceRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.ProductRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SaleItemRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.SeatGradeRepository;
import io.github.jihu1130.ticketonline.domain.product.repository.TicketDetailRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 앱이 뜨는지 확인. 개발 DB 대신 테스트 DB(ticketonline_test)를 쓴다. */
@SpringBootTest
@ActiveProfiles("test")
class TicketonlineApplicationTests {

	@Test
	void contextLoads() {
	}

}

/**
 * 개발 DB(ticketonline)에 관리자 계정과 예시 상품을 넣는다.
 * ./gradlew test 에서는 빠지고(build.gradle 의 seed 태그), 직접 골라 실행할 때만 돈다.
 *   IntelliJ: seedAdminAndProducts 옆 ▶ 버튼
 *   터미널  : ./gradlew test --tests "*DevDataSeed*" --rerun   (--rerun 이 없으면 Gradle 이 이전 결과를 쓰고 건너뛸 수 있다)
 * 관리자(admin@admin.admin)가 이미 있으면 아무것도 하지 않는다. 날짜는 실행 시각 기준.
 */
@SpringBootTest
@Tag("seed")
class DevDataSeed {

	static final String ADMIN_EMAIL = "admin@admin.admin";

	@Autowired private MemberRepository memberRepository;
	@Autowired private PasswordEncoder passwordEncoder;
	@Autowired private ProductRepository productRepository;
	@Autowired private TicketDetailRepository ticketDetailRepository;
	@Autowired private GoodsDetailRepository goodsDetailRepository;
	@Autowired private PerformanceRepository performanceRepository;
	@Autowired private SeatGradeRepository seatGradeRepository;
	@Autowired private SaleItemRepository saleItemRepository;

	@Test
	@Transactional
	@Commit
	void seedAdminAndProducts() {
		if (memberRepository.existsByEmail(ADMIN_EMAIL)) {
			System.out.println("[seed] 관리자 계정이 이미 있어 건너뜁니다.");
			return;
		}
		// 회원가입 검사(비밀번호 8자 이상)를 거치지 않고 바로 저장한다
		Member admin = memberRepository.save(Member.builder().email(ADMIN_EMAIL)
				.password(passwordEncoder.encode("admin")).name("admin").role(MemberRole.ADMIN).build());

		LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
		LocalDate today = now.toLocalDate();

		// 판매 중: 어제 ~ 마지막 공연 1시간 전
		LocalDateTime concert1 = today.plusDays(21).atTime(19, 0);
		LocalDateTime concert2 = today.plusDays(22).atTime(18, 0);
		ticket(admin, "2026 겨울 콘서트 <WINTER LIGHT>", now.minusDays(1), concert2.minusHours(1), 4,
				"올림픽홀", "한 해를 마무리하는 겨울 단독 콘서트입니다.",
				List.of(concert1, concert2),
				List.of(new Grade("VIP", 150_000, 50), new Grade("R", 120_000, 100), new Grade("S", 90_000, 150)));

		// 판매 예정: 2일 뒤 20시 오픈
		ticket(admin, "뮤지컬 <별을 쫓는 아이>", today.plusDays(2).atTime(20, 0), today.plusDays(36).atTime(12, 0), 2,
				"블루스퀘어 신한카드홀", "가족과 함께 보는 창작 뮤지컬.",
				List.of(today.plusDays(35).atTime(19, 30), today.plusDays(36).atTime(14, 0)),
				List.of(new Grade("R", 130_000, 80), new Grade("S", 100_000, 120)));

		// 오늘 오픈: 10분 뒤 ~ 내일
		ticket(admin, "오늘 오픈 팬미팅 <HELLO>", now.plusMinutes(10), now.plusDays(1), 2,
				"예스24라이브홀", "오늘 예매가 열리는 팬미팅입니다.",
				List.of(today.plusDays(2).atTime(19, 0)),
				List.of(new Grade("R", 77_000, 100), new Grade("S", 55_000, 100)));

		goods(admin, "공식 응원봉", now.minusDays(1), now.plusDays(14), 2,
				35_000, 200, DeliveryType.PARCEL, "블루투스 연동 공식 응원봉입니다.");
		goods(admin, "한정판 포토북", today.plusDays(1).atTime(12, 0), now.plusDays(14), 1,
				25_000, 100, DeliveryType.PICKUP, "공연장 현장에서 수령하는 한정판 포토북입니다.");
		goods(admin, "오늘 오픈 키링", now.plusMinutes(10), now.plusDays(1), 2,
				15_000, 100, DeliveryType.PARCEL, "오늘만 판매하는 아크릴 키링입니다.");

		System.out.println("[seed] 관리자(" + ADMIN_EMAIL + ")와 예시 상품 6개를 만들었습니다.");
	}

	private record Grade(String name, int price, int seatCount) {
	}

	/** 티켓: 회차 x 등급마다 판매 단위(재고) 하나 */
	private void ticket(Member seller, String title, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
			int maxPerPerson, String venue, String description, List<LocalDateTime> eventAts, List<Grade> grades) {
		Product product = productRepository.save(product(seller, ProductType.TICKET, title, saleStartAt, saleEndAt,
				maxPerPerson));
		ticketDetailRepository.save(TicketDetail.builder().product(product).venue(venue).closeBeforeHours(1)
				.description(description).build());
		List<SeatGrade> seatGrades = grades.stream()
				.map(g -> seatGradeRepository.save(SeatGrade.builder().product(product).name(g.name())
						.price(g.price()).seatCount(g.seatCount()).build()))
				.toList();
		for (LocalDateTime eventAt : eventAts) {
			Performance performance = performanceRepository.save(Performance.builder().product(product)
					.eventAt(eventAt).build());
			for (SeatGrade grade : seatGrades) {
				saleItemRepository.save(SaleItem.builder().product(product).performance(performance).seatGrade(grade)
						.price(grade.getPrice()).totalQuantity(grade.getSeatCount()).build());
			}
		}
	}

	private void goods(Member seller, String title, LocalDateTime saleStartAt, LocalDateTime saleEndAt,
			int maxPerPerson, int price, int quantity, DeliveryType deliveryType, String description) {
		Product product = productRepository.save(product(seller, ProductType.GOODS, title, saleStartAt, saleEndAt,
				maxPerPerson));
		goodsDetailRepository.save(GoodsDetail.builder().product(product).description(description)
				.deliveryType(deliveryType).build());
		saleItemRepository.save(SaleItem.builder().product(product).price(price).totalQuantity(quantity).build());
	}

	private Product product(Member seller, ProductType type, String title, LocalDateTime saleStartAt,
			LocalDateTime saleEndAt, int maxPerPerson) {
		return Product.builder().seller(seller).type(type).title(title).maxPerPerson(maxPerPerson)
				.saleStartAt(saleStartAt).saleEndAt(saleEndAt).build();
	}
}
