const pptxgen = require('pptxgenjs');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = '智慧社区项目组';
pptx.title = '功能架构';
pptx.subject = 'AI智慧社区功能架构';
pptx.lang = 'zh-CN';
pptx.theme = { headFontFace: 'Microsoft YaHei', bodyFontFace: 'Microsoft YaHei', lang: 'zh-CN' };

const slide = pptx.addSlide();
slide.background = { color: 'F4F7F6' };
const S = pptx.ShapeType;
const C = { green:'14967F', dark:'173B35', blue:'2878B5', orange:'E89A35', ink:'1B312D', muted:'60736D', white:'FFFFFF', line:'D6E2DE' };

slide.addShape(S.rect,{x:0,y:0,w:13.333,h:.12,fill:{color:C.green},line:{color:C.green}});
slide.addText('04',{x:.6,y:.4,w:.48,h:.28,fontSize:13,bold:true,color:C.green,margin:0});
slide.addText('功能架构',{x:1.15,y:.29,w:3.0,h:.5,fontSize:26,bold:true,color:C.ink,margin:0});
slide.addText('居民端 · 物业端 · 平台运营端',{x:9.25,y:.42,w:3.45,h:.28,fontSize:10.5,color:C.muted,align:'right',margin:0});
slide.addShape(S.line,{x:.6,y:.95,w:12.1,h:0,line:{color:C.line,width:1}});

function column(x, title, subtitle, color, items) {
  slide.addShape(S.roundRect,{x,y:1.26,w:3.85,h:5.55,rectRadius:.05,fill:{color:C.white},line:{color:'D8E4E0',width:1},shadow:{type:'outer',color:'AEBDB8',opacity:.13,blur:1,angle:45,distance:1}});
  slide.addShape(S.roundRect,{x,y:1.26,w:3.85,h:.9,rectRadius:.05,fill:{color},line:{color}});
  slide.addText(title,{x:x+.28,y:1.46,w:2.45,h:.35,fontSize:20,bold:true,color:C.white,margin:0,fit:'shrink'});
  slide.addText(subtitle,{x:x+2.58,y:1.53,w:.95,h:.22,fontSize:9.5,bold:true,color:'E8F7F3',align:'right',margin:0,fit:'shrink'});
  const startY=2.43;
  const rowH=4.05/items.length;
  items.forEach((item,i)=>{
    const y=startY+i*rowH;
    slide.addShape(S.ellipse,{x:x+.31,y:y+.08,w:.11,h:.11,fill:{color:C.white},line:{color,width:1.25}});
    slide.addText(item,{x:x+.55,y,w:3.0,h:rowH-.03,fontSize:11.1,color:C.ink,margin:0,fit:'shrink',valign:'mid'});
  });
}

column(.55,'居民移动端','便民服务',C.green,[
  '手机号登录与用户认证',
  '房屋绑定与房屋切换',
  '在线报修与进度查询',
  '物业账单查询与缴费',
  '社区公告与活动查看',
  '访客登记',
  '车辆管理与智慧停车',
  '社区商户与便民服务',
  '失物招领与邻里倾诉',
  'AI 智能管家与知识问答'
]);

column(4.74,'物业工作端','业务协同',C.blue,[
  '工作台数据看板',
  '物业账号管理',
  '房屋与住户管理',
  '迁入迁出审批',
  '报修工单受理、派单与处理',
  '物业收费与账单管理',
  '临时访客管理',
  '社区活动、公告与商户管理',
  '巡检任务与巡检点位管理',
  '设备台账与设备维保',
  'AI 知识库管理'
]);

column(8.93,'平台运营端','统一运营',C.orange,[
  '数据监控驾驶舱',
  '园区、楼栋、房屋与住户管理',
  '工单、巡检与设备管理',
  '收银台、账单与账务管理',
  '招商、合同与租客管理',
  '门禁、停车与充电桩管理',
  'SaaS 租户管理',
  '套餐与功能权限管理',
  '订单与财务管理',
  '角色权限与组织账号管理',
  '统计报表与 Excel 导出'
]);

slide.addText('三端共享统一账号、租户、权限与业务数据能力',{x:3.75,y:7.08,w:5.85,h:.2,fontSize:9.5,bold:true,color:C.muted,align:'center',margin:0});

pptx.writeFile({ fileName: '功能架构-三端功能.pptx' });
