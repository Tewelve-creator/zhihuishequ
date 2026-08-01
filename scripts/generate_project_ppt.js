const pptxgen = require('pptxgenjs');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = '智慧社区项目组';
pptx.subject = 'AI智慧社区服务平台项目介绍';
pptx.title = 'AI智慧社区服务平台';
pptx.company = '智慧社区项目组';
pptx.lang = 'zh-CN';
pptx.theme = {
  headFontFace: 'Microsoft YaHei',
  bodyFontFace: 'Microsoft YaHei',
  lang: 'zh-CN'
};
pptx.defineSlideMaster({
  title: 'MASTER',
  background: { color: 'F4F7F6' },
  objects: [
    { rect: { x: 0, y: 0, w: 13.333, h: 0.12, fill: { color: '16A085' }, line: { color: '16A085' } } },
    { text: { text: 'AI 智慧社区服务平台', options: { x: 0.55, y: 7.12, w: 4.2, h: 0.18, fontFace: 'Microsoft YaHei', fontSize: 8, color: '70807A', margin: 0 } } },
    { text: { text: '项目答辩', options: { x: 11.75, y: 7.12, w: 1.0, h: 0.18, fontFace: 'Microsoft YaHei', fontSize: 8, color: '70807A', align: 'right', margin: 0 } } }
  ],
  slideNumber: { x: 12.8, y: 7.1, color: '70807A', fontFace: 'Microsoft YaHei', fontSize: 8 }
});

const C = { green:'16A085', dark:'173B35', blue:'2878B5', ink:'18332E', muted:'667871', pale:'E8F3EF', white:'FFFFFF', orange:'F3A847', red:'D96459', line:'CCDAD5' };
const S = pptx.ShapeType;
function addTitle(slide, no, title, sub='') {
  slide.addText(String(no).padStart(2,'0'), {x:.58,y:.38,w:.55,h:.32,fontFace:'Aptos Display',fontSize:14,bold:true,color:C.green,margin:0});
  slide.addText(title, {x:1.15,y:.3,w:8.8,h:.48,fontSize:26,bold:true,color:C.ink,margin:0,breakLine:false});
  if(sub) slide.addText(sub,{x:9.15,y:.39,w:3.55,h:.3,fontSize:10,color:C.muted,align:'right',margin:0});
  slide.addShape(S.line,{x:.58,y:.92,w:12.15,h:0,line:{color:C.line,width:1}});
}
function card(slide,x,y,w,h,title,body,color=C.green) {
  slide.addShape(S.roundRect,{x,y,w,h,rectRadius:.06,fill:{color:C.white},line:{color:'DCE7E3',width:1},shadow:{type:'outer',color:'B8C8C2',opacity:.14,blur:1,angle:45,distance:1}});
  slide.addShape(S.rect,{x,y,w:.08,h,fill:{color},line:{color}});
  slide.addText(title,{x:x+.25,y:y+.2,w:w-.45,h:.32,fontSize:15,bold:true,color:C.ink,margin:0});
  slide.addText(body,{x:x+.25,y:y+.65,w:w-.45,h:h-.82,fontSize:10.5,color:C.muted,breakLine:false,margin:0,breakLineOnOverflow:false,fit:'shrink',valign:'top',paraSpaceAfterPt:6});
}
function pill(slide,x,y,w,text,color=C.green){
  slide.addShape(S.roundRect,{x,y,w,h:.36,rectRadius:.08,fill:{color},line:{color}});
  slide.addText(text,{x:x+.08,y:y+.08,w:w-.16,h:.18,fontSize:9,bold:true,color:C.white,align:'center',margin:0,fit:'shrink'});
}

// 1 封面
{
  const slide=pptx.addSlide();
  slide.background={color:'123B35'};
  slide.addShape(S.rect,{x:0,y:0,w:13.333,h:7.5,fill:{color:'123B35'},line:{color:'123B35'}});
  slide.addShape(S.arc,{x:8.65,y:-1.1,w:5.4,h:5.4,adjustPoint:.25,rotate:28,fill:{color:'1A554B',transparency:20},line:{color:'2C7568',transparency:40,width:2}});
  slide.addShape(S.arc,{x:9.75,y:3.35,w:4.5,h:4.5,rotate:205,fill:{color:'2878B5',transparency:55},line:{color:'51A9D8',transparency:45,width:2}});
  slide.addText('AI 智慧社区\n服务平台',{x:.75,y:1.45,w:7.4,h:1.65,fontSize:38,bold:true,color:C.white,margin:0,breakLine:false,fit:'shrink'});
  slide.addText('多租户 SaaS · 微服务架构 · 智能社区服务',{x:.78,y:3.35,w:6.9,h:.42,fontSize:17,color:'BDE3D9',margin:0});
  slide.addShape(S.line,{x:.78,y:4.05,w:2.05,h:0,line:{color:'4BC5AA',width:4,beginArrowType:'none'}});
  slide.addText('项目背景｜技术架构｜功能模块｜个人职责',{x:.78,y:4.35,w:6.8,h:.32,fontSize:12,color:'D6E9E4',margin:0});
  slide.addText('项目答辩汇报',{x:.78,y:6.42,w:2.2,h:.3,fontSize:12,bold:true,color:C.white,margin:0});
  slide.addText('2026',{x:11.55,y:6.42,w:.9,h:.3,fontSize:12,color:'BDE3D9',align:'right',margin:0});
}

// 2 背景与价值
{
  const slide=pptx.addSlide('MASTER'); addTitle(slide,2,'项目背景与价值','从线下分散到线上协同');
  slide.addText('传统社区服务的核心痛点',{x:.65,y:1.2,w:4.2,h:.4,fontSize:20,bold:true,color:C.ink,margin:0});
  const pains=[['01','渠道分散','电话、微信群、纸质记录并行'],['02','流程不透明','报修进度难追踪，责任不清晰'],['03','数据割裂','房屋、住户、账单和设备独立管理'],['04','扩展困难','单社区系统难支撑多社区运营']];
  pains.forEach((p,i)=>{const y=1.83+i*1.02; slide.addText(p[0],{x:.7,y,w:.45,h:.28,fontSize:12,bold:true,color:C.green,margin:0});slide.addText(p[1],{x:1.25,y:y-.04,w:1.3,h:.3,fontSize:14,bold:true,color:C.ink,margin:0});slide.addText(p[2],{x:2.55,y:y-.02,w:2.65,h:.3,fontSize:10.5,color:C.muted,margin:0,fit:'shrink'});slide.addShape(S.line,{x:.7,y:y+.48,w:4.45,h:0,line:{color:'DCE6E2',width:1}})});
  slide.addShape(S.chevron,{x:5.4,y:3.05,w:.75,h:.8,fill:{color:C.green,transparency:8},line:{color:C.green}});
  slide.addText('平台价值',{x:6.45,y:1.2,w:2.5,h:.4,fontSize:20,bold:true,color:C.ink,margin:0});
  card(slide,6.4,1.82,2.85,1.55,'居民端','随时办理报修、缴费、访客登记，实时查看进度。',C.green);
  card(slide,9.55,1.82,2.85,1.55,'物业端','统一工单、住户、巡检与设备管理，提高协同效率。',C.blue);
  card(slide,6.4,3.68,2.85,1.55,'运营端','多社区统一运营，按租户套餐灵活开放功能。',C.orange);
  card(slide,9.55,3.68,2.85,1.55,'管理决策','通过数据驾驶舱掌握工单、缴费及运营趋势。',C.red);
  slide.addText('目标：实现社区业务线上化、流程化、数据化与智能化',{x:6.45,y:5.72,w:5.9,h:.65,fontSize:17,bold:true,color:C.dark,align:'center',valign:'mid',fill:{color:C.pale},margin:.1});
}

// 3 技术架构
{
  const slide=pptx.addSlide('MASTER'); addTitle(slide,3,'技术架构','前后端分离 · 多端协同 · 微服务');
  const layers=[
    {y:1.25,h:.82,name:'用户端',color:'DDEFE9',items:['运营管理端 Vue 3','物业工作端 Vue 3','居民移动端 UniApp']},
    {y:2.32,h:.72,name:'接入层',color:'DCEAF4',items:['Spring Cloud Gateway','Sa-Token 认证','动态路由 / 权限校验']},
    {y:3.40,h:1.05,name:'服务层',color:'E7F1EE',items:['Auth 用户认证','Repair 社区业务','AI 智能管家','Order 订单','Stats 统计','SaaS 租户']},
    {y:4.82,h:.72,name:'治理层',color:'F6EEDC',items:['Nacos 注册发现','OpenFeign 调用','Sentinel 限流降级']},
    {y:5.91,h:.72,name:'数据层',color:'EEE9F4',items:['MySQL 8','Redis','MinIO 对象存储','MyBatis-Plus']}
  ];
  layers.forEach(l=>{
    slide.addShape(S.roundRect,{x:.65,y:l.y,w:12.0,h:l.h,rectRadius:.04,fill:{color:l.color},line:{color:'C9D8D3',width:.8}});
    slide.addText(l.name,{x:.88,y:l.y+.22,w:1.08,h:.3,fontSize:14,bold:true,color:C.dark,margin:0});
    const start=2.25, gap=.16, avail=9.95, iw=(avail-gap*(l.items.length-1))/l.items.length;
    l.items.forEach((it,i)=>{slide.addShape(S.roundRect,{x:start+i*(iw+gap),y:l.y+.16,w:iw,h:l.h-.32,rectRadius:.04,fill:{color:C.white,transparency:3},line:{color:'C2D4CE',width:.7}});slide.addText(it,{x:start+i*(iw+gap)+.05,y:l.y+.29,w:iw-.1,h:.25,fontSize:10.5,bold:true,color:C.ink,align:'center',margin:0,fit:'shrink'});});
  });
}

// 4 功能架构
{
  const slide=pptx.addSlide('MASTER'); addTitle(slide,4,'功能架构','三端一体化业务平台');
  card(slide,.65,1.28,3.75,4.85,'居民移动端','• 手机号登录与房屋绑定\n• 在线报修与进度查询\n• 物业账单查询与缴费\n• 访客登记与车辆管理\n• 公告活动、社区商户\n• AI 智能管家与知识问答',C.green);
  card(slide,4.78,1.28,3.75,4.85,'物业工作端','• 房屋、住户及车辆管理\n• 迁入迁出审批\n• 工单受理、派单与处理\n• 巡检任务与设备维保\n• 收费账单与收款通知\n• 公告活动与知识库管理',C.blue);
  card(slide,8.91,1.28,3.75,4.85,'平台运营端','• 数据监控驾驶舱\n• 多社区与租户管理\n• 套餐及功能权限配置\n• 订单和财务管理\n• 组织账号与角色权限\n• 统计报表与数据导出',C.orange);
  slide.addText('核心能力',{x:.65,y:6.42,w:1.0,h:.3,fontSize:12,bold:true,color:C.ink,margin:0});
  ['多租户隔离','身份权限','工单闭环','账单缴费','巡检维保','AI知识库','数据可视化'].forEach((v,i)=>pill(slide,1.75+i*1.53,6.36,1.32,v,i===6?C.blue:C.green));
}

// 5 个人职责
{
  const slide=pptx.addSlide('MASTER'); addTitle(slide,5,'个人职责','Web 管理后台前端开发');
  slide.addShape(S.roundRect,{x:.68,y:1.28,w:3.2,h:4.98,rectRadius:.04,fill:{color:C.dark},line:{color:C.dark}});
  slide.addText('我的角色',{x:1.0,y:1.72,w:1.7,h:.35,fontSize:14,bold:true,color:'86D7C4',margin:0});
  slide.addText('Web 管理后台\n前端开发',{x:1.0,y:2.25,w:2.45,h:1.15,fontSize:27,bold:true,color:C.white,margin:0,fit:'shrink'});
  slide.addText('负责物业工作端与平台运营端的页面开发、接口联调和通用能力建设。',{x:1.0,y:3.78,w:2.3,h:1.05,fontSize:12,color:'D3E6E0',margin:0,breakLine:false,fit:'shrink'});
  pill(slide,1.0,5.35,1.1,'Vue 3',C.green); pill(slide,2.25,5.35,1.1,'TypeScript',C.blue);
  slide.addText('主要交付模块',{x:4.35,y:1.35,w:2.0,h:.36,fontSize:19,bold:true,color:C.ink,margin:0});
  const mods=[['后台基础','登录、整体布局、路由与状态管理'],['物业业务','工作台、用户住户、报修工单、公告活动'],['SaaS运营','租户套餐、功能权限、订单财务'],['数据能力','社区驾驶舱、营收可视化、Excel导出']];
  mods.forEach((m,i)=>{const y=1.95+i*1.02;slide.addShape(S.ellipse,{x:4.35,y:y+.02,w:.38,h:.38,fill:{color:i%2?C.blue:C.green},line:{color:i%2?C.blue:C.green}});slide.addText(String(i+1),{x:4.35,y:y+.11,w:.38,h:.15,fontSize:9,bold:true,color:C.white,align:'center',margin:0});slide.addText(m[0],{x:4.95,y,w:1.45,h:.3,fontSize:14,bold:true,color:C.ink,margin:0});slide.addText(m[1],{x:6.45,y:y+.02,w:5.4,h:.3,fontSize:11,color:C.muted,margin:0});});
  slide.addShape(S.roundRect,{x:4.35,y:6.05,w:7.95,h:.58,rectRadius:.04,fill:{color:C.pale},line:{color:'C8DED6'}});
  slide.addText('职责目标：让复杂的社区业务在后台中可操作、可追踪、可统计',{x:4.6,y:6.23,w:7.45,h:.22,fontSize:12,bold:true,color:C.dark,align:'center',margin:0});
}

// 6 流程
{
  const slide=pptx.addSlide('MASTER'); addTitle(slide,6,'核心业务流程','报修工单闭环');
  slide.addText('居民提交问题后，物业与维修人员协同处理，居民全程可查看状态。',{x:.7,y:1.2,w:8,h:.35,fontSize:14,color:C.muted,margin:0});
  const steps=[['1','提交报修','填写描述\n上传图片'],['2','物业受理','核对信息\n判断类型'],['3','分配人员','选择维修人员\n完成派单'],['4','接单处理','现场维修\n更新进度'],['5','完成工单','上传结果\n记录过程'],['6','居民评价','查看结果\n服务反馈']];
  steps.forEach((s,i)=>{const x=.65+i*2.08; const col=i<2?C.green:(i<4?C.blue:C.orange);slide.addShape(S.ellipse,{x:x+.5,y:2.08,w:.7,h:.7,fill:{color:col},line:{color:col}});slide.addText(s[0],{x:x+.5,y:2.27,w:.7,h:.2,fontSize:13,bold:true,color:C.white,align:'center',margin:0});if(i<5)slide.addShape(S.chevron,{x:x+1.45,y:2.28,w:.38,h:.3,fill:{color:'AFC3BC'},line:{color:'AFC3BC'}});slide.addText(s[1],{x:x,y:3.05,w:1.7,h:.3,fontSize:14,bold:true,color:C.ink,align:'center',margin:0});slide.addText(s[2],{x:x,y:3.58,w:1.7,h:.64,fontSize:10.5,color:C.muted,align:'center',margin:0,fit:'shrink'});});
  slide.addShape(S.roundRect,{x:1.0,y:5.05,w:11.3,h:1.0,rectRadius:.05,fill:{color:C.white},line:{color:'C9DCD5'}});
  slide.addText('业务价值',{x:1.25,y:5.37,w:1.1,h:.3,fontSize:14,bold:true,color:C.green,margin:0});
  slide.addText('状态可追踪', {x:2.65,y:5.37,w:1.45,h:.3,fontSize:13,bold:true,color:C.ink,align:'center',margin:0});
  slide.addText('责任可定位', {x:5.05,y:5.37,w:1.45,h:.3,fontSize:13,bold:true,color:C.ink,align:'center',margin:0});
  slide.addText('过程可留痕', {x:7.45,y:5.37,w:1.45,h:.3,fontSize:13,bold:true,color:C.ink,align:'center',margin:0});
  slide.addText('结果可评价', {x:9.85,y:5.37,w:1.45,h:.3,fontSize:13,bold:true,color:C.ink,align:'center',margin:0});
}

// 7 七项功能
{
  const slide=pptx.addSlide('MASTER'); addTitle(slide,7,'七项主要功能','技术落地与业务成果');
  const items=[
    ['01','房屋住户管理','Vue 3 + Element Plus','信息维护、查询与迁入迁出审批'],
    ['02','报修工单管理','Vue 3 + Axios','受理、派单、处理与状态跟踪'],
    ['03','物业缴费管理','Element Plus + API','收费项目、账单和缴费记录'],
    ['04','登录权限控制','Router + Pinia + Sa-Token','角色区分、登录拦截与动态菜单'],
    ['05','数据驾驶舱','Vue 3 + ECharts','工单、缴费和社区运营可视化'],
    ['06','SaaS租户套餐','功能码 + 动态菜单','按套餐开放功能并隔离社区数据'],
    ['07','公告图片管理','Axios + MinIO','公告发布、活动管理与图片上传']
  ];
  items.forEach((it,i)=>{const left=i<4;const row=left?i:i-4;const x=left?.65:6.72;const y=1.25+row*1.25;const w=left?5.7:5.95;const col=i%3===0?C.green:(i%3===1?C.blue:C.orange);slide.addShape(S.roundRect,{x,y,w,h:1.0,rectRadius:.04,fill:{color:C.white},line:{color:'D7E3DF'},shadow:{type:'outer',color:'B9C8C3',opacity:.1,blur:1,angle:45,distance:1}});slide.addText(it[0],{x:x+.18,y:y+.22,w:.52,h:.28,fontSize:13,bold:true,color:col,margin:0});slide.addText(it[1],{x:x+.83,y:y+.15,w:1.75,h:.28,fontSize:13.5,bold:true,color:C.ink,margin:0,fit:'shrink'});slide.addText(it[2],{x:x+2.65,y:y+.16,w:w-2.85,h:.25,fontSize:9.5,bold:true,color:col,margin:0,fit:'shrink'});slide.addText(it[3],{x:x+.83,y:y+.55,w:w-1.05,h:.25,fontSize:10.5,color:C.muted,margin:0,fit:'shrink'});});
  slide.addShape(S.roundRect,{x:6.72,y:5.2,w:5.95,h:1.05,rectRadius:.04,fill:{color:C.dark},line:{color:C.dark}});
  slide.addText('谢谢观看',{x:7.05,y:5.48,w:2.0,h:.4,fontSize:22,bold:true,color:C.white,margin:0});
  slide.addText('Q & A',{x:10.65,y:5.52,w:1.4,h:.35,fontSize:16,bold:true,color:'86D7C4',align:'right',margin:0});
}

pptx.writeFile({ fileName: 'AI智慧社区项目答辩-7页.pptx' });
