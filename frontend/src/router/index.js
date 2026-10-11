import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

import LoginView from '../views/LoginView.vue'
import StudentWorkbench from '../views/student/StudentWorkbench.vue'
import StudentSchedule from '../views/student/StudentSchedule.vue'
import StudentReportCard from '../views/student/StudentReportCard.vue'
import ProfessorTeachSelect from '../views/professor/ProfessorTeachSelect.vue'
import ProfessorRoster from '../views/professor/ProfessorRoster.vue'
import ProfessorGrades from '../views/professor/ProfessorGrades.vue'
import RegistrarStudents from '../views/registrar/RegistrarStudents.vue'
import RegistrarProfessors from '../views/registrar/RegistrarProfessors.vue'
import RegistrarCloseRegistration from '../views/registrar/RegistrarCloseRegistration.vue'
import RegistrarBilling from '../views/registrar/RegistrarBilling.vue'
import SystemPanel from '../views/SystemPanel.vue'

const routes = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: LoginView, meta: { title: '登录' } },
  { path: '/student', name: 'student-workbench', component: StudentWorkbench, meta: { roles: ['STUDENT'], title: '课程注册工作台' } },
  { path: '/student/schedule', name: 'student-schedule', component: StudentSchedule, meta: { roles: ['STUDENT'], title: '我的课表' } },
  { path: '/student/report-card', name: 'student-report-card', component: StudentReportCard, meta: { roles: ['STUDENT'], title: '电子成绩单' } },
  { path: '/professor', name: 'professor-teach', component: ProfessorTeachSelect, meta: { roles: ['PROFESSOR'], title: '选择授课课程' } },
  { path: '/professor/roster', name: 'professor-roster', component: ProfessorRoster, meta: { roles: ['PROFESSOR'], title: '选课名单' } },
  { path: '/professor/grades', name: 'professor-grades', component: ProfessorGrades, meta: { roles: ['PROFESSOR'], title: '成绩录入' } },
  { path: '/registrar', name: 'registrar-students', component: RegistrarStudents, meta: { roles: ['REGISTRAR'], title: '维护学生信息' } },
  { path: '/registrar/professors', name: 'registrar-professors', component: RegistrarProfessors, meta: { roles: ['REGISTRAR'], title: '维护教授信息' } },
  { path: '/registrar/close-registration', name: 'registrar-close', component: RegistrarCloseRegistration, meta: { roles: ['REGISTRAR'], title: '关闭注册' } },
  { path: '/registrar/billing', name: 'registrar-billing', component: RegistrarBilling, meta: { roles: ['REGISTRAR'], title: '计费交易监控' } },
  { path: '/system', name: 'system-panel', component: SystemPanel, meta: { roles: ['REGISTRAR', 'PROFESSOR'], title: '系统演示面板' } },
  { path: '/:pathMatch(.*)*', redirect: '/login' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.roles && !to.meta.roles.includes(auth.role)) {
    return auth.loggedIn ? auth.homePath : '/login'
  }
  return true
})

export default router