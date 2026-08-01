const pptxgen = require('pptxgenjs');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = '智慧社区项目组';
pptx.subject = 'AI智慧社区技术架构';
pptx.title = '技术架构';
pptx.lang = 'zh-CN';
pptx.theme = { headFontFace: 'Microsoft YaHei', bodyFontFace: 'Microsoft YaHei', lang: 'zh-CN' };

const slide = pptx.addSlide();
slide.background = { color: 'F5F8FC' };
const S = pptx.ShapeType;
const C = { navy:'073D85', blue:'0756AE', cyan:'23B6B4', ink:'17283D', muted:'52657A', white:'FFFFFF', border:'184B82', line:'93D8D4' };

slide.addShape(S.roundRect,{x:.12,y:.12,w:13.09,h:7.22,rectRadius:.08,fill:{color:C.white},line:{color:'DCE5EF',width:1}});
slide.addText('技术架构',{x:.42,y:.34,w:2.5,h:.52,fontSize:28,bold:true,color:C.navy,margin:0});
slide.addShape(S.line,{x:.43,y:.98,w:12.4,h:0,line:{color:'D6E2ED',width:1}});

function panel(x,y,w,h,title,items,columns=1) {
  slide.addShape(S.roundRect,{x,y,w,h,rectRadius:.06,fill:{color:'FFFFFF'},line:{color:C.border,width:1.15}});
  slide.addShape(S.roundRect,{x,y,w,h:.55,rectRadius:.06,fill:{color:C.navy},line:{color:C.navy,width:1}});
  slide.addText(title,{x:x+.1,y:y+.13,w:w-.2,h:.25,fontSize:15,bold:true,color:C.white,align:'center',margin:0,fit:'shrink'});
  const colW=(w-.45)/columns;
  const rows=Math.ceil(items.length/columns);
  items.forEach((item,i)=>{
    const col=Math.floor(i/rows), row=i%rows;
    const tx=x+.27+col*colW, ty=y+.76+row*((h-.9)/rows);
    slide.addShape(S.ellipse,{x:tx,y:ty+.055,w:.105,h:.105,fill:{color:C.white},line:{color:C.cyan,width:1.35}});
    slide.addText(item,{x:tx+.19,y:ty,w:colW-.23,h:(h-.9)/rows-.02,fontSize:10.4,color:C.ink,margin:0,fit:'shrink',valign:'top'});
  });
}

// Subtle connection lines behind the cards.
slide.addShape(S.line,{x:1.95,y:4.75,w:0,h:.35,line:{color:C.line,width:1}});
slide.addShape(S.line,{x:1.95,y:5.1,w:8.0,h:0,line:{color:C.line,width:1}});
slide.addShape(S.line,{x:9.95,y:4.75,w:0,h:.35,line:{color:C.line,width:1}});
slide.addShape(S.line,{x:6.62,y:1.25,w:0,h:4.58,line:{color:C.line,width:.8,transparency:25}});

panel(.55,1.22,2.75,3.35,'后端技术',[
  'Java 17','Spring Boot 3','Spring Cloud','Spring Cloud Alibaba','Spring Cloud Gateway','统一入口与路由转发'
]);
panel(3.52,1.22,2.75,3.35,'服务治理',[
  'Nacos：服务注册与发现','OpenFeign：服务间调用','Sentinel：限流与降级','Sa-Token：认证与权限','MyBatis-Plus：数据访问'
]);
panel(6.49,1.22,2.75,3.35,'数据与存储',[
  'MySQL 8：业务数据','Redis：会话与缓存','MinIO：图片对象存储','租户数据隔离','Maven：多模块管理'
]);
panel(9.46,1.22,3.3,3.35,'Web 管理端技术',[
  'Vue 3、TypeScript','Vite','Element Plus','Pinia、Vue Router','Axios','ECharts、Vue-ECharts','XLSX 报表导出'
]);
panel(.55,4.88,5.72,1.93,'移动端技术',[
  'UniApp','Vue 3','uView Plus','Vite','Axios','H5 / App 多端适配'
],2);
panel(6.49,4.88,6.27,1.93,'AI 能力',[
  'AI 对话服务','业务意图识别与槽位提取','RAG 社区知识库检索','报修图片识别','Mock / 真实模型切换','服务降级与异常兜底'
],2);

slide.addText('前后端分离 · 多端协同 · 微服务架构 · 多租户 SaaS',{x:3.25,y:7.02,w:6.85,h:.2,fontSize:9.5,bold:true,color:C.muted,align:'center',margin:0});

pptx.writeFile({ fileName: '技术架构-错字修正版.pptx' });
