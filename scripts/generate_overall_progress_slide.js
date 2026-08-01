const pptxgen = require('pptxgenjs');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = '智慧社区项目组';
pptx.title = '项目总体进度';
pptx.subject = 'AI智慧社区项目总体进度';
pptx.lang = 'zh-CN';
pptx.theme = { headFontFace: 'Microsoft YaHei', bodyFontFace: 'Microsoft YaHei', lang: 'zh-CN' };

const slide = pptx.addSlide();
slide.background = { color: 'F3F6F5' };
const S = pptx.ShapeType;
const C = { green:'14967F', dark:'173B35', blue:'2878B5', orange:'E89A35', ink:'1B312D', muted:'60736D', white:'FFFFFF', line:'D6E2DE', pale:'E5F2EE', gray:'A8B7B2' };

slide.addShape(S.rect,{x:0,y:0,w:13.333,h:.12,fill:{color:C.green},line:{color:C.green}});
slide.addText('项目总体进度',{x:.6,y:.3,w:4.1,h:.52,fontSize:27,bold:true,color:C.ink,margin:0});
slide.addText('截至 2026.07.27 · 主体功能完成，进入联调验收阶段',{x:7.55,y:.42,w:5.15,h:.28,fontSize:10.5,color:C.muted,align:'right',margin:0});
slide.addShape(S.line,{x:.6,y:.96,w:12.1,h:0,line:{color:C.line,width:1}});

// Overall progress
slide.addShape(S.roundRect,{x:.6,y:1.22,w:3.05,h:2.05,rectRadius:.05,fill:{color:C.dark},line:{color:C.dark}});
slide.addText('总体完成度',{x:.9,y:1.52,w:1.3,h:.28,fontSize:13,bold:true,color:'A5DDD0',margin:0});
slide.addText('90%',{x:.88,y:1.91,w:1.9,h:.68,fontSize:38,bold:true,color:C.white,margin:0});
slide.addShape(S.roundRect,{x:.9,y:2.69,w:2.35,h:.18,rectRadius:.05,fill:{color:'476660'},line:{color:'476660'}});
slide.addShape(S.roundRect,{x:.9,y:2.69,w:2.12,h:.18,rectRadius:.05,fill:{color:'35B99D'},line:{color:'35B99D'}});
slide.addText('当前阶段：系统联调与验收收尾',{x:.9,y:2.98,w:2.35,h:.18,fontSize:8.7,bold:true,color:'D9ECE7',margin:0,fit:'shrink'});

// Module progress cards
const modules=[
  ['后端微服务',95,'网关、认证、业务服务与数据层'],
  ['Web 管理端',92,'物业工作端与平台运营端'],
  ['移动端 App',90,'居民端与物业移动工作台'],
  ['AI 能力',85,'对话、知识库、意图识别与图片识别'],
  ['部署与文档',88,'Docker、数据库脚本与接口文档']
];
modules.forEach((m,i)=>{
  const x=3.93+i*1.76;
  slide.addShape(S.roundRect,{x,y:1.22,w:1.55,h:2.05,rectRadius:.04,fill:{color:C.white},line:{color:'D7E3DF',width:1}});
  slide.addText(m[0],{x:x+.13,y:1.48,w:1.29,h:.3,fontSize:12,bold:true,color:C.ink,align:'center',margin:0,fit:'shrink'});
  slide.addText(`${m[1]}%`,{x:x+.13,y:1.91,w:1.29,h:.4,fontSize:21,bold:true,color:i===3?C.orange:C.green,align:'center',margin:0});
  slide.addShape(S.roundRect,{x:x+.18,y:2.44,w:1.19,h:.12,rectRadius:.03,fill:{color:'E2EAE7'},line:{color:'E2EAE7'}});
  slide.addShape(S.roundRect,{x:x+.18,y:2.44,w:1.19*m[1]/100,h:.12,rectRadius:.03,fill:{color:i===3?C.orange:C.green},line:{color:i===3?C.orange:C.green}});
  slide.addText(m[2],{x:x+.13,y:2.72,w:1.29,h:.34,fontSize:7.8,color:C.muted,align:'center',margin:0,fit:'shrink'});
});

function statusColumn(x,title,count,color,bg,items){
  slide.addShape(S.roundRect,{x,y:3.58,w:3.87,h:2.82,rectRadius:.05,fill:{color:bg},line:{color:'D8E4E0',width:1}});
  slide.addShape(S.roundRect,{x:x+.25,y:3.82,w:1.15,h:.36,rectRadius:.05,fill:{color},line:{color}});
  slide.addText(`${title} · ${count}`,{x:x+.33,y:3.92,w:.99,h:.15,fontSize:9,bold:true,color:C.white,align:'center',margin:0,fit:'shrink'});
  items.forEach((v,i)=>{
    slide.addShape(S.ellipse,{x:x+.31,y:4.5+i*.42,w:.1,h:.1,fill:{color},line:{color}});
    slide.addText(v,{x:x+.55,y:4.43+i*.42,w:2.95,h:.24,fontSize:10.2,color:C.ink,margin:0,fit:'shrink'});
  });
}

statusColumn(.6,'已完成',7,C.green,'EAF6F2',[
  '微服务基础架构与统一网关',
  '登录认证、多租户与权限控制',
  '居民端、物业端主要业务功能',
  'Web 管理后台与数据驾驶舱',
  '数据库、MinIO 与部署脚本'
]);
statusColumn(4.73,'进行中',4,C.orange,'FFF7E8',[
  '三端接口全链路联调',
  '真实 AI 模型效果验证',
  '异常场景与权限边界测试',
  '答辩材料和演示数据完善'
]);
statusColumn(8.86,'待验收',3,'6F8197','F1F4F7',[
  '核心业务流程回归测试',
  '性能、安全与兼容性检查',
  '最终部署及项目成果验收'
]);

slide.addShape(S.roundRect,{x:.6,y:6.66,w:12.13,h:.4,rectRadius:.04,fill:{color:C.white},line:{color:'D5E2DD'}});
slide.addText('下一里程碑',{x:.83,y:6.78,w:1.1,h:.17,fontSize:9.5,bold:true,color:C.green,margin:0});
slide.addText('完成全链路回归测试 → 固化演示环境 → 提交最终验收材料',{x:2.05,y:6.76,w:9.8,h:.2,fontSize:10.5,bold:true,color:C.dark,margin:0});

pptx.writeFile({ fileName: '项目总体进度-当前评估.pptx' });
