import http from './http'

/** 认证 */
export const authApi = {
  login: (payload) => http.post('/auth/login', payload),
  me: () => http.get('/auth/me')
}

/** 学期列表（全角色可用，用于各页面学期切换） */
export const semesterApi = {
  list: () => http.get('/semesters')
}

/** 学生用例 */
export const studentApi = {
  catalog: () => http.get('/student/catalog'),
  schedule: (semesterId) => http.get('/student/schedule', { params: { semesterId } }),
  saveDraft: (items) => http.post('/student/schedule/draft', { items }),
  submit: (items) => http.post('/student/schedule/submit', { items }),
  remove: () => http.delete('/student/schedule'),
  adjustmentAdd: (offeringId) => http.post(`/student/adjustment/add/${offeringId}`),
  adjustmentDrop: (offeringId) => http.post(`/student/adjustment/drop/${offeringId}`),
  reportCard: (semesterId) => http.get('/student/report-card', { params: { semesterId } }),
  transcript: () => http.get('/student/transcript'),
  billingTransactions: (semesterId) => http.get('/student/billing-transactions', { params: { semesterId } })
}

/** 教授用例 */
export const professorApi = {
  qualifiedCourses: () => http.get('/professor/qualified-courses'),
  myOfferings: (semesterId) => http.get('/professor/offerings', { params: { semesterId } }),
  select: (offeringId) => http.post(`/professor/offerings/${offeringId}/select`),
  unselect: (offeringId) => http.delete(`/professor/offerings/${offeringId}/select`),
  roster: (offeringId) => http.get(`/professor/offerings/${offeringId}/roster`),
  gradeSheet: (offeringId) => http.get(`/professor/offerings/${offeringId}/grades`),
  saveGrades: (offeringId, grades) => http.put(`/professor/offerings/${offeringId}/grades`, { grades })
}

/** 注册管理员用例 */
export const registrarApi = {
  students: (keyword) => http.get('/registrar/students', { params: { keyword } }),
  createStudent: (payload) => http.post('/registrar/students', payload),
  updateStudent: (id, payload) => http.put(`/registrar/students/${id}`, payload),
  deleteStudent: (id) => http.delete(`/registrar/students/${id}`),
  professors: (keyword) => http.get('/registrar/professors', { params: { keyword } }),
  createProfessor: (payload) => http.post('/registrar/professors', payload),
  updateProfessor: (id, payload) => http.put(`/registrar/professors/${id}`, payload),
  deleteProfessor: (id) => http.delete(`/registrar/professors/${id}`),
  closeRegistration: () => http.post('/registrar/close-registration'),
  billingTransactions: (semesterId) => http.get('/registrar/billing-transactions', { params: { semesterId } }),
  retryBilling: () => http.post('/registrar/billing-transactions/retry'),

  /* 学期管理 */
  semesters: () => http.get('/semesters'),
  createSemester: (payload) => http.post('/registrar/semesters', payload),
  updateSemester: (id, payload) => http.put(`/registrar/semesters/${id}`, payload),
  setCurrentSemester: (id) => http.post(`/registrar/semesters/${id}/set-current`),

  /* 课程班（开班）管理 */
  courses: () => http.get('/registrar/courses'),
  offerings: (semesterId) => http.get('/registrar/offerings', { params: { semesterId } }),
  createOffering: (payload) => http.post('/registrar/offerings', payload),
  updateOffering: (id, payload) => http.put(`/registrar/offerings/${id}`, payload),
  deleteOffering: (id) => http.delete(`/registrar/offerings/${id}`)
}

/** 系统演示开关 */
export const systemApi = {
  status: () => http.get('/system/status'),
  setLegacyMode: (mode) => http.post('/system/legacy-mode', null, { params: { mode } }),
  setLegacyAvailability: (available) => http.post('/system/legacy-availability', null, { params: { available } }),
  refreshCache: () => http.post('/system/cache/refresh'),
  setBillingAvailability: (available) => http.post('/system/billing-availability', null, { params: { available } })
}