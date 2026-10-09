import {ADMIN_CATALOG} from './admin.catalog.js';

// The caller authorizes the original operation before exposing its picker.
export async function adminLookup(input,db,ErrorType,loadPolicy) {
  const operation=ADMIN_CATALOG.find(x=>x.action===input.operation);
  const field=operation?.fields.find(x=>x.key===input.field);
  if(!field?.lookup)throw new ErrorType('검색할 수 없는 입력란입니다.');
  const search=String(input.search??'').trim();
  if(search.length>80)throw new ErrorType('검색어는 80자 이내로 입력하세요.');
  const requested=Number(input.page??0),size=Number(input.pageSize??8);
  if(!Number.isSafeInteger(requested)||requested<0||requested>100000||!Number.isSafeInteger(size)||size<1||size>10)throw new ErrorType('목록 페이지가 올바르지 않습니다.');
  const kind=field.lookup;
  if(kind==='rewards'||kind==='deliveries') {
    const policy=await loadPolicy(db,kind);
    const updated=(await db.query('SELECT updated_at FROM economy_settings WHERE key=$1',[kind])).rows[0]?.updated_at??null;
    const entries=kind==='rewards'?Object.entries(policy).map(([id,v])=>({id,...v})):policy;
    const found=entries.filter(v=>[v.id,v.title].some(x=>String(x??'').toLocaleLowerCase().includes(search.toLocaleLowerCase()))).sort((a,b)=>a.id.localeCompare(b.id));
    const page=Math.min(requested,Math.max(0,Math.ceil(found.length/size)-1));
    return {lookup:true,kind,page,pageSize:size,total:found.length,hasMore:(page+1)*size<found.length,rows:found.slice(page*size,(page+1)*size).map(v=>({value:v.id,occurredAt:updated,playerName:'공통 정책',label:kind==='rewards'?'보상 '+v.amount+'원':v.title}))};
  }
  const sources={
    players:`SELECT p.id::text AS value,p.created_at AS "occurredAt",COALESCE(p.username,'이름 미등록') AS "playerName",p.id::text AS "playerId",COALESCE(w.balance,0) AS balance,'플레이어 등록' AS label FROM players p LEFT JOIN wallets w ON w.player_id=p.id`,
    contracts:`SELECT c.id::text AS value,c.accepted_at AS "occurredAt",COALESCE(p.username,'이름 미등록') AS "playerName",c.player_id::text AS "playerId",COALESCE(c.definition->>'title',c.template_id) AS label FROM delivery_contracts c JOIN players p ON p.id=c.player_id WHERE c.status='active'`,
    mail:`SELECT m.id::text AS value,m.created_at AS "occurredAt",COALESCE(p.username,'이름 미등록') AS "playerName",m.player_id::text AS "playerId",m.title AS label FROM player_mail m JOIN players p ON p.id=m.player_id WHERE NOT m.is_claimed AND NOT m.is_cancelled`,
    projects:`SELECT id::text AS value,created_at AS "occurredAt",'공동 프로젝트' AS "playerName",name AS label FROM invest_projects ${['project_refund','project_complete','project_save'].includes(input.operation)?"WHERE status='active'":''}`,
    events:`SELECT id::text AS value,created_at AS "occurredAt",'서버 이벤트' AS "playerName",name AS label FROM events ${input.operation==='event_end'?"WHERE status<>'ended'":''}`,
    purchases:`SELECT r.player_id::text||'/'||r.request_id::text AS value,r.created_at AS "occurredAt",COALESCE(p.username,'이름 미등록') AS "playerName",r.player_id::text AS "playerId",r.item_id||' × '||r.quantity::text||' · '||COALESCE(r.result->>'totalPrice','?')||'원' AS label FROM shop_trade_receipts r JOIN players p ON p.id=r.player_id WHERE r.transaction_type='buy' AND NOT EXISTS(SELECT 1 FROM admin_refunds f WHERE f.source_id=r.player_id::text||'/'||r.request_id::text)`
  };
  if(!sources[kind])throw new ErrorType('지원하지 않는 검색 분류입니다.');
  // Literal search, bound values; no user-controlled SQL identifiers.
  const pattern='%'+search.replace(/[\\%_]/g,'\\$&')+'%';
  const source=`(${sources[kind]}) source WHERE value ILIKE $1 OR "playerName" ILIKE $1 OR label ILIKE $1 ${kind==='purchases'?"OR split_part(value,'/',2) ILIKE $1":''}`;
  const total=Number((await db.query('SELECT COUNT(*) AS n FROM '+source,[pattern])).rows[0].n);
  const page=Math.min(requested,Math.max(0,Math.ceil(total/size)-1));
  const rows=(await db.query('SELECT * FROM '+source+' ORDER BY "occurredAt" DESC,value LIMIT $2 OFFSET $3',[pattern,size,page*size])).rows;
  return {lookup:true,kind,page,pageSize:size,total,hasMore:(page+1)*size<total,rows};
}
