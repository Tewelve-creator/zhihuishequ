<script>
import { useUserStore } from './store/user'

export default {
  onLaunch() {
    const store = useUserStore()
    store.hydrate()
    if (store.token) {
      store.enterHome()
    }
  }
}
</script>

<style lang="scss">
/* uview-plus 基础样式必须放在第一行 */
@import 'uview-plus/index.scss';
@import './styles/common.scss';
</style>
