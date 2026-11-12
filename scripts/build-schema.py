"""Generate an offline SVG of the actual Flyway v003 schema (no network or JS)."""
from pathlib import Path
from html import escape

root=Path(__file__).resolve().parents[1]
tables={
 'app_user':(40,100,'#007EB5',['PK id : BIGINT (1, 2, 3…)','legacy_uuid : UUID UNIQUE','login_normalized : UNIQUE','display_name, role, enabled','password_hash, salt, iterations']),
 'auction':(490,100,'#8A3E90',['PK id : UUID','auction_number : BIGINT UNIQUE','FK owner_id, leader_id → app_user','title, description, category, status','current_price, bid_count, version','start_at, end_at, photo_version']),
 'bid':(940,100,'#D42B78',['PK id : UUID','FK auction_id → auction','FK bidder_id → app_user','sequence_no, amount, accepted_at','UNIQUE bidder_id + command_request_id']),
 'security_audit':(40,420,'#009A98',['PK id : UUID','FK actor_id → app_user','FK target_user_id → app_user','action, occurred_at, details']),
 'auction_photo':(490,420,'#65A524',['PK / FK auction_id → auction','content : BYTEA (do 2 MB)','version, sha256, width, height','created_at']),
 'auction_event':(940,420,'#EB8A18',['PK id : UUID','FK auction_id → auction','FK actor_id → app_user','auction_version, event_type','occurred_at, details : JSONB']),
 'diagnostic_log':(40,730,'#007EB5',['PK id : UUID','FK reported_by_user_id → app_user','origin : CLIENT / SERVER','event_type, message, received_at','auction_id : identyfikator zdarzenia']),
 'auction_report':(490,730,'#8A3E90',['PK / FK auction_id → auction','filename : UNIQUE','sha256, bytes, generated_at','Treść .txt: katalog reports/ serwera']),
 'command_result':(940,730,'#D42B78',['PK user_id + request_id','FK user_id → app_user','operation, input_digest (HMAC)','result : JSONB, created_at'])}
parts=['<svg xmlns="http://www.w3.org/2000/svg" width="1360" height="1060" viewBox="0 0 1360 1060">','<defs><marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0 0 L10 5 L0 10" fill="#9BAAB9"/></marker></defs>','<rect width="1360" height="1060" fill="#F6F5FA"/>','<g font-family="Segoe UI,Arial,sans-serif" fill="#14243A">','<text x="40" y="45" font-size="27" font-weight="700">BidRoom · schemat PostgreSQL 1.1</text>','<text x="40" y="73" font-size="14" fill="#516176">Strzałka prowadzi od tabeli nadrzędnej do zależnej. Pełne relacje FK podano w kartach.</text>']
paths=['M400 180 H490','M850 180 H940','M220 330 V420','M400 255 H447 V820 H400','M670 355 V420','M850 275 H900 V500 H940','M850 300 H875 V705 H670 V730','M850 310 H915 V800 H940']
for path in paths:parts.append(f'<path d="{path}" fill="none" stroke="#9BAAB9" stroke-width="2" marker-end="url(#arrow)"/>')
for name,(x,y,color,rows) in tables.items():
 h=73+len(rows)*28
 parts += [f'<rect x="{x}" y="{y}" width="360" height="{h}" rx="12" fill="white" stroke="#DFE5ED"/>',f'<rect x="{x}" y="{y}" width="360" height="8" rx="4" fill="{color}"/>',f'<text x="{x+20}" y="{y+42}" font-size="22" font-weight="700" fill="{color}">{name}</text>']
 for i,row in enumerate(rows):parts.append(f'<text x="{x+20}" y="{y+76+i*28}" font-size="14">{escape(row)}</text>')
parts+=['<text x="40" y="1030" font-size="14" fill="#516176">PK — klucz główny · FK — klucz obcy · UUID aukcji/polecenia zachowuje tożsamość; konta mają proste numery.</text>','</g></svg>']
(root/'docs/database-schema.svg').write_text('\n'.join(parts),encoding='utf-8')
print('docs/database-schema.svg')
