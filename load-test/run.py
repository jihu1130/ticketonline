"""
k6 부하 테스트 실행기 (11단계): 방식 A / B / C 를 같은 조건으로 실행하고 결과 표를 만든다.

조건 (기획서 3-1, 3-2)
  재고 100개 굿즈(1인 1개)에 회원 1000명이 동시에 1개씩 구매 요청
  대기열·알림은 끈다 (구매 처리만 비교), DB 연결 수는 기본값(10)으로 같게
  방식마다 서버를 새로 띄우고, 기록하지 않는 예열 1번 뒤 5번 측정 -> 가운데 값(중앙값)

준비물: MySQL(localhost:3306), Redis(localhost:6379), k6, Java 21.
  DB 비밀번호는 src/main/resources/application-secret.yaml 에서 읽는다 (이 파일은 커밋하지 않음).
  부하 테스트 전용 DB ticketonline_load 를 쓴다 (개발 DB 와 섞이지 않게). Redis 는 3번 DB.

실행 (저장소 최상위에서):  py -3 load-test/run.py            # A, B, C 각 5번
                           py -3 load-test/run.py redis 1    # 방식 C 만 1번
                           py -3 load-test/run.py --users 1000,3000,5000   # 동시 인원별 (재고는 100 그대로)
                           py -3 load-test/run.py stress    # 한계 측정 (조회·대기열·구매를 섞어 초당 요청 수를 올림)
결과: load-test/results/results.md, results.json
      --users 를 주면 load-test/results/scale/ 에 따로 남긴다 (기본 1000명 결과는 덮어쓰지 않음)
      stress 는 load-test/results/stress/
"""
import json
import os
import re
import secrets
import shutil
import statistics
import subprocess
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).resolve().parent.parent
HERE = ROOT / "load-test"
WORK = HERE / "work"
RESULTS = HERE / "results"
SCALE_RESULTS = RESULTS / "scale"
PORT = 8891
BASE = f"http://localhost:{PORT}"
DB = "ticketonline_load"
REDIS_DB = 3
MEMBERS = 1000  # 기본 동시 인원 (--users 로 바꿀 수 있음)
STOCK = 100
# 연결을 미리 맺는 속도: 초당 500개 (1000명이면 2초). Windows 의 연결 대기 줄(약 200)이 넘치지 않게
CONNECT_PER_SECOND = 500
WARMUPS = 3  # 측정 전 예열 횟수 (기록하지 않음)

# 한계 측정 (run.py stress)
STRESS_RESULTS = RESULTS / "stress"
STRESS_RATES = [200, 500, 1000, 2000, 4000, 8000, 16000, 32000]  # 초당 요청 수를 이 순서로 올린다
STRESS_STAGE_DURATION = "30s"
STRESS_WARMUP_RATE = 300
STRESS_MEMBERS = 5000
STRESS_PRODUCTS = 5
STRESS_STOCK = 1000
STRESS_MAX_VUS = 3000  # k6 가상 사용자 상한 (PC 메모리 보호)
STRATEGIES = {"pessimistic": "A. 비관적 락", "optimistic": "B. 낙관적 락", "redis": "C. Redis 차감"}

MYSQL = os.environ.get("MYSQL_EXE", r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe")
K6 = os.environ.get("K6_EXE") or shutil.which("k6") or r"C:\Program Files\k6\k6.exe"
REDIS_CLI = os.environ.get("REDIS_CLI", r"C:\redis\redis-cli.exe")


# ---------- 도우미 ----------

def db_password():
    text = (ROOT / "src/main/resources/application-secret.yaml").read_text(encoding="utf-8")
    match = re.search(r"datasource:\s*(?:\n\s+\w[\w-]*:.*)*?\n\s+password:\s*['\"]?([^'\"\n]+)", text)
    return match.group(1).strip() if match else os.environ.get("DB_PASSWORD", "1234")


def sql(query):
    """mysql 명령으로 실행하고 결과를 (탭 구분) 줄 목록으로 돌려준다. SQL 은 길 수 있어 표준 입력으로 넘긴다"""
    env = dict(os.environ, MYSQL_PWD=db_password())
    out = subprocess.run([MYSQL, "-uroot", "-N", "-B", "--default-character-set=utf8mb4", DB], input=query, env=env,
                         capture_output=True, text=True, encoding="utf-8", check=True)
    return [line.split("\t") for line in out.stdout.strip().splitlines() if line]


def call(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            return res.status, json.loads(res.read().decode("utf-8") or "null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "null")


def build_jar():
    print("서버 jar 만드는 중 (gradlew bootJar)...")
    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    subprocess.run([str(gradlew), "bootJar", "-q"], cwd=ROOT, check=True, stdin=subprocess.DEVNULL)
    return next(p for p in (ROOT / "build/libs").glob("*.jar") if not p.name.endswith("-plain.jar"))


def java_exe():
    """JDK 의 java.exe 를 직접 쓴다. PATH 의 java 가 Oracle 실행기(javapath)면 그 실행기만 꺼지고 서버가 남는다"""
    home = os.environ.get("JAVA_HOME")
    candidates = [Path(home) / "bin" / "java.exe"] if home else []
    candidates += [Path(r"C:\Program Files\Java\jdk-21\bin\java.exe")]
    return next((str(p) for p in candidates if p.exists()), "java")


def port_in_use():
    try:
        call("GET", "/api/products")
        return True
    except OSError:
        return False


def start_server(jar, strategy, real_flow=False):
    """real_flow=True (한계 측정): 대기열·알림을 실제 서비스처럼 켠다. 기본은 구매 처리만 비교하려고 끈다"""
    # 이전 실행의 서버가 남아 있으면 그 서버를 재게 되므로 멈춘다
    if port_in_use():
        raise RuntimeError(f"{PORT} 포트를 이미 다른 서버가 쓰고 있습니다. 그 서버를 끄고 다시 실행하세요.")
    log = open(WORK / f"server-{strategy}.log", "w", encoding="utf-8")
    args = [java_exe(), "-jar", str(jar),
            f"--server.port={PORT}",
            f"--app.purchase.strategy={strategy}",
            f"--app.queue.enabled={str(real_flow).lower()}",
            f"--app.notice.enabled={str(real_flow).lower()}",
            f"--spring.datasource.url=jdbc:mysql://localhost:3306/{DB}?serverTimezone=Asia/Seoul&createDatabaseIfNotExist=true",
            "--spring.jpa.hibernate.ddl-auto=update",
            "--spring.jpa.show-sql=false",
            # 1000명이 한꺼번에 연결하므로 연결 대기 줄을 늘린다 (기본 100이면 나머지 연결이 거절됨)
            "--server.tomcat.accept-count=1000",
            f"--spring.data.redis.database={REDIS_DB}",
            "--logging.level.root=WARN"]
    proc = subprocess.Popen(args, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    for _ in range(120):
        time.sleep(1)
        if proc.poll() is not None:
            raise RuntimeError(f"서버가 꺼졌습니다. {log.name} 를 보세요.")
        try:
            if call("GET", "/api/products")[0] == 200 and proc.poll() is None:
                return proc
        except OSError:
            pass
    raise RuntimeError("서버가 120초 안에 켜지지 않았습니다.")


def stop_server(proc):
    proc.terminate()
    try:
        proc.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proc.kill()
    for _ in range(30):  # 포트가 비워질 때까지 기다린다 (다음 방식 서버가 같은 포트를 씀)
        if not port_in_use():
            return
        time.sleep(1)
    raise RuntimeError(f"서버를 껐는데 {PORT} 포트가 아직 응답합니다.")


# ---------- 테스트 데이터 ----------

def prepare_members(members):
    """판매자 1명 + 구매자 members 명. 구매자는 같은 비밀번호 해시로 한 번에 넣고, 로그인해서 토큰을 모은다."""
    account_file = WORK / "account.json"
    if account_file.exists():
        account = json.loads(account_file.read_text(encoding="utf-8"))
    else:
        account = {"password": "load-" + secrets.token_hex(8)}
        account_file.write_text(json.dumps(account), encoding="utf-8")
    password = account["password"]

    call("POST", "/api/members/signup", {"email": "load-seller@test.com", "password": password, "name": "부하판매자"})
    pw_hash = sql("select password from member where email = 'load-seller@test.com'")[0][0]
    have = int(sql("select count(*) from member where email like 'load-user-%@test.com'")[0][0])
    if have < members:
        rows = ",".join(f"('load-user-{i}@test.com', '{pw_hash}', '구매자{i}', 'USER', now(6))"
                        for i in range(have, members))
        sql(f"insert into member (email, password, name, role, created_at) values {rows}")

    def login(i):
        status, body = call("POST", "/api/members/login", {"email": f"load-user-{i}@test.com", "password": password})
        if status != 200:
            raise RuntimeError(f"로그인 실패 load-user-{i}: {status} {body}")
        return body["accessToken"]

    with ThreadPoolExecutor(16) as pool:
        tokens = list(pool.map(login, range(members)))
    (WORK / "tokens.json").write_text(json.dumps(tokens), encoding="utf-8")
    seller_id = int(sql("select member_id from member where email = 'load-seller@test.com'")[0][0])
    print(f"구매자 {len(tokens)}명 토큰 준비 완료")
    return seller_id


def create_goods(seller_id, label, stock=STOCK):
    """판매 중인 굿즈(현장 수령, 1인 1개, 재고 stock)를 SQL 로 바로 만든다 (등록 API 는 판매 시작 전만 허용)"""
    rows = sql(f"""
        insert into product (title, type, max_per_person, sale_start_at, sale_end_at, created_at, seller_id)
        values ('부하 테스트 {label}', 'GOODS', 1, now(6) - interval 1 hour, now(6) + interval 1 day, now(6), {seller_id});
        set @p = last_insert_id();
        insert into goods_detail (product_id, delivery_type, description) values (@p, 'PICKUP', 'k6');
        insert into sale_item (product_id, price, total_quantity, sold_quantity, version) values (@p, 10000, {stock}, 0, 0);
        select @p, last_insert_id();""")
    return int(rows[-1][0]), int(rows[-1][1])


# ---------- 실행과 집계 ----------

def run_k6(product_id, sale_item_id, name, vus=MEMBERS):
    summary = WORK / f"summary-{name}.json"
    env = dict(os.environ, K6_NO_USAGE_REPORT="true")
    spread_ms = max(2000, vus * 1000 // CONNECT_PER_SECOND)
    subprocess.run([K6, "run", "--quiet",
                    "-e", f"BASE_URL={BASE}", "-e", f"PRODUCT_ID={product_id}", "-e", f"SALE_ITEM_ID={sale_item_id}",
                    "-e", f"TOKENS={WORK / 'tokens.json'}", "-e", f"SUMMARY={summary}", "-e", f"VUS={vus}",
                    "-e", f"SPREAD_MS={spread_ms}",
                    str(HERE / "purchase.js")], cwd=HERE, env=env, check=True, stdin=subprocess.DEVNULL)
    return json.loads(summary.read_text(encoding="utf-8"))


def count(metrics, name):
    return int(metrics.get(name, {}).get("values", {}).get("count", 0))


def measure(strategy, run_no, seller_id, users=MEMBERS):
    if strategy == "redis":
        subprocess.run([REDIS_CLI, "-n", str(REDIS_DB), "flushdb"], capture_output=True, check=True)
    product_id, sale_item_id = create_goods(seller_id, f"{strategy} {users}명 {run_no}")
    data = run_k6(product_id, sale_item_id, f"{strategy}-{users}-{run_no}", users)
    m = data["metrics"]
    requests = count(m, "http_reqs{name:purchase}")
    duration = m["http_req_duration{name:purchase}"]["values"]
    # TPS = 구매 요청 수 / (출발부터 마지막 응답까지 걸린 시간)
    burst_ms = m["purchase_finished_at"]["values"]["max"]
    sold, total = map(int, sql(f"select sold_quantity, total_quantity from sale_item where sale_item_id = {sale_item_id}")[0])
    ordered, orders = map(int, sql(f"select coalesce(sum(quantity), 0), count(*) from product_order "
                                   f"where sale_item_id = {sale_item_id} and status = 'SUCCESS'")[0])
    result = {
        "strategy": strategy, "users": users, "run": run_no, "requests": requests,
        "success": count(m, "purchase_success"), "sold_out": count(m, "purchase_sold_out"),
        "server_errors": count(m, "server_errors"), "other_errors": count(m, "other_errors"),
        # 응답을 아예 못 받은 요청 (연결 거절 등). k6 는 이런 요청을 상태 0 으로 센다
        "no_response": count(m, "no_response"),
        "burst_ms": round(burst_ms, 1),
        "tps": round(requests / (burst_ms / 1000), 1) if burst_ms else 0,
        "p95_ms": round(duration["p(95)"], 1), "p99_ms": round(duration["p(99)"], 1),
        "avg_ms": round(duration["avg"], 1), "max_ms": round(duration["max"], 1),
        "error_rate": round(100 * count(m, "server_errors") / requests, 2) if requests else 0,
        "db_sold": sold, "db_ordered": ordered, "db_orders": orders,
        "oversell": max(0, ordered - total) + max(0, sold - total),
        "sold_matches_orders": sold == ordered,
    }
    print(f"  {STRATEGIES[strategy]} {users}명 {run_no}회: 성공 {result['success']}, 매진 {result['sold_out']}, "
          f"5xx {result['server_errors']}, 무응답 {result['no_response']}, 처리 {result['burst_ms']}ms, TPS {result['tps']}, p95 {result['p95_ms']}ms, "
          f"DB 판매 {sold} / 주문 {ordered}, 초과 판매 {result['oversell']}")
    return result


def summarize(results):
    lines = [f"# k6 부하 테스트 결과 ({datetime.now():%Y-%m-%d %H:%M})", "",
             f"조건: 재고 {STOCK}개 굿즈(1인 1개)에 회원 {MEMBERS}명이 동시에 1개씩 구매 요청, "
             "대기열·알림 끔, DB 연결 10개(기본값). 방식마다 예열 1번 뒤 측정, 표는 측정값의 중앙값.", "",
             "| 방법 | 초과 판매 건수 | 처리량 (TPS) | 응답 시간 (p95) | 에러율 (5xx) | 성공 / 매진 / 5xx |",
             "|---|---|---|---|---|---|"]
    medians = {}
    for strategy, label in STRATEGIES.items():
        runs = [r for r in results if r["strategy"] == strategy]
        if not runs:
            continue
        med = {k: statistics.median(r[k] for r in runs)
               for k in ("tps", "p95_ms", "error_rate", "success", "sold_out", "server_errors")}
        med["oversell"] = max(r["oversell"] for r in runs)  # 초과 판매는 한 번이라도 있으면 실패라 최댓값
        medians[strategy] = med
        lines.append(f"| {label} | {med['oversell']}건 | {med['tps']:.0f} | {med['p95_ms']:.0f}ms | "
                     f"{med['error_rate']:.1f}% | {med['success']:.0f} / {med['sold_out']:.0f} / {med['server_errors']:.0f} |")
    lines += ["", "## 회차별", "",
              "| 방법 | 회차 | 성공 | 매진 | 5xx | 무응답 | 처리 시간 | TPS | p95 | p99 | 평균 | 최대 | DB 판매 / 주문 | 초과 판매 |",
              "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in results:
        lines.append(f"| {STRATEGIES[r['strategy']]} | {r['run']} | {r['success']} | {r['sold_out']} | {r['server_errors']} | "
                     f"{r['no_response']} | {r['burst_ms']}ms | {r['tps']} | {r['p95_ms']}ms | {r['p99_ms']}ms | {r['avg_ms']}ms | {r['max_ms']}ms | "
                     f"{r['db_sold']} / {r['db_ordered']} | {r['oversell']} |")
    return "\n".join(lines) + "\n", medians


def median_of(runs):
    med = {k: statistics.median(r[k] for r in runs)
           for k in ("tps", "p95_ms", "error_rate", "success", "sold_out", "server_errors", "no_response")}
    med["oversell"] = max(r["oversell"] for r in runs)  # 초과 판매는 한 번이라도 있으면 실패라 최댓값
    return med


def summarize_scale(results, user_levels):
    """동시 인원별 표. medians = {"인원": {방식: 중앙값}}"""
    lines = [f"# k6 동시 인원별 부하 테스트 결과 ({datetime.now():%Y-%m-%d %H:%M})", "",
             f"조건: 재고 {STOCK}개 굿즈(1인 1개)에 회원 {', '.join(map(str, user_levels))}명이 동시에 1개씩 구매 요청, "
             "대기열·알림 끔, DB 연결 10개(기본값), k6 와 서버를 같은 PC 에서 실행. "
             "방식·인원마다 예열 1번 뒤 측정, 표는 측정값의 중앙값.", "",
             "| 인원 | 방법 | 초과 판매 건수 | 처리량 (TPS) | 응답 시간 (p95) | 에러율 (5xx) | 성공 / 매진 / 5xx / 무응답 |",
             "|---|---|---|---|---|---|---|"]
    medians = {}
    for users in user_levels:
        for strategy, label in STRATEGIES.items():
            runs = [r for r in results if r["strategy"] == strategy and r["users"] == users]
            if not runs:
                continue
            med = median_of(runs)
            medians.setdefault(str(users), {})[strategy] = med
            lines.append(f"| {users:,} | {label} | {med['oversell']}건 | {med['tps']:.0f} | {med['p95_ms']:.0f}ms | "
                         f"{med['error_rate']:.1f}% | {med['success']:.0f} / {med['sold_out']:.0f} / "
                         f"{med['server_errors']:.0f} / {med['no_response']:.0f} |")
    lines += ["", "## 회차별", "",
              "| 인원 | 방법 | 회차 | 성공 | 매진 | 5xx | 무응답 | 처리 시간 | TPS | p95 | p99 | 평균 | 최대 | DB 판매 / 주문 | 초과 판매 |",
              "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"]
    for r in sorted(results, key=lambda r: (r["users"], list(STRATEGIES).index(r["strategy"]), r["run"])):
        lines.append(f"| {r['users']:,} | {STRATEGIES[r['strategy']]} | {r['run']} | {r['success']} | {r['sold_out']} | "
                     f"{r['server_errors']} | {r['no_response']} | {r['burst_ms']}ms | {r['tps']} | {r['p95_ms']}ms | "
                     f"{r['p99_ms']}ms | {r['avg_ms']}ms | {r['max_ms']}ms | {r['db_sold']} / {r['db_ordered']} | {r['oversell']} |")
    return "\n".join(lines) + "\n", medians


def parse_args(argv):
    """[방식] [횟수] [--users 1000,3000,5000]. --users 가 없으면 기본 1000명만 재고 기존 결과 파일에 쓴다"""
    args = list(argv)
    user_levels = None
    if "--users" in args:
        i = args.index("--users")
        user_levels = sorted({int(u) for u in args[i + 1].split(",")})
        del args[i:i + 2]
    only = args[0] if len(args) > 0 else None
    runs = int(args[1]) if len(args) > 1 else 5
    return only, runs, user_levels


def sample_pc():
    """1초마다 PC 전체 CPU 사용률(%)과 남은 메모리(MB)를 기록하는 typeperf 를 띄운다"""
    out = open(WORK / "pc-sample.csv", "w", encoding="utf-8")
    proc = subprocess.Popen(["typeperf", r"\Processor(_Total)\% Processor Time", r"\Memory\Available MBytes", "-si", "1"],
                            stdout=out, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL)
    return proc, out


def read_pc_samples(proc, out):
    proc.terminate()
    proc.wait()
    out.close()
    cpu, mem = [], []
    for line in (WORK / "pc-sample.csv").read_text(encoding="utf-8", errors="ignore").splitlines()[1:]:
        cells = [c.strip('"') for c in line.split(",")]
        try:
            cpu.append(float(cells[1]))
            mem.append(float(cells[2]))
        except (IndexError, ValueError):
            pass  # 머리글, 안내 문구, 빈 값
    return cpu, mem


def stress():
    """한계 측정: 방식 C + 대기열·알림 켬. 초당 요청 수를 단계별로 올리다가 오류·지연이 기준을 넘으면 멈춘다"""
    WORK.mkdir(exist_ok=True)
    STRESS_RESULTS.mkdir(parents=True, exist_ok=True)
    jar = build_jar()
    proc = start_server(jar, "redis", real_flow=True)
    stages = []
    try:
        seller_id = prepare_members(STRESS_MEMBERS)
        subprocess.run([REDIS_CLI, "-n", str(REDIS_DB), "flushdb"], capture_output=True, check=True)
        items = [create_goods(seller_id, f"한계 측정 {n}", STRESS_STOCK) for n in range(1, STRESS_PRODUCTS + 1)]
        items_arg = ",".join(f"{p}:{s}" for p, s in items)
        print(f"\n== 한계 측정 (방식 C, 대기열·알림 켬, 상품 {len(items)}개 x 재고 {STRESS_STOCK}) ==")
        run_stress_k6(items_arg, STRESS_WARMUP_RATE, "warmup", "15s")  # 예열 (기록하지 않음)
        for rate in STRESS_RATES:
            stage = run_stress_stage(items_arg, rate)
            stages.append(stage)
            reason = stop_reason(stage)
            if reason:
                print(f"  -> {reason}: 여기서 멈춤")
                stage["stopped"] = reason
                break
            time.sleep(5)
    finally:
        stop_server(proc)
    sold = sql("select coalesce(sum(s.sold_quantity), 0), coalesce(sum(s.total_quantity), 0) from sale_item s "
               "join product p on p.product_id = s.product_id where p.title like '%한계 측정 %'")[0]
    (STRESS_RESULTS / "results.json").write_text(json.dumps(stages, ensure_ascii=False, indent=2), encoding="utf-8")
    table = summarize_stress(stages)
    (STRESS_RESULTS / "results.md").write_text(table, encoding="utf-8")
    print("\n" + table)
    print(f"판매 수량 합계 {sold[0]} / 재고 합계 {sold[1]}")


def run_stress_k6(items_arg, rate, name, duration=STRESS_STAGE_DURATION):
    summary = WORK / f"stress-{name}.json"
    env = dict(os.environ, K6_NO_USAGE_REPORT="true")
    # 기준을 넘어 k6 가 스스로 멈추면 종료 코드가 0 이 아니다. 요약 파일은 그래도 남으므로 check 하지 않는다
    subprocess.run([K6, "run", "--quiet", "-e", f"BASE_URL={BASE}", "-e", f"ITEMS={items_arg}",
                    "-e", f"TOKENS={WORK / 'tokens.json'}", "-e", f"SUMMARY={summary}", "-e", f"RATE={rate}",
                    "-e", f"DURATION={duration}", "-e", f"MAX_VUS={STRESS_MAX_VUS}",
                    str(HERE / "stress.js")], cwd=HERE, env=env, stdin=subprocess.DEVNULL)
    return json.loads(summary.read_text(encoding="utf-8"))


def run_stress_stage(items_arg, rate):
    pc, out = sample_pc()
    started = time.time()
    data = run_stress_k6(items_arg, rate, f"rate-{rate}")
    elapsed = time.time() - started
    cpu, mem = read_pc_samples(pc, out)
    m = data["metrics"]
    total = count(m, "http_reqs")
    duration_s = data["state"]["testRunDurationMs"] / 1000
    errors = count(m, "server_errors") + count(m, "no_response")

    def p95(group):
        values = m.get(f"http_req_duration{{group:{group}}}", {}).get("values", {})
        return round(values["p(95)"], 1) if "p(95)" in values else None

    stage = {
        "target_rps": rate,
        "actual_rps": round(total / duration_s, 1) if duration_s else 0,
        "requests": total,
        "p95_ms": round(m["http_req_duration"]["values"]["p(95)"], 1),
        "p99_ms": round(m["http_req_duration"]["values"]["p(99)"], 1),
        "p95_by_group": {g: p95(g) for g in ("list", "detail", "queue", "purchase", "misc")},
        "server_errors": count(m, "server_errors"),
        "no_response": count(m, "no_response"),
        "error_rate": round(100 * errors / total, 2) if total else 0,
        "dropped": count(m, "dropped_iterations"),
        "purchases": count(m, "purchase_success"),
        "cpu_avg": round(statistics.mean(cpu), 1) if cpu else None,
        "cpu_max": round(max(cpu), 1) if cpu else None,
        "mem_free_min_mb": round(min(mem)) if mem else None,
        "aborted": data["state"]["testRunDurationMs"] / 1000 < 25,
        "elapsed_s": round(elapsed, 1),
    }
    print(f"  목표 {rate} req/s: 실제 {stage['actual_rps']} req/s, p95 {stage['p95_ms']}ms, 오류 {stage['error_rate']}%, "
          f"못 보낸 요청 {stage['dropped']}, 구매 {stage['purchases']}, CPU 평균 {stage['cpu_avg']}% 최대 {stage['cpu_max']}%, "
          f"남은 메모리 최소 {stage['mem_free_min_mb']}MB")
    return stage


def stop_reason(stage):
    """다음 단계로 가지 않을 이유. PC 를 보호하려고 메모리가 부족해도 멈춘다"""
    if stage["aborted"]:
        return "k6 가 기준(오류 20% 또는 p95 10초)을 넘어 도중에 멈춤"
    if stage["error_rate"] >= 20:
        return f"오류 {stage['error_rate']}%"
    if stage["p95_ms"] >= 10000:
        return f"p95 {stage['p95_ms']}ms"
    if stage["dropped"] > stage["target_rps"] * 30 * 0.2:
        return "목표 요청의 20% 이상을 보내지 못함 (요청을 보낼 사용자가 모자람 = 응답이 너무 느림)"
    if stage["mem_free_min_mb"] is not None and stage["mem_free_min_mb"] < 500:
        return f"PC 남은 메모리 {stage['mem_free_min_mb']}MB"
    return None


def summarize_stress(stages):
    lines = [f"# k6 한계 측정 결과 ({datetime.now():%Y-%m-%d %H:%M})", "",
             f"조건: 방식 C, 대기열·알림 켬(입장 초당 50명), 굿즈 {STRESS_PRODUCTS}개 x 재고 {STRESS_STOCK}, 회원 {STRESS_MEMBERS}명. "
             "요청 비율: 목록 20% · 상세 40% · 대기열/구매 30% · 구매 내역/알림 10%. "
             f"단계마다 {STRESS_STAGE_DURATION}, k6 와 서버를 같은 PC 에서 실행, DB 연결 10개.", "",
             "| 목표 req/s | 실제 req/s | p95 | p99 | 오류율 (5xx+무응답) | 못 보낸 요청 | 구매 성공 | CPU 평균 / 최대 | 남은 메모리 최소 | 비고 |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    for s in stages:
        lines.append(f"| {s['target_rps']:,} | {s['actual_rps']:,} | {s['p95_ms']}ms | {s['p99_ms']}ms | {s['error_rate']}% | "
                     f"{s['dropped']:,} | {s['purchases']:,} | {s['cpu_avg']}% / {s['cpu_max']}% | {s['mem_free_min_mb']}MB | "
                     f"{s.get('stopped', '')} |")
    lines += ["", "## 요청 종류별 p95 (ms)", "", "| 목표 req/s | 목록 | 상세 | 대기열 | 구매 | 기타 |", "|---|---|---|---|---|---|"]
    for s in stages:
        g = s["p95_by_group"]
        lines.append(f"| {s['target_rps']:,} | {g['list']} | {g['detail']} | {g['queue']} | {g['purchase']} | {g['misc']} |")
    return "\n".join(lines) + "\n"


def main():
    if sys.argv[1:2] == ["stress"]:
        stress()
        return
    only, runs, user_levels = parse_args(sys.argv[1:])
    scale = user_levels is not None
    levels = user_levels or [MEMBERS]
    WORK.mkdir(exist_ok=True)
    RESULTS.mkdir(exist_ok=True)
    jar = build_jar()
    results = []
    seller_id = None
    for strategy in STRATEGIES:
        if only and strategy != only:
            continue
        print(f"\n== {STRATEGIES[strategy]} ({strategy}) ==")
        proc = start_server(jar, strategy)
        try:
            if seller_id is None:
                seller_id = prepare_members(max(levels))
            for users in levels:
                # 예열 (기록하지 않음). 서버가 막 켜지면 JIT 최적화 전이라 느려서, 측정 회차가 갈수록 빨라지지 않게 충분히 돌린다
                for warmup_no in range(1, WARMUPS + 1):
                    product_id, sale_item_id = create_goods(seller_id, f"{strategy} {users}명 예열 {warmup_no}")
                    run_k6(product_id, sale_item_id, f"{strategy}-{users}-warmup-{warmup_no}", users)
                    time.sleep(2)
                for run_no in range(1, runs + 1):
                    results.append(measure(strategy, run_no, seller_id, users))
                    time.sleep(2)
        finally:
            stop_server(proc)
    out = SCALE_RESULTS if scale else RESULTS
    out.mkdir(parents=True, exist_ok=True)
    (out / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    table, medians = summarize_scale(results, levels) if scale else summarize(results)
    (out / "results.md").write_text(table, encoding="utf-8")
    (out / "medians.json").write_text(json.dumps(medians, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n" + table)


if __name__ == "__main__":
    main()
