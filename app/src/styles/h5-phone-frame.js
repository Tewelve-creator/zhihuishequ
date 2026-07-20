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

  if (document.getElementById('h5-phone-frame-style')) return
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
      uni-page-body {
        height: 100% !important;
        overflow-y: auto !important;
        overflow-x: hidden !important;
      }
      .uni-page-head, uni-page-head {
        position: absolute !important;
        top: 0 !important;
        left: 0 !important;
        width: 100% !important;
        max-width: 100% !important;
        transform: none !important;
      }
      .uni-tabbar, .uni-tabbar-bottom, uni-tabbar {
        position: absolute !important;
        left: 0 !important;
        bottom: 0 !important;
        width: 100% !important;
        max-width: 100% !important;
        transform: none !important;
      }
    }
  `
  document.head.appendChild(css)
}

patchUniPhoneFrame()
