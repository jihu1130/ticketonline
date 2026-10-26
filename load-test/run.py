"""
k6 부하 테스트 실행기 (11단계): 방식 A / B / C 를 같은 조건으로 실행하고 결과 표를 만든다.

조건 (기획서 3-1, 3-2)
  재고 100개 굿즈(1인 1개)에 회원 1000명이 동시에 1개씩 구매 요청
  대기열·알림은 끈다 (구매 처리만 비교), DB 연결 수는 기본값(10)으로 같게
  방식마다 서버를 새로 띄우고, 기록하지 않는 예열 1번 뒤 3번 측정 -> 가운데 값(중앙값)

준비물: MySQL(localhost:3306), Redis(localhost:6379), k6, Java 21.
  DB 비밀번호는 src/main/resources/application-secret.yaml 에서 읽는다 (이 파일은 커밋하지 않음).
  부하 테스트 전용 DB ticketonline_load 를 쓴다 (개발 DB 와 섞이지 않게). Redis 는 3번 DB.

실행 (저장소 최상위에서):  py -3 load-test/run.py            # A, B, C 각 3번
                           py -3 load-test/run.py redis 1    # 방식 C 만 1번
결과: load-test/results/results.md, results.json
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
PORT = 8891
BASE = f"http://localhost:{PORT}"
DB = "ticketonline_load"
REDIS_DB = 3
MEMBERS = 1000
STOCK = 100
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


def start_server(jar, strategy):
    log = open(WORK / f"server-{strategy}.log", "w", encoding="utf-8")
    args = ["java", "-jar", str(jar),
            f"--server.port={PORT}",
            f"--app.purchase.strategy={strategy}",
            "--app.queue.enabled=false",
            "--app.notice.enabled=false",
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
            if call("GET", "/api/products")[0] == 200:
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


# ---------- 테스트 데이터 ----------

def prepare_members():
    """판매자 1명 + 구매자 1000명. 구매자는 같은 비밀번호 해시로 한 번에 넣고, 로그인해서 토큰을 모은다."""
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
    if have < MEMBERS:
        rows = ",".join(f"('load-user-{i}@test.com', '{pw_hash}', '구매자{i}', 'USER', now(6))"
                        for i in range(have, MEMBERS))
        sql(f"insert into member (email, password, name, role, created_at) values {rows}")

    def login(i):
        status, body = call("POST", "/api/members/login", {"email": f"load-user-{i}@test.com", "password": password})
        if status != 200:
            raise RuntimeError(f"로그인 실패 load-user-{i}: {status} {body}")
        return body["accessToken"]

    with ThreadPoolExecutor(16) as pool:
        tokens = list(pool.map(login, range(MEMBERS)))
    (WORK / "tokens.json").write_text(json.dumps(tokens), encoding="utf-8")
    seller_id = int(sql("select member_id from member where email = 'load-seller@test.com'")[0][0])
    print(f"구매자 {len(tokens)}명 토큰 준비 완료")
    return seller_id


def create_goods(seller_id, label):
    """판매 중인 굿즈(현장 수령, 1인 1개, 재고 100)를 SQL 로 바로 만든다 (등록 API 는 판매 시작 전만 허용)"""
    rows = sql(f"""
        insert into product (title, type, max_per_person, sale_start_at, sale_end_at, created_at, seller_id)
        values ('부하 테스트 {label}', 'GOODS', 1, now(6) - interval 1 hour, now(6) + interval 1 day, now(6), {seller_id});
        set @p = last_insert_id();
        insert into goods_detail (product_id, delivery_type, description) values (@p, 'PICKUP', 'k6');
        insert into sale_item (product_id, price, total_quantity, sold_quantity, version) values (@p, 10000, {STOCK}, 0, 0);
        select @p, last_insert_id();""")
    return int(rows[-1][0]), int(rows[-1][1])


# ---------- 실행과 집계 ----------

def run_k6(product_id, sale_item_id, name, vus=MEMBERS):
    summary = WORK / f"summary-{name}.json"
    env = dict(os.environ, K6_NO_USAGE_REPORT="true")
    subprocess.run([K6, "run", "--quiet",
                    "-e", f"BASE_URL={BASE}", "-e", f"PRODUCT_ID={product_id}", "-e", f"SALE_ITEM_ID={sale_item_id}",
                    "-e", f"TOKENS={WORK / 'tokens.json'}", "-e", f"SUMMARY={summary}", "-e", f"VUS={vus}",
                    str(HERE / "purchase.js")], cwd=HERE, env=env, check=True, stdin=subprocess.DEVNULL)
    return json.loads(summary.read_text(encoding="utf-8"))


def count(metrics, name):
    return int(metrics.get(name, {}).get("values", {}).get("count", 0))


def measure(strategy, run_no, seller_id):
    if strategy == "redis":
        subprocess.run([REDIS_CLI, "-n", str(REDIS_DB), "flushdb"], capture_output=True, check=True)
    product_id, sale_item_id = create_goods(seller_id, f"{strategy} {run_no}")
    data = run_k6(product_id, sale_item_id, f"{strategy}-{run_no}")
    m = data["metrics"]
    requests = count(m, "http_reqs{name:purchase}")
    duration = m["http_req_duration{name:purchase}"]["values"]
    # TPS = 구매 요청 수 / (출발부터 마지막 응답까지 걸린 시간)
    burst_ms = m["purchase_finished_at"]["values"]["max"]
    sold, total = map(int, sql(f"select sold_quantity, total_quantity from sale_item where sale_item_id = {sale_item_id}")[0])
    ordered, orders = map(int, sql(f"select coalesce(sum(quantity), 0), count(*) from product_order "
                                   f"where sale_item_id = {sale_item_id} and status = 'SUCCESS'")[0])
    result = {
        "strategy": strategy, "run": run_no, "requests": requests,
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
    print(f"  {STRATEGIES[strategy]} {run_no}회: 성공 {result['success']}, 매진 {result['sold_out']}, "
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


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    runs = int(sys.argv[2]) if len(sys.argv) > 2 else 3
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
                seller_id = prepare_members()
            product_id, sale_item_id = create_goods(seller_id, f"{strategy} 예열")
            run_k6(product_id, sale_item_id, f"{strategy}-warmup")  # 예열 (기록하지 않음)
            for run_no in range(1, runs + 1):
                results.append(measure(strategy, run_no, seller_id))
                time.sleep(2)
        finally:
            stop_server(proc)
    (RESULTS / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    table, medians = summarize(results)
    (RESULTS / "results.md").write_text(table, encoding="utf-8")
    (RESULTS / "medians.json").write_text(json.dumps(medians, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n" + table)


if __name__ == "__main__":
    main()
