/**
 * 桌面 H5：强制按 375×667 手机竖屏计算 rpx，并限制预览框
 */
function patchUniPhoneFrame() {
  if (typeof window === 'undefined') return
  if (window.innerWidth < 460) return
  if (typeof uni === 'undefined') {
    setTimeout(patchUniPhoneFrame, 16)
    return
  }

  const PHONE_W = 375
  const PHONE_H = 667

  const patchInfo = (info) => {
    if (!info) return info
    info.windowWidth = PHONE_W
    info.windowHeight = PHONE_H
    info.screenWidth = PHONE_W
    info.screenHeight = PHONE_H
    info.safeArea = {
      left: 0,
      right: PHONE_W,
      top: 0,
      bottom: PHONE_H,
      width: PHONE_W,
      height: PHONE_H
    }
    if (info.safeAreaInsets) {
      info.safeAreaInsets = { top: 0, right: 0, bottom: 0, left: 0 }
    }
    return info
  }

  try {
    const _sync = uni.getSystemInfoSync.bind(uni)
    uni.getSystemInfoSync = function () {
      return patchInfo(_sync())
    }
  } catch (e) {}

  try {
    if (uni.getWindowInfo) {
      const _win = uni.getWindowInfo.bind(uni)
      uni.getWindowInfo = function () {
        return patchInfo(_win())
      }
    }
  } catch (e) {}

  // 允许热更新覆盖旧样式
  const old = document.getElementById('h5-phone-frame-style')
  if (old) old.remove()
  const css = document.createElement('style')
  css.id = 'h5-phone-frame-style'
  css.textContent = `
    @media screen and (min-width: 460px) {
      #app, uni-app {
        width: ${PHONE_W}px !important;
        height: ${PHONE_H}px !important;
        max-width: ${PHONE_W}px !important;
        overflow: hidden !important;
      }
      uni-page, uni-page-wrapper {
        position: absolute !important;
        inset: 0 !important;
        width: 100% !important;
        height: 100% !important;
      }
      /* Tab 页：页面内容区底部让出 tabBar，避免结算条等被挡住 */
      #app:has(.uni-tabbar) uni-page-wrapper,
      #app:has(uni-tabbar) uni-page-wrapper,
      uni-app:has(.uni-tabbar) uni-page-wrapper,
      uni-app:has(uni-tabbar) uni-page-wrapper {
        bottom: 50px !important;
        height: auto !important;
      }
      /* 框内已让出 Tab 高度，页面内不再重复扣 --window-bottom */
      #app:has(.uni-tabbar),
      #app:has(uni-tabbar),
      uni-app:has(.uni-tabbar),
      uni-app:has(uni-tabbar) {
        --window-bottom: 0px;
      }
      uni-page-body {
        position: relative !important;
        height: 100% !important;
        overflow-x: hidden !important;
        overflow-y: auto !important;
        box-sizing: border-box !important;
        /* 默认让出系统导航栏高度，避免正文钻到标题栏下 */
        padding-top: 44px !important;
      }
      /* 自定义导航页（无系统顶栏）：取消垫顶，避免头部留白 */
      uni-page-body:has(> .home),
      uni-page-body:has(> .mine),
      uni-page-body:has(> .login-page),
      uni-page-body:has(> .moments-page),
      uni-page-body:has(.home),
      uni-page-body:has(.mine),
      uni-page-body:has(.login-page),
      uni-page-body:has(.moments-page) {
        padding-top: 0 !important;
      }
      .uni-page-head, uni-page-head {
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        width: 100% !important;
        max-width: 100% !important;
        height: 44px !important;
        transform: none !important;
        z-index: 998 !important;
      }
      .uni-tabbar, .uni-tabbar-bottom, uni-tabbar {
        position: absolute !important;
        left: 0 !important;
        bottom: 0 !important;
        width: 100% !important;
        max-width: 100% !important;
        height: 50px !important;
        transform: none !important;
        z-index: 998 !important;
      }
      :root {
        --window-top: 44px;
        --window-bottom: 50px;
      }
      /* 禁止页面内 fixed 底栏逃出手机预览框 */
      uni-page-body [style*="position: fixed"],
      uni-page-body .dock,
      uni-page-body .foot-bar {
        position: relative !important;
        left: auto !important;
        right: auto !important;
        bottom: auto !important;
        max-width: 100% !important;
        box-sizing: border-box !important;
      }
      /* 认领等 absolute 遮罩：锁在手机框内，避免宽屏溢出 */
      uni-page-body .claim-mask,
      uni-page-body .offer-mask,
      uni-page-body .report-mask,
      uni-page-body .mask {
        position: absolute !important;
        left: 0 !important;
        right: 0 !important;
        top: 0 !important;
        bottom: 0 !important;
        width: 100% !important;
        max-width: 100% !important;
        box-sizing: border-box !important;
      }
    }
  `
  document.head.appendChild(css)
}

patchUniPhoneFrame()
