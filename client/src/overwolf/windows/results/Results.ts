import './results.styl';
import Actions from '@/components/Actions.vue';
import { Windows } from '@/overwolf/scripts';
import { store, vuetify } from '@/lib';
import Vue from 'vue';

new Vue({
  store: store.original,
  vuetify,
  methods: {
    showSettings(): void {
      Windows.getInstance().minimize(Windows.RESULTS);
      Windows.getInstance().restore(Windows.SETTINGS);
    },
  },
  components: {
    Actions,
  },
  mounted() {
    this.$store.direct.dispatch.findPath();
  },

}).$mount('#app');
