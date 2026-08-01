const pptxgen = require('pptxgenjs');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = '智慧社区项目组';
pptx.title = '个人职责';
pptx.subject = 'Web管理后台前端开发个人职责';
pptx.lang = 'zh-CN';
pptx.theme = { headFontFace: 'Microsoft YaHei', bodyFontFace: 'Microsoft YaHei', lang: 'zh-CN' };

const slide = pptx.addSlide();
slide.background = { color: 'F4F7F6' };
const S = pptx.ShapeType;
const C = { green:'14967F', dark:'173B35', blue:'2878B5', orange:'E89A35', ink:'1B312D', muted:'60736D', white:'FFFFFF', line:'D6E2DE', pale:'E5F2EE' };

slide.addShape(S.rect,{x:0,y:0,w:13.333,h:.12,fill:{color:C.green},line:{color:C.green}});
slide.addText('05',{x:.58,y:.39,w:.48,h:.28,fontSize:13,bold:true,color:C.green,margin:0});
slide.addText('个人职责',{x:1.14,y:.28,w:3,h:.5,fontSize:26,bold:true,color:C.ink,margin:0});
slide.addText('Web 管理后台前端开发',{x:9.35,y:.41,w:3.35,h:.28,fontSize:11,bold:true,color:C.muted,align:'right',margin:0});
slide.addShape(S.line,{x:.58,y:.94,w:12.12,h:0,line:{color:C.line,width:1}});

// Role banner
slide.addShape(S.roundRect,{x:.58,y:1.16,w:12.12,h:.72,rectRadius:.04,fill:{color:C.dark},line:{color:C.dark}});
slide.addText('我的角色',{x:.88,y:1.36,w:1.05,h:.25,fontSize:11,bold:true,color:'86D7C4',margin:0});
slide.addText('负责物业工作端与平台运营管理端的页面开发、接口联调及通用能力建设',{x:2.05,y:1.31,w:8.7,h:.32,fontSize:15,bold:true,color:C.white,margin:0,fit:'shrink'});
slide.addText('Vue 3 · TypeScript',{x:10.75,y:1.37,w:1.55,h:.2,fontSize:9.5,bold:true,color:'CFEAE3',align:'right',margin:0});

function sectionBox(x,y,w,h,title,color) {
  slide.addShape(S.roundRect,{x,y,w,h,rectRadius:.04,fill:{color:C.white},line:{color:'D7E3DF',width:1},shadow:{type:'outer',color:'AFBDB8',opacity:.1,blur:1,angle:45,distance:1}});
  slide.addShape(S.rect,{x,y,w:.08,h,fill:{color},line:{color}});
  slide.addText(title,{x:x+.28,y:y+.2,w:w-.5,h:.3,fontSize:16,bold:true,color:C.ink,margin:0});
}

// Modules
sectionBox(.58,2.12,4.28,4.68,'主要负责模块',C.green);
const groups=[
  ['后台基础',['登录及整体页面布局','角色权限及动态菜单','Excel 数据导出']],
  ['物业业务',['物业工作台','用户、房屋和住户管理','报修工单、公告与活动管理']],
  ['平台运营',['SaaS 租户与套餐权限','订单和财务管理','社区驾驶舱与营收可视化']]
];
groups.forEach((g,i)=>{
  const y=2.75+i*1.27;
  const color=i===0?C.green:(i===1?C.blue:C.orange);
  slide.addShape(S.roundRect,{x:.92,y,w:1.03,h:.4,rectRadius:.05,fill:{color},line:{color}});
  slide.addText(g[0],{x:.98,y:y+.11,w:.91,h:.18,fontSize:9.5,bold:true,color:C.white,align:'center',margin:0,fit:'shrink'});
  g[1].forEach((v,j)=>{
    slide.addShape(S.ellipse,{x:2.18,y:y+j*.35+.075,w:.09,h:.09,fill:{color:C.white},line:{color,width:1.1}});
    slide.addText(v,{x:2.37,y:y+j*.35,w:2.1,h:.25,fontSize:10.2,color:C.ink,margin:0,fit:'shrink'});
  });
});
slide.addShape(S.roundRect,{x:.92,y:6.25,w:3.55,h:.3,rectRadius:.04,fill:{color:C.pale},line:{color:'C9DDD6'}});
slide.addText('覆盖页面开发、状态管理、权限控制与数据展示',{x:1.02,y:6.34,w:3.35,h:.13,fontSize:8.5,bold:true,color:C.dark,align:'center',margin:0,fit:'shrink'});

// Repair flow
sectionBox(5.12,2.12,7.58,2.36,'报修工单业务流程',C.blue);
slide.addText('居民提交报修及图片后，物业完成受理派单，维修人员处理并反馈结果，最终由居民评价。',{x:5.42,y:2.63,w:6.9,h:.33,fontSize:9.8,color:C.muted,margin:0,fit:'shrink'});
const repair=['居民提交','物业受理','分配人员','维修接单','现场处理','完成工单','居民评价'];
repair.forEach((v,i)=>{
  const x=5.39+i*.98;
  const color=i<2?C.green:(i<5?C.blue:C.orange);
  slide.addShape(S.ellipse,{x:x+.22,y:3.16,w:.38,h:.38,fill:{color},line:{color}});
  slide.addText(String(i+1),{x:x+.22,y:3.27,w:.38,h:.12,fontSize:8,bold:true,color:C.white,align:'center',margin:0});
  if(i<repair.length-1) slide.addShape(S.chevron,{x:x+.7,y:3.25,w:.18,h:.19,fill:{color:'ABC0B9'},line:{color:'ABC0B9'}});
  slide.addText(v,{x:x,y:3.7,w:.82,h:.22,fontSize:8.8,bold:true,color:C.ink,align:'center',margin:0,fit:'shrink'});
});

// SaaS flow
sectionBox(5.12,4.72,7.58,2.08,'SaaS 租户业务流程',C.orange);
const saas=[['创建租户','平台录入社区'],['配置套餐','选择功能与期限'],['租户登录','识别租户身份'],['加载能力','数据隔离与动态菜单'],['到期限制','前端与网关双重控制']];
saas.forEach((v,i)=>{
  const x=5.42+i*1.38;
  slide.addShape(S.roundRect,{x,y:5.42,w:1.16,h:.72,rectRadius:.04,fill:{color:i===4?'FFF0DE':'F0F6F4'},line:{color:i===4?C.orange:'BFD8D1',width:1}});
  slide.addText(v[0],{x:x+.08,y:5.56,w:1,h:.2,fontSize:10.2,bold:true,color:i===4?C.orange:C.dark,align:'center',margin:0,fit:'shrink'});
  slide.addText(v[1],{x:x+.06,y:5.84,w:1.04,h:.16,fontSize:7.7,color:C.muted,align:'center',margin:0,fit:'shrink'});
  if(i<saas.length-1) slide.addShape(S.chevron,{x:x+1.2,y:5.68,w:.14,h:.2,fill:{color:'AFC2BC'},line:{color:'AFC2BC'}});
});
slide.addText('核心机制：租户数据隔离 + 套餐功能码 + 前后端权限校验',{x:6.2,y:6.35,w:5.4,h:.2,fontSize:9.2,bold:true,color:C.muted,align:'center',margin:0});

slide.addText('个人贡献：将复杂社区业务转化为可操作、可追踪、可统计的管理界面',{x:3.23,y:7.1,w:6.9,h:.2,fontSize:9.5,bold:true,color:C.muted,align:'center',margin:0});

pptx.writeFile({ fileName: '个人职责-模块与业务流程.pptx' });
