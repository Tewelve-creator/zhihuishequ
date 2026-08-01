<script>
import { useUserStore } from './store/user'
import { useHouseStore } from './store/house'

export default {
  onLaunch() {
    const store = useUserStore()
    const houseStore = useHouseStore()
    store.hydrate()
    houseStore.hydrate()
    if (store.token) {
      if (store.isResident) {
        houseStore.refresh().finally(() => {
          store.enterHome()
        })
      } else {
        store.enterHome()
      }
    }
  }
}
</script>

<style lang="scss">
/* uview-plus 基础样式必须放在第一行 */
@import 'uview-plus/index.scss';
@import './styles/common.scss';
@import './styles/safe-area.scss';
</style>
