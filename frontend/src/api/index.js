import http from './http'

/** 认证 */
export const authApi = {
  login: (payload) => http.post('/auth/login', payload),
  me: () => http.get('/auth/me')
}

/** 学生用例 */
export const studentApi = {
  catalog: () => http.get('/student/catalog'),
  schedule: () => http.get('/student/schedule'),
  saveDraft: (items) => http.post('/student/schedule/draft', { items }),
  submit: (items) => http.post('/student/schedule/submit', { items }),
  remove: () => http.delete('/student/schedule'),
  adjustmentAdd: (offeringId) => http.post(`/student/adjustment/add/${offeringId}`),
  adjustmentDrop: (offeringId) => http.post(`/student/adjustment/drop/${offeringId}`),
  reportCard: (semesterId) => http.get('/student/report-card', { params: { semesterId } })
}

/** 教授用例 */
export const professorApi = {
  qualifiedCourses: () => http.get('/professor/qualified-courses'),
  myOfferings: () => http.get('/professor/offerings'),
  previousOfferings: () => http.get('/professor/offerings/previous'),
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
  retryBilling: () => http.post('/registrar/billing-transactions/retry')
}

/** 系统演示开关 */
export const systemApi = {
  status: () => http.get('/system/status'),
  setLegacyMode: (mode) => http.post('/system/legacy-mode', null, { params: { mode } }),
  setLegacyAvailability: (available) => http.post('/system/legacy-availability', null, { params: { available } }),
  refreshCache: () => http.post('/system/cache/refresh'),
  setBillingAvailability: (available) => http.post('/system/billing-availability', null, { params: { available } })
}