import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import DatasetView from '../views/DatasetView.vue'
import WorkflowView from '../views/WorkflowView.vue'
import AgentView from '../views/AgentView.vue'
import BillingView from '../views/BillingView.vue'

const routes = [
  { path: '/', name: 'Dashboard', component: DashboardView },
  { path: '/datasets', name: 'Datasets', component: DatasetView },
  { path: '/workflows', name: 'Workflows', component: WorkflowView },
  { path: '/agents', name: 'Agents', component: AgentView },
  { path: '/billing', name: 'Billing', component: BillingView }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
